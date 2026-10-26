#!/usr/bin/env python3
"""Aperçu (25/10, à valider) de l'objet d'événement à revendre : « Dossier militaire classé », trouvé dans chaque
coffre d'événement (largage, zone chaude, hélicoptère...), revendu au PNJ du spawn pour des hérals et des points de
saison pour le clan (docs/EVENEMENTS.md). Icône d'inventaire 32 x 32 dessinée par le calcul (jamais à la main).

    python modules/evenements/outils/icone_dossier.py [DOSSIER]     (par défaut : maquettes/evenements)

Écrit l'icône à sa vraie taille et une planche agrandie (x 10, sur fond d'inventaire) pour la validation.
"""
import sys
from pathlib import Path

from PIL import Image, ImageDraw

RACINE = Path(__file__).resolve().parents[3]
N = 32


def couleur(h):
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4)) + (255,)


def dossier():
    im = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ombre, carton, carton_c, carton_f = couleur("2a2418"), couleur("b89a5e"), couleur("d4b97c"), couleur("8a7140")
    papier, papier_f, rouge, rouge_f, noir = couleur("ece6d6"), couleur("bdb6a4"), couleur("c8302c"), couleur("8e1f1c"), couleur("1c1a16")
    # ombre portée
    d.rectangle([5, 9, 29, 29], fill=ombre)
    # dos du dossier et onglet
    d.rectangle([3, 7, 27, 27], fill=carton_f)
    d.rectangle([3, 5, 12, 7], fill=carton_f)
    # feuilles qui dépassent
    d.rectangle([6, 6, 25, 24], fill=papier_f)
    d.rectangle([7, 5, 24, 23], fill=papier)
    for y in (8, 10, 12):
        d.line([9, y, 22, y], fill=papier_f)
    # rabat avant
    d.polygon([(3, 12), (27, 10), (27, 27), (3, 27)], fill=carton)
    d.line([3, 12, 27, 10], fill=carton_c)
    d.line([3, 27, 27, 27], fill=carton_f)
    d.line([27, 10, 27, 27], fill=carton_f)
    # bande rouge « CLASSÉ » en travers et tampon
    d.polygon([(4, 19), (26, 16), (26, 20), (4, 23)], fill=rouge)
    d.line([4, 23, 26, 20], fill=rouge_f)
    for x in range(7, 24, 3):
        d.point((x, 20 - (x - 4) // 8), fill=papier)
    # étoile militaire en coin
    d.polygon([(21, 23), (22, 25), (24, 25), (22, 26), (23, 28), (21, 27), (19, 28), (20, 26), (18, 25), (20, 25)], fill=noir)
    # contour sombre
    for x in range(N):
        for y in range(N):
            if im.getpixel((x, y))[3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < N and 0 <= ny < N and im.getpixel((nx, ny))[3] == 255 and im.getpixel((nx, ny)) != ombre:
                        im.putpixel((x, y), (20, 18, 14, 180))
                        break
    return im


def planche(icone):
    k = 10
    fond = Image.new("RGBA", (N * k + 80, N * k + 80), (24, 26, 30, 255))
    case = Image.new("RGBA", (N * k + 20, N * k + 20), (58, 60, 64, 255))
    fond.alpha_composite(case, (30, 30))
    fond.alpha_composite(icone.resize((N * k, N * k), Image.NEAREST), (40, 40))
    return fond


def main():
    sortie = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "evenements"
    sortie.mkdir(parents=True, exist_ok=True)
    ic = dossier()
    ic.save(sortie / "dossier_militaire_32.png")
    planche(ic).save(sortie / "dossier_militaire_planche.png")
    print("écrit", sortie)


if __name__ == "__main__":
    main()
