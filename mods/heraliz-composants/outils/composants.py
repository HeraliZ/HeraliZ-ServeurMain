"""Catalogue des composants HeraliZ.
Modeles de profil : X = longueur, Y = hauteur, Z = epaisseur (centree sur 8)."""
import math

ITEMS = []

# categorie -> (fr, en)
CATEGORIES = {
    'armes': ('Armes', 'Weapons'),
    'soins': ('Soins', 'Healing'),
    'viseurs': ('Viseurs', 'Scopes'),
    'munitions': ('Munitions', 'Ammo'),
    'protection': ('Protection', 'Protection'),
    'divers': ('Divers', 'Misc'),
    'vente': ('À vendre', 'To sell'),
    'construction': ('Construction', 'Construction'),
    'barricades': ('Barricades', 'Barricades'),
}

DIAG = {'roll': 32, 'yaw': -25, 'pitch': 25}
DIAG_STEEP = {'roll': 40, 'yaw': -22, 'pitch': 22}
TOP = {'roll': 0, 'yaw': -35, 'pitch': 38}
FRONT = {'roll': 0, 'yaw': -28, 'pitch': 20}


# Pose en 1re personne selon la forme de l'objet (verifiee avec les captures en jeu) :
#  long    : objet allonge sur X, tenu comme un outil vanilla
#  flat    : objet pose a plat (grande face vers +Y) -> on le releve vers la camera
#  upright : objet debout (face avant vers +Z) -> on tourne sa face vers la camera
FP_POSES = {
    'long': {'rotation': [0, -90, 25], 'translation': [1.13, 3.6, 1.13], 'k': 1.25},
    'flat': {'rotation': [55, 0, 33], 'translation': [-1, 6.5, 0], 'k': 1.5},
    'upright': {'rotation': [-25, -30, 0], 'translation': [-1, 6.5, 0], 'k': 1.55},
}


def item(id, fr, en, cats, desc_fr, desc_en, hs, view, stack=32, rarity='common', fp=None, pose=None, tp=None):
    """fp : tenue en 1re personne ; tp : tenue vue par les autres joueurs (3e personne)."""
    if pose is None:
        pose = 'flat' if view is TOP else ('long' if view.get('roll', 0) else 'upright')
    fp = fp or FP_POSES[pose]

    def deco(fn):
        ITEMS.append(dict(id=id, fr=fr, en=en, cats=cats, desc_fr=desc_fr, desc_en=desc_en,
                          hs=hs, view=view, stack=stack, rarity=rarity, fp=fp, tp=tp, build=fn))
        return fn
    return deco


# ================================================================ ARMES

@item('canon', 'Canon', 'Barrel', ['armes'],
      "Canon d'arme récupéré, rayures encore nettes.", 'Salvaged gun barrel, rifling still sharp.',
      0.85, {'roll': 40, 'yaw': -16, 'pitch': 20}, 16, 'uncommon')
def _(m):
    # chambre + epaulement
    m.cyl('x', 0, 3.6, 8, 8, 2.3, 'steel_dark')
    m.cyl('x', 3.6, 4.4, 8, 8, 1.9, 'steel_dark')
    m.box((0.6, 10.2, 7.3), (2.8, 10.36, 8.7), 'chip')
    # tube du canon
    m.cyl('x', 4.4, 14.4, 8, 8, 1.55, 'steel')
    m.cyl('x', 7.0, 7.5, 8, 8, 1.62, 'steel_dark')
    # bloc guidon
    m.box((10.4, 6.3, 6.5), (12.0, 10.0, 9.5), 'gunmetal')
    m.box((10.9, 10.0, 7.6), (11.5, 11.2, 8.4), 'gunmetal')
    # cache-flammes
    m.cyl('x', 14.4, 16.2, 8, 8, 1.75, 'gunmetal')
    for (y1, y2, z1, z2) in ((9.7, 9.8, 7.6, 8.4), (7.6, 8.4, 9.7, 9.8), (7.6, 8.4, 6.2, 6.3)):
        m.box((14.8, y1, z1), (15.9, y2, z2), 'chip')
    # ames
    m.box((16.2, 7.3, 7.3), (16.26, 8.7, 8.7), 'chip')
    m.box((-0.06, 7.1, 7.1), (0.0, 8.9, 8.9), 'chip')


@item('ressort', 'Ressort', 'Spring', ['armes'],
      'Ressort en acier, encore bien raide.', 'Steel spring, still nice and stiff.',
      0.5, {'roll': 18, 'yaw': -30, 'pitch': 22}, 32, 'uncommon')
def _(m):
    m.helix(2, 14, 8, 8, 3.4, 1.1, 5.5, 'steel', n=16)


@item('kit_pieces', 'Kit de pièces', 'Parts Kit', ['armes'],
      "Boîte de petites pièces d'armes : vis, goupilles, ressorts.", 'Box of small gun parts: screws, pins, springs.',
      0.6, {'roll': 0, 'yaw': -32, 'pitch': 42}, 16, 'uncommon',
      fp={'rotation': [40, -90, 0], 'translation': [1.13, 4.2, 1.13]})
def _(m):
    # boite ouverte vert militaire
    m.box((1, 0, 3), (15, 0.8, 13), 'olive')
    m.box((1, 0.8, 3), (15, 4, 3.6), 'olive')
    m.box((1, 0.8, 12.4), (15, 4, 13), 'olive')
    m.box((1, 0.8, 3.6), (1.6, 4, 12.4), 'olive')
    m.box((14.4, 0.8, 3.6), (15, 4, 12.4), 'olive')
    m.box((7.7, 0.8, 3.6), (8.3, 3.2, 12.4), 'olive')
    m.box((1, 4.0, 0.4), (15, 4.6, 3.4), 'olive', ('x', 45, (8, 4.3, 3.3)))
    m.box((5.4, 3.2, 12.95), (10.6, 3.96, 13.05), 'yellow')
    # contenu
    m.helix(2.4, 7.0, 2.4, 6.0, 1.0, 0.45, 5, 'steel')
    m.cyl('x', 2.4, 7.0, 1.6, 10.0, 0.5, 'steel')
    m.cyl('x', 2.0, 2.6, 1.6, 10.0, 0.8, 'steel')
    m.box((3.0, 0.8, 7.6), (6.4, 2.6, 9.2), 'black')
    for (x, z) in ((9.6, 5.2), (11.6, 6.4), (13.0, 4.8), (10.4, 7.8)):
        m.cyl('y', 0.8, 1.5, x, z, 0.75, 'zinc')
        m.box((x - 0.3, 1.5, z - 0.3), (x + 0.3, 1.55, z + 0.3), 'chip')
    m.box((9.0, 0.8, 9.4), (13.6, 1.8, 11.6), 'gunmetal')
    m.cyl('x', 9.4, 13.2, 2.1, 10.5, 0.35, 'brass')


# ================================================================ SOINS

@item('tissu', 'Tissu', 'Cloth', ['soins', 'protection'],
      'Tissu propre, de quoi faire un bandage.', 'Clean cloth, enough for a bandage.',
      0.55, TOP, 32, 'uncommon')
def _(m):
    m.box((1.6, 0, 3.0), (14.4, 1.2, 13.0), 'cloth')
    m.box((2.2, 1.2, 3.4), (14.0, 2.4, 12.6), 'cloth', ('y', -22.5, (8, 1.8, 8)))
    m.box((2.8, 2.4, 4.0), (13.4, 3.4, 12.0), 'cloth')
    m.cyl('z', 4.0, 12.0, 13.4, 2.9, 0.5, 'cloth')
    m.box((2.8, 3.4, 7.2), (13.4, 3.44, 8.4), 'red')


@item('alcool', 'Alcool', 'Rubbing Alcohol', ['soins'],
      'Alcool à 70 %. Désinfecte, et ça pique.', '70% alcohol. Disinfects, and it stings.',
      0.5, {'roll': 0, 'yaw': -24, 'pitch': 16}, 16, 'uncommon')
def _(m):
    # flacon plat transparent : liquide en bas, vide en haut
    m.box((3.6, 0, 5.8), (12.4, 7.4, 10.2), 'alcohol')
    m.box((3.6, 7.4, 5.8), (12.4, 9.4, 10.2), 'jar_glass')
    m.box((4.6, 9.4, 6.4), (11.4, 10.4, 9.6), 'jar_glass')
    m.box((6.2, 10.4, 7.0), (9.8, 11.0, 9.0), 'jar_glass')
    m.cyl('y', 11.0, 11.8, 8, 8, 1.0, 'jar_glass')
    m.cyl('y', 11.8, 14.0, 8, 8, 1.35, 'white')
    # etiquette blanche a croix rouge
    m.box((4.4, 1.6, 10.2), (11.6, 7.8, 10.26), 'white')
    m.box((7.4, 2.6, 10.26), (8.6, 6.8, 10.3), 'red')
    m.box((5.9, 4.1, 10.26), (10.1, 5.3, 10.3), 'red')


# ================================================================ VISEURS

@item('batterie', 'Batterie', 'Battery', ['viseurs'],
      'Alimente lunettes, lasers et viseurs point rouge.', 'Powers scopes, lasers and red dots.',
      0.45, FRONT, 16, 'uncommon')
def _(m):
    for z in (6.4, 9.6):
        m.cyl('y', 1, 11, 8, z, 1.55, 'cell_blue')
        m.cyl('y', 11, 11.6, 8, z, 1.4, 'aluminum')
        m.cyl('y', 11.6, 12.2, 8, z, 0.55, 'aluminum')
        m.cyl('y', 0.6, 1, 8, z, 1.4, 'aluminum')
    m.box((6.2, 4.6, 5.0), (9.8, 7.4, 11.0), 'tape')


@item('circuit_imprime', 'Circuit imprimé', 'Circuit Board', ['viseurs'],
      "L'électronique d'un viseur, récupérée sur une radio.", 'Scope electronics, salvaged from a radio.',
      0.6, TOP, 16, 'uncommon')
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


@item('ferraille', 'Ferraille', 'Scrap Metal', ['viseurs', 'munitions'],
      'Un peu de tout, beaucoup de rouille.', 'A bit of everything, lots of rust.',
      0.7, TOP, 64, 'uncommon')
