"""Repères des sites A et B des duels (recherche et destruction) : formes du badge, en blanc, teintées en jeu.

Écrit src/client/resources/assets/heralisz_matchs/textures/gui/duel_repere.png (+ .mcmeta : filtrage lisse) et, avec
--apercu FICHIER, une planche d'aperçu (badges de l'attaque, de la défense, discret, bombe posée, sur fond de jeu).

Planche 256 x 128, trois cases de 64 x 80 sur la première ligne (coordonnées lues par DuelHud.Repere) :
  x 0   : fond (badge plein, teinté verre sombre en jeu)
  x 64  : bord (anneau de 4 px, teinté couleur de l'équipe)
  x 128 : reflet (haut du badge, blanc dégradé : effet verre)
Le badge : carré aux coins très arrondis de 56 px (de y 4 à y 60) et une pointe vers le bas (jusqu'à y 76) qui montre
le site. Dessiné 4 fois plus grand puis réduit (bords lisses).

Usage : python mods/heralisz-matchs/outils/reperes/reperes_sites.py [--apercu planche.png]
"""
import argparse
import os

from PIL import Image, ImageDraw, ImageFilter

ICI = os.path.dirname(os.path.abspath(__file__))
PROJET = os.path.normpath(os.path.join(ICI, "..", ".."))
SORTIE = os.path.join(PROJET, "src", "client", "resources", "assets", "heralisz_matchs", "textures", "gui", "duel_repere.png")

S = 4  # sur-échantillonnage
CASE_L, CASE_H = 64, 80
BADGE = (4, 4, 60, 60)  # x0, y0, x1, y1 dans la case
RAYON = 15
POINTE_BAS = 76
POINTE_DEMI = 9
BORD = 4


def forme(epaisseur=None):
    """Masque du badge (plein, ou anneau d'épaisseur donnée), en grand."""
    def dessiner(d, retrait, remplir):
        x0, y0, x1, y1 = [v * S for v in BADGE]
        r = RAYON * S
        x0 += retrait; y0 += retrait; x1 -= retrait; y1 -= retrait
        rr = max(1, r - retrait)
        d.rounded_rectangle((x0, y0, x1, y1), radius=rr, fill=remplir)
        cx = (BADGE[0] + BADGE[2]) / 2 * S
        demi = POINTE_DEMI * S - retrait * 1.4
        bas = POINTE_BAS * S - retrait * 2.2
        haut = y1 - r * 0.6
        if demi > 0 and bas > haut:
            d.polygon([(cx - demi, haut), (cx + demi, haut), (cx, bas)], fill=remplir)

    im = Image.new("L", (CASE_L * S, CASE_H * S), 0)
    d = ImageDraw.Draw(im)
    dessiner(d, 0, 255)
    if epaisseur is not None:
        trou = Image.new("L", im.size, 0)
        dessiner(ImageDraw.Draw(trou), epaisseur * S, 255)
        im = Image.composite(Image.new("L", im.size, 0), im, trou)
    return im


def reduire(im):
    return im.resize((CASE_L, CASE_H), Image.LANCZOS)


def reflet():
    """Haut du badge en blanc dégradé (fort en haut, nul à mi-hauteur), dans le bord."""
    plein = forme()
    interieur = Image.new("L", plein.size, 0)
    x0, y0, x1, y1 = [v * S for v in BADGE]
    d = ImageDraw.Draw(interieur)
    m = (BORD + 1) * S
    d.rounded_rectangle((x0 + m, y0 + m, x1 - m, y1 - m), radius=(RAYON - BORD) * S, fill=255)
    deg = Image.new("L", plein.size, 0)
    h = (y1 - y0) * 0.55
    for y in range(int(y0), int(y0 + h)):
        v = int(255 * (1 - (y - y0) / h) ** 1.6)
        ImageDraw.Draw(deg).line([(0, y), (deg.width, y)], fill=v)
    masque = Image.composite(deg, Image.new("L", plein.size, 0), interieur)
    return masque


def planche():
    out = Image.new("RGBA", (256, 128), (255, 255, 255, 0))
    for i, masque in enumerate([forme(), forme(BORD), reflet()]):
        a = reduire(masque)
        case = Image.new("RGBA", (CASE_L, CASE_H), (255, 255, 255, 0))
        case.putalpha(a)
        out.paste(case, (i * CASE_L, 0))
    return out


