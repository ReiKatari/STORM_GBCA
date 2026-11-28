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
        const uint32_t* src = (alpha >= 0.5f) ? cur_rgba : prev_rgba;
        std::memcpy(out_rgba.data(), src, count * 4);
        return;
    }

    for (int y = 0; y < h; ++y) {
        const int ym = std::max(0, y - 1);
        const int yp = std::min(h - 1, y + 1);
        const int row = y * w;
        const int row_m = ym * w;
        const int row_p = yp * w;
        for (int x = 0; x < w; ++x) {
            const int xm = std::max(0, x - 1);
            const int xp = std::min(w - 1, x + 1);
            const int idx = row + x;

            const float it = l1_buf[idx] - l0_buf[idx];
            float vx = 0.0f;
            float vy = 0.0f;
            if (std::abs(it) > 0.018f) {
                const float ix = 0.5f * (l1_buf[row + xp] - l1_buf[row + xm]);
                const float iy = 0.5f * (l1_buf[row_p + x] - l1_buf[row_m + x]);
                const float grad2 = ix * ix + iy * iy + 0.004f;
                vx = std::clamp(-it * ix / grad2, -3.0f, 3.0f);
                vy = std::clamp(-it * iy / grad2, -3.0f, 3.0f);
            }

            RgbF p0 = unpack_rgba(prev_rgba[idx]);
            RgbF p1 = unpack_rgba(cur_rgba[idx]);
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
            out_rgba[idx] = pack_rgba(res.r, res.g, res.b);
        }
    }
}

static void run_rcas_cpu(const std::vector<RgbF>& stage, int w, int h, float sharpness, std::vector<uint32_t>& out_rgba) {
    out_rgba.resize(static_cast<size_t>(w) * static_cast<size_t>(h));
    const float sharp = std::clamp(sharpness, 0.0f, 1.0f);
    for (int y = 0; y < h; ++y) {
        const int ym = std::max(0, y - 1);
        const int yp = std::min(h - 1, y + 1);
        const size_t row = static_cast<size_t>(y) * w;
        const size_t row_m = static_cast<size_t>(ym) * w;
        const size_t row_p = static_cast<size_t>(yp) * w;
        for (int x = 0; x < w; ++x) {
            const int xm = std::max(0, x - 1);
            const int xp = std::min(w - 1, x + 1);
            const RgbF& b = stage[row_m + x];
            const RgbF& d = stage[row + xm];
            const RgbF& e = stage[row + x];
            const RgbF& f = stage[row + xp];
            const RgbF& h_px = stage[row_p + x];

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
            out_rgba[row + x] = pack_rgba(
                (lobe * (b.r + d.r + f.r + h_px.r) + e.r) * rcpL,
                (lobe * (b.g + d.g + f.g + h_px.g) + e.g) * rcpL,
                (lobe * (b.b + d.b + f.b + h_px.b) + e.b) * rcpL
            );
        }
    }
}

