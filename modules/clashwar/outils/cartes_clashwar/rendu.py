"""Isometric and top-down renders of ClashWar structure maps, textured with the imposed client resource packs.

Pipeline: blocks -> boxes (blocs.py) -> visible faces (culling, sun shadows by ray marching, ambient occlusion)
-> painter's order -> integer ID buffer rasterised with PIL -> per-pixel texture lookup and shading (numpy)
-> crease lines -> 2x supersampling downscale -> overlays (spawns, sites, captions).

Usage: python rendu.py [--cartes id,id] [--sortie DOSSIER] [--oppose] [--seulement iso|dessus|tout] [--plat]
Reads the repository read-only; writes PNGs to the output folder only (build/cartes_clashwar/rendus by default).
"""
import argparse
import json
import math
import os
import time

import numpy as np
from PIL import Image, ImageDraw, ImageFont

import blocs as bl
import nbt_lecture as nl
from chemins_cartes import CARTES, DONNEES, POLICES, SORTIE

DATA = str(DONNEES)

DIRS = ["u", "d", "n", "s", "e", "w"]
NORMALES = {"u": (0, 1, 0), "d": (0, -1, 0), "n": (0, 0, -1), "s": (0, 0, 1), "e": (1, 0, 0), "w": (-1, 0, 0)}
TEINTE_FACE = {"u": 1.0, "d": 0.5, "n": 0.80, "s": 0.80, "e": 0.62, "w": 0.62}

POLICE = str(POLICES / "dmsans_medium.ttf")
POLICE_G = str(POLICES / "dmsans_bold.ttf")
POLICE_SB = POLICE

COUL_A = (255, 205, 60)     # attackers / spawn A
COUL_B = (90, 200, 120)     # defenders / spawn B
COUL_SITE = (255, 72, 72)

TEXTURES = True             # --plat switches to flat average colours


def police(taille, gras=False, semi=False):
    chemin = POLICE_G if gras else (POLICE_SB if semi else POLICE)
    try:
        return ImageFont.truetype(chemin, int(taille))
    except OSError:
        return ImageFont.load_default()


# ------------------------------------------------------------------------------------------------ loading
def dossier_carte(ident):
    """Folder holding structures/<ident>.nbt and cartes/<ident>.json: the clashwar module data."""
    return DATA


def charger(ident):
    base = dossier_carte(ident)
    size, palette, ids, _ = nl.grille(os.path.join(base, "structures", ident + ".nbt"))
    with open(os.path.join(base, "cartes", ident + ".json"), encoding="utf-8") as f:
        meta = json.load(f)
    return size, palette, ids, meta


def niveau_sol(meta):
    return int(math.floor(meta["points"]["spawn_a"][0][1])) - 1


