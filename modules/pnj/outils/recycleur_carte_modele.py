#!/usr/bin/env python3
"""
Modèle du recycleur de la carte (demande du propriétaire du 31/10, point 8) : un second recycleur, posé par le staff dans
les zones de loot, nettement différent de celui du spawn (recycleur_modele.py, vert et jaune, propre). Celui-ci est une
machine de terrain bricolée, façon Rust : caisson en tôle rouillée rapiécée (plaques soudées, tôle galvanisée de
travers, porte de visite arrachée qui pend), fût bleu coupé en guise de trémie (cerclages, deux barres de protection,
broyeur au fond), vieux moteur rouge sur un bâti soudé avec volant d'inertie à l'avant et pot d'échappement qui traverse
un auvent en tôle ondulée, baladeuse pendue sous l'auvent, batterie de voiture et câbles, jerrican, caisse en bois de
sortie remplie de ferraille, le tout sur une palette. Pictogramme de recyclage à la bombe, jaune.

Deux états : arrêté (baladeuse et voyants éteints) et en marche (baladeuse et voyant vert allumés, braise au bout du pot
d'échappement ; fumée et étincelles dessinées sur les rendus : en jeu, ce seront des particules, pas le modèle).

RENDUS À VALIDER PAR LE PROPRIÉTAIRE AVANT TOUTE INSTALLATION (règle du dépôt) : ce script n'écrit rien dans le mod.
Réutilise le moteur de modules/bases/outils/explosifs.py et les matières de récupération de coeur_tc_rendus.py, et
celles du recycleur du spawn (recycleur_modele.py) pour la comparaison. Rien n'est dessiné à la main : on modifie ce
script.

    python modules/pnj/outils/recycleur_carte_modele.py --apercu DOSSIER
        recycleur_carte_vues.png         4 vues, arrêté
        recycleur_carte_arret_marche.png arrêté / en marche, trois quarts (même cadrage)
        recycleur_carte_arret_marche_face.png   la même chose, de face
        recycleur_carte_echelle.png      à côté d'un joueur (1,8 bloc), sol quadrillé d'un bloc
        comparaison_spawn_carte.png      les deux recycleurs côte à côte, joueur au milieu
        recycleur_carte_trois_quarts.png grand rendu, en marche

Coordonnées en pixels de modèle (16 = 1 bloc) : x de 0 à 32 (2 blocs, ouest -> est), z de 0 à 16, face avant au nord
(z petit), hauteur 29 au plus (moins de 2 blocs). Pièces tournées : un seul axe, par pas de 22,5°, 45° au plus (règle
des modèles de Minecraft) ; les limites -16 à 32 sont contrôlées.
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import recycleur_modele as rm  # noqa: E402  (recycleur du spawn : matières, cylindres, modèle pour la comparaison)
import explosifs as ex  # noqa: E402  (chemin ajouté par recycleur_modele)
from explosifs import Cube, Decal, Os, bruit, col, fbm, melange  # noqa: E402

# en marche : pièces qui brillent (forge_data du modèle de bloc, comme le recycleur du spawn)
LUMINEUX = {"ampoule_on", "voyant_vert", "braise", "etincelle"}


# ================================================================================================ matières
def peindre(mat, p, n, fu, fv, w, h, prm):
    sh = p.shape[:2]
    a = np.ones(sh)
    g = np.zeros(sh + (3,))
    e = ex.bords(fu, fv, w, h)
    arete = np.exp(-e / 0.12)
    gr = prm.get("graine", 0)
    rouille = melange(col(112, 58, 28), col(72, 38, 20), fbm(p, 0.5, 4, 140 + gr))
    if mat in ("fut_bleu", "fut_cercle"):                               # vieux fût bleu, peinture passée, rouille qui perce
        base = col(44, 78, 122) if mat == "fut_bleu" else col(34, 60, 96)
        r = melange(base, base * 0.78, fbm(p, 0.4, 3, 141 + gr) * 0.9)
        perce = np.clip((fbm(p, 0.9, 4, 142 + gr) - 0.56) * 5, 0, 1)
        r = melange(r, rouille, perce)
        coulures = np.clip(bruit(p * np.array([3.0, 0.12, 3.0]), 1.0, 143 + gr) - 0.5, 0, 1) * 2
        r = melange(r, col(70, 40, 22), coulures * 0.5)
        r = melange(r, col(150, 156, 160), arete * 0.25)
    elif mat == "tole_galva":                                           # tôle galvanisée : fleurs de zinc, oxyde blanc
        fleurs = bruit(p, 2.2, 144 + gr)
        r = melange(col(118, 124, 126), col(156, 162, 164), fleurs)
        r = melange(r, col(196, 198, 194), np.clip((fbm(p, 0.8, 3, 145 + gr) - 0.6) * 4, 0, 1) * 0.6)
        r = melange(r, rouille, np.clip((fbm(p, 1.2, 3, 146 + gr) - 0.7) * 4, 0, 1) * arete.clip(0.3, 1))
        r = melange(r, col(70, 72, 72), arete * 0.4)
    elif mat == "peint_rouge":                                          # moteur peint en rouge, très passé
        r = melange(col(138, 40, 30), col(104, 32, 26), fbm(p, 0.6, 3, 147 + gr))
        r = melange(r, col(160, 110, 96), np.clip(p[..., 1] / 30.0, 0, 1) * 0.25)   # décoloré par le soleil en haut
        eclats = np.clip((fbm(p, 1.3, 4, 148 + gr) - 0.6) * 5, 0, 1)
        r = melange(r, rouille, np.maximum(eclats, arete * 0.7))
        r = melange(r, col(30, 26, 24), np.clip((fbm(p, 0.5, 3, 149 + gr) - 0.62) * 4, 0, 1) * 0.7)  # cambouis
    elif mat == "jerrican":                                             # jerrican rouge, croix embouties
        r = melange(col(150, 34, 26), col(118, 26, 20), bruit(p, 3, 150))
        croix = np.minimum(np.abs(fu - fv), np.abs(fu + fv - 1)) < 0.06
        if abs(n[2]) > 0.5:
            r = np.where(croix[..., None], r * 0.72, r)
        r = melange(r, col(70, 40, 30), arete * 0.5)
    elif mat == "beton":
        r = melange(col(116, 114, 108), col(140, 138, 132), bruit(p, 5, 151))
        r = r * (0.88 + 0.16 * bruit(p, 14, 152))[..., None]
    elif mat == "circuit":                                              # carte électronique (ferraille de la caisse)
        pistes = (np.sin(p[..., 0] * 4.0) * np.sin(p[..., 2] * 3.0 + p[..., 1] * 3.0)) > 0.55
        r = np.where(pistes[..., None], col(190, 160, 70), col(36, 96, 52))
    elif mat in ("ampoule", "ampoule_on"):                              # baladeuse : verre éteint ou allumé
        c = np.exp(-((fu - 0.5) ** 2 + (fv - 0.45) ** 2) / 0.10)
        if mat == "ampoule":
            r = melange(col(150, 148, 136), col(214, 212, 200), c * 0.6)
        else:
            r = melange(col(255, 196, 96), col(255, 246, 214), c)
            g = r * 0.98
    elif mat in ("voyant_vert", "voyant_vert_off", "voyant_rouge_off"):
        c = np.exp(-((fu - 0.5) ** 2 + (fv - 0.45) ** 2) / 0.12)
        if mat == "voyant_vert":
            r = melange(col(40, 210, 80), col(200, 255, 210), c)
            g = r * 0.95
        else:
            teinte = col(26, 70, 36) if mat == "voyant_vert_off" else col(82, 22, 20)
            r = melange(teinte, teinte * 1.6, c * 0.5)
    elif mat == "braise":                                               # bout du pot d'échappement, chaud
        r = melange(col(200, 70, 20), col(255, 190, 90), fbm(p, 1.2, 3, 153))
        g = r * 0.9
    elif mat == "etincelle":
        r = np.broadcast_to(col(255, 214, 120), sh + (3,)).copy()
        g = r
    elif mat == "fumee":
        r = melange(col(92, 92, 94), col(150, 150, 152), fbm(p, 0.7, 3, 154 + gr))
    elif mat == "vide":                                                 # invisible (cadrage des rendus seulement)
        r = np.zeros(sh + (3,))
        a = np.zeros(sh)
    # ---- joueur (échelle) et sol
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
    elif mat == "sol":                                                  # sol quadrillé d'un bloc (échelle)
        r = melange(col(86, 84, 76), col(104, 102, 92), fbm(p, 0.4, 3, 161))
        r = r * (0.9 + 0.14 * bruit(p, 6, 162))[..., None]
        r = melange(r, col(44, 44, 40), np.exp(-e / 0.25) * 0.8)
    else:
        return rm.peindre(mat, p, n, fu, fv, w, h, prm)
    return np.clip(r, 0, 1), a, np.clip(g, 0, 1)


def masque_bombe(taille=256):
    """Pictogramme de recyclage du spawn, repris « à la bombe » : bords flous, grain, petites coulures."""
    m = rm.masque_recyclage(taille)
    im = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(taille / 90))
    m = np.asarray(im, float) / 255
    rng = np.random.default_rng(31)
    grain = rng.random(m.shape)
    m = np.clip(m * (0.75 + 0.35 * grain), 0, 1)
    coul = np.zeros_like(m)
    for x in rng.integers(int(taille * 0.2), int(taille * 0.8), 6):
        peint = np.nonzero(m[:, x] > 0.5)[0]
        if len(peint):                                                  # coulure sous le bas de la peinture
            bas = int(peint.max())
            coul[bas:min(taille, bas + int(taille * rng.uniform(0.06, 0.16))), max(0, x - 1):x + 2] = 0.8
    return np.maximum(m, coul)


# ================================================================================================ outils de modélisation
def anneau_y(B, cx, cz, ray, y0, y1, ep, mat, prm=None):
    """Paroi d'un fût (axe y) ouverte en haut : 16 panneaux tangents (un seul axe de rotation, pas de 22,5°)."""
    demi = ray * math.tan(math.radians(180 / 16)) + 0.05
    for k in range(16):
        theta = k * 22.5
        base = int(round(theta / 90.0)) * 90                           # côté le plus proche : 0, 90, 180, 270 (360)
        phi = theta - base                                               # -45 à 45
        base %= 360
        # panneau posé sur le côté « base » puis tourné de phi autour de l'axe y passant par le centre
        if base == 0:        # côté sud (+z)
            a, b = (cx - demi, y0, cz + ray - ep), (cx + demi, y1, cz + ray)
        elif base == 180:    # côté nord (-z)
            a, b = (cx - demi, y0, cz - ray), (cx + demi, y1, cz - ray + ep)
        elif base == 90:     # côté est (+x)
            a, b = (cx + ray - ep, y0, cz - demi), (cx + ray, y1, cz + demi)
        else:                # côté ouest (-x)
            a, b = (cx - ray, y0, cz - demi), (cx - ray + ep, y1, cz + demi)
        B(a, b, mat, rot=(0, phi, 0) if phi else None, pivot=(cx, y0, cz), prm=dict(prm or {}, graine=k))


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


