#!/usr/bin/env python3
"""Chargeurs TaCZ sans modèle 3D : vrai modèle 3D en main et icône 3D dans l'inventaire ; crosses d'usine : icône 3D.

    python outils/accessoires_3d.py --apercu DOSSIER   planche avant / après des icônes dans DOSSIER, rien n'est changé
    python outils/accessoires_3d.py --installer        écrit les modèles, les affichages et les icônes
    python outils/accessoires_3d.py --verifier         contrôle seulement (code de sortie 1 si ce n'est plus à jour)

Besoin de numpy et Pillow.

Les chargeurs agrandis de TaCZ, de W Mags et de Max Stuff n'ont pas de modèle : sur l'arme, c'est le modèle de l'arme qui
montre son chargeur (os « mag_extended_1 / 2 / 3 », ou « windowed_mag » des armes de W Mags). Tenus en main, au sol ou
dans un cadre, TaCZ les dessinait comme leur icône d'inventaire posée à plat : un carré (AttachmentItemRenderer,
SlotModel), et l'icône elle-même est une carte pleine qui cache la couleur de rareté de la case.

Ici, le chargeur est pris dans le vrai modèle d'une arme qui le porte (SOURCES : os gardés avec leurs enfants ; leurs
parents sont gardés sans leurs cubes, pour garder positions et rotations), posé au centre (milieu de sa boîte à
l'origine) et écrit comme modèle d'accessoire dans le gunpack serveur/tacz/heraliz_accessoires_3d ; il garde la
texture de l'arme (mêmes UV). Son affichage (display/attachments/<id>_display.json de son gunpack) reçoit « model » et
« texture » : TaCZ dessine alors ce modèle en main, au sol et dans un cadre, comme un viseur ou une poignée. Sur l'arme,
rien ne change : aucune arme n'a d'os « extended_mag_pos » (seul endroit où TaCZ poserait le modèle d'un chargeur).
L'icône d'inventaire (image « slot ») est rendue hors du jeu depuis ce modèle, de trois quarts, fond transparent (la
couleur de rareté de la case se voit derrière), comme les icônes des armes (outils/icones_armes.py).

Crosses d'usine (oem_stock_*) : icône 3D seulement. Elles ont un « adapter » : sur l'arme, c'est un nœud du modèle de
l'arme qui les dessine ; un modèle d'accessoire serait AUSSI posé sur l'os « stock_pos » (deux crosses). En main, TaCZ
pose donc toujours leur icône à plat, mais sans la carte.

Les munitions spéciales (ammo_mod_*) gardent leur carte (outils/icones_accessoires.py) : aucun modèle à prendre.

Tout est écrit par cet outil, jamais à la main ; à relancer après la mise à jour d'un gunpack (--verifier le dit).
Fichiers des joueurs : il faut leur redonner le dossier tacz.
"""
import argparse
import copy
import json
import math
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
from silencieux import lire  # noqa: E402  (lecture tolérante : commentaires, virgules finales)

DEPOT = Path(__file__).resolve().parents[1]
TACZ = DEPOT / "serveur" / "tacz"
PACK = "heraliz_accessoires_3d"
SORTIE = TACZ / PACK
META = {"namespace": PACK, "name": "HeraliZ Accessoires 3D",
        "description": "Modèles 3D des chargeurs, pris dans les modèles des armes, écrits par outils/accessoires_3d.py "
                       "(pas à la main)"}

