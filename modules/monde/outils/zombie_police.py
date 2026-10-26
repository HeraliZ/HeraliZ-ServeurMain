#!/usr/bin/env python3
"""Peau du zombie en tenue de police (25/10, zones « police » de /zombies zone) : écrite à partir de la peau du prisonnier
(même corps, même visage, mêmes taches de sang), jamais retouchée à la main.

    python modules/monde/outils/zombie_police.py [--source DOSSIER] [--apercu FICHIER.png]

--source : dossier où lire prisonnier.png et sa mâchoire (par défaut, le dossier des peaux du projet).

- la combinaison orange devient un uniforme bleu marine (chemise un peu plus claire que le pantalon), les nuances gardées ;
- ceinture noire, insigne argenté sur la poitrine, bandes réfléchissantes discrètes sur les épaules ;
- casquette de police sur la couche du chapeau (visière noire, bandeau à damier, plaque argentée) ;
- les pixels de sang (rouges sombres) ne bougent pas.
Écrit textures/entity/zombies/police.png et machoire_police.png (copie de la mâchoire du prisonnier).
"""
import colorsys
import shutil
import sys
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[3]
DOSSIER = RACINE / "modules/monde/src/client/resources/assets/heralisz_utils/textures/entity/zombies"
SOURCE = Path(sys.argv[sys.argv.index("--source") + 1]) if "--source" in sys.argv else DOSSIER

MARINE_CHEMISE = (38, 52, 86)
MARINE_PANTALON = (24, 32, 54)
NOIR = (18, 18, 22)
ARGENT = (196, 200, 208)
ARGENT_SOMBRE = (128, 134, 146)
REFLET = (150, 160, 170)


def orange(p):
    r, g, b, a = p
    if a == 0:
        return False
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return 0.04 <= h <= 0.14 and s >= 0.45 and v >= 0.35


def teinte(p, base):
    """Garde la luminosité relative du pixel orange d'origine sur la couleur de base."""
    r, g, b, a = p
    v = max(r, g, b) / 255
    k = 0.65 + 0.7 * (v - 0.75)   # orange clair -> marine un peu plus clair, orange sombre -> plus sombre
    return tuple(max(0, min(255, int(c * k / 0.65))) for c in base) + (a,)


def dans(x, y, boite):
    x0, y0, x1, y1 = boite
    return x0 <= x < x1 and y0 <= y < y1


# zones de la peau 64 x 64 (format des joueurs) : torse, bras, jambes (couche de base et seconde couche)
TORSE = [(16, 16, 40, 32), (16, 32, 40, 48)]
BRAS = [(40, 16, 56, 32), (32, 48, 48, 64), (40, 32, 56, 48), (48, 48, 64, 64)]
JAMBES = [(0, 16, 16, 32), (16, 48, 32, 64), (0, 32, 16, 48), (0, 48, 16, 64)]


def peindre(src):
    im = src.copy()
    px = im.load()
    for y in range(64):
        for x in range(64):
            p = px[x, y]
            if not orange(p):
                continue
            if any(dans(x, y, b) for b in JAMBES):
                px[x, y] = teinte(p, MARINE_PANTALON)
            else:
                px[x, y] = teinte(p, MARINE_CHEMISE)
    # ceinture : bas du torse (face avant 20..28, côtés et dos), deux lignes
    for x in range(16, 40):
        for y in (30, 31):
            if px[x, y][3]:
                px[x, y] = NOIR + (255,)
    px[24, 30] = ARGENT_SOMBRE + (255,)   # boucle
    # insigne sur la poitrine (face avant du torse : x 20..27, y 20..31)
    for (x, y, c) in ((21, 22, ARGENT), (22, 22, ARGENT), (21, 23, ARGENT_SOMBRE), (22, 23, ARGENT)):
        px[x, y] = c + (255,)
    # bandes réfléchissantes discrètes en haut des bras (faces avant)
    for x in (44, 45, 46, 47):
        px[x, 21] = REFLET + (255,)
    for x in (36, 37, 38, 39):
        px[x, 53] = REFLET + (255,)
    # casquette : couche du chapeau (x 32..64, y 0..16) ; dessus (40..48, 0..8), faces (32..64, 8..16)
    for x in range(40, 48):
        for y in range(0, 8):
            px[x, y] = (MARINE_PANTALON if (x + y) % 5 else MARINE_CHEMISE) + (255,)
    for x in range(32, 64):
        for y in range(8, 11):
            px[x, y] = MARINE_PANTALON + (255,)          # haut du calot
        px[x, 11] = (ARGENT if (x // 2) % 2 == 0 else NOIR) + (255,)   # bandeau à damier
    for x in range(40, 48):
        px[x, 12] = NOIR + (255,)                         # visière (face avant)
    px[43, 9], px[44, 9] = ARGENT + (255,), ARGENT_SOMBRE + (255,)   # plaque
    for x in range(32, 64):
        for y in range(13, 16):
            if not dans(x, y, (40, 12, 48, 13)):
                px[x, y] = (0, 0, 0, 0)                   # le reste du chapeau : transparent
    return im


def main():
    src = Image.open(SOURCE / "prisonnier.png").convert("RGBA")
    out = peindre(src)
    DOSSIER.mkdir(parents=True, exist_ok=True)
    out.save(DOSSIER / "police.png")
    shutil.copyfile(SOURCE / "machoire_prisonnier.png", DOSSIER / "machoire_police.png")
    print("écrit", DOSSIER / "police.png")
    if "--apercu" in sys.argv:
        f = Path(sys.argv[sys.argv.index("--apercu") + 1])
        planche = Image.new("RGBA", (1024, 512), (40, 40, 40, 255))
        planche.paste(src.resize((512, 512), Image.NEAREST), (0, 0))
        planche.paste(out.resize((512, 512), Image.NEAREST), (512, 0))
        planche.save(f)
        print("aperçu", f)


if __name__ == "__main__":
    main()
