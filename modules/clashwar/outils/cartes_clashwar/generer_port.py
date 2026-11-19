"""ClashWar 3v3 map "Port à sec" (clashwar_3v3_port): drained harbour, 170 x 170.

A basin 4 blocks deep crosses the middle of the map (quay walls, slipway, stairs, wooden jetty, stranded boats). The
steel bridge in the centre (final zone) is the middle lane; a stranded cargo ship (holds, wheelhouse 7 up, gangway) is
the crossing of each side lane. North quay (team A): customs house (spawn), portal cranes on rails (platform 9 up), a
container yard, a warehouse with a mezzanine, the port office (3 storeys), fuel tanks. Built in the north half and
mirrored by a 180-degree rotation with another look for the south half.

Usage: python generer_port.py
"""
import xl_carte as K
import xl_formes as F
import xl_kit as KT
from xl_lib import dalle, etat, marche, oriente, tronc
from xl_formes import C, D, G, M, S, U, LUMIERE, LUMIERE_F, garde_corps

ID = "clashwar_3v3_port"
SX = SZ = 170
SY = 30
QUAI = 65            # A quay edge (last ground row); the basin is z 66..103
FOND = 0             # basin floor block y (stand y 1)

PAL_B = {
    M + "lime_terracotta": M + "red_terracotta",                # rusty hull (A) -> red hull (B)
    D + "enduit_blanc": D + "enduit_sable",
    D + "enduit_blanc_use": D + "enduit_sable_use",
    M + "light_blue_terracotta": M + "brown_terracotta",
    M + "yellow_terracotta": M + "orange_terracotta",            # crane paint
    D + "tole_toit_rouille": D + "tole_toit_verte",
    D + "tole_toit_rouille_escalier": D + "tole_toit_verte_escalier",
    D + "tole_toit_rouille_dalle": D + "tole_toit_verte_dalle",
    M + "bricks": M + "white_terracotta",
    M + "white_wool": M + "yellow_wool",
}
VAR_B = {
    D + "conteneur_paroi_bleu": D + "conteneur_paroi_vert",
    D + "conteneur_porte_bleu": D + "conteneur_porte_vert",
    D + "conteneur_paroi_rouge": D + "conteneur_paroi_orange",
    D + "conteneur_porte_rouge": D + "conteneur_porte_orange",
    U + "epave_rouge": U + "epave_bleue",
}

K.init(SX, SY, SZ, PAL_B, VAR_B, graine=33)
from xl_carte import *  # noqa: E402,F401,F403

QUAI_SOL = [(M + "stone_bricks", 5), (M + "cracked_stone_bricks", 2), (M + "gray_concrete", 2), (M + "andesite", 1)]
BETON = [(M + "gray_concrete", 6), (M + "light_gray_concrete", 2), (M + "black_concrete", 2), (M + "gravel", 1)]
VASE = [(M + "mud", 5), (M + "packed_mud", 3), (M + "coarse_dirt", 2), (M + "gravel", 2), (M + "sand", 1)]


def surfaces():
    surface(2, 2, 167, 54, BETON, graine=1)
    surface(2, 55, 167, QUAI, QUAI_SOL, graine=2)
    surface(162, 24, 167, 54, PELOUSE, graine=4)


# ------------------------------------------------------------------------------------------------ basin
def bassin():
    """Drained basin z 66..103 (A part z 66..84), x 14..155: quay walls 5 high (floor y 0 to ground), mud floor."""
    for x in range(13, 157):
        for z in range(QUAI + 1, 85):
            for y in (G - 1, G):
                W.set(x, y, z, None)
            if 14 <= x <= 155:
                W.set(x, FOND, z, None)
    surface_y(14, QUAI + 1, 155, 84, VASE, FOND, graine=5)
    # quay wall (z = QUAI, face toward the basin) and end walls (x 13 / 156)
    for x in range(13, 157):
        for y in range(FOND, G + 1):
            W.set(x, y, QUAI, M + ("stone_bricks" if (x + y) % 5 else "cracked_stone_bricks") if y < G else M + "smooth_stone")
    for z in range(QUAI, 85):
        for x in (13, 156):
            for y in range(FOND, G + 1):
                W.set(x, y, z, M + "stone_bricks" if y < G else M + "smooth_stone")
    # tyre fenders on the quay face, bollards on the edge
    for x in range(18, 152, 6):
        W.set(x, G - 2, QUAI + 1, etat(D + "pneu", axis="z"))
    for x in range(16, 155, 8):
        W.set(x, S, QUAI, M + "polished_deepslate")
        W.set(x, S + 1, QUAI, dalle(M + "polished_deepslate", "bottom"))


