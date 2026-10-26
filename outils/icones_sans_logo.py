#!/usr/bin/env python3
"""Retire le petit logo « M » de Max Stuff des icônes TaCZ des gunpacks de serveur/tacz.

    python outils/icones_sans_logo.py              retire le logo des icônes qui l'ont encore
    python outils/icones_sans_logo.py --verifier   contrôle seulement (code de sortie 1 s'il en reste un)

Besoin de Pillow et numpy (pip install pillow numpy).

Le gunpack Max Stuff (espace de noms « maxstuff », dans heraliz_6_armes_maxstuff) dessine dans le coin haut-gauche
de ses icônes d'accessoires un petit badge : un carré au coin bas-droit coupé en escalier, bord violet, fond bleu nuit,
un « M » blanc. Dans l'inventaire, il gâche l'icône (choix du propriétaire : l'enlever). Les crédits de l'auteur
restent dans le gunpack (gunpack_info.json, CREDITS.txt) : seule la petite image change.

Le badge est cherché par son motif (MOTIF ci-dessous : la forme, le bord violet, le fond bleu nuit et le M blanc),
à toutes les tailles (une case du motif = 1, 2, 3… pixels de l'icône) et à toutes les places du coin haut-gauche
(le badge doit tenir dans les 60 % haut-gauche de l'icône) : pas de position fixe. Une fois trouvé, seuls ses
pixels (couleur du badge à leur place dans le motif) deviennent transparents ; un pixel de l'objet posé sur la zone
du badge est gardé. Une icône est laissée telle quelle et listée (« douteuse ») si le motif n'est qu'à moitié
reconnu, ou si des pixels inconnus dans le badge ne sont rattachés à aucun objet (variante du badge, lissage…) :
dans ces cas, on regarde l'icône à la main (--verifier la liste sans échouer).

Relancer ne change rien aux icônes déjà nettoyées : à lancer après l'ajout ou la mise à jour d'un gunpack. Les
icônes sont des fichiers des joueurs : il faut leur redonner le dossier tacz.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image

DEPOT = Path(__file__).resolve().parents[1]
TACZ = DEPOT / "serveur" / "tacz"

# Le badge, une case par « gros pixel » : V bord violet, N fond bleu nuit, B le M blanc, . hors du badge.
MOTIF = (
    "VNNNNNNNNV",
    "VNNNNNNNNV",
    "VNNBNBNNNV",
    "VNBBNBBNNV",
    "VNBNBNBNNV",
    "VNBNNNBNNV",
    "VNNNNNNNNV",
    "VNNNNNNNV.",
    "VNNNNNVV..",
    "VVVVVV....",
)
CLASSES = {"V": 1, "N": 2, "B": 3}
TAILLE_MIN, TAILLE_MAX = 0.08, 0.50   # côté du badge, en part du côté de l'icône
ZONE = 0.60                           # le badge doit tenir dans les 60 % haut-gauche de l'icône
SUR, DOUTE = 0.90, 0.50               # part des cases V, N et B reconnues : badge sûr / icône douteuse
CASE_PLEINE = 0.75                    # une case est reconnue si les trois quarts de ses pixels ont la bonne couleur
COUVERT_MAX = 0.50                    # au plus la moitié des cases de chaque couleur cachées par l'objet


def icones():
    """Images d'icônes des gunpacks (dossiers « slot » des textures)."""
    return sorted(f for f in TACZ.glob("*/assets/*/textures/**/*.png") if "slot" in f.parent.name)


def _motif():
    m = np.array([[CLASSES.get(c, 0) for c in ligne] for ligne in MOTIF], np.uint8)
    assert m.shape == (10, 10)
    return m


def _classes(a):
    """Classe de chaque pixel : 1 violet (bord), 2 bleu nuit (fond), 3 blanc (M), 0 autre ou transparent."""
    rgb = a[..., :3].astype(int)
    r, g, b = rgb[..., 0], rgb[..., 1], rgb[..., 2]
    mx, mn = rgb.max(axis=2), rgb.min(axis=2)
    opaque = a[..., 3] >= 128
    sat = (mx - mn) / np.maximum(mx, 1)
    violet = opaque & (b >= r) & (r > g + 20) & (b > g + 40) & (sat >= 0.45) & (mx >= 70) & (b - r <= 130)
    nuit = opaque & (mx <= 40) & (b >= r) & (b - r <= 16)
    blanc = opaque & (mn >= 140) & (mx - mn <= 40) & (b >= r)
    cl = np.zeros(a.shape[:2], np.uint8)
    cl[violet], cl[nuit], cl[blanc] = 1, 2, 3
    return cl


