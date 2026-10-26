#!/usr/bin/env python3
"""
Textures inutiles du pack de textures imposé (heraliz_interface, resourcepacks/textures) — LECTURE SEULE.

Minecraft range toutes les images des dossiers textures/block et textures/item (de tous les packs et de tous les mods)
dans la grande texture des blocs, qu'un modèle s'en serve ou non : une image que personne n'utilise coûte quand même de la
mémoire graphique. Ce script cherche, dans le pack « textures », les images que rien ne peut afficher :

- REMPLACÉES : un pack posé au-dessus (herbe, heraliz_terrain) donne une image au même chemin ; Minecraft ne lit jamais
  celle du dessous (ni son .mcmeta) ;
- ORPHELINES : un nom que ni Minecraft 1.20.1, ni Forge, ni aucun mod livré aux joueurs ne fournit (anciens noms 1.12 :
  tallgrass, double_plant_*, sapling_*…), et que rien ne cite : aucun modèle, état de bloc, atlas, particule, police,
  fichier texte d'un mod, gunpack ou module, aucune chaîne du code (classes de Minecraft, de Forge, des mods, jars
  imbriqués compris), même en morceau (« block/destroy_stage_ » + numéro), et ce n'est pas le nom d'un bloc ou d'un
  objet (un mod pourrait composer « block/ » + nom).

Un nom cité seulement par les tables de conversion des vieux mondes de Minecraft (net/minecraft/util/datafix : noms de
BLOCS de la 1.12, pas d'images) ne compte pas. Les .mcmeta (animations) ne partent qu'avec leur image.

    python outils/textures_inutiles.py               rapport (rien n'est écrit)
    python outils/textures_inutiles.py --liste F     écrit aussi la liste des fichiers à retirer (chemins relatifs au
                                                     dépôt, preuve en commentaire « # ») dans F
    python outils/textures_inutiles.py --detail      montre aussi chaque image gardée et pourquoi

Lit : livraison/mods client/*.jar (fabriquer d'abord), Minecraft 1.20.1 du lanceur CurseForge (outils/chemins.py),
Forge 1.20.1 en noms officiels du cache de Gradle (sinon les classes de Minecraft sont lues sans les noms de paquets),
les trois packs imposés, les sources des modules et les gunpacks de serveur/tacz. À relancer après l'ajout d'un mod ou
d'un pack : une image « orpheline » peut devenir utile si un nouveau modèle la cite.
"""
import argparse
import io
import json
import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins  # noqa: E402

DEPOT = chemins.DEPOT
RP = DEPOT / "modules" / "interface" / "src" / "main" / "resources" / "resourcepacks"
PACK_TEXTURES = RP / "textures"
PACK_HERBE = RP / "herbe"
PACK_TERRAIN = DEPOT / "modules" / "monde" / "src" / "client" / "resources" / "resourcepacks" / "heraliz_terrain"
# packs posés au-dessus du pack « textures » (toujours actifs : PackImpose, AmbianceClient)
PACKS_DESSUS = [("herbe", PACK_HERBE), ("heraliz_terrain", PACK_TERRAIN)]
JARS_JOUEURS = DEPOT / "livraison" / "mods client"
VANILLA = chemins.LANCEUR / "versions" / "1.20.1" / "1.20.1.jar"
FORGE_NOMME = (Path.home() / ".gradle" / "caches" / "forge_gradle" / "minecraft_user_repo" / "net" / "minecraftforge" / "forge"
               / "1.20.1-47.4.0_mapped_official_1.20.1" / "forge-1.20.1-47.4.0_mapped_official_1.20.1.jar")

TEXTE = (".json", ".mcmeta", ".txt", ".properties", ".toml", ".cfg", ".yml", ".yaml", ".lang", ".fsh", ".vsh", ".glsl",
         ".java", ".js", ".md", ".csv", ".mcfunction")
IGNORER_DATAFIX = "net/minecraft/util/datafix/"
# jars des modules qui portent nos packs imposés (leurs resourcepacks/ sont lus dans les sources)
NOS_JARS_A_PACKS = ("heraliz_interface", "heraliz_monde")
TEXTE_MAX = 4_000_000  # octets : au-delà (animations des gunpacks), un fichier des dossiers est sauté et signalé
SEP = re.compile(r"[^A-Za-z0-9_./\-]+")
ATLAS = re.compile(r"^assets/[^/]+/textures/(block|item)/")


