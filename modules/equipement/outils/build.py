"""Genere tous les assets du mod + une planche d'apercu.
Usage : python build.py <dossier du projet> [<planche.png>]"""
import json
import os
import shutil
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
from engine import Model, fp_matrix, material_textures, render  # noqa: E402
from composants import CATEGORIES, ITEMS  # noqa: E402

MODID = 'heraliz_composants'
ROOT = os.path.abspath(sys.argv[1])
SHEET = sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, 'apercus', 'apercu_composants.png')
# projet coupé (CLAUDE.md section 4) : assets/ côté client, data/ côté serveur, ComponentDefs.java dans commun
ASSETS = os.path.join(ROOT, 'src', 'client', 'resources', 'assets', MODID)
DATA = os.path.join(ROOT, 'src', 'serveur', 'resources', 'data', MODID)

# code couleur : gris, vert, bleu, violet, orange, rouge (voir HeralizRarity.java)
RARITY = {r: 'HeralizRarity.' + r.upper() for r in ('common', 'uncommon', 'rare', 'epic', 'legendary', 'mythic')}


def wjson(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)


def r4(v):
    return [round(float(x), 4) for x in v]


FP_DEFAULT = {'rotation': [0, -90, 25], 'translation': [1.13, 3.2, 1.13]}


def display(hs, fp=None, tp=None):
    fp = fp or FP_DEFAULT
    s = lambda k: [round(k * hs, 4)] * 3  # noqa: E731
    hk = fp.get('k', 1.0)  # agrandissement en main selon la pose
    # tenue vue par les autres joueurs (3e personne), reglable par item
    tp = tp or {'rotation': [0, 0, 0], 'translation': [0, 3, 1], 'scale': 0.55 * hk}
    third = {'rotation': tp['rotation'], 'translation': tp['translation'], 'scale': s(tp['scale'])}
    left = dict(third)
    if tp['rotation'] != [0, 0, 0]:
        # le jeu inverse les rotations Y et Z en main gauche : on les pre-inverse pour garder la meme tenue
        r = tp['rotation']
        left['rotation'] = [r[0], -r[1], -r[2]]
    return {
        'thirdperson_righthand': third,
        'thirdperson_lefthand': left,
        'firstperson_righthand': {'rotation': fp['rotation'], 'translation': fp['translation'], 'scale': s(0.68 * hk)},
        'firstperson_lefthand': {'rotation': fp['rotation'], 'translation': fp['translation'], 'scale': s(0.68 * hk)},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': s(0.5)},
        'head': {'rotation': [0, 180, 0], 'translation': [0, 13, 7], 'scale': s(1.0)},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': s(1.0)},
        'gui': {'rotation': [30, 225, 0], 'translation': [0, 0, 0], 'scale': [0.625] * 3},
    }


def model_json(m, hs, fp=None, tp=None):
    used = []
    elements = []
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
    return {'gui_light': 'side', 'textures': tex, 'elements': elements, 'display': display(hs, fp, tp)}


