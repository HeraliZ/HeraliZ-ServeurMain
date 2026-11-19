"""Police des symboles HeraliZ (symboles.ttf du noyau).

DM Sans et Onest n'ont pas les symboles des messages (☢ ⚠ ❄ ✔ ✖ ☠ ★…) : Minecraft les prenait dans sa propre police,
dont la ligne de base est plus basse que celle de DM Sans (retour du propriétaire du 19/11 : « le logo de la zone
irradiée est un peu trop bas »). Cette police les dessine avec les mesures de DM Sans (ascendante 992, descendante
-310, capitales 700, 1000 unités) : posée dans nos polices juste après DM Sans et Onest, à la même taille, chaque
symbole tombe sur la même ligne de base, centré sur les capitales, et reste net (tracé comme une lettre).

Les contours qui se chevauchent sont permis (même sens : réunis par le moteur des polices) ; un trou est un contour en
sens inverse posé dans une seule forme pleine.

    python modules/noyau/outils/symboles_police.py            écrit la police
    python modules/noyau/outils/symboles_police.py --apercu   et une image de contrôle (texte de DM Sans + symboles)
"""
import math
import os
import sys

from fontTools.fontBuilder import FontBuilder
from fontTools.pens.ttGlyphPen import TTGlyphPen

ICI = os.path.dirname(os.path.abspath(__file__))
POLICES = os.path.join(ICI, "..", "src", "client", "resources", "assets", "heraliz_noyau", "font")
SORTIE = os.path.join(POLICES, "symboles.ttf")

ASC, DESC, CAP, XH = 992, -310, 700, 504
MILIEU = CAP / 2  # les symboles sont centrés sur les capitales


# --- formes (listes de points en unités de police, y vers le haut) ---------------------------------------------------

def sens(points, horaire=True):
    """Remet un contour dans le sens voulu (horaire : forme pleine ; anti-horaire : trou)."""
    aire = 0.0
    for (x1, y1), (x2, y2) in zip(points, points[1:] + points[:1]):
        aire += x1 * y2 - x2 * y1
    est_horaire = aire < 0
    return list(points) if est_horaire == horaire else list(reversed(points))


def cercle(cx, cy, r, n=48):
    return [(cx + r * math.cos(2 * math.pi * i / n), cy + r * math.sin(2 * math.pi * i / n)) for i in range(n)]


def secteur(cx, cy, r1, r2, a1, a2, n=16):
    """Anneau entre r1 et r2, des angles a1 à a2 (degrés)."""
    ext = [(cx + r2 * math.cos(math.radians(a1 + (a2 - a1) * i / n)),
            cy + r2 * math.sin(math.radians(a1 + (a2 - a1) * i / n))) for i in range(n + 1)]
    inte = [(cx + r1 * math.cos(math.radians(a2 - (a2 - a1) * i / n)),
             cy + r1 * math.sin(math.radians(a2 - (a2 - a1) * i / n))) for i in range(n + 1)]
    return ext + inte


def barre(x1, y1, x2, y2, ep, bouts=0.0):
    """Trait épais de (x1, y1) à (x2, y2), prolongé de « bouts » à chaque extrémité."""
    dx, dy = x2 - x1, y2 - y1
    l = math.hypot(dx, dy)
    ux, uy = dx / l, dy / l
    x1, y1, x2, y2 = x1 - ux * bouts, y1 - uy * bouts, x2 + ux * bouts, y2 + uy * bouts
    nx, ny = -uy * ep / 2, ux * ep / 2
    return [(x1 + nx, y1 + ny), (x2 + nx, y2 + ny), (x2 - nx, y2 - ny), (x1 - nx, y1 - ny)]


def rect_arrondi(x1, y1, x2, y2, r, n=6):
    pts = []
    for cx, cy, a0 in ((x2 - r, y2 - r, 0), (x1 + r, y2 - r, 90), (x1 + r, y1 + r, 180), (x2 - r, y1 + r, 270)):
        for i in range(n + 1):
            a = math.radians(a0 + 90 * i / n)
            pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def etoile(cx, cy, r_ext, r_int, branches, depart=90):
    pts = []
    for i in range(branches * 2):
        r = r_ext if i % 2 == 0 else r_int
        a = math.radians(depart + 180 * i / branches)
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    return pts


def poli(points, r=40, n=4):
    """Coins arrondis d'un polygone convexe (triangle d'alerte, flèches)."""
    sortie = []
    k = len(points)
    for i in range(k):
        p0, p1, p2 = points[i - 1], points[i], points[(i + 1) % k]
        v1 = (p0[0] - p1[0], p0[1] - p1[1])
        v2 = (p2[0] - p1[0], p2[1] - p1[1])
        l1, l2 = math.hypot(*v1), math.hypot(*v2)
        v1, v2 = (v1[0] / l1, v1[1] / l1), (v2[0] / l2, v2[1] / l2)
        a = math.acos(max(-1.0, min(1.0, v1[0] * v2[0] + v1[1] * v2[1])))
        d = r / math.tan(a / 2)
        q1 = (p1[0] + v1[0] * d, p1[1] + v1[1] * d)
        q2 = (p1[0] + v2[0] * d, p1[1] + v2[1] * d)
        for j in range(n + 1):
            t = j / n
            # courbe de Bézier quadratique q1 → p1 → q2, échantillonnée
            x = (1 - t) ** 2 * q1[0] + 2 * (1 - t) * t * p1[0] + t ** 2 * q2[0]
            y = (1 - t) ** 2 * q1[1] + 2 * (1 - t) * t * p1[1] + t ** 2 * q2[1]
            sortie.append((x, y))
    return sortie


