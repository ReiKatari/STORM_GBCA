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

float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }

void main() {
    vec2 texel = params.SourceSize.zw;
    vec2 f = fract(vTexCoord * params.SourceSize.xy);
    vec2 base = (floor(vTexCoord * params.SourceSize.xy) + 0.5) * texel;
    vec3 c  = texture(Source, base).rgb;
    vec3 n  = texture(Source, base + vec2(0.0, -texel.y)).rgb;
    vec3 s  = texture(Source, base + vec2(0.0,  texel.y)).rgb;
    vec3 w  = texture(Source, base + vec2(-texel.x, 0.0)).rgb;
    vec3 e  = texture(Source, base + vec2( texel.x, 0.0)).rgb;
    vec3 res = c;
    float dNW = abs(luma(n) - luma(w));
    float dNE = abs(luma(n) - luma(e));
    float dSW = abs(luma(s) - luma(w));
    float dSE = abs(luma(s) - luma(e));
    if (dNW < 0.08 && abs(luma(n) - luma(c)) > 0.12 && (f.x + f.y) < 0.55) res = mix(res, 0.5 * (n + w), smoothstep(0.55, 0.15, f.x + f.y));
    if (dNE < 0.08 && abs(luma(n) - luma(c)) > 0.12 && ((1.0 - f.x) + f.y) < 0.55) res = mix(res, 0.5 * (n + e), smoothstep(0.55, 0.15, (1.0 - f.x) + f.y));
    if (dSW < 0.08 && abs(luma(s) - luma(c)) > 0.12 && (f.x + (1.0 - f.y)) < 0.55) res = mix(res, 0.5 * (s + w), smoothstep(0.55, 0.15, f.x + (1.0 - f.y)));
    if (dSE < 0.08 && abs(luma(s) - luma(c)) > 0.12 && ((1.0 - f.x) + (1.0 - f.y)) < 0.55) res = mix(res, 0.5 * (s + e), smoothstep(0.55, 0.15, (1.0 - f.x) + (1.0 - f.y)));
    FragColor = vec4(res, 1.0);
}
