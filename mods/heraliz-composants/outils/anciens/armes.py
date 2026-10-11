"""Composants d'armes : 6 types x 3 pieces (canon / mecanisme / carcasse) x 4 tiers,
plus quelques composants communs sans tier.
Modeles de profil : X = longueur (avant vers +X), Y = hauteur, Z = epaisseur (centree sur 8).
Les builders recoivent une palette P (role -> materiau) qui change selon le tier."""
import math

ITEMS = []

DIAG = {'roll': 32, 'yaw': -25, 'pitch': 25}
DIAG_STEEP = {'roll': 40, 'yaw': -22, 'pitch': 22}
TOP = {'roll': 0, 'yaw': -35, 'pitch': 38}
FRONT = {'roll': 0, 'yaw': -28, 'pitch': 20}

TIERS = {
    1: dict(metal='rusty_steel', metal2='rust', body='rusty_steel', poly='rusty_steel',
            grip='tape', furn='wood_raw', accent='tape', butt='rubber'),
    2: dict(metal='steel_dark', metal2='gunmetal', body='gunmetal_worn', poly='polymer',
            grip='polymer_grip', furn='wood', accent='steel', butt='rubber'),
    3: dict(metal='gunmetal', metal2='black', body='black', poly='polymer',
            grip='polymer_grip', furn='olive', accent='steel_dark', butt='rubber'),
    4: dict(metal='steel', metal2='gunmetal', body='tan', poly='tan',
            grip='tan_grip', furn='tan', accent='gold', butt='black'),
}
TIER_LABEL = {1: ('Bricolé', 'Makeshift'), 2: ('Usé', 'Worn'), 3: ('Militaire', 'Military'), 4: ('Élite', 'Elite')}

CLASSES = {
    'pistolet': ('Pistolet', 'Pistol'),
    'smg': ('SMG', 'SMG'),
    'ar': ("Fusil d'assaut", 'Assault rifle'),
    'pompe': ('Fusil à pompe', 'Shotgun'),
    'sniper': ('Sniper', 'Sniper rifle'),
    'lmg': ('Mitrailleuse', 'Machine gun'),
}
SLOTS = {'canon': ('Canon', 'Barrel'), 'mecanisme': ('Mécanisme', 'Action'), 'carcasse': ('Carcasse', 'Frame')}

PARTS = {}  # (cls, slot) -> dict(fr, en, hs, view, build)


def part(cls, slot, fr, en, hs, view):
    def deco(fn):
        PARTS[(cls, slot)] = dict(fr=fr, en=en, hs=hs, view=view, build=fn)
        return fn
    return deco


def rail(m, mat, x1, x2, y, z1=7.0, z2=9.0, pitch=1.0):
    m.box((x1, y, z1), (x2, y + 0.5, z2), mat)
    n = int((x2 - x1) / pitch)
    for i in range(n):
        x = x1 + i * pitch + 0.15
        m.box((x, y + 0.5, z1 + 0.1), (x + pitch * 0.5, y + 0.95, z2 - 0.1), mat)


def grip(m, P, x1, y1, x2, y2, piv, z1=6.8, z2=9.2):
    m.box((x1, y1, z1), (x2, y2, z2), P['grip'], ('z', -22.5, piv))
    m.box((x1 - 0.2, y1 - 0.5, z1 - 0.15), (x2 + 0.2, y1 + 0.05, z2 + 0.15), P['poly'], ('z', -22.5, piv))


# ================================================================ PISTOLET

@part('pistolet', 'canon', 'Canon de pistolet', 'Pistol Barrel', 0.55, DIAG)
def _(m, P):
    m.box((1, 6.1, 6.1), (5.2, 10.2, 9.9), P['metal'])
    m.box((1, 9.6, 6.6), (5.2, 10.6, 9.4), P['metal'])
    m.cyl('x', 5.2, 15, 8, 8, 1.6, P['metal'])
    m.cyl('x', 11, 12.2, 8, 8, 1.72, P['accent'])
    m.box((2, 4.6, 7.0), (4.6, 6.1, 9.0), P['metal2'])
    m.box((14.98, 7.45, 7.45), (15.05, 8.55, 8.55), 'chip')
    m.box((0.95, 7.3, 7.3), (1.0, 8.7, 8.7), 'chip')


