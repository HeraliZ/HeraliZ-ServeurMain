#!/usr/bin/env python3
"""
Modèle du recycleur de la carte (décision du propriétaire du 31/10, après refus de la version rouillée façon Rust) :
la MÊME machine que le recycleur du spawn (recycleur_modele.py : mêmes pièces, même silhouette, 2 x 1 bloc), en
« version de terrain » pour qu'on les distingue d'un coup d'oeil, propre et belle quand même :
  - peinture sable délavée par le soleil (au lieu du vert), coulures de rouille légères sous les rivets et les arêtes,
    arêtes un peu frottées ; rebords orange sécurité passé (au lieu du jaune) ;
  - bandes d'avertissement rouges et blanches (au lieu de jaunes et noires), pictogramme peint au pochoir sombre ;
  - en plus : gyrophare orange sur le moteur, antenne fouet, jerrican sanglé à l'arrière, anneaux de levage aux coins
    du couvercle, plaque de terrain « R-02 ».
Deux états (propriété « marche » du bloc) : à l'arrêt (voyants et gyrophare éteints) et en marche (voyants vert et
ambre et gyrophare allumés). Une seule texture pour les deux modèles (atlas commun).

Rien n'est dessiné à la main : on modifie ce script. Moteur et matières de recycleur_modele.py (explosifs.py,
coeur_tc_rendus.py) ; écriture du modèle de bloc comme tc_modele.py (faces cachées retirées, pièces lumineuses).

    python modules/pnj/outils/recycleur_carte_modele.py --apercu DOSSIER   rendus de contrôle (rien n'est écrit dans le mod)
        recycleur_carte_vues.png             4 vues, à l'arrêt
        recycleur_carte_arret_marche.png     à l'arrêt / en marche, trois quarts et face
        recycleur_carte_echelle.png          à côté d'un joueur (1,8 bloc), sol quadrillé d'un bloc
        comparaison_spawn_carte.png          spawn · joueur · carte, côte à côte (trois quarts et face)
        recycleur_carte_densite_jeu.png      rendu avec la texture installée (densité du jeu), arrêt et marche
    python modules/pnj/outils/recycleur_carte_modele.py --installer        modèles, texture, états et objet dans le mod

Coordonnées en pixels de modèle (16 = 1 bloc) : x de 0 à 32 (2 blocs, ouest -> est), z de 0 à 16, face avant au nord
(z petit). Pièces tournées : un seul axe, par pas de 22,5°, 45° au plus (règle des modèles de Minecraft) ; les limites
-16 à 32 sont contrôlées.
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recycleur_modele as rm  # noqa: E402  (le recycleur du spawn : pièces, matières, cylindres)
import explosifs as ex  # noqa: E402  (chemin ajouté par recycleur_modele)
import tc_modele as tc  # noqa: E402
from explosifs import Cube, Decal, Os, bruit, col, fbm, melange  # noqa: E402

TEXTURE = "heraliz_quetes:block/recycleur_carte"
DENSITE, LARGEUR = rm.DENSITE, rm.LARGEUR
# pièces qui brillent (forge_data du modèle) : seulement dans le modèle « en marche »
LUMINEUX = {"lampe_verte", "lampe_ambre", "gyro_on"}

# matières du spawn -> matières de terrain (mêmes pièces)
TERRAIN = {"peint_vert": "sable", "peint_vert_fonce": "sable_fonce", "peint_jaune": "orange", "danger": "danger_rouge",
           "grille": "grille_sable"}
# voyants du spawn : éteints à l'arrêt, allumés en marche
VOYANTS = {"lampe_verte": "verre_vert", "lampe_ambre": "verre_ambre"}


# ================================================================================================ matières
def peindre(mat, p, n, fu, fv, w, h, prm):
    sh = p.shape[:2]
    a = np.ones(sh)
    g = np.zeros(sh + (3,))
    e = ex.bords(fu, fv, w, h)
    arete = np.exp(-e / 0.10)
    gr = prm.get("graine", 0)
    bas = np.clip(1 - p[..., 1] / 6.0, 0, 1)                         # poussière qui remonte du sol
    rouille = melange(col(128, 72, 36), col(92, 50, 26), fbm(p, 0.6, 3, 210 + gr))
    if mat in ("sable", "sable_fonce", "orange"):
        base, ombre = {"sable": (col(186, 166, 122), col(160, 140, 100)),
                       "sable_fonce": (col(146, 128, 92), col(124, 108, 76)),
                       "orange": (col(214, 112, 38), col(186, 92, 30))}[mat]
        r = melange(base, ombre, fbm(p, 0.25, 3, 200 + gr) * 0.8)
        r = melange(r, r * 1.10 + 0.04, np.clip(p[..., 1] / 24.0, 0, 1) * 0.5)   # délavé par le soleil vers le haut
        r = r * (0.97 + 0.05 * bruit(p, 7.0, 201 + gr))[..., None]                 # grain de la peinture
        # coulures de rouille : traînées verticales, rares, plus marquées sous les arêtes hautes
        coulures = np.clip(bruit(p * np.array([3.2, 0.14, 3.2]), 1.0, 202 + gr) - 0.56, 0, 1) * 2.8
        coulures *= np.clip(fbm(p, 0.35, 3, 203 + gr) - 0.30, 0, 1) * 2.4
        r = melange(r, rouille, np.clip(coulures, 0, 1) * (0.55 if mat != "orange" else 0.35))
        # arêtes frottées : sous-couche grise, rouille par endroits
        eclats = np.clip((fbm(p, 1.4, 3, 204 + gr) - 0.62) * 6, 0, 1) * arete
        r = melange(r, col(120, 120, 116), eclats)
        r = melange(r, rouille, eclats * np.clip((fbm(p, 2.0, 2, 205 + gr) - 0.5) * 4, 0, 1))
        r = melange(r, col(110, 96, 72), bas * 0.35 * np.clip(fbm(p, 0.6, 3, 206 + gr) + 0.3, 0, 1))
        r = melange(r, r * 1.15, arete * 0.3)
    elif mat == "danger_rouge":                                         # bandes rouges et blanches, un peu passées
        bande = ((p[..., 0] + p[..., 1] + p[..., 2]) % 2.4) < 1.2
        r = np.where(bande[..., None], col(188, 40, 34), col(226, 222, 212))
        r = melange(r, col(150, 144, 136), np.clip((fbm(p, 1.4, 3, 207) - 0.66) * 6, 0, 1) * arete)
        r = r * (0.95 + 0.06 * bruit(p, 6.0, 208))[..., None]
    elif mat == "grille_sable":                                         # ouïes d'aération, cadre sable
        fente = (np.sin(p[..., 1] * 5.0) * 0.5 + 0.5) > 0.45
        r = np.where(fente[..., None], col(16, 15, 14), col(150, 132, 96))
    elif mat in ("verre_vert", "verre_ambre"):                          # voyant éteint : verre teinté, reflet
        teinte = col(30, 72, 40) if mat == "verre_vert" else col(96, 60, 20)
        c = np.exp(-((fu - 0.35) ** 2 + (fv - 0.3) ** 2) / 0.04)
        r = melange(teinte, teinte * 1.8 + 0.08, c * 0.6)
    elif mat in ("gyro_off", "gyro_on"):                                # dôme du gyrophare (verre orange strié)
        stries = (np.sin((p[..., 0] + p[..., 2]) * 5.0) * 0.5 + 0.5) * 0.25
        if mat == "gyro_off":
            r = melange(col(150, 72, 18), col(196, 112, 44), stries + np.clip(p[..., 1] - 22.4, 0, 1) * 0.3)
        else:
            c = np.exp(-((fv - 0.45) ** 2) / 0.10)
            r = melange(col(255, 136, 30), col(255, 228, 150), c * 0.8 + stries)
            g = r * 0.95
    elif mat == "jerrican_kaki":                                        # jerrican militaire, croix embouties
        r = melange(col(96, 98, 64), col(78, 80, 52), bruit(p, 3, 209))
        croix = np.minimum(np.abs(fu - fv), np.abs(fu + fv - 1)) < 0.06
        if abs(n[0]) > 0.5:
            r = np.where(croix[..., None], r * 0.75, r)
        r = melange(r, col(140, 136, 110), arete * 0.35)
    elif mat == "sangle":
        tissage = (np.sin(p[..., 1] * 9.0 + p[..., 0] * 9.0) * 0.5 + 0.5) * 0.25
        r = melange(col(44, 46, 38), col(70, 72, 60), tissage + bruit(p, 6, 211) * 0.2)
    elif mat == "plaque_terrain":                                       # plaque rivetée, numéro au pochoir
        r = melange(col(196, 190, 172), col(214, 208, 190), bruit(p, 4, 212))
        r = melange(r, col(110, 104, 92), arete * 0.7)
    elif mat == "vide":                                                 # cadrage des rendus seulement
        r = np.zeros(sh + (3,))
        a = np.zeros(sh)
    # ---- joueur (échelle) et sol : rendus seulement
    elif mat == "peau":
        r = melange(col(196, 150, 118), col(176, 130, 100), bruit(p, 4, 155))
    elif mat == "cheveux":
        r = melange(col(52, 38, 28), col(70, 52, 38), bruit(p, 6, 156))
    elif mat == "veste":
        r = melange(col(78, 86, 58), col(62, 70, 46), fbm(p, 0.8, 3, 157))
        r = melange(r, col(40, 44, 30), arete * 0.5)
    elif mat == "jean":
        r = melange(col(52, 66, 96), col(40, 52, 78), bruit(p * np.array([1, 4, 1.0]), 2, 158))
    elif mat == "bottes":
        r = melange(col(48, 38, 30), col(64, 52, 40), bruit(p, 5, 159))
    elif mat == "sac":
        r = melange(col(96, 80, 56), col(78, 64, 44), fbm(p, 0.9, 3, 160))
        r = melange(r, col(46, 38, 28), arete * 0.6)
    elif mat == "yeux":
        r = np.broadcast_to(col(30, 34, 44), sh + (3,)).copy()
    elif mat == "sol":                                                  # dalles d'un bloc (échelle)
        r = melange(col(86, 84, 76), col(104, 102, 92), fbm(p, 0.4, 3, 161))
        r = r * (0.9 + 0.14 * bruit(p, 6, 162))[..., None]
        r = melange(r, col(44, 44, 40), np.exp(-e / 0.25) * 0.8)
    else:
        return rm.peindre(mat, p, n, fu, fv, w, h, prm)
    return np.clip(r, 0, 1), a, np.clip(g, 0, 1)


# ================================================================================================ modèle
def _copie(c, mat=None, prm=None, decals=None):
    return Cube(c.os, c.a, c.b, mat or c.mat, faces=c.faces, rot=c.rot, pivot=c.pivot,
                prm=c.prm if prm is None else prm, decals=c.decals if decals is None else decals)


def cubes_carte():
    """Toutes les pièces, les deux états compris : prm « etat » = « arret » ou « marche » pour les pièces d'un seul état
    (voyants, gyrophare), absent pour les autres. Le modèle de chaque état garde les pièces de son état."""
    _, spawn = rm.cubes_recycleur()
    cubes = []

    def B(a, b, mat, etat=None, **k):
        prm = dict(k.pop("prm", None) or {})
        if etat:
            prm["etat"] = etat
        cubes.append(Cube("r", a, b, mat, prm=prm, **k))

    # ---- la machine du spawn, repeinte (mêmes pièces, mêmes places)
    for c in spawn:
        if c.mat in VOYANTS:
            cubes.append(_copie(c, VOYANTS[c.mat], prm=dict(c.prm, etat="arret")))
            cubes.append(_copie(c, c.mat, prm=dict(c.prm, etat="marche")))
            continue
        decals = c.decals
        if decals:                                                      # pictogramme : pochoir sombre au lieu de blanc
            decals = tuple(Decal(d.m, d.o, d.a, d.b, col(52, 46, 36), 0.92, d.lueur, d.n) for d in decals)
        cubes.append(_copie(c, TERRAIN.get(c.mat, c.mat), decals=decals))

    # ---- gyrophare sur le carter du moteur (avant gauche ; le pot d'échappement est à l'arrière droite)
    gx, gz = 20.4, 7.4
    rm.cylindre_y(B, gx, gz, 1.5, 21.0, 21.7, "acier_sombre")
    for etat, mat in (("arret", "gyro_off"), ("marche", "gyro_on")):
        rm.cylindre_y(lambda a, b, m, **k: B(a, b, m, etat=etat, **k), gx, gz, 1.1, 21.7, 23.6, mat)
    B((gx - 0.6, 23.6, gz - 0.6), (gx + 0.6, 23.9, gz + 0.6), "acier_sombre")
    for dx, dz in ((-1.35, -0.15), (1.15, -0.15), (-0.15, -1.35), (-0.15, 1.15)):     # cage de protection
        B((gx + dx, 21.7, gz + dz), (gx + dx + 0.3, 23.7, gz + dz + 0.3), "acier")
    # ---- antenne fouet à l'arrière gauche du carter, embase et ressort
    B((17.6, 21.0, 11.4), (18.8, 21.6, 12.6), "acier_sombre")
    B((17.95, 21.6, 11.75), (18.45, 22.6, 12.25), "caoutchouc")
    B((18.1, 22.6, 11.9), (18.3, 29.0, 12.1), "acier_sombre")
    B((18.0, 29.0, 11.8), (18.4, 29.4, 12.2), "caoutchouc")
    # ---- anneaux de levage aux quatre coins du couvercle
    for x0 in (1.4, 29.2):
        for z0 in (2.6, 12.6):
            B((x0, 15.6, z0 + 0.3), (x0 + 1.4, 16.8, z0 + 0.7), "acier")
            B((x0 + 0.4, 16.0, z0 + 0.3), (x0 + 1.0, 16.4, z0 + 0.7), "interieur")
    # ---- jerrican sanglé à l'arrière (face sud), à droite vu de dos
    jx0, jx1, jy0, jy1, jz0, jz1 = 3.4, 10.2, 1.6, 11.6, 14.2, 16.0
    B((jx0, jy0, jz0), (jx1, jy1, jz1), "jerrican_kaki")
    B((jx0 + 1.6, jy1, jz0 + 0.5), (jx0 + 2.2, jy1 + 1.0, jz1 - 0.5), "jerrican_kaki", prm={"graine": 1})   # poignées
    B((jx0 + 3.4, jy1, jz0 + 0.5), (jx0 + 4.0, jy1 + 1.0, jz1 - 0.5), "jerrican_kaki", prm={"graine": 2})
    B((jx0 + 1.6, jy1 + 1.0, jz0 + 0.5), (jx0 + 4.0, jy1 + 1.4, jz1 - 0.5), "jerrican_kaki", prm={"graine": 3})
    B((jx1 - 1.6, jy1, jz0 + 0.6), (jx1 - 0.4, jy1 + 0.8, jz0 + 1.6), "acier_sombre")                    # bouchon
    for y in (3.4, 8.6):                                                # sangles autour du jerrican
        B((jx0 - 0.2, y, jz0), (jx1 + 0.2, y + 0.8, jz1 + 0.15), "sangle")
    # ---- plaque de terrain rivetée sur le flanc ouest (pochoir « R-02 »)
    texte = ex.masque_texte(["R-02"], haut=64)
    B((0.2, 9.0, 3.6), (0.4, 12.2, 9.2), "plaque_terrain", faces=("west", "north", "south", "up", "down"),
      decals=(Decal(texte, (0.2, 11.8, 3.8), (0, 0, 5.2), (0, -2.4, 0), col(40, 36, 30), 1.0, normale=(-1, 0, 0)),))
    return cubes


def etat(cubes, nom):
    """Pièces du modèle d'un état (« arret » ou « marche »)."""
    return [c for c in cubes if c.prm.get("etat", nom) == nom]


