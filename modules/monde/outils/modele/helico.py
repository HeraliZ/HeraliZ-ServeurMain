#!/usr/bin/env python3
"""Hélicoptère militaire de l'événement « hélico » : modèle en vol et épave brûlée, avec leurs textures.

    python modules/monde/outils/modele/helico.py                 écrit les modèles et les textures du mod
    python modules/monde/outils/modele/helico.py <dossier>       les écrit ailleurs (aperçu avant validation)

Besoin de Pillow et numpy (pip install pillow numpy). Rendus hors du jeu : rendu_geo.py, à côté de ce fichier.

Fichiers écrits (assets d'airstrike) : geo/helicopter.geo, textures/entity/helicopter.png, geo/helicopter_wreck.geo,
textures/entity/helicopter_wreck.png. Jamais retouchés à la main : on modifie ce script puis on le relance.

Repère « naturel » du modèle : blocs (1 bloc = 1 m), x vers la droite, y vers le haut, z vers l'arrière (nez vers -z),
origine = sol sous le centre de la cabine (bas des roues), comme la position de l'entité. Le fichier .geo est en
seizièmes de bloc et dans le repère des modèles du jeu (x et y retournés) : la conversion est faite à l'écriture.

Surfaces lisses (« quad » à 4 sommets avec leur normale, éclairage arrondi) : fuselage et poutre de queue d'une seule
pièce (sections en super-ellipse), capot et moteurs, pales en profil d'aile, roues, miniguns de porte (canons qui
tournent), dérive, stabilisateur, rotor de queue. Vitres posées par-dessus le fuselage (passe « glass »), feux de
navigation et tableau de bord éclairés (passes « emissive »), disques flous des rotors (passe « glass »).

Parties lues par le rendu (HelicopterRenderer) : body, glass, rotor (au moyeu, tourne sur y), rotor_blur, tail_rotor
(au moyeu de queue, tourne sur x), tail_blur, gun_l et gun_r (au pivot de l'arme, sous-partie barrels qui tourne sur z),
emit_nav_l, emit_nav_r, emit_strobe, emit_cockpit. Points : rotor_hub, tail_hub, muzzle_l, muzzle_r, engine_l,
engine_r, cabin_center ; épave : fire_0 à fire_3.
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ICI = Path(__file__).resolve().parent
MOD = ICI.parents[1]
ASSETS = MOD / "src" / "client" / "resources" / "assets" / "airstrike"
TEX = 1024
PI = math.pi

# ------------------------------------------------------------------ cotes (blocs, repère naturel)
# fuselage et poutre de queue : (z, haut, bas, demi-largeur, exposant de la super-ellipse)
SECTIONS = [
    (-4.50, 1.66, 1.30, 0.06, 2.0),
    (-4.40, 1.90, 1.06, 0.40, 2.3),
    (-4.18, 2.08, 0.88, 0.72, 2.8),
    (-3.88, 2.28, 0.75, 0.96, 3.3),
    (-3.45, 2.55, 0.66, 1.11, 3.9),
    (-2.95, 2.80, 0.62, 1.19, 4.4),
    (-2.30, 2.90, 0.62, 1.22, 4.8),
    (-0.50, 2.92, 0.62, 1.22, 4.8),
    (0.80, 2.92, 0.66, 1.20, 4.5),
    (1.60, 2.90, 0.95, 1.02, 3.6),
    (2.30, 2.86, 1.45, 0.72, 3.0),
    (2.90, 2.82, 1.70, 0.56, 2.6),
    (4.50, 2.76, 1.86, 0.44, 2.4),
    (6.50, 2.72, 1.98, 0.34, 2.3),
    (7.80, 2.72, 2.04, 0.28, 2.2),
    (8.42, 2.74, 2.08, 0.22, 2.2),
]
Z_NEZ, Z_QUEUE = SECTIONS[0][0], SECTIONS[-1][0]
CAPOT = [  # (z, haut, demi-largeur) ; bas à 2,70 (dans le fuselage)
    (-1.95, 2.88, 0.30), (-1.60, 3.22, 0.68), (-0.80, 3.40, 0.82), (0.60, 3.40, 0.82), (1.60, 3.28, 0.72),
    (2.40, 3.00, 0.44), (2.95, 2.84, 0.18),
]
CAPOT_BAS = 2.70
MOTEURS = dict(x=0.80, y=3.12, r=0.30, z0=-0.95, z1=1.55)
MAT = dict(z=-0.35, y0=3.35, y1=3.95, r=0.17)
MOYEU = (0.0, 4.06, -0.35)
R_PALE, CORDE, EPAISSEUR, CONE = 6.45, 0.52, 0.075, math.radians(2.0)
MOYEU_QUEUE = (0.36, 3.72, 8.62)
R_PALE_QUEUE, CORDE_QUEUE = 1.40, 0.21
DERIVE = dict(z_racine=(7.52, 8.52), y_racine=2.62, z_bout=(8.30, 8.84), y_bout=4.30, ep_racine=0.15, ep_bout=0.08)
STAB = dict(y=2.24, z_racine=(7.92, 8.66), z_bout=(8.08, 8.62), envergure=2.10, ep=0.08)
ROUES = [(-1.46, 0.32, -0.78, 0.32), (1.46, 0.32, -0.78, 0.32), (0.0, 0.20, 7.10, 0.20)]
ARMES = {"l": (-1.36, 1.98, -1.43, 1), "r": (1.36, 1.98, -1.43, -1)}   # pivot, côté
LACET_ARME, TANGAGE_ARME = math.radians(32), math.radians(-10)

# vitres (les deux côtés) : z0, z1 et soit la hauteur y0, y1 sur le flanc, soit « haut » : pare-brise qui passe par-dessus
# le nez depuis yc + haut x demi-hauteur, en deux vitres séparées par un montant
VITRES = [
    dict(nom="pare_brise", z=(-4.05, -3.00), haut=0.12),
    dict(nom="porte", z=(-2.92, -1.98), y=(1.62, 2.66)),
    dict(nom="cabine", z=(-1.80, -1.06), y=(1.58, 2.50)),
    dict(nom="cabine", z=(0.02, 0.86), y=(1.58, 2.50)),
    dict(nom="cabine", z=(1.12, 1.46), y=(1.80, 2.40)),
]
MONTANT = 0.05            # largeur du montant central du pare-brise (angle d'arc)
PORTES = [dict(z=(-2.98, -1.92), y=(0.95, 2.74)), dict(z=(-0.48, 1.08), y=(0.78, 2.72))]

# ------------------------------------------------------------------ régions de la texture (pixels)
R = {
    "fuselage": (0, 0, 512, 1024),
    "capot": (512, 0, 768, 256),
    "moteur": (768, 0, 1024, 160),
    "entree": (768, 160, 832, 224),
    "sortie": (832, 160, 896, 224),
    "pale": (512, 256, 560, 768),
    "pale_queue": (560, 256, 584, 384),
    "derive": (584, 256, 712, 416),
    "stab": (712, 256, 840, 336),
    "pneu": (840, 256, 968, 288),
    "jante": (968, 256, 1000, 288),
    "peint": (584, 416, 712, 480),
    "flou": (768, 512, 1024, 768),
    "flou_queue": (896, 896, 1024, 1024),
    "metal": (512, 768, 640, 896),
    "metal_clair": (640, 768, 768, 896),
    "moyeu": (768, 768, 896, 896),
    "vitre": (896, 768, 1024, 832),
    "rouge": (896, 832, 912, 848),
    "vert": (912, 832, 928, 848),
    "blanc": (928, 832, 944, 848),
    "tableau": (944, 832, 976, 848),
    "sombre": (976, 832, 992, 848),
}


def u_phi(phi):
    u0, _, u1, _ = R["fuselage"]
    return u0 + (u1 - u0) * phi / (2 * PI)


def v_z(z):
    _, v0, _, v1 = R["fuselage"]
    return v0 + (v1 - v0) * (z - Z_NEZ) / (Z_QUEUE - Z_NEZ)


# ------------------------------------------------------------------ modèle
def nrm(v):
    v = np.asarray(v, float)
    n = np.linalg.norm(v)
    return v / n if n > 1e-9 else v


def rot_xyz(rx, ry, rz):
    """Matrice de rotationZYX(rz, ry, rx) de JOML (comme GeoPart) : d'abord x, puis y, puis z."""
    cx, sx, cy, sy, cz, sz = math.cos(rx), math.sin(rx), math.cos(ry), math.sin(ry), math.cos(rz), math.sin(rz)
    mx = np.array([[1, 0, 0], [0, cx, -sx], [0, sx, cx]])
    my = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    mz = np.array([[cz, -sz, 0], [sz, cz, 0], [0, 0, 1]])
    return mz @ my @ mx


