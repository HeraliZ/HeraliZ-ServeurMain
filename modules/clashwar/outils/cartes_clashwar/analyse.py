"""Objective metrics for the ClashWar maps (read-only): floating pieces, exposure, sightlines, verticality.

Usage: python analyse.py [--cartes id,id] [--json sortie.json]
"""
import argparse
import collections
import json
import math

import numpy as np
import scipy.ndimage as ndi

import blocs as bl
from chemins_cartes import CARTES
from rendu import charger, niveau_sol

PASSABLES = ("carpet", "rail", "torch", "lantern", "ladder", "button", "pressure_plate", "dead_bush", "fern",
             "flower", "chain", "lightning_rod", "_sign", "banner", "corde", "drapeau",
             "manche_a_air", "newspapers", "lampadaire_mat", "lampadaire_pied")
PASSABLES_EXACTS = {"light", "snow", "grass", "short_grass", "tall_grass", "bell", "air"}
VOIT_A_TRAVERS = ("_pane", "iron_bars", "glass", "_fence", "chain", "ladder", "barbeles", "herisson", "filet")


def masques(ident):
    size, palette, ids, meta = charger(ident)
    bs = [bl.bloc(e) for e in palette]
    n = len(bs)
    vis = np.array([b.visible for b in bs] + [False])
    occ = np.array([b.plein or b.occ for b in bs] + [False])
    court = [b.nom.split(":")[1] for b in bs]
    passable = np.array([any(k in c for k in PASSABLES) or c in PASSABLES_EXACTS for c in court] + [True])
    transparent = np.array([any(k in c for k in VOIT_A_TRAVERS) for c in court] + [True])
    support = np.array([b.plein or b.occ or any(k in c for k in ("_slab", "_stairs", "_fence", "_wall", "iron_bars", "_pane"))
                        for b, c in zip(bs, court)] + [False])
    idx = np.where(ids >= 0, ids, n)
    present = vis[idx]
    m = {
        "size": size, "meta": meta, "ids": ids, "palette": palette, "noms": court + ["air"], "idx": idx,
        "present": present,
        "bloque_pas": present & ~passable[idx],
        "bloque_vue": present & occ[idx] & ~transparent[idx],
        "support": present & support[idx],
        "occ": present & occ[idx],
    }
    return m


def bornes_xz(meta, size):
    b = meta.get("bornes", [0, 0, 0, size[0] - 1, size[1] - 1, size[2] - 1])
    return int(b[0]), int(b[3]), int(b[2]), int(b[5])


def flottants(m):
    pres = m["present"]
    res = {}
    for nom_c, st in (("6", ndi.generate_binary_structure(3, 1)), ("26", np.ones((3, 3, 3), bool))):
        lab, nb = ndi.label(pres, structure=st)
        if nb == 0:
            res[nom_c] = (0, 0, [])
            continue
        tailles = np.bincount(lab.ravel())
        tailles[0] = 0
        principal = int(np.argmax(tailles))
        autres = [i for i in range(1, nb + 1) if i != principal]
        total = int(sum(tailles[i] for i in autres))
        # describe the largest few
        gros = sorted(autres, key=lambda i: -tailles[i])[:4]
        desc = []
        objs = ndi.find_objects(lab)
        for i in gros:
            sl = objs[i - 1]
            sous = lab[sl] == i
            idxs = m["idx"][sl][sous]
            noms = collections.Counter(m["noms"][j] for j in idxs.tolist()).most_common(2)
            desc.append((int(tailles[i]), (sl[0].start, sl[1].start, sl[2].start), noms))
        res[nom_c] = (len(autres), total, desc)
    return res


def debout(m):
    """Standable cells: two passable cells above a support."""
    bp = m["bloque_pas"]
    sup = m["support"]
    st = np.zeros_like(bp)
    st[:, 1:-1, :] = ~bp[:, 1:-1, :] & ~bp[:, 2:, :] & sup[:, :-2, :]
    return st


def lignes_de_vue(m, y_oeil, x0, x1, z0, z1):
    libre = ~m["bloque_vue"][:, y_oeil, :]
    sub = libre[x0:x1 + 1, z0:z1 + 1]

    def runs(arr):
        best = 0
        counts = []
        for ligne in arr:
            r = 0
            mx = 0
            for v in ligne:
                r = r + 1 if v else 0
                mx = max(mx, r)
            counts.append(mx)
            best = max(best, mx)
        return best, counts

    best_ns, cols = runs(sub)        # along z for each x
    best_eo, rows = runs(sub.T)      # along x for each z
    return best_ns, best_eo, sum(1 for c in cols if c >= 64), sum(1 for r in rows if r >= 64)


