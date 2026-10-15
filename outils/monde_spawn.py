"""Monde du spawn (dimension heralisz:spawn, déclarée par HeraliZ Utils) : fabrique ses fichiers de régions.

Le monde du spawn est une dimension à part du monde du serveur : du vide tout autour, rien qui se génère, pas de
monstres. Il est déposé UNE fois dans dimensions/heralisz/spawn/ du monde du Ptero (workflow « Serveur HeraliZ »,
case « deposer_spawn ») ; la carte (dayz_map) n'est jamais touchée.

    python outils/monde_spawn.py grotte [--sortie DOSSIER] [--rendus DOSSIER]
    python outils/monde_spawn.py copier <monde> --zone X0 Y0 Z0 X1 Y1 Z1 [--dimension ID] [--decalage DX DY DZ]
                                        [--reveil X Y Z] [--cap DEGRES] [--sortie DOSSIER] [--rendus DOSSIER]
    python outils/monde_spawn.py rendu <dossier de la dimension> --rendus DOSSIER
    python outils/monde_spawn.py schematic <fichier> [--rapport FICHIER.md]
    python outils/monde_spawn.py composer <fichier> [--sortie DOSSIER] [--rendus DOSSIER] [--rapport FICHIER.md]
                                        [--position X Y Z] [--borne X Y Z] [--ecart N] [--config-bordure FICHIER.toml]

grotte : la grotte du terminus, celle de la cinématique d'arrivée (HeraliZ Utils, PlateauRendu : même profil de roche,
    même bruit, même tunnel, même voie, même heurtoir), en blocs, à l'endroit que la cinématique attend (réveil en
    -61 265 2, épave du train couchée derrière : heralisz_utils_cinematique.json). Les blocs barrière invisibles qui
    rendent l'épave solide sont posés comme le ferait « /cinematique epave poser ». Le train lui-même n'est pas fait de
    blocs : c'est le modèle 3D du mod, dessiné par le jeu des joueurs à sa place. La borne de départ (bloc
    heralisz_utils:borne_depart) est posée dans une niche de la paroi, sous une lampe verte.
copier : recopie SEULEMENT la zone donnée (blocs, entités de blocs, entités) d'un monde (LECTURE SEULE) ; tout le reste
    est du vide. --dimension : minecraft:overworld (défaut, dossier region/), minecraft:the_nether, ou ns:nom
    (dimensions/ns/nom/). --decalage : déplacement des coordonnées (0 0 0 par défaut). --reveil : point de réveil dans
    la copie (après décalage), contrôlé (place libre, sol dessous) et noté dans heraliz_spawn.json, avec --cap (regard ;
    celui de la cinématique par défaut) ; les rendus montrent alors aussi la vue au réveil. Sert à recopier l'ancien
    spawn (outils/recherche_grotte.py, workflow « Grotte du spawn »). Le point de réveil et l'épave se règlent ensuite
    en jeu s'il le faut (/cinematique reveil, /cinematique epave ...), la borne se pose avec /depart borne.
rendu : vues de contrôle d'une dimension déjà fabriquée.
schematic : lit un schematic (Sponge v1/v2/v3 .schem de WorldEdit, ou structure .nbt de Minecraft ; l'ancien
    .schematic de MCEdit est refusé) et en fait le rapport : taille, palette, entités, blocs de nos mods, où l'on marche.
composer : la grotte (comme « grotte », réveil et épave inchangés, porte de service de la niche retirée et borne pas
    posée là) + le schematic du spawn à l'est, relié par un tunnel de 3 × 3 (pierre de taille, lanternes, escaliers si
    le niveau change) qui part de la niche. Le schematic est posé pour qu'on y marche à la hauteur de la grotte (sinon
    le plus près possible) et pour que la rangée du tunnel rejoigne au moindre coût la plus grande zone où l'on marche ;
    sur cette rangée, le raccord creuse 3 × 3 là où c'est bouché et pose un sol là où il manque (rien d'autre n'est
    touché). La borne de départ : celle du schematic s'il en a une, sinon --borne (repère du schematic), sinon au cœur
    de la grande zone, avec de la place autour, visible en sortant du tunnel, écran tourné vers l'arrivée. --position :
    coin bas nord-ouest du schematic donné à la main (la rangée de la niche, z = 9, doit le traverser). La bordure du
    monde du spawn (centrée sur le réveil, 256 par défaut) est vérifiée : si elle doit grandir, la taille conseillée est
    écrite dans --config-bordure (heraliz_spawn-server.toml, section [spawn]).

Sortie (--sortie, défaut livraison/monde_spawn, hors Git) : <sortie>/spawn/region/*.mca (et entities/ pour une copie),
<sortie>/spawn/heraliz_spawn.json (ce qui a été fait, point de réveil conseillé, borne). Le dossier <sortie>/spawn est
celui qui devient dimensions/heralisz/spawn/ dans le monde du serveur.
Rendus (--rendus) : vue de dessus (plafond retiré), coupe de côté le long de la voie, vue au réveil (ce que voit le
joueur en se relevant ; l'épave, dessinée par le jeu, y est en gris).

Les chunks écrits n'ont ni lumière ni cartes de hauteur : le serveur les calcule au premier chargement (comme la carte
de monde/HeraliZ). Rien n'est jamais écrit dans un monde existant. Besoin : numpy (et Pillow pour les rendus).
"""
import argparse
import json
import math
import os
import struct
import sys
import uuid
import zlib
from pathlib import Path

import numpy as np

DEPOT = Path(__file__).resolve().parent.parent
DATA_VERSION = 3465                       # Minecraft 1.20.1
Y_MIN, Y_MAX = -64, 319                   # hauteur de la dimension (comme le monde principal)
BIOME = "minecraft:the_void"              # aucun monstre ni animal n'y apparaît
CINE_JSON = DEPOT / "mods/heralisz-utils/src/commun/resources/heralisz_utils_cinematique.json"
PACK = DEPOT / "mods/heraliz-interface/src/main/resources/resourcepacks/textures/assets/minecraft/textures/block"


# ================================================================== NBT (lecture et écriture complètes)
class T:
    """Étiquette NBT : type (1 à 12) et valeur (dict pour 10, (type, liste) pour 9)."""
    __slots__ = ("t", "v")

    def __init__(self, t, v):
        self.t, self.v = t, v

    def __getitem__(self, k):
        return self.v[k]

    def get(self, k, d=None):
        return self.v.get(k, d) if self.t == 10 else d


def _lire(d, i, t):
    if t == 1: return struct.unpack_from(">b", d, i)[0], i + 1
    if t == 2: return struct.unpack_from(">h", d, i)[0], i + 2
    if t == 3: return struct.unpack_from(">i", d, i)[0], i + 4
    if t == 4: return struct.unpack_from(">q", d, i)[0], i + 8
    if t == 5: return struct.unpack_from(">f", d, i)[0], i + 4
    if t == 6: return struct.unpack_from(">d", d, i)[0], i + 8
    if t == 7:
        n = struct.unpack_from(">i", d, i)[0]
        return bytes(d[i + 4:i + 4 + n]), i + 4 + n
    if t == 8:
        n = struct.unpack_from(">H", d, i)[0]
        return bytes(d[i + 2:i + 2 + n]).decode("utf-8", "surrogatepass"), i + 2 + n
    if t == 9:
        et = d[i]
        n = struct.unpack_from(">i", d, i + 1)[0]
        i += 5
        out = []
        for _ in range(max(0, n)):
            v, i = _lire(d, i, et)
            out.append(T(et, v))
        return (et, out), i
    if t == 10:
        o = {}
        while True:
            tt = d[i]
            i += 1
            if tt == 0:
                return o, i
            n = struct.unpack_from(">H", d, i)[0]
            k = bytes(d[i + 2:i + 2 + n]).decode("utf-8", "surrogatepass")
            i += 2 + n
            v, i = _lire(d, i, tt)
            o[k] = T(tt, v)
    if t == 11:
        n = struct.unpack_from(">i", d, i)[0]
        return list(struct.unpack_from(">%di" % n, d, i + 4)), i + 4 + 4 * n
    if t == 12:
        n = struct.unpack_from(">i", d, i)[0]
        return list(struct.unpack_from(">%dq" % n, d, i + 4)), i + 4 + 8 * n
    raise ValueError("type NBT inconnu %d" % t)


def nbt_lire(d):
    n = struct.unpack_from(">H", d, 1)[0]
    v, _ = _lire(d, 3 + n, d[0])
    return T(d[0], v)


def _ecrire(o, t, v):
    if t == 1: o.append(struct.pack(">b", v))
    elif t == 2: o.append(struct.pack(">h", v))
    elif t == 3: o.append(struct.pack(">i", v))
    elif t == 4: o.append(struct.pack(">q", v))
    elif t == 5: o.append(struct.pack(">f", v))
    elif t == 6: o.append(struct.pack(">d", v))
    elif t == 7:
        o.append(struct.pack(">i", len(v)))
        o.append(bytes(v))
    elif t == 8:
        b = v.encode("utf-8", "surrogatepass")
        o.append(struct.pack(">H", len(b)))
        o.append(b)
    elif t == 9:
        et, l = v
        o.append(struct.pack(">bi", et if l else (et or 0), len(l)))
        for x in l:
            _ecrire(o, et, x.v)
    elif t == 10:
        for k, x in v.items():
            b = k.encode("utf-8", "surrogatepass")
            o.append(struct.pack(">bH", x.t, len(b)))
            o.append(b)
            _ecrire(o, x.t, x.v)
        o.append(b"\x00")
    elif t == 11:
        o.append(struct.pack(">i%di" % len(v), len(v), *v))
    elif t == 12:
        o.append(struct.pack(">i%dq" % len(v), len(v), *v))


def nbt_ecrire(tag):
    o = [struct.pack(">bH", tag.t, 0)]
    _ecrire(o, tag.t, tag.v)
    return b"".join(o)


def C(**kw):
    return T(10, kw)


def S(s):
    return T(8, s)


def I(n):
    return T(3, n)


def L(t, items):
    return T(9, (t, list(items)))


# ================================================================== régions (.mca)
def region_lire(chemin):
    """{(cx local, cz local): Tag} d'un fichier de régions."""
    out = {}
    if not os.path.exists(chemin):
        return out
    data = open(chemin, "rb").read()
    if len(data) < 8192:
        return out
    for k in range(1024):
        off = struct.unpack_from(">I", data, 4 * k)[0]
        sec = off >> 8
        if sec == 0 or sec * 4096 + 5 > len(data):
            continue
        n, comp = struct.unpack_from(">IB", data, sec * 4096)
        brut = data[sec * 4096 + 5:sec * 4096 + 4 + n]
        try:
            if comp == 2:
                brut = zlib.decompress(brut)
            elif comp == 1:
                import gzip
                brut = gzip.decompress(brut)
            elif comp != 3:
                continue
            out[(k & 31, k >> 5)] = nbt_lire(brut)
        except Exception as e:  # chunk abîmé : sauté (dit à l'écran)
            print("  chunk illisible dans %s (%d, %d) : %s" % (os.path.basename(chemin), k & 31, k >> 5, e))
    return out


def region_ecrire(chemin, chunks):
    """chunks : {(cx local, cz local): Tag}. Écrit un fichier de régions complet (zlib, horodatage 0)."""
    entete = bytearray(8192)
    corps = bytearray()
    secteur = 2
    for (lx, lz), tag in sorted(chunks.items(), key=lambda e: (e[0][1], e[0][0])):
        brut = zlib.compress(nbt_ecrire(tag), 6)
        bloc = struct.pack(">IB", len(brut) + 1, 2) + brut
        n = (len(bloc) + 4095) // 4096
        if n > 255:
            raise ValueError("chunk trop gros (%d secteurs)" % n)
        bloc += b"\x00" * (n * 4096 - len(bloc))
        struct.pack_into(">I", entete, 4 * (lx + 32 * lz), (secteur << 8) | n)
        corps += bloc
        secteur += n
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "wb") as f:
        f.write(entete)
        f.write(corps)


