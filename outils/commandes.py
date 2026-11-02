"""Inventaire de toutes les commandes du serveur, lu dans les sources (jamais à la main).

    python outils/commandes.py              écrit docs/COMMANDES_INVENTAIRE.md
    python outils/commandes.py --console    affiche le tableau

Pour chaque commande de premier niveau enregistrée par nos mods, le module, le niveau de permission demandé
(requires(... hasPermission(n))), les sous-commandes repérées et leur niveau. Lecture simple du texte des sources : c'est
un repère pour le tri, pas une preuve (une permission vérifiée dans le code de la commande, une sous-commande construite
dans un autre fichier ou une commande d'un plugin n'apparaissent pas ici).

02/11 (audit des commandes, §11) : l'outil voit aussi les racines enregistrées par une variable
(« racine = Commands.literal("x") ... d.register(racine) ») ou à travers une méthode
(« d.register(Rotation.ajouter(Commands.literal("x"))) ») : avant, il en ratait 6 (heralizloot, rarete, caracrea,
reanimation, unlockbackpack, clashwar...). Les modules jamais livrés avec le serveur normal (banc, capture, events) sont
comptés à part, et les sous-commandes enregistrées seulement avec le banc d'essai (« if (essais()) ») marquées.
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
SORTIE = RACINE / "docs" / "COMMANDES_INVENTAIRE.md"
# jamais livrés avec le serveur normal : banc d'essai, outil de capture, boss mis de côté (outils/construire.py)
PAS_LIVRES = {"banc", "capture", "events"}

RE_REGISTER = re.compile(r"\.register\(")
RE_LIT = re.compile(r'(?:Commands\.)?(?:literal|lit)\("([a-z0-9_]+)"\)')
RE_PERM = re.compile(r"hasPermission\((\d)\)")
RE_IDENT = re.compile(r"\s*([A-Za-z_]\w*)\s*[).]")


def fin_appel(t, debut):
    """Indice de la parenthèse fermante qui correspond à celle ouverte juste avant `debut` (chaînes et caractères sautés)."""
    n, i = 1, debut
    while i < len(t) and n > 0:
        c = t[i]
        if t.startswith("//", i):
            j = t.find("\n", i)
            i = len(t) if j < 0 else j
            continue
        if t.startswith("/*", i):
            j = t.find("*/", i + 2)
            i = len(t) if j < 0 else j + 2
            continue
        if c == "'":
            # caractère : 'x' ou '\x' seulement (une apostrophe d'un texte n'en est pas un)
            m = re.match(r"'(?:\\.|[^'\\])'", t[i:i + 4])
            i += len(m.group(0)) if m else 1
            continue
        if c == '"':
            j = i + 1
            while j < len(t) and t[j] != '"':
                j += 2 if t[j] == "\\" else 1
            i = j + 1
            continue
        if c == "(":
            n += 1
        elif c == ")":
            n -= 1
        i += 1
    return i - 1


def sous_commandes(c, bloc, nom, essai=False):
    for s in RE_LIT.finditer(bloc):
        if s.group(1) == nom:
            continue
        fin = bloc.find("\n", s.end())
        ligne = bloc[s.start(): fin if fin > 0 else len(bloc)]
        ps = RE_PERM.search(ligne)
        niv = int(ps.group(1)) if ps else None
        cle = s.group(1) + (" (banc d'essai)" if essai else "")
        ancien = c["sous"].get(cle, "absent")
        c["sous"][cle] = niv if ancien == "absent" or ancien is None else ancien


def lire():
    cmds = defaultdict(lambda: {"modules": set(), "fichiers": set(), "perm": None, "sous": {}})
    for f in sorted(RACINE.glob("modules/*/src/**/*.java")):
        parts = f.relative_to(RACINE / "modules").parts
        if parts[1] == "build" or parts[0] == "_gabarit":   # fabrication de Gradle, gabarit (jamais un dossier « build » du code)
            continue
        t = f.read_text(encoding="utf-8", errors="ignore")
        if ".register(" not in t or ("literal(" not in t and "lit(" not in t):
            continue
        module = f.relative_to(RACINE / "modules").parts[0]
        cote = f.relative_to(RACINE / "modules" / module / "src").parts[0]
        for m in RE_REGISTER.finditer(t):
            fin = fin_appel(t, m.end())
            arg = t[m.end():fin]
            debut_ligne = t.rfind("\n", 0, m.start()) + 1
            essai = "essais()" in t[debut_ligne:m.start()]
            premier = RE_LIT.search(arg)
            bloc = arg
            if premier is None or premier.start() > 200:
                # « d.register(racine) » : la variable construite plus haut
                v = RE_IDENT.match(arg + ")")
                if not v:
                    continue
                aff = None
                for a in re.finditer(r"\b" + re.escape(v.group(1)) + r"\s*=\s*", t[:m.start()]):
                    aff = a
                if aff is None:
                    continue
                premier = RE_LIT.match(t, aff.end()) or RE_LIT.search(t[aff.end(): aff.end() + 200])
                if premier is None:
                    continue
                bloc = t[aff.end(): m.start()]
                premier = RE_LIT.search(bloc)
            nom = premier.group(1)
            if not re.match(r"[a-z]", nom):
                continue
            c = cmds[nom]
            c["modules"].add(module + (" (joueur)" if cote == "client" else ""))
            c["fichiers"].add(f.relative_to(RACINE).as_posix())
            entete = bloc[premier.end(): premier.end() + 120]
            p = RE_PERM.search(entete.split(".then(")[0])
            if p and (c["perm"] is None or int(p.group(1)) < c["perm"]):
                c["perm"] = int(p.group(1))
            elif not p and not essai and premier.start() < 60:
                c["ouverte"] = True
            sous_commandes(c, bloc, nom, essai)
    for c in cmds.values():
        if c.pop("ouverte", False):
            c["perm"] = None
    return cmds


def qui(perm):
    return {None: "tous", 0: "tous", 1: "op 1", 2: "staff (op 2)", 3: "op 3", 4: "op 4"}.get(perm, str(perm))


def livree(c):
    return any(m.split(" ")[0] not in PAS_LIVRES for m in c["modules"])


def tableau(cmds):
    livrees = {n: c for n, c in cmds.items() if livree(c)}
    a_part = {n: c for n, c in cmds.items() if not livree(c)}
    lignes = ["# Inventaire des commandes (écrit par outils/commandes.py)", "",
              "Lu dans les sources de modules/ (les plugins Bukkit — AuthMe, LiteBans, LuckPerms, WorldEdit, WorldGuard — "
              "et Minecraft lui-même ne sont pas comptés). « tous » = aucune permission demandée à l'entrée de la "
              "commande (une sous-commande ou le code peut en demander une).", "",
              f"**{len(livrees)} commandes de premier niveau sur le serveur, "
              f"{sum(len(c['sous']) for c in livrees.values())} sous-commandes repérées** "
              f"(plus {len(a_part)} dans des modules jamais livrés avec le serveur normal : "
              f"{', '.join('/' + n for n in sorted(a_part)) or 'aucune'}).", "",
              "| Commande | Module | Entrée | Sous-commandes (niveau si différent) |", "|---|---|---|---|"]
    for nom in sorted(cmds):
        c = cmds[nom]
        sous = ", ".join(f"{s}" + (f" ({qui(n)})" if n not in (None, c["perm"]) else "") for s, n in sorted(c["sous"].items()))
        mods = ", ".join(sorted(m + (" (pas livré)" if m.split(" ")[0] in PAS_LIVRES else "") for m in c["modules"]))
        lignes.append(f"| `/{nom}` | {mods} | {qui(c['perm'])} | {sous} |")
    return "\n".join(lignes) + "\n"


def main():
    cmds = lire()
    t = tableau(cmds)
    if "--console" in sys.argv:
        print(t)
        return
    # le fichier du dépôt est en CRLF : on le garde ainsi
    ancien = SORTIE.read_bytes() if SORTIE.exists() else b""
    if b"\r\n" in ancien:
        t = t.replace("\n", "\r\n")
    SORTIE.write_bytes(t.encode("utf-8"))
    n = sum(1 for c in cmds.values() if livree(c))
    print(f"[commandes] {n} commandes sur le serveur ({len(cmds)} avec les modules jamais livrés) -> {SORTIE.relative_to(RACINE)}")


if __name__ == "__main__":
    main()
