"""ClashWar 4v4 map "Garnison" (clashwar_4v4_garnison): an abandoned garrison and its village, 220 x 220.

Each half is built in the north and turned by 180 degrees for the south, with another look:
- the garrison of a team: spawn in the vehicle hangar, barracks with three stairwells, headquarters, canteen, store with
  its loading dock, obstacle course with a trench, motor pool (boxes, a tank, an armoured carrier, an inspection ramp,
  an open repair shed), ammunition depot under earth mounds one can climb, parade ground and review stand, fuel depot,
  watchtowers, anti-aircraft pit, and the garrison wall with three gates and two breaches;
- half of the village: kolkhoz cowshed, silage trench, water tower, log houses with attics, gardens, wells, haystacks,
  the club (house of culture) with its gallery and portico balcony, the village shop, the ruined church with its bell
  tower, and half of the square with the war memorial at the centre (final zone).
Three lanes go from gate to gate (west through the cowshed, centre through the clubs, east through the church); the
village street along each wall and the road across the middle link them.

Usage: python generer_garnison.py
"""
import math

import xl_carte as K
import xl_formes as F
import xl_kit as KT
from xl_lib import dalle, etat, marche, oriente, tronc
from xl_formes import C, D, G, M, S, U, LUMIERE, LUMIERE_F, garde_corps

ID = "clashwar_4v4_garnison"
SX = SZ = 220
SY = 28
MUR_Z = 64                                    # garrison wall of team A (team B: 155)
PORTES = ((32, 37), (103, 116), (182, 187))   # gates in the wall (x ranges)
BRECHES = ((66, 70), (150, 154))              # collapsed parts of the wall

PAL_B = {
    M + "lime_concrete": M + "green_terracotta",          # white silicate brick (A) -> yellow brick (B)
    M + "white_terracotta": M + "red_terracotta",
    D + "enduit_ocre": D + "enduit_bleu",                  # ochre plaster (A) -> blue (B)
    D + "enduit_ocre_use": D + "enduit_bleu_use",
    D + "enduit_blanc": D + "enduit_sable",
    D + "enduit_blanc_use": D + "enduit_sable_use",
    D + "tole_toit_rouille": D + "tole_toit_verte",         # rusty roofs (A) -> green (B)
    D + "tole_toit_rouille_escalier": D + "tole_toit_verte_escalier",
    D + "tole_toit_rouille_dalle": D + "tole_toit_verte_dalle",
    M + "pink_wool": M + "diamond_block",                   # grey corrugated sheet (A) -> white (B)
    M + "spruce_log": M + "dark_oak_log",                   # log houses
    M + "stripped_spruce_log": M + "stripped_dark_oak_log",
    M + "dark_oak_stairs": M + "mangrove_stairs",
    M + "dark_oak_slab": M + "mangrove_slab",
    M + "birch_log": M + "oak_log",                         # birches (A) -> oaks (B)
    M + "birch_leaves": M + "oak_leaves",
}
VAR_B = {
    U + "epave_rouge": U + "epave_bleue",
    U + "epave_bleue": U + "epave_rouge",
}

K.init(SX, SY, SZ, PAL_B, VAR_B, graine=47)
from xl_carte import *  # noqa: E402,F401,F403

BETON = [(M + "gray_concrete", 6), (M + "light_gray_concrete", 2), (M + "black_concrete", 2), (M + "gravel", 1)]
VILLAGE = [(M + "grass_block", 6), (M + "podzol", 3), (M + "coarse_dirt", 2), (M + "dirt_path", 2), (M + "moss_block", 1)]
CHEMIN = [(M + "dirt_path", 5), (M + "coarse_dirt", 3), (M + "gravel", 2)]
KAKI = D + "carlingue_kaki"
SAC = D + "bache_kaki"
FUT = etat(M + "barrel", facing="up", open="false")


def surfaces():
    surface(2, 2, 217, MUR_Z - 1, BETON, graine=1)
    surface(2, 2, 31, MUR_Z - 1, PELOUSE, graine=2)            # ammunition depot, west strip
    surface(64, 2, 95, 20, TERRE, graine=9)                     # obstacle course
    surface(2, MUR_Z, 217, 109, VILLAGE, graine=3)
    surface(2, MUR_Z + 1, 217, MUR_Z + 5, CHEMIN, graine=4)     # village street along the wall
    for i, (a, b) in enumerate(PORTES):
        surface(a, MUR_Z + 6, b, 109, CHEMIN, graine=5 + i)     # lanes south from the gates
    surface(38, 86, 181, 89, CHEMIN, graine=8)                  # lane between the two rows of houses
    surface(2, 104, 217, 109, CHEMIN, graine=6)                 # road across the middle


# ------------------------------------------------------------------------------------------------ small military props
def _cadre(x0, z0, axe):
    """(i along the length, j across) -> (x, z) for a vehicle laid along x or z."""
    return lambda i, j: (x0 + i, z0 + j) if axe == "x" else (x0 + j, z0 + i)


def char(x0, z0, axe="x", y=S):
    """Tank (blocks): tracks and hull 8 x 4, turret 3 x 2 with a hatch, gun sticking out 3 blocks toward +axe."""
    p = _cadre(x0, z0, axe)
    for i in range(8):
        for j in range(4):
            x, z = p(i, j)
            chenille = j in (0, 3)
            W.set(x, y, z, M + "coal_block" if chenille else M + "black_concrete")
            W.set(x, y + 1, z, KAKI if (not chenille or i in (0, 7)) else M + "coal_block")
    for i in range(3, 6):
        for j in (1, 2):
            x, z = p(i, j)
            W.set(x, y + 2, z, M + "green_concrete")
    x, z = p(4, 2)
    W.set(x, y + 3, z, dalle(D + "carlingue_kaki_dalle", "bottom"))
    for i in range(6, 11):
        x, z = p(i, 1)
        W.set(x, y + 2, z, etat(M + "polished_basalt", axis=axe))


def btr(x0, z0, axe="x", y=S):
    """Wheeled armoured carrier 7 x 3: three axles, khaki hull, small turret and gun toward +axe."""
    p = _cadre(x0, z0, axe)
    travers = "z" if axe == "x" else "x"
    for i in range(7):
        for j in range(3):
            x, z = p(i, j)
            roue = j in (0, 2) and i in (1, 3, 5)
            W.set(x, y, z, etat(D + "pneu", axis=travers) if roue else M + "coal_block")
            W.set(x, y + 1, z, KAKI)
    for i in (2, 3):
        x, z = p(i, 1)
        W.set(x, y + 2, z, M + "green_concrete")
    for i in (4, 5, 6):
        x, z = p(i, 1)
        W.set(x, y + 2, z, etat(M + "polished_basalt", axis=axe))


def stend(x0, z0, axe="x", long=5):
    """Painted information board (a 'stend'): concrete base and panel 4 high between two posts, red band on top."""
    for i in range(long):
        x, z = (x0 + i, z0) if axe == "x" else (x0, z0 + i)
        bout = i in (0, long - 1)
        for y in range(S, S + 4):
            if bout:
                W.set(x, y, z, M + "stone_bricks")
            elif y == S:
                W.set(x, y, z, M + "polished_andesite")
            else:
                W.set(x, y, z, M + "red_concrete" if y == S + 3 else M + "yellow_concrete")
        if bout:
            W.set(x, S + 4, z, dalle(M + "stone_brick", "bottom"))


def herisson(x, z):
    """Anti-tank hedgehog."""
    W.set(x, S, z, oriente(U + "herisson", "north"))


def barbeles(x0, z0, x1, z1):
    """Barbed wire coils along a line."""
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            W.set(x, S, z, oriente(U + "barbeles", "north" if x0 != x1 else "east"))


def sacs(x0, z0, x1, z1, haut=2):
    """Sandbag wall along a line, `haut` high (khaki tarp blocks, a half block on top every other cell)."""
    i = 0
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            for y in range(S, S + haut - 1):
                W.set(x, y, z, SAC)
            W.set(x, S + haut - 1, z, SAC if i % 2 else dalle(D + "bache_kaki_dalle", "bottom"))
            i += 1


def projecteur(x, y, z, facing):
    W.set(x, y, z, etat(D + "projecteur", facing=facing, pose="sol"))


# ================================================================================================ garrison
def hangar_spawn(x0=98, z0=3):
    """Vehicle hangar (spawn A): 24 x 18, brick then corrugated walls, stepped vault of rusty sheet, two big gates to
    the south and a door on each side. The start barrier (x 101..118, z 8..14) is kept free."""
    x1, z1 = x0 + 23, z0 + 17
    W.walls(x0, S, z0, x1, S + 2, z1, M + "bricks")
    W.walls(x0, S + 3, z0, x1, S + 7, z1, M + "pink_wool")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "smooth_stone")
    milieu = (x0 + x1) / 2.0
    for x in range(x0, x1 + 1):
        dx = min(x - x0, x1 - x)
        top = S + 8 + min(3, dx // 3)
        vers = "east" if x < milieu else "west"
        for z in range(z0 - 1, z1 + 2):
            if dx in (3, 6, 9):
                W.set(x, top, z, marche(D + "tole_toit_rouille_escalier", vers))
                W.set(x, top - 1, z, D + "tole_toit_rouille")
            else:
                W.set(x, top, z, D + "tole_toit_rouille")
        for z in (z0, z1):
            for y in range(S + 8, top):
                if W.get(x, y, z) is None:
                    W.set(x, y, z, M + "pink_wool")
    W.clear(x0 + 3, S, z1, x0 + 8, S + 4, z1)
    W.clear(x1 - 8, S, z1, x1 - 3, S + 4, z1)
    W.clear(x0, S, z0 + 8, x0, S + 1, z0 + 9)
    W.clear(x1, S, z0 + 8, x1, S + 1, z0 + 9)
    for z in (z0 + 4, z0 + 13):
        for x in (x0, x1):
            W.set(x, S + 5, z, M + "gray_stained_glass_pane")
    for x in range(x0 + 4, x1 - 2, 5):
        W.set(x, S + 7, z0 + 9, LUMIERE)
    camion(x0 + 2, z0 + 1, "east")
    caisses(x1 - 5, z0 + 1, ["22", "21"])
    W.set(x1 - 1, S, z0 + 6, M + "crafting_table")
    W.set(x0 + 1, S, z1 - 2, FUT)
    W.set(x1 - 1, S, z1 - 2, FUT)
    pneus(x0 + 1, z0 + 12, 2)


def caserne():
    """Barracks: 4-storey block of white silicate brick, three stairwells (to the 1st and 2nd floors), windows in the
    gables, no balconies."""
    st = dict(F.STYLE_BRIQUE)
    st.update({"mur": M + "lime_concrete", "mur_var": M + "white_terracotta", "bandeau": M + "light_gray_concrete",
               "socle": M + "stone", "acrotere": M + "light_gray_concrete", "edicule": M + "lime_concrete",
               "cloison": M + "white_wool", "peinture_bas": M + "green_concrete", "peinture_haut": M + "white_wool",
               "sol_appart": M + "spruce_planks"})
    F.Barre(W, F.Repere(30, 22, "+x", "+z"), 56, etages=4, style=st, podyezds=(5, 25, 45), balcons=False,
            pignon_fenetres=True, graine=11).construire()


def etat_major():
    """Headquarters: 4-storey block of ochre plaster, one stairwell, three flagpoles in front."""
    st = dict(F.STYLE_BRIQUE)
    st.update({"mur": D + "enduit_ocre", "mur_var": D + "enduit_ocre_use", "bandeau": M + "smooth_stone",
               "socle": M + "stone", "acrotere": M + "smooth_stone", "edicule": D + "enduit_ocre",
               "cloison": M + "yellow_wool", "peinture_bas": M + "light_blue_terracotta"})
    F.Barre(W, F.Repere(138, 22, "+x", "+z"), 30, etages=4, style=st, podyezds=(12,), balcons=False,
            pignon_fenetres=True, graine=13).construire()
    for x, haut in ((146, 9), (152, 11), (158, 9)):
        W.set(x, S, 37, M + "polished_andesite")
        for y in range(S + 1, S + haut):
            W.set(x, y, 37, M + "iron_bars")
        for y in (S + haut - 3, S + haut - 2):
            W.set(x, y, 38, M + "red_wool")
            W.set(x, y, 39, M + "red_wool")


def entrepot(x0=34, z0=2):
    """Store: 29 x 10 brick shed, pitched rusty roof (ridge along x), loading dock along the south side with three
    open gates, rows of crates inside."""
    x1, z1 = x0 + 28, z0 + 9
    W.walls(x0, S, z0, x1, S + 4, z1, M + "bricks")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "smooth_stone")
    for k in range(5):
        y = S + 5 + k
        for x in range(x0, x1 + 1):
            W.set(x, y, z0 + k, marche(D + "tole_toit_rouille_escalier", "south"))
            W.set(x, y, z1 - k, marche(D + "tole_toit_rouille_escalier", "north"))
        for x in (x0, x1):
            for z in range(z0 + k + 1, z1 - k):
                W.set(x, y, z, M + "bricks")
    for x in range(x0 + 2, x1 - 1):
        for z in (z1 + 1, z1 + 2):
            W.set(x, S, z, M + "smooth_stone" if z == z1 + 1 else M + "polished_andesite")
    for z in (z1 + 1, z1 + 2):
        W.set(x0 + 1, S, z, marche(M + "stone_brick", "east"))
        W.set(x1 - 1, S, z, marche(M + "stone_brick", "west"))
    for xa in (x0 + 5, x0 + 13, x0 + 21):
        W.clear(xa, S, z1, xa + 2, S + 3, z1)
    for xa in (x0 + 2, x0 + 10, x0 + 18):
        caisses(xa, z0 + 2, ["2321", "12"])
        caisses(xa + 2, z0 + 5, ["232", "1"])
    for x in range(x0 + 4, x1, 6):
        W.set(x, S + 3, z0 + 4, LUMIERE_F)
    W.set(x0 + 26, S, z1 + 3, etat(D + "tas_charbon", taille="grand"))
    W.set(x0 + 25, S, z1 + 3, etat(D + "tas_charbon", taille="moyen"))


