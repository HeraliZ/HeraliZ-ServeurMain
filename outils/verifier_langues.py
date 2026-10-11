"""Contrôle des traductions de nos mods (français, anglais, russe) : pour chaque dossier assets/<modid>/lang, chaque
texte français (fr_fr.json, la référence) doit exister en anglais (en_us.json) et en russe (ru_ru.json), avec les mêmes
paramètres (%s, %d, %1$s, %%), les mêmes codes de couleur (§a, §l...) et les mêmes retours à la ligne. Signale aussi
les textes anglais restés en français. Un dossier sans fr_fr.json (texte d'origine en anglais) sert de référence à
son en_us.json.

    python outils/verifier_langues.py              tous nos mods
    python outils/verifier_langues.py heraliz-loot ces projets seulement
    python outils/verifier_langues.py --strict     les manques sont des erreurs (sinon : avertissements)

Les fichiers d'un même modid répartis entre src/commun, src/client et src/main sont réunis (même jar).
"""
import json
import re
import sys
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANGUES = ("fr_fr", "en_us", "ru_ru")
PARAM = re.compile(r"%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[sdfxXbcoeEgGn%]")
COULEUR = re.compile(r"§[0-9a-fk-orA-FK-OR]")
ACCENTS = set("éèêëàâçùûîïôœÉÈÊÀÇÙÛÎÔ")


def signature(texte):
    """Ce qui doit être identique entre les langues : paramètres, codes de couleur, nombre de retours à la ligne."""
    # %1$s / %2$s comptent comme %s : une langue peut choisir un autre argument (nom français ou anglais d'une salle)
    params = sorted(re.sub(r"^%\d+\$", "%", m) for m in PARAM.findall(texte))
    return (params, sorted(COULEUR.findall(texte)), texte.count("\n"))


def lire(fichiers):
    d = {}
    for f in fichiers:
        try:
            d.update(json.loads(f.read_text(encoding="utf-8")))
        except ValueError as e:
            raise SystemExit("[langues] JSON illisible : %s (%s)" % (f.relative_to(ROOT), e))
    return d


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    strict = "--strict" in sys.argv
    projets = args or sorted(p.name for p in (ROOT / "mods").iterdir() if p.is_dir() and not p.name.startswith("_"))
    erreurs, avertissements = [], []
    for projet in projets:
        # modid -> langue -> fichiers (src/commun, src/client, src/main...)
        groupes = defaultdict(lambda: defaultdict(list))
        for f in sorted((ROOT / "mods" / projet).glob("src/*/resources/assets/*/lang/*.json")):
            groupes[f.parent.parent.name][f.stem].append(f)
        for modid, langues in sorted(groupes.items()):
            ref_code = "fr_fr" if "fr_fr" in langues else "en_us" if "en_us" in langues else None
            if ref_code is None:
                continue
            ref = lire(langues[ref_code])
            for code in LANGUES:
                if code == ref_code:
                    continue
                if code == "fr_fr" and ref_code == "en_us":
                    continue  # texte d'origine en anglais (mod repris d'un autre auteur) : pas de français exigé
                trad = lire(langues.get(code, []))
                nom = "%s/%s %s" % (projet, modid, code)
                manque = [k for k in ref if k not in trad]
                if manque:
                    (erreurs if strict else avertissements).append("%s : %d texte(s) manquant(s) (ex. %s)" % (nom, len(manque), manque[0]))
                for k, v in trad.items():
                    if k not in ref or not isinstance(v, str) or not isinstance(ref[k], str):
                        continue
                    if signature(v) != signature(ref[k]):
                        erreurs.append("%s : %s : paramètres ou codes différents de %s (%r / %r)" % (nom, k, ref_code, v, ref[k]))
                    if code == "en_us" and ref_code == "fr_fr" and v == ref[k] and ACCENTS & set(v):
                        avertissements.append("%s : %s : encore en français (%r)" % (nom, k, v))
                    if code == "ru_ru" and v == ref[k] and re.search(r"[A-Za-zÀ-ÿ]{4,}", v) and not re.search(r"[А-Яа-яЁё]", v):
                        avertissements.append("%s : %s : pas traduit en russe (%r)" % (nom, k, v))
    for a in avertissements:
        print("[langues] avertissement :", a)
    for e in erreurs:
        print("[langues] ERREUR :", e)
    print("[langues] %d erreur(s), %d avertissement(s)" % (len(erreurs), len(avertissements)))
    sys.exit(1 if erreurs else 0)


if __name__ == "__main__":
    main()
