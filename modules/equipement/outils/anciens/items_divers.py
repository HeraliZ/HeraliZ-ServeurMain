"""Catalogue des composants. Chaque piece est modelisee de profil :
X = longueur (avant vers +X), Y = hauteur, Z = epaisseur (centree sur 8).
cyl(axe, debut, fin, c1, c2, rayon, mat) : c1/c2 = coordonnees des deux autres axes."""
from engine import Model

ITEMS = []


def item(id, fr, en, cat, desc_fr, desc_en, rarity='common', stack=16, hs=0.7, view=None):
    def deco(fn):
        ITEMS.append(dict(id=id, fr=fr, en=en, cat=cat, desc_fr=desc_fr, desc_en=desc_en,
                          rarity=rarity, stack=stack, hs=hs, view=view or {}, build=fn))
        return fn
    return deco


DIAG = {'roll': 32, 'yaw': -25, 'pitch': 25}
DIAG_STEEP = {'roll': 40, 'yaw': -22, 'pitch': 22}
TOP = {'roll': 0, 'yaw': -35, 'pitch': 38}
FRONT = {'roll': 0, 'yaw': -28, 'pitch': 20}

# =============================================================== PIECES D'ARMES


@item('ressort_recuperateur', 'Ressort récupérateur', 'Recoil Spring', 'arme',
      "Ramène la culasse après chaque tir.", 'Returns the slide after every shot.',
      'common', 16, 0.6, DIAG)
def _(m):
    m.cyl('x', 0.6, 15.4, 8, 8, 0.45, 'steel_dark')
    m.helix(1, 15, 8, 8, 2.4, 0.85, 7, 'spring')


@item('ressort_chargeur', 'Ressort de chargeur', 'Magazine Spring', 'arme',
      "Ressort plat qui pousse les cartouches.", 'Flat spring that feeds the rounds.',
      'common', 16, 0.6, DIAG)
def _(m):
    loops, x1, x2, a, b, w = 7, 1.0, 15.0, 1.3, 3.0, 0.55
    p = (x2 - x1 - w) / loops
    for k in range(loops):
        x = x1 + k * p
        q = p / 4
        m.box((x, 8 - a, 8 + b - w), (x + w + q, 8 + a, 8 + b), 'spring')
        m.box((x + q, 8 + a - w, 8 - b), (x + q + w + q, 8 + a, 8 + b), 'spring')
        m.box((x + 2 * q, 8 - a, 8 - b), (x + 2 * q + w + q, 8 + a, 8 - b + w), 'spring')
        m.box((x + 3 * q, 8 - a, 8 - b), (x + 3 * q + w + q, 8 - a + w, 8 + b), 'spring')
    m.box((x2 - 1.2, 8 - a - 0.3, 8 - b - 0.3), (x2, 8 + a + 0.3, 8 + b + 0.3), 'olive')


@item('canon_court', 'Canon court', 'Short Barrel', 'arme',
      "Canon de pistolet, rayures encore nettes.", 'Pistol barrel, rifling still sharp.',
      'uncommon', 4, 0.6, DIAG)
def _(m):
    m.box((1, 6.1, 6.1), (5.2, 10.2, 9.9), 'steel')
    m.box((1, 9.6, 6.6), (5.2, 10.6, 9.4), 'steel')
    m.cyl('x', 5.2, 15, 8, 8, 1.6, 'steel')
    m.box((2, 4.6, 7.0), (4.6, 6.1, 9.0), 'steel_dark')
    m.box((14.98, 7.45, 7.45), (15.05, 8.55, 8.55), 'chip')
    m.box((0.95, 7.3, 7.3), (1.0, 8.7, 8.7), 'chip')


@item('canon_long', 'Canon de fusil', 'Rifle Barrel', 'arme',
      "Long canon avec bloc d'emprunt de gaz.", 'Long barrel with gas block.',
      'rare', 2, 0.95, DIAG_STEEP)
def _(m):
    m.cyl('x', 0, 3.4, 8, 8, 1.75, 'gunmetal')
    m.cyl('x', 3.4, 15, 8, 8, 1.15, 'gunmetal')
    m.cyl('x', 15, 16, 8, 8, 0.85, 'thread')
    m.box((9.2, 6.9, 6.6), (10.8, 10.4, 9.4), 'black')
    m.box((9.6, 10.4, 7.5), (10.4, 10.9, 8.5), 'black')
    m.box((15.98, 7.6, 7.6), (16.05, 8.4, 8.4), 'chip')