def controler(cubes):
    for c in cubes:
        if c.mat == "vide":
            continue
        for v in list(c.a) + list(c.b):
            if v < -16 or v > 32:
                raise SystemExit(f"pièce hors des limites de Minecraft : {c.a} {c.b} ({c.mat})")
        if c.rot:
            axes = [k for k, a in enumerate(c.rot) if a]
            if len(axes) != 1 or abs(c.rot[axes[0]]) % 22.5 or abs(c.rot[axes[0]]) > 45:
                raise SystemExit(f"rotation impossible dans un modèle de bloc : {c.rot}")


# ================================================================================================ installation
ASSETS = rm.ASSETS


def atlas(cubes):
    """Atlas commun aux deux états (densité du jeu) : UV posées sur chaque pièce, image carrée."""
    ex.D, ex.LARGEUR, ex.MARGE = DENSITE, LARGEUR, 1
    tex, lueur, tw, th = ex.construire([Os("r")], cubes)
    if tex.shape[0] < tex.shape[1]:
        pad = np.zeros((tex.shape[1] - tex.shape[0], tex.shape[1], 4))
        tex = np.concatenate([tex, pad])
        lueur = np.concatenate([lueur, np.zeros((pad.shape[0], lueur.shape[1], 4))])
        th = tw
    return tex, lueur, tw, th


