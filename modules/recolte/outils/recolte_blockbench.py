#!/usr/bin/env python3
"""HeraliZ Récolte : la source des modèles du mod, en cubes façon Blockbench (choix du propriétaire du 15/11 : le moins
de lag possible). Gisements (pierre, métal, métal rare, 4 étapes chacun), arbres à abattre (bouleau, épicéa : souche,
tronc qui tombe, entaille en 4 états, feuillage), outils (hache, pioche, hache-pioche légendaire), icônes et matières
brutes.

    python modules/recolte/outils/recolte_blockbench.py --installer [--pack COPIE_DU_DEPOT] [--rendus DOSSIER]

écrit, sans rien dessiner à la main :
  - modules/recolte/blockbench/<modèle>.bbmodel : à ouvrir dans Blockbench (format « Generic Model », texture
    intégrée, groupes nommés), pour retoucher un modèle à la main si besoin ;
  - modules/recolte/src/client/resources/assets/heraliz_recolte/modeles/<modèle>.json : le même modèle « cuit » en
    quadrilatères (position, UV, normale) rangés par groupe, prêt à dessiner d'un seul envoi (le plus léger) ;
  - .../textures/entity/<modèle>.png (texture du modèle), .../textures/item/<objet>.png (icônes) ;
  - --rendus : images de contrôle (éclairage des entités de Minecraft 1.20.1, sol du pack du serveur).

Mesures : 1 m = 1 bloc = 16 unités de Blockbench ; le modèle est posé sur y = 0, centré en x et z.
"""
from __future__ import annotations

import argparse
import base64
import io
import json
import math
import uuid
from pathlib import Path

import numpy as np
from PIL import Image

import recolte_moteur as mo
import recolte_modeles as rm
import recolte_cubes as rc
from recolte_cubes import Cube, rot, segment
from recolte_moteur import fbm3, lisse, melange, perlin3, rgb

MODULE = Path(__file__).resolve().parents[1]
ASSETS = MODULE / "src" / "client" / "resources" / "assets" / "heraliz_recolte"
BLOCKBENCH = MODULE / "blockbench"
DENSITE_ROCHE = 100
DENSITE_ARBRE = 72


# ============================================================================================================ matières

def _bordure(m):
    return lambda p, n, ctxm: m(p, n, ctxm) * rc._bord(ctxm, len(p), 0.12)[:, None]


MATIERES = dict(rc.MATIERES)
for _k in ("pierre", "metal", "rare"):
    MATIERES[_k] = _bordure(rm.MATIERES[_k])


def mat_cristal_cube(p, n, ctxm):
    uv = ctxm.get("uv_local", np.zeros((len(p), 2)))
    v = 1 - uv[:, 1]
    c = melange(rgb("#2E3C50"), rgb("#6788AA"), lisse(0.0, 1.0, v))
    c = melange(c, rgb("#B5C7D9"), lisse(0.55, 0.95, v) * 0.5)
    c *= (0.9 + 0.2 * lisse(-0.5, 0.5, fbm3(p, 60.0, 2, 91)))[:, None]
    return c * rc._bord(ctxm, len(p), 0.4)[:, None]


def mat_bois_vif(p, n, ctxm):
    c = melange(rgb("#E3CFA6"), rgb("#C8AE82"), lisse(-0.4, 0.6, fbm3(p * np.array([30, 4, 30]), 1.0, 3, 251)))
    marques = np.abs(perlin3(p * np.array([3.0, 90.0, 3.0]), 252))
    return c * (0.85 + 0.2 * lisse(0.0, 0.3, marques))[:, None] * rc._bord(ctxm, len(p), 0.0)[:, None]


MATIERES.update({"cristal": mat_cristal_cube, "bois_vif": mat_bois_vif,
                 "ecorce_bouleau": _bordure(rm.MATIERES["ecorce_bouleau"]),
                 "ecorce_epicea": _bordure(rm.MATIERES["ecorce_epicea"]),
                 "bout_bouleau": rm.MATIERES["bout_bouleau"], "bout_epicea": rm.MATIERES["bout_epicea"]})


# ============================================================================================================ gisements

GISEMENTS = {
    # nom : (graine, demi-tailles (x, y, z) en m, matière)
    "pierre": (12, (0.95, 0.86, 0.85), "pierre"),
    "metal": (31, (0.9, 0.9, 0.8), "metal"),
    "rare": (57, (0.8, 0.98, 0.72), "rare"),
}
ETAPES = rm.ETAPES      # 1.0, 0.84, 0.68, 0.52


def _rot_libre(rng, inclinaison=22):
    return (rot((0, 1, 0), rng.uniform(0, 360)) @ rot((1, 0, 0), rng.uniform(-inclinaison, inclinaison))
            @ rot((0, 0, 1), rng.uniform(-inclinaison, inclinaison)))


