"""Building blocks for the ClashWar maps (generer_*.py): voxel grid with central (180 degree) symmetry, block-state helpers,
connections (fences, panes, walls, decor railings and fences) and the .nbt writer.

Coordinates of the structure: x to the east, y up, z to the south, bounds included. A block is a string
"minecraft:stone" or "minecraft:oak_stairs[facing=north,half=bottom,shape=straight,waterlogged=false]". Air is never
written (the arena loader skips air; arenas sit in an empty void dimension).

Central symmetry: every write through Sym.set() also writes the mirrored cell (sx-1-x, y, sz-1-z) with the block state
turned by 180 degrees and, for the side materials listed in the B palette, the B-side material. Gameplay geometry is
therefore identical for both teams (sides are swapped every two rounds), while each half keeps its own look.
"""
import gzip
import io
import random
import struct

DATA_VERSION = 3465  # Minecraft 1.20.1

OPPOSE = {"north": "south", "south": "north", "east": "west", "west": "east", "up": "up", "down": "down"}
CW = {"north": "east", "east": "south", "south": "west", "west": "north"}
DIRS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}


# ------------------------------------------------------------------------------------------------ block states
def nom(b):
    return b.split("[", 1)[0]


def props(b):
    if "[" not in b:
        return {}
    corps = b.split("[", 1)[1].rstrip("]")
    return dict(kv.split("=", 1) for kv in corps.split(",") if kv)


def etat(n, **p):
    if ":" not in n:
        n = "minecraft:" + n
    if not p:
        return n
    return n + "[" + ",".join(f"{k}={v}" for k, v in sorted(p.items())) + "]"


def avec(b, **p):
    q = props(b)
    q.update({k: str(v).lower() if isinstance(v, bool) else str(v) for k, v in p.items()})
    return etat(nom(b), **q)


def marche(mat, facing, half="bottom", shape="straight"):
    """Stairs: `mat` without suffix ('stone_brick' -> stone_brick_stairs) or a full stairs id."""
    n = mat if (mat.endswith("_stairs") or mat.endswith("_escalier")) else mat + "_stairs"
    return etat(n, facing=facing, half=half, shape=shape, waterlogged="false")


def dalle(mat, t="bottom"):
    n = mat if (mat.endswith("_slab") or mat.endswith("_dalle")) else mat + "_slab"
    return etat(n, type=t, waterlogged="false")


def trappe(mat, facing, half="bottom", ouverte=False):
    n = mat if mat.endswith("_trapdoor") else mat + "_trapdoor"
    return etat(n, facing=facing, half=half, open=str(ouverte).lower(), powered="false", waterlogged="false")


def tronc(mat, axe="y"):
    return etat(mat, axis=axe)


def feuilles(mat):
    n = mat if mat.endswith("_leaves") else mat + "_leaves"
    return etat(n, distance="1", persistent="true", waterlogged="false")


def oriente(n, facing):
    return etat(n, facing=facing)


def tourner180(b):
    """Block state turned by 180 degrees around the vertical axis."""
    if b is None or "[" not in b:
        return b
    p = props(b)
    q = {}
    for k, v in p.items():
        if k == "facing" and v in OPPOSE:
            q[k] = OPPOSE[v]
        elif k == "rotation":
            q[k] = str((int(v) + 8) % 16)
        elif k in ("north", "south", "east", "west"):
            q[OPPOSE[k]] = v
        elif k in ("nord_est", "sud_ouest"):
            q["sud_ouest" if k == "nord_est" else "nord_est"] = v
        elif k in ("sud_est", "nord_ouest"):
            q["nord_ouest" if k == "sud_est" else "sud_est"] = v
        else:
            q[k] = v
    return etat(nom(b), **q)


# blocks that connect to their neighbours (properties computed at write time)
RACCORDS = ("_pane", "iron_bars", "_wall", "_fence", "balustrade_fer", "balustrade_bois")
NON_PLEINS = ("air", "_pane", "iron_bars", "_wall", "_fence", "_slab", "_stairs", "lantern", "torch", "carpet", "snow",
              "rail", "_trapdoor", "chain", "_button", "flower_pot", "ladder", "_sign", "light", "grass", "fern", "bush",
              "dead_bush", "_door", "cobweb", "scaffolding", "_head", "candle", "heraliz_decors:", "heralisz_utils:", "cfm:",
              "balustrade", "garde_corps", "grillage", "lightning_rod", "_leaves")
