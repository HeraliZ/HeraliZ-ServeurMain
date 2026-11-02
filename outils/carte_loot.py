"""Remplacer la carte de loot du serveur (Ptero) en GARDANT le spawn : lancé par le workflow « Remplacer la carte de loot »
(.github/workflows/carte_loot.yml), jamais tout seul. Méthode et listes : docs/NOUVELLE_CARTE.md.

Par défaut, ESSAI À BLANC : il lit le Ptero (SFTP, API), vérifie que la nouvelle carte est là où on l'attend et écrit dans
le résumé du workflow exactement ce qui serait remplacé, mis de côté et gardé. Rien n'est changé.

Vraie exécution seulement si la case « executer » est cochée ET que la phrase PHRASE est tapée exactement, depuis main :
 1. (source « release ») la nouvelle carte est envoyée À CÔTÉ du monde pendant que le serveur tourne, puis recomptée ;
 2. joueurs prévenus, serveur arrêté proprement (API du Ptero) ; sauvegarde du Ptero si demandée ;
 3. bascule par renommages SFTP seulement (rien n'est supprimé) : region/, entities/, poi/ de l'ancienne carte, les packs
    de données remplacés et les fichiers de data/ liés à l'ancienne carte vont dans <monde>/ancienne_carte_<date>/ ; ceux
    de la nouvelle carte prennent leur place. Un échec remet tout comme avant ;
 4. redémarrage dans tous les cas, résumé (ce qui a été fait, ce qu'il reste à refaire en jeu).
Jamais touchés : level.dat, uid.dat, dimensions/ (spawn, tutoriel, arènes...), playerdata/, serverconfig/ et les autres
fichiers de data/ (point de réveil du spawn, planques, casino, Purge...).

Où mettre la nouvelle carte (au choix, case « source ») :
 - « dossier_ptero » (conseillé : la carte se construit sur le serveur) : le dossier SOURCE_PTERO à la racine du Ptero,
   avec dedans region/ (obligatoire), et si la carte en a : entities/, poi/, datapacks/<pack>/, data/heraliz_zones.json ;
 - « release » : une Release GitHub du dépôt (tag au choix, « carte-loot » par défaut) avec UNE archive .zip qui contient
   la même chose (à la racine de l'archive ou dans un seul dossier).
Tout le reste de la nouvelle carte (son level.dat, son playerdata/, ses dimensions/, ses autres fichiers data/) est ignoré.
"""
import datetime
import json
import os
import re
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import zipfile
from pathlib import Path

PHRASE = "REMPLACER LA CARTE DE LOOT"
SOURCE_PTERO = "/carte_nouvelle"
# dossiers de la carte de loot (monde principal) remplacés
REMPLACES = ["region", "entities", "poi"]
# fichiers de data/ liés aux lieux de l'ancienne carte : mis de côté (docs/NOUVELLE_CARTE.md §4, « repartir de zéro »)
DATA_ANCIENNE_CARTE = [
    "heraliz_extraction_zones.dat", "heraliz_carte_lieux.dat", "heraliz_depart.dat", "heralisz_bunker_salles.dat",
    "heraliz_loot_rotation.dat", "heraliz_loot_points_t7.dat", "heraliz_vitres.dat", "heraliz_corps.dat",
    "heralisz_train.dat", "heralisz_clans_territoires.dat", "heraliz_zones.json", "chunks.dat",
    "crac_claims.dat", "crac_buildings.dat", "crac_placable_positions.dat", "crac_placable_hp.dat",
    "crac_raidblocks.dat", "crac_placed_terrain.dat", "crac_carrieres.dat",
    "crac_bases_clans.dat", "crac_coeurs_clans.dat", "crac_coeurs_deplaces.dat", "crac_acces_bases.dat",
]
# fichier de data/ que la nouvelle carte peut apporter (zones à effets dessinées pour elle)
DATA_DE_LA_CARTE = ["heraliz_zones.json"]
# fichiers de data/ indispensables : leur absence est signalée (jamais touchés)
DATA_INDISPENSABLES = ["heraliz_cinematique.dat"]
SPAWN = "dimensions/heralisz/spawn"
NOMS_INTERDITS_MONDE = {"mods", "plugins", "config", "defaultconfigs", "libraries", "logs", "tacz", "mohist-config"}
MCA = re.compile(r"^r\.-?\d+\.-?\d+\.mca$")