def elements(sous, tw, th):
    """Éléments d'un modèle de bloc pour ces pièces (même écriture que tc_modele.modele_minecraft, UV de l'atlas commun)."""
    tc.LUMINEUX = LUMINEUX
    cachees = tc.faces_cachees(sous)
    out = []
    for i, c in enumerate(sous):
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
            k = next(k for k, a in enumerate(c.rot) if a)
            el["rotation"] = {"origin": [round(x, 4) for x in c.pivot], "axis": "xyz"[k], "angle": c.rot[k]}
        if c.mat in LUMINEUX:
            el["forge_data"] = {"block_light": 15, "sky_light": 15}
            el["shade"] = False
        out.append(el)
    return out


def installer(cubes):
    """Deux modèles de bloc (arrêt, marche) sur une seule texture, états (4 orientations x marche ; la case de droite
    est un bloc invisible, RecycleurPartie) et objet (modèle à l'arrêt)."""
    from PIL import Image
    tex, _, tw, th = atlas(cubes)
    credit = "HeraliZ, modules/pnj/outils/recycleur_carte_modele.py"
    for nom, sous in (("recycleur_carte", etat(cubes, "arret")), ("recycleur_carte_marche", etat(cubes, "marche"))):
        modele = {"credit": credit, "textures": {"0": TEXTURE, "particle": TEXTURE}, "elements": elements(sous, tw, th),
                  "display": rm.AFFICHAGE}
        rm.ecrire(ASSETS / f"models/block/{nom}.json", modele)
        print(f"[recycleur carte] {nom} : {len(modele['elements'])} éléments")
    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
    Image.fromarray((np.clip(tex, 0, 1) * 255).astype(np.uint8), "RGBA").save(ASSETS / "textures/block/recycleur_carte.png",
                                                                             optimize=True)
    rm.ecrire(ASSETS / "models/block/recycleur_carte_partie.json", {"credit": credit, "textures": {"particle": TEXTURE}})
    rm.ecrire(ASSETS / "models/item/recycleur_carte.json", {"parent": "heraliz_quetes:block/recycleur_carte"})
    rot = {"north": 0, "east": 90, "south": 180, "west": 270}

    def variante(modele, r):
        return {"model": modele, "y": r} if r else {"model": modele}

    variantes = {}
    for f, r in rot.items():
        variantes[f"facing={f},marche=false"] = variante("heraliz_quetes:block/recycleur_carte", r)
        variantes[f"facing={f},marche=true"] = variante("heraliz_quetes:block/recycleur_carte_marche", r)
    rm.ecrire(ASSETS / "blockstates/recycleur_carte.json", {"variants": variantes})
    rm.ecrire(ASSETS / "blockstates/recycleur_carte_partie.json",
              {"variants": {f"facing={f}": variante("heraliz_quetes:block/recycleur_carte_partie", r) for f, r in rot.items()}})
    print(f"[recycleur carte] texture {tex.shape[1]} x {tex.shape[0]}, installé dans {ASSETS}")


