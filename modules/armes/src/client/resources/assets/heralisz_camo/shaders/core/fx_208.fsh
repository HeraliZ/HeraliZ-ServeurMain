#version 150
#define FX_ID 208
// fichier généré par tools/gen_camo_shaders.py, ne pas modifier

// Effets 3D des skins HeralisZ (CamoFx). Gabarit : tools/gen_camo_shaders.py en tire un programme par skin (FX_ID).
// Chaque sommet porte dans sa « couleur » la sorte d'élément (r), deux paramètres (g, b) et un fondu (a) ;
// les vraies couleurs sont ici. La partie entre FX LIB BEGIN / END est aussi chargée par l'aperçu WebGL (tools/camo_preview).

uniform float FxTime;
uniform float FxFade;
uniform float FxGui;
uniform float FxLight;
uniform float FxFp;
uniform float FxScale;

in vec4 vColor;
in vec2 vUv;
in vec3 vNormal;
in vec3 vView;
in vec2 vSeed;

out vec4 fragColor;

// === FX LIB BEGIN ===
const float PI = 3.14159265;
const float TAU = 6.2831853;

float h1(float p) { return fract(sin(p * 91.3458) * 47453.5453); }
float h2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
vec2 h22(vec2 p) { return fract(sin(vec2(dot(p, vec2(127.1, 311.7)), dot(p, vec2(269.5, 183.3)))) * 43758.5453); }

float vn(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(h2(i), h2(i + vec2(1.0, 0.0)), u.x), mix(h2(i + vec2(0.0, 1.0)), h2(i + vec2(1.0, 1.0)), u.x), u.y);
}

float fbm(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 5; k++) {
        v += a * vn(p);
        p = p * 2.03 + vec2(1.7, 9.2);
        a *= 0.5;
    }
    return v;
}

float fbm3(vec2 p) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 3; k++) {
        v += a * vn(p);
        p = p * 2.07 + vec2(3.1, 1.3);
        a *= 0.5;
    }
    return v;
}

vec3 hexc(int h) {
    return vec3(float((h >> 16) & 255), float((h >> 8) & 255), float(h & 255)) / 255.0;
}

float fres(vec3 n, vec3 v, float p) {
    return pow(1.0 - clamp(abs(dot(n, v)), 0.0, 1.0), p);
}

// décor de studio (reflets des métaux)
vec3 studio(vec3 n, vec3 v) {
    vec3 r = reflect(-v, n);
    float y = r.y;
    vec3 sky = mix(vec3(0.42, 0.47, 0.56), vec3(0.97, 0.98, 1.0), smoothstep(0.15, 0.85, y));
    vec3 col = mix(vec3(0.04, 0.04, 0.05), sky, smoothstep(-0.2, 0.08, y));
    col += 1.1 * exp(-pow((y - 0.04) / 0.05, 2.0));
    col += 1.0 * exp(-pow((r.x - 0.45) / 0.14, 2.0)) * smoothstep(0.1, 0.5, y);
    col += 0.6 * exp(-pow((r.x + 0.6) / 0.1, 2.0)) * smoothstep(-0.1, 0.4, y);
    return col;
}

// éclairage des éléments opaques : diffus + reflets du studio ; metal = 0 (vernis) .. 1 (métal teinté)
vec3 litSolid(vec3 base, vec3 n, vec3 v, float metal, float gloss) {
    vec3 L = normalize(vec3(0.35, 0.85, 0.45));
    float dif = 0.3 + 0.7 * max(dot(n, L), 0.0);
    vec3 env = studio(n, v);
    vec3 spec = env * mix(vec3(1.0), clamp(base * 1.6, 0.0, 1.5), metal);
    vec3 col = base * dif * (1.0 - 0.55 * metal) + spec * gloss * (0.2 + 0.8 * fres(n, v, 3.0)) * mix(0.3, 1.0, metal);
    return col * mix(0.3, 1.0, FxLight);
}

float dist01(vec2 uv) { return length(uv * 2.0 - 1.0); }
float softGlow(vec2 uv, float k) { float d = dist01(uv); return exp(-d * d * k); }

// étoile scintillante à 4 branches
float sparkle(vec2 uv) {
    vec2 q = abs(uv * 2.0 - 1.0);
    float r = length(q);
    float ray = max(0.0, 1.0 - q.x * 10.0) * max(0.0, 1.0 - q.y) + max(0.0, 1.0 - q.y * 10.0) * max(0.0, 1.0 - q.x);
    return exp(-r * r * 16.0) + 0.7 * ray * ray;
}