# accessoire -> (modèle de l'arme, os gardés avec leurs enfants, texture de l'arme, modèle en main ?, copies)
# copies : nombre d'exemplaires côte à côte (chargeurs couplés).
SOURCES = {
    # chargeurs de fusil (« Heavy Ammo ») : les trois tailles du HK416D (40, 60 coups, double tambour)
    "tacz:extended_mag_1": ("tacz:gun/hk416d_geo", ["mag_extended_1"], "tacz:gun/uv/hk416d", True, 1),
    "tacz:extended_mag_2": ("tacz:gun/hk416d_geo", ["mag_extended_2"], "tacz:gun/uv/hk416d", True, 1),
    "tacz:extended_mag_3": ("tacz:gun/hk416d_geo", ["mag_extended_3"], "tacz:gun/uv/hk416d", True, 1),
    # chargeurs de pistolet et de mitraillette (« Light Ammo ») : les trois tailles du CZ75
    "tacz:light_extended_mag_1": ("tacz:gun/cz75_geo", ["mag_extended_1"], "tacz:gun/uv/cz75", True, 1),
    "tacz:light_extended_mag_2": ("tacz:gun/cz75_geo", ["mag_extended_2"], "tacz:gun/uv/cz75", True, 1),
    "tacz:light_extended_mag_3": ("tacz:gun/cz75_geo", ["mag_extended_3"], "tacz:gun/uv/cz75", True, 1),
    # chargeurs de fusil de précision : celui de l'AWP (même forme aux trois niveaux, comme sur l'arme)
    "tacz:sniper_extended_mag_1": ("tacz:gun/ai_awp_geo", ["mag_extended_1"], "tacz:gun/uv/ai_awp", True, 1),
    "tacz:sniper_extended_mag_2": ("tacz:gun/ai_awp_geo", ["mag_extended_2"], "tacz:gun/uv/ai_awp", True, 1),
    "tacz:sniper_extended_mag_3": ("tacz:gun/ai_awp_geo", ["mag_extended_3"], "tacz:gun/uv/ai_awp", True, 1),
    # chargeurs à fenêtre de W Mags : celui de l'arme pour laquelle il est fait (sans le ressort, caché)
    "wmags:wmag_12g": ("wmags:gun/aa12_geo", ["win_mag", "win_bullets"], "tacz:gun/uv/aa12", True, 1),
    "wmags:wmag_556": ("wmags:gun/m4a1_geo", ["win_mag", "win_bullets"], "tacz:gun/uv/m4a1", True, 1),
    "wmags:wmag_762": ("wmags:gun/ak47_geo", ["win_mag", "win_bullets"], "tacz:gun/uv/ak47", True, 1),
    "wmags:wmag_pistols": ("wmags:gun/glock_17_geo", ["win_mag", "win_bullets"], "tacz:gun/uv/glock_17", True, 1),
    "wmags:wmag_rpk": ("wmags:gun/rpk_geo", ["win_mag", "win_bullets"], "tacz:gun/uv/rpk", True, 1),
    "wmags:wmag_smg": ("wmags:gun/ump45_geo", ["win_mag", "win_bullets"], "tacz:gun/uv/ump45", True, 1),
    # « Speed mag » de Max Stuff : deux chargeurs de fusil couplés
    "maxstuff:speed_mag": ("tacz:gun/hk416d_geo", ["mag_standard"], "tacz:gun/uv/hk416d", True, 2),
    # crosses d'usine : icône seulement (voir plus haut)
    "tacz:oem_stock_heavy": ("tacz:gun/ak47_geo", ["oem_stock_heavy"], "tacz:gun/uv/ak47", False, 1),
    "tacz:oem_stock_light": ("tacz:gun/rpk_geo", ["oem_stock_light"], "tacz:gun/uv/rpk", False, 1),
    "tacz:oem_stock_tactical": ("tacz:gun/m4a1_geo", ["oem_stock_tactical"], "tacz:gun/uv/m4a1", False, 1),
}

TAILLE, SUR = 64, 4                       # icône (px), rendue SUR fois plus grande puis réduite (bords lisses)
ROULIS, LACET, TANGAGE = -20.0, 25.0, 15.0  # vue de trois quarts, penchée (degrés)
MARGE = 0.06
LUMIERE = np.array([-0.35, 0.55, 1.0]) / np.linalg.norm([-0.35, 0.55, 1.0])
ECART_PIXELS = 0.01                       # --verifier : part des pixels qui peuvent différer (arrondis d'une machine à l'autre)


# ====================================================================== fichiers des gunpacks
def chercher(ident, sous, ext):
    """« ns:chemin » -> fichier assets/<ns>/<sous>/<chemin><ext> d'un gunpack (le premier par ordre alphabétique)."""
    ns, chemin = ident.split(":", 1)
    fs = sorted(TACZ.glob("*/assets/%s/%s/%s%s" % (ns, sous, chemin, ext)))
    return fs[0] if fs else None


