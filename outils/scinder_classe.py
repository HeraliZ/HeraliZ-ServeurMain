"""Coupe une classe Java en deux : la classe commune (gardée en place, dans src/commun/java) et sa sous-classe serveur
<Nom>Serveur (créée dans src/serveur/java, même paquet). Méthode : docs/METHODE_CLIENT_SERVEUR.md §5.

    python outils/scinder_classe.py <Fichier.java> --liste
    python outils/scinder_classe.py <Fichier.java> --serveur nom1,nom2,... [--deux nom3,...] [--essai]

<Fichier.java> : le fichier dans src/commun/java d'un projet fusionné (le dossier src/serveur/java est trouvé à côté).
--liste   : affiche les membres de la classe (genre, nom, lignes, indices « S » = mots serveur, « C » = mots client) pour
            décider ; ne modifie rien.
--serveur : membres (champs, méthodes, types internes, par NOM : toutes les surcharges) DÉPLACÉS dans <Nom>Serveur.
--deux    : méthodes gardées dans la classe commune (version client / neutre, à réduire ensuite à la main) ET recopiées
            telles quelles dans <Nom>Serveur, qui les redéfinit (@Override ajouté, « private » retiré des deux côtés).
--essai   : montre ce qui serait fait sans rien écrire.
--imports : seulement retirer les imports inutiles du fichier (après une retouche à la main).
--vers <Fichier.java> : ajoute les membres à une sous-classe serveur EXISTANTE (même paquet) au lieu de créer
            <Nom>Serveur : pour une classe mère abstraite qui porte de la logique (HeraliszBoss<A> -> SujetN07Serveur, §5.5).
--type A=Action : remplace un paramètre de type de la classe mère dans le texte déplacé (avec --vers).
Le reste ne bouge pas. Les constructeurs restent communs ; <Nom>Serveur reçoit les mêmes, qui appellent super(...).
Visibilité : un membre « private » de la classe commune cité par <Nom>Serveur devient « package-private » (même paquet,
autre ensemble de sources : javac et le jar l'acceptent). Imports : chaque fichier garde ceux qu'il utilise.
Avertit si la classe commune cite encore un membre déplacé (à classer en --deux avec une version neutre), et liste les
autres fichiers du projet qui appellent un membre statique déplacé (à réécrire en <Nom>Serveur.membre).
"""
import re
import sys
from pathlib import Path

MOTS_S = re.compile(r"\b(ServerLevel|ServerPlayer|sendParticles|customServerAiStep|goalSelector|targetSelector|CompoundTag|bossBar|"
                    r"ServerBossEvent|hurt\(|Config\.|addFreshEntity|setBlock\(|dropLoot|getNavigation|Vfx\.)")
MOTS_C = re.compile(r"\b(partial|Partial|registerControllers|AnimationController|cinematic|hud|Hud|client|Client|render|Render)")


# ------------------------------------------------------------------ lecture
def masque(src):
    """Copie du source où commentaires, chaînes et caractères sont remplacés par des espaces (mêmes positions)."""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                out[k] = " "
            i = j
        elif src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if src[k] != "\n":
                    out[k] = " "
            i = j
        elif src.startswith('"""', i):
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
            for k in range(i + 1, j - 1):
                if src[k] != "\n":
                    out[k] = " "
            i = j
        elif c in "\"'":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            for k in range(i + 1, min(j, n)):
                out[k] = " "
            i = j + 1
        else:
            i += 1
    return "".join(out)


class Membre:
    def __init__(self, debut, fin, entete, genre, noms, prive, statique):
        self.debut, self.fin, self.entete, self.genre, self.noms = debut, fin, entete, genre, noms
        self.prive, self.statique = prive, statique


def sans_annotations(t):
    out, i = [], 0
    while i < len(t):
        m = re.match(r"@[\w.]+", t[i:])
        if m and (i == 0 or not (t[i - 1].isalnum() or t[i - 1] == "_")):
            i += m.end()
            j = i
            while j < len(t) and t[j] in " \t\r\n":
                j += 1
            if j < len(t) and t[j] == "(":
                d = 0
                while j < len(t):
                    d += t[j] == "("
                    d -= t[j] == ")"
                    j += 1
                    if d == 0:
                        break
                i = j
            continue
        out.append(t[i])
        i += 1
    return "".join(out)