@item('culasse', 'Culasse', 'Bolt Carrier', 'arme',
      "Ensemble culasse mobile, à graisser.", 'Bolt carrier group, needs oil.',
      'rare', 4, 0.65, DIAG)
def _(m):
    m.cyl('x', 3, 14, 8, 8, 1.95, 'gunmetal')
    m.cyl('x', 0.6, 3, 8, 8, 1.15, 'steel')
    m.cyl('x', 0.6, 1.4, 8, 8, 1.45, 'steel')
    m.box((6, 9.6, 7.3), (11.2, 11.0, 8.7), 'steel_dark')
    m.box((10.4, 9.9, 7.0), (11.2, 10.7, 9.0), 'steel_dark')
    m.box((4.2, 7.4, 9.7), (5.2, 8.6, 10.6), 'steel')
    m.cyl('x', 14, 15.6, 8, 8, 0.45, 'steel')
    m.box((8, 7.0, 9.85), (10.5, 9.0, 9.95), 'chip')


@item('glissiere', 'Glissière de pistolet', 'Pistol Slide', 'arme',
      "Glissière usinée avec ses organes de visée.", 'Machined slide with sights.',
      'rare', 4, 0.75, FRONT)
def _(m):
    m.box((1, 6, 6.5), (15, 10, 9.5), 'black')
    m.box((1, 10, 7.0), (15, 10.6, 9.0), 'black')
    for i in range(5):
        x = 1.6 + i * 0.85
        m.box((x, 6.6, 9.5), (x + 0.42, 9.6, 9.6), 'gunmetal')
        m.box((x, 6.6, 6.4), (x + 0.42, 9.6, 6.5), 'gunmetal')
    m.box((6, 8.4, 9.5), (9.6, 10.05, 9.56), 'steel')
    m.box((6, 10.0, 7.2), (9.6, 10.65, 8.8), 'steel')
    m.box((13.4, 10.6, 7.6), (14.2, 11.5, 8.4), 'black')
    m.box((1.6, 10.6, 6.9), (2.8, 11.6, 9.1), 'black')
    m.box((13.38, 11.0, 7.85), (13.42, 11.3, 8.15), 'white')
    m.box((15, 7.2, 7.4), (15.05, 8.6, 8.6), 'chip')


@item('carcasse', 'Carcasse de pistolet', 'Pistol Frame', 'arme',
      "Carcasse polymère, numéro de série limé.", 'Polymer frame, serial filed off.',
      'rare', 4, 0.7, FRONT)
def _(m):
    m.box((1, 8, 6.7), (15, 9.6, 9.3), 'polymer')
    m.box((0.4, 8.6, 7.0), (1.6, 9.3, 9.0), 'polymer')
    piv = (3.6, 5.0, 8)
    m.box((1.8, 0.6, 6.8), (5.4, 8.2, 9.2), 'polymer_grip', ('z', -22.5, piv))
    m.box((1.6, 0.2, 6.6), (5.6, 0.8, 9.4), 'polymer', ('z', -22.5, piv))
    m.box((5.2, 5.0, 7.4), (9.6, 5.6, 8.6), 'polymer')
    m.box((9.0, 5.0, 7.4), (9.6, 8.0, 8.6), 'polymer')
    m.box((6.6, 5.6, 7.6), (7.2, 8.0, 8.4), 'black', ('z', 22.5, (6.9, 8.0, 8)))
    m.box((10, 7.6, 6.9), (15, 8.0, 9.1), 'black')


@item('percuteur', 'Percuteur', 'Firing Pin', 'arme',
      "Petite pièce, gros problème si elle manque.", 'Tiny part, big trouble if missing.',
      'uncommon', 16, 0.45, DIAG)
def _(m):
    m.cyl('x', 1, 3.2, 8, 8, 1.0, 'steel_dark')
    m.cyl('x', 3.2, 15, 8, 8, 0.55, 'steel')
    m.cyl('x', 15, 15.9, 8, 8, 0.3, 'steel')
    m.cyl('x', 8.6, 9.6, 8, 8, 1.15, 'steel')
    m.helix(9.8, 14.4, 8, 8, 1.0, 0.35, 5, 'spring')


@item('mecanisme_detente', 'Mécanisme de détente', 'Trigger Group', 'arme',
      "Détente, chien et ressorts dans leur boîtier.", 'Trigger, hammer and springs in a housing.',
      'rare', 4, 0.6, FRONT)
