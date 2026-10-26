"""Repères des PNJ au spawn (HeraliZ Quêtes) : petites pastilles rondes au-dessus des PNJ, vues à travers les murs.

    python modules/pnj/outils/reperes.py [--apercu FICHIER]

Écrit src/client/resources/assets/heraliz_quetes/textures/gui/reperes.png : une rangée de pastilles de 64 px (dessinées
à 256 px puis réduites, bords nets sans escalier), dans l'ordre de PICTOS (lu par client/Reperes.java : même ordre).
On modifie ce fichier, jamais l'image produite.
"""
import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw

ICI = Path(__file__).resolve().parent.parent
SORTIE = ICI / "src/client/resources/assets/heraliz_quetes/textures/gui/reperes.png"
TAILLE, GRAND = 64, 256

# (picto, couleur) : l'ordre est celui des indices de client/Reperes.java
PICTOS = [("pnj", (150, 160, 172)), ("varga", (226, 168, 38)), ("soins", (214, 58, 52)), ("rachat", (52, 168, 96)),
          ("nourriture", (232, 128, 40)), ("accessoires", (58, 132, 222)),
          ("construction", (176, 122, 64))]


def pastille(nom, couleur):
    im = Image.new("RGBA", (GRAND, GRAND), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c, r = GRAND / 2, GRAND / 2 - 8
    d.ellipse([c - r, c - r, c + r, c + r], fill=(14, 18, 24, 235))                 # bord sombre
    r2 = r - 14
    d.ellipse([c - r2, c - r2, c + r2, c + r2], fill=couleur + (255,))
    d.chord([c - r2, c - r2, c + r2, c + r2], 180, 360, fill=tuple(min(255, int(v * 1.12)) for v in couleur) + (255,))
    B, s, x, y = (255, 255, 255, 255), 4.2, c, c

    def L(*pts, w=3.0):
        d.line([(x + a * s, y + b * s) for a, b in pts], fill=B, width=int(w * s), joint="curve")

    if nom == "varga":                                                              # étoile
        pts = []
        for i in range(10):
            a = -math.pi / 2 + i * math.pi / 5
            rr = (13 if i % 2 == 0 else 5.5) * s
            pts.append((x + math.cos(a) * rr, y + math.sin(a) * rr))
        d.polygon(pts, fill=B)
    elif nom == "soins":                                                            # croix
        w, l = 4 * s, 11 * s
        d.rectangle([x - w, y - l, x + w, y + l], fill=B)
        d.rectangle([x - l, y - w, x + l, y + w], fill=B)
    elif nom == "rachat":                                                           # sac de hérals
        d.ellipse([x - 10 * s, y - 6 * s, x + 10 * s, y + 13 * s], fill=B)
        d.polygon([(x - 6 * s, y - 6 * s), (x + 6 * s, y - 6 * s), (x + 3 * s, y - 12 * s), (x - 3 * s, y - 12 * s)], fill=B)
        L((-3.5, 0), (-3.5, 9), w=2.4)
        L((3.5, 0), (3.5, 9), w=2.4)
        L((-3.5, 4.5), (3.5, 4.5), w=2.4)
        d.line([(x - 3.5 * s, y), (x - 3.5 * s, y + 9 * s)], fill=couleur + (255,), width=int(2.4 * s))
        d.line([(x + 3.5 * s, y), (x + 3.5 * s, y + 9 * s)], fill=couleur + (255,), width=int(2.4 * s))
        d.line([(x - 3.5 * s, y + 4.5 * s), (x + 3.5 * s, y + 4.5 * s)], fill=couleur + (255,), width=int(2.4 * s))
    elif nom == "nourriture":                                                       # fourchette et couteau
        d.rectangle([x - 7.5 * s, y - 2 * s, x - 4.5 * s, y + 12 * s], fill=B)
        for k in (-10, -6, -2):
            d.rectangle([x + (k + 2.5) * s - 0.9 * s, y - 12 * s, x + (k + 2.5) * s + 0.9 * s, y - 3 * s], fill=B)
        d.rectangle([x - 9.5 * s, y - 4 * s, x - 2.5 * s, y - 1 * s], fill=B)
        d.rectangle([x + 3.5 * s, y - 2 * s, x + 6.5 * s, y + 12 * s], fill=B)
        d.pieslice([x + 0.5 * s, y - 12 * s, x + 9.5 * s, y + 2 * s], 270, 90, fill=B)
    elif nom == "accessoires":                                                      # viseur
        d.ellipse([x - 10 * s, y - 10 * s, x + 10 * s, y + 10 * s], outline=B, width=int(2.8 * s))
        for a, b, e, f in ((-15, 0, -5, 0), (5, 0, 15, 0), (0, -15, 0, -5), (0, 5, 0, 15)):
            d.line([(x + a * s, y + b * s), (x + e * s, y + f * s)], fill=B, width=int(2.8 * s))
        d.ellipse([x - 1.8 * s, y - 1.8 * s, x + 1.8 * s, y + 1.8 * s], fill=B)
    elif nom == "construction":                                                     # marteau
        d.polygon([(x - 2 * s, y - 4 * s), (x + 2 * s, y - 4 * s), (x + 2 * s, y + 13 * s), (x - 2 * s, y + 13 * s)], fill=B)
        d.rounded_rectangle([x - 11 * s, y - 12 * s, x + 9 * s, y - 4 * s], int(1.5 * s), fill=B)
        d.polygon([(x + 9 * s, y - 12 * s), (x + 13 * s, y - 9 * s), (x + 9 * s, y - 7 * s)], fill=B)
    else:                                                                           # PNJ : bulle de dialogue
        d.rounded_rectangle([x - 12 * s, y - 10 * s, x + 12 * s, y + 6 * s], int(5 * s), fill=B)
        d.polygon([(x - 6 * s, y + 5 * s), (x + 1 * s, y + 5 * s), (x - 7 * s, y + 12 * s)], fill=B)
        for k in (-6, 0, 6):
            d.ellipse([x + (k - 1.8) * s, y - 3.8 * s, x + (k + 1.8) * s, y - 0.2 * s], fill=couleur + (255,))
    return im.resize((TAILLE, TAILLE), Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apercu")
    a = ap.parse_args()
    planche = Image.new("RGBA", (TAILLE * len(PICTOS), TAILLE), (0, 0, 0, 0))
    for i, (nom, couleur) in enumerate(PICTOS):
        planche.paste(pastille(nom, couleur), (i * TAILLE, 0))
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    planche.save(SORTIE)
    print("repères :", SORTIE, len(PICTOS), "pastilles")
    if a.apercu:
        fond = Image.new("RGBA", (planche.width * 3, planche.height * 3), (40, 44, 50, 255))
        fond.alpha_composite(planche.resize(fond.size, Image.LANCZOS))
        fond.save(a.apercu)


if __name__ == "__main__":
    main()