def ligne_vue(m, a, b, hauteur=1.62):
    """True if eye-level straight line from a to b is clear (points are feet positions)."""
    bv = m["bloque_vue"]
    sx, sy, sz = m["size"]
    pa = np.array([a[0], a[1] + hauteur, a[2]])
    pb = np.array([b[0], b[1] + hauteur, b[2]])
    d = pb - pa
    L = np.linalg.norm(d)
    n = int(L / 0.2) + 1
    for i in range(1, n):
        p = pa + d * (i / n)
        x, y, z = (int(math.floor(v)) for v in p)
        if 0 <= x < sx and 0 <= y < sy and 0 <= z < sz and bv[x, y, z]:
            return False
    return True


def visibilite(m, p, y_oeil, sol_ext, x0, x1, z0, z1, pas_deg=0.5, portee=160):
    """Fraction of outdoor ground cells visible (at eye level) from point p."""
    bv = m["bloque_vue"][:, y_oeil, :]
    vu = np.zeros_like(bv)
    px, pz = p[0], p[2]
    for k in range(int(360 / pas_deg)):
        a = math.radians(k * pas_deg)
        dx, dz = math.cos(a), math.sin(a)
        t = 0.0
        while t < portee:
            x, z = px + dx * t, pz + dz * t
            ix, iz = int(math.floor(x)), int(math.floor(z))
            if not (x0 <= ix <= x1 and z0 <= iz <= z1):
                break
            if bv[ix, iz]:
                break
            vu[ix, iz] = True
            t += 0.25
    tot = sol_ext.sum()
    return float((vu & sol_ext).sum()) / max(1, tot)


