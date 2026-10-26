"""Cartes du ClashWar en recherche et destruction, version 1 (maquette 5 validée le 21/10) : 6 cartes de 112 x 112
blocs, 3 couloirs chacune, pour tous les formats (1v1 à 4v4, type « clashwar_rd »).

Version de départ, faite par le calcul : le propriétaire les reprend en jeu, les embellit, puis les rend en .schem.
Chaque carte : attaquants au nord (spawn_a), défenseurs au sud (spawn_b), bombes A (ouest) et B (est) du côté des
défenseurs, sur une dalle libre de 9 x 9. Écrit, pour chaque carte, data/heraliz_clashwar/structures/<id>.nbt et
data/heraliz_clashwar/cartes/<id>.json ; contrôle que chaque bombe et chaque spawn est accessible à pied depuis les deux
spawns (marche, marches d'un bloc, échelles), et, avec --apercu DOSSIER, dessine une vue de dessus de chaque carte.

Usage : python mods/heralisz-matchs/outils/cartes_clashwar/generer.py [--apercu DOSSIER] [--seulement id,id]
"""
import argparse
import json
import math
import os
import sys
from collections import deque

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, ICI)
import voxels as vx  # noqa: E402

PROJET = os.path.normpath(os.path.join(ICI, "..", ".."))
DATA = os.path.join(PROJET, "src", "serveur", "resources", "data", "heraliz_clashwar")

S = 112          # côté de la carte
H = 36           # hauteur de la structure
SOL = 6          # y où l'on marche (dernier bloc du sol en SOL - 1 ; tunnels possibles dessous)
MUR_H = 9        # hauteur de l'enceinte
CX = S // 2
SPAWN_A_Z, SPAWN_B_Z = 8, S - 9
SITE_A = (30, 80)
SITE_B = (S - 31, 80)


# ---------------------------------------------------------------------- cadre commun
def cadre(g, surface, dessous, enceinte, couronne=None):
    g.remplir(0, 0, 0, S - 1, SOL - 2, S - 1, dessous)
    g.remplir(0, SOL - 1, 0, S - 1, SOL - 1, S - 1, surface)
    g.creux(0, SOL, 0, S - 1, SOL + MUR_H - 1, S - 1, enceinte)
    if couronne:
        g.creux(0, SOL + MUR_H, 0, S - 1, SOL + MUR_H, S - 1, couronne)


def zones_libres(g, sol_spawn="minecraft:polished_andesite"):
    """Spawns et sites dégagés à la fin (rien n'y reste par erreur)."""
    for zc in (SPAWN_A_Z, SPAWN_B_Z):
        g.vider(CX - 6, SOL, zc - 5, CX + 6, SOL + 4, zc + 4)
        g.remplir(CX - 6, SOL - 1, zc - 5, CX + 6, SOL - 1, zc + 4, sol_spawn)
    vx.site_bombe(g, SITE_A[0], SITE_A[1], SOL, "A", "minecraft:red_concrete")
    vx.site_bombe(g, SITE_B[0], SITE_B[1], SOL, "B", "minecraft:red_concrete")


def ligne_wagons(g, x, z0, z1, couleurs, rnd, rail=True):
    if rail:
        for z in range(z0, z1 + 1):
            g.set(x + 1, SOL, z, "minecraft:rail[shape=north_south,waterlogged=false]")
    z = z0
    while z + 7 <= z1:
        if rnd.random() < 0.8:
            c = rnd.choice(couleurs)
            g.remplir(x, SOL, z, x + 2, SOL, z + 6, "minecraft:black_concrete")
            g.vider(x + 1, SOL, z + 1, x + 1, SOL, z + 5)
            g.remplir(x, SOL + 1, z, x + 2, SOL + 3, z + 6, c)
            # portes ouvertes au milieu : on traverse le wagon d'est en ouest
            if rnd.random() < 0.6:
                g.vider(x, SOL + 1, z + 3, x + 2, SOL + 2, z + 3)
        z += 9


