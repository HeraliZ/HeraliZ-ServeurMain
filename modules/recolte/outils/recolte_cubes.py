"""Outils de la récolte en cubes, comme les armes (modèles Blockbench / Bedrock de GeckoLib) : hache, pioche et
hache-pioche légendaire.

Choix du propriétaire (15/11) : des modèles en cubes plutôt qu'en maillage lisse, pour peser le moins possible chez les
joueurs (quelques centaines de faces au lieu de plusieurs milliers). Le serveur, lui, ne dessine jamais rien : pour lui un
outil n'est qu'un objet et son usure.

Chaque cube : centre, taille, rotation quelconque (comme dans Blockbench en Bedrock), une matière ; chaque face a sa
place dans la texture (UV par face), peinte depuis la position 3D (outils de recolte_moteur). Mesures en mètres,
manche vers +y, tranchant vers +x, épaisseur en z.
"""
from __future__ import annotations

import math

import numpy as np

import recolte_moteur as mo
from recolte_moteur import Morceau, fbm3, lisse, melange, perlin3, rgb, voronoi3

DENSITE = 420          # points de texture par mètre (une tête de hache de 15 cm : environ 60 points)


NOMS_FACES = {(0, 1): "east", (0, -1): "west", (1, 1): "up", (1, -1): "down", (2, 1): "south", (2, -1): "north"}


class Cube:
    """Un cube de Blockbench : centre, taille, rotation autour du centre, matière (peinte depuis la position 3D) ou image
    peinte (feuillage), groupe (os du modèle : « souche », « tronc »…). Une taille nulle sur un axe = un plan (feuillage),
    comme dans Blockbench."""

    def __init__(self, centre, taille, R=None, matiere="acier", groupe="outil", faces=None, image=None, extra=None,
                 densite=None, nom=None):
        self.c = np.asarray(centre, float)
        self.s = np.asarray(taille, float)
        self.R = np.eye(3) if R is None else np.asarray(R, float)
        self.matiere = matiere
        self.groupe = groupe
        self.faces = faces or {}
        self.image = image
        self.extra = extra or {}
        self.densite = densite
        self.nom = nom

    def morceaux(self, densite=DENSITE):
        d = self.densite or densite
        out = []
        for a in range(3):
            for sg in (1, -1):
                n = np.zeros(3)
                n[a] = sg
                if a == 1:
                    ua, va = 0, 2
                else:
                    ua, va = (2 if a == 0 else 0), 1
                if self.s[ua] < 1e-7 or self.s[va] < 1e-7:
                    continue                      # face sans surface (plan)
                tu, tv = np.zeros(3), np.zeros(3)
                tu[ua], tv[va] = 1, 1
                c = n * self.s[a] / 2
                hu, hv = self.s[ua] / 2, self.s[va] / 2
                loc = [c - tu * hu - tv * hv, c + tu * hu - tv * hv, c + tu * hu + tv * hv, c - tu * hu + tv * hv]
                pts = np.array([self.R @ p + self.c for p in loc])
                nw = self.R @ n
                tri = np.array([[0, 1, 2], [0, 2, 3]])
                if np.dot(np.cross(pts[1] - pts[0], pts[2] - pts[0]), nw) < 0:
                    tri = tri[:, [0, 2, 1]]
                uv = np.array([[0, 1], [1, 1], [1, 0], [0, 0]], float)
                tw = max(2, int(round(self.s[ua] * d)))
                th = max(2, int(round(self.s[va] * d)))
                nom_face = NOMS_FACES[(a, sg)]
                m = Morceau(pts, tri, uv, self.faces.get(nom_face, self.matiere), (tw, th), nor=np.tile(nw, (4, 1)),
                            extra={k: np.tile(np.atleast_1d(np.asarray(v, float)), (4, 1)) for k, v in self.extra.items()})
                m.groupe = self.groupe
                m.ao = np.ones(4)
                m.face = nom_face
                m.local_u, m.local_v = tu, tv
                m.cube = self
                m.image = self.image
                m.apercu = not (self.s[a] < 1e-7 and sg < 0)     # un plan : une seule face dans les aperçus (deux faces)
                if self.image is not None:
                    m.decoupe = True
                    m.occulte = False
                out.append(m)
        return out


