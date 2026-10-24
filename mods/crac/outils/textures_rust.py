#!/usr/bin/env python3
"""
Textures des pièces de construction de CRac (bois, pierre, tôle, blindé) façon Rust : propres, nettes, en double
définition (64 points par bloc au lieu de 32). Le palier « brindilles » n'est pas touché.

Rien n'est dessiné à la main : on modifie ce script, puis on le relance.

    python mods/crac/outils/textures_rust.py --apercu DOSSIER    textures + planche + maquette 3D d'une petite base
    python mods/crac/outils/textures_rust.py --installer         écrit dans assets/crac/textures/block/building/<palier>/

Tailles (même rapport qu'avant, deux fois plus fin ; le jeu lit les coordonnées de texture de 0 à 1, cf. PieceBakery) :
wall, floor, floor_under, foundation_top, roof, roof_under, side, ramp 192 x 192 (3 x 3 blocs) ; door 64 x 128 ;
foundation_side 192 x 64 ; skirt 192 x 128 ; trim, beam, post 64 x 64 ; post_top 32 x 32.
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RACINE = Path(__file__).resolve().parents[1]
TEXTURES = RACINE / "src" / "client" / "resources" / "assets" / "crac" / "textures" / "block" / "building"
PALIERS = ["wood", "stone", "metal", "armored"]
TAILLES = {
    "wall": (192, 192), "floor": (192, 192), "floor_under": (192, 192), "foundation_top": (192, 192),
    "roof": (192, 192), "roof_under": (192, 192), "side": (192, 192), "ramp": (192, 192),
    "door": (64, 128), "foundation_side": (192, 64), "skirt": (192, 128),
    "trim": (64, 64), "beam": (64, 64), "post": (64, 64), "post_top": (32, 32),
}


# ------------------------------------------------------------------------------------------------- outils


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)])


def bruit(h, w, echelle, graine, octaves=3):
    """Bruit doux entre 0 et 1 (valeurs tirées au hasard, agrandies et lissées), plusieurs octaves."""
    r = np.random.default_rng(graine)
    tot = np.zeros((h, w))
    amp, poids = 1.0, 0.0
    for o in range(octaves):
        e = max(1, int(echelle / (2 ** o)))
        g = r.random((h // e + 2, w // e + 2))
        im = Image.fromarray((g * 255).astype(np.uint8)).resize(((w // e + 2) * e, (h // e + 2) * e), Image.BICUBIC)
        tot += amp * np.asarray(im, dtype=float)[:h, :w] / 255
        poids += amp
        amp *= 0.5
    return tot / poids


def grain(h, w, long_, graine, axe="v"):
    """Grain étiré (bois, métal brossé) le long de v (vertical) ou de u."""
    r = np.random.default_rng(graine)
    if axe == "v":
        g = r.random((max(2, h // long_), w))
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)
    else:
        g = r.random((h, max(2, w // long_)))
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR)
    return np.asarray(im, dtype=float) / 255


class Tex:
    """Image RGB en flottants, avec quelques pinceaux."""

    def __init__(self, w, h, couleur):
        self.w, self.h = w, h
        self.a = np.ones((h, w, 3)) * couleur

    def rect(self, x0, y0, x1, y1, couleur, alpha=1.0):
        x0, y0, x1, y1 = max(0, int(x0)), max(0, int(y0)), min(self.w, int(x1)), min(self.h, int(y1))
        if x1 > x0 and y1 > y0:
            self.a[y0:y1, x0:x1] = self.a[y0:y1, x0:x1] * (1 - alpha) + np.asarray(couleur) * alpha

    def mul(self, x0, y0, x1, y1, f):
        x0, y0, x1, y1 = max(0, int(x0)), max(0, int(y0)), min(self.w, int(x1)), min(self.h, int(y1))
        if x1 > x0 and y1 > y0:
            self.a[y0:y1, x0:x1] *= f

    def biseau(self, x0, y0, x1, y1, e=2, clair=1.18, sombre=0.62):
        """Bord relevé : haut et gauche éclairés, bas et droite dans l'ombre."""
        self.mul(x0, y0, x1, y0 + e, clair)
        self.mul(x0, y0 + e, x0 + e, y1, clair * 0.97)
        self.mul(x0, y1 - e, x1, y1, sombre)
        self.mul(x1 - e, y0, x1, y1 - e, sombre * 1.05)

    def joint(self, x0, y0, x1, y1, ombre=0.45):
        """Rainure : trait sombre puis liseré clair."""
        if x1 - x0 >= y1 - y0:  # horizontale
            self.mul(x0, y0, x1, y0 + 1, ombre)
            self.mul(x0, y0 + 1, x1, y0 + 2, 1.2)
        else:
            self.mul(x0, y0, x0 + 1, y1, ombre)
            self.mul(x0 + 1, y0, x0 + 2, y1, 1.15)

    def boulon(self, cx, cy, r=2.6, couleur=None, hexa=False):
        couleur = self.a[int(min(self.h - 1, cy)), int(min(self.w - 1, cx))] if couleur is None else couleur
        yy, xx = np.mgrid[0:self.h, 0:self.w]
        d = np.hypot(xx - cx, yy - cy)
        if hexa:
            ang = np.arctan2(yy - cy, xx - cx)
            d = d * np.cos((np.mod(ang, math.pi / 3) - math.pi / 6)) / math.cos(math.pi / 6)
        ombre = (np.hypot(xx - cx - 0.9, yy - cy - 1.1) < r + 0.6) & (d >= r)
        self.a[ombre] *= 0.72
        tete = d < r
        lum = np.clip(1.15 - ((xx - cx + r * 0.4) ** 2 + (yy - cy + r * 0.4) ** 2) / (r * r * 2.2), 0.55, 1.35)
        base = np.asarray(couleur) * 0.95 + 0.04
        self.a[tete] = (base[None, :] * lum[tete][:, None])

    def bruiter(self, n, force):
        self.a *= (1 + (n[..., None] - 0.5) * force)

    def image(self):
        return Image.fromarray((np.clip(self.a, 0, 1) * 255).astype(np.uint8), "RGB").convert("RGBA")