def polygone():
    """Obstacle course (x 64..95, z 2..20): a zigzag trench 1 deep behind sandbags, concrete walls to vault, a
    'ruined house' training wall with windows, pull-up bars, a log pile."""
    tranchee = [(66, 16, 76, 17), (75, 9, 76, 15), (77, 9, 86, 10), (85, 11, 86, 17), (87, 16, 94, 17)]
    for (a, b, c, d) in tranchee:
        for x in range(a, c + 1):
            for z in range(b, d + 1):
                W.set(x, G, z, None)
                W.set(x, G - 1, z, M + "mud")
    sacs(66, 18, 74, 18, 1)
    sacs(77, 11, 84, 11, 1)
    sacs(87, 18, 94, 18, 1)
    sacs(73, 8, 74, 8, 2)
    for (x, z) in ((67, 5), (91, 5), (80, 14), (66, 12), (89, 20), (72, 19), (90, 11)):
        for i in range(4):
            for y in (S, S + 1):
                W.set(x + i, y, z, D + "beton_banche")
    for x in range(82, 92):
        for y in range(S, S + 3):
            W.set(x, y, 3, M + "bricks")
    for z in range(3, 8):
        for y in range(S, S + 3):
            W.set(82, y, z, M + "bricks")
    for x in (84, 88):
        W.set(x, S + 1, 3, None)
        W.set(x + 1, S + 1, 3, None)
    W.clear(82, S, 6, 82, S + 1, 6)
    W.set(82, S + 1, 4, None)
    for z in (6, 9):
        for y in range(S, S + 3):
            W.set(70, y, z, M + "iron_bars")
    for z in (7, 8):
        W.set(70, S + 2, z, M + "iron_bars")
    for z in range(4, 9):
        W.set(64, S, z, tronc(M + "stripped_spruce_log", "z"))
        if z % 2:
            W.set(64, S + 1, z, tronc(M + "spruce_log", "z"))


def box_vehicule(x0, z0, porte, contenu=None, graine=0):
    """Vehicle box 8 x 11 x 5 (brick, flat roof), gate on the `porte` side (north/south), open with a vehicle or crates
    inside, or closed."""
    x1, z1 = x0 + 7, z0 + 10
    W.walls(x0, S, z0, x1, S + 4, z1, M + "bricks")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "smooth_stone")
    W.fill(x0, S + 5, z0, x1, S + 5, z1, M + "black_concrete")
    zp = z1 if porte == "south" else z0
    if contenu is None:
        W.fill(x0 + 1, S, zp, x1 - 1, S + 3, zp, M + ("lapis_block" if graine % 2 else "netherite_block"))
        return
    W.clear(x0 + 1, S, zp, x1 - 1, S + 3, zp)
    W.set(x0 + 4, S + 4, (z0 + z1) // 2, LUMIERE_F)
    if contenu == "camion":
        if porte == "south":
            KT.camion_conteneur(x0 + 4, z1 - 1, "south")
        else:
            KT.camion_conteneur(x0 + 3, z0 + 1, "north")
    elif contenu == "caisses":
        if porte == "south":
            caisses(x0 + 1, z0 + 1, ["2222", "11"])
        else:
            caisses(x0 + 1, z1 - 2, ["11", "2222"])
    elif contenu == "chenillette":
        KT.chenillette(x0 + 3, z0 + 4, porte)
    elif contenu == "btr":
        btr(x0 + 2, z0 + 2, axe="z")


def parc_vehicules():
    """Motor pool (x 178..217): two rows of boxes facing a yard with a tank under camouflage nets, an armoured
    carrier, a truck, tyres and drums."""
    devant = ["camion", None, "caisses", "chenillette", None]
    fond = [None, "btr", None, "caisses", "camion"]
    for i in range(5):
        box_vehicule(178 + 8 * i, 2, "south", devant[i], graine=i)
        box_vehicule(178 + 8 * i, 23, "north", fond[i], graine=i + 1)
    char(181, 15, axe="x")
    W.set(185, S + 4, 17, oriente(U + "filet_camouflage", "north"))
    W.set(182, S + 2, 17, oriente(U + "filet_camouflage", "north"))
    btr(196, 16, axe="x")
    camion(205, 14, "west")
    caisses(205, 19, ["21"])
    pneus(214, 20, 3)
    W.set(203, S, 20, FUT)
    W.set(204, S, 20, FUT)


def estacade(x0=159, z0=4):
    """Inspection ramp: two concrete tracks 2 high along x with ramps at both ends, a pit 1 deep between them."""
    for z in (z0, z0 + 3):
        for i in range(16):
            x = x0 + i
            if i in (0, 15):
                W.set(x, S, z, marche(M + "stone_brick", "east" if i == 0 else "west"))
            elif i in (1, 14):
                W.set(x, S, z, M + "polished_andesite")
                W.set(x, S + 1, z, marche(M + "stone_brick", "east" if i == 1 else "west"))
            else:
                W.set(x, S, z, M + "polished_andesite")
                W.set(x, S + 1, z, M + "light_gray_concrete")
    for x in range(x0 + 2, x0 + 14):
        for z in (z0 + 1, z0 + 2):
            W.set(x, G, z, None)
            W.set(x, G - 1, z, M + "smooth_stone")


def hangar_ouvert(x0=158, z0=11):
    """Open repair shed 17 x 10: posts, rusty flat roof at y S+5, back wall, a truck under repair, workbenches."""
    x1, z1 = x0 + 16, z0 + 9
    for x in range(x0, x1 + 1, 4):
        for z in (z0, z1):
            for y in range(S, S + 5):
                W.set(x, y, z, tronc(M + "stripped_spruce_log"))
    W.fill(x0, S + 5, z0, x1, S + 5, z1, D + "tole_toit_rouille")
    for x in range(x0, x1 + 1):
        W.set(x, S + 6, z0, dalle(D + "tole_toit_rouille_dalle", "bottom"))
    for z in range(z0 + 1, z1):
        for y in range(S, S + 3):
            W.set(x1, y, z, M + "pink_wool")
    camion(x0 + 2, z0 + 3, "east")
    caisses(x0 + 11, z0 + 1, ["21", "1"])
    W.set(x1 - 1, S, z0 + 6, M + "crafting_table")
    W.set(x1 - 1, S, z0 + 7, oriente(M + "anvil", "north"))
    pneus(x0 + 12, z0 + 7, 2)
    W.set(x0 + 8, S + 4, z0 + 4, LUMIERE_F)


def cantine(x0=126, z0=2):
    """Canteen: 29 x 12 one-storey block of white panels, ribbon window and porch on the south, kitchen at the east end
    with a loading door, tables and a serving counter inside, ventilation boxes on the roof."""
    x1, z1 = x0 + 28, z0 + 11
    for y in range(S, S + 5):
        mat = M + "andesite" if y == S else (M + "blue_concrete" if y == S + 4 else M + "yellow_concrete")
        for x in range(x0, x1 + 1):
            W.set(x, y, z0, mat)
            W.set(x, y, z1, mat)
        for z in range(z0 + 1, z1):
            W.set(x0, y, z, mat)
            W.set(x1, y, z, mat)
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "white_concrete")
    W.fill(x0, S + 5, z0, x1, S + 5, z1, M + "black_concrete")
    for x in range(x0, x1 + 1):
        W.set(x, S + 6, z0, M + "smooth_stone")
        W.set(x, S + 6, z1, M + "smooth_stone")
    for z in range(z0, z1 + 1):
        W.set(x0, S + 6, z, M + "smooth_stone")
        W.set(x1, S + 6, z, M + "smooth_stone")
    xk = x1 - 8
    for x in range(x0 + 2, xk - 1):
        if (x - x0) % 4 != 1 and not (x0 + 9 <= x <= x0 + 10):
            for y in (S + 1, S + 2):
                W.set(x, y, z1, M + "glass_pane" if h(x, y) % 5 else None)
    W.clear(x0 + 9, S, z1, x0 + 10, S + 2, z1)
    for x in range(x0 + 7, x0 + 13):
        W.set(x, S + 3, z1 + 1, dalle(M + "smooth_stone", "top"))
        W.set(x, S + 3, z1 + 2, dalle(M + "smooth_stone", "top"))
    for x in (x0 + 7, x0 + 12):
        for y in range(S, S + 3):
            W.set(x, y, z1 + 2, M + "stone_brick_wall")
    for y in range(S, S + 5):
        for z in range(z0 + 1, z1):
            W.set(xk, y, z, M + "yellow_concrete")
    W.clear(xk, S, z0 + 5, xk, S + 1, z0 + 6)
    for z in range(z0 + 2, z1 - 1):
        if z not in (z0 + 5, z0 + 6):
            W.set(xk - 1, S, z, oriente(D + "comptoir_caisse", "west"))
    W.clear(x1, S, z0 + 4, x1, S + 2, z0 + 6)
    for x in range(x0 + 3, xk - 3, 4):
        for z in (z0 + 3, z0 + 7):
            W.set(x, S, z, oriente(D + "table_metal", "south"))
            W.set(x + 1, S, z, oriente(D + "table_metal", "south"))
            W.set(x, S, z + 1, oriente(D + "chaise_metal", "north"))
    caisses(x1 - 4, z0 + 2, ["21", "1"])
    W.set(x1 - 2, S, z1 - 2, oriente(C + "arkaniaz_oil_drum", "north"))
    for x in range(x0 + 4, x1, 8):
        W.set(x, S + 4, z0 + 6, LUMIERE_F)
    for (x, z) in ((x0 + 6, z0 + 4), (x0 + 18, z0 + 4), (x1 - 4, z0 + 6)):
        W.set(x, S + 6, z, D + "groupe_ventilation")


