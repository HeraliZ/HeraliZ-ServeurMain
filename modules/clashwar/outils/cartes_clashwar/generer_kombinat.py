"""ClashWar 2v2 map "Cité du Kombinat" (clashwar_2v2_kombinat): Soviet housing estate, 130 x 130 (pilot map approved by the owner).

Two L-shaped 4-storey blocks around a courtyard (hockey box at the centre), garages, boiler house with its chimney,
shop and market, construction pit, barrack. Built in the north half (team A) and mirrored by a 180-degree rotation
(xl_lib.Sym) with another look for the south half. Writes structures/ and cartes/ next to this script.

Usage: python generer_kombinat.py
"""
import xl_carte as K
import xl_formes as F
import xl_lib as xl
from xl_lib import dalle, etat, feuilles, marche, oriente, trappe, tronc
from xl_formes import C, D, G, M, S, U, LUMIERE, LUMIERE_F, garde_corps

ID = "clashwar_2v2_kombinat"
SX = SZ = 130
SY = 28

PAL_B = {
    M + "bricks": M + "blue_concrete",                   # brick block (A) -> grey panel block (B)
    M + "light_gray_concrete": M + "orange_terracotta",  # concrete bands -> panel seams
    M + "stone": M + "andesite",
    M + "white_wool": M + "yellow_wool",
    M + "green_concrete": M + "light_blue_terracotta",
    M + "spruce_planks": M + "oak_planks",
    M + "white_terracotta": M + "bricks",                # white-brick garages (A) -> red-brick garages (B)
    D + "tole_toit_rouille": D + "tole_toit_verte",
    D + "tole_toit_rouille_escalier": D + "tole_toit_verte_escalier",
    D + "tole_toit_rouille_dalle": D + "tole_toit_verte_dalle",
    D + "tole_toit_bleue": D + "tole_toit_rouille",
    D + "tole_toit_bleue_escalier": D + "tole_toit_rouille_escalier",
    D + "tole_toit_bleue_dalle": D + "tole_toit_rouille_dalle",
    M + "yellow_concrete": M + "quartz_block",
    D + "enduit_ocre": D + "enduit_bleu",
    D + "enduit_ocre_use": D + "enduit_bleu_use",
    M + "birch_log": M + "oak_log",
    M + "birch_leaves": M + "oak_leaves",
    M + "red_wool": M + "lime_terracotta",
}
# cosmetic variants of the same shape on the B side


VAR_B = {
    U + "epave_rouge": U + "epave_bleue",
    U + "epave_bleue": U + "epave_rouge",
    C + "arkaniaz_trash_can1": C + "arkaniaz_trash_can2",
    C + "zombie_garbage_1": C + "zombie_garbage_2",
    C + "zombie_woodboards_1": C + "zombie_woodboards_2",
    C + "arkaniaz_old_tv_1": C + "arkaniaz_old_tv_3",
}




K.init(SX, SY, SZ, PAL_B, VAR_B, graine=14)
from xl_carte import *  # noqa: E402,F401,F403  (W, props and helpers bound to this map)


def immeubles():
    # long arm of block A: x 30..99, z 30..40, courtyard to the south
    rep = F.Repere(30, 30, "+x", "+z")
    F.Barre(W, rep, 70, style=F.STYLE_BRIQUE, podyezds=(5, 29, 45), arche=(16, 20), graine=3,
            sans_balcon=set(range(58, 70))).construire()
    # short arm of block A: x 89..99, z 41..72, street side east (x = 99), courtyard side west (x = 89)
    rep2 = F.Repere(99, 41, "+z", "-x")
    st2 = dict(F.STYLE_BRIQUE)
    st2["pas_balcon"] = 7
    F.Barre(W, rep2, 32, style=st2, podyezds=(5, 21), graine=5, pignon_fenetres=True).construire()


def garages_no():
    # row 1 along the north fence: z 2..7, doors facing south (alley z 8..12)
    for i in range(7):
        garage(2 + 4 * i, 2, "south", ouvert=(i in (1, 4)), graine=i)
    # double row: z 13..18 doors north, z 19..24 doors south; gap x 15..17
    for i, x0 in enumerate((2, 6, 10, 18, 22, 26)):
        garage(x0, 13, "north", ouvert=(i in (2, 3)), graine=i + 3)
        garage(x0, 19, "south", ouvert=(i in (0, 4)), graine=i + 5)
    # rubble ramp to climb on the double row from the gap
    for k in range(4):
        x = 17 - k
        for z in (16, 17):
            W.set(x, S + k, z, marche(M + "cobblestone", "west"))
            for y in range(S, S + k):
                W.set(x, y, z, M + "cobblestone" if (y + z) % 2 else M + "andesite")
    # crates against row 1 at the west end (climb on the north row)
    caisses(2, 8, ["3", "2", "1"])
    surface(2, 8, 29, 12, TERRE, graine=1)
    surface(15, 13, 17, 24, TERRE, graine=2)
    surface(2, 25, 29, 28, TERRE, graine=3)
    # junk in the alleys
    voiture(9, 10, "north", U + "epave_brulee")
    pneus(21, 9, 2)
    pneus(22, 12, 1)
    benne(26, 11, "x")
    caisses(12, 11, ["2"])
    caisses(5, 26, ["12"])
    W.set(23, S, 26, oriente(C + "zombie_debrispile", "north"))
    lampadaire(13, 8, "south", allume=False)
    W.set(16, S, 20, oriente(C + "arkaniaz_oil_drum", "north"))
    W.set(16, S, 21, oriente(C + "arkaniaz_oil_drum", "north"))
    W.set(16, S + 1, 21, etat(M + "barrel", facing="up", open="false"))


