"""Lecture d'un monde Minecraft 1.20.1 pour l'analyse des props (LECTURE SEULE : rien n'est jamais écrit dans le monde).

Fonctions communes à outils/props_carte.py : lecture des fichiers de régions (*.mca), des sections de chunk (palette
avec les propriétés des blocs, indices tassés), des entités de bloc (NBT complet, rendu en SNBT pour les commandes), et
classement de chaque bloc pour l'analyse des pièces :

    VIDE   air
    FIN    passage libre qui ne fait pas plafond (plantes, tapis, torches, feuilles, eau, panneaux...)
    PROP   nos props et conteneurs (libres pour l'analyse : ils sont DANS la pièce)
    PORTE  portes et portillons (séparent deux pièces, ne font pas plafond)
    MUR    tout le reste (murs, sols, plafonds, vitres, barrières, escaliers, dalles...)
"""
import gzip
import struct
import zlib
from pathlib import Path

import numpy as np

VIDE, FIN, PROP, PORTE, MUR = 0, 1, 2, 3, 4

AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:light", "minecraft:structure_void"}
_FINS = ("grass", "fern", "flower", "tulip", "poppy", "dandelion", "orchid", "allium", "bluet", "daisy", "cornflower",
         "lily", "rose", "peony", "lilac", "sunflower", "bush", "sapling", "mushroom", "vine", "carpet", "rail", "torch",
         "button", "pressure_plate", "lever", "redstone_wire", "tripwire", "sign", "banner", "flower_pot", "potted_",
         "candle", "ladder", "snow", "cobweb", "lantern", "chain", "skull", "head", "seagrass", "kelp", "water", "leaves",
         "sugar_cane", "wheat", "carrots", "potatoes", "beetroots", "_stem", "dead_bush", "moss_carpet", "lichen",
         "petals", "dripleaf", "roots", "bamboo", "hanging_sign", "item_frame", "fire", "end_rod", "lightning_rod",
         "scaffolding", "string", "pointed_dripstone", "amethyst_cluster", "bud", "frogspawn", "sculk_vein",
         "tache", "herbe", "fleur", "buisson", "fougere", "roseau", "ronce", "guirlande", "lampe_suspendue",
         "lanterne", "mousse_pendante", "stalactite", "eau_decor", "ambiance", "tapis", "corbeaux")
_PORTES = ("_door", "fence_gate", "metal_door_", "century_door", "porte")
import re
_MURS_FORCES = re.compile(r"(_wall$|_fence$|^fence$|glass_pane|iron_bars|grillage|barbeles|herisson)")


class Classeur:
    """Classe de chaque état de bloc (cache par nom ; les props sont donnés à la construction)."""

    def __init__(self, props, structure):
        self.props = props            # ids des props (libres pour l'analyse)
        self.structure = structure    # ids de props qui font mur (vitres de cfm, garde-corps...)
        self.cache = {}

    def classe(self, nom):
        c = self.cache.get(nom)
        if c is not None:
            return c
        c = self._classe(nom)
        self.cache[nom] = c
        return c

    def _classe(self, nom):
        if nom in AIRS:
            return VIDE
        if nom in self.structure or nom == "heralisz_utils:corps":
            return MUR
        if nom in self.props or nom == "heraliz_loot:partie":
            return PROP
        ns, _, p = nom.partition(":")
        if ns == "heraliz_loot" or ns == "cfm":
            return PROP
        if any(m in p for m in _PORTES) and "trapdoor" not in p:
            return PORTE
        if _MURS_FORCES.search(p):
            return MUR
        if ns in ("heralisz_utils", "heraliz_decors"):
            return MUR if _materiau(p) else FIN
        if any(m in p for m in _FINS) and not p.endswith("_block") and "slab" not in p and "stairs" not in p:
            return FIN
        return MUR


def _materiau(p):
    mots = ("sable", "enduit", "brique", "pierre", "dallage", "carreaux", "paves", "zellige", "bois_", "rondin", "tole",
            "panneau_polaire", "beton", "neige", "glace", "roche", "rocher", "hublot", "moucharabieh", "toile", "palmes",
            "cristal", "cascade", "echaf", "pont_corde", "voie_pleine", "collision", "terre", "tuyau", "projecteur")
    return any(m in p for m in mots)


