"""Inventaire de toutes les commandes du serveur, lu dans les sources (jamais à la main).

    python outils/commandes.py              écrit docs/COMMANDES_INVENTAIRE.md
    python outils/commandes.py --console    affiche le tableau

Pour chaque commande de premier niveau (Commands.literal("x") enregistrée par d'abord d'un dispatcher), le module, le
fichier, le niveau de permission demandé (requires(... hasPermission(n))), les sous-commandes repérées et leur niveau.
Lecture simple du texte des sources : c'est un repère pour le tri, pas une preuve (une permission vérifiée dans le code
de la commande, une commande enregistrée par réflexion ou par un plugin n'apparaissent pas ici).
"""
import re
import sys
from collections import defaultdict
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
SORTIE = RACINE / "docs" / "COMMANDES_INVENTAIRE.md"

RE_RACINE = re.compile(r'register\(\s*(?:[A-Za-z_.]+\.)?(?:literal|lit)\("([a-z0-9_]+)"\)')
RE_LIT = re.compile(r'(?:Commands\.)?(?:literal|lit)\("([a-z0-9_]+)"\)')
RE_PERM = re.compile(r'hasPermission\((\d)\)')


def lire():
    cmds = defaultdict(lambda: {"modules": set(), "fichiers": set(), "perm": None, "sous": {}})
    for f in sorted(RACINE.glob("modules/*/src/**/*.java")):
        if "/build/" in f.as_posix() or "_gabarit" in f.as_posix():
            continue
        t = f.read_text(encoding="utf-8", errors="ignore")
        if "literal(" not in t and "lit(" not in t:
            continue
        module = f.relative_to(RACINE / "modules").parts[0]
        cote = f.relative_to(RACINE / "modules" / module / "src").parts[0]
        for m in RE_RACINE.finditer(t):
            nom = m.group(1)
            c = cmds[nom]
            c["modules"].add(module + (" (joueur)" if cote == "client" else ""))
            c["fichiers"].add(f.relative_to(RACINE).as_posix())
            # bloc de la commande : jusqu'au prochain register( ou la fin du fichier
            debut = m.end()
            suite = t.find("register(", debut)
            bloc = t[debut: suite if suite > 0 else len(t)]
            entete = bloc[:400]
            p = RE_PERM.search(entete)
            if p and (c["perm"] is None or int(p.group(1)) < c["perm"]):
                c["perm"] = int(p.group(1))
            for s in RE_LIT.finditer(bloc):
                fin = bloc.find("\n", s.end())
                ligne = bloc[s.start(): fin if fin > 0 else len(bloc)]
                ps = RE_PERM.search(ligne)
                niv = int(ps.group(1)) if ps else None
                if s.group(1) != nom:
                    ancien = c["sous"].get(s.group(1), "absent")
                    c["sous"][s.group(1)] = niv if ancien == "absent" or ancien is None else ancien
    return cmds


def qui(perm):
    return {None: "tous", 0: "tous", 1: "op 1", 2: "staff (op 2)", 3: "op 3", 4: "op 4"}.get(perm, str(perm))


def tableau(cmds):
    lignes = ["# Inventaire des commandes (écrit par outils/commandes.py)", "",
              "Lu dans les sources de modules/ (les plugins Bukkit — AuthMe, LiteBans, LuckPerms, WorldEdit, WorldGuard, "
              "spark — et Minecraft lui-même ne sont pas comptés). « tous » = aucune permission demandée à l'entrée de la "
              "commande (une sous-commande ou le code peut en demander une).", "",
              f"**{len(cmds)} commandes de premier niveau, "
              f"{sum(len(c['sous']) for c in cmds.values())} sous-commandes repérées.**", "",
              "| Commande | Module | Entrée | Sous-commandes (niveau si différent) |", "|---|---|---|---|"]
    for nom in sorted(cmds):
        c = cmds[nom]
        sous = ", ".join(f"{s}" + (f" ({qui(n)})" if n not in (None, c["perm"]) else "") for s, n in sorted(c["sous"].items()))
        lignes.append(f"| `/{nom}` | {', '.join(sorted(c['modules']))} | {qui(c['perm'])} | {sous} |")
    return "\n".join(lignes) + "\n"


def main():
    cmds = lire()
    t = tableau(cmds)
    if "--console" in sys.argv:
        print(t)
        return
    SORTIE.write_text(t, encoding="utf-8")
    print(f"[commandes] {len(cmds)} commandes -> {SORTIE.relative_to(RACINE)}")


if __name__ == "__main__":
    main()
