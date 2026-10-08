#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform vec4 Decalages;      // décalage (monde + vent) des deux couches, déjà ramené à la période de la texture
uniform vec3 Teinte;         // couleur des nuages éclairés (jour, couchant, nuit, orage)
uniform float Couverture;    // 0 ciel dégagé .. 1 couvert
uniform float Rayon;         // au-delà, les nuages se fondent dans le ciel

in vec2 local;

out vec4 fragColor;

void main() {
    if (length(local) > Rayon) {
        discard;
    }
    float d1 = texture(Sampler0, local / 512.0 + Decalages.xy).r;
    float d2 = texture(Sampler0, local / 190.0 + Decalages.zw).r;
    float d = d1 * 0.68 + d2 * 0.32;
    float seuil = mix(0.66, 0.20, Couverture);
    float a = smoothstep(seuil, seuil + 0.20, d);
    float bord = 1.0 - smoothstep(Rayon * 0.5, Rayon, length(local));
    // le cœur des nuages est plus sombre (épaisseur), les bords plus clairs
    vec3 c = Teinte * mix(1.04, 0.70, smoothstep(seuil, seuil + 0.45, d));
    fragColor = vec4(c, a * bord * 0.92) * ColorModulator;
}