@part('pistolet', 'mecanisme', 'Glissière de pistolet', 'Pistol Slide', 0.75, FRONT)
def _(m, P):
    m.box((1, 6, 6.5), (15, 10, 9.5), P['body'])
    m.box((1, 10, 7.0), (15, 10.6, 9.0), P['body'])
    for i in range(5):
        x = 1.6 + i * 0.85
        m.box((x, 6.6, 9.5), (x + 0.42, 9.6, 9.6), P['metal2'])
        m.box((x, 6.6, 6.4), (x + 0.42, 9.6, 6.5), P['metal2'])
    m.box((6, 8.4, 9.5), (9.6, 10.05, 9.56), P['metal'])
    m.box((6, 10.0, 7.2), (9.6, 10.65, 8.8), P['metal'])
    m.box((13.4, 10.6, 7.6), (14.2, 11.5, 8.4), P['metal2'])
    m.box((1.6, 10.6, 6.9), (2.8, 11.6, 9.1), P['metal2'])
    m.box((13.38, 11.0, 7.85), (13.42, 11.3, 8.15), P['accent'])
    m.box((15, 7.2, 7.4), (15.05, 8.6, 8.6), 'chip')


@part('pistolet', 'carcasse', 'Carcasse de pistolet', 'Pistol Frame', 0.6,
      {'roll': 0, 'yaw': -38, 'pitch': 40})
def _(m, P):
    # chassis interne (module de detente) : deux flancs ajoures relies par des axes
    for (z1, z2, zc) in ((5.4, 6.0, 5.36), (10.0, 10.6, 10.64)):
        m.box((2, 6, z1), (14, 9.4, z2), P['body'])
        for x in (5.0, 9.4):
            m.box((x, 6.8, min(zc, z1)), (x + 2.6, 8.6, max(zc, z2)), 'chip')
        for x in (2.2, 11.8):
            m.box((x, 9.4, z1 - 0.2), (x + 2.0, 10.2, z2 + 0.2), P['metal'])
    for x in (3.6, 8.2, 12.8):
        m.cyl('z', 5.0, 11.0, x, 6.8, 0.45, P['metal'])
    m.box((2.6, 6.4, 7.0), (4.8, 9.0, 9.0), P['metal2'])
    m.box((4.8, 6.6, 7.6), (12.4, 7.2, 8.4), P['metal2'])
    m.helix(5.4, 9.8, 8.4, 8.0, 0.75, 0.38, 6, 'spring')
    m.box((12.2, 6.2, 6.6), (14.2, 8.6, 9.4), P['metal'])
    m.box((13.2, 8.6, 7.6), (14.0, 9.2, 8.4), P['accent'])
    m.box((3.2, 9.0, 7.6), (4.2, 9.4, 8.4), P['accent'])


# ================================================================ SMG

@part('smg', 'canon', 'Canon de SMG', 'SMG Barrel', 0.6, DIAG)
def _(m, P):
    m.cyl('x', 0, 15, 8, 8, 1.0, P['metal'])
    m.box((0, 6.2, 6.4), (2.6, 9.8, 9.6), P['metal2'])
    m.cyl('x', 3.2, 10.4, 8, 8, 1.9, P['body'])
    for x in (4.0, 6.2, 8.4):
        m.box((x, 7.45, 9.86), (x + 1.3, 8.55, 9.94), 'chip')
        m.box((x, 9.86, 7.45), (x + 1.3, 9.94, 8.55), 'chip')
    m.cyl('x', 10.8, 11.6, 8, 8, 1.2, P['accent'])
    for (dy, dz) in ((1.15, 0), (-0.6, 1.0), (-0.6, -1.0)):
        m.box((13.4, 8 + dy - 0.3, 8 + dz - 0.3), (14.6, 8 + dy + 0.3, 8 + dz + 0.3), P['metal2'])
    m.box((14.98, 7.6, 7.6), (15.05, 8.4, 8.4), 'chip')


