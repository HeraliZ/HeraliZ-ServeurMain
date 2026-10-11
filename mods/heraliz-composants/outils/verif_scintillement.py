"""Detecte les faces coplanaires qui se recouvrent (z-fighting) dans les modeles.
Usage : py zfight.py <dossier tools> [ids...]"""
import sys
import numpy as np
sys.path.insert(0, sys.argv[1])
from engine import Model  # noqa: E402
from composants import ITEMS  # noqa: E402

ids = set(sys.argv[2:])
EPS = 0.012


def faces(b):
    """Faces (axe, signe, plan, rect 2D) dans le repere local de la boite."""
    f, t = b['f'], b['t']
    out = []
    for a in range(3):
        o = [k for k in range(3) if k != a]
        rect = (f[o[0]], t[o[0]], f[o[1]], t[o[1]])
        out.append((a, -1, f[a], rect))
        out.append((a, 1, t[a], rect))
    return out


def inside(p, b):
    """Point p strictement a l'interieur de la boite b (repere monde)."""
    q = np.array(p, float)
    if b['rot'] is not None:
        from engine import rot_matrix
        axis, ang, org = b['rot']
        q = rot_matrix(axis, ang).T @ (q - org) + org
    return np.all(q > b['f'] + 1e-4) and np.all(q < b['t'] - 1e-4)


total = 0
for it in ITEMS:
    if ids and it['id'] not in ids:
        continue
    m = Model()
    it['build'](m)
    bs = m.boxes
    hits = []
    for i in range(len(bs)):
        for j in range(i + 1, len(bs)):
            bi, bj = bs[i], bs[j]
            ri, rj = bi['rot'], bj['rot']
            same_rot = (ri is None and rj is None) or (
                ri is not None and rj is not None and ri[0] == rj[0] and ri[1] == rj[1]
                and np.allclose(ri[2], rj[2]))
            if not same_rot:
                continue
            same_uv = bi['m'] == bj['m']  # meme matiere + meme rotation = memes UV, pas de scintillement
            if same_uv:
                continue
            for (a, s, p, r) in faces(bi):
                for (a2, s2, p2, r2) in faces(bj):
                    if a != a2 or s != s2 or abs(p - p2) > EPS:
                        continue
                    x1, x2 = max(r[0], r2[0]), min(r[1], r2[1])
                    y1, y2 = max(r[2], r2[2]), min(r[3], r2[3])
                    if x2 - x1 > 0.05 and y2 - y1 > 0.05:
                        # zone visible ? on teste le centre, juste devant la face
                        o = [k for k in range(3) if k != a]
                        c = [0, 0, 0]
                        c[a] = p + s * 0.02
                        c[o[0]] = (x1 + x2) / 2
                        c[o[1]] = (y1 + y2) / 2
                        if ri is not None:
                            from engine import rot_matrix
                            c = rot_matrix(ri[0], ri[1]) @ (np.array(c) - ri[2]) + ri[2]
                        if any(inside(c, bk) for k, bk in enumerate(bs) if k not in (i, j)):
                            continue
                        hits.append((i, j, bi['m'], bj['m'], 'xyz'[a] + ('+' if s > 0 else '-'), round(p, 3)))
    if hits:
        total += len(hits)
        print(f"{it['id']}: {len(hits)}")
        for h in hits[:12]:
            print('   ', h)
print('total', total)