def surface_y(x0, z0, x1, z1, mats, y, graine=0):
    tot = sum(p for _, p in mats)
    for x in range(min(x0, x1), max(x0, x1) + 1):
        for z in range(min(z0, z1), max(z0, z1) + 1):
            v = h(x // 2 + graine, z // 2, graine) % tot
            if h(x, z, graine + 3) % 5 == 0:
                v = h(x, z, graine) % tot
            for b, p in mats:
                if v < p:
                    W.set(x, y, z, b)
                    break
                v -= p


def descentes():
    """Ways down into the basin on the A side: concrete slipway (west), stairs (east of centre), stairs (east)."""
    # slipway x 15..20: steps z 66..69 down to the floor
    for x in range(15, 21):
        for i, z in enumerate(range(QUAI + 1, QUAI + 5)):
            y = G - 1 - i
            for yy in range(FOND, y):
                W.set(x, yy, z, M + "stone_bricks")
            W.set(x, y, z, marche(M + "stone_brick", "north"))
        W.set(x, G, QUAI, M + "smooth_stone")
    # stairs down at x 110..112 and at x 140..142 (against the quay wall, going east)
    for (xa, larg) in ((110, 3), (140, 3)):
        for i in range(4):
            x = xa + i
            for z in range(QUAI + 1, QUAI + 1 + larg):
                for yy in range(FOND, G - 1 - i):
                    W.set(x, yy, z, M + "stone_bricks")
                W.set(x, G - 1 - i, z, marche(M + "stone_brick", "west"))
        W.set(xa - 1, G, QUAI, M + "smooth_stone")
        for z in range(QUAI + 1, QUAI + 1 + larg):
            W.set(xa - 1, G, z, M + "smooth_stone")
            for yy in range(FOND, G):
                W.set(xa - 1, yy, z, M + "stone_bricks")


def pont_central():
    """Steel bridge x 82..87 across the whole basin (deck at ground level), girders, concrete piers, trusses as cover,
    operator cabin on a side platform. Symmetric (built for the A part, mirrored)."""
    for x in range(82, 88):
        for z in range(QUAI + 1, 85):
            W.set(x, G, z, M + "light_blue_wool")
            W.set(x, G - 1, z, M + "netherite_block")
    for z in (72, 79):
        for x in (82, 87):
            for y in range(FOND + 1, G - 1):
                W.set(x, y, z, M + "smooth_stone")
    # trusses (2 high) on the edges every 4 blocks, railings between
    for z in range(QUAI + 1, 85):
        for x, cote in ((82, "west"), (87, "east")):
            if z % 4 == 0:
                W.set(x, S, z, M + "netherite_block")
                W.set(x, S + 1, z, M + "netherite_block")
                W.set(x, S + 2, z, dalle(M + "polished_deepslate", "bottom"))
            else:
                W.set(x, S, z, garde_corps(cote))
    # operator cabin on a platform east of the deck (A part), z 79..82
    for x in (88, 89, 90):
        for z in range(79, 83):
            W.set(x, G, z, M + "light_blue_wool")
            W.set(x, G - 1, z, M + "netherite_block")
    for x in (88, 89, 90):
        for z in range(79, 83):
            bord = x in (88, 90) or z in (79, 82)
            if bord:
                for y in range(S, S + 3):
                    vitre = y == S + 1 and not (x == 88 and z in (80, 81))
                    W.set(x, y, z, M + "gray_stained_glass_pane" if vitre and x == 90 else M + "yellow_terracotta")
            W.set(x, S + 3, z, M + "black_concrete")
    W.clear(88, S, 80, 88, S + 1, 81)
    W.set(89, S + 2, 80, LUMIERE_F)
    W.set(87, S, 80, None)
    W.set(87, S, 81, None)


def navire(x0=20, z0=70):
    """Stranded cargo ship lying along x in the A part of the basin: hull 11 wide on the floor, deck at y S+2 (stand
    S+3), two holds open from the deck with stairs inside, hull breaches at floor level, wheelhouse at the stern
    (stand S+7) under a funnel, gangway from the A quay, stairs down to the basin on the B side."""
    L, P = 43, 11
    x1, z1 = x0 + L - 1, z0 + P - 1
    yd = S + 2
    rouille, gris = M + "lime_terracotta", M + "gray_concrete"

    def largeur(x):
        """Half-width taper toward the bow (x1)."""
        r = x1 - x
        return 5 if r >= 6 else max(1, r - 1)

    zc = z0 + 5
    for x in range(x0, x1 + 1):
        dz = largeur(x)
        for z in range(zc - dz, zc + dz + 1):
            bord = abs(z - zc) == dz or x in (x0, x1)
            for y in range(FOND + 1, yd + 1):
                if bord or y == FOND + 1 or y == yd:
                    if y == yd:
                        b = M + "light_blue_wool" if (x + z) % 7 else rouille
                    elif bord:
                        b = rouille if (y + x) % 4 else M + "black_concrete"
                    else:
                        b = gris
                    W.set(x, y, z, b)
    # bulwark (1 high) around the deck, open at the gangway and the stairs
    for x in range(x0, x1 + 1):
        dz = largeur(x)
        for z in (zc - dz, zc + dz):
            W.set(x, yd + 1, z, rouille)
    # holds: x 31..37 and 43..49, open hatches, floor y FOND+1 (stand FOND+2), stairs inside
    for (ha, hb) in ((31, 37), (43, 49)):
        for x in range(ha, hb + 1):
            for z in range(zc - 3, zc + 4):
                W.set(x, yd, z, None)
                for y in range(FOND + 2, yd):
                    W.set(x, y, z, None)
        for z in range(zc - 3, zc + 4):
            for y in range(FOND + 2, yd + 1):
                W.set(ha - 1, y, z, M + "black_concrete") if W.get(ha - 1, y, z) is None else None
        # hatch coamings
        for x in range(ha - 1, hb + 2):
            for z in (zc - 4, zc + 4):
                W.set(x, yd + 1, z, M + "yellow_terracotta")
        for z in range(zc - 4, zc + 5):
            for x in (ha - 1, hb + 1):
                W.set(x, yd + 1, z, M + "yellow_terracotta")
        # stairs from the hold floor up to the deck along the hold's east side
        for i in range(5):
            z = zc - 3 + i
            W.set(hb, FOND + 2 + i, z, marche(M + "stone_brick", "south"))
            for y in range(FOND + 2, FOND + 2 + i):
                W.set(hb, y, z, M + "black_concrete")
        W.set(hb, yd, zc + 2, M + "light_blue_wool")
        W.set(hb + 1, yd + 1, zc + 2, None)
        # cargo left in the hold
        caisses_y(ha + 1, zc - 2, ["22", "1."], FOND + 2)
        W.set(ha + 4, FOND + 2, zc + 2, etat(M + "barrel", facing="up", open="false"))
    # transverse bulkheads (doors 2 high) and lights in the hull
    for xb in (29, 40, 51):
        dzb = largeur(xb)
        for z in range(zc - dzb + 1, zc + dzb):
            for y in range(FOND + 2, yd):
                W.set(xb, y, z, M + "black_concrete")
        W.clear(xb, FOND + 2, zc, xb, FOND + 3, zc)
    for xl in (24, 34, 46, 56):
        W.set(xl, yd - 1, zc + 2, LUMIERE_F)
    # breaches in the hull at floor level
    for (x, z) in ((40, zc + 5), (41, zc + 5), (52, zc - 5), (53, zc - 5)):
        W.set(x, FOND + 2, z, None)
        W.set(x, FOND + 3, z, None)
    for x in (40, 41, 52, 53):
        for z in range(zc - 4, zc + 5):
            for y in (FOND + 2, FOND + 3):
                if W.get(x, y, z) is None:
                    pass
    # wheelhouse at the stern: x 21..28, deck house (stand yd+1) and bridge (floor y yd+4, stand yd+5)
    sx0, sx1 = x0 + 1, x0 + 8
    for x in range(sx0, sx1 + 1):
        for z in range(zc - 3, zc + 4):
            bord = x in (sx0, sx1) or z in (zc - 3, zc + 3)
            for y in range(yd + 1, yd + 4):
                if bord:
                    W.set(x, y, z, M + "white_wool" if y < yd + 3 else M + "white_wool")
            W.set(x, yd + 4, z, M + "white_wool" if bord else M + "spruce_planks")
            for y in range(yd + 5, yd + 8):
                if bord:
                    vitre = y == yd + 6 and x in (sx1,) or (y == yd + 6 and z in (zc - 3, zc + 3) and x % 2 == 0)
                    W.set(x, y, z, M + "glass_pane" if vitre else M + "white_wool")
            W.set(x, yd + 8, z, M + "gray_concrete")
    # door to the deck house (east) and inner stair to the bridge (along the north wall, going west)
    W.clear(sx1, yd + 1, zc, sx1, yd + 2, zc)
    for i in range(4):
        x = sx1 - 2 - i
        if i < 3:
            W.set(x, yd + 4, zc - 2, None)
        W.set(x, yd + 1 + i, zc - 2, marche(M + "spruce", "west"))
        for y in range(yd + 1, yd + 1 + i):
            W.set(x, y, zc - 2, M + "spruce_planks")
    W.set(sx0 + 1, yd + 4, zc - 2, M + "spruce_planks")
    W.set(sx0 + 3, yd + 5, zc + 1, oriente(D + "console_navigation", "east"))
    W.set(sx0 + 4, yd + 2, zc + 1, LUMIERE)
    W.set(sx0 + 4, yd + 6, zc, LUMIERE)
    # funnel
    for y in range(yd + 9, yd + 13):
        for x in (sx0 + 3, sx0 + 4):
            for z in (zc - 1, zc):
                W.set(x, y, z, M + "black_concrete" if y > yd + 10 else M + "red_terracotta")
    # mast at the bow
    for y in range(yd + 1, yd + 7):
        W.set(x1 - 7, y, zc, M + "iron_bars")
    # gangway from the A quay (z QUAI) up to the deck, on iron posts (x 55..56)
    for x in (55, 56):
        for i, z in enumerate(range(QUAI + 1, z0 - 0)):
            y = S + min(i, 2)
            if z >= zc - largeur(x):
                break
            W.set(x, y, z, marche(M + "stone_brick", "south") if i < 3 else M + "light_blue_wool")
            for yy in range(FOND + 1, y):
                W.set(x, yy, z, M + "iron_bars" if yy < y - 1 else M + "netherite_block")
        W.set(x, yd + 1, zc - largeur(x), None)
    # stairs down the hull's south side to the basin floor (x 44..49, going west)
    zs = zc + 6
    for i in range(6):
        x = 49 - i
        W.set(x, yd - i, zs, marche(M + "stone_brick", "east"))
        for y in range(FOND + 1, yd - i):
            W.set(x, y, zs, M + "black_concrete")
    W.set(49, yd + 1, zc + 5, None)
    W.set(36, yd + 2, zc, LUMIERE_F)


def caisses_y(x, z, motif, y):
    caisses(x, z, motif, y=y)


def jetee():
    """Wooden jetty x 96..99 from the A quay into the basin (deck at ground level on log piles), hut at its end,
    small boats beside it; one can walk under the deck on the basin floor."""
    for x in range(96, 100):
        for z in range(QUAI + 1, 79):
            W.set(x, G, z, M + "spruce_planks" if z % 3 else M + "dark_oak_planks")
        for z in range(QUAI + 3, 79, 4):
            if x in (96, 99):
                for y in range(FOND + 1, G):
                    W.set(x, y, z, tronc(M + "spruce_log"))
    for z in range(QUAI + 1, 79):
        W.set(96, S, z, garde_corps("west"))
        W.set(99, S, z, garde_corps("east"))
    # hut
    for x in range(96, 100):
        for z in range(74, 78):
            bord = x in (96, 99) or z in (74, 77)
            if bord:
                for y in range(S, S + 3):
                    W.set(x, y, z, M + "gold_block" if (x + y) % 4 else M + "coal_block")
            W.set(x, S + 3, z, dalle(D + "tole_toit_rouille_dalle", "bottom"))
    W.clear(97, S, 74, 98, S + 1, 74)
    W.set(98, S, 76, etat(M + "barrel", facing="up", open="false"))
    W.set(97, S + 2, 76, LUMIERE_F)
    barque(101, 70, "z")
    barque(91, 74, "z")


def barque(x0, z0, axe="z"):
    """Small wooden boat lying on the basin floor (2 high hull, open)."""
    for i in range(7):
        for j in range(3):
            x, z = (x0 + j, z0 + i) if axe == "z" else (x0 + i, z0 + j)
            bout = i in (0, 6)
            if bout and j != 1:
                continue
            bord = j in (0, 2) or bout
            W.set(x, FOND + 1, z, M + "dark_oak_planks")
            if bord:
                W.set(x, FOND + 2, z, M + "spruce_planks")


def barge(x0, z0, long=14):
    """Grounded steel barge along x with a deck house (cover at quay height in the middle of the basin)."""
    for x in range(x0, x0 + long):
        for z in range(z0, z0 + 5):
            bord = z in (z0, z0 + 4) or x in (x0, x0 + long - 1)
            for y in range(FOND + 1, G + 1):
                if bord or y == FOND + 1:
                    W.set(x, y, z, M + "black_concrete" if (x + y) % 5 else M + "lime_terracotta")
            W.set(x, G, z, M + "lime_terracotta" if not bord else M + "black_concrete")
    for x in range(x0 + 4, x0 + 9):
        for z in range(z0 + 1, z0 + 4):
            for y in (S, S + 1):
                if x in (x0 + 4, x0 + 8) or z in (z0 + 1, z0 + 3):
                    W.set(x, y, z, M + "white_wool")
            W.set(x, S + 2, z, M + "gray_concrete")
    W.clear(x0 + 8, S, z0 + 2, x0 + 8, S + 1, z0 + 2)
    for y in range(S, S + 4):
        W.set(x0 + 11, y, z0 + 2, M + "iron_bars")


def duc_albe(x, z):
    """Mooring dolphin: concrete pile cap 2 x 2 rising from the basin floor to y S+1."""
    for xx in (x, x + 1):
        for zz in (z, z + 1):
            for y in range(FOND + 1, S + 2):
                W.set(xx, y, zz, M + "smooth_stone" if y < S + 1 else M + "polished_deepslate")


# ------------------------------------------------------------------------------------------------ quay
def grue(x0, z0=56):
    """Portal crane on the quay rails: 4 legs (x0, x0+6) x (z0, z0+6) up to y S+7, portal girders, machinery house
    (floor y S+8, stand S+9) with windows, boom over the basin with a hook. Stair inside the north-west leg."""
    x1, z1 = x0 + 6, z0 + 6
    jaune = M + "yellow_terracotta"
    for (x, z) in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for y in range(S, S + 8):
            W.set(x, y, z, jaune if y % 2 else M + "black_concrete")
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            W.set(x, S + 8, z, jaune)
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            W.set(x, S + 8, z, jaune)
    # machinery house platform and house
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            W.set(x, S + 8, z, M + "light_blue_wool")
    for x in range(x0 + 1, x1):
        for z in range(z0 + 1, z1):
            bord = x in (x0 + 1, x1 - 1) or z in (z0 + 1, z1 - 1)
            if bord:
                for y in range(S + 9, S + 12):
                    vitre = y == S + 10 and (z == z1 - 1 or x in (x0 + 1, x1 - 1)) and (x + z) % 2 == 0
                    W.set(x, y, z, M + "gray_stained_glass_pane" if vitre else jaune)
            W.set(x, S + 12, z, M + "black_concrete")
    W.clear(x0 + 3, S + 9, z1 - 1, x0 + 3, S + 10, z1 - 1)
    for x in range(x0, x1 + 1):
        W.set(x, S + 9, z1, garde_corps("south") if x not in (x0, x1) else None)
    W.set(x0 + 3, S + 10, z0 + 3, LUMIERE_F)
    # boom toward the basin, horizontal at the roof level, hook on a chain at its tip
    for z in range(z1 - 1, z1 + 15):
        W.set(x0 + 3, S + 12, z, jaune if (z - z1) % 3 else M + "black_concrete")
    for y in range(S + 8, S + 12):
        W.set(x0 + 3, y, z1 + 14, etat(M + "chain", axis="y", waterlogged="false"))
    W.set(x0 + 3, S + 7, z1 + 14, D + "crochet_grue")
    # straight stair west of the portal, rising north from z1 + 1 (y S) to z0 (y S + 7), then onto the frame
    for i in range(8):
        z = z1 + 1 - i
        for yy in range(S, S + i):
            W.set(x0 - 1, yy, z, jaune if yy % 2 else M + "black_concrete")
        W.set(x0 - 1, S + i, z, marche(M + "stone_brick", "north"))
    W.set(x0, S + 9, z0, None)


def douane():
    """Customs house (spawn A): white plaster, one storey, flat roof, glazed front with doors to the quay side."""
    x0, x1, z0, z1 = 76, 93, 2, 11
    for y in range(S, S + 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, D + ("enduit_blanc_use" if h(x, y) % 5 == 0 else "enduit_blanc") if y > S else M + "stone")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, D + ("enduit_blanc_use" if h(z, y) % 5 == 0 else "enduit_blanc") if y > S else M + "stone")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "quartz_bricks")
    W.fill(x0, S + 4, z0, x1, S + 4, z1, M + "light_blue_terracotta")
    for x in range(x0, x1 + 1):
        W.set(x, S + 5, z0, M + "smooth_stone")
        W.set(x, S + 5, z1, M + "smooth_stone")
    for z in range(z0, z1 + 1):
        W.set(x0, S + 5, z, M + "smooth_stone")
        W.set(x1, S + 5, z, M + "smooth_stone")
    for x in (79, 80, 89, 90):
        W.clear(x, S, z1, x, S + 2, z1)
    for x in (83, 84, 85, 86):
        W.set(x, S + 2, z1, M + "gray_stained_glass_pane")
    W.clear(x0, S, 8, x0, S + 1, 9)
    W.clear(x1, S, 4, x1, S + 1, 5)
    for x in range(78, 92, 3):
        W.set(x, S, z0 + 1, oriente(C + "arkaniaz_office_drawers", "south"))
    W.set(84, S, 9, oriente(D + "comptoir_caisse", "north"))
    W.set(85, S, 9, oriente(D + "comptoir_caisse", "north"))
    for x in (79, 84, 89):
        W.set(x, S + 3, 6, LUMIERE)
    for x in range(78, 92):
        W.set(x, S + 3, z1 + 1, dalle(M + "smooth_stone", "top"))
    for x in (78, 91):
        for y in range(S, S + 3):
            W.set(x, y, z1 + 1, M + "stone_brick_wall")


