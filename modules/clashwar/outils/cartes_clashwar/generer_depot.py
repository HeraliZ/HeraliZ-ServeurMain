"""ClashWar 1v1 map "Dépôt des locomotives" (clashwar_1v1_depot): railway locomotive depot, 90 x 90.

Centre: a turntable pit (2 deep) crossed by its bridge, where the final zone closes. Each half: the spawn in the
railwaymen's locker room, tracks with wagons, a footbridge over the yard (stairs, deck 6 up), the engine shed (inspection
pits, gallery, locomotive) on one side, the water tower (upper room 6 up) and the coal trestle (deck 4 up) on the other.
Built in the north half (team A) and mirrored by a 180-degree rotation, with another look for the south half.

Usage: python generer_depot.py
"""
import xl_carte as K
import xl_kit as KT
from xl_lib import dalle, etat, marche, oriente, tronc
from xl_formes import C, D, G, M, S, U, LUMIERE, LUMIERE_F, garde_corps

ID = "clashwar_1v1_depot"
SX = SZ = 90
SY = 26
CX = CZ = SX / 2.0     # symmetry centre 45.0 (between cells 44 and 45)

PAL_B = {
    M + "bricks": M + "mud_bricks",                         # red brick (A) -> brown brick (B)
    D + "tole_toit_rouille": D + "tole_toit_bleue",
    D + "tole_toit_rouille_escalier": D + "tole_toit_bleue_escalier",
    D + "tole_toit_rouille_dalle": D + "tole_toit_bleue_dalle",
    M + "green_concrete": M + "red_terracotta",             # green locomotive (A) -> red one (B)
    M + "brown_terracotta": M + "light_gray_terracotta",    # boxcars
    M + "lime_terracotta": M + "cyan_terracotta",           # rusty gondolas -> grey
    M + "white_wool": M + "yellow_wool",
    M + "spruce_planks": M + "dark_oak_planks",
    M + "stone": M + "andesite",
}
VAR_B = {
    U + "epave_rouge": U + "epave_bleue",
    C + "arkaniaz_trash_can1": C + "arkaniaz_trash_can2",
}

K.init(SX, SY, SZ, PAL_B, VAR_B, graine=21)
from xl_carte import *  # noqa: E402,F401,F403

BALLAST = [(M + "gravel", 6), (M + "andesite", 2), (M + "coarse_dirt", 2), (M + "black_concrete", 1)]
CHARBON = [(M + "black_concrete", 4), (M + "gravel", 3), (M + "coarse_dirt", 3), (M + "coal_block", 1)]
BETON = [(M + "stone_bricks", 6), (M + "cracked_stone_bricks", 2), (M + "gray_concrete", 2), (M + "andesite", 1)]


def surfaces():
    surface(2, 2, 25, 44, CHARBON, graine=1)
    surface(26, 2, 63, 9, BETON, graine=2)
    surface(26, 10, 63, 44, BALLAST, graine=3)
    surface(64, 2, 87, 44, BETON, graine=4)
    surface(64, 41, 87, 44, BALLAST, graine=5)
    surface(84, 2, 87, 11, PELOUSE, graine=6)
    surface(2, 40, 6, 44, PELOUSE, graine=7)


