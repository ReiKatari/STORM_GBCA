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
    vec2 uv = vTexCoord * 2.0 - 1.0;
    vec2 offset = abs(uv.yx) / vec2(6.5, 5.5);
    uv = uv + uv * offset * offset;
    uv = uv * 0.5 + 0.5;
    if (uv.x < 0.0 || uv.x > 1.0 || uv.y < 0.0 || uv.y > 1.0) {
        FragColor = vec4(0.0, 0.0, 0.0, 1.0);
        return;
    }
    vec3 color = texture(Source, uv).rgb;
    float scan = 0.86 + 0.14 * sin(uv.y * params.OriginalSize.y * 6.2831853);
    float vignette = clamp(1.05 - dot(uv - 0.5, uv - 0.5) * 0.45, 0.72, 1.0);
    FragColor = vec4(color * scan * vignette * 1.08, 1.0);
}