ENV = os.environ
RESUME = []


def dire(ligne=""):
    print(ligne, flush=True)
    RESUME.append(ligne)


def ecrire_resume():
    f = ENV.get("GITHUB_STEP_SUMMARY")
    if f:
        with open(f, "a", encoding="utf-8") as s:
            s.write("\n".join(RESUME) + "\n")
    RESUME.clear()


def erreur(msg):
    print("::error::" + msg, flush=True)
    dire("**ERREUR : " + msg + "**")


def arreter(msg, rien=True):
    """Erreur avant tout changement du monde : rien n'a été touché (rien=False : seulement le monde)."""
    erreur(msg)
    dire("Rien n'a été changé sur le Ptero." if rien else "Le monde n'a pas été touché.")
    ecrire_resume()
    sys.exit(1)


# ---------------------------------------------------------------- accès SFTP (lftp) et API du Ptero

def hote_port():
    h = ENV.get("PTERO_SFTP_HOST", "").strip()
    h = h[len("sftp://"):] if h.startswith("sftp://") else h
    h = h.split("/", 1)[0]
    p = ENV.get("PTERO_SFTP_PORT", "").strip()
    if ":" in h:
        h, p2 = h.rsplit(":", 1)
        p = p or p2
    return h, (p or "2022")


HOTE, PORT = hote_port()
# machine du Ptero vérifiée par son empreinte quand le workflow la donne (LFTP_SSH, outils/hote_connu.sh)
REGLAGES = (os.environ.get("LFTP_SSH") or "set sftp:auto-confirm yes;") + " set net:max-retries 3; set net:timeout 30; set net:reconnect-interval-base 5; " \
           "set cmd:fail-exit yes; set xfer:clobber on;"


def lftp(cmds, delai=1800):
    """(réussi, sortie) d'une suite de commandes lftp."""
    try:
        r = subprocess.run(["lftp", "-u", ENV["PTERO_SFTP_USER"] + "," + ENV["PTERO_SFTP_PASSWORD"], "-p", PORT,
                            "sftp://" + HOTE, "-e", REGLAGES + " " + cmds + " bye"],
                           stdin=subprocess.DEVNULL, capture_output=True, text=True, encoding="utf-8", errors="replace",
                           timeout=delai)
    except subprocess.TimeoutExpired:
        return False, ""
    return r.returncode == 0, r.stdout


def q(chemin):
    if '"' in chemin or "\n" in chemin:
        raise ValueError("chemin refusé : " + chemin)
    return '"' + chemin + '"'


def existe(chemin):
    return lftp("cls -d " + q(chemin) + ";", 120)[0]


def lister(dossier):
    """{nom: est_un_dossier} du dossier (vide s'il n'existe pas). Seule la présence du nom compte pour décider ; « est un
    dossier » (lu avec -F) ne sert qu'à l'affichage."""
    ok, out = lftp("cls -1aF " + q(dossier.rstrip("/") + "/") + ";", 300)
    noms = {}
    if not ok:
        return noms
    for l in out.splitlines():
        l = l.strip()
        if not l:
            continue
        dossier_ = l.endswith("/")
        n = l.rstrip("/").rstrip("@*=|").split("/")[-1]
        if n not in (".", "..", ""):
            noms[n] = dossier_
    return noms


def taille_mo(chemin):
    ok, out = lftp("du -sm " + q(chemin) + ";", 900)
    try:
        return int(out.split()[0]) if ok else None
    except (IndexError, ValueError):
        return None


def regions(dossier):
    """(nombre de fichiers r.X.Z.mca, taille en Mo) d'un dossier region/ (ou entities/, poi/)."""
    n = sum(1 for x in lister(dossier) if MCA.match(x))
    return n, taille_mo(dossier)


