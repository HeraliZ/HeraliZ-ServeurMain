#!/usr/bin/env python3
"""
Modèle du recycleur du spawn (demande du propriétaire, 24/10 : « une presse ou un recycleur style Rust bien clean, 2 blocs
par 1 »). Machine industrielle propre, peinture verte et jaune à peine usée : trémie d'entrée à gauche (on y jette les
objets), moteur, pot d'échappement et volant d'inertie à droite, bac de sortie et volets en caoutchouc devant, pupitre
avec gros bouton rouge, voyants et levier.

Rendus validés par le propriétaire le 25/10 : --installer écrit le bloc dans le mod (HeraliZ Quêtes).
Réutilise le moteur de modules/bases/outils/explosifs.py (pièces, matières peintes au calcul, rendu par le calcul) et les
matières de coeur_tc_rendus.py. Rien n'est dessiné à la main : on modifie ce script.

    python modules/pnj/outils/recycleur_modele.py --apercu DOSSIER     rendus (planche de 4 vues), modèle et texture
    python modules/pnj/outils/recycleur_modele.py --installer          modèle, texture, états et objet dans le mod

Coordonnées en pixels de modèle (16 = 1 bloc) : x de 0 à 32 (2 blocs, ouest -> est), z de 0 à 16, face avant au nord
(z petit), hauteur 27 au plus. Pièces tournées : un seul axe, par pas de 22,5°, 45° au plus (règle des modèles de
Minecraft) ; pour un pavé à section carrée, 67,5° = -22,5° et 90° = 0°.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RACINE = Path(__file__).resolve().parents[1]
DEPOT = RACINE.parents[1]
sys.path.insert(0, str(DEPOT / "modules" / "bases" / "outils"))  # moteur partagé (explosifs, tc_modele)
import coeur_tc_rendus as cr  # noqa: E402  (matières de récupération et propres)
import explosifs as ex  # noqa: E402
from explosifs import Cube, Decal, Os, bruit, col, fbm, melange  # noqa: E402

TEXTURE = "heraliz_quetes:block/recycleur"
DENSITE = 4
LARGEUR = 512
LUMINEUX = {"lampe_verte", "lampe_ambre", "lampe_rouge"}


# ================================================================================================ matières
def peindre(mat, p, n, fu, fv, w, h, prm):
    sh = p.shape[:2]
    a = np.ones(sh)
    g = np.zeros(sh + (3,))
    e = ex.bords(fu, fv, w, h)
    arete = np.exp(-e / 0.10)
    gr = prm.get("graine", 0)
    bas = np.clip(1 - p[..., 1] / 6.0, 0, 1)                         # saleté qui remonte du sol
    if mat in ("peint_vert", "peint_vert_fonce", "peint_jaune"):
        base, ombre = {"peint_vert": (col(74, 102, 66), col(60, 86, 54)),
                       "peint_vert_fonce": (col(52, 72, 48), col(42, 60, 40)),
                       "peint_jaune": (col(222, 174, 40), col(196, 148, 30))}[mat]
        r = melange(base, ombre, fbm(p, 0.25, 3, 70 + gr) * 0.8)
        r = r * (0.97 + 0.05 * bruit(p, 7.0, 71 + gr))[..., None]     # grain de la peinture
        eclats = np.clip((fbm(p, 1.4, 3, 72 + gr) - 0.68) * 6, 0, 1) * arete
        r = melange(r, col(150, 152, 150), eclats)                     # arêtes un peu frottées : métal clair
        r = melange(r, col(58, 50, 40), bas * 0.35 * np.clip(fbm(p, 0.6, 3, 73 + gr) + 0.3, 0, 1))
        r = melange(r, r * 1.18, arete * 0.35)                          # arêtes qui accrochent la lumière
    elif mat == "danger":                                               # bande propre (celle de cr est écaillée)
        bande = ((p[..., 0] + p[..., 1] + p[..., 2]) % 2.4) < 1.2
        r = np.where(bande[..., None], col(222, 176, 36), col(28, 28, 28))
        r = melange(r, col(150, 150, 146), np.clip((fbm(p, 1.4, 3, 74) - 0.7) * 6, 0, 1) * arete)
    elif mat == "acier":
        brosse = bruit(p * np.array([1, 6.0, 1]), 2.5, 75 + gr)
        r = melange(col(88, 92, 96), col(124, 128, 132), brosse * 0.7 + 0.2 * (n[1] > 0.5))
        r = melange(r, col(176, 180, 184), arete * 0.6)
    elif mat == "acier_sombre":
        r = melange(col(40, 42, 46), col(56, 59, 64), bruit(p, 10, 76 + gr) * 0.6)
        r = melange(r, col(118, 122, 128), arete * 0.6)
    elif mat == "chrome":
        reflet = np.sin(p[..., 1] * 0.9 + p[..., 0] * 0.3) * 0.5 + 0.5
        r = melange(col(130, 134, 140), col(214, 218, 224), reflet)
        r = melange(r, col(240, 242, 246), arete * 0.5)
    elif mat == "suie":                                                  # bout du pot d'échappement
        r = melange(col(26, 24, 22), col(52, 48, 44), fbm(p, 1.5, 3, 77))
    elif mat == "interieur":
        prof = np.clip(fv, 0, 1) if prm.get("degrade") else np.zeros(sh)
        r = melange(col(26, 27, 28), col(8, 8, 9), prof * 0.8 + 0.2)
        r = r * (0.9 + 0.2 * bruit(p, 5, 78))[..., None]
    elif mat == "lames":                                                 # dents du broyeur, au fond de la trémie
        dents = (np.sin(p[..., 0] * 3.2) * 0.5 + 0.5) > 0.5
        r = np.where(dents[..., None], col(110, 112, 116), col(30, 31, 33))
        r = melange(r, col(170, 172, 176), arete * 0.4)
    elif mat == "grille":                                                # ouïes d'aération
        fente = (np.sin(p[..., 1] * 5.0) * 0.5 + 0.5) > 0.45
        r = np.where(fente[..., None], col(14, 14, 15), col(60, 84, 54))
    elif mat == "caoutchouc":
        r = melange(col(24, 24, 25), col(40, 40, 42), bruit(p, 9, 79 + gr))
        r = melange(r, col(66, 66, 68), arete * 0.4)
    elif mat == "bouton_rouge":
        c = np.exp(-((fu - 0.35) ** 2 + (fv - 0.35) ** 2) / 0.05)
        r = melange(col(170, 24, 20), col(255, 120, 110), c * 0.7)
    elif mat in LUMINEUX:
        teinte = {"lampe_verte": (col(40, 200, 70), col(190, 255, 200)),
                  "lampe_ambre": (col(220, 130, 20), col(255, 225, 150)),
                  "lampe_rouge": (col(200, 30, 24), col(255, 170, 160))}[mat]
        c = np.exp(-((fu - 0.5) ** 2 + (fv - 0.42) ** 2) / 0.12)
        r = melange(teinte[0], teinte[1], c)
        g = r * 0.95
    elif mat == "plaque":                                                # plaque du fabricant, rivetée
        r = melange(col(176, 178, 176), col(204, 206, 204), bruit(p, 4, 80))
        r = melange(r, col(96, 98, 98), arete * 0.7)
    else:
        return cr.peindre(mat, p, n, fu, fv, w, h, prm)
    return np.clip(r, 0, 1), a, np.clip(g, 0, 1)


def masque_recyclage(taille=256):
    """Pictogramme « recyclage » : trois flèches en triangle (dessiné au calcul, sans texte)."""
    im = Image.new("L", (taille, taille), 0)
    d = ImageDraw.Draw(im)
    c = taille / 2
    R = taille * 0.40
    pts = [(c + R * math.cos(math.radians(-90 + 120 * k)), c + R * math.sin(math.radians(-90 + 120 * k))) for k in range(3)]
    ep = taille * 0.11
    for k in range(3):
        a, b = pts[k], pts[(k + 1) % 3]
        ax, ay = a[0] + (b[0] - a[0]) * 0.16, a[1] + (b[1] - a[1]) * 0.16
        bx, by = a[0] + (b[0] - a[0]) * 0.70, a[1] + (b[1] - a[1]) * 0.70
        d.line([(ax, ay), (bx, by)], fill=255, width=int(ep))
        dx, dy = b[0] - a[0], b[1] - a[1]
        L = math.hypot(dx, dy)
        ux, uy = dx / L, dy / L
        px, py = -uy, ux
        t = (a[0] + dx * 0.86, a[1] + dy * 0.86)
        s = taille * 0.15
        d.polygon([t, (bx + px * s, by + py * s), (bx - px * s, by - py * s)], fill=255)
    return np.asarray(im, float) / 255


# ================================================================================================ modèle
def _pans(ray):
    """Demi-largeur d'un pan pour un cylindre à 16 pans de rayon ray."""
    return ray * math.tan(math.radians(180 / 16))