@part('smg', 'mecanisme', 'Culasse de SMG', 'SMG Bolt', 0.6, DIAG)
def _(m, P):
    m.box((2, 6.4, 6.6), (13, 9.6, 9.4), P['metal'])
    m.box((3, 9.6, 7.5), (11, 9.66, 8.5), 'chip')
    m.cyl('x', 13, 15, 8, 8, 1.2, P['metal2'])
    m.cyl('x', 0, 2, 8, 8, 0.5, P['metal2'])
    m.box((5, 8.4, 9.4), (6, 9.2, 12), P['metal2'])
    m.box((4.6, 8.1, 11.6), (6.4, 9.5, 12.8), P['accent'])
    m.box((14.98, 7.5, 7.5), (15.05, 8.5, 8.5), 'chip')


@part('smg', 'carcasse', 'Carcasse de SMG', 'SMG Receiver', 0.8, FRONT)
def _(m, P):
    m.box((2, 8, 6.6), (14, 11, 9.4), P['body'])
    m.box((0.8, 7.6, 6.8), (2, 11.4, 9.2), P['body'])
    grip(m, P, 3.8, 2.0, 7.2, 8.2, (5.5, 6.0, 8))
    m.box((8.6, 3.6, 7.0), (11.2, 8, 9.0), P['body'])
    m.box((5.6, 5.4, 7.4), (8.6, 6.0, 8.6), P['body'])
    m.box((7.0, 6.0, 7.7), (7.5, 7.6, 8.3), P['metal2'])
    m.box((1, 10.2, 9.45), (12, 10.8, 10.0), P['metal2'])
    m.box((1, 8.4, 9.45), (12, 9.0, 10.0), P['metal2'])
    m.box((0, 8.0, 9.3), (1.0, 11.2, 10.2), P['butt'])
    m.box((9.5, 9.0, 9.4), (11.5, 10.0, 9.46), 'chip')
    m.box((3.0, 9.2, 9.4), (3.8, 9.8, 9.5), P['accent'])


# ================================================================ FUSIL D'ASSAUT

@part('ar', 'canon', "Canon d'AR", 'AR Barrel', 0.95, DIAG_STEEP)
def _(m, P):
    m.cyl('x', 0, 3.4, 8, 8, 1.75, P['metal'])
    m.cyl('x', 3.4, 14.6, 8, 8, 1.15, P['metal'])
    m.cyl('x', 14.6, 16, 8, 8, 1.0, P['metal2'])
    m.box((9.2, 6.9, 6.6), (10.8, 10.4, 9.4), P['metal2'])
    m.box((3.4, 9.3, 7.75), (9.2, 9.8, 8.25), P['metal2'])
    m.cyl('x', 5.6, 6.4, 8, 8, 1.25, P['accent'])
    m.box((15.98, 7.55, 7.55), (16.05, 8.45, 8.45), 'chip')


@part('ar', 'mecanisme', "Culasse d'AR", 'AR Bolt Carrier', 0.65, DIAG)
def _(m, P):
    m.cyl('x', 3, 14, 8, 8, 1.95, P['metal'])
    m.cyl('x', 0.6, 3, 8, 8, 1.15, P['metal2'])
    m.cyl('x', 0.6, 1.4, 8, 8, 1.45, P['metal2'])
    m.box((6, 9.6, 7.3), (11.2, 11.0, 8.7), P['metal2'])
    m.box((7.0, 11.0, 7.75), (7.5, 11.1, 8.25), P['accent'])
    m.box((9.6, 11.0, 7.75), (10.1, 11.1, 8.25), P['accent'])
    m.box((4.2, 7.4, 9.7), (5.2, 8.6, 10.6), P['metal2'])
    m.cyl('x', 14, 15.6, 8, 8, 0.45, P['metal2'])
    m.box((8, 7.0, 9.85), (10.5, 9.0, 9.95), 'chip')