// langue de flamme dans un sprite : uv.x en travers, uv.y de la base (0) à la pointe (1)
float flame(vec2 uv, float t, float seed) {
    float y = uv.y;
    float x = uv.x - 0.5;
    float nz = fbm3(vec2(x * 3.0 + seed * 7.13, y * 2.4 - t * 3.2));
    x += (nz - 0.5) * 0.45 * y;
    float w = 0.5 * (1.0 - y) * (0.7 + 0.6 * nz);
    float body = smoothstep(w, w * 0.2, abs(x));
    return body * smoothstep(0.0, 0.1, y) * smoothstep(1.0, 0.5 + 0.35 * nz, y);
}






// ------------------------------------------------------------------ 208 Séraphin
#if FX_ID == 208
float featherMask(vec2 uv, float seed) {
    // u en travers, v de la base (0) à la pointe (1) : plume effilée, barbes, rachis
    float v = uv.y;
    float w = 0.5 * sin(3.14159 * pow(clamp(v, 0.0, 1.0), 0.55)) * (0.5 + 0.5 * smoothstep(0.0, 0.2, v));
    float x = abs(uv.x - 0.5);
    float body = smoothstep(w, w - 0.08, x);
    float barbs = 0.85 + 0.15 * sin((v * 40.0 + x * 18.0) + seed);
    float gap = step(0.93, h1(floor(v * 14.0) + seed * 3.0)) * step(0.18, x);
    return body * barbs * (1.0 - gap);
}
vec4 fxSeraph(int kind, vec4 prm, vec2 uv, vec3 n, vec3 v, vec2 sd, float t) {
    vec3 white = hexc(0xfffbf0), cream = hexc(0xf2e2bd), gold = hexc(0xf5c451), sky = hexc(0xcfe6ff);
    if (kind == 1 || kind == 2) {
        float m = featherMask(uv, sd.x * 0.1);
        float rachis = smoothstep(0.03, 0.0, abs(uv.x - 0.5)) * (1.0 - uv.y);
        if (kind == 1) {
            vec3 col = mix(white, mix(cream, sky, 0.5), 0.25 * prm.g + 0.15 * (1.0 - uv.y));
            col = mix(col, gold, rachis * 0.5 + smoothstep(0.8, 1.0, uv.y) * 0.5);
            return vec4(col * 1.05 * mix(0.6, 1.0, FxLight), m * 0.95 * prm.a);
        }
        float edge = m * smoothstep(0.2, 0.5, abs(uv.x - 0.5) * 2.0 + uv.y * 0.4);
        float shimmer = 0.6 + 0.4 * sin(t * 2.0 + uv.y * 6.0 + sd.x);
        return vec4(mix(gold, sky, 0.3), edge * 0.28 * shimmer * prm.a);
    }
    if (kind == 3) return vec4(litSolid(gold, n, v, 1.0, 1.3) + gold * 0.25, 1.0);
    if (kind == 4) return vec4(mix(gold, white, 0.3), pow(1.0 - fres(n, v, 1.0), 3.0) * 0.35);
    if (kind == 5) return vec4(mix(gold, white, 0.6), softGlow(uv, 6.0) * prm.g * 0.8);
    if (kind == 6) {
        // plume pleine (ailes des sacs) : opaque, bord net -> toujours juste devant ou derrière le joueur ; blanc nacré,
        // base crème, rachis et pointe dorés, légèrement lumineuse (plumes de lumière). prm.r = rang (bord d'attaque plus doré)
        float m = featherMask(uv, sd.x * 0.1);
        if (m < 0.42) return vec4(0.0);
        float rachis = smoothstep(0.035, 0.0, abs(uv.x - 0.5)) * (1.0 - uv.y * 0.6);
        vec3 col = mix(cream, white, smoothstep(0.0, 0.65, uv.y));
        col = mix(col, mix(cream, sky, 0.45), 0.2 * (0.5 + 0.5 * sin(sd.x * 1.7)));
        col = mix(col, gold, rachis * 0.55 + smoothstep(0.86, 1.0, uv.y) * 0.3 + 0.18 * prm.g);
        vec3 lit = litSolid(col, n, v, 0.1, 0.35);
        return vec4(mix(lit, col * mix(0.55, 1.0, FxLight), 0.4) + col * 0.1, 1.0);
    }
    return vec4(0.0);
}
#endif