def gisement_cubes(nom, etape):
    """Rocher en cubes, par couches arrondies (chaque couche : un cube et le même tourné de 45° : contour à 8 pans, l'astuce
    de Blockbench), de plus en plus petites vers le haut, avec des blocs penchés qui dépassent sur les côtés. À chaque
    étape, le haut part (couche par couche), une cassure fraîche reste dessus, et tout rétrécit."""
    graine, (hx, hy, hz), mat = GISEMENTS[nom]
    rng = np.random.default_rng(graine)
    k = ETAPES[etape]
    H = np.array([hx, hy, hz])
    couches = [  # (hauteur du centre, taille relative, inclinaison max)
        (0.2, (1.9, 0.6, 1.7), 4),
        (0.58, (1.55, 0.6, 1.35), 7),
        (0.95, (1.1, 0.5, 0.95), 11),
        (1.22, (0.62, 0.34, 0.55), 16),
    ]
    blocs = []          # (centre, taille, R, couche : 0 = reste toujours)
    lacet = rng.uniform(0, 90)
    decal = np.zeros(3)
    for i, (y, t, inc) in enumerate(couches):
        lacet += rng.uniform(12, 28)
        decal = decal + np.array([rng.uniform(-0.08, 0.08), 0, rng.uniform(-0.08, 0.08)]) * H
        R = rot((0, 1, 0), lacet) @ rot((1, 0, 0), rng.uniform(-inc, inc)) @ rot((0, 0, 1), rng.uniform(-inc, inc))
        c = decal + np.array([0, y * hy, 0])
        t = np.array(t) * H
        blocs.append((c, t, R, i))
        tw = np.array([min(t[0], t[2]) * 0.92, t[1] * 0.96, min(t[0], t[2]) * 0.92])
        blocs.append((c, tw, R @ rot((0, 1, 0), 45), i))
    # blocs penchés sur les côtés (partent avec les couches 1, 2, 3)
    for j in range(4):
        a = math.radians(j * 90 + rng.uniform(-30, 30))
        d = np.array([math.cos(a), 0, math.sin(a)])
        c = d * H * np.array([0.72, 0, 0.72]) + np.array([0, hy * rng.uniform(0.25, 0.55), 0])
        t = H * rng.uniform(0.45, 0.7, 3) * np.array([1.0, 0.85, 1.0])
        R = rot((0, 1, 0), math.degrees(-a) + rng.uniform(-20, 20)) @ rot((0, 0, 1), rng.uniform(-35, -15))
        blocs.append((c, t, R, 1 + j % 3 if j < 3 else 0))
    depart = etape          # couches 1..etape parties (du haut : couche 3, puis 2, puis 1)
    partis = {3, 2, 1} if depart >= 3 else ({3, 2} if depart == 2 else ({3} if depart == 1 else set()))
    cubes = []
    for (c, t, R, couche) in blocs:
        if couche in partis:
            continue
        cubes.append(Cube(c, t, R, mat, groupe="gisement", extra={"frais": 0.0}, densite=DENSITE_ROCHE))
    if partis:
        # cassure fraîche sur le dessus de ce qui reste
        reste = max(c for c in range(4) if c not in partis)
        y, t, _ = couches[reste]
        c = np.array([0, (y + t[1] * 0.5) * hy, 0])
        tt = np.array([t[0] * 0.78, 0.12, t[2] * 0.78]) * np.array([hx, 1, hz])
        cubes.append(Cube(c, tt, rot((0, 1, 0), rng.uniform(0, 90)) @ rot((1, 0, 0), rng.uniform(-6, 6)), mat,
                          groupe="gisement", extra={"frais": 1.0}, densite=DENSITE_ROCHE))
    # cailloux au pied (restent jusqu'au bout)
    for i in range(5):
        a = rng.uniform(0, 2 * math.pi)
        d = np.array([math.cos(a), 0, math.sin(a)])
        c = d * np.array([hx, 0, hz]) * rng.uniform(0.95, 1.25) + np.array([0, 0.04, 0])
        t = np.array([1, 0.7, 1]) * rng.uniform(0.12, 0.24)
        cubes.append(Cube(c, t, _rot_libre(rng, 30), mat, groupe="gisement", extra={"frais": 0.0}, densite=DENSITE_ROCHE))
    # cristaux du métal rare (ils cassent avec les étapes)
    if nom == "rare":
        rng2 = np.random.default_rng(graine + 7)
        grappes = [(np.array([0.2, 1.3, 0.1]), np.array([0.3, 1, 0.2])), (np.array([-0.45, 0.95, -0.25]), np.array([-0.7, 1, -0.3])),
                   (np.array([0.1, 0.85, 0.55]), np.array([0.1, 1, 0.8]))]
        for g, (base, dirg) in enumerate(grappes[: [3, 2, 1, 1][etape]]):
            base = base * np.array([hx, hy, hz]) * np.array([1, [1.0, 0.82, 0.7, 0.62][etape], 1])
            for j in range(4):
                d = dirg / np.linalg.norm(dirg) + rng2.normal(size=3) * 0.32
                d /= np.linalg.norm(d)
                L = rng2.uniform(0.22, 0.42) * (1 if j == 0 else 0.75)
                l = rng2.uniform(0.06, 0.09)
                R = rc.repere(d, (1, 0, 0)) @ rot((0, 1, 0), rng2.uniform(0, 90))
                c = base + d * L / 2 + rng2.normal(size=3) * 0.04
                cubes.append(Cube(c, (l, L, l), R, "cristal", groupe="cristaux", densite=DENSITE_ROCHE * 2))
                pointe = c + d * (L / 2)
                Rp = R @ rot((1, 0, 0), 45) @ rot((0, 0, 1), 45)
                cubes.append(Cube(pointe, (l * 0.72,) * 3, Rp, "cristal", groupe="cristaux", densite=DENSITE_ROCHE * 2))
    # mise à l'échelle de l'étape, puis enfoncé de quelques centimètres dans le sol
    for cb in cubes:
        cb.c = cb.c * k
        cb.s = cb.s * k
    bas = min(_coins(cb)[:, 1].min() for cb in cubes if cb.groupe == "gisement" and cb.s.min() > 0.2 * k)
    for cb in cubes:
        cb.c = cb.c - np.array([0, bas + 0.07 * k, 0])
    return cubes