def affichage(acc):
    """Fichier display de l'accessoire (via son index), ou None."""
    ns, nom = acc.split(":", 1)
    for idx in sorted(TACZ.glob("*/data/%s/index/attachments/%s.json" % (ns, nom))):
        d = lire(idx).get("display", "")
        if ":" in d:
            return chercher(d, "display/attachments", ".json")
    return None


# ====================================================================== géométrie (comme TaCZ : prep.py, verif_accessoires.py)
def _mat(pos, rot):
    m = np.eye(4)
    m[:3, 3] = pos
    r = np.eye(3)
    for axe in (2, 1, 0):                     # Z, puis Y, puis X (BedrockPart.translateAndRotateAndScale)
        a = rot[axe]
        if a:
            c, s = math.cos(a), math.sin(a)
            r = r @ (np.array([[1, 0, 0], [0, c, -s], [0, s, c]]) if axe == 0 else
                     np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]) if axe == 1 else
                     np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]]))
    m[:3, :3] = r
    return m


def _boite(u, v, x, y, z, w, h, d, gonfle, miroir, tw, th):
    xe, ye, ze = x + w + gonfle, y + h + gonfle, z + d + gonfle
    x, y, z = x - gonfle, y - gonfle, z - gonfle
    if miroir:
        x, xe = xe, x
    v1, v2, v3, v4 = (x, y, z), (xe, y, z), (xe, ye, z), (x, ye, z)
    v5, v6, v7, v8 = (x, y, ze), (xe, y, ze), (xe, ye, ze), (x, ye, ze)
    dx, dy, dz = int(w), int(h), int(d)
    p1, p2, p3 = u + dz, u + dz + dx, u + dz + dx + dx
    p4, p5 = u + dz + dx + dz, u + dz + dx + dz + dx
    p6, p7, p8, p9 = v + dz, v + dz + dy, v, u
    faces = [([v6, v5, v1, v2], p1, p8, p2, p6), ([v3, v4, v8, v7], p2, p6, p3, p8),
             ([v1, v5, v8, v4], p9, p6, p1, p7), ([v2, v1, v4, v3], p1, p6, p2, p7),
             ([v6, v2, v3, v7], p2, p6, p4, p7), ([v5, v6, v7, v8], p4, p6, p5, p7)]
    out = []
    for coins, u1, w1, u2, w2 in faces:
        uv = [(u2 / tw, w1 / th), (u1 / tw, w1 / th), (u1 / tw, w2 / th), (u2 / tw, w2 / th)]
        q = [list(coins[i]) + list(uv[i]) for i in range(4)]
        out.append(q[::-1] if miroir else q)
    return out


def _faces(x, y, z, w, h, d, gonfle, tw, th, faces):
    xe, ye, ze = x + w + gonfle, y + h + gonfle, z + d + gonfle
    x, y, z = x - gonfle, y - gonfle, z - gonfle
    v1, v2, v3, v4 = (x, y, z), (xe, y, z), (xe, ye, z), (x, ye, z)
    v5, v6, v7, v8 = (x, y, ze), (xe, y, ze), (xe, ye, ze), (x, ye, ze)
    ordre = [([v6, v5, v1, v2], "up"), ([v3, v4, v8, v7], "down"), ([v1, v5, v8, v4], "east"),
             ([v2, v1, v4, v3], "north"), ([v6, v2, v3, v7], "west"), ([v5, v6, v7, v8], "south")]
    out = []
    for coins, cle in ordre:
        f = faces.get(cle)
        if not isinstance(f, dict) or "uv" not in f:
            continue
        u1, w1 = f["uv"]
        t = f.get("uv_size", [0, 0])
        u2, w2 = u1 + t[0], w1 + t[1]
        uv = [(u2 / tw, w1 / th), (u1 / tw, w1 / th), (u1 / tw, w2 / th), (u2 / tw, w2 / th)]
        out.append([list(coins[i]) + list(uv[i]) for i in range(4)])
    return out