def _(m):
    m.box((1, 3, 6), (15, 4, 10), 'black')
    m.box((1, 3, 5.5), (15, 9, 6), 'black')
    piv = (5.2, 4.8, 8)
    m.box((4, 4, 7.2), (6.4, 10.2, 8.8), 'steel', ('z', 22.5, piv))
    m.box((3.0, 9.0, 7.4), (4.4, 10.8, 8.6), 'steel', ('z', 22.5, piv))
    m.box((8, 4, 7.3), (12, 5.6, 8.7), 'steel')
    m.box((9.2, 5.6, 7.5), (12.6, 6.4, 8.5), 'steel_dark')
    m.box((9.5, 0.8, 7.5), (10.6, 3.0, 8.5), 'steel')
    m.box((9.1, -0.8, 7.5), (10.1, 1.2, 8.5), 'steel', ('z', 22.5, (9.6, 1.0, 8)))
    m.cyl('z', 5.5, 10.6, 5.2, 4.8, 0.5, 'aluminum')
    m.cyl('z', 5.5, 10.6, 10.6, 4.8, 0.5, 'aluminum')
    m.box((6.4, 5.4, 8.8), (9.0, 5.8, 9.2), 'spring')
    m.box((12.6, 4.0, 7.6), (13.0, 7.5, 8.4), 'spring')


@item('crosse_polymere', 'Crosse polymère', 'Polymer Stock', 'arme',
      "Crosse réglable couleur sable.", 'Adjustable stock, desert tan.',
      'uncommon', 1, 0.85, FRONT)
def _(m):
    m.cyl('x', 6, 16, 8.6, 8, 1.3, 'black')
    m.box((1, 6, 6.5), (9, 10.6, 9.5), 'tan')
    m.box((1, 10.6, 7.0), (8, 11.6, 9.0), 'tan')
    m.box((1, 2.2, 6.5), (4.2, 6, 9.5), 'tan')
    m.obox((4.6, 5.0, 8), (4.4, 2.2, 3.0), 'tan', 'z', 45)
    m.box((0, 2.0, 6.3), (1, 11.6, 9.7), 'rubber')
    m.box((6, 5.2, 7.4), (8.4, 6.0, 8.6), 'black')
    m.box((2, 6.6, 9.5), (8, 7.0, 9.6), 'olive')


@item('crosse_bois', 'Crosse en bois', 'Wooden Stock', 'arme',
      "Crosse en noyer récupérée sur un vieux fusil.", 'Walnut stock salvaged from an old rifle.',
      'uncommon', 1, 0.9, FRONT)
def _(m):
    m.box((0, 2, 6.4), (1, 10.2, 9.6), 'black')
    m.box((1, 2, 6.6), (6, 10, 9.4), 'wood')
    m.box((6, 3.2, 6.7), (9, 9.8, 9.3), 'wood')
    m.box((9, 4.4, 6.8), (12, 9.5, 9.2), 'wood')
    m.box((12, 5.4, 6.9), (16, 9.0, 9.1), 'wood')
    m.box((1, 10, 7.0), (7, 10.8, 9.0), 'wood')
    m.box((4.6, 1.2, 7.6), (5.6, 2.0, 8.4), 'steel')


@item('poignee_pistolet', 'Poignée pistolet', 'Pistol Grip', 'arme',
      "Poignée texturée pour fusil d'assaut.", 'Textured grip for an assault rifle.',
      'common', 4, 0.6, FRONT)
def _(m):
    piv = (7, 6.5, 8)
    m.box((5, 2, 6.6), (9, 11, 9.4), 'polymer_grip', ('z', -22.5, piv))
    for y in (3.4, 5.4, 7.4):
        m.box((9, y, 6.8), (9.5, y + 1.0, 9.2), 'polymer', ('z', -22.5, piv))
    m.box((4.8, 1.4, 6.4), (9.2, 2.0, 9.6), 'black', ('z', -22.5, piv))
    m.box((6.4, 10, 6.6), (12.4, 11.6, 9.4), 'polymer')
    m.box((4, 10.2, 7.0), (6.6, 11.2, 9.0), 'polymer')


@item('garde_main', 'Garde-main', 'Handguard', 'arme',
      "Garde-main à fentes M-LOK et rail supérieur.", 'M-LOK handguard with top rail.',
      'uncommon', 2, 0.95, DIAG_STEEP)
def _(m):
    m.cyl('x', 0, 16, 8, 8, 2.6, 'black')
    for x in (2, 6, 10):
        m.box((x, 7.3, 10.58), (x + 2.6, 8.7, 10.64), 'chip')
    m.box((0, 10.4, 7.0), (16, 11.0, 9.0), 'black')
    for i in range(16):
        m.box((i + 0.15, 11.0, 7.1), (i + 0.65, 11.5, 8.9), 'gunmetal')