def entrepot(x0=104, z0=17):
    """Warehouse 40 x 22 (walls 9 high): pallet racks in aisles, a mezzanine office along the west wall (stand S+5)
    with stairs, loading doors on both long sides, saw-tooth roof with skylights."""
    x1, z1 = x0 + 39, z0 + 21
    for y in range(S, S + 9):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "bricks" if y < S + 4 else M + "pink_wool")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "bricks" if y < S + 4 else M + "pink_wool")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "smooth_stone")
    # loading doors (4 wide, 4 high)
    for xa in (x0 + 8, x0 + 22, x0 + 33):
        W.clear(xa, S, z1, xa + 3, S + 3, z1)
    for xa in (x0 + 14, x0 + 28):
        W.clear(xa, S, z0, xa + 3, S + 3, z0)
    W.clear(x1, S, z0 + 9, x1, S + 2, z0 + 11)
    # high windows
    for x in range(x0 + 2, x1 - 1, 4):
        W.set(x, S + 6, z0, M + "iron_bars")
        W.set(x, S + 6, z1, M + "gray_stained_glass_pane")
    # pallet racks: rows along x with gaps (2 high frames of iron bars with crates)
    for zr in (z0 + 5, z0 + 10, z0 + 15):
        for x in range(x0 + 12, x1 - 2):
            if (x - x0) % 9 in (0, 1):
                continue
            W.set(x, S, zr, M + "iron_block" if (x + zr) % 3 else M + "orange_wool")
            W.set(x, S + 1, zr, M + "spruce_slab" and dalle(M + "spruce", "top"))
            W.set(x, S + 2, zr, M + "terracotta" if (x * 7 + zr) % 4 else None)
    # mezzanine over the west half (x x0+1..x0+18, floor y S+4, stand S+5), posts, railing on its east edge,
    # stair along its east side rising north (x x0+19)
    xm = x0 + 18
    for x in range(x0 + 1, xm + 1):
        for z in range(z0 + 1, z1):
            W.set(x, S + 4, z, M + "spruce_planks" if (x + z) % 7 else M + "dark_oak_planks")
    for z in range(z0 + 1, z1):
        W.set(xm, S + 5, z, garde_corps("east"))
    for x in range(x0 + 6, xm + 1, 6):
        for z in (z0 + 5, z0 + 11, z0 + 16):
            for y in range(S, S + 4):
                W.set(x, y, z, tronc(M + "stripped_dark_oak_log"))
    for i in range(4):
        z = z1 - 2 - i
        W.set(xm + 1, S + i, z, marche(M + "spruce", "north"))
        for y in range(S, S + i):
            W.set(xm + 1, y, z, M + "spruce_planks")
    W.set(xm + 1, S + 4, z1 - 6, M + "spruce_planks")
    W.set(xm, S + 5, z1 - 6, None)
    # offices on the mezzanine: partitions and furniture
    for z in (z0 + 7, z0 + 14):
        for x in range(x0 + 1, x0 + 8):
            for y in (S + 5, S + 6):
                W.set(x, y, z, M + "white_wool") if x != x0 + 4 else None
    W.set(x0 + 2, S + 5, z0 + 3, oriente(D + "table_metal", "east"))
    W.set(x0 + 2, S + 5, z0 + 10, oriente(C + "arkaniaz_office_drawers", "east"))
    # saw-tooth roof (teeth along x), skylights
    for x in range(x0, x1 + 1):
        k = (x - x0) % 6
        for z in range(z0, z1 + 1):
            W.set(x, S + 9, z, M + "black_concrete" if k else M + "glass")
            if k in (1, 2):
                W.set(x, S + 10, z, marche(D + "tole_toit_rouille_escalier", "east"))
            elif k == 3:
                W.set(x, S + 10, z, D + "tole_toit_rouille")
    for x in range(x0 + 4, x1 - 2, 8):
        for z in (z0 + 5, z0 + 15):
            W.set(x, S + 7, z, LUMIERE)
    W.set(x0 + 4, S + 7, z0 + 10, LUMIERE)