def _(m):
    m.box((1, 3, 3), (12, 4, 13), 'rusty_steel', ('z', -22.5, (6.5, 3.5, 8)))
    m.box((3, 5.4, 4.8), (15, 7.6, 7.2), 'rust', ('y', 22.5, (9, 6.5, 6)))
    m.box((3.5, 5.0, 5.4), (14.5, 8.0, 6.6), 'rust', ('y', 22.5, (9, 6.5, 6)))
    m.box((2, 4.2, 10), (11, 5.2, 11.2), 'steel_dark', ('y', -45, (6.5, 4.7, 10.6)))
    m.box((9, 3.6, 8.4), (13, 6.4, 12.4), 'rust')
    m.box((4.5, 4.0, 8.0), (7.5, 5.6, 10.2), 'zinc', ('y', 22.5, (6, 4.8, 9.1)))


# ================================================================ MUNITIONS

@item('bocal_poudre', 'Bocal de poudre', 'Gunpowder Jar', ['munitions'],
      'Un bocal rempli de poudre noire.', 'A jar full of black powder.',
      0.45, {'roll': 0, 'yaw': -28, 'pitch': 22}, 32, 'uncommon')
def _(m):
    # bocal en verre : poudre noire en bas, verre vide en haut, couvercle metal
    m.cyl('y', 0, 0.6, 8, 8, 3.6, 'jar_glass')
    m.cyl('y', 0.6, 7.6, 8, 8, 3.7, 'powder')
    m.cyl('y', 7.6, 9.4, 8, 8, 3.7, 'jar_glass')
    m.cyl('y', 9.4, 10.2, 8, 8, 3.0, 'jar_glass')
    m.cyl('y', 10.2, 12.0, 8, 8, 3.25, 'steel_dark')
    m.cyl('y', 12.0, 12.3, 8, 8, 2.6, 'steel')
    m.box((5.2, 2.6, 11.68), (10.8, 6.8, 11.74), 'white')
    m.box((6.0, 5.2, 11.74), (10.0, 5.8, 11.8), 'black')
    m.box((6.6, 3.6, 11.74), (9.4, 4.1, 11.8), 'black')


@item('tonnelet_poudre', 'Tonnelet de poudre', 'Gunpowder Keg', ['munitions'],
      'Un tonnelet plein de poudre noire. Loin du feu.', 'A keg full of black powder. Keep away from fire.',
      0.5, {'roll': 0, 'yaw': -28, 'pitch': 22}, 16, 'uncommon')
def _(m):
    # tonnelet en bois cercle de metal
    m.cyl('y', 0, 1.6, 8, 8, 4.3, 'wood')
    m.cyl('y', 1.6, 9.4, 8, 8, 4.8, 'wood')
    m.cyl('y', 9.4, 11.0, 8, 8, 4.3, 'wood')
    for y in (0.6, 2.2, 8.2, 9.8):
        m.cyl('y', y, y + 0.7, 8, 8, 4.95 if 1 < y < 9 else 4.45, 'steel_dark')
    m.cyl('y', 11.0, 11.2, 8, 8, 3.9, 'wood_raw')
    m.cyl('y', 11.2, 11.8, 9.4, 8, 0.7, 'black')


# ================================================================ PROTECTION (plaques)

PLATE_VIEW = {'roll': 0, 'yaw': -24, 'pitch': 14}


def plate_shape(m, mat, z1, z2, x1=3.0, x2=13.0, h=13.0):
    """Plaque balistique : rectangle aux coins superieurs coupes en escalier."""
    m.box((x1, 0, z1), (x2, h - 3, z2), mat)
    for k in range(3):
        d = 0.8 * (k + 1)
        m.box((x1 + d, h - 3 + k, z1), (x2 - d, h - 2 + k, z2), mat)


@item('plaque_recyclee', 'Plaque recyclée', 'Recycled Plate', ['protection'],
      'Des bouts de tôle rouillée tenus au scotch. Mieux que rien.',
      'Rusty sheet scraps held with tape. Better than nothing.',
      0.7, PLATE_VIEW, 8, 'uncommon')
def _(m):
    m.box((2.4, 0.2, 7.5), (9.2, 8.0, 8.3), 'rust')
    m.box((7.2, 1.0, 7.9), (13.6, 10.6, 8.7), 'rusty_steel', ('z', -22.5, (10.4, 5.8, 8.3)))
    m.box((3.0, 6.8, 8.2), (10.2, 13.0, 9.0), 'zinc', ('z', 22.5, (6.6, 9.9, 8.6)))
    m.box((1.8, 5.0, 9.0), (14.2, 6.4, 9.25), 'tape')
    m.box((5.4, -0.2, 8.95), (6.8, 12.6, 9.2), 'tape')
    for (x, y) in ((11.6, 2.2), (3.6, 11.2), (12.4, 9.0)):
        m.box((x - 0.35, y - 0.35, 8.7), (x + 0.35, y + 0.35, 9.3), 'steel_dark')


@item('plaque_soudee', 'Plaque soudée', 'Welded Plate', ['protection'],
      "Des chutes d'acier soudées ensemble. Ça commence à tenir.",
      'Steel offcuts welded together. Starting to hold up.',
      0.7, PLATE_VIEW, 8, 'rare')
def _(m):
    plate_shape(m, 'plate', 7.4, 8.6)
    m.box((3.0, 5.0, 8.6), (13.0, 5.6, 8.9), 'weld')
    m.box((8.2, 0.0, 8.6), (8.8, 5.0, 8.9), 'weld')
    m.box((3.4, 9.6, 8.6), (11.6, 10.2, 8.9), 'weld', ('z', 22.5, (7.5, 9.9, 8.75)))
    for (x, y) in ((4.2, 1.2), (11.8, 1.2), (4.2, 7.0), (11.8, 7.0)):
        m.cyl('z', 8.6, 9.0, x, y, 0.45, 'steel_dark')


@item('plaque_metal', 'Plaque en métal', 'Metal Plate', ['protection'],
      "Une vraie plaque d'acier, découpée proprement.", 'A real steel plate, cleanly cut.',
      0.7, PLATE_VIEW, 8, 'epic')
def _(m):
    plate_shape(m, 'plate_clean', 7.4, 8.6)
    plate_shape(m, 'plate_clean', 8.6, 8.75, 3.6, 12.4, 12.4)
    for (x, y) in ((4.4, 1.4), (11.6, 1.4), (4.4, 9.0), (11.6, 9.0)):
        m.cyl('z', 8.75, 9.1, x, y, 0.5, 'steel_dark')
    m.box((6.0, 1.6, 8.75), (10.0, 2.4, 8.8), 'gunmetal')


@item('plaque_renforcee', 'Plaque renforcée', 'Reinforced Plate', ['protection'],
      'Plaque épaisse à revêtement balistique. Le haut de gamme.',
      'Thick plate with ballistic coating. Top of the line.',
      0.7, PLATE_VIEW, 8, 'legendary')
def _(m):
    plate_shape(m, 'gunmetal', 7.0, 9.0)
    plate_shape(m, 'cordura', 9.0, 9.4, 3.5, 12.5, 12.5)
    plate_shape(m, 'cordura', 9.4, 9.7, 5.0, 11.0, 10.5)
    m.box((3.0, -0.04, 9.0), (13.0, 0.5, 9.5), 'gunmetal')
    for (x, y) in ((4.2, 1.2), (8.0, 1.2), (11.8, 1.2), (4.2, 8.8), (11.8, 8.8), (8.0, 11.4)):
        m.cyl('z', 9.4, 9.8, x, y, 0.42, 'steel')


# ================================================================ DIVERS

@item('scotch', 'Scotch', 'Duct Tape', ['protection'],
      'Répare tout. Presque.', 'Fixes everything. Almost.',
      0.5, {'roll': 0, 'yaw': -32, 'pitch': 20}, 32, 'uncommon')
def _(m):
    m.ring('z', 5.6, 10.4, 8, 8.6, 5.4, 2.4, 'tape')
    m.ring('z', 5.8, 10.2, 8, 8.6, 3.0, 0.6, 'cardboard')
    m.box((8, 2.9, 5.6), (13.4, 3.4, 10.4), 'tape', ('z', -22.5, (8, 3.2, 8)))


# ================================================================ PROTECTION, BARRICADES

@item('caoutchouc', 'Caoutchouc', 'Rubber', ['protection'],
      'Un vieux pneu de vélo, bon pour les protections.', 'An old bike tyre, good for protective gear.',
      0.6, {'roll': 0, 'yaw': -30, 'pitch': 26}, 32, 'uncommon')
def _(m):
    m.ring('z', 6.4, 9.6, 8, 8, 7.0, 2.2, 'tire')
    m.ring('z', 6.9, 9.1, 8, 8, 4.9, 0.5, 'steel_dark')
    m.cyl('y', 0.4, 2.2, 8, 8, 0.4, 'brass')


@item('corde', 'Corde', 'Rope', ['barricades'],
      'Quelques mètres de corde effilochée.', 'A few metres of frayed rope.',
      0.55, TOP, 32)
def _(m):
    for i, (r, y) in enumerate(((6.0, 0), (5.2, 1.1), (6.2, 2.2))):
        m.ring('y', y, y + 1.2, 8 + i * 0.3, 8, r, 1.2, 'rope')
    m.box((13.4, 0, 7.4), (16.6, 1.0, 8.6), 'rope', ('y', -22.5, (13.6, 0.5, 8)))


@item('clou', 'Clous', 'Nails', ['construction', 'barricades'],
      'Une poignée de clous rouillés.', 'A handful of rusty nails.',
      0.4, TOP, 64)
def _(m):
    for (z, x1, mat) in ((4.6, 1.0, 'steel'), (8.0, 2.4, 'rusty_steel'), (11.4, 1.6, 'steel')):
        m.cyl('x', x1, x1 + 0.6, 1.2, z, 1.25, mat)
        m.cyl('x', x1 + 0.6, x1 + 11.0, 1.2, z, 0.5, mat)
        m.cyl('x', x1 + 11.0, x1 + 12.0, 1.2, z, 0.25, mat)


@item('plastique', 'Plastique', 'Plastic', ['vente'],
      'Une bouteille en plastique écrasée.', 'A crushed plastic bottle.',
      0.5, DIAG, 64)
def _(m):
    m.cyl('x', 1, 5.4, 8, 8, 2.6, 'plastic')
    m.box((5.0, 6.6, 6.2), (9.6, 9.2, 9.8), 'plastic', ('z', -22.5, (7.3, 7.9, 8)))
    m.cyl('x', 9.2, 12.4, 7.2, 8, 2.4, 'plastic')
    m.cyl('x', 12.4, 13.4, 7.2, 8, 1.4, 'plastic')
    m.cyl('x', 13.4, 15.0, 7.2, 8, 0.95, 'white')