def _coins(cb):
    s = cb.s / 2
    pts = np.array([[x, y, z] for x in (-1, 1) for y in (-1, 1) for z in (-1, 1)]) * s
    return pts @ cb.R.T + cb.c


# ============================================================================================================ arbres

HAUTEUR_COUPE = rm.HAUTEUR_COUPE      # 0,5 m : la souche reste, le reste tombe
NIVEAUX = {"bouleau": [-0.25, 0.0, 0.25, 0.5, 0.75, 1.1, 1.6, 2.2, 2.9, 3.7, 4.6, 5.5, 6.4, 7.3, 8.2, 9.2],
           "epicea": [-0.25, 0.0, 0.25, 0.5, 0.75, 1.1, 1.6, 2.3, 3.1, 4.0, 5.0, 6.0, 7.0, 8.0, 9.0, 10.0, 11.0, 12.0]}


def _rayon(espece, y):
    if espece == "bouleau":
        H = 9.2
        return 0.165 * (1 - 0.82 * min(max(y / H, 0), 1)) + 0.03 + 0.07 * math.exp(-max(y, 0) / 0.35)
    H = 12.0
    return 0.24 * (1 - 0.9 * min(max(y / H, 0), 1)) + 0.02 + 0.09 * math.exp(-max(y, 0) / 0.4)


def _plan(centre, largeur, hauteur, R, image, groupe="feuillage"):
    """Plan de feuillage (cube d'épaisseur nulle, comme dans Blockbench) : largeur en x local, hauteur en y local."""
    return Cube(centre, (largeur, hauteur, 0.0), R, "feuilles", groupe=groupe, image=image)


def _plan_couche(centre, longueur, largeur, R, image, groupe="feuillage"):
    """Plan couché (rameau d'épicéa) : longueur en x local, largeur en z local, face vers le haut."""
    return Cube(centre, (longueur, 0.0, largeur), R, "feuilles", groupe=groupe, image=image)


def arbre_cubes(espece):
    """Arbre en cubes. Groupes : « souche » (reste au sol), « tronc » (tombe, pivot au centre de la coupe), dedans
    « coupe_0 » à « coupe_3 » (le morceau de tronc au-dessus de la coupe, entaille de plus en plus profonde : un seul
    visible à la fois), « fut », « branches », « feuillage »."""
    rng = np.random.default_rng(4 if espece == "bouleau" else 8)
    H = 9.2 if espece == "bouleau" else 12.0
    spine = rm._spine_tronc(H, 4 if espece == "bouleau" else 8, (0.22, 0.12) if espece == "bouleau" else (0.06, 0.04))
    ecorce = "ecorce_" + espece
    bout = "bout_" + espece
    cubes = []
    niv = NIVEAUX[espece]
    hc = HAUTEUR_COUPE
    centre_coupe = rm._point_sur(spine, hc)
    extra_bout = {"centre": centre_coupe, "rayon": _rayon(espece, hc)}
    for i in range(len(niv) - 1):
        y0, y1 = niv[i], niv[i + 1]
        p0, p1 = rm._point_sur(spine, y0), rm._point_sur(spine, y1)
        w = 2 * _rayon(espece, (y0 + y1) / 2) * 0.9
        if y1 <= hc:
            groupe = "souche"
        elif y0 < hc + 0.3:
            groupe = "coupe"
        else:
            groupe = "fut"
        faces = {}
        if abs(y1 - hc) < 1e-6:
            faces["up"] = bout
        if abs(y0 - hc) < 1e-6:
            faces["down"] = bout
        if groupe != "coupe":
            for cb in segment(p0, p1, w, w, (1, 0, 0), ecorce, jumeau=True, deborde=0.004):
                cb.groupe, cb.faces, cb.extra, cb.densite = groupe, faces, (extra_bout if faces else {}), DENSITE_ARBRE
                cubes.append(cb)
            continue
        # morceau de la coupe : 4 états de l'entaille (0 = intact), côté +x (le joueur frappe de ce côté)
        for e in range(4):
            prof = [0.0, 0.22, 0.42, 0.62][e] * w
            for j, cb in enumerate(segment(p0, p1, w - prof, w, (1, 0, 0), ecorce, jumeau=True, deborde=0.004)):
                if j == 1:
                    cb.s = np.array([max(cb.s[0] - prof * 0.8, 0.02), cb.s[1], max(cb.s[2] - prof * 0.8, 0.02)])
                cb.c = cb.c - np.array([prof / 2, 0, 0])
                f = dict(faces)
                if e > 0:
                    f.update({"east": "bois_vif"} if j == 0 else {"east": "bois_vif", "south": "bois_vif"})
                cb.groupe, cb.faces, cb.densite = "coupe_%d" % e, f, DENSITE_ARBRE
                cb.extra = extra_bout if faces else {}
                cubes.append(cb)
    # racines
    for j in range(6 if espece == "bouleau" else 6):
        a = math.radians(j * 60 + rng.uniform(-20, 20))
        d = np.array([math.cos(a), 0, math.sin(a)])
        r0 = _rayon(espece, 0.2)
        p0 = rm._point_sur(spine, 0.12) + d * r0 * 0.4
        p1 = p0 + d * rng.uniform(0.6, 0.9) - np.array([0, 0.32, 0])
        for cb in segment(p0, p1, r0 * 0.55, r0 * 0.42, (0, 1, 0), ecorce, jumeau=False):
            cb.groupe, cb.densite = "souche", DENSITE_ARBRE
            cubes.append(cb)
    if espece == "bouleau":
        cubes += _feuillage_bouleau(rng, spine, H, ecorce)
    else:
        cubes += _feuillage_epicea(rng, spine, H, ecorce)
    return cubes, spine


