#ifdef ANDROID

#include <GLES3/gl3.h>
#include <android/log.h>
#include "storm_gbca_core.h"
#include <algorithm>
#include <string>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "StormGbcaGLES3", __VA_ARGS__)

namespace storm {

static std::string strip_version_directive(const std::string& src) {
    std::string out = src;
    size_t pos = 0;
    while ((pos = out.find("#version 300 es", pos)) != std::string::npos) {
        out.replace(pos, 15, "// #version 300 es");
        pos += 18;
    }
    return out;
}

static GLuint compile_shader(GLenum type, const char* src) {
    GLuint s = glCreateShader(type);
    glShaderSource(s, 1, &src, nullptr);
    glCompileShader(s);
    GLint ok = 0;
    glGetShaderiv(s, GL_COMPILE_STATUS, &ok);
    if (!ok) {
        char log[1024] = {};
        glGetShaderInfoLog(s, sizeof(log) - 1, nullptr, log);
        LOGE("Shader compile failed: %s", log);
        glDeleteShader(s);
        return 0;
    }
    return s;
}

static const char* kFallbackShader = R"GLSL(
#ifdef STORM_VERTEX_SHADER
layout(location = 0) in vec2 a_pos;
layout(location = 1) in vec2 a_uv;
out vec2 v_uv;
void main() {
    v_uv = a_uv;
    gl_Position = vec4(a_pos, 0.0, 1.0);
}
#endif
#ifdef STORM_FRAGMENT_SHADER
precision highp float;
uniform sampler2D u_tex;
in vec2 v_uv;
out vec4 frag_color;
void main() {
    frag_color = texture(u_tex, v_uv);
}
#endif
)GLSL";

class Gles3Renderer {
public:
    bool init(const std::string& shader_source) {
        destroy();
        std::string body = shader_source.empty()
            ? std::string(kFallbackShader)
            : strip_version_directive(shader_source);

        std::string vs_src = "#version 300 es\n#define STORM_VERTEX_SHADER\n" + body;
        std::string fs_src = "#version 300 es\n#define STORM_FRAGMENT_SHADER\n" + body;

        GLuint vs = compile_shader(GL_VERTEX_SHADER, vs_src.c_str());
        GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fs_src.c_str());
        if ((!vs || !fs) && !shader_source.empty()) {
            if (vs) glDeleteShader(vs);
            if (fs) glDeleteShader(fs);
            std::string fb(kFallbackShader);
            vs = compile_shader(GL_VERTEX_SHADER, ("#version 300 es\n#define STORM_VERTEX_SHADER\n" + fb).c_str());
            fs = compile_shader(GL_FRAGMENT_SHADER, ("#version 300 es\n#define STORM_FRAGMENT_SHADER\n" + fb).c_str());
        }
        if (!vs || !fs) {
            if (vs) glDeleteShader(vs);
            if (fs) glDeleteShader(fs);
            return false;
        }

        prog_ = glCreateProgram();
        glAttachShader(prog_, vs);
        glAttachShader(prog_, fs);
        glLinkProgram(prog_);
        glDeleteShader(vs);
        glDeleteShader(fs);

        GLint ok = 0;
        glGetProgramiv(prog_, GL_LINK_STATUS, &ok);
        if (!ok) {
            char log[1024] = {};
            glGetProgramInfoLog(prog_, sizeof(log) - 1, nullptr, log);
            LOGE("Program link failed: %s", log);
            glDeleteProgram(prog_);
            prog_ = 0;
            return false;
        }