def ondule(w, h, periode, axe="v", force=0.22):
    """Tôle ondulée : relief en sinus, éclairé de la gauche (ou du haut)."""
    if axe == "v":  # ondes verticales (cannelures qui descendent)
        x = np.arange(w)
        s = np.cos(2 * math.pi * x / periode)
        d = -np.sin(2 * math.pi * x / periode)
        prof = 1 + force * (0.55 * s + 0.45 * d)
        return np.tile(prof, (h, 1))
    y = np.arange(h)
    s = np.cos(2 * math.pi * y / periode)
    d = -np.sin(2 * math.pi * y / periode)
    prof = 1 + force * (0.55 * s + 0.45 * d)
    return np.tile(prof[:, None], (1, w))


def larme(t, x, y0, long_, couleur, alpha, graine):
    """Coulure de rouille fine sous un boulon (très légère : le métal reste propre)."""
    r = np.random.default_rng(graine)
    for i in range(long_):
        a = alpha * (1 - i / long_) * (0.7 + 0.3 * r.random())
        t.rect(x - 1, y0 + i, x + 1, y0 + i + 1, couleur, a)


def tolerie(t, cases, graine, palette, periode=8, onde=True, axe="v", rivets=16, rouille=0.0):
    """Remplit une suite de cases (x0, y0, x1, y1) de plaques de tôle, chacune de sa teinte."""
    r = np.random.default_rng(graine)
    for i, (x0, y0, x1, y1) in enumerate(cases):
        c = palette[int(r.integers(len(palette)))] * (0.94 + 0.12 * r.random())
        w, h = int(x1 - x0), int(y1 - y0)
        if w <= 0 or h <= 0:
            continue
        plaque = np.ones((h, w, 3)) * c
        if onde:
            plaque *= ondule(w, h, periode, axe)[..., None]
        n = bruit(h, w, 24, int(r.integers(1e9)), 3)
        plaque *= (1 + (n[..., None] - 0.5) * 0.10)
        # usure claire sur les crêtes et un voile plus sombre en bas (poussière)
        vy = np.linspace(0, 1, h)[:, None, None]
        plaque *= (1.02 - 0.08 * vy ** 2)
        t.a[int(y0):int(y0) + h, int(x0):int(x0) + w] = plaque
        t.biseau(x0, y0, x1, y1, e=1, clair=1.25, sombre=0.55)
        # recouvrement : la plaque du dessus jette une ombre de 2 points sur celle du dessous
        t.mul(x0, y1, x1, y1 + 2, 0.72)
        if rivets:
            for x in np.arange(x0 + 5, x1 - 3, rivets):
                for yy in (y0 + 4, y1 - 4):
                    t.boulon(x, yy, 1.8)
                    if rouille and r.random() < rouille:
                        larme(t, int(x), int(yy) + 2, int(4 + r.integers(8)), rgb("8a4a25"), 0.35, int(r.integers(1e9)))


