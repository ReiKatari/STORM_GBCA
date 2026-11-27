#include "storm_internal.hpp"
#include <algorithm>
#include <cmath>
#include <cstring>

namespace storm {

namespace {

inline uint8_t clamp_u8(float v) {
    if (v <= 0.0f) return 0;
    if (v >= 255.0f) return 255;
    return static_cast<uint8_t>(v + 0.5f);
}

struct ColorLutTables {
    float pow_16[256];
    float pow_22[256];
    float pow_32[256];
    float inv_16[4097];
    float inv_22[4097];

    ColorLutTables() {
        for (int i = 0; i < 256; ++i) {
            const double x = static_cast<double>(i) / 255.0;
            pow_16[i] = static_cast<float>(std::pow(x, 1.6));
            pow_22[i] = static_cast<float>(std::pow(x, 2.2));
            pow_32[i] = static_cast<float>(std::pow(x, 3.2));
        }
        for (int i = 0; i <= 4096; ++i) {
            const double x = std::clamp(static_cast<double>(i) / 4095.0, 0.0, 1.0);
            inv_16[i] = static_cast<float>(std::pow(x, 1.0 / 1.6) * 255.0);
            inv_22[i] = static_cast<float>(std::pow(x, 1.0 / 2.2) * 255.0);
        }
    }
};

const ColorLutTables& get_color_luts() {
    static const ColorLutTables kLuts;
    return kLuts;
}

inline float lookup_inv_lut(const float* inv_lut, float x) {
    const float clamped = std::clamp(x, 0.0f, 1.0f) * 4095.0f;
    const int idx = static_cast<int>(clamped);
    const float frac = clamped - static_cast<float>(idx);
    return inv_lut[idx] + (inv_lut[idx + 1] - inv_lut[idx]) * frac;
}

inline uint32_t pack_px(uint8_t r, uint8_t g, uint8_t b, int pixel_format) {
    if (pixel_format == STORM_PIXEL_FORMAT_BGRA8888) {
        // Memory bytes: B, G, R, A -> uint32_t LE = 0xAARRGGBB
        return (0xFFu << 24) | (static_cast<uint32_t>(r) << 16) |
               (static_cast<uint32_t>(g) << 8) | static_cast<uint32_t>(b);
    }
    // STORM_PIXEL_FORMAT_RGBA8888: memory bytes R, G, B, A -> uint32_t LE = 0xAABBGGRR
    return (0xFFu << 24) | (static_cast<uint32_t>(b) << 16) |
           (static_cast<uint32_t>(g) << 8) | static_cast<uint32_t>(r);
}

inline void unpack_rgba(uint32_t px, uint8_t& r, uint8_t& g, uint8_t& b) {
    r = static_cast<uint8_t>(px & 0xFFu);
    g = static_cast<uint8_t>((px >> 8) & 0xFFu);
    b = static_cast<uint8_t>((px >> 16) & 0xFFu);
}

// Color correction following SameBoy 7 modes + NanoBoyAdvance Filters.hlsl AGB/AGS matrix
void apply_color_correction(uint8_t& r, uint8_t& g, uint8_t& b, int mode, bool is_gba) {
    if (mode == STORM_COLOR_CORRECTION_DISABLED) {
        return;
    }

    const ColorLutTables& luts = get_color_luts();

    if (mode == STORM_COLOR_CORRECTION_GBA_AGB_AGS || (is_gba && mode != STORM_COLOR_CORRECTION_CORRECT_CURVES)) {
        // Ported from NanoBoyAdvance Filters.hlsl FColorCorrect using precomputed LUTs
        const float* in_lut = is_gba ? luts.pow_32 : luts.pow_22;
        const float luminance = (mode == STORM_COLOR_CORRECTION_LOW_CONTRAST) ? 0.85f : 0.94f;
        const float lr = in_lut[r] * luminance;
        const float lg = in_lut[g] * luminance;
        const float lb = in_lut[b] * luminance;

        const float cr = 0.82f * lr + 0.125f * lg + 0.195f * lb;
        const float cg = 0.24f * lr + 0.665f * lg + 0.075f * lb;
        const float cb = -0.06f * lr + 0.21f * lg + 0.73f * lb;

        r = clamp_u8(lookup_inv_lut(luts.inv_22, cr));
        g = clamp_u8(lookup_inv_lut(luts.inv_22, cg));
        b = clamp_u8(lookup_inv_lut(luts.inv_22, cb));
        return;
    }

    // SameBoy 5-bit curve approximation on 8-bit values
    static const uint8_t curve_cgb[32] = {
        0,6,12,20,28,36,45,56,66,76,88,100,113,125,137,149,
        161,172,182,192,202,210,218,225,232,238,243,247,250,252,254,255
    };
    static const uint8_t curve_agb[32] = {
        0,3,8,14,20,26,33,40,47,54,62,70,78,86,94,103,
        112,120,129,138,147,157,166,176,185,195,205,215,225,235,245,255
    };
    const uint8_t* curve = is_gba ? curve_agb : curve_cgb;
    const uint8_t cr = curve[r >> 3];
    const uint8_t cg = curve[g >> 3];
    const uint8_t cb = curve[b >> 3];

    if (mode == STORM_COLOR_CORRECTION_CORRECT_CURVES) {
        r = cr;
        g = cg;
        b = cb;
        return;
    }

    const bool use_16 = (mode < STORM_COLOR_CORRECTION_REDUCE_CONTRAST);
    const float* pow_lut = use_16 ? luts.pow_16 : luts.pow_22;
    const float* inv_lut = use_16 ? luts.inv_16 : luts.inv_22;
    float new_g = lookup_inv_lut(inv_lut, (pow_lut[cg] * 3.0f + pow_lut[cb]) * 0.25f);
    float new_r = static_cast<float>(cr);
    float new_b = static_cast<float>(cb);

    if (mode == STORM_COLOR_CORRECTION_REDUCE_CONTRAST) {
        const float nr = new_r * (15.0f / 16.0f) + (new_g + new_b) / 32.0f;
        const float ng = new_g * (15.0f / 16.0f) + (new_r + new_b) / 32.0f;
        const float nb = new_b * (15.0f / 16.0f) + (new_r + new_g) / 32.0f;
        new_r = nr * (220.0f - 40.0f) / 255.0f + 40.0f;
        new_g = ng * (224.0f - 36.0f) / 255.0f + 36.0f;
        new_b = nb * (216.0f - 32.0f) / 255.0f + 32.0f;
    } else if (mode == STORM_COLOR_CORRECTION_LOW_CONTRAST) {
        const float nr = new_r * (13.0f / 16.0f) + (new_g + new_b) * (3.0f / 32.0f);
        const float ng = new_g * (13.0f / 16.0f) + (new_r + new_b) * (3.0f / 32.0f);
        const float nb = new_b * (13.0f / 16.0f) + (new_r + new_g) * (3.0f / 32.0f);
        new_r = nr * (205.0f - 48.0f) / 255.0f + 48.0f;
        new_g = ng * (208.0f - 44.0f) / 255.0f + 44.0f;
        new_b = nb * (200.0f - 40.0f) / 255.0f + 40.0f;
    } else if (mode == STORM_COLOR_CORRECTION_MODERN_BOOST) {
        new_r = std::min(255.0f, new_r * 1.08f);
        new_g = std::min(255.0f, new_g * 1.08f);
        new_b = std::min(255.0f, new_b * 1.08f);
    } else if (mode == STORM_COLOR_CORRECTION_MODERN_ACCURATE) {
        new_r = new_r * 0.92f + new_g * 0.08f;
        new_g = new_g * 0.88f + new_b * 0.12f;
        new_b = new_b * 0.90f + new_g * 0.10f;
    }

    r = clamp_u8(new_r);
    g = clamp_u8(new_g);
    b = clamp_u8(new_b);
}

} // namespace

void VideoPostProcessor::reset() {
    prev_frame.clear();
    work_frame.clear();
    prev_w = 0;
    prev_h = 0;
}

void VideoPostProcessor::process_frame(const uint32_t* src_rgba, int w, int h,
                                       int color_correction_mode,
                                       int ghosting_pct,
                                       int video_filter,
                                       bool is_gba,
                                       uint32_t* dst, int dst_stride_px, int pixel_format) {
    if (!src_rgba || !dst || w <= 0 || h <= 0) return;
    if (dst_stride_px < w) dst_stride_px = w;

    const size_t total = static_cast<size_t>(w) * static_cast<size_t>(h);
    work_frame.resize(total);
    if (prev_w != w || prev_h != h || prev_frame.size() != total) {
        prev_frame.assign(src_rgba, src_rgba + total);
        prev_w = w;
        prev_h = h;
    }

    const int ghost_alpha = std::clamp(ghosting_pct, 0, 100) * 140 / 100; // max ~55% persistence

    for (size_t i = 0; i < total; ++i) {
        uint8_t r, g, b;
        unpack_rgba(src_rgba[i], r, g, b);

        if (ghost_alpha > 0) {
            uint8_t pr, pg, pb;
            unpack_rgba(prev_frame[i], pr, pg, pb);
            r = static_cast<uint8_t>((r * (256 - ghost_alpha) + pr * ghost_alpha) >> 8);
            g = static_cast<uint8_t>((g * (256 - ghost_alpha) + pg * ghost_alpha) >> 8);
            b = static_cast<uint8_t>((b * (256 - ghost_alpha) + pb * ghost_alpha) >> 8);
        }

        if (color_correction_mode != STORM_COLOR_CORRECTION_DISABLED) {
            apply_color_correction(r, g, b, color_correction_mode, is_gba);
        }

        work_frame[i] = (0xFFu << 24) | (static_cast<uint32_t>(b) << 16) |
                        (static_cast<uint32_t>(g) << 8) | static_cast<uint32_t>(r);
    }

    // Store raw source for next frame's ghosting
    std::memcpy(prev_frame.data(), src_rgba, total * sizeof(uint32_t));

    // Software fallback filter subtle effects (GPU shader handles scaled passes when GLES3/DX is used)
    for (int y = 0; y < h; ++y) {
        uint32_t* row_out = dst + static_cast<size_t>(y) * static_cast<size_t>(dst_stride_px);
        const uint32_t* row_in = work_frame.data() + static_cast<size_t>(y) * static_cast<size_t>(w);
        for (int x = 0; x < w; ++x) {
            uint8_t r, g, b;
            unpack_rgba(row_in[x], r, g, b);
            if (video_filter == STORM_VIDEO_FILTER_CRT_SCANLINES && (y & 1)) {
                r = static_cast<uint8_t>((r * 224) >> 8);
                g = static_cast<uint8_t>((g * 224) >> 8);
                b = static_cast<uint8_t>((b * 224) >> 8);
            } else if (video_filter == STORM_VIDEO_FILTER_BILINEAR && x + 1 < w) {
                uint8_t nr, ng, nb;
                unpack_rgba(row_in[x + 1], nr, ng, nb);
                r = static_cast<uint8_t>((r * 3 + nr) >> 2);
                g = static_cast<uint8_t>((g * 3 + ng) >> 2);
                b = static_cast<uint8_t>((b * 3 + nb) >> 2);
            }
            row_out[x] = pack_px(r, g, b, pixel_format);
        }
    }
}

} // namespace storm