# ------------------------------------------------------------------ chaînes du code (constantes des classes)
def chaines_classe(data):
    """Chaînes CONSTANT_Utf8 d'un fichier .class (sans dépendance)."""
    out = []
    try:
        if data[:4] != b"\xca\xfe\xba\xbe":
            return out
        n = int.from_bytes(data[8:10], "big")
        i, k = 10, 1
        while k < n:
            tag = data[i]
            if tag == 1:
                ln = int.from_bytes(data[i + 1:i + 3], "big")
                out.append(data[i + 3:i + 3 + ln].decode("utf-8", "replace"))
                i += 3 + ln
            elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
                i += 5
            elif tag in (5, 6):
                i += 9
                k += 1
            elif tag in (7, 8, 16, 19, 20):
                i += 3
            elif tag == 15:
                i += 4
            else:
                break
            k += 1
    except IndexError:
        pass
    return out


class Corpus:
    """Tout ce qui peut citer une image : jetons « forts » (chemins) et « faibles » (mots), avec leur source."""

    def __init__(self):
        self.chemins = set()          # « block/xxx », « particle/xxx »… (sans textures/ ni .png)
        self.mots = defaultdict(set)  # mot -> sources (une dizaine au plus)
        self.ids = set()              # noms de blocs et d'objets (états de blocs, modèles d'objets)
        self.fournis = {}             # chemin assets/... -> première source qui le fournit (jars et Minecraft)
        self.textes = 0
        self.classes = 0
        self.trop_gros = []           # fichiers texte des dossiers sautés (animations de gunpacks de plusieurs Mo)

    def _mot(self, m, src):
        s = self.mots[m]
        if len(s) < 12:
            s.add(src)

    def ajouter_chaines(self, chaines, src):
        for ch in chaines:
            for jeton in SEP.split(ch):
                if not jeton:
                    continue
                j = jeton.split(":")[-1] if ":" in jeton else jeton
                low = j.lower()
                if "/" in low:
                    t = low[:-4] if low.endswith(".png") else low
                    i = t.find("textures/")
                    if i >= 0:
                        t = t[i + 9:]
                    self.chemins.add(t)
                    # « minecraft/block/x », « assets/minecraft/textures/block/x » : aussi chaque fin de chemin
                    parts = t.split("/")
                    for a in range(1, len(parts) - 1):
                        self.chemins.add("/".join(parts[a:]))
                    base = parts[-1]
                else:
                    base = low[:-4] if low.endswith(".png") else low
                for m in re.split(r"[./\-]", base):
                    if m and not m.isdigit():
                        self._mot(m, src)

    def ajouter_texte(self, texte, src):
        self.textes += 1
        self.ajouter_chaines([texte], src)

    def ajouter_classe(self, data, src):
        self.classes += 1
        self.ajouter_chaines(chaines_classe(data), src)

    def ajouter_jar(self, z, nom, profondeur=0, fournir=True, texte_assets=True):
        nos_packs = nom.startswith(NOS_JARS_A_PACKS)
        for n in z.namelist():
            if n.endswith("/") or (nos_packs and n.startswith("resourcepacks/")):
                continue                    # nos packs imposés : lus dans leurs sources (copie à jour)
            if fournir and n.startswith("assets/"):
                self.fournis.setdefault(n, nom)
            if n.startswith("assets/") and "/blockstates/" in n and n.endswith(".json"):
                self.ids.add(n.rsplit("/", 1)[1][:-5])
            if n.startswith("assets/") and "/models/item/" in n and n.endswith(".json"):
                self.ids.add(n.rsplit("/", 1)[1][:-5])
            low = n.lower()
            if low.endswith(".class"):
                self.ajouter_classe(z.read(n), nom + "!" + n)
            elif low.endswith(TEXTE) and (texte_assets or not n.startswith("assets/")):
                self.ajouter_texte(z.read(n).decode("utf-8", "replace"), nom + "!" + n)
            elif low.endswith(".jar") and profondeur < 3:
                try:
                    with zipfile.ZipFile(io.BytesIO(z.read(n))) as zi:
                        self.ajouter_jar(zi, nom + "!" + n, profondeur + 1, fournir)
                except zipfile.BadZipFile:
                    pass

    def ajouter_dossier(self, racine, nom, fournir=False):
        for f in racine.rglob("*"):
            if not f.is_file():
                continue
            rel = f.relative_to(racine).as_posix()
            if "/build/" in "/" + rel or rel.startswith("build/") or "/resourcepacks/" in "/" + rel:
                continue                    # build/ : fabrication ; resourcepacks/ : nos packs, lus à part
            if rel.startswith("assets/") and "/blockstates/" in rel and rel.endswith(".json"):
                self.ids.add(f.stem)
            if rel.startswith("assets/") and "/models/item/" in rel and rel.endswith(".json"):
                self.ids.add(f.stem)
            if f.suffix.lower() in TEXTE:
                if f.stat().st_size > TEXTE_MAX:
                    self.trop_gros.append(nom + "/" + rel)
                    continue
                try:
                    self.ajouter_texte(f.read_text(encoding="utf-8", errors="replace"), nom + "/" + rel)
                except OSError:
                    pass