def tour_guet(x0, z0, sens_projo="north"):
    """Watchtower: 4 log posts, platform at y S+5 (stand S+6) with half walls, roof, straight wooden stair from the
    south ending level with the platform, a searchlight."""
    for (x, z) in ((x0, z0), (x0 + 3, z0), (x0, z0 + 3), (x0 + 3, z0 + 3)):
        for y in range(S, S + 8):
            W.set(x, y, z, tronc(M + "spruce_log"))
    W.fill(x0, S + 5, z0, x0 + 3, S + 5, z0 + 3, M + "spruce_planks")
    for x in range(x0, x0 + 4):
        for z in range(z0, z0 + 4):
            bord = x in (x0, x0 + 3) or z in (z0, z0 + 3)
            coin = x in (x0, x0 + 3) and z in (z0, z0 + 3)
            if bord and not coin:
                W.set(x, S + 6, z, M + "spruce_planks")
    W.set(x0 + 1, S + 6, z0 + 3, None)
    W.fill(x0 - 1, S + 8, z0 - 1, x0 + 4, S + 8, z0 + 4, dalle(D + "tole_toit_rouille_dalle", "bottom"))
    for i in range(6):
        z = z0 + 9 - i
        for y in range(S, S + i):
            W.set(x0 + 1, y, z, M + "spruce_planks")
        W.set(x0 + 1, S + i, z, marche(M + "spruce", "north"))
    projecteur(x0 + 2, S + 7, z0 if sens_projo == "north" else z0 + 3, sens_projo)


def bunker_terre(x0, z0, w, d):
    """Earth-covered store: concrete room (door to the south) under a stepped grass mound; one can walk up the mound
    (top stand S+5). Concrete retaining wall along the front."""
    x1, z1 = x0 + w - 1, z0 + d - 1
    W.walls(x0, S, z0, x1, S + 2, z1, M + "stone_bricks")
    W.fill(x0, S + 3, z0, x1, S + 3, z1, M + "smooth_stone")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "polished_andesite")
    for k in range(3):
        for x in range(x0 - 3 + k, x1 + 4 - k):
            for z in range(z0 - 3 + k, z1 + 1):
                if not (x0 <= x <= x1 and z0 <= z <= z1):
                    W.set(x, S + k, z, M + "dirt")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1):
            W.set(x, S + 4, z, M + "dirt")
    for x in range(x0 - 3, x1 + 4):
        for z in range(z0 - 3, z1 + 1):
            for y in range(S, S + 5):
                if W.get(x, y, z) == M + "dirt" and W.get(x, y + 1, z) is None:
                    W.set(x, y, z, M + "grass_block")
    for x in range(x0 - 3, x1 + 4):
        haut = min(4, 1 + min(x - (x0 - 3), (x1 + 3) - x))
        for y in range(S, S + haut):
            W.set(x, y, z1, M + "stone_bricks")
        W.set(x, S + haut, z1, dalle(M + "stone_brick", "bottom"))
    xm = (x0 + x1) // 2
    W.clear(xm, S, z1, xm + 1, S + 1, z1)
    W.set(xm - 1, S + 2, z1 + 1, oriente(D + "panneau_danger", "south"))
    caisses(x0 + 1, z0 + 1, ["22", "2"])
    W.set(x1 - 1, S, z0 + 1, oriente(C + "arkaniaz_crate", "south"))
    W.set(x1 - 2, S, z0 + 1, oriente(C + "arkaniaz_crate", "south"))
    W.set(xm, S + 2, z0 + 3, LUMIERE_F)


def depot_munitions():
    """North-west corner: three earth-covered stores, a watchtower, birches."""
    bunker_terre(6, 5, 9, 8)
    bunker_terre(6, 20, 9, 7)
    bunker_terre(22, 6, 7, 9)
    tour_guet(22, 18)
    for (x, z) in ((19, 30), (5, 31), (27, 33)):
        arbre_bouleau(x, z, 7 + x % 3)
    caisses(17, 16, ["21"])
    caisses(4, 14, ["22", "2"])


def zenitka(cx, cz):
    """Anti-aircraft gun pit: ring of sandbags (2 high, open to the south-east) around a twin gun."""
    for x in range(cx - 4, cx + 5):
        for z in range(cz - 4, cz + 5):
            d = math.hypot(x - cx, z - cz)
            if 3.0 <= d <= 4.2 and not (x > cx and z > cz):
                W.set(x, S, z, SAC)
                W.set(x, S + 1, z, SAC if (x + z) % 2 else dalle(D + "bache_kaki_dalle", "bottom"))
    W.set(cx, S, cz, M + "smooth_stone")
    W.set(cx, S + 1, cz, M + "green_concrete")
    W.set(cx - 1, S + 1, cz, M + "green_concrete")
    W.set(cx + 1, S + 1, cz, M + "green_concrete")
    for i in range(2, 5):
        W.set(cx + i, S + 1, cz - 1, etat(M + "polished_basalt", axis="x"))
        W.set(cx + i, S + 1, cz + 1, etat(M + "polished_basalt", axis="x"))
    W.set(cx + 1, S + 1, cz - 1, M + "green_concrete")
    W.set(cx + 1, S + 1, cz + 1, M + "green_concrete")
    caisses(cx - 2, cz + 2, ["1"])


def kotelnaya(x0=6, z0=50):
    """Boiler house of the garrison: brick hall 14 x 10 (6 high) with tall windows, two boilers and their flue inside,
    a door and a coal gate, a brick chimney 3 x 3 rising to y S+21 beside it, a coal heap in the yard."""
    x1, z1 = x0 + 13, z0 + 9
    W.walls(x0, S, z0, x1, S + 5, z1, M + "bricks")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "smooth_stone")
    W.fill(x0, S + 6, z0, x1, S + 6, z1, M + "black_concrete")
    for x in range(x0, x1 + 1):
        W.set(x, S + 7, z0, M + "stone_bricks")
        W.set(x, S + 7, z1, M + "stone_bricks")
    for z in range(z0, z1 + 1):
        W.set(x0, S + 7, z, M + "stone_bricks")
        W.set(x1, S + 7, z, M + "stone_bricks")
    for z in (z0 + 2, z0 + 3, z0 + 6, z0 + 7):
        for y in range(S + 2, S + 5):
            W.set(x1, y, z, M + "glass_pane" if h(z, y) % 3 else M + "iron_bars")
    for x in (x0 + 3, x0 + 4, x0 + 9, x0 + 10):
        for y in range(S + 2, S + 5):
            W.set(x, y, z1, M + "glass_pane" if h(x, y) % 3 else None)
    W.clear(x1, S, z0 + 4, x1, S + 2, z0 + 5)
    W.clear(x0 + 5, S, z0, x0 + 7, S + 2, z0)
    for zb in (z0 + 2, z0 + 6):
        for x in range(x0 + 2, x0 + 5):
            for z in (zb, zb + 1):
                for y in range(S, S + 3):
                    W.set(x, y, z, M + "netherite_block" if y < S + 2 else M + "lapis_block")
        W.set(x0 + 1, S + 3, zb, etat(M + "polished_basalt", axis="x"))
    for z in range(z0 + 2, z0 + 8):
        W.set(x0 + 1, S + 4, z, etat(M + "polished_basalt", axis="z"))
    W.set(x0 - 1, S + 4, z0 + 4, M + "bricks")
    W.set(x0 - 1, S + 3, z0 + 4, M + "bricks")
    for y in range(S, S + 22):
        for x in range(x0 - 4, x0 - 1):
            for z in range(z0 + 3, z0 + 6):
                creux = y >= S + 20 and x == x0 - 3 and z == z0 + 4
                if not creux:
                    W.set(x, y, z, M + ("stone_bricks" if (y - S) % 6 == 5 else "bricks"))
    KT.tas(x1 + 2, z0 + 1, ["0122", "1233", "1221"], mat=M + "coal_block")
    for (x, z) in ((x0 + 6, z0 + 4), (x0 + 10, z0 + 5)):
        W.set(x, S + 5, z, LUMIERE_F)


def bande_ouest():
    """West strip of the garrison (x 2..29, z 36..63): watchtower, boiler house, anti-aircraft pit, wire, trees."""
    tour_guet(5, 40)
    zenitka(22, 44)
    kotelnaya()
    caisses(24, 56, ["22", "1"])
    barbeles(3, 62, 8, 62)
    for (x, z) in ((27, 38), (28, 61)):
        arbre_bouleau(x, z, 7 + z % 3)