def capitainerie():
    """Port office: 3-storey block (stairwell to every floor), white plaster with blue bands, no balconies."""
    st = dict(F.STYLE_BRIQUE)
    st.update({"mur": D + "enduit_blanc", "mur_var": D + "enduit_blanc_use", "bandeau": M + "light_blue_terracotta",
               "socle": M + "stone", "acrotere": M + "light_blue_terracotta", "edicule": D + "enduit_blanc",
               "cloison": M + "white_wool", "peinture_bas": M + "light_blue_terracotta", "peinture_haut": M + "white_wool"})
    rep = F.Repere(60, 16, "+x", "+z")
    F.Barre(W, rep, 26, etages=4, style=st, podyezds=(9,), balcons=False, graine=7).construire()


def conteneurs_ouest():
    """Container yard (west, x 2..58, z 12..52): 7 rows of stacks along x (1 to 3 high, mostly 2-3), aisles 3 wide,
    cross aisles every few containers: a maze of steel corridors. A container truck and a forklift in the aisles."""
    couleurs = ["bleu", "rouge", "gris", "orange", "vert", "blanc"]
    k = 0
    for r, zr in enumerate(range(12, 50, 6)):
        x = 2 + (r % 2) * 3
        while x + 5 <= 58:
            if (x // 7 + r) % 5 == 2:          # cross aisle
                x += 4
                continue
            n = 3 if h(x, zr) % 3 else 2
            for niv in range(n):
                KT.conteneur(x, zr, axe="x", couleur=couleurs[(k + niv * 2) % 6], niveau=niv)
            k += 1
            x += 7
    W.set(48, S, 33, oriente(C + "arkaniaz_forklift", "west"))
    caisses(57, 16, ["2", "21"])
    caisses(25, 52, ["21"])


def hangar_ouvert(x0=104, z0=42, long=40):
    """Open storage shed: roof on concrete pillars (y S+5), stacks of pallets and crates underneath."""
    x1, z1 = x0 + long - 1, z0 + 8
    for x in range(x0, x1 + 1, 6):
        for z in (z0, z1):
            for y in range(S, S + 5):
                W.set(x, y, z, M + "stone_brick_wall")
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            W.set(x, S + 5, z, D + "tole_toit_rouille" if (z - z0) % 4 else M + "black_concrete")
    for x in range(x0 + 2, x1 - 1, 5):
        caisses(x, z0 + 2, ["22", "2.", "1"])
        W.set(x + 3, S, z0 + 6, oriente(C + "wooden_pallet_1", "north"))
    for x in range(x0 + 6, x1, 12):
        W.set(x, S + 4, z0 + 4, LUMIERE_F)


def garage_camions(x0=90, z0=14):
    """Truck garage: brick shed 12 x 16 with two big doors to the south, a truck inside, inspection pit, tin roof."""
    x1, z1 = x0 + 11, z0 + 15
    for y in range(S, S + 6):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "bricks")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "bricks")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "smooth_stone")
    for xa in (x0 + 1, x0 + 7):
        W.clear(xa, S, z1, xa + 3, S + 4, z1)
    W.clear(x0, S, z0 + 4, x0, S + 1, z0 + 5)
    for x in range(x0 - 1, x1 + 2):
        for z in range(z0 - 1, z1 + 2):
            W.set(x, S + 6, z, D + "tole_toit_rouille")
    KT.camion_conteneur(x0 + 3, z0 + 3, "south")
    for z in range(z0 + 3, z0 + 12):
        W.set(x0 + 9, G, z, None)
        W.set(x0 + 9, G - 1, z, None)
        W.set(x0 + 9, G - 2, z, M + "polished_andesite")
        for x in (x0 + 8, x0 + 10):
            W.set(x, G - 1, z, M + "stone_bricks")
            W.set(x, G - 2, z, M + "stone_bricks")
    for x in (x0 + 9,):
        W.set(x, G - 1, z0 + 2, M + "stone_bricks")
        W.set(x, G - 2, z0 + 2, M + "stone_bricks")
        W.set(x, G - 1, z0 + 12, marche(M + "stone_brick", "south"))
        W.set(x, G - 2, z0 + 12, M + "stone_bricks")
    caisses(x0 + 1, z0 + 1, ["21", "1"])
    W.set(x0 + 10, S, z0 + 1, M + "crafting_table")
    for z in (z0 + 4, z0 + 11):
        W.set(x0 + 6, S + 4, z, LUMIERE)