def triangles(modele):
    """(sommets (n, 3, 3) dans le repère de l'objet tenu, en px : x = -x Bedrock, y et z Bedrock ; uv (n, 3, 2))."""
    g = modele["minecraft:geometry"][0]
    desc = g["description"]
    tw, th = float(desc["texture_width"]), float(desc["texture_height"])
    os_ = g.get("bones", [])
    par_nom = {b["name"]: b for b in os_}
    enfants, racines = {}, []
    for b in os_:
        if b.get("parent") in par_nom:
            enfants.setdefault(b["parent"], []).append(b)
        else:
            racines.append(b)
    pts, uvs = [], []

    def visite(b, m):
        pv = b.get("pivot", [0, 0, 0])
        if b.get("parent") in par_nom:
            pp = par_nom[b["parent"]].get("pivot", [0, 0, 0])
            pos = (pv[0] - pp[0], pp[1] - pv[1], pv[2] - pp[2])
        else:
            pos = (pv[0], 24 - pv[1], pv[2])
        m = m @ _mat(pos, [math.radians(a) for a in b.get("rotation", [0, 0, 0])])
        for c in b.get("cubes", []) or []:
            org, t = c["origin"], c["size"]
            gonfle = c.get("inflate", 0)
            uv = c.get("uv", [0, 0])
            if c.get("rotation"):
                cp = c.get("pivot", [0, 0, 0])
                cm = m @ _mat((cp[0] - pv[0], pv[1] - cp[1], cp[2] - pv[2]), [math.radians(a) for a in c["rotation"]])
                o = (org[0] - cp[0], cp[1] - org[1] - t[1], org[2] - cp[2])
            else:
                cm = m
                o = (org[0] - pv[0], pv[1] - org[1] - t[1], org[2] - pv[2])
            if isinstance(uv, dict):
                qs = _faces(o[0], o[1], o[2], t[0], t[1], t[2], gonfle, tw, th, uv)
            else:
                qs = _boite(uv[0], uv[1], o[0], o[1], o[2], t[0], t[1], t[2], gonfle,
                            c.get("mirror", b.get("mirror", False)), tw, th)
            for q in qs:
                p = np.array([s[:3] for s in q])
                p = p @ cm[:3, :3].T + cm[:3, 3]
                p = np.stack([-p[:, 0], 24 - p[:, 1], p[:, 2]], axis=1)    # repère de l'objet tenu
                w = [s[3:] for s in q]
                for a, bb, cc in ((0, 1, 2), (0, 2, 3)):
                    pts.append([p[a], p[bb], p[cc]])
                    uvs.append([w[a], w[bb], w[cc]])
        for e in enfants.get(b["name"], []):
            visite(e, m)

    for r in racines:
        visite(r, np.eye(4))
    return np.array(pts, dtype=float).reshape(-1, 3, 3), np.array(uvs, dtype=float).reshape(-1, 3, 2)


# ====================================================================== modèle d'accessoire pris dans l'arme
def _deplacer(b, dx, dy, dz):
    p = b.get("pivot", [0, 0, 0])
    b["pivot"] = [_r(p[0] + dx), _r(p[1] + dy), _r(p[2] + dz)]
    for c in b.get("cubes", []) or []:
        c["origin"] = [_r(c["origin"][0] + dx), _r(c["origin"][1] + dy), _r(c["origin"][2] + dz)]
        if "pivot" in c or c.get("rotation"):
            q = c.get("pivot", [0, 0, 0])
            c["pivot"] = [_r(q[0] + dx), _r(q[1] + dy), _r(q[2] + dz)]


def _r(v):
    return round(float(v), 5) + 0.0