def tribune(x0=62, z0=36):
    """Review stand in front of the barracks: stepped platform (stand S+2), back wall with a red banner and a star,
    canopy on two posts."""
    x1 = x0 + 11
    for x in range(x0, x1 + 1):
        for z in range(z0, z0 + 3):
            W.set(x, S, z, M + "smooth_stone")
        W.set(x, S + 1, z0, M + "smooth_stone")
        W.set(x, S + 1, z0 + 1, M + "smooth_stone")
        W.set(x, S + 1, z0 + 2, marche(M + "stone_brick", "north"))
        W.set(x, S, z0 + 3, marche(M + "stone_brick", "north"))
        for y in range(S + 2, S + 5):
            W.set(x, y, z0, M + "red_wool" if y == S + 3 and x0 + 2 <= x <= x1 - 2 else M + "smooth_stone")
    W.set((x0 + x1) // 2, S + 5, z0, M + "red_concrete")
    W.set((x0 + x1) // 2 + 1, S + 5, z0, M + "red_concrete")
    for x in (x0, x1):
        for y in range(S + 2, S + 5):
            W.set(x, y, z0 + 2, M + "stone_brick_wall")
    for x in range(x0, x1 + 1):
        for z in (z0 + 1, z0 + 2):
            W.set(x, S + 5, z, dalle(M + "smooth_stone", "bottom"))


def place_armes():
    """Parade ground x 30..95, z 36..58: painted asphalt, review stand, trucks and carriers parked in columns,
    sandbag nests, boards, flagpoles."""
    for x in range(30, 96):
        for z in range(36, 59):
            W.set(x, G, z, M + ("gray_concrete" if h(x, z, 2) % 9 else "black_concrete"))
            if z in (44, 52) and x % 4 < 2:
                W.set(x, G, z, D + "marquage_blanc")
    tribune()
    for (x0, z0) in ((34, 45), (34, 53), (78, 41), (85, 49)):
        camion(x0, z0, "east")
    btr(44, 40, axe="x")
    btr(66, 54, axe="x")
    sacs(56, 46, 59, 46, 2)
    sacs(56, 47, 56, 48, 2)
    sacs(73, 49, 76, 49, 2)
    sacs(76, 47, 76, 48, 2)
    sacs(44, 49, 46, 49, 2)
    sacs(63, 45, 66, 45, 2)
    sacs(66, 50, 69, 50, 2)
    sacs(69, 51, 69, 52, 2)
    sacs(57, 56, 58, 56, 2)
    stend(50, 36, "x", 5)
    stend(90, 56, "z", 3)
    caisses(28, 39, ["22"])
    caisses(56, 39, ["22"])
    caisses(88, 39, ["2"])
    caisses(93, 40, ["22"])
    caisses(54, 53, ["22"])
    caisses(70, 57, ["22", "22"])
    caisses(81, 56, ["2"])
    caisses(96, 50, ["2"])
    camion(50, 60, "east")
    camion(78, 60, "west")
    caisses(45, 59, ["22"])
    caisses(85, 62, ["2", "2"])
    caisses(92, 58, ["2", "2"])
    caisses(60, 63, ["2"])
    caisses(68, 59, ["2"])
    caisses(20, 37, ["2"])
    caisses(85, 36, ["2"])
    caisses(62, 44, ["2"])
    caisses(90, 43, ["2"])
    camion(61, 47, "east")
    for (x, z) in ((47, 35), (70, 34), (90, 35), (14, 35)):
        arbre_bouleau(x, z, 7 + x % 3)
    for x in (31, 94):
        for y in range(S, S + 8):
            W.set(x, y, 37, M + "iron_bars")
        for y in (S + 5, S + 6):
            W.set(x, y, 38, M + "red_wool")
    beton_bloc(48, 57, "x", 3)
    beton_bloc(84, 44, "z", 2)
    herisson(62, 58)


def allee():
    """Avenue from the hangar to the main gate: white kerbs, birch alley, boards, the carrier on its plinth, the
    checkpoint booth."""
    for z in range(21, MUR_Z):
        W.set(100, G, z, M + "white_concrete")
        W.set(119, G, z, M + "white_concrete")
    W.fill(106, S, 37, 114, S + 1, 42, M + "polished_andesite")
    for x in range(106, 115):
        W.set(x, S + 1, 37, M + "polished_granite")
        W.set(x, S + 1, 42, M + "polished_granite")
    btr(107, 38, axe="x", y=S + 2)
    stend(101, 25, "x", 5)
    stend(114, 29, "x", 5)
    stend(114, 49, "x", 5)
    stend(125, 53, "z", 5)
    caisses(100, 44, ["2", "2", "2", "2"])
    caisses(104, 50, ["22"])
    caisses(112, 56, ["22"])
    caisses(124, 37, ["2", "2"])
    barrage(109, 60, 110, 60)
    caisses(120, 36, ["2"])
    caisses(120, 43, ["2"])
    for z in range(26, 58, 9):
        arbre_bouleau(97, z, 7 + z % 3)
        arbre_bouleau(122, z + 4, 7 + (z + 1) % 3)
    for x in (102, 117):
        lampadaire(x, 33, "south")
    sacs(103, 55, 106, 55, 2)
    sacs(113, 52, 116, 52, 2)
    camion(118, 33, "west")
    stend(95, 28, "z", 4)
    caisses(98, 21, ["22"])
    # checkpoint booth (KPP) east of the main gate
    x0, z0 = 119, 58
    for x in range(x0, x0 + 4):
        for z in range(z0, z0 + 4):
            if x in (x0, x0 + 3) or z in (z0, z0 + 3):
                for y in range(S, S + 3):
                    vitre = y == S + 1 and (x == x0 or z == z0)
                    W.set(x, y, z, M + "glass_pane" if vitre else M + "lime_concrete")
    W.fill(x0 - 1, S + 3, z0 - 1, x0 + 4, S + 3, z0 + 4, M + "black_concrete")
    W.clear(x0 + 3, S, z0 + 1, x0 + 3, S + 1, z0 + 1)
    W.set(x0 + 1, S, z0 + 2, oriente(D + "radio_militaire", "west"))
    W.set(x0 + 2, S + 2, z0 + 1, LUMIERE_F)


def place_qg():
    """Square in front of the headquarters (x 126..175, z 33..63): stele with a star, a tank, jeeps, a smoking
    gazebo, boards, sandbags and hedgehogs by the breach."""
    W.fill(149, S, 46, 155, S, 48, M + "polished_andesite")
    for x in (151, 152, 153):
        for y in range(S + 1, S + 6):
            W.set(x, y, 47, M + "smooth_stone" if x != 152 else etat(M + "quartz_pillar", axis="y"))
    W.set(152, S + 6, 47, M + "red_concrete")
    char(128, 51, axe="x")
    voiture(156, 50, "north", U + "epave_rouge")
    voiture(170, 52, "north", U + "epave_brulee")
    voiture(134, 41, "south", U + "epave_bleue")
    for i in range(5):
        garage(126 + 4 * i, 58, "north", ouvert=i in (1, 3), graine=i + 2)
    medpunkt()
    sacs(141, 48, 143, 48, 2)
    caisses(148, 53, ["2"])
    camion(138, 44, "east")
    caisses(140, 38, ["22"])
    caisses(160, 37, ["22", "22"])
    caisses(128, 44, ["2"])
    caisses(150, 51, ["2", "2"])
    caisses(150, 55, ["2", "2"])
    caisses(166, 49, ["2", "2"])
    caisses(153, 58, ["2"])
    caisses(150, 43, ["2"])
    caisses(130, 50, ["2"])
    for (x, z) in ((132, 35), (150, 36), (172, 35), (196, 35), (175, 36)):
        arbre_bouleau(x, z, 7 + x % 3)
    gx, gz = 160, 52
    for (x, z) in ((gx, gz), (gx + 4, gz), (gx, gz + 4), (gx + 4, gz + 4)):
        for y in range(S, S + 3):
            W.set(x, y, z, M + "spruce_fence")
    W.fill(gx - 1, S + 3, gz - 1, gx + 5, S + 3, gz + 5, dalle(D + "tole_toit_rouille_dalle", "bottom"))
    for x in range(gx + 1, gx + 4):
        W.set(x, S, gz, oriente(D + "banc_parc", "south"))
        W.set(x, S, gz + 4, oriente(D + "banc_parc", "north"))
    W.set(gx + 2, S, gz + 2, FUT)
    stend(128, 37, "x", 5)
    stend(164, 34, "x", 5)
    sacs(147, 59, 151, 59, 2)
    sacs(156, 57, 158, 57, 2)
    beton_bloc(138, 47, "z", 2)
    herisson(158, 62)
    arbre_bouleau(172, 58, 7)
    caisses(165, 60, ["21"])


def medpunkt(x0=160, z0=39):
    """Medical post: one-storey block 14 x 9 of white plaster with a grey band, door and porch on the west, windows,
    camp beds and cabinets inside, flat roof with a low parapet."""
    x1, z1 = x0 + 13, z0 + 8
    for y in range(S, S + 4):
        mat = M + "stone" if y == S else (M + "blue_concrete" if y == S + 3 else D + "enduit_blanc")
        for x in range(x0, x1 + 1):
            W.set(x, y, z0, mat)
            W.set(x, y, z1, mat)
        for z in range(z0 + 1, z1):
            W.set(x0, y, z, mat)
            W.set(x1, y, z, mat)
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "white_concrete")
    W.fill(x0, S + 4, z0, x1, S + 4, z1, M + "black_concrete")
    for x in range(x0, x1 + 1):
        W.set(x, S + 5, z0, dalle(M + "smooth_stone", "bottom"))
        W.set(x, S + 5, z1, dalle(M + "smooth_stone", "bottom"))
    for (xa, z) in ((x0 + 2, z0), (x0 + 9, z0), (x0 + 4, z1), (x0 + 11, z1)):
        W.set(xa, S + 1, z, M + "glass_pane" if h(xa, z) % 3 else None)
        W.set(xa + 1, S + 1, z, M + "glass_pane")
        W.set(xa, S + 2, z, M + "glass_pane")
        W.set(xa + 1, S + 2, z, M + "glass_pane")
    W.clear(x0, S, z0 + 3, x0, S + 2, z0 + 4)
    for z in range(z0 + 2, z0 + 6):
        W.set(x0 - 1, S + 3, z, dalle(M + "smooth_stone", "top"))
    for y in range(S, S + 4):
        W.set(x0 + 7, y, z0 + 1, D + "enduit_blanc")
        W.set(x0 + 7, y, z1 - 1, D + "enduit_blanc")
    for z in range(z0 + 2, z1 - 1):
        if z not in (z0 + 4,):
            for y in range(S, S + 4):
                W.set(x0 + 7, y, z, D + "enduit_blanc")
    for x in (x0 + 9, x0 + 11):
        W.set(x, S, z0 + 1, oriente(D + "lit_camp_militaire", "south"))
        W.set(x, S, z1 - 1, oriente(D + "lit_camp_militaire", "north"))
    W.set(x0 + 2, S, z0 + 1, oriente(D + "table_metal", "south"))
    W.set(x0 + 3, S, z0 + 1, oriente(D + "chaise_metal", "west"))
    W.set(x0 + 1, S, z1 - 1, oriente(C + "arkaniaz_crate", "east"))
    W.set(x0 + 5, S, z1 - 1, FUT)
    W.set(x0 + 4, S + 3, z0 + 4, LUMIERE_F)
    W.set(x0 + 10, S + 3, z0 + 4, LUMIERE_F)


def depot_carburant():
    """Fuel depot (x 176..217, z 36..63): three rusty round tanks inside a low concrete bund, a pump island with a
    canopy, a fuel truck, a watchtower."""
    for (cx, cz) in ((186.0, 46.0), (200.0, 46.0)):
        KT.citerne_ronde(cx, cz, 4.0, 6, mat=M + "light_gray_concrete", cercle=M + "lime_terracotta")
    KT.citerne_ronde(193.0, 56.0, 3.5, 5, mat=M + "light_gray_concrete", cercle=M + "lime_terracotta")
    for y, mat in ((S, D + "beton_banche"), (S + 1, dalle(D + "beton_banche_dalle", "bottom"))):
        for x in range(179, 209):
            for z in (38, 62):
                if not 192 <= x <= 195:
                    W.set(x, y, z, mat)
        for z in range(39, 62):
            if not 48 <= z <= 51:
                W.set(179, y, z, mat)
                W.set(208, y, z, mat)
    for x in (181, 184):
        W.set(x, S, 56, etat(D + "pompe_essence", facing="east", partie="bas"))
        W.set(x, S + 1, 56, etat(D + "pompe_essence", facing="east", partie="haut"))
    for (x, z) in ((180, 54), (185, 54), (180, 58), (185, 58)):
        for y in range(S, S + 3):
            W.set(x, y, z, M + "stone_brick_wall")
    W.fill(180, S + 3, 54, 185, S + 3, 58, M + "smooth_stone")
    camion(181, 59, "east")
    tour_guet(211, 40, "east")
    caisses(214, 55, ["21", "2"])
    caisses(200, 50, ["22", "22"])
    caisses(186, 52, ["2"])
    caisses(190, 37, ["2", "2"])
    caisses(200, 36, ["22"])
    W.set(176, S, 55, FUT)
    W.set(176, S + 1, 55, FUT)
    W.set(176, S, 56, FUT)
    W.set(176, S + 1, 56, FUT)
    caisses(176, 51, ["2"])
    W.set(205, S, 58, FUT)
    W.set(206, S, 58, FUT)
    W.set(205, S, 59, FUT)


def mur_garnison():
    """Garrison wall along z = MUR_Z: concrete panels 3 high between posts, three gates with pillars, red stars and
    green gate leaves swung open, two breaches with rubble, anti-tank hedgehogs and barbed wire outside."""
    for x in range(2, 218):
        if any(a <= x <= b for a, b in PORTES):
            continue
        breche = next(((a, b) for a, b in BRECHES if a <= x <= b), None)
        if breche:
            a, b = breche
            W.set(x, S, MUR_Z, etat(D + "gravats", taille="grand") if h(x, 3) % 2 else M + "cobblestone")
            if x in (a, b):
                W.set(x, S + 1, MUR_Z, M + "smooth_stone")
            for dz in (-1, 1):
                if h(x, dz + 5) % 3 == 0:
                    W.set(x, S, MUR_Z + dz, etat(D + "gravats", taille="moyen"))
            continue
        poteau = x % 4 == 0
        for y in range(S, S + 3):
            W.set(x, y, MUR_Z, M + ("stone_bricks" if poteau else ("andesite" if y == S else "smooth_stone")))
        if poteau:
            W.set(x, S + 3, MUR_Z, dalle(M + "stone_brick", "bottom"))
    for a, b in PORTES:
        for x in (a - 1, b + 1):
            for y in range(S, S + 4):
                W.set(x, y, MUR_Z, M + "stone_bricks")
            W.set(x, S + 4, MUR_Z, M + "red_concrete")
        for x in (a, b):
            for z in range(MUR_Z - 5, MUR_Z):
                for y in (S, S + 1, S + 2):
                    etoile = y == S + 1 and z == MUR_Z - 3
                    W.set(x, y, z, M + ("red_concrete" if etoile else "green_concrete"))
    for (x, z) in ((40, 67), (44, 66), (60, 68), (76, 67), (126, 67), (140, 68), (161, 66), (174, 68), (209, 66),
                   (24, 68)):
        herisson(x, z)
    barbeles(84, 66, 88, 66)
    barbeles(128, 66, 133, 66)
    barbeles(192, 69, 197, 69)
    barbeles(14, 69, 19, 69)


# ================================================================================================ village
TOITS_IZBA = [
    (M + "dark_oak", M + "dark_oak"),
    (D + "tole_toit_rouille_escalier", D + "tole_toit_rouille_dalle"),
    (M + "spruce", M + "spruce"),
    (D + "tole_toit_bleue_escalier", D + "tole_toit_bleue_dalle"),
]


def izba(x0, z0, porte="south", graine=0):
    """Log house 7 x 9 (ridge along z): porch and door on the `porte` gable, windows with blue shutters or broken,
    white stove with its chimney through the roof, stair to an attic one can stand in (stand S+5) with an open window
    in each gable. Built in a local frame (u across 0..6, v along 0..8, door at v = 8), turned for a north door."""
    if porte == "south":
        rep = F.Repere(x0, z0, "+x", "+z")
    else:
        rep = F.Repere(x0 + 6, z0 + 8, "-x", "-z")
    l = F.Local(W, rep)
    pu, mu, pv, mv = l.d("+u"), l.d("-u"), l.d("+v"), l.d("-v")
    axe_u = "x" if pu in ("east", "west") else "z"
    axe_v = "z" if axe_u == "x" else "x"
    log_u, log_v = tronc(M + "spruce_log", axe_u), tronc(M + "spruce_log", axe_v)
    coin = tronc(M + "stripped_spruce_log", "y")
    for y in range(S, S + 5):
        for u in range(7):
            for v in (0, 8):
                l.set(u, y, v, coin if u in (0, 6) else log_u)
        for v in range(1, 8):
            for u in (0, 6):
                l.set(u, y, v, log_v)
    l.fill(1, G, 1, 5, G, 7, M + "spruce_planks")
    l.fill(1, S + 4, 1, 5, S + 4, 7, M + "spruce_planks")
    esc, dal = TOITS_IZBA[graine % len(TOITS_IZBA)]
    for k in range(5):
        y = S + 4 + k
        ua, ub = k - 1, 7 - k
        for v in range(-1, 10):
            if ua == ub:
                l.set(ua, y, v, dalle(dal, "bottom"))
            else:
                l.set(ua, y, v, marche(esc, pu))
                l.set(ub, y, v, marche(esc, mu))
        if k:
            for v in (0, 8):
                for u in range(ua + 1, ub):
                    l.set(u, y, v, M + "spruce_planks")
    l.clear(3, S, 8, 3, S + 1, 8)
    for u in (2, 4):
        for y in range(S, S + 3):
            l.set(u, y, 9, M + "spruce_fence")
    for u in (2, 3, 4):
        l.set(u, S + 3, 9, dalle(M + "spruce", "top"))
    for (u, v, dehors) in ((0, 3, mu), (0, 6, mu), (6, 2, pu), (6, 5, pu), (5, 8, pv)):
        if h(x0, z0, u, v) % 3 == 0:
            l.set(u, S + 1, v, None)
            l.set(u, S + 2, v, None)
        else:
            l.set(u, S + 1, v, etat(D + "fenetre_volets", facing=dehors, partie="bas"))
            l.set(u, S + 2, v, etat(D + "fenetre_volets", facing=dehors, partie="haut"))
    for v in (0, 8):
        l.set(3, S + 5, v, None)
        l.set(3, S + 6, v, None)
    for u in (1, 2):
        for v in (1, 2):
            for y in (S, S + 1):
                l.set(u, y, v, M + "white_terracotta")
    for y in range(S + 2, S + 9):
        l.set(1, y, 1, M + "bricks")
    for i in range(5):
        v = 6 - i
        for y in range(S, S + i):
            l.set(4, y, v, M + "spruce_planks")
        l.set(4, S + i, v, marche(M + "spruce", mv))
    for v in (5, 4, 3):
        l.set(4, S + 4, v, None)
    l.set(2, S, 5, oriente(D + "table_rustique", pu))
    l.set(1, S, 6, oriente(C + "arkaniaz_bed", pv))
    l.set(5, S, 1, FUT)
    l.set(3, S + 3, 4, LUMIERE_F)
    l.set(3, S + 6, 5, LUMIERE_F)
    if graine % 2:
        l.set(2, S + 5, 6, etat(M + "hay_block", axis="y"))
    else:
        l.set(3, S + 5, 1, oriente(C + "arkaniaz_crate", pv))


def jardin(x0, z0, x1, z1, ouvertures=()):
    """Garden: picket fence with openings, furrows."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            bord = x in (x0, x1) or z in (z0, z1)
            if bord and (x, z) not in ouvertures:
                W.set(x, S, z, M + "spruce_fence")
            elif not bord and (x - x0) % 2:
                W.set(x, G, z, M + "rooted_dirt")


def puits(x, z):
    """Village well: stone ring 3 x 3, two posts, small roof, chain."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            if (dx, dz) != (0, 0):
                W.set(x + dx, S, z + dz, M + "cobblestone")
    W.set(x, G, z, M + "mud")
    for dz in (-1, 1):
        W.set(x, S + 1, z + dz, M + "spruce_fence")
        W.set(x, S + 2, z + dz, M + "spruce_fence")
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            W.set(x + dx, S + 3, z + dz, dalle(M + "dark_oak", "bottom"))
    W.set(x, S + 2, z, etat(M + "chain", axis="y", waterlogged="false"))


def meule(x, z):
    """Haystack 3 x 3 x 3, rounded."""
    for dx in (-1, 0, 1):
        for dz in (-1, 0, 1):
            W.set(x + dx, S, z + dz, etat(M + "hay_block", axis="y"))
            if abs(dx) + abs(dz) < 2:
                W.set(x + dx, S + 1, z + dz, etat(M + "hay_block", axis="y"))
    W.set(x, S + 2, z, etat(M + "hay_block", axis="y"))


def tracteur(x0, z0):
    """Old red tractor along z: big rear wheels, bonnet, cab frame with a roof."""
    for x in (x0, x0 + 2):
        W.set(x, S, z0, etat(D + "pneu", axis="x"))
        W.set(x, S, z0 + 3, etat(D + "pneu", axis="x"))
        W.set(x, S + 1, z0 + 3, etat(D + "pneu", axis="x"))
    for z in range(z0, z0 + 4):
        W.set(x0 + 1, S, z, M + "red_terracotta" if z < z0 + 3 else M + "black_concrete")
        W.set(x0 + 1, S + 1, z, M + "red_concrete" if z < z0 + 2 else M + "black_concrete")
    W.set(x0 + 1, S + 2, z0 + 2, M + "iron_bars")
    W.set(x0 + 1, S + 2, z0 + 3, M + "iron_bars")
    W.set(x0 + 1, S + 3, z0 + 2, dalle(M + "smooth_stone", "bottom"))
    W.set(x0 + 1, S + 3, z0 + 3, dalle(M + "smooth_stone", "bottom"))


def remorque_foin(x0, z0):
    """Farm trailer along x loaded with hay bales, on two tyres, tow bar to the west."""
    for x in range(x0, x0 + 4):
        for z in (z0, z0 + 1):
            W.set(x, S, z, etat(D + "pneu", axis="z") if x == x0 + 1 else M + "spruce_planks")
            W.set(x, S + 1, z, etat(M + "hay_block", axis="x"))
            if (x + z) % 2:
                W.set(x, S + 2, z, etat(M + "hay_block", axis="x"))
    W.set(x0 - 1, S, z0, M + "spruce_fence")


def etable(x0=4, z0=74):
    """Kolkhoz cowshed 38 x 12: brick base, grey board walls, long pitched roof (ridge along x), doors at both ends and a
    passage across where the west lane goes (x 33..35), stalls and a feeding alley inside."""
    x1, z1 = x0 + 37, z0 + 11
    W.walls(x0, S, z0, x1, S + 1, z1, M + "bricks")
    W.walls(x0, S + 2, z0, x1, S + 4, z1, M + "gold_block")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "coarse_dirt")
    for k in range(6):
        y = S + 5 + k
        for x in range(x0 - 1, x1 + 2):
            W.set(x, y, z0 + k, marche(D + "tole_toit_rouille_escalier", "south"))
            W.set(x, y, z1 - k, marche(D + "tole_toit_rouille_escalier", "north"))
        for x in (x0, x1):
            for z in range(z0 + k + 1, z1 - k):
                W.set(x, y, z, M + "gold_block")
    W.clear(x0, S, z0 + 4, x0, S + 2, z0 + 7)
    W.clear(x1, S, z0 + 4, x1, S + 2, z0 + 7)
    passage = range(x0 + 29, x0 + 32)
    for z in (z0, z1):
        W.clear(passage[0], S, z, passage[-1], S + 2, z)
    for x in range(x0 + 3, x1 - 1, 4):
        if x not in passage:
            W.set(x, S + 3, z0, None if h(x, 1) % 2 else M + "glass_pane")
            W.set(x, S + 3, z1, None if h(x, 2) % 2 else M + "glass_pane")
    for x in range(x0 + 2, x1 - 1, 3):
        if x0 + 28 <= x <= x0 + 32:
            continue
        for z in (z0 + 1, z0 + 2, z1 - 2, z1 - 1):
            W.set(x, S, z, M + "spruce_fence")
    for x in range(x0 + 1, x1):
        if x0 + 28 <= x <= x0 + 32:
            continue
        for z in (z0 + 3, z1 - 3):
            if x % 5:
                W.set(x, S, z, M + "spruce_fence")
    for x in (x0 + 7, x0 + 16, x0 + 24):
        W.set(x, S, z0 + 5, etat(M + "hay_block", axis="y"))
        W.set(x, S + 1, z0 + 5, etat(M + "hay_block", axis="y"))
        W.set(x + 1, S, z0 + 6, etat(M + "hay_block", axis="y"))
    remorque_foin(x0 + 11, z0 + 5)
    for (x, z) in ((passage[0], z0 + 3), (passage[-1], z0 + 7)):
        W.set(x, S, z, etat(M + "hay_block", axis="y"))
        W.set(x, S + 1, z, etat(M + "hay_block", axis="y"))
    for x in range(x0 + 5, x1 - 2, 8):
        W.set(x, S + 4, z0 + 6, LUMIERE_F)