# ================================================================================================ rendus
VUES = dict(rm.VUES, dos=(math.radians(25), math.radians(24)))


def joueur(B, px, pz):
    """Joueur debout (modèle de Minecraft à l'échelle 0,9375 : 1,875 bloc), tourné vers le nord, sac sur le dos."""
    s = 0.9375
    L, H, P = 4 * s, 12 * s, 4 * s
    B((px - L, 0, pz - P / 2), (px, H, pz + P / 2), "jean")
    B((px, 0, pz - P / 2), (px + L, H, pz + P / 2), "jean", prm={"graine": 1})
    B((px - L - 0.05, 0, pz - P / 2 - 0.05), (px + L + 0.05, 2.4, pz + P / 2 + 0.05), "bottes")
    B((px - L, H, pz - P / 2), (px + L, 2 * H, pz + P / 2), "veste", prm={"graine": 2})
    for sgn in (-1, 1):
        x0, x1 = (px - 2 * L, px - L) if sgn < 0 else (px + L, px + 2 * L)
        B((x0, H, pz - P / 2), (x1, 2 * H, pz + P / 2), "veste", prm={"graine": 3})
        B((x0 - 0.02, H, pz - P / 2 - 0.02), (x1 + 0.02, H + 1.8, pz + P / 2 + 0.02), "peau")
    T = 8 * s
    B((px - T / 2, 2 * H, pz - T / 2), (px + T / 2, 2 * H + T, pz + T / 2), "peau")
    B((px - T / 2 - 0.05, 2 * H + T - 1.6, pz - T / 2 - 0.05), (px + T / 2 + 0.05, 2 * H + T + 0.05, pz + T / 2 + 0.05), "cheveux")
    for dx in (-1.9, 1.1):
        B((px + dx, 2 * H + 3.4, pz - T / 2 - 0.1), (px + dx + 0.9, 2 * H + 4.3, pz - T / 2), "yeux")
    B((px - 3.0, H + 1.2, pz + P / 2), (px + 3.0, 2 * H - 0.6, pz + P / 2 + 2.6), "sac")


