#!/usr/bin/env python3
"""
Cœur de clan (saison des clans, modèle validé le 22/10) : écrit les modèles du jeu depuis celui des rendus
(coeur_tc_rendus.modele_coeur_futur, jeu=True). Rien n'est dessiné à la main : on modifie ces scripts.

Modèles OBJ (chargeur « forge:obj » de Forge : formes tournées librement, bien lisses), une texture commune :
  - coeur_clan          le socle et les bras : modèle fixe du bloc (aucun coût par image) ;
  - coeur_clan_lueur    les filets lumineux : dessinés par CoeurClanRendu en pleine lumière ;
  - coeur_clan_cristal  le cristal : dessiné par CoeurClanRendu, il tourne et flotte.
Les points (plaque du socle) et l'hologramme des rangs sont écrits en direct par le jeu : leurs places sont dans
CoeurClanDonnees.java, écrit ici aussi.

    python mods/crac/outils/coeur_modele.py --installer     écrit modèles, texture, états, objet et CoeurClanDonnees
    python mods/crac/outils/coeur_modele.py --apercu DOSSIER mêmes fichiers dans DOSSIER

Avant d'écrire, le script vérifie que le cristal tourne sans rien traverser (coeur_tc_rendus.verifier_rotation).
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import coeur_tc_rendus as cr  # noqa: E402
import explosifs as ex  # noqa: E402
from explosifs import Os  # noqa: E402

RACINE = Path(__file__).resolve().parents[1]
ASSETS = RACINE / "src" / "client" / "resources" / "assets" / "crac"
JAVA = RACINE / "src" / "client" / "java" / "fr" / "heraliz" / "crac" / "client" / "render" / "CoeurClanDonnees.java"
TEXTURE = "crac:block/coeur_clan"
DENSITE = 6
LARGEUR = 512
PARTIES = {"socle": "coeur_clan", "lueur": "coeur_clan_lueur", "cristal": "coeur_clan_cristal"}
AFFICHAGE = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.6, 0.6, 0.6]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.25, 0.25, 0.25]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -1.5, 0], "scale": [0.5, 0.5, 0.5]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]},
}
ROTATION = {"north": 0, "east": 90, "south": 180, "west": 270}


def construire():
    os_, cubes = cr.modele_coeur_futur(jeu=True)
    marge = cr.verifier_rotation(cubes)
    ex.D, ex.LARGEUR, ex.MARGE = DENSITE, LARGEUR, 1
    ex.peindre = cr.peindre_futur            # matières du cœur (graphite, filets, énergie)
    tex, _, tw, th = ex.construire(os_, cubes)
    if tex.shape[0] < tex.shape[1]:
        tex = np.concatenate([tex, np.zeros((tex.shape[1] - tex.shape[0], tex.shape[1], 4))])
        th = tw
    if tex.shape[0] != tex.shape[1]:
        raise SystemExit(f"texture pas carrée : {tex.shape[1]}×{tex.shape[0]}")
    objs = {}
    for partie in PARTIES:
        objs[partie] = obj([c for c in cubes if c.os == partie], tw, th)
    img = Image.fromarray((np.clip(tex, 0, 1) * 255).astype(np.uint8), "RGBA")
    return objs, img, marge


def obj(cubes, tw, th):
    """Fichier OBJ (coordonnées en blocs, 0 à 1) : un quadrilatère par face, tourné dans le sens qui le montre de
    l'extérieur, UV dans la texture commune (v depuis le haut de l'image, « flip_v » à false)."""
    v, vt, vn, f = [], [], [], []
    for c in cubes:
        rot = ex.rot_matrice(c.rot)
        pv = np.array(c.pivot if c.pivot else (0, 0, 0), float)
        for face in c.faces:
            u0, v0, w, h = c.uv[face]
            coins = c.point(face, np.array([0, 1, 1, 0.0]), np.array([0, 0, 1, 1.0]))
            if c.rot:
                coins = (coins - pv) @ rot.T + pv
            normale = rot @ np.array(ex.NORMALES[face], float)
            uv = [(u0, v0), (u0 + w, v0), (u0 + w, v0 + h), (u0, v0 + h)]
            ordre = [0, 1, 2, 3]
            if np.dot(np.cross(coins[1] - coins[0], coins[3] - coins[0]), normale) < 0:
                ordre = [0, 3, 2, 1]
            base_v, base_t = len(v), len(vt)
            for k in ordre:
                v.append(coins[k] / 16.0)
                vt.append((uv[k][0] / tw, uv[k][1] / th))   # v depuis le haut : Forge le lit tel quel (flip_v false)
            vn.append(normale)
            n = len(vn)
            f.append(" ".join(f"{base_v + i + 1}/{base_t + i + 1}/{n}" for i in range(4)))
    lignes = ["# HeraliZ, mods/crac/outils/coeur_modele.py (écrit, jamais à la main)", "mtllib coeur_clan.mtl",
              "o coeur", "usemtl coeur"]
    lignes += [f"v {p[0]:.5f} {p[1]:.5f} {p[2]:.5f}" for p in v]
    lignes += [f"vt {t[0]:.6f} {t[1]:.6f}" for t in vt]
    lignes += [f"vn {n[0]:.5f} {n[1]:.5f} {n[2]:.5f}" for n in vn]
    lignes += [f"f {x}" for x in f]
    return "\n".join(lignes) + "\n"


def json_modele(nom, affichage=False):
    m = {"credit": "HeraliZ, mods/crac/outils/coeur_modele.py", "loader": "forge:obj",
         "model": f"crac:models/block/{nom}.obj", "flip_v": False, "automatic_culling": False,
         "textures": {"coeur": TEXTURE, "particle": TEXTURE}}
    if affichage:
        m["display"] = AFFICHAGE
    return m


def java():
    p, h, c = cr.PLAQUE, cr.HOLO, cr.CRISTAL
    return f"""package fr.heraliz.crac.client.render;

