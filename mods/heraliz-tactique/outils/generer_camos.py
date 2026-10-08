#!/usr/bin/env python3
"""Fabrique les shaders des camos (un programme par camo) depuis le gabarit.

    python mods/heraliz-tactique/outils/generer_camos.py

Tout le code des camos est dans outils/camo_gabarit.fsh. Chaque camo a son propre programme camo_<id>.fsh : le gabarit
avec « #define CAMO_ID <id> », pour que la carte graphique ne compile que la branche de ce camo (petits shaders rapides).
La liste des camos est celle des fichiers camo_<id>.json déjà présents ; pour un nouveau camo, copier un camo_<id>.json en
changeant son « fragment », puis relancer ce script. Les camo_<id>.fsh produits vont dans Git avec le reste des sources.

On modifie donc toujours le gabarit, jamais les camo_<id>.fsh (ils seraient écrasés au prochain passage).
"""
import re
import sys
from pathlib import Path

MOD = Path(__file__).resolve().parent.parent
GABARIT = MOD / "outils" / "camo_gabarit.fsh"
CORE = MOD / "src" / "client" / "resources" / "assets" / "heralisz_camo" / "shaders" / "core"


def main():
    gabarit = GABARIT.read_text(encoding="utf-8")
    if gabarit.count("@CAMO_ID@") != 1:
        sys.exit("[camos] le gabarit doit contenir une seule fois @CAMO_ID@")
    ids = sorted(int(m.group(1)) for f in CORE.iterdir() if (m := re.fullmatch(r"camo_(\d+)\.json", f.name)))
    if not ids:
        sys.exit("[camos] aucun camo_<id>.json dans " + str(CORE))
    changes = 0
    for i in ids:
        cible = CORE / ("camo_%d.fsh" % i)
        texte = gabarit.replace("@CAMO_ID@", str(i))
        ancien = cible.read_text(encoding="utf-8") if cible.exists() else None
        if ancien != texte:
            # LF comme le reste des sources, quel que soit le PC
            cible.write_bytes(texte.encode("utf-8"))
            changes += 1
    print("[camos] %d programmes, %d mis à jour" % (len(ids), changes))


if __name__ == "__main__":
    main()