def repere(y, ref):
    """Rotation dont l'axe y local suit y, l'axe x local au plus près de ref."""
    y = np.asarray(y, float)
    y /= np.linalg.norm(y)
    x = np.asarray(ref, float) - np.dot(ref, y) * y
    x /= np.linalg.norm(x)
    z = np.cross(x, y)
    return np.stack([x, y, z], 1)


def rot(axe, deg):
    return mo.rotation(axe, math.radians(deg))


def segment(p0, p1, sx, sz, ref=(1, 0, 0), matiere="bois", jumeau=True, deborde=0.002):
    """Cube allongé de p0 à p1 (section sx x sz) ; jumeau : le même tourné de 45° autour de son axe (section à 8 pans,
    l'astuce des modèles Blockbench pour un manche rond)."""
    p0, p1 = np.asarray(p0, float), np.asarray(p1, float)
    L = np.linalg.norm(p1 - p0) + deborde
    R = repere(p1 - p0, ref)
    c = (p0 + p1) / 2
    out = [Cube(c, (sx, L, sz), R, matiere)]
    if jumeau:
        m = min(sx, sz)
        out.append(Cube(c, (m, L - 0.0005, m), R @ rot((0, 1, 0), 45), matiere))
    return out


def manche(points, largeurs, epaisseurs, matiere, ref=(1, 0, 0)):
    out = []
    for i in range(len(points) - 1):
        out += segment(points[i], points[i + 1], (largeurs[i] + largeurs[i + 1]) / 2,
                       (epaisseurs[i] + epaisseurs[i + 1]) / 2, ref, matiere)
    return out


# ============================================================================================================ outils

HACHE_YH = 0.667
PIOCHE_YH = 0.825
LEGENDAIRE_YH = 0.74


def hache_cubes():
    """Hache de bûcheron (72 cm) : manche en hickory à 8 pans, ruban noir, tête forgée à joues, tranchant affûté."""
    cubes = []
    t = np.linspace(0, 1, 9)
    L = 0.715
    x = 0.010 * np.sin(t * math.pi * 1.2) - 0.008 * t
    x -= x[-1] * t ** 3
    pts = np.stack([x, t * L, np.zeros_like(t)], 1)
    w = 0.029 + 0.006 * t
    d = 0.022 + 0.004 * t
    w[0], d[0] = 0.038, 0.03                                  # bout renflé
    cubes += manche(pts, w, d, "bois")
    # ruban
    tr = np.linspace(0.05, 0.3, 4)
    pr = np.stack([np.interp(tr, t, x), tr * L, np.zeros_like(tr)], 1)
    cubes += manche(pr, np.interp(tr, t, w) + 0.004, np.interp(tr, t, d) + 0.004, "ruban")
    yh = HACHE_YH
    cubes += [Cube((-0.006, yh, 0), (0.074, 0.064, 0.036), matiere="acier_hache"),         # oeil
              Cube((-0.046, yh, 0), (0.012, 0.054, 0.03), matiere="acier_hache"),          # talon
              Cube((0.042, yh - 0.002, 0), (0.026, 0.058, 0.029), matiere="acier_hache"),  # joue
              Cube((0.066, yh - 0.005, 0), (0.026, 0.074, 0.022), matiere="acier_hache"),
              Cube((0.092, yh - 0.008, 0), (0.028, 0.094, 0.015), matiere="acier_hache"),
              Cube((0.117, yh - 0.009, 0), (0.024, 0.112, 0.009), matiere="acier_hache"),
              Cube((0.114, yh + 0.047, 0), (0.052, 0.014, 0.009), rot((0, 0, 1), 18), "acier_hache"),   # pointe haute
              Cube((0.105, yh - 0.066, 0), (0.07, 0.016, 0.009), rot((0, 0, 1), -30), "acier_hache"),  # barbe
              Cube((0.1365, yh - 0.006, 0), (0.014, 0.08, 0.0042), matiere="tranchant"),
              Cube((0.133, yh + 0.044, 0), (0.014, 0.032, 0.0042), rot((0, 0, 1), 16), "tranchant"),
              Cube((0.131, yh - 0.057, 0), (0.014, 0.036, 0.0042), rot((0, 0, 1), -18), "tranchant"),
              Cube((-0.004, yh + 0.036, 0), (0.028, 0.009, 0.019), matiere="bois_bout"),         # bout du manche
              Cube((-0.004, yh + 0.0365, 0), (0.026, 0.0095, 0.004), matiere="coin")]           # coin d'acier
    return cubes


