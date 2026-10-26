"""Monde des bases de CRac (dimension crac:bases) : écrit ses fichiers de génération, jamais retouchés à la main.

    python modules/bases/outils/monde_bases.py                         écrit src/serveur/resources/data/crac/...
    python modules/bases/outils/monde_bases.py --vanilla DOSSIER       lit les fichiers de Minecraft 1.20.1 dans DOSSIER
    python modules/bases/outils/monde_bases.py --apercu DOSSIER        aperçus du terrain (numpy + Pillow), voir plus bas
    python modules/bases/outils/monde_bases.py --apercu DOSSIER --graine 3   autre tirage du bruit pour l'aperçu

Terrain (CRac 1.6.0) : un générateur « noise » de Minecraft, mais avec un relief fait maison, en 2 dimensions : la
hauteur du sol H(x, z) est une somme de courbes (« spline ») de quelques bruits, et la densité finale vaut H - y
(plein sous H, vide au-dessus). Pas de fromage, pas de spaghetti : aucune grotte, aucun surplomb.
- plateaux : un bruit lent choisit pour chaque grande zone UNE hauteur parmi NIVEAUX (des demi-blocs, pour que le sol
  tombe toujours sur le même bloc) ; entre deux niveaux, une pente douce (courbe en S, dérivée nulle aux deux bouts).
  Sur un plateau, H est une constante exacte : sol parfaitement plat, pas une marche ;
- collines : un deuxième bruit ; sous le seuil COLLINES[0], rien (exactement 0) ; au-dessus, une colline arrondie,
  basse (COLLINES) ; un petit bruit de détail ne joue que sur les collines et les reliefs (masque nul ailleurs) ;
- reliefs : un troisième bruit, très lent, à seuil haut : 2 ou 3 grands reliefs lisses sur la carte de 2 500 blocs ;
- aucune eau : ni océan, ni rivière, ni lac, ni source, ni nappe (aquifères coupés, niveau de la mer au fond du monde,
  fluide par défaut = air) ; aucune lave ; aucun minerai ; aucune structure ;
- biomes propres à CRac, copiés de ceux de Minecraft (plaine, prairie à tournesols, forêt claire de chênes et bouleaux,
  bouleaux, savane, prairie fleurie des collines, taïga légère) : mêmes herbes et fleurs, arbres moins serrés en forêt,
  bouleaux et taïga ; aucun minerai, lac, source, grotte, donjon ; AUCUNE créature (ni animal, ni monstre : listes
  d'apparition vides, probabilité d'apparition nulle, pas d'apparition à la génération) ; ils ne sont dans aucune liste
  de structures ni dans « is_overworld ». Choix du biome : température et humidité (bruits propres) et « érosion » =
  le bruit des collines (prairie fleurie sur les collines et les reliefs).

Aperçu (--apercu) : le script évalue LES MÊMES fichiers JSON qu'il vient d'écrire (interpréteur des fonctions de
densité utilisées, bruit de Perlin et « normal noise » de Minecraft 1.20.1 recodés, interpolation par cellules de 4
blocs, choix du biome au plus proche comme Minecraft). Seul le tirage au hasard du bruit diffère (Minecraft le tire de
la graine du monde, que l'on ne connaît pas ici) : formes, tailles, pentes et proportions sont les vraies, pas
l'emplacement exact. Ni arbres ni herbes dans l'aperçu (couleur du biome seulement).
Fichiers : carte.png (2 500 × 2 500 blocs, 1 px = 4 blocs, relief ombré + biomes), hauteurs.png (hauteurs en
niveaux de gris), biomes.png, vue_3d_ordinaire.png et vue_3d_relief.png
(512 × 512 blocs en perspective cavalière, hauteurs ×2 : un carré ordinaire et le plus vallonné de la carte),
detail_ordinaire.png et detail_relief.png (les mêmes carrés vus de dessus, 1 px = 1 bloc), mesures.txt.

Les fichiers de Minecraft sont lus dans --vanilla, sinon téléchargés (miroir public misode/mcmeta, branche 1.20.1-data).
"""
import argparse
import json
import math
import os
import sys
import urllib.request

ICI = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(ICI, "..", "src", "serveur", "resources", "data", "crac")
MIROIR = "https://raw.githubusercontent.com/misode/mcmeta/1.20.1-data/data/minecraft/"

