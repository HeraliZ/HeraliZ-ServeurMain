#version 150
#define FX_ID 230
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

// ------------------------------------------------------------------ 230 Texture manquante : cubes-pixels au damier
#if FX_ID == 230
vec4 fxMissing(int kind, vec4 prm, vec2 uv, vec3 n, vec3 v, vec2 sd, float t) {
    if (kind == 1) {
        // damier 2x2 rose / noir de la texture manquante ; prm.g : désintégration (des pixels disparaissent), prm.b : plantage
        vec2 cell = floor(uv * 2.0);
        float chk = mod(cell.x + cell.y, 2.0);
        vec2 px = floor(uv * 6.0);
        float h = fract(sin(dot(px + sd * 13.1, vec2(12.9898, 78.233))) * 43758.5453);
        if (h < prm.g * 1.15) return vec4(0.0);
        vec3 magenta = vec3(0.973, 0.0, 0.973);
        float lit = 0.72 + 0.28 * max(dot(n, normalize(vec3(0.3, 1.0, 0.5))), 0.0);
        vec3 col = magenta * chk * lit + magenta * chk * 0.25 + vec3(1.0) * prm.b * 0.3 * chk;
        return vec4(col, 1.0);
    }
    return vec4(0.0);
}
#endif

// ------------------------------------------------------------------ 231 Money Gun : billets éjectés à chaque tir, éclats d'or
#if FX_ID == 231

// billet (uv 0..1 x 0..1, format 2.3:1) : papier vert, guillochis, cadre, médaillon au buste, « 100 », bande holographique
vec3 banknote(vec2 q, float seed, float t, out float foil) {
    float aspect = 2.3;
    vec3 paper = mix(hexc(0x3e7b58), hexc(0x9fd3a6), 0.5 + 0.5 * sin(q.x * 3.0 + seed));
    float g1 = sin(q.x * 90.0 + sin(q.y * 12.0 + seed) * 3.0);
    float g2 = sin(q.y * 70.0 + sin(q.x * 9.0 + seed) * 4.0);
    paper *= 0.9 + 0.07 * g1 * g2;
    float bx = min(q.x, 1.0 - q.x) * aspect, by = min(q.y, 1.0 - q.y);
    float bd = min(bx, by);
    float frame = smoothstep(0.11, 0.095, bd) * smoothstep(0.045, 0.06, bd);
    float rosette = 0.5 + 0.5 * sin(atan(q.y - 0.5, (q.x - 0.5) * aspect) * 40.0);
    paper = mix(paper, mix(hexc(0x1d4a33), hexc(0x2f6d4c), rosette), frame * 0.85);
    float edge = smoothstep(0.03, 0.0, bd);
    vec2 m = (q - vec2(0.27, 0.5)) * vec2(aspect, 1.0);
    float oval = length(m / vec2(0.27, 0.37));
    float inOval = smoothstep(1.0, 0.95, oval);
    paper = mix(paper, hexc(0xd9efd2), inOval * 0.75);
    float head = length((m - vec2(0.0, 0.07)) / vec2(0.085, 0.115));
    float bust = length((m - vec2(0.0, -0.24)) / vec2(0.2, 0.15));
    float figure = max(smoothstep(1.0, 0.88, head), smoothstep(1.0, 0.9, bust) * step(m.y, -0.11));
    paper = mix(paper, hexc(0x234a36), figure * inOval * 0.85);
    paper = mix(paper, hexc(0x1d4a33), smoothstep(0.05, 0.0, abs(oval - 1.0)));
    vec2 d = (q - vec2(0.73, 0.5)) * vec2(aspect, 1.0);
    float one = smoothstep(0.035, 0.02, abs(d.x + 0.3)) * step(abs(d.y), 0.2);
    float z1 = abs(length((d - vec2(-0.1, 0.0)) / vec2(0.1, 0.2)) - 1.0);
    float z2 = abs(length((d - vec2(0.16, 0.0)) / vec2(0.1, 0.2)) - 1.0);
    float digits = max(one, max(smoothstep(0.3, 0.14, z1), smoothstep(0.3, 0.14, z2)));
    paper = mix(paper, hexc(0x123524), digits);
    foil = smoothstep(0.04, 0.025, abs(q.x - 0.52));
    vec3 holo = 0.6 + 0.4 * cos(6.2831 * (q.y * 2.0 + t * 0.35 + seed * 0.1 + vec3(0.0, 0.33, 0.67)));
    paper = mix(paper, holo, foil * 0.85);
    return mix(paper, vec3(0.93, 0.95, 0.9), edge);
}

