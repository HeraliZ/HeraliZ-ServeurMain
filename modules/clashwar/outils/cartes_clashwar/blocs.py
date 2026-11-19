"""Block state -> list of boxes with per-face colours, for the preview renderer.

Boxes are in block-local units 0..16 (Minecraft model units), may extend outside the cell (modded models).
Each box: dict(b=(x0,y0,z0,x1,y1,z1), f={dir: (r,g,b,alpha)}) with dir in 'u','d','n','s','e','w'.
A block also reports: plein (full opaque cube, used for face culling and light occlusion), occ (casts shadows),
transl (translucent material id for culling between identical translucent blocks).

Vanilla textures are not available offline: vanilla colours are hand-tuned averages of the 1.20 textures.
Modded blocks (cfm furniture, heralisz_utils props) use their real model elements and average texture colours,
read (read-only) from the repository assets and the cfm jar.
"""
import glob
import io
import json
import os
import zipfile

from PIL import Image

from chemins_cartes import DEPOT

REPO = str(DEPOT)

# client resource packs imposed on every player (PackImpose.java, AmbianceClient.java): they override vanilla textures
PACKS = [
    os.path.join(REPO, "modules/monde/src/client/resources/resourcepacks/heraliz_terrain"),
    os.path.join(REPO, "modules/interface/src/main/resources/resourcepacks/herbe"),
    os.path.join(REPO, "modules/interface/src/main/resources/resourcepacks/textures"),
]
# biome of the arena dimension: minecraft:the_void (temperature 0.5, downfall 0.5, like river)
TEINTE_HERBE = (142, 185, 113)
TEINTE_FEUILLAGE = (113, 167, 77)
TEINTE_SAPIN = (97, 153, 97)
TEINTE_BOULEAU = (128, 167, 85)

INVISIBLES = {
    "minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:barrier", "minecraft:structure_void",
    "minecraft:light", "minecraft:moving_piston", "heralisz_utils:collision_invisible",
}

# ------------------------------------------------------------------------------------------------ colours
DYE_CONCRETE = {
    "white": (207, 213, 214), "orange": (224, 97, 0), "magenta": (169, 48, 159), "light_blue": (35, 137, 198),
    "yellow": (240, 175, 21), "lime": (94, 168, 24), "pink": (213, 101, 142), "gray": (54, 57, 61),
    "light_gray": (125, 125, 115), "cyan": (21, 119, 136), "purple": (100, 31, 156), "blue": (44, 46, 143),
    "brown": (96, 59, 31), "green": (73, 91, 36), "red": (142, 32, 32), "black": (14, 16, 21),
}
DYE_TERRACOTTA = {
    "white": (209, 178, 161), "orange": (161, 83, 37), "magenta": (149, 88, 108), "light_blue": (113, 108, 137),
    "yellow": (186, 133, 35), "lime": (103, 117, 52), "pink": (161, 78, 78), "gray": (57, 42, 35),
    "light_gray": (135, 106, 97), "cyan": (86, 91, 91), "purple": (118, 70, 86), "blue": (74, 59, 91),
    "brown": (77, 51, 35), "green": (76, 83, 42), "red": (143, 61, 46), "black": (37, 22, 16),
}
DYE_WOOL = {
    "white": (233, 236, 236), "orange": (240, 118, 19), "magenta": (189, 68, 179), "light_blue": (58, 175, 217),
    "yellow": (248, 197, 39), "lime": (112, 185, 25), "pink": (237, 141, 172), "gray": (62, 68, 71),
    "light_gray": (142, 142, 134), "cyan": (21, 137, 145), "purple": (121, 42, 172), "blue": (53, 57, 157),
    "brown": (114, 71, 40), "green": (84, 109, 27), "red": (160, 39, 34), "black": (20, 21, 25),
}
DYE_GLASS = {
    "white": (240, 240, 240), "orange": (216, 127, 51), "magenta": (178, 76, 216), "light_blue": (102, 153, 216),
    "yellow": (229, 229, 51), "lime": (127, 204, 25), "pink": (242, 127, 165), "gray": (76, 76, 76),
    "light_gray": (153, 153, 153), "cyan": (76, 127, 153), "purple": (127, 63, 178), "blue": (51, 76, 178),
    "brown": (102, 76, 51), "green": (102, 127, 51), "red": (153, 51, 51), "black": (25, 25, 25),
}