@item('rail_picatinny', 'Rail Picatinny', 'Picatinny Rail', 'arme',
      "Rail de montage pour optiques et accessoires.", 'Mounting rail for optics and accessories.',
      'common', 8, 0.55, TOP)
def _(m):
    m.box((1, 6.4, 6), (15, 7.8, 10), 'black')
    m.box((1, 7.8, 6.6), (15, 8.6, 9.4), 'black')
    for i in range(10):
        x = 1.4 + i * 1.4
        m.box((x, 8.6, 6.2), (x + 0.7, 9.6, 9.8), 'gunmetal')
    m.cyl('y', 6.2, 7.85, 3.5, 8, 0.6, 'steel')
    m.cyl('y', 6.2, 7.85, 12.5, 8, 0.6, 'steel')


@item('cache_flamme', 'Cache-flammes', 'Flash Hider', 'arme',
      "Masque le départ du coup la nuit.", 'Hides the muzzle flash at night.',
      'uncommon', 4, 0.5, DIAG)
def _(m):
    m.cyl('x', 0.6, 2.4, 8, 8, 1.05, 'thread')
    m.cyl('x', 2.4, 6.4, 8, 8, 1.9, 'black')
    for (dy, dz) in ((1.3, 0), (-1.3, 0), (0, 1.3), (0, -1.3)):
        m.obox((10.2, 8 + dy, 8 + dz), (7.6, 0.8 if dy else 0.95, 0.95 if dy else 0.8), 'black', 'x', 45, (10.2, 8, 8))
    m.box((6.4, 7.45, 7.45), (14, 8.55, 8.55), 'chip')


@item('tube_silencieux', 'Tube de silencieux', 'Suppressor Tube', 'arme',
      "Silencieux usiné, chicanes intactes.", 'Machined suppressor, baffles intact.',
      'epic', 1, 0.75, DIAG)
def _(m):
    m.cyl('x', 0, 1.4, 8, 8, 1.2, 'thread')
    m.cyl('x', 1.2, 2.2, 8, 8, 2.35, 'gunmetal')
    m.cyl('x', 2.2, 15, 8, 8, 2.5, 'black')
    m.cyl('x', 7.0, 7.5, 8, 8, 2.6, 'gunmetal')
    m.cyl('x', 15, 15.8, 8, 8, 2.3, 'gunmetal')
    m.box((15.78, 7.4, 7.4), (15.85, 8.6, 8.6), 'chip')


@item('silencieux_artisanal', 'Silencieux artisanal', 'Improvised Suppressor', 'arme',
      "Bouteille et ruban adhésif. Ça tiendra… un peu.", 'Bottle and duct tape. It will hold... a bit.',
      'uncommon', 1, 0.8, DIAG)
def _(m):
    m.cyl('x', 3, 12.4, 8, 8, 2.7, 'bottle')
    m.cyl('x', 12.4, 13.6, 8, 8, 1.9, 'bottle')
    m.cyl('x', 13.6, 15.4, 8, 8, 1.1, 'white')
    m.cyl('x', 5.2, 10, 8, 8, 2.78, 'red')
    m.cyl('x', 0.6, 3.6, 8, 8, 2.85, 'tape')
    m.cyl('x', 11, 12, 8, 8, 2.8, 'tape')


@item('chargeur_vide', 'Chargeur vide', 'Empty Magazine', 'arme',
      "Chargeur 30 coups. Vide, évidemment.", '30-round magazine. Empty, of course.',
      'uncommon', 4, 0.7, FRONT)
def _(m):
    m.box((5, 9, 6.7), (11, 15, 9.3), 'gunmetal')
    piv = (8, 9.2, 8)
    m.box((5.05, 2.5, 6.7), (10.95, 9.3, 9.3), 'gunmetal', ('z', 22.5, piv))
    m.box((4.6, 1.6, 6.4), (11.4, 2.6, 9.6), 'black', ('z', 22.5, piv))
    for y in (4.2, 6.2):
        m.box((5.6, y, 9.3), (10.4, y + 0.6, 9.42), 'steel_dark', ('z', 22.5, piv))
    m.box((5.6, 10.4, 9.3), (10.4, 11.0, 9.42), 'steel_dark')
    m.box((5.6, 12.6, 9.3), (10.4, 13.2, 9.42), 'steel_dark')
    m.box((5.4, 15, 7.2), (10.6, 15.8, 8.8), 'steel_dark')
    m.box((6.2, 15, 7.0), (10.0, 15.4, 9.0), 'olive')


