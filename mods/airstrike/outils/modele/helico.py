#!/usr/bin/env python3
"""Hélicoptère de patrouille (événement « hélico » de l'Airstrike) : modèle, épave, caisse, pièces, sons d'aperçu.

ÉCRIT, jamais à la main :
  - geo/helico.geo                 l'hélicoptère en vol (rotors, tourelle qui pivote, mitrailleuse rotative, feux) ;
  - textures/entity/helico.png     sa texture (64 points par bloc) ;
  - geo/helico_epave.geo           les 6 morceaux de l'épave (cabine, queue, rotor, moteur, patins, aile) et la caisse
                                   militaire (T6) de l'hélicoptère, chacun posé au sol (y = 0) ;
  - textures/entity/helico_epave.png   la même peinture, brûlée (même placement que helico.png) ;
  - textures/item/helico_<pièce>.png et models/item/helico_<pièce>.json   les 7 pièces récupérables (icônes 64 x 64).

    python mods/airstrike/outils/modele/helico.py --rendus DOSSIER     images à faire valider, rien d'installé
    python mods/airstrike/outils/modele/helico.py --installer          écrit les fichiers dans les assets du mod

Besoin de Python 3.11+, numpy et Pillow. Repère « naturel » : x à droite, y en haut, z vers l'arrière (le nez est vers
-z), en pixels de modèle (16 par bloc), origine au milieu du bas de la boîte de l'entité. Le fichier .geo est écrit dans
le repère des modèles du jeu (x et y retournés, comme les autres modèles de l'Airstrike) ; ses « point » restent dans le
repère naturel (GeoLoader). Après --installer, refabriquer le mod (fichiers des joueurs : le jar -client change).

Parties lues par le code (client/helico/HelicoRendu, EpaveRendu, CaisseRendu ; ne pas les renommer sans le code) :
  corps/rotor, corps/queue_rotor, corps/tourelle, corps/tourelle/canon, corps/tourelle/canon/barillet,
  verre, flou/rotor_flou, flou/queue_flou, feu_rouge, feu_vert, feu_blanc, gyrophare, phare ;
  points : bouche (sortie des balles, repère naturel), rotor, echappement_g, echappement_d, tourelle ;
  épave : piece_0 ... piece_5, caisse.
"""
import argparse
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ICI = Path(__file__).resolve().parent
MOD = ICI.parents[1]
DEPOT = MOD.parents[1]
ASSETS = MOD / "src" / "client" / "resources" / "assets" / "airstrike"
POLICE = DEPOT / "launcher" / "app" / "assets" / "fonts" / "DMSans-Bold.ttf"

D = 4               # points de texture par pixel de modèle (64 par bloc)
MARGE = 3           # points de marge autour de chaque région (débord de couleur)
LARGEUR_ATLAS = 2048

# pièces récupérables : identifiant, nom (fr), prix de rachat chez Mila (hérals), poids du tirage, rareté
# (mêmes valeurs que fr.heraliz.airstrike.helico.PieceHelico, outils/marchands.py et outils/loot_armes.py)
PIECES = [
    ("tole", "Tôle de carlingue", 35, 30, "commun"),
    ("cables", "Faisceau de câbles", 55, 24, "peu_commun"),
    ("pale", "Pale de rotor", 90, 18, "peu_commun"),
    ("reservoir", "Réservoir blindé", 120, 12, "rare"),
    ("mitrailleuse", "Mitrailleuse démontée", 170, 8, "rare"),
    ("avionique", "Module avionique", 210, 5, "epique"),
    ("turbine", "Turbine du moteur", 250, 3, "legendaire"),
]
COULEUR_RARETE = {"commun": (200, 200, 200), "peu_commun": (85, 200, 85), "rare": (80, 150, 255),
                  "epique": (180, 95, 255), "legendaire": (255, 190, 50)}


# =============================================================================================== bruit
def _hash(ix, iy, iz, graine):
    h = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + iz.astype(np.int64) * 2147483647
         + graine * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def bruit(p, freq, graine=0):
    """Bruit de valeur 3D lissé, p : (..., 3)."""
    q = p * freq
    i = np.floor(q)
    f = q - i
    f = f * f * (3 - 2 * f)
    i = i.astype(np.int64)
    r = 0.0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = ((f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1])
                     * (f[..., 2] if dz else 1 - f[..., 2]))
                r = r + w * _hash(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, graine)
    return r


def fbm(p, freq, octaves=4, graine=0):
    r, a, s = 0.0, 1.0, 0.0
    for o in range(octaves):
        r = r + a * bruit(p, freq * (2 ** o), graine + o * 17)
        s += a
        a *= 0.5
    return r / s


def col(h):
    return np.array([int(h[i:i + 2], 16) / 255.0 for i in (1, 3, 5)])


def lisse(a, b, x):
    t = np.clip((x - a) / (b - a), 0, 1)
    return t * t * (3 - 2 * t)


# =============================================================================================== géométrie
def norm(v):
    v = np.asarray(v, float)
    n = np.linalg.norm(v, axis=-1, keepdims=True)
    return v / np.where(n < 1e-12, 1, n)


class Surf:
    """Ensemble de quads d'une partie, peints avec un matériau. uv : en pixels de modèle, dans le repère local de la
    région (continus pour une surface lisse ; posés un par un pour les autres : voir Modele.ranger)."""

    def __init__(self, part, mat, passe=None, continu=False):
        self.part, self.mat, self.continu = part, mat, continu
        self.quads = []          # (P 4x3, N 4x3, UV 4x2)
        self.region = None       # (x, y, w, h) en points de texture
        self.passe = passe

    def quad(self, P, N=None, UV=None):
        """Sens des sommets : (P1 - P0) x (P2 - P0) du côté de la normale (face visible, comme les modèles du jeu). Avec
        des normales données, le quad est retourné au besoin ; sans, la normale suit le sens donné."""
        P = np.asarray(P, float)
        g = np.cross(P[1] - P[0], P[2] - P[0]) + np.cross(P[2] - P[0], P[3] - P[0])
        if N is None:
            N = np.repeat(norm(g)[None], 4, 0)
        else:
            N = np.asarray(N, float)
            if float(np.dot(g, N.mean(0))) < 0:
                P, N = P[::-1].copy(), N[::-1].copy()
                UV = None if UV is None else np.asarray(UV, float)[::-1].copy()
        self.quads.append([P, N, None if UV is None else np.asarray(UV, float)])

    def orienter(self, centre):
        """Primitive convexe : chaque quad tourné vers l'extérieur (loin de centre)."""
        for q in self.quads:
            P = q[0]
            g = np.cross(P[1] - P[0], P[2] - P[0]) + np.cross(P[2] - P[0], P[3] - P[0])
            if float(np.dot(g, P.mean(0) - centre)) < 0:
                q[0] = P[::-1].copy()
                q[1] = -q[1][::-1].copy()
                if q[2] is not None:
                    q[2] = q[2][::-1].copy()


class Modele:
    def __init__(self):
        self.surfs = []
        self.parts = {}          # chemin -> (pivot naturel, rotation (rx, ry, rz) en radians, repère du jeu)
        self.passes = {}         # partie du haut -> solid | glass | emissive
        self.points = {}

    def partie(self, chemin, pivot=(0, 0, 0), rot=(0, 0, 0), passe=None):
        self.parts[chemin] = (np.asarray(pivot, float), rot)
        if "/" not in chemin:
            self.passes[chemin] = passe or "solid"

    def surf(self, part, mat, continu=False):
        s = Surf(part, mat, continu=continu)
        self.surfs.append(s)
        return s

    # ------------------------------------------------------------------ primitives
    def boite(self, part, mat, a, b, arrondi=None):
        """Pavé de a à b (coins naturels)."""
        s = self.surf(part, mat)
        x0, y0, z0 = a
        x1, y1, z1 = b
        v = lambda x, y, z: (x, y, z)
        faces = [
            [v(x1, y0, z1), v(x0, y0, z1), v(x0, y0, z0), v(x1, y0, z0)],   # bas
            [v(x1, y1, z0), v(x0, y1, z0), v(x0, y1, z1), v(x1, y1, z1)],   # haut
            [v(x0, y0, z0), v(x0, y0, z1), v(x0, y1, z1), v(x0, y1, z0)],   # ouest (-x)
            [v(x1, y0, z0), v(x0, y0, z0), v(x0, y1, z0), v(x1, y1, z0)],   # nord (-z)
            [v(x1, y0, z1), v(x1, y0, z0), v(x1, y1, z0), v(x1, y1, z1)],   # est (+x)
            [v(x0, y0, z1), v(x1, y0, z1), v(x1, y1, z1), v(x0, y1, z1)],   # sud (+z)
        ]
        for f in faces:
            s.quad(f)
        s.orienter((np.asarray(a, float) + np.asarray(b, float)) / 2)
        return s

    def prisme(self, part, mat, poly, x0, x1):
        """Prisme le long de x : poly = [(z, y), ...] dans le sens trigonométrique vu de +x."""
        s = self.surf(part, mat)
        n = len(poly)
        for i in range(n):
            (za, ya), (zb, yb) = poly[i], poly[(i + 1) % n]
            s.quad([(x1, ya, za), (x1, yb, zb), (x0, yb, zb), (x0, ya, za)])
        # faces : éventail (quads dégénérés)
        for i in range(1, n - 1):
            (za, ya), (zb, yb), (zc, yc) = poly[0], poly[i], poly[i + 1]
            s.quad([(x1, ya, za), (x1, yc, zc), (x1, yb, zb), (x1, yb, zb)])
            s.quad([(x0, ya, za), (x0, yb, zb), (x0, yc, zc), (x0, yc, zc)])
        zc_, yc_ = np.mean([p[0] for p in poly]), np.mean([p[1] for p in poly])
        s.orienter(np.array([(x0 + x1) / 2, yc_, zc_]))
        return s

    def cylindre(self, part, mat, a, b, r, seg=16, ra=None, rb=None, caps=(True, True), mat_cap=None):
        """Cylindre (ou cône) de l'axe a -> b, surface lisse."""
        a, b = np.asarray(a, float), np.asarray(b, float)
        ra = r if ra is None else ra
        rb = r if rb is None else rb
        ax = norm(b - a)
        ref = np.array([0, 1.0, 0]) if abs(ax[1]) < 0.9 else np.array([1.0, 0, 0])
        e1 = norm(np.cross(ax, ref))
        e2 = np.cross(ax, e1)
        s = self.surf(part, mat, continu=True)
        L = float(np.linalg.norm(b - a))
        C = 2 * math.pi * max(ra, rb)
        pente = (ra - rb) / max(L, 1e-6)
        for j in range(seg):
            t0, t1 = 2 * math.pi * j / seg, 2 * math.pi * (j + 1) / seg
            d0 = math.cos(t0) * e1 + math.sin(t0) * e2
            d1 = math.cos(t1) * e1 + math.sin(t1) * e2
            n0, n1 = norm(d0 + pente * ax), norm(d1 + pente * ax)
            P = [a + d0 * ra, b + d0 * rb, b + d1 * rb, a + d1 * ra]
            u0, u1 = C * j / seg, C * (j + 1) / seg
            s.quad(P, [n0, n0, n1, n1], [(u0, 0), (u0, L), (u1, L), (u1, 0)])
        mc = mat_cap or mat
        if caps[0] or caps[1]:
            c = self.surf(part, mc)
            for k, (o, rr, sg) in enumerate(((a, ra, -1), (b, rb, 1))):
                if not caps[k] or rr <= 0:
                    continue
                for j in range(0, seg, 2):
                    pts = [o + (math.cos(2 * math.pi * (j + q) / seg) * e1 + math.sin(2 * math.pi * (j + q) / seg) * e2) * rr
                           for q in (0, 1, 2)]
                    quad = [o, pts[0], pts[1], pts[2]] if sg > 0 else [o, pts[2], pts[1], pts[0]]
                    c.quad(quad, np.repeat((ax * sg)[None], 4, 0))
        return s

    def loft(self, part, mat, anneaux, passe_verre=None, mat_verre=None):
        """Surface lisse par anneaux : anneaux = liste de tableaux (M, 3) de même M, tournés dans le même sens.
        passe_verre(centre, normale) -> vrai : le quad va dans la surface vitrée (partie « verre »)."""
        A = np.array(anneaux)                 # (R, M, 3)
        R, M, _ = A.shape
        # normales lissées : différences finies sur la grille
        tz = np.gradient(A, axis=0)
        tu = (np.roll(A, -1, axis=1) - np.roll(A, 1, axis=1)) / 2
        N = norm(np.cross(tz, tu))
        cen = A.mean(axis=1, keepdims=True)
        sgn = np.sign(np.sum((A - cen) * N, axis=-1, keepdims=True))
        N = N * np.where(sgn == 0, 1, sgn)
        # u : longueur d'arc de chaque anneau, recentrée ; v : distance le long de l'axe
        seg = np.linalg.norm(np.roll(A, -1, axis=1) - A, axis=-1)          # (R, M)
        cum = np.concatenate([np.zeros((R, 1)), np.cumsum(seg, axis=1)], axis=1)  # (R, M+1)
        tot = cum[:, -1:]
        cref = tot.max()
        U = cref / 2 + (cum - tot / 2)
        vpos = np.concatenate([[0], np.cumsum(np.linalg.norm(A[1:].mean(1) - A[:-1].mean(1), axis=-1))])
        s = self.surf(part, mat, continu=True)
        v = self.surf("verre", mat_verre, continu=True) if passe_verre else None
        for i in range(R - 1):
            for j in range(M):
                jj = (j + 1) % M
                P = [A[i, j], A[i + 1, j], A[i + 1, jj], A[i, jj]]
                Nn = [N[i, j], N[i + 1, j], N[i + 1, jj], N[i, jj]]
                UV = [(U[i, j], vpos[i]), (U[i + 1, j], vpos[i + 1]), (U[i + 1, j + 1], vpos[i + 1]), (U[i, j + 1], vpos[i])]
                cible = s
                if v is not None:
                    c = np.mean(P, axis=0)
                    if passe_verre(c, np.mean(Nn, axis=0)):
                        cible = v
                cible.quad(P, Nn, UV)
        return s, v


