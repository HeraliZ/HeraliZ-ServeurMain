#!/usr/bin/env python3
"""Jumelles de l'Airstrike : génère le modèle (geo/designator.geo) et sa texture (textures/entity/designator.png).

    python mods/airstrike/outils/modele/jumelles.py                 écrit le modèle et la texture du mod
    python mods/airstrike/outils/modele/jumelles.py <geo> <png>     les écrit ailleurs (aperçu avant validation)

Besoin de Pillow et numpy (pip install pillow numpy).

Sources : jumelles_origine.geo et jumelles_origine.png, à côté de ce fichier (le modèle d'origine). Le module laser
(coque, trappe, voyant, émetteur, récepteur, boutons, écran) et les pattes de sangle en sont repris tels quels. Le corps
des jumelles (tubes, objectifs, oculaires, œilletons, pont, molette, pas de vis du trépied) est refait en surfaces
lisses : des « quad » (4 sommets avec leur normale, pour un éclairage arrondi), peints dans la partie libre de la
texture (sous l'atlas d'origine). Les points dont le code dépend (baie de la cartouche, charnière et loquet de la trappe,
écran, voyant) ne bougent pas : ils sont dans le module.

Repère : celui de l'objet (pixels, 16 par bloc) : x vers la droite, y vers le haut, z vers l'arrière (les œilletons).
Le fichier .geo est dans le repère des modèles du jeu (x et y retournés) : la conversion est faite à l'écriture.
Relancer le script après toute retouche, puis refabriquer le mod (fichiers des joueurs : le jar -client change).
"""
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ICI = Path(__file__).resolve().parent
MOD = ICI.parents[1]
ASSETS = MOD / "src" / "client" / "resources" / "assets" / "airstrike"
GEO_ORIGINE = ICI / "jumelles_origine.geo"
TEX_ORIGINE = ICI / "jumelles_origine.png"
GEO_SORTIE = ASSETS / "geo" / "designator.geo"
TEX_SORTIE = ASSETS / "textures" / "entity" / "designator.png"

PX = 8                      # pixels de texture par pixel de modèle (atlas de 1024 pour 128 unités)
# pièces du corps d'origine remplacées (les autres pièces du fichier d'origine sont gardées)
REMPLACEES = re.compile(r"^(hood|objective|barrel|taper|eyepiece|eyecup|ocular|bridge|hinge|focus|tripod|emitter|emitter_ring|receiver|receiver_ring)\d*$")

# ------------------------------------------------------------------ cotes (pixels de l'objet)
TUBES_X = (4.6, 11.4)       # axes des deux tubes (comme l'original)
TUBE_Y = 6.4
R_TUBE = 2.75               # caoutchouc des tubes
Z_TUBE = (2.6, 10.0)
R_BOL, Z_BOL = 3.05, (0.4, 2.6)          # bol de l'objectif
R_LEVRE, Z_VERRE = 2.55, 0.9             # lèvre avant, verre de l'objectif en retrait
Z_CONE, R_OCULAIRE = 11.4, 1.95          # cône vers l'oculaire
Z_OCULAIRE = 12.8
R_OEIL, Z_OEIL = 2.1, 14.2               # œilleton (caoutchouc), fond arrondi
R_OEIL_IN, Z_VERRE_OC = 1.35, 13.6       # ouverture de l'œilleton, verre de l'oculaire en retrait
R_CHARNIERE, Z_CHARNIERE = 1.15, (2.8, 10.6)
MOLETTE = dict(c=(8.0, 7.75, 11.75), r=1.1, l=2.2)
TREPIED = dict(c=(8.0, 4.15, 6.4), r=0.9, h=1.1)
# optiques du module laser (devant la plaque avant, z = 1,62) : émetteur du laser (petit verre rouge) et récepteur
# (grand verre sombre), comme l'original
EMETTEUR = dict(c=(6.3, 10.7), r_verre=0.85, r=1.15)
RECEPTEUR = dict(c=(9.7, 10.7), r_verre=1.2, r=1.5)
Z_PLAQUE, Z_BAGUE, Z_LENTILLE = 1.62, 1.2, 1.32