def cantine(x0=60, z0=40):
    """Dockers' canteen: one storey, white plaster, windows, tables inside, flat roof."""
    x1, z1 = x0 + 13, z0 + 9
    for y in range(S, S + 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, D + "enduit_blanc" if y > S else M + "stone")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, D + "enduit_blanc" if y > S else M + "stone")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "quartz_bricks")
    W.fill(x0, S + 4, z0, x1, S + 4, z1, M + "black_concrete")
    for x in range(x0 + 2, x1 - 1, 3):
        W.set(x, S + 2, z1, M + "glass_pane")
        W.set(x, S + 2, z0, M + "glass_pane")
    W.clear(x0 + 6, S, z1, x0 + 7, S + 2, z1)
    W.clear(x1, S, z0 + 3, x1, S + 1, z0 + 4)
    for x in range(x0 + 2, x1 - 1, 3):
        W.set(x, S, z0 + 3, oriente(D + "table_metal", "south"))
        W.set(x, S, z0 + 6, oriente(D + "table_metal", "south"))
    W.set(x0 + 1, S, z0 + 1, oriente(D + "comptoir_caisse", "east"))
    for x in (x0 + 3, x0 + 9):
        W.set(x, S + 3, z0 + 4, LUMIERE)


def epi_ferre():
    """Rail spur along x 96 into the warehouse yard, with two boxcars (north-south cover)."""
    KT.voie(97, 32, 97, 54)
    KT.wagon_couvert(96, 32, long=9, couleur=M + "brown_terracotta")
    KT.wagon_couvert(96, 43, long=8, couleur=M + "light_blue_terracotta")


def bateaux_sur_bers():
    """Repair yard (east, x 146..166, z 26..52): boats on cradles (3 high hulls), a small slipway winch, crates."""
    for (x0, z0) in ((149, 40), (158, 44)):
        for i in range(8):
            for j in range(4):
                x, z = x0 + j, z0 + i
                bout = i in (0, 7)
                if bout and j in (0, 3):
                    continue
                W.set(x, S, z, M + "spruce_fence" if (i in (1, 6) and j in (0, 3)) else None)
                bord = j in (0, 3) or bout
                for y in (S + 1, S + 2):
                    if bord:
                        W.set(x, y, z, M + "white_wool" if y == S + 2 else M + "red_terracotta")
                if not bord:
                    W.set(x, S + 1, z, M + "spruce_planks")
        for y in (S, S + 1):
            pass
    caisses(163, 37, ["21", "1"])
    W.set(155, S, 38, oriente(D + "treuil_cable", "west"))
    caisses(146, 52, ["2"])


def quai_marchandises():
    """Goods on the quay apron: container stacks near the cranes, crates on pallets, pipes."""
    for (x0, z0, n, c) in ((2, 56, 2, "bleu"), (30, 61, 1, "gris"), (100, 61, 2, "rouge"), (144, 56, 3, "vert"),
                           (160, 60, 1, "orange")):
        for niv in range(n):
            KT.conteneur(x0, z0, axe="x", couleur=c, niveau=niv, long=6)
    for (x, z) in ((56, 63), (112, 56), (90, 63)):
        caisses(x, z, ["22", "1"])
    for x in range(64, 70):
        W.set(x, S, 55, tronc(M + "stripped_dark_oak_log", "x"))
        W.set(x, S + 1, 55, tronc(M + "stripped_dark_oak_log", "x")) if x < 68 else None


