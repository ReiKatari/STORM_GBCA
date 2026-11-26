#ifdef ANDROID

#define VK_USE_PLATFORM_ANDROID_KHR 1
#define VK_NO_PROTOTYPES 1
#include <vulkan/vulkan.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>
#include <android/log.h>
#include <dlfcn.h>

#include <algorithm>
#include <cstring>
#include <mutex>
#include <string>
#include <vector>

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "StormVulkan", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "StormVulkan", __VA_ARGS__)

namespace storm {

extern bool librashader_apply_vk(VkInstance instance, VkPhysicalDevice phys, VkDevice device, VkQueue queue,
                                 PFN_vkGetInstanceProcAddr vk_entry, VkCommandBuffer cmd,
                                 VkImage src_img, VkFormat src_fmt, uint32_t src_w, uint32_t src_h,
                                 VkImage dst_img, VkFormat dst_fmt, uint32_t dst_w, uint32_t dst_h,
                                 uint32_t frames_in_flight, int rotation_deg);
extern void librashader_reset_vk();

namespace {

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
    bool probe_support(std::string& out_device_name) {
        std::lock_guard<std::mutex> lock(mu_);
        if (!device_name_.empty()) {
            out_device_name = device_name_;
            return true;
        }
        VkFuncs tmp{};
        if (!tmp.load_library("")) return false;
        VkApplicationInfo app{};
        app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        app.pApplicationName = "STORM GBCA";
        app.apiVersion = VK_API_VERSION_1_1;
        const char* exts[] = { "VK_KHR_surface", "VK_KHR_android_surface" };
        VkInstanceCreateInfo ci{};
        ci.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        ci.pApplicationInfo = &app;
        ci.enabledExtensionCount = 2;
        ci.ppEnabledExtensionNames = exts;
        VkInstance inst = VK_NULL_HANDLE;
        if (tmp.createInstance(&ci, nullptr, &inst) != VK_SUCCESS) {
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
            out_device_name = device_name_;
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

        VkApplicationInfo app{};
        app.sType = VK_STRUCTURE_TYPE_APPLICATION_INFO;
        app.pApplicationName = "STORM GBCA";
        app.applicationVersion = VK_MAKE_VERSION(1, 1, 0);
        app.pEngineName = "STORM GBCA Core";
        app.apiVersion = VK_API_VERSION_1_1;

        const char* inst_exts[] = { "VK_KHR_surface", "VK_KHR_android_surface" };
        VkInstanceCreateInfo ici{};
        ici.sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO;
        ici.pApplicationInfo = &app;
        ici.enabledExtensionCount = 2;
        ici.ppEnabledExtensionNames = inst_exts;

        if (vk_.createInstance(&ici, nullptr, &instance_) != VK_SUCCESS) {
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
        LOGI("Vulkan initialized on %s (%ux%u)", device_name_.c_str(), swap_extent_.width, swap_extent_.height);
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

        if (!ensure_source_image_locked(static_cast<uint32_t>(w), static_cast<uint32_t>(h))) return;

        // Upload RGBA frame into host-visible staging buffer
        size_t bytes = static_cast<size_t>(w) * static_cast<size_t>(h) * 4;
        void* mapped = nullptr;
        if (vk_.mapMemory(device_, staging_mem_, 0, bytes, 0, &mapped) == VK_SUCCESS && mapped) {
            std::memcpy(mapped, rgba, bytes);
            vk_.unmapMemory(device_, staging_mem_);
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
        region.imageExtent = { static_cast<uint32_t>(w), static_cast<uint32_t>(h), 1 };
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
        if (ensure_shader_out_image_locked(dst_w, dst_h)) {
            transition_image(cmd_buf_, src_img_, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, VK_IMAGE_LAYOUT_SHADER_READ_ONLY_OPTIMAL,
                             VK_ACCESS_TRANSFER_WRITE_BIT, VK_ACCESS_SHADER_READ_BIT,
                             VK_PIPELINE_STAGE_TRANSFER_BIT, VK_PIPELINE_STAGE_FRAGMENT_SHADER_BIT);
            transition_image(cmd_buf_, shader_out_img_, VK_IMAGE_LAYOUT_UNDEFINED, VK_IMAGE_LAYOUT_COLOR_ATTACHMENT_OPTIMAL,
                             0, VK_ACCESS_COLOR_ATTACHMENT_WRITE_BIT,
                             VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT, VK_PIPELINE_STAGE_COLOR_ATTACHMENT_OUTPUT_BIT);

            shaded = librashader_apply_vk(
                instance_, phys_, device_, queue_, vk_.getInstanceProcAddr, cmd_buf_,
                src_img_, VK_FORMAT_R8G8B8A8_UNORM, static_cast<uint32_t>(w), static_cast<uint32_t>(h),
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
                // Transition src_img back from SHADER_READ_ONLY to TRANSFER_SRC for direct blit
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
            blit.srcOffsets[1] = { w, h, 1 };
            blit.dstSubresource = { VK_IMAGE_ASPECT_COLOR_BIT, 0, 0, 1 };
            blit.dstOffsets[0] = { dx0, dy0, 0 };
            blit.dstOffsets[1] = { dx1, dy1, 1 };
            vk_.cmdBlitImage(cmd_buf_, src_img_, VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
                             swap_img, VK_IMAGE_LAYOUT_TRANSFER_DST_OPTIMAL, 1, &blit,
                             linear_filter ? VK_FILTER_LINEAR : VK_FILTER_NEAREST);
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
    VkImage src_img_ = VK_NULL_HANDLE;
    VkDeviceMemory src_mem_ = VK_NULL_HANDLE;
    uint32_t src_w_ = 0, src_h_ = 0;

    VkImage shader_out_img_ = VK_NULL_HANDLE;
    VkDeviceMemory shader_out_mem_ = VK_NULL_HANDLE;
    uint32_t shader_out_w_ = 0, shader_out_h_ = 0;

    bool ready_ = false;
    bool need_recreate_ = false;
    std::string device_name_;
};

static VulkanRenderer g_vk;

} // namespace

bool vk_probe_support(std::string& device_name) { return g_vk.probe_support(device_name); }
bool vk_init(ANativeWindow* window, const std::string& custom_driver_path) { return g_vk.init(window, custom_driver_path); }
void vk_resize(int w, int h) { g_vk.resize(w, h); }
void vk_render(const uint32_t* rgba, int w, int h, int vp_x, int vp_y, int vp_w, int vp_h, bool linear_filter, int rotation_deg) {
    g_vk.render(rgba, w, h, vp_x, vp_y, vp_w, vp_h, linear_filter, rotation_deg);
}
void vk_destroy() { g_vk.destroy(); }
std::string vk_device_name() { return g_vk.device_name(); }

} // namespace storm

#endif // ANDROID
