"""Props shared by the new ClashWar maps (rail depot, dry harbour, garrison): tracks, wagons, multi-block vehicles,
containers, round tanks, stairs and towers. Every write goes through xl_carte.W (the mirrored writer of the current map),
so call xl_carte.init() before using them.
"""
import math

import xl_carte as K
import xl_lib as xl
from xl_lib import dalle, etat, marche, oriente, tronc
from xl_formes import C, D, G, M, S, U, LUMIERE_F, garde_corps


def W():
    return K.W


def h(*a):
    return K.h(*a)


# ------------------------------------------------------------------------------------------------ multi-block decors
def multibloc(nom, x, z, facing, parties, y=S):
    """Place a heraliz_decors multi-block (cars, trucks, tracked vehicles): parties = [(name, col, row, dy)] laid out as
    the block does it (column to the right of `facing`, row toward the back)."""
    cw = xl.CW[facing]
    op = xl.OPPOSE[facing]
    for p, col, rang, dy in parties:
        px = x + xl.DIRS[cw][0] * col + xl.DIRS[op][0] * rang
        pz = z + xl.DIRS[cw][1] * col + xl.DIRS[op][1] * rang
        K.W.set(px, y + dy, pz, etat(D + nom, facing=facing, partie=p))


CAMION_CONTENEUR = [(f"r{i}_{c}", 0 if c == "g" else 1, i, 0) for i in range(8) for c in ("g", "d")] + \
                   [(f"h{i}_{c}", 0 if c == "g" else 1, i, 1) for i in range(2) for c in ("g", "d")]
CHENILLETTE = [("avant_gauche", 0, 0, 0), ("avant_droite", 1, 0, 0), ("milieu_gauche", 0, 1, 0), ("milieu_droite", 1, 1, 0),
               ("arriere_gauche", 0, 2, 0), ("arriere_droite", 1, 2, 0)]


def camion_conteneur(x, z, facing):
    multibloc("camion_porte_conteneur", x, z, facing, CAMION_CONTENEUR)


def chenillette(x, z, facing):
    multibloc("chenillette", x, z, facing, CHENILLETTE)


# ------------------------------------------------------------------------------------------------ railway
def voie(x0, z0, x1, z1, ballast=True):
    """A track: rail blocks on a gravel ballast line (1 wide); straight along x or z."""
    w = K.W
    if x0 == x1:
        for z in range(min(z0, z1), max(z0, z1) + 1):
            if ballast:
                w.set(x0, G, z, M + ("gravel" if h(x0, z) % 4 else "andesite"))
            if w.get(x0, S, z) is None:
                w.set(x0, S, z, etat(M + "rail", shape="north_south", waterlogged="false"))
    else:
        for x in range(min(x0, x1), max(x0, x1) + 1):
            if ballast:
                w.set(x, G, z0, M + ("gravel" if h(x, z0) % 4 else "andesite"))
            if w.get(x, S, z0) is None:
                w.set(x, S, z0, etat(M + "rail", shape="east_west", waterlogged="false"))