def reservoirs():
    """Fuel tanks in a bund (north-east corner)."""
    for x in range(145, 168):
        for z in (3, 22):
            W.set(x, S, z, M + "smooth_stone")
    for z in range(3, 23):
        W.set(145, S, z, M + "smooth_stone")
    W.set(145, S, 12, None)
    W.set(145, S, 13, None)
    KT.citerne_ronde(152.5, 9.5, 5, 8, mat=M + "smooth_stone", cercle=M + "light_gray_concrete")
    KT.citerne_ronde(161.5, 15.5, 4, 7, mat=M + "smooth_stone", cercle=M + "light_gray_concrete")
    W.set(150, S, 19, oriente(D + "pompe_hydraulique", "north"))


def quai_divers():
    """Rails along the quay with wagons, cars, crates, lamp posts, bollards; cover on the aprons."""
    KT.voie(8, 59, 160, 59)
    KT.wagon_couvert(24, 58, long=8, axe="x", couleur=M + "brown_terracotta")
    KT.tombereau(66, 58, long=7, axe="x")
    KT.wagon_citerne(128, 58, long=7, axe="x")
    for (x, z) in ((40, 62), (104, 63), (150, 62), (62, 54)):
        caisses(x, z, ["21", "1"])
    for (x, z) in ((12, 62), (78, 61), (138, 63)):
        benne(x, z, "x")
    voiture(75, 48, "north", U + "epave_rouge")
    voiture(98, 50, "east", U + "epave_brulee")
    KT.camion_conteneur(132, 47, "west")
    for x in (20, 60, 100, 140):
        lampadaire(x, 53, "south")
    # north apron: cover, barriers and kiosk-like booths between the buildings
    for (x, z) in ((70, 14), (98, 9), (58, 8), (110, 48), (150, 30), (160, 45)):
        caisses(x, z, ["2", "12"])
    kiosque(66, 3, "south")
    kiosque(100, 4, "south")
    for (x0, z0, x1, z1) in ((72, 40, 75, 40), (94, 30, 97, 30), (150, 52, 153, 52), (10, 54, 13, 54)):
        barrage(x0, z0, x1, z1)


def centre_renforts():
    """Cover close to the centre lane and the basin edges."""
    duc_albe(50, 66)
    duc_albe(120, 68)
    duc_albe(66, 82)
    barge(36, 83, long=14)
    barque(126, 73, "z")
    barque(70, 70, "z")
    for (x, z) in ((90, 60), (79, 57), (104, 58)):
        caisses(x, z, ["2"])


def hangar_bateaux(x0=2, z0=67):
    """Boat shed at the west end of the basin (ground strip x 2..12): wooden walls, pitched tin roof, a boat inside,
    open toward the south (B side) and a door to the north."""
    x1, z1 = x0 + 10, z0 + 15
    for y in range(S, S + 5):
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "gold_block" if (z + y) % 5 else M + "coal_block")
        for x in range(x0, x1 + 1):
            W.set(x, y, z0, M + "gold_block")
    W.clear(x0 + 3, S, z0, x0 + 5, S + 2, z0)
    for k in range(5):
        y = S + 5 + k
        for z in range(z0 - 1, z1 + 2):
            W.set(x0 + k, y, z, marche(D + "tole_toit_rouille_escalier", "east"))
            W.set(x1 - k, y, z, marche(D + "tole_toit_rouille_escalier", "west"))
        for xx in range(x0 + k + 1, x1 - k):
            if W.get(xx, y, z0) is None:
                W.set(xx, y, z0, M + "gold_block")
    for z in range(z0 - 1, z1 + 2):
        W.set(x0 + 4, S + 9, z, dalle(D + "tole_toit_rouille_dalle", "bottom"))
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1, M + "spruce_planks")
    # a boat on its cradle inside
    for i in range(9):
        for j in range(3):
            x, z = x0 + 3 + j, z0 + 3 + i
            bout = i in (0, 8)
            if bout and j != 1:
                continue
            W.set(x, S, z, M + "spruce_fence" if i in (1, 7) and j != 1 else None)
            if j in (0, 2) or bout:
                W.set(x, S + 1, z, M + "red_terracotta")
                W.set(x, S + 2, z, M + "white_wool")
            else:
                W.set(x, S + 1, z, M + "spruce_planks")
    caisses(x0 + 1, z0 + 2, ["2", "21"])
    W.set(x0 + 4, S + 4, z0 + 8, LUMIERE)


def bout_est(x0=157, z0=67):
    """East end of the basin (ground strip x 157..167): container stacks and a crane winch, crates."""
    KT.conteneur(x0, z0 + 2, axe="x", couleur="gris", niveau=0)
    KT.conteneur(x0, z0 + 2, axe="x", couleur="rouge", niveau=1)
    KT.conteneur(x0 + 2, z0 + 9, axe="x", couleur="bleu", niveau=0)
    caisses(x0, z0 + 14, ["21", "1"])
    W.set(x0 + 7, S, z0 + 7, oriente(D + "treuil_cable", "north"))


def remorqueur(x0=120, z0=66):
    """Small tug lying against the quay in the basin: hull 4 wide on the floor up to ground level, wheelhouse above."""
    for x in range(x0, x0 + 9):
        for z in range(z0, z0 + 4):
            bord = z in (z0, z0 + 3) or x in (x0, x0 + 8)
            for y in range(FOND + 1, G + 1):
                if bord or y in (FOND + 1, G):
                    W.set(x, y, z, M + "black_concrete" if y < G - 1 else M + "red_terracotta")
    for x in range(x0 + 2, x0 + 6):
        for z in range(z0, z0 + 4):
            bord = x in (x0 + 2, x0 + 5) or z in (z0, z0 + 3)
            for y in (S, S + 1, S + 2):
                if bord:
                    W.set(x, y, z, M + "white_wool" if y != S + 1 or (x + z) % 2 else M + "glass_pane")
            W.set(x, S + 3, z, M + "gray_concrete")
    W.clear(x0 + 5, S, z0 + 1, x0 + 5, S + 1, z0 + 1)
    W.set(x0 + 3, S + 2, z0 + 1, LUMIERE_F)
    for y in range(S + 4, S + 6):
        W.set(x0 + 3, y, z0 + 2, M + "black_concrete")


