"""Monde des bases de CRac (dimension crac:bases) : écrit ses fichiers de génération, jamais retouchés à la main.

    python mods/crac/outils/monde_bases.py                      écrit src/serveur/resources/data/crac/...
    python mods/crac/outils/monde_bases.py --vanilla DOSSIER    lit les fichiers de Minecraft 1.20.1 dans DOSSIER
    python mods/crac/outils/monde_bases.py --apercu carte.png   aperçu GROSSIER du relief (numpy + Pillow)

Le terrain reprend le moteur du monde normal de Minecraft 1.20.1 (« noise router » de l'overworld), modifié :
- jamais d'océan : la « continentalité » est ramenée dans l'intérieur des terres (0,1 à 0,7 au lieu de -1 à 1) ;
- beaucoup de grands endroits plats : l'« érosion » est poussée vers le haut (terrain lisse) et plus étalée (bruit
  plus lent) ; restent des collines, des vallées (avec rivières et petits lacs au niveau 63) et quelques montagnes là
  où l'érosion est basse ;
- aucune grotte (la densité finale est le terrain seul, sans fromage, spaghetti ni nouilles), pas de nappes
  souterraines, pas de filons de minerai ;
- biomes propres à CRac (plaine, prairie, forêt, bouleaux, savane, collines, taïga) copiés de ceux de Minecraft :
  mêmes arbres, herbes et fleurs, mais aucun minerai, aucun lac de lave, aucune source, aucune grotte, aucun donjon,
  aucun monstre ; ils ne sont dans aucune liste de structures (ni village, ni temple, ni mine abandonnée) ni dans
  « is_overworld » (les mods qui ajoutent des monstres à l'overworld ne les touchent pas).

Les fichiers de Minecraft sont lus dans --vanilla, sinon téléchargés (miroir public misode/mcmeta, branche
1.20.1-data). L'aperçu n'est PAS le vrai terrain (il imite seulement la logique « continent / érosion / crêtes ») :
le vrai rendu se fait depuis une copie du monde du serveur (outils/carte_monde.py, dossier dimensions/crac/bases).
"""
import argparse
import copy
import json
import os
import sys
import urllib.request

ICI = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(ICI, "..", "src", "serveur", "resources", "data", "crac")
MIROIR = "https://raw.githubusercontent.com/misode/mcmeta/1.20.1-data/data/minecraft/"

# fonctions de densité de l'overworld remplacées par celles du monde des bases
REMPLACEES = ["continents", "erosion", "offset", "factor", "jaggedness", "depth", "sloped_cheese"]
COPIEES = ["offset", "factor", "jaggedness", "depth", "sloped_cheese"]

# continentalité : 0,1 à 0,7 (intérieur des terres, jamais de côte ni d'océan)
CONTINENT_BASE, CONTINENT_AMPLITUDE = 0.4, 0.3
# érosion : poussée vers le haut (plat) et bruit plus lent (grandes plaines)
EROSION_BASE, EROSION_AMPLITUDE, EROSION_ECHELLE = 0.3, 0.8, 0.18

# biome du monde des bases -> biome de Minecraft copié, paramètres de choix (température, humidité, érosion)
BIOMES = [
    ("bases_plaine", "plains", [-0.45, 0.55], [-1.0, 0.1], [0.05, 1.0]),
    ("bases_prairie", "sunflower_plains", [-0.45, 0.55], [0.1, 1.0], [0.05, 1.0]),
    ("bases_foret", "forest", [-0.45, 0.2], [-0.1, 1.0], [-0.45, 0.05]),
    ("bases_bouleaux", "birch_forest", [0.2, 0.55], [-0.1, 1.0], [-0.45, 0.05]),
    ("bases_savane", "savanna", [0.55, 1.0], [-1.0, 1.0], [-0.45, 1.0]),
    ("bases_collines", "windswept_hills", [-1.0, 1.0], [-1.0, 1.0], [-1.0, -0.45]),
    ("bases_taiga", "taiga", [-1.0, -0.45], [-1.0, 1.0], [-0.45, 1.0]),
]
# animaux gardés (aucun monstre, aucun loup ni lama)
ANIMAUX = {"minecraft:sheep", "minecraft:pig", "minecraft:chicken", "minecraft:cow", "minecraft:horse",
           "minecraft:donkey", "minecraft:rabbit"}
# décorations retirées de la végétation (sous terre)
SANS = {"minecraft:glow_lichen"}