def bande_nord():
    surface(31, 2, 99, 13, TROTTOIR, graine=4)
    surface(31, 14, 99, 21, ASPHALTE, graine=5)
    surface(31, 22, 99, 24, TROTTOIR, graine=6)
    surface(31, 25, 99, 29, PELOUSE, graine=7)
    # bus depot shed over the spawn: x 55..74, z 2..11, roof at y = 10
    for x in range(55, 75):
        for z in range(2, 12):
            W.set(x, 10, z, M + "light_gray_concrete" if (x + z) % 5 else M + "stone")
    for x in range(55, 75):
        W.set(x, 11, 11, dalle(M + "smooth_stone", "bottom"))
        W.set(x, 10, 12, dalle(M + "smooth_stone", "top"))
    for x in (55, 61, 68, 74):
        for y in range(S, 10):
            W.set(x, y, 11, M + "stone_bricks")
    for x in range(55, 75):
        for y in range(S, 10):
            W.set(x, y, 2, M + ("pink_wool" if y > S else "stone"))
    for z in range(2, 12):
        for y in range(S, 10):
            if 5 <= z <= 7 and y <= S + 2:
                continue
            W.set(55, y, z, M + "pink_wool" if y > S else M + "stone")
            W.set(74, y, z, M + "pink_wool" if y > S else M + "stone")
    for x in range(57, 74, 4):
        W.set(x, 9, 6, LUMIERE)
    for x in (57, 58, 71, 72):
        W.set(x, S, 3, oriente(D + "banc_parc", "south"))
    W.set(64, S, 3, oriente(C + "arkaniaz_electric_box", "south"))
    W.set(65, S, 3, oriente(C + "arkaniaz_electric_box", "south"))
    for x in (57, 58, 59, 70, 71, 72):
        W.set(x, S, 12, M + "smooth_stone")
    # crates outside the shed side doors (break the line through the shed)
    caisses(52, 5, ["2", "2", "1"])
    caisses(76, 6, ["1", "2"])
    # bus terminus (west part)
    bus_couche(39, 15)
    abribus(42, 11)
    pavillon(31, 7)
    kiosque(37, 3, "south")
    colonne_affiche(51, 13)
    # boiler house and chimney (east part)
    chaufferie(77, 3)
    # road: truck, roadblock, cars
    camion(57, 19, "east")
    barrage(87, 14, 87, 18)
    W.set(88, S, 15, oriente(U + "herisson", "east"))
    voiture(95, 20, "north", U + "epave_rouge")
    voiture(70, 15, "south", U + "epave_bleue")
    W.set(66, S, 15, oriente(C + "arkaniaz_traffic_cone", "north"))
    W.set(67, S, 16, oriente(C + "arkaniaz_traffic_cone", "north"))
    beton_bloc(62, 16, "x", 2)
    # sidewalk furniture (2 high where it must cut the lines)
    cabine_tel(69, 23, "north")
    citerne_kvas(75, 22)
    benne(92, 22, "x")
    poubelle(44, 23, "south")
    poubelle(81, 23, "south")
    colonne_affiche(35, 23)
    colonne_affiche(52, 24)
    kiosque(88, 22, "north")
    barrage(97, 19, 97, 21)
    # front gardens: low iron fence, trees, hedges 2 high cutting the strip into gardens
    for x in range(31, 100):
        if x in range(46, 51) or x in (38, 39, 62, 63, 78, 79):
            continue
        W.set(x, S, 25, D + "balustrade_fer")
    for x in (34, 44, 56, 66, 72, 86, 95):
        haie(x, 25, x, 29, haut=2)
    for x in (32, 41, 53, 63, 76, 83, 92):
        arbre_bouleau(x, 27 + (x % 2), 7 + (x % 3))
    for x in (36, 58, 77, 97):
        lampadaire(x, 22, "north")
    W.set(31, S, 21, oriente(C + "zombie_barricade_1", "east"))
    W.set(98, S, 13, oriente(C + "zombie_garbage_1", "south"))
    caisses(49, 3, ["21", "1."])
    caisses(96, 10, ["2", "1"])


def chaufferie(x0, z0):
    """District boiler house: 16 x 10 brick hall (walls y 5..12, flat roof y 13), two boilers, a steel catwalk along
    the north wall (stand y 9) reached by stairs, big south gate and a west door; tall brick chimney east of it."""
    x1, z1 = x0 + 15, z0 + 9
    for y in range(S, S + 8):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "polished_andesite")
    W.fill(x0, S + 8, z0, x1, S + 8, z1, M + "black_concrete")
    for x in range(x0, x1 + 1):
        W.set(x, S + 9, z0, M + "stone_bricks")
        W.set(x, S + 9, z1, M + "stone_bricks")
    for z in range(z0, z1 + 1):
        W.set(x0, S + 9, z, M + "stone_bricks")
        W.set(x1, S + 9, z, M + "stone_bricks")
    # high windows (barred, some broken) on the long walls
    for x in range(x0 + 2, x1 - 1, 3):
        for y in (S + 4, S + 5, S + 6):
            W.set(x, y, z1, M + "iron_bars" if h(x, y) % 4 else None)
            W.set(x, y, z0, M + "iron_bars" if h(x, y, 1) % 4 else None)
    # south gate (4 wide, 4 high) and west door
    W.clear(x0 + 6, S, z1, x0 + 9, S + 3, z1)
    W.clear(x0, S, z0 + 5, x0, S + 1, z0 + 6)
    # boilers (dark metal blocks with pipes)
    for bx in (x0 + 2, x0 + 10):
        W.fill(bx, S, z0 + 3, bx + 2, S + 3, z0 + 5, M + "netherite_block")
        W.fill(bx, S + 4, z0 + 4, bx + 2, S + 4, z0 + 4, M + "polished_deepslate")
        W.set(bx + 1, S + 5, z0 + 4, D + "gaine_calorifugee")
        W.set(bx + 1, S + 6, z0 + 4, D + "gaine_calorifugee")
        W.set(bx + 1, S, z0 + 6, oriente(U + "vanne", "south"))
    # catwalk along the north wall at y S+3 (stand S+4), stairs up from the west end
    for x in range(x0 + 1, x1):
        W.set(x, S + 3, z0 + 1, M + "light_blue_wool")
        if x > x0 + 1:
            W.set(x, S + 4, z0 + 1, garde_corps("south"))
    for k in range(3):
        W.set(x0 + 1, S + k, z0 + 4 - k, marche(M + "stone_brick", "north"))
        for y in range(S, S + k):
            W.set(x0 + 1, y, z0 + 4 - k, M + "stone_bricks")
    # coal heap and lights
    for (x, z, n) in ((x0 + 6, z0 + 7, 2), (x0 + 7, z0 + 7, 1), (x0 + 13, z0 + 7, 1)):
        for k in range(n):
            W.set(x, S + k, z, M + "coal_block")
    for x in range(x0 + 3, x1 - 1, 4):
        W.set(x, S + 6, z0 + 6, LUMIERE)
    W.set(x0 + 4, S + 2, z0 + 1, LUMIERE_F)
    # chimney: 3 x 3 brick shaft from the ground to y 26, stone rings
    cx, cz = x1 + 2, z0 + 1
    for y in range(S, 27):
        for x in range(cx, cx + 3):
            for z in range(cz, cz + 3):
                if (x, z) == (cx + 1, cz + 1) and y > S:
                    continue
                W.set(x, y, z, M + "stone_bricks" if y % 6 == 0 else M + "bricks")
    W.set(cx + 1, S, cz + 1, M + "bricks")
    # flue between the hall and the chimney (pipe on brackets)
    for x in range(x1 + 1, cx):
        W.set(x, S + 6, cz + 1, M + "light_gray_wool")
    # coal heap outside
    for (x, z, n) in ((x1 + 2, z1 - 2, 2), (x1 + 3, z1 - 2, 1), (x1 + 2, z1 - 1, 1), (x1 + 3, z1 - 3, 1)):
        for k in range(n):
            W.set(x, S + k, z, M + "coal_block")