# ================================================================== volume de blocs
class Monde:
    """Blocs d'une dimension en construction : sections de 16³ (numpy), palette globale d'états, entités de blocs et
    entités (étiquettes NBT déjà à leur place)."""

    def __init__(self):
        self.etats = [("minecraft:air", ())]
        self.index = {self.etats[0]: 0}
        self.sections = {}       # (cx, sy, cz) -> uint16[16 y][16 z][16 x]
        self.blocs_entites = {}  # (x, y, z) -> Tag
        self.entites = []        # Tag (Pos dedans)

    def etat(self, nom, **props):
        if ":" not in nom:
            nom = "minecraft:" + nom
        cle = (nom, tuple(sorted((k, str(v).lower() if isinstance(v, bool) else str(v)) for k, v in props.items())))
        i = self.index.get(cle)
        if i is None:
            i = len(self.etats)
            self.etats.append(cle)
            self.index[cle] = i
        return i

    def section(self, cx, sy, cz, creer=True):
        k = (cx, sy, cz)
        s = self.sections.get(k)
        if s is None and creer:
            s = self.sections[k] = np.zeros((16, 16, 16), dtype=np.uint16)
        return s

    def poser(self, x, y, z, etat):
        if not (Y_MIN <= y <= Y_MAX):
            return
        self.section(x >> 4, y >> 4, z >> 4)[y & 15, z & 15, x & 15] = etat

    def lire(self, x, y, z):
        s = self.section(x >> 4, y >> 4, z >> 4, False)
        return 0 if s is None else int(s[y & 15, z & 15, x & 15])

    def poser_grille(self, x0, y0, z0, grille):
        """grille : int[nx][ny][nz] d'états (0 = laisser tel quel)."""
        nx, ny, nz = grille.shape
        for cx in range(x0 >> 4, ((x0 + nx - 1) >> 4) + 1):
            for cz in range(z0 >> 4, ((z0 + nz - 1) >> 4) + 1):
                for sy in range(max(Y_MIN, y0) >> 4, (min(Y_MAX, y0 + ny - 1) >> 4) + 1):
                    ax0, ax1 = max(x0, cx * 16), min(x0 + nx - 1, cx * 16 + 15)
                    ay0, ay1 = max(y0, sy * 16), min(y0 + ny - 1, sy * 16 + 15)
                    az0, az1 = max(z0, cz * 16), min(z0 + nz - 1, cz * 16 + 15)
                    bout = grille[ax0 - x0:ax1 - x0 + 1, ay0 - y0:ay1 - y0 + 1, az0 - z0:az1 - z0 + 1]
                    if not bout.any():
                        continue
                    s = self.section(cx, sy, cz)
                    vue = s[ay0 - sy * 16:ay1 - sy * 16 + 1, az0 - cz * 16:az1 - cz * 16 + 1, ax0 - cx * 16:ax1 - cx * 16 + 1]
                    b = bout.transpose(1, 2, 0)
                    np.copyto(vue, b.astype(np.uint16), where=b > 0)

    # ------------------------------------------------------------------ écriture
    def ecrire(self, dossier):
        """Écrit region/ (et entities/) : un chunk pour chaque colonne qui a au moins un bloc, une entité de bloc ou une
        entité ; renvoie le nombre de chunks."""
        colonnes = {}
        for (cx, sy, cz), s in self.sections.items():
            if s.any():
                colonnes.setdefault((cx, cz), {})[sy] = s
        for (x, y, z) in self.blocs_entites:
            colonnes.setdefault((x >> 4, z >> 4), {})
        regions = {}
        for (cx, cz), secs in colonnes.items():
            regions.setdefault((cx >> 5, cz >> 5), {})[(cx & 31, cz & 31)] = self._chunk(cx, cz, secs)
        for (rx, rz), chunks in regions.items():
            region_ecrire(os.path.join(dossier, "region", "r.%d.%d.mca" % (rx, rz)), chunks)
        if self.entites:
            par_chunk = {}
            for e in self.entites:
                p = e["Pos"].v[1]
                par_chunk.setdefault((math.floor(p[0].v) >> 4, math.floor(p[2].v) >> 4), []).append(e)
            regs = {}
            for (cx, cz), es in par_chunk.items():
                regs.setdefault((cx >> 5, cz >> 5), {})[(cx & 31, cz & 31)] = C(
                    DataVersion=I(DATA_VERSION), Position=T(11, [cx, cz]), Entities=L(10, es))
            for (rx, rz), chunks in regs.items():
                region_ecrire(os.path.join(dossier, "entities", "r.%d.%d.mca" % (rx, rz)), chunks)
        return len(colonnes)

    def _palette(self, ids):
        return [C(Name=S(self.etats[i][0]), **({"Properties": C(**{k: S(v) for k, v in self.etats[i][1]})}
                                                 if self.etats[i][1] else {})) for i in ids]

    def _chunk(self, cx, cz, secs):
        sections = []
        for sy in range(Y_MIN >> 4, (Y_MAX >> 4) + 1):
            s = secs.get(sy)
            if s is None or not s.any():
                bs = C(palette=L(10, self._palette([0])))
            else:
                ids, inv = np.unique(s.ravel(), return_inverse=True)   # ordre y, z, x = ordre de Minecraft
                if len(ids) == 1:
                    bs = C(palette=L(10, self._palette([int(ids[0])])))
                else:
                    bits = max(4, (len(ids) - 1).bit_length())
                    par = 64 // bits
                    vals = inv.astype(np.uint64)
                    n = (4096 + par - 1) // par
                    vals = np.concatenate([vals, np.zeros(n * par - 4096, dtype=np.uint64)]).reshape(n, par)
                    longs = np.zeros(n, dtype=np.uint64)
                    for j in range(par):
                        longs |= vals[:, j] << np.uint64(j * bits)
                    bs = C(palette=L(10, self._palette([int(i) for i in ids])),
                           data=T(12, longs.view(np.int64).tolist()))
            sections.append(C(Y=T(1, sy), block_states=bs, biomes=C(palette=L(8, [S(BIOME)]))))
        be = [t for (x, y, z), t in sorted(self.blocs_entites.items()) if x >> 4 == cx and z >> 4 == cz]
        return C(DataVersion=I(DATA_VERSION), xPos=I(cx), zPos=I(cz), yPos=I(Y_MIN >> 4), LastUpdate=T(4, 0),
                 InhabitedTime=T(4, 0), isLightOn=T(1, 0), Status=S("minecraft:full"), sections=L(10, sections),
                 block_entities=L(10, be), block_ticks=L(10, []), fluid_ticks=L(10, []),
                 structures=C(References=C(), starts=C()))


# ================================================================== bruit de la cinématique (PlateauRendu, à l'identique)
M64 = (1 << 64) - 1


def _signe(k):
    k &= M64
    return k - (1 << 64) if k >> 63 else k


def _h(x, y, g):
    k = _signe(x * 374761393 + y * 668265263 + g * 1442695040888963407)
    k = _signe((k ^ ((k & M64) >> 13)) * 1274126177)
    return ((k ^ ((k & M64) >> 16)) & 0xFFFFFF) / float(0xFFFFFF)


_CACHE_H = {}


def bruit(x, y, g):
    xi, yi = math.floor(x), math.floor(y)
    fx, fy = x - xi, y - yi

    def h(a, b):
        k = (a, b, g)
        v = _CACHE_H.get(k)
        if v is None:
            v = _CACHE_H[k] = _h(a, b, g)
        return v

    a, b, c, d = h(xi, yi), h(xi + 1, yi), h(xi, yi + 1), h(xi + 1, yi + 1)
    fx = fx * fx * (3 - 2 * fx)
    fy = fy * fy * (3 - 2 * fy)
    return (a + (b - a) * fx) + ((c + (d - c) * fx) - (a + (b - a) * fx)) * fy


# ================================================================== épave (CineDecorServeur.volume, à l'identique)
LOCO, VOITURE = (9.7, 0.2, 5.1, 2.15), (11.3, 0.2, 5.0, 2.15)


def volume_epave(p, marge):
    modele, px, py, pz, lacet, roulis = p
    d = LOCO if modele == "loco" else VOITURE if (modele.startswith("voiture") or modele in ("fourgon", "automotrice")) else None
    if d is None:
        return []
    ax, y0, y1, az = d[0] + marge, d[1] - marge, d[2] + marge, d[3] + marge
    r = math.sqrt(ax * ax + y1 * y1 + az * az) + 1
    cl, sl = math.cos(math.radians(lacet)), math.sin(math.radians(lacet))
    cr, sr = math.cos(math.radians(roulis)), math.sin(math.radians(roulis))
    out = []
    for x in range(math.floor(px - r), math.floor(px + r) + 1):
        for z in range(math.floor(pz - r), math.floor(pz + r) + 1):
            for y in range(math.floor(py - r), math.floor(py + r) + 1):
                wx, wy, wz = x + 0.5 - px, y + 0.5 - py, z + 0.5 - pz
                lx, mz = wx * cl + wz * sl, -wx * sl + wz * cl
                ly, lz = wy * cr + mz * sr, -wy * sr + mz * cr
                if abs(lx) <= ax and y0 <= ly <= y1 and abs(lz) <= az:
                    out.append((x, y, z))
    return out


# ================================================================== la grotte du terminus
# Repère de la cinématique (PlateauRendu) : xp le long de la voie (vers l'avant), zp en travers (vers la droite), hauteur
# au-dessus du sol. Ici la rame roule vers +z : z = Z0 + xp, x = XC - zp (face au sud, la droite est à l'ouest).
XC = -67.5            # axe de la voie (rails en x = -68)
Z0 = -18              # portail du tunnel (xp = 0)
SOL_Y = 265           # premier bloc d'air au-dessus du sol (le joueur s'y tient)
HEURTOIR = 52.0       # PlateauRendu.HEURTOIR
LONGUEUR = 68.0       # longueur de la grotte (le fond se resserre à partir de 54)
NA = 26
TL, TH, TV = 4.3, 4.9, 7.2   # tunnel : demi-largeur, piédroits, voûte (au-dessus du sol)
TUNNEL = 13.0         # longueur du tunnel gardée derrière le portail (puis éboulement)
EP = 3                # épaisseur de la roche autour du vide
# lampes de chantier de la cinématique (xp, hauteur au-dessus du rail, zp)
LAMPES = [(6, 5.0, -6.5), (14, 5.5, -11), (24, 6.0, 11), (33, 6.5, 12), (44, 5.0, -10), (50, 4.0, 6), (58, 4.0, -9)]
# niche de la sortie (borne de départ) : dans la paroi gauche (côté +x), face à la grotte
NICHE_XP = 27.0
LUMIERE_FOND = 9      # niveau des blocs de lumière invisibles posés en quinconce sous la voûte


def rayon_grotte(xp, k):
    return 0.86 + 0.24 * bruit(xp * 0.18, k * 0.45, 7) + 0.08 * bruit(xp * 0.7, k * 1.3, 9)


def echelles(xp):
    fin = 1 - (xp - (LONGUEUR - 14)) / 16.0 if xp > LONGUEUR - 14 else 1.0
    return max(0.3, fin), max(0.45, fin)


def dans_grotte(xp, zp, h):
    """(xp, zp, h) dans le vide de la grotte ? h >= 0 au-dessus du sol."""
    if xp < 0 or xp > LONGUEUR or h < 0:
        return False
    sh, sv = echelles(xp)
    u, v = zp / (15 * sh), h / (13 * sv)
    a = math.atan2(v, u)                     # 0 à pi (à droite, en haut, à gauche)
    k = (math.pi - a) / math.pi * NA
    r = rayon_grotte(xp, k)
    return u * u + v * v <= r * r


def dans_tunnel(zp, h, ech=1.0, echv=1.0):
    if h < 0:
        return False
    tl, th, tv = TL * ech, TH * echv, TV * echv
    if abs(zp) > tl:
        return False
    if h <= th:
        return True
    return (zp / tl) ** 2 + ((h - th) / (tv - th)) ** 2 <= 1


def rocher(xp, k, graine=11):
    return int(bruit(xp * 0.3, k * 0.5, graine) * 4.99)


