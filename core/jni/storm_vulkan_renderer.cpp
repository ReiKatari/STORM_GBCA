#ifdef ANDROID

#define VK_USE_PLATFORM_ANDROID_KHR 1
#define VK_NO_PROTOTYPES 1
#include <vulkan/vulkan.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include "storm_postprocess.hpp"

#include <algorithm>
#include <cmath>
#include <cstdio>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "StormVulkan", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "StormVulkan", __VA_ARGS__)

namespace storm {

extern bool librashader_has_preset();
extern bool librashader_apply_vk(VkInstance instance, VkPhysicalDevice phys, VkDevice device, VkQueue queue,
                                 PFN_vkGetInstanceProcAddr vk_entry, VkCommandBuffer cmd,
                                 VkImage src_img, VkFormat src_fmt, uint32_t src_w, uint32_t src_h,
                                 VkImage dst_img, VkFormat dst_fmt, uint32_t dst_w, uint32_t dst_h,
                                 uint32_t frames_in_flight, int rotation_deg);
extern void librashader_reset_vk();

namespace {

static std::string format_vk_version(uint32_t ver) {
    uint32_t major = (ver >> 22) & 0x7Fu;
    uint32_t minor = (ver >> 12) & 0x3FFu;
    uint32_t patch = ver & 0xFFFu;
    char buf[32];
    std::snprintf(buf, sizeof(buf), "%u.%u.%u", major, minor, patch);
    return buf;
}

struct RgbF {
    float r, g, b;
};

static inline RgbF unpack_rgba(uint32_t px) {
    return {
        static_cast<float>(px & 0xFFu) * (1.0f / 255.0f),
        static_cast<float>((px >> 8) & 0xFFu) * (1.0f / 255.0f),
        static_cast<float>((px >> 16) & 0xFFu) * (1.0f / 255.0f),
    };
}

static inline uint32_t pack_rgba(float r, float g, float b) {
    uint32_t ri = static_cast<uint32_t>(std::clamp(r * 255.0f + 0.5f, 0.0f, 255.0f));
    uint32_t gi = static_cast<uint32_t>(std::clamp(g * 255.0f + 0.5f, 0.0f, 255.0f));
    uint32_t bi = static_cast<uint32_t>(std::clamp(b * 255.0f + 0.5f, 0.0f, 255.0f));
    return ri | (gi << 8) | (bi << 16) | 0xFF000000u;
}

static inline float rgb_luma(const RgbF& c) {
    return 0.299f * c.r + 0.587f * c.g + 0.114f * c.b;
}

static inline float fsr_luma(const RgbF& c) {
    return 0.5f * c.g + 0.25f * (c.r + c.b);
}

static RgbF sample_bilinear_rgba(const uint32_t* img, int w, int h, float fx, float fy) {
    fx = std::clamp(fx, 0.0f, static_cast<float>(w - 1));
    fy = std::clamp(fy, 0.0f, static_cast<float>(h - 1));
    int x0 = static_cast<int>(fx);
    int y0 = static_cast<int>(fy);
    int x1 = std::min(x0 + 1, w - 1);
    int y1 = std::min(y0 + 1, h - 1);
    float tx = fx - static_cast<float>(x0);
    float ty = fy - static_cast<float>(y0);
    RgbF c00 = unpack_rgba(img[y0 * w + x0]);
    RgbF c10 = unpack_rgba(img[y0 * w + x1]);
    RgbF c01 = unpack_rgba(img[y1 * w + x0]);
    RgbF c11 = unpack_rgba(img[y1 * w + x1]);
    float w00 = (1.0f - tx) * (1.0f - ty);
    float w10 = tx * (1.0f - ty);
    float w01 = (1.0f - tx) * ty;
    float w11 = tx * ty;
    return {
        c00.r * w00 + c10.r * w10 + c01.r * w01 + c11.r * w11,
        c00.g * w00 + c10.g * w10 + c01.g * w01 + c11.g * w11,
        c00.b * w00 + c10.b * w10 + c01.b * w01 + c11.b * w11,
    };
}

static void synthesize_optical_flow_midpoint(
    const uint32_t* prev_rgba, const uint32_t* cur_rgba, int w, int h, float alpha,
    std::vector<float>& l0_buf, std::vector<float>& l1_buf, std::vector<uint32_t>& out_rgba
) {
    const size_t count = static_cast<size_t>(w) * static_cast<size_t>(h);
    l0_buf.resize(count);
    l1_buf.resize(count);
    out_rgba.resize(count);

    float scene_diff_sum = 0.0f;
    size_t scene_samples = 0;
    for (size_t i = 0; i < count; ++i) {
        float l0 = rgb_luma(unpack_rgba(prev_rgba[i]));
        float l1 = rgb_luma(unpack_rgba(cur_rgba[i]));
        l0_buf[i] = l0;
        l1_buf[i] = l1;
        if ((i & 3u) == 0u) {
            scene_diff_sum += std::abs(l1 - l0);
            scene_samples++;
        }
    }

    const float avg_scene_diff = scene_samples > 0 ? (scene_diff_sum / static_cast<float>(scene_samples)) : 0.0f;
    if (avg_scene_diff > 0.35f) {
        // Hard scene transition: avoid motion smearing
        const uint32_t* src = (alpha >= 0.5f) ? cur_rgba : prev_rgba;
        std::memcpy(out_rgba.data(), src, count * 4);
        return;
    }

    auto at_l0 = [&](int x, int y) -> float {
        return l0_buf[std::clamp(y, 0, h - 1) * w + std::clamp(x, 0, w - 1)];
    };
    auto at_l1 = [&](int x, int y) -> float {
        return l1_buf[std::clamp(y, 0, h - 1) * w + std::clamp(x, 0, w - 1)];
    };

    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            float sum_ix2 = 0.0f, sum_iy2 = 0.0f, sum_ixiy = 0.0f;
            float sum_ixit = 0.0f, sum_iyit = 0.0f, sum_it_abs = 0.0f;
            for (int oy = -1; oy <= 1; ++oy) {
                for (int ox = -1; ox <= 1; ++ox) {
                    int sx = x + ox;
                    int sy = y + oy;
                    float ix = 0.25f * ((at_l0(sx + 1, sy) - at_l0(sx - 1, sy)) +
                                        (at_l1(sx + 1, sy) - at_l1(sx - 1, sy)));
                    float iy = 0.25f * ((at_l0(sx, sy + 1) - at_l0(sx, sy - 1)) +
                                        (at_l1(sx, sy + 1) - at_l1(sx, sy - 1)));
                    float it = at_l1(sx, sy) - at_l0(sx, sy);
                    sum_ix2 += ix * ix;
                    sum_iy2 += iy * iy;
                    sum_ixiy += ix * iy;
                    sum_ixit += ix * it;
                    sum_iyit += iy * it;
                    sum_it_abs += std::abs(it);
                }
            }

            float vx = 0.0f, vy = 0.0f;
            if (sum_it_abs > 0.025f) {
                float det = sum_ix2 * sum_iy2 - sum_ixiy * sum_ixiy + 0.0025f;
                vx = std::clamp((-sum_iy2 * sum_ixit + sum_ixiy * sum_iyit) / det, -4.0f, 4.0f);
                vy = std::clamp((-sum_ix2 * sum_iyit + sum_ixiy * sum_ixit) / det, -4.0f, 4.0f);
            }

            RgbF p0 = unpack_rgba(prev_rgba[y * w + x]);
            RgbF p1 = unpack_rgba(cur_rgba[y * w + x]);
            RgbF w0 = sample_bilinear_rgba(prev_rgba, w, h, static_cast<float>(x) - vx * alpha, static_cast<float>(y) - vy * alpha);
            RgbF w1 = sample_bilinear_rgba(cur_rgba,  w, h, static_cast<float>(x) + vx * (1.0f - alpha), static_cast<float>(y) + vy * (1.0f - alpha));

            float warped_sad = rgb_luma({std::abs(w0.r - w1.r), std::abs(w0.g - w1.g), std::abs(w0.b - w1.b)});
            float disocc = std::clamp((warped_sad - 0.12f) / 0.24f, 0.0f, 1.0f);
            float raw_sad = rgb_luma({std::abs(p0.r - p1.r), std::abs(p0.g - p1.g), std::abs(p0.b - p1.b)});
            float cut_t = std::clamp((raw_sad - 0.40f) / 0.25f, 0.0f, 1.0f);

            RgbF lin{
                p0.r + (p1.r - p0.r) * alpha,
                p0.g + (p1.g - p0.g) * alpha,
                p0.b + (p1.b - p0.b) * alpha,
            };
            RgbF wrp{
                w0.r + (w1.r - w0.r) * alpha,
                w0.g + (w1.g - w0.g) * alpha,
                w0.b + (w1.b - w0.b) * alpha,
            };
            RgbF res{
                wrp.r + (lin.r - wrp.r) * disocc,
                wrp.g + (lin.g - wrp.g) * disocc,
                wrp.b + (lin.b - wrp.b) * disocc,
            };
            const RgbF& hc = (alpha >= 0.5f) ? p1 : p0;
            res.r += (hc.r - res.r) * cut_t;
            res.g += (hc.g - res.g) * cut_t;
            res.b += (hc.b - res.b) * cut_t;
            out_rgba[y * w + x] = pack_rgba(res.r, res.g, res.b);
        }
    }
}