def membres(src, nom_classe):
    m = masque(src)
    decl = re.search(r"\b(class|enum|record|interface)\s+" + nom_classe + r"\b", m)
    if not decl:
        raise SystemExit(f"classe {nom_classe} introuvable")
    corps = m.index("{", decl.end())
    out, i, n = [], corps + 1, len(m)
    while True:
        # début : premier caractère non blanc (les commentaires du source d'origine suivent le membre)
        j = i
        while j < n and m[j] in " \t\r\n":
            j += 1
        if j >= n or m[j] == "}":
            fin_classe = j
            break
        prof, par, egal, k, fin = 0, 0, False, j, None
        while k < n:
            c = m[k]
            if c == "(":
                par += 1
            elif c == ")":
                par -= 1
            elif c == "=" and par == 0 and prof == 0 and m[k + 1] != "=" and m[k - 1] not in "=!<>":
                egal = True
            elif c == "{":
                prof += 1
            elif c == "}":
                prof -= 1
                if prof == 0 and not egal:
                    fin = k + 1
                    break
            elif c == ";" and prof == 0 and par == 0:
                fin = k + 1
                break
            k += 1
        if fin is None:
            raise SystemExit("fin de membre introuvable (accolades déséquilibrées ?)")
        # bornes dans le source : on remonte aux commentaires et à la ligne vide qui précèdent
        deb = i
        entete_m = sans_annotations(m[j:fin])
        coupe = re.search(r"[{;=]", entete_m)
        entete = entete_m[:coupe.start()] if coupe else entete_m
        t = re.search(r"\b(class|interface|enum|record)\s+(\w+)", entete)
        if t and "(" not in entete.split(t.group(0))[0]:
            genre, noms = "type", [t.group(2)]
        elif re.match(r"\s*(static\s*)?$", entete):
            genre, noms = "bloc", ["<clinit>" if "static" in entete else "<init-bloc>"]
        elif "(" in entete:
            nm = re.search(r"(\w+)\s*\(", entete)
            genre = "constructeur" if nm.group(1) == nom_classe else "méthode"
            noms = [nm.group(1)]
        else:
            genre = "champ"
            decl_txt = entete_m.rstrip(";")
            if "=" in entete_m and coupe and entete_m[coupe.start()] == "=":
                # « long a, b, c = 1, d = 2; » : noms avant le premier « = » (séparés par des virgules hors <>), puis après
                avant, d, cour = [], 0, ""
                for c in entete_m[:coupe.start()]:
                    if c == "<":
                        d += 1
                    elif c == ">":
                        d -= 1
                    if c == "," and d == 0:
                        avant.append(cour)
                        cour = ""
                    else:
                        cour += c
                avant.append(cour)
                noms = [re.search(r"(\w+)\s*(\[\s*\])*\s*$", x).group(1) for x in avant]
                reste = entete_m[coupe.start() + 1:]
                d = 0
                for idx, c in enumerate(reste):
                    if c in "([{":
                        d += 1
                    elif c in ")]}":
                        d -= 1
                    elif c == "," and d == 0:
                        mm = re.match(r"\s*(\w+)\s*(=|,|;|$)", reste[idx + 1:])
                        if mm:
                            noms.append(mm.group(1))
            else:
                parties, d, cour = [], 0, ""
                for c in decl_txt:
                    if c == "<":
                        d += 1
                    elif c == ">":
                        d -= 1
                    if c == "," and d == 0:
                        parties.append(cour)
                        cour = ""
                    else:
                        cour += c
                parties.append(cour)
                noms = [re.search(r"(\w+)\s*(\[\s*\])*\s*$", p).group(1) for p in parties]
        mots = re.findall(r"\b\w+\b", entete.split("(")[0])
        out.append(Membre(deb, fin, entete.strip(), genre, noms, "private" in mots, "static" in mots))
        i = fin
    return out, corps, fin_classe


# ------------------------------------------------------------------ écriture
def imports(src):
    return re.findall(r"^import\s+(static\s+)?([\w.]+(?:\.\*)?)\s*;\s*$", src, re.M)


def garder_imports(liste, corps):
    mots = set(re.findall(r"\b\w+\b", masque(corps)))
    out = []
    for stat, imp in liste:
        dernier = imp.rsplit(".", 1)[1]
        if dernier == "*" or dernier in mots:
            out.append(f"import {stat or ''}{imp};")
    return out


def retirer_prive(texte):
    """Retire le premier « private » de l'en-tête d'un membre (après annotations et commentaires)."""
    m = masque(texte)
    p = re.search(r"\bprivate\s+", m)
    return texte if not p else texte[:p.start()] + texte[p.end():]