def _feuillage_bouleau(rng, spine, H, ecorce):
    cubes = []
    centre_h = np.array([0.2, 6.2, 0.1])
    R_h = 2.6
    nb = 13
    for k in range(nb):
        y = 2.9 + (H - 3.5) * (k / nb) ** 0.85
        a = k * 2.399
        base = rm._point_sur(spine, y)
        L = (2.2 - 1.3 * (k / nb)) * rng.uniform(0.85, 1.1)
        d = np.array([math.cos(a), 0, math.sin(a)])
        milieu = base + d * L * 0.5 + np.array([0, L * 0.38, 0])
        bout_ = base + d * L + np.array([0, L * 0.25, 0])
        r0 = _rayon("bouleau", y) * 0.35
        for (q0, q1, r) in ((base, milieu, r0), (milieu, bout_, r0 * 0.6)):
            for cb in segment(q0, q1, r * 2, r * 2, (0, 1, 0), ecorce, jumeau=False):
                cb.groupe, cb.densite = "branches", DENSITE_ARBRE
                cubes.append(cb)
        for t in (0.55, 1.0):
            pt = base + d * L * t + np.array([0, L * (0.38 * t - 0.13 * t * t), 0]) + rng.normal(size=3) * 0.12
            cubes += _croix(rng, pt, rng.uniform(1.3, 1.75), centre_h, R_h)
    for k in range(26):
        u = rng.normal(size=3)
        u /= np.linalg.norm(u)
        u[1] = abs(u[1]) * 0.9 - 0.1
        r = R_h * rng.uniform(0.25, 0.9) ** 0.7
        cubes += _croix(rng, centre_h + u * np.array([r, r * 1.3, r]), rng.uniform(1.3, 1.8), centre_h, R_h)
    cubes += _croix(rng, rm._point_sur(spine, H - 0.4), 1.5, centre_h, R_h)
    return cubes


def _croix(rng, pt, taille, centre_h, R_h):
    """Deux plans de feuillage en croix (comme les plantes de Minecraft) ; plus sombres au coeur de la couronne."""
    dist = np.linalg.norm((pt - centre_h) / np.array([1, 1.3, 1])) / R_h
    sombre = dist < 0.55
    image = "grappe_sombre_%d" % rng.integers(0, 2) if sombre else "grappe_%d" % rng.integers(0, 4)
    lacet = rng.uniform(0, 180)
    tilt = rng.uniform(-12, 12)
    R0 = rot((0, 1, 0), lacet) @ rot((1, 0, 0), tilt)
    return [_plan(pt, taille, taille, R0, image), _plan(pt, taille, taille, R0 @ rot((0, 1, 0), 90), image)]


def _feuillage_epicea(rng, spine, H, ecorce):
    cubes = []
    y = 1.3
    while y < H - 0.5:
        th = (y - 1.3) / (H - 1.8)
        nbv = 6 if th < 0.55 else (5 if th < 0.8 else 4)
        decal = rng.uniform(0, 360)
        for j in range(nbv):
            a = decal + 360 * j / nbv + rng.uniform(-12, 12)
            L = (2.6 * (1 - th) ** 1.1 + 0.35) * rng.uniform(0.88, 1.1)
            chute = 26 * (1 - th) + 4
            base = rm._point_sur(spine, y)
            Rr = rot((0, 1, 0), -a)
            dirh = Rr @ np.array([1.0, 0, 0])
            for (incl, larg, dy, img) in ((chute, 0.74, 0.0, "rameau_%d" % rng.integers(0, 4)),
                                          (chute + 14, 0.56, -0.07, "rameau_sombre_%d" % rng.integers(0, 2)),
                                          (max(chute - 14, -4), 0.4, 0.06, "rameau_%d" % rng.integers(0, 4))):
                R = Rr @ rot((0, 0, 1), -incl)
                centre = base + (R @ np.array([L / 2, 0, 0])) + np.array([0, dy, 0])
                cubes.append(_plan_couche(centre, L, L * larg, R, img))
            if L > 1.0:
                for cb in segment(base, base + dirh * L * 0.5 - np.array([0, L * 0.12, 0]), 0.035, 0.035, (0, 1, 0), ecorce, jumeau=False):
                    cb.groupe, cb.densite = "branches", DENSITE_ARBRE
                    cubes.append(cb)
        y += rng.uniform(0.34, 0.41) * (1 if th < 0.75 else 0.85)
    pointe = rm._point_sur(spine, H - 0.6)
    for j in range(3):
        cubes.append(_plan(pointe + np.array([0, 0.6, 0]), 1.4, 0.7, rot((0, 1, 0), 60 * j) @ rot((0, 0, 1), 90), "rameau_1"))
    for j in range(8):
        yy = rng.uniform(0.75, 1.25)
        a = rng.uniform(0, 2 * math.pi)
        d = np.array([math.cos(a), 0, math.sin(a)])
        b = rm._point_sur(spine, yy)
        for cb in segment(b, b + d * rng.uniform(0.3, 0.6) - np.array([0, 0.08, 0]), 0.025, 0.025, (0, 1, 0), ecorce, jumeau=False):
            cb.groupe, cb.densite = "branches", DENSITE_ARBRE
            cubes.append(cb)
    return cubes


