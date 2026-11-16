#version 150

// Flou du monde derriere nos ecrans (heraliz_interface, kit/Flou.java). Un seul programme, trois usages :
//  - Reduction >= 2 : moyenne des Reduction x Reduction pixels de l'image sous chaque pixel de l'image reduite ;
//  - Radius >= 0.5  : flou gaussien separable, une passe horizontale puis une verticale (BlurDir), lectures de -Radius a
//                     Radius par pas d'un pixel ; sigma = Sigma (image reduite, Radius entier) ou, Sigma nul, Radius / 2
//                     (les passes en pleine resolution d'avant le 15/11) ;
//  - sinon          : simple copie, lue lissee (agrandissement de l'image reduite ; Radius 0 : image intacte).

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 BlurDir;
uniform float Radius;
uniform float Sigma;
uniform float Reduction;

out vec4 fragColor;

void main() {
    if (Reduction > 1.5) {
        // centres des Reduction x Reduction pixels de l'image sous ce pixel de l'image reduite
        float debut = -(Reduction - 1.0) * 0.5;
        vec3 somme = vec3(0.0);
        for (float y = 0.0; y < Reduction; y += 1.0) {
            for (float x = 0.0; x < Reduction; x += 1.0) {
                somme += texture(DiffuseSampler, texCoord + oneTexel * vec2(debut + x, debut + y)).rgb;
            }
        }
        fragColor = vec4(somme / (Reduction * Reduction), 1.0);
        return;
    }
    if (Radius < 0.5) {
        fragColor = vec4(texture(DiffuseSampler, texCoord).rgb, 1.0);
        return;
    }
    float sigma = Sigma > 0.0 ? Sigma : Radius * 0.5;
    float k = -0.5 / (sigma * sigma);
    vec3 acc = vec3(0.0);
    float total = 0.0;
    for (float r = -Radius; r <= Radius; r += 1.0) {
        float w = exp(r * r * k);
        acc += texture(DiffuseSampler, texCoord + oneTexel * r * BlurDir).rgb * w;
        total += w;
    }
    fragColor = vec4(acc / total, 1.0);
}