# -------------------------------------------------------------------------------------------------------- NBT
class Nbt:
    """Lecteur NBT (big-endian). Les sections et entités de bloc sont lues, le reste du chunk est sauté."""

    __slots__ = ("d", "i")

    def __init__(self, d):
        self.d = d
        self.i = 0

    def u8(self):
        v = self.d[self.i]
        self.i += 1
        return v

    def chaine(self):
        n = struct.unpack_from(">H", self.d, self.i)[0]
        s = self.d[self.i + 2:self.i + 2 + n].decode("utf-8", "replace")
        self.i += 2 + n
        return s

    def sauter(self, t):
        d = self.d
        if t == 1:
            self.i += 1
        elif t == 2:
            self.i += 2
        elif t in (3, 5):
            self.i += 4
        elif t in (4, 6):
            self.i += 8
        elif t == 7:
            self.i += 4 + struct.unpack_from(">i", d, self.i)[0]
        elif t == 8:
            self.i += 2 + struct.unpack_from(">H", d, self.i)[0]
        elif t == 9:
            et = d[self.i]
            n = struct.unpack_from(">i", d, self.i + 1)[0]
            self.i += 5
            taille = {1: 1, 2: 2, 3: 4, 4: 8, 5: 4, 6: 8}.get(et)
            if taille is not None:
                self.i += taille * max(0, n)
            else:
                for _ in range(max(0, n)):
                    self.sauter(et)
        elif t == 10:
            while True:
                tt = d[self.i]
                self.i += 1
                if tt == 0:
                    return
                self.i += 2 + struct.unpack_from(">H", d, self.i)[0]
                self.sauter(tt)
        elif t == 11:
            self.i += 4 + 4 * struct.unpack_from(">i", d, self.i)[0]
        elif t == 12:
            self.i += 4 + 8 * struct.unpack_from(">i", d, self.i)[0]
        else:
            raise ValueError("type NBT inconnu %d" % t)

    def valeur(self, t):
        """Valeur typée complète : (type, valeur) ; compound -> dict, list -> (type des éléments, [valeurs])."""
        d = self.d
        if t == 1:
            v = struct.unpack_from(">b", d, self.i)[0]; self.i += 1
        elif t == 2:
            v = struct.unpack_from(">h", d, self.i)[0]; self.i += 2
        elif t == 3:
            v = struct.unpack_from(">i", d, self.i)[0]; self.i += 4
        elif t == 4:
            v = struct.unpack_from(">q", d, self.i)[0]; self.i += 8
        elif t == 5:
            v = struct.unpack_from(">f", d, self.i)[0]; self.i += 4
        elif t == 6:
            v = struct.unpack_from(">d", d, self.i)[0]; self.i += 8
        elif t == 7:
            n = struct.unpack_from(">i", d, self.i)[0]
            v = list(struct.unpack_from(">%db" % n, d, self.i + 4)); self.i += 4 + n
        elif t == 8:
            v = self.chaine()
        elif t == 9:
            et = d[self.i]
            n = struct.unpack_from(">i", d, self.i + 1)[0]
            self.i += 5
            v = (et, [self.valeur(et)[1] for _ in range(max(0, n))])
        elif t == 10:
            v = {}
            while True:
                tt = d[self.i]
                self.i += 1
                if tt == 0:
                    break
                k = self.chaine()
                v[k] = self.valeur(tt)
        elif t == 11:
            n = struct.unpack_from(">i", d, self.i)[0]
            v = list(struct.unpack_from(">%di" % n, d, self.i + 4)); self.i += 4 + 4 * n
        elif t == 12:
            n = struct.unpack_from(">i", d, self.i)[0]
            v = list(struct.unpack_from(">%dq" % n, d, self.i + 4)); self.i += 4 + 8 * n
        else:
            raise ValueError("type NBT inconnu %d" % t)
        return t, v


