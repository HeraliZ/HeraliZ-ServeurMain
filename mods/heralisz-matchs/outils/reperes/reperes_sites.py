"""Repères des sites A et B des duels (recherche et destruction) : petit carré fin et lettre fine, en blanc, teintés en jeu.

Écrit src/client/resources/assets/heralisz_matchs/textures/gui/duel_repere.png (+ .mcmeta : filtrage lisse) et, avec
--apercu FICHIER, une planche d'aperçu (repères de l'attaque, de la défense, discrets, près du viseur, bombe amorcée,
sur un faux écran de jeu à l'échelle d'interface 3).

Choix du propriétaire (16/10, Matchs 0.10.0) : l'ancien badge (bulle avec pointe et distance) était trop gros. Repère =
petit carré de 10 px d'interface, contour de 1 px, fond presque transparent, lettre fine au centre ; ni pointe, ni
distance, ni badge.

Planche 256 x 32, cinq cases de 32 x 32 sur une ligne (coordonnées lues par DuelHud) :
  x 0   : contour du carré (trait de 3 texels = 1 px d'interface environ, coins à peine arrondis)
  x 32  : fond du carré (plein, teinté sombre et très transparent en jeu)
  x 64  : lettre A      x 96 : lettre B      x 128 : « ! » (bombe amorcée, ordinateur au sol)
Dessiné 8 fois plus grand puis réduit (bords lisses). Lettres en DM Sans Medium (police des interfaces).

Usage : python mods/heralisz-matchs/outils/reperes/reperes_sites.py [--apercu planche.png] [--sortie fichier.png]
"""
import argparse
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ICI = os.path.dirname(os.path.abspath(__file__))
PROJET = os.path.normpath(os.path.join(ICI, "..", ".."))
ASSETS = os.path.join(PROJET, "src", "client", "resources", "assets", "heralisz_matchs")
SORTIE = os.path.join(ASSETS, "textures", "gui", "duel_repere.png")
POLICE = os.path.join(ASSETS, "font", "dmsans_medium.ttf")
POLICE_GRAS = os.path.join(ASSETS, "font", "dmsans_bold.ttf")

S = 8  # sur-échantillonnage
CASE = 32
CASES = 5
MARGE = 1.0  # texels laissés autour du carré (filtrage)
TRAIT = 3.0  # épaisseur du contour, en texels (1 px d'interface pour un repère de 10 px)
RAYON = 2.5  # coins
HAUTEUR_LETTRE = 0.50  # hauteur des capitales, part de la case


def carre(plein):
    im = Image.new("L", (CASE * S, CASE * S), 0)
    d = ImageDraw.Draw(im)
    a, b = MARGE * S, (CASE - MARGE) * S - 1
    d.rounded_rectangle((a, a, b, b), radius=RAYON * S, fill=255)
    if not plein:
        t = TRAIT * S
        d.rounded_rectangle((a + t, a + t, b - t, b - t), radius=max(1, (RAYON - TRAIT * 0.6) * S), fill=0)
    return im


def lettre(texte):
    """Lettre centrée (par sa boîte réelle), capitales à HAUTEUR_LETTRE de la case."""
    im = Image.new("L", (CASE * S, CASE * S), 0)
    d = ImageDraw.Draw(im)
    taille = 100
    f = ImageFont.truetype(POLICE, taille)
    x0, y0, x1, y1 = d.textbbox((0, 0), "H", font=f)
    taille = int(taille * HAUTEUR_LETTRE * CASE * S / (y1 - y0))
    f = ImageFont.truetype(POLICE, taille)
    x0, y0, x1, y1 = d.textbbox((0, 0), texte, font=f)
    cx, cy = CASE * S / 2, CASE * S / 2
    d.text((cx - (x0 + x1) / 2, cy - (y0 + y1) / 2), texte, font=f, fill=255)
    return im


def reduire(im):
    return im.resize((CASE, CASE), Image.LANCZOS)


def planche():
    out = Image.new("RGBA", (256, 32), (255, 255, 255, 0))
    masques = [carre(False), carre(True), lettre("A"), lettre("B"), lettre("!")]
    for i, m in enumerate(masques):
        case = Image.new("RGBA", (CASE, CASE), (255, 255, 255, 0))
        case.putalpha(reduire(m))
        out.paste(case, (i * CASE, 0))
    return out