def lire_corpus():
    c = Corpus()
    if not JARS_JOUEURS.is_dir() or not any(JARS_JOUEURS.glob("*.jar")):
        sys.exit("livraison/mods client/ vide : fabriquer d'abord (outils/construire.py)")
    for j in sorted(JARS_JOUEURS.glob("*.jar")):
        with zipfile.ZipFile(j) as z:
            c.ajouter_jar(z, j.name)
    if not VANILLA.exists():
        sys.exit("Minecraft 1.20.1 introuvable : " + str(VANILLA) + " (outils/chemins.py, lanceur_curseforge)")
    with zipfile.ZipFile(VANILLA) as z:
        if FORGE_NOMME.exists():
            # ressources de Minecraft seulement : ses classes sont lues avec leurs vrais noms dans le jar de Forge
            for n in z.namelist():
                if n.startswith("assets/") and not n.endswith("/"):
                    c.fournis.setdefault(n, "minecraft-1.20.1")
                    if "/blockstates/" in n or "/models/item/" in n:
                        c.ids.add(n.rsplit("/", 1)[1].rsplit(".", 1)[0])
                    if n.lower().endswith(TEXTE):
                        c.ajouter_texte(z.read(n).decode("utf-8", "replace"), "minecraft-1.20.1!" + n)
        else:
            c.ajouter_jar(z, "minecraft-1.20.1")
    if FORGE_NOMME.exists():
        with zipfile.ZipFile(FORGE_NOMME) as z:
            c.ajouter_jar(z, "forge-1.20.1")
    else:
        print("(jar de Forge en noms officiels absent du cache de Gradle : tables datafix non reconnues)")
    for nom, d in [("pack textures", PACK_TEXTURES), ("pack herbe", PACK_HERBE), ("pack heraliz_terrain", PACK_TERRAIN)]:
        c.ajouter_dossier(d, nom)
    for m in sorted((DEPOT / "modules").iterdir()):
        if (m / "src").is_dir():
            c.ajouter_dossier(m / "src", "modules/" + m.name + "/src")
    if (DEPOT / "serveur" / "tacz").is_dir():
        c.ajouter_dossier(DEPOT / "serveur" / "tacz", "serveur/tacz")
    return c


def pixels(f):
    try:
        w, h = Image.open(f).size
        return w * h, (w, h)
    except Exception:
        return 0, (0, 0)


def analyser(c):
    """Pour chaque image du pack « textures » : (catégorie, preuve)."""
    res = []
    for f in sorted(PACK_TEXTURES.rglob("*.png")):
        rel = f.relative_to(PACK_TEXTURES).as_posix()                        # assets/ns/textures/dir/....png
        parts = rel.split("/")
        if parts[0] != "assets" or len(parts) < 5 or parts[2] != "textures":
            continue                                                          # pack.png
        ns, sous = parts[1], "/".join(parts[3:])[:-4]                         # sous : block/xxx
        nom = sous.rsplit("/", 1)[-1]
        px, taille = pixels(f)
        meta = f.with_name(f.name + ".mcmeta")
        info = dict(fichier=f, rel=rel, ns=ns, sous=sous, nom=nom, px=px, taille=taille,
                    meta=meta if meta.exists() else None, atlas=bool(ATLAS.match(rel)))
        dessus = [n for n, d in PACKS_DESSUS if (d / rel).exists()]
        if dessus:
            res.append((info, "remplacee", "le pack « %s », posé au-dessus et toujours actif, donne la même image" % dessus[-1]))
            continue
        if rel in c.fournis:
            res.append((info, "gardee", "remplace une image de " + c.fournis[rel]))
            continue
        if sous.lower() in c.chemins:
            res.append((info, "gardee", "chemin « %s » cité" % sous))
            continue
        pref = next((sous[:k] for k in range(len(sous) - 1, len(sous.split("/")[0]) + 2, -1)
                     if sous[:k].lower() in c.chemins and (sous[k - 1] in "_-/" or sous[k:].isdigit())), None)
        if pref:
            res.append((info, "gardee", "début de chemin « %s » cité (nom composé par le code ?)" % pref))
            continue
        if nom.lower() in c.ids:
            res.append((info, "gardee", "nom d'un bloc ou d'un objet (« %s »)" % nom))
            continue
        sources = c.mots.get(nom.lower(), set())
        fortes = sorted(s for s in sources if IGNORER_DATAFIX not in s)
        if fortes:
            res.append((info, "gardee", "mot « %s » cité dans %s" % (nom, ", ".join(fortes[:3]))))
            continue
        preuve = "nom absent de Minecraft 1.20.1, de Forge et des %d jars des joueurs ; cité nulle part" % len(list(JARS_JOUEURS.glob("*.jar")))
        if sources:
            preuve += " (sauf les tables de conversion des vieux mondes : nom de bloc 1.12)"
        res.append((info, "orpheline", preuve))
    return res