// ------------------------------------------------------------------ 209 Chaînes de l'Enfer
#if FX_ID == 209
vec3 fireRamp(float x) {
    return mix(mix(hexc(0x3a0200), hexc(0xff3a00), smoothstep(0.0, 0.45, x)), mix(hexc(0xffb020), hexc(0xfff4c0), smoothstep(0.7, 1.0, x)), smoothstep(0.4, 0.8, x));
}
vec4 fxInferno(int kind, vec4 prm, vec2 uv, vec3 n, vec3 v, vec2 sd, float t) {
    if (kind == 1) {
        float heat = prm.g;
        float rust = fbm3(vec2(uv.x * 9.0 + sd.x, uv.y * 5.0));
        vec3 iron = mix(hexc(0x1b1512), hexc(0x3a2b22), rust);
        vec3 col = litSolid(iron, n, v, 0.6, 0.8);
        float glowPat = smoothstep(0.35, 0.8, heat) * (0.55 + 0.45 * rust);
        col += fireRamp(0.35 + 0.45 * glowPat) * glowPat * 1.2;
        return vec4(col, 1.0);
    }
    if (kind == 2) {
        float fl = flame(uv, t * 1.3, sd.x * 0.17);
        float core = flame(vec2(0.5 + (uv.x - 0.5) * 1.7, uv.y * 1.3), t * 1.3, sd.x * 0.17);
        vec3 col = fireRamp(0.3 + 0.7 * core);
        return vec4(col, fl * 0.95);
    }
    if (kind == 3) return vec4(fireRamp(0.6 + 0.4 * prm.g), softGlow(uv, 7.0) * prm.g);
    return vec4(0.0);
}
#endif





vec3 fxSpectrum(float h) {
    return clamp(abs(fract(h + vec3(0.0, 0.6667, 0.3333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}
vec3 fxFire(float x) {
    return mix(mix(hexc(0x3a0200), hexc(0xff3a00), smoothstep(0.0, 0.45, x)), mix(hexc(0xffb020), hexc(0xfff4c0), smoothstep(0.7, 1.0, x)), smoothstep(0.4, 0.8, x));
}






// ------------------------------------------------------------------ 70 Tempête (arcs électriques)
#if FX_ID == 70
vec4 fxStorm(int kind, vec4 prm, vec2 uv, vec3 n, vec3 v, vec2 sd, float t) {
    float b = prm.b;
    float prof = 1.0 - smoothstep(0.0, 0.5, abs(uv.y - 0.5));
    vec3 col = prm.g < 0.25 ? vec3(0.03, 0.07, 0.22) : prm.g < 0.75 ? vec3(0.12, 0.3, 0.8) : vec3(0.9, 0.95, 1.0);
    return vec4(col * b * 1.6, prof);
}
#endif

vec4 fxShade(int kind, vec4 prm, vec2 uv, vec3 n, vec3 v, vec2 sd, float t) {
#if FX_ID == 208
    return fxSeraph(kind, prm, uv, n, v, sd, t);
#elif FX_ID == 209
    return fxInferno(kind, prm, uv, n, v, sd, t);
#elif FX_ID == 70
    return fxStorm(kind, prm, uv, n, v, sd, t);
#else
    return vec4(1.0, 0.0, 1.0, 1.0);
#endif
}
// === FX LIB END ===

void main() {
    int kind = int(vColor.r * 255.0 + 0.5);
    vec3 v = FxGui > 0.5 ? vec3(0.0, 0.0, 1.0) : normalize(-vView);
    vec3 n = normalize(vNormal);
    if (dot(n, v) < 0.0) {
        n = -n;
    }
    vec4 c = fxShade(kind, vColor, vUv, n, v, vSeed, FxTime);
    float near = FxGui > 0.5 ? 1.0 : smoothstep(0.05, 0.16, length(vView));
    if (FxFp > 0.5) {
        // vu par le porteur : ce qui passe tout près de l'œil (autour de la crosse) disparaît ; arme tenue (FxFp = 1) :
        // le centre de l'écran (réticule, visée) reste aussi toujours dégagé (FxFp = 2 : apparition, près de l'œil seulement)
        near *= FxFp < 1.5 ? smoothstep(0.24, 0.62, length(vView)) : smoothstep(0.1 * FxScale, 0.26 * FxScale, length(vView));
        if (FxFp < 1.5) {
            float tanA = length(vView.xy) / max(-vView.z, 0.001);
            near *= smoothstep(0.09, 0.32, tanA);
        }
    }
    c.a *= near * FxFade;
    if (c.a <= 0.004) {
        discard;
    }
    fragColor = c;
}