static inline void easu_set_cpu(float& dirX, float& dirY, float& len, float ppx, float ppy,
                                bool biS, bool biT, bool biU, bool biV,
                                float lA, float lB, float lC, float lD, float lE) {
    float w = 0.0f;
    if (biS) w = (1.0f - ppx) * (1.0f - ppy);
    if (biT) w = ppx * (1.0f - ppy);
    if (biU) w = (1.0f - ppx) * ppy;
    if (biV) w = ppx * ppy;

    float lenX = std::max(std::abs(lD - lC), std::abs(lC - lB));
    float dX = lD - lB;
    dirX += dX * w;
    float lx = std::clamp(std::abs(dX) / std::max(lenX, 1e-5f), 0.0f, 1.0f);
    len += (lx * lx) * w;

    float lenY = std::max(std::abs(lE - lC), std::abs(lC - lA));
    float dY = lE - lA;
    dirY += dY * w;
    float ly = std::clamp(std::abs(dY) / std::max(lenY, 1e-5f), 0.0f, 1.0f);
    len += (ly * ly) * w;
}

static inline void easu_tap_cpu(RgbF& aC, float& aW, float ox, float oy,
                                float dirX, float dirY, float len2X, float len2Y,
                                float lob, float clp, const RgbF& c) {
    float vx = (ox * dirX + oy * dirY) * len2X;
    float vy = (ox * (-dirY) + oy * dirX) * len2Y;
    float d2 = std::min(vx * vx + vy * vy, clp);
    float wB = 0.4f * d2 - 1.0f;
    float wA = lob * d2 - 1.0f;
    wB *= wB;
    wA *= wA;
    wB = (25.0f / 16.0f) * wB - (25.0f / 16.0f - 1.0f);
    float w = wB * wA;
    aC.r += c.r * w;
    aC.g += c.g * w;
    aC.b += c.b * w;
    aW += w;
}

static void run_rcas_cpu(const std::vector<RgbF>& stage, int w, int h, float sharpness, std::vector<uint32_t>& out_rgba) {
    out_rgba.resize(static_cast<size_t>(w) * static_cast<size_t>(h));
    auto at = [&](int x, int y) -> const RgbF& {
        return stage[static_cast<size_t>(std::clamp(y, 0, h - 1)) * w + std::clamp(x, 0, w - 1)];
    };
    const float sharp = std::clamp(sharpness, 0.0f, 1.0f);
    for (int y = 0; y < h; ++y) {
        for (int x = 0; x < w; ++x) {
            const RgbF& b = at(x, y - 1);
            const RgbF& d = at(x - 1, y);
            const RgbF& e = at(x, y);
            const RgbF& f = at(x + 1, y);
            const RgbF& h_px = at(x, y + 1);

            float bL = fsr_luma(b), dL = fsr_luma(d), eL = fsr_luma(e), fL = fsr_luma(f), hL = fsr_luma(h_px);
            float mnL = std::min({bL, dL, fL, hL});
            float mxL = std::max({bL, dL, fL, hL});
            float nz = 0.25f * (bL + dL + fL + hL) - eL;
            nz = std::clamp(std::abs(nz) / std::max(std::max(mxL, eL) - std::min(mnL, eL), 1e-5f), 0.0f, 1.0f);
            nz = -0.5f * nz + 1.0f;

            auto calc_lobe = [&](float bv, float dv, float ev, float fv, float hv) -> float {
                float mn4 = std::min({bv, dv, fv, hv});
                float mx4 = std::max({bv, dv, fv, hv});
                float hitMin = std::min(mn4, ev) / std::max(4.0f * mx4, 1e-5f);
                float hitMax = (1.0f - std::max(mx4, ev)) / std::max(4.0f * (1.0f - mn4), 1e-5f);
                return std::max(-0.1875f, -std::min(hitMin, hitMax));
            };

            float lobe = std::max({
                calc_lobe(b.r, d.r, e.r, f.r, h_px.r),
                calc_lobe(b.g, d.g, e.g, f.g, h_px.g),
                calc_lobe(b.b, d.b, e.b, f.b, h_px.b)
            });
            lobe *= sharp * nz;
            float rcpL = 1.0f / (4.0f * lobe + 1.0f);
            out_rgba[static_cast<size_t>(y) * w + x] = pack_rgba(
                (lobe * (b.r + d.r + f.r + h_px.r) + e.r) * rcpL,
                (lobe * (b.g + d.g + f.g + h_px.g) + e.g) * rcpL,
                (lobe * (b.b + d.b + f.b + h_px.b) + e.b) * rcpL
            );
        }
    }
}

static void run_fsr1_cpu(const uint32_t* src, int sw, int sh, int dw, int dh, float sharpness,
                         std::vector<RgbF>& stage, std::vector<uint32_t>& out_rgba) {
    stage.resize(static_cast<size_t>(dw) * static_cast<size_t>(dh));
    auto at_src = [&](int x, int y) -> RgbF {
        return unpack_rgba(src[std::clamp(y, 0, sh - 1) * sw + std::clamp(x, 0, sw - 1)]);
    };
    const float sx = static_cast<float>(sw) / static_cast<float>(dw);
    const float sy = static_cast<float>(sh) / static_cast<float>(dh);

    for (int y = 0; y < dh; ++y) {
        float py = (static_cast<float>(y) + 0.5f) * sy - 0.5f;
        int iy = static_cast<int>(std::floor(py));
        float fpy = py - static_cast<float>(iy);
        for (int x = 0; x < dw; ++x) {
            float px = (static_cast<float>(x) + 0.5f) * sx - 0.5f;
            int ix = static_cast<int>(std::floor(px));
            float fpx = px - static_cast<float>(ix);

            RgbF b = at_src(ix + 0, iy - 1), c = at_src(ix + 1, iy - 1);
            RgbF e = at_src(ix - 1, iy + 0), f = at_src(ix + 0, iy + 0), g = at_src(ix + 1, iy + 0), h = at_src(ix + 2, iy + 0);
            RgbF i = at_src(ix - 1, iy + 1), j = at_src(ix + 0, iy + 1), k = at_src(ix + 1, iy + 1), l = at_src(ix + 2, iy + 1);
            RgbF n = at_src(ix + 0, iy + 2), o = at_src(ix + 1, iy + 2);

            float bL = fsr_luma(b), cL = fsr_luma(c);
            float eL = fsr_luma(e), fL = fsr_luma(f), gL = fsr_luma(g), hL = fsr_luma(h);
            float iL = fsr_luma(i), jL = fsr_luma(j), kL = fsr_luma(k), lL = fsr_luma(l);
            float nL = fsr_luma(n), oL = fsr_luma(o);

            float dirX = 0.0f, dirY = 0.0f, len = 0.0f;
            easu_set_cpu(dirX, dirY, len, fpx, fpy, true,  false, false, false, bL, eL, fL, gL, jL);
            easu_set_cpu(dirX, dirY, len, fpx, fpy, false, true,  false, false, cL, fL, gL, hL, kL);
            easu_set_cpu(dirX, dirY, len, fpx, fpy, false, false, true,  false, fL, iL, jL, kL, nL);
            easu_set_cpu(dirX, dirY, len, fpx, fpy, false, false, false, true,  gL, jL, kL, lL, oL);

            float dirR = dirX * dirX + dirY * dirY;
            if (dirR < (1.0f / 32768.0f)) {
                dirX = 1.0f; dirY = 0.0f;
            } else {
                float inv = 1.0f / std::sqrt(dirR);
                dirX *= inv; dirY *= inv;
            }
            len = len * 0.5f;
            len *= len;
            float stretch = 1.0f / std::max(std::abs(dirX), std::abs(dirY));
            float len2X = 1.0f + (stretch - 1.0f) * len;
            float len2Y = 1.0f - 0.5f * len;
            float lob = 0.5f + (0.29f - 0.5f) * len;
            float clp = 1.0f / lob;

            RgbF min4{ std::min({f.r, g.r, j.r, k.r}), std::min({f.g, g.g, j.g, k.g}), std::min({f.b, g.b, j.b, k.b}) };
            RgbF max4{ std::max({f.r, g.r, j.r, k.r}), std::max({f.g, g.g, j.g, k.g}), std::max({f.b, g.b, j.b, k.b}) };

            RgbF aC{0.0f, 0.0f, 0.0f};
            float aW = 0.0f;
            easu_tap_cpu(aC, aW,  0.0f - fpx, -1.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, b);
            easu_tap_cpu(aC, aW,  1.0f - fpx, -1.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, c);
            easu_tap_cpu(aC, aW, -1.0f - fpx,  0.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, e);
            easu_tap_cpu(aC, aW,  0.0f - fpx,  0.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, f);
            easu_tap_cpu(aC, aW,  1.0f - fpx,  0.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, g);
            easu_tap_cpu(aC, aW,  2.0f - fpx,  0.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, h);
            easu_tap_cpu(aC, aW, -1.0f - fpx,  1.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, i);
            easu_tap_cpu(aC, aW,  0.0f - fpx,  1.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, j);
            easu_tap_cpu(aC, aW,  1.0f - fpx,  1.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, k);
            easu_tap_cpu(aC, aW,  2.0f - fpx,  1.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, l);
            easu_tap_cpu(aC, aW,  0.0f - fpx,  2.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, n);
            easu_tap_cpu(aC, aW,  1.0f - fpx,  2.0f - fpy, dirX, dirY, len2X, len2Y, lob, clp, o);

            float invW = 1.0f / std::max(aW, 1e-5f);
            stage[static_cast<size_t>(y) * dw + x] = {
                std::clamp(aC.r * invW, min4.r, max4.r),
                std::clamp(aC.g * invW, min4.g, max4.g),
                std::clamp(aC.b * invW, min4.b, max4.b)
            };
        }
    }
    run_rcas_cpu(stage, dw, dh, sharpness, out_rgba);
}