def cylindre_x(B, cx, cy, cz, ray, x0, x1, mat, prm=None):
    """Cylindre d'axe x à 16 pans : 8 plaques (ray x demi et demi x ray), tournées de 0, 22,5, 45 et -22,5°
    (67,5° = -22,5° de la plaque couchée)."""
    d = _pans(ray)
    for ang in (0, 22.5, 45, -22.5):
        for (hy, hz) in ((ray, d), (d, ray)):
            B((x0, cy - hy, cz - hz), (x1, cy + hy, cz + hz), mat, rot=(ang, 0, 0) if ang else None,
              pivot=(x0, cy, cz), prm=prm)


def cylindre_z(B, cx, cy, cz, ray, z0, z1, mat, prm=None):
    d = _pans(ray)
    for ang in (0, 22.5, 45, -22.5):
        for (hx, hy) in ((ray, d), (d, ray)):
            B((cx - hx, cy - hy, z0), (cx + hx, cy + hy, z1), mat, rot=(0, 0, ang) if ang else None,
              pivot=(cx, cy, z0), prm=prm)


def cylindre_y(B, cx, cz, ray, y0, y1, mat, prm=None):
    d = _pans(ray)
    for ang in (0, 22.5, 45, -22.5):
        for (hx, hz) in ((ray, d), (d, ray)):
            B((cx - hx, y0, cz - hz), (cx + hx, y1, cz + hz), mat, rot=(0, ang, 0) if ang else None,
              pivot=(cx, y0, cz), prm=prm)


