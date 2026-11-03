#!/usr/bin/env python3
"""
Échelle de raid de CRac (crac:echelle, 03/11) : modèle dans le style des pièces de base (deux montants en planches, des
barreaux tous les quarts de bloc, des colliers d'acier), texture et rendus. Rien n'est dessiné à la main : on modifie ce
script.

    python modules/bases/outils/echelle_modele.py --apercu DOSSIER    rendus (un étage contre un mur, deux étages)
    python modules/bases/outils/echelle_modele.py --installer         écrit dans le mod : texture, modèles des trois
        cases (echelle_bas, echelle_milieu, echelle_haut), états du bloc, objet

Une échelle a la hauteur d'un mur de base (un étage, 3 blocs : EchelleBlock.HAUTEUR) ; elle se pose d'un coup, dans les
trois cases du mur, une case par partie du bloc (EchelleBlock.PARTIE). Tournée vers le nord (le mur est au sud) ; 3 px
d'épaisseur comme la forme du bloc (EchelleBlock.EPAISSEUR), de z 11 à 14 (la face du mur est à 2 px du bord de sa case).
Pour l'atlas et le contrôle des faces confondues (faces_confondues.py), les trois parties sont rangées côte à côte (x -16,
0 et 16) puis remises chacune dans sa case à l'écriture.
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import coffres_modeles as cm  # noqa: E402

EPAISSEUR = 3.0
Z1 = 14.0                       # face du mur (2 px du bord de la case)
Z0 = Z1 - EPAISSEUR
PARTIES = ("bas", "milieu", "haut")
RANGEMENT = (-16.0, 0.0, 16.0)  # x de chaque partie dans l'atlas (limites des modèles : -16..32)
SENS = {"north": 0, "east": 90, "south": 180, "west": 270}


def echelle():
    m = cm.Modele("echelle", "peu_commune", "1 étage (3 blocs)", "haut", 1)
    for k, nom in enumerate(PARTIES):
        m.os[nom] = cm.Os(nom)
        dx = RANGEMENT[k]
        for x0, g in ((1.5, 1), (12.5, 2)):                                               # montants
            m.B(nom, (dx + x0, 0, Z0), (dx + x0 + 2.0, 16.0, Z1), "bois_brut", prm={"graine": g + 3 * k})
        for j, y0 in enumerate((1.5, 5.5, 9.5, 13.5)):                                    # barreaux
            m.B(nom, (dx + 3.5, y0, Z0 + 0.6), (dx + 12.5, y0 + 1.5, Z1 - 0.6), "bois_brut",
                prm={"graine": 10 + 4 * k + j})
        y0 = (0.5, 7.25, 12.0)[k]                                                         # colliers d'acier (devant)
        for x0 in (1.25, 12.25):
            m.B(nom, (dx + x0, y0, Z0 - 0.25), (dx + x0 + 2.5, y0 + 1.25, Z0), "acier_coin", prm={"rivets": False},
                faces=("north", "east", "west", "up", "down"))
    return m


def parties(cf):
    """Éléments de chaque partie, remis dans sa case (x 0..16, y 0..16)."""
    return [cm.decaler_elements(cf.groupes[nom], -RANGEMENT[k], 0.0, 0.0) for k, nom in enumerate(PARTIES)]


def objet(cf):
    """L'échelle entière pour l'objet en main : les trois parties l'une sur l'autre, réduites de moitié (un modèle ne
    dépasse pas 32 px), centrées dans le bloc."""
    out = []
    for k, els in enumerate(parties(cf)):
        for el in els:
            def p(v):
                return cm._arrondi([(v[0] - 8.0) * 0.5 + 8.0, (v[1] + 16.0 * k - 24.0) * 0.5 + 8.0,
                                    (v[2] - (Z0 + Z1) / 2) * 0.5 + 8.0])
            out.append(dict(el, **{"from": p(el["from"]), "to": p(el["to"])}))
    return out


AFFICHAGE = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}


def installer(cf):
    import adossement
    import faces_confondues as fc
    els = parties(cf)
    for nom, e in zip(PARTIES, els):
        fc.exiger_aucun(f"echelle_{nom}", e)
    fc.exiger_aucun("echelle (objet)", objet(cf))
    tex = "crac:block/echelle"
    cf.atlas.save(cm.ASSETS / "textures" / "block" / "echelle.png", optimize=True)
    for nom, e in zip(PARTIES, els):
        adossement.ecrire(cm.ASSETS / "models" / "block" / f"echelle_{nom}.json",
                          {"credit": "HeraliZ, modules/bases/outils/echelle_modele.py", "textures": {"0": tex, "particle": tex},
                           "elements": e})
    adossement.ecrire(cm.ASSETS / "models" / "item" / "echelle.json",
                      {"credit": "HeraliZ, modules/bases/outils/echelle_modele.py", "textures": {"0": tex, "particle": tex},
                       "elements": objet(cf), "display": AFFICHAGE})
    variantes = {}
    for sens, y in SENS.items():
        for k, nom in enumerate(PARTIES):
            v = {"model": f"crac:block/echelle_{nom}"}
            if y:
                v["y"] = y
            variantes[f"facing={sens},partie={k}"] = v
    adossement.ecrire(cm.ASSETS / "blockstates" / "echelle.json", {"variants": variantes})
    print(f"[echelle] {cf.nb_elements()} pièces, texture {cf.atlas.size[0]} px")


# ------------------------------------------------------------------------------------------------ rendus

def mur(x0, x1, y0, y1, z, graine=11, dessus=True):
    """Mur de planches (face au nord) de y0 à y1, et le dessus de son étage (plancher) : pour les rendus."""
    W = int((x1 - x0) * 4)
    Hm = int((y1 - y0) * 4)
    yy, xx = np.mgrid[0:Hm, 0:W] / 4.0
    p = np.stack([xx + x0, y1 - yy, np.full_like(xx, z)], -1)
    fibres, joint, teinte = cm._planches(p, np.array([0, 0, -1.0]), 3.2, graine)
    bois = cm.melange(cm.col(78, 60, 42), cm.col(110, 86, 60), fibres * 0.6 + teinte * 0.4) * (1 - 0.6 * joint)[..., None]
    tex = np.dstack([bois, np.ones((Hm, W))])
    face = (np.array([[x1, y1, z], [x1, y0, z], [x0, y0, z], [x0, y1, z]], float),
            np.array([[0, 0], [0, Hm], [W, Hm], [W, 0]], float), tex, True)
    D = 16
    plancher = np.dstack([cm.melange(cm.col(70, 54, 38), cm.col(96, 76, 54), np.full((D * 4, W), 0.5)), np.ones((D * 4, W))])
    toit = (np.array([[x0, y1, z], [x0, y1, z + D], [x1, y1, z + D], [x1, y1, z]], float),
            np.array([[0, 0], [0, D * 4], [W, D * 4], [W, 0]], float), plancher, True)
    return [face, toit] if dessus else [face]


def echelles(els, tex, etages):
    """Les échelles d'autant d'étages, l'une sur l'autre (une colonne de cases)."""
    q = []
    for e in range(etages):
        for k, part in enumerate(els):
            q += cm.quads_elements(part, tex, [("decal", (0.0, 48.0 * e + 16.0 * k, 0))])
    return q


def planche(cf, dossier):
    els = parties(cf)
    w = 560
    im = Image.new("RGB", (3 * w, w + 150), (20, 22, 25))
    d = ImageDraw.Draw(im)
    d.text((24, 14), "Échelle de raid : une échelle = un mur de haut (un étage, 3 blocs)", font=cm.police(32),
           fill=(236, 236, 236))
    d.text((24, 58), "posée d'un coup contre un mur droit ; une 2e se pose au-dessus pour l'étage suivant ; "
           "le dernier barreau mène sur le toit", font=cm.police(19, True), fill=(92, 191, 74))
    d.text((24, 88), "mur en biais : pas possible proprement (voir le compte rendu)", font=cm.police(17, True),
           fill=(200, 160, 90))
    un = cm.sol_et_mur(-24, 40, -32, 14, 14.0)[:1] + mur(-24, 40, 0, 48, 14.0) + echelles(els, cf.tex, 1)
    deux = cm.sol_et_mur(-24, 40, -32, 14, 14.0)[:1] + mur(-24, 40, 0, 48, 14.0, dessus=False) + mur(-24, 40, 48, 96, 14.0, 17) \
        + echelles(els, cf.tex, 2)
    vues = [("un étage", un, (math.radians(180 - 32), math.radians(16)), ((-14, 0, -10), (30, 56, 18))),
            ("deux étages", deux, (math.radians(180 - 32), math.radians(14)), ((-14, 0, -10), (30, 104, 18))),
            ("haut de l'échelle", un, (math.radians(180 - 25), math.radians(22)), ((-2, 30, 6), (18, 52, 20)))]
    for k, (nom, q, (yaw, pitch), bornes) in enumerate(vues):
        v = cm.Vue(yaw, pitch, bornes, 2 * w)
        im.paste(cm.vers_image(cm.rendre(q, v), w), (k * w, 130))
        d.text((k * w + 12, 136), nom, font=cm.police(18, True), fill=(200, 200, 200))
    im.save(dossier / "echelle.png", optimize=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu")
    g.add_argument("--installer", action="store_true")
    args = ap.parse_args()
    cf = cm.Coffre(echelle())
    cm.controler([cf])
    if args.installer:
        installer(cf)
        return
    dossier = Path(args.apercu)
    dossier.mkdir(parents=True, exist_ok=True)
    planche(cf, dossier)
    print("rendus dans", dossier)


if __name__ == "__main__":
    main()