# ------------------------------------------------------------------------------------------------ spawn building
def vestiaire():
    """Railwaymen's locker room (spawn A): brick, flat roof, two doors to the yard, side doors, lockers inside."""
    x0, x1, z0, z1 = 37, 52, 2, 9
    for y in range(S, S + 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "polished_andesite")
    W.fill(x0, S + 4, z0, x1, S + 4, z1, M + "black_concrete")
    for x in range(x0, x1 + 1):
        W.set(x, S + 5, z0, M + "stone_bricks")
        W.set(x, S + 5, z1, M + "stone_bricks")
    for z in range(z0, z1 + 1):
        W.set(x0, S + 5, z, M + "stone_bricks")
        W.set(x1, S + 5, z, M + "stone_bricks")
    # doors to the yard (south) and side doors
    W.clear(40, S, z1, 41, S + 2, z1)
    W.clear(48, S, z1, 49, S + 2, z1)
    W.clear(x0, S, 5, x0, S + 1, 6)
    W.clear(x1, S, 7, x1, S + 1, 8)
    # small high windows (barred)
    for x in (39, 44, 45, 51):
        W.set(x, S + 2, z0, M + "iron_bars")
    # lockers and benches
    for x in range(39, 51, 2):
        W.set(x, S, z0 + 1, oriente(C + "arkaniaz_office_drawers", "south"))
    W.set(43, S, 7, oriente(D + "banc_parc", "north"))
    W.set(46, S, 7, oriente(D + "banc_parc", "north"))
    for x in (40, 45, 50):
        W.set(x, S + 3, 5, LUMIERE)
    # canopy over the yard doors
    for x in range(39, 51):
        W.set(x, S + 3, z1 + 1, dalle(M + "smooth_stone", "top"))
    # roof: vent, antenna
    W.set(41, S + 5, 5, M + "stone_bricks")
    W.set(48, S + 5, 4, D + "groupe_ventilation")


# ------------------------------------------------------------------------------------------------ yard
def voies():
    KT.voie(33, 10, 33, 44)
    KT.voie(56, 10, 56, 44)
    KT.voie(70, 2, 70, 14)
    KT.voie(80, 2, 80, 14)
    KT.voie(70, 41, 70, 44)
    KT.voie(80, 41, 80, 44)
    KT.butoir(33, 10, "south")
    KT.butoir(56, 10, "south")
    # wagons
    KT.wagon_couvert(32, 12, long=9)
    KT.wagon_citerne(32, 29, long=8)
    KT.tombereau(55, 15, long=8)
    KT.wagon_couvert(55, 30, long=7, couleur=M + "brown_terracotta")


def plaque_tournante():
    """Turntable pit at the map centre (radius 7, 2 deep) with its bridge along x through the centre (deck at ground
    level, girder under it), a cabin at each end beside the deck, steps down into both half-pits. Symmetric."""
    r_mur, r_fosse = 7.0, 6.0
    for x in range(int(CX - 8), int(CX + 9)):
        for z in range(int(CZ - 8), int(CZ) + 1):
            d = math.hypot(x + 0.5 - (CX + 0.5), z + 0.5 - (CZ + 0.5))
            if d <= r_fosse:
                W.set(x, G, z, None)
                W.set(x, G - 1, z, None)
                W.set(x, G - 2, z, M + ("gravel" if h(x, z) % 3 else "andesite"))
            elif d <= r_mur:
                for y in range(G - 2, G + 1):
                    W.set(x, y, z, M + "stone_bricks" if y < G else M + "smooth_stone")
    # pivot and bridge (deck at y G, girder at y G-1), x 38..51, z 44..45
    for x in range(38, 52):
        for z in (44, 45):
            W.set(x, G, z, M + "light_blue_wool")
            W.set(x, G - 1, z, M + "netherite_block")
    for x in range(38, 52):
        W.set(x, S, 44, garde_corps("north") if x not in (38, 51) else None)
    KT.voie(38, 44, 51, 44, ballast=False)
    # cabins beside the deck at its west end (north side), mirrored at the east end (south side)
    for x in (38, 39):
        for z in (42, 43):
            W.set(x, G, z, M + "light_blue_wool")
            W.set(x, G - 1, z, M + "netherite_block")
            for y in range(S, S + 3):
                W.set(x, y, z, M + "lapis_block" if not (y == S + 1 and z == 42) else M + "gray_stained_glass_pane")
            W.set(x, S + 3, z, dalle(M + "smooth_stone", "bottom"))
    # steps into the north half-pit (from the north rim and from the west rim)
    for (x, z, f) in ((43, 38, "north"), (44, 38, "north"), (40, 40, "west")):
        W.set(x, G - 1, z, marche(M + "stone_brick", f))
        W.set(x, G, z, None)
        W.set(x, G - 2, z, M + "stone_bricks")
    # cover in the half-pit: a dropped bogie, crates
    W.set(41, G - 1, 41, M + "coal_block")
    W.set(42, G - 1, 41, M + "coal_block")
    W.set(47, G - 1, 40, M + "iron_block")
    W.set(48, G - 1, 40, M + "iron_block")
    W.set(48, G, 40, M + "orange_wool")