def bus(x0, z0):
    bus_couche(x0, z0)


def marche_ne():
    surface(100, 2, 127, 29, TROTTOIR, graine=8)
    surface(100, 14, 103, 29, ASPHALTE, graine=9)
    x0, x1, z0, z1 = 105, 124, 3, 13
    for y in range(S, S + 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "white_terracotta" if y < S + 3 else M + "light_gray_concrete")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "white_terracotta" if y < S + 3 else M + "light_gray_concrete")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "quartz_bricks")
    W.fill(x0, S + 4, z0, x1, S + 4, z1, M + "black_concrete")
    for x in range(x0, x1 + 1):
        W.set(x, S + 5, z0, M + "light_gray_concrete")
        W.set(x, S + 5, z1, M + "light_gray_concrete")
    for z in range(z0, z1 + 1):
        W.set(x0, S + 5, z, M + "light_gray_concrete")
        W.set(x1, S + 5, z, M + "light_gray_concrete")
    for x in range(x0 + 2, x1 - 1):
        if x in (x0 + 9, x0 + 10):
            W.clear(x, S, z1, x, S + 2, z1)
            continue
        for y in (S + 1, S + 2):
            W.set(x, y, z1, M + "gray_stained_glass_pane" if h(x, y) % 3 else None)
    W.clear(x0, S, 7, x0, S + 2, 8)
    W.clear(x1, S, 9, x1, S + 1, 9)
    for z in (6, 9):
        for x in range(x0 + 3, x1 - 3):
            if x in (x0 + 9, x0 + 10) or h(x, z) % 4 == 0:
                continue
            W.set(x, S, z, oriente(C + "fashion_shelf_1", "south"))
    W.set(x0 + 2, S, z1 - 2, oriente(D + "comptoir_caisse", "east"))
    W.set(x0 + 2, S, z1 - 3, oriente(D + "comptoir_caisse", "east"))
    W.set(x0 + 15, S, 11, oriente(D + "rayonnage_renverse", "north"))
    for x in range(x0 + 2, x1 - 1, 4):
        for z in (5, 10):
            W.set(x, S + 3, z, LUMIERE)
    # loading dock west of the shop (stand y S+2), stairs from the road, then stairs up to the roof (stand S+5)
    for x in range(100, 105):
        for z in range(4, 12):
            W.set(x, S, z, M + "stone_bricks")
            W.set(x, S + 1, z, M + "smooth_stone" if x > 100 else M + "stone_bricks")
    for z in (4, 11):
        for x in range(100, 105):
            W.set(x, S + 2, z, D + "balustrade_fer")
    for z in range(5, 11):
        if z not in (7, 8):
            W.set(100, S + 2, z, D + "balustrade_fer")
    W.set(99, S, 7, marche(M + "stone_brick", "east"))
    W.set(99, S, 8, marche(M + "stone_brick", "east"))
    W.set(99, S + 1, 7, None)
    for k, x in enumerate((101, 102, 103)):
        W.set(x, S + 2 + k, 5, marche(M + "stone_brick", "east"))
        for y in range(S + 2, S + 2 + k):
            W.set(x, y, 5, M + "stone_bricks")
    W.set(104, S + 4, 5, M + "stone_bricks")
    for y in range(S + 2, S + 4):
        W.set(104, y, 5, M + "stone_bricks")
    W.set(105, S + 5, 5, None)
    W.set(101, S + 2, 4, None)
    W.set(102, S + 2, 4, None)
    W.set(103, S + 2, 4, None)
    for x in (101, 102):
        W.set(x, S + 2, 9, M + "iron_block")
    W.set(102, S + 3, 9, M + "orange_wool")
    W.set(110, S + 5, 7, D + "groupe_ventilation")
    W.set(118, S + 5, 9, D + "groupe_ventilation")
    W.set(114, S + 5, 5, oriente(D + "antenne_parabolique", "south"))
    W.set(121, S + 5, 11, M + "stone_bricks")
    # market stalls
    for i, x in enumerate(range(104, 126, 4)):
        etal(x, 17, "south", i)
        etal(x, 24, "north", i + 3)
    # back alley along the east fence: containers and crates (cut the line along the fence)
    benne(126, 15, "z")
    caisses(126, 21, ["2", "1"])
    caisses(125, 28, ["1", "2"])
    peuplier(102, 28, 11)
    voiture(108, 28, "north", U + "epave_brulee")
    benne(101, 26, "z")
    caisses(121, 21, ["1"])
    lampadaire(103, 15, "east")
    kiosque(100, 20, "east")
    caisses(114, 7, ["22"])
    # cover in the stall alley (mirrored around site B on the south side)
    caisses(110, 21, ["2", "1"])
    caisses(119, 20, ["21"])
    W.set(106, S, 22, etat(M + "barrel", facing="up", open="false"))
    W.set(106, S + 1, 22, etat(M + "barrel", facing="up", open="false"))
    W.set(122, S, 22, oriente(C + "arkaniaz_oil_drum", "north"))


