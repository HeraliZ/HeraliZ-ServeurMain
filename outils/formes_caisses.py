"""Recalcule les hitbox des caisses de HeraliZ Crates à partir de leurs vrais modèles 3D.

    python outils/formes_caisses.py

À relancer après tout changement du modèle d'une caisse (taille, pièces, cadenas) ou après l'ajout d'une caisse, puis
refabriquer heraliz-crates. Écrit mods/heraliz-crates/src/commun/java/fr/heraliz/crates/block/CrateFormes.java (fichier
généré, à envoyer dans Git avec le changement de modèle).

Le calcul est fait par mods/heraliz-crates/outils/FormesCaisses.java (méthode expliquée dans le fichier) : il compile
seulement le code des modèles (Java pur, sans Minecraft) avec JOML, pris dans le cache de Gradle du PC (il y est dès
que le mod a été fabriqué une fois) ou donné par la variable d'environnement JOML_JAR.
Besoin : un JDK 17 (javac), comme pour la fabrication.
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[1]
PROJET = DEPOT / "mods" / "heraliz-crates"
SRC = PROJET / "src"
SORTIE = SRC / "commun" / "java" / "fr" / "heraliz" / "crates" / "block" / "CrateFormes.java"


def outil(nom):
    for base in (os.environ.get("JAVA_HOME"), None):
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
    jars = sorted(caches.rglob("joml-1.*.jar")) if caches.exists() else []
    jars = [j for j in jars if "sources" not in j.name]
    if not jars:
        sys.exit("JOML introuvable dans %s : fabriquer heraliz-crates une fois (Gradle le télécharge) "
                 "ou donner son chemin dans JOML_JAR" % caches)
    return str(jars[-1])


def caisses():
    """Identifiants des caisses, dans l'ordre de CrateType."""
    s = (SRC / "commun" / "java" / "fr" / "heraliz" / "crates" / "crate" / "CrateType.java").read_text(encoding="utf-8")
    return re.findall(r'^    [A-Z_0-9]+\("([a-z_0-9]+)"', s, re.M)


def main():
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    jar = joml()
    sources = [str(p) for d in ("mesh", "scene") for p in (SRC / "client" / "java" / "fr" / "heraliz" / "crates" / "client" / "render" / d).rglob("*.java")]
    sources += [str(SRC / "commun" / "java" / "fr" / "heraliz" / "crates" / "registry" / "Snd.java"),
                str(SRC / "commun" / "java" / "fr" / "heraliz" / "crates" / "block" / "CrateAnim.java"),
                str(PROJET / "outils" / "FormesCaisses.java")]
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([outil("javac"), "-nowarn", "-encoding", "UTF-8", "-d", tmp, "-cp", jar] + sources, check=True, env=env)
        cp = os.pathsep.join([tmp, jar])
        subprocess.run([outil("java"), "-cp", cp, "FormesCaisses", str(SORTIE)] + caisses(), check=True, env=env)
    print("écrit :", SORTIE.relative_to(DEPOT))


if __name__ == "__main__":
    main()