def silo_tranchee(x0=8, z0=91):
    """Silage trench: two concrete walls 3 high and 22 long, a heap of silage between them, a tractor."""
    for x in range(x0, x0 + 22):
        for z in (z0, z0 + 6):
            for y in range(S, S + 3):
                W.set(x, y, z, D + "beton_banche")
    for x in range(x0 + 4, x0 + 16):
        for z in range(z0 + 1, z0 + 6):
            haut = 2 if (2 <= z - z0 <= 4 and x0 + 6 <= x <= x0 + 13) else 1
            for y in range(S, S + haut):
                W.set(x, y, z, M + "moss_block" if y == S + haut - 1 else M + "hay_block")
    tracteur(x0 + 18, z0 + 1)


def chateau_eau(cx=17.5, cz=103.5):
    """Kolkhoz water tower: brick shaft, steel tank on top, conical roof. A landmark, closed."""
    for y in range(S, S + 10):
        for x in range(int(cx) - 4, int(cx) + 5):
            for z in range(int(cz) - 4, int(cz) + 5):
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                if 1.5 <= d <= 2.6:
                    W.set(x, y, z, M + "bricks" if (y - S) % 5 else M + "stone_bricks")
    for y in range(S + 10, S + 16):
        for x in range(int(cx) - 5, int(cx) + 6):
            for z in range(int(cz) - 5, int(cz) + 6):
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                if d <= 3.6 and (y in (S + 10, S + 15) or d >= 2.6):
                    W.set(x, y, z, M + "netherite_block" if y in (S + 10, S + 13) else M + "lapis_block")
    for k, r in enumerate((2.6, 1.6, 0.8)):
        for x in range(int(cx) - 3, int(cx) + 4):
            for z in range(int(cz) - 3, int(cz) + 4):
                if math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= r:
                    W.set(x, S + 16 + k, z, M + "lapis_block" if k < 2 else M + "iron_bars")
    W.set(int(cx), S, int(cz) - 2, M + "netherite_block")
    W.set(int(cx), S + 1, int(cz) - 2, M + "netherite_block")


