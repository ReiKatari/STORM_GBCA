#ifdef ANDROID

#include <GLES3/gl3.h>
#include <EGL/egl.h>
#include <vulkan/vulkan.h>
#include <android/log.h>
#include <dlfcn.h>

#include <algorithm>
#include <atomic>
#include <chrono>
#include <mutex>
#include <string>

#define LIBRA_RUNTIME_OPENGL 1
#define LIBRA_RUNTIME_VULKAN 1
#include "librashader.h"

#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, "StormLibrashader", __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "StormLibrashader", __VA_ARGS__)

namespace storm {

namespace {

const void* android_gl_loader(const char* name) {
    if (!name) return nullptr;
    void* p = reinterpret_cast<void*>(eglGetProcAddress(name));
    if (!p) p = dlsym(RTLD_DEFAULT, name);
    return p;
}

struct GlStateGuard {
    GLint draw_fbo = 0, read_fbo = 0, program = 0, vao = 0;
    GLint active_tex = GL_TEXTURE0, tex0 = 0, vp[4]{};
    GLboolean scissor = GL_FALSE, blend = GL_FALSE, depth = GL_FALSE, cull = GL_FALSE;

    GlStateGuard() {
        glGetIntegerv(GL_DRAW_FRAMEBUFFER_BINDING, &draw_fbo);
        glGetIntegerv(GL_READ_FRAMEBUFFER_BINDING, &read_fbo);
        glGetIntegerv(GL_CURRENT_PROGRAM, &program);
        glGetIntegerv(GL_VERTEX_ARRAY_BINDING, &vao);
        glGetIntegerv(GL_ACTIVE_TEXTURE, &active_tex);
        glActiveTexture(GL_TEXTURE0);
        glGetIntegerv(GL_TEXTURE_BINDING_2D, &tex0);
        glActiveTexture(active_tex);
        glGetIntegerv(GL_VIEWPORT, vp);
        scissor = glIsEnabled(GL_SCISSOR_TEST);
        blend = glIsEnabled(GL_BLEND);
        depth = glIsEnabled(GL_DEPTH_TEST);
        cull = glIsEnabled(GL_CULL_FACE);
    }

    ~GlStateGuard() {
        glUseProgram(program);
        glBindVertexArray(vao);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex0);
        glActiveTexture(active_tex);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, read_fbo);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw_fbo);
        glViewport(vp[0], vp[1], vp[2], vp[3]);
        if (scissor) glEnable(GL_SCISSOR_TEST); else glDisable(GL_SCISSOR_TEST);
        if (blend) glEnable(GL_BLEND); else glDisable(GL_BLEND);
        if (depth) glEnable(GL_DEPTH_TEST); else glDisable(GL_DEPTH_TEST);
        if (cull) glEnable(GL_CULL_FACE); else glDisable(GL_CULL_FACE);
    }
};

struct LibraLoader {
    std::mutex mu;
    void* handle = nullptr;
    bool attempted = false;

    PFN_libra_error_free error_free = nullptr;
    PFN_libra_error_write error_write = nullptr;
    PFN_libra_error_free_string error_free_string = nullptr;
    PFN_libra_preset_ctx_create preset_ctx_create = nullptr;
    PFN_libra_preset_ctx_free preset_ctx_free = nullptr;
    PFN_libra_preset_ctx_set_runtime preset_ctx_set_runtime = nullptr;
    PFN_libra_preset_create_with_options preset_create_with_options = nullptr;
    PFN_libra_preset_free preset_free = nullptr;

    PFN_libra_gl_filter_chain_create gl_create = nullptr;
    PFN_libra_gl_filter_chain_frame gl_frame = nullptr;
    PFN_libra_gl_filter_chain_free gl_free = nullptr;

    PFN_libra_vk_filter_chain_create vk_create = nullptr;
    PFN_libra_vk_filter_chain_frame vk_frame = nullptr;
    PFN_libra_vk_filter_chain_free vk_free = nullptr;

