#version 450

#pragma parameter FSR_SHARPNESS "FSR RCAS Sharpness" 0.55 0.0 1.0 0.05

layout(std140, set = 0, binding = 0) uniform UBO {
    mat4 MVP;
} global;

layout(push_constant) uniform Push {
    vec4 SourceSize;
    vec4 OriginalSize;
    vec4 OutputSize;
    uint FrameCount;
    float FSR_SHARPNESS;
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

float fsr_luma(vec3 c) {
    return 0.5 * c.g + 0.25 * (c.r + c.b);
}

void main() {
    vec2 invSize = params.SourceSize.zw;
    vec3 b = texture(Source, clamp(vTexCoord + vec2( 0.0, -invSize.y), 0.0, 1.0)).rgb;
    vec3 d = texture(Source, clamp(vTexCoord + vec2(-invSize.x,  0.0), 0.0, 1.0)).rgb;
    vec3 e = texture(Source, clamp(vTexCoord, 0.0, 1.0)).rgb;
    vec3 f = texture(Source, clamp(vTexCoord + vec2( invSize.x,  0.0), 0.0, 1.0)).rgb;
    vec3 h = texture(Source, clamp(vTexCoord + vec2( 0.0,  invSize.y), 0.0, 1.0)).rgb;

    float bL = fsr_luma(b);
    float dL = fsr_luma(d);
    float eL = fsr_luma(e);
    float fL = fsr_luma(f);
    float hL = fsr_luma(h);

    float mnL = min(min(bL, dL), min(fL, hL));
    float mxL = max(max(bL, dL), max(fL, hL));
    float nz = 0.25 * (bL + dL + fL + hL) - eL;
    nz = clamp(abs(nz) / max(max(mxL, eL) - min(mnL, eL), 1e-5), 0.0, 1.0);
    nz = -0.5 * nz + 1.0;

    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));

    vec3 hitMin = min(mn4, e) / max(4.0 * mx4, vec3(1e-5));
    vec3 hitMax = (vec3(1.0) - max(mx4, e)) / max(4.0 * (vec3(1.0) - mn4), vec3(1e-5));
    vec3 lobeRGB = max(-vec3(0.1875), -min(hitMin, hitMax));
    float lobe = max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b));

    float sharp = clamp(params.FSR_SHARPNESS, 0.0, 1.0);
    lobe *= sharp * nz;

    float rcpL = 1.0 / (4.0 * lobe + 1.0);
    vec3 res = clamp((lobe * (b + d + f + h) + e) * rcpL, 0.0, 1.0);
    FragColor = vec4(res, 1.0);
}
