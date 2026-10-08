#version 150

// HUD de HeraliZ Ping : comme position_color_tex, mais sans couper les pixels sous 10 % d'opacite (le jeu les jette) :
// bords adoucis des formes et des lettres, fondu d'apparition et de disparition jusqu'au bout.
uniform sampler2D Sampler0;

uniform vec4 ColorModulator;

in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0) * vertexColor;
    if (color.a < 0.004) {
        discard;
    }
    fragColor = color * ColorModulator;
}