@item('planche', 'Planche', 'Plank', ['vente'],
      'Une planche de récup, encore cloutée.', 'A salvaged plank, nails still in.',
      0.85, DIAG_STEEP, 32)
def _(m):
    m.box((0, 6.6, 5.2), (16, 9.4, 10.8), 'wood_raw')
    m.box((15.2, 6.6, 5.2), (16.4, 9.4, 7.2), 'wood_raw', ('z', -22.5, (15.2, 8, 6.2)))
    for x in (2.0, 13.6):
        for z in (6.4, 9.6):
            m.box((x - 0.4, 9.4, z - 0.4), (x + 0.4, 9.55, z + 0.4), 'steel_dark')
    m.cyl('y', 9.4, 11.6, 13.6, 9.6, 0.3, 'rusty_steel')


@item('collier_casse', 'Collier cassé', 'Broken Necklace', ['vente'],
      'Une chaîne dorée cassée avec son pendentif.', 'A broken golden chain with its pendant.',
      0.55, {'roll': 0, 'yaw': -18, 'pitch': 10}, 16)
def _(m):
    # collier qui pend : brin gauche + bas de la boucle, casse a droite
    def link(x, y, ang, i):
        a = round(ang / 22.5) * 22.5
        size = (1.3, 0.62, 0.36) if i % 2 == 0 else (1.3, 0.36, 0.62)
        m.obox((x, y, 8), size, 'gold', 'z', a)

    i = 0
    for y in (15.2, 14.1, 13.0, 11.9):
        link(3.8, y, 90, i)
        i += 1
    cx, cy, rx, ry = 8.0, 11.0, 4.2, 7.4
    for deg in range(180, 336, 13):
        th = math.radians(deg)
        x, y = cx + rx * math.cos(th), cy + ry * math.sin(th)
        tang = math.degrees(math.atan2(ry * math.cos(th), -rx * math.sin(th)))
        link(x, y, tang, i)
        i += 1
    # pendentif
    m.box((7.6, 2.2, 7.7), (8.4, 3.6, 8.3), 'gold')
    m.cyl('z', 7.5, 8.5, 8.0, 0.9, 1.7, 'gold')
    m.cyl('z', 8.5, 8.8, 8.0, 0.9, 0.9, 'red')


@item('telephone_casse', 'Téléphone cassé', 'Broken Phone', ['vente'],
      'Écran fissuré, plus de batterie, plus de réseau.', 'Cracked screen, no battery, no signal.',
      0.5, {'roll': 0, 'yaw': -20, 'pitch': 10}, 16)
def _(m):
    # smartphone debout, ecran vers +Z, coin superieur droit casse
    m.box((4.6, 0.6, 7.3), (11.4, 14.2, 8.7), 'black')
    m.box((4.6, 14.2, 7.3), (10.0, 15.4, 8.7), 'black')
    m.box((10.0, 14.2, 7.6), (10.8, 14.8, 8.4), 'black', ('z', 45, (10.0, 14.2, 8)))
    m.box((5.1, 1.5, 8.7), (10.9, 13.8, 8.78), 'screen')
    m.box((5.1, 13.8, 8.7), (9.6, 14.6, 8.78), 'screen')
    m.box((7.2, 14.75, 8.7), (8.8, 15.05, 8.8), 'chip')
    m.box((7.4, 0.8, 8.7), (8.6, 1.1, 8.8), 'gunmetal')
    m.box((11.4, 10.0, 7.7), (11.6, 12.4, 8.3), 'steel_dark')
    m.box((5.2, 11.6, 7.05), (7.6, 14.4, 7.3), 'gunmetal')
    m.box((5.6, 12.0, 7.0), (6.4, 12.8, 7.05), 'chip')
    m.box((5.6, 13.2, 7.0), (6.4, 14.0, 7.05), 'chip')


@item('montre_cassee', 'Montre cassée', 'Broken Watch', ['vente'],
      "Verre fêlé, bracelet arraché. L'heure s'est arrêtée.", 'Cracked glass, torn strap. Time stopped.',
      0.5, {'roll': 0, 'yaw': -20, 'pitch': 12}, 16)
def _(m):
    m.box((6.3, 10.4, 7.5), (9.7, 15.6, 8.3), 'leather')
    m.box((6.3, 3.0, 7.5), (9.7, 5.4, 8.3), 'leather')
    m.box((6.3, 0.6, 7.5), (9.7, 3.0, 8.3), 'leather', ('z', 22.5, (6.3, 3.0, 7.9)))
    m.cyl('z', 7.2, 9.0, 8, 8, 3.1, 'steel')
    m.cyl('z', 9.0, 9.1, 8, 8, 2.6, 'white')
    m.box((7.8, 7.8, 9.1), (8.2, 10.0, 9.16), 'black')
    m.box((7.85, 7.8, 9.1), (8.15, 10.4, 9.16), 'black', ('z', -45, (8, 8, 9.13)))
    m.box((6.0, 7.9, 9.16), (10.2, 8.1, 9.2), 'gunmetal', ('z', 22.5, (8, 8, 9.18)))
    m.box((7.0, 6.0, 9.16), (8.9, 6.2, 9.2), 'gunmetal', ('z', -45, (8, 6.6, 9.18)))
    m.box((11.0, 7.5, 7.8), (11.7, 8.5, 8.4), 'steel')


@item('lunettes_cassees', 'Lunettes cassées', 'Broken Glasses', ['vente'],
      'Un verre fendu, une branche tordue.', 'One cracked lens, one bent arm.',
      0.5, {'roll': 0, 'yaw': -30, 'pitch': 14}, 16)
def _(m):
    m.ring('z', 7.6, 8.4, 4.8, 8, 2.8, 0.65, 'black')
    m.ring('z', 7.6, 8.4, 11.2, 8, 2.8, 0.65, 'black')
    m.cyl('z', 7.85, 8.15, 4.8, 8, 2.3, 'jar_glass')
    m.box((9.3, 6.0, 7.85), (12.6, 7.8, 8.15), 'jar_glass')
    m.box((9.6, 7.8, 7.85), (11.0, 9.0, 8.15), 'jar_glass', ('z', 22.5, (10.3, 8.4, 8)))
    m.box((7.0, 8.8, 7.7), (9.0, 9.5, 8.3), 'black')
    m.box((1.6, 8.8, 1.6), (2.3, 9.5, 7.8), 'black')
    m.box((13.7, 8.8, 5.0), (14.4, 9.5, 7.8), 'black')
    m.box((13.7, 8.8, 2.2), (14.4, 9.5, 5.2), 'black', ('y', 45, (14.05, 9.15, 5.1)))


@item('radio_cassee', 'Radio cassée', 'Broken Radio', ['vente'],
      'Vieille radio rouillée, rafistolée au scotch. Plus un son.', 'Old rusty radio, patched with tape. Not a sound.',
      0.6, {'roll': 0, 'yaw': -24, 'pitch': 14}, 8, 'uncommon')
def _(m):
    # boitier delave et rouille
    m.box((2, 1, 5.6), (14, 10, 10.4), 'red_worn')
    for (f, t) in (((2.0, 1.0, 10.4), (4.4, 2.2, 10.46)), ((11.6, 9.0, 10.4), (14.0, 10.0, 10.46)),
                   ((14.0, 1.6, 6.4), (14.06, 4.8, 9.6)), ((6.0, 10.0, 6.0), (9.6, 10.06, 8.4))):
        m.box(f, t, 'rust')
    # haut-parleur creve
    m.box((2.8, 1.8, 10.4), (8.2, 8.8, 10.5), 'grille')
    m.box((3.6, 2.6, 10.5), (5.6, 4.6, 10.52), 'chip')
    m.box((5.4, 2.4, 10.5), (6.4, 3.2, 10.56), 'grille', ('z', 22.5, (5.6, 2.8, 10.53)))
    # cadran fissure, aiguille tordue
    m.box((9.0, 5.8, 10.4), (13.2, 8.8, 10.46), 'screen')
    m.box((10.6, 6.0, 10.46), (10.9, 8.6, 10.5), 'red', ('z', 22.5, (10.75, 6.0, 10.48)))
    # un bouton, l'autre arrache
    m.cyl('z', 10.4, 11.2, 10.0, 3.2, 0.85, 'steel_dark')
    m.cyl('z', 10.4, 10.8, 12.4, 3.2, 0.3, 'steel_dark')
    # poignee cassee
    m.box((2.8, 10.0, 7.6), (3.6, 12.0, 8.4), 'black')
    m.box((2.8, 11.4, 7.6), (9.0, 12.2, 8.4), 'black')
    m.box((12.4, 10.0, 7.6), (13.2, 10.6, 8.4), 'black')
    # antenne cassee et pliee
    m.cyl('y', 10.0, 12.0, 12.8, 6.4, 0.3, 'rusty_steel')
    m.box((12.65, 11.8, 6.25), (12.95, 13.6, 6.55), 'rusty_steel', ('z', -45, (12.8, 11.9, 6.4)))
    # scotch sur le coin
    m.box((1.94, 5.6, 10.4), (3.6, 6.8, 10.56), 'tape')
    m.box((1.88, 5.6, 8.2), (1.94, 6.8, 10.56), 'tape')
    for (x, y) in ((2.5, 9.4), (13.5, 1.6)):
        m.box((x - 0.25, y - 0.25, 10.4), (x + 0.25, y + 0.25, 10.44), 'chip')


@item('ampoule_grillee', 'Ampoule grillée', 'Burnt Bulb', ['vente'],
      'Grillée. Comme le reste du monde.', 'Burnt out. Like the rest of the world.',
      0.5, {'roll': 0, 'yaw': -22, 'pitch': 14}, 16)
def _(m):
    # culot a vis
    m.cyl('y', 0, 0.5, 8, 8, 0.55, 'brass')
    m.cyl('y', 0.5, 1.0, 8, 8, 1.1, 'black')
    for i in range(6):
        y = 1.0 + i * 0.5
        m.cyl('y', y, y + 0.5, 8, 8, 1.6 if i % 2 == 0 else 1.4, 'aluminum')
    m.cyl('y', 4.0, 4.6, 8, 8, 1.7, 'aluminum')
    # verre en forme de poire, noirci en haut
    bulb = ((4.6, 5.4, 1.8), (5.4, 6.2, 2.3), (6.2, 7.0, 2.9), (7.0, 7.8, 3.5), (7.8, 9.0, 3.95),
            (9.0, 10.8, 4.2), (10.8, 12.0, 3.95), (12.0, 12.8, 3.4), (12.8, 13.4, 2.6), (13.4, 13.8, 1.6))
    for (y1, y2, r) in bulb:
        m.cyl('y', y1, y2, 8, 8, r, 'smoke' if y1 >= 12.0 else 'jar_glass')
    # filament casse vu a travers le verre (dessine sur la face avant)
    for (y1, y2, r) in bulb[:6]:
        for x in (7.2, 8.8):
            m.box((x - 0.12, max(y1, 5.0), 8 + r), (x + 0.12, min(y2, 10.0), 8 + r + 0.05), 'filament')
    r = 4.2
    m.box((7.2, 9.8, 8 + r), (7.9, 10.1, 8 + r + 0.05), 'filament')
    m.box((8.4, 9.6, 8 + r), (8.8, 9.9, 8 + r + 0.05), 'filament', ('z', 22.5, (8.6, 9.75, 8 + r)))
    m.box((6.6, 11.0, 8 + 3.95), (8.0, 11.8, 8 + 3.95 + 0.05), 'smoke')


