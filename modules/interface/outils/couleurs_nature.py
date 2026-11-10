"""Couleurs de la nature du pack imposé (heraliz_interface) : « proposition A », validée par le propriétaire le 10/11
sur des images avant / après (herbe et feuilles plus vertes et plus claires, terre et bois éclaircis ; les bâtiments ne
changent pas).

Le pack d'origine rendait le jeu terne : couleurs de l'herbe et du feuillage presque grises (4 fois moins colorées que
celles de Minecraft), feuilles, terre et bois 30 à 50 % plus sombres. Cet outil refait, dans le pack
(`src/main/resources/resourcepacks/textures/assets/minecraft/textures/`) :

- `colormap/grass.png` et `colormap/foliage.png` : mélange de la carte de couleurs du pack et de celle de Minecraft
  (`PART_MINECRAFT`), puis écarts entre biomes resserrés autour de la couleur de la plaine (`ECART_BIOMES`, demande du
  propriétaire du 10/11 : « la différence entre deux biomes sur le même bloc est trop flagrante ») : rivière, océan et
  plage donnent la même herbe que la plaine à l'œil, savane, désert et neige ne s'en écartent presque plus (15 % de l'écart d'avant) ;
- les feuilles (`block/*_leaves.png`, sauf celles qui ont leur propre couleur) et les lianes : éclaircies jusqu'à
  `FEUILLES` fois la clarté de la texture de Minecraft ;
- la terre, le gravier, les pierres de sol et les troncs plus sombres que ceux de Minecraft (pas les planches, qui
  servent aux bâtiments) : éclaircis d'une part `TERRE` de l'écart.

Les textures d'origine du pack sont gardées dans `outils/couleurs_origine/` (copiées au premier passage) : l'outil part
toujours d'elles, on peut donc le relancer ou changer les réglages sans que l'effet s'ajoute. Les textures de Minecraft
sont lues dans le lanceur CurseForge (`outils/chemins.py`), jamais copiées dans le dépôt.

Jamais retouché à la main : on modifie cet outil puis on le relance, puis on refabrique heraliz_interface.

    python modules/interface/outils/couleurs_nature.py            refait les textures
    python modules/interface/outils/couleurs_nature.py --origine  remet les textures d'origine du pack
"""
import io
import shutil
import sys
import zipfile
from pathlib import Path

import numpy as np
from PIL import Image

ICI = Path(__file__).resolve().parent
RACINE = ICI.parents[2]
sys.path.insert(0, str(RACINE / "outils"))
import chemins  # noqa: E402

PART_MINECRAFT = 0.55      # cartes de couleurs : 0 = celles du pack, 1 = celles de Minecraft
ECART_BIOMES = 0.15        # part gardée de l'écart de couleur entre un biome et la plaine (1 = comme Minecraft, 0 = aucun)
PLAINE = (50, 173)         # point de la plaine dans les cartes de couleurs (température 0,8, humidité 0,4)
FEUILLES = 0.92            # clarté visée des feuilles, par rapport à la texture de Minecraft
TERRE = 0.5                # part de l'écart de clarté rattrapée pour la terre et le bois
GAIN_MAX_FEUILLES = 1.7
GAIN_MAX_TERRE = 1.6

TEXTURES = ICI.parent / "src/main/resources/resourcepacks/textures/assets/minecraft/textures"
ORIGINE = ICI / "couleurs_origine"
CARTES = ("colormap/grass.png", "colormap/foliage.png")
SOL_SOMBRE = {"dirt", "coarse_dirt", "podzol_top", "podzol_side", "rooted_dirt", "farmland", "farmland_moist", "mud",
              "gravel", "cobblestone", "mossy_cobblestone", "dirt_path_top", "dirt_path_side"}
FEUILLES_A_PART = {"azalea_leaves", "flowering_azalea_leaves", "cherry_leaves"}     # elles ont leur propre couleur


def minecraft():
    jar = chemins.LANCEUR / "versions" / "1.20.1" / "1.20.1.jar"
    if not jar.exists():
        sys.exit(f"Minecraft 1.20.1 introuvable ({jar}) : lancer une fois un profil Forge 1.20.1 de CurseForge.")
    return zipfile.ZipFile(jar)


def clarte(a):
    m = a[..., 3] > 25
    return float(a[..., :3][m].max(-1).mean()) if m.any() else 0.0


def gain(a, g):
    b = a.astype(np.float32)
    b[..., :3] = np.clip(b[..., :3] * g, 0, 255)
    return (b + 0.5).astype(np.uint8)


def concernees():
    """Textures du pack que l'outil peut changer (chemins relatifs au dossier des textures)."""
    out = [c for c in CARTES if (TEXTURES / c).exists() or (ORIGINE / c).exists()]
    for f in sorted((TEXTURES / "block").glob("*.png")):
        n = f.stem
        if (n.endswith("_leaves") and n not in FEUILLES_A_PART) or n == "vine" or n in SOL_SOMBRE \
                or n.endswith("_log") or n.endswith("_log_top"):
            out.append("block/" + f.name)
    return out


def main():
    if "--origine" in sys.argv:
        n = 0
        for f in ORIGINE.rglob("*.png"):
            shutil.copyfile(f, TEXTURES / f.relative_to(ORIGINE))
            n += 1
        print(f"{n} textures d'origine remises dans le pack")
        return
    mc = minecraft()
    faits = []
    for rel in concernees():
        dest, org = TEXTURES / rel, ORIGINE / rel
        a = np.asarray(Image.open(org if org.exists() else dest).convert("RGBA")).copy()
        try:
            v = np.asarray(Image.open(io.BytesIO(mc.read("assets/minecraft/textures/" + rel))).convert("RGBA")).copy()
        except KeyError:
            v = None
        n = Path(rel).stem
        b, quoi = a, None
        if rel in CARTES:
            if v is not None and v.shape == a.shape:
                m = a.astype(np.float32) * (1 - PART_MINECRAFT) + v.astype(np.float32) * PART_MINECRAFT
                base = m[PLAINE[1] * m.shape[0] // 256, PLAINE[0] * m.shape[1] // 256, :3].copy()
                m[..., :3] = base + (m[..., :3] - base) * ECART_BIOMES
                b, quoi = (np.clip(m, 0, 255) + 0.5).astype(np.uint8),                     f"mélange {int(PART_MINECRAFT * 100)} % Minecraft, écarts entre biomes x{ECART_BIOMES}"
        elif v is not None and clarte(a) > 0:
            if n.endswith("_leaves") or n == "vine":
                g = min(max(FEUILLES * clarte(v) / clarte(a), 1.0), GAIN_MAX_FEUILLES)
            elif clarte(a) < clarte(v):
                g = min((clarte(a) + (clarte(v) - clarte(a)) * TERRE) / clarte(a), GAIN_MAX_TERRE)
            else:
                g = 1.0
            if g > 1.001:
                b, quoi = gain(a, g), f"x{g:.2f}"
        if quoi is None:
            if org.exists():
                shutil.copyfile(org, dest)
            continue
        if not org.exists():
            org.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(dest, org)
        Image.fromarray(b, "RGBA").save(dest)
        faits.append((rel, quoi))
    for rel, quoi in faits:
        print(f"  {rel:40s} {quoi}")
    print(f"{len(faits)} textures refaites ; origines gardées dans {ORIGINE.relative_to(RACINE)}")


if __name__ == "__main__":
    main()