PLEINS_MODDES = ("heraliz_decors:enduit", "heraliz_decors:beton_banche[", "heraliz_decors:beton_banche", "heraliz_decors:tole_toit_rouille",
                 "heraliz_decors:tole_toit_bleue", "heraliz_decors:tole_toit_verte", "heraliz_decors:tole_larmee",
                 "heraliz_decors:tole_blanche", "heraliz_decors:carreaux", "heraliz_decors:marquage", "heraliz_decors:beton_piste")


def plein(b):
    if b is None:
        return False
    n = nom(b)
    for p in PLEINS_MODDES:
        if n.startswith(p) and not any(s in n for s in ("_dalle", "_escalier", "_mur")):
            return True
    return not any(m in n for m in NON_PLEINS)


# ------------------------------------------------------------------------------------------------ grid
class Grille:
    def __init__(self, sx, sy, sz, graine=7):
        self.sx, self.sy, self.sz = sx, sy, sz
        self.b = {}
        self.rnd = random.Random(graine)

    def dedans(self, x, y, z):
        return 0 <= x < self.sx and 0 <= y < self.sy and 0 <= z < self.sz

    def get(self, x, y, z):
        return self.b.get((x, y, z))

    def set(self, x, y, z, b):
        if not self.dedans(x, y, z):
            return
        if b is None or nom(b) == "minecraft:air":
            self.b.pop((x, y, z), None)
        else:
            self.b[(x, y, z)] = b

    # ---------------------------------------------------------------- connections & writing
    def raccorder(self):
        for (x, y, z), b in list(self.b.items()):
            n = nom(b)
            if n == "heraliz_decors:grillage" or n == "heraliz_decors:grillage_haut":
                self._grillage(x, y, z, b)
                continue
            if n == "heraliz_decors:gaine_calorifugee":
                self._gaine(x, y, z, b)
                continue
            if not any(m in n for m in RACCORDS) or n.endswith("_fence_gate") or n.endswith("balustrade_bois_basse"):
                continue
            mur = n.endswith("_wall")
            p = {}
            for cote, (dx, dz) in DIRS.items():
                v = self.get(x + dx, y, z + dz)
                lie = v is not None and (plein(v) or any(m in nom(v) for m in RACCORDS))
                if v is not None and ("_fence" in n or "balustrade" in n) and "_pane" in nom(v):
                    lie = False
                if v is not None and nom(v).endswith("_fence_gate"):
                    lie = True
                p[cote] = ("low" if lie else "none") if mur else ("true" if lie else "false")
            if mur:
                ns = p["north"] != "none" and p["south"] != "none" and p["east"] == "none" and p["west"] == "none"
                eo = p["east"] != "none" and p["west"] != "none" and p["north"] == "none" and p["south"] == "none"
                p["up"] = "false" if (ns or eo) else "true"
                haut = self.get(x, y + 1, z)
                if haut is not None and (plein(haut) or nom(haut).endswith("_wall")):
                    p = {k: ("tall" if v == "low" else v) for k, v in p.items()}
                    p["up"] = "true" if not (ns or eo) else p["up"]
            p["waterlogged"] = "false"
            self.b[(x, y, z)] = etat(n, **p)

    def _grillage(self, x, y, z, b):
        lies = {}
        for cote, (dx, dz) in DIRS.items():
            v = self.get(x + dx, y, z + dz)
            lies[cote] = v is not None and nom(v).startswith("heraliz_decors:grillage")
        n = sum(lies.values())
        droit = (lies["north"] and lies["south"] and not lies["east"] and not lies["west"]) or (
            lies["east"] and lies["west"] and not lies["north"] and not lies["south"])
        poteau = props(b).get("poteau")
        if poteau is None:
            poteau = "false" if (droit and (x + z) % 3 != 0) else "true"
        p = {"north": lies["north"], "south": lies["south"], "east": lies["east"], "west": lies["west"],
             "nord_est": False, "sud_est": False, "sud_ouest": False, "nord_ouest": False}
        q = {k: str(v).lower() for k, v in p.items()}
        q["poteau"] = poteau
        self.b[(x, y, z)] = etat(nom(b), **q)

    def _gaine(self, x, y, z, b):
        q = {}
        for cote, (dx, dy, dz) in (("north", (0, 0, -1)), ("south", (0, 0, 1)), ("east", (1, 0, 0)), ("west", (-1, 0, 0)),
                                   ("up", (0, 1, 0)), ("down", (0, -1, 0))):
            v = self.get(x + dx, y + dy, z + dz)
            q[cote] = "true" if (v is not None and (nom(v) == "heraliz_decors:gaine_calorifugee" or plein(v))) else "false"
        self.b[(x, y, z)] = etat(nom(b), **q)

    def ecrire(self, chemin):
        self.raccorder()
        palette, index, blocs = [], {}, []
        for (x, y, z), b in sorted(self.b.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0])):
            if b not in index:
                index[b] = len(palette)
                palette.append(b)
            blocs.append((index[b], x, y, z))
        racine = {
            "DataVersion": ("int", DATA_VERSION),
            "size": ("list_int", [self.sx, self.sy, self.sz]),
            "palette": ("list_comp", [_entree_palette(p) for p in palette]),
            "blocks": ("list_comp", [{"state": ("int", s), "pos": ("list_int", [x, y, z])} for s, x, y, z in blocs]),
            "entities": ("list_comp", []),
        }
        tampon = io.BytesIO()
        tampon.write(b"\x0a")
        _chaine(tampon, "")
        _compose(tampon, racine)
        with open(chemin, "wb") as brut, gzip.GzipFile(fileobj=brut, mode="wb", mtime=0) as f:
            f.write(tampon.getvalue())
        return len(blocs), len(palette)


