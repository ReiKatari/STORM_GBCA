#ifdef ANDROID

#include <GLES3/gl3.h>
#include <android/log.h>
#include "storm_gbca_core.h"
#include "storm_postprocess.hpp"
#include <algorithm>
#include <cmath>
#include <mutex>
#include <string>

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "StormGbcaGLES3", __VA_ARGS__)

namespace storm {

extern bool librashader_apply_gl(GLuint src_tex, uint32_t src_w, uint32_t src_h,
                                 int vp_x, int vp_y, uint32_t vp_w, uint32_t vp_h, int rotation_deg);
extern void librashader_reset_gl();

static std::mutex g_gles_ver_mu;
static std::string g_gles_version_str;

std::string gles_get_version_string() {
    std::lock_guard<std::mutex> lock(g_gles_ver_mu);
    return g_gles_version_str;
}

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

static GLuint link_program(const char* vs_src, const char* fs_src) {
    GLuint vs = compile_shader(GL_VERTEX_SHADER, vs_src);
    GLuint fs = compile_shader(GL_FRAGMENT_SHADER, fs_src);
    if (!vs || !fs) {
        if (vs) glDeleteShader(vs);
        if (fs) glDeleteShader(fs);
        return 0;
    }
    GLuint prog = glCreateProgram();
    glAttachShader(prog, vs);
    glAttachShader(prog, fs);
    glLinkProgram(prog);
    glDeleteShader(vs);
    glDeleteShader(fs);
    GLint ok = 0;
    glGetProgramiv(prog, GL_LINK_STATUS, &ok);
    if (!ok) {
        char log[1024] = {};
        glGetProgramInfoLog(prog, sizeof(log) - 1, nullptr, log);
        LOGE("Program link failed: %s", log);
        glDeleteProgram(prog);
        return 0;
    }
    return prog;
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

static const char* kPostVertexShader = R"GLSL(#version 300 es
precision highp float;
layout(location = 0) in vec2 a_pos;
layout(location = 1) in vec2 a_uv;
out vec2 v_uv;
void main() {
    v_uv = a_uv;
    gl_Position = vec4(a_pos, 0.0, 1.0);
}
)GLSL";

static const char* kFrameGenFragmentShader = R"GLSL(#version 300 es
precision highp float;
uniform sampler2D u_prev_tex;
uniform sampler2D u_cur_tex;
uniform vec2 u_texel_size;
uniform float u_alpha;
in vec2 v_uv;
out vec4 frag_color;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec3 p0_center = texture(u_prev_tex, v_uv).rgb;
    vec3 p1_center = texture(u_cur_tex,  v_uv).rgb;

    float sum_ix2 = 0.0;
    float sum_iy2 = 0.0;
    float sum_ixiy = 0.0;
    float sum_ixit = 0.0;
    float sum_iyit = 0.0;
    float sum_it_abs = 0.0;

    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 uv = v_uv + vec2(float(x), float(y)) * u_texel_size;
            float l0_c = luma(texture(u_prev_tex, uv).rgb);
            float l1_c = luma(texture(u_cur_tex,  uv).rgb);
            float l0_r = luma(texture(u_prev_tex, uv + vec2( u_texel_size.x, 0.0)).rgb);
            float l0_l = luma(texture(u_prev_tex, uv + vec2(-u_texel_size.x, 0.0)).rgb);
            float l0_d = luma(texture(u_prev_tex, uv + vec2(0.0,  u_texel_size.y)).rgb);
            float l0_u = luma(texture(u_prev_tex, uv + vec2(0.0, -u_texel_size.y)).rgb);
            float l1_r = luma(texture(u_cur_tex,  uv + vec2( u_texel_size.x, 0.0)).rgb);
            float l1_l = luma(texture(u_cur_tex,  uv + vec2(-u_texel_size.x, 0.0)).rgb);
            float l1_d = luma(texture(u_cur_tex,  uv + vec2(0.0,  u_texel_size.y)).rgb);
            float l1_u = luma(texture(u_cur_tex,  uv + vec2(0.0, -u_texel_size.y)).rgb);

            float ix = 0.25 * ((l0_r - l0_l) + (l1_r - l1_l));
            float iy = 0.25 * ((l0_d - l0_u) + (l1_d - l1_u));
            float it = l1_c - l0_c;

            sum_ix2  += ix * ix;
            sum_iy2  += iy * iy;
            sum_ixiy += ix * iy;
            sum_ixit += ix * it;
            sum_iyit += iy * it;
            sum_it_abs += abs(it);
        }
    }

    vec2 mv = vec2(0.0);
    if (sum_it_abs > 0.025) {
        float det = sum_ix2 * sum_iy2 - sum_ixiy * sum_ixiy + 0.0025;
        mv.x = (-sum_iy2 * sum_ixit + sum_ixiy * sum_iyit) / det;
        mv.y = (-sum_ix2 * sum_iyit + sum_ixiy * sum_ixit) / det;
        mv = clamp(mv, vec2(-4.0), vec2(4.0));
    }

    vec2 uv0 = clamp(v_uv - mv * u_texel_size * u_alpha, vec2(0.0), vec2(1.0));
    vec2 uv1 = clamp(v_uv + mv * u_texel_size * (1.0 - u_alpha), vec2(0.0), vec2(1.0));
    vec3 w0 = texture(u_prev_tex, uv0).rgb;
    vec3 w1 = texture(u_cur_tex,  uv1).rgb;

    float warped_sad = luma(abs(w0 - w1));
    float raw_sad = luma(abs(p0_center - p1_center));

    vec3 linear_mix = mix(p0_center, p1_center, u_alpha);
    vec3 warped_mix = mix(w0, w1, u_alpha);

    // Scene-cut and disocclusion fallback to prevent motion glitches
    float disocc_blend = smoothstep(0.12, 0.36, warped_sad);
    float cut_blend = smoothstep(0.40, 0.65, raw_sad);
    vec3 hard_cut = (u_alpha >= 0.5) ? p1_center : p0_center;
    vec3 res = mix(warped_mix, linear_mix, disocc_blend);
    res = mix(res, hard_cut, cut_blend);
    frag_color = vec4(clamp(res, 0.0, 1.0), 1.0);
}
)GLSL";

