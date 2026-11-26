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
    const float PI = 3.14159265;
    vec3 c = texture(Source, vTexCoord).rgb;
    vec2 angle = 2.0 * PI * (vTexCoord * params.OriginalSize.xy);
    float yfactor = (16.0 + sin(angle.y)) / 17.0;
    vec3 xfactor = (16.0 + sin(angle.x + vec3(0.0, 2.094, 4.188))) / 17.0;
    FragColor = vec4(c * yfactor * xfactor * 1.06, 1.0);
}