def generer_grotte(m, sortie_ouverte=False):
    """Construit la grotte dans le Monde m ; renvoie les informations du manifeste. sortie_ouverte (composition avec le
    schematic du spawn) : la porte de service de la niche est retirée (passage de 3 de large vers le tunnel du spawn) et
    la borne de départ n'est pas posée ici (elle va dans la partie spawn)."""
    cine = json.loads(CINE_JSON.read_text(encoding="utf-8"))
    reveil = cine["reveil"]
    epave = [tuple(p) for p in cine["epave"]]
    # emprise (blocs) : x de XC - 22 à XC + 22, z de Z0 - TUNNEL - 6 à Z0 + LONGUEUR + 5, y du sol - 6 à + 22
    x0, x1 = int(math.floor(XC - 24)), int(math.ceil(XC + 24))
    z0, z1 = int(Z0 - TUNNEL - 8), int(Z0 + LONGUEUR + 6)
    y0, y1 = SOL_Y - 6, SOL_Y + 22
    nx, ny, nz = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    vide = np.zeros((nx, ny, nz), dtype=bool)
    tun = np.zeros_like(vide)
    for i in range(nx):
        zp = XC - (x0 + i + 0.5)
        for kz in range(nz):
            xp = (z0 + kz + 0.5) - Z0
            for j in range(ny):
                h = (y0 + j) - SOL_Y + 0.5
                if dans_grotte(xp, zp, h):
                    vide[i, j, kz] = True
                elif -TUNNEL <= xp < 0.5 and dans_tunnel(zp, h):
                    vide[i, j, kz] = True
                    tun[i, j, kz] = True
    # niche de la sortie : 3 de large, 3 de haut, creusée dans la paroi gauche jusqu'au vide de la grotte
    zn = int(Z0 + NICHE_XP)
    bord = None
    for i in range(nx - 1, -1, -1):                       # paroi gauche (+x) au niveau du sol
        if vide[i, SOL_Y - y0, zn - z0]:
            bord = x0 + i
            break
    niche_x = bord + 3                                     # fond de la niche
    for x in range(bord - 1, niche_x + 1):
        for z in range(zn - 1, zn + 2):
            for y in range(SOL_Y, SOL_Y + 3):
                vide[x - x0, y - y0, z - z0] = True
    # roche : tout ce qui touche le vide à moins de EP blocs (dilatation 3D)
    roche = np.zeros_like(vide)
    for dx in range(-EP, EP + 1):
        for dy in range(-EP, EP + 1):
            for dz in range(-EP, EP + 1):
                if dx * dx + dy * dy + dz * dz > EP * EP + 1:
                    continue
                roche |= np.roll(np.roll(np.roll(vide, dx, 0), dy, 1), dz, 2)
    roche &= ~vide
    roche[0, :, :] = roche[-1, :, :] = False
    roche[:, 0, :] = roche[:, -1, :] = False
    roche[:, :, 0] = roche[:, :, -1] = False

    E = m.etat
    pierres = [E("stone"), E("andesite"), E("tuff"), E("deepslate"), E("stone")]
    grille = np.zeros((nx, ny, nz), dtype=np.int32)
    for i, j, kz in zip(*np.nonzero(roche)):
        x, y, z = x0 + i, y0 + j, z0 + kz
        xp, zp, h = (z + 0.5) - Z0, XC - (x + 0.5), y - SOL_Y + 0.5
        a = math.atan2(max(h, 0) / 13, zp / 15)
        k = (math.pi - a) / math.pi * NA
        b = pierres[rocher(xp, k)]
        if y == SOL_Y - 1 and vide[i, j + 1, kz]:      # dessus du sol : gravier, terre, éboulis (comme la cinématique)
            nb = bruit(xp * 0.25, zp * 0.25, 13)
            sous_voie = abs(zp) < 3.1 and xp < HEURTOIR + 1
            b = E("gravel") if sous_voie or nb < 0.45 else E("coarse_dirt") if nb < 0.75 else E("cobblestone")
        grille[i, j, kz] = b
    # tunnel : revêtement de pierre de taille, portail (bandeau de pierre de taille et clé sculptée)
    for i, j, kz in zip(*np.nonzero(roche)):
        x, y, z = x0 + i, y0 + j, z0 + kz
        xp, zp, h = (z + 0.5) - Z0, XC - (x + 0.5), y - SOL_Y + 0.5
        if -TUNNEL - 1 <= xp < 0 and dans_tunnel(zp, h, 1.3, 1.2) and y >= SOL_Y:
            grille[i, j, kz] = E("cracked_stone_bricks") if bruit(xp * 0.6, h * 0.6 + zp, 31) > 0.72 else E("stone_bricks")
        if 0 <= xp < 1.0 and dans_tunnel(zp, h, 1.16 * 1.25, 1.13 * 1.2) and not dans_tunnel(zp, h) and y >= SOL_Y:
            cle = abs(zp) < 1.0 and h > TV
            grille[i, j, kz] = E("chiseled_stone_bricks") if cle else E("stone_bricks")
    m.poser_grille(x0, y0, z0, grille)

    def vide_ici(x, y, z):
        return x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1 and vide[x - x0, y - y0, z - z0]

    # épave : volume (marge 0,3 comme /cinematique epave poser) ; rien d'autre n'y est posé
    barrieres = set()
    for p in epave:
        for (x, y, z) in volume_epave(p, 0.3):
            if vide_ici(x, y, z):
                barrieres.add((x, y, z))
    pris = set(barrieres)
    xr, yr, zr = reveil
    garde = {(xr + dx, yr + dy, zr + dz) for dx in (-1, 0, 1) for dy in (0, 1, 2) for dz in (-1, 0, 1)}

    def libre(x, y, z):
        return vide_ici(x, y, z) and (x, y, z) not in pris and (x, y, z) not in garde and m.lire(x, y, z) == 0

    def mettre(x, y, z, e):
        if libre(x, y, z):
            m.poser(x, y, z, e)
            pris.add((x, y, z))
            return True
        return False

    # éboulement qui ferme le tunnel (derrière le portail) : gravier, pavés, andésite
    for x in range(x0, x1 + 1):
        for z in range(int(Z0 - TUNNEL) - 1, int(Z0 - TUNNEL + 5)):
            for y in range(SOL_Y, SOL_Y + 9):
                if not vide_ici(x, y, z):
                    continue
                prof = z - (Z0 - TUNNEL)            # 0 au fond, 5 vers la grotte
                hmax = 9 - prof * 1.6 + bruit(x * 0.7, z * 0.7, 41) * 2.0
                if y - SOL_Y < hmax:
                    nb = bruit(x * 0.9, y * 0.9 + z, 43)
                    mettre(x, y, z, E("gravel") if nb < 0.4 else E("cobblestone") if nb < 0.7 else E("andesite"))
    # voie : rails (nord-sud) du tunnel au heurtoir, sauf où l'épave l'a arrachée ; traverses de ballast déjà au sol
    xrail = math.floor(XC)
    for z in range(int(Z0 - TUNNEL + 4), int(Z0 + HEURTOIR)):
        if (xrail, SOL_Y, z) in barrieres or any((xrail + d, SOL_Y, z) in barrieres for d in (-1, 1)):
            continue
        if bruit(z * 0.37, 2.5, 47) < 0.12 and z > Z0 + 4:      # rail arraché çà et là près de l'épave
            continue
        mettre(xrail, SOL_Y, z, E("rail", shape="north_south"))
    # heurtoir (ce qu'il en reste : l'épave l'a percuté) : massif, panneau rayé, feu rouge
    zh = int(Z0 + HEURTOIR)
    for x in range(xrail - 1, xrail + 2):
        mettre(x, SOL_Y, zh, E("smooth_stone"))
        mettre(x, SOL_Y, zh + 1, E("smooth_stone"))
        mettre(x, SOL_Y + 1, zh + 1, E("gray_concrete"))
        mettre(x, SOL_Y + 1, zh, E("yellow_concrete") if (x - xrail) % 2 == 0 else E("black_concrete"))
    mettre(xrail, SOL_Y + 2, zh + 1, E("redstone_torch"))
    # lampes de chantier : lanternes pendues à une chaîne jusqu'au plafond
    for (lxp, lh, lzp) in LAMPES:
        x, z = math.floor(XC - lzp), math.floor(Z0 + lxp)
        y = SOL_Y + int(round(lh)) - 1
        # rapprochée du vide si la paroi est là
        for _ in range(6):
            if vide_ici(x, y, z):
                break
            x += 1 if lzp > 0 else -1
        if not vide_ici(x, y, z):
            continue
        if mettre(x, y, z, E("lantern", hanging=True)):
            yy = y + 1
            while vide_ici(x, yy, z) and yy < y1:
                mettre(x, yy, z, E("chain", axis="y"))
                yy += 1
    # stalactites et racines au plafond, éboulis au pied des parois
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            # plafond : premier bloc de roche au-dessus du vide
            col = vide[x - x0, :, z - z0]
            if not col.any():
                continue
            haut = y0 + int(np.nonzero(col)[0].max())
            xp = z + 0.5 - Z0
            if xp < 2:
                continue
            nb = bruit(x * 0.5, z * 0.5, 53)
            if haut - SOL_Y >= 7 and nb > 0.78:
                mettre(x, haut, z, E("pointed_dripstone", vertical_direction="down", thickness="tip"))
            elif haut - SOL_Y >= 6 and nb < 0.08:
                mettre(x, haut, z, E("hanging_roots"))
            # éboulis contre les parois (pas sur la voie ni devant la niche)
            zp = XC - (x + 0.5)
            if abs(zp) > 6 and vide_ici(x, SOL_Y, z) and not (abs(z - zn) <= 3 and x >= bord - 4):
                pres = any(not vide_ici(x + dx, SOL_Y, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1))
                nb2 = bruit(x * 0.8, z * 0.8, 59)
                if pres and nb2 > 0.62:
                    mettre(x, SOL_Y, z, E("cobblestone") if nb2 > 0.85 else E("andesite_slab", type="bottom") if nb2 > 0.72 else E("gravel"))
                    if nb2 > 0.9:
                        mettre(x, SOL_Y + 1, z, E("cobblestone_slab", type="bottom"))
    # la sortie : niche en pierre de taille, porte de service fermée (fer), lampe verte, borne de départ
    for z in range(zn - 2, zn + 3):
        for y in range(SOL_Y - 1, SOL_Y + 4):
            for x in range(bord - 1, niche_x + 2):
                if not vide_ici(x, y, z):
                    if y == SOL_Y - 1:
                        m.poser(x, y, z, E("polished_andesite"))
                    elif x > bord:
                        m.poser(x, y, z, E("stone_bricks") if (y + z) % 4 else E("cracked_stone_bricks"))
    for z in range(zn - 1, zn + 2):
        for x in range(bord - 1, niche_x + 1):
            m.poser(x, SOL_Y - 1, z, E("polished_andesite"))
    porte_x = niche_x + 1
    m.poser(porte_x, SOL_Y + 2, zn, E("verdant_froglight", axis="y"))
    if sortie_ouverte:
        # passage vers le spawn : 3 de large, 2 de haut, sous la lampe verte (le tunnel part de porte_x + 1)
        for dz in (-1, 0, 1):
            for y in (SOL_Y, SOL_Y + 1):
                m.poser(porte_x, y, zn + dz, 0)
        borne = None
    else:
        m.poser(porte_x, SOL_Y, zn, E("iron_door", facing="west", half="lower", hinge="left", open=False, powered=False))
        m.poser(porte_x, SOL_Y + 1, zn, E("iron_door", facing="west", half="upper", hinge="left", open=False, powered=False))
        for dz in (-1, 1):
            m.poser(porte_x, SOL_Y, zn + dz, E("polished_andesite"))
            m.poser(porte_x, SOL_Y + 1, zn + dz, E("polished_andesite"))
        borne = (niche_x - 1, SOL_Y, zn)
        m.poser(*borne, E("heralisz_utils:borne_depart", facing="west"))
        pris.add(borne)
    # lumière d'appoint invisible (blocs « light » de niveau LUMIERE_FOND, sans collision, que personne ne peut prendre) :
    # la grotte reste sombre, mais on y voit où l'on marche et la niche se trouve
    for x in range(x0, x1 + 1, 6):
        for z in range(z0 + 3, z1 + 1, 6):
            y = SOL_Y + 6
            if vide_ici(x, y, z) and Z0 - 2 <= z:
                mettre(x, y, z, E("light", level=LUMIERE_FOND))
    # barrières invisibles de l'épave en dernier (rien ne les remplace)
    for (x, y, z) in barrieres:
        if m.lire(x, y, z) == 0:
            m.poser(x, y, z, E("barrier"))
    # contrôle : le réveil est praticable (deux blocs d'air, un sol dessous)
    ok = all(m.lire(xr, yr + d, zr) == 0 and vide_ici(xr, yr + d, zr) for d in (0, 1)) and m.lire(xr, yr - 1, zr) != 0
    if not ok:
        raise SystemExit("Le point de réveil %s n'est pas praticable dans la grotte : réglages à revoir." % (reveil,))
    hors = sum(1 for p in epave for b in volume_epave(p, 0.0) if not vide_ici(*b) and b[1] >= SOL_Y)
    info = {
        "source": "grotte du terminus (cinématique d'arrivée), générée par outils/monde_spawn.py",
        "emprise": {"x": [x0, x1], "y": [y0, y1], "z": [z0, z1]},
        "reveil": reveil, "cap": cine.get("cap", 0),
        "barrieres_epave": len(barrieres),
        "epave_dans_la_roche": hors,
        "centre_bordure": [XC, (z0 + z1) / 2.0],
        "sortie": {"x": porte_x, "y": SOL_Y, "z": zn},
    }
    if borne:
        info["borne"] = {"x": borne[0], "y": borne[1], "z": borne[2], "facing": "west"}
    return info


