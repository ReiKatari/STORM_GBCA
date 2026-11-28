// STORM GBCA Unified GLES 3.00 / Desktop GLSL Fragment & Vertex Shaders
// Based on SkyEmu lcd_shaders.shd + NanoBoyAdvance Filters.hlsl
// Zero em-dashes.

#ifdef STORM_VERTEX_SHADER
#version 300 es
precision highp float;

layout(location = 0) in vec2 a_pos;
layout(location = 1) in vec2 a_uv;

out vec2 v_uv;

void main() {
    v_uv = a_uv;
    gl_Position = vec4(a_pos, 0.0, 1.0);
}
#endif

#ifdef STORM_FRAGMENT_SHADER
#version 300 es
precision mediump float;
precision highp int;

uniform sampler2D u_tex;
uniform sampler2D u_prev_tex;

uniform highp vec2 u_emu_lcd_size;
uniform highp vec2 u_render_size;
uniform int        u_display_mode;        // 0=Nearest, 1=Bilinear, 2=LCD Grid, 3=RGB Subpixel, 4=xBRZ-lite, 5=CRT Scanlines, 6=Sharp Bilinear, 7=FSR1, 8=FSR3
uniform float      u_lcd_is_grayscale;
uniform float      u_integer_scaling;
uniform float      u_color_correction_strength;
uniform vec3       u_red_color;
uniform vec3       u_green_color;
uniform vec3       u_blue_color;
uniform float      u_input_gamma;
uniform float      u_ghosting;            // 0.0 .. 1.0

in highp vec2 v_uv;
out vec4 frag_color;

vec4 sample_raw(highp vec2 uv) {
    vec4 curr = texture(u_tex, uv);
    if (u_ghosting > 0.005) {
        vec4 prev = texture(u_prev_tex, uv);
        curr = mix(curr, prev, clamp(u_ghosting * 0.55, 0.0, 0.65));
    }
    return curr;
}

vec3 apply_color_correct(vec3 c) {
    if (u_color_correction_strength > 0.005) {
        vec3 lin = pow(max(c, vec3(1e-5)), vec3(u_input_gamma));
        vec3 corrected = lin.r * u_red_color + lin.g * u_green_color + lin.b * u_blue_color;
        corrected = pow(clamp(corrected, 1e-5, 1.0), vec3(1.0 / 2.2));
        c = mix(c, corrected, u_color_correction_strength);
    }
    return c;
}

#define BLEND_NONE 0
#define BLEND_NORMAL 1
#define BLEND_DOMINANT 2
#define LUMINANCE_WEIGHT 1.0
#define EQUAL_COLOR_TOLERANCE (30.0 / 255.0)
#define STEEP_DIRECTION_THRESHOLD 2.2
#define DOMINANT_DIRECTION_THRESHOLD 3.6

float DistYCbCr(vec3 pixA, vec3 pixB) {
    const vec3 w = vec3(0.2627, 0.6780, 0.0593);
    const float scaleB = 0.5 / (1.0 - w.b);
    const float scaleR = 0.5 / (1.0 - w.r);
    vec3 diff = pixA - pixB;
    float Y = dot(diff.rgb, w);
    float Cb = scaleB * (diff.b - Y);
    float Cr = scaleR * (diff.r - Y);
    return sqrt(((LUMINANCE_WEIGHT * Y) * (LUMINANCE_WEIGHT * Y)) + (Cb * Cb) + (Cr * Cr));
}

bool IsPixEqual(vec3 pixA, vec3 pixB) {
    return DistYCbCr(pixA, pixB) < EQUAL_COLOR_TOLERANCE;
}

float get_left_ratio(highp vec2 center, highp vec2 origin, highp vec2 direction, highp vec2 scale) {
    highp vec2 P0 = center - origin;
    highp vec2 proj = direction * (dot(P0, direction) / dot(direction, direction));
    highp vec2 distv = P0 - proj;
    highp vec2 orth = vec2(-direction.y, direction.x);
    highp float side = sign(dot(P0, orth));
    highp float v = side * length(distv * scale);
    return smoothstep(-sqrt(2.0) / 2.0, sqrt(2.0) / 2.0, v);
}

#define eq(a, b)  (all(lessThan(abs((a) - (b)), vec3(0.004))))
#define neq(a, b) (!eq(a, b))
#define P(x, y)   sample_raw(coord + invSize * vec2(float(x), float(y))).rgb