def anneau(z, w, yb, yt, n, M=40, x0=0.0):
    """Section en super-ellipse (x0 : décalage), de +x vers +y."""
    yc, h = (yb + yt) / 2, (yt - yb) / 2
    out = []
    for k in range(M):
        t = 2 * math.pi * k / M
        c, s = math.cos(t), math.sin(t)
        x = w * math.copysign(abs(c) ** (2 / n), c)
        y = yc + h * math.copysign(abs(s) ** (2 / n), s)
        out.append((x0 + x, y, z))
    return np.array(out)


def interpoler_stations(stations, pas):
    """Stations (z, w, yb, yt, n) -> stations plus serrées (interpolation de Catmull-Rom de chaque grandeur)."""
    S = np.array(stations, float)
    out = []
    for i in range(len(S) - 1):
        p0, p1, p2, p3 = S[max(i - 1, 0)], S[i], S[i + 1], S[min(i + 2, len(S) - 1)]
        k = max(1, int(math.ceil((p2[0] - p1[0]) / pas)))
        for q in range(k):
            t = q / k
            t2, t3 = t * t, t * t * t
            out.append(0.5 * ((2 * p1) + (-p0 + p2) * t + (2 * p0 - 5 * p1 + 4 * p2 - p3) * t2 + (-p0 + 3 * p1 - 3 * p2 + p3) * t3))
    out.append(S[-1])
    return out


# =============================================================================================== l'hélicoptère
CABINE = [  # z, demi-largeur, bas, haut, exposant
    (-92.5, 0.5, 26.6, 27.4, 2.2), (-90.5, 5.5, 21, 33, 2.3), (-88, 8, 18.5, 35, 2.4), (-82, 13.5, 13.5, 41, 2.7), (-73, 17.5, 10.5, 46, 3.0),
    (-61, 20, 9, 50, 3.2), (-46, 21.5, 8, 52.5, 3.5), (-20, 22, 8, 53, 3.7), (8, 22, 8, 53, 3.7),
    (22, 21, 9, 52, 3.4), (32, 17, 14, 50.5, 3.0), (42, 12, 21, 48.5, 2.8), (54, 8.2, 28.5, 47, 2.6)]
POUTRE = [(54, 8.2, 28.5, 47, 2.6), (78, 6.6, 32, 46, 2.5), (108, 5.2, 34.5, 45, 2.4), (128, 4.4, 36, 44.5, 2.4),
          (136, 2.5, 38.5, 43, 2.2), (137.2, 0.5, 40.4, 41.2, 2.2)]
CAPOT = [(-43.5, 0.5, 50.5, 51.5, 2.2), (-42, 5, 48, 55, 2.6), (-36, 11.5, 48, 61, 3.0), (-24, 14, 48, 65, 3.4), (12, 14, 48, 65, 3.4),
         (26, 12, 48, 62, 3.0), (36, 7, 48, 57, 2.6), (42, 2.5, 48, 52, 2.2), (43.4, 0.5, 49.6, 50.4, 2.2)]
TOURELLE = (0.0, 7.0, -66.0)        # pivot de la tourelle (lacet)
CANON = (0.0, 2.5, -66.0)           # pivot du canon (tangage)
BARILLET = (0.0, 2.5, -72.0)        # pivot du barillet (rotation autour de z)
ROTOR = (0.0, 79.0, -6.0)
QUEUE_ROTOR = (-6.5, 56.0, 130.0)


def verre_cabine(c, n):
    """Quads vitrés de la cabine : verrière avant et fenêtres des portes du poste."""
    x, y, z = c
    if z < -50 and y > 25 and n[1] > -0.2:
        return True
    if -48 < z < -31 and 31 < y < 47 and abs(n[0]) > 0.55:
        return True
    return False


def modele_helico():
    m = Modele()
    m.partie("corps")
    m.partie("verre", passe="glass")
    m.partie("flou", passe="glass")
    for f in ("feu_rouge", "feu_vert", "feu_blanc", "gyrophare", "phare"):
        m.partie(f, passe="emissive")
    m.partie("corps/rotor", ROTOR)
    m.partie("corps/queue_rotor", QUEUE_ROTOR)
    m.partie("corps/tourelle", TOURELLE)
    m.partie("corps/tourelle/canon", CANON)
    m.partie("corps/tourelle/canon/barillet", BARILLET)
    m.partie("flou/rotor_flou", ROTOR)
    m.partie("flou/queue_flou", QUEUE_ROTOR)

    # --- fuselage : cabine (verrière à part), poutre de queue, capot moteur
    st = interpoler_stations(CABINE, 3.0)
    m.loft("corps", "coque", [anneau(s[0], s[1], s[2], s[3], s[4], 48) for s in st], verre_cabine, "verre")
    st = interpoler_stations(POUTRE, 6.0)
    m.loft("corps", "coque", [anneau(s[0], s[1], s[2], s[3], s[4], 24) for s in st])
    st = interpoler_stations(CAPOT, 3.0)
    m.loft("corps", "capot", [anneau(s[0], s[1], s[2], s[3], s[4], 32) for s in st])

    # --- intérieur du poste (vu à travers la verrière) : plancher, tableau de bord, sièges, pilotes
    m.boite("corps", "interieur", (-15, 11.5, -72), (15, 13, -44))
    m.boite("corps", "interieur", (-12.5, 14, -74), (12.5, 24, -70))
    m.boite("corps", "tableau", (-12, 24, -75), (12, 28.5, -69))
    m.boite("corps", "interieur", (-17, 13, -46), (17, 50, -44))   # cloison derrière les pilotes
    for x in (-8.5, 8.5):
        m.boite("corps", "siege", (x - 5, 13, -56), (x + 5, 18, -48))
        m.boite("corps", "siege", (x - 5, 18, -50), (x + 5, 38, -47))
        m.boite("corps", "pilote", (x - 4.2, 18, -60), (x + 4.2, 32, -50))      # buste
        m.boite("corps", "pilote", (x - 5.2, 26, -59), (x - 3.8, 31, -52))      # bras
        m.boite("corps", "pilote", (x + 3.8, 26, -59), (x + 5.2, 31, -52))
        m.loft("corps", "casque", [_sphere_anneau(x, 36.5, -55, 3.7, -math.pi / 2 + math.pi * k / 8) for k in range(9)])
        m.boite("corps", "visiere", (x - 2.8, 34.5, -59.2), (x + 2.8, 37.5, -57.8))

    # --- portes : rails et poignées, marchepieds
    for sx in (-1, 1):
        x = 22.3 * sx
        m.boite("corps", "metal_sombre", (min(x, x + 0.8 * sx), 49, -30), (max(x, x + 0.8 * sx), 50, 18))
        m.boite("corps", "metal_sombre", (min(x, x + 1.0 * sx), 23, -6), (max(x, x + 1.0 * sx), 24.5, -2))
        m.boite("corps", "metal_sombre", (min(16 * sx, 25 * sx), 6, -40), (max(16 * sx, 25 * sx), 7, -32))

    # --- échappements (deux de chaque côté du capot)
    for sx in (-1, 1):
        m.cylindre("corps", "echappement", (11 * sx, 58, 22), (18 * sx, 58.5, 34), 3.0, seg=12, caps=(False, True),
                   mat_cap="suie")
        m.points["echappement_" + ("g" if sx < 0 else "d")] = (18 * sx, 58.5, 34)
    # prises d'air
    for sx in (-1, 1):
        m.boite("corps", "grille", (min(13.2 * sx, 14.6 * sx), 55, -28), (max(13.2 * sx, 14.6 * sx), 61, -14))

    # --- mât et moyeu du rotor (partie corps/rotor : tout ce qui tourne)
    m.cylindre("corps", "metal_sombre", (0, 64, -6), (0, 76, -6), 2.4, seg=12)
    m.cylindre("corps/rotor", "moyeu", (0, 75, -6), (0, 81, -6), 5.5, seg=16)
    m.cylindre("corps/rotor", "metal_sombre", (0, 81, -6), (0, 84, -6), 2.6, seg=12)
    for k in range(4):
        a = math.pi / 2 * k + math.pi / 4
        d = np.array([math.cos(a), 0, math.sin(a)])
        m.cylindre("corps/rotor", "metal_sombre", np.array([0, 78.5, -6]) + d * 4, np.array([0, 78.5, -6]) + d * 11, 1.5,
                   seg=8)
        pale(m, "corps/rotor", np.array([0, 78.5, -6]), d, 10, 106, 8.0, 5.0, "pale")
    # flou du rotor (disque transparent qui tourne)
    disque(m, "flou/rotor_flou", "flou", np.array([0, 79.2, -6]), 106, 48)

    # --- empennage : dérive, plans horizontaux, rotor de queue
    m.prisme("corps", "coque", [(108, 43), (136, 43), (143, 76), (131, 76)][::-1], -1.6, 1.6)
    m.prisme("corps", "coque", [(108, 40), (118, 40), (118, 41.6), (108, 41.6)][::-1], -27, 27)
    for sx in (-1, 1):
        m.prisme("corps", "coque", [(107, 35), (119, 35), (119, 47), (107, 47)][::-1],
                 min(26 * sx, 27.4 * sx), max(26 * sx, 27.4 * sx))
    m.cylindre("corps", "metal_sombre", (-1.6, 56, 130), (-4.2, 56, 130), 2.6, seg=12)
    m.cylindre("corps/queue_rotor", "moyeu", (-4.2, 56, 130), (-6.8, 56, 130), 2.0, seg=10)
    for k in range(4):
        a = math.pi / 2 * k
        d = np.array([0, math.cos(a), math.sin(a)])
        pale(m, "corps/queue_rotor", np.array([-6.0, 56, 130]), d, 1.5, 19, 3.8, 3.0, "pale_queue", axe=np.array([1.0, 0, 0]))
    disque(m, "flou/queue_flou", "flou", np.array([-6.3, 56, 130]), 19, 24, axe=np.array([1.0, 0, 0]))

    # --- ailerons porte-armes et paniers de roquettes
    for sx in (-1, 1):
        x0, x1 = 19 * sx, 45 * sx
        poly = [(-9, 23), (9, 23), (6, 26), (-7, 26.5)]
        aile(m, "corps", sx, 19, 45)
        m.boite("corps", "metal_sombre", (min(34 * sx, 38 * sx), 19, -4), (max(34 * sx, 38 * sx), 23, 4))
        m.cylindre("corps", "panier", (36 * sx, 16.5, -22), (36 * sx, 16.5, 10), 4.6, seg=16, caps=(True, False),
                   mat_cap="roquettes")
        m.cylindre("corps", "panier", (36 * sx, 16.5, 10), (36 * sx, 16.5, 16), 4.6, seg=16, rb=2.0, caps=(False, True))
        m.points["feu_" + ("g" if sx < 0 else "d")] = (45.5 * sx, 24.5, 0)

    # --- tourelle du menton : support (lacet), canon (tangage), barillet (rotation)
    m.cylindre("corps", "metal_sombre", (0, 11, -66), (0, 8, -66), 6.5, seg=16)
    m.cylindre("corps/tourelle", "metal_sombre", (0, 8.2, -66), (0, 4.5, -66), 5.6, seg=16)
    for sx in (-1, 1):
        m.boite("corps/tourelle", "metal_sombre", (min(4 * sx, 5.2 * sx), -0.5, -69), (max(4 * sx, 5.2 * sx), 5, -63))
    m.boite("corps/tourelle/canon", "arme", (-3.4, 0, -73), (3.4, 5, -59))
    m.boite("corps/tourelle/canon", "arme", (3.4, 0.5, -70), (6.5, 4.5, -62))            # boîte d'alimentation
    m.cylindre("corps/tourelle/canon/barillet", "arme", (0, 2.5, -73), (0, 2.5, -77), 3.0, seg=14)
    for k in range(6):
        a = 2 * math.pi * k / 6
        o = np.array([math.cos(a) * 1.85, 2.5 + math.sin(a) * 1.85, 0])
        m.cylindre("corps/tourelle/canon/barillet", "canon", o + (0, 0, -77), o + (0, 0, -96), 0.62, seg=6,
                   caps=(False, True), mat_cap="suie")
    m.cylindre("corps/tourelle/canon/barillet", "arme", (0, 2.5, -89), (0, 2.5, -91), 2.75, seg=14)
    m.points["bouche"] = (0.0, 2.5, -97.0)
    m.points["tourelle"] = TOURELLE
    m.points["rotor"] = ROTOR

    # --- patins d'atterrissage
    for sx in (-1, 1):
        x = 22 * sx
        m.cylindre("corps", "patin", (x, -9, -46), (x, -9, 32), 1.5, seg=10, caps=(False, True))
        m.cylindre("corps", "patin", (x, -9, -46), (x, -5.5, -55), 1.5, seg=10, caps=(False, True))
        for z in (-32, 16):
            m.cylindre("corps", "patin", (13.5 * sx, 9.5, z), (x, -8.5, z), 1.3, seg=8, caps=(False, False))

    # --- antennes, phare, feux
    m.boite("corps", "metal_sombre", (-0.4, 52, 24), (0.4, 60, 25))
    m.boite("corps", "metal_sombre", (-0.4, 9, 30), (0.4, 9.8, 38))
    m.cylindre("corps", "metal_sombre", (6, 10.5, -78), (6, 7.5, -79), 2.2, seg=10, caps=(False, False))
    m.cylindre("phare", "phare", (6, 7.6, -79.0), (6, 7.3, -79.1), 1.9, seg=10, caps=(False, True))
    for nom, (sx, mat) in {"feu_rouge": (-1, "feu_rouge"), "feu_vert": (1, "feu_vert")}.items():
        m.boite(nom, mat, (45 * sx - 0.9, 24, -1), (45 * sx + 0.9, 25.6, 1.4))
    m.boite("feu_blanc", "feu_blanc", (-0.9, 41, 136.2), (0.9, 42.6, 137.6))
    m.cylindre("gyrophare", "gyrophare", (0, 65.2, 18), (0, 67.4, 18), 1.3, seg=10, caps=(False, True))
    m.cylindre("gyrophare", "gyrophare", (0, 76, 140), (0, 77.6, 140), 1.0, seg=8, caps=(False, True))
    return m