# name -> rgb, or (top_rgb, side_rgb)
BASE = {
    "stone": (125, 125, 125), "cobblestone": (120, 119, 120), "mossy_cobblestone": (110, 118, 95),
    "stone_bricks": (122, 121, 122), "mossy_stone_bricks": (113, 121, 103), "cracked_stone_bricks": (116, 115, 116),
    "chiseled_stone_bricks": (119, 118, 119), "smooth_stone": ((160, 160, 160), (150, 150, 150)),
    "andesite": (136, 136, 136), "polished_andesite": (132, 134, 133), "diorite": (188, 188, 189),
    "polished_diorite": (192, 193, 194), "calcite": (223, 224, 220), "tuff": (108, 109, 102),
    "deepslate": (80, 80, 82), "cobbled_deepslate": (77, 77, 80), "polished_deepslate": (72, 72, 73),
    "deepslate_tiles": (54, 54, 55), "blackstone": (42, 36, 41), "polished_blackstone": (53, 48, 56),
    "bricks": (150, 97, 83), "mud_bricks": (137, 103, 79), "packed_mud": (142, 106, 79), "mud": (60, 57, 60),
    "terracotta": (152, 94, 67), "dirt": (134, 96, 67), "coarse_dirt": (119, 85, 59), "rooted_dirt": (144, 103, 76),
    "podzol": ((91, 63, 24), (123, 88, 57)), "grass_block": ((104, 150, 62), (121, 104, 66)),
    "dirt_path": ((148, 122, 65), (134, 96, 67)), "gravel": (131, 127, 126), "sand": (219, 207, 163),
    "snow_block": (238, 244, 246), "snow": (238, 244, 246), "packed_ice": (141, 180, 250), "ice": (145, 183, 253),
    "oak_planks": (162, 130, 78), "spruce_planks": (114, 84, 48), "dark_oak_planks": (66, 43, 20),
    "birch_planks": (192, 175, 121), "jungle_planks": (160, 115, 80), "acacia_planks": (168, 90, 50),
    "oak_log": ((151, 121, 73), (109, 85, 50)), "spruce_log": ((106, 80, 48), (58, 37, 16)),
    "dark_oak_log": ((79, 59, 35), (60, 46, 26)), "stripped_spruce_log": ((106, 80, 48), (115, 89, 52)),
    "stripped_dark_oak_log": ((65, 44, 22), (96, 76, 49)), "stripped_oak_log": ((160, 130, 80), (177, 144, 86)),
    "spruce_leaves": (50, 84, 55), "dark_oak_leaves": (60, 94, 33), "oak_leaves": (64, 104, 36),
    "birch_leaves": (90, 120, 60), "hay_block": ((165, 139, 12), (166, 136, 38)), "moss_block": (89, 109, 45),
    "soul_sand": (81, 62, 51), "netherite_block": (66, 61, 63), "coal_block": (16, 15, 15),
    "iron_block": (220, 220, 220), "lodestone": ((147, 149, 152), (120, 122, 125)),
    "prismarine_bricks": (99, 171, 158), "quartz_pillar": ((235, 230, 224), (236, 231, 224)),
    "barrel": ((115, 86, 50), (134, 100, 58)), "bell": (226, 186, 70), "chain": (52, 58, 72),
    "iron_bars": (128, 131, 128), "iron_trapdoor": (203, 203, 203), "spruce_trapdoor": (104, 79, 48),
    "oak_trapdoor": (124, 99, 56), "ladder": (125, 97, 56), "lantern": (232, 176, 86),
    "soul_lantern": (90, 190, 200), "lightning_rod": (198, 108, 80), "dead_bush": (107, 79, 40),
    "rail": (125, 111, 97), "glass": (215, 232, 238), "water": (46, 88, 190), "torch": (240, 200, 90),
    "grass": (95, 140, 55), "fern": (85, 125, 50), "tall_grass": (95, 140, 55), "cobweb": (220, 220, 220),
    "crafting_table": (120, 80, 50), "chest": (160, 110, 40), "furnace": (110, 110, 110),
    "bookshelf": (117, 94, 59), "scaffolding": (190, 160, 90), "flower_pot": (124, 68, 52),
    "campfire": (90, 70, 50), "anvil": (68, 68, 68), "cauldron": (60, 60, 64), "hopper": (70, 70, 74),
}


def _dyed(n):
    for suffix, table in (("_concrete", DYE_CONCRETE), ("_terracotta", DYE_TERRACOTTA), ("_wool", DYE_WOOL),
                          ("_stained_glass", DYE_GLASS), ("_carpet", DYE_WOOL), ("_concrete_powder", DYE_CONCRETE)):
        if n.endswith(suffix):
            dye = n[: -len(suffix)]
            if dye in table:
                c = table[dye]
                if suffix == "_concrete_powder":
                    c = tuple(min(255, int(v * 1.08 + 12)) for v in c)
                return c
    return None


def couleur_materiau(n):
    """Short name (no namespace) -> (top, side) colours, or None if unknown."""
    c = BASE.get(n)
    if c is None:
        c = _dyed(n)
    if c is None:
        return None
    if isinstance(c[0], tuple):
        return c
    return (c, c)


DERIVES = ("_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pane", "_carpet", "_trapdoor", "_door", "_button",
           "_pressure_plate")