class Modele:
    def __init__(self):
        self.ordre = []          # chemins des parties, dans l'ordre
        self.pivots = {}         # chemin -> pivot absolu (repère naturel, blocs)
        self.rots = {}           # chemin -> (rx, ry, rz) repère naturel
        self.quads = {}
        self.passes = {}
        self.points = {}

    def partie(self, chemin, pivot=(0, 0, 0), passe=None, rot=(0, 0, 0)):
        if chemin not in self.pivots:
            self.ordre.append(chemin)
            self.pivots[chemin] = np.asarray(pivot, float)
            self.rots[chemin] = rot
            self.quads[chemin] = []
            if passe:
                self.passes[chemin] = passe
        return chemin

    def quad(self, chemin, sommets, local=False):
        """sommets : 4 x (x, y, z, u, v, nx, ny, nz) ; position absolue (ou locale si local=True). Le sens des sommets
        est remis pour que la face soit vue de l'extérieur (sens trigonométrique vu du côté de la normale)."""
        s = [list(map(float, p)) for p in sommets]
        if not local:
            pv = self.pivots[chemin]
            for p in s:
                p[0] -= pv[0]
                p[1] -= pv[1]
                p[2] -= pv[2]
        a, b, c, d = (np.array(p[:3]) for p in s)
        g = np.cross(b - a, c - a) + np.cross(c - a, d - a)
        moy = sum(np.array(p[5:8]) for p in s)
        if np.dot(g, moy) < 0:
            s = s[::-1]
        self.quads[chemin].append(s)

    def ecrire(self, chemin_geo):
        L = ["# Airstrike geometry - GENERATED by modules/monde/outils/modele/helico.py, do not edit by hand",
             "texture %d %d" % (TEX, TEX)]
        for p in self.ordre:
            if p in self.passes:
                L.append("pass %s %s" % (p, self.passes[p]))
        for n, (x, y, z) in self.points.items():
            L.append("point %s %.4f %.4f %.4f" % (n, x * 16, y * 16, z * 16))
        for p in self.ordre:
            parent = p.rsplit("/", 1)[0] if "/" in p else None
            pv = self.pivots[p] - (self.pivots[parent] if parent else 0)
            rx, ry, rz = self.rots[p]
            # repère du jeu : x et y retournés (rotation d'un demi-tour sur z) ; les rotations sur x et y changent de signe
            L.append("part %s %.4f %.4f %.4f %.5f %.5f %.5f" % (p, -pv[0] * 16, -pv[1] * 16, pv[2] * 16, -rx, -ry, rz))
            for q in self.quads[p]:
                vals = []
                for (x, y, z, u, v, nx, ny, nz) in q:
                    vals += [-x * 16, -y * 16, z * 16, u, v, -nx, -ny, nz]
                L.append("quad " + " ".join("%.4f" % a for a in vals))
        Path(chemin_geo).write_text("\n".join(L) + "\n", encoding="utf-8")
        return sum(len(q) for q in self.quads.values())


def normales_grille(P, couture=False):
    """Normales lissées d'une grille (gradients). couture : la dernière colonne est la première (surface fermée)."""
    P = np.asarray(P, float)
    if couture:
        Q = P[:, :-1]
        dj = np.roll(Q, -1, axis=1) - np.roll(Q, 1, axis=1)
        dj = np.concatenate([dj, dj[:, :1]], axis=1)
    else:
        dj = np.gradient(P, axis=1)
    di = np.gradient(P, axis=0)
    N = np.cross(di, dj)
    ln = np.linalg.norm(N, axis=2, keepdims=True)
    return np.where(ln > 1e-9, N / np.maximum(ln, 1e-9), 0)


def orienter(N, P, dehors):
    """Retourne les normales qui ne vont pas vers « dehors » (fonction point -> direction vers l'extérieur)."""
    N = N.copy()
    I, J = P.shape[:2]
    for i in range(I):
        for j in range(J):
            if np.dot(N[i, j], dehors(P[i, j])) < 0:
                N[i, j] = -N[i, j]
    return N


def grille(m, chemin, P, region_uv, dehors=None, normales=None, couture=False):
    """Surface en grille P[i][j] (I x J x 3) ; region_uv : UV[i][j] (I x J x 2). Normales données, sinon lissées et
    orientées vers « dehors »."""
    P = np.asarray(P, float)
    UV = np.asarray(region_uv, float)
    I, J = P.shape[:2]
    if normales is None:
        N = normales_grille(P, couture)
        if dehors is not None:
            N = orienter(N, P, dehors)
    else:
        N = np.asarray(normales, float)
    for i in range(I - 1):
        for j in range(J - 1):
            idx = [(i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)]
            m.quad(chemin, [list(P[a, b]) + list(UV[a, b]) + list(N[a, b]) for (a, b) in idx])


def sp(t, n):
    return math.copysign(abs(t) ** (2.0 / n), t)


def interp(zs, vals, zq):
    """Interpolation cubique (Hermite, pentes de Catmull-Rom) de vals(zs) en zq."""
    zs = np.asarray(zs, float)
    vals = np.asarray(vals, float)
    k = int(np.clip(np.searchsorted(zs, zq) - 1, 0, len(zs) - 2))
    z0, z1 = zs[k], zs[k + 1]
    t = (zq - z0) / (z1 - z0)
    h = z1 - z0

    def pente(i):
        if i <= 0:
            return (vals[1] - vals[0]) / (zs[1] - zs[0])
        if i >= len(zs) - 1:
            return (vals[-1] - vals[-2]) / (zs[-1] - zs[-2])
        return (vals[i + 1] - vals[i - 1]) / (zs[i + 1] - zs[i - 1])

    m0, m1 = pente(k) * h, pente(k + 1) * h
    t2, t3 = t * t, t * t * t
    return (2 * t3 - 3 * t2 + 1) * vals[k] + (t3 - 2 * t2 + t) * m0 + (-2 * t3 + 3 * t2) * vals[k + 1] + (t3 - t2) * m1


def section(z):
    cols = list(zip(*SECTIONS))
    zs = cols[0]
    haut, bas, w, n = (interp(zs, cols[i], z) for i in (1, 2, 3, 4))
    return haut, bas, max(w, 0.02), max(n, 2.0)


_ANNEAUX = {}


def anneau(z):
    """Section à z, échantillonnée finement : angle géométrique phi, « angle d'arc » a (abscisse curviligne ramenée à
    0..2 pi : la texture est répartie à densité égale tout autour), x, y. a = 0 dessous, pi / 2 flanc droit, pi dessus."""
    k = round(float(z), 5)
    r = _ANNEAUX.get(k)
    if r is None:
        haut, bas, w, n = section(z)
        yc, h = (haut + bas) / 2, (haut - bas) / 2
        phi = np.linspace(0, 2 * PI, 2881)
        s, c = np.sin(phi), -np.cos(phi)
        x = w * np.sign(s) * np.abs(s) ** (2.0 / n)
        y = yc + h * np.sign(c) * np.abs(c) ** (2.0 / n)
        d = np.hypot(np.diff(x), np.diff(y))
        a = np.concatenate([[0.0], np.cumsum(d)])
        a = a / a[-1] * 2 * PI
        r = _ANNEAUX[k] = (phi, a, x, y, yc, h)
    return r


def point_fuselage(z, a, gonfle=0.0):
    """Point du fuselage à z, à l'angle d'arc a ; gonfle : décalé vers l'extérieur (vitres)."""
    phi_t, a_t, _, _, _, _ = anneau(z)
    phi = float(np.interp(a, a_t, phi_t))
    haut, bas, w, n = section(z)
    yc, h = (haut + bas) / 2, (haut - bas) / 2
    x = (w + gonfle) * sp(math.sin(phi), n)
    y = yc + (h + gonfle) * sp(-math.cos(phi), n)
    return [x, y, z]


def a_de_y(z, y):
    """Angle d'arc du flanc droit à la hauteur y (sur le flanc gauche : 2 pi - a)."""
    _, a_t, _, y_t, _, _ = anneau(z)
    moitie = len(a_t) // 2 + 1
    return float(np.interp(y, y_t[:moitie], a_t[:moitie]))


def plages_vitre(v, z):
    """Plages d'angle d'arc (a0, a1) de la vitre v à z : une par côté (deux vitres pour le pare-brise)."""
    if "haut" in v:
        _, _, _, _, yc, h = anneau(z)
        a0 = a_de_y(z, yc + v["haut"] * h)
        return [(a0, PI - MONTANT / 2), (PI + MONTANT / 2, 2 * PI - a0)]
    a0, a1 = a_de_y(z, v["y"][0]), a_de_y(z, v["y"][1])
    return [(a0, a1), (2 * PI - a1, 2 * PI - a0)]


def zs_fuselage(z0=Z_NEZ, z1=Z_QUEUE):
    zs, z = [], z0
    while z < z1 - 1e-6:
        zs.append(z)
        pas = 0.06 if z < -4.25 else 0.12 if z < -2.8 else 0.30 if z < 2.9 else 0.42
        z += pas
    zs.append(z1)
    # bords des vitres et des portes : sections exactes (contours nets)
    for v in VITRES + PORTES:
        zs += list(v["z"])
    return sorted(set(round(a, 4) for a in zs if z0 <= a <= z1))


def dehors_fuselage(p):
    haut, bas, _, _ = section(p[2])
    return [p[0], p[1] - (haut + bas) / 2, 0.0]