def rangees_decalees(x0, y0, x1, y1, nb, graine, mini, maxi):
    """Plaques en quinconce : chaque rangée coupée à d'autres endroits que la précédente."""
    r = np.random.default_rng(graine)
    cases = []
    hr = (y1 - y0) / nb
    for k in range(nb):
        ya, yb = y0 + round(k * hr), y0 + round((k + 1) * hr)
        x = x0
        premier = int(r.integers(mini // 2, maxi))
        while x < x1:
            larg = premier if x == x0 else int(r.integers(mini, maxi + 1))
            xb = min(x1, x + larg)
            if x1 - xb < mini // 2:
                xb = x1
            cases.append((x, ya, xb, yb))
            x = xb
    return cases


def cadre(t, e, couleur, boulons=0, hexa=False):
    """Cornière tout autour (les bords des murs se touchent d'une pièce à l'autre)."""
    for (x0, y0, x1, y1) in ((0, 0, t.w, e), (0, t.h - e, t.w, t.h), (0, 0, e, t.h), (t.w - e, 0, t.w, t.h)):
        t.rect(x0, y0, x1, y1, couleur)
    t.biseau(0, 0, t.w, t.h, e=1, clair=1.25, sombre=0.6)
    t.biseau(e, e, t.w - e, t.h - e, e=1, clair=0.6, sombre=1.15)
    if boulons:
        for x in range(e + boulons // 2, t.w - e, boulons):
            t.boulon(x, e / 2, e * 0.3, hexa=hexa)
            t.boulon(x, t.h - e / 2, e * 0.3, hexa=hexa)
        for y in range(e + boulons // 2, t.h - e, boulons):
            t.boulon(e / 2, y, e * 0.3, hexa=hexa)
            t.boulon(t.w - e / 2, y, e * 0.3, hexa=hexa)


def tole_striee(w, h, couleur, graine, pas=12):
    """Tôle larmée (antidérapante) : petits reliefs en chevrons alternés."""
    t = Tex(w, h, couleur)
    yy, xx = np.mgrid[0:h, 0:w]
    cy = (yy // pas) * pas + pas / 2
    cx = (xx // pas) * pas + pas / 2
    alt = ((xx // pas + yy // pas) % 2 == 0)
    # larme en biais, sens alterné d'une case à l'autre
    u = np.where(alt, (xx - cx) + (yy - cy), (xx - cx) - (yy - cy)) / 1.414
    v = np.where(alt, (xx - cx) - (yy - cy), (xx - cx) + (yy - cy)) / 1.414
    relief = ((u / (pas * 0.36)) ** 2 + (v / (pas * 0.11)) ** 2) < 1
    haut = relief & (np.where(alt, -(xx - cx) + (yy - cy), (xx - cx) + (yy - cy)) < 0)
    t.a[relief] *= 1.22
    t.a[relief & ~haut] *= 0.80
    t.bruiter(bruit(h, w, 32, graine, 3), 0.10)
    return t


# ------------------------------------------------------------------------------------------------- tôle


TOLE = [rgb("8e979c"), rgb("7f8b94"), rgb("9da3a3"), rgb("74828c"), rgb("8a8f8c"), rgb("8e979c"), rgb("7f8b94"),
        rgb("6f7862"), rgb("8a6f5c")]  # dont une tôle peinte vert olive passé et une tôle brunie, comme dans Rust
ACIER = rgb("5b6267")
ROUILLE = rgb("8a4a25")


def metal(nom, w, h, g):
    if nom == "wall":
        t = Tex(w, h, ACIER)
        tolerie(t, rangees_decalees(6, 6, w - 6, h - 6, 3, g, 48, 96), g + 1, TOLE, periode=12, rivets=22, rouille=0.10)
        # deux plaques de renfort vissées par-dessus, décalées (façon Rust)
        r = np.random.default_rng(g + 2)
        for k in range(2):
            pw, ph = int(r.integers(34, 52)), int(r.integers(26, 40))
            px, py = int(r.integers(14, w - 14 - pw)), int(r.integers(14 + k * 80, 60 + k * 80))
            tolerie(t, [(px, py, px + pw, py + ph)], g + 10 + k, [rgb("a2a8aa"), rgb("6f7b83")], onde=False, rivets=0)
            for bx, by in ((px + 4, py + 4), (px + pw - 4, py + 4), (px + 4, py + ph - 4), (px + pw - 4, py + ph - 4)):
                t.boulon(bx, by, 2.0)
        cadre(t, 6, ACIER, boulons=48)
        return t
    if nom == "door":  # porte en tôle peinte (gris-vert foncé), traverses et écharpe en acier, comme dans Rust
        t = Tex(w, h, rgb("3c4346"))
        tolerie(t, [(5, 5, w - 5, h - 5)], g, [rgb("56625d")], periode=12, rivets=0)
        for i in range(int(h * 0.5)):  # écharpe en biais entre les traverses
            x = 6 + (w - 20) * i / (h * 0.5)
            t.rect(x, h * 0.25 + i, x + 8, h * 0.25 + i + 1, rgb("6b7378"))
        for y in (h * 0.25, h * 0.75):  # deux traverses plates
            t.rect(5, y - 6, w - 5, y + 6, rgb("6b7378"))
            t.biseau(5, y - 6, w - 5, y + 6, e=1, clair=1.3, sombre=0.55)
            t.mul(5, y + 6, w - 5, y + 8, 0.7)
            for x in (11, w / 2, w - 11):
                t.boulon(x, y, 2.2)
        t.rect(5, h - 14, w - 5, h - 5, rgb("4b5357"))  # plaque de bas de porte
        t.biseau(5, h - 14, w - 5, h - 5, e=1)
        cadre(t, 5, rgb("2f3538"))
        return t
    if nom in ("foundation_top", "floor", "side", "ramp"):
        t = tole_striee(w, h, rgb("858d92"), g)
        cases = rangees_decalees(0, 0, w, h, 2, g, 64, 128)
        for (x0, y0, x1, y1) in cases:
            t.joint(x0, y0, x1, y0 + 2)
            t.joint(x0, y0, x0 + 2, y1)
            for bx in (x0 + 5, x1 - 5):
                for by in (y0 + 5, y1 - 5):
                    t.boulon(bx, by, 2.0)
        return t
    if nom == "floor_under":
        t = Tex(w, h, rgb("6d757a"))
        tolerie(t, rangees_decalees(0, 0, w, h, 3, g, 64, 128), g, [rgb("6d757a"), rgb("737c80")], onde=False, rivets=0)
        for x in (w * 0.25, w * 0.75):  # deux poutrelles
            t.rect(x - 7, 0, x + 7, h, rgb("4f565b"))
            t.biseau(x - 7, 0, x + 7, h, e=2)
        return t
    if nom == "roof":
        t = Tex(w, h, ACIER)
        tolerie(t, rangees_decalees(0, 0, w, h, 2, g, 48, 96), g, TOLE, periode=12, rivets=24, rouille=0.08)
        return t
    if nom == "roof_under":
        t = Tex(w, h, rgb("6a7277"))
        tolerie(t, rangees_decalees(0, 0, w, h, 2, g, 48, 96), g, [rgb("6a7277"), rgb("646c71")], periode=10, rivets=0)
        return t
    if nom == "foundation_side":
        t = Tex(w, h, rgb("6b7378"))
        t.bruiter(grain(h, w, 24, g, "u"), 0.08)
        t.biseau(0, 0, w, h, e=2)
        t.rect(0, h - 10, w, h, rgb("50575c"))
        t.biseau(0, h - 10, w, h, e=1)
        for x in range(8, w, 24):
            t.boulon(x, 8, 2.4, hexa=True)
        return t
    if nom == "skirt":
        return beton(w, h, g)
    if nom == "trim":
        t = Tex(w, h, rgb("6a7277"))
        t.bruiter(grain(h, w, 32, g, "v"), 0.10)
        t.biseau(0, 0, w, h, e=3)
        return t
    if nom == "beam":
        t = Tex(w, h, rgb("5d656a"))
        t.bruiter(grain(h, w, 32, g, "u"), 0.10)
        t.rect(0, h * 0.42, w, h * 0.58, rgb("4d5459"))
        t.biseau(0, 0, w, h, e=3)
        for x in (w * 0.25, w * 0.75):
            t.boulon(x, h * 0.25, 3.2, hexa=True)
            t.boulon(x, h * 0.75, 3.2, hexa=True)
        return t
    if nom == "post":
        t = Tex(w, h, rgb("62696e"))
        t.bruiter(grain(h, w, 32, g, "v"), 0.10)
        t.biseau(0, 0, w, h, e=3)
        t.rect(w * 0.46, 0, w * 0.54, h, rgb("555c61"))
        for y in (h * 0.25, h * 0.75):
            t.boulon(w * 0.25, y, 3.0, hexa=True)
            t.boulon(w * 0.75, y, 3.0, hexa=True)
        return t
    if nom == "post_top":
        t = Tex(w, h, rgb("62696e"))
        t.biseau(0, 0, w, h, e=2)
        t.boulon(w / 2, h / 2, 4.0, hexa=True)
        return t
    raise KeyError(nom)


def beton(w, h, g, couleur="9a9890"):
    """Socle en béton coffré (dessous des fondations), propre."""
    t = Tex(w, h, rgb(couleur))
    t.bruiter(bruit(h, w, 20, g, 4), 0.16)
    t.bruiter(bruit(h, w, 4, g + 1, 1), 0.06)
    for y in range(0, h, 32):  # traces des planches de coffrage
        t.joint(0, y, w, y + 2, ombre=0.82)
    for x in range(0, w, 96):
        t.joint(x, 0, x + 2, h, ombre=0.8)
    vy = np.linspace(0, 1, h)[:, None, None]
    t.a *= (1.0 - 0.18 * vy)  # bas plus sombre (terre)
    return t


# ------------------------------------------------------------------------------------------------- blindé


BLINDE = [rgb("4e565c"), rgb("535b61"), rgb("495156")]
SOUDURE = rgb("3a4045")


def plaques_blindees(t, cases, g, chanfrein=3):
    r = np.random.default_rng(g)
    for (x0, y0, x1, y1) in cases:
        c = BLINDE[int(r.integers(len(BLINDE)))] * (0.95 + 0.1 * r.random())
        w, h = int(x1 - x0), int(y1 - y0)
        p = np.ones((h, w, 3)) * c
        p *= (1 + (bruit(h, w, 28, int(r.integers(1e9)), 3)[..., None] - 0.5) * 0.10)
        p *= (1 + (grain(h, w, 40, int(r.integers(1e9)), "u")[..., None] - 0.5) * 0.05)
        t.a[int(y0):int(y0) + h, int(x0):int(x0) + w] = p
        t.biseau(x0, y0, x1, y1, e=chanfrein, clair=1.30, sombre=0.55)
        t.rect(x0, y0, x1, y0 + 1, SOUDURE)  # cordon de soudure
        t.rect(x0, y0, x0 + 1, y1, SOUDURE)
        for bx in (x0 + 9, x1 - 9):
            for by in (y0 + 9, y1 - 9):
                t.boulon(bx, by, 3.4, hexa=True)
        # rayures légères
        for _ in range(int(r.integers(0, 3))):
            sx, sy = x0 + r.integers(8, max(9, w - 8)), y0 + r.integers(8, max(9, h - 8))
            ln = int(r.integers(6, 16))
            for i in range(ln):
                t.mul(sx + i, sy + i // 3, sx + i + 1, sy + i // 3 + 1, 1.35)


def blinde(nom, w, h, g):
    if nom in ("wall", "roof", "side"):
        t = Tex(w, h, SOUDURE)
        plaques_blindees(t, rangees_decalees(0, 0, w, h, 3, g, 56, 96), g)
        return t
    if nom in ("foundation_top", "floor", "ramp"):
        t = tole_striee(w, h, rgb("5a6267"), g, pas=14)
        for (x0, y0, x1, y1) in rangees_decalees(0, 0, w, h, 2, g, 80, 128):
            t.joint(x0, y0, x1, y0 + 2, 0.4)
            t.joint(x0, y0, x0 + 2, y1, 0.4)
            for bx in (x0 + 8, x1 - 8):
                for by in (y0 + 8, y1 - 8):
                    t.boulon(bx, by, 3.0, hexa=True)
        return t
    if nom in ("floor_under", "roof_under"):
        t = Tex(w, h, SOUDURE)
        plaques_blindees(t, rangees_decalees(0, 0, w, h, 2, g, 96, 128), g, chanfrein=2)
        return t
    if nom == "door":
        t = Tex(w, h, SOUDURE)
        plaques_blindees(t, [(4, 4, w - 4, h * 0.5 - 1), (4, h * 0.5 + 1, w - 4, h - 4)], g, chanfrein=3)
        t.rect(w * 0.2, h * 0.12, w * 0.8, h * 0.12 + 6, rgb("1d2125"))  # judas
        t.biseau(w * 0.2, h * 0.12, w * 0.8, h * 0.12 + 6, e=1, clair=0.6, sombre=1.3)
        cadre(t, 4, SOUDURE)
        return t
    if nom == "foundation_side":
        t = Tex(w, h, BLINDE[0])
        plaques_blindees(t, [(x, 0, x + 64, h) for x in range(0, w, 64)], g, chanfrein=3)
        return t
    if nom == "skirt":
        return beton(w, h, g, "8d8b84")
    if nom in ("trim", "post"):
        t = Tex(w, h, rgb("4a5157"))
        t.bruiter(grain(h, w, 32, g, "v"), 0.08)
        t.biseau(0, 0, w, h, e=4, clair=1.3, sombre=0.55)
        if nom == "post":
            for y in (h * 0.25, h * 0.75):
                t.boulon(w / 2, y, 4.0, hexa=True)
        return t
    if nom == "beam":
        t = Tex(w, h, rgb("4a5157"))
        t.bruiter(grain(h, w, 32, g, "u"), 0.08)
        t.biseau(0, 0, w, h, e=4, clair=1.3, sombre=0.55)
        for x in (w * 0.25, w * 0.75):
            t.boulon(x, h / 2, 4.0, hexa=True)
        return t
    if nom == "post_top":
        t = Tex(w, h, rgb("4a5157"))
        t.biseau(0, 0, w, h, e=3, clair=1.3, sombre=0.55)
        t.boulon(w / 2, h / 2, 4.5, hexa=True)
        return t
    raise KeyError(nom)


# ------------------------------------------------------------------------------------------------- pierre


def blocs(t, cases, g, couleurs, joint_c):
    r = np.random.default_rng(g)
    for (x0, y0, x1, y1) in cases:
        c = couleurs[int(r.integers(len(couleurs)))] * (0.93 + 0.12 * r.random())
        w, h = int(x1 - x0) - 3, int(y1 - y0) - 3
        if w <= 2 or h <= 2:
            continue
        p = np.ones((h, w, 3)) * c
        p *= (1 + (bruit(h, w, 18, int(r.integers(1e9)), 4)[..., None] - 0.5) * 0.22)
        p *= (1 + (bruit(h, w, 3, int(r.integers(1e9)), 1)[..., None] - 0.5) * 0.10)
        # bloc un peu bombé : centre plus clair
        yy, xx = np.mgrid[0:h, 0:w]
        bom = 1 - 0.10 * (((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2)
        p *= bom[..., None]
        t.a[int(y0) + 1:int(y0) + 1 + h, int(x0) + 1:int(x0) + 1 + w] = p
        t.biseau(x0 + 1, y0 + 1, x0 + 1 + w, y0 + 1 + h, e=2, clair=1.14, sombre=0.70)
        # éclat sur un coin
        if r.random() < 0.35:
            cx, cy = (x0 + 2, y0 + 2) if r.random() < 0.5 else (x0 + w - 4, y0 + h - 4)
            t.mul(cx, cy, cx + 4, cy + 3, 0.78)


def pierre(nom, w, h, g):
    gris = [rgb("a7a59d"), rgb("9c9b95"), rgb("b2afa6"), rgb("94948f")]
    joint_c = rgb("5f5d58")
    if nom in ("wall", "side", "foundation_side", "skirt", "ramp"):
        t = Tex(w, h, joint_c)
        nb = max(1, round(h / 32))
        blocs(t, rangees_decalees(0, 0, w, h, nb, g, 40, 72), g, gris, joint_c)
        return t
    if nom in ("foundation_top", "floor", "floor_under", "roof_under"):
        t = Tex(w, h, joint_c)
        blocs(t, rangees_decalees(0, 0, w, h, 3, g, 56, 96), g, gris, joint_c)
        return t
    if nom == "roof":  # tuiles de schiste
        t = Tex(w, h, rgb("3f454a"))
        r = np.random.default_rng(g)
        for k, y in enumerate(range(0, h, 16)):
            dec = 12 if k % 2 else 0
            for x in range(-dec, w, 24):
                c = rgb("59636a") * (0.88 + 0.2 * r.random())
                t.rect(x + 1, y, x + 23, y + 16, c)
                t.mul(x + 1, y + 13, x + 23, y + 16, 0.6)
                t.mul(x + 1, y, x + 23, y + 1, 1.25)
        t.bruiter(bruit(h, w, 12, g, 3), 0.10)
        return t
    if nom == "door":  # porte en bois renforcée de fer (comme dans Rust)
        return bois_porte(w, h, g, fer=True)
    if nom in ("trim", "post", "beam", "post_top"):
        t = Tex(w, h, gris[0])
        t.bruiter(bruit(h, w, 10, g, 4), 0.20)
        t.biseau(0, 0, w, h, e=max(2, w // 16), clair=1.15, sombre=0.7)
        return t
    raise KeyError(nom)


# ------------------------------------------------------------------------------------------------- bois


BOIS = [rgb("8a6440"), rgb("7d5a39"), rgb("94704a"), rgb("74533a")]


def planches(t, x0, y0, x1, y1, n, g, axe="v", clous=True):
    r = np.random.default_rng(g)
    long_ = (x1 - x0) if axe == "v" else (y1 - y0)
    larg = ((x1 - x0) if axe == "v" else (y1 - y0)) / n
    for k in range(n):
        c = BOIS[int(r.integers(len(BOIS)))] * (0.92 + 0.14 * r.random())
        if axe == "v":
            a0, a1, b0, b1 = x0 + round(k * larg), x0 + round((k + 1) * larg), y0, y1
        else:
            a0, a1, b0, b1 = y0 + round(k * larg), y0 + round((k + 1) * larg), x0, x1
        if axe == "v":
            w, h = a1 - a0, b1 - b0
            gr = grain(h, w, 14, int(r.integers(1e9)), "v")
        else:
            w, h = b1 - b0, a1 - a0
            gr = grain(h, w, 14, int(r.integers(1e9)), "u")
        p = np.ones((h, w, 3)) * c
        p *= (1 + (gr[..., None] - 0.5) * 0.35)
        p *= (1 + (bruit(h, w, 20, int(r.integers(1e9)), 2)[..., None] - 0.5) * 0.12)
        if axe == "v":
            t.a[b0:b1, a0:a1] = p
            t.biseau(a0, b0, a1, b1, e=1, clair=1.15, sombre=0.55)
        else:
            t.a[a0:a1, b0:b1] = p
            t.biseau(b0, a0, b1, a1, e=1, clair=1.15, sombre=0.55)
        # un nœud de temps en temps
        if r.random() < 0.4:
            kx = (a0 + a1) / 2 if axe == "v" else b0 + r.random() * w
            ky = b0 + r.random() * h if axe == "v" else (a0 + a1) / 2
            yy, xx = np.mgrid[0:t.h, 0:t.w]
            d = ((xx - kx) / 3.2) ** 2 + ((yy - ky) / 2.2) ** 2
            t.a[d < 1] *= 0.62
    return t


def bois_porte(w, h, g, fer=False):
    t = Tex(w, h, rgb("4a3524"))
    planches(t, 3, 3, w - 3, h - 3, 3, g, "v")
    for y in (h * 0.2, h * 0.8):  # deux traverses
        t.rect(3, y - 5, w - 3, y + 5, rgb("6e4f33") if not fer else rgb("4d5459"))
        t.biseau(3, y - 5, w - 3, y + 5, e=1)
        t.mul(3, y + 5, w - 3, y + 7, 0.7)
        for x in (9, w / 2, w - 9):
            t.boulon(x, y, 1.8, couleur=rgb("3d4246"))
    if not fer:  # écharpe en biais
        for i in range(int(h * 0.6)):
            x = 6 + (w - 18) * i / (h * 0.6)
            t.rect(x, h * 0.2 + i, x + 9, h * 0.2 + i + 1, rgb("6e4f33"))
    cadre(t, 3, rgb("3a2a1d"))
    return t


def bois(nom, w, h, g):
    if nom == "wall":
        t = Tex(w, h, rgb("4a3524"))
        planches(t, 0, 0, w, h, 8, g, "v")
        for y in (24, h - 30):  # deux lisses horizontales clouées
            t.rect(0, y, w, y + 10, rgb("6b4b31"))
            t.bruiter(np.ones((h, w)) * 0.5, 0)
            t.biseau(0, y, w, y + 10, e=1, clair=1.2, sombre=0.55)
            t.mul(0, y + 10, w, y + 12, 0.65)
            for x in range(12, w, 24):
                t.boulon(x, y + 5, 1.5, couleur=rgb("4a4d50"))
        return t
    if nom == "door":
        return bois_porte(w, h, g)
    if nom in ("foundation_top", "floor"):
        t = Tex(w, h, rgb("4a3524"))
        planches(t, 0, 0, w, h, 8, g, "u")
        for x in (8, w // 2, w - 8):
            for y in range(12, h, 24):
                t.boulon(x, y, 1.4, couleur=rgb("4a4d50"))
        return t
    if nom in ("floor_under", "roof_under", "side", "ramp"):
        t = Tex(w, h, rgb("4a3524"))
        planches(t, 0, 0, w, h, 8, g, "u" if nom != "side" else "v")
        return t
    if nom == "roof":  # bardeaux
        t = Tex(w, h, rgb("3a2a1d"))
        r = np.random.default_rng(g)
        for k, y in enumerate(range(0, h, 16)):
            dec = 12 if k % 2 else 0
            for x in range(-dec, w, 24):
                c = BOIS[int(r.integers(len(BOIS)))] * (0.8 + 0.2 * r.random())
                t.rect(x + 1, y, x + 23, y + 16, c)
                t.mul(x + 1, y + 13, x + 23, y + 16, 0.55)
        t.bruiter(grain(h, w, 10, g, "v"), 0.25)
        return t
    if nom == "foundation_side":
        t = Tex(w, h, rgb("4a3524"))
        planches(t, 0, 0, w, h, 2, g, "u")
        return t
    if nom == "skirt":
        t = Tex(w, h, rgb("4a3524"))
        planches(t, 0, 0, w, h, 4, g, "u")
        t.a *= (1 - 0.25 * np.linspace(0, 1, h)[:, None, None])
        return t
    if nom in ("trim", "post"):
        t = Tex(w, h, BOIS[0])
        t.bruiter(grain(h, w, 14, g, "v"), 0.35)
        t.biseau(0, 0, w, h, e=2)
        return t
    if nom == "beam":
        t = Tex(w, h, BOIS[1])
        t.bruiter(grain(h, w, 14, g, "u"), 0.35)
        t.biseau(0, 0, w, h, e=2)
        for x in (w * 0.25, w * 0.75):
            t.boulon(x, h / 2, 2.2, couleur=rgb("4a4d50"))
        return t
    if nom == "post_top":
        t = Tex(w, h, BOIS[2])
        yy, xx = np.mgrid[0:h, 0:w]
        d = np.hypot(xx - w / 2, yy - h / 2)
        t.a *= (1 + 0.12 * np.sin(d * 1.4))[..., None]  # cernes
        t.biseau(0, 0, w, h, e=2)
        return t
    raise KeyError(nom)


FABRIQUES = {"wood": bois, "stone": pierre, "metal": metal, "armored": blinde}


def fabriquer(palier, nom):
    w, h = TAILLES[nom]
    graine = (hash((palier, nom)) & 0xFFFF) if False else sum(map(ord, palier + nom)) * 7919
    return FABRIQUES[palier](nom, w, h, graine).image()


# ------------------------------------------------------------------------------------------------- aperçu


def quad(dst, src, coins, ombre=1.0):
    """Colle src dans dst sur le quadrilatère coins (haut-gauche, bas-gauche, bas-droite, haut-droite)."""
    xs = [p[0] for p in coins]
    ys = [p[1] for p in coins]
    bx0, by0, bx1, by1 = int(min(xs)), int(min(ys)), int(math.ceil(max(xs))), int(math.ceil(max(ys)))
    loc = [(x - bx0, y - by0) for x, y in coins]
    sw, sh = src.size
    # coefficients de perspective : de la case de destination vers la source
    a = []
    b = []
    for (x, y), (u, v) in zip(loc, [(0, 0), (0, sh), (sw, sh), (sw, 0)]):
        a.append([x, y, 1, 0, 0, 0, -u * x, -u * y])
        a.append([0, 0, 0, x, y, 1, -v * x, -v * y])
        b += [u, v]
    k = np.linalg.solve(np.array(a, float), np.array(b, float))
    if ombre != 1.0:
        src = Image.fromarray(np.clip(np.asarray(src.convert("RGB"), float) * ombre, 0, 255).astype(np.uint8)).convert("RGBA")
    morceau = src.transform((bx1 - bx0, by1 - by0), Image.PERSPECTIVE, tuple(k), Image.BICUBIC)
    masque = Image.new("L", morceau.size, 0)
    ImageDraw.Draw(masque).polygon(loc, fill=255)
    dst.paste(morceau, (bx0, by0), masque)


def maquette(tex, taille=520):
    """Petite base vue en biais : fondation, deux murs (dont un avec la porte), toit plat. Ombres de Minecraft."""
    im = Image.new("RGBA", (taille, taille), (196, 206, 212, 255))
    d = ImageDraw.Draw(im)
    d.rectangle((0, taille * 0.62, taille, taille), fill=(150, 140, 110, 255))
    s = taille / 520
    ox, oy = 260 * s, 300 * s
    ux, uy = 70 * s, 35 * s     # un bloc vers la droite (axe x)
    vx, vy = -70 * s, 35 * s    # un bloc vers l'avant gauche (axe z)
    hz = 72 * s                 # un bloc vers le haut

    def P(x, y, z):
        return (ox + x * ux + z * vx, oy + x * uy + z * vy - y * hz)

    L, Pq = 3, 3  # base de 3 x 3 blocs
    # fondation (1 bloc de haut) : dessus, flanc avant gauche, flanc avant droit
    quad(im, tex["foundation_side"], [P(0, 1, Pq), P(0, 0, Pq), P(L, 0, Pq), P(L, 1, Pq)], 0.8)
    quad(im, tex["foundation_side"], [P(L, 1, Pq), P(L, 0, Pq), P(L, 0, 0), P(L, 1, 0)], 0.6)
    quad(im, tex["foundation_top"], [P(0, 1, 0), P(0, 1, Pq), P(L, 1, Pq), P(L, 1, 0)], 1.0)
    # murs : avant (z = Pq) avec la porte, droit (x = L)
    quad(im, tex["wall"], [P(0, 4, Pq), P(0, 1, Pq), P(L, 1, Pq), P(L, 4, Pq)], 0.8)
    quad(im, tex["door"], [P(1.25, 3, Pq + 0.01), P(1.25, 1, Pq + 0.01), P(2.25, 1, Pq + 0.01), P(2.25, 3, Pq + 0.01)], 0.8)
    quad(im, tex["wall"], [P(L, 4, Pq), P(L, 1, Pq), P(L, 1, 0), P(L, 4, 0)], 0.6)
    # bord de toit (trim) et toit
    quad(im, tex["roof"], [P(0, 4, 0), P(0, 4, Pq), P(L, 4, Pq), P(L, 4, 0)], 1.0)
    quad(im, tex["trim"], [P(0, 4.15, Pq), P(0, 4, Pq), P(L, 4, Pq), P(L, 4.15, Pq)], 0.8)
    quad(im, tex["trim"], [P(L, 4.15, Pq), P(L, 4, Pq), P(L, 4, 0), P(L, 4.15, 0)], 0.6)
    return im


def planche(textures, dossier):
    noms = list(TAILLES)
    W = 150
    out = Image.new("RGB", (W * len(noms), W * len(PALIERS) + 24), (28, 28, 28))
    d = ImageDraw.Draw(out)
    for j, n in enumerate(noms):
        d.text((j * W + 6, 6), n, fill=(230, 230, 230))
    for i, p in enumerate(PALIERS):
        for j, n in enumerate(noms):
            im = textures[p][n]
            s = 140 / max(im.size)
            im = im.resize((max(1, int(im.size[0] * s)), max(1, int(im.size[1] * s))), Image.NEAREST)
            out.paste(im, (j * W + 5, 24 + i * W + 5), im)
    out.save(dossier / "planche_textures.png")


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", type=Path)
    g.add_argument("--installer", action="store_true")
    a = ap.parse_args()
    textures = {p: {n: fabriquer(p, n) for n in TAILLES} for p in PALIERS}
    if a.installer:
        for p in PALIERS:
            for n, im in textures[p].items():
                im.save(TEXTURES / p / f"{n}.png")
        print("textures installées :", ", ".join(PALIERS))
        return
    a.apercu.mkdir(parents=True, exist_ok=True)
    for p in PALIERS:
        (a.apercu / p).mkdir(exist_ok=True)
        for n, im in textures[p].items():
            im.save(a.apercu / p / f"{n}.png")
    planche(textures, a.apercu)
    rendus = [maquette(textures[p]) for p in PALIERS]
    out = Image.new("RGB", (520 * len(rendus), 560), (28, 28, 28))
    d = ImageDraw.Draw(out)
    for i, (p, im) in enumerate(zip(PALIERS, rendus)):
        out.paste(im, (i * 520, 40))
        d.text((i * 520 + 12, 12), {"wood": "BOIS", "stone": "PIERRE", "metal": "TOLE", "armored": "BLINDE"}[p],
               fill=(240, 240, 240))
    out.save(a.apercu / "maquettes.png")
    print("aperçu écrit dans", a.apercu)


if __name__ == "__main__":
    main()
