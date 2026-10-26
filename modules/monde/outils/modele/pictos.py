#!/usr/bin/env python3
"""Pictogrammes des frappes de l'Airstrike (roue des frappes, annonce du chargement) : une bande de 16 cases de 64 px,
blanc sur transparent (teintés à l'affichage), dans textures/gui/strike_icons.png.

    python modules/monde/outils/modele/pictos.py               écrit dans le mod
    python modules/monde/outils/modele/pictos.py <fichier.png> écrit ailleurs

Cases 0 à 7 : les frappes dans l'ordre de StrikeType (bombardement, précision, sous-munitions, napalm, mitraillage,
fumée, gaz, ravitaillement) ; case 8 : cadenas (frappe sans cartouche). Mêmes dessins que la maquette validée de la roue
(repère de 24 x 24 par icône), dessinés 4 fois plus grands puis réduits pour des bords nets.
"""
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ICI = Path(__file__).resolve().parent
SORTIE = ICI.parents[1] / "src" / "client" / "resources" / "assets" / "airstrike" / "textures" / "gui" / "strike_icons.png"
CASE, SUR, CASES = 64, 4, 16
K = CASE * SUR / 24.0          # pixels (dessin sur-échantillonné) par unité du repère 24 x 24


class Picto:
    def __init__(self):
        self.im = Image.new("L", (CASE * SUR, CASE * SUR), 0)
        self.d = ImageDraw.Draw(self.im)

    def p(self, x, y):
        return (x * K, y * K)

    def ellipse(self, cx, cy, rx, ry, v=255):
        self.d.ellipse([(cx - rx) * K, (cy - ry) * K, (cx + rx) * K, (cy + ry) * K], fill=v)

    def anneau(self, cx, cy, r, ep, v=255):
        self.d.ellipse([(cx - r - ep / 2) * K, (cy - r - ep / 2) * K, (cx + r + ep / 2) * K, (cy + r + ep / 2) * K], fill=v)
        self.d.ellipse([(cx - r + ep / 2) * K, (cy - r + ep / 2) * K, (cx + r - ep / 2) * K, (cy + r - ep / 2) * K], fill=0)

    def rect(self, x0, y0, x1, y1, v=255):
        self.d.rectangle([x0 * K, y0 * K, x1 * K, y1 * K], fill=v)

    def poly(self, pts, v=255):
        self.d.polygon([self.p(x, y) for x, y in pts], fill=v)

    def trait(self, x0, y0, x1, y1, ep, v=255):
        self.d.line([self.p(x0, y0), self.p(x1, y1)], fill=v, width=int(ep * K))
        for x, y in ((x0, y0), (x1, y1)):
            self.ellipse(x, y, ep / 2, ep / 2, v)

    def image(self):
        return self.im.resize((CASE, CASE), Image.LANCZOS)


def bezier3(p0, p1, p2, p3, n=24):
    out = []
    for i in range(1, n + 1):
        t = i / n
        a, b, c, d = (1 - t) ** 3, 3 * (1 - t) ** 2 * t, 3 * (1 - t) * t * t, t ** 3
        out.append((a * p0[0] + b * p1[0] + c * p2[0] + d * p3[0], a * p0[1] + b * p1[1] + c * p2[1] + d * p3[1]))
    return out


def bezier2(p0, p1, p2, n=16):
    out = []
    for i in range(1, n + 1):
        t = i / n
        a, b, c = (1 - t) ** 2, 2 * (1 - t) * t, t * t
        out.append((a * p0[0] + b * p1[0] + c * p2[0], a * p0[1] + b * p1[1] + c * p2[1]))
    return out


def chemin(depart, segments):
    """segments : (point de contrôle 1, 2, arrivée) en cubiques."""
    pts = [depart]
    cur = depart
    for c1, c2, fin in segments:
        pts += bezier3(cur, c1, c2, fin)
        cur = fin
    return pts


def bombe(P, bx, by, k):
    t = lambda x, y: (bx + x * k, by + y * k)
    P.ellipse(*t(0, 3), 3.6 * k, 6.2 * k)
    P.poly([t(-3.4, -4.5), t(3.4, -4.5), t(2.2, -1.5), t(-2.2, -1.5)])
    x0, y0 = t(-0.6, -7)
    P.rect(x0, y0, x0 + 1.2 * k, y0 + 3 * k)


def bombardement(P):
    bombe(P, 8, 9, 0.85)
    bombe(P, 16, 13, 0.85)


def sous_munitions(P):
    P.ellipse(12, 5, 4.2, 3.2)
    for x, y in ((5, 13), (12, 12.5), (19, 13), (8, 19), (16, 19), (12, 22), (3, 19.5), (21, 19.5)):
        P.ellipse(x, y, 1.7, 1.7)


