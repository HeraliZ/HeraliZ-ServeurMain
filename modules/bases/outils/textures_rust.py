#!/usr/bin/env python3
"""
Textures des pièces de construction de CRac façon Rust, pour les 5 paliers (brindilles, bois, pierre, tôle, blindé),
en double définition (64 points par bloc au lieu de 32). Version 3 (24/10, retours du propriétaire sur la v2 « trop
enfantine ») : matériaux réalistes et en relief.

- brindilles : vraies branches brutes (écorce fissurée, nœuds, moignons, bois clair là où l'écorce est partie) de
  diamètres variés, liées à la corde, petits jours sombres entre elles ; plus aucune paille ;
- bois : planches de récupération irrégulières clouées sur des traverses, fil du bois marqué, bords usés, fentes,
  clous rouillés qui coulent, planches plus claires ou plus sombres, joints sombres ;
- pierre : moellons taillés à la main, faces bombées et piquetées, arêtes ébréchées, joints de mortier en retrait,
  crasse dans les creux et en bas ;
- tôle : tôles d'acier de tailles différentes qui se chevauchent, rivetées (rivets en relief), bords relevés, ombres
  portées d'une tôle sur l'autre, rouille sur les bords et en coulures sous les rivets, rayures ;
- blindé : plaques épaisses chanfreinées, cordons de soudure, renforts soudés en relief, gros boulons, peinture écaillée
  sur les arêtes.

Chaque texture est un vrai matériau : une couleur (albedo), un relief (carte de hauteur) et un reflet, éclairés par le
calcul (lumière rasante en haut à gauche, ombres portées d'un relief sur l'autre, occlusion dans les creux, reflets sur
le métal), dessinés deux fois plus grands puis réduits (bords lisses, pas d'escaliers). Rien n'est dessiné à la main :
on modifie ce script, puis on le relance.

    python modules/bases/outils/textures_rust.py --apercu DOSSIER    textures + planche + maquettes d'une petite base
    python modules/bases/outils/textures_rust.py --installer         écrit dans assets/crac/textures/block/building/<palier>/

Tailles (même rapport qu'avant, deux fois plus fin ; le jeu lit les coordonnées de texture de 0 à 1, cf. PieceBakery) :
wall, floor, floor_under, foundation_top, roof, roof_under, side, ramp 192 x 192 (3 x 3 blocs) ; door 64 x 128 ;
foundation_side 192 x 64 ; skirt 192 x 128 ; trim, beam, post 64 x 64 ; post_top 32 x 32. Brindilles en plus :
straw_end 64 x 64 (bout des fagots : bouts coupés des branches) ; leur wall.png est fait de 6 bandes horizontales (un
fagot de branches chacune, branches le long de u, raccord parfait à gauche et à droite : TwigMeshes la répète tous les
48 px de mur).
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


def _flou(a, r, axe, mode):
    """Flou gaussien le long d'un axe : direct pour un petit rayon, par transformée de Fourier pour un grand."""
    n = int(r * 3) + 1
    pad = [(0, 0), (0, 0)]
    pad[axe] = (n, n)
    p = np.pad(a, pad, mode=mode)
    taille = p.shape[axe]
    if n < 10:
        k = np.exp(-0.5 * (np.arange(-n, n + 1) / r) ** 2)
        k /= k.sum()
        if axe == 0:
            return sum(k[i] * p[i:i + a.shape[0]] for i in range(2 * n + 1))
        return sum(k[i] * p[:, i:i + a.shape[1]] for i in range(2 * n + 1))
    f = np.fft.rfftfreq(taille)
    g = np.exp(-2 * (math.pi * r * f) ** 2)
    g = g[:, None] if axe == 0 else g[None, :]
    q = np.fft.irfft(np.fft.rfft(p, axis=axe) * g, n=taille, axis=axe)
    return q[n:n + a.shape[0]] if axe == 0 else q[:, n:n + a.shape[1]]


def lisser(a, r, enrouler=False, ry=None):
    """Flou gaussien (séparable) ; r le long de x, ry le long de y (r si absent) ; enrouler = raccord parfait."""
    ry = r if ry is None else ry
    mode = "wrap" if enrouler else "edge"
    if ry > 0:
        a = _flou(a, ry, 0, mode)
    if r > 0:
        a = _flou(a, r, 1, mode)
    return a


def bruit(h, w, echelle, graine, octaves=3, enrouler=False, ey=None):
    """Bruit doux entre 0 et 1 (plusieurs octaves) ; ey = échelle le long de y (étiré) ; enrouler = raccord parfait."""
    r = np.random.default_rng(graine)
    tot = np.zeros((h, w))
    amp, poids = 1.0, 0.0
    for o in range(octaves):
        ex = max(0.6, echelle / (2 ** o))
        eyy = ex if ey is None else max(0.6, ey / (2 ** o))
        tot += amp * lisser(r.random((h, w)), ex * 0.6, enrouler, eyy * 0.6)
        poids += amp
        amp *= 0.55
    tot /= poids
    tot -= tot.min()
    return tot / max(1e-9, tot.max())


def lisse(t):
    t = np.clip(t, 0, 1)
    return t * t * (3 - 2 * t)


def melange(a, b, f):
    f = np.asarray(f)
    if f.ndim == 2:
        f = f[..., None]
    return a * (1 - f) + b * f


def decaler(a, dy, dx, enrouler):
    """Valeur de a au point (y + dy, x + dx), décalages entiers en points de calcul."""
    if enrouler:
        return np.roll(a, (-dy, -dx), axis=(0, 1))
    p = max(abs(dy), abs(dx))
    if p == 0:
        return a
    q = np.pad(a, p, mode="edge")
    return q[p + dy:p + dy + a.shape[0], p + dx:p + dx + a.shape[1]]