static float halton_cpu(int index, int base) {
    float f = 1.0f, r = 0.0f;
    int i = index;
    while (i > 0) {
        f /= static_cast<float>(base);
        r += f * static_cast<float>(i % base);
        i /= base;
    }
    return r;
}

static void run_fsr3_cpu(const uint32_t* src, int sw, int sh, int dw, int dh, float sharpness,
                         int frame_idx, bool& history_valid,
                         std::vector<RgbF>& history_stage, std::vector<RgbF>& work_stage,
                         std::vector<uint32_t>& out_rgba) {
    work_stage.resize(static_cast<size_t>(dw) * static_cast<size_t>(dh));
    if (history_stage.size() != work_stage.size()) {
        history_stage.assign(work_stage.size(), {0.0f, 0.0f, 0.0f});
        history_valid = false;
    }

    auto at_src = [&](int x, int y) -> RgbF {
        return unpack_rgba(src[std::clamp(y, 0, sh - 1) * sw + std::clamp(x, 0, sw - 1)]);
    };
    auto catmull = [&](float t, float& w0, float& w1, float& w2, float& w3) {
        float t2 = t * t, t3 = t2 * t;
        w0 = -0.5f * t + t2 - 0.5f * t3;
        w1 = 1.0f - 2.5f * t2 + 1.5f * t3;
        w2 = 0.5f * t + 2.0f * t2 - 1.5f * t3;
        w3 = -0.5f * t2 + 0.5f * t3;
    };

    const float jx = (halton_cpu(frame_idx + 1, 2) - 0.5f) * 0.5f;
    const float jy = (halton_cpu(frame_idx + 1, 3) - 0.5f) * 0.5f;
    const float sx = static_cast<float>(sw) / static_cast<float>(dw);
    const float sy = static_cast<float>(sh) / static_cast<float>(dh);

    for (int y = 0; y < dh; ++y) {
        float src_y = (static_cast<float>(y) + 0.5f) * sy - 0.5f + jy;
        int iy = static_cast<int>(std::floor(src_y));
        float fy = src_y - static_cast<float>(iy);
        float wy[4];
        catmull(fy, wy[0], wy[1], wy[2], wy[3]);

        for (int x = 0; x < dw; ++x) {
            float src_x = (static_cast<float>(x) + 0.5f) * sx - 0.5f + jx;
            int ix = static_cast<int>(std::floor(src_x));
            float fx = src_x - static_cast<float>(ix);
            float wx[4];
            catmull(fx, wx[0], wx[1], wx[2], wx[3]);

            RgbF cur{0.0f, 0.0f, 0.0f};
            RgbF nmin{1.0f, 1.0f, 1.0f}, nmax{0.0f, 0.0f, 0.0f};
            for (int j = -1; j <= 2; ++j) {
                RgbF row{0.0f, 0.0f, 0.0f};
                for (int i = -1; i <= 2; ++i) {
                    RgbF s = at_src(ix + i, iy + j);
                    float weight_x = wx[i + 1];
                    row.r += s.r * weight_x;
                    row.g += s.g * weight_x;
                    row.b += s.b * weight_x;
                    if (j >= 0 && j <= 1 && i >= 0 && i <= 1) {
                        nmin.r = std::min(nmin.r, s.r); nmin.g = std::min(nmin.g, s.g); nmin.b = std::min(nmin.b, s.b);
                        nmax.r = std::max(nmax.r, s.r); nmax.g = std::max(nmax.g, s.g); nmax.b = std::max(nmax.b, s.b);
                    }
                }
                float weight_y = wy[j + 1];
                cur.r += row.r * weight_y;
                cur.g += row.g * weight_y;
                cur.b += row.b * weight_y;
            }
            cur.r = std::clamp(cur.r, nmin.r, nmax.r);
            cur.g = std::clamp(cur.g, nmin.g, nmax.g);
            cur.b = std::clamp(cur.b, nmin.b, nmax.b);

            if (!history_valid) {
                work_stage[static_cast<size_t>(y) * dw + x] = cur;
                continue;
            }

            // 3x3 YCoCg variance clip (gamma = 1.25)
            float m1y = 0, m1co = 0, m1cg = 0, m2y = 0, m2co = 0, m2cg = 0;
            int cx = std::clamp(static_cast<int>(std::round(src_x)), 0, sw - 1);
            int cy = std::clamp(static_cast<int>(std::round(src_y)), 0, sh - 1);
            for (int oy = -1; oy <= 1; ++oy) {
                for (int ox = -1; ox <= 1; ++ox) {
                    RgbF s = at_src(cx + ox, cy + oy);
                    float yv = 0.25f * s.r + 0.5f * s.g + 0.25f * s.b;
                    float co = 0.5f * s.r - 0.5f * s.b;
                    float cg = -0.25f * s.r + 0.5f * s.g - 0.25f * s.b;
                    m1y += yv; m1co += co; m1cg += cg;
                    m2y += yv * yv; m2co += co * co; m2cg += cg * cg;
                }
            }
            float muy = m1y / 9.0f, muco = m1co / 9.0f, mucg = m1cg / 9.0f;
            float sy_v = std::sqrt(std::max(0.0f, m2y / 9.0f - muy * muy)) * 1.25f;
            float sco_v = std::sqrt(std::max(0.0f, m2co / 9.0f - muco * muco)) * 1.25f;
            float scg_v = std::sqrt(std::max(0.0f, m2cg / 9.0f - mucg * mucg)) * 1.25f;

            const RgbF& h_px = history_stage[static_cast<size_t>(y) * dw + x];
            float hy = 0.25f * h_px.r + 0.5f * h_px.g + 0.25f * h_px.b;
            float hco = 0.5f * h_px.r - 0.5f * h_px.b;
            float hcg = -0.25f * h_px.r + 0.5f * h_px.g - 0.25f * h_px.b;

            float vy = hy - muy, vco = hco - muco, vcg = hcg - mucg;
            float ma = std::max({
                std::abs(vy) / (sy_v + 1e-5f),
                std::abs(vco) / (sco_v + 1e-5f),
                std::abs(vcg) / (scg_v + 1e-5f)
            });
            if (ma > 1.0f) {
                hy = muy + vy / ma;
                hco = muco + vco / ma;
                hcg = mucg + vcg / ma;
            }
            float tmp = hy - hcg;
            RgbF clipped{
                std::clamp(tmp + hco, 0.0f, 1.0f),
                std::clamp(hy + hcg, 0.0f, 1.0f),
                std::clamp(tmp - hco, 0.0f, 1.0f)
            };

            float disocc = rgb_luma({std::abs(cur.r - h_px.r), std::abs(cur.g - h_px.g), std::abs(cur.b - h_px.b)});
            float hw = 0.72f * (1.0f - std::clamp((disocc - 0.08f) / 0.27f, 0.0f, 1.0f));
            work_stage[static_cast<size_t>(y) * dw + x] = {
                cur.r + (clipped.r - cur.r) * hw,
                cur.g + (clipped.g - cur.g) * hw,
                cur.b + (clipped.b - cur.b) * hw
            };
        }
    }

    run_rcas_cpu(work_stage, dw, dh, sharpness, out_rgba);
    history_stage.swap(work_stage);
    history_valid = true;
}