def passerelle():
    """Footbridge over the yard at z 24..25, deck at y S+5 (stand S+6), on concrete posts, corrugated cover plates;
    stair towers at both ends (rising north)."""
    yd = S + 5
    for x in range(26, 64):
        for z in (24, 25):
            W.set(x, yd, z, M + "light_blue_wool")
        W.set(x, yd + 1, 24, garde_corps("north"))
        W.set(x, yd + 1, 25, garde_corps("south"))
        if x % 4 in (0, 1):
            W.set(x, yd, 23, M + "pink_wool")
            W.set(x, yd + 1, 23, M + "pink_wool")
            W.set(x, yd, 26, M + "pink_wool")
            W.set(x, yd + 1, 26, M + "pink_wool")
    for x in (28, 36, 44, 52, 60):
        for z in (24, 25):
            for y in range(S, yd):
                W.set(x, y, z, M + "stone_brick_wall")
    for (x0, ) in ((24,), (64,)):
        # stair x0..x0+1, steps from z 29 (y S) to z 25 (y S+4), landing at z 23..24 (y S+5)
        for i in range(5):
            z = 29 - i
            for x in (x0, x0 + 1):
                for y in range(S, S + i):
                    W.set(x, y, z, M + "stone_bricks")
                W.set(x, S + i, z, marche(M + "stone_brick", "north"))
        for z in (23, 24):
            for x in (x0, x0 + 1):
                for y in range(S, yd + 1):
                    W.set(x, y, z, M + "stone_bricks")
        W.set(x0 if x0 == 24 else x0 + 1, yd + 1, 23, garde_corps("north"))


# ------------------------------------------------------------------------------------------------ west: water tower, trestle
def chateau_eau(cx=11.5, cz=34.5):
    """Brick water tower (radius 4.5): ground room, inner stair to an upper room (floor y S+5, stand S+6) with slits,
    wooden tank above (radius 5.5) and a cone roof. Landmark of the west side."""
    r = 4.5
    for y in range(S, S + 10):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                if r - 1.0 <= d <= r:
                    W.set(x, y, z, M + ("bricks" if (y - S) % 5 else "stone_bricks"))
                elif d < r - 1.0 and y == S + 5:
                    W.set(x, y, z, M + "spruce_planks")
                elif d < r - 1.0 and y == S + 9:
                    W.set(x, y, z, M + "spruce_planks")
    for x in range(int(cx - r), int(cx + r + 1)):
        for z in range(int(cz - r), int(cz + r + 1)):
            if math.hypot(x + 0.5 - cx, z + 0.5 - cz) < r - 1.0:
                W.set(x, G, z, M + "polished_andesite")
    # door on the south side, inner stair (x 10..11) rising north from z 36 to the upper floor
    W.clear(11, S, 38, 12, S + 2, 38)
    for i in range(5):
        z = 36 - i
        for x in (10, 11):
            W.set(x, S + i, z, marche(M + "spruce", "north"))
            for y in range(S, S + i):
                W.set(x, y, z, M + "spruce_planks")
            W.set(x, S + 5, z, None)
    # slits of the upper room
    for (x, z) in ((11, 30), (12, 30), (11, 38), (12, 38), (7, 34), (7, 35), (15, 34), (15, 35)):
        W.set(x, S + 7, z, None)
    for (x, y, z) in ((11, S + 3, 34), (12, S + 8, 35)):
        W.set(x, y, z, LUMIERE)
    # tank and roof
    rt = 5.5
    for y in range(S + 10, S + 15):
        for x in range(int(cx - rt - 1), int(cx + rt + 2)):
            for z in range(int(cz - rt - 1), int(cz + rt + 2)):
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                if d <= rt and (d > rt - 1.0 or y in (S + 10, S + 14)):
                    W.set(x, y, z, tronc(M + "stripped_dark_oak_log") if (y - S) % 2 else M + "spruce_planks")
    for k in range(4):
        rr = rt - 1.2 * k
        for x in range(int(cx - rr - 1), int(cx + rr + 2)):
            for z in range(int(cz - rr - 1), int(cz + rr + 2)):
                if math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= rr:
                    W.set(x, S + 15 + k, z, dalle(D + "tole_toit_rouille_dalle", "bottom") if k == 3 else D + "tole_toit_rouille")
    W.set(11, S + 19, 34, D + "tole_toit_rouille")


