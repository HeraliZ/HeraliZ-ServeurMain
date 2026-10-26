#version 150

uniform vec4 ColorModulator;
uniform float Visibilite;

in vec3 couleur;
in vec2 coin;
in float hauteur;
in float scint;

out vec4 fragColor;

void main() {
    float r2 = dot(coin, coin);
    if (r2 >= 1.0) {
        discard;
    }
    // point lumineux doux + très léger halo
    float lum = exp(-r2 * 7.0) + 0.22 * exp(-r2 * 2.2);
    // près de l'horizon, l'air épais rougit puis éteint les étoiles
    float ext = smoothstep(-0.03, 0.22, hauteur);
    vec3 c = couleur * mix(vec3(1.0, 0.72, 0.52), vec3(1.0), smoothstep(0.0, 0.35, hauteur));
    fragColor = vec4(c * ColorModulator.rgb, clamp(lum * ext * scint * Visibilite * ColorModulator.a, 0.0, 1.0));
}
