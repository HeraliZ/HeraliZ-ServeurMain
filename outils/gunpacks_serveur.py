"""Compare le dossier tacz du Ptero avec serveur/tacz du dépôt (workflow « Gunpacks du serveur »).

Le serveur envoie aux joueurs la liste des armes, accessoires, munitions et tables de TOUS les gunpacks de son dossier
tacz ; les joueurs n'ont que les images et modèles du dépôt (pack du launcher). Un gunpack resté sur le Ptero sans être
dans le dépôt donne donc des objets en carrés roses et noirs dans le menu créatif. La mise en ligne complète le dossier
tacz sans rien y supprimer : ce qui a été retiré du dépôt peut y rester.

Seuls le haut de /tacz et les index des gunpacks (data/<ns>/index/<type>/) sont lus : ce sont eux qui font apparaître
les objets (lister tout le dossier, plus de 6 000 fichiers, prend plus de 13 minutes par SFTP).

    python outils/gunpacks_serveur.py --resume resume.md            lecture seule (accès SFTP : PTERO_SFTP_* )
    python outils/gunpacks_serveur.py --resume resume.md --supprimer   SUPPRIME du Ptero ce qui n'est pas dans le dépôt
                                                                    (gunpacks entiers et index d'objets en trop)
    python outils/gunpacks_serveur.py --resume r.md --listes racine.txt index.txt   essai sans Ptero (une ligne par
                                                                    chemin sous /tacz, dossiers finis par « / »)
Les joueurs n'ont jamais téléchargé ces gunpacks (le pack du launcher est fait avec le dépôt) : les supprimer enlève
seulement les objets en carrés roses, au prochain redémarrage du serveur.
"""
import argparse
import collections
import os
import pathlib
import re
import stat
import sys

RACINE = pathlib.Path(__file__).resolve().parent.parent
DEPOT = RACINE / "serveur" / "tacz"
TYPES = re.compile(r"^[^/]+/data/([^/]+)/index/(guns|attachments|ammo|blocks)/(.+)\.json$")
NOMS = {"guns": "armes", "attachments": "accessoires", "ammo": "munitions", "blocks": "tables de fabrication"}
# Gardés sur le Ptero même s'ils ne sont pas dans le dépôt. Vide : le pack par défaut de TaCZ (tacz_default_gun) n'est
# PAS gardé. Avec DefaultPackDebug = true (serveur/tacz/tacz-pre.toml), TaCZ ne le copie jamais de son jar dans le
# dossier tacz des joueurs : ses 49 objets absents de nos gunpacks étaient des carrés roses chez eux, et ses 131 autres
# fichiers, différents des versions retouchées de arkaniaz_pack_0_converted (mêmes chemins), pouvaient prendre leur
# place sur le serveur selon l'ordre de lecture des dossiers (12/10).
GARDES = set()


def depot():
    racine = {p.name.rstrip("/") for p in DEPOT.iterdir()}
    index = set()
    for p in DEPOT.glob("*/data/*/index/*/**/*"):
        rel = p.relative_to(DEPOT).as_posix()
        if p.is_file() and TYPES.match(rel):
            index.add(rel)
    return racine, index


def connexion():
    import paramiko
    hote = os.environ["PTERO_SFTP_HOST"].strip()
    hote = hote.split("://", 1)[-1].split("/", 1)[0]
    port = os.environ.get("PTERO_SFTP_PORT", "").strip()
    if ":" in hote:
        hote, p = hote.rsplit(":", 1)
        port = port or p
    transport = paramiko.Transport((hote, int(port or 2022)))
    transport.connect(username=os.environ["PTERO_SFTP_USER"], password=os.environ["PTERO_SFTP_PASSWORD"])
    return transport, paramiko.SFTPClient.from_transport(transport)


def dossiers(sftp, chemin):
    try:
        return [(a.filename, stat.S_ISDIR(a.st_mode or 0)) for a in sftp.listdir_attr(chemin)]
    except OSError:
        return []


