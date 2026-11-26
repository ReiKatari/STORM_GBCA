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
    vec2 subpixel = fract(vTexCoord * params.OriginalSize.xy);
    vec2 grid = smoothstep(0.0, 0.14, subpixel) * (1.0 - smoothstep(0.86, 1.0, subpixel));
    float mask = mix(0.82, 1.0, grid.x * grid.y);
    float subR = mix(0.92, 1.06, smoothstep(0.0, 0.33, subpixel.x) * (1.0 - smoothstep(0.30, 0.42, subpixel.x)));
    float subG = mix(0.92, 1.06, smoothstep(0.30, 0.42, subpixel.x) * (1.0 - smoothstep(0.62, 0.72, subpixel.x)));
    float subB = mix(0.92, 1.06, smoothstep(0.62, 0.72, subpixel.x));
    FragColor = vec4(color * mask * vec3(subR, subG, subB), 1.0);
}
