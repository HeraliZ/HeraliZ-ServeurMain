#!/usr/bin/env python3
"""Fabrique les shaders des camos (un programme par camo) depuis le gabarit.

    python modules/armes/outils/generer_camos.py

Tout le code des camos est dans outils/camo_gabarit.fsh. Chaque camo a son propre programme camo_<id>.fsh, pour que la
carte graphique ne compile que la branche de ce camo (petits shaders rapides) : « #define CAMO_ID <id> » puis
« #moj_import <heralisz_camo.glsl> ». Le code commun (le gabarit sans ses deux premières lignes) n'existe qu'en UN
exemplaire, assets/minecraft/shaders/include/heralisz_camo.glsl, que Minecraft recopie à la compilation de chaque
programme (même chemin que son fog.glsl) : même programme compilé qu'avant, mais ~190 Ko dans le jar au lieu de ~13 Mo
(audit du 02/11, mineur 11 : des dizaines de copies de 191 Ko qui ne différaient que d'une ligne).
La liste des camos est celle des fichiers camo_<id>.json déjà présents ; pour un nouveau camo, copier un camo_<id>.json en
changeant son « fragment », puis relancer ce script. Les fichiers produits vont dans Git avec le reste des sources.

On modifie donc toujours le gabarit, jamais les camo_<id>.fsh ni heralisz_camo.glsl (ils seraient écrasés au prochain
passage).
"""
import re
import sys
from pathlib import Path

MOD = Path(__file__).resolve().parent.parent
GABARIT = MOD / "outils" / "camo_gabarit.fsh"
CORE = MOD / "src" / "client" / "resources" / "assets" / "heralisz_camo" / "shaders" / "core"
# code commun, inclus par « #moj_import <heralisz_camo.glsl> » (Minecraft cherche dans minecraft:shaders/include/)
INCLUS = MOD / "src" / "client" / "resources" / "assets" / "minecraft" / "shaders" / "include" / "heralisz_camo.glsl"
ENTETE = "#version 150\n#define CAMO_ID @CAMO_ID@\n"
PROGRAMME = ("#version 150\n#define CAMO_ID %d\n"
             "// fichier généré par outils/generer_camos.py depuis outils/camo_gabarit.fsh (dossier du mod) : ne pas modifier ici.\n"
             "// Le code des camos est dans assets/minecraft/shaders/include/heralisz_camo.glsl (un seul exemplaire).\n"
             "#moj_import <heralisz_camo.glsl>\n")


def ecrire(cible, texte):
    """Écrit si le contenu change (LF comme le reste des sources, quel que soit le PC) ; vrai si écrit."""
    ancien = cible.read_bytes() if cible.exists() else None
    if ancien == texte.encode("utf-8"):
        return False
    cible.parent.mkdir(parents=True, exist_ok=True)
    cible.write_bytes(texte.encode("utf-8"))
    return True


def main():
    gabarit = GABARIT.read_text(encoding="utf-8").replace("\r\n", "\n")
    if gabarit.count("@CAMO_ID@") != 1 or not gabarit.startswith(ENTETE):
        sys.exit("[camos] le gabarit doit commencer par « #version 150 » puis « #define CAMO_ID @CAMO_ID@ » (seul @CAMO_ID@)")
    ids = sorted(int(m.group(1)) for f in CORE.iterdir() if (m := re.fullmatch(r"camo_(\d+)\.json", f.name)))
    if not ids:
        sys.exit("[camos] aucun camo_<id>.json dans " + str(CORE))
    changes = int(ecrire(INCLUS, gabarit[len(ENTETE):]))
    for i in ids:
        changes += int(ecrire(CORE / ("camo_%d.fsh" % i), PROGRAMME % i))
    print("[camos] %d programmes et le code commun, %d fichier(s) mis à jour" % (len(ids), changes))


if __name__ == "__main__":
    main()