# ---------------------------------------------------------------------- 1. gare de triage
def gare(g):
    rnd = g.rnd
    cadre(g, "minecraft:gravel", "minecraft:stone", "minecraft:stone_bricks", "minecraft:iron_bars")
    couleurs = ["minecraft:red_terracotta", "minecraft:brown_terracotta", "minecraft:cyan_terracotta", "minecraft:gray_terracotta"]
    for x in (36, 42, 67, 73):
        ligne_wagons(g, x, 22, 46, couleurs, rnd)
        ligne_wagons(g, x, 64, 94, couleurs, rnd)
    # quais couverts au centre (abri, toit de dalles sur poteaux)
    for x0 in (48, 60):
        g.remplir(x0, SOL, 24, x0 + 3, SOL, 92, "minecraft:smooth_stone")
        for z in range(26, 92, 6):
            g.remplir(x0 + 1, SOL + 1, z, x0 + 1, SOL + 4, z, "minecraft:dark_oak_fence")
        g.remplir(x0, SOL + 5, 24, x0 + 3, SOL + 5, 92, "minecraft:dark_oak_slab[type=bottom,waterlogged=false]")
        for z in range(28, 90, 9):
            vx.caisses(g, x0 + 1, SOL + 1, z + 3, rnd, 2)
    # passerelle au-dessus des voies, d'est en ouest, escaliers aux deux bouts et au milieu
    yp = SOL + 6
    g.remplir(14, yp, 54, S - 15, yp, 57, "minecraft:spruce_planks")
    g.remplir(14, yp + 1, 54, S - 15, yp + 1, 54, "minecraft:iron_bars")
    g.remplir(14, yp + 1, 57, S - 15, yp + 1, 57, "minecraft:iron_bars")
    for x in range(16, S - 15, 8):
        g.remplir(x, SOL, 54, x, yp - 1, 54, "minecraft:stripped_spruce_log[axis=y]")
        g.remplir(x, SOL, 57, x, yp - 1, 57, "minecraft:stripped_spruce_log[axis=y]")
    vx.escalier(g, 14, SOL, 49, "south", 6, "minecraft:spruce_stairs", largeur=2)
    vx.escalier(g, S - 16, SOL, 62, "north", 6, "minecraft:spruce_stairs", largeur=2)
    g.vider(14, yp + 1, 54, 15, yp + 1, 54)
    g.vider(S - 16, yp + 1, 57, S - 15, yp + 1, 57)
    # cabanes d'aiguillage (attaque) et petite gare (défense)
    vx.batiment(g, 14, 18, 24, 26, SOL, 1, 5, "minecraft:bricks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
                [("s", 4, 2), ("e", 3, 2)])
    vx.batiment(g, S - 25, 18, S - 15, 26, SOL, 1, 5, "minecraft:bricks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
                [("s", 4, 2), ("o", 3, 2)])
    vx.batiment(g, 44, 86, 67, 96, SOL, 2, 5, "minecraft:bricks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
                [("n", 3, 3), ("n", 18, 3), ("s", 10, 4), ("o", 4, 2), ("e", 4, 2)])
    # couverture près des sites : piles de traverses et de caisses
    for (sx, sz) in (SITE_A, SITE_B):
        g.remplir(sx - 7, SOL, sz - 9, sx - 4, SOL + 1, sz - 9, "minecraft:stripped_dark_oak_log[axis=x]")
        g.remplir(sx + 4, SOL, sz + 7, sx + 7, SOL + 1, sz + 7, "minecraft:stripped_dark_oak_log[axis=x]")
        vx.caisses(g, sx + 6, SOL, sz - 6, rnd, 4)
        vx.caisses(g, sx - 7, SOL, sz + 5, rnd, 3)
    # lampadaires
    for x in range(20, S - 20, 18):
        for z in (36, 74):
            g.remplir(x, SOL, z, x, SOL + 4, z, "minecraft:dark_oak_fence")
            g.set(x, SOL + 5, z, "minecraft:lantern[hanging=false,waterlogged=false]")
    zones_libres(g)
    return {"nom_fr": "Gare de triage", "nom_en": "Rail yard", "theme": "rangées de wagons, quais couverts, passerelle"}


