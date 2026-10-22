#!/usr/bin/env python3
"""
Modèles et textures HD de la charge de C4 et de la charge explosive de CRac.

La C4 doit se voir « bien meilleure » que la charge explosive : la C4 est un explosif militaire propre (deux pains M112
vert olive sous film, sangles noires, minuteur durci à écran rouge, clavier, voyant, antenne, détonateurs), la charge
explosive est un explosif de fortune (sac de jute usé et taché, boîtes de conserve rouillées tenues au scotch gris,
corde, mèche tressée).

Écrit (jamais à la main) :
  - geo/c4.geo.json, geo/explosive_charge.geo.json (mêmes os qu'avant : c4/bundle/timer/led/display/tens_N/ones_N/
    wiring et charge/sack/strap/cans/fuse_root/fuse/ember, lus par C4Display et ExplosiveChargeFuse) ;
  - textures/entity/c4.png, c4_glowmask.png, explosive_charge.png, explosive_charge_glowmask.png.

    python mods/crac/outils/explosifs.py --apercu DOSSIER     rendus et textures dans DOSSIER, rien d'installé
    python mods/crac/outils/explosifs.py --installer          écrit les fichiers dans les assets de CRac

Après --installer : relancer tenue_explosifs.py (boîtes tenues en tête de ce script-là) puis fabriquer CRac.

Les coordonnées sont celles que GeckoLib dessine (x de Minecraft) ; le fichier geo est écrit en coordonnées Bedrock
(x opposé), comme GeckoLib 4 le relit (BakedModelFactory : origine x = -(x + taille), pivot x opposé, rotations x et y
opposées). Placement des textures sur les faces : celui de GeoQuad / VertexSet de GeckoLib 4.4.
"""
import argparse
import json
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

RACINE = Path(__file__).resolve().parents[1]
ASSETS = RACINE / "src" / "client" / "resources" / "assets" / "crac"
DEPOT = RACINE.parents[1]
POLICE = DEPOT / "launcher" / "app" / "assets" / "fonts" / "DMSans-Bold.ttf"

D = 16          # points de texture par pixel de modèle
MARGE = 2       # points de marge autour de chaque face (débord de couleur)
LARGEUR = 1024  # largeur de l'atlas


# =============================================================================================== bruit
def _hash(ix, iy, iz, graine):
    h = (ix.astype(np.int64) * 374761393 + iy.astype(np.int64) * 668265263 + iz.astype(np.int64) * 2147483647
         + graine * 1442695041) & 0xFFFFFFFF
    h = ((h ^ (h >> 13)) * 1274126177) & 0xFFFFFFFF
    return ((h ^ (h >> 16)) & 0xFFFF) / 65535.0


def bruit(p, freq, graine=0):
    q = p * freq
    i = np.floor(q)
    f = q - i
    f = f * f * (3 - 2 * f)
    i = i.astype(np.int64)
    r = 0
    for dx in (0, 1):
        for dy in (0, 1):
            for dz in (0, 1):
                w = (f[..., 0] if dx else 1 - f[..., 0]) * (f[..., 1] if dy else 1 - f[..., 1]) * \
                    (f[..., 2] if dz else 1 - f[..., 2])
                r = r + w * _hash(i[..., 0] + dx, i[..., 1] + dy, i[..., 2] + dz, graine)
    return r


def fbm(p, freq, octaves=4, graine=0):
    s, a, t = 0, 0.5, 0
    for o in range(octaves):
        s = s + a * bruit(p, freq * 2 ** o, graine + 17 * o)
        t += a
        a *= 0.5
    return s / t


def col(*c):
    return np.array(c, dtype=np.float64) / 255.0


