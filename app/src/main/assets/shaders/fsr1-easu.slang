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

float fsr_luma(vec3 c) {
    return 0.5 * c.g + 0.25 * (c.r + c.b);
}

void easu_set(inout vec2 dir, inout float len, vec2 pp, bool biS, bool biT, bool biU, bool biV,
              float lA, float lB, float lC, float lD, float lE) {
    float w = 0.0;
    if (biS) w = (1.0 - pp.x) * (1.0 - pp.y);
    if (biT) w =        pp.x  * (1.0 - pp.y);
    if (biU) w = (1.0 - pp.x) *        pp.y;
    if (biV) w =        pp.x  *        pp.y;

    float lenX = max(abs(lD - lC), abs(lC - lB));
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) / max(lenX, 1e-5), 0.0, 1.0);
    len += (lenX * lenX) * w;

    float lenY = max(abs(lE - lC), abs(lC - lA));
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) / max(lenY, 1e-5), 0.0, 1.0);
    len += (lenY * lenY) * w;
}

void easu_tap(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len2, float lob, float clp, vec3 c) {
    vec2 v;
    v.x = (off.x * ( dir.x)) + (off.y * dir.y);
    v.y = (off.x * (-dir.y)) + (off.y * dir.x);
    v *= len2;
    float d2 = min(dot(v, v), clp);
    float wB = (2.0 / 5.0) * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = (25.0 / 16.0) * wB - (25.0 / 16.0 - 1.0);
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

void main() {
    vec2 invSize = params.SourceSize.zw;
    vec2 pp = vTexCoord * params.SourceSize.xy - 0.5;
    vec2 fp = floor(pp);
    pp -= fp;
    vec2 p0 = (fp + 0.5) * invSize;

    vec3 b = texture(Source, clamp(p0 + vec2( 0.0, -1.0) * invSize, 0.0, 1.0)).rgb;
    vec3 c = texture(Source, clamp(p0 + vec2( 1.0, -1.0) * invSize, 0.0, 1.0)).rgb;
    vec3 e = texture(Source, clamp(p0 + vec2(-1.0,  0.0) * invSize, 0.0, 1.0)).rgb;
    vec3 f = texture(Source, clamp(p0 + vec2( 0.0,  0.0) * invSize, 0.0, 1.0)).rgb;
    vec3 g = texture(Source, clamp(p0 + vec2( 1.0,  0.0) * invSize, 0.0, 1.0)).rgb;
    vec3 h = texture(Source, clamp(p0 + vec2( 2.0,  0.0) * invSize, 0.0, 1.0)).rgb;
    vec3 i = texture(Source, clamp(p0 + vec2(-1.0,  1.0) * invSize, 0.0, 1.0)).rgb;
    vec3 j = texture(Source, clamp(p0 + vec2( 0.0,  1.0) * invSize, 0.0, 1.0)).rgb;
    vec3 k = texture(Source, clamp(p0 + vec2( 1.0,  1.0) * invSize, 0.0, 1.0)).rgb;
    vec3 l = texture(Source, clamp(p0 + vec2( 2.0,  1.0) * invSize, 0.0, 1.0)).rgb;
    vec3 n = texture(Source, clamp(p0 + vec2( 0.0,  2.0) * invSize, 0.0, 1.0)).rgb;
    vec3 o = texture(Source, clamp(p0 + vec2( 1.0,  2.0) * invSize, 0.0, 1.0)).rgb;

    float bL = fsr_luma(b); float cL = fsr_luma(c);
    float eL = fsr_luma(e); float fL = fsr_luma(f);
    float gL = fsr_luma(g); float hL = fsr_luma(h);
    float iL = fsr_luma(i); float jL = fsr_luma(j);
    float kL = fsr_luma(k); float lL = fsr_luma(l);
    float nL = fsr_luma(n); float oL = fsr_luma(o);

    vec2 dir = vec2(0.0);
    float len = 0.0;
    easu_set(dir, len, pp, true,  false, false, false, bL, eL, fL, gL, jL);
    easu_set(dir, len, pp, false, true,  false, false, cL, fL, gL, hL, kL);
    easu_set(dir, len, pp, false, false, true,  false, fL, iL, jL, kL, nL);
    easu_set(dir, len, pp, false, false, false, true,  gL, jL, kL, lL, oL);

    float dirR = dot(dir, dir);
    if (dirR < (1.0 / 32768.0)) {
        dir = vec2(1.0, 0.0);
    } else {
        dir *= inversesqrt(dirR);
    }

    len = len * 0.5;
    len *= len;
    float stretch = 1.0 / max(abs(dir.x), abs(dir.y));
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
    float lob = 0.5 + (0.29 - 0.5) * len;
    float clp = 1.0 / lob;

    vec3 min4 = min(min(f, g), min(j, k));
    vec3 max4 = max(max(f, g), max(j, k));

    vec3 aC = vec3(0.0);
    float aW = 0.0;
    easu_tap(aC, aW, vec2( 0.0, -1.0) - pp, dir, len2, lob, clp, b);
    easu_tap(aC, aW, vec2( 1.0, -1.0) - pp, dir, len2, lob, clp, c);
    easu_tap(aC, aW, vec2(-1.0,  0.0) - pp, dir, len2, lob, clp, e);
    easu_tap(aC, aW, vec2( 0.0,  0.0) - pp, dir, len2, lob, clp, f);
    easu_tap(aC, aW, vec2( 1.0,  0.0) - pp, dir, len2, lob, clp, g);
    easu_tap(aC, aW, vec2( 2.0,  0.0) - pp, dir, len2, lob, clp, h);
    easu_tap(aC, aW, vec2(-1.0,  1.0) - pp, dir, len2, lob, clp, i);
    easu_tap(aC, aW, vec2( 0.0,  1.0) - pp, dir, len2, lob, clp, j);
    easu_tap(aC, aW, vec2( 1.0,  1.0) - pp, dir, len2, lob, clp, k);
    easu_tap(aC, aW, vec2( 2.0,  1.0) - pp, dir, len2, lob, clp, l);
    easu_tap(aC, aW, vec2( 0.0,  2.0) - pp, dir, len2, lob, clp, n);
    easu_tap(aC, aW, vec2( 1.0,  2.0) - pp, dir, len2, lob, clp, o);

    vec3 res = clamp(aC / max(aW, 1e-5), min4, max4);
    FragColor = vec4(res, 1.0);
}