# ------------------------------------------------------------------ régions de la texture (pixels de la texture 1024)
REGIONS = {
    "caoutchouc": (0, 616, 138, 676),
    "cone": (0, 680, 138, 694),
    "bol": (0, 700, 154, 718),
    "levre_paroi": (0, 724, 128, 729),
    "oculaire": (0, 734, 98, 746),
    "oeil": (0, 750, 106, 764),
    "oeil_paroi": (0, 768, 68, 774),
    "anneau_metal": (160, 616, 210, 666),
    "verre_obj": (216, 616, 258, 658),
    "oeil_fond": (264, 616, 294, 646),
    "verre_oc": (300, 616, 322, 638),
    "charniere": (330, 616, 388, 680),
    "bouchon": (396, 616, 414, 634),
    "molette": (420, 616, 476, 634),
    "trepied": (504, 616, 550, 626),
    "trepied_fond": (556, 616, 572, 632),
    "bague": (580, 616, 680, 622),
    "bague_face": (688, 616, 714, 642),
    "verre_emetteur": (720, 616, 736, 632),
    "verre_recepteur": (744, 616, 764, 636),
}


# ------------------------------------------------------------------ géométrie
class Corps:
    """Quads du corps (repère de l'objet) : 4 x (x, y, z, u, v, nx, ny, nz), u v en pixels de texture."""

    def __init__(self):
        self.quads = []

    def quad(self, sommets):
        self.quads.append(sommets)

    def lignes(self):
        out = []
        for q in self.quads:
            vals = []
            for (x, y, z, u, v, nx, ny, nz) in q:
                # repère des modèles du jeu : x et y retournés ; u v en unités de l'atlas (128)
                vals += [-x, -y, z, u / PX, v / PX, -nx, -ny, nz]
            out.append("quad " + " ".join("%.4f" % a for a in vals))
        return out


def norm(v):
    l = math.sqrt(sum(c * c for c in v)) or 1.0
    return [c / l for c in v]


def repere(axe):
    """Axe a et deux directions e1, e2 telles que (e1, e2, a) soit direct ; e1 vers le bas (couture en dessous)."""
    a = norm(axe)
    bas = [0.0, -1.0, 0.0] if abs(a[1]) < 0.9 else [0.0, 0.0, 1.0]
    e1 = norm([bas[i] - a[i] * sum(bas[k] * a[k] for k in range(3)) for i in range(3)])
    e2 = [a[1] * e1[2] - a[2] * e1[1], a[2] * e1[0] - a[0] * e1[2], a[0] * e1[1] - a[1] * e1[0]]
    return a, e1, e2


def point(c, a, e1, e2, t, r, th):
    return [c[i] + a[i] * t + r * (math.cos(th) * e1[i] + math.sin(th) * e2[i]) for i in range(3)]


def cylindre(corps, c, axe, t0, t1, r0, r1, n, region, vers_dedans=False):
    """Côté d'un cylindre (ou d'un cône) d'axe axe passant par c, de t0 à t1, rayons r0 -> r1."""
    a, e1, e2 = repere(axe)
    u0, v0, u1, v1 = region
    pente = (r1 - r0) / (t1 - t0)
    for i in range(n):
        q = []
        for (k, t, r) in ((i, t0, r0), (i + 1, t0, r0), (i + 1, t1, r1), (i, t1, r1)):
            th = 2 * math.pi * k / n
            radial = [math.cos(th) * e1[j] + math.sin(th) * e2[j] for j in range(3)]
            nrm = norm([radial[j] - pente * a[j] for j in range(3)])
            if vers_dedans:
                nrm = [-x for x in nrm]
            uu = u0 + (u1 - u0) * k / n
            vv = v0 + (v1 - v0) * (t - t0) / (t1 - t0)
            q.append(point(c, a, e1, e2, t, r, th) + [uu, vv] + nrm)
        corps.quad(q[::-1] if vers_dedans else q)