def _entree_palette(b):
    e = {"Name": ("str", nom(b))}
    p = props(b)
    if p:
        e["Properties"] = ("comp", {k: ("str", v) for k, v in p.items()})
    return e


def _chaine(f, s):
    d = s.encode("utf-8")
    f.write(struct.pack(">H", len(d)))
    f.write(d)


def _compose(f, d):
    for k, (t, v) in d.items():
        if t == "int":
            f.write(b"\x03"); _chaine(f, k); f.write(struct.pack(">i", v))
        elif t == "str":
            f.write(b"\x08"); _chaine(f, k); _chaine(f, v)
        elif t == "comp":
            f.write(b"\x0a"); _chaine(f, k); _compose(f, v)
        elif t == "list_int":
            f.write(b"\x09"); _chaine(f, k); f.write(b"\x03"); f.write(struct.pack(">i", len(v)))
            for i in v:
                f.write(struct.pack(">i", i))
        elif t == "list_comp":
            f.write(b"\x09"); _chaine(f, k); f.write(b"\x0a" if v else b"\x00"); f.write(struct.pack(">i", len(v)))
            for c in v:
                _compose(f, c)
        else:
            raise ValueError(t)
    f.write(b"\x00")


# ------------------------------------------------------------------------------------------------ symmetric writer
class Sym:
    """Writes every block twice: as given, and at the 180-degree mirror with the B palette (names) and B variants."""

    def __init__(self, g, palette_b=None, variantes_b=None):
        self.g = g
        self.pb = palette_b or {}
        self.vb = variantes_b or {}
        self.rnd = g.rnd

    def miroir(self, x, z):
        return self.g.sx - 1 - x, self.g.sz - 1 - z

    def version_b(self, b):
        if b is None:
            return None
        n = nom(b)
        if n in self.vb:
            n2 = self.vb[n]
        else:
            n2 = self.pb.get(n, n)
        if n2 != n:
            b = n2 + b[len(n):]
        return tourner180(b)

    def set(self, x, y, z, b, b_b="meme"):
        self.g.set(x, y, z, b)
        mx, mz = self.miroir(x, z)
        self.g.set(mx, y, mz, self.version_b(b) if b_b == "meme" else (tourner180(b_b) if b_b else None))

    def get(self, x, y, z):
        return self.g.get(x, y, z)

    def fill(self, x0, y0, z0, x1, y1, z1, b, b_b="meme"):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for y in range(min(y0, y1), max(y0, y1) + 1):
                for z in range(min(z0, z1), max(z0, z1) + 1):
                    self.set(x, y, z, b, b_b)

    def clear(self, x0, y0, z0, x1, y1, z1):
        self.fill(x0, y0, z0, x1, y1, z1, None, None)

    def walls(self, x0, y0, z0, x1, y1, z1, b):
        for y in range(y0, y1 + 1):
            for x in range(x0, x1 + 1):
                self.set(x, y, z0, b)
                self.set(x, y, z1, b)
            for z in range(z0, z1 + 1):
                self.set(x0, y, z, b)
                self.set(x1, y, z, b)

    def si_vide(self, x, y, z, b):
        if self.g.get(x, y, z) is None:
            self.set(x, y, z, b)
