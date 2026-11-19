#version 150
#define CAMO_ID @CAMO_ID@
// fichier généré par outils/generer_camos.py depuis outils/camo_gabarit.fsh (dossier du mod) : ne pas modifier ici

#moj_import <fog.glsl>

// Camouflages HeralisZ. Gabarit : outils/camo_gabarit.fsh, dont outils/generer_camos.py tire un programme par camo (CAMO_ID)
// -> petits shaders rapides.
// Règle d'or : la couleur ne dépend que de la position 3D sur l'arme (lue dans la carte
// de positions Sampler3), jamais de la pièce -> motif continu, pas de mosaïque. CamoTime anime les camos.
// La partie entre CAMO LIB BEGIN / END est aussi chargée telle quelle par l'aperçu WebGL (outils/apercu_camos).

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform int CamoId;
uniform int CamoParts;
uniform float CamoTime;
// apparition de l'arme (skins à créature) : front de matérialisation 0 (crosse) .. 1 (canon), -1 = arme entière
uniform float CamoReveal;
// coordonnées de texture -> carte de positions : (u0, v0, largeur, hauteur). Armes : (0, 0, 1, 1) ; sacs : leur texture
// dans l'atlas des blocs. Hors du rectangle (autre texture du même modèle) : pas de camo.
uniform vec4 CamoUvRect;
// rotation du repère de la vue vers celui du monde (posée par Minecraft) : reflets liés au monde des camos refaits
uniform mat3 IViewRotMat;

in float vertexDistance;
in vec4 vertexColor;
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;
in vec3 viewNormal;
// position du fragment dans le repère de la vue (reflets en perspective)
in vec3 viewPos;

out vec4 fragColor;

// === CAMO LIB BEGIN ===
// relief d'usinage (voir « relief d'usinage des armes ») : toute la LIB lit la normale avec relief VN_DETAIL à la place de
// viewNormal ; le main la pose avant chaque camo (normalize(viewNormal), puis appliquerRelief sur les armes)
vec3 VN_DETAIL = vec3(0.0, 0.0, 1.0);
#define viewNormal VN_DETAIL
// camos refaits en 2026-10 : relief de l'arme gardé et reflets liés au monde (voir « refonte 2026-10 »)
#if CAMO_ID == 1 || CAMO_ID == 5 || CAMO_ID == 43 || CAMO_ID == 44 || (CAMO_ID >= 20 && CAMO_ID <= 25)
#define CAMO_RELIEF
#endif
// nouveaux camos du 19/11 (Opale, Marbre noir, Patine, Néon, Rubis, Phénix, Abysses ; Forge, Or craquelé, Dichroïque,
// Vipère, Rouages, Prisme) : même relief, mêmes reflets
#if (CAMO_ID >= 80 && CAMO_ID <= 89) || (CAMO_ID >= 210 && CAMO_ID <= 212)
#define CAMO_RELIEF
#endif
const float TAU = 6.2831853;

// ------------------------------------------------------------------ bruit
float hash(vec3 p) {
    p = fract(p * 0.3183099 + vec3(0.71, 0.113, 0.419));
    p *= 17.0;
    return fract(p.x * p.y * p.z * (p.x + p.y + p.z));
}

vec3 hash3(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453123);
}

float vnoise(vec3 x) {
    vec3 i = floor(x);
    vec3 f = fract(x);
    vec3 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash(i), hash(i + vec3(1, 0, 0)), u.x),
                   mix(hash(i + vec3(0, 1, 0)), hash(i + vec3(1, 1, 0)), u.x), u.y),
               mix(mix(hash(i + vec3(0, 0, 1)), hash(i + vec3(1, 0, 1)), u.x),
                   mix(hash(i + vec3(0, 1, 1)), hash(i + vec3(1, 1, 1)), u.x), u.y), u.z);
}

float fbm(vec3 p, int octaves) {
    float v = 0.0;
    float a = 0.5;
    for (int k = 0; k < 6; k++) {
        if (k >= octaves) break;
        v += a * vnoise(p);
        p = p * 2.03 + vec3(1.7, 9.2, 3.1);
        a *= 0.5;
    }
    return v;
}

// cellules de Voronoï 3D : f1/f2 = distances au 1er/2e point, cell = cellule du 1er, fp = position du 1er
void worley(vec3 q, out float f1, out float f2, out vec3 cell, out vec3 fp) {
    vec3 b = floor(q);
    f1 = 9.0; f2 = 9.0; cell = b; fp = b;
    for (int x = -1; x <= 1; x++)
    for (int y = -1; y <= 1; y++)
    for (int z = -1; z <= 1; z++) {
        vec3 cc = b + vec3(float(x), float(y), float(z));
        vec3 pt = cc + 0.15 + 0.7 * hash3(cc);
        float d = length(q - pt);
        if (d < f1) { f2 = f1; f1 = d; cell = cc; fp = pt; }
        else if (d < f2) { f2 = d; }
    }
}

// normale de la face dans le repère de l'arme (dérivées écran de la position lue) et coordonnées 2D sur la face
vec3 faceNormal(vec3 p) {
    vec3 n = cross(dFdx(p), dFdy(p));
    float l = length(n);
    return l > 1e-12 ? n / l : vec3(0.0, 0.0, 1.0);
}

vec2 surf2d(vec3 p, vec3 n) {
    vec3 a = abs(n);
    if (a.x > a.y && a.x > a.z) return p.zy;
    if (a.y > a.z) return p.xz;
    return p.xy;
}

// dégradé à 6 couleurs
vec3 ramp6(float t, float s[6], vec3 c[6]) {
    t = clamp(t, 0.0, 1.0);
    vec3 col = c[0];
    for (int i = 1; i < 6; i++) {
        col = mix(col, c[i], smoothstep(s[i - 1], s[i], t));
    }
    return col;
}

vec3 hexc(int h) {
    return vec3(float((h >> 16) & 255), float((h >> 8) & 255), float(h & 255)) / 255.0;
}

vec3 spectrum(float h) {
    return clamp(abs(fract(h + vec3(0.0, 0.6667, 0.3333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// ------------------------------------------------------------------ Dark Matter (animé) — validé, ne pas toucher
vec3 darkMatter(vec3 p, out vec3 em) {
    float t = CamoTime * 3.5;
    float T = CamoTime;
    float u = p.x + 0.5;                       // 0 = bout de crosse, 1 = bout du canon
    vec3 q = p * 9.0;
    // champ de déformation qui évolue dans le temps (4e dimension simulée par z) : les volutes bougent partout, pas seulement en translation
    vec3 w = vec3(fbm(q * 0.7 + vec3(0.0, 0.0, T * 0.32), 3),
                  fbm(q * 0.7 + vec3(5.2, 1.3, -T * 0.27), 3),
                  fbm(q * 0.7 + vec3(9.7, 4.1, T * 0.22), 3));
    vec3 w2 = vec3(fbm(q * 1.4 + 3.0 * w + vec3(1.7, 0.0, T * 0.18), 2), fbm(q * 1.4 + 3.0 * w + vec3(8.3, 2.8, -T * 0.21), 2), 0.0);
    float neb = fbm(q + 2.2 * w + 0.9 * w2 + vec3(T * 0.04, -T * 0.025, T * 0.15), 5);
    float neb2 = fbm(q * 1.7 - 1.3 * w + vec3(3.1, 0.0, -T * 0.2), 4);
    float big = fbm(q * 0.35 + vec3(17.0, 0.0, T * 0.08), 2);
    // profil de lumière validé : crosse très claire -> milieu plus sombre -> canon plus clair -> bout du canon sombre
    float env = u < 0.10 ? mix(0.55, 0.40, u / 0.10)
              : u < 0.28 ? mix(0.40, 0.22, (u - 0.10) / 0.18)
              : u < 0.45 ? mix(0.22, 0.16, (u - 0.28) / 0.17)
              : u < 0.62 ? mix(0.16, 0.22, (u - 0.45) / 0.17)
              : u < 0.80 ? mix(0.22, 0.34, (u - 0.62) / 0.18)
              : u < 0.93 ? mix(0.34, 0.16, (u - 0.80) / 0.13)
              : mix(0.16, -0.10, (u - 0.93) / 0.07);
    float tn = clamp((neb - 0.24) * 2.2 + 0.45 * (big - 0.5) + env, 0.0, 1.0);
    float s[6] = float[6](0.0, 0.22, 0.42, 0.60, 0.80, 1.0);
    vec3 c[6] = vec3[6](hexc(0x010006), hexc(0x070320), hexc(0x1c0a52), hexc(0x43108f), hexc(0x9a2ad6), hexc(0xffb3f5));
    vec3 col = ramp6(tn, s, c);
    col += clamp(0.5 - tn, 0.0, 1.0) * clamp((big - 0.55) * 3.0, 0.0, 1.0) * hexc(0x0b2a7a) * 0.8;
    float ridge = 1.0 - abs(2.0 * neb2 - 1.0);
    float fil = pow(clamp((ridge - 0.82) / 0.18, 0.0, 1.0), 2.0) * clamp((neb - 0.35) * 3.0, 0.0, 1.0);
    vec3 filc = mix(mix(vec3(0.25, 0.42, 1.0), vec3(0.61, 0.30, 1.0), smoothstep(0.0, 0.5, neb2)), vec3(1.0, 0.36, 0.88), smoothstep(0.5, 1.0, neb2));
    em = fil * filc * 0.8;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec3 sq = q * (38.0 - fi * 10.0) + vec3(t * (0.25 + fi * 0.45), t * 0.05 * fi, 0.0) + vec3(fi * 31.7);
        float st = clamp((vnoise(sq) - 0.925) / 0.035, 0.0, 1.0);
        float tw = 0.55 + 0.45 * sin(t * (1.5 + fi) + hash(floor(sq)) * TAU);
        em += st * tw * (0.55 + fi * 0.35) * vec3(0.90, 0.93, 1.0);
    }
    vec3 bq = q * 13.0 + vec3(71.0);
    float b2 = vnoise(bq);
    float btw = 0.7 + 0.3 * sin(t * 2.3 + hash(floor(bq)) * TAU);
    em += (1.4 * clamp((b2 - 0.975) / 0.02, 0.0, 1.0) + 0.3 * pow(clamp((b2 - 0.93) / 0.07, 0.0, 1.0), 3.0)) * btw * vec3(1.0, 0.95, 1.0);
    vec3 floorCol = hexc(0x0e0630) * (0.6 + 0.4 * clamp(env + 0.3, 0.0, 1.0));
    return max(col, floorCol);
}

// ------------------------------------------------------------------ Or poli (statique, grands reflets doux, éclat qui balaie lentement)
vec3 goldMetal(vec3 p) {
    float f = fbm(p * vec3(3.0, 6.0, 6.0) + vec3(11.0), 3);
    float tt = 0.56 + 0.55 * (f - 0.5) + 0.16 * viewNormal.y + 0.05 * sin(p.x * 14.0 + p.y * 30.0);
    float s[6] = float[6](0.0, 0.2, 0.4, 0.62, 0.84, 1.0);
    vec3 c[6] = vec3[6](hexc(0x3a2203), hexc(0x7a5007), hexc(0xb8860b), hexc(0xe5b43a), hexc(0xf8dc88), hexc(0xfff6da));
    vec3 col = ramp6(tt, s, c);
    col *= 0.96 + 0.04 * vnoise(vec3(p.x * 25.0, p.y * 700.0, p.z * 700.0));   // brossage fin
    col += 0.25 * pow(clamp(viewNormal.y * 0.5 + 0.5, 0.0, 1.0), 4.0) * hexc(0xfff1c2);
    return col;
}

float goldSweep(vec3 p) {
    float u = p.x + 0.5;
    return exp(-pow((u - (fract(CamoTime * 0.07) * 1.8 - 0.4) - p.y * 1.5) / 0.04, 2.0));
}

vec3 gold(vec3 p, out vec3 em) {
    em = goldSweep(p) * vec3(1.0, 0.86, 0.55) * 0.5;
    return goldMetal(p);
}


// ------------------------------------------------------------------ Lave : croûte de basalte qui craque sur une lave qui coule
vec3 lava(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(6.0, 11.0, 11.0);
    vec3 w = vec3(fbm(q * 0.8 + vec3(-t * 0.07, 0.0, 0.0), 3), fbm(q * 0.8 + vec3(3.3, -t * 0.05, 7.1), 3), 0.0);
    float heat = fbm(q * 1.1 + 1.8 * w + vec3(-t * 0.16, t * 0.02, 0.0), 5);
    float f1, f2; vec3 cell, fp;
    worley(q * 2.6 + vec3(-t * 0.05, 0.0, 0.0) + 0.6 * w, f1, f2, cell, fp);
    float edge = f2 - f1;
    float g1, g2; vec3 cell2, fp2;
    worley(q * 7.0 + cell * 0.3, g1, g2, cell2, fp2);
    float crack = max(1.0 - smoothstep(0.015, 0.07, edge), 0.55 * (1.0 - smoothstep(0.01, 0.035, g2 - g1)) * smoothstep(0.4, 0.6, heat));
    float molten = smoothstep(0.56, 0.64, heat);
    float glow = clamp(max(crack * smoothstep(0.28, 0.5, heat), molten), 0.0, 1.0);
    float pulse = 0.75 + 0.25 * sin(t * 1.7 + heat * 12.0);
    float s[6] = float[6](0.0, 0.2, 0.45, 0.65, 0.85, 1.0);
    vec3 c[6] = vec3[6](hexc(0x2a0000), hexc(0x8a0a00), hexc(0xe02a00), hexc(0xff6a00), hexc(0xffb13a), hexc(0xfff1b8));
    vec3 hot = ramp6(heat * 1.25 * pulse + 0.15 * crack + 0.35 * (fbm(q * 3.0 + vec3(-t * 0.35, 0.0, t * 0.1), 3) - 0.5), s, c);
    float rough = fbm(q * 4.0 + cell, 3);
    vec3 crust = mix(hexc(0x0b0504), hexc(0x33190f), rough * rough) + hot * 0.22 * exp(-edge * 14.0) * smoothstep(0.3, 0.55, heat);
    em = hot * glow * pulse * 0.85;
    return mix(crust, hot, glow);
}

// ------------------------------------------------------------------ Galaxie bleue (même famille que Dark Matter, étoiles filantes)
vec3 galaxy(vec3 p, out vec3 em) {
    float t = CamoTime;
    float u = p.x + 0.5;
    vec3 q = p * 8.0;
    vec3 flow = vec3(-t * 0.045, t * 0.015, t * 0.02);
    vec3 w = vec3(fbm(q * 0.7 + flow, 3), fbm(q * 0.7 + vec3(2.9) - flow, 3), fbm(q * 0.7 + vec3(6.1) + flow * 1.4, 3));
    float neb = fbm(q + 2.4 * w, 5);
    float neb2 = fbm(q * 1.6 + 1.5 * w + vec3(t * 0.03, 0.0, 0.0), 4);
    float arm = sin((p.x * 10.0 + 3.0 * neb) - t * 0.2) * 0.5 + 0.5;          // bras de galaxie
    float tn = clamp((neb - 0.30) * 2.2 + 0.22 * (arm - 0.5), 0.0, 1.0);
    float s[6] = float[6](0.0, 0.2, 0.42, 0.62, 0.82, 1.0);
    vec3 c[6] = vec3[6](hexc(0x00030d), hexc(0x021338), hexc(0x06307f), hexc(0x1463d8), hexc(0x2fb6f5), hexc(0x9eeeff));
    vec3 col = ramp6(tn, s, c);
    float ridge = 1.0 - abs(2.0 * neb2 - 1.0);
    float fil = pow(clamp((ridge - 0.84) / 0.16, 0.0, 1.0), 2.0) * clamp((neb - 0.33) * 3.0, 0.0, 1.0);
    em = fil * mix(hexc(0x39e6ff), hexc(0xb07bff), smoothstep(0.3, 0.8, neb2)) * 0.8;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        vec3 sq = q * (42.0 - fi * 11.0) + vec3(-t * (0.3 + fi * 0.5), 0.0, t * 0.04 * fi) + vec3(fi * 19.3);
        float st = clamp((vnoise(sq) - 0.93) / 0.035, 0.0, 1.0);
        float tw = 0.55 + 0.45 * sin(t * (1.8 + fi) + hash(floor(sq)) * TAU);
        em += st * tw * (0.6 + fi * 0.35) * vec3(0.85, 0.95, 1.0);
    }
    // étoiles filantes : départ, direction, vitesse et longueur aléatoires à chaque passage
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float cyc = t * (0.18 + 0.05 * fi) + fi * 0.37;
        float k = floor(cyc);
        vec3 rnd = hash3(vec3(k, fi, 7.0));
        float ang = (rnd.x - 0.5) * 1.6 + (rnd.z > 0.5 ? 3.14159265 : 0.0);
        vec2 dir = vec2(cos(ang), sin(ang) * 0.35);
        vec2 start = vec2(rnd.y - 0.5, (rnd.z - 0.5) * 0.14) - dir * 0.35;
        vec2 head = start + dir * fract(cyc) * 0.9;
        vec2 rel = vec2(p.x, p.y) - head;
        float along = dot(rel, dir);
        float across = length(rel - dir * along);
        float tail = along < 0.0 ? exp(along * (18.0 + 20.0 * rnd.x)) : exp(-along * 500.0);
        float fade = smoothstep(0.0, 0.15, fract(cyc)) * smoothstep(1.0, 0.8, fract(cyc));
        em += tail * exp(-pow(across / 0.003, 2.0)) * fade * vec3(0.8, 0.95, 1.0) * 2.4;
    }
    return max(col, hexc(0x020a24));
}


// ------------------------------------------------------------------ Toxique 115 : liquide lumineux qui coule dans du métal noir
vec3 toxic(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(6.0, 11.0, 11.0);
    vec3 w = vec3(fbm(q * 0.7 + vec3(0.0, -t * 0.08, 0.0), 3), fbm(q * 0.7 + vec3(5.5, 2.2, -t * 0.05), 3), 0.0);
    float f = fbm(q + 2.0 * w + vec3(-t * 0.12, 0.0, 0.0), 5);
    float vein = max(1.0 - smoothstep(0.0, 0.05, abs(f - 0.5)), 0.6 * (1.0 - smoothstep(0.0, 0.02, abs(fbm(q * 2.3 + w + vec3(0.0, -t * 0.1, 0.0), 3) - 0.5))));
    float pool = smoothstep(0.60, 0.68, f);
    float liquid = max(vein, pool);
    // bulles qui montent dans les flaques
    float f1, f2; vec3 cell, fp;
    worley(q * 3.0 + vec3(0.0, -t * 0.5, 0.0), f1, f2, cell, fp);
    float bub = smoothstep(0.30, 0.22, f1) * smoothstep(0.12, 0.2, f1) * step(0.55, hash(cell)) * pool;
    float pulse = 0.8 + 0.2 * sin(t * 2.2 + f * 20.0);
    vec3 glowc = mix(hexc(0x39ff14), hexc(0x00ffd0), smoothstep(0.45, 0.75, f));
    vec3 metal = mix(hexc(0x050a07), hexc(0x1a3024), fbm(q * 3.0, 3)) + 0.10 * pow(clamp(viewNormal.y * 0.5 + 0.5, 0.0, 1.0), 3.0) * hexc(0x9fffc8) + hexc(0x0f3a1c) * smoothstep(0.12, 0.0, abs(f - 0.5)) * 0.6;
    em = (liquid * pulse + bub * 1.2) * glowc * 0.9;
    return mix(metal, glowc * 0.55, liquid);
}

// ------------------------------------------------------------------ Black Ice : arme congelée (givre, fougères de glace, cristaux qui scintillent)
vec3 blackIce(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 n = faceNormal(p);
    vec3 q = p * vec3(8.0, 14.0, 14.0);
    vec3 metal = mix(hexc(0x04070b), hexc(0x0f1925), fbm(q * 2.0, 3));            // métal sombre sous la glace
    vec3 ice = mix(hexc(0x0b2640), hexc(0x3d82b4), fbm(q * 1.4 + vec3(5.0), 3));  // couche de glace bleue
    vec3 col = mix(metal, ice, 0.55);
    // givre : plus épais sur le dessus de l'arme, par plaques
    float frostAmt = clamp(smoothstep(0.38, 0.68, fbm(q * 0.9 + vec3(3.0), 4)) + 0.35 * clamp(n.y, 0.0, 1.0), 0.0, 1.0);
    vec3 frost = mix(hexc(0x9ccbe8), hexc(0xf4fcff), fbm(q * 7.0, 3));
    col = mix(col, frost, frostAmt * 0.78);
    // fougères de glace (cristaux ramifiés) qui courent sur la surface
    vec2 uv = surf2d(p, n) * 26.0;
    float r1 = 1.0 - abs(2.0 * fbm(vec3(uv, 1.3), 5) - 1.0);
    float r2 = 1.0 - abs(2.0 * fbm(vec3(uv * 2.3, 7.7), 4) - 1.0);
    float fern = smoothstep(0.84, 0.97, r1) + 0.7 * smoothstep(0.88, 0.99, r2);
    col += fern * (0.35 + 0.65 * frostAmt) * hexc(0xe9f8ff) * 0.55;
    col += 0.22 * pow(clamp(viewNormal.y * 0.5 + 0.5, 0.0, 1.0), 5.0) * hexc(0xdff3ff);   // brillance de la glace
    // cristaux de givre qui scintillent (paillettes)
    vec3 sq = floor(q * 45.0);
    float sparkle = step(0.975, hash(sq)) * pow(max(0.0, sin(t * 3.0 + hash(sq + 3.0) * 40.0)), 10.0);
    em = sparkle * frostAmt * vec3(1.3) + fern * 0.05 * hexc(0x9fdcff) + ice * 0.04;
    return col;
}

// ------------------------------------------------------------------ Nid d'abeille : bouclier énergétique hexagonal (verre fumé, vagues d'énergie)
vec3 honeycomb(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 n = faceNormal(p);
    vec2 hq = surf2d(p, n) * 34.0;
    vec2 r = vec2(1.0, 1.7320508), hh = r * 0.5;
    vec2 a = mod(hq, r) - hh, b = mod(hq - hh, r) - hh;
    vec2 g = dot(a, a) < dot(b, b) ? a : b;
    vec2 id = hq - g;
    float hd = max(abs(g.x), dot(abs(g), vec2(0.5, 0.8660254)));
    float h = hash(vec3(id, 2.0));
    vec3 tile = mix(hexc(0x0a0f1c), hexc(0x243353), smoothstep(0.5, 0.0, hd)) * (0.8 + 0.4 * h);   // verre fumé bombé
    float wave = pow(0.5 + 0.5 * sin(id.x * 0.09 + id.y * 0.035 - t * 1.6), 6.0);                  // vague qui parcourt l'arme
    float pulse = pow(max(0.0, sin(t * (0.6 + h) + h * 40.0)), 14.0);                             // alvéoles qui s'allument au hasard
    float lit = max(wave, pulse);
    vec3 ec = mix(hexc(0x27e0ff), hexc(0xb04bff), 0.5 + 0.5 * sin(t * 0.35 + id.x * 0.04));
    float edge = smoothstep(0.42, 0.49, hd);
    vec3 col = tile + ec * 0.22 * lit * (1.0 - edge) * smoothstep(0.5, 0.0, hd);
    col = mix(col, mix(hexc(0x2a3550), ec, 0.4 + 0.6 * lit), edge);
    col += 0.15 * pow(clamp(viewNormal.y * 0.5 + 0.5, 0.0, 1.0), 5.0);
    em = ec * (edge * (0.12 + 0.9 * lit) + (1.0 - edge) * lit * 0.12 * smoothstep(0.5, 0.0, hd));
    return col;
}


vec3 woodland(vec3 p, out vec3 em) {
    vec3 q = p * vec3(13.0, 22.0, 22.0);
    float a = fbm(q + vec3(1.0), 4), b = fbm(q * 1.3 + vec3(7.0), 4), c = fbm(q * 1.7 + vec3(13.0), 4);
    vec3 col = hexc(0x6b6a3e);
    col = mix(col, hexc(0x3b4a24), step(0.52, a));
    col = mix(col, hexc(0x5a3d22), step(0.56, b));
    col = mix(col, hexc(0x141610), step(0.60, c));
    em = vec3(0.0);
    return col * (0.93 + 0.07 * vnoise(p * 300.0));
}

vec3 digital(vec3 p, out vec3 em) {
    vec3 n = faceNormal(p);
    vec2 uv = floor(surf2d(p, n) * 160.0) / 160.0 * 26.0;             // pixels
    float a = fbm(vec3(uv, 1.0), 3), b = fbm(vec3(uv * 1.4, 5.0), 3);
    vec3 col = hexc(0x8b8f94);
    col = mix(col, hexc(0x5d6268), step(0.48, a));
    col = mix(col, hexc(0x2b2e33), step(0.56, b));
    col = mix(col, hexc(0xc5c9cd), step(0.62, a * 0.6 + b * 0.5));
    em = vec3(0.0);
    return col;
}



// ================================================================== collection « légendes COD » (inspirations)


// Orion (MW 2022) : violet profond lisse, voiles irisés (rose, violet, bleu) qui glissent, paillettes fines
vec3 orion(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(6.0, 10.0, 10.0);
    vec3 w = vec3(fbm(q * 0.8 + vec3(0.0, 0.0, t * 0.12), 3), fbm(q * 0.8 + vec3(4.2, 1.1, -t * 0.1), 3), 0.0);
    float f = fbm(q + 2.2 * w, 5);
    float band = fract(f * 3.0 + 0.15 * viewNormal.x + t * 0.03);             // voiles irisés qui changent avec l'angle
    vec3 iris = spectrum(0.66 + 0.2 * sin(band * 6.2831));                    // entre bleu, violet et rose
    float veil = smoothstep(0.45, 0.75, f);
    vec3 col = mix(hexc(0x0a0318), hexc(0x3a1170), smoothstep(0.25, 0.7, f));
    col = mix(col, iris * 0.85, veil * 0.55);
    col += 0.22 * pow(clamp(viewNormal.y * 0.5 + 0.5, 0.0, 1.0), 5.0) * hexc(0xe6d0ff);   // vernis brillant
    vec3 sq = floor(p * 900.0);
    float sp = step(0.994, hash(sq)) * pow(max(0.0, sin(t * 3.0 + hash(sq + 5.0) * 40.0)), 6.0);
    em = iris * veil * 0.18 + sp * vec3(1.3) * (0.4 + veil);
    return col;
}

// petits symboles (runes) dans une cellule locale [-0.5, 0.5]
float glyph(vec2 l, float h) {
    int bits = int(h * 32.0);
    float d = 1.0;
    if ((bits & 1) != 0) d = min(d, abs(l.x) + step(0.34, abs(l.y)));
    if ((bits & 2) != 0) d = min(d, abs(l.y - 0.12) + step(0.3, abs(l.x)));
    if ((bits & 4) != 0) d = min(d, abs(length(l) - 0.27));
    if ((bits & 8) != 0) d = min(d, abs(abs(l.x) * 0.9 - l.y - 0.05) + step(0.32, abs(l.x)));
    if ((bits & 16) != 0) d = min(d, abs(l.x - l.y) * 0.707 + step(0.3, length(l)));
    return smoothstep(0.05, 0.02, d);
}


// Bioluminescence (MW3) : abysses sombres, cellules et veines qui pulsent en cyan / turquoise
vec3 biolum(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(9.0, 16.0, 16.0);
    vec3 base = mix(hexc(0x01080c), hexc(0x06222a), fbm(q * 1.2, 4));
    float f1, f2; vec3 cell, fp;
    worley(q * 1.8 + vec3(0.0, 0.0, t * 0.05), f1, f2, cell, fp);
    float h = hash(cell);
    float spot = smoothstep(0.32, 0.12, f1) * step(0.45, h);
    float ring = smoothstep(0.05, 0.0, abs(f1 - 0.3)) * step(0.45, h);
    float pulse = 0.5 + 0.5 * sin(t * (1.0 + h) + h * 30.0);
    float vein = pow(1.0 - abs(2.0 * fbm(q * 0.9 + vec3(t * 0.06, -t * 0.04, 0.0), 4) - 1.0), 12.0);
    vec3 gc = mix(hexc(0x00fff0), hexc(0x3b7bff), 0.5 + 0.5 * sin(t * 0.25 + h * 6.0));
    gc = mix(gc, hexc(0xb65cff), step(0.9, h));
    vec3 col = base + gc * (spot * 0.35 + ring * 0.2 + vein * 0.2) * pulse;
    em = gc * (spot * pulse * 0.9 + ring * 0.5 + vein * 0.6);
    return col;
}



// Nexus Horizon (BO7) : nébuleuse violette et bleu électrique, traînées d'étoiles, cœur noir lumineux
vec3 nexusHorizon(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * 8.0;
    vec3 w = vec3(fbm(q * 0.7 + vec3(0.0, 0.0, t * 0.25), 3), fbm(q * 0.7 + vec3(4.0, 2.0, -t * 0.2), 3), 0.0);
    float neb = fbm(q + 2.4 * w, 5);
    float s[6] = float[6](0.0, 0.2, 0.42, 0.62, 0.82, 1.0);
    vec3 c[6] = vec3[6](hexc(0x02010a), hexc(0x12073a), hexc(0x3a0f8a), hexc(0x6d2bd6), hexc(0x2d8cff), hexc(0x9fe8ff));
    vec3 col = ramp6((neb - 0.2) * 2.3, s, c);
    // traînées d'étoiles qui filent le long de l'arme
    vec2 sd = vec2(p.x * 6.0 - t * 0.6, p.y * 90.0);
    float lane = floor(sd.y);
    float hl = hash(vec3(lane, 4.0, 0.0));
    float trail = step(0.94, hl) * smoothstep(0.0, 0.6, fract(sd.x + hl * 7.0)) * smoothstep(1.0, 0.96, fract(sd.x + hl * 7.0))
                * smoothstep(0.5, 0.2, abs(fract(sd.y) - 0.5));
    // cœur noir à halo pulsant
    vec2 d = (p.xy - vec2(0.05, 0.0)) * vec2(1.0, 2.4);
    float rad = length(d);
    float halo = exp(-pow((rad - 0.05) / 0.02, 2.0)) * (0.7 + 0.3 * sin(t * 2.0));
    col = mix(col, vec3(0.0), smoothstep(0.042, 0.03, rad));
    em = trail * mix(hexc(0x9fe8ff), hexc(0xd08cff), hl) * 1.3 + halo * hexc(0x8fd0ff) * 1.2
       + pow(1.0 - abs(2.0 * neb - 1.0), 10.0) * hexc(0x7a5cff) * 0.5;
    return col;
}


// Dark Matter Nucléaire (version orange de BO4) : fumée orange et rouge, taches noires et picots brûlants qui dérivent
vec3 darkMatterNuclear(vec3 p, out vec3 em) {
    float t = CamoTime * 1.5;
    vec3 q = p * 9.0;
    vec3 w = vec3(fbm(q * 0.7 + vec3(0.0, 0.0, t * 0.2), 3), fbm(q * 0.7 + vec3(5.2, 1.3, -t * 0.18), 3), fbm(q * 0.7 + vec3(9.7, 4.1, t * 0.15), 3));
    float smoke = fbm(q + 2.2 * w, 5);
    float s[6] = float[6](0.0, 0.22, 0.42, 0.6, 0.8, 1.0);
    vec3 c[6] = vec3[6](hexc(0x0a0200), hexc(0x3a0800), hexc(0x8a1a00), hexc(0xe04a00), hexc(0xff9a1a), hexc(0xffe7a0));
    vec3 col = ramp6((smoke - 0.40) * 2.4, s, c);
    float f1, f2; vec3 cell, fp;
    worley(q * 3.0 + vec3(t * 0.15, -t * 0.08, 0.0), f1, f2, cell, fp);
    float h = hash(cell);
    float dotm = smoothstep(0.22, 0.12, f1);
    col = mix(col, vec3(0.0), dotm * step(0.55, h));                                        // taches noires
    float spike = dotm * step(h, 0.18) * (0.6 + 0.4 * sin(t * 3.0 + h * 50.0));              // picots brûlants
    col = mix(col, hexc(0xfff0b0), spike);
    em = pow(1.0 - abs(2.0 * fbm(q * 1.7 - 1.3 * w, 4) - 1.0), 9.0) * hexc(0xff7a1a) * 0.8 + spike * hexc(0xffb040) * 1.5
       + col * 0.06;
    return col;
}


// ================================================================== finitions de couteaux façon CS:GO (ids 50 à 68)
// Repère : x = le long de la lame (0.5 = pointe), y = largeur. PART = rôle de la pièce :
// 1 lame, 0.75 manche caoutchouc, 0.5 pièce métal (garde, pommeau, anneau, goupille), 0.25 manche métal sombre (Butterfly).
// Rendu « métal » : la lame reflète un décor de studio qui dépend de l'orientation, avec une légère courbure simulée
// le long et en travers de la lame -> les reflets glissent quand le couteau bouge, comme dans CS.
float PART = 1.0;
// couleur de la texture d'origine au point dessiné (manches ornés : leurs motifs restent visibles sous la finition)
vec3 BASE_COL = vec3(0.0);

vec3 studio(vec3 n) {
    vec3 r = reflect(vec3(0.0, 0.0, -1.0), n);
    float y = r.y;
    vec3 sky = mix(vec3(0.42, 0.47, 0.56), vec3(0.97, 0.98, 1.0), smoothstep(0.15, 0.85, y));
    vec3 col = mix(vec3(0.04, 0.04, 0.05), sky, smoothstep(-0.2, 0.08, y));
    col += 1.1 * exp(-pow((y - 0.04) / 0.035, 2.0));                              // ligne d'horizon
    col += 1.2 * exp(-pow((r.x - 0.45) / 0.12, 2.0)) * smoothstep(0.1, 0.5, y);   // panneau lumineux
    col += 0.7 * exp(-pow((r.x + 0.6) / 0.08, 2.0)) * smoothstep(-0.1, 0.4, y);
    return col;
}

vec3 bladeNormal(vec3 p) {
    // courbure fictive : biseau en travers de la lame, légère cambrure en longueur
    return normalize(viewNormal + vec3(p.x * 1.4, p.y * 6.5, 0.0));
}

// base = couleur de la finition ; tint = reflets teintés (anodisé / candy) ou blancs (acier) ; gloss = brillance
vec3 metalShade(vec3 p, vec3 base, float tint, float gloss) {
    vec3 n = bladeNormal(p);
    vec3 env = studio(n);
    float fres = pow(1.0 - clamp(abs(n.z), 0.0, 1.0), 2.0);
    vec3 spec = env * mix(vec3(1.0), clamp(base * 1.7, 0.0, 1.5), tint);
    vec3 col = base * (0.42 + 0.22 * env.g) + spec * gloss * (0.30 + 0.45 * fres);
    // paillettes du vernis : scintillent selon l'angle et le temps
    vec3 cell = floor(p * 520.0);
    vec3 fn = normalize(hash3(cell) - 0.5);
    float flake = step(0.985, hash(cell)) * pow(max(0.0, dot(fn, n)), 18.0) * (0.6 + 0.4 * sin(CamoTime * 3.0 + hash(cell + 7.0) * 30.0));
    return col + flake * gloss * 0.9 * mix(vec3(1.0), base * 1.5, tint * 0.5);
}

vec3 rubberGrip(vec3 p) {
    vec3 n = faceNormal(p);
    vec2 f = fract(surf2d(p, n) * 150.0) - 0.5;
    float bump = smoothstep(0.34, 0.2, length(f));                                  // grain pointillé
    vec3 col = mix(hexc(0x0a0a0b), hexc(0x202125), bump);
    return col + 0.07 * pow(clamp(viewNormal.y * 0.5 + 0.5, 0.0, 1.0), 4.0);
}

// motif Doppler : volutes marbrées qui coulent lentement, stries sombres, couche profonde en parallaxe
vec3 dopplerBase(vec3 p, vec3 c0, vec3 c1, vec3 c2, vec3 c3, vec3 accent, float accentAmt) {
    float t = CamoTime;
    vec3 q = p * vec3(4.0, 8.0, 8.0);
    vec3 w = vec3(fbm(q + vec3(1.0, 0.0, t * 0.035), 3), fbm(q + vec3(5.2, 1.3, 2.8 - t * 0.03), 3), fbm(q + vec3(9.1, 4.4, 0.7 + t * 0.025), 3));
    float f = fbm(q + 1.8 * w, 5);
    // couche profonde vue « à travers » le vernis, décalée selon l'angle -> profondeur
    float fd = fbm(q * 1.3 + 1.5 * w + vec3(viewNormal.xy * 0.35, 3.0), 4);
    float tt = clamp((f - 0.22) * 2.1 + 0.25 * (fd - 0.5), 0.0, 1.0);
    vec3 col = tt < 0.33 ? mix(c0, c1, tt / 0.33) : tt < 0.66 ? mix(c1, c2, (tt - 0.33) / 0.33) : mix(c2, c3, (tt - 0.66) / 0.34);
    col = mix(col, accent, smoothstep(0.62, 0.8, fbm(q * 1.6 + 2.0 * w + vec3(3.0), 4)) * accentAmt);
    float ridge = 1.0 - abs(2.0 * fbm(q * 1.7 + w, 4) - 1.0);
    col *= 1.0 - 0.55 * smoothstep(0.9, 0.985, ridge);                               // stries sombres
    return col;
}

// ================================================================== refonte 2026-09-27 : camos retravaillés + nouveaux
// vernis : reflet doux du décor de studio selon l'orientation (donne du « verni » aux motifs plats)
vec3 lacquer(vec3 col, float amt) {
    return col + studio(normalize(viewNormal)) * amt * 0.16;
}

// Carbone : sergé 2/2 bien lisible, mèches de fibres, reflet anisotrope qui change selon la mèche, vernis épais
vec3 carbon(vec3 p, out vec3 em) {
    vec3 n = faceNormal(p);
    vec2 uv = surf2d(p, n) * 42.0;
    vec2 cell = floor(uv);
    float dir = step(2.0, mod(cell.x + cell.y, 4.0));
    vec2 f = fract(uv);
    float across = dir > 0.5 ? f.y : f.x;
    float strands = 0.6 + 0.4 * sin(across * TAU * 4.0 + 2.0 * vnoise(vec3(uv * 3.0, 1.0)));
    float tow = smoothstep(0.0, 0.14, across) * smoothstep(1.0, 0.86, across);
    float sheen = pow(clamp(dir > 0.5 ? 0.55 + 0.45 * viewNormal.x : 0.55 + 0.45 * viewNormal.y, 0.0, 1.0), 2.0);
    vec3 col = mix(hexc(0x16191e), hexc(0x9ca5b3), (0.25 + 0.75 * sheen) * strands * tow);
    em = vec3(0.0);
    return lacquer(col * vec3(0.95, 0.98, 1.06), 1.0);
}

// Tigre : fourrure orange dégradée, rayures noires peintes au pinceau (pointes effilées, fourches)
vec3 tiger(vec3 p, out vec3 em) {
    vec3 q = p * vec3(6.0, 12.0, 12.0);
    float warp = fbm(q + vec3(2.0), 4);
    float coord = p.x * 24.0 + p.y * 9.0 + 3.2 * warp + 1.2 * fbm(q * 2.3, 3);
    float band = abs(fract(coord) - 0.5) * 2.0;
    float along = fbm(vec3(floor(coord) * 7.1, (p.y + p.z) * 14.0, 3.0), 3);
    float width = 0.18 + 0.6 * smoothstep(0.3, 0.72, along);
    float brush = 0.1 * (vnoise(vec3(p.x * 120.0, p.y * 420.0, p.z * 420.0)) - 0.5);
    float stripe = (1.0 - smoothstep(width - 0.06, width + 0.02, band + brush)) * step(0.2, hash(vec3(floor(coord), 3.0, 1.0)));
    float tone = 0.62 + 0.5 * (warp - 0.5) + 0.2 * viewNormal.y;
    vec3 fur = mix(mix(hexc(0x8a3606), hexc(0xf07a14), smoothstep(0.1, 0.5, tone)), hexc(0xffb84e), smoothstep(0.55, 0.95, tone));
    fur *= 0.88 + 0.12 * vnoise(vec3(p.x * 40.0, p.y * 500.0, p.z * 500.0));
    em = vec3(0.0);
    return lacquer(mix(fur, hexc(0x090605), stripe), 0.6);
}

// Plasma : coque bleu nuit qui respire, arcs électriques nombreux et lumineux, trame hexagonale
vec3 plasma(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(5.0, 10.0, 10.0);
    vec3 n = faceNormal(p);
    vec2 hq = surf2d(p, n) * 55.0;
    vec2 hv = abs(fract(vec2(hq.x + 0.5 * floor(hq.y), hq.y)) - 0.5);
    float hexLine = smoothstep(0.44, 0.5, max(hv.x, hv.y));
    vec3 col = mix(hexc(0x0a1024), hexc(0x26336a), fbm(q * 1.5 + vec3(0.0, 0.0, t * 0.15), 4)) + hexLine * hexc(0x2a4a8a) * 0.5;
    em = hexLine * hexc(0x3fd0ff) * 0.18 * (0.5 + 0.5 * sin(p.x * 30.0 - t * 2.0));
    for (int i = 0; i < 4; i++) {
        float fi = float(i);
        float nn = fbm(q * vec3(1.6, 2.6, 2.6) + vec3(t * (0.5 + 0.25 * fi), fi * 7.3, -t * 0.22 + fi * 3.1), 4);
        float ridge = 1.0 - abs(2.0 * nn - 1.0);
        float bolt = pow(ridge, 40.0);
        float halo = pow(ridge, 14.0);
        float flick = 0.55 + 0.45 * step(0.3, hash(vec3(floor(t * 9.0), fi, 1.0)));
        vec3 bc = i == 0 ? hexc(0x5cf0ff) : i == 1 ? hexc(0xb45cff) : i == 2 ? hexc(0x4f8bff) : hexc(0xff5cf0);
        em += (bolt * 2.0 + halo * 0.15) * flick * bc;
        col += halo * bc * 0.08;
    }
    return col;
}

// Solaire : granulation contrastée (bords sombres), filaments de plasma, taches solaires avec pénombre
vec3 solar(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(7.0, 12.0, 12.0);
    vec3 w = vec3(fbm(q * 0.6 + vec3(0.0, 0.0, t * 0.08), 3), fbm(q * 0.6 + vec3(4.0, 1.0, -t * 0.07), 3), 0.0);
    float f1, f2; vec3 cell, fp;
    worley(q * 3.0 + 1.2 * w + vec3(0.0, 0.0, t * 0.12), f1, f2, cell, fp);
    float gran = 0.35 + 0.65 * smoothstep(0.0, 0.1, f2 - f1);
    float heat = fbm(q * 1.2 + 2.0 * w + vec3(-t * 0.15, 0.0, 0.0), 5);
    float fl = pow(1.0 - abs(2.0 * fbm(q * 1.4 + 2.5 * w + vec3(t * 0.3, 0.0, 0.0), 4) - 1.0), 12.0);
    float v = 0.1 + 0.55 * gran * (0.5 + 0.9 * heat) + 0.4 * fl;
    float s[6] = float[6](0.0, 0.2, 0.4, 0.62, 0.82, 1.0);
    vec3 c[6] = vec3[6](hexc(0x1a0200), hexc(0x7a1000), hexc(0xe83a00), hexc(0xff8a10), hexc(0xffd048), hexc(0xfff6d8));
    vec3 col = ramp6(v, s, c);
    float sp = fbm(q * 0.5 + vec3(11.0, 0.0, t * 0.03), 3);
    float umbra = smoothstep(0.68, 0.72, sp), pen = smoothstep(0.6, 0.68, sp);
    col = mix(col, col * vec3(0.45, 0.3, 0.2), pen * 0.7);
    col = mix(col, hexc(0x100300), umbra);
    em = (col * 0.45 + fl * hexc(0xffe090) * 0.6) * (1.0 - umbra);
    return col;
}

// Borealis : ciel de nuit étoilé, rideaux d'aurore (bord bas net vert, sommet violet) qui ondulent le long de l'arme
vec3 borealis(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 col = mix(hexc(0x02050f), hexc(0x0d1c3e), smoothstep(-0.12, 0.12, p.y));
    vec3 sc = floor(p * 700.0);
    em = step(0.995, hash(sc)) * (0.5 + 0.5 * sin(t * 2.0 + hash(sc + 1.0) * 30.0)) * vec3(0.9);
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float fold = fbm(vec3(p.x * 3.0 + fi * 4.0 - t * 0.08, t * 0.05, fi + p.z * 3.0), 4);
        float h = p.y + 0.05 * sin(p.x * (9.0 + 2.0 * fi) + t * (0.5 + 0.2 * fi) + fi) + 0.12 * (fold - 0.5);
        float b0 = -0.07 + fi * 0.045;
        float curtain = smoothstep(b0 - 0.012, b0 + 0.012, h) * exp(-max(h - b0, 0.0) * (14.0 - fi * 3.0));
        float rays = 0.4 + 0.6 * pow(0.5 + 0.5 * sin(p.x * 180.0 + fold * 14.0 + t * 1.2), 3.0);
        float a = curtain * rays * smoothstep(0.25, 0.6, fbm(vec3(p.x * 2.0 + fi * 9.0, t * 0.04, 2.0), 3));
        vec3 ac = mix(hexc(0x2bff8a), hexc(0x9a5cff), smoothstep(0.0, 0.09, h - b0));
        col += a * ac * 0.5;
        em += a * ac * 0.9;
    }
    return col;
}

// Afterlife : ardoise sombre, flammes spectrales bleues qui montent le long de l'arme, volutes d'âmes
vec3 afterlife(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(8.0, 14.0, 14.0);
    vec3 base = mix(hexc(0x07090d), hexc(0x1d2433), fbm(q * 2.0, 4));
    vec3 fq = vec3(p.x * 10.0, p.y * 18.0 - t * 0.9, p.z * 10.0);
    float w = fbm(fq * 0.8 + vec3(0.0, 0.0, t * 0.2), 3);
    float fire = fbm(fq + 2.2 * vec3(w, w * 0.5, 0.0), 5);
    float flame = smoothstep(0.48, 0.72, fire + (0.04 - p.y) * 1.2);
    float core = smoothstep(0.66, 0.88, fire + (0.04 - p.y) * 1.2);
    vec3 fc = mix(hexc(0x0a3dff), hexc(0x8ff9ff), core);
    float wisp = pow(1.0 - abs(2.0 * fbm(fq * 1.7 + vec3(3.0), 4) - 1.0), 14.0);
    em = fc * flame * (0.7 + 0.3 * sin(t * 2.0 + fire * 10.0)) + wisp * hexc(0x6fe0ff) * 0.7;
    return mix(base, fc * 0.6, flame) + wisp * hexc(0x9ff4ff) * 0.2;
}

// Singularité : nébuleuse aspirée en spirale sur toute l'arme, trou noir, disque d'accrétion, anneau de photons
vec3 singularity(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec2 d = (p.xy - vec2(-0.05, 0.0)) * vec2(1.0, 2.2);
    float rad = length(d);
    float ang = atan(d.y, d.x);
    float sw = ang + 0.8 / (rad + 0.08) - t * 0.25;
    vec3 sq = vec3(cos(sw) * rad * 9.0, sin(sw) * rad * 9.0, p.z * 9.0 + t * 0.05);
    float neb = fbm(sq + vec3(3.0), 5);
    float s[6] = float[6](0.0, 0.2, 0.42, 0.62, 0.82, 1.0);
    vec3 c[6] = vec3[6](hexc(0x020109), hexc(0x160a3a), hexc(0x40158a), hexc(0x8a2ac0), hexc(0xff7a3a), hexc(0xffe2b0));
    vec3 col = ramp6((neb - 0.28) * 2.1 + 0.35 * exp(-rad * 7.0), s, c);
    float disk = exp(-pow((rad - 0.115) / 0.045, 2.0)) * (0.55 + 0.45 * sin(ang * 3.0 - t * 3.0 + neb * 6.0));
    float lens = exp(-pow((rad - 0.072) / 0.007, 2.0));
    vec3 hot = mix(hexc(0xff6a10), hexc(0xfff4d6), smoothstep(0.15, 0.08, rad));
    float core = smoothstep(0.068, 0.058, rad);
    col = mix(col + disk * hot * 0.45, vec3(0.0), core);
    vec2 sd = vec2(sw * 4.0, rad * 60.0 + t * 2.0);
    float st = step(0.985, hash(vec3(floor(sd), 2.0))) * smoothstep(0.5, 0.1, abs(fract(sd.y) - 0.5));
    em = (disk * hot * 0.5 + lens * vec3(1.0, 0.92, 0.8) * 1.8 + st * vec3(0.9) + pow(1.0 - abs(2.0 * neb - 1.0), 12.0) * hexc(0x9a5cff) * 0.4) * (1.0 - core);
    return col;
}

// ------------------------------------------------------------------ Black Ice (épique) : glace blanc-bleu très claire, plaques de
// givre en relief, voile laiteux, cristaux qui scintillent (esprit Black Ops 7)
vec3 blackIceEpic(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 n = faceNormal(p);
    vec2 uv = surf2d(p, n);
    vec3 q = p * vec3(9.0, 16.0, 16.0);
    float cloud = fbm(q * 0.8 + vec3(2.0), 5);
    vec3 col = mix(hexc(0x8fc3d8), hexc(0xe8f5f9), smoothstep(0.3, 0.7, cloud));      // givre blanc laiteux
    float pool = fbm(q * 0.45 + vec3(7.0), 4);
    col = mix(col, hexc(0x5fa4c0), smoothstep(0.58, 0.72, pool) * 0.6);                // plaques de glace bleu clair
    // plaques de givre : cellules de Worley (croûte cristalline), plus blanches, bords légèrement bleus
    float f1, f2; vec3 cell, fp;
    worley(vec3(uv * 16.0, 1.0), f1, f2, cell, fp);
    float crust = smoothstep(0.55, 0.3, f1) * step(0.35, hash(cell));
    col = mix(col, mix(hexc(0xeef9fc), vec3(1.0), hash(cell + 1.0)), crust * 0.35);
    col = mix(col, hexc(0x78b4ca), (1.0 - smoothstep(0.0, 0.03, f2 - f1)) * 0.3);
    // relief : ombrage des plaques selon la lumière (normale perturbée)
    float bump = (f2 - f1) * 1.6 - 0.4;
    col *= 0.97 + 0.08 * clamp(bump + 0.3 * n.y, -0.5, 1.0);
    // givre fin en poudre + fougères discrètes
    float powder = fbm(vec3(uv * 120.0, 3.0), 3);
    col = mix(col, vec3(1.0), smoothstep(0.55, 0.75, powder) * 0.55);
    float fern = smoothstep(0.9, 0.98, 1.0 - abs(2.0 * fbm(vec3(uv * 22.0, 9.0), 5) - 1.0));
    col = mix(col, hexc(0xf4fcff), fern * 0.5);
    col += 0.06 * pow(clamp(viewNormal.y * 0.5 + 0.5, 0.0, 1.0), 4.0);
    vec3 sq = floor(q * 55.0);
    float sparkle = step(0.97, hash(sq)) * pow(max(0.0, sin(t * 2.6 + hash(sq + 3.0) * 40.0)), 12.0);
    em = sparkle * vec3(1.2) + col * 0.08;
    return col;
}

// ------------------------------------------------------------------ Diamant (épique) : éclats triangulaires argentés façon miroir,
// chaque facette reflète le studio sous son propre angle, arêtes nettes, léger feu arc-en-ciel qui bouge avec la vue
vec3 spectral(float h) {
    return clamp(abs(fract(h + vec3(0.0, 0.6667, 0.3333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

vec3 diamondShards(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 n = faceNormal(p);
    vec2 uv = surf2d(p, n) * 40.0;
    // grille triangulaire irrégulière : on biaise la grille puis on coupe chaque carré en deux triangles
    vec2 sk = vec2(uv.x + uv.y * 0.57, uv.y);
    vec2 id = floor(sk);
    vec2 f = fract(sk);
    vec2 jit = (hash3(vec3(id, 5.0)).xy - 0.5) * 0.35;
    float side = step(f.y + jit.y, f.x + jit.x);
    vec3 tid = vec3(id, side);
    vec3 h = hash3(tid * 1.37 + 2.0);
    vec3 fn = normalize(normalize(viewNormal) + 1.4 * (h - 0.5));
    vec3 env = studio(fn);
    float lum = clamp(env.g * 0.95 + 0.4 * (h.z - 0.5) + 0.15, 0.0, 1.6);
    vec3 col = mix(hexc(0x3a3f48), hexc(0xf4f7fb), smoothstep(0.0, 0.85, lum));
    col = mix(col, vec3(1.0), smoothstep(0.85, 1.2, lum));
    // feu (dispersion) : un peu de couleur spectrale sur les facettes très claires, selon l'angle de vue
    vec3 fire = spectral(h.x + dot(viewNormal, vec3(0.7, 0.4, 0.2)) * 1.5 + t * 0.03);
    col = mix(col, col * (0.7 + 0.5 * fire), smoothstep(0.75, 1.1, lum) * 0.25);
    // arêtes des éclats : fines lignes sombres et un liseré clair
    float e1 = min(f.x, 1.0 - f.y);
    float e2 = abs((f.y + jit.y) - (f.x + jit.x));
    float edge = 1.0 - smoothstep(0.0, 0.035, min(min(e1, 1.0 - f.x), min(f.y, e2)));
    col = mix(col, hexc(0x4a505a), edge * 0.45);
    em = smoothstep(1.05, 1.3, lum) * vec3(0.35) + col * 0.05;
    return col;
}

// Diamant (façon Minecraft) : gemme turquoise taillée — grandes facettes aux reflets francs (sombre -> cyan -> blanc),
// arêtes qui accrochent la lumière, lueur propre et reflet qui balaie lentement l'arme ; aucun scintillement aléatoire
vec3 diamond(vec3 p, out vec3 em) {
    float t = CamoTime;
    float f1, f2; vec3 cell, fp;
    worley(p * vec3(5.0, 8.0, 8.0), f1, f2, cell, fp);
    vec3 hc = hash3(cell);
    vec3 fn = normalize(normalize(viewNormal) + 1.15 * (hc - 0.5));
    vec3 env = studio(fn);
    float lum = clamp(env.g * 0.62, 0.0, 1.4);
    float s[6] = float[6](0.0, 0.2, 0.42, 0.65, 0.85, 1.0);
    vec3 c[6] = vec3[6](hexc(0x063b44), hexc(0x0e7c86), hexc(0x1fc9c4), hexc(0x55f0e2), hexc(0xb4fff6), hexc(0xffffff));
    vec3 col = ramp6(lum * 0.95 + 0.25 * (hc.z - 0.5), s, c);
    float edge = 1.0 - smoothstep(0.0, 0.022, f2 - f1);
    col = mix(col, hexc(0xe8fffc), edge * 0.35);
    float u = p.x + 0.5;
    float sweep = exp(-pow((u - (fract(t * 0.08) * 1.6 - 0.3) - p.y * 1.2) / 0.07, 2.0));
    em = col * 0.14 + sweep * hexc(0xd8fffa) * 0.55 + edge * hexc(0x7ff5ea) * 0.1;
    return col;
}

// Dark Aether : fumée violette qui tourbillonne, failles rouges incandescentes, braises
vec3 darkAether(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(7.0, 12.0, 12.0);
    vec3 w = vec3(fbm(q * 0.8 + vec3(0.0, 0.0, t * 0.3), 3), fbm(q * 0.8 + vec3(3.0, 7.0, -t * 0.25), 3), 0.0);
    float smoke = fbm(q + 2.6 * w + vec3(0.0, -t * 0.1, 0.0), 5);
    float s[6] = float[6](0.0, 0.22, 0.42, 0.62, 0.82, 1.0);
    vec3 c[6] = vec3[6](hexc(0x020006), hexc(0x12041f), hexc(0x40106a), hexc(0x8a2fd0), hexc(0xd07cff), hexc(0xf6e0ff));
    vec3 col = ramp6((smoke - 0.26) * 2.5, s, c);
    float rift = pow(1.0 - abs(2.0 * fbm(q * 1.3 + 1.5 * w + vec3(0.0, 0.0, t * 0.2), 4) - 1.0), 18.0);
    col = mix(col, hexc(0xff3a1a), rift * 0.75);
    float f1, f2; vec3 cell, fp;
    worley(q * 2.0 + w, f1, f2, cell, fp);
    float ember = smoothstep(0.18, 0.05, f1) * step(0.8, hash(cell)) * pow(max(0.0, sin(t * 1.5 + hash(cell + 2.0) * 30.0)), 4.0);
    col = mix(col, hexc(0xff5a2a), ember);
    em = pow(1.0 - abs(2.0 * smoke - 1.0), 8.0) * hexc(0xa05cff) * 0.7 + rift * hexc(0xff4a1a) * 1.4 + ember * hexc(0xff3a1a) * 1.5;
    return col;
}

// Infestation : métal souillé, veines organiques qui rampent, pustules qui pulsent, teinte qui change lentement
vec3 infestation(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(8.0, 14.0, 14.0);
    vec3 infect = mix(spectrum(fract(t * 0.08 + 0.25)), spectrum(fract(t * 0.08 + 0.55)), 0.5 + 0.5 * sin(t * 0.9 + fbm(q * 0.4, 2) * 6.0));
    vec3 base = mix(hexc(0x1a1511), hexc(0x4a3a2e), fbm(q * 2.5, 4));
    float vein = pow(1.0 - abs(2.0 * fbm(q * 1.1 + vec3(-t * 0.08, t * 0.05, 0.0), 5) - 1.0), 7.0);
    float vein2 = pow(1.0 - abs(2.0 * fbm(q * 2.6 + vec3(t * 0.1, 0.0, -t * 0.06), 4) - 1.0), 12.0);
    float spread = smoothstep(0.3, 0.6, fbm(q * 0.5 + vec3(0.0, 0.0, t * 0.04), 3));
    float f1, f2; vec3 cell, fp;
    worley(q * 2.4, f1, f2, cell, fp);
    float h = hash(cell);
    float pust = smoothstep(0.3, 0.1, f1) * step(0.65, h) * spread;
    float beat = 0.6 + 0.4 * sin(t * 2.2 + h * 20.0);
    vec3 col = mix(base, infect * 0.4, spread * 0.55) + (vein + vein2 * 0.6) * spread * infect * 0.55 + pust * infect * beat * 0.6;
    em = (vein * 0.7 + vein2 * 0.4 + pust * beat * 1.1) * spread * infect;
    return col;
}

// ------------------------------------------------------------------ nouveaux camos
// Damascus : acier feuilleté, strates ondulées claires et sombres, poli miroir
vec3 damascus(vec3 p, out vec3 em) {
    vec3 q = p * vec3(6.0, 11.0, 11.0);
    float w = fbm(q, 4);
    float layer = sin(p.y * 190.0 + p.z * 190.0 + p.x * 40.0 + 14.0 * w + 4.0 * sin(p.x * 45.0 + w * 5.0));
    float l2 = sin(p.x * 260.0 + 9.0 * fbm(q * 2.0 + vec3(4.0), 3));
    float v = mix(0.5 + 0.5 * layer, 0.5 + 0.5 * l2, 0.25);
    em = vec3(0.0);
    return metalShade(p, mix(hexc(0x121417), hexc(0xd8dde4), smoothstep(0.35, 0.65, v)), 0.1, 0.9);
}

// Atomique : vernis nacré irisé rouge / magenta / orange qui change avec l'angle, paillettes métal
vec3 atomic(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(5.0, 9.0, 9.0);
    float f = fbm(q + vec3(0.0, 0.0, t * 0.05), 4);
    vec3 fn = faceNormal(p);
    float hue = 0.93 + 0.5 * (f - 0.5) + 0.2 * dot(viewNormal, vec3(0.7, 0.7, 0.2)) + 0.08 * dot(fn, vec3(0.5, 0.8, 0.3)) + 0.05 * sin(t * 0.4);
    vec3 base = mix(hexc(0x220008), spectrum(fract(hue)), 0.85) * (0.8 + 0.4 * fbm(q * 3.0, 3));
    em = spectrum(fract(hue)) * 0.06;
    return metalShade(p, base, 0.95, 0.9);
}

// Obsidienne : verre volcanique noir miroir, parcouru de fines lignes irisées (or, vert, violet) façon film d'huile
// qui ondulent lentement et changent de teinte avec l'angle
vec3 obsidian(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(4.0, 9.0, 9.0);
    float w = fbm(q + vec3(0.0, 0.0, t * 0.03), 4);
    float flow = sin(p.x * 38.0 + p.y * 60.0 + p.z * 40.0 + 9.0 * w - t * 0.25);
    float line = smoothstep(0.86, 0.995, flow);
    vec3 n = normalize(viewNormal);
    vec3 env = studio(n);
    float fres = pow(1.0 - clamp(abs(n.z), 0.0, 1.0), 2.0);
    vec3 irid = spectrum(fract(0.1 + 0.6 * w + 0.3 * dot(n, vec3(0.8, 0.6, 0.0)) + p.x * 0.8));
    vec3 col = hexc(0x07060b) + hexc(0x141020) * w;
    col += line * mix(irid, vec3(1.0), 0.3) * (0.32 + 0.2 * fres);
    col += env * (0.16 + 0.36 * fres) * vec3(0.86, 0.86, 0.95);
    em = line * irid * 0.08;
    return col;
}

// Chrome liquide : miroir qui ondule et coule, reflets irisés sur les crêtes
vec3 liquidChrome(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(5.0, 9.0, 9.0);
    vec3 w = vec3(fbm(q + vec3(0.0, 0.0, t * 0.12), 3), fbm(q + vec3(3.1, 5.2, -t * 0.1), 3), fbm(q + vec3(7.7, 1.9, t * 0.09), 3));
    vec3 n = normalize(viewNormal + 1.1 * (w - 0.5));
    vec3 env = studio(n);
    vec3 col = env * vec3(0.9, 0.94, 1.0) * 0.8 + hexc(0x15181d);
    float cr = pow(1.0 - abs(2.0 * fbm(q * 1.5 + 2.0 * w, 4) - 1.0), 10.0);
    col += cr * spectrum(fract(w.x * 2.0 + t * 0.05)) * 0.2;
    em = env * 0.06;
    return col;
}

// Glitch : blocs de données qui s'allument, décalages de lignes, balayage cyan, séparation RVB
vec3 glitch(vec3 p, out vec3 em) {
    float t = CamoTime;
    float row = floor((p.y + p.z) * 60.0);
    float shift = (hash(vec3(row, floor(t * 12.0), 1.0)) - 0.5) * 0.06 * step(0.8, hash(vec3(floor(t * 3.0), floor(row * 0.1), 2.0)));
    vec3 pp = p + vec3(shift, 0.0, 0.0);
    vec2 blk = floor(vec2(pp.x * 40.0, (pp.y + pp.z) * 25.0));
    float hb = hash(vec3(blk, floor(t * 2.0)));
    float lit = step(0.72, hb);
    vec3 lc = mix(hexc(0x00f0ff), hexc(0xff2bd6), step(0.5, hash(vec3(blk, 9.0))));
    vec3 col = mix(hexc(0x1c2442), hexc(0x3e4a78), hash(vec3(blk, 5.0))) + lit * lc * 0.45;
    col *= 0.85 + 0.15 * (0.5 + 0.5 * sin((p.y + p.z) * 900.0 + t * 20.0));
    float edgeX = 1.0 - smoothstep(0.0, 0.06, 0.5 - abs(fract(pp.x * 40.0) - 0.5));
    col += edgeX * vec3(1.0, 0.0, 0.35) * 0.2 * lit;
    float band = exp(-pow((fract(t * 0.25) * 1.4 - 0.2 - (p.x + 0.5)) / 0.02, 2.0));
    em = lit * lc * 0.8 + band * hexc(0x00f0ff) * 1.2;
    return col;
}

// Souffle du dragon : laque cramoisi sombre aux reflets or ; vrilles de feu nettes qui filent le long de l'arme vers le canon
vec3 dragon(vec3 p, out vec3 em) {
    float t = CamoTime;
    float u = p.x + 0.5;
    vec3 q = p * vec3(6.0, 11.0, 11.0);
    vec3 n = normalize(viewNormal);
    float fres = pow(1.0 - clamp(abs(n.z), 0.0, 1.0), 2.0);
    vec3 base = mix(hexc(0x140103), hexc(0x4a070c), fbm(q * 0.8 + vec3(3.0), 4));
    base += fres * hexc(0xc08030) * 0.25 + studio(n) * 0.06 * hexc(0xffb070);
    vec3 fq = vec3(p.x * 6.0 - t * 1.2, p.y * 12.0, p.z * 12.0);
    vec3 wv = vec3(fbm(fq * 0.6 + vec3(0.0, t * 0.25, 0.0), 3), fbm(fq * 0.6 + vec3(5.0, -t * 0.2, 2.0), 3), 0.0);
    float fire = fbm(fq + 2.2 * wv, 5);
    float tongue = pow(1.0 - abs(2.0 * fire - 1.0), 4.0) * smoothstep(0.15, 0.5, fbm(fq * 0.5 + vec3(3.0), 3) + (u - 0.5) * 0.2);
    float flame = clamp(tongue * 1.5, 0.0, 1.0);
    vec3 fc = mix(hexc(0xa01200), hexc(0xff8a18), smoothstep(0.2, 0.7, tongue));
    fc = mix(fc, hexc(0xfff0b0), smoothstep(0.75, 1.0, tongue));
    vec3 col = mix(base, fc, flame);
    em = fc * flame * (0.8 + 0.2 * sin(t * 3.0 + fire * 9.0));
    return col;
}

// Circuit néon : carte électronique sombre, pistes et pastilles, impulsions qui courent
vec3 circuit(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 n = faceNormal(p);
    vec2 uv = surf2d(p, n) * 38.0;
    vec2 c = floor(uv), f = fract(uv);
    float h = hash(vec3(c, 1.0));
    float hor = step(0.5, h);
    float line = (hor > 0.5 ? smoothstep(0.09, 0.04, abs(f.y - 0.5)) : smoothstep(0.09, 0.04, abs(f.x - 0.5))) * step(0.3, hash(vec3(c, 2.0)));
    float pad = smoothstep(0.22, 0.14, length(f - 0.5)) * step(0.82, hash(vec3(c, 3.0)));
    float pulse = pow(0.5 + 0.5 * sin((hor > 0.5 ? uv.x : uv.y) * 0.8 - t * 4.0 + h * 20.0), 20.0);
    vec3 board = mix(hexc(0x03110b), hexc(0x0b2a1d), fbm(p * 30.0, 3));
    vec3 tr = mix(hexc(0x1d6a4c), hexc(0x2dffb0), pulse);
    vec3 col = mix(board, tr, max(line, pad)) + pad * 0.08;
    em = max(line, pad) * (pulse * hexc(0x2dffc0) * 1.3 + hexc(0x0f5a3a) * 0.2);
    return lacquer(col, 0.4);
}

// Rouille : peinture militaire écaillée sur un métal rongé, coulures de rouille (ambiance post-apo)
vec3 rust(vec3 p, out vec3 em) {
    vec3 q = p * vec3(8.0, 14.0, 14.0);
    float keep = fbm(q * 1.2 + vec3(3.0), 5);
    float paintM = smoothstep(0.44, 0.47, keep);
    float steel = smoothstep(0.41, 0.44, keep) * (1.0 - paintM);
    float s[6] = float[6](0.0, 0.25, 0.45, 0.65, 0.85, 1.0);
    vec3 c[6] = vec3[6](hexc(0x1c0c05), hexc(0x4a1c08), hexc(0x7a3210), hexc(0xa24e1a), hexc(0xc8742e), hexc(0xdc9a5a));
    vec3 rustc = ramp6(fbm(q * 4.0, 4) * 1.2 - 0.1, s, c) * (0.7 + 0.4 * fbm(q * 9.0, 3));
    float drip = smoothstep(0.55, 0.8, fbm(vec3(p.x * 70.0, p.y * 4.0, p.z * 70.0), 3));
    vec3 paint = mix(hexc(0x303626), hexc(0x4f5a3c), fbm(q * 2.0, 3)) * (0.85 + 0.15 * vnoise(p * 300.0));
    paint = mix(paint, hexc(0x5a2a10), drip * 0.55);
    vec3 col = mix(rustc, paint, paintM);
    col = mix(col, hexc(0x8a8c90), steel * 0.8);
    em = vec3(0.0);
    return col;
}

// Améthyste : agate violette en couches ondulées (violet profond, lavande, fines veines de quartz blanc), vernis brillant
vec3 amethyst(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(3.5, 7.0, 7.0);
    vec3 w = vec3(fbm(q + vec3(0.0, 0.0, t * 0.015), 4), fbm(q + vec3(4.1, 2.3, 7.7), 4), 0.0);
    float g = p.y * 24.0 + p.z * 15.0 + 3.6 * w.x + 1.5 * sin(p.x * 6.0 + w.y * 3.0) + p.x * 2.0;
    float b = fract(g);
    float s[6] = float[6](0.0, 0.35, 0.62, 0.8, 0.9, 1.0);
    vec3 c[6] = vec3[6](hexc(0x1a0532), hexc(0x3e1270), hexc(0x6a2cb0), hexc(0x9a62d8), hexc(0xcdb2ff), hexc(0x1a0532));
    vec3 col = ramp6(b, s, c);
    float quartz = smoothstep(0.03, 0.0, abs(b - 0.9)) * step(0.35, hash(vec3(floor(g), 3.0, 1.0)));
    col = mix(col, hexc(0xf6f0ff), quartz * 0.75);
    col *= 0.93 + 0.07 * vnoise(p * 160.0);
    col = lacquer(col, 0.9);
    em = col * 0.05;
    return col;
}

// Tempête : nuages d'orage qui défilent, éclairs qui zèbrent l'arme et illuminent les nuages
vec3 storm(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(6.0, 10.0, 10.0);
    vec3 w = vec3(fbm(q * 0.7 + vec3(-t * 0.1, 0.0, 0.0), 3), fbm(q * 0.7 + vec3(4.0, 2.0, t * 0.08), 3), 0.0);
    float cloud = fbm(q + 2.0 * w + vec3(-t * 0.15, 0.0, t * 0.05), 5);
    vec3 col = mix(hexc(0x141a28), hexc(0x8a98b8), smoothstep(0.26, 0.8, cloud));
    em = hexc(0x6a88ff) * 0.08 * smoothstep(0.55, 0.8, cloud);
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        float cyc = t * (0.55 + 0.2 * fi) + fi * 0.5;
        vec3 rnd = hash3(vec3(floor(cyc), fi, 3.0));
        float ph = fract(cyc);
        float flash = step(ph, 0.14) * (0.55 + 0.45 * step(0.5, fract(ph * 30.0)));
        float bn = fbm(vec3(p.x * 3.0, p.y * 16.0, p.z * 16.0) + rnd * 20.0, 4);
        float win = smoothstep(0.32, 0.0, abs(p.x - (rnd.x - 0.5) * 0.8));
        float bolt = pow(1.0 - abs(2.0 * bn - 1.0), 60.0) * win;
        col += flash * cloud * hexc(0x9fb8ff) * 0.45 * win;
        em += flash * (bolt * hexc(0xdce8ff) * 3.0 + cloud * win * 0.12 * hexc(0x9fb8ff));
    }
    return col;
}

// Genesis (arme) : même émeraude profonde que les couteaux ultimes
vec3 gemDoppler(vec3 p, vec3 deep, vec3 mid, vec3 bright, vec3 hi, float glow, vec3 seed, out vec3 em);
vec3 genesis(vec3 p, out vec3 em) {
    return gemDoppler(p, hexc(0x002a0e), hexc(0x009a3c), hexc(0x12e064), hexc(0xb0ffc8), 0.12, vec3(7.1, 3.9, 12.2), em);
}

// ================================================================== finitions ultimes de couteaux (gemmes Doppler)
// Repère : x = le long de la lame (0.5 = pointe), y = largeur. PART : 1 lame, 0.75 manche caoutchouc,
// 0.5 pièce métal (garde, pommeau, anneau), 0.25 manche métal sombre (Butterfly).
// Rendu : base de métal poli recouverte d'un vernis « candy » saturé ; en dessous, un marbre profond (nuages clairs,
// volutes sombres fines, couche en parallaxe qui bouge avec l'angle) qui dérive lentement ; reflets de studio teintés.
vec3 gemDoppler(vec3 p, vec3 deep, vec3 mid, vec3 bright, vec3 hi, float glow, vec3 seed, out vec3 em) {
    float t = CamoTime;
    // réglages par couteau (type passé dans les bits 4+ de CamoParts) : le M9 garde le rendu validé ; Butterfly et
    // Karambit partagent le même vernis (validé sur le Karambit ; l'ancien vernis du Butterfly, trop brillant, laissait un
    // liseré blanc à l'inspection)
    int kn = CamoParts >> 4;
    float veinAmt = kn == 1 || kn == 2 ? 0.7 : 1.0;
    float gloss = kn == 1 || kn == 2 ? 0.6 : 0.0;
    float scale = 1.0;
    vec3 q = p * vec3(3.4, 9.0, 9.0) * scale + seed;
    vec3 w = vec3(fbm(q + vec3(0.0, 0.0, t * 0.05), 4), fbm(q + vec3(5.2, 1.3, 2.8 - t * 0.04), 4), fbm(q + vec3(9.1, 4.4, 0.7 + t * 0.035), 4));
    float cloud = fbm(q * 1.2 + 2.2 * w, 5);
    float par = fbm(q * 1.9 + 1.6 * w + vec3(viewNormal.xy * 0.3, 7.0), 4);                // couche profonde (parallaxe)
    float r1 = 1.0 - abs(2.0 * fbm(q * vec3(0.9, 1.9, 1.9) + 1.7 * w + vec3(1.0, 0.0, t * 0.03), 5) - 1.0);
    float r2 = 1.0 - abs(2.0 * fbm(q * 3.2 + 2.4 * w + vec3(4.0, 0.0, t * 0.06), 4) - 1.0);
    float wisp = max(smoothstep(0.9, 0.985, r1), 0.55 * smoothstep(0.93, 0.995, r2) * veinAmt) * mix(1.0, 0.75, 1.0 - veinAmt);
    float tt = clamp((cloud - 0.28) * 2.2 + 0.35 * (par - 0.5), 0.0, 1.0);
    vec3 base = tt < 0.55 ? mix(mid, bright, tt / 0.55) : mix(bright, hi, (tt - 0.55) / 0.45 * 0.4);
    base = mix(base, deep, wisp * 0.75);
    base = mix(base, mix(deep, mid, 0.4), smoothstep(0.52, 0.2, cloud + 0.25 * (par - 0.5)) * 0.8);
    vec3 n = bladeNormal(p);
    vec3 env = studio(n);
    float fres = pow(1.0 - clamp(abs(n.z), 0.0, 1.0), 2.0);
    vec3 col = base * (0.7 + 0.16 * min(env.g, 1.2)) + min(env, vec3(1.6)) * mix(mid, hi, 0.4) * (0.1 + 0.3 * fres) * (1.0 - 0.7 * wisp);
    vec3 cell = floor(p * 520.0);
    float flake = step(0.985, hash(cell)) * pow(max(0.0, dot(normalize(hash3(cell) - 0.5), n)), 18.0) * (0.6 + 0.4 * sin(t * 3.0 + hash(cell + 7.0) * 30.0));
    col += flake * mix(hi, vec3(1.0), 0.5) * 0.8;
    // vernis épais : deux reflets nets en bande le long de la lame, qui glissent quand le couteau tourne,
    // et un léger assombrissement du fond -> brillance et profondeur
    float c1 = p.y * 5.0 + p.x * 0.8 - 0.9 * viewNormal.y - 0.5 * viewNormal.x;
    float streak = exp(-pow((c1 - 0.18) / 0.1, 2.0)) + 0.55 * exp(-pow((c1 + 0.45) / 0.06, 2.0));
    col = col * (0.88 + 0.12 * fres) + streak * mix(bright, vec3(1.0), 0.65) * 0.5 * (1.0 - 0.6 * wisp);
    // vernis très brillant (Butterfly, Karambit) : grand reflet doux + liseré net qui glissent avec l'angle, bords plus sombres
    float sp = p.y * 6.0 - 1.1 * viewNormal.y - 0.6 * viewNormal.x + p.x * 0.5;
    float sheen = exp(-pow(sp / 0.35, 2.0));
    float glint = exp(-pow((sp - 0.12) / 0.05, 2.0));
    col = mix(col, col * 1.25 + mix(bright, vec3(1.0), 0.5) * 0.2, sheen * 0.5 * gloss) + glint * mix(bright, vec3(1.0), 0.8) * 0.6 * gloss;
    col *= 1.0 - 0.18 * gloss * smoothstep(0.02, 0.09, abs(p.y));
    em = base * glow * (0.85 + 0.15 * sin(t * 0.7 + cloud * 8.0)) + streak * bright * 0.08 + glint * bright * 0.1 * gloss;
    return col;
}

// ================================================================== finitions CS:GO classiques (ids 59 à 66)
// Métal poli (metalShade : reflets studio, paillettes) sur un motif propre à chaque finition.
float lineMask(float d, float w) {
    return 1.0 - smoothstep(w * 0.5, w, abs(d));
}

vec3 csFinish(vec3 p, out vec3 em) {
    em = vec3(0.0);
    float along = clamp(p.x + 0.5, 0.0, 1.0);                                       // 0 = garde, 1 = pointe
    vec3 base;
    float tint = 0.8, gloss = 0.75;
#if CAMO_ID == 59
    // Fade : pointe jaune, milieu rose, base violette, fondus doux
    float f = along + 0.08 * (fbm(p * vec3(4.0, 20.0, 20.0), 3) - 0.5);
    base = f > 0.72 ? mix(hexc(0xff4fa0), hexc(0xffd84a), smoothstep(0.72, 0.95, f))
         : f > 0.4 ? mix(hexc(0x9a3ce0), hexc(0xff4fa0), smoothstep(0.4, 0.72, f))
         : mix(hexc(0x3a3ad8), hexc(0x9a3ce0), smoothstep(0.1, 0.4, f));
    base *= 1.25; tint = 1.0; gloss = 0.85;
#elif CAMO_ID == 60
    // Marble Fade (Fire & Ice) : marbre rouge / bleu / jaune, pointe dorée, fines veines sombres
    vec3 q = p * vec3(5.0, 14.0, 14.0);
    vec3 w = vec3(fbm(q, 4), fbm(q + vec3(5.2, 1.3, 2.8), 4), 0.0);
    float m = fbm(q + 3.0 * w, 5);
    float band = fract(m * 3.0 + along * 0.6);
    vec3 fire = hexc(0xe0182c), ice = hexc(0x1e58f0), sun = hexc(0xffd22e);
    base = band < 0.33 ? mix(ice, fire, smoothstep(0.26, 0.33, band))
         : band < 0.66 ? mix(fire, sun, smoothstep(0.58, 0.66, band)) : mix(sun, ice, smoothstep(0.9, 1.0, band));
    base = mix(base, sun, smoothstep(0.75, 1.0, along) * 0.6);
    base = mix(base, hexc(0x14080a), lineMask(fract(m * 9.0) - 0.5, 0.06) * 0.35);
    base *= 1.2; tint = 1.0; gloss = 0.85;
#elif CAMO_ID == 61
    // Tiger Tooth : or orangé, rayures de tigre ondulées plus sombres
    float warp = fbm(p * vec3(3.0, 12.0, 12.0), 3);
    float stripe = smoothstep(0.35, 0.8, sin(p.x * 55.0 + warp * 6.0 + sin(p.y * 40.0) * 1.5));
    base = mix(hexc(0xf6b233), hexc(0xb65a06), stripe);
    base = mix(base, hexc(0xffe08a), (1.0 - stripe) * 0.25 * fbm(p * 60.0, 2));
    base *= 1.2; tint = 1.0; gloss = 0.95;
#elif CAMO_ID == 62
    // Crimson Web : rouge sang, toile d'araignée noire (rayons + anneaux polygonaux)
    vec2 c = vec2(p.x - 0.05, p.y * 3.0);
    float r = length(c), a = atan(c.y, c.x);
    float da = (fract(a / 6.2832 * 9.0) - 0.5) * 6.2832 / 9.0;
    float spokes = 1.0 - smoothstep(0.004, 0.008, abs(r * sin(da)));
    float rp = r * cos(da) * (1.0 + 0.08 * (1.0 - cos(da * 9.0)));
    float ring = 1.0 - smoothstep(0.004, 0.008, abs(fract(rp * 8.0) - 0.5) / 8.0);
    base = mix(mix(hexc(0x7a0810), hexc(0xb0121c), fbm(p * vec3(8.0, 25.0, 25.0), 3)), hexc(0x050304), max(spokes, ring) * 0.95);
    tint = 0.6; gloss = 0.45;
#elif CAMO_ID == 63
    // Slaughter : rouge vif, taches rose pâle irrégulières cernées de rouge sombre
    float n = fbm(p * vec3(7.0, 22.0, 22.0) + 3.0, 5);
    float blot = smoothstep(0.56, 0.6, n);
    base = mix(hexc(0xc41622), hexc(0xf6d6d8), blot);
    base = mix(base, hexc(0x6e0810), (smoothstep(0.5, 0.56, n) - blot) * 0.8);
    base *= 1.1; tint = 0.8; gloss = 0.8;
#elif CAMO_ID == 64
    // Case Hardened « Blue Gem » : patine bleue dominante, reflets violets, touches d'or et d'acier
    vec3 q = p * vec3(4.0, 12.0, 12.0) + vec3(7.0);
    vec3 w = vec3(fbm(q, 4), fbm(q + vec3(3.1, 7.7, 1.3), 4), 0.0);
    float m = fbm(q + 2.5 * w, 5);
    base = mix(hexc(0x0f3aa8), hexc(0x3b7cff), smoothstep(0.3, 0.7, m));
    base = mix(base, hexc(0x7a44c8), smoothstep(0.62, 0.72, m) * 0.8);
    base = mix(base, hexc(0xd8b25a), smoothstep(0.74, 0.8, fbm(q * 1.7 + w, 4)));
    base = mix(base, hexc(0x9aa3ad), smoothstep(0.7, 0.78, 1.0 - m) * 0.6);
    base *= 1.15; tint = 0.9; gloss = 0.85;
#elif CAMO_ID == 65
    // Lore : or gravé d'arabesques (deux familles de volutes)
    vec2 u = vec2(p.x * 16.0, p.y * 48.0);
    float warp = fbm(p * vec3(6.0, 20.0, 20.0), 3) * 2.0;
    float l1 = abs(sin(u.x + sin(u.y * 0.35 + warp) * 2.2));
    float l2 = abs(sin(u.x * 0.5 - u.y * 0.3 + cos(u.x * 0.7) * 1.8 + warp));
    float engr = max(1.0 - smoothstep(0.1, 0.22, l1), 0.8 * (1.0 - smoothstep(0.08, 0.18, l2)));
    base = mix(hexc(0xe0b453), hexc(0x5a3a12), engr * 0.85);
    base = mix(base, hexc(0xfff0b0), (1.0 - engr) * 0.15 * fbm(p * 80.0, 2));
    base *= 1.2; tint = 1.0; gloss = 0.95;
#else
    // Autotronic : rouge métallique, panneaux noirs, joints et rivets
    vec2 g = vec2(p.x * 6.0, p.y * 16.0);
    vec2 cid = floor(g), cf = fract(g);
    float black = step(0.62, hash(vec3(cid, 3.0)));
    float seam = max(1.0 - smoothstep(0.03, 0.07, min(cf.x, 1.0 - cf.x)), 1.0 - smoothstep(0.03, 0.07, min(cf.y, 1.0 - cf.y)));
    float bolt = 1.0 - smoothstep(0.06, 0.1, length(cf - vec2(0.2, 0.25)));
    base = mix(hexc(0xc4121a), hexc(0x141416), black);
    base = mix(base, hexc(0x0a0a0c), seam);
    base = mix(base, hexc(0x8a8f96), bolt * (1.0 - black) * 0.8);
    base *= 1.15; tint = 0.8; gloss = 0.8;
#endif
    return metalShade(p, base, tint, gloss);
}

// ================================================================== finitions exclusives (ids 100+) : une par couteau
// p : x le long de la lame (-0.5 garde -> 0.5 pointe), y en travers (étroit), z épaisseur.
vec3 hue(float h) {
    return clamp(abs(fract(h + vec3(0.0, 0.6667, 0.3333)) * 6.0 - 3.0) - 1.0, 0.0, 1.0);
}

// Manche enroulé (cuir en bandes en biais, relief) dans une palette : finitions exclusives et skins par paliers
vec3 gripWrap(vec3 p, vec3 dark, vec3 light, vec3 accent, float glow, out vec3 em) {
    float t = CamoTime;
    float grain = fbm(p * vec3(60.0, 200.0, 200.0), 2);
    // cuir enroulé : bandes larges en relief (bord ombré, dos éclairé), grain fin, pas de points parasites
    float band = fract(p.x * 22.0 + p.y * 140.0);
    float bevel = smoothstep(0.0, 0.18, band) * smoothstep(1.0, 0.72, band);
    vec3 col = mix(dark, light, 0.25 + 0.55 * bevel * (0.85 + 0.15 * grain));
    // un filet d'accent fin et régulier dans le creux entre deux bandes
    float groove = 1.0 - smoothstep(0.0, 0.06, min(band, 1.0 - band));
    col = mix(col, accent, groove * 0.45);
    em = accent * groove * 0.4 * glow * (0.8 + 0.2 * sin(t * 2.0 + p.x * 20.0));
    return metalShade(p, col, 0.5, 0.35);
}

// Manche des finitions exclusives : grip enroulé (bandes en biais, relief) dans la palette du skin, avec son détail propre
vec3 uniqueHandle(vec3 p, out vec3 em) {
    em = vec3(0.0);
    vec3 dark, light, accent;
    float glow = 0.0;
#if CAMO_ID == 100
    dark = hexc(0x16171a); light = hexc(0xe9e9e4); accent = hexc(0xf47a1f);      // Asiimov : blanc et noir, filet orange
#elif CAMO_ID == 101
    dark = hexc(0x0e0714); light = hexc(0x3a1c5c); accent = hexc(0x8a4dff);
#elif CAMO_ID == 102
    dark = hexc(0x06222e); light = hexc(0x1b6d82); accent = hexc(0x6fe6f2);
#elif CAMO_ID == 103
    dark = hexc(0x07061a); light = hexc(0x2a1d5e); accent = hexc(0xff3aa8); glow = 1.0;
#elif CAMO_ID == 104
    dark = hexc(0x0b0908); light = hexc(0x2c2521); accent = hexc(0xff5a10); glow = 1.0;
#elif CAMO_ID == 105
    dark = hexc(0x3e6f8e); light = hexc(0xd8eef8); accent = hexc(0xffffff);
#elif CAMO_ID == 106
    dark = hexc(0x0c0304); light = hexc(0x3a0810); accent = hexc(0xe01020); glow = 0.6;
#elif CAMO_ID == 107
    dark = hexc(0x2a1a06); light = hexc(0x8a5e1c); accent = hexc(0xf2c040);
#elif CAMO_ID == 108
    dark = hexc(0x07060a); light = hexc(0x1d1428); accent = hexc(0x9a4dff); glow = 1.0;
#elif CAMO_ID == 109
    dark = hexc(0x2a0604); light = hexc(0x7a1a0c); accent = hexc(0xffb03a); glow = 0.5;
#elif CAMO_ID == 110
    dark = hexc(0xcfc6b4); light = hexc(0xf6f2ea); accent = hexc(0xd9a640);
#else
    dark = hexc(0x0d1c30); light = hexc(0x3d6a86); accent = hexc(0x9fe8ff);
#endif
    return gripWrap(p, dark, light, accent, glow, em);
}

vec3 uniqueFinish(vec3 p, out vec3 em) {
    em = vec3(0.0);
    float t = CamoTime;
    float along = clamp(p.x + 0.5, 0.0, 1.0);
    float yy = clamp(p.y / 0.09, -1.0, 1.0);                                           // en travers de la lame, -1 .. 1
    vec3 base;
    float tint = 0.8, gloss = 0.75;
#if CAMO_ID == 100
    // Asiimov (Butterfly) : blanc cassé, bande noire côté dos, liseré orange, panneaux gris et triangles d'alerte
    base = hexc(0xeeeeea);
    float band = yy - (0.25 + 0.12 * sin(p.x * 9.0));
    base = mix(base, hexc(0x121314), smoothstep(0.0, 0.02, band));
    base = mix(base, hexc(0xf47a1f), lineMask(band + 0.12, 0.14));
    float seam = lineMask(fract(p.x * 5.0 + 0.5 * yy) - 0.5, 0.03);
    base = mix(base, hexc(0x9a9ea4), seam * 0.6 * step(band, 0.0));
    float tri = step(fract(p.x * 14.0) + abs(yy + 0.55) * 1.5, 0.5) * step(0.25, along) * step(along, 0.7);
    base = mix(base, hexc(0x121314), tri);
    tint = 0.2; gloss = 0.5;
#elif CAMO_ID == 101
    // Ultraviolet (CT Knife) : violet profond presque noir, usure satinée plus claire
    float n = fbm(p * vec3(9.0, 30.0, 30.0), 4);
    base = mix(hexc(0x120818), hexc(0x3a1662), smoothstep(0.35, 0.75, n));
    base += hexc(0x8a4dff) * 0.25 * pow(smoothstep(0.62, 0.8, fbm(p * vec3(20.0, 70.0, 70.0), 3)), 2.0);
    tint = 0.9; gloss = 0.45;
#elif CAMO_ID == 102
    // Bright Water (T Knife) : vagues turquoise qui ondulent, écume claire
    float w = sin(p.x * 42.0 + sin(p.y * 70.0 + t * 0.9) * 1.6 + t * 0.7 + fbm(p * 12.0, 2) * 3.0);
    base = mix(hexc(0x0a4a66), hexc(0x2ac8dc), smoothstep(-0.3, 0.8, w));
    base = mix(base, hexc(0xe8fbff), smoothstep(0.85, 0.98, w) * 0.8);
    tint = 1.0; gloss = 0.85;
#elif CAMO_ID == 103
    // Nebula (dagger) : espace profond, nuages magenta / cyan lumineux qui dérivent, étoiles qui scintillent
    vec3 q = p * vec3(6.0, 18.0, 18.0) + vec3(t * 0.03, 0.0, 0.0);
    vec3 w = vec3(fbm(q, 4), fbm(q + vec3(4.1, 2.7, 1.3), 4), 0.0);
    float neb = fbm(q + 2.2 * w, 5);
    vec3 cloud = mix(hexc(0x3a0f7a), mix(hexc(0xff3aa8), hexc(0x38e0ff), w.x), smoothstep(0.45, 0.8, neb));
    base = mix(hexc(0x05040e), cloud, smoothstep(0.35, 0.75, neb));
    vec3 cell = floor(p * 300.0);
    float star = step(0.992, hash(cell)) * (0.5 + 0.5 * sin(t * 4.0 + hash(cell + 3.0) * 40.0));
    em = cloud * smoothstep(0.55, 0.85, neb) * 0.45 + vec3(star);
    tint = 0.8; gloss = 0.7;
#elif CAMO_ID == 104
    // Lava Core (TAC Dagger) : basalte noir, fissures de lave qui pulsent et s'écoulent
    vec3 q = p * vec3(7.0, 22.0, 22.0) + vec3(0.0, 0.0, t * 0.05);
    float f = fbm(q + 1.5 * vec3(fbm(q, 3), fbm(q + 5.0, 3), 0.0), 5);
    float crack = 1.0 - smoothstep(0.015, 0.06, abs(f - 0.5));
    float pulse = 0.75 + 0.25 * sin(t * 2.2 + p.x * 12.0);
    vec3 lava = mix(hexc(0xff3a00), hexc(0xffd24a), crack);
    base = mix(mix(hexc(0x0d0b0a), hexc(0x2a2522), fbm(q * 3.0, 2)), lava, crack);
    em = lava * crack * pulse * 0.9;
    tint = 0.4; gloss = 0.35;
#elif CAMO_ID == 105
    // Frostbite (Creed) : glace bleutée à facettes cristallines, givre ramifié, scintillement
    vec3 c = floor(p * vec3(22.0, 60.0, 60.0));
    float facet = hash(c);
    float frost = 1.0 - smoothstep(0.02, 0.08, abs(fbm(p * vec3(14.0, 44.0, 44.0), 5) - 0.5));
    base = mix(hexc(0x7fb6e6), hexc(0xe6f6ff), facet * 0.6 + along * 0.2);
    base = mix(base, vec3(1.0), frost * 0.7);
    float glint = step(0.985, hash(c + 9.0)) * (0.5 + 0.5 * sin(t * 3.0 + facet * 30.0));
    em = vec3(0.6, 0.85, 1.0) * glint * 0.8;
    tint = 1.0; gloss = 0.95;
#elif CAMO_ID == 106
    // Blood Moon (Desmoulins Edge) : noir vers rouge sang, lune écarlate lumineuse vers la pointe, coulures
    base = mix(hexc(0x0a0304), hexc(0x5a0610), along);
    vec2 mc = vec2(p.x - 0.28, p.y * 2.5);
    float moon = 1.0 - smoothstep(0.07, 0.085, length(mc));
    float halo = exp(-pow(length(mc) / 0.14, 2.0));
    base = mix(base, hexc(0xe01020), moon);
    float col60 = floor(p.x * 60.0);
    float drip = step(0.72, hash(vec3(col60, 1.0, 2.0))) * smoothstep(0.2, -0.6, yy + 0.4 * hash(vec3(col60, 5.0, 1.0)));
    base = mix(base, hexc(0x8a0010), drip * 0.8);
    em = hexc(0xff2030) * (moon * 0.6 + halo * 0.25) * (0.85 + 0.15 * sin(t * 1.5));
    tint = 0.9; gloss = 0.7;
#elif CAMO_ID == 107
    // Dragon Gold (Dragons Fang) : écailles d'or en quinconce, bords sombres, reflet qui balaie la lame
    vec2 g = vec2(p.x * 13.0, yy * 2.6);
    g.y += 0.5 * mod(floor(g.x), 2.0);
    vec2 cf = fract(g) - vec2(0.5, 0.2);
    float scl = smoothstep(0.55, 0.42, length(cf));
    base = mix(hexc(0x5a3808), hexc(0xf2c040), scl);
    float sweep = exp(-pow((p.x - (fract(t * 0.15) * 1.6 - 0.8)) / 0.08, 2.0));
    base += hexc(0xfff2b0) * sweep * 0.5;
    tint = 1.0; gloss = 0.95;
#elif CAMO_ID == 108
    // Voidwalker (Executor) : noir mat, failles violettes lumineuses qui respirent, étoiles au fond des failles
    vec3 q = p * vec3(5.0, 16.0, 16.0) + vec3(0.0, 0.0, t * 0.04);
    float r = 1.0 - abs(2.0 * fbm(q + fbm(q * 2.0, 3), 5) - 1.0);
    float rift = smoothstep(0.86, 0.97, r);
    vec3 cell = floor(p * 260.0);
    float star = step(0.97, hash(cell)) * rift;
    base = mix(hexc(0x0b0a0f), hexc(0x6a1fd0), rift);
    em = mix(hexc(0x9a4dff), hexc(0xff66ff), r) * rift * (0.7 + 0.3 * sin(t * 1.8 + r * 10.0)) + vec3(star);
    tint = 0.5; gloss = 0.3;
#elif CAMO_ID == 109
    // Phoenix (Fiery Owl) : dégradé rouge -> or, langues de feu qui montent le long de la lame
    float fl = fbm(vec3(p.x * 10.0 - t * 1.2, yy * 3.0, t * 0.3), 5);
    float flame = smoothstep(0.35, 0.75, fl + along * 0.25);
    base = mix(hexc(0x5a0806), mix(hexc(0xff4a10), hexc(0xffd04a), flame), flame);
    em = mix(hexc(0xff3a00), hexc(0xffe07a), flame) * flame * 0.6;
    tint = 1.0; gloss = 0.6;
#elif CAMO_ID == 110
    // Seraph (Mercy) : nacre blanche, filigrane d'or et runes qui luisent doucement
    float nac = fbm(p * vec3(8.0, 24.0, 24.0), 3);
    base = mix(hexc(0xf4f1ea), hexc(0xd8e6f2), nac) + hue(nac + viewNormal.x * 0.3) * 0.06;
    float fil = 1.0 - smoothstep(0.06, 0.12, abs(sin(p.x * 30.0 + sin(yy * 3.0 + p.x * 8.0) * 1.4)));
    float rune = step(0.8, hash(vec3(floor(p.x * 18.0), floor(yy * 2.0), 4.0))) * lineMask(fract(p.x * 18.0) - 0.5, 0.12) * step(abs(yy), 0.6);
    base = mix(base, hexc(0xd9a640), max(fil * 0.8, rune));
    em = hexc(0xffcf60) * rune * (0.5 + 0.5 * sin(t * 2.0 + p.x * 20.0)) * 0.7;
    tint = 0.7; gloss = 0.85;
#else
    // Abyssal Pearl (Sea Gleam) : nacre irisée qui change avec l'angle, reflets d'eau (caustiques) animés
    float n = fbm(p * vec3(6.0, 18.0, 18.0), 4);
    base = mix(hexc(0x1a2a4a), hexc(0xcfe8f0), 0.55 + 0.35 * n) * (0.75 + 0.35 * hue(n * 1.5 + viewNormal.x * 0.6 + viewNormal.y * 0.4));
    vec2 cq = p.xy * vec2(30.0, 90.0);
    float caus = pow(abs(sin(cq.x + sin(cq.y * 0.5 + t) * 1.5 + t * 0.8) * sin(cq.y * 0.7 - t * 0.6 + sin(cq.x * 0.4))), 6.0);
    em = vec3(0.6, 0.95, 1.0) * caus * 0.35;
    tint = 1.0; gloss = 0.9;
#endif
    return metalShade(p, base, tint, gloss);
}

// ================================================================== skins de couteaux par paliers (ids 120 à 199)
// Chaque skin (outils/skins_couteaux.py) appelle un de ces styles avec sa palette. Tous finissent par metalShade (reflets
// du studio qui glissent avec l'angle, comme les finitions CS:GO) : du plus sobre (Classique) au plus travaillé (Prestige).
// p : x le long de la lame (-0.5 garde -> 0.5 pointe), y en travers, z épaisseur ; unités : longueur de l'arme.
float kAlong(vec3 p) {
    return clamp(p.x + 0.5, 0.0, 1.0);
}

// trait net de largeur w (unités du champ v), adouci d'un pixel d'écran : pas de scintillement de loin
float kLine(float v, float w) {
    float aa = max(fwidth(v), 1e-5);
    return 1.0 - smoothstep(w, w + 1.5 * aa, abs(v));
}

// --- Classique
// Satiné : acier brossé teinté, stries fines le long de la lame
vec3 kSatin(vec3 p, vec3 c, float brush, out vec3 em) {
    em = vec3(0.0);
    float s = vnoise(vec3(p.x * 16.0, p.y * 900.0, p.z * 900.0));
    float s2 = vnoise(vec3(p.x * 5.0, p.y * 220.0, p.z * 220.0 + 3.0));
    return metalShade(p, c * (0.9 + brush * (0.16 * s + 0.08 * s2)), 0.4, 0.75);
}

// Pierre (stonewash) : acier passé au tonneau, marques claires et petites cuvettes sombres
vec3 kStonewash(vec3 p, vec3 light, vec3 dark, out vec3 em) {
    em = vec3(0.0);
    float f1, f2;
    vec3 cell, fp;
    worley(p * vec3(70.0, 200.0, 200.0), f1, f2, cell, fp);
    float mark = smoothstep(0.02, 0.22, f2 - f1);
    float m = fbm(p * vec3(12.0, 36.0, 36.0), 3);
    vec3 base = mix(dark, light, 0.25 + 0.6 * mark + 0.15 * hash(cell)) * (0.92 + 0.16 * m);
    return metalShade(p, base, 0.2, 0.5);
}

// Opérations de nuit : noir mat, rayures fines où réapparaît le métal coloré du dessous
vec3 kNightOps(vec3 p, vec3 c, vec3 accent, out vec3 em) {
    float m = fbm(p * vec3(30.0, 90.0, 90.0), 3);
    vec3 q = p * vec3(5.0, 15.0, 15.0);
    float r = 1.0 - abs(2.0 * fbm(q + vec3(2.0), 4) - 1.0);
    float scr = smoothstep(0.93, 0.975, r) * smoothstep(0.35, 0.55, fbm(q * 0.7 + vec3(9.0), 2));
    vec3 base = mix(c * (0.85 + 0.3 * m), accent, scr);
    em = accent * scr * 0.06;
    return metalShade(p, base, 0.3, 0.3 + 0.5 * scr);
}

// Camouflage en taches (ou en pixels) : quatre teintes, peinture satinée
vec3 kCamo(vec3 p, vec3 c1, vec3 c2, vec3 c3, vec3 c4, float sc, float pixels, out vec3 em) {
    em = vec3(0.0);
    vec3 q = p * vec3(sc, sc * 2.6, sc * 2.6);
    if (pixels > 0.5) {
        q = floor(q * 7.0) / 7.0;
    }
    float a = fbm(q + vec3(1.0), 3);
    float b = fbm(q * 1.3 + vec3(7.0), 3);
    float c = fbm(q * 0.8 + vec3(13.0), 3);
    vec3 col = c1;
    col = mix(col, c2, smoothstep(0.5, 0.505, a));
    col = mix(col, c3, smoothstep(0.55, 0.555, b));
    col = mix(col, c4, smoothstep(0.6, 0.605, c));
    col *= 0.93 + 0.1 * vnoise(p * 300.0);
    return metalShade(p, col, 0.15, 0.32);
}

// Filet (safari) : fond sable nuagé, filet en losanges plus sombre
vec3 kMesh(vec3 p, vec3 c1, vec3 c2, vec3 net, out vec3 em) {
    em = vec3(0.0);
    float m = fbm(p * vec3(9.0, 26.0, 26.0), 4);
    vec3 col = mix(c1, c2, smoothstep(0.45, 0.62, m));
    float g1 = fract((p.x + p.y * 1.4) * 30.0) - 0.5;
    float g2 = fract((p.x - p.y * 1.4) * 30.0) - 0.5;
    float l = max(kLine(g1, 0.05), kLine(g2, 0.05));
    col = mix(col, net, l * 0.6);
    return metalShade(p, col, 0.1, 0.3);
}

// Brûlé : acier poli noirci par la flamme depuis la garde, liseré bleu-violet au bord de la brûlure
vec3 kScorched(vec3 p, vec3 metal, vec3 soot, out vec3 em) {
    em = vec3(0.0);
    float v = fbm(p * vec3(8.0, 24.0, 24.0), 5) + (1.0 - kAlong(p)) * 0.5;
    float burn = smoothstep(0.55, 0.75, v);
    float rim = 1.0 - smoothstep(0.0, 0.08, abs(v - 0.55));
    vec3 col = mix(metal, soot * (0.8 + 0.4 * fbm(p * 80.0, 2)), burn);
    col = mix(col, hexc(0x5a5cc8), rim * 0.45);
    return metalShade(p, col, 0.6, mix(0.8, 0.25, burn));
}

// Céramique (Cerakote) : couleur unie mate, grain fin
vec3 kCerakote(vec3 p, vec3 c, out vec3 em) {
    em = vec3(0.0);
    float g = vnoise(p * 380.0);
    float w = fbm(p * vec3(10.0, 30.0, 30.0), 3);
    return metalShade(p, c * 1.3 * (0.9 + 0.1 * g + 0.08 * w), 0.15, 0.3);
}

// Rayures de tigre (camouflage du Vietnam) : trois teintes en bandes déchirées
vec3 kTigerStripe(vec3 p, vec3 bg, vec3 mid, vec3 dark, out vec3 em) {
    em = vec3(0.0);
    float w = fbm(p * vec3(6.0, 18.0, 18.0), 3);
    float s = sin(p.x * 34.0 + w * 7.0 + p.y * 40.0);
    float s2 = sin(p.x * 21.0 - w * 5.0 + 2.0 - p.y * 25.0);
    vec3 col = mix(bg, mid, smoothstep(0.25, 0.3, s2));
    col = mix(col, dark, smoothstep(0.55, 0.6, s + 0.3 * (fbm(p * 40.0, 2) - 0.5)));
    return metalShade(p, col, 0.1, 0.3);
}

// Rouille ancienne : acier sombre, croûtes de rouille orangée qui se creusent
vec3 kRustCoat(vec3 p, vec3 steel, vec3 rustc, out vec3 em) {
    em = vec3(0.0);
    float n = fbm(p * vec3(8.0, 24.0, 24.0), 5);
    float m = smoothstep(0.42, 0.62, n);
    float pit = fbm(p * 140.0, 3);
    vec3 r = mix(rustc * 0.55, rustc, pit);
    vec3 metal = metalShade(p, steel * (0.9 + 0.2 * fbm(p * 60.0, 2)), 0.1, 0.55);
    vec3 crust = metalShade(p, r, 0.1, 0.12);
    return mix(metal, crust, m);
}

// --- Raffiné
// Damas : acier feuilleté, couches ondulées claires et sombres
vec3 kDamascus(vec3 p, vec3 c1, vec3 c2, float fq, out vec3 em) {
    em = vec3(0.0);
    // couches fines presque parallèles à la lame, ondulées par un champ lent (et quelques remous plus serrés)
    vec3 q = p * vec3(4.0, 12.0, 12.0);
    float w = fbm(q, 4) + 0.5 * fbm(q * 2.7 + vec3(4.0), 3);
    float v = p.y * 6.0 + p.x * 1.2 + 0.35 * w + 0.04 * sin(p.x * 40.0 + w * 6.0);
    float l = smoothstep(0.3, 0.7, 0.5 + 0.5 * sin(v * fq));
    return metalShade(p, mix(c2, c1, l), 0.3, 0.85);
}

// Revenu : couleurs de recuit de l'acier le long de la lame (paille, bronze, pourpre, bleu)
vec3 kTempered(vec3 p, vec3 c0, vec3 c1, vec3 c2, vec3 c3, out vec3 em) {
    em = vec3(0.0);
    float t = kAlong(p) + 0.2 * (fbm(p * vec3(6.0, 18.0, 18.0), 4) - 0.5) + 0.6 * p.y;
    vec3 col = t < 0.35 ? mix(c0, c1, smoothstep(0.08, 0.35, t)) : t < 0.65 ? mix(c1, c2, smoothstep(0.35, 0.65, t))
             : mix(c2, c3, smoothstep(0.65, 0.92, t));
    return metalShade(p, col * 1.1, 0.95, 0.9);
}

// Bicolore : deux teintes anodisées séparées par une coupe nette en biais et un filet d'accent
vec3 kTwoTone(vec3 p, vec3 c1, vec3 c2, vec3 accent, out vec3 em) {
    float d = p.x - 0.02 + p.y * 2.5;
    vec3 col = mix(c1, c2, smoothstep(-0.002, 0.002, d));
    float ln = kLine(d + 0.035, 0.006);
    col = mix(col, accent, ln);
    em = accent * ln * 0.15;
    return metalShade(p, col, 0.6, 0.7);
}

// Rayé : bandes en biais, peinture satinée
vec3 kStripes(vec3 p, vec3 c1, vec3 c2, float fq, out vec3 em) {
    em = vec3(0.0);
    float s = fract((p.x + p.y * 2.2) * fq);
    float b = smoothstep(0.48, 0.5, s) * smoothstep(0.98, 0.96, s);
    vec3 col = mix(c1, c2, b) * (0.94 + 0.08 * vnoise(p * 300.0));
    return metalShade(p, col, 0.4, 0.55);
}

// Courbes de niveau : dégradé de relief, isolignes fines, une sur cinq plus marquée
vec3 kTopo(vec3 p, vec3 c1, vec3 c2, vec3 line, out vec3 em) {
    em = vec3(0.0);
    float h = fbm(p * vec3(4.0, 12.0, 12.0) + vec3(3.0), 5);
    float v = h * 10.0;
    float iso = kLine(fract(v + 0.5) - 0.5, 0.04);
    float major = kLine(fract(v / 5.0 + 0.5) - 0.5, 0.012);
    vec3 col = mix(c1, c2, smoothstep(0.25, 0.75, h));
    col = mix(col, line, max(iso * 0.7, major));
    return metalShade(p, col, 0.5, 0.6);
}

// Blindage hexagonal : plaques à facettes de deux teintes, joints d'accent
vec3 kHex(vec3 p, vec3 c1, vec3 c2, vec3 edge, out vec3 em) {
    vec2 q = p.xy * 55.0;
    vec2 S = vec2(1.0, 1.7320508);
    vec2 a = mod(q, S) - 0.5 * S;
    vec2 b = mod(q - 0.5 * S, S) - 0.5 * S;
    vec2 g = dot(a, a) < dot(b, b) ? a : b;
    vec2 id = q - g;
    float d = max(dot(abs(g), vec2(0.866, 0.5)), abs(g.y));
    float rim = smoothstep(0.43, 0.47, d);
    vec3 col = mix(c1, c2, hash(vec3(floor(id * 4.0), 1.0)) * 0.7);
    col *= 1.0 - 0.15 * smoothstep(0.3, 0.43, d);
    col = mix(col, edge, rim);
    em = edge * rim * 0.12;
    return metalShade(p, col, 0.45, 0.65);
}

// Carbone teinté
vec3 kCarbon(vec3 p, vec3 tint, out vec3 em) {
    vec3 c = carbon(p, em);
    return c * tint * 1.4;
}

// Patine : cuivre poli rongé de vert-de-gris
vec3 kPatina(vec3 p, vec3 cu, vec3 vg, out vec3 em) {
    em = vec3(0.0);
    float n = fbm(p * vec3(9.0, 26.0, 26.0), 5);
    float m = smoothstep(0.5, 0.64, n);
    float spots = smoothstep(0.68, 0.74, fbm(p * 60.0 + vec3(3.0), 3));
    vec3 metal = metalShade(p, cu, 0.9, 0.85);
    vec3 crust = metalShade(p, vg * (0.85 + 0.3 * fbm(p * 120.0, 2)), 0.1, 0.15);
    return mix(metal, crust, max(m, spots * 0.7));
}

// Anodisé en dégradé : deux couleurs « candy » fondues le long de la lame
vec3 kAnodFade(vec3 p, vec3 c1, vec3 c2, out vec3 em) {
    em = vec3(0.0);
    float t = kAlong(p) + 0.06 * (fbm(p * vec3(5.0, 15.0, 15.0), 3) - 0.5);
    return metalShade(p, mix(c1, c2, smoothstep(0.2, 0.85, t)) * 1.15, 1.0, 0.9);
}

// Main levée : traits de feutre de trois couleurs sur un fond uni
vec3 kFreehand(vec3 p, vec3 bg, vec3 c1, vec3 c2, vec3 c3, out vec3 em) {
    em = vec3(0.0);
    vec3 q = p * vec3(4.0, 12.0, 12.0);
    float r1 = 1.0 - abs(2.0 * fbm(q + vec3(1.0), 3) - 1.0);
    float r2 = 1.0 - abs(2.0 * fbm(q * 1.4 + vec3(9.0), 3) - 1.0);
    float r3 = 1.0 - abs(2.0 * fbm(q * 0.8 + vec3(17.0), 3) - 1.0);
    vec3 col = bg * (0.95 + 0.1 * vnoise(p * 200.0));
    col = mix(col, c1, smoothstep(0.9, 0.93, r1));
    col = mix(col, c2, smoothstep(0.92, 0.945, r2));
    col = mix(col, c3, smoothstep(0.9, 0.925, r3));
    return metalShade(p, col, 0.2, 0.45);
}

// --- Prestige
// Fondu à trois couleurs (base, milieu, pointe)
vec3 kFade(vec3 p, vec3 c0, vec3 c1, vec3 c2, out vec3 em) {
    em = vec3(0.0);
    float f = kAlong(p) + 0.08 * (fbm(p * vec3(4.0, 20.0, 20.0), 3) - 0.5);
    vec3 col = f > 0.62 ? mix(c1, c2, smoothstep(0.62, 0.92, f)) : mix(c0, c1, smoothstep(0.12, 0.62, f));
    return metalShade(p, col * 1.25, 1.0, 0.88);
}

// Marbre en fondu : trois teintes en volutes, pointe de la deuxième, fines veines sombres
vec3 kMarble(vec3 p, vec3 a, vec3 b, vec3 c, vec3 vein, out vec3 em) {
    em = vec3(0.0);
    vec3 q = p * vec3(5.0, 14.0, 14.0) + vec3(11.0);
    vec3 w = vec3(fbm(q, 4), fbm(q + vec3(5.2, 1.3, 2.8), 4), 0.0);
    float m = fbm(q + 3.0 * w, 5);
    float band = fract(m * 3.0 + kAlong(p) * 0.6);
    vec3 col = band < 0.33 ? mix(a, b, smoothstep(0.26, 0.33, band))
             : band < 0.66 ? mix(b, c, smoothstep(0.58, 0.66, band)) : mix(c, a, smoothstep(0.9, 1.0, band));
    col = mix(col, b, smoothstep(0.75, 1.0, kAlong(p)) * 0.5);
    col = mix(col, vein, kLine(fract(m * 9.0) - 0.5, 0.025) * 0.5);
    return metalShade(p, col * 1.2, 1.0, 0.88);
}

// Gravure (façon Lore) : métal gravé d'arabesques, creux remplis d'une teinte
vec3 kEngraved(vec3 p, vec3 metal, vec3 fill, out vec3 em) {
    em = vec3(0.0);
    vec2 u = vec2(p.x * 16.0, p.y * 48.0);
    float warp = fbm(p * vec3(6.0, 20.0, 20.0) + vec3(5.0), 3) * 2.0;
    float l1 = abs(sin(u.x + sin(u.y * 0.35 + warp) * 2.2));
    float l2 = abs(sin(u.x * 0.5 - u.y * 0.3 + cos(u.x * 0.7) * 1.8 + warp));
    float engr = max(1.0 - smoothstep(0.1, 0.22, l1), 0.8 * (1.0 - smoothstep(0.08, 0.18, l2)));
    vec3 col = mix(metal, fill, engr * 0.9);
    col = mix(col, vec3(1.0), (1.0 - engr) * 0.08 * fbm(p * 80.0, 2));
    return metalShade(p, col * 1.15, 0.8, 0.95);
}

// Kintsugi : céramique noire fêlée, fêlures réparées à l'or qui luit doucement
vec3 kKintsugi(vec3 p, vec3 cer, vec3 gold, out vec3 em) {
    float f1, f2;
    vec3 c, fp;
    vec3 q = p * vec3(8.0, 24.0, 24.0);
    worley(q + 0.4 * vec3(fbm(q * 2.0, 2), fbm(q * 2.0 + vec3(5.0), 2), 0.0), f1, f2, c, fp);
    float crack = kLine(f2 - f1, 0.025);
    vec3 ceramic = metalShade(p, cer * (0.85 + 0.25 * hash(c)), 0.2, 0.55);
    vec3 g = metalShade(p, gold * 1.2, 1.0, 1.0);
    em = gold * crack * (0.12 + 0.05 * sin(CamoTime * 1.5 + f1 * 20.0));
    return mix(ceramic, g, crack);
}

// Prisme : vernis holographique, l'arc-en-ciel glisse avec l'angle, fin réseau de diffraction
vec3 kPrism(vec3 p, vec3 base, out vec3 em) {
    vec3 n = bladeNormal(p);
    float h = p.x * 1.2 + p.y * 4.0 + n.x * 0.7 + n.y * 0.5 + 0.1 * fbm(p * 20.0, 3);
    vec3 irid = spectrum(fract(h));
    float grat = 0.5 + 0.5 * sin((p.x * 0.6 + p.y) * 700.0);
    vec3 col = mix(base, irid * 1.1, 0.32 + 0.12 * grat);
    em = irid * 0.05;
    return metalShade(p, col, 1.0, 0.95);
}

// Opale : blanc laiteux, éclats de couleur (jeu de lumière) qui changent avec l'angle
vec3 kOpal(vec3 p, out vec3 em) {
    float f1, f2;
    vec3 c, fp;
    vec3 q = p * vec3(13.0, 40.0, 40.0);
    worley(q + 0.5 * vec3(fbm(q, 3), fbm(q + vec3(4.0), 3), 0.0), f1, f2, c, fp);
    vec3 n = bladeNormal(p);
    float h = hash(c) + dot(n.xy, vec2(0.8, 0.5)) * 0.8 + CamoTime * 0.02;
    float flash = pow(0.5 + 0.5 * sin(h * TAU * 1.5 + hash(c + vec3(3.0)) * 6.0), 1.6) * smoothstep(0.0, 0.25, f2 - f1) * (0.4 + 0.6 * fbm(q * 0.5, 2));
    vec3 milk = mix(hexc(0xe8eef4), hexc(0xb8d0e8), fbm(p * vec3(6.0, 18.0, 18.0), 3));
    vec3 fire = mix(spectrum(fract(h)), vec3(1.0), 0.25) * 1.15;
    em = fire * flash * 0.08;
    return metalShade(p, mix(milk * 1.15, fire, flash * 0.8), 0.6, 0.9);
}

// Toile : fond en deux teintes nuagées, toile d'araignée nette
vec3 kWeb(vec3 p, vec3 c1, vec3 c2, vec3 web, out vec3 em) {
    em = vec3(0.0);
    vec2 c = vec2(p.x - 0.05, p.y * 3.0);
    float r = length(c), a = atan(c.y, c.x);
    float da = (fract(a / 6.2832 * 9.0) - 0.5) * 6.2832 / 9.0;
    float spokes = kLine(r * sin(da), 0.004);
    float rp = r * cos(da) * (1.0 + 0.08 * (1.0 - cos(da * 9.0)));
    float ring = kLine((fract(rp * 8.0) - 0.5) / 8.0, 0.004);
    vec3 col = mix(mix(c1, c2, fbm(p * vec3(8.0, 25.0, 25.0), 3)), web, max(spokes, ring) * 0.95);
    return metalShade(p, col, 0.6, 0.5);
}

// Trempe (case hardened) : patine en quatre teintes
vec3 kCaseHard(vec3 p, vec3 c1, vec3 c2, vec3 c3, vec3 c4, out vec3 em) {
    em = vec3(0.0);
    vec3 q = p * vec3(4.0, 12.0, 12.0) + vec3(19.0);
    vec3 w = vec3(fbm(q, 4), fbm(q + vec3(3.1, 7.7, 1.3), 4), 0.0);
    float m = fbm(q + 2.5 * w, 5);
    vec3 col = mix(c1, c2, smoothstep(0.3, 0.7, m));
    col = mix(col, c3, smoothstep(0.62, 0.72, m) * 0.8);
    col = mix(col, c4, smoothstep(0.74, 0.8, fbm(q * 1.7 + w, 4)));
    return metalShade(p, col * 1.15, 0.9, 0.85);
}

// Case Hardened façon CS (21/10, Skeleton : « faire mieux » que le Blue Gem tout bleu) : acier gris brossé, taches de
// trempe bleues frangées de violet, plaques or et laiton, grandes taches douces ; bleu, or et acier à parts presque égales
vec3 kCaseHardCS(vec3 p, vec3 bleu, vec3 or_, vec3 acier, out vec3 em) {
    em = vec3(0.0);
    vec3 q = p * vec3(5.0, 15.0, 15.0) + vec3(11.0);
    vec3 w = vec3(fbm(q * 0.6, 3), fbm(q * 0.6 + vec3(4.3, 1.7, 8.1), 3), 0.0);
    float mb = fbm(q + 2.2 * w, 4);
    float mo = fbm(q * 1.15 + 2.0 * w.yxz + vec3(5.5), 4);
    vec3 col = acier * (0.88 + 0.12 * vnoise(vec3(p.x * 8.0, p.y * 420.0, 3.0)));
    float b = smoothstep(0.47, 0.55, mb);
    float frange = smoothstep(0.42, 0.47, mb) - smoothstep(0.47, 0.5, mb);
    col = mix(col, mix(bleu * 0.65, bleu * 1.25, vnoise(q * 2.0)), b);
    col = mix(col, hexc(0x6a3ab0), frange * 0.55);
    float o = smoothstep(0.49, 0.57, mo) * (1.0 - 0.85 * b);
    col = mix(col, mix(or_ * 0.7, or_ * 1.2, vnoise(q * 2.5 + vec3(2.0))), o);
    col = mix(col, hexc(0x8a5a2a), (smoothstep(0.44, 0.49, mo) - smoothstep(0.49, 0.57, mo)) * 0.35 * (1.0 - b));
    return metalShade(p, col * 1.1, 0.75, 0.85);
}

// --- deuxième série (styles en plus)
// Martelé : acier forgé à la main, petites cupules qui accrochent la lumière
vec3 kHammered(vec3 p, vec3 c, out vec3 em) {
    em = vec3(0.0);
    float f1, f2;
    vec3 cell, fp;
    worley(p * vec3(45.0, 130.0, 130.0), f1, f2, cell, fp);
    float dimple = smoothstep(0.0, 0.55, f1);
    float rim = 1.0 - smoothstep(0.0, 0.08, f2 - f1);
    vec3 base = c * (0.78 + 0.3 * dimple + 0.12 * hash(cell)) + rim * 0.06;
    return metalShade(p, base, 0.4, 0.8);
}

// Usé au combat : peinture écaillée sur les bords des taches, métal nu dessous
vec3 kBattleWorn(vec3 p, vec3 paint, vec3 metal, out vec3 em) {
    em = vec3(0.0);
    float n = fbm(p * vec3(10.0, 30.0, 30.0), 5) + 0.15 * fbm(p * 90.0, 2);
    float chip = smoothstep(0.66, 0.69, n);
    vec3 pc = metalShade(p, paint * (0.92 + 0.12 * vnoise(p * 300.0)), 0.15, 0.3);
    vec3 mc = metalShade(p, metal * (0.9 + 0.15 * fbm(p * 200.0, 2)), 0.1, 0.85);
    return mix(pc, mc, chip);
}

// Bois : veines du bois vernis (cernes ondulés, pores fins, nœuds)
vec3 kWood(vec3 p, vec3 light, vec3 dark, out vec3 em) {
    em = vec3(0.0);
    vec3 q = p * vec3(2.0, 22.0, 22.0);
    float w = fbm(q * vec3(1.0, 0.3, 0.3) + vec3(3.0), 4);
    float r = length(p.yz) * 70.0 + w * 5.0 + 0.6 * sin(p.x * 18.0 + w * 4.0);
    float ring = 0.5 + 0.5 * sin(r * 3.0);
    float pores = vnoise(vec3(p.x * 30.0, p.y * 600.0, p.z * 600.0));
    vec3 col = 1.35 * mix(dark, light, smoothstep(0.15, 0.85, ring) * (0.8 + 0.2 * pores));
    return metalShade(p, col, 0.2, 0.55);
}

// Pulvérisé : deux teintes peintes au pistolet, fondu granuleux
vec3 kSpray(vec3 p, vec3 c1, vec3 c2, out vec3 em) {
    em = vec3(0.0);
    float n = fbm(p * vec3(5.0, 15.0, 15.0) + vec3(7.0), 4);
    float grain = hash(floor(p * 900.0));
    float t = smoothstep(0.4, 0.6, n + (grain - 0.5) * 0.18);
    return metalShade(p, mix(c1, c2, t), 0.3, 0.45);
}

// Éclaboussures : fond uni, gouttes de peinture rondes de deux couleurs
vec3 kSplatter(vec3 p, vec3 bg, vec3 c1, vec3 c2, out vec3 em) {
    em = vec3(0.0);
    vec3 col = bg * (0.95 + 0.08 * vnoise(p * 200.0));
    for (int i = 0; i < 2; i++) {
        float s = i == 0 ? 32.0 : 58.0;
        float f1, f2;
        vec3 cell, fp;
        worley(p * s + vec3(float(i) * 13.0), f1, f2, cell, fp);   // échelle égale dans les trois axes : gouttes rondes
        float rr = 0.12 + 0.3 * hash(cell + vec3(2.0));
        float drop = step(0.4, hash(cell)) * (1.0 - smoothstep(rr, rr + 0.04, f1 + 0.08 * vnoise(p * 400.0)));
        col = mix(col, i == 0 ? c1 : c2, drop);
    }
    return metalShade(p, col, 0.3, 0.6);
}

// Léopard : rosettes (anneaux sombres ouverts autour d'un cœur plus chaud)
vec3 kLeopard(vec3 p, vec3 bg, vec3 core, vec3 ring, out vec3 em) {
    em = vec3(0.0);
    float f1, f2;
    vec3 cell, fp;
    vec3 q = p * 34.0;
    worley(q + 0.3 * vec3(fbm(q, 2), fbm(q + vec3(5.0), 2), 0.0), f1, f2, cell, fp);
    float r = 0.28 + 0.1 * hash(cell);
    float d = f1 + 0.06 * vnoise(q * 3.0);
    float rim = smoothstep(r - 0.1, r - 0.04, d) * (1.0 - smoothstep(r + 0.04, r + 0.1, d));
    float open = step(0.25, fract(atan((q - fp).y, (q - fp).x) / 6.2832 + hash(cell + vec3(4.0))));
    vec3 col = mix(bg, core, 1.0 - smoothstep(r - 0.12, r - 0.04, d));
    col = mix(col, ring, rim * open);
    return metalShade(p, col, 0.2, 0.45);
}

// Facettes : triangles « basse définition » de trois teintes, arêtes fines
vec3 kFacets(vec3 p, vec3 c1, vec3 c2, vec3 c3, out vec3 em) {
    em = vec3(0.0);
    vec2 q = vec2(p.x * 9.0, p.y * 26.0);
    vec2 s = vec2(q.x + q.y * 0.5, q.y);
    vec2 id = floor(s);
    vec2 f = fract(s);
    float tri = step(f.y, f.x);
    float h = hash(vec3(id, tri));
    vec3 col = h < 0.33 ? c1 : h < 0.66 ? c2 : c3;
    col *= 0.85 + 0.3 * hash(vec3(id, tri + 5.0));
    float e = min(min(f.x, f.y), abs(f.x - f.y));
    col *= 1.0 - 0.35 * kLine(e, 0.015);
    return metalShade(p, col, 0.5, 0.75);
}

// Titane anodisé : arc-en-ciel de recuit (bronze, violet, bleu, or, vert) en vagues
vec3 kTitanium(vec3 p, out vec3 em) {
    em = vec3(0.0);
    float t = kAlong(p) * 1.4 + 0.5 * fbm(p * vec3(5.0, 15.0, 15.0), 4) + 2.0 * p.y;
    float s[6] = float[](0.0, 0.2, 0.4, 0.6, 0.8, 1.0);
    vec3 c[6] = vec3[](hexc(0xb08a4a), hexc(0x8a3aa8), hexc(0x2a5ae0), hexc(0x3ac8d8), hexc(0xe0c040), hexc(0x40c070));
    return metalShade(p, ramp6(fract(t), s, c) * 1.1, 1.0, 0.95);
}

// Sakura : fond laqué, pétales de cerisier (cinq lobes) et branches fines
vec3 kSakura(vec3 p, vec3 bg, vec3 petal, vec3 branch, out vec3 em) {
    em = vec3(0.0);
    vec3 col = bg * 1.3;
    float br = 1.0 - abs(2.0 * fbm(p * vec3(3.0, 9.0, 9.0) + vec3(2.0), 4) - 1.0);
    col = mix(col, branch, smoothstep(0.955, 0.975, br));
    float f1, f2;
    vec3 cell, fp;
    vec3 q = p * 14.0;
    worley(q, f1, f2, cell, fp);
    vec3 dq = q - fp;
    float a = atan(dq.y, dq.x) + hash(cell) * 6.2832;
    float rr = (0.26 + 0.14 * hash(cell + vec3(1.0))) * (0.7 + 0.3 * abs(cos(a * 2.5)));
    float pet = step(0.3, hash(cell + vec3(7.0))) * (1.0 - smoothstep(rr - 0.03, rr, length(dq)));
    vec3 pc = mix(petal * 1.2, vec3(1.0), 0.45 * (1.0 - smoothstep(0.0, rr, length(dq))));
    col = mix(col, pc, pet);
    return metalShade(p, col, 0.4, 0.85);
}

// Circuit : carte électronique, pistes et pastilles qui luisent par impulsions
vec3 kCircuit(vec3 p, vec3 bg, vec3 trace, vec3 glow, out vec3 em) {
    vec2 g = vec2(p.x * 20.0, p.y * 60.0);
    vec2 id = floor(g), f = fract(g);
    float h = hash(vec3(id, 3.0));
    float hl = kLine(f.y - 0.5, 0.07) * step(0.35, h);
    float vl = kLine(f.x - 0.5, 0.07) * step(0.7, hash(vec3(id, 9.0)));
    float pad = (1.0 - smoothstep(0.16, 0.22, length(f - 0.5))) * step(0.82, h);
    float tr = max(max(hl, vl), pad);
    float pulse = pow(0.5 + 0.5 * sin(p.x * 30.0 - CamoTime * 3.0 + h * 6.0), 6.0);
    vec3 col = mix(bg * (0.9 + 0.1 * vnoise(p * 300.0)), trace, tr);
    em = glow * tr * (0.15 + 0.6 * pulse);
    return metalShade(p, col, 0.5, 0.6);
}

// Aurore : ciel nocturne, rideaux de lumière verte et violette qui ondulent
vec3 kAurora(vec3 p, vec3 night, vec3 c1, vec3 c2, out vec3 em) {
    float t = CamoTime;
    float x = p.x * 6.0;
    float curtain = 0.0;
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float y0 = 0.25 * sin(x * (0.8 + 0.3 * fi) + t * (0.3 + 0.1 * fi) + fi * 2.0) * 0.06;
        float d = p.y - y0 - (fi - 1.0) * 0.02;
        curtain += exp(-pow(d / 0.018, 2.0)) * (0.6 + 0.4 * sin(p.x * 80.0 + t * 1.5 + fi));
    }
    vec3 au = mix(c1, c2, smoothstep(-0.03, 0.04, p.y));
    vec3 cell = floor(p * 300.0);
    float star = step(0.993, hash(cell)) * (0.5 + 0.5 * sin(t * 3.0 + hash(cell + 3.0) * 40.0));
    vec3 col = night + au * min(curtain, 1.2) * 0.9;
    em = au * curtain * 0.35 + vec3(star) * 0.8;
    return metalShade(p, col, 0.8, 0.8);
}

// Foudre : métal sombre, arcs électriques qui glissent en continu (21/10 : plus de sauts 6 fois par seconde, jugés
// saccadés ; 6 octaves de bruit au lieu de 10, déformation lente qui fait vivre les arcs sans à-coup)
vec3 kElectric(vec3 p, vec3 dark, vec3 arc, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(5.0, 15.0, 15.0) + vec3(t * 0.35, 0.0, t * 0.55);
    float w = fbm(q * 2.0 + vec3(0.0, t * 0.25, 0.0), 2);
    float r = 1.0 - abs(2.0 * fbm(q + vec3(w), 4) - 1.0);
    float bolt = smoothstep(0.93, 0.99, r);
    float glow = smoothstep(0.8, 0.99, r);
    float pouls = 0.88 + 0.12 * sin(t * 5.0 + p.x * 6.0);
    vec3 col = mix(dark * (0.9 + 0.2 * vnoise(p * 40.0)), mix(arc, vec3(1.0), 0.5), bolt);
    em = arc * (bolt * 0.9 + glow * 0.25) * pouls;
    return metalShade(p, col, 0.5, 0.7);
}

// Métal liquide : or (ou autre) en fusion qui coule lentement, reflets mouvants
vec3 kMolten(vec3 p, vec3 c, vec3 hot, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(4.0, 12.0, 12.0) + vec3(-t * 0.08, 0.0, t * 0.03);
    float f = fbm(q + 1.5 * vec3(fbm(q, 3), fbm(q + vec3(4.0), 3), 0.0), 5);
    float ridge = 1.0 - abs(2.0 * f - 1.0);
    vec3 n = normalize(bladeNormal(p) + vec3((f - 0.5) * 1.4, (ridge - 0.5) * 1.4, 0.0));
    vec3 env = studio(n);
    vec3 col = c * (0.35 + 0.5 * env.g) + env * c * 0.5 + hot * smoothstep(0.85, 0.98, ridge) * 0.5;
    em = hot * smoothstep(0.9, 0.99, ridge) * 0.25;
    return col;
}


// Autotronic (CS2, refait le 21/10 : « ne ressemble pas à un Autotronic ») : lame rouge anodisée brillante, bande noire en
// carbone le long de l'arête, bordée de découpes mécaniques en escalier et d'un filet d'acier, rivets d'acier, col noir à
// la base barré d'entailles rouges, tranchants d'acier poli. Largeur de la lame mesurée sur le Push (demi-largeur 0,237 à
// la base, 0,025 à la pointe) : tout est placé en part de la demi-largeur (v = 0 sur l'arête, 1 au tranchant)
vec3 kAutotronic(vec3 p, vec3 rouge, vec3 noir, out vec3 em) {
    em = vec3(0.0);
    float t = clamp(p.x + 0.5, 0.0, 1.0);
    float hw = max(0.237 - 0.215 * pow(t, 2.2), 0.02);
    float v = clamp(abs(p.y) / hw, 0.0, 1.0);
    // rouge anodisé : brossage fin le long de la lame, profondeur qui varie doucement
    float brosse = 0.9 + 0.1 * vnoise(vec3(p.x * 6.0, p.y * 380.0, 2.0));
    vec3 anod = rouge * brosse * (0.85 + 0.25 * smoothstep(0.35, 0.85, v));
    // bande noire de l'arête : bord en escalier (crans tous les 0,12 le long de la lame), plus étroite vers la pointe
    float cran = floor((p.x + 0.5) / 0.12);
    float marche = 0.22 + 0.08 * mod(cran, 2.0) - 0.06 * step(0.5, fract((p.x + 0.5) / 0.12)) * mod(cran + 1.0, 2.0);
    float lim = marche * (1.0 - smoothstep(0.30, 0.50, p.x) * 0.55);
    float dBande = v - lim;
    float bande = 1.0 - smoothstep(-0.008, 0.008, dBande);
    vec3 em2;
    vec3 carb = carbon(p, em2) * noir * 4.0;
    // filet d'acier entre le noir et le rouge
    float filet = 1.0 - smoothstep(0.012, 0.03, abs(dBande - 0.035));
    // col noir à la base, entailles rouges en biais
    float col_ = 1.0 - smoothstep(-0.43, -0.415, p.x);
    float entaille = col_ * kLine(fract((p.x * 2.0 + p.y * 3.0) * 6.0) - 0.5, 0.06) * step(v, 0.8);
    // rivets d'acier dans la bande, un par cran
    vec2 rq = vec2(fract((p.x + 0.5) / 0.12) - 0.5, abs(p.y) / hw * 0.6 - 0.09);
    float rivet = (1.0 - smoothstep(0.08, 0.12, length(rq * vec2(1.0, 2.2)))) * step(p.x, 0.25) * step(-0.36, p.x);
    // tranchant d'acier poli (biseau)
    float tranchant = smoothstep(0.84, 0.9, v);
    vec3 acier = hexc(0xd4d9e0) * (0.92 + 0.08 * vnoise(vec3(p.x * 9.0, p.y * 500.0, 4.0)));
    vec3 base = anod;
    float noirAmt = max(bande, col_);
    base = mix(base, carb, noirAmt);
    base = mix(base, rouge * 1.1, entaille);
    base = mix(base, acier * 0.95, max(filet * (1.0 - col_), rivet * bande));
    base = mix(base, acier, tranchant);
    float teinte = mix(0.95, 0.15, max(noirAmt, max(tranchant, filet)));
    float brillance = mix(0.92, 0.55, noirAmt * (1.0 - tranchant));
    return metalShade(p, base, teinte, brillance);
}

// <skins_couteaux> : écrit par outils/skins_couteaux.py (catalogue des skins), ne pas modifier à la main
#if CAMO_ID == 120
#define SKIN_PALIERS
int K_STYLE = 0;
vec3 K_C[5];
vec4 K_F = vec4(0.0);
int K_GRIP = 0;
vec3 K_GD = vec3(0.0), K_GL = vec3(0.0), K_GA = vec3(0.0);
float K_GG = 0.0;
vec3 K_INLAY = vec3(0.1);
void skinParams() {
    switch (CamoId) {
    case 120:   // m9 | Night Ops (Classique)
        K_STYLE = 20;
        K_C[0] = hexc(0x383a41);
        K_C[1] = hexc(0xe02a36);
        break;
    case 121:   // m9 | Forest DDPAT (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0x6a7450);
        K_C[1] = hexc(0x3e4830);
        K_C[2] = hexc(0x9a9a72);
        K_C[3] = hexc(0x22281a);
        K_F.x = 7.0;
        K_F.y = 1.0;
        break;
    case 122:   // m9 | Satin (Classique)
        K_STYLE = 25;
        K_C[0] = hexc(0xb9bec6);
        K_F.x = 1.0;
        break;
    case 123:   // m9 | Damascus Steel (Raffiné)
        K_STYLE = 9;
        K_C[0] = hexc(0xd4d8de);
        K_C[1] = hexc(0x3a3e46);
        K_F.x = 90.0;
        break;
    case 124:   // m9 | Blue Steel (Raffiné)
        K_STYLE = 31;
        K_C[0] = hexc(0xc8a865);
        K_C[1] = hexc(0x9a5a3a);
        K_C[2] = hexc(0x5a3aa0);
        K_C[3] = hexc(0x2a5ad8);
        break;
    case 125:   // m9 | Ocean Fade (Prestige)
        K_STYLE = 13;
        K_C[0] = hexc(0x2c2ab8);
        K_C[1] = hexc(0x1aa0d8);
        K_C[2] = hexc(0x4ef0d0);
        break;
    case 126:   // karambit | Urban Masked (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0x8a8e94);
        K_C[1] = hexc(0x4a4e55);
        K_C[2] = hexc(0xc8ccd2);
        K_C[3] = hexc(0x24272b);
        K_F.x = 6.0;
        K_F.y = 0.0;
        break;
    case 127:   // karambit | Stonewash (Classique)
        K_STYLE = 29;
        K_C[0] = hexc(0xb8bcc2);
        K_C[1] = hexc(0x5a5e64);
        break;
    case 128:   // karambit | Coyote (Classique)
        K_STYLE = 7;
        K_C[0] = hexc(0x9a7a52);
        break;
    case 130:   // karambit | Copper Patina (Raffiné)
        K_STYLE = 21;
        K_C[0] = hexc(0xc8743a);
        K_C[1] = hexc(0x3aa088);
        break;
    case 133:   // butterfly | Scorched (Classique)
        K_STYLE = 26;
        K_C[0] = hexc(0xc0c4ca);
        K_C[1] = hexc(0x2a2420);
        K_INLAY = hexc(0x2a2420);
        break;
    case 136:   // butterfly | Blue Carbon (Raffiné)
        K_STYLE = 4;
        K_C[0] = vec3(0.75, 0.9, 1.25);
        K_INLAY = hexc(0x1a2a4a);
        break;
    case 139:   // tac_dagger | Tiger Stripe (Classique)
        K_STYLE = 32;
        K_C[0] = hexc(0x8a8a5a);
        K_C[1] = hexc(0x4a5a30);
        K_C[2] = hexc(0x16180e);
        K_GRIP = 1; K_GD = hexc(0x14160e); K_GL = hexc(0x3a4228); K_GA = hexc(0x8a8a5a); K_GG = 0.0;
        break;
    case 140:   // tac_dagger | Satin Bronze (Classique)
        K_STYLE = 25;
        K_C[0] = hexc(0xb07a40);
        K_F.x = 1.0;
        K_GRIP = 1; K_GD = hexc(0x1a120a); K_GL = hexc(0x5a3e22); K_GA = hexc(0xc89050); K_GG = 0.0;
        break;
    case 142:   // tac_dagger | Sunset (Raffiné)
        K_STYLE = 1;
        K_C[0] = hexc(0xe0306a);
        K_C[1] = hexc(0xffa030);
        K_GRIP = 1; K_GD = hexc(0x1a0a10); K_GL = hexc(0x5a1a30); K_GA = hexc(0xffa030); K_GG = 0.0;
        break;
    case 143:   // tac_dagger | Emerald Web (Prestige)
        K_STYLE = 35;
        K_C[0] = hexc(0x0a4a24);
        K_C[1] = hexc(0x16903c);
        K_C[2] = hexc(0x020604);
        K_GRIP = 1; K_GD = hexc(0x020604); K_GL = hexc(0x0a3a1c); K_GA = hexc(0x16c04c); K_GG = 0.0;
        break;
    case 146:   // creed | Blackout (Classique)
        K_STYLE = 20;
        K_C[0] = hexc(0x34353a);
        K_C[1] = hexc(0xe8b850);
        K_GRIP = 1; K_GD = hexc(0x0a0a0c); K_GL = hexc(0x1e1e22); K_GA = hexc(0xd8a840); K_GG = 0.0;
        break;
    case 147:   // creed | Freehand (Raffiné)
        K_STYLE = 14;
        K_C[0] = hexc(0xe8e4dc);
        K_C[1] = hexc(0x1a1a1a);
        K_C[2] = hexc(0xe02a2a);
        K_C[3] = hexc(0x2a6ae0);
        K_GRIP = 1; K_GD = hexc(0x141414); K_GL = hexc(0x3a3a3a); K_GA = hexc(0xe02a2a); K_GG = 0.0;
        break;
    case 148:   // creed | Twin Tone (Raffiné)
        K_STYLE = 34;
        K_C[0] = hexc(0xe8e8e4);
        K_C[1] = hexc(0x16171a);
        K_C[2] = hexc(0xe02030);
        K_GRIP = 1; K_GD = hexc(0x0e0e10); K_GL = hexc(0x2a2a2e); K_GA = hexc(0xe02030); K_GG = 0.0;
        break;
    case 149:   // creed | Prism (Prestige)
        K_STYLE = 22;
        K_C[0] = hexc(0xc0c4cc);
        K_GRIP = 1; K_GD = hexc(0x101014); K_GL = hexc(0x3a3a48); K_GA = hexc(0x9ad8ff); K_GG = 0.4;
        break;
    case 152:   // desmoulins_edge | Ash (Classique)
        K_STYLE = 26;
        K_C[0] = hexc(0xa8aab0);
        K_C[1] = hexc(0x3a3a3e);
        break;
    case 153:   // desmoulins_edge | Rose Gold (Raffiné)
        K_STYLE = 1;
        K_C[0] = hexc(0xd88a8a);
        K_C[1] = hexc(0xf2c8a0);
        break;
    case 155:   // desmoulins_edge | Silver Arabesque (Prestige)
        K_STYLE = 11;
        K_C[0] = hexc(0xd0d6de);
        K_C[1] = hexc(0x1a3a8a);
        break;
    case 178:   // desmoulins_edge | Garnet (Ultime)
        K_STYLE = 0;
        K_C[0] = hexc(0x14000a);
        K_C[1] = hexc(0x5a0628);
        K_C[2] = hexc(0xa0103a);
        K_C[3] = hexc(0xff90b0);
        K_F.x = 0.12;
        K_C[4] = vec3(19.3, 4.1, 33.7);
        break;
    case 158:   // dragons_fang | Charcoal (Classique)
        K_STYLE = 7;
        K_C[0] = hexc(0x2e3034);
        K_GRIP = 1; K_GD = hexc(0x0e0e10); K_GL = hexc(0x2a2c30); K_GA = hexc(0x6a6e74); K_GG = 0.0;
        break;
    case 159:   // dragons_fang | Forged Damascus (Raffiné)
        K_STYLE = 9;
        K_C[0] = hexc(0xe0d0b8);
        K_C[1] = hexc(0x2e221a);
        K_F.x = 120.0;
        K_GRIP = 1; K_GD = hexc(0x140e0a); K_GL = hexc(0x4a3628); K_GA = hexc(0xd8c8b0); K_GG = 0.0;
        break;
    case 163:   // executor | Urban Digital (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0x6a6e74);
        K_C[1] = hexc(0x3a3d42);
        K_C[2] = hexc(0xa8acb2);
        K_C[3] = hexc(0x1c1e22);
        K_F.x = 7.0;
        K_F.y = 1.0;
        K_GRIP = 1; K_GD = hexc(0x141518); K_GL = hexc(0x3a3d42); K_GA = hexc(0xa8acb2); K_GG = 0.0;
        break;
    case 165:   // executor | Hazard (Raffiné)
        K_STYLE = 34;
        K_C[0] = hexc(0xf0c020);
        K_C[1] = hexc(0x141416);
        K_C[2] = hexc(0xffffff);
        K_GRIP = 1; K_GD = hexc(0x0e0e10); K_GL = hexc(0x2a2a2c); K_GA = hexc(0xf0c020); K_GG = 0.0;
        break;
    case 166:   // executor | Toxic Fade (Prestige)
        K_STYLE = 13;
        K_C[0] = hexc(0x1a6a20);
        K_C[1] = hexc(0x7ad020);
        K_C[2] = hexc(0xf0f040);
        K_GRIP = 1; K_GD = hexc(0x0a1a0a); K_GL = hexc(0x1a4a1a); K_GA = hexc(0x9af030); K_GG = 0.5;
        break;
    case 179:   // executor | Amethyst (Ultime)
        K_STYLE = 0;
        K_C[0] = hexc(0x12021e);
        K_C[1] = hexc(0x5a1490);
        K_C[2] = hexc(0xa040e8);
        K_C[3] = hexc(0xe8b0ff);
        K_F.x = 0.12;
        K_C[4] = vec3(27.9, 15.2, 8.6);
        K_GRIP = 1; K_GD = hexc(0x0a0410); K_GL = hexc(0x2a0e44); K_GA = hexc(0xb060ff); K_GG = 0.5;
        break;
    case 169:   // mercy | Snow Camo (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0xf0f2f4);
        K_C[1] = hexc(0xb8bec6);
        K_C[2] = hexc(0x7a828c);
        K_C[3] = hexc(0xd8dce2);
        K_F.x = 6.0;
        K_F.y = 0.0;
        K_GRIP = 1; K_GD = hexc(0x3a4048); K_GL = hexc(0xb8bec6); K_GA = hexc(0xf0f2f4); K_GG = 0.0;
        break;
    case 170:   // mercy | Azure Temper (Raffiné)
        K_STYLE = 31;
        K_C[0] = hexc(0xd8d0b0);
        K_C[1] = hexc(0x9ac0d8);
        K_C[2] = hexc(0x3a7ad8);
        K_C[3] = hexc(0x1a2a8a);
        K_GRIP = 1; K_GD = hexc(0x0e1428); K_GL = hexc(0x2a4a8a); K_GA = hexc(0x9ac0d8); K_GG = 0.0;
        break;
    case 173:   // sea_gleam | Navy (Classique)
        K_STYLE = 7;
        K_C[0] = hexc(0x1e2e4a);
        K_GRIP = 1; K_GD = hexc(0x0a1020); K_GL = hexc(0x1e2e4a); K_GA = hexc(0x6a8ab8); K_GG = 0.0;
        break;
    case 176:   // sea_gleam | Tide Stripe (Raffiné)
        K_STYLE = 30;
        K_C[0] = hexc(0x0e2a3a);
        K_C[1] = hexc(0x1a8a9a);
        K_F.x = 8.0;
        K_GRIP = 1; K_GD = hexc(0x081a24); K_GL = hexc(0x0e2a3a); K_GA = hexc(0x1a8a9a); K_GG = 0.0;
        break;
    case 177:   // sea_gleam | Gold Hardened (Prestige)
        K_STYLE = 5;
        K_C[0] = hexc(0xc89a30);
        K_C[1] = hexc(0xf0d070);
        K_C[2] = hexc(0x8a4a20);
        K_C[3] = hexc(0x3a6ab8);
        K_GRIP = 1; K_GD = hexc(0x1a1006); K_GL = hexc(0x5a3a12); K_GA = hexc(0xf0d070); K_GG = 0.0;
        break;
    case 300:   // m9 | Battle Worn (Classique)
        K_STYLE = 2;
        K_C[0] = hexc(0x4a5a3a);
        K_C[1] = hexc(0xb8bcc2);
        break;
    case 303:   // m9 | Leopard (Raffiné)
        K_STYLE = 17;
        K_C[0] = hexc(0xd8b070);
        K_C[1] = hexc(0xa86a2a);
        K_C[2] = hexc(0x1a1208);
        break;
    case 304:   // m9 | Shard (Raffiné)
        K_STYLE = 12;
        K_C[0] = hexc(0x1a3a6a);
        K_C[1] = hexc(0x2a6ab8);
        K_C[2] = hexc(0x8ab8e8);
        break;
    case 305:   // m9 | Sakura (Prestige)
        K_STYLE = 24;
        K_C[0] = hexc(0x120c10);
        K_C[1] = hexc(0xf0a0c0);
        K_C[2] = hexc(0x3a2418);
        break;
    case 306:   // karambit | Arid Digital (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0xc8b490);
        K_C[1] = hexc(0x9a7a52);
        K_C[2] = hexc(0x6a5a3a);
        K_C[3] = hexc(0xe0d4b4);
        K_F.x = 6.0;
        K_F.y = 1.0;
        break;
    case 307:   // karambit | Black Satin (Classique)
        K_STYLE = 25;
        K_C[0] = hexc(0x3a3d44);
        K_F.x = 1.0;
        break;
    case 308:   // karambit | Ember Worn (Classique)
        K_STYLE = 2;
        K_C[0] = hexc(0xa83a1a);
        K_C[1] = hexc(0xb0b4ba);
        break;
    case 309:   // karambit | Splatter (Raffiné)
        K_STYLE = 27;
        K_C[0] = hexc(0x16171a);
        K_C[1] = hexc(0xe02a6a);
        K_C[2] = hexc(0x30c0e0);
        break;
    case 311:   // karambit | Titanium (Prestige)
        K_STYLE = 33;
        break;
    case 312:   // butterfly | Forest Camo (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0x4a5a3a);
        K_C[1] = hexc(0x2a3a22);
        K_C[2] = hexc(0x6a7a4a);
        K_C[3] = hexc(0x1a2214);
        K_F.x = 5.0;
        K_F.y = 0.0;
        K_INLAY = hexc(0x2a3a22);
        break;
    case 314:   // butterfly | Polar Spray (Classique)
        K_STYLE = 28;
        K_C[0] = hexc(0xe8ecf0);
        K_C[1] = hexc(0x8aa0b8);
        K_INLAY = hexc(0x5a6a80);
        break;
    case 316:   // butterfly | Neon Stripe (Raffiné)
        K_STYLE = 30;
        K_C[0] = hexc(0x101014);
        K_C[1] = hexc(0x30e0a0);
        K_F.x = 8.0;
        K_INLAY = hexc(0x0e4a36);
        break;
    case 318:   // tac_dagger | Urban Worn (Classique)
        K_STYLE = 2;
        K_C[0] = hexc(0x3a3e44);
        K_C[1] = hexc(0xb8bcc2);
        K_GRIP = 1; K_GD = hexc(0x0a0b0c); K_GL = hexc(0x1f2225); K_GA = hexc(0xb8bcc2); K_GG = 0.0;
        break;
    case 321:   // tac_dagger | Ember Shard (Raffiné)
        K_STYLE = 12;
        K_C[0] = hexc(0x6a1a0a);
        K_C[1] = hexc(0xc04018);
        K_C[2] = hexc(0xf0a040);
        K_GRIP = 1; K_GD = hexc(0x130401); K_GL = hexc(0x3a0e05); K_GA = hexc(0xf0a040); K_GG = 0.0;
        break;
    case 322:   // tac_dagger | Snow Leopard (Raffiné)
        K_STYLE = 17;
        K_C[0] = hexc(0xe8e8ea);
        K_C[1] = hexc(0xb8bcc4);
        K_C[2] = hexc(0x2a2c30);
        K_GRIP = 1; K_GD = hexc(0x29292a); K_GL = hexc(0x7f7f80); K_GA = hexc(0xe8e8ea); K_GG = 0.0;
        break;
    case 325:   // creed | Hammered Steel (Classique)
        K_STYLE = 15;
        K_C[0] = hexc(0x8a9098);
        K_GRIP = 1; K_GD = hexc(0x18191b); K_GL = hexc(0x4b4f53); K_GA = hexc(0x8a9098); K_GG = 0.0;
        break;
    case 326:   // creed | Midnight Camo (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0x2a3048);
        K_C[1] = hexc(0x1a1e30);
        K_C[2] = hexc(0x3a4460);
        K_C[3] = hexc(0x0e1018);
        K_F.x = 6.0;
        K_F.y = 0.0;
        K_GRIP = 1; K_GD = hexc(0x07080c); K_GL = hexc(0x171a27); K_GA = hexc(0x3a4460); K_GG = 0.0;
        break;
    case 328:   // creed | Ink Splatter (Raffiné)
        K_STYLE = 27;
        K_C[0] = hexc(0xe8e4dc);
        K_C[1] = hexc(0x101012);
        K_C[2] = hexc(0xc01820);
        K_GRIP = 1; K_GD = hexc(0x292927); K_GL = hexc(0x7f7d79); K_GA = hexc(0xc01820); K_GG = 0.0;
        break;
    case 330:   // creed | Imperial Marble (Prestige)
        K_STYLE = 18;
        K_C[0] = hexc(0x8a0a1a);
        K_C[1] = hexc(0xf0d080);
        K_C[2] = hexc(0x2a0a0a);
        K_C[3] = hexc(0x0a0204);
        K_GRIP = 1; K_GD = hexc(0x180104); K_GL = hexc(0x4b050e); K_GA = hexc(0xf0d080); K_GG = 0.0;
        break;
    case 331:   // creed | Tanzanite (Ultime)
        K_STYLE = 0;
        K_C[0] = hexc(0x0a0628);
        K_C[1] = hexc(0x3a2aa0);
        K_C[2] = hexc(0x7a6af0);
        K_C[3] = hexc(0xd0c8ff);
        K_F.x = 0.12;
        K_C[4] = vec3(2.7, 17.9, 40.3);
        K_GRIP = 1; K_GD = hexc(0x010107); K_GL = hexc(0x050316); K_GA = hexc(0x7a6af0); K_GG = 0.0;
        break;
    case 332:   // desmoulins_edge | Night Digital (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0x5a5a64);
        K_C[1] = hexc(0x3a3a44);
        K_C[2] = hexc(0x7a7a86);
        K_C[3] = hexc(0x24242c);
        K_F.x = 5.0;
        K_F.y = 1.0;
        break;
    case 334:   // desmoulins_edge | Gilded (Classique)
        K_STYLE = 25;
        K_C[0] = hexc(0xd8b060);
        K_F.x = 0.8;
        break;
    case 336:   // desmoulins_edge | Crimson Shard (Raffiné)
        K_STYLE = 12;
        K_C[0] = hexc(0x3a0610);
        K_C[1] = hexc(0x8a1020);
        K_C[2] = hexc(0xd02040);
        break;
    case 337:   // desmoulins_edge | Obsidian Sakura (Prestige)
        K_STYLE = 24;
        K_C[0] = hexc(0x08080a);
        K_C[1] = hexc(0xe02040);
        K_C[2] = hexc(0x2a2a2e);
        break;
    case 338:   // desmoulins_edge | Molten Gold (Ultime)
        K_STYLE = 19;
        K_C[0] = hexc(0xe0b040);
        K_C[1] = hexc(0xff8020);
        break;
    case 340:   // dragons_fang | Volcanic (Classique)
        K_STYLE = 29;
        K_C[0] = hexc(0x5a4a44);
        K_C[1] = hexc(0x1a1210);
        K_GRIP = 1; K_GD = hexc(0x100d0c); K_GL = hexc(0x312825); K_GA = hexc(0x5a4a44); K_GG = 0.0;
        break;
    case 345:   // dragons_fang | Citrine (Ultime)
        K_STYLE = 0;
        K_C[0] = hexc(0x2a1400);
        K_C[1] = hexc(0xa05a00);
        K_C[2] = hexc(0xf0b020);
        K_C[3] = hexc(0xfff0a0);
        K_F.x = 0.12;
        K_C[4] = vec3(5.5, 21.2, 9.9);
        K_GRIP = 1; K_GD = hexc(0x070300); K_GL = hexc(0x170b00); K_GA = hexc(0xf0b020); K_GG = 0.0;
        break;
    case 346:   // executor | Charcoal Worn (Classique)
        K_STYLE = 2;
        K_C[0] = hexc(0x26282c);
        K_C[1] = hexc(0x9aa0a8);
        K_GRIP = 1; K_GD = hexc(0x060707); K_GL = hexc(0x141618); K_GA = hexc(0x9aa0a8); K_GG = 0.0;
        break;
    case 349:   // executor | Toxic Splatter (Raffiné)
        K_STYLE = 27;
        K_C[0] = hexc(0x141614);
        K_C[1] = hexc(0x9af030);
        K_C[2] = hexc(0xf0e040);
        K_GRIP = 1; K_GD = hexc(0x030303); K_GL = hexc(0x0b0c0b); K_GA = hexc(0x9af030); K_GG = 0.0;
        break;
    case 350:   // executor | Violet Shard (Raffiné)
        K_STYLE = 12;
        K_C[0] = hexc(0x2a1048);
        K_C[1] = hexc(0x5a2a90);
        K_C[2] = hexc(0xa060e0);
        K_GRIP = 1; K_GD = hexc(0x07020c); K_GL = hexc(0x170827); K_GA = hexc(0xa060e0); K_GG = 0.0;
        break;
    case 351:   // executor | Neon Circuit (Prestige)
        K_STYLE = 8;
        K_C[0] = hexc(0x0c0a14);
        K_C[1] = hexc(0x6a2aa0);
        K_C[2] = hexc(0xff40e0);
        K_GRIP = 1; K_GD = hexc(0x020103); K_GL = hexc(0x06050b); K_GA = hexc(0xff40e0); K_GG = 0.0;
        break;
    case 352:   // executor | Onyx (Ultime)
        K_STYLE = 0;
        K_C[0] = hexc(0x020203);
        K_C[1] = hexc(0x1a1a20);
        K_C[2] = hexc(0x50545e);
        K_C[3] = hexc(0xc0c8d8);
        K_F.x = 0.05;
        K_C[4] = vec3(9.1, 33.3, 2.2);
        K_GRIP = 1; K_GD = hexc(0x000000); K_GL = hexc(0x010101); K_GA = hexc(0xc0c8d8); K_GG = 0.0;
        break;
    case 355:   // mercy | Steel Worn (Classique)
        K_STYLE = 2;
        K_C[0] = hexc(0x5a6a7a);
        K_C[1] = hexc(0xd0d4da);
        K_GRIP = 1; K_GD = hexc(0x101315); K_GL = hexc(0x313a43); K_GA = hexc(0xd0d4da); K_GG = 0.0;
        break;
    case 356:   // mercy | Celestial Shard (Raffiné)
        K_STYLE = 12;
        K_C[0] = hexc(0xd8e4f0);
        K_C[1] = hexc(0x8ab0d8);
        K_C[2] = hexc(0xf0d8a0);
        K_GRIP = 1; K_GD = hexc(0x26292b); K_GL = hexc(0x767d84); K_GA = hexc(0xf0d8a0); K_GG = 0.0;
        break;
    case 359:   // mercy | Rose Quartz (Ultime)
        K_STYLE = 0;
        K_C[0] = hexc(0x2a0614);
        K_C[1] = hexc(0xa03a6a);
        K_C[2] = hexc(0xf08ab8);
        K_C[3] = hexc(0xffd8ea);
        K_F.x = 0.1;
        K_C[4] = vec3(31.7, 8.8, 15.5);
        K_GRIP = 1; K_GD = hexc(0x070103); K_GL = hexc(0x17030b); K_GA = hexc(0xf08ab8); K_GG = 0.0;
        break;
    case 360:   // sea_gleam | Coral Worn (Classique)
        K_STYLE = 2;
        K_C[0] = hexc(0xd86a50);
        K_C[1] = hexc(0xb8c0c8);
        K_GRIP = 1; K_GD = hexc(0x26130e); K_GL = hexc(0x763a2c); K_GA = hexc(0xd86a50); K_GG = 0.0;
        break;
    case 362:   // sea_gleam | Deep Hammered (Classique)
        K_STYLE = 15;
        K_C[0] = hexc(0x3a5a7a);
        K_GRIP = 1; K_GD = hexc(0x0a1015); K_GL = hexc(0x1f3143); K_GA = hexc(0x3a5a7a); K_GG = 0.0;
        break;
    case 363:   // sea_gleam | Reef Splatter (Raffiné)
        K_STYLE = 27;
        K_C[0] = hexc(0x0e2a3a);
        K_C[1] = hexc(0xf08a50);
        K_C[2] = hexc(0x40e0d0);
        K_GRIP = 1; K_GD = hexc(0x02070a); K_GL = hexc(0x07171f); K_GA = hexc(0x40e0d0); K_GG = 0.0;
        break;
    case 365:   // sea_gleam | Tidal Marble (Prestige)
        K_STYLE = 18;
        K_C[0] = hexc(0x0a4a8a);
        K_C[1] = hexc(0xf0f4f8);
        K_C[2] = hexc(0x30b0c0);
        K_C[3] = hexc(0x02101c);
        K_GRIP = 1; K_GD = hexc(0x010d18); K_GL = hexc(0x05284b); K_GA = hexc(0x30b0c0); K_GG = 0.0;
        break;
    case 366:   // sea_gleam | Aquamarine (Ultime)
        K_STYLE = 0;
        K_C[0] = hexc(0x002a2e);
        K_C[1] = hexc(0x008a90);
        K_C[2] = hexc(0x30e0d8);
        K_C[3] = hexc(0xc0fff8);
        K_F.x = 0.12;
        K_C[4] = vec3(14.4, 6.6, 28.1);
        K_GRIP = 1; K_GD = hexc(0x000708); K_GL = hexc(0x001719); K_GA = hexc(0x30e0d8); K_GG = 0.0;
        break;
    case 368:   // baseball_bat | Walnut (Classique)
        K_STYLE = 36;
        K_C[0] = hexc(0x8a5a34);
        K_C[1] = hexc(0x3a2214);
        K_GRIP = 1; K_GD = hexc(0x181009); K_GL = hexc(0x4b311c); K_GA = hexc(0x8a5a34); K_GG = 0.0;
        break;
    case 371:   // baseball_bat | Graphite (Classique)
        K_STYLE = 7;
        K_C[0] = hexc(0x34363a);
        K_GRIP = 1; K_GD = hexc(0x09090a); K_GL = hexc(0x1c1d1f); K_GA = hexc(0x34363a); K_GG = 0.0;
        break;
    case 373:   // baseball_bat | Slugger Stripes (Raffiné)
        K_STYLE = 30;
        K_C[0] = hexc(0xe8e8e4);
        K_C[1] = hexc(0xc01820);
        K_F.x = 6.0;
        K_GRIP = 1; K_GD = hexc(0x292929); K_GL = hexc(0x7f7f7d); K_GA = hexc(0xc01820); K_GG = 0.0;
        break;
    case 374:   // baseball_bat | Graffiti (Raffiné)
        K_STYLE = 27;
        K_C[0] = hexc(0x1a1a1c);
        K_C[1] = hexc(0xf0e020);
        K_C[2] = hexc(0x20c0f0);
        K_GRIP = 1; K_GD = hexc(0x040405); K_GL = hexc(0x0e0e0f); K_GA = hexc(0xf0e020); K_GG = 0.0;
        break;
    case 376:   // baseball_bat | Tempered Alloy (Raffiné)
        K_STYLE = 31;
        K_C[0] = hexc(0xd8c890);
        K_C[1] = hexc(0xc07a40);
        K_C[2] = hexc(0x7a3ab0);
        K_C[3] = hexc(0x2a60e0);
        K_GRIP = 1; K_GD = hexc(0x262419); K_GL = hexc(0x766e4f); K_GA = hexc(0x2a60e0); K_GG = 0.0;
        break;
    case 377:   // baseball_bat | Midnight Fade (Prestige)
        K_STYLE = 13;
        K_C[0] = hexc(0x10103a);
        K_C[1] = hexc(0x6a2ab8);
        K_C[2] = hexc(0xe060c0);
        K_GRIP = 1; K_GD = hexc(0x02020a); K_GL = hexc(0x08081f); K_GA = hexc(0xe060c0); K_GG = 0.0;
        break;
    case 378:   // baseball_bat | Red Crackle (Prestige)
        K_STYLE = 16;
        K_C[0] = hexc(0x8a1018);
        K_C[1] = hexc(0xf0c050);
        K_GRIP = 1; K_GD = hexc(0x180204); K_GL = hexc(0x4b080d); K_GA = hexc(0xf0c050); K_GG = 0.0;
        break;
    case 379:   // baseball_bat | Molten Steel (Ultime)
        K_STYLE = 19;
        K_C[0] = hexc(0xc8ccd4);
        K_C[1] = hexc(0x80c0ff);
        K_GRIP = 1; K_GD = hexc(0x242426); K_GL = hexc(0x6e7074); K_GA = hexc(0x80c0ff); K_GG = 0.0;
        break;
    case 394:   // ct_knife | Ranger Digital (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0x5a6a4a);
        K_C[1] = hexc(0x3a4a30);
        K_C[2] = hexc(0x8a9a6a);
        K_C[3] = hexc(0x1e2618);
        K_F.x = 7.0;
        K_F.y = 1.0;
        K_GRIP = 1; K_GD = hexc(0x10130d); K_GL = hexc(0x313a28); K_GA = hexc(0x8a9a6a); K_GG = 0.0;
        break;
    case 395:   // ct_knife | Blue Satin (Classique)
        K_STYLE = 25;
        K_C[0] = hexc(0x4a6aa8);
        K_F.x = 1.0;
        K_GRIP = 1; K_GD = hexc(0x0d131e); K_GL = hexc(0x283a5c); K_GA = hexc(0x4a6aa8); K_GG = 0.0;
        break;
    case 397:   // ct_knife | Navy Worn (Classique)
        K_STYLE = 2;
        K_C[0] = hexc(0x1e2a44);
        K_C[1] = hexc(0xb0b6be);
        K_GRIP = 1; K_GD = hexc(0x05070c); K_GL = hexc(0x101725); K_GA = hexc(0xb0b6be); K_GG = 0.0;
        break;
    case 399:   // ct_knife | Steel Blue (Classique)
        K_STYLE = 7;
        K_C[0] = hexc(0x3a5070);
        K_GRIP = 1; K_GD = hexc(0x0a0e14); K_GL = hexc(0x1f2c3d); K_GA = hexc(0x3a5070); K_GG = 0.0;
        break;
    case 400:   // ct_knife | Cobalt Damascus (Raffiné)
        K_STYLE = 9;
        K_C[0] = hexc(0x8ab0f0);
        K_C[1] = hexc(0x0e1a3a);
        K_F.x = 100.0;
        K_GRIP = 1; K_GD = hexc(0x181f2b); K_GL = hexc(0x4b6084); K_GA = hexc(0x8ab0f0); K_GG = 0.0;
        break;
    case 401:   // ct_knife | Ice Shard (Raffiné)
        K_STYLE = 12;
        K_C[0] = hexc(0x8ab8e8);
        K_C[1] = hexc(0xd8ecff);
        K_C[2] = hexc(0x3a6ab8);
        K_GRIP = 1; K_GD = hexc(0x182129); K_GL = hexc(0x4b657f); K_GA = hexc(0x3a6ab8); K_GG = 0.0;
        break;
    case 402:   // ct_knife | Blue Stripe (Raffiné)
        K_STYLE = 30;
        K_C[0] = hexc(0x0e1428);
        K_C[1] = hexc(0x2a5ad8);
        K_F.x = 8.0;
        K_GRIP = 1; K_GD = hexc(0x020307); K_GL = hexc(0x070b16); K_GA = hexc(0x2a5ad8); K_GG = 0.0;
        break;
    case 403:   // ct_knife | Blue Circuit (Prestige)
        K_STYLE = 8;
        K_C[0] = hexc(0x080e1a);
        K_C[1] = hexc(0x2a5a9a);
        K_C[2] = hexc(0x40c0ff);
        K_GRIP = 1; K_GD = hexc(0x010204); K_GL = hexc(0x04070e); K_GA = hexc(0x40c0ff); K_GG = 0.0;
        break;
    case 404:   // ct_knife | Glacier Marble (Prestige)
        K_STYLE = 18;
        K_C[0] = hexc(0x2a6ad8);
        K_C[1] = hexc(0xf0f6ff);
        K_C[2] = hexc(0x8ac8f0);
        K_C[3] = hexc(0x04102a);
        K_GRIP = 1; K_GD = hexc(0x071326); K_GL = hexc(0x173a76); K_GA = hexc(0x2a6ad8); K_GG = 0.0;
        break;
    case 406:   // ct_knife | Molten Silver (Ultime)
        K_STYLE = 19;
        K_C[0] = hexc(0xd8dee8);
        K_C[1] = hexc(0x80c0ff);
        K_GRIP = 1; K_GD = hexc(0x262729); K_GL = hexc(0x767a7f); K_GA = hexc(0x80c0ff); K_GG = 0.0;
        break;
    case 407:   // t_knife | Desert Digital (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0xc8a878);
        K_C[1] = hexc(0x9a7a4a);
        K_C[2] = hexc(0x7a5a34);
        K_C[3] = hexc(0xe0cca0);
        K_F.x = 6.0;
        K_F.y = 1.0;
        K_GRIP = 1; K_GD = hexc(0x241e15); K_GL = hexc(0x6e5c42); K_GA = hexc(0xc8a878); K_GG = 0.0;
        break;
    case 409:   // t_knife | Red Worn (Classique)
        K_STYLE = 2;
        K_C[0] = hexc(0x8a1a14);
        K_C[1] = hexc(0xb8bcc2);
        K_GRIP = 1; K_GD = hexc(0x180403); K_GL = hexc(0x4b0e0b); K_GA = hexc(0x8a1a14); K_GG = 0.0;
        break;
    case 413:   // t_knife | Sunburst (Raffiné)
        K_STYLE = 12;
        K_C[0] = hexc(0xf0a030);
        K_C[1] = hexc(0xe05a20);
        K_C[2] = hexc(0xffd060);
        K_GRIP = 1; K_GD = hexc(0x2b1c08); K_GL = hexc(0x84581a); K_GA = hexc(0xf0a030); K_GG = 0.0;
        break;
    case 415:   // t_knife | Red Splatter (Raffiné)
        K_STYLE = 27;
        K_C[0] = hexc(0xd8d0c0);
        K_C[1] = hexc(0xc01818);
        K_C[2] = hexc(0x101010);
        K_GRIP = 1; K_GD = hexc(0x262522); K_GL = hexc(0x767269); K_GA = hexc(0xc01818); K_GG = 0.0;
        break;
    case 417:   // t_knife | Inferno Circuit (Prestige)
        K_STYLE = 8;
        K_C[0] = hexc(0x140806);
        K_C[1] = hexc(0x8a2a10);
        K_C[2] = hexc(0xff6020);
        K_GRIP = 1; K_GD = hexc(0x030101); K_GL = hexc(0x0b0403); K_GA = hexc(0xff6020); K_GG = 0.0;
        break;
    case 418:   // t_knife | Desert Sakura (Prestige)
        K_STYLE = 24;
        K_C[0] = hexc(0x2a1a10);
        K_C[1] = hexc(0xf0b080);
        K_C[2] = hexc(0x0e0806);
        K_GRIP = 1; K_GD = hexc(0x070402); K_GL = hexc(0x170e08); K_GA = hexc(0xf0b080); K_GG = 0.0;
        break;
    case 421:   // cs2_skeleton | Stained (Raffiné)
        K_STYLE = 31;
        K_C[0] = hexc(0xa89880);
        K_C[1] = hexc(0x7a5a8a);
        K_C[2] = hexc(0x4a3a80);
        K_C[3] = hexc(0x2a2c4a);
        break;
    case 422:   // cs2_skeleton | Boreal Forest (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0x6a7a52);
        K_C[1] = hexc(0x3a4a2e);
        K_C[2] = hexc(0x9a9a72);
        K_C[3] = hexc(0x22281a);
        K_F.x = 6.0;
        K_F.y = 0.0;
        break;
    case 423:   // cs2_skeleton | Black Laminate (Raffiné)
        K_STYLE = 36;
        K_C[0] = hexc(0x5a3a30);
        K_C[1] = hexc(0x120c0c);
        break;
    case 424:   // cs2_skeleton | Ossuary (Prestige)
        K_STYLE = 11;
        K_C[0] = hexc(0xe4dcc8);
        K_C[1] = hexc(0x1a1412);
        break;
    case 425:   // cs2_skeleton | Volt (Prestige)
        K_STYLE = 10;
        K_C[0] = hexc(0x0c0e14);
        K_C[1] = hexc(0x40c8ff);
        break;
    case 427:   // cs2_push | Blued Steel (Raffiné)
        K_STYLE = 31;
        K_C[0] = hexc(0xb0a070);
        K_C[1] = hexc(0x6a5aa0);
        K_C[2] = hexc(0x2a4ad0);
        K_C[3] = hexc(0x1a2a6a);
        break;
    case 428:   // cs2_push | Arctic Camo (Classique)
        K_STYLE = 3;
        K_C[0] = hexc(0xeef2f6);
        K_C[1] = hexc(0xaab4c0);
        K_C[2] = hexc(0x6a7684);
        K_C[3] = hexc(0xd0d8e0);
        K_F.x = 7.0;
        K_F.y = 1.0;
        break;
    case 429:   // cs2_push | Damascus Steel (Raffiné)
        K_STYLE = 9;
        K_C[0] = hexc(0xd8dce2);
        K_C[1] = hexc(0x2e3238);
        K_F.x = 110.0;
        break;
    case 430:   // cs2_push | Rust Veil (Classique)
        K_STYLE = 23;
        K_C[0] = hexc(0x6a6e74);
        K_C[1] = hexc(0xa8501c);
        break;
    case 431:   // cs2_stiletto | Nero Marble (Prestige)
        K_STYLE = 18;
        K_C[0] = hexc(0x0e0e10);
        K_C[1] = hexc(0x2a2a2e);
        K_C[2] = hexc(0xe8e4dc);
        K_C[3] = hexc(0xd8b050);
        break;
    case 432:   // cs2_stiletto | Vendetta (Prestige)
        K_STYLE = 16;
        K_C[0] = hexc(0x1a181a);
        K_C[1] = hexc(0xe01c2c);
        break;
    case 433:   // cs2_stiletto | Rosso (Raffiné)
        K_STYLE = 34;
        K_C[0] = hexc(0xc0141e);
        K_C[1] = hexc(0x121214);
        K_C[2] = hexc(0xe8e4dc);
        break;
    case 434:   // cs2_stiletto | Mirage (Raffiné)
        K_STYLE = 1;
        K_C[0] = hexc(0x8a3ae0);
        K_C[1] = hexc(0x20d0c8);
        break;
    case 436:   // cs2_skeleton | Case Hardened (Ultime)
        K_STYLE = 6;
        K_C[0] = hexc(0x2a5ad8);
        K_C[1] = hexc(0xd8a840);
        K_C[2] = hexc(0xa8b0b8);
        break;
    }
}
vec3 skinCouteau(vec3 p, out vec3 em) {
    em = vec3(0.0);
    if (K_STYLE == 0) return gemDoppler(p, K_C[0], K_C[1], K_C[2], K_C[3], K_F.x, K_C[4], em);
    if (K_STYLE == 1) return kAnodFade(p, K_C[0], K_C[1], em);
    if (K_STYLE == 2) return kBattleWorn(p, K_C[0], K_C[1], em);
    if (K_STYLE == 3) return kCamo(p, K_C[0], K_C[1], K_C[2], K_C[3], K_F.x, K_F.y, em);
    if (K_STYLE == 4) return kCarbon(p, K_C[0], em);
    if (K_STYLE == 5) return kCaseHard(p, K_C[0], K_C[1], K_C[2], K_C[3], em);
    if (K_STYLE == 6) return kCaseHardCS(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 7) return kCerakote(p, K_C[0], em);
    if (K_STYLE == 8) return kCircuit(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 9) return kDamascus(p, K_C[0], K_C[1], K_F.x, em);
    if (K_STYLE == 10) return kElectric(p, K_C[0], K_C[1], em);
    if (K_STYLE == 11) return kEngraved(p, K_C[0], K_C[1], em);
    if (K_STYLE == 12) return kFacets(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 13) return kFade(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 14) return kFreehand(p, K_C[0], K_C[1], K_C[2], K_C[3], em);
    if (K_STYLE == 15) return kHammered(p, K_C[0], em);
    if (K_STYLE == 16) return kKintsugi(p, K_C[0], K_C[1], em);
    if (K_STYLE == 17) return kLeopard(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 18) return kMarble(p, K_C[0], K_C[1], K_C[2], K_C[3], em);
    if (K_STYLE == 19) return kMolten(p, K_C[0], K_C[1], em);
    if (K_STYLE == 20) return kNightOps(p, K_C[0], K_C[1], em);
    if (K_STYLE == 21) return kPatina(p, K_C[0], K_C[1], em);
    if (K_STYLE == 22) return kPrism(p, K_C[0], em);
    if (K_STYLE == 23) return kRustCoat(p, K_C[0], K_C[1], em);
    if (K_STYLE == 24) return kSakura(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 25) return kSatin(p, K_C[0], K_F.x, em);
    if (K_STYLE == 26) return kScorched(p, K_C[0], K_C[1], em);
    if (K_STYLE == 27) return kSplatter(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 28) return kSpray(p, K_C[0], K_C[1], em);
    if (K_STYLE == 29) return kStonewash(p, K_C[0], K_C[1], em);
    if (K_STYLE == 30) return kStripes(p, K_C[0], K_C[1], K_F.x, em);
    if (K_STYLE == 31) return kTempered(p, K_C[0], K_C[1], K_C[2], K_C[3], em);
    if (K_STYLE == 32) return kTigerStripe(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 33) return kTitanium(p, em);
    if (K_STYLE == 34) return kTwoTone(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 35) return kWeb(p, K_C[0], K_C[1], K_C[2], em);
    if (K_STYLE == 36) return kWood(p, K_C[0], K_C[1], em);
    return vec3(0.5);
}
vec3 skinGrip(vec3 p, out vec3 em) {
    if (K_GRIP == 1) return gripWrap(p, K_GD, K_GL, K_GA, K_GG, em);
    em = vec3(0.0);
    return rubberGrip(p);
}
#endif
// </skins_couteaux>

// Manche orné (Lame de Desmoulins…) : manche noir ; les motifs colorés de la texture d'origine (étoile, pierres, filets
// bleus) prennent la finition de la lame (motif), le reste (plaques argentées comprises) reste noir
vec3 ornateHandle(vec3 p, vec3 motif) {
    float lum = dot(BASE_COL, vec3(0.299, 0.587, 0.114));
    float sat = max(max(BASE_COL.r, BASE_COL.g), BASE_COL.b) - min(min(BASE_COL.r, BASE_COL.g), BASE_COL.b);
    vec3 dark = metalShade(p, mix(hexc(0x0a0a0c), hexc(0x17181b), fbm(p * 40.0, 2)), 0.0, 0.4);
    float m = smoothstep(0.12, 0.26, sat);
    return mix(dark, motif * (0.8 + 0.4 * lum), m);
}
vec3 knifeBlade(vec3 p, out vec3 em) {
    vec3 col;
#ifdef SKIN_PALIERS
    col = skinCouteau(p, em);
#elif CAMO_ID >= 100
    col = uniqueFinish(p, em);
#elif CAMO_ID >= 59 && CAMO_ID <= 66
    col = csFinish(p, em);
#elif CAMO_ID == 55
    // Doppler Ruby : rouge sang profond, nuages rubis, volutes presque noires
    col = gemDoppler(p, hexc(0x2a0006), hexc(0xa0000c), hexc(0xf0102c), hexc(0xff9aa6), 0.12, vec3(41.2, 7.7, 1.9), em);
#elif CAMO_ID == 56
    // Doppler Sapphire : bleu roi profond, nuages bleu électrique, volutes marine
    col = gemDoppler(p, hexc(0x00082e), hexc(0x0028b0), hexc(0x1a62ff), hexc(0xa8ccff), 0.12, vec3(3.3, 29.1, 17.4), em);
#elif CAMO_ID == 57
    // Doppler Black Pearl : violet presque noir, nuages bleu-violet nacrés
    col = gemDoppler(p, hexc(0x04010a), hexc(0x1c0c40), hexc(0x5a3cc0), hexc(0xb8a8ff), 0.08, vec3(61.5, 11.3, 23.8), em);
#else
    // Gamma Doppler Emerald : vert émeraude profond et saturé, nuages vert vif, volutes vert sombre (image de référence)
    col = gemDoppler(p, hexc(0x002a0e), hexc(0x009a3c), hexc(0x12e064), hexc(0xb0ffc8), 0.12, vec3(13.7, 2.1, 5.3), em);
#endif
    return col;
}

// Corde tressée (paracorde) enroulée en spires serrées autour du manche, teinte prise sur la lame (couteau Skeleton)
vec3 cordeWrap(vec3 p, vec3 teinte) {
    float mx = max(max(teinte.r, teinte.g), teinte.b);
    vec3 c = teinte / max(mx, 0.05) * mix(0.30, 0.55, clamp(mx * 1.6, 0.0, 1.0));
    float t = p.x * 62.0;
    float f = fract(t);
    float rond = sin(f * 3.14159);                                      // profil rond de chaque spire
    float tresse = fract(p.x * 248.0 + (p.y + p.z) * 210.0 * (mod(floor(t), 2.0) * 2.0 - 1.0));
    float fil = 0.78 + 0.22 * smoothstep(0.0, 0.5, tresse) * smoothstep(1.0, 0.5, tresse);
    vec3 col = c * (0.28 + 0.72 * rond) * fil * (0.94 + 0.06 * vnoise(p * 400.0));
    col = mix(col * 0.35, col, smoothstep(0.0, 0.12, min(f, 1.0 - f)));   // creux sombre entre deux spires
    return metalShade(p, col, 0.0, 0.12);
}

// Manche du Push : noir mat uniforme (choix du propriétaire), très léger brossage ; même teinte sur toutes les faces
// (la texture d'origine changeait de gris d'une face à l'autre)
vec3 acierManche(vec3 p) {
    float brosse = fbm(vec3(p.x * 6.0, p.y * 520.0, p.z * 520.0), 2);
    vec3 col = hexc(0x1e1f22) * (0.95 + 0.06 * brosse);
    return metalShade(p, col, 0.0, 0.18);
}

vec3 knifeFinish(vec3 p, out vec3 em) {
    em = vec3(0.0);
#ifdef SKIN_PALIERS
    skinParams();                                                                   // couleurs du skin dessiné (CamoId)
#endif
    bool orne = abs(PART - 0.68) < 0.02;                                            // manche orné : motifs sur fond noir
    if (orne) {
        PART = 1.0;
    }
    if (abs(PART - 0.82) < 0.02) {                                                  // corde tressée (Skeleton)
        vec3 e2;
        float garde = PART;
        PART = 1.0;
        vec3 teinte = knifeBlade(vec3(-0.15, 0.0, 0.0), e2);
        PART = garde;
        return cordeWrap(p, teinte);
    }
    if (abs(PART - 0.56) < 0.02) {                                                  // manche noir mat (Push)
        em = vec3(0.0);
        return acierManche(p);
    }
    if (PART > 0.62 && PART <= 0.9) {                                                // manche caoutchouc (Karambit, M9)
#ifdef SKIN_PALIERS
        return skinGrip(p, em);                                                     // skins par paliers : manche du catalogue
#elif CAMO_ID >= 100
        return uniqueHandle(p, em);                                                 // finitions exclusives : manche assorti
#else
        return rubberGrip(p);
#endif
    }
    if (PART > 0.15 && PART <= 0.28) {                                               // manche métal noir (Butterfly)
        vec3 dark = mix(hexc(0x0c0d10), hexc(0x1e2025), fbm(vec3(p.x * 30.0, p.y * 300.0, 1.0), 2));
        return metalShade(p, dark, 0.0, 0.35);
    }
    if (PART > 0.36 && PART <= 0.46) {                                               // métal noir poli : pivots, loquet, anneau du Karambit
        return metalShade(p, mix(hexc(0x0d0e11), hexc(0x1a1c21), fbm(p * 40.0, 2)), 0.0, 0.55);
    }
#ifdef SKIN_PALIERS
    vec3 inlay = K_INLAY;
#elif CAMO_ID == 100
    vec3 inlay = hexc(0x1a1b1d);
#elif CAMO_ID == 59
    vec3 inlay = hexc(0x6a34c0);
#elif CAMO_ID == 60
    vec3 inlay = hexc(0x9a1020);
#elif CAMO_ID == 61
    vec3 inlay = hexc(0xb05e0c);
#elif CAMO_ID == 62
    vec3 inlay = hexc(0x5a0610);
#elif CAMO_ID == 63
    vec3 inlay = hexc(0x8e1018);
#elif CAMO_ID == 64
    vec3 inlay = hexc(0x1a44a8);
#elif CAMO_ID == 65
    vec3 inlay = hexc(0x7a5a22);
#elif CAMO_ID == 66
    vec3 inlay = hexc(0x7a0e12);
#elif CAMO_ID == 55
    vec3 inlay = hexc(0x620e1a);
#elif CAMO_ID == 56
    vec3 inlay = hexc(0x103074);
#elif CAMO_ID == 57
    vec3 inlay = hexc(0x2c1c56);
#else
    vec3 inlay = hexc(0x145c42);
#endif
    if (PART > 0.28 && PART <= 0.36) {                                               // insert du manche : teinte plus sombre, lisse, satinée
        return metalShade(p, inlay * (0.92 + 0.08 * fbm(p * 12.0, 2)), 0.7, 0.55);
    }
    float metal = 1.0;                                                              // garde du M9, anneau du Karambit : même finition que la lame
    vec3 col;
    col = knifeBlade(p, em);
    if (orne) {
        em = vec3(0.0);                                                             // la lueur des gemmes reste sur la lame
        return ornateHandle(p, col);
    }
    return col * metal;
}

// ================================================================== skins X (200+) : surfaces sous les effets 3D
// champ d'étoiles rondes (sphères 3D coupées par la surface -> points ronds de tailles variées), scintillement
float sxStars(vec3 p, float scale, float thr, float t) {
    vec3 g = p * scale;
    vec3 c = floor(g);
    float h = hash(c + 17.0);
    vec3 o = (hash3(c) - 0.5) * 0.5;
    float d = length(fract(g) - 0.5 - o);
    float tw = 0.55 + 0.45 * sin(t * (1.5 + 3.0 * h) + h * 40.0);
    return step(thr, h) * (smoothstep(0.22, 0.0, d) + 0.35 * smoothstep(0.5, 0.0, d) * step(0.995, h)) * tw;
}

// ligne de niveau v = c d'épaisseur constante wP (unités de l'arme), quelle que soit la pente du bruit
float isoLine(float v, float c, float wP, vec3 p) {
    float g = length(vec2(dFdx(v), dFdy(v))) / max(length(vec2(length(dFdx(p)), length(dFdy(p)))), 1e-7);
    return smoothstep(wP, wP * 0.35, abs(v - c) / max(g, 1e-3));
}

// vagues seigaiha : arcs concentriques en écailles (rangées décalées, la rangée du bas passe devant)
float seigaiha(vec2 p, out float ring) {
    ring = 1.0;
    float j0 = floor(p.y * 2.0);
    for (int dj = -1; dj <= 2; dj++) {
        float j = j0 + float(dj);
        float off = 0.5 * mod(j, 2.0);
        float cx = floor(p.x - off + 0.5) + off;
        vec2 c = vec2(cx, j * 0.5);
        float d = length(p - c) / 0.5;
        if (d < 1.0 && p.y >= c.y - 0.02) {
            ring = d;
            return 1.0;
        }
    }
    return 0.0;
}

// écailles de reptile en quinconce (bord = 1)
float scaleEdge(vec2 q) {
    float row = floor(q.x);
    vec2 f = vec2(fract(q.x), fract(q.y + 0.5 * mod(row, 2.0)));
    float d = length(vec2(f.x, (f.y - 0.5) * 1.3));
    return smoothstep(0.07, 0.0, abs(d - 0.82));
}





// 208 Séraphin : marbre de Carrare, veines grises fines, larges veines d'or poli qui scintillent, reflet nacré
vec3 seraphMarble(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * vec3(4.0, 8.0, 8.0);
    vec3 w0 = vec3(fbm(q, 3), fbm(q + 3.1, 3), 0.0);
    float w = fbm(q + 2.0 * w0, 5);
    float w2 = fbm(q * 2.3 + 5.0 + w0, 4);
    float grey = isoLine(w2, 0.48, 0.0035, p) * 0.6 + isoLine(fbm(q * 4.1 + 2.0, 3), 0.5, 0.0018, p) * 0.35;
    float gold = isoLine(w, 0.5, 0.0055, p);
    float goldFine = max(isoLine(w, 0.585, 0.0025, p), isoLine(w2, 0.56, 0.003, p)) * 0.85;
    float g = clamp(gold + goldFine, 0.0, 1.0);
    vec3 col = mix(hexc(0xe8e4dd), hexc(0xfdfcf9), fbm(p * 22.0, 3));
    col = mix(col, hexc(0x8a8580), grey);
    vec3 nv = normalize(viewNormal);
    vec3 env = studio(nv);
    float shine = 0.55 + 0.45 * sin(p.x * 9.0 - t * 1.1 + w * 6.0);
    vec3 gm = mix(hexc(0x9a6a18), hexc(0xffdf8a), shine) * (0.7 + 0.4 * env.g);
    vec3 cell = floor(p * 300.0);
    float spark = step(0.97, hash(cell)) * (0.5 + 0.5 * sin(t * 4.0 + hash(cell + 3.0) * 30.0));
    col = mix(col, gm, g);
    float fr = pow(1.0 - clamp(abs(nv.z), 0.0, 1.0), 2.0);
    col += spectrum(fr * 0.6 + p.x) * fr * 0.1 * (1.0 - g);
    em = hexc(0xffd56a) * g * (0.12 * shine + spark * 0.8);
    return lacquer(col, 1.4);
}

// 209 Enfer : fer noirci, braises sous la surface, fissures incandescentes qui respirent
vec3 infernalIron(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 q = p * 11.0;
    float f1, f2; vec3 wc, wp;
    worley(q, f1, f2, wc, wp);
    float crack = smoothstep(0.06, 0.0, f2 - f1);
    float heat = 0.5 + 0.5 * sin(t * 1.6 + hash(wc) * 6.2832 + p.x * 4.0);
    vec3 col = mix(hexc(0x0b0908), hexc(0x231a16), fbm(p * 20.0, 3));
    vec3 fire = mix(hexc(0xff2a00), hexc(0xffb030), heat);
    col = mix(col, fire * 0.5, crack);
    em = fire * crack * (0.4 + 0.8 * heat);
    return col + studio(normalize(viewNormal)) * 0.05;
}

#if CAMO_ID == 210 || CAMO_ID == 211
// 210 Phénix et 211 Abysses (19/11) : surfaces écrites plus bas avec les nouveaux camos du 19/11, après les reflets liés au
// monde (envWorld, reflWorld…) qu'elles lisent
vec3 phenix(vec3 p, out vec3 em);
vec3 abysses(vec3 p, out vec3 em);
#endif
#if CAMO_ID == 212
// 212 Prisme (2e lot du 19/11) : surface écrite plus bas, avec le 2e lot
vec3 l2Prisme(vec3 p, out vec3 em);
#endif

vec3 skinSurface(vec3 p, out vec3 em) {
    em = vec3(0.0);
#if CAMO_ID == 208
    return seraphMarble(p, em);
#elif CAMO_ID == 209
    return infernalIron(p, em);
#elif CAMO_ID == 210
    return phenix(p, em);
#elif CAMO_ID == 211
    return abysses(p, em);
#elif CAMO_ID == 212
    return l2Prisme(p, em);
#else
    return darkMatter(p, em);
#endif
}

// ================================================================== refonte 2026-10 : Gold, Diamond, Obsidian, Liquid Chrome,
// Amethyst et couleurs simples. Deux nouveautés, réservées aux camos CAMO_RELIEF :
// - le relief de l'arme : la texture d'origine sert de carte d'ombrage (gravures, vis, rails, arêtes) ; RELIEF = 1 au ton
//   moyen du voisinage, < 1 dans les creux sombres, > 1 sur les arêtes claires ;
// - des reflets liés au monde : le décor reflété (ciel, horizon, panneaux lumineux, soleil) est fixé dans le monde
//   (IViewRotMat) et vu en perspective (viewPos) : il glisse sur l'arme quand le joueur se retourne ou lève la tête.
const float PI = 3.14159265;
float RELIEF = 1.0;
// part de l'ombrage fixe des entités (deux lumières de Minecraft, vertexColor) retirée par le camo : un métal très poli
// vit de ses reflets, pas de cet ombrage ; la lumière du monde (lightmap, nuit, grottes) reste entière
float NO_SHADE = 0.0;

float lum3(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

// texel voisin de la texture d'origine, pris dans le rectangle de la carte (sacs : leur place dans l'atlas) ;
// rend (luminance, 1) ou (0, 0) s'il est transparent. Niveau de détail fixe : lu dans une branche (pas de dérivées)
vec2 reliefTap(vec2 uv, vec2 d) {
    vec4 c = textureLod(Sampler0, clamp(uv + d, CamoUvRect.xy, CamoUvRect.xy + CamoUvRect.zw), 0.0);
    float a = step(0.1, c.a);
    return vec2(lum3(c.rgb) * a, a);
}

float baseRelief(vec4 base, vec2 uv) {
    vec2 ts = 1.0 / vec2(textureSize(Sampler0, 0));
    vec2 s = reliefTap(uv, vec2(2.0, 0.0) * ts) + reliefTap(uv, vec2(-2.0, 0.0) * ts)
           + reliefTap(uv, vec2(0.0, 2.0) * ts) + reliefTap(uv, vec2(0.0, -2.0) * ts)
           + reliefTap(uv, vec2(1.5, 1.5) * ts) + reliefTap(uv, vec2(-1.5, 1.5) * ts)
           + reliefTap(uv, vec2(1.5, -1.5) * ts) + reliefTap(uv, vec2(-1.5, -1.5) * ts);
    float l = lum3(base.rgb);
    float m = s.y > 0.5 ? s.x / s.y : l;
    // détail fin (lignes gravées, vis, arêtes) : écart au voisinage ; ombrage large (pièces claires / sombres) : atténué
    float fine = clamp((l + 0.025) / (m + 0.025), 0.28, 1.9);
    float broad = clamp(pow((m + 0.025) / 0.22, 0.2), 0.8, 1.25);
    return fine * broad;
}

vec3 eyeDir() {
    float l = length(viewPos);
    return l > 1e-6 ? viewPos / l : vec3(0.0, 0.0, -1.0);
}

// reflet de la direction regardée sur la normale n (repère de la vue), rendu dans le repère du monde
vec3 reflWorld(vec3 n) {
    return normalize(IViewRotMat * reflect(eyeDir(), n));
}

float facing(vec3 n) {
    return clamp(-dot(eyeDir(), n), 0.0, 1.0);
}

float softBox(float az, float el, float a0, float e0, float wa, float we, float soft) {
    float da = abs(mod(az - a0 + PI, TAU) - PI);
    return smoothstep(wa + soft, wa - soft, da) * smoothstep(we + soft, we - soft, abs(el - e0));
}

// décor reflété, direction r dans le repère du monde (y vers le haut) ; rough : 0 miroir net .. 1 reflet très flou.
// Ciel clair en dégradé, grands panneaux lumineux à azimuts fixes, ligne d'horizon soulignée d'une bande sombre (le
// « trait » des chromes), sol gris neutre plus ou moins éclairé selon la direction, soleil : les métaux ont des reflets
// contrastés dans toutes les directions, et ils changent quand le joueur se retourne
vec3 envWorld(vec3 r, float rough) {
    float el = asin(clamp(r.y, -1.0, 1.0));
    float az = atan(r.z, r.x);
    float b = 0.02 + 0.3 * rough;
    vec3 sky = mix(vec3(1.0, 0.98, 0.95), vec3(0.56, 0.62, 0.74), smoothstep(0.0, 1.1, el));
    float zone = 0.5 + 0.5 * sin(az * 3.0 + 1.0) * sin(az * 2.0 - 0.5 + el * 3.0);
    vec3 ground = vec3(0.30, 0.30, 0.32) * mix(0.45, 1.35, mix(zone, 0.5, rough));
    ground *= 1.0 - (0.72 - 0.5 * rough) * exp(-pow((el + 0.08) / (0.06 + 0.2 * rough), 2.0));     // bande sombre
    vec3 col = mix(ground, sky, smoothstep(-b, b, el));
    col += (1.2 - 0.9 * rough) * exp(-pow(el / (0.018 + 0.25 * rough), 2.0)) * vec3(1.0, 0.97, 0.92);  // ligne d'horizon
    float sb = 0.04 + 0.3 * rough;
    float panels = softBox(az, el, 0.6, 0.42, 0.30, 0.22, sb) + softBox(az, el, 2.4, 0.30, 0.22, 0.30, sb)
                 + softBox(az, el, -1.9, 0.62, 0.42, 0.14, sb) + softBox(az, el, -0.5, 0.16, 0.10, 0.12, sb)
                 + 0.6 * softBox(az, el, 1.5, -0.32, 0.5, 0.08, sb) + 0.6 * softBox(az, el, -2.6, -0.38, 0.35, 0.1, sb);
    col += panels * (2.0 - 1.4 * rough) * vec3(1.0, 0.99, 0.97);
    float sd = max(dot(r, normalize(vec3(0.42, 0.68, -0.6))), 0.0);
    col += pow(sd, mix(1500.0, 30.0, rough)) * mix(9.0, 1.2, rough) * vec3(1.0, 0.94, 0.82);           // soleil
    return col;
}

// relief « métal » : on ne garde que les vrais détails (gravures, arêtes), pas le grain fin de la texture
float reliefMetal(float k) {
    float d = RELIEF - 1.0;
    // creux (gravures, vis, jours) gardés ; points clairs isolés (rayures, grain) très atténués
    return 1.0 + k * d * smoothstep(0.2, 0.42, abs(d)) * (d < 0.0 ? 1.0 : 0.35);
}

// champ lisse dans le repère de l'arme (-0.5..0.5) : fausse courbure des surfaces polies
vec3 softWarp(vec3 p, float f) {
    return vec3(vnoise(p * f + vec3(1.3, 7.1, 3.3)), vnoise(p * f + vec3(8.2, 2.4, 5.9)), vnoise(p * f + vec3(4.6, 9.7, 0.8))) - 0.5;
}

// bosse de hauteur h (unités de l'arme : 1 = longueur de l'arme) sur la normale n, gradient pris sur les dérivées écran
// (Mikkelsen 2010) : aucun calcul de plus du motif
vec3 bumpNormal(vec3 n, float h, vec3 p) {
    vec3 sx = dFdx(viewPos);
    vec3 sy = dFdy(viewPos);
    vec3 r1 = cross(sy, n);
    vec3 r2 = cross(n, sx);
    float det = dot(sx, r1);
    float k = length(sx) / max(length(dFdx(p)), 1e-7);
    vec3 grad = sign(det) * (dFdx(h) * r1 + dFdy(h) * r2) * k;
    // pente bornée : sur une arête, le voisin d'écran tombe sur une autre face (saut de hauteur) -> pas de pixel aberrant
    float gmax = 1.5 * abs(det);
    float gl = length(grad);
    grad *= gl > gmax ? gmax / gl : 1.0;
    return normalize(abs(det) * n - grad);
}

// ================================================================== relief d'usinage des armes (2026-10-09)
// Les modèles TaCZ sont faits de blocs lisses. Ce relief s'ajoute sous tous les camos des armes, là où il tient sur ces
// modèles : grain régulier en petites bosses sur la poignée. Il penche la normale (reflets des métaux) et assombrit les
// creux (tous les camos), et s'efface de loin (pas de moiré). Hauteurs en unités de l'arme (1 = sa longueur).
// Essayé puis retiré : nervures des chargeurs (un chargeur est fait de plusieurs blocs penchés : les nervures se cassent
// aux jointures ou les traversent), grain d'usinage du corps (rayures sur les métaux).
// Parties de la carte : 1 corps, 0.985 poignée, 0.97 petites pièces, 0.94 carcasse, 0.75 chargeur, 0.5 canon (PosMap).
float DETAIL_SHADE = 1.0;

// axes de la face dans le repère de l'arme, tirés des coordonnées de texture (une face de bloc est plaquée le long de
// ses arêtes) : « long » = l'axe de la face le plus vertical dans l'arme, « travers » = l'autre. Dérivées écran.
void axesFace(vec3 p, out vec3 travers, out vec3 long_) {
    vec2 dtx = dFdx(texCoord0), dty = dFdy(texCoord0);
    vec3 dpx = dFdx(p), dpy = dFdy(p);
    float det = dtx.x * dty.y - dtx.y * dty.x;
    float inv = abs(det) > 1e-20 ? 1.0 / det : 0.0;
    vec3 du = (dpx * dty.y - dpy * dtx.y) * inv;
    vec3 dv = (dpy * dtx.x - dpx * dty.x) * inv;
    float lu = length(du), lv = length(dv);
    du = lu > 1e-9 ? du / lu : vec3(1.0, 0.0, 0.0);
    dv = lv > 1e-9 ? dv / lv : vec3(0.0, 1.0, 0.0);
    bool uLong = abs(du.y) > abs(dv.y);
    long_ = uLong ? du : dv;
    travers = uLong ? dv : du;
}

float reliefUsinage(vec3 p, vec3 N, float part, float px, out float creux) {
    vec3 a = abs(N);
    creux = 0.0;
    // poignée : grain régulier de petites bosses rondes (réseau hexagonal serré), mat
    if (abs(part - 0.985) < 0.006) {
        // s'efface avant que les bosses passent sous ~6 pixels (sinon moiré)
        float k = 1.0 - smoothstep(0.00045, 0.0009, px);
        if (k <= 0.0) return 0.0;
        vec3 tr, lg;
        axesFace(p, tr, lg);
        vec2 q = vec2(dot(p, tr), dot(p, lg)) * 380.0;
        vec2 S = vec2(1.0, 1.7320508);
        vec2 ga = mod(q, S) - 0.5 * S;
        vec2 gb = mod(q - 0.5 * S, S) - 0.5 * S;
        vec2 g = dot(ga, ga) < dot(gb, gb) ? ga : gb;
        float rr = length(g) / 0.42;
        float bosse = rr < 1.0 ? 1.0 - rr * rr : 0.0;
        creux = (1.0 - smoothstep(0.0, 0.35, bosse)) * k * 0.4;
        return 0.00012 * bosse * k;
    }
    return 0.0;
}

// relief d'usinage d'une arme (pas des couteaux ni des sacs) : normale penchée (VN_DETAIL) et ombre des creux
void appliquerRelief(vec3 p, float part) {
    if (CamoParts != 7 || CamoUvRect != vec4(0.0, 0.0, 1.0, 1.0)) return;
    vec3 N = faceNormal(p);
    float px = length(fwidth(p));
    float creux;
    float h = reliefUsinage(p, N, part, px, creux);
    vec3 n0 = VN_DETAIL;
    VN_DETAIL = bumpNormal(n0, h, p);
    // lumière des entités (repère du monde, venant d'en haut) : les flancs des nervures s'éclairent ou s'assombrissent
    vec3 lv = normalize(transpose(IViewRotMat) * normalize(vec3(0.2, 1.0, -0.7)));
    float dl = dot(VN_DETAIL, lv) - dot(n0, lv);
    DETAIL_SHADE = (1.0 - 0.28 * creux) * (1.0 + 0.9 * dl);
}

// ------------------------------------------------------------------ Or (Gold) : or poli façon Call of Duty
// reflets nets du décor lié au monde (ils glissent sur l'arme quand on se retourne), surfaces très légèrement bombées,
// relief de l'arme bien lisible ; plus de reflet qui balaie l'arme
vec3 goldCod(vec3 p, out vec3 em) {
    vec3 n = normalize(normalize(viewNormal) + 0.20 * softWarp(p, 2.2));
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 F0 = vec3(1.0, 0.76, 0.33);
    vec3 F = F0 + (vec3(1.0) - F0) * pow(1.0 - ndv, 5.0);
    // reflet net (vernis poli) + reflet adouci (corps du métal) : jamais tout noir, jamais plat
    vec3 env = envWorld(r, 0.04) * 0.5 + envWorld(r, 0.5) * 0.5;
    float brush = 0.96 + 0.04 * vnoise(vec3(p.x * 24.0, p.y * 700.0, p.z * 700.0));
    vec3 col = (env * F * brush * 1.1 + F0 * 0.2) * vec3(1.0, 0.98, 0.94);
    em = vec3(0.0);
    return col * reliefMetal(0.85);
}

// ------------------------------------------------------------------ Chrome liquide : mercure qui coule le long de l'arme
// grande houle lente qui file vers la bouche, vaguelettes, miroir chromé net (horizon, panneaux, soleil du décor)
vec3 liquidChromeCod(vec3 p, out vec3 em) {
    float t = CamoTime;
    // coulées de mercure étirées le long de l'arme, qui filent vers la bouche ; domaine tordu -> formes de liquide ;
    // vaguelettes parallèles qui suivent la coulée
    vec3 q = p * vec3(2.4, 7.0, 7.0);
    vec3 w = vec3(fbm(q * 0.8 + vec3(-t * 0.14, 0.0, 0.0), 3), fbm(q * 0.8 + vec3(3.1, -t * 0.07, 5.2), 3), 0.0);
    float swell = fbm(q + 1.6 * w + vec3(-t * 0.2, 0.0, t * 0.03), 4);
    float ripple = sin(p.x * 80.0 + swell * 5.0 + w.y * 9.0 - t * 2.6);
    float h = swell * 0.014 + ripple * 0.0010 * (0.3 + 0.7 * smoothstep(0.35, 0.7, swell));
    vec3 n = bumpNormal(normalize(viewNormal), h, p);
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 F0 = vec3(0.93, 0.94, 0.96);
    vec3 F = F0 + (vec3(1.0) - F0) * pow(1.0 - ndv, 5.0);
    vec3 col = envWorld(r, 0.012) * F + vec3(0.012);
    // légère irisation (film d'huile) au bord des coulées
    col += spectrum(fract(swell * 2.2 + r.y * 0.5 + t * 0.03)) * smoothstep(0.45, 0.6, swell) * smoothstep(0.75, 0.6, swell) * 0.07;
    em = vec3(0.0);
    return col * reliefMetal(0.7);
}

// ------------------------------------------------------------------ Obsidienne : verre volcanique noir poli
// éclats conchoïdaux (cuvettes aux arêtes vives et ondes concentriques) qui accrochent des reflets nets, reflet violet
// profond qui suit l'angle, paillettes violettes dans la masse
vec3 obsidianCod(vec3 p, out vec3 em) {
    float t = CamoTime;
    float f1, f2; vec3 cell, fp;
    vec3 q = p * vec3(6.0, 10.0, 10.0);
    worley(q, f1, f2, cell, fp);
    vec3 hc = hash3(cell);
    // éclat conchoïdal (verre taillé) : cuvette, ondes concentriques, arêtes vives entre éclats
    float ring = sin(f1 * 24.0 + hc.x * 6.0);
    float h = -0.016 * f1 * f1 + 0.0012 * ring * f1;
    vec3 n = bumpNormal(normalize(normalize(viewNormal) + 0.3 * (hc - 0.5)), h, p);
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    // verre noir très poli : noir profond, reflets nets et francs (contraste renforcé : le gris du décor s'efface,
    // les panneaux, l'horizon et le soleil restent)
    float F = 0.22 + 0.78 * pow(1.0 - ndv, 4.0);
    vec3 env = envWorld(r, 0.015);
    vec3 refl = env * env * 0.75;
    vec3 deep = mix(hexc(0x030206), hexc(0x110a1c), fbm(p * vec3(6.0, 10.0, 10.0), 3));
    vec3 col = deep + refl * F * vec3(0.9, 0.88, 1.0);
    // reflet violet profond (obsidienne de Minecraft) qui suit l'angle, paillettes violettes dans la masse
    float sheen = pow(1.0 - ndv, 2.0) * (0.5 + 0.5 * hc.y);
    col += hexc(0x4a1f86) * sheen * 0.28;
    vec3 sc = floor(p * 300.0);
    float speck = step(0.992, hash(sc)) * (0.5 + 0.5 * sin(t * 1.3 + hash(sc + 3.0) * 30.0));
    col += speck * hexc(0xb07cff) * 0.45;
    float edge = 1.0 - smoothstep(0.0, 0.025, f2 - f1);
    col += edge * hexc(0x8f6ad0) * 0.14;
    em = speck * hexc(0x9a60ff) * 0.25;
    return col * reliefMetal(0.8);
}

// ------------------------------------------------------------------ Améthyste : la même gemme que l'Emerald (Genesis,
// Gamma Doppler), en violet : nuages améthyste, volutes violet profond, couche en parallaxe, qui bouge
vec3 amethystGem(vec3 p, out vec3 em) {
    return gemDoppler(p, hexc(0x1a0030), hexc(0x6a1cc0), hexc(0xb05cff), hexc(0xe8ccff), 0.12, vec3(19.3, 8.7, 4.1), em);
}

// ------------------------------------------------------------------ Diamant façon Call of Duty (Black Ops 6, refait le 2026-10-09) :
// tout le corps de l'arme (carcasse, garde-main, crosse, organes de visée, rails) couvert d'un pavé de petits diamants
// blancs sertis en or blanc ; canon, chargeur, poignée et petites pièces rapportées (détente, sûreté, leviers, vis :
// PosMap.ACCENT) en or jaune poli. Pierres rondes taille brillant en relief réel (parallaxe : elles dépassent du serti) sur
// un réseau hexagonal régulier, facettes très contrastées (blanc / noir) avec un peu de feu, éclats en étoile qui
// s'allument quand le joueur bouge (décor lié au monde) ; de loin, le pavé se fond en un argent scintillant.
// Corrigé le 19/11 (en jeu : bruit de pixels en vue joueur, aplats et traînées par endroits), même pavé fin, même relief :
// - faces où la carte de positions ne peut pas suivre la surface (UV réduites à un point, UV prises par plusieurs faces de
//   formes différentes, faces ou bords de faces plus fins qu'un texel de la carte) : le pavé y est posé sur un repère de
//   secours (coordonnées de texture de la face redressées, à défaut la position dans la vue), mêmes pierres en relief, au
//   lieu d'un aplat ou de traînées ;
// - faces à 45° : plan du motif toujours le même (surf2d en changeait d'un pixel à l'autre : pierres en bruit) ; faces
//   penchées : pierres rondes (rangées un peu plus espacées) au lieu de pierres étirées ;
// - pierres petites à l'écran : facettes, arêtes, griffes et scintillement des facettes se fondent peu à peu en une pierre
//   bombée (relief, reflet et éclat gardés, par pierre) ; plus de scintillement d'une facette à l'autre de loin ; rayons des
//   éclats d'au moins un pixel ; parallaxe bornée de biais (les pierres ne s'étirent plus en pointes sur le dessus).

// studio de bijoutier (or du Diamond) : grands panneaux lumineux verticaux tout autour, à hauteur d'yeux, sombres entre
// eux ; fixés dans le monde comme le reste du décor : les faces de côté passent du clair au sombre quand on se retourne
float panneauxOr(vec3 r, float soft) {
    float el = asin(clamp(r.y, -1.0, 1.0));
    float az = atan(r.z, r.x);
    return softBox(az, el, 0.2, 0.05, 0.22, 0.75, soft) + softBox(az, el, 1.75, 0.0, 0.16, 0.7, soft)
         + softBox(az, el, 3.0, 0.1, 0.25, 0.8, soft) + softBox(az, el, -1.2, -0.05, 0.18, 0.7, soft)
         + 0.8 * softBox(az, el, -2.3, 0.15, 0.12, 0.6, soft);
}

// or jaune poli : le reflet net du décor lié au monde (soleil, panneaux, horizon) passe par une rampe d'or : creux brun
// profond, or saturé, jaune pâle, éclats blanc chaud (contraste fort, comme l'or du Diamond de Black Ops 6)
vec3 orJaune(vec3 n, out float eclat) {
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 e = envWorld(r, 0.025) * 0.6 + envWorld(r, 0.22) * 0.25 + panneauxOr(r, 0.06) * 0.9;
    float L = lum3(e) + 0.1;
    L += (0.3 + 0.4 * L) * pow(1.0 - ndv, 5.0);                 // Fresnel : bords plus clairs
    L = pow(L, 1.3) * 1.12;                                       // décor plus contrasté : creux profonds, éclats francs
    float t = 1.0 - exp(-0.95 * L);
    // un or reste jaune même sous une forte lumière : seuls les éclats les plus forts pâlissent
    float s[6] = float[6](0.0, 0.2, 0.42, 0.62, 0.82, 1.0);
    vec3 c[6] = vec3[6](hexc(0x2a1400), hexc(0x643a04), hexc(0xb0780c), hexc(0xe3aa1e), hexc(0xf9d046), hexc(0xffeca6));
    eclat = smoothstep(0.85, 0.98, t);
    return ramp6(t, s, c);
}

// or blanc / platine du serti et des griffes : reflet un peu adouci, neutre
vec3 orBlanc(vec3 n) {
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 F0 = vec3(0.84, 0.85, 0.87);
    vec3 F = F0 + (vec3(1.0) - F0) * pow(1.0 - ndv, 5.0);
    vec3 env = envWorld(r, 0.06) * 0.55 + envWorld(r, 0.4) * 0.45;
    return env * F * 0.8 + F0 * 0.1;
}

// repère de la face dans la vue, aligné sur les coordonnées uv du motif (dérivées écran : constant sur une face) :
// sert à pencher les normales des pierres pour de vrai (relief 3D) sans bruit
mat3 faceFrame(vec3 n, vec2 uv) {
    vec3 dp1 = dFdx(viewPos);
    vec3 dp2 = dFdy(viewPos);
    vec2 du1 = dFdx(uv);
    vec2 du2 = dFdy(uv);
    vec3 dp2perp = cross(dp2, n);
    vec3 dp1perp = cross(n, dp1);
    vec3 T = dp2perp * du1.x + dp1perp * du2.x;
    vec3 B = dp2perp * du1.y + dp1perp * du2.y;
    float im = inversesqrt(max(max(dot(T, T), dot(B, B)), 1e-24));
    return mat3(T * im, B * im, n);
}

// cellule du pavé : vecteur depuis le centre de la pierre la plus proche (réseau hexagonal, voisines à distance 1)
vec2 paveCell(vec2 uv, out vec2 cid) {
    vec2 S = vec2(1.0, 1.7320508);
    vec2 ga = mod(uv, S) - 0.5 * S;
    vec2 gb = mod(uv - 0.5 * S, S) - 0.5 * S;
    vec2 g = dot(ga, ga) < dot(gb, gb) ? ga : gb;
    cid = floor((uv - g) * 2.0 + 0.5);
    return g;
}

// écart au centre de la pierre redressé dans la vraie métrique de la face : le motif est la projection sur un plan des axes,
// qui écrase une face penchée dans la direction plan.xy (voir diamPlan) -> pierres rondes sur toutes les faces ; plan.z :
// coefficient 1 / (nz (1 + nz)), calculé une fois par pixel (diamRedresse est appelé jusqu'à 30 fois)
vec2 diamRedresse(vec2 g, vec3 plan) {
    return g + plan.xy * (dot(g, plan.xy) * plan.z);
}

// griffe la plus proche : sommet de la cellule où trois pierres se touchent (repère du motif) ; par symétrie, c'est un des
// deux sommets du premier quart, remis dans le quart de g (deux distances au lieu de six : appelé jusqu'à 14 fois par pixel)
vec2 diamGriffe(vec2 g) {
    vec2 a = abs(g);
    vec2 d1 = a - vec2(0.5, 0.288675), d2 = a - vec2(0.0, 0.57735);
    vec2 c = dot(d1, d1) < dot(d2, d2) ? vec2(0.5, 0.288675) : vec2(0.0, 0.57735);
    return vec2(g.x < 0.0 ? -c.x : c.x, g.y < 0.0 ? -c.y : c.y);
}

// relief du pavé (unités de cellule) : 0 = table des pierres = surface de l'arme ; la couronne descend vers le serti,
// creusé à -0.38 ; les griffes remontent un peu entre les pierres
float paveHeight(vec2 uv, vec3 plan) {
    vec2 cid;
    vec2 g = paveCell(uv, cid);
    float rr = length(diamRedresse(g, plan));
    float h = rr < 0.44 ? -0.24 * smoothstep(0.17, 0.44, rr) : -0.38;
    float bead = length(diamRedresse(g - diamGriffe(g), plan));
    return max(h, -0.38 + 0.2 * sqrt(max(1.0 - bead * bead / 0.0072, 0.0)));
}

// parallaxe : le rayon regardé (vt, repère de la face, vers l'intérieur) descend sous la surface jusqu'au pavé ; rend le
// point du pavé réellement vu -> les pierres dépassent, le serti est en creux, elles se masquent entre elles de biais.
// Au plus 13 lectures de paveHeight par pixel, et seulement de près (fin > 0)
vec2 pavePom(vec2 uv, vec3 vt, vec3 plan) {
    // de biais, le décalage est borné (au plus 2.5 fois la profondeur) et s'atténue : sinon les pierres s'étirent en pointes
    vec2 dir = vt.xy / max(-vt.z, 0.4) * smoothstep(0.08, 0.3, -vt.z);
    float d = 0.0;
    float prev = 0.0;
    vec2 q = uv;
    float h = paveHeight(q, plan);
    for (int i = 0; i < 9; i++) {
        if (d >= -h) break;
        prev = d;
        d += 0.045;
        q = uv + dir * d;
        h = paveHeight(q, plan);
    }
    // affinage entre les deux derniers pas
    float lo = prev, hi = d;
    for (int i = 0; i < 3; i++) {
        float m = 0.5 * (lo + hi);
        if (m >= -paveHeight(uv + dir * m, plan)) hi = m; else lo = m;
    }
    return uv + dir * hi;
}

// plan du motif sur la face : projection sur le plan des axes le plus proche de la normale n (comme surf2d), mais les
// égalités sont tranchées toujours pareil (côtés, puis dessus, puis avant) avec une marge : sur une face à 45°, surf2d
// changeait de plan d'un pixel à l'autre (arrondis, demi-précision de la carte) -> pierres en bruit. plan : xy = part de
// la normale dans le plan du motif, z = |part sur l'axe de projection| (au moins 0.57) ; voir diamRedresse
vec2 diamPlan(vec3 p, vec3 n, out vec3 plan) {
    vec3 a = abs(n) + vec3(0.0, 0.05, 0.1);
    if (a.x > a.y && a.x > a.z) {
        plan = vec3(n.z, n.y, abs(n.x));
        return p.zy;
    }
    if (a.y > a.z) {
        plan = vec3(n.x, n.z, abs(n.y));
        return p.xz;
    }
    plan = vec3(n.x, n.y, abs(n.z));
    return p.xy;
}

// isotropie d'une projection de la face au pixel (dérivées écran de la vue ev*, de la grandeur projetée ep*) : 1 si elle
// suit la vraie surface avec le même étirement dans toutes les directions, vers 0 si elle s'écrase dans une direction
float diamIsotropie(vec3 evx, vec3 evy, vec3 epx, vec3 epy) {
    float sv = 1.0 / max(dot(evx, evx) + dot(evy, evy), 1e-30);
    float sp = 1.0 / max(dot(epx, epx) + dot(epy, epy), 1e-30);
    float a = dot(evx, evx) * sv, b = dot(evx, evy) * sv, c = dot(evy, evy) * sv;
    float A = dot(epx, epx) * sp, B = dot(epx, epy) * sp, C = dot(epy, epy) * sp;
    float tr = c * A - 2.0 * b * B + a * C;
    return clamp(4.0 * (a * c - b * b) * (A * C - B * B) / max(tr * tr, 1e-12), 0.0, 1.0);
}

// pas du pavé de secours, en pierres par unité de la vue : en vue joueur, un pixel du modèle TaCZ vaut 1/16 de bloc ; une
// pierre tous les 0.3 pixel du modèle, comme le pavé de la carte (160 pierres par longueur) sur une arme de ~48 pixels
const float DIAM_SECOURS = 16.0 / 0.3;

// repère de secours du pavé (unités de cellule) : coordonnées de texture de la face, redressées par leurs dérivées écran
// (exactes) -> collées à l'arme, pierres rondes ; si la face n'en a pas (UV réduites à un point), position dans la vue
// projetée sur la face (elle peut alors glisser un peu quand l'arme bouge dans la vue)
vec2 diamSecours(vec3 n, vec3 ex, vec3 ey, vec2 tx, vec2 ty, float det) {
    if (abs(det) > 1e-9 * length(cross(ex, ey))) {
        // déplacements dans la vue pour un pas de u et de v sur la face, puis repère orthonormé de la face (T le long de u)
        vec3 au = (ex * ty.y - ey * tx.y) / det;
        vec3 av = (ey * tx.x - ex * ty.x) / det;
        vec3 T = normalize(au);
        vec3 B = normalize(cross(n, T));
        return vec2(texCoord0.x * length(au) + texCoord0.y * dot(av, T), texCoord0.y * dot(av, B)) * DIAM_SECOURS;
    }
    vec3 T = normalize(cross(n, abs(n.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0)));
    return vec2(dot(viewPos, T), dot(viewPos, cross(n, T))) * DIAM_SECOURS;
}

vec3 diamondCod(vec3 p, out vec3 em) {
    float t = CamoTime;
    NO_SHADE = 0.5;
    vec3 nv = normalize(viewNormal);
    // réseau hexagonal des pierres (voisines à distance 1) ; dérivées écran prises avant toute branche
    vec3 fnl = faceNormal(p);
    vec3 plan;
    vec2 uvC = diamPlan(p, fnl, plan) * 160.0;
    // là où la carte de positions ne suit pas la surface (UV réduites à un point, UV prises par plusieurs faces de formes
    // différentes, faces ou bords de faces plus fins qu'un texel de la carte : un texel vaut environ deux pierres), le pavé
    // est posé sur le repère de secours, avec les mêmes pierres
    vec3 evx = dFdx(viewPos), evy = dFdy(viewPos);
    vec2 tx = dFdx(texCoord0), ty = dFdy(texCoord0);
    bool secours = diamIsotropie(evx, evy, dFdx(p), dFdy(p)) < 0.35;
    vec2 uv0 = secours ? diamSecours(nv, evx, evy, tx, ty, tx.x * ty.y - ty.x * tx.y) : uvC;
    plan = secours ? vec3(0.0) : vec3(plan.xy, 1.0 / (plan.z * (1.0 + plan.z)));
    // pixels par pierre (direction la plus serrée) : pierre et relief dès ~2 pixels (fin), facettes, arêtes et griffes de ~7
    // à ~14 pixels (facette) ; en dessous, la pierre devient bombée et lisse
    float pps = 1.0 / max(max(length(dFdx(uv0)), length(dFdy(uv0))), 1e-4);
    float fin = smoothstep(1.8, 4.0, pps);
    float facette = smoothstep(7.0, 14.0, pps);
    mat3 tbn = faceFrame(nv, uv0);
    // pavé sur le corps (codes 1.0 et 0.94 de la carte), pas sur les petites pièces ni les poignées en or (0.97, 0.985).
    // La carte est en demi-précision : 0.94, 0.97 et 0.985 + 2 x id de face reviennent à 0.0025 près au plus -> seuils
    // serrés, sans recouvrement
    float m = (1.0 - smoothstep(0.008, 0.02, abs(PART - 0.94))) + (1.0 - smoothstep(0.004, 0.009, abs(PART - 1.0)));
    // or jaune (canon, chargeur, poignée, petites pièces) : surfaces très légèrement bombées ; calculé seulement là où il
    // sert (pièces en or, transitions) ; sous le pavé, le serti presque caché par les pierres prend la normale de la face
    float eclatOr = 0.0;
    vec3 gold = vec3(0.0);
    vec3 n0 = nv;
    if (m < 0.999) {
        n0 = normalize(nv + 0.16 * softWarp(p, 2.4));
        gold = orJaune(n0, eclatOr);
    }
    if (m < 0.001) {
        em = gold * eclatOr * 0.35;
        return gold * reliefMetal(0.45);
    }
    vec3 metal = orBlanc(n0);
    // relief réel : on suit le regard sous la surface jusqu'au pavé (pierres en saillie, serti en creux)
    vec2 uv = fin > 0.0 ? mix(uv0, pavePom(uv0, transpose(tbn) * eyeDir(), plan), fin) : uv0;
    vec2 cid;
    vec2 gu = paveCell(uv, cid);
    vec2 g = diamRedresse(gu, plan);
    float rr = length(g);
    // lumière du ciel (repère du monde, venant d'en haut) ramenée dans la vue puis sur la face
    vec3 lv = normalize(transpose(IViewRotMat) * normalize(vec3(0.25, 1.0, 0.35)));
    vec3 lt = transpose(tbn) * lv;
    vec3 sunW = normalize(vec3(0.42, 0.68, -0.6));
    // pierre taille brillant en relief : table octogonale plate au sommet, 8 facettes de couronne qui descendent vers le
    // serti ; chaque facette est un petit miroir penché, et ce qu'elle renvoie (reflets internes) est très contrasté
    float R = 0.44;
    float hc = hash(vec3(cid, 5.0));
    float a0 = hc * TAU;
    float ang = atan(g.y, g.x) - a0;
    float sec = floor(fract(ang / TAU) * 8.0);
    float am = a0 + (sec + 0.5) * (TAU / 8.0);
    float oct = 0.0;
    for (int i = 0; i < 8; i++) {
        float a = a0 + float(i) * (TAU / 8.0);
        oct = max(oct, dot(g, vec2(cos(a), sin(a))));
    }
    float isTable = 1.0 - step(0.19, oct);
    // couronne : facettes en étoile (moitié intérieure) et facettes de ceinture (moitié extérieure), pentes différentes
    float outer = step(0.32, rr);
    float tilt = mix(0.62, 1.0, outer) * (1.0 - isTable);
    float sub = floor(fract(ang / TAU * 16.0) * 2.0);                  // demi-facettes de ceinture
    float am2 = am + (sub - 0.5) * (TAU / 32.0) * outer;
    vec3 nt = normalize(vec3(vec2(cos(am2), sin(am2)) * tilt + g / R * 0.25, 1.0));
    // petite à l'écran : pierre bombée (normale lisse), reflet du décor adouci ; de loin : face plane
    nt = normalize(mix(vec3(0.0, 0.0, 1.0), mix(normalize(vec3(g / R * 0.7, 1.0)), nt, facette), fin));
    vec3 ns = normalize(tbn * nt);
    vec3 rs = reflWorld(ns);
    float v = lum3(envWorld(rs, mix(0.25, 0.02, facette)));
    float fh = hash(vec3(cid * 1.31, sec + 16.0 * sub * outer + isTable * 40.0 + 3.0));
    // reflets internes : chaque facette renvoie une autre partie du décor, presque blanche ou presque noire (scintillement
    // du brillant) ; elle change avec l'angle de vue et lentement avec le temps
    // (petite à l'écran : un seul éclat pour toute la pierre, plus d'une facette à l'autre)
    float fhs = hash(vec3(cid * 1.31, 61.0));
    float flick = 0.5 + 0.5 * sin(dot(rs, vec3(5.1, 3.7, 4.3)) * 3.1 + mix(fhs, fh, facette) * TAU + t * 0.45);
    float ndl = dot(ns, lv);
    // dessin du brillant vu de dessus : table claire, couronne en « flèches » (une facette sur deux sombre, décalées
    // entre facettes d'étoile et de ceinture) ; des facettes s'allument ou s'éteignent avec l'angle (scintillement)
    float fleche = mod(sec + outer, 2.0) * (1.0 - isTable);
    float allume = smoothstep(0.7, 0.86, flick);
    float eteint = (1.0 - smoothstep(0.1, 0.22, flick)) * (1.0 - isTable);
    float clair = clamp(1.0 - 0.8 * fleche + allume - eteint * 0.7, 0.0, 1.0);
    // petite à l'écran : dessin moyen du brillant (table claire, couronne plus grise), l'éclat de la pierre en plus
    float tableLisse = 1.0 - smoothstep(0.19 - 0.7 / pps, 0.19 + 0.7 / pps, oct);
    clair = mix(clamp(mix(0.74, 1.0, tableLisse) + 0.5 * allume - 0.3 * eteint, 0.0, 1.0), clair, facette);
    float b = 0.8 + 0.28 * smoothstep(0.2, 1.6, v) + 0.12 * ndl + (0.06 * (fh - 0.5) + 0.15 * allume) * facette;
    vec3 stone = mix(mix(hexc(0x5a606a), hexc(0x2a2f38) * (0.85 + 0.3 * fh), facette), vec3(b) * vec3(0.97, 0.985, 1.0), clair);
    // feu : un peu de couleur spectrale dans les facettes claires (dispersion), selon l'angle de vue
    vec3 fire = spectrum(fract(fh * 3.0 + dot(rs, vec3(0.6, 0.3, 0.75)) * 2.0));
    stone = mix(stone, stone * (0.55 + 0.6 * fire), clair * 0.3 * step(0.62, fh) * facette);
    // reflet net de la lumière sur les facettes qui lui font face
    float spec = pow(max(dot(reflect(-lv, ns), -eyeDir()), 0.0), mix(12.0, 28.0, facette));
    stone += spec * 0.7;
    // arêtes des facettes : traits fins et nets (dessin de la taille brillant)
    float fa = fract(ang / TAU * 8.0);
    float edgeF = (1.0 - smoothstep(0.0, 0.07, min(fa, 1.0 - fa))) * (1.0 - isTable)
                + (1.0 - smoothstep(0.0, 0.02, abs(oct - 0.19))) + (1.0 - smoothstep(0.0, 0.02, abs(rr - 0.32))) * (1.0 - isTable);
    stone *= 1.0 - 0.3 * clamp(edgeF, 0.0, 1.0) * fin * facette;
    float aa = 0.7 / pps + 1e-4;
    float inside = 1.0 - smoothstep(R - aa, R + aa, rr);
    stone *= 1.0 - 0.45 * smoothstep(R - max(0.05, 1.2 * aa), R - 0.005, rr);   // liseré du serti, d'au moins un pixel
    // serti en or blanc dans le creux, ombre portée de chaque pierre à l'opposé de la lumière, occlusion autour des
    // pierres, griffes (billes) en relief là où trois pierres se touchent
    float hgt = 0.16;
    vec2 off = lt.xy * hgt / max(lt.z, 0.25);
    float shadow = 1.0 - smoothstep(R - 0.04, R + max(0.06, 1.5 * aa), length(g + off));
    float ao = mix(mix(0.78, 0.55, facette), 1.0, smoothstep(R, R + max(0.12, 1.5 * aa), rr));
    // (de près le serti est profond et sombre ; petites pierres : jours plus clairs, comme un pavé vu de loin)
    vec3 setting = metal * ao * (1.0 - mix(0.25, 0.45, facette) * shadow * fin);
    vec2 bd = diamRedresse(gu - diamGriffe(gu), plan);
    float bead = length(bd);
    float rb = 0.085;
    float bm = (1.0 - smoothstep(rb - aa, rb + aa, bead)) * facette;           // griffes : de près seulement
    bd /= rb;
    vec3 nb = normalize(tbn * vec3(bd, sqrt(max(1.0 - dot(bd, bd), 0.0)) + 0.15));
    float bl = 0.6 + 0.5 * max(dot(nb, lv), 0.0) + 0.9 * pow(max(dot(reflect(-lv, nb), -eyeDir()), 0.0), 30.0);
    if (bm > 0.0) setting = mix(setting, orBlanc(nb) * bl, bm);
    vec3 pave = mix(setting, stone, inside);
    // de loin : argent scintillant (moyenne des pierres et du serti), un éclat par pierre qui varie lentement (plus d'une
    // facette à l'autre : bruit de pixels), effacé quand les pierres passent sous ~2 pixels
    vec3 paveFar = mix(metal * 0.85, vec3(0.9, 0.92, 0.96), 0.72)
                 * (1.0 + 0.14 * (sin(fhs * TAU + t * 0.45 + dot(reflWorld(n0), vec3(2.3, 1.7, 2.9))) * smoothstep(1.5, 3.0, pps)));
    pave = mix(paveFar, pave, fin);
    // éclats en étoile : une pierre sur cinq environ renvoie le soleil ou s'allume un instant ; quatre rayons fins
    float hs = hash(vec3(cid, 11.0));
    vec3 rc = reflWorld(normalize(tbn * normalize(vec3((hash3(vec3(cid, 7.0)).xy - 0.5) * 0.9, 1.0))));
    float soleil = pow(max(dot(rc, sunW), 0.0), 18.0);
    float tw = pow(max(0.0, sin(t * 1.7 + hs * 60.0)), 24.0) * step(0.78, hs);
    float force = clamp(soleil * step(0.45, hs) + tw, 0.0, 1.2) * fin;
    float ca = cos(a0), sa = sin(a0);
    vec2 gr = vec2(ca * g.x + sa * g.y, -sa * g.x + ca * g.y);
    float wr = max(1.0 / 60.0, 0.8 * aa);                                   // rayons d'au moins un pixel
    float star = exp(-abs(gr.x) / wr) * exp(-abs(gr.y) * 5.5) + exp(-abs(gr.y) / wr) * exp(-abs(gr.x) * 5.5);
    star += exp(-rr * rr * 120.0);
    vec3 col = mix(gold * reliefMetal(0.45), pave, clamp(m, 0.0, 1.0));
    // les pierres renvoient la lumière même à l'ombre : une part de leur éclat ne dépend pas de l'éclairage
    em = (stone * 0.22 * inside + star * force * vec3(1.0, 0.98, 0.95) * 1.1) * clamp(m, 0.0, 1.0)
       + gold * eclatOr * 0.35 * (1.0 - clamp(m, 0.0, 1.0));
    return col;
}

// ------------------------------------------------------------------ couleurs simples : peinture satinée façon Cerakote
// posée SUR l'arme : le relief de la texture d'origine (gravures, vis, rails, arêtes) reste lisible ; grain fin de la
// peinture et reflet doux du décor
vec3 solidPaint(int id, vec3 p, out vec3 em) {
    vec3 c = id == 20 ? hexc(0x8e1119) : id == 21 ? hexc(0x14234a) : id == 22 ? hexc(0xdfe3e6)
           : id == 23 ? hexc(0x4b5330) : id == 24 ? hexc(0xb89a6a) : hexc(0xe6a4b8);
    vec3 n = normalize(viewNormal);
    float ndv = facing(n);
    vec3 env = envWorld(reflWorld(n), 0.7);
    float grain = vnoise(p * 230.0);
    vec3 col = c * (0.92 + 0.12 * grain) * RELIEF;
    col += env * (0.035 + 0.25 * pow(1.0 - ndv, 4.0)) * mix(vec3(1.0), c, 0.35) * clamp(RELIEF, 0.5, 1.2);
    em = vec3(0.0);
    return col;
}

// ------------------------------------------------------------------ couleurs simples (avant 2026-10 : céramique mate)
vec3 solid(int id, vec3 p, out vec3 em) {
    vec3 c = id == 20 ? hexc(0x8e1119) : id == 21 ? hexc(0x14234a) : id == 22 ? hexc(0xdfe3e6)
           : id == 23 ? hexc(0x4b5330) : id == 24 ? hexc(0xb89a6a) : hexc(0xe6a4b8);
    vec3 col = c * (0.93 + 0.07 * fbm(p * 60.0, 2));
    col += 0.10 * pow(clamp(viewNormal.y * 0.5 + 0.5, 0.0, 1.0), 4.0) * (c * 0.5 + 0.5);
    em = vec3(0.0);
    return col;
}

// ================================================================== nouveaux camos du 19/11 (rendus validés par le propriétaire)
// Opale 80 (épique), Marbre noir 81 (légendaire), Patine 82 (rare), Néon 83 (épique, animé), Rubis 84 (légendaire),
// Phénix 210 et Abysses 211 (skins X mythiques, animés : surfaces sous leurs effets 3D, appelées par skinSurface).
// Entièrement procéduraux : aucune texture. Ils gardent le relief de l'arme et les reflets liés au monde des camos refaits
// (CAMO_RELIEF). Repris du Diamond : diamPlan (plan du motif), diamIsotropie (la carte de positions suit-elle la
// surface ?) et, pour le Rubis, paveCell, diamRedresse et diamSecours. Cette partie n'est compilée que dans les programmes
// de ces sept camos (les autres n'ont pas à la lire au chargement du jeu).
// Contre le bruit de pixels (vue joueur) : chaque détail a une taille minimale à l'écran ; un trait plus fin qu'un pixel
// s'estompe (couverture) au lieu de clignoter ; un point lumineux garde son énergie en grossissant jusqu'à un pixel.
// Outils communs aux deux lots du 19/11 (le 2e lot, plus bas, s'en sert aussi)
#if (CAMO_ID >= 80 && CAMO_ID <= 89) || (CAMO_ID >= 210 && CAMO_ID <= 212)

// taille d'un pixel à l'écran, en unités de l'arme (1 = longueur de l'arme)
float ncPx(vec3 p) {
    return max(length(fwidth(p)), 1e-6);
}

// trait de demi-largeur w autour de d = 0 (mêmes unités que px), adouci sur un pixel ; plus fin qu'un demi-pixel : estompé
float ncTrait(float d, float w, float px) {
    float ww = max(w, 0.5 * px);
    return (1.0 - smoothstep(ww - 0.5 * px, ww + 0.5 * px, abs(d))) * (w / ww);
}

// parallaxe (vraie profondeur sous la surface) : déplacement, dans le plan de la face et dans le repère de l'arme, du point
// vu à la profondeur d, par unité de d. Tiré des dérivées écran de la position sur l'arme et de la position dans la vue :
// ni uniforme ni texture de plus, et le sens du repère de la carte n'intervient pas. Borné de biais (pas de traînées) et
// effacé là où la carte de positions ne suit pas la surface.
vec3 ncParallaxe(vec3 p) {
    vec3 ex = dFdx(viewPos), ey = dFdy(viewPos);
    vec3 px = dFdx(p), py = dFdy(p);
    vec3 nvv = cross(ex, ey);
    float lv = length(nvv);
    float lg = length(cross(px, py));
    float fid = diamIsotropie(ex, ey, px, py);
    vec3 n = nvv / max(lv, 1e-30);
    vec3 e = eyeDir();
    // e = a ex + b ey + c n (base duale de la face)
    float a = dot(e, cross(ey, n)) / max(lv, 1e-30);
    float b = dot(e, cross(n, ex)) / max(lv, 1e-30);
    float c = dot(e, n);
    float k = sqrt(lg / max(lv, 1e-30));
    vec3 tg = (a * px + b * py) / max(k, 1e-12);
    float ok = lv > 1e-30 && lg > 1e-30 ? 1.0 : 0.0;
    return tg / max(abs(c), 0.35) * smoothstep(0.6, 0.85, fid) * ok;
}

// repère orthonormé de la face dans la vue, premier axe le long de u du motif (dérivées écran : constant sur une face) ;
// le faceFrame du Diamond n'est pas orthonormé (il garde l'étirement du motif) : les normales des rivets et des rubis
// seraient penchées autrement
mat3 ncRepere(vec3 n, vec2 uv) {
    vec3 dp1 = dFdx(viewPos);
    vec3 dp2 = dFdy(viewPos);
    vec2 du1 = dFdx(uv);
    vec2 du2 = dFdy(uv);
    vec3 dp2perp = cross(dp2, n);
    vec3 dp1perp = cross(n, dp1);
    vec3 T = dp2perp * du1.x + dp1perp * du2.x;
    vec3 B = dp2perp * du1.y + dp1perp * du2.y;
    T -= n * dot(T, n);
    float lt = dot(T, T);
    T = lt > 1e-30 ? T * inversesqrt(lt) : normalize(cross(n, abs(n.x) < 0.9 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0)));
    vec3 bb = cross(n, T);
    return mat3(T, dot(bb, B) < 0.0 ? -bb : bb, n);
}
#endif

// 1er lot : Opale, Marbre noir, Patine, Néon, Rubis, Phénix, Abysses
#if (CAMO_ID >= 80 && CAMO_ID <= 84) || CAMO_ID == 210 || CAMO_ID == 211
// distance (unités de l'arme) à la ligne de niveau v = c d'un champ lisse : pente prise sur les dérivées écran
float ncDistIso(float v, float c, vec3 p) {
    float g = length(vec2(dFdx(v), dFdy(v))) / max(length(vec2(length(dFdx(p)), length(dFdy(p)))), 1e-7);
    return abs(v - c) / max(g, 1e-3);
}

float ncSeg(vec2 p, vec2 a, vec2 b) {
    vec2 pa = p - a, ba = b - a;
    float h = clamp(dot(pa, ba) / max(dot(ba, ba), 1e-12), 0.0, 1.0);
    return length(pa - ba * h);
}

// or jaune poli (rampe d'or sur le reflet du décor lié au monde, comme l'or du Diamond) ; eclat : éclats les plus forts
vec3 ncOr(vec3 n, float rough, out float eclat) {
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 e = envWorld(r, rough) * 0.75 + envWorld(r, rough + 0.25) * 0.25;
    float L = lum3(e) + 0.08;
    L += (0.3 + 0.4 * L) * pow(1.0 - ndv, 5.0);
    L = pow(L, 1.3) * 1.1;
    float tt = 1.0 - exp(-0.95 * L);
    float s[6] = float[6](0.0, 0.2, 0.42, 0.62, 0.82, 1.0);
    vec3 c[6] = vec3[6](hexc(0x2a1400), hexc(0x643a04), hexc(0xb0780c), hexc(0xe3aa1e), hexc(0xf9d046), hexc(0xffeca6));
    eclat = smoothstep(0.85, 0.98, tt);
    return ramp6(tt, s, c);
}

// rampe de feu : 0 rouge sombre -> orange -> jaune -> 1 blanc chaud
vec3 ncFeu(float x) {
    return mix(mix(hexc(0x3a0500), hexc(0xff4a00), smoothstep(0.0, 0.45, x)),
               mix(hexc(0xffb52a), hexc(0xfff3c8), smoothstep(0.72, 1.0, x)), smoothstep(0.4, 0.78, x));
}

// points lumineux d'une couche (un point par cellule de taille cs, rayon r0, une cellule sur deux environ) : rayon d'au
// moins un pixel, énergie gardée (pas de scintillement de pixels de loin) ; tw : scintillement lent
float ncPoints(vec3 q, float cs, float r0, float px, float t, float graine) {
    vec3 g = q / cs;
    vec3 c = floor(g);
    vec3 h = hash3(c + graine);
    vec3 pt = c + 0.22 + 0.56 * h;
    float d = length(g - pt) * cs;
    float r = max(r0, 0.8 * px);
    float k = (r0 / r) * (r0 / r);
    float tw = 0.55 + 0.45 * sin(t * (0.7 + 1.6 * h.z) + h.x * 40.0);
    return step(0.45, h.y) * exp(-d * d / (r * r)) * k * tw;
}

// ------------------------------------------------------------------ 80 Opale (épique)
// opale blanche : corps laiteux bleuté, translucide ; « jeu de couleurs » : chaque plage (domaine de silice ordonnée) a sa
// propre orientation fixée dans le monde et renvoie une couleur pure (bleu, vert, jaune, orange, un peu de rouge) quand le
// regard s'y aligne ; l'alignement varie un peu à travers la plage, si bien que l'éclat la balaie et change de teinte quand
// la vue bouge ; les plages voisines s'allument ensemble (champ d'orientation lent). Nacre et plages vues un peu sous la
// surface (parallaxe), vernis poli
vec3 ncOpaleTeinte(float h) {
    float s[6] = float[6](0.0, 0.22, 0.44, 0.6, 0.8, 1.0);
    vec3 c[6] = vec3[6](hexc(0x2f4bff), hexc(0x0cc0ff), hexc(0x16e88a), hexc(0x9cf03a), hexc(0xffa21e), hexc(0xff4a2e));
    return ramp6(h, s, c);
}

// une couche de plages : rend l'éclat (a), la teinte (rgb) ; q : point de la couche, r : reflet lié au monde
vec4 ncOpaleCouche(vec3 q, vec3 sc, float warp, vec3 r, vec3 champ, float t, float graine) {
    vec3 qc = q * sc + graine;
    vec3 w = vec3(fbm(qc * 0.3, 2), fbm(qc * 0.3 + vec3(5.2, 1.3, 2.8), 2), 0.5) - 0.5;
    float f1, f2; vec3 cell, fp;
    vec3 qw = qc + warp * w;
    worley(qw, f1, f2, cell, fp);
    vec3 hc = hash3(cell + 3.7);
    // orientation du réseau : penchée différemment à travers la plage (l'éclat la balaie)
    // orientation propre à chaque plage, autour d'un champ qui varie lentement sur l'arme : les plages voisines s'allument
    // ensemble (grands éclats qui balaient l'arme) au lieu de confettis
    vec3 gdir = normalize(champ * 3.0 + (hash3(cell + 11.3) - 0.5) * 1.3 + (qw - fp) * 0.15);
    float al = dot(r, gdir);
    float k = 0.5 + 0.5 * cos(TAU * (al * 1.1 + 0.25 * hc.y));
    // éclat franc quand le regard s'aligne, lueur colorée faible sinon
    float I = smoothstep(0.7, 0.93, k) + 0.1 * smoothstep(0.45, 0.7, k);
    float hue = fract(hc.x * 0.55 + 0.45 * al + 0.9 * champ.x + 0.03 * sin(t * 0.3 + hc.z * 6.0));
    // la plage est plus vive en son cœur qu'à ses bords (l'éclat vient de la masse)
    float edge = smoothstep(0.01, 0.045, f2 - f1) * (0.75 + 0.25 * smoothstep(0.05, 0.3, f2 - f1)) * step(0.12, hc.z);
    return vec4(ncOpaleTeinte(hue), clamp(I, 0.0, 1.0) * edge);
}

vec3 opale(vec3 p, out vec3 em) {
    float t = CamoTime;
    NO_SHADE = 0.3;
    vec3 nv = normalize(viewNormal);
    float px = ncPx(p);
    vec3 par = ncParallaxe(p);
    vec3 r = reflWorld(nv);
    float ndv = facing(nv);
    // nacre laiteuse, un peu sous la surface : couches ondulées et irisation douce (film mince) qui glisse avec l'angle
    vec3 qm = (p + par * 0.006) * vec3(3.5, 8.0, 8.0);
    vec3 wm = vec3(fbm(qm, 2), fbm(qm + vec3(5.2, 1.3, 2.8), 2), 0.0);
    float couche = fbm(qm * 1.3 + 2.0 * wm, 3);
    float bande = 0.5 + 0.5 * sin(couche * 38.0);
    float film = couche * 3.0 + 0.6 * dot(r, vec3(0.3, 0.8, 0.5)) + 0.3 * (1.0 - ndv);
    vec3 irid = spectrum(fract(film));
    vec3 milk = mix(hexc(0xd2dbe4), hexc(0xf8f6f1), smoothstep(0.3, 0.7, fbm(qm * 0.9 + vec3(3.0), 3)));
    vec3 nacre = mix(milk, milk * (0.78 + 0.4 * irid), 0.3 + 0.25 * bande);
    // éclats d'opale : petites plages vives, regroupées en zones, qui s'allument quand le regard s'y aligne
    vec3 champ = vec3(fbm(p * 2.2 + vec3(1.0, 7.0, 3.0), 2), fbm(p * 2.2 + vec3(8.0, 2.0, 5.0), 2), fbm(p * 2.2 + vec3(4.0, 9.0, 1.0), 2)) - 0.5;
    vec4 c1 = ncOpaleCouche(p + par * 0.004, vec3(30.0, 48.0, 48.0), 1.4, r, champ, t, 0.0);
    float zone = smoothstep(0.36, 0.56, fbm(p * vec3(3.0, 5.0, 5.0) + vec3(4.0), 2));
    c1.a *= 0.4 + 0.6 * zone;
    vec3 col = mix(nacre, c1.rgb * 1.1, c1.a * 0.92);
    // vernis poli : reflet net du décor surtout de biais, reflet doux ; reflet bleuté de biais
    float F = 0.04 + 0.96 * pow(1.0 - ndv, 5.0);
    col = col * 0.93 + envWorld(r, 0.03) * F * 0.5 + envWorld(r, 0.55) * 0.035;
    col += hexc(0x6f9cff) * pow(1.0 - ndv, 3.0) * 0.1;
    em = c1.rgb * c1.a * 0.22;
    return col * reliefMetal(0.5);
}

// ------------------------------------------------------------------ 81 Marbre noir (légendaire)
// marbre noir (Nero Marquina) poli miroir : nuages gris très sombres ; veines blanches de marbre (familles de veines
// sinueuses qui se croisent, plus ou moins marquées le long de leur cours, halo laiteux autour des grandes) ; quelques veines d'or poli qui luisent, dont les éclats courent le long de la veine quand la vue bouge ;
// reflets nets et contrastés du décor lié au monde
// veine de marbre : lignes phase = k pi d'un champ ondulé ; rend la distance (unités de l'arme) et l'indice de la veine
float ncVeine(vec3 p, vec3 dir, float freq, float amp, vec3 w, float graine, out float idx) {
    float ph = dot(p, dir) * freq + amp * fbm(p * vec3(3.5, 6.0, 6.0) + 1.8 * w + graine, 4);
    idx = floor(ph / PI + 0.5);
    return ncDistIso(sin(ph), 0.0, p);
}

vec3 marbreNoir(vec3 p, out vec3 em) {
    float t = CamoTime;
    NO_SHADE = 0.45;
    vec3 nv = normalize(viewNormal);
    float px = ncPx(p);
    vec3 q = p * vec3(2.4, 4.6, 4.6) + vec3(4.0, 1.0, 7.0);
    vec3 w0 = vec3(fbm(q, 3), fbm(q + vec3(5.2, 1.3, 2.8), 3), fbm(q + vec3(9.1, 4.4, 0.7), 3));
    float i1, i2, iG;
    float d1 = ncVeine(p, normalize(vec3(1.0, 0.62, 0.35)), 30.0, 5.5, w0, 0.0, i1);
    float d2 = ncVeine(p, normalize(vec3(-0.45, 1.0, 0.6)), 46.0, 4.0, w0, 9.0, i2);
    float dG = ncVeine(p, normalize(vec3(1.0, -0.35, 0.5)), 16.0, 4.5, w0, 21.0, iG);
    // marquage des veines le long de leur cours : elles apparaissent, s'affinent, disparaissent
    float m1 = smoothstep(0.25, 0.5, fbm(p * vec3(3.0, 5.0, 5.0) + vec3(i1 * 1.7, 3.0, 1.0), 2));
    float m2 = smoothstep(0.3, 0.55, fbm(p * vec3(4.0, 7.0, 7.0) + vec3(i2 * 2.3, 8.0, 2.0), 2));
    float or_ = step(0.4, hash(vec3(iG, 2.0, 5.0))) * smoothstep(0.28, 0.45, fbm(p * vec3(2.0, 4.0, 4.0) + vec3(iG, 1.0, 9.0), 2));
    // fond : noir profond, nuages gris-bleu très sombres
    float cloud = fbm(q * 0.8 + w0, 3);
    vec3 col = mix(hexc(0x030304), hexc(0x16171c), smoothstep(0.38, 0.85, cloud));
    // veines blanches
    float wv = ncTrait(d1, 0.0017 * (0.45 + 0.75 * m1), px) * m1 + 0.85 * ncTrait(d2, 0.0008, px) * m2;
    col += hexc(0xbcb9b2) * (exp(-d1 / 0.008) * m1 * 0.2 + exp(-d2 / 0.004) * m2 * 0.07);
    col = mix(col, hexc(0xefece5), clamp(wv, 0.0, 1.0));
    // veines d'or : surface un peu ondulée (les reflets varient le long de la veine), éclats qui courent quand la vue bouge
    float gold = ncTrait(dG, 0.0022, px) * or_;
    vec3 nG = normalize(nv + 0.35 * softWarp(p * 3.0, 9.0));
    float ecl;
    vec3 gcol = ncOr(nG, 0.03, ecl);
    vec3 rg = reflWorld(nG);
    float glint = pow(max(0.0, sin(dot(rg, vec3(5.0, 3.0, 4.0)) * 2.3 + dot(p, vec3(60.0, 35.0, 35.0)) + t * 0.5)), 14.0);
    col = mix(col, gcol * (1.0 + 1.1 * glint), gold);
    col += hexc(0xffb43a) * exp(-dG / 0.004) * or_ * 0.06;
    // poli miroir : reflet net, contrasté (le gris du décor s'efface, panneaux, horizon et soleil restent)
    vec3 n = normalize(nv + 0.025 * softWarp(p, 3.0));
    float ndv = facing(n);
    vec3 env = envWorld(reflWorld(n), 0.012);
    float F = 0.07 + 0.93 * pow(1.0 - ndv, 5.0);
    col += env * env * 0.55 * F * (1.0 - gold);
    em = (hexc(0xffc24a) * (0.12 + 0.9 * glint) + gcol * ecl * 0.3) * gold;
    return col * reliefMetal(0.6);
}

// ------------------------------------------------------------------ 82 Patine (rare)
// cuivre vieilli assemblé en plaques : joints creusés, rivets bombés (cuivre poli, collerette de vert-de-gris), vert-de-gris
// crayeux en plaques et dans les creux de l'arme, coulures vertes sous les rivets et le joint du flanc, cuivre poli là où
// l'arme s'use (arêtes), crasse dans le bas (ambiance survie)
vec3 patine(vec3 p, out vec3 em) {
    em = vec3(0.0);
    NO_SHADE = 0.15;
    vec3 nv = normalize(viewNormal);
    vec3 fn = faceNormal(p);
    vec3 an = abs(fn);
    float px = ncPx(p);
    // plan du motif (diamPlan du Diamond) : x reste le long de l'arme sur les flancs, le dessus et le dessous
    vec3 plan;
    vec2 uv = diamPlan(p, fn, plan);
    mat3 tbn = ncRepere(nv, uv);
    float flanc = smoothstep(0.55, 0.75, an.z);
    float trans = 1.0 - smoothstep(0.55, 0.75, an.x);
    // joints transverses (plaques de 1/8.5 de longueur) et joint longitudinal des flancs
    const float NP = 8.5;
    float sx = p.x * NP + 0.31;
    float kS = floor(sx + 0.5);
    float xsj = (kS - 0.31) / NP;
    float dS = abs(p.x - xsj);
    const float Y0 = 0.012;
    float dL = abs(p.y - Y0);
    float joint = max(ncTrait(dS, 0.0010, px) * trans, ncTrait(dL, 0.0010, px) * flanc);
    // rivets : deux rangées de part et d'autre du joint transverse le plus proche, et le long du joint des flancs
    const float SP = 0.0145, RR = 0.0034, OFF = 0.0072;
    vec2 rc = vec2(xsj + (p.x > xsj ? OFF : -OFF), (floor(uv.y / SP) + 0.5) * SP);
    vec2 rl = vec2((floor(p.x / SP) + 0.5) * SP, Y0 + (p.y > Y0 ? OFF : -OFF));
    vec2 o1 = vec2(p.x, uv.y) - rc;
    vec2 o2 = p.xy - rl;
    float d1 = length(o1) + (1.0 - trans);
    float d2 = length(o2) + (1.0 - flanc);
    vec2 ro = d1 < d2 ? o1 : o2;
    float dR = min(d1, d2);
    float rvis = smoothstep(1.3, 2.4, RR / px);
    float riv = (1.0 - smoothstep(RR - 0.5 * px, RR + 0.5 * px, dR)) * rvis;
    float col_ = (1.0 - smoothstep(RR * 1.2, RR * 2.1, dR)) * (1.0 - riv) * rvis;
    // coulures sous les rivets (flancs, vers le bas de l'arme) et sous le joint longitudinal
    float drip = 0.0;
    {
        float xr = rc.x + (vnoise(vec3(p.y * 90.0, kS, 1.0)) - 0.5) * 0.002;
        for (int k = 0; k < 3; k++) {
            float yr = (floor(p.y / SP) + 0.5 + float(k)) * SP;
            float dy = yr - p.y - RR;
            float hk = hash(vec3(kS, floor(yr / SP), p.x > xsj ? 1.0 : 0.0));
            float Ld = 0.014 + 0.04 * hk;
            float wd = 0.0019 + 0.03 * max(dy, 0.0);
            drip = max(drip, step(0.0, dy) * exp(-dy / Ld) * exp(-pow((p.x - xr) / wd, 2.0)) * step(0.3, hk));
        }
        float colL = (floor(p.x / 0.0055) + 0.5) * 0.0055 + (vnoise(vec3(p.y * 70.0, 3.0, floor(p.x / 0.0055))) - 0.5) * 0.0018;
        float hL = hash(vec3(floor(p.x / 0.0055), 5.0, 7.0));
        float dyL = Y0 - OFF - RR - p.y;
        drip = max(drip, step(0.0, dyL) * exp(-dyL / (0.015 + 0.05 * hL)) * exp(-pow((p.x - colL) / 0.0016, 2.0)) * step(0.55, hL));
        drip *= flanc;
    }
    // vert-de-gris : plaques, creux de la texture d'origine, bord des joints, collerette des rivets, coulures
    vec3 q = p * vec3(10.0, 17.0, 17.0);
    float n1 = fbm(q + vec3(3.0), 4);
    float cav = smoothstep(0.25, 0.55, 1.0 - RELIEF);
    float vdgF = n1 + 0.12 * cav + 0.24 * exp(-dS / 0.0035) * trans + 0.2 * exp(-dL / 0.003) * flanc;
    float vdg = smoothstep(0.52, 0.62, vdgF);
    vdg = max(max(vdg, col_ * 0.92), drip * 0.9);
    // liseré sombre autour des plaques de vert-de-gris (cuivre noirci au bord de la croûte)
    float lis = smoothstep(0.44, 0.52, vdgF) * (1.0 - vdg);
    // cuivre : rampe de cuivre sur le reflet du décor (creux brun profond, cuivre, saumon clair) ; poli là où l'arme s'use
    // (arêtes claires de la texture, plaques usées, têtes de rivets), patiné ailleurs (brun rouge marbré, reflet terne)
    float wear = smoothstep(0.5, 0.7, fbm(q * 1.6 + vec3(9.0), 3) + 0.15 * smoothstep(0.2, 0.5, RELIEF - 1.0));
    vec2 rd = ro / RR;
    vec3 nr = normalize(tbn * vec3(rd * 0.9, sqrt(max(1.0 - dot(rd, rd), 0.0)) + 0.25));
    vec3 n = normalize(mix(nv + 0.06 * softWarp(p, 8.0), nr, riv));
    vec3 rr = reflWorld(n);
    float ndv = facing(n);
    vec3 eS = envWorld(rr, 0.22), eR = envWorld(rr, 0.75);
    float LS = lum3(eS) + 0.1;
    LS += (0.3 + 0.3 * LS) * pow(1.0 - ndv, 5.0);
    float tt = 1.0 - exp(-0.9 * pow(LS, 1.25));
    float s6[6] = float[6](0.0, 0.2, 0.42, 0.62, 0.82, 1.0);
    vec3 c6[6] = vec3[6](hexc(0x1e0a04), hexc(0x5a2410), hexc(0x9c4a22), hexc(0xd07a46), hexc(0xf0a878), hexc(0xffe0c8));
    vec3 polished = ramp6(tt, s6, c6);
    float ox = fbm(q * 2.2 + vec3(1.0, 4.0, 2.0), 4);
    vec3 tarnish = mix(mix(hexc(0x24140c), hexc(0x4a2616), smoothstep(0.3, 0.6, ox)), hexc(0x6a3420), smoothstep(0.62, 0.82, ox));
    tarnish *= 0.75 + 0.45 * lum3(eR);
    tarnish += polished * 0.16;
    vec3 metal = mix(tarnish, polished, max(wear * 0.9, riv));
    // vert-de-gris crayeux, mat : croûte claire, fond plus bleu et plus sombre, coulures bleu-vert
    float vt = smoothstep(0.25, 0.8, fbm(q * 3.1 + vec3(5.0), 3));
    vec3 verd = mix(hexc(0x24645a), mix(hexc(0x4fa58c), hexc(0x9ad8bf), smoothstep(0.55, 0.9, vt)), smoothstep(0.1, 0.6, vt));
    verd = mix(verd, hexc(0x2f7d78), drip * 0.45);
    verd *= (0.8 + 0.25 * lum3(eR)) * (0.95 + 0.1 * vnoise(p * 150.0) * (1.0 - smoothstep(0.001, 0.0025, px)));
    vec3 col = mix(metal, verd, vdg);
    col *= 1.0 - 0.45 * lis;
    // joints : creux sombres, bord éclairé d'un côté
    col = mix(col, hexc(0x120804), joint * 0.85);
    // crasse : le bas de l'arme et les creux plus sombres
    col *= (0.82 + 0.18 * smoothstep(-0.1, 0.05, p.y)) * mix(1.0, 0.85, cav);
    return col * reliefMetal(0.55);
}

// ------------------------------------------------------------------ 83 Néon (épique, animé)
// noir mat (toucher doux) parcouru de pistes de néon rose et cyan façon circuit : voies le long de l'arme, coudes à 45°,
// pastilles aux bouts ; tubes incrustés (sillon sombre autour), cœur blanc, halo ; les voies respirent lentement et des
// impulsions de lumière y glissent (boucle de 4 s)
float neonBase(vec2 c) {
    return hash(vec3(c, 41.0)) < 0.44 ? 0.0 : 1.0;
}

// 0 vide, 1 droite, 2 coude vers la voie du dessus, 3 vers celle du dessous (seulement si la cellule visée est vide)
float neonEtat(vec2 c) {
    if (neonBase(c) < 0.5) return 0.0;
    float hj = hash(vec3(c, 43.0));
    if (hj > 0.8 && neonBase(c + vec2(0.0, 1.0)) < 0.5) return 2.0;
    if (hj < 0.2 && neonBase(c - vec2(0.0, 1.0)) < 0.5) return 3.0;
    return 1.0;
}

vec3 neon(vec3 p, out vec3 em) {
    float t = CamoTime;
    NO_SHADE = 0.1;
    vec3 nv = normalize(viewNormal);
    vec3 fn = faceNormal(p);
    float px = ncPx(p);
    vec3 plan;
    vec2 uv = diamPlan(p, fn, plan);
    const float CL = 0.05, CH = 0.0165, PAD = 0.0034;
    vec2 g = vec2(uv.x / CL, uv.y / CH);
    vec2 c0 = floor(g);
    float dmin = 1e3;
    float cSel = 0.0;
    float lane = 0.0;
    for (int di = -1; di <= 1; di++)
    for (int dj = -1; dj <= 1; dj++) {
        vec2 c = c0 + vec2(float(di), float(dj));
        float e = neonEtat(c);
        if (e < 0.5) continue;
        float x0 = c.x * CL, x1 = x0 + CL, yc = (c.y + 0.5) * CH;
        float s = e < 1.5 ? 0.0 : e < 2.5 ? 1.0 : -1.0;
        float y1 = yc + s * CH;
        float ca = step(0.5, hash(vec3(floor(c.x / 3.0), floor(c.y / 2.0), 47.0)));
        float cb = step(0.5, hash(vec3(floor(c.x / 3.0), floor((c.y + s) / 2.0), 47.0)));
        // bouts libres : la piste s'arrête sur une pastille
        bool finD = neonEtat(c + vec2(1.0, s)) < 0.5;
        bool finG = neonEtat(c - vec2(1.0, 0.0)) < 0.5;
        float xg = finG ? x0 + PAD * 1.6 : x0;
        float xd = finD ? x1 - PAD * 1.6 : x1;
        float d;
        float cc = ca;
        if (s == 0.0) {
            d = ncSeg(uv, vec2(xg, yc), vec2(xd, yc));
        } else {
            float xa = x0 + 0.5 * (CL - CH), xb = xa + CH;
            float da = ncSeg(uv, vec2(xg, yc), vec2(xa, yc));
            float dd = ncSeg(uv, vec2(xa, yc), vec2(xb, y1));
            float db = ncSeg(uv, vec2(xb, y1), vec2(xd, y1));
            d = min(min(da, dd), db);
            // la couleur passe d'une voie à l'autre le long du coude
            cc = db < min(da, dd) ? cb : dd < da ? mix(ca, cb, clamp((uv.x - xa) / CH, 0.0, 1.0)) : ca;
        }
        if (finD) d = min(d, abs(length(uv - vec2(xd, y1)) - PAD * 0.75));
        if (finG) d = min(d, abs(length(uv - vec2(xg, yc)) - PAD * 0.75));
        if (d < dmin) { dmin = d; cSel = cc; lane = c.y + 0.37 * c.x; }
    }
    vec3 rose = hexc(0xff2bd0), cyan = hexc(0x18eaff);
    vec3 nc = mix(cyan, rose, cSel);
    // respiration lente par voie et impulsions qui glissent le long de l'arme (périodiques : 4 s)
    float ph = hash(vec3(floor(lane), 3.0, 9.0));
    float souffle = 0.62 + 0.38 * (0.5 + 0.5 * sin(TAU * (t / 4.0 + ph)));
    float pk = fract(uv.x / 0.32 - t / 4.0 + ph);
    float impuls = exp(-pow((pk - 0.5) / 0.05, 2.0));
    float tube = ncTrait(dmin, 0.0011, px);
    float coeur = ncTrait(dmin, 0.0004, px);
    float sillon = ncTrait(dmin, 0.0024, px) * (1.0 - tube);
    float halo = exp(-dmin / 0.0026);
    // noir mat : grain très fin (effacé de loin), reflet doux du décor, un peu de lustre de biais
    float ndv = facing(nv);
    vec3 env = envWorld(reflWorld(nv), 0.75);
    float grain = (vnoise(p * 260.0) - 0.5) * (1.0 - smoothstep(0.0009, 0.0022, px));
    vec3 col = hexc(0x0e0e12) * (1.0 + 0.25 * grain) + env * (0.03 + 0.16 * pow(1.0 - ndv, 4.0));
    col *= 1.0 - 0.6 * sillon;
    vec3 lum = nc * (tube * (0.55 + 0.45 * souffle + 0.9 * impuls)) + vec3(1.0) * coeur * (0.35 + 0.5 * impuls) * souffle;
    col = mix(col, nc * 0.35, tube);
    em = lum * 1.1 + nc * halo * (0.12 + 0.18 * souffle + 0.25 * impuls);
    return col * reliefMetal(0.6);
}

// ------------------------------------------------------------------ 84 Rubis (légendaire)
// frère du Diamond : tout le corps (carcasse, garde-main, crosse) couvert d'un pavé serré de rubis taille brillant sertis
// dans de l'or noir ; canon, chargeur, poignée et petites pièces en or noir poli. Même logique de niveaux de détail que le
// Diamond refait le 18/11 (taille d'une pierre à l'écran) : facettes de près, pierre simple et nette à mi-distance, rouge
// profond scintillant de loin ; jamais d'un pixel à l'autre. Les rubis rougeoient un peu à l'ombre (fluorescence).
// Comme le Diamond corrigé le 19/11 : là où la carte de positions ne suit pas la surface, le pavé est posé sur son repère
// de secours (diamSecours), mêmes pierres, au lieu d'un aplat ; pierres rondes sur les faces penchées (diamRedresse).
const float RB_ECHELLE = 88.0;
const float RB_R = 0.46;

float rbEclat(vec3 rF, float h, float t) {
    return 0.5 + 0.5 * sin(TAU * h + dot(rF, vec3(3.1, 2.3, 2.7)) + t * 0.25);
}

float rbTeinte(float base, float s) {
    return base + 0.85 * (s - 0.5) + 0.5 * pow(s, 10.0);
}

// rouge du rubis : 0 grenat presque noir, 0.5 rouge vif, 1 rose clair, au-delà blanc rosé (reflets)
vec3 rbRouge(float x) {
    float s[6] = float[6](0.0, 0.22, 0.45, 0.66, 0.84, 1.0);
    vec3 c[6] = vec3[6](hexc(0x0e0002), hexc(0x3c000c), hexc(0x860014), hexc(0xd00a26), hexc(0xff3a55), hexc(0xffd0d8));
    return ramp6(x / 1.3, s, c);
}

// or noir (rhodié) poli : noir chaud, reflets nets gris chaud, bords plus clairs
vec3 orNoir(vec3 n) {
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 e = envWorld(r, 0.03) * 0.8 + envWorld(r, 0.3) * 0.2;
    float F = 0.3 + 0.7 * pow(1.0 - ndv, 5.0);
    return hexc(0x090605) + (e * 0.2 + e * e * 0.1) * vec3(1.0, 0.86, 0.72) * F;
}

vec3 rubis(vec3 p, out vec3 em) {
    float t = CamoTime;
    NO_SHADE = 0.5;
    vec3 nv = normalize(viewNormal);
    vec3 ed = eyeDir();
    vec3 fnl = faceNormal(p);
    vec3 plan;
    vec2 uvC = diamPlan(p, fnl, plan) * RB_ECHELLE;
    // là où la carte de positions ne suit pas la surface : repère de secours du Diamond (pierres à la même taille)
    vec3 evx = dFdx(viewPos), evy = dFdy(viewPos);
    vec2 tx = dFdx(texCoord0), ty = dFdy(texCoord0);
    bool secours = diamIsotropie(evx, evy, dFdx(p), dFdy(p)) < 0.35;
    vec2 uv = secours ? diamSecours(nv, evx, evy, tx, ty, tx.x * ty.y - ty.x * tx.y) * (RB_ECHELLE / 160.0) : uvC;
    // redressement des pierres (diamRedresse) : xy = part de la normale dans le plan du motif, z = 1 / (nz (1 + nz))
    vec3 nm = secours ? vec3(0.0) : vec3(plan.xy, 1.0 / (plan.z * (1.0 + plan.z)));
    float pps = 1.0 / max(max(length(dFdx(uv)), length(dFdy(uv))), 1e-4);
    mat3 tbn = ncRepere(nv, uv);
    vec3 n0 = normalize(nv + 0.16 * softWarp(p, 2.4));
    float m = clamp((1.0 - smoothstep(0.008, 0.02, abs(PART - 0.94))) + (1.0 - smoothstep(0.004, 0.009, abs(PART - 1.0))), 0.0, 1.0);
    vec3 noir = orNoir(n0);
    if (m < 0.001) {
        em = vec3(0.0);
        return noir * reliefMetal(0.5);
    }
    float wB = smoothstep(3.5, 6.0, pps);
    float wA = smoothstep(16.0, 24.0, pps);
    float aa = 0.7 / pps;
    vec2 cid;
    vec2 g = paveCell(uv, cid);
    vec2 gf = diamRedresse(g, nm);
    float rf = length(gf);
    vec3 hs3 = hash3(vec3(cid, 7.0));
    // de loin : rouge profond velouté (moyenne des pierres et du serti), un éclat par pierre qui varie lentement
    vec3 metal = noir;
    float Lm = min(lum3(metal), 1.4);
    vec3 rougeMoy = rbRouge(0.48 + 0.25 * Lm);
    vec3 loin = mix(metal, rougeMoy, 0.78);
    float eclatP = 0.5 + 0.5 * sin(hs3.x * TAU + t * 0.4 + dot(reflWorld(n0), vec3(2.3, 1.7, 2.9)));
    loin *= 1.0 + 0.3 * (eclatP - 0.5) * smoothstep(1.5, 3.0, pps);
    vec3 col = loin;
    vec3 emP = rougeMoy * 0.06;
    if (wB > 0.0) {
        vec3 lv = normalize(transpose(IViewRotMat) * normalize(vec3(0.25, 1.0, 0.35)));
        vec3 lt = transpose(tbn) * lv;
        float inside = 1.0 - smoothstep(RB_R - aa, RB_R + aa, rf);
        // serti d'or noir : plus sombre contre les pierres, ombre portée de chaque pierre (relief)
        float pres = smoothstep(7.0, 12.0, pps);
        float ao = 1.0 - 0.35 * pres * (1.0 - smoothstep(RB_R, RB_R + 0.09, rf));
        float ombre = 1.0 - smoothstep(RB_R - 0.04, RB_R + 0.05, length(gf + lt.xy * (0.14 / max(lt.z, 0.3))));
        vec3 serti = metal * ao * (1.0 - 0.35 * ombre * pres);
        // griffes : billes d'or noir là où trois pierres se touchent
        float ka = floor((atan(g.y, g.x) - PI / 6.0) / (PI / 3.0) + 0.5);
        vec2 bc = 0.57735 * vec2(cos(PI / 6.0 + ka * PI / 3.0), sin(PI / 6.0 + ka * PI / 3.0));
        vec2 bd = (gf - diamRedresse(bc, nm)) / 0.085;
        float bm = (1.0 - smoothstep(1.0 - aa / 0.085, 1.0 + aa / 0.085, length(bd))) * pres;
        vec3 nb = normalize(tbn * vec3(bd, sqrt(max(1.0 - dot(bd, bd), 0.0)) + 0.15));
        float bl = 0.8 + 0.6 * max(dot(nb, lv), 0.0) + 2.0 * pow(max(dot(reflect(-lv, nb), -ed), 0.0), 24.0);
        serti = mix(serti, orNoir(nb) * bl, bm);
        // pierre : une normale par pierre (penchée au hasard) donne sa clarté propre ; secteurs de couronne et table
        vec3 nS = normalize(tbn * normalize(vec3((hs3.xy - 0.5) * 0.5, 1.0)));
        vec3 rS = reflWorld(nS);
        float LS = min(lum3(envWorld(rS, 0.35)), 1.4);
        float a0 = hash(vec3(cid, 5.0)) * TAU;
        float r = rf / RB_R;
        float an8 = (atan(gf.y, abs(gf.x) > 1e-7 ? gf.x : 1e-7) - a0) / (TAU / 8.0);
        float sec = floor(an8 + 0.5);
        float phi = (an8 - sec) * (TAU / 8.0);
        float haut = phi >= 0.0 ? 1.0 : 0.0;
        vec2 P = r * vec2(cos(phi), abs(sin(phi)));
        float aaN = aa / RB_R;
        float mTable = 1.0 - smoothstep(-aaN, aaN, P.x * 0.9239 + P.y * 0.3827 - 0.52);
        float azB = sec * (TAU / 8.0) + a0;
        float sB = rbEclat(reflWorld(tbn * vec3(cos(azB) * 0.5, sin(azB) * 0.5, 0.866)), hash(vec3(cid, 20.0 + mod(sec, 8.0))), t);
        float sT = rbEclat(rS, hs3.z, t);
        // rubis : table plus claire et plus rose, couronne en rouge profond qui s'allume par secteurs ; bord du rondiste sombre
        float teinte = mix(mix(0.55, rbTeinte(0.5, sB), smoothstep(6.0, 10.0, pps)), rbTeinte(0.72, sT), mTable);
        teinte += 0.15 * (LS - 0.5);
        if (wA > 0.0) {
            vec2 C = vec2(0.563, 0.0), E = vec2(0.7234, 0.2996), G = vec2(1.0, 0.0);
            float dEt = ((E.x - C.x) * (P.y - C.y) - (E.y - C.y) * (P.x - C.x)) / 0.3398;
            float dHa = ((G.x - E.x) * (P.y - E.y) - (G.y - E.y) * (P.x - E.x)) / 0.4078;
            float k = haut > 0.5 ? sec : sec - 1.0;
            float azE = (k + 0.5) * (TAU / 8.0) + a0, azL = sec * (TAU / 8.0) + a0;
            float azH = (sec + (haut - 0.5) * 0.6) * (TAU / 8.0) + a0;
            float sE = rbEclat(reflWorld(tbn * vec3(cos(azE) * 0.27, sin(azE) * 0.27, 0.963)), hash(vec3(cid, 40.0 + mod(k, 8.0))), t);
            float sL = rbEclat(reflWorld(tbn * vec3(cos(azL) * 0.565, sin(azL) * 0.565, 0.825)), hash(vec3(cid, 50.0 + mod(sec, 8.0))), t);
            float sH = rbEclat(reflWorld(tbn * vec3(cos(azH) * 0.667, sin(azH) * 0.667, 0.745)), hash(vec3(cid, 60.0 + 2.0 * mod(sec, 8.0) + haut)), t);
            float tL = rbTeinte(0.8, sL);
            float tE = rbTeinte(0.42, sE);
            float tH = rbTeinte(mod(sec + haut, 2.0) > 0.5 ? 0.7 : 0.2, sH);
            float couronne = mix(mix(tL, tH, smoothstep(-aaN, aaN, dHa)), tE, smoothstep(-aaN, aaN, dEt));
            float fleche = (1.0 - smoothstep(-aaN, aaN, P.y - 0.12 * P.x)) * smoothstep(28.0, 40.0, pps);
            float tT = rbTeinte(0.78, sT) - 0.5 * fleche;
            teinte = mix(teinte, mix(couronne, tT, mTable), wA);
        }
        teinte = clamp(teinte, 0.03, mix(1.0, 1.3, smoothstep(9.0, 16.0, pps)));
        vec3 pierre = rbRouge(teinte);
        // reflet du soleil et des panneaux sur la table (vernis de la gemme)
        float spec = pow(max(dot(reflect(-lv, nS), -ed), 0.0), 28.0) * mTable;
        pierre += vec3(1.0, 0.85, 0.88) * spec * 0.35 * smoothstep(7.0, 12.0, pps);
        // rondiste : liseré sombre du bord de la pierre
        pierre *= 1.0 - 0.45 * smoothstep(5.0, 10.0, pps) * smoothstep(1.0 - max(0.07, 1.4 * aaN), 1.0, r);
        col = mix(loin, mix(serti, pierre, inside), wB);
        // éclats en étoile (blanc rosé), comme le Diamond : la pierre et ses deux voisines du côté du pixel
        vec3 rN = reflWorld(nv);
        float wr = max(0.025, aa);
        float eclats = 0.0;
        for (int j = 0; j < 3; j++) {
            float aj = (ka + float(j - 1)) * (PI / 3.0);
            vec2 o = j == 0 ? vec2(0.0) : vec2(cos(aj), sin(aj));
            vec2 idk = floor((uv - g + o) * 2.0 + 0.5);
            float hx = hash(vec3(idk, 11.0));
            if (hx < 0.86) continue;
            vec3 hk = hash3(vec3(idk, 7.0));
            float f = pow(max(0.0, sin(t * 0.9 + hx * 600.0)), 24.0)
                    + pow(max(dot(normalize(rN + (hk - 0.5) * 1.2), normalize(vec3(0.42, 0.68, -0.6))), 0.0), 80.0);
            if (f < 0.02) continue;
            vec2 d = g - o;
            d = diamRedresse(d, nm);
            float ak = hash(vec3(idk, 5.0)) * TAU;
            vec2 gr = vec2(cos(ak) * d.x + sin(ak) * d.y, -sin(ak) * d.x + cos(ak) * d.y);
            eclats += min(f, 1.2) * (exp(-abs(gr.x) / wr) * exp(-abs(gr.y) * 1.7) + exp(-abs(gr.y) / wr) * exp(-abs(gr.x) * 1.7)
                                     + 0.8 * exp(-dot(d, d) * 8.0));
        }
        emP = mix(emP, pierre * 0.2 * inside, wB) + eclats * vec3(1.0, 0.86, 0.9) * wB * smoothstep(5.0, 9.0, pps);
    }
    em = emP * m;
    return mix(noir * reliefMetal(0.5), col, m);
}

// ------------------------------------------------------------------ 210 Phénix (skin X mythique, animé)
// or en fusion qui coule lentement vers la crosse : grande houle et vaguelettes (les reflets nets du décor ondulent),
// chaleur qui fait trembler les reflets (miroitement) ; dessous, le métal chauffé à blanc affleure par grandes plages
// orange et jaune qui dérivent, et des filets incandescents filent le long de l'arme avec la coulée (lignes de coulée,
// halo orange) ; lueur propre (émission) : il brille même dans le noir
vec3 phenix(vec3 p, out vec3 em) {
    float t = CamoTime;
    NO_SHADE = 0.45;
    vec3 nv = normalize(viewNormal);
    float px = ncPx(p);
    // miroitement de chaleur : le motif et les reflets tremblent un peu (bruit lent qui monte)
    float sh1 = vnoise(vec3(p.x * 36.0, p.y * 36.0 - t * 2.4, p.z * 36.0)) - 0.5;
    float sh2 = vnoise(vec3(p.x * 36.0 + 7.0, p.y * 36.0 - t * 2.0, p.z * 36.0 + 3.0)) - 0.5;
    vec3 ps = p + vec3(sh1, sh2, 0.0) * 0.0025;
    // coulée vers la crosse : le domaine avance le long de l'arme
    float adv = t * 0.05;
    vec3 q = vec3(ps.x + adv, ps.y, ps.z) * vec3(3.0, 7.0, 7.0);
    vec3 w = vec3(fbm(q * 0.8, 3), fbm(q * 0.8 + vec3(5.2, 1.3, 2.8), 3), 0.0);
    float swell = fbm(q + 1.6 * w + vec3(0.0, 0.0, t * 0.04), 4);
    float h = swell * 0.011 + sin((ps.x + adv * 1.6) * 75.0 + swell * 6.0) * 0.0005;
    vec3 n = bumpNormal(nv, h, p);
    n = normalize(n + vec3(sh1, sh2, 0.0) * 0.1);
    float ecl;
    vec3 gold = ncOr(n, 0.02, ecl);
    // métal chauffé à blanc qui affleure (creux de la houle, plages qui dérivent)
    float T = fbm(q * 1.1 + 2.2 * w + vec3(7.0, 0.0, t * 0.05), 4) + 0.35 * (0.5 - swell);
    float hot = smoothstep(0.52, 0.7, T);
    // filets incandescents : lignes de coulée le long de l'arme (champ étiré), seulement par endroits
    float fl = ps.y * 46.0 + 5.0 * fbm(vec3((ps.x + adv * 1.3) * 7.0, ps.y * 6.0, ps.z * 6.0) + w, 4);
    float dl = ncDistIso(sin(fl * PI), 0.0, p);
    float li = floor(fl + 0.5);
    float on = smoothstep(0.38, 0.6, fbm(vec3((ps.x + adv * 1.3) * 6.0, li * 0.73, ps.z * 3.0), 3)) * step(0.25, hash(vec3(li, 4.0, 1.0)));
    float filet = ncTrait(dl, 0.0011, px) * on;
    float halo = exp(-dl / 0.0045) * on;
    // or liquide ; la chaleur le fait virer à l'orange incandescent (cœur jaune-blanc) ; filets blanc-jaune, halo orange
    float cT = smoothstep(0.62, 0.9, T);
    float vT = fbm(q * 3.0 + 1.5 * w + vec3(0.0, -t * 0.12, 0.0), 3);
    vec3 chaud = mix(ncFeu(0.42 + 0.12 * vT), ncFeu(0.9), cT);
    vec3 col = mix(gold, chaud * 0.9, hot * 0.85);
    col = mix(col, ncFeu(0.97), filet);
    float pouls = 0.85 + 0.15 * sin(t * 2.2 + swell * 9.0);
    em = chaud * hot * (0.55 + 0.35 * cT) * pouls + ncFeu(0.92) * filet * 1.1 + ncFeu(0.55) * halo * (0.45 + 0.2 * hot) + gold * ecl * 0.25;
    return col * reliefMetal(0.35);
}

// ------------------------------------------------------------------ 211 Abysses (skin X mythique, animé)
// verre noir des grands fonds, avec une vraie profondeur (parallaxe) : en dessous, la lueur bleu nuit de la surface vers le
// haut de l'arme et des caustiques très douces ; dans la masse, trois couches de neige marine qui dérivent, deux couches de
// filaments bioluminescents turquoise qui ondulent, et sur les flancs des méduses qui pulsent (ombrelle translucide au
// bord lumineux, canaux, gonades roses, tentacules) ; en surface, le verre poli renvoie le décor
vec4 ncMeduse(vec2 u, vec2 c, float R, float t, float ph, float px) {
    float pulse = 0.5 + 0.5 * sin(t * 1.8 + ph);
    vec2 q = (u - c) / R;
    q.y -= 0.12 * sin(t * 0.45 + ph);
    q.x -= 0.05 * sin(t * 0.3 + ph * 2.0);
    float pR = px / R;
    float sx = 1.0 - 0.12 * pulse, sy = 0.7 + 0.12 * pulse;
    vec2 b = vec2(q.x / sx, (q.y + 0.12) / sy);
    float rB = length(b);
    float scal = 0.05 * cos(q.x * 13.0 + ph);
    float rimY = -0.12 + scal - 0.05 * pulse;
    float dome = (1.0 - smoothstep(1.0 - pR, 1.0 + pR, rB)) * smoothstep(rimY - pR, rimY + pR, q.y);
    float bord = smoothstep(0.55, 1.0, rB) * dome * 0.8 + ncTrait(q.y - rimY, 0.015, pR) * step(abs(q.x), sx) * 0.45;
    float ang = atan(b.x, b.y + 0.12);
    float canal = ncTrait(sin(ang * 4.0) * rB * 0.5, 0.012, pR) * dome * smoothstep(0.25, 0.6, rB);
    // gonades : lueur rose diffuse au centre de l'ombrelle (quatre lobes à peine marqués, pas deux « yeux »)
    float gon = exp(-pow(q.x / 0.42, 2.0) - pow((q.y - 0.08) / 0.16, 2.0)) * (0.75 + 0.25 * cos(q.x * 15.0)) * dome * 0.7;
    // bras buccaux : quatre rubans froncés sous le centre
    float bras = 0.0;
    for (int k = 0; k < 4; k++) {
        float fk = float(k);
        float s = rimY - q.y;
        float L = 1.1 + 0.3 * fk;
        float xk = (fk - 1.5) * 0.12 + 0.14 * s * sin(s * 3.0 - t * 1.1 + fk * 2.0 + ph);
        float wk = 0.07 * (1.0 - 0.5 * s / L) * (0.7 + 0.3 * sin(s * 18.0 + fk));
        bras += step(0.0, s) * step(s, L) * (1.0 - smoothstep(wk - pR, wk + pR, abs(q.x - xk))) * (1.0 - s / L);
    }
    // tentacules : fils depuis le bord de l'ombrelle, qui ondulent
    float ten = 0.0;
    for (int k = 0; k < 9; k++) {
        float fk = float(k);
        float x0 = (fk / 8.0 - 0.5) * 1.75 * sx;
        float L = 2.1 + 1.1 * hash(vec3(fk, ph, 3.0));
        float s = rimY - q.y;
        float xk = x0 * (1.0 - 0.12 * s) + 0.16 * s * sin(s * 2.3 - t * 1.25 + fk * 1.7 + ph);
        float d = abs(q.x - xk);
        float wk = 0.016 * (1.0 - 0.55 * s / L);
        float a = step(0.0, s) * step(s, L) * (1.0 - s / L);
        ten += a * (ncTrait(d, wk, pR) + 0.22 * exp(-d / 0.06));
    }
    vec3 c1 = hexc(0x7ff8ff), c2 = hexc(0xa98cff), c3 = hexc(0xff7ad6), c4 = hexc(0x3dffd8);
    // halo de lumière autour de l'ombrelle (eau éclairée par la méduse)
    float aura = exp(-max(rB - 0.9, 0.0) * 3.5) * smoothstep(rimY - 0.6, rimY + 0.1, q.y) * (1.0 - dome);
    vec3 col = c1 * bord * 1.4 + mix(c1, c2, 0.55) * dome * 0.28 + c1 * canal * 0.6 + c3 * gon * 1.0
             + mix(c2, c3, 0.3) * clamp(bras, 0.0, 1.0) * 0.45 + c4 * ten * 0.85 + mix(c1, c2, 0.4) * aura * 0.1;
    float a = clamp(dome * 0.45 + ten + bord + bras * 0.4, 0.0, 1.0);
    return vec4(col * (0.8 + 0.35 * pulse), a);
}

vec3 abysses(vec3 p, out vec3 em) {
    float t = CamoTime;
    NO_SHADE = 0.5;
    vec3 nv = normalize(viewNormal);
    vec3 fn = faceNormal(p);
    float px = ncPx(p);
    vec3 par = ncParallaxe(p);
    // fond (couche la plus profonde) : bleu nuit, plus clair vers le haut de l'arme (lumière de la surface), caustiques
    vec3 pD = p + par * 0.03;
    float lumHaut = smoothstep(-0.1, 0.14, pD.y);
    vec3 col = mix(hexc(0x010306), hexc(0x0a3044), lumHaut * 0.75 + 0.25 * fbm(pD * vec3(4.0, 7.0, 7.0) + vec3(0.0, 0.0, t * 0.03), 3));
    vec3 qcs = pD * vec3(9.0, 14.0, 14.0) + vec3(t * 0.05, t * 0.03, -t * 0.04);
    float caust = pow(1.0 - abs(2.0 * vnoise(qcs) - 1.0), 6.0) * pow(1.0 - abs(2.0 * vnoise(qcs * 1.7 + 5.0) - 1.0), 2.0) * lumHaut;
    // rayons de lumière qui descendent de la surface, très doux
    float rayons = pow(vnoise(vec3(pD.x * 14.0 + pD.y * 4.0, t * 0.12, pD.z * 3.0)), 3.0) * smoothstep(-0.12, 0.15, pD.y);
    col += hexc(0x0f5a6a) * caust * 0.22 + hexc(0x0b3a4a) * rayons * 0.35;
    em = hexc(0x06303c) * caust * 0.12 + hexc(0x062a36) * rayons * 0.2;
    // neige marine : trois couches (les plus profondes plus petites, plus bleues, plus ternes)
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float d = 0.003 + 0.009 * fi + 0.004 * fi * fi;
        vec3 qi = p + par * d + vec3(t * 0.002 * (1.0 + fi), t * (0.0025 - 0.0006 * fi), 0.0);
        float pt = ncPoints(qi, 0.01 + 0.004 * fi, 0.0013 - 0.0002 * fi, px, t, 13.0 + fi * 7.0);
        em += mix(hexc(0xbff8ff), hexc(0x3f9cff), fi * 0.5) * pt * (1.0 - 0.25 * fi) * 1.1;
    }
    // filaments bioluminescents (deux couches) : lignes de niveau d'un bruit lent qui ondule, découpées en brins
    for (int i = 0; i < 2; i++) {
        float fi = float(i);
        float d = 0.007 + 0.014 * fi;
        vec3 qi = p + par * d;
        vec3 qq = qi * vec3(6.0, 11.0, 11.0) + vec3(fi * 7.3 + t * 0.045, t * 0.03, -t * 0.02);
        float n1 = fbm(qq + 0.8 * vec3(vnoise(qq * 0.6 + vec3(t * 0.06)), vnoise(qq * 0.6 + vec3(3.0)), 0.0), 3);
        float dd = ncDistIso(n1, 0.5, p);
        float brins = smoothstep(0.36, 0.66, vnoise(qq * 1.2 + vec3(0.0, 0.0, t * 0.08)));
        float puls = 0.6 + 0.4 * sin(t * 1.3 + qq.x * 3.0 + fi * 2.0);
        float f = (ncTrait(dd, 0.0008, px) * 1.0 + 0.42 * exp(-dd / 0.0032)) * brins * puls * (1.0 - 0.35 * fi);
        vec3 tq = mix(hexc(0x14f7d2), hexc(0x2aa8ff), fi * 0.6);
        em += tq * f;
        col += tq * f * 0.08;
    }
    // méduses : couche profonde, sur les flancs (repère (x, y) de l'arme : la même méduse vue des deux côtés), une par
    // tranche de l'arme (une sur trois absente), tailles et rythmes différents ; trois voisines évaluées
    vec3 pj = p + par * 0.02;
    float flanc = smoothstep(0.55, 0.85, abs(fn.z));
    vec4 md = vec4(0.0);
    const float MC = 0.21;
    float gx = (pj.x + 0.5) / MC;
    float ic = floor(gx);
    float iv = fract(gx) < 0.5 ? -1.0 : 1.0;
    for (int k = 0; k <= 1; k++) {
        float ci = ic + float(k) * iv;
        vec3 hm = hash3(vec3(ci, 3.0, 8.0));
        if (hm.x < 0.2) continue;
        vec2 cm = vec2((ci + 0.35 + 0.3 * hm.y) * MC - 0.5, 0.012 + 0.02 * (hm.z - 0.5));
        vec4 mk = ncMeduse(pj.xy, cm, 0.05 + 0.02 * hm.z, t, hm.y * 20.0, px);
        md = vec4(md.rgb + mk.rgb, max(md.a, mk.a));
    }
    md *= flanc;
    col = mix(col, col * 0.6, md.a * 0.3);
    em += md.rgb;
    // verre poli : reflet net et contrasté du décor
    float ndv = facing(nv);
    vec3 env = envWorld(reflWorld(nv), 0.015);
    float F = 0.06 + 0.94 * pow(1.0 - ndv, 5.0);
    col += env * env * 0.5 * F;
    return col * reliefMetal(0.6);
}
#endif

// ================================================================== 2e lot de camos du 19/11 (demandés par le propriétaire)
// Forge 85 (rare), Or craquelé 86 (épique), Dichroïque 87 (épique), Vipère 88 (légendaire), Rouages 89 (légendaire,
// animé), Prisme 212 (skin X mythique, animé : surface sous son effet 3D, appelée par skinSurface). Entièrement procéduraux :
// aucune texture. Ils gardent le relief de l'arme et les reflets liés au monde des camos refaits (CAMO_RELIEF). Ce qui est
// propre à ce lot commence par l2 / L2_ ; outils repris du 1er lot (bloc commun plus haut) : ncPx, ncTrait, ncRepere,
// ncParallaxe. Compilé seulement dans les programmes de ces six camos.
#if (CAMO_ID >= 85 && CAMO_ID <= 89) || CAMO_ID == 212
// Contre le bruit de pixels (vue joueur) : chaque trait a une largeur réelle sur l'arme et s'estompe (couverture) quand il
// devient plus fin qu'un pixel ; les motifs fins (dents, écailles, cuvettes, stries) se fondent en leur teinte moyenne quand
// ils passent sous quelques pixels ; un point lumineux garde au moins un pixel.

// plan du motif sur la face (projection sur le plan des axes le plus proche de la normale, égalités toujours tranchées
// pareil, comme le Diamond) : x reste le long de l'arme sur les flancs, le dessus et le dessous.
// axe : 0 bouts (zy), 1 dessus / dessous (xz), 2 flancs (xy)
vec2 l2Plan(vec3 p, vec3 n, out float axe) {
    vec3 a = abs(n) + vec3(0.0, 0.05, 0.1);
    if (a.x > a.y && a.x > a.z) {
        axe = 0.0;
        return p.zy;
    }
    if (a.y > a.z) {
        axe = 1.0;
        return p.xz;
    }
    axe = 2.0;
    return p.xy;
}

// point de l'arme qui a les coordonnées uv dans le plan du motif (3e coordonnée : celle du pixel)
vec3 l2Point(vec3 p, vec2 uv, float axe) {
    return axe < 0.5 ? vec3(p.x, uv.y, uv.x) : axe < 1.5 ? vec3(uv.x, p.y, uv.y) : vec3(uv, p.z);
}

// normale penchée par une hauteur h dont on connaît le gradient g (repère de l'arme, unités de l'arme) : formule de
// bumpNormal (Mikkelsen 2010) avec les dérivées de h calculées (g . dp) au lieu de dFdx(h) : facettes nettes, aucun pixel
// faux aux arêtes du motif. dpx, dpy : dérivées écran de la position sur l'arme ; sx, sy : de la position dans la vue
vec3 l2Pente(vec3 n, vec3 g, vec3 dpx, vec3 dpy, vec3 sx, vec3 sy) {
    vec3 r1 = cross(sy, n);
    vec3 r2 = cross(n, sx);
    float det = dot(sx, r1);
    if (abs(det) < 1e-24) return n;
    float k = length(sx) / max(length(dpx), 1e-9);
    vec3 grad = sign(det) * (dot(g, dpx) * r1 + dot(g, dpy) * r2) * k;
    float gmax = 2.0 * abs(det);
    float gl = length(grad);
    grad *= gl > gmax ? gmax / gl : 1.0;
    return normalize(abs(det) * n - grad);
}

// hachage 3D sans sinus (moins cher que hash3 : il est appelé 27 fois par pixel dans les cellules de Voronoï)
vec3 l2Hash3(vec3 p) {
    p = fract(p * vec3(0.1031, 0.103, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.xxy + p.yxx) * p.zyx);
}

// cellules de Voronoï 3D, domaine non tordu (bords droits) : c1 cellule du 1er point, p1 / p2 : 1er et 2e points
void l2Voronoi(vec3 q, float jit, out vec3 c1, out vec3 p1, out vec3 p2) {
    vec3 b = floor(q);
    float f1 = 9.0, f2 = 9.0;
    c1 = b;
    p1 = b;
    p2 = b;
    for (int x = -1; x <= 1; x++)
    for (int y = -1; y <= 1; y++)
    for (int z = -1; z <= 1; z++) {
        vec3 cc = b + vec3(float(x), float(y), float(z));
        vec3 pt = cc + 0.5 + jit * (l2Hash3(cc) - 0.5);
        vec3 d = q - pt;
        float dd = dot(d, d);
        // sélections sans branchement (moins cher que des if dans la boucle de 27 cellules)
        bool a1 = dd < f1;
        bool a2 = dd < f2;
        p2 = a1 ? p1 : (a2 ? pt : p2);
        f2 = a1 ? f1 : (a2 ? dd : f2);
        p1 = a1 ? pt : p1;
        c1 = a1 ? cc : c1;
        f1 = a1 ? dd : f1;
    }
}

// distance signée (unités de l'arme, mesurée sur la face) au bord entre les cellules de p1 et de p2 (Voronoï en q = p * S),
// < 0 dans la cellule de p1 ; fn : normale de la face (repère de l'arme). Rend aussi gb, la direction (repère de l'arme)
// qui sort de la cellule vers p2
float l2Bord(vec3 q, vec3 p1, vec3 p2, vec3 S, vec3 fn, out vec3 gb) {
    vec3 nq = normalize(p2 - p1);
    float e = dot(q - 0.5 * (p1 + p2), nq);
    vec3 ng = S * nq;
    float lg = length(ng);
    gb = ng / lg;
    // le plan du bord coupe la face de biais : distance sur la face = distance au plan / sinus de l'angle
    float sinA = length(gb - fn * dot(gb, fn));
    return e / lg / max(sinA, 0.25);
}

// métal jaune poli : le reflet net du décor lié au monde passe par une rampe (creux sombres, éclats pâles), comme l'or du
// Diamond ; rampe 0 : or jaune, 1 : laiton d'horlogerie ; eclat : éclats les plus forts. Une seule lecture du décor (un peu
// adoucie) : c'est la lecture la plus chère du camo
vec3 l2MetalJaune(vec3 n, float rough, float rampe, out float eclat) {
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 e = envWorld(r, rough + 0.05);
    float L = lum3(e) + 0.08;
    L += (0.3 + 0.4 * L) * pow(1.0 - ndv, 5.0);
    L = pow(L, 1.3) * 1.1;
    float tt = 1.0 - exp(-0.95 * L);
    float s[6] = float[6](0.0, 0.2, 0.42, 0.62, 0.82, 1.0);
    vec3 c[6] = vec3[6](hexc(0x2a1400), hexc(0x643a04), hexc(0xb0780c), hexc(0xe3aa1e), hexc(0xf9d046), hexc(0xffeca6));
    if (rampe > 0.5) {
        c = vec3[6](hexc(0x241809), hexc(0x5e4417), hexc(0xa47b2e), hexc(0xd6ad58), hexc(0xf2dc9c), hexc(0xfff6dc));
    }
    eclat = smoothstep(0.85, 0.98, tt);
    return ramp6(tt, s, c);
}

// points lumineux (un par cellule de taille cs, rayon r0, une cellule sur deux environ) : rayon d'au moins un pixel,
// énergie gardée (pas de scintillement de pixels de loin) ; vif : scintillement qui suit l'angle de vue (r)
float l2Points(vec3 q, float cs, float r0, float px, vec3 r, float graine) {
    vec3 g = q / cs;
    vec3 c = floor(g);
    vec3 h = hash3(c + graine);
    vec3 pt = c + 0.22 + 0.56 * h;
    float d = length(g - pt) * cs;
    float rr = max(r0, 0.8 * px);
    float k = (r0 / rr) * (r0 / rr);
    float vif = 0.35 + 0.65 * pow(0.5 + 0.5 * sin(dot(r, vec3(9.0, 7.0, 8.0)) + h.x * 40.0), 3.0);
    return step(0.5, h.y) * exp(-d * d / (rr * rr)) * k * vif;
}

// ------------------------------------------------------------------ 85 Forge (rare)
// acier forgé à la main : cuvettes de martelage (chacune renvoie le décor à sa façon : l'arme scintille doucement quand elle
// bouge), calamine bleu-noir mate dans les creux et par plaques, couleurs de revenu (paille, bronze, pourpre, bleu) en
// auréoles là où le métal a chauffé, arêtes plus claires (usure)
vec3 l2Revenu(float x) {
    // 0 acier nu -> paille -> bronze -> pourpre -> bleu roi -> bleu pâle
    float s[6] = float[6](0.0, 0.2, 0.4, 0.6, 0.8, 1.0);
    vec3 c[6] = vec3[6](vec3(1.0), hexc(0xf6dca0), hexc(0xd29550), hexc(0xa04a78), hexc(0x3d48b8), hexc(0x86b0dc));
    return ramp6(x, s, c);
}

vec3 l2Forge(vec3 p, out vec3 em) {
    em = vec3(0.0);
    NO_SHADE = 0.35;
    vec3 nv = normalize(viewNormal);
    vec3 dpx = dFdx(p), dpy = dFdy(p), sx = dFdx(viewPos), sy = dFdy(viewPos);
    float px = ncPx(p);
    // cuvettes de martelage : cellules de Voronoï (~1,4 % de la longueur de l'arme) creusées en paraboloïde autour du point
    // de frappe ; pente analytique : facettes nettes, crêtes vives entre deux cuvettes ; fondues de loin (moins de ~4 pixels)
    const vec3 S = vec3(68.0, 74.0, 74.0);
    vec3 q = p * S;
    vec3 c1, p1, p2;
    l2Voronoi(q, 0.75, c1, p1, p2);
    vec3 hc = l2Hash3(c1 + 5.3);
    float fin = 1.0 - smoothstep(0.0022, 0.0045, px);
    vec3 g = 2.0 * 0.0009 * S * (q - p1) * (0.7 + 0.6 * hc.x) * fin;
    vec3 n = l2Pente(nv, g, dpx, dpy, sx, sy);
    // couleurs de revenu : champ de chaleur lent, auréoles le long de ses lignes de niveau ; acier nu ailleurs
    // points chauds (un par case de 0,22 de côté vue de profil, neuf sur dix) : auréole complète de la paille au bleu
    vec2 hxy = p.xy / 0.22;
    vec2 h0 = floor(hxy - 0.5);
    float heat = 0.0;
    for (int k = 0; k < 4; k++) {
        vec2 cc = h0 + vec2(float(k - (k / 2) * 2), float(k / 2));
        vec3 hh = hash3(vec3(cc, 17.0));
        if (hh.z < 0.1) continue;
        vec2 hp = (cc + 0.2 + 0.6 * hh.xy) * 0.22;
        float R = 0.085 + 0.07 * hh.z;
        heat = max(heat, 1.0 - length((p.xy - hp) * vec2(1.0, 1.25)) / R);
    }
    heat += 0.12 * (fbm(p * 26.0, 2) - 0.5);
    float tr = clamp(heat * 1.15, 0.0, 1.0);
    vec3 tint = l2Revenu(tr);
    // calamine : plaques et creux de la texture d'origine, bord net écaillé (pas dans les auréoles de chauffe)
    float cav = smoothstep(0.2, 0.5, 1.0 - RELIEF);
    float sc = fbm(p * vec3(9.0, 15.0, 15.0) + vec3(3.0, 1.0, 7.0), 4) + 0.22 * cav;
    float cal = smoothstep(0.56, 0.6, sc) * (1.0 - smoothstep(0.1, 0.4, heat));
    // usure : arêtes claires de la texture d'origine plus polies, revenu effacé
    float usure = smoothstep(0.15, 0.45, RELIEF - 1.0);
    tint = mix(tint, vec3(1.0), usure * 0.7);
    // acier poli : reflet net et reflet adouci du décor lié au monde
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 F0 = vec3(0.6, 0.61, 0.63);
    vec3 F = F0 + (vec3(1.0) - F0) * pow(1.0 - ndv, 5.0);
    vec3 env = envWorld(r, 0.13) * 0.6 + envWorld(r, 0.42) * 0.4;
    vec3 acier = (env * F * 0.8 + F0 * 0.06) * tint;
    // calamine : bleu-noir mat (reflet très flou), un peu de grain
    vec3 calc = mix(hexc(0x101318), hexc(0x252b34), vnoise(q * 0.7)) * (0.75 + 0.45 * lum3(envWorld(reflWorld(nv), 0.85)));
    vec3 col = mix(acier, calc, cal * (1.0 - usure * 0.5));
    return col * reliefMetal(0.6);
}

// ------------------------------------------------------------------ 86 Or craquelé (épique)
// or poli brisé puis recollé : plaques (Voronoï 3D, bords droits) penchées un peu les unes par rapport aux autres (chacune
// renvoie le décor à sa façon : mosaïque de reflets qui s'allument et s'éteignent quand la vue bouge), joints noirs fins,
// bords des plaques arrondis (liseré clair ou sombre selon le décor), quelques plaques plus mates, nuances d'or
vec3 l2OrCraquele(vec3 p, out vec3 em) {
    NO_SHADE = 0.45;
    vec3 nv = normalize(viewNormal);
    vec3 dpx = dFdx(p), dpy = dFdy(p), sx = dFdx(viewPos), sy = dFdy(viewPos);
    vec3 fn = faceNormal(p);
    float px = ncPx(p);
    const vec3 S = vec3(24.0, 31.0, 31.0);
    vec3 q = p * S;
    vec3 c1, p1, p2;
    l2Voronoi(q, 0.95, c1, p1, p2);
    vec3 gb;
    float e = l2Bord(q, p1, p2, S, fn, gb);
    vec3 hc = l2Hash3(c1 + 1.7);
    vec3 hd = l2Hash3(c1 + 8.9);
    // fissure secondaire : plan au hasard passant par le centre de la plaque (une plaque sur deux) ; distance sur la face
    vec3 nf = normalize(l2Hash3(c1 + 4.1) - 0.5);
    vec3 ngf = S * nf;
    float lgf = length(ngf);
    vec3 gf = ngf / lgf;
    float df = dot(q - p1, nf) / lgf / max(length(gf - fn * dot(gf, fn)), 0.25);
    float fis = step(0.5, hc.x);
    float cote = fis * (df < 0.0 ? -1.0 : 1.0);
    // plaque penchée au hasard (jusqu'à ~9 degrés), chaque morceau un peu autrement ; bord arrondi sur 0,3 % de l'arme,
    // effacé quand il passe sous 2 pixels
    float bw = 0.003;
    float bev = (1.0 - smoothstep(0.0, bw, -e)) * smoothstep(1.0, 2.5, bw / px);
    vec3 g = (hd - 0.5) * 0.32 + cote * (l2Hash3(c1 + 6.6) - 0.5) * 0.14 - gb * 0.75 * bev;
    vec3 n = l2Pente(nv, g, dpx, dpy, sx, sy);
    float rough = hc.y < 0.22 ? 0.16 : 0.02;
    float ecl;
    vec3 gold = l2MetalJaune(n, rough, 0.0, ecl);
    // nuances : la plupart en or jaune, quelques plaques plus rosées ou plus pâles
    gold *= hc.z < 0.14 ? vec3(1.04, 0.86, 0.8) : hc.z > 0.9 ? vec3(1.02, 1.0, 0.86) : vec3(1.0);
    // joint noir (laque) au fond de la fissure
    float joint = max(ncTrait(e, 0.00065, px), ncTrait(df, 0.0004, px) * fis);
    vec3 col = mix(gold, hexc(0x0a0705), joint * 0.92);
    em = gold * ecl * 0.3 * (1.0 - joint);
    return col * reliefMetal(0.6);
}

// ------------------------------------------------------------------ 87 Dichroïque (épique)
// panneaux de verre dichroïque sertis dans un cadre d'argent brossé : la couche mince du verre renvoie un vert d'eau qui
// vire au bleu puis au violet et au rose de biais (la teinte suit l'angle de vue, un peu différente d'un panneau à l'autre),
// le décor s'y reflète net ; dessous, à faible profondeur (parallaxe), un fond nuit pailleté de points fins qui scintillent
// avec l'angle ; cadre brossé le long de l'arme (reflet étiré en travers), chanfreins polis autour du verre
const float L2_LC = 0.085, L2_HR = 0.036, L2_CADRE = 0.0033, L2_COIN = 0.003;

float l2BordCol(float k) {
    return (k + 0.3 * (hash(vec3(k, 3.0, 1.0)) - 0.5)) * L2_LC;
}

vec3 l2Dichroique(vec3 p, out vec3 em) {
    em = vec3(0.0);
    NO_SHADE = 0.4;
    vec3 nv = normalize(viewNormal);
    vec3 dpx = dFdx(p), dpy = dFdy(p), sx = dFdx(viewPos), sy = dFdy(viewPos);
    vec3 fn = faceNormal(p);
    float px = ncPx(p);
    float axe;
    vec2 uv = l2Plan(p, fn, axe);
    mat3 tbn = ncRepere(nv, uv);
    vec3 par = ncParallaxe(p);
    // colonnes de largeur irrégulière le long de l'arme, rangées décalées d'une colonne à l'autre ; un panneau sur quatre
    // couvre deux rangées
    float k0 = floor(uv.x / L2_LC);
    float xa = l2BordCol(k0), xb = l2BordCol(k0 + 1.0), kc = k0;
    if (uv.x < xa) {
        kc = k0 - 1.0;
        xb = xa;
        xa = l2BordCol(k0 - 1.0);
    } else if (uv.x >= xb) {
        kc = k0 + 1.0;
        xa = xb;
        xb = l2BordCol(k0 + 2.0);
    }
    float ph = hash(vec3(kc, 5.0, 2.0)) * L2_HR + axe * 0.37;
    float jr = floor((uv.y + ph) / L2_HR);
    float ya = jr * L2_HR - ph, yb = ya + L2_HR;
    if (hash(vec3(kc, jr, 4.0)) < 0.25) {
        yb += L2_HR;
    } else if (hash(vec3(kc, jr - 1.0, 4.0)) < 0.25) {
        ya -= L2_HR;
        jr -= 1.0;
    }
    vec2 cen = vec2(0.5 * (xa + xb), 0.5 * (ya + yb));
    vec2 hs = vec2(0.5 * (xb - xa), 0.5 * (yb - ya)) - L2_CADRE;
    vec2 dv = abs(uv - cen) - hs + L2_COIN;
    float sd = length(max(dv, 0.0)) + min(max(dv.x, dv.y), 0.0) - L2_COIN;      // < 0 dans le verre
    vec2 gd = (dv.x > 0.0 && dv.y > 0.0) ? normalize(dv) : (dv.x > dv.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
    gd *= sign(uv - cen + 1e-7);
    float verre = 1.0 - smoothstep(-0.5 * px, 0.5 * px, sd);
    float hp = hash(vec3(kc, jr, 9.0));
    float ndv = facing(nv);
    vec3 col = vec3(0.0);
    if (verre > 0.0) {
        // couche mince : épaisseur optique propre au panneau (vert d'eau, vert, bleu) ; de biais, le chemin dans la couche
        // raccourcit et la teinte glisse vers le bleu, le violet puis le rose
        float cosT = sqrt(1.0 - (1.0 - ndv * ndv) / 4.2);
        float trainee = vnoise(vec3(uv.x * 14.0, uv.y * 70.0, hp * 9.0 + axe));
        float ep = (0.472 + 0.06 * (hp - 0.5) + 0.03 * (trainee - 0.5)) * cosT;
        vec3 film = 0.5 + 0.5 * cos(TAU * 2.0 * ep / vec3(0.65, 0.53, 0.45));
        film = mix(film, vec3(dot(film, vec3(0.3333))), 0.12);
        vec3 r = reflWorld(nv);
        vec3 eS = envWorld(r, 0.012);
        float Ls = lum3(eS);
        float Fg = 0.04 + 0.96 * pow(1.0 - ndv, 5.0);
        // fond nuit à faible profondeur, points fins qui scintillent avec l'angle (pas de bruit de pixels : rayon >= 1 px)
        vec3 pf = p + par * 0.005;
        float st = l2Points(pf, 0.0045, 0.00045, px, r, 3.0) + 0.6 * l2Points(pf + par * 0.004, 0.0032, 0.00035, px, r, 11.0);
        vec3 fond = mix(hexc(0x03060c), hexc(0x0a2230), smoothstep(0.3, 0.8, fbm(pf * vec3(18.0, 30.0, 30.0), 2)));
        float lis = (1.0 - smoothstep(0.0, 0.0022, -sd)) * smoothstep(1.0, 2.5, 0.0022 / px);
        vec3 glass = film * (0.42 + 0.7 * pow(Ls, 0.75)) * (1.0 + 0.6 * lis) + eS * Fg * 0.55
                   + (fond + st * vec3(0.75, 0.95, 1.0)) * (1.0 - film * 0.6);
        col = glass * verre;
        em = st * vec3(0.5, 0.85, 1.0) * 0.35 * verre + film * 0.03 * verre;
    }
    if (verre < 1.0) {
        // argent brossé : reflet étiré en travers des stries (moyenne de trois normales penchées en travers), stries fines
        // effacées de loin ; chanfrein poli autour du verre (penché vers le verre)
        float ch = (1.0 - smoothstep(0.0, 0.0022, sd)) * smoothstep(1.0, 2.5, 0.0022 / px);
        vec3 nf = normalize(nv - (tbn[0] * gd.x + tbn[1] * gd.y) * 0.9 * ch);
        float strie = (vnoise(vec3(uv.x * 40.0, uv.y * 1500.0, axe * 7.0)) - 0.5) * (1.0 - smoothstep(0.0004, 0.0012, px));
        vec3 bt = tbn[1];
        vec3 fr = envWorld(reflWorld(normalize(nf + bt * 0.14)), 0.12) + envWorld(reflWorld(normalize(nf - bt * 0.14)), 0.12)
                + envWorld(reflWorld(normalize(nf + bt * strie * 0.3)), 0.06);
        float fnd = facing(nf);
        vec3 F0 = vec3(0.82, 0.84, 0.86);
        vec3 F = F0 + (vec3(1.0) - F0) * pow(1.0 - fnd, 5.0);
        vec3 argent = (fr * 0.3333 * F * 0.82 + F0 * 0.05) * vec3(0.96, 0.99, 1.0) * (1.0 + 0.06 * strie);
        col += argent * (1.0 - verre);
    }
    return col * reliefMetal(0.55);
}

// ------------------------------------------------------------------ 88 Vipère (légendaire)
// écailles de serpent en relief, en quinconce, qui se recouvrent (celle qui est le plus vers le canon passe dessus, bord libre
// relevé vers la crosse), carène au milieu ; or poli (chaque écaille renvoie le décor à sa façon) ; robe de vipère : réseau de
// losanges d'écailles noires (or noir, reflets irisés de biais) bordées d'écailles d'or pâle ; ombre de chaque écaille sur
// celles qu'elle recouvre
const float L2_EW = 0.024, L2_EH = 0.0085;

// robe au point c de l'arme : grandes selles (taches étirées le long de l'arme), lues dans un champ lisse en 3D : le dessin
// passe d'une pièce à l'autre sans coupure
float l2Robe(vec3 c) {
    return 0.5 + 2.6 * (fbm(c * vec3(6.5, 11.0, 11.0) + vec3(3.0, 7.0, 1.0), 3) - 0.44);
}

// or noir poli aux reflets irisés de biais (comme une peau de serpent arc-en-ciel)
vec3 l2NoirIrise(vec3 n) {
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 e = envWorld(r, 0.03);
    float F = 0.08 + 0.92 * pow(1.0 - ndv, 4.0);
    vec3 irid = spectrum(fract(0.55 + 1.6 * (1.0 - ndv) + 0.15 * r.y));
    return hexc(0x060504) + e * e * 0.12 * F + irid * pow(1.0 - ndv, 2.0) * 0.16 * (0.4 + 0.6 * min(lum3(e), 1.5));
}

vec3 l2Vipere(vec3 p, out vec3 em) {
    NO_SHADE = 0.45;
    vec3 nv = normalize(viewNormal);
    vec3 fn = faceNormal(p);
    float px = ncPx(p);
    float axe;
    vec2 uv = l2Plan(p, fn, axe);
    mat3 tbn = ncRepere(nv, uv);
    float pps = L2_EW / px;
    float fine = smoothstep(3.0, 7.0, pps);
    // écaille visible : parmi les 9 voisines, celle qui couvre le point et dont le centre est le plus vers le canon ;
    // demi-diagonales du losange de pavage : 0.5 dans les deux sens (coordonnées réduites)
    const float RS = 0.55, EXPO = 1.8;
    float j0 = floor(uv.y / L2_EH);
    float Fk[9];
    float Ck[9];
    float bu = -1e9, bF = 9.0;
    vec2 bq = vec2(0.0), bc = vec2(0.0);
    for (int k = 0; k < 9; k++) {
        float j = j0 + float(k / 3 - 1);
        float off = 0.5 * mod(j, 2.0);
        float i = floor(uv.x / L2_EW - off + 0.5) + float(k - (k / 3) * 3 - 1);
        vec2 c = vec2((i + off) * L2_EW, (j + 0.5) * L2_EH);
        vec2 q = (uv - c) / vec2(L2_EW, 2.0 * L2_EH);
        vec2 aq = abs(q) + 1e-6;
        float F = pow(pow(aq.x, EXPO) + pow(aq.y, EXPO), 1.0 / EXPO) / RS;
        Fk[k] = F;
        Ck[k] = c.x + 0.001 * c.y;
        if (F < 1.0 && Ck[k] > bu) {
            bu = Ck[k];
            bF = F;
            bq = q;
            bc = c;
        }
    }
    // ombre portée : écailles placées au-dessus (plus vers le canon) dont le bord libre passe tout près
    float ombre = 0.0;
    for (int k = 0; k < 9; k++) {
        if (Ck[k] > bu + 1e-5) ombre = max(ombre, 1.0 - smoothstep(1.0, 1.16, Fk[k]));
    }
    // relief de l'écaille (hauteur en unités de l'arme) : bombée, bord libre relevé vers la crosse, carène au milieu
    float qy = bq.y;
    float dhx = -2.0 * bq.x / (RS * RS) * 0.6 - 0.3;
    float dhy = -2.0 * qy / (RS * RS) * 0.6 - 0.12 * qy / sqrt(qy * qy + 0.0006);
    float Hs = 0.17 * L2_EW;
    vec2 pente = vec2(dhx * Hs / L2_EW, dhy * Hs / (2.0 * L2_EH)) * fine;
    vec3 n = normalize(tbn * vec3(-pente, 1.0));
    // robe : couleur de l'écaille (motif lu à son centre) ; de loin (écailles sous ~3 pixels) motif lissé au pixel
    float rb = mix(l2Robe(p), l2Robe(l2Point(p, bc, axe)), smoothstep(1.5, 3.0, pps));
    // contour noir de la selle, cœur bronze sombre, écailles d'or pâle juste autour
    float wNoir = 1.0 - smoothstep(0.045, 0.06, abs(rb - 0.6));
    float wCoeur = smoothstep(0.64, 0.66, rb) * (1.0 - wNoir);
    float wPale = (1.0 - smoothstep(0.03, 0.05, abs(rb - 0.515))) * (1.0 - wNoir) * (1.0 - wCoeur);
    vec3 he = hash3(vec3(bc * 37.0, axe));
    float ecl;
    vec3 gold = l2MetalJaune(n, 0.03, 0.0, ecl) * (0.9 + 0.2 * he.x);
    vec3 pale = mix(gold, vec3(lum3(gold)) * vec3(1.05, 1.0, 0.86), 0.35) * 1.08;
    vec3 col = mix(gold, pale, wPale);
    col = mix(col, gold * vec3(0.5, 0.33, 0.2), wCoeur);
    if (wNoir > 0.0) col = mix(col, l2NoirIrise(n), wNoir);
    // liseré du bord libre (épaisseur de l'écaille) et ombre sur les écailles recouvertes : de près seulement
    col *= 1.0 - 0.45 * smoothstep(0.86, 0.995, bF) * fine;
    col *= 1.0 - 0.6 * ombre * fine;
    em = gold * ecl * 0.25 * (1.0 - wNoir) * (1.0 - ombre);
    return col * reliefMetal(0.55);
}

// ------------------------------------------------------------------ 89 Rouages (légendaire, animé)
// mouvement d'horlogerie vivant : roues ajourées en laiton poli (jante, bras, dents), grandes et petites en damier, qui
// s'engrènent toutes et tournent (deux voisines en sens contraire, la petite 1,5 fois plus vite) ; petits pignons d'acier
// bleui avec un rubis serti en chaton d'or, posés au-dessus aux coins ; platine anthracite à côtes de Genève très douces ;
// sous chaque roue, une lueur vert d'eau qui pulse (l'énergie qui fait tourner le mécanisme) se voit à travers les ajours ;
// chanfreins polis, ombres portées
const float L2_RC = 0.064;

// roue dentée de centre c (rayon primitif R, N dents, K bras), angle phi : couverture du métal (bords adoucis sur un pixel) ;
// ajour : où le fond se voit à travers la roue ; dch : direction (plan du motif) vers laquelle penche le chanfrein ; cf :
// force du chanfrein. Hauteur des dents : moitié du pas (même pas pour toutes les roues : elles s'engrènent)
float l2Roue(vec2 uv, vec2 c, float R, float N, float K, float phi, float px, out float ajour, out vec2 dch, out float cf) {
    vec2 d = uv - c;
    float r = length(d) + 1e-7;
    vec2 rad = d / r;
    float th = atan(d.y, d.x) - phi;
    float Ht = 0.5 * TAU * R / N;
    float Rr = R - 0.5 * Ht, Ro = R + 0.5 * Ht;
    // dents : trapèzes (plus étroits en tête) ; de loin (période sous ~3 pixels), couronne moyenne
    float a = fract(th * N / TAU + 0.5) - 0.5;
    float wt = mix(0.27, 0.15, clamp((r - Rr) / (Ro - Rr), 0.0, 1.0));
    float per = TAU * r / N;
    float sdDent = min((wt - abs(a)) * per, Ro - r);
    float covD = smoothstep(-0.5 * px, 0.5 * px, sdDent);
    covD = mix(0.42 * (1.0 - smoothstep(Ro - 0.5 * px, Ro + 0.5 * px, r)), covD, smoothstep(2.0, 4.0, per / px));
    float covOut = max(covD, 1.0 - smoothstep(Rr - 0.5 * px, Rr + 0.5 * px, r));
    // ajours : entre la jante, le moyeu et les bras (bras droits, un peu plus larges au moyeu)
    float rimW = 0.19 * R, hubR = 0.25 * R;
    float ks = floor(th * K / TAU + 0.5);
    float ang = ks * TAU / K;
    float dS = r * abs(sin(th - ang));
    float ws = R * mix(0.12, 0.085, clamp((r - hubR) / (Rr - rimW - hubR), 0.0, 1.0));
    float t1 = Rr - rimW - r, t2 = r - hubR, t3 = dS - ws;
    float sdCut = min(min(t1, t2), t3);
    float cov = covOut * (1.0 - smoothstep(-0.5 * px, 0.5 * px, sdCut));
    ajour = covOut * smoothstep(-0.5 * px, 0.5 * px, sdCut);
    // chanfrein : vers l'ajour le plus proche (dans le métal), ou vers l'extérieur au bord de la roue
    float bw = 0.07 * R;
    vec2 sp = vec2(cos(ang + phi), sin(ang + phi));
    vec2 perp = d - sp * dot(d, sp);
    vec2 g3 = perp / (length(perp) + 1e-9);
    dch = t1 < t2 && t1 < t3 ? -rad : (t2 < t3 ? rad : g3);
    cf = (1.0 - smoothstep(0.0, bw, -sdCut)) * step(sdCut, 0.0);
    float sdo = r < Rr ? Rr - r : sdDent;
    float co = (1.0 - smoothstep(0.0, bw, sdo)) * step(0.0, sdo);
    if (co > cf) {
        cf = co;
        dch = rad;
    }
    cf *= smoothstep(1.0, 2.5, bw / px);
    return cov;
}

// acier bleui poli (pignons) : le reflet du décor passe par une rampe bleu nuit -> bleu roi -> bleu ciel
vec3 l2AcierBleu(vec3 n) {
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    float L = lum3(envWorld(r, 0.05)) + 0.06;
    L += 0.4 * pow(1.0 - ndv, 5.0);
    float tt = 1.0 - exp(-0.9 * L);
    float s[6] = float[6](0.0, 0.2, 0.42, 0.62, 0.82, 1.0);
    vec3 c[6] = vec3[6](hexc(0x02040e), hexc(0x081a4a), hexc(0x1438a0), hexc(0x2f62d8), hexc(0x7fb0ff), hexc(0xe6f2ff));
    return ramp6(tt, s, c);
}

vec3 l2Rouages(vec3 p, out vec3 em) {
    float t = CamoTime;
    NO_SHADE = 0.4;
    vec3 nv = normalize(viewNormal);
    vec3 fn = faceNormal(p);
    float px = ncPx(p);
    float axe;
    vec2 uv = l2Plan(p, fn, axe);
    mat3 tbn = ncRepere(nv, uv);
    // dessus et dessous : une rangée de roues centrée sur la ligne du milieu de l'arme
    uv.y += axe > 0.5 && axe < 1.5 ? 0.5 * L2_RC : 0.0;
    // ombre portée : lumière venue d'en haut du motif
    const vec2 OMB = vec2(0.0022, -0.003);
    // --- roues (laiton) en damier : grandes (rayon primitif 0,6 pas, 24 dents) et petites (0,4 pas, 16 dents) ; même pas
    // de denture, la petite décalée d'une demi-dent : toutes les voisines s'engrènent (24 et 16 : multiples de 4, aussi en
    // vertical). Les quatre centres autour du point suffisent. Animation de 4 s qui boucle exactement : en 4 s la grande
    // roue (6 bras) tourne d'un sixième de tour, la petite (4 bras) d'un quart, le pignon (3 bras) d'un tour
    float w = PI / 12.0 * t;
    vec2 g0 = floor(uv / L2_RC - 0.5);
    float cov1 = 0.0, aj1 = 0.0, cf1 = 0.0, omb1 = 0.0, puits = 0.0, rc1 = 1.0;
    vec2 dch1 = vec2(0.0), cen1 = vec2(0.0);
    float hub1 = 9.0;
    for (int k = 0; k < 4; k++) {
        vec2 ij = g0 + vec2(float(k - (k / 2) * 2), float(k / 2));
        vec3 h = hash3(vec3(ij, 7.0 + axe));
        vec2 c = (ij + 0.5) * L2_RC;
        bool grande = mod(ij.x + ij.y, 2.0) < 0.5;
        float R = (grande ? 0.6 : 0.4) * L2_RC;
        float N = grande ? 24.0 : 16.0;
        float phi = grande ? w : -1.5 * w + PI / 16.0;
        float K = grande ? 6.0 : 4.0;
        float aj, cf;
        vec2 dch;
        float cv = l2Roue(uv, c, R, N, K, phi, px, aj, dch, cf);
        omb1 = max(omb1, 1.0 - smoothstep(R * 0.93, R * 1.07, length(uv - OMB - c)));
        if (cv > cov1) {
            cov1 = cv;
            dch1 = dch;
            cf1 = cf;
            cen1 = c;
            rc1 = R;
        }
        aj1 = max(aj1, aj);
        float rr = length(uv - c);
        puits = max(puits, 1.0 - smoothstep(R * 0.66, R * 0.8, rr));
        hub1 = min(hub1, rr / R);
    }
    // --- pignons (acier bleui, rubis) aux coins de la grille, au-dessus des roues ; un coin sur trois environ
    // coin de la grille le plus proche
    vec2 cn = floor(uv / L2_RC + 0.5);
    vec3 hp = hash3(vec3(cn, 13.0 + axe));
    float cov2 = 0.0, cf2 = 0.0, omb2 = 0.0, rub = 0.0, chaton = 0.0;
    vec2 dch2 = vec2(0.0), dq = vec2(0.0);
    float R2 = 0.17 * L2_RC;
    if (hp.x > 0.64) {
        vec2 c = cn * L2_RC;
        float phi = (hp.y < 0.5 ? 1.0 : -1.0) * (TAU / 4.0) * t + hp.z * TAU;
        float aj, cf;
        vec2 dch;
        cov2 = l2Roue(uv, c, R2, 10.0, 3.0, phi, px, aj, dch, cf);
        cf2 = cf;
        dch2 = dch;
        omb2 = 1.0 - smoothstep(R2 * 0.95, R2 * 1.12, length(uv - OMB * 1.5 - c));
        dq = (uv - c) / (0.045 * L2_RC);
        float rq = length(dq);
        float aq = px / (0.045 * L2_RC);
        rub = 1.0 - smoothstep(1.0 - aq, 1.0 + aq, rq);
        chaton = (1.0 - smoothstep(1.4 - aq, 1.4 + aq, rq)) * (1.0 - rub);
        cov2 = max(cov2, rub + chaton);
    }
    // de loin (une case de la grille sous ~20 pixels) : roues fondues en leur teinte moyenne, sans pignons ni chanfreins
    // (bras, jantes et dents de 1 à 3 pixels feraient du bruit de pixels en vue joueur)
    float lodR = smoothstep(9.0, 20.0, L2_RC / px);
    cov1 = mix(0.6, cov1, lodR);
    cf1 *= lodR;
    cov2 *= lodR;
    rub *= lodR;
    chaton *= lodR;
    // --- fond (platine, ou puits d'énergie sous les roues) : seulement là où une roue ne le cache pas
    vec3 col = vec3(0.0);
    em = vec3(0.0);
    if (cov1 < 0.999) {
        // platine : côtes de Genève très douces (bandes brossées en arc : la normale penche d'un bord à l'autre), acier
        // anthracite ; effacées de loin
        vec2 dirC = normalize(vec2(1.0, 0.55));
        float fC = fract(dot(uv, dirC) / 0.011) - 0.5;
        float tiltC = fC * 0.12 * (1.0 - smoothstep(0.0012, 0.003, px));
        vec3 nC = normalize(nv + (tbn[0] * dirC.x + tbn[1] * dirC.y) * tiltC);
        vec3 platine = hexc(0x0a0b0d) + envWorld(reflWorld(nC), 0.25) * 0.14 * vec3(0.9, 0.95, 1.0);
        // puits d'énergie sous les roues : vert d'eau qui pulse et circule (vu à travers les ajours) ; une vague de lumière
        // court le long de l'arme, de la crosse vers le canon (boucle de 4 s)
        float fl = fbm(vec3(uv * 60.0, 0.0) + vec3(0.6 * cos(TAU * t / 4.0), 0.6 * sin(TAU * t / 4.0), 0.0), 3);
        float vague = pow(0.5 + 0.5 * sin(TAU * (p.x * 1.5 - t / 4.0)), 8.0);
        float pouls = 0.55 + 0.25 * sin(TAU * t / 4.0 - hub1 * 3.0) + 0.9 * vague;
        vec3 nrj = mix(hexc(0x01100f), hexc(0x16e6c4), smoothstep(0.45, 0.85, fl)) * pouls * (1.0 - 0.55 * smoothstep(0.2, 0.75, hub1));
        col = mix(platine, nrj * 0.55 + hexc(0x021010), puits) * (1.0 - 0.6 * omb1 * (1.0 - cov1));
        em = nrj * puits * (1.0 - cov1) * 0.9 * (1.0 - cov2);
    }
    if (cov1 > 0.0) {
        vec3 nb = normalize(nv + (tbn[0] * dch1.x + tbn[1] * dch1.y) * 0.8 * cf1);
        float ecl;
        vec3 laiton = l2MetalJaune(nb, 0.04, 1.0, ecl);
        // arbre au centre de la roue (acier sombre poli)
        float ra = 0.09 * rc1;
        float arbre = 1.0 - smoothstep(ra - px, ra + px, length(uv - cen1));
        laiton = mix(laiton, hexc(0x1a1c20) + envWorld(reflWorld(nv), 0.05) * 0.35, arbre);
        col = mix(col, laiton, cov1);
        em += laiton * ecl * 0.15 * cov1;
    }
    col *= 1.0 - 0.55 * omb2 * (1.0 - cov2);
    if (cov2 > 0.0) {
        vec3 ns = normalize(nv + (tbn[0] * dch2.x + tbn[1] * dch2.y) * 0.8 * cf2);
        vec3 acier = l2AcierBleu(ns);
        // rubis en cabochon (bombé) dans un chaton d'or
        vec3 nr = normalize(tbn * vec3(dq * 0.6, 1.0));
        float spec = pow(max(dot(reflWorld(nr), normalize(vec3(0.42, 0.68, -0.6))), 0.0), 40.0);
        vec3 rubis = mix(hexc(0x3a0008), hexc(0xc80c28), 0.3 + 0.7 * (1.0 - min(length(dq), 1.0) * 0.7))
                   + spec * vec3(1.0, 0.8, 0.85) * 1.5 + lum3(envWorld(reflWorld(nr), 0.1)) * hexc(0xff4060) * 0.1;
        float eo;
        vec3 orC = l2MetalJaune(nv, 0.05, 0.0, eo);
        vec3 pig = mix(acier, orC, chaton);
        pig = mix(pig, rubis, rub);
        col = mix(col, pig, min(cov2, 1.0));
        em += rubis * rub * 0.1;
    }
    return col * reliefMetal(0.5);
}

// ------------------------------------------------------------------ 212 Prisme (skin X mythique, animé)
// cristal taillé : éclats (Voronoï 3D, bords droits) taillés en pyramide basse, une facette par voisin (chaque facette est un
// petit miroir penché) ; teintes de joyau (indigo, violet, magenta, bleu électrique, cyan, quelques éclats d'ambre)
// regroupées par zones ; chaque facette renvoie le décor lié au monde dans sa teinte, dispersion (franges spectrales) sur les
// reflets forts, éclairs de soleil sur les facettes ; arêtes en lumière fine ; une onde de lumière intérieure parcourt
// l'arme lentement (émission : il luit la nuit) ; petites pièces et poignée en verre noir poli
vec3 l2Joyau(float h) {
    float s[6] = float[6](0.0, 0.2, 0.4, 0.6, 0.8, 1.0);
    vec3 c[6] = vec3[6](hexc(0x1c0c90), hexc(0x5a1ee0), hexc(0x9a2cf2), hexc(0xd42cc8), hexc(0x2a48ff), hexc(0x16c4ff));
    return ramp6(h, s, c);
}

vec3 l2Prisme(vec3 p, out vec3 em) {
    float t = CamoTime;
    NO_SHADE = 0.5;
    vec3 nv = normalize(viewNormal);
    vec3 dpx = dFdx(p), dpy = dFdy(p), sx = dFdx(viewPos), sy = dFdy(viewPos);
    vec3 fn = faceNormal(p);
    float px = ncPx(p);
    const vec3 S = vec3(21.0, 33.0, 33.0);
    vec3 q = p * S;
    vec3 c1, p1, p2;
    l2Voronoi(q, 0.8, c1, p1, p2);
    vec3 gb;
    float e = l2Bord(q, p1, p2, S, fn, gb);
    vec3 hc = l2Hash3(c1 + 9.1);
    vec3 ht = l2Hash3(c1 + 2.3);
    // facette : la pyramide de l'éclat descend vers le voisin p2 (pente de 20 à 40 degrés), plus une inclinaison propre
    vec3 g = -gb * (0.38 + 0.42 * hc.x) + (ht - 0.5) * 0.3;
    vec3 n = l2Pente(nv, g, dpx, dpy, sx, sy);
    vec3 r = reflWorld(n);
    float ndv = facing(n);
    vec3 env = envWorld(r, 0.02);
    float L = lum3(env);
    // teinte : zones lentes sur l'arme + écart propre à l'éclat ; un éclat sur 14 en ambre
    float zone = 0.25 + 0.5 * vnoise(p * vec3(1.8, 2.6, 2.6) + vec3(5.0, 1.0, 3.0));
    float h = clamp(0.45 + 2.2 * (zone - 0.5) + 0.45 * (hc.y - 0.5) + 0.32 * dot(r, normalize(vec3(0.5, 0.75, -0.4))), 0.0, 1.0);
    vec3 gem = hc.z > 0.955 ? mix(hexc(0xff9a1a), hexc(0xffd86a), hc.x) : l2Joyau(h);
    // la lumière se concentre près des arêtes de l'éclat (cœur plus profond)
    gem *= 0.8 + 0.45 * (1.0 - smoothstep(0.0, 0.007, -e));
    float F = 0.05 + 0.95 * pow(1.0 - ndv, 5.0);
    vec3 col = gem * (0.03 + 0.8 * pow(L, 1.35)) + env * F * 0.22;
    // dispersion : franges spectrales sur les reflets forts, propres à chaque facette
    float fs = smoothstep(1.1, 3.0, L);
    col += spectrum(fract(dot(r, vec3(1.7, 2.3, 1.1)) * 1.4 + hc.x)) * fs * 0.4;
    // éclair de soleil sur la facette, éclat qui s'allume de temps en temps
    float soleil = pow(max(dot(r, normalize(vec3(0.42, 0.68, -0.6))), 0.0), 260.0);
    float tw = pow(max(0.0, sin(TAU * t / 4.0 + hc.z * 60.0)), 30.0) * step(0.7, ht.z);
    // arêtes des éclats : fil de lumière
    float arete = ncTrait(e, 0.00055, px);
    col = mix(col, mix(gem, vec3(0.85, 0.92, 1.0), 0.4) * (0.45 + 0.45 * min(L, 2.0)), arete * 0.55);
    // onde de lumière intérieure qui parcourt l'arme (de la crosse vers le canon)
    float onde = pow(0.5 + 0.5 * sin(p.x * 8.0 - TAU * t / 4.0 + hc.y * 2.0), 6.0);
    em = gem * (0.1 + 0.5 * onde) * (0.6 + 0.4 * hc.x) + arete * vec3(0.55, 0.8, 1.0) * (0.2 + 0.4 * onde)
       + (soleil * 2.0 + tw * 0.8) * mix(gem, vec3(1.0), 0.6);
    // petites pièces et poignée : verre noir poli
    float acc = clamp((1.0 - smoothstep(0.004, 0.009, abs(PART - 0.97))) + (1.0 - smoothstep(0.004, 0.009, abs(PART - 0.985))), 0.0, 1.0);
    if (acc > 0.0) {
        float nd = facing(nv);
        vec3 en = envWorld(reflWorld(nv), 0.015);
        vec3 noir = hexc(0x050409) + en * en * 0.45 * (0.06 + 0.94 * pow(1.0 - nd, 4.0)) + hexc(0x3a1a80) * pow(1.0 - nd, 2.0) * 0.2;
        col = mix(col, noir, acc);
        em *= 1.0 - acc;
    }
    return col * reliefMetal(0.55);
}
#endif

vec3 camoColor(int id, vec3 p, out vec3 em) {
    em = vec3(0.0);
#if CAMO_ID == 1
    return goldCod(p, em);
#elif CAMO_ID == 5
    return diamondCod(p, em);
#elif CAMO_ID == 71
    return blackIceEpic(p, em);
#elif CAMO_ID == 7
    return lava(p, em);
#elif CAMO_ID == 8
    return galaxy(p, em);
#elif CAMO_ID == 9
    return plasma(p, em);
#elif CAMO_ID == 10
    return toxic(p, em);
#elif CAMO_ID == 11
    return blackIce(p, em);
#elif CAMO_ID == 12
    return honeycomb(p, em);
#elif CAMO_ID == 13
    return carbon(p, em);
#elif CAMO_ID == 14
    return woodland(p, em);
#elif CAMO_ID == 15
    return digital(p, em);
#elif CAMO_ID == 16
    return tiger(p, em);
#elif CAMO_ID == 30
    return solar(p, em);
#elif CAMO_ID == 31
    return borealis(p, em);
#elif CAMO_ID == 32
    return orion(p, em);
#elif CAMO_ID == 33
    return afterlife(p, em);
#elif CAMO_ID == 34
    return biolum(p, em);
#elif CAMO_ID == 35
    return genesis(p, em);
#elif CAMO_ID == 36
    return singularity(p, em);
#elif CAMO_ID == 37
    return nexusHorizon(p, em);
#elif CAMO_ID == 38
    return darkAether(p, em);
#elif CAMO_ID == 39
    return darkMatterNuclear(p, em);
#elif CAMO_ID == 40
    return infestation(p, em);
#elif CAMO_ID == 41
    return damascus(p, em);
#elif CAMO_ID == 42
    return atomic(p, em);
#elif CAMO_ID == 43
    return obsidianCod(p, em);
#elif CAMO_ID == 44
    return liquidChromeCod(p, em);
#elif CAMO_ID == 45
    return glitch(p, em);
#elif CAMO_ID == 46
    return dragon(p, em);
#elif CAMO_ID == 47
    return circuit(p, em);
#elif CAMO_ID == 48
    return rust(p, em);
#elif CAMO_ID == 49
    return amethystGem(p, em);
#elif CAMO_ID == 70
    return storm(p, em);
#elif CAMO_ID == 80
    return opale(p, em);
#elif CAMO_ID == 81
    return marbreNoir(p, em);
#elif CAMO_ID == 82
    return patine(p, em);
#elif CAMO_ID == 83
    return neon(p, em);
#elif CAMO_ID == 84
    return rubis(p, em);
#elif CAMO_ID == 85
    return l2Forge(p, em);
#elif CAMO_ID == 86
    return l2OrCraquele(p, em);
#elif CAMO_ID == 87
    return l2Dichroique(p, em);
#elif CAMO_ID == 88
    return l2Vipere(p, em);
#elif CAMO_ID == 89
    return l2Rouages(p, em);
#elif CAMO_ID >= 200
    return skinSurface(p, em);
#elif (CAMO_ID >= 50 && CAMO_ID < 70) || (CAMO_ID >= 100 && CAMO_ID < 200)
    return knifeFinish(p, em);
#elif CAMO_ID >= 20
    return solidPaint(id, p, em);
#else
    return darkMatter(p, em);
#endif
}

// partie de l'arme lue dans la carte (canal A) : 1 = corps, 0.75 = chargeur, 0.5 = canon, 0.25 = manche métal de couteau, 0 = jamais
bool camoCovers(float a, int parts) {
    int bit = a > 0.9 ? 1 : a > 0.62 ? 2 : a > 0.35 ? 4 : a > 0.15 ? 8 : 0;
    return (parts & bit) != 0;
}
#undef viewNormal
// === CAMO LIB END ===

// Carte de positions lue face par face (0.9.13) : alpha = partie + 2 x id de face (1..8, deux faces voisines sur la texture
// ont des id différents). Le filtrage bilinéaire est refait à la main en ne gardant que les texels de la face du texel le
// plus proche : les petites faces (4 texels de côté sur les modèles très détaillés) ne prennent plus les positions de faces
// voisines sur la texture mais lointaines sur l'arme (points et rayures parasites). Rend (position, partie).
vec4 posMap(vec2 puv) {
    ivec2 sz = textureSize(Sampler3, 0);
    ivec2 hi = sz - ivec2(1);
    vec2 tc = puv * vec2(sz) - 0.5;
    ivec2 i0 = ivec2(floor(tc));
    vec2 f = tc - vec2(i0);
    vec4 a = texelFetch(Sampler3, clamp(i0, ivec2(0), hi), 0);
    vec4 b = texelFetch(Sampler3, clamp(i0 + ivec2(1, 0), ivec2(0), hi), 0);
    vec4 c = texelFetch(Sampler3, clamp(i0 + ivec2(0, 1), ivec2(0), hi), 0);
    vec4 d = texelFetch(Sampler3, clamp(i0 + ivec2(1, 1), ivec2(0), hi), 0);
    vec4 n = f.y < 0.5 ? (f.x < 0.5 ? a : b) : (f.x < 0.5 ? c : d);
    float id = floor(n.a * 0.5 + 0.01);
    float wa = (1.0 - f.x) * (1.0 - f.y) * step(abs(floor(a.a * 0.5 + 0.01) - id), 0.5);
    float wb = f.x * (1.0 - f.y) * step(abs(floor(b.a * 0.5 + 0.01) - id), 0.5);
    float wc = (1.0 - f.x) * f.y * step(abs(floor(c.a * 0.5 + 0.01) - id), 0.5);
    float wd = f.x * f.y * step(abs(floor(d.a * 0.5 + 0.01) - id), 0.5);
    vec3 p = (a.xyz * wa + b.xyz * wb + c.xyz * wc + d.xyz * wd) / max(wa + wb + wc + wd, 1e-5);
    return vec4(p, clamp(n.a - 2.0 * id, 0.0, 1.0));
}

void main() {
    vec4 base = texture(Sampler0, texCoord0);
    if (base.a < 0.1) {
        discard;
    }
    vec2 puv = (texCoord0 - CamoUvRect.xy) / CamoUvRect.zw;
    vec4 pm = posMap(puv);
    bool inRect = puv.x >= 0.0 && puv.y >= 0.0 && puv.x <= 1.0 && puv.y <= 1.0;
    vec3 em = vec3(0.0);
    vec4 color = base;
    if (CamoId > 0 && inRect && camoCovers(pm.a, CamoParts)) {
        PART = pm.a;
        BASE_COL = base.rgb;
        VN_DETAIL = normalize(viewNormal);
        appliquerRelief(pm.xyz, pm.a);
#ifdef CAMO_RELIEF
        // camos refaits : le camo pose lui-même le relief de la texture d'origine (gravures, vis, arêtes)
        RELIEF = baseRelief(base, texCoord0);
        color = vec4(camoColor(CamoId, pm.xyz, em), 1.0);
#else
        vec3 camo = camoColor(CamoId, pm.xyz, em);
        // garde un soupçon des détails gravés de la texture d'origine
        float lum = dot(base.rgb, vec3(0.299, 0.587, 0.114));
        camo *= mix(1.0, clamp(lum * 2.0, 0.45, 1.15), 0.12);
        color = vec4(camo, 1.0);
#endif
        color.rgb *= DETAIL_SHADE;
    }
    color *= mix(vertexColor, vec4(1.0), NO_SHADE) * ColorModulator;
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
    color *= lightMapColor;
    color.rgb += em;
    if (CamoReveal >= 0.0) {
        // apparition : derrière le front l'arme est faite ; devant, un hologramme sombre strié de lumière ; le front brille
        // front orange (201, Phénix 210), violet (Prisme 212) ou bleu ; aucun skin n'a d'apparition aujourd'hui (SkinReveal.has)
        vec3 rc = CamoId == 201 || CamoId == 210 ? vec3(1.0, 0.78, 0.3) : CamoId == 212 ? vec3(0.72, 0.42, 1.0) : vec3(0.35, 0.85, 1.0);
        float gx = pm.x + 0.5;
        float front = CamoReveal * 1.08 - 0.04;
        float d = gx - front;
        vec3 g = fract(pm.xyz * 36.0);
        float grid = max(max(smoothstep(0.08, 0.0, min(g.x, 1.0 - g.x)), smoothstep(0.08, 0.0, min(g.y, 1.0 - g.y))), smoothstep(0.08, 0.0, min(g.z, 1.0 - g.z)));
        if (d > 0.0) {
            float fade = exp(-d * 9.0);
            color.rgb = rc * (0.05 + 0.55 * grid * (0.35 + 0.65 * fade)) + vec3(0.02, 0.03, 0.05);
        }
        float edge = exp(-abs(d) * 70.0);
        color.rgb += rc * edge * 2.6 + rc * grid * exp(-max(-d, 0.0) * 20.0) * 0.8 * step(d, 0.0);
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