def etal(x0, z0, sens, i):
    """Market stall 3 x 3 with a counter on the front and a sloped tin roof (stairs) falling to the front."""
    toit = [D + "tole_toit_rouille", D + "tole_toit_bleue", D + "tole_toit_rouille"][i % 3]
    zf = z0 + 2 if sens == "south" else z0
    zb = z0 if sens == "south" else z0 + 2
    for x in range(x0, x0 + 3):
        for y in range(S, S + 3):
            W.set(x, y, zb, M + "pink_wool" if y > S else M + "coal_block")
        W.set(x, S, zf, dalle(M + "spruce", "top"))
        W.set(x, S, z0 + 1, None)
    for z in range(z0, z0 + 3):
        for y in range(S, S + 3):
            W.set(x0, y, z, M + "pink_wool" if y > S else M + "coal_block")
    for x in range(x0, x0 + 4):
        W.set(x, S + 3, zb, marche(toit + "_escalier", sens))
        W.set(x, S + 3, z0 + 1, dalle(toit + "_dalle", "top"))
        W.set(x, S + 3, zf, dalle(toit + "_dalle", "bottom"))
    objet = [C + "arkaniaz_radio", C + "arkaniaz_jar", C + "arkaniaz_old_tv_2"][i % 3]
    W.set(x0 + 1, S + 1, zf, oriente(objet, sens))


def couloir_ouest():
    surface(2, 29, 29, 64, PELOUSE, graine=10)
    surface(18, 25, 24, 64, ASPHALTE, graine=11)
    surface(25, 29, 29, 64, TROTTOIR, graine=12)
    chantier(2, 29)
    tuyaux_chauffage()
    baraque(2, 47)
    # roadblocks staggered across the road
    barrage(18, 37, 21, 37)
    barrage(21, 56, 24, 56)
    W.set(22, S, 36, oriente(U + "herisson", "north"))
    W.set(20, S, 57, oriente(U + "herisson", "north"))
    # sidewalk along B's short arm and the gap: bus shelter, kiosk, bins
    abribus_nord_sud(27, 31, "west")
    bois_empile(14, 52, 3)
    kiosque(26, 52, "west")
    caisses(14, 61, ["22", "1."])
    caisses(23, 63, ["2"])
    voiture(27, 41, "east", U + "epave_brulee")
    poubelle(25, 39, "west")
    lampadaire(17, 48, "east")
    lampadaire(25, 61, "west")
    caisses(26, 59, ["2"])
    peuplier(16, 62, 10)