# ================================================================== copie d'une zone d'un monde
def dossier_dimension(monde, dim):
    m = Path(monde)
    if dim in ("minecraft:overworld", "overworld"):
        return m
    if dim in ("minecraft:the_nether", "the_nether"):
        return m / "DIM-1"
    if dim in ("minecraft:the_end", "the_end"):
        return m / "DIM1"
    ns, _, nom = dim.partition(":")
    return m / "dimensions" / ns / nom


def decoder_section(sec):
    bs = sec.get("block_states")
    if bs is None:
        return None, None
    pal = bs["palette"].v[1]
    if len(pal) == 1 or bs.get("data") is None:
        return pal, np.zeros(4096, dtype=np.int64)
    bits = max(4, (len(pal) - 1).bit_length())
    par = 64 // bits
    a = np.array(bs["data"].v, dtype=np.int64).view(np.uint64)
    sh = np.arange(par, dtype=np.uint64) * np.uint64(bits)
    v = (a[:, None] >> sh[None, :]) & np.uint64((1 << bits) - 1)
    return pal, v.ravel()[:4096].astype(np.int64)


def copier_zone(m, monde, dim, zone, dec):
    x0, y0, z0, x1, y1, z1 = zone
    x0, x1 = sorted((x0, x1))
    y0, y1 = sorted((y0, y1))
    z0, z1 = sorted((z0, z1))
    dx, dy, dz = dec
    base = dossier_dimension(monde, dim)
    if not (base / "region").is_dir():
        raise SystemExit("Pas de dossier region/ pour %s dans %s" % (dim, monde))
    nb_blocs = nb_be = nb_ent = 0
    regions = {}
    for cx in range(x0 >> 4, (x1 >> 4) + 1):
        for cz in range(z0 >> 4, (z1 >> 4) + 1):
            rk = (cx >> 5, cz >> 5)
            if rk not in regions:
                regions[rk] = region_lire(base / "region" / ("r.%d.%d.mca" % rk))
            t = regions[rk].get((cx & 31, cz & 31))
            if t is None:
                continue
            for sec in t["sections"].v[1]:
                sy = sec["Y"].v
                if sy * 16 + 15 < y0 or sy * 16 > y1:
                    continue
                pal, idx = decoder_section(sec)
                if pal is None:
                    continue
                loc = np.array([m.etat(p["Name"].v, **({k: x.v for k, x in p["Properties"].v.items()} if p.get("Properties") else {}))
                                for p in pal], dtype=np.int32)
                ids = loc[idx].reshape(16, 16, 16).transpose(2, 0, 1)   # -> x, y, z
                bx, by, bz = cx * 16, sy * 16, cz * 16
                ax0, ax1 = max(x0, bx), min(x1, bx + 15)
                ay0, ay1 = max(y0, by), min(y1, by + 15)
                az0, az1 = max(z0, bz), min(z1, bz + 15)
                bout = ids[ax0 - bx:ax1 - bx + 1, ay0 - by:ay1 - by + 1, az0 - bz:az1 - bz + 1]
                m.poser_grille(ax0 + dx, ay0 + dy, az0 + dz, bout)
                nb_blocs += int((bout > 0).sum())
            for be in t.get("block_entities", T(9, (10, []))).v[1]:
                x, y, z = be["x"].v, be["y"].v, be["z"].v
                if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1:
                    be.v["x"], be.v["y"], be.v["z"] = I(x + dx), I(y + dy), I(z + dz)
                    m.blocs_entites[(x + dx, y + dy, z + dz)] = be
                    nb_be += 1
    # entités (dossier entities/ depuis 1.17) : nouvel identifiant, position déplacée
    ents = {}
    for cx in range(x0 >> 4, (x1 >> 4) + 1):
        for cz in range(z0 >> 4, (z1 >> 4) + 1):
            rk = (cx >> 5, cz >> 5)
            if rk not in ents:
                ents[rk] = region_lire(base / "entities" / ("r.%d.%d.mca" % rk))
            t = ents[rk].get((cx & 31, cz & 31))
            if t is None or t.get("Entities") is None:
                continue
            for e in t["Entities"].v[1]:
                if e["id"].v == "minecraft:player":
                    continue
                p = [x.v for x in e["Pos"].v[1]]
                if not (x0 <= p[0] < x1 + 1 and y0 <= p[1] < y1 + 1 and z0 <= p[2] < z1 + 1):
                    continue
                e.v["Pos"] = L(6, [T(6, p[0] + dx), T(6, p[1] + dy), T(6, p[2] + dz)])
                for k, d in (("TileX", dx), ("TileY", dy), ("TileZ", dz)):
                    if e.get(k) is not None:
                        e.v[k] = I(e[k].v + d)
                u = uuid.uuid5(uuid.NAMESPACE_URL, "heraliz-spawn/%s/%r" % (e["id"].v, p)).int
                hi, lo = u >> 64, u & M64
                e.v["UUID"] = T(11, [_i32(hi >> 32), _i32(hi), _i32(lo >> 32), _i32(lo)])
                e.v.pop("Passengers", None)
                m.entites.append(e)
                nb_ent += 1
    return {"source": "copie de %s (%s), zone %s, décalage %s" % (Path(monde).name, dim, [x0, y0, z0, x1, y1, z1], [dx, dy, dz]),
            "emprise": {"x": [x0 + dx, x1 + dx], "y": [y0 + dy, y1 + dy], "z": [z0 + dz, z1 + dz]},
            "blocs": nb_blocs, "entites_de_blocs": nb_be, "entites": nb_ent,
            "centre_bordure": [(x0 + x1) / 2.0 + dx, (z0 + z1) / 2.0 + dz]}


def _i32(v):
    v &= 0xFFFFFFFF
    return v - (1 << 32) if v >> 31 else v


# ================================================================== schematic du spawn (WorldEdit .schem, structure .nbt)
AIR_NOMS = ("minecraft:air", "minecraft:cave_air", "minecraft:void_air", "minecraft:structure_void")
LIQUIDES = ("minecraft:water", "minecraft:lava", "minecraft:bubble_column")
# nos mods (CLAUDE.md, section 3.1) : espaces de noms des blocs à signaler dans le rapport
NOS_ESPACES = ("crac", "reanimation", "airstrike", "caracrea", "spartan", "dayzhotbar", "backpacked")


def est_a_nous(nom):
    ns = nom.partition(":")[0]
    return ns.startswith(("heraliz", "heralisz")) or ns in NOS_ESPACES


def etat_texte(s):
    """« minecraft:oak_stairs[facing=east,half=bottom] » -> (nom, {propriétés})."""
    nom, _, reste = s.strip().partition("[")
    props = {}
    for kv in reste.rstrip("]").split(","):
        if "=" in kv:
            k, _, v = kv.partition("=")
            props[k.strip()] = v.strip()
    nom = nom.strip()
    if ":" not in nom:
        nom = "minecraft:" + nom
    return nom, props


def traversable(nom, props):
    """Le joueur passe-t-il à travers ce bloc (pas de collision, ou presque) ? Estimation par le nom, pour trouver où
    l'on marche dans le schematic."""
    if nom in AIR_NOMS or nom == "minecraft:light":
        return True
    n = nom.split(":")[-1]
    if props.get("open") == "true" and ("door" in n or "gate" in n):
        return True
    if n in ("grass", "tall_grass", "fern", "large_fern", "dead_bush", "seagrass", "tall_seagrass", "snow", "cobweb",
             "redstone_wire", "tripwire", "string", "lever", "hanging_roots", "glow_lichen", "sculk_vein", "moss_carpet",
             "sugar_cane", "wheat", "carrots", "potatoes", "beetroots", "nether_sprouts", "warped_roots", "crimson_roots",
             "spore_blossom", "big_dripleaf_stem", "small_dripleaf", "pink_petals"):
        return True
    cles = ("torch", "rail", "button", "_sign", "banner", "sapling", "flower", "tulip", "orchid", "allium", "bluet",
            "daisy", "dandelion", "poppy", "cornflower", "lily_of_the_valley", "rose_bush", "lilac", "peony", "vine",
            "pressure_plate", "carpet", "mushroom", "coral_fan", "_roots", "lichen", "fungus")
    return any(c in n for c in cles) and not n.endswith("_block") and "wall_fan" not in n


class Schematic:
    """Un schematic lu : taille (W, H, LZ), états (0 = air), blocs[x][y][z], entités de blocs et entités (positions
    relatives au coin bas nord-ouest du volume)."""

    def __init__(self):
        self.format = ""
        self.data_version = None
        self.taille = (0, 0, 0)
        self.etats = [("minecraft:air", {})]
        self.blocs = None
        self.blocs_entites = []      # (x, y, z, id, {clé: Tag})
        self.entites = []            # ((x, y, z), Tag compound complet, id compris)
        self.entites_ignorees = 0
        self.offset = None

    def _etat(self, nom, props, index):
        if nom in AIR_NOMS:
            return 0
        cle = (nom, tuple(sorted(props.items())))
        i = index.get(cle)
        if i is None:
            i = index[cle] = len(self.etats)
            self.etats.append((nom, dict(props)))
        return i


def _varints(b, n):
    a = np.frombuffer(bytes(b), dtype=np.uint8)
    if a.size == n and (a < 128).all():
        return a.astype(np.int32)
    fins = np.nonzero(a < 128)[0]
    debuts = np.concatenate([[0], fins[:-1] + 1]).astype(np.int64)
    longs = fins - debuts + 1
    v = (a[debuts] & 127).astype(np.int64)
    for t in range(1, int(longs.max()) if longs.size else 1):
        q = longs > t
        v[q] |= (a[debuts[q] + t].astype(np.int64) & 127) << (7 * t)
    if v.size < n:
        raise SystemExit("schematic abîmé : %d blocs lus sur %d" % (v.size, n))
    return v[:n].astype(np.int32)


def _court(t):
    return t.v & 0xFFFF if t.t == 2 else int(t.v)