def pioche_cubes():
    """Pioche (86 cm) : manche droit à 8 pans qui s'épaissit, tissu kaki, collier, tête courbe (pointe carrée d'un côté,
    tranchant plat de l'autre)."""
    cubes = []
    L = 0.86
    t = np.linspace(0, 1, 9)
    pts = np.stack([np.zeros_like(t), t * L, np.zeros_like(t)], 1)
    w = 0.03 + 0.012 * t ** 1.5
    d = 0.024 + 0.01 * t ** 1.5
    w[0], d[0] = 0.04, 0.032
    cubes += manche(pts, w, d, "bois")
    tr = np.linspace(0.04, 0.26, 4)
    pr = np.stack([np.zeros_like(tr), tr * L, np.zeros_like(tr)], 1)
    cubes += manche(pr, np.interp(tr, t, w) + 0.005, np.interp(tr, t, d) + 0.005, "tissu")
    yh = PIOCHE_YH
    cubes += [Cube((0, yh, 0), (0.052, 0.086, 0.048), matiere="acier_pioche"),
              Cube((0, yh, 0), (0.046, 0.08, 0.046), rot((0, 1, 0), 45), "acier_pioche"),
              Cube((0, yh + 0.047, 0), (0.03, 0.01, 0.024), matiere="bois_bout"),
              Cube((0, yh + 0.0475, 0), (0.028, 0.0105, 0.004), matiere="coin")]
    n = 7
    for cote in (-1, 1):
        ks = np.linspace(0, 1, n + 1)
        for i in range(n):
            k0, k1 = ks[i], ks[i + 1]
            p0 = np.array([cote * 0.28 * k0, yh + 0.012 - 0.07 * k0 ** 2, 0])
            p1 = np.array([cote * 0.28 * k1, yh + 0.012 - 0.07 * k1 ** 2, 0])
            km = (k0 + k1) / 2
            if cote < 0:          # pointe : section carrée qui s'affine
                s = 0.036 * (1 - km ** 1.5) + 0.006
                cubes += segment(p0, p1, s, s, (0, 1, 0), "acier_pioche", jumeau=True)
            else:                 # tranchant plat : de plus en plus mince, garde sa largeur
                e = 0.036 * (1 - km) ** 1.1 + 0.005
                lz = 0.036 + 0.004 * km
                cubes += segment(p0, p1, e, lz, (0, 1, 0), "acier_pioche", jumeau=False)
    return cubes