def chantier(x0, z0):
    """Abandoned construction site: board fence, pit 3 deep with ramps and started foundation walls, soil mound,
    stacked concrete slabs, a site cabin. Area x0..x0+14, z0..z0+13 (A half, west lane)."""
    x1, z1 = x0 + 14, z0 + 13
    surface(x0, z0, x1, z1, TERRE, graine=30)
    # board fence on the north, east and south sides with gates
    palissade(x0, z0, x1, z0, trous={(x0 + 9, z0), (x0 + 10, z0)})
    palissade(x1 + 1, z0, x1 + 1, z1, trous={(x1 + 1, z0 + 5), (x1 + 1, z0 + 6)})
    palissade(x0, z1, x1, z1, trous={(x0 + 3, z1), (x0 + 4, z1), (x0 + 10, z1)})
    # the pit: x0+3..x0+12, z0+2..z0+10, floor at y 1 (stand y 2)
    px0, px1, pz0, pz1 = x0 + 3, x0 + 12, z0 + 2, z0 + 10
    for x in range(px0, px1 + 1):
        for z in range(pz0, pz1 + 1):
            for y in range(2, G + 1):
                W.set(x, y, z, None)
            W.set(x, 1, z, M + ("gravel" if h(x, z) % 3 else "coarse_dirt"))
    # pit walls: earth with stones
    for x in range(px0 - 1, px1 + 2):
        for z in (pz0 - 1, pz1 + 1):
            for y in range(1, G + 1):
                W.set(x, y, z, M + ("coarse_dirt" if h(x, y, z) % 3 else "stone"))
    for z in range(pz0 - 1, pz1 + 2):
        for x in (px0 - 1, px1 + 1):
            for y in range(1, G + 1):
                W.set(x, y, z, M + ("coarse_dirt" if h(x, y, z) % 3 else "stone"))
    for x in range(px0 - 1, px1 + 2):
        for z in (pz0 - 1, pz1 + 1):
            W.set(x, G, z, M + "dirt_path")
    for z in range(pz0 - 1, pz1 + 2):
        for x in (px0 - 1, px1 + 1):
            W.set(x, G, z, M + "dirt_path")
    # ramp down on the east side (x px1 .. px1-2 at z pz0+6..pz0+7), and on the north-west corner
    for k in range(3):
        for z in (pz0 + 6, pz0 + 7):
            x = px1 + 1 - k
            W.set(x, G - k, z, marche(M + "cobblestone", "east"))
            for y in range(G - k + 1, G + 1):
                W.set(x, y, z, None)
    for k in range(3):
        for x in (px0, px0 + 1):
            z = pz0 - 1 + k
            W.set(x, G - k, z, marche(M + "andesite", "north"))
            for y in range(G - k + 1, G + 1):
                W.set(x, y, z, None)
    # started foundation walls (formwork concrete) in the pit, 3 high from the pit floor, with gaps
    for x in range(px0 + 2, px1 - 1):
        for y in (2, 3, 4):
            if x not in (px0 + 5, px0 + 6):
                W.set(x, y, pz0 + 3, D + "beton_banche")
    for z in range(pz0 + 3, pz1 - 1):
        for y in (2, 3, 4):
            if z != pz0 + 5:
                W.set(px0 + 2, y, z, D + "beton_banche")
    for x in (px0 + 2, px0 + 4, px0 + 7):
        W.set(x, 5, pz0 + 3, M + "iron_bars")
    # stacked slabs and blocks in the pit
    for x in range(px0 + 5, px0 + 9):
        W.set(x, 2, pz1 - 1, M + "smooth_stone")
        W.set(x, 3, pz1 - 1, dalle(M + "smooth_stone", "bottom"))
    W.set(px1 - 1, 2, pz0 + 1, M + "light_gray_concrete")
    W.set(px1 - 1, 3, pz0 + 1, M + "light_gray_concrete")
    W.set(px1 - 2, 2, pz0 + 1, M + "light_gray_concrete")
    # soil mound (3 high) in the north-west corner of the site
    for (x, z, n) in ((x0, z0 + 2, 2), (x0 + 1, z0 + 2, 3), (x0, z0 + 3, 3), (x0 + 1, z0 + 3, 3), (x0 + 1, z0 + 4, 2),
                      (x0, z0 + 4, 2), (x0, z0 + 5, 1), (x0 + 1, z0 + 5, 1), (x0 + 2, z0 + 3, 1), (x0, z0 + 1, 1)):
        for k in range(n):
            W.set(x, S + k, z, M + ("coarse_dirt" if k < n - 1 else "podzol"))
    # site cabin (blue metal 'bytovka') 3 x 6 x 3 near the east gate
    bx0, bz0 = x0, z0 + 6
    for x in range(bx0, bx0 + 3):
        for z in range(bz0, bz0 + 6):
            for y in range(S, S + 3):
                bord = x in (bx0, bx0 + 2) or z in (bz0, bz0 + 5)
                if bord:
                    W.set(x, y, z, M + "lapis_block" if y != S + 1 else M + "diamond_block")
            W.set(x, S + 3, z, M + "light_blue_wool")
    W.set(bx0 + 2, S + 1, bz0 + 2, M + "glass_pane")
    W.clear(bx0 + 1, S, bz0 + 5, bx0 + 1, S + 1, bz0 + 5)
    W.set(bx0 + 1, S + 2, bz0 + 3, LUMIERE_F)
    # concrete rings (well rings) and a pallet stack near the north gate
    caisses(x0 + 6, z0 + 1, ["2"])
    W.set(x0 + 12, S, z0 + 12, oriente(C + "wooden_pallet_1", "north"))
    W.set(x0 + 13, S, z0 + 12, oriente(C + "wooden_pallet_2", "north"))


def tuyaux_chauffage():
    """Two insulated heating mains along z 44..45 from the west fence to the courtyard gap, on concrete footings,
    with an inverted U over the road (x 17..25)."""
    for x in range(2, 41):
        if 17 <= x <= 25:
            continue
        for z in (44, 45):
            # pipes resting on the ground (1 high: crouch cover, one can jump over them)
            W.set(x, S, z, M + "white_wool" if x % 6 == 3 else M + "light_gray_wool")
    for z in (44, 45):
        for y in range(S, S + 5):
            W.set(17, y, z, M + "light_gray_wool")
            W.set(25, y, z, M + "light_gray_wool")
        for x in range(17, 26):
            W.set(x, S + 4, z, M + "light_gray_wool")
        W.set(16, S, z, M + "stone_bricks")
        W.set(26, S, z, M + "stone_bricks")