def place_centrale():
    """Cover on the plaza between the customs house and the quay (centre lane): weighbridge hut, trucks, blocks."""
    # weighbridge hut and its scale plate
    for x in range(80, 85):
        for z in range(36, 40):
            bord = x in (80, 84) or z in (36, 39)
            for y in range(S, S + 3):
                if bord:
                    W.set(x, y, z, M + "yellow_concrete" if y != S + 1 or x in (80, 84) else M + "glass_pane")
            W.set(x, S + 3, z, M + "lapis_block")
    W.clear(84, S, 37, 84, S + 1, 37)
    for x in range(78, 90):
        for z in range(42, 47):
            W.set(x, G, z, M + "polished_deepslate" if (x + z) % 2 else M + "light_blue_wool")
    KT.camion_conteneur(87, 48, "north")
    voiture(80, 50, "east", U + "epave_bleue")
    for (x0, z0, x1, z1) in ((76, 32, 78, 32), (91, 41, 93, 41)):
        barrage(x0, z0, x1, z1)
    caisses(88, 33, ["2", "12"])
    caisses(78, 54, ["21"])


def renforts_port():
    """Cross-blocks in the yard aisles, a row of goods between the yard and the quay, the north strip, the bridge."""
    for (x0, z0) in ((20, 15), (44, 21), (8, 27), (32, 33), (52, 39), (16, 45), (40, 45)):
        for z in range(z0, z0 + 3):
            for y in (S, S + 1):
                W.set(x0, y, z, M + "iron_block" if (z + y) % 2 else M + "orange_wool")
    for x0 in (6, 22, 36, 48, 62, 112, 128, 140):
        KT.conteneur(x0, 51, axe="x", couleur=["blanc", "gris", "rouge", "bleu"][x0 % 4], niveau=0, long=5)
    for x0 in (106, 118, 130):
        KT.conteneur(x0, 8, axe="x", couleur=["gris", "orange", "bleu"][x0 % 3], niveau=0, long=6)
        KT.conteneur(x0, 8, axe="x", couleur=["vert", "rouge", "gris"][x0 % 3], niveau=1, long=6)
    for x in (58, 92, 112, 150):
        caisses(x, 3, ["2", "2"])
    for (x, z) in ((30, 63), (110, 63), (150, 63), (70, 63)):
        W.set(x, S, z, oriente(D + "treuil_cable", "south"))
        W.set(x, S, z - 1, M + "iron_block")
        W.set(x, S + 1, z - 1, M + "iron_block")
    # bridge: keeper's cabin at the centre (symmetric), staggered barricades
    for x in (84, 85):
        for z in (83, 84):
            for y in range(S, S + 2):
                W.set(x, y, z, M + "yellow_terracotta")
            W.set(x, S + 2, z, dalle(M + "smooth_stone", "bottom"))
    barrage(83, 72, 84, 72)
    barrage(85, 77, 86, 77)
    # perimeter strip along the north fence west of the customs
    for x in (10, 30, 46):
        caisses(x, 3, ["21", "2"])


def barge_conteneurs(x0=100, z0=76, long=18):
    """Container barge grounded in the basin: steel hull up to ground level, two rows of containers on deck."""
    for x in range(x0, x0 + long):
        for z in range(z0, z0 + 7):
            bord = z in (z0, z0 + 6) or x in (x0, x0 + long - 1)
            for y in range(FOND + 1, G + 1):
                if bord or y in (FOND + 1, G):
                    W.set(x, y, z, M + "black_concrete" if (x + y) % 6 else M + "lime_terracotta")
    KT.conteneur(x0 + 1, z0 + 1, axe="x", couleur="rouge", niveau=0, long=6)
    KT.conteneur(x0 + 1, z0 + 1, axe="x", couleur="gris", niveau=1, long=6)
    KT.conteneur(x0 + 9, z0 + 4, axe="x", couleur="bleu", niveau=0, long=6)
    W.set(x0 + 15, S, z0 + 2, oriente(D + "treuil_cable", "west"))


def drague(x0=130, z0=72):
    """Bucket dredger grounded in the basin: hull, machinery house, tall ladder frame (to y S+8)."""
    for x in range(x0, x0 + 15):
        for z in range(z0, z0 + 6):
            bord = z in (z0, z0 + 5) or x in (x0, x0 + 14)
            for y in range(FOND + 1, G + 1):
                if bord or y in (FOND + 1, G):
                    W.set(x, y, z, M + "black_concrete" if (x + z) % 4 else M + "yellow_terracotta")
    for x in range(x0 + 2, x0 + 8):
        for z in range(z0 + 1, z0 + 5):
            bord = x in (x0 + 2, x0 + 7) or z in (z0 + 1, z0 + 4)
            for y in (S, S + 1, S + 2):
                if bord:
                    W.set(x, y, z, M + "yellow_terracotta" if y != S + 1 or (x + z) % 3 else M + "gray_stained_glass_pane")
            W.set(x, S + 3, z, M + "black_concrete")
    W.clear(x0 + 7, S, z0 + 2, x0 + 7, S + 1, z0 + 2)
    for y in range(S, S + 9):
        for z in (z0 + 1, z0 + 4):
            W.set(x0 + 10, y, z, M + "yellow_terracotta" if y % 2 else M + "black_concrete")
    for z in range(z0 + 1, z0 + 5):
        W.set(x0 + 10, S + 8, z, M + "yellow_terracotta")
    for x in range(x0 + 11, x0 + 15):
        W.set(x, S + 8, z0 + 2, M + "black_concrete")
        W.set(x, S + 8, z0 + 3, M + "black_concrete")
    for y in range(G + 1, S + 8):
        W.set(x0 + 14, y, z0 + 2, etat(M + "chain", axis="y", waterlogged="false"))
    W.set(x0 + 4, S + 2, z0 + 2, LUMIERE_F)


def chalutier(x0=70, z0=68):
    """Grounded fishing boat along x with a wheelhouse (above ground level): cover at eye level in the basin."""
    for x in range(x0, x0 + 10):
        for z in range(z0, z0 + 4):
            bord = z in (z0, z0 + 3) or x in (x0, x0 + 9)
            for y in range(FOND + 1, G):
                if bord or y == FOND + 1:
                    W.set(x, y, z, M + "blue_concrete" if y < G - 1 else M + "white_wool")
            W.set(x, G, z, M + "spruce_planks" if not bord else M + "white_wool")
    for x in range(x0 + 1, x0 + 5):
        for z in range(z0, z0 + 4):
            bord = x in (x0 + 1, x0 + 4) or z in (z0, z0 + 3)
            for y in (S, S + 1, S + 2):
                if bord:
                    W.set(x, y, z, M + "glass_pane" if (y == S + 2 and (x + z) % 2) else M + "white_wool")
            W.set(x, S + 3, z, M + "red_terracotta")
    for y in range(S, S + 5):
        W.set(x0 + 7, y, z0 + 2, M + "iron_bars")


