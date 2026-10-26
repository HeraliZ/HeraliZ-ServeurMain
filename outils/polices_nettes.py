"""Polices nettes : monte l'« oversample » des polices TTF de nos mods (22/10, demande du propriétaire).

    python outils/polices_nettes.py            réécrit les polices (sans rien faire si c'est déjà à jour)
    python outils/polices_nettes.py --essai    montre seulement ce qui changerait

Chaque police `mods/*/src/**/assets/<espace>/font/*.json` : tout fournisseur « ttf » dont l'oversample est sous
OVERSAMPLE y passe (taille inchangée) ; « bitmap », « space », « reference » ne sont pas touchés. Les lettres sont
ensuite lissées chez le joueur (heraliz_interface, `client/PolicesNettes`) : une planche plus fine, lue en GL_LINEAR,
donne des lettres nettes à toutes les échelles d'interface.

Gardées telles quelles (EXCLUES) : les polices de la fiche `/base info` de CRac, réglées à la main pour son échelle non
entière (oversample 3), la référence de netteté.

Contrôle aussi que chaque espace de noms qui a des polices est dans `NOS_ESPACES` de
`modules/interface/.../client/PolicesNettes.java` (sinon ses lettres ne seraient pas lissées).
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OVERSAMPLE = 8.0
EXCLUES = {"crac/font/ui_fiche.json", "crac/font/ui_fiche_gros.json", "crac/font/ui_fiche_mini.json"}
POLICES_NETTES = ROOT / "modules/interface/src/main/java/fr/heraliz/interfaces/client/PolicesNettes.java"


def cible(taille):
    """Oversample voulu : 8, plafonné pour les grandes polices (25/10) : Minecraft range les lettres dans des planches de
    256 x 256 points ; une lettre de taille x oversample plus grande n'y tient pas et disparaît (lettres, accents). Plafond
    240 / taille, arrondi vers le bas (46 -> 5, 33 -> 7)."""
    return float(max(1, min(int(OVERSAMPLE), int(240 // max(1.0, taille)))))


def polices():
    for p in sorted((ROOT / "modules").glob("*/src/**/assets/*/font/*.json")):
        if "build" in p.relative_to(ROOT).parts:
            continue
        yield p


def main():
    essai = "--essai" in sys.argv
    changees = 0
    espaces = set()
    for p in polices():
        espace = p.parent.parent.name
        espaces.add(espace)
        if f"{espace}/font/{p.name}" in EXCLUES:
            continue
        donnees = json.loads(p.read_text(encoding="utf-8"))
        modifs = []
        for f in donnees.get("providers", []):
            if f.get("type") != "ttf":
                continue
            avant = float(f.get("oversample", 1.0))
            voulu = cible(float(f.get("size", 11.0)))
            if avant != voulu:
                f["oversample"] = voulu
                modifs.append(f"{f.get('file')} {avant:g} -> {voulu:g}")
        if modifs:
            changees += 1
            print(f"{p.relative_to(ROOT)} : " + ", ".join(modifs))
            if not essai:
                p.write_text(json.dumps(donnees, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"{changees} police(s) {'à changer' if essai else 'changée(s)'}.")

    connus = set(re.findall(r'"([a-z0-9_]+)"', POLICES_NETTES.read_text(encoding="utf-8").split("NOS_ESPACES", 1)[1]
                            .split(");", 1)[0])) if POLICES_NETTES.exists() else set()
    manquants = sorted(espaces - connus)
    if manquants:
        print("ATTENTION : espaces de noms de polices absents de NOS_ESPACES (PolicesNettes.java) : " + ", ".join(manquants))
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
