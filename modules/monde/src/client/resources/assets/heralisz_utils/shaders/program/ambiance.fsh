#version 150

// Filtre d'image de la map HeraliZ : une passe plein écran après le monde et l'arme en main, avant l'interface.
// Uniformes réglés à chaque image par fr.heralisz.utils.ambiance.FiltreImage (Nuit, Nuages, Pluie, Brume : 0..1).

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Nuit;
uniform float Nuages;
uniform float Pluie;
uniform float Brume;
uniform float Graine;
uniform float Force;

in vec2 texCoord;

out vec4 fragColor;

float bruit(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float luma(vec3 c) {
    return dot(c, vec3(0.2126, 0.7152, 0.0722));
}

void main() {
    vec3 src = texture(DiffuseSampler, texCoord).rgb;
    vec3 c = src;

    // 1. journées un peu moins claires, plus encore par temps couvert (la nuit, l'éclairage fait déjà le travail)
    c *= mix(mix(0.94, 0.88, Nuages), 1.0, Nuit);

    // 2. moins « jeu vidéo » : verts saturés ramenés vers l'olive, saturation générale baissée
    float vert = max(0.0, c.g - max(c.r, c.b));
    c.g -= vert * 0.40;
    c.r += vert * 0.12;
    float l = luma(c);
    float sat = mix(0.84, 0.68, Nuages);
    sat = mix(sat, 0.64, Pluie);
    sat = mix(sat, 0.55, Nuit);
    c = mix(vec3(l), c, sat);

    // 3. virage : ombres froides, hautes lumières un peu chaudes le jour, froides la nuit
    vec3 ombre = mix(vec3(0.94, 0.98, 1.05), vec3(0.86, 0.95, 1.16), Nuit);
    vec3 haute = mix(vec3(1.035, 1.0, 0.955), vec3(0.96, 1.0, 1.06), Nuit);
    c *= mix(ombre, haute, smoothstep(0.08, 0.75, l));

    // 4. contraste en S doux, noirs un peu plus profonds
    c = clamp(c, 0.0, 1.0);
    c = mix(c, c * c * (3.0 - 2.0 * c), 0.18);
    c = max(c - 0.010, 0.0) / 0.990;

    // 5. la nuit, l'œil voit bleu-gris dans le sombre (effet Purkinje)
    l = luma(c);
    float sombre = (1.0 - smoothstep(0.02, 0.30, l)) * Nuit;
    c = mix(c, vec3(l) * vec3(0.78, 0.90, 1.14), sombre * 0.55);

    // 6. brume du matin : léger voile laiteux (le brouillard fait le gros du travail)
    c = mix(c, vec3(luma(c)) * 0.9 + 0.08, Brume * 0.12 * (1.0 - Nuit));

    // 7. vignette, plus marquée la nuit
    vec2 d = (texCoord - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
    float v = smoothstep(0.45, 1.30, length(d));
    c *= 1.0 - v * mix(0.30, 0.45, Nuit);

    // 8. grain de pellicule, plus visible dans le sombre et la nuit (casse aussi les dégradés en escalier)
    float g = bruit(texCoord * OutSize + vec2(Graine * 517.0, Graine * 263.0)) - 0.5;
    c += g * mix(0.020, 0.042, Nuit) * (1.0 - 0.6 * luma(c));

    fragColor = vec4(mix(src, clamp(c, 0.0, 1.0), Force), 1.0);
}
