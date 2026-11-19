"""Shared parts of the ClashWar map generators (generer_*.py): map context (size, grid, mirrored writer), ground, perimeter fence
with its reachability pass, and the props every map uses (cars, trees, crates, roadblocks, kiosks, sheds...).

A generator calls init() first, then `from xl_carte import *`, builds its half of the map with the props and its own
buildings, and ends with construire_carte(). Every write goes through the mirrored writer W (xl_lib.Sym): the other
half is the same geometry turned by 180 degrees, with the B palette.
"""
import json
import math
import os

import xl_lib as xl
from xl_lib import dalle, etat, feuilles, marche, oriente, trappe, tronc
import xl_formes as F
from xl_formes import C, D, G, M, S, U, LUMIERE, LUMIERE_F, garde_corps

from chemins_cartes import DONNEES
SX = SZ = SY = 0
g = None
W = None


def centre():
    """Symmetry centre in block coordinates (between the two middle cells): SX / 2, SZ / 2."""
    return SX / 2.0, SZ / 2.0


def miroir_point(p):
    """Mirror of a point [x, y, z, yaw]: the same place for the other team (x -> SX - x, z -> SZ - z, yaw + 180)."""
    yaw = p[3] if len(p) > 3 else 0.0
    return [SX - p[0], p[1], SZ - p[2], (yaw + 180.0) % 360.0 if yaw >= 0 else yaw + 180.0]


def init(sx, sy, sz, pal_b, var_b, graine=14):
    """New map context: size, empty grid, mirrored writer with the B palette and cosmetic variants."""
    global SX, SZ, SY, g, W
    SX, SY, SZ = sx, sy, sz
    g = xl.Grille(sx, sy, sz, graine=graine)
    W = xl.Sym(g, pal_b, var_b)
    HAUT_ENCEINTE.clear()
    PASSE1["actif"] = False


def h(*a):
    """Deterministic hash in 0..9999 (same for a cell in every run)."""
    v = 0
    for x in a:
        v = (v * 1000003 + int(x) * 7919 + 17) % 2147483647
    return (v * 2654435761) % 10000


def voiture(x, z, facing, modele=U + "epave_rouge"):
    """Car wreck model (3 cells long, along x for facing north/south), with invisible collision on the two ends."""
    W.set(x, S, z, oriente(modele, facing))
    if facing in ("north", "south"):
        W.set(x - 1, S, z, oriente(U + "collision_invisible", facing))
        W.set(x + 1, S, z, oriente(U + "collision_invisible", facing))
    else:
        W.set(x, S, z - 1, oriente(U + "collision_invisible", facing))
        W.set(x, S, z + 1, oriente(U + "collision_invisible", facing))


def voiture_accidentee(x, z, facing):
    """heraliz_decors 2 x 4 crashed car (multi-block, 8 parts)."""
    cw = xl.CW[facing]
    op = xl.OPPOSE[facing]
    for i, partie in enumerate(("r0_g", "r0_d", "r1_g", "r1_d", "r2_g", "r2_d", "r3_g", "r3_d")):
        dx, dz = i % 2, i // 2
        px = x + xl.DIRS[cw][0] * dx + xl.DIRS[op][0] * dz
        pz = z + xl.DIRS[cw][1] * dx + xl.DIRS[op][1] * dz
        W.set(px, S, pz, etat(D + "voiture_accidentee", facing=facing, partie=partie))


def lampadaire(x, z, facing, allume=True):
    W.set(x, S, z, oriente(U + "lampadaire_pied", facing))
    for y in (S + 1, S + 2, S + 3):
        W.set(x, y, z, oriente(U + "lampadaire_mat", facing))
    W.set(x, S + 4, z, oriente(U + ("lampadaire_tete_allumee" if allume else "lampadaire_tete"), facing))