@part('ar', 'carcasse', "Boîte de culasse d'AR", 'AR Receiver', 0.95, FRONT)
def _(m, P):
    m.box((2, 9, 6.7), (14, 11.4, 9.3), P['body'])
    rail(m, P['metal2'], 2, 14, 11.4)
    m.box((6, 9.5, 9.3), (9.5, 10.8, 9.36), 'chip')
    m.cyl('x', 2.4, 4, 10.4, 9.6, 0.5, P['body'])
    m.box((2, 6.4, 6.8), (12, 9, 9.2), P['body'])
    m.box((8, 2.6, 6.9), (11.6, 6.4, 9.1), P['body'])
    grip(m, P, 3.2, 1.0, 6.4, 6.8, (4.8, 4.5, 8))
    m.box((6.2, 5.2, 7.5), (8.2, 5.8, 8.5), P['body'])
    m.box((7.0, 5.8, 7.7), (7.5, 7.2, 8.3), P['metal2'])
    m.cyl('x', 0, 2, 8.4, 8, 1.2, P['metal2'])
    m.box((4.2, 7.8, 9.2), (5.2, 8.4, 9.3), P['accent'])
    m.box((10.8, 7.2, 9.2), (11.4, 7.8, 9.3), P['accent'])


# ================================================================ FUSIL A POMPE

@part('pompe', 'canon', 'Canon de pompe', 'Shotgun Barrel', 1.0, DIAG_STEEP)
def _(m, P):
    m.cyl('x', 0, 1.6, 8, 8, 1.8, P['metal2'])
    m.cyl('x', 1.6, 16, 8, 8, 1.45, P['metal'])
    m.cyl('x', 3, 3.6, 8, 8, 1.56, P['accent'])
    for i in range(10):
        x = 2.2 + i * 1.4
        m.box((x, 9.4, 7.8), (x + 0.4, 10.1, 8.2), P['metal2'])
    m.box((1.8, 10.1, 7.4), (16, 10.5, 8.6), P['metal2'])
    m.box((15.3, 10.5, 7.75), (15.8, 11.0, 8.25), P['accent'])
    m.box((15.98, 7.2, 7.2), (16.05, 8.8, 8.8), 'chip')


@part('pompe', 'mecanisme', 'Mécanisme de pompe', 'Shotgun Pump', 0.9, DIAG_STEEP)
def _(m, P):
    m.cyl('x', 0, 15, 8, 8, 1.2, P['metal2'])
    m.cyl('x', 15, 16, 8, 8, 1.3, P['accent'])
    m.cyl('x', 4, 11, 8, 8, 2.0, P['furn'])
    for x in (5.0, 6.4, 7.8, 9.2):
        m.cyl('x', x, x + 0.5, 8, 8, 2.18, P['furn'])
    m.box((0, 7.4, 9.1), (4, 8.0, 9.45), P['metal'])
    m.box((0, 7.4, 6.55), (4, 8.0, 6.9), P['metal'])


@part('pompe', 'carcasse', 'Boîtier de pompe', 'Shotgun Receiver', 0.95, FRONT)
def _(m, P):
    m.box((8, 6.5, 6.7), (16, 10.5, 9.3), P['body'])
    m.box((10.5, 8.0, 9.3), (14, 9.8, 9.36), 'chip')
    m.box((9, 5.3, 7.5), (12.5, 5.9, 8.5), P['metal2'])
    m.box((12.0, 5.3, 7.5), (12.6, 6.5, 8.5), P['metal2'])
    m.box((10.2, 5.9, 7.7), (10.7, 6.5, 8.3), P['metal'])
    m.box((0, 2, 6.5), (1, 10, 9.5), P['butt'])
    m.box((1, 2, 6.6), (5, 9.8, 9.4), P['furn'])
    m.box((5, 3.4, 6.7), (8, 9.4, 9.3), P['furn'])
    m.box((7, 5.0, 6.8), (8.2, 9.0, 9.2), P['furn'])
    m.box((8.4, 10.5, 7.6), (9.2, 10.9, 8.4), P['accent'])


