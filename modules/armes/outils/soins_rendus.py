#!/usr/bin/env python3
"""Deux soins simples demandés par le propriétaire (25/10), rendus validés :

  - Bandage (Commun) : enlève le saignement et rend 2 cœurs. Rouleau de gaze blanche, bout déroulé, épingle.
  - Attelle (Peu commun) : soigne seulement la jambe cassée. Deux planchettes de bois, deux sangles noires à boucle.

Moteur de modules/bases/outils/explosifs.py (comme le recycleur). --installer écrit, pour chaque soin, son modèle en main
(le squelette du CAR Kit, carfak_geo, pour garder les mains et les animations de « Attelle et bandage » : le soin remplace
la trousse dans la main gauche et la bande dans la main droite), sa texture et son icône. Jamais à la main.

    python modules/armes/outils/soins_rendus.py --apercu DOSSIER
    python modules/armes/outils/soins_rendus.py --installer
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

RACINE = Path(__file__).resolve().parents[1]
DEPOT = RACINE.parents[1]
sys.path.insert(0, str(DEPOT / "modules" / "bases" / "outils"))
sys.path.insert(0, str(DEPOT / "modules" / "pnj" / "outils"))
import coeur_tc_rendus as cr  # noqa: E402
import explosifs as ex  # noqa: E402
from explosifs import Cube, Os, bruit, col, fbm, melange  # noqa: E402
import recycleur_modele as rm  # noqa: E402  (cylindres à 16 pans)


def peindre(mat, p, n, fu, fv, w, h, prm):
    sh = p.shape[:2]
    a = np.ones(sh)
    g = np.zeros(sh + (3,))
    e = ex.bords(fu, fv, w, h)
    arete = np.exp(-e / 0.08)
    if mat == "gaze":
        trame = (np.sin(p[..., 0] * 9) * 0.5 + 0.5) * (np.sin((p[..., 1] + p[..., 2]) * 9) * 0.5 + 0.5)
        r = melange(col(214, 214, 206), col(244, 243, 236), trame * 0.6 + fbm(p, 1.5, 2, 90) * 0.3)
        r = melange(r, col(186, 184, 176), arete * 0.4)
    elif mat == "gaze_tranche":                                          # spirale du rouleau vue de côté
        rr = np.hypot(p[..., 1] - prm["cy"], p[..., 2] - prm["cz"])
        spirale = np.sin(rr * 7.0 + np.arctan2(p[..., 1] - prm["cy"], p[..., 2] - prm["cz"]) * 0.5) * 0.5 + 0.5
        r = melange(col(198, 197, 188), col(240, 239, 232), spirale)
    elif mat == "bois_clair":
        fibres = np.sin(p[..., 1] * 0.8 + fbm(p * np.array([1, 0.15, 1]), 1.6, 3, 91) * 5) * 0.5 + 0.5
        r = melange(col(150, 112, 70), col(196, 156, 104), fibres)
        r = melange(r, col(110, 80, 50), arete * 0.5)
    elif mat == "sangle":
        trame = (np.sin(p[..., 0] * 24) * np.sin(p[..., 1] * 24 + p[..., 2] * 24)) * 0.5 + 0.5
        r = melange(col(26, 27, 29), col(48, 49, 52), trame * 0.6 + bruit(p, 3, 92) * 0.2)
        r = melange(r, col(80, 80, 84), arete * 0.4)
    elif mat == "boucle":
        r = melange(col(150, 154, 160), col(214, 218, 224), bruit(p, 4, 93))
    elif mat == "mousse":
        r = melange(col(60, 92, 140), col(82, 118, 170), bruit(p, 6, 94))
    else:
        return rm.peindre(mat, p, n, fu, fv, w, h, prm)
    return np.clip(r, 0, 1), a, np.clip(g, 0, 1)


def bandage():
    os_ = [Os("b")]
    cubes = []

    def B(a, b, mat, **k):
        cubes.append(Cube("b", a, b, mat, **k))

    # rouleau couché le long de x
    rm.cylindre_x(B, 0, 4.0, 8.0, 3.6, 3.0, 13.0, "gaze")
    rm.cylindre_x(B, 0, 4.0, 8.0, 1.2, 2.95, 13.05, "gaze_tranche", prm={"cy": 4.0, "cz": 8.0})
    # bout déroulé posé au sol, épingle de sûreté
    B((4.0, 0.0, 10.8), (12.0, 0.4, 15.6), "gaze")
    B((5.0, 0.4, 13.6), (8.6, 0.8, 14.0), "boucle")
    return os_, cubes


def attelle():
    os_ = [Os("a")]
    cubes = []

    def B(a, b, mat, **k):
        cubes.append(Cube("a", a, b, mat, **k))

    for x0 in (4.0, 9.2):                                                 # deux planchettes
        B((x0, 0.0, 1.0), (x0 + 2.8, 1.0, 15.0), "bois_clair")
        B((x0 + 0.3, 1.0, 2.0), (x0 + 2.5, 1.4, 14.0), "mousse")
    for z0 in (3.5, 10.5):                                                # deux sangles et leur boucle
        B((3.5, -0.05, z0), (12.5, 1.6, z0 + 1.6), "sangle")
        B((11.0, 1.6, z0 - 0.2), (12.6, 1.9, z0 + 1.8), "boucle")
    return os_, cubes


VUES = {"trois quarts": (math.radians(215), math.radians(30)), "dessus": (math.radians(200), math.radians(70))}


# ================================================================================================ installation
ASSETS = RACINE / "src" / "client" / "resources" / "assets" / "heralisz_heal"
# os du CAR Kit remplacés : la trousse (main gauche) et la bande (main droite)
OS_TROUSSE = ("medic", "bag_front", "zipper", "chain", "bag_back")
OS_BANDE = ("bandage", "bone")


def en_main(nom):
    """Pièces du soin en main, coordonnées du moteur (x opposé à celui de Bedrock) : « medic » (main gauche, à la place de
    la trousse), « bandage » et « bone » (main droite, aux mêmes places que la bande du CAR Kit)."""
    os_ = [Os("medic", None, (0, 0, 0.5)), Os("bandage", None, (0, 2.25, 0)), Os("bone", "bandage", (0.236, 2.25, 0.447))]
    cubes = []

    def B(osn, a, b, mat, **k):
        cubes.append(Cube(osn, a, b, mat, **k))

    if nom == "bandage":
        G = lambda a, b, mat, **k: B("medic", a, b, mat, **k)
        rm.cylindre_x(G, 0, 1.8, 0.0, 1.8, -3.4, 3.4, "gaze")
        rm.cylindre_x(G, 0, 1.8, 0.0, 0.6, -3.45, 3.45, "gaze_tranche", prm={"cy": 1.8, "cz": 0.0})
        G((-2.6, 0.0, 1.2), (2.6, 0.15, 3.4), "gaze")
        G((-1.6, 0.15, 2.5), (0.6, 0.35, 2.7), "boucle")
        bande, bout = "gaze", "gaze"
    else:
        for z0 in (-1.5, 0.3):                                            # deux planchettes et leur mousse
            B("medic", (-3.5, 0.0, z0), (3.5, 0.5, z0 + 1.2), "bois_clair")
            B("medic", (-3.3, 0.5, z0 + 0.15), (3.3, 0.7, z0 + 1.05), "mousse")
        for x0 in (-2.3, 1.5):                                            # deux sangles et leur boucle
            B("medic", (x0, -0.05, -1.7), (x0 + 0.8, 0.85, 1.7), "sangle")
            B("medic", (x0 - 0.05, 0.85, 0.9), (x0 + 0.85, 1.0, 1.7), "boucle")
        bande, bout = "sangle", "boucle"
    # main droite : mêmes dimensions que la bande du CAR Kit (les animations la tiennent là)
    B("bandage", (-0.764, 1.0, -0.553), (0.236, 3.5, 0.447), bande)
    B("bone", (0.236, 1.0, 0.197), (0.986, 3.5, 0.447), bout, rot=(0, 67.5, 0), pivot=(0.236, 2.25, 0.447))
    return os_, cubes


def icone(os_, cubes, tex, lueur):
    """Icône d'inventaire 128 px sur fond transparent (rendu du moteur, fond retiré)."""
    # seulement le soin lui-même (pas la bande tenue dans l'autre main pendant l'animation)
    im = ex.rendu(os_, cubes, tex, lueur, {"bandage", "bone"}, (math.radians(215), math.radians(32)), taille=512)
    a = np.asarray(im).astype(float)
    fond = np.array([0.11, 0.12, 0.13]) * 255
    masque = (np.abs(a - fond).max(-1) > 7).astype(np.uint8) * 255
    rgba = Image.fromarray(np.dstack([a.astype(np.uint8), masque]), "RGBA")
    return rgba.resize((128, 128), Image.LANCZOS)


