"""Fond de la carte du monde (écran « Carte » de HeraliZ Utils, touche M) : la vue de dessus de tout le monde, refaite
depuis ses fichiers de régions. LECTURE SEULE : rien n'est jamais écrit dans le dossier du monde.

    python outils/carte_monde.py <dossier du monde> [options]
    python outils/carte_monde.py <dossier du monde> --installer      (met les images dans le mod, à fabriquer ensuite)

Deux vues, comme sur les maquettes validées :
- « satellite » : le bloc du dessus de chaque colonne (couleur moyenne de sa texture, celle du pack imposé par
  heraliz_interface d'abord), teinte du biome pour l'herbe, les feuilles et l'eau, ombre du relief, eau plus sombre
  quand elle est profonde ;
- « relief » (topographique) : teintes par altitude, ombrage, courbes de niveau (5 et 25 blocs), eau, forêts, champs,
  bâtiments et routes.

Sortie (dossier --sortie, défaut livraison/carte, hors Git) : tuiles PNG de 1024 px au plus par vue
(satellite_<colonne>_<ligne>.png, relief_<colonne>_<ligne>.png) et carte.json (emprise en blocs, blocs par pixel,
tuiles). L'écran de la carte les lit dans le jar joueur de HeraliZ Utils (assets/heraliz_carte/carte/) ; sans elles,
il montre un fond neutre et un message. --installer remplace celles du mod (puis fabriquer heralisz-utils).

Options :
  --sortie <dossier>    où écrire les images (défaut : livraison/carte)
  --installer           écrire dans le mod (modules/monde/src/client/resources/assets/heraliz_carte/carte)
  --dimension <id>      dimension lue (défaut minecraft:overworld, dossier region/ ; ex. heralisz_matchs:arenes)
  --pixels <n>          côté maximal de l'image entière (défaut 2048) : l'échelle (1, 2, 4... blocs par pixel) en découle
  --echelle <n>         blocs par pixel imposés (puissance de 2)
  --ignorer <ns,...>    namespaces de blocs vus « à travers » (défaut : crac, les constructions des joueurs)
  --textures <src>...   dossiers ou jars où lire les textures des blocs (défaut : pack imposé, nos mods, mods-tiers)
  --niveau-mer <y>      niveau de la mer (défaut 63)
  --processus <n>       processus de lecture (défaut : nombre de cœurs)
  --stats               liste les blocs du dessus les plus fréquents, leur classe et d'où vient leur couleur
  --bordure x1 z1 x2 z2 recadre le fond sur ce rectangle (coins en blocs, dans n'importe quel ordre)
  --bordure monde       recadre sur la bordure du monde lue dans son level.dat (DÉFAUT pour l'overworld quand le
                        level.dat est là ; sinon rien n'est recadré)
  --bordure aucune      pas de recadrage (tous les chunks lus)

Recadrage sur la bordure : au-delà de la bordure, les cartes faites à la main ont souvent un terrain « étiré » (dernière
ligne du relief répétée jusqu'au bout des régions : bandes de neige ou fond de mer en traits) ; personne ne peut y
aller, il n'a rien à faire sur la carte. Le rectangle retenu est écrit dans carte.json (« bordure ») : l'écran de la
carte n'affiche rien au-delà (ni fond, ni zones, ni lieux), en plus de la vraie bordure envoyée par le serveur.

Les constructions des joueurs posées sur la carte apparaîtraient sur le fond : le refaire depuis la carte propre (copie
finale, ou monde en construction avant l'ouverture). Les blocs de CRac sont ignorés d'office.
"""
import io
import json
import math
import os
import re
import shutil
import struct
import sys
import time
import zipfile
import zlib
from multiprocessing import Pool
from pathlib import Path

import numpy as np
from PIL import Image

DEPOT = Path(__file__).resolve().parent.parent
DOSSIER_MOD = DEPOT / "modules/monde/src/client/resources/assets/heraliz_carte/carte"
TUILE = 1024
VIDE = (16, 19, 23)  # hors des chunks lus : fond sombre de l'interface

# catégories de bloc pour la recherche du dessus de chaque colonne (seuils croissants)
TRANSPARENT, EAU, VEGETAL, CONSTRUCTION, SOL = 0, 1, 2, 3, 4
# classes pour la vue relief
C_SOL, C_EAU, C_ARBRE, C_PLANTE, C_CHAMP, C_ROUTE, C_BATIMENT, C_NEIGE = range(8)
NOMS_CLASSES = ["sol", "eau", "arbre", "plante", "champ", "route", "bâtiment", "neige"]


# ------------------------------------------------------------------ NBT (lecture partielle et rapide)
class Nbt:
    """Lecture d'un chunk : seules les clés utiles sont construites, le reste est sauté."""

    __slots__ = ("d", "i")

    def __init__(self, data):
        self.d = data
        self.i = 0

    def chaine(self):
        n = struct.unpack_from(">H", self.d, self.i)[0]
        s = self.d[self.i + 2:self.i + 2 + n].decode("utf-8", "replace")
        self.i += 2 + n
        return s

    def sauter(self, t):
        d = self.d
        if t in (1,):
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
                for _ in range(n):
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

    def longs(self):
        n = struct.unpack_from(">i", self.d, self.i)[0]
        b = self.d[self.i + 4:self.i + 4 + 8 * n]
        self.i += 4 + 8 * n
        return b

    def entier(self, t):
        fmt = {1: ">b", 2: ">h", 3: ">i", 4: ">q"}.get(t)
        if fmt is None:
            self.sauter(t)
            return None
        v = struct.unpack_from(fmt, self.d, self.i)[0]
        self.i += struct.calcsize(fmt)
        return v

    def compose(self, lecteurs):
        """Parcourt un compound : lecteurs[nom](type) lit la valeur, sinon elle est sautée."""
        d = self.d
        while True:
            t = d[self.i]
            self.i += 1
            if t == 0:
                return
            nom = self.chaine()
            f = lecteurs.get(nom)
            if f is None:
                self.sauter(t)
            else:
                f(t)