/**
 * Places du cœur de clan (modèle tourné vers le nord, px). ÉCRIT par mods/crac/outils/coeur_modele.py --installer :
 * jamais à la main.
 */
final class CoeurClanDonnees {{
    /** Plaque des points, sur l'avant du socle. */
    static final float PLAQUE_X0 = {p["x0"]}F, PLAQUE_X1 = {p["x1"]}F, PLAQUE_Y0 = {p["y0"]}F, PLAQUE_Y1 = {p["y1"]}F, PLAQUE_Z = {p["z"]}F;
    /** Hologramme des rangs : centre (hauteur) et taille. */
    static final float HOLO_Y = {h["y"]}F, HOLO_LARGEUR = {h["largeur"]}F, HOLO_HAUTEUR = {h["hauteur"]}F;
    /** Centre du cristal (il tourne autour de l'axe vertical qui passe par le milieu du bloc). */
    static final float CRISTAL_Y = {c["y"]}F;

    private CoeurClanDonnees() {{
    }}
}}
"""


def ecrire(dossier, objs, img):
    mdl = dossier / "models" / "block"
    mdl.mkdir(parents=True, exist_ok=True)
    (mdl / "coeur_clan.mtl").write_text("newmtl coeur\nmap_Kd #coeur\n", encoding="utf-8")
    for partie, nom in PARTIES.items():
        (mdl / f"{nom}.obj").write_text(objs[partie], encoding="utf-8")
        (mdl / f"{nom}.json").write_text(json.dumps(json_modele(nom, partie == "socle"), indent=2) + "\n",
                                         encoding="utf-8")
    (dossier / "textures" / "block").mkdir(parents=True, exist_ok=True)
    img.save(dossier / "textures" / "block" / "coeur_clan.png", optimize=True)
    (dossier / "models" / "item").mkdir(parents=True, exist_ok=True)
    (dossier / "models" / "item" / "coeur_clan.json").write_text(
        json.dumps({"parent": "crac:block/coeur_clan"}, indent=2) + "\n", encoding="utf-8")
    etats = {"variants": {f"facing={f}": ({"model": "crac:block/coeur_clan", "y": r} if r else
                                          {"model": "crac:block/coeur_clan"}) for f, r in ROTATION.items()}}
    (dossier / "blockstates").mkdir(parents=True, exist_ok=True)
    (dossier / "blockstates" / "coeur_clan.json").write_text(json.dumps(etats, indent=2) + "\n", encoding="utf-8")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", type=Path)
    g.add_argument("--installer", action="store_true")
    args = ap.parse_args()
    objs, img, marge = construire()
    dossier = ASSETS if args.installer else args.apercu
    ecrire(dossier, objs, img)
    if args.installer:
        JAVA.write_text(java(), encoding="utf-8")
    faces = sum(o.count("\nf ") for o in objs.values())
    print(f"[coeur] {faces} faces, texture {img.size[0]}×{img.size[1]}, cristal libre à {marge:.2f} px -> {dossier}")


if __name__ == "__main__":
    main()