def images_feuillage(espece):
    if espece == "bouleau":
        imgs = {"grappe_%d" % i: rm._peindre_grappe_bouleau(300 + i, 128) for i in range(4)}
        for i in range(2):
            im = rm._peindre_grappe_bouleau(320 + i, 128).copy()
            im[..., :3] *= 0.62
            imgs["grappe_sombre_%d" % i] = im
    else:
        imgs = {"rameau_%d" % i: rm._peindre_rameau_epicea(400 + i, 192, 72) for i in range(4)}
        for i in range(2):
            im = rm._peindre_rameau_epicea(420 + i, 192, 72).copy()
            im[..., :3] *= 0.6
            imgs["rameau_sombre_%d" % i] = im
    return imgs


# ============================================================================================================ texture

def construire(cubes, images=None, largeur=1024):
    """Faces des cubes, texture (cuite depuis la position 3D + images peintes en bas) ; chaque face garde sa place
    (m.rect, en points de la texture) pour l'export."""
    ms = []
    for cb in cubes:
        for m in cb.morceaux():
            # face entièrement sous terre : jamais vue, pas de place dans la texture
            if cb.image is None and cb.groupe in ("gisement", "souche") and m.pos[:, 1].max() < -0.02:
                m.cachee = True
                continue
            ms.append(m)
    cuits = [m for m in ms if m.image is None]
    peints = [m for m in ms if m.image is not None]
    h_cuit = mo.ranger(cuits, largeur)
    tex_cuit, _ = mo.cuire_texture(cuits, MATIERES, largeur, h_cuit, dilatation=3)
    regions = {}
    bas = None
    if peints:
        noms = sorted({m.image for m in peints})
        x = y = rangee = 0
        places = {}
        for nm in noms:
            im = images[nm]
            h, w = im.shape[:2]
            if x + w > largeur:
                x, y, rangee = 0, y + rangee, 0
            places[nm] = (x, y, w, h)
            x += w
            rangee = max(rangee, h)
        hb = y + rangee
        bas = np.zeros((hb, largeur, 4))
        for nm, (px, py, w, h) in places.items():
            bas[py:py + h, px:px + w] = images[nm]
            regions[nm] = (px, h_cuit + py, w, h)
        tex = np.concatenate([tex_cuit, bas], 0)
    else:
        tex = tex_cuit
    H = tex.shape[0]
    for m in cuits:
        px, py, w, h = m.rect
        m.uv_atlas = np.stack([(px + m.uv[:, 0] * w) / largeur, (py + m.uv[:, 1] * h) / H], 1)
    for m in peints:
        px, py, w, h = regions[m.image]
        m.rect = (px, py, w, h)
        du, dv = 0.5 / largeur, 0.5 / H
        m.uv_atlas = np.stack([(px + m.uv[:, 0] * w) / largeur, (py + m.uv[:, 1] * h) / H], 1)
        m.uv_atlas = np.clip(m.uv_atlas, [px / largeur + du, py / H + dv], [(px + w) / largeur - du, (py + h) / H - dv])
    return ms, tex


# ============================================================================================================ exports

# sens « droite » et « bas » de la texture sur chaque face, vue de dehors (Blockbench, comme les modèles de Minecraft)
DROITE_BAS = {"north": ((-1, 0, 0), (0, -1, 0)), "south": ((1, 0, 0), (0, -1, 0)), "east": ((0, 0, -1), (0, -1, 0)),
              "west": ((0, 0, 1), (0, -1, 0)), "up": ((1, 0, 0), (0, 0, 1)), "down": ((1, 0, 0), (0, 0, -1))}


def euler_zyx(R):
    """Angles (degrés) tels que R = Rz . Ry . Rx (ordre « ZYX » de Blockbench, comme three.js)."""
    sy = -R[2, 0]
    sy = max(-1.0, min(1.0, sy))
    y = math.asin(sy)
    if abs(sy) < 0.9999999:
        x = math.atan2(R[2, 1], R[2, 2])
        z = math.atan2(R[1, 0], R[0, 0])
    else:
        x = 0.0
        z = math.atan2(-R[0, 1], R[1, 1])
    return [round(math.degrees(v), 4) for v in (x, y, z)]


def _uid():
    return str(uuid.uuid4())


def png_base64(img):
    b = io.BytesIO()
    Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(b, "PNG")
    return "data:image/png;base64," + base64.b64encode(b.getvalue()).decode()