def _sphere_anneau(x, y, z, r, a):
    """Anneau d'une petite sphère (casque) à la latitude a (de -pi/2 à pi/2), axe vertical."""
    rr = max(r * math.cos(a), 0.05)
    yy = y + r * math.sin(a)
    out = []
    for k in range(12):
        t = 2 * math.pi * k / 12
        out.append((x + rr * math.cos(t), yy, z + rr * math.sin(t)))
    return np.array(out)


def pale(m, part, centre, d, r0, r1, corde0, corde1, mat, axe=None):
    """Pale profilée de r0 à r1 le long de d, corde qui se resserre, légère flèche ; axe = axe du rotor."""
    axe = np.array([0, 1.0, 0]) if axe is None else axe
    t = norm(np.cross(axe, d))               # sens de rotation (bord d'attaque)
    s = m.surf(part, mat, continu=True)
    k = 10
    ep = 0.65
    prof = [(-0.5, 0.0), (-0.25, 0.75), (0.25, 0.9), (0.5, 0.0), (0.25, -0.35), (-0.25, -0.35)]   # (corde, épaisseur)
    anneaux = []
    for q in range(k + 1):
        r = r0 + (r1 - r0) * q / k
        c = corde0 + (corde1 - corde0) * (q / k) ** 1.5
        chute = -0.018 * (r - r0)
        o = centre + d * r + axe * chute
        anneaux.append(np.array([o + t * (cc * c) + axe * (ee * ep) for cc, ee in prof]))
    A = np.array(anneaux)
    R, M, _ = A.shape
    for i in range(R - 1):
        for j in range(M):
            jj = (j + 1) % M
            P = [A[i, j], A[i + 1, j], A[i + 1, jj], A[i, jj]]
            n = norm(np.cross(P[1] - P[0], P[3] - P[0]))
            # sens extérieur
            cen = A[i].mean(0)
            if np.dot(n, (P[0] + P[3]) / 2 - cen) < 0:
                P = P[::-1]
                n = -n
            u0, u1 = j * 3.0, (j + 1) * 3.0
            v0, v1 = r0 + (r1 - r0) * i / k, r0 + (r1 - r0) * (i + 1) / k
            uv = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
            if P[0] is not A[i, j]:
                uv = uv[::-1]
            s.quad(P, None, uv)
    # bout de pale
    e = m.surf(part, mat)
    bout = A[-1]
    for j in range(1, M - 1):
        e.quad([bout[0], bout[j], bout[j + 1], bout[j + 1]])
    e.orienter(A[-2].mean(0))
    return s


def disque(m, part, mat, centre, r, seg, axe=None):
    """Disque du flou d'un rotor (deux faces), uv : coordonnées polaires (u : angle, v : rayon)."""
    axe = np.array([0, 1.0, 0]) if axe is None else axe
    ref = np.array([1.0, 0, 0]) if abs(axe[0]) < 0.9 else np.array([0, 0, 1.0])
    e1 = norm(np.cross(axe, ref))
    e2 = np.cross(axe, e1)
    s = m.surf(part, mat, continu=True)
    anneaux = 4
    for i in range(anneaux):
        ra, rb = r * i / anneaux, r * (i + 1) / anneaux
        for j in range(seg):
            t0, t1 = 2 * math.pi * j / seg, 2 * math.pi * (j + 1) / seg
            pa0 = centre + (math.cos(t0) * e1 + math.sin(t0) * e2) * ra
            pb0 = centre + (math.cos(t0) * e1 + math.sin(t0) * e2) * rb
            pb1 = centre + (math.cos(t1) * e1 + math.sin(t1) * e2) * rb
            pa1 = centre + (math.cos(t1) * e1 + math.sin(t1) * e2) * ra
            u0, u1 = 40.0 * j / seg, 40.0 * (j + 1) / seg
            v0, v1 = 12.0 * i / anneaux, 12.0 * (i + 1) / anneaux
            for sg in (1, -1):
                P = [pa0, pb0, pb1, pa1]
                UV = [(u0, v0), (u0, v1), (u1, v1), (u1, v0)]
                if sg < 0:
                    P, UV = P[::-1], UV[::-1]
                n = norm(np.cross(P[1] - P[0], P[2] - P[0]))
                if np.dot(n, axe * sg) < 0:
                    P, UV = P[::-1], UV[::-1]
                s.quad(P, np.repeat((axe * sg)[None], 4, 0), UV)


def aile(m, part, sx, x0, x1):
    """Aileron porte-armes : profil épais, corde qui se resserre, léger dièdre négatif."""
    s = m.surf(part, "coque", continu=True)
    prof = [(-9, 0.0), (-6, 1.6), (2, 1.9), (9, 0.0), (2, -1.4), (-6, -1.4)]
    anneaux = []
    for q in range(5):
        x = x0 + (x1 - x0) * q / 4
        c = 1.0 - 0.3 * q / 4
        y = 24.5 - 1.5 * q / 4
        anneaux.append(np.array([(x * sx, y + e, cz * c) for cz, e in prof]))
    A = np.array(anneaux)
    R, M, _ = A.shape
    for i in range(R - 1):
        for j in range(M):
            jj = (j + 1) % M
            P = [A[i, j], A[i + 1, j], A[i + 1, jj], A[i, jj]]
            n = norm(np.cross(P[1] - P[0], P[3] - P[0]))
            cen = A[i].mean(0)
            uv = [(j * 4.0, i * 6.5), (j * 4.0, (i + 1) * 6.5), ((j + 1) * 4.0, (i + 1) * 6.5), ((j + 1) * 4.0, i * 6.5)]
            if np.dot(n, (P[0] + P[3]) / 2 - cen) < 0:
                P, uv = P[::-1], uv[::-1]
            s.quad(P, None, uv)
    e = m.surf(part, "coque")
    for bout in (A[0], A[-1]):
        for j in range(1, M - 1):
            q = [bout[0], bout[j], bout[j + 1], bout[j + 1]]
            n = norm(np.cross(q[1] - q[0], q[2] - q[0]))
            if np.dot(n, bout.mean(0) - A.mean((0, 1))) < 0:
                q = q[::-1]
            e.quad(q)


# =============================================================================================== caisse militaire (T6)
CAISSE = (22.0, 14.0, 14.0)         # longueur (x), hauteur, profondeur (pixels de modèle)


def ajouter_caisse(m, part):
    """Caisse de matériel de l'hélicoptère : bois peint vert, cornières et fermoirs en métal, poignées de corde."""
    L, H, P = CAISSE
    x0, x1, z0, z1 = -L / 2, L / 2, -P / 2, P / 2
    m.boite(part, "caisse", (x0 + 0.6, 1.2, z0 + 0.6), (x1 - 0.6, H - 1.4, z1 - 0.6))
    m.boite(part, "caisse_couvercle", (x0 + 0.2, H - 1.6, z0 + 0.2), (x1 - 0.2, H, z1 - 0.2))
    m.boite(part, "caisse_couvercle", (x0 + 0.3, 0, z0 + 0.3), (x1 - 0.3, 1.4, z1 - 0.3))
    for sx in (-1, 1):
        for sz in (-1, 1):
            x, z = sx * (L / 2 - 0.9), sz * (P / 2 - 0.9)
            m.boite(part, "corniere", (x - 1.0, 0, z - 1.0), (x + 1.0, H + 0.1, z + 1.0))
    for fx in (-0.28, 0.28):
        for sz in (-1, 1):
            z = sz * (P / 2 + 0.05)
            m.boite(part, "fermoir", (fx * L - 1.3, H - 4.2, min(z, z + 0.7 * sz)), (fx * L + 1.3, H - 0.4, max(z, z + 0.7 * sz)))
    for sx in (-1, 1):
        x = sx * (L / 2 + 0.05)
        m.boite(part, "corde", (min(x, x + 0.8 * sx), H * 0.55, -3.0), (max(x, x + 0.8 * sx), H * 0.55 + 1.0, 3.0))


# =============================================================================================== atlas
def uv_auto(P):
    e1 = P[1] - P[0]
    if np.linalg.norm(e1) < 1e-6:
        e1 = P[2] - P[0]
    e1 = norm(e1)
    n = norm(np.cross(P[1] - P[0], P[2] - P[0]) + np.cross(P[2] - P[0], P[3] - P[0]))
    e2 = np.cross(n, e1)
    c = np.stack([(P - P[0]) @ e1, (P - P[0]) @ e2], -1)
    return c - c.min(0)


def ranger(m, largeur=LARGEUR_ATLAS, d=D):
    """Place chaque surface dans l'atlas ; ajoute à chaque quad ses coordonnées de texture (q[3], en points)."""
    surfs = [s for s in m.surfs if s.quads]
    for s in surfs:
        if s.continu:
            uv = np.concatenate([q[2] for q in s.quads])
            mn, mx = uv.min(0), uv.max(0)
            for q in s.quads:
                q[2] = q[2] - mn
            w, h = mx - mn
        else:
            items = []
            for q in s.quads:
                q[2] = uv_auto(q[0]) if q[2] is None else q[2] - q[2].min(0)
                items.append(q)
            items.sort(key=lambda q: -q[2][:, 1].max())
            W = max(40.0, max(q[2][:, 0].max() for q in items) + 1)
            x = y = sh = 0.0
            pad = 0.9
            for q in items:
                sz = q[2].max(0)
                if x + sz[0] > W:
                    x, y, sh = 0.0, y + sh + pad, 0.0
                q[2] = q[2] + (x, y)
                x += sz[0] + pad
                sh = max(sh, sz[1])
            w, h = W, y + sh
        s.taille = (int(math.ceil(w * d)) + 2 * MARGE + 1, int(math.ceil(h * d)) + 2 * MARGE + 1)
    ordre = sorted(surfs, key=lambda s: -s.taille[1])
    x = y = sh = 0
    for s in ordre:
        tw, th = s.taille
        if tw > largeur:
            raise SystemExit("surface trop large pour l'atlas : %s (%d points)" % (s.mat, tw))
        if x + tw > largeur:
            x, y, sh = 0, y + sh, 0
        s.region = (x, y, tw, th)
        x += tw
        sh = max(sh, th)
    H = int(math.ceil((y + sh) / 64.0)) * 64
    for s in surfs:
        rx, ry = s.region[0] + MARGE, s.region[1] + MARGE
        for q in s.quads:
            q.append(np.stack([rx + q[2][:, 0] * d, ry + q[2][:, 1] * d], -1))
    return largeur, H


def cartes(m, W, H):
    """Pour chaque point de l'atlas : position et normale (repère naturel), matériau."""
    POS = np.zeros((H, W, 3))
    NOR = np.zeros((H, W, 3))
    MAT = np.full((H, W), -1, np.int32)
    mats = sorted({s.mat for s in m.surfs if s.quads})
    idx = {k: i for i, k in enumerate(mats)}
    for s in m.surfs:
        k = idx.get(s.mat)
        for q in s.quads:
            P, N, _, T = q[:4]
            for tri in ((0, 1, 2), (0, 2, 3)):
                A = T[list(tri)]
                x0, x1 = int(max(0, math.floor(A[:, 0].min() - 1))), int(min(W - 1, math.ceil(A[:, 0].max() + 1)))
                y0, y1 = int(max(0, math.floor(A[:, 1].min() - 1))), int(min(H - 1, math.ceil(A[:, 1].max() + 1)))
                if x1 < x0 or y1 < y0:
                    continue
                X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
                (ax, ay), (bx, by), (cx, cy) = A
                den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
                if abs(den) < 1e-9:
                    continue
                l1 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
                l2 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
                l3 = 1 - l1 - l2
                eps = 0.04
                ok = (l1 >= -eps) & (l2 >= -eps) & (l3 >= -eps)
                if not ok.any():
                    continue
                Pt, Nt = P[list(tri)], N[list(tri)]
                pos = l1[..., None] * Pt[0] + l2[..., None] * Pt[1] + l3[..., None] * Pt[2]
                nor = norm(l1[..., None] * Nt[0] + l2[..., None] * Nt[1] + l3[..., None] * Nt[2])
                sl = (slice(y0, y1 + 1), slice(x0, x1 + 1))
                POS[sl] = np.where(ok[..., None], pos, POS[sl])
                NOR[sl] = np.where(ok[..., None], nor, NOR[sl])
                MAT[sl] = np.where(ok, k, MAT[sl])
    # débord : les points vides voisins reprennent les valeurs d'à côté (pas de liseré au bord des faces)
    for _ in range(MARGE + 2):
        vide = MAT < 0
        if not vide.any():
            break
        for dy, dx in ((0, 1), (0, -1), (1, 0), (-1, 0)):
            src = np.roll(np.roll(MAT, dy, 0), dx, 1)
            prend = vide & (src >= 0)
            MAT = np.where(prend, src, MAT)
            POS = np.where(prend[..., None], np.roll(np.roll(POS, dy, 0), dx, 1), POS)
            NOR = np.where(prend[..., None], np.roll(np.roll(NOR, dy, 0), dx, 1), NOR)
            vide = MAT < 0
    return POS, NOR, MAT, mats


