#version 150

// Rame du train HeraliZ (tampons du GPU, une matrice par véhicule) : éclairage directionnel des entités, lumière du
// monde au niveau du véhicule (uniforme Lumiere : bloc, ciel en 0..15) lue dans la carte de lumière, brouillard.

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec2 UV0;
in vec4 Color;
in vec3 Normal;

uniform sampler2D Sampler2;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;
uniform int FogShape;
uniform vec2 Lumiere;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;

void main() {
    vec4 vue = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vue;
    vertexDistance = length(vue.xyz);
    vec3 n = normalize(mat3(ModelViewMat) * Normal);
    vec4 lm = texture(Sampler2, clamp(Lumiere / 16.0, vec2(0.5 / 16.0), vec2(15.5 / 16.0)));   // comme les entités vanilla
    vertexColor = minecraft_mix_light(Light0_Direction, Light1_Direction, n, Color) * lm;
    texCoord0 = UV0;
}