def exporter_bbmodel(nom, cubes, ms, tex, chemin, pivots=None, caches=()):
    """Projet Blockbench (format libre « Generic Model »), texture intégrée. pivots : {groupe: (x, y, z) en m} ;
    caches : groupes masqués à l'ouverture (états de l'entaille)."""
    H, W = tex.shape[:2]
    par_cube = {}
    for m in ms:
        par_cube.setdefault(id(m.cube), []).append(m)
    elements = []
    groupes = {}
    for cb in cubes:
        faces = {}
        for m in par_cube.get(id(cb), []):
            px, py, w, h = m.rect
            droite, bas = (np.array(v, float) for v in DROITE_BAS[m.face])
            u1, u2 = (px, px + w) if np.dot(m.local_u, droite) > 0 else (px + w, px)
            v1, v2 = (py, py + h) if np.dot(-m.local_v, bas) > 0 else (py + h, py)
            faces[m.face] = {"uv": [round(u1, 4), round(v1, 4), round(u2, 4), round(v2, 4)], "texture": 0}
        for f in ("north", "east", "south", "west", "up", "down"):
            faces.setdefault(f, {"uv": [0, 0, 0, 0], "texture": None})
        c = cb.c * 16
        s = cb.s * 16
        el = {"name": cb.nom or cb.groupe, "box_uv": False, "rescale": False, "locked": False, "render_order": "default",
              "allow_mirror_modeling": True, "from": [round(v, 4) for v in (c - s / 2)], "to": [round(v, 4) for v in (c + s / 2)],
              "autouv": 0, "color": 0, "origin": [round(v, 4) for v in c], "rotation": euler_zyx(cb.R), "faces": faces,
              "type": "cube", "uuid": _uid()}
        elements.append(el)
        groupes.setdefault(cb.groupe, []).append(el["uuid"])
    pivots = pivots or {}
    # arbre des groupes (os du modèle)
    parent = {"coupe_0": "tronc", "coupe_1": "tronc", "coupe_2": "tronc", "coupe_3": "tronc", "fut": "tronc",
              "branches": "tronc", "feuillage": "tronc"}

    def noeud(nomg):
        enfants = [noeud(g) for g in sorted(groupes_tous) if parent.get(g) == nomg] + groupes.get(nomg, [])
        o = [round(v * 16, 4) for v in pivots.get(nomg, (0, 0, 0))]
        return {"name": nomg, "origin": o, "color": 0, "uuid": _uid(), "export": True, "mirror_uv": False,
                "isOpen": nomg in ("tronc",), "locked": False, "visibility": nomg not in caches, "autouv": 0,
                "children": enfants}

    groupes_tous = set(groupes) | {p for g in groupes for p in [parent.get(g)] if p}
    racines = [noeud(g) for g in sorted(groupes_tous) if g not in parent]
    projet = {
        "meta": {"format_version": "5.0", "model_format": "free", "box_uv": False},
        "name": nom, "model_identifier": nom, "visible_box": [4, 4, 0], "variable_placeholders": "",
        "variable_placeholder_buttons": [], "timeline_setups": [], "unhandled_root_fields": {},
        "resolution": {"width": W, "height": H},
        "elements": elements,
        "outliner": racines,
        "textures": [{"path": "", "name": nom + ".png", "folder": "", "namespace": "heraliz_recolte", "id": "0",
                      "group": "", "width": W, "height": H, "uv_width": W, "uv_height": H, "particle": False,
                      "use_as_default": True, "layers_enabled": False, "sync_to_project": "", "render_mode": "default",
                      "render_sides": "auto", "pbr_channel": "color", "frame_time": 1, "frame_order_type": "loop",
                      "frame_order": "", "frame_interpolate": False, "visible": True, "internal": True, "saved": True,
                      "uuid": _uid(), "source": png_base64(tex)}],
    }
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(projet, ensure_ascii=False), encoding="utf-8")


def exporter_quads(nom, ms, tex, chemin, pivots=None):
    """Modèle cuit pour le jeu : par groupe, une liste de quadrilatères [x y z u v nx ny nz] x 4 (mètres, UV 0..1,
    v = 0 en haut de l'image), et la découpe (feuillage) à dessiner sans tri. Une seule texture."""
    groupes = {}
    for m in ms:
        if getattr(m, "cachee", False):
            continue
        n = m.normales()[0]
        q = []
        for i in range(4):
            q += [round(float(v), 5) for v in m.pos[i]] + [round(float(v), 6) for v in m.uv_atlas[i]] + \
                 [round(float(v), 4) for v in n]
        # ordre des sommets : sens direct vu de dehors (comme les faces de Minecraft)
        if m.tri[0].tolist() != [0, 1, 2]:
            q = q[:8] + q[24:32] + q[16:24] + q[8:16]
        groupes.setdefault(m.groupe, []).append(q)
    data = {"texture": "heraliz_recolte:textures/entity/%s.png" % nom, "taille_texture": [tex.shape[1], tex.shape[0]],
            "unite": "metre", "pivots": {k: list(v) for k, v in (pivots or {}).items()},
            "groupes": {k: v for k, v in sorted(groupes.items())}}
    chemin.parent.mkdir(parents=True, exist_ok=True)
    chemin.write_text(json.dumps(data, separators=(",", ":")), encoding="utf-8")
    return sum(len(v) for v in groupes.values())


# ============================================================================================================ icônes

def icone(ms, tex, chemin, vue=(1.0, 0.75, 1.15), taille=64, marge=0.08, R=None):
    """Icône d'inventaire : le modèle vu de trois quarts, fond transparent, sans ombre ni sol."""
    pts = np.concatenate([m.pos for m in ms if getattr(m, "apercu", True)])
    M = np.eye(4)
    if R is not None:
        M[:3, :3] = R
        pts = pts @ R.T
    c = (pts.max(0) + pts.min(0)) / 2
    r = np.linalg.norm(pts - c, axis=1).max()
    ob = mo.objet_de([m for m in ms if getattr(m, "apercu", True)], tex, model=M)
    ob.alpha_min = 0.5
    d = np.array(vue, float)
    d /= np.linalg.norm(d)
    im = mo.rendre([ob], oeil=c + d * r * 4, cible=c, taille=(taille * 4, taille * 4), sur=1, transparent=True,
                   orthographique=2 * r * (1 + marge), brume=(1e5, 2e5))
    im = im.resize((taille, taille), Image.LANCZOS)
    a = np.array(im)
    a[..., 3] = np.where(a[..., 3] > 110, 255, 0)
    chemin.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(a, "RGBA").save(chemin)