# ================================================================ A VENDRE (objets de valeur)
# peu commun (16) -> rare (8) -> epique (4) -> legendaire (1)

def snap(ang):
    return round(ang / 22.5) * 22.5


def ellipse(cx, cy, rx, ry, d1, d2, step):
    """Points (x, y, angle de la tangente) sur une ellipse, de d1 a d2 degres."""
    out = []
    deg = d1
    while deg <= d2 + 1e-6:
        th = math.radians(deg)
        out.append((cx + rx * math.cos(th), cy + ry * math.sin(th),
                    math.degrees(math.atan2(ry * math.cos(th), -rx * math.sin(th)))))
        deg += step
    return out


def chain(m, pts, mat, z=8.0, size=(1.3, 0.62, 0.36)):
    """Chaine a maillons alternes (comme le collier casse)."""
    for i, (x, y, ang) in enumerate(pts):
        s = size if i % 2 == 0 else (size[0], size[2], size[1])
        m.obox((x, y, z), s, mat, 'z', snap(ang))


def pendant_chain(m, top, rx, ry, mat, size=(1.3, 0.62, 0.36), n=24):
    """Belière posee sur le haut du pendentif (y = top) + chaine fermee qui passe dedans."""
    r = 0.75
    m.ring('x', 7.5, 8.5, top + r, 8, r, 0.35, mat, n=8)
    pts = []
    for k in range(n):
        th = math.radians(-90 + k * 360.0 / n)
        pts.append((8 + rx * math.cos(th), top + r + ry + ry * math.sin(th),
                    math.degrees(math.atan2(ry * math.cos(th), -rx * math.sin(th)))))
    chain(m, pts, mat, size=size)


def ingot(m, x1, x2, z1, z2, h, mat, t=0.7):
    """Lingot coule : dessus plus petit que la base, flancs inclines a 22.5 degres."""
    d = h * math.tan(math.radians(22.5))
    lift = t * math.sin(math.radians(22.5))  # pour que le flanc ne passe pas sous la base
    L = (h - lift) / math.cos(math.radians(22.5))
    m.box((x1 + d, 0, z1 + d), (x2 - d, h, z2 - d), mat)
    for f in (0.25, 0.5, 0.75):  # remplissage en escalier sous les flancs
        m.box((x1 + d * f, 0, z1 + d * f), (x2 - d * f, h * f, z2 - d * f), mat)
    m.box((x1 + d, lift, z2 - t), (x2 - d, lift + L, z2), mat, ('x', -22.5, (8, lift, z2)))
    m.box((x1 + d, lift, z1), (x2 - d, lift + L, z1 + t), mat, ('x', 22.5, (8, lift, z1)))
    m.box((x2 - t, lift, z1 + d), (x2, lift + L, z2 - d), mat, ('z', 22.5, (x2, lift, 8)))
    m.box((x1, lift, z1 + d), (x1 + t, lift + L, z2 - d), mat, ('z', -22.5, (x1, lift, 8)))
    return x1 + d, x2 - d, z1 + d, z2 - d


def around_y(m, R, phi, y1, y2, rad, tan, mat):
    """Petit bloc pose sur un cercle autour de l'axe Y (comme un segment de ring)."""
    th = math.radians(phi)
    m.obox((8 + R * math.cos(th), (y1 + y2) / 2, 8 + R * math.sin(th)), (rad, y2 - y1, tan), mat, 'y', snap(-phi))


# ---------------------------------------------------------------- peu commun

@item('briquet', 'Briquet en laiton', 'Brass Lighter', ['vente'],
      'Vieux briquet terni, le capot ne tient plus. Une étincelle sur trois.',
      'Old tarnished lighter, the lid is loose. Sparks one time in three.',
      0.45, {'roll': 0, 'yaw': -24, 'pitch': 14}, 16, 'uncommon')
def _(m):
    # corps terni, vert-de-gris dans les coins, rayures
    m.box((3.0, 0, 6.6), (8.2, 8.0, 9.4), 'brass_worn')
    m.box((3.6, 1.4, 9.4), (7.6, 6.2, 9.46), 'gold_dark')
    m.box((4.2, 2.0, 9.46), (7.0, 5.6, 9.5), 'brass_worn')
    for (f, t) in (((3.0, 0, 9.4), (4.4, 1.8, 9.48)), ((6.6, 6.4, 9.4), (8.2, 8.0, 9.48)),
                   ((8.2, 0.4, 6.8), (8.26, 3.0, 8.6)), ((2.94, 4.6, 7.0), (3.0, 7.4, 9.0))):
        m.box(f, t, 'brass_patina')
    m.box((4.4, 4.6, 9.5), (6.8, 4.75, 9.54), 'brass', ('z', 22.5, (5.6, 4.7, 9.52)))
    m.box((4.6, 2.8, 9.5), (6.4, 2.95, 9.54), 'brass', ('z', -22.5, (5.5, 2.9, 9.52)))
    # cheminee rouillee + molette
    m.box((3.6, 8.0, 7.0), (7.2, 11.4, 9.0), 'rusty_steel')
    for y in (8.9, 10.1):
        for x in (4.1, 5.0, 5.9, 6.7):
            m.box((x - 0.25, y - 0.3, 9.0), (x + 0.25, y + 0.3, 9.04), 'chip')
    m.cyl('z', 7.3, 8.7, 7.7, 10.6, 0.8, 'rust')
    # capot ouvert, charniere desserree : il penche
    hinge = ('z', -22.5, (8.4, 8.0, 8))
    m.box((8.4, 8.0, 6.6), (11.0, 13.2, 9.4), 'brass_worn', hinge)
    m.box((8.36, 8.4, 6.9), (8.4, 12.8, 9.1), 'gold_dark', hinge)
    m.box((10.0, 11.6, 9.4), (11.0, 13.2, 9.46), 'brass_patina', hinge)
    m.box((7.9, 7.6, 7.2), (8.7, 8.4, 8.8), 'brass_patina')


@item('plaque_identite', "Plaque d'identité", 'Dog Tag', ['vente'],
      'Plaque militaire sur sa chaînette. Le nom est presque effacé.',
      'Military tag on its chain. The name is almost worn off.',
      0.5, {'roll': 0, 'yaw': -20, 'pitch': 10}, 16, 'uncommon')
def _(m):
    def tag(z1, z2, rot=None):
        m.box((5.0, 1.6, z1), (11.0, 8.0, z2), 'aluminum', rot)
        m.box((5.6, 1.0, z1), (10.4, 8.6, z2), 'aluminum', rot)
    tag(7.2, 7.6, ('z', 22.5, (8, 8.2, 7.4)))
    tag(7.7, 8.1)
    for (y, x1, x2) in ((6.6, 5.8, 10.0), (5.4, 5.8, 9.2), (4.2, 5.8, 10.2), (3.0, 5.8, 8.4)):
        m.box((x1, y, 8.1), (x2, y + 0.5, 8.16), 'steel_dark')
    m.box((7.65, 7.6, 8.1), (8.35, 8.2, 8.16), 'chip')
    # chaine a billes
    for (x, y, _a) in ellipse(8, 12.2, 3.0, 3.9, -90, 262, 15):
        m.cyl('z', 7.6, 8.4, x, y, 0.4, 'steel')


@item('cigarettes', 'Paquet de cigarettes', 'Pack of Cigarettes', ['vente'],
      "Paquet encore plein. La vraie monnaie de l'apocalypse.", 'Still full. The real currency of the apocalypse.',
      0.45, {'roll': 0, 'yaw': -28, 'pitch': 22}, 16, 'uncommon')
def _(m):
    m.box((4.8, 0, 6.8), (11.2, 8.0, 9.2), 'white')
    m.box((4.8, 5.0, 9.2), (11.2, 8.0, 9.24), 'red')
    m.box((5.8, 3.8, 9.2), (10.2, 4.3, 9.24), 'black')
    m.box((7.3, 1.6, 9.2), (8.7, 3.0, 9.24), 'gold')
    m.box((4.76, 5.0, 6.8), (4.8, 8.0, 9.2), 'red')
    m.box((11.2, 5.0, 6.8), (11.24, 8.0, 9.2), 'red')
    # papier alu + filtres, une cigarette sortie
    m.box((5.0, 8.0, 7.0), (11.0, 8.6, 7.4), 'aluminum')
    for x in (5.9, 7.3, 10.1):
        m.cyl('y', 8.0, 9.8, x, 8.2, 0.68, 'filter')
    m.cyl('y', 8.0, 10.2, 8.7, 8.2, 0.68, 'white')
    m.cyl('y', 10.2, 12.4, 8.7, 8.2, 0.68, 'filter')
    # couvercle rabattu en arriere
    m.box((4.8, 8.0, 4.2), (11.2, 10.4, 6.8), 'red')
    m.box((5.2, 8.4, 6.8), (10.8, 10.0, 6.84), 'white')


@item('bague_argent', 'Bague en argent', 'Silver Ring', ['vente'],
      'Simple anneau en argent, un peu terni.', 'A plain silver ring, slightly tarnished.',
      0.5, {'roll': 0, 'yaw': -35, 'pitch': 20}, 16, 'uncommon')
def _(m):
    m.ring('z', 6.8, 9.2, 8, 8, 5.0, 1.3, 'silver')


# ---------------------------------------------------------------- rare

@item('amulette', 'Amulette', 'Amulet', ['vente'],
      'Pendentif ancien en argent, une pierre bleue en son cœur.', 'An old silver pendant with a blue stone at its heart.',
      0.55, {'roll': 0, 'yaw': -18, 'pitch': 10}, 8, 'rare')
