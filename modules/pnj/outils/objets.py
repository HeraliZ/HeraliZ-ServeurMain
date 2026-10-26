"""Objets de quête de HeraliZ Quêtes en 3D, au même style que les composants (même moteur : outils/engine.py de
heraliz-composants). Ils ne copient aucun composant : ce sont des objets propres aux quêtes.

  python modules/pnj/outils/objets.py --installer            modèles, matières et icônes dans le mod
  python modules/pnj/outils/objets.py --apercu planche.png    planche d'aperçu (icône, modèle, 1re personne)

On modifie ce script, jamais les fichiers écrits (models/item/3d, models/item/icon, textures/item/mat,
textures/item/icon et models/item/<id>.json de ces objets).

Modèles de profil : X = longueur, Y = hauteur, Z = épaisseur (centrée sur 8)."""
import json
import os
import sys

ICI = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(ICI, '..', '..', 'heraliz-composants', 'outils'))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
from engine import Model, fp_matrix, material_textures, render  # noqa: E402

OBJETS = []

FP_PLAT = {'rotation': [55, 0, 33], 'translation': [-1, 6.5, 0], 'k': 1.5}
FP_DEBOUT = {'rotation': [-25, -30, 0], 'translation': [-1, 6.5, 0], 'k': 1.55}


def objet(id, fr, desc, hs, vue, fp):
    def deco(fn):
        OBJETS.append(dict(id=id, fr=fr, desc=desc, hs=hs, vue=vue, fp=fp, build=fn))
        return fn
    return deco


@objet('insigne_regiment', 'Insigne du 7e régiment',
       "Écusson de métal de l'unité du Commandant Varga. Arraché à un uniforme.",
       0.5, {'roll': 0, 'yaw': -22, 'pitch': 12}, FP_DEBOUT)
def _(m):
    # écu : corps, pointe en bas, bordure de laiton
    m.box((4.4, 5.0, 7.5), (11.6, 12.0, 8.5), 'brass_worn')
    m.box((5.4, 3.4, 7.5), (10.6, 5.0, 8.5), 'brass_worn')
    m.box((6.6, 2.2, 7.5), (9.4, 3.4, 8.5), 'brass_worn')
    m.box((5.0, 5.4, 8.5), (11.0, 11.4, 8.62), 'enamel')
    m.box((6.0, 3.9, 8.5), (10.0, 5.4, 8.62), 'enamel')
    m.box((7.2, 2.8, 8.5), (8.8, 3.9, 8.62), 'enamel')
    # bande noire et chiffre romain VII en laiton
    m.box((5.0, 8.6, 8.62), (11.0, 9.6, 8.66), 'black')
    for x in (6.6, 8.4, 9.4):
        m.box((x, 5.6, 8.62), (x + 0.5, 8.0, 8.7), 'gold')
    m.box((5.8, 5.6, 8.62), (6.3, 8.0, 8.7), 'gold', ('z', 22.5, (6.05, 6.8, 8.66)))
    m.box((6.9, 5.6, 8.62), (7.4, 8.0, 8.7), 'gold', ('z', -22.5, (7.15, 6.8, 8.66)))
    # étoile du haut
    m.box((7.4, 10.0, 8.62), (8.6, 11.0, 8.7), 'gold')
    m.box((7.7, 9.7, 8.62), (8.3, 11.3, 8.7), 'gold')
    # attache au dos
    m.box((6.5, 9.0, 7.2), (9.5, 9.6, 7.5), 'steel_dark')


@objet('boite_noire', 'Boîte noire',
       "Enregistreur de vol de l'hélicoptère abattu. Ce qu'il a entendu intéresse le Commandant.",
       0.8, {'roll': 0, 'yaw': -32, 'pitch': 24}, FP_DEBOUT)