    bool ensure_loaded() {
        std::lock_guard<std::mutex> lock(mu);
        if (handle) return preset_ctx_create && preset_create_with_options && preset_free;
        if (attempted) return false;
        attempted = true;
        handle = dlopen("liblibrashader.so", RTLD_NOW | RTLD_LOCAL);
        if (!handle) {
            LOGE("dlopen(liblibrashader.so) failed: %s", dlerror());
            return false;
        }
        auto load = [&](auto& fn, const char* sym) {
            fn = reinterpret_cast<std::decay_t<decltype(fn)>>(dlsym(handle, sym));
        };
        load(error_free, "libra_error_free");
        load(error_write, "libra_error_write");
        load(error_free_string, "libra_error_free_string");
        load(preset_ctx_create, "libra_preset_ctx_create");
        load(preset_ctx_free, "libra_preset_ctx_free");
        load(preset_ctx_set_runtime, "libra_preset_ctx_set_runtime");
        load(preset_create_with_options, "libra_preset_create_with_options");
        load(preset_free, "libra_preset_free");
        load(gl_create, "libra_gl_filter_chain_create");
        load(gl_frame, "libra_gl_filter_chain_frame");
        load(gl_free, "libra_gl_filter_chain_free");
        load(vk_create, "libra_vk_filter_chain_create");
        load(vk_frame, "libra_vk_filter_chain_frame");
        load(vk_free, "libra_vk_filter_chain_free");
        LOGI("liblibrashader.so loaded successfully (gl=%d, vk=%d)", gl_create ? 1 : 0, vk_create ? 1 : 0);
        return preset_ctx_create && preset_create_with_options && preset_free;
    }

    std::string consume_error(const char* where, libra_error_t err) {
        if (!err) return "";
        std::string msg = where ? where : "librashader error";
        if (error_write) {
            char* s = nullptr;
            if (error_write(err, &s) == 0 && s) {
                msg += ": ";
                msg += s;
                if (error_free_string) error_free_string(&s);
            }
        }
        if (error_free) error_free(&err);
        LOGE("%s", msg.c_str());
        return msg;
    }
};

LibraLoader& loader() {
    static LibraLoader inst;
    return inst;
}

class LibrashaderHost {
public:
    bool is_available() { return loader().ensure_loaded(); }

    std::string status() {
        std::lock_guard<std::mutex> lock(mu_);
        return last_status_;
    }

    void set_preset(const std::string& path) {
        std::lock_guard<std::mutex> lock(mu_);
        if (desired_preset_ != path) {
            desired_preset_ = path;
            failed_preset_.clear();
        }
    }

    std::string get_preset() {
        std::lock_guard<std::mutex> lock(mu_);
        return desired_preset_;
    }

    void reset_gl() {
        std::lock_guard<std::mutex> lock(mu_);
        reset_gl_locked();
    }

    void reset_vk() {
        std::lock_guard<std::mutex> lock(mu_);
        reset_vk_locked();
    }

