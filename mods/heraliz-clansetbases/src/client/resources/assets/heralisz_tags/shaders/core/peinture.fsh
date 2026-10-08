#version 150

#moj_import <fog.glsl>

// HeraliZ Tags - peinture a la bombe sur le mur (TAGS_V2_SPEC par. 3).
// - porosite alignee sur les pixels des blocs (hachage de floor(position monde x 16)) : 7 a 14 % de couverture
//   retiree au coeur selon des zones de 4 x 4 pixels (manques francs rares + pixels plus maigres), bien davantage
//   sur les bords du dessin (la bombe accroche mal en bordure) ;
// - assombrissement leger par pixel et par taches de 3 pixels (la texture du mur transparait) ;
// - bord : celui du dessin (brume de bombe peinte dans l'atlas, texels transparents de la couleur voisine) ; aucune
//   decoupe nette d'autocollant, et une seule lecture de texture par fragment ;
// - finition mate (aucun reflet), lightmap et brouillard comme les blocs, fondu par ColorModulator ;
// - au loin (pixel de bloc plus petit qu'un pixel d'ecran) le motif est remplace par sa moyenne : pas de scintillement.

uniform sampler2D Sampler0;
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;
in vec3 posMonde;

out vec4 fragColor;

// hachage d'un point du reseau entier -> [0, 1)
float hachage(vec3 p) {
    p = fract(p * vec3(0.1031, 0.1030, 0.0973));
    p += dot(p, p.yxz + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    // taille d'un pixel de bloc a l'ecran (derivees prises avant tout rejet : flux de controle encore uniforme) ;
    // au-dela d'un pixel d'ecran par pixel de bloc, le motif est remplace par sa moyenne (pas de scintillement)
    float pxEcran = max(length(dFdx(posMonde)), length(dFdy(posMonde))) * 16.0;
    // une seule lecture de texture, rejet immediat des texels vides (la majeure partie de chaque quad) : le bord doux
    // vient du dessin lui-meme (brume de bombe peinte dans l'atlas) et du filtrage trilineaire. Banc 0.3.0 : la
    // version a 5 lectures (adoucissement par 4 voisins) coutait ~150 us GPU de plus que rendertype_text pour 50 tags.
    vec4 tex = texture(Sampler0, texCoord0);
    float a = tex.a;
    if (a < 0.004) {
        discard;
    }

    // pixels de bloc : 16 par bloc, comme les textures du mur
    vec3 px = floor(posMonde * 16.0);
    float grain = hachage(px);
    float zone = hachage(floor(posMonde * 4.0) + 71.0);
    float tache = hachage(floor(posMonde * (16.0 / 3.0)) + 29.0);
    // pixels mal accroches selon la zone : rares manques francs au coeur (1-3 %, 80 % de peinture en moins) et
    // beaucoup de pixels un peu plus maigres (30-55 %, 22 % en moins) ; bien davantage dans la brume du bord, ou la
    // bombe accroche mal. Reglage du banc 0.3.0 : des retraits plus forts au coeur decoupaient des carres de 1/16 de
    // bloc dans des dessins de 256 texels par bloc (effet mosaique, lettrages sales).
    float bord = (1.0 - a) * 0.35;
    float seuilFranc = mix(0.01, 0.03, zone) + bord;
    float seuilLeger = mix(0.30, 0.55, zone) + bord;
    float loin = smoothstep(0.5, 1.2, pxEcran);
    // couverture retiree au coeur du dessin : 7-14 % selon la zone (moyenne ~10,5 %)
    float retrait = grain < seuilFranc ? 0.8 : (grain < seuilLeger ? 0.22 : 0.0);
    float retraitMoyen = 0.8 * seuilFranc + 0.22 * max(seuilLeger - seuilFranc, 0.0);
    float couverture = 1.0 - mix(retrait, min(retraitMoyen, 0.8), loin);
    // assombrissement : la texture du mur transparait (0-5 % par pixel, tire du meme hachage, jusqu'a 6 % par taches
    // de 3 pixels)
    float sombre = 0.05 * fract(grain * 53.17) + 0.06 * max(tache - 0.55, 0.0) / 0.45;
    sombre = mix(sombre, 0.038, loin);

    vec4 couleur = vec4(tex.rgb * (1.0 - sombre), a * couverture) * vertexColor * ColorModulator;
    if (couleur.a < 0.004) {
        discard;
    }
    fragColor = linear_fog(couleur, vertexDistance, FogStart, FogEnd, FogColor);
}
