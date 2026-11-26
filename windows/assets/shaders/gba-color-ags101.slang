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
    vec3 c = pow(clamp(texture(Source, vTexCoord).rgb, 0.0, 1.0), vec3(2.8));
    mat3 ags = mat3(
        0.84, 0.14, 0.02,
        0.10, 0.78, 0.12,
        0.06, 0.08, 0.86
    );
    vec3 outc = pow(clamp(ags * c, 0.0, 1.0), vec3(1.0 / 2.2));
    FragColor = vec4(outc, 1.0);
}