# ------------------------------------------------------------------------------------------------ geometry
class Scene:
    """All boxes of a map, with per-face colours and textures, plus occupancy grids."""
    PAD = 3

    def __init__(self, size, palette, ids, retirer=None):
        self.size = size
        sx, sy, sz = size
        self.blocs = [bl.bloc(e) for e in palette]
        vis = np.array([b.visible for b in self.blocs] + [False])
        plein_p = np.array([b.plein for b in self.blocs] + [False])
        occ_p = np.array([b.occ for b in self.blocs] + [False])
        idx = np.where(ids >= 0, ids, len(self.blocs))
        present = vis[idx]
        if retirer is not None:
            present &= ~retirer
        self.idx = np.where(present, idx, len(self.blocs))
        self.present = present
        P = self.PAD
        self.plein = np.zeros((sx + 2 * P, sy + 2 * P, sz + 2 * P), bool)
        self.plein[P:P + sx, P:P + sy, P:P + sz] = present & plein_p[idx]
        self.occ = np.zeros_like(self.plein)
        self.occ[P:P + sx, P:P + sy, P:P + sz] = present & (occ_p[idx] | plein_p[idx])
        tr_names = {}
        tr_p = np.zeros(len(self.blocs) + 1, np.int32)
        for i, b in enumerate(self.blocs):
            if b.transl:
                tr_p[i] = tr_names.setdefault(b.transl, len(tr_names) + 1)
        self.transl = np.zeros(self.plein.shape, np.int32)
        self.transl[P:P + sx, P:P + sy, P:P + sz] = np.where(present, tr_p[idx], 0)
        self.refs = []          # texture references, indexed by TX
        self._ref_idx = {}
        self._construire()

    def _ref(self, ref):
        if ref is None:
            return -1
        i = self._ref_idx.get(ref)
        if i is None:
            i = self._ref_idx[ref] = len(self.refs)
            self.refs.append(ref)
        return i

    def _faces_tex(self, bo):
        """(6,) tex ids, (6,4) coefficients, (6,3) tint multipliers for one box."""
        tx = np.full(6, -1, np.int32)
        cf = np.zeros((6, 4), np.float32)
        tn = np.ones((6, 3), np.float32)
        for k, d in enumerate(DIRS):
            t = bo.get("t", {}).get(d)
            if t is None:
                continue
            ref, coef, teinte = t
            tx[k] = self._ref(ref)
            cf[k] = coef
            if teinte is not None:
                tn[k] = np.array(teinte, np.float32) / 255.0
        return tx, cf, tn

    def _construire(self):
        P = self.PAD
        nb = len(self.blocs)
        simple = np.zeros(nb + 1, bool)
        pal_f = np.zeros((nb + 1, 6, 4), np.float32)
        pal_tx = np.full((nb + 1, 6), -1, np.int32)
        pal_cf = np.zeros((nb + 1, 6, 4), np.float32)
        pal_tn = np.ones((nb + 1, 6, 3), np.float32)
        for i, b in enumerate(self.blocs):
            if b.visible and b.plein and len(b.boites) == 1 and tuple(b.boites[0]["b"]) == (0, 0, 0, 16, 16, 16) \
                    and all(v[3] >= 0.999 for v in b.boites[0]["f"].values()) and len(b.boites[0]["f"]) == 6:
                simple[i] = True
                for k, d in enumerate(DIRS):
                    pal_f[i, k] = b.boites[0]["f"][d]
                pal_tx[i], pal_cf[i], pal_tn[i] = self._faces_tex(b.boites[0])
        cells = np.argwhere(self.present & simple[self.idx])
        pl = self.plein
        c = cells + P
        cache = np.ones(len(c), bool)
        for d in DIRS:
            n = NORMALES[d]
            cache &= pl[c[:, 0] + n[0], c[:, 1] + n[1], c[:, 2] + n[2]]
        cells = cells[~cache]
        pi = self.idx[cells[:, 0], cells[:, 1], cells[:, 2]]
        B1 = np.concatenate([cells, cells + 1], axis=1).astype(np.float32)
        C1 = cells.astype(np.int32)
        lots = [(B1, C1, pal_f[pi], pal_tx[pi], pal_cf[pi], pal_tn[pi], C1.copy())]
        L = {k: [] for k in ("B", "C", "F", "TX", "CF", "TN", "O")}
        autres = np.argwhere(self.present & ~simple[self.idx])
        for (x, y, z) in autres:
            b = self.blocs[self.idx[x, y, z]]
            for bo in b.boites:
                q = bo["b"]
                w = (x + q[0] / 16.0, y + q[1] / 16.0, z + q[2] / 16.0, x + q[3] / 16.0, y + q[4] / 16.0, z + q[5] / 16.0)
                f = np.zeros((6, 4), np.float32)
                for k, d in enumerate(DIRS):
                    if d in bo["f"]:
                        f[k] = bo["f"][d]
                tx, cf, tn = self._faces_tex(bo)
                self._decouper(w, f, (x, y, z), tx, cf, tn, L)
        if L["B"]:
            lots.append((np.array(L["B"], np.float32), np.array(L["C"], np.int32), np.array(L["F"], np.float32),
                         np.array(L["TX"], np.int32), np.array(L["CF"], np.float32), np.array(L["TN"], np.float32),
                         np.array(L["O"], np.int32)))
        self.B, self.C, self.F, self.TX, self.CF, self.TN, self.O = (np.concatenate([l[i] for l in lots]) for i in range(7))

    def _decouper(self, w, f, propre, tx, cf, tn, L):
        x0, y0, z0, x1, y1, z1 = w
        eps = 1e-4
        rx = range(int(math.floor(x0 + eps)), int(math.ceil(x1 - eps)))
        ry = range(int(math.floor(y0 + eps)), int(math.ceil(y1 - eps)))
        rz = range(int(math.floor(z0 + eps)), int(math.ceil(z1 - eps)))
        P = self.PAD
        unique = len(rx) == 1 and len(ry) == 1 and len(rz) == 1
        for cx in rx:
            for cy in ry:
                for cz in rz:
                    if not unique and (cx, cy, cz) != tuple(propre):
                        i, j, k = cx + P, cy + P, cz + P
                        if 0 <= i < self.plein.shape[0] and 0 <= j < self.plein.shape[1] and 0 <= k < self.plein.shape[2]:
                            if self.plein[i, j, k]:
                                continue
                    b = (max(x0, cx), max(y0, cy), max(z0, cz), min(x1, cx + 1), min(y1, cy + 1), min(z1, cz + 1))
                    if b[3] - b[0] <= eps or b[4] - b[1] <= eps or b[5] - b[2] <= eps:
                        continue
                    ff = f.copy()
                    if b[4] < y1 - eps:
                        ff[0, 3] = 0
                    if b[1] > y0 + eps:
                        ff[1, 3] = 0
                    if b[2] > z0 + eps:
                        ff[2, 3] = 0
                    if b[5] < z1 - eps:
                        ff[3, 3] = 0
                    if b[3] < x1 - eps:
                        ff[4, 3] = 0
                    if b[0] > x0 + eps:
                        ff[5, 3] = 0
                    L["B"].append(b)
                    L["C"].append((cx, cy, cz))
                    L["F"].append(ff)
                    L["TX"].append(tx)
                    L["CF"].append(cf)
                    L["TN"].append(tn)
                    L["O"].append(tuple(propre))

    def faces(self, dirs_visibles):
        """Visible face indices per direction (culling against full neighbours and identical translucent ones)."""
        P = self.PAD
        B, C, F = self.B, self.C, self.F
        out = []
        for d in dirs_visibles:
            k = DIRS.index(d)
            n = NORMALES[d]
            a = F[:, k, 3] > 0
            axe = 0 if n[0] else (1 if n[1] else 2)
            if n[axe] > 0:
                sur_bord = np.abs(B[:, axe + 3] - (C[:, axe] + 1)) < 1e-4
            else:
                sur_bord = np.abs(B[:, axe] - C[:, axe]) < 1e-4
            nx = np.clip(C[:, 0] + n[0] + P, 0, self.plein.shape[0] - 1)
            ny = np.clip(C[:, 1] + n[1] + P, 0, self.plein.shape[1] - 1)
            nz = np.clip(C[:, 2] + n[2] + P, 0, self.plein.shape[2] - 1)
            voisin_plein = self.plein[nx, ny, nz]
            tr_soi = self.transl[C[:, 0] + P, C[:, 1] + P, C[:, 2] + P]
            tr_v = self.transl[nx, ny, nz]
            meme_tr = (tr_soi > 0) & (tr_soi == tr_v) & (F[:, k, 3] < 0.999)
            garde = a & ~(sur_bord & (voisin_plein | meme_tr))
            out.append((d, np.nonzero(garde)[0]))
        return out


def coins_face(B, d):
    """4 corners (N,4,3) of face d of boxes B (N,6); corner 0->1 is the s axis, 0->3 the t axis."""
    x0, y0, z0, x1, y1, z1 = (B[:, i] for i in range(6))
    N = len(B)
    if d in ("u", "d"):
        y = y1 if d == "u" else y0
        xs = np.stack([x0, x1, x1, x0], 1)
        zs = np.stack([z0, z0, z1, z1], 1)
        ys = np.repeat(y[:, None], 4, 1)
    elif d in ("e", "w"):
        x = x1 if d == "e" else x0
        ys = np.stack([y0, y1, y1, y0], 1)
        zs = np.stack([z0, z0, z1, z1], 1)
        xs = np.repeat(x[:, None], 4, 1)
    else:
        z = z1 if d == "s" else z0
        xs = np.stack([x0, x1, x1, x0], 1)
        ys = np.stack([y0, y0, y1, y1], 1)
        zs = np.repeat(z[:, None], 4, 1)
    return np.stack([xs, ys, zs], 2).reshape(N, 4, 3)


# ------------------------------------------------------------------------------------------------ lighting
def direction_soleil(azimut_deg=-110.0, elevation_deg=50.0):
    """Unit vector towards the sun. azimut measured from +z (south) towards +x (east): -110 = west-south-west."""
    a, e = math.radians(azimut_deg), math.radians(elevation_deg)
    return np.array([math.cos(e) * math.sin(a), math.sin(e), math.cos(e) * math.cos(a)], np.float64)