def teinte_texture(nom_tex):
    t = nom_tex.split("/")[-1]
    if t in ("grass_block_top", "grass", "short_grass", "tall_grass_top", "tall_grass_bottom", "fern") or "herbe" in nom_tex:
        return TEINTE_HERBE
    if t == "spruce_leaves":
        return TEINTE_SAPIN
    if t == "birch_leaves":
        return TEINTE_BOULEAU
    if t.endswith("_leaves") and t not in ("cherry_leaves", "azalea_leaves", "flowering_azalea_leaves"):
        return TEINTE_FEUILLAGE
    return None


_MOY = {}


def moyenne_texture(ref):
    """(rgb with tint, coverage) of a whole texture from the packs, or None."""
    if ref in _MOY:
        return _MOY[ref]
    im = assets().image(ref)
    r = None
    if im is not None:
        rgba = _moyenne(im, (0, 0, 16, 16))
        c = rgba[:3]
        t = teinte_texture(ref)
        if t:
            c = tuple(v * w / 255.0 for v, w in zip(c, t))
        r = (tuple(int(v) for v in c), rgba[3])
    _MOY[ref] = r
    return r


def texture_existe(nom_tex):
    return assets().image("minecraft:block/" + nom_tex) is not None


def couleur_pack(n):
    """(top, side) colours from the imposed pack textures, or None."""
    noms = noms_faces(n, plats_aussi=True)
    if noms is None:
        return None
    haut, _bas, cote = noms
    a, b = moyenne_texture("minecraft:block/" + haut), moyenne_texture("minecraft:block/" + cote)
    if a is None or b is None:
        return None
    return (a[0], b[0])


GARDER_TABLE = ("lantern", "soul_lantern", "water", "torch", "bell")


def materiau(n):
    """Colours for a block name, resolving stairs/slabs/walls/fences to their base material."""
    if n not in GARDER_TABLE:
        c = couleur_pack(n)
        if c is not None:
            return c
    c = couleur_materiau(n)
    if c is not None:
        return c
    for s in DERIVES:
        if n.endswith(s):
            b = n[: -len(s)]
            for cand in (b, b + "s", b + "_planks", b + "_block", b + "_wool", b.replace("brick", "bricks"),
                         b.replace("tile", "tiles")):
                c = couleur_materiau(cand)
                if c is not None:
                    return c
    if n.startswith("stripped_"):
        return materiau(n[len("stripped_"):])
    return None


# ------------------------------------------------------------------------------------------------ vanilla face textures
PLATS = ("iron_bars", "_pane", "chain", "ladder", "rail", "lantern", "torch", "lightning_rod", "bell", "dead_bush",
         "cobweb", "water", "glass", "_trapdoor", "_door", "_button", "flower_pot", "candle", "_head", "fern",
         "_sapling", "light")
SPECIAUX = {
    "grass_block": ("grass_block_top", "dirt", "grass_block_side"),
    "podzol": ("podzol_top", "dirt", "podzol_side"),
    "dirt_path": ("dirt_path_top", "dirt", "dirt_path_side"),
    "barrel": ("barrel_top", "barrel_bottom", "barrel_side"),
    "hay_block": ("hay_block_top", "hay_block_top", "hay_block_side"),
    "quartz_pillar": ("quartz_pillar_top", "quartz_pillar_top", "quartz_pillar"),
    "lodestone": ("lodestone_top", "lodestone_top", "lodestone_side"),
    "snow_block": ("snow", "snow", "snow"),
    "smooth_stone_slab": ("smooth_stone", "smooth_stone", "smooth_stone_slab_side"),
    "moss_carpet": ("moss_block", "moss_block", "moss_block"),
}


def noms_faces(n, plats_aussi=False):
    """(top, bottom, side) texture names of a vanilla block, or None when it is drawn as a flat colour."""
    if not plats_aussi and n not in ("grass_block", "snow") and (any(p in n for p in PLATS) or n in ("grass", "short_grass", "tall_grass")):
        return None
    if n in SPECIAUX:
        return SPECIAUX[n]
    if n.endswith("_log") or n.endswith("_wood"):
        b = n if n.endswith("_log") else n[:-5] + "_log"
        if texture_existe(b):
            return (b + "_top", b + "_top", b)
    cands = [n]
    for s in DERIVES:
        if n.endswith(s):
            b = n[: -len(s)]
            cands = [b, b + "s", b + "_planks", b + "_block", b + "_wool", b.replace("brick", "bricks"), b.replace("tile", "tiles")]
            break
    if n.endswith("_carpet"):
        cands = [n[:-7] + "_wool"] + cands
    for c in cands:
        if c in SPECIAUX:
            return SPECIAUX[c]
        if texture_existe(c + "_top") and texture_existe(c + "_side"):
            return (c + "_top", c + "_bottom" if texture_existe(c + "_bottom") else c + "_top", c + "_side")
        if texture_existe(c):
            return (c, c, c)
    return None