def main():
    for sub in ('textures/item', 'models/item'):
        p = os.path.join(ASSETS, sub)
        if os.path.isdir(p):
            shutil.rmtree(p)
    # lang/ : fr_fr et en_us sont réécrits plus bas ; ru_ru.json (traduit à part) est gardé
    if os.path.isdir(DATA):
        shutil.rmtree(DATA)

    mats = material_textures()
    used_mats = set()
    previews = []
    built = []
    for it in ITEMS:
        m = Model()
        it['build'](m)
        m.recenter()
        used_mats.update(b['m'] for b in m.boxes)
        built.append((it, m))

    for name in used_mats:
        p = os.path.join(ASSETS, 'textures', 'item', 'mat', name + '.png')
        os.makedirs(os.path.dirname(p), exist_ok=True)
        Image.fromarray(mats[name].astype('uint8'), 'RGB').save(p)
    icon_dir = os.path.join(ASSETS, 'textures', 'item', 'icon')
    os.makedirs(icon_dir, exist_ok=True)

    fr = {f'itemGroup.{MODID}': 'HeraliZ — Composants'}
    en = {f'itemGroup.{MODID}': 'HeraliZ — Components'}
    for c, (cfr, cen) in CATEGORIES.items():
        fr[f'tooltip.{MODID}.cat.{c}'] = cfr
        en[f'tooltip.{MODID}.cat.{c}'] = cen
    fr[f'tooltip.{MODID}.used_for'] = 'Utilisé pour : %s'
    en[f'tooltip.{MODID}.used_for'] = 'Used for: %s'
    fr[f'tooltip.{MODID}.sell'] = 'Sans utilité · se revend'
    en[f'tooltip.{MODID}.sell'] = 'Useless · can be sold'

    tags = {}
    java_rows = []
    for it, m in built:
        iid = it['id']
        icon = render(m, mats, 32, **it['view'])
        icon.save(os.path.join(icon_dir, iid + '.png'))
        big = render(m, mats, 112, detail=1.0, outline=False, pad=5, **it['view'])
        fp_rot = (it.get('fp') or FP_DEFAULT)['rotation']
        fpv = render(m, mats, 112, detail=1.0, outline=False, pad=5, M=fp_matrix(fp_rot))
        previews.append((it, icon, big, fpv))

        wjson(os.path.join(ASSETS, 'models', 'item', '3d', iid + '.json'), model_json(m, it['hs'], it.get('fp'), it.get('tp')))
        wjson(os.path.join(ASSETS, 'models', 'item', 'icon', iid + '.json'),
              {'parent': 'minecraft:item/generated', 'textures': {'layer0': f'{MODID}:item/icon/{iid}'}})
        wjson(os.path.join(ASSETS, 'models', 'item', iid + '.json'), {
            'loader': 'forge:separate_transforms',
            'base': {'parent': f'{MODID}:item/3d/{iid}'},
            'perspectives': {'gui': {'parent': f'{MODID}:item/icon/{iid}'}},
        })
        fr[f'item.{MODID}.{iid}'] = it['fr']
        en[f'item.{MODID}.{iid}'] = it['en']
        fr[f'item.{MODID}.{iid}.desc'] = it['desc_fr']
        en[f'item.{MODID}.{iid}.desc'] = it['desc_en']
        for c in it['cats']:
            tags.setdefault(c, []).append(f'{MODID}:{iid}')
        tags.setdefault('tous', []).append(f'{MODID}:{iid}')
        java_rows.append(f'            new Def("{iid}", "{",".join(it["cats"])}", '
                         f'{RARITY[it["rarity"]]}, {it["stack"]})')
        print(f'{iid:18s} {len(m.boxes):4d} cubes')

    wjson(os.path.join(ASSETS, 'lang', 'fr_fr.json'), fr)
    wjson(os.path.join(ASSETS, 'lang', 'en_us.json'), en)
    for k, vals in tags.items():
        wjson(os.path.join(DATA, 'tags', 'items', k + '.json'), {'replace': False, 'values': vals})

    java = os.path.join(ROOT, 'src', 'commun', 'java', 'fr', 'heraliz', 'composants', 'ComponentDefs.java')
    with open(java, 'w', encoding='utf-8', newline='\n') as f:
        f.write('package fr.heraliz.composants;\n\n'
                'import net.minecraft.world.item.Rarity;\n\n'
                'import java.util.List;\n\n'
                '// Genere par tools/build.py, ne pas modifier a la main.\n'
                'public final class ComponentDefs {\n'
                '    /** categories : liste separee par des virgules (armes, soins, viseurs, munitions, divers, vente). */\n'
                '    public record Def(String id, String categories, Rarity rarity, int stack) {}\n\n'
                '    public static final List<Def> DEFS = List.of(\n'
                + ',\n'.join(java_rows) + '\n    );\n\n'
                '    private ComponentDefs() {}\n}\n')

    ecrire_raretes()
    make_sheet(previews)