@item('lentille_optique', 'Lentilles optiques', 'Optical Lenses', 'arme',
      "Lentilles de lunette, sans rayure.", 'Scope lenses, not a scratch.',
      'rare', 8, 0.5, {'roll': 0, 'yaw': -50, 'pitch': 22})
def _(m):
    m.cyl('x', 3.2, 5.4, 8, 8, 4.5, 'black')
    m.cyl('x', 3.0, 5.6, 8, 8, 3.7, 'glass')
    m.cyl('x', 9.4, 11.0, 8, 6.5, 3.0, 'black')
    m.cyl('x', 9.2, 11.2, 8, 6.5, 2.4, 'glass')


@item('corps_lunette', 'Corps de lunette', 'Scope Body', 'arme',
      "Tube de lunette avec tourelles, sans lentilles.", 'Scope tube with turrets, no lenses.',
      'rare', 1, 0.85, DIAG)
def _(m):
    m.cyl('x', 0, 3, 8, 8, 1.8, 'black')
    m.cyl('x', 3, 12.6, 8, 8, 1.25, 'black')
    m.cyl('x', 12.6, 16, 8, 8, 2.3, 'black')
    m.cyl('y', 9, 10.9, 8, 8, 0.95, 'black')
    m.cyl('y', 10.9, 11.4, 8, 8, 0.95, 'steel_dark')
    m.cyl('z', 9, 10.9, 8, 8, 0.95, 'black')
    m.cyl('x', 15.95, 16.05, 8, 8, 1.8, 'glass')
    m.cyl('x', -0.05, 0.05, 8, 8, 1.3, 'glass')
    for x in (4.4, 12.0):
        m.box((x, 5.4, 7.0), (x + 1, 6.8, 9.0), 'gunmetal')


@item('kit_goupilles', 'Kit de goupilles', 'Pin Kit', 'arme',
      "Goupilles et axes de rechange.", 'Spare pins and axles.',
      'common', 16, 0.45, TOP)
def _(m):
    m.cyl('x', 2, 14, 8, 4.8, 0.7, 'steel')
    m.cyl('x', 1.4, 2, 8, 4.8, 1.05, 'steel')
    m.cyl('x', 3, 13, 8, 8, 0.75, 'black')
    m.box((3, 8.6, 7.9), (13, 8.78, 8.1), 'chip')
    m.cyl('x', 4.5, 11.5, 8, 11.2, 0.6, 'steel')
    m.cyl('x', 11.5, 12.1, 8, 11.2, 0.95, 'steel')


@item('visserie', 'Visserie', 'Nuts & Bolts', 'meca',
      "Boulons, écrous et rondelles.", 'Bolts, nuts and washers.',
      'common', 64, 0.5, DIAG)
def _(m):
    m.cyl('x', 1, 3, 8, 8, 2.2, 'zinc')
    m.cyl('x', 3, 15, 8, 8, 1.0, 'thread')
    m.cyl('x', 8.6, 9.0, 8, 8, 2.4, 'zinc')
    m.cyl('x', 9.0, 11.0, 8, 8, 2.0, 'zinc')


@item('huile_arme', "Huile pour arme", 'Gun Oil', 'arme',
      "Quelques gouttes et ça repart.", 'A few drops and it runs again.',
      'common', 8, 0.5, FRONT)
def _(m):
    m.cyl('y', 0, 10, 8, 8, 2.6, 'oil')
    m.cyl('y', 3, 9, 8, 8, 2.68, 'label_black')
    m.cyl('y', 10, 11, 8, 8, 1.8, 'oil')
    m.cyl('y', 11, 12.2, 8, 8, 1.2, 'red')
    m.cyl('y', 12.2, 15, 8, 8, 0.45, 'red')

# =============================================================== MUNITIONS


@item('douilles_vides', 'Douilles vides', 'Empty Casings', 'munition',
      "Laiton récupéré, prêt à être rechargé.", 'Recovered brass, ready to reload.',
      'common', 64, 0.45, TOP)
def _(m):
    for (x, z) in ((5, 6), (10, 7.5), (6.8, 11)):
        m.cyl('y', 0, 0.7, x, z, 1.2, 'brass')
        m.cyl('y', 0.7, 1.1, x, z, 0.9, 'brass')
        m.cyl('y', 1.1, 9, x, z, 1.15, 'brass')
        m.cyl('y', 9, 9.6, x, z, 0.95, 'brass')
        m.cyl('y', 9.6, 11.5, x, z, 0.75, 'brass')
        m.box((x - 0.35, 11.5, z - 0.35), (x + 0.35, 11.55, z + 0.35), 'chip')