struct VkFuncs {
    void* lib = nullptr;
    PFN_vkGetInstanceProcAddr getInstanceProcAddr = nullptr;
    PFN_vkCreateInstance createInstance = nullptr;
    PFN_vkDestroyInstance destroyInstance = nullptr;
    PFN_vkEnumeratePhysicalDevices enumeratePhysicalDevices = nullptr;
    PFN_vkGetPhysicalDeviceProperties getPhysicalDeviceProperties = nullptr;
    PFN_vkGetPhysicalDeviceMemoryProperties getPhysicalDeviceMemoryProperties = nullptr;
    PFN_vkGetPhysicalDeviceQueueFamilyProperties getPhysicalDeviceQueueFamilyProperties = nullptr;
    PFN_vkCreateDevice createDevice = nullptr;
    PFN_vkDestroyDevice destroyDevice = nullptr;
    PFN_vkGetDeviceQueue getDeviceQueue = nullptr;
    PFN_vkDeviceWaitIdle deviceWaitIdle = nullptr;
    PFN_vkCreateAndroidSurfaceKHR createAndroidSurfaceKHR = nullptr;
    PFN_vkDestroySurfaceKHR destroySurfaceKHR = nullptr;
    PFN_vkGetPhysicalDeviceSurfaceSupportKHR getPhysicalDeviceSurfaceSupportKHR = nullptr;
    PFN_vkGetPhysicalDeviceSurfaceCapabilitiesKHR getPhysicalDeviceSurfaceCapabilitiesKHR = nullptr;
    PFN_vkGetPhysicalDeviceSurfaceFormatsKHR getPhysicalDeviceSurfaceFormatsKHR = nullptr;
    PFN_vkCreateSwapchainKHR createSwapchainKHR = nullptr;
    PFN_vkDestroySwapchainKHR destroySwapchainKHR = nullptr;
    PFN_vkGetSwapchainImagesKHR getSwapchainImagesKHR = nullptr;
    PFN_vkAcquireNextImageKHR acquireNextImageKHR = nullptr;
    PFN_vkQueuePresentKHR queuePresentKHR = nullptr;
    PFN_vkCreateCommandPool createCommandPool = nullptr;
    PFN_vkDestroyCommandPool destroyCommandPool = nullptr;
    PFN_vkAllocateCommandBuffers allocateCommandBuffers = nullptr;
    PFN_vkResetCommandBuffer resetCommandBuffer = nullptr;
    PFN_vkBeginCommandBuffer beginCommandBuffer = nullptr;
    PFN_vkEndCommandBuffer endCommandBuffer = nullptr;
    PFN_vkQueueSubmit queueSubmit = nullptr;
    PFN_vkCreateSemaphore createSemaphore = nullptr;
    PFN_vkDestroySemaphore destroySemaphore = nullptr;
    PFN_vkCreateFence createFence = nullptr;
    PFN_vkDestroyFence destroyFence = nullptr;
    PFN_vkWaitForFences waitForFences = nullptr;
    PFN_vkResetFences resetFences = nullptr;
    PFN_vkCreateBuffer createBuffer = nullptr;
    PFN_vkDestroyBuffer destroyBuffer = nullptr;
    PFN_vkGetBufferMemoryRequirements getBufferMemoryRequirements = nullptr;
    PFN_vkBindBufferMemory bindBufferMemory = nullptr;
    PFN_vkCreateImage createImage = nullptr;
    PFN_vkDestroyImage destroyImage = nullptr;
    PFN_vkGetImageMemoryRequirements getImageMemoryRequirements = nullptr;
    PFN_vkBindImageMemory bindImageMemory = nullptr;
    PFN_vkAllocateMemory allocateMemory = nullptr;
    PFN_vkFreeMemory freeMemory = nullptr;
    PFN_vkMapMemory mapMemory = nullptr;
    PFN_vkUnmapMemory unmapMemory = nullptr;
    PFN_vkCmdPipelineBarrier cmdPipelineBarrier = nullptr;
    PFN_vkCmdCopyBufferToImage cmdCopyBufferToImage = nullptr;
    PFN_vkCmdBlitImage cmdBlitImage = nullptr;
    PFN_vkCmdClearColorImage cmdClearColorImage = nullptr;

    bool load_library(const std::string& custom_driver_path) {
        if (lib) {
            dlclose(lib);
            lib = nullptr;
        }
        if (!custom_driver_path.empty()) {
            lib = dlopen(custom_driver_path.c_str(), RTLD_NOW | RTLD_LOCAL);
            if (lib) LOGI("Loaded custom Vulkan driver: %s", custom_driver_path.c_str());
        }
        if (!lib) {
            lib = dlopen("libvulkan.so", RTLD_NOW | RTLD_LOCAL);
        }
        if (!lib) {
            LOGE("Failed to dlopen libvulkan.so");
            return false;
        }
        getInstanceProcAddr = reinterpret_cast<PFN_vkGetInstanceProcAddr>(dlsym(lib, "vkGetInstanceProcAddr"));
        if (!getInstanceProcAddr) return false;
        createInstance = reinterpret_cast<PFN_vkCreateInstance>(getInstanceProcAddr(VK_NULL_HANDLE, "vkCreateInstance"));
        return createInstance != nullptr;
    }

    uint32_t query_instance_version() const {
        if (!getInstanceProcAddr) return VK_API_VERSION_1_1;
        auto fn = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
            getInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
        uint32_t ver = VK_API_VERSION_1_1;
        if (fn && fn(&ver) == VK_SUCCESS && ver >= VK_API_VERSION_1_0) {
            return ver;
        }
        return VK_API_VERSION_1_1;
    }

    bool load_instance(VkInstance inst) {
        auto g = [&](auto& fn, const char* name) {
            fn = reinterpret_cast<std::decay_t<decltype(fn)>>(getInstanceProcAddr(inst, name));
        };
        g(destroyInstance, "vkDestroyInstance");
        g(enumeratePhysicalDevices, "vkEnumeratePhysicalDevices");
        g(getPhysicalDeviceProperties, "vkGetPhysicalDeviceProperties");
        g(getPhysicalDeviceMemoryProperties, "vkGetPhysicalDeviceMemoryProperties");
        g(getPhysicalDeviceQueueFamilyProperties, "vkGetPhysicalDeviceQueueFamilyProperties");
        g(createDevice, "vkCreateDevice");
        g(destroyDevice, "vkDestroyDevice");
        g(getDeviceQueue, "vkGetDeviceQueue");
        g(deviceWaitIdle, "vkDeviceWaitIdle");
        g(createAndroidSurfaceKHR, "vkCreateAndroidSurfaceKHR");
        g(destroySurfaceKHR, "vkDestroySurfaceKHR");
        g(getPhysicalDeviceSurfaceSupportKHR, "vkGetPhysicalDeviceSurfaceSupportKHR");
        g(getPhysicalDeviceSurfaceCapabilitiesKHR, "vkGetPhysicalDeviceSurfaceCapabilitiesKHR");
        g(getPhysicalDeviceSurfaceFormatsKHR, "vkGetPhysicalDeviceSurfaceFormatsKHR");
        g(createSwapchainKHR, "vkCreateSwapchainKHR");
        g(destroySwapchainKHR, "vkDestroySwapchainKHR");
        g(getSwapchainImagesKHR, "vkGetSwapchainImagesKHR");
        g(acquireNextImageKHR, "vkAcquireNextImageKHR");
        g(queuePresentKHR, "vkQueuePresentKHR");
        g(createCommandPool, "vkCreateCommandPool");
        g(destroyCommandPool, "vkDestroyCommandPool");
        g(allocateCommandBuffers, "vkAllocateCommandBuffers");
        g(resetCommandBuffer, "vkResetCommandBuffer");
        g(beginCommandBuffer, "vkBeginCommandBuffer");
        g(endCommandBuffer, "vkEndCommandBuffer");
        g(queueSubmit, "vkQueueSubmit");
        g(createSemaphore, "vkCreateSemaphore");
        g(destroySemaphore, "vkDestroySemaphore");
        g(createFence, "vkCreateFence");
        g(destroyFence, "vkDestroyFence");
        g(waitForFences, "vkWaitForFences");
        g(resetFences, "vkResetFences");
        g(createBuffer, "vkCreateBuffer");
        g(destroyBuffer, "vkDestroyBuffer");
        g(getBufferMemoryRequirements, "vkGetBufferMemoryRequirements");
        g(bindBufferMemory, "vkBindBufferMemory");
        g(createImage, "vkCreateImage");
        g(destroyImage, "vkDestroyImage");
        g(getImageMemoryRequirements, "vkGetImageMemoryRequirements");
        g(bindImageMemory, "vkBindImageMemory");
        g(allocateMemory, "vkAllocateMemory");
        g(freeMemory, "vkFreeMemory");
        g(mapMemory, "vkMapMemory");
        g(unmapMemory, "vkUnmapMemory");
        g(cmdPipelineBarrier, "vkCmdPipelineBarrier");
        g(cmdCopyBufferToImage, "vkCmdCopyBufferToImage");
        g(cmdBlitImage, "vkCmdBlitImage");
        g(cmdClearColorImage, "vkCmdClearColorImage");
        return createDevice && createAndroidSurfaceKHR && createSwapchainKHR && cmdBlitImage;
    }
};