def _attendu(forme, motif, x0, y0, s):
    """Classe attendue à chaque pixel de l'icône si le badge est à cette place (0 hors du badge)."""
    attendu = np.zeros(forme, np.uint8)
    attendu[y0:y0 + 10 * s, x0:x0 + 10 * s] = np.kron(motif, np.ones((s, s), np.uint8))
    return attendu


def _integrale(m):
    """Somme cumulée 2D : la somme d'un rectangle se lit en quatre valeurs."""
    i = np.zeros((m.shape[0] + 1, m.shape[1] + 1), np.int64)
    i[1:, 1:] = m.cumsum(0).cumsum(1)
    return i


def chercher(a):
    """Meilleure place du badge : (taux, (x, y, échelle)), ou (0, None) si rien ne lui ressemble.

    Pour chaque taille, toutes les places du coin haut-gauche qui ont assez de pixels de chaque couleur sont notées
    d'un coup (sommes cumulées). Une case du motif est « reconnue » si les trois quarts de ses pixels visibles ont sa
    couleur, « cachée » si l'objet en couvre au moins la moitié. Taux = part minimale des cases V, N et B reconnues
    parmi celles qui ne sont pas cachées (0 si plus de la moitié des cases d'une couleur sont cachées). À taux égal,
    la place où le plus de pixels ont la bonne couleur gagne (des places voisines d'un pixel peuvent reconnaître les
    mêmes cases)."""
    h, w = a.shape[:2]
    cl = _classes(a)
    objet = (a[..., 3] >= 128) & (cl == 0)          # pixels de l'objet (ni violets, ni bleu nuit, ni blancs)
    motif = _motif()
    cases = {k: int((motif == k).sum()) for k in (1, 2, 3)}
    boites = {}                                     # rectangle (en cases) qui contient les cases de chaque couleur
    for k in (1, 2, 3):
        li, co = np.nonzero(motif == k)
        boites[k] = (li.min(), li.max() + 1, co.min(), co.max() + 1)
    dans_zone = cl[:int(ZONE * h), :int(ZONE * w)]
    pixels = {k: int((dans_zone == k).sum()) for k in (1, 2, 3)}
    integ = {k: _integrale(cl == k) for k in (1, 2, 3)}
    integ_objet = _integrale(objet)
    meilleur, place = (0.0, 0.0), None
    for s in range(max(1, int(TAILLE_MIN * min(h, w) / 10)), int(TAILLE_MAX * min(h, w) / 10) + 1):
        nx, ny = int(ZONE * w) - 10 * s + 1, int(ZONE * h) - 10 * s + 1
        if nx <= 0 or ny <= 0:
            continue
        # il faut assez de pixels violets, bleu nuit et blancs pour un badge de cette taille : dans toute la zone,
        # puis, à chaque place, dans le rectangle où le motif met cette couleur (le M blanc au milieu…) ; seules les
        # places qui restent sont notées case par case
        besoin = {k: DOUTE * (1 - COUVERT_MAX) * cases[k] * np.ceil(CASE_PLEINE * s * s / 2) for k in (1, 2, 3)}
        if any(pixels[k] < besoin[k] for k in (1, 2, 3)):
            continue
        ok = np.ones((ny, nx), bool)
        for k, (i0, i1, j0, j1) in boites.items():
            y0, y1, x0, x1 = i0 * s, i1 * s, j0 * s, j1 * s
            i = integ[k]
            ok &= (i[y1:y1 + ny, x1:x1 + nx] - i[y0:y0 + ny, x1:x1 + nx]
                   - i[y1:y1 + ny, x0:x0 + nx] + i[y0:y0 + ny, x0:x0 + nx]) >= besoin[k]
        ys, xs = np.nonzero(ok)
        if not len(ys):
            continue

        def bloc(i, y, x):                          # somme du bloc s × s en (y, x), pour chaque place gardée
            y, x = ys + y, xs + x
            return i[y + s, x + s] - i[y, x + s] - i[y + s, x] + i[y, x]
        reconnu = {k: np.zeros(len(ys), np.int32) for k in (1, 2, 3)}
        cache = {k: np.zeros(len(ys), np.int32) for k in (1, 2, 3)}
        bons, visibles = np.zeros(len(ys)), np.zeros(len(ys))
        for i in range(10):
            for j in range(10):
                k = motif[i, j]
                if not k:
                    continue
                bon, obj = bloc(integ[k], i * s, j * s), bloc(integ_objet, i * s, j * s)
                cachee = 2 * obj >= s * s
                cache[k] += cachee
                reconnu[k] += ~cachee & (bon > 0) & (bon >= CASE_PLEINE * (s * s - obj))
                bons += np.where(cachee, 0, bon)
                visibles += np.where(cachee, 0, s * s - obj)
        taux = np.minimum.reduce([np.where(cache[k] <= COUVERT_MAX * cases[k],
                                           reconnu[k] / np.maximum(cases[k] - cache[k], 1), 0.0) for k in (1, 2, 3)])
        note = taux + 1e-3 * bons / np.maximum(visibles, 1)
        n = int(np.argmax(note))
        if taux[n] >= DOUTE and (note[n], taux[n]) > meilleur:
            meilleur, place = (note[n], taux[n]), (int(xs[n]), int(ys[n]), s)
    return float(meilleur[1]), place