def lire_chunk(data):
    """dict : xPos, zPos, yPos, Status, sections [(Y, palette, data, palette biomes, data biomes)]."""
    r = Nbt(data)
    if r.d[0] != 10:
        return None
    r.i = 1
    r.chaine()
    out = {"sections": []}

    def entier(nom):
        return lambda t: out.__setitem__(nom, r.entier(t))

    def statut(t):
        if t == 8:
            out["Status"] = r.chaine()
        else:
            r.sauter(t)

    def sections(t):
        if t != 9:
            r.sauter(t)
            return
        et = r.d[r.i]
        n = struct.unpack_from(">i", r.d, r.i + 1)[0]
        r.i += 5
        for _ in range(n):
            if et != 10:
                r.sauter(et)
                continue
            sec = {"Y": None, "pal": [], "data": None, "bpal": [], "bdata": None}

            def y(t2, sec=sec):
                sec["Y"] = r.entier(t2)

            def etats(t2, sec=sec):
                if t2 != 10:
                    r.sauter(t2)
                    return

                def palette(t3):
                    if t3 != 9:
                        r.sauter(t3)
                        return
                    et3 = r.d[r.i]
                    n3 = struct.unpack_from(">i", r.d, r.i + 1)[0]
                    r.i += 5
                    for _ in range(n3):
                        if et3 != 10:
                            r.sauter(et3)
                            continue
                        nom = ["minecraft:air"]

                        def name(t4, nom=nom):
                            if t4 == 8:
                                nom[0] = r.chaine()
                            else:
                                r.sauter(t4)
                        r.compose({"Name": name})
                        sec["pal"].append(nom[0])

                def donnees(t3):
                    if t3 == 12:
                        sec["data"] = r.longs()
                    else:
                        r.sauter(t3)
                r.compose({"palette": palette, "data": donnees})

            def biomes(t2, sec=sec):
                if t2 != 10:
                    r.sauter(t2)
                    return

                def palette(t3):
                    if t3 != 9:
                        r.sauter(t3)
                        return
                    et3 = r.d[r.i]
                    n3 = struct.unpack_from(">i", r.d, r.i + 1)[0]
                    r.i += 5
                    for _ in range(n3):
                        if et3 == 8:
                            sec["bpal"].append(r.chaine())
                        else:
                            r.sauter(et3)

                def donnees(t3):
                    if t3 == 12:
                        sec["bdata"] = r.longs()
                    else:
                        r.sauter(t3)
                r.compose({"palette": palette, "data": donnees})
            r.compose({"Y": y, "block_states": etats, "biomes": biomes})
            if sec["Y"] is not None:
                out["sections"].append(sec)

    r.compose({"xPos": entier("xPos"), "zPos": entier("zPos"), "yPos": entier("yPos"), "Status": statut,
               "sections": sections})
    return out


def chunks_region(chemin):
    """(index du chunk dans la région 0..1023, octets NBT) ; les chunks illisibles sont sautés."""
    with open(chemin, "rb") as f:
        tout = f.read()
    if len(tout) < 8192:
        return
    for k in range(1024):
        loc = struct.unpack_from(">I", tout, k * 4)[0]
        off = (loc >> 8) * 4096
        if off == 0 or off + 5 > len(tout):
            continue
        n = struct.unpack_from(">I", tout, off)[0]
        comp = tout[off + 4]
        brut = tout[off + 5:off + 4 + n]
        try:
            if comp == 2:
                yield k, zlib.decompress(brut)
            elif comp == 1:
                import gzip
                yield k, gzip.decompress(brut)
            elif comp == 3:
                yield k, brut
        except Exception:
            continue


def indices(donnees, n_palette, n_valeurs, minimum_bits):
    """Indices de palette tassés dans des longs (format 1.16+ : pas de valeur à cheval sur deux longs)."""
    if n_palette <= 1 or not donnees:
        return np.zeros(n_valeurs, dtype=np.int32)
    bits = max(minimum_bits, (n_palette - 1).bit_length())
    par_long = 64 // bits
    longs = np.frombuffer(donnees, dtype=">u8").astype(np.uint64)
    decal = (np.arange(par_long, dtype=np.uint64) * np.uint64(bits))
    vals = (longs[:, None] >> decal[None, :]) & np.uint64((1 << bits) - 1)
    vals = vals.reshape(-1)[:n_valeurs]
    if vals.size < n_valeurs:
        vals = np.concatenate([vals, np.zeros(n_valeurs - vals.size, dtype=np.uint64)])
    return np.minimum(vals.astype(np.int32), n_palette - 1)


# ------------------------------------------------------------------ catégories et classes des blocs
AIRS = {"minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:barrier", "minecraft:light",
        "minecraft:structure_void"}
EAUX = ("minecraft:water", "minecraft:bubble_column", "minecraft:kelp", "minecraft:kelp_plant", "minecraft:seagrass",
        "minecraft:tall_seagrass")
PLANTES = re.compile(r"(^|_)(grass|tall_grass|fern|large_fern|dead_bush|dandelion|poppy|blue_orchid|allium|azure_bluet|"
                     r"tulip|oxeye_daisy|cornflower|lily_of_the_valley|wither_rose|sunflower|lilac|rose_bush|peony|"
                     r"sweet_berry_bush|sugar_cane|vine|vines|lily_pad|sapling|mushroom|bush|flower|flowers|moss_carpet|"
                     r"pink_petals|torchflower|pitcher_plant|small_dripleaf|big_dripleaf|hanging_roots|glow_lichen|"
                     r"herbe|herbes|fleur|fleurs|buisson|fougere|roseau|roseaux|ronce|ronces)($|_)")
CULTURES = re.compile(r"(^|_)(wheat|carrots|potatoes|beetroots|melon_stem|pumpkin_stem|crop|crops|culture|cultures|"
                      r"ble|mais)($|_)")
ARBRES = re.compile(r"(leaves|_log$|_wood$|_stem$|_hyphae$|^log|mangrove_roots|bamboo$|feuillage|feuilles|tronc|"
                    r"branche|branches|arbre)")
ROUTES = re.compile(r"(^dirt_path$|^gravel$|route|asphalt|asphalte|goudron|bitume|chaussee|trottoir|^road|_road)")
SOLS = re.compile(r"^(grass_block|dirt|coarse_dirt|rooted_dirt|podzol|mycelium|mud|clay|sand|red_sand|suspicious_sand|"
                  r"suspicious_gravel|gravel|stone|andesite|diorite|granite|deepslate|tuff|calcite|dripstone_block|"
                  r"snow|snow_block|powder_snow|ice|packed_ice|blue_ice|moss_block|sandstone|red_sandstone|"
                  r"bedrock|[a-z_]*_ore|magma_block|obsidian|netherrack|soul_sand|soul_soil|"
                  r"end_stone|farmland|dirt_path|muddy_mangrove_roots|smooth_basalt|basalt|blackstone|"
                  r"amethyst_block|budding_amethyst|raw_[a-z]+_block|lava)$")
SOLS_MODS = re.compile(r"(sable|neige|glace|terre|herbe_bloc|roche|rocher|boue|gravier|caillou|cailloux|permafrost|"
                       r"toundra|dune|argile|falaise|desert|arctique|sol_)")
# mots d'un bloc construit, même s'il contient « sable » ou « terre » (enduit_sable_mur, terre_cuite...)
BATIS = re.compile(r"(mur|toit|enduit|brique|dalle|dallage|escalier|tuile|beton|carreau|pave|planche|tole|porte|"
                   r"fenetre|vitre|grille|grillage|cuite|_stairs|_slab|_wall)")
NEIGES = re.compile(r"(^snow$|^snow_block$|^powder_snow$|neige|^ice$|^packed_ice$|^blue_ice$|glace)")
# décors posés à plat sur le sol (taches de sang...) : vus à travers
A_PLAT = re.compile(r"(^|_)(tache|taches|sang|decal|flaque)(_|$)")