static void fast_edge_aware_upscale_cpu(const uint32_t* src, int sw, int sh, int dw, int dh, std::vector<RgbF>& stage) {
    stage.resize(static_cast<size_t>(dw) * static_cast<size_t>(dh));
    const float sx = static_cast<float>(sw) / static_cast<float>(dw);
    const float sy = static_cast<float>(sh) / static_cast<float>(dh);

    for (int y = 0; y < dh; ++y) {
        float py = std::clamp((static_cast<float>(y) + 0.5f) * sy - 0.5f, 0.0f, static_cast<float>(sh - 1));
        int y0 = static_cast<int>(py);
        int y1 = std::min(y0 + 1, sh - 1);
        float fy = py - static_cast<float>(y0);
        // S-curve sharpen on fractional weight for crisp pixel-art edges
        float fy2 = fy * fy * (3.0f - 2.0f * fy);
        float wy = fy + (fy2 - fy) * 0.65f;

        const uint32_t* row0 = src + static_cast<size_t>(y0) * sw;
        const uint32_t* row1 = src + static_cast<size_t>(y1) * sw;
        RgbF* dst_row = stage.data() + static_cast<size_t>(y) * dw;

        for (int x = 0; x < dw; ++x) {
            float px = std::clamp((static_cast<float>(x) + 0.5f) * sx - 0.5f, 0.0f, static_cast<float>(sw - 1));
            int x0 = static_cast<int>(px);
            int x1 = std::min(x0 + 1, sw - 1);
            float fx = px - static_cast<float>(x0);
            float fx2 = fx * fx * (3.0f - 2.0f * fx);
            float wx = fx + (fx2 - fx) * 0.65f;

            RgbF c00 = unpack_rgba(row0[x0]);
            RgbF c10 = unpack_rgba(row0[x1]);
            RgbF c01 = unpack_rgba(row1[x0]);
            RgbF c11 = unpack_rgba(row1[x1]);

            float w00 = (1.0f - wx) * (1.0f - wy);
            float w10 = wx * (1.0f - wy);
            float w01 = (1.0f - wx) * wy;
            float w11 = wx * wy;
            dst_row[x] = {
                c00.r * w00 + c10.r * w10 + c01.r * w01 + c11.r * w11,
                c00.g * w00 + c10.g * w10 + c01.g * w01 + c11.g * w11,
                c00.b * w00 + c10.b * w10 + c01.b * w01 + c11.b * w11,
            };
        }
    }
}

static void run_fsr1_cpu(const uint32_t* src, int sw, int sh, int dw, int dh, float sharpness,
                         std::vector<RgbF>& stage, std::vector<uint32_t>& out_rgba) {
    fast_edge_aware_upscale_cpu(src, sw, sh, dw, dh, stage);
    run_rcas_cpu(stage, dw, dh, sharpness, out_rgba);
}