def estacade():
    """Coal trestle along z (x 17..20, z 8..30): brick piers, deck at y S+3 (stand S+4), coal heaps on it; reached by a
    ramp of steps at the south end; coal bunkers between the piers underneath."""
    yd = S + 3
    for z in range(8, 31):
        for x in range(17, 21):
            W.set(x, yd, z, M + "spruce_planks" if x in (17, 20) else M + "black_concrete")
    for z in (8, 13, 18, 23, 28):
        for x in range(17, 21):
            for y in range(S, yd):
                W.set(x, y, z, M + "bricks")
    for z in range(8, 31):
        W.set(17, yd + 1, z, garde_corps("west"))
        W.set(20, yd + 1, z, garde_corps("east"))
    KT.voie(18, 8, 18, 30, ballast=False)
    # coal on the deck and in the bunkers
    for (x, z, n) in ((19, 11, 2), (19, 12, 1), (18, 20, 1), (19, 21, 2), (19, 22, 1), (19, 26, 1)):
        for k in range(n):
            W.set(x, yd + 1 + k, z, M + "coal_block")
    for (x, z, n) in ((18, 10, 2), (19, 10, 1), (17, 16, 1), (18, 16, 2), (19, 20, 2), (18, 25, 1), (19, 25, 2)):
        for k in range(n):
            W.set(x, S + k, z, M + "coal_block")
    # ramp at the south end: steps z 33 -> 31 up to the deck (z 30)
    for i, z in enumerate((33, 32, 31)):
        for x in range(17, 21):
            for y in range(S, S + i):
                W.set(x, y, z, M + "stone_bricks")
            W.set(x, S + i, z, marche(M + "stone_brick", "north"))
    for x in range(17, 21):
        W.set(x, yd + 1, 30, None) if x in (18, 19) else None


def tas_charbon_ouest():
    KT.tas(3, 4, ["122", "2331", "1221", " 11"])
    KT.tas(9, 9, ["12", "232", "121"])
    KT.tas(3, 18, ["11", "121", " 1"])
    for z in range(4, 15, 5):
        W.set(14, S, z, tronc(M + "stripped_spruce_log", "z"))
        W.set(14, S, z + 1, tronc(M + "stripped_spruce_log", "z"))
        W.set(14, S + 1, z, tronc(M + "stripped_spruce_log", "z"))