# ================================================================ SNIPER

@part('sniper', 'canon', 'Canon de sniper', 'Sniper Barrel', 1.0, DIAG_STEEP)
def _(m, P):
    m.cyl('x', 0, 3, 8, 8, 2.0, P['metal'])
    m.cyl('x', 2.6, 3.2, 8, 8, 2.1, P['accent'])
    m.cyl('x', 3, 14, 8, 8, 1.6, P['metal'])
    m.box((4, 7.6, 9.58), (13, 8.4, 9.64), P['metal2'])
    m.box((4, 9.58, 7.6), (13, 9.64, 8.4), P['metal2'])
    m.box((14, 6.6, 6.6), (16, 9.4, 9.4), P['metal2'])
    for x in (14.3, 15.1):
        m.box((x, 7.2, 9.4), (x + 0.5, 8.8, 9.46), 'chip')
        m.box((x, 7.2, 6.54), (x + 0.5, 8.8, 6.6), 'chip')
    m.box((15.98, 7.6, 7.6), (16.05, 8.4, 8.4), 'chip')


@part('sniper', 'mecanisme', 'Verrou de sniper', 'Sniper Bolt', 0.7, DIAG)
def _(m, P):
    m.cyl('x', 2, 14, 8, 8, 1.2, P['metal'])
    m.box((13, 6.3, 7.4), (14.4, 9.7, 8.6), P['metal'])
    m.cyl('x', 0.6, 2, 8, 8, 1.45, P['metal2'])
    m.box((0, 6.4, 7.4), (1.2, 7.6, 8.6), P['metal2'])
    m.box((2.6, 7.4, 8.8), (3.6, 8.6, 12.4), P['metal'], ('x', 22.5, (3.1, 8, 9)))
    c = (3.1, 8 - 3.6 * math.sin(math.radians(22.5)), 9 + 3.6 * math.cos(math.radians(22.5)))
    m.box((c[0] - 0.95, c[1] - 0.95, c[2] - 0.95), (c[0] + 0.95, c[1] + 0.95, c[2] + 0.95), P['accent'])
    m.box((6, 9.15, 7.6), (11, 9.25, 8.4), 'chip')


@part('sniper', 'carcasse', 'Châssis de sniper', 'Sniper Chassis', 1.0, FRONT)
def _(m, P):
    m.box((6, 7, 6.6), (16, 10, 9.4), P['body'])
    rail(m, P['metal2'], 6, 16, 10)
    m.box((9, 4.4, 7.0), (11.6, 7, 9.0), P['body'])
    grip(m, P, 4.4, 1.5, 7.6, 7.4, (6, 4.8, 8))
    m.box((7.4, 5.4, 7.5), (9.2, 6.0, 8.5), P['body'])
    m.box((0, 8.4, 7.0), (6, 9.8, 9.0), P['poly'])
    m.box((1, 9.8, 7.2), (5, 11.0, 8.8), P['poly'])
    m.box((0.5, 3, 7.2), (4.5, 4.2, 8.8), P['poly'])
    m.obox((3.2, 6.2, 8), (5.2, 1.0, 1.6), P['poly'], 'z', 45)
    m.box((-0.8, 3, 6.6), (0.2, 11, 9.4), P['butt'])
    m.box((2.6, 11.0, 7.6), (3.4, 11.3, 8.4), P['accent'])


# ================================================================ MITRAILLEUSE