def herbe_inutilisee(c):
    """Modèles et images du pack herbe que rien ne cite (hors leur propre modèle)."""
    out = []
    for f in sorted((PACK_HERBE / "assets" / "minecraft" / "models" / "block").glob("*.json")):
        nom = f.stem
        refs = set()
        for d in [PACK_HERBE, PACK_TERRAIN, PACK_TEXTURES]:
            for g in (d / "assets").rglob("*.json"):
                if g == f:
                    continue
                txt = g.read_text(encoding="utf-8", errors="replace")
                if re.search(r"(?<![a-z0-9_])block/" + re.escape(nom) + r"(?![a-z0-9_])", txt):
                    refs.add(g.relative_to(DEPOT).as_posix())
        hors_packs = [s for s in c.mots.get(nom.lower(), set()) if not s.startswith("pack ")]
        if not refs and not hors_packs:
            out.append(f)
            png = PACK_HERBE / "assets" / "minecraft" / "textures" / "block" / (nom + ".png")
            if png.exists():
                autres = [g for g in (PACK_HERBE / "assets").rglob("*.json") if g != f
                          and ("block/" + nom + '"') in g.read_text(encoding="utf-8", errors="replace")]
                if not autres:
                    out.append(png)
    return out


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--liste", type=Path, help="écrit la liste des fichiers à retirer (chemins relatifs au dépôt)")
    ap.add_argument("--detail", action="store_true")
    a = ap.parse_args()
    c = lire_corpus()
    print("lu : %d fichiers texte, %d classes, %d chemins cités, %d noms de blocs/objets"
          % (c.textes, c.classes, len(c.chemins), len(c.ids)))
    if c.trop_gros:
        print("sautés (plus de %d Mo) : %d fichiers, ex. %s" % (TEXTE_MAX // 1_000_000, len(c.trop_gros), c.trop_gros[0]))
    res = analyser(c)
    par = defaultdict(list)
    for info, cat, preuve in res:
        par[cat].append((info, preuve))
    total = sum(i["px"] for i, _, _ in res)
    for cat in ("remplacee", "orpheline", "gardee"):
        l = par[cat]
        px = sum(i["px"] for i, _ in l)
        pxa = sum(i["px"] for i, _ in l if i["atlas"])
        print("%-10s %5d images, %6.2f Mpx (dont %5.2f Mpx dans la grande texture des blocs)" % (cat, len(l), px / 1e6, pxa / 1e6))
    print("pack « textures » : %d images, %.2f Mpx" % (len(res), total / 1e6))
    herbe = herbe_inutilisee(c)
    print("pack « herbe » : %d fichiers que rien ne cite" % len(herbe))
    for f in herbe:
        print("   ", f.relative_to(DEPOT).as_posix())
    for cat in ("remplacee", "orpheline") + (("gardee",) if a.detail else ()):
        print("\n==", cat)
        for info, preuve in par[cat]:
            print("  %-70s %4dx%-5d %s" % (info["rel"], info["taille"][0], info["taille"][1], preuve if cat != "orpheline" else ""))
    if a.liste:
        lignes = ["# Fichiers à retirer du projet (écrit par outils/textures_inutiles.py, lecture seule ; rien n'a été supprimé).",
                  "# Une ligne = un chemin relatif au dépôt ; les lignes « # » donnent la preuve.", ""]
        for cat, titre in (("remplacee", "Images du pack « textures » remplacées par un pack posé au-dessus (jamais lues)"),
                           ("orpheline", "Images du pack « textures » orphelines (anciens noms, citées nulle part)")):
            l = par[cat]
            if not l:
                continue
            px = sum(i["px"] for i, _ in l)
            lignes.append("# ---- %s : %d images, %.2f Mpx" % (titre, len(l), px / 1e6))
            for info, preuve in l:
                lignes.append("# " + preuve)
                lignes.append(info["fichier"].relative_to(DEPOT).as_posix())
                if info["meta"] is not None:
                    lignes.append("# (son animation, inutile sans l'image)")
                    lignes.append(info["meta"].relative_to(DEPOT).as_posix())
            lignes.append("")
        if herbe:
            lignes.append("# ---- Pack « herbe » : modèles et images que ni un état de bloc, ni un autre modèle, ni le code ne citent")
            for f in herbe:
                lignes.append(f.relative_to(DEPOT).as_posix())
            lignes.append("")
        a.liste.write_text("\n".join(lignes), encoding="utf-8")
        print("\nliste écrite :", a.liste)


if __name__ == "__main__":
    main()
