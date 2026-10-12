"""Compare le dossier tacz du Ptero (liste de ses fichiers) avec serveur/tacz du dépôt.

Le serveur envoie aux joueurs la liste des armes, accessoires et munitions de TOUS les gunpacks de son dossier tacz ;
les joueurs n'ont que les images et modèles du dépôt (pack du launcher). Un gunpack resté sur le Ptero sans être dans
le dépôt donne donc des objets en carrés roses et noirs dans le menu créatif. La mise en ligne complète le dossier
tacz sans rien y supprimer : ce qui a été retiré du dépôt peut y rester.

Usage (workflow « Gunpacks du serveur ») :
    python outils/gunpacks_serveur.py racine.txt index.txt --resume resume.md [--ranger ranger.lftp --date AAAA-MM-JJ]
racine.txt : ce qu'il y a directement dans /tacz (« cls -1 -F /tacz/ » de lftp, les dossiers finissent par « / ») ;
index.txt : les fichiers d'index des gunpacks (« cls -1 /tacz/*/data/*/index/*/* »), seuls lus par le serveur pour
la liste des objets (lister tout le dossier, plus de 6 000 fichiers, prend plus de 13 minutes par SFTP).
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
TYPES = re.compile(r"^[^/]+/data/([^/]+)/index/(guns|attachments|ammo|blocks)/(.+)\.json$")
NOMS = {"guns": "armes", "attachments": "accessoires", "ammo": "munitions", "blocks": "tables de fabrication"}


def depot():
    racine = {p.name + ("/" if p.is_dir() else "") for p in DEPOT.iterdir()}
    index = {p.relative_to(DEPOT).as_posix() for p in DEPOT.glob("*/data/*/index/*/*") if p.is_file() and TYPES.match(p.relative_to(DEPOT).as_posix())}
    return racine, index


def lignes(fichier):
    for ligne in pathlib.Path(fichier).read_text(encoding="utf-8", errors="replace").splitlines():
        ligne = ligne.strip()
        if ligne.startswith("/tacz/"):
            ligne = ligne[len("/tacz/"):]
        if ligne and not ligne.startswith("/"):
            yield ligne


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("racine")
    ap.add_argument("index")
    ap.add_argument("--resume", required=True)
    ap.add_argument("--ranger")
    ap.add_argument("--date", default="")
    a = ap.parse_args()

    racine_depot, index_depot = depot()
    racine_ptero = {x for x in lignes(a.racine) if "/" not in x.rstrip("/")}
    index_ptero = {x for x in lignes(a.index) if TYPES.match(x)}
    if not racine_ptero:
        print("::error::liste du dossier tacz du Ptero vide ou illisible : rien n'est comparé")
        return 1
    noms_depot = {x.rstrip("/") for x in racine_depot}
    # en trop : dossiers (ou fichiers) entiers absents du dépôt, et index en trop dans les gunpacks du dépôt
    packs_en_trop = sorted(x.rstrip("/") for x in racine_ptero if x.rstrip("/") not in noms_depot)
    fichiers_en_trop = sorted(f for f in index_ptero - index_depot if f.split("/", 1)[0] in noms_depot)
    manquants = sorted(index_depot - index_ptero)
    objets = collections.defaultdict(list)
    for f in sorted(index_ptero - index_depot):
        m = TYPES.match(f)
        objets[NOMS[m.group(2)]].append(f"{m.group(1)}:{m.group(3)}  ({f.split('/', 1)[0]})")

    l = ["### Gunpacks du serveur (dossier tacz du Ptero comparé au dépôt)", ""]
    l.append(f"- dans /tacz du Ptero : {len(racine_ptero)} dossiers ou fichiers ; dépôt : {len(racine_depot)}")
    l.append(f"- dossiers en trop sur le Ptero (absents du dépôt, donc absents chez les joueurs) : {len(packs_en_trop)}")
    l.append(f"- index d'objets en trop dans les gunpacks du dépôt : {len(fichiers_en_trop)} ; manquants sur le Ptero : {len(manquants)}")
    if objets:
        l.append("- objets en trop envoyés aux joueurs (carrés roses et noirs dans le menu créatif) : "
                 + ", ".join(f"{len(v)} {k}" for k, v in sorted(objets.items())))
    l.append("")
    if packs_en_trop:
        l.append("**Dossiers entiers absents du dépôt :**")
        for p in packs_en_trop:
            n = sum(1 for f in index_ptero if f.split("/", 1)[0] == p)
            l.append(f"- `{p}`" + (f" ({n} objets)" if n else ""))
        l.append("")
    if fichiers_en_trop:
        l.append("**Index d'objets en trop dans les gunpacks du dépôt :**")
        par_pack = collections.Counter(f.split("/", 1)[0] for f in fichiers_en_trop)
        for p, n in sorted(par_pack.items()):
            l.append(f"- `{p}` : {n} objets")
        l.append("")
    for k, v in sorted(objets.items()):
        l.append(f"<details><summary>{len(v)} {k} en trop</summary>")
        l.append("")
        l.extend(f"- `{x}`" for x in sorted(v))
        l.append("")
        l.append("</details>")
        l.append("")
    if manquants:
        l.append("<details><summary>Index du dépôt absents du Ptero</summary>")
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
