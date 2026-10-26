"""Grillage militaire de HeraliZ Décors (11/10, retour en jeu : « les barrières ne se relient pas entre elles… il
faudrait qu'elles se relient au bloc d'à côté… des arrondis, ultra fluide, comme les fences ») : deux blocs qu'on
empile, `grillage` (maille sur toute la hauteur) puis `grillage_haut` (maille, main courante, rouleau de barbelés
au-dessus). Chaque bloc se raccorde à ses 8 voisins : droites est-ouest et nord-sud, diagonales à 45° (une ligne en
biais est une vraie diagonale, pas un escalier), angles. Un poteau aux angles, aux bouts et tous les 3 blocs en ligne
droite (tous les 2 en diagonale).

Textures HD 32 px : maille en losanges (fil galvanisé torsadé, quelques points de rouille), acier des poteaux, rouleau
de barbelés (boucles et pointes). Losanges et boucles au pas de 4 px de modèle : une demi-branche droite (8 px) en
compte 2, une demi-diagonale (11,3 px) 3, sans raccord visible.

    import grillage ; grillage.ecrire(A)        (appelé par gen_assets.py)
"""
import json
import math
import os

import numpy as np
from PIL import Image

NS = "heraliz_decors"
T = 32
DIAG = 8 * math.sqrt(2)                      # demi-diagonale d'un bloc (px de modèle)
BLOCS = ("grillage", "grillage_haut")
NOMS = {"grillage": ("Grillage militaire", "Military fence"),
        "grillage_haut": ("Grillage militaire (haut, barbelés)", "Military fence (top, barbed wire)")}


# ---------------------------------------------------------------------------------------------- textures
def _rng(g):
    return np.random.default_rng(g)