def fuselage(m, deform=None, rot_deform=None, z1=Z_QUEUE, chemin="body"):
    """Fuselage et poutre d'une pièce. deform(p, z, phi) : déformation de l'épave ; rot_deform : sa rotation d'ensemble
    (les normales de la forme d'origine, tournées, servent à orienter celles de la forme déformée)."""
    M = 60
    zs = zs_fuselage(Z_NEZ, z1)
    P0, P, UV = [], [], []
    for z in zs:
        l0, ligne, luv = [], [], []
        for j in range(M + 1):
            phi = 2 * PI * j / M
            p = point_fuselage(z, phi)
            l0.append(p)
            ligne.append(deform(p, z, phi) if deform else p)
            luv.append([u_phi(phi), v_z(z)])
        P0.append(l0)
        P.append(ligne)
        UV.append(luv)
    P0, P, UV = np.array(P0), np.array(P), np.array(UV)
    # couture recousue : la dernière colonne (phi = 2 pi) reprend les positions de la première
    P0[:, -1] = P0[:, 0]
    P[:, -1] = P[:, 0]
    N0 = orienter(normales_grille(P0, True), P0, dehors_fuselage)
    if deform:
        N = normales_grille(P, True)
        Rd = rot_deform if rot_deform is not None else np.eye(3)
        ref = N0 @ Rd.T
        N = np.where((np.sum(N * ref, axis=2) < 0)[..., None], -N, N)
    else:
        N = N0
    grille(m, chemin, P, UV, normales=N)
    # pointe du nez : petit cône fermé
    tip = np.array(point_fuselage(Z_NEZ - 0.05, 0)) * 0 + [0, (SECTIONS[0][1] + SECTIONS[0][2]) / 2, Z_NEZ - 0.07]
    if deform:
        tip = np.array(deform(list(tip), Z_NEZ, 0.0))
    for j in range(M):
        a, b = P[0, j], P[0, j + 1]
        uv = [u_phi(2 * PI * (j + 0.5) / M), v_z(Z_NEZ)]
        m.quad(chemin, [list(tip) + uv + [0, 0, -1], list(a) + [u_phi(2 * PI * j / M), v_z(Z_NEZ)] + list(nrm(a - tip + [0, 0, -0.3])),
                        list(b) + [u_phi(2 * PI * (j + 1) / M), v_z(Z_NEZ)] + list(nrm(b - tip + [0, 0, -0.3])),
                        list(tip) + uv + [0, 0, -1]])
    # bout de la poutre : disque
    fin = P[-1]
    c = fin[:-1].mean(axis=0)
    for j in range(M):
        m.quad(chemin, [list(c) + [u_phi(PI), v_z(z1)] + [0, 0, 1], list(fin[j]) + [u_phi(2 * PI * j / M), v_z(z1)] + [0, 0, 1],
                        list(fin[j + 1]) + [u_phi(2 * PI * (j + 1) / M), v_z(z1)] + [0, 0, 1], list(c) + [u_phi(PI), v_z(z1)] + [0, 0, 1]])
    return P


def vitres(m, gonfle=0.018):
    u0, v0, u1, v1 = R["vitre"]
    for v in VITRES:
        z0, z1 = v["z"]
        nz, na = 8, 10
        for k in range(2):
            P, UV = [], []
            for i in range(nz + 1):
                z = z0 + (z1 - z0) * i / nz
                a0, a1 = plages_vitre(v, z)[k]
                ligne, luv = [], []
                for j in range(na + 1):
                    ligne.append(point_fuselage(z, a0 + (a1 - a0) * j / na, gonfle))
                    luv.append([u0 + (u1 - u0) * j / na, v0 + (v1 - v0) * i / nz])
                P.append(ligne)
                UV.append(luv)
            grille(m, "glass", P, UV, dehors_fuselage)


def capot(m, ecrase=0.0):
    zs = [c[0] for c in CAPOT]
    M = 32
    Z = np.linspace(zs[0], zs[-1], 22)
    u0, v0, u1, v1 = R["capot"]
    P, UV = [], []
    for z in Z:
        haut = interp(zs, [c[1] for c in CAPOT], z) - ecrase
        w = max(interp(zs, [c[2] for c in CAPOT], z), 0.05)
        yc, h = (haut + CAPOT_BAS) / 2, (haut - CAPOT_BAS) / 2
        ligne, luv = [], []
        for j in range(M + 1):
            phi = PI / 2 + PI * j / M      # moitié haute seulement (le bas est dans le fuselage)
            ligne.append([w * sp(math.sin(phi), 3.0), yc + h * sp(-math.cos(phi), 3.0), z])
            luv.append([u0 + (u1 - u0) * j / M, v0 + (v1 - v0) * (z - zs[0]) / (zs[-1] - zs[0])])
        P.append(ligne)
        UV.append(luv)

    def dehors(p):
        return [p[0], p[1] - CAPOT_BAS, 0.0]

    grille(m, "body", P, UV, dehors)


def cylindre(m, chemin, c, axe, t0, t1, r0, r1, n, region, local=False, couvercles=(None, None), raccourci=None):
    """Cylindre (ou cône) d'axe « axe » passant par c, de t0 à t1, rayons r0 -> r1 ; couvercles : régions des disques."""
    a = nrm(axe)
    ref = np.array([0, 1.0, 0]) if abs(a[1]) < 0.9 else np.array([1.0, 0, 0])
    e1 = nrm(ref - a * np.dot(ref, a))
    e2 = np.cross(a, e1)
    c = np.asarray(c, float)
    u0, v0, u1, v1 = region
    pente = (r1 - r0) / (t1 - t0)
    for i in range(n):
        q = []
        for (k, t, r) in ((i, t0, r0), (i + 1, t0, r0), (i + 1, t1, r1), (i, t1, r1)):
            th = 2 * PI * k / n
            rad = math.cos(th) * e1 + math.sin(th) * e2
            p = c + a * t + r * rad
            q.append(list(p) + [u0 + (u1 - u0) * k / n, v0 + (v1 - v0) * (t - t0) / (t1 - t0)] + list(nrm(rad - pente * a)))
        m.quad(chemin, q, local)
    for (t, r, reg, sens) in ((t0, r0, couvercles[0], -1), (t1, r1, couvercles[1], 1)):
        if reg is None or r <= 0:
            continue
        cu, cv, ru, rv = (reg[0] + reg[2]) / 2, (reg[1] + reg[3]) / 2, (reg[2] - reg[0]) / 2, (reg[3] - reg[1]) / 2
        centre = c + a * t
        for i in range(n):
            q = [list(centre) + [cu, cv] + list(a * sens)]
            for k in (i, i + 1):
                th = 2 * PI * k / n
                p = centre + r * (math.cos(th) * e1 + math.sin(th) * e2)
                q.append(list(p) + [cu + ru * math.cos(th), cv + rv * math.sin(th)] + list(a * sens))
            q.append(q[0])
            m.quad(chemin, q, local)


def boite(m, chemin, c, demi, region, rot=None, local=False):
    """Pavé de centre c, demi-tailles demi (x, y, z), même région pour toutes les faces, rotation optionnelle (3 x 3)."""
    c = np.asarray(c, float)
    hx, hy, hz = demi
    u0, v0, u1, v1 = region
    M = rot if rot is not None else np.eye(3)
    faces = [((1, 0, 0), (0, 0, 1), (0, 1, 0)), ((-1, 0, 0), (0, 0, -1), (0, 1, 0)), ((0, 1, 0), (1, 0, 0), (0, 0, 1)),
             ((0, -1, 0), (1, 0, 0), (0, 0, -1)), ((0, 0, 1), (-1, 0, 0), (0, 1, 0)), ((0, 0, -1), (1, 0, 0), (0, 1, 0))]
    for (n, a, b) in faces:
        n, a, b = np.array(n, float), np.array(a, float), np.array(b, float)
        d = np.array([hx, hy, hz])
        cn = n * d
        da, db = a * d, b * d
        pts = [cn - da - db, cn + da - db, cn + da + db, cn - da + db]
        uvs = [(u0, v1), (u1, v1), (u1, v0), (u0, v0)]
        q = [list(c + M @ p) + list(uv) + list(M @ n) for p, uv in zip(pts, uvs)]
        m.quad(chemin, q, local)


def moteurs(m, ecrase=0.0):
    E = MOTEURS
    for s in (1, -1):
        c = [s * E["x"], E["y"] - ecrase, 0.0]
        cylindre(m, "body", c, [0, 0, 1], E["z0"], E["z1"], E["r"], E["r"] * 0.92, 28, R["moteur"],
                 couvercles=(R["entree"], None))
        # sortie des gaz tournée vers l'extérieur et le haut (suppresseur infrarouge)
        d = nrm([s * 0.62, 0.25, 0.75])
        base = np.array([s * E["x"], E["y"] - ecrase, E["z1"] - 0.02])
        cylindre(m, "body", base, d, 0.0, 0.55, E["r"] * 0.86, E["r"] * 0.78, 24, R["moteur"], couvercles=(None, R["sortie"]))
        bout = base + d * 0.6
        m.points["engine_" + ("r" if s == 1 else "l")] = tuple(bout)


def mat_moyeu(m):
    cylindre(m, "body", [0, 0, MAT["z"]], [0, 1, 0], MAT["y0"], MAT["y1"], MAT["r"] * 1.25, MAT["r"], 20, R["metal"])
    # plateau cyclique et biellettes
    cylindre(m, "body", [0, 0, MAT["z"]], [0, 1, 0], 3.55, 3.63, 0.34, 0.34, 24, R["metal_clair"], couvercles=(R["moyeu"], R["moyeu"]))
    for k in range(4):
        a = PI / 4 + k * PI / 2
        c = [0.24 * math.cos(a), 3.80, MAT["z"] + 0.24 * math.sin(a)]
        cylindre(m, "body", c, [0, 1, 0], -0.17, 0.17, 0.025, 0.025, 6, R["metal_clair"])


