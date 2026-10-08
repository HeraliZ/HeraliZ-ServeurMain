#version 150

in vec3 Position;
in vec4 Color;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat4 RotCiel;

out vec2 texCoord0;
out vec4 couleur;
out float hauteur;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
    couleur = Color;
    hauteur = normalize((RotCiel * vec4(Position, 0.0)).xyz).y;
}