def _voisins8(m):
    p = np.pad(m, 1)
    v = np.zeros_like(m)
    for dy in (0, 1, 2):
        for dx in (0, 1, 2):
            if dy != 1 or dx != 1:
                v |= p[dy:dy + m.shape[0], dx:dx + m.shape[1]]
    return v


def sans_logo(a):
    """a : image RGBA (uint8). Rend (image, état, détail) ; état = « nettoyée », « sans logo » ou « douteuse »."""
    taux, place = chercher(a)
    if place is None:
        return a, "sans logo", ""
    x0, y0, s = place
    if taux < SUR:
        return a, "douteuse", "motif reconnu à %d %% seulement (case de %d px en %d, %d)" % (100 * taux, s, x0, y0)
    attendu = _attendu(a.shape[:2], _motif(), x0, y0, s)
    zone = attendu > 0
    badge = zone & (_classes(a) == attendu)         # pixels du badge : la bonne couleur à la bonne place
    reste = zone & ~badge & (a[..., 3] > 0)         # pixels visibles dans la zone qui ne sont pas du badge
    # un pixel gardé doit appartenir à un objet qui continue hors du badge, sinon on ne sait pas ce que c'est
    objet = (a[..., 3] > 0) & ~badge
    relie = objet & ~zone
    while True:
        plus = relie | (objet & _voisins8(relie))
        if (plus == relie).all():
            break
        relie = plus
    perdus = reste & ~relie
    if perdus.any():
        return a, "douteuse", "%d pixel(s) inconnu(s) dans le badge, rattaché(s) à aucun objet" % perdus.sum()
    b = a.copy()
    b[badge] = 0
    contact = int(((objet & ~zone) & _voisins8(zone)).sum() + reste.sum())
    if not contact:
        return b, "nettoyée", ""
    return b, "nettoyée", "objet au contact du badge : %d pixel(s) gardé(s) ou voisin(s)" % contact


def main():
    verifier = "--verifier" in sys.argv[1:]
    compte = {"nettoyée": 0, "sans logo": 0, "douteuse": 0}
    douteuses = []
    for f in icones():
        a = np.asarray(Image.open(f).convert("RGBA"))
        b, etat, detail = sans_logo(a)
        compte[etat] += 1
        nom = f.relative_to(DEPOT)
        if etat == "douteuse":
            douteuses.append("%s (%s)" % (nom, detail))
        elif etat == "nettoyée" and verifier:
            print("[logos] logo Max Stuff : %s" % nom)
        elif etat == "nettoyée":
            Image.fromarray(b).save(f, optimize=True)
            if sans_logo(np.asarray(Image.open(f).convert("RGBA")))[1] != "sans logo":
                sys.exit("[logos] nettoyage incomplet : " + str(nom))
            if detail:
                print("[logos] %s : %s" % (nom, detail))
    for d in douteuses:
        print("[logos] icône douteuse laissée telle quelle : %s" % d)
    print("[logos] %d icônes : %d %s, %d sans logo, %d douteuses"
          % (sum(compte.values()), compte["nettoyée"], "avec le logo Max Stuff" if verifier else "nettoyées",
             compte["sans logo"], compte["douteuse"]))
    sys.exit(1 if verifier and compte["nettoyée"] else 0)


if __name__ == "__main__":
    main()