def legendaire_cubes():
    """Hache-pioche de survie, LÉGENDAIRE (80 cm) : une seule main pour le bois et la pierre. Manche en composite noir
    à 8 pans, bague en laiton, poignée en paracorde noir et or ; tête noircie à l'oeil gravé d'un filet doré, lame de
    hache d'un côté, pic courbe de l'autre, arêtes polies."""
    cubes = []
    L = 0.79
    t = np.linspace(0, 1, 9)
    pts = np.stack([np.zeros_like(t), t * L, np.zeros_like(t)], 1)
    w = 0.03 + 0.008 * t
    d = 0.024 + 0.006 * t
    w[0], d[0] = 0.04, 0.034
    cubes += manche(pts, w, d, "composite")
    tr = np.linspace(0.045, 0.3, 5)
    pr = np.stack([np.zeros_like(tr), tr * L, np.zeros_like(tr)], 1)
    cubes += manche(pr, np.interp(tr, t, w) + 0.005, np.interp(tr, t, d) + 0.005, "paracorde")
    for yb in (0.025, 0.32):                                   # bagues en laiton
        cubes += segment((0, yb, 0), (0, yb + 0.012, 0), 0.044, 0.036, (1, 0, 0), "laiton", deborde=0.0)
    cubes += segment((0, 0.66, 0), (0, 0.69, 0), 0.046, 0.038, (1, 0, 0), "laiton", deborde=0.0)
    yh = LEGENDAIRE_YH
    cubes += [Cube((0, yh, 0), (0.07, 0.07, 0.04), matiere="acier_noir"),
              Cube((0, yh + 0.026, 0), (0.072, 0.006, 0.042), matiere="laiton"),       # filets dorés
              Cube((0, yh - 0.026, 0), (0.072, 0.006, 0.042), matiere="laiton"),
              # lame de hache (+x)
              Cube((0.046, yh - 0.002, 0), (0.026, 0.06, 0.026), matiere="acier_noir"),
              Cube((0.07, yh - 0.005, 0), (0.026, 0.078, 0.018), matiere="acier_noir"),
              Cube((0.094, yh - 0.007, 0), (0.024, 0.096, 0.011), matiere="acier_noir"),
              Cube((0.093, yh + 0.042, 0), (0.044, 0.013, 0.009), rot((0, 0, 1), 18), "acier_noir"),
              Cube((0.087, yh - 0.056, 0), (0.056, 0.015, 0.009), rot((0, 0, 1), -28), "acier_noir"),
              Cube((0.112, yh - 0.006, 0), (0.013, 0.072, 0.0042), matiere="tranchant"),
              Cube((0.109, yh + 0.04, 0), (0.013, 0.028, 0.0042), rot((0, 0, 1), 16), "tranchant"),
              Cube((0.107, yh - 0.05, 0), (0.013, 0.032, 0.0042), rot((0, 0, 1), -18), "tranchant"),
              Cube((0, yh + 0.04, 0), (0.03, 0.01, 0.024), matiere="composite_bout")]
    # pic courbe (-x)
    n = 5
    ks = np.linspace(0, 1, n + 1)
    for i in range(n):
        k0, k1 = ks[i], ks[i + 1]
        p0 = np.array([-0.035 - 0.2 * k0, yh + 0.004 - 0.075 * k0 ** 2, 0])
        p1 = np.array([-0.035 - 0.2 * k1, yh + 0.004 - 0.075 * k1 ** 2, 0])
        km = (k0 + k1) / 2
        s = 0.032 * (1 - km ** 1.3) + 0.005
        cubes += segment(p0, p1, s, s * 0.9, (0, 1, 0), "acier_noir" if km < 0.75 else "tranchant", jumeau=True)
    return cubes


OUTILS = {"hache": hache_cubes, "pioche": pioche_cubes, "legendaire": legendaire_cubes}
POINTES = {"hache": np.array([0.143, HACHE_YH, 0.0]), "pioche": np.array([-0.28, PIOCHE_YH - 0.058, 0.0]),
           "legendaire": np.array([0.118, LEGENDAIRE_YH, 0.0])}
POINTES_PIC = {"legendaire": np.array([-0.235, LEGENDAIRE_YH - 0.071, 0.0])}


# ============================================================================================================ matières

def _bord(ctxm, n_pts, clair=0.14, sombre=0.0):
    """Liseré clair au bord de chaque face (arête usée), comme les textures peintes des armes."""
    uv = ctxm.get("uv_local")
    tc = ctxm.get("taille_carte")
    if uv is None or tc is None:
        return np.ones(n_pts)
    du = np.minimum(uv[:, 0], 1 - uv[:, 0]) * tc[0]
    dv = np.minimum(uv[:, 1], 1 - uv[:, 1]) * tc[1]
    b = np.minimum(du, dv)
    return 1 + clair * (b < 1.0) + 0.05 * ((b >= 1.0) & (b < 2.0)) - sombre