class VulkanRenderer {
public:
    bool probe_info(std::string& out_device_name, std::string& out_device_api_ver, std::string& out_instance_api_ver) {
        std::lock_guard<std::mutex> lock(mu_);
        if (!device_name_.empty() && !device_api_ver_.empty() && !instance_api_ver_.empty()) {
            out_device_name = device_name_;
            out_device_api_ver = device_api_ver_;
            out_instance_api_ver = instance_api_ver_;
            return true;
        }
        VkFuncs tmp{};
        if (!tmp.load_library("")) return false;
        uint32_t inst_ver = tmp.query_instance_version();
        std::string inst_ver_str = format_vk_version(inst_ver);
        uint32_t req_api = std::max<uint32_t>(VK_API_VERSION_1_1, std::min<uint32_t>(inst_ver, VK_MAKE_VERSION(1, 3, 0)));

        VkApplicationInfo app{};
        app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        app.pApplicationName = "STORM GBCA";
        app.apiVersion = req_api;
        const char* exts[] = { "VK_KHR_surface", "VK_KHR_android_surface" };
        VkInstanceCreateInfo ci{};
        ci.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        ci.pApplicationInfo = &app;
        ci.enabledExtensionCount = 2;
        ci.ppEnabledExtensionNames = exts;
        VkInstance inst = VK_NULL_HANDLE;
        VkResult cres = tmp.createInstance(&ci, nullptr, &inst);
        if (cres == VK_ERROR_INCOMPATIBLE_DRIVER) {
            app.apiVersion = VK_API_VERSION_1_0;
            cres = tmp.createInstance(&ci, nullptr, &inst);
        }
        if (cres != VK_SUCCESS) {
            if (tmp.lib) dlclose(tmp.lib);
            return false;
        }
        tmp.load_instance(inst);
        uint32_t count = 0;
        bool ok = false;
        if (tmp.enumeratePhysicalDevices && tmp.enumeratePhysicalDevices(inst, &count, nullptr) == VK_SUCCESS && count > 0) {
            std::vector<VkPhysicalDevice> devs(count);
            tmp.enumeratePhysicalDevices(inst, &count, devs.data());
            VkPhysicalDeviceProperties props{};
            tmp.getPhysicalDeviceProperties(devs[0], &props);
            device_name_ = props.deviceName;
            device_api_ver_ = format_vk_version(props.apiVersion);
            instance_api_ver_ = inst_ver_str;
            out_device_name = device_name_;
            out_device_api_ver = device_api_ver_;
            out_instance_api_ver = instance_api_ver_;
            ok = true;
        }
        if (tmp.destroyInstance) tmp.destroyInstance(inst, nullptr);
        if (tmp.lib) dlclose(tmp.lib);
        return ok;
    }

    bool init(ANativeWindow* window, const std::string& custom_driver_path) {
        std::lock_guard<std::mutex> lock(mu_);
        destroy_locked();
        if (!window) return false;
        window_ = window;
        ANativeWindow_acquire(window_);

        if (!vk_.load_library(custom_driver_path)) {
            destroy_locked();
            return false;
        }

        uint32_t inst_ver = vk_.query_instance_version();
        instance_api_ver_ = format_vk_version(inst_ver);
        uint32_t req_api = std::max<uint32_t>(VK_API_VERSION_1_1, std::min<uint32_t>(inst_ver, VK_MAKE_VERSION(1, 3, 0)));

        VkApplicationInfo app{};
        app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        app.pApplicationName = "STORM GBCA";
        app.applicationVersion = VK_MAKE_VERSION(1, 8, 0);
        app.pEngineName = "STORM GBCA Core";
        app.apiVersion = req_api;

        const char* inst_exts[] = { "VK_KHR_surface", "VK_KHR_android_surface" };
        VkInstanceCreateInfo ici{};
        ici.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        ici.pApplicationInfo = &app;
        ici.enabledExtensionCount = 2;
        ici.ppEnabledExtensionNames = inst_exts;

        VkResult cres = vk_.createInstance(&ici, nullptr, &instance_);
        if (cres == VK_ERROR_INCOMPATIBLE_DRIVER) {
            app.apiVersion = VK_API_VERSION_1_0;
            cres = vk_.createInstance(&ici, nullptr, &instance_);
        }
        if (cres != VK_SUCCESS) {
            destroy_locked();
            return false;
        }
        if (!vk_.load_instance(instance_)) {
            destroy_locked();
            return false;
        }

        VkAndroidSurfaceCreateInfoKHR sci{};
        sci.sType = VK_STRUCTURE_TYPE_ANDROID_SURFACE_CREATE_INFO_KHR;
        sci.window = window_;
        if (vk_.createAndroidSurfaceKHR(instance_, &sci, nullptr, &surface_) != VK_SUCCESS) {
            destroy_locked();
            return false;
        }

        uint32_t dev_count = 0;
        vk_.enumeratePhysicalDevices(instance_, &dev_count, nullptr);
        if (dev_count == 0) {
            destroy_locked();
            return false;
        }
        std::vector<VkPhysicalDevice> devs(dev_count);
        vk_.enumeratePhysicalDevices(instance_, &dev_count, devs.data());
        phys_ = devs[0];

        VkPhysicalDeviceProperties props{};
        vk_.getPhysicalDeviceProperties(phys_, &props);
        device_name_ = props.deviceName;
        device_api_ver_ = format_vk_version(props.apiVersion);
        vk_.getPhysicalDeviceMemoryProperties(phys_, &mem_props_);

        uint32_t qf_count = 0;
        vk_.getPhysicalDeviceQueueFamilyProperties(phys_, &qf_count, nullptr);
        std::vector<VkQueueFamilyProperties> qfs(qf_count);
        vk_.getPhysicalDeviceQueueFamilyProperties(phys_, &qf_count, qfs.data());
        queue_family_ = UINT32_MAX;
        for (uint32_t i = 0; i < qf_count; ++i) {
            VkBool32 present_ok = VK_FALSE;
            vk_.getPhysicalDeviceSurfaceSupportKHR(phys_, i, surface_, &present_ok);
            if ((qfs[i].queueFlags & VK_QUEUE_GRAPHICS_BIT) && present_ok) {
                queue_family_ = i;
                break;
            }
        }
        if (queue_family_ == UINT32_MAX) {
            destroy_locked();
            return false;
        }

        float prio = 1.0f;
        VkDeviceQueueCreateInfo qci{};
        qci.sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO;
        qci.queueFamilyIndex = queue_family_;
        qci.queueCount = 1;
        qci.pQueuePriorities = &prio;

        const char* dev_exts[] = { "VK_KHR_swapchain" };
        VkDeviceCreateInfo dci{};
        dci.sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO;
        dci.queueCreateInfoCount = 1;
        dci.pQueueCreateInfos = &qci;
        dci.enabledExtensionCount = 1;
        dci.ppEnabledExtensionNames = dev_exts;
        if (vk_.createDevice(phys_, &dci, nullptr, &device_) != VK_SUCCESS) {
            destroy_locked();
            return false;
        }
        vk_.getDeviceQueue(device_, queue_family_, 0, &queue_);

        VkCommandPoolCreateInfo cpci{};
        cpci.sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO;
        cpci.flags = VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT;
        cpci.queueFamilyIndex = queue_family_;
        if (vk_.createCommandPool(device_, &cpci, nullptr, &cmd_pool_) != VK_SUCCESS) {
            destroy_locked();
            return false;
        }

        VkCommandBufferAllocateInfo cbai{};
        cbai.sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO;
        cbai.commandPool = cmd_pool_;
        cbai.level = VK_COMMAND_BUFFER_LEVEL_PRIMARY;
        cbai.commandBufferCount = 1;
        if (vk_.allocateCommandBuffers(device_, &cbai, &cmd_buf_) != VK_SUCCESS) {
            destroy_locked();
            return false;
        }

        VkSemaphoreCreateInfo sem_ci{ VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO, nullptr, 0 };
        VkFenceCreateInfo fence_ci{ VK_STRUCTURE_TYPE_FENCE_CREATE_INFO, nullptr, VK_FENCE_CREATE_SIGNALED_BIT };
        vk_.createSemaphore(device_, &sem_ci, nullptr, &img_avail_sem_);
        vk_.createSemaphore(device_, &sem_ci, nullptr, &render_done_sem_);
        vk_.createFence(device_, &fence_ci, nullptr, &in_flight_fence_);

        if (!recreate_swapchain_locked()) {
            destroy_locked();
            return false;
        }
        ready_ = true;
        LOGI("Vulkan %s (instance %s) initialized on %s (%ux%u)",
             device_api_ver_.c_str(), instance_api_ver_.c_str(),
             device_name_.c_str(), swap_extent_.width, swap_extent_.height);
        return true;
    }