def extraire(acc, arme, garder, copies):
    """Modèle Bedrock de l'accessoire : os « garder » de l'arme et tous leurs enfants, leurs parents sans cubes ;
    copies côte à côte en x ; le tout centré sur l'origine (milieu de la boîte des cubes)."""
    g = arme["minecraft:geometry"][0]
    os_ = g["bones"]
    par_nom = {b["name"]: b for b in os_}
    enfants = {}
    for b in os_:
        enfants.setdefault(b.get("parent"), []).append(b)
    choisis, cubes = [], set()
    for nom in garder:
        if nom not in par_nom:
            raise ValueError("os %s absent du modèle de l'arme" % nom)
        pile = [par_nom[nom]]
        while pile:
            b = pile.pop()
            cubes.add(b["name"])
            pile += enfants.get(b["name"], [])
        b = par_nom[nom]
        while b.get("parent") in par_nom:
            b = par_nom[b["parent"]]
            choisis.append(b["name"])
    gardes = [b for b in os_ if b["name"] in cubes or b["name"] in choisis]
    os_neufs = []
    base = []
    for b in gardes:
        nb = copy.deepcopy(b)
        if b["name"] not in cubes:
            nb.pop("cubes", None)
        if nb.get("parent") not in par_nom or (nb["parent"] not in cubes and nb["parent"] not in choisis):
            nb.pop("parent", None)
        base.append(nb)
    modele = {"format_version": "1.12.0", "minecraft:geometry": [{"description": {
        "identifier": "geometry.%s" % acc.replace(":", "_"), "texture_width": g["description"]["texture_width"],
        "texture_height": g["description"]["texture_height"]}, "bones": base}]}
    if copies > 1:
        # chargeurs couplés : l'un devant l'autre (vus de côté, comme sur l'icône d'origine), un demi-pixel entre eux
        p, _ = triangles(modele)
        profondeur = p[..., 2].max() - p[..., 2].min()
        for k in range(copies):
            for b in base:
                nb = copy.deepcopy(b)
                nb["name"] = "%s_%d" % (b["name"], k + 1)
                if nb.get("parent"):
                    nb["parent"] = "%s_%d" % (nb["parent"], k + 1)
                _deplacer(nb, 0, 0, k * (profondeur + 0.5))
                os_neufs.append(nb)
        modele["minecraft:geometry"][0]["bones"] = os_neufs
    p, _ = triangles(modele)
    if not len(p):
        raise ValueError("aucun cube sous %s" % ", ".join(garder))
    mn, mx = p.reshape(-1, 3).min(axis=0), p.reshape(-1, 3).max(axis=0)
    c = (mn + mx) / 2                                         # repère de l'objet : x = -x Bedrock
    for b in modele["minecraft:geometry"][0]["bones"]:
        _deplacer(b, c[0], -c[1], -c[2])
    t = mx - mn
    d = modele["minecraft:geometry"][0]["description"]
    d["visible_bounds_width"] = _r(round(max(t[0], t[2]) / 16 + 0.5, 2))
    d["visible_bounds_height"] = _r(round(t[1] / 16 + 0.5, 2))
    d["visible_bounds_offset"] = [0, 0, 0]
    return modele