def eclaire(scene, pts, cell_propre, L, pas=0.25):
    """Ray-march from points toward the sun through the occupancy grid. Returns bool lit."""
    P = scene.PAD
    occ = scene.occ
    M = len(pts)
    lit = np.ones(M, bool)
    actifs = np.arange(M)
    t = 0.0
    smax = np.array(occ.shape) - P
    while actifs.size:
        q = pts[actifs] + L[None, :] * t
        ci = np.floor(q).astype(np.int64)
        dehors = (q[:, 1] >= scene.size[1] + 0.5) | (ci[:, 0] < -P) | (ci[:, 2] < -P) | (ci[:, 0] >= smax[0]) | (ci[:, 2] >= smax[2])
        ci = ci[~dehors]
        a2 = actifs[~dehors]
        cy = np.clip(ci[:, 1] + P, 0, occ.shape[1] - 1)
        touche = occ[ci[:, 0] + P, cy, ci[:, 2] + P] & (ci[:, 1] >= -P)
        if t > 0:
            touche &= ~(ci == cell_propre[a2]).all(1)
        else:
            touche[:] = False
        lit[a2[touche]] = False
        actifs = a2[~touche]
        t += pas
    return lit


def occlusion(scene, C, d):
    """Simple per-face ambient occlusion: count of full blocks around the cell in front of the face."""
    P = scene.PAD
    n = NORMALES[d]
    axe = 0 if n[0] else (1 if n[1] else 2)
    autres = [i for i in range(3) if i != axe]
    f = C + np.array(n)[None, :] + P
    cnt = np.zeros(len(C), np.float64)
    for da in (-1, 0, 1):
        for db in (-1, 0, 1):
            if da == 0 and db == 0:
                continue
            q = f.copy()
            q[:, autres[0]] += da
            q[:, autres[1]] += db
            for i in range(3):
                q[:, i] = np.clip(q[:, i], 0, scene.plein.shape[i] - 1)
            poids = 1.0 if (da == 0 or db == 0) else 0.6
            cnt = cnt + poids * scene.plein[q[:, 0], q[:, 1], q[:, 2]]
    return np.clip(1.0 - 0.075 * cnt, 0.55, 1.0)


def bruit(C, ampl=0.09):
    h = (C[:, 0].astype(np.int64) * 73856093) ^ (C[:, 1].astype(np.int64) * 19349663) ^ (C[:, 2].astype(np.int64) * 83492791)
    return (1.0 - ampl / 2) + ampl * ((h & 1023) / 1023.0)


def multiplicateur(fac, lit, ndotl, force=0.60):
    """Per-face colour multiplier (N,3): face shade, cast shadow, warm light / cool shadow."""
    chaud = np.array([1.03, 1.0, 0.95])
    froid = np.array([0.93, 0.97, 1.08])
    if ndotl > 0:
        ombre = np.where(lit, 1.0, force)
        t = np.where(lit, 1.0, 0.0)[:, None]
        return (fac * ombre)[:, None] * (chaud * t + froid * (1 - t))
    return fac[:, None] * froid


# ------------------------------------------------------------------------------------------------ view
class Vue:
    def __init__(self, azimut, tangage):
        a, p = math.radians(azimut), math.radians(tangage)
        self.c = np.array([math.cos(p) * math.sin(a), math.sin(p), math.cos(p) * math.cos(a)])
        self.r = np.array([math.cos(a), 0.0, -math.sin(a)])
        self.u = np.array([-math.sin(p) * math.sin(a), math.cos(p), -math.sin(p) * math.cos(a)])
        self.dirs = [d for d in DIRS if np.dot(NORMALES[d], self.c) > 1e-6]

    def projeter(self, pts):
        return pts @ self.r, -(pts @ self.u)


AXE_U = bl.AXE_U
AXE_V = bl.AXE_V


def construire_faces(scene, dirs, L, ordre, force=0.60, hfac=None, bruit_ampl=0.09):
    """All visible faces (split into 4 on shadow boundaries) with geometry, shading and texture mapping,
    sorted by `ordre(box_index_array) -> sort keys tuple`."""
    B, C, F = scene.B, scene.C, scene.F
    parts = {k: [] for k in ("P3", "base", "mult", "alpha", "tex", "uvc", "key", "ordre")}
    for d, ii in scene.faces(dirs):
        if len(ii) == 0:
            continue
        k = DIRS.index(d)
        n = np.array(NORMALES[d], np.float64)
        coins = coins_face(B[ii], d).astype(np.float64)
        Cc = C[ii]
        fac = TEINTE_FACE[d] * occlusion(scene, Cc, d) * bruit(Cc, bruit_ampl)
        if hfac is not None:
            fac = fac * hfac(B[ii])
        ndotl = float(np.dot(n, L))
        sous = [coins[:, 0] * (1 - s) * (1 - t) + coins[:, 1] * s * (1 - t) + coins[:, 2] * s * t + coins[:, 3] * (1 - s) * t
                for (s, t) in ((0.25, 0.25), (0.75, 0.25), (0.75, 0.75), (0.25, 0.75))]
        if ndotl > 0:
            pts = np.concatenate(sous) + n[None, :] * 0.03 + L[None, :] * 0.03
            lit = eclaire(scene, pts, np.concatenate([Cc] * 4), L).reshape(4, -1).T
        else:
            lit = np.zeros((len(ii), 4), bool)
        tous, aucun = lit.all(1), (~lit).all(1)
        uni = tous | aucun
        # texture mapping data
        tex = scene.TX[ii, k]
        cf = scene.CF[ii, k].astype(np.float64)
        tn = scene.TN[ii, k].astype(np.float64)
        O = scene.O[ii].astype(np.float64)
        ua, va = AXE_U[d], AXE_V[d]
        plan = coins[:, 0, 1] if d in ("u", "d") else (coins[:, 0, 0] if d in ("e", "w") else coins[:, 0, 2])
        cle = (k + 1) * 1_000_000 + np.round(plan * 32).astype(np.int64) + 500_000
        base = F[ii, k, :3].astype(np.float64)
        alpha = F[ii, k, 3].astype(np.float64)
        ok = scene.ordre_cles(ii)

        def ajouter(sel, quads, lit_sel):
            c0 = quads[:, 0]
            e1 = quads[:, 1] - c0
            e2 = quads[:, 3] - c0
            au, bu, av, bv = cf[sel, 0], cf[sel, 1], cf[sel, 2], cf[sel, 3]
            Au = au + bu * (c0[:, ua] - O[sel, ua])
            Av = av + bv * (c0[:, va] - O[sel, va])
            uvc = np.stack([Au, bu * e1[:, ua], bu * e2[:, ua], Av, bv * e1[:, va], bv * e2[:, va]], 1)
            m = multiplicateur(fac[sel], lit_sel, ndotl, force) * tn[sel]
            parts["P3"].append(quads)
            parts["base"].append(base[sel])
            parts["mult"].append(m)
            parts["alpha"].append(alpha[sel])
            parts["tex"].append(tex[sel])
            parts["uvc"].append(uvc)
            parts["key"].append(cle[sel])
            parts["ordre"].append(ok[sel])

        u = np.nonzero(uni)[0]
        ajouter(u, coins[u], tous[u])
        m = np.nonzero(~uni)[0]
        if len(m):
            q = coins[m]
            c0, c1, c2, c3 = q[:, 0], q[:, 1], q[:, 2], q[:, 3]
            m01, m12, m23, m30 = (c0 + c1) / 2, (c1 + c2) / 2, (c2 + c3) / 2, (c3 + c0) / 2
            cc = (c0 + c1 + c2 + c3) / 4
            subs = [np.stack([c0, m01, cc, m30], 1), np.stack([m01, c1, m12, cc], 1),
                    np.stack([cc, m12, c2, m23], 1), np.stack([m30, cc, m23, c3], 1)]
            for s in range(4):
                ajouter(m, subs[s], lit[m, s])
    R = {k: np.concatenate(v) for k, v in parts.items()}
    o = R.pop("ordre")
    perm = np.lexsort(tuple(o[:, i] for i in range(o.shape[1] - 1, -1, -1)))
    return {k: v[perm] for k, v in R.items()}