# ---------------------------------------------------------------------------------------------- réglages du relief
# bruits propres au monde des bases : (nom, premier octave, amplitudes) ; période du premier octave = 2^-octave blocs
BRUITS = {
    "plateaux": (-9, [1.0, 0.6, 0.25]),      # zones de 300 à 600 blocs
    "collines": (-8, [1.0, 0.5, 0.25]),      # collines de 80 à 200 blocs
    "reliefs": (-10, [1.0, 0.3]),            # 2 ou 3 grands reliefs sur 2 500 blocs (seuil haut)
    "detail": (-6, [1.0, 0.5]),              # petites bosses sur les collines et les reliefs seulement
    "temperature": (-9, [1.0, 0.5]),
    "humidite": (-8, [1.0, 0.5]),
}
# hauteurs des plateaux (demi-blocs : le sol est le bloc juste en dessous) et seuils du bruit « plateaux » entre eux :
# plateau i de SEUILS[i-1]+PENTE/2 à SEUILS[i]-PENTE/2, pente douce de largeur PENTE autour de chaque seuil
NIVEAUX = [67.5, 71.5, 75.5]
SEUILS = [-0.22, 0.24]
PENTE = 0.14
# collines : (valeur du bruit, hauteur ajoutée) ; dérivée nulle au pied et au sommet (formes arrondies)
COLLINES = [(0.24, 0.0), (0.36, 4.0), (0.50, 10.0), (0.66, 16.0)]
# reliefs : idem, plus hauts
RELIEFS = [(0.28, 0.0), (0.36, 10.0), (0.46, 24.0), (0.58, 34.0), (0.70, 38.0)]
DETAIL = 1.5            # amplitude du détail (blocs) là où le masque vaut 1
MASQUE = 0.25           # masque du détail = clamp((collines + reliefs) × MASQUE, 0, 1)
FREQUENCE = 1.0         # xz_scale des bruits du relief

# biome du monde des bases -> biome de Minecraft copié, (température, humidité, érosion = bruit des collines)
COLLINE_BIOME = 0.40    # bruit des collines au-dessus duquel on passe en prairie fleurie (sommets)
BIOMES = [
    ("bases_plaine", "plains", [-0.3, 0.35], [-1.0, -0.12], [-1.0, COLLINE_BIOME]),
    ("bases_prairie", "sunflower_plains", [-0.3, 0.35], [-0.12, 0.12], [-1.0, COLLINE_BIOME]),
    ("bases_foret", "forest", [-0.3, 0.05], [0.12, 1.0], [-1.0, COLLINE_BIOME]),
    ("bases_bouleaux", "birch_forest", [0.05, 0.35], [0.12, 1.0], [-1.0, COLLINE_BIOME]),
    ("bases_savane", "savanna", [0.35, 1.0], [-1.0, 1.0], [-1.0, COLLINE_BIOME]),
    ("bases_collines", "meadow", [-1.0, 1.0], [-1.0, 1.0], [COLLINE_BIOME, 1.0]),
    ("bases_taiga", "taiga", [-1.0, -0.3], [-1.0, 1.0], [-1.0, COLLINE_BIOME]),
]
# arbres moins serrés : biome -> (arbres de Minecraft remplacés, nombre par tronçon au lieu de 10)
ARBRES = {
    "bases_foret": ("minecraft:trees_birch_and_oak", 5),
    "bases_bouleaux": ("minecraft:trees_birch", 6),
    "bases_taiga": ("minecraft:trees_taiga", 4),
}
# décorations retirées (sous terre, ou qui veulent de l'eau)
SANS = {"minecraft:glow_lichen", "minecraft:patch_sugar_cane"}

# anciens fichiers (CRac 1.5.0 : relief copié de l'overworld), retirés à chaque écriture
ANCIENS = ["continents", "erosion", "offset", "factor", "jaggedness", "depth", "sloped_cheese"]


# Lumière de fond du monde des bases (0 = noir comme le monde normal, 1 = plein jour partout) : assez pour voir
# l'intérieur d'une base fermée et la nuit, sans enlever la nuit.
LUMIERE_FOND = 0.3

