"""Compare le dossier tacz du Ptero (liste de ses fichiers) avec serveur/tacz du dépôt.

Le serveur envoie aux joueurs la liste des armes, accessoires et munitions de TOUS les gunpacks de son dossier tacz ;
les joueurs n'ont que les images et modèles du dépôt (pack du launcher). Un gunpack resté sur le Ptero sans être dans
le dépôt donne donc des objets en carrés roses et noirs dans le menu créatif. La mise en ligne complète le dossier
tacz sans rien y supprimer : ce qui a été retiré du dépôt peut y rester.

Usage (workflow « Gunpacks du serveur ») :
    python outils/gunpacks_serveur.py liste_ptero.txt --resume resume.md [--ranger ranger.lftp --date AAAA-MM-JJ]
liste_ptero.txt : sortie de « find /tacz » de lftp (un chemin par ligne, les dossiers finissent par « / »).
--ranger écrit les commandes lftp qui DÉPLACENT (jamais n'effacent) ce qui n'est pas dans le dépôt vers
/tacz_ranges/<date>/ : rien n'est perdu, il suffit de le remettre dans /tacz pour revenir en arrière.
"""
import argparse
import collections
import pathlib
import re
import sys

RACINE = pathlib.Path(__file__).resolve().parent.parent
DEPOT = RACINE / "serveur" / "tacz"
TYPES = re.compile(r"^[^/]+/data/([^/]+)/index/(guns|attachments|ammo)/(.+)\.json$")
NOMS = {"guns": "armes", "attachments": "accessoires", "ammo": "munitions"}


def fichiers_depot():
    return {p.relative_to(DEPOT).as_posix() for p in DEPOT.rglob("*") if p.is_file()}


def fichiers_ptero(liste):
    out = set()
    for ligne in pathlib.Path(liste).read_text(encoding="utf-8", errors="replace").splitlines():
        ligne = ligne.strip()
        if not ligne.startswith("/tacz/") or ligne.endswith("/"):
            continue
        out.add(ligne[len("/tacz/"):])
    return out


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("liste")
    ap.add_argument("--resume", required=True)
    ap.add_argument("--ranger")
    ap.add_argument("--date", default="")
    a = ap.parse_args()

    depot, ptero = fichiers_depot(), fichiers_ptero(a.liste)
    if not ptero:
        print("::error::liste du dossier tacz du Ptero vide ou illisible : rien n'est comparé")
        return 1
    packs_depot = {f.split("/", 1)[0] for f in depot}
    en_trop = sorted(ptero - depot)
    manquants = sorted(depot - ptero)
    # ce qui est en trop : dossiers entiers absents du dépôt, et fichiers en trop dans les gunpacks du dépôt
    packs_en_trop = sorted({f.split("/", 1)[0] for f in en_trop if f.split("/", 1)[0] not in packs_depot})
    fichiers_en_trop = [f for f in en_trop if f.split("/", 1)[0] in packs_depot]
    objets = collections.defaultdict(list)
    for f in en_trop:
        m = TYPES.match(f)
        if m:
            objets[NOMS[m.group(2)]].append(f"{m.group(1)}:{m.group(3)}")

    l = ["### Gunpacks du serveur (dossier tacz du Ptero comparé au dépôt)", ""]
    l.append(f"- Ptero : {len(ptero)} fichiers ; dépôt : {len(depot)} fichiers")
    l.append(f"- en trop sur le Ptero (absents du dépôt, donc absents chez les joueurs) : {len(en_trop)} fichiers")
    l.append(f"- manquants sur le Ptero : {len(manquants)} fichiers")
    if objets:
        l.append("- objets en trop envoyés aux joueurs (carrés roses et noirs dans le menu créatif) : "
                 + ", ".join(f"{len(v)} {k}" for k, v in sorted(objets.items())))
    l.append("")
    if packs_en_trop:
        l.append("**Dossiers entiers absents du dépôt :**")
        for p in packs_en_trop:
            n = sum(1 for f in en_trop if f.split("/", 1)[0] == p)
            l.append(f"- `{p}` ({n} fichiers)")
        l.append("")
    if fichiers_en_trop:
        l.append("**Fichiers en trop dans les gunpacks du dépôt :**")
        par_pack = collections.Counter(f.split("/", 1)[0] for f in fichiers_en_trop)
        for p, n in sorted(par_pack.items()):
            l.append(f"- `{p}` : {n} fichiers")
        l.append("")
    for k, v in sorted(objets.items()):
        l.append(f"<details><summary>{len(v)} {k} en trop</summary>")
        l.append("")
        l.extend(f"- `{x}`" for x in sorted(v))
        l.append("")
        l.append("</details>")
        l.append("")
    if manquants:
        l.append("<details><summary>Fichiers du dépôt absents du Ptero</summary>")
        l.append("")
        l.extend(f"- `{x}`" for x in manquants[:200])
        l.append("")
        l.append("</details>")
    texte = "\n".join(l) + "\n"
    pathlib.Path(a.resume).write_text(texte, encoding="utf-8")
    print(texte)

    if a.ranger:
        if not re.fullmatch(r"\d{4}-\d{2}-\d{2}", a.date):
            print("::error::--ranger demande --date AAAA-MM-JJ")
            return 1
        dest = f"/tacz_ranges/{a.date}"
        cmds = [f'mkdir -p -f "{dest}"']
        for p in packs_en_trop:
            cmds.append(f'mv "/tacz/{p}" "{dest}/{p}"')
        dossiers = sorted({str(pathlib.PurePosixPath(f).parent) for f in fichiers_en_trop})
        for d in dossiers:
            cmds.append(f'mkdir -p -f "{dest}/{d}"')
        for f in fichiers_en_trop:
            cmds.append(f'mv "/tacz/{f}" "{dest}/{f}"')
        for c in cmds:
            if any(ch in c for ch in "`$\n;"):
                print(f"::error::nom de fichier inattendu, rien n'est déplacé : {c}")
                return 1
        pathlib.Path(a.ranger).write_text("\n".join(cmds) + "\n", encoding="utf-8")
        print(f"{len(packs_en_trop)} dossiers et {len(fichiers_en_trop)} fichiers à ranger dans {dest}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
