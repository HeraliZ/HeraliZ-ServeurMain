"""Diagnostics of a ClashWar map: long eye-level sightlines (runs), exposed outdoor ground, floating pieces.
Usage: python diag_xl.py <map id> [minimum run length, 48]"""
import sys
import numpy as np
import scipy.ndimage as ndi
import analyse as an
from rendu import niveau_sol

ident = sys.argv[1] if len(sys.argv) > 1 else "clashwar_2v2_kombinat"
seuil = int(sys.argv[2]) if len(sys.argv) > 2 else 48
m = an.masques(ident)
meta = m["meta"]
sx, sy, sz = m["size"]
sol = niveau_sol(meta) + 1
x0, x1, z0, z1 = an.bornes_xz(meta, m["size"])
y = sol + 1
libre = ~m["bloque_vue"][:, y, :]


def runs(arr, axe):
    out = []
    for i, ligne in enumerate(arr):
        r = 0
        for j, v in enumerate(ligne):
            if v:
                r += 1
            else:
                if r >= seuil:
                    out.append((i, j - r, j - 1, r))
                r = 0
        if r >= seuil:
            out.append((i, len(ligne) - r, len(ligne) - 1, r))
    return out

sub = libre[x0:x1 + 1, z0:z1 + 1]
print("== N-S runs (x, z_from, z_to, len) >=", seuil)
for (i, a, b, r) in runs(sub, "ns"):
    print("   x=%d z %d..%d (%d)" % (i + x0, a + z0, b + z0, r))
print("== E-W runs (z, x_from, x_to, len) >=", seuil)
for (i, a, b, r) in runs(sub.T, "eo"):
    print("   z=%d x %d..%d (%d)" % (i + z0, a + x0, b + x0, r))
# exposed ground
st = an.debout(m)
occ = m["occ"]
ciel = ~np.flip(np.logical_or.accumulate(np.flip(occ, axis=1), axis=1), axis=1)
sol_ext = st[:, sol, :] & ciel[:, sol + 2, :]
sol_ext[:x0] = False; sol_ext[x1 + 1:] = False; sol_ext[:, :z0] = False; sol_ext[:, z1 + 1:] = False
couvert = m["bloque_vue"][:, sol, :] | m["bloque_vue"][:, sol + 1, :]
dist = ndi.distance_transform_edt(~couvert)
exp = sol_ext & (dist > 6)
lab, n = ndi.label(exp)
print("== exposed clusters (>6 from cover):", n, "cells", int(exp.sum()))
for i, sl in enumerate(ndi.find_objects(lab)):
    c = (lab[sl] == i + 1).sum()
    print("   x %d..%d z %d..%d (%d cells)" % (sl[0].start, sl[0].stop - 1, sl[1].start, sl[1].stop - 1, c))
# floating
pres = m["present"]
lab, nb = ndi.label(pres, structure=ndi.generate_binary_structure(3, 1))
t = np.bincount(lab.ravel()); t[0] = 0; p = int(np.argmax(t))
objs = ndi.find_objects(lab)
fl = [(i + 1) for i in range(nb) if i + 1 != p]
print("== floating pieces (6-conn):", len(fl))
for i in fl[:40]:
    sl = objs[i - 1]
    idxs = m["idx"][sl][lab[sl] == i]
    noms = sorted({m["noms"][j] for j in idxs.tolist()})
    print("   at", (sl[0].start, sl[1].start, sl[2].start), t[i], noms)
# perimeter risk: standable cells within 4 of the outer ring whose height >= ring top - 1
