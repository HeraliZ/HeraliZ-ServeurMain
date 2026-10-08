#version 150

// Flou gaussien separable du menu Echap (heraliz_interface) : une passe horizontale puis une verticale.
// Radius = rayon en pixels (sigma = Radius / 2) ; 0 = image intacte (animation d'ouverture).

uniform sampler2D DiffuseSampler;

in vec2 texCoord;
in vec2 oneTexel;

uniform vec2 BlurDir;
uniform float Radius;

out vec4 fragColor;

void main() {
    if (Radius < 0.5) {
        fragColor = vec4(texture(DiffuseSampler, texCoord).rgb, 1.0);
        return;
    }
    float sigma = Radius * 0.5;
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