# texture coordinate conventions (u, v in 0..1 as functions of the block-local coordinate along one axis)
AXE_U = {"u": 0, "d": 0, "s": 0, "n": 0, "e": 2, "w": 2}
AXE_V = {"u": 2, "d": 2, "s": 1, "n": 1, "e": 1, "w": 1}
COEF_VANILLE = {  # (au, bu, av, bv): u = au + bu * coord_u, v = av + bv * coord_v (coords in blocks, 0..1)
    "u": (0.0, 1.0, 0.0, 1.0), "d": (0.0, 1.0, 1.0, -1.0), "s": (0.0, 1.0, 1.0, -1.0),
    "n": (1.0, -1.0, 1.0, -1.0), "e": (1.0, -1.0, 1.0, -1.0), "w": (0.0, 1.0, 1.0, -1.0),
}


def coef_modele(d, b, uv):
    """Affine texture coefficients for a model face from its rotated box b (0..16) and uv (0..16)."""
    u1, v1, u2, v2 = (x / 16.0 for x in uv)
    x0, y0, z0, x1, y1, z1 = (q / 16.0 for q in b)

    def lin(a0, a1, c0, c1):  # value a0 at coordinate c0, a1 at c1
        if abs(c1 - c0) < 1e-6:
            return ((a0 + a1) / 2.0, 0.0)
        bb = (a1 - a0) / (c1 - c0)
        return (a0 - bb * c0, bb)

    if d in ("u", "d"):
        au, bu = lin(u1, u2, x0, x1)
        av, bv = lin(v1, v2, z0, z1)
    elif d == "s":
        au, bu = lin(u1, u2, x0, x1)
        av, bv = lin(v2, v1, y0, y1)
    elif d == "n":
        au, bu = lin(u2, u1, x0, x1)
        av, bv = lin(v2, v1, y0, y1)
    elif d == "e":
        au, bu = lin(u2, u1, z0, z1)
        av, bv = lin(v2, v1, y0, y1)
    else:
        au, bu = lin(u1, u2, z0, z1)
        av, bv = lin(v2, v1, y0, y1)
    return (au, bu, av, bv)


# ------------------------------------------------------------------------------------------------ assets (modded)
class Assets:
    def __init__(self):
        self.dirs = sorted(glob.glob(os.path.join(REPO, "modules", "*", "src", "client", "resources")))
        self.zips = []
        for j in sorted(glob.glob(os.path.join(REPO, "serveur", "mods-tiers", "cfm-*.jar"))):
            try:
                self.zips.append(zipfile.ZipFile(j))
            except (OSError, zipfile.BadZipFile):
                pass
        self._img = {}
        self._json = {}

    def lire(self, rel):
        for d in self.dirs:
            p = os.path.join(d, rel)
            if os.path.isfile(p):
                with open(p, "rb") as f:
                    return f.read()
        for z in self.zips:
            try:
                return z.read(rel)
            except KeyError:
                pass
        return None

    def json(self, rel):
        if rel not in self._json:
            raw = self.lire(rel)
            try:
                self._json[rel] = json.loads(raw) if raw else None
            except ValueError:
                self._json[rel] = None
        return self._json[rel]

    def image(self, ref):
        if ref not in self._img:
            ns, p = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
            raw = None
            for pack in PACKS:
                f = os.path.join(pack, "assets", ns, "textures", p + ".png")
                if os.path.isfile(f):
                    with open(f, "rb") as h:
                        raw = h.read()
                    break
            if raw is None and ns != "minecraft":
                raw = self.lire(f"assets/{ns}/textures/{p}.png")
            im = None
            if raw:
                try:
                    im = Image.open(io.BytesIO(raw)).convert("RGBA")
                    w, h = im.size
                    if h > w and h % w == 0:
                        im = im.crop((0, 0, w, w))  # animated texture: first frame
                except OSError:
                    im = None
            self._img[ref] = im
        return self._img[ref]


ASSETS = None


def assets():
    global ASSETS
    if ASSETS is None:
        ASSETS = Assets()
    return ASSETS


def _moyenne(im, uv):
    """Alpha-weighted average colour of a texture region (uv in 0..16), returns (r,g,b,coverage)."""
    w, h = im.size
    u1, v1, u2, v2 = uv
    x0, x1 = sorted((u1 * w / 16.0, u2 * w / 16.0))
    y0, y1 = sorted((v1 * h / 16.0, v2 * h / 16.0))
    x0, y0 = max(0, int(x0)), max(0, int(y0))
    x1, y1 = min(w, max(x0 + 1, int(round(x1)))), min(h, max(y0 + 1, int(round(y1))))
    reg = im.crop((x0, y0, x1, y1))
    px = list(reg.getdata())
    tot = sum(p[3] for p in px)
    if tot <= 0:
        return (0, 0, 0, 0.0)
    r = sum(p[0] * p[3] for p in px) / tot
    g = sum(p[1] * p[3] for p in px) / tot
    b = sum(p[2] * p[3] for p in px) / tot
    return (r, g, b, tot / (255.0 * len(px)))