def profil_pale(c, corde, ep):
    """Profil d'aile (8 points) dans le plan (corde, épaisseur), bord d'attaque vers -corde."""
    pts = [(-0.50, 0.0), (-0.42, 0.42), (-0.20, 0.50), (0.15, 0.40), (0.50, 0.04), (0.50, -0.04), (0.10, -0.16), (-0.35, -0.22)]
    return [(c + x * corde, y * ep) for (x, y) in pts]


def pale(m, chemin, centre, angle, r0, r1, corde, ep, cone, region, plan="xz", affaisse=None, bout_jaune=True):
    """Une pale de r0 à r1 dans le plan du rotor. plan « xz » : rotor principal (axe y) ; « yz » : rotor de queue (axe x)."""
    centre = np.asarray(centre, float)
    st = np.linspace(r0, r1, 12)
    u0, v0, u1, v1 = region
    P, UV = [], []
    for r in st:
        cr = corde * (0.62 if r < r0 + 0.35 else 1.0)
        prof = profil_pale(0.0, cr, ep * (1.0 - 0.35 * (r - r0) / (r1 - r0)))
        ligne, luv = [], []
        for j, (cx, cy) in enumerate(prof + [prof[0]]):
            # repère de la pale : radial e_r, corde e_t (sens de rotation), épaisseur e_n
            if plan == "xz":
                er = np.array([math.cos(angle), 0, math.sin(angle)])
                et = np.array([-math.sin(angle), 0, math.cos(angle)])
                en = np.array([0, 1.0, 0])
                chute = (r * math.sin(cone)) + (affaisse(r) if affaisse else 0.0)
                p = centre + er * r + et * cx + en * (cy + chute)
            else:
                er = np.array([0, math.cos(angle), math.sin(angle)])
                et = np.array([0, -math.sin(angle), math.cos(angle)])
                en = np.array([1.0, 0, 0])
                p = centre + er * r + et * cx + en * cy
            ligne.append(list(p))
            luv.append([u0 + (u1 - u0) * j / len(prof), v0 + (v1 - v0) * (r - r0) / (r1 - r0)])
        P.append(ligne)
        UV.append(luv)

    if plan == "xz":
        er = np.array([math.cos(angle), 0, math.sin(angle)])
        en = np.array([0, 1.0, 0])
    else:
        er = np.array([0, math.cos(angle), math.sin(angle)])
        en = np.array([1.0, 0, 0])

    def dehors(p):
        # depuis l'axe de la pale (rayon r, hauteur du cône et de l'affaissement) vers le point
        q = np.asarray(p) - centre
        r = float(np.dot(q, er))
        axe = er * r
        if plan == "xz":
            axe = axe + en * (r * math.sin(cone) + (affaisse(r) if affaisse else 0.0))
        return q - axe

    P = np.array(P)
    grille(m, chemin, P, UV, dehors)
    # bouchon du bout
    fin = P[-1, :-1]
    cfin = fin.mean(axis=0)
    for j in range(len(fin)):
        a, b = fin[j], fin[(j + 1) % len(fin)]
        er = nrm(cfin - centre)
        m.quad(chemin, [list(cfin) + [u0, v1 - 1] + list(er), list(a) + [u0, v1 - 1] + list(er), list(b) + [u1, v1 - 1] + list(er),
                        list(cfin) + [u1, v1 - 1] + list(er)])


def disque_flou(m, chemin, centre, axe, r_in, r_out, region, n=48):
    """Disque du rotor en mouvement (deux faces), texture polaire."""
    a = nrm(axe)
    ref = np.array([1.0, 0, 0]) if abs(a[0]) < 0.9 else np.array([0, 0, 1.0])
    e1 = nrm(ref - a * np.dot(ref, a))
    e2 = np.cross(a, e1)
    c = np.asarray(centre, float)
    u0, v0, u1, v1 = region
    cu, cv, ru, rv = (u0 + u1) / 2, (v0 + v1) / 2, (u1 - u0) / 2 - 1, (v1 - v0) / 2 - 1
    for sens in (1, -1):
        for i in range(n):
            q = []
            for (k, r) in ((i, r_out), (i + 1, r_out), (i + 1, r_in), (i, r_in)):
                th = 2 * PI * k / n
                p = c + r * (math.cos(th) * e1 + math.sin(th) * e2)
                q.append(list(p) + [cu + ru * r / r_out * math.cos(th), cv + rv * r / r_out * math.sin(th)] + list(a * sens))
            m.quad(chemin, q)


def plaque(m, chemin, contour_haut, contour_bas, region, mapping):
    """Plaque fine : deux faces (contours à 4 points, même ordre) et la tranche. mapping(p) -> (u, v)."""
    H = [np.asarray(p, float) for p in contour_haut]
    B = [np.asarray(p, float) for p in contour_bas]
    nh = nrm(np.cross(H[1] - H[0], H[2] - H[0]))
    centre = (sum(H) + sum(B)) / 8
    if np.dot(nh, H[0] - centre) < 0:
        nh = -nh
    m.quad(chemin, [list(p) + list(mapping(p)) + list(nh) for p in H])
    m.quad(chemin, [list(p) + list(mapping(p)) + list(-nh) for p in B])
    for i in range(4):
        a, b = H[i], H[(i + 1) % 4]
        c, d = B[(i + 1) % 4], B[i]
        n = nrm(((a + b + c + d) / 4) - centre)
        m.quad(chemin, [list(a) + list(mapping(a)) + list(n), list(b) + list(mapping(b)) + list(n),
                        list(c) + list(mapping(c)) + list(n), list(d) + list(mapping(d)) + list(n)])


def derive_stab(m, chemin="body", transf=None):
    D = DERIVE
    T = transf or (lambda p: np.asarray(p, float))
    u0, v0, u1, v1 = R["derive"]

    def map_d(p):
        return (u0 + (u1 - u0) * (p[2] - 7.4) / 1.6, v0 + (v1 - v0) * (4.45 - p[1]) / 1.95)

    zr, zb = D["z_racine"], D["z_bout"]
    er, eb = D["ep_racine"] / 2, D["ep_bout"] / 2
    haut = [(er, D["y_racine"], zr[0]), (eb, D["y_bout"], zb[0]), (eb, D["y_bout"], zb[1]), (er, D["y_racine"], zr[1])]
    bas = [(-x, y, z) for (x, y, z) in haut]
    plaque(m, chemin, [T(p) for p in haut], [T(p) for p in bas], R["derive"], lambda p: map_d(p))
    S = STAB
    su0, sv0, su1, sv1 = R["stab"]

    def map_s(p):
        return (su0 + (su1 - su0) * (p[0] + S["envergure"]) / (2 * S["envergure"]), sv0 + (sv1 - sv0) * (p[2] - 7.9) / 0.8)

    e = S["ep"] / 2
    for s in (1, -1):
        dessus = [(s * 0.15, S["y"] + e, S["z_racine"][0]), (s * S["envergure"], S["y"] + e, S["z_bout"][0]),
                  (s * S["envergure"], S["y"] + e, S["z_bout"][1]), (s * 0.15, S["y"] + e, S["z_racine"][1])]
        dessous = [(x, y - 2 * e, z) for (x, y, z) in dessus]
        plaque(m, chemin, [T(p) for p in dessus], [T(p) for p in dessous], R["stab"], lambda p: map_s(p))


def roues(m, chemin="body", lesquelles=(0, 1, 2), baisse=0.0):
    for k in lesquelles:
        x, y, z, r = ROUES[k]
        y -= baisse
        axe = [1, 0, 0]
        cylindre(m, chemin, [x, y, z], axe, -0.11, 0.11, r, r, 24, R["pneu"], couvercles=(R["jante"], R["jante"]))
        if k < 2:
            # jambe de train et bielle vers le fuselage
            s = 1 if x > 0 else -1
            a = np.array([x - s * 0.10, y, z])
            b = np.array([s * 1.02, 0.86 - baisse, z + 0.15])
            cylindre(m, chemin, a, b - a, 0, np.linalg.norm(b - a), 0.065, 0.06, 10, R["metal"])
            c = np.array([s * 1.02, 0.80 - baisse, z - 0.55])
            cylindre(m, chemin, a, c - a, 0, np.linalg.norm(c - a), 0.045, 0.045, 8, R["metal"])
        else:
            haut = interp([s[0] for s in SECTIONS], [s[2] for s in SECTIONS], z)
            a = np.array([0, y, z])
            b = np.array([0, haut + 0.05, z - 0.25])
            cylindre(m, chemin, a, b - a, 0, np.linalg.norm(b - a), 0.055, 0.05, 10, R["metal"])