    void resize(int w, int h) {
        std::lock_guard<std::mutex> lock(mu_);
        if (!ready_ || w <= 0 || h <= 0) return;
        need_recreate_ = true;
    }

    void render(const uint32_t* rgba, int w, int h,
                int vp_x, int vp_y, int vp_w, int vp_h,
                bool linear_filter, int rotation_deg) {
        std::lock_guard<std::mutex> lock(mu_);
        if (!ready_ || !rgba || w <= 0 || h <= 0 || vp_w <= 0 || vp_h <= 0) return;

        if (need_recreate_) {
            if (!recreate_swapchain_locked()) return;
            need_recreate_ = false;
        }

        vk_.waitForFences(device_, 1, &in_flight_fence_, VK_TRUE, UINT64_MAX);
        uint32_t img_idx = 0;
        VkResult acq = vk_.acquireNextImageKHR(device_, swapchain_, UINT64_MAX, img_avail_sem_, VK_NULL_HANDLE, &img_idx);
        if (acq == VK_ERROR_OUT_OF_DATE_KHR) {
            need_recreate_ = true;
            return;
        }
        if (acq != VK_SUCCESS && acq != VK_SUBOPTIMAL_KHR) return;

        const PostProcessParams post = get_post_process_params();
        const bool fg_midpoint = post.frame_gen_enabled &&
                                 post.frame_gen_alpha > 0.01f &&
                                 post.frame_gen_alpha < 0.99f;

        const size_t raw_count = static_cast<size_t>(w) * static_cast<size_t>(h);
        if (!fg_midpoint || cached_w_ != w || cached_h_ != h || cur_frame_rgba_.size() != raw_count) {
            if (cached_w_ != w || cached_h_ != h || cur_frame_rgba_.size() != raw_count) {
                prev_frame_rgba_.assign(rgba, rgba + raw_count);
                cur_frame_rgba_.assign(rgba, rgba + raw_count);
                cached_w_ = w;
                cached_h_ = h;
                fsr3_history_valid_ = false;
                fsr3_frame_idx_ = 0;
            } else {
                prev_frame_rgba_.swap(cur_frame_rgba_);
                std::memcpy(cur_frame_rgba_.data(), rgba, raw_count * 4);
            }
        }

        const uint32_t* base_rgba = cur_frame_rgba_.data();
        if (fg_midpoint && prev_frame_rgba_.size() == raw_count && cur_frame_rgba_.size() == raw_count) {
            synthesize_optical_flow_midpoint(
                prev_frame_rgba_.data(), cur_frame_rgba_.data(), w, h, post.frame_gen_alpha,
                fg_luma0_, fg_luma1_, fg_frame_rgba_);
            base_rgba = fg_frame_rgba_.data();
        }

        int upload_w = w;
        int upload_h = h;
        const uint32_t* upload_rgba = base_rgba;
        bool native_fsr_used = false;

        if (!librashader_has_preset() && (post.fsr_mode == 1 || post.fsr_mode == 2)) {
            float sharp = std::clamp(post.rcas_sharpness + fsr_quality_sharpness_bias(post.fsr_quality), 0.0f, 1.0f);
            float mult = (post.fsr_quality <= 1) ? 2.0f : 1.5f;
            if (w > 320 || h > 240) mult = 1.5f;
            int target_w = std::max(w, static_cast<int>(std::lround(static_cast<float>(w) * mult)));
            int target_h = std::max(h, static_cast<int>(std::lround(static_cast<float>(h) * mult)));
            if (post.fsr_mode == 1) {
                run_fsr1_cpu(base_rgba, w, h, target_w, target_h, sharp, fsr_stage_, fsr_out_rgba_);
            } else {
                run_fsr3_cpu(base_rgba, w, h, target_w, target_h, sharp,
                             fsr3_frame_idx_, fsr3_history_valid_, fsr3_history_stage_, fsr_stage_, fsr_out_rgba_);
                fsr3_frame_idx_ = (fsr3_frame_idx_ + 1) & 31;
            }
            upload_w = target_w;
            upload_h = target_h;
            upload_rgba = fsr_out_rgba_.data();
            native_fsr_used = true;
        }

        if (!ensure_source_image_locked(static_cast<uint32_t>(upload_w), static_cast<uint32_t>(upload_h))) return;

        // Upload RGBA frame into persistently mapped host-visible staging buffer
        size_t bytes = static_cast<size_t>(upload_w) * static_cast<size_t>(upload_h) * 4;
        if (mapped_ptr_) {
            std::memcpy(mapped_ptr_, upload_rgba, bytes);
        }

        vk_.resetFences(device_, 1, &in_flight_fence_);
        vk_.resetCommandBuffer(cmd_buf_, 0);
        VkCommandBufferBeginInfo bi{ VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO, nullptr, VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT, nullptr };
        vk_.beginCommandBuffer(cmd_buf_, &bi);

        VkImage swap_img = swap_images_[img_idx];

        // Transition src_img -> TRANSFER_DST and copy staging buffer
        transition_image(cmd_buf_, src_img_, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                         0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);

        VkBufferImageCopy region{};
        region.imageSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 };
        region.imageExtent = { static_cast<uint32_t>(upload_w), static_cast<uint32_t>(upload_h), 1 };
        vk_.cmdCopyBufferToImage(cmd_buf_, staging_buf_, src_img_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &region);

        // Clear swapchain image to pure black
        transition_image(cmd_buf_, swap_img, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL,
                         0, VK_ACCESS_TRANSFER_WRITE_BIT, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
        VkClearColorValue black = {{0.0f, 0.0f, 0.0f, 1.0f}};
        VkImageSubresourceRange range{ VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 };
        vk_.cmdClearColorImage(cmd_buf_, swap_img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, &black, 1, &range);

        // Clamp viewport inside swapchain extent (Android Y=0 at top for Vulkan)
        int dx0 = std::clamp(vp_x, 0, static_cast<int>(swap_extent_.width));
        int dy0 = std::clamp(vp_y, 0, static_cast<int>(swap_extent_.height));
        int dx1 = std::clamp(vp_x + vp_w, dx0 + 1, static_cast<int>(swap_extent_.width));
        int dy1 = std::clamp(vp_y + vp_h, dy0 + 1, static_cast<int>(swap_extent_.height));
        uint32_t dst_w = static_cast<uint32_t>(dx1 - dx0);
        uint32_t dst_h = static_cast<uint32_t>(dy1 - dy0);

        bool shaded = false;
        if (!native_fsr_used && librashader_has_preset() && ensure_shader_out_image_locked(dst_w, dst_h)) {
            transition_image(cmd_buf_, src_img_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                             VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                             VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
            transition_image(cmd_buf_, shader_out_img_, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                             0, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                             VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);

            shaded = librashader_apply_vk(
                instance_, phys_, device_, queue_, vk_.getInstanceProcAddr, cmd_buf_,
                src_img_, VK_FORMAT_R8G8B8A8_UNORM, static_cast<uint32_t>(upload_w), static_cast<uint32_t>(upload_h),
                shader_out_img_, VK_FORMAT_R8G8B8A8_UNORM, dst_w, dst_h,
                static_cast<uint32_t>(swap_images_.size()), rotation_deg);

            if (shaded) {
                transition_image(cmd_buf_, shader_out_img_, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                 VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                                 VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
                VkImageBlit blit{};
                blit.srcSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 };
                blit.srcOffsets[0] = { 0, 0, 0 };
                blit.srcOffsets[1] = { static_cast<int32_t>(dst_w), static_cast<int32_t>(dst_h), 1 };
                blit.dstSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 };
                blit.dstOffsets[0] = { dx0, dy0, 0 };
                blit.dstOffsets[1] = { dx1, dy1, 1 };
                vk_.cmdBlitImage(cmd_buf_, shader_out_img_, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                 swap_img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit, VK_FILTER_LINEAR);
            } else {
                transition_image(cmd_buf_, src_img_, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                                 VK_ACCESS_SHADER_READ_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                                 VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
            }
        } else {
            transition_image(cmd_buf_, src_img_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                             VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_TRANSFER_READ_BIT,
                             VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_TRANSFER_BIT);
        }

        if (!shaded) {
            VkImageBlit blit{};
            blit.srcSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 };
            blit.srcOffsets[0] = { 0, 0, 0 };
            blit.srcOffsets[1] = { upload_w, upload_h, 1 };
            blit.dstSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 };
            blit.dstOffsets[0] = { dx0, dy0, 0 };
            blit.dstOffsets[1] = { dx1, dy1, 1 };
            const bool use_linear = linear_filter || native_fsr_used;
            vk_.cmdBlitImage(cmd_buf_, src_img_, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                             swap_img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit,
                             use_linear ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
        }

        transition_image(cmd_buf_, swap_img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                         VK_ACCESS_TRANSFER_WRITE_BIT, 0,
                         VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT);

        vk_.endCommandBuffer(cmd_buf_);

        VkPipelineStageFlags wait_stage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        VkSubmitInfo si{};
        si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        si.waitSemaphoreCount = 1;
        si.pWaitSemaphores = &img_avail_sem_;
        si.pWaitDstStageMask = &wait_stage;
        si.commandBufferCount = 1;
        si.pCommandBuffers = &cmd_buf_;
        si.signalSemaphoreCount = 1;
        si.pSignalSemaphores = &render_done_sem_;
        vk_.queueSubmit(queue_, 1, &si, in_flight_fence_);

        VkPresentInfoKHR pi{};
        pi.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        pi.waitSemaphoreCount = 1;
        pi.pWaitSemaphores = &render_done_sem_;
        pi.swapchainCount = 1;
        pi.pSwapchains = &swapchain_;
        pi.pImageIndices = &img_idx;
        VkResult pr = vk_.queuePresentKHR(queue_, &pi);
        if (pr == VK_ERROR_OUT_OF_DATE_KHR || pr == VK_SUBOPTIMAL_KHR) {
            need_recreate_ = true;
        }
    }