vec4 xbrz_lite() {
    highp vec2 invSize = 1.0 / u_emu_lcd_size;
    highp vec2 scale = u_render_size * invSize;
    highp vec2 pos = fract(v_uv * u_emu_lcd_size) - vec2(0.5, 0.5);
    highp vec2 coord = v_uv - pos * invSize;

    vec3 A = P(-1, -1); vec3 B = P(0, -1); vec3 C = P(1, -1);
    vec3 D = P(-1,  0); vec3 E = P(0,  0); vec3 F = P(1,  0);
    vec3 G = P(-1,  1); vec3 H = P(0,  1); vec3 I = P(1,  1);

    ivec4 blendResult = ivec4(BLEND_NONE);

    if (!((eq(E, F) && eq(H, I)) || (eq(E, H) && eq(F, I)))) {
        float dist_H_F = DistYCbCr(G, E) + DistYCbCr(E, C) + DistYCbCr(P(0, 2), I) + DistYCbCr(I, P(2, 0)) + (4.0 * DistYCbCr(H, F));
        float dist_E_I = DistYCbCr(D, H) + DistYCbCr(H, P(1, 2)) + DistYCbCr(B, F) + DistYCbCr(F, P(2, 1)) + (4.0 * DistYCbCr(E, I));
        bool dom = (DOMINANT_DIRECTION_THRESHOLD * dist_H_F) < dist_E_I;
        blendResult.z = ((dist_H_F < dist_E_I) && neq(E, F) && neq(E, H)) ? (dom ? BLEND_DOMINANT : BLEND_NORMAL) : BLEND_NONE;
    }
    if (!((eq(D, E) && eq(G, H)) || (eq(D, G) && eq(E, H)))) {
        float dist_G_E = DistYCbCr(P(-2, 1), D) + DistYCbCr(D, B) + DistYCbCr(P(-1, 2), H) + DistYCbCr(H, F) + (4.0 * DistYCbCr(G, E));
        float dist_D_H = DistYCbCr(P(-2, 0), G) + DistYCbCr(G, P(0, 2)) + DistYCbCr(A, E) + DistYCbCr(E, I) + (4.0 * DistYCbCr(D, H));
        bool dom = (DOMINANT_DIRECTION_THRESHOLD * dist_D_H) < dist_G_E;
        blendResult.w = ((dist_G_E > dist_D_H) && neq(E, D) && neq(E, H)) ? (dom ? BLEND_DOMINANT : BLEND_NORMAL) : BLEND_NONE;
    }
    if (!((eq(B, C) && eq(E, F)) || (eq(B, E) && eq(C, F)))) {
        float dist_E_C = DistYCbCr(D, B) + DistYCbCr(B, P(1, -2)) + DistYCbCr(H, F) + DistYCbCr(F, P(2, -1)) + (4.0 * DistYCbCr(E, C));
        float dist_B_F = DistYCbCr(A, E) + DistYCbCr(E, I) + DistYCbCr(P(0, -2), C) + DistYCbCr(C, P(2, 0)) + (4.0 * DistYCbCr(B, F));
        bool dom = (DOMINANT_DIRECTION_THRESHOLD * dist_B_F) < dist_E_C;
        blendResult.y = ((dist_E_C > dist_B_F) && neq(E, B) && neq(E, F)) ? (dom ? BLEND_DOMINANT : BLEND_NORMAL) : BLEND_NONE;
    }
    if (!((eq(A, B) && eq(D, E)) || (eq(A, D) && eq(B, E)))) {
        float dist_D_B = DistYCbCr(P(-2, 0), A) + DistYCbCr(A, P(0, -2)) + DistYCbCr(G, E) + DistYCbCr(E, C) + (4.0 * DistYCbCr(D, B));
        float dist_A_E = DistYCbCr(P(-2, -1), D) + DistYCbCr(D, H) + DistYCbCr(P(-1, -2), B) + DistYCbCr(B, F) + (4.0 * DistYCbCr(A, E));
        bool dom = (DOMINANT_DIRECTION_THRESHOLD * dist_D_B) < dist_A_E;
        blendResult.x = ((dist_D_B < dist_A_E) && neq(E, D) && neq(E, B)) ? (dom ? BLEND_DOMINANT : BLEND_NORMAL) : BLEND_NONE;
    }

    vec3 res = E;

    if (blendResult.z != BLEND_NONE) {
        float dist_F_G = DistYCbCr(F, G);
        float dist_H_C = DistYCbCr(H, C);
        bool doLineBlend = (blendResult.z == BLEND_DOMINANT ||
            !((blendResult.y != BLEND_NONE && !IsPixEqual(E, G)) || (blendResult.w != BLEND_NONE && !IsPixEqual(E, C)) ||
              (IsPixEqual(G, H) && IsPixEqual(H, I) && IsPixEqual(I, F) && IsPixEqual(F, C) && !IsPixEqual(E, I))));
        highp vec2 origin = vec2(0.0, 1.0 / sqrt(2.0));
        highp vec2 direction = vec2(1.0, -1.0);
        if (doLineBlend) {
            bool haveShallow = (STEEP_DIRECTION_THRESHOLD * dist_F_G <= dist_H_C) && neq(E, G) && neq(D, G);
            bool haveSteep   = (STEEP_DIRECTION_THRESHOLD * dist_H_C <= dist_F_G) && neq(E, C) && neq(B, C);
            origin = haveShallow ? vec2(0.0, 0.25) : vec2(0.0, 0.5);
            direction.x += haveShallow ? 1.0 : 0.0;
            direction.y -= haveSteep ? 1.0 : 0.0;
        }
        vec3 blendPix = mix(H, F, step(DistYCbCr(E, F), DistYCbCr(E, H)));
        res = mix(res, blendPix, get_left_ratio(pos, origin, direction, scale));
    }
    if (blendResult.w != BLEND_NONE) {
        float dist_H_A = DistYCbCr(H, A);
        float dist_D_I = DistYCbCr(D, I);
        bool doLineBlend = (blendResult.w == BLEND_DOMINANT ||
            !((blendResult.z != BLEND_NONE && !IsPixEqual(E, A)) || (blendResult.x != BLEND_NONE && !IsPixEqual(E, I)) ||
              (IsPixEqual(A, D) && IsPixEqual(D, G) && IsPixEqual(G, H) && IsPixEqual(H, I) && !IsPixEqual(E, G))));
        highp vec2 origin = vec2(-1.0 / sqrt(2.0), 0.0);
        highp vec2 direction = vec2(1.0, 1.0);
        if (doLineBlend) {
            bool haveShallow = (STEEP_DIRECTION_THRESHOLD * dist_H_A <= dist_D_I) && neq(E, A) && neq(B, A);
            bool haveSteep   = (STEEP_DIRECTION_THRESHOLD * dist_D_I <= dist_H_A) && neq(E, I) && neq(F, I);
            origin = haveShallow ? vec2(-0.25, 0.0) : vec2(-0.5, 0.0);
            direction.y += haveShallow ? 1.0 : 0.0;
            direction.x += haveSteep ? 1.0 : 0.0;
        }
        vec3 blendPix = mix(H, D, step(DistYCbCr(E, D), DistYCbCr(E, H)));
        res = mix(res, blendPix, get_left_ratio(pos, origin, direction, scale));
    }
    if (blendResult.y != BLEND_NONE) {
        float dist_B_I = DistYCbCr(B, I);
        float dist_F_A = DistYCbCr(F, A);
        bool doLineBlend = (blendResult.y == BLEND_DOMINANT ||
            !((blendResult.x != BLEND_NONE && !IsPixEqual(E, I)) || (blendResult.z != BLEND_NONE && !IsPixEqual(E, A)) ||
              (IsPixEqual(I, F) && IsPixEqual(F, C) && IsPixEqual(C, B) && IsPixEqual(B, A) && !IsPixEqual(E, C))));
        highp vec2 origin = vec2(1.0 / sqrt(2.0), 0.0);
        highp vec2 direction = vec2(-1.0, -1.0);
        if (doLineBlend) {
            bool haveShallow = (STEEP_DIRECTION_THRESHOLD * dist_B_I <= dist_F_A) && neq(E, I) && neq(H, I);
            bool haveSteep   = (STEEP_DIRECTION_THRESHOLD * dist_F_A <= dist_B_I) && neq(E, A) && neq(D, A);
            origin = haveShallow ? vec2(0.25, 0.0) : vec2(0.5, 0.0);
            direction.y -= haveShallow ? 1.0 : 0.0;
            direction.x -= haveSteep ? 1.0 : 0.0;
        }
        vec3 blendPix = mix(F, B, step(DistYCbCr(E, B), DistYCbCr(E, F)));
        res = mix(res, blendPix, get_left_ratio(pos, origin, direction, scale));
    }
    if (blendResult.x != BLEND_NONE) {
        float dist_D_C = DistYCbCr(D, C);
        float dist_B_G = DistYCbCr(B, G);
        bool doLineBlend = (blendResult.x == BLEND_DOMINANT ||
            !((blendResult.w != BLEND_NONE && !IsPixEqual(E, C)) || (blendResult.y != BLEND_NONE && !IsPixEqual(E, G)) ||
              (IsPixEqual(C, B) && IsPixEqual(B, A) && IsPixEqual(A, D) && IsPixEqual(D, G) && !IsPixEqual(E, A))));
        highp vec2 origin = vec2(0.0, -1.0 / sqrt(2.0));
        highp vec2 direction = vec2(-1.0, 1.0);
        if (doLineBlend) {
            bool haveShallow = (STEEP_DIRECTION_THRESHOLD * dist_D_C <= dist_B_G) && neq(E, C) && neq(F, C);
            bool haveSteep   = (STEEP_DIRECTION_THRESHOLD * dist_B_G <= dist_D_C) && neq(E, G) && neq(H, G);
            origin = haveShallow ? vec2(0.0, -0.25) : vec2(0.0, -0.5);
            direction.x -= haveShallow ? 1.0 : 0.0;
            direction.y += haveSteep ? 1.0 : 0.0;
        }
        vec3 blendPix = mix(D, B, step(DistYCbCr(E, B), DistYCbCr(E, D)));
        res = mix(res, blendPix, get_left_ratio(pos, origin, direction, scale));
    }
    return vec4(res, 1.0);
}