# ---------------------------------------------------------------------- 2. usine chimique
def usine(g):
    rnd = g.rnd
    cadre(g, "minecraft:light_gray_concrete", "minecraft:stone", "minecraft:gray_concrete", "minecraft:iron_bars")
    # hangar central à deux niveaux, grandes ouvertures sur les 4 côtés
    vx.batiment(g, 40, 42, 71, 70, SOL, 2, 5, "minecraft:white_concrete", "minecraft:gray_concrete", "minecraft:iron_block",
                [("n", 6, 3), ("n", 22, 3), ("s", 6, 3), ("s", 22, 3), ("o", 12, 4), ("e", 12, 4)],
                rebord="minecraft:gray_concrete")
    for z in range(46, 68, 5):
        vx.caisses(g, 50 + (z % 3), SOL, z, rnd, 3, ("minecraft:barrel[facing=up,open=false]", "minecraft:light_blue_concrete", "minecraft:iron_block"))
        vx.caisses(g, 60 - (z % 3), SOL, z, rnd, 3, ("minecraft:barrel[facing=up,open=false]", "minecraft:yellow_concrete", "minecraft:iron_block"))
    # citernes et tuyauteries
    for (cx, cz, r, h) in ((18, 40, 4, 10), (S - 19, 40, 4, 10), (CX, 26, 3, 8), (16, 62, 3, 7), (S - 17, 62, 3, 7)):
        vx.citerne(g, cx, cz, SOL, r, h, "minecraft:white_concrete", "minecraft:light_gray_concrete")
    yt = SOL + 4
    for (x0, x1, z) in ((23, 39, 40), (72, S - 24, 40)):
        g.remplir(x0, yt, z, x1, yt, z, "minecraft:iron_block")
        g.remplir(x0, yt, z + 2, x1, yt, z + 2, "minecraft:cyan_terracotta")
        for x in range(x0, x1 + 1, 5):
            g.remplir(x, SOL, z + 1, x, yt - 1, z + 1, "minecraft:iron_bars")
    g.remplir(CX, yt + 2, 30, CX, yt + 2, 41, "minecraft:iron_block")
    # bureaux (défense) et poste de garde (attaque)
    vx.batiment(g, 44, 84, 67, 95, SOL, 2, 5, "minecraft:light_gray_concrete", "minecraft:smooth_stone", "minecraft:gray_concrete",
                [("n", 4, 3), ("n", 17, 3), ("s", 10, 4), ("o", 4, 2), ("e", 4, 2)])
    vx.batiment(g, 20, 16, 30, 24, SOL, 1, 5, "minecraft:light_gray_concrete", "minecraft:smooth_stone", "minecraft:gray_concrete",
                [("s", 4, 2), ("e", 3, 2)])
    # palettes et fûts autour des sites, murets de béton
    for (sx, sz) in (SITE_A, SITE_B):
        g.remplir(sx - 8, SOL, sz - 7, sx - 8, SOL + 1, sz - 3, "minecraft:gray_concrete")
        g.remplir(sx + 8, SOL, sz + 3, sx + 8, SOL + 1, sz + 7, "minecraft:gray_concrete")
        vx.caisses(g, sx + 5, SOL, sz - 8, rnd, 4, ("minecraft:barrel[facing=up,open=false]", "minecraft:yellow_concrete"))
        vx.caisses(g, sx - 6, SOL, sz + 6, rnd, 3, ("minecraft:barrel[facing=up,open=false]", "minecraft:lime_concrete"))
    for z in (32, 52, 72):
        g.remplir(28, SOL, z, 31, SOL + 1, z, "minecraft:gray_concrete")
        g.remplir(S - 32, SOL, z, S - 29, SOL + 1, z, "minecraft:gray_concrete")
    zones_libres(g)
    return {"nom_fr": "Usine chimique", "nom_en": "Chemical plant", "theme": "hangar central, citernes, tuyauteries"}