def lire_schematic(chemin):
    """Lit un schematic : Sponge v1/v2/v3 (.schem de WorldEdit 7) ou structure de Minecraft (.nbt). L'ancien format
    MCEdit (.schematic, identifiants numériques d'avant 1.13) est refusé avec un message clair."""
    import gzip
    data = open(chemin, "rb").read()
    if data[:2] == b"\x1f\x8b":
        data = gzip.decompress(data)
    elif data[:1] == b"\x78":
        data = zlib.decompress(data)
    r = nbt_lire(data).v
    if "Schematic" in r and r["Schematic"].t == 10:
        r = r["Schematic"].v
    sc = Schematic()
    index = {}
    if r.get("DataVersion") is not None:
        sc.data_version = r["DataVersion"].v
    v3 = "Blocks" in r and r["Blocks"].t == 10
    if v3 or ("Palette" in r and "BlockData" in r):
        W, H, LZ = _court(r["Width"]), _court(r["Height"]), _court(r["Length"])
        sc.taille = (W, H, LZ)
        sc.format = "Sponge v%s (.schem)" % (r["Version"].v if r.get("Version") is not None else ("3" if v3 else "2"))
        if r.get("Offset") is not None:
            sc.offset = list(r["Offset"].v)
        bl = r["Blocks"].v if v3 else r
        pal = bl["Palette"].v
        lut = np.zeros(max([x.v for x in pal.values()] + [0]) + 1, dtype=np.int32)
        for texte, i in pal.items():
            nom, props = etat_texte(texte)
            lut[i.v] = sc._etat(nom, props, index)
        idx = _varints(bl["Data"].v if v3 else bl["BlockData"].v, W * H * LZ)
        sc.blocs = lut[idx].reshape(H, LZ, W).transpose(2, 0, 1).copy()
        bes = bl.get("BlockEntities") or bl.get("TileEntities")
        for be in (bes.v[1] if bes is not None else []):
            p = list(be["Pos"].v) if be["Pos"].t == 11 else [x.v for x in be["Pos"].v[1]]
            ident = be.get("Id") or be.get("id")
            if v3 and be.get("Data") is not None:
                donnees = dict(be["Data"].v)
            else:
                donnees = {k: x for k, x in be.v.items() if k not in ("Pos", "Id", "id", "ContentVersion")}
            for k in ("x", "y", "z", "id", "keepPacked"):
                donnees.pop(k, None)
            sc.blocs_entites.append((p[0], p[1], p[2], ident.v if ident is not None else "", donnees))
        ents = r.get("Entities")
        for e in (ents.v[1] if ents is not None else []):
            p = [x.v for x in e["Pos"].v[1]]
            ident = e.get("Id") or e.get("id")
            donnees = dict(e["Data"].v) if (v3 and e.get("Data") is not None) else {k: x for k, x in e.v.items() if k not in ("Pos", "Id")}
            donnees["id"] = S(ident.v if ident is not None else "minecraft:marker")
            donnees["Pos"] = L(6, [T(6, float(c)) for c in p])
            sc.entites.append((tuple(p), T(10, donnees)))
    elif "size" in r and "blocks" in r and ("palette" in r or "palettes" in r):
        W, H, LZ = [x.v for x in r["size"].v[1]]
        sc.taille = (W, H, LZ)
        sc.format = "structure de Minecraft (.nbt)"
        pal = r["palette"].v[1] if "palette" in r else r["palettes"].v[1][0].v[1]
        lut = []
        for p in pal:
            props = {k: x.v for k, x in p["Properties"].v.items()} if p.get("Properties") is not None else {}
            lut.append(sc._etat(p["Name"].v, props, index))
        sc.blocs = np.zeros((W, H, LZ), dtype=np.int32)
        for b in r["blocks"].v[1]:
            x, y, z = [c.v for c in b["pos"].v[1]]
            sc.blocs[x, y, z] = lut[b["state"].v]
            if b.get("nbt") is not None:
                d = dict(b["nbt"].v)
                ident = d.pop("id", None)
                for k in ("x", "y", "z", "keepPacked"):
                    d.pop(k, None)
                sc.blocs_entites.append((x, y, z, ident.v if ident is not None else "", d))
        for e in (r["entities"].v[1] if r.get("entities") is not None else []):
            p = [c.v for c in e["pos"].v[1]]
            d = dict(e["nbt"].v)
            d["Pos"] = L(6, [T(6, float(c)) for c in p])
            sc.entites.append((tuple(p), T(10, d)))
    elif "Blocks" in r and r["Blocks"].t == 7:
        raise SystemExit("%s : ancien format MCEdit (.schematic, identifiants numériques d'avant 1.13), illisible ici. "
                         "Le réenregistrer avec WorldEdit sur le serveur : //schem load <nom> puis //schem save <nom> sponge."
                         % chemin)
    else:
        raise SystemExit("%s : format de schematic inconnu (clés : %s)" % (chemin, ", ".join(sorted(r)[:20])))
    # entités : WorldEdit note leur position par rapport au coin du volume ; hors du volume = autre repère, ignorées
    W, H, LZ = sc.taille
    garde = []
    for p, e in sc.entites:
        if -1 <= p[0] <= W + 1 and -1 <= p[1] <= H + 1 and -1 <= p[2] <= LZ + 1:
            garde.append((p, e))
        else:
            sc.entites_ignorees += 1
    sc.entites = garde
    return sc


def masques_schematic(sc):
    """(obstacle, liquide, praticable) : cases où l'on marche (deux cases libres sur un sol), estimées par les noms."""
    passe = np.array([traversable(n, p) for n, p in sc.etats])
    liq = np.array([n in LIQUIDES or (p.get("waterlogged") == "true" and traversable(n, p)) for n, p in sc.etats])
    ob = ~passe[sc.blocs]
    lq = liq[sc.blocs]
    sol = ob & ~lq
    libre = ~ob & ~lq
    tete = np.ones_like(libre)
    tete[:, :-1, :] = libre[:, 1:, :]
    dessous = np.zeros_like(libre)
    dessous[:, 1:, :] = sol[:, :-1, :]
    return ob, lq, libre & tete & dessous


def zone_principale(prat):
    """La plus grande étendue praticable d'un seul tenant (cases voisines, marche de 1 au plus) : tableau booléen."""
    from collections import deque
    W, H, LZ = prat.shape
    lab = np.zeros(prat.shape, dtype=np.int32)
    tailles = [0]
    for x0, y0, z0 in zip(*np.nonzero(prat)):
        if lab[x0, y0, z0]:
            continue
        n = len(tailles)
        lab[x0, y0, z0] = n
        q = deque([(x0, y0, z0)])
        c = 0
        while q:
            x, y, z = q.popleft()
            c += 1
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                a, b = x + dx, z + dz
                if not (0 <= a < W and 0 <= b < LZ):
                    continue
                for dy in (0, 1, -1):
                    yy = y + dy
                    if 0 <= yy < H and prat[a, yy, b] and not lab[a, yy, b]:
                        lab[a, yy, b] = n
                        q.append((a, yy, b))
        tailles.append(c)
    if len(tailles) == 1:
        return None
    return lab == int(np.argmax(tailles))


def rapport_schematic(sc, chemin, md=None):
    """Résumé du schematic (taille, palette, entités, blocs de nos mods) ; écrit en Markdown si md est donné."""
    from collections import Counter
    W, H, LZ = sc.taille
    compte = Counter()
    ids, nb = np.unique(sc.blocs, return_counts=True)
    for i, n in zip(ids, nb):
        if i:
            compte[sc.etats[i][0]] += int(n)
    ents = Counter(e.get("id").v if e.get("id") is not None else "?" for p, e in sc.entites)
    bes = Counter(b[3] for b in sc.blocs_entites)
    nous = {k: v for k, v in compte.items() if est_a_nous(k)}
    autres = {k: v for k, v in compte.items() if not k.startswith("minecraft:") and not est_a_nous(k)}
    _, _, prat = masques_schematic(sc)
    princ = zone_principale(prat)
    info = {"fichier": Path(chemin).name, "octets": os.path.getsize(chemin), "format": sc.format,
            "data_version": sc.data_version, "taille": [W, H, LZ], "blocs": int(sum(compte.values())),
            "etats": len(sc.etats) - 1, "types_de_blocs": len(compte), "entites": dict(ents),
            "entites_ignorees": sc.entites_ignorees, "entites_de_blocs": dict(bes), "blocs_de_nos_mods": nous,
            "blocs_d_autres_mods": autres, "cases_praticables": int(prat.sum()),
            "zone_principale": int(princ.sum()) if princ is not None else 0}
    if md:
        def liste(c):
            return ", ".join("`%s` %d" % kv for kv in sorted(c.items(), key=lambda e: -e[1])) or "aucun"
        o = ["## Schematic `%s`" % info["fichier"], "",
             "- fichier : %d octets, %s, DataVersion %s%s" % (info["octets"], sc.format, sc.data_version,
                                                               "" if sc.data_version == DATA_VERSION else " (Minecraft 1.20.1 = 3465)"),
             "- dimensions : **%d × %d × %d** (largeur x, hauteur y, longueur z) ; %d blocs posés (hors air), %d types de blocs, %d états"
             % (W, H, LZ, info["blocs"], len(compte), info["etats"]),
             "- entités : %d%s%s" % (sum(ents.values()), (" — " + liste(ents)) if ents else "",
                                    (" ; %d hors du volume, ignorées" % sc.entites_ignorees) if sc.entites_ignorees else ""),
             "- entités de blocs (coffres, panneaux...) : %d%s" % (sum(bes.values()), (" — " + liste(bes)) if bes else ""),
             "- blocs de nos mods : %s" % liste(nous),
             "- blocs d'autres mods : %s" % liste(autres),
             "- cases où l'on marche : %d, dont %d dans la plus grande zone d'un seul tenant" % (info["cases_praticables"], info["zone_principale"]),
             "", "<details><summary>Palette complète (%d types)</summary>" % len(compte), "", "| bloc | nombre |", "|---|---|"]
        o += ["| `%s` | %d |" % kv for kv in compte.most_common()]
        o += ["", "</details>", ""]
        Path(md).write_text("\n".join(o), encoding="utf-8")
    return info


# ================================================================== composition : grotte du terminus + schematic du spawn
ECART = 10             # longueur minimale du tunnel entre la niche de la grotte et le schematic
BORDURE_DEFAUT = 256   # heraliz_spawn-server.toml, [spawn] bordure (défaut de HeraliZ Utils)


