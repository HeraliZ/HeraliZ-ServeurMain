#version 150

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float Visibilite;

in vec2 texCoord0;
in vec4 couleur;
in float hauteur;

out vec4 fragColor;

void main() {
    vec4 t = texture(Sampler0, texCoord0);
    float ext = smoothstep(0.0, 0.40, hauteur);                      // s'efface dans l'air épais de l'horizon
    fragColor = vec4(t.rgb * couleur.rgb * ColorModulator.rgb, clamp(t.a * couleur.a * ext * Visibilite * 0.85, 0.0, 1.0));
}
