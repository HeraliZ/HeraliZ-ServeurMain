#version 150

// HeraliZ Tags - peinture a la bombe sur le mur (TAGS_V2_SPEC par. 3), sommets POSITION_COLOR_TEX_LIGHTMAP.
// Position = relative a l'origine de la cellule de 32 blocs du tag ; Origine = cette origine en monde (modulo 1024,
// pour garder la precision des flottants) : Origine + Position = position monde, alignee sur les pixels des blocs.

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV2;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;
uniform vec3 Origine;

out float vertexDistance;
out vec4 vertexColor;
out vec2 texCoord0;
out vec3 posMonde;

void main() {
    vec4 vue = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vue;
    // distance de brouillard : vecteur camera -> sommet ramene sur les axes du monde (sphere ou cylindre)
    vec3 rel = IViewRotMat * vue.xyz;
    vertexDistance = FogShape == 0 ? length(rel) : max(length(rel.xz), abs(rel.y));
    // gris d'ombrage directionnel cuit x lumiere du bloc (lightmap : jour/nuit, torches)
    vertexColor = Color * texelFetch(Sampler2, UV2 / 16, 0);
    texCoord0 = UV0;
    posMonde = Origine + Position;
}
