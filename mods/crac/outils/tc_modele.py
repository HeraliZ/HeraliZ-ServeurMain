#!/usr/bin/env python3
"""
Modèle « de survie » de l'armoire à outils (Claim Block façon TC de Rust), validé le 22/10 (saison des clans).

Armoire faite de récupération : tôles ondulées rouillées, vieille peinture verte, plaques soudées, bande danger, chaîne
et cadenas, palette en bois, batterie de voiture sur le flanc, « NE PAS TOUCHER » à la bombe, antenne, et un petit écran
rouillé qui bugue. Le texte de l'écran (entretien demandé, temps avant disparition) est écrit en direct par le jeu du
joueur (client/render/EcranArmoireRendu) : la texture n'a que le fond de l'écran (lignes, fêlure, coin rouillé).

Les matières et l'écran sont ceux des rendus validés (coeur_tc_rendus.py, moteur de explosifs.py). Rien n'est dessiné
à la main : on modifie ce script.

    python mods/crac/outils/tc_modele.py --apercu DOSSIER     écrit claim_block.json, claim_block.png et un rendu
    python mods/crac/outils/tc_modele.py --installer          écrit dans le mod : modèle et texture du Claim Block, sa
                                                              case du haut, l'objet, les états et modèles adossés
                                                              (adossement.py) et EcranArmoireDonnees.java

Le modèle fait 1 bloc de large et 2 de haut (y de 0 à 32), face avant au nord (z petit), et tient dans la boîte
d'adossement de l'armoire (x 0,25 à 15,75, z 2,5 à 15,75 : BOITE de ClaimBlock et BOITE_ARMOIRE d'adossement.py).
Pièces tournées : un seul axe, par pas de 22,5° (règle des modèles de Minecraft).
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import coeur_tc_rendus as cr  # noqa: E402  (branche les matières en plus sur le moteur)
import explosifs as ex  # noqa: E402
from explosifs import Cube, Decal, Os, col, masque_texte  # noqa: E402

RACINE = Path(__file__).resolve().parents[1]
ASSETS = RACINE / "src" / "client" / "resources" / "assets" / "crac"
JAVA_ECRAN = RACINE / "src" / "client" / "java" / "fr" / "heraliz" / "crac" / "client" / "render" / "EcranArmoireDonnees.java"
TEXTURE = "crac:block/claim_block"
DENSITE = 4           # points de texture par pixel de modèle (64 par bloc, 4 fois Minecraft)
LARGEUR = 512
BOITE = (0.25, 2.5, 15.75, 15.75)   # x0, z0, x1, z1 : la boîte d'adossement (ne pas en sortir)
# surface de l'écran (modèle tourné vers le nord) : le texte est écrit juste devant
ECRAN = {"x0": 3.6, "x1": 12.4, "y0": 19.6, "y1": 25.8, "z": 3.15}
# en main et dans l'inventaire : l'armoire fait 2 blocs de haut, réduite et redescendue au centre
AFFICHAGE = {
    "gui": {"rotation": [30, 225, 0], "translation": [0, -3.2, 0], "scale": [0.4, 0.4, 0.4]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 1, 0], "scale": [0.2, 0.2, 0.2]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -3.2, 0], "scale": [0.4, 0.4, 0.4]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 0], "scale": [0.25, 0.25, 0.25]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 0.5, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, -2, 0], "scale": [0.25, 0.25, 0.25]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [0, -2, 0], "scale": [0.25, 0.25, 0.25]},
}
# pièces qui brillent dans le noir (lumière propre du modèle, réglage « forge_data » de Forge)
LUMINEUX = {"ecran_img", "lampe_ambre"}


# ------------------------------------------------------------------------------------------------- modèle

def cubes_armoire():
    os_ = [Os("tc")]
    cubes = []
    B = lambda *a, **k: cubes.append(Cube(*a, **k))
    # palette de récupération (y 0 à 2,4)
    for k, z0 in enumerate((2.6, 8.4, 14.2)):
        B("tc", (0.5, 0, z0), (15.5, 1.2, z0 + 1.4), "bois", prm={"graine": k})
    for k, x0 in enumerate((0.5, 4.2, 7.9, 11.6)):
        B("tc", (x0, 1.2, 2.6), (x0 + 3.4, 2.4, 15.6), "bois", prm={"graine": 5 + k})
    # caisse : flancs en tôle ondulée rouillée, toit qui déborde
    B("tc", (1.5, 2.4, 4.5), (14.5, 30.0, 15.4), "tole_ondulee")
    B("tc", (0.8, 30.0, 3.0), (15.2, 31.2, 15.75), "tole_ondulee", prm={"graine": 3})
    # façade : porte du bas peinte, plaques soudées, bande danger, porte du haut
    B("tc", (1.8, 3.0, 3.7), (14.2, 13.5, 4.5), "tole_peinte")
    B("tc", (2.4, 4.0, 3.4), (6.8, 7.8, 3.7), "plaque_soudee", prm={"graine": 1})
    B("tc", (9.4, 8.6, 3.4), (13.6, 12.6, 3.7), "plaque_soudee", prm={"graine": 2})
    B("tc", (1.8, 13.5, 3.5), (14.2, 15.0, 4.5), "danger")
    B("tc", (1.8, 15.0, 3.7), (14.2, 29.4, 4.5), "tole_peinte", prm={"graine": 5})
    # cornières d'acier aux angles avant
    for x0 in (0.9, 14.1):
        B("tc", (x0, 2.4, 3.3), (x0 + 1.0, 30.0, 4.7), "acier_brut")
    # pochoir sur le flanc gauche (ouest) : « NE PAS TOUCHER » à la bombe
    pochoir = np.asarray(Image.fromarray((masque_texte(["NE PAS", "TOUCHER"], 90) * 255).astype(np.uint8))
                         .filter(ImageFilter.GaussianBlur(3)), float) / 255
    B("tc", (1.1, 6.0, 6.0), (1.5, 22.0, 14.5), "tole_ondulee", prm={"graine": 8},
      decals=(Decal(pochoir, (1.1, 18.5, 6.4), (0, 0, 7.7), (0, -6.0, 0), col(150, 24, 18), 0.85,
                    normale=(-1, 0, 0)),))
    # écran rouillé dans son boîtier (le fond seul : le texte est écrit en direct)
    img = cr.image_ecran_tc(texte=False)
    e = ECRAN
    B("tc", (e["x0"] - 0.8, e["y0"] - 0.8, e["z"] + 0.05), (e["x1"] + 0.8, e["y1"] + 0.8, 3.7), "acier_brut",
      prm={"graine": 9})
    B("tc", (e["x0"], e["y0"], e["z"]), (e["x1"], e["y1"], e["z"] + 0.05), "ecran_img", faces=("north",),
      prm={"img": img, "cadre": (e["x0"], e["x1"], e["y0"], e["y1"])})
    for vx in (e["x0"] - 0.45, e["x1"] + 0.45):
        for vy in (e["y0"] - 0.45, e["y1"] + 0.45):
            B("tc", (vx - 0.25, vy - 0.25, e["z"] - 0.1), (vx + 0.25, vy + 0.25, e["z"] + 0.05), "chaine")
    # interrupteurs et voyant ambre sous l'écran
    for x0 in (4.0, 5.6, 7.2):
        B("tc", (x0, 16.4, 3.2), (x0 + 0.9, 17.6, 3.7), "plastique")
        B("tc", (x0 + 0.3, 17.1, 2.7), (x0 + 0.6, 17.4, 3.2), "acier_brut")
    B("tc", (10.6, 16.3, 3.1), (11.8, 17.5, 3.7), "lampe_ambre")
    # chaîne en travers de la porte du bas et cadenas
    for k in range(9):
        x = 2.3 + k * 1.35
        y = 13.2 - abs(k - 4) * 0.55
        B("tc", (x, y - 0.35, 2.95), (x + 1.1, y + 0.35, 3.4), "chaine", rot=(0, 0, 22.5 if k < 4 else -22.5),
          pivot=(x + 0.55, y, 3.2))
    B("tc", (7.0, 8.0, 2.6), (9.0, 10.4, 3.4), "laiton")
    B("tc", (7.3, 10.4, 2.85), (8.7, 11.6, 3.15), "chaine")
    # batterie de voiture sanglée sur le flanc droit (est), câble vers l'écran
    B("tc", (14.5, 2.4, 6.0), (15.75, 9.0, 13.0), "plastique")
    B("tc", (14.9, 9.0, 7.0), (15.5, 9.6, 8.0), "borne_rouge")
    B("tc", (14.9, 9.0, 11.0), (15.5, 9.6, 12.0), "acier_brut")
    B("tc", (15.1, 9.6, 7.3), (15.5, 24.0, 7.7), "cable")
    B("tc", (14.2, 23.6, 4.6), (15.5, 24.0, 7.7), "cable")
    # antenne bricolée sur le toit
    B("tc", (11.5, 31.2, 12.5), (12.5, 31.8, 13.5), "acier_brut")
    B("tc", (11.85, 31.8, 12.85), (12.15, 32.0, 13.15), "cable")
    return os_, cubes


# ------------------------------------------------------------------------------------------------- faces cachées

def _aligne(c):
    return c.rot is None


def faces_cachees(cubes):
    """Faces entièrement couvertes par une autre pièce (collées contre elle ou dedans) : jamais vues, pas dessinées."""
    cachees = set()
    for i, c in enumerate(cubes):
        if not _aligne(c):
            continue
        for f in c.faces:
            axe = {"north": 2, "south": 2, "west": 0, "east": 0, "down": 1, "up": 1}[f]
            signe = 1 if f in ("south", "east", "up") else -1
            plan = (c.b if signe > 0 else c.a)[axe] + signe * 0.01
            autres = [k for k in range(3) if k != axe]
            for j, d in enumerate(cubes):
                if j == i or not _aligne(d) or d.mat in LUMINEUX or d.mat == "ecran_img":
                    continue
                if not (d.a[axe] < plan < d.b[axe]):
                    continue
                if all(d.a[k] <= c.a[k] + 1e-6 and d.b[k] >= c.b[k] - 1e-6 for k in autres):
                    cachees.add((i, f))
                    break
    return cachees


# ------------------------------------------------------------------------------------------------- modèle Minecraft

def modele_minecraft(cubes, texture):
    """Éléments du modèle de bloc ; UV du moteur (GeckoLib) retournés comme Minecraft les lit (côtés : u, dessus et
    dessous : v)."""
    # atlas plus léger que pour les rendus
    ex.D, ex.LARGEUR, ex.MARGE = DENSITE, LARGEUR, 1
    tex, _, tw, th = ex.construire([Os("tc")], cubes)
    if tex.shape[0] < tex.shape[1]:                                   # texture carrée
        pad = np.zeros((tex.shape[1] - tex.shape[0], tex.shape[1], 4))
        tex = np.concatenate([tex, pad])
        th = tw
    cachees = faces_cachees(cubes)
    elements = []
    for i, c in enumerate(cubes):
        for v in list(c.a) + list(c.b):
            if v < -16 or v > 32:
                raise SystemExit(f"pièce hors des limites de Minecraft : {c.a} {c.b}")
        faces = {}
        for f in c.faces:
            if (i, f) in cachees:
                continue
            u0, v0, w, h = c.uv[f]
            U0, V0 = u0 * 16 / tw, v0 * 16 / th
            U1, V1 = (u0 + w) * 16 / tw, (v0 + h) * 16 / th
            uv = [U0, V1, U1, V0] if f in ("up", "down") else [U1, V0, U0, V1]
            faces[f] = {"uv": [round(x, 4) for x in uv], "texture": "#0"}
        if not faces:
            continue
        el = {"from": [round(x, 4) for x in c.a], "to": [round(x, 4) for x in c.b], "faces": faces}
        if c.rot:
            axes = [k for k, a in enumerate(c.rot) if a]
            if len(axes) != 1 or abs(c.rot[axes[0]]) % 22.5 or abs(c.rot[axes[0]]) > 45:
                raise SystemExit(f"rotation impossible dans un modèle de bloc : {c.rot}")
            el["rotation"] = {"origin": [round(x, 4) for x in c.pivot], "axis": "xyz"[axes[0]],
                              "angle": c.rot[axes[0]]}
        if c.mat in LUMINEUX:
            el["forge_data"] = {"block_light": 15, "sky_light": 15}
            el["shade"] = False
        elements.append(el)
    modele = {"credit": "HeraliZ, mods/crac/outils/tc_modele.py",
              "textures": {"0": texture, "particle": texture}, "elements": elements, "display": AFFICHAGE}
    img = Image.fromarray((np.clip(tex, 0, 1) * 255).astype(np.uint8), "RGBA")
    return modele, img


def controler(cubes):
    x0, z0, x1, z1 = BOITE
    for c in cubes:
        if c.rot:
            continue
        if c.a[0] < x0 - 1e-6 or c.b[0] > x1 + 1e-6 or c.a[2] < z0 - 1e-6 or c.b[2] > z1 + 1e-6 or c.b[1] > 32:
            raise SystemExit(f"pièce hors de la boîte d'adossement {BOITE} : {c.a} {c.b} ({c.mat})")


def construire():
    _, cubes = cubes_armoire()
    controler(cubes)
    return modele_minecraft(cubes, TEXTURE)


def java_ecran():
    e = ECRAN
    return f"""package fr.heraliz.crac.client.render;