# =============================================================================================== peinture
def ligne(d, larg=0.3):
    return 1 - lisse(larg * 0.45, larg, np.abs(d))


def dist_grille(v, pas, decal=0.0):
    q = (v - decal) / pas
    return (q - np.round(q)) * pas


def masque_texte(texte, haut=80):
    f = ImageFont.truetype(str(POLICE), haut)
    b = f.getbbox(texte)
    im = Image.new("L", (b[2] - b[0] + 8, b[3] - b[1] + 8), 0)
    ImageDraw.Draw(im).text((4 - b[0], 4 - b[1]), texte, font=f, fill=255)
    return np.asarray(im, float) / 255.0


def echantillon(masque, s, t):
    """masque (h, w) lu en s, t dans [0, 1] (hors : 0)."""
    h, w = masque.shape
    ok = (s >= 0) & (s <= 1) & (t >= 0) & (t <= 1)
    xi = np.clip((s * (w - 1)).astype(int), 0, w - 1)
    yi = np.clip((t * (h - 1)).astype(int), 0, h - 1)
    return np.where(ok, masque[yi, xi], 0.0)


TEXTE_QUEUE = masque_texte("HZ-07")
TEXTE_DANGER = masque_texte("DANGER", 60)
TEXTE_CAISSE = masque_texte("HZ · MATÉRIEL", 60)


def tache_texte(P, masque, cote, z0, z1, y0, y1):
    """Texte posé sur un flanc (cote = +1 à droite, -1 à gauche), lisible de l'extérieur."""
    x, y, z = P.T
    s = (z1 - z) / (z1 - z0) if cote > 0 else (z - z0) / (z1 - z0)
    t = (y1 - y) / (y1 - y0)
    return echantillon(masque, s, t) * (x * cote > 0)


def usure(P, rgb, force=1.0, graine=0):
    """Rayures claires (métal nu) et salissures qui coulent vers le bas."""
    x, y, z = P.T
    ray = bruit(np.stack([x * 1.8, y * 0.25, z * 1.8], -1), 1.0, graine + 40)
    ray = lisse(0.80, 0.93, ray * (0.7 + 0.5 * fbm(P, 1 / 9, 2, graine + 41)))
    coul = fbm(np.stack([x * 0.6, y * 0.06, z * 0.6], -1), 1 / 1.5, 3, graine + 42)
    sale = lisse(0.45, 0.8, coul) * 0.22
    rgb = rgb * (1 - sale[:, None] * force) + col("#2a2618") * (sale[:, None] * force) * 0.6
    return rgb * (1 - 0.55 * ray[:, None] * force) + col("#8f9182") * (0.55 * ray[:, None] * force)


def peindre_coque(P, N, capot=False):
    x, y, z = P.T
    haut, bas = col("#4b5335"), col("#5d6352")
    t = lisse(9, 22, y)[:, None]
    rgb = bas * (1 - t) + haut * t
    tache = fbm(P, 1 / 26, 3, 3)
    rgb = np.where((tache > 0.57)[:, None], rgb * 0.84 + col("#353a28") * 0.16, rgb)
    rgb = rgb * (0.93 + 0.14 * fbm(P, 1 / 3.5, 3, 5))[:, None]
    noir = np.zeros(len(P))
    cote = np.abs(N[:, 0]) > 0.45
    # panneaux : coupes le long du fuselage, lignes horizontales des flancs, portes
    dz = np.where(z < 54, dist_grille(z, 24.0, 4.0), dist_grille(z, 20.0, 2.0))
    noir = np.maximum(noir, ligne(dz) * (z > -86))
    noir = np.maximum(noir, ligne(y - 13.0) * cote * (z < 30) * (z > -84))
    noir = np.maximum(noir, ligne(y - 47.5) * cote * (z < 26) * (z > -44))
    porte = (z > -28.5) & (z < 16.5) & (y > 11.5) & (y < 49.5) & cote
    dporte = np.minimum(np.minimum(np.abs(z + 28), np.abs(z - 16)), np.minimum(np.abs(y - 12), np.abs(y - 49)))
    noir = np.maximum(noir, ligne(dporte, 0.4) * porte)
    noir = np.maximum(noir, ligne(np.minimum(np.abs(z + 48.5), np.abs(z + 30.5)), 0.4) * cote * (y > 14) * (y < 49) * (z < -28))
    # rivets le long des coupes
    riv = (np.abs(np.abs(dz) - 0.85) < 0.28) & (np.abs(dist_grille(y + np.abs(x) * 0.5, 2.4)) < 0.28) & (z > -86)
    if capot:
        rgb = rgb * 0.92
        # suie des échappements
        for sx in (-1, 1):
            d = np.sqrt((x - 15 * sx) ** 2 + (y - 60) ** 2)
            k = lisse(14, 2, d) * lisse(18, 40, z) * (x * sx > 0)
            rgb = rgb * (1 - 0.75 * k[:, None]) + col("#15130f") * (0.75 * k[:, None])
        noir = np.maximum(noir, ligne(dist_grille(z, 12.0, 0.0)) * (y > 61))
    # fenêtres des portes (vitrage peint, opaque)
    for za, zb in ((-22.0, -6.0), (0.0, 12.0)):
        dx_ = np.maximum(np.maximum(za + 1.5 - z, z - zb + 1.5), 0)
        dy_ = np.maximum(np.maximum(34.5 - y, y - 43.5), 0)
        dd = np.sqrt(dx_ ** 2 + dy_ ** 2)
        fen = (dd < 1.5) & cote
        bord = fen & (dd > 0.9)
        reflet = 0.12 + 0.18 * lisse(-1, 1, np.sin((z + y * 0.8) * 0.22))
        vitre = col("#18232a") * (1 + reflet[:, None])
        rgb = np.where(fen[:, None], vitre, rgb)
        rgb = np.where(bord[:, None], col("#1a1a18"), rgb)
    # textes et bandes de danger
    txt = np.maximum(tache_texte(P, TEXTE_QUEUE, 1, 66, 98, 36.2, 43.8), tache_texte(P, TEXTE_QUEUE, -1, 66, 98, 36.2, 43.8))
    txt = txt * (z > 54) * (0.75 + 0.25 * lisse(0.3, 0.6, fbm(P, 1 / 1.2, 2, 9)))
    rgb = rgb * (1 - txt[:, None]) + col("#c9c6b4") * txt[:, None]
    dang = np.maximum(tache_texte(P, TEXTE_DANGER, 1, 100, 114, 37.5, 41.5), tache_texte(P, TEXTE_DANGER, -1, 100, 114, 37.5, 41.5))
    rgb = rgb * (1 - dang[:, None]) + col("#c23a2a") * dang[:, None]
    bande = (z > 118) & (z < 126) & (y > 34)
    rayure = (np.floor((z + y) / 2.2) % 2) == 0
    rgb = np.where((bande & rayure)[:, None], col("#c9a227"), np.where(bande[:, None], col("#1c1c1a"), rgb))
    rgb = usure(P, rgb, 1.0, 11)
    rgb = rgb * (1 - 0.72 * noir[:, None])
    rgb = np.where(riv[:, None], rgb * 1.25 + 0.03, rgb)
    rgb = rgb * (0.82 + 0.18 * lisse(4, 20, y))[:, None]
    return rgb, np.ones(len(P))


def peindre_verre(P, N):
    x, y, z = P.T
    a = np.full(len(P), 0.36)
    rgb = np.tile(col("#22343c"), (len(P), 1)) * (1 + 0.35 * lisse(30, 50, y))[:, None]
    cadre = np.zeros(len(P))
    for zc in (-75.0, -62.0, -50.2, -48.2, -30.8):
        cadre = np.maximum(cadre, ligne(z - zc, 0.7))
    cadre = np.maximum(cadre, ligne(x, 0.6) * (y > 38) * (z < -50))
    cadre = np.maximum(cadre, lisse(28.0, 26.0, y) * (z < -50))
    cadre = np.maximum(cadre, np.maximum(lisse(32.2, 31.0, y), lisse(45.8, 47.0, y)) * (z > -49) * (z < -30))
    frame = col("#3b412b") * (0.9 + 0.1 * fbm(P, 1 / 2, 2, 3))[:, None]
    rgb = rgb * (1 - cadre[:, None]) + frame * cadre[:, None]
    return rgb, np.maximum(a, cadre)


def metal(P, base, rayures=1.0, graine=0):
    rgb = np.tile(col(base), (len(P), 1)) * (0.9 + 0.2 * fbm(P, 1 / 2.5, 3, graine))[:, None]
    return usure(P, rgb, rayures, graine)


def peindre(mat, P, N, brule=False):
    """Couleur (K, 3) et opacité (K,) d'un matériau aux points P, N (repère naturel)."""
    x, y, z = P.T
    K = len(P)
    un = np.ones(K)
    if mat == "coque":
        rgb, a = peindre_coque(P, N)
    elif mat == "capot":
        rgb, a = peindre_coque(P, N, capot=True)
    elif mat == "verre":
        rgb, a = peindre_verre(P, N)
    elif mat in ("metal_sombre", "moyeu", "patin"):
        rgb, a = metal(P, {"metal_sombre": "#30332f", "moyeu": "#3a3d3b", "patin": "#2f322e"}[mat], 0.8, 21), un
    elif mat == "arme":
        rgb, a = metal(P, "#262827", 0.6, 22), un
    elif mat == "canon":
        rgb = metal(P, "#202224", 0.4, 23)
        chaud = lisse(-86, -96, z)[:, None] * 0.55
        rgb = rgb * (1 - chaud) + col("#4a4038") * chaud
        a = un
    elif mat == "echappement":
        rgb = metal(P, "#4a4138", 0.3, 24)
        k = lisse(24, 34, z)[:, None]
        rgb = rgb * (1 - k) + col("#141210") * k
        a = un
    elif mat == "suie":
        rgb, a = np.tile(col("#0d0c0b"), (K, 1)), un
    elif mat == "grille":
        rgb = np.tile(col("#1b1d1a"), (K, 1))
        lame = ligne(dist_grille(z, 1.4), 0.5)
        rgb = rgb * (1 - 0.6 * lame[:, None]) + col("#4a4f3c") * (0.6 * lame[:, None])
        a = un
    elif mat == "pale":
        r = np.sqrt(x ** 2 + (z - ROTOR[2]) ** 2)
        rgb = metal(P, "#2b2d2e", 0.5, 25)
        rgb = np.where(((r > 98) & (r < 103.5))[:, None], col("#c9a227"), rgb)
        a = un
    elif mat == "pale_queue":
        r = np.sqrt((y - QUEUE_ROTOR[1]) ** 2 + (z - QUEUE_ROTOR[2]) ** 2)
        bande = (np.floor(r / 4.5) % 2 == 0) & (r > 6)
        rgb, a = np.where(bande[:, None], col("#b8382c"), col("#d7d4c8")), un
    elif mat == "flou":
        grand = np.abs(x - QUEUE_ROTOR[0]) > 1.5
        rr = np.where(grand, np.sqrt(x ** 2 + (z - ROTOR[2]) ** 2) / 106.0,
                      np.sqrt((y - QUEUE_ROTOR[1]) ** 2 + (z - QUEUE_ROTOR[2]) ** 2) / 19.0)
        bout = (rr > 0.925) & (rr < 0.975)
        a = 0.075 * lisse(0.08, 0.25, rr) * (1 - lisse(0.94, 1.0, rr)) * (0.9 + 0.1 * np.sin(rr * 40)) + 0.03 * bout
        rgb = np.where(bout[:, None], col("#6e6440"), col("#1f2120"))
    elif mat == "panier":
        rgb = peindre_coque(P, N)[0] * 0.95
        rgb = rgb * (1 - 0.6 * ligne(dist_grille(z, 10.0, 0.0), 0.5)[:, None])
        a = un
    elif mat == "roquettes":
        sx = np.sign(x)
        lx, ly = x - 36 * sx, y - 16.5
        rgb = np.tile(col("#3d4430"), (K, 1))
        trous = [(0, 0)] + [(2.6 * math.cos(k * math.pi / 3), 2.6 * math.sin(k * math.pi / 3)) for k in range(6)]
        for cx, cy in trous:
            d = np.sqrt((lx - cx) ** 2 + (ly - cy) ** 2)
            rgb = np.where(((d >= 1.15) & (d < 1.4))[:, None], col("#6e6f62"), rgb)
            rgb = np.where((d < 1.15)[:, None], col("#0b0b0a"), rgb)
            rgb = np.where((d < 0.55)[:, None], col("#8f7b3a"), rgb)
        a = un
    elif mat == "interieur":
        rgb, a = metal(P, "#242624", 0.2, 26), un
    elif mat == "tableau":
        rgb = np.tile(col("#161817"), (K, 1))
        ecr = (np.abs(dist_grille(x, 7.5, 3.75)) < 2.6) & (y > 25.5) & (y < 29)
        rgb = np.where(ecr[:, None], col("#2c6b3f") * (0.8 + 0.2 * np.sin(y * 9))[:, None], rgb)
        a = un
    elif mat == "siege":
        rgb, a = metal(P, "#3a3328", 0.2, 27), un
    elif mat == "pilote":
        rgb, a = metal(P, "#4a4d38", 0.15, 28) * 0.9, un
    elif mat == "casque":
        rgb, a = metal(P, "#3f4630", 0.1, 29), un
    elif mat == "visiere":
        rgb, a = np.tile(col("#0b0d10"), (K, 1)) + 0.05 * lisse(35, 37.5, y)[:, None], un
    elif mat in EMISSIFS:
        rgb, a = np.tile(col(EMISSIFS[mat]), (K, 1)), un
    elif mat == "caisse":
        rgb = np.tile(col("#4d5532"), (K, 1)) * (0.88 + 0.24 * fbm(np.stack([x * 0.25, y * 2.5, z * 0.25], -1), 1 / 1.5, 3, 31))[:, None]
        lame = ligne(dist_grille(y, 3.4, 0.6), 0.25) * (np.abs(N[:, 1]) < 0.5)
        rgb = rgb * (1 - 0.5 * lame[:, None])
        s_ = np.where(N[:, 2] > 0, (x + 8) / 16, (8 - x) / 16)
        t_ = (9.4 - y) / 3.6
        txt = echantillon(TEXTE_CAISSE, s_, t_) * (np.abs(N[:, 2]) > 0.7)
        rgb = rgb * (1 - 0.85 * txt[:, None]) + col("#d8d2b8") * (0.85 * txt[:, None])
        rgb = usure(P, rgb, 0.6, 32)
        a = un
    elif mat == "caisse_couvercle":
        rgb = usure(P, np.tile(col("#454c2d"), (K, 1)) * (0.9 + 0.2 * fbm(P, 1 / 1.5, 3, 33))[:, None], 0.7, 34)
        a = un
    elif mat == "corniere":
        rgb, a = metal(P, "#2c2e2b", 1.2, 35), un
    elif mat == "fermoir":
        rgb, a = metal(P, "#6a6c66", 0.6, 36), un
    elif mat == "corde":
        rgb = np.tile(col("#8a7a55"), (K, 1)) * (0.75 + 0.35 * (np.sin((x + y + z) * 6) * 0.5 + 0.5))[:, None]
        a = un
    elif mat in PEINTURES_PIECES:
        rgb, a = PEINTURES_PIECES[mat](P, N), un
    else:
        raise SystemExit("matériau inconnu : " + mat)
    if brule:
        rgb = bruler(mat, P, rgb)
    return np.clip(rgb, 0, 1), np.clip(a, 0, 1)