# ---------------------------------------------------------------------- 3. port de Margot
def port(g):
    rnd = g.rnd
    cadre(g, "minecraft:stone_bricks", "minecraft:stone", "minecraft:stone_bricks", "minecraft:chain[axis=y,waterlogged=false]")
    # quai en bois et bassin le long de l'est (on peut y tomber : 2 blocs d'eau)
    g.remplir(S - 13, SOL - 1, 14, S - 2, SOL - 1, S - 15, "minecraft:spruce_planks")
    g.remplir(S - 8, SOL - 3, 18, S - 2, SOL - 2, S - 19, "minecraft:water[level=0]")
    g.vider(S - 8, SOL - 1, 18, S - 2, SOL - 1, S - 19)
    for z in range(20, S - 19, 6):
        g.set(S - 9, SOL, z, "minecraft:spruce_fence")
    couleurs = ["minecraft:red_concrete", "minecraft:blue_concrete", "minecraft:orange_concrete", "minecraft:green_concrete",
                "minecraft:white_concrete", "minecraft:brown_concrete"]
    # rangées de conteneurs (couloirs entre eux), parfois empilés
    for i, z in enumerate((22, 30, 38)):
        for x in (12, 22, 34, 66, 76, 86):
            if rnd.random() < 0.85:
                vx.conteneur(g, x, SOL, z, 7, "x", rnd.choice(couleurs), ouvert=rnd.random() < 0.5)
                if rnd.random() < 0.45:
                    vx.conteneur(g, x, SOL + 3, z, 7, "x", rnd.choice(couleurs), ouvert=False)
    for z in (60, 68):
        for x in (14, 40, 64, 88):
            if rnd.random() < 0.8:
                vx.conteneur(g, x, SOL, z, 7, "x", rnd.choice(couleurs), ouvert=True)
    # portique de la grue à cheval sur le couloir central
    for (x, z) in ((44, 46), (44, 54), (67, 46), (67, 54)):
        g.remplir(x, SOL, z, x + 1, SOL + 15, z + 1, "minecraft:yellow_concrete")
    g.remplir(44, SOL + 16, 46, 68, SOL + 17, 55, "minecraft:yellow_concrete")
    g.vider(46, SOL + 16, 48, 66, SOL + 16, 53)
    g.remplir(55, SOL + 6, 50, 57, SOL + 15, 50, "minecraft:chain[axis=y,waterlogged=false]")
    vx.conteneur(g, 52, SOL + 3, 49, 7, "x", "minecraft:red_concrete", ouvert=False)
    # entrepôt de la pêcherie (défense) et capitainerie (attaque)
    vx.batiment(g, 38, 84, 73, 96, SOL, 1, 7, "minecraft:spruce_planks", "minecraft:stone_bricks", "minecraft:dark_oak_planks",
                [("n", 4, 4), ("n", 16, 4), ("n", 28, 4), ("s", 15, 5), ("o", 5, 3), ("e", 5, 3)])
    for x in range(42, 70, 6):
        vx.caisses(g, x, SOL, 89, rnd, 4)
    vx.batiment(g, 14, 12, 26, 19, SOL, 2, 5, "minecraft:white_terracotta", "minecraft:spruce_planks", "minecraft:blue_terracotta",
                [("s", 5, 2), ("e", 2, 2)])
    for (sx, sz) in (SITE_A, SITE_B):
        vx.caisses(g, sx - 7, SOL, sz - 7, rnd, 5)
        vx.caisses(g, sx + 6, SOL, sz + 5, rnd, 4)
        g.remplir(sx - 2, SOL, sz - 8, sx + 2, SOL, sz - 8, "minecraft:spruce_slab[type=bottom,waterlogged=false]")
    zones_libres(g)
    return {"nom_fr": "Port de Margot", "nom_en": "Margot harbour", "theme": "conteneurs empilés, grue, entrepôt de la pêcherie"}


# ---------------------------------------------------------------------- 4. base militaire
def base(g):
    rnd = g.rnd
    cadre(g, "minecraft:grass_block[snowy=false]", "minecraft:dirt", "minecraft:mossy_stone_bricks", "minecraft:iron_bars")
    # piste d'est en ouest
    g.remplir(1, SOL - 1, 44, S - 2, SOL - 1, 52, "minecraft:black_concrete")
    for x in range(4, S - 4, 6):
        g.remplir(x, SOL - 1, 48, x + 2, SOL - 1, 48, "minecraft:white_concrete")
    # deux hangars : chaque site est dedans
    for (sx, sz) in (SITE_A, SITE_B):
        x0, x1 = sx - 13, sx + 13
        vx.batiment(g, x0, 66, x1, 94, SOL, 1, 8, "minecraft:light_gray_concrete", "minecraft:smooth_stone",
                    "minecraft:iron_block", [("n", 9, 9), ("s", 10, 7), ("o", 10, 3), ("e", 10, 3)], fenetres=True,
                    rebord="minecraft:gray_concrete")
        vx.caisses(g, x0 + 3, SOL, 70, rnd, 4, ("minecraft:green_concrete", "minecraft:barrel[facing=up,open=false]"))
        vx.caisses(g, x1 - 4, SOL, 90, rnd, 4, ("minecraft:green_concrete", "minecraft:barrel[facing=up,open=false]"))
        g.remplir(x0 + 4, SOL, 84, x0 + 8, SOL + 1, 85, "minecraft:green_terracotta")
        g.remplir(x1 - 8, SOL, 74, x1 - 4, SOL + 1, 75, "minecraft:green_terracotta")
    # tour de contrôle au centre
    vx.batiment(g, CX - 5, 30, CX + 5, 40, SOL, 3, 5, "minecraft:white_concrete", "minecraft:smooth_stone", "minecraft:gray_concrete",
                [("s", 4, 3), ("n", 4, 3)])
    # sacs de sable et barbelés de fortune
    for (x, z) in ((20, 24), (S - 26, 24), (34, 58), (S - 40, 58), (CX - 3, 60), (18, 40), (S - 22, 40)):
        vx.sacs(g, x, z, x + 5, z, SOL, haut=1 + (x + z) % 2)
    # tunnel sous la piste qui relie les deux hangars (entrées par escalier)
    yt = 1
    g.vider(SITE_A[0] + 6, yt, 98, SITE_B[0] - 6, yt + 2, 100)
    g.remplir(SITE_A[0] + 6, yt - 1, 98, SITE_B[0] - 6, yt - 1, 100, "minecraft:polished_andesite")
    for x in range(SITE_A[0] + 8, SITE_B[0] - 6, 6):
        g.set(x, yt + 2, 99, "minecraft:lantern[hanging=true,waterlogged=false]")
    for xs, sens in ((SITE_A[0] + 6, "north"), (SITE_B[0] - 7, "north")):
        g.vider(xs, yt, 92, xs + 1, SOL + 3, 97)
        vx.escalier(g, xs, yt, 97, "north", SOL - yt, "minecraft:stone_brick_stairs", largeur=2)
    # arbres le long de l'enceinte
    for x in range(6, S - 6, 9):
        vx.sapin(g, x, SOL, 4 + (x % 3), 7, "minecraft:dark_oak_log[axis=y]", "minecraft:dark_oak_leaves[distance=1,persistent=true,waterlogged=false]")
    zones_libres(g)
    return {"nom_fr": "Base militaire", "nom_en": "Military base", "theme": "deux hangars, tour de contrôle, tunnel sous la piste"}