def baraque(x0, z0):
    """Pre-war two-storey barrack: 12 x 14, plastered walls (ocre, worn), wooden floors, interior stairs, pitched
    roof (ridge along z) of rusty tin. Doors at both ends, windows on both long sides."""
    x1, z1 = x0 + 11, z0 + 13
    mur, mur2 = D + "enduit_ocre", D + "enduit_ocre_use"
    for y in range(S, S + 8):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, mur2 if h(x, y, z) % 4 == 0 else mur)
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, mur2 if h(x, y, z) % 4 == 0 else mur)
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            W.set(x, S, z, M + "stone")
    for z in range(z0, z1 + 1):
        for x in (x0, x1):
            W.set(x, S, z, M + "stone")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "spruce_planks")
    W.fill(x0 + 1, S + 3, z0 + 1, x1 - 1, S + 3, z1 - 1, M + "spruce_planks")
    for x in range(x0, x1 + 1):
        W.set(x, S + 3, z0, M + "dark_oak_planks")
        W.set(x, S + 3, z1, M + "dark_oak_planks")
    W.fill(x0 + 1, S + 7, z0 + 1, x1 - 1, S + 7, z1 - 1, M + "spruce_planks")
    # interior stairs (straight flight along z, west side) from z0+9 going north to the first floor
    for k in range(4):
        z = z0 + 9 - k
        for x in (x0 + 1, x0 + 2):
            W.set(x, S + k, z, marche(M + "spruce", "north"))
            for y in range(S, S + k):
                W.set(x, y, z, M + "spruce_planks")
    W.clear(x0 + 1, S + 3, z0 + 7, x0 + 2, S + 3, z0 + 9)
    W.set(x0 + 1, S + 3, z0 + 6, M + "spruce_planks")
    W.set(x0 + 2, S + 3, z0 + 6, M + "spruce_planks")
    # partition with doorways (ground and first floor)
    for y in list(range(S, S + 3)) + list(range(S + 4, S + 7)):
        for x in range(x0 + 3, x1):
            W.set(x, y, z0 + 7, M + "white_wool")
    for y0 in (S, S + 4):
        W.clear(x0 + 6, y0, z0 + 7, x0 + 7, y0 + 1, z0 + 7)
        W.clear(x0 + 10, y0, z0 + 7, x0 + 10, y0 + 1, z0 + 7)
    # doors at both ends, 2 wide
    W.clear(x0 + 5, S, z0, x0 + 6, S + 2, z0)
    W.clear(x0 + 5, S, z1, x0 + 6, S + 2, z1)
    # windows on the long sides (2 high, both storeys); the west side (fence) gets none
    for z in range(z0 + 2, z1 - 1, 3):
        for y0 in (S + 1, S + 5):
            for y in (y0, y0 + 1):
                v = h(x1, y, z) % 5
                W.set(x1, y, z, None if v < 3 else (M + "glass_pane" if v == 3 else oriente(C + "zombie_woodboards_1", "east")))
    for x in (x0 + 3, x0 + 8):
        for z in (z0, z1):
            W.set(x, S + 5, z, None)
            W.set(x, S + 6, z, None)
    # pitched roof, ridge along z: rows of stairs rising from both eaves
    for k in range(6):
        y = S + 8 + k
        for z in range(z0 - 1, z1 + 2):
            if x0 - 1 + k >= 2:
                W.set(x0 - 1 + k, y, z, marche(D + "tole_toit_rouille_escalier", "east"))
            W.set(x1 + 1 - k, y, z, marche(D + "tole_toit_rouille_escalier", "west"))
            if k > 0 and z in (z0, z1):
                for xx in range(x0 - 1 + k + 1, x1 + 1 - k):
                    if W.get(xx, y, z) is None:
                        W.set(xx, y, z, mur)
    for z in range(z0 - 1, z1 + 2):
        W.set(x0 + 5, S + 14, z, dalle(D + "tole_toit_rouille_dalle", "bottom"))
        W.set(x0 + 6, S + 14, z, dalle(D + "tole_toit_rouille_dalle", "bottom"))
    for z in range(z0, z1 + 1):
        W.set(x0, S + 8, z, mur)
        W.set(x1, S + 8, z, mur)
    for y in range(S + 8, S + 16):
        W.set(x0 + 3, y, z0 + 4, M + "bricks")
    W.set(x0 + 9, S, z0 + 2, oriente(C + "century_cupboard", "south"))
    W.set(x0 + 4, S + 4, z1 - 2, oriente(C + "arkaniaz_bed", "west"))
    W.set(x0 + 9, S + 4, z0 + 3, oriente(C + "century_table", "west"))
    W.set(x0 + 9, S + 4, z1 - 3, etat(D + "gravats", taille="moyen"))
    W.set(x0 + 8, S, z1 - 3, oriente(C + "century_green_leather_sofa", "west"))
    for (x, y, z) in ((x0 + 5, S + 2, z0 + 3), (x0 + 5, S + 2, z1 - 3), (x0 + 5, S + 6, z0 + 3), (x0 + 5, S + 6, z1 - 3)):
        W.set(x, y, z, LUMIERE)


def cour():
    surface(41, 41, 88, 64, PELOUSE, graine=13)
    surface(41, 41, 88, 45, ASPHALTE, graine=14)
    surface(84, 41, 88, 64, ASPHALTE, graine=15)
    surface(30, 41, 40, 56, TERRE, graine=16)
    for x0 in (60, 76):
        surface(x0, 46, x0 + 1, 57, TROTTOIR, graine=17)
    surface(44, 56, 84, 57, TROTTOIR, graine=18)
    boite_hockey()
    aire_jeux(44, 47)
    sous_station(68, 47)
    enclos_poubelles(31, 46)
    coquille(33, 51, "east")
    coquille(80, 57, "west")
    coquille(80, 62, "west")
    etendoir(56, 48)
    haie(44, 46, 50, 46, haut=2)        # screen in front of the arch exit
    for (x, z) in ((42, 58), (58, 53), (64, 52), (82, 54), (61, 60), (37, 54)):
        arbre_bouleau(x, z, 7 + x % 3)
    voiture(52, 43, "north", U + "epave_rouge")
    voiture(70, 43, "south", U + "epave_bleue")
    voiture(86, 50, "east", U + "epave_brulee")
    voiture_accidentee(85, 60, "south")
    banc(48, 57, "south")
    banc(76, 58, "south")
    poubelle(57, 57, "north")
    poubelle(44, 45, "south")
    lampadaire(63, 45, "south")
    lampadaire(83, 46, "west")
    caisses(66, 44, ["12"])
    beton_bloc(43, 62, "z", 2)
    benne(86, 44, "z")
    camion(31, 41, "west")
    benne(52, 44, "x")
    benne(73, 45, "x")
    haie(38, 55, 40, 56, haut=2)


def boite_hockey():
    """Hockey box ('korobka') at the map centre, x 52..77, z 58..71: boards 1.5 high (planks + slab), chain-link
    above the short sides, openings at the corners and in the middle of every side."""
    x0, x1, z0, z1 = 52, 77, 58, 71
    for x in range(x0, x1 + 1):
        for z in range(z0, 65):
            W.set(x, G, z, M + "light_gray_concrete" if (x + z) % 7 else M + "gray_concrete")
    for x in range(x0, x1 + 1):
        if x in (x0, x0 + 1, x1 - 1, x1, 64, 65):
            continue
        W.set(x, S, z0, M + "spruce_planks")
        W.set(x, S + 1, z0, dalle(M + "spruce", "bottom"))
    for z in range(z0, 65):
        if z in (z0, z0 + 1, 63, 64):
            continue
        for x in (x0, x1):
            W.set(x, S, z, M + "spruce_planks")
            W.set(x, S + 1, z, dalle(M + "spruce", "bottom"))
    # goal frames (iron) at both ends inside the box
    for z in (63, 64):
        for y in (S, S + 1):
            W.set(x0 + 2, y, z, M + "iron_bars")
    for y in (S, S + 1, S + 2):
        W.set(x0 + 2, y, 62, M + "iron_bars")
    W.set(x0 + 2, S + 2, 63, M + "iron_bars")
    W.set(x0 + 2, S + 2, 64, M + "iron_bars")
    # cover inside the box (A half, mirrored): crates, tyres, an overturned bench and a pile of boards
    caisses(60, 61, ["2"])
    pneus(69, 61, 2)
    W.set(57, S, 63, M + "spruce_planks")
    W.set(57, S + 1, 63, dalle(M + "spruce", "bottom"))
    W.set(58, S, 63, M + "spruce_planks")
    W.set(64, S, 61, oriente(C + "zombie_woodboards_1", "north"))
    lampadaire(x0 - 2, z0 + 1, "east")