def composer(m, sc, chemin, position=None, borne_rel=None, ecart=ECART):
    """La grotte du terminus (réveil et épave inchangés) + le schematic du spawn à l'est, relié par un tunnel qui part de
    la niche de la grotte (porte de service retirée) ; la borne de départ dans la partie spawn. Renvoie le manifeste."""
    info = generer_grotte(m, sortie_ouverte=True)
    E = m.etat
    W, H, LZ = sc.taille
    ob, lq, prat = masques_schematic(sc)
    princ = zone_principale(prat)
    if princ is None:
        raise SystemExit("Le schematic n'a aucune case où l'on peut marcher (deux cases libres sur un sol).")
    yl = int(np.bincount(np.nonzero(princ)[1]).argmax())    # niveau où l'on marche le plus (repère du schematic)
    niv = princ[:, yl, :]                                    # grande zone à ce niveau (x, z)
    sx, sy, sz = info["sortie"]["x"], info["sortie"]["y"], info["sortie"]["z"]

    def obst(x, y, z):
        return bool(ob[x, y, z] or lq[x, y, z]) if 0 <= y < H else False

    def cout_ligne(z, xe):
        c = 0
        for x in range(xe):
            if obst(x, yl, z) or obst(x, yl + 1, z):
                c += sum(obst(x, yl + d, z + k) for d in (0, 1, 2) for k in (-1, 0, 1))
                c += 50 * sum(bool(lq[x, yl + d, z + k]) for d in (0, 1, 2) for k in (-1, 0, 1) if yl + d < H)
        return c

    # entrée : la rangée (z) où rejoindre la grande zone depuis la face ouest coûte le moins de blocs retirés
    if position is not None:
        rangs = [sz - position[2]]
        if not 1 <= rangs[0] <= LZ - 2:
            raise SystemExit("Avec --position, la rangée de la niche (z = %d) doit traverser le schematic (z de %d à %d)."
                             % (sz, position[2] + 1, position[2] + LZ - 2))
    else:
        rangs = range(1, LZ - 1)
    best = None
    for z in rangs:
        xs = np.nonzero(niv[:, z])[0]
        if xs.size == 0:
            continue
        xe = int(xs.min())
        score = cout_ligne(z, xe) + 0.25 * xe + 0.05 * abs(z - LZ / 2.0)
        if best is None or score < best[0]:
            best = (score, z, xe)
    if best is None:
        raise SystemExit("Aucune entrée possible dans le schematic depuis l'ouest (rangée de la niche : voir --position).")
    _, ze, xe = best
    # placement : niveau de marche du schematic à la hauteur de la grotte si la hauteur du monde le permet
    if position is not None:
        x0s, y0s, z0s = position
    else:
        y0s = max(min(SOL_Y - yl, Y_MAX - H + 1), Y_MIN)
        z0s = sz - ze
        x0s = None
    yw = y0s + yl                                           # niveau de marche (absolu) dans le schematic
    montee = yw - sy
    if x0s is None:
        x0s = sx + 1 + max(ecart, abs(montee) + 4)
    n = x0s - (sx + 1)                                      # longueur du tunnel
    if n < abs(montee) + 2:
        raise SystemExit("Tunnel trop court (%d) pour monter de %d : écarter le schematic (--ecart ou --position)." % (n, montee))

    # 1. le schematic, ses entités de blocs et ses entités
    lut = np.array([0] + [E(nom, **p) for nom, p in sc.etats[1:]], dtype=np.int32)
    m.poser_grille(x0s, y0s, z0s, lut[sc.blocs])
    nbe = 0
    for (x, y, z, ident, d) in sc.blocs_entites:
        if not (0 <= x < W and 0 <= y < H and 0 <= z < LZ) or not sc.blocs[x, y, z] or not ident:
            continue
        ax, ay, az = x0s + x, y0s + y, z0s + z
        m.blocs_entites[(ax, ay, az)] = C(id=S(ident), x=I(ax), y=I(ay), z=I(az), **d)
        nbe += 1
    # cadres et tableaux : TileX/Y/Z recalés avec le même écart que leur position (repère du schematic ou du monde d'origine)
    ecarts = {}
    for p, e in sc.entites:
        if e.get("TileX") is not None:
            k = (e["TileX"].v - math.floor(p[0]), e["TileY"].v - math.floor(p[1]), e["TileZ"].v - math.floor(p[2]))
            ecarts[k] = ecarts.get(k, 0) + 1
    o = max(ecarts, key=ecarts.get) if ecarts else (0, 0, 0)
    for i, (p, e) in enumerate(sc.entites):
        e.v["Pos"] = L(6, [T(6, p[0] + x0s), T(6, p[1] + y0s), T(6, p[2] + z0s)])
        for k, c, b in (("TileX", 0, x0s), ("TileY", 1, y0s), ("TileZ", 2, z0s)):
            if e.get(k) is not None:
                e.v[k] = I(e[k].v - o[c] + b)
        u = uuid.uuid5(uuid.NAMESPACE_URL, "heraliz-spawn/schematic/%d/%r" % (i, p)).int
        hi, lo = u >> 64, u & M64
        e.v["UUID"] = T(11, [_i32(hi >> 32), _i32(hi), _i32(lo >> 32), _i32(lo)])
        e.v.pop("Passengers", None)
        m.entites.append(e)

    # 2. le tunnel : de la niche (porte_x + 1) à la face ouest du schematic, 3 de large, 3 de haut, escaliers si la
    #    hauteur change (même pierre de taille que le tunnel de la voie, lanternes pendues tous les 6 blocs)
    pierre = [E("stone_bricks"), E("stone_bricks"), E("cracked_stone_bricks"), E("andesite"), E("mossy_stone_bricks")]
    x_deb = sx + 1
    haut, h = [], sy
    for i in range(n):
        if h != yw and i >= 2:
            h += 1 if yw > h else -1
        haut.append(h)
    if haut[-1] != yw:
        raise SystemExit("Rampe du tunnel impossible (%d -> %d en %d blocs)." % (sy, yw, n))
    vide_tunnel = set()
    for i in range(n):
        x, h = x_deb + i, haut[i]
        hp, hn = (haut[i - 1] if i else sy), (haut[i + 1] if i + 1 < n else yw)
        for dz in (-1, 0, 1):
            for y in range(h, max(h, hp, hn) + 3):
                m.poser(x, y, sz + dz, 0)
                vide_tunnel.add((x, y, sz + dz))
    for i in range(n):
        x, h = x_deb + i, haut[i]
        hp, hn = (haut[i - 1] if i else sy), (haut[i + 1] if i + 1 < n else yw)
        for dz in range(-2, 3):
            for y in range(min(h, hp, hn) - 1, max(h, hp, hn) + 4):
                if (x, y, sz + dz) in vide_tunnel:
                    continue
                if abs(dz) <= 1 and y == h - 1:
                    if h > hp:              # on monte en allant vers l'est : la marche est sous ses pieds
                        b = E("stone_brick_stairs", facing="east", half="bottom", shape="straight", waterlogged=False)
                    elif hn < h:            # on descend vers l'est : la marche est ici, elle monte vers l'ouest
                        b = E("stone_brick_stairs", facing="west", half="bottom", shape="straight", waterlogged=False)
                    else:
                        b = E("gravel") if dz == 0 and bruit(x * 0.6, 3.0, 71) < 0.35 else E("polished_andesite")
                    m.poser(x, y, sz + dz, b)
                elif m.lire(x, y, sz + dz) == 0:
                    m.poser(x, y, sz + dz, pierre[int(bruit(x * 0.45, y * 0.45 + dz * 3.1, 73) * 4.99)])
        if i % 6 == 3:
            m.poser(x, h + 2, sz, E("lantern", hanging=True))

    # 3. raccord dans le schematic, sur la rangée de l'entrée : creusé (3 × 3) là où elle est bouchée, sol posé où il manque
    retires = ajoutes = 0
    passe_m = {}

    def passe(i):
        v = passe_m.get(i)
        if v is None:
            nom, p = m.etats[i]
            v = passe_m[i] = traversable(nom, dict(p)) and nom not in LIQUIDES
        return v

    az = z0s + ze
    for xl in range(xe):
        ax = x0s + xl
        bouche = not passe(m.lire(ax, yw, az)) or not passe(m.lire(ax, yw + 1, az))
        if bouche:
            for d in (0, 1, 2):
                for k in (-1, 0, 1):
                    c = (ax, yw + d, az + k)
                    if m.lire(*c):
                        m.poser(*c, 0)
                        m.blocs_entites.pop(c, None)
                        retires += 1
        for k in (-1, 0, 1):
            c = (ax, yw - 1, az + k)
            if passe(m.lire(*c)) and (bouche or k == 0 or passe(m.lire(ax, yw, az + k))):
                m.poser(*c, E("polished_andesite"))
                ajoutes += 1
    arrivee = (x0s + xe, yw, az)

    # 4. la borne de départ : celle du schematic s'il en a une, sinon au cœur de la grande zone, écran vers l'arrivée
    deja = [i for i, (nom, p) in enumerate(sc.etats) if nom == "heralisz_utils:borne_depart"]
    if deja:
        pos = np.argwhere(np.isin(sc.blocs, deja))[0]
        bx, by, bz = x0s + int(pos[0]), y0s + int(pos[1]), z0s + int(pos[2])
        face = sc.etats[int(sc.blocs[tuple(pos)])][1].get("facing", "north")
        origine = "déjà dans le schematic"
    else:
        if borne_rel is not None:
            bl = tuple(borne_rel)
            origine = "donnée à la main (--borne, repère du schematic)"
        else:
            libre = niv.copy()
            libre[max(0, xe - 2):xe + 3, max(0, ze - 2):ze + 3] = False      # pas sur l'arrivée du tunnel
            if not libre.any():
                libre = niv.copy()
            dist = np.zeros(libre.shape, dtype=np.int32)
            e_ = libre.copy()
            k = 0
            while e_.any() and k < 12:                       # place libre autour (distance de Tchebychev au bord)
                dist[e_] = k
                p_ = np.pad(e_, 1, constant_values=False)
                for dx in (-1, 0, 1):
                    for dz in (-1, 0, 1):
                        e_ = e_ & p_[1 + dx:1 + dx + e_.shape[0], 1 + dz:1 + dz + e_.shape[1]]
                k += 1
            cand = np.argwhere(libre & (dist >= min(2, int(dist.max()))))
            cx, cz = np.argwhere(niv).mean(axis=0)

            def vue_degagee(c):        # on la voit en arrivant : la ligne droite depuis l'arrivée reste praticable
                n_ = int(max(abs(c[0] - xe), abs(c[1] - ze)))
                return all(niv[int(round(xe + (c[0] - xe) * t / n_)), int(round(ze + (c[1] - ze) * t / n_))]
                           for t in range(1, n_)) if n_ > 1 else True

            score = np.sqrt(((cand - [cx, cz]) ** 2).sum(axis=1))
            for j in np.argsort(score)[:400]:
                if not vue_degagee(cand[j]):
                    score[j] += 1000
            j = int(np.argmin(score))
            bl = (int(cand[j][0]), yl, int(cand[j][1]))
            origine = "au cœur de la grande zone du spawn, avec de la place autour" + (
                ", visible en sortant du tunnel" if score[j] < 1000 else "")
        bx, by, bz = x0s + bl[0], y0s + bl[1], z0s + bl[2]
        dx, dz = arrivee[0] - bx, arrivee[2] - bz
        face = ("east" if dx > 0 else "west") if abs(dx) >= abs(dz) else ("south" if dz > 0 else "north")
        m.poser(bx, by, bz, E("heralisz_utils:borne_depart", facing=face))
        m.blocs_entites.pop((bx, by, bz), None)
        if m.lire(bx, by + 2, bz) == 0:
            m.poser(bx, by + 2, bz, E("light", level=10))
    # point de vue des rendus : sur le chemin de l'arrivée du tunnel à la borne, à 5 à 9 blocs d'elle, pieds et tête libres
    oeil = [arrivee[0] + 0.5, arrivee[1] + 1.62, arrivee[2] + 0.5]
    dist = math.hypot(arrivee[0] - bx, arrivee[2] - bz)
    for r in (6, 7, 5, 8, 9):
        if r >= dist:
            continue
        px, pz = bx + 0.5 + (arrivee[0] - bx) * r / dist, bz + 0.5 + (arrivee[2] - bz) * r / dist
        cx_, cz_ = math.floor(px), math.floor(pz)
        if all(passe(m.lire(cx_, by + d, cz_)) for d in (0, 1)) and not passe(m.lire(cx_, by - 1, cz_)):
            oeil = [px, by + 1.62, pz]
            break

    # bordure du monde du spawn : centrée sur le point de réveil, elle doit tout contenir (marge de 8)
    xr, zr = info["reveil"][0] + 0.5, info["reveil"][2] + 0.5
    e0 = info["emprise"]
    xmin, xmax = min(e0["x"][0], sx), max(e0["x"][1], x0s + W - 1)
    zmin, zmax = min(e0["z"][0], z0s, sz - 2), max(e0["z"][1], z0s + LZ - 1, sz + 2)
    demi = max(xr - xmin, xmax + 1 - xr, zr - zmin, zmax + 1 - zr) + 8
    bord = max(BORDURE_DEFAUT, int(math.ceil(2 * demi / 32.0)) * 32)
    info.update({
        "source": "composition : grotte du terminus + schematic %s (outils/monde_spawn.py composer)" % Path(chemin).name,
        "emprise": {"x": [xmin, xmax], "y": [min(e0["y"][0], y0s, min(haut) - 1), max(e0["y"][1], y0s + H - 1)],
                    "z": [zmin, zmax]},
        "borne": {"x": bx, "y": by, "z": bz, "facing": face, "origine": origine},
        "oeil_borne": oeil,
        "tunnel": {"de": [x_deb, sy, sz], "a": [x0s - 1, yw, sz], "longueur": n, "montee": montee, "arrivee": list(arrivee)},
        "schematic": {"fichier": Path(chemin).name, "format": sc.format, "taille": [W, H, LZ], "position": [x0s, y0s, z0s],
                      "niveau_de_marche": yw, "entree_rangee_z": az, "blocs_retires": retires, "sols_ajoutes": ajoutes,
                      "entites_de_blocs": nbe, "entites": len(sc.entites), "entites_ignorees": sc.entites_ignorees},
        "bordure_conseillee": bord,
        "bordure_suffit": bord <= BORDURE_DEFAUT,
        "centre_bordure": [xr, zr],
    })
    return info