def sol(B, x0, x1, z0, z1):
    """Dalles d'un bloc (16 px), dessus seulement : le quadrillage donne l'échelle."""
    for x in range(int(x0), int(x1), 16):
        for z in range(int(z0), int(z1), 16):
            B((x, -0.6, z), (x + 16, 0, z + 16), "sol", faces=("up",))


def decale(cubes, dx, dz):
    """Copie déplacée (scènes de comparaison) ; les décalcomanies suivent."""
    out = []
    o = np.array([dx, 0, dz], float)
    for c in cubes:
        d = tuple(Decal(k.m, k.o + o, k.a, k.b, k.c, k.op, k.lueur, k.n) for k in c.decals)
        out.append(Cube(c.os, c.a + o, c.b + o, c.mat, faces=c.faces, rot=c.rot,
                        pivot=None if c.pivot is None else tuple(np.array(c.pivot, float) + o), prm=c.prm, decals=d))
    return out


def scene(*groupes):
    """Cubes réunis ; un groupe peut être une fonction (B) -> None qui pose ses pièces."""
    out = []
    for g in groupes:
        if callable(g):
            g(lambda a, b, mat, **k: out.append(Cube("r", a, b, mat, **k)))
        else:
            out.extend(g)
    return out


def rendre(cubes, vue, taille=1000):
    ex.D, ex.LARGEUR, ex.MARGE = 16, 2048, 2
    os_ = [Os("r")]
    tex, lueur, _, _ = ex.construire(os_, cubes)
    return ex.rendu(os_, cubes, tex, lueur, set(), vue, taille=taille)


