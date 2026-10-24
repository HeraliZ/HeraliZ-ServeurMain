#!/usr/bin/env python3
"""
Textures des pièces de construction de CRac façon Rust, pour les 5 paliers (brindilles, bois, pierre, tôle, blindé) :
propres et nettes, en double définition (64 points par bloc au lieu de 32).

Chaque texture est faite comme un vrai matériau : une couleur (albedo), un relief (carte de hauteur) et un reflet,
puis éclairée par le calcul (lumière en haut à gauche, ombres dans les creux, reflets sur le métal), dessinée deux fois
plus grande puis réduite (bords lisses, pas d'escaliers). Rien n'est dessiné à la main : on modifie ce script, puis
on le relance.

    python mods/crac/outils/textures_rust.py --apercu DOSSIER    textures + planche + maquettes d'une petite base
    python mods/crac/outils/textures_rust.py --installer         écrit dans assets/crac/textures/block/building/<palier>/

Tailles (même rapport qu'avant, deux fois plus fin ; le jeu lit les coordonnées de texture de 0 à 1, cf. PieceBakery) :
wall, floor, floor_under, foundation_top, roof, roof_under, side, ramp 192 x 192 (3 x 3 blocs) ; door 64 x 128 ;
foundation_side 192 x 64 ; skirt 192 x 128 ; trim, beam, post 64 x 64 ; post_top 32 x 32. Brindilles en plus :
straw_end 64 x 64 ; leur wall.png est fait de 6 bandes horizontales (une botte de paille chacune, tiges le long de u,
raccord parfait à gauche et à droite : TwigMeshes la répète tous les 48 px de mur).
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

RACINE = Path(__file__).resolve().parents[1]
TEXTURES = RACINE / "src" / "client" / "resources" / "assets" / "crac" / "textures" / "block" / "building"
PALIERS = ["twig", "wood", "stone", "metal", "armored"]
TAILLES = {
    "wall": (192, 192), "floor": (192, 192), "floor_under": (192, 192), "foundation_top": (192, 192),
    "roof": (192, 192), "roof_under": (192, 192), "side": (192, 192), "ramp": (192, 192),
    "door": (64, 128), "foundation_side": (192, 64), "skirt": (192, 128),
    "trim": (64, 64), "beam": (64, 64), "post": (64, 64), "post_top": (32, 32),
}
EN_PLUS = {"twig": {"straw_end": (64, 64)}}
SS = 2  # suréchantillonnage : dessiné deux fois plus grand, puis réduit
LUMIERE = np.array([-0.42, -0.55, 0.72])
LUMIERE = LUMIERE / np.linalg.norm(LUMIERE)


def rgb(h):
    h = h.lstrip("#")
    return np.array([int(h[i:i + 2], 16) / 255 for i in (0, 2, 4)])


def lisser(a, r, enrouler=False):
    """Flou gaussien rapide (séparable) sur un tableau 2D ; enrouler = raccord parfait sur les bords."""
    if r <= 0:
        return a
    n = int(r * 3) + 1
    k = np.exp(-0.5 * (np.arange(-n, n + 1) / r) ** 2)
    k /= k.sum()
    mode = "wrap" if enrouler else "edge"
    p = np.pad(a, ((n, n), (0, 0)), mode=mode)
    a = sum(k[i] * p[i:i + a.shape[0]] for i in range(2 * n + 1))
    p = np.pad(a, ((0, 0), (n, n)), mode=mode)
    return sum(k[i] * p[:, i:i + a.shape[1]] for i in range(2 * n + 1))


def bruit(h, w, echelle, graine, octaves=3, enrouler=False):
    """Bruit doux entre 0 et 1 (plusieurs octaves) ; enrouler = raccord parfait (textures répétées)."""
    r = np.random.default_rng(graine)
    tot = np.zeros((h, w))
    amp, poids = 1.0, 0.0
    for o in range(octaves):
        e = max(1.0, echelle / (2 ** o))
        tot += amp * lisser(r.random((h, w)), e * 0.6, enrouler)
        poids += amp
        amp *= 0.5
    tot /= poids
    tot -= tot.min()
    return tot / max(1e-9, tot.max())


def lisse(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


class Toile:
    """Matériau en cours : couleur, relief, reflet ; coordonnées données en points de la texture finale."""

    def __init__(self, w, h, couleur, reflet=0.0):
        self.w, self.h = w, h
        self.W, self.H = w * SS, h * SS
        self.X = (np.arange(self.W)[None, :] + 0.5) / SS
        self.Y = (np.arange(self.H)[:, None] + 0.5) / SS
        self.a = np.ones((self.H, self.W, 3)) * couleur
        self.z = np.zeros((self.H, self.W))
        self.s = np.ones((self.H, self.W)) * reflet

    def boite(self, x0, y0, x1, y1):
        return (self.X >= x0) & (self.X < x1) & (self.Y >= y0) & (self.Y < y1)

    def distance_bord(self, x0, y0, x1, y1):
        return np.minimum(np.minimum(self.X - x0, x1 - self.X), np.minimum(self.Y - y0, y1 - self.Y))

    def plaque(self, x0, y0, x1, y1, couleur, haut, chanfrein=1.5, reflet=None, var=0.0, graine=0):
        """Plaque posée (relief = haut, bords chanfreinés) ; écrase ce qu'il y a dessous."""
        m = self.boite(x0, y0, x1, y1)
        d = self.distance_bord(x0, y0, x1, y1)
        prof = haut * lisse(d / max(0.01, chanfrein))
        self.z = np.where(m, np.maximum(self.z * 0 + prof, prof), self.z)
        c = np.asarray(couleur)
        if var:
            n = bruit(self.H, self.W, 14 * SS, graine, 3)
            c = c[None, None, :] * (1 + (n[..., None] - 0.5) * var)
            self.a = np.where(m[..., None], c, self.a)
        else:
            self.a[m] = c
        if reflet is not None:
            self.s[m] = reflet
        return m

    def creux(self, m, prof):
        self.z = np.where(m, self.z - prof, self.z)

    def boulon(self, cx, cy, r, couleur, haut=1.6, hexa=False, reflet=0.5):
        dx, dy = self.X - cx, self.Y - cy
        d = np.hypot(dx, dy)
        if hexa:
            ang = np.arctan2(dy, dx)
            d = d * np.cos(np.mod(ang, math.pi / 3) - math.pi / 6) / math.cos(math.pi / 6)
        m = d < r
        dome = np.sqrt(np.clip(1 - (d / r) ** 2, 0, 1)) if not hexa else np.clip(1.4 - d / r, 0, 1)
        base = self.z[int(min(self.H - 1, cy * SS)), int(min(self.W - 1, cx * SS))]
        self.z = np.where(m, base + haut * dome, self.z)
        self.a[m] = couleur
        self.s[m] = reflet

    def ligne(self, x0, y0, x1, y1, ep, couleur=None, haut=None, prof=None, reflet=None):
        """Segment épais (soudure, tige, rainure) ; haut = relief ajouté en dôme, prof = rainure."""
        vx, vy = x1 - x0, y1 - y0
        l2 = vx * vx + vy * vy or 1
        t = np.clip(((self.X - x0) * vx + (self.Y - y0) * vy) / l2, 0, 1)
        d = np.hypot(self.X - (x0 + t * vx), self.Y - (y0 + t * vy))
        m = d < ep / 2
        f = np.sqrt(np.clip(1 - (d / (ep / 2)) ** 2, 0, 1))
        if haut is not None:
            self.z = np.where(m, np.maximum(self.z, self.z + haut * f), self.z)
        if prof is not None:
            self.z = np.where(m, self.z - prof * f, self.z)
        if couleur is not None:
            self.a[m] = couleur
        if reflet is not None:
            self.s[m] = reflet
        return m

    def teinte(self, n, force, m=None):
        f = (1 + (n[..., None] - 0.5) * force)
        if m is None:
            self.a = self.a * f
        else:
            self.a = np.where(m[..., None], self.a * f, self.a)

    def eclairer(self, relief=2.2, occlusion=1.0, enrouler=False):
        """Éclairage : normales tirées du relief, lumière diffuse + reflet, ombre dans les creux ; puis réduction."""
        z = self.z * relief * SS
        gy, gx = np.gradient(z)
        n = np.dstack([-gx, -gy, np.ones_like(z)])
        n /= np.linalg.norm(n, axis=2, keepdims=True)
        diff = np.clip((n * LUMIERE).sum(axis=2), 0, 1)
        amb = 0.50
        lum = amb + (1 - amb) * diff / LUMIERE[2]
        moitie = LUMIERE + np.array([0, 0, 1.0])
        moitie /= np.linalg.norm(moitie)
        spec = np.clip((n * moitie).sum(axis=2), 0, 1) ** 28 * self.s * 0.55
        cav = lisser(self.z, 2.5 * SS, enrouler) - self.z
        ao = np.clip(1 - np.clip(cav, 0, None) * 0.35 * occlusion, 0.45, 1)
        out = self.a * (lum * ao)[..., None] + spec[..., None]
        im = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), "RGB")
        return im.resize((self.w, self.h), Image.LANCZOS).convert("RGBA")