def lire(vanilla, chemin):
    nom = chemin.replace("/", "_") + ".json"
    if vanilla:
        with open(os.path.join(vanilla, nom), encoding="utf-8") as f:
            return json.load(f)
    with urllib.request.urlopen(MIROIR + chemin + ".json", timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def ecrire(chemin, obj):
    complet = os.path.join(DATA, chemin)
    os.makedirs(os.path.dirname(complet), exist_ok=True)
    with open(complet, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("écrit", os.path.relpath(complet, os.path.join(ICI, "..")))


# ---------------------------------------------------------------------------------------------- fonctions de densité
def bruit(nom):
    return {"type": "minecraft:noise", "noise": "crac:bases/" + nom, "xz_scale": FREQUENCE, "y_scale": 0.0}


def point(loc, val, der=0.0):
    return {"location": round(loc, 4), "value": val, "derivative": der}


def courbe(coord, points):
    return {"type": "minecraft:spline", "spline": {"coordinate": coord, "points": points}}


def plateaux():
    pts = [point(-1.0, NIVEAUX[0])]
    for i, s in enumerate(SEUILS):
        pts.append(point(s - PENTE / 2, NIVEAUX[i]))
        pts.append(point(s + PENTE / 2, NIVEAUX[i + 1]))
    pts.append(point(1.0, NIVEAUX[-1]))
    return courbe("crac:bases/bruit_plateaux", pts)


def bosses(nom, table):
    pts = [point(-1.0, 0.0)] + [point(v, h) for v, h in table]
    if table[-1][0] < 1.0:
        pts.append(point(1.0, table[-1][1]))
    return courbe("crac:bases/bruit_" + nom, pts)


def fonctions():
    """Toutes les fonctions de densité du monde des bases : nom -> objet JSON."""
    f = {}
    for nom in ("plateaux", "collines", "reliefs", "detail"):
        f["bruit_" + nom] = bruit(nom)
    f["plateaux"] = plateaux()
    f["collines"] = bosses("collines", COLLINES)
    f["reliefs"] = bosses("reliefs", RELIEFS)
    relief = {"type": "minecraft:add", "argument1": "crac:bases/collines", "argument2": "crac:bases/reliefs"}
    masque = {"type": "minecraft:clamp", "min": 0.0, "max": 1.0,
              "input": {"type": "minecraft:mul", "argument1": MASQUE, "argument2": relief}}
    detail = {"type": "minecraft:mul", "argument1": masque,
              "argument2": {"type": "minecraft:mul", "argument1": DETAIL, "argument2": "crac:bases/bruit_detail"}}
    f["hauteur"] = {"type": "minecraft:cache_2d", "argument": {
        "type": "minecraft:add", "argument1": "crac:bases/plateaux",
        "argument2": {"type": "minecraft:add", "argument1": relief, "argument2": detail}}}
    # densité : H - y (y_clamped_gradient donne -y entre -64 et 320)
    moins_y = {"type": "minecraft:y_clamped_gradient", "from_y": -64, "to_y": 320, "from_value": 64.0, "to_value": -320.0}
    f["terrain"] = {"type": "minecraft:add", "argument1": "crac:bases/hauteur", "argument2": moins_y}
    # « érosion » du choix des biomes : le bruit des collines, poussé vers le haut sur les reliefs
    f["climat_erosion"] = {"type": "minecraft:flat_cache", "argument": {
        "type": "minecraft:max", "argument1": "crac:bases/bruit_collines",
        "argument2": {"type": "minecraft:add", "argument1": round(COLLINE_BIOME - RELIEFS[1][0] + 0.02, 4),
                      "argument2": "crac:bases/bruit_reliefs"}}}
    for nom in ("temperature", "humidite"):
        f["climat_" + nom] = {"type": "minecraft:flat_cache", "argument": bruit(nom)}
    return f


def routeur():
    return {
        "barrier": 0.0, "fluid_level_floodedness": 0.0, "fluid_level_spread": 0.0, "lava": 0.0,
        "temperature": "crac:bases/climat_temperature", "vegetation": "crac:bases/climat_humidite",
        "continents": 0.0, "erosion": "crac:bases/climat_erosion", "depth": 0.0, "ridges": 0.0,
        "initial_density_without_jaggedness": "crac:bases/terrain",
        "final_density": {"type": "minecraft:interpolated", "argument": "crac:bases/terrain"},
        "vein_toggle": 0.0, "vein_ridged": 0.0, "vein_gap": 0.0,
    }


def generer(vanilla):
    dossier_df = os.path.join(DATA, "worldgen", "density_function", "bases")
    for x in ANCIENS:
        p = os.path.join(dossier_df, x + ".json")
        if os.path.exists(p):
            os.remove(p)
            print("retiré", os.path.relpath(p, os.path.join(ICI, "..")))
    for nom, (octave, amps) in BRUITS.items():
        ecrire("worldgen/noise/bases/" + nom + ".json", {"firstOctave": octave, "amplitudes": amps})
    for nom, obj in fonctions().items():
        ecrire("worldgen/density_function/bases/" + nom + ".json", obj)

    ns = lire(vanilla, "worldgen/noise_settings/overworld")
    ns["aquifers_enabled"] = False
    ns["ore_veins_enabled"] = False
    ns["disable_mob_generation"] = True
    ns["sea_level"] = -64
    ns["default_fluid"] = {"Name": "minecraft:air"}
    ns["noise_router"] = routeur()
    ns["spawn_target"] = [{"temperature": [-1.0, 1.0], "humidity": [-1.0, 1.0], "continentalness": [-1.0, 1.0],
                           "erosion": [-1.0, 1.0], "depth": 0.0, "weirdness": [-1.0, 1.0], "offset": 0.0}]
    ecrire("worldgen/noise_settings/bases.json", ns)

    dt = lire(vanilla, "dimension_type/overworld")
    dt["has_raids"] = False
    # 18/10, choix du propriétaire : on voit quelque chose la nuit et dans les bases fermées (lumière de fond)
    dt["ambient_light"] = LUMIERE_FOND
    ecrire("dimension_type/bases.json", dt)

    liste = []
    for nom, source, temp, hum, ero in BIOMES:
        b = lire(vanilla, "worldgen/biome/" + source)
        b["carvers"] = {}
        feats = [[] for _ in range(11)]
        veg = [f for f in b["features"][9] if f not in SANS] if len(b["features"]) > 9 else []
        if nom in ARBRES:
            ancien, nombre = ARBRES[nom]
            pf = lire(vanilla, "worldgen/placed_feature/" + ancien.split(":")[1])
            pf["placement"][0] = {"type": "minecraft:count", "count": nombre}
            nouveau = "crac:bases/" + ancien.split(":")[1]
            ecrire("worldgen/placed_feature/bases/" + ancien.split(":")[1] + ".json", pf)
            veg = [nouveau if f == ancien else f for f in veg]
        feats[9] = veg
        feats[10] = b["features"][10] if len(b["features"]) > 10 else ["minecraft:freeze_top_layer"]
        b["features"] = feats
        b["spawners"] = {k: [] for k in b["spawners"]}
        b["spawn_costs"] = {}
        b["creature_spawn_probability"] = 0.0
        ecrire("worldgen/biome/" + nom + ".json", b)
        liste.append({"biome": "crac:" + nom, "parameters": {
            "temperature": temp, "humidity": hum, "continentalness": [-1.0, 1.0], "erosion": ero,
            "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0}})

    ecrire("dimension/bases.json", {
        "type": "crac:bases",
        "generator": {"type": "minecraft:noise", "settings": "crac:bases",
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": liste}}})


# ---------------------------------------------------------------------------------------------- aperçu fidèle
# Bruit de Minecraft 1.20.1 (ImprovedNoise, PerlinNoise, NormalNoise), vectorisé : mêmes formules, autre tirage.
GRADIENTS = [(1, 1, 0), (-1, 1, 0), (1, -1, 0), (-1, -1, 0), (1, 0, 1), (-1, 0, 1), (1, 0, -1), (-1, 0, -1),
             (0, 1, 1), (0, -1, 1), (0, 1, -1), (0, -1, -1), (1, 1, 0), (0, -1, 1), (-1, 1, 0), (0, -1, -1)]


class Perlin3:
    """ImprovedNoise : permutation de 256 et décalage aléatoires, gradients de SimplexNoise.GRADIENT."""

    def __init__(self, rng):
        import numpy as np
        self.np = np
        self.o = rng.uniform(0, 256, 3)
        self.p = rng.permutation(256).astype(np.int64)
        self.g = np.array(GRADIENTS, dtype=float)

    def __call__(self, x, y, z):
        np = self.np
        x, y, z = x + self.o[0], y + self.o[1], z + self.o[2]
        i, j, k = np.floor(x), np.floor(y), np.floor(z)
        fx, fy, fz = x - i, y - j, z - k
        i, j, k = i.astype(np.int64), j.astype(np.int64), k.astype(np.int64)
        P = lambda v: self.p[v & 255]
        a, b = P(i), P(i + 1)
        aa, ab, ba, bb = P(a + j), P(a + j + 1), P(b + j), P(b + j + 1)

        def dot(h, dx, dy, dz):
            g = self.g[h & 15]
            return g[..., 0] * dx + g[..., 1] * dy + g[..., 2] * dz

        n000 = dot(P(aa + k), fx, fy, fz)
        n100 = dot(P(ba + k), fx - 1, fy, fz)
        n010 = dot(P(ab + k), fx, fy - 1, fz)
        n110 = dot(P(bb + k), fx - 1, fy - 1, fz)
        n001 = dot(P(aa + k + 1), fx, fy, fz - 1)
        n101 = dot(P(ba + k + 1), fx - 1, fy, fz - 1)
        n011 = dot(P(ab + k + 1), fx, fy - 1, fz - 1)
        n111 = dot(P(bb + k + 1), fx - 1, fy - 1, fz - 1)
        s = lambda t: t * t * t * (t * (t * 6 - 15) + 10)
        u, v, w = s(fx), s(fy), s(fz)
        l = lambda t, a_, b_: a_ + t * (b_ - a_)
        return l(w, l(v, l(u, n000, n100), l(u, n010, n110)), l(v, l(u, n001, n101), l(u, n011, n111)))


class PerlinOctaves:
    def __init__(self, rng, octave, amps):
        self.amps = amps
        self.niveaux = [Perlin3(rng) if a != 0 else None for a in amps]
        n = len(amps)
        self.entree = 2.0 ** octave
        self.valeur = 2.0 ** (n - 1) / (2.0 ** n - 1.0)

    def __call__(self, x, y, z):
        tot, e, f = 0.0, self.entree, self.valeur
        for a, bruit_ in zip(self.amps, self.niveaux):
            if bruit_ is not None:
                tot = tot + a * bruit_(x * e, y * e, z * e) * f
            e *= 2.0
            f /= 2.0
        return tot


class BruitNormal:
    """NormalNoise : deux Perlin à octaves, le second à 1,0181268882175227 fois la fréquence."""

    def __init__(self, rng, octave, amps):
        self.a = PerlinOctaves(rng, octave, amps)
        self.b = PerlinOctaves(rng, octave, amps)
        nz = [i for i, v in enumerate(amps) if v != 0]
        ecart = nz[-1] - nz[0]
        self.facteur = (1.0 / 6.0) / (0.1 * (1.0 + 1.0 / (ecart + 1)))

    def __call__(self, x, y, z):
        m = 1.0181268882175227
        return (self.a(x, y, z) + self.b(x * m, y * m, z * m)) * self.facteur


class Evaluateur:
    """Évalue en 2D (y donné) les fonctions de densité JSON utilisées ici, comme DensityFunctions de Minecraft."""

    def __init__(self, fonctions_, graine):
        import numpy as np
        self.np = np
        self.f = fonctions_
        self.bruits = {}
        for i, (nom, (octave, amps)) in enumerate(sorted(BRUITS.items())):
            self.bruits["crac:bases/" + nom] = BruitNormal(np.random.default_rng(graine * 1000 + i), octave, amps)

    def __call__(self, o, x, z, y=0.0):
        np = self.np
        if isinstance(o, (int, float)):
            return np.full(x.shape, float(o))
        if isinstance(o, str):
            return self(self.f[o.split("/", 1)[1]], x, z, y)
        t = o["type"].split(":")[1]
        if t in ("flat_cache", "cache_2d", "interpolated", "cache_once", "cache_all_in_cell"):
            return self(o["argument"], x, z, y)
        if t == "noise":
            s = o["xz_scale"]
            return self.bruits[o["noise"]](x * s, np.full(x.shape, y * o["y_scale"]), z * s)
        if t in ("add", "mul", "min", "max"):
            a, b = self(o["argument1"], x, z, y), self(o["argument2"], x, z, y)
            return {"add": np.add, "mul": np.multiply, "min": np.minimum, "max": np.maximum}[t](a, b)
        if t == "clamp":
            return np.clip(self(o["input"], x, z, y), o["min"], o["max"])
        if t == "y_clamped_gradient":
            yy = min(max(y, o["from_y"]), o["to_y"])
            return np.full(x.shape, o["from_value"] + (yy - o["from_y"]) / (o["to_y"] - o["from_y"]) * (o["to_value"] - o["from_value"]))
        if t == "spline":
            return self.courbe(o["spline"], x, z, y)
        raise SystemExit("aperçu : fonction de densité non prise en charge : " + t)

    def courbe(self, s, x, z, y):
        """CubicSpline.Multipoint (float) : Hermite entre deux points, prolongement linéaire par la dérivée."""
        np = self.np
        c = self(s["coordinate"], x, z, y).astype(np.float32)
        pts = s["points"]
        loc = np.array([p["location"] for p in pts], dtype=np.float32)
        val = np.array([p["value"] for p in pts], dtype=np.float32)
        der = np.array([p["derivative"] for p in pts], dtype=np.float32)
        i = np.searchsorted(loc, c, side="right") - 1
        out = np.empty(c.shape, dtype=np.float32)
        avant, apres = i < 0, i >= len(pts) - 1
        out[avant] = val[0] + der[0] * (c[avant] - loc[0])
        out[apres] = val[-1] + der[-1] * (c[apres] - loc[-1])
        m = ~(avant | apres)
        ii = i[m]
        g, h = loc[ii], loc[ii + 1]
        k = (c[m] - g) / (h - g)
        n, o_ = val[ii], val[ii + 1]
        p = der[ii] * (h - g) - (o_ - n)
        q = -der[ii + 1] * (h - g) + (o_ - n)
        out[m] = n + k * (o_ - n) + k * (1 - k) * (p + k * (q - p))
        return out.astype(float)


def sol(ev, f, x0, z0, n, pas=1):
    """Bloc du dessus de chaque colonne (n × n colonnes, une tous les « pas » blocs), comme Minecraft : H aux coins des
    cellules de 4 blocs, interpolé entre eux, puis plein tant que H - y > 0."""
    import numpy as np
    xs = x0 + np.arange(n) * pas
    zs = z0 + np.arange(n) * pas
    cx0, cz0 = (xs[0] // 4) * 4, (zs[0] // 4) * 4
    nx = int((xs[-1] - cx0) // 4) + 2
    nz = int((zs[-1] - cz0) // 4) + 2
    gx, gz = np.meshgrid(cx0 + np.arange(nx) * 4.0, cz0 + np.arange(nz) * 4.0)
    h = ev(f["hauteur"], gx, gz)
    fx = (xs - cx0) / 4.0
    fz = (zs - cz0) / 4.0
    ix, iz = fx.astype(int), fz.astype(int)
    tx, tz = fx - ix, fz - iz
    ix2, iz2 = np.meshgrid(ix, iz)
    tx2, tz2 = np.meshgrid(tx, tz)
    a = h[iz2, ix2] * (1 - tx2) + h[iz2, ix2 + 1] * tx2
    b = h[iz2 + 1, ix2] * (1 - tx2) + h[iz2 + 1, ix2 + 1] * tx2
    hb = a * (1 - tz2) + b * tz2
    return np.ceil(hb - 1e-9).astype(int) - 1, xs, zs


def biomes_de(ev, f, xs, zs):
    """Biome de chaque point : le plus proche dans l'espace des paramètres (0 si dans l'intervalle), comme Climate."""
    import numpy as np
    gx, gz = np.meshgrid(xs.astype(float), zs.astype(float))
    t = ev(f["climat_temperature"], gx, gz)
    hu = ev(f["climat_humidite"], gx, gz)
    e = ev(f["climat_erosion"], gx, gz)
    best = np.full(gx.shape, np.inf)
    idx = np.zeros(gx.shape, dtype=int)
    ecart = lambda v, r: np.maximum(0, np.maximum(r[0] - v, v - r[1]))
    for i, (_, _, rt, rh, re) in enumerate(BIOMES):
        d = ecart(t, rt) ** 2 + ecart(hu, rh) ** 2 + ecart(e, re) ** 2
        mieux = d < best
        best[mieux] = d[mieux]
        idx[mieux] = i
    return idx


COULEURS = {"bases_plaine": (128, 176, 82), "bases_prairie": (150, 190, 70), "bases_foret": (72, 120, 50),
            "bases_bouleaux": (100, 150, 70), "bases_savane": (178, 168, 92), "bases_collines": (118, 182, 110),
            "bases_taiga": (70, 104, 80)}


def ombrage(h, echelle):
    import numpy as np
    gz, gx = np.gradient(h.astype(float), echelle)
    lum = (-gx + -gz) / math.sqrt(2)
    return np.clip(1.0 + 0.9 * lum, 0.45, 1.35)


def apercu(dossier, graine, cote=2500):
    import numpy as np
    from PIL import Image

    os.makedirs(dossier, exist_ok=True)
    f = fonctions()
    ev = Evaluateur(f, graine)
    demi = cote // 2
    mesures = []

    # 1. toute la carte, 1 px = 4 blocs (hauteur exacte aux coins des cellules : pas besoin d'interpoler)
    pas = 4
    n = cote // pas
    hs, xs, zs = sol(ev, f, -demi, -demi, n, pas)
    bi = biomes_de(ev, f, xs, zs)
    noms = [b[0] for b in BIOMES]
    coul = np.array([COULEURS[nm] for nm in noms], dtype=float)[bi]
    ombre = ombrage(hs, pas)
    t = np.clip((hs - 60) / 70.0, 0, 1)[..., None]
    rgb = coul * (0.85 + 0.35 * t) * ombre[..., None]
    Image.fromarray(rgb.clip(0, 255).astype("uint8")).save(os.path.join(dossier, "carte.png"))
    gris = np.clip((hs - 60) * 3.0, 0, 255).astype("uint8")
    Image.fromarray(gris).save(os.path.join(dossier, "hauteurs.png"))
    Image.fromarray(np.array([COULEURS[nm] for nm in noms], dtype="uint8")[bi]).save(os.path.join(dossier, "biomes.png"))
    for i, nm in enumerate(noms):
        mesures.append(f"biome {nm:15s} {float((bi == i).mean()):6.1%}")

    # 2. mesures au bloc près sur toute la carte (par bandes de 500 blocs)
    plat = tot = 0
    marches = 0
    hist = {}
    hmin, hmax = 10 ** 9, -10 ** 9
    bande = 500
    for bz in range(-demi, demi, bande):
        for bx in range(-demi, demi, bande):
            h1, _, _ = sol(ev, f, bx - 1, bz - 1, bande + 2, 1)
            c = h1[1:-1, 1:-1]
            voisins = [h1[1 + dz:bande + 1 + dz, 1 + dx:bande + 1 + dx] for dz in (-1, 0, 1) for dx in (-1, 0, 1)]
            pareil = np.all([v == c for v in voisins], axis=0)
            plat += int(pareil.sum())
            tot += c.size
            ecarts = np.maximum(np.abs(c - h1[1:-1, 2:]), np.abs(c - h1[2:, 1:-1]))
            marches += int((ecarts >= 2).sum())
            hmin, hmax = min(hmin, int(c.min())), max(hmax, int(c.max()))
            v, k = np.unique(c[pareil], return_counts=True)
            for a, b in zip(v, k):
                hist[int(a)] = hist.get(int(a), 0) + int(b)
    niveaux = {math.ceil(v) - 1 for v in NIVEAUX}
    sur_plateau = sum(v for k, v in hist.items() if k in niveaux)
    mesures.append(f"sol parfaitement plat à la hauteur d'un plateau ({sorted(niveaux)}) : {sur_plateau / tot:.1%}")
    mesures.append(f"sol plat en comptant les marches larges des pentes douces (8 voisins à la même hauteur) : {plat / tot:.1%}")
    mesures.append(f"marches de 2 blocs ou plus entre deux voisins : {marches / tot:.2%}")
    mesures.append(f"hauteur du sol : {hmin} à {hmax}")
    for a in sorted(hist):
        if hist[a] > tot * 0.01:
            mesures.append(f"  plat à y = {a} : {hist[a] / tot:.1%}")
    cr = ev(f["reliefs"], *np.meshgrid(xs.astype(float), zs.astype(float)))
    grands = cr > 12
    mesures.append(f"grands reliefs (plus de 12 blocs au-dessus du plateau) : {float(grands.mean()):.1%} de la carte, "
                   f"{composantes(grands)} reliefs")

    # 3. deux carrés de 512 × 512 blocs, 1 px = 1 bloc, vus de dessus et en 3D : un carré « ordinaire » (vallonné comme
    #    6 carrés sur 10 de la carte) et le plus vallonné de la carte (grand relief)
    m = 512
    k = m // pas
    carres = []
    for a in range(0, n - k + 1, k // 4):
        for b in range(0, n - k + 1, k // 4):
            carres.append((float(hs[a:a + k, b:b + k].std()), a, b))
    carres.sort()
    for nom, (_, oz, ox) in (("ordinaire", carres[int(len(carres) * 0.6)]), ("relief", carres[-1])):
        x0, z0 = int(xs[ox]), int(zs[oz])
        mesures.append(f"carré « {nom} » : x {x0} à {x0 + m - 1}, z {z0} à {z0 + m - 1}")
        h2, xs2, zs2 = sol(ev, f, x0, z0, m, 1)
        bi2 = biomes_de(ev, f, xs2, zs2)
        coul2 = np.array([COULEURS[nm] for nm in noms], dtype=float)[bi2]
        rgb2 = coul2 * ombrage(h2, 1)[..., None]
        Image.fromarray(rgb2.clip(0, 255).astype("uint8")).save(os.path.join(dossier, f"detail_{nom}.png"))
        vue_3d(h2, coul2, os.path.join(dossier, f"vue_3d_{nom}.png"))

    with open(os.path.join(dossier, "mesures.txt"), "w", encoding="utf-8") as fo:
        fo.write(f"graine de l'aperçu : {graine} (Minecraft tirera ses bruits de la graine du monde)\n")
        fo.write("\n".join(mesures) + "\n")
    print("\n".join(mesures))
    print("aperçus dans", dossier)


def composantes(masque):
    import numpy as np
    vu = np.zeros(masque.shape, dtype=bool)
    n = 0
    hz, hx = masque.shape
    for z0, x0 in zip(*np.nonzero(masque)):
        if vu[z0, x0]:
            continue
        n += 1
        pile = [(z0, x0)]
        vu[z0, x0] = True
        while pile:
            z, x = pile.pop()
            for dz, dx in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                a, b = z + dz, x + dx
                if 0 <= a < hz and 0 <= b < hx and masque[a, b] and not vu[a, b]:
                    vu[a, b] = True
                    pile.append((a, b))
    return n


def vue_3d(h, coul, sortie, echelle_h=2.0):
    """Perspective cavalière : chaque colonne dessinée de l'arrière vers l'avant (hauteur ×2 pour mieux voir)."""
    import numpy as np
    from PIL import Image
    m = h.shape[0]
    base = int(h.min()) - 4
    haut = (h - base) * echelle_h
    oy = int(haut.max()) + 8
    hauteur_img = oy + m // 2 + 8
    img = np.zeros((hauteur_img, m, 3), dtype=float)
    img[...] = (150, 185, 220)
    ombre = ombrage(h, 1)
    xs = np.arange(m)
    for z in range(m):
        y_haut = (oy + z // 2 - haut[z]).astype(int)
        y_bas = oy + z // 2 + 1
        dessus = coul[z] * ombre[z][:, None]
        cote = coul[z] * 0.55
        for x in xs:
            a, b = y_haut[x], y_bas
            img[a, x] = dessus[x]
            if b > a + 1:
                img[a + 1:b, x] = cote[x] * np.linspace(1.0, 0.7, b - a - 1)[:, None]
    Image.fromarray(img.clip(0, 255).astype("uint8")).save(sortie)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--vanilla", help="dossier des fichiers de Minecraft 1.20.1 (noms : chemin avec des _)")
    ap.add_argument("--apercu", help="écrit seulement les aperçus du terrain dans ce dossier")
    ap.add_argument("--graine", type=int, default=1, help="tirage du bruit de l'aperçu")
    a = ap.parse_args()
    if a.apercu:
        apercu(a.apercu, a.graine)
        return
    generer(a.vanilla)


if __name__ == "__main__":
    sys.exit(main())