@item('poudre_propulsive', 'Poudre propulsive', 'Gunpowder', 'munition',
      "Boîte de poudre. Loin du feu.", 'Powder tin. Keep away from fire.',
      'uncommon', 16, 0.55, FRONT)
def _(m):
    m.cyl('y', 1, 12, 8, 8, 4.0, 'label_green')
    m.cyl('y', 0, 1, 8, 8, 4.15, 'steel')
    m.cyl('y', 12, 13, 8, 8, 4.15, 'steel')
    m.cyl('y', 13, 13.7, 8, 8, 1.6, 'steel_dark')


@item('amorces', 'Amorces', 'Primers', 'munition',
      "Boîte d'amorces pour le rechargement.", 'Box of primers for reloading.',
      'uncommon', 32, 0.5, TOP)
def _(m):
    m.box((2, 4, 3), (14, 7.4, 13), 'label_red')
    m.box((1.8, 7.0, 2.8), (14.2, 8.2, 13.2), 'red')
    for (x, z) in ((5, 6), (7.4, 9.6), (11, 7)):
        m.cyl('y', 8.2, 8.7, x, z, 0.75, 'brass')
        m.box((x - 0.25, 8.7, z - 0.25), (x + 0.25, 8.75, z + 0.25), 'steel_dark')


@item('ogives', 'Ogives', 'Bullet Heads', 'munition',
      "Têtes de balles à sertir.", 'Bullet heads to crimp.',
      'common', 64, 0.4, TOP)
def _(m):
    for (x, z, mat) in ((5, 6, 'copper'), (10, 7.5, 'copper'), (6.8, 11, 'lead')):
        m.cyl('y', 0, 5, x, z, 1.2, mat)
        m.cyl('y', 5, 6.6, x, z, 1.0, mat)
        m.cyl('y', 6.6, 7.7, x, z, 0.75, mat)
        m.cyl('y', 7.7, 8.4, x, z, 0.45, mat)


@item('lingot_plomb', 'Lingot de plomb', 'Lead Ingot', 'munition',
      "Plomb fondu pour couler des balles.", 'Melted lead to cast bullets.',
      'common', 16, 0.6, TOP)
def _(m):
    m.box((1, 4, 3), (15, 6, 13), 'lead')
    m.box((1.6, 6, 3.6), (14.4, 7.6, 12.4), 'lead')
    m.box((2.2, 7.6, 4.2), (13.8, 8.6, 11.8), 'lead')
    m.box((6, 8.6, 6.8), (10, 8.68, 9.2), 'steel_dark')

# =============================================================== MECANIQUE


@item('ressort_compression', 'Ressort de compression', 'Compression Spring', 'meca',
      "Gros ressort zingué, très raide.", 'Heavy zinc-plated spring, very stiff.',
      'common', 16, 0.5, FRONT)
def _(m):
    m.helix(3, 13, 8, 8, 4.2, 1.1, 5, 'zinc', n=16)


@item('ressort_traction', 'Ressort de traction', 'Extension Spring', 'meca',
      "Ressort à crochets, garde sa tension.", 'Hooked spring, keeps its tension.',
      'common', 16, 0.5, DIAG)
def _(m):
    m.helix(3, 13, 8, 8, 1.9, 0.8, 11, 'spring')
    for (x0, s) in ((3, -1), (13, 1)):
        xa, xb = (x0 + s * 2.6, x0 + s * 1.9)
        m.box((min(x0, xb), 9.4, 7.6), (max(x0, xb), 10.1, 8.4), 'spring')
        m.box((min(xa, xb), 10.1, 7.6), (max(xa, xb), 12.8, 8.4), 'spring')
        m.box((min(xa, x0 + s * 0.8), 12.2, 7.6), (max(xa, x0 + s * 0.8), 12.9, 8.4), 'spring')


@item('engrenages', 'Engrenages', 'Gears', 'meca',
      "Pignons en laiton et en acier.", 'Brass and steel cogs.',
      'common', 32, 0.55, {'roll': 0, 'yaw': -22, 'pitch': 18})