# ---------------------------------------------------------------------- 5. village enneigé
def chalet(g, x0, z0, x1, z1, rnd, ouvertures):
    vx.batiment(g, x0, z0, x1, z1, SOL, 1 + (rnd.random() < 0.4), 5, "minecraft:spruce_planks", "minecraft:spruce_planks",
                "minecraft:dark_oak_planks", ouvertures, rebord="minecraft:spruce_log[axis=y]")
    for (x, z) in ((x0, z0), (x0, z1), (x1, z0), (x1, z1)):
        for y in range(SOL, g.haut(x, z) + 1):
            g.set(x, y, z, "minecraft:stripped_spruce_log[axis=y]")
    top = max(g.haut(x, z) for x in (x0, x1) for z in (z0, z1))
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            if g.get(x, top + 1, z) is None and vx.plein(g.get(x, top, z)):
                g.set(x, top + 1, z, "minecraft:snow[layers=2]")


def village(g):
    rnd = g.rnd
    cadre(g, "minecraft:snow_block", "minecraft:packed_ice", "minecraft:spruce_log[axis=y]", "minecraft:spruce_fence")
    # église au centre avec clocher
    vx.batiment(g, CX - 6, 40, CX + 6, 58, SOL, 1, 8, "minecraft:stone_bricks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
                [("n", 5, 3), ("s", 5, 3), ("o", 8, 2), ("e", 8, 2)])
    vx.batiment(g, CX - 2, 36, CX + 2, 40, SOL, 3, 5, "minecraft:stone_bricks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
                [("n", 1, 3), ("s", 1, 3)], fenetres=True, escaliers=True)
    g.set(CX, SOL + 17, 38, "minecraft:bell[attachment=floor,facing=north,powered=false]")
    # chalets dispersés le long des trois couloirs
    places = [(12, 18, 22, 26, [("s", 4, 2), ("e", 3, 2)]), (S - 23, 18, S - 13, 26, [("s", 4, 2), ("o", 3, 2)]),
              (12, 38, 21, 47, [("e", 3, 2), ("n", 4, 2)]), (S - 22, 38, S - 13, 47, [("o", 3, 2), ("n", 4, 2)]),
              (34, 24, 42, 32, [("s", 3, 2), ("o", 3, 2)]), (S - 43, 24, S - 35, 32, [("s", 3, 2), ("e", 3, 2)]),
              (12, 60, 20, 68, [("n", 3, 2), ("e", 3, 2)]), (S - 21, 60, S - 13, 68, [("n", 3, 2), ("o", 3, 2)]),
              (40, 66, 47, 74, [("o", 3, 2), ("s", 3, 2)]), (S - 48, 66, S - 41, 74, [("e", 3, 2), ("s", 3, 2)])]
    for x0, z0, x1, z1, ouv in places:
        chalet(g, x0, z0, x1, z1, rnd, ouv)
    # scierie (défense) : hangar ouvert et piles de grumes
    vx.batiment(g, 44, 86, 67, 95, SOL, 1, 6, "minecraft:spruce_planks", "minecraft:spruce_planks", "minecraft:dark_oak_planks",
                [("n", 3, 5), ("n", 15, 5), ("s", 9, 5), ("o", 3, 3), ("e", 3, 3)])
    for (x, z) in ((48, 89), (58, 91)):
        g.remplir(x, SOL, z, x + 4, SOL + 1, z + 1, "minecraft:spruce_log[axis=x]")
    for (sx, sz) in (SITE_A, SITE_B):
        g.remplir(sx - 8, SOL, sz - 6, sx - 8, SOL + 1, sz - 2, "minecraft:spruce_log[axis=z]")
        g.remplir(sx + 4, SOL, sz + 7, sx + 8, SOL + 1, sz + 7, "minecraft:spruce_log[axis=x]")
        vx.caisses(g, sx + 6, SOL, sz - 7, rnd, 3, ("minecraft:spruce_planks", "minecraft:barrel[facing=up,open=false]"))
    # sapins
    for _ in range(26):
        x, z = rnd.randint(6, S - 7), rnd.randint(14, S - 15)
        if all(g.get(x + dx, SOL, z + dz) is None for dx in (-2, 0, 2) for dz in (-2, 0, 2)):
            if math.dist((x, z), SITE_A) > 9 and math.dist((x, z), SITE_B) > 9 and abs(x - CX) > 8:
                vx.sapin(g, x, SOL, z, rnd.randint(6, 9))
    # neige au sol par endroits
    for _ in range(500):
        x, z = rnd.randint(1, S - 2), rnd.randint(1, S - 2)
        if g.get(x, SOL, z) is None:
            g.set(x, SOL, z, "minecraft:snow[layers=1]")
    zones_libres(g, "minecraft:spruce_planks")
    return {"nom_fr": "Village enneigé", "nom_en": "Snowy village", "theme": "chalets serrés, église et clocher, scierie"}