def _(m):
    pendant_chain(m, 8.8, 3.6, 3.8, 'silver')
    m.box((7.5, 8.0, 7.6), (8.5, 8.9, 8.4), 'silver')
    m.cyl('z', 7.4, 8.6, 8, 5.0, 3.3, 'silver')
    m.ring('z', 8.6, 9.0, 8, 5.0, 3.3, 0.7, 'silver')
    m.cyl('z', 8.6, 9.4, 8, 5.0, 1.6, 'sapphire')
    for (x, y) in ((8, 7.3), (8, 2.7), (5.7, 5.0), (10.3, 5.0)):
        m.box((x - 0.35, y - 0.35, 8.6), (x + 0.35, y + 0.35, 9.1), 'silver')
    # goutte
    m.box((7.6, 1.0, 7.7), (8.4, 1.8, 8.3), 'silver')
    m.box((7.4, -0.6, 7.6), (8.6, 1.0, 8.4), 'sapphire')


@item('montre_gousset', 'Montre à gousset', 'Pocket Watch', ['vente'],
      'Couvercle en argent gravé. Elle tourne encore.', 'Engraved silver lid. Still ticking.',
      0.5, TOP, 8, 'rare')
def _(m):
    # boitier + cadran
    m.cyl('y', 0, 1.6, 8, 5.0, 3.6, 'silver')
    m.cyl('y', 1.6, 1.7, 8, 5.0, 3.0, 'white')
    for (dx, dz) in ((0, -2.4), (0, 2.4), (-2.4, 0), (2.4, 0)):
        m.box((8 + dx - 0.2, 1.7, 5.0 + dz - 0.2), (8 + dx + 0.2, 1.74, 5.0 + dz + 0.2), 'black')
    m.box((7.85, 1.7, 3.2), (8.15, 1.76, 5.0), 'black')
    m.box((7.85, 1.7, 5.0), (9.5, 1.76, 5.3), 'black', ('y', -22.5, (8, 1.73, 5.0)))
    # remontoir + anneau
    m.cyl('z', 0.6, 1.4, 8, 0.8, 0.5, 'silver')
    m.cyl('z', -0.4, 0.6, 8, 0.8, 0.9, 'silver')
    m.ring('y', 0.5, 1.1, 8, -1.6, 1.3, 0.45, 'silver', n=8)
    # couvercle ouvert (interieur grave)
    m.box((7.4, 0.2, 8.4), (8.6, 1.0, 9.0), 'steel')
    m.cyl('y', 0, 0.8, 8, 12.4, 3.6, 'silver')
    m.ring('y', 0.8, 0.86, 8, 12.4, 2.6, 0.3, 'silver_worn')
    m.cyl('y', 0.8, 0.86, 8, 12.4, 1.2, 'silver_worn')
    # bout de chaine, accroche a l'anneau
    for i in range(12):
        t = math.radians(-90 + i * 9.5)
        x, z = 8 + 6.0 * math.cos(t), 1.4 + 4.3 * math.sin(t)
        ang = math.degrees(math.atan2(4.3 * math.cos(t), -6.0 * math.sin(t)))
        s = (1.3, 0.36, 0.62) if i % 2 == 0 else (1.3, 0.62, 0.36)
        m.obox((x, 0.5, z), s, 'silver', 'y', snap(-ang))


@item('bague_fiancailles', 'Bague de fiançailles', 'Engagement Ring', ['vente'],
      "Or et petit diamant, dans son écrin. Il y a une histoire derrière.",
      'Gold and a small diamond, in its box. There is a story behind it.',
      0.5, {'roll': 0, 'yaw': -28, 'pitch': 24}, 8, 'rare')
def _(m):
    # ecrin ouvert
    m.box((3.6, 0, 4.6), (12.4, 4.0, 11.4), 'velvet')
    m.box((4.0, 4.0, 5.0), (12.0, 4.6, 11.0), 'cloth')
    m.box((4.0, 4.6, 7.7), (12.0, 4.64, 8.3), 'black')
    m.box((3.6, 4.0, 2.0), (12.4, 10.8, 4.6), 'velvet')
    m.box((4.0, 4.4, 4.6), (12.0, 10.4, 4.66), 'cloth')
    m.box((3.4, 3.6, 4.2), (12.6, 4.2, 4.8), 'gold_dark')
    # bague
    m.ring('z', 7.4, 8.6, 8, 6.6, 2.6, 0.7, 'gold')
    m.cyl('y', 8.8, 9.6, 8, 8, 0.8, 'gold')
    m.cyl('y', 9.6, 10.6, 8, 8, 1.25, 'diamond')
    m.cyl('y', 10.6, 11.0, 8, 8, 0.8, 'diamond')


@item('appareil_photo', 'Appareil photo argentique', 'Film Camera', ['vente'],
      'Boîtier en métal, objectif intact. Plus de pellicule.', 'Metal body, lens intact. Out of film.',
      0.6, {'roll': 0, 'yaw': -28, 'pitch': 18}, 8, 'rare')
def _(m):
    m.box((2, 1.4, 6.4), (14, 2.2, 9.6), 'steel')
    m.box((2, 2.2, 6.4), (14, 8.2, 9.6), 'grille')
    m.box((2, 8.2, 6.4), (14, 9.4, 9.6), 'steel')
    # prisme
    m.box((5.8, 9.4, 6.8), (10.2, 11.4, 9.2), 'steel')
    m.box((6.4, 11.4, 7.2), (9.6, 12.2, 8.8), 'steel')
    m.box((6.6, 9.8, 9.2), (9.4, 10.8, 9.26), 'black')
    # objectif
    m.cyl('z', 9.6, 10.4, 8, 5.2, 2.7, 'steel')
    m.cyl('z', 10.4, 12.6, 8, 5.2, 2.3, 'black')
    m.cyl('z', 11.0, 11.9, 8, 5.2, 2.45, 'grille')
    m.cyl('z', 12.4, 13.1, 8, 5.2, 1.7, 'glass')
    m.ring('z', 12.6, 13.0, 8, 5.2, 2.4, 0.7, 'steel')
    # commandes
    m.cyl('y', 9.4, 10.0, 12.2, 8.0, 0.5, 'steel')
    m.cyl('y', 9.4, 10.4, 3.6, 8.0, 1.1, 'steel_dark')
    m.box((10.6, 9.6, 7.2), (13.6, 9.9, 8.0), 'steel')
    m.box((3.0, 6.0, 9.6), (3.6, 7.4, 9.8), 'steel')
    for x in (1.6, 14.0):
        m.box((x, 7.0, 7.6), (x + 0.4, 8.0, 8.4), 'steel')


@item('crucifix', 'Crucifix doré', 'Golden Crucifix', ['vente'],
      "Croix en or sur une chaîne fine. Quelqu'un priait avec.", 'A gold cross on a thin chain. Someone prayed with it.',
      0.5, {'roll': 0, 'yaw': -18, 'pitch': 10}, 8, 'rare')
def _(m):
    pendant_chain(m, 9.0, 3.2, 3.4, 'gold', size=(1.1, 0.5, 0.3))
    m.box((7.3, 0.6, 7.6), (8.7, 9.0, 8.4), 'gold')
    m.box((4.8, 5.6, 7.6), (11.2, 7.0, 8.4), 'gold')
    for (x1, y1, x2, y2) in ((7.0, 0.4, 9.0, 1.0), (7.0, 8.6, 9.0, 9.2), (4.6, 5.3, 5.2, 7.3), (10.8, 5.3, 11.4, 7.3)):
        m.box((x1, y1, 7.5), (x2, y2, 8.5), 'gold')
    m.box((7.7, 1.4, 8.4), (8.3, 8.2, 8.46), 'gold_dark')
    m.box((5.6, 6.0, 8.4), (10.4, 6.6, 8.46), 'gold_dark')
    m.box((7.6, 5.9, 8.46), (8.4, 6.7, 8.6), 'ruby')


# ---------------------------------------------------------------- epique

@item('lingot_argent', "Lingot d'argent", 'Silver Bar', ['vente'],
      'Un lingot coulé et poinçonné. Ça pèse son poids.', 'A cast, stamped bar. Heavier than it looks.',
      0.55, TOP, 4, 'epic')
def _(m):
    x1, x2, z1, z2 = ingot(m, 2.0, 14.0, 4.6, 11.4, 3.2, 'silver')
    h = 3.2
    # poincons : cartouche, titre et numero
    m.box((x1 + 0.6, h, z1 + 0.5), (x2 - 0.6, h + 0.06, z2 - 0.5), 'silver_worn')
    m.box((x1 + 1.0, h, z1 + 0.9), (x2 - 1.0, h + 0.1, z2 - 0.9), 'silver')
    m.box((x1 + 1.6, h + 0.1, 7.3), (x2 - 2.6, h + 0.14, 7.7), 'steel_dark')
    m.box((x1 + 1.6, h + 0.1, 8.3), (x2 - 4.0, h + 0.14, 8.7), 'steel_dark')
    m.cyl('y', h + 0.1, h + 0.14, x2 - 1.8, 8.0, 0.75, 'steel_dark')


@item('chronographe', 'Chronographe de luxe', 'Luxury Chronograph', ['vente'],
      "Montre en or, intacte. Elle valait plus qu'une voiture.", 'Gold watch, untouched. Used to be worth more than a car.',
      0.5, {'roll': 0, 'yaw': -20, 'pitch': 12}, 4, 'epic')
def _(m):
    # bracelet a maillons
    for (y1, y2) in ((11.0, 15.6), (0.4, 5.0)):
        k = 0
        y = y1
        while y < y2 - 0.1:
            m.box((6.3, y, 7.5), (9.7, y + 1.0, 8.3), 'gold' if k % 2 == 0 else 'gold_dark')
            m.box((7.2, y, 8.3), (8.8, y + 1.0, 8.5), 'gold')
            y += 1.15
            k += 1
    # boitier, lunette, cadran noir
    m.cyl('z', 7.2, 9.0, 8, 8, 3.2, 'gold')
    m.ring('z', 9.0, 9.5, 8, 8, 3.2, 0.7, 'gold_dark')
    m.cyl('z', 9.02, 9.1, 8, 8, 2.6, 'chip')
    for (x, y) in ((8, 9.3), (6.8, 7.4), (9.2, 7.4)):
        m.cyl('z', 9.1, 9.14, x, y, 0.6, 'gunmetal')
    for (dx, dy) in ((0, 2.1), (0, -2.1), (2.1, 0), (-2.1, 0)):
        m.box((8 + dx - 0.2, 8 + dy - 0.2, 9.1), (8 + dx + 0.2, 8 + dy + 0.2, 9.16), 'gold')
    m.box((7.85, 8.0, 9.16), (8.15, 10.2, 9.2), 'gold')
    m.box((7.85, 8.0, 9.16), (8.15, 9.6, 9.2), 'gold', ('z', -45, (8, 8, 9.18)))
    # couronne + poussoirs
    m.cyl('x', 11.1, 11.8, 8, 8, 0.6, 'gold')
    for y in (9.6, 6.4):
        m.box((10.6, y - 0.3, 7.8), (11.4, y + 0.3, 8.4), 'gold')


