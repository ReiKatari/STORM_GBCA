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

void main() {
    vec3 src = texture(Source, vTexCoord).rgb;
    float luma = dot(src, vec3(0.299, 0.587, 0.114));
    vec3 dark = vec3(0.059, 0.220, 0.059);
    vec3 light = vec3(0.608, 0.737, 0.059);
    vec3 olive = mix(dark, light, clamp(luma, 0.0, 1.0));
    vec2 cell = fract(vTexCoord * params.OriginalSize.xy);
    vec2 dotMask = smoothstep(0.0, 0.16, cell) * (1.0 - smoothstep(0.84, 1.0, cell));
    vec3 bg = vec3(0.545, 0.675, 0.059);
    FragColor = vec4(mix(bg * 0.90, olive, mix(0.35, 1.0, dotMask.x * dotMask.y)), 1.0);
}