def _uv_defaut(face, f, t):
    x0, y0, z0 = f
    x1, y1, z1 = t
    if face in ("up", "down"):
        return (x0, z0, x1, z1)
    if face in ("north", "south"):
        return (x0, 16 - y1, x1, 16 - y0)
    return (z0, 16 - y1, z1, 16 - y0)


CUBES_VANILLE = {
    "block/cube_all": {"up": "#all", "down": "#all", "north": "#all", "south": "#all", "east": "#all", "west": "#all"},
    "block/cube_column": {"up": "#end", "down": "#end", "north": "#side", "south": "#side", "east": "#side", "west": "#side"},
    "block/cube_bottom_top": {"up": "#top", "down": "#bottom", "north": "#side", "south": "#side", "east": "#side", "west": "#side"},
    "block/cube": {d: "#" + d for d in ("up", "down", "north", "south", "east", "west")},
    "block/leaves": {d: "#all" for d in ("up", "down", "north", "south", "east", "west")},
}


def _modele(ref, profondeur=0):
    """Resolve a model: returns (elements, textures)."""
    a = assets()
    ns, p = ref.split(":", 1) if ":" in ref else ("minecraft", ref)
    textures = {}
    elements = None
    m = a.json(f"assets/{ns}/models/{p}.json")
    if m is None:
        key = p if p.startswith("block/") else "block/" + p
        if key in CUBES_VANILLE:
            faces = {d: {"texture": t} for d, t in CUBES_VANILLE[key].items()}
            return [{"from": [0, 0, 0], "to": [16, 16, 16], "faces": faces}], {}
        return None, {}
    if "parent" in m and profondeur < 10:
        pe, pt = _modele(m["parent"], profondeur + 1)
        textures.update(pt)
        elements = pe
    textures.update(m.get("textures", {}))
    if m.get("elements"):
        elements = m["elements"]
    if m.get("loader") == "forge:obj":
        elements = None
    return elements, textures


def _texture(ref, textures):
    seen = 0
    while ref and ref.startswith("#") and seen < 10:
        ref = textures.get(ref[1:])
        seen += 1
    return ref


ROT_DIR_Y = {"north": "east", "east": "south", "south": "west", "west": "north", "up": "up", "down": "down"}
ROT_DIR_X = {"north": "down", "down": "south", "south": "up", "up": "north", "east": "east", "west": "west"}
COURT = {"up": "u", "down": "d", "north": "n", "south": "s", "east": "e", "west": "w"}