def lister_ptero(sftp):
    """(noms en haut de /tacz, index des gunpacks) ; chemins relatifs à /tacz."""
    racine, index = set(), set()
    for pack, est_dossier in dossiers(sftp, "/tacz"):
        racine.add(pack)
        if not est_dossier:
            continue
        for ns, d in dossiers(sftp, f"/tacz/{pack}/data"):
            if not d:
                continue
            for typ, d2 in dossiers(sftp, f"/tacz/{pack}/data/{ns}/index"):
                if not d2:
                    continue
                pile = [f"{pack}/data/{ns}/index/{typ}"]
                while pile:
                    rel = pile.pop()
                    for nom, d3 in dossiers(sftp, f"/tacz/{rel}"):
                        chemin = f"{rel}/{nom}"
                        if d3:
                            pile.append(chemin)
                        elif TYPES.match(chemin):
                            index.add(chemin)
    return racine, index


def lire_listes(fichier_racine, fichier_index):
    def lignes(f):
        for ligne in pathlib.Path(f).read_text(encoding="utf-8", errors="replace").splitlines():
            ligne = ligne.strip()
            if ligne.startswith("/tacz/"):
                ligne = ligne[len("/tacz/"):]
            if ligne and not ligne.startswith("/"):
                yield ligne
    racine = {x.rstrip("/") for x in lignes(fichier_racine) if "/" not in x.rstrip("/")}
    return racine, {x for x in lignes(fichier_index) if TYPES.match(x)}


def comparer(racine_ptero, index_ptero):
    racine_depot, index_depot = depot()
    racine_ptero = {x for x in racine_ptero if x not in GARDES}
    index_ptero = {f for f in index_ptero if f.split("/", 1)[0] not in GARDES}
    packs = sorted(x for x in racine_ptero if x not in racine_depot)
    fichiers = sorted(f for f in index_ptero - index_depot if f.split("/", 1)[0] in racine_depot)
    manquants = sorted(index_depot - index_ptero)
    objets = collections.defaultdict(list)
    for f in sorted(index_ptero - index_depot):
        m = TYPES.match(f)
        objets[NOMS[m.group(2)]].append(f"{m.group(1)}:{m.group(3)}  ({f.split('/', 1)[0]})")
    return racine_ptero, index_ptero, packs, fichiers, manquants, objets


def resume(racine_ptero, index_ptero, packs, fichiers, manquants, objets):
    l = [f"- dans /tacz du Ptero : {len(racine_ptero)} dossiers ou fichiers, {len(index_ptero)} objets",
         f"- dossiers en trop sur le Ptero (absents du dépôt, donc absents chez les joueurs) : {len(packs)}",
         f"- objets en trop dans les gunpacks du dépôt : {len(fichiers)} ; objets du dépôt manquants sur le Ptero : "
         f"{len(manquants)}"]
    if objets:
        l.append("- objets en trop envoyés aux joueurs (carrés roses et noirs dans le menu créatif) : "
                 + ", ".join(f"{len(v)} {k}" for k, v in sorted(objets.items())))
    l.append("")
    if packs:
        l.append("**Dossiers entiers absents du dépôt :**")
        for p in packs:
            n = sum(1 for f in index_ptero if f.split("/", 1)[0] == p)
            l.append(f"- `{p}`" + (f" ({n} objets)" if n else ""))
        l.append("")
    if fichiers:
        l.append("**Objets en trop dans les gunpacks du dépôt :**")
        for p, n in sorted(collections.Counter(f.split("/", 1)[0] for f in fichiers).items()):
            l.append(f"- `{p}` : {n} objets")
        l.append("")
    for k, v in sorted(objets.items()):
        l += [f"<details><summary>{len(v)} {k} en trop</summary>", ""] + [f"- `{x}`" for x in v] + ["", "</details>", ""]
    if manquants:
        l += ["<details><summary>Objets du dépôt absents du Ptero</summary>", ""]
        l += [f"- `{x}`" for x in manquants[:200]] + ["", "</details>", ""]
    return l


def effacer(sftp, chemin):
    """Supprime un fichier ou un dossier entier du Ptero ; rend le nombre de fichiers supprimés."""
    try:
        contenu = sftp.listdir_attr(chemin)
    except OSError:
        sftp.remove(chemin)
        return 1
    n = 0
    for a in contenu:
        sous = f"{chemin}/{a.filename}"
        if stat.S_ISDIR(a.st_mode or 0):
            n += effacer(sftp, sous)
        else:
            sftp.remove(sous)
            n += 1
    sftp.rmdir(chemin)
    return n


