"""Variantes de modele pour la poudre a canon (choix du joueur).
Usage : python variantes_poudre.py <planche.png>"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from PIL import Image, ImageDraw, ImageFont  # noqa: E402
from engine import Model, material_textures, render  # noqa: E402

FRONT = {'roll': 0, 'yaw': -28, 'pitch': 22}


def bocal(m):
    m.cyl('y', 0, 0.6, 8, 8, 3.6, 'jar_glass')
    m.cyl('y', 0.6, 7.6, 8, 8, 3.7, 'powder')
    m.cyl('y', 7.6, 9.4, 8, 8, 3.7, 'jar_glass')
    m.cyl('y', 9.4, 10.2, 8, 8, 3.0, 'jar_glass')
    m.cyl('y', 10.2, 12.0, 8, 8, 3.25, 'steel_dark')
    m.box((5.2, 3.0, 11.68), (10.8, 6.6, 11.74), 'white')
    m.box((6.6, 3.6, 11.72), (9.4, 6.0, 11.78), 'hazard', ('z', 45, (8, 4.8, 11.75)))


def bidon(m):
    m.box((3, 0, 5), (13, 11, 11), 'red')
    m.box((3.4, 11, 5.4), (12.6, 11.8, 10.6), 'red')
    m.cyl('y', 11.8, 13.6, 10.2, 8, 1.6, 'black')
    m.box((4.0, 2.0, 11.0), (12.0, 9.0, 11.06), 'white')
    m.box((6.2, 3.6, 11.06), (9.8, 7.2, 11.12), 'hazard', ('z', 45, (8, 5.4, 11.1)))
    m.box((7.6, 5.0, 11.12), (8.4, 5.8, 11.16), 'black')
    m.box((4.2, 11.8, 7.4), (7.8, 13.0, 8.6), 'black')


def corne(m):
    # corne courbe : segments de rayon decroissant
    m.cyl('x', 0, 1.2, 7.0, 8, 2.9, 'brass')
    m.cyl('x', 1.2, 5.0, 7.0, 8, 2.7, 'bone')
    m.obox((6.6, 7.4, 8), (4.0, 4.2, 4.2), 'bone', 'z', 22.5)
    m.obox((9.6, 8.8, 8), (3.6, 3.4, 3.4), 'bone', 'z', 22.5)
    m.obox((12.0, 10.6, 8), (3.2, 2.6, 2.6), 'bone', 'z', 45)
    m.obox((13.6, 12.6, 8), (2.4, 1.8, 1.8), 'bone', 'z', 45)
    m.obox((14.6, 13.8, 8), (1.6, 1.4, 1.4), 'brass', 'z', 45)
    m.cyl('x', 1.6, 2.4, 7.0, 8, 2.8, 'wood')
    m.cyl('x', 4.6, 5.2, 7.0, 8, 2.8, 'wood')
    m.box((2.0, 4.2, 7.6), (14.0, 4.6, 8.4), 'wood', ('z', 22.5, (8, 4.4, 8)))


def tonnelet(m):
    m.cyl('y', 0, 1.6, 8, 8, 4.3, 'wood')
    m.cyl('y', 1.6, 9.4, 8, 8, 4.8, 'wood')
    m.cyl('y', 9.4, 11.0, 8, 8, 4.3, 'wood')
    for y in (0.6, 2.2, 8.2, 9.8):
        m.cyl('y', y, y + 0.7, 8, 8, 4.95 if 1 < y < 9 else 4.45, 'steel_dark')
    m.cyl('y', 11.0, 11.2, 8, 8, 3.9, 'wood_raw')
    m.cyl('y', 11.2, 11.8, 9.4, 8, 0.7, 'black')
    m.box((5.4, 3.8, 12.72), (10.6, 7.2, 12.78), 'black')
    m.box((6.6, 4.4, 12.76), (9.4, 6.6, 12.82), 'hazard', ('z', 45, (8, 5.5, 12.8)))


VARIANTS = [('A', 'Bocal de poudre', bocal), ('B', 'Bidon rouge', bidon),
            ('C', 'Corne à poudre', corne), ('D', 'Tonnelet', tonnelet)]


def main(out):
    mats = material_textures()
    try:
        ft = ImageFont.truetype('C:/Windows/Fonts/segoeuib.ttf', 20)
    except OSError:
        ft = ImageFont.load_default()
    cw, ch = 330, 230
    sheet = Image.new('RGB', (20 + cw * len(VARIANTS) + 10, ch + 30), (24, 26, 30))
    d = ImageDraw.Draw(sheet)
    for i, (k, name, fn) in enumerate(VARIANTS):
        m = Model()
        fn(m)
        m.recenter()
        icon = render(m, mats, 32, **FRONT)
        big = render(m, mats, 150, detail=1.0, outline=False, pad=6, **FRONT)
        x = 20 + i * cw
        d.rounded_rectangle((x, 20, x + cw - 14, 20 + ch - 6), 10, fill=(36, 39, 45))
        slot = Image.new('RGB', (100, 100), (139, 139, 139))
        up = icon.resize((96, 96), Image.NEAREST)
        slot.paste(up, (2, 2), up)
        sheet.paste(slot, (x + 12, 40))
        sheet.paste(big, (x + 160, 28), big)
        d.text((x + 14, 190), f'{k}. {name}', fill=(235, 235, 235), font=ft)
    sheet.save(out)
    print(out)


if __name__ == '__main__':
    main(sys.argv[1])