def _(m):
    cx, cy = 7, 9
    m.cyl('z', 7, 9, cx, cy, 5.0, 'brass')
    for k in range(16):
        import math
        a = k * 22.5
        r = 5.5
        m.obox((cx + r * math.cos(math.radians(a)), cy + r * math.sin(math.radians(a)), 8),
               (1.4, 1.15, 2.0), 'brass', 'z', a)
    m.cyl('z', 6.5, 9.5, cx, cy, 1.6, 'steel')
    m.box((cx - 0.45, cy - 0.45, 9.5), (cx + 0.45, cy + 0.45, 9.55), 'chip')
    for k in range(4):
        import math
        a = math.radians(45 + 90 * k)
        x, y = cx + 3.2 * math.cos(a), cy + 3.2 * math.sin(a)
        m.box((x - 0.6, y - 0.6, 9.0), (x + 0.6, y + 0.6, 9.04), 'chip')
    gx, gy = 13.2, 3.4
    m.cyl('z', 5.4, 7.0, gx, gy, 2.3, 'steel')
    for k in range(8):
        import math
        a = k * 45 + 22.5
        m.obox((gx + 2.7 * math.cos(math.radians(a)), gy + 2.7 * math.sin(math.radians(a)), 6.2),
               (1.1, 0.95, 1.6), 'steel', 'z', a)
    m.box((gx - 0.4, gy - 0.4, 7.0), (gx + 0.4, gy + 0.4, 7.04), 'chip')


@item('roulement', 'Roulement à billes', 'Ball Bearing', 'meca',
      "Roulement graissé, tourne sans bruit.", 'Greased bearing, spins silently.',
      'uncommon', 16, 0.45, {'roll': 0, 'yaw': -30, 'pitch': 24})
def _(m):
    import math
    m.ring('z', 6.6, 9.4, 8, 8, 6.0, 1.3, 'steel')
    m.ring('z', 6.6, 9.4, 8, 8, 3.0, 1.1, 'steel')
    for k in range(10):
        a = math.radians(k * 36)
        x, y = 8 + 3.95 * math.cos(a), 8 + 3.95 * math.sin(a)
        m.box((x - 0.55, y - 0.55, 7.45), (x + 0.55, y + 0.55, 8.55), 'steel_dark')


@item('tuyau_acier', "Tuyau d'acier", 'Steel Pipe', 'mat',
      "Tuyau galvanisé fileté. Base de canon artisanal.", 'Threaded galvanized pipe. Makeshift barrel base.',
      'common', 16, 0.85, DIAG_STEEP)
def _(m):
    m.ring('x', 1.2, 13.8, 8, 8, 2.4, 0.75, 'zinc')
    m.ring('x', 13.6, 16, 8, 8, 2.8, 0.9, 'steel_dark')
    m.ring('x', 0, 1.4, 8, 8, 2.2, 0.7, 'thread')


@item('plaque_acier', "Plaque d'acier", 'Steel Plate', 'mat',
      "Tôle épaisse, bonne pour blinder.", 'Thick sheet, good for armor.',
      'common', 16, 0.8, TOP)
def _(m):
    m.box((1, 7.4, 2), (15, 8.6, 14), 'plate')
    for (x, z) in ((2.4, 3.4), (13.6, 3.4), (2.4, 12.6), (13.6, 12.6)):
        m.box((x - 0.5, 8.6, z - 0.5), (x + 0.5, 8.64, z + 0.5), 'chip')


@item('ferraille', 'Ferraille', 'Scrap Metal', 'mat',
      "Un peu de tout, beaucoup de rouille.", 'A bit of everything, lots of rust.',
      'common', 64, 0.7, TOP)
def _(m):
    m.box((1, 3, 3), (12, 4, 13), 'rusty_steel', ('z', -22.5, (6.5, 3.5, 8)))
    m.box((3, 5.4, 4.8), (15, 7.6, 7.2), 'rust', ('y', 22.5, (9, 6.5, 6)))
    m.box((3.5, 5.0, 5.4), (14.5, 8.0, 6.6), 'rust', ('y', 22.5, (9, 6.5, 6)))
    m.box((2, 4.2, 10), (11, 5.2, 11.2), 'steel_dark', ('y', -45, (6.5, 4.7, 10.6)))
    m.box((9, 3.6, 8.4), (13, 6.4, 12.4), 'rust')
    m.box((4.5, 4.0, 8.0), (7.5, 5.6, 10.2), 'zinc', ('y', 22.5, (6, 4.8, 9.1)))


@item('ruban_adhesif', 'Ruban adhésif', 'Duct Tape', 'mat',
      "Répare tout. Presque.", 'Fixes everything. Almost.',
      'common', 16, 0.5, {'roll': 0, 'yaw': -32, 'pitch': 20})