    bool apply_gl(GLuint src_tex, uint32_t src_w, uint32_t src_h,
                  int vp_x, int vp_y, uint32_t vp_w, uint32_t vp_h, int rotation_deg) {
        std::lock_guard<std::mutex> lock(mu_);
        if (desired_preset_.empty() || src_tex == 0 || src_w == 0 || src_h == 0 || vp_w == 0 || vp_h == 0) {
            if (gl_chain_) reset_gl_locked();
            return false;
        }
        if (failed_preset_ == desired_preset_) return false;

        auto& api = loader();
        if (!api.ensure_loaded() || !api.gl_create || !api.gl_frame) return false;

        if (gl_chain_ && gl_active_preset_ != desired_preset_) {
            reset_gl_locked();
        }

        GlStateGuard guard;

        if (!gl_chain_) {
            libra_preset_ctx_t ctx = nullptr;
            libra_shader_preset_t preset = nullptr;
            if (libra_error_t err = api.preset_ctx_create(&ctx)) {
                last_status_ = api.consume_error("gl.ctx_create", err);
                failed_preset_ = desired_preset_;
                return false;
            }
            if (api.preset_ctx_set_runtime) {
                api.preset_ctx_set_runtime(&ctx, LIBRA_PRESET_CTX_RUNTIME_GL_CORE);
            }
            libra_preset_opt_t p_opt{};
            p_opt.version = LIBRASHADER_CURRENT_ABI;
            p_opt.original_aspect_uniforms = true;
            p_opt.frametime_uniforms = true;

            if (libra_error_t err = api.preset_create_with_options(desired_preset_.c_str(), &ctx, &p_opt, &preset)) {
                last_status_ = api.consume_error("gl.preset_create", err);
                if (ctx) api.preset_ctx_free(&ctx);
                failed_preset_ = desired_preset_;
                return false;
            }
            if (ctx) api.preset_ctx_free(&ctx);

            filter_chain_gl_opt_t c_opt{};
            c_opt.version = LIBRASHADER_CURRENT_ABI;
            c_opt.glsl_version = 0;
            c_opt.use_dsa = false;
            c_opt.force_no_mipmaps = false;
            c_opt.disable_cache = false;

            if (libra_error_t err = api.gl_create(&preset, &android_gl_loader, &c_opt, &gl_chain_)) {
                last_status_ = api.consume_error("gl.chain_create", err);
                if (preset) api.preset_free(&preset);
                failed_preset_ = desired_preset_;
                return false;
            }
            gl_active_preset_ = desired_preset_;
            gl_frame_count_ = 0;
            last_status_ = "OpenGL ES 3.2: " + desired_preset_;
        }

        if (gl_out_tex_ == 0 || gl_out_w_ != vp_w || gl_out_h_ != vp_h) {
            if (!gl_out_fbo_) glGenFramebuffers(1, &gl_out_fbo_);
            if (!gl_out_tex_) glGenTextures(1, &gl_out_tex_);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, gl_out_tex_);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, vp_w, vp_h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindFramebuffer(GL_FRAMEBUFFER, gl_out_fbo_);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, gl_out_tex_, 0);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            gl_out_w_ = vp_w;
            gl_out_h_ = vp_h;
        }

        gl_frame_count_++;
        libra_image_gl_t src{ src_tex, GL_RGBA8, src_w, src_h };
        libra_image_gl_t dst{ gl_out_tex_, GL_RGBA8, vp_w, vp_h };
        libra_viewport_t vp{ 0.0f, 0.0f, vp_w, vp_h };

        frame_gl_opt_t f_opt{};
        f_opt.version = LIBRASHADER_CURRENT_ABI;
        f_opt.clear_history = (gl_frame_count_ <= 1);
        f_opt.frame_direction = 1;
        f_opt.rotation = static_cast<uint32_t>((rotation_deg / 90) & 3);
        f_opt.total_subframes = 1;
        f_opt.current_subframe = 1;
        f_opt.aspect_ratio = static_cast<float>(vp_w) / std::max(1.0f, static_cast<float>(vp_h));
        f_opt.frames_per_second = 59.7275f;
        f_opt.frametime_delta = 16;

        if (libra_error_t err = api.gl_frame(&gl_chain_, static_cast<size_t>(gl_frame_count_), src, dst, &vp, nullptr, &f_opt)) {
            last_status_ = api.consume_error("gl.chain_frame", err);
            failed_preset_ = desired_preset_;
            return false;
        }

        // Blit filtered texture onto default framebuffer at exact custom viewport (flip Y for top-down RGBA)
        glDisable(GL_SCISSOR_TEST);
        glColorMask(GL_TRUE, GL_TRUE, GL_TRUE, GL_TRUE);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, gl_out_fbo_);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, 0);
        glBlitFramebuffer(0, static_cast<GLint>(vp_h), static_cast<GLint>(vp_w), 0,
                          vp_x, vp_y, vp_x + static_cast<GLint>(vp_w), vp_y + static_cast<GLint>(vp_h),
                          GL_COLOR_BUFFER_BIT, GL_LINEAR);
        return true;
    }

    bool apply_vk(VkInstance instance, VkPhysicalDevice phys, VkDevice device, VkQueue queue,
                  PFN_vkGetInstanceProcAddr vk_entry, VkCommandBuffer cmd,
                  VkImage src_img, VkFormat src_fmt, uint32_t src_w, uint32_t src_h,
                  VkImage dst_img, VkFormat dst_fmt, uint32_t dst_w, uint32_t dst_h,
                  uint32_t frames_in_flight, int rotation_deg) {
        std::lock_guard<std::mutex> lock(mu_);
        if (desired_preset_.empty() || !device || !src_img || !dst_img || src_w == 0 || src_h == 0 || dst_w == 0 || dst_h == 0) {
            if (vk_chain_) reset_vk_locked();
            return false;
        }
        if (failed_preset_ == desired_preset_) return false;

        auto& api = loader();
        if (!api.ensure_loaded() || !api.vk_create || !api.vk_frame) return false;

        if (vk_chain_ && (vk_active_preset_ != desired_preset_ || vk_device_ != device)) {
            reset_vk_locked();
        }

        if (!vk_chain_) {
            libra_preset_ctx_t ctx = nullptr;
            libra_shader_preset_t preset = nullptr;
            if (libra_error_t err = api.preset_ctx_create(&ctx)) {
                last_status_ = api.consume_error("vk.ctx_create", err);
                failed_preset_ = desired_preset_;
                return false;
            }
            if (api.preset_ctx_set_runtime) {
                api.preset_ctx_set_runtime(&ctx, LIBRA_PRESET_CTX_RUNTIME_VULKAN);
            }
            libra_preset_opt_t p_opt{};
            p_opt.version = LIBRASHADER_CURRENT_ABI;
            p_opt.original_aspect_uniforms = true;
            p_opt.frametime_uniforms = true;

            if (libra_error_t err = api.preset_create_with_options(desired_preset_.c_str(), &ctx, &p_opt, &preset)) {
                last_status_ = api.consume_error("vk.preset_create", err);
                if (ctx) api.preset_ctx_free(&ctx);
                failed_preset_ = desired_preset_;
                return false;
            }
            if (ctx) api.preset_ctx_free(&ctx);

            libra_device_vk_t vk_info{};
            vk_info.physical_device = phys;
            vk_info.instance = instance;
            vk_info.device = device;
            vk_info.queue = queue;
            vk_info.entry = vk_entry;

            filter_chain_vk_opt_t c_opt{};
            c_opt.version = LIBRASHADER_CURRENT_ABI;
            c_opt.frames_in_flight = std::max<uint32_t>(2u, frames_in_flight);
            c_opt.force_no_mipmaps = false;
            c_opt.use_dynamic_rendering = false;
            c_opt.disable_cache = false;

            if (libra_error_t err = api.vk_create(&preset, vk_info, &c_opt, &vk_chain_)) {
                last_status_ = api.consume_error("vk.chain_create", err);
                if (preset) api.preset_free(&preset);
                failed_preset_ = desired_preset_;
                return false;
            }
            vk_active_preset_ = desired_preset_;
            vk_device_ = device;
            vk_frame_count_ = 0;
            last_status_ = "Vulkan: " + desired_preset_;
        }

        vk_frame_count_++;
        libra_image_vk_t src{ src_img, src_fmt, src_w, src_h };
        libra_image_vk_t dst{ dst_img, dst_fmt, dst_w, dst_h };
        libra_viewport_t vp{ 0.0f, 0.0f, dst_w, dst_h };

        frame_vk_opt_t f_opt{};
        f_opt.version = LIBRASHADER_CURRENT_ABI;
        f_opt.clear_history = (vk_frame_count_ <= 1);
        f_opt.frame_direction = 1;
        f_opt.rotation = static_cast<uint32_t>((rotation_deg / 90) & 3);
        f_opt.total_subframes = 1;
        f_opt.current_subframe = 1;
        f_opt.aspect_ratio = static_cast<float>(dst_w) / std::max(1.0f, static_cast<float>(dst_h));
        f_opt.frames_per_second = 59.7275f;
        f_opt.frametime_delta = 16;

        if (libra_error_t err = api.vk_frame(&vk_chain_, cmd, static_cast<size_t>(vk_frame_count_), src, dst, &vp, nullptr, &f_opt)) {
            last_status_ = api.consume_error("vk.chain_frame", err);
            failed_preset_ = desired_preset_;
            return false;
        }
        return true;
    }