# ================================================================================================ modèle
def cubes_recycleur_carte(marche=False, cadre=False):
    cubes = []

    def B(a, b, mat, **k):
        cubes.append(Cube("r", a, b, mat, **k))

    # ---- palette en bois (une latte cassée)
    for z0 in (1.0, 7.0, 13.0):
        B((0.4, 0, z0), (31.6, 1.4, z0 + 2.0), "bois", prm={"graine": int(z0)})
    for k in range(9):
        x0 = 0.6 + k * 3.45
        z1 = 9.2 if k == 7 else 15.4
        B((x0, 1.4, 0.6), (x0 + 2.6, 2.2, z1), "bois", prm={"graine": 20 + k})
    B((0.6 + 7 * 3.45, 1.4, 10.4), (0.6 + 7 * 3.45 + 2.6, 1.9, 15.4), "bois", rot=(0, 22.5, 0),
      pivot=(0.6 + 7 * 3.45, 1.4, 12.0), prm={"graine": 29})          # morceau de latte détaché, de travers

    # ---- caisson de broyage : tôle rouillée, cornières, couvercle en plaque soudée
    X0, X1, Z0, Z1, Y0, Y1 = 1.6, 20.4, 5.0, 14.2, 2.2, 14.2
    B((X0, Y0, Z0), (X1, Y1, Z1), "tole_rouille", prm={"graine": 1})
    for x0 in (X0 - 0.4, X1 - 0.6):
        for z0 in (Z0 - 0.4, Z1 - 0.6):
            B((x0, Y0, z0), (x0 + 1.0, Y1 + 0.4, z0 + 1.0), "acier_brut", prm={"graine": int(x0 + z0)})
    B((X0 - 0.4, Y1, Z0 - 0.4), (X1 + 0.4, Y1 + 0.8, Z1 + 0.4), "plaque_soudee", prm={"graine": 2})
    # façade : porte en tôle peinte (vieille peinture verte), pictogramme à la bombe, rustines
    porte = Decal(masque_bombe(), (9.6, 13.0, Z0 - 0.3), (-6.4, 0, 0), (0, -6.4, 0), col(232, 196, 46), 0.95,
                  normale=(0, 0, -1))
    B((2.4, 3.4, Z0 - 0.3), (10.4, 13.4, Z0), "tole_peinte", prm={"graine": 3}, decals=(porte,))
    B((10.9, 9.8, Z0 - 0.3), (19.6, 13.6, Z0), "plaque_soudee", prm={"graine": 4})
    B((3.0, 10.8, Z0 - 0.6), (6.6, 13.8, Z0 - 0.3), "tole_galva", rot=(0, 0, 22.5), pivot=(4.8, 12.3, Z0 - 0.45),
      prm={"graine": 5})                                                # rustine de travers
    for (x, y) in ((3.0, 4.0), (9.8, 4.0), (9.8, 12.8), (11.5, 13.0), (19.0, 13.0), (19.0, 10.4)):
        B((x - 0.3, y - 0.3, Z0 - 0.55), (x + 0.3, y + 0.3, Z0 - 0.3), "acier_brut")
    B((9.0, 7.0, Z0 - 1.1), (9.5, 9.4, Z0 - 0.3), "acier_brut")       # poignée en fer à béton
    # fente de sortie, goulotte en tôle galvanisée qui descend vers la caisse
    B((11.4, 6.0, Z0 - 0.4), (19.2, 9.6, Z0), "acier_brut", prm={"graine": 6})
    B((12.0, 6.6, Z0 - 0.45), (18.6, 9.0, Z0 - 0.4), "interieur")
    B((12.2, 5.6, Z0 - 4.2), (18.4, 6.2, Z0), "tole_galva", rot=(-22.5, 0, 0), pivot=(12.2, 6.2, Z0), prm={"graine": 7})
    # flanc ouest : porte de visite arrachée qui pend, trou noir derrière
    B((X0 - 0.05, 4.4, 7.4), (X0, 11.0, 12.2), "interieur", faces=("west",))
    B((X0 - 0.5, 3.8, 7.2), (X0 - 0.1, 11.6, 12.4), "tole_galva", rot=(0, 0, -22.5), pivot=(X0 - 0.1, 11.6, 9.8),
      prm={"graine": 8})
    B((X0 - 0.6, 11.0, 7.6), (X0 - 0.1, 11.8, 8.4), "acier_brut")      # charnière restante

    # ---- trémie : fût bleu coupé, cerclages, barres de protection, broyeur au fond
    cx, cz, R = 7.6, 9.6, 4.5
    anneau_y(B, cx, cz, R, Y1 + 0.8, 23.8, 0.5, "fut_bleu")
    anneau_y(B, cx, cz, R - 0.5, Y1 + 0.8, 23.6, 0.3, "interieur", prm={"degrade": True})
    for y in (17.4, 21.0):
        anneau_y(B, cx, cz, R + 0.25, y, y + 0.7, 0.3, "fut_cercle")
    B((cx - 3.2, Y1 + 0.8, cz - 3.2), (cx + 3.2, Y1 + 1.3, cz + 3.2), "lames", faces=("up",))
    for dz in (-1.8, 1.8):
        B((cx - R - 0.6, 23.8, cz + dz - 0.3), (cx + R + 0.6, 24.4, cz + dz + 0.3), "acier_brut")

    # ---- jerrican sur le couvercle, tuyau vers le moteur
    B((13.6, Y1 + 0.8, 11.0), (19.2, 22.4, 13.6), "jerrican")
    for x in (14.6, 16.0, 17.4):
        B((x, 22.4, 11.9), (x + 0.6, 23.4, 12.7), "jerrican")
    B((14.4, 23.0, 11.9), (18.6, 23.6, 12.7), "jerrican")
    B((18.4, 22.4, 11.2), (19.4, 23.8, 12.2), "acier_brut")            # bec
    B((19.4, 16.0, 11.6), (22.6, 16.5, 12.1), "cable")                  # tuyau d'essence

    # ---- bâti du moteur (soudé), moteur rouge, volant d'inertie à l'avant, arbre vers le caisson
    for x0 in (21.2, 30.2):
        for z0 in (3.0, 12.6):
            B((x0, Y0, z0), (x0 + 0.8, 7.4, z0 + 0.8), "acier_brut", prm={"graine": int(x0 + z0)})
    B((21.0, 7.4, 2.8), (31.2, 8.2, 13.6), "plaque_soudee", prm={"graine": 9})
    B((22.0, 8.2, 4.4), (29.6, 14.6, 12.4), "peint_rouge", prm={"graine": 1})
    B((22.6, 14.6, 5.4), (29.0, 16.2, 11.4), "acier_brut", prm={"graine": 10})
    for k in range(5):                                                   # ailettes de refroidissement
        x0 = 23.0 + k * 1.2
        B((x0, 16.2, 5.8), (x0 + 0.5, 17.4, 11.0), "acier_brut", prm={"graine": 11 + k})
    rm.cylindre_z(B, 25.8, 11.2, 4.4, 3.1, 3.4, 4.4, "acier_brut", prm={"graine": 12})     # volant
    rm.cylindre_z(B, 25.8, 11.2, 3.4, 1.0, 2.8, 3.4, "acier")
    B((25.5, 11.0, 2.7), (26.1, 13.9, 3.4), "peint_jaune")              # repère du volant
    rm.cylindre_x(B, 0, 11.0, 8.4, 0.8, X1, 22.0, "acier_brut")         # arbre moteur -> broyeur
    B((20.8, 10.0, 7.4), (21.6, 12.0, 9.4), "acier")                     # accouplement
    # pot d'échappement qui traverse l'auvent
    rm.cylindre_y(B, 28.4, 11.0, 0.9, 16.2, 28.0, "acier_brut")
    rm.cylindre_y(B, 28.4, 11.0, 1.1, 27.4, 28.6, "braise" if marche else "suie")
    rm.cylindre_x(B, 0, 18.6, 11.0, 1.5, 24.6, 28.6, "acier_brut", prm={"graine": 13})    # silencieux
    # boîte à interrupteur sur le pied avant du bâti, voyants
    B((22.2, 3.0, 2.2), (25.6, 6.8, 3.0), "acier_brut", prm={"graine": 14})
    B((22.8, 4.0, 1.8), (23.6, 6.0, 2.2), "acier")                      # interrupteur
    B((24.0, 5.2, 2.0), (24.9, 6.1, 2.2), "voyant_vert" if marche else "voyant_vert_off")
    B((24.0, 3.8, 2.0), (24.9, 4.7, 2.2), "voyant_rouge_off")
    # auvent : deux montants en fer à béton, tôle ondulée penchée vers l'est
    for z0 in (3.0, 12.6):
        B((30.4, 8.2, z0), (31.2, 23.0, z0 + 0.8), "acier_brut", prm={"graine": 15})
    B((21.2, Y1 + 0.8, 13.2), (22.0, 26.6, 14.0), "acier_brut", prm={"graine": 16})
    B((20.6, 26.6, 1.6), (32.0, 27.2, 15.2), "tole_ondulee", rot=(0, 0, -22.5), pivot=(20.6, 27.2, 8.4),
      prm={"graine": 17})
    # baladeuse pendue sous l'auvent
    B((24.1, 21.0, 4.1), (24.4, 24.6, 4.4), "cable")
    B((23.5, 19.4, 3.5), (25.0, 21.0, 5.0), "ampoule_on" if marche else "ampoule")
    B((23.4, 20.8, 3.4), (25.1, 21.2, 5.1), "acier_brut")
    # batterie de voiture devant la porte, câbles vers la boîte à interrupteur (derrière la caisse)
    B((3.0, Y0, 1.4), (7.6, 6.2, 4.4), "plastique")
    B((3.6, 6.2, 2.2), (4.6, 6.8, 3.2), "borne_rouge")
    B((6.0, 6.2, 2.2), (7.0, 6.8, 3.2), "acier")
    B((6.4, 6.8, 2.5), (7.0, 7.2, 4.6), "cable")
    B((7.0, Y0, 4.4), (22.2, 2.7, 4.8), "cable")
    B((21.8, Y0, 2.6), (22.2, 4.2, 4.8), "cable")
    # caisse de sortie en bois, ferraille dedans
    C0, C1, D0, D1 = 11.6, 20.2, -1.6, 4.4
    B((C0, Y0, D0), (C1, Y0 + 0.6, D1), "bois", prm={"graine": 40})
    for (y0, y1) in ((Y0 + 0.6, 4.6), (4.8, 6.8)):
        B((C0, y0, D0), (C1, y1, D0 + 0.6), "bois", prm={"graine": 41 + int(y0)})
        B((C0, y0, D1 - 0.6), (C1, y1, D1), "bois", prm={"graine": 43 + int(y0)})
        B((C0, y0, D0 + 0.6), (C0 + 0.6, y1, D1 - 0.6), "bois", prm={"graine": 45 + int(y0)})
        B((C1 - 0.6, y0, D0 + 0.6), (C1, y1, D1 - 0.6), "bois", prm={"graine": 47 + int(y0)})
    B((13.0, Y0 + 0.6, 0.0), (15.8, 6.4, 2.6), "acier_brut", rot=(0, 22.5, 0), pivot=(14.4, Y0 + 0.6, 1.3))
    B((15.6, Y0 + 0.6, -0.6), (19.0, 5.9, 0.0), "circuit", rot=(22.5, 0, 0), pivot=(15.6, Y0 + 0.6, -0.6))
    B((16.4, Y0 + 0.6, 1.0), (19.0, 6.0, 3.4), "tole_galva", prm={"graine": 18})

    if marche or cadre:
        # fumée et étincelles : des particules en jeu, dessinées ici pour montrer la machine en marche ; arrêté avec
        # « cadre » : mêmes pièces invisibles, pour que les deux rendus aient exactement le même cadrage
        for k, (dx, y, t) in enumerate(((0.2, 29.4, 1.2), (0.9, 31.0, 1.6), (0.3, 32.8, 2.0), (1.4, 34.8, 2.5))):
            B((28.4 + dx - t, y, 11.0 - t), (28.4 + dx + t, y + 2 * t * 0.8, 11.0 + t), "fumee" if marche else "vide",
              prm={"graine": k})
        for (x, y, z) in ((6.0, 24.8, 8.4), (8.8, 25.6, 10.6), (7.2, 26.4, 11.4), (9.6, 24.6, 7.8), (5.4, 25.9, 10.2)):
            B((x, y, z), (x + 0.35, y + 0.35, z + 0.35), "etincelle" if marche else "vide")
    return [Os("r")], cubes


