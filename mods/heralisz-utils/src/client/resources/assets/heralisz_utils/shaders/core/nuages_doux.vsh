#version 150

// Couche de nuages doux : un grand carré horizontal centré sur la caméra (coordonnées relatives à la caméra).

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform float Rayon;

out vec2 local;

void main() {
    // carré unité étiré au rayon d'affichage : rien n'est tramé au-delà
    vec3 p = vec3(Position.x * Rayon, Position.y, Position.z * Rayon);
    gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
    local = p.xz;
}