@part('lmg', 'canon', 'Canon de mitrailleuse', 'Machine Gun Barrel', 1.0, DIAG_STEEP)
def _(m, P):
    m.box((0, 6, 6.2), (3, 10, 9.8), P['metal2'])
    m.cyl('x', 3, 15, 8, 8, 1.4, P['metal'])
    m.cyl('x', 15, 16.2, 8, 8, 1.1, P['metal2'])
    m.cyl('x', 13, 13.6, 8, 8, 1.52, P['accent'])
    m.box((5, 9.3, 7.6), (5.8, 12, 8.4), P['metal2'])
    m.box((9.2, 9.3, 7.6), (10, 12, 8.4), P['metal2'])
    m.box((4.6, 12, 7.2), (10.4, 13.2, 8.8), P['furn'])
    m.box((11, 5.4, 6.8), (12.6, 9.4, 9.2), P['metal2'])
    m.cyl('x', 3, 11, 6.0, 8, 0.5, P['metal2'])
    m.box((16.18, 7.5, 7.5), (16.25, 8.5, 8.5), 'chip')


@part('lmg', 'mecanisme', 'Alimentation de mitrailleuse', 'Machine Gun Feed', 0.8, TOP)
def _(m, P):
    m.box((2, 5, 5), (14, 7, 11), P['body'])
    m.box((2, 7, 5.6), (14, 9.2, 10.4), P['body'])
    m.box((12.5, 9.2, 7), (14, 9.8, 9), P['accent'])
    m.cyl('z', 5.2, 10.8, 2, 8.6, 0.6, P['metal2'])
    m.box((4, 6.0, 11), (10.5, 7.0, 11.06), 'chip')
    for z in (11.8, 13.3, 14.8):
        m.box((5, 6.0, z - 0.7), (9, 6.4, z + 0.7), P['metal2'])
        m.cyl('x', 3.6, 9.6, 6.9, z, 0.6, 'brass')
        m.cyl('x', 9.6, 10.6, 6.9, z, 0.4, 'copper')


@part('lmg', 'carcasse', 'Carcasse de mitrailleuse', 'Machine Gun Receiver', 1.0, FRONT)
def _(m, P):
    m.box((4, 7, 6.4), (16, 11, 9.6), P['body'])
    rail(m, P['metal2'], 4, 16, 11)
    grip(m, P, 5, 1.2, 8.2, 7.2, (6.5, 4.6, 8))
    m.box((8, 5.4, 7.5), (10.6, 6.0, 8.5), P['body'])
    m.box((9.0, 6.0, 7.7), (9.5, 7.2, 8.3), P['metal2'])
    m.box((0.9, 6, 6.6), (4, 10.6, 9.4), P['poly'])
    m.box((0.9, 4, 6.8), (3, 6, 9.2), P['poly'])
    m.box((0, 4, 6.4), (0.9, 10.8, 9.6), P['butt'])
    m.box((10.6, 2.4, 6.0), (15.4, 7, 10), P['furn'])
    m.box((10.4, 6.6, 5.8), (15.6, 7.0, 10.2), P['furn'])
    m.box((12, 4, 9.98), (14, 5.4, 10.04), P['accent'])
    m.box((6, 9.2, 9.6), (7, 9.8, 9.7), P['accent'])


for cls in CLASSES:
    for slot in SLOTS:
        p = PARTS[(cls, slot)]
        for tier in (1, 2, 3, 4):
            ITEMS.append(dict(
                id=f'{cls}_{slot}_t{tier}', fr=f"{p['fr']} T{tier}", en=f"{p['en']} T{tier}",
                tier=tier, cls=cls, slot=slot, hs=p['hs'], view=p['view'], stack=16,
                build=(lambda m, b=p['build'], P=TIERS[tier]: b(m, P))))


# ================================================================ COMMUNS (sans tier)

def common(id, fr, en, desc_fr, desc_en, hs, view, stack=32, cls='commun'):
    def deco(fn):
        ITEMS.append(dict(id=id, fr=fr, en=en, tier=0, cls=cls, slot=None, hs=hs, view=view,
                          stack=stack, desc_fr=desc_fr, desc_en=desc_en, build=fn))
        return fn
    return deco


@common('circuit_imprime', 'Circuit imprimé', 'Circuit Board',
        "Pour les lasers, lampes et optiques.", 'For lasers, lights and optics.', 0.6, TOP)
