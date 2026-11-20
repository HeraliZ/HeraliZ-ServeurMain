"""Pseudos des joueurs à masquer dans les journaux des tâches GitHub (dépôt public, 20/11).

Les tâches « Lenteurs du serveur » et « Journaux du serveur » masquaient les pseudos par des motifs (« X joined the
game », « <X> », « name=X »…), mais des lignes d'autres formes les laissaient passer (« [GRADE X: Set own game mode…] »,
« X issued server command » dans les lignes avant un retard, « Rejecting UseItemOnPacket from X », lignes de nos mods ou
de l'anticheat). Ce script fait la liste des pseudos connus du serveur, puis un script sed qui les remplace PARTOUT par
*** (sans distinction de casse, mot entier) :
- caches de noms du serveur rapatriés (usercache.json de Minecraft, usernamecache.json de Forge) ;
- journaux rapatriés (.log, .log.gz, .log.zip) : connexions, déconnexions, commandes.

N'affiche JAMAIS un pseudo : seulement leur nombre. Le script sed reste sur la machine de la tâche (jamais affiché ni
envoyé). Usage : python3 -I outils/pseudos_a_masquer.py <script.sed> <fichiers ou dossiers...>
"""
import gzip
import json
import os
import re
import sys
import zipfile

PSEUDO = re.compile(r"[A-Za-z0-9_]{3,16}")
# lignes de Minecraft, de Forge et de Mohist qui donnent un pseudo à coup sûr : « UUID of player X is », « X joined the
# game », « X issued server command », « X[/adresse] logged in », chat « <X> », retour d'une commande aux opérateurs
# « [GRADE X: …] »
MOTIFS = re.compile(r"UUID of player ([A-Za-z0-9_]{3,16}) is "
                    r"|\]: ([A-Za-z0-9_]{3,16})(?= joined the game| left the game| lost connection| issued server command|\[/)"
                    r"|\]: <([A-Za-z0-9_]{3,16})> "
                    r"|\[(?:[A-Z]+ )?([A-Za-z0-9_]{3,16}): ")
# mots du journal qu'un pseudo identique ne doit jamais faire disparaître partout (le masquage les rendrait illisibles)
GARDES = {"server", "thread", "info", "warn", "error", "debug", "main", "player", "players", "console", "rcon", "null",
          "true", "false", "minecraft", "forge", "mohist", "heraliz", "spartan", "admin", "staff"}


def lire_journal(chemin):
    try:
        if chemin.endswith(".gz"):
            with gzip.open(chemin, "rt", encoding="utf-8", errors="replace") as f:
                return f.read()
        if chemin.endswith(".zip"):
            with zipfile.ZipFile(chemin) as z:
                return "".join(z.read(n).decode("utf-8", "replace") for n in z.namelist())
        with open(chemin, encoding="utf-8", errors="replace") as f:
            return f.read()
    except (OSError, zipfile.BadZipFile, EOFError):
        return ""


def lire_cache(chemin):
    try:
        with open(chemin, encoding="utf-8", errors="replace") as f:
            d = json.load(f)
    except (OSError, ValueError):
        return set()
    if isinstance(d, list):
        return {str(x.get("name", "")) for x in d if isinstance(x, dict)}
    if isinstance(d, dict):
        return {str(v) for v in d.values()}
    return set()


def fichiers(chemins):
    for c in chemins:
        if os.path.isdir(c):
            for racine, _, noms in os.walk(c):
                for n in sorted(noms):
                    yield os.path.join(racine, n)
        elif os.path.isfile(c):
            yield c


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 2
    sortie, entrees = sys.argv[1], sys.argv[2:]
    vus = set()
    for f in fichiers(entrees):
        if f.endswith(".json"):
            vus |= lire_cache(f)
        elif re.search(r"\.log(\.gz|\.zip)?$", f):
            vus |= {g for m in MOTIFS.finditer(lire_journal(f)) for g in m.groups() if g}
    noms = sorted({n for n in vus if PSEUDO.fullmatch(n) and n.lower() not in GARDES}, key=lambda n: (-len(n), n.lower()))
    with open(sortie, "w", encoding="utf-8") as s:
        for n in noms:
            s.write("s/\\b%s\\b/***/gI\n" % n)
    print("pseudos à masquer : %d (liste jamais affichée)" % len(noms))
    return 0


if __name__ == "__main__":
    sys.exit(main())
