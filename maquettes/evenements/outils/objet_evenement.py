"""Propositions de modèle 3D pour l'objet d'événement à revendre (aperçu seulement, rien d'installé).
Même moteur que les composants et les objets de quête (modules/equipement/outils/engine.py).
    python maquettes/evenements/outils/objet_evenement.py maquettes/evenements/objet_evenement_propositions.png"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', '..', '..', 'modules', 'equipement',
                                'outils'))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
from engine import Model, fp_matrix, material_textures, render  # noqa: E402

FP_DEBOUT = {'rotation': [-25, -30, 0], 'translation': [-1, 6.5, 0], 'k': 1.55}
OPTIONS = []


def option(lettre, nom, desc, vue):
    def deco(fn):
        OPTIONS.append(dict(lettre=lettre, nom=nom, desc=desc, vue=vue, build=fn))
        return fn
    return deco


@option('A', 'Mallette scellée', "Mallette d'aluminium cabossée, coins renforcés, deux serrures à molettes et un scellé "
        "rouge au fil d'acier. Le scellé intact prouve qu'elle n'a pas été ouverte.", {'roll': 0, 'yaw': -32, 'pitch': 22})
def _(m):
    m.box((2.8, 2.0, 6.0), (13.2, 9.6, 10.0), 'aluminum')
    m.box((2.6, 5.6, 5.9), (13.4, 5.9, 10.1), 'black')               # ligne d'ouverture
    for y in (3.3, 8.0):                                             # nervures
        m.box((3.4, y, 10.0), (12.6, y + 0.35, 10.12), 'plate_clean')
    for (x1, x2) in ((2.5, 3.7), (12.3, 13.5)):                     # coins renforcés
        for (y1, y2) in ((1.8, 3.0), (8.6, 9.8)):
            m.box((x1, y1, 5.8), (x2, y2, 10.2), 'black')
    for x in (6.4, 9.0):                                             # poignée
        m.box((x, 9.6, 7.4), (x + 0.6, 10.5, 8.6), 'steel_dark')
    m.box((6.1, 10.5, 7.3), (9.9, 11.3, 8.7), 'rubber')
    for x in (4.0, 10.6):                                            # serrures et molettes
        m.box((x, 6.6, 10.0), (x + 1.4, 8.0, 10.3), 'steel_dark')
        for k in range(3):
            m.box((x + 0.15 + k * 0.4, 6.85, 10.3), (x + 0.45 + k * 0.4, 7.3, 10.42), 'brass')
    m.box((4.55, 4.2, 10.3), (4.75, 6.6, 10.38), 'steel')            # fil du scellé
    m.box((3.9, 3.0, 10.32), (5.5, 4.4, 10.48), 'label_red')         # scellé
    m.box((7.0, 2.5, 10.0), (11.4, 3.1, 10.08), 'label_black')       # étiquette pochoir


@option('B', 'Dossier classé', "Chemise cartonnée bourrée de rapports, bande rouge « SECRET », sangle noire et cachet "
        "de cire. Papier : léger, mais il vaut cher à la bonne personne.", {'roll': 0, 'yaw': -28, 'pitch': 18})
def _(m):
    m.box((3.0, 1.4, 7.3), (12.8, 13.6, 8.7), 'kraft')               # chemise
    m.box((12.8, 1.8, 7.45), (13.1, 13.2, 8.55), 'newspaper')        # tranche des feuilles
    m.box((3.4, 13.6, 7.45), (12.4, 13.85, 8.55), 'newspaper')
    m.box((3.0, 9.4, 8.7), (12.8, 10.9, 8.76), 'label_red')          # bande SECRET
    m.box((3.0, 9.4, 7.24), (12.8, 10.9, 7.3), 'label_red')
    m.box((10.2, 1.2, 7.15), (10.9, 13.8, 8.85), 'black')            # sangle
    m.cyl('z', 8.7, 9.0, 7.6, 5.4, 1.25, 'enamel', sides=10)         # cachet de cire
    m.cyl('z', 9.0, 9.08, 7.6, 5.4, 0.7, 'red_worn', sides=10)
    m.box((4.0, 12.2, 8.7), (4.4, 14.2, 8.85), 'steel')              # trombone et photo
    m.box((4.4, 11.2, 8.7), (7.0, 13.2, 8.78), 'newspaper')


@option('C', 'Disque dur militaire', "Disque dur durci, coque de caoutchouc, voyant encore allumé, étiquette de "
        "l'armée et scellé rouge sur le port. Les données dedans valent de l'or.", {'roll': 0, 'yaw': -34, 'pitch': 24})
def _(m):
    m.box((3.6, 2.2, 6.4), (12.4, 11.0, 9.6), 'gunmetal')
    for (x1, x2) in ((3.0, 4.8), (11.2, 13.0)):                     # coins de caoutchouc
        for (y1, y2) in ((1.6, 3.4), (9.8, 11.6)):
            m.box((x1, y1, 6.0), (x2, y2, 10.0), 'rubber')
    m.box((3.2, 3.4, 6.2), (3.6, 9.8, 9.8), 'rubber')                # flancs
    m.box((12.4, 3.4, 6.2), (12.8, 9.8, 9.8), 'rubber')
    m.box((5.2, 5.0, 9.6), (10.8, 8.4, 9.7), 'label_black')          # étiquette de l'armée
    m.box((5.2, 4.2, 9.6), (10.8, 4.6, 9.66), 'white')
    m.box((5.0, 3.2, 9.6), (11.0, 3.7, 9.68), 'hazard')
    m.box((10.4, 9.6, 9.6), (10.9, 10.1, 9.75), 'ruby')              # voyant
    m.box((6.8, 11.0, 7.4), (9.2, 11.8, 8.6), 'steel_dark')          # port
    m.box((7.6, 11.8, 7.8), (8.4, 13.0, 8.2), 'steel')               # fil du scellé
    m.box((7.2, 13.0, 7.6), (8.8, 14.3, 8.4), 'label_red')


def police(nom, t):
    for f in (nom, 'arial.ttf'):
        try:
            return ImageFont.truetype('C:/Windows/Fonts/' + f, t)
        except OSError:
            pass
    return ImageFont.load_default()


def planche(chemin):
    mats = material_textures()
    ft, fs, fp = police('arialbd.ttf', 20), police('arial.ttf', 14), police('arialbd.ttf', 13)
    cw, ch = 470, 300
    sheet = Image.new('RGB', (24 + cw * len(OPTIONS) + 8, ch + 90), (22, 24, 28))
    d = ImageDraw.Draw(sheet)
    d.text((24, 18), "Objet d'événement à revendre : 3 propositions (icône d'inventaire ×3 · modèle 3D · en main)",
           fill=(235, 235, 235), font=ft)
    d.text((24, 46), "Trouvé à coup sûr dans les caisses d'événement · revendu au spawn pour des hérals ET des points "
           "de saison pour son clan", fill=(150, 160, 175), font=fs)
    for i, o in enumerate(OPTIONS):
        m = Model()
        o['build'](m)
        m.recenter()
        icon = render(m, mats, 32, **o['vue'])
        big = render(m, mats, 150, detail=1.0, outline=False, pad=6, **o['vue'])
        fpv = render(m, mats, 120, detail=1.0, outline=False, pad=5, M=fp_matrix(FP_DEBOUT['rotation']))
        x, y = 24 + i * cw, 78
        d.rounded_rectangle((x, y, x + cw - 16, y + ch), 10, fill=(34, 37, 43))
        slot = Image.new('RGB', (104, 104), (139, 139, 139))
        up = icon.resize((96, 96), Image.NEAREST)
        slot.paste(up, (4, 4), up)
        sheet.paste(slot, (x + 12, y + 40))
        sheet.paste(big, (x + 128, y + 6), big)
        d.rounded_rectangle((x + 290, y + 30, x + cw - 28, y + 150), 8, fill=(58, 74, 92))
        sheet.paste(fpv, (x + 300, y + 30), fpv)
        d.text((x + 14, y + 12), o['lettre'], fill=(255, 122, 69), font=ft)
        d.text((x + 14, y + 168), o['nom'], fill=(240, 205, 110), font=ft)
        mots, ligne, ly = o['desc'].split(), '', y + 200
        for mot in mots:
            if d.textlength(ligne + ' ' + mot, font=fs) > cw - 48:
                d.text((x + 14, ly), ligne, fill=(170, 178, 192), font=fs)
                ligne, ly = mot, ly + 19
            else:
                ligne = (ligne + ' ' + mot).strip()
        d.text((x + 14, ly), ligne, fill=(170, 178, 192), font=fs)
        d.text((x + 14, y + ch - 24), 'Rareté Légendaire · un seul par case', fill=(224, 182, 74),
               font=fp)
    sheet.save(chemin)
    print('planche :', chemin)


if __name__ == '__main__':
    planche(sys.argv[1])