def ecrire_config_bordure(chemin, taille):
    """[spawn] bordure de heraliz_spawn-server.toml (defaultconfigs du dépôt) : seule cette valeur est écrite ; Forge
    complète le reste du fichier avec les valeurs par défaut au chargement."""
    import re
    p = Path(chemin)
    texte = p.read_text(encoding="utf-8") if p.exists() else ""
    if re.search(r"(?m)^\s*bordure\s*=", texte):
        texte = re.sub(r"(?m)^(\s*bordure\s*=\s*)\d+", r"\g<1>%d" % taille, texte)
    elif "[spawn]" in texte:
        texte = texte.replace("[spawn]", "[spawn]\n\tbordure = %d" % taille, 1)
    else:
        texte += ("\n" if texte else "") + (
            "# Monde du spawn composé par outils/monde_spawn.py composer : bordure agrandie pour contenir la grotte, le\n"
            "# tunnel et le schematic du spawn (centrée sur le point de réveil). Les autres réglages gardent leur défaut.\n"
            "[spawn]\n\tbordure = %d\n" % taille)
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(texte, encoding="utf-8")


# ================================================================== rendus de contrôle
FIXES = {"minecraft:barrier": (150, 150, 158), "minecraft:grass_block": (92, 128, 58), "minecraft:water": (45, 75, 170), "minecraft:chain": (60, 64, 72), "minecraft:lantern": (250, 200, 110),
         "minecraft:redstone_torch": (230, 40, 30), "minecraft:pointed_dripstone": (130, 105, 90),
         "minecraft:hanging_roots": (150, 110, 80)}
VIDES = ("minecraft:air", "minecraft:cave_air", "minecraft:light")
TEXTURES_MOD = {"heralisz_utils:borne_depart":
                DEPOT / "mods/heralisz-utils/src/client/resources/assets/heralisz_utils/textures/block/borne_depart_ecran.png"}


_TEX_MODS = None


def textures_des_mods():
    """{espace de noms: [dossiers textures/block]} de nos mods (sources du dépôt), pour les rendus de leurs blocs."""
    global _TEX_MODS
    if _TEX_MODS is None:
        _TEX_MODS = {}
        for d in sorted(DEPOT.glob("mods/*/src/*/resources/assets/*/textures/block")):
            _TEX_MODS.setdefault(d.parent.parent.name, []).append(d)
    return _TEX_MODS


def couleur_par_nom(n):
    """Couleur approchée d'un bloc sans texture connue (rendus seulement) ; magenta : inconnu."""
    nom = n.split(":")[-1]
    for cles, c in ((("water",), (50, 80, 200)), (("lava",), (220, 100, 30)), (("leaves",), (60, 110, 45)),
                    (("glass", "pane"), (170, 200, 215)), (("oak", "spruce", "birch", "jungle", "acacia", "mangrove",
                                                            "planks", "log", "wood", "crate", "barrel"), (125, 95, 60)),
                    (("iron", "metal", "steel", "tole", "grillage", "fence"), (150, 150, 155)),
                    (("concrete", "beton", "cement"), (140, 140, 140)), (("brick",), (150, 85, 70)),
                    (("stone", "cobble", "andesite", "deepslate", "tuff", "gravel", "rock"), (120, 120, 118)),
                    (("sand", "sable"), (210, 195, 140)), (("dirt", "mud", "terre"), (110, 80, 55)),
                    (("grass", "herbe", "moss"), (90, 130, 60)), (("snow", "ice", "neige"), (225, 235, 240)),
                    (("wool", "carpet"), (200, 200, 200)), (("lamp", "lantern", "light", "lampe"), (240, 200, 120))):
        if any(k in nom for k in cles):
            return c
    return (180, 60, 180)


def textures_blocs(noms):
    """Pour chaque bloc : (couleur moyenne, texture 16×16×3 ou None). Textures du pack imposé (ce que voient les
    joueurs), sinon une couleur fixe ; (None, None) pour l'air."""
    from PIL import Image
    out = []
    for n in noms:
        if n in VIDES:
            out.append((None, None))
            continue
        f = TEXTURES_MOD.get(n)
        if f is None or not f.exists():
            f = None
            ns, _, nom = n.partition(":")
            base = nom
            for suffixe in ("_slab", "_stairs", "_wall", "_fence_gate", "_fence", "_pressure_plate", "_button"):
                base = base.replace(suffixe, "")
            cands = (nom, base, base + "_side", base + "_top", base + "s", base.replace("bricks", "brick"), base + "_bottom",
                     base + "_planks", base.replace("brick", "bricks"))
            dossiers = [PACK] if ns == "minecraft" else [d for d in textures_des_mods().get(ns, [])]
            for cand in cands:
                for d in dossiers:
                    if (d / (cand + ".png")).exists():
                        f = d / (cand + ".png")
                        break
                if f is not None:
                    break
        if n in FIXES or f is None:
            out.append((FIXES.get(n, couleur_par_nom(n)), None))
            continue
        im = np.asarray(Image.open(f).convert("RGBA").crop((0, 0, 16, 16)).resize((16, 16)), dtype=np.float64)
        a = im[..., 3:4] / 255.0
        moy = (im[..., :3] * a).sum((0, 1)) / max(a.sum(), 1e-6)
        out.append((tuple(int(v) for v in moy), im[..., :3] * a + moy * (1 - a)))
    return out


def police(taille):
    from PIL import ImageFont
    try:
        return ImageFont.truetype("DejaVuSans.ttf", taille)
    except Exception:
        return ImageFont.load_default()


def tableau(m):
    """Le Monde en un tableau x, y, z d'états (emprise des sections non vides)."""
    cles = [k for k, s in m.sections.items() if s.any()]
    cx0, cx1 = min(k[0] for k in cles), max(k[0] for k in cles)
    sy0, sy1 = min(k[1] for k in cles), max(k[1] for k in cles)
    cz0, cz1 = min(k[2] for k in cles), max(k[2] for k in cles)
    A = np.zeros(((cx1 - cx0 + 1) * 16, (sy1 - sy0 + 1) * 16, (cz1 - cz0 + 1) * 16), dtype=np.uint16)
    for (cx, sy, cz), s in m.sections.items():
        A[(cx - cx0) * 16:(cx - cx0 + 1) * 16, (sy - sy0) * 16:(sy - sy0 + 1) * 16, (cz - cz0) * 16:(cz - cz0 + 1) * 16] = \
            s.transpose(2, 0, 1)
    return A, (cx0 * 16, sy0 * 16, cz0 * 16)


def lire_dimension(dossier):
    m = Monde()
    for f in sorted(Path(dossier, "region").glob("r.*.mca")):
        for (lx, lz), t in region_lire(f).items():
            cx, cz = t["xPos"].v, t["zPos"].v
            for sec in t["sections"].v[1]:
                pal, idx = decoder_section(sec)
                if pal is None:
                    continue
                loc = np.array([m.etat(p["Name"].v, **({k: x.v for k, x in p["Properties"].v.items()} if p.get("Properties") else {}))
                                for p in pal], dtype=np.uint16)
                s = loc[idx].reshape(16, 16, 16)
                if s.any():
                    m.sections[(cx, sec["Y"].v, cz)] = s
    return m


SOURCES = {"minecraft:lantern": 15, "minecraft:verdant_froglight": 15, "minecraft:redstone_torch": 7,
           "heralisz_utils:borne_depart": 7, "minecraft:glowstone": 15, "minecraft:sea_lantern": 15}
PASSE = ("minecraft:air", "minecraft:light", "minecraft:barrier", "minecraft:chain", "minecraft:rail", "minecraft:lantern",
         "minecraft:redstone_torch", "minecraft:pointed_dripstone", "minecraft:hanging_roots", "heralisz_utils:borne_depart",
         "minecraft:iron_door")


LUNE = 6               # lumière du ciel de nuit dans les rendus (le jeu la montre à peu près ainsi)


def lumiere(A, etats):
    """Lumière des blocs approchée (sources, -1 par bloc, arrêtée par les blocs pleins) : pour les rendus seulement."""
    vals = np.array([SOURCES.get(n, int(dict(p).get("level", 0)) if n == "minecraft:light" else 0) for n, p in etats])
    opaque = np.array([n not in PASSE and not n.endswith("_slab") and not traversable(n, dict(p)) for n, p in etats])
    bloque = opaque[A]
    # ciel de nuit (le monde du spawn est figé à minuit) : les cases à ciel ouvert reçoivent une lumière de lune
    couvert = np.flip(np.maximum.accumulate(np.flip(bloque, 1), axis=1), 1)
    src = np.maximum(vals[A], np.where(couvert, 0, LUNE)).astype(np.int16)
    Lm = src.copy()
    for _ in range(15):
        n = Lm.copy()
        for ax in range(3):
            for d in (-1, 1):
                n = np.maximum(n, np.roll(Lm, d, ax) - 1)
        n[bloque] = 0
        n = np.maximum(n, src)
        if (n == Lm).all():
            break
        Lm = n
    return Lm


def legende(im, texte):
    from PIL import ImageDraw
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, im.width, 30), fill=(10, 12, 16))
    d.text((10, 5), texte, fill=(235, 235, 235), font=police(18))
    return d