def ferme():
    """Kolkhoz (x 2..43): cowshed, silage trench, water tower, haystacks, a shed, a broken fence, trees."""
    etable()
    silo_tranchee()
    chateau_eau()
    for (x, z) in ((40, 101), (6, 101), (29, 106)):
        meule(x, z)
    cabane(38, 92, 2)
    bois_empile(3, 88, 4)
    for x in range(2, 31, 6):
        W.set(x, S, 72, M + "spruce_fence")
        W.set(x, S + 1, 72, M + "spruce_fence")
    for (x, z) in ((10, 70), (44, 104), (5, 106)):
        arbre_bouleau(x, z, 7 + z % 3)


def club(x0=96, z0=72):
    """Club (house of culture), 28 x 18, ochre plaster: a hall with a stage and rows of seats, emergency exits on
    the north beside the stage, a foyer with side doors, a gallery over the foyer (stand S+5) reached by two stairs,
    and a portico of six columns whose roof is a balcony (stand S+5) open from the gallery."""
    x1, z1 = x0 + 27, z0 + 17
    mur, mur2 = D + "enduit_ocre", D + "enduit_ocre_use"

    def mat(x, y, z):
        return M + "stone_bricks" if y == S else (mur2 if h(x, y, z) % 7 == 0 else mur)

    for y in range(S, S + 10):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, mat(x, y, z))
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                W.set(x, y, z, mat(x, y, z))
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "polished_andesite")
    W.fill(x0, S + 10, z0, x1, S + 10, z1, M + "black_concrete")
    for x in range(x0, x1 + 1):
        W.set(x, S + 11, z0, M + "white_terracotta")
        W.set(x, S + 11, z1, M + "white_terracotta")
    for z in range(z0, z1 + 1):
        W.set(x0, S + 11, z, M + "white_terracotta")
        W.set(x1, S + 11, z, M + "white_terracotta")
    xm = (x0 + x1) // 2          # 109: the middle pair is xm, xm + 1
    zf = z1 - 5                  # foyer wall
    # stage and curtain
    for x in range(x0 + 4, x1 - 3):
        for z in range(z0 + 1, z0 + 4):
            W.set(x, S, z, M + "dark_oak_planks")
        for y in range(S + 1, S + 7):
            W.set(x, y, z0 + 1, M + "red_wool")
    for z in range(z0 + 2, z0 + 4):
        W.set(x0 + 3, S, z, marche(M + "dark_oak", "east"))
        W.set(x1 - 3, S, z, marche(M + "dark_oak", "west"))
    W.set(xm, S + 1, z0 + 3, oriente(D + "tableau_ecole", "south"))
    # emergency exits on the north wall, beside the stage
    W.clear(x0 + 2, S, z0, x0 + 3, S + 2, z0)
    W.clear(x1 - 3, S, z0, x1 - 2, S + 2, z0)
    # rows of seats, central aisle
    for z in range(z0 + 6, zf - 1, 2):
        for x in range(x0 + 4, x1 - 3):
            if x not in (xm, xm + 1):
                W.set(x, S, z, marche(M + "dark_oak", "south"))
    # foyer wall with three doorways
    for x in range(x0 + 1, x1):
        for y in range(S, S + 4):
            W.set(x, y, zf, M + "white_wool")
    for xa in (x0 + 5, xm, x1 - 6):
        W.clear(xa, S, zf, xa + 1, S + 2, zf)
    # gallery over the foyer, railing along its north edge
    for x in range(x0 + 1, x1):
        for z in range(zf, z1):
            W.set(x, S + 4, z, M + "spruce_planks")
        W.set(x, S + 5, zf, garde_corps("north"))
    # two stairs in the foyer, each rising toward a side wall (5 steps, the last one level with the gallery)
    for (xs, sens, pas) in ((x0 + 5, "west", -1), (x1 - 5, "east", 1)):
        for i in range(5):
            x = xs + pas * i
            for z in (z1 - 2, z1 - 1):
                for y in range(S, S + i):
                    W.set(x, y, z, M + "stone_bricks")
                W.set(x, S + i, z, marche(M + "stone_brick", sens))
        for i in (1, 2, 3):
            for z in (z1 - 2, z1 - 1):
                W.set(xs + pas * i, S + 4, z, None)
    # side doors into the foyer, tall windows in the hall
    for x in (x0, x1):
        W.clear(x, S, zf + 1, x, S + 2, zf + 2)
        for z in range(z0 + 5, zf - 1, 4):
            for y in range(S + 2, S + 7):
                W.set(x, y, z, M + "glass_pane" if h(x, y, z) % 4 else None)
    # portico: six columns, roof = balcony at y S+4 with a parapet; doors below and from the gallery
    for x in range(x0 + 5, x1 - 4):
        for z in range(z1 + 1, z1 + 4):
            W.set(x, G, z, M + "smooth_stone")
            W.set(x, S + 4, z, M + "white_terracotta")
    for x in range(x0 + 5, x1 - 4, 3):
        for y in range(S, S + 4):
            W.set(x, y, z1 + 3, etat(M + "quartz_pillar", axis="y"))
    for x in range(x0 + 5, x1 - 4):
        W.set(x, S + 5, z1 + 3, M + "white_terracotta" if x % 2 else dalle(M + "smooth_stone", "bottom"))
    for z in (z1 + 1, z1 + 2):
        W.set(x0 + 5, S + 5, z, M + "white_terracotta")
        W.set(x1 - 5, S + 5, z, M + "white_terracotta")
    for x in (x0 + 4, x1 - 4):
        for z in range(z1 + 1, z1 + 4):
            W.set(x, S, z, M + "stone_bricks")
            W.set(x, S + 1, z, M + "stone_bricks")
            W.set(x, S + 2, z, dalle(M + "stone_brick", "bottom"))
    for xa in (xm - 5, xm, xm + 5):
        W.clear(xa, S, z1, xa + 1, S + 2, z1)
    for xa in (xm - 5, xm + 5):
        W.clear(xa, S + 5, z1, xa + 1, S + 6, z1)
    for x in (x0 + 2, x1 - 2):
        W.set(x, S + 6, z1, M + "glass_pane")
    for (x, z) in ((x0 + 8, z0 + 8), (x1 - 8, z0 + 8), (xm, z0 + 4)):
        W.set(x, S + 8, z, LUMIERE)
    for (x, z) in ((x0 + 8, z1 - 3), (x1 - 8, z1 - 3)):
        W.set(x, S + 3, z, LUMIERE)
    W.set(xm, S + 7, z1 - 2, LUMIERE_F)


def magasin(x0=128, z0=72):
    """Village shop ('Produkty'): brick with a white front, shop windows and a red sign board, counter and shelves,
    back room, kvass trailer in front."""
    x1, z1 = x0 + 13, z0 + 9
    W.walls(x0, S, z0, x1, S + 3, z1, M + "bricks")
    for x in range(x0, x1 + 1):
        for y in range(S, S + 4):
            W.set(x, y, z1, M + "white_terracotta" if y > S else M + "stone")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "white_concrete")
    W.fill(x0, S + 4, z0, x1, S + 4, z1, M + "black_concrete")
    for x in range(x0 + 1, x1):
        W.set(x, S + 4, z1 + 1, M + "red_concrete")
        W.set(x, S + 5, z1 + 1, M + "red_concrete" if x % 3 else M + "white_concrete")
    for xa in (x0 + 2, x0 + 8):
        for x in range(xa, xa + 4):
            W.set(x, S + 1, z1, M + "glass_pane" if h(x, 9) % 4 else None)
            W.set(x, S + 2, z1, M + "glass_pane")
    W.clear(x0 + 6, S, z1, x0 + 7, S + 2, z1)
    for x in (x0 + 6, x0 + 7):
        W.set(x, S, z1 + 1, dalle(M + "smooth_stone", "bottom"))
    for x in range(x0 + 2, x0 + 10):
        if x != x0 + 6:
            W.set(x, S, z0 + 4, oriente(D + "comptoir_caisse", "south"))
    for x in range(x0 + 1, x0 + 10):
        W.set(x, S, z0 + 1, oriente(D + "rayonnage_boutique", "south"))
    for z in range(z0 + 1, z1):
        for y in range(S, S + 4):
            W.set(x1 - 3, y, z, M + "bricks")
    W.clear(x1 - 3, S, z0 + 6, x1 - 3, S + 1, z0 + 6)
    caisses(x1 - 2, z0 + 1, ["2", "1", "2"])
    W.clear(x1, S, z0 + 2, x1, S + 1, z0 + 2)
    W.set(x0 + 5, S + 3, z0 + 6, LUMIERE_F)
    citerne_kvas(x0 + 2, z1 + 3)