def melange(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return a * (1 - t) + b * t


# =============================================================================================== textes
_polices = {}


def masque_texte(lignes, haut=64, interligne=1.15):
    """Masque (0..1) d'un texte sur plusieurs lignes, centré."""
    if haut not in _polices:
        _polices[haut] = ImageFont.truetype(str(POLICE), haut)
    f = _polices[haut]
    larg = max(int(f.getlength(l)) for l in lignes) + 8
    h = int(haut * interligne * len(lignes)) + 8
    im = Image.new("L", (larg, h), 0)
    d = ImageDraw.Draw(im)
    for k, l in enumerate(lignes):
        w = f.getlength(l)
        d.text(((larg - w) / 2, 4 + k * haut * interligne), l, font=f, fill=255)
    return np.asarray(im, dtype=np.float64) / 255.0


def masque_sept_segments(chiffre, w=60, h=100):
    """Chiffre à sept segments (10 = tiret), segments en biseau."""
    seg = {0: "abcdef", 1: "bc", 2: "abged", 3: "abgcd", 4: "fgbc", 5: "afgcd", 6: "afgedc", 7: "abc",
           8: "abcdefg", 9: "abcdfg", 10: "g"}[chiffre]
    im = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(im)
    e = w * 0.16
    m = 0.10 * w
    x0, x1 = m, w - m
    y0, y1, ym = m, h - m, h / 2

    def horiz(y):
        return [(x0 + e * 0.6, y), (x0 + e * 1.1, y - e / 2), (x1 - e * 1.1, y - e / 2), (x1 - e * 0.6, y),
                (x1 - e * 1.1, y + e / 2), (x0 + e * 1.1, y + e / 2)]

    def vert(x, ya, yb):
        return [(x, ya + e * 0.6), (x + e / 2, ya + e * 1.1), (x + e / 2, yb - e * 1.1), (x, yb - e * 0.6),
                (x - e / 2, yb - e * 1.1), (x - e / 2, ya + e * 1.1)]

    formes = {"a": horiz(y0 + e / 2), "g": horiz(ym), "d": horiz(y1 - e / 2),
              "f": vert(x0 + e / 2, y0 + e / 2, ym), "b": vert(x1 - e / 2, y0 + e / 2, ym),
              "e": vert(x0 + e / 2, ym, y1 - e / 2), "c": vert(x1 - e / 2, ym, y1 - e / 2)}
    for s in seg:
        d.polygon(formes[s], fill=255)
    im = im.transform(im.size, Image.AFFINE, (1, 0.12, -0.06 * h, 0, 1, 0), resample=Image.BICUBIC)  # léger italique
    return np.asarray(im, dtype=np.float64) / 255.0


def echantillon(masque, a, b):
    """a, b dans [0, 1] (a vers la droite, b vers le bas) -> valeur bilinéaire du masque (0 hors du cadre)."""
    h, w = masque.shape
    dedans = (a >= 0) & (a <= 1) & (b >= 0) & (b <= 1)
    x = np.clip(a * (w - 1), 0, w - 1)
    y = np.clip(b * (h - 1), 0, h - 1)
    x0 = np.floor(x).astype(int)
    y0 = np.floor(y).astype(int)
    x1 = np.minimum(x0 + 1, w - 1)
    y1 = np.minimum(y0 + 1, h - 1)
    fx, fy = x - x0, y - y0
    v = (masque[y0, x0] * (1 - fx) * (1 - fy) + masque[y0, x1] * fx * (1 - fy) + masque[y1, x0] * (1 - fx) * fy
         + masque[y1, x1] * fx * fy)
    return np.where(dedans, v, 0.0)


class Decal:
    """Image posée sur un plan : origine (coin haut gauche), axe a (droite) et axe b (bas) en px du modèle."""

    def __init__(self, masque, origine, axe_a, axe_b, couleur, opacite=1.0, lueur=None, normale=None):
        self.m = masque
        self.o = np.array(origine, float)
        self.a = np.array(axe_a, float)
        self.b = np.array(axe_b, float)
        self.c = couleur
        self.op = opacite
        self.lueur = lueur
        self.n = None if normale is None else np.array(normale, float)

    def poser(self, p, n, rgb, glow):
        if self.n is not None and float(np.dot(n, self.n)) < 0.9:
            return
        d = p - self.o
        a = (d @ self.a) / float(self.a @ self.a)
        b = (d @ self.b) / float(self.b @ self.b)
        v = echantillon(self.m, a, b) * self.op
        rgb[:] = melange(rgb, np.broadcast_to(self.c, rgb.shape), v)
        if self.lueur is not None:
            glow[:] = np.maximum(glow, self.lueur[None, None, :] * v[..., None])


# =============================================================================================== matières
def bords(fu, fv, w, h, larg=0.35):
    """Distance (px) au bord le plus proche de la face."""
    return np.minimum(np.minimum(fu * w, (1 - fu) * w), np.minimum(fv * h, (1 - fv) * h))


def peindre(mat, p, n, fu, fv, w, h, prm):
    """Couleur (h, w, 3), opacité (h, w), lueur (h, w, 3) d'une face. p : points du modèle (px)."""
    sh = p.shape[:2]
    a = np.ones(sh)
    g = np.zeros(sh + (3,))
    e = bords(fu, fv, w, h)
    usure = np.exp(-e / 0.12)                                     # arêtes usées / éclaircies
    if mat == "m112":
        base = col(84, 92, 56)
        r = melange(base, col(66, 74, 42), fbm(p, 0.35, 3, 1))
        plis = np.sin((p[..., 0] * 1.7 + p[..., 2] * 0.6 + fbm(p, 0.6, 2, 3) * 6.0)) * 0.5 + 0.5
        r = r * (0.93 + 0.10 * plis[..., None] ** 4)                # reflets du film plastique
        r = r * (0.96 + 0.08 * bruit(p, 6.0, 4)[..., None])
        r = melange(r, col(130, 136, 96), usure * 0.55)
    elif mat == "joint":
        r = np.broadcast_to(col(26, 28, 20), sh + (3,)).copy()
    elif mat == "sangle":
        trame = (np.sin(p[..., 0] * 40) * np.sin(p[..., 2] * 40 + p[..., 1] * 40)) * 0.5 + 0.5
        r = melange(col(24, 25, 27), col(44, 45, 48), trame * 0.6 + bruit(p, 3, 5) * 0.2)
        r = melange(r, col(70, 70, 72), usure * 0.4)
    elif mat == "metal":
        brosse = bruit(p * np.array([1, 1, 12.0]), 4.0, 6)
        r = melange(col(118, 122, 128), col(170, 174, 180), brosse * 0.7 + 0.15 * (n[1] > 0.5))
        r = melange(r, col(215, 218, 222), usure * 0.6)
    elif mat == "boitier":
        grain = bruit(p, 14.0, 7)
        r = melange(col(30, 32, 35), col(44, 46, 50), grain * 0.5 + fbm(p, 0.8, 2, 8) * 0.3)
        r = melange(r, col(92, 96, 102), usure * 0.7)
    elif mat == "couvercle":
        grain = bruit(p, 14.0, 9)
        r = melange(col(40, 42, 46), col(54, 57, 62), grain * 0.5)
        r = melange(r, col(110, 114, 120), usure * 0.8)
    elif mat == "ecran":
        r = melange(col(10, 8, 8), col(22, 16, 16), fbm(p, 1.5, 2, 10))
        reflet = np.clip(1 - np.abs((p[..., 0] + p[..., 2] * 0.8) - 0.6) / 0.5, 0, 1)
        r = r + 0.05 * reflet[..., None]
        # chiffres éteints « 88 » à peine visibles
        for x0 in prm.get("fantomes", ()):
            m = masque_sept_segments(8)
            v = echantillon(m, (x0[1] - p[..., 0]) / (x0[1] - x0[0]), (x0[3] - p[..., 2]) / (x0[3] - x0[2]))
            r = melange(r, col(48, 14, 12), v * 0.8)
    elif mat == "chiffre":
        m = masque_sept_segments(prm["chiffre"])
        x0, x1, z0, z1 = prm["cadre"]
        v = echantillon(m, (x1 - p[..., 0]) / (x1 - x0), (z1 - p[..., 2]) / (z1 - z0))  # lu depuis le joueur qui la tient
        r = melange(col(150, 20, 14), col(255, 70, 50), v)
        a = (v > 0.5).astype(float)
        g = col(255, 60, 40)[None, None, :] * (v[..., None] > 0.5)
    elif mat == "caoutchouc":
        r = melange(col(58, 56, 50), col(74, 72, 64), bruit(p, 10, 11))
        r = melange(r, col(110, 108, 98), usure * 0.5)
    elif mat == "bouton":
        r = melange(col(52, 54, 58), col(66, 68, 72), bruit(p, 12, 12))
        r = r * (1 - 0.35 * np.exp(-e / 0.06))[..., None]
    elif mat == "voyant_socle":
        r = np.broadcast_to(col(70, 12, 10), sh + (3,)).copy()
        r = r * (1 - 0.4 * np.exp(-e / 0.05))[..., None]
    elif mat == "voyant":
        c = np.exp(-((fu - 0.5) ** 2 + (fv - 0.5) ** 2) / 0.12)
        r = melange(col(200, 24, 18), col(255, 190, 170), c)
        g = melange(col(255, 40, 30), col(255, 200, 180), c)
    elif mat in ("fil_rouge", "fil_bleu", "fil_jaune"):
        c = {"fil_rouge": col(170, 26, 22), "fil_bleu": col(30, 70, 170), "fil_jaune": col(205, 170, 30)}[mat]
        reflet = np.exp(-((fv - 0.35) ** 2) / 0.02) if w > h else np.exp(-((fu - 0.35) ** 2) / 0.02)
        r = c * (0.75 + 0.25 * bruit(p, 8, 13))[..., None] + 0.25 * reflet[..., None]
    elif mat == "alu":
        brosse = bruit(p * np.array([1, 1, 14.0]), 5.0, 14)
        r = melange(col(150, 152, 156), col(205, 208, 212), brosse)
        sertis = np.abs(np.sin(p[..., 2] * 3.0)) > 0.97
        r = np.where(sertis[..., None], r * 0.7, r)
    elif mat == "antenne":
        r = melange(col(18, 18, 20), col(40, 40, 44), bruit(p, 8, 15))
    elif mat == "pointe":
        r = np.broadcast_to(col(190, 30, 24), sh + (3,)).copy()
    # ------------------------------------------------------------------------------------- charge explosive
    elif mat in ("jute", "jute_fonce", "jute_roule"):
        fils = (np.sin(p[..., 0] * 22 + fbm(p, 2, 2, 20) * 3) * 0.5 + 0.5) * \
               (np.sin(p[..., 2] * 22 + p[..., 1] * 22 + fbm(p, 2, 2, 21) * 3) * 0.5 + 0.5)
        clair = col(168, 132, 86) if mat == "jute" else col(132, 100, 62)
        r = melange(col(96, 70, 42), clair, 0.35 + 0.65 * fils)
        tache = np.clip((fbm(p, 0.45, 4, 22) - 0.52) * 4, 0, 1)
        r = melange(r, col(64, 46, 30), tache * 0.75)                # taches de graisse et de terre
        suie = np.clip((fbm(p, 0.9, 3, 23) - 0.62) * 5, 0, 1)
        r = melange(r, col(40, 34, 30), suie * 0.6)
        r = r * (1 - 0.25 * np.exp(-e / 0.25))[..., None]
        if mat == "jute_roule":
            r = r * (0.8 + 0.2 * (np.sin(p[..., 1] * 10) * 0.5 + 0.5))[..., None]
    elif mat == "corde":
        tors = np.sin((p[..., 0] + p[..., 1] + p[..., 2]) * 14) * 0.5 + 0.5
        r = melange(col(110, 86, 52), col(176, 146, 98), tors)
        r = r * (0.85 + 0.15 * bruit(p, 6, 24))[..., None]
    elif mat == "boite":
        cx, cy, ray = prm["axe"]
        rr = np.hypot(p[..., 0] - cx, p[..., 1] - cy) / ray
        rouille = np.clip((fbm(p, 0.9, 4, 25) - 0.42) * 3.2, 0, 1)
        metal = melange(col(120, 122, 118), col(160, 160, 152), bruit(p * np.array([4, 4, 1.0]), 3, 26))
        r = melange(metal, melange(col(120, 58, 26), col(78, 40, 22), fbm(p, 2.5, 3, 27)), rouille)
        if abs(n[2]) > 0.5:                                          # couvercle : bourrelet et anneaux
            anneau = np.exp(-((rr - 0.82) ** 2) / 0.004) + 0.6 * np.exp(-((rr - 0.55) ** 2) / 0.003)
            r = r * (1 - 0.35 * np.clip(anneau, 0, 1))[..., None]
        else:                                                        # cannelures et reste d'étiquette
            r = r * (0.88 + 0.12 * (np.sin(p[..., 2] * 7) * 0.5 + 0.5))[..., None]
            z0, z1 = prm["etiquette"]
            dans = (p[..., 2] > z0) & (p[..., 2] < z1) & (fbm(p, 1.4, 3, 28) > 0.45)
            papier = melange(col(176, 160, 120), col(120, 100, 70), fbm(p, 3, 2, 29))
            r = np.where(dans[..., None], papier, r)
    elif mat == "scotch":
        trame = (np.abs(np.sin(p[..., 0] * 18)) > 0.9) | (np.abs(np.sin(p[..., 2] * 18 + p[..., 1] * 18)) > 0.9)
        r = melange(col(126, 128, 130), col(176, 178, 182), bruit(p, 2.5, 30) * 0.6 + 0.2)
        r = np.where(trame[..., None], r * 0.9, r)
        plis = np.clip((fbm(p, 1.8, 2, 31) - 0.6) * 6, 0, 1)
        r = melange(r, col(210, 212, 214), plis * 0.5)
        r = melange(r, col(88, 88, 90), np.exp(-e / 0.08) * 0.6)    # bords déchirés plus sombres
    elif mat == "bouchon":
        r = melange(col(70, 52, 36), col(96, 76, 52), bruit(p, 9, 32))
    elif mat == "meche":
        tresse = np.sin(p[..., 2] * 26 + np.arctan2(p[..., 1] - 1.75, p[..., 0]) * 3) * 0.5 + 0.5
        r = melange(col(34, 26, 18), col(92, 70, 40), tresse)
    elif mat == "braise":
        c = bruit(p, 9, 33)
        r = melange(col(255, 120, 20), col(255, 230, 120), c)
        g = melange(col(255, 110, 20), col(255, 220, 110), c)
    else:
        raise ValueError(mat)
    return np.clip(r, 0, 1), a, np.clip(g, 0, 1)


# =============================================================================================== modèle
FACES = ("north", "south", "east", "west", "up", "down")
NORMALES = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0),
            "down": (0, -1, 0)}