class Toile:
    """Matériau en cours : couleur, relief, reflet ; coordonnées données en points de la texture finale."""

    def __init__(self, w, h, couleur, reflet=0.0, enrouler=False):
        self.w, self.h = w, h
        self.W, self.H = w * SS, h * SS
        self.X = (np.arange(self.W)[None, :] + 0.5) / SS
        self.Y = (np.arange(self.H)[:, None] + 0.5) / SS
        self.a = np.ones((self.H, self.W, 3)) * couleur
        self.z = np.zeros((self.H, self.W))
        self.s = np.ones((self.H, self.W)) * reflet
        self.bord = np.zeros((self.H, self.W))  # près d'un bord visible (1) ou loin (0) : rouille, écaillage, usure
        self.enrouler = enrouler

    def B(self, echelle, graine, octaves=3, ey=None):
        return bruit(self.H, self.W, echelle * SS, graine, octaves, self.enrouler, None if ey is None else ey * SS)

    def boite(self, x0, y0, x1, y1):
        return (self.X >= x0) & (self.X < x1) & (self.Y >= y0) & (self.Y < y1)

    def distance_bord(self, x0, y0, x1, y1):
        return np.minimum(np.minimum(self.X - x0, x1 - self.X), np.minimum(self.Y - y0, y1 - self.Y))

    def fenetre(self, x0, y0, x1, y1):
        i0, i1 = max(0, int(y0 * SS) - 1), min(self.H, int(math.ceil(y1 * SS)) + 1)
        j0, j1 = max(0, int(x0 * SS) - 1), min(self.W, int(math.ceil(x1 * SS)) + 1)
        return slice(i0, i1), slice(j0, j1)

    def courbure(self, r=1.2):
        """> 0 sur les arêtes et les bosses, < 0 dans les creux."""
        return self.z - lisser(self.z, r * SS, self.enrouler)

    def plaque(self, x0, y0, x1, y1, couleur, haut, chanfrein=1.5, reflet=None, var=0.0, graine=0, sur=False):
        """Plaque posée (relief = haut, bords chanfreinés) ; sur = posée par-dessus le relief existant."""
        m = self.boite(x0, y0, x1, y1)
        d = self.distance_bord(x0, y0, x1, y1)
        prof = haut * lisse(d / max(0.01, chanfrein))
        base = lisser(self.z, 1.5 * SS, self.enrouler) if sur else 0
        self.z = np.where(m, base + prof, self.z)
        c = np.asarray(couleur, float)
        if var:
            n = self.B(14, graine, 3)
            c = c[None, None, :] * (1 + (n[..., None] - 0.5) * var)
            self.a = np.where(m[..., None], c, self.a)
        else:
            self.a[m] = c
        if reflet is not None:
            self.s[m] = reflet
        self.bord = np.where(m, 1 - lisse(d / 4.0), self.bord)
        return m

    def boulon(self, cx, cy, r, couleur, haut=1.6, hexa=False, reflet=0.5):
        sy, sx = self.fenetre(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1)
        X, Y = self.X[:, sx], self.Y[sy, :]
        dx, dy = X - cx, Y - cy
        d = np.hypot(dx, dy)
        if hexa:
            ang = np.arctan2(dy, dx)
            d = d * np.cos(np.mod(ang, math.pi / 3) - math.pi / 6) / math.cos(math.pi / 6)
        m = d < r
        if not m.any():
            return
        dome = np.sqrt(np.clip(1 - (d / r) ** 2, 0, 1)) if not hexa else np.clip(1.6 - 1.2 * d / r, 0, 1) ** 0.7
        z = self.z[sy, sx]
        base = float(np.median(z[m]))
        self.z[sy, sx] = np.where(m, base + haut * dome, z)
        a = self.a[sy, sx]
        a[m] = couleur
        self.s[sy, sx][m] = reflet
        # petite ombre de contact autour de la tête
        anneau = (d >= r) & (d < r + 0.8)
        a[anneau] *= 0.78

    def ligne(self, x0, y0, x1, y1, ep, couleur=None, haut=None, prof=None, reflet=None):
        """Segment épais (soudure, tige, rainure) ; haut = relief ajouté en dôme, prof = rainure."""
        sy, sx = self.fenetre(min(x0, x1) - ep, min(y0, y1) - ep, max(x0, x1) + ep, max(y0, y1) + ep)
        X, Y = self.X[:, sx], self.Y[sy, :]
        vx, vy = x1 - x0, y1 - y0
        l2 = vx * vx + vy * vy or 1
        t = np.clip(((X - x0) * vx + (Y - y0) * vy) / l2, 0, 1)
        d = np.hypot(X - (x0 + t * vx), Y - (y0 + t * vy))
        m = d < ep / 2
        f = np.sqrt(np.clip(1 - (d / (ep / 2)) ** 2, 0, 1))
        z = self.z[sy, sx]
        if haut is not None:
            z[...] = np.where(m, z + haut * f, z)
        if prof is not None:
            z[...] = np.where(m, z - prof * f, z)
        if couleur is not None:
            self.a[sy, sx][m] = couleur
        if reflet is not None:
            self.s[sy, sx][m] = reflet
        tout = np.zeros((self.H, self.W), bool)
        tout[sy, sx] = m
        return tout

    def teinte(self, n, force, m=None):
        f = (1 + (n[..., None] - 0.5) * force)
        if m is None:
            self.a = self.a * f
        else:
            self.a = np.where(m[..., None], self.a * f, self.a)

    def rayures(self, nb, graine, m=None, clair=1.35, longueur=(5, 18)):
        r = np.random.default_rng(graine)
        for _ in range(nb):
            sx, sy = r.uniform(0, self.w), r.uniform(0, self.h)
            if m is not None and not m[min(self.H - 1, int(sy * SS)), min(self.W - 1, int(sx * SS))]:
                continue
            a = r.uniform(-0.5, 0.5) + (0 if r.random() < 0.7 else math.pi / 2)
            ln = r.uniform(*longueur)
            ex, ey = sx + ln * math.cos(a), sy + ln * math.sin(a)
            mm = self.ligne(sx, sy, ex, ey, r.uniform(0.35, 0.6), prof=0.12)
            self.a[mm] = np.clip(self.a[mm] * clair, 0, 1)

    def coulures(self, points, couleur, force=0.55, graine=0):
        """Traînées (rouille, crasse) qui coulent vers le bas depuis des points (x, y, longueur, largeur)."""
        r = np.random.default_rng(graine)
        acc = np.zeros((self.H, self.W))
        for (x, y, ln, lg) in points:
            sy, sx = self.fenetre(x - lg * 3, y, x + lg * 3, y + ln)
            X, Y = self.X[:, sx], self.Y[sy, :]
            v = (Y - y) / ln
            ondule = 0.6 * np.sin(v * r.uniform(3, 7) + r.uniform(0, 6))
            larg = lg * (1 + 0.6 * v)
            f = np.exp(-((X - x - ondule) / larg) ** 2) * np.clip(1 - v, 0, 1) ** 1.3 * (v >= 0)
            acc[sy, sx] = np.maximum(acc[sy, sx], f)
        n = self.B(3, graine, 2, ey=12)
        acc = np.clip(acc * (0.5 + n), 0, 1) * force
        self.a = melange(self.a, couleur[None, None, :] * (0.8 + 0.4 * n[..., None]), acc)
        self.s *= 1 - 0.6 * acc

    def rouiller(self, force, graine, seuil=0.5, pique=0.35):
        """Rouille là où force est grande (bords, bas des plaques), découpée par un bruit : sombre, orangée, piquée."""
        n = self.B(7, graine, 4)
        fin = self.B(1.4, graine + 1, 2)
        m = np.clip((force * 1.25 + (n - 0.5) * 1.2 + (fin - 0.5) * 0.45 - seuil) * 3.0, 0, 1)
        c = melange(ROUILLE_SOMBRE[None, None, :] * np.ones_like(self.a), ROUILLE_CLAIRE[None, None, :] * np.ones_like(self.a),
                    lisse((self.B(4, graine + 2, 3) - 0.3) * 1.6))
        c = c * (0.8 + 0.35 * fin[..., None])
        self.a = melange(self.a, c, m)
        self.s *= 1 - 0.85 * m
        self.z += m * (fin - 0.5) * pique
        return m

    def eclairer(self, relief=2.0, occlusion=1.0, ombre=1.0, rugueux=26):
        """Éclairage : normales tirées du relief, lumière diffuse + reflet, ombres portées, occlusion ; puis réduction."""
        z = self.z * relief * SS
        gy, gx = np.gradient(z)
        n = np.dstack([-gx, -gy, np.ones_like(z)])
        n /= np.linalg.norm(n, axis=2, keepdims=True)
        diff = np.clip((n * LUMIERE).sum(axis=2), 0, 1)
        amb = 0.36
        lum = np.minimum(1.35, amb + (1 - amb) * diff / LUMIERE[2])
        moitie = LUMIERE + np.array([0, 0, 1.0])
        moitie /= np.linalg.norm(moitie)
        nh = np.clip((n * moitie).sum(axis=2), 0, 1)
        spec = (nh ** rugueux * 0.55 + nh ** 6 * 0.08) * self.s
        # occlusion : creux fins et creux larges
        cav1 = lisser(self.z, 1.5 * SS, self.enrouler) - self.z
        cav2 = lisser(self.z, 5 * SS, self.enrouler) - self.z
        ao = np.clip(1 - np.clip(cav1, 0, None) * 0.30 * occlusion - np.clip(cav2, 0, None) * 0.09 * occlusion, 0.30, 1)
        # ombres portées : un relief cache la lumière rasante au relief plus bas, derrière lui
        lx, ly = LUMIERE[0], LUMIERE[1]
        lh = math.hypot(lx, ly)
        pente = 0.85  # hauteur gagnée par point parcouru vers la lumière (lumière un peu plus rasante que l'éclairage)
        zz = self.z * SS
        cache = np.zeros_like(zz)
        for k in range(1, 9 * SS):
            dx, dy = int(round(lx / lh * k)), int(round(ly / lh * k))
            cache = np.maximum(cache, decaler(zz, dy, dx, self.enrouler) - zz - k * pente)
        sh = np.clip(cache / (1.2 * SS), 0, 1)
        sh = lisser(sh, 0.6 * SS, self.enrouler)
        lum = lum * (1 - 0.55 * sh * ombre)
        spec = spec * (1 - sh)
        out = self.a * (lum * ao)[..., None] + spec[..., None]
        im = Image.fromarray((np.clip(out, 0, 1) * 255).astype(np.uint8), "RGB")
        return im.resize((self.w, self.h), Image.LANCZOS).convert("RGBA")


def rangees(y0, y1, nb, g, var=0.25):
    """nb rangées de hauteurs un peu différentes entre y0 et y1."""
    r = np.random.default_rng(g)
    p = 1 + r.uniform(-var, var, nb)
    p = p / p.sum() * (y1 - y0)
    bornes = [y0]
    for v in p:
        bornes.append(bornes[-1] + v)
    bornes[-1] = y1
    return [(round(a, 1), round(b, 1)) for a, b in zip(bornes, bornes[1:])]


def appareil(x0, y0, x1, y1, lignes, graine, mini, maxi):
    """Plaques ou pierres en quinconce : chaque rangée coupée à d'autres endroits que la précédente."""
    r = np.random.default_rng(graine)
    cases = []
    for (ya, yb) in lignes:
        x = x0
        premier = float(r.uniform(max(6, mini * 0.4), maxi))
        while x < x1 - 0.5:
            larg = premier if x == x0 else float(r.uniform(mini, maxi))
            xb = min(x1, x + larg)
            if x1 - xb < mini * 0.5:
                xb = x1
            cases.append((x, ya, xb, yb))
            x = xb
    return cases


def quinconce(x0, y0, x1, y1, nb, graine, mini, maxi):
    return appareil(x0, y0, x1, y1, [(y0 + k * (y1 - y0) / nb, y0 + (k + 1) * (y1 - y0) / nb) for k in range(nb)],
                    graine, mini, maxi)


ROUILLE_SOMBRE = rgb("3f2a1e")
ROUILLE_CLAIRE = rgb("8a5636")
CRASSE = rgb("3a352e")


def crasse_bas(t, force=0.22, graine=0):
    """Saleté qui monte du sol : bas de la texture plus sombre, en traînées."""
    n = t.B(5, graine, 3, ey=18)
    v = np.clip(t.Y / t.h, 0, 1) ** 2.2
    f = np.clip(v * (0.6 + 0.8 * n), 0, 1) * force
    t.a = melange(t.a, CRASSE[None, None, :], f)


# ------------------------------------------------------------------------------------------------- tôle


TOLES = [rgb("5f6364"), rgb("686a69"), rgb("585c5e"), rgb("6b6b65"), rgb("525657"), rgb("62645e"), rgb("525a4d"), rgb("665a4d")]
ACIER = rgb("4a5054")
FOND = rgb("23272a")
BOULON = rgb("8b9194")