def aire_jeux(x0, z0):
    """Playground: sandbox with a wooden edge, slide tower (stand 3 above ground), swings, climbing frame."""
    for x in range(x0, x0 + 5):
        for z in range(z0, z0 + 5):
            W.set(x, G, z, M + "sand")
            if x in (x0, x0 + 4) or z in (z0, z0 + 4):
                W.set(x, S, z, dalle(M + "spruce", "bottom"))
    # slide tower: posts, platform at y S+2 (stand S+3), railing, stairs up on the east, solid slide to the south
    tx, tz = x0 + 7, z0 + 1
    for (x, z) in ((tx, tz), (tx + 2, tz), (tx, tz + 2), (tx + 2, tz + 2)):
        for y in range(S, S + 2):
            W.set(x, y, z, M + "red_wool" if (x + z) % 2 else M + "lapis_block")
    W.fill(tx, S + 2, tz, tx + 2, S + 2, tz + 2, M + "spruce_planks")
    for x in range(tx, tx + 3):
        W.set(x, S + 3, tz, D + "balustrade_fer")
    W.set(tx, S + 3, tz + 1, D + "balustrade_fer")
    W.set(tx, S + 3, tz + 2, D + "balustrade_fer")
    W.set(tx + 4, S, tz + 1, marche(M + "spruce", "west"))
    W.set(tx + 3, S, tz + 1, M + "spruce_planks")
    W.set(tx + 3, S + 1, tz + 1, marche(M + "spruce", "west"))
    W.set(tx + 1, S, tz + 3, M + "lapis_block")
    W.set(tx + 1, S + 1, tz + 3, dalle(M + "smooth_stone", "bottom"))
    W.set(tx + 1, S, tz + 4, dalle(M + "smooth_stone", "bottom"))
    # swings frame (fence posts + chains + slab seats), along x
    sx, sz = x0, z0 + 7
    for y in range(S, S + 4):
        W.set(sx, y, sz, M + "dark_oak_fence")
        W.set(sx + 4, y, sz, M + "dark_oak_fence")
    for x in range(sx, sx + 5):
        W.set(x, S + 4, sz, M + "dark_oak_fence")
    for x in (sx + 1, sx + 3):
        W.set(x, S + 3, sz, etat(M + "chain", axis="y", waterlogged="false"))
        W.set(x, S + 2, sz, etat(M + "chain", axis="y", waterlogged="false"))
        W.set(x, S + 1, sz, dalle(M + "dark_oak", "top"))
    # climbing frame: square iron frame 5 x 5 x 3 with a cross on top (all connected)
    dx0, dz0 = x0 + 7, z0 + 6
    for x in range(dx0, dx0 + 5):
        for z in range(dz0, dz0 + 5):
            bord = x in (dx0, dx0 + 4) or z in (dz0, dz0 + 4)
            coin = x in (dx0, dx0 + 4) and z in (dz0, dz0 + 4)
            if coin:
                for y in range(S, S + 3):
                    W.set(x, y, z, M + "iron_bars")
            elif bord:
                W.set(x, S + 2, z, M + "iron_bars")
                if (x + z) % 2 == 0:
                    W.set(x, S + 1, z, M + "iron_bars")
                    W.set(x, S, z, M + "iron_bars")
            elif x == dx0 + 2 or z == dz0 + 2:
                W.set(x, S + 2, z, M + "iron_bars")