def _(m):
    # corps orange, coins arrondis
    m.box((3.4, 1.0, 5.4), (12.6, 8.4, 10.6), 'hazard')
    m.box((3.0, 1.4, 5.8), (13.0, 8.0, 10.2), 'hazard')
    # embouts noirs
    m.box((2.6, 1.2, 5.6), (3.4, 8.2, 10.4), 'black')
    m.box((12.6, 1.2, 5.6), (13.4, 8.2, 10.4), 'black')
    # bandes réfléchissantes
    for y in (2.4, 6.4):
        m.box((3.4, y, 10.6), (12.6, y + 0.7, 10.66), 'white')
    # plaque et inscription
    m.box((5.4, 3.6, 10.6), (10.6, 5.8, 10.7), 'aluminum')
    for (x1, x2) in ((5.9, 7.4), (7.8, 10.1)):
        m.box((x1, 4.9, 10.7), (x2, 5.3, 10.74), 'chip')
    m.box((5.9, 4.1, 10.7), (9.4, 4.4, 10.74), 'chip')
    # poignée et connecteur
    m.box((5.6, 8.4, 7.4), (6.2, 9.8, 8.6), 'black')
    m.box((9.8, 8.4, 7.4), (10.4, 9.8, 8.6), 'black')
    m.box((5.6, 9.8, 7.4), (10.4, 10.4, 8.6), 'black')
    m.cyl('x', 13.4, 14.2, 4.6, 8.0, 0.9, 'steel_dark')
    # traces de brûlé
    m.box((3.6, 1.0, 10.6), (5.0, 2.2, 10.64), 'chip')
    m.box((11.2, 7.0, 10.6), (12.4, 8.4, 10.64), 'chip')


@objet('sacoche_deserteurs', 'Sacoche des déserteurs',
       "Ce que les déserteurs ont volé au camp, encore bouclé. Le Commandant veut le récupérer.",
       0.75, {'roll': 0, 'yaw': -30, 'pitch': 22}, FP_DEBOUT)
def _(m):
    # sac de toile
    m.box((3.4, 1.0, 6.0), (12.6, 8.4, 10.2), 'olive')
    m.box((3.0, 1.4, 6.4), (13.0, 8.0, 9.8), 'olive')
    # rabat
    m.box((3.2, 6.0, 10.2), (12.8, 8.8, 10.6), 'cordura')
    m.box((3.2, 8.4, 6.2), (12.8, 8.8, 10.6), 'cordura')
    # sangles de cuir et boucles
    for x in (5.2, 9.8):
        m.box((x, 3.4, 10.2), (x + 1.0, 8.9, 10.7), 'leather')
        m.box((x - 0.15, 4.0, 10.7), (x + 1.15, 5.0, 10.82), 'brass')
        m.box((x + 0.25, 4.25, 10.82), (x + 0.75, 4.75, 10.86), 'leather')
    # poignée
    m.box((6.6, 8.8, 7.8), (7.1, 10.2, 8.6), 'leather')
    m.box((8.9, 8.8, 7.8), (9.4, 10.2, 8.6), 'leather')
    m.box((6.6, 10.2, 7.8), (9.4, 10.7, 8.6), 'leather')
    # marquage peint
    m.box((4.0, 1.8, 10.2), (6.4, 2.6, 10.26), 'white')
    m.box((6.8, 1.8, 10.2), (7.6, 2.6, 10.26), 'white')


MODID = 'heraliz_quetes'
ASSETS = os.path.join(ICI, '..', 'src', 'client', 'resources', 'assets', MODID)


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def r4(v):
    return [round(float(x), 4) for x in v]


def display(hs, fp):
    """Mêmes tenues que les composants (heraliz-composants/outils/build.py)."""
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


def planche(chemin):
    mats = material_textures()
    try:
        ft = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf', 16)
        fs = ImageFont.truetype('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf', 12)
    except OSError:
        ft = fs = ImageFont.load_default()
    cw, ch = 372, 190
    sheet = Image.new('RGB', (20 + cw * len(OBJETS) + 20, ch + 60), (24, 26, 30))
    d = ImageDraw.Draw(sheet)
    d.text((20, 14), 'Objets de quête · icône (×3) · modèle 3D · 1re personne', fill=(235, 235, 235), font=ft)
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
        mots, ligne, ly = o['desc'].split(), '', y + 146
        for mot in mots:
            if d.textlength(ligne + ' ' + mot, font=fs) > cw - 30:
                d.text((x + 12, ly), ligne, fill=(160, 170, 185), font=fs)
                ligne, ly = mot, ly + 15
            else:
                ligne = (ligne + ' ' + mot).strip()
        d.text((x + 12, ly), ligne, fill=(160, 170, 185), font=fs)
    sheet.save(chemin)
    print('planche :', chemin)


if __name__ == '__main__':
    if len(sys.argv) >= 3 and sys.argv[1] == '--apercu':
        planche(sys.argv[2])
    elif len(sys.argv) >= 2 and sys.argv[1] == '--installer':
        installer()
    else:
        print(__doc__)