def teinter(case, rgb, alpha):
    r, g, b = rgb
    a = case.split()[3].point(lambda v: v * alpha // 255)
    im = Image.new("RGBA", case.size, (r, g, b, 0))
    im.putalpha(a)
    return im


def apercu(sheet, chemin):
    """Planche d'aperçu : 4 états sur un fond de jeu flou, à l'échelle 3 (comme en jeu en échelle d'interface 3)."""
    from PIL import ImageFont
    polices = os.path.join(PROJET, "src", "client", "resources", "assets", "heralisz_matchs", "font")
    gras = None
    for nom in os.listdir(polices) if os.path.isdir(polices) else []:
        if "bold" in nom.lower() and nom.endswith(".ttf") and "cyril" not in nom.lower():
            gras = os.path.join(polices, nom)
    W, H = 1100, 380
    fond = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(fond)
    for y in range(H):  # ciel et sol, flous
        t = y / H
        c = (int(120 + 60 * (1 - t)), int(150 + 50 * (1 - t)), int(170 + 40 * (1 - t))) if t < 0.55 else (int(92 - 30 * t), int(84 - 25 * t), int(66 - 20 * t))
        d.line([(0, y), (W, y)], fill=c)
    for x in range(0, W, 140):
        d.rectangle((x + 20, 120, x + 90, 215), fill=(150, 140, 120))
    fond = fond.filter(ImageFilter.GaussianBlur(6)).convert("RGBA")
    cases = [sheet.crop((i * CASE_L, 0, (i + 1) * CASE_L, CASE_H)) for i in range(3)]
    etats = [("A", (0xE8, 0xB3, 0x4A), 1.0, "27 m", "Attaque"), ("B", (0x6F, 0xA8, 0xFF), 1.0, "41 m", "Défense"),
             ("A", (0xE8, 0xB3, 0x4A), 0.4, None, "Discret (après 8 s)"), ("!", (0xFF, 0x6B, 0x6B), 1.0, "12 m", "Bombe amorcée")]
    ech = 3 * 22 / CASE_L  # badge de 22 px d'interface, échelle d'interface 3
    for k, (lettre, coul, force, dist, legende) in enumerate(etats):
        cx = 140 + k * 270
        cy = 150
        calque = Image.new("RGBA", (CASE_L, CASE_H), (0, 0, 0, 0))
        calque.alpha_composite(teinter(cases[0], (0x0D, 0x0F, 0x14), int(200 * force)))
        calque.alpha_composite(teinter(cases[1], coul, int(235 * force)))
        calque.alpha_composite(teinter(cases[2], (255, 255, 255), int(40 * force)))
        grand = calque.resize((int(CASE_L * ech), int(CASE_H * ech)), Image.BILINEAR)
        x0, y0 = int(cx - grand.width / 2), int(cy - grand.height * (32 / CASE_H))
        fond.alpha_composite(grand, (x0, y0))
        dd = ImageDraw.Draw(fond)
        if gras:
            f = ImageFont.truetype(gras, int(13 * 3))
            clair = tuple(int(c + (255 - c) * 0.55) for c in coul)
            dd.text((cx, cy), lettre, font=f, fill=clair + (int(255 * max(force, 0.5)),), anchor="mm")
            if dist:
                f2 = ImageFont.truetype(gras, int(8 * 3))
                dd.text((cx, y0 + grand.height + 14), dist, font=f2, fill=(236, 238, 242, 230), anchor="mm",
                        stroke_width=2, stroke_fill=(0, 0, 0, 120))
            f3 = ImageFont.truetype(gras, 22)
            dd.text((cx, 330), legende, font=f3, fill=(255, 255, 255, 255), anchor="mm", stroke_width=2, stroke_fill=(0, 0, 0, 160))
    fond.convert("RGB").save(chemin)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apercu")
    ap.add_argument("--sortie", default=SORTIE)
    a = ap.parse_args()
    sheet = planche()
    os.makedirs(os.path.dirname(a.sortie), exist_ok=True)
    sheet.save(a.sortie, optimize=True)
    with open(a.sortie + ".mcmeta", "w", encoding="utf-8", newline="\n") as f:
        f.write('{\n  "texture": {\n    "blur": true,\n    "clamp": true\n  }\n}\n')
    print("écrit", a.sortie)
    if a.apercu:
        apercu(sheet, a.apercu)
        print("aperçu", a.apercu)


if __name__ == "__main__":
    main()