# rareté des composants pour HeraliZ Loot (fond de la case, ligne de rareté, couleur du nom) : section « objets » de
# serveur/config/heraliz_loot/raretes.json, clés heraliz_composants:* (le reste du fichier est gardé)
RARETES_JSON = os.path.join(ROOT, '..', '..', 'serveur', 'config', 'heraliz_loot', 'raretes.json')
RARETE_LOOT = {'common': 'commun', 'uncommon': 'peu_commun', 'rare': 'rare', 'epic': 'epique', 'legendary': 'legendaire',
               'mythic': 'unique'}


def ecrire_raretes():
    from collections import OrderedDict
    if not os.path.exists(RARETES_JSON):
        print('raretes.json absent : raretés des composants non écrites')
        return
    with open(RARETES_JSON, encoding='utf-8') as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    obj = OrderedDict((k, v) for k, v in data.get('objets', {}).items() if not k.startswith(MODID + ':'))
    for it in ITEMS:
        obj[f'{MODID}:{it["id"]}'] = RARETE_LOOT[it['rarity']]
    data['objets'] = obj
    with open(RARETES_JSON, 'w', encoding='utf-8', newline='\n') as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write('\n')


def make_sheet(previews):
    try:
        ft = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 16)
        fs = ImageFont.truetype('C:/Windows/Fonts/segoeui.ttf', 13)
        fh = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 24)
        fc = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 19)
    except OSError:
        ft = fs = fh = fc = ImageFont.load_default()
    per_row, cw, ch = 4, 372, 168
    groups = []
    for c in CATEGORIES:
        its = [p for p in previews if p[0]['cats'][0] == c]
        if its:
            groups.append((c, its))
    W = 20 + per_row * cw + 20
    H = 80 + sum(44 + ((len(its) + per_row - 1) // per_row) * ch for _, its in groups) + 10
    sheet = Image.new('RGB', (W, H), (24, 26, 30))
    d = ImageDraw.Draw(sheet)
    d.text((20, 16), f'HeraliZ Composants — {len(previews)} items', fill=(235, 235, 235), font=fh)
    d.text((20, 50), 'Icône inventaire (32 px ×3) · modèle 3D · aperçu approximatif en 1re personne',
           fill=(150, 160, 175), font=fs)

    def slot_img(icon):
        s = Image.new('RGB', (100, 100), (139, 139, 139))
        sd = ImageDraw.Draw(s)
        sd.rectangle((0, 0, 99, 1), fill=(55, 55, 55))
        sd.rectangle((0, 0, 1, 99), fill=(55, 55, 55))
        sd.rectangle((0, 98, 99, 99), fill=(255, 255, 255))
        sd.rectangle((98, 0, 99, 99), fill=(255, 255, 255))
        up = icon.resize((96, 96), Image.NEAREST)
        s.paste(up, (2, 2), up)
        return s

    y = 80
    for c, its in groups:
        d.text((20, y + 8), CATEGORIES[c][0].upper(), fill=(235, 235, 235), font=fc)
        d.line((20, y + 36, W - 20, y + 36), fill=(60, 64, 72))
        y += 44
        for i, (it, icon, big, fpv) in enumerate(its):
            x = 20 + (i % per_row) * cw
            yy = y + (i // per_row) * ch
            d.rounded_rectangle((x, yy + 4, x + cw - 12, yy + ch - 4), 8, fill=(36, 39, 45))
            sheet.paste(slot_img(icon), (x + 10, yy + 12))
            sheet.paste(big, (x + 116, yy + 6), big)
            d.rounded_rectangle((x + 232, yy + 10, x + 348, yy + 114), 6, fill=(58, 74, 92))
            sheet.paste(fpv, (x + 234, yy + 6), fpv)
            d.text((x + 238, yy + 96), '1re personne', fill=(170, 185, 200), font=fs)
            d.text((x + 12, yy + 120), it['fr'], fill=(235, 235, 235), font=ft)
            extra = ' + '.join(CATEGORIES[k][0] for k in it['cats'][1:])
            d.text((x + 12, yy + 142), it['id'] + (f'  · aussi : {extra}' if extra else ''),
                   fill=(140, 150, 165), font=fs)
        y += ((len(its) + per_row - 1) // per_row) * ch
    sheet.save(SHEET)
    print('planche :', SHEET)


if __name__ == '__main__':
    main()