def cubes_recycleur():
    os_ = [Os("r")]
    cubes = []

    def B(a, b, mat, **k):
        cubes.append(Cube("r", a, b, mat, **k))

    # ---- châssis : deux longerons et quatre pieds
    for z0 in (2.0, 12.0):
        B((0.6, 0, z0), (31.4, 1.6, z0 + 2.0), "acier_sombre")
    for x0 in (0.6, 29.4):
        for z0 in (1.6, 12.4):
            B((x0, 0, z0), (x0 + 2.0, 0.6, z0 + 2.0), "caoutchouc")
    # ---- caisson principal
    B((1.4, 1.6, 2.6), (30.6, 14.6, 13.8), "peint_vert")
    B((1.0, 14.6, 2.2), (31.0, 15.6, 14.2), "peint_vert_fonce")          # couvercle qui déborde
    for x0 in (1.0, 29.8):                                                # montants d'angle
        for z0 in (2.2, 13.0):
            B((x0, 1.6, z0), (x0 + 1.2, 14.6, z0 + 1.2), "acier")
    # bande danger en haut de la façade
    B((2.2, 12.6, 2.3), (29.8, 14.2, 2.6), "danger")
    # panneaux de façade en relief (portes de visite), poignées
    B((2.4, 2.6, 2.3), (15.4, 12.0, 2.6), "peint_vert", prm={"graine": 3})
    B((16.6, 7.4, 2.3), (29.6, 12.0, 2.6), "peint_vert", prm={"graine": 4})
    for (x0, y0) in ((13.6, 4.4), (13.6, 9.0)):
        B((x0, y0, 1.8), (x0 + 0.5, y0 + 1.6, 2.3), "chrome")
    # rivets des panneaux
    for x in (3.0, 14.8):
        for y in (3.2, 11.4):
            B((x - 0.25, y - 0.25, 2.1), (x + 0.25, y + 0.25, 2.3), "acier")
    for x in (17.2, 29.0):
        for y in (8.0, 11.4):
            B((x - 0.25, y - 0.25, 2.1), (x + 0.25, y + 0.25, 2.3), "acier")

    # ---- pupitre de commande (devant, à gauche)
    B((4.0, 5.0, 1.2), (11.6, 10.6, 2.3), "acier_sombre")
    B((4.4, 5.4, 1.0), (11.2, 10.2, 1.2), "plastique")
    cylindre_z(B, 6.4, 7.6, 1.0, 1.25, 0.2, 1.0, "acier")                 # collerette du bouton
    cylindre_z(B, 6.4, 7.6, 0.2, 1.0, -0.6, 0.2, "bouton_rouge")          # gros bouton rouge
    B((8.6, 8.6, 0.6), (9.6, 9.6, 1.0), "lampe_verte")
    B((10.0, 8.6, 0.6), (11.0, 9.6, 1.0), "lampe_ambre")
    # levier : socle, bras incliné, boule
    B((8.8, 5.8, 0.6), (10.8, 6.8, 1.0), "acier")
    B((9.5, 6.4, -0.6), (10.1, 9.4, 0.0), "chrome", rot=(-22.5, 0, 0), pivot=(9.8, 6.4, 0.6))
    B((9.3, 8.6, -2.0), (10.3, 9.6, -1.0), "bouton_rouge", rot=(-22.5, 0, 0), pivot=(9.8, 6.4, 0.6))
    # pictogramme de recyclage sur la porte de gauche
    picto = masque_recyclage()
    B((20.6, 7.6, 2.2), (25.4, 12.4, 2.3), "peint_vert", faces=("north",), prm={"graine": 6},
      decals=(Decal(picto, (25.4, 12.4, 2.2), (-4.8, 0, 0), (0, -4.8, 0), col(236, 240, 232), 1.0, normale=(0, 0, -1)),))

    # ---- sortie (devant, à droite) : fente à volets en caoutchouc et bac
    B((17.4, 3.2, 2.0), (28.8, 7.0, 2.6), "acier_sombre")                 # cadre de la fente
    B((18.0, 3.8, 1.9), (28.2, 6.4, 2.0), "interieur")                    # fond noir
    for k in range(6):
        x0 = 18.1 + k * 1.7
        B((x0, 4.1, 1.7), (x0 + 1.6, 6.4, 1.9), "caoutchouc", prm={"graine": k})
    B((17.6, 1.6, -1.6), (28.6, 2.4, 2.6), "acier")                       # fond du bac
    B((17.6, 2.4, -1.6), (28.6, 3.6, -1.0), "peint_jaune")                # rebord avant
    B((17.6, 2.4, -1.0), (18.2, 3.6, 2.0), "peint_jaune")
    B((28.0, 2.4, -1.0), (28.6, 3.6, 2.0), "peint_jaune")

    # ---- trémie d'entrée (dessus, à gauche) : caisson ouvert, lèvre jaune, broyeur au fond
    T0, T1, Z0, Z1, Y0, Y1 = 2.6, 15.4, 3.2, 13.2, 15.6, 21.4
    ep = 0.8
    B((T0, Y0, Z0), (T1, Y1, Z0 + ep), "peint_vert", prm={"graine": 7})   # paroi avant
    B((T0, Y0, Z1 - ep), (T1, Y1, Z1), "peint_vert", prm={"graine": 8})   # paroi arrière
    B((T0, Y0, Z0 + ep), (T0 + ep, Y1, Z1 - ep), "peint_vert", prm={"graine": 9})
    B((T1 - ep, Y0, Z0 + ep), (T1, Y1, Z1 - ep), "peint_vert", prm={"graine": 10})
    B((T0 + ep, Y0 + 0.4, Z0 + ep), (T1 - ep, Y0 + 0.8, Z1 - ep), "lames", faces=("up",))
    # plan incliné à l'intérieur (les objets glissent vers le broyeur)
    B((T0 + ep, Y0 + 0.8, Z1 - ep - 4.0), (T1 - ep, Y0 + 1.4, Z1 - ep), "acier", rot=(22.5, 0, 0),
      pivot=(T0 + ep, Y0 + 0.8, Z1 - ep - 4.0))
    for (a, b) in (((T0 + ep, Y0 + 0.8, Z0 + ep), (T1 - ep, Y1 - 0.2, Z0 + ep + 0.05)),
                   ((T0 + ep, Y0 + 0.8, Z1 - ep - 0.05), (T1 - ep, Y1 - 0.2, Z1 - ep)),
                   ((T0 + ep, Y0 + 0.8, Z0 + ep), (T0 + ep + 0.05, Y1 - 0.2, Z1 - ep)),
                   ((T1 - ep - 0.05, Y0 + 0.8, Z0 + ep), (T1 - ep, Y1 - 0.2, Z1 - ep))):
        B(a, b, "interieur", prm={"degrade": True})
    # lèvre jaune autour de l'ouverture, bande danger devant
    B((T0 - 0.4, Y1, Z0 - 0.4), (T1 + 0.4, Y1 + 0.8, Z0 + ep), "peint_jaune")
    B((T0 - 0.4, Y1, Z1 - ep), (T1 + 0.4, Y1 + 0.8, Z1 + 0.4), "peint_jaune")
    B((T0 - 0.4, Y1, Z0 + ep), (T0 + ep, Y1 + 0.8, Z1 - ep), "peint_jaune")
    B((T1 - ep, Y1, Z0 + ep), (T1 + 0.4, Y1 + 0.8, Z1 - ep), "peint_jaune")
    B((T0 + 0.6, 17.8, Z0 - 0.1), (T1 - 0.6, 19.2, Z0), "danger")

    # ---- moteur (dessus, à droite) : carter à ouïes, pot d'échappement, plaque du fabricant
    B((17.2, 15.6, 4.0), (29.8, 20.4, 12.8), "peint_vert_fonce")
    B((17.0, 20.4, 3.8), (30.0, 21.0, 13.0), "acier_sombre")
    B((18.0, 16.4, 3.8), (24.6, 19.6, 4.0), "grille")
    B((26.0, 16.6, 3.7), (29.0, 18.6, 3.8), "plaque")
    cylindre_y(B, 27.2, 10.6, 1.0, 21.0, 26.0, "chrome")                  # pot d'échappement
    cylindre_y(B, 27.2, 10.6, 1.25, 25.6, 26.6, "suie")
    B((26.0, 21.0, 9.4), (28.4, 21.6, 11.8), "acier")                     # bride
    # ---- volant d'inertie et courroie sur le flanc est
    cylindre_x(B, 0, 10.4, 8.2, 3.6, 31.0, 31.9, "acier_sombre")
    cylindre_x(B, 0, 10.4, 8.2, 1.2, 31.9, 32.0, "chrome")
    B((31.0, 4.6, 7.4), (31.9, 10.4, 9.0), "caoutchouc", prm={"graine": 11})   # courroie vers l'arbre du bas
    cylindre_x(B, 0, 4.6, 8.2, 1.4, 31.0, 31.8, "acier")
    # ---- câbles du pupitre vers le moteur, gaine sur le flanc ouest
    B((0.6, 3.0, 6.0), (1.0, 13.8, 6.6), "cable")
    B((0.6, 13.4, 6.0), (2.2, 13.8, 6.6), "cable")
    B((0.4, 4.0, 8.0), (1.0, 7.0, 11.0), "acier_sombre")                  # boîtier électrique
    B((0.2, 4.6, 8.6), (0.4, 6.4, 10.4), "danger")
    return os_, cubes