static const char* kFsr1EasuFragmentShader = R"GLSL(#version 300 es
precision highp float;
uniform sampler2D u_tex;
uniform sampler2D u_prev_tex;
uniform vec2 u_src_size;
uniform vec2 u_inv_src_size;
uniform float u_ghosting;
in vec2 v_uv;
out vec4 frag_color;

vec3 fetch_src(vec2 uv) {
    vec3 c = texture(u_tex, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
    if (u_ghosting > 0.001) {
        vec3 p = texture(u_prev_tex, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
        c = mix(c, p, clamp(u_ghosting * 0.55, 0.0, 0.65));
    }
    return c;
}

float fsr_luma(vec3 c) {
    return 0.5 * c.g + 0.25 * (c.r + c.b);
}

void easu_set(inout vec2 dir, inout float len, vec2 pp, bool biS, bool biT, bool biU, bool biV,
              float lA, float lB, float lC, float lD, float lE) {
    float w = 0.0;
    if (biS) w = (1.0 - pp.x) * (1.0 - pp.y);
    if (biT) w =        pp.x  * (1.0 - pp.y);
    if (biU) w = (1.0 - pp.x) *        pp.y;
    if (biV) w =        pp.x  *        pp.y;

    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = max(abs(dc), abs(cb));
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) / max(lenX, 1e-5), 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;

    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = max(abs(ec), abs(ca));
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) / max(lenY, 1e-5), 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

void easu_tap(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len2, float lob, float clp, vec3 c) {
    vec2 v;
    v.x = (off.x * ( dir.x)) + (off.y * dir.y);
    v.y = (off.x * (-dir.y)) + (off.y * dir.x);
    v *= len2;
    float d2 = min(dot(v, v), clp);
    float wB = (2.0 / 5.0) * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = (25.0 / 16.0) * wB - (25.0 / 16.0 - 1.0);
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

void main() {
    vec2 pp = v_uv * u_src_size - 0.5;
    vec2 fp = floor(pp);
    pp -= fp;
    vec2 p0 = (fp + 0.5) * u_inv_src_size;

    vec3 b = fetch_src(p0 + vec2( 0.0, -1.0) * u_inv_src_size);
    vec3 c = fetch_src(p0 + vec2( 1.0, -1.0) * u_inv_src_size);
    vec3 e = fetch_src(p0 + vec2(-1.0,  0.0) * u_inv_src_size);
    vec3 f = fetch_src(p0 + vec2( 0.0,  0.0) * u_inv_src_size);
    vec3 g = fetch_src(p0 + vec2( 1.0,  0.0) * u_inv_src_size);
    vec3 h = fetch_src(p0 + vec2( 2.0,  0.0) * u_inv_src_size);
    vec3 i = fetch_src(p0 + vec2(-1.0,  1.0) * u_inv_src_size);
    vec3 j = fetch_src(p0 + vec2( 0.0,  1.0) * u_inv_src_size);
    vec3 k = fetch_src(p0 + vec2( 1.0,  1.0) * u_inv_src_size);
    vec3 l = fetch_src(p0 + vec2( 2.0,  1.0) * u_inv_src_size);
    vec3 n = fetch_src(p0 + vec2( 0.0,  2.0) * u_inv_src_size);
    vec3 o = fetch_src(p0 + vec2( 1.0,  2.0) * u_inv_src_size);

    float bL = fsr_luma(b); float cL = fsr_luma(c);
    float eL = fsr_luma(e); float fL = fsr_luma(f);
    float gL = fsr_luma(g); float hL = fsr_luma(h);
    float iL = fsr_luma(i); float jL = fsr_luma(j);
    float kL = fsr_luma(k); float lL = fsr_luma(l);
    float nL = fsr_luma(n); float oL = fsr_luma(o);

    vec2 dir = vec2(0.0);
    float len = 0.0;
    easu_set(dir, len, pp, true,  false, false, false, bL, eL, fL, gL, jL);
    easu_set(dir, len, pp, false, true,  false, false, cL, fL, gL, hL, kL);
    easu_set(dir, len, pp, false, false, true,  false, fL, iL, jL, kL, nL);
    easu_set(dir, len, pp, false, false, false, true,  gL, jL, kL, lL, oL);

    float dirR = dot(dir, dir);
    if (dirR < (1.0 / 32768.0)) {
        dir = vec2(1.0, 0.0);
    } else {
        dir *= inversesqrt(dirR);
    }

    len = len * 0.5;
    len *= len;
    float stretch = 1.0 / max(abs(dir.x), abs(dir.y));
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
    float lob = 0.5 + (0.29 - 0.5) * len;
    float clp = 1.0 / lob;

    vec3 min4 = min(min(f, g), min(j, k));
    vec3 max4 = max(max(f, g), max(j, k));

    vec3 aC = vec3(0.0);
    float aW = 0.0;
    easu_tap(aC, aW, vec2( 0.0, -1.0) - pp, dir, len2, lob, clp, b);
    easu_tap(aC, aW, vec2( 1.0, -1.0) - pp, dir, len2, lob, clp, c);
    easu_tap(aC, aW, vec2(-1.0,  0.0) - pp, dir, len2, lob, clp, e);
    easu_tap(aC, aW, vec2( 0.0,  0.0) - pp, dir, len2, lob, clp, f);
    easu_tap(aC, aW, vec2( 1.0,  0.0) - pp, dir, len2, lob, clp, g);
    easu_tap(aC, aW, vec2( 2.0,  0.0) - pp, dir, len2, lob, clp, h);
    easu_tap(aC, aW, vec2(-1.0,  1.0) - pp, dir, len2, lob, clp, i);
    easu_tap(aC, aW, vec2( 0.0,  1.0) - pp, dir, len2, lob, clp, j);
    easu_tap(aC, aW, vec2( 1.0,  1.0) - pp, dir, len2, lob, clp, k);
    easu_tap(aC, aW, vec2( 2.0,  1.0) - pp, dir, len2, lob, clp, l);
    easu_tap(aC, aW, vec2( 0.0,  2.0) - pp, dir, len2, lob, clp, n);
    easu_tap(aC, aW, vec2( 1.0,  2.0) - pp, dir, len2, lob, clp, o);

    vec3 res = clamp(aC / max(aW, 1e-5), min4, max4);
    frag_color = vec4(res, 1.0);
}
)GLSL";

static const char* kFsr3TemporalFragmentShader = R"GLSL(#version 300 es
precision highp float;
uniform sampler2D u_tex;
uniform sampler2D u_prev_tex;
uniform sampler2D u_history_tex;
uniform vec2 u_src_size;
uniform vec2 u_inv_src_size;
uniform vec2 u_inv_stage_size;
uniform vec2 u_jitter;
uniform float u_reset_history;
uniform float u_ghosting;
in vec2 v_uv;
out vec4 frag_color;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

vec3 fetch_cur(vec2 uv) {
    vec3 c = texture(u_tex, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
    if (u_ghosting > 0.001) {
        vec3 p = texture(u_prev_tex, clamp(uv, vec2(0.0), vec2(1.0))).rgb;
        c = mix(c, p, clamp(u_ghosting * 0.55, 0.0, 0.65));
    }
    return c;
}

vec3 rgb_to_ycocg(vec3 c) {
    float y  = dot(c, vec3( 0.25, 0.50,  0.25));
    float co = dot(c, vec3( 0.50, 0.00, -0.50));
    float cg = dot(c, vec3(-0.25, 0.50, -0.25));
    return vec3(y, co, cg);
}

vec3 ycocg_to_rgb(vec3 ycc) {
    float tmp = ycc.x - ycc.z;
    return clamp(vec3(tmp + ycc.y, ycc.x + ycc.z, tmp - ycc.y), 0.0, 1.0);
}

vec3 clip_aabb(vec3 aabb_min, vec3 aabb_max, vec3 hist) {
    vec3 p_clip = 0.5 * (aabb_max + aabb_min);
    vec3 e_clip = 0.5 * (aabb_max - aabb_min) + vec3(1e-5);
    vec3 v_clip = hist - p_clip;
    vec3 v_unit = v_clip / e_clip;
    vec3 a_unit = abs(v_unit);
    float ma_unit = max(a_unit.x, max(a_unit.y, a_unit.z));
    if (ma_unit > 1.0) {
        return p_clip + v_clip / ma_unit;
    }
    return hist;
}

vec4 catmull_weights(float t) {
    float t2 = t * t;
    float t3 = t2 * t;
    return vec4(
        -0.5 * t + t2 - 0.5 * t3,
         1.0 - 2.5 * t2 + 1.5 * t3,
         0.5 * t + 2.0 * t2 - 1.5 * t3,
        -0.5 * t2 + 0.5 * t3
    );
}

vec3 sample_catmull_rom_16(vec2 uv) {
    vec2 pos = uv * u_src_size - 0.5;
    vec2 f = fract(pos);
    vec2 base = (floor(pos) + 0.5) * u_inv_src_size;
    vec4 wx = catmull_weights(f.x);
    vec4 wy = catmull_weights(f.y);

    vec3 sum = vec3(0.0);
    vec3 nmin = vec3(1.0);
    vec3 nmax = vec3(0.0);
    for (int j = -1; j <= 2; ++j) {
        float w_y = (j == -1) ? wy.x : ((j == 0) ? wy.y : ((j == 1) ? wy.z : wy.w));
        vec3 row = vec3(0.0);
        for (int i = -1; i <= 2; ++i) {
            float w_x = (i == -1) ? wx.x : ((i == 0) ? wx.y : ((i == 1) ? wx.z : wx.w));
            vec3 s = fetch_cur(base + vec2(float(i), float(j)) * u_inv_src_size);
            row += s * w_x;
            if (j >= 0 && j <= 1 && i >= 0 && i <= 1) {
                nmin = min(nmin, s);
                nmax = max(nmax, s);
            }
        }
        sum += row * w_y;
    }
    return clamp(sum, nmin, nmax);
}

void main() {
    vec2 jittered_uv = v_uv + u_jitter * u_inv_src_size;
    vec3 current_rgb = sample_catmull_rom_16(jittered_uv);

    if (u_reset_history > 0.5) {
        frag_color = vec4(current_rgb, 1.0);
        return;
    }

    // 3x3 YCoCg variance clipping (gamma = 1.25)
    vec3 m1 = vec3(0.0);
    vec3 m2 = vec3(0.0);
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec3 s = fetch_cur(v_uv + vec2(float(x), float(y)) * u_inv_src_size);
            vec3 ycc = rgb_to_ycocg(s);
            m1 += ycc;
            m2 += ycc * ycc;
        }
    }
    vec3 mu = m1 / 9.0;
    vec3 sigma = sqrt(max(m2 / 9.0 - mu * mu, vec3(0.0)));
    vec3 aabb_min = mu - 1.25 * sigma;
    vec3 aabb_max = mu + 1.25 * sigma;

    // Luma-gradient optical flow reprojection from previous source frame to current
    float sum_ix2 = 0.0, sum_iy2 = 0.0, sum_ixiy = 0.0, sum_ixit = 0.0, sum_iyit = 0.0;
    float sum_it_abs = 0.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 uv = v_uv + vec2(float(x), float(y)) * u_inv_src_size;
            float l0 = luma(texture(u_prev_tex, clamp(uv, vec2(0.0), vec2(1.0))).rgb);
            float l1 = luma(texture(u_tex,      clamp(uv, vec2(0.0), vec2(1.0))).rgb);
            float l1_r = luma(texture(u_tex, clamp(uv + vec2( u_inv_src_size.x, 0.0), vec2(0.0), vec2(1.0))).rgb);
            float l1_l = luma(texture(u_tex, clamp(uv + vec2(-u_inv_src_size.x, 0.0), vec2(0.0), vec2(1.0))).rgb);
            float l1_d = luma(texture(u_tex, clamp(uv + vec2(0.0,  u_inv_src_size.y), vec2(0.0), vec2(1.0))).rgb);
            float l1_u = luma(texture(u_tex, clamp(uv + vec2(0.0, -u_inv_src_size.y), vec2(0.0), vec2(1.0))).rgb);
            float ix = 0.5 * (l1_r - l1_l);
            float iy = 0.5 * (l1_d - l1_u);
            float it = l1 - l0;
            sum_ix2  += ix * ix;
            sum_iy2  += iy * iy;
            sum_ixiy += ix * iy;
            sum_ixit += ix * it;
            sum_iyit += iy * it;
            sum_it_abs += abs(it);
        }
    }

    vec2 mv = vec2(0.0);
    if (sum_it_abs > 0.02) {
        float det = sum_ix2 * sum_iy2 - sum_ixiy * sum_ixiy + 0.0025;
        mv.x = (-sum_iy2 * sum_ixit + sum_ixiy * sum_iyit) / det;
        mv.y = (-sum_ix2 * sum_iyit + sum_ixiy * sum_ixit) / det;
        mv = clamp(mv, vec2(-4.0), vec2(4.0));
    }

    vec2 hist_uv = v_uv - mv * u_inv_src_size;
    bool in_bounds = all(greaterThanEqual(hist_uv, vec2(0.0))) && all(lessThanEqual(hist_uv, vec2(1.0)));
    vec3 hist_rgb = texture(u_history_tex, clamp(hist_uv, vec2(0.0), vec2(1.0))).rgb;
    vec3 hist_clipped = ycocg_to_rgb(clip_aabb(aabb_min, aabb_max, rgb_to_ycocg(hist_rgb)));

    float disocc = luma(abs(current_rgb - hist_rgb));
    float motion_mag = length(mv);
    float hist_weight = in_bounds ? (0.72 * (1.0 - smoothstep(0.08, 0.35, disocc)) * (1.0 - 0.35 * clamp(motion_mag * 0.25, 0.0, 1.0))) : 0.0;

    vec3 out_rgb = mix(current_rgb, hist_clipped, hist_weight);
    frag_color = vec4(clamp(out_rgb, 0.0, 1.0), 1.0);
}
)GLSL";