def snbt(tv):
    """Valeur typée (type, valeur) -> SNBT (format des commandes)."""
    t, v = tv
    if t == 1:
        return "%db" % v
    if t == 2:
        return "%ds" % v
    if t == 3:
        return "%d" % v
    if t == 4:
        return "%dL" % v
    if t == 5:
        return "%rf" % v
    if t == 6:
        return "%rd" % v
    if t == 7:
        return "[B;" + ",".join("%db" % x for x in v) + "]"
    if t == 8:
        return _chaine_snbt(v)
    if t == 9:
        et, vals = v
        return "[" + ",".join(snbt((et, x)) for x in vals) + "]"
    if t == 10:
        return "{" + ",".join("%s:%s" % (_cle(k), snbt(x)) for k, x in v.items()) + "}"
    if t == 11:
        return "[I;" + ",".join("%d" % x for x in v) + "]"
    if t == 12:
        return "[L;" + ",".join("%dL" % x for x in v) + "]"
    raise ValueError(t)


def _cle(k):
    return k if k and all(c.isalnum() or c in "._+-" for c in k) else _chaine_snbt(k)


def _chaine_snbt(s):
    return '"' + s.replace("\\", "\\\\").replace('"', '\\"') + '"'


def nbt_entite_bloc(be):
    """SNBT des données d'une entité de bloc (sans x, y, z, id, keepPacked), ou "" si vide."""
    reste = {k: v for k, v in be.items() if k not in ("x", "y", "z", "id", "keepPacked")}
    return snbt((10, reste)) if reste else ""


def lire_chunk(data, garder_be):
    """{xPos, zPos, Status, sections: [(Y, palette [(nom, {prop: valeur})], octets des indices)], be: [dict]}."""
    r = Nbt(data)
    if r.u8() != 10:
        return None
    r.chaine()
    out = {"sections": [], "be": [], "Status": ""}
    d = r.d
    while True:
        t = d[r.i]
        r.i += 1
        if t == 0:
            break
        nom = r.chaine()
        if nom in ("xPos", "zPos", "yPos") and t == 3:
            out[nom] = struct.unpack_from(">i", d, r.i)[0]
            r.i += 4
        elif nom == "Status" and t == 8:
            out["Status"] = r.chaine()
        elif nom == "sections" and t == 9:
            et = d[r.i]
            n = struct.unpack_from(">i", d, r.i + 1)[0]
            r.i += 5
            for _ in range(n):
                if et != 10:
                    r.sauter(et)
                    continue
                sec = _section(r)
                if sec is not None:
                    out["sections"].append(sec)
        elif nom == "block_entities" and t == 9 and garder_be:
            et, vals = r.valeur(9)[1]
            if et == 10:
                out["be"] = vals
        else:
            r.sauter(t)
    return out


def _section(r):
    d = r.d
    y = None
    pal, donnees = [], None
    while True:
        t = d[r.i]
        r.i += 1
        if t == 0:
            break
        nom = r.chaine()
        if nom == "Y" and t == 1:
            y = struct.unpack_from(">b", d, r.i)[0]
            r.i += 1
        elif nom == "block_states" and t == 10:
            while True:
                t2 = d[r.i]
                r.i += 1
                if t2 == 0:
                    break
                n2 = r.chaine()
                if n2 == "palette" and t2 == 9:
                    et = d[r.i]
                    n = struct.unpack_from(">i", d, r.i + 1)[0]
                    r.i += 5
                    for _ in range(n):
                        if et != 10:
                            r.sauter(et)
                            continue
                        pal.append(_etat(r))
                elif n2 == "data" and t2 == 12:
                    n = struct.unpack_from(">i", d, r.i)[0]
                    donnees = d[r.i + 4:r.i + 4 + 8 * n]
                    r.i += 4 + 8 * n
                else:
                    r.sauter(t2)
        else:
            r.sauter(t)
    if y is None or not pal:
        return None
    return y, pal, donnees


def _etat(r):
    d = r.d
    nom, props = "minecraft:air", {}
    while True:
        t = d[r.i]
        r.i += 1
        if t == 0:
            break
        k = r.chaine()
        if k == "Name" and t == 8:
            nom = r.chaine()
        elif k == "Properties" and t == 10:
            while True:
                t2 = d[r.i]
                r.i += 1
                if t2 == 0:
                    break
                k2 = r.chaine()
                if t2 == 8:
                    props[k2] = r.chaine()
                else:
                    r.sauter(t2)
        else:
            r.sauter(t)
    return nom, props