def quinconce(x0, y0, x1, y1, nb, graine, mini, maxi):
    """Plaques en quinconce : chaque rangée coupée à d'autres endroits que la précédente."""
    r = np.random.default_rng(graine)
    cases = []
    hr = (y1 - y0) / nb
    for k in range(nb):
        ya, yb = y0 + k * hr, y0 + (k + 1) * hr
        x = x0
        premier = int(r.integers(max(8, mini // 2), maxi))
        while x < x1 - 0.5:
            larg = premier if x == x0 else int(r.integers(mini, maxi + 1))
            xb = min(x1, x + larg)
            if x1 - xb < mini // 2:
                xb = x1
            cases.append((x, ya, xb, yb))
            x = xb
    return cases


# ------------------------------------------------------------------------------------------------- tôle


TOLES = [rgb("8f989d"), rgb("838e96"), rgb("9ba1a2"), rgb("7b8790"), rgb("8b8f8d"), rgb("6e765f"), rgb("86705f")]
ACIER = rgb("5c6368")
BOULON = rgb("a0a6a8")


def tole_ondulee(t, x0, y0, x1, y1, couleur, periode, haut, sens="v", graine=0, rivets=24, ombre_bas=True):
    """Une plaque de tôle ondulée posée par-dessus la précédente (marche de 1,2 point : ombre portée nette)."""
    m = t.boite(x0, y0, x1, y1)
    if sens == "v":
        onde = np.cos(2 * math.pi * (t.X - x0) / periode)
    else:
        onde = np.cos(2 * math.pi * (t.Y - y0) / periode)
    d = t.distance_bord(x0, y0, x1, y1)
    z = 1.2 + haut * 0.5 * (1 + onde) * lisse(d / 1.2)
    t.z = np.where(m, z, t.z)
    n = bruit(t.H, t.W, 10 * SS, graine, 3)
    c = couleur[None, None, :] * (0.97 + 0.06 * n[..., None])
    vy = np.clip((t.Y - y0) / max(1, y1 - y0), 0, 1)
    c = c * (1.02 - 0.07 * vy[..., None] ** 2)  # un peu de poussière en bas de plaque
    t.a = np.where(m[..., None], c, t.a)
    t.s[m] = 0.55
    if rivets:
        for x in np.arange(x0 + rivets / 2, x1 - 3, rivets):
            for y in (y0 + 3.2, y1 - 3.2):
                t.boulon(x, y, 1.3, BOULON, haut=0.9)


def metal(nom, w, h, g):
    r = np.random.default_rng(g)
    if nom in ("wall", "roof", "roof_under"):
        t = Toile(w, h, ACIER, 0.4)
        cadre = 6 if nom == "wall" else 0
        rangs = 3 if nom == "wall" else 2
        teintes = TOLES if nom != "roof_under" else [rgb("6c7378"), rgb("666e73")]
        for i, (x0, y0, x1, y1) in enumerate(quinconce(cadre, cadre, w - cadre, h - cadre, rangs, g, 48, 100)):
            c = teintes[int(r.integers(len(teintes)))] * (0.96 + 0.08 * r.random())
            tole_ondulee(t, x0, y0, x1, min(h - cadre, y1 + 2), c, 12, 1.1, graine=g + i,
                         rivets=24 if nom != "roof_under" else 0)
        if nom == "wall":
            for k in range(2):  # deux plaques de renfort vissées par-dessus, décalées
                pw, ph = int(r.integers(34, 54)), int(r.integers(26, 40))
                px, py = int(r.integers(14, w - 14 - pw)), int(r.integers(16 + k * 84, 56 + k * 84))
                t.plaque(px, py, px + pw, py + ph, rgb("8e9599") if k == 0 else rgb("6f7b83"), 2.6, 1.0, 0.45, 0.16, g + 30 + k)
                for bx, by in ((px + 3.5, py + 3.5), (px + pw - 3.5, py + 3.5), (px + 3.5, py + ph - 3.5), (px + pw - 3.5, py + ph - 3.5)):
                    t.boulon(bx, by, 1.6, BOULON, haut=1.0)
            # cornière tout autour, boulonnée
            for (a, b, c_, d) in ((0, 0, w, cadre), (0, h - cadre, w, h), (0, 0, cadre, h), (w - cadre, 0, w, h)):
                t.plaque(a, b, c_, d, ACIER, 3.0, 1.2, 0.45)
            for x in range(cadre + 18, w - cadre, 36):
                t.boulon(x, cadre / 2, 1.8, BOULON, 1.0, hexa=True)
                t.boulon(x, h - cadre / 2, 1.8, BOULON, 1.0, hexa=True)
            for y in range(cadre + 18, h - cadre, 36):
                t.boulon(cadre / 2, y, 1.8, BOULON, 1.0, hexa=True)
                t.boulon(w - cadre / 2, y, 1.8, BOULON, 1.0, hexa=True)
        return t.eclairer(2.0)
    if nom == "door":  # porte en tôle peinte gris-vert, traverses et écharpe en acier
        t = Toile(w, h, rgb("2e3437"), 0.4)
        tole_ondulee(t, 5, 5, w - 5, h - 5, rgb("57635e"), 10, 1.0, graine=g, rivets=0)
        t.ligne(10, h * 0.25 + 4, w - 10, h * 0.75 - 4, 7, rgb("6d757a"), haut=1.6, reflet=0.5)
        for y in (h * 0.25, h * 0.75):
            t.plaque(5, y - 6, w - 5, y + 6, rgb("6d757a"), 2.6, 1.0, 0.55)
            for x in (11, w / 2, w - 11):
                t.boulon(x, y, 1.9, BOULON, 1.0, hexa=True)
        t.plaque(5, h - 15, w - 5, h - 5, rgb("4c5458"), 2.2, 1.0, 0.45)
        for (a, b, c_, d) in ((0, 0, w, 5), (0, h - 5, w, h), (0, 0, 5, h), (w - 5, 0, w, h)):
            t.plaque(a, b, c_, d, rgb("33393c"), 3.0, 1.0, 0.4)
        return t.eclairer(2.0)
    if nom in ("foundation_top", "floor", "side", "ramp"):
        return tole_larmee(w, h, g, rgb("848c91"), [(64, 128), 2])
    if nom == "floor_under":
        t = Toile(w, h, rgb("666e73"), 0.3)
        for i, (x0, y0, x1, y1) in enumerate(quinconce(0, 0, w, h, 3, g, 64, 128)):
            t.plaque(x0, y0, x1, y1, rgb("6d757a") * (0.95 + 0.1 * r.random()), 1.0, 1.0, 0.35, 0.06, g + i)
        for x in (w * 0.25, w * 0.75):  # deux poutrelles
            t.plaque(x - 7, 0, x + 7, h, rgb("4f565b"), 4.0, 2.0, 0.4)
        return t.eclairer(2.0)
    if nom == "foundation_side":
        t = Toile(w, h, rgb("666e73"), 0.4)
        t.plaque(0, 0, w, h - 11, rgb("6d757a"), 1.5, 1.5, 0.45, 0.06, g)
        t.plaque(0, h - 11, w, h, rgb("4f565b"), 2.5, 1.0, 0.4)
        for x in range(12, w, 24):
            t.boulon(x, 7, 2.0, BOULON, 1.1, hexa=True)
        return t.eclairer(2.0)
    if nom == "skirt":
        return beton(w, h, g)
    return profile(nom, w, h, g, rgb("656c71"), 0.5, boulons=nom != "trim")


def tole_larmee(w, h, g, couleur, decoupe):
    """Tôle larmée (antidérapante) en grandes plaques décalées, boulonnées aux coins."""
    t = Toile(w, h, couleur, 0.5)
    (mini, maxi), rangs = decoupe
    cases = quinconce(0, 0, w, h, rangs, g, mini, maxi)
    pas = 12
    ci = np.floor(t.X / pas)
    cj = np.floor(t.Y / pas)
    cx = (ci + 0.5) * pas
    cy = (cj + 0.5) * pas
    alt = ((ci + cj) % 2 == 0)
    dx, dy = t.X - cx, t.Y - cy
    u = np.where(alt, dx + dy, dx - dy) / 1.414
    v = np.where(alt, dx - dy, dx + dy) / 1.414
    larme = np.clip(1 - (u / (pas * 0.36)) ** 2 - (v / (pas * 0.10)) ** 2, 0, 1)
    t.z = 0.9 * np.sqrt(larme)
    t.teinte(bruit(t.H, t.W, 20 * SS, g, 3), 0.10)
    for (x0, y0, x1, y1) in cases:
        t.ligne(x0, y0, x1, y0, 1.6, prof=1.6)
        t.ligne(x0, y0, x0, y1, 1.6, prof=1.6)
        for bx in (x0 + 5, x1 - 5):
            for by in (y0 + 5, y1 - 5):
                t.boulon(bx, by, 1.8, BOULON, 1.0, hexa=True)
    return t.eclairer(2.0)


def profile(nom, w, h, g, couleur, reflet, boulons=True):
    """Chants, poutres, poteaux : acier brossé (fil le long de v pour trim/poteau, de u pour la poutre)."""
    t = Toile(w, h, couleur, reflet)
    fil = bruit(t.H, t.W, 2, g, 1)
    fil = lisser(fil, 6 * SS) if False else fil
    if nom == "beam":
        f = lisser(np.random.default_rng(g).random((t.H, 1)) * np.ones((1, t.W)), 0.5)
    else:
        f = lisser(np.random.default_rng(g).random((1, t.W)) * np.ones((t.H, 1)), 0.5)
    t.teinte(f, 0.10)
    t.plaque(0, 0, w, h, couleur, 1.5, 2.5, reflet)
    t.teinte(f, 0.10)
    if nom == "post_top":
        t.boulon(w / 2, h / 2, 4.5, BOULON, 1.5, hexa=True)
    elif boulons and nom == "beam":
        t.ligne(0, h / 2, w, h / 2, 6, couleur * 0.85, prof=0.8)
        for x in (w * 0.25, w * 0.75):
            t.boulon(x, h * 0.25, 3.0, BOULON, 1.4, hexa=True)
            t.boulon(x, h * 0.75, 3.0, BOULON, 1.4, hexa=True)
    elif boulons and nom == "post":
        t.ligne(w / 2, 0, w / 2, h, 5, couleur * 0.85, prof=0.8)
        for y in (h * 0.25, h * 0.75):
            t.boulon(w * 0.25, y, 3.0, BOULON, 1.4, hexa=True)
            t.boulon(w * 0.75, y, 3.0, BOULON, 1.4, hexa=True)
    return t.eclairer(1.6)


def beton(w, h, g, couleur="9b9991"):
    """Socle en béton coffré (dessous des fondations), propre."""
    t = Toile(w, h, rgb(couleur), 0.05)
    t.z = bruit(t.H, t.W, 3 * SS, g, 2) * 0.4
    t.teinte(bruit(t.H, t.W, 22 * SS, g + 1, 4), 0.14)
    for y in range(32, h, 32):  # traces des planches de coffrage
        t.ligne(0, y, w, y, 1.2, prof=0.8)
    for x in range(96, w, 96):
        t.ligne(x, 0, x, h, 1.2, prof=0.8)
    vy = np.clip(t.Y / h, 0, 1)
    t.a = t.a * (1.0 - 0.16 * vy[..., None])
    return t.eclairer(1.5)


# ------------------------------------------------------------------------------------------------- blindé


BLINDES = [rgb("50585e"), rgb("555d63"), rgb("4b5358")]
SOUDURE = rgb("3c4247")


def blindage(t, cases, g, haut=3.2, chanfrein=3.0):
    r = np.random.default_rng(g)
    for i, (x0, y0, x1, y1) in enumerate(cases):
        c = BLINDES[int(r.integers(len(BLINDES)))] * (0.96 + 0.08 * r.random())
        t.plaque(x0 + 0.8, y0 + 0.8, x1 - 0.8, y1 - 0.8, c, haut, chanfrein, 0.55, 0.07, g + i)
        # cordon de soudure entre les plaques
        t.ligne(x0, y0, x1, y0, 1.6, SOUDURE, haut=0.8, reflet=0.3)
        t.ligne(x0, y0, x0, y1, 1.6, SOUDURE, haut=0.8, reflet=0.3)
        for bx in (x0 + 8, x1 - 8):
            for by in (y0 + 8, y1 - 8):
                t.boulon(bx, by, 2.8, rgb("6b7378"), 1.6, hexa=True, reflet=0.6)
        for _ in range(int(r.integers(0, 3))):  # rayures légères
            sx, sy = x0 + r.uniform(10, max(11, x1 - x0 - 10)), y0 + r.uniform(10, max(11, y1 - y0 - 10))
            t.ligne(sx, sy, sx + r.uniform(6, 16), sy + r.uniform(-4, 4), 0.6, c * 1.35)


def blinde(nom, w, h, g):
    if nom in ("wall", "roof", "side", "floor_under", "roof_under"):
        t = Toile(w, h, SOUDURE, 0.4)
        blindage(t, quinconce(0, 0, w, h, 3 if nom in ("wall", "side") else 2, g, 56, 100), g)
        return t.eclairer(1.8)
    if nom in ("foundation_top", "floor", "ramp"):
        return tole_larmee(w, h, g, rgb("5b6368"), [(80, 128), 2])
    if nom == "door":
        t = Toile(w, h, SOUDURE, 0.4)
        blindage(t, [(4, 4, w - 4, h * 0.5), (4, h * 0.5, w - 4, h - 4)], g, haut=3.6)
        m = t.boite(w * 0.2, h * 0.12, w * 0.8, h * 0.12 + 6)  # judas
        t.a[m] = rgb("16191c")
        t.creux(m, 3.0)
        for (a, b, c_, d) in ((0, 0, w, 4), (0, h - 4, w, h), (0, 0, 4, h), (w - 4, 0, w, h)):
            t.plaque(a, b, c_, d, rgb("33393d"), 4.2, 1.0, 0.4)
        return t.eclairer(1.8)
    if nom == "foundation_side":
        t = Toile(w, h, SOUDURE, 0.4)
        blindage(t, [(x, 0, x + 64, h) for x in range(0, w, 64)], g)
        return t.eclairer(1.8)
    if nom == "skirt":
        return beton(w, h, g, "8e8c85")
    return profile(nom, w, h, g, rgb("4b5257"), 0.55)


# ------------------------------------------------------------------------------------------------- pierre


GRIS = [rgb("a9a79f"), rgb("9e9d97"), rgb("b1aea5"), rgb("979791")]
MORTIER = rgb("6d6b65")


def maconnerie(t, cases, g):
    r = np.random.default_rng(g)
    rugo = bruit(t.H, t.W, 2.5 * SS, g, 3)
    for i, (x0, y0, x1, y1) in enumerate(cases):
        c = GRIS[int(r.integers(len(GRIS)))] * (0.94 + 0.10 * r.random())
        m = t.boite(x0 + 1.2, y0 + 1.2, x1 - 1.2, y1 - 1.2)
        d = t.distance_bord(x0 + 1.2, y0 + 1.2, x1 - 1.2, y1 - 1.2)
        # bloc taillé : bords arrondis irréguliers, surface piquetée
        bord = lisse(d / 3.0) * (0.85 + 0.3 * rugo)
        z = 3.0 * bord + 0.55 * rugo
        t.z = np.where(m, z, t.z)
        n = bruit(t.H, t.W, 16 * SS, g + i, 3)
        cc = c[None, None, :] * (0.92 + 0.16 * n[..., None])
        t.a = np.where(m[..., None], cc, t.a)


def pierre(nom, w, h, g):
    if nom in ("wall", "side", "foundation_side", "skirt", "ramp"):
        t = Toile(w, h, MORTIER, 0.03)
        maconnerie(t, quinconce(0, 0, w, h, max(1, round(h / 32)), g, 40, 76), g)
        return t.eclairer(2.0, 1.3)
    if nom in ("foundation_top", "floor", "floor_under", "roof_under"):
        t = Toile(w, h, MORTIER, 0.03)
        maconnerie(t, quinconce(0, 0, w, h, 3, g, 56, 100), g)
        return t.eclairer(2.0, 1.3)
    if nom == "roof":  # ardoises
        t = Toile(w, h, rgb("343a3e"), 0.25)
        r = np.random.default_rng(g)
        for k, y in enumerate(range(0, h, 16)):
            dec = 12 if k % 2 else 0
            for x in range(-dec, w, 24):
                c = rgb("5a646b") * (0.88 + 0.2 * r.random())
                m = t.boite(x + 0.6, y, x + 23.4, y + 17)
                d = t.distance_bord(x + 0.6, y, x + 23.4, y + 17)
                pente = 1.0 + 1.5 * (t.Y - y) / 17
                t.z = np.where(m, pente * lisse(d / 1.2), t.z)
                t.a[m] = c
        t.teinte(bruit(t.H, t.W, 8 * SS, g, 3), 0.12)
        return t.eclairer(2.0)
    if nom == "door":  # porte en bois renforcée de fer (comme dans Rust)
        return porte_bois(w, h, g, fer=True)
    t = Toile(w, h, GRIS[0], 0.03)
    t.z = bruit(t.H, t.W, 3 * SS, g, 3) * 0.8
    t.teinte(bruit(t.H, t.W, 10 * SS, g, 3), 0.18)
    t.plaque(0, 0, w, h, GRIS[0], 1.0, max(2, w / 16))
    t.z += bruit(t.H, t.W, 3 * SS, g + 1, 3) * 0.8
    return t.eclairer(2.0)


# ------------------------------------------------------------------------------------------------- bois


BOIS = [rgb("8b6542"), rgb("7e5b3a"), rgb("95714b"), rgb("75553b")]
SOMBRE = rgb("3e2c1e")
CLOU = rgb("6c6f72")


def planches(t, x0, y0, x1, y1, n, g, sens="v", relief=1.6):
    """Planches côte à côte, fil du bois le long de la planche, légèrement bombées, joints creux."""
    r = np.random.default_rng(g)
    larg = ((x1 - x0) if sens == "v" else (y1 - y0)) / n
    for k in range(n):
        c = BOIS[int(r.integers(len(BOIS)))] * (0.93 + 0.12 * r.random())
        if sens == "v":
            a0, a1 = x0 + k * larg, x0 + (k + 1) * larg
            m = t.boite(a0 + 0.5, y0, a1 - 0.5, y1)
            pos = (t.X - a0) / larg
            fil_long, fil_trav = t.Y, t.X
        else:
            a0, a1 = y0 + k * larg, y0 + (k + 1) * larg
            m = t.boite(x0, a0 + 0.5, x1, a1 - 0.5)
            pos = (t.Y - a0) / larg
            fil_long, fil_trav = t.X, t.Y
        bombe = relief * np.sin(np.clip(pos, 0, 1) * math.pi) ** 0.4
        # fil du bois : sinus déformé par un bruit, bien plus long que large
        bru = bruit(t.H, t.W, 18 * SS, g + 7 * k, 3)
        fil = 0.5 + 0.5 * np.sin((fil_trav * 1.7 + bru * 9 + k * 3.1))
        t.z = np.where(m, bombe + 0.25 * fil, t.z)
        cc = c[None, None, :] * (0.86 + 0.24 * fil[..., None]) * (0.95 + 0.1 * bru[..., None])
        t.a = np.where(m[..., None], cc, t.a)
        t.s[m] = 0.08
        if r.random() < 0.45:  # un nœud
            kx = (a0 + a1) / 2 if sens == "v" else r.uniform(x0 + 8, x1 - 8)
            ky = r.uniform(y0 + 8, y1 - 8) if sens == "v" else (a0 + a1) / 2
            d = ((t.X - kx) / 3.0) ** 2 + ((t.Y - ky) / 2.0) ** 2
            mk = (d < 1) & m
            t.a[mk] *= 0.6
            t.z = np.where(mk, t.z - 0.4, t.z)


def clou(t, x, y):
    t.boulon(x, y, 1.1, CLOU, 0.6, reflet=0.4)


def porte_bois(w, h, g, fer=False):
    t = Toile(w, h, SOMBRE, 0.05)
    planches(t, 3, 3, w - 3, h - 3, 3, g, "v")
    if not fer:  # écharpe en biais
        t.ligne(9, h * 0.25, w - 9, h * 0.75, 9, rgb("6e4f33"), haut=2.0)
    for y in (h * 0.22, h * 0.78):
        if fer:
            t.plaque(3, y - 5, w - 3, y + 5, rgb("4c5358"), 2.4, 1.0, 0.5)
            for x in (9, w / 2, w - 9):
                t.boulon(x, y, 1.8, rgb("8a9196"), 1.0, hexa=True)
        else:
            t.plaque(3, y - 5, w - 3, y + 5, rgb("6e4f33"), 2.4, 1.0, 0.08, 0.1, g)
            for x in (9, w / 2, w - 9):
                clou(t, x, y)
    for (a, b, c_, d) in ((0, 0, w, 3), (0, h - 3, w, h), (0, 0, 3, h), (w - 3, 0, w, h)):
        t.plaque(a, b, c_, d, SOMBRE, 2.6, 0.8, 0.05)
    return t.eclairer(1.8)


def bois(nom, w, h, g):
    if nom == "wall":
        t = Toile(w, h, SOMBRE, 0.05)
        planches(t, 0, 0, w, h, 8, g, "v")
        for y in (22, h - 32):  # deux lisses clouées
            t.plaque(0, y, w, y + 10, rgb("6b4b31"), 2.6, 1.0, 0.06, 0.12, g + y)
            for x in range(12, w, 24):
                clou(t, x, y + 5)
        return t.eclairer(1.8)
    if nom == "door":
        return porte_bois(w, h, g)
    if nom in ("foundation_top", "floor"):
        t = Toile(w, h, SOMBRE, 0.05)
        planches(t, 0, 0, w, h, 8, g, "u")
        for x in (8, w / 2, w - 8):
            for y in np.arange(12, h, 24):
                clou(t, x, y)
        return t.eclairer(1.8)
    if nom in ("floor_under", "roof_under", "side", "ramp"):
        t = Toile(w, h, SOMBRE, 0.05)
        planches(t, 0, 0, w, h, 8, g, "u" if nom != "side" else "v")
        return t.eclairer(1.8)
    if nom == "roof":  # bardeaux
        t = Toile(w, h, SOMBRE, 0.04)
        r = np.random.default_rng(g)
        for k, y in enumerate(range(0, h, 16)):
            dec = 12 if k % 2 else 0
            for x in range(-dec, w, 24):
                c = BOIS[int(r.integers(len(BOIS)))] * (0.82 + 0.2 * r.random())
                m = t.boite(x + 0.6, y, x + 23.4, y + 17)
                d = t.distance_bord(x + 0.6, y, x + 23.4, y + 17)
                t.z = np.where(m, (1.0 + 1.6 * (t.Y - y) / 17) * lisse(d / 1.0), t.z)
                t.a[m] = c
        fil = bruit(t.H, t.W, 1.5 * SS, g, 1)
        t.teinte(lisser(fil, 0.5) * 0 + fil, 0.25)
        return t.eclairer(1.8)
    if nom == "foundation_side":
        t = Toile(w, h, SOMBRE, 0.05)
        planches(t, 0, 0, w, h, 2, g, "u")
        return t.eclairer(1.8)
    if nom == "skirt":
        t = Toile(w, h, SOMBRE, 0.05)
        planches(t, 0, 0, w, h, 4, g, "u")
        t.a = t.a * (1 - 0.25 * np.clip(t.Y / h, 0, 1))[..., None]
        return t.eclairer(1.8)
    if nom == "post_top":
        t = Toile(w, h, BOIS[2], 0.05)
        d = np.hypot(t.X - w / 2, t.Y - h / 2)
        t.a = t.a * (1 + 0.14 * np.sin(d * 1.5))[..., None]
        t.plaque(0, 0, w, h, BOIS[2], 1.0, 2.0)
        t.a = t.a * (1 + 0.14 * np.sin(d * 1.5))[..., None]
        return t.eclairer(1.5)
    t = Toile(w, h, SOMBRE, 0.05)
    planches(t, 0, 0, w, h, 1, g, "u" if nom == "beam" else "v", relief=2.5)
    if nom == "beam":
        for x in (w * 0.25, w * 0.75):
            clou(t, x, h / 2)
    return t.eclairer(1.8)


# ------------------------------------------------------------------------------------------------- brindilles


PAILLE = [rgb("c9a85a"), rgb("bf9b4e"), rgb("d3b469"), rgb("b08d45"), rgb("c4a052")]
BRANCHE = rgb("6a4c33")


def tiges(t, y0, y1, g, densite=1.0, enrouler=True):
    """Une botte de paille : tiges fines couchées le long de u, raccord parfait à gauche et à droite."""
    r = np.random.default_rng(g)
    hauteur = y1 - y0
    nb = int(hauteur * 3.4 * densite)
    for i in range(nb):
        yc = y0 + r.uniform(0.6, hauteur - 0.6)
        ep = r.uniform(1.1, 1.9)
        c = PAILLE[int(r.integers(len(PAILLE)))] * r.uniform(0.85, 1.12)
        # légère ondulation périodique (raccord) le long de u
        k = int(r.integers(1, 4))
        ph = r.uniform(0, 2 * math.pi)
        yy = yc + 0.6 * np.sin(2 * math.pi * k * t.X / t.w + ph)
        d = np.abs(t.Y - yy)
        m = (d < ep / 2) & (t.Y >= y0) & (t.Y < y1)
        f = np.sqrt(np.clip(1 - (d / (ep / 2)) ** 2, 0, 1))
        z = 1.0 + 0.9 * f + r.uniform(0, 0.6)
        t.z = np.where(m & (z > t.z), z, t.z)
        nuance = (0.9 + 0.2 * (0.5 + 0.5 * np.sin(2 * math.pi * int(r.integers(1, 5)) * t.X / t.w + ph)))
        cc = c[None, None, :] * nuance[..., None] * (0.85 + 0.15 * f[..., None])
        t.a = np.where((m & (z >= t.z))[..., None], cc, t.a)
    t.s[t.boite(0, y0, t.w, y1)] = 0.12


def brindilles(nom, w, h, g):
    if nom == "wall":  # 6 bottes de paille, une par bande
        t = Toile(w, h, rgb("8f7440"), 0.1)
        bande = h / 6
        for k in range(6):
            tiges(t, k * bande, (k + 1) * bande, g + k)
            # botte un peu plus sombre vers ses bords (le relief vient du modèle)
            m = t.boite(0, k * bande, w, (k + 1) * bande)
            v = (t.Y - k * bande) / bande
            f = 0.66 + 0.34 * np.sin(np.clip(v, 0, 1) * math.pi) ** 0.5
            t.a = np.where(m[..., None], t.a * f[..., None], t.a)
        return t.eclairer(1.1, 0.45, enrouler=True)
    if nom == "straw_end":  # bouts coupés des tiges
        t = Toile(w, h, rgb("5e4a2a"), 0.1)
        r = np.random.default_rng(g)
        for _ in range(420):
            cx, cy, rr = r.uniform(0, w), r.uniform(0, h), r.uniform(1.0, 1.9)
            c = PAILLE[int(r.integers(len(PAILLE)))] * r.uniform(0.8, 1.05)
            d = np.hypot(t.X - cx, t.Y - cy)
            m = d < rr
            t.z = np.where(m, 1 + np.sqrt(np.clip(1 - (d / rr) ** 2, 0, 1)), t.z)
            t.a[m] = c
            t.a[(d < rr * 0.4)] *= 0.7  # tige creuse
        return t.eclairer(1.6)
    if nom == "door":  # panneau de paille tressée sur un cadre de branches
        t = Toile(w, h, rgb("6b5530"), 0.1)
        for y in np.arange(4, h - 4, 8):
            tiges(t, y, y + 8, g + int(y))
        for k, y in enumerate(np.arange(4, h - 4, 8)):  # tressage : une bande sur deux décalée
            for x in np.arange(4 + (k % 2) * 8, w - 4, 16):
                m = t.boite(x, y, x + 8, y + 8)
                t.a[m] *= 0.86
        for (a, b, c_, d) in ((0, 0, w, 4), (0, h - 4, w, h), (0, 0, 4, h), (w - 4, 0, w, h)):
            t.plaque(a, b, c_, d, BRANCHE, 2.4, 1.6, 0.08, 0.18, g)
        t.ligne(6, h - 8, w - 6, 8, 4.0, BRANCHE * 1.1, haut=2.0)
        return t.eclairer(1.6)
    if nom in ("beam", "trim", "post"):  # branche écorcée par endroits
        t = Toile(w, h, BRANCHE, 0.06)
        long_ = t.X if nom == "beam" else t.Y
        trav = t.Y if nom == "beam" else t.X
        bru = bruit(t.H, t.W, 10 * SS, g, 3)
        ecorce = 0.5 + 0.5 * np.sin(trav * 2.2 + bru * 6)
        t.z = 1.2 * np.sin(np.clip(trav / (h if nom == "beam" else w), 0, 1) * math.pi) + 0.35 * ecorce
        t.a = BRANCHE[None, None, :] * (0.8 + 0.35 * ecorce[..., None])
        nu = bruit(t.H, t.W, 18 * SS, g + 1, 2) > 0.62  # bois nu, plus clair
        t.a[nu] = rgb("a5865e") * 1.0
        return t.eclairer(1.8)
    if nom == "post_top":
        t = Toile(w, h, rgb("a5865e"), 0.05)
        d = np.hypot(t.X - w / 2, t.Y - h / 2)
        t.a = t.a * (1 + 0.16 * np.sin(d * 2.0))[..., None]
        m = d > w * 0.42
        t.a[m] = BRANCHE
        return t.eclairer(1.4)
    if nom in ("roof", "foundation_top", "floor", "floor_under", "roof_under", "side", "ramp", "foundation_side", "skirt"):
        t = Toile(w, h, rgb("6b5530"), 0.1)
        bande = 16
        for k, y in enumerate(np.arange(0, h, bande)):
            tiges(t, y, y + bande, g + k)
            m = t.boite(0, y, w, y + bande)
            v = (t.Y - y) / bande
            f = 0.75 + 0.25 * np.sin(np.clip(v, 0, 1) * math.pi) ** 0.6
            t.a = np.where(m[..., None], t.a * f[..., None], t.a)
        if nom in ("foundation_top", "floor", "side", "ramp", "skirt", "foundation_side"):
            for x in np.arange(0, w, 48):  # traverses de branches liées
                t.ligne(x + 4, 0, x + 4, h, 5, BRANCHE, haut=2.2)
        return t.eclairer(1.6, 0.8)
    raise KeyError(nom)


FABRIQUES = {"twig": brindilles, "wood": bois, "stone": pierre, "metal": metal, "armored": blinde}


def noms(palier):
    return {**TAILLES, **EN_PLUS.get(palier, {})}


def fabriquer(palier, nom):
    w, h = noms(palier)[nom]
    graine = sum(map(ord, palier + nom)) * 7919
    return FABRIQUES[palier](nom, w, h, graine)


# ------------------------------------------------------------------------------------------------- aperçu


def quad(dst, src, coins, ombre=1.0):
    """Colle src dans dst sur le quadrilatère coins (haut-gauche, bas-gauche, bas-droite, haut-droite)."""
    xs = [p[0] for p in coins]
    ys = [p[1] for p in coins]
    bx0, by0, bx1, by1 = int(min(xs)), int(min(ys)), int(math.ceil(max(xs))), int(math.ceil(max(ys)))
    loc = [(x - bx0, y - by0) for x, y in coins]
    sw, sh = src.size
    a, b = [], []
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
    """Petite base vue en biais : fondation, deux murs (dont un avec la porte), toit. Ombres de Minecraft."""
    im = Image.new("RGBA", (taille, taille), (196, 206, 212, 255))
    ImageDraw.Draw(im).rectangle((0, taille * 0.62, taille, taille), fill=(150, 140, 110, 255))
    s = taille / 520
    ox, oy, ux, uy, vx, vy, hz = 260 * s, 300 * s, 70 * s, 35 * s, -70 * s, 35 * s, 72 * s

    def P(x, y, z):
        return (ox + x * ux + z * vx, oy + x * uy + z * vy - y * hz)

    L = Q = 3
    quad(im, tex["foundation_side"], [P(0, 1, Q), P(0, 0, Q), P(L, 0, Q), P(L, 1, Q)], 0.8)
    quad(im, tex["foundation_side"], [P(L, 1, Q), P(L, 0, Q), P(L, 0, 0), P(L, 1, 0)], 0.6)
    quad(im, tex["foundation_top"], [P(0, 1, 0), P(0, 1, Q), P(L, 1, Q), P(L, 1, 0)], 1.0)
    mur = tex["wall"]
    quad(im, mur, [P(0, 4, Q), P(0, 1, Q), P(L, 1, Q), P(L, 4, Q)], 0.8)
    quad(im, tex["door"], [P(1.25, 3, Q + 0.01), P(1.25, 1, Q + 0.01), P(2.25, 1, Q + 0.01), P(2.25, 3, Q + 0.01)], 0.8)
    quad(im, mur, [P(L, 4, Q), P(L, 1, Q), P(L, 1, 0), P(L, 4, 0)], 0.6)
    quad(im, tex["roof"], [P(0, 4, 0), P(0, 4, Q), P(L, 4, Q), P(L, 4, 0)], 1.0)
    quad(im, tex["trim"], [P(0, 4.15, Q), P(0, 4, Q), P(L, 4, Q), P(L, 4.15, Q)], 0.8)
    quad(im, tex["trim"], [P(L, 4.15, Q), P(L, 4, Q), P(L, 4, 0), P(L, 4.15, 0)], 0.6)
    return im


def planche(textures, dossier):
    liste = list(TAILLES) + ["straw_end"]
    W = 150
    out = Image.new("RGB", (W * len(liste), W * len(textures) + 24), (28, 28, 28))
    d = ImageDraw.Draw(out)
    for j, n in enumerate(liste):
        d.text((j * W + 6, 6), n, fill=(230, 230, 230))
    for i, p in enumerate(textures):
        for j, n in enumerate(liste):
            if n not in textures[p]:
                continue
            im = textures[p][n]
            s = 140 / max(im.size)
            im = im.resize((max(1, int(im.size[0] * s)), max(1, int(im.size[1] * s))), Image.LANCZOS)
            out.paste(im, (j * W + 5, 24 + i * W + 5), im)
    out.save(dossier / "planche_textures.png")


def main():
    ap = argparse.ArgumentParser()
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", type=Path)
    g.add_argument("--installer", action="store_true")
    ap.add_argument("--paliers", nargs="*", default=PALIERS)
    a = ap.parse_args()
    paliers = [p for p in PALIERS if p in a.paliers]
    textures = {p: {n: fabriquer(p, n) for n in noms(p)} for p in paliers}
    if a.installer:
        for p in paliers:
            for n, im in textures[p].items():
                im.save(TEXTURES / p / f"{n}.png")
        print("textures installées :", ", ".join(paliers))
        return
    a.apercu.mkdir(parents=True, exist_ok=True)
    for p in paliers:
        (a.apercu / p).mkdir(exist_ok=True)
        for n, im in textures[p].items():
            im.save(a.apercu / p / f"{n}.png")
    planche(textures, a.apercu)
    noms_fr = {"twig": "BRINDILLES", "wood": "BOIS", "stone": "PIERRE", "metal": "TOLE", "armored": "BLINDE"}
    rendus = [maquette(textures[p]) for p in paliers]
    out = Image.new("RGB", (520 * len(rendus), 560), (28, 28, 28))
    d = ImageDraw.Draw(out)
    for i, (p, im) in enumerate(zip(paliers, rendus)):
        out.paste(im, (i * 520, 40))
        d.text((i * 520 + 12, 12), noms_fr[p], fill=(240, 240, 240))
    out.save(a.apercu / "maquettes.png")
    murs = [textures[p]["wall"] for p in paliers] + [textures[p]["door"].resize((96, 192), Image.LANCZOS) for p in paliers]
    out = Image.new("RGB", (sum(m.width * 2 + 10 for m in murs), 384), (28, 28, 28))
    x = 0
    for m in murs:
        z = m.resize((m.width * 2, m.height * 2), Image.LANCZOS)
        out.paste(z, (x, 0), z)
        x += z.width + 10
    out.save(a.apercu / "murs.png")
    print("aperçu écrit dans", a.apercu)


if __name__ == "__main__":
    main()
