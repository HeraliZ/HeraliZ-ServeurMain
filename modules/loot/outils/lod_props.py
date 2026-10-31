"""Version allégée « C » des conteneurs de HeraliZ Loot (faces cachées + simplification), contrôles et rendus avant / après.

    python modules/loot/outils/lod_props.py --installer [--sortie DOSSIER]    écrit les données du jeu (et le rapport)
    python modules/loot/outils/lod_props.py --sortie DOSSIER [--rendus 12] [--modeles a,b]   contrôles et rendus seuls
    python modules/loot/outils/lod_props.py --controle                         données à jour avec les modèles ? (rapide)

Pourquoi : les conteneurs T1 à T6 sont cuits dans le rendu du terrain (client/ModeleConteneur) ; 246 modèles, 231 587
faces, environ 945 par conteneur (t2_vaisselier 3 290), et environ 29 500 conteneurs posés sur la carte. Version « C »
choisie par le propriétaire le 31/10 (rendus du 24/10) : faces cachées retirées (sans perte) + regroupement des sommets à
0,4 px près pièce par pièce, sans retirer les petites pièces. Le 31/10, contrôles ajoutés pour que ça reste propre (voir
LodConteneurs.java : rien de percé, retourné, tordu, aplati, collé ni effacé) : environ un cinquième de faces en moins
(au lieu de la moitié de la proposition du 24/10, qui cassait anneaux, rebords et tiges de près).

Qui calcule quoi :
  - faces cachées : ICI (rayons lancés depuis chaque face, LodConteneurs.java), écrites dans
    src/client/resources/assets/heraliz_loot/lod/conteneurs_lod.txt (jamais à la main) ;
  - simplification : dans le JEU, une fois par modèle au premier affichage, par le même code Java que l'outil
    (rendu/conteneurs/Allegement.java) ; l'outil y ajoute les cases « gelées » (là où le regroupement abîmerait le modèle)
    et l'empreinte du résultat : le jeu garde le modèle sans simplification si son résultat diffère.
À relancer (--installer) après TOUT changement d'un modèle de conteneur, de ses textures ou de l'occlusion / salissure
cuites : sinon le jeu reconnaît que le maillage a changé et garde ce modèle complet (verifier.py lance --controle).

Sorties de --sortie : rapport.md, lod.csv, rendus/<modèle>.png (actuel | C | écart des couleurs ×4 ; de près, de loin,
de dos en rasant le sol), planche.png. Rendus en moteur logiciel : textures, couleurs cuites et ombrage des blocs du jeu,
sans la lumière du monde.

Besoin : JDK 17+ (javac), Pillow (planche) ; JOML (variable JOML_JAR, cache de Gradle, sinon téléchargé dans
~/.cache/heraliz, jamais dans le dépôt).
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
DONNEES = PROJET / "src" / "client" / "resources" / "assets" / "heraliz_loot" / "lod" / "conteneurs_lod.txt"
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


def java(arguments, bavard=False, verifier=True):
    """Compile le code des modèles (Java pur) et LodConteneurs, puis lance LodConteneurs avec ces arguments."""
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    jar = joml()
    sources = [str(p) for d in ("mesh", "conteneurs") for p in (RENDU / d).glob("*.java")]
    sources.append(str(ICI / "LodConteneurs.java"))
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([outil("javac"), "-nowarn", "-encoding", "UTF-8", "-d", tmp, "-cp", jar] + sources, check=True, env=env)
        cp = os.pathsep.join([tmp, jar])
        return subprocess.run([outil("java"), "-Xmx3g", "-cp", cp, "LodConteneurs"] + arguments, check=verifier, env=env,
                              stdout=None if bavard else subprocess.DEVNULL).returncode


def lancer(sortie, rendus, modeles, bavard=False):
    java([str(TEXTURES), str(sortie), ",".join(rendus) if rendus else "-"] + modeles, bavard)


def lire(sortie):
    with open(sortie / "lod.csv", encoding="utf-8") as f:
        return [{k: (v if k == "nom" else float(v) if k == "ecart_c" else int(v)) for k, v in r.items()} for r in csv.DictReader(f)]


def pct(a, b):
    return "%.1f %%" % (100.0 * a / b) if b else "-"


def rapport(sortie, lignes, rendus):
    cles = ("faces", "cachees", "dessous", "apres_a", "apres_c", "ecarts_a", "trous_c", "retournees", "collees", "degenerees")
    tot = {k: sum(r[k] for r in lignes) for k in cles}
    seuls_a = [r["nom"] for r in lignes if r["apres_c"] == r["apres_a"] and r["apres_c"] != r["faces"]]
    out = ["# Conteneurs de HeraliZ Loot : version allégée « C »", "",
           "%d modèles, %d faces opaques (le verre n'est jamais touché)." % (len(lignes), tot["faces"]), "",
           "| version | faces | gain |", "|---|---|---|",
           "| actuel | %d | |" % tot["faces"],
           "| A. faces cachées retirées (sans perte) | %d | -%s |" % (tot["apres_a"], pct(tot["cachees"], tot["faces"])),
           "| C. A + simplification (sommets à 0,4 px près) | %d | -%s |" % (tot["apres_c"], pct(tot["faces"] - tot["apres_c"], tot["faces"])),
           "",
           "Dont %d faces « dessous » (vues seulement si le bloc d'en dessous n'est pas plein : Minecraft les cache sinon)." % tot["dessous"],
           "",
           "## Contrôles (doivent être à 0)", "",
           "- points des vues où le modèle sans faces cachées montre autre chose que le complet (96 vues, avec et sans bloc "
           "plein dessous) : %d" % tot["ecarts_a"],
           "- percées du modèle C (fond, ou surface à plus de 1 px derrière, au milieu d'une surface du complet ; 96 vues) : %d"
           % tot["trous_c"],
           "- faces retournées ou tordues : %d ; faces collées, passées derrière ou pièces aplaties : %d ; faces dégénérées : %d"
           % (tot["retournees"], tot["collees"], tot["degenerees"]),
           "- modèles gardés sans simplification (cases gelées sans fin) : %s" % (", ".join(seuls_a) or "aucun"), "",
           "Rendus (`rendus/`) : actuel | C | écart des couleurs ×4 ; de près (en haut), de loin (milieu), de dos en rasant le sol (bas).",
           "", "Modèles rendus : " + (", ".join(rendus) or "aucun"), "",
           "## Par modèle (du plus lourd au plus léger)", "",
           "| modèle | faces | A | C | dessous | cases gelées | écart moyen (0-255) |", "|---|---|---|---|---|---|---|"]
    for r in sorted(lignes, key=lambda r: -r["faces"]):
        out.append("| %s | %d | %d | %d | %d | %d | %s |" % (r["nom"], r["faces"], r["apres_a"], r["apres_c"], r["dessous"], r["gel"],
                                                         "%.2f" % r["ecart_c"] if r["nom"] in rendus else ""))
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
        d.text((6, k * h + 3), "%s  -  actuel | C | ecart x4 (de pres, de loin, de dos)" % n, fill=(230, 230, 230))
    p.save(sortie / "planche.png")


def main():
    ap = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    ap.add_argument("--sortie", help="dossier des contrôles et rendus (jamais le mod)")
    ap.add_argument("--installer", action="store_true", help="écrire les données du jeu (conteneurs_lod.txt)")
    ap.add_argument("--controle", action="store_true", help="les données du jeu sont-elles à jour ? (code 1 sinon)")
    ap.add_argument("--rendus", type=int, default=12, help="rendre les N modèles les plus lourds (en plus de --modeles)")
    ap.add_argument("--modeles", default="", help="modèles à rendre en plus des plus lourds (virgules) ; tous sont traités")
    a = ap.parse_args()
    if not (a.sortie or a.installer or a.controle):
        ap.error("--sortie, --installer ou --controle")
    if a.sortie:
        sortie = Path(a.sortie).resolve()
        if PROJET in sortie.parents or sortie == PROJET:
            sys.exit("--sortie dans le module : refusé (les rendus ne vont pas dans le dépôt)")
        sortie.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        tmp = Path(tmp)
        if a.controle and not (a.sortie or a.installer):
            # rapide (quelques secondes) : le calcul du jeu retrouve-t-il les empreintes écrites ?
            if not DONNEES.exists():
                print("[lod] %s absent : python modules/loot/outils/lod_props.py --installer" % DONNEES.name)
                sys.exit(1)
            if java(["--controle", str(DONNEES)], bavard=True, verifier=False) != 0:
                print("[lod] conteneurs_lod.txt n'est plus à jour : python modules/loot/outils/lod_props.py --installer")
                sys.exit(1)
            print("[lod] conteneurs_lod.txt à jour")
            return
        # 1er passage : tous les modèles, sans rendu ; 2e : rendus des plus lourds et des modèles choisis
        lancer(tmp, [], [], bavard=False)
        lignes = lire(tmp)
        if a.installer:
            DONNEES.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(tmp / "conteneurs_lod.txt", DONNEES)
            print("écrit :", DONNEES.relative_to(PROJET.parent.parent))
        if not a.sortie:
            tot = {k: sum(r[k] for r in lignes) for k in ("faces", "apres_a", "apres_c")}
            print("faces : %d ; A %d ; C %d" % (tot["faces"], tot["apres_a"], tot["apres_c"]))
            return
        shutil.copyfile(tmp / "lod.csv", sortie / "lod.csv")
        connus = {r["nom"] for r in lignes}
        choisis = [m for m in a.modeles.split(",") if m]
        inconnus = [m for m in choisis if m not in connus]
        if inconnus:
            print("modèles inconnus, ignorés :", ", ".join(inconnus))
        rendus = list(dict.fromkeys([m for m in choisis if m in connus]
                                    + [r["nom"] for r in sorted(lignes, key=lambda r: -r["faces"])[:a.rendus]]))
        if rendus:
            t2 = tmp / "rendus_passage"
            lancer(t2, rendus, rendus)
            (sortie / "rendus").mkdir(exist_ok=True)
            for f in (t2 / "rendus").glob("*.png"):
                shutil.move(str(f), str(sortie / "rendus" / f.name))
            ecarts = {r["nom"]: r["ecart_c"] for r in lire(t2)}
            for r in lignes:
                if r["nom"] in ecarts:
                    r["ecart_c"] = ecarts[r["nom"]]
        tot = rapport(sortie, lignes, rendus)
        planche(sortie, rendus)
        print("faces : %d ; A %d ; C %d" % (tot["faces"], tot["apres_a"], tot["apres_c"]))
        print("contrôles : écarts A %d, trous C %d, retournées %d, collées %d, dégénérées %d"
              % (tot["ecarts_a"], tot["trous_c"], tot["retournees"], tot["collees"], tot["degenerees"]))
        print("écrit :", sortie / "rapport.md")


if __name__ == "__main__":
    main()