/**
 * Surface de l'écran de l'armoire, modèle tourné vers le nord (px). ÉCRIT par mods/crac/outils/tc_modele.py
 * --installer : jamais à la main.
 */
final class EcranArmoireDonnees {{
    static final float X0 = {e["x0"]}F, X1 = {e["x1"]}F, Y0 = {e["y0"]}F, Y1 = {e["y1"]}F, Z = {e["z"]}F;

    private EcranArmoireDonnees() {{
    }}
}}
"""


def ecrire(chemin, donnees):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def installer():
    modele, img = construire()
    ecrire(ASSETS / "models/block/claim_block.json", modele)
    img.save(ASSETS / "textures/block/claim_block.png", optimize=True)
    ecrire(ASSETS / "models/block/claim_block_haut.json", {"credit": modele["credit"], "textures": {"particle": TEXTURE}})
    ecrire(ASSETS / "models/item/claim_block.json", {"parent": "crac:block/claim_block"})
    JAVA_ECRAN.write_text(java_ecran(), encoding="utf-8")
    import adossement
    adossement.armoire(modele)
    print(f"[tc_modele] {len(modele['elements'])} éléments installés dans {ASSETS}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", help="dossier où écrire le modèle, sa texture et un rendu")
    g.add_argument("--installer", action="store_true", help="écrit le modèle dans le mod")
    args = ap.parse_args()
    if args.installer:
        installer()
        return
    out = Path(args.apercu)
    out.mkdir(parents=True, exist_ok=True)
    modele, img = construire()
    (out / "claim_block.json").write_text(json.dumps(modele, indent=1), encoding="utf-8")
    img.save(out / "claim_block.png")
    os_, cubes = cubes_armoire()
    ex.D, ex.LARGEUR, ex.MARGE = 16, 1024, 2
    tex, lueur, _, _ = ex.construire(os_, cubes)
    ims = [ex.rendu(os_, cubes, tex, lueur, set(), v, taille=900) for v in cr.VUES.values()]
    ex.planche(ims, list(cr.VUES), out / "armoire_vues.png")
    print(f"[tc_modele] {len(modele['elements'])} éléments, texture {img.size[0]}×{img.size[1]} -> {out}")


if __name__ == "__main__":
    main()