def eglise():
    """Ruined church (east lane): roofless nave of white plaster with brick showing, arched windows, side portals,
    rubble and broken pews inside, a half-round apse; bell tower at the west end with two flights of stairs to the
    belfry (stand S+9), arched openings on four sides and a bell, pyramid roof."""
    nx0, nx1, nz0, nz1 = 174, 199, 78, 89
    mur, mur2 = D + "enduit_blanc", D + "enduit_blanc_use"

    def haut_mur(x, z):
        if (x, z) in ((nx0, nz0), (nx0, nz1), (nx1, nz0), (nx1, nz1)):
            return S + 8
        if z == nz0 and 189 <= x <= 194:
            return S + 1 + (1 if x in (189, 194) else 0)
        if z == nz1 and 176 <= x <= 179:
            return S + 3
        return S + 5 + h(x, z, 4) % 3

    def mat(x, y, z):
        if y == S:
            return M + "stone_bricks"
        return M + "bricks" if h(x, y, z) % 6 == 0 else (mur2 if h(x, y, z, 1) % 5 == 0 else mur)

    for x in range(nx0, nx1 + 1):
        for z in (nz0, nz1):
            for y in range(S, haut_mur(x, z)):
                W.set(x, y, z, mat(x, y, z))
    for z in range(nz0 + 1, nz1):
        for y in range(S, haut_mur(nx0, z)):
            W.set(nx0, y, z, mat(nx0, y, z))
    W.fill(nx0 + 1, G, nz0 + 1, nx1, G, nz1 - 1, M + "polished_andesite")
    # apse (half round, east)
    acx, acz = nx1 + 0.5, (nz0 + nz1 + 1) / 2.0
    for x in range(nx1 + 1, nx1 + 8):
        for z in range(nz0, nz1 + 1):
            d = math.hypot(x + 0.5 - acx, z + 0.5 - acz)
            if 5.0 <= d <= 6.1:
                for y in range(S, S + 5 + h(x, z, 6) % 3):
                    W.set(x, y, z, mat(x, y, z))
            elif d < 5.0:
                W.set(x, G, z, M + "polished_andesite")
    W.set(nx1 + 4, S, int(acz), M + "polished_andesite")
    W.set(nx1 + 4, S + 1, int(acz), M + "smooth_stone")
    # arched windows on both long walls
    for xa in (177, 181, 192, 196):
        for z in (nz0, nz1):
            if haut_mur(xa, z) < S + 6 or haut_mur(xa + 1, z) < S + 6:
                continue
            W.clear(xa, S + 2, z, xa + 1, S + 3, z)
            W.set(xa, S + 4, z, marche(M + "stone_brick", "west", half="top"))
            W.set(xa + 1, S + 4, z, marche(M + "stone_brick", "east", half="top"))
    # side portals where the east lane crosses (x 184..186)
    for z in (nz0, nz1):
        W.clear(184, S, z, 186, S + 3, z)
        W.set(184, S + 4, z, marche(M + "stone_brick", "west", half="top"))
        W.set(186, S + 4, z, marche(M + "stone_brick", "east", half="top"))
        W.set(185, S + 4, z, dalle(M + "stone_brick", "top"))
    # rubble, pews, a fallen beam
    for (x, z, t) in ((189, 79, "grand"), (193, 79, "moyen"), (177, 88, "grand"), (196, 86, "moyen"),
                      (180, 81, "petit"), (198, 83, "moyen")):
        W.set(x, S, z, etat(D + "gravats", taille=t))
    for (x, z) in ((190, 80), (191, 80), (192, 80), (191, 79), (192, 79)):
        W.set(x, S, z, M + "cobblestone")
    W.set(191, S + 1, 79, M + "cobblestone")
    for (x, z) in ((184, 83), (185, 83), (186, 86)):
        W.set(x, S, z, M + "cobblestone")
        W.set(x, S + 1, z, M + "mossy_cobblestone")
        W.set(x, S + 2, z, etat(D + "gravats", taille="petit"))
    for x in range(187, 195):
        W.set(x, S, 84, tronc(M + "stripped_spruce_log", "x"))
    for z in (80, 82, 86, 88):
        for x in range(176, 182):
            if h(x, z, 8) % 4:
                W.set(x, S, z, marche(M + "dark_oak", "west"))
    caisses(196, 81, ["1"])
    # bell tower: walls x 168..173, z 80..87 up to S+13, door south, openings to the nave
    tx0, tx1, tz0, tz1 = 168, 173, 80, 87
    for y in range(S, S + 14):
        for x in range(tx0, tx1 + 1):
            for z in (tz0, tz1):
                W.set(x, y, z, mat(x, y, z) if y < S + 9 else mur)
        for z in range(tz0 + 1, tz1):
            for x in (tx0, tx1):
                W.set(x, y, z, mat(x, y, z) if y < S + 9 else mur)
    W.fill(tx0 + 1, G, tz0 + 1, tx1 - 1, G, tz1 - 1, M + "stone_bricks")
    W.clear(tx0 + 3, S, tz1, tx0 + 4, S + 2, tz1)
    for x in (tx1, nx0):
        W.clear(x, S, tz1 - 2, x, S + 2, tz1 - 1)
    # flight 1 (x 169..170) rising north, landing (z 81..82) at y S+4, flight 2 (x 171..172) rising south
    for i in range(4):
        z = tz1 - 1 - i
        for x in (tx0 + 1, tx0 + 2):
            for y in range(S, S + i):
                W.set(x, y, z, M + "stone_bricks")
            W.set(x, S + i, z, marche(M + "stone_brick", "north"))
    W.fill(tx0 + 1, S + 4, tz0 + 1, tx1 - 1, S + 4, tz0 + 2, M + "spruce_planks")
    W.fill(tx0 + 1, S + 8, tz0 + 1, tx1 - 1, S + 8, tz1 - 1, M + "spruce_planks")
    for i in range(4):
        z = tz0 + 3 + i
        for x in (tx0 + 3, tx0 + 4):
            W.set(x, S + 5 + i, z, marche(M + "stone_brick", "south"))
            if i < 3:
                W.set(x, S + 8, z, None)
    # belfry openings, bell, roof
    for y in range(S + 9, S + 12):
        for x in (tx0 + 2, tx0 + 3):
            W.set(x, y, tz0, None)
            W.set(x, y, tz1, None)
        for z in (tz0 + 3, tz0 + 4):
            W.set(tx0, y, z, None)
            W.set(tx1, y, z, None)
    for x in range(tx0 + 1, tx1):
        W.set(x, S + 13, tz0 + 2, tronc(M + "dark_oak_log", "x"))
    W.set(tx0 + 2, S + 12, tz0 + 2, etat(M + "bell", attachment="ceiling", facing="north", powered="false"))
    W.fill(tx0, S + 14, tz0, tx1, S + 14, tz1, M + "smooth_stone")
    for k, a in enumerate((1, 2)):
        for x in range(tx0 + a, tx1 - a + 1):
            for z in range(tz0 + a, tz1 - a + 1):
                W.set(x, S + 15 + k, z, D + "tole_toit_rouille")
    for z in range(tz0 + 3, tz1 - 2):
        W.set(tx0 + 2, S + 17, z, D + "tole_toit_rouille")
        W.set(tx0 + 3, S + 17, z, D + "tole_toit_rouille")
    W.set(tx0 + 2, S + 7, tz0 + 2, LUMIERE_F)
    W.set(tx0 + 2, S + 11, tz0 + 4, LUMIERE_F)


def memorial():
    """War memorial at the map centre: paved round square, stepped base, obelisk with a red star, a flower bed, walls
    of names (cover), two field guns, benches, birches, lamps (A half; the B half is the mirror)."""
    cx, cz = centre()
    for x in range(int(cx) - 15, int(cx) + 15):
        for z in range(int(cz) - 15, int(cz)):
            d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
            if d <= 14.5:
                W.set(x, G, z, M + ("stone_bricks" if h(x, z, 3) % 5 else "polished_andesite"))
            if 6.5 <= d <= 7.5:
                W.set(x, G, z, M + "polished_granite")
    for x in range(int(cx) - 3, int(cx) + 3):
        for z in range(int(cz) - 3, int(cz)):
            W.set(x, S, z, M + "polished_granite")
    for x in range(int(cx) - 2, int(cx) + 2):
        for z in range(int(cz) - 2, int(cz)):
            W.set(x, S + 1, z, M + "polished_andesite")
    for x in range(int(cx) - 1, int(cx) + 1):
        z = int(cz) - 1
        for y in range(S + 2, S + 13):
            W.set(x, y, z, etat(M + "quartz_pillar", axis="y"))
        W.set(x, S + 13, z, M + "red_concrete")
    for x in range(int(cx) - 2, int(cx) + 2):
        W.set(x, G, int(cz) - 5, M + "grass_block")
        W.set(x, S, int(cz) - 5, M + "poppy")
    # walls of names: two arcs 2 high, gaps on the axes
    for x in range(int(cx) - 12, int(cx) + 12):
        for z in range(int(cz) - 12, int(cz)):
            d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
            ang = math.degrees(math.atan2(z + 0.5 - cz, x + 0.5 - cx))
            if 10.0 <= d <= 11.0 and (-160 <= ang <= -110 or -70 <= ang <= -20):
                W.set(x, S, z, M + "polished_andesite")
                W.set(x, S + 1, z, M + "polished_blackstone" if h(x, z) % 3 else M + "polished_andesite")
    # two field guns (the mirror gives the B pair)
    for (gx, gz, sens) in ((int(cx) - 7, int(cz) - 5, 1), (int(cx) + 5, int(cz) - 8, -1)):
        W.set(gx, S, gz, etat(D + "pneu", axis="x"))
        W.set(gx, S, gz + 2, etat(D + "pneu", axis="x"))
        W.set(gx, S, gz + 1, M + "green_concrete")
        W.set(gx, S + 1, gz + 1, M + "green_concrete")
        for i in range(1, 4):
            W.set(gx + sens * i, S + 1, gz + 1, etat(M + "polished_basalt", axis="x"))
        W.set(gx - sens, S, gz + 1, M + "green_concrete")
    for x in (int(cx) - 10, int(cx) + 9):
        for z in range(int(cz) - 3, int(cz)):
            W.set(x, S, z, M + "polished_andesite")
            W.set(x, S + 1, z, M + "polished_granite")
        W.set(x, S + 2, int(cz) - 2, etat(M + "lantern", hanging="false", waterlogged="false"))
    for (x, z) in ((int(cx) - 8, int(cz) - 10), (int(cx) + 7, int(cz) - 10)):
        banc(x, z, "south")
    for (x, z) in ((int(cx) - 13, int(cz) - 9), (int(cx) + 11, int(cz) - 10), (int(cx) - 4, int(cz) - 14)):
        arbre_bouleau(x, z, 7 + x % 3)
    for (x, z) in ((int(cx) - 14, int(cz) - 1), (int(cx) + 13, int(cz) - 6)):
        lampadaire(x, z, "south")


def village():
    """Houses of the A part of the village (two rows each side of the club), gardens, wells, haystacks, sheds, trees,
    a bus stop on the road across the middle."""
    nord = [(46, 72), (60, 72), (74, 72), (146, 72), (160, 72)]
    sud = [(50, 95), (64, 95), (80, 95), (130, 95), (144, 95), (158, 95)]
    for i, (x0, z0) in enumerate(nord):
        izba(x0, z0, "south", graine=i)
    for i, (x0, z0) in enumerate(sud):
        izba(x0, z0, "north", graine=i + 2)
    for (x0, z0) in ((53, 73), (67, 73), (153, 73), (167, 73)):
        jardin(x0, z0, x0 + 5, z0 + 6, ((x0 + 2, z0 + 6),))
    for (x0, z0) in ((57, 97), (71, 97), (137, 97), (151, 97)):
        jardin(x0, z0, x0 + 5, z0 + 6, ((x0 + 2, z0),))
    for (x, z) in ((84, 87), (139, 87), (44, 88)):
        puits(x, z)
    for (x, z) in ((89, 77), (124, 101), (170, 92), (91, 104)):
        meule(x, z)
    cabane(88, 96, 0)
    bois_empile(58, 82, 3)
    bois_empile(158, 83, 3)
    abribus(64, 106)
    for (x, z) in ((44, 79), (82, 82), (126, 92), (143, 84), (174, 102), (46, 106), (136, 106), (76, 105)):
        arbre_bouleau(x, z, 6 + z % 3)
    for (x, z) in ((100, 70), (120, 70), (43, 70), (178, 70)):
        lampadaire(x, z, "north")
    voiture(30, 98, "north", U + "epave_brulee")
    voiture(150, 106, "east", U + "epave_bleue")
    tracteur(178, 104)
    for (x, z) in ((44, 96), (92, 72), (156, 69), (171, 107)):
        caisses(x, z, ["21"])
    meule(46, 93)
    for (x, z, motif) in ((76, 93, ["22", "22"]), (142, 93, ["22", "22"]), (166, 93, ["22", "22"]), (88, 71, ["2"]),
                          (118, 71, ["2"]), (56, 81, ["2"]), (70, 81, ["2"]), (66, 86, ["22", "22"]), (44, 90, ["2"]),
                          (26, 90, ["2"]), (205, 99, ["2", "2"]), (146, 104, ["2", "2"]), (62, 104, ["22"]),
                          (153, 85, ["22"]), (156, 80, ["22"]), (77, 86, ["2"]), (217, 80, ["2", "2"]),
                          (217, 96, ["2"]), (188, 105, ["2"]), (176, 71, ["22"]), (180, 69, ["2"])):
        caisses(x, z, motif)
    bois_empile(2, 106, 3)
    meule(190, 74)
    for (x0, x1, z) in ((101, 104, 94), (115, 118, 94), (108, 111, 93)):
        haie(x0, z, x1, z, 2)