static const char* kFsrRcasFragmentShader = R"GLSL(#version 300 es
precision highp float;
uniform sampler2D u_tex;
uniform vec2 u_inv_tex_size;
uniform float u_sharpness;
uniform float u_color_correction_strength;
uniform vec3 u_red_color;
uniform vec3 u_green_color;
uniform vec3 u_blue_color;
uniform float u_input_gamma;
in vec2 v_uv;
out vec4 frag_color;

float fsr_luma(vec3 c) {
    return 0.5 * c.g + 0.25 * (c.r + c.b);
}

void main() {
    vec3 b = texture(u_tex, clamp(v_uv + vec2( 0.0, -u_inv_tex_size.y), vec2(0.0), vec2(1.0))).rgb;
    vec3 d = texture(u_tex, clamp(v_uv + vec2(-u_inv_tex_size.x,  0.0), vec2(0.0), vec2(1.0))).rgb;
    vec3 e = texture(u_tex, clamp(v_uv, vec2(0.0), vec2(1.0))).rgb;
    vec3 f = texture(u_tex, clamp(v_uv + vec2( u_inv_tex_size.x,  0.0), vec2(0.0), vec2(1.0))).rgb;
    vec3 h = texture(u_tex, clamp(v_uv + vec2( 0.0,  u_inv_tex_size.y), vec2(0.0), vec2(1.0))).rgb;

    float bL = fsr_luma(b);
    float dL = fsr_luma(d);
    float eL = fsr_luma(e);
    float fL = fsr_luma(f);
    float hL = fsr_luma(h);

    float mnL = min(min(bL, dL), min(fL, hL));
    float mxL = max(max(bL, dL), max(fL, hL));
    float nz = 0.25 * (bL + dL + fL + hL) - eL;
    nz = clamp(abs(nz) / max(max(mxL, eL) - min(mnL, eL), 1e-5), 0.0, 1.0);
    nz = -0.5 * nz + 1.0;

    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));

    vec3 hitMin = min(mn4, e) / max(4.0 * mx4, vec3(1e-5));
    vec3 hitMax = (vec3(1.0) - max(mx4, e)) / max(4.0 * (vec3(1.0) - mn4), vec3(1e-5));
    vec3 lobeRGB = max(-vec3(0.1875), -min(hitMin, hitMax));
    float lobe = max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b));

    float sharp = clamp(u_sharpness, 0.0, 1.0);
    float gain = sharp * nz;
    lobe *= gain;

    float rcpL = 1.0 / (4.0 * lobe + 1.0);
    vec3 res = clamp((lobe * (b + d + f + h) + e) * rcpL, 0.0, 1.0);

    if (u_color_correction_strength > 0.001) {
        vec3 lin = pow(max(res, vec3(0.0)), vec3(u_input_gamma));
        vec3 corrected = lin.r * u_red_color + lin.g * u_green_color + lin.b * u_blue_color;
        corrected = pow(clamp(corrected, 0.0, 1.0), vec3(1.0 / 2.2));
        res = mix(res, corrected, u_color_correction_strength);
    }

    frag_color = vec4(res, 1.0);
}
)GLSL";