def wagon_couvert(x0, z0, long=9, axe="z", couleur=M + "brown_terracotta", porte_ouverte=True):
    """Boxcar 3 wide x `long` x 4 high (bogies, body 3 high, roof): solid wall of cover, open sliding door in the
    middle (one can walk through)."""
    w = K.W
    for i in range(long):
        for j in range(3):
            x, z = (x0 + j, z0 + i) if axe == "z" else (x0 + i, z0 + j)
            bout = i in (0, long - 1)
            bogie = i in (1, 2, long - 3, long - 2)
            w.set(x, S, z, M + "coal_block" if bogie else (M + "black_concrete" if bout else None))
            for y in (S + 1, S + 2, S + 3):
                bord = j in (0, 2) or bout
                if bord:
                    w.set(x, y, z, couleur if (y < S + 3 or not bout) else couleur)
            w.set(x, S + 4, z, dalle(M + "smooth_stone", "bottom") if j == 1 else M + "gray_concrete")
            if not bout and not bogie:
                w.set(x, S, z, M + "coal_block" if j == 1 else None)
    if porte_ouverte:
        m = long // 2
        for j in (0, 2):
            for y in (S + 1, S + 2):
                x, z = (x0 + j, z0 + m) if axe == "z" else (x0 + m, z0 + j)
                w.set(x, y, z, None)
    # a light inside so the dark car is not a black hole
    x, z = (x0 + 1, z0 + long // 2) if axe == "z" else (x0 + long // 2, z0 + 1)
    w.set(x, S + 3, z, LUMIERE_F)


def wagon_citerne(x0, z0, long=8, axe="z", couleur=M + "black_concrete"):
    """Tank car: round tank (3 x 2 cross-section) on two bogies, ladder-free, dome on top."""
    w = K.W
    for i in range(long):
        x_, z_ = (0, i) if axe == "z" else (i, 0)
        bogie = i in (1, long - 2)
        for j in range(3):
            x, z = (x0 + (j if axe == "z" else i), z0 + (i if axe == "z" else j))
            w.set(x, S, z, M + "coal_block" if (bogie or j == 1) else None)
            if j == 1:
                w.set(x, S + 1, z, couleur)
                w.set(x, S + 2, z, couleur)
                w.set(x, S + 3, z, dalle(M + "smooth_stone", "bottom") if i == long // 2 else None)
            else:
                w.set(x, S + 1, z, couleur if i not in (0, long - 1) else None)
                w.set(x, S + 2, z, couleur if i not in (0, long - 1) else None)
    # end caps
    for i in (0, long - 1):
        x, z = (x0 + 1, z0 + i) if axe == "z" else (x0 + i, z0 + 1)
        w.set(x, S + 1, z, M + "netherite_block")
        w.set(x, S + 2, z, M + "netherite_block")


def tombereau(x0, z0, long=8, axe="z", charge=M + "coal_block"):
    """Open gondola car: walls 2 high, half full of coal (one can climb in and crouch)."""
    w = K.W
    for i in range(long):
        for j in range(3):
            x, z = (x0 + j, z0 + i) if axe == "z" else (x0 + i, z0 + j)
            bout = i in (0, long - 1)
            w.set(x, S, z, M + "coal_block" if (i in (1, long - 2) or j == 1) else M + "black_concrete")
            bord = j in (0, 2) or bout
            for y in (S + 1, S + 2):
                if bord:
                    w.set(x, y, z, M + "lime_terracotta" if (i + y) % 3 else M + "red_terracotta")
            if not bord:
                w.set(x, S + 1, z, charge if h(x, z) % 3 else None)


def locomotive(x0, z0, long=12, axe="z", sens=1, couleur=M + "green_concrete"):
    """Diesel shunter: long hood, cab 4 high at one end (sens = 1: cab at the high end), railings, bogies."""
    w = K.W
    cab = range(long - 4, long) if sens == 1 else range(0, 4)
    for i in range(long):
        for j in range(3):
            x, z = (x0 + j, z0 + i) if axe == "z" else (x0 + i, z0 + j)
            w.set(x, S, z, M + "coal_block" if i in (1, 2, long - 3, long - 2) else M + "black_concrete")
            if i in cab:
                for y in (S + 1, S + 2, S + 3):
                    bord = j in (0, 2) or i in (cab[0], cab[-1])
                    if bord:
                        vitre = y == S + 2 and (j == 1 or i not in (cab[0], cab[-1]))
                        w.set(x, y, z, M + "gray_stained_glass_pane" if vitre else couleur)
                w.set(x, S + 4, z, M + "gray_concrete")
            elif i not in (0, long - 1):
                if j == 1:
                    w.set(x, S + 1, z, couleur)
                    w.set(x, S + 2, z, couleur if i % 3 else M + "lapis_block")
                else:
                    if axe == "z":
                        cote = "west" if j == 0 else "east"
                    else:
                        cote = "north" if j == 0 else "south"
                    w.set(x, S + 1, z, garde_corps(cote))
            else:
                w.set(x, S + 1, z, M + "yellow_terracotta" if j == 1 else None)
    x, z = (x0 + 1, z0 + (cab[0] + cab[-1]) // 2) if axe == "z" else (x0 + (cab[0] + cab[-1]) // 2, z0 + 1)
    w.set(x, S + 3, z, LUMIERE_F)


def butoir(x, z, facing):
    K.W.set(x, S, z, oriente(D + "butoir", facing))


# ------------------------------------------------------------------------------------------------ containers & tanks
COULEURS_CONTENEUR = ["bleu", "rouge", "vert", "orange", "gris", "blanc"]


def conteneur(x0, z0, axe="x", couleur="bleu", niveau=0, portes=True, long=6):
    """Shipping container 3 wide x `long` x 3 high made of corrugated container blocks, doors at one end; `niveau`
    stacks it (0 = on the ground)."""
    w = K.W
    y0 = S + 3 * niveau
    for i in range(long):
        for j in range(3):
            x, z = (x0 + i, z0 + j) if axe == "x" else (x0 + j, z0 + i)
            for y in range(y0, y0 + 3):
                fac = ("north" if j == 0 else "south") if axe == "x" else ("west" if j == 0 else "east")
                if i == long - 1 and portes:
                    fac = "east" if axe == "x" else "south"
                    w.set(x, y, z, oriente(D + "conteneur_porte_" + couleur, fac))
                else:
                    w.set(x, y, z, oriente(D + "conteneur_paroi_" + couleur, fac))


def citerne_ronde(cx, cz, r, haut, mat=M + "smooth_stone", cercle=M + "light_gray_concrete", toit=True):
    """Upright round tank (fuel / water) of radius r: shell only, ring bands every 4, flat roof."""
    w = K.W
    for y in range(S, S + haut):
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                d = math.hypot(x + 0.5 - cx, z + 0.5 - cz)
                if r - 1.0 <= d <= r:
                    w.set(x, y, z, cercle if (y - S) % 4 == 3 else mat)
    if toit:
        for x in range(int(cx - r - 1), int(cx + r + 2)):
            for z in range(int(cz - r - 1), int(cz + r + 2)):
                if math.hypot(x + 0.5 - cx, z + 0.5 - cz) <= r:
                    w.set(x, S + haut, z, mat)


def escalier_droit(x, z, sens, hauteur, mat=M + "stone_brick", plein=M + "stone_bricks", y0=S, largeur=1, cote=None):
    """Straight stair rising `hauteur` toward `sens`, filled underneath (solid), `largeur` wide to the side `cote`."""
    w = K.W
    dx, dz = xl.DIRS[sens]
    cote = cote or xl.CW[sens]
    px, pz = xl.DIRS[cote]
    for i in range(hauteur):
        for l_ in range(largeur):
            cx, cz = x + dx * i + px * l_, z + dz * i + pz * l_
            for yy in range(y0, y0 + i):
                w.set(cx, yy, cz, plein)
            w.set(cx, y0 + i, cz, marche(mat, sens))


def tas(x0, z0, profil, mat=M + "coal_block", dessus=None):
    """Heap from a height profile (list of strings, digits = height)."""
    w = K.W
    for j, ligne in enumerate(profil):
        for i, c in enumerate(ligne):
            if c.isdigit():
                n = int(c)
                for k in range(n):
                    w.set(x0 + i, S + k, z0 + j, dessus if (dessus and k == n - 1) else mat)