def api_ptero():
    """Adresse de l'API « client » du serveur et sa clé (secrets PTERO_URL, PTERO_SERVER_ID, PTERO_API_KEY), ou None."""
    url, serveur, cle = (os.environ.get(k, "").strip() for k in ("PTERO_URL", "PTERO_SERVER_ID", "PTERO_API_KEY"))
    if not (url and serveur and cle):
        return None
    return f"{url.rstrip('/')}/api/client/servers/{serveur}", cle


def effacer_api(api, chemins):
    """Fait effacer par le panel (bouton « Supprimer » du gestionnaire de fichiers) : un dossier entier en un appel,
    sur la machine du serveur, au lieu d'un aller-retour SFTP par fichier (des milliers dans un gunpack)."""
    import json
    import urllib.request
    adresse, cle = api
    for i in range(0, len(chemins), 50):
        corps = json.dumps({"root": "/tacz", "files": chemins[i:i + 50]}).encode()
        requete = urllib.request.Request(f"{adresse}/files/delete", data=corps, method="POST", headers={
            "Authorization": f"Bearer {cle}", "Content-Type": "application/json",
            "Accept": "Application/vnd.pterodactyl.v1+json"})
        with urllib.request.urlopen(requete, timeout=600) as reponse:
            reponse.read()
        print(f"effacé par le panel : {', '.join(chemins[i:i + 50])}", flush=True)


def supprimer(sftp, packs, fichiers):
    faits = []
    api = api_ptero()
    if api is not None:
        try:
            effacer_api(api, list(packs) + list(fichiers))
            return [f"/tacz/{x}" for x in list(packs) + list(fichiers)]
        except OSError as e:
            print(f"::warning::panel du Ptero injoignable ({e}) : suppression fichier par fichier (SFTP, plus lent)",
                  flush=True)
    restants = set(sftp.listdir("/tacz"))  # le panel a pu en effacer une partie avant de lâcher
    for p in packs:
        if p in restants:
            faits.append(f"/tacz/{p} ({effacer(sftp, f'/tacz/{p}')} fichiers)")
            print(faits[-1], flush=True)
    for f in fichiers:
        try:
            sftp.remove(f"/tacz/{f}")
        except FileNotFoundError:
            continue
        faits.append(f"/tacz/{f}")
    return faits


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--resume", required=True)
    ap.add_argument("--supprimer", action="store_true")
    ap.add_argument("--listes", nargs=2, metavar=("RACINE", "INDEX"))
    a = ap.parse_args()

    transport = sftp = None
    if a.listes:
        racine_ptero, index_ptero = lire_listes(*a.listes)
    else:
        transport, sftp = connexion()
        racine_ptero, index_ptero = lister_ptero(sftp)
    if not racine_ptero:
        print("::error::dossier tacz du Ptero vide ou illisible : rien n'est comparé")
        return 1
    racine_ptero, index_ptero, packs, fichiers, manquants, objets = comparer(racine_ptero, index_ptero)
    l = ["### Gunpacks du serveur (dossier tacz du Ptero comparé au dépôt)", ""]
    l += resume(racine_ptero, index_ptero, packs, fichiers, manquants, objets)

    if a.supprimer and sftp is not None and (packs or fichiers):
        faits = supprimer(sftp, packs, fichiers)
        apres = comparer(*lister_ptero(sftp))
        l += ["### Supprimé du Ptero", ""] + [f"- `{x}`" for x in faits] + [""]
        l.append(f"Après : {len(apres[2])} dossiers et {len(apres[3])} objets encore en trop. "
                 "Redémarrer le serveur pour que ce soit pris en compte.")
    elif a.supprimer:
        l.append("Rien à supprimer.")
    if transport is not None:
        transport.close()
    texte = "\n".join(l) + "\n"
    pathlib.Path(a.resume).write_text(texte, encoding="utf-8")
    print(texte)
    return 0


if __name__ == "__main__":
    sys.exit(main())