def arme(m, cle):
    """Minigun de porte : pièce gun_<cle> au pivot (tournée), canons dans la sous-pièce barrels (tournent sur z)."""
    x, y, z, s = ARMES[cle]
    rot = (TANGAGE_ARME, s * LACET_ARME, 0.0)
    chemin = m.partie("gun_" + cle, (x, y, z), "solid", rot)
    met, clair = R["metal"], R["metal_clair"]
    # carter, poignées, goulotte des munitions (repère local : canons vers -z)
    cylindre(m, chemin, [0, 0, 0], [0, 0, 1], -0.12, 0.34, 0.13, 0.13, 16, met, local=True, couvercles=(met, met))
    boite(m, chemin, [0.0, -0.22, 0.30], (0.12, 0.10, 0.16), R["peint"], local=True)       # caisse de munitions
    boite(m, chemin, [0, -0.13, 0.08], (0.05, 0.05, 0.16), met, local=True)
    for sx in (-0.09, 0.09):
        cylindre(m, chemin, [sx, 0.0, 0.34], [0, 1, 0], -0.08, 0.08, 0.02, 0.02, 6, clair, local=True)
    boite(m, chemin, [0.0, 0.0, 0.42], (0.10, 0.012, 0.012), clair, local=True)
    boite(m, chemin, [-0.13 * s, -0.04, 0.10], (0.04, 0.035, 0.18), met, local=True)
    # bras de la monture vers la cabine
    boite(m, chemin, [0.0, -0.20, 0.10], (0.03, 0.10, 0.03), met, local=True)
    bar = m.partie("gun_%s/barrels" % cle, (x, y, z), None)
    # canons (6) et colliers ; repère local de la pièce mère
    for k in range(6):
        a = 2 * PI * k / 6
        c = [0.085 * math.cos(a), 0.085 * math.sin(a), 0.0]
        cylindre(m, bar, c, [0, 0, -1], 0.10, 1.08, 0.03, 0.027, 6, met, local=True)
    for t in (0.20, 0.62, 1.02):
        cylindre(m, bar, [0, 0, 0], [0, 0, -1], t - 0.03, t + 0.03, 0.125, 0.125, 14, clair, local=True)
    R3 = rot_xyz(*rot)
    bouche = np.array([x, y, z]) + R3 @ np.array([0, 0, -1.10])
    m.points["muzzle_" + cle] = tuple(bouche)


def details(m, queue=True):
    # sondes de Pitot sur le nez, antennes, marchepieds, rétroviseur de queue
    for s in (1, -1):
        a = np.array([s * 0.30, 2.18, -4.05])
        cylindre(m, "body", a, [0, 0, -1], 0.0, 0.62, 0.022, 0.016, 6, R["metal_clair"])
        cylindre(m, "body", [s * 1.12, 0.78, -2.6], [s, 0, 0], 0.0, 0.10, 0.03, 0.03, 6, R["metal"])
        boite(m, "body", [s * 1.24, 0.78, -2.6], (0.03, 0.02, 0.18), R["metal"])
    for (z, h) in ((-1.0, 0.45), (3.6, 0.35)):
        haut = interp([s[0] for s in SECTIONS], [s[1] for s in SECTIONS], z)
        boite(m, "body", [0, haut + h / 2, z], (0.012, h / 2, 0.09), R["metal"], rot=rot_xyz(math.radians(-20), 0, 0))
    boite(m, "body", [0, 0.58, 0.4], (0.012, 0.12, 0.10), R["metal"])
    # boîte de transmission du rotor de queue
    if queue:
        cylindre(m, "body", [0.0, MOYEU_QUEUE[1], MOYEU_QUEUE[2]], [1, 0, 0], -0.10, MOYEU_QUEUE[0] - 0.02, 0.15, 0.12, 14, R["peint"],
                 couvercles=(R["peint"], None))
    # rails de la porte coulissante
    for s in (1, -1):
        boite(m, "body", [s * 1.20, 2.62, 0.35], (0.03, 0.025, 1.10), R["metal"])


def rotor(m, angle0=0.0, flou=True, cassee=None):
    chemin = m.partie("rotor", MOYEU, "solid")
    c = np.array(MOYEU)
    cylindre(m, chemin, c + [0, -0.11, 0], [0, 1, 0], 0, 0.22, 0.42, 0.38, 24, R["moyeu"], couvercles=(R["moyeu"], R["moyeu"]))
    cylindre(m, chemin, c + [0, 0.11, 0], [0, 1, 0], 0, 0.16, 0.16, 0.08, 12, R["metal_clair"], couvercles=(None, R["moyeu"]))
    for k in range(4):
        a = angle0 + k * PI / 2
        er = np.array([math.cos(a), 0, math.sin(a)])
        # manchon de pale
        cylindre(m, chemin, c + er * 0.30, er, 0.0, 0.45, 0.10, 0.08, 10, R["metal"])
        aff = cassee(k) if cassee else None
        if aff == "absente":
            continue
        r1 = R_PALE if not isinstance(aff, tuple) else aff[0]
        pale(m, chemin, c, a, 0.72, r1, CORDE, EPAISSEUR, CONE, R["pale"], "xz", affaisse=(aff[1] if isinstance(aff, tuple) else None))
    m.points["rotor_hub"] = MOYEU
    if flou:
        m.partie("rotor_blur", MOYEU, "glass")
        disque_flou(m, "rotor_blur", np.array(MOYEU) + [0, 0.06, 0], [0, 1, 0], 0.55, R_PALE + 0.05, R["flou"])


def rotor_queue(m):
    chemin = m.partie("tail_rotor", MOYEU_QUEUE, "solid")
    c = np.array(MOYEU_QUEUE)
    cylindre(m, chemin, c, [1, 0, 0], -0.02, 0.12, 0.10, 0.08, 14, R["moyeu"], couvercles=(None, R["moyeu"]))
    for k in range(4):
        a = PI / 4 + k * PI / 2
        pale(m, chemin, c + [0.06, 0, 0], a, 0.10, R_PALE_QUEUE, CORDE_QUEUE, 0.035, 0.0, R["pale_queue"], "yz")
    m.points["tail_hub"] = MOYEU_QUEUE
    m.partie("tail_blur", MOYEU_QUEUE, "glass")
    disque_flou(m, "tail_blur", np.array(MOYEU_QUEUE) + [0.07, 0, 0], [1, 0, 0], 0.12, R_PALE_QUEUE + 0.03, R["flou_queue"], n=32)


def feux(m):
    S = STAB
    for (nom, s, reg) in (("emit_nav_l", -1, "rouge"), ("emit_nav_r", 1, "vert")):
        m.partie(nom, (0, 0, 0), "emissive")
        boite(m, nom, [s * (S["envergure"] + 0.03), S["y"], 8.36], (0.035, 0.035, 0.07), R[reg])
    m.partie("emit_strobe", (0, 0, 0), "emissive")
    boite(m, "emit_strobe", [0, DERIVE["y_bout"] + 0.05, 8.62], (0.04, 0.05, 0.08), R["blanc"])
    boite(m, "emit_strobe", [0, 0.56, -0.9], (0.06, 0.03, 0.06), R["rouge"])   # feu anticollision sous la cabine
    # tableau de bord vu à travers le pare-brise (lueur verte des écrans)
    m.partie("emit_cockpit", (0, 0, 0), "emissive")
    u0, v0, u1, v1 = R["tableau"]
    p = [[-0.82, 1.62, -3.30], [0.82, 1.62, -3.30], [0.82, 1.95, -3.55], [-0.82, 1.95, -3.55]]
    n = list(nrm([0, 0.6, -1]))
    m.quad("emit_cockpit", [p[0] + [u0, v1] + n, p[1] + [u1, v1] + n, p[2] + [u1, v0] + n, p[3] + [u0, v0] + n])


def helicoptere():
    m = Modele()
    m.partie("body", (0, 0, 0), "solid")
    m.partie("glass", (0, 0, 0), "glass")
    fuselage(m)
    capot(m)
    moteurs(m)
    mat_moyeu(m)
    derive_stab(m)
    roues(m)
    details(m)
    vitres(m)
    rotor(m)
    rotor_queue(m)
    for c in ("l", "r"):
        arme(m, c)
    feux(m)
    m.points["cabin_center"] = (0.0, 1.70, -0.8)
    return m