def _tourner_point(x, y, z, rx, ry):
    # x rotation first (north -> down for 90), then y (north -> east for 90); around the block centre (8,8,8)
    dx, dy, dz = x - 8, y - 8, z - 8
    for _ in range((rx // 90) % 4):
        dy, dz = dz, -dy
    for _ in range((ry // 90) % 4):
        dx, dz = -dz, dx
    return dx + 8, dy + 8, dz + 8


def _tourner_dir(d, rx, ry):
    for _ in range((rx // 90) % 4):
        d = ROT_DIR_X[d]
    for _ in range((ry // 90) % 4):
        d = ROT_DIR_Y[d]
    return d


def _variante(etat_props, blockstate):
    v = blockstate.get("variants")
    if v:
        for cle, val in v.items():
            ok = True
            if cle:
                for kv in cle.split(","):
                    k, _, val_k = kv.partition("=")
                    if etat_props.get(k) != val_k:
                        ok = False
                        break
            if ok:
                return [val[0] if isinstance(val, list) else val]
        val = next(iter(v.values()))
        return [val[0] if isinstance(val, list) else val]
    out = []
    for part in blockstate.get("multipart", []):
        quand = part.get("when")
        ok = True
        if quand:
            conds = quand.get("OR", [quand])
            ok = False
            for c in conds:
                if all(etat_props.get(k) in str(vv).split("|") for k, vv in c.items()):
                    ok = True
                    break
        if ok:
            ap = part["apply"]
            out.append(ap[0] if isinstance(ap, list) else ap)
    return out


def boites_modele(nom, props):
    """Boxes from a modded block's real model; None if not resolvable."""
    a = assets()
    ns, p = nom.split(":", 1)
    bs = a.json(f"assets/{ns}/blockstates/{p}.json")
    if bs is None:
        return None
    boites = []
    for var in _variante(props, bs):
        elements, textures = _modele(var["model"])
        if not elements:
            continue
        rx, ry = int(var.get("x", 0)), int(var.get("y", 0))
        for el in elements:
            f, t = el["from"], el["to"]
            pts = [_tourner_point(x, y, z, rx, ry) for x in (f[0], t[0]) for y in (f[1], t[1]) for z in (f[2], t[2])]
            b = (min(q[0] for q in pts), min(q[1] for q in pts), min(q[2] for q in pts),
                 max(q[0] for q in pts), max(q[1] for q in pts), max(q[2] for q in pts))
            faces = {}
            texs = {}
            for fd, fv in el.get("faces", {}).items():
                tex = _texture(fv.get("texture"), textures)
                rgba = None
                if tex:
                    im = a.image(tex)
                    if im is not None:
                        uv = fv.get("uv") or _uv_defaut(fd, f, t)
                        rgba = _moyenne(im, uv)
                    else:
                        short = tex.split(":")[-1].split("/")[-1]
                        c = materiau(short)
                        if c is not None:
                            rgba = (*c[0 if fd == "up" else 1], 1.0)
                if rgba is None:
                    rgba = (128, 128, 128, 1.0)
                if rgba[3] < 0.12:
                    continue
                alpha = 1.0 if rgba[3] > 0.85 else max(0.35, rgba[3])
                dd = COURT[_tourner_dir(fd, rx, ry)]
                faces[dd] = (rgba[0], rgba[1], rgba[2], alpha)
                if tex and alpha >= 1.0 and a.image(tex) is not None:
                    uv = fv.get("uv") or _uv_defaut(fd, f, t)
                    texs[dd] = (tex, coef_modele(dd, b, uv), None)
            bb = list(b)
            if faces and not (b[3] - b[0] > 0.01 and b[4] - b[1] > 0.01 and b[5] - b[2] > 0.01):
                # flat element (plane): give it a tiny thickness so its faces exist
                for i in range(3):
                    if bb[i + 3] - bb[i] <= 0.01:
                        bb[i + 3] = bb[i] + 0.15
            if faces:
                boites.append({"b": tuple(bb), "f": faces, "t": texs})
    return boites or None


# ------------------------------------------------------------------------------------------------ vanilla shapes
def nom_props(etat):
    if "[" in etat:
        n, rest = etat.split("[", 1)
        props = dict(kv.split("=", 1) for kv in rest.rstrip("]").split(",") if "=" in kv)
    else:
        n, props = etat, {}
    return n, props


def _boite(x0, y0, z0, x1, y1, z1, top, side, alpha=1.0, faces=None):
    f = {}
    for d in faces or ("u", "d", "n", "s", "e", "w"):
        c = top if d in ("u", "d") else side
        f[d] = (c[0], c[1], c[2], alpha)
    return {"b": (x0, y0, z0, x1, y1, z1), "f": f}


def _log(top, side, axis):
    f = {}
    for d in ("u", "d", "n", "s", "e", "w"):
        bout = (axis == "y" and d in ("u", "d")) or (axis == "x" and d in ("e", "w")) or (axis == "z" and d in ("n", "s"))
        c = top if bout else side
        f[d] = (c[0], c[1], c[2], 1.0)
    return {"b": (0, 0, 0, 16, 16, 16), "f": f}


class Bloc:
    __slots__ = ("etat", "nom", "boites", "plein", "occ", "transl", "visible", "connu")

    def __init__(self, etat):
        self.etat = etat
        self.nom, props = nom_props(etat)
        self.boites, self.plein, self.occ, self.transl, self.visible, self.connu = [], False, False, None, True, True
        if self.nom in INVISIBLES:
            self.visible = False
            return
        ns, n = self.nom.split(":", 1)
        if ns != "minecraft":
            self._modde(n, props)
            return
        self._vanille(n, props)
        self._habiller(n, props)

    def _habiller(self, n, props):
        noms = noms_faces(n)
        if noms is None:
            return
        haut, bas, cote = noms
        axe = props.get("axis", "y")
        if n == "barrel":
            fa = props.get("facing", "up")
            axe = "y" if fa in ("up", "down") else ("x" if fa in ("east", "west") else "z")
        bout_dirs = {"y": ("u", "d"), "x": ("e", "w"), "z": ("n", "s")}[axe] if (
            n.endswith("_log") or n.endswith("_wood") or n in ("hay_block", "quartz_pillar", "barrel")) else ("u", "d")
        for bo in self.boites:
            t = {}
            for d in bo["f"]:
                if d in bout_dirs:
                    nom_t = haut if d != "d" or bout_dirs != ("u", "d") else bas
                else:
                    nom_t = cote
                ref = "minecraft:block/" + nom_t
                teinte = teinte_texture(nom_t)
                if n == "grass_block" and props.get("snowy") == "true" and d == "u":
                    ref, teinte = "minecraft:block/snow", None
                t[d] = (ref, COEF_VANILLE[d], teinte)
            bo["t"] = t

    # ---------------------------------------------------------------- modded
    def _modde(self, n, props):
        # slabs, stairs and walls of our decor mod use vanilla template models (minecraft:block/slab, inner_stairs,
        # template_wall_post) that are not in the assets read here: same geometry as vanilla, texture of the base block
        if n.endswith("_dalle") or n.endswith("_escalier") or n.endswith("_mur"):
            if self._decor_forme(n, props):
                return
        b = None
        try:
            b = boites_modele(self.nom, props)
        except Exception:  # noqa: BLE001 - a broken model must not stop the render
            b = None
        if b:
            self.boites = b
            vol = sum((q["b"][3] - q["b"][0]) * (q["b"][4] - q["b"][1]) * (q["b"][5] - q["b"][2]) for q in b)
            self.occ = vol > 0.45 * 4096
            return
        # fallbacks for OBJ models or missing assets
        self.connu = False
        if "barbeles_piquet" in n:
            self.boites = [_boite(7, 0, 7, 9, 14, 9, (95, 85, 72), (95, 85, 72))]
        elif "barbeles" in n:
            self.boites = [_boite(0, 0, 3, 16, 9, 13, (75, 75, 78), (75, 75, 78), alpha=0.55)]
        else:
            self.boites = [_boite(3, 0, 3, 13, 11, 13, (128, 128, 128), (118, 118, 118))]

    def _decor_forme(self, n, props):
        ns = self.nom.split(":")[0]
        base = n.rsplit("_", 1)[0]
        tex = f"{ns}:block/{base}"
        moy = moyenne_texture(tex)
        if moy is None:
            return False
        c = moy[0]
        fake = "stone_slab" if n.endswith("_dalle") else ("stone_stairs" if n.endswith("_escalier") else "stone_brick_wall")
        self.boites = []
        self._vanille(fake, props)
        for bo in self.boites:
            for d in list(bo["f"]):
                bo["f"][d] = (c[0], c[1], c[2], 1.0)
            bo["t"] = {d: (tex, COEF_VANILLE[d], None) for d in bo["f"]}
        self.connu = True
        return True

    # ---------------------------------------------------------------- vanilla
    def _vanille(self, n, props):
        c = materiau(n)
        if c is None:
            self.connu = False
            c = ((150, 140, 150), (140, 130, 140))
        top, side = c
        bx = self.boites
        if n == "water":
            self.boites = [_boite(0, 0, 0, 16, 14, 16, top, side, alpha=0.72)]
            self.transl = "water"
            return
        if n.endswith("_stained_glass") or n in ("glass", "tinted_glass"):
            self.boites = [_boite(0, 0, 0, 16, 16, 16, top, side, alpha=0.45)]
            self.transl = n
            return
        if n.endswith("_slab"):
            t = props.get("type", "bottom")
            if t == "double":
                bx.append(_boite(0, 0, 0, 16, 16, 16, top, side))
                self.plein = self.occ = True
            elif t == "top":
                bx.append(_boite(0, 8, 0, 16, 16, 16, top, side))
                self.occ = True
            else:
                bx.append(_boite(0, 0, 0, 16, 8, 16, top, side))
                self.occ = True
            return
        if n.endswith("_stairs"):
            f, h = props.get("facing", "north"), props.get("half", "bottom")
            demi = {"north": (0, 0, 16, 8), "south": (0, 8, 16, 16), "east": (8, 0, 16, 16), "west": (0, 0, 8, 16)}[f]
            if h == "top":
                bx.append(_boite(0, 8, 0, 16, 16, 16, top, side))
                bx.append(_boite(demi[0], 0, demi[1], demi[2], 8, demi[3], top, side))
            else:
                bx.append(_boite(0, 0, 0, 16, 8, 16, top, side))
                bx.append(_boite(demi[0], 8, demi[1], demi[2], 16, demi[3], top, side))
            self.occ = True
            return
        if n.endswith("_fence"):
            bx.append(_boite(6, 0, 6, 10, 16, 10, top, side))
            for d, (a0, a1, b0, b1) in (("north", (7, 9, 0, 6)), ("south", (7, 9, 10, 16)),
                                        ("west", (0, 6, 7, 9)), ("east", (10, 16, 7, 9))):
                if props.get(d) == "true":
                    for y0, y1 in ((6, 9), (12, 15)):
                        bx.append(_boite(a0, y0, b0, a1, y1, b1, top, side))
            return
        if n.endswith("_wall"):
            if props.get("up", "true") == "true" or not any(props.get(d, "none") != "none" for d in ("north", "south", "east", "west")):
                bx.append(_boite(4, 0, 4, 12, 16, 12, top, side))
            for d, (a0, a1, b0, b1) in (("north", (5, 11, 0, 8)), ("south", (5, 11, 8, 16)),
                                        ("west", (0, 8, 5, 11)), ("east", (8, 16, 5, 11))):
                v = props.get(d, "none")
                if v != "none":
                    bx.append(_boite(a0, 0, b0, a1, 16 if v == "tall" else 14, b1, top, side))
            self.occ = True
            return
        if n == "iron_bars" or n.endswith("_pane"):
            alpha = 0.55 if n.endswith("_pane") else 1.0
            bx.append(_boite(7, 0, 7, 9, 16, 9, top, side, alpha))
            for d, (a0, a1, b0, b1) in (("north", (7, 9, 0, 7)), ("south", (7, 9, 9, 16)),
                                        ("west", (0, 7, 7, 9)), ("east", (9, 16, 7, 9))):
                if props.get(d) == "true":
                    bx.append(_boite(a0, 0, b0, a1, 16, b1, top, side, alpha))
            return
        if n == "chain":
            a = props.get("axis", "y")
            if a == "y":
                bx.append(_boite(6.5, 0, 6.5, 9.5, 16, 9.5, top, side))
            elif a == "x":
                bx.append(_boite(0, 6.5, 6.5, 16, 9.5, 9.5, top, side))
            else:
                bx.append(_boite(6.5, 6.5, 0, 9.5, 9.5, 16, top, side))
            return
        if n == "ladder":
            f = props.get("facing", "north")
            b = {"west": (13, 0, 0, 16, 16, 16), "east": (0, 0, 0, 3, 16, 16),
                 "north": (0, 0, 13, 16, 16, 16), "south": (0, 0, 0, 16, 16, 3)}[f]
            bx.append(_boite(*b, top, side))
            return
        if n.endswith("rail"):
            bx.append(_boite(0, 0, 0, 16, 1.5, 16, top, side))
            return
        if n.endswith("_carpet"):
            bx.append(_boite(0, 0, 0, 16, 1, 16, top, side))
            return
        if n == "snow":
            h = int(props.get("layers", "1")) * 2
            bx.append(_boite(0, 0, 0, 16, h, 16, top, side))
            if h >= 16:
                self.plein = True
            self.occ = h >= 8
            return
        if n in ("lantern", "soul_lantern"):
            if props.get("hanging") == "true":
                bx.append(_boite(5, 1, 5, 11, 8, 11, top, side))
                bx.append(_boite(7, 8, 7, 9, 16, 9, BASE["chain"], BASE["chain"]))
            else:
                bx.append(_boite(5, 0, 5, 11, 7, 11, top, side))
            return
        if n in ("torch", "wall_torch", "soul_torch"):
            bx.append(_boite(7, 0, 7, 9, 10, 9, top, side))
            return
        if n == "lightning_rod":
            bx.append(_boite(7, 0, 7, 9, 16, 9, top, side))
            return
        if n == "bell":
            bx.append(_boite(4, 4, 4, 12, 13, 12, top, side))
            bx.append(_boite(2, 13, 7, 14, 15, 9, (60, 50, 40), (60, 50, 40)))
            return
        if n.endswith("_trapdoor"):
            f, op, h = props.get("facing", "north"), props.get("open", "false"), props.get("half", "bottom")
            if op == "true":
                b = {"north": (0, 0, 13, 16, 16, 16), "south": (0, 0, 0, 16, 16, 3),
                     "east": (0, 0, 0, 3, 16, 16), "west": (13, 0, 0, 16, 16, 16)}[f]
            else:
                b = (0, 13, 0, 16, 16, 16) if h == "top" else (0, 0, 0, 16, 3, 16)
            bx.append(_boite(*b, top, side))
            return
        if n.endswith("_door"):
            f = props.get("facing", "north")
            b = {"north": (0, 0, 13, 16, 16, 16), "south": (0, 0, 0, 16, 16, 3),
                 "east": (0, 0, 0, 3, 16, 16), "west": (13, 0, 0, 16, 16, 16)}[f]
            bx.append(_boite(*b, top, side))
            return
        if n in ("dead_bush", "grass", "fern", "tall_grass", "short_grass") or n.endswith("_sapling") or n in (
                "poppy", "dandelion", "cornflower", "azure_bluet", "oxeye_daisy", "sweet_berry_bush"):
            bx.append(_boite(3, 0, 3, 13, 11, 13, top, side, alpha=0.8))
            return
        if n.endswith("_button") or n.endswith("_pressure_plate") or n == "flower_pot" or n.endswith("_head") or "candle" in n:
            bx.append(_boite(5, 0, 5, 11, 5, 11, top, side))
            return
        if n.endswith("_fence_gate"):
            bx.append(_boite(0, 5, 7, 16, 15, 9, top, side))
            return
        if n.endswith("_log") or n.endswith("_wood") or n in ("hay_block", "quartz_pillar", "basalt", "bone_block"):
            bx.append(_log(top, side, props.get("axis", "y")))
            self.plein = self.occ = True
            return
        if n == "barrel":
            fa = props.get("facing", "up")
            axis = "y" if fa in ("up", "down") else ("x" if fa in ("east", "west") else "z")
            bx.append(_log(top, side, axis))
            self.plein = self.occ = True
            return
        if n.endswith("_leaves"):
            bx.append(_boite(0, 0, 0, 16, 16, 16, top, side))
            self.plein = self.occ = True
            self.transl = None
            return
        if n == "grass_block" and props.get("snowy") == "true":
            top = BASE["snow"]
        if n == "cobweb":
            bx.append(_boite(0, 0, 0, 16, 16, 16, top, side, alpha=0.3))
            return
        bx.append(_boite(0, 0, 0, 16, 16, 16, top, side))
        self.plein = self.occ = True


_CACHE = {}


def bloc(etat):
    b = _CACHE.get(etat)
    if b is None:
        b = Bloc(etat)
        _CACHE[etat] = b
    return b