def mat_bois(p, n, ctxm):
    c = melange(rgb("#9A673A"), rgb("#BD8650"), lisse(-0.5, 0.6, fbm3(p, 25.0, 3, 301)))
    fil = np.abs(perlin3(p * np.array([220.0, 9.0, 220.0]) + 0.3 * fbm3(p, 40.0, 2, 302)[:, None], 303))
    c = melange(c, rgb("#6E4524"), (1 - lisse(0.0, 0.1, fil)) * 0.55)
    y = p[:, 1]
    crasse = (1 - lisse(0.04, 0.32, y)) * 0.3
    c = melange(c, rgb("#3E2C1E"), crasse * lisse(-0.3, 0.6, fbm3(p, 50.0, 3, 305) + 0.3))
    return c * _bord(ctxm, len(p))[:, None]


def mat_bois_bout(p, n, ctxm):
    r = np.sqrt(p[:, 0] ** 2 + p[:, 2] ** 2)
    c = melange(rgb("#C8A272"), rgb("#9A7046"), 0.5 + 0.5 * np.cos(r * 900))
    return c * _bord(ctxm, len(p), 0.0)[:, None]


def _rayures(p, y_pas, couleur_a, couleur_b, decal=0.25):
    s = (p[:, 1] / y_pas + decal * (p[:, 0] + p[:, 2]) / 0.02) % 1.0
    bord = 1 - lisse(0.0, 0.12, np.minimum(s, 1 - s))
    return melange(couleur_a, couleur_b, bord)


def mat_ruban(p, n, ctxm):
    c = melange(rgb("#1D1E20"), rgb("#2B2C2F"), lisse(-0.5, 0.5, fbm3(p, 80.0, 2, 321)))
    c = melange(c, rgb("#4A4C50"), (_rayures(p, 0.011, np.zeros(3), np.ones(3))[:, 0]) * 0.7)
    return melange(c, rgb("#5A5650"), lisse(0.3, 0.7, fbm3(p, 50.0, 3, 322)) * 0.25)


def mat_tissu(p, n, ctxm):
    c = melange(rgb("#4A4F35"), rgb("#5E6443"), lisse(-0.5, 0.5, fbm3(p, 60.0, 2, 331)))
    trame = 0.5 + 0.5 * np.sin(p[:, 1] * 2400) * np.sin((p[:, 0] + p[:, 2]) * 2400)
    c *= (0.92 + 0.12 * trame)[:, None]
    c = melange(c, rgb("#2A2B22"), _rayures(p, 0.016, np.zeros(3), np.ones(3))[:, 0] * 0.6)
    return melange(c, rgb("#3A2F24"), lisse(0.2, 0.6, fbm3(p, 30.0, 3, 332)) * 0.35)


def _acier(p, base):
    c = melange(base, base * 1.35, lisse(-0.4, 0.6, fbm3(p, 30.0, 3, 341)))
    c *= (0.9 + 0.16 * lisse(0.2, 0.6, fbm3(p, 90.0, 2, 342)))[:, None]
    ray = 1 - lisse(0.0, 0.025, np.abs(perlin3(p * np.array([400.0, 40.0, 400.0]) + 3.0, 347)))
    c = melange(c, base * 2.1, ray * lisse(0.2, 0.6, fbm3(p, 25.0, 2, 348)) * 0.5)
    rouille = lisse(0.15, 0.45, fbm3(p, 18.0, 4, 345))
    return melange(c, melange(rgb("#5A321C"), rgb("#865530"), lisse(-0.3, 0.6, fbm3(p, 120.0, 2, 346))), rouille * 0.45)


def _vif(p):
    stries = np.abs(perlin3(p * np.array([30.0, 700.0, 60.0]), 353))
    return melange(rgb("#9DA3AA"), rgb("#D6DBE0"), lisse(0.0, 0.5, stries))


