#version 150

#moj_import <light.glsl>
#moj_import <fog.glsl>

in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform sampler2D Sampler2;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform mat3 IViewRotMat;
uniform int FogShape;
uniform vec3 Light0_Direction;
uniform vec3 Light1_Direction;

out float vertexDistance;
out float shade;
out float finish;
out vec4 lightMapColor;
out vec2 gunUv;
out vec2 texCoord0;
out vec3 viewN;
out vec3 viewPos;

// Stickers HeralisZ : la finition arrive dans le rouge de la couleur du sommet (0 papier, 1 holo, 2 métallisé, 3 or) ;
// UV1 = UV de la texture de l'arme sous le sommet (x 32767), pour ne pas dessiner sur ses pixels transparents
void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    vertexDistance = fog_distance(ModelViewMat, IViewRotMat * Position, FogShape);
    shade = minecraft_mix_light(Light0_Direction, Light1_Direction, Normal, vec4(1.0)).r;
    finish = Color.r;
    lightMapColor = texelFetch(Sampler2, UV2 / 16, 0);
    gunUv = vec2(UV1) / 32767.0;
    texCoord0 = UV0;
    viewN = mat3(ModelViewMat) * Normal;
    viewPos = vp.xyz;
}