def maille():
    """Maille en losanges, fil galvanisé torsadé (clair / sombre en alternance), pas de 8 texels."""
    r = _rng(11)
    img = np.zeros((T, T, 4), np.uint8)
    for v in range(T):
        for u in range(T):
            a, b = (u + v) % 8, (u - v) % 8
            if a == 0 or b == 0:
                torsade = ((u // 2) + (v // 2)) % 2
                base = 178 if torsade else 148
                if a == 0 and b == 0:
                    base = 196                                             # croisement : le fil se recouvre
                n = int(r.integers(-10, 10))
                c = np.array([base + n, base + 4 + n, base + 9 + n], float)
                if r.random() < 0.06:                                      # rouille
                    c = np.array([128, 86, 58], float) + r.integers(-12, 12)
                img[v, u, :3] = np.clip(c, 0, 255)
                img[v, u, 3] = 255
    return Image.fromarray(img, "RGBA")


def acier():
    """Acier galvanisé des poteaux et de la main courante : gris bleuté, reflets verticaux, taches de rouille."""
    r = _rng(12)
    x = np.arange(T)
    reflet = 0.82 + 0.18 * np.cos((x / T) * 2 * math.pi * 2) ** 2
    img = np.zeros((T, T, 4), np.uint8)
    for v in range(T):
        for u in range(T):
            g = 150 * reflet[u] + r.normal(0, 6)
            c = np.array([g, g + 5, g + 11])
            if r.random() < 0.025:
                c = np.array([118, 80, 54]) + r.normal(0, 8)
            img[v, u, :3] = np.clip(c, 0, 255)
            img[v, u, 3] = 255
    return Image.fromarray(img, "RGBA")


def barbeles():
    """Rouleau de barbelés (concertina) vu de côté : boucles rondes de fil fin qui se chevauchent largement (une tous
    les 8 texels, 18 de diamètre : on lit un ressort), acier clair dessus, plus sombre dessous ; pointes en croix."""
    img = np.zeros((T, T, 4), np.uint8)
    cy, R = 15.5, 8.6
    for cx0 in range(0, T, 8):
        for cx in (cx0 - T, cx0, cx0 + T):                                   # raccord horizontal
            for k in range(420):
                a = 2 * math.pi * k / 420
                u = cx + 4 + R * 0.92 * math.cos(a)
                v = cy + R * math.sin(a)
                iu, iv = int(math.floor(u)), int(math.floor(v))
                if 0 <= iu < T and 0 <= iv < T:
                    g = 214 - 80 * (0.5 + 0.5 * math.sin(a))
                    img[iv, iu] = (int(g), int(g + 3), int(g + 8), 255)
            for k in range(6):                                              # pointes
                a = 2 * math.pi * (k + 0.25) / 6
                u = cx + 4 + R * 0.92 * math.cos(a)
                v = cy + R * math.sin(a)
                for du, dv in ((-1, -1), (1, 1), (-1, 1), (1, -1)):
                    iu, iv = int(math.floor(u + du)), int(math.floor(v + dv))
                    if 0 <= iu < T and 0 <= iv < T:
                        img[iv, iu] = (92, 96, 102, 255)
    return Image.fromarray(img, "RGBA")


def textures():
    return {"grillage_maille": maille(), "grillage_acier": acier(), "grillage_barbeles": barbeles()}


# ---------------------------------------------------------------------------------------------- modèles
def _tex():
    return {"maille": f"{NS}:block/grillage_maille", "acier": f"{NS}:block/grillage_acier",
            "barbeles": f"{NS}:block/grillage_barbeles", "particle": f"{NS}:block/grillage_acier"}


def _boite(fr, to, tex="#acier", rot=None, faces=("north", "south", "east", "west", "up", "down")):
    e = {"from": [round(v, 4) for v in fr], "to": [round(v, 4) for v in to],
         "faces": {f: {"texture": tex} for f in faces}}
    if rot:
        e["rotation"] = rot
    return e


def _plan(fr, to, faces, uv, tex, rot=None):
    e = {"from": [round(v, 4) for v in fr], "to": [round(v, 4) for v in to],
         "faces": {f: {"texture": tex, "uv": uv} for f in faces}}
    if rot:
        e["rotation"] = rot
    return e


def bras(haut, diagonale):
    """Branche vers le nord (diagonale : vers le nord-est) : maille, et pour le haut main courante + barbelés."""
    L = DIAG if diagonale else 8.0
    z0 = 8 - L
    rot = {"origin": [8, 8, 8], "axis": "y", "angle": -45} if diagonale else None
    u1 = 12 if diagonale else 8                                            # 3 ou 2 pas de 4 px
    h = 14.5 if haut else 16
    el = [_plan([8, 0, z0], [8, h, 8], ("east", "west"), [0, 16 - h, u1, 16], "#maille", rot)]
    if haut:
        el.append(_boite([7.3, 14, z0], [8.7, 15.4, 8], rot=rot, faces=("east", "west", "up", "down")))   # main courante
        if diagonale:                                                       # un élément ne tourne que sur un axe :
            for x_ in (6.6, 9.4):                                           # en diagonale, deux plans parallèles
                el.append(_plan([x_, 15, z0], [x_, 25, 8], ("east", "west"), [0, 3, u1, 13], "#barbeles", rot))
        else:
            for a in (-22.5, 22.5):                                        # rouleau : deux plans croisés
                rr = {"origin": [8, 20, 8 - L / 2], "axis": "z", "angle": a}
                el.append(_plan([8, 15, z0], [8, 25, 8], ("east", "west"), [0, 3, u1, 13], "#barbeles", rr))
    return el


def poteau(haut):
    top = 23 if haut else 16
    el = [_boite([7.25, 0, 7.25], [8.75, top, 8.75])]
    if haut:
        el.append(_boite([6.9, top, 6.9], [9.1, top + 0.6, 9.1]))           # chapeau
    return el


def modeles():
    m = {}
    for b in BLOCS:
        haut = b.endswith("haut")
        c = "minecraft:cutout"
        m[f"{b}_poteau"] = {"render_type": c, "textures": _tex(), "elements": poteau(haut)}
        m[f"{b}_bras"] = {"render_type": c, "textures": _tex(), "elements": bras(haut, False)}
        m[f"{b}_bras_diag"] = {"render_type": c, "textures": _tex(), "elements": bras(haut, True)}
    return m


def objets():
    """Modèle d'objet : un bout de grillage droit (deux branches et un poteau)."""
    o = {}
    for b in BLOCS:
        haut = b.endswith("haut")
        el = poteau(haut) + bras(haut, False)
        sud = bras(haut, False)
        for e in sud:                                                       # branche sud : symétrique de la nord
            e2 = json.loads(json.dumps(e))
            e2["from"][2], e2["to"][2] = 16 - e["to"][2], 16 - e["from"][2]
            if "rotation" in e2 and e2["rotation"]["axis"] == "z":
                e2["rotation"]["origin"][2] = 16 - e2["rotation"]["origin"][2]
            el.append(e2)
        o[b] = {"render_type": "minecraft:cutout", "textures": _tex(), "elements": el, "display": {
            "gui": {"rotation": [30, 225, 0], "translation": [0, -1.5, 0], "scale": [0.55, 0.55, 0.55]},
            "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
            "fixed": {"rotation": [0, 90, 0], "translation": [0, 0, 0], "scale": [0.5, 0.5, 0.5]},
            "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
            "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}}}
    return o


DIRS = (("north", 0), ("east", 90), ("south", 180), ("west", 270))
DIAGS = (("nord_est", 0), ("sud_est", 90), ("sud_ouest", 180), ("nord_ouest", 270))


def etats():
    e = {}
    for b in BLOCS:
        parts = [{"when": {"poteau": "true"}, "apply": {"model": f"{NS}:block/{b}_poteau"}}]
        for d, y in DIRS:
            parts.append({"when": {d: "true"}, "apply": {"model": f"{NS}:block/{b}_bras", "y": y}})
        for d, y in DIAGS:
            parts.append({"when": {d: "true"}, "apply": {"model": f"{NS}:block/{b}_bras_diag", "y": y}})
        e[b] = {"multipart": parts}
    return e


# ---------------------------------------------------------------------------------------------- règles (aussi pour la map)
def poteau_voulu(x, z, conn):
    """conn : ensemble des directions raccordées ('north', ..., 'nord_est', ...). Poteau aux bouts, aux angles et aux
    croisements ; en ligne droite tous les 3 blocs, en diagonale tous les 2."""
    c = set(conn)
    if c == {"east", "west"}:
        return x % 3 == 0
    if c == {"north", "south"}:
        return z % 3 == 0
    if c in ({"nord_est", "sud_ouest"}, {"nord_ouest", "sud_est"}):
        return x % 2 == 0
    return True


def ecrire(A):
    d = os.path.join(A, "textures", "block")
    for sous in ("textures/block", "models/block", "models/item", "blockstates"):
        os.makedirs(os.path.join(A, sous), exist_ok=True)
    for n, im in textures().items():
        im.save(os.path.join(d, n + ".png"))
    for n, m in modeles().items():
        with open(os.path.join(A, "models", "block", n + ".json"), "w", encoding="utf-8") as f:
            json.dump(m, f, indent=1)
    for n, m in objets().items():
        with open(os.path.join(A, "models", "item", n + ".json"), "w", encoding="utf-8") as f:
            json.dump(m, f, indent=1)
    for n, m in etats().items():
        with open(os.path.join(A, "blockstates", n + ".json"), "w", encoding="utf-8") as f:
            json.dump(m, f, indent=1)
