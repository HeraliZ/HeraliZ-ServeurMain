"""Contrôle des traductions de nos mods (français, anglais, russe) : pour chaque dossier assets/<modid>/lang, chaque
texte français (fr_fr.json, la référence) doit exister en anglais (en_us.json) et en russe (ru_ru.json), avec les mêmes
paramètres (%s, %d, %1$s, %%), les mêmes codes de couleur (§a, §l...) et les mêmes retours à la ligne. Signale aussi
les textes anglais restés en français. Un dossier sans fr_fr.json (texte d'origine en anglais) sert de référence à
son en_us.json.

    python outils/verifier_langues.py              tous nos mods
    python outils/verifier_langues.py heraliz-loot ces projets seulement
    python outils/verifier_langues.py --strict     les manques sont des erreurs (sinon : avertissements)

Les fichiers d'un même modid répartis entre src/commun, src/client et src/main sont réunis (même jar).

Clés citées dans le code (03/11, le propriétaire voyait « crac.build.need » brut en jeu) : pour les modules de
CLES_DU_CODE, chaque clé écrite en toutes lettres dans le Java (« "crac.build.need" ») doit exister dans le texte de
référence, et un appel de traduction (translatable, translatableWithFallback, Txt.k / red / green…, T.t) ne doit pas
donner moins d'arguments que le texte n'a de %s : Minecraft montre sinon le texte cassé. Les essais (src/essais) et les
débuts de clé complétés dans le code (« "crac.mort.raison." + n ») ne sont pas vus.
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
# module -> modids dont les clés citées dans le code sont contrôlées (les autres modules ont encore des faux positifs :
# préfixes « …_ » complétés dans le code, noms de propriétés système)
CLES_DU_CODE = {"bases": ("crac",)}
# appels de traduction : la clé en premier argument ; translatableWithFallback et T.t ont ensuite le texte français
APPEL = re.compile(r'\b(translatable|translatableWithFallback|k|red|green|yellow|gray|tr|t|c|fail|error)\(\s*"([a-z0-9_]+\.[A-Za-z0-9_.]+)"')
AVEC_TEXTE = ("translatableWithFallback", "t")


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


def nombre_arguments(src, i):
    """Arguments d'un appel après sa clé (i : juste après le littéral de la clé) ; -1 si la fin de l'appel n'est pas trouvée."""
    n, prof, chaine = 0, 0, None
    while i < len(src):
        c = src[i]
        if chaine:
            if c == "\\":
                i += 2
                continue
            if c == chaine:
                chaine = None
        elif c in "\"'":
            chaine = c
        elif c in "([{":
            prof += 1
        elif c in ")]}":
            if prof == 0:
                return n
            prof -= 1
        elif c == "," and prof == 0:
            n += 1
        i += 1
    return -1


def cles_du_code(projet, textes):
    """
    Clés du code absentes d'une des trois langues, ou appelées avec trop peu d'arguments (textes : modid -> langue ->
    textes ; la référence est le français).
    """
    erreurs, avertissements = [], []
    litteral = re.compile(r'"((?:%s)\.[A-Za-z0-9_.]+)"' % "|".join(re.escape(m) for m in textes))
    for f in sorted((ROOT / "modules" / projet).glob("src/*/java/**/*.java")):
        if f.relative_to(ROOT / "modules" / projet).parts[1] == "essais":
            continue
        src = f.read_text(encoding="utf-8", errors="replace")
        rel = f.relative_to(ROOT)
        vues = set()
        for m in litteral.finditer(src):
            cle = m.group(1)
            if cle.endswith((".", "_")) or cle in vues:
                continue   # début de clé complété dans le code
            vues.add(cle)
            absente = [code for code in LANGUES if cle not in textes[cle.split(".")[0]].get(code, {})]
            if absente:
                erreurs.append("%s:%d : clé %s absente de %s (le joueur la verrait brute)"
                               % (rel, src.count("\n", 0, m.start()) + 1, cle, ", ".join(absente)))
        for m in APPEL.finditer(src):
            meth, cle = m.group(1), m.group(2)
            ref = textes.get(cle.split(".")[0], {}).get("fr_fr")
            if ref is None or cle not in ref or not isinstance(ref[cle], str):
                continue
            n = nombre_arguments(src, m.end())
            if n < 0:
                continue
            if meth in AVEC_TEXTE and re.match(r'\s*,\s*"', src[m.end():]):
                n -= 1   # texte français de secours, pas un argument
            attendus = len([p for p in PARAM.findall(ref[cle]) if p != "%%"])
            if n < attendus:
                erreurs.append("%s:%d : %s(\"%s\") : %d argument(s) pour %d %%s (texte cassé en jeu)"
                               % (rel, src.count("\n", 0, m.start()) + 1, meth, cle, n, attendus))
            elif n > attendus:
                avertissements.append("%s:%d : %s(\"%s\") : %d argument(s) pour %d %%s"
                                      % (rel, src.count("\n", 0, m.start()) + 1, meth, cle, n, attendus))
    return erreurs, avertissements


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    strict = "--strict" in sys.argv
    projets = args or sorted(p.name for p in (ROOT / "modules").iterdir() if p.is_dir() and not p.name.startswith("_"))
    erreurs, avertissements = [], []
    for projet in projets:
        # modid -> langue -> fichiers (src/commun, src/client, src/main...)
        groupes = defaultdict(lambda: defaultdict(list))
        for f in sorted((ROOT / "modules" / projet).glob("src/*/resources/assets/*/lang/*.json")):
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
        if projet in CLES_DU_CODE:
            textes = {modid: {code: lire(langues.get(code, [])) for code in LANGUES}
                      for modid, langues in groupes.items() if modid in CLES_DU_CODE[projet]}
            e, a = cles_du_code(projet, textes)
            erreurs += e
            avertissements += a
    for a in avertissements:
        print("[langues] avertissement :", a)
    for e in erreurs:
        print("[langues] ERREUR :", e)
    print("[langues] %d erreur(s), %d avertissement(s)" % (len(erreurs), len(avertissements)))
    sys.exit(1 if erreurs else 0)


if __name__ == "__main__":
    main()