vec4 fxMoney(int kind, vec4 prm, vec2 uv, vec3 n, vec3 v, vec2 sd, float t) {
    if (kind == 1) {
        // les deux moitiés d'un billet (prm.g = 0 / 1) forment un billet entier
        float foil;
        vec2 q = vec2((prm.g + uv.x) * 0.5, uv.y);
        vec3 col = banknote(q, sd.x * 0.37, t, foil);
        return vec4(litSolid(col, n, v, 0.0, 0.55) + vec3(1.0, 0.95, 0.8) * foil * 0.15, 1.0);
    }
    if (kind == 2) {
        float g = sparkle(uv);
        return vec4(vec3(1.0, 0.86, 0.45) * 1.6, g * prm.g);
    }
    return vec4(0.0);
}
#endif

// ------------------------------------------------------------------ 232 Surchauffe : ondulations de chaleur, fumée, braises
#if FX_ID == 232
vec4 fxOverheat(int kind, vec4 prm, vec2 uv, vec3 n, vec3 v, vec2 sd, float t) {
    if (kind == 1) {
        // ondulation de chaleur : voile clair qui ondoie et monte
        float x = uv.x - 0.5 + 0.1 * sin(uv.y * 14.0 + t * 7.0 + sd.x * 3.0);
        float g = smoothstep(0.5, 0.0, abs(x) * 2.0) * smoothstep(0.0, 0.2, uv.y) * smoothstep(1.0, 0.45, uv.y);
        float bands = 0.5 + 0.5 * sin(uv.y * 30.0 - t * 8.0 + sd.x * 5.0);
        return vec4(vec3(1.0, 0.9, 0.78), g * bands * prm.g * 0.35);
    }
    if (kind == 2) {
        float g = softGlow(uv, 3.0) * (0.55 + 0.45 * fbm3(uv * 3.0 + vec2(sd.x, t * 0.3)));
        return vec4(vec3(0.3, 0.29, 0.28), g * prm.g * 0.7);
    }
    if (kind == 3) {
        float g = pow(1.0 - abs(uv.x - 0.5) * 2.0, 1.5);
        return vec4(mix(vec3(1.0, 0.35, 0.05), vec3(1.0, 0.9, 0.6), uv.y) * 1.6, g * prm.g);
    }
    if (kind == 4) {
        float g = softGlow(uv, 5.0);
        return vec4(vec3(1.0, 0.55, 0.15) * 1.4, g * prm.g * 0.6);
    }
    return vec4(0.0);
}
#endif