# ------------------------------------------------------------------------------------------------ east: engine shed
def remise():
    """Engine shed x 66..86, z 12..40: brick hall 8 high, two tracks with inspection pits (2 deep), a diesel shunter,
    gallery along the west wall (stand S+6) under high windows, big doors at both ends, low pitched tin roof."""
    x0, x1, z0, z1 = 66, 87, 12, 40
    for y in range(S, S + 8):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "polished_andesite")
    # doors (5 wide, 6 high) at both ends on both tracks
    for (xa, xb) in ((68, 72), (78, 82)):
        W.clear(xa, S, z0, xb, S + 5, z0)
        W.clear(xa, S, z1, xb, S + 5, z1)
    # high windows (west wall: from the gallery; east wall: light)
    for z in range(z0 + 2, z1 - 1, 3):
        for y in (S + 6, S + 7):
            W.set(x0, y, z, None if h(z, y) % 3 else M + "iron_bars")
            W.set(x1, y, z, M + "iron_bars" if h(z, y, 2) % 3 else None)
    # inspection pits (x 69..71 and 79..81), 2 deep, steps at the ends; rails on the pit edges
    for (pa, pb, za, zb) in ((69, 71, 15, 37), (79, 81, 15, 26)):
        for x in range(pa, pb + 1):
            for z in range(za, zb + 1):
                W.set(x, G, z, None)
                W.set(x, G - 1, z, None)
                W.set(x, G - 2, z, M + "polished_andesite")
        for z in range(za - 1, zb + 2):
            for x in (pa - 1, pb + 1):
                for y in (G - 2, G - 1):
                    W.set(x, y, z, M + "stone_bricks")
        for x in range(pa, pb + 1):
            for y in (G - 2, G - 1):
                W.set(x, y, za - 1, M + "stone_bricks")
                W.set(x, y, zb + 1, M + "stone_bricks")
            W.set(x, G - 1, za, marche(M + "stone_brick", "north"))
        for z in range(za, zb + 1):
            for x in (pa - 1, pb + 1):
                if W.get(x, S, z) is None:
                    W.set(x, S, z, etat(M + "rail", shape="north_south", waterlogged="false"))
    # shunter on track 2 (over solid floor), crates and tools
    KT.locomotive(79, 28, long=11, sens=1)
    caisses(74, 30, ["21", "1"])
    caisses(83, 16, ["2", "21"])
    W.set(84, S, 34, oriente(C + "arkaniaz_oil_drum", "west"))
    W.set(84, S, 35, oriente(C + "arkaniaz_oil_drum", "west"))
    W.set(75, S, 14, M + "crafting_table")
    W.set(76, S, 14, etat(M + "barrel", facing="up", open="false"))
    # gallery along the west wall at y S+5 (stand S+6), stairs from the north end going south
    yg = S + 5
    for z in range(z0 + 1, z1):
        W.set(x0 + 1, yg, z, M + "light_blue_wool")
        W.set(x0 + 1, yg + 1, z, garde_corps("east"))
    for i in range(5):
        z = z0 + 2 + i
        W.set(x0 + 2, S + i, z, marche(M + "stone_brick", "south"))
        for y in range(S, S + i):
            W.set(x0 + 2, y, z, M + "stone_bricks")
    W.set(x0 + 2, yg, z0 + 7, M + "light_blue_wool")
    for z in range(z0 + 2, z0 + 8):
        W.set(x0 + 1, yg + 1, z, None)
    # lights
    for z in range(z0 + 3, z1 - 1, 6):
        for x in (70, 76, 82):
            W.set(x, S + 6, z, LUMIERE)
    # low pitched roof: eaves at y S+8, ridge along z at x 76, one step every 2 blocks
    for x in range(x0, x1 + 1):
        dx = min(x - x0, x1 - x)
        y = S + 8 + dx // 2
        demi = dx % 2
        for z in range(z0 - 1, z1 + 2):
            if x == 76:
                W.set(x, y, z, dalle(D + "tole_toit_rouille_dalle", "bottom"))
                continue
            sens = "east" if x < 76 else "west"
            if demi:
                W.set(x, y, z, dalle(D + "tole_toit_rouille_dalle", "bottom"))
            else:
                W.set(x, y, z, marche(D + "tole_toit_rouille_escalier", sens))
        for z in (z0, z1):
            for yy in range(S + 8, y):
                if W.get(x, yy, z) is None:
                    W.set(x, yy, z, M + "bricks")
    W.set(76, S + 14, z0 + 5, M + "bricks")
    W.set(76, S + 15, z0 + 5, M + "bricks")