# ------------------------------------------------------------------ épave
def epave():
    rng = np.random.default_rng(7)
    m = Modele()
    m.partie("body", (0, 0, 0), "solid")
    COUPE = 4.55
    INCL = rot_xyz(math.radians(2.5), math.radians(0), math.radians(-7.0))
    BAISSE = 0.52

    def bruit(z, phi):
        return 0.045 * math.sin(7.1 * z + 3 * phi) + 0.035 * math.sin(13.7 * phi - 2.3 * z)

    def deform(p, z, phi):
        x, y, zz = p
        # nez écrasé vers le bas et raccourci, flancs cabossés, bout de la poutre arraché
        if zz < -2.6:
            k = (-2.6 - zz) / 2.1
            y = y - 0.55 * k * k - 0.18 * k * (y - 1.3)
            x *= 1.0 + 0.22 * k
            zz = zz + 0.55 * k * k
        y += bruit(zz, phi)
        x += 0.8 * bruit(zz + 1.3, phi + 0.7)
        if zz > COUPE - 0.35:
            arr = (zz - (COUPE - 0.35)) / 0.35
            r = 1.0 - 0.45 * arr * (0.5 + 0.5 * math.sin(9 * phi + 2.0))
            haut, bas, _, _ = section(zz)
            yc = (haut + bas) / 2
            x *= r
            y = yc + (y - yc) * r
        q = INCL @ np.array([x, y - BAISSE, zz])
        return list(q)

    fuselage(m, deform=deform, rot_deform=INCL, z1=COUPE)

    def corps_incl(p):
        return INCL @ (np.asarray(p, float) - [0, BAISSE, 0])

    # capot et moteurs (posés selon la même inclinaison), sans vitres ni armes
    sous = Modele()
    sous.partie("body", (0, 0, 0), "solid")
    capot(sous, ecrase=0.08)
    moteurs(sous, ecrase=0.06)
    mat_moyeu(sous)
    details(sous, queue=False)
    roues(sous, lesquelles=(1,), baisse=0.05)
    for q in sous.quads["body"]:
        m.quad("body", [list(corps_incl(p[:3])) + p[3:5] + list(INCL @ np.array(p[5:8])) for p in q])
    for k in ("engine_l", "engine_r"):
        m.points["fire_%d" % (0 if k == "engine_l" else 1)] = tuple(corps_incl(sous.points[k]))
    # rotor : deux pales tordues jusqu'au sol, une cassée court, une arrachée
    rot_m = Modele()

    def cassee(k):
        return [(R_PALE, lambda r: -0.065 * r * r), (2.1, None), "absente", (R_PALE * 0.92, lambda r: -0.05 * r * r - 0.02 * r)][k]

    rotor(rot_m, angle0=math.radians(20), flou=False, cassee=cassee)
    for q in rot_m.quads["rotor"]:
        pv = np.array(MOYEU)
        m.quad("body", [list(corps_incl(np.array(p[:3]) + pv)) + p[3:5] + list(INCL @ np.array(p[5:8])) for p in q])
    # queue arrachée, couchée à côté de l'épave
    queue = Modele()
    queue.partie("body", (0, 0, 0), "solid")
    Q = rot_xyz(math.radians(0), math.radians(38), math.radians(78))
    deplace = np.array([3.4, 0.0, 1.2])
    pivot_queue = np.array([0, 2.3, COUPE + 0.1])

    def tq(p):
        return Q @ (np.asarray(p, float) - pivot_queue) + pivot_queue + deplace

    # tronçon de poutre (de la coupure à la queue) : réutilise le fuselage sur cette plage
    M = 36
    zs = [z for z in zs_fuselage(COUPE + 0.1, Z_QUEUE)]
    P, UV = [], []
    for z in zs:
        ligne, luv = [], []
        for j in range(M + 1):
            phi = 2 * PI * j / M
            p = point_fuselage(z, phi)
            if z < COUPE + 0.45:
                arr = 1 - (z - COUPE - 0.1) / 0.35
                r = 1.0 - 0.4 * arr * (0.5 + 0.5 * math.sin(11 * phi))
                haut, bas, _, _ = section(z)
                yc = (haut + bas) / 2
                p = [p[0] * r, yc + (p[1] - yc) * r, z]
            ligne.append(p)
            luv.append([u_phi(phi), v_z(z)])
        P.append(ligne)
        UV.append(luv)
    P = np.array(P)
    P[:, -1] = P[:, 0]
    grille(queue, "body", P, np.array(UV), dehors_fuselage, couture=True)
    derive_stab(queue)
    rq = Modele()
    rq.partie("body", (0, 0, 0), "solid")
    # rotor de queue tordu : deux pales seulement
    c = np.array(MOYEU_QUEUE)
    rq.partie("tr", MOYEU_QUEUE, "solid")
    for k, a in enumerate((0.4, 2.1)):
        pale(rq, "tr", c + [0.06, 0, 0], a, 0.10, R_PALE_QUEUE * (1.0 if k == 0 else 0.55), CORDE_QUEUE, 0.035, 0.0, R["pale_queue"], "yz")
    for q in rq.quads["tr"]:
        queue.quad("body", [list(np.array(p[:3]) + c) + p[3:] for p in q])
    # boîte de transmission du rotor de queue (reste sur la queue arrachée)
    cylindre(queue, "body", [0.0, MOYEU_QUEUE[1], MOYEU_QUEUE[2]], [1, 0, 0], -0.10, MOYEU_QUEUE[0] - 0.02, 0.15, 0.12, 14,
             R["peint"], couvercles=(R["peint"], None))
    # la queue repose sur le sol (son point le plus bas à y = 0,02)
    bas = min(tq(p[:3])[1] for q in queue.quads["body"] for p in q)
    leve = np.array([0, 0.02 - bas, 0])
    for q in queue.quads["body"]:
        m.quad("body", [list(tq(p[:3]) + leve) + p[3:5] + list(Q @ np.array(p[5:8])) for p in q])
    m.points["fire_2"] = tuple(corps_incl([0, 1.9, COUPE - 0.2]))
    m.points["fire_3"] = tuple(tq([0, 2.4, 6.4]) + leve)
    m.points["cabin_center"] = tuple(corps_incl([0, 1.6, -0.8]))
    return m


# ------------------------------------------------------------------ textures
def bruit(h, w, graine, lissage=1, echelle=1.0):
    rng = np.random.default_rng(graine)
    n = rng.normal(0, 1, (h, w))
    for _ in range(lissage):
        p = np.pad(n, 1, mode="wrap")
        n = sum(p[1 + dy:1 + dy + h, 1 + dx:1 + dx + w] for dy in (-1, 0, 1) for dx in (-1, 0, 1)) / 9
    n /= max(n.std(), 1e-6)
    return n * echelle