EMISSIFS = {"phare": "#fff3d6", "feu_rouge": "#ff2a1f", "feu_vert": "#2bff5a", "feu_blanc": "#ffffff", "gyrophare": "#ff3020"}
PEINTURES_PIECES = {}


def bruler(mat, P, rgb):
    """Peinture de l'épave : noircie, rouille et métal bleui par la chaleur, cendres ; la caisse est à peine roussie."""
    if mat in ("caisse", "caisse_couvercle", "corniere", "fermoir", "corde"):
        k = lisse(0.55, 0.85, fbm(P, 1 / 6, 3, 61))[:, None] * 0.45
        return rgb * (1 - k) + col("#1b1813") * k
    if mat in EMISSIFS:
        return rgb * 0.08
    f = fbm(P, 1 / 10, 4, 62)
    noirci = lisse(0.30, 0.62, f)[:, None]
    out = rgb * 0.42 * (1 - noirci) + col("#12100e") * noirci
    rouille = lisse(0.62, 0.8, fbm(P, 1 / 5, 3, 63))[:, None] * (1 - noirci) * 0.8
    out = out * (1 - rouille) + col("#5a3018") * rouille
    bleui = lisse(0.7, 0.9, fbm(P, 1 / 4, 3, 64))[:, None] * 0.5
    out = out * (1 - bleui) + col("#2f3140") * bleui
    cendre = lisse(0.78, 0.95, bruit(P, 1 / 1.7, 65))[:, None] * 0.35
    return out * (1 - cendre) + col("#6a655e") * cendre


def texture(m, W, H, brule=False):
    POS, NOR, MAT, mats = cartes(m, W, H)
    img = np.zeros((H, W, 4))
    for k, mat in enumerate(mats):
        sel = MAT == k
        if not sel.any():
            continue
        rgb, a = peindre(mat, POS[sel], NOR[sel], brule)
        img[sel, :3] = rgb
        img[sel, 3] = a
    return img