void main() {
    highp vec2 invSize = 1.0 / max(u_emu_lcd_size, vec2(1.0));
    highp float smooth_width  = u_emu_lcd_size.x / max(u_render_size.x, 1.0);
    highp float smooth_height = u_emu_lcd_size.y / max(u_render_size.y, 1.0);
    highp vec2 pix_sub = fract(v_uv * u_emu_lcd_size);

    int mode = u_display_mode;
    if (u_lcd_is_grayscale > 0.5 && mode == 3) {
        mode = 2;
    }

    if (mode == 1) {
        highp vec2 sub = fract(v_uv * u_emu_lcd_size - 0.5);
        highp vec2 ip = (floor(v_uv * u_emu_lcd_size - 0.5) + 0.5) * invSize;
        vec4 v0 = sample_raw(ip);
        vec4 v1 = sample_raw(ip + vec2(invSize.x, 0.0));
        vec4 v2 = sample_raw(ip + vec2(0.0, invSize.y));
        vec4 v3 = sample_raw(ip + invSize);
        frag_color = mix(mix(v0, v1, sub.x), mix(v2, v3, sub.x), sub.y);
    } else if (mode == 2) {
        vec4 val = sample_raw(v_uv);
        float rgb_factor = smoothstep(0.0, smooth_height, pix_sub.y) - smoothstep(1.0 - smooth_height, 1.0, pix_sub.y);
        rgb_factor *= (smoothstep(0.0, smooth_width, pix_sub.x) - smoothstep(1.0 - smooth_width, 1.0, pix_sub.x));
        rgb_factor = mix(0.66, 1.0, rgb_factor);
        frag_color = val;
        frag_color.rgb *= rgb_factor;
        frag_color = mix(frag_color, val, clamp(smooth_width, 0.0, 1.0));
    } else if (mode == 3) {
        vec4 val = sample_raw(v_uv);
        float expansion = 0.15;
        float sw_sub = smooth_width * 1.5;
        float r_factor = smoothstep(0.0, sw_sub, pix_sub.x) - smoothstep(0.33 - sw_sub, 0.33 + expansion, pix_sub.x);
        float g_factor = smoothstep(0.33 - expansion * 0.5, 0.33 + sw_sub, pix_sub.x) - smoothstep(0.66 - sw_sub, 0.66 + expansion * 0.5, pix_sub.x);
        float b_factor = smoothstep(0.66 - expansion, 0.66 + sw_sub, pix_sub.x) - smoothstep(1.0 - sw_sub, 1.0, pix_sub.x);
        float rgb_factor = smoothstep(0.0, smooth_height, pix_sub.y) - smoothstep(1.0 - smooth_height, 1.0, pix_sub.y);
        rgb_factor = rgb_factor * 0.2 + 0.8;
        frag_color = val;
        frag_color.r *= clamp(mix(0.6, 1.0, r_factor) * 1.33, 0.0, 1.0);
        frag_color.g *= clamp(mix(0.6, 1.0, g_factor) * 1.33, 0.0, 1.0);
        frag_color.b *= clamp(mix(0.6, 1.0, b_factor) * 1.33, 0.0, 1.0);
        frag_color = mix(frag_color, val, clamp(smooth_width * 3.0, 0.0, 1.0));
        frag_color *= rgb_factor;
        frag_color = mix(frag_color, val, clamp(smooth_width, 0.0, 1.0));
    } else if (mode == 4) {
        frag_color = xbrz_lite();
    } else if (mode == 5) {
        frag_color = sample_raw(v_uv);
        float scan = 0.82 + 0.18 * sin(pix_sub.y * 6.2831853);
        frag_color.rgb *= scan;
    } else if (mode == 6) {
        if (u_integer_scaling <= 0.5) {
            highp vec2 ip = (floor(v_uv * u_emu_lcd_size - 0.5) + 0.5) * invSize;
            highp vec2 residual = fract(v_uv * u_emu_lcd_size + 0.5);
            vec4 v0 = sample_raw(ip);
            vec4 v1 = sample_raw(ip + vec2(invSize.x, 0.0));
            vec4 v2 = sample_raw(ip + vec2(0.0, invSize.y));
            vec4 v3 = sample_raw(ip + invSize);
            highp vec2 smooth_dim = vec2(smooth_width, smooth_height);
            vec2 alpha = vec2(
                smoothstep(0.5 - smooth_dim.x * 0.5, 0.5 + smooth_dim.x * 0.5, residual.x),
                smoothstep(0.5 - smooth_dim.y * 0.5, 0.5 + smooth_dim.y * 0.5, residual.y)
            );
            frag_color = mix(mix(v0, v1, alpha.x), mix(v2, v3, alpha.x), alpha.y);
        } else {
            highp vec2 scale = max(floor(u_render_size / max(u_emu_lcd_size, vec2(1.0))), vec2(1.0));
            highp vec2 regionRange = 0.5 - (0.5 / scale);
            highp vec2 texel = v_uv * u_emu_lcd_size;
            highp vec2 centerDist = fract(texel) - 0.5;
            highp vec2 modTexel = floor(texel) + (centerDist - clamp(centerDist, -regionRange, regionRange)) * scale + 0.5;
            frag_color = sample_raw(modTexel * invSize);
        }
    } else if (mode == 7 || mode == 8) {
        highp vec2 ip = (floor(v_uv * u_emu_lcd_size - 0.5) + 0.5) * invSize;
        highp vec2 residual = fract(v_uv * u_emu_lcd_size + 0.5);
        vec4 v0 = sample_raw(ip);
        vec4 v1 = sample_raw(ip + vec2(invSize.x, 0.0));
        vec4 v2 = sample_raw(ip + vec2(0.0, invSize.y));
        vec4 v3 = sample_raw(ip + invSize);
        highp vec2 smooth_dim = vec2(smooth_width, smooth_height);
        vec2 alpha = vec2(
            smoothstep(0.5 - smooth_dim.x * 0.5, 0.5 + smooth_dim.x * 0.5, residual.x),
            smoothstep(0.5 - smooth_dim.y * 0.5, 0.5 + smooth_dim.y * 0.5, residual.y)
        );
        vec4 base = mix(mix(v0, v1, alpha.x), mix(v2, v3, alpha.x), alpha.y);
        vec3 avg4 = 0.25 * (v0.rgb + v1.rgb + v2.rgb + v3.rgb);
        vec3 mn4 = min(min(v0.rgb, v1.rgb), min(v2.rgb, v3.rgb));
        vec3 mx4 = max(max(v0.rgb, v1.rgb), max(v2.rgb, v3.rgb));
        vec3 sharp_rgb = clamp(base.rgb + (base.rgb - avg4) * 0.35, mn4, mx4);
        frag_color = vec4(sharp_rgb, base.a);
    } else {
        frag_color = sample_raw(v_uv);
    }

    frag_color.rgb = apply_color_correct(frag_color.rgb);
}
#endif
