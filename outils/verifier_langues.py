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

Tous les modules (21/11, audit des données) : chaque clé donnée en toutes lettres à un appel de traduction
(Component.translatable, translatableWithFallback, I18n.get / exists, Language.getOrDefault, et les petites méthodes
« t / tr / texte (String cle, ...) » qui ajoutent un préfixe : « "gui.heraliz_interface." + cle ») est cherchée dans les
textes RÉUNIS de tous nos jars, des mods des autres auteurs (serveur/mods-tiers, client/mods-tiers) et de Minecraft
(en_us du pack « textures ») : chez le joueur, tous les fichiers de langue ne font qu'un. Absente des trois langues :
erreur (le joueur voit la clé brute, ou le texte français de secours en anglais et en russe) ; absente d'une langue
seulement : avertissement. Un appel avec moins d'arguments que le texte n'a de %s (« new Object[]{a, b} » compté
comme deux, « %1$s » deux fois comme un seul) : erreur.
"""
import io
import json
import re
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
LANGUES = ("fr_fr", "en_us", "ru_ru")
PARAM = re.compile(r"%(?:\d+\$)?[-#+0,(]*\d*(?:\.\d+)?[sdfxXbcoeEgGn%]")
COULEUR = re.compile(r"§[0-9a-fk-orA-FK-OR]")
ACCENTS = set("éèêëàâçùûîïôœÉÈÊÀÇÙÛÎÔ")
# module -> modids dont les clés citées dans le code sont contrôlées (les autres modules ont encore des faux positifs :
# préfixes « …_ » complétés dans le code, noms de propriétés système)
# 19/11 (audit) : pnj (heraliz_quetes) ajouté, trois de ses clés manquaient dans les trois langues
CLES_DU_CODE = {"bases": ("crac",), "pnj": ("heraliz_quetes",)}
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


# ------------------------------------------------------------------------------------------------ tous les modules
JAMAIS_LIVRES = ("_gabarit", "capture", "banc")
# code des autres auteurs dans nos jars (outils/construire.py, DOSSIERS_TIERS) : un défaut y est seulement signalé (on ne
# change pas leur code pour un texte ; ex. Backpacked cite « backpacked.backpack.wandering_bag », son texte s'appelle
# « backpack.backpacked.wandering_bag » : nom du sac des marchands ambulants, absents de la carte)
CODE_TIERS = ("com/mrcrayfish/", "com/hbm/", "dev/kosmx/", "io/github/kosmx/", "org/concentus/")
VANILLE = ROOT / "modules/interface/src/main/resources/resourcepacks/textures/assets/minecraft/lang/en_us.json"
# méthode NOM(String cle, ...) : son corps appelle une traduction, avec ou sans préfixe « "gui.x." + cle »
DECLARATION = re.compile(r"(?:static|public|private|protected)[^;{=()]*?\b([a-zA-Z_]\w*)\(\s*(?:final\s+)?String\s+(\w+)"
                         r"\s*(,\s*(?:final\s+)?String\s+\w+)?[^)]*\)\s*(?:throws[^{]*)?\{")
# appel avec une clé en toutes lettres, complète (suivie de « , » ou « ) ») : Classe.nom("cle" ou nom("cle"
APPEL_CLE = re.compile(r'(?:\b([A-Z]\w*)\.)?(?<!\w)([a-zA-Z_]\w*)\(\s*"([a-z0-9_]+(?:\.[A-Za-z0-9_\-]+)*)"\s*(?=[,)])')


def textes_reunis():
    """Textes de tous nos jars, des autres auteurs et de Minecraft : (clés par langue, texte de référence par clé)."""
    cles = {code: set() for code in LANGUES}
    ref = {}
    for f in sorted(ROOT.glob("modules/*/src/*/resources/assets/*/lang/*.json")):
        if f.relative_to(ROOT).parts[1] in JAMAIS_LIVRES or f.stem not in LANGUES:
            continue
        d = lire([f])
        cles[f.stem].update(d)
        for k, v in d.items():
            if isinstance(v, str) and (f.stem == "fr_fr" or k not in ref):
                ref[k] = v
    autres = set()
    if VANILLE.exists():
        d = lire([VANILLE])
        autres.update(d)
        for k, v in d.items():
            ref.setdefault(k, v)

    def jar(z):
        for n in z.namelist():
            m = re.match(r"assets/[^/]+/lang/(\w+)\.json$", n)
            if m and m.group(1) in LANGUES:
                try:
                    d = json.loads(z.read(n).decode("utf-8", "replace"))
                except ValueError:
                    continue
                autres.update(d)
                for k, v in d.items():
                    if isinstance(v, str):
                        ref.setdefault(k, v)
            elif n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
                jar(zipfile.ZipFile(io.BytesIO(z.read(n))))
    for j in sorted(ROOT.glob("serveur/mods-tiers/*.jar")) + sorted(ROOT.glob("client/mods-tiers/*.jar")):
        with zipfile.ZipFile(j) as z:
            jar(z)
    for code in LANGUES:
        cles[code] |= autres
    return cles, ref


def arguments(src, i):
    """Arguments (texte brut) d'un appel, i juste après le premier ; None si la fin de l'appel n'est pas trouvée."""
    out, prof, cur, chaine = [], 0, "", None
    while i < len(src):
        c = src[i]
        if chaine:
            cur += c
            if c == "\\" and i + 1 < len(src):
                cur += src[i + 1]
                i += 2
                continue
            if c == chaine:
                chaine = None
        elif c in "\"'":
            chaine = c
            cur += c
        elif c in "([{":
            prof += 1
            cur += c
        elif c in ")]}":
            if prof == 0:
                if cur.strip():
                    out.append(cur.strip())
                return out
            prof -= 1
            cur += c
        elif c == "," and prof == 0:
            if cur.strip():
                out.append(cur.strip())
            cur = ""
        else:
            cur += c
        i += 1
    return None