def anneau(corps, c, axe, t, r_in, r_out, n, region, vers_avant):
    """Anneau (disque si r_in = 0) perpendiculaire à l'axe, tourné vers +axe (vers_avant) ou -axe. Projection plane."""
    a, e1, e2 = repere(axe)
    u0, v0, u1, v1 = region
    cu, cv, ru, rv = (u0 + u1) / 2, (v0 + v1) / 2, (u1 - u0) / 2, (v1 - v0) / 2
    nrm = a if vers_avant else [-x for x in a]
    for i in range(n):
        q = []
        for (k, r) in ((i, r_out), (i + 1, r_out), (i + 1, r_in), (i, r_in)):
            th = 2 * math.pi * k / n
            q.append(point(c, a, e1, e2, t, r, th) + [cu + ru * r / r_out * math.cos(th), cv + rv * r / r_out * math.sin(th)] + nrm)
        corps.quad(q if vers_avant else q[::-1])


def tube(corps, x):
    """Un tube complet : bol de l'objectif, verre, caoutchouc, cône, oculaire, œilleton, verre de l'oculaire."""
    c, z = [x, TUBE_Y, 0.0], [0, 0, 1]
    G = REGIONS
    # bol de l'objectif (chanfrein à l'avant), lèvre, verre en retrait
    cylindre(corps, c, z, Z_BOL[0], Z_BOL[0] + 0.45, R_BOL - 0.3, R_BOL, 32, (G["bol"][0], G["bol"][1], G["bol"][2], G["bol"][1] + 4))
    cylindre(corps, c, z, Z_BOL[0] + 0.45, Z_BOL[1], R_BOL, R_BOL, 32, (G["bol"][0], G["bol"][1] + 4, G["bol"][2], G["bol"][3]))
    anneau(corps, c, z, Z_BOL[0], R_LEVRE, R_BOL - 0.3, 32, G["anneau_metal"], False)
    cylindre(corps, c, z, Z_BOL[0], Z_VERRE, R_LEVRE, R_LEVRE, 32, G["levre_paroi"], vers_dedans=True)
    anneau(corps, c, z, Z_VERRE, 0.0, R_LEVRE, 32, G["verre_obj"], False)
    # marche entre le bol et le caoutchouc (vers l'arrière)
    anneau(corps, c, z, Z_BOL[1], R_TUBE, R_BOL, 32, G["anneau_metal"], True)
    # caoutchouc du tube
    cylindre(corps, c, z, Z_TUBE[0], Z_TUBE[1], R_TUBE, R_TUBE, 32, G["caoutchouc"])
    # cône vers l'oculaire, oculaire métal
    cylindre(corps, c, z, Z_TUBE[1], Z_CONE, R_TUBE, R_OCULAIRE, 32, G["cone"])
    cylindre(corps, c, z, Z_CONE, Z_OCULAIRE, R_OCULAIRE, R_OCULAIRE, 24, G["oculaire"])
    # œilleton : marche, côté, bord arrondi, fond, ouverture, verre de l'oculaire
    anneau(corps, c, z, Z_OCULAIRE, R_OCULAIRE, R_OEIL, 24, G["anneau_metal"], False)
    o = G["oeil"]
    hv = o[3] - o[1]
    cylindre(corps, c, z, Z_OCULAIRE, Z_OEIL - 0.45, R_OEIL, R_OEIL, 24, (o[0], o[1], o[2], o[1] + hv * 0.7))
    cylindre(corps, c, z, Z_OEIL - 0.45, Z_OEIL - 0.12, R_OEIL, R_OEIL - 0.18, 24, (o[0], o[1] + hv * 0.7, o[2], o[1] + hv * 0.9))
    cylindre(corps, c, z, Z_OEIL - 0.12, Z_OEIL, R_OEIL - 0.18, R_OEIL - 0.4, 24, (o[0], o[1] + hv * 0.9, o[2], o[3]))
    anneau(corps, c, z, Z_OEIL, R_OEIL_IN, R_OEIL - 0.4, 24, G["oeil_fond"], True)
    cylindre(corps, c, z, Z_VERRE_OC, Z_OEIL, R_OEIL_IN, R_OEIL_IN, 24, G["oeil_paroi"], vers_dedans=True)
    anneau(corps, c, z, Z_VERRE_OC, 0.0, R_OEIL_IN, 24, G["verre_oc"], True)