def est_divers():
    caisses(64, 3, ["12", "1"])
    caisses(86, 43, ["1"])
    W.set(84, S, 6, oriente(C + "arkaniaz_electric_box", "west"))
    W.set(84, S + 1, 6, oriente(C + "arkaniaz_electric_box", "west"))
    peuplier(86, 4, 10)
    peuplier(86, 8, 9)
    lampadaire(65, 42, "west")
    KT.tombereau(68, 42, long=6, axe="x")


def cour_divers():
    """Cover in the yard: relay cabinets, piles of sleepers, rails, barrels, lamp posts, signal masts."""
    for (x, z) in ((28, 14), (61, 38), (29, 33)):
        W.set(x, S, z, oriente(C + "arkaniaz_electric_box", "east"))
        W.set(x, S + 1, z, oriente(C + "arkaniaz_electric_box", "east"))
    # sleepers stacks (2 high)
    for (x0, z0, axe) in ((40, 15, "x"), (46, 31, "x"), (60, 13, "z"), (27, 40, "z"), (49, 19, "x"), (37, 36, "x")):
        for i in range(3):
            x, z = (x0 + i, z0) if axe == "x" else (x0, z0 + i)
            W.set(x, S, z, tronc(M + "stripped_spruce_log", "z" if axe == "x" else "x"))
            if i < 2:
                W.set(x, S + 1, z, tronc(M + "stripped_spruce_log", "z" if axe == "x" else "x"))
    caisses(43, 12, ["21"])
    caisses(52, 37, ["12"])
    caisses(31, 22, ["1"])
    W.set(59, S, 22, etat(M + "barrel", facing="up", open="false"))
    W.set(59, S + 1, 22, etat(M + "barrel", facing="up", open="false"))
    W.set(47, S, 21, oriente(C + "arkaniaz_oil_drum", "north"))
    lampadaire(39, 11, "south")
    lampadaire(62, 30, "west")
    # signal masts (iron post with a lamp)
    for (x, z) in ((35, 21), (54, 42)):
        for y in range(S, S + 4):
            W.set(x, y, z, M + "iron_bars")
        W.set(x, S + 4, z, M + "lapis_block")
    poubelle(30, 9, "south")
    poubelle(59, 9, "south")
    voiture(27, 4, "north", U + "epave_rouge")
    caisses(57, 3, ["2", "1"])
    caisses(30, 5, ["1"])


