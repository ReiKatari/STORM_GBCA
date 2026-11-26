#include "storm_internal.hpp"
#include <algorithm>
#include <cmath>
#include <cstdint>
#include <cstring>

namespace storm {

namespace {

inline int y_luma(uint32_t c) {
    int r = c & 0xFF;
    int g = (c >> 8) & 0xFF;
    int b = (c >> 16) & 0xFF;
    return (r * 299 + g * 587 + b * 114) / 1000;
}

inline int color_dist(uint32_t a, uint32_t b) {
    if (a == b) return 0;
    int dr = static_cast<int>(a & 0xFF) - static_cast<int>(b & 0xFF);
    int dg = static_cast<int>((a >> 8) & 0xFF) - static_cast<int>((b >> 8) & 0xFF);
    int db = static_cast<int>((a >> 16) & 0xFF) - static_cast<int>((b >> 16) & 0xFF);
    // Weighted perceptual distance (YCbCr approximation)
    int dy = (dr * 299 + dg * 587 + db * 114) / 1000;
    int dcb = (db - dy) / 2;
    int dcr = (dr - dy) / 2;
    return std::abs(dy) * 4 + std::abs(dcb) * 2 + std::abs(dcr) * 2;
}

inline bool similar(uint32_t a, uint32_t b, int thresh = 68) {
    return color_dist(a, b) <= thresh;
}

inline uint32_t blend_rgba(uint32_t a, uint32_t b, int w_b_256) {
    int w_a = 256 - w_b_256;
    uint32_t r = (((a & 0xFF) * w_a + (b & 0xFF) * w_b_256) >> 8) & 0xFF;
    uint32_t g = ((((a >> 8) & 0xFF) * w_a + ((b >> 8) & 0xFF) * w_b_256) >> 8) & 0xFF;
    uint32_t bl = ((((a >> 16) & 0xFF) * w_a + ((b >> 16) & 0xFF) * w_b_256) >> 8) & 0xFF;
    return 0xFF000000u | (bl << 16) | (g << 8) | r;
}

inline uint32_t scale_rgb_mul(uint32_t c, int mr, int mg, int mb) {
    int r = std::min(255, ((static_cast<int>(c & 0xFF) * mr) >> 8));
    int g = std::min(255, ((static_cast<int>((c >> 8) & 0xFF) * mg) >> 8));
    int b = std::min(255, ((static_cast<int>((c >> 16) & 0xFF) * mb) >> 8));
    return 0xFF000000u | (static_cast<uint32_t>(b) << 16) |
           (static_cast<uint32_t>(g) << 8) | static_cast<uint32_t>(r);
}

inline uint32_t sample_clamped(const uint32_t* src, int w, int h, int x, int y) {
    x = std::clamp(x, 0, w - 1);
    y = std::clamp(y, 0, h - 1);
    return src[y * w + x];
}

void upscale_nearest(const uint32_t* src, int sw, int sh, int scale, uint32_t* dst) {
    const int dw = sw * scale;
    for (int y = 0; y < sh; ++y) {
        const uint32_t* srow = src + y * sw;
        for (int sy = 0; sy < scale; ++sy) {
            uint32_t* drow = dst + (y * scale + sy) * dw;
            for (int x = 0; x < sw; ++x) {
                uint32_t c = srow[x];
                for (int sx = 0; sx < scale; ++sx) {
                    drow[x * scale + sx] = c;
                }
            }
        }
    }
}

void upscale_epx_scale(const uint32_t* src, int sw, int sh, int scale, uint32_t* dst) {
    const int dw = sw * scale;
    for (int y = 0; y < sh; ++y) {
        for (int x = 0; x < sw; ++x) {
            uint32_t E = sample_clamped(src, sw, sh, x, y);
            uint32_t B = sample_clamped(src, sw, sh, x, y - 1);
            uint32_t H = sample_clamped(src, sw, sh, x, y + 1);
            uint32_t D = sample_clamped(src, sw, sh, x - 1, y);
            uint32_t F = sample_clamped(src, sw, sh, x + 1, y);

            bool bh_diff = !similar(B, H, 28);
            bool df_diff = !similar(D, F, 28);
            uint32_t tl = (bh_diff && df_diff && similar(D, B, 28)) ? blend_rgba(D, B, 128) : E;
            uint32_t tr = (bh_diff && df_diff && similar(B, F, 28)) ? blend_rgba(B, F, 128) : E;
            uint32_t bl = (bh_diff && df_diff && similar(D, H, 28)) ? blend_rgba(D, H, 128) : E;
            uint32_t br = (bh_diff && df_diff && similar(H, F, 28)) ? blend_rgba(H, F, 128) : E;

            if (scale == 2) {
                dst[(y * 2 + 0) * dw + (x * 2 + 0)] = tl;
                dst[(y * 2 + 0) * dw + (x * 2 + 1)] = tr;
                dst[(y * 2 + 1) * dw + (x * 2 + 0)] = bl;
                dst[(y * 2 + 1) * dw + (x * 2 + 1)] = br;
            } else {
                for (int sy = 0; sy < scale; ++sy) {
                    for (int sx = 0; sx < scale; ++sx) {
                        uint32_t outc = E;
                        if (sy == 0 && sx == 0) outc = tl;
                        else if (sy == 0 && sx == scale - 1) outc = tr;
                        else if (sy == scale - 1 && sx == 0) outc = bl;
                        else if (sy == scale - 1 && sx == scale - 1) outc = br;
                        dst[(y * scale + sy) * dw + (x * scale + sx)] = outc;
                    }
                }
            }
        }
    }
}

void upscale_xbrz_edge(const uint32_t* src, int sw, int sh, int scale, uint32_t* dst, bool mmpx_sharp) {
    const int dw = sw * scale;
    const int thresh = mmpx_sharp ? 44 : 84;
    for (int y = 0; y < sh; ++y) {
        for (int x = 0; x < sw; ++x) {
            uint32_t A = sample_clamped(src, sw, sh, x - 1, y - 1);
            uint32_t B = sample_clamped(src, sw, sh, x,     y - 1);
            uint32_t C = sample_clamped(src, sw, sh, x + 1, y - 1);
            uint32_t D = sample_clamped(src, sw, sh, x - 1, y);
            uint32_t E = sample_clamped(src, sw, sh, x,     y);
            uint32_t F = sample_clamped(src, sw, sh, x + 1, y);
            uint32_t G = sample_clamped(src, sw, sh, x - 1, y + 1);
            uint32_t H = sample_clamped(src, sw, sh, x,     y + 1);
            uint32_t I = sample_clamped(src, sw, sh, x + 1, y + 1);

            bool diagNW = similar(B, D, thresh) && !similar(B, E, thresh) && !similar(D, E, thresh) &&
                          (!similar(B, H, thresh) || !similar(D, F, thresh));
            bool diagNE = similar(B, F, thresh) && !similar(B, E, thresh) && !similar(F, E, thresh) &&
                          (!similar(B, H, thresh) || !similar(D, F, thresh));
            bool diagSW = similar(H, D, thresh) && !similar(H, E, thresh) && !similar(D, E, thresh) &&
                          (!similar(B, H, thresh) || !similar(D, F, thresh));
            bool diagSE = similar(H, F, thresh) && !similar(H, E, thresh) && !similar(F, E, thresh) &&
                          (!similar(B, H, thresh) || !similar(D, F, thresh));

            // Suppress single-pixel checkerboard distortion
            if (mmpx_sharp) {
                if (similar(A, E, thresh) && similar(C, E, thresh) && similar(G, E, thresh) && similar(I, E, thresh)) {
                    diagNW = diagNE = diagSW = diagSE = false;
                }
            }

            uint32_t nwCol = blend_rgba(B, D, 128);
            uint32_t neCol = blend_rgba(B, F, 128);
            uint32_t swCol = blend_rgba(H, D, 128);
            uint32_t seCol = blend_rgba(H, F, 128);

            for (int sy = 0; sy < scale; ++sy) {
                float fy = (static_cast<float>(sy) + 0.5f) / static_cast<float>(scale);
                uint32_t* drow = dst + (y * scale + sy) * dw + x * scale;
                for (int sx = 0; sx < scale; ++sx) {
                    float fx = (static_cast<float>(sx) + 0.5f) / static_cast<float>(scale);
                    uint32_t outc = E;
                    if (diagNW && (fx + fy) < 0.72f) {
                        float t = std::clamp((0.72f - (fx + fy)) / 0.42f, 0.0f, 1.0f);
                        outc = blend_rgba(E, nwCol, static_cast<int>(t * 256.0f));
                    } else if (diagNE && ((1.0f - fx) + fy) < 0.72f) {
                        float t = std::clamp((0.72f - ((1.0f - fx) + fy)) / 0.42f, 0.0f, 1.0f);
                        outc = blend_rgba(E, neCol, static_cast<int>(t * 256.0f));
                    } else if (diagSW && (fx + (1.0f - fy)) < 0.72f) {
                        float t = std::clamp((0.72f - (fx + (1.0f - fy))) / 0.42f, 0.0f, 1.0f);
                        outc = blend_rgba(E, swCol, static_cast<int>(t * 256.0f));
                    } else if (diagSE && ((1.0f - fx) + (1.0f - fy)) < 0.72f) {
                        float t = std::clamp((0.72f - ((1.0f - fx) + (1.0f - fy))) / 0.42f, 0.0f, 1.0f);
                        outc = blend_rgba(E, seCol, static_cast<int>(t * 256.0f));
                    }
                    drow[sx] = outc;
                }
            }
        }
    }
}

void upscale_lcd_hd(const uint32_t* src, int sw, int sh, int scale, uint32_t* dst) {
    const int dw = sw * scale;
    for (int y = 0; y < sh; ++y) {
        const uint32_t* srow = src + y * sw;
        for (int sy = 0; sy < scale; ++sy) {
            bool y_gap = (scale >= 2 && sy == scale - 1);
            int y_mul = y_gap ? 204 : 264;
            uint32_t* drow = dst + (y * scale + sy) * dw;
            for (int x = 0; x < sw; ++x) {
                uint32_t c = srow[x];
                for (int sx = 0; sx < scale; ++sx) {
                    int mr = y_mul, mg = y_mul, mb = y_mul;
                    if (scale == 3) {
                        if (sx == 0) { mr = (y_mul * 276) >> 8; mg = (y_mul * 236) >> 8; mb = (y_mul * 224) >> 8; }
                        else if (sx == 1) { mr = (y_mul * 232) >> 8; mg = (y_mul * 276) >> 8; mb = (y_mul * 232) >> 8; }
                        else { mr = (y_mul * 224) >> 8; mg = (y_mul * 236) >> 8; mb = (y_mul * 276) >> 8; }
                    } else if (scale == 4) {
                        if (sx == 0) { mr = (y_mul * 278) >> 8; mg = (y_mul * 232) >> 8; mb = (y_mul * 220) >> 8; }
                        else if (sx == 1) { mr = (y_mul * 232) >> 8; mg = (y_mul * 278) >> 8; mb = (y_mul * 232) >> 8; }
                        else if (sx == 2) { mr = (y_mul * 220) >> 8; mg = (y_mul * 232) >> 8; mb = (y_mul * 278) >> 8; }
                        else { mr = (y_mul * 196) >> 8; mg = (y_mul * 196) >> 8; mb = (y_mul * 196) >> 8; }
                    } else if (scale == 2 && sx == 1) {
                        mr = (y_mul * 228) >> 8; mg = (y_mul * 228) >> 8; mb = (y_mul * 228) >> 8;
                    }
                    drow[x * scale + sx] = scale_rgb_mul(c, mr, mg, mb);
                }
            }
        }
    }
}

void upscale_crt_hd(const uint32_t* src, int sw, int sh, int scale, uint32_t* dst) {
    const int dw = sw * scale;
    for (int y = 0; y < sh; ++y) {
        const uint32_t* srow = src + y * sw;
        for (int sy = 0; sy < scale; ++sy) {
            int scan_mul = 256;
            if (scale == 2) scan_mul = (sy == 0) ? 276 : 178;
            else if (scale == 3) scan_mul = (sy == 1) ? 280 : (sy == 0 ? 236 : 162);
            else if (scale == 4) scan_mul = (sy == 1 || sy == 2) ? 278 : (sy == 0 ? 210 : 148);
            uint32_t* drow = dst + (y * scale + sy) * dw;
            for (int x = 0; x < sw; ++x) {
                uint32_t c = srow[x];
                int lum = y_luma(c);
                int dyn_mul = scan_mul + ((lum * 24) >> 8);
                uint32_t sc = scale_rgb_mul(c, dyn_mul, dyn_mul, dyn_mul);
                for (int sx = 0; sx < scale; ++sx) {
                    drow[x * scale + sx] = sc;
                }
            }
        }
    }
}

} // namespace

void upscale_rgba_frame(const uint32_t* src, int sw, int sh,
                        int scale, int mode,
                        std::vector<uint32_t>& out, int& out_w, int& out_h) {
    scale = std::clamp(scale, 1, 4);
    out_w = sw * scale;
    out_h = sh * scale;
    out.resize(static_cast<size_t>(out_w) * static_cast<size_t>(out_h));
    if (scale <= 1) {
        std::memcpy(out.data(), src, out.size() * sizeof(uint32_t));
        return;
    }
    switch (mode) {
        case STORM_UPSCALE_XBRZ:
            upscale_xbrz_edge(src, sw, sh, scale, out.data(), false);
            break;
        case STORM_UPSCALE_SCALE_EPX:
            upscale_epx_scale(src, sw, sh, scale, out.data());
            break;
        case STORM_UPSCALE_MMPX:
            upscale_xbrz_edge(src, sw, sh, scale, out.data(), true);
            break;
        case STORM_UPSCALE_LCD_HD:
            upscale_lcd_hd(src, sw, sh, scale, out.data());
            break;
        case STORM_UPSCALE_CRT_HD:
            upscale_crt_hd(src, sw, sh, scale, out.data());
            break;
        case STORM_UPSCALE_NONE:
        default:
            upscale_nearest(src, sw, sh, scale, out.data());
            break;
    }
}

} // namespace storm
