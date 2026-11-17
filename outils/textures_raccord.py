"""Textures de blocs qui se raccordent : supprime le trait visible entre deux blocs voisins quand le bord d'une texture
ne continue pas le bord opposé (17/11 : la neige du pack imposé avait sa dernière colonne de pixels plus foncée, d'où un
quadrillage sur toute l'île arctique).

    python outils/textures_raccord.py <texture.png>... [--bande 0.25] [--essai] [--apercu <dossier>]

Méthode : la texture est fondue avec sa copie décalée d'une demi-largeur (puis, de même, d'une demi-hauteur). Près des
bords, c'est la copie décalée qui compte : ses bords viennent du milieu de la texture, d'un seul tenant, donc le bord
droit continue exactement le bord gauche (et le bas, le haut). Au milieu, la texture d'origine reste. --bande : largeur du fondu
(part du côté, de chaque bord). Faite pour les textures douces (neige, glace, sable fin) : sur une texture à motifs
(briques, planches), le fondu se verrait.

Mesure avant / après : écart moyen entre colonnes voisines à l'intérieur et au raccord (bord droit contre bord
gauche), de même pour les lignes. --essai : mesure seulement, n'écrit rien. --apercu : image avant / après (mosaïque
4 x 4, puis la même au contraste renforcé pour voir les traits).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image


def mesure(a):
    """(voisins x, raccord x, voisins z, raccord z) en niveaux de gris."""
    g = a[..., :3].astype(float).mean(axis=2)
    return (np.abs(np.diff(g[:, 1:-1], axis=1)).mean(), np.abs(g[:, -1] - g[:, 0]).mean(),
            np.abs(np.diff(g[1:-1, :], axis=0)).mean(), np.abs(g[-1, :] - g[0, :]).mean())


def raccorder(a, bande):
    """Un passage par direction : d'abord gauche / droite, puis haut / bas. (Les deux à la fois laissaient voir, dans les
    bandes du bord, le raccord d'origine que la copie décalée porte en son milieu.)"""
    h, w = a.shape[:2]

    def poids(n):
        d = np.minimum(np.arange(n) + 0.5, n - np.arange(n) - 0.5) / max(1.0, bande * n)   # 0 au bord, 1 dedans
        d = np.clip(d, 0, 1)
        return d * d * (3 - 2 * d)

    out = a.astype(float)
    mx = poids(w)[None, :, None]
    out = out * mx + np.roll(out, w // 2, axis=1) * (1 - mx)
    my = poids(h)[:, None, None]
    out = out * my + np.roll(out, h // 2, axis=0) * (1 - my)
    if a.shape[2] == 4:
        out[..., 3] = a[..., 3]          # transparence gardée telle quelle
    return np.clip(np.rint(out), 0, 255).astype(np.uint8)


def mosaique(a, n=4, contraste=1.0):
    t = np.tile(a[..., :3], (n, n, 1)).astype(float)
    if contraste != 1.0:
        moy = t.mean()
        t = (t - moy) * contraste + moy
    img = Image.fromarray(np.clip(t, 0, 255).astype(np.uint8))
    return img.resize((img.width * 2, img.height * 2), Image.NEAREST)


def main(argv):
    bande, essai, apercu, fichiers = 0.25, False, None, []
    i = 0
    while i < len(argv):
        if argv[i] == "--bande":
            bande = float(argv[i + 1])
            i += 2
        elif argv[i] == "--essai":
            essai = True
            i += 1
        elif argv[i] == "--apercu":
            apercu = Path(argv[i + 1])
            i += 2
        else:
            fichiers.append(Path(argv[i]))
            i += 1
    if not fichiers:
        raise SystemExit(__doc__)
    for f in fichiers:
        im = Image.open(f)
        mode = im.mode
        a = np.asarray(im.convert("RGBA"))
        b = raccorder(a, bande)
        avant, apres = mesure(a), mesure(b)
        print("%s : voisins %.1f / raccord %.1f (x), %.1f / %.1f (z)  ->  raccord %.1f (x), %.1f (z)"
              % (f.name, avant[0], avant[1], avant[2], avant[3], apres[1], apres[3]))
        if apercu:
            apercu.mkdir(parents=True, exist_ok=True)
            hauts = [mosaique(a), mosaique(b)]
            bas = [mosaique(a, contraste=6), mosaique(b, contraste=6)]
            W, H = hauts[0].width, hauts[0].height
            planche = Image.new("RGB", (2 * W + 12, 2 * H + 12), (20, 22, 26))
            for k, (x, y) in enumerate([(0, 0), (W + 12, 0)]):
                planche.paste(hauts[k], (x, y))
                planche.paste(bas[k], (x, H + 12))
            planche.save(apercu / (f.stem + "_avant_apres.png"))
        if not essai:
            sortie = Image.fromarray(b, "RGBA")
            if mode != "RGBA":
                sortie = sortie.convert(mode)
            sortie.save(f)


if __name__ == "__main__":
    main(sys.argv[1:])