def rendus(m, dossier, info):
    from PIL import Image
    os.makedirs(dossier, exist_ok=True)
    A, (ox, oy, oz) = tableau(m)
    tx = textures_blocs([e[0] for e in m.etats])
    pal = np.array([c or (0, 0, 0) for c, t in tx], dtype=np.float64)
    air = np.array([c is None for c, t in tx])
    Lm = lumiere(A, m.etats)
    nx, ny, nz = A.shape
    sol = info["reveil"][1] if "reveil" in info else oy + ny // 2
    # 1. vue de dessus, plafond retiré : pour chaque colonne, le premier bloc solide sous le vide le plus haut
    solide = ~air[A]
    img = np.zeros((nz, nx, 3))
    for i in range(nx):
        for k in range(nz):
            col = solide[i, :, k]
            if not col.any():
                continue
            js = np.nonzero(col)[0]
            j = js.max()
            while j >= 0 and col[j]:
                j -= 1
            while j >= 0 and not col[j]:
                j -= 1
            if j < 0:
                j = js.max()
            h = (j + oy - sol) / 25.0
            lum = 0.55 + 0.45 * Lm[i, min(j + 1, ny - 1), k] / 15.0
            img[k, i] = np.clip(pal[A[i, j, k]] * (0.85 + 0.5 * h) * lum, 0, 255)
    e = max(2, min(8, 1400 // max(nx, nz)))      # petites images : 1400 px au plus de côté
    im = Image.fromarray(img.astype(np.uint8)).resize((nx * e, nz * e), Image.NEAREST)
    canvas = Image.new("RGB", (max(im.width, 900), im.height + 34), (10, 12, 16))
    canvas.paste(im, (0, 34))
    d = legende(canvas, "Vue de dessus, plafond retiré (nord en haut) — x %d à %d, z %d à %d" % (ox, ox + nx - 1, oz, oz + nz - 1))
    f = police(16)
    if "reveil" in info:
        rx, rz = info["reveil"][0] - ox, info["reveil"][2] - oz
        d.ellipse((rx * e - 6, 34 + rz * e - 6, rx * e + e + 6, 34 + rz * e + e + 6), outline=(255, 70, 70), width=3)
        d.text((rx * e + 16, 34 + rz * e - 6), "réveil", fill=(255, 110, 110), font=f)
    if "borne" in info:
        bx, bz = info["borne"]["x"] - ox, info["borne"]["z"] - oz
        d.rectangle((bx * e - 3, 34 + bz * e - 3, bx * e + e + 3, 34 + bz * e + e + 3), outline=(80, 255, 140), width=3)
        d.text((bx * e - 4, 34 + bz * e + 14), "borne", fill=(80, 255, 140), font=f)
    if "tunnel" in info:
        for cle, nom in (("de", "tunnel"), ("arrivee", "arrivée")):
            tx_, tz_ = info["tunnel"][cle][0] - ox, info["tunnel"][cle][2] - oz
            d.ellipse((tx_ * e - 5, 34 + tz_ * e - 5, tx_ * e + e + 5, 34 + tz_ * e + e + 5), outline=(255, 210, 80), width=2)
            d.text((tx_ * e - 10, 34 + tz_ * e - 26), nom, fill=(255, 210, 80), font=f)
    canvas.save(os.path.join(dossier, "spawn_dessus.png"))
    # 2. coupe de côté : tout ce qui est à l'est de l'axe de la voie est retiré, on regarde vers l'ouest
    if "reveil" in info:      # grotte générée : axe de la voie ; copie : à travers le point de réveil
        xc = (int(math.floor(XC)) if str(info.get("source", "")).startswith(("grotte", "composition"))
              else int(info["reveil"][0])) - ox
        xc = min(max(xc, 0), nx - 1)
    else:
        xc = nx // 2
    e = max(2, min(8, 1400 // max(nz, ny)))
    img = np.zeros((ny, nz, 3))
    for k in range(nz):
        for j in range(ny):
            for i in range(xc, -1, -1):
                b = A[i, j, k]
                if not air[b]:
                    lum = 0.3 + 0.7 * Lm[min(i + 1, nx - 1), j, k] / 15.0
                    img[ny - 1 - j, k] = pal[b] * (lum if i < xc else 0.45)
                    break
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((nz * e, ny * e), Image.NEAREST)
    canvas = Image.new("RGB", (im.width, im.height + 34), (10, 12, 16))
    canvas.paste(im, (0, 34))
    legende(canvas, "Coupe de côté le long de la voie (moitié est retirée, on regarde vers l'ouest, sud à droite)")
    canvas.save(os.path.join(dossier, "spawn_cote.png"))
    # 2 bis. coupe le long du tunnel du spawn : moitié sud retirée, on regarde vers le nord (est à droite)
    if "tunnel" in info:
        zc = min(max(info["tunnel"]["de"][2] - oz, 0), nz - 1)
        e = max(2, min(8, 1400 // max(nx, ny)))
        img = np.zeros((ny, nx, 3))
        for i in range(nx):
            for j in range(ny):
                for k in range(zc, -1, -1):
                    b = A[i, j, k]
                    if not air[b]:
                        lum = 0.3 + 0.7 * Lm[i, j, min(k + 1, nz - 1)] / 15.0
                        img[ny - 1 - j, i] = pal[b] * (lum if k < zc else 0.45)
                        break
        im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8)).resize((nx * e, ny * e), Image.NEAREST)
        canvas = Image.new("RGB", (max(im.width, 900), im.height + 34), (10, 12, 16))
        canvas.paste(im, (0, 34))
        legende(canvas, "Coupe le long du tunnel (grotte à gauche, spawn à droite ; on regarde vers le nord)")
        canvas.save(os.path.join(dossier, "spawn_tunnel.png"))
    # 3. vues du joueur : au réveil, et tourné vers la borne
    if "reveil" in info:
        vue_joueur(A, (ox, oy, oz), tx, air, Lm, info, os.path.join(dossier, "spawn_reveil.png"))
        if "borne" in info:
            vue_joueur(A, (ox, oy, oz), tx, air, Lm, info, os.path.join(dossier, "spawn_borne.png"), vers_borne=True)


def vue_joueur(A, o, tx, air, Lm, info, chemin, vers_borne=False):
    """Lancer de rayons depuis les yeux du joueur (champ de 70°), textures du pack, lumière approchée."""
    from PIL import Image
    W, H, fov = 640, 360, 70.0
    ex, ey, ez = info["reveil"][0] + 0.5, info["reveil"][1] + 1.62, info["reveil"][2] + 0.5
    yaw = math.radians(info.get("cap", 0))
    if vers_borne:
        b = info["borne"]
        if info.get("oeil_borne"):
            ex, ey, ez = info["oeil_borne"]
        else:
            ex, ez = b["x"] - 7.5, b["z"] + 0.5
        yaw = math.atan2(-(b["x"] + 0.5 - ex), b["z"] + 0.5 - ez)
    pitch = math.radians(8)
    fwd = np.array([-math.sin(yaw) * math.cos(pitch), -math.sin(pitch), math.cos(yaw) * math.cos(pitch)])
    right = np.array([-math.cos(yaw), 0, -math.sin(yaw)])
    up = np.cross(right, fwd)
    t = math.tan(math.radians(fov) / 2)
    U, V = np.meshgrid(((np.arange(W) + 0.5) / W * 2 - 1) * t * W / H, (1 - (np.arange(H) + 0.5) / H * 2) * t)
    D = fwd[None, None, :] + U[..., None] * right[None, None, :] + V[..., None] * up[None, None, :]
    D = (D / np.linalg.norm(D, axis=-1, keepdims=True)).reshape(-1, 3)
    oeil = np.array([ex, ey, ez])
    orig = np.array(o)
    P = np.tile(oeil, (D.shape[0], 1))
    out = np.zeros((D.shape[0], 3))
    fini = np.zeros(D.shape[0], dtype=bool)
    nx, ny, nz = A.shape
    texs = [t_ for c, t_ in tx]
    moy = np.array([c or (0, 0, 0) for c, t_ in tx], dtype=np.float64)
    pas = 0.04
    prev_cell = np.floor(P - orig).astype(int)
    for _ in range(int(80 / pas)):
        P = P + D * pas
        cell = np.floor(P - orig).astype(int)
        dedans = (cell[:, 0] >= 0) & (cell[:, 0] < nx) & (cell[:, 1] >= 0) & (cell[:, 1] < ny) & (cell[:, 2] >= 0) & (cell[:, 2] < nz)
        act = ~fini & dedans
        if not act.any():
            break
        idx = np.nonzero(act)[0]
        b = A[cell[idx, 0], cell[idx, 1], cell[idx, 2]]
        touche = ~air[b]
        if touche.any():
            ti, bt = idx[touche], b[touche]
            pc = np.clip(prev_cell[ti], 0, np.array([nx - 1, ny - 1, nz - 1]))
            axe = np.argmax(np.abs(cell[ti] - prev_cell[ti]), axis=1)          # face traversée : 0 x, 1 y, 2 z
            lum = Lm[pc[:, 0], pc[:, 1], pc[:, 2]] / 15.0
            dist = np.linalg.norm(P[ti] - oeil, axis=1)
            fr = P[ti] - np.floor(P[ti])
            uu = np.where(axe == 0, fr[:, 2], fr[:, 0])
            vv = np.where(axe == 1, fr[:, 2], 1 - fr[:, 1])
            px = np.clip((uu * 16).astype(int), 0, 15)
            py = np.clip((vv * 16).astype(int), 0, 15)
            coul = moy[bt].copy()
            for s in np.unique(bt):
                tex = texs[s]
                if tex is None:
                    continue
                q = bt == s
                coul[q] = tex[py[q], px[q]]
            face = np.where(axe == 1, np.where(D[ti, 1] < 0, 1.0, 0.55), np.where(axe == 0, 0.8, 0.68))
            k = (0.16 + 0.84 * lum ** 1.3) * face * np.exp(-dist / 120.0)
            out[ti] = coul * k[:, None]
            fini[ti] = True
        prev_cell = np.where(act[:, None], cell, prev_cell)
    img = np.clip(out.reshape(H, W, 3) * 1.25, 0, 255).astype(np.uint8)
    im = Image.fromarray(img).resize((W * 2, H * 2), Image.NEAREST)
    canvas = Image.new("RGB", (im.width, im.height + 34), (10, 12, 16))
    canvas.paste(im, (0, 34))
    if vers_borne and info.get("oeil_borne"):
        legende(canvas, "En arrivant du tunnel, face à la borne de départ (dans la partie spawn)")
    else:
        legende(canvas, ("Face à la borne de départ" if vers_borne else "Au réveil, regard de la cinématique")
                + " — volumes gris : place de l'épave (le train lui-même est dessiné par le jeu)")
    canvas.save(chemin)


# ================================================================== commande
def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("mode", choices=["grotte", "copier", "rendu", "schematic", "composer"])
    ap.add_argument("monde", nargs="?")
    ap.add_argument("--zone", nargs=6, type=int)
    ap.add_argument("--dimension", default="minecraft:overworld")
    ap.add_argument("--decalage", nargs=3, type=int, default=[0, 0, 0])
    ap.add_argument("--sortie", default=str(DEPOT / "livraison/monde_spawn"))
    ap.add_argument("--rendus")
    ap.add_argument("--reveil", nargs=3, type=int)
    ap.add_argument("--cap", type=float)
    ap.add_argument("--position", nargs=3, type=int, help="composer : coin bas nord-ouest du schematic (sinon automatique)")
    ap.add_argument("--borne", nargs=3, type=int, help="composer : place de la borne dans le repère du schematic")
    ap.add_argument("--ecart", type=int, default=ECART, help="composer : longueur minimale du tunnel")
    ap.add_argument("--rapport", help="schematic, composer : rapport Markdown du schematic")
    ap.add_argument("--config-bordure", help="composer : heraliz_spawn-server.toml à régler si la bordure doit grandir")
    a = ap.parse_args()
    if a.mode == "schematic":
        if not a.monde:
            ap.error("schematic : fichier du schematic attendu")
        info = rapport_schematic(lire_schematic(a.monde), a.monde, a.rapport)
        print(json.dumps(info, ensure_ascii=False, indent=1))
        return
    if a.mode == "rendu":
        if not a.monde or not a.rendus:
            ap.error("rendu : dossier de la dimension et --rendus attendus")
        m = lire_dimension(a.monde)
        info = {}
        f = Path(a.monde, "heraliz_spawn.json")
        if f.exists():
            info = json.loads(f.read_text(encoding="utf-8"))
        rendus(m, a.rendus, info)
        print("[spawn] rendus dans %s" % a.rendus)
        return
    m = Monde()
    if a.mode == "grotte":
        info = generer_grotte(m)
    elif a.mode == "composer":
        if not a.monde:
            ap.error("composer : fichier du schematic attendu")
        sc = lire_schematic(a.monde)
        if a.rapport:
            rapport_schematic(sc, a.monde, a.rapport)
        info = composer(m, sc, a.monde, a.position, a.borne, a.ecart)
        if not info["bordure_suffit"]:
            print("[spawn] ATTENTION : bordure du monde du spawn à agrandir : %d blocs (défaut %d)" % (info["bordure_conseillee"], BORDURE_DEFAUT))
            if a.config_bordure:
                ecrire_config_bordure(a.config_bordure, info["bordure_conseillee"])
                print("[spawn]   écrite dans %s" % a.config_bordure)
    else:
        if not a.monde or not a.zone:
            ap.error("copier : dossier du monde et --zone X0 Y0 Z0 X1 Y1 Z1 attendus")
        info = copier_zone(m, a.monde, a.dimension, a.zone, a.decalage)
        if a.reveil:
            xr, yr, zr = a.reveil
            info["reveil"] = list(a.reveil)
            info["cap"] = a.cap if a.cap is not None else json.loads(CINE_JSON.read_text(encoding="utf-8")).get("cap", 0)

            def nom(x, y, z):
                return m.etats[m.lire(x, y, z)][0]

            libres = ("minecraft:air", "minecraft:cave_air", "minecraft:light")
            info["reveil_praticable"] = nom(xr, yr, zr) in libres and nom(xr, yr + 1, zr) in libres \
                and nom(xr, yr - 1, zr) not in libres
            if not info["reveil_praticable"]:
                print("[spawn] ATTENTION : réveil %s pas praticable dans la copie (pieds %s, tête %s, sol %s)" % (
                    a.reveil, nom(xr, yr, zr), nom(xr, yr + 1, zr), nom(xr, yr - 1, zr)))
    dossier = Path(a.sortie) / "spawn"
    if dossier.exists():
        import shutil
        shutil.rmtree(dossier)
    n = m.ecrire(str(dossier))
    info["chunks"] = n
    info["dimension"] = "heralisz:spawn"
    info["fabrique_par"] = "outils/monde_spawn.py %s" % a.mode
    (dossier / "heraliz_spawn.json").write_text(json.dumps(info, ensure_ascii=False, indent=1), encoding="utf-8")
    taille = sum(f.stat().st_size for f in dossier.rglob("*") if f.is_file())
    print("[spawn] %s : %d chunks, %d Ko -> %s" % (info["source"], n, taille // 1024, dossier))
    for k in ("reveil", "reveil_praticable", "borne", "barrieres_epave", "epave_dans_la_roche", "emprise", "blocs", "entites",
              "tunnel", "schematic", "bordure_conseillee"):
        if k in info:
            print("[spawn]   %s : %s" % (k, info[k]))
    if a.rendus:
        rendus(m, a.rendus, info)
        print("[spawn] rendus dans %s" % a.rendus)


if __name__ == "__main__":
    main()