    void destroy() {
        std::lock_guard<std::mutex> lock(mu_);
        destroy_locked();
    }

    std::string device_name() {
        std::lock_guard<std::mutex> lock(mu_);
        return device_name_;
    }

private:
    uint32_t find_mem_type(uint32_t bits, VkMemoryPropertyFlags flags) {
        for (uint32_t i = 0; i < mem_props_.memoryTypeCount; ++i) {
            if ((bits & (1u << i)) && (mem_props_.memoryTypes[i].propertyFlags & flags) == flags) {
                return i;
            }
        }
        return 0;
    }

    void transition_image(VkCommandBuffer cmd, VkImage img, VkImageLayout old_l, VkImageLayout new_l,
                          VkAccessFlags src_acc, VkAccessFlags dst_acc,
                          VkPipelineStageFlags src_stage, VkPipelineStageFlags dst_stage) {
        VkImageMemoryBarrier b{};
        b.sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER;
        b.oldLayout = old_l;
        b.newLayout = new_l;
        b.srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        b.dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED;
        b.image = img;
        b.subresourceRange = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 };
        b.srcAccessMask = src_acc;
        b.dstAccessMask = dst_acc;
        vk_.cmdPipelineBarrier(cmd, src_stage, dst_stage, 0, 0, nullptr, 0, nullptr, 1, &b);
    }

    bool recreate_swapchain_locked() {
        if (device_) vk_.deviceWaitIdle(device_);
        librashader_reset_vk();
        if (swapchain_) {
            vk_.destroySwapchainKHR(device_, swapchain_, nullptr);
            swapchain_ = VK_NULL_HANDLE;
        }
        VkSurfaceCapabilitiesKHR caps{};
        if (vk_.getPhysicalDeviceSurfaceCapabilitiesKHR(phys_, surface_, &caps) != VK_SUCCESS) return false;
        swap_extent_ = caps.currentExtent;
        if (swap_extent_.width == 0 || swap_extent_.height == 0 || swap_extent_.width == UINT32_MAX) {
            swap_extent_.width = static_cast<uint32_t>(std::max(1, ANativeWindow_getWidth(window_)));
            swap_extent_.height = static_cast<uint32_t>(std::max(1, ANativeWindow_getHeight(window_)));
        }

        uint32_t fmt_count = 0;
        vk_.getPhysicalDeviceSurfaceFormatsKHR(phys_, surface_, &fmt_count, nullptr);
        std::vector<VkSurfaceFormatKHR> fmts(fmt_count);
        if (fmt_count > 0) vk_.getPhysicalDeviceSurfaceFormatsKHR(phys_, surface_, &fmt_count, fmts.data());
        swap_format_ = VK_FORMAT_R8G8B8A8_UNORM;
        VkColorSpaceKHR cs = VK_COLOR_SPACE_SRGB_NONLINEAR_KHR;
        for (const auto& f : fmts) {
            if (f.format == VK_FORMAT_R8G8B8A8_UNORM || f.format == VK_FORMAT_B8G8R8A8_UNORM) {
                swap_format_ = f.format;
                cs = f.colorSpace;
                break;
            }
        }

        uint32_t img_count = std::max(2u, caps.minImageCount);
        if (caps.maxImageCount > 0 && img_count > caps.maxImageCount) img_count = caps.maxImageCount;

        VkSwapchainCreateInfoKHR sci{};
        sci.sType = VK_STRUCTURE_TYPE_SWAPCHAIN_CREATE_INFO_KHR;
        sci.surface = surface_;
        sci.minImageCount = img_count;
        sci.imageFormat = swap_format_;
        sci.imageColorSpace = cs;
        sci.imageExtent = swap_extent_;
        sci.imageArrayLayers = 1;
        sci.imageUsage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT;
        sci.imageSharingMode = VK_SHARING_MODE_EXCLUSIVE;
        sci.preTransform = (caps.supportedTransforms & VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR)
                               ? VK_SURFACE_TRANSFORM_IDENTITY_BIT_KHR : caps.currentTransform;
        sci.compositeAlpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        sci.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        sci.clipped = VK_TRUE;

        if (vk_.createSwapchainKHR(device_, &sci, nullptr, &swapchain_) != VK_SUCCESS) return false;
        uint32_t sc_cnt = 0;
        vk_.getSwapchainImagesKHR(device_, swapchain_, &sc_cnt, nullptr);
        swap_images_.resize(sc_cnt);
        vk_.getSwapchainImagesKHR(device_, swapchain_, &sc_cnt, swap_images_.data());
        return !swap_images_.empty();
    }

    bool ensure_source_image_locked(uint32_t w, uint32_t h) {
        if (src_img_ && src_w_ == w && src_h_ == h) return true;
        vk_.deviceWaitIdle(device_);
        destroy_source_locked();

        VkDeviceSize bytes = static_cast<VkDeviceSize>(w) * static_cast<VkDeviceSize>(h) * 4;
        VkBufferCreateInfo bci{};
        bci.sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO;
        bci.size = bytes;
        bci.usage = VK_BUFFER_USAGE_TRANSFER_SRC_BIT;
        bci.sharingMode = VK_SHARING_MODE_EXCLUSIVE;
        if (vk_.createBuffer(device_, &bci, nullptr, &staging_buf_) != VK_SUCCESS) return false;

        VkMemoryRequirements breq{};
        vk_.getBufferMemoryRequirements(device_, staging_buf_, &breq);
        VkMemoryAllocateInfo bai{ VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, breq.size,
                                  find_mem_type(breq.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT) };
        if (vk_.allocateMemory(device_, &bai, nullptr, &staging_mem_) != VK_SUCCESS) return false;
        vk_.bindBufferMemory(device_, staging_buf_, staging_mem_, 0);
        if (vk_.mapMemory(device_, staging_mem_, 0, bytes, 0, &mapped_ptr_) != VK_SUCCESS || !mapped_ptr_) {
            mapped_ptr_ = nullptr;
            destroy_source_locked();
            return false;
        }

        VkImageCreateInfo ici{};
        ici.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        ici.imageType = VK_IMAGE_TYPE_2D;
        ici.format = VK_FORMAT_R8G8B8A8_UNORM;
        ici.extent = { w, h, 1 };
        ici.mipLevels = 1;
        ici.arrayLayers = 1;
        ici.samples = VK_SAMPLE_COUNT_1_BIT;
        ici.tiling = VK_IMAGE_TILING_OPTIMAL;
        ici.usage = VK_IMAGE_USAGE_TRANSFER_DST_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
        ici.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        if (vk_.createImage(device_, &ici, nullptr, &src_img_) != VK_SUCCESS) return false;

        VkMemoryRequirements ireq{};
        vk_.getImageMemoryRequirements(device_, src_img_, &ireq);
        VkMemoryAllocateInfo iai{ VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, ireq.size,
                                  find_mem_type(ireq.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) };
        if (vk_.allocateMemory(device_, &iai, nullptr, &src_mem_) != VK_SUCCESS) return false;
        vk_.bindImageMemory(device_, src_img_, src_mem_, 0);

        src_w_ = w;
        src_h_ = h;
        return true;
    }

    bool ensure_shader_out_image_locked(uint32_t w, uint32_t h) {
        if (shader_out_img_ && shader_out_w_ == w && shader_out_h_ == h) return true;
        vk_.deviceWaitIdle(device_);
        if (shader_out_img_) { vk_.destroyImage(device_, shader_out_img_, nullptr); shader_out_img_ = VK_NULL_HANDLE; }
        if (shader_out_mem_) { vk_.freeMemory(device_, shader_out_mem_, nullptr); shader_out_mem_ = VK_NULL_HANDLE; }

        VkImageCreateInfo ici{};
        ici.sType = VK_STRUCTURE_TYPE_IMAGE_CREATE_INFO;
        ici.imageType = VK_IMAGE_TYPE_2D;
        ici.format = VK_FORMAT_R8G8B8A8_UNORM;
        ici.extent = { w, h, 1 };
        ici.mipLevels = 1;
        ici.arrayLayers = 1;
        ici.samples = VK_SAMPLE_COUNT_1_BIT;
        ici.tiling = VK_IMAGE_TILING_OPTIMAL;
        ici.usage = VK_IMAGE_USAGE_COLOR_ATTACHMENT_BIT | VK_IMAGE_USAGE_TRANSFER_SRC_BIT | VK_IMAGE_USAGE_SAMPLED_BIT;
        ici.initialLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        if (vk_.createImage(device_, &ici, nullptr, &shader_out_img_) != VK_SUCCESS) return false;

        VkMemoryRequirements req{};
        vk_.getImageMemoryRequirements(device_, shader_out_img_, &req);
        VkMemoryAllocateInfo ai{ VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, req.size,
                                 find_mem_type(req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT) };
        if (vk_.allocateMemory(device_, &ai, nullptr, &shader_out_mem_) != VK_SUCCESS) return false;
        vk_.bindImageMemory(device_, shader_out_img_, shader_out_mem_, 0);
        shader_out_w_ = w;
        shader_out_h_ = h;
        return true;
    }

    void destroy_source_locked() {
        if (mapped_ptr_ && staging_mem_ && vk_.unmapMemory) {
            vk_.unmapMemory(device_, staging_mem_);
            mapped_ptr_ = nullptr;
        }
        if (src_img_) { vk_.destroyImage(device_, src_img_, nullptr); src_img_ = VK_NULL_HANDLE; }
        if (src_mem_) { vk_.freeMemory(device_, src_mem_, nullptr); src_mem_ = VK_NULL_HANDLE; }
        if (staging_buf_) { vk_.destroyBuffer(device_, staging_buf_, nullptr); staging_buf_ = VK_NULL_HANDLE; }
        if (staging_mem_) { vk_.freeMemory(device_, staging_mem_, nullptr); staging_mem_ = VK_NULL_HANDLE; }
        src_w_ = src_h_ = 0;
    }

    void destroy_locked() {
        ready_ = false;
        if (device_) {
            vk_.deviceWaitIdle(device_);
            librashader_reset_vk();
            destroy_source_locked();
            if (shader_out_img_) { vk_.destroyImage(device_, shader_out_img_, nullptr); shader_out_img_ = VK_NULL_HANDLE; }
            if (shader_out_mem_) { vk_.freeMemory(device_, shader_out_mem_, nullptr); shader_out_mem_ = VK_NULL_HANDLE; }
            if (in_flight_fence_) { vk_.destroyFence(device_, in_flight_fence_, nullptr); in_flight_fence_ = VK_NULL_HANDLE; }
            if (img_avail_sem_) { vk_.destroySemaphore(device_, img_avail_sem_, nullptr); img_avail_sem_ = VK_NULL_HANDLE; }
            if (render_done_sem_) { vk_.destroySemaphore(device_, render_done_sem_, nullptr); render_done_sem_ = VK_NULL_HANDLE; }
            if (cmd_pool_) { vk_.destroyCommandPool(device_, cmd_pool_, nullptr); cmd_pool_ = VK_NULL_HANDLE; }
            if (swapchain_) { vk_.destroySwapchainKHR(device_, swapchain_, nullptr); swapchain_ = VK_NULL_HANDLE; }
            vk_.destroyDevice(device_, nullptr);
            device_ = VK_NULL_HANDLE;
        }
        if (surface_ && instance_) { vk_.destroySurfaceKHR(instance_, surface_, nullptr); surface_ = VK_NULL_HANDLE; }
        if (instance_) { vk_.destroyInstance(instance_, nullptr); instance_ = VK_NULL_HANDLE; }
        if (window_) { ANativeWindow_release(window_); window_ = nullptr; }
        if (vk_.lib) { dlclose(vk_.lib); vk_.lib = nullptr; }
        swap_images_.clear();
        prev_frame_rgba_.clear();
        cur_frame_rgba_.clear();
        fg_frame_rgba_.clear();
        fsr_stage_.clear();
        fsr3_history_stage_.clear();
        fsr_out_rgba_.clear();
        cached_w_ = cached_h_ = 0;
        fsr3_history_valid_ = false;
        fsr3_frame_idx_ = 0;
    }

    std::mutex mu_;
    VkFuncs vk_{};
    ANativeWindow* window_ = nullptr;
    VkInstance instance_ = VK_NULL_HANDLE;
    VkSurfaceKHR surface_ = VK_NULL_HANDLE;
    VkPhysicalDevice phys_ = VK_NULL_HANDLE;
    VkPhysicalDeviceMemoryProperties mem_props_{};
    VkDevice device_ = VK_NULL_HANDLE;
    VkQueue queue_ = VK_NULL_HANDLE;
    uint32_t queue_family_ = UINT32_MAX;
    VkSwapchainKHR swapchain_ = VK_NULL_HANDLE;
    VkFormat swap_format_ = VK_FORMAT_R8G8B8A8_UNORM;
    VkExtent2D swap_extent_{};
    std::vector<VkImage> swap_images_;
    VkCommandPool cmd_pool_ = VK_NULL_HANDLE;
    VkCommandBuffer cmd_buf_ = VK_NULL_HANDLE;
    VkSemaphore img_avail_sem_ = VK_NULL_HANDLE;
    VkSemaphore render_done_sem_ = VK_NULL_HANDLE;
    VkFence in_flight_fence_ = VK_NULL_HANDLE;

    VkBuffer staging_buf_ = VK_NULL_HANDLE;
    VkDeviceMemory staging_mem_ = VK_NULL_HANDLE;
    void* mapped_ptr_ = nullptr;
    VkImage src_img_ = VK_NULL_HANDLE;
    VkDeviceMemory src_mem_ = VK_NULL_HANDLE;
    uint32_t src_w_ = 0, src_h_ = 0;

    VkImage shader_out_img_ = VK_NULL_HANDLE;
    VkDeviceMemory shader_out_mem_ = VK_NULL_HANDLE;
    uint32_t shader_out_w_ = 0, shader_out_h_ = 0;

    std::vector<uint32_t> prev_frame_rgba_;
    std::vector<uint32_t> cur_frame_rgba_;
    std::vector<uint32_t> fg_frame_rgba_;
    std::vector<float> fg_luma0_;
    std::vector<float> fg_luma1_;
    std::vector<RgbF> fsr_stage_;
    std::vector<RgbF> fsr3_history_stage_;
    std::vector<uint32_t> fsr_out_rgba_;
    int cached_w_ = 0, cached_h_ = 0;
    bool fsr3_history_valid_ = false;
    int fsr3_frame_idx_ = 0;

    bool ready_ = false;
    bool need_recreate_ = false;
    std::string device_name_;
    std::string device_api_ver_;
    std::string instance_api_ver_;
};

static VulkanRenderer g_vk;

} // namespace

bool vk_probe_info(std::string& out_device_name, std::string& out_device_api_ver, std::string& out_instance_api_ver) {
    return g_vk.probe_info(out_device_name, out_device_api_ver, out_instance_api_ver);
}
bool vk_probe_support(std::string& device_name) {
    std::string dev_ver, inst_ver;
    return vk_probe_info(device_name, dev_ver, inst_ver);
}
bool vk_init(ANativeWindow* window, const std::string& custom_driver_path) { return g_vk.init(window, custom_driver_path); }
void vk_resize(int w, int h) { g_vk.resize(w, h); }
void vk_render(const uint32_t* rgba, int w, int h, int vp_x, int vp_y, int vp_w, int vp_h, bool linear_filter, int rotation_deg) {
    g_vk.render(rgba, w, h, vp_x, vp_y, vp_w, vp_h, linear_filter, rotation_deg);
}
void vk_destroy() { g_vk.destroy(); }
std::string vk_device_name() { return g_vk.device_name(); }

} // namespace storm

#endif // ANDROID