// ------------------------------------------------------------------ 233 Trophée : compteur façon StatTrak, effets des paliers
#if FX_ID == 233
float segD(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
    return length(pa - ba * h);
}
// chiffre à 7 segments dans un sprite (uv 0..1)
float digit7(int d, vec2 uv) {
    vec2 p = (uv - 0.5) * vec2(1.0, 1.25);
    int m = d == 0 ? 63 : d == 1 ? 6 : d == 2 ? 91 : d == 3 ? 79 : d == 4 ? 102 : d == 5 ? 109 : d == 6 ? 125 : d == 7 ? 7 : d == 8 ? 127 : 111;
    float w = 0.26, h = 0.46;
    vec2 tl = vec2(-w, h), tr = vec2(w, h), ml = vec2(-w, 0.0), mr = vec2(w, 0.0), bl = vec2(-w, -h), br = vec2(w, -h);
    float dist = 9.0;
    if ((m & 1) != 0) dist = min(dist, segD(p, tl, tr));
    if ((m & 2) != 0) dist = min(dist, segD(p, tr, mr));
    if ((m & 4) != 0) dist = min(dist, segD(p, mr, br));
    if ((m & 8) != 0) dist = min(dist, segD(p, bl, br));
    if ((m & 16) != 0) dist = min(dist, segD(p, ml, bl));
    if ((m & 32) != 0) dist = min(dist, segD(p, tl, ml));
    if ((m & 64) != 0) dist = min(dist, segD(p, ml, mr));
    return smoothstep(0.085, 0.05, dist);
}
vec4 fxTrophy(int kind, vec4 prm, vec2 uv, vec3 n, vec3 v, vec2 sd, float t) {
    if (kind == 1) {
        // plaquette : métal noir mat, léger liseré
        float e = max(abs(uv.x - 0.5), abs(uv.y - 0.5));
        vec3 col = mix(hexc(0x0b0b0d), hexc(0x2a2a30), smoothstep(0.42, 0.5, e));
        return vec4(litSolid(col, n, v, 0.6, 0.8), 1.0);
    }
    if (kind == 2) {
        // chiffre orange lumineux (prm.g = chiffre / 10, prm.b = allumé ; les zéros de tête restent éteints)
        int d = int(prm.g * 10.0);
        float g = digit7(d, uv);
        float ghost = digit7(8, uv) * 0.12;
        vec3 col = vec3(1.0, 0.55, 0.12) * 1.8;
        return vec4(col, max(g * prm.b, ghost));
    }
    if (kind == 3) {
        float g = sparkle(uv);
        vec3 col = prm.b > 0.5 ? vec3(1.0, 0.9, 0.55) : vec3(0.7, 1.0, 1.0);
        return vec4(col * 1.6, g * prm.g);
    }
    if (kind == 4) {
        float g = softGlow(uv, 7.0);
        vec3 col = prm.b > 0.5 ? vec3(1.0, 0.8, 0.35) : vec3(0.75, 0.3, 1.0);
        return vec4(col * 1.4, g * prm.g);
    }
    if (kind == 5) {
        // rune dorée (alphabet des tables d'enchantement stylisé) et son halo
        vec2 l = uv - 0.5;
        float r = 0.0;
        float hsh = prm.b;
        r = max(r, smoothstep(0.06, 0.03, abs(l.x - 0.12 * (hsh - 0.5))) * step(abs(l.y), 0.3));
        r = max(r, smoothstep(0.06, 0.03, abs(l.y - 0.18 * sign(hsh - 0.5))) * step(abs(l.x), 0.22));
        r = max(r, smoothstep(0.06, 0.03, abs(length(l - vec2(0.0, -0.1)) - 0.16)) * step(0.3, fract(hsh * 7.0)));
        r = max(r, smoothstep(0.06, 0.03, abs(l.x + l.y * (hsh - 0.5))) * step(length(l), 0.3) * step(0.6, fract(hsh * 13.0)));
        float halo = softGlow(uv, 6.0) * 0.35;
        return vec4(vec3(1.0, 0.85, 0.4) * 1.6, max(r, halo) * prm.g);
    }
    return vec4(0.0);
}
#endif

vec4 fxShade(int kind, vec4 prm, vec2 uv, vec3 n, vec3 v, vec2 sd, float t) {
#if FX_ID == 208
    return fxSeraph(kind, prm, uv, n, v, sd, t);
#elif FX_ID == 209
    return fxInferno(kind, prm, uv, n, v, sd, t);
#elif FX_ID == 230
    return fxMissing(kind, prm, uv, n, v, sd, t);
#elif FX_ID == 231
    return fxMoney(kind, prm, uv, n, v, sd, t);
#elif FX_ID == 232
    return fxOverheat(kind, prm, uv, n, v, sd, t);
#elif FX_ID == 233
    return fxTrophy(kind, prm, uv, n, v, sd, t);
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