def matieres_brutes():
    """Petits modèles des matières brutes (bûche, pierre brute, minerai de fer, minerai rare), pour leurs icônes."""
    out = {}
    out["buche"] = [Cube((0, 0, 0), (0.3, 0.75, 0.3), rot((0, 0, 1), 70), "ecorce_bouleau", "obj",
                         faces={"up": "bout_bouleau", "down": "bout_bouleau"}, extra={"centre": (0, 0, 0), "rayon": 0.18},
                         densite=60),
                    Cube((0, 0, 0), (0.25, 0.75, 0.25), rot((0, 0, 1), 70) @ rot((0, 1, 0), 45), "ecorce_bouleau", "obj",
                         faces={"up": "bout_bouleau", "down": "bout_bouleau"}, extra={"centre": (0, 0, 0), "rayon": 0.18},
                         densite=60)]
    for nom, mat in (("pierre_brute", "pierre"), ("minerai_fer", "metal"), ("minerai_rare", "rare")):
        rng = np.random.default_rng(len(nom))
        cs = [Cube((0, 0, 0), (0.42, 0.34, 0.38), _rot_libre(rng, 15), mat, "obj", extra={"frais": 0.4}, densite=80)]
        for k in range(3):
            cs.append(Cube(rng.normal(size=3) * 0.1, rng.uniform(0.18, 0.28, 3), _rot_libre(rng, 35), mat, "obj",
                           extra={"frais": 0.4}, densite=80))
        if nom == "minerai_rare":
            cs.append(Cube((0.05, 0.2, 0.0), (0.07, 0.26, 0.07), rot((0, 0, 1), -18), "cristal", "obj", densite=160))
            cs.append(Cube((-0.07, 0.17, 0.05), (0.06, 0.2, 0.06), rot((0, 0, 1), 22), "cristal", "obj", densite=160))
        out[nom] = cs
    return out


# ============================================================================================================ rendus

def objets(ms, tex, model=None, groupes=None):
    sel = [m for m in ms if getattr(m, "apercu", True) and not getattr(m, "cachee", False)
           and (groupes is None or m.groupe in groupes)]
    ob = mo.objet_de(sel, tex, model=model)
    ob.alpha_min = 0.5
    return ob


def rendus(dossier, modeles):
    from PIL import ImageDraw
    dossier.mkdir(parents=True, exist_ok=True)
    sol = rm.sol_herbe(0, 0, 22)
    # gisements : les 4 étapes de chacun
    for nom in ("pierre", "metal", "rare"):
        obs = [rm.sol_herbe(2, 0, 22)]
        for e in range(4):
            ms, tex = modeles["gisement_%s_%d" % (nom, e + 1)]
            M = np.eye(4)
            M[:3, :3] = rot((0, 1, 0), 35)
            M[0, 3] = e * 2.2 - 1.2
            obs.append(objets(ms, tex, M))
        obs.append(rm.mannequin(-3.1, 0.6, rot=0.6))
        im = mo.rendre(obs, oeil=(1.6, 2.4, 6.2), cible=(1.6, 0.4, 0), taille=(1600, 760), fov=46, brume=(14, 45))
        rm.titre(im, {"pierre": "Gisement de pierre", "metal": "Gisement de métal", "rare": "Gisement de métal rare"}[nom]
                 + " en cubes : les 4 étapes", "modèle Blockbench, à côté d'un joueur (1,8 m)").save(dossier / f"cubes_gisement_{nom}.png")
    # arbres
    for espece in ("bouleau", "epicea"):
        if "arbre_" + espece not in modeles:
            continue
        ms, tex = modeles["arbre_" + espece]
        visibles ={"souche", "coupe_0", "fut", "branches", "feuillage"}
        obs = [sol, objets(ms, tex, groupes=visibles), rm.mannequin(1.6, 1.4, rot=-2.3),
               rm.arbre_vanille(-6, -2, "birch" if espece == "bouleau" else "spruce", 6 if espece == "bouleau" else 7)]
        im = mo.rendre(obs, oeil=(5.8, 2.4, 10.0), cible=(-1.8, 4.4, 0), taille=(1300, 1000), fov=58, brume=(20, 55))
        rm.titre(im, ("Bouleau" if espece == "bouleau" else "Épicéa") + " à abattre, en cubes",
                 "modèle Blockbench ; à gauche un arbre en blocs de la carte, devant un joueur (1,8 m)").save(dossier / f"cubes_arbre_{espece}.png")
        # entaille (3e état) et souche
        im1 = mo.rendre([sol, objets(ms, tex, groupes={"souche", "coupe_3", "fut", "branches"})], oeil=(1.9, 1.0, 1.3),
                        cible=(0, 0.62, 0), taille=(800, 620), fov=50, brume=(20, 55))
        im2 = mo.rendre([sol, objets(ms, tex, groupes={"souche"})], oeil=(1.5, 1.2, 1.6), cible=(0, 0.3, 0),
                        taille=(800, 620), fov=50, brume=(20, 55))
        pl = Image.new("RGB", (1600, 620))
        pl.paste(im1, (0, 0))
        pl.paste(im2, (800, 0))
        rm.titre(pl, "Entaille (4e état) et souche", "le tronc tombe autour du centre de la coupe ; la souche reste").save(
            dossier / f"cubes_souche_{espece}.png")
    # chute du bouleau (3 images)
    if "arbre_bouleau" not in modeles:
        print("rendus de contrôle :", dossier)
        return
    ms, tex = modeles["arbre_bouleau"]
    tronc = {"coupe_3", "fut", "branches", "feuillage"}
    images = []
    for ang in (0, 35, 84):
        T = mo.pivot(mo.rotation((0, 0, 1), math.radians(ang)), (-0.2, HAUTEUR_COUPE, 0))
        im = mo.rendre([rm.sol_herbe(-3, 0, 26), objets(ms, tex, groupes={"souche"}), objets(ms, tex, T, groupes=tronc),
                        rm.mannequin(1.25, 0.15, rot=-math.pi / 2)], oeil=(-3.4, 2.6, 11.8), cible=(-3.4, 3.9, 0),
                       taille=(800, 540), fov=56, brume=(22, 60))
        images.append(im)
    pl = Image.new("RGB", (2400, 540))
    for i, im in enumerate(images):
        pl.paste(im, (i * 800, 0))
    rm.titre(pl, "Chute (bouleau en cubes)", "le groupe « tronc » tourne autour de la coupe").save(dossier / "cubes_chute.png")
    # outils et matières : icônes en planche
    noms = ["hache", "pioche", "hache_pioche", "buche", "pierre_brute", "minerai_fer", "minerai_rare"]
    pl = Image.new("RGBA", (7 * 160, 190), (40, 44, 52, 255))
    d = ImageDraw.Draw(pl)
    for i, n in enumerate(noms):
        if not (ASSETS / "textures" / "item" / (n + ".png")).exists():
            continue
        ic = Image.open(ASSETS / "textures" / "item" / (n + ".png")).resize((128, 128), Image.NEAREST)
        pl.alpha_composite(ic, (i * 160 + 16, 12))
        d.text((i * 160 + 16, 150), n, fill=(220, 224, 230, 255))
    pl.convert("RGB").save(dossier / "cubes_icones.png")
    print("rendus de contrôle :", dossier)