class Cube:
    def __init__(self, os_, a, b, mat, faces=FACES, rot=None, pivot=None, prm=None, decals=()):
        self.os = os_
        self.a = np.array(a, float)
        self.b = np.array(b, float)
        self.mat = mat
        self.faces = faces
        self.rot = rot
        self.pivot = pivot
        self.prm = prm or {}
        self.decals = decals
        self.uv = {}

    def taille_face(self, f):
        s = self.b - self.a
        return {"north": (s[0], s[1]), "south": (s[0], s[1]), "east": (s[2], s[1]), "west": (s[2], s[1]),
                "up": (s[0], s[2]), "down": (s[0], s[2])}[f]

    def point(self, f, fu, fv):
        """Point de la face (avant rotation) pour fu, fv dans [0, 1] : placement GeckoLib (GeoQuad)."""
        (x0, y0, z0), (x1, y1, z1) = self.a, self.b
        X = lambda t: x0 + t * (x1 - x0)
        Y = lambda t: y1 - t * (y1 - y0)
        o = np.ones_like(fu)
        if f == "north":
            return np.stack([X(fu), Y(fv), z0 * o], -1)
        if f == "south":
            return np.stack([X(1 - fu), Y(fv), z1 * o], -1)
        if f == "west":
            return np.stack([x0 * o, Y(fv), z1 - fu * (z1 - z0)], -1)
        if f == "east":
            return np.stack([x1 * o, Y(fv), z0 + fu * (z1 - z0)], -1)
        if f == "up":
            return np.stack([X(fu), y1 * o, z1 - fv * (z1 - z0)], -1)
        return np.stack([X(fu), y0 * o, z0 + fv * (z1 - z0)], -1)