def poste_aiguillage(x0=25, z0=30):
    """Signal box: brick ground floor (relay room) and a glazed operator room above (floor y S+4, stand S+5), reached by
    an outside stair; overhanging flat roof. Blocks the west side of the yard, vantage over the tracks."""
    x1, z1 = x0 + 6, z0 + 6
    for y in range(S, S + 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
    W.fill(x0, S + 4, z0, x1, S + 4, z1, M + "spruce_planks")
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            bord = x in (x0, x1) or z in (z0, z1)
            if bord:
                W.set(x, S + 5, z, M + "white_wool")
                coin = x in (x0, x1) and z in (z0, z1)
                W.set(x, S + 6, z, M + "white_wool" if coin else M + "glass_pane")
                W.set(x, S + 7, z, M + "white_wool")
    W.fill(x0 - 1, S + 8, z0 - 1, x1 + 1, S + 8, z1 + 1, M + "black_concrete")
    # relay racks downstairs, levers desk upstairs
    for z in range(z0 + 1, z1, 2):
        W.set(x0 + 1, S, z, oriente(C + "arkaniaz_electric_box", "east"))
        W.set(x0 + 1, S + 1, z, oriente(C + "arkaniaz_electric_box", "east"))
    W.set(x0 + 3, S + 5, z0 + 1, oriente(D + "table_metal", "south"))
    W.set(x0 + 4, S + 5, z0 + 1, oriente(D + "table_metal", "south"))
    W.set(x0 + 3, S + 2, z0 + 3, LUMIERE)
    W.set(x0 + 3, S + 7, z0 + 3, LUMIERE)
    # doors: ground floor (east), upper floor (south) from the outside stair rising west along the south wall
    W.clear(x1, S, z0 + 3, x1, S + 1, z0 + 3)
    W.set(x0 + 1, S + 5, z1, None)
    W.set(x0 + 1, S + 6, z1, None)
    for i in range(4):
        x = x0 + 5 - i
        W.set(x, S + i, z1 + 1, marche(M + "stone_brick", "west"))
        for y in range(S, S + i):
            W.set(x, y, z1 + 1, M + "stone_bricks")
    W.set(x0 + 1, S + 4, z1 + 1, M + "stone_bricks")
    for y in range(S, S + 4):
        W.set(x0 + 1, y, z1 + 1, M + "stone_bricks")


def lampisterie(x0=41, z0=15):
    """Lamp room hut on the centre line (cover between the spawn and the turntable): 7 x 5, brick, tin lean-to roof."""
    x1, z1 = x0 + 6, z0 + 4
    for y in range(S, S + 3):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "bricks")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "bricks")
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            W.set(x, S + 3, z, D + "tole_toit_rouille" if z < z1 else dalle(D + "tole_toit_rouille_dalle", "top"))
    W.clear(x0 + 3, S, z1, x0 + 3, S + 1, z1)
    W.clear(x0, S, z0 + 2, x0, S + 1, z0 + 2)
    W.set(x0 + 2, S + 1, z0, None)
    W.set(x0 + 5, S, z0 + 1, etat(M + "barrel", facing="up", open="false"))
    W.set(x0 + 4, S, z0 + 1, etat(M + "lantern", hanging="false", waterlogged="false"))
    W.set(x0 + 3, S + 2, z0 + 2, LUMIERE_F)


def renforts():
    """More wagons on stub tracks off the turntable and on the sidings, cover along the yard edges and fences."""
    KT.voie(26, 44, 37, 44)
    KT.wagon_couvert(26, 43, long=8, axe="x", couleur=M + "brown_terracotta")
    KT.tombereau(32, 37, long=6)
    KT.wagon_citerne(55, 38, long=6)
    KT.voie(61, 12, 61, 36)
    KT.wagon_couvert(60, 14, long=8, couleur=M + "brown_terracotta")
    # along the north edge of the yard
    benne(19, 10, "x")
    caisses(63, 10, ["2", "1"])
    caisses(73, 4, ["21"])
    # west fence: coal heaps against it, crates
    KT.tas(2, 26, ["12", "21", "1"])
    KT.tas(2, 40, ["1", "2", "21"])
    caisses(2, 12, ["2"])
    # cover on the west lane south part
    caisses(14, 42, ["21"])
    W.set(22, S, 39, oriente(C + "arkaniaz_oil_drum", "north"))
    W.set(22, S + 1, 39, etat(M + "barrel", facing="up", open="false"))
    caisses(36, 27, ["2"])
    caisses(50, 27, ["12"])
    # yard corridors along the trains and the centre line: relay cabinets and sleeper stacks (2 high)
    for (x, z) in ((35, 23), (54, 32), (35, 8), (54, 15)):
        W.set(x, S, z, M + "lapis_block")
        W.set(x, S + 1, z, M + "lapis_block")
        W.set(x, S + 2, z, dalle(M + "smooth_stone", "bottom"))
    caisses(46, 37, ["2"])
    for (x0, z0) in ((40, 30), (47, 26), (43, 33)):
        for i in range(3):
            W.set(x0 + i, S, z0, tronc(M + "stripped_spruce_log", "z"))
            W.set(x0 + i, S + 1, z0, tronc(M + "stripped_spruce_log", "z"))
    # north edge of the yard (in front of the locker room)
    benne(30, 11, "x")
    caisses(52, 11, ["2"])
    caisses(77, 11, ["21"])
    caisses(54, 7, ["2", "2"])
    # under the trestle: a bunker wall; west lane obstacles
    for x in range(17, 21):
        for z in (21, 22):
            W.set(x, S, z, M + "bricks")
            W.set(x, S + 1, z, M + "bricks")
    caisses(16, 20, ["2"])
    caisses(15, 26, ["1"])
    benne(21, 33, "x")
    for i in range(3):
        W.set(21 + i, S, 16, tronc(M + "stripped_spruce_log", "z"))
        W.set(21 + i, S + 1, 16, tronc(M + "stripped_spruce_log", "z"))
    KT.tas(10, 41, ["12", "21"])
    cabane(3, 40, 1)
    # engine shed doors: crates beside the tracks, offset
    caisses(69, 7, ["2", "2"])
    caisses(71, 37, ["2"])
    caisses(78, 8, ["2"])
    caisses(82, 43, ["2"])