# ================================================================================================ rendus
VUES = {"trois quarts": (math.radians(215), math.radians(24)), "face": (math.radians(180), math.radians(8)),
        "autre côté": (math.radians(145), math.radians(24)), "plongée": (math.radians(200), math.radians(55))}


def controler(cubes):
    for c in cubes:
        for v in list(c.a) + list(c.b):
            if v < -16 or v > 32:
                raise SystemExit(f"pièce hors des limites de Minecraft : {c.a} {c.b} ({c.mat})")
        if c.rot:
            axes = [k for k, a in enumerate(c.rot) if a]
            if len(axes) != 1 or abs(c.rot[axes[0]]) % 22.5 or abs(c.rot[axes[0]]) > 45:
                raise SystemExit(f"rotation impossible dans un modèle de bloc : {c.rot}")


# ================================================================================================ installation
ASSETS = RACINE / "src" / "client" / "resources" / "assets" / "heraliz_quetes"
# en main et dans l'inventaire : la machine fait 2 blocs de large, réduite et recentrée
AFFICHAGE = {
    "gui": {"rotation": [30, 225, 0], "translation": [-2.6, -1.0, 0], "scale": [0.36, 0.36, 0.36]},
    "ground": {"rotation": [0, 0, 0], "translation": [-2, 1, 0], "scale": [0.18, 0.18, 0.18]},
    "fixed": {"rotation": [0, 180, 0], "translation": [2.6, -1.0, 0], "scale": [0.36, 0.36, 0.36]},
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [-1, 0.5, 0], "scale": [0.2, 0.2, 0.2]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [-1, 0.5, 0], "scale": [0.2, 0.2, 0.2]},
    "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [-2, -1, 0], "scale": [0.2, 0.2, 0.2]},
    "firstperson_lefthand": {"rotation": [0, 225, 0], "translation": [-2, -1, 0], "scale": [0.2, 0.2, 0.2]},
}


