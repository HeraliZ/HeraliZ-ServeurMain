"""Aperçu d'une caisse de HeraliZ Crates hors du jeu : son vrai modèle, ses textures et ses effets, en image PNG.

    python outils/apercu_caisse.py <caisse> <dossier> [--temps 0,40,90,150,210] [--vue yaw,pitch,distance]

Temps en ticks de l'ouverture (-1 = au repos ; repères : clé 0, déverrouillage 24, ouverture 34, roue 78 à 196, gain
200). Le dessin est fait par modules/boutique/outils/ApercuCaisse.java (moteur logiciel, sans Minecraft) avec JOML
(cache de Gradle, ou variable JOML_JAR), comme outils/formes_caisses.py. Ce n'est pas la lumière du jeu : sert à juger
formes, couleurs et chorégraphie avant un essai en jeu.
"""
import argparse
import os
import subprocess
import tempfile
from pathlib import Path

import formes_caisses as fc


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("caisse")
    ap.add_argument("dossier")
    ap.add_argument("--temps", default="-1,30,60,120,205")
    ap.add_argument("--vue", default="25,22,82")
    a = ap.parse_args()
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    jar = fc.joml()
    src = fc.SRC
    sources = [str(p) for d in ("mesh", "scene") for p in (src / "client" / "java" / "fr" / "heraliz" / "crates" / "client" / "render" / d).rglob("*.java")]
    sources += [str(src / "commun" / "java" / "fr" / "heraliz" / "crates" / "registry" / "Snd.java"),
                str(src / "commun" / "java" / "fr" / "heraliz" / "crates" / "block" / "CrateAnim.java"),
                str(fc.PROJET / "outils" / "ApercuCaisse.java")]
    res = src / "client" / "resources" / "assets" / "heralizcrates"
    sortie = Path(a.dossier)
    sortie.mkdir(parents=True, exist_ok=True)
    yaw, pitch, dist = a.vue.split(",")
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([fc.outil("javac"), "-nowarn", "-encoding", "UTF-8", "-d", tmp, "-cp", jar] + sources, check=True, env=env)
        cp = os.pathsep.join([tmp, jar])
        for t in a.temps.split(","):
            nom = sortie / ("%s_%s.png" % (a.caisse, "repos" if float(t) < 0 else "t%03d" % int(float(t))))
            subprocess.run([fc.outil("java"), "-cp", cp, "ApercuCaisse", str(res), str(nom), a.caisse, t, yaw, pitch, dist], check=True, env=env)
            print("écrit :", nom)


if __name__ == "__main__":
    main()
