// === PROTOTYPE (rendus à valider, pas dans le mod) : skins mythiques « premium animés » 210 Faille, 211 Accès Rouge ===
// CamoReload : -1 = pas de rechargement ; 0..1 = disparition de l'ancien chargeur ; 1..2 = arrivée du nouveau ;
// 2..3 = enclenché (onde sur l'arme). Ne touche que le chargeur (PART ~ 0.75), sauf l'onde finale.
uniform float CamoReload;
// hauteur du chargeur dans le repère de l'arme (bas, haut) : en jeu, tirée de la carte de positions comme CamoAnchor
uniform vec2 CamoMagY;

bool estChargeur() { return PART > 0.62 && PART < 0.88; }

// distance (unités de l'arme) à la ligne de niveau v = c
float isoDist(float v, float c, vec3 p) {
    float g = length(vec2(dFdx(v), dFdy(v))) / max(length(vec2(length(dFdx(p)), length(dFdy(p)))), 1e-7);
    return abs(v - c) / max(g, 1e-3);
}

// ------------------------------------------------------------------ 210 Faille
// Verre volcanique noir poli. Une ou deux grandes fractures traversent l'arme, avec leurs ramifications : bord magenta,
// lumière cyan, fil blanc au cœur ; au fond de la fracture, l'espace de l'anomalie (nébuleuse et étoiles, décalés selon
// l'angle de vue). La lumière court le long des fractures et la pierre garde un halo autour.
vec3 failleCouleur(float k) {
    vec3 c = mix(hexc(0xff2bd6), hexc(0x22e3ff), smoothstep(0.2, 0.65, k));
    return mix(c, vec3(1.0), smoothstep(0.8, 1.0, k));
}

float failleV(vec3 p, out float vb) {
    vec3 q = p * vec3(3.1, 5.2, 5.2) + vec3(3.7, 1.1, 0.0);
    vec3 w = vec3(fbm(q * 1.6, 3), fbm(q * 1.6 + 5.1, 3), fbm(q * 1.6 + 9.3, 3));
    float v = fbm(q + 0.9 * w, 4) + 0.012 * (vnoise(p * 110.0) - 0.5);
    vb = fbm(p * vec3(9.0, 15.0, 15.0) + 2.5 * w, 3) + 0.01 * (vnoise(p * 160.0 + 3.0) - 0.5);
    return v;
}

vec3 faille(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 n = normalize(viewNormal);
    vec3 env = studio(n);
    float fres = pow(1.0 - clamp(abs(n.z), 0.0, 1.0), 2.0);
    // verre volcanique : noir à reflets prune, ondes de coulée très douces
    float coul = fbm(p * vec3(3.0, 14.0, 14.0), 3);
    vec3 col = mix(hexc(0x020104), hexc(0x0d0716), coul);
    col += env * (0.09 + 0.55 * fres) * vec3(0.82, 0.8, 0.95);
    float vb;
    float v = failleV(p, vb);
    float d = isoDist(v, 0.5, p);
    float dB = isoDist(vb, 0.5, p);
    float pres = exp(-d / 0.035);                      // ramifications seulement près des grandes fractures
    float coeur = smoothstep(0.0022, 0.0006, d);
    float bord = smoothstep(0.0105, 0.0026, d);
    float ram = smoothstep(0.0022, 0.0006, dB) * pres;
    float halo = exp(-d / 0.014) * 0.55 + exp(-dB / 0.006) * pres * 0.25;
    // la lumière court le long des fractures
    float flux = 0.6 + 0.4 * sin(p.x * 34.0 + p.y * 20.0 - t * 2.6);
    float souffle = 0.8 + 0.2 * sin(t * 1.2);
    // fond de la fracture : nébuleuse et étoiles vues « à travers » (parallaxe)
    vec3 dp = p * 7.0 + vec3(n.xy * 0.3, t * 0.04);
    vec3 neb = mix(hexc(0x220a3d), hexc(0x063a55), fbm(dp, 4)) * 1.6 + sxStars(dp * 3.0, 6.0, 0.9, t) * 0.8;
    float ouvert = max(bord, ram * 0.8);
    col = mix(col, neb, ouvert * 0.9);
    vec3 lumB = failleCouleur(0.05 + 0.25 * flux);
    vec3 lumC = failleCouleur(0.7 + 0.3 * flux);
    em = lumB * (bord * 0.7 + ram * 0.8) * flux * souffle + lumC * coeur * 1.4 * souffle;
    em += failleCouleur(0.0) * halo * 0.3 * souffle;
    // arêtes : liseré violet pâle
    em += hexc(0x9b6bff) * pow(fres, 3.0) * 0.16;
    // poussière d'étoiles très fine dans la pierre
    em += vec3(0.8, 0.9, 1.0) * sxStars(p, 420.0, 0.985, t) * 0.25;
    return col;
}