def _(m):
    m.box((1, 7.4, 2), (15, 8.1, 14), 'pcb')
    m.box((4, 8.1, 5), (9, 8.8, 9), 'chip')
    for i in range(6):
        x = 4.3 + i * 0.8
        m.box((x, 8.1, 4.6), (x + 0.35, 8.4, 5.0), 'steel')
        m.box((x, 8.1, 9.0), (x + 0.35, 8.4, 9.4), 'steel')
    m.cyl('y', 8.1, 10.4, 11.5, 4.5, 0.85, 'blue')
    m.cyl('y', 10.4, 10.6, 11.5, 4.5, 0.85, 'aluminum')
    m.cyl('y', 8.1, 9.6, 13, 7, 0.6, 'blue')
    m.box((10.2, 8.1, 9.6), (13.4, 8.6, 10.2), 'tan')
    m.box((2, 8.1, 10.4), (3.2, 9.6, 13.4), 'black')
    for z in (10.9, 11.8, 12.7):
        m.box((2.4, 9.6, z), (2.8, 10.3, z + 0.4), 'brass')
    m.box((4.5, 8.1, 11), (8.5, 8.6, 12.6), 'chip')


@common('batterie', 'Batterie', 'Battery',
        "Alimente lunettes, lasers et lampes.", 'Powers scopes, lasers and lights.', 0.45, FRONT)
def _(m):
    for z in (6.4, 9.6):
        m.cyl('y', 1, 11, 8, z, 1.55, 'cell_blue')
        m.cyl('y', 11, 11.6, 8, z, 1.4, 'aluminum')
        m.cyl('y', 11.6, 12.2, 8, z, 0.55, 'aluminum')
        m.cyl('y', 0.6, 1, 8, z, 1.4, 'aluminum')
    m.box((6.2, 4.6, 5.0), (9.8, 7.4, 11.0), 'tape')


@common('ferraille', 'Ferraille', 'Scrap Metal',
        "Un peu de tout, beaucoup de rouille.", 'A bit of everything, lots of rust.', 0.7, TOP, 64)
def _(m):
    m.box((1, 3, 3), (12, 4, 13), 'rusty_steel', ('z', -22.5, (6.5, 3.5, 8)))
    m.box((3, 5.4, 4.8), (15, 7.6, 7.2), 'rust', ('y', 22.5, (9, 6.5, 6)))
    m.box((3.5, 5.0, 5.4), (14.5, 8.0, 6.6), 'rust', ('y', 22.5, (9, 6.5, 6)))
    m.box((2, 4.2, 10), (11, 5.2, 11.2), 'steel_dark', ('y', -45, (6.5, 4.7, 10.6)))
    m.box((9, 3.6, 8.4), (13, 6.4, 12.4), 'rust')
    m.box((4.5, 4.0, 8.0), (7.5, 5.6, 10.2), 'zinc', ('y', 22.5, (6, 4.8, 9.1)))


@common('poudre_a_canon', 'Poudre à canon', 'Gunpowder',
        "Sac de poudre noire. Loin du feu.", 'Bag of black powder. Keep away from fire.', 0.55,
        {'roll': 0, 'yaw': -30, 'pitch': 28}, 64)
def _(m):
    # sac en toile arrondi, ficele au col
    m.cyl('y', 0, 0.8, 5.5, 8, 3.4, 'burlap')
    m.cyl('y', 0.8, 5.2, 5.5, 8, 4.0, 'burlap')
    m.cyl('y', 5.2, 6.6, 5.5, 8, 3.1, 'burlap')
    m.cyl('y', 6.6, 7.8, 5.5, 8, 1.5, 'burlap')
    m.cyl('y', 7.4, 8.1, 5.5, 8, 1.7, 'string')
    m.cyl('y', 7.8, 9.0, 5.5, 8, 2.1, 'burlap')
    m.box((6.6, 7.0, 9.4), (7.2, 8.0, 10.6), 'string', ('x', 22.5, (6.9, 7.8, 9.4)))
    # tas de poudre renverse devant
    m.box((8.6, 0, 5.4), (15.4, 0.7, 12.4), 'powder')
    m.box((9.6, 0.7, 6.4), (14.4, 1.5, 11.4), 'powder')
    m.box((10.6, 1.5, 7.4), (13.4, 2.2, 10.4), 'powder')
    m.box((11.4, 2.2, 8.2), (12.6, 2.7, 9.6), 'powder')