@item('collier_perles', 'Collier de perles', 'Pearl Necklace', ['vente'],
      'Un rang de perles complet, fermoir en or.', 'A full strand of pearls, gold clasp.',
      0.55, {'roll': 0, 'yaw': -18, 'pitch': 10}, 4, 'epic')
def _(m):
    for (x, y, _a) in ellipse(8, 8.6, 5.0, 7.0, -265, 85, 12.5):
        m.cyl('z', 7.4, 8.6, x, y, 0.65, 'pearl')
    m.box((7.0, 15.0, 7.5), (9.0, 16.0, 8.5), 'gold')
    m.box((7.6, 15.2, 8.5), (8.4, 15.8, 8.66), 'diamond')


@item('statuette_jade', 'Statuette de jade', 'Jade Statuette', ['vente'],
      'Petit éléphant sculpté dans le jade. Fragile.', 'A small elephant carved in jade. Fragile.',
      0.55, {'roll': 0, 'yaw': -30, 'pitch': 16}, 4, 'epic')
def _(m):
    # socle
    m.box((1.6, 0, 4.6), (14.4, 1.4, 11.4), 'wood')
    m.box((2.0, 1.4, 5.0), (14.0, 1.8, 11.0), 'gold_dark')
    # elephant (trompe vers +X)
    for x in (3.6, 9.4):
        for z in (5.8, 8.6):
            m.box((x, 1.8, z), (x + 1.8, 5.4, z + 1.6), 'jade')
    m.box((3.0, 4.6, 5.4), (12.0, 9.6, 10.6), 'jade')
    m.box((4.0, 9.6, 6.0), (11.0, 10.4, 10.0), 'jade')
    m.box((11.4, 6.4, 6.2), (14.4, 10.8, 9.8), 'jade')
    for z in (4.6, 9.8):
        m.box((11.4, 5.8, z), (12.2, 11.0, z + 1.6), 'jade')
    m.box((13.8, 3.6, 7.3), (15.0, 7.0, 8.7), 'jade')
    m.box((14.4, 2.4, 7.4), (16.0, 3.6, 8.6), 'jade')
    for z in (6.6, 8.8):
        m.box((14.0, 5.6, z), (15.8, 6.2, z + 0.6), 'bone')
    m.box((2.4, 6.0, 7.8), (3.0, 8.6, 8.2), 'jade')
    for z in (6.16, 9.8):
        m.box((13.2, 9.0, z), (13.8, 9.6, z + 0.04), 'pcb')


# ---------------------------------------------------------------- legendaire

@item('diamant', 'Diamant', 'Diamond', ['vente'],
      'Une pierre taillée grosse comme une noix. Une vraie folie.', 'A cut stone the size of a walnut. Pure madness.',
      0.6, {'roll': 0, 'yaw': -25, 'pitch': 26}, 1, 'legendary')
def _(m):
    # silhouette de diamant : couronne en trapeze sur un pavillon en pointe,
    # facettes de cote plus sombres
    k = math.sqrt(2)
    m.box((4.5, 9.0, 6.0), (11.5, 11.5, 10.0), 'diamond')
    for x in (4.5, 11.5):
        m.obox((x, 9.0, 8), (2.5 * k, 2.5 * k, 3.9), 'diamond_shade', 'z', 45)
    m.obox((8, 6.5, 8), (3.5 * k, 3.5 * k, 3.8), 'diamond_shade', 'z', 45)
    # aretes brillantes : rondiste, couronne, etoile du pavillon (devant et derriere)
    for (z1, z2) in ((9.94, 10.06), (5.94, 6.06)):
        m.box((2.3, 8.85, z1), (13.7, 9.15, z2), 'diamond')
        for x in (6.6, 9.4):
            m.box((x - 0.15, 9.15, z1), (x + 0.15, 11.46, z2), 'diamond_shade')
        for (sg, a) in ((-1, 22.5), (1, -22.5)):
            m.obox((8 + sg * 1.25, 6.0, (z1 + z2) / 2), (0.3, 6.5, z2 - z1), 'diamond', 'z', a)
        m.box((7.85, 3.4, z1), (8.15, 8.85, z2), 'diamond')


@item('lingot_or', "Lingot d'or", 'Gold Bar', ['vente'],
      "Un kilo d'or massif, poinçonné. Le rêve de tout pillard.", "A kilo of solid gold, stamped. Every looter's dream.",
      0.6, TOP, 1, 'legendary')
def _(m):
    h = 4.0
    x1, x2, z1, z2 = ingot(m, 0.6, 15.4, 3.8, 12.2, h, 'gold')
    m.box((x1 + 0.6, h, z1 + 0.5), (x2 - 0.6, h + 0.06, z2 - 0.5), 'gold_dark')
    m.box((x1 + 1.0, h, z1 + 0.9), (x2 - 1.0, h + 0.1, z2 - 0.9), 'gold')
    m.box((x1 + 3.4, h + 0.1, 7.0), (x2 - 1.4, h + 0.14, 7.4), 'gold_dark')
    m.box((x1 + 3.4, h + 0.1, 8.0), (x2 - 2.8, h + 0.14, 8.4), 'gold_dark')
    m.box((x1 + 3.4, h + 0.1, 9.0), (x2 - 4.0, h + 0.14, 9.3), 'gold_dark')
    m.cyl('y', h + 0.1, h + 0.14, x1 + 1.9, 8.2, 0.9, 'gold_dark')


@item('couronne', 'Couronne', 'Crown', ['vente'],
      "Or, rubis et saphirs. Quelqu'un régnait avant la fin.", 'Gold, rubies and sapphires. Someone ruled before the end.',
      0.55, {'roll': 0, 'yaw': -28, 'pitch': 24}, 1, 'legendary')
def _(m):
    m.cyl('y', 0.6, 5.4, 8, 8, 5.0, 'velvet')
    m.cyl('y', 5.4, 6.8, 8, 8, 3.6, 'velvet')
    m.ring('y', 0, 3.0, 8, 8, 6.0, 1.0, 'gold')
    m.ring('y', -0.04, 0.5, 8, 8, 6.3, 0.6, 'gold_dark')
    # fleurons + perles
    for k in range(8):
        phi = k * 45
        around_y(m, 5.5, phi, 3.0, 5.4, 0.8, 1.4, 'gold')
        around_y(m, 5.5, phi, 5.4, 6.2, 0.9, 0.9, 'pearl')
        around_y(m, 6.0, phi + 22.5, 1.0, 2.0, 0.6, 1.0, 'ruby' if k % 2 == 0 else 'sapphire')
    # arceaux + globe et croix
    for (x, y, ang) in ellipse(8, 5.4, 5.2, 3.0, 8, 172, 16):
        m.obox((x, y, 8), (1.2, 0.7, 0.9), 'gold', 'z', snap(ang))
    for (z, y, ang) in ellipse(8, 5.4, 5.2, 3.0, 8, 172, 16):
        m.obox((8, y, z), (0.9, 0.7, 1.2), 'gold', 'x', snap(-ang))
    m.cyl('y', 8.4, 9.4, 8, 8, 0.9, 'gold')
    m.box((7.7, 9.4, 7.7), (8.3, 11.4, 8.3), 'gold')
    m.box((7.0, 10.2, 7.7), (9.0, 10.8, 8.3), 'gold')


@item('idole_doree', 'Idole dorée', 'Golden Idol', ['vente'],
      'Statuette antique en or massif. Ses yeux de rubis vous suivent.',
      'Ancient solid gold statuette. Its ruby eyes follow you.',
      0.55, {'roll': 0, 'yaw': -24, 'pitch': 12}, 1, 'legendary')
def _(m):
    m.box((4.2, 0, 5.4), (11.8, 1.2, 10.8), 'gold_dark')
    # corps accroupi
    m.box((5.0, 1.2, 6.0), (11.0, 4.6, 10.0), 'gold')
    for x in (5.2, 8.4):
        m.box((x, 3.4, 9.2), (x + 2.4, 5.4, 10.8), 'gold')
    m.box((5.6, 4.6, 6.4), (10.4, 8.0, 9.4), 'gold')
    for x in (4.6, 10.4):
        m.box((x, 4.2, 7.0), (x + 1.0, 7.6, 9.8), 'gold')
    m.box((4.8, 4.6, 10.6), (11.2, 5.5, 11.2), 'gold_dark')
    # grosse tete
    m.box((4.8, 8.0, 6.0), (11.2, 13.6, 10.0), 'gold')
    m.box((4.8, 11.2, 10.0), (11.2, 12.0, 10.4), 'gold_dark')
    for x in (5.8, 8.6):
        m.box((x, 10.0, 10.0), (x + 1.6, 11.0, 10.2), 'ruby')
    m.box((7.5, 8.8, 10.0), (8.5, 10.6, 10.6), 'gold')
    m.box((6.4, 8.3, 10.0), (9.6, 8.6, 10.1), 'gold_dark')
    for x in (4.2, 11.2):
        m.box((x, 9.2, 7.4), (x + 0.6, 11.8, 8.6), 'gold')
    m.box((5.4, 13.6, 6.6), (10.6, 14.4, 9.4), 'gold_dark')


@item('oeuf_orne', 'Œuf orné', 'Ornate Egg', ['vente'],
      "Émail et or, un objet de musée. Personne ne sait comment il est arrivé là.",
      'Enamel and gold, a museum piece. Nobody knows how it got here.',
      0.5, {'roll': 0, 'yaw': -25, 'pitch': 18}, 1, 'legendary')