def png(a, fichier):
    Path(fichier).parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray((np.clip(a, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").save(fichier, optimize=True)


# =============================================================================================== fichier .geo
def nombre(v):
    """Nombre court pour le .geo (3 décimales, sans zéros inutiles)."""
    t = ("%.3f" % v).rstrip("0").rstrip(".")
    return "0" if t in ("-0", "") else t


def ecrire_geo(m, W, H, fichier, entete):
    """Format lu par client/model/GeoLoader : positions et normales dans le repère du jeu (x et y retournés),
    relatives au pivot de leur partie ; points dans le repère naturel ; u v en points de la texture."""
    lignes = ["# " + entete + " - ÉCRIT par mods/airstrike/outils/modele/helico.py, ne pas modifier à la main",
              "texture %d %d" % (W, H)]
    parts = [p for p in m.parts if not p.startswith("_")]
    for p in parts:
        if "/" not in p:
            lignes.append("pass %s %s" % (p, m.passes[p]))
    for nom, (x, y, z) in m.points.items():
        lignes.append("point %s %.4f %.4f %.4f" % (nom, x, y, z))
    nq = 0
    for p in sorted(parts, key=lambda c: (c.count("/"), c)):
        pivot = m.parts[p][0]
        parent = p.rsplit("/", 1)[0] if "/" in p else None
        rel = pivot - (m.parts[parent][0] if parent else 0)
        lignes.append("part %s %.4f %.4f %.4f 0 0 0" % (p, -rel[0], -rel[1], rel[2]))
        for s in m.surfs:
            if s.part != p:
                continue
            for q in s.quads:
                P, N, T = q[0], q[1], q[3]
                vals = []
                for i in range(4):
                    x, y, z = P[i] - pivot
                    vals += [-x, -y, z, T[i, 0], T[i, 1], -N[i, 0], -N[i, 1], N[i, 2]]
                lignes.append("quad " + " ".join(nombre(v) for v in vals))
                nq += 1
    Path(fichier).parent.mkdir(parents=True, exist_ok=True)
    Path(fichier).write_text("\n".join(lignes) + "\n", encoding="utf-8")
    return nq


# =============================================================================================== rendu logiciel (aperçus)
def rot(axe, a):
    c, s = math.cos(a), math.sin(a)
    x, y, z = norm(axe)
    return np.array([[c + x * x * (1 - c), x * y * (1 - c) - z * s, x * z * (1 - c) + y * s],
                     [y * x * (1 - c) + z * s, c + y * y * (1 - c), y * z * (1 - c) - x * s],
                     [z * x * (1 - c) - y * s, z * y * (1 - c) + x * s, c + z * z * (1 - c)]])


def orientation(lacet=0.0, tangage=0.0, roulis=0.0):
    """Comme StrikeRenderer.pushAircraft / HelicoRendu : lacet autour de y, tangage (> 0 : nez en bas), roulis (> 0 :
    côté droit en bas). Degrés."""
    return rot((0, 1, 0), math.radians(-lacet)) @ rot((1, 0, 0), math.radians(-tangage)) @ rot((0, 0, 1), math.radians(-roulis))


def triangles(m, tex, anim=None, R=np.eye(3), t=np.zeros(3), sauf=()):
    """Triangles du modèle posé : P (n, 3, 3) monde, N, UV (points de texture), passe, texture."""
    anim = anim or {}
    cache = {}

    def mat(path):
        if path in cache:
            return cache[path]
        pivot = m.parts[path][0]
        Rl = anim.get(path, np.eye(3))
        Rr, tr = Rl, pivot - Rl @ pivot
        if "/" in path:
            Rp, tp = mat(path.rsplit("/", 1)[0])
            Rr, tr = Rp @ Rr, Rp @ tr + tp
        cache[path] = (Rr, tr)
        return Rr, tr

    out = {"P": [], "N": [], "UV": [], "passe": [], "tex": []}
    for s in m.surfs:
        if not s.quads or s.part.startswith("_") or any(s.part == x or s.part.startswith(x + "/") for x in sauf):
            continue
        Rr, tr = mat(s.part)
        top = s.part.split("/")[0]
        passe = m.passes.get(top, "solid")
        for q in s.quads:
            P = (q[0] @ Rr.T + tr) @ R.T + t
            N = q[1] @ Rr.T @ R.T
            T = q[3]
            for tri in ((0, 1, 2), (0, 2, 3)):
                out["P"].append(P[list(tri)])
                out["N"].append(N[list(tri)])
                out["UV"].append(T[list(tri)])
                out["passe"].append(passe)
                out["tex"].append(tex)
    return out


def fusion(*listes):
    r = {"P": [], "N": [], "UV": [], "passe": [], "tex": []}
    for l in listes:
        for k in r:
            r[k] += l[k]
    return r


class Rendu:
    """Petit moteur de rendu : perspective, tampon de profondeur, textures (bilinéaire), soleil avec ombres portées,
    ciel, sol, fumée et flammes (taches douces). Unités : pixels de modèle."""

    def __init__(self, W, H, oeil, cible, fov=38.0, ss=2):
        self.W, self.H, self.ss = W * ss, H * ss, ss
        self.oeil = np.asarray(oeil, float)
        f = norm(np.asarray(cible, float) - self.oeil)
        r = norm(np.cross(f, (0, 1, 0)))
        u = np.cross(r, f)
        self.f, self.r, self.u = f, r, u
        self.fpx = (self.H / 2) / math.tan(math.radians(fov) / 2)
        self.soleil = norm((0.45, 0.8, -0.35))
        self.ciel_haut, self.ciel_bas = col("#6f93b8"), col("#c9d6dc")
        self.img = np.zeros((self.H, self.W, 3))
        self.z = np.full((self.H, self.W), np.inf)
        self.ombre = None
        self.brouillard = 9000.0
        self.expo = 1.0

    def projeter(self, P):
        d = P - self.oeil
        zc = d @ self.f
        x = d @ self.r
        y = d @ self.u
        return np.stack([self.W / 2 + x / np.maximum(zc, 1e-6) * self.fpx, self.H / 2 - y / np.maximum(zc, 1e-6) * self.fpx, zc], -1)

    # ------------------------------------------------------------------ ombres
    def carte_ombre(self, tris, taille=2048):
        L = self.soleil
        lx = norm(np.cross(L, (0, 0, 1.0)))
        ly = np.cross(L, lx)
        P = np.array(tris["P"]).reshape(-1, 3)
        if len(P) == 0:
            return
        X, Y = P @ lx, P @ ly
        mn = np.array([X.min(), Y.min()]) - 4
        mx = np.array([X.max(), Y.max()]) + 4
        ech = (taille - 1) / max(mx - mn)
        prof = np.full((taille, taille), -np.inf)
        for Pt, pa in zip(tris["P"], tris["passe"]):
            if pa != "solid":
                continue
            A = np.stack([(Pt @ lx - mn[0]) * ech, (Pt @ ly - mn[1]) * ech, Pt @ L], -1)
            self._raster(A, prof, None, maxi=True)
        self.ombre = (lx, ly, mn, ech, prof, taille)

    def ombrage(self, W):
        if self.ombre is None:
            return np.ones(W.shape[:-1])
        lx, ly, mn, ech, prof, taille = self.ombre
        X = (W @ lx - mn[0]) * ech
        Y = (W @ ly - mn[1]) * ech
        Z = W @ self.soleil
        tot = np.zeros(W.shape[:-1])
        for dx in (-1, 0, 1):
            for dy in (-1, 0, 1):
                xi = np.clip(np.round(X + dx).astype(int), 0, taille - 1)
                yi = np.clip(np.round(Y + dy).astype(int), 0, taille - 1)
                tot += (prof[yi, xi] > Z + 1.2).astype(float)
        dans = (X >= 0) & (X < taille) & (Y >= 0) & (Y < taille)
        return 1 - np.where(dans, tot / 9.0, 0.0)

    @staticmethod
    def _raster(A, tampon, rappel, maxi=False):
        """Remplit tampon (min ou max de la 3e coordonnée) ; rappel(pix_y, pix_x, b0, b1, b2) pour les points gagnants."""
        H, W = tampon.shape
        x0, x1 = int(max(0, math.floor(A[:, 0].min()))), int(min(W - 1, math.ceil(A[:, 0].max())))
        y0, y1 = int(max(0, math.floor(A[:, 1].min()))), int(min(H - 1, math.ceil(A[:, 1].max())))
        if x1 < x0 or y1 < y0:
            return
        X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
        (ax, ay, _), (bx, by, _), (cx, cy, _) = A
        den = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy)
        if abs(den) < 1e-9:
            return
        l1 = ((by - cy) * (X - cx) + (cx - bx) * (Y - cy)) / den
        l2 = ((cy - ay) * (X - cx) + (ax - cx) * (Y - cy)) / den
        l3 = 1 - l1 - l2
        ok = (l1 >= 0) & (l2 >= 0) & (l3 >= 0)
        if not ok.any():
            return
        z = l1 * A[0, 2] + l2 * A[1, 2] + l3 * A[2, 2]
        sl = tampon[y0:y1 + 1, x0:x1 + 1]
        gagne = ok & ((z > sl) if maxi else (z < sl))
        if rappel is None:
            sl[gagne] = z[gagne]
            return
        rappel(y0, x0, gagne, l1, l2, l3, z)

    # ------------------------------------------------------------------ ciel et sol
    def fond(self, sol_y=None, sol_couleur=None, brulure=None):
        jj, ii = np.mgrid[0:self.H, 0:self.W]
        dx = (ii + 0.5 - self.W / 2) / self.fpx
        dy = -(jj + 0.5 - self.H / 2) / self.fpx
        d = norm(self.f + dx[..., None] * self.r + dy[..., None] * self.u)
        h = np.clip(d[..., 1], -1, 1)
        ciel = self.ciel_bas * (1 - lisse(0, 0.6, h))[..., None] + self.ciel_haut * lisse(0, 0.6, h)[..., None]
        gl = np.clip(d @ self.soleil, 0, 1) ** 40
        ciel = ciel + gl[..., None] * col("#fff2d0") * 0.5
        self.img[:] = ciel
        if sol_y is None:
            return
        tt = (sol_y - self.oeil[1]) / np.where(d[..., 1] < -1e-4, d[..., 1], -1e-4)
        touche = (d[..., 1] < -1e-4) & (tt > 0)
        Wp = self.oeil + d * tt[..., None]
        zc = tt * (d @ self.f)
        c = (sol_couleur or sol_herbe)(Wp)
        if brulure is not None:
            cx, cz, rr = brulure
            dd = np.sqrt((Wp[..., 0] - cx) ** 2 + (Wp[..., 2] - cz) ** 2) / rr
            k = lisse(1.15, 0.35, dd + 0.25 * (fbm(Wp * np.array([1, 0, 1]), 1 / 30, 3, 77) - 0.5))[..., None]
            c = c * (1 - k) + col("#1d1915") * k * (0.8 + 0.4 * fbm(Wp, 1 / 6, 2, 78))[..., None]
        sh = self.ombrage(Wp)
        lum = 0.45 + 0.6 * sh * max(0.0, self.soleil[1])
        c = c * lum[..., None]
        brou = 1 - np.exp(-np.maximum(tt, 0) / self.brouillard)
        c = c * (1 - brou[..., None]) + self.ciel_bas * brou[..., None]
        self.img = np.where(touche[..., None], c, self.img)
        self.z = np.where(touche, zc, self.z)

    # ------------------------------------------------------------------ objets
    def objets(self, tris, textures):
        ordre = {"solid": 0, "emissive": 1, "glass": 2}
        idx = sorted(range(len(tris["P"])), key=lambda i: ordre[tris["passe"][i]])
        verres = []
        for i in idx:
            pa = tris["passe"][i]
            if pa == "glass":
                verres.append(i)
                continue
            self._tri(tris, i, textures, pa)
        # verres : du plus loin au plus près, sans écrire la profondeur
        verres.sort(key=lambda i: -float(np.mean(tris["P"][i] @ self.f)))
        for i in verres:
            self._tri(tris, i, textures, "glass")

    def _tri(self, tris, i, textures, passe):
        Pw, Nw, UV = tris["P"][i], tris["N"][i], tris["UV"][i]
        tex = textures[tris["tex"][i]]
        g = np.cross(Pw[1] - Pw[0], Pw[2] - Pw[0])
        if passe != "glass" and float(g @ (self.oeil - Pw[0])) <= 0:
            return
        A = self.projeter(Pw)
        if (A[:, 2] < 2).any():
            return
        L = self.soleil
        Ht, Wt = tex.shape[:2]

        def ecrire(y0, x0, gagne, l1, l2, l3, z):
            # barycentriques corrigées de la perspective
            w = np.stack([l1 / A[0, 2], l2 / A[1, 2], l3 / A[2, 2]], -1)
            w = w / w.sum(-1, keepdims=True)
            sel = gagne
            ws = w[sel]
            uv = ws @ UV
            u, v = uv[:, 0] - 0.5, uv[:, 1] - 0.5
            u0, v0 = np.floor(u).astype(int), np.floor(v).astype(int)
            fu, fv = (u - u0)[:, None], (v - v0)[:, None]
            c00 = tex[np.clip(v0, 0, Ht - 1), np.clip(u0, 0, Wt - 1)]
            c10 = tex[np.clip(v0, 0, Ht - 1), np.clip(u0 + 1, 0, Wt - 1)]
            c01 = tex[np.clip(v0 + 1, 0, Ht - 1), np.clip(u0, 0, Wt - 1)]
            c11 = tex[np.clip(v0 + 1, 0, Ht - 1), np.clip(u0 + 1, 0, Wt - 1)]
            c = (c00 * (1 - fu) + c10 * fu) * (1 - fv) + (c01 * (1 - fu) + c11 * fu) * fv
            alb, a = c[:, :3], c[:, 3]
            if passe == "solid":
                garde = a > 0.5
            else:
                garde = a > 0.004
            ys, xs = np.nonzero(sel)
            ys, xs = ys[garde] + y0, xs[garde] + x0
            alb, a, ws = alb[garde], a[garde], ws[garde]
            if len(ys) == 0:
                return
            n = norm(ws @ Nw)
            Wp = ws @ Pw
            vue = norm(self.oeil - Wp)
            n = np.where((np.sum(n * vue, -1) < 0)[:, None] & (passe == "glass"), -n, n)
            if passe == "emissive":
                col_ = np.clip(alb * 1.25, 0, 1)
            else:
                sh = self.ombrage(Wp)
                dif = np.clip(n @ L, 0, 1) * sh
                amb = 0.30 + 0.20 * (n[:, 1] * 0.5 + 0.5)
                h = norm(L + vue)
                spec = np.clip(np.sum(n * h, -1), 0, 1) ** 36 * sh * 0.32
                col_ = alb * (amb + 0.92 * dif)[:, None] * self.expo + spec[:, None] * col("#fff6e6")
                if passe == "glass":
                    fres = (1 - np.clip(np.sum(n * vue, -1), 0, 1)) ** 3
                    col_ = col_ + (fres * 0.35)[:, None] * self.ciel_haut
            dist = np.linalg.norm(Wp - self.oeil, axis=-1)
            brou = (1 - np.exp(-dist / self.brouillard))[:, None]
            col_ = col_ * (1 - brou) + self.ciel_bas * brou
            if passe == "glass":
                a2 = a[:, None]
                self.img[ys, xs] = self.img[ys, xs] * (1 - a2) + col_ * a2
            else:
                self.img[ys, xs] = col_
                self.z[ys, xs] = z[sel][garde]

        self._raster(A, self.z, ecrire)       # le verre ne fait que lire la profondeur (ecrire ne l'écrit pas)

    # ------------------------------------------------------------------ fumée, flammes
    def taches(self, parts):
        """parts : liste de (centre, rayon, rgb, opacité, additif). Taches douces, cachées par ce qui est devant."""
        P = np.array([p[0] for p in parts]) if parts else np.zeros((0, 3))
        if len(P) == 0:
            return
        A = self.projeter(P)
        ordre = np.argsort(-A[:, 2])
        for k in ordre:
            c, r, rgb, al, add = parts[k]
            sx, sy, zc = A[k]
            if zc < 2:
                continue
            rp = r / zc * self.fpx
            if rp < 0.6:
                continue
            x0, x1 = int(max(0, sx - rp * 1.6)), int(min(self.W - 1, sx + rp * 1.6))
            y0, y1 = int(max(0, sy - rp * 1.6)), int(min(self.H - 1, sy + rp * 1.6))
            if x1 <= x0 or y1 <= y0:
                continue
            X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            d2 = ((X - sx) ** 2 + (Y - sy) ** 2) / (rp * rp)
            nz = 0.65 + 0.7 * fbm(np.stack([X / max(rp, 1) * 2.2 + k * 3.1, Y / max(rp, 1) * 2.2, np.full(X.shape, k * 0.37)], -1), 1.0, 3, 90)
            a = al * np.exp(-d2 * 1.6) * nz
            devant = self.z[y0:y1 + 1, x0:x1 + 1] > zc - r * 0.5
            a = np.clip(a * devant, 0, 1)
            sl = self.img[y0:y1 + 1, x0:x1 + 1]
            if add:
                sl += a[..., None] * np.asarray(rgb)
            else:
                sl[:] = sl * (1 - a[..., None]) + np.asarray(rgb) * a[..., None]

    def image(self):
        im = Image.fromarray((np.clip(self.img, 0, 1) ** (1 / 1.05) * 255).astype(np.uint8))
        return im.resize((self.W // self.ss, self.H // self.ss), Image.LANCZOS)


def sol_herbe(Wp):
    n1 = fbm(Wp * np.array([1, 0, 1]), 1 / 40, 4, 70)
    n2 = fbm(Wp * np.array([1, 0, 1]), 1 / 4, 2, 71)
    herbe = col("#56683a") * (0.8 + 0.35 * n2)[..., None]
    terre = col("#6b5a42") * (0.85 + 0.3 * n2)[..., None]
    k = lisse(0.55, 0.7, n1)[..., None]
    return herbe * (1 - k) + terre * k


def fumee(depart, direction, n, r0, r1, couleur, opacite, graine=0, montee=1.0):
    """Panache : taches le long d'une trajectoire qui monte et s'élargit."""
    rng = np.random.default_rng(graine)
    out = []
    d = norm(direction)
    for i in range(n):
        t = i / max(n - 1, 1)
        p = np.asarray(depart, float) + d * t * 260 + np.array([0, 1, 0]) * (t ** 1.3) * 140 * montee
        p = p + rng.normal(0, 3 + 22 * t, 3)
        r = r0 + (r1 - r0) * t ** 0.8
        g = 0.85 + 0.3 * rng.random()
        out.append((p, r * (0.8 + 0.4 * rng.random()), np.asarray(couleur) * g, opacite * (1 - 0.55 * t), False))
    return out


def flammes(centre, rayon, n, graine=0, haut=1.0):
    rng = np.random.default_rng(graine)
    out = []
    for i in range(n):
        p = np.asarray(centre, float) + np.array([rng.normal(0, rayon), rng.random() * 14 * haut, rng.normal(0, rayon)])
        r = 4 + 6 * rng.random()
        couleur = col("#ff9a2a") if rng.random() < 0.6 else col("#ffd25a")
        out.append((p, r, couleur * 0.9, 0.7, True))
    return out


def legende(im, titre, sous=None):
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(str(POLICE), 22)
    f2 = ImageFont.truetype(str(POLICE), 15)
    w = d.textlength(titre, font=f)
    d.rectangle((14, im.height - 62, 30 + max(w, d.textlength(sous or "", font=f2)), im.height - 12), fill=(18, 20, 22))
    d.text((22, im.height - 58), titre, font=f, fill=(235, 232, 220))
    if sous:
        d.text((22, im.height - 32), sous, font=f2, fill=(170, 175, 170))
    return im


# =============================================================================================== épave
CABINE_EXCLUS = {"pilote", "casque", "visiere", "patin", "capot", "echappement", "grille", "pale", "pale_queue",
                 "panier", "roquettes", "flou", "verre"} | set(EMISSIFS)


def est_aile(c):
    return abs(c[0]) > 18.5 and 15 < c[1] < 29 and abs(c[2]) < 24


def deplacer_bruit(P, ampl, graine):
    d = np.stack([fbm(P, 1 / 9, 2, graine + k) - 0.5 for k in range(3)], -1)
    return P + d * 2 * ampl


def plier_y(P, N, pivot_z, angle_par_px, z_min):
    """Plie autour de l'axe y passant par (0, *, pivot_z) : angle qui croît avec z au-delà de z_min."""
    a = np.maximum(P[:, 2] - z_min, 0) * angle_par_px
    c, s = np.cos(a), np.sin(a)
    x, z = P[:, 0], P[:, 2] - pivot_z
    P2 = P.copy()
    P2[:, 0] = x * c + z * s
    P2[:, 2] = pivot_z - x * s + z * c
    N2 = N.copy()
    N2[:, 0] = N[:, 0] * c + N[:, 2] * s
    N2[:, 2] = -N[:, 0] * s + N[:, 2] * c
    return P2, N2


PIECES_EPAVE = ["cabine", "queue", "rotor", "moteur", "patins", "aile"]


def modele_epave(h, W, H):
    """Les 6 morceaux de l'épave et la caisse, faits des quads de l'hélicoptère (mêmes coordonnées de texture : la
    texture de l'épave est celle de l'hélicoptère, brûlée)."""
    suie = None
    for s in h.surfs:
        if s.mat == "suie" and s.quads:
            suie = s.quads[0][3].mean(0)
            break
    e = Modele()
    choix = {k: [] for k in range(6)}
    caisse = []
    for s in h.surfs:
        for q in s.quads:
            P, N, T = q[0], q[1], q[3]
            c = P.mean(0)
            mat, part = s.mat, s.part
            if part == "_caisse":
                caisse.append((P, N, T, mat))
                continue
            if mat in ("flou", "verre", "pilote", "casque", "visiere") or mat in EMISSIFS:
                continue
            if part.startswith("corps/rotor") or (mat == "metal_sombre" and abs(c[0]) < 3.2 and c[1] > 62 and abs(c[2] + 6) < 4):
                choix[2].append((P, N, T, mat))
            elif mat in ("capot", "grille", "echappement") or (mat == "suie" and c[1] > 50):
                choix[3].append((P, N, T, mat))
            elif mat == "patin":
                choix[4].append((P, N, T, mat))
            elif mat in ("panier", "roquettes") or (est_aile(c) and mat in ("coque", "metal_sombre")):
                if c[0] > 0:
                    choix[5].append((P, N, T, mat))
            elif part.startswith("corps/queue_rotor"):
                if abs(c[1] - QUEUE_ROTOR[1]) > abs(c[2] - QUEUE_ROTOR[2]) or mat == "moyeu":
                    choix[1].append((P, N, T, mat))
            elif mat == "coque" and c[2] >= 40:
                choix[1].append((P, N, T, mat))
            elif mat == "metal_sombre" and c[2] > 120:
                choix[1].append((P, N, T, mat))
            elif mat not in CABINE_EXCLUS:
                choix[0].append((P, N, T, mat))
    def garde_rotor(q):
        """Rotor de l'épave : deux pales cassées court, deux à mi-longueur."""
        if q[3] != "pale":
            return True
        P0 = q[0]
        rr = np.sqrt(P0[:, 0] ** 2 + (P0[:, 2] - ROTOR[2]) ** 2).max()
        ph = math.atan2(P0.mean(0)[2] - ROTOR[2], P0.mean(0)[0])
        bb = int(round((ph - math.pi / 4) / (math.pi / 2))) % 4
        return rr <= (44 if bb in (1, 3) else 72)

    choix[2] = [q for q in choix[2] if garde_rotor(q)]
    poses = {0: orientation(0, 7, 21), 1: orientation(0, -4, 78), 2: orientation(0, -7, 12),
             3: orientation(0, 11, -27), 4: orientation(0, 0, 9), 5: orientation(0, 6, -33)}
    tailles = {}
    for k, quads in choix.items():
        P = np.concatenate([q[0] for q in quads])
        N = np.concatenate([q[1] for q in quads])
        if k == 0:
            f = np.clip((-56 - P[:, 2]) / 36, 0, 1)
            P[:, 1] = P[:, 1] - f * (P[:, 1] - 22) * 0.38
            P[:, 2] = P[:, 2] + f * 7
            P = deplacer_bruit(P, 1.3, 101)
        elif k == 1:
            P, N = plier_y(P, N, 70.0, 0.42 / 66, 70.0)
            P = deplacer_bruit(P, 0.9, 102)
        elif k == 2:
            r = np.sqrt(P[:, 0] ** 2 + (P[:, 2] - ROTOR[2]) ** 2)
            phi = np.arctan2(P[:, 2] - ROTOR[2], P[:, 0])
            b = np.round((phi - math.pi / 4) / (math.pi / 2)).astype(int) % 4
            k_b = np.array([0.020, 0.045, 0.012, 0.055])[b]
            P[:, 1] = P[:, 1] - k_b * np.maximum(r - 11, 0) ** 1.35 + np.where(b == 0, 0.004 * np.maximum(r - 11, 0) ** 2, 0)
        elif k == 3:
            P = deplacer_bruit(P, 1.1, 103)
        elif k == 4:
            P[:, 1] = P[:, 1] + np.where(P[:, 0] > 0, 0.004 * (P[:, 2] + 10) ** 2 * 0.15, 0)
            P = deplacer_bruit(P, 0.5, 104)
        Rr = poses[k]
        P = P @ Rr.T
        N = norm(N @ Rr.T)
        mn, mx = P.min(0), P.max(0)
        decal = np.array([-(mn[0] + mx[0]) / 2, -mn[1], -(mn[2] + mx[2]) / 2])
        P = P + decal
        tailles[k] = P.max(0) - P.min(0)
        nom = "piece_%d" % k
        e.partie(nom)
        s = e.surf(nom, "epave")
        for i, (_, _, T, mat) in enumerate(quads):
            Pq, Nq = P[i * 4:i * 4 + 4], N[i * 4:i * 4 + 4]
            s.quads.append([Pq, Nq, None, T])
            if k in (0, 1, 3) and mat in ("coque", "capot") and suie is not None:
                s.quads.append([Pq[::-1].copy(), -Nq[::-1].copy(), None, np.repeat(suie[None], 4, 0)])
    e.partie("caisse")
    s = e.surf("caisse", "caisse")
    for P, N, T, mat in caisse:
        s.quads.append([P.copy(), N.copy(), None, T])
    tailles["caisse"] = np.array(CAISSE)
    return e, tailles


# =============================================================================================== pièces (icônes)
def _tole(P, N):
    x, y, z = P.T
    rgb = np.tile(col("#4b5335"), (len(P), 1)) * (0.9 + 0.2 * fbm(P, 1 / 3, 3, 201))[:, None]
    lig = ligne(x - 1.0, 0.35)
    riv = (np.abs(x - 2.0) < 0.35) & (np.abs(dist_grille(z, 2.2)) < 0.35)
    rgb = rgb * (1 - 0.7 * lig[:, None])
    rgb = np.where(riv[:, None], rgb * 1.3 + 0.04, rgb)
    bord = lisse(5.5, 8.0, x + 1.2 * np.sin(z * 1.7))
    rgb = rgb * (1 - bord[:, None]) + col("#1d1a16") * bord[:, None]
    return usure(P, rgb, 1.4, 202)


def _plastique(hexa):
    return lambda P, N: np.tile(col(hexa), (len(P), 1)) * (0.85 + 0.25 * (N[:, 1] * 0.5 + 0.5))[:, None]


def _reservoir(P, N):
    x, y, z = P.T
    rgb = np.tile(col("#566038"), (len(P), 1)) * (0.9 + 0.2 * fbm(P, 1 / 3, 3, 203))[:, None]
    t = echantillon(masque_texte("JP-8"), (z + 4.5) / 9, (2.0 - y) / 4.0) * (N[:, 0] < -0.55)
    rgb = rgb * (1 - t[:, None]) + col("#d8d2b8") * t[:, None]
    return usure(P, rgb, 0.8, 204)


def _avionique_face(P, N):
    x, y, z = P.T
    rgb = np.tile(col("#1e2021"), (len(P), 1))
    ecran = (np.abs(x) < 4.2) & (y > 3.2) & (y < 7.6)
    grille = (np.abs(dist_grille(x, 1.0)) < 0.08) | (np.abs(dist_grille(y, 1.0)) < 0.08)
    vert = col("#3fd07a") * (0.55 + 0.45 * lisse(0.2, 0.9, fbm(P, 1 / 1.3, 2, 205)))[:, None]
    rgb = np.where(ecran[:, None], np.where(grille[:, None], vert * 1.3, vert * 0.7), rgb)
    btn = (y > 1.0) & (y < 2.4) & (np.abs(dist_grille(x, 1.8, 0.9)) < 0.55) & (np.abs(x) < 5)
    rgb = np.where(btn[:, None], col("#b5ad8c"), rgb)
    return rgb


def _turbine(P, N):
    rgb = np.tile(col("#9aa0a6"), (len(P), 1)) * (0.75 + 0.35 * fbm(P, 1 / 2, 3, 206))[:, None]
    chaud = lisse(0.5, 0.9, fbm(P, 1 / 5, 2, 207))[:, None] * 0.5
    return rgb * (1 - chaud) + col("#6d5c8a") * chaud


PEINTURES_PIECES.update({
    "tole": _tole, "cable_rouge": _plastique("#b8302a"), "cable_jaune": _plastique("#d0a427"),
    "cable_noir": _plastique("#1d1e1f"), "cable_bleu": _plastique("#2f5ea8"),
    "connecteur": lambda P, N: metal(P, "#7a7c76", 0.4, 208), "reservoir": _reservoir,
    "sangle": lambda P, N: metal(P, "#2a2c2a", 0.8, 209), "bouchon": lambda P, N: metal(P, "#8c7a3a", 0.5, 210),
    "avionique": lambda P, N: metal(P, "#2c2f31", 0.7, 211), "avionique_face": _avionique_face,
    "turbine": _turbine, "carter": lambda P, N: metal(P, "#4c4f50", 0.6, 212),
})


def piece_modele(nom):
    m = Modele()
    m.partie("p")
    if nom == "tole":
        anneaux = []
        for i in range(9):
            z = -7 + 14 * i / 8
            pts = []
            for j in range(13):
                a = -0.45 + 0.9 * j / 12
                r = 16 + (0.6 if j % 2 else 0) * 0
                pts.append((math.sin(a) * r + (0.6 * math.sin(z * 2.1 + j) if j in (0, 12) else 0), math.cos(a) * r - 16, z))
            anneaux.append(np.array(pts))
        A = np.array(anneaux)
        s = m.surf("p", "tole")
        for ep in (0, 1):
            for i in range(8):
                for j in range(12):
                    P = [A[i, j], A[i + 1, j], A[i + 1, j + 1], A[i, j + 1]]
                    P = np.array(P) * (1 - 0.04 * ep)
                    n = norm(np.array(P).mean(0) - np.array([0, -16, np.array(P).mean(0)[2]]))
                    s.quad(P, np.repeat((n if ep == 0 else -n)[None], 4, 0))
    elif nom == "cables":
        for k, (mat, dr) in enumerate((("cable_rouge", 0.0), ("cable_jaune", 1.5), ("cable_noir", 3.0), ("cable_bleu", 4.5))):
            anneaux = []
            for i in range(73):
                t = i / 72 * 2 * math.pi * 2.2
                rr = 6 + 0.5 * math.sin(t * 3 + k)
                c = np.array([math.cos(t) * rr, 2.0 + t * 0.45 + dr * 0.35, math.sin(t) * rr])
                tg = norm(np.array([-math.sin(t), 0.07, math.cos(t)]))
                e1 = norm(np.cross(tg, (0, 1, 0)))
                e2 = np.cross(tg, e1)
                anneaux.append(np.array([c + (math.cos(a) * e1 + math.sin(a) * e2) * 0.65 for a in np.linspace(0, 2 * math.pi, 8, endpoint=False)]))
            m.loft("p", mat, anneaux)
        m.boite("p", "connecteur", (5, 5.5, -2), (9, 8.5, 2))
    elif nom == "pale":
        pale(m, "p", np.array([0.0, 0, ROTOR[2]]), np.array([1.0, 0, 0]), 62, 104, 8.0, 5.0, "pale")
        m.cylindre("p", "metal_sombre", (60, 0, ROTOR[2]), (64, 0, ROTOR[2]), 1.6, seg=8)
    elif nom == "reservoir":
        m.cylindre("p", "reservoir", (-5.5, 0, -7), (-5.5, 0, 7), 5.5, seg=24, mat_cap="reservoir")
        for z in (-4, 4):
            m.cylindre("p", "sangle", (-5.5, 0, z - 0.6), (-5.5, 0, z + 0.6), 5.8, seg=24, caps=(False, False))
        m.cylindre("p", "bouchon", (-5.5, 5.2, 2), (-5.5, 6.6, 2), 1.4, seg=12)
    elif nom == "mitrailleuse":
        m.boite("p", "arme", (-3.4, 0, -73), (3.4, 5, -59))
        m.boite("p", "arme", (3.4, 0.5, -70), (6.5, 4.5, -62))
        m.cylindre("p", "arme", (0, 2.5, -73), (0, 2.5, -77), 3.0, seg=14)
        for k in range(6):
            a = 2 * math.pi * k / 6
            o = np.array([math.cos(a) * 1.85, 2.5 + math.sin(a) * 1.85, 0])
            m.cylindre("p", "canon", o + (0, 0, -77), o + (0, 0, -96), 0.62, seg=6, caps=(False, True), mat_cap="suie")
        m.cylindre("p", "arme", (0, 2.5, -89), (0, 2.5, -91), 2.75, seg=14)
    elif nom == "avionique":
        m.boite("p", "avionique", (-5.5, 0, -4.5), (5.5, 9, 4.5))
        s = m.surf("p", "avionique_face")
        s.quad([(5.5, 0, -4.55), (-5.5, 0, -4.55), (-5.5, 9, -4.55), (5.5, 9, -4.55)], np.repeat(np.array([[0, 0, -1.0]]), 4, 0))
        for sx in (-1, 1):
            m.boite("p", "connecteur", (sx * 6.2 - 0.5, 1, -5.6), (sx * 6.2 + 0.5, 8, -4.6))
        m.cylindre("p", "connecteur", (-2, 4.5, 4.5), (-2, 4.5, 6), 1.1, seg=10)
        m.cylindre("p", "connecteur", (2, 4.5, 4.5), (2, 4.5, 6), 1.1, seg=10)
    elif nom == "turbine":
        m.cylindre("p", "carter", (0, 0, -6), (0, 0, 6), 6.0, seg=28, caps=(False, False), rb=4.6)
        m.cylindre("p", "carter", (0, 0, -6), (0, 0, 6), 5.6, seg=28, caps=(False, False), rb=4.2)
        m.cylindre("p", "turbine", (0, 0, -5.0), (0, 0, -2.0), 1.6, seg=12, ra=0.4)
        for k in range(18):
            a = 2 * math.pi * k / 18
            d = np.array([math.cos(a), math.sin(a), 0])
            t = np.array([-math.sin(a), math.cos(a), 0])
            s = m.surf("p", "turbine")
            P = [d * 1.4 + (0, 0, -3.6) - t * 0.4, d * 5.4 + (0, 0, -4.4) - t * 0.9,
                 d * 5.4 + (0, 0, -2.6) + t * 0.9, d * 1.4 + (0, 0, -2.4) + t * 0.4]
            s.quad(P, np.repeat(norm(np.cross(np.array(P[1]) - P[0], np.array(P[2]) - P[0]))[None], 4, 0))
            s.quad(P[::-1], None)
    return m


def icone(nom, taille=64):
    m = piece_modele(nom)
    W, H = ranger(m, 1024, 10)
    tex = texture(m, W, H)
    tris = triangles(m, 0)
    P = np.array(tris["P"]).reshape(-1, 3)
    c = (P.min(0) + P.max(0)) / 2
    r = np.linalg.norm(P.max(0) - P.min(0)) / 2
    d = norm(np.array([-0.62, 0.55, -0.56]))
    rd = Rendu(256, 256, c + d * r * 6.2, c, fov=19, ss=2)
    A = rd.projeter(P)
    ext = max(np.ptp(A[:, 0]), np.ptp(A[:, 1])) / rd.W
    centre_img = np.array([(A[:, 0].max() + A[:, 0].min()) / 2, (A[:, 1].max() + A[:, 1].min()) / 2])
    # recadrage : la pièce remplit 90 % de l'icône, centrée
    decal = (centre_img - rd.W / 2) / rd.fpx * np.linalg.norm(d * r * 6.2)
    c2 = c + rd.r * decal[0] - rd.u * decal[1]
    rd = Rendu(256, 256, c2 + d * r * 6.2, c2, fov=2 * math.degrees(math.atan(math.tan(math.radians(19) / 2) * ext / 0.9)), ss=2)
    rd.soleil = norm((-0.3, 0.85, -0.45))
    rd.expo = 1.35
    rd.carte_ombre(tris, 1024)
    rd.img[:] = 0
    rd.objets(tris, [tex])
    a = (rd.z < np.inf).astype(float)
    rgba = np.concatenate([np.clip(rd.img, 0, 1) * a[..., None], a[..., None]], -1)
    im = Image.fromarray((rgba * 255).astype(np.uint8), "RGBA").resize((taille * 4, taille * 4), Image.LANCZOS)
    im = im.resize((taille, taille), Image.LANCZOS)
    arr = np.asarray(im).astype(float) / 255
    al = arr[..., 3:4]
    arr[..., :3] = np.where(al > 0.01, arr[..., :3] / np.maximum(al, 1e-3), 0)
    return np.clip(arr, 0, 1)


# =============================================================================================== scènes d'aperçu
def anim_helico(rotor=0.35, queue=0.8, lacet_tourelle=0.0, tangage_canon=0.0, barillet=0.0):
    return {"corps/rotor": rot((0, 1, 0), rotor), "flou/rotor_flou": rot((0, 1, 0), rotor * 0.3),
            "corps/queue_rotor": rot((1, 0, 0), queue), "flou/queue_flou": rot((1, 0, 0), queue * 0.3),
            "corps/tourelle": rot((0, 1, 0), math.radians(lacet_tourelle)),
            "corps/tourelle/canon": rot((1, 0, 0), math.radians(tangage_canon)),
            "corps/tourelle/canon/barillet": rot((0, 0, 1), barillet)}


def scene_vol(h, tex, oeil, cible, lacet=0, tangage=6, roulis=0, sol=-640, anim=None, parts=(), W=1280, Hh=800, fov=36):
    rd = Rendu(W, Hh, oeil, cible, fov=fov)
    tris = triangles(h, 0, anim or anim_helico(), orientation(lacet, tangage, roulis))
    rd.carte_ombre(tris)
    rd.fond(sol)
    rd.objets(tris, [tex])
    if parts:
        rd.taches(list(parts))
    return rd.image()


def scene_epave(e, tex_ep, W=1280, Hh=800):
    rd = Rendu(W, Hh, (-330, 150, -380), (10, 14, 10), fov=38)
    rd.soleil = norm((0.5, 0.7, -0.3))
    places = [(0, (0, 0), 25), (1, (95, 60), -60), (2, (-80, 70), 15), (3, (70, -55), 110), (4, (-75, -40), 70),
              (5, (30, 120), 200)]
    tris = []
    for k, (x, z), lac in places:
        tris.append(triangles(e, 0, None, orientation(lac), np.array([x, 0, z]), sauf=tuple(p for p in e.parts if p != "piece_%d" % k)))
    for i, (x, z, lac) in enumerate(((-30, -70, 12), (-2, -78, 38))):
        tris.append(triangles(e, 0, None, orientation(lac), np.array([x, 0, z]), sauf=tuple(p for p in e.parts if p != "caisse")))
    tris = fusion(*tris)
    rd.carte_ombre(tris)
    rd.fond(0.0, brulure=(10, 20, 150))
    rd.objets(tris, [tex_ep])
    parts = []
    for i, (x, z) in enumerate(((0, 0), (95, 60), (70, -55), (-80, 70))):
        parts += flammes((x, 6, z), 12, 22, graine=i, haut=1.4)
    for i, (x, z) in enumerate(((0, 0), (95, 60), (70, -55))):
        parts += fumee((x, 30, z), (0.25, 1, 0.1), 26, 14, 70, col("#262422"), 0.65, graine=10 + i)
    rd.taches(parts)
    return rd.image()


def planche_pieces(icones):
    larg, haut = 1500, 560
    im = Image.new("RGB", (larg, haut), (22, 24, 26))
    d = ImageDraw.Draw(im)
    f = ImageFont.truetype(str(POLICE), 22)
    f2 = ImageFont.truetype(str(POLICE), 17)
    d.text((30, 22), "Pièces d'hélicoptère récupérables (3 par épave, tirées au sort)", font=f, fill=(235, 232, 220))
    tot = sum(p[3] for p in PIECES)
    for k, (ident, nom, prix, poids, rar) in enumerate(PIECES):
        x = 30 + k * 208
        ic = Image.fromarray((icones[ident] * 255).astype(np.uint8), "RGBA").resize((192, 192), Image.NEAREST)
        fondc = tuple(int(c * 0.18 + 30) for c in COULEUR_RARETE[rar])
        d.rectangle((x, 80, x + 192, 272), fill=fondc)
        d.rectangle((x, 268, x + 192, 272), fill=COULEUR_RARETE[rar])
        im.paste(ic, (x, 80), ic)
        y = 290
        for ligne_ in _couper(nom, 18):
            d.text((x, y), ligne_, font=f2, fill=COULEUR_RARETE[rar] if rar != "commun" else (235, 235, 235))
            y += 22
        d.text((x, y + 4), "%d hérals chez Mila" % prix, font=f2, fill=(230, 200, 110))
        d.text((x, y + 28), "chance : %d %%" % round(100 * poids / tot), font=f2, fill=(170, 175, 170))
        d.text((x, y + 52), rar.replace("_", " "), font=f2, fill=COULEUR_RARETE[rar])
        im.paste(Image.fromarray((icones[ident] * 255).astype(np.uint8), "RGBA"), (x, 470),
                 Image.fromarray((icones[ident] * 255).astype(np.uint8), "RGBA"))
    d.text((30, 535), "En bas : taille réelle de l'icône (64 x 64). Aperçu hors du jeu, rien testé en jeu.", font=f2, fill=(150, 155, 150))
    return im


def _couper(t, n):
    mots, lignes_, cur = t.split(), [], ""
    for w in mots:
        if len(cur) + len(w) + 1 > n and cur:
            lignes_.append(cur)
            cur = w
        else:
            cur = (cur + " " + w).strip()
    return lignes_ + [cur]


# =============================================================================================== principal
def construire():
    h = modele_helico()
    h.partie("_caisse")
    ajouter_caisse(h, "_caisse")
    W, H = ranger(h)
    print("atlas de l'hélicoptère : %d x %d points, %d quads" % (W, H, sum(len(s.quads) for s in h.surfs)))
    tex = texture(h, W, H)
    tex_ep = texture(h, W, H, brule=True)
    e, tailles = modele_epave(h, W, H)
    for k, t in tailles.items():
        print("  épave %s : %.1f x %.1f x %.1f blocs" % (k, t[0] / 16, t[1] / 16, t[2] / 16))
    return h, e, tex, tex_ep, W, H


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--rendus", type=Path, help="dossier des images d'aperçu")
    ap.add_argument("--installer", action="store_true", help="écrit les fichiers dans les assets du mod")
    args = ap.parse_args()
    if not args.rendus and not args.installer:
        ap.error("--rendus DOSSIER et/ou --installer")
    h, e, tex, tex_ep, W, H = construire()
    icones = {p[0]: icone(p[0]) for p in PIECES}
    if args.installer:
        n1 = ecrire_geo(h, W, H, ASSETS / "geo" / "helico.geo", "Hélicoptère de patrouille")
        n2 = ecrire_geo(e, W, H, ASSETS / "geo" / "helico_epave.geo", "Épave de l'hélicoptère et caisse")
        png(tex, ASSETS / "textures" / "entity" / "helico.png")
        # épave : même placement, moitié de la résolution (les u v du .geo sont relatifs à la taille déclarée)
        Image.fromarray((np.clip(tex_ep, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA").resize((W // 2, H // 2), Image.LANCZOS) \
            .save(ASSETS / "textures" / "entity" / "helico_epave.png", optimize=True)
        for ident, *_ in PIECES:
            png(icones[ident], ASSETS / "textures" / "item" / ("helico_" + ident + ".png"))
            (ASSETS / "models" / "item" / ("helico_" + ident + ".json")).write_text(json.dumps(
                {"parent": "item/generated", "textures": {"layer0": "airstrike:item/helico_" + ident}}, indent=1) + "\n",
                encoding="utf-8")
        print("installé : helico.geo (%d quads), helico_epave.geo (%d quads), textures, 7 icônes" % (n1, n2))
    if args.rendus:
        d = args.rendus
        d.mkdir(parents=True, exist_ok=True)
        png(tex, d / "texture_helico.png")
        png(tex_ep, d / "texture_epave.png")
        anim = anim_helico(0.35, 0.8, 25, 18, 0.3)
        legende(scene_vol(h, tex, (-215, 80, -255), (0, 30, 10), lacet=0, tangage=6, roulis=3, anim=anim),
                "Hélicoptère de patrouille - vol, trois quarts avant", "aperçu hors du jeu").save(d / "1_vol_trois_quarts.png")
        legende(scene_vol(h, tex, (0, 20, -470), (0, 36, 0), tangage=4, anim=anim, fov=34),
                "Vol, de face", "tourelle du menton tournée vers la cible").save(d / "2_vol_face.png")
        legende(scene_vol(h, tex, (470, 34, 18), (0, 36, 18), tangage=5, anim=anim_helico(0.6, 0.2), fov=36),
                "Vol, de côté", "rotor principal (4 pales), rotor de queue, paniers de roquettes").save(d / "3_vol_cote.png")
        abime = []
        for sx in (-1, 1):
            p = orientation(-20, 6, 9) @ np.array([18 * sx, 58.5, 34])
            abime += fumee(p, (0.15, 0.25, 1.0), 30, 6, 46, col("#1c1a18"), 0.8, graine=3 + sx, montee=0.4)
            abime += flammes(p, 2.5, 6, graine=5 + sx, haut=0.4)
        legende(scene_vol(h, tex, (-260, 70, 300), (0, 40, 30), lacet=-20, tangage=6, roulis=9, anim=anim, parts=abime),
                "Très abîmé : fumée noire épaisse (plus il perd de vie, plus elle est haute)", "flammes à la fin").save(d / "4_vol_abime.png")
        legende(scene_vol(h, tex, (-95, 2, -175), (0, 8, -72), tangage=4, anim=anim_helico(0.35, 0.8, 30, -18, 0.5), fov=30),
                "Tourelle du menton : mitrailleuse rotative à 6 canons", "pivote (lacet), se baisse (tangage), barillet qui tourne").save(d / "5_tourelle.png")
        legende(scene_epave(e, tex_ep), "Épave au sol : 6 morceaux brûlés, flammes, 2 caisses T6 (+ coffre T7 de HeraliZ Loot)",
                "3 morceaux fouillables par épave (8 s), caisses verrouillées tant que ça brûle").save(d / "6_epave.png")
        planche_pieces(icones).save(d / "7_pieces.png")
        # morceaux de l'épave un par un
        ims = []
        for nom in ["piece_%d" % k for k in range(6)] + ["caisse"]:
            tris = triangles(e, 0, None, orientation(30), sauf=tuple(p for p in e.parts if p != nom))
            P = np.array(tris["P"]).reshape(-1, 3)
            c = (P.min(0) + P.max(0)) / 2
            r = np.linalg.norm(P.max(0) - P.min(0)) / 2
            rd = Rendu(420, 300, c + norm(np.array([-0.7, 0.5, -0.6])) * r * 3.1, c, fov=36)
            rd.carte_ombre(tris)
            rd.fond(0.0)
            rd.objets(tris, [tex_ep])
            ims.append(legende(rd.image(), PIECES_EPAVE[int(nom[-1])] if nom != "caisse" else "caisse T6"))
        out = Image.new("RGB", (420 * 4, 300 * 2), (22, 24, 26))
        for i, im in enumerate(ims):
            out.paste(im, ((i % 4) * 420, (i // 4) * 300))
        out.save(d / "8_morceaux_epave.png")
        print("rendus dans", d)


if __name__ == "__main__":
    main()
