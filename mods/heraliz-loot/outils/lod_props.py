"""Versions allégées (LOD) des conteneurs de HeraliZ Loot, avec rendus avant / après. N'installe RIEN dans le mod.

    python mods/heraliz-loot/outils/lod_props.py --sortie DOSSIER [--rendus 12] [--modeles a,b] [--petites 0.75] [--cellule 0.4]

Pourquoi : les conteneurs T1 à T6 sont cuits dans le rendu du terrain (client/ModeleConteneur) ; 246 modèles, 231 587 faces,
environ 945 par conteneur (t2_vaisselier 3 290), et environ 29 500 conteneurs posés sur la carte : dans une ville, des
centaines de milliers de faces dans les tronçons autour du joueur, à envoyer à la carte graphique à chaque image et à
recuire à chaque changement d'un tronçon (le jeu n'a pas de niveau de détail selon la distance pour les blocs).

Trois niveaux, calculés par mods/heraliz-loot/outils/LodConteneurs.java (Java pur, mêmes maillages que le jeu : modèle,
occlusion ambiante et salissure cuites, Conteneurs.maillages) :
  A. faces cachées (sans perte) : face qu'aucun regard ne peut atteindre (dessous posé au sol, intérieur des boîtes
     fermées, faces collées) ; rayons lancés depuis chaque face, l'envers des faces, les découpes de texture et le verre
     laissent passer le regard, comme dans le jeu ;
  B. A + petites pièces : pièce d'un seul tenant plus petite que --petites px (rivets, vis, éclats... mais aussi les
     étiquettes des clés de t6_armoire_cles : à juger sur les rendus) ;
  C. A + simplification : regroupement des sommets à --cellule px près, pièce par pièce (cylindres, biseaux, arrondis
     moins découpés), sans retirer les petites pièces.

Sorties dans DOSSIER : rapport.md (faces par modèle et en tout), lod.csv, conteneurs_lod.json (pour chaque conteneur, les
numéros des faces des niveaux A et B, avec le nombre de faces du maillage en garde), rendus/<modèle>.png (de gauche à
droite : actuel, A, B, C ; dessous, le même vu de loin, environ 15 à 20 blocs), rendus/<modèle>_dos.png (de derrière, en
rasant le sol, actuel puis A : contrôle qu'aucune face visible ne manque), planche.png (tous les rendus).
Rendus en moteur logiciel : textures, couleurs cuites et ombrage des blocs du jeu, sans la lumière du monde.

Besoin : JDK 17 (javac), Pillow ; JOML (variable JOML_JAR, cache de Gradle, sinon téléchargé dans ~/.cache/heraliz,
jamais dans le dépôt).
"""
import argparse
import csv
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

ICI = Path(__file__).resolve().parent
PROJET = ICI.parent
RENDU = PROJET / "src" / "client" / "java" / "fr" / "heraliz" / "loot" / "client" / "rendu"
TEXTURES = PROJET / "src" / "client" / "resources" / "assets" / "heraliz_loot" / "textures" / "block"
CACHE = Path.home() / ".cache" / "heraliz"


def outil(nom):
    base = os.environ.get("JAVA_HOME")
    if base:
        for c in (Path(base) / "bin" / (nom + ".exe"), Path(base) / "bin" / nom):
            if c.exists():
                return str(c)
    trouve = shutil.which(nom)
    if not trouve:
        sys.exit("%s introuvable : installer un JDK 17 (ou régler JAVA_HOME)" % nom)
    return trouve


def joml():
    if os.environ.get("JOML_JAR"):
        return os.environ["JOML_JAR"]
    caches = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches"
    jars = [j for j in sorted(caches.rglob("joml-1.*.jar")) if "sources" not in j.name] if caches.exists() else []
    if jars:
        return str(jars[-1])
    j = CACHE / "joml-1.10.5.jar"
    if not j.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve("https://repo1.maven.org/maven2/org/joml/joml/1.10.5/joml-1.10.5.jar", j)
    return str(j)


def lancer(sortie, petites, cellule, rendus, modeles):
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    jar = joml()
    sources = [str(p) for d in ("mesh", "conteneurs") for p in (RENDU / d).glob("*.java")]
    sources.append(str(ICI / "LodConteneurs.java"))
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([outil("javac"), "-nowarn", "-encoding", "UTF-8", "-d", tmp, "-cp", jar] + sources, check=True, env=env)
        cp = os.pathsep.join([tmp, jar])
        subprocess.run([outil("java"), "-cp", cp, "LodConteneurs", str(TEXTURES), str(sortie), str(petites), str(cellule),
                        ",".join(rendus) if rendus else "-"] + modeles, check=True, env=env, stdout=subprocess.DEVNULL)


def lire(sortie):
    with open(sortie / "lod.csv", encoding="utf-8") as f:
        return [{k: (v if k == "nom" else int(v)) for k, v in r.items()} for r in csv.DictReader(f)]


def pct(a, b):
    return "%.1f %%" % (100.0 * a / b) if b else "-"