def ecrire(chemin, donnees):
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def installer(cubes):
    """Modèle de bloc Minecraft (moteur de tc_modele.py : faces cachées retirées, UV, pièces lumineuses), texture, états
    (4 orientations ; la case de droite est un bloc invisible, RecycleurPartie) et objet."""
    import tc_modele as tc
    tc.LUMINEUX, tc.AFFICHAGE, tc.DENSITE, tc.LARGEUR = LUMINEUX, AFFICHAGE, DENSITE, LARGEUR
    modele, img = tc.modele_minecraft(cubes, TEXTURE)
    modele["credit"] = "HeraliZ, modules/pnj/outils/recycleur_modele.py"
    ecrire(ASSETS / "models/block/recycleur.json", modele)
    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
    img.save(ASSETS / "textures/block/recycleur.png", optimize=True)
    ecrire(ASSETS / "models/block/recycleur_partie.json", {"credit": modele["credit"], "textures": {"particle": TEXTURE}})
    ecrire(ASSETS / "models/item/recycleur.json", {"parent": "heraliz_quetes:block/recycleur"})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}
    for nom in ("recycleur", "recycleur_partie"):
        ecrire(ASSETS / f"blockstates/{nom}.json",
               {"variants": {f"facing={f}": ({"model": f"heraliz_quetes:block/{nom}", "y": r} if r else {"model": f"heraliz_quetes:block/{nom}"})
                             for f, r in rot.items()}})
    print(f"[recycleur] {len(modele['elements'])} éléments installés dans {ASSETS}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", type=Path, help="dossier des rendus")
    g.add_argument("--installer", action="store_true", help="écrit le bloc dans le mod")
    args = ap.parse_args()
    ex.peindre = peindre
    os_, cubes = cubes_recycleur()
    controler(cubes)
    if args.installer:
        installer(cubes)
        return
    out = args.apercu
    out.mkdir(parents=True, exist_ok=True)
    ex.D, ex.LARGEUR, ex.MARGE = 16, 1024, 2
    tex, lueur, _, _ = ex.construire(os_, cubes)
    ims = [ex.rendu(os_, cubes, tex, lueur, set(), v, taille=1000) for v in VUES.values()]
    ex.planche(ims, list(VUES), out / "recycleur_vues.png")
    ims[0].save(out / "recycleur_trois_quarts.png")
    print(f"[recycleur] {len(cubes)} pièces -> {out}")


if __name__ == "__main__":
    main()