# ---------------------------------------------------------------------- 6. centre-ville en ruine
def voiture(g, x, z, couleur, axe):
    if axe == "x":
        g.remplir(x, SOL, z, x + 4, SOL, z + 1, couleur)
        g.remplir(x + 1, SOL + 1, z, x + 3, SOL + 1, z + 1, "minecraft:gray_stained_glass")
    else:
        g.remplir(x, SOL, z, x + 1, SOL, z + 4, couleur)
        g.remplir(x, SOL + 1, z + 1, x + 1, SOL + 1, z + 3, "minecraft:gray_stained_glass")


def ville(g):
    rnd = g.rnd
    cadre(g, "minecraft:gray_concrete", "minecraft:stone", "minecraft:bricks", "minecraft:iron_bars")
    # trottoirs autour des pâtés
    debris = ["minecraft:cobblestone", "minecraft:gravel", "minecraft:andesite", "minecraft:cobblestone_slab[type=bottom,waterlogged=false]"]
    pates = [(10, 16, 30, 32), (S - 31, 16, S - 11, 32), (38, 16, 48, 26), (S - 49, 16, S - 39, 26),
             (10, 40, 24, 58), (S - 25, 40, S - 11, 58), (10, 92, 22, 100), (S - 23, 92, S - 11, 100),
             (40, 86, 71, 96)]
    for i, (x0, z0, x1, z1) in enumerate(pates):
        g.remplir(x0 - 1, SOL - 1, z0 - 1, x1 + 1, SOL - 1, z1 + 1, "minecraft:smooth_stone")
        etages = 3 if i < 6 else 2
        vx.batiment(g, x0, z0, x1, z1, SOL, etages, 5, rnd.choice(["minecraft:bricks", "minecraft:terracotta", "minecraft:stone_bricks"]),
                    "minecraft:oak_planks", "minecraft:smooth_stone",
                    [("s", (x1 - x0) // 2 - 1, 3), ("n", (x1 - x0) // 2 - 1, 3), ("o", 3, 2), ("e", 3, 2)])
        ruiner(g, x0, SOL, z0, x1, SOL + etages * 5, z1, 0.18, debris, rnd)
    # place centrale, fontaine, bouche de métro
    g.remplir(CX - 10, SOL - 1, 46, CX + 10, SOL - 1, 66, "minecraft:polished_andesite")
    vx.citerne(g, CX, 56, SOL, 3, 1, "minecraft:stone_bricks")
    vx.citerne(g, CX, 56, SOL, 2, 1, "minecraft:water[level=0]")
    g.remplir(CX, SOL, 56, CX, SOL + 2, 56, "minecraft:stone_brick_wall")
    yt = 1
    g.vider(28, yt, 55, S - 29, yt + 2, 57)
    g.remplir(28, yt - 1, 55, S - 29, yt - 1, 57, "minecraft:polished_andesite")
    for x in range(30, S - 29, 6):
        g.set(x, yt + 2, 56, "minecraft:lantern[hanging=true,waterlogged=false]")
    for xs, sens in ((28, "east"), (S - 33, "west")):
        g.vider(xs, yt, 54, xs + 5, SOL + 3, 58)
        if sens == "east":
            vx.escalier(g, xs, yt, 55, "west", SOL - yt, "minecraft:stone_brick_stairs", largeur=3)
        else:
            vx.escalier(g, xs + 5, yt, 55, "east", SOL - yt, "minecraft:stone_brick_stairs", largeur=3)
    # épaves de voitures et barrières dans les rues
    couleurs = ["minecraft:red_terracotta", "minecraft:blue_terracotta", "minecraft:white_terracotta", "minecraft:gray_terracotta"]
    for (x, z, axe) in ((34, 38, "z"), (S - 36, 38, "z"), (CX - 2, 32, "x"), (28, 70, "x"), (S - 33, 70, "x"),
                        (CX + 3, 76, "z"), (CX - 6, 40, "x")):
        voiture(g, x, z, rnd.choice(couleurs), axe)
    for (sx, sz) in (SITE_A, SITE_B):
        vx.sacs(g, sx - 3, sz - 9, sx + 3, sz - 9, SOL, "minecraft:cobblestone", 1)
        voiture(g, sx + 6, sz + 2, rnd.choice(couleurs), "z")
    zones_libres(g, "minecraft:polished_andesite")
    return {"nom_fr": "Centre-ville en ruine", "nom_en": "Ruined city centre", "theme": "immeubles éventrés, place, métro sous la place"}


def ruiner(g, x0, y0, z0, x1, y1, z1, part, debris, rnd):
    vx.ruiner(g, x0, y0, z0, x1, y1, z1, part, debris, rnd)


CARTES = {
    "clashwar_gare": gare,
    "clashwar_usine": usine,
    "clashwar_port": port,
    "clashwar_base": base,
    "clashwar_village": village,
    "clashwar_ville": ville,
}


# ---------------------------------------------------------------------- points, contrôles, sortie
def lacet_vers(x, z, cible_x, cible_z):
    return round(math.degrees(math.atan2(-(cible_x - x), cible_z - z)), 1)


def points():
    sa = [[CX - 3.5 + 2 * i + 0.0, float(SOL), SPAWN_A_Z + 0.5, 0.0] for i in range(4)]
    sb = [[CX - 3.5 + 2 * i + 0.0, float(SOL), SPAWN_B_Z + 0.5, 180.0] for i in range(4)]
    for p in sa + sb:
        p[0] = math.floor(p[0]) + 0.5
    ba = [SITE_A[0] + 0.5, float(SOL), SITE_A[1] + 0.5, lacet_vers(SITE_A[0], SITE_A[1], CX, SPAWN_A_Z)]
    bb = [SITE_B[0] + 0.5, float(SOL), SITE_B[1] + 0.5, lacet_vers(SITE_B[0], SITE_B[1], CX, SPAWN_A_Z)]
    return {
        "spawn_a": sa,
        "spawn_b": sb,
        "bombe_a": [ba],
        "bombe_b": [bb],
        "site_a": [list(ba)],
        "site_b": [list(bb)],
        "charge": [[CX + 0.5, float(SOL), SPAWN_A_Z + 2.5, 0.0]],
        "spectateur": [[CX + 0.5, float(SOL + 22), 3.5, 0.0], [CX + 0.5, float(SOL + 22), S - 3.5, 180.0]],
    }


def accessible(g, depart, cibles):
    """Parcours à pied : 2 blocs libres au-dessus d'un appui, montée d'un bloc, chute de 4 au plus, échelles."""
    def libre(x, y, z):
        b = g.get(x, y, z)
        return b is None or not vx.plein(b) and "fence" not in b and "_wall" not in b and "iron_bars" not in b and "_pane" not in b
    def echelle(x, y, z):
        b = g.get(x, y, z)
        return b is not None and "ladder" in b
    def debout(x, y, z):
        if not (0 < x < S - 1 and 0 < z < S - 1 and 0 < y < H - 2):
            return False
        sous = g.get(x, y - 1, z)
        appui = vx.plein(sous) or (sous is not None and ("slab" in sous or "stairs" in sous)) or echelle(x, y, z)
        return appui and libre(x, y, z) and libre(x, y + 1, z)
    vu = {depart}
    q = deque([depart])
    while q:
        x, y, z = q.popleft()
        voisins = []
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for ny in range(y + 1, y - 5, -1):
                if debout(x + dx, ny, z + dz) and (ny <= y or libre(x, y + 2, z)):
                    voisins.append((x + dx, ny, z + dz))
                    break
        if echelle(x, y, z) or echelle(x, y + 1, z):
            for ny in (y + 1, y - 1):
                if debout(x, ny, z):
                    voisins.append((x, ny, z))
        for v in voisins:
            if v not in vu:
                vu.add(v)
                q.append(v)
    return {nom: c in vu for nom, c in cibles.items()}


def ecrire_carte(ident, fabrique, apercu):
    g = vx.Grille(S, H, S, graine=sum(ord(c) * (i + 1) for i, c in enumerate(ident)) & 0xFFFF)
    g.rnd.seed(sum(map(ord, ident)))
    meta = fabrique(g)
    pts = points()
    n = g.ecrire(os.path.join(DATA, "structures", ident + ".nbt"))
    a = (CX, SOL, SPAWN_A_Z)
    b = (CX, SOL, SPAWN_B_Z)
    cibles = {"bombe_a": (SITE_A[0], SOL, SITE_A[1]), "bombe_b": (SITE_B[0], SOL, SITE_B[1]), "spawn_b": b}
    acc_a = accessible(g, a, cibles)
    acc_b = accessible(g, b, {"bombe_a": cibles["bombe_a"], "bombe_b": cibles["bombe_b"], "spawn_a": a})
    ok = all(acc_a.values()) and all(acc_b.values())
    carte = {
        "id": ident,
        "nom_fr": meta["nom_fr"],
        "nom_en": meta["nom_en"],
        "type": "clashwar_rd",
        "dimension": "heraliz_clashwar:arenes",
        "taille": [S, H, S],
        "bornes": [1, 0, 1, S - 2, H - 1, S - 2],
        "points": pts,
        "theme": meta["theme"],
        "recherche_destruction": {"attaquants": "a", "defenseurs": "b",
                                  "note": "V1 faite par le calcul (outils/cartes_clashwar/generer.py), à reprendre en jeu."},
        "version": 1,
        "blocs": n,
        "generateur": "mods/heralisz-matchs/outils/cartes_clashwar/generer.py",
    }
    with open(os.path.join(DATA, "cartes", ident + ".json"), "w", encoding="utf-8") as f:
        json.dump(carte, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"{ident:18s} {n:7d} blocs  depuis A {acc_a}  depuis B {acc_b}  {'OK' if ok else 'PROBLEME'}")
    if apercu:
        dessiner(g, ident, meta, pts, os.path.join(apercu, ident + ".png"))
    return ok


def dessiner(g, ident, meta, pts, chemin):
    from PIL import Image, ImageDraw
    k = 6
    im = Image.new("RGB", (S * k, S * k + 26), (16, 18, 22))
    d = ImageDraw.Draw(im)
    couleurs = {}
    def couleur(bloc):
        n = vx.nom(bloc).split(":")[1]
        if n not in couleurs:
            h = sum(ord(c) * (i + 7) for i, c in enumerate(n))
            couleurs[n] = ((h * 53) % 160 + 60, (h * 97) % 160 + 60, (h * 31) % 160 + 60)
        return couleurs[n]
    for x in range(S):
        for z in range(S):
            y = -1
            for yy in range(H - 1, -1, -1):
                bl = g.get(x, yy, z)
                if bl is not None and "light" != vx.nom(bl).split(":")[1]:
                    y = yy
                    break
            if y < 0:
                continue
            c = couleur(g.get(x, y, z))
            f = 0.55 + 0.45 * min(1.0, (y - SOL + 4) / 18)
            d.rectangle([x * k, z * k + 26, x * k + k - 1, z * k + k - 1 + 26], fill=tuple(int(v * f) for v in c))
    for p, c in ((pts["spawn_a"][0], (255, 214, 64)), (pts["spawn_b"][0], (111, 191, 115))):
        d.ellipse([p[0] * k - 9, p[2] * k + 26 - 9, p[0] * k + 9, p[2] * k + 26 + 9], outline=c, width=3)
    for nom_, c in (("bombe_a", "A"), ("bombe_b", "B")):
        p = pts[nom_][0]
        d.rectangle([p[0] * k - 12, p[2] * k + 26 - 12, p[0] * k + 12, p[2] * k + 26 + 12], outline=(255, 70, 70), width=3)
        d.text((p[0] * k - 3, p[2] * k + 26 - 6), c, fill=(255, 255, 255))
    d.text((6, 6), f"{meta['nom_fr']} ({ident}) : attaque au nord (jaune), defense au sud (vert)", fill=(236, 238, 242))
    im.save(chemin)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apercu", help="dossier des vues de dessus")
    ap.add_argument("--seulement", help="ids séparés par des virgules")
    a = ap.parse_args()
    if a.apercu:
        os.makedirs(a.apercu, exist_ok=True)
    choix = a.seulement.split(",") if a.seulement else list(CARTES)
    tous = True
    for ident in choix:
        tous &= ecrire_carte(ident, CARTES[ident], a.apercu)
    if not tous:
        sys.exit(1)


if __name__ == "__main__":
    main()
