#version 150

// Étoiles du ciel HeraliZ : position dans le repère du ciel (rayon 100), couleur = teinte × éclat,
// alpha de la couleur = phase du scintillement propre à chaque étoile, UV = coin du petit carré.

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 RotCiel;
uniform float GameTime;

out vec3 couleur;
out vec2 coin;
out float hauteur;
out float scint;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    hauteur = normalize((RotCiel * vec4(Position, 0.0)).xyz).y;      // hauteur réelle au-dessus de l'horizon
    couleur = Color.rgb;
    coin = UV0 * 2.0 - 1.0;
    // scintillement : deux ondes lentes par étoile, plus marqué près de l'horizon (air plus épais)
    float t = GameTime * 1200.0;                                     // secondes (un jour = 1200 s)
    float ph = Color.a * 6.2831853;
    float amp = mix(0.45, 0.12, clamp(hauteur * 1.7, 0.0, 1.0));
    float onde = (0.5 + 0.5 * sin(t * (2.1 + Color.a * 4.7) + ph)) * (0.6 + 0.4 * sin(t * 0.73 + ph * 3.0));
    scint = 1.0 - amp * onde;
}