def teinter(case, rgb, alpha):
    a = case.split()[3].point(lambda v: v * alpha // 255)
    im = Image.new("RGBA", case.size, rgb + (0,))
    im.putalpha(a)
    return im


def mix(c, d, t):
    return tuple(int(c[i] + (d[i] - c[i]) * t) for i in range(3))


# mêmes réglages que DuelHud (à garder alignés)
TAILLE = 10  # px d'interface
ATTAQUE, DEFENSE, DANGER = (0xE8, 0xB3, 0x4A), (0x6F, 0xA8, 0xFF), (0xFF, 0x6B, 0x6B)
ATTENUE = 0.45  # couleur de l'équipe mêlée de gris clair
GRIS = (0xC8, 0xCC, 0xD2)


def repere(fond, sheet, cx, cy, lettre_i, coul, a, echelle):
    cases = [sheet.crop((i * CASE, 0, (i + 1) * CASE, CASE)) for i in range(CASES)]
    c = mix(coul, GRIS, ATTENUE)
    calque = Image.new("RGBA", (CASE, CASE), (0, 0, 0, 0))
    calque.alpha_composite(teinter(cases[1], (0x0D, 0x0F, 0x14), int(255 * 0.18 * a)))
    calque.alpha_composite(teinter(cases[0], c, int(255 * a)))
    calque.alpha_composite(teinter(cases[lettre_i], mix(c, (255, 255, 255), 0.35), int(255 * min(1, a * 1.1))))
    px = int(round(TAILLE * echelle))
    grand = calque.resize((px, px), Image.BILINEAR)
    fond.alpha_composite(grand, (int(cx - px / 2), int(cy - px / 2)))


def faux_ecran(sheet, W=960, H=540, ech=3):
    """Faux écran de jeu (960 x 540, échelle d'interface 3 : 320 x 180 px d'interface) : rue, bâtiments, un adversaire
    près du viseur, repères des sites A et B, viseur au centre."""
    fond = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(fond)
    for y in range(H):
        t = y / H
        c = (int(150 + 50 * (1 - t)), int(172 + 40 * (1 - t)), int(190 + 30 * (1 - t))) if t < 0.5 else (int(96 - 40 * t), int(90 - 36 * t), int(78 - 30 * t))
        d.line([(0, y), (W, y)], fill=c)
    for i, (x, w, h, c) in enumerate([(20, 180, 210, (120, 112, 98)), (230, 120, 160, (138, 128, 110)), (610, 140, 190, (110, 104, 96)),
                                      (780, 170, 230, (128, 118, 102))]):
        d.rectangle((x, H // 2 - h + 40, x + w, H // 2 + 60), fill=c)
        for k in range(3):
            d.rectangle((x + 20 + k * 45, H // 2 - h + 70, x + 45 + k * 45, H // 2 - h + 100), fill=(60, 62, 66))
    # caisse et bombe au loin (site A), muret (site B)
    d.rectangle((640, 300, 700, 330), fill=(70, 72, 60))
    d.rectangle((250, 315, 330, 335), fill=(90, 86, 80))
    fond = fond.filter(ImageFilter.GaussianBlur(1.2))
    d = ImageDraw.Draw(fond)
    # adversaire juste à côté du viseur
    ax, ay = W // 2 + 14, H // 2 + 6
    d.rectangle((ax - 6, ay - 28, ax + 6, ay - 16), fill=(196, 160, 130))
    d.rectangle((ax - 9, ay - 16, ax + 9, ay + 14), fill=(54, 70, 52))
    d.rectangle((ax - 8, ay + 14, ax + 8, ay + 40), fill=(44, 48, 56))
    fond = fond.convert("RGBA")
    # repères : A presque sur le viseur (très pâle), B loin du viseur (discret), « ! » d'une bombe amorcée
    repere(fond, sheet, W // 2 + 22, H // 2 - 30, 2, ATTAQUE, alpha_hud(0.0, 0.40, 22 / ech + 0, 30 / ech), ech)
    repere(fond, sheet, 290, 300, 3, ATTAQUE, alpha_hud(0.0, 0.40, (W / 2 - 290) / ech, (H / 2 - 300) / ech), ech)
    # viseur simple
    dd = ImageDraw.Draw(fond)
    for dx, dy in [(-12, 0), (12, 0), (0, -12), (0, 12)]:
        dd.line([(W // 2 + dx * 0.5, H // 2 + dy * 0.5), (W // 2 + dx, H // 2 + dy)], fill=(255, 255, 255, 230), width=2)
    return fond


def alpha_hud(force, base, ex, ey):
    """Copie de DuelHud.opacite : base (discret 0,40 ; bien visible 0,62), atténué près du viseur."""
    import math
    a = base + (0.62 - base) * force
    r = math.hypot(ex, ey)
    t = 0 if r <= 10 else 1 if r >= 48 else (r - 10) / 38
    t = t * t * (3 - 2 * t)
    return a * (0.22 + 0.78 * t)


def apercu(sheet, chemin):
    """Planche : en haut les repères en grand (x8) et à l'échelle 3 sur fond clair et sombre ; en bas un faux écran."""
    W = 960
    haut = Image.new("RGBA", (W, 230), (32, 34, 38, 255))
    d = ImageDraw.Draw(haut)
    d.rectangle((W // 2, 0, W, 230), fill=(176, 190, 200, 255))
    f = ImageFont.truetype(POLICE_GRAS, 16)
    etats = [(2, ATTAQUE, 0.62, "attaque"), (3, DEFENSE, 0.62, "défense"), (2, ATTAQUE, 0.40, "discret"),
             (3, DEFENSE, 0.40 * 0.22, "au viseur"), (4, DANGER, 0.55, "bombe")]
    for moitie in range(2):
        for k, (li, coul, a, leg) in enumerate(etats):
            x = moitie * W // 2 + 50 + k * 92
            repere(haut, sheet, x, 60, li, coul, a, 8)
            repere(haut, sheet, x, 150, li, coul, a, 3)
            d.text((x, 200), leg, font=ImageFont.truetype(POLICE_GRAS, 11), fill=(255, 255, 255, 255) if moitie == 0 else (20, 20, 20, 255),
                   anchor="mm")
    d.text((10, 8), "x8", font=f, fill=(200, 200, 200, 255))
    d.text((10, 128), "x3 (taille réelle à l'échelle d'interface 3)", font=f, fill=(200, 200, 200, 255))
    ecran = faux_ecran(sheet)
    out = Image.new("RGBA", (W, 230 + ecran.height), (0, 0, 0, 255))
    out.alpha_composite(haut, (0, 0))
    out.alpha_composite(ecran, (0, 230))
    out.convert("RGB").save(chemin)


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
