"""Lettres russes (cyrillique) dans les polices de nos interfaces : DM Sans n'en a pas. Ajoute à chaque police du kit
« style clan » (assets/<modid>/font/*.json qui utilisent dmsans_medium.ttf ou dmsans_bold.ttf), juste après DM Sans,
la police Onest (même dessin géométrique, licence OFL) réduite aux seules lettres cyrilliques : DM Sans garde tout le
reste, Onest ne sert que pour le russe. Sans elle, le russe s'affichait dans la police en pixels de Minecraft.

    python outils/polices_cyrillique.py           ajoute ce qui manque (sans danger si relancé)

Sources : outils/polices/cyrillique_medium.ttf et cyrillique_bold.ttf (Onest 2.001, instances 500 et 700, glyphes
U+0400 à U+052F et « № »), licence outils/polices/onest_ofl.txt, copiée à côté de chaque police.
"""
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = ROOT / "outils" / "polices"
POIDS = {"dmsans_medium.ttf": "cyrillique_medium.ttf", "dmsans_bold.ttf": "cyrillique_bold.ttf"}


def traiter(fichier):
    """Ajoute Onest après chaque DM Sans du fichier de police ; True s'il a changé."""
    doc = json.loads(fichier.read_text(encoding="utf-8"))
    fournisseurs = doc.get("providers", [])
    noms = {p.get("file") for p in fournisseurs if isinstance(p, dict)}
    nouveau, change = [], False
    for p in fournisseurs:
        nouveau.append(p)
        if not isinstance(p, dict) or p.get("type") != "ttf":
            continue
        ns, _, nom = str(p.get("file", "")).partition(":")
        if nom not in POIDS:
            continue
        cyr = "%s:%s" % (ns, POIDS[nom])
        if cyr in noms:
            continue
        ajout = {k: v for k, v in p.items() if k not in ("file", "skip")}
        ajout["file"] = cyr
        nouveau.append(ajout)
        noms.add(cyr)
        change = True
        # la police va à côté de DM Sans (assets/<ns>/font/), avec sa licence
        dossier = fichier.parent
        for f in (POIDS[nom], "onest_ofl.txt"):
            cible = dossier / f
            if not cible.exists() or cible.read_bytes() != (SOURCES / f).read_bytes():
                shutil.copyfile(SOURCES / f, cible)
    if change:
        doc["providers"] = nouveau
        fichier.write_text(json.dumps(doc, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    return change


def main():
    modifies = [f for f in sorted(ROOT.glob("mods/*/src/*/resources/assets/*/font/*.json")) if traiter(f)]
    for f in modifies:
        print("[polices] cyrillique ajouté :", f.relative_to(ROOT))
    print("[polices] %d polices complétées" % len(modifies))


if __name__ == "__main__":
    main()