def _(m):
    # pied
    m.cyl('y', 0, 0.6, 8, 8, 2.8, 'gold_dark')
    m.cyl('y', 0.6, 1.6, 8, 8, 1.3, 'gold')
    # coquille (profil d'oeuf, plus large en bas)
    y0, yc, y1, R = 1.6, 6.6, 14.0, 4.6
    bands = [1.6, 2.4, 3.4, 4.6, 6.0, 7.6, 9.2, 10.6, 11.8, 12.8, 13.5, 14.0]
    for a, b in zip(bands, bands[1:]):
        mid = (a + b) / 2
        ry = (yc - y0) if mid < yc else (y1 - yc)
        r = max(0.8, R * math.sqrt(max(0.0, 1 - ((mid - yc) / ry) ** 2)))
        m.cyl('y', a, b, 8, 8, r, 'enamel')
        if r > 2.0:  # treillis d'or
            for ang in (0, 45):
                for sgn in (-1, 1):
                    m.box((7.8, a - 0.03, 8 + sgn * r - 0.06), (8.2, b + 0.03, 8 + sgn * r + 0.08), 'gold', ('y', ang, (8, mid, 8)))
                    m.box((8 + sgn * r - 0.06, a - 0.03, 7.8), (8 + sgn * r + 0.08, b + 0.03, 8.2), 'gold', ('y', ang, (8, mid, 8)))
    # ceinture d'or sertie
    m.cyl('y', 6.0, 6.8, 8, 8, 4.75, 'gold')
    for k in range(8):
        around_y(m, 4.75, k * 45 + 22.5, 6.1, 6.7, 0.4, 0.6, 'ruby')
    m.cyl('y', 14.0, 14.6, 8, 8, 0.9, 'gold')
    m.cyl('y', 14.6, 15.4, 8, 8, 0.7, 'diamond')


# ---------------------------------------------------------------- mythique

@item('crane_cristal', 'Crâne de cristal', 'Crystal Skull', ['vente'],
      "Taillé d'un seul bloc de cristal. Personne n'ose le regarder dans les yeux.",
      'Carved from a single block of crystal. Nobody dares to look it in the eyes.',
      0.55, {'roll': 0, 'yaw': -28, 'pitch': 10}, 1, 'mythic')
def _(m):
    # boite cranienne
    m.cyl('y', 5.0, 11.6, 8, 7.4, 5.0, 'crystal')
    m.cyl('y', 11.6, 12.8, 8, 7.4, 4.2, 'crystal')
    m.cyl('y', 12.8, 13.6, 8, 7.4, 3.0, 'crystal')
    # visage
    m.box((3.6, 3.4, 9.0), (12.4, 10.6, 12.6), 'crystal')
    m.box((3.8, 9.0, 12.6), (12.2, 10.0, 13.0), 'crystal')
    for x in (4.6, 8.6):
        m.box((x, 6.4, 12.6), (x + 2.8, 9.0, 12.66), 'crystal_dark')
    m.box((7.6, 6.6, 12.6), (8.4, 9.0, 12.8), 'crystal')
    m.box((7.3, 4.9, 12.6), (8.7, 6.4, 12.66), 'crystal_dark')
    for x in (3.2, 12.0):
        m.box((x, 5.0, 10.0), (x + 0.8, 7.4, 12.2), 'crystal')
    # machoires et dents
    m.box((5.0, 2.4, 9.6), (11.0, 3.4, 12.4), 'crystal')
    m.box((5.4, 1.6, 11.6), (10.6, 2.6, 12.5), 'crystal')
    for x in (6.3, 7.2, 8.0, 8.8, 9.7):
        m.box((x - 0.06, 1.6, 12.5), (x + 0.06, 2.6, 12.54), 'crystal_dark')
    m.box((5.4, 1.55, 12.5), (10.6, 1.7, 12.54), 'crystal_dark')
    m.box((5.0, 0.4, 9.4), (11.0, 1.6, 12.2), 'crystal')
    m.box((6.4, 0, 10.6), (9.6, 0.6, 12.0), 'crystal')
    for x in (4.6, 10.6):
        m.box((x, 0.6, 7.4), (x + 0.8, 4.0, 10.0), 'crystal')


@item('sceptre', 'Sceptre royal', 'Royal Sceptre', ['vente'],
      "Or massif et saphir géant. Le pouvoir, du temps où ça voulait dire quelque chose.",
      'Solid gold and a giant sapphire. Power, back when it meant something.',
      0.85, {'roll': 40, 'yaw': -16, 'pitch': 20}, 1, 'mythic',
      fp={'rotation': [0, 90, 45], 'translation': [1.13, 3.6, 1.13], 'k': 1.25},  # tenu leve, tete en haut
      tp={'rotation': [0, -90, 100], 'translation': [0, 3.28, 0.63], 'scale': 1.0})  # tenu comme une epee, manche dans la main
def _(m):
    # pommeau
    m.cyl('x', 0.0, 0.8, 8, 8, 1.2, 'gold')
    m.box((-0.3, 7.6, 7.6), (0.0, 8.4, 8.4), 'ruby')
    # manche cercle d'anneaux, noeud central
    m.cyl('x', 0.8, 11.0, 8, 8, 0.65, 'gold')
    for x in (1.8, 3.4, 8.4, 10.0):
        m.cyl('x', x, x + 0.4, 8, 8, 0.85, 'gold_dark')
    m.cyl('x', 5.2, 6.8, 8, 8, 1.1, 'gold')
    for (y, z) in ((9.1, 8), (6.9, 8), (8, 9.1), (8, 6.9)):
        m.box((5.7, y - 0.3, z - 0.3), (6.3, y + 0.3, z + 0.3), 'pearl')
    # tete : coupelle, griffes, saphir geant, croix
    m.cyl('x', 11.0, 11.6, 8, 8, 1.1, 'gold')
    m.cyl('x', 11.6, 12.3, 8, 8, 1.7, 'gold')
    m.cyl('x', 12.1, 14.9, 8, 8, 1.75, 'sapphire')
    for (y, z) in ((9.75, 8), (6.25, 8), (8, 9.75), (8, 6.25)):
        m.box((12.1, y - 0.25, z - 0.25), (13.9, y + 0.25, z + 0.25), 'gold')
    m.cyl('x', 14.9, 15.5, 8, 8, 0.9, 'gold')
    m.box((15.5, 7.75, 7.75), (17.3, 8.25, 8.25), 'gold')
    m.box((16.2, 7.0, 7.75), (16.7, 9.0, 8.25), 'gold')


@item('calice', 'Calice sacré', 'Sacred Chalice', ['vente'],
      "Une relique d'or et de pierres, sortie d'une cathédrale en ruine.",
      'A relic of gold and gems, taken from a ruined cathedral.',
      0.55, {'roll': 0, 'yaw': -25, 'pitch': 20}, 1, 'mythic')
def _(m):
    # pied
    m.cyl('y', 0, 0.6, 8, 8, 4.2, 'gold')
    m.ring('y', -0.04, 0.4, 8, 8, 4.4, 0.5, 'gold_dark')
    m.cyl('y', 0.6, 1.4, 8, 8, 3.0, 'gold')
    m.cyl('y', 1.4, 2.0, 8, 8, 1.6, 'gold')
    # tige + noeud serti
    m.cyl('y', 2.0, 6.0, 8, 8, 0.85, 'gold')
    m.cyl('y', 3.4, 4.6, 8, 8, 1.8, 'gold')
    for k in range(4):
        around_y(m, 1.8, k * 90 + 45, 3.7, 4.3, 0.4, 0.7, 'ruby' if k % 2 == 0 else 'emerald')
    # coupe
    for (y1, y2, r) in ((6.0, 6.6, 1.6), (6.6, 7.4, 2.6), (7.4, 8.4, 3.4), (8.4, 11.6, 3.9)):
        m.cyl('y', y1, y2, 8, 8, r, 'gold')
    for k in range(8):
        around_y(m, 3.9, k * 45 + 22.5, 9.4, 10.4, 0.4, 1.0, ('ruby', 'sapphire', 'emerald', 'sapphire')[k % 4])
    m.ring('y', 11.6, 12.1, 8, 8, 4.0, 0.6, 'gold_dark')
    m.cyl('y', 11.6, 11.7, 8, 8, 3.4, 'enamel')


# ================================================================ CONSTRUCTION (bases de CRac)
# Vendus par le Bâtisseur (PNJ) : planches, pierres taillées, fragments de métal, métal renforcé.
# Trouvés sur la carte (plus tard) : clous, sac de ciment, équerres d'acier, bobine de fil à souder.

LETTRES = {
    'C': ['111', '100', '100', '100', '111'],
    'I': ['111', '010', '010', '010', '111'],
    'M': ['101', '111', '101', '101', '101'],
    'E': ['111', '100', '110', '100', '111'],
    'N': ['101', '111', '111', '111', '101'],
    'T': ['111', '010', '010', '010', '010'],
}


def ecrire(m, mot, x0, y0, z, px, mat):
    """Lettres en relief sur la face avant (z), pixel de px."""
    x = x0
    for c in mot:
        for r, ligne in enumerate(LETTRES[c]):
            for k, b in enumerate(ligne):
                if b == '1':
                    m.box((x + k * px, y0 - (r + 1) * px, z), (x + (k + 1) * px, y0 - r * px, z + 0.06), mat)
        x += 4 * px


@item('planches', 'Planches', 'Planks', ['construction'],
      'Des planches sciées, prêtes à clouer.', 'Sawn planks, ready to nail.',
      0.6, TOP, 64, 'uncommon')
def _(m):
    # trois planches sciées empilées, sanglées
    for i, (y, dz, mat) in enumerate(((0, 0, 'wood_raw'), (1.6, 0.6, 'wood'), (3.2, -0.4, 'wood_raw'))):
        for j in range(2):
            z0 = 3.0 + j * 5.2 + dz
            m.box((0.6 + i * 0.4, y, z0), (15.4 - i * 0.3, y + 1.5, z0 + 4.8), mat)
    for x in (3.4, 12.2):
        m.box((x, -0.1, 2.6), (x + 1.0, 4.9, 13.8), 'string')


@item('brindilles', 'Brindilles', 'Twigs', ['construction'],
      'Du petit bois ramassé. De quoi monter le squelette d\'une base.', 'Gathered kindling. Enough to frame a base.',
      0.6, TOP, 64, 'uncommon')
def _(m):
    # brindilles en vrac, croisées, quelques feuilles
    for i, ang in enumerate((-22.5, 0, 22.5, -45, 45, 22.5)):
        y = 0.5 + i * 0.75
        m.box((2.0, y, 7.5), (14.0, y + 0.9, 8.4), 'twig' if i % 2 else 'twig_lt', ('y', ang, (8, y, 8)))
    m.box((10.5, 2.6, 9.5), (12.0, 2.7, 11.0), 'leaf')
    m.box((4.0, 4.0, 5.0), (5.4, 4.1, 6.2), 'leaf')