        glGenTextures(2, tex_);
        for (int i = 0; i < 2; ++i) {
            glBindTexture(GL_TEXTURE_2D, tex_[i]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }

        static const float quad[] = {
            -1.0f, -1.0f, 0.0f, 1.0f,
             1.0f, -1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 0.0f,
        };
        glGenVertexArrays(1, &vao_);
        glGenBuffers(1, &vbo_);
        glBindVertexArray(vao_);
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);
        glBufferData(GL_ARRAY_BUFFER, sizeof(quad), quad, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), nullptr);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), reinterpret_cast<void*>(2 * sizeof(float)));

        loc_tex_ = glGetUniformLocation(prog_, "u_tex");
        loc_prev_tex_ = glGetUniformLocation(prog_, "u_prev_tex");
        loc_lcd_size_ = glGetUniformLocation(prog_, "u_emu_lcd_size");
        loc_render_size_ = glGetUniformLocation(prog_, "u_render_size");
        loc_display_mode_ = glGetUniformLocation(prog_, "u_display_mode");
        loc_grayscale_ = glGetUniformLocation(prog_, "u_lcd_is_grayscale");
        loc_integer_ = glGetUniformLocation(prog_, "u_integer_scaling");
        loc_cc_strength_ = glGetUniformLocation(prog_, "u_color_correction_strength");
        loc_red_ = glGetUniformLocation(prog_, "u_red_color");
        loc_green_ = glGetUniformLocation(prog_, "u_green_color");
        loc_blue_ = glGetUniformLocation(prog_, "u_blue_color");
        loc_gamma_ = glGetUniformLocation(prog_, "u_input_gamma");
        loc_ghosting_ = glGetUniformLocation(prog_, "u_ghosting");
        has_prev_ = false;
        return true;
    }

    void resize(int surface_w, int surface_h) {
        if (surface_w <= 0 || surface_h <= 0) return;
        surface_w_ = surface_w;
        surface_h_ = surface_h;
    }

    void render(const uint32_t* rgba, int w, int h,
                int vp_x, int vp_y, int vp_w, int vp_h,
                int display_mode, float ghosting, float cc_strength,
                bool is_grayscale, bool integer_scaling) {
        if (!prog_ || surface_w_ <= 0 || surface_h_ <= 0) return;

        glViewport(0, 0, surface_w_, surface_h_);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        if (!rgba || w <= 0 || h <= 0 || vp_w <= 0 || vp_h <= 0) return;

        if (w != last_w_ || h != last_h_) {
            has_prev_ = false;
            last_w_ = w;
            last_h_ = h;
        }

        int prev = cur_tex_ ^ 1;
        glBindTexture(GL_TEXTURE_2D, tex_[cur_tex_]);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
        if (!has_prev_) {
            glBindTexture(GL_TEXTURE_2D, tex_[prev]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            has_prev_ = true;
        }

        glViewport(vp_x, vp_y, vp_w, vp_h);
        glUseProgram(prog_);

        if (loc_tex_ >= 0) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, tex_[cur_tex_]);
            glUniform1i(loc_tex_, 0);
        }
        if (loc_prev_tex_ >= 0) {
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, tex_[prev]);
            glUniform1i(loc_prev_tex_, 1);
        }
        if (loc_lcd_size_ >= 0) glUniform2f(loc_lcd_size_, static_cast<float>(w), static_cast<float>(h));
        if (loc_render_size_ >= 0) glUniform2f(loc_render_size_, static_cast<float>(vp_w), static_cast<float>(vp_h));
        if (loc_display_mode_ >= 0) glUniform1i(loc_display_mode_, display_mode);
        if (loc_grayscale_ >= 0) glUniform1f(loc_grayscale_, is_grayscale ? 1.0f : 0.0f);
        if (loc_integer_ >= 0) glUniform1f(loc_integer_, integer_scaling ? 1.0f : 0.0f);
        if (loc_ghosting_ >= 0) glUniform1f(loc_ghosting_, std::clamp(ghosting, 0.0f, 1.0f));
        if (loc_cc_strength_ >= 0) glUniform1f(loc_cc_strength_, std::clamp(cc_strength, 0.0f, 1.0f));
        if (loc_red_ >= 0) glUniform3f(loc_red_, 0.82f, 0.24f, -0.06f);
        if (loc_green_ >= 0) glUniform3f(loc_green_, 0.125f, 0.665f, 0.21f);
        if (loc_blue_ >= 0) glUniform3f(loc_blue_, 0.195f, 0.075f, 0.73f);
        if (loc_gamma_ >= 0) glUniform1f(loc_gamma_, 2.2f);

        glBindVertexArray(vao_);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
        cur_tex_ = prev;
    }

    void destroy() {
        if (vbo_) { glDeleteBuffers(1, &vbo_); vbo_ = 0; }
        if (vao_) { glDeleteVertexArrays(1, &vao_); vao_ = 0; }
        if (tex_[0] || tex_[1]) { glDeleteTextures(2, tex_); tex_[0] = tex_[1] = 0; }
        if (prog_) { glDeleteProgram(prog_); prog_ = 0; }
        has_prev_ = false;
        last_w_ = 0;
        last_h_ = 0;
    }

private:
    GLuint prog_ = 0;
    GLuint tex_[2] = {0, 0};
    GLuint vao_ = 0;
    GLuint vbo_ = 0;
    int cur_tex_ = 0;
    bool has_prev_ = false;
    int last_w_ = 0;
    int last_h_ = 0;
    int surface_w_ = 1;
    int surface_h_ = 1;

    GLint loc_tex_ = -1;
    GLint loc_prev_tex_ = -1;
    GLint loc_lcd_size_ = -1;
    GLint loc_render_size_ = -1;
    GLint loc_display_mode_ = -1;
    GLint loc_grayscale_ = -1;
    GLint loc_integer_ = -1;
    GLint loc_cc_strength_ = -1;
    GLint loc_red_ = -1;
    GLint loc_green_ = -1;
    GLint loc_blue_ = -1;
    GLint loc_gamma_ = -1;
    GLint loc_ghosting_ = -1;
};

static Gles3Renderer g_gles3;

bool gles_init(const std::string& shader_source) { return g_gles3.init(shader_source); }
void gles_resize(int w, int h) { g_gles3.resize(w, h); }
void gles_render(const uint32_t* rgba, int w, int h, int vp_x, int vp_y, int vp_w, int vp_h,
                 int display_mode, float ghosting, float cc_strength, bool is_grayscale, bool integer_scaling) {
    g_gles3.render(rgba, w, h, vp_x, vp_y, vp_w, vp_h, display_mode, ghosting, cc_strength, is_grayscale, integer_scaling);
}
void gles_destroy() { g_gles3.destroy(); }

} // namespace storm

#endif // ANDROID