class Os:
    def __init__(self, nom, parent=None, pivot=(0, 0, 0), rot=None, brut=None):
        self.nom, self.parent, self.pivot, self.rot, self.brut = nom, parent, pivot, rot, brut


def rot_matrice(r):
    if r is None:
        return np.eye(3)
    ax, ay, az = (math.radians(v) for v in r)
    Rx = np.array([[1, 0, 0], [0, math.cos(ax), -math.sin(ax)], [0, math.sin(ax), math.cos(ax)]])
    Ry = np.array([[math.cos(ay), 0, math.sin(ay)], [0, 1, 0], [-math.sin(ay), 0, math.cos(ay)]])
    Rz = np.array([[math.cos(az), -math.sin(az), 0], [math.sin(az), math.cos(az), 0], [0, 0, 1]])
    return Rz @ Ry @ Rx


# ----------------------------------------------------------------------------------------------- C4
def modele_c4():
    os_ = [Os("c4"), Os("bundle", "c4"), Os("timer", "c4"), Os("led", "timer", (2.25, 4.35, 2.3)),
           Os("display", "timer"), Os("wiring", "c4")]
    cubes = []
    B = lambda *a, **k: cubes.append(Cube(*a, **k))
    # deux pains M112 sous film, inscriptions jaunes au pochoir
    etiq = masque_texte(["CHARGE, DEMOLITION", "M112  1.25 LB  COMP C-4"], 72)
    lot = masque_texte(["LOT HZ-07  ·  HERALIZ ARMORY"], 72)
    jaune = col(214, 190, 70)
    for x0, x1 in ((-5.4, -0.1), (0.1, 5.4)):
        dec = (Decal(etiq, (x0 + 0.35, 2.4, -2.1), (x1 - x0 - 0.7, 0, 0), (0, 0, 0.85), jaune, 0.9,
                     normale=(0, 1, 0)),
               Decal(etiq, (x1 - 0.45, 2.05, -3.9), (-(x1 - x0 - 0.9), 0, 0), (0, -0.95, 0), jaune, 0.9,
                     normale=(0, 0, -1)),
               Decal(lot, (x0 + 0.45, 1.55, 3.9), (x1 - x0 - 0.9, 0, 0), (0, -0.5, 0), jaune, 0.8,
                     normale=(0, 0, 1)))
        B("bundle", (x0, 0, -3.9), (x1, 2.4, 3.9), "m112", decals=dec)
    B("bundle", (-0.1, 0.1, -3.8), (0.1, 2.3, 3.8), "joint")
    # sangles (une sous le minuteur) et boucle
    for z0, z1 in ((-3.15, -2.15), (0.45, 1.45)):
        B("bundle", (-5.58, -0.12, z0), (5.58, 2.54, z1), "sangle")
    B("bundle", (-1.0, 2.54, -3.3), (0.7, 2.8, -2.0), "metal")
    B("bundle", (-0.85, 2.8, -2.8), (0.55, 2.9, -2.5), "metal")
    B("bundle", (0.7, 2.54, -3.05), (2.0, 2.62, -2.25), "sangle")
    # minuteur durci
    plaque = masque_texte(["HZ-7  DEMO TIMER"], 72)
    danger = Image.new("L", (400, 40), 0)
    dd = ImageDraw.Draw(danger)
    for k in range(-2, 22):
        dd.polygon([(k * 20, 40), (k * 20 + 10, 40), (k * 20 + 30, 0), (k * 20 + 20, 0)], fill=255)
    danger = np.asarray(danger, float) / 255
    B("timer", (-3.1, 2.4, -1.2), (3.1, 3.95, 3.0), "boitier",
      decals=(Decal(np.ones((4, 4)), (2.9, 3.05, -1.2), (-5.8, 0, 0), (0, -0.45, 0), col(200, 160, 30), 1.0,
                    normale=(0, 0, -1)),
              Decal(danger, (2.9, 3.05, -1.2), (-5.8, 0, 0), (0, -0.45, 0), col(20, 20, 20), 1.0,
                    normale=(0, 0, -1)),
              Decal(plaque, (2.4, 3.8, -1.2), (-4.8, 0, 0), (0, -0.6, 0), col(200, 204, 210), 0.9,
                    normale=(0, 0, -1))))
    B("timer", (-2.85, 3.95, -0.95), (2.85, 4.2, 2.75), "couvercle")
    fant = ((-2.2, -0.9, -0.4, 1.6), (-0.7, 0.6, -0.4, 1.6))
    B("timer", (-2.5, 4.2, -0.6), (0.75, 4.24, 1.8), "ecran", prm={"fantomes": fant})
    for cx in (-3.3, 2.7):
        for cz in (-1.4, 2.6):
            B("timer", (cx, 2.4, cz), (cx + 0.6, 4.08, cz + 0.6), "caoutchouc")
    symboles = ["+", "−", "▶", "■", "◀", "✓"]
    k = 0
    for z0 in (-0.5, 0.3, 1.1):
        for x0 in (1.05, 1.85):
            m = masque_texte([symboles[k]], 72)
            B("timer", (x0, 4.2, z0), (x0 + 0.55, 4.36, z0 + 0.5), "bouton",
              decals=(Decal(m, (x0 + 0.1, 4.36, z0 + 0.45), (0.35, 0, 0), (0, 0, -0.4), col(220, 220, 220), 0.9,
                            normale=(0, 1, 0)),))
            k += 1
    B("timer", (1.9, 4.2, 1.95), (2.6, 4.3, 2.65), "voyant_socle")
    B("led", (1.98, 4.25, 2.03), (2.52, 4.5, 2.57), "voyant")
    # antenne
    B("timer", (-2.6, 4.2, 2.0), (-2.0, 4.45, 2.6), "metal")
    B("timer", (-2.42, 4.45, 2.18), (-2.18, 5.75, 2.42), "antenne")
    B("timer", (-2.47, 5.75, 2.13), (-2.13, 5.95, 2.47), "pointe")
    # chiffres : un os par chiffre (C4Display montre celui des dizaines et celui des unités)
    for k in range(11):
        os_.append(Os(f"tens_{k}", "display"))
        os_.append(Os(f"ones_{k}", "display"))
        for nom, (x0, x1, z0, z1) in (("tens", fant[1]), ("ones", fant[0])):
            B(f"{nom}_{k}", (x0, 4.245, z0), (x1, 4.245, z1), "chiffre", faces=("up",),
              prm={"chiffre": k, "cadre": (x0, x1, z0, z1)})
    # détonateurs et fils
    for s, fil in ((-1, "fil_rouge"), (1, "fil_bleu")):
        xa, xb = sorted((s * 4.1, s * 4.6))
        B("wiring", (xa, 2.4, -0.9), (xb, 2.9, 1.7), "alu")
        xa, xb = sorted((s * 3.1, s * 4.35))
        B("wiring", (xa, 2.45, 1.7), (xb, 2.72, 1.97), fil)
        B("wiring", (s * 4.35 - 0.135, 2.45, 1.6), (s * 4.35 + 0.135, 2.72, 1.97), fil)
    B("wiring", (-0.3, 2.45, -2.1), (0.0, 2.7, -1.2), "fil_jaune")
    B("wiring", (-1.6, 2.45, -2.1), (-0.3, 2.7, -1.82), "fil_jaune")
    return os_, cubes