def decale(cubes, dx, dz):
    """Copie déplacée (scènes de comparaison) ; les décalcomanies suivent."""
    out = []
    o = np.array([dx, 0, dz], float)
    for c in cubes:
        d = tuple(Decal(k.m, k.o + o, k.a, k.b, k.c, k.op, k.lueur, k.n) for k in c.decals)
        out.append(Cube(c.os, c.a + o, c.b + o, c.mat, faces=c.faces, rot=c.rot,
                        pivot=None if c.pivot is None else tuple(np.array(c.pivot, float) + o), prm=c.prm, decals=d))
    return out


def controler(cubes):
    """Mêmes limites que les modèles de bloc de Minecraft (le modèle ne sera installé qu'après validation)."""
    for c in cubes:
        if c.mat in ("fumee", "etincelle", "vide"):
            continue
        for v in list(c.a) + list(c.b):
            if v < -16 or v > 32:
                raise SystemExit(f"pièce hors des limites de Minecraft : {c.a} {c.b} ({c.mat})")
        if c.rot:
            axes = [k for k, a in enumerate(c.rot) if a]
            if len(axes) != 1 or abs(c.rot[axes[0]]) % 22.5 or abs(c.rot[axes[0]]) > 45:
                raise SystemExit(f"rotation impossible dans un modèle de bloc : {c.rot}")


