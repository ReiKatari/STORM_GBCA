#version 450

layout(std140, set = 0, binding = 0) uniform UBO {
    mat4 MVP;
} global;

layout(push_constant) uniform Push {
    vec4 SourceSize;
    vec4 OriginalSize;
    vec4 OutputSize;
    uint FrameCount;
} params;

#pragma stage vertex
layout(location = 0) in vec4 Position;
layout(location = 1) in vec2 TexCoord;
layout(location = 0) out vec2 vTexCoord;

void main() {
    gl_Position = global.MVP * Position;
    vTexCoord = TexCoord;
}

#pragma stage fragment
layout(location = 0) in vec2 vTexCoord;
layout(location = 0) out vec4 FragColor;
layout(set = 0, binding = 2) uniform sampler2D Source;
layout(set = 0, binding = 3) uniform sampler2D OriginalHistory1;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float halton(uint index, uint base) {
    float f = 1.0;
    float r = 0.0;
    uint i = index;
    for (int step = 0; step < 6; ++step) {
        if (i == 0u) break;
        f /= float(base);
        r += f * float(i % base);
        i /= base;
    }
    return r;
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
    vec2 invSize = params.SourceSize.zw;
    vec2 pos = uv * params.SourceSize.xy - 0.5;
    vec2 f = fract(pos);
    vec2 base = (floor(pos) + 0.5) * invSize;
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
            vec3 s = texture(Source, clamp(base + vec2(float(i), float(j)) * invSize, 0.0, 1.0)).rgb;
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
    vec2 invSize = params.SourceSize.zw;
    uint phase = (params.FrameCount % 32u) + 1u;
    vec2 jitter = vec2(halton(phase, 2u) - 0.5, halton(phase, 3u) - 0.5) * 0.5;
    vec3 current_rgb = sample_catmull_rom_16(vTexCoord + jitter * invSize);

    if (params.FrameCount <= 1u) {
        FragColor = vec4(current_rgb, 1.0);
        return;
    }

    vec3 m1 = vec3(0.0);
    vec3 m2 = vec3(0.0);
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec3 s = texture(Source, clamp(vTexCoord + vec2(float(x), float(y)) * invSize, 0.0, 1.0)).rgb;
            vec3 ycc = rgb_to_ycocg(s);
            m1 += ycc;
            m2 += ycc * ycc;
        }
    }
    vec3 mu = m1 / 9.0;
    vec3 sigma = sqrt(max(m2 / 9.0 - mu * mu, vec3(0.0)));
    vec3 aabb_min = mu - 1.25 * sigma;
    vec3 aabb_max = mu + 1.25 * sigma;

    float sum_ix2 = 0.0, sum_iy2 = 0.0, sum_ixiy = 0.0, sum_ixit = 0.0, sum_iyit = 0.0;
    float sum_it_abs = 0.0;
    for (int y = -1; y <= 1; ++y) {
        for (int x = -1; x <= 1; ++x) {
            vec2 uv = vTexCoord + vec2(float(x), float(y)) * invSize;
            float l0 = luma(texture(OriginalHistory1, clamp(uv, 0.0, 1.0)).rgb);
            float l1 = luma(texture(Source, clamp(uv, 0.0, 1.0)).rgb);
            float l1_r = luma(texture(Source, clamp(uv + vec2( invSize.x, 0.0), 0.0, 1.0)).rgb);
            float l1_l = luma(texture(Source, clamp(uv + vec2(-invSize.x, 0.0), 0.0, 1.0)).rgb);
            float l1_d = luma(texture(Source, clamp(uv + vec2(0.0,  invSize.y), 0.0, 1.0)).rgb);
            float l1_u = luma(texture(Source, clamp(uv + vec2(0.0, -invSize.y), 0.0, 1.0)).rgb);
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

    vec2 hist_uv = vTexCoord - mv * invSize;
    bool in_bounds = all(greaterThanEqual(hist_uv, vec2(0.0))) && all(lessThanEqual(hist_uv, vec2(1.0)));
    vec3 hist_rgb = texture(OriginalHistory1, clamp(hist_uv, 0.0, 1.0)).rgb;
    vec3 hist_clipped = ycocg_to_rgb(clip_aabb(aabb_min, aabb_max, rgb_to_ycocg(hist_rgb)));

    float disocc = luma(abs(current_rgb - hist_rgb));
    float motion_mag = length(mv);
    float hist_weight = in_bounds ? (0.72 * (1.0 - smoothstep(0.08, 0.35, disocc)) * (1.0 - 0.35 * clamp(motion_mag * 0.25, 0.0, 1.0))) : 0.0;

    vec3 out_rgb = mix(current_rgb, hist_clipped, hist_weight);
    FragColor = vec4(clamp(out_rgb, 0.0, 1.0), 1.0);
}