def indices(donnees, n_palette):
    """Indices de palette (4096, ordre y, z, x) tassés dans des longs (format 1.16+)."""
    if n_palette <= 1 or not donnees:
        return np.zeros(4096, dtype=np.int32)
    bits = max(4, (n_palette - 1).bit_length())
    par_long = 64 // bits
    longs = np.frombuffer(donnees, dtype=">u8").astype(np.uint64)
    decal = np.arange(par_long, dtype=np.uint64) * np.uint64(bits)
    vals = ((longs[:, None] >> decal[None, :]) & np.uint64((1 << bits) - 1)).reshape(-1)[:4096]
    if vals.size < 4096:
        vals = np.concatenate([vals, np.zeros(4096 - vals.size, dtype=np.uint64)])
    return np.minimum(vals.astype(np.int32), n_palette - 1)


class Regions:
    """Accès aux chunks d'un dossier region/ (fichiers r.X.Z.mca), en lecture seule, avec cache des fichiers."""

    def __init__(self, dossier):
        self.dossier = Path(dossier)
        self.fichiers = {}

    def octets(self, cx, cz):
        rx, rz = cx >> 5, cz >> 5
        f = self.fichiers.get((rx, rz))
        if f is None:
            p = self.dossier / ("r.%d.%d.mca" % (rx, rz))
            f = p.read_bytes() if p.is_file() else b""
            self.fichiers[(rx, rz)] = f
        if len(f) < 8192:
            return None
        k = (cx & 31) + (cz & 31) * 32
        loc = struct.unpack_from(">I", f, k * 4)[0]
        off = (loc >> 8) * 4096
        if off == 0 or off + 5 > len(f):
            return None
        n = struct.unpack_from(">I", f, off)[0]
        comp = f[off + 4]
        brut = f[off + 5:off + 4 + n]
        try:
            if comp == 2:
                return zlib.decompress(brut)
            if comp == 1:
                return gzip.decompress(brut)
            if comp == 3:
                return brut
        except Exception:
            return None
        return None

    def chunk(self, cx, cz, garder_be=False):
        b = self.octets(cx, cz)
        if b is None:
            return None
        try:
            ch = lire_chunk(b, garder_be)
        except Exception:
            return None
        if ch is None:
            return None
        st = ch.get("Status") or ""
        if st and not (st.endswith("full") or st.endswith("spawn") or st.endswith("heightmaps")):
            return None
        return ch

    def regions(self):
        out = []
        for p in sorted(self.dossier.glob("r.*.*.mca")):
            try:
                _, x, z, _ = p.name.split(".")
                out.append((int(x), int(z)))
            except ValueError:
                pass
        return out


def dossier_region(monde, dimension="minecraft:overworld"):
    m = Path(monde)
    if dimension == "minecraft:overworld":
        return m / "region"
    if dimension == "minecraft:the_nether":
        return m / "DIM-1" / "region"
    if dimension == "minecraft:the_end":
        return m / "DIM1" / "region"
    ns, _, nom = dimension.partition(":")
    return m / "dimensions" / ns / nom / "region"


def etat_texte(nom, props):
    """« mod:bloc[a=b,c=d] » (propriétés triées)."""
    if not props:
        return nom
    return nom + "[" + ",".join("%s=%s" % (k, props[k]) for k in sorted(props)) + "]"