def toles_rivetees(t, cases, g, couleurs, ondulees=(), epaisseur=1.4, pas_rivet=9.0, coulures=None):
    """Tôles posées une à une dans le désordre, qui se chevauchent : chacune repose sur ce qui est dessous (ombre portée
    au bord), bord légèrement relevé, cabossée, rivetée tout autour."""
    r = np.random.default_rng(g)
    ordre = r.permutation(len(cases))
    for rang, i in enumerate(ordre):
        x0, y0, x1, y1 = cases[i]
        x0, y0 = max(0.0, x0 - r.uniform(0, 3)), max(0.0, y0 - r.uniform(0, 3))
        x1, y1 = min(float(t.w), x1 + r.uniform(1, 4)), min(float(t.h), y1 + r.uniform(1, 4))
        m = t.boite(x0, y0, x1, y1)
        d = t.distance_bord(x0, y0, x1, y1)
        dessous = lisser(t.z, 2.0 * SS, t.enrouler)
        bosse = (t.B(r.uniform(14, 26), g + 11 * rang, 3) - 0.5) * 2.6 + (t.B(5, g + 13 * rang, 2) - 0.5) * 0.35
        z = dessous + epaisseur * lisse(d / 0.8) + 0.45 * (1 - lisse(d / 2.2)) * lisse(d / 0.5) + bosse
        if i in ondulees:
            z = z + 0.85 * np.cos(2 * math.pi * (t.X - x0) / 8.0) * lisse(d / 3.0)
        t.z = np.where(m, z, t.z)
        c = couleurs[int(r.integers(len(couleurs)))] * r.uniform(0.94, 1.06)
        tache = t.B(r.uniform(10, 22), g + 7 * rang, 4)
        fil = t.B(r.uniform(14, 24), g + 3 * rang, 2, ey=0.7)  # acier laminé : traînées horizontales
        cc = c[None, None, :] * (0.74 + 0.40 * tache[..., None]) * (0.93 + 0.14 * fil[..., None])
        t.a = np.where(m[..., None], cc, t.a)
        t.s[m] = r.uniform(0.35, 0.55)
        t.bord = np.where(m, 1 - lisse(d / 5.0), t.bord)
        # rivets
        ins = 2.7
        for (ax, ay, bx, by) in ((x0 + ins, y0 + ins, x1 - ins, y0 + ins), (x0 + ins, y1 - ins, x1 - ins, y1 - ins),
                                 (x0 + ins, y0 + ins, x0 + ins, y1 - ins), (x1 - ins, y0 + ins, x1 - ins, y1 - ins)):
            ln = math.hypot(bx - ax, by - ay)
            nb = max(1, int(ln // pas_rivet))
            for k in range(nb + 1):
                if r.random() < 0.08:
                    continue
                px, py = ax + (bx - ax) * k / nb, ay + (by - ay) * k / nb
                t.boulon(px, py, 1.25, BOULON * r.uniform(0.75, 1.05), 0.95, reflet=0.55)
                if coulures is not None and r.random() < 0.22:
                    coulures.append((px, py + 1, r.uniform(8, 28), r.uniform(0.8, 1.6)))
        if coulures is not None:
            for _ in range(int(r.integers(1, 4))):  # la rouille coule du bas de la tôle
                coulures.append((r.uniform(x0 + 3, max(x0 + 4, x1 - 3)), y1 - 1, r.uniform(10, 34), r.uniform(1.2, 2.6)))


def finir_metal(t, g, rouille=0.45, coul=(), rayures=14):
    t.coulures(coul, ROUILLE_CLAIRE * 0.85, 0.85, g + 5)
    v = np.clip(t.Y / t.h, 0, 1)
    plaques = np.clip((t.B(14, g + 21, 3) - 0.5) * 2.4, 0, 1)  # taches de rouille au milieu des tôles aussi
    t.rouiller(t.bord * 0.9 + v ** 3 * 0.35 + plaques * 1.1, g + 9, seuil=1.0 - rouille)
    crasse = t.B(9, g + 22, 4)
    t.a = melange(t.a, CRASSE[None, None, :], np.clip((crasse - 0.55) * 1.2, 0, 0.35))
    t.rayures(rayures, g + 13)
    convexe = np.clip((t.courbure(0.8) - 0.12) * 2.0, 0, 1) * t.bord  # arêtes frottées : acier plus clair
    t.a = melange(t.a, np.clip(t.a * 1.5, 0, 1), convexe * 0.6)


def metal(nom, w, h, g):
    r = np.random.default_rng(g)
    if nom in ("wall", "side"):
        t = Toile(w, h, FOND, 0.4)
        cadre = 6 if nom == "wall" else 0
        cases = appareil(cadre, cadre, w - cadre, h - cadre, rangees(cadre, h - cadre, 3, g, 0.3), g, 38, 104)
        coul = []
        toles_rivetees(t, cases, g, TOLES, ondulees={int(r.integers(len(cases)))}, coulures=coul)
        if nom == "wall":
            for k in range(2):  # plaques de rapiéçage vissées par-dessus
                pw, ph = float(r.uniform(26, 44)), float(r.uniform(20, 34))
                px, py = float(r.uniform(14, w - 14 - pw)), float(r.uniform(18 + k * 86, 50 + k * 86))
                toles_rivetees(t, [(px, py, px + pw, py + ph)], g + 40 + k, [rgb("7d8487"), rgb("6a7059")], epaisseur=1.6,
                               pas_rivet=7, coulures=coul)
            fil = t.B(20, g + 50, 2, ey=0.7)
            for (a, b, c_, d) in ((0, 0, w, cadre), (0, h - cadre, w, h), (0, 0, cadre, h), (w - cadre, 0, w, h)):
                m = t.plaque(a, b, c_, d, ACIER, 4.2, 1.4, 0.45)
                t.teinte(fil, 0.18, m)
            for x in range(cadre + 15, w - cadre, 30):
                for y in (cadre / 2, h - cadre / 2):
                    t.boulon(x, y, 1.9, BOULON * 0.9, 1.1, hexa=True)
            for y in range(cadre + 15, h - cadre, 30):
                for x in (cadre / 2, w - cadre / 2):
                    t.boulon(x, y, 1.9, BOULON * 0.9, 1.1, hexa=True)
        finir_metal(t, g, 0.62, coul)
        crasse_bas(t, 0.25, g)
        return t.eclairer(2.0)
    if nom in ("roof", "roof_under"):  # tôles ondulées qui se recouvrent, celle du haut par-dessus
        t = Toile(w, h, FOND, 0.4)
        coul = []
        lignes = rangees(0, h, 3, g, 0.2)
        for k, (ya, yb) in enumerate(lignes):
            for j, (x0, _, x1, _) in enumerate(appareil(0, ya, w, yb, [(ya, yb)], g + k, 60, 120)):
                m = t.boite(x0, ya, x1, yb + 5)
                d = t.distance_bord(x0, ya, x1, yb + 5)
                z = 1.6 + 1.1 * np.cos(2 * math.pi * t.X / 12.0) * lisse(d / 1.5) + (t.B(24, g + k * 9 + j, 3) - 0.5) * 0.8
                z = z + 0.9 * lisse(d / 0.8) + (len(lignes) - k) * 0.0
                t.z = np.where(m, z, t.z)
                c = TOLES[int(r.integers(len(TOLES)))] * (0.85 if nom == "roof_under" else 1.0)
                tache = t.B(16, g + 31 * k + j, 4)
                t.a = np.where(m[..., None], c[None, None, :] * (0.86 + 0.24 * tache[..., None]), t.a)
                t.s[m] = 0.45
                t.bord = np.where(m, 1 - lisse(d / 5.0), t.bord)
                if nom == "roof":
                    for x in np.arange(x0 + 6, x1 - 2, 12):
                        t.boulon(x, yb + 1.5, 1.3, BOULON, 0.9)
                        if r.random() < 0.3:
                            coul.append((x, yb + 2.5, r.uniform(6, 16), 0.7))
        finir_metal(t, g, 0.4 if nom == "roof" else 0.3, coul, 8)
        return t.eclairer(2.0)
    if nom == "door":  # porte en tôle : deux tôles rivetées sur un cadre, trois raidisseurs, poignée
        t = Toile(w, h, FOND, 0.4)
        coul = []
        toles_rivetees(t, [(5, 5, w - 5, h * 0.52), (5, h * 0.48, w - 5, h - 5)], g, [rgb("5d675f"), rgb("66706a")], coulures=coul)
        for y in (h * 0.2, h * 0.5, h * 0.8):
            t.plaque(7, y - 5, w - 7, y + 5, rgb("4f5850"), 2.4, 1.5, 0.5, sur=True)
            for x in (12, w - 12):
                t.boulon(x, y, 1.8, BOULON, 1.0, hexa=True)
        t.plaque(w - 16, h * 0.42, w - 9, h * 0.58, rgb("3a3f42"), 3.0, 1.0, 0.6, sur=True)  # poignée
        for (a, b, c_, d) in ((0, 0, w, 5), (0, h - 5, w, h), (0, 0, 5, h), (w - 5, 0, w, h)):
            t.plaque(a, b, c_, d, ACIER, 4.5, 1.2, 0.45)
        finir_metal(t, g, 0.45, coul, 6)
        crasse_bas(t, 0.3, g)
        return t.eclairer(2.0)
    if nom in ("foundation_top", "floor", "ramp"):
        return tole_larmee(w, h, g, rgb("6c7377"), [(64, 128), 2])
    if nom == "floor_under":
        t = Toile(w, h, FOND, 0.3)
        coul = []
        toles_rivetees(t, appareil(0, 0, w, h, rangees(0, h, 3, g), g, 60, 120), g, [rgb("5c6367"), rgb("53595d")], coulures=coul)
        for x in (w * 0.25, w * 0.75):  # deux poutrelles en I
            t.plaque(x - 8, 0, x + 8, h, rgb("3f454a"), 5.0, 2.0, 0.4, sur=True)
            t.ligne(x, 0, x, h, 4, prof=1.2)
        finir_metal(t, g, 0.35, coul, 4)
        return t.eclairer(2.0)
    if nom == "foundation_side":
        t = Toile(w, h, FOND, 0.4)
        coul = []
        toles_rivetees(t, appareil(0, 0, w, h - 12, [(0, h - 12)], g, 50, 90), g, TOLES, coulures=coul)
        t.plaque(0, h - 12, w, h, ACIER, 4.0, 1.4, 0.4)
        for x in range(12, w, 24):
            t.boulon(x, h - 6, 2.0, BOULON, 1.1, hexa=True)
        finir_metal(t, g, 0.5, coul, 4)
        crasse_bas(t, 0.35, g)
        return t.eclairer(2.0)
    if nom == "skirt":
        return beton(w, h, g)
    return profile(nom, w, h, g, rgb("575e62"), 0.5, boulons=nom != "trim")


def tole_larmee(w, h, g, couleur, decoupe, peinte=False):
    """Tôle larmée (antidérapante) en grandes plaques décalées : larmes en relief usées au sommet, joints rouillés."""
    t = Toile(w, h, couleur, 0.5)
    (mini, maxi), rangs = decoupe
    cases = quinconce(0, 0, w, h, rangs, g, mini, maxi)
    pas = 10
    ci = np.floor(t.X / pas)
    cj = np.floor(t.Y / pas)
    cx = (ci + 0.5) * pas
    cy = (cj + 0.5) * pas
    alt = ((ci + cj) % 2 == 0)
    dx, dy = t.X - cx, t.Y - cy
    u = np.where(alt, dx + dy, dx - dy) / 1.414
    v = np.where(alt, dx - dy, dx + dy) / 1.414
    larme = np.clip(1 - (u / (pas * 0.38)) ** 2 - (v / (pas * 0.11)) ** 2, 0, 1)
    t.z = 1.1 * np.sqrt(larme) + (t.B(30, g, 3) - 0.5) * 0.8
    t.teinte(t.B(18, g, 4), 0.22)
    t.s[...] = 0.5
    for (x0, y0, x1, y1) in cases:
        d = t.distance_bord(x0, y0, x1, y1)
        m = t.boite(x0, y0, x1, y1)
        t.bord = np.where(m, 1 - lisse(d / 4.0), t.bord)
        t.ligne(x0, y0, x1, y0, 2.0, FOND, prof=2.2)
        t.ligne(x0, y0, x0, y1, 2.0, FOND, prof=2.2)
        for bx in (x0 + 5, x1 - 5):
            for by in (y0 + 5, y1 - 5):
                t.boulon(bx, by, 2.0, BOULON, 1.2, hexa=True)
    usure = np.clip(np.sqrt(larme) - 0.55, 0, 1) * 2.0 * (0.4 + 0.6 * t.B(12, g + 4, 3))  # sommets frottés
    t.a = melange(t.a, np.clip(t.a * 1.6, 0, 1), usure)
    t.rouiller(t.bord * 0.8, g + 9, seuil=0.62)
    t.rayures(20, g + 13)
    return t.eclairer(2.0)


def profile(nom, w, h, g, couleur, reflet, boulons=True, peinture=None):
    """Chants, poutres, poteaux : profilé d'acier (fil le long de la pièce), boulonné, rouillé aux arêtes."""
    t = Toile(w, h, couleur, reflet)
    long_ = nom == "beam"
    fil = t.B(18, g, 2, ey=0.7) if long_ else t.B(0.7, g, 2, ey=18)
    t.plaque(0, 0, w, h, couleur, 2.0, 3.0, reflet)
    t.teinte(fil, 0.22)
    t.z += (t.B(14, g + 1, 3) - 0.5) * 0.6
    if nom == "post_top":
        t.boulon(w / 2, h / 2, 5.0, BOULON, 1.8, hexa=True)
    elif boulons and nom == "beam":
        t.ligne(0, h / 2, w, h / 2, 6, couleur * 0.8, prof=1.0)
        for x in (w * 0.25, w * 0.75):
            t.boulon(x, h * 0.25, 3.2, BOULON, 1.6, hexa=True)
            t.boulon(x, h * 0.75, 3.2, BOULON, 1.6, hexa=True)
    elif boulons and nom == "post":
        t.ligne(w / 2, 0, w / 2, h, 6, couleur * 0.8, prof=1.0)
        for y in (h * 0.25, h * 0.75):
            t.boulon(w * 0.25, y, 3.2, BOULON, 1.6, hexa=True)
            t.boulon(w * 0.75, y, 3.2, BOULON, 1.6, hexa=True)
    if peinture is not None:
        ecailler(t, peinture, g + 7)
    else:
        t.rouiller(t.bord * 0.8, g + 9, seuil=0.55)
    t.rayures(5, g + 13, longueur=(3, 10))
    return t.eclairer(1.8)


def beton(w, h, g, couleur="85837c"):
    """Socle en béton coffré (dessous des fondations) : bullage, traces de planches, coulures, bas encrassé."""
    t = Toile(w, h, rgb(couleur), 0.05)
    t.z = t.B(2.5, g, 3) * 0.7 + t.B(20, g + 3, 3) * 0.8
    t.teinte(t.B(22, g + 1, 4), 0.22)
    t.teinte(t.B(1.2, g + 2, 1), 0.10)
    r = np.random.default_rng(g)
    for _ in range(140):  # petites bulles d'air
        x, y, rr = r.uniform(0, w), r.uniform(0, h), r.uniform(0.4, 1.0)
        sy, sx = t.fenetre(x - rr, y - rr, x + rr, y + rr)
        d = np.hypot(t.X[:, sx] - x, t.Y[sy, :] - y)
        m = d < rr
        t.z[sy, sx][m] -= 0.8
    for y in range(32, h, 32):  # traces des planches de coffrage
        t.ligne(0, y, w, y, 1.4, prof=0.9)
    for x in range(96, w, 96):
        t.ligne(x, 0, x, h, 1.4, prof=0.9)
    t.coulures([(r.uniform(0, w), r.uniform(0, h * 0.3), r.uniform(20, 60), r.uniform(1, 3)) for _ in range(10)], CRASSE, 0.35, g)
    crasse_bas(t, 0.45, g)
    return t.eclairer(1.8)


# ------------------------------------------------------------------------------------------------- blindé


PEINTURE = [rgb("4e5449"), rgb("4a5047"), rgb("52574d")]
NU = rgb("7f8487")
SOUDURE = rgb("2f3336")


def cordon(t, x0, y0, x1, y1, ep=2.6, haut=1.3):
    """Cordon de soudure : bourrelet ondulé (écailles de la soudure), bleui et sombre."""
    sy, sx = t.fenetre(min(x0, x1) - ep, min(y0, y1) - ep, max(x0, x1) + ep, max(y0, y1) + ep)
    X, Y = t.X[:, sx], t.Y[sy, :]
    vx, vy = x1 - x0, y1 - y0
    ln = math.hypot(vx, vy) or 1
    s = ((X - x0) * vx + (Y - y0) * vy) / ln
    d = np.abs((X - x0) * vy - (Y - y0) * vx) / ln
    m = (d < ep / 2) & (s >= 0) & (s <= ln)
    f = np.sqrt(np.clip(1 - (d / (ep / 2)) ** 2, 0, 1))
    ecaille = 0.7 + 0.3 * np.abs(np.sin(s * 2.4 + d * 0.8))
    z = t.z[sy, sx]
    z[...] = np.where(m, np.maximum(z, lisser(t.z, 1.5 * SS, t.enrouler)[sy, sx]) + haut * f * ecaille, z)
    a = t.a[sy, sx]
    a[m] = SOUDURE * (0.85 + 0.35 * ecaille[m])[:, None]
    t.s[sy, sx][m] = 0.35
    # auréole de chaleur autour du cordon
    halo = (d < ep * 1.6) & (d >= ep / 2) & (s >= 0) & (s <= ln)
    a[halo] = a[halo] * 0.82 + rgb("3b3a44") * 0.18


def ecailler(t, peinture, g, force=1.0):
    """Peinture écaillée : arêtes et bords usés jusqu'au métal nu (un peu en retrait, bord sombre), éclats au hasard."""
    conv = np.clip(t.courbure(1.0) * 1.5, 0, 1)
    n = t.B(3, g, 4)
    fin = t.B(0.9, g + 1, 2)
    m = np.clip((t.bord * 0.8 + conv * 1.0 + (n - 0.5) * 1.0 + (fin - 0.5) * 0.6 - 0.40 / force) * 4.0, 0, 1)
    e = 0.55 * t.B(2.4, g + 2, 2) + 0.75 * t.B(14, g + 10, 3)  # éclats groupés par endroits (coups, frottements)
    eclats = np.clip((e - np.quantile(e, 0.94)) * 12.0, 0, 1)
    m = np.maximum(m, eclats * force)
    nu = NU[None, None, :] * (0.85 + 0.3 * fin[..., None])
    peint = t.a
    bordure = np.clip(lisser(m, 0.7 * SS, t.enrouler) - m, 0, 1) * 2.5
    t.a = melange(peint * (1 - 0.35 * np.clip(bordure, 0, 1))[..., None], nu, m)
    t.s = t.s * (1 - m) + 0.7 * m
    t.z -= m * 0.3
    return m


def blindage(t, cases, g, haut=4.0, chanfrein=3.5):
    r = np.random.default_rng(g)
    for i, (x0, y0, x1, y1) in enumerate(cases):
        c = PEINTURE[int(r.integers(len(PEINTURE)))] * r.uniform(0.95, 1.05)
        m = t.plaque(x0 + 1.2, y0 + 1.2, x1 - 1.2, y1 - 1.2, c, haut, chanfrein, 0.25, 0.10, g + i)
        t.z = np.where(m, t.z + (t.B(22, g + 17 * i, 3) - 0.5) * 1.3, t.z)
        t.teinte(t.B(10, g + 5 * i, 4), 0.30, m)
        t.teinte(t.B(2.5, g + 6 * i, 2, ey=12), 0.16, m)  # traînées verticales
        t.teinte(t.B(5, g + 8 * i, 3), 0.22, m)  # peinture passée par endroits
    for i, (x0, y0, x1, y1) in enumerate(cases):
        cordon(t, x0, y0, x1, y0)
        cordon(t, x0, y0, x0, y1)
        for bx in (x0 + 9, x1 - 9):
            for by in (y0 + 9, y1 - 9):
                if x1 - x0 > 26 and y1 - y0 > 26:
                    t.boulon(bx, by, 3.0, rgb("5d6266"), 1.9, hexa=True, reflet=0.5)


def renfort(t, x0, y0, x1, y1, g, boulons=True):
    """Fer plat épais soudé par-dessus : chanfreiné, cordons sur ses deux bords, gros boulons."""
    m = t.plaque(x0, y0, x1, y1, PEINTURE[0] * 0.92, 3.4, 1.6, 0.3, 0.12, g, sur=True)
    if x1 - x0 > y1 - y0:
        cordon(t, x0, y0, x1, y0, 2.0, 1.0)
        cordon(t, x0, y1, x1, y1, 2.0, 1.0)
        if boulons:
            for x in np.arange(x0 + 12, x1 - 6, 24):
                t.boulon(x, (y0 + y1) / 2, 2.6, rgb("5d6266"), 1.8, hexa=True)
    else:
        cordon(t, x0, y0, x0, y1, 2.0, 1.0)
        cordon(t, x1, y0, x1, y1, 2.0, 1.0)
        if boulons:
            for y in np.arange(y0 + 12, y1 - 6, 24):
                t.boulon((x0 + x1) / 2, y, 2.6, rgb("5d6266"), 1.8, hexa=True)
    return m


def blinde(nom, w, h, g):
    if nom in ("wall", "side", "roof", "floor_under", "roof_under"):
        t = Toile(w, h, SOUDURE, 0.3)
        blindage(t, appareil(0, 0, w, h, rangees(0, h, 3 if nom in ("wall", "side") else 2, g, 0.15), g, 56, 104), g)
        if nom == "wall":
            renfort(t, 0, h * 0.30 - 6, w, h * 0.30 + 6, g + 1)
            renfort(t, 0, h * 0.72 - 6, w, h * 0.72 + 6, g + 2)
        ecailler(t, PEINTURE[0], g + 3)
        t.rayures(16, g + 13, clair=1.5)
        rr = np.random.default_rng(g + 1)
        t.coulures([(x, y, rr.uniform(10, 30), rr.uniform(0.8, 1.8)) for x, y in rr.uniform(0, w, (14, 2))], ROUILLE_CLAIRE, 0.55, g)
        t.a = melange(t.a, CRASSE[None, None, :], np.clip((t.B(12, g + 30, 4) - 0.5) * 1.0, 0, 0.35))
        crasse_bas(t, 0.3, g)
        return t.eclairer(1.9, 1.1)
    if nom in ("foundation_top", "floor", "ramp"):
        return tole_larmee(w, h, g, rgb("4f5550"), [(80, 128), 2])
    if nom == "door":
        t = Toile(w, h, SOUDURE, 0.3)
        blindage(t, [(4, 4, w - 4, h * 0.5), (4, h * 0.5, w - 4, h - 4)], g, haut=4.5)
        renfort(t, 8, 8, 16, h - 8, g + 1, False)
        renfort(t, w - 16, 8, w - 8, h - 8, g + 2, False)
        m = t.boite(w * 0.25, h * 0.14, w * 0.75, h * 0.14 + 6)  # judas
        t.a[m] = rgb("0e1012")
        t.z[m] -= 4.0
        for y in (h * 0.3, h * 0.7):
            t.boulon(w / 2, y, 3.2, rgb("5d6266"), 2.0, hexa=True)
        for (a, b, c_, d) in ((0, 0, w, 4), (0, h - 4, w, h), (0, 0, 4, h), (w - 4, 0, w, h)):
            t.plaque(a, b, c_, d, rgb("2c3033"), 6.0, 1.2, 0.3)
        ecailler(t, PEINTURE[0], g + 3)
        t.rayures(6, g + 13, clair=1.5)
        crasse_bas(t, 0.35, g)
        return t.eclairer(1.9, 1.1)
    if nom == "foundation_side":
        t = Toile(w, h, SOUDURE, 0.3)
        blindage(t, [(x, 0, x + 64, h) for x in range(0, w, 64)], g)
        ecailler(t, PEINTURE[0], g + 3)
        crasse_bas(t, 0.4, g)
        return t.eclairer(1.9, 1.1)
    if nom == "skirt":
        return beton(w, h, g, "7a7872")
    return profile(nom, w, h, g, PEINTURE[1], 0.3, peinture=PEINTURE[1])


# ------------------------------------------------------------------------------------------------- pierre


GRIS = [rgb("8a877f"), rgb("7f7d77"), rgb("939087"), rgb("78766f"), rgb("8c8579"), rgb("83837d")]
MORTIER = rgb("5b5850")


def moellons(t, cases, g, joint=2.0, bombe=4.5):
    """Moellons taillés : contour irrégulier aux coins arrondis, face bombée et bosselée, piquetée, arêtes ébréchées ;
    joints de mortier en retrait, sableux."""
    r = np.random.default_rng(g)
    rugo = t.B(1.0, g, 3)
    grain = t.B(3.5, g + 1, 3)
    contour = t.B(5, g + 2, 3)
    contour_fin = t.B(1.5, g + 6, 2)
    t.z = -0.6 + 0.6 * t.B(1.2, g + 3, 2)  # mortier rugueux, en retrait
    t.a = MORTIER[None, None, :] * (0.78 + 0.4 * t.B(2, g + 4, 2))[..., None] * np.ones_like(t.a)
    for i, (x0, y0, x1, y1) in enumerate(cases):
        c = GRIS[int(r.integers(len(GRIS)))] * r.uniform(0.88, 1.08)
        c = c * np.array([r.uniform(0.97, 1.03), 1.0, r.uniform(0.96, 1.02)])
        x0j, y0j = x0 + joint * r.uniform(0.6, 1.4), y0 + joint * r.uniform(0.6, 1.4)
        x1j, y1j = x1 - joint * r.uniform(0.6, 1.4), y1 - joint * r.uniform(0.6, 1.4)
        cx, cy, hx, hy = (x0j + x1j) / 2, (y0j + y1j) / 2, (x1j - x0j) / 2, (y1j - y0j) / 2
        coin = min(hx, hy) * r.uniform(0.25, 0.5)
        sy, sx = t.fenetre(x0 - 2, y0 - 2, x1 + 2, y1 + 2)
        X, Y = t.X[:, sx], t.Y[sy, :]
        qx, qy = np.abs(X - cx) - (hx - coin), np.abs(Y - cy) - (hy - coin)
        dist = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - coin
        d = -dist + (contour[sy, sx] - 0.5) * 4.0 + (contour_fin[sy, sx] - 0.5) * 1.2
        m = d > 0
        # face bombée : dôme sur toute la pierre (pas un plateau), un peu penché, bosselé
        dome = np.sqrt(np.clip(d / max(2.0, min(hx, hy) * 0.9), 0, 1))
        pente = (X - cx) * r.uniform(-0.025, 0.025) + (Y - cy) * r.uniform(-0.035, 0.035)
        bosse = (t.B(r.uniform(6, 12), g + 7 * i, 3)[sy, sx] - 0.5) * 2.2
        z = 0.8 + bombe * dome + pente + bosse * dome + 0.8 * (rugo[sy, sx] - 0.5)
        t.z[sy, sx] = np.where(m, z, t.z[sy, sx])
        cc = c[None, None, :] * (0.82 + 0.28 * grain[sy, sx][..., None]) * (0.92 + 0.14 * rugo[sy, sx][..., None])
        t.a[sy, sx] = np.where(m[..., None], cc, t.a[sy, sx])
        t.bord[sy, sx] = np.where(m, 1 - lisse(d / 4.0), t.bord[sy, sx])
        # éclats sur les arêtes
        for _ in range(int(r.integers(2, 6))):
            cote = int(r.integers(4))
            ex = r.uniform(x0j, x1j) if cote < 2 else (x0j if cote == 2 else x1j)
            ey = r.uniform(y0j, y1j) if cote >= 2 else (y0j if cote == 0 else y1j)
            er = r.uniform(1.5, 3.8)
            sy2, sx2 = t.fenetre(ex - er, ey - er, ex + er, ey + er)
            dd = np.hypot(t.X[:, sx2] - ex, t.Y[sy2, :] - ey) / er
            dans = np.zeros((t.H, t.W), bool)
            dans[sy, sx] = m
            me = (dd < 1) & dans[sy2, sx2]
            t.z[sy2, sx2] = np.where(me, t.z[sy2, sx2] - 1.8 * (1 - dd) - 0.4, t.z[sy2, sx2])
            t.a[sy2, sx2][me] *= 1.07  # cassure plus claire
    t.teinte(t.B(1.2, g + 5, 2), 0.12)
    t.teinte(t.B(24, g + 8, 3), 0.18)  # grandes nuances de la paroi


def pierre(nom, w, h, g):
    r = np.random.default_rng(g)
    if nom in ("wall", "side", "foundation_side", "skirt", "ramp"):
        t = Toile(w, h, MORTIER, 0.03)
        nb = max(1, round(h / 30))
        moellons(t, appareil(0, 0, w, h, rangees(0, h, nb, g, 0.3), g, 34, 72), g)
        t.coulures([(r.uniform(0, w), r.uniform(0, h), r.uniform(10, 40), r.uniform(1, 2.5)) for _ in range(12)], CRASSE, 0.25, g)
        crasse_bas(t, 0.35, g)
        return t.eclairer(2.0, 1.2)
    if nom in ("foundation_top", "floor", "floor_under", "roof_under"):
        t = Toile(w, h, MORTIER, 0.03)
        moellons(t, appareil(0, 0, w, h, rangees(0, h, 4, g, 0.3), g, 40, 90), g, bombe=3.0)
        return t.eclairer(2.0, 1.2)
    if nom == "roof":  # lauzes / ardoises épaisses qui se recouvrent
        t = Toile(w, h, rgb("2a2e30"), 0.15)
        for k, y in enumerate(range(-4, h, 16)):
            dec = float(r.uniform(4, 16)) if k % 2 else 0.0
            x = -dec
            while x < w:
                lg = float(r.uniform(18, 30))
                c = rgb("575d61") * r.uniform(0.82, 1.12)
                bas = y + 18 + r.uniform(-1.5, 1.5)
                m = t.boite(x + 0.7, y, x + lg - 0.7, bas)
                d = t.distance_bord(x + 0.7, y, x + lg - 0.7, bas)
                pente = 0.6 + 2.6 * (t.Y - y) / 18
                z = pente * lisse(d / 1.4) + (t.B(6, g + k * 31 + int(x), 2) - 0.5) * 0.8
                t.z = np.where(m, z, t.z)
                t.a = np.where(m[..., None], c[None, None, :] * (0.9 + 0.2 * t.B(4, g + k, 2)[..., None]), t.a)
                t.bord = np.where(m, 1 - lisse(d / 3), t.bord)
                x += lg
        t.teinte(t.B(1.0, g, 2), 0.15)
        crasse_bas(t, 0.2, g)
        return t.eclairer(2.0, 1.2)
    if nom == "door":  # porte en bois renforcée de fer (comme dans Rust)
        return porte_bois(w, h, g, fer=True)
    # chants, poteaux, poutres : pierre taillée au ciseau
    t = Toile(w, h, GRIS[0], 0.03)
    t.plaque(0, 0, w, h, GRIS[0], 2.5, max(3, w / 10))
    long_ = nom == "beam"
    t.z += (t.B(1.0, g + 1, 3) - 0.5) * 0.9 + (t.B(10, g + 2, 3) - 0.5) * 1.8 + (t.B(4, g + 6, 3) - 0.5) * 0.9
    t.teinte(t.B(5, g + 3, 4), 0.22)
    t.teinte(t.B(1.0, g + 4, 2), 0.10)
    crasse_bas(t, 0.2, g)
    return t.eclairer(2.0, 1.2)


# ------------------------------------------------------------------------------------------------- bois


BOIS = [rgb("6b5845"), rgb("74624e"), rgb("5f4e3e"), rgb("7b6b59"), rgb("665b4e"), rgb("84705a"), rgb("51432f"), rgb("6e6a60")]
SOMBRE = rgb("1d1712")
TRAVERSE = rgb("5a4734")


def fil_du_bois(t, long_, trav, g, k):
    """Fil du bois : veines serrées déformées (étirées le long de la planche), fibres fines."""
    if long_ is t.X:
        lent = bruit(t.H, t.W, 40 * SS, g + 7 * k, 3, t.enrouler, 4 * SS)
        fibre = bruit(t.H, t.W, 12 * SS, g + 7 * k + 1, 2, t.enrouler, 0.6 * SS)
    else:
        lent = bruit(t.H, t.W, 4 * SS, g + 7 * k, 3, t.enrouler, 40 * SS)
        fibre = bruit(t.H, t.W, 0.6 * SS, g + 7 * k + 1, 2, t.enrouler, 12 * SS)
    veine = 0.5 + 0.5 * np.sin(trav * 1.35 + lent * 16)
    veine = veine ** 2.5
    return veine, fibre


def une_planche(t, x0, y0, x1, y1, c, g, k, sens="v", bombe=1.4):
    """Une planche : bombée, arêtes arrondies et usées, fil marqué, fentes au bout, un nœud parfois."""
    r = np.random.default_rng(g + 101 * k)
    m = t.boite(x0, y0, x1, y1)
    d = t.distance_bord(x0, y0, x1, y1)
    long_, trav = (t.Y, t.X) if sens == "v" else (t.X, t.Y)
    veine, fibre = fil_du_bois(t, long_, trav, g, k)
    arete = lisse(d / 1.8)
    z = 1.0 + bombe * arete + 0.45 * veine * arete + 0.25 * fibre + (t.B(30, g + k, 2) - 0.5) * 0.8
    t.z = np.where(m, z + r.uniform(-0.4, 0.6), t.z)
    cc = c[None, None, :] * (1.06 - 0.30 * veine[..., None]) * (0.92 + 0.16 * fibre[..., None])
    cc = cc * (0.9 + 0.2 * t.B(14, g + 13 * k, 3)[..., None])
    t.a = np.where(m[..., None], cc, t.a)
    t.s[m] = 0.06
    t.bord = np.where(m, 1 - arete, t.bord)
    # fentes le long du fil, depuis un bout
    for _ in range(int(r.integers(0, 3)) if min(x1 - x0, y1 - y0) > 5 else 0):
        if sens == "v":
            fx = r.uniform(x0 + 2, x1 - 2)
            fy0 = y0 if r.random() < 0.5 else y1
            fy1 = fy0 + (r.uniform(6, 22) if fy0 == y0 else -r.uniform(6, 22))
            t.ligne(fx, fy0, fx + r.uniform(-1, 1), fy1, r.uniform(0.6, 1.0), SOMBRE * 1.5, prof=1.2)
        else:
            fy = r.uniform(y0 + 2, y1 - 2)
            fx0 = x0 if r.random() < 0.5 else x1
            fx1 = fx0 + (r.uniform(6, 22) if fx0 == x0 else -r.uniform(6, 22))
            t.ligne(fx0, fy, fx1, fy + r.uniform(-1, 1), r.uniform(0.6, 1.0), SOMBRE * 1.5, prof=1.2)
    if r.random() < 0.5 and x1 - x0 > 8 and y1 - y0 > 14:  # nœud : tache sombre, veines qui l'entourent
        kx = r.uniform(x0 + 3, x1 - 3)
        ky = r.uniform(y0 + 6, y1 - 6)
        ax, ay = (2.2, 3.4) if sens == "v" else (3.4, 2.2)
        dd = np.hypot((t.X - kx) / ax, (t.Y - ky) / ay)
        mk = (dd < 1) & m
        anneaux = 0.5 + 0.5 * np.sin(dd * 9)
        t.a = np.where(mk[..., None], t.a * (0.45 + 0.25 * anneaux[..., None]), t.a)
        t.z = np.where(mk, t.z - 0.5 + 0.3 * anneaux, t.z)
        halo = (dd >= 1) & (dd < 2.2) & m
        t.a = np.where(halo[..., None], t.a * 0.85, t.a)
    return m


def clou(t, x, y, coul=None, r=None):
    t.boulon(x, y, 1.3, rgb("4a3326") * (0.8 + 0.4 * (r.random() if r is not None else 0.5)), 0.7, reflet=0.15)
    if coul is not None:
        coul.append((x, y + 1, (r.uniform(4, 12) if r is not None else 8), 0.6))


def planches(t, x0, y0, x1, y1, g, sens="v", larg=(18, 32), joints=True):
    """Planches de récupération de largeurs différentes côte à côte, aboutées par endroits, joints sombres."""
    r = np.random.default_rng(g)
    a, fin = (x0, x1) if sens == "v" else (y0, y1)
    b0, b1 = (y0, y1) if sens == "v" else (x0, x1)
    k = 0
    while a < fin - 0.5:
        lg = float(r.uniform(*larg))
        b = min(fin, a + lg)
        if fin - b < larg[0] * 0.5:
            b = fin
        c = BOIS[int(r.integers(len(BOIS)))] * r.uniform(0.9, 1.08)
        coupes = [b0]
        if joints and r.random() < 0.45:
            coupes.append(float(r.uniform(b0 + (b1 - b0) * 0.25, b0 + (b1 - b0) * 0.75)))
        coupes.append(b1)
        for j, (u0, u1) in enumerate(zip(coupes, coupes[1:])):
            cj = c if j == 0 else BOIS[int(r.integers(len(BOIS)))] * r.uniform(0.9, 1.08)
            if sens == "v":
                une_planche(t, a + 0.6, u0 + 0.4, b - 0.6, u1 - 0.4, cj, g, k * 3 + j, "v")
            else:
                une_planche(t, u0 + 0.4, a + 0.6, u1 - 0.4, b - 0.6, cj, g, k * 3 + j, "u")
        a = b
        k += 1


def porte_bois(w, h, g, fer=False):
    t = Toile(w, h, SOMBRE, 0.05)
    r = np.random.default_rng(g)
    coul = []
    planches(t, 3, 3, w - 3, h - 3, g, "v", (14, 22), joints=False)
    if not fer:  # écharpe en biais, clouée
        m = t.ligne(10, h * 0.78, w - 10, h * 0.24, 9, None, haut=2.6)
        t.a = np.where(m[..., None], t.a * 0.92, t.a)
        for f in (0.2, 0.5, 0.8):
            clou(t, 10 + (w - 20) * f, h * 0.78 + (h * 0.24 - h * 0.78) * f, coul, r)
    for y in (h * 0.2, h * 0.8):
        if fer:
            t.plaque(3, y - 6, w - 3, y + 6, rgb("3a3f42"), 2.6, 1.0, 0.5, sur=True)
            for x in (9, w / 2, w - 9):
                t.boulon(x, y, 2.0, rgb("6e7478"), 1.1, hexa=True)
                coul.append((x, y + 2, r.uniform(8, 18), 0.9))
        else:
            m = une_planche(t, 3, y - 6, w - 3, y + 6, TRAVERSE, g + 3, int(y), "u", 1.2)
            t.z = np.where(m, t.z + 1.8, t.z)
            for x in (9, w / 2, w - 9):
                clou(t, x, y - 2, coul, r)
                clou(t, x + 1.5, y + 2.5, coul, r)
    if fer:
        t.rouiller(t.bord * 0.5, g + 9, seuil=0.75)
    t.coulures(coul, ROUILLE_SOMBRE * 1.2, 0.5, g)
    for (a, b, c_, d) in ((0, 0, w, 3), (0, h - 3, w, h), (0, 0, 3, h), (w - 3, 0, w, h)):
        t.plaque(a, b, c_, d, SOMBRE, 3.0, 0.8, 0.05)
    crasse_bas(t, 0.3, g)
    return t.eclairer(1.9, 1.1)


def bois(nom, w, h, g):
    r = np.random.default_rng(g)
    if nom in ("wall", "side"):
        t = Toile(w, h, SOMBRE, 0.05)
        planches(t, 0, 0, w, h, g, "v")
        coul = []
        if nom == "wall":
            for y in (26, h - 38):  # deux traverses clouées par-dessus
                m = une_planche(t, 0, y, w, y + 12, TRAVERSE, g + y, 900 + y, "u", 1.3)
                t.z = np.where(m, t.z + 2.2, t.z)
                for x in np.arange(r.uniform(5, 12), w, 22):
                    clou(t, x + r.uniform(-1, 1), y + 3.5 + r.uniform(-0.5, 0.5), coul, r)
                    clou(t, x + r.uniform(-1, 1), y + 8.5 + r.uniform(-0.5, 0.5), coul, r)
        t.coulures(coul, ROUILLE_SOMBRE * 1.3, 0.55, g)
        t.a = melange(t.a, np.clip(t.a * 1.3, 0, 1), np.clip(t.courbure(1.0), 0, 1) * 0.5)  # arêtes usées
        crasse_bas(t, 0.3, g)
        return t.eclairer(2.0, 1.15)
    if nom == "door":
        return porte_bois(w, h, g)
    if nom in ("foundation_top", "floor", "floor_under", "roof_under", "ramp"):
        t = Toile(w, h, SOMBRE, 0.05)
        planches(t, 0, 0, w, h, g, "u", (16, 28))
        coul = []
        if nom in ("foundation_top", "floor", "ramp"):
            for x in (6, w / 2, w - 6):
                for y in np.arange(r.uniform(6, 12), h, 22):
                    clou(t, x + r.uniform(-1, 1), y, None, r)
        t.a = melange(t.a, np.clip(t.a * 1.3, 0, 1), np.clip(t.courbure(1.0), 0, 1) * 0.5)
        return t.eclairer(2.0, 1.15)
    if nom == "roof":  # bardeaux fendus
        t = Toile(w, h, SOMBRE, 0.04)
        for k, y in enumerate(range(-4, h, 16)):
            x = -float(r.uniform(0, 12))
            while x < w:
                lg = float(r.uniform(14, 26))
                c = BOIS[int(r.integers(len(BOIS)))] * r.uniform(0.75, 0.95)
                bas = y + 18 + r.uniform(-1.2, 1.2)
                m = une_planche(t, x + 0.6, y, x + lg - 0.6, bas, c, g, k * 50 + int(x + 20), "v", 0.6)
                t.z = np.where(m, t.z + 2.4 * (t.Y - y) / 18, t.z)
                x += lg
        crasse_bas(t, 0.15, g)
        return t.eclairer(2.0, 1.15)
    if nom == "foundation_side":
        t = Toile(w, h, SOMBRE, 0.05)
        planches(t, 0, 0, w, h, g, "u", (28, 34))
        crasse_bas(t, 0.4, g)
        return t.eclairer(2.0, 1.15)
    if nom == "skirt":
        t = Toile(w, h, SOMBRE, 0.05)
        planches(t, 0, 0, w, h, g, "u", (28, 36))
        crasse_bas(t, 0.55, g)
        return t.eclairer(2.0, 1.15)
    if nom == "post_top":  # bois de bout : cernes, fentes rayonnantes
        t = Toile(w, h, rgb("8a7258"), 0.05)
        d = np.hypot(t.X - w / 2 - 2, t.Y - h / 2 + 1)
        cernes = 0.5 + 0.5 * np.sin(d * 2.4 + t.B(4, g, 2) * 4)
        t.a = t.a * (0.82 + 0.25 * cernes[..., None])
        t.z = 0.3 * cernes
        ang = np.arctan2(t.Y - h / 2, t.X - w / 2)
        for a0 in r.uniform(-math.pi, math.pi, 3):
            fente = (np.abs(np.angle(np.exp(1j * (ang - a0)))) < 0.06) & (d > 3)
            t.a[fente] = SOMBRE
            t.z[fente] -= 1.0
        return t.eclairer(1.6)
    t = Toile(w, h, SOMBRE, 0.05)
    une_planche(t, 0, 0, w, h, BOIS[1], g, 0, "u" if nom == "beam" else "v", bombe=2.5)
    if nom == "beam":
        for x in (w * 0.25, w * 0.75):
            clou(t, x, h / 2, None, r)
    t.a = melange(t.a, np.clip(t.a * 1.3, 0, 1), np.clip(t.courbure(1.0), 0, 1) * 0.5)
    return t.eclairer(2.0, 1.15)


# ------------------------------------------------------------------------------------------------- brindilles


ECORCES = [rgb("4d4033"), rgb("5c4d3d"), rgb("40362c"), rgb("625a50"), rgb("534735"), rgb("3a3027"), rgb("6b6257")]
AUBIER = rgb("a08c6c")
CORDE = rgb("7d6c50")
JOUR = rgb("15120e")


def branche(t, y0, y1, xs, ray, g, k, sens="u"):
    """Une branche brute couchée le long de u (sens « u ») ou de v : cylindre (relief selon son épaisseur), écorce
    fissurée, nœuds et moignons, plaques d'écorce partie (bois clair). Courbe périodique : raccord parfait."""
    r = np.random.default_rng(g * 31 + k)
    long_, trav = (t.X, t.Y) if sens == "u" else (t.Y, t.X)
    L = t.w if sens == "u" else t.h
    ph = r.uniform(0, 2 * math.pi, 3)
    centre = xs + sum(r.uniform(0.3, 1.2) / (j + 1) * np.sin(2 * math.pi * (j + 1) * long_ / L + ph[j]) for j in range(3))
    rayon = ray * (1 + 0.12 * np.sin(2 * math.pi * long_ / L * int(r.integers(1, 3)) + ph[0] * 2))
    d = np.abs(trav - centre)
    m = (d < rayon) & (trav >= y0) & (trav < y1)
    f = np.sqrt(np.clip(1 - (d / rayon) ** 2, 0, 1))
    if sens == "u":
        fis = bruit(t.H, t.W, 10 * SS, g + k, 3, t.enrouler, 0.45 * SS)
        grain = bruit(t.H, t.W, 3 * SS, g + k + 5, 2, t.enrouler, 0.8 * SS)
    else:
        fis = bruit(t.H, t.W, 0.45 * SS, g + k, 3, t.enrouler, 10 * SS)
        grain = bruit(t.H, t.W, 0.8 * SS, g + k + 5, 2, t.enrouler, 3 * SS)
    fissure = np.clip((0.42 - fis) * 4, 0, 1)
    z = 0.6 + ray * 0.85 * f - 0.55 * fissure * f + 0.2 * grain
    haut = m & (z > t.z)
    t.z = np.where(haut, z, t.z)
    c = ECORCES[int(r.integers(len(ECORCES)))] * r.uniform(0.9, 1.1)
    cc = c[None, None, :] * (0.72 + 0.55 * grain[..., None]) * (1 - 0.55 * fissure[..., None])
    cc = cc * (0.8 + 0.25 * f[..., None])  # le dessous de la branche dans l'ombre du fagot
    # écorce partie par plaques : bois plus clair, lisse
    pele = t.B(r.uniform(8, 16), g + k + 9, 3)
    nu = np.clip((pele - r.uniform(0.66, 0.8) - 0.02 * max(0.0, ray - 5)) * 8, 0, 1) * (f > 0.2)
    bois_nu = AUBIER[None, None, :] * (0.68 + 0.3 * fibre_long(t, sens, g + k)[..., None])
    cc = melange(cc, bois_nu, nu)
    t.a = np.where(haut[..., None], cc, t.a)
    t.s = np.where(haut, 0.05 + 0.08 * nu, t.s)
    t.z = np.where(haut, t.z - 0.3 * nu, t.z)
    # nœuds et moignons de petites branches coupées
    for _ in range(int(r.integers(0, 3))):
        p = r.uniform(0, L)
        cpos = float(np.interp(p, long_.ravel() if sens == "u" else long_.ravel(), (centre * np.ones_like(long_)).ravel()))
        q = cpos + r.uniform(-0.4, 0.4) * ray
        kx, ky = (p, q) if sens == "u" else (q, p)
        kr = r.uniform(0.9, 1.6) * min(ray, 3.5) * 0.6
        sy, sx = t.fenetre(kx - kr * 1.6, ky - kr * 1.6, kx + kr * 1.6, ky + kr * 1.6)
        ax_, ay_ = (kr * 1.6, kr) if sens == "u" else (kr, kr * 1.6)
        dd = np.hypot((t.X[:, sx] - kx) / ax_, (t.Y[sy, :] - ky) / ay_)
        mk = (dd < 1) & haut[sy, sx]
        if r.random() < 0.5:  # moignon coupé : bois clair au centre, bourrelet
            t.z[sy, sx] = np.where(mk, t.z[sy, sx] + 1.0 * np.sqrt(np.clip(1 - dd, 0, 1)), t.z[sy, sx])
            a = t.a[sy, sx]
            a[mk & (dd < 0.55)] = AUBIER * 0.9
            a[mk & (dd >= 0.55)] *= 0.82
        else:  # nœud sombre
            a = t.a[sy, sx]
            a[mk] *= 0.7
            t.z[sy, sx] = np.where(mk, t.z[sy, sx] + 0.5, t.z[sy, sx])
    return haut


def fibre_long(t, sens, g):
    if sens == "u":
        return bruit(t.H, t.W, 14 * SS, g, 2, t.enrouler, 0.5 * SS)
    return bruit(t.H, t.W, 0.5 * SS, g, 2, t.enrouler, 14 * SS)


def ligature(t, xc, y0, y1, g, larg=6.0, sens="u"):
    """Corde de chanvre enroulée autour du fagot : torons en biais, par-dessus les branches."""
    r = np.random.default_rng(g)
    if sens == "u":
        du = np.mod(t.X - xc + t.w / 2, t.w) - t.w / 2  # distance périodique
        along, trav = du, t.Y
    else:
        du = t.Y - xc
        along, trav = du, t.X
    pench = r.uniform(0.25, 0.45)
    m = (np.abs(along + (trav - (y0 + y1) / 2) * pench * 0.3) < larg / 2) & (trav >= y0 - 0.5) & (trav < y1 + 0.5)
    tour = np.abs(np.sin((along * 0.9 + trav * 1.25) * 1.6))  # torons en spirale
    spires = 0.5 + 0.5 * np.cos(math.pi * (along + (trav - (y0 + y1) / 2) * pench * 0.3) / (larg / 2))
    dessus = lisser(t.z, 1.5 * SS, t.enrouler)
    z = np.maximum(t.z, dessus) + 0.9 + 0.8 * tour * spires
    t.z = np.where(m, z, t.z)
    cc = CORDE[None, None, :] * (0.62 + 0.45 * tour[..., None]) * (0.8 + 0.2 * spires[..., None])
    cc = cc * (0.9 + 0.2 * t.B(2, g, 2)[..., None])
    t.a = np.where(m[..., None], cc, t.a)
    t.s[m] = 0.04
    return m


def fagot(t, y0, y1, g, cordes=1, sens="u"):
    """Fagot de branches de diamètres variés couchées le long de u entre y0 et y1 (ou de v entre x0 et x1)."""
    r = np.random.default_rng(g)
    haut = y1 - y0
    pos = y0
    tiges = []
    while pos < y1 - 1.5:
        ray = float(r.uniform(1.6, 6.2)) if r.random() < 0.8 else float(r.uniform(1.0, 1.8))
        c = pos + ray * r.uniform(0.75, 1.0)
        if c > y1:
            break
        tiges.append((c, ray))
        pos = c + ray * r.uniform(0.55, 0.95)
    for k, (c, ray) in enumerate(r.permutation(np.array(tiges)) if tiges else []):
        branche(t, y0 - 0.5, y1 + 0.5, c, ray, g, k, sens)
    L = t.w if sens == "u" else t.h
    if cordes:
        x = r.uniform(0, L)
        for j in range(cordes):
            ligature(t, (x + j * L / cordes + r.uniform(-8, 8)) % L, y0, y1, g + 40 + j, r.uniform(5, 7), sens)
    return haut


def brindilles(nom, w, h, g):
    r = np.random.default_rng(g)
    if nom == "wall":  # 6 fagots de branches, un par bande (raccord parfait à gauche et à droite)
        t = Toile(w, h, JOUR, 0.05, enrouler=True)
        bande = h / 6
        for k in range(6):
            fagot(t, k * bande, (k + 1) * bande, g + 97 * k, cordes=2)
        return t.eclairer(2.0, 1.2)
    if nom == "straw_end":  # bouts coupés des branches du fagot
        t = Toile(w, h, JOUR, 0.05)
        pts = []
        for _ in range(4000):
            if len(pts) > 70:
                break
            cx, cy, rr = r.uniform(0, w), r.uniform(0, h), r.uniform(2.5, 7.0)
            if all(math.hypot(cx - a, cy - b) > (rr + c) * 0.92 for a, b, c in pts):
                pts.append((cx, cy, rr))
        for i, (cx, cy, rr) in enumerate(pts):
            sy, sx = t.fenetre(cx - rr - 1, cy - rr - 1, cx + rr + 1, cy + rr + 1)
            X, Y = t.X[:, sx], t.Y[sy, :]
            d = np.hypot(X - cx, Y - cy)
            m = d < rr
            ecorce = d > rr * r.uniform(0.78, 0.88)
            cernes = 0.5 + 0.5 * np.sin(d / rr * r.uniform(14, 22))
            coupe = r.uniform(-0.25, 0.25)
            z = 1.5 + 0.8 * np.sqrt(np.clip(1 - (d / rr) ** 2, 0, 1)) + coupe * (X - cx) / rr
            t.z[sy, sx] = np.where(m, z, t.z[sy, sx])
            c = AUBIER * r.uniform(0.75, 1.0)
            a = t.a[sy, sx]
            a[m] = (c[None, :] * (0.82 + 0.22 * cernes[m])[:, None])
            a[m & ecorce] = ECORCES[int(r.integers(len(ECORCES)))] * 0.9
            a[m & (d < rr * 0.12)] *= 0.6  # cœur
            t.s[sy, sx][m] = 0.05
        return t.eclairer(1.8, 1.2)
    if nom == "door":  # cadre de branches, remplissage de baguettes verticales, ligatures, écharpe
        t = Toile(w, h, JOUR, 0.05)
        x = 4.0
        k = 0
        while x < w - 4:
            ray = float(r.uniform(2.0, 3.4))
            branche(t, x, x + 2 * ray, x + ray, ray, g + 3, k, "v")
            x += 2 * ray + r.uniform(0.2, 0.9)
            k += 1
        for y in (h * 0.16, h * 0.84):
            branche(t, y - 5, y + 5, y, 4.2, g + 5, int(y), "u")
            for xc in (8, w / 2, w - 8):
                ligature(t, xc, y - 5, y + 5, g + int(xc + y), 4.0)
        m = t.ligne(9, h * 0.8, w - 9, h * 0.2, 7.5, None, haut=4.0)
        t.a = np.where(m[..., None], ECORCES[1][None, None, :] * (0.8 + 0.4 * t.B(0.7, g, 2)[..., None]), t.a)
        for (a, b, c_, d) in ((0, 0, w, 4), (0, h - 4, w, h), (0, 0, 4, h), (w - 4, 0, w, h)):
            t.plaque(a, b, c_, d, ECORCES[2], 4.0, 1.8, 0.05, 0.3, g)
        return t.eclairer(2.0, 1.2)
    if nom in ("beam", "trim", "post"):  # une branche écorcée par endroits
        sens = "u" if nom == "beam" else "v"
        t = Toile(w, h, JOUR, 0.05, enrouler=True)
        L = h if sens == "u" else w
        branche(t, 0, L, L / 2, L / 2 - 0.5, g, 0, sens)
        return t.eclairer(1.6, 1.0)
    if nom == "post_top":  # bout coupé : cernes, cœur, écorce
        t = Toile(w, h, ECORCES[0], 0.05)
        d = np.hypot(t.X - w / 2, t.Y - h / 2)
        cernes = 0.5 + 0.5 * np.sin(d * 2.6 + t.B(3, g, 2) * 3)
        t.a = AUBIER[None, None, :] * (0.78 + 0.25 * cernes[..., None]) * np.ones_like(t.a)
        t.z = 0.25 * cernes
        m = d > w * 0.40
        t.a[m] = ECORCES[0]
        t.z[m] += 0.8
        t.a[d < 1.5] *= 0.6
        return t.eclairer(1.6)
    if nom in ("roof", "foundation_top", "floor", "floor_under", "roof_under", "side", "ramp", "foundation_side", "skirt"):
        t = Toile(w, h, JOUR, 0.05)
        bande = 24 if h >= 96 else 16
        for k, y in enumerate(np.arange(0, h, bande)):
            fagot(t, y, min(h, y + bande), g + 53 * k, cordes=0)
        if nom in ("foundation_top", "floor", "side", "ramp", "skirt", "foundation_side", "roof"):
            for j, x in enumerate(np.arange(r.uniform(10, 30), w, 64)):  # traverses de branches, liées à chaque croisement
                branche(t, x - 6, x + 6, x, 4.0, g + 7, 50 + j, "v")
                t.z = np.where(t.boite(x - 5, 0, x + 5, h), t.z + 1.2, t.z)
                for y in np.arange(bande / 2, h, bande):
                    ligature(t, y, x - 5, x + 5, g + int(x * 7 + y), 3.5, sens="v")
        if nom in ("skirt", "foundation_side"):
            crasse_bas(t, 0.4, g)
        return t.eclairer(2.0, 1.2)
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