def ordre_iso(scene, vue):
    B, C = scene.B, scene.C
    pc = (C + 0.5) @ vue.c
    pb = ((B[:, :3] + B[:, 3:]) / 2.0) @ vue.c
    allk = np.stack([pc, pb, np.arange(len(B), dtype=np.float64)], 1)
    scene.ordre_cles = lambda ii: allk[ii]


def ordre_dessus(scene):
    B = scene.B
    allk = np.stack([B[:, 4].astype(np.float64), np.arange(len(B), dtype=np.float64)], 1)
    scene.ordre_cles = lambda ii: allk[ii]


# ------------------------------------------------------------------------------------------------ textures & compositing
FEUILLES_SOMBRES = 0.55


def atlas_textures(refs, R):
    """(T,R,R,3) float atlas; transparent texels are filled (leaves dark, others with the average)."""
    a = bl.assets()
    out = np.zeros((max(1, len(refs)), R, R, 3), np.float32)
    for i, ref in enumerate(refs):
        im = a.image(ref)
        if im is None:
            out[i] = 128
            continue
        arr = np.asarray(im).astype(np.float32)
        al = arr[..., 3:4] / 255.0
        tot = al.sum()
        moy = (arr[..., :3] * al).sum((0, 1)) / max(tot, 1e-6)
        fond = moy * (FEUILLES_SOMBRES if ref.endswith("_leaves") else 1.0)
        rgb = arr[..., :3] * al + fond[None, None, :] * (1 - al)
        img = Image.fromarray(np.clip(rgb, 0, 255).astype(np.uint8))
        w, h = img.size
        img = img.resize((R, R), Image.BOX if max(w, h) > R else Image.NEAREST)
        out[i] = np.asarray(img).astype(np.float32)
    return out


def resolution_atlas(px_par_bloc):
    r = 2 ** int(math.floor(math.log2(max(1.0, px_par_bloc))))
    return int(min(64, max(16, r)))


def _rasteriser(W, H, XY, garder):
    img = Image.new("I", (W, H), 0)
    dr = ImageDraw.Draw(img)
    xy = XY.tolist()
    g = garder.tolist()
    for i in range(len(xy)):
        if g[i]:
            p = xy[i]
            pts = [(p[0][0], p[0][1]), (p[1][0], p[1][1]), (p[2][0], p[2][1]), (p[3][0], p[3][1])]
            dr.polygon(pts, fill=i + 1, outline=i + 1)
    return np.asarray(img, dtype=np.int32)