def main():
    args = sys.argv[1:]
    if not args:
        raise SystemExit(__doc__)
    fichier = Path(args[0]).resolve()

    def opt(nom):
        if nom in args:
            return [x for x in args[args.index(nom) + 1].split(",") if x]
        return []

    src = fichier.read_text(encoding="utf-8")
    nom = fichier.stem
    if "--imports" in args:
        # nettoyage seul (après une retouche à la main) : retire les imports devenus inutiles
        sans = re.sub(r"^import\s+.*?;[ \t]*\r?\n", "", src, flags=re.M)
        garde = garder_imports(imports(src), sans)
        fin_package = sans.index("\n", sans.index("package")) + 1
        neuf = sans[:fin_package] + "\n" + "\n".join(garde) + ("\n\n" if garde else "") + sans[fin_package:].lstrip("\r\n")
        retires = len(imports(src)) - len(garde)
        fichier.write_text(neuf, encoding="utf-8", newline="\n")
        print(f"[scinder] {nom} : {retires} import(s) inutile(s) retiré(s)")
        return
    mbs, corps, fin_classe = membres(src, nom)
    if "--liste" in args:
        for k, mb in enumerate(mbs):
            texte = src[mb.debut:mb.fin]
            l0 = src.count("\n", 0, mb.debut) + 1
            l1 = src.count("\n", 0, mb.fin) + 1
            s = "S" if MOTS_S.search(texte) else " "
            c = "C" if MOTS_C.search(texte) else " "
            print(f"{k:3} {s}{c} {mb.genre:12} {','.join(mb.noms):40} l.{l0}-{l1}{'  private' if mb.prive else ''}{'  static' if mb.statique else ''}")
        return
    serveur, deux = set(opt("--serveur")), set(opt("--deux"))
    inconnus = (serveur | deux) - {n for mb in mbs for n in mb.noms}
    if inconnus:
        raise SystemExit(f"membres inconnus : {sorted(inconnus)}")
    racine = next(p for p in fichier.parents if p.name == "java" and p.parent.name == "commun")
    paquet = re.search(r"^package\s+([\w.]+)\s*;", src, re.M).group(1)
    sortie = racine.parent.parent / "serveur" / "java" / Path(*paquet.split(".")) / f"{nom}Serveur.java"

    a_deplacer = [mb for mb in mbs if any(n in serveur for n in mb.noms)]
    a_doubler = [mb for mb in mbs if mb.genre == "méthode" and any(n in deux for n in mb.noms)]
    for mb in a_deplacer:
        if mb.genre == "champ" and not set(mb.noms) <= serveur:
            raise SystemExit(f"déclaration groupée {mb.noms} : à déplacer en entier ou à couper d'abord")
    # texte serveur
    morceaux = []
    for mb in mbs:
        if mb in a_deplacer:
            morceaux.append(src[mb.debut:mb.fin])
        elif mb in a_doubler:
            t = retirer_prive(src[mb.debut:mb.fin])
            if "@Override" not in t:
                mt = masque(t)
                p = len(mt) - len(mt.lstrip())
                ligne = t.rfind("\n", 0, p) + 1
                t = t[:p] + "@Override\n" + t[ligne:p] + t[p:]
            morceaux.append(t)
    ctors = [mb for mb in mbs if mb.genre == "constructeur"]
    ctor_txt = []
    for mb in ctors:
        entete = mb.entete
        params = entete[entete.index("(") + 1:entete.rindex(")")]
        noms_p, d, cour = [], 0, ""
        for c in params:
            if c == "<":
                d += 1
            elif c == ">":
                d -= 1
            if c == "," and d == 0:
                noms_p.append(cour)
                cour = ""
            else:
                cour += c
        if cour.strip():
            noms_p.append(cour)
        args_s = ", ".join(re.search(r"(\w+)\s*$", p.strip()).group(1) for p in noms_p)
        vis = "public " if "public" in entete.split("(")[0] else "protected " if "protected" in entete else ""
        ctor_txt.append(f"   {vis}{nom}Serveur({params.strip()}) {{\n      super({args_s});\n   }}\n")
    vers = opt("--vers")
    for sub in opt("--type"):
        de, a = sub.split("=")
        morceaux = [re.sub(r"\b" + re.escape(de) + r"\b", a, m) for m in morceaux]
    if vers:
        ctor_txt = []  # la classe cible existe déjà, avec ses constructeurs
    corps_serveur = "\n".join(ctor_txt) + "".join(morceaux)
    # texte commun
    base = src
    enlever = sorted(a_deplacer, key=lambda mb: mb.debut, reverse=True)
    for mb in enlever:
        base = base[:mb.debut] + base[mb.fin:]
    texte_serveur_masque = masque(corps_serveur)
    mots_serveur = set(re.findall(r"\b\w+\b", texte_serveur_masque))
    mbs_base, _, _ = membres(base, nom)
    elargis = []
    for mb in sorted(mbs_base, key=lambda m: m.debut, reverse=True):
        doubler = mb.genre == "méthode" and any(n in deux for n in mb.noms)
        if mb.prive and (doubler or any(n in mots_serveur for n in mb.noms)):
            base = base[:mb.debut] + retirer_prive(base[mb.debut:mb.fin]) + base[mb.fin:]
            elargis.append(",".join(mb.noms))
    final = re.search(r"\bfinal\s+(?=class\s+" + nom + r"\b)", base)
    if final:
        base = base[:final.start()] + base[final.end():]
    # imports
    liste = imports(src)
    sans_imp_base = re.sub(r"^import\s+.*?;[ \t]*\r?\n", "", base, flags=re.M)
    imp_base = garder_imports(liste, sans_imp_base)
    fin_package = sans_imp_base.index("\n", sans_imp_base.index("package")) + 1
    reste_base = sans_imp_base[fin_package:].lstrip("\r\n")
    base = sans_imp_base[:fin_package] + "\n" + "\n".join(imp_base) + ("\n\n" if imp_base else "") + reste_base
    imp_serveur = garder_imports(liste, corps_serveur + f" extends {nom} ")
    if vers:
        # --vers : on ajoute les membres à une sous-classe serveur qui existe déjà (classe mère abstraite, §5.5)
        sortie = Path(vers[0]).resolve()
        cible = sortie.read_text(encoding="utf-8")
        deja = {l.strip() for l in cible.splitlines() if l.startswith("import ")}
        neufs = [i for i in imp_serveur if i not in deja]
        if neufs:
            derniers = list(re.finditer(r"^import .*;$", cible, re.M))
            pos = derniers[-1].end() if derniers else cible.index("\n", cible.index("package")) + 1
            cible = cible[:pos] + "\n" + "\n".join(neufs) + cible[pos:]
        fin = cible.rstrip().rindex("}")
        serveur_txt = (cible[:fin].rstrip() + f"\n\n   // ---- repris de {nom} (logique serveur de la classe mère)\n"
                       + corps_serveur.rstrip() + "\n}\n")
    else:
        serveur_txt = (f"package {paquet};\n\n" + "\n".join(imp_serveur) + ("\n\n" if imp_serveur else "")
                       + f"/**\n * Partie SERVEUR de {{@link {nom}}} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.\n"
                       + f" * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new {nom}(...) » côté serveur.\n */\n"
                       + f"public class {nom}Serveur extends {nom} {{\n" + corps_serveur.rstrip() + "\n}\n")
    # contrôles
    base_masque = masque(base)
    mots_base = set(re.findall(r"\b\w+\b", base_masque))
    restes = sorted({n for mb in a_deplacer for n in mb.noms if n in mots_base})
    statiques = sorted({n for mb in a_deplacer if mb.statique for n in mb.noms})
    appelants = {}
    projet_src = racine.parent.parent
    for f in projet_src.rglob("*.java"):
        if f == fichier:
            continue
        t = f.read_text(encoding="utf-8", errors="replace")
        for s in statiques:
            if re.search(r"\b" + nom + r"\." + s + r"\b", t):
                appelants.setdefault(s, []).append(f.relative_to(projet_src).as_posix())
    print(f"[scinder] {nom} : {len(a_deplacer)} membre(s) déplacé(s), {len(a_doubler)} redéfini(s) côté serveur -> {sortie}")
    if elargis:
        print(f"[scinder] « private » retiré (cités par le serveur) : {', '.join(reversed(elargis))}")
    if final:
        print(f"[scinder] « final » retiré de la classe {nom} (elle a maintenant une sous-classe)")
    if restes:
        print(f"[scinder] ATTENTION : la classe commune cite encore des membres déplacés : {', '.join(restes)}")
        print("           -> les garder communs, ou les classer en --deux avec une version neutre côté commun")
    for s, fs in appelants.items():
        print(f"[scinder] appel statique {nom}.{s} à réécrire en {nom}Serveur.{s} dans : {', '.join(fs)}")
    for mb in a_doubler:
        for n in mb.noms:
            if re.search(r"\bsuper\s*\.\s*" + n + r"\s*\(", masque(src[mb.debut:mb.fin])):
                print(f"[scinder] ATTENTION : la copie serveur de {n}() appelle super.{n}(), qui sera la version COMMUNE réduite "
                      f"(piège §9 n°15) : faire faire la part commune par une méthode à part, redéfinie côté serveur")
    if "--essai" in args:
        print("[scinder] essai : rien n'est écrit")
        return
    if sortie.exists() and not vers:
        raise SystemExit(f"{sortie} existe déjà : rien n'est écrit (ou --vers pour y ajouter)")
    sortie.parent.mkdir(parents=True, exist_ok=True)
    sortie.write_text(serveur_txt, encoding="utf-8", newline="\n")
    fichier.write_text(base, encoding="utf-8", newline="\n")


if __name__ == "__main__":
    main()