def sol_naturel(ns, chemin):
    """Bloc de terrain (pas construit) : liste de Minecraft, ou nom de décor de terrain de nos mods."""
    if ns == "minecraft":
        return bool(SOLS.match(chemin))
    return bool(SOLS_MODS.search(chemin)) and not BATIS.search(chemin)


def categorie(nom, ignores):
    """Catégorie du bloc pour la recherche du dessus (voir les seuils plus haut)."""
    if nom in AIRS:
        return TRANSPARENT
    ns, _, chemin = nom.partition(":")
    if ns in ignores or A_PLAT.search(chemin):
        return TRANSPARENT
    if nom in EAUX:
        return EAU
    if sol_naturel(ns, chemin):
        return SOL
    if ARBRES.search(chemin) or PLANTES.search(chemin) or CULTURES.search(chemin):
        return VEGETAL
    if ROUTES.search(chemin):
        return SOL
    return CONSTRUCTION


def classe(nom):
    """Classe du bloc du dessus pour la vue relief (un bloc construit au ras du sol devient une rue au rendu)."""
    ns, _, chemin = nom.partition(":")
    if nom in EAUX:
        return C_EAU
    if ROUTES.search(chemin):
        return C_ROUTE
    if CULTURES.search(chemin) or chemin in ("farmland", "hay_block"):
        return C_CHAMP
    if NEIGES.search(chemin) and not BATIS.search(chemin):
        return C_NEIGE
    if sol_naturel(ns, chemin):
        return C_SOL
    if ARBRES.search(chemin):
        return C_ARBRE
    if PLANTES.search(chemin):
        return C_PLANTE
    return C_BATIMENT


