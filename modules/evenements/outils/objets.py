"""Objets d'événement à revendre de HeraliZ Événements en 3D (rendus validés le 25/10 : maquettes/evenements/
objet_evenement_propositions.png), au même style que les objets de quête (même moteur : modules/equipement/outils/engine.py).

  python modules/evenements/outils/objets.py --installer            modèles, matières et icônes dans le mod
  python modules/evenements/outils/objets.py --apercu planche.png    planche d'aperçu (icône, modèle, 1re personne)

On modifie ce script, jamais les fichiers écrits (models/item/3d, models/item/icon, textures/item/mat, textures/item/icon
et models/item/<id>.json de ces objets). Modèles de profil : X = longueur, Y = hauteur, Z = épaisseur (centrée sur 8)."""
import json
import os
import sys

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ICI, '..', '..', 'equipement', 'outils'))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
from engine import Model, fp_matrix, material_textures, render  # noqa: E402

OBJETS = []
FP_DEBOUT = {'rotation': [-25, -30, 0], 'translation': [-1, 6.5, 0], 'k': 1.55}


def objet(id, fr, hs, vue):
    def deco(fn):
        OBJETS.append(dict(id=id, fr=fr, hs=hs, vue=vue, fp=FP_DEBOUT, build=fn))
        return fn
    return deco


@objet('dossier_classe', 'Dossier classé (Rare)', 0.7, {'roll': 0, 'yaw': -28, 'pitch': 18})
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


@objet('mallette_scellee', 'Mallette scellée (Épique)', 0.8, {'roll': 0, 'yaw': -32, 'pitch': 22})
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


@objet('disque_dur_militaire', 'Disque dur militaire (Légendaire)', 0.75, {'roll': 0, 'yaw': -34, 'pitch': 24})
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


@objet('boite_noire_helico', "Boîte noire d'hélicoptère (Légendaire)", 0.8, {'roll': 0, 'yaw': -34, 'pitch': 24})
def _(m):
    # enregistreur de vol (refait le 25/10, demande du propriétaire : « une vraie boîte noire ») : boîtier orange vif à
    # deux bandes blanches réfléchissantes, plaque « NE PAS OUVRIR », socle de fixation noir, poignée, balise de détresse
    # cylindrique à l'avant ; autre aspect que la boîte noire de la quête de Varga (heraliz_quetes)
    m.box((2.4, 1.0, 5.0), (13.4, 1.7, 11.0), 'black')                # socle de fixation
    for (x, z) in ((2.7, 5.3), (12.6, 5.3), (2.7, 10.2), (12.6, 10.2)):  # vis du socle
        m.box((x, 1.7, z), (x + 0.5, 1.95, z + 0.5), 'steel')
    m.box((3.0, 1.7, 5.5), (12.4, 9.3, 10.5), 'hazard')               # boîtier orange
    m.box((3.0, 9.3, 5.9), (12.4, 9.5, 10.1), 'hazard')               # dessus légèrement bombé
    for x in (4.3, 10.4):                                              # bandes blanches réfléchissantes (tout autour)
        m.box((x, 1.65, 5.42), (x + 0.75, 9.38, 10.58), 'white')
    m.box((5.5, 4.0, 10.5), (9.9, 7.4, 10.58), 'label_black')         # plaque « ENREGISTREUR DE VOL - NE PAS OUVRIR »
    m.box((5.5, 4.0, 5.42), (9.9, 7.4, 5.5), 'label_black')
    m.box((12.4, 1.7, 5.5), (13.0, 9.3, 10.5), 'black')               # face avant : cadre et connecteur
    m.box((13.0, 6.6, 6.9), (13.5, 8.2, 9.1), 'steel_dark')
    m.cyl('x', 13.0, 15.3, 3.8, 8.0, 1.0, 'aluminum', sides=12)       # balise de détresse sous-marine
    m.cyl('x', 15.3, 15.7, 3.8, 8.0, 0.75, 'black', sides=10)
    m.box((13.0, 2.6, 7.6), (14.6, 2.9, 8.4), 'black')                # collier de la balise
    for x in (5.6, 9.8):                                               # poignée de transport
        m.box((x, 9.5, 7.6), (x + 0.6, 10.9, 8.4), 'black')
    m.box((5.6, 10.9, 7.6), (10.4, 11.5, 8.4), 'black')


MODID = 'heraliz_evenements'
ASSETS = os.path.join(ICI, '..', 'src', 'client', 'resources', 'assets', MODID)


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def r4(v):
    return [round(float(x), 4) for x in v]


def display(hs, fp):
    """Mêmes tenues que les objets de quête (modules/pnj/outils/objets.py)."""
    s = lambda k: [round(k * hs, 4)] * 3  # noqa: E731
    hk = fp.get('k', 1.0)
    third = {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': s(0.55 * hk)}
    main = {'rotation': fp['rotation'], 'translation': fp['translation'], 'scale': s(0.68 * hk)}
    return {
        'thirdperson_righthand': third, 'thirdperson_lefthand': dict(third),
        'firstperson_righthand': main, 'firstperson_lefthand': dict(main),
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': s(0.5)},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': s(1.0)},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': s(1.0)},
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625] * 3},
    }


