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
    vec2 dx = vec2(params.SourceSize.z, 0.0);
    vec2 dy = vec2(0.0, params.SourceSize.w);
    vec3 c = texture(Source, vTexCoord).rgb;
    vec3 blur = (texture(Source, vTexCoord - dx).rgb +
                 texture(Source, vTexCoord + dx).rgb +
                 texture(Source, vTexCoord - dy).rgb +
                 texture(Source, vTexCoord + dy).rgb) * 0.25;
    vec3 sharp = clamp(c + (c - blur) * 0.35, 0.0, 1.0);
    float luma = dot(sharp, vec3(0.2126, 0.7152, 0.0722));
    vec3 sat = clamp(mix(vec3(luma), sharp, 1.20), 0.0, 1.0);
    vec3 contrasted = clamp((sat - 0.5) * 1.06 + 0.5, 0.0, 1.0);
    FragColor = vec4(contrasted, 1.0);
}