# --- les symboles ----------------------------------------------------------------------------------------------------
# Chaque symbole : (largeur d'avance, [(points, plein?)]). Les formes sont dessinées entre x = 0 et la largeur.

def radioactif():
    R, cx = 405, 465
    formes = [(cercle(cx, MILIEU, 0.23 * R), True)]
    for a in (30, 150, 270):
        formes.append((secteur(cx, MILIEU, 0.36 * R, R, a - 30, a + 30), True))
    return 930, formes


def attention():
    l, bas, haut = 920, -30, 745
    tri = poli([(60, bas), (60 + l / 2, haut), (60 + l, bas)], r=55)
    cx = 60 + l / 2
    formes = [(tri, True),
              (rect_arrondi(cx - 52, 225, cx + 52, 545, 52), False),
              (cercle(cx, 125, 60, 24), False)]
    return 1040, formes


def flocon():
    R, cx, ep = 420, 460, 92
    formes = []
    for k in range(3):
        a = math.radians(90 + 60 * k)
        formes.append((barre(cx - R * math.cos(a), MILIEU - R * math.sin(a), cx + R * math.cos(a),
                             MILIEU + R * math.sin(a), ep), True))
    for k in range(6):
        a = math.radians(90 + 60 * k)
        for t, l in ((0.52, 0.30), (0.80, 0.22)):
            bx, by = cx + R * t * math.cos(a), MILIEU + R * t * math.sin(a)
            for s in (-1, 1):
                b = a + s * math.radians(45)
                formes.append((barre(bx, by, bx + R * l * math.cos(b), by + R * l * math.sin(b), ep * 0.8), True))
    return 920, formes


def coche(ep):
    p1, p2, p3 = (90, 360), (330, 90), (800, 690)
    return 880, [(barre(*p1, *p2, ep, ep / 2), True), (barre(*p2, *p3, ep, ep / 2), True)]


def croix(ep):
    m, c = 110, 700 - 110
    return 800, [(barre(m + 50, m - 60, c + 50, c + 60, ep), True), (barre(c + 50, m - 60, m + 50, c + 60, ep), True)]


def crane():
    cx = 450
    formes = [(cercle(cx, 450, 300, 40), True),
              (rect_arrondi(cx - 170, 30, cx + 170, 300, 60), True),
              (cercle(cx - 118, 440, 78, 24), False),
              (cercle(cx + 118, 440, 78, 24), False),
              (sens([(cx, 360), (cx - 45, 285), (cx + 45, 285)], False), False)]
    # dents : deux fentes dans la mâchoire, sous le nez
    for dx in (-60, 60):
        formes.append((rect_arrondi(cx + dx - 18, 60, cx + dx + 18, 200, 14, 3), False))
    return 900, formes


def etoile5():
    return 920, [(etoile(460, 330, 430, 180, 5), True)]


def etoile4():
    pts = []
    for i in range(4):
        a = math.radians(90 * i)
        pts.append((460 + 430 * math.cos(a), MILIEU + 430 * math.sin(a)))
        # côtés creusés : 5 points sur une courbe vers le centre
        for j in range(1, 6):
            t = j / 6
            b = a + math.radians(90) * t
            r = 430 * (0.24 + 0.76 * abs(1 - 2 * t) ** 2.2)
            pts.append((460 + r * math.cos(b), MILIEU + r * math.sin(b)))
    return 920, [(pts, True)]


def triangle(direction):
    h = 640
    if direction == "haut":
        pts = [(80, MILIEU - h / 2), (430, MILIEU + h / 2), (780, MILIEU - h / 2)]
    elif direction == "bas":
        pts = [(80, MILIEU + h / 2), (780, MILIEU + h / 2), (430, MILIEU - h / 2)]
    elif direction == "droite":
        pts = [(110, MILIEU - h / 2), (110, MILIEU + h / 2), (110 + 0.87 * h, MILIEU)]
    else:
        pts = [(110 + 0.87 * h, MILIEU - h / 2), (110, MILIEU), (110 + 0.87 * h, MILIEU + h / 2)]
    return 860 if direction in ("haut", "bas") else 780, [(poli(pts, 30), True)]


def carre():
    return 760, [(rect_arrondi(90, MILIEU - 290, 670, MILIEU + 290, 40), True)]


def rond():
    return 800, [(cercle(400, MILIEU, 310), True)]


