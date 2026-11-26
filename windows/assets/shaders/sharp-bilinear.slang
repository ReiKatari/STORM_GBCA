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
    vec2 texel = vTexCoord * params.SourceSize.xy;
    vec2 scale = max(params.OutputSize.xy * params.SourceSize.zw, vec2(1.0));
    vec2 region_range = 0.5 - 0.5 / scale;
    vec2 distFromCenter = fract(texel) - 0.5;
    vec2 f = clamp(distFromCenter / region_range, -0.5, 0.5) + 0.5;
    vec2 sampleUv = (floor(texel) + f) * params.SourceSize.zw;
    FragColor = vec4(texture(Source, sampleUv).rgb, 1.0);
}