def lire(vanilla, chemin):
    nom = chemin.replace("/", "_") + ".json"
    if vanilla:
        with open(os.path.join(vanilla, nom), encoding="utf-8") as f:
            return json.load(f)
    with urllib.request.urlopen(MIROIR + chemin + ".json", timeout=60) as r:
        return json.loads(r.read().decode("utf-8"))


def remplacer(o):
    """Les références « minecraft:overworld/X » des fonctions remplacées deviennent « crac:bases/X »."""
    if isinstance(o, str):
        for x in REMPLACEES:
            if o == "minecraft:overworld/" + x:
                return "crac:bases/" + x
        return o
    if isinstance(o, list):
        return [remplacer(v) for v in o]
    if isinstance(o, dict):
        return {k: remplacer(v) for k, v in o.items()}
    return o


def bruit(nom, echelle):
    return {"type": "minecraft:shifted_noise", "noise": nom, "shift_x": "minecraft:shift_x", "shift_y": 0.0,
            "shift_z": "minecraft:shift_z", "xz_scale": echelle, "y_scale": 0.0}


def sans_grottes(final):
    """Densité finale de l'overworld sans grottes : le choix « fromage / spaghetti / nouilles » devient le terrain."""
    def parcourir(o):
        if isinstance(o, dict):
            if o.get("type") == "minecraft:range_choice" and o.get("input") == "crac:bases/sloped_cheese":
                return "crac:bases/sloped_cheese"
            return {k: parcourir(v) for k, v in o.items()}
        if isinstance(o, list):
            return [parcourir(v) for v in o]
        return o
    if final.get("type") == "minecraft:min" and final.get("argument2") == "minecraft:overworld/caves/noodle":
        final = final["argument1"]
    final = parcourir(final)
    texte = json.dumps(final)
    if "caves" in texte:
        raise SystemExit("densité finale : une grotte est restée (" + texte[:200] + ")")
    return final