@common('scotch', 'Scotch', 'Duct Tape',
        "Répare tout. Presque.", 'Fixes everything. Almost.', 0.5, {'roll': 0, 'yaw': -32, 'pitch': 20})
def _(m):
    m.ring('z', 5.6, 10.4, 8, 8.6, 5.4, 2.4, 'tape')
    m.ring('z', 5.8, 10.2, 8, 8.6, 3.0, 0.6, 'cardboard')
    m.box((8, 2.9, 5.6), (13.4, 3.4, 10.4), 'tape', ('z', -22.5, (8, 3.2, 8)))


# ================================================================ DECHETS (inutiles)

@common('canette_ecrasee', 'Canette écrasée', 'Crushed Can',
        "Vide, écrasée, inutile.", 'Empty, crushed, useless.', 0.45, DIAG, 64, 'dechet')
def _(m):
    m.cyl('x', 1, 5, 8, 8, 2.4, 'red')
    m.cyl('x', 0.6, 1, 8, 8, 2.0, 'aluminum')
    m.box((5, 6.6, 6.0), (8.6, 9.0, 10.0), 'red', ('z', 22.5, (6.8, 7.8, 8)))
    m.box((5.6, 6.2, 6.6), (8.0, 9.6, 9.4), 'red', ('y', 22.5, (6.8, 7.8, 8)))
    m.cyl('x', 8.6, 12.6, 7.4, 8, 2.4, 'red')
    m.cyl('x', 12.6, 13.0, 7.4, 8, 2.0, 'aluminum')
    m.box((12.98, 7.4, 7.4), (13.04, 8.4, 8.6), 'chip')


@common('conserve_vide', 'Boîte de conserve vide', 'Empty Tin Can',
        "Même pas une miette dedans.", 'Not even a crumb inside.', 0.45, TOP, 64, 'dechet')
def _(m):
    m.ring('y', 0.6, 9, 8, 8, 4.0, 0.6, 'tin')
    m.cyl('y', 0, 0.6, 8, 8, 4.0, 'tin')
    m.ring('y', 3, 7, 8, 8, 4.06, 0.2, 'label_red')
    m.box((4.4, 9.0, 3.6), (11.6, 9.4, 7.0), 'tin', ('x', -45, (8, 9.0, 4.0)))


@common('telephone_casse', 'Téléphone cassé', 'Broken Phone',
        "Écran fissuré, plus de batterie, plus de réseau.", 'Cracked screen, no battery, no signal.',
        0.45, TOP, 16, 'dechet')
def _(m):
    m.box((2, 7.2, 4.6), (14, 8.4, 11.4), 'black')
    m.box((2.6, 8.4, 5.2), (13.4, 8.46, 10.8), 'screen')
    m.box((12.0, 8.4, 7.4), (12.6, 8.5, 8.6), 'steel_dark')
    m.box((4.0, 6.9, 5.6), (6.2, 7.2, 7.4), 'steel_dark')


@common('journal_moisi', 'Journal moisi', 'Moldy Newspaper',
        "Les nouvelles d'avant. Illisible.", 'News from before. Unreadable.', 0.55, TOP, 16, 'dechet')
def _(m):
    m.box((2, 7.0, 3), (14, 7.6, 13), 'newspaper')
    m.box((2.2, 7.6, 3.4), (13.6, 8.0, 12.6), 'newspaper', ('y', 22.5, (8, 7.8, 8)))
    m.box((3.0, 8.0, 4.2), (12.8, 8.2, 11.6), 'newspaper', ('y', -22.5, (8, 8.1, 8)))