def sous_station(x0, z0):
    """Heating substation: one-storey brick building 11 x 8, flat roof (stand y S+5) reached by an outside concrete
    stair, pumps and pipes inside, metal door."""
    x1, z1 = x0 + 10, z0 + 7
    for y in range(S, S + 4):
        for x in range(x0, x1 + 1):
            for z in (z0, z1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
        for z in range(z0, z1 + 1):
            for x in (x0, x1):
                W.set(x, y, z, M + "bricks" if y > S else M + "stone")
    W.fill(x0, S + 4, z0, x1, S + 4, z1, M + "light_gray_concrete")
    W.fill(x0 + 1, G, z0 + 1, x1 - 1, G, z1 - 1, M + "smooth_stone")
    for x in range(x0, x1 + 1):
        W.set(x, S + 5, z0, M + "stone_brick_wall")
        W.set(x, S + 5, z1, M + "stone_brick_wall")
    for z in range(z0, z1 + 1):
        W.set(x0, S + 5, z, M + "stone_brick_wall")
        W.set(x1, S + 5, z, M + "stone_brick_wall")
    for k in range(5):
        x = x0 + 2 + k
        W.set(x, S + k, z1 + 1, marche(M + "stone_brick", "east"))
        for y in range(S, S + k):
            W.set(x, y, z1 + 1, M + "stone_bricks")
    W.set(x0 + 7, S + 4, z1 + 1, M + "stone_bricks")
    for y in range(S, S + 4):
        W.set(x0 + 7, y, z1 + 1, M + "stone_bricks")
    W.set(x0 + 7, S + 5, z1, None)
    for x in range(x0 + 2, x1 - 1, 3):
        W.set(x, S + 2, z0, M + "iron_bars")
    W.clear(x0, S, z0 + 3, x0, S + 1, z0 + 4)
    W.set(x0 + 3, S, z0 + 3, D + "groupe_ventilation")
    W.set(x0 + 6, S, z0 + 3, oriente(U + "vanne", "north"))
    for x in range(x0 + 2, x1):
        W.set(x, S + 2, z0 + 6, D + "gaine_calorifugee") if x != x0 + 2 else W.set(x, S + 2, z0 + 6, M + "stone_bricks")
    W.set(x1 - 1, S + 2, z0 + 6, D + "gaine_calorifugee")
    W.set(x0 + 5, S + 3, z0 + 2, LUMIERE)
    W.set(x0 + 3, S + 5, z0 + 2, M + "stone_bricks")
    W.set(x0 + 8, S + 5, z0 + 5, D + "groupe_ventilation")


def enclos_poubelles(x0, z0):
    """Three-sided brick enclosure for waste containers (2 high), open to the east."""
    for y in range(S, S + 2):
        for x in range(x0, x0 + 6):
            W.set(x, y, z0, M + "white_terracotta")
            W.set(x, y, z0 + 4, M + "white_terracotta")
        for z in range(z0, z0 + 5):
            W.set(x0, y, z, M + "white_terracotta")
    W.fill(x0 + 1, G, z0 + 1, x0 + 5, G, z0 + 3, M + "gray_concrete")
    benne(x0 + 1, z0 + 1, "x")
    W.set(x0 + 2, S, z0 + 3, M + "lapis_block")
    W.set(x0 + 4, S, z0 + 2, oriente(C + "zombie_garbage_1", "east"))
    W.set(x0 + 3, S, z0 + 2, oriente(C + "zombie_newspapers", "east"))


def couloir_est():
    surface(100, 30, 127, 64, PELOUSE, graine=20)
    surface(105, 30, 111, 64, ASPHALTE, graine=21)
    surface(100, 30, 104, 64, TROTTOIR, graine=22)
    voiture(102, 35, "north", U + "epave_bleue")
    voiture(115, 31, "south", U + "epave_rouge")
    voiture_accidentee(107, 46, "north")
    barrage(108, 40, 111, 40)
    barrage(105, 54, 108, 54)
    W.set(107, S, 41, oriente(U + "herisson", "north"))
    W.set(109, S, 53, oriente(U + "herisson", "north"))
    pigeonnier(118, 33)
    for i in range(3):
        cabane(116 + i * 4, 46, i)
    transfo(121, 56)
    for z in range(58, 64):
        W.set(113, S, z, M + "smooth_stone")
        if z % 3:
            W.set(113, S + 1, z, M + "smooth_stone")
    beton_bloc(103, 58, "z", 3)
    caisses(124, 38, ["21", "1."])
    peuplier(126, 31, 11)
    lampadaire(104, 50, "west")
    poubelle(103, 41, "west")
    colonne_affiche(101, 46)
    cabine_tel(101, 62, "east")
    kiosque(102, 40, "east")
    abribus_nord_sud(100, 49, "east")
    caisses(100, 57, ["2", "2"])
    kiosque(113, 38, "west")
    caisses(114, 59, ["22", "21"])


def meta(n):  # noqa: C901
    cx, cz = centre()  # 65.0: symmetry centre (between cells 64 and 65)
    return {
        "id": ID,
        "nom_fr": "Cité du Kombinat",
        "nom_en": "Kombinat estate",
        "type": "clashwar_2v2",
        "dimension": "heraliz_clashwar:arenes",
        "taille": [SX, SY, SZ],
        "bornes": [2, 0, 2, SX - 3, SY + 3, SZ - 3],
        "points": {
            "spawn_a": [[62.5, float(S), 6.5, 0.0], [66.5, float(S), 6.5, 0.0]],
            "spawn_b": [miroir_point([62.5, float(S), 6.5, 0.0]), miroir_point([66.5, float(S), 6.5, 0.0])],
            "barriere_a": [[58.0, float(S), 3.0, 0.0], [71.0, float(S + 2), 9.0, 0.0]],
            "barriere_b": [[58.0, float(S), 120.0, 0.0], [71.0, float(S + 2), 126.0, 0.0]],
            "zone_centre": [[cx, float(S), cz, 0.0]],
            # inside the bounds (a spectator outside them is brought back to this point every tick)
            "spectateur": [[cx, 29.0, 2.5, 0.0], [cx, 29.0, SZ - 2.5, 180.0]],
            "bombe_a": [[112.5, float(S), 119.5, 160.0]],
            "site_a": [[112.5, float(S), 119.5, 160.0]],
            "bombe_b": [[14.5, float(S), 107.5, -160.0]],
            "site_b": [[14.5, float(S), 107.5, -160.0]],
            "charge": [[64.5, float(S), 6.5, 0.0]],
        },
        "format": "2v2",
        "joueurs_par_equipe": 2,
        "theme": "cité soviétique",
        "barrieres": {"a": [58, S, 3, 71, S + 2, 9], "b": [58, S, 120, 71, S + 2, 126]},
        "zone": {"centre": [cx, S, cz], "rayon_depart": 92.0, "rayon_min": 6.0},
        "symetrie": "centrale (rotation 180° autour du point x = 65, z = 65)",
        "blocs": n,
        "recherche_destruction": {
            "attaquants": "a",
            "defenseurs": "b",
            "note": "Sites A (garages sud-est) et B (marché sud-ouest) du côté de spawn_b, pour le mode recherche et "
                    "destruction (inactif aujourd'hui) : à déplacer librement dans generer_kombinat.py, la carte reste "
                    "symétrique.",
        },
        "generateur": "modules/clashwar/outils/cartes_clashwar/generer_kombinat.py",
    }


def construire():
    g.b.clear()
    sol()
    enceinte()
    garages_no()
    bande_nord()
    marche_ne()
    couloir_ouest()
    couloir_est()
    cour()
    immeubles()
    herbes_partout()


def main():
    K.construire_carte(ID, construire, meta)


if __name__ == "__main__":
    main()