def api(methode, chemin, corps=None, delai=60):
    url = ENV["PTERO_URL"].strip().rstrip("/") + "/api/client/servers/" + ENV["PTERO_SERVER_ID"].strip() + chemin
    data = json.dumps(corps).encode("utf-8") if corps is not None else None
    req = urllib.request.Request(url, data=data, method=methode, headers={
        "Authorization": "Bearer " + ENV["PTERO_API_KEY"], "Content-Type": "application/json",
        "Accept": "Application/vnd.pterodactyl.v1+json"})
    for essai in range(3):
        try:
            with urllib.request.urlopen(req, timeout=delai) as r:
                t = r.read()
                return json.loads(t) if t else {}
        except urllib.error.HTTPError:
            raise
        except (urllib.error.URLError, socket.timeout, ConnectionError):
            if essai == 2:
                raise
            time.sleep(5)


def etat():
    try:
        return api("GET", "/resources")["attributes"]["current_state"]
    except Exception:
        return "inconnu"


def console(cmd):
    try:
        api("POST", "/command", {"command": cmd})
    except Exception:
        pass


# ---------------------------------------------------------------- la nouvelle carte

def carte_release(dossier_local):
    """Télécharge et ouvre l'archive .zip de la Release ; renvoie le dossier qui contient region/ (ou None)."""
    tag = ENV.get("RELEASE_TAG", "").strip() or "carte-loot"
    if not re.fullmatch(r"[A-Za-z0-9._-]+", tag):
        arreter("tag de Release refusé : « %s »" % tag)
    depot = ENV.get("GITHUB_REPOSITORY", "")
    entetes = {"Authorization": "Bearer " + ENV.get("GH_TOKEN", ""), "Accept": "application/vnd.github+json",
               "User-Agent": "HeraliZ"}
    try:
        with urllib.request.urlopen(urllib.request.Request(
                "https://api.github.com/repos/%s/releases/tags/%s" % (depot, urllib.parse.quote(tag)), headers=entetes),
                timeout=60) as r:
            rel = json.loads(r.read())
    except urllib.error.HTTPError as e:
        arreter("Release « %s » introuvable dans le dépôt (code %s) : la créer et y joindre l'archive .zip de la carte."
                % (tag, e.code))
    zips = [a for a in rel.get("assets", []) if a["name"].lower().endswith(".zip")]
    if len(zips) != 1:
        arreter("la Release « %s » doit contenir UNE archive .zip (trouvées : %d)." % (tag, len(zips)))
    a = zips[0]
    dire("- Release « %s » : archive `%s` (%.0f Mo, envoyée le %s)" % (tag, a["name"], a["size"] / 1e6,
                                                                       a.get("updated_at", "?")[:10]))
    archive = Path(dossier_local).with_suffix(".zip")
    # l'API renvoie vers le stockage de GitHub : on suit ce renvoi SANS le jeton (refusé là-bas, et inutile)
    class SansSuite(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None
    req = urllib.request.Request(a["url"], headers=dict(entetes, Accept="application/octet-stream"))
    try:
        r = urllib.request.build_opener(SansSuite).open(req, timeout=600)
    except urllib.error.HTTPError as e:
        if e.code not in (301, 302, 303, 307, 308) or not e.headers.get("Location"):
            arreter("archive de la Release illisible (code %s)." % e.code)
        r = urllib.request.urlopen(urllib.request.Request(e.headers["Location"], headers={"User-Agent": "HeraliZ"}),
                                   timeout=600)
    with r, open(archive, "wb") as f:
        shutil.copyfileobj(r, f, 1 << 20)
    with zipfile.ZipFile(archive) as z:
        z.extractall(dossier_local)   # ZipFile retire « .. » et les chemins absolus
    archive.unlink()
    racine = Path(dossier_local)
    for cand in [racine] + sorted(p for p in racine.iterdir() if p.is_dir()):
        if (cand / "region").is_dir():
            return cand
    return None


def decrire_source_locale(racine):
    """Même description que decrire_source, pour une carte sur le disque du workflow."""
    d = {"region": None, "entities": None, "poi": None, "datapacks": [], "data": [], "ignores": []}
    for n in REMPLACES:
        p = racine / n
        if p.is_dir():
            fichiers = [x for x in p.iterdir() if MCA.match(x.name)]
            d[n] = (len(fichiers), round(sum(x.stat().st_size for x in fichiers) / 1e6))
    if (racine / "datapacks").is_dir():
        d["datapacks"] = sorted(p.name for p in (racine / "datapacks").iterdir())
    d["data"] = [n for n in DATA_DE_LA_CARTE if (racine / "data" / n).is_file()]
    d["ignores"] = sorted(p.name + ("/" if p.is_dir() else "") for p in racine.iterdir()
                          if p.name not in REMPLACES + ["datapacks", "data"]) \
        + (["data/ (sauf %s)" % ", ".join(DATA_DE_LA_CARTE)] if (racine / "data").is_dir() else [])
    return d


def decrire_source(src):
    d = {"region": None, "entities": None, "poi": None, "datapacks": [], "data": [], "ignores": []}
    contenu = lister(src)
    for n in REMPLACES:
        if n in contenu:
            d[n] = regions(src + "/" + n)
    if "datapacks" in contenu:
        d["datapacks"] = sorted(lister(src + "/datapacks"))
    if "data" in contenu:
        donnees = lister(src + "/data")
        d["data"] = [n for n in DATA_DE_LA_CARTE if n in donnees and not donnees[n]]
    d["ignores"] = sorted(n + ("/" if est else "") for n, est in contenu.items()
                          if n not in REMPLACES + ["datapacks", "data"]) \
        + (["data/ (sauf %s)" % ", ".join(DATA_DE_LA_CARTE)] if "data" in contenu else [])
    return d


def fmt(r):
    if not r:
        return "absent"
    n, mo = r
    return "%d fichiers de régions, %s Mo" % (n, "?" if mo is None else mo)


# ---------------------------------------------------------------- programme

def main():
    executer = ENV.get("EXECUTER", "false").lower() == "true"
    phrase = ENV.get("CONFIRMATION", "").strip()
    source = ENV.get("SOURCE", "dossier_ptero")
    date = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%d_%Hh%M")

    dire("## Remplacer la carte de loot : " + ("**VRAIE EXÉCUTION**" if executer else "essai à blanc (rien ne change)"))
    if executer and phrase != PHRASE:
        arreter("case « executer » cochée mais phrase de confirmation incorrecte (attendue : « %s »)." % PHRASE)
    if executer and ENV.get("GITHUB_REF") != "refs/heads/main":
        arreter("la vraie exécution ne part que de la branche main (ici : %s)." % ENV.get("GITHUB_REF"))
    if not all(ENV.get(k) for k in ("PTERO_SFTP_HOST", "PTERO_SFTP_USER", "PTERO_SFTP_PASSWORD", "PTERO_URL",
                                    "PTERO_SERVER_ID", "PTERO_API_KEY")):
        arreter("il faut les secrets du SFTP et de l'API du Ptero (PTERO_*).")
    print("::add-mask::" + HOTE, flush=True)

    # --- le monde en service
    ok, _ = lftp("get /server.properties -o server_ptero_carte.properties;", 120)
    if not ok:
        arreter("server.properties du Ptero illisible (SFTP injoignable ?).")
    monde = ""
    for l in open("server_ptero_carte.properties", encoding="utf-8", errors="replace"):
        if l.startswith("level-name="):
            monde = l.split("=", 1)[1].strip()
            break
    if (not re.fullmatch(r"[A-Za-z0-9_.-]+", monde) or monde in (".", "..") or monde in NOMS_INTERDITS_MONDE
            or monde.startswith(("heralisz_", "heraliz_"))):
        arreter("nom du monde illisible ou inattendu dans le server.properties du Ptero : « %s »." % monde)
    W = "/" + monde
    racine_monde = lister(W)
    if "level.dat" not in racine_monde:
        arreter("%s/level.dat introuvable : ce n'est pas le monde en service." % W)
    if not existe(W + "/" + SPAWN + "/region"):
        arreter("le monde du spawn n'est pas là où on l'attend (%s/%s/region absent) : on ne touche à rien."
                % (W, SPAWN))
    garde = W + "/ancienne_carte_" + date
    if existe(garde):
        arreter(garde + " existe déjà : relancer dans une minute.")
    dire("- monde en service : `%s` ; spawn trouvé : `%s/%s`" % (W, W, SPAWN))
    dire("- serveur : %s" % etat())

    # --- la nouvelle carte
    dire("### Nouvelle carte")
    local = None
    if source == "release":
        Path("carte_release").mkdir(exist_ok=True)
        local = carte_release("carte_release/contenu")
        if local is None:
            arreter("l'archive ne contient pas de dossier region/ (à la racine ou dans un seul dossier).")
        nouvelle = decrire_source_locale(local)
        src = "/carte_nouvelle_release_" + date
        dire("- envoyée au Ptero (vraie exécution seulement) dans `%s`, à côté du monde, serveur en marche" % src)
    elif source == "dossier_ptero":
        src = SOURCE_PTERO
        if not existe(src):
            arreter("dossier `%s` introuvable sur le Ptero : y mettre la nouvelle carte (region/, et si elle en a "
                    "entities/, poi/, datapacks/, data/heraliz_zones.json)." % src)
        nouvelle = decrire_source(src)
        dire("- dossier `%s` du Ptero" % src)
    else:
        arreter("source inconnue : " + source)
    if not nouvelle["region"] or nouvelle["region"][0] == 0:
        arreter("la nouvelle carte n'a pas de fichiers de régions (region/r.X.Z.mca).")
    for n in REMPLACES:
        dire("- `%s/` : %s" % (n, fmt(nouvelle[n])))
    dire("- packs de données : " + (", ".join("`%s`" % p for p in nouvelle["datapacks"]) or "aucun"))
    dire("- fichiers de data/ repris : " + (", ".join("`%s`" % p for p in nouvelle["data"]) or "aucun"))
    if nouvelle["ignores"]:
        dire("- ignoré (jamais repris) : " + ", ".join("`%s`" % p for p in nouvelle["ignores"]))

    # --- le plan
    ancienne = {n: (regions(W + "/" + n) if n in racine_monde else None) for n in REMPLACES}
    if ancienne["region"] and nouvelle["region"] and ancienne["region"] == nouvelle["region"]:
        print("::warning::la nouvelle carte a exactement le même nombre et la même taille de régions que l'actuelle",
              flush=True)
        dire("- **attention** : même nombre et même taille de régions que la carte actuelle (est-ce bien la nouvelle ?)")
    data_monde = lister(W + "/data")
    packs_monde = lister(W + "/datapacks") if "datapacks" in racine_monde else {}
    deplacements = []          # (de, vers) : l'ancienne carte part dans « garde »
    for n in REMPLACES:
        if n in racine_monde:
            deplacements.append((W + "/" + n, garde + "/" + n))
    for p in nouvelle["datapacks"]:
        if p in packs_monde:
            deplacements.append((W + "/datapacks/" + p, garde + "/datapacks/" + p))
    a_cote = [n for n in DATA_ANCIENNE_CARTE if n in data_monde and not data_monde[n]]
    for n in a_cote:
        deplacements.append((W + "/data/" + n, garde + "/data/" + n))
    arrivees = []              # (de, vers) : la nouvelle carte prend la place
    for n in REMPLACES:
        if nouvelle[n]:
            arrivees.append((src + "/" + n, W + "/" + n))
    for p in nouvelle["datapacks"]:
        arrivees.append((src + "/datapacks/" + p, W + "/datapacks/" + p))
    for n in nouvelle["data"]:
        arrivees.append((src + "/data/" + n, W + "/data/" + n))

    serait = "est" if executer else "serait"
    dire("### Ce qui %s mis de côté (renommé dans `%s/`, jamais supprimé)" % (serait, garde))
    for n in REMPLACES:
        dire("- `%s/` de l'ancienne carte : %s" % (n, fmt(ancienne[n])))
    for p in nouvelle["datapacks"]:
        if p in packs_monde:
            dire("- pack de données `datapacks/%s` (remplacé par celui de la nouvelle carte)" % p)
    dire("- data/ : " + (", ".join("`%s`" % n for n in a_cote) or "aucun fichier lié à l'ancienne carte"))
    dire("### Ce qui %s mis en place" % serait)
    for de, vers in arrivees:
        dire("- `%s` → `%s`" % (de, vers))
    dire("### Ce qui est gardé tel quel")
    garder_racine = sorted(n + ("/" if est else "") for n, est in racine_monde.items() if n not in REMPLACES)
    dire("- racine du monde : " + ", ".join("`%s`" % n for n in garder_racine))
    dims = lister(W + "/dimensions")
    mondes = []
    for ns in sorted(dims):
        mondes += ["%s:%s" % (ns, m) for m in sorted(lister(W + "/dimensions/" + ns))]
    dire("- mondes à part (dimensions/) : " + ", ".join("`%s`" % m for m in mondes))
    gardes_data = sorted(n for n in data_monde if n not in a_cote and n not in nouvelle["data"])
    dire("- data/ : " + ", ".join("`%s`" % n for n in gardes_data))
    for n in DATA_INDISPENSABLES:
        if n not in data_monde:
            print("::warning::data/%s absent du monde (point de réveil du spawn) : à vérifier" % n, flush=True)
            dire("- **attention** : `data/%s` absent (point de réveil du spawn) : à vérifier avant" % n)
    if packs_monde:
        dire("- packs de données gardés : " + (", ".join("`%s`" % p for p in sorted(packs_monde)
                                                          if p not in nouvelle["datapacks"]) or "aucun"))
    if not nouvelle["datapacks"] and packs_monde:
        dire("- **la nouvelle carte n'apporte pas de pack de données** : les zones de loot de l'ancienne carte "
             "(datapacks/) restent en place, à refaire pour la nouvelle")
    # sauvegarde du Ptero : place libre ?
    sauvegarde = ENV.get("SAUVEGARDE", "true").lower() == "true"
    try:
        limite = api("GET", "")["attributes"]["feature_limits"]["backups"]
        nb = len(api("GET", "/backups").get("data", []))
        dire("- sauvegardes du Ptero : %d / %s utilisées%s" % (nb, limite, "" if not sauvegarde else
             (" ; une sauvegarde serait faite serveur arrêté" if nb < limite else
              " ; **plus de place : la vraie exécution s'arrêterait** (en supprimer une, ou décocher sauvegarde_ptero)")))
    except Exception as e:
        limite, nb = None, None
        dire("- sauvegardes du Ptero : illisibles (%s)" % type(e).__name__)

    if not executer:
        dire()
        dire("**Essai à blanc : rien n'a été changé.** Pour le faire vraiment : « Run workflow » depuis main, cocher "
             "« executer » et taper « %s »." % PHRASE)
        ecrire_resume()
        return
    ecrire_resume()

    # ================================================================= VRAIE EXÉCUTION
    if sauvegarde and limite is not None and nb >= limite:
        arreter("plus de place pour une sauvegarde du Ptero (%d / %d)." % (nb, limite))
    if local is not None:
        dire("### Envoi de la carte au Ptero (serveur en marche)")
        ok, _ = lftp("mkdir -p %s; mirror -R --parallel=4 %s %s;" % (q(src), q(str(local)), q(src)), 4 * 3600)
        recu = regions(src + "/region")
        if not ok or recu[0] != nouvelle["region"][0]:
            arreter("envoi incomplet (%d fichiers de régions reçus sur %d) : le dossier `%s` peut être supprimé "
                    "depuis le panel." % (recu[0], nouvelle["region"][0], src), rien=False)
        dire("- carte envoyée dans `%s` (%s)" % (src, fmt(recu)))
        ecrire_resume()

    # 1. arrêt propre
    dire("### Arrêt du serveur")
    e = etat()
    if e == "inconnu":
        arreter("l'API du Ptero ne répond pas.")
    if e != "offline":
        if e == "running":
            preavis = max(1, int(ENV.get("PREAVIS", "5") or 5))
            console("say [HeraliZ] Nouvelle carte : le serveur s'arrête dans %d minute(s) pour la mise en place." % preavis)
            if preavis > 1:
                time.sleep((preavis - 1) * 60)
                console("say [HeraliZ] Nouvelle carte : arrêt du serveur dans 1 minute.")
            time.sleep(60)
        try:
            api("POST", "/power", {"signal": "stop"})
        except Exception:
            arreter("le panel refuse d'arrêter le serveur.")
        for _ in range(90):
            time.sleep(10)
            e = etat()
            if e == "offline":
                break
        if e != "offline":
            arreter("le serveur ne s'est pas arrêté en 15 minutes (état : %s). Le vérifier dans le panel." % e)
    dire("- serveur arrêté")
    ecrire_resume()

    def demarrer():
        try:
            api("POST", "/power", {"signal": "start"})
        except Exception:
            erreur("le serveur n'a pas pu être démarré : le démarrer depuis le panel.")
            return "?"
        debut = time.time()
        e = "?"
        while time.time() - debut < 900:
            time.sleep(15)
            e = etat()
            if e == "running" or (e == "offline" and time.time() - debut > 60):
                break
        return e

    # 2. sauvegarde du Ptero (serveur arrêté : fichiers cohérents)
    if sauvegarde:
        try:
            b = api("POST", "/backups", {"name": "avant nouvelle carte " + date, "ignored": "", "is_locked": True})
            uid = b["attributes"]["uuid"]
            debut = time.time()
            fini = None
            while time.time() - debut < 3 * 3600:
                time.sleep(30)
                a = api("GET", "/backups/" + uid)["attributes"]
                if a.get("completed_at"):
                    fini = a.get("is_successful")
                    break
            if not fini:
                raise RuntimeError("sauvegarde pas terminée ou ratée")
            dire("- sauvegarde du Ptero faite (verrouillée) : « avant nouvelle carte %s »" % date)
        except Exception as ex:
            erreur("sauvegarde du Ptero impossible (%s) : rien n'a été changé, le serveur redémarre." % ex)
            dire("- état du serveur après le démarrage : " + demarrer())
            ecrire_resume()
            sys.exit(1)

    # 3. bascule (renommages seulement), avec retour en arrière
    dire("### Bascule")
    faits = []
    probleme = None
    dossiers = sorted({vers.rsplit("/", 1)[0] for _, vers in deplacements + arrivees})
    ok, _ = lftp(" ".join("mkdir -p %s;" % q(d) for d in [garde] + dossiers), 300)
    if not ok:
        probleme = "création de " + garde
    try:
        for de, vers in ([] if probleme else deplacements + arrivees):
            ok, _ = lftp("mv %s %s;" % (q(de), q(vers)), 300)
            if not ok or existe(de) or not existe(vers):
                if not existe(de) and existe(vers):   # renommé malgré l'erreur : à remettre aussi
                    faits.append((de, vers))
                probleme = "%s → %s" % (de, vers)
                break
            faits.append((de, vers))
    except Exception as ex:   # jamais laisser le serveur arrêté au milieu de la bascule
        probleme = "erreur inattendue : %s" % ex
    if probleme:
        erreur("bascule arrêtée (%s) : tout est remis comme avant." % probleme)
        for de, vers in reversed(faits):
            if not lftp("mv %s %s;" % (q(vers), q(de)), 300)[0]:
                erreur("impossible de remettre %s à sa place (%s) : le faire depuis le panel AVANT de rejouer." % (de, vers))
        dire("- état du serveur après le démarrage : " + demarrer())
        ecrire_resume()
        sys.exit(1)
    for de, vers in faits:
        dire("- `%s` → `%s`" % (de, vers))
    ecrire_resume()

    # 4. redémarrage et suite
    e = demarrer()
    dire("### Fait")
    dire("- état du serveur après le démarrage : " + e)
    dire("- ancienne carte gardée telle quelle dans `%s` (à supprimer depuis le panel quand tout va bien ; pour revenir "
         "en arrière : serveur arrêté, remettre ses dossiers et fichiers à leur place)" % garde)
    if source == "dossier_ptero":
        dire("- ce qui reste dans `%s` (ignoré) peut être supprimé depuis le panel" % src)
    dire("- gardés sans changement : spawn et autres mondes (dimensions/), level.dat, playerdata/, serverconfig/, "
         "point de réveil du spawn et autres données (data/)")
    dire("- **à refaire en jeu** (docs/NOUVELLE_CARTE.md §5 à §7) : bordure (`/worldborder`), zones à effets, points "
         "de la borne de départ (`/depart`), extractions (`/extraction create`, 12 au moins), lieux de la carte M, "
         "salles à carte, points des coffres T7, points de quête de la carte, lieux des événements, territoires "
         "(`config/heralisz_clans/zones.json`), masque de construction, fond de la carte M (nouveau jar joueur), "
         "voie du train ; puis les vérifications du §7")
    ecrire_resume()
    if e != "running":
        print("::warning::Serveur pas encore « running » (état : %s) : voir la console du panel." % e, flush=True)


if __name__ == "__main__":
    main()