def losange():
    return 840, [(poli([(420, MILIEU + 400), (790, MILIEU), (420, MILIEU - 400), (50, MILIEU)], 30), True)]


SYMBOLES = {
    0x2622: ("radioactif", radioactif),
    0x26A0: ("attention", attention),
    0x2744: ("flocon", flocon),
    0x2714: ("coche_grasse", lambda: coche(165)),
    0x2713: ("coche", lambda: coche(105)),
    0x2705: ("coche_carre", lambda: coche(165)),
    0x2716: ("croix_grasse", lambda: croix(160)),
    0x2715: ("croix", lambda: croix(100)),
    0x2620: ("crane", crane),
    0x2605: ("etoile", etoile5),
    0x2726: ("etoile4", etoile4),
    0x25B2: ("triangle_haut", lambda: triangle("haut")),
    0x25BC: ("triangle_bas", lambda: triangle("bas")),
    0x25B6: ("triangle_droite", lambda: triangle("droite")),
    0x25C0: ("triangle_gauche", lambda: triangle("gauche")),
    0x25A0: ("carre", carre),
    0x25CF: ("rond", rond),
    0x25C6: ("losange", losange),
}


def fabriquer(sortie=SORTIE):
    noms = [".notdef", "space"] + [n for n, _ in SYMBOLES.values()]
    fb = FontBuilder(1000, isTTF=True)
    fb.setupGlyphOrder(noms)
    fb.setupCharacterMap({0x20: "space", **{cp: n for cp, (n, _) in SYMBOLES.items()}})
    glyphes, mesures = {}, {}
    vide = TTGlyphPen(None)
    glyphes[".notdef"] = vide.glyph()
    mesures[".notdef"] = (500, 0)
    glyphes["space"] = TTGlyphPen(None).glyph()
    mesures["space"] = (260, 0)
    for cp, (nom, f) in SYMBOLES.items():
        avance, formes = f()
        stylo = TTGlyphPen(None)
        gauche = 10 ** 9
        for points, plein in formes:
            points = sens([(round(x), round(y)) for x, y in points], plein)
            # points en double (arrondis) retirés : un contour propre
            propres = [p for i, p in enumerate(points) if p != points[i - 1]]
            stylo.moveTo(propres[0])
            for p in propres[1:]:
                stylo.lineTo(p)
            stylo.closePath()
            gauche = min(gauche, min(x for x, _ in propres))
        glyphes[nom] = stylo.glyph()
        mesures[nom] = (avance, gauche)
    fb.setupGlyf(glyphes)
    fb.setupHorizontalMetrics(mesures)
    fb.setupHorizontalHeader(ascent=ASC, descent=DESC, lineGap=0)
    fb.setupNameTable({"familyName": "HeraliZ Symboles", "styleName": "Regular", "psName": "HeraliZSymboles-Regular",
                       "manufacturer": "HeraliZ", "designer": "HeraliZ"})
    fb.setupOS2(sTypoAscender=ASC, sTypoDescender=DESC, sTypoLineGap=0, usWinAscent=ASC, usWinDescent=-DESC,
                sCapHeight=CAP, sxHeight=XH, achVendID="HRLZ")
    fb.setupPost()
    fb.save(sortie)
    return sortie


def apercu(chemin):
    from PIL import Image, ImageDraw, ImageFont
    taille = 96
    texte_police = ImageFont.truetype(os.path.join(POLICES, "dmsans_bold.ttf"), taille)
    symb_police = ImageFont.truetype(SORTIE, taille)
    lignes = [("☢", " Zone irradiée : combinaison Hazmat"), ("⚠", " Zone de gaz : masque obligatoire"),
              ("❄", " Froid intense"), ("✔", " Réanimé  ✖ Exécuté  ✓ ✕"), ("☠", " Base raidée  ★ ✦ ▲ ▼ ▶ ◀ ■ ● ◆")]
    image = Image.new("RGB", (2400, 140 * len(lignes) + 40), (20, 22, 28))
    d = ImageDraw.Draw(image)
    for i, (symbole, texte) in enumerate(lignes):
        y = 40 + 140 * i
        base = y + texte_police.getmetrics()[0]
        d.line([(30, base), (2370, base)], fill=(70, 70, 90))
        d.line([(30, base - CAP * taille / 1000), (2370, base - CAP * taille / 1000)], fill=(50, 50, 70))
        x = 40
        for morceau in [symbole] + list(texte):
            police = symb_police if ord(morceau) in SYMBOLES else texte_police
            # même ligne de base : DM Sans et nos symboles ont la même ascendante
            d.text((x, y), morceau, font=police, fill=(240, 200, 60) if police is symb_police else (235, 235, 240))
            x += police.getlength(morceau)
    image.save(chemin)
    return chemin


if __name__ == "__main__":
    print("police :", os.path.normpath(fabriquer()))
    if "--apercu" in sys.argv:
        i = sys.argv.index("--apercu")
        chemin = sys.argv[i + 1] if len(sys.argv) > i + 1 else "symboles_apercu.png"
        print("aperçu :", apercu(chemin))