def arbre_bouleau(x, z, haut=7, mat="birch"):
    """Birch (A) / oak (B, via the palette): trunk, two short branches and an irregular, lumpy crown (leaves
    persistent). The crown is an ellipsoid with random bites and bumps, so no two trees look alike."""
    for y in range(S, S + haut):
        W.set(x, y, z, tronc(M + mat + "_log"))
    top = S + haut
    # branches at the crown base
    for i, (dx, dz) in enumerate(((1, 0), (-1, 0), (0, 1), (0, -1))):
        if h(x, z, i) % 3 == 0:
            W.set(x + dx, top - 2, z + dz, tronc(M + mat + "_log", "x" if dx else "z"))
    rx = 2.3 + (h(x, z, 7) % 10) / 20.0
    rz = 2.3 + (h(x, z, 8) % 10) / 20.0
    ry = 2.2
    cy = top - 0.5
    pose = set()
    for y in range(top - 4, top + 3):
        for dx in range(-3, 4):
            for dz in range(-3, 4):
                d = (dx / rx) ** 2 + (dz / rz) ** 2 + ((y - cy) / ry) ** 2
                bruit = (h(x + dx, y, z + dz, 3) % 100) / 100.0
                if d > 0.75 + 0.45 * bruit:
                    continue
                if d > 0.55 and bruit < 0.18:
                    continue          # bites in the crown
                if W.get(x + dx, y, z + dz) is None:
                    W.set(x + dx, y, z + dz, feuilles(M + mat))
                    pose.add((x + dx, y, z + dz))
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        if W.get(x + dx, top - 1, z + dz) is None:
            W.set(x + dx, top - 1, z + dz, feuilles(M + mat))
            pose.add((x + dx, top - 1, z + dz))
    W.set(x, top, z, feuilles(M + mat))
    pose.add((x, top, z))
    # remove leaves that the bites cut off from the tree (no floating leaves)
    tenu = {(x, top - 1, z), (x, top, z)}
    pile = [(x, top - 1, z)]
    while pile:
        c = pile.pop()
        for d in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (c[0] + d[0], c[1] + d[1], c[2] + d[2])
            if n in pose and n not in tenu:
                tenu.add(n)
                pile.append(n)
    for c in pose - tenu:
        W.set(c[0], c[1], c[2], None)


def peuplier(x, z, haut=10):
    """Tall narrow poplar (spruce log + dark oak leaves column)."""
    for y in range(S, S + haut - 2):
        W.set(x, y, z, tronc(M + "dark_oak_log"))
    for y in range(S + 3, S + haut + 1):
        r = 1 if S + 4 < y < S + haut - 1 else 0
        for dx in range(-r, r + 1):
            for dz in range(-r, r + 1):
                if abs(dx) + abs(dz) > 1 and h(x, y, z, dx) % 2:
                    continue
                if W.get(x + dx, y, z + dz) is None:
                    W.set(x + dx, y, z + dz, feuilles(M + "dark_oak"))
        if r == 0 and W.get(x, y, z) is None:
            W.set(x, y, z, feuilles(M + "dark_oak"))


def haie(x0, z0, x1, z1, haut=1, mat="spruce"):
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(S, S + haut):
                W.set(x, y, z, feuilles(M + mat))