def lire_zone(dossier, x0, z0, x1, z1, ya, yb, classeur):
    """Classes des blocs d'une zone (bornes comprises) : tableau [y - ya][z - z0][x - x0], et noms des blocs."""
    regs = Regions(dossier)
    W, P, H = x1 - x0 + 1, z1 - z0 + 1, yb - ya + 1
    cls = np.zeros((H, P, W), dtype=np.uint8)
    noms = np.empty((H, P, W), dtype=object)
    noms[:] = "minecraft:air"
    for cx in range(x0 >> 4, (x1 >> 4) + 1):
        for cz in range(z0 >> 4, (z1 >> 4) + 1):
            ch = regs.chunk(cx, cz)
            if ch is None:
                continue
            for sy, pal, donnees in ch["sections"]:
                b0 = sy * 16
                if b0 + 16 <= ya or b0 > yb:
                    continue
                idx = indices(donnees, len(pal)).reshape(16, 16, 16)
                lut = np.array([classeur.classe(n) for n, _p in pal], dtype=np.uint8)
                lnoms = np.array([n for n, _p in pal], dtype=object)
                for yy in range(max(b0, ya), min(b0 + 16, yb + 1)):
                    for zz in range(max(cz * 16, z0), min(cz * 16 + 16, z1 + 1)):
                        xa, xb = max(cx * 16, x0), min(cx * 16 + 16, x1 + 1)
                        row = idx[yy - b0, zz - cz * 16, xa - cx * 16:xb - cx * 16]
                        cls[yy - ya, zz - z0, xa - x0:xb - x0] = lut[row]
                        noms[yy - ya, zz - z0, xa - x0:xb - x0] = lnoms[row]
    return cls, noms


def chunks_forces(monde):
    """Chunks forcés (/forceload) du monde d'après data/chunks.dat : ensemble de (cx, cz). Vide si absent."""
    f = Path(monde) / "data" / "chunks.dat"
    if not f.is_file():
        return set()
    try:
        b = gzip.decompress(f.read_bytes())
    except OSError:
        b = f.read_bytes()
    try:
        r = Nbt(b)
        if r.u8() != 10:
            return set()
        r.chaine()
        racine = r.valeur(10)[1]
    except Exception:
        return set()
    data = racine.get("data", (10, {}))[1]
    forces = data.get("Forced", (12, []))[1]
    out = set()
    for v in forces:
        x = v & 0xFFFFFFFF
        z = (v >> 32) & 0xFFFFFFFF
        out.add((x - (1 << 32) if x >= 1 << 31 else x, z - (1 << 32) if z >= 1 << 31 else z))
    return out


def lire_dat(chemin):
    """Racine (dict de valeurs typées) d'un fichier .dat de données du monde (NBT compressé), ou None."""
    f = Path(chemin)
    if not f.is_file():
        return None
    b = f.read_bytes()
    try:
        b = gzip.decompress(b)
    except OSError:
        pass
    try:
        r = Nbt(b)
        if r.u8() != 10:
            return None
        r.chaine()
        return r.valeur(10)[1]
    except Exception:
        return None


def position(v):
    """BlockPos.asLong -> (x, y, z)."""
    x = v >> 38
    y = ((v << 52) & 0xFFFFFFFFFFFFFFFF) >> 52
    z = ((v << 26) & 0xFFFFFFFFFFFFFFFF) >> 38
    y = y - (1 << 12) if y >= 1 << 11 else y
    z = z - (1 << 26) if z >= 1 << 25 else z
    return int(x), int(y), int(z)


def donnees_loot(monde):
    """Données de HeraliZ Loot gardées avec le monde : parts réglées, conteneurs des zones créées en jeu, points T7."""
    out = {"parts": {}, "places": [], "points": []}
    rot = lire_dat(Path(monde) / "data" / "heraliz_loot_rotation.dat")
    if rot:
        d = rot.get("data", (10, {}))[1]
        for c in d.get("Parts", (9, (10, [])))[1][1]:
            out["parts"][c["Zone"][1]] = c["Part"][1]
        for c in d.get("Places", (9, (10, [])))[1][1]:
            out["places"].append(list(position(c["Pos"][1])) + [c["Zone"][1], c["Nom"][1]])
    pts = lire_dat(Path(monde) / "data" / "heraliz_loot_points_t7.dat")
    if pts:
        d = pts.get("data", (10, {}))[1]
        for c in d.get("Points", (9, (10, [])))[1][1]:
            out["points"].append({"nom": c.get("Nom", (8, ""))[1], "type": c.get("Type", (8, ""))[1],
                                  "pos": list(position(c["Pos"][1])) if "Pos" in c else None})
    return out