def gaz(P):
    P.ellipse(12, 10, 8, 8)
    P.rect(7, 14, 17, 21)
    P.ellipse(8.8, 10.5, 2.6, 3, 0)
    P.ellipse(15.2, 10.5, 2.6, 3, 0)
    P.poly([(12, 13.3), (10.8, 16), (13.2, 16)], 0)
    for x in (9.5, 12, 14.5):
        P.rect(x - 0.5, 18, x + 0.5, 21.2, 0)


def precision(P):
    P.anneau(12, 12, 7.5, 2)
    for x0, y0, x1, y1 in ((12, 1, 12, 7), (12, 17, 12, 23), (1, 12, 7, 12), (17, 12, 23, 12)):
        P.trait(x0, y0, x1, y1, 2)
    P.ellipse(12, 12, 2, 2)


def napalm(P):
    P.poly(chemin((12, 1.5), [((15, 7), (21, 10), (19.5, 16.5)), ((18.5, 21), (15, 23), (12, 23)),
                              ((8, 23), (4.5, 20.5), (4.5, 16)), ((4.5, 12), (7.5, 10), (8.5, 6.5)),
                              ((10, 9), (10.5, 10.5), (11, 12)), ((12.5, 8.5), (12.5, 5), (12, 1.5))]))
    P.poly(chemin((12, 13), [((14, 16), (15.5, 17.5), (15, 19.5)), ((14.5, 21), (13.3, 21.5), (12, 21.5)),
                             ((10.4, 21.5), (9, 20.4), (9, 18.6)), ((9, 16.5), (11, 15.5), (12, 13))]), 0)


def mitraillage(P):
    for bx in (6, 12, 18):
        pts = [(bx - 2.4, 22), (bx - 2.4, 9)]
        pts += bezier2((bx - 2.4, 9), (bx - 2.4, 3), (bx, 1.5))
        pts += bezier2((bx, 1.5), (bx + 2.4, 3), (bx + 2.4, 9))
        pts += [(bx + 2.4, 22)]
        P.poly(pts)
        P.rect(bx - 3, 9.2, bx + 3, 10.3, 0)


def fumee(P):
    P.ellipse(8, 14, 5.5, 5.5)
    P.ellipse(13.5, 10, 6.5, 6.5)
    P.ellipse(18, 14.5, 4.8, 4.8)
    P.rect(6, 14, 19, 19.3)
    P.ellipse(6.3, 15.5, 3.8, 3.8)


def ravitaillement(P):
    dome = [(12 + 9 * math.cos(a / 20 * math.pi), 8 - 6 * math.sin(a / 20 * math.pi)) for a in range(0, 21)]
    P.poly(dome)
    for i in range(4):
        P.ellipse(4.1 + i * 5.3, 8, 2.65, 1.3, 0)
    for x0, x1 in ((3.5, 9.4), (12, 12), (20.5, 14.6)):
        P.trait(x0, 8.4, x1, 15, 0.9)
    P.rect(8.5, 15, 15.5, 22)
    P.trait(9.6, 16.1, 14.4, 20.9, 0.8, 0)
    P.trait(14.4, 16.1, 9.6, 20.9, 0.8, 0)


def cadenas(P):
    P.anneau(12, 9.5, 4.3, 2.2)
    P.rect(6, 9.5, 18, 13, 0)
    P.rect(5.7, 9.4, 7.9, 12)
    P.rect(16.1, 9.4, 18.3, 12)
    P.d.rounded_rectangle([5 * K, 11 * K, 19 * K, 21.5 * K], radius=int(1.6 * K), fill=255)
    P.ellipse(12, 15, 1.4, 1.4, 0)
    P.rect(11.4, 15, 12.6, 18.2, 0)


ORDRE = [bombardement, precision, sous_munitions, napalm, mitraillage, fumee, gaz, ravitaillement, cadenas]


def planche():
    out = Image.new("RGBA", (CASE * CASES, CASE), (255, 255, 255, 0))
    for i, f in enumerate(ORDRE):
        P = Picto()
        f(P)
        a = P.image()
        case = Image.new("RGBA", (CASE, CASE), (255, 255, 255, 0))
        case.putalpha(a)
        out.paste(case, (i * CASE, 0))
    return out


def main():
    dest = Path(sys.argv[1]) if len(sys.argv) > 1 else SORTIE
    dest.parent.mkdir(parents=True, exist_ok=True)
    planche().save(dest, optimize=True)
    print("[pictos] %d pictogrammes -> %s" % (len(ORDRE), dest))


if __name__ == "__main__":
    main()