def bout_est():
    """East edge of the village (x 200..217): haystacks, a shed, birches, a hay trailer, crates."""
    meule(210, 96)
    meule(206, 104)
    cabane(211, 80, 2)
    for (x, z) in ((203, 72), (213, 74), (213, 107), (203, 95)):
        arbre_bouleau(x, z, 7 + x % 3)
    remorque_foin(206, 86)
    remorque_foin(196, 106)
    caisses(213, 89, ["21", "1"])


def tambour(x0, z0, mat):
    """Entrance porch 4 wide x 2 deep in front of a street door (door at x0+1..x0+2 on the wall z0+2): side walls,
    front wall with the doorway, slab roof."""
    for z in (z0, z0 + 1):
        for y in range(S, S + 3):
            W.set(x0, y, z, mat)
            W.set(x0 + 3, y, z, mat)
    for y in range(S, S + 3):
        W.set(x0 + 1, y, z0, mat if y == S + 2 else None)
        W.set(x0 + 2, y, z0, mat if y == S + 2 else None)
    for x in range(x0, x0 + 4):
        for z in (z0, z0 + 1):
            W.set(x, S + 3, z, dalle(M + "smooth_stone", "bottom"))


def couloir_nord():
    """Service road north of the barracks and the headquarters (z 13..21): porches at the street doors, a truck by the
    store, crates and drums."""
    for xp in (37, 57, 77):
        tambour(xp, 20, M + "lime_concrete")
    tambour(152, 20, D + "enduit_ocre")
    camion(44, 16, "east")
    caisses(55, 14, ["21", "2"])
    caisses(60, 17, ["2", "2", "2", "2"])
    caisses(42, 12, ["2", "2"], y=S + 1)
    caisses(80, 18, ["22"])
    caisses(95, 12, ["2", "2"])
    caisses(78, 2, ["22"])
    caisses(108, 2, ["2"])
    camion(124, 17, "east")
    caisses(140, 20, ["22"])
    caisses(129, 27, ["22", "22"])
    caisses(192, 21, ["2", "2"])
    for (x, z) in ((176, 14), (176, 15), (177, 15), (175, 29), (176, 29), (176, 30), (177, 44)):
        W.set(x, S, z, FUT)
        W.set(x, S + 1, z, FUT)
    caisses(176, 6, ["2", "1"])


def banya(x0, z0, porte="south"):
    """Bathhouse 5 x 4: log walls, pitched roof (ridge along x), door, a small window, stove with its chimney through
    the roof, bench inside, firewood by the wall."""
    x1, z1 = x0 + 4, z0 + 3
    for y in range(S, S + 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, tronc(M + "spruce_log", "x") if x not in (x0, x1) else tronc(M + "stripped_spruce_log"))
        for z in range(z0 + 1, z1):
            for x in (x0, x1):
                W.set(x, y, z, tronc(M + "spruce_log", "z"))
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "spruce_planks")
    for k in range(3):
        y = S + 3 + k
        for x in range(x0 - 1, x1 + 2):
            W.set(x, y, z0 - 1 + k, marche(M + "dark_oak", "south"))
            W.set(x, y, z1 + 1 - k, marche(M + "dark_oak", "north"))
        if k:
            for x in (x0, x1):
                for z in range(z0 + k, z1 + 1 - k):
                    W.set(x, y, z, M + "spruce_planks")
    zp = z1 if porte == "south" else z0
    zf = z0 if porte == "south" else z1
    W.clear(x0 + 1, S, zp, x0 + 1, S + 1, zp)
    W.set(x0 + 3, S + 1, zf, M + "glass_pane")
    W.set(x0 + 3, S, z0 + 1, M + "bricks")
    W.set(x0 + 3, S + 1, z0 + 1, M + "bricks")
    for y in range(S + 2, S + 7):
        W.set(x0 + 3, y, z0 + 1, M + "bricks")
    W.set(x0 + 2, S, z1 - 1 if porte == "north" else z0 + 2, dalle(M + "spruce", "top"))
    W.set(x0 + 2, S + 2, z0 + 1, LUMIERE_F)
    bois_empile(x1 + 1, z0, 3)


def rue_village():
    """Chicanes in the village street along the wall (z 65..71): parked trucks, haystacks, sandbags, roadblocks, a
    kiosk and a bus stop, so that no straight view runs along it."""
    camion(8, 65, "east")
    camion(52, 65, "west")
    camion(90, 65, "east")
    camion(142, 65, "west")
    camion(192, 65, "east")
    meule(24, 70)
    meule(137, 70)
    meule(205, 70)
    sacs(40, 68, 40, 71, 2)
    barrage(80, 68, 80, 71)
    barrage(165, 68, 165, 71)
    kiosque(124, 68, "south")
    abribus(93, 69)
    bois_empile(216, 66, 4)


def ruelle():
    """Back yards between the two rows of houses (z 81..94): bathhouses, sheds, woodpiles, haystacks, a hay trailer;
    things in the west lane and along the fence."""
    banya(49, 83, "south")
    banya(70, 88, "north")
    banya(145, 83, "south")
    banya(163, 88, "north")
    banya(14, 86, "north")
    cabane(90, 83, 1)
    meule(62, 91)
    meule(128, 91)
    bois_empile(30, 86, 3)
    bois_empile(152, 89, 3)
    remorque_foin(78, 90)
    caisses(24, 93, ["21"])
    meule(35, 98)
    caisses(33, 104, ["22"])
    meule(3, 96)
    bois_empile(2, 84, 3)
    bois_empile(216, 92, 4)
    caisses(215, 70, ["2", "2"])
    meule(215, 102)


def route_centrale():
    """The road across the middle (z 104..109): an overturned bus, a truck wreck, a roadblock, haystacks, woodpiles,
    crates, so that it is no long open corridor."""
    bus_couche(40, 107)
    barrage(78, 107, 78, 109)
    caisses(87, 107, ["21", "12", "2"])
    camion(135, 107, "west")
    meule(160, 108)
    bois_empile(8, 107, 3)
    caisses(212, 108, ["2", "2"])
    caisses(56, 104, ["12"])


def cimetiere(x0=176, z0=92, x1=201, z1=103):
    """Village cemetery south of the church: low iron fence with gates on the east lane, grave plots with iron
    railings and headstones or small obelisks with a red star, birches."""
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if not 183 <= x <= 186:
                W.set(x, S, z, M + "iron_bars")
    for z in range(z0 + 1, z1):
        W.set(x0, S, z, M + "iron_bars")
        W.set(x1, S, z, M + "iron_bars")
    for k, (px, pz) in enumerate([(px, pz) for px in (177, 180, 188, 191, 194, 197) for pz in (94, 98)]):
        for x in range(px, px + 3):
            for z in range(pz, pz + 3):
                if (x, z) == (px + 1, pz):
                    continue
                if x in (px, px + 2) or z in (pz, pz + 2):
                    W.set(x, S, z, M + "iron_bars")
        W.set(px + 1, G, pz + 1, M + "grass_block")
        if k % 3 == 0:
            W.set(px + 1, S, pz, M + "smooth_stone")
            W.set(px + 1, S + 1, pz, M + "smooth_stone")
            W.set(px + 1, S + 2, pz, M + "red_concrete")
        else:
            W.set(px + 1, S, pz, M + "polished_granite")
            W.set(px + 1, S + 1, pz, M + "polished_granite" if k % 2 else M + "polished_blackstone")
        if k % 4 == 1:
            W.set(px + 1, S, pz + 1, M + "poppy")
    for (x, z) in ((184, 97), (182, 102), (187, 100)):
        arbre_bouleau(x, z, 7 + x % 3)


def poteaux_telegraphe(xs, z):
    """Wooden telegraph poles (fences 7 high) along the street, with two wires (chains) between neighbours."""
    for x in xs:
        for y in range(S, S + 7):
            W.set(x, y, z, M + "spruce_fence")
    for a, b in zip(xs, xs[1:]):
        if b - a > 16:
            continue
        for x in range(a + 1, b):
            if W.get(x, S + 6, z) is None:
                W.set(x, S + 6, z, etat(M + "chain", axis="x", waterlogged="false"))


def arbres_en_plus():
    """More birches across the map (shade, cover from above, landmarks)."""
    for (x, z) in ((11, 47), (27, 49), (142, 40), (28, 101), (58, 90), (209, 75), (194, 108), (150, 93), (174, 62)):
        arbre_bouleau(x, z, 7 + (x + z) % 3)


# ================================================================================================ meta
SITE_A = [74.5, float(S), 168.5, 160.0]    # B's headquarters square (mirror of x 145.5, z 51.5)
SITE_B = [159.5, float(S), 168.5, -160.0]  # B's parade ground (mirror of x 60.5, z 51.5)


def meta(n):
    cx, cz = centre()
    a = [[104.5, float(S), 11.5, 0.0], [108.5, float(S), 11.5, 0.0], [111.5, float(S), 11.5, 0.0],
         [115.5, float(S), 11.5, 0.0]]
    return {
        "id": ID,
        "nom_fr": "Garnison",
        "nom_en": "Garrison",
        "type": "clashwar_4v4",
        "dimension": "heraliz_clashwar:arenes",
        "taille": [SX, SY, SZ],
        "bornes": [2, 0, 2, SX - 3, SY + 3, SZ - 3],
        "points": {
            "spawn_a": a,
            "spawn_b": [miroir_point(p) for p in a],
            "barriere_a": [[101.0, float(S), 8.0, 0.0], [118.0, float(S + 2), 14.0, 0.0]],
            "barriere_b": [[101.0, float(S), SZ - 15.0, 0.0], [118.0, float(S + 2), SZ - 9.0, 0.0]],
            "zone_centre": [[cx, float(S), cz, 0.0]],
            "spectateur": [[cx, 29.0, 2.5, 0.0], [cx, 29.0, SZ - 2.5, 180.0]],
            "bombe_a": [SITE_A],
            "site_a": [SITE_A],
            "bombe_b": [SITE_B],
            "site_b": [SITE_B],
            "charge": [[110.0, float(S), 11.5, 0.0]],
        },
        "format": "4v4",
        "joueurs_par_equipe": 4,
        "theme": "garnison et village",
        "barrieres": {"a": [101, S, 8, 118, S + 2, 14], "b": [101, S, SZ - 15, 118, S + 2, SZ - 9]},
        "zone": {"centre": [cx, S, cz], "rayon_depart": 155.0, "rayon_min": 7.0},
        "symetrie": "centrale (rotation 180° autour du point x = 110, z = 110)",
        "blocs": n,
        "recherche_destruction": {
            "attaquants": "a",
            "defenseurs": "b",
            "note": "Sites A (place de l'état-major, sud-ouest) et B (place d'armes, sud-est) du côté de spawn_b, pour "
                    "le mode recherche et destruction (inactif aujourd'hui) : à déplacer librement dans "
                    "generer_garnison.py.",
        },
        "generateur": "modules/clashwar/outils/cartes_clashwar/generer_garnison.py",
    }


def construire():
    sol()
    surfaces()
    enceinte()
    hangar_spawn()
    caserne()
    etat_major()
    entrepot()
    polygone()
    parc_vehicules()
    estacade()
    hangar_ouvert()
    cantine()
    depot_munitions()
    bande_ouest()
    place_armes()
    allee()
    place_qg()
    depot_carburant()
    mur_garnison()
    ferme()
    club()
    magasin()
    eglise()
    memorial()
    village()
    bout_est()
    couloir_nord()
    rue_village()
    ruelle()
    route_centrale()
    cimetiere()
    poteaux_telegraphe([5, 19, 33, 47, 61, 75, 89], 71)
    poteaux_telegraphe([131, 145, 159, 173, 187, 201, 215], 71)
    arbres_en_plus()
    herbes_partout()


def main():
    K.construire_carte(ID, construire, meta)


if __name__ == "__main__":
    main()