def compter(args):
    """Nombre d'arguments donnés au texte (« new Object[]{a, b} » : deux) ; -1 : tableau passé tel quel, inconnu."""
    if len(args) == 1:
        m = re.match(r"new\s+Object\s*\[\s*\]\s*\{(.*)\}$", args[0], re.S)
        if m:
            dedans = arguments(m.group(1) + ")", 0)
            return len(dedans) if dedans is not None else -1
        if re.match(r"^\w+$", args[0]) and args[0] in ("args", "a", "arguments", "params", "parametres", "valeurs"):
            return -1
    return len(args)


def attendus(texte):
    """Arguments demandés par un texte : %s comptés un par un, %1$s par leur plus grand numéro."""
    n, plus_grand = 0, 0
    for m in re.finditer(r"%(?:(\d+)\$)?[-#+0,(]*\d*(?:\.\d+)?([sdfxXbcoeEgGn%])", texte):
        if m.group(2) == "%":
            continue
        if m.group(1):
            plus_grand = max(plus_grand, int(m.group(1)))
        else:
            n += 1
    return max(n, plus_grand)


def cles_tous_modules(projets, textes, ref):
    """Clés des appels de traduction de ces projets contre les textes réunis ; rend (erreurs, avertissements)."""
    erreurs, avertissements = [], []
    sources = {}
    enveloppes = {}   # (classe, méthode) -> (préfixe, texte de secours en 2e argument)
    for f in sorted(ROOT.glob("modules/*/src/*/java/**/*.java")):
        p = f.relative_to(ROOT).parts
        if p[1] in JAMAIS_LIVRES or p[3] == "essais":
            continue
        src = f.read_text(encoding="utf-8", errors="replace")
        if p[1] in projets:
            sources[f] = src
        for m in DECLARATION.finditer(src):
            nom, param, secours = m.group(1), m.group(2), m.group(3)
            corps = src[m.end():m.end() + 400]
            if "\n    }" in corps:
                corps = corps[:corps.index("\n    }")]
            mp = re.search(r'"([a-z0-9_]+(?:\.[a-z0-9_]+)*\.)"\s*\+\s*' + re.escape(param) + r"\b", corps)
            if mp and re.search(r"translatable|I18n|Language|\btr\(|TexteClient|\bt\(", corps):
                enveloppes[(f.stem, nom)] = (mp.group(1), bool(secours))
            elif re.search(r"(translatable(WithFallback)?|I18n\.get)\(\s*" + re.escape(param) + r"\b", corps):
                enveloppes.setdefault((f.stem, nom), ("", bool(secours)))
    for f, src in sources.items():
        rel = f.relative_to(ROOT)
        tiers = "/".join(rel.parts[5:]).startswith(CODE_TIERS)
        erreurs_ici = avertissements if tiers else erreurs
        for m in APPEL_CLE.finditer(src):
            classe, meth, cle = m.group(1), m.group(2), m.group(3)
            if meth in ("translatable", "translatableWithFallback"):
                prefixe, secours = "", meth == "translatableWithFallback"
            elif classe == "I18n" and meth in ("get", "exists"):
                prefixe, secours = "", False
            elif meth == "getOrDefault" and "Language" in src[max(0, m.start() - 40):m.start()]:
                prefixe, secours = "", False
            elif (classe or f.stem, meth) in enveloppes:
                prefixe, secours = enveloppes[(classe or f.stem, meth)]
            else:
                continue
            cle = prefixe + cle
            if "." not in cle or cle.endswith((".", "_")):
                continue
            ligne = src.count("\n", 0, m.start()) + 1
            absente = [code for code in LANGUES if cle not in textes[code]]
            if len(absente) == len(LANGUES):
                erreurs_ici.append("%s:%d : %s(\"%s\") : clé absente des trois langues (%s)" % (
                    rel, ligne, meth, cle, "texte français de secours pour tous" if secours else "le joueur verrait la clé brute"))
                continue
            if absente:
                avertissements.append("%s:%d : clé %s absente de %s" % (rel, ligne, cle, ", ".join(absente)))
            texte = ref.get(cle)
            a = arguments(src, m.end())
            if not isinstance(texte, str) or a is None:
                continue
            if secours and a and a[0].startswith('"'):
                a = a[1:]   # texte français de secours, pas un argument
            n = compter(a)
            if 0 <= n < attendus(texte):
                erreurs_ici.append("%s:%d : %s(\"%s\") : %d argument(s) pour %d %%s (texte cassé en jeu : %r)" % (
                    rel, ligne, meth, cle, n, attendus(texte), texte[:60]))
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
    # clés des appels de traduction de tous les modules, contre les textes réunis de tous les jars (21/11)
    textes, ref = textes_reunis()
    e, a = cles_tous_modules(set(projets), textes, ref)
    erreurs += [x for x in e if x not in erreurs]
    avertissements += [x for x in a if x not in avertissements]
    for a in avertissements:
        print("[langues] avertissement :", a)
    for e in erreurs:
        print("[langues] ERREUR :", e)
    print("[langues] %d erreur(s), %d avertissement(s)" % (len(erreurs), len(avertissements)))
    sys.exit(1 if erreurs else 0)


if __name__ == "__main__":
    main()