def corps_jumelles():
    corps = Corps()
    for x in TUBES_X:
        tube(corps, x)
    G = REGIONS
    # charnière centrale (entre les tubes), bouchons
    c, z = [8.0, TUBE_Y, 0.0], [0, 0, 1]
    cylindre(corps, c, z, Z_CHARNIERE[0], Z_CHARNIERE[1], R_CHARNIERE, R_CHARNIERE, 16, G["charniere"])
    anneau(corps, c, z, Z_CHARNIERE[0], 0.0, R_CHARNIERE, 16, G["bouchon"], False)
    anneau(corps, c, z, Z_CHARNIERE[1], 0.0, R_CHARNIERE, 16, G["bouchon"], True)
    # molette de mise au point (axe x), entre les oculaires, sous l'écran
    m = MOLETTE
    cm, x = list(m["c"]), [1, 0, 0]
    cylindre(corps, cm, x, -m["l"] / 2, m["l"] / 2, m["r"], m["r"], 16, G["molette"])
    anneau(corps, cm, x, -m["l"] / 2, 0.0, m["r"], 16, G["bouchon"], False)
    anneau(corps, cm, x, m["l"] / 2, 0.0, m["r"], 16, G["bouchon"], True)
    # axe de la molette jusqu'à la charnière
    cylindre(corps, [8.0, TUBE_Y + 0.3, 11.75], [0, 1, 0], 0, cm[1] - TUBE_Y - 0.3, 0.45, 0.45, 8, G["oculaire"])
    # optiques du module laser : bague (côté, face), verre en léger retrait
    for d, verre in ((EMETTEUR, "verre_emetteur"), (RECEPTEUR, "verre_recepteur")):
        co = [d["c"][0], d["c"][1], 0.0]
        cylindre(corps, co, z, Z_BAGUE, Z_PLAQUE, d["r"], d["r"], 24, G["bague"])
        anneau(corps, co, z, Z_BAGUE, d["r_verre"], d["r"], 24, G["bague_face"], False)
        cylindre(corps, co, z, Z_BAGUE, Z_LENTILLE, d["r_verre"], d["r_verre"], 24, G["levre_paroi"], vers_dedans=True)
        anneau(corps, co, z, Z_LENTILLE, 0.0, d["r_verre"], 24, G[verre], False)
    # pas de vis du trépied, sous la charnière
    t = TREPIED
    ct, y = list(t["c"]), [0, 1, 0]
    cylindre(corps, ct, y, 0.0, t["h"], t["r"], t["r"], 12, G["trepied"])
    anneau(corps, ct, y, 0.0, 0.0, t["r"], 12, G["trepied_fond"], False)
    return corps


# ------------------------------------------------------------------ texture
def bruit(h, w, graine, echelle=1.0):
    rng = np.random.default_rng(graine)
    n = rng.normal(0, 1, (h, w))
    # lissage léger (moyenne 3 x 3) : grain fin sans scintillement
    p = np.pad(n, 1, mode="wrap")
    n = sum(p[1 + dy:1 + dy + h, 1 + dx:1 + dx + w] for dy in (-1, 0, 1) for dx in (-1, 0, 1)) / 9
    return n * echelle


