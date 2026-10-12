"""Atlas des icônes de la carte du monde (HeraliZ Utils · Carte) : formes blanches sur fond transparent, teintées par
l'écran au dessin. Tracés repris de la maquette validée (cadre arrondi, sortie, avion, danger, éclair, drapeau...).

    python mods/heralisz-utils/outils/icones_carte.py

Écrit src/client/resources/assets/heraliz_carte/textures/gui/icones.png : cases de 64 px, 8 par ligne, dans l'ordre de
NOMS (le même que fr.heraliz.carte.client.Icones). Dessin 4 fois plus grand puis réduit : bords lisses.
"""
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ICI = Path(__file__).resolve().parent.parent
SORTIE = ICI / "src/client/resources/assets/heraliz_carte/textures/gui/icones.png"
CASE, SUR, COLONNES = 64, 4, 8
NOMS = ["cadre_fond", "cadre_bord", "sortie", "avion", "danger", "eclair", "drapeau", "usine",
        "ancre", "croix", "train", "etoile", "losange", "fleche", "disque", "anneau",
        "halo", "nord", "maison", "cible"]


class Pinceau:
    """Coordonnées de la maquette : boîte de -16 à 16, (0, 0) au centre de la case."""

    def __init__(self):
        self.t = CASE * SUR
        self.img = Image.new("L", (self.t, self.t), 0)
        self.d = ImageDraw.Draw(self.img)

    def p(self, x, y):
        k = self.t / 36.0  # 36 unités par case : un peu de marge autour de la boîte de 32
        return (self.t / 2 + x * k, self.t / 2 + y * k)

    def ep(self, e):
        return max(1, int(round(e * self.t / 36.0)))

    def trait(self, pts, e=2.4):
        q = [self.p(*a) for a in pts]
        w = self.ep(e)
        self.d.line(q, fill=255, width=w, joint="curve")
        r = w / 2
        for (x, y) in (q[0], q[-1]):
            self.d.ellipse((x - r, y - r, x + r, y + r), fill=255)

    def plein(self, pts):
        self.d.polygon([self.p(*a) for a in pts], fill=255)

    def rond(self, cx, cy, r, plein=True, e=2.4):
        (x0, y0), (x1, y1) = self.p(cx - r, cy - r), self.p(cx + r, cy + r)
        if plein:
            self.d.ellipse((x0, y0, x1, y1), fill=255)
        else:
            self.d.ellipse((x0, y0, x1, y1), outline=255, width=self.ep(e))

    def boite(self, r, plein, e=2.0):
        (x0, y0), (x1, y1) = self.p(-16, -16), self.p(16, 16)
        rr = r * self.t / 36.0
        if plein:
            self.d.rounded_rectangle((x0, y0, x1, y1), radius=rr, fill=255)
        else:
            w = self.ep(e)
            self.d.rounded_rectangle((x0 + w / 2, y0 + w / 2, x1 - w / 2, y1 - w / 2), radius=rr, outline=255, width=w)

    def finir(self):
        return self.img.resize((CASE, CASE), Image.LANCZOS)