def _(m):
    m.ring('z', 5.6, 10.4, 8, 8.6, 5.4, 2.4, 'tape')
    m.ring('z', 5.8, 10.2, 8, 8.6, 3.0, 0.6, 'cardboard')
    m.box((8, 2.9, 5.6), (13.4, 3.4, 10.4), 'tape', ('z', -22.5, (8, 3.2, 8)))


@item('colle_forte', 'Colle forte', 'Super Glue', 'mat',
      "Colle instantanée, ne pas toucher.", 'Instant glue, do not touch.',
      'common', 16, 0.4, DIAG)
def _(m):
    m.box((1, 6.0, 7.6), (3.2, 10.0, 8.4), 'aluminum')
    m.cyl('x', 3, 11, 8, 8, 1.6, 'label_red')
    m.cyl('x', 11, 12, 8, 8, 1.15, 'aluminum')
    m.cyl('x', 12, 12.8, 8, 8, 0.6, 'aluminum')
    m.cyl('x', 12.4, 15.4, 8, 8, 0.95, 'red')


@item('chambre_a_air', 'Chambre à air', 'Inner Tube', 'mat',
      "Caoutchouc souple : joints, élastiques, frondes.", 'Soft rubber: seals, bands, slingshots.',
      'common', 16, 0.6, {'roll': 0, 'yaw': -30, 'pitch': 26})
def _(m):
    m.ring('z', 6.6, 9.4, 8, 7.4, 6.2, 2.0, 'rubber')
    m.cyl('y', 13.4, 15.2, 8, 8, 0.45, 'brass')

# =============================================================== ELECTRONIQUE


@item('circuit_imprime', 'Circuit imprimé', 'Circuit Board', 'elec',
      "Carte électronique récupérée sur une radio.", 'Board salvaged from a radio.',
      'uncommon', 16, 0.6, TOP)
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


@item('bobine_cuivre', 'Bobine de cuivre', 'Copper Wire Spool', 'elec',
      "Fil de cuivre émaillé. Vaut de l'or.", 'Enamelled copper wire. Worth gold.',
      'uncommon', 16, 0.55, {'roll': 0, 'yaw': -38, 'pitch': 26})
def _(m):
    m.cyl('x', 1, 2, 8, 8, 4.2, 'polymer')
    m.cyl('x', 14, 15, 8, 8, 4.2, 'polymer')
    m.cyl('x', 2, 14, 8, 8, 3.2, 'copper_wire')
    m.box((0.95, 7.3, 7.3), (1.0, 8.7, 8.7), 'chip')
    m.box((15.0, 7.3, 7.3), (15.05, 8.7, 8.7), 'chip')
    m.box((11, 11.0, 7.8), (16, 11.4, 8.2), 'copper', ('z', 22.5, (11, 11.2, 8)))


@item('batterie', 'Batterie', 'Battery', 'elec',
      "Batterie de voiture, encore un peu de jus.", 'Car battery, still some juice.',
      'rare', 4, 0.6, {'roll': 0, 'yaw': -32, 'pitch': 26})
def _(m):
    m.box((1, 2, 4), (15, 11, 12), 'polymer')
    m.box((0.8, 11, 3.8), (15.2, 12, 12.2), 'polymer')
    m.box((3, 4, 12.0), (13, 9, 12.06), 'label_yellow')
    m.cyl('y', 12, 12.6, 3.5, 6.2, 1.3, 'red')
    m.cyl('y', 12.6, 13.6, 3.5, 6.2, 0.75, 'lead')
    m.cyl('y', 12, 13.6, 12.5, 6.2, 0.75, 'lead')
    m.box((6, 12, 9.2), (10, 13.2, 10.2), 'black')


@item('moteur_electrique', 'Moteur électrique', 'Electric Motor', 'elec',
      "Petit moteur à courant continu.", 'Small DC motor.',
      'rare', 8, 0.5, DIAG)
def _(m):
    m.cyl('x', 3, 13, 8, 8, 3.0, 'aluminum')
    m.cyl('x', 1.6, 3, 8, 8, 2.6, 'black')
    m.box((0.6, 9.2, 7.6), (1.6, 10.2, 8.4), 'brass')
    m.box((0.6, 5.8, 7.6), (1.6, 6.8, 8.4), 'brass')
    m.cyl('x', 13, 13.8, 8, 8, 1.0, 'brass')
    m.cyl('x', 13.8, 16, 8, 8, 0.45, 'steel')
    m.box((6, 7.4, 10.98), (10, 8.6, 11.04), 'chip')
    m.box((6, 10.98, 7.4), (10, 11.04, 8.6), 'chip')