# ------------------------------------------------------------------------------------------------ meta
def meta(n):
    return {
        "id": ID,
        "nom_fr": "Dépôt des locomotives",
        "nom_en": "Locomotive depot",
        "type": "clashwar_1v1",
        "dimension": "heraliz_clashwar:arenes",
        "taille": [SX, SY, SZ],
        "bornes": [2, 0, 2, SX - 3, SY + 3, SZ - 3],
        "points": {
            "spawn_a": [[44.5, float(S), 5.5, 0.0]],
            "spawn_b": [miroir_point([44.5, float(S), 5.5, 0.0])],
            "barriere_a": [[39.0, float(S), 3.0, 0.0], [50.0, float(S + 2), 7.0, 0.0]],
            "barriere_b": [[39.0, float(S), SZ - 8.0, 0.0], [50.0, float(S + 2), SZ - 4.0, 0.0]],
            "zone_centre": [[CX, float(S), CZ, 0.0]],
            "spectateur": [[CX, 24.0, 2.5, 0.0], [CX, 24.0, SZ - 2.5, 180.0]],
            "bombe_a": [[14.5, float(S), 67.5, 160.0]],
            "site_a": [[14.5, float(S), 67.5, 160.0]],
            "bombe_b": [[70.5, float(S), 51.5, -160.0]],
            "site_b": [[70.5, float(S), 51.5, -160.0]],
            "charge": [[44.5, float(S), 5.5, 0.0]],
        },
        "format": "1v1",
        "joueurs_par_equipe": 1,
        "theme": "dépôt ferroviaire",
        "barrieres": {"a": [39, S, 3, 50, S + 2, 7], "b": [39, S, SZ - 8, 50, S + 2, SZ - 4]},
        "zone": {"centre": [CX, S, CZ], "rayon_depart": 64.0, "rayon_min": 4.0},
        "symetrie": "centrale (rotation 180° autour du point x = 45, z = 45)",
        "blocs": n,
        "recherche_destruction": {
            "attaquants": "a",
            "defenseurs": "b",
            "note": "Sites A (remise sud-ouest) et B (château d'eau sud-est) du côté de spawn_b, pour le mode recherche et "
                    "destruction (inactif aujourd'hui) : à déplacer librement dans generer_depot.py.",
        },
        "generateur": "modules/clashwar/outils/cartes_clashwar/generer_depot.py",
    }


def construire():
    sol()
    surfaces()
    enceinte()
    vestiaire()
    voies()
    plaque_tournante()
    passerelle()
    chateau_eau()
    estacade()
    tas_charbon_ouest()
    remise()
    est_divers()
    cour_divers()
    poste_aiguillage()
    lampisterie()
    renforts()
    herbes_partout()


def main():
    K.construire_carte(ID, construire, meta)


if __name__ == "__main__":
    main()
