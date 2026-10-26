"""Atlas des icônes de la carte du monde (HeraliZ Utils · Carte) : formes blanches sur fond transparent, teintées par
l'écran au dessin. Style « trait fin » (Utils 2.14.1) : pictogrammes au trait de 2 unités sur 36, coins et bouts
arrondis, posés sur une petite pastille ronde de verre (pastille, pastille_bord) avec une ombre douce (ombre), à la
place de l'ancien cadre carré à gros bord.

    python modules/monde/outils/icones_carte.py [--planche FICHIER.png]

Écrit src/client/resources/assets/heraliz_carte/textures/gui/icones.png : trois niveaux de détail l'un sous l'autre,
cases de 128, 64 et 32 px, 8 par ligne, dans l'ordre de NOMS (le même que fr.heraliz.carte.client.Icones, qui prend le
niveau le plus proche de la taille affichée : jamais plus de 2 fois plus petit, donc pas de crénelage au filtrage).
Chaque niveau est réduit directement depuis un dessin 8 fois plus grand que la grande case, par moyenne de surface
(couverture exacte, bords lisses sans halo ni « ringing »). La couleur est blanche partout, seul l'alpha change : aucune
frange sombre au filtrage linéaire.

Repère de dessin : boîte de -18 à 18 (36 unités par case), (0, 0) au centre. Pictogrammes dans ±10 (comme avant),
formes pleines de la carte (losange, flèche, disque, nord) jusqu'à ±16.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ICI = Path(__file__).resolve().parent.parent
SORTIE = ICI / "src/client/resources/assets/heraliz_carte/textures/gui/icones.png"
CASES = (128, 64, 32)
SUR, COLONNES = 8, 8
NOMS = ["pastille", "pastille_bord", "ombre", "sortie", "avion", "danger", "eclair", "drapeau",
        "usine", "ancre", "croix", "train", "etoile", "losange", "fleche", "disque",
        "anneau", "halo", "nord", "maison", "cible", "helico", "epave"]
TRAIT = 2.0  # épaisseur des pictogrammes (unités sur 36)


class Pinceau:
    def __init__(self):
        self.t = CASES[0] * SUR
        self.img = Image.new("L", (self.t, self.t), 0)
        self.d = ImageDraw.Draw(self.img)

    def k(self):
        return self.t / 36.0

    def p(self, x, y):
        return (self.t / 2 + x * self.k(), self.t / 2 + y * self.k())

    def ep(self, e):
        return max(1, int(round(e * self.k())))

    def _bouts(self, q, w, fill=255):
        r = w / 2
        for (x, y) in q:
            self.d.ellipse((x - r, y - r, x + r, y + r), fill=fill)

    def trait(self, pts, e=TRAIT, ferme=False, fill=255):
        """Trait à bouts et coins ronds (chaque sommet reçoit un disque : jonctions sans pointe ni trou)."""
        q = [self.p(*a) for a in pts]
        if ferme:
            q = q + [q[0]]
        w = self.ep(e)
        self.d.line(q, fill=fill, width=w)
        self._bouts(q, w, fill)

    def plein(self, pts, arrondi=0.0, fill=255):
        """Polygone plein ; arrondi > 0 : coins adoucis par un trait rond de cette épaisseur sur le bord."""
        q = [self.p(*a) for a in pts]
        self.d.polygon(q, fill=fill)
        if arrondi > 0:
            self.trait(list(pts), arrondi, ferme=True, fill=fill)

    def rond(self, cx, cy, r, plein=True, e=TRAIT, fill=255):
        (x0, y0), (x1, y1) = self.p(cx - r, cy - r), self.p(cx + r, cy + r)
        if plein:
            self.d.ellipse((x0, y0, x1, y1), fill=fill)
        else:
            w = self.ep(e)
            h = w / 2
            self.d.ellipse((x0 - h, y0 - h, x1 + h, y1 + h), outline=fill, width=w)

    def arc(self, cx, cy, r, a0, a1, e=TRAIT):
        """Arc de cercle (degrés, 0 = droite, sens horaire à l'écran), bouts ronds."""
        n = max(8, int(abs(a1 - a0) / 4))
        pts = [(cx + r * math.cos(math.radians(a0 + (a1 - a0) * i / n)), cy + r * math.sin(math.radians(a0 + (a1 - a0) * i / n)))
               for i in range(n + 1)]
        self.trait(pts, e)

    def boite(self, x0, y0, x1, y1, r, plein=True, e=TRAIT, fill=255):
        (a, b), (c, d) = self.p(x0, y0), self.p(x1, y1)
        rr = r * self.k()
        if plein:
            self.d.rounded_rectangle((a, b, c, d), radius=rr, fill=fill)
        else:
            w = self.ep(e)
            h = w / 2
            self.d.rounded_rectangle((a - h, b - h, c + h, d + h), radius=rr + h, outline=fill, width=w)


def dessiner(nom):
    c = Pinceau()
    if nom == "pastille":
        c.rond(0, 0, 15.2)
    elif nom == "pastille_bord":
        c.rond(0, 0, 14.6, False, 1.25)
    elif nom == "ombre":
        c.rond(0, 1.2, 14.5)
        c.img = c.img.filter(ImageFilter.GaussianBlur(c.k() * 1.9))
    elif nom == "sortie":
        # porte ouverte (crochet arrondi) et flèche qui en sort
        c.trait([(-2.5, -9), (6, -9), (8.5, -6.5), (8.5, 6.5), (6, 9), (-2.5, 9)])
        c.trait([(-9.5, 0), (3, 0)])
        c.trait([(-1.5, -4.5), (3, 0), (-1.5, 4.5)])
    elif nom == "avion":
        # silhouette fine, ailes en flèche
        c.plein([(0, -10), (1.5, -7.5), (1.6, -2.6), (10, 2.2), (10, 4), (1.6, 1.6), (1.1, 6.6), (4.2, 8.8), (4.2, 10),
                 (0, 9), (-4.2, 10), (-4.2, 8.8), (-1.1, 6.6), (-1.6, 1.6), (-10, 4), (-10, 2.2), (-1.6, -2.6), (-1.5, -7.5)], 0.9)
    elif nom == "danger":
        c.trait([(0, -9.5), (10, 8), (-10, 8)], ferme=True)
        c.trait([(0, -2.5), (0, 2.5)])
        c.rond(0, 5.4, 1.25)
    elif nom == "eclair":
        c.plein([(2.5, -10), (-6, 1.8), (-0.6, 1.8), (-2.5, 10), (6, -1.8), (0.6, -1.8)], 1.0)
    elif nom == "drapeau":
        c.trait([(-6.5, 10), (-6.5, -9.5)])
        c.plein([(-6.5, -9.2), (8, -9.2), (4.4, -4.6), (8, 0), (-6.5, 0)], 1.2)
    elif nom == "usine":
        # bâtiment à toit en dents de scie et cheminée, au trait
        c.trait([(-10, 9), (-10, -1), (-4.5, 2.5), (-4.5, -1), (1, 2.5), (1, -9), (5.5, -9), (5.5, 2.5), (10, 2.5), (10, 9)],
                ferme=True)
        c.trait([(-6.5, 5.8), (-4.5, 5.8)], 1.8)
        c.trait([(-1, 5.8), (1, 5.8)], 1.8)
        c.trait([(4.5, 5.8), (6.5, 5.8)], 1.8)
    elif nom == "ancre":
        c.rond(0, -7.6, 2.1, False)
        c.trait([(0, -5.4), (0, 9.2)])
        c.trait([(-4.5, -1.8), (4.5, -1.8)])
        c.arc(0, 1.2, 8.2, 25, 155)
        c.trait([(-9.4, 3.2), (-7.4, 4.8)])
        c.trait([(9.4, 3.2), (7.4, 4.8)])
    elif nom == "croix":
        c.plein([(-2.4, -9), (2.4, -9), (2.4, -2.4), (9, -2.4), (9, 2.4), (2.4, 2.4), (2.4, 9), (-2.4, 9), (-2.4, 2.4),
                 (-9, 2.4), (-9, -2.4), (-2.4, -2.4)], 1.6)
    elif nom == "train":
        c.boite(-6.5, -10, 6.5, 5.5, 3.2, False)
        c.trait([(-6.5, -3.5), (6.5, -3.5)])
        c.rond(-3.2, 1.2, 1.25)
        c.rond(3.2, 1.2, 1.25)
        c.trait([(-4, 8.5), (-6.5, 10.5)])
        c.trait([(4, 8.5), (6.5, 10.5)])
    elif nom == "etoile":
        pts = []
        for i in range(10):
            a = -math.pi / 2 + i * math.pi / 5
            r = 9.6 if i % 2 == 0 else 4.2
            pts.append((r * math.cos(a), r * math.sin(a) + 0.8))
        c.plein(pts, 1.4)
    elif nom == "losange":
        c.plein([(0, -14), (12, 0), (0, 14), (-12, 0)], 2.4)
    elif nom == "fleche":
        # flèche du joueur (pointe en haut, encoche en bas), coins adoucis
        c.plein([(0, -14.5), (10.5, 12), (0, 6.2), (-10.5, 12)], 2.2)
    elif nom == "disque":
        c.rond(0, 0, 16)
    elif nom == "anneau":
        c.rond(0, 0, 15, False, 1.7)
    elif nom == "halo":
        img = Image.new("L", (c.t, c.t), 0)
        d = ImageDraw.Draw(img)
        n = 64
        for i in range(n):
            r = c.t / 2 * (1 - i / n)
            v = int(255 * (i / n) ** 1.6)
            d.ellipse((c.t / 2 - r, c.t / 2 - r, c.t / 2 + r, c.t / 2 + r), fill=v)
        c.img = img.filter(ImageFilter.GaussianBlur(c.t / 40))
    elif nom == "nord":
        c.plein([(0, -14.5), (5.6, 3.6), (0, 0.4), (-5.6, 3.6)], 1.2)
    elif nom == "maison":
        c.trait([(-9.5, -0.5), (0, -9.5), (9.5, -0.5)])
        c.trait([(-6.8, -2.5), (-6.8, 9), (6.8, 9), (6.8, -2.5)])
        c.trait([(-2, 9), (-2, 3.5), (2, 3.5), (2, 9)], 1.8)
    elif nom == "cible":
        c.rond(0, 0, 8.6, False, 1.7)
        c.rond(0, 0, 2.1)
        for (a, b) in (((0, -14), (0, -10.5)), ((0, 10.5), (0, 14)), ((-14, 0), (-10.5, 0)), ((10.5, 0), (14, 0))):
            c.trait([a, b], 1.7)
    elif nom == "helico":
        # hélicoptère vu de dessus, nez en haut (l'écran le tourne selon son cap) : deux longues pales croisées sur le
        # moyeu, cabine en goutte, poutre de queue et petit rotor de queue
        c.trait([(-7.6, -10.6), (7.6, 4.6)], 1.7)
        c.trait([(-7.6, 4.6), (7.6, -10.6)], 1.7)
        c.plein([(0, -9.6), (2.6, -8.0), (3.2, -4.6), (2.6, -0.6), (1.2, 2.4), (-1.2, 2.4), (-2.6, -0.6), (-3.2, -4.6),
                 (-2.6, -8.0)], 1.2)
        c.trait([(0, 2.0), (0, 10.2)], 1.9)
        c.trait([(-3.2, 9.6), (3.2, 9.6)], 1.7)
    elif nom == "epave":
        # épave : éclat d'explosion au trait (8 pointes) et, dedans, la cabine brisée en deux morceaux
        pts = []
        for i in range(16):
            a = -math.pi / 2 + i * math.pi / 8
            r = 10.4 if i % 2 == 0 else 6.4
            pts.append((r * math.cos(a), r * math.sin(a)))
        c.trait(pts, 1.5, ferme=True)
        c.plein([(-4.6, -1.4), (-0.6, -3.6), (0.6, -1.6), (-3.4, 0.6)], 0.8)
        c.plein([(0.4, 0.4), (4.4, -1.2), (5.0, 0.8), (1.2, 2.6)], 0.8)
        c.trait([(-1.6, 1.6), (-3.2, 4.4)], 1.2)
    return c.img


def niveaux(alpha):
    """Réduction par moyenne de surface vers chaque taille de case (couverture exacte : bords lisses)."""
    return [alpha.resize((t, t), Image.BOX) for t in CASES]


def atlas():
    lignes = -(-len(NOMS) // COLONNES)
    largeur = COLONNES * CASES[0]
    hauteur = sum(lignes * t for t in CASES)
    im = Image.new("RGBA", (largeur, hauteur), (255, 255, 255, 0))
    for i, nom in enumerate(NOMS):
        y0 = 0
        for t, a in zip(CASES, niveaux(dessiner(nom))):
            case = Image.new("RGBA", (t, t), (255, 255, 255, 0))
            case.putalpha(a)
            im.paste(case, ((i % COLONNES) * t, y0 + (i // COLONNES) * t))
            y0 += lignes * t
    return im


def planche(im, fichier):
    """Planche de contrôle : chaque icône à 3 tailles, sur fond clair et sombre, avec son nom."""
    lignes = -(-len(NOMS) // COLONNES)
    t0 = CASES[0]
    pas = 150
    out = Image.new("RGB", (COLONNES * pas, len(range(0, len(NOMS), COLONNES)) * pas * 2), (24, 27, 33))
    d = ImageDraw.Draw(out)
    for i, nom in enumerate(NOMS):
        cx, cy = (i % COLONNES) * pas, (i // COLONNES) * pas * 2
        d.rectangle((cx, cy + pas, cx + pas - 1, cy + 2 * pas - 1), fill=(150, 160, 150))
        case = im.crop(((i % COLONNES) * t0, (i // COLONNES) * t0, (i % COLONNES) * t0 + t0, (i // COLONNES) * t0 + t0))
        for fond_y in (cy, cy + pas):
            out.paste(case, (cx + 11, fond_y + 4), case)
        y0 = lignes * t0
        petit = im.crop(((i % COLONNES) * 32, y0 + lignes * 64 + (i // COLONNES) * 32,
                         (i % COLONNES) * 32 + 32, y0 + lignes * 64 + (i // COLONNES) * 32 + 32))
        for fond_y in (cy, cy + pas):
            out.paste(petit, (cx + 108, fond_y + 100), petit)
        d.text((cx + 6, cy + pas - 14), nom, fill=(220, 224, 230))
    out.save(fichier)
    print("[icones] planche -> %s" % fichier)


def main():
    im = atlas()
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    im.save(SORTIE, optimize=True)
    print("[icones] %d icônes, cases %s -> %s (%d x %d)" % (len(NOMS), "/".join(map(str, CASES)), SORTIE, im.width, im.height))
    if "--planche" in sys.argv:
        planche(im, sys.argv[sys.argv.index("--planche") + 1])


if __name__ == "__main__":
    main()