@item('pierres_taillees', 'Pierres taillées', 'Cut Stone', ['construction'],
      'Des blocs de pierre équarris. Lourds.', 'Squared stone blocks. Heavy.',
      0.6, TOP, 64, 'rare')
def _(m):
    m.box((1.0, 0, 2.0), (7.6, 4.2, 14.0), 'stone_cut')
    m.box((8.2, 0, 2.6), (15.0, 4.2, 13.4), 'stone_dark')
    m.box((3.4, 4.2, 4.0), (12.2, 8.0, 12.0), 'stone_cut')
    for (x, z) in ((5.0, 5.0), (10.4, 10.6)):
        m.box((x, 8.0, z), (x + 1.2, 8.05, z + 0.4), 'stone_dark')


@item('fragments_metal', 'Fragments de métal', 'Metal Fragments', ['construction'],
      'Des chutes de tôle et de barres découpées.', 'Offcuts of sheet metal and cut bars.',
      0.6, TOP, 64, 'epic')
def _(m):
    m.box((1.0, 0, 3.0), (9.0, 0.8, 9.0), 'plate', ('y', 22.5, (5, 0.4, 6)))
    m.box((6.0, 0, 7.0), (14.6, 0.8, 13.0), 'rusty_steel', ('y', -22.5, (10, 0.4, 10)))
    m.box((3.0, 0.8, 7.0), (11.0, 1.6, 10.6), 'steel', ('y', 45, (7, 1.2, 8.8)))
    m.box((8.0, 0.8, 2.4), (14.4, 1.5, 5.6), 'gunmetal_worn')
    m.box((2.0, 1.6, 4.0), (6.6, 2.3, 8.0), 'steel_dark', ('x', 22.5, (4, 1.9, 6)))
    m.cyl('x', 4.0, 13.0, 2.0, 11.6, 0.6, 'steel')


@item('metal_renforce', 'Métal renforcé', 'Reinforced Metal', ['construction'],
      "Une plaque d'acier épaisse, cerclée et rivetée.", 'A thick steel plate, banded and riveted.',
      0.6, TOP, 64, 'legendary')
def _(m):
    m.box((1.0, 0, 3.5), (15.0, 3.0, 12.5), 'hq_metal')
    for x in (3.4, 12.0):
        m.box((x, -0.1, 3.3), (x + 0.8, 3.15, 12.7), 'hq_band')
    for x in (2.2, 6.2, 9.8, 13.8):
        for z in (4.6, 11.4):
            m.cyl('y', 3.0, 3.4, x, z, 0.45, 'steel', sides=4)
    m.box((5.0, 3.0, 6.6), (11.0, 3.1, 9.4), 'yellow')


# ---------------------------------------------------------------- trouvés sur la carte


@item('sac_ciment', 'Sac de ciment', 'Cement Bag', ['construction'],
      'Pour monter la pierre.', 'For laying stone.',
      0.6, {'roll': 0, 'yaw': -24, 'pitch': 16}, 64, 'rare')
def _(m):
    # sac debout, gris, un peu bombé
    m.box((2.2, 0, 5.0), (13.8, 11.0, 11.0), 'cem_bag')
    m.box((1.8, 0.6, 5.4), (14.2, 10.4, 10.6), 'cem_bag')
    m.box((2.6, 0, 4.6), (13.4, 10.6, 11.4), 'cem_bag')
    # haut plié et ouvert : poudre grise qui déborde
    m.box((2.6, 11.0, 5.6), (13.4, 11.8, 10.4), 'cem_bag_dk')
    m.box((3.6, 11.8, 6.4), (12.4, 12.8, 9.6), 'cem_powder')
    m.box((5.4, 12.8, 7.0), (10.6, 13.5, 9.0), 'cem_powder')
    # bandeau rouge et inscription CIMENT
    m.box((2.6, 7.4, 11.4), (13.4, 9.6, 11.46), 'ink_red')
    ecrire(m, 'CIMENT', 3.2, 6.8, 11.4, 0.42, 'ink')
    m.box((2.6, 1.0, 11.4), (13.4, 1.6, 11.46), 'ink_red')
    # poudre tombée au pied du sac
    m.box((9.0, 0, 11.4), (13.6, 0.5, 13.4), 'cem_powder')
    m.box((10.2, 0.5, 11.8), (12.6, 0.8, 12.8), 'cem_powder')


@item('equerres_acier', "Équerres d'acier", 'Steel Brackets', ['construction'],
      'Pour assembler les plaques de métal.', 'For joining metal sheets.',
      0.6, TOP, 64, 'epic')
def _(m):
    for (x, z, rot) in ((2.0, 3.0, 0), (6.0, 8.6, -22.5), (9.6, 3.6, 22.5)):
        o = (x + 2.5, 1, z + 2.5)
        r = ('y', rot, o) if rot else None
        m.box((x, 0, z), (x + 5.0, 0.7, z + 5.0), 'steel', r)
        m.box((x, 0.7, z), (x + 5.0, 5.0, z + 0.7), 'steel', r)
        m.box((x + 1.6, 0.7, z + 2.6), (x + 3.4, 0.75, z + 3.6), 'chip', r)
        m.box((x + 1.6, 2.4, z + 0.7), (x + 3.4, 3.4, z + 0.75), 'chip', r)


@item('fil_souder', 'Bobine de fil à souder', 'Welding Wire Spool', ['construction'],
      'Pour souder le métal renforcé.', 'For welding reinforced metal.',
      0.6, {'roll': 0, 'yaw': -30, 'pitch': 22}, 64, 'legendary')
def _(m):
    # bobine debout : deux flasques noires, fil cuivré enroulé
    for z in (4.0, 11.2):
        m.cyl('z', z, z + 0.8, 8, 8, 6.4, 'polymer')
        m.ring('z', z - 0.05, z + 0.85, 8, 8, 6.4, 0.5, 'black')
    for k, r in enumerate((5.4, 5.0, 4.6)):
        m.cyl('z', 4.8, 11.2, 8, 8, r, 'copper_wire')
    m.cyl('z', 3.8, 12.2, 8, 8, 1.6, 'steel_dark')
    m.cyl('z', 3.7, 12.3, 8, 8, 0.8, 'chip')
    # étiquette sur la flasque avant
    m.box((5.4, 9.6, 12.0), (10.6, 12.2, 12.06), 'label_yellow')
    # bout de fil qui dépasse
    m.box((12.8, 3.6, 7.8), (16.0, 4.0, 8.2), 'copper', ('z', -22.5, (12.8, 3.8, 8)))



# ================================================================ NIVEAUX DES COMPOSANTS DE FABRICATION
# 21/10 (choix du propriétaire, rendus validés) : chaque composant de fabrication existe en 4 niveaux ; l'objet d'origine
# est le niveau I (Peu commun), puis II Rare « anodisé », III Épique « militaire », IV Légendaire « doré ». Même modèle,
# autres matériaux. Pour le craft à venir : niveau I -> armes Commun, II -> Peu commun, III -> Rare, IV -> Épique
# (Légendaire et Unique jamais fabriquées).
NIVEAUX = (('II', 'rare'), ('III', 'epic'), ('IV', 'legendary'))
MATERIAUX_NIVEAUX = {
    'canon': ({'steel': 'plate_clean', 'steel_dark': 'blue_metal', 'gunmetal': 'blue_metal'},
              {'steel': 'gunmetal', 'steel_dark': 'black', 'gunmetal': 'olive'},
              {'steel': 'gold', 'steel_dark': 'gold_dark', 'gunmetal': 'black'}),
    'ressort': ({'steel': 'blue_metal'}, {'steel': 'black'}, {'steel': 'gold'}),
    'kit_pieces': ({'olive': 'blue', 'zinc': 'aluminum', 'steel': 'plate_clean'},
                   {'olive': 'black', 'steel': 'gunmetal', 'zinc': 'olive'},
                   {'olive': 'black', 'steel': 'gold', 'zinc': 'gold_dark', 'brass': 'gold'}),
    'tissu': ({'red': 'blue'}, {'cloth': 'cloth_khaki', 'red': 'olive'}, {'cloth': 'cloth_silk', 'red': 'gold'}),
    'alcool': ({'red': 'blue'}, {'white': 'olive', 'red': 'white'}, {'white': 'black', 'red': 'gold'}),
    'batterie': ({'cell_blue': 'cell_cyan', 'aluminum': 'plate_clean'}, {'cell_blue': 'label_green', 'aluminum': 'gunmetal'},
                 {'cell_blue': 'label_black', 'aluminum': 'gold'}),
    'circuit_imprime': ({'pcb': 'pcb_blue'}, {'pcb': 'pcb_black', 'steel': 'gunmetal'},
                        {'pcb': 'pcb_black', 'steel': 'gold', 'aluminum': 'gold', 'brass': 'gold'}),
    'ferraille': ({'rusty_steel': 'steel', 'rust': 'blue_metal', 'zinc': 'aluminum'},
                  {'rusty_steel': 'gunmetal', 'rust': 'black', 'steel_dark': 'olive', 'zinc': 'olive'},
                  {'rusty_steel': 'gold', 'rust': 'gold_dark', 'steel_dark': 'gold', 'zinc': 'gold'}),
    'bocal_poudre': ({'steel_dark': 'blue_metal', 'steel': 'plate_clean', 'white': 'blue'},
                     {'steel_dark': 'black', 'steel': 'gunmetal', 'white': 'olive'},
                     {'steel_dark': 'gold_dark', 'steel': 'gold', 'white': 'gold'}),
    'caoutchouc': ({'steel_dark': 'blue_metal'}, {'tire': 'tire_olive', 'steel_dark': 'black'},
                   {'steel_dark': 'gold', 'brass': 'gold'}),
    'scotch': ({'tape': 'tape_blue'}, {'tape': 'olive_tape'}, {'tape': 'gold', 'cardboard': 'black'}),
}


def _niveau(fn, carte):
    def construire(m):
        fn(m)
        for b in m.boxes:
            b['m'] = carte.get(b['m'], b['m'])
    return construire


def _ajouter_niveaux():
    par_id = {it['id']: it for it in ITEMS}
    for iid, cartes in MATERIAUX_NIVEAUX.items():
        base = par_id[iid]
        pos = ITEMS.index(base) + 1
        for (chiffre, rarete), carte in zip(NIVEAUX, cartes):
            ITEMS.insert(pos, dict(base, id=f'{iid}_{chiffre.lower()}', fr=f"{base['fr']} {chiffre}", en=f"{base['en']} {chiffre}",
                                   rarity=rarete, build=_niveau(base['build'], carte)))
            pos += 1


_ajouter_niveaux()