static void run_fsr3_cpu(const uint32_t* src, const uint32_t* prev_src, int sw, int sh, int dw, int dh,
                         float sharpness, bool& history_valid,
                         std::vector<RgbF>& history_stage, std::vector<RgbF>& work_stage,
                         std::vector<uint32_t>& out_rgba) {
    fast_edge_aware_upscale_cpu(src, sw, sh, dw, dh, work_stage);
    const size_t total = static_cast<size_t>(dw) * static_cast<size_t>(dh);
    if (history_valid && history_stage.size() == total) {
        const bool has_prev_raw = (prev_src != nullptr);
        const float sx = static_cast<float>(sw) / static_cast<float>(dw);
        const float sy = static_cast<float>(sh) / static_cast<float>(dh);
        for (int y = 0; y < dh; ++y) {
            const int src_y = std::clamp(static_cast<int>(static_cast<float>(y) * sy), 0, sh - 1);
            const size_t row = static_cast<size_t>(y) * dw;
            const size_t srow = static_cast<size_t>(src_y) * sw;
            for (int x = 0; x < dw; ++x) {
                RgbF& cur = work_stage[row + x];
                const RgbF& h_px = history_stage[row + x];
                float diff = fsr_luma({std::abs(cur.r - h_px.r), std::abs(cur.g - h_px.g), std::abs(cur.b - h_px.b)});
                if (has_prev_raw) {
                    const int src_x = std::clamp(static_cast<int>(static_cast<float>(x) * sx), 0, sw - 1);
                    RgbF p_raw = unpack_rgba(prev_src[srow + src_x]);
                    float mot = fsr_luma({std::abs(cur.r - p_raw.r), std::abs(cur.g - p_raw.g), std::abs(cur.b - p_raw.b)});
                    diff = std::max(diff, mot * 0.75f);
                }
                float hw = 0.55f * (1.0f - std::clamp((diff - 0.06f) / 0.22f, 0.0f, 1.0f));
                cur.r += (h_px.r - cur.r) * hw;
                cur.g += (h_px.g - cur.g) * hw;
                cur.b += (h_px.b - cur.b) * hw;
            }
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
        getInstanceProcAddr = nullptr;
        createInstance = nullptr;
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
        if (!getInstanceProcAddr) {
            dlclose(lib);
            lib = nullptr;
            return false;
        }
        createInstance = reinterpret_cast<PFN_vkCreateInstance>(getInstanceProcAddr(VK_NULL_HANDLE, "vkCreateInstance"));
        if (!createInstance) {
            dlclose(lib);
            lib = nullptr;
            getInstanceProcAddr = nullptr;
            return false;
        }
        return true;
    }

    uint32_t query_instance_version() const {
        if (!getInstanceProcAddr) return VK_API_VERSION_1_0;
        auto fn = reinterpret_cast<PFN_vkEnumerateInstanceVersion>(
            getInstanceProcAddr(VK_NULL_HANDLE, "vkEnumerateInstanceVersion"));
        uint32_t ver = VK_API_VERSION_1_0;
        if (fn && fn(&ver) == VK_SUCCESS && ver >= VK_API_VERSION_1_0) {
            return ver;
        }
        return VK_API_VERSION_1_0;
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
        if (probed_once_) {
            out_device_name = device_name_;
            out_device_api_ver = device_api_ver_;
            out_instance_api_ver = instance_api_ver_;
            return probe_ok_;
        }
        probed_once_ = true;
        probe_ok_ = false;
        VkFuncs tmp{};
        if (!tmp.load_library("")) return false;
        uint32_t inst_ver = tmp.query_instance_version();
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
            inst_ver = VK_API_VERSION_1_0;
            cres = tmp.createInstance(&ci, nullptr, &inst);
        }
        std::string inst_ver_str = format_vk_version(inst_ver);
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
        probe_ok_ = ok;
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
        uint32_t req_api = std::max<uint32_t>(VK_API_VERSION_1_1, std::min<uint32_t>(inst_ver, VK_MAKE_VERSION(1, 3, 0)));

        VkApplicationInfo app{};
        app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        app.pApplicationName = "STORM GBCA";
        app.applicationVersion = VK_MAKE_VERSION(1, 9, 1);
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
            inst_ver = VK_API_VERSION_1_0;
            cres = vk_.createInstance(&ici, nullptr, &instance_);
        }
        instance_api_ver_ = format_vk_version(inst_ver);
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
        if (vk_.createSemaphore(device_, &sem_ci, nullptr, &img_avail_sem_) != VK_SUCCESS ||
            vk_.createFence(device_, &fence_ci, nullptr, &in_flight_fence_) != VK_SUCCESS) {
            destroy_locked();
            return false;
        }

        if (!recreate_swapchain_locked()) {
            destroy_locked();
            return false;
        }
        probed_once_ = true;
        probe_ok_ = true;
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

        const int sw = static_cast<int>(swap_extent_.width);
        const int sh = static_cast<int>(swap_extent_.height);
        if (sw <= 0 || sh <= 0 || vp_w <= 0 || vp_h <= 0 || swapchain_ == VK_NULL_HANDLE) return;

        VkResult wf = vk_.waitForFences(device_, 1, &in_flight_fence_, VK_TRUE, 50000000ULL);
        if (wf == VK_TIMEOUT) {
            return;
        }
        if (wf != VK_SUCCESS) {
            need_recreate_ = true;
            return;
        }

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

        if (!librashader_has_preset() && (post.fsr_mode == 1 || post.fsr_mode == 2) && w <= 256 && h <= 224) {
            float sharp = std::clamp(post.rcas_sharpness + fsr_quality_sharpness_bias(post.fsr_quality), 0.0f, 1.0f);
            float mult = (post.fsr_quality <= 1) ? 2.0f : 1.5f;
            int target_w = std::max(w, static_cast<int>(std::lround(static_cast<float>(w) * mult)));
            int target_h = std::max(h, static_cast<int>(std::lround(static_cast<float>(h) * mult)));
            if (post.fsr_mode == 1) {
                run_fsr1_cpu(base_rgba, w, h, target_w, target_h, sharp, fsr_stage_, fsr_out_rgba_);
            } else {
                const uint32_t* prev_ptr = (prev_frame_rgba_.size() == raw_count) ? prev_frame_rgba_.data() : nullptr;
                run_fsr3_cpu(base_rgba, prev_ptr, w, h, target_w, target_h, sharp,
                             fsr3_history_valid_, fsr3_history_stage_, fsr_stage_, fsr_out_rgba_);
            }
            upload_w = target_w;
            upload_h = target_h;
            upload_rgba = fsr_out_rgba_.data();
            native_fsr_used = true;
        }

        // Clamp viewport safely inside swapchain extent (Android Y=0 at top for Vulkan)
        int dx0 = std::clamp(vp_x, 0, sw - 1);
        int dx1 = std::clamp(vp_x + vp_w, dx0 + 1, sw);
        int dy0 = std::clamp(vp_y, 0, sh - 1);
        int dy1 = std::clamp(vp_y + vp_h, dy0 + 1, sh);
        uint32_t dst_w = static_cast<uint32_t>(dx1 - dx0);
        uint32_t dst_h = static_cast<uint32_t>(dy1 - dy0);

        // Allocate/resize source and shader output images BEFORE acquiring swapchain image
        if (!ensure_source_image_locked(static_cast<uint32_t>(upload_w), static_cast<uint32_t>(upload_h))) return;
        const bool want_librashader = !native_fsr_used && librashader_has_preset() &&
                                      ensure_shader_out_image_locked(dst_w, dst_h);

        uint32_t img_idx = 0;
        VkResult acq = vk_.acquireNextImageKHR(device_, swapchain_, 50000000ULL, img_avail_sem_, VK_NULL_HANDLE, &img_idx);
        if (acq == VK_TIMEOUT) {
            return;
        }
        if (acq == VK_ERROR_OUT_OF_DATE_KHR) {
            need_recreate_ = true;
            return;
        }
        if (acq != VK_SUCCESS && acq != VK_SUBOPTIMAL_KHR) return;
        if (img_idx >= swap_images_.size() || img_idx >= render_done_sems_.size()) {
            need_recreate_ = true;
            return;
        }
        bool acq_suboptimal = (acq == VK_SUBOPTIMAL_KHR);

        // Upload RGBA frame into persistently mapped host-visible staging buffer
        size_t bytes = static_cast<size_t>(upload_w) * static_cast<size_t>(upload_h) * 4;
        if (mapped_ptr_) {
            std::memcpy(mapped_ptr_, upload_rgba, bytes);
        }

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

        bool shaded = false;
        if (want_librashader) {
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

        VkSemaphore render_done = render_done_sems_[img_idx];
        VkPipelineStageFlags wait_stage = VK_PIPELINE_STAGE_TRANSFER_BIT;
        VkSubmitInfo si{};
        si.sType = VK_STRUCTURE_TYPE_SUBMIT_INFO;
        si.waitSemaphoreCount = 1;
        si.pWaitSemaphores = &img_avail_sem_;
        si.pWaitDstStageMask = &wait_stage;
        si.commandBufferCount = 1;
        si.pCommandBuffers = &cmd_buf_;
        si.signalSemaphoreCount = 1;
        si.pSignalSemaphores = &render_done;

        vk_.resetFences(device_, 1, &in_flight_fence_);
        VkResult sr = vk_.queueSubmit(queue_, 1, &si, in_flight_fence_);
        if (sr != VK_SUCCESS) {
            need_recreate_ = true;
            if (in_flight_fence_) {
                vk_.destroyFence(device_, in_flight_fence_, nullptr);
                in_flight_fence_ = VK_NULL_HANDLE;
            }
            VkFenceCreateInfo fence_ci{ VK_STRUCTURE_TYPE_FENCE_CREATE_INFO, nullptr, VK_FENCE_CREATE_SIGNALED_BIT };
            vk_.createFence(device_, &fence_ci, nullptr, &in_flight_fence_);
            return;
        }

        VkPresentInfoKHR pi{};
        pi.sType = VK_STRUCTURE_TYPE_PRESENT_INFO_KHR;
        pi.waitSemaphoreCount = 1;
        pi.pWaitSemaphores = &render_done;
        pi.swapchainCount = 1;
        pi.pSwapchains = &swapchain_;
        pi.pImageIndices = &img_idx;
        VkResult pr = vk_.queuePresentKHR(queue_, &pi);
        if (pr == VK_ERROR_OUT_OF_DATE_KHR || pr == VK_SUBOPTIMAL_KHR || acq_suboptimal) {
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
        return UINT32_MAX;
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

        VkCompositeAlphaFlagBitsKHR comp_alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        if (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR) {
            comp_alpha = VK_COMPOSITE_ALPHA_OPAQUE_BIT_KHR;
        } else if (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR) {
            comp_alpha = VK_COMPOSITE_ALPHA_INHERIT_BIT_KHR;
        } else if (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR) {
            comp_alpha = VK_COMPOSITE_ALPHA_PRE_MULTIPLIED_BIT_KHR;
        } else if (caps.supportedCompositeAlpha & VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR) {
            comp_alpha = VK_COMPOSITE_ALPHA_POST_MULTIPLIED_BIT_KHR;
        }

        VkSwapchainKHR old_swapchain = swapchain_;
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
        sci.compositeAlpha = comp_alpha;
        sci.presentMode = VK_PRESENT_MODE_FIFO_KHR;
        sci.clipped = VK_TRUE;
        sci.oldSwapchain = old_swapchain;

        VkSwapchainKHR new_swapchain = VK_NULL_HANDLE;
        if (vk_.createSwapchainKHR(device_, &sci, nullptr, &new_swapchain) != VK_SUCCESS) {
            return false;
        }
        if (old_swapchain) {
            vk_.destroySwapchainKHR(device_, old_swapchain, nullptr);
        }
        swapchain_ = new_swapchain;

        uint32_t sc_cnt = 0;
        vk_.getSwapchainImagesKHR(device_, swapchain_, &sc_cnt, nullptr);
        swap_images_.resize(sc_cnt);
        if (sc_cnt > 0) {
            vk_.getSwapchainImagesKHR(device_, swapchain_, &sc_cnt, swap_images_.data());
        }

        for (VkSemaphore s : render_done_sems_) {
            if (s) vk_.destroySemaphore(device_, s, nullptr);
        }
        render_done_sems_.assign(swap_images_.size(), VK_NULL_HANDLE);
        VkSemaphoreCreateInfo sem_ci{ VK_STRUCTURE_TYPE_SEMAPHORE_CREATE_INFO, nullptr, 0 };
        for (size_t i = 0; i < swap_images_.size(); ++i) {
            if (vk_.createSemaphore(device_, &sem_ci, nullptr, &render_done_sems_[i]) != VK_SUCCESS) {
                return false;
            }
        }
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
        if (vk_.createBuffer(device_, &bci, nullptr, &staging_buf_) != VK_SUCCESS) {
            destroy_source_locked();
            return false;
        }

        VkMemoryRequirements breq{};
        vk_.getBufferMemoryRequirements(device_, staging_buf_, &breq);
        uint32_t memType = find_mem_type(breq.memoryTypeBits, VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT | VK_MEMORY_PROPERTY_HOST_COHERENT_BIT);
        if (memType == UINT32_MAX) {
            destroy_source_locked();
            return false;
        }
        VkMemoryAllocateInfo bai{ VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, breq.size, memType };
        if (vk_.allocateMemory(device_, &bai, nullptr, &staging_mem_) != VK_SUCCESS) {
            destroy_source_locked();
            return false;
        }
        if (vk_.bindBufferMemory(device_, staging_buf_, staging_mem_, 0) != VK_SUCCESS) {
            destroy_source_locked();
            return false;
        }
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
        if (vk_.createImage(device_, &ici, nullptr, &src_img_) != VK_SUCCESS) {
            destroy_source_locked();
            return false;
        }

        VkMemoryRequirements ireq{};
        vk_.getImageMemoryRequirements(device_, src_img_, &ireq);
        uint32_t imgMemType = find_mem_type(ireq.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (imgMemType == UINT32_MAX) {
            destroy_source_locked();
            return false;
        }
        VkMemoryAllocateInfo iai{ VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, ireq.size, imgMemType };
        if (vk_.allocateMemory(device_, &iai, nullptr, &src_mem_) != VK_SUCCESS) {
            destroy_source_locked();
            return false;
        }
        if (vk_.bindImageMemory(device_, src_img_, src_mem_, 0) != VK_SUCCESS) {
            destroy_source_locked();
            return false;
        }

        src_w_ = w;
        src_h_ = h;
        return true;
    }

    bool ensure_shader_out_image_locked(uint32_t w, uint32_t h) {
        if (shader_out_img_ && shader_out_w_ == w && shader_out_h_ == h) return true;
        vk_.deviceWaitIdle(device_);
        if (shader_out_img_) { vk_.destroyImage(device_, shader_out_img_, nullptr); shader_out_img_ = VK_NULL_HANDLE; }
        if (shader_out_mem_) { vk_.freeMemory(device_, shader_out_mem_, nullptr); shader_out_mem_ = VK_NULL_HANDLE; }
        shader_out_w_ = shader_out_h_ = 0;

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
        uint32_t memType = find_mem_type(req.memoryTypeBits, VK_MEMORY_PROPERTY_DEVICE_LOCAL_BIT);
        if (memType == UINT32_MAX) {
            vk_.destroyImage(device_, shader_out_img_, nullptr);
            shader_out_img_ = VK_NULL_HANDLE;
            return false;
        }
        VkMemoryAllocateInfo ai{ VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO, nullptr, req.size, memType };
        if (vk_.allocateMemory(device_, &ai, nullptr, &shader_out_mem_) != VK_SUCCESS) {
            vk_.destroyImage(device_, shader_out_img_, nullptr);
            shader_out_img_ = VK_NULL_HANDLE;
            return false;
        }
        if (vk_.bindImageMemory(device_, shader_out_img_, shader_out_mem_, 0) != VK_SUCCESS) {
            vk_.destroyImage(device_, shader_out_img_, nullptr);
            shader_out_img_ = VK_NULL_HANDLE;
            vk_.freeMemory(device_, shader_out_mem_, nullptr);
            shader_out_mem_ = VK_NULL_HANDLE;
            return false;
        }
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
        need_recreate_ = false;
        if (device_) {
            vk_.deviceWaitIdle(device_);
            librashader_reset_vk();
            destroy_source_locked();
            if (shader_out_img_) { vk_.destroyImage(device_, shader_out_img_, nullptr); shader_out_img_ = VK_NULL_HANDLE; }
            if (shader_out_mem_) { vk_.freeMemory(device_, shader_out_mem_, nullptr); shader_out_mem_ = VK_NULL_HANDLE; }
            shader_out_w_ = shader_out_h_ = 0;
            if (in_flight_fence_) { vk_.destroyFence(device_, in_flight_fence_, nullptr); in_flight_fence_ = VK_NULL_HANDLE; }
            if (img_avail_sem_) { vk_.destroySemaphore(device_, img_avail_sem_, nullptr); img_avail_sem_ = VK_NULL_HANDLE; }
            for (VkSemaphore s : render_done_sems_) {
                if (s) vk_.destroySemaphore(device_, s, nullptr);
            }
            render_done_sems_.clear();
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
    std::vector<VkSemaphore> render_done_sems_;
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

    bool ready_ = false;
    bool need_recreate_ = false;
    bool probed_once_ = false;
    bool probe_ok_ = false;
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