def ecrire(chemin, obj):
    complet = os.path.join(DATA, chemin)
    os.makedirs(os.path.dirname(complet), exist_ok=True)
    with open(complet, "w", encoding="utf-8", newline="\n") as f:
        json.dump(obj, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print("écrit", os.path.relpath(complet, os.path.join(ICI, "..")))


def generer(vanilla):
    # fonctions de densité
    ecrire("worldgen/density_function/bases/continents.json", {
        "type": "minecraft:flat_cache",
        "argument": {"type": "minecraft:clamp", "min": 0.05, "max": 1.0, "input": {
            "type": "minecraft:add", "argument1": CONTINENT_BASE, "argument2": {
                "type": "minecraft:mul", "argument1": CONTINENT_AMPLITUDE,
                "argument2": bruit("minecraft:continentalness", 0.25)}}}})
    ecrire("worldgen/density_function/bases/erosion.json", {
        "type": "minecraft:flat_cache",
        "argument": {"type": "minecraft:clamp", "min": -1.0, "max": 1.0, "input": {
            "type": "minecraft:add", "argument1": EROSION_BASE, "argument2": {
                "type": "minecraft:mul", "argument1": EROSION_AMPLITUDE,
                "argument2": bruit("minecraft:erosion", EROSION_ECHELLE)}}}})
    for x in COPIEES:
        ecrire("worldgen/density_function/bases/" + x + ".json",
               remplacer(lire(vanilla, "worldgen/density_function/overworld/" + x)))

    # réglages du bruit
    ns = lire(vanilla, "worldgen/noise_settings/overworld")
    ns["aquifers_enabled"] = False
    ns["ore_veins_enabled"] = False
    router = remplacer(ns["noise_router"])
    router["final_density"] = sans_grottes(router["final_density"])
    for k in ("vein_toggle", "vein_ridged", "vein_gap"):
        router[k] = 0.0
    ns["noise_router"] = router
    ecrire("worldgen/noise_settings/bases.json", ns)

    # type de dimension : celui de l'overworld, sans raids
    dt = lire(vanilla, "dimension_type/overworld")
    dt["has_raids"] = False
    ecrire("dimension_type/bases.json", dt)

    # biomes
    liste = []
    for nom, source, temp, hum, ero in BIOMES:
        b = lire(vanilla, "worldgen/biome/" + source)
        b["carvers"] = {}
        feats = [[] for _ in range(11)]
        veg = [f for f in b["features"][9] if f not in SANS] if len(b["features"]) > 9 else []
        feats[9] = veg
        feats[10] = b["features"][10] if len(b["features"]) > 10 else ["minecraft:freeze_top_layer"]
        b["features"] = feats
        sp = b["spawners"]
        for cat in list(sp.keys()):
            if cat == "creature":
                sp[cat] = [s for s in sp[cat] if s["type"] in ANIMAUX]
            elif cat != "water_creature":
                sp[cat] = []
        b["spawn_costs"] = {}
        ecrire("worldgen/biome/" + nom + ".json", b)
        liste.append({"biome": "crac:" + nom, "parameters": {
            "temperature": temp, "humidity": hum, "continentalness": [-1.0, 1.0], "erosion": ero,
            "weirdness": [-1.0, 1.0], "depth": 0.0, "offset": 0.0}})

    ecrire("dimension/bases.json", {
        "type": "crac:bases",
        "generator": {"type": "minecraft:noise", "settings": "crac:bases",
                      "biome_source": {"type": "minecraft:multi_noise", "biomes": liste}}})


# ---------------------------------------------------------------------------------------------- aperçu grossier
def apercu(sortie, taille=6000, px=8, graine=7):
    import numpy as np
    from PIL import Image

    n = taille // px
    rng = np.random.default_rng(graine)

    def valeur(periode):
        g = max(2, int(n / (periode / px)) + 2)
        grille = rng.uniform(-1, 1, (g, g))
        x = np.linspace(0, g - 1.001, n)
        i = x.astype(int)
        f = x - i
        f = f * f * (3 - 2 * f)
        a = grille[np.ix_(i, i)] * (1 - f)[:, None] + grille[np.ix_(i + 1, i)] * f[:, None]
        b = grille[np.ix_(i, i + 1)] * (1 - f)[:, None] + grille[np.ix_(i + 1, i + 1)] * f[:, None]
        return a * (1 - f)[None, :] + b * f[None, :]

    def fbm(periode, octaves=4):
        s, amp, tot = 0, 1.0, 0
        for o in range(octaves):
            s = s + amp * valeur(periode / (2 ** o))
            tot += amp
            amp *= 0.5
        return s / tot

    cont = np.clip(CONTINENT_BASE + CONTINENT_AMPLITUDE * fbm(1600) * 1.6, 0.05, 1)
    ero = np.clip(EROSION_BASE + EROSION_AMPLITUDE * fbm(1400 / EROSION_ECHELLE * 0.25) * 1.6, -1, 1)
    crete = np.clip(fbm(500) * 2.4, -1, 1)
    pli = 1 - np.abs(3 * np.abs(crete) - 2)                    # « ridges_folded » : vallées (-1) à sommets (1)
    plat = np.clip((ero + 0.2) / 0.9, 0, 1)                   # 1 = érodé (plat)
    hauteur = 66 + 22 * cont + (1 - plat) * (14 * pli + 55 * np.clip(pli, 0, 1) * np.clip(-ero, 0, 1) * 1.6)
    hauteur = hauteur + 3 * fbm(120, 3) * (0.4 + (1 - plat))
    hauteur = np.where(pli < -0.8, np.minimum(hauteur, 58 + 30 * (pli + 1)), hauteur)   # vallées : rivières, lacs
    gy, gx = np.gradient(hauteur)
    pente = np.hypot(gx, gy) / px
    ombre = np.clip(1 - (gx - gy) / px * 0.9, 0.55, 1.25)
    t = np.clip((hauteur - 60) / 90, 0, 1)
    r = (95 + 90 * t) * ombre
    g = (150 + 40 * t - 30 * (t > 0.65)) * ombre
    b = (70 + 80 * t) * ombre
    eau = hauteur < 63
    r, g, b = np.where(eau, 60, r), np.where(eau, 110, g), np.where(eau, 190, b)
    img = np.stack([r, g, b], axis=-1).clip(0, 255).astype("uint8")
    Image.fromarray(img).resize((n * 1, n * 1)).save(sortie)
    plats = float(((pente < 0.08) & ~eau).mean())
    print("aperçu", sortie, f"{n} px (1 px = {px} blocs) ; part de terrain presque plat (< 8 %) : {plats:.0%}")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--vanilla", help="dossier des fichiers de Minecraft 1.20.1 (noms : chemin avec des _)")
    ap.add_argument("--apercu", help="écrit seulement un aperçu grossier du relief dans ce PNG")
    a = ap.parse_args()
    if a.apercu:
        apercu(a.apercu)
        return
    generer(a.vanilla)


if __name__ == "__main__":
    sys.exit(main())
