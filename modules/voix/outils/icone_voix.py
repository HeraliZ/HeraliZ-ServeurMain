"""Icône du mode de voix du HUD (27/11, maquette validée : maquettes/icone_allonge/voix_micro_27nov.png).

Écrit, en blanc sur transparent (le jeu les teinte), dans
src/client/resources/assets/caracrea/textures/gui/voix/ :
  micro.png    le micro (capsule, berceau, pied)
  ondes_1.png  1 onde de chaque côté (chuchoter)
  ondes_2.png  2 ondes (parler)
  ondes_3.png  3 ondes (crier)
  barre.png    le trait du micro coupé
Repère : 18 x 11 unités d'interface, dessiné x16 puis réduit à 4 pixels par unité (72 x 44), lissé.
Mêmes cotes dans VoiceHud (ICONE_L, ICONE_H). Jamais retouché à la main : relancer ce script.

Usage : python -I modules/voix/outils/icone_voix.py
"""
import pathlib

from PIL import Image, ImageDraw

SS = 16
PX = 4  # pixels de texture par unité d'interface
W, H = 18, 11
SORTIE = pathlib.Path(__file__).resolve().parents[1] / "src/client/resources/assets/caracrea/textures/gui/voix"


def toile():
    s = PX * SS
    im = Image.new("RGBA", (W * s, H * s), (0, 0, 0, 0))
    return im, ImageDraw.Draw(im), s


def fini(im, nom):
    im = im.resize((W * PX, H * PX), Image.LANCZOS)
    SORTIE.mkdir(parents=True, exist_ok=True)
    im.save(SORTIE / nom)
    print("écrit", SORTIE / nom)


def main():
    blanc = (255, 255, 255, 255)
    ox, oy = 9.0, 1.0

    def X(x, s):
        return (ox + x) * s

    def Y(y, s):
        return (oy + y) * s

    im, d, s = toile()
    w = round(0.75 * s)
    d.rounded_rectangle([X(-1.35, s), Y(0.0, s), X(1.35, s), Y(4.9, s)], radius=1.35 * s, fill=blanc)
    d.arc([X(-2.4, s), Y(1.7, s), X(2.4, s), Y(6.3, s)], 0, 180, fill=blanc, width=w)
    d.line([X(0, s), Y(6.3, s), X(0, s), Y(7.9, s)], fill=blanc, width=w)
    d.line([X(-1.4, s), Y(8.1, s), X(1.4, s), Y(8.1, s)], fill=blanc, width=w)
    fini(im, "micro.png")

    for n in (1, 2, 3):
        im, d, s = toile()
        w = round(0.75 * s)
        for i in range(n):
            r = 3.6 + 1.55 * i
            bb = [X(-r, s), Y(2.6 - r, s), X(r, s), Y(2.6 + r, s)]
            d.arc(bb, -38, 38, fill=blanc, width=w)
            d.arc(bb, 142, 218, fill=blanc, width=w)
        fini(im, "ondes_%d.png" % n)

    im, d, s = toile()
    d.line([(X(-3.2, s), Y(0.2, s)), (X(3.2, s), Y(8.4, s))], fill=blanc, width=round(0.9 * s))
    fini(im, "barre.png")


if __name__ == "__main__":
    main()
