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
    vec3 color = texture(Source, vTexCoord).rgb;
    float lum = dot(color, vec3(0.299, 0.587, 0.114));
    vec3 darkOlive = vec3(0.06, 0.14, 0.07);
    vec3 lightOlive = vec3(0.68, 0.79, 0.52);
    float levels = floor(clamp(lum, 0.0, 1.0) * 7.0 + 0.5) / 7.0;
    vec3 tone = mix(darkOlive, lightOlive, levels);
    FragColor = vec4(tone, 1.0);
}