def peindre(atlas, nom, img):
    u0, v0, u1, v1 = REGIONS[nom]
    h, w = v1 - v0, u1 - u0
    assert img.shape[:2] == (h, w), (nom, img.shape, (h, w))
    a = np.clip(img, 0, 255).astype(np.uint8)
    if a.shape[2] == 3:
        a = np.concatenate([a, np.full((h, w, 1), 255, np.uint8)], axis=2)
    atlas[v0:v1, u0:u1] = a


def taille(nom):
    u0, v0, u1, v1 = REGIONS[nom]
    return v1 - v0, u1 - u0


OLIVE = np.array([66, 72, 50], float)          # caoutchouc des tubes (proche de l'original)
OLIVE_PEINT = np.array([74, 80, 55], float)
CAOUTCHOUC = np.array([30, 31, 33], float)
METAL = np.array([44, 46, 50], float)


def texture_caoutchouc(h, w, graine, rainures=True):
    """Caoutchouc des tubes : rainures en anneau (tous les 6 texels), bords plus sombres, grain fin."""
    y = np.arange(h)[:, None] * np.ones((1, w))
    img = np.ones((h, w, 3)) * OLIVE
    if rainures:
        zone = (y > 7) & (y < h - 8)
        rainure = zone & ((y.astype(int) % 6) == 0)
        bord = zone & ((y.astype(int) % 6) == 1)
        img[rainure] *= 0.72
        img[bord] *= 1.08
    # ombre douce aux deux bouts (contact avec le bol et le cône)
    f = np.minimum(y, h - 1 - y) / 6.0
    img *= (0.86 + 0.14 * np.clip(f, 0, 1))[..., None]
    img += bruit(h, w, graine, 3.0)[..., None]
    return img


def texture_metal(h, w, graine, base=METAL, lignes=True):
    img = np.ones((h, w, 3)) * base
    img += bruit(h, w, graine, 2.5)[..., None]
    if lignes:
        img[0] += 18
        img[-1] -= 8
    return img


def texture_verre_objectif(h, w):
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = np.sqrt(((xx - cx) / (w / 2)) ** 2 + ((yy - cy) / (h / 2)) ** 2)
    centre = np.array([38, 62, 140], float)
    milieu = np.array([28, 40, 104], float)
    bord = np.array([10, 13, 28], float)
    t = np.clip(r, 0, 1)[..., None]
    img = np.where(t < 0.55, centre + (milieu - centre) * (t / 0.55), milieu + (bord - milieu) * ((t - 0.55) / 0.45))
    # reflet violet du traitement (arc), puis tache de lumière en haut à gauche
    ang = np.arctan2(yy - cy, xx - cx)
    arc = np.exp(-((r - 0.62) / 0.08) ** 2) * np.clip(np.cos(ang + 2.3), 0, 1)
    img += arc[..., None] * np.array([70, 40, 110])
    spot = np.exp(-(((xx - cx + w * 0.18) / (w * 0.09)) ** 2 + ((yy - cy + h * 0.2) / (h * 0.07)) ** 2))
    img += spot[..., None] * 200
    return img


def texture_verre_oculaire(h, w):
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = np.sqrt(((xx - cx) / (w / 2)) ** 2 + ((yy - cy) / (h / 2)) ** 2)
    img = np.ones((h, w, 3)) * np.array([10, 14, 18], float)
    img += (np.exp(-((r - 0.5) / 0.15) ** 2) * 22)[..., None] * np.array([0.6, 1.0, 0.8])
    spot = np.exp(-(((xx - cx + w * 0.2) / (w * 0.1)) ** 2 + ((yy - cy + h * 0.2) / (h * 0.08)) ** 2))
    img += spot[..., None] * 140
    return img