# ----------------------------------------------------------------------------------------------- charge explosive
FUSE_BEDROCK = [  # os de la mèche, gardés tels quels (ExplosiveChargeFuse : FUSE_PX = 3.4, braise au bout)
    {"name": "fuse_root", "parent": "charge", "pivot": [0.55, 1.75, 6.55], "rotation": [28, 14, 0]},
    {"name": "fuse", "parent": "fuse_root", "pivot": [0.55, 1.75, 6.55]},
    {"name": "ember", "parent": "fuse_root", "pivot": [0.55, 1.75, 9.95]},
]


def modele_charge():
    os_ = [Os("charge"), Os("sack", "charge"), Os("strap", "charge"), Os("cans", "charge")]
    for b in FUSE_BEDROCK:
        os_.append(Os(b["name"], b["parent"], (-b["pivot"][0], b["pivot"][1], b["pivot"][2]),
                      None if "rotation" not in b else (-b["rotation"][0], -b["rotation"][1], b["rotation"][2])))
    cubes = []
    B = lambda *a, **k: cubes.append(Cube(*a, **k))
    tnt = masque_texte(["TNT"], 120)
    tnt = np.asarray(Image.fromarray((tnt * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(2)), float) / 255
    rouge = col(150, 30, 22)
    B("sack", (-4.5, -0.1, -4.0), (4.5, 3.8, 1.5), "jute")
    B("sack", (-3.9, 0.0, -3.5), (3.9, 4.35, 1.0), "jute",
      decals=(Decal(tnt, (-1.9, 4.35, -1.15), (3.8, 0, 0), (0, 0, 2.0), rouge, 0.85, normale=(0, 1, 0)),))
    B("sack", (-3.9, 0.3, -4.55), (3.9, 3.4, -3.5), "jute")
    B("sack", (-5.0, 0.3, -3.5), (-4.5, 3.4, 1.0), "jute")
    B("sack", (4.5, 0.3, -3.5), (5.0, 3.4, 1.0), "jute")
    B("sack", (-3.4, 0.25, 1.5), (3.4, 3.3, 2.3), "jute_fonce")
    B("sack", (-3.0, 0.3, 2.3), (3.0, 3.1, 2.8), "jute_roule")
    B("sack", (-3.55, 1.45, 1.6), (3.55, 1.85, 2.2), "corde")
    B("sack", (2.2, 1.2, 2.0), (2.9, 2.1, 2.7), "corde")
    B("sack", (2.4, 0.15, 2.35), (2.7, 1.3, 2.65), "corde")
    B("sack", (2.0, 0.15, 2.4), (2.25, 1.0, 2.65), "corde")
    # poignée de corde
    B("strap", (-2.8, 4.35, -1.7), (2.8, 4.65, -1.3), "corde")
    B("strap", (-2.8, 3.6, -1.7), (-2.5, 4.65, -1.3), "corde")
    B("strap", (2.5, 3.6, -1.7), (2.8, 4.65, -1.3), "corde")
    # boîtes de conserve rouillées (prismes à 6 pans : 3 pavés tournés autour de leur axe)
    boites = (((-0.55, 1.75), 1.1, 2.2, 6.55), ((1.55, 1.6), 0.95, 2.2, 5.9), ((-2.55, 1.45), 0.88, 2.2, 5.4))
    for (cx, cy), ap, z0, z1 in boites:
        demi = ap * math.tan(math.radians(30))
        for ang in (0, 60, 120):
            B("cans", (cx - ap, cy - demi, z0), (cx + ap, cy + demi, z1), "boite", rot=(0, 0, ang),
              pivot=(cx, cy, z0), prm={"axe": (cx, cy, ap / math.cos(math.radians(30))),
                                        "etiquette": (z0 + 0.9, z0 + 2.2)})
    # scotch autour des trois boîtes
    B("cans", (-3.55, 0.45, 4.0), (2.62, 2.95, 4.65), "scotch")
    B("cans", (-3.55, 0.45, 2.85), (2.62, 2.95, 3.25), "scotch")
    # mèche (mêmes cubes qu'avant, en coordonnées Bedrock)
    brut = {
        "fuse_root": ((0.13, 1.33, 6.5), (0.84, 0.84, 0.5), "bouchon"),
        "fuse": ((0.29, 1.49, 6.55), (0.52, 0.52, 3.4), "meche"),
        "ember": ((0.19, 1.39, 9.5), (0.72, 0.72, 0.65), "braise"),
    }
    for nom, (o, s, mat) in brut.items():
        B(nom, (-(o[0] + s[0]), o[1], o[2]), (-o[0], o[1] + s[1], o[2] + s[2]), mat)
    return os_, cubes


# =============================================================================================== atlas, geo
def construire(os_, cubes):
    rects = []
    for c in cubes:
        for f in c.faces:
            w, h = c.taille_face(f)
            tw, th = max(1, int(math.ceil(w * D))), max(1, int(math.ceil(h * D)))
            rects.append((c, f, tw, th))
    rects.sort(key=lambda r: -r[3])
    x = y = rangee = 0
    places = []
    for c, f, tw, th in rects:
        W, H = tw + 2 * MARGE, th + 2 * MARGE
        if x + W > LARGEUR:
            x, y, rangee = 0, y + rangee, 0
        places.append((c, f, tw, th, x + MARGE, y + MARGE))
        x += W
        rangee = max(rangee, H)
    haut = 1 << int(math.ceil(math.log2(y + rangee)))
    rgb = np.zeros((haut, LARGEUR, 3))
    alpha = np.zeros((haut, LARGEUR))
    glow = np.zeros((haut, LARGEUR, 3))
    for c, f, tw, th, px, py in places:
        w, h = c.taille_face(f)
        # points de la face, marge comprise (débord), au centre de chaque point de texture
        uu = (np.arange(-MARGE, tw + MARGE) + 0.5) / tw
        vv = (np.arange(-MARGE, th + MARGE) + 0.5) / th
        fu, fv = np.meshgrid(np.clip(uu, 0, 1), np.clip(vv, 0, 1))
        p = c.point(f, fu, fv)
        n = np.array(NORMALES[f], float)
        r, a, g = peindre(c.mat, p, n, fu, fv, max(w, 1e-3), max(h, 1e-3), c.prm)
        for d in c.decals:
            d.poser(p, n, r, g)
        sl = (slice(py - MARGE, py + th + MARGE), slice(px - MARGE, px + tw + MARGE))
        rgb[sl], alpha[sl], glow[sl] = r, a, g
        c.uv[f] = (px / D, py / D, w, h)
    tex = np.dstack([rgb, alpha])
    lueur = np.dstack([glow, (glow.max(-1) > 0.02).astype(float)])
    return tex, lueur, LARGEUR / D, haut / D


def geo(ident, os_, cubes, tw, th, bornes):
    bones = []
    for o in os_:
        b = {"name": o.nom}
        if o.parent:
            b["parent"] = o.parent
        b["pivot"] = [round(-o.pivot[0], 4), round(o.pivot[1], 4), round(o.pivot[2], 4)]
        if o.rot:
            b["rotation"] = [round(-o.rot[0], 4), round(-o.rot[1], 4), round(o.rot[2], 4)]
        lst = []
        for c in cubes:
            if c.os != o.nom:
                continue
            s = c.b - c.a
            e = {"origin": [round(-c.b[0], 4), round(c.a[1], 4), round(c.a[2], 4)],
                 "size": [round(s[0], 4), round(s[1], 4), round(s[2], 4)]}
            if c.rot:
                e["pivot"] = [round(-c.pivot[0], 4), round(c.pivot[1], 4), round(c.pivot[2], 4)]
                e["rotation"] = [round(-c.rot[0], 4), round(-c.rot[1], 4), round(c.rot[2], 4)]
            e["uv"] = {f: {"uv": [round(u, 5), round(v, 5)], "uv_size": [round(w, 5), round(h, 5)]}
                       for f, (u, v, w, h) in c.uv.items()}
            lst.append(e)
        if lst:
            b["cubes"] = lst
        bones.append(b)
    return {"format_version": "1.12.0", "minecraft:geometry": [{
        "description": {"identifier": ident, "texture_width": tw, "texture_height": th,
                        "visible_bounds_width": bornes[0], "visible_bounds_height": bornes[1],
                        "visible_bounds_offset": [0, 0.5, 0]},
        "bones": bones}]}


# =============================================================================================== rendu d'aperçu
def matrices_os(os_):
    par = {o.nom: o for o in os_}
    cache = {}

    def m(nom):
        if nom in cache:
            return cache[nom]
        o = par[nom]
        R = rot_matrice(o.rot)
        pv = np.array(o.pivot, float)
        M = np.eye(4)
        M[:3, :3] = R
        M[:3, 3] = pv - R @ pv
        if o.parent:
            M = m(o.parent) @ M
        cache[nom] = M
        return M

    return m


def rendu(os_, cubes, tex, lueur, cache_os, vue, taille=900, allume=True):
    m_os = matrices_os(os_)
    yaw, pitch = vue
    cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
    Rv = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
    Rp = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
    V = Rp @ Rv
    L = np.array([0.35, 0.85, -0.4])
    L /= np.linalg.norm(L)
    tris = []
    for c in cubes:
        if c.os in cache_os:
            continue
        M = m_os(c.os)
        Rc = rot_matrice(c.rot)
        pv = np.array(c.pivot if c.pivot else (0, 0, 0), float)
        for f in c.faces:
            u0, v0, w, h = c.uv[f]
            coins = c.point(f, np.array([0, 1, 1, 0.0]), np.array([0, 0, 1, 1.0]))
            if c.rot:
                coins = (coins - pv) @ Rc.T + pv
            coins = coins @ M[:3, :3].T + M[:3, 3]
            n = np.cross(coins[1] - coins[0], coins[3] - coins[0])
            nn = np.linalg.norm(n)
            n = n / nn if nn > 1e-9 else np.array(NORMALES[f], float)
            if np.dot(n, NORMALES[f]) < 0 and not c.rot and c.os == c.os:
                n = -n
            uv = np.array([[u0, v0], [u0 + w, v0], [u0 + w, v0 + h], [u0, v0 + h]]) * D
            tris.append((coins, uv, n))
    pts = np.concatenate([t[0] for t in tris])
    pv_ = pts @ V.T
    cx, cy_ = (pv_[:, 0].max() + pv_[:, 0].min()) / 2, (pv_[:, 1].max() + pv_[:, 1].min()) / 2
    ech = taille * 0.82 / max(np.ptp(pv_[:, 0]), np.ptp(pv_[:, 1]))
    img = np.zeros((taille, taille, 3)) + np.array([0.11, 0.12, 0.13])
    zb = np.full((taille, taille), np.inf)
    H, W = tex.shape[:2]
    for coins, uv, n in tris:
        q = coins @ V.T
        sxy = np.stack([(q[:, 0] - cx) * ech + taille / 2, taille / 2 - (q[:, 1] - cy_) * ech, -q[:, 2]], -1)
        nv = V @ n
        lum = 0.45 + 0.6 * max(0.0, float(n @ L)) + 0.1 * max(0.0, n[1])
        for idx in ((0, 1, 2), (0, 2, 3)):
            P = sxy[list(idx)]
            T = uv[list(idx)]
            x0, x1 = int(max(0, np.floor(P[:, 0].min()))), int(min(taille - 1, np.ceil(P[:, 0].max())))
            y0, y1 = int(max(0, np.floor(P[:, 1].min()))), int(min(taille - 1, np.ceil(P[:, 1].max())))
            if x1 < x0 or y1 < y0:
                continue
            X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            (ax, ay, az), (bx, by, bz), (qx, qy, qz) = P
            den = (by - qy) * (ax - qx) + (qx - bx) * (ay - qy)
            if abs(den) < 1e-9:
                continue
            l1 = ((by - qy) * (X - qx) + (qx - bx) * (Y - qy)) / den
            l2 = ((qy - ay) * (X - qx) + (ax - qx) * (Y - qy)) / den
            l3 = 1 - l1 - l2
            dedans = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
            if not dedans.any():
                continue
            z = l1 * az + l2 * bz + l3 * qz
            tu = np.clip(l1 * T[0, 0] + l2 * T[1, 0] + l3 * T[2, 0], 0, W - 1).astype(int)
            tv = np.clip(l1 * T[0, 1] + l2 * T[1, 1] + l3 * T[2, 1], 0, H - 1).astype(int)
            texel = tex[tv, tu]
            ok = dedans & (texel[..., 3] > 0.5) & (z < zb[y0:y1 + 1, x0:x1 + 1])
            couleur = texel[..., :3] * lum
            if allume:
                couleur = np.maximum(couleur, lueur[tv, tu, :3])
            zb[y0:y1 + 1, x0:x1 + 1] = np.where(ok, z, zb[y0:y1 + 1, x0:x1 + 1])
            img[y0:y1 + 1, x0:x1 + 1] = np.where(ok[..., None], couleur, img[y0:y1 + 1, x0:x1 + 1])
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    return im.resize((taille // 2, taille // 2), Image.LANCZOS)


VUES = {"trois_quarts": (math.radians(-35), math.radians(32)), "dessus": (0.0, math.radians(80)),
        "face": (0.0, math.radians(8)), "dos": (math.radians(160), math.radians(25))}


def planche(images, titres, fichier):
    w, h = images[0].size
    out = Image.new("RGB", (w * len(images), h + 34), (24, 26, 28))
    d = ImageDraw.Draw(out)
    f = ImageFont.truetype(str(POLICE), 20)
    for k, (im, t) in enumerate(zip(images, titres)):
        out.paste(im, (k * w, 34))
        d.text((k * w + 10, 6), t, font=f, fill=(220, 220, 220))
    out.save(fichier)


def generer():
    res = {}
    for nom, fab, ident, bornes in (("c4", modele_c4, "geometry.crac.c4", (2, 2)),
                                     ("explosive_charge", modele_charge, "geometry.crac.explosive_charge", (2, 2))):
        os_, cubes = fab()
        tex, lueur, tw, th = construire(os_, cubes)
        res[nom] = (os_, cubes, tex, lueur, geo(ident, os_, cubes, tw, th, bornes))
    return res


def png(a, fichier):
    Image.fromarray((np.clip(a, 0, 1) * 255).astype(np.uint8), "RGBA").save(fichier, optimize=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apercu", type=Path)
    ap.add_argument("--installer", action="store_true")
    args = ap.parse_args()
    res = generer()
    if args.apercu:
        dos = args.apercu
        dos.mkdir(parents=True, exist_ok=True)
        cache = {"c4": {f"{n}_{k}" for n in ("tens", "ones") for k in range(11)} - {"tens_0", "ones_7"},
                 "explosive_charge": set()}
        tout = []
        for nom, (os_, cubes, tex, lueur, g) in res.items():
            ims = [rendu(os_, cubes, tex, lueur, cache[nom], v) for v in VUES.values()]
            planche(ims, list(VUES), dos / f"{nom}_vues.png")
            tout.append(ims[0])
            png(tex, dos / f"{nom}.png")
            png(lueur, dos / f"{nom}_glowmask.png")
            (dos / f"{nom}.geo.json").write_text(json.dumps(g, indent=1), encoding="utf-8")
        planche(tout, ["C4 (militaire)", "Charge explosive (artisanale)"], dos / "comparaison.png")
        print("aperçus dans", dos)
    if args.installer:
        for nom, (os_, cubes, tex, lueur, g) in res.items():
            (ASSETS / "geo" / f"{nom}.geo.json").write_text(json.dumps(g, indent=1) + "\n", encoding="utf-8")
            png(tex, ASSETS / "textures" / "entity" / f"{nom}.png")
            png(lueur, ASSETS / "textures" / "entity" / f"{nom}_glowmask.png")
        print("installé dans", ASSETS)


if __name__ == "__main__":
    main()