private:
    void reset_gl_locked() {
        auto& api = loader();
        if (gl_chain_ && api.gl_free) {
            api.consume_error("gl.chain_free", api.gl_free(&gl_chain_));
        }
        gl_chain_ = nullptr;
        if (gl_out_fbo_) { glDeleteFramebuffers(1, &gl_out_fbo_); gl_out_fbo_ = 0; }
        if (gl_out_tex_) { glDeleteTextures(1, &gl_out_tex_); gl_out_tex_ = 0; }
        gl_out_w_ = gl_out_h_ = 0;
        gl_active_preset_.clear();
        gl_frame_count_ = 0;
    }

    void reset_vk_locked() {
        auto& api = loader();
        if (vk_chain_ && api.vk_free) {
            api.consume_error("vk.chain_free", api.vk_free(&vk_chain_));
        }
        vk_chain_ = nullptr;
        vk_device_ = VK_NULL_HANDLE;
        vk_active_preset_.clear();
        vk_frame_count_ = 0;
    }

    std::mutex mu_;
    std::string desired_preset_;
    std::string failed_preset_;
    std::string last_status_ = "Ожидание пресета";

    libra_gl_filter_chain_t gl_chain_ = nullptr;
    std::string gl_active_preset_;
    GLuint gl_out_tex_ = 0;
    GLuint gl_out_fbo_ = 0;
    uint32_t gl_out_w_ = 0;
    uint32_t gl_out_h_ = 0;
    uint64_t gl_frame_count_ = 0;

    libra_vk_filter_chain_t vk_chain_ = nullptr;
    VkDevice vk_device_ = VK_NULL_HANDLE;
    std::string vk_active_preset_;
    uint64_t vk_frame_count_ = 0;
};