def mat_acier_hache(p, n, ctxm):
    c = _acier(p, rgb("#3A3E43"))
    c = melange(c, _vif(p), lisse(0.112, 0.126, p[:, 0]))
    return c * _bord(ctxm, len(p), 0.25)[:, None]


def mat_acier_pioche(p, n, ctxm):
    c = _acier(p, rgb("#383B40"))
    c = melange(c, _vif(p), lisse(0.17, 0.25, np.abs(p[:, 0])) * 0.9)
    return c * _bord(ctxm, len(p), 0.25)[:, None]


def mat_tranchant(p, n, ctxm):
    return _vif(p) * _bord(ctxm, len(p), 0.08)[:, None]


def mat_coin(p, n, ctxm):
    return melange(rgb("#55595E"), rgb("#7E848A"), lisse(-0.5, 0.5, fbm3(p, 200.0, 2, 371)))


def mat_acier_noir(p, n, ctxm):
    """Acier noirci (revêtement mat), usé aux arêtes : l'outil légendaire."""
    c = melange(rgb("#17181B"), rgb("#24262A"), lisse(-0.4, 0.6, fbm3(p, 40.0, 3, 381)))
    use = lisse(0.3, 0.7, fbm3(p, 60.0, 3, 382)) * 0.35
    c = melange(c, rgb("#4A4E54"), use)
    c = melange(c, _vif(p), lisse(0.088, 0.104, p[:, 0]) * 0.8)
    return c * _bord(ctxm, len(p), 0.35)[:, None]


def mat_laiton(p, n, ctxm):
    c = melange(rgb("#A9822F"), rgb("#D8B45A"), lisse(-0.4, 0.6, fbm3(p, 60.0, 3, 391)))
    c = melange(c, rgb("#5E4A22"), lisse(0.35, 0.7, fbm3(p, 120.0, 2, 392)) * 0.35)
    return c * _bord(ctxm, len(p), 0.12)[:, None]


def mat_composite(p, n, ctxm):
    c = melange(rgb("#1C1D1F"), rgb("#2A2B2E"), lisse(-0.5, 0.5, fbm3(p, 40.0, 2, 401)))
    tissage = 0.5 + 0.5 * np.sign(np.sin(p[:, 1] * 1600) * np.sin((p[:, 0] + p[:, 2]) * 1600))
    c *= (0.94 + 0.08 * tissage)[:, None]
    c = melange(c, rgb("#45474B"), lisse(0.35, 0.75, fbm3(p, 70.0, 3, 402)) * 0.3)
    return c * _bord(ctxm, len(p), 0.18)[:, None]


def mat_composite_bout(p, n, ctxm):
    return mat_composite(p, n, ctxm)


def mat_paracorde(p, n, ctxm):
    s = (p[:, 1] / 0.009 + 0.5 * (p[:, 0] + p[:, 2]) / 0.02) % 1.0
    losange = np.abs(((p[:, 1] / 0.018 - (p[:, 0] + p[:, 2]) / 0.03) % 1.0) - 0.5) < 0.07
    c = np.where(losange[:, None], rgb("#A88434"), rgb("#1B1B1D"))
    c = melange(c, c * 0.55, 1 - lisse(0.0, 0.15, np.minimum(s, 1 - s)))
    return c


MATIERES = {"bois": mat_bois, "bois_bout": mat_bois_bout, "ruban": mat_ruban, "tissu": mat_tissu,
            "acier_hache": mat_acier_hache, "acier_pioche": mat_acier_pioche, "tranchant": mat_tranchant, "coin": mat_coin,
            "acier_noir": mat_acier_noir, "laiton": mat_laiton, "composite": mat_composite,
            "composite_bout": mat_composite_bout, "paracorde": mat_paracorde}


def modele(nom, largeur=512):
    """Morceaux (faces des cubes) et texture d'un outil ; rend aussi le nombre de cubes."""
    cubes = OUTILS[nom]()
    ms = []
    for c in cubes:
        ms += c.morceaux()
    tex, h = mo.cuire_texture(ms, MATIERES, largeur, dilatation=3)
    return ms, tex, len(cubes)
