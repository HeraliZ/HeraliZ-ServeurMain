"""Reachability of a ClashWar map: flood fill over standable cells from the spawns, with simple movement rules
(walk to a 4-neighbour, step up 1 when there is head room, drop down at most 4). Fences, walls, bars, panes and
railings are not standable on top (1.5 high collision). Reports the reachable cells by height, the highest ones, the
reachable cells near the outer ring (out-of-bounds risk) and whether both spawns reach each other.

Usage: python acces_xl.py [carte]
"""
import collections
import sys

import numpy as np

import analyse as an
import blocs as bl
from rendu import niveau_sol

NON_SUPPORT = ("_fence", "_wall", "iron_bars", "_pane", "garde_corps", "grillage", "balustrade", "chain", "lightning_rod",
               "barbeles", "herisson", "lampadaire", "_leaves")


def accessibles(ident, chute=4, departs=("spawn_a", "spawn_b")):
    m = an.masques(ident)
    sx, sy, sz = m["size"]
    bp = m["bloque_pas"]
    noms = m["noms"]
    idx = m["idx"]
    ok_sup = np.array([not any(k in n for k in NON_SUPPORT) for n in noms])
    sup = m["support"] & ok_sup[idx]
    # an edge railing (garde_corps) only fills one side of its cell: one stands in that cell
    mince = np.array([("garde_corps" in n) for n in noms])
    libre = ~bp | (m["present"] & mince[idx])
    st = np.zeros_like(bp)
    st[:, 1:-1, :] = libre[:, 1:-1, :] & libre[:, 2:, :] & sup[:, :-2, :]
    vu = np.zeros_like(bp)
    meta = m["meta"]
    pile = collections.deque()
    for cle in departs:
        for q in meta["points"].get(cle, []):
            x, y, z = int(q[0]), int(round(q[1])), int(q[2])
            if st[x, y, z]:
                vu[x, y, z] = True
                pile.append((x, y, z))
    while pile:
        x, y, z = pile.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            if not (0 <= nx < sx and 0 <= nz < sz):
                continue
            # same level or step up 1 (head room at the origin for the jump)
            for ny in (y, y + 1):
                if ny + 1 >= sy:
                    continue
                if ny == y + 1 and not (y + 2 < sy and libre[x, y + 2, z]):
                    continue
                if st[nx, ny, nz] and not vu[nx, ny, nz]:
                    vu[nx, ny, nz] = True
                    pile.append((nx, ny, nz))
            # drop down (the column above the target must be free from y+1 down)
            if libre[nx, y, nz] and libre[nx, y + 1, nz]:
                for ny in range(y - 1, max(0, y - chute) - 1, -1):
                    if not libre[nx, ny, nz]:
                        break
                    if st[nx, ny, nz]:
                        if not vu[nx, ny, nz]:
                            vu[nx, ny, nz] = True
                            pile.append((nx, ny, nz))
                        break
    return m, st, vu


def main():
    ident = sys.argv[1] if len(sys.argv) > 1 else "clashwar_2v2_kombinat"
    m, st, vu = accessibles(ident)
    meta = m["meta"]
    sx, sy, sz = m["size"]
    sol = niveau_sol(meta) + 1
    ys = np.nonzero(vu)[1]
    print(f"{ident}: standable {int(st.sum())}, reachable {int(vu.sum())}")
    c = collections.Counter((ys - sol).tolist())
    print("   reachable by height above ground:", dict(sorted(c.items())))
    hmax = int(ys.max())
    pts = np.argwhere(vu & (np.arange(sy)[None, :, None] >= hmax - 1))
    print(f"   highest reachable feet y = {hmax} (+{hmax - sol}), e.g.", [tuple(p) for p in pts[:8]])
    b = meta.get("bornes")
    x0, z0, x1, z1 = b[0], b[2], b[3], b[5]
    dehors = np.argwhere(vu & ~((np.arange(sx)[:, None, None] >= x0) & (np.arange(sx)[:, None, None] <= x1)
                                & (np.arange(sz)[None, None, :] >= z0) & (np.arange(sz)[None, None, :] <= z1)))
    print(f"   reachable cells outside the bounds: {len(dehors)}", [tuple(p) for p in dehors[:10]])
    trop_haut = np.argwhere(vu & (np.arange(sy)[None, :, None] + 2.3 > b[4] + 1))
    print(f"   reachable cells whose jump leaves the bounds (y1 = {b[4]}): {len(trop_haut)}")
    # both spawns in the same component?
    pa = [tuple(int(v) for v in (q[0], round(q[1]), q[2])) for q in meta["points"]["spawn_a"]]
    pb = [tuple(int(v) for v in (q[0], round(q[1]), q[2])) for q in meta["points"]["spawn_b"]]
    print("   spawns standable:", [bool(st[p]) for p in pa + pb])
    return m, st, vu


if __name__ == "__main__":
    main()