# ====================================================================== icône
def icone(tris, uvs, tex):
    """Icône RGBA TAILLE x TAILLE : vue orthographique de trois quarts, modèle centré, éclairage doux."""
    def rot(axe, deg):
        a = math.radians(deg)
        c, s = math.cos(a), math.sin(a)
        return (np.array([[1, 0, 0], [0, c, -s], [0, s, c]]) if axe == 0 else
                np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]]) if axe == 1 else
                np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]]))
    P = tris.reshape(-1, 3)
    c = (P.min(axis=0) + P.max(axis=0)) / 2
    R = rot(2, ROULIS) @ rot(0, TANGAGE) @ rot(1, -90 + LACET)
    v = ((P - c) @ R.T).reshape(-1, 3, 3)
    x0, x1, y0, y1 = v[..., 0].min(), v[..., 0].max(), v[..., 1].min(), v[..., 1].max()
    demi = max(x1 - x0, y1 - y0) / 2 * (1 + MARGE)
    W = TAILLE * SUR
    sx = (v[..., 0] - (x0 + x1) / 2) / demi * W / 2 + W / 2
    sy = W / 2 - (v[..., 1] - (y0 + y1) / 2) / demi * W / 2
    z = v[..., 2]
    n = np.cross(v[:, 1] - v[:, 0], v[:, 2] - v[:, 0])
    n /= np.maximum(np.linalg.norm(n, axis=1, keepdims=True), 1e-12)
    li = np.minimum(1.08, 0.55 + 0.55 * np.abs(n @ LUMIERE))
    th, tw = tex.shape[:2]
    prof = np.full((W, W), -1e30)
    img = np.zeros((W, W, 4))
    for t in range(len(v)):
        X, Y, Z = sx[t], sy[t], z[t]
        aire = (X[1] - X[0]) * (Y[2] - Y[0]) - (X[2] - X[0]) * (Y[1] - Y[0])
        if abs(aire) < 1e-9:
            continue
        bx0, bx1 = max(int(math.floor(X.min())), 0), min(int(math.ceil(X.max())), W - 1)
        by0, by1 = max(int(math.floor(Y.min())), 0), min(int(math.ceil(Y.max())), W - 1)
        if bx0 > bx1 or by0 > by1:
            continue
        gx, gy = np.meshgrid(np.arange(bx0, bx1 + 1) + 0.5, np.arange(by0, by1 + 1) + 0.5)
        w0 = ((X[1] - gx) * (Y[2] - gy) - (X[2] - gx) * (Y[1] - gy)) / aire
        w1 = ((X[2] - gx) * (Y[0] - gy) - (X[0] - gx) * (Y[2] - gy)) / aire
        w2 = 1 - w0 - w1
        dedans = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not dedans.any():
            continue
        zz = w0 * Z[0] + w1 * Z[1] + w2 * Z[2]
        bloc = prof[by0:by1 + 1, bx0:bx1 + 1]
        dedans &= zz > bloc
        if not dedans.any():
            continue
        u = w0 * uvs[t, 0, 0] + w1 * uvs[t, 1, 0] + w2 * uvs[t, 2, 0]
        vv = w0 * uvs[t, 0, 1] + w1 * uvs[t, 1, 1] + w2 * uvs[t, 2, 1]
        coul = tex[np.clip((vv * th).astype(int), 0, th - 1), np.clip((u * tw).astype(int), 0, tw - 1)]
        dedans &= coul[..., 3] >= 0.1                          # découpe, comme le rendu « cutout » de TaCZ
        if not dedans.any():
            continue
        bloc[dedans] = zz[dedans]
        cible = img[by0:by1 + 1, bx0:bx1 + 1]
        cible[dedans, :3] = coul[dedans, :3] * li[t]
        cible[dedans, 3] = 1.0
    pre = img.copy()
    pre[..., :3] *= pre[..., 3:4]
    pre = pre.reshape(TAILLE, SUR, TAILLE, SUR, 4).mean(axis=(1, 3))
    a = pre[..., 3:4]
    rgb = np.where(a > 0, pre[..., :3] / np.maximum(a, 1e-6), 0)
    return np.clip(np.concatenate([rgb, a], axis=-1) * 255 + 0.5, 0, 255).astype(np.uint8)


# ====================================================================== affichages
def affichage_avec_modele(texte, modele, texture):
    """Texte du display avec « model » et « texture » (ajoutés en tête, ou remplacés) ; reste du fichier intact."""
    nl = "\r\n" if "\r\n" in texte else "\n"
    for cle, val in (("model", modele), ("texture", texture)):
        motif = re.compile(r'("%s"\s*:\s*)"[^"]*"' % cle)
        if motif.search(texte):
            texte = motif.sub(lambda m, v=val: m.group(1) + json.dumps(v), texte, count=1)
    manque = [(c, v) for c, v in (("model", modele), ("texture", texture)) if not re.search(r'"%s"\s*:' % c, texte)]
    if manque:
        i = texte.index("{") + 1
        ajout = "".join('%s  "%s": %s,' % (nl, c, json.dumps(v)) for c, v in manque)
        texte = texte[:i] + ajout + texte[i:]
    return texte


