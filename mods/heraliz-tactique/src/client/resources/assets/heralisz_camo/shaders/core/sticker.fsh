#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform float GameTime;

in float vertexDistance;
in float shade;
in float finish;
in vec4 lightMapColor;
in vec2 gunUv;
in vec2 texCoord0;
in vec3 viewN;
in vec3 viewPos;

out vec4 fragColor;

vec3 hsv(float h) {
    return clamp(abs(mod(h * 6.0 + vec3(0.0, 4.0, 2.0), 6.0) - 3.0) - 1.0, 0.0, 1.0);
}

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    // pas de sticker là où l'arme est transparente (Sampler1 = texture de l'arme)
    if (tex.a < 0.1 || texture(Sampler1, gunUv).a < 0.1) {
        discard;
    }
    int f = int(finish * 255.0 + 0.5);
    vec3 n = normalize(viewN);
    vec3 v = normalize(-viewPos);
    float ndv = clamp(abs(dot(n, v)), 0.0, 1.0);
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    float t = GameTime * 1200.0;
    vec2 q = fract(texCoord0 * vec2(8.0, 4.0));
    vec3 col = tex.rgb;
    vec3 spec = vec3(0.0);
    if (f == 1) {
        // holographique : arc-en-ciel qui glisse selon l'angle de vue, paillettes
        // holographique (comme CS) : le dessin reste lisible, l'arc-en-ciel vit surtout dans les blancs (découpe, reflets)
        float h = fract(ndv * 1.6 + (q.x - q.y) * 1.2 + t * 0.04);
        vec3 rb = hsv(h);
        float white = smoothstep(0.72, 0.93, min(min(tex.r, tex.g), tex.b));
        col = mix(col, col * (0.7 + 0.6 * rb), 0.35);
        col = mix(col, 0.3 + 0.7 * rb, white * 0.8);
        float glit = step(0.985, fract(sin(dot(floor(q * 90.0), vec2(12.9898, 78.233))) * 43758.5453));
        spec = rb * (pow(1.0 - ndv, 2.5) * 0.3 + glit * 0.6 * (0.5 + 0.5 * sin(t * 3.0 + q.x * 50.0)));
    } else if (f == 2) {
        // métallisé : chrome, bande de reflet qui glisse
        float band = 0.5 + 0.5 * sin((q.x - q.y) * 9.0 + ndv * 8.0 + t * 0.3);
        col = mix(col, vec3(lum) * vec3(0.86, 0.92, 1.0), 0.3) * (0.65 + 0.7 * band);
        spec = vec3(1.0) * pow(band, 12.0) * 0.55;
    } else if (f == 3) {
        // or : dessin frappé dans l'or, reflet chaud
        vec3 g = mix(vec3(0.42, 0.25, 0.04), vec3(1.0, 0.86, 0.42), smoothstep(0.1, 0.9, lum));
        float band = 0.5 + 0.5 * sin((q.x + q.y) * 7.0 + ndv * 7.0 + t * 0.25);
        col = mix(g, col, 0.15) * (0.75 + 0.5 * band);
        spec = vec3(1.0, 0.9, 0.6) * pow(band, 14.0) * 0.65;
    }
    vec4 c = vec4(col * shade, tex.a) * ColorModulator;
    c *= lightMapColor;
    c.rgb += spec * max(lightMapColor.rgb, vec3(0.25));
    fragColor = linear_fog(c, vertexDistance, FogStart, FogEnd, FogColor);
}