static float halton_seq(int index, int base) {
    float f = 1.0f;
    float r = 0.0f;
    int i = index;
    while (i > 0) {
        f /= static_cast<float>(base);
        r += f * static_cast<float>(i % base);
        i /= base;
    }
    return r;
}

class Gles3Renderer {
public:
    bool init(const std::string& shader_source) {
        destroy();

        const GLubyte* gl_ver = glGetString(GL_VERSION);
        if (gl_ver) {
            std::lock_guard<std::mutex> lock(g_gles_ver_mu);
            g_gles_version_str = reinterpret_cast<const char*>(gl_ver);
        }

        std::string body = shader_source.empty()
            ? std::string(kFallbackShader)
            : strip_version_directive(shader_source);

        std::string vs_src = "#version 300 es\n#define STORM_VERTEX_SHADER\n" + body;
        std::string fs_src = "#version 300 es\n#define STORM_FRAGMENT_SHADER\n" + body;

        prog_ = link_program(vs_src.c_str(), fs_src.c_str());
        if (!prog_ && !shader_source.empty()) {
            std::string fb(kFallbackShader);
            prog_ = link_program(
                ("#version 300 es\n#define STORM_VERTEX_SHADER\n" + fb).c_str(),
                ("#version 300 es\n#define STORM_FRAGMENT_SHADER\n" + fb).c_str());
        }
        if (!prog_) {
            return false;
        }

        // Compile optional Frame Gen & AMD FSR 1 / FSR 3 programs (fallback safely to prog_ if driver rejects)
        prog_fg_ = link_program(kPostVertexShader, kFrameGenFragmentShader);
        prog_easu_ = link_program(kPostVertexShader, kFsr1EasuFragmentShader);
        prog_temporal_ = link_program(kPostVertexShader, kFsr3TemporalFragmentShader);
        prog_rcas_ = link_program(kPostVertexShader, kFsrRcasFragmentShader);

        glGenTextures(2, tex_);
        for (int i = 0; i < 2; ++i) {
            glBindTexture(GL_TEXTURE_2D, tex_[i]);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        }

        static const float quad_flipped[] = {
            -1.0f, -1.0f, 0.0f, 1.0f,
             1.0f, -1.0f, 1.0f, 1.0f,
            -1.0f,  1.0f, 0.0f, 0.0f,
             1.0f,  1.0f, 1.0f, 0.0f,
        };
        glGenVertexArrays(1, &vao_);
        glGenBuffers(1, &vbo_);
        glBindVertexArray(vao_);
        glBindBuffer(GL_ARRAY_BUFFER, vbo_);
        glBufferData(GL_ARRAY_BUFFER, sizeof(quad_flipped), quad_flipped, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), nullptr);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), reinterpret_cast<void*>(2 * sizeof(float)));

        static const float quad_unflipped[] = {
            -1.0f, -1.0f, 0.0f, 0.0f,
             1.0f, -1.0f, 1.0f, 0.0f,
            -1.0f,  1.0f, 0.0f, 1.0f,
             1.0f,  1.0f, 1.0f, 1.0f,
        };
        glGenVertexArrays(1, &vao_fbo_);
        glGenBuffers(1, &vbo_fbo_);
        glBindVertexArray(vao_fbo_);
        glBindBuffer(GL_ARRAY_BUFFER, vbo_fbo_);
        glBufferData(GL_ARRAY_BUFFER, sizeof(quad_unflipped), quad_unflipped, GL_STATIC_DRAW);
        glEnableVertexAttribArray(0);
        glVertexAttribPointer(0, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), nullptr);
        glEnableVertexAttribArray(1);
        glVertexAttribPointer(1, 2, GL_FLOAT, GL_FALSE, 4 * sizeof(float), reinterpret_cast<void*>(2 * sizeof(float)));
        glBindVertexArray(0);

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

        cache_post_uniforms();
        has_prev_ = false;
        history_valid_ = false;
        fsr3_frame_idx_ = 0;
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

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_BLEND);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_CULL_FACE);
        glViewport(0, 0, surface_w_, surface_h_);
        glClearColor(0.0f, 0.0f, 0.0f, 1.0f);
        glClear(GL_COLOR_BUFFER_BIT);

        if (!rgba || w <= 0 || h <= 0 || vp_w <= 0 || vp_h <= 0) return;

        if (w != last_w_ || h != last_h_) {
            has_prev_ = false;
            history_valid_ = false;
            fsr3_frame_idx_ = 0;
            last_w_ = w;
            last_h_ = h;
        }

        const PostProcessParams post = get_post_process_params();
        const bool fg_midpoint = post.frame_gen_enabled &&
                                 post.frame_gen_alpha > 0.01f &&
                                 post.frame_gen_alpha < 0.99f;

        if (!fg_midpoint || !has_prev_ || tex_w_[cur_tex_] != w || tex_h_[cur_tex_] != h) {
            if (has_prev_) {
                cur_tex_ ^= 1;
            }
            int prev = cur_tex_ ^ 1;
            glBindTexture(GL_TEXTURE_2D, tex_[cur_tex_]);
            if (tex_w_[cur_tex_] != w || tex_h_[cur_tex_] != h) {
                glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
                tex_w_[cur_tex_] = w;
                tex_h_[cur_tex_] = h;
            } else {
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
            }
            if (!has_prev_ || tex_w_[prev] != w || tex_h_[prev] != h) {
                glBindTexture(GL_TEXTURE_2D, tex_[prev]);
                if (tex_w_[prev] != w || tex_h_[prev] != h) {
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
                    tex_w_[prev] = w;
                    tex_h_[prev] = h;
                } else {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, w, h, GL_RGBA, GL_UNSIGNED_BYTE, rgba);
                }
                has_prev_ = true;
            }
        }

        int prev = cur_tex_ ^ 1;
        GLuint input_tex = tex_[cur_tex_];

        if (fg_midpoint && prog_fg_ && ensure_fg_fbo(w, h)) {
            glBindFramebuffer(GL_FRAMEBUFFER, fbo_fg_);
            glViewport(0, 0, w, h);
            glUseProgram(prog_fg_);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, tex_[prev]);
            if (fg_loc_prev_ >= 0) glUniform1i(fg_loc_prev_, 0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, tex_[cur_tex_]);
            if (fg_loc_cur_ >= 0) glUniform1i(fg_loc_cur_, 1);
            if (fg_loc_texel_ >= 0) glUniform2f(fg_loc_texel_, 1.0f / static_cast<float>(w), 1.0f / static_cast<float>(h));
            if (fg_loc_alpha_ >= 0) glUniform1f(fg_loc_alpha_, post.frame_gen_alpha);
            glBindVertexArray(vao_fbo_);
            glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
            glBindFramebuffer(GL_FRAMEBUFFER, 0);
            input_tex = tex_fg_;
        }

        // First try full RetroArch .slangp multi-pass shader via librashader if preset is active!
        if (librashader_apply_gl(input_tex, static_cast<uint32_t>(w), static_cast<uint32_t>(h),
                                 vp_x, vp_y, static_cast<uint32_t>(vp_w), static_cast<uint32_t>(vp_h), rotation_deg_)) {
            return;
        }

        int eff_fsr = post.fsr_mode;
        if (eff_fsr == 0) {
            if (display_mode == STORM_VIDEO_FILTER_FSR1) eff_fsr = 1;
            else if (display_mode == STORM_VIDEO_FILTER_FSR3) eff_fsr = 2;
        }

        if ((eff_fsr == 1 || eff_fsr == 2) && prog_rcas_) {
            float q_scale = fsr_quality_scale(post.fsr_quality);
            int st_w = std::clamp(static_cast<int>(std::lround(static_cast<float>(vp_w) * q_scale)), w, vp_w);
            int st_h = std::clamp(static_cast<int>(std::lround(static_cast<float>(vp_h) * q_scale)), h, vp_h);
            float sharp = std::clamp(post.rcas_sharpness + fsr_quality_sharpness_bias(post.fsr_quality), 0.0f, 1.0f);

            if (eff_fsr == 1 && prog_easu_ && ensure_stage_fbos(st_w, st_h, false)) {
                glBindFramebuffer(GL_FRAMEBUFFER, fbo_stage1_);
                glViewport(0, 0, st_w, st_h);
                glUseProgram(prog_easu_);
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, input_tex);
                if (easu_loc_tex_ >= 0) glUniform1i(easu_loc_tex_, 0);
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, tex_[prev]);
                if (easu_loc_prev_ >= 0) glUniform1i(easu_loc_prev_, 1);
                if (easu_loc_src_size_ >= 0) glUniform2f(easu_loc_src_size_, static_cast<float>(w), static_cast<float>(h));
                if (easu_loc_inv_src_ >= 0) glUniform2f(easu_loc_inv_src_, 1.0f / static_cast<float>(w), 1.0f / static_cast<float>(h));
                if (easu_loc_ghosting_ >= 0) glUniform1f(easu_loc_ghosting_, std::clamp(ghosting, 0.0f, 1.0f));
                glBindVertexArray(vao_fbo_);
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

                draw_rcas_to_screen(vp_x, vp_y, vp_w, vp_h, st_w, st_h, sharp, cc_strength);
                return;
            }

            if (eff_fsr == 2 && prog_temporal_ && ensure_stage_fbos(st_w, st_h, true)) {
                float jx = (halton_seq(fsr3_frame_idx_ + 1, 2) - 0.5f) * 0.5f;
                float jy = (halton_seq(fsr3_frame_idx_ + 1, 3) - 0.5f) * 0.5f;

                glBindFramebuffer(GL_FRAMEBUFFER, fbo_stage1_);
                glViewport(0, 0, st_w, st_h);
                glUseProgram(prog_temporal_);
                glActiveTexture(GL_TEXTURE0);
                glBindTexture(GL_TEXTURE_2D, input_tex);
                if (temp_loc_tex_ >= 0) glUniform1i(temp_loc_tex_, 0);
                glActiveTexture(GL_TEXTURE1);
                glBindTexture(GL_TEXTURE_2D, tex_[prev]);
                if (temp_loc_prev_ >= 0) glUniform1i(temp_loc_prev_, 1);
                glActiveTexture(GL_TEXTURE2);
                glBindTexture(GL_TEXTURE_2D, tex_history_);
                if (temp_loc_history_ >= 0) glUniform1i(temp_loc_history_, 2);
                if (temp_loc_src_size_ >= 0) glUniform2f(temp_loc_src_size_, static_cast<float>(w), static_cast<float>(h));
                if (temp_loc_inv_src_ >= 0) glUniform2f(temp_loc_inv_src_, 1.0f / static_cast<float>(w), 1.0f / static_cast<float>(h));
                if (temp_loc_inv_stage_ >= 0) glUniform2f(temp_loc_inv_stage_, 1.0f / static_cast<float>(st_w), 1.0f / static_cast<float>(st_h));
                if (temp_loc_jitter_ >= 0) glUniform2f(temp_loc_jitter_, jx, jy);
                if (temp_loc_reset_ >= 0) glUniform1f(temp_loc_reset_, history_valid_ ? 0.0f : 1.0f);
                if (temp_loc_ghosting_ >= 0) glUniform1f(temp_loc_ghosting_, std::clamp(ghosting, 0.0f, 1.0f));
                glBindVertexArray(vao_fbo_);
                glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);

                draw_rcas_to_screen(vp_x, vp_y, vp_w, vp_h, st_w, st_h, sharp, cc_strength);

                std::swap(fbo_stage1_, fbo_history_);
                std::swap(tex_stage1_, tex_history_);
                history_valid_ = true;
                fsr3_frame_idx_ = (fsr3_frame_idx_ + 1) & 31;
                return;
            }
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(vp_x, vp_y, vp_w, vp_h);
        glUseProgram(prog_);

        if (loc_tex_ >= 0) {
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, input_tex);
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
    }

    void set_rotation(int deg) {
        rotation_deg_ = ((deg % 360) + 360) % 360;
    }

    void destroy() {
        librashader_reset_gl();
        destroy_fbos();
        if (vbo_fbo_) { glDeleteBuffers(1, &vbo_fbo_); vbo_fbo_ = 0; }
        if (vao_fbo_) { glDeleteVertexArrays(1, &vao_fbo_); vao_fbo_ = 0; }
        if (vbo_) { glDeleteBuffers(1, &vbo_); vbo_ = 0; }
        if (vao_) { glDeleteVertexArrays(1, &vao_); vao_ = 0; }
        if (tex_[0] || tex_[1]) { glDeleteTextures(2, tex_); tex_[0] = tex_[1] = 0; }
        if (prog_fg_) { glDeleteProgram(prog_fg_); prog_fg_ = 0; }
        if (prog_easu_) { glDeleteProgram(prog_easu_); prog_easu_ = 0; }
        if (prog_temporal_) { glDeleteProgram(prog_temporal_); prog_temporal_ = 0; }
        if (prog_rcas_) { glDeleteProgram(prog_rcas_); prog_rcas_ = 0; }
        if (prog_) { glDeleteProgram(prog_); prog_ = 0; }
        has_prev_ = false;
        history_valid_ = false;
        fsr3_frame_idx_ = 0;
        last_w_ = 0;
        last_h_ = 0;
        tex_w_[0] = tex_w_[1] = 0;
        tex_h_[0] = tex_h_[1] = 0;
    }