// rechargement de la Faille, sur le chargeur : l'ancien se fend de lumière et part en éclats aspirés par la faille ; le
// nouveau sort de la faille chauffé à blanc et se refroidit en obsidienne pendant qu'on l'enclenche
vec3 failleRecharge(vec3 p, vec3 col, inout vec3 em) {
    float r = CamoReload;
    if (r < 0.0) return col;
    float t = CamoTime;
    if (r >= 2.0) {
        // enclenché : les fractures de toute l'arme s'embrasent une demi-seconde
        float a = r - 2.0;
        em *= 1.0 + 2.2 * exp(-a * 5.0);
        return col;
    }
    if (!estChargeur()) return col;
    float bruit = fbm(p * 45.0, 3);
    float f1, f2; vec3 wc, wp;
    worley(p * 26.0, f1, f2, wc, wp);
    float fis = smoothstep(0.07, 0.0, f2 - f1);
    if (r < 1.0) {
        float avance = smoothstep(0.0, 0.5, r);
        vec3 lum = failleCouleur(0.55 + 0.45 * sin(t * 11.0 + hash(wc) * 6.0));
        col = mix(col, lum * 0.5, fis * avance);
        em += lum * fis * avance * 1.8;
        // éclats : chaque cellule part à son tour
        float seuil = smoothstep(0.45, 1.0, r) * 1.15;
        float h = hash(wc) * 0.7 + bruit * 0.3;
        if (h < seuil - 0.04) discard;
        em += failleCouleur(0.9) * smoothstep(0.1, 0.0, abs(h - seuil)) * 2.4;
        return col;
    }
    // arrivée : condensation (les éclats se ressoudent), puis la lumière se retire dans les fissures
    float a = r - 1.0;
    float cond = smoothstep(0.0, 0.3, a);
    float h = hash(wc) * 0.7 + bruit * 0.3;
    if (h > cond * 1.1 + 0.02) discard;
    float chaud = 1.0 - smoothstep(0.25, 1.0, a);
    vec3 lum = failleCouleur(0.7 + 0.3 * bruit);
    col = mix(col, lum * 0.6, chaud * (1.0 - fis * 0.5));
    em += lum * (chaud * 1.5 + fis * chaud * 1.2);
    return col;
}

// ------------------------------------------------------------------ 211 Accès Rouge
// Blindage de laboratoire : panneaux en angles vifs, céramique noire brillante et composite graphite mat (micro-hexagones),
// quelques panneaux rouges vernis marqués au pochoir. Les joints entre les panneaux sont des fentes ; certaines laissent
// passer une lumière rouge de sécurité qui pulse, et un balayage lumineux parcourt l'arme. Chargeur rouge vernis à chevrons.
void chebyshev(vec3 q, out float f1, out float f2, out vec3 cell) {
    vec3 b = floor(q);
    f1 = 9.0; f2 = 9.0; cell = b;
    for (int x = -1; x <= 1; x++)
    for (int y = -1; y <= 1; y++)
    for (int z = -1; z <= 1; z++) {
        vec3 cc = b + vec3(float(x), float(y), float(z));
        vec3 pt = cc + 0.2 + 0.6 * hash3(cc);
        vec3 dd = abs(q - pt);
        float d = max(dd.x, max(dd.y, dd.z));
        if (d < f1) { f2 = f1; f1 = d; cell = cc; }
        else if (d < f2) { f2 = d; }
    }
}

float hexMicro(vec2 s) {
    vec2 r = vec2(1.0, 1.732);
    vec2 a = mod(s, r) - r * 0.5;
    vec2 b = mod(s - r * 0.5, r) - r * 0.5;
    vec2 g = dot(a, a) < dot(b, b) ? a : b;
    float h = max(abs(g.x) * 0.866 + abs(g.y) * 0.5, abs(g.y));
    return smoothstep(0.42, 0.47, h);
}

