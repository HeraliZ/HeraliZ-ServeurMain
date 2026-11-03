#!/usr/bin/env python3
"""
Échelle de raid de CRac (crac:echelle, 03/11) : modèle dans le style des pièces de base (deux montants en planches, quatre
barreaux, deux colliers d'acier), texture et rendus. Rien n'est dessiné à la main : on modifie ce script.

    python modules/bases/outils/echelle_modele.py --apercu DOSSIER    rendus (contre un mur, empilée sur un étage)
    python modules/bases/outils/echelle_modele.py --installer         écrit dans le mod : texture, modèles (contre une
        fondation : echelle ; dans la case d'un mur : echelle_mur, 2 px plus loin du bord), objet

Échelle tournée vers le nord (le mur est au sud) ; 3 px d'épaisseur comme la forme du bloc (EchelleBlock.EPAISSEUR), de z
13 à 16 contre une fondation, de 11 à 14 dans la case d'un mur (la face du mur y est à 2 px du bord). Contrôle : aucune
face confondue (faces_confondues.py).
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


def echelle():
    m = cm.Modele("echelle", "peu_commune", "1 bloc", "large", 1)
    B = m.B
    z0, z1 = 16.0 - EPAISSEUR, 16.0
    for x0, g in ((1.5, 1), (12.5, 2)):                                                   # montants
        B("corps", (x0, 0, z0), (x0 + 2.0, 16.0, z1), "bois_brut", prm={"graine": g})
    for k, y0 in enumerate((1.5, 5.5, 9.5, 13.5)):                                        # barreaux
        B("corps", (3.5, y0, z0 + 0.6), (12.5, y0 + 1.5, z1 - 0.6), "bois_brut", prm={"graine": 10 + k})
    for y0 in (0.5, 12.0):                                                                # colliers d'acier (devant)
        for x0 in (1.25, 12.25):
            B("corps", (x0, y0, z0 - 0.25), (x0 + 2.5, y0 + 1.25, z0), "acier_coin", prm={"rivets": False},
              faces=("north", "east", "west", "up", "down"))
    return m


def modeles(cf):
    """Éléments contre une fondation, et dans la case d'un mur (2 px vers le nord)."""
    pres = cf.groupes["corps"]
    mur = cm.decaler_elements(pres, 0.0, 0.0, -2.0)
    return pres, mur


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
    pres, mur = modeles(cf)
    for nom, els in (("echelle", pres), ("echelle_mur", mur)):
        fc.exiger_aucun(nom, els)
    tex = "crac:block/echelle"
    cf.atlas.save(cm.ASSETS / "textures" / "block" / "echelle.png", optimize=True)
    for nom, els in (("echelle", pres), ("echelle_mur", mur)):
        adossement.ecrire(cm.ASSETS / "models" / "block" / f"{nom}.json",
                          {"credit": "HeraliZ, modules/bases/outils/echelle_modele.py", "textures": {"0": tex, "particle": tex},
                           "elements": els})
    adossement.ecrire(cm.ASSETS / "models" / "item" / "echelle.json",
                      {"parent": "crac:block/echelle", "display": AFFICHAGE})
    print(f"[echelle] {cf.nb_elements()} pièces, texture {cf.atlas.size[0]} px")


def planche(cf, dossier):
    pres, mur = modeles(cf)
    w = 520
    im = Image.new("RGB", (3 * w, w + 120), (20, 22, 25))
    d = ImageDraw.Draw(im)
    d.text((24, 14), "Échelle de raid (proposition, à valider)", font=cm.police(32), fill=(236, 236, 236))
    d.text((24, 56), "contre la face d'un mur droit : 3 échelles pour un étage, une 4e au-dessus pour passer sur le toit",
           font=cm.police(19, True), fill=(92, 191, 74))
    # colonne de 4 échelles dans la rangée du mur (face du mur à z = 14), sur une fondation, mur de 3 blocs
    quads = cm.sol_et_mur(-24, 40, -32, 14, 14.0)
    for k in range(4):
        quads += cm.quads_elements(mur, cf.tex, [("decal", (0, 16.0 * k, 0))])
    vues = [("trois quarts", (math.radians(180 - 35), math.radians(18)), ((-8, 0, -6), (24, 64, 16))),
            ("de face", (math.radians(180), math.radians(6)), ((-8, 0, -6), (24, 64, 16))),
            ("de près", (math.radians(180 - 25), math.radians(15)), ((-1, 0, 8), (17, 18, 16)))]
    for k, (nom, (yaw, pitch), bornes) in enumerate(vues):
        v = cm.Vue(yaw, pitch, bornes, 2 * w)
        q = quads if k < 2 else cm.sol_et_mur(-8, 24, -8, 14, 14.0) + cm.quads_elements(mur, cf.tex)
        im.paste(cm.vers_image(cm.rendre(q, v), w), (k * w, 100))
        d.text((k * w + 12, 106), nom, font=cm.police(18, True), fill=(200, 200, 200))
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
