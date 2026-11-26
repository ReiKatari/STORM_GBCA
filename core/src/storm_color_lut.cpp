#include "storm_internal.hpp"
#include <algorithm>
#include <cmath>
#include <cstring>

namespace storm {

namespace {

inline uint8_t clamp_u8(double v) {
    if (v <= 0.0) return 0;
    if (v >= 255.0) return 255;
    return static_cast<uint8_t>(v + 0.5);
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

    if (mode == STORM_COLOR_CORRECTION_GBA_AGB_AGS || (is_gba && mode != STORM_COLOR_CORRECTION_CORRECT_CURVES)) {
        // Ported from NanoBoyAdvance Filters.hlsl FColorCorrect
        const double gamma = 2.2;
        const double screen_darken = is_gba ? 1.0 : 0.0;
        const double luminance = (mode == STORM_COLOR_CORRECTION_LOW_CONTRAST) ? 0.85 : 0.94;
        const double lr = std::pow(r / 255.0, gamma + screen_darken) * luminance;
        const double lg = std::pow(g / 255.0, gamma + screen_darken) * luminance;
        const double lb = std::pow(b / 255.0, gamma + screen_darken) * luminance;

        double cr = 0.82 * lr + 0.125 * lg + 0.195 * lb;
        double cg = 0.24 * lr + 0.665 * lg + 0.075 * lb;
        double cb = -0.06 * lr + 0.21 * lg + 0.73 * lb;

        cr = std::clamp(cr, 0.0, 1.0);
        cg = std::clamp(cg, 0.0, 1.0);
        cb = std::clamp(cb, 0.0, 1.0);

        r = clamp_u8(std::pow(cr, 1.0 / gamma) * 255.0);
        g = clamp_u8(std::pow(cg, 1.0 / gamma) * 255.0);
        b = clamp_u8(std::pow(cb, 1.0 / gamma) * 255.0);
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
    double cr = curve[r >> 3];
    double cg = curve[g >> 3];
    double cb = curve[b >> 3];

    if (mode == STORM_COLOR_CORRECTION_CORRECT_CURVES) {
        r = clamp_u8(cr);
        g = clamp_u8(cg);
        b = clamp_u8(cb);
        return;
    }

    double gamma = (mode < STORM_COLOR_CORRECTION_REDUCE_CONTRAST) ? 1.6 : 2.2;
    double new_g = std::pow((std::pow(cg / 255.0, gamma) * 3.0 + std::pow(cb / 255.0, gamma)) * 0.25, 1.0 / gamma) * 255.0;
    double new_r = cr;
    double new_b = cb;

    if (mode == STORM_COLOR_CORRECTION_REDUCE_CONTRAST) {
        double nr = new_r * (15.0 / 16.0) + (new_g + new_b) / 32.0;
        double ng = new_g * (15.0 / 16.0) + (new_r + new_b) / 32.0;
        double nb = new_b * (15.0 / 16.0) + (new_r + new_g) / 32.0;
        new_r = nr * (220.0 - 40.0) / 255.0 + 40.0;
        new_g = ng * (224.0 - 36.0) / 255.0 + 36.0;
        new_b = nb * (216.0 - 32.0) / 255.0 + 32.0;
    } else if (mode == STORM_COLOR_CORRECTION_LOW_CONTRAST) {
        double nr = new_r * (13.0 / 16.0) + (new_g + new_b) * (3.0 / 32.0);
        double ng = new_g * (13.0 / 16.0) + (new_r + new_b) * (3.0 / 32.0);
        double nb = new_b * (13.0 / 16.0) + (new_r + new_g) * (3.0 / 32.0);
        new_r = nr * (205.0 - 48.0) / 255.0 + 48.0;
        new_g = ng * (208.0 - 44.0) / 255.0 + 44.0;
        new_b = nb * (200.0 - 40.0) / 255.0 + 40.0;
    } else if (mode == STORM_COLOR_CORRECTION_MODERN_BOOST) {
        new_r = std::min(255.0, new_r * 1.08);
        new_g = std::min(255.0, new_g * 1.08);
        new_b = std::min(255.0, new_b * 1.08);
    } else if (mode == STORM_COLOR_CORRECTION_MODERN_ACCURATE) {
        new_r = new_r * 0.92 + new_g * 0.08;
        new_g = new_g * 0.88 + new_b * 0.12;
        new_b = new_b * 0.90 + new_g * 0.10;
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