def renforts_port2():
    # dolphins at the quay foot (rows z 66..67) and at the basin ends
    for (x, z) in ((100, 66), (140, 66), (30, 66), (14, 75), (154, 72), (92, 79)):
        duc_albe(x, z)
    barge_conteneurs()
    drague()
    chalutier()
    # containers along the north fence (strip z 3..11) and north of the office / garage
    for (x0, z0, n) in ((4, 3, 2), (20, 7, 1), (36, 3, 3), (52, 7, 2), (103, 12, 2), (140, 7, 1)):
        for niv in range(n):
            KT.conteneur(x0, z0, axe="x", couleur=["gris", "bleu", "rouge", "orange"][(x0 + niv) % 4], niveau=niv, long=6)
    caisses(70, 13, ["2"])
    caisses(87, 12, ["21"])
    # more aisle cross-blocks in the yard
    for (x0, z0) in ((36, 15), (12, 21), (30, 27), (50, 27), (10, 33), (26, 39), (44, 39), (30, 45), (55, 21)):
        for z in range(z0, z0 + 3):
            for y in (S, S + 1):
                W.set(x0, y, z, M + "iron_block" if (z + y) % 2 else M + "terracotta")
    W.set(76, S, 29, M + "iron_block")
    W.set(76, S + 1, 29, M + "orange_wool")
    W.set(77, S, 29, M + "iron_block")
    # quay edge row (z 64): winch stacks
    for x in (24, 64, 104, 146):
        W.set(x, S, 64, M + "iron_block")
        W.set(x, S + 1, 64, M + "iron_block")
        W.set(x + 1, S, 64, oriente(D + "treuil_cable", "south"))
    for x in (90, 125, 150):
        caisses(x, 54, ["2"])
    # boat shed reaches the fence and the basin wall (no strip on either side)


def silo(x0=146, z0=24):
    """Grain silo: four concrete cylinders (radius 2.5, 14 high) under an elevator house; landmark of the east side."""
    for (cx, cz) in ((x0 + 3, z0 + 3), (x0 + 9, z0 + 3), (x0 + 3, z0 + 9), (x0 + 9, z0 + 9)):
        KT.citerne_ronde(cx + 0.5, cz + 0.5, 3, 14, mat=M + "light_gray_concrete", cercle=M + "smooth_stone")
    for x in range(x0 + 2, x0 + 11):
        for z in range(z0 + 2, z0 + 11):
            bord = x in (x0 + 2, x0 + 10) or z in (z0 + 2, z0 + 10)
            if bord:
                for y in range(S + 14, S + 18):
                    W.set(x, y, z, M + "light_gray_concrete" if (y - S) % 2 else M + "gray_stained_glass" if x % 3 == 0 else M + "light_gray_concrete")
            W.set(x, S + 18, z, M + "gray_concrete")
    for y in range(S, S + 3):
        W.set(x0 + 6, y, z0 + 6, M + "light_gray_concrete")


def renforts_port3():
    for (x, z) in ((74, 50), (94, 45)):
        caisses(x, z, ["22", "2"])
    for (x, z) in ((74, 75), (94, 70), (110, 70), (64, 70)):
        duc_albe(x, z)
    caisses(64, 33, ["2"])
    for z in (70, 78):
        for x in (13, 156):
            W.set(x, S, z, M + "polished_deepslate")
            W.set(x, S + 1, z, dalle(M + "polished_deepslate", "bottom"))
    for (x, z) in ((100, 55), (130, 55), (155, 55)):
        caisses(x, z, ["2"])
    for z in (27, 40, 52, 60):
        caisses(166, z, ["2", "2"])
    caisses(155, 37, ["2"])
    caisses(156, 48, ["21"])



def renforts_port4():
    for z in (14, 33, 58, 75):
        caisses(167, z, ["2"])
    duc_albe(17, 81)
    duc_albe(80, 72)
    duc_albe(125, 73)
    duc_albe(55, 82)
    duc_albe(25, 83)
    barrage(85, 67, 86, 67)
    for x in (15, 45, 65):
        caisses(x, 2, ["2"])
    for x in (14, 30, 60):
        caisses(x, 6, ["2"])
    for x in (12, 46, 66):
        caisses(x, 10, ["2", "2"])
    for x in (110, 130, 150, 163):
        caisses(x, 39, ["2"])
    for x in (112, 136, 158):
        caisses(x, 2, ["2"])
    duc_albe(115, 71)


# ------------------------------------------------------------------------------------------------ meta
def meta(n):
    cx, cz = centre()
    a = [[81.5, float(S), 6.5, 0.0], [84.5, float(S), 6.5, 0.0], [87.5, float(S), 6.5, 0.0]]
    return {
        "id": ID,
        "nom_fr": "Port à sec",
        "nom_en": "Dry harbour",
        "type": "clashwar_3v3",
        "dimension": "heraliz_clashwar:arenes",
        "taille": [SX, SY, SZ],
        "bornes": [2, 0, 2, SX - 3, SY + 3, SZ - 3],
        "points": {
            "spawn_a": a,
            "spawn_b": [miroir_point(p) for p in a],
            "barriere_a": [[79.0, float(S), 3.0, 0.0], [90.0, float(S + 2), 9.0, 0.0]],
            "barriere_b": [[79.0, float(S), SZ - 10.0, 0.0], [90.0, float(S + 2), SZ - 4.0, 0.0]],
            "zone_centre": [[cx, float(S), cz, 0.0]],
            "spectateur": [[cx, 30.0, 2.5, 0.0], [cx, 30.0, SZ - 2.5, 180.0]],
            "bombe_a": [[49.5, float(S), 144.5, 160.0]],
            "site_a": [[49.5, float(S), 144.5, 160.0]],
            "bombe_b": [[129.5, float(S), 135.5, -160.0]],
            "site_b": [[129.5, float(S), 135.5, -160.0]],
            "charge": [[84.5, float(S), 6.5, 0.0]],
        },
        "format": "3v3",
        "joueurs_par_equipe": 3,
        "theme": "port asséché",
        "barrieres": {"a": [79, S, 3, 90, S + 2, 9], "b": [79, S, SZ - 10, 90, S + 2, SZ - 4]},
        "zone": {"centre": [cx, S, cz], "rayon_depart": 120.0, "rayon_min": 6.0},
        "symetrie": "centrale (rotation 180° autour du point x = 85, z = 85)",
        "blocs": n,
        "recherche_destruction": {
            "attaquants": "a",
            "defenseurs": "b",
            "note": "Sites A (entrepôt sud-ouest) et B (parc à conteneurs sud-est) du côté de spawn_b, pour le mode "
                    "recherche et destruction (inactif aujourd'hui) : à déplacer librement dans generer_port.py.",
        },
        "generateur": "modules/clashwar/outils/cartes_clashwar/generer_port.py",
    }


def construire():
    sol()
    surfaces()
    enceinte()
    bassin()
    descentes()
    pont_central()
    navire()
    jetee()
    douane()
    entrepot()
    capitainerie()
    conteneurs_ouest()
    reservoirs()
    for x0 in (42, 118):
        grue(x0)
    quai_divers()
    centre_renforts()
    hangar_ouvert()
    garage_camions()
    cantine()
    epi_ferre()
    bateaux_sur_bers()
    quai_marchandises()
    hangar_bateaux()
    bout_est()
    remorqueur()
    place_centrale()
    renforts_port()
    renforts_port2()
    renforts_port3()
    renforts_port4()
    silo()
    herbes_partout()


def main():
    K.construire_carte(ID, construire, meta)


if __name__ == "__main__":
    main()