# ====================================================================== calcul
def calculer():
    """[(accessoire, fichiers {chemin: contenu attendu (bytes ou tableau RGBA)}, icône avant, icône après)]."""
    out = []
    for acc, (arme_id, garder, tex_id, en_main, copies) in sorted(SOURCES.items()):
        f_arme = chercher(arme_id, "geo_models", ".json")
        f_tex = chercher(tex_id, "textures", ".png")
        f_aff = affichage(acc)
        if not (f_arme and f_tex and f_aff):
            raise SystemExit("[3d] %s : modèle, texture ou affichage introuvable" % acc)
        modele = extraire(acc, lire(f_arme), garder, copies)
        tris, uvs = triangles(modele)
        tex = np.asarray(Image.open(f_tex).convert("RGBA"), dtype=np.float64) / 255.0
        ic = icone(tris, uvs, tex)
        aff = lire(f_aff)
        ns_slot, ch_slot = aff["slot"].split(":", 1)
        f_slot = f_aff.parents[3] / ns_slot / "textures" / (ch_slot + ".png")
        if not f_slot.exists():
            f_slot = chercher(aff["slot"], "textures", ".png") or f_slot
        fichiers = {f_slot: ic}
        if en_main:
            nom = acc.replace(":", "_")
            f_geo = SORTIE / "assets" / PACK / "geo_models" / "attachment" / (nom + ".json")
            fichiers[f_geo] = (json.dumps(modele, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")
            texte = f_aff.read_bytes().decode("utf-8")
            fichiers[f_aff] = affichage_avec_modele(texte, "%s:attachment/%s" % (PACK, nom), tex_id).encode("utf-8")
        avant = np.asarray(Image.open(f_slot).convert("RGBA"), dtype=np.uint8) if f_slot.exists() else None
        out.append((acc, fichiers, avant, ic))
    f_meta = SORTIE / "gunpack.meta.json"
    out.append(("(gunpack)", {f_meta: (json.dumps(META, ensure_ascii=False, indent=2) + "\n").encode("utf-8")}, None, None))
    return out


def a_jour(f, attendu):
    if not f.exists():
        return False
    if isinstance(attendu, bytes):
        return f.read_bytes() == attendu
    actuel = np.asarray(Image.open(f).convert("RGBA"), dtype=np.int16)
    if actuel.shape != attendu.shape:
        return False
    return (np.abs(actuel - attendu.astype(np.int16)).max(axis=2) > 8).mean() <= ECART_PIXELS


def planche(lignes, sortie):
    from PIL import ImageFont
    try:
        police = ImageFont.truetype("DejaVuSans.ttf", 12)
    except OSError:
        police = ImageFont.load_default()
    col, L, H = 4, 300, 150
    lignes = [l for l in lignes if l[3] is not None]
    fond = Image.new("RGBA", (col * L, ((len(lignes) + col - 1) // col) * H), (40, 42, 48, 255))
    dr = ImageDraw.Draw(fond)
    for i, (acc, _, avant, apres) in enumerate(lignes):
        x, y = (i % col) * L, (i // col) * H
        dr.text((x + 6, y + 4), acc, fill=(220, 220, 220, 255), font=police)
        for j, im in enumerate((avant, apres)):
            if im is None:
                continue
            bx = x + 6 + j * 150
            for taille, dy in ((64, 22), (32, 92)):
                dr.rectangle([bx, y + dy, bx + taille - 1, y + dy + taille - 1], fill=(139, 139, 139, 255))
                fond.alpha_composite(Image.fromarray(im, "RGBA").resize((taille, taille), Image.LANCZOS), (bx, y + dy))
        dr.text((x + 76, y + 126), "avant", fill=(150, 150, 150, 255), font=police)
        dr.text((x + 226, y + 126), "après", fill=(150, 150, 150, 255), font=police)
    fond.convert("RGB").save(sortie)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apercu")
    p.add_argument("--installer", action="store_true")
    p.add_argument("--verifier", action="store_true")
    a = p.parse_args()
    if not (a.apercu or a.installer or a.verifier):
        p.error("--apercu DOSSIER, --installer ou --verifier")
    lignes = calculer()
    if a.apercu:
        Path(a.apercu).mkdir(parents=True, exist_ok=True)
        planche(lignes, Path(a.apercu) / "accessoires_3d.png")
        print("[3d] planche :", Path(a.apercu) / "accessoires_3d.png")
        return
    pas_a_jour = 0
    for acc, fichiers, _, _ in lignes:
        for f, attendu in fichiers.items():
            if a_jour(f, attendu):
                continue
            pas_a_jour += 1
            if a.verifier:
                print("[3d] %s : %s pas à jour" % (acc, f.relative_to(DEPOT)))
                continue
            f.parent.mkdir(parents=True, exist_ok=True)
            if isinstance(attendu, bytes):
                f.write_bytes(attendu)
            else:
                Image.fromarray(attendu, "RGBA").save(f, optimize=True)
            print("[3d] %s : %s" % (acc, f.relative_to(DEPOT)))
    print("[3d] %d accessoire(s), %d fichier(s) %s" % (len(lignes) - 1, pas_a_jour,
                                                       "pas à jour" if a.verifier else "écrit(s)"))
    if a.verifier and pas_a_jour:
        sys.exit(1)


if __name__ == "__main__":
    main()