def planche_recadree(images, titres, fichier, marge=14):
    """Planche de rendus recadrés sur ce qui est dessiné : même cadre pour toutes les images, donc même échelle."""
    fond = np.array([28, 31, 33])
    boites = []
    for im in images:
        a = np.asarray(im.convert("RGB"), int)
        ys, xs = np.nonzero(np.abs(a - fond).sum(-1) > 12)
        boites.append((xs.min(), ys.min(), xs.max(), ys.max()))
    x0 = max(0, min(b[0] for b in boites) - marge)
    y0 = max(0, min(b[1] for b in boites) - marge)
    x1 = min(images[0].size[0], max(b[2] for b in boites) + marge)
    y1 = min(images[0].size[1], max(b[3] for b in boites) + marge)
    ex.planche([im.crop((x0, y0, x1, y1)) for im in images], titres, fichier)


def apercu(cubes, out):
    out.mkdir(parents=True, exist_ok=True)
    arret, marche = etat(cubes, "arret"), etat(cubes, "marche")
    tq, face = VUES["trois quarts"], VUES["face"]

    vues = {k: VUES[k] for k in ("trois quarts", "face", "autre côté", "dos")}
    ims = [rendre(arret, v) for v in vues.values()]
    ex.planche(ims, ["à l'arrêt · " + t for t in vues], out / "recycleur_carte_vues.png")

    planche_recadree([rendre(arret, tq), rendre(marche, tq), rendre(arret, face), rendre(marche, face)],
                     ["à l'arrêt", "en marche", "à l'arrêt · face", "en marche · face"], out / "recycleur_carte_arret_marche.png")

    sc = scene(arret, lambda B: joueur(B, -16.0, 4.0), lambda B: sol(B, -32, 48, -16, 32))
    planche_recadree([rendre(sc, tq, 1400), rendre(sc, face, 1400), rendre(sc, VUES["dos"], 1400)],
                     ["à côté d'un joueur · trois quarts", "face (1 dalle = 1 bloc)", "dos (jerrican sanglé)"],
                     out / "recycleur_carte_echelle.png")

    # vu de face, l'est (+x) est à gauche : le spawn (+x) à gauche, la carte à droite, le joueur au milieu
    _, spawn = rm.cubes_recycleur()
    sc = scene(decale(spawn, 12.0, 0), decale(marche, -44.0, 0), lambda B: joueur(B, 0.0, 4.0), lambda B: sol(B, -48, 48, -16, 32))
    planche_recadree([rendre(sc, tq, 1600), rendre(sc, face, 1600)],
                     ["spawn · joueur · carte (en marche)", "face : spawn · joueur · carte"], out / "comparaison_spawn_carte.png")

    # avec la texture du jeu (densité installée) : ce que verra le joueur
    tex, lueur, _, _ = atlas(cubes)
    ims = [ex.rendu([Os("r")], sous, tex, lueur, set(), tq, taille=1000) for sous in (arret, marche)]
    planche_recadree(ims, ["densité du jeu · à l'arrêt", "densité du jeu · en marche"], out / "recycleur_carte_densite_jeu.png")
    print(f"[recycleur carte] {len(arret)} pièces à l'arrêt, {len(marche)} en marche -> {out}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", type=Path, help="dossier des rendus (hors du dépôt)")
    g.add_argument("--installer", action="store_true", help="écrit le bloc dans le mod")
    args = ap.parse_args()
    ex.peindre = peindre
    cubes = cubes_carte()
    controler(cubes)
    if args.installer:
        installer(cubes)
    else:
        apercu(cubes, args.apercu)


if __name__ == "__main__":
    main()