# ================================================================================================ rendus
VUES = {"trois quarts": (math.radians(215), math.radians(24)), "face": (math.radians(180), math.radians(8)),
        "autre côté": (math.radians(145), math.radians(24)), "plongée": (math.radians(200), math.radians(55))}


def rendre(cubes, vue, taille=1000):
    os_ = [Os("r")]
    tex, lueur, _, _ = ex.construire(os_, cubes)
    return ex.rendu(os_, cubes, tex, lueur, set(), vue, taille=taille)


def planche_recadree(images, titres, fichier, marge=14):
    """Planche de rendus recadrés sur ce qui est dessiné : même cadre pour toutes les images (la plus grande boîte), donc
    même échelle d'une image à l'autre."""
    fond = np.array([28, 31, 33])
    boites = []
    for im in images:
        a = np.asarray(im.convert("RGB"), int)
        plein = np.abs(a - fond).sum(-1) > 12
        ys, xs = np.nonzero(plein)
        boites.append((xs.min(), ys.min(), xs.max(), ys.max()))
    x0 = max(0, min(b[0] for b in boites) - marge)
    y0 = max(0, min(b[1] for b in boites) - marge)
    x1 = min(images[0].size[0], max(b[2] for b in boites) + marge)
    y1 = min(images[0].size[1], max(b[3] for b in boites) + marge)
    ex.planche([im.crop((x0, y0, x1, y1)) for im in images], titres, fichier)


