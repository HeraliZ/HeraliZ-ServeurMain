#!/usr/bin/env python3
"""Retire le contour noir des icônes TaCZ (armes, accessoires, munitions) des gunpacks de serveur/tacz.

    python outils/icones_sans_contour.py              corrige les icônes qui ont un contour noir
    python outils/icones_sans_contour.py --verifier   contrôle seulement (code de sortie 1 s'il en reste)

Besoin de Pillow et numpy (pip install pillow numpy).

Dans l'inventaire, TaCZ dessine chaque objet avec une petite image du gunpack (textures/.../slot/*.png). La plupart des
gunpacks dessinent un trait noir épais autour de l'objet (2 pixels sur une icône de 64, 3 sur une de 128) : un contour
noir qui jure avec l'inventaire. Le script ne l'efface pas (les parties fines, canons et rails, disparaîtraient) : le
trait prend la couleur de l'objet juste à côté, sans bord plus sombre (choix du propriétaire). Seul le trait collé au
vide change ; les traits intérieurs (détails du dessin) restent. Un objet entièrement noir (le noir continue au-delà de
l'épaisseur d'un trait) est laissé tel quel.

Relancer ne change rien aux icônes déjà corrigées : à lancer après l'ajout ou la mise à jour d'un gunpack. Les icônes
sont des fichiers des joueurs : il faut leur redonner le dossier tacz.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

DEPOT = Path(__file__).resolve().parents[1]
TACZ = DEPOT / "serveur" / "tacz"

NOIR = 30                  # un pixel du trait : max(R, G, B) <= NOIR
MAJORITE = 0.5             # une couche fait partie du trait si au moins la moitié de ses pixels sont noirs


def icones():
    """Images d'icônes des gunpacks (dossiers « slot » des textures)."""
    return sorted(f for f in TACZ.glob("*/assets/*/textures/**/*.png") if "slot" in f.parent.name)


def _bord(vivant):
    pad = np.pad(vivant, 1, constant_values=False)
    interieur = pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
    return vivant & ~interieur


def _couches(alpha, n):
    """Numéro de couche de chaque pixel visible (1 = collé au vide), jusqu'à n ; 0 ailleurs."""
    vivant = alpha > 0
    num = np.zeros(alpha.shape, int)
    for k in range(1, n + 1):
        b = _bord(vivant)
        if not b.any():
            break
        num[b] = k
        vivant &= ~b
    return num


def _voisins(masque, valeurs):
    """Somme pondérée des valeurs des 8 voisins qui sont dans le masque, et somme des poids."""
    h, w = masque.shape
    somme = np.zeros(valeurs.shape)
    poids = np.zeros((h, w))
    for dy, dx, p in ((-1, 0, 1.0), (1, 0, 1.0), (0, -1, 1.0), (0, 1, 1.0),
                      (-1, -1, 0.5), (-1, 1, 0.5), (1, -1, 0.5), (1, 1, 0.5)):
        ys, yd = slice(max(0, -dy), h - max(0, dy)), slice(max(0, dy), h - max(0, -dy))
        xs, xd = slice(max(0, -dx), w - max(0, dx)), slice(max(0, dx), w - max(0, -dx))
        m = np.zeros((h, w))
        m[yd, xd] = masque[ys, xs] * p
        v = np.zeros(valeurs.shape)
        v[yd, xd] = valeurs[ys, xs]
        somme += v * m[..., None]
        poids += m
    return somme, poids


def sans_contour(a):
    """a : image RGBA (uint8). Rend (image, état) ; état = « corrigée », « sans contour » ou « objet noir »."""
    cap = max(3, round(max(a.shape[:2]) / 24))   # épaisseur maximale d'un trait (en pixels de l'icône)
    alpha = a[..., 3]
    num = _couches(alpha, cap + 2)
    noir = a[..., :3].max(axis=2) <= NOIR
    largeur = 0
    for k in range(1, cap + 2):
        couche = num == k
        if not couche.any() or (noir & couche).sum() < MAJORITE * couche.sum():
            break
        largeur = k
    else:
        return a, "objet noir"
    if largeur == 0:
        return a, "sans contour"
    trait = noir & (num >= 1) & (num <= largeur + 1)
    # chaque pixel du trait prend la couleur moyenne de ses voisins déjà connus, de l'intérieur vers l'extérieur
    rgb = a[..., :3].astype(float)
    connu = (alpha > 0) & ~trait
    reste = trait.copy()
    for _ in range(4 * (largeur + 2)):
        if not reste.any():
            break
        somme, poids = _voisins(connu, rgb)
        prend = reste & (poids > 0)
        rgb[prend] = somme[prend] / poids[prend][:, None]
        connu |= prend
        reste &= ~prend
    b = a.copy()
    b[..., :3] = np.clip(rgb + 0.5, 0, 255).astype(np.uint8)
    return b, "corrigée"


def main():
    verifier = "--verifier" in sys.argv[1:]
    compte = {"corrigée": 0, "sans contour": 0, "objet noir": 0}
    noirs = []
    for f in icones():
        a = np.asarray(Image.open(f).convert("RGBA"))
        b, etat = sans_contour(a)
        compte[etat] += 1
        if etat == "objet noir":
            noirs.append(f)
        elif etat == "corrigée" and not verifier:
            Image.fromarray(b).save(f, optimize=True)
            if sans_contour(np.asarray(Image.open(f).convert("RGBA")))[1] != "sans contour":
                sys.exit("[icônes] correction incomplète : " + str(f))
        elif etat == "corrigée":
            print("[icônes] contour noir : %s" % f.relative_to(DEPOT))
    for f in noirs:
        print("[icônes] objet noir laissé tel quel : %s" % f.relative_to(DEPOT))
    print("[icônes] %d icônes : %d %s, %d sans contour, %d objets noirs"
          % (sum(compte.values()), compte["corrigée"], "avec contour" if verifier else "corrigées",
             compte["sans contour"], compte["objet noir"]))
    sys.exit(1 if verifier and compte["corrigée"] else 0)


if __name__ == "__main__":
    main()