static LibrashaderHost g_libra;

} // namespace

bool librashader_is_available() { return g_libra.is_available(); }
std::string librashader_status() { return g_libra.status(); }
void librashader_set_preset(const std::string& path) { g_libra.set_preset(path); }
void librashader_reset_gl() { g_libra.reset_gl(); }
void librashader_reset_vk() { g_libra.reset_vk(); }

bool librashader_apply_gl(GLuint src_tex, uint32_t src_w, uint32_t src_h,
                          int vp_x, int vp_y, uint32_t vp_w, uint32_t vp_h, int rotation_deg) {
    return g_libra.apply_gl(src_tex, src_w, src_h, vp_x, vp_y, vp_w, vp_h, rotation_deg);
}

bool librashader_apply_vk(VkInstance instance, VkPhysicalDevice phys, VkDevice device, VkQueue queue,
                          PFN_vkGetInstanceProcAddr vk_entry, VkCommandBuffer cmd,
                          VkImage src_img, VkFormat src_fmt, uint32_t src_w, uint32_t src_h,
                          VkImage dst_img, VkFormat dst_fmt, uint32_t dst_w, uint32_t dst_h,
                          uint32_t frames_in_flight, int rotation_deg) {
    return g_libra.apply_vk(instance, phys, device, queue, vk_entry, cmd,
                            src_img, src_fmt, src_w, src_h, dst_img, dst_fmt, dst_w, dst_h,
                            frames_in_flight, rotation_deg);
}

} // namespace storm

#endif // ANDROID
