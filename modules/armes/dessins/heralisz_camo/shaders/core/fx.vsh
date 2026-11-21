#version 150

// Effets 3D des skins (CamoFx) : la géométrie arrive déjà dans le repère de l'arme dessinée.
in vec3 Position;
in vec4 Color;
in vec2 UV0;
in ivec2 UV1;
in ivec2 UV2;
in vec3 Normal;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec4 vColor;
out vec2 vUv;
out vec3 vNormal;
out vec3 vView;
out vec2 vSeed;

void main() {
    vec4 vp = ModelViewMat * vec4(Position, 1.0);
    gl_Position = ProjMat * vp;
    vColor = Color;
    vUv = UV0;
    vNormal = mat3(ModelViewMat) * Normal;
    vView = vp.xyz;
    vSeed = vec2(UV2);
}