# ------------------------------------------------------------------ lecture d'une région (processus de travail)
def lire_region(args):
    """Pour une région : hauteur et nom du bloc du dessus, fond sous l'eau, sol naturel, biome du dessus (512 x 512)."""
    chemin, ignores = args
    surf_y = np.full((512, 512), -32768, dtype=np.int16)
    surf_id = np.zeros((512, 512), dtype=np.int32)
    fond_y = np.full((512, 512), -32768, dtype=np.int16)
    sol_y = np.full((512, 512), -32768, dtype=np.int16)
    biome = np.zeros((512, 512), dtype=np.int32)
    present = np.zeros((32, 32), dtype=bool)
    noms, index_noms = [], {}
    biomes, index_biomes = [], {}
    cats = {}
    lus = erreurs = 0

    def id_nom(n):
        i = index_noms.get(n)
        if i is None:
            i = index_noms[n] = len(noms)
            noms.append(n)
        return i

    def id_biome(n):
        i = index_biomes.get(n)
        if i is None:
            i = index_biomes[n] = len(biomes)
            biomes.append(n)
        return i

    for k, data in chunks_region(chemin):
        try:
            ch = lire_chunk(data)
        except Exception:
            erreurs += 1
            continue
        if ch is None:
            continue
        statut = ch.get("Status") or ""
        if statut and not statut.endswith("full") and not statut.endswith("spawn") and not statut.endswith("heightmaps"):
            continue
        cx, cz = k % 32, k // 32
        secs = sorted((s for s in ch["sections"] if s["pal"]), key=lambda s: -s["Y"])
        if not secs:
            continue
        lus += 1
        trouve = [np.zeros(256, dtype=bool) for _ in range(3)]
        hy = [np.full(256, -32768, dtype=np.int32) for _ in range(3)]
        sid = np.zeros(256, dtype=np.int32)
        sbio = np.zeros(256, dtype=np.int32)
        seuils = (EAU, VEGETAL, SOL)
        for s in secs:
            pal = s["pal"]
            pcat = np.array([cats.setdefault(n, categorie(n, ignores)) for n in pal], dtype=np.int8)
            if pcat.max() == TRANSPARENT:
                continue
            idx = indices(s["data"], len(pal), 4096, 4).reshape(16, 256)  # [y][z*16 + x]
            cat = pcat[idx]
            base_y = s["Y"] * 16
            for h in range(3):
                if trouve[h].all():
                    continue
                m = cat >= seuils[h]
                a = m.any(axis=0) & ~trouve[h]
                if not a.any():
                    continue
                haut = 15 - np.argmax(m[::-1], axis=0)
                cols = np.nonzero(a)[0]
                hy[h][cols] = base_y + haut[cols]
                trouve[h][cols] = True
                if h == 0:
                    gid = np.array([id_nom(n) for n in pal], dtype=np.int32)
                    sid[cols] = gid[idx[haut[cols], cols]]
                    if s["bpal"]:
                        bidx = indices(s["bdata"], len(s["bpal"]), 64, 0).reshape(4, 4, 4)  # [y][z][x]
                        bg = np.array([id_biome(n) for n in s["bpal"]], dtype=np.int32)
                        zz, xx = cols // 16, cols % 16
                        sbio[cols] = bg[bidx[haut[cols] // 4, zz // 4, xx // 4]]
            if trouve[2].all():
                break
        # pas de sol naturel trouvé, ou très loin sous le fond (bunker, sous-sol) : le fond sert de sol
        sans = ~trouve[2] | (hy[2] < hy[1] - 48)
        hy[2][sans] = hy[1][sans]
        x0, z0 = cx * 16, cz * 16
        surf_y[z0:z0 + 16, x0:x0 + 16] = np.clip(hy[0], -32768, 32767).reshape(16, 16)
        fond_y[z0:z0 + 16, x0:x0 + 16] = np.clip(hy[1], -32768, 32767).reshape(16, 16)
        sol_y[z0:z0 + 16, x0:x0 + 16] = np.clip(hy[2], -32768, 32767).reshape(16, 16)
        surf_id[z0:z0 + 16, x0:x0 + 16] = sid.reshape(16, 16)
        biome[z0:z0 + 16, x0:x0 + 16] = sbio.reshape(16, 16)
        present[cz, cx] = trouve[0].any()
    m = re.match(r"r\.(-?\d+)\.(-?\d+)\.mca$", Path(chemin).name)
    return (int(m.group(1)), int(m.group(2)), surf_y, surf_id, fond_y, sol_y, biome, present, noms, biomes, lus, erreurs)


# ------------------------------------------------------------------ couleurs des blocs
class Textures:
    """Couleur moyenne des textures de blocs, cherchées dans des dossiers (pack, ressources) ou des jars."""

    def __init__(self, sources):
        self.index = {}  # (ns, chemin sans .png) -> (source, membre)
        self.cache = {}
        for src in sources:
            p = Path(src)
            if p.is_dir():
                for f in p.glob("**/assets/*/textures/block/**/*.png"):
                    rel = f.relative_to(p).as_posix()
                    m = re.search(r"assets/([^/]+)/textures/block/(.+)\.png$", rel)
                    if m:
                        self.index.setdefault((m.group(1), m.group(2)), (p, rel))
            elif p.suffix in (".jar", ".zip") and p.is_file():
                try:
                    with zipfile.ZipFile(p) as z:
                        for n in z.namelist():
                            m = re.match(r"assets/([^/]+)/textures/block/(.+)\.png$", n)
                            if m:
                                self.index.setdefault((m.group(1), m.group(2)), (p, n))
                except zipfile.BadZipFile:
                    pass

    def couleur(self, ns, chemin):
        cle = (ns, chemin)
        if cle in self.cache:
            return self.cache[cle]
        c = None
        ou = self.index.get(cle)
        if ou is not None:
            src, membre = ou
            try:
                if src.is_dir():
                    img = Image.open(src / membre)
                else:
                    with zipfile.ZipFile(src) as z:
                        img = Image.open(io.BytesIO(z.read(membre)))
                img = img.convert("RGBA")
                w, h = img.size
                if h > w:
                    img = img.crop((0, 0, w, w))  # texture animée : première image
                a = np.asarray(img, dtype=np.float32)
                al = a[..., 3] / 255.0
                if al.sum() > 0.5:
                    c = tuple(float((a[..., i] * al).sum() / al.sum()) for i in range(3))
            except Exception:
                c = None
        self.cache[cle] = c
        return c


def candidats_texture(chemin):
    """Noms de texture possibles pour un bloc (le dessus d'abord)."""
    out = [chemin + "_top", chemin + "_dessus", chemin + "_haut", chemin, chemin + "_0"]
    c = re.sub(r"^(waxed_|infested_|potted_)", "", chemin)
    if c != chemin:
        out += [c + "_top", c]
    if c.endswith("_block"):
        out += [c[:-6] + "_top", c[:-6]]
    for sfx in ("_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button", "_carpet", "_pane",
                "_escalier", "_dalle", "_muret", "_barriere"):
        if c.endswith(sfx):
            b = c[:-len(sfx)]
            out += [b + "_top", b, b + "s", b + "_planks", b + "_block_top", b + "_block", b + "_bricks"]
            if b.endswith("_brick"):
                out.append(b + "s")
            break
    for sfx, rep in (("_wood", "_log"), ("_hyphae", "_stem")):
        if c.endswith(sfx):
            out += [c[:-len(sfx)] + rep]
    if c.endswith("_door"):
        out.append(c + "_top")
    if c == "farmland":
        out.append("farmland_moist")
    if c == "lava":
        out.append("lava_still")
    if c.endswith("_glass_pane"):
        out.append(c[:-5])
    return out


# teintes fixes (couleurs de Minecraft, sans pack) : herbe, feuillage et eau par biome
HERBE = {"plains": (145, 189, 89), "sunflower_plains": (145, 189, 89), "forest": (121, 192, 90),
         "flower_forest": (121, 192, 90), "birch_forest": (136, 187, 103), "old_growth_birch_forest": (136, 187, 103),
         "dark_forest": (80, 122, 50), "taiga": (134, 183, 131), "old_growth_pine_taiga": (134, 184, 127),
         "old_growth_spruce_taiga": (134, 184, 127), "snowy_taiga": (128, 180, 151), "snowy_plains": (128, 180, 151),
         "ice_spikes": (128, 180, 151), "snowy_beach": (131, 181, 147), "desert": (191, 183, 85), "savanna": (191, 183, 85),
         "savanna_plateau": (191, 183, 85), "windswept_savanna": (191, 183, 85), "badlands": (144, 129, 77),
         "eroded_badlands": (144, 129, 77), "wooded_badlands": (144, 129, 77), "swamp": (106, 112, 57),
         "mangrove_swamp": (106, 112, 57), "jungle": (89, 201, 60), "sparse_jungle": (100, 199, 63),
         "bamboo_jungle": (89, 201, 60), "meadow": (131, 187, 109), "cherry_grove": (182, 219, 97),
         "windswept_hills": (138, 182, 137), "windswept_forest": (138, 182, 137), "stony_shore": (138, 182, 137),
         "grove": (128, 180, 151), "snowy_slopes": (128, 180, 151), "frozen_peaks": (128, 180, 151),
         "jagged_peaks": (128, 180, 151), "stony_peaks": (154, 190, 75), "beach": (145, 189, 89),
         "river": (142, 185, 113), "frozen_river": (128, 180, 151), "ocean": (142, 185, 113)}
FEUILLAGE = {"plains": (119, 171, 47), "forest": (89, 174, 48), "birch_forest": (107, 169, 65),
             "dark_forest": (89, 174, 48), "taiga": (104, 164, 100), "snowy_taiga": (96, 161, 123),
             "snowy_plains": (96, 161, 123), "desert": (174, 164, 42), "savanna": (174, 164, 42),
             "badlands": (158, 129, 77), "swamp": (106, 112, 57), "mangrove_swamp": (141, 177, 39),
             "jungle": (48, 187, 11), "meadow": (99, 169, 72), "windswept_hills": (109, 163, 107),
             "river": (113, 167, 77), "ocean": (113, 167, 77), "beach": (119, 171, 47)}
EAU_BIOME = {"swamp": (97, 123, 100), "mangrove_swamp": (58, 122, 106), "warm_ocean": (67, 213, 238),
             "lukewarm_ocean": (69, 173, 242), "deep_lukewarm_ocean": (69, 173, 242), "cold_ocean": (61, 87, 214),
             "deep_cold_ocean": (61, 87, 214), "frozen_ocean": (57, 56, 201), "deep_frozen_ocean": (57, 56, 201),
             "frozen_river": (57, 56, 201), "snowy_plains": (61, 87, 214)}
EAU_DEFAUT, HERBE_DEFAUT, FEUILLAGE_DEFAUT = (63, 118, 228), (145, 189, 89), (119, 171, 47)
T_AUCUNE, T_HERBE, T_FEUILLAGE, T_EAU = 0, 1, 2, 3

# couleurs de secours (sans texture) : premier motif qui correspond au nom du bloc
SECOURS = [
    (r"water|bubble_column", (63, 118, 228)), (r"lava", (207, 92, 20)), (r"grass_block|^grass$|herbe", (100, 140, 60)),
    (r"leaves|feuill", (60, 105, 40)), (r"_log|_wood|tronc|stem|hyphae", (102, 81, 51)), (r"planks|plancher|bois", (157, 128, 79)),
    (r"snow|neige|powder", (240, 244, 246)), (r"ice|glace", (160, 190, 240)), (r"red_sand", (190, 102, 33)),
    (r"sandstone|sable|sand|dune", (219, 207, 163)), (r"gravel|gravier|cailloux", (130, 124, 122)),
    (r"dirt_path|chemin", (148, 122, 65)), (r"dirt|terre|mud|boue|podzol|farmland", (134, 96, 67)),
    (r"clay|argile", (160, 166, 179)), (r"deepslate", (80, 80, 82)), (r"blackstone|basalt|obsidian", (42, 36, 41)),
    (r"brick|brique", (150, 97, 83)), (r"terracotta|terre_cuite", (152, 94, 67)), (r"copper|cuivre", (192, 107, 79)),
    (r"quartz|calcite|enduit_blanc", (235, 229, 222)), (r"glass|verre|vitre", (200, 220, 230)),
    (r"iron|fer|metal|tole|acier|steel", (167, 167, 167)), (r"wool|laine|carpet|tapis", (220, 220, 220)),
    (r"moss|mousse", (89, 109, 45)), (r"asphalt|asphalte|goudron|bitume|route|road", (58, 58, 62)),
    (r"concrete|beton", (125, 125, 125)), (r"stone|pierre|roche|cobble|andesite|diorite|granite|tuff", (125, 125, 125)),
    (r"hay|foin|paille", (166, 136, 38)), (r"wheat|carrots|potatoes|beetroots|crop|culture", (110, 150, 50)),
]
COULEURS_MOTS = {"white": (233, 236, 236), "orange": (240, 118, 19), "magenta": (189, 68, 179), "light_blue": (58, 175, 217),
                 "yellow": (248, 197, 39), "lime": (112, 185, 25), "pink": (237, 141, 172), "light_gray": (142, 142, 134),
                 "gray": (62, 68, 71), "cyan": (21, 137, 145), "purple": (121, 42, 172), "blue": (53, 57, 157),
                 "brown": (114, 71, 40), "green": (84, 109, 27), "red": (161, 39, 34), "black": (20, 21, 25)}


def teinte_bloc(nom):
    ns, _, c = nom.partition(":")
    if c in ("grass_block", "grass", "tall_grass", "fern", "large_fern", "sugar_cane"):
        return T_HERBE
    if c in ("oak_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves", "mangrove_leaves", "vine"):
        return T_FEUILLAGE
    if nom in ("minecraft:water", "minecraft:bubble_column"):
        return T_EAU
    return T_AUCUNE


def couleur_bloc(nom, textures):
    """(couleur RGB de base, d'où elle vient) ; pour un bloc teinté, la texture grise sera multipliée par la teinte."""
    ns, _, chemin = nom.partition(":")
    fixes = {"minecraft:spruce_leaves": (97, 153, 97), "minecraft:birch_leaves": (128, 167, 85),
             "minecraft:lily_pad": (32, 128, 48), "minecraft:water": (255, 255, 255), "minecraft:bubble_column": (255, 255, 255)}
    if nom in fixes:
        return fixes[nom], "fixe"
    if textures is not None:
        for cand in candidats_texture(chemin):
            c = textures.couleur(ns, cand)
            if c is not None:
                return c, "texture"
            if ns != "minecraft":
                c = textures.couleur("minecraft", cand)
                if c is not None:
                    return c, "texture"
    for mot, rgb in COULEURS_MOTS.items():
        if re.search(r"(^|_)" + mot + r"_", chemin) and not (mot == "gray" and "light_gray" in chemin):
            return rgb, "couleur du nom"
    for motif, rgb in SECOURS:
        if re.search(motif, chemin):
            return rgb, "secours"
    return (128, 128, 128), "inconnu"


# ------------------------------------------------------------------ rendu
def ombrage(h, pas, exag=1.0, az=315.0, alt=45.0):
    """Éclairage du relief (soleil au nord-ouest) : -1..1 (1 = face au soleil). pas = blocs par pixel."""
    gz, gx = np.gradient(h.astype(np.float32) * exag, pas)
    nx, ny, nz = -gx, np.ones_like(gx), -gz
    n = np.sqrt(nx * nx + ny * ny + nz * nz)
    a, e = math.radians(az), math.radians(alt)
    lx, ly, lz = -math.cos(e) * math.sin(a), math.sin(e), -math.cos(e) * math.cos(a)
    return (nx * lx + ny * ly + nz * lz) / n


def reduire(arr, k, mode="moyenne"):
    """Réduit un tableau 2D (ou 2D x 3) d'un facteur entier k (moyenne, max ou min par bloc de k x k)."""
    if k == 1:
        return arr
    h, w = arr.shape[0] // k, arr.shape[1] // k
    a = arr[:h * k, :w * k]
    a = a.reshape(h, k, w, k, *arr.shape[2:])
    if mode == "max":
        return a.max(axis=(1, 3))
    if mode == "min":
        return a.min(axis=(1, 3))
    return a.mean(axis=(1, 3), dtype=np.float32)


def flou(a, r=1):
    """Flou léger (moyenne 3 x 3 répétée r fois), bords répétés."""
    a = a.astype(np.float32)
    for _ in range(r):
        p = np.pad(a, 1, mode="edge")
        a = (p[:-2, :-2] + p[:-2, 1:-1] + p[:-2, 2:] + p[1:-1, :-2] + p[1:-1, 1:-1] + p[1:-1, 2:] + p[2:, :-2] + p[2:, 1:-1]
             + p[2:, 2:]) / 9.0
    return a


def melange(a, b, t):
    t = np.clip(t, 0, 1)[..., None]
    return a * (1 - t) + b * t


def rendu_satellite(surf_y, surf_id, fond_y, biome, present, couleurs, teintes, biomes_noms, k, mer):
    """Vue satellite, calculée bloc par bloc puis réduite (moyenne) à k blocs par pixel."""
    base = couleurs[surf_id]                               # (H, W, 3) 0..255
    t = teintes[surf_id]
    nb = len(biomes_noms)
    herbe = np.array([HERBE.get(b.partition(":")[2], HERBE_DEFAUT) for b in biomes_noms] or [HERBE_DEFAUT], np.float32)
    feuil = np.array([FEUILLAGE.get(b.partition(":")[2], FEUILLAGE_DEFAUT) for b in biomes_noms] or [FEUILLAGE_DEFAUT], np.float32)
    eaub = np.array([EAU_BIOME.get(b.partition(":")[2], EAU_DEFAUT) for b in biomes_noms] or [EAU_DEFAUT], np.float32)
    bi = np.clip(biome, 0, max(0, nb - 1))
    col = base.copy()
    m = t == T_HERBE
    col[m] = base[m] * herbe[bi[m]] / 255.0
    m = t == T_FEUILLAGE
    col[m] = base[m] * feuil[bi[m]] / 255.0
    eau = t == T_EAU
    h = np.where(present_pixels(present, surf_y.shape), surf_y, mer).astype(np.float32)
    sh = ombrage(h, 1.0, exag=1.0)
    lum = np.clip(0.80 + 0.42 * sh, 0.52, 1.14)
    col = col * lum[..., None]
    # eau : claire au bord, sombre au large ; reflets du relief coupés
    prof = np.clip((surf_y - fond_y).astype(np.float32), 0, 40)
    eau_c = eaub[bi] * 0.55
    sombre = np.array([18, 44, 82], np.float32)
    eau_c = melange(eau_c + 30, sombre, (prof / 22.0) ** 0.6)
    col = np.where(eau[..., None], eau_c, col)
    vide = ~present_pixels(present, surf_y.shape)
    col[vide] = VIDE
    return np.clip(reduire(col, k), 0, 255).astype(np.uint8)


def present_pixels(present, forme):
    """Masque des colonnes lues (chunks présents), à la taille des tableaux par bloc (déjà par bloc : rendu tel quel)."""
    if present.shape == tuple(forme[:2]):
        return present
    return np.repeat(np.repeat(present, 16, axis=0), 16, axis=1)[:forme[0], :forme[1]]


def rendu_relief(surf_y, sol_y, classes_px, present, k, mer):
    """Vue topographique à k blocs par pixel : teintes par altitude, ombrage, courbes, eau, forêts, bâtiments, routes."""
    pres = present_pixels(present, surf_y.shape)
    sol = np.where(pres, sol_y, mer).astype(np.float32)
    h = flou(reduire(sol, k), 2)
    cls = classes_px

    # un bloc construit au ras du sol (2 blocs au plus au-dessus du terrain) est une rue, une place ou un trottoir
    ras = (cls == C_BATIMENT) & ((surf_y.astype(np.int32) - sol_y) <= 2)

    def part(c):
        if c == C_BATIMENT:
            return reduire(((cls == c) & ~ras).astype(np.float32), k)
        return reduire((cls == c).astype(np.float32), k)

    eau = part(C_EAU) > 0.5
    terre = h[~eau] if (~eau).any() else h.reshape(-1)
    haut = max(float(np.percentile(terre, 99.5)), mer + 20.0)
    # teintes : plaine verte et pâle, collines beiges, sommets bruns puis gris clair
    pal = [(0.00, (196, 216, 170)), (0.12, (216, 228, 180)), (0.30, (236, 232, 192)), (0.50, (230, 214, 168)),
           (0.70, (214, 190, 148)), (0.88, (196, 172, 140)), (1.00, (226, 222, 214))]
    r = np.clip((h - mer) / (haut - mer), 0, 1)
    col = np.zeros(h.shape + (3,), np.float32)
    for (r0, c0), (r1, c1) in zip(pal, pal[1:]):
        t = np.clip((r - r0) / (r1 - r0), 0, 1)
        sel = (r >= r0) & (r <= r1)
        col[sel] = (np.array(c0, np.float32) * (1 - t[sel, None]) + np.array(c1, np.float32) * t[sel, None])
    sh = ombrage(h, float(k), exag=1.6)
    col = col * np.clip(0.90 + 0.18 * sh, 0.74, 1.07)[..., None]
    col = melange(col, np.array([150, 196, 132], np.float32), part(C_ARBRE) * 0.62)
    col = melange(col, np.array([244, 238, 196], np.float32), part(C_CHAMP) * 0.8)
    col = melange(col, np.array([246, 248, 250], np.float32), part(C_NEIGE) * 0.75)
    # courbes de niveau tous les 5 blocs (fines) et 25 blocs (marquées) : là où l'étage change d'un pixel à son voisin
    for pas, force in ((5.0, 0.22), (25.0, 0.5)):
        niv = np.floor((h - mer) / pas)
        trait = ((niv != np.roll(niv, 1, 0)) | (niv != np.roll(niv, 1, 1))).astype(np.float32)
        trait[0, :] = 0
        trait[:, 0] = 0
        col = melange(col, np.array([140, 104, 70], np.float32), np.where(eau, 0, trait * force))
    bat = part(C_BATIMENT)
    route = part(C_ROUTE)
    rue = reduire(ras.astype(np.float32), k)
    # rues et places (blanc cassé), puis routes et chemins (jaune), chacun avec un liseré sombre
    for voie, teinte in ((rue, (242, 240, 232)), (route, (250, 214, 120))):
        if voie.any():
            bord = np.maximum.reduce([np.roll(voie, s, a) for s in (-1, 1) for a in (0, 1)])
            col = melange(col, np.array([118, 102, 84], np.float32), np.clip(bord - voie, 0, 1) * 0.6)
            col = melange(col, np.array(teinte, np.float32), np.clip(voie * 1.8, 0, 1))
    col = melange(col, np.array([206, 204, 198], np.float32), np.clip(bat * 3 - 0.2, 0, 1) * 0.35)
    col = melange(col, np.array([88, 88, 94], np.float32), np.clip(bat * 1.6 - 0.15, 0, 1))
    # eau : bleu clair, plus soutenu au large, rive plus sombre
    prof = reduire(np.clip((surf_y - sol_y).astype(np.float32), 0, 40), k)
    eau_c = melange(np.zeros_like(col) + np.array([170, 210, 230], np.float32), np.zeros_like(col) + np.array([120, 172, 214], np.float32),
                    prof / 24.0)
    col = np.where(eau[..., None], eau_c, col)
    rive = eau & ~(np.roll(eau, 1, 0) & np.roll(eau, -1, 0) & np.roll(eau, 1, 1) & np.roll(eau, -1, 1))
    col[rive] = (92, 142, 190)
    vide = reduire(pres.astype(np.float32), k) < 0.5
    col[vide] = VIDE
    return np.clip(col, 0, 255).astype(np.uint8)


def ecrire_tuiles(img, prefixe, sortie):
    """Coupe l'image en tuiles PNG de TUILE px au plus (palette de 256 couleurs tramée : fichiers légers)."""
    h, w = img.shape[:2]
    noms = []
    total = 0
    for j in range(0, h, TUILE):
        for i in range(0, w, TUILE):
            t = Image.fromarray(img[j:j + TUILE, i:i + TUILE])
            q = t.quantize(colors=256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.FLOYDSTEINBERG)
            f = sortie / ("%s_%d_%d.png" % (prefixe, i // TUILE, j // TUILE))
            q.save(f, optimize=True)
            total += f.stat().st_size
            noms.append(f.name)
    return noms, total


# ------------------------------------------------------------------ bordure du monde
def bordure_level_dat(monde):
    """(x1, z1, x2, z2) de la bordure du monde lue dans <monde>/level.dat (gzip, NBT), ou None (absent, illisible, ou
    bordure par défaut de Minecraft, immense)."""
    import gzip
    f = Path(monde) / "level.dat"
    if Path(monde).name == "region":
        f = Path(monde).parent / "level.dat"
    if not f.is_file():
        return None
    try:
        d = gzip.decompress(f.read_bytes())
    except Exception:
        return None

    def double(nom):
        cle = bytes([6]) + struct.pack(">H", len(nom)) + nom.encode()
        i = d.find(cle)
        if i < 0:
            return None
        return struct.unpack_from(">d", d, i + len(cle))[0]

    cx, cz, taille = double("BorderCenterX"), double("BorderCenterZ"), double("BorderSize")
    if cx is None or cz is None or taille is None or not (16 <= taille <= 200000):
        return None
    r = taille / 2.0
    return (int(math.floor(cx - r)), int(math.floor(cz - r)), int(math.ceil(cx + r)), int(math.ceil(cz + r)))


def bordure_voulue(o):
    """Rectangle de recadrage (x1, z1, x2, z2) ou None, d'après --bordure."""
    b = o["bordure"]
    if b == "aucune":
        return None
    if b in (None, "monde"):
        if o["dimension"] not in ("minecraft:overworld", "overworld", ""):
            if b == "monde":
                print("[carte] --bordure monde : seulement pour l'overworld (level.dat), pas de recadrage")
            return None
        r = bordure_level_dat(o["monde"])
        if r is None:
            print("[carte] bordure du monde introuvable (level.dat absent, illisible ou bordure par défaut) : pas de recadrage")
        else:
            print("[carte] bordure du monde (level.dat) : x %d à %d, z %d à %d" % (r[0], r[2], r[1], r[3]))
        return r
    x1, z1, x2, z2 = b
    return (min(x1, x2), min(z1, z2), max(x1, x2), max(z1, z2))


# ------------------------------------------------------------------ programme
def regions_de(monde, dimension):
    m = Path(monde)
    if m.name == "region" and m.is_dir():
        return m
    if dimension in ("minecraft:overworld", "overworld", ""):
        return m / "region"
    if dimension in ("minecraft:the_nether", "nether"):
        return m / "DIM-1" / "region"
    if dimension in ("minecraft:the_end", "the_end"):
        return m / "DIM1" / "region"
    ns, _, nom = dimension.partition(":")
    return m / "dimensions" / ns / nom / "region"


def sources_defaut():
    """Textures : le pack imposé par heraliz_interface d'abord (ce que voient les joueurs), puis nos mods et les jars."""
    out = [DEPOT / "modules/interface/src/main/resources/resourcepacks/textures"]
    out += sorted(p for p in DEPOT.glob("modules/*/src/*/resources") if p.is_dir())
    out += sorted(DEPOT.glob("serveur/mods-tiers/*.jar")) + sorted(DEPOT.glob("client/mods-tiers/*.jar"))
    return [p for p in out if p.exists()]


def options(argv):
    o = {"sortie": None, "installer": False, "dimension": "minecraft:overworld", "pixels": 2048, "echelle": None,
         "ignorer": "crac", "textures": None, "mer": 63, "processus": os.cpu_count() or 2, "stats": False,
         "bordure": None}
    libres = []
    i = 0
    while i < len(argv):
        a = argv[i]
        if a == "--sortie":
            o["sortie"] = argv[i + 1]; i += 2
        elif a == "--installer":
            o["installer"] = True; i += 1
        elif a == "--dimension":
            o["dimension"] = argv[i + 1]; i += 2
        elif a == "--pixels":
            o["pixels"] = int(argv[i + 1]); i += 2
        elif a == "--echelle":
            o["echelle"] = int(argv[i + 1]); i += 2
        elif a == "--ignorer":
            o["ignorer"] = argv[i + 1]; i += 2
        elif a == "--niveau-mer":
            o["mer"] = int(argv[i + 1]); i += 2
        elif a == "--processus":
            o["processus"] = max(1, int(argv[i + 1])); i += 2
        elif a == "--stats":
            o["stats"] = True; i += 1
        elif a == "--bordure":
            if i + 1 < len(argv) and argv[i + 1] in ("monde", "aucune"):
                o["bordure"] = argv[i + 1]; i += 2
            else:
                try:
                    o["bordure"] = tuple(int(v) for v in argv[i + 1:i + 5])
                except ValueError:
                    raise SystemExit("--bordure : quatre nombres entiers (x1 z1 x2 z2), ou « monde », ou « aucune »")
                if len(o["bordure"]) != 4:
                    raise SystemExit("--bordure : quatre nombres entiers (x1 z1 x2 z2), ou « monde », ou « aucune »")
                i += 5
        elif a == "--textures":
            o["textures"] = []
            i += 1
            while i < len(argv) and not argv[i].startswith("--"):
                o["textures"].append(argv[i]); i += 1
        elif a.startswith("--"):
            raise SystemExit("option inconnue : %s\n\n%s" % (a, __doc__))
        else:
            libres.append(a); i += 1
    if len(libres) != 1:
        raise SystemExit(__doc__)
    o["monde"] = libres[0]
    return o


def main():
    o = options(sys.argv[1:])
    t0 = time.time()
    reg = regions_de(o["monde"], o["dimension"])
    fichiers = sorted(p for p in reg.glob("r.*.mca") if re.match(r"r\.-?\d+\.-?\d+\.mca$", p.name))
    if not fichiers:
        raise SystemExit("[carte] aucun fichier de région dans %s" % reg)
    sortie = DOSSIER_MOD if o["installer"] else Path(o["sortie"] or DEPOT / "livraison/carte")
    monde_abs = Path(o["monde"]).resolve()
    if monde_abs in sortie.resolve().parents or sortie.resolve() == monde_abs:
        raise SystemExit("[carte] la sortie ne peut pas être dans le dossier du monde (lecture seule)")
    ignores = {s.strip() for s in o["ignorer"].split(",") if s.strip()}
    print("[carte] %d régions dans %s (blocs ignorés : %s)" % (len(fichiers), reg, ", ".join(sorted(ignores)) or "aucun"))
    rx = [int(re.match(r"r\.(-?\d+)\.", p.name).group(1)) for p in fichiers]
    rz = [int(re.match(r"r\.-?\d+\.(-?\d+)\.", p.name).group(1)) for p in fichiers]
    rx0, rz0 = min(rx), min(rz)
    W, H = (max(rx) - rx0 + 1) * 512, (max(rz) - rz0 + 1) * 512
    surf_y = np.full((H, W), o["mer"], dtype=np.int16)
    fond_y = np.full((H, W), o["mer"], dtype=np.int16)
    sol_y = np.full((H, W), o["mer"], dtype=np.int16)
    surf_id = np.zeros((H, W), dtype=np.int32)
    biome = np.zeros((H, W), dtype=np.int32)
    present = np.zeros((H // 16, W // 16), dtype=bool)
    noms, index_noms, biomes, index_biomes = [], {}, [], {}
    lus = erreurs = 0
    travaux = [(str(f), ignores) for f in fichiers]
    with Pool(o["processus"]) as pool:
        for n, res in enumerate(pool.imap_unordered(lire_region, travaux), 1):
            x, z, sy, sid, fy, so, bio, pres, rnoms, rbiomes, l, e = res
            lus += l
            erreurs += e
            gid = np.array([index_noms.setdefault(nm, len(index_noms)) for nm in rnoms] or [0], dtype=np.int32)
            gb = np.array([index_biomes.setdefault(b, len(index_biomes)) for b in rbiomes] or [0], dtype=np.int32)
            ox, oz = (x - rx0) * 512, (z - rz0) * 512
            m = np.repeat(np.repeat(pres, 16, axis=0), 16, axis=1)
            sl = (slice(oz, oz + 512), slice(ox, ox + 512))
            surf_y[sl] = np.where(m, sy, surf_y[sl])
            fond_y[sl] = np.where(m, fy, fond_y[sl])
            sol_y[sl] = np.where(m, so, sol_y[sl])
            surf_id[sl] = np.where(m, gid[np.clip(sid, 0, len(gid) - 1)], surf_id[sl])
            biome[sl] = np.where(m, gb[np.clip(bio, 0, len(gb) - 1)], biome[sl])
            present[oz // 16:oz // 16 + 32, ox // 16:ox // 16 + 32] = pres
            if n % 8 == 0 or n == len(travaux):
                print("[carte] %d/%d régions lues (%d chunks, %.0f s)" % (n, len(travaux), lus, time.time() - t0))
    if not present.any():
        raise SystemExit("[carte] aucun chunk lisible : rien à dessiner")
    noms = sorted(index_noms, key=index_noms.get)
    biomes = sorted(index_biomes, key=index_biomes.get)
    # emprise : les chunks présents (bords de la région vide retirés), recadrée sur la bordure, alignée sur l'échelle.
    # Tout est en blocs, relatif au coin des tableaux (rx0 * 512, rz0 * 512) : un pixel = k x k blocs, le même pas en X
    # et en Z (jamais d'image étirée).
    zs, xs = np.nonzero(present)
    bx, bz = int(xs.min()) * 16, int(zs.min()) * 16
    ex, ez = (int(xs.max()) + 1) * 16, (int(zs.max()) + 1) * 16
    bordure = bordure_voulue(o)
    if bordure is not None:
        ox_, oz_ = rx0 * 512, rz0 * 512
        bx, bz = max(bx, bordure[0] - ox_), max(bz, bordure[1] - oz_)
        ex, ez = min(ex, bordure[2] - ox_), min(ez, bordure[3] - oz_)
        if ex <= bx or ez <= bz:
            raise SystemExit("[carte] la bordure %s ne contient aucun chunk lu" % (bordure,))
    largeur, hauteur = ex - bx, ez - bz
    k = o["echelle"] or 1
    if not o["echelle"]:
        while max(largeur, hauteur) / k > o["pixels"]:
            k *= 2
    # agrandit l'emprise d'un multiple de k (tableaux réduits sans reste)
    largeur, hauteur = -(-largeur // k) * k, -(-hauteur // k) * k
    pad_x = max(0, bx + largeur - W)
    pad_z = max(0, bz + hauteur - H)

    def coupe(a, remplir):
        if pad_x or pad_z:
            a = np.pad(a, ((0, pad_z), (0, pad_x)), constant_values=remplir)
        return a[bz:bz + hauteur, bx:bx + largeur]

    surf_y, fond_y, sol_y = coupe(surf_y, o["mer"]), coupe(fond_y, o["mer"]), coupe(sol_y, o["mer"])
    surf_id, biome = coupe(surf_id, 0), coupe(biome, 0)
    # chunks présents, bloc par bloc puis ramenés à 16 (le recadrage peut couper un chunk : on garde le masque par bloc)
    pres_blocs = np.repeat(np.repeat(present, 16, axis=0), 16, axis=1)
    pres_blocs = np.pad(pres_blocs, ((0, pad_z), (0, pad_x)), constant_values=False)[bz:bz + hauteur, bx:bx + largeur]
    x0, z0 = (rx0 * 512) + bx, (rz0 * 512) + bz
    print("[carte] emprise : x %d à %d, z %d à %d (%d x %d blocs), %d bloc(s) par pixel"
          % (x0, x0 + largeur, z0, z0 + hauteur, largeur, hauteur, k))
    # couleurs et classes de chaque bloc rencontré au-dessus
    tex = Textures(o["textures"] if o["textures"] is not None else sources_defaut())
    couleurs = np.zeros((max(1, len(noms)), 3), np.float32)
    teintes = np.zeros(max(1, len(noms)), np.int8)
    classes = np.zeros(max(1, len(noms)), np.int8)
    origines = {}
    for i, nm in enumerate(noms):
        c, src = couleur_bloc(nm, tex)
        couleurs[i] = c
        teintes[i] = teinte_bloc(nm)
        classes[i] = classe(nm)
        origines[nm] = src
    classes_px = classes[surf_id]
    if o["stats"]:
        compte = np.bincount(surf_id[pres_blocs].reshape(-1), minlength=len(noms))
        print("\n[carte] blocs du dessus les plus fréquents :")
        for i in np.argsort(-compte)[:80]:
            if compte[i] == 0:
                break
            print("  %8d  %-48s %-9s %-15s %s" % (compte[i], noms[i], NOMS_CLASSES[classes[i]], origines[noms[i]],
                                                  "#%02X%02X%02X" % tuple(int(v) for v in couleurs[i])))
        print()
    sat = rendu_satellite(surf_y, surf_id, fond_y, biome, pres_blocs, couleurs, teintes, biomes, k, o["mer"])
    rel = rendu_relief(surf_y, sol_y, classes_px, pres_blocs, k, o["mer"])
    sortie.mkdir(parents=True, exist_ok=True)
    for f in list(sortie.glob("satellite_*.png")) + list(sortie.glob("relief_*.png")):
        f.unlink()
    tuiles_sat, poids_sat = ecrire_tuiles(sat, "satellite", sortie)
    tuiles_rel, poids_rel = ecrire_tuiles(rel, "relief", sortie)
    h, w = sat.shape[:2]
    info = {
        "version": 1,
        "dimension": o["dimension"] if ":" in o["dimension"] else "minecraft:" + o["dimension"],
        "x0": int(x0), "z0": int(z0), "largeur": int(largeur), "hauteur": int(hauteur),
        "blocsParPixel": int(k), "pixelsLargeur": int(w), "pixelsHauteur": int(h), "tuile": TUILE,
        "colonnes": int(-(-w // TUILE)), "lignes": int(-(-h // TUILE)),
        "vues": ["satellite", "relief"], "niveauMer": int(o["mer"]),
        "bordure": [int(v) for v in bordure] if bordure is not None else None,
        "fait": time.strftime("%Y-%m-%d"), "chunks": int(lus),
    }
    (sortie / "carte.json").write_text(json.dumps(info, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    apercu = Image.fromarray(sat).resize((max(1, w // 4), max(1, h // 4)), Image.BOX)
    if not o["installer"]:
        apercu.save(sortie / "apercu_satellite.png", optimize=True)
        Image.fromarray(rel).resize((max(1, w // 4), max(1, h // 4)), Image.BOX).save(sortie / "apercu_relief.png", optimize=True)
    print("[carte] %d chunks lus (%d illisibles), %d blocs différents au-dessus, %d biomes" % (lus, erreurs, len(noms), len(biomes)))
    print("[carte] satellite : %d tuile(s), %.1f Mo ; relief : %d tuile(s), %.1f Mo -> %s"
          % (len(tuiles_sat), poids_sat / 1e6, len(tuiles_rel), poids_rel / 1e6, sortie))
    print("[carte] fini en %.0f s" % (time.time() - t0))


if __name__ == "__main__":
    main()