def dessiner(nom):
    c = Pinceau()
    if nom == "cadre_fond":
        c.boite(5, True)
    elif nom == "cadre_bord":
        c.boite(5, False, 2.0)
    elif nom == "sortie":
        c.trait([(-3, -9), (8, -9), (8, 9), (-3, 9)])
        c.trait([(-9, 0), (4, 0)])
        c.trait([(0, -4), (4, 0), (0, 4)])
    elif nom == "avion":
        c.plein([(0, -10), (2, -3), (10, 1), (10, 3), (2, 1), (1, 7), (4, 9), (4, 10), (0, 9), (-4, 10), (-4, 9), (-1, 7),
                 (-2, 1), (-10, 3), (-10, 1), (-2, -3)])
    elif nom == "danger":
        c.trait([(0, -10), (10, 8), (-10, 8), (0, -10)])
        c.trait([(0, -3), (0, 3)])
        c.rond(0, 6, 1.3)
    elif nom == "eclair":
        c.plein([(2, -10), (-6, 2), (0, 2), (-2, 10), (6, -2), (0, -2)])
    elif nom == "drapeau":
        c.trait([(-6, 10), (-6, -10)])
        c.trait([(-6, -9), (8, -9), (4, -4), (8, 1), (-6, 1)])
    elif nom == "usine":
        c.plein([(-10, 9), (-10, -2), (-4, 2), (-4, -2), (2, 2), (2, -9), (6, -9), (6, 2), (10, 2), (10, 9)])
    elif nom == "ancre":
        c.rond(0, -8, 2.2, False, 2.2)
        c.trait([(0, -5.5), (0, 9)])
        c.trait([(-5, -2), (5, -2)])
        c.trait([(-9, 3), (-7, 7), (-2, 9.5), (0, 9), (2, 9.5), (7, 7), (9, 3)])
    elif nom == "croix":
        c.plein([(-3, -10), (3, -10), (3, -3), (10, -3), (10, 3), (3, 3), (3, 10), (-3, 10), (-3, 3), (-10, 3), (-10, -3),
                 (-3, -3)])
    elif nom == "train":
        c.d.rounded_rectangle((*c.p(-7, -10), *c.p(7, 6)), radius=3 * c.t / 36.0, fill=255)
        c.d.rectangle((*c.p(-4.5, -7.5), *c.p(4.5, -1.5)), fill=0)
        c.rond(-3.5, 2.5, 1.5)
        c.rond(3.5, 2.5, 1.5)
        c.d.ellipse((*c.p(-5, 1), *c.p(-2, 4)), fill=0)
        c.d.ellipse((*c.p(2, 1), *c.p(5, 4)), fill=0)
        c.trait([(-5, 7), (-8, 10)], 2.0)
        c.trait([(5, 7), (8, 10)], 2.0)
    elif nom == "etoile":
        pts = []
        for i in range(10):
            a = -math.pi / 2 + i * math.pi / 5
            r = 10.5 if i % 2 == 0 else 4.4
            pts.append((r * math.cos(a), r * math.sin(a) + 0.8))
        c.plein(pts)
    elif nom == "losange":
        c.plein([(0, -15), (13, 0), (0, 15), (-13, 0)])
    elif nom == "fleche":
        # flèche du joueur (pointe en haut, encoche en bas), comme la maquette
        c.plein([(0, -16), (11.5, 13), (0, 6.5), (-11.5, 13)])
    elif nom == "disque":
        c.rond(0, 0, 16)
    elif nom == "anneau":
        c.rond(0, 0, 15, False, 2.6)
    elif nom == "halo":
        img = Image.new("L", (c.t, c.t), 0)
        d = ImageDraw.Draw(img)
        n = 48
        for i in range(n):
            r = c.t / 2 * (1 - i / n)
            v = int(255 * (i / n) ** 1.6)
            d.ellipse((c.t / 2 - r, c.t / 2 - r, c.t / 2 + r, c.t / 2 + r), fill=v)
        c.img = img.filter(ImageFilter.GaussianBlur(c.t / 40))
    elif nom == "nord":
        c.plein([(0, -15), (6, 4), (0, 0), (-6, 4)])
    elif nom == "maison":
        c.plein([(0, -10), (10, -1), (7, -1), (7, 9), (-7, 9), (-7, -1), (-10, -1)])
    elif nom == "cible":
        c.rond(0, 0, 9, False, 2.2)
        c.rond(0, 0, 2.5)
        for (a, b) in (((0, -14), (0, -10)), ((0, 10), (0, 14)), ((-14, 0), (-10, 0)), ((10, 0), (14, 0))):
            c.trait([a, b], 2.2)
    return c.finir()


def main():
    lignes = -(-len(NOMS) // COLONNES)
    atlas = Image.new("RGBA", (COLONNES * CASE, lignes * CASE), (255, 255, 255, 0))
    for i, nom in enumerate(NOMS):
        alpha = dessiner(nom)
        case = Image.new("RGBA", (CASE, CASE), (255, 255, 255, 0))
        case.putalpha(alpha)
        atlas.paste(case, ((i % COLONNES) * CASE, (i // COLONNES) * CASE))
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    atlas.save(SORTIE, optimize=True)
    print("[icones] %d icônes -> %s (%d x %d)" % (len(NOMS), SORTIE, atlas.width, atlas.height))


if __name__ == "__main__":
    main()