def bruit_grossier(h, w, graine, taille):
    """Bruit lisse à grosses taches (taille en pixels)."""
    rng = np.random.default_rng(graine)
    gh, gw = max(2, h // taille + 2), max(2, w // taille + 2)
    g = rng.normal(0, 1, (gh, gw))
    im = Image.fromarray(((g - g.min()) / (g.max() - g.min() + 1e-9) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
    a = np.asarray(im, float) / 255 * 2 - 1
    return a


PEINTURE = np.array([70, 78, 54], float)          # vert olive militaire passé
PEINTURE_SOMBRE = np.array([52, 58, 42], float)
METAL = np.array([46, 48, 50], float)
METAL_CLAIR = np.array([96, 98, 100], float)
NOIR = np.array([22, 23, 24], float)


def region_vide(nom):
    u0, v0, u1, v1 = R[nom]
    return v1 - v0, u1 - u0


def coller(atlas, nom, img, alpha=None):
    u0, v0, u1, v1 = R[nom]
    h, w = v1 - v0, u1 - u0
    assert img.shape[:2] == (h, w), (nom, img.shape, (h, w))
    a = np.clip(img, 0, 255)
    atlas[v0:v1, u0:u1, :3] = a[..., :3]
    atlas[v0:v1, u0:u1, 3] = 255 if alpha is None else np.clip(alpha, 0, 255)


def peinture(h, w, graine, base=PEINTURE):
    img = np.ones((h, w, 3)) * base
    img *= (1 + 0.035 * bruit_grossier(h, w, graine, 40))[..., None]
    img *= (1 + 0.05 * bruit_grossier(h, w, graine + 1, 9))[..., None]
    img += bruit(h, w, graine + 2, 1, 2.2)[..., None]
    return img


def ligne_h(img, y, x0, x1, couleur=0.62, rivets=True, ep=1):
    y = int(round(y))
    x0, x1 = int(max(0, x0)), int(min(img.shape[1], x1))
    if not 0 <= y < img.shape[0] or x1 <= x0:
        return
    img[y:y + ep, x0:x1] *= couleur
    if y + ep < img.shape[0]:
        img[y + ep, x0:x1] *= 1.10
    if rivets:
        for x in range(x0 + 3, x1, 7):
            for dy in (-3, 3):
                yy = y + dy
                if 0 <= yy < img.shape[0]:
                    img[yy, x] *= 0.78
                    if yy + 1 < img.shape[0]:
                        img[yy + 1, x] *= 1.12


def ligne_v(img, x, y0, y1, couleur=0.62, rivets=True, ep=1):
    x = int(round(x)) % img.shape[1]
    y0, y1 = int(max(0, y0)), int(min(img.shape[0], y1))
    if y1 <= y0:
        return
    img[y0:y1, x:x + ep] *= couleur
    if x + ep < img.shape[1]:
        img[y0:y1, x + ep] *= 1.10
    if rivets:
        for y in range(y0 + 3, y1, 7):
            for dx in (-3, 3):
                xx = (x + dx) % img.shape[1]
                img[y, xx] *= 0.78


def rect(img, x0, y0, x1, y1, couleur=0.62, rivets=False):
    ligne_h(img, y0, x0, x1, couleur, rivets)
    ligne_h(img, y1, x0, x1, couleur, rivets)
    ligne_v(img, x0, y0, y1, couleur, rivets)
    ligne_v(img, x1, y0, y1, couleur, rivets)


def texte(chaine, hauteur, police="bahnschrift.ttf"):
    """Texte blanc au pochoir (masque 0..1)."""
    try:
        f = ImageFont.truetype(police, hauteur)
    except OSError:
        try:
            f = ImageFont.truetype("arialbd.ttf", hauteur)
        except OSError:
            f = ImageFont.load_default()
    l, t, r, b = f.getbbox(chaine)
    im = Image.new("L", (r - l + 4, b - t + 4), 0)
    ImageDraw.Draw(im).text((2 - l, 2 - t), chaine, fill=255, font=f)
    return np.asarray(im, float) / 255


def perimetre(z):
    _, _, x, y, _, _ = anneau(z)
    return float(np.hypot(np.diff(x), np.diff(y)).sum())


def tampon(img, masque, z_c, a_c, cote, hauteur_m, couleur, opac=0.92):
    """Marquage (masque 0..1, lignes = haut du texte) posé sur le fuselage, centré en (z_c, a_c), de hauteur hauteur_m
    mètres, lisible de l'extérieur et sans déformation : la texture n'a pas la même densité autour (u, selon le tour de
    la section) et en long (v). À droite (cote 1), le texte court vers le nez (-z) et monte quand a augmente ; à gauche
    (cote -1), il court vers la queue et monte quand a diminue."""
    _, v0, _, v1 = R["fuselage"]
    dens_v = (v1 - v0) / (Z_QUEUE - Z_NEZ)
    dens_u = (R["fuselage"][2] - R["fuselage"][0]) / perimetre(z_c)
    hm, wm = masque.shape
    larg_m = hauteur_m * wm / hm
    T = masque[::-1, ::-1].T if cote > 0 else masque.T
    lv, lu = max(2, int(round(larg_m * dens_v))), max(2, int(round(hauteur_m * dens_u)))
    T = np.asarray(Image.fromarray((T * 255).astype(np.uint8)).resize((lu, lv), Image.LANCZOS), float) / 255
    vc = int(round(v_z(z_c) - v0 - lv / 2))
    uc = int(round(u_phi(a_c) - R["fuselage"][0] - lu / 2))
    zone = img[max(vc, 0):vc + lv, max(uc, 0):uc + lu]
    k = (T[max(0, -vc):max(0, -vc) + zone.shape[0], max(0, -uc):max(0, -uc) + zone.shape[1]] * opac)[..., None]
    zone[:] = zone * (1 - k) + np.asarray(couleur, float) * k


def tex_fuselage(epave_=False):
    h, w = region_vide("fuselage")
    img = peinture(h, w, 11)
    U = lambda phi: u_phi(phi) - R["fuselage"][0]
    V = lambda z: v_z(z) - R["fuselage"][1]
    vv = np.arange(h)[:, None] * np.ones((1, w))
    uu = np.ones((h, 1)) * np.arange(w)[None, :]
    phi = uu / w * 2 * PI
    z = Z_NEZ + vv / h * (Z_QUEUE - Z_NEZ)
    # dessous plus sombre (poussière), dessus un peu passé par le soleil
    dessous = np.clip(np.cos(phi), 0, 1) ** 1.5
    img *= (1 - 0.22 * dessous)[..., None]
    img *= (1 + 0.06 * np.clip(-np.cos(phi), 0, 1))[..., None]
    # bande anti-reflet noire devant le pare-brise (sur le nez)
    nez = (z > -4.42) & (z < -4.06) & (np.abs(phi - PI) < 0.60)
    img[nez] = img[nez] * 0.35 + NOIR * 0.65 * 0.6
    # suie des moteurs sur le dessus de la poutre
    suie = np.exp(-((phi - PI) / 0.55) ** 2) * np.clip((z - 1.6) / 0.6, 0, 1) * np.exp(-np.clip(z - 2.4, 0, 9) / 2.4)
    img *= (1 - 0.55 * suie)[..., None]
    # coulures verticales sur les flancs (vers le bas : phi décroît à droite, croît à gauche)
    for graine, cote in ((21, 1), (22, -1)):
        rng = np.random.default_rng(graine)
        for _ in range(70):
            zc = rng.uniform(-3.5, 8.0)
            larg = rng.uniform(1.5, 4.5)
            phi0 = rng.uniform(PI / 2 + 0.2, PI - 0.3) if cote == 1 else rng.uniform(PI + 0.3, 3 * PI / 2 - 0.2)
            long_ = rng.uniform(0.4, 1.3)
            vc = V(zc)
            col = np.exp(-((vv - vc) / larg) ** 2)
            if cote == 1:
                bande = (phi <= phi0) & (phi >= phi0 - long_)
                f = np.clip((phi - (phi0 - long_)) / long_, 0, 1)
            else:
                bande = (phi >= phi0) & (phi <= phi0 + long_)
                f = np.clip(((phi0 + long_) - phi) / long_, 0, 1)
            img *= (1 - 0.10 * col * bande * f)[..., None]
    # panneaux : cadres (z constant) et coutures longitudinales (hauteur constante sur la cabine)
    for zc in (-4.05, -3.00, 1.65, 2.40, 3.60, 4.80, 6.00, 7.20, 8.10):
        ligne_h(img, V(zc), 0, w, 0.60)
    rangees = range(int(V(-4.05)), int(V(2.40)))
    for y_c in (0.95, 2.80):
        for cote in (1, -1):
            for r in rangees:
                zr = Z_NEZ + (r + 0.5) / h * (Z_QUEUE - Z_NEZ)
                a = a_de_y(zr, y_c)
                img[r, int(U(a if cote == 1 else 2 * PI - a)) % w] *= 0.62
    ligne_v(img, U(PI), V(-1.98), V(2.4), 0.66, rivets=False)

    def contour(z0, z1, y0, y1, cote, couleur, rivets=False):
        """Contour d'une porte (z0..z1, hauteurs y0..y1) : côtés à hauteur constante suivis rangée par rangée."""
        def ua(z, y):
            a = a_de_y(z, y)
            return U(a if cote == 1 else 2 * PI - a)
        for zc in (z0, z1):
            a, b = sorted((ua(zc, y0), ua(zc, y1)))
            ligne_h(img, V(zc), a, b, couleur, rivets)
        for r in range(int(V(z0)), int(V(z1)) + 1):
            zr = Z_NEZ + (r + 0.5) / h * (Z_QUEUE - Z_NEZ)
            for y in (y0, y1):
                img[r, int(ua(zr, y)) % w] *= couleur

    for cote in (1, -1):
        for p in PORTES:
            contour(p["z"][0], p["z"][1], p["y"][0], p["y"][1], cote, 0.52, True)
        # poignée de la porte coulissante, marchepied
        a = a_de_y(0.95, 1.55)
        hx = int(U(a if cote == 1 else 2 * PI - a))
        img[int(V(0.92)):int(V(1.0)), hx - 3:hx + 3] *= 0.4
        # trappes d'accès sur la poutre
        for (z0, z1) in ((3.0, 3.5), (5.2, 5.6)):
            contour(z0, z1, 2.05, 2.55, cote, 0.62)
    # vitres : fond sombre de l'intérieur et joint autour, rangée par rangée (mêmes plages que les vitres posées)
    fond_vitre = bruit(h, w, 31, 1, 1.5)
    for v in VITRES:
        z0, z1 = v["z"]
        for r in range(int(V(z0)) - 2, int(V(z1)) + 3):
            zr = min(max(Z_NEZ + (r + 0.5) / h * (Z_QUEUE - Z_NEZ), z0), z1)
            for (a0, a1) in plages_vitre(v, zr):
                ua, ub = int(U(a0)), int(U(a1))
                img[r, ua - 2:ub + 3] = img[r, ua - 2:ub + 3] * 0.45 + NOIR * 0.55
                if int(V(z0)) <= r <= int(V(z1)):
                    img[r, ua:ub + 1] = np.array([18, 20, 24]) + fond_vitre[r, ua:ub + 1, None]
    # marquages : immatriculation au pochoir sur la poutre (deux côtés), « DANGER » sous le rotor de queue
    num = texte("HZ-07", 96)
    danger = texte("DANGER", 48)
    for cote in (1, -1):
        a_c = PI / 2 if cote == 1 else 3 * PI / 2
        tampon(img, num, 4.95, a_c + (0.15 if cote == 1 else -0.15), cote, 0.42, (205, 205, 196))
        tampon(img, danger, 7.45, a_c + (0.35 if cote == 1 else -0.35), cote, 0.11, (190, 160, 40), 0.85)
    # étoile pâle (insigne fictif) sur la cabine, entre la fenêtre du mitrailleur et la porte
    etoile = Image.new("L", (96, 96), 0)
    d = ImageDraw.Draw(etoile)
    pts = []
    for k in range(10):
        r_ = 42 if k % 2 == 0 else 17
        a = -PI / 2 + k * PI / 5
        pts.append((48 + r_ * math.cos(a), 48 + r_ * math.sin(a)))
    d.ellipse((4, 4, 92, 92), outline=255, width=6)
    d.polygon(pts, fill=255)
    msk = np.asarray(etoile, float) / 255
    for cote in (1, -1):
        a = a_de_y(-0.77, 1.95)
        tampon(img, msk, -0.77, a if cote == 1 else 2 * PI - a, cote, 0.52, (120, 124, 104), 0.55)
    if epave_:
        img = bruler(img, 51)
    return img


def bruler(img, graine, force=1.0):
    """Peinture brûlée : suie, métal chauffé (bleu-brun), cloques, restes d'olive par endroits."""
    h, w = img.shape[:2]
    t = bruit_grossier(h, w, graine, 60) * 0.6 + bruit_grossier(h, w, graine + 1, 14) * 0.4
    brule = np.clip(0.88 + 0.32 * t, 0, 1) * force
    suie = np.array([34, 31, 29], float) * (1 + 0.25 * bruit_grossier(h, w, graine + 9, 8))[..., None]
    chauffe = np.array([88, 70, 58], float)
    rouille = np.array([110, 62, 34], float)
    out = img * (1 - brule[..., None]) + suie * brule[..., None]
    rr = np.clip(bruit_grossier(h, w, graine + 2, 22), 0, 1) ** 2
    out = out * (1 - 0.6 * rr[..., None]) + rouille * 0.6 * rr[..., None]
    cc = np.clip(-bruit_grossier(h, w, graine + 3, 30), 0, 1) ** 2 * brule
    out = out * (1 - 0.4 * cc[..., None]) + chauffe * 0.4 * cc[..., None]
    out += bruit(h, w, graine + 4, 1, 4.0)[..., None]
    return out


def tex_simple(nom, base, graine, epave_=False):
    h, w = region_vide(nom)
    img = peinture(h, w, graine, base)
    if epave_:
        img = bruler(img, graine + 100)
    return img


def tex_capot(epave_=False):
    h, w = region_vide("capot")
    img = peinture(h, w, 41)
    for x in (w * 0.25, w * 0.5, w * 0.75):
        ligne_v(img, x, 0, h, 0.6)
    for y in (h * 0.18, h * 0.62):
        ligne_h(img, y, 0, w, 0.6)
    # grilles d'aération
    for (x0, y0) in ((w * 0.3, h * 0.25), (w * 0.58, h * 0.25)):
        for k in range(8):
            img[int(y0 + k * 5):int(y0 + k * 5 + 2), int(x0):int(x0 + w * 0.12)] *= 0.45
    # passerelle antidérapante sur le dessus
    img[int(h * 0.65):int(h * 0.95), int(w * 0.42):int(w * 0.58)] *= 0.7
    if epave_:
        img = bruler(img, 141, 1.0)
    return img


def tex_moteur(epave_=False):
    h, w = region_vide("moteur")
    img = peinture(h, w, 61, PEINTURE_SOMBRE)
    for y in (h * 0.15, h * 0.55, h * 0.85):
        ligne_h(img, y, 0, w, 0.6)
    img[int(h * 0.88):] *= 0.5     # sortie noircie
    if epave_:
        img = bruler(img, 161)
    return img


def tex_disque(nom, centre, bord, trou=0.0, rayons=0):
    h, w = region_vide(nom)
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = np.clip(np.sqrt(((xx - cx) / (w / 2)) ** 2 + ((yy - cy) / (h / 2)) ** 2), 0, 1)[..., None]
    img = np.asarray(centre, float) + (np.asarray(bord, float) - np.asarray(centre, float)) * r
    if rayons:
        ang = np.arctan2(yy - cy, xx - cx)
        img *= (1 - 0.18 * (np.cos(ang * rayons) > 0.6))[..., None]
    if trou:
        img[r[..., 0] < trou] = NOIR
    return img


def tex_pale(nom, epave_=False):
    h, w = region_vide(nom)
    img = np.ones((h, w, 3)) * np.array([34, 36, 37], float)
    img += bruit(h, w, 71, 1, 2.0)[..., None]
    img[:, : max(2, w // 8)] = METAL_CLAIR * 0.85      # bord d'attaque (bande d'usure)
    bout = int(h * 0.93)
    img[bout:] = np.array([200, 168, 40], float)        # bout jaune
    if epave_:
        img = bruler(img, 171, 0.7)
    return img


def tex_flou(nom, couleur_bout=(200, 168, 40)):
    h, w = region_vide(nom)
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = np.sqrt(((xx - cx) / (w / 2)) ** 2 + ((yy - cy) / (h / 2)) ** 2)
    ang = np.arctan2(yy - cy, xx - cx)
    img = np.ones((h, w, 3)) * np.array([36, 38, 40], float)
    anneau_ = np.clip((r - 0.08) / 0.10, 0, 1) * np.clip((1.0 - r) / 0.015, 0, 1)
    # voile très léger, un peu plus marqué vers le bout des pales, quatre traînées douces (pas de grain)
    a = anneau_ * (0.045 + 0.055 * r + 0.025 * np.cos(4 * ang) ** 8)
    bout = np.exp(-((r - 0.955) / 0.016) ** 2)
    img = img * (1 - bout[..., None]) + np.asarray(couleur_bout, float) * bout[..., None]
    a = np.clip(a + 0.10 * bout * (r < 1), 0, 1)
    return img, a * 255


def tex_vitre():
    h, w = region_vide("vitre")
    yy, xx = np.mgrid[0:h, 0:w]
    img = np.ones((h, w, 3)) * np.array([60, 82, 96], float)
    reflet = np.clip(1 - np.abs((xx / w) * 1.2 - (yy / h) * 0.8 - 0.25) / 0.12, 0, 1)
    img += reflet[..., None] * 110
    a = np.full((h, w), 140.0) + reflet * 70
    return img, a


def atlas(epave_=False):
    a = np.zeros((TEX, TEX, 4), np.uint8)
    coller(a, "fuselage", tex_fuselage(epave_))
    coller(a, "capot", tex_capot(epave_))
    coller(a, "moteur", tex_moteur(epave_))
    coller(a, "entree", tex_disque("entree", (14, 14, 15), (70, 72, 74), rayons=12))
    coller(a, "sortie", tex_disque("sortie", (10, 9, 9), (40, 36, 33)))
    coller(a, "pale", tex_pale("pale", epave_))
    coller(a, "pale_queue", tex_pale("pale_queue", epave_))
    for nom, graine in (("derive", 91), ("stab", 92), ("peint", 93)):
        img = tex_simple(nom, PEINTURE, graine, epave_)
        if nom == "derive":
            h, w = img.shape[:2]
            ligne_h(img, h * 0.55, 0, w, 0.6)
            ligne_v(img, w * 0.5, 0, h, 0.6)
        coller(a, nom, img)
    h, w = region_vide("pneu")
    pneu = np.ones((h, w, 3)) * np.array([26, 26, 27], float) + bruit(h, w, 94, 1, 2.0)[..., None]
    pneu[:, ::6] *= 0.7
    coller(a, "pneu", pneu)
    coller(a, "jante", tex_disque("jante", (90, 92, 94), (50, 52, 54), trou=0.15))
    coller(a, "metal", np.ones(region_vide("metal") + (3,)) * METAL + bruit(*region_vide("metal"), 95, 1, 3.0)[..., None])
    coller(a, "metal_clair", np.ones(region_vide("metal_clair") + (3,)) * METAL_CLAIR + bruit(*region_vide("metal_clair"), 96, 1, 3.0)[..., None])
    coller(a, "moyeu", tex_disque("moyeu", (80, 82, 84), (44, 46, 48), rayons=4))
    if not epave_:
        img, al = tex_flou("flou")
        coller(a, "flou", img, al)
        img, al = tex_flou("flou_queue", (210, 210, 205))
        coller(a, "flou_queue", img, al)
        img, al = tex_vitre()
        coller(a, "vitre", img, al)
    for nom, c in (("rouge", (255, 40, 30)), ("vert", (40, 255, 90)), ("blanc", (255, 255, 250)), ("sombre", (16, 17, 18))):
        coller(a, nom, np.ones(region_vide(nom) + (3,)) * np.array(c, float))
    h, w = region_vide("tableau")
    tab = np.ones((h, w, 3)) * np.array([6, 30, 12], float)
    tab[2:-2, 2:w // 2 - 1] = (30, 150, 60)
    tab[2:-2, w // 2 + 1:-2] = (40, 140, 160)
    coller(a, "tableau", tab)
    deborder(a)
    return Image.fromarray(a, "RGBA")


def deborder(a, marge=2):
    """Recopie le bord de chaque région autour d'elle là où l'atlas est vide (jamais de trou à l'arrondi)."""
    libre = a[..., 3] == 0
    H, W = libre.shape
    for (u0, v0, u1, v1) in R.values():
        for k in range(1, marge + 1):
            for (dst, src) in (((v0 - k, slice(u0, u1)), (v0, slice(u0, u1))), ((v1 - 1 + k, slice(u0, u1)), (v1 - 1, slice(u0, u1)))):
                if 0 <= dst[0] < H:
                    ok = libre[dst]
                    a[dst][ok] = a[src][ok]
                    libre[dst] &= ~ok
            for (dst, src) in (((slice(v0, v1), u0 - k), (slice(v0, v1), u0)), ((slice(v0, v1), u1 - 1 + k), (slice(v0, v1), u1 - 1))):
                if 0 <= dst[1] < W:
                    ok = libre[dst]
                    a[dst][ok] = a[src][ok]
                    libre[dst] &= ~ok


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    geo_dir = dossier if dossier else ASSETS / "geo"
    tex_dir = dossier if dossier else ASSETS / "textures" / "entity"
    geo_dir.mkdir(parents=True, exist_ok=True)
    tex_dir.mkdir(parents=True, exist_ok=True)
    m = helicoptere()
    n = m.ecrire(geo_dir / "helicopter.geo")
    atlas().save(tex_dir / "helicopter.png", optimize=True)
    e = epave()
    ne = e.ecrire(geo_dir / "helicopter_wreck.geo")
    atlas(epave_=True).save(tex_dir / "helicopter_wreck.png", optimize=True)
    print("[helico] %d quads (hélicoptère), %d quads (épave) -> %s, %s" % (n, ne, geo_dir, tex_dir))


if __name__ == "__main__":
    main()