def installer():
    carfak = json.loads((ASSETS / "geo_models/consumable/carfak_geo.json").read_text(encoding="utf-8"))
    ex.D, ex.LARGEUR, ex.MARGE = 8, 256, 1
    for nom in ("bandage", "attelle"):
        os_, cubes = en_main(nom)
        tex, lueur, tw, th = ex.construire(os_, cubes)
        nos = {b["name"]: b.get("cubes", []) for b in ex.geo("tmp", os_, cubes, tw, th, (2, 2))["minecraft:geometry"][0]["bones"]}
        g = json.loads(json.dumps(carfak))
        geom = g["minecraft:geometry"][0]
        geom["description"].update({"identifier": f"geometry.heralisz_heal.{nom}", "texture_width": tw, "texture_height": th})
        for b in geom["bones"]:
            if b["name"] in OS_TROUSSE + OS_BANDE:
                if nos.get(b["name"]):
                    b["cubes"] = nos[b["name"]]
                else:
                    b.pop("cubes", None)
        (ASSETS / f"geo_models/consumable/{nom}_geo.json").write_text(json.dumps(g, indent=1) + "\n", encoding="utf-8")
        ex.png(tex, ASSETS / f"textures/consumable/{nom}_uv.png")
        icone(os_, cubes, tex, lueur).save(ASSETS / f"textures/consumable/slot/{nom}.png", optimize=True)
        affichage = json.loads((ASSETS / "display/consumable/attelle_bandage.json").read_text(encoding="utf-8"))
        affichage.update({"model": f"heralisz_heal:consumable/{nom}_geo", "animation": f"heralisz_heal:consumable/{nom}",  # 26/10 : animation propre (animations_soins.py)
                          "texture": f"heralisz_heal:consumable/{nom}_uv", "slot_texture": f"heralisz_heal:consumable/slot/{nom}"})
        (ASSETS / f"display/consumable/{nom}.json").write_text(json.dumps(affichage, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"[soins] {nom} : {len(cubes)} pièces, texture {tex.shape[1]} x {tex.shape[0]}")


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", type=Path)
    g.add_argument("--installer", action="store_true")
    args = ap.parse_args()
    ex.peindre = peindre
    if args.installer:
        installer()
        return
    args.apercu.mkdir(parents=True, exist_ok=True)
    ex.D, ex.LARGEUR, ex.MARGE = 16, 1024, 2
    ims, titres = [], []
    for nom, fab in (("Bandage (Commun)", bandage), ("Attelle (Peu commun)", attelle)):
        os_, cubes = fab()
        tex, lueur, _, _ = ex.construire(os_, cubes)
        for v, ang in VUES.items():
            ims.append(ex.rendu(os_, cubes, tex, lueur, set(), ang, taille=800))
            titres.append(f"{nom} · {v}")
    ex.planche(ims, titres, args.apercu / "soins_simples.png")
    print("rendus dans", args.apercu)


if __name__ == "__main__":
    main()