def _couleurs(ids, XY, Fc, atlas, R, inv, bloc=1_500_000):
    """Colours (M,3) of the pixels covered by face ids (flattened arrays)."""
    H, W = ids.shape
    flat = ids.ravel()
    sel = np.nonzero(flat)[0]
    out = np.zeros((len(sel), 3), np.float32)
    c0 = XY[:, 0, :]
    for a in range(0, len(sel), bloc):
        s_idx = sel[a:a + bloc]
        f = flat[s_idx] - 1
        py = (s_idx // W).astype(np.float64) + 0.5
        px = (s_idx % W).astype(np.float64) + 0.5
        dx = px - c0[f, 0]
        dy = py - c0[f, 1]
        s = np.clip(inv[f, 0] * dx + inv[f, 1] * dy, 0.0, 1.0)
        t = np.clip(inv[f, 2] * dx + inv[f, 3] * dy, 0.0, 1.0)
        uvc = Fc["uvc"][f]
        u = uvc[:, 0] + uvc[:, 1] * s + uvc[:, 2] * t
        v = uvc[:, 3] + uvc[:, 4] * s + uvc[:, 5] * t
        col = Fc["base"][f].astype(np.float32)
        tex = Fc["tex"][f]
        has = tex >= 0
        if TEXTURES and has.any():
            tx = np.clip(np.floor(u[has] * R), 0, R - 1).astype(np.int64)
            ty = np.clip(np.floor(v[has] * R), 0, R - 1).astype(np.int64)
            col[has] = atlas[tex[has], ty, tx]
        col *= Fc["mult"][f].astype(np.float32)
        out[a:a + bloc] = col
    return sel, out


def composer(W, H, XY, Fc, refs, R, fond, contours=0.80):
    """Rasterise faces (painter's order) into an ID buffer, texture and shade every pixel. Returns uint8 (H,W,3)."""
    atlas = atlas_textures(refs, R) if TEXTURES else np.zeros((1, R, R, 3), np.float32)
    c0 = XY[:, 0, :]
    e1 = XY[:, 1, :] - c0
    e2 = XY[:, 3, :] - c0
    det = e1[:, 0] * e2[:, 1] - e1[:, 1] * e2[:, 0]
    det = np.where(np.abs(det) < 1e-9, 1e-9, det)
    inv = np.stack([e2[:, 1] / det, -e2[:, 0] / det, -e1[:, 1] / det, e1[:, 0] / det], 1)
    opaque = Fc["alpha"] >= 0.99
    ids = _rasteriser(W, H, XY, opaque)
    img = fond.reshape(-1, 3).astype(np.float32).copy()
    sel, col = _couleurs(ids, XY, Fc, atlas, R, inv)
    img[sel] = col
    if (~opaque).any():
        ids_t = _rasteriser(W, H, XY, np.ones(len(XY), bool))
        f = ids_t.ravel()
        tsel = np.nonzero(f)[0]
        tsel = tsel[~opaque[f[tsel] - 1]]
        if len(tsel):
            ids_only = np.zeros_like(f)
            ids_only[tsel] = f[tsel]
            s2, c2 = _couleurs(ids_only.reshape(H, W), XY, Fc, atlas, R, inv)
            a = Fc["alpha"][ids_only[s2] - 1][:, None].astype(np.float32)
            img[s2] = a * c2 + (1 - a) * img[s2]
            ids = np.where(ids_only.reshape(H, W) > 0, ids_only.reshape(H, W), ids)
    if contours:
        cle = np.concatenate([[0], Fc["key"]])[ids]
        bord = np.zeros((H, W), bool)
        bord[:, :-1] |= cle[:, :-1] != cle[:, 1:]
        bord[:, 1:] |= cle[:, 1:] != cle[:, :-1]
        bord[:-1, :] |= cle[:-1, :] != cle[1:, :]
        bord[1:, :] |= cle[1:, :] != cle[:-1, :]
        bord &= ids > 0
        img.reshape(H, W, 3)[bord] *= contours
    return np.clip(img, 0, 255).astype(np.uint8).reshape(H, W, 3)


# ------------------------------------------------------------------------------------------------ cutaway
def retrait_bords(size, ids, meta, palette, vue):
    """Mask of blocks to hide: border rows (outside the playable bounds) on the two camera-side edges, above the floor."""
    sx, sy, sz = size
    sol = niveau_sol(meta)
    bornes = meta.get("bornes", [0, 0, 0, sx - 1, sy - 1, sz - 1])
    masque = np.zeros(size, bool)
    lignes = []
    if vue.c[0] > 0:
        w = max(1, sx - 1 - int(bornes[3]))
        masque[sx - w:, sol + 1:, :] = True
        lignes.append(("est", w))
    elif vue.c[0] < 0:
        w = max(1, int(bornes[0]))
        masque[:w, sol + 1:, :] = True
        lignes.append(("ouest", w))
    if vue.c[2] > 0:
        w = max(1, sz - 1 - int(bornes[5]))
        masque[:, sol + 1:, sz - w:] = True
        lignes.append(("sud", w))
    elif vue.c[2] < 0:
        w = max(1, int(bornes[2]))
        masque[:, sol + 1:, :w] = True
        lignes.append(("nord", w))
    return masque, lignes


# ------------------------------------------------------------------------------------------------ overlays
def etat_point(scene, vue, p):
    """'' if visible, 'sous un toit' if covered from above, 'caché' if hidden from the camera."""
    P = scene.PAD
    occ = scene.occ
    sx, sy, sz = scene.size
    x, y, z = p
    ix, iz = int(math.floor(x)), int(math.floor(z))
    y0 = int(math.floor(y)) + 2
    if 0 <= ix < sx and 0 <= iz < sz and y0 < sy:
        if occ[ix + P, y0 + P:sy + P, iz + P].any():
            return "sous un toit"
    q = np.array([x, y + 1.0, z], np.float64)
    c = vue.c / np.linalg.norm(vue.c)
    t = 0.6
    while True:
        r = q + c * t
        if r[1] > sy + 1:
            return ""
        i, j, k = (int(math.floor(v)) for v in r)
        if not (-P <= i < sx + P and -P <= k < sz + P):
            return ""
        if 0 <= j + P < occ.shape[1] and occ[i + P, j + P, k + P]:
            return "caché"
        t += 0.25


def _ligne_pointillee(dr, a, b, coul, largeur, pas):
    lg = math.hypot(b[0] - a[0], b[1] - a[1])
    n = max(1, int(lg / pas))
    for i in range(0, n, 2):
        u, v = i / n, min(1.0, (i + 1) / n)
        dr.line([(a[0] + (b[0] - a[0]) * u, a[1] + (b[1] - a[1]) * u), (a[0] + (b[0] - a[0]) * v, a[1] + (b[1] - a[1]) * v)],
                fill=coul, width=largeur)


def _texte_bulle(dr, xy, texte, fnt, fond, fg=(255, 255, 255), pad=6, ancre="mb"):
    bbox = dr.textbbox(xy, texte, font=fnt, anchor=ancre)
    r = (bbox[0] - pad, bbox[1] - pad, bbox[2] + pad, bbox[3] + pad)
    dr.rounded_rectangle(r, radius=pad, fill=fond)
    dr.text(xy, texte, font=fnt, fill=fg, anchor=ancre)
    return r


def epingle(dr, proj, p, texte, coul, SS, hauteur=5.0, fnt=None, cache=False):
    x, y, z = p
    X0, Y0 = proj(np.array([[x, y, z]]))
    X1, Y1 = proj(np.array([[x, y + hauteur, z]]))
    X0, Y0, X1, Y1 = float(X0[0]), float(Y0[0]), float(X1[0]), float(Y1[0])
    rr = 5 * SS
    if cache:
        _ligne_pointillee(dr, (X0, Y0), (X1, Y1), (20, 20, 24, 200), int(5 * SS), 7 * SS)
        _ligne_pointillee(dr, (X0, Y0), (X1, Y1), coul + (230,), int(3 * SS), 7 * SS)
        dr.ellipse([X0 - rr, Y0 - rr * 0.6, X0 + rr, Y0 + rr * 0.6], outline=coul, width=int(2 * SS))
    else:
        dr.line([(X0, Y0), (X1, Y1)], fill=(20, 20, 24), width=int(5 * SS))
        dr.line([(X0, Y0), (X1, Y1)], fill=coul, width=int(3 * SS))
        dr.ellipse([X0 - rr, Y0 - rr * 0.6, X0 + rr, Y0 + rr * 0.6], fill=coul, outline=(20, 20, 24), width=int(1.5 * SS))
    fond = tuple(int(v * 0.55) for v in coul) + (235,)
    _texte_bulle(dr, (X1, Y1 - 4 * SS), texte, fnt, fond, pad=int(6 * SS))


def anneau_sol(dr, proj, cx, cz, y, demi, coul, SS, cache=False):
    pts = np.array([[cx - demi, y, cz - demi], [cx + demi, y, cz - demi], [cx + demi, y, cz + demi], [cx - demi, y, cz + demi]])
    X, Y = proj(pts)
    poly = list(zip(X.tolist(), Y.tolist()))
    if cache:
        for a, b in zip(poly, poly[1:] + poly[:1]):
            _ligne_pointillee(dr, a, b, coul + (230,), int(3 * SS), 7 * SS)
        return
    dr.line(poly + [poly[0]], fill=(20, 20, 24), width=int(5 * SS), joint="curve")
    dr.line(poly + [poly[0]], fill=coul, width=int(3 * SS), joint="curve")


def demi_site(meta):
    return 4.5 if meta.get("type") == "clashwar_rd" else 2.5


def _degrade(W, H, haut=(38, 44, 54), bas=(16, 18, 23)):
    f = np.linspace(0, 1, H)[:, None]
    g = (np.array(haut)[None, :] * (1 - f) + np.array(bas)[None, :] * f).astype(np.uint8)
    return np.repeat(g[:, None, :], W, 1)


# ------------------------------------------------------------------------------------------------ iso render
def rendu_iso(ident, sortie, azimut=45.0, tangage=35.0, largeur=1600, SS=2, avec_coupe=True, suffixe=""):
    t0 = time.time()
    size, palette, ids, meta = charger(ident)
    vue = Vue(azimut, tangage)
    masque, lignes = retrait_bords(size, ids, meta, palette, vue) if avec_coupe else (None, [])
    scene = Scene(size, palette, ids, masque)
    L = direction_soleil()
    ordre_iso(scene, vue)
    Fc = construire_faces(scene, vue.dirs, L, None)
    P3 = Fc["P3"]
    X, Y = vue.projeter(P3.reshape(-1, 3))
    X = X.reshape(-1, 4)
    Y = Y.reshape(-1, 4)
    marge, entete, pied = 40, 120, 74
    k = (largeur - 2 * marge) / (X.max() - X.min())
    Wf = largeur
    Hf = int((Y.max() - Y.min()) * k + 2 * marge + entete + pied)
    ox, oy = marge - X.min() * k, marge + entete - Y.min() * k
    W, H = Wf * SS, Hf * SS
    XY = np.stack([(X * k + ox) * SS, (Y * k + oy) * SS], 2)
    R = resolution_atlas(k * SS)
    arr = composer(W, H, XY, Fc, scene.refs, R, _degrade(W, H))
    img = Image.fromarray(arr)

    def proj(pts):
        x, y = vue.projeter(pts)
        return (x * k + ox) * SS, (y * k + oy) * SS

    dr = ImageDraw.Draw(img, "RGBA")
    pts = meta["points"]
    fnt = police(15 * SS, semi=True)
    demi = demi_site(meta)
    etats = {}
    for cle in ("bombe_a", "bombe_b", "spawn_a", "spawn_b"):
        if cle in pts:
            e = [etat_point(scene, vue, tuple(q[:3])) for q in pts[cle]]
            etats[cle] = "sous un toit" if all(x == "sous un toit" for x in e) else ("caché" if all(x for x in e) else "")
    for cle in ("bombe_a", "bombe_b"):
        if cle in pts:
            p = pts[cle][0]
            anneau_sol(dr, proj, p[0], p[2], p[1] + 0.05, demi, COUL_SITE, SS, cache=bool(etats.get(cle)))
    for cle, coul in (("spawn_a", COUL_A), ("spawn_b", COUL_B)):
        if cle in pts:
            for q in pts[cle]:
                Xq, Yq = proj(np.array([q[:3]]))
                rr = 4 * SS
                box = [Xq[0] - rr, Yq[0] - rr * 0.6, Xq[0] + rr, Yq[0] + rr * 0.6]
                if etats.get(cle):
                    dr.ellipse(box, outline=coul, width=SS)
                else:
                    dr.ellipse(box, fill=coul, outline=(20, 20, 24), width=SS)
    for cle, lettre in (("bombe_a", "A"), ("bombe_b", "B")):
        if cle in pts:
            p = pts[cle][0]
            e = etats.get(cle)
            epingle(dr, proj, (p[0], p[1], p[2]), "Site " + lettre + (f" ({e})" if e else ""), COUL_SITE, SS,
                    hauteur=6.0, fnt=fnt, cache=bool(e))
    for cle, texte, coul in (("spawn_a", "Spawn A (attaque)", COUL_A), ("spawn_b", "Spawn B (défense)", COUL_B)):
        if cle in pts:
            m = np.array([q[:3] for q in pts[cle]]).mean(0)
            e = etats.get(cle)
            if e:
                texte = texte[:-1] + f", {e})"
            epingle(dr, proj, tuple(m), texte, coul, SS, hauteur=7.0, fnt=fnt, cache=bool(e))
    img = img.resize((Wf, Hf), Image.LANCZOS)
    dr = ImageDraw.Draw(img, "RGBA")
    titre = f"{meta.get('nom_fr', ident)}  ·  {ident}"
    sx, sy, sz = size
    fmt = meta.get("format") or "tous formats (1v1 à 4v4)"
    dr.text((marge, 26), titre, font=police(34, gras=True), fill=(240, 242, 246))
    dr.text((marge, 72), f"{sx} × {sz} blocs, hauteur {sy}  ·  format : {fmt}  ·  thème : {meta.get('theme', '')}",
            font=police(18), fill=(190, 196, 206))
    cote = " et ".join(n for n, w in lignes)
    coin = {(1, 1): "sud-est", (-1, -1): "nord-ouest", (1, -1): "nord-est", (-1, 1): "sud-ouest"}[
        (int(np.sign(vue.c[0])), int(np.sign(vue.c[2])))]
    nx_, ny_ = vue.projeter(np.array([[0, 0, -1.0]]))
    ou_nord = ("en haut" if ny_[0] < 0 else "en bas") + (" à droite" if nx_[0] > 0 else " à gauche")
    note = (f"Vue 3D depuis le {coin} (le nord est {ou_nord}). "
            + (f"Bordure côté caméra ({cote}) coupée au ras du sol pour voir l'intérieur. " if lignes else "")
            + "Repère en pointillés = caché par un bâtiment ou sous un toit. "
            + ("Textures du pack imposé aux joueurs (couleurs moyennes pour les petits objets)." if TEXTURES
               else "Couleurs moyennes des textures du pack imposé."))
    fn = police(15)
    lignes_txt, cour = [], ""
    for mot in note.split(" "):
        essai = (cour + " " + mot).strip()
        if dr.textlength(essai, font=fn) > Wf - 2 * marge and cour:
            lignes_txt.append(cour)
            cour = mot
        else:
            cour = essai
    lignes_txt.append(cour)
    for i, t in enumerate(lignes_txt[:3]):
        dr.text((marge, Hf - pied + 10 + i * 21), t, font=fn, fill=(150, 156, 166))
    # compass
    cx, cy = Wf - marge - 60, 64
    v = np.array([nx_[0], ny_[0]])
    v = v / np.linalg.norm(v)
    tip = (cx + v[0] * 34, cy + v[1] * 34)
    tail = (cx - v[0] * 34, cy - v[1] * 34)
    dr.line([tail, tip], fill=(230, 232, 236), width=3)
    perp = np.array([-v[1], v[0]])
    dr.polygon([tip, (tip[0] - v[0] * 14 + perp[0] * 8, tip[1] - v[1] * 14 + perp[1] * 8),
                (tip[0] - v[0] * 14 - perp[0] * 8, tip[1] - v[1] * 14 - perp[1] * 8)], fill=(230, 232, 236))
    dr.text((tip[0] + v[0] * 14, tip[1] + v[1] * 14), "N", font=police(20, gras=True), fill=(255, 255, 255), anchor="mm")
    os.makedirs(sortie, exist_ok=True)
    chemin = os.path.join(sortie, f"{ident}_iso{suffixe}.png")
    img.save(chemin, optimize=True)
    print(f"{ident}: iso {Wf}x{Hf}, {len(P3)} faces, atlas {R}px, {time.time() - t0:.1f}s -> {chemin}")
    return chemin


# ------------------------------------------------------------------------------------------------ top view
def rendu_dessus(ident, sortie, px_bloc=None, cible=1400, SS=2):
    t0 = time.time()
    size, palette, ids, meta = charger(ident)
    sx, sy, sz = size
    scene = Scene(size, palette, ids, None)
    L = direction_soleil()
    sol = niveau_sol(meta)
    ordre_dessus(scene)
    hfac = lambda B: np.clip(0.80 + 0.20 * (B[:, 4] - (sol + 1)) / 10.0, 0.62, 1.12)  # noqa: E731
    Fc = construire_faces(scene, ["u"], L, None, force=0.74, hfac=hfac)
    if px_bloc is None:
        px_bloc = max(8, int(cible / sx))
    k = px_bloc
    marge, entete, pied = 56, 110, 104
    Wf = sx * k + 2 * marge
    Hf = sz * k + 2 * marge + entete + pied
    W, H = Wf * SS, Hf * SS
    ox, oy = marge, marge + entete
    P3 = Fc["P3"]
    XY = np.stack([(P3[:, :, 0] * k + ox) * SS, (P3[:, :, 2] * k + oy) * SS], 2)
    R = resolution_atlas(k * SS)
    fond = np.zeros((H, W, 3), np.uint8)
    fond[:] = (20, 22, 27)
    arr = composer(W, H, XY, Fc, scene.refs, R, fond, contours=0.72)
    img = Image.fromarray(arr)
    dr = ImageDraw.Draw(img, "RGBA")
    souterrain = souterrains(scene, sol)
    for (i, j) in np.argwhere(souterrain):
        x0, y0 = (i * k + ox) * SS, (j * k + oy) * SS
        dr.rectangle([x0, y0, x0 + k * SS - 1, y0 + k * SS - 1], fill=(80, 220, 255, 80))
    fg = police(12 * SS)
    for v in range(0, max(sx, sz) + 1, 16):
        if v <= sx:
            X0 = (v * k + ox) * SS
            dr.line([(X0, oy * SS), (X0, (sz * k + oy) * SS)], fill=(255, 255, 255, 40), width=SS)
            dr.text((X0, (oy - 8) * SS), str(v), font=fg, fill=(170, 176, 186), anchor="mb")
        if v <= sz:
            Y0 = (v * k + oy) * SS
            dr.line([(ox * SS, Y0), ((sx * k + ox) * SS, Y0)], fill=(255, 255, 255, 40), width=SS)
            dr.text(((ox - 8) * SS, Y0), str(v), font=fg, fill=(170, 176, 186), anchor="rm")
    pts = meta["points"]

    def pp(x, z):
        return ((x * k + ox) * SS, (z * k + oy) * SS)

    demi = demi_site(meta)
    for cle, coul in (("barriere_a", COUL_A), ("barriere_b", COUL_B)):
        if cle in pts and len(pts[cle]) >= 2:
            a, b = pts[cle][0], pts[cle][1]
            x0, x1 = sorted((a[0], b[0] + 1))
            z0, z1 = sorted((a[2], b[2] + 1))
            _pointilles(dr, [pp(x0, z0), pp(x1, z0), pp(x1, z1), pp(x0, z1)], coul + (220,), SS)
    if "zone" in meta and "rayon_min" in meta["zone"]:
        zc = meta["zone"]["centre"]
        cx, cz = pp(zc[0], zc[2])
        _cercle_pointille(dr, cx, cz, meta["zone"]["rayon_min"] * k * SS, (235, 235, 245, 200), SS)
        dr.line([(cx - 7 * SS, cz), (cx + 7 * SS, cz)], fill=(235, 235, 245, 230), width=2 * SS)
        dr.line([(cx, cz - 7 * SS), (cx, cz + 7 * SS)], fill=(235, 235, 245, 230), width=2 * SS)
    fnt = police(15 * SS, gras=True)
    for cle, lettre in (("bombe_a", "A"), ("bombe_b", "B")):
        if cle in pts:
            p = pts[cle][0]
            x0, z0 = pp(p[0] - demi, p[2] - demi)
            x1, z1 = pp(p[0] + demi, p[2] + demi)
            dr.rectangle([x0, z0, x1, z1], outline=(20, 20, 24, 255), width=5 * SS)
            dr.rectangle([x0, z0, x1, z1], outline=COUL_SITE + (255,), width=3 * SS)
            cx, cz = pp(p[0], p[2])
            rb = 13 * SS
            dr.ellipse([cx - rb, cz - rb, cx + rb, cz + rb], fill=COUL_SITE + (255,), outline=(20, 20, 24, 255), width=2 * SS)
            dr.text((cx, cz), lettre, font=fnt, fill=(255, 255, 255), anchor="mm")
    for cle, coul in (("spawn_a", COUL_A), ("spawn_b", COUL_B)):
        if cle in pts:
            for q in pts[cle]:
                cx, cz = pp(q[0], q[2])
                rb = 7 * SS
                dr.ellipse([cx - rb, cz - rb, cx + rb, cz + rb], fill=coul + (255,), outline=(20, 20, 24, 255), width=2 * SS)
                yaw = math.radians(q[3] if len(q) > 3 else 0)
                vx, vz = -math.sin(yaw), math.cos(yaw)  # Minecraft yaw: 0 = south
                dr.line([(cx, cz), (cx + vx * 16 * SS, cz + vz * 16 * SS)], fill=(20, 20, 24, 255), width=3 * SS)
    img = img.resize((Wf, Hf), Image.LANCZOS)
    dr = ImageDraw.Draw(img, "RGBA")
    ftxt = police(15, semi=True)

    def ppf(x, z):
        return (x * k + ox, z * k + oy)

    for cle, texte, coul in (("spawn_a", "Spawn A (attaque)", COUL_A), ("spawn_b", "Spawn B (défense)", COUL_B)):
        if cle in pts:
            arr_p = np.array([q[:3] for q in pts[cle]])
            zc = arr_p[:, 2].mean()
            fond_b = tuple(int(v * 0.45) for v in coul) + (235,)
            larg = dr.textlength(texte, font=ftxt) + 12
            gx, gz = ppf(arr_p[:, 0].max(), zc)
            if gx + 24 + larg < Wf - marge:
                _texte_bulle(dr, (gx + 18, gz), texte, ftxt, fond_b, pad=5, ancre="lm")
            else:
                gx, gz = ppf(arr_p[:, 0].min(), zc)
                _texte_bulle(dr, (gx - 18, gz), texte, ftxt, fond_b, pad=5, ancre="rm")
    dr.text((marge, 26), f"{meta.get('nom_fr', ident)}  ·  {ident}  ·  vue de dessus", font=police(30, gras=True),
            fill=(240, 242, 246))
    dr.text((marge, 68), f"{sx} × {sz} blocs  ·  nord en haut  ·  un carreau de la grille = 16 blocs  ·  {meta.get('theme', '')}",
            font=police(16), fill=(185, 190, 200))
    ly, lx = Hf - pied + 22, marge
    fl = police(15)

    def sym_spawn(x, y, c):
        dr.ellipse([x - 7, y - 7, x + 7, y + 7], fill=c, outline=(20, 20, 24), width=2)

    def sym_site(x, y, c):
        dr.rectangle([x - 8, y - 8, x + 8, y + 8], outline=c, width=3)

    def sym_barriere(x, y, c):
        for i in range(-9, 9, 6):
            dr.line([(x + i, y - 7), (x + i + 3, y - 7)], fill=c, width=2)
            dr.line([(x + i, y + 7), (x + i + 3, y + 7)], fill=c, width=2)
        dr.line([(x - 9, y - 7), (x - 9, y - 2)], fill=c, width=2)
        dr.line([(x + 9, y + 2), (x + 9, y + 7)], fill=c, width=2)

    def sym_zone(x, y, c):
        for i in range(0, 12, 2):
            a0, a1 = 2 * math.pi * i / 12, 2 * math.pi * (i + 1) / 12
            dr.line([(x + 8 * math.cos(a0), y + 8 * math.sin(a0)), (x + 8 * math.cos(a1), y + 8 * math.sin(a1))], fill=c, width=2)

    def sym_tunnel(x, y, c):
        dr.rectangle([x - 8, y - 8, x + 8, y + 8], fill=c + (90,), outline=c, width=1)

    items = [(sym_spawn, COUL_A, "Spawn A, attaque (trait = regard)"), (sym_spawn, COUL_B, "Spawn B, défense"),
             (sym_site, COUL_SITE, "Site de bombe (cadre = dalle)")]
    if any(k_ in pts for k_ in ("barriere_a", "barriere_b")):
        items.append((sym_barriere, (225, 225, 232), "Barrière de départ"))
    if "zone" in meta:
        items.append((sym_zone, (235, 235, 245), "Zone finale (rayon min.)"))
    if souterrain.any():
        items.append((sym_tunnel, (80, 220, 255), "Tunnel / sous-sol"))
    for sym, coul, txt in items:
        sym(lx + 9, ly, coul)
        dr.text((lx + 26, ly), txt, font=fl, fill=(205, 210, 218), anchor="lm")
        lx += 26 + dr.textlength(txt, font=fl) + 30
        if lx > Wf - 260:
            lx = marge
            ly += 28
    dr.text((marge, Hf - 34), "Plus clair = plus haut ; traits sombres = bords de murs et de toits ; ombres du soleil (ouest-sud-ouest). "
            + ("Textures du pack imposé aux joueurs." if TEXTURES else "Couleurs moyennes."), font=police(14), fill=(140, 146, 156))
    chemin = os.path.join(sortie, f"{ident}_dessus.png")
    img.save(chemin, optimize=True)
    print(f"{ident}: dessus {Wf}x{Hf}, atlas {R}px, {time.time() - t0:.1f}s -> {chemin}")
    return chemin


def _pointilles(dr, poly, coul, SS):
    pts = poly + [poly[0]]
    for (x0, y0), (x1, y1) in zip(pts, pts[1:]):
        lg = math.hypot(x1 - x0, y1 - y0)
        n = max(1, int(lg / (10 * SS)))
        for i in range(0, n, 2):
            a, b = i / n, min(1.0, (i + 1) / n)
            dr.line([(x0 + (x1 - x0) * a, y0 + (y1 - y0) * a), (x0 + (x1 - x0) * b, y0 + (y1 - y0) * b)], fill=coul, width=3 * SS)


def _cercle_pointille(dr, cx, cy, r, coul, SS):
    n = max(24, int(2 * math.pi * r / (8 * SS)))
    for i in range(0, n, 2):
        a0, a1 = 2 * math.pi * i / n, 2 * math.pi * (i + 1) / n
        dr.line([(cx + r * math.cos(a0), cy + r * math.sin(a0)), (cx + r * math.cos(a1), cy + r * math.sin(a1))], fill=coul, width=2 * SS)


def souterrains(scene, sol):
    """Columns with walkable air below the ground level (tunnels, basements)."""
    sx, sy, sz = scene.size
    P = scene.PAD
    occ = scene.plein[P:P + sx, P:P + sy, P:P + sz]
    res = np.zeros((sx, sz), bool)
    for y in range(1, max(1, sol - 1)):
        libre = ~occ[:, y, :] & ~occ[:, y + 1, :]
        sous = occ[:, y - 1, :]
        couvert = occ[:, y + 2:sol + 1, :].any(1) if y + 2 <= sol else np.zeros((sx, sz), bool)
        res |= libre & sous & couvert
    return res


def main():
    global TEXTURES
    ap = argparse.ArgumentParser()
    ap.add_argument("--cartes", default=",".join(CARTES))
    ap.add_argument("--sortie", default=str(SORTIE / "rendus"))
    ap.add_argument("--azimut", type=float, default=45.0)
    ap.add_argument("--tangage", type=float, default=35.0)
    ap.add_argument("--seulement", choices=["iso", "dessus", "tout"], default="tout")
    ap.add_argument("--suffixe", default="")
    ap.add_argument("--oppose", action="store_true", help="also render the opposite corner (north-west)")
    ap.add_argument("--plat", action="store_true", help="flat average colours instead of textures")
    a = ap.parse_args()
    TEXTURES = not a.plat
    os.makedirs(a.sortie, exist_ok=True)
    for ident in a.cartes.split(","):
        if a.seulement in ("iso", "tout"):
            rendu_iso(ident, a.sortie, a.azimut, a.tangage, suffixe=a.suffixe)
            if a.oppose:
                rendu_iso(ident, os.path.join(a.sortie, "angle_oppose"), a.azimut + 180.0, a.tangage, suffixe="_nord_ouest")
        if a.seulement in ("dessus", "tout"):
            rendu_dessus(ident, a.sortie)


if __name__ == "__main__":
    main()
