"""Minimal reader for vanilla structure .nbt files (gzip, big-endian NBT)."""
import gzip
import struct

import numpy as np


class _R:
    def __init__(self, data):
        self.d = data
        self.i = 0

    def take(self, n):
        v = self.d[self.i:self.i + n]
        self.i += n
        return v

    def u8(self):
        v = self.d[self.i]
        self.i += 1
        return v

    def s(self, fmt):
        n = struct.calcsize(fmt)
        v = struct.unpack(fmt, self.d[self.i:self.i + n])[0]
        self.i += n
        return v

    def string(self):
        n = self.s(">H")
        return self.take(n).decode("utf-8", errors="replace")


def _payload(r, t):
    if t == 1:
        return r.s(">b")
    if t == 2:
        return r.s(">h")
    if t == 3:
        return r.s(">i")
    if t == 4:
        return r.s(">q")
    if t == 5:
        return r.s(">f")
    if t == 6:
        return r.s(">d")
    if t == 7:
        n = r.s(">i")
        return r.take(n)
    if t == 8:
        return r.string()
    if t == 9:
        et = r.u8()
        n = r.s(">i")
        return [_payload(r, et) for _ in range(n)]
    if t == 10:
        out = {}
        while True:
            tt = r.u8()
            if tt == 0:
                return out
            k = r.string()
            out[k] = _payload(r, tt)
    if t == 11:
        n = r.s(">i")
        return list(struct.unpack(">%di" % n, r.take(4 * n)))
    if t == 12:
        n = r.s(">i")
        return list(struct.unpack(">%dq" % n, r.take(8 * n)))
    raise ValueError("unknown tag %d" % t)


def lire(chemin):
    with open(chemin, "rb") as f:
        raw = f.read()
    try:
        data = gzip.decompress(raw)
    except OSError:
        data = raw
    r = _R(data)
    t = r.u8()
    assert t == 10, t
    r.string()
    return _payload(r, 10)


def etat(entree):
    """Palette entry -> 'minecraft:name[k=v,...]'."""
    n = entree.get("Name", "minecraft:air")
    p = entree.get("Properties")
    if p:
        return n + "[" + ",".join(f"{k}={v}" for k, v in sorted(p.items())) + "]"
    return n


def structure(chemin):
    """Return (size(sx,sy,sz), palette list of state strings, blocks list of (state_idx, x, y, z), raw)."""
    racine = lire(chemin)
    size = tuple(racine["size"])
    if "palette" in racine:
        pal = racine["palette"]
    else:
        pal = racine["palettes"][0]
    palette = [etat(e) for e in pal]
    blocs = [(b["state"], *b["pos"]) for b in racine["blocks"]]
    return size, palette, blocs, racine


def grille(chemin):
    """Return (size, palette, ids array [x,y,z] with -1 for empty)."""
    size, palette, blocs, racine = structure(chemin)
    ids = np.full(size, -1, dtype=np.int32)
    for s, x, y, z in blocs:
        if 0 <= x < size[0] and 0 <= y < size[1] and 0 <= z < size[2]:
            ids[x, y, z] = s
    return size, palette, ids, racine