def scene(*groupes):
    """Cubes réunis ; un groupe peut être une fonction (B) -> None qui pose ses pièces."""
    out = []
    for g in groupes:
        if callable(g):
            g(lambda a, b, mat, **k: out.append(Cube("r", a, b, mat, **k)))
        else:
            out.extend(g)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--apercu", type=Path, required=True, help="dossier des rendus (hors du dépôt)")
    args = ap.parse_args()
    ex.peindre = peindre
    ex.D, ex.LARGEUR, ex.MARGE = 16, 2048, 2
    _, arret = cubes_recycleur_carte(False)
    _, arret_cadre = cubes_recycleur_carte(False, cadre=True)
    _, marche = cubes_recycleur_carte(True)
    controler(arret)
    controler(marche)
    out = args.apercu
    out.mkdir(parents=True, exist_ok=True)

    ims = [rendre(arret, v) for v in VUES.values()]
    ex.planche(ims, ["arrêté · " + t for t in VUES], out / "recycleur_carte_vues.png")

    tq, face = VUES["trois quarts"], VUES["face"]
    planche_recadree([rendre(arret_cadre, tq), rendre(marche, tq)],
                     ["arrêté", "en marche"], out / "recycleur_carte_arret_marche.png")
    planche_recadree([rendre(arret_cadre, face), rendre(marche, face)], ["arrêté · face", "en marche · face"],
                     out / "recycleur_carte_arret_marche_face.png")
    rendre(marche, tq, taille=1600).save(out / "recycleur_carte_trois_quarts.png")

    # échelle : joueur de 1,8 bloc à côté, sol quadrillé d'un bloc
    sc = scene(arret, lambda B: joueur(B, -16.0, 4.0), lambda B: sol(B, -32, 48, -16, 32))
    planche_recadree([rendre(sc, tq, 1400), rendre(sc, face, 1400), rendre(sc, VUES["autre côté"], 1400)],
                     ["échelle · trois quarts", "échelle · face (1 dalle = 1 bloc)", "échelle · autre côté"],
                     out / "recycleur_carte_echelle.png")

    # comparaison avec le recycleur du spawn (même taille au sol : 2 x 1 bloc). Vu de face, l'est (+x) est à gauche :
    # le recycleur du spawn (+x) est à gauche, celui de la carte à droite.
    _, spawn = rm.cubes_recycleur()
    sc = scene(decale(spawn, 12.0, 0), decale(marche, -44.0, 0), lambda B: joueur(B, 0.0, 4.0), lambda B: sol(B, -48, 48, -16, 32))
    planche_recadree([rendre(sc, tq, 1600), rendre(sc, face, 1600)],
                     ["spawn (propre) · joueur · carte (rouillé, en marche)", "face : spawn · joueur · carte"],
                     out / "comparaison_spawn_carte.png")
    print(f"[recycleur carte] {len(arret)} pièces (arrêté), {len(marche)} (en marche, particules comprises) -> {out}")


if __name__ == "__main__":
    main()