# ============================================================================================================ principal

def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--installer", action="store_true", help="écrit les modèles, textures et icônes dans le module")
    ap.add_argument("--rendus", type=Path, help="dossier des images de contrôle")
    ap.add_argument("--pack", type=Path, help="autre copie du dépôt dont on lit l'herbe du pack (rendus seulement)")
    ap.add_argument("--seulement", default="tout", help="gisements | arbres | outils | tout (essais rapides)")
    a = ap.parse_args()
    if a.pack:
        rm.PACK_CARTE = a.pack
    if not a.installer and not a.rendus:
        ap.error("--installer et/ou --rendus DOSSIER")
    modeles = {}
    bilan = []
    import time
    t0 = time.time()
    # gisements
    for nom in (("pierre", "metal", "rare") if a.seulement in ("gisements", "tout") else ()):
        for e in range(4):
            mid = "gisement_%s_%d" % (nom, e + 1)
            cubes = gisement_cubes(nom, e)
            ms, tex = construire(cubes, largeur=512)
            modeles[mid] = (ms, tex)
            if a.installer:
                mo.enregistre_png(tex, ASSETS / "textures" / "entity" / (mid + ".png"))
                nq = exporter_quads(mid, ms, tex, ASSETS / "modeles" / (mid + ".json"))
                exporter_bbmodel(mid, cubes, ms, tex, BLOCKBENCH / (mid + ".bbmodel"))
                bilan.append((mid, len(cubes), nq, tex.shape[1], tex.shape[0]))
    print("gisements :", round(time.time() - t0), "s")
    # arbres
    for espece in (("bouleau", "epicea") if a.seulement in ("arbres", "tout") else ()):
        mid = "arbre_" + espece
        cubes, spine = arbre_cubes(espece)
        ms, tex = construire(cubes, images_feuillage(espece), largeur=1024)
        modeles[mid] = (ms, tex)
        centre = rm._point_sur(spine, HAUTEUR_COUPE)
        pivots = {"tronc": tuple(round(float(v), 4) for v in centre)}
        if a.installer:
            mo.enregistre_png(tex, ASSETS / "textures" / "entity" / (mid + ".png"))
            nq = exporter_quads(mid, ms, tex, ASSETS / "modeles" / (mid + ".json"), pivots)
            exporter_bbmodel(mid, cubes, ms, tex, BLOCKBENCH / (mid + ".bbmodel"), pivots,
                             caches=("coupe_1", "coupe_2", "coupe_3"))
            bilan.append((mid, len(cubes), nq, tex.shape[1], tex.shape[0]))
    # outils
    noms_outils = {"hache": "hache", "pioche": "pioche", "legendaire": "hache_pioche"}
    print("arbres :", round(time.time() - t0), "s")
    for nom, mid in (noms_outils.items() if a.seulement in ("outils", "tout") else ()):
        cubes = rc.OUTILS[nom]()
        for cb in cubes:
            cb.groupe = "tete" if cb.c[1] > {"hache": 0.62, "pioche": 0.77, "legendaire": 0.69}[nom] and abs(cb.c[0]) + cb.c[1] > 0 else "manche"
        ms, tex = construire(cubes, largeur=512)
        modeles["outil_" + mid] = (ms, tex)
        if a.installer:
            mo.enregistre_png(tex, ASSETS / "textures" / "item" / "modele" / (mid + ".png"))
            nq = exporter_quads(mid, ms, tex, ASSETS / "modeles" / (mid + ".json"))
            exporter_bbmodel(mid, cubes, ms, tex, BLOCKBENCH / (mid + ".bbmodel"))
            icone(ms, tex, ASSETS / "textures" / "item" / (mid + ".png"), vue=(0.0, 0.0, 1.0),
                  R=rot((0, 0, 1), -45), taille=64, marge=0.02)
            bilan.append((mid, len(cubes), nq, tex.shape[1], tex.shape[0]))
    # matières brutes : icônes seulement
    if a.installer and a.seulement in ("outils", "tout"):
        for nom, cubes in matieres_brutes().items():
            ms, tex = construire(cubes, largeur=256)
            icone(ms, tex, ASSETS / "textures" / "item" / (nom + ".png"), taille=64, marge=0.1)
        print("modèle                  cubes  quads  texture")
        for (mid, nc, nq, w, h) in bilan:
            print(f"  {mid:22s} {nc:5d}  {nq:5d}  {w} x {h}")
    if a.rendus:
        rendus(a.rendus, modeles)


if __name__ == "__main__":
    main()