private:
    void cache_post_uniforms() {
        if (prog_fg_) {
            fg_loc_prev_ = glGetUniformLocation(prog_fg_, "u_prev_tex");
            fg_loc_cur_ = glGetUniformLocation(prog_fg_, "u_cur_tex");
            fg_loc_texel_ = glGetUniformLocation(prog_fg_, "u_texel_size");
            fg_loc_alpha_ = glGetUniformLocation(prog_fg_, "u_alpha");
        }
        if (prog_easu_) {
            easu_loc_tex_ = glGetUniformLocation(prog_easu_, "u_tex");
            easu_loc_prev_ = glGetUniformLocation(prog_easu_, "u_prev_tex");
            easu_loc_src_size_ = glGetUniformLocation(prog_easu_, "u_src_size");
            easu_loc_inv_src_ = glGetUniformLocation(prog_easu_, "u_inv_src_size");
            easu_loc_ghosting_ = glGetUniformLocation(prog_easu_, "u_ghosting");
        }
        if (prog_temporal_) {
            temp_loc_tex_ = glGetUniformLocation(prog_temporal_, "u_tex");
            temp_loc_prev_ = glGetUniformLocation(prog_temporal_, "u_prev_tex");
            temp_loc_history_ = glGetUniformLocation(prog_temporal_, "u_history_tex");
            temp_loc_src_size_ = glGetUniformLocation(prog_temporal_, "u_src_size");
            temp_loc_inv_src_ = glGetUniformLocation(prog_temporal_, "u_inv_src_size");
            temp_loc_inv_stage_ = glGetUniformLocation(prog_temporal_, "u_inv_stage_size");
            temp_loc_jitter_ = glGetUniformLocation(prog_temporal_, "u_jitter");
            temp_loc_reset_ = glGetUniformLocation(prog_temporal_, "u_reset_history");
            temp_loc_ghosting_ = glGetUniformLocation(prog_temporal_, "u_ghosting");
        }
        if (prog_rcas_) {
            rcas_loc_tex_ = glGetUniformLocation(prog_rcas_, "u_tex");
            rcas_loc_inv_tex_ = glGetUniformLocation(prog_rcas_, "u_inv_tex_size");
            rcas_loc_sharp_ = glGetUniformLocation(prog_rcas_, "u_sharpness");
            rcas_loc_cc_ = glGetUniformLocation(prog_rcas_, "u_color_correction_strength");
            rcas_loc_red_ = glGetUniformLocation(prog_rcas_, "u_red_color");
            rcas_loc_green_ = glGetUniformLocation(prog_rcas_, "u_green_color");
            rcas_loc_blue_ = glGetUniformLocation(prog_rcas_, "u_blue_color");
            rcas_loc_gamma_ = glGetUniformLocation(prog_rcas_, "u_input_gamma");
        }
    }

    static bool create_fbo_tex(GLuint& fbo, GLuint& tex, int w, int h, GLenum filter) {
        if (!fbo) glGenFramebuffers(1, &fbo);
        if (!tex) glGenTextures(1, &tex);
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, w, h, 0, GL_RGBA, GL_UNSIGNED_BYTE, nullptr);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        GLenum st = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        return st == GL_FRAMEBUFFER_COMPLETE;
    }

    bool ensure_fg_fbo(int w, int h) {
        if (fbo_fg_ && tex_fg_ && fg_w_ == w && fg_h_ == h) return true;
        if (!create_fbo_tex(fbo_fg_, tex_fg_, w, h, GL_NEAREST)) {
            return false;
        }
        fg_w_ = w;
        fg_h_ = h;
        return true;
    }

    bool ensure_stage_fbos(int w, int h, bool need_history) {
        if (stage_w_ != w || stage_h_ != h || !fbo_stage1_ || !tex_stage1_) {
            if (!create_fbo_tex(fbo_stage1_, tex_stage1_, w, h, GL_LINEAR)) {
                return false;
            }
            if (need_history) {
                if (!create_fbo_tex(fbo_history_, tex_history_, w, h, GL_LINEAR)) {
                    return false;
                }
            }
            stage_w_ = w;
            stage_h_ = h;
            history_valid_ = false;
            return true;
        }
        if (need_history && (!fbo_history_ || !tex_history_)) {
            if (!create_fbo_tex(fbo_history_, tex_history_, w, h, GL_LINEAR)) {
                return false;
            }
            history_valid_ = false;
        }
        return true;
    }

    void draw_rcas_to_screen(int vp_x, int vp_y, int vp_w, int vp_h,
                             int st_w, int st_h, float sharp, float cc_strength) {
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(vp_x, vp_y, vp_w, vp_h);
        glUseProgram(prog_rcas_);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, tex_stage1_);
        if (rcas_loc_tex_ >= 0) glUniform1i(rcas_loc_tex_, 0);
        if (rcas_loc_inv_tex_ >= 0) glUniform2f(rcas_loc_inv_tex_, 1.0f / static_cast<float>(st_w), 1.0f / static_cast<float>(st_h));
        if (rcas_loc_sharp_ >= 0) glUniform1f(rcas_loc_sharp_, sharp);
        if (rcas_loc_cc_ >= 0) glUniform1f(rcas_loc_cc_, std::clamp(cc_strength, 0.0f, 1.0f));
        if (rcas_loc_red_ >= 0) glUniform3f(rcas_loc_red_, 0.82f, 0.24f, -0.06f);
        if (rcas_loc_green_ >= 0) glUniform3f(rcas_loc_green_, 0.125f, 0.665f, 0.21f);
        if (rcas_loc_blue_ >= 0) glUniform3f(rcas_loc_blue_, 0.195f, 0.075f, 0.73f);
        if (rcas_loc_gamma_ >= 0) glUniform1f(rcas_loc_gamma_, 2.2f);
        glBindVertexArray(vao_);
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4);
    }

    void destroy_fbos() {
        if (fbo_fg_) { glDeleteFramebuffers(1, &fbo_fg_); fbo_fg_ = 0; }
        if (tex_fg_) { glDeleteTextures(1, &tex_fg_); tex_fg_ = 0; }
        if (fbo_stage1_) { glDeleteFramebuffers(1, &fbo_stage1_); fbo_stage1_ = 0; }
        if (tex_stage1_) { glDeleteTextures(1, &tex_stage1_); tex_stage1_ = 0; }
        if (fbo_history_) { glDeleteFramebuffers(1, &fbo_history_); fbo_history_ = 0; }
        if (tex_history_) { glDeleteTextures(1, &tex_history_); tex_history_ = 0; }
        fg_w_ = fg_h_ = 0;
        stage_w_ = stage_h_ = 0;
    }

    GLuint prog_ = 0;
    GLuint prog_fg_ = 0;
    GLuint prog_easu_ = 0;
    GLuint prog_temporal_ = 0;
    GLuint prog_rcas_ = 0;

    GLuint tex_[2] = {0, 0};
    GLuint vao_ = 0;
    GLuint vbo_ = 0;
    GLuint vao_fbo_ = 0;
    GLuint vbo_fbo_ = 0;

    GLuint fbo_fg_ = 0;
    GLuint tex_fg_ = 0;
    int fg_w_ = 0, fg_h_ = 0;

    GLuint fbo_stage1_ = 0;
    GLuint tex_stage1_ = 0;
    GLuint fbo_history_ = 0;
    GLuint tex_history_ = 0;
    int stage_w_ = 0, stage_h_ = 0;

    int cur_tex_ = 0;
    bool has_prev_ = false;
    bool history_valid_ = false;
    int fsr3_frame_idx_ = 0;
    int last_w_ = 0;
    int last_h_ = 0;
    int tex_w_[2] = {0, 0};
    int tex_h_[2] = {0, 0};
    int surface_w_ = 1;
    int surface_h_ = 1;
    int rotation_deg_ = 0;

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

    GLint fg_loc_prev_ = -1, fg_loc_cur_ = -1, fg_loc_texel_ = -1, fg_loc_alpha_ = -1;
    GLint easu_loc_tex_ = -1, easu_loc_prev_ = -1, easu_loc_src_size_ = -1, easu_loc_inv_src_ = -1, easu_loc_ghosting_ = -1;
    GLint temp_loc_tex_ = -1, temp_loc_prev_ = -1, temp_loc_history_ = -1;
    GLint temp_loc_src_size_ = -1, temp_loc_inv_src_ = -1, temp_loc_inv_stage_ = -1;
    GLint temp_loc_jitter_ = -1, temp_loc_reset_ = -1, temp_loc_ghosting_ = -1;
    GLint rcas_loc_tex_ = -1, rcas_loc_inv_tex_ = -1, rcas_loc_sharp_ = -1;
    GLint rcas_loc_cc_ = -1, rcas_loc_red_ = -1, rcas_loc_green_ = -1, rcas_loc_blue_ = -1, rcas_loc_gamma_ = -1;
};

static Gles3Renderer g_gles3;

bool gles_init(const std::string& shader_source) { return g_gles3.init(shader_source); }
void gles_resize(int w, int h) { g_gles3.resize(w, h); }
void gles_set_rotation(int deg) { g_gles3.set_rotation(deg); }
void gles_render(const uint32_t* rgba, int w, int h, int vp_x, int vp_y, int vp_w, int vp_h,
                 int display_mode, float ghosting, float cc_strength, bool is_grayscale, bool integer_scaling) {
    g_gles3.render(rgba, w, h, vp_x, vp_y, vp_w, vp_h, display_mode, ghosting, cc_strength, is_grayscale, integer_scaling);
}
void gles_destroy() { g_gles3.destroy(); }

} // namespace storm

#endif // ANDROID