def texture_verre(h, w, centre, bord, reflet):
    """Verre teinté : dégradé du centre au bord, petite tache de lumière en haut à gauche."""
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = np.clip(np.sqrt(((xx - cx) / (w / 2)) ** 2 + ((yy - cy) / (h / 2)) ** 2), 0, 1)[..., None]
    img = centre + (bord - centre) * r
    spot = np.exp(-(((xx - cx + w * 0.2) / (w * 0.12)) ** 2 + ((yy - cy + h * 0.2) / (h * 0.1)) ** 2))
    return img + spot[..., None] * reflet


def texture_disque_sombre(h, w, base, trou=0.0):
    yy, xx = np.mgrid[0:h, 0:w]
    cx, cy = (w - 1) / 2, (h - 1) / 2
    r = np.sqrt(((xx - cx) / (w / 2)) ** 2 + ((yy - cy) / (h / 2)) ** 2)
    img = np.ones((h, w, 3)) * base
    img *= (1.0 - 0.18 * np.clip(r, 0, 1))[..., None]
    if trou:
        img[r < trou] = np.array([8, 8, 9])
    return img


def atlas():
    im = Image.open(TEX_ORIGINE).convert("RGBA")
    a = np.array(im)
    hauteur_libre = 616
    assert not a[hauteur_libre:, :, 3].any(), "la partie libre de la texture d'origine n'est plus vide"
    h, w = taille("caoutchouc")
    peindre(a, "caoutchouc", texture_caoutchouc(h, w, 1))
    h, w = taille("cone")
    peindre(a, "cone", texture_caoutchouc(h, w, 2, rainures=False) * 0.92)
    h, w = taille("bol")
    bol = texture_caoutchouc(h, w, 3, rainures=False)
    bol[:4] = texture_metal(4, w, 4, base=np.array([36, 38, 41]))   # chanfrein avant
    bol[4:6] *= 0.8
    peindre(a, "bol", bol)
    h, w = taille("levre_paroi")
    peindre(a, "levre_paroi", np.ones((h, w, 3)) * np.array([16, 17, 19]))
    h, w = taille("oculaire")
    oc = texture_metal(h, w, 5)
    oc[h // 2 - 1:h // 2 + 1] *= 0.7                                  # bague de réglage
    peindre(a, "oculaire", oc)
    h, w = taille("oeil")
    oeil = np.ones((h, w, 3)) * CAOUTCHOUC + bruit(h, w, 6, 2.0)[..., None]
    oeil[::3] *= 0.85                                                 # stries du caoutchouc
    peindre(a, "oeil", oeil)
    h, w = taille("oeil_paroi")
    peindre(a, "oeil_paroi", np.ones((h, w, 3)) * np.array([14, 14, 15]))
    h, w = taille("anneau_metal")
    peindre(a, "anneau_metal", texture_disque_sombre(h, w, np.array([40, 42, 46], float)) + bruit(h, w, 7, 2.0)[..., None])
    h, w = taille("verre_obj")
    peindre(a, "verre_obj", texture_verre_objectif(h, w))
    h, w = taille("oeil_fond")
    peindre(a, "oeil_fond", texture_disque_sombre(h, w, CAOUTCHOUC * 0.9))
    h, w = taille("verre_oc")
    peindre(a, "verre_oc", texture_verre_oculaire(h, w))
    h, w = taille("charniere")
    ch = np.ones((h, w, 3)) * OLIVE_PEINT + bruit(h, w, 8, 2.5)[..., None]
    ch[:, w // 2 - 1:w // 2 + 1] *= 0.75                              # joint de la charnière
    ch[h // 2 - 1:h // 2 + 1] *= 0.8
    peindre(a, "charniere", ch)
    h, w = taille("bouchon")
    peindre(a, "bouchon", texture_disque_sombre(h, w, METAL * 1.1, trou=0.18))
    h, w = taille("molette")
    mo = np.ones((h, w, 3)) * np.array([34, 35, 37], float)
    mo[:, ::2] = np.array([52, 54, 57])                               # moletage
    mo[:2] *= 0.8
    mo[-2:] *= 0.8
    peindre(a, "molette", mo)
    h, w = taille("trepied")
    peindre(a, "trepied", texture_metal(h, w, 9, base=np.array([58, 61, 66], float)))
    h, w = taille("trepied_fond")
    peindre(a, "trepied_fond", texture_disque_sombre(h, w, np.array([58, 61, 66], float), trou=0.45))
    h, w = taille("bague")
    peindre(a, "bague", texture_metal(h, w, 10, base=np.array([34, 35, 38], float)))
    h, w = taille("bague_face")
    peindre(a, "bague_face", texture_disque_sombre(h, w, np.array([46, 48, 52], float)) + bruit(h, w, 11, 2.0)[..., None])
    h, w = taille("verre_emetteur")
    peindre(a, "verre_emetteur", texture_verre(h, w, np.array([220, 40, 32], float), np.array([70, 6, 8], float), 120))
    h, w = taille("verre_recepteur")
    peindre(a, "verre_recepteur", texture_verre(h, w, np.array([30, 40, 62], float), np.array([6, 7, 12], float), 150))
    deborder(a, hauteur_libre)
    return Image.fromarray(a)


def deborder(a, haut, marge=2):
    """Recopie le bord de chaque région sur quelques pixels autour, là où la texture est vide (partie libre seulement) :
    un point pris tout au bord d'une région (arrondis de la carte graphique) garde la bonne couleur, jamais un trou."""
    libre = a[..., 3] == 0
    libre[:haut] = False
    H, W = libre.shape

    def copier(dst, src):
        ok = libre[dst]
        a[dst][ok] = a[src][ok]
        libre[dst] &= ~ok

    for (u0, v0, u1, v1) in REGIONS.values():
        for k in range(1, marge + 1):
            if v0 - k >= 0:
                copier((v0 - k, slice(u0, u1)), (v0, slice(u0, u1)))
            if v1 - 1 + k < H:
                copier((v1 - 1 + k, slice(u0, u1)), (v1 - 1, slice(u0, u1)))
        lignes = slice(max(0, v0 - marge), min(H, v1 + marge))
        for k in range(1, marge + 1):
            if u0 - k >= 0:
                copier((lignes, u0 - k), (lignes, u0))
            if u1 - 1 + k < W:
                copier((lignes, u1 - 1 + k), (lignes, u1 - 1))


# ------------------------------------------------------------------ fichier .geo
def geo(corps):
    sortie = []
    garde = True
    for l in GEO_ORIGINE.read_text(encoding="utf-8").splitlines():
        t = l.split()
        if l.startswith("#"):
            continue
        if t and t[0] == "part":
            morceaux = t[1].split("/")
            garde = not (len(morceaux) >= 2 and REMPLACEES.match(morceaux[1]))
            if garde:
                sortie.append(l)
            continue
        if t and t[0] == "box" and not garde:
            continue
        sortie.append(l)
    # le corps lisse : une pièce sans transformation sous « body »
    neuf = ["part body/corps 0 0 0 0 0 0"] + corps.lignes()
    i = next(k for k, l in enumerate(sortie) if l.startswith("part emit_led"))
    tete = ["# Airstrike geometry - GENERATED by mods/airstrike/outils/modele/jumelles.py, do not edit by hand"]
    return "\n".join(tete + sortie[:i] + neuf + sortie[i:]) + "\n"


def main():
    geo_sortie, tex_sortie = (Path(sys.argv[1]), Path(sys.argv[2])) if len(sys.argv) > 2 else (GEO_SORTIE, TEX_SORTIE)
    corps = corps_jumelles()
    geo_sortie.write_text(geo(corps), encoding="utf-8")
    atlas().save(tex_sortie, optimize=True)
    print("[jumelles] %d quads lisses -> %s, %s" % (len(corps.quads), geo_sortie, tex_sortie))


if __name__ == "__main__":
    main()