vec3 accesRouge(vec3 p, out vec3 em) {
    float t = CamoTime;
    vec3 n = normalize(viewNormal);
    vec3 env = studio(n);
    float fres = pow(1.0 - clamp(abs(n.z), 0.0, 1.0), 2.0);
    vec3 fn = faceNormal(p);
    vec2 s = surf2d(p, fn);
    vec3 rouge = hexc(0xff1530);
    // panneaux
    float f1, f2; vec3 cell;
    chebyshev(p * vec3(7.0, 11.0, 11.0), f1, f2, cell);
    float joint = isoDist(f2 - f1, 0.0, p);
    float h = hash(cell + 4.0);
    float fente = smoothstep(0.0034, 0.0012, joint);
    float allume = step(0.45, hash(cell + 9.0));
    // matériaux des panneaux
    vec3 ceramique = mix(hexc(0x050506), hexc(0x101114), fbm(p * 30.0, 2)) + env * (0.12 + 0.6 * fres);
    vec3 graphite = mix(hexc(0x17181c), hexc(0x1f2125), hexMicro(s * 120.0)) + env * (0.03 + 0.12 * fres);
    vec3 laque = mix(hexc(0x7a0712), hexc(0xc4102a), 0.5 + 0.5 * env.g) + env * (0.1 + 0.5 * fres) * vec3(1.0, 0.75, 0.75);
    vec3 col = h < 0.48 ? ceramique : h < 0.88 ? graphite : laque;
    // pochoir blanc sur les panneaux rouges et quelques panneaux noirs
    vec2 gq = s * 70.0;
    float marque = glyph(fract(gq) - 0.5, hash(vec3(floor(gq), 5.0))) * step(0.55, hash(vec3(floor(gq / 3.0), 2.0)));
    float pochoir = (h > 0.88 || (h < 0.1)) ? marque * step(0.3, f1) : 0.0;
    col = mix(col, hexc(0xe6e2da), pochoir * 0.9);
    // petites pièces : acier sombre brossé
    if (PART > 0.96) col = hexc(0x2b2d33) * (0.75 + 0.35 * vnoise(vec3(s.x * 900.0, s.y * 20.0, 0.0))) + env * 0.25;
    // chargeur : rouge vernis à chevrons noirs
    if (estChargeur()) {
        float chev = step(0.5, fract((s.x * 1.4 + abs(s.y - 0.0) * 0.0 + s.y) * 18.0));
        float bande = smoothstep(0.02, 0.0, abs(fract(s.y * 3.0) - 0.5) - 0.18);
        col = mix(laque, hexc(0x0b0b0d), chev * bande);
    }
    // fentes : sombres, ou lumière rouge de sécurité
    float pulse = 0.4 + 0.6 * pow(0.5 + 0.5 * sin(t * 2.2 + h * 1.5), 2.0);
    float balayage = exp(-pow(fract(p.x * 0.7 - t * 0.3) - 0.5, 2.0) * 90.0);
    col = mix(col, hexc(0x020203), fente);
    em = rouge * fente * allume * (0.55 * pulse + 1.6 * balayage);
    em += rouge * exp(-joint / 0.006) * allume * 0.16 * pulse;
    em += rouge * pow(fres, 3.0) * 0.08;
    return col;
}

// rechargement d'Accès Rouge, sur le chargeur : l'ancien se coupe comme un hologramme et part en cubes ; le nouveau
// s'imprime en hologramme rouge (grille), une ligne de balayage le rend solide de bas en haut ; enclenché : onde rouge
// sur toute l'arme et lumières qui passent au vert une fraction de seconde (accès autorisé)
vec3 accesRecharge(vec3 p, vec3 col, inout vec3 em) {
    float r = CamoReload;
    if (r < 0.0) return col;
    float t = CamoTime;
    vec3 rouge = hexc(0xff1530);
    if (r >= 2.0) {
        float a = r - 2.0;
        float vert = smoothstep(0.0, 0.08, a) * (1.0 - smoothstep(0.55, 0.9, a));
        // onde : part du puits de chargeur vers les deux bouts, seulement dans les joints allumés
        float onde = exp(-pow((abs(p.x - 0.0) - a * 0.9) * 30.0, 2.0));
        float lum = max(max(em.r, em.g), em.b);
        float joints = clamp(lum * 1.6, 0.0, 1.0);
        vec3 c = mix(rouge, hexc(0x2bff7a), vert);
        em = mix(em, c * lum * 1.3, vert);
        em += c * onde * joints * 2.5;
        return col;
    }
    if (!estChargeur()) return col;
    vec3 fn = faceNormal(p);
    vec2 s = surf2d(p, fn);
    if (r < 1.0) {
        // instable : lignes de balayage, décalages, puis part en cubes
        float vox = 0.016;
        vec3 cell = floor(p / vox);
        float h = hash(cell);
        float seuil = smoothstep(0.35, 1.0, r) * 1.05;
        if (h < seuil) discard;
        float scan = step(0.5, fract(s.y * 150.0 + t * 9.0));
        float inst = smoothstep(0.05, 0.3, r);
        col = mix(col, rouge * 0.25, inst * 0.7);
        em += rouge * (smoothstep(0.12, 0.0, h - seuil) * 2.5 + inst * 0.5 * scan);
        return col;
    }
    // impression : au-dessus de la ligne, hologramme (grille et contour) ; en dessous, le vrai chargeur
    float a = r - 1.0;
    float hauteur = (p.y - CamoMagY.x) / max(CamoMagY.y - CamoMagY.x, 1e-3);   // 0 bas du chargeur -> 1 haut
    float ligne = mix(-0.15, 1.15, smoothstep(0.1, 0.95, a));
    float solide = step(hauteur, ligne);
    vec2 g = fract(s * 38.0);
    float grille = max(smoothstep(0.06, 0.0, 0.5 - abs(g.x - 0.5)), smoothstep(0.06, 0.0, 0.5 - abs(g.y - 0.5)));
    float scan = 0.65 + 0.35 * step(0.5, fract(s.y * 170.0 - t * 7.0));
    vec3 holo = rouge * (0.07 + 1.2 * grille) * scan;
    float front = exp(-pow((hauteur - ligne) * 26.0, 2.0));
    col = mix(holo * 0.15 + vec3(0.01, 0.0, 0.0), col, solide);
    em = mix(holo, em, solide) + vec3(1.0, 0.6, 0.6) * front * 2.4;
    return col;
}