def rapport(sortie, lignes, petites, cellule, rendus):
    tot = {k: sum(r[k] for r in lignes) for k in ("faces", "cachees", "petites", "apres_cachees", "apres_petites", "apres_simplifie")}
    out = ["# Conteneurs de HeraliZ Loot : versions allégées (proposition, rien d'installé)", "",
           "%d modèles, %d faces (opaques ; le verre n'est jamais touché)." % (len(lignes), tot["faces"]), "",
           "| niveau | faces restantes | gain |", "|---|---|---|",
           "| actuel | %d | |" % tot["faces"],
           "| A. faces cachées retirées (sans perte) | %d | -%s |" % (tot["apres_cachees"], pct(tot["cachees"], tot["faces"])),
           "| B. A + petites pièces < %s px | %d | -%s |" % (petites, tot["apres_petites"], pct(tot["faces"] - tot["apres_petites"], tot["faces"])),
           "| C. A + simplification (sommets à %s px près) | %d | -%s |" % (cellule, tot["apres_simplifie"],
                                                                         pct(tot["faces"] - tot["apres_simplifie"], tot["faces"])),
           "", "Rendus (`rendus/`) : de gauche à droite actuel, A, B, C ; dessous, vus de loin. `_dos` : de derrière en rasant le sol,",
           "actuel puis A (les faces cachées sont surtout là : rien de visible ne doit manquer).", "",
           "Modèles rendus : " + ", ".join(rendus), "",
           "## Par modèle (du plus lourd au plus léger)", "",
           "| modèle | faces | A | B | C |", "|---|---|---|---|---|"]
    for r in sorted(lignes, key=lambda r: -r["faces"]):
        out.append("| %s | %d | %d | %d | %d |" % (r["nom"], r["faces"], r["apres_cachees"], r["apres_petites"], r["apres_simplifie"]))
    (sortie / "rapport.md").write_text("\n".join(out) + "\n", encoding="utf-8")
    return tot


def planche(sortie, rendus):
    try:
        from PIL import Image, ImageDraw
    except ImportError:
        print("Pillow absent : pas de planche")
        return
    images = [(n, Image.open(sortie / "rendus" / (n + ".png"))) for n in rendus if (sortie / "rendus" / (n + ".png")).exists()]
    if not images:
        return
    l = max(i.width for _, i in images) // 2
    h = max(i.height for _, i in images) // 2 + 18
    p = Image.new("RGB", (l, h * len(images)), (20, 20, 24))
    d = ImageDraw.Draw(p)
    for k, (n, i) in enumerate(images):
        p.paste(i.resize((i.width // 2, i.height // 2), Image.LANCZOS), (0, k * h + 18))
        d.text((6, k * h + 3), "%s  -  actuel | A faces cachées | B = A + petites pièces | C = A + simplification" % n, fill=(230, 230, 230))
    p.save(sortie / "planche.png")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--sortie", required=True, help="dossier des résultats (jamais le mod)")
    ap.add_argument("--rendus", type=int, default=12, help="rendre les N modèles les plus lourds (en plus de --modeles)")
    ap.add_argument("--modeles", default="", help="modèles à rendre en plus des plus lourds (virgules) ; tous sont traités")
    ap.add_argument("--petites", type=float, default=0.75, help="niveau B : taille des petites pièces retirées (px, 0 : aucune)")
    ap.add_argument("--cellule", type=float, default=0.4, help="niveau C : regroupement des sommets (px, 0 : aucun)")
    a = ap.parse_args()
    sortie = Path(a.sortie).resolve()
    if PROJET in sortie.parents or sortie == PROJET:
        sys.exit("--sortie dans le mod : refusé (rien n'est installé tant que le propriétaire n'a pas validé les rendus)")
    sortie.mkdir(parents=True, exist_ok=True)
    choisis = [m for m in a.modeles.split(",") if m]
    # 1er passage : tous les modèles, sans rendu ; 2e : rendus des plus lourds et des modèles choisis (à part, puis rangés)
    lancer(sortie, a.petites, a.cellule, [], [])
    lignes = lire(sortie)
    connus = {r["nom"] for r in lignes}
    inconnus = [m for m in choisis if m not in connus]
    if inconnus:
        print("modèles inconnus, ignorés :", ", ".join(inconnus))
    rendus = list(dict.fromkeys([m for m in choisis if m in connus] + [r["nom"] for r in sorted(lignes, key=lambda r: -r["faces"])[:a.rendus]]))
    if rendus:
        with tempfile.TemporaryDirectory() as tmp:
            lancer(Path(tmp), a.petites, a.cellule, rendus, rendus)
            (sortie / "rendus").mkdir(exist_ok=True)
            for f in (Path(tmp) / "rendus").glob("*.png"):
                shutil.move(str(f), str(sortie / "rendus" / f.name))
    tot = rapport(sortie, lignes, a.petites, a.cellule, rendus)
    planche(sortie, rendus)
    print("faces : %d ; A %d ; B %d ; C %d" % (tot["faces"], tot["apres_cachees"], tot["apres_petites"], tot["apres_simplifie"]))
    print("écrit :", sortie / "rapport.md")


if __name__ == "__main__":
    main()