def analyser(ident):
    m = masques(ident)
    meta = m["meta"]
    sx, sy, sz = m["size"]
    sol = niveau_sol(meta) + 1  # y where players stand
    x0, x1, z0, z1 = bornes_xz(meta, m["size"])
    res = {"id": ident, "nom": meta.get("nom_fr"), "taille": [sx, sy, sz], "y_sol": sol}
    # floating pieces
    fl = flottants(m)
    res["flottants_6"] = {"pieces": fl["6"][0], "blocs": fl["6"][1], "exemples": fl["6"][2]}
    res["flottants_26"] = {"pieces": fl["26"][0], "blocs": fl["26"][1], "exemples": fl["26"][2]}
    # standable cells & verticality
    st = debout(m)
    st[:x0, :, :] = False
    st[x1 + 1:, :, :] = False
    st[:, :, :z0] = False
    st[:, :, z1 + 1:] = False
    ys = np.nonzero(st)[1]
    tot = len(ys)
    res["debout_total"] = int(tot)
    res["debout_sol_pct"] = round(100.0 * np.sum(ys == sol) / max(1, tot), 1)
    res["debout_bas_pct"] = round(100.0 * np.sum((ys > sol) & (ys <= sol + 2)) / max(1, tot), 1)
    res["debout_haut_pct"] = round(100.0 * np.sum(ys > sol + 2) / max(1, tot), 1)
    res["debout_sous_sol_pct"] = round(100.0 * np.sum(ys < sol) / max(1, tot), 1)
    res["hauteur_max_debout"] = int(ys.max() - sol) if tot else 0
    # outdoor ground: standable at floor level and open to the sky
    occ = m["occ"]
    ciel = ~np.flip(np.logical_or.accumulate(np.flip(occ, axis=1), axis=1), axis=1)  # no occluder at or above
    sol_ext = st[:, sol, :] & ciel[:, sol + 2, :] if sol + 2 < sy else st[:, sol, :]
    res["sol_exterieur_pct_aire"] = round(100.0 * sol_ext.sum() / ((x1 - x0 + 1) * (z1 - z0 + 1)), 1)
    # cover = sight-blocking cell at crouch height (y = sol) or eye height
    couvert = m["bloque_vue"][:, sol, :] | m["bloque_vue"][:, sol + 1, :]
    dist = ndi.distance_transform_edt(~couvert)
    d_ext = dist[sol_ext]
    res["exterieur_loin_6_pct"] = round(100.0 * np.mean(d_ext > 6), 1) if d_ext.size else 0.0
    res["exterieur_loin_10_pct"] = round(100.0 * np.mean(d_ext > 10), 1) if d_ext.size else 0.0
    res["dist_couvert_moy"] = round(float(np.mean(d_ext)), 1) if d_ext.size else 0.0
    # sightlines at eye level
    y_oeil = sol + 1
    ns, eo, n_ns, n_eo = lignes_de_vue(m, y_oeil, x0, x1, z0, z1)
    res["plus_longue_ligne_ns"] = ns
    res["plus_longue_ligne_eo"] = eo
    res["couloirs_ns_64"] = n_ns
    res["couloirs_eo_64"] = n_eo
    # key points
    pts = meta["points"]
    sa = np.array([q[:3] for q in pts["spawn_a"]]).mean(0)
    sb = np.array([q[:3] for q in pts["spawn_b"]]).mean(0)
    ba = pts["bombe_a"][0][:3]
    bb = pts["bombe_b"][0][:3]
    res["vue_spawnA_siteA"] = ligne_vue(m, sa, ba)
    res["vue_spawnA_siteB"] = ligne_vue(m, sa, bb)
    res["vue_spawnA_spawnB"] = ligne_vue(m, sa, sb)
    res["vue_spawnB_siteA"] = ligne_vue(m, sb, ba)
    res["vue_spawnB_siteB"] = ligne_vue(m, sb, bb)
    sa_l = [q[:3] for q in pts["spawn_a"]]
    sb_l = [q[:3] for q in pts["spawn_b"]]
    res["paires_spawnA_voit_spawnB"] = f"{sum(ligne_vue(m, a, b) for a in sa_l for b in sb_l)}/{len(sa_l) * len(sb_l)}"
    res["spawnA_voit_siteA"] = f"{sum(ligne_vue(m, a, ba) for a in sa_l)}/{len(sa_l)}"
    res["spawnA_voit_siteB"] = f"{sum(ligne_vue(m, a, bb) for a in sa_l)}/{len(sa_l)}"
    res["dist_spawnA_siteA"] = round(float(math.dist((sa[0], sa[2]), (ba[0], ba[2]))), 1)
    res["dist_spawnA_siteB"] = round(float(math.dist((sa[0], sa[2]), (bb[0], bb[2]))), 1)
    res["dist_spawnB_sites"] = round(float(min(math.dist((sb[0], sb[2]), (ba[0], ba[2])), math.dist((sb[0], sb[2]), (bb[0], bb[2])))), 1)
    res["visible_depuis_spawnA_pct"] = round(100 * visibilite(m, sa, y_oeil, sol_ext, x0, x1, z0, z1), 1)
    res["visible_depuis_siteA_pct"] = round(100 * visibilite(m, ba, y_oeil, sol_ext, x0, x1, z0, z1), 1)
    res["visible_depuis_siteB_pct"] = round(100 * visibilite(m, bb, y_oeil, sol_ext, x0, x1, z0, z1), 1)
    # cover near sites: sight-blocking columns within 10 blocks
    for cle, p in (("siteA", ba), ("siteB", bb)):
        xx, zz = np.meshgrid(np.arange(sx), np.arange(sz), indexing="ij")
        disque = (xx + 0.5 - p[0]) ** 2 + (zz + 0.5 - p[2]) ** 2 <= 100
        res[f"couvert_autour_{cle}_pct"] = round(100.0 * np.mean(couvert[disque]), 1)
    # material variety on surfaces (blocks with an exposed face above floor)
    pres = m["present"]
    pl = np.pad(m["occ"], 1)
    expose = np.zeros_like(pres)
    for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
        expose |= ~pl[1 + dx:1 + dx + sx, 1 + dy:1 + dy + sy, 1 + dz:1 + dz + sz]
    surf = pres & expose
    surf[:, :sol - 1, :] = False
    noms = collections.Counter(m["noms"][j] for j in m["idx"][surf].tolist())
    tot_s = sum(noms.values())
    res["types_blocs"] = len({m["noms"][j] for j in np.unique(m["idx"][pres])})
    res["top_materiaux"] = [(k, round(100.0 * v / tot_s, 1)) for k, v in noms.most_common(5)]
    # blocks above the floor level (amount of "stuff")
    au_dessus = pres[:, sol:, :].sum()
    res["blocs_au_dessus_sol"] = int(au_dessus)
    res["blocs_par_m2"] = round(float(au_dessus) / ((x1 - x0 + 1) * (z1 - z0 + 1)), 2)
    return res


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--cartes", default=",".join(CARTES))
    ap.add_argument("--json")
    a = ap.parse_args()
    tous = []
    for ident in a.cartes.split(","):
        r = analyser(ident)
        tous.append(r)
        print(f"\n== {ident} ({r['nom']}) taille {r['taille']} y_sol {r['y_sol']}")
        for k, v in r.items():
            if k in ("id", "nom", "taille", "y_sol"):
                continue
            print(f"   {k:28s} {v}")
    if a.json:
        with open(a.json, "w", encoding="utf-8") as f:
            json.dump(tous, f, ensure_ascii=False, indent=1, default=str)


if __name__ == "__main__":
    main()