def model_json(m, hs, fp):
    used, elements = [], []
    for b in m.boxes:
        if b['m'] not in used:
            used.append(b['m'])
        e = {'from': r4(b['f']), 'to': r4(b['t']),
             'faces': {d: {'texture': '#' + b['m']} for d in ('north', 'east', 'south', 'west', 'up', 'down')}}
        if b['rot'] is not None:
            axis, ang, org = b['rot']
            e['rotation'] = {'angle': ang, 'axis': axis, 'origin': r4(org)}
        for v in e['from'] + e['to']:
            assert -16 <= v <= 32, v
        elements.append(e)
    tex = {name: f'{MODID}:item/mat/{name}' for name in used}
    tex['particle'] = tex[used[0]]
    return {'gui_light': 'side', 'textures': tex, 'elements': elements, 'display': display(hs, fp)}


def installer():
    mats = material_textures()
    for sub in ('models/item/3d', 'models/item/icon', 'textures/item/mat', 'textures/item/icon'):
        p = os.path.join(ASSETS, sub)
        if os.path.isdir(p):
            for f in os.listdir(p):
                os.remove(os.path.join(p, f))
    utilises = set()
    for o in OBJETS:
        m = Model()
        o['build'](m)
        m.recenter()
        utilises.update(b['m'] for b in m.boxes)
        iid = o['id']
        icone = render(m, mats, 32, **o['vue'])
        os.makedirs(os.path.join(ASSETS, 'textures', 'item', 'icon'), exist_ok=True)
        icone.save(os.path.join(ASSETS, 'textures', 'item', 'icon', iid + '.png'))
        wjson(os.path.join(ASSETS, 'models', 'item', '3d', iid + '.json'), model_json(m, o['hs'], o['fp']))
        wjson(os.path.join(ASSETS, 'models', 'item', 'icon', iid + '.json'),
              {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/icon/{iid}'}})
        wjson(os.path.join(ASSETS, 'models', 'item', iid + '.json'), {
            'loader': 'forge:separate_transforms',
            'base': {'parent': f'{MODID}:item/3d/{iid}'},
            'perspectives': {'gui': {'parent': f'{MODID}:item/icon/{iid}'}},
        })
    for name in sorted(utilises):
        p = os.path.join(ASSETS, 'textures', 'item', 'mat', name + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        Image.fromarray(mats[name].astype('uint8'), 'RGB').save(p)
    print(len(OBJETS), 'objets,', len(utilises), 'matières installés dans', os.path.normpath(ASSETS))


def police(nom, t):
    for f in (nom, 'arial.ttf', 'DejaVuSans.ttf'):
        try:
            return ImageFont.truetype(f, t)
        except OSError:
            pass
    return ImageFont.load_default()


def planche(chemin):
    mats = material_textures()
    ft = police('arialbd.ttf', 16)
    cw, ch = 372, 150
    sheet = Image.new('RGB', (20 + cw * len(OBJETS) + 20, ch + 60), (24, 26, 30))
    d = ImageDraw.Draw(sheet)
    d.text((20, 14), "Objets d'événement · icône (×3) · modèle 3D · 1re personne", fill=(235, 235, 235), font=ft)
    for i, o in enumerate(OBJETS):
        m = Model()
        o['build'](m)
        m.recenter()
        icon = render(m, mats, 32, **o['vue'])
        big = render(m, mats, 112, detail=1.0, outline=False, pad=5, **o['vue'])
        fpv = render(m, mats, 112, detail=1.0, outline=False, pad=5, M=fp_matrix(o['fp']['rotation']))
        x, y = 20 + i * cw, 46
        d.rounded_rectangle((x, y, x + cw - 12, y + ch), 8, fill=(36, 39, 45))
        slot = Image.new('RGB', (100, 100), (139, 139, 139))
        up = icon.resize((96, 96), Image.NEAREST)
        slot.paste(up, (2, 2), up)
        sheet.paste(slot, (x + 10, y + 10))
        sheet.paste(big, (x + 116, y + 4), big)
        d.rounded_rectangle((x + 232, y + 8, x + 348, y + 112), 6, fill=(58, 74, 92))
        sheet.paste(fpv, (x + 234, y + 4), fpv)
        d.text((x + 12, y + 122), o['fr'], fill=(240, 205, 110), font=ft)
    sheet.save(chemin)
    print('planche :', chemin)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == '--apercu':
        planche(sys.argv[2])
    elif len(sys.argv) >= 2 and sys.argv[1] == '--installer':
        installer()
    else:
        print(__doc__)