def caisses(x, z, motif, y=S):
    """Stack of crates from a small pattern: list of strings, digits = height."""
    mats = [M + "iron_block", M + "orange_wool", M + "terracotta", M + "melon", M + "barrel"]
    for j, ligne in enumerate(motif):
        for i, c in enumerate(ligne):
            if c.isdigit():
                for k in range(int(c)):
                    m = mats[(h(x + i, z + j, k) // 7) % len(mats)]
                    if m == M + "barrel":
                        m = etat(M + "barrel", facing="up", open="false")
                    W.set(x + i, y + k, z + j, m)


def pneus(x, z, n=2):
    for k in range(n):
        W.set(x, S + k, z, etat(D + "pneu", axis="y"))


def poubelle(x, z, facing):
    W.set(x, S, z, oriente(C + ("arkaniaz_trash_can1" if h(x, z) % 2 else "zombie_trashcan_1"), facing))


def benne(x, z, axe="x"):
    """Steel waste container 2 x 1 x 2 (cover)."""
    for i in range(2):
        xx, zz = (x + i, z) if axe == "x" else (x, z + i)
        W.set(xx, S, zz, M + "lapis_block")
        W.set(xx, S + 1, zz, dalle(M + "smooth_stone", "bottom") if i == 0 else M + "green_concrete")


def beton_bloc(x, z, axe="x", n=2):
    """Concrete road blocks (FBS foundation blocks) as low cover."""
    for i in range(n):
        xx, zz = (x + i, z) if axe == "x" else (x, z + i)
        W.set(xx, S, zz, M + "smooth_stone")


def banc(x, z, facing):
    W.set(x, S, z, oriente(D + ("banc_parc" if h(x, z) % 3 else "banc_parc_casse"), facing))


def sol():
    """Ground: two layers (dirt under the surface y = G); sunken parts (pits) write their own walls and floor. Thin on
    purpose: fewer blocks to place for each arena, nothing changes for the players (one cannot dig in an arena)."""
    for x in range(SX):
        for z in range((SZ + 1) // 2):
            W.set(x, G - 1, z, M + "dirt")
            W.set(x, G, z, M + "coarse_dirt")


def surface(x0, z0, x1, z1, mats, graine=0):
    """Paint the surface with a weighted list [(block, weight), ...] (patchy, deterministic)."""
    tot = sum(p for _, p in mats)
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            # patches of 2x2 so that the noise looks like wear rather than pixels
            v = h(x // 2 + graine, z // 2, graine) % tot
            if h(x, z, graine + 3) % 5 == 0:
                v = h(x, z, graine) % tot
            for b, p in mats:
                if v < p:
                    W.set(x, G, z, b)
                    break
                v -= p


ASPHALTE = [(M + "gray_concrete", 9), (M + "black_concrete", 4), (M + "cyan_terracotta", 2), (M + "gravel", 1)]


TROTTOIR = [(M + "stone_bricks", 8), (M + "cracked_stone_bricks", 2), (M + "andesite", 1), (M + "gravel", 1)]


PELOUSE = [(M + "grass_block", 7), (M + "podzol", 3), (M + "coarse_dirt", 3), (M + "moss_block", 1), (M + "dirt_path", 1)]


TERRE = [(M + "coarse_dirt", 5), (M + "dirt_path", 2), (M + "gravel", 2), (M + "podzol", 2), (M + "rooted_dirt", 1)]


def herbes_partout(dens=13):
    """Final pass: grass tufts / ferns / dead bushes on open green ground (A half, mirrored), never under a roof."""
    for x in range(2, SX - 2):
        for z in range(2, (SZ + 1) // 2):
            if W.get(x, S, z) is not None or W.get(x, S + 1, z) is not None:
                continue
            base = W.get(x, G, z) or ""
            if not any(k in base for k in ("grass_block", "podzol", "moss_block", "coarse_dirt")):
                continue
            if any(W.get(x, y, z) is not None for y in range(S + 2, SY)):
                continue
            v = h(x, z, 5) % 100
            if v < dens:
                W.set(x, S, z, M + ("grass" if v % 4 else "fern"))
            elif v < dens + 2 and "coarse" in base:
                W.set(x, S, z, M + "dead_bush")


def herbes(x0, z0, x1, z1, dens=12, graine=0):
    """Grass tufts, ferns and dead bushes on green surfaces (passable, cosmetic)."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if W.get(x, S, z) is not None:
                continue
            base = W.get(x, G, z) or ""
            if not any(k in base for k in ("grass_block", "podzol", "moss_block", "coarse_dirt")):
                continue
            v = h(x, z, graine, 5) % 100
            if v < dens:
                W.set(x, S, z, M + ("grass" if v % 4 else "fern"))
            elif v < dens + 2 and "coarse" in base:
                W.set(x, S, z, M + "dead_bush")


HAUT_ENCEINTE = {}   # (x, z) -> top block y of the fence column (default S + 3: 4 high), set by the first pass


PASSE1 = {"actif": False}


def enceinte():
    """Concrete fence (PO-2 like panels between posts) on the outer 2-block ring; 4 high by default, higher where a
    reachable surface is near (first pass: a tall fence nobody can climb, reachability, then the needed heights)."""
    def colonne(x, z):
        top = S + 14 if PASSE1["actif"] else HAUT_ENCEINTE.get((x, z), S + 3)
        poteau = (x + z) % 4 == 0
        for y in range(S, top + 1):
            if poteau:
                b = M + "stone_bricks"
            elif y == S:
                b = M + "andesite"
            elif y == top:
                b = M + "smooth_stone"
            elif y > S + 3:
                b = M + "cracked_stone_bricks" if h(x, y, z) % 3 else M + "stone_bricks"
            else:
                b = M + ("chiseled_stone_bricks" if (y - S) == 1 and (x + z) % 4 == 2 else "smooth_stone")
            W.set(x, y, z, b)
        W.set(x, top + 1, z, dalle(M + "stone_brick", "bottom") if poteau else None)

    for x in range(SX):
        for z in (0, 1):
            colonne(x, z)
    for z in range(2, (SZ + 1) // 2):
        for x in (0, 1, SX - 2, SX - 1):
            colonne(x, z)


def anneau(x, z):
    return x <= 1 or z <= 1 or x >= SX - 2 or z >= SZ - 2


def ajuster_enceinte(vu, portee=4):
    """Every fence column gets a top at least 1 above the highest reachable feet within `portee` blocks (nobody can
    jump or drop onto it and leave the bounds); the two halves keep identical heights (max with the mirror)."""
    import numpy as np
    sy = vu.shape[1]
    hauteur = np.where(vu, np.arange(sy)[None, :, None], -1).max(axis=1)
    hauteur[:2, :] = -1
    hauteur[-2:, :] = -1
    hauteur[:, :2] = -1
    hauteur[:, -2:] = -1
    HAUT_ENCEINTE.clear()
    for x in range(SX):
        for z in range(SZ):
            if not anneau(x, z):
                continue
            x0, x1 = max(0, x - portee), min(SX - 1, x + portee)
            z0, z1 = max(0, z - portee), min(SZ - 1, z + portee)
            besoin = max(S + 3, int(hauteur[x0:x1 + 1, z0:z1 + 1].max()) + 1)
            mx, mz = SX - 1 - x, SZ - 1 - z
            HAUT_ENCEINTE[(x, z)] = max(HAUT_ENCEINTE.get((x, z), S + 3), besoin)
            HAUT_ENCEINTE[(mx, mz)] = max(HAUT_ENCEINTE.get((mx, mz), S + 3), besoin)
    return sum(1 for v in HAUT_ENCEINTE.values() if v > S + 3)


def bus_couche(x0, z0):
    """Burnt city bus lying on its side along x (11 x 3 x 3): roof to the south, underside and wheels to the north,
    the window side up. A 3-high metal wall; one can climb on it from the crates at its west end."""
    x1 = x0 + 10
    for x in range(x0, x1 + 1):
        for y in range(S, S + 3):
            W.set(x, y, z0 + 2, M + "lime_terracotta" if h(x, y) % 3 else M + "black_concrete")   # roof (rust)
            W.set(x, y, z0, M + "black_concrete" if (x + y) % 3 else M + "coal_block")             # underside
        for z in (z0 + 1,):
            W.set(x, S, z, M + "lime_terracotta")                         # lower side (was the right side)
            W.set(x, S + 1, z, M + "black_concrete" if x in (x0, x1) else None)
            fen = (x - x0) % 3 != 0 and x not in (x0, x1)
            W.set(x, S + 2, z, M + "iron_bars" if fen else M + "lime_terracotta")   # window side, now on top
    for x in (x0, x1):
        for y in range(S, S + 3):
            W.set(x, y, z0 + 1, M + "black_concrete")
    # wheels sticking out of the underside
    for x in (x0 + 1, x0 + 2, x1 - 2, x1 - 1):
        W.set(x, S + 1, z0 - 1, etat(D + "pneu", axis="z"))
        W.set(x, S, z0 - 1, M + "black_concrete") if x in (x0 + 1, x1 - 1) else None
    W.set(x0 + 5, S + 1, z0 + 1, LUMIERE_F)
    caisses(x0 - 1, z0, ["1", "2", "1"])


def camion(x0, z0, sens):
    """Army-green truck (ZIL like) along x, cab to the `sens` (east/west) end: 7 x 3 x 3, khaki tarp on the box."""
    xs = list(range(x0, x0 + 7))
    cab = xs[-2:] if sens == "east" else xs[:2]
    for x in xs:
        for z in range(z0, z0 + 3):
            W.set(x, S, z, etat(D + "pneu", axis="z") if (z != z0 + 1 and x in (xs[0], xs[2], xs[-1])) else M + "coal_block")
            if x in cab:
                W.set(x, S + 1, z, M + "green_concrete" if not (x == cab[-1 if sens == "east" else 0] and z == z0 + 1) else M + "glass_pane")
                W.set(x, S + 2, z, M + "green_concrete" if x != cab[-1 if sens == "east" else 0] else M + "gray_stained_glass")
            else:
                bord = z in (z0, z0 + 2) or x in (xs[0], cab[0] - 1 if sens == "east" else cab[-1] + 1)
                W.set(x, S + 1, z, D + "bache_kaki" if bord else None)
                W.set(x, S + 2, z, D + "bache_kaki")


def barrage(x0, z0, x1, z1):
    """Roadblock: concrete blocks 2 high with khaki sandbags on top in places, barbed wire in front (cover at eye
    level). x0..x1 / z0..z1 is a line (1 wide)."""
    i = 0
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            W.set(x, S, z, M + "smooth_stone")
            W.set(x, S + 1, z, D + "bache_kaki" if i % 3 else M + "smooth_stone")
            if i % 4 == 1:
                W.set(x, S + 2, z, dalle(D + "bache_kaki_dalle", "bottom"))
            i += 1


def cabine_tel(x, z, facing):
    W.set(x, S, z, M + "red_wool")
    W.set(x, S + 1, z, M + "red_wool")
    W.set(x, S + 2, z, dalle(M + "smooth_stone", "bottom"))


def colonne_affiche(x, z):
    W.set(x, S, z, M + "stone")
    W.set(x, S + 1, z, etat(M + "quartz_pillar", axis="y"))
    W.set(x, S + 2, z, etat(M + "quartz_pillar", axis="y"))
    W.set(x, S + 3, z, dalle(M + "smooth_stone", "bottom"))


def citerne_kvas(x0, z0):
    """Yellow kvass tank trailer along x (3 long), 2 wide, on two wheels, tow bar to the west."""
    for x in range(x0, x0 + 3):
        for z in (z0, z0 + 1):
            W.set(x, S + 1, z, M + "yellow_terracotta")
            W.set(x, S + 2, z, M + "yellow_terracotta" if x == x0 + 1 else dalle(M + "smooth_stone", "bottom"))
    for z in (z0, z0 + 1):
        W.set(x0 + 1, S, z, etat(D + "pneu", axis="z"))
    W.set(x0 - 1, S, z0, M + "iron_bars")


def abribus_nord_sud(x0, z0, sens):
    """Bus shelter oriented along z (7 long), open toward `sens` (east/west)."""
    xb = x0 if sens == "east" else x0 + 2
    mos = [M + "light_blue_glazed_terracotta", M + "white_glazed_terracotta", M + "orange_glazed_terracotta"]
    for z in range(z0, z0 + 7):
        for y in range(S, S + 3):
            W.set(xb, y, z, mos[(z + y) % 3] if y > S else M + "smooth_stone")
        W.set(xb, S + 3, z, M + "smooth_stone")
        for x in range(x0, x0 + 3):
            if x != xb:
                W.set(x, S + 3, z, dalle(M + "smooth_stone", "top"))
    for y in range(S, S + 3):
        W.set(x0 + 1, y, z0, M + "smooth_stone")
        W.set(x0 + 1, y, z0 + 6, M + "smooth_stone")
    for z in range(z0 + 1, z0 + 6):
        W.set(x0 + 1, S, z, dalle(M + "spruce", "bottom"))


def pavillon(x0, z0):
    """Small beer pavilion ('Pivo'): 6 x 5 x 3, white panels, counter window to the south, flat roof, tables."""
    x1, z1 = x0 + 5, z0 + 4
    for y in range(S, S + 3):
        for x in range(x0, x1 + 1):
            W.set(x, y, z0, M + "yellow_concrete")
            W.set(x, y, z1, M + "yellow_concrete")
        for z in range(z0, z1 + 1):
            W.set(x0, y, z, M + "yellow_concrete")
            W.set(x1, y, z, M + "yellow_concrete")
    W.fill(x0, S + 3, z0, x1, S + 3, z1, M + "lapis_block")
    for x in range(x0 - 1, x1 + 2):
        W.set(x, S + 3, z1 + 1, dalle(M + "smooth_stone", "top"))
    for x in (x0 + 2, x0 + 3):
        W.set(x, S + 1, z1, None)
        W.set(x, S, z1, dalle(M + "spruce", "top"))
    W.clear(x0, S, z0 + 2, x0, S + 1, z0 + 2)
    W.set(x0 + 2, S + 2, z0 + 2, LUMIERE_F)
    W.set(x0 + 1, S, z0 + 1, etat(M + "barrel", facing="up", open="false"))
    W.set(x1 - 1, S, z0 + 1, etat(M + "barrel", facing="up", open="false"))
    W.set(x0 + 1, S, z1 + 2, oriente(D + "table_metal", "south"))
    W.set(x0 + 4, S, z1 + 2, oriente(D + "table_metal", "south"))


def palissade(x0, z0, x1, z1, trous=()):
    """Construction-site board fence (grey vertical boards), 2 high, along a line; `trous` = cells left open."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if (x, z) in trous:
                continue
            W.set(x, S, z, M + "gold_block")
            W.set(x, S + 1, z, M + "gold_block" if h(x, z) % 7 else None)


def bois_empile(x0, z0, n=3):
    """Firewood stack ('polennitsa') along z, 2 high, logs lying along x, under a little tin roof."""
    for z in range(z0, z0 + n):
        W.set(x0, S, z, tronc(M + "stripped_oak_log", "x"))
        W.set(x0, S + 1, z, tronc(M + "oak_log", "x") if z % 2 else tronc(M + "stripped_oak_log", "x"))
        W.set(x0, S + 2, z, dalle(D + "tole_toit_rouille_dalle", "bottom"))


PORTES_GARAGE = [M + "lapis_block", M + "netherite_block", M + "green_concrete", M + "lime_terracotta", M + "red_wool",
                 M + "light_blue_wool", M + "brown_wool"]


TOITS_GARAGE = [M + "black_concrete", D + "tole_toit_rouille", M + "gray_concrete", D + "tole_toit_bleue", M + "black_concrete",
                M + "cyan_terracotta", D + "tole_toit_rouille"]


def garage(x0, z0, sens, ouvert=False, graine=0):
    """Single garage 4 wide (shared walls) x 6 deep x 3 high, door 3 wide on the `sens` side (north/south)."""
    x1, z1 = x0 + 4, z0 + 5
    mur = M + "white_terracotta"
    for y in range(S, S + 3):
        for x in range(x0, x1 + 1):
            W.set(x, y, z0, mur)
            W.set(x, y, z1, mur)
        for z in range(z0, z1 + 1):
            W.set(x0, y, z, mur)
            W.set(x1, y, z, mur)
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "packed_mud")
    toit = TOITS_GARAGE[graine % len(TOITS_GARAGE)]
    W.fill(x0, S + 3, z0, x1, S + 3, z1, toit)
    zp = z0 if sens == "north" else z1
    zf = z0 - 1 if sens == "north" else z1 + 1
    for x in range(x0, x1 + 1):
        W.set(x, S + 3, zf, dalle(M + "smooth_stone", "top"))
    porte = PORTES_GARAGE[graine % len(PORTES_GARAGE)]
    if ouvert:
        W.clear(x0 + 1, S, zp, x1 - 1, S + 1, zp)
        zfond = z1 - 1 if sens == "north" else z0 + 1
        k = graine % 4
        if k == 0:
            voiture(x0 + 2, zfond, "north", U + ("epave_brulee" if graine % 2 else "epave_rouge"))
        elif k == 1:
            caisses(x0 + 1, zfond, ["21"])
            pneus(x0 + 3, zfond, 2)
        elif k == 2:
            W.set(x0 + 1, S, zfond, oriente(C + "arkaniaz_crate", sens))
            W.set(x0 + 2, S, zfond, etat(M + "barrel", facing="up", open="false"))
            W.set(x0 + 3, S, zfond, M + "crafting_table")
        else:
            W.set(x0 + 2, S, zfond, oriente(U + "generateur", sens))
        W.set(x0 + 2, S + 2, (z0 + z1) // 2, LUMIERE_F)
    else:
        W.fill(x0 + 1, S, zp, x1 - 1, S + 1, zp, porte)


def abribus(x0, z0):
    """Soviet concrete bus shelter with a mosaic back wall: 7 wide x 3 deep, roof slab at y = S + 3."""
    for x in range(x0, x0 + 7):
        for y in range(S, S + 3):
            mos = [M + "light_blue_glazed_terracotta", M + "white_glazed_terracotta", M + "orange_glazed_terracotta"]
            W.set(x, y, z0, mos[(x + y) % 3] if y > S else M + "smooth_stone")
        W.set(x, S + 3, z0, M + "smooth_stone")
        W.set(x, S + 3, z0 + 1, dalle(M + "smooth_stone", "top"))
        W.set(x, S + 3, z0 + 2, dalle(M + "smooth_stone", "top"))
    for y in range(S, S + 3):
        W.set(x0, y, z0 + 1, M + "smooth_stone")
        W.set(x0 + 6, y, z0 + 1, M + "smooth_stone")
    for x in range(x0 + 1, x0 + 6):
        W.set(x, S, z0 + 1, dalle(M + "spruce", "bottom"))


def kiosque(x0, z0, sens):
    """Small news / tobacco kiosk 3 x 3 x 3, glazed front, metal shutter."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 3):
            for y in range(S, S + 3):
                bord = x in (x0, x0 + 2) or z in (z0, z0 + 2)
                if bord:
                    W.set(x, y, z, M + "yellow_concrete")
            W.set(x, S + 3, z, M + "lapis_block")
    zf = z0 + 2 if sens == "south" else z0
    W.set(x0 + 1, S + 1, zf, M + "glass_pane")
    W.set(x0 + 1, S + 2, zf, M + "glass_pane")
    W.set(x0, S + 1, zf, M + "gray_stained_glass_pane")
    W.set(x0 + 2, S + 1, zf, M + "gray_stained_glass_pane")
    for x in range(x0 - 1, x0 + 4):
        W.set(x, S + 3, zf + (1 if sens == "south" else -1), dalle(M + "smooth_stone", "top"))


def transfo(x0, z0):
    """Transformer substation (TP): 7 x 6 brick box, metal doors, danger sign, hip roof of tin."""
    x1, z1 = x0 + 6, z0 + 5
    for y in range(S, S + 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
    W.fill(x0, S + 4, z0, x1, S + 4, z1, M + "light_gray_concrete")
    for x in range(x0, x1 + 1):
        W.set(x, S + 5, z0, marche(D + "tole_toit_bleue_escalier", "south"))
        W.set(x, S + 5, z1, marche(D + "tole_toit_bleue_escalier", "north"))
    for z in range(z0 + 1, z1):
        for x in range(x0 + 1, x1):
            W.set(x, S + 5, z, D + "tole_toit_bleue")
        W.set(x0, S + 5, z, marche(D + "tole_toit_bleue_escalier", "east"))
        W.set(x1, S + 5, z, marche(D + "tole_toit_bleue_escalier", "west"))
    for x in range(x0 + 1, x1):
        W.set(x, S + 6, z0 + 1, marche(D + "tole_toit_bleue_escalier", "south"))
        W.set(x, S + 6, z1 - 1, marche(D + "tole_toit_bleue_escalier", "north"))
        for z in range(z0 + 2, z1 - 1):
            W.set(x, S + 6, z, dalle(D + "tole_toit_bleue_dalle", "bottom"))
    W.fill(x0 + 1, S, z0, x0 + 2, S + 2, z0, M + "lapis_block")
    W.fill(x0 + 4, S, z0, x0 + 5, S + 2, z0, M + "lapis_block")
    W.set(x0 + 3, S + 2, z0 - 1, oriente(D + "panneau_danger", "north"))


def cabane(x0, z0, i):
    """Wooden shed 4 x 5 x 3 (grey boards), lean-to tin roof, door on the west side."""
    x1, z1 = x0 + 3, z0 + 4
    for y in range(S, S + 3):
        for x in range(x0, x1 + 1):
            W.set(x, y, z0, M + "gold_block")
            W.set(x, y, z1, M + "gold_block")
        for z in range(z0, z1 + 1):
            W.set(x0, y, z, M + "gold_block")
            W.set(x1, y, z, M + "gold_block")
    for z in range(z0 - 1, z1 + 2):
        for x in range(x0, x1 + 1):
            W.set(x, S + 3, z, D + "tole_toit_rouille" if x > x0 + 1 else dalle(D + "tole_toit_rouille_dalle", "top"))
    if i != 1:
        W.clear(x0, S, z0 + 2, x0, S + 1, z0 + 2)
        W.set(x0 + 2, S, z0 + 3, etat(M + "barrel", facing="up", open="false"))
    else:
        W.set(x0, S, z0 + 2, M + "coal_block")


def pigeonnier(x0, z0):
    """Pigeon loft on stilts: 4 x 4 wooden cabin, floor at y S+4 (stand S+5), wooden stairs on supports."""
    for (x, z) in ((x0, z0), (x0 + 3, z0), (x0, z0 + 3), (x0 + 3, z0 + 3)):
        for y in range(S, S + 4):
            W.set(x, y, z, tronc(M + "spruce_log"))
    W.fill(x0, S + 4, z0, x0 + 3, S + 4, z0 + 3, M + "spruce_planks")
    for x in range(x0, x0 + 4):
        for z in range(z0, z0 + 4):
            if x in (x0, x0 + 3) or z in (z0, z0 + 3):
                W.set(x, S + 5, z, M + "gold_block")
                W.set(x, S + 6, z, M + "iron_bars" if (x + z) % 2 else M + "gold_block")
    for x in range(x0 - 1, x0 + 5):
        for z in range(z0 - 1, z0 + 5):
            W.set(x, S + 7, z, dalle(D + "tole_toit_rouille_dalle", "bottom"))
    W.clear(x0, S + 5, z0 + 1, x0, S + 6, z0 + 2)
    # stairs going up westward to the platform's west side, on fence supports
    for k in range(5):
        x = x0 - 5 + k
        W.set(x, S + k, z0 + 1, marche(M + "spruce", "east"))
        for y in range(S, S + k):
            W.set(x, y, z0 + 1, M + "spruce_fence")
    W.set(x0 - 1, S + 4, z0 + 2, None)
    W.set(x0 + 2, S + 5, z0 + 2, etat(M + "barrel", facing="up", open="false"))


def coquille(x0, z0, sens):
    """Metal shell garage ('rakushka'): 3 x 4, curved roof (stairs + slab), closed."""
    for x in range(x0, x0 + 3):
        for z in range(z0, z0 + 4):
            for y in range(S, S + 2):
                W.set(x, y, z, M + "lapis_block")
            W.set(x, S + 2, z, dalle(M + "smooth_stone", "bottom") if x == x0 + 1 else marche(
                M + "stone_brick", "east" if x == x0 else "west"))


def etendoir(x0, z0):
    """Laundry drying poles (T shaped) with lines."""
    for z in (z0, z0 + 6):
        for y in range(S, S + 3):
            W.set(x0, y, z, M + "iron_bars")
        for x in (x0 - 1, x0, x0 + 1):
            W.set(x, S + 3, z, M + "iron_bars")
    for z in range(z0 + 1, z0 + 6):
        W.set(x0 - 1, S + 3, z, etat(M + "chain", axis="z", waterlogged="false"))
        W.set(x0 + 1, S + 3, z, etat(M + "chain", axis="z", waterlogged="false"))


def construire_carte(ident, construire, meta, dossier=str(DONNEES)):
    """Two passes: build with an unclimbable fence, flood-fill what players can reach, set each fence column just high
    enough, build again and write structures/<ident>.nbt and cartes/<ident>.json. Returns (blocks, states)."""
    import acces_xl
    tmp = ident + "_passe1"

    def ecrire(nom):
        n, npal = g.ecrire(os.path.join(dossier, "structures", nom + ".nbt"))
        m = meta(n)
        m["id"] = nom
        with open(os.path.join(dossier, "cartes", nom + ".json"), "w", encoding="utf-8") as f:
            json.dump(m, f, ensure_ascii=False, indent=1)
        return n, npal

    os.makedirs(os.path.join(dossier, "structures"), exist_ok=True)
    os.makedirs(os.path.join(dossier, "cartes"), exist_ok=True)
    PASSE1["actif"] = True
    g.b.clear()
    construire()
    ecrire(tmp)
    _m, _st, vu = acces_xl.accessibles(tmp)
    ch = ajuster_enceinte(vu)
    PASSE1["actif"] = False
    for dos, ext in (("structures", ".nbt"), ("cartes", ".json")):
        try:
            os.remove(os.path.join(dossier, dos, tmp + ext))
        except OSError:
            pass
    g.b.clear()
    construire()
    n, npal = ecrire(ident)
    print(f"{ident}: {n} blocs, {npal} états, taille {SX}x{SY}x{SZ}, {ch} colonnes d'enceinte rehaussées")
    return n, npal
