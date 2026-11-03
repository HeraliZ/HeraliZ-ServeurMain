#!/usr/bin/env python3
"""
Cœur de clan plus simple (retours du propriétaire du 03/11 : « un truc plus simple, qui n'a pas de rotation, ou qui
n'affiche rien mais quand on clique dessus on a accès aux infos »). Option A validée par le propriétaire le 03/11 et
installée (--installer) ; l'option B reste dans les rendus.

Deux options, modèles de bloc ordinaires (plus de modèles OBJ, plus de pièce qui tourne, plus d'hologramme), aucune face
confondue (faces_confondues.py) :
  A. « simple et fixe » : socle bas en acier graphite, colonne, cristal cyan fixe qui éclaire (pleine lumière), plaque des
     points sur l'avant (texte écrit par le jeu, comme aujourd'hui) ;
  B. « discret » : une plaque au sol de 14 × 14 px, 1,5 px de haut, filet cyan ; on marche dessus ; rien d'écrit.
Dans les deux cas, clic droit d'un membre du clan : écran du clan, onglet Saison (déjà branché le 03/11).

    python modules/bases/outils/coeur_simple.py --apercu DOSSIER     rendus des deux options
    python modules/bases/outils/coeur_simple.py --installer          écrit l'option A dans le mod : modèle du bloc (et de
        l'objet), texture, client/render/CoeurClanDonnees.java (place de la plaque des points) ; jamais à la main

Remplace coeur_modele.py (modèles OBJ du 22/10 : socle, filets lumineux et cristal qui tournait, retirés le 03/11). La
forme du bloc (CoeurClanBlock.FORME) suit ce modèle : socle 1..15 × 0..3,4, colonne 5..11 jusqu'à 14 px.
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import coffres_modeles as cm  # noqa: E402

ex = cm.ex
_peindre = ex.peindre


def peindre(mat, p, n, fu, fv, w, h, prm):
    """Matières du cœur en plus : cristal et filet cyan (lumineux)."""
    if mat == "cristal_cyan":
        e = ex.bords(fu, fv, w, h)
        c = np.exp(-((fu - 0.5) ** 2 + (fv - 0.5) ** 2) / 0.25)
        r = cm.melange(cm.col(40, 170, 210), cm.col(190, 250, 255), c * 0.8 + 0.2 * cm.bruit(p, 3.0, 7))
        r = cm.melange(r, cm.col(230, 255, 255), np.exp(-e / 0.12) * 0.6)
        return np.clip(r, 0, 1), np.ones(r.shape[:2]), np.clip(r, 0, 1)
    if mat == "filet_cyan":
        r = cm.melange(cm.col(60, 200, 235), cm.col(140, 240, 255), cm.bruit(p, 4.0, 9))
        return np.clip(r, 0, 1), np.ones(r.shape[:2]), np.clip(r, 0, 1)
    return _peindre(mat, p, n, fu, fv, w, h, prm)


ex.peindre = peindre
cm.LUMINEUX = cm.LUMINEUX | {"cristal_cyan", "filet_cyan"}


def option_a():
    """Socle bas, colonne et cristal fixe, plaque des points devant."""
    m = cm.Modele("coeur_a", "legendaire", "1 bloc", "large", 1)
    B = m.B
    G = {"filet": 0.8}
    B("corps", (1.0, 0, 1.0), (15.0, 3.0, 15.0), "acier_coffre", prm=G)                       # socle
    for a, b in (((1.0, 3.0, 1.0), (15.0, 3.4, 1.6)), ((1.0, 3.0, 14.4), (15.0, 3.4, 15.0)),     # filet cyan du bord
                 ((1.0, 3.0, 1.6), (1.6, 3.4, 14.4)), ((14.4, 3.0, 1.6), (15.0, 3.4, 14.4))):
        B("corps", a, b, "filet_cyan", faces=("up", "north", "south", "east", "west"))
    B("corps", (5.0, 3.5, 5.0), (11.0, 6.0, 11.0), "acier_coffre", prm=dict(G, filet=None))   # colonne
    B("corps", (6.0, 6.0, 6.0), (10.0, 13.0, 10.0), "cristal_cyan")                            # cristal (fixe)
    B("corps", (6.5, 13.0, 6.5), (9.5, 14.0, 9.5), "cristal_cyan")
    B("corps", (3.6, 0.6, 0.6), (12.4, 2.4, 1.0), "sombre", faces=("north", "east", "west", "up", "down"))  # plaque
    return m


def option_b():
    """Plaque au sol, filet cyan : presque invisible, on marche dessus."""
    m = cm.Modele("coeur_b", "legendaire", "1 bloc", "large", 1)
    B = m.B
    B("corps", (1.0, 0, 1.0), (15.0, 1.0, 15.0), "acier_coffre", prm={"filet": 1.2})
    B("corps", (6.0, 1.0, 6.0), (10.0, 1.5, 10.0), "filet_cyan")
    return m


def vue(cf, yaw, pitch, bornes, taille, decor=True):
    a, b = cf.bornes()
    quads = cf.quads(0.0)
    if decor:
        quads = cm.sol_et_mur(-24, 40, -24, 40, 40.0)[:1] + quads
    v = cm.Vue(yaw, pitch, bornes, taille * 2)
    return cm.vers_image(cm.rendre(quads, v, ombre_sol=(a[0], a[2], b[0], b[2], 0.0)), taille)


def planche(dossier):
    opts = [("A · simple et fixe", "socle, cristal fixe qui éclaire, points écrits sur la plaque", cm.Coffre(option_a())),
            ("B · discret", "plaque au sol, on marche dessus, rien d'écrit", cm.Coffre(option_b()))]
    cm.controler([c for _, _, c in opts])
    w = 460
    im = Image.new("RGB", (3 * w, 2 * (w + 70) + 90), (20, 22, 25))
    d = ImageDraw.Draw(im)
    d.text((24, 16), "Cœur de clan : deux propositions (à valider)", font=cm.police(34), fill=(236, 236, 236))
    d.text((24, 58), "Rien ne tourne, plus d'hologramme ; clic droit d'un membre : écran du clan, onglet Saison",
           font=cm.police(20, True), fill=(92, 230, 255))
    bornes = ((-4, 0, -4), (20, 16, 20))
    y = 100
    for titre, sous, cf in opts:
        d.text((24, y), titre, font=cm.police(28), fill=(236, 236, 236))
        d.text((360, y + 6), sous, font=cm.police(20, True), fill=(170, 170, 170))
        for k, (yaw, pitch) in enumerate(((math.radians(180 - 32), math.radians(26)), (math.radians(180), math.radians(8)),
                                          (math.radians(180 - 20), math.radians(62)))):
            im.paste(vue(cf, yaw, pitch, bornes, w), (k * w, y + 40))
        y += w + 70
    im.save(dossier / "coeur_options.png", optimize=True)
    for _, _, cf in opts:
        print(f"[coeur] {cf.m.nom} : {cf.nb_elements()} pièces")


PLAQUE = (3.6, 12.4, 0.6, 2.4, 0.6)   # x0, x1, y0, y1, z de la face avant de la plaque (option A)
AFFICHAGE = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.625, 0.625, 0.625]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}
JAVA = cm.RACINE / "src" / "client" / "java" / "fr" / "heraliz" / "crac" / "client" / "render" / "CoeurClanDonnees.java"
ANCIENS = ["coeur_clan.obj", "coeur_clan.mtl", "coeur_clan_lueur.json", "coeur_clan_lueur.obj", "coeur_clan_cristal.json",
           "coeur_clan_cristal.obj"]


def installer():
    import adossement
    cf = cm.Coffre(option_a())
    cm.controler([cf])
    tex = "crac:block/coeur_clan"
    cf.atlas.save(cm.ASSETS / "textures" / "block" / "coeur_clan.png", optimize=True)
    m = {"credit": "HeraliZ, modules/bases/outils/coeur_simple.py", "textures": {"0": tex, "particle": tex},
         "elements": cf.groupes["corps"], "display": AFFICHAGE}
    adossement.ecrire(cm.ASSETS / "models" / "block" / "coeur_clan.json", m)
    for nom in ANCIENS:
        f = cm.ASSETS / "models" / "block" / nom
        if f.exists():
            f.unlink()
    x0, x1, y0, y1, z = PLAQUE
    JAVA.write_text(f"""package fr.heraliz.crac.client.render;

/**
 * Place de la plaque des points du cœur de clan (modèle tourné vers le nord, px). ÉCRIT par
 * modules/bases/outils/coeur_simple.py --installer : jamais à la main.
 */
final class CoeurClanDonnees {{
    /** Plaque des points, sur l'avant du socle. */
    static final float PLAQUE_X0 = {x0}F, PLAQUE_X1 = {x1}F, PLAQUE_Y0 = {y0}F, PLAQUE_Y1 = {y1}F, PLAQUE_Z = {z}F;

    private CoeurClanDonnees() {{
    }}
}}
""", encoding="utf-8", newline="\n")
    print(f"[coeur] option A installée : {cf.nb_elements()} pièces, texture {cf.atlas.size[0]} px")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu")
    g.add_argument("--installer", action="store_true")
    args = ap.parse_args()
    if args.installer:
        installer()
        return
    dossier = Path(args.apercu)
    dossier.mkdir(parents=True, exist_ok=True)
    planche(dossier)
    print("rendus dans", dossier)


if __name__ == "__main__":
    main()
