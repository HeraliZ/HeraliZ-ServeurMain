"""Pack de jeu du launcher HeraliZ (distribution Helios), fait par Nebula, le générateur officiel de Helios.

    python outils/pack_launcher.py <dossier de Nebula> <sortie> [--version 1.0.42] [--essai]

Contenu : Forge 1.20.1-<FORGE> (installeur de Forge lancé sans fenêtre, repris par Nebula), les jars joueurs de
livraison/mods client (exactement ceux de la même fabrication que le serveur : Spartan compare leurs empreintes) et le
dossier tacz des gunpacks (modèles, sons, icônes des armes). --essai : sans fabrication, avec seulement les mods tiers
du dépôt.

Résultat dans <sortie>/envoi : distribution.json et les seuls fichiers qu'il cite, prêts à être déposés tels quels
dans le dossier de « distributionUrl » du launcher (launcher/app/heraliz.json : https://heraliz.fr/launcher/, dossier
/var/www/launcher de la VM du site). Chaque fichier est contrôlé (taille, MD5) et la distribution passe le contrôle du
launcher lui-même (launcher/app/assets/js/security.js). Résumé (nombre de mods et de fichiers, taille) dans
<sortie>/resume.json. Nebula a besoin de Node.js 22 et de Java (pour l'installeur de Forge).
"""
import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
import urllib.request
from pathlib import Path
from urllib.parse import unquote, urlparse

sys.path.insert(0, str(Path(__file__).resolve().parent))
from sans_secrets import env_propre  # noqa: E402

RACINE_DEPOT = Path(__file__).resolve().parent.parent
FORGE = "47.4.0"            # version de Forge de nos mods (gradle.properties)
SERVEUR = "heraliz"         # Nebula : dossier servers/heraliz-1.20.1, identifiant heraliz-1.20.1
MINECRAFT = "1.20.1"


def nebula(dossier, *args):
    print("[pack] nebula", " ".join(args), flush=True)
    subprocess.run(["node", "dist/index.js", *args], cwd=dossier, check=True, env=env_propre())


def md5(fichier):
    h = hashlib.md5()
    with open(fichier, "rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    return h.hexdigest()


def modules(liste):
    for m in liste or []:
        yield m
        yield from modules(m.get("subModules"))


def main():
    a = argparse.ArgumentParser()
    a.add_argument("nebula")
    a.add_argument("sortie")
    a.add_argument("--version", default="1.0.0")
    a.add_argument("--essai", action="store_true")
    args = a.parse_args()
    neb, sortie = Path(args.nebula).resolve(), Path(args.sortie).resolve()
    # le pack est déposé là où le launcher va le chercher
    config = json.loads((RACINE_DEPOT / "launcher" / "app" / "heraliz.json").read_text(encoding="utf-8"))
    if not str(config.get("distributionUrl") or "").startswith("https://") or not config["distributionUrl"].endswith("/distribution.json"):
        sys.exit("[pack] launcher/app/heraliz.json : distributionUrl doit être https://.../distribution.json.")
    base_url = config["distributionUrl"].rsplit("/", 1)[0] + "/"
    racine, envoi = sortie / "racine", sortie / "envoi"
    for d in (racine, envoi):
        shutil.rmtree(d, ignore_errors=True)
        d.mkdir(parents=True)

    java = shutil.which("java")
    if not java:
        sys.exit("[pack] Java introuvable (nécessaire à l'installeur de Forge).")
    (neb / ".env").write_text(
        f"JAVA_EXECUTABLE={java}\nROOT={racine}\nBASE_URL={base_url}\nHELIOS_DATA_FOLDER={sortie / 'helios'}\n",
        encoding="utf-8")
    nebula(neb, "init", "root")
    # sans les exemples de Nebula (flux RSS, Discord) : le launcher HeraliZ ne s'en sert pas
    meta_p = racine / "meta" / "distrometa.json"
    meta = json.loads(meta_p.read_text(encoding="utf-8"))
    meta["meta"] = {}
    meta_p.write_text(json.dumps(meta, indent=2), encoding="utf-8")
    nebula(neb, "generate", "server", SERVEUR, MINECRAFT, "--forge", FORGE)
    serveur = racine / "servers" / f"{SERVEUR}-{MINECRAFT}"
    if not serveur.is_dir():
        sys.exit(f"[pack] Nebula n'a pas créé {serveur}.")

    # mods des joueurs : ceux de la fabrication (ou, pour un essai, les mods tiers du dépôt)
    requis = serveur / "forgemods" / "required"
    requis.mkdir(parents=True, exist_ok=True)
    if args.essai:
        jars = sorted((RACINE_DEPOT / "serveur" / "mods-tiers").glob("*.jar")) + sorted((RACINE_DEPOT / "client" / "mods-tiers").glob("*.jar"))
    else:
        jars = sorted((RACINE_DEPOT / "livraison" / "mods client").glob("*.jar"))
    if not jars:
        sys.exit("[pack] Aucun jar joueur : lancer d'abord outils/construire.py.")
    for j in jars:
        shutil.copy2(j, requis / j.name)
    print(f"[pack] {len(jars)} mods joueurs", flush=True)

    # gunpacks TaCZ : le dossier tacz du jeu (les joueurs en ont besoin pour les modèles, sons et icônes)
    shutil.copytree(RACINE_DEPOT / "serveur" / "tacz", serveur / "files" / "tacz")

    # fiche du serveur : adresse de jeu (doit être celle du launcher), connexion directe, serveur principal
    fiche_p = serveur / "servermeta.json"
    fiche = json.loads(fiche_p.read_text(encoding="utf-8"))
    fiche["meta"].update({
        "version": args.version, "name": "HeraliZ", "description": "Survie façon DayZ sur Minecraft 1.20.1",
        "address": config["serverAddress"], "mainServer": True, "autoconnect": True,
    })
    for exemple in ("icon", "discord"):
        fiche["meta"].pop(exemple, None)
    fiche_p.write_text(json.dumps(fiche, indent=2, ensure_ascii=False), encoding="utf-8")

    # Nebula ouvre l'installeur de Forge en fenêtre et attend qu'on clique (impossible sur GitHub) : on l'installe
    # d'abord nous-mêmes, sans fenêtre, là où Nebula le cherche ; Nebula reprend ensuite cette installation.
    nom = f"{MINECRAFT}-{FORGE}"
    installeur = racine / "repo" / "lib" / "net" / "minecraftforge" / "forge" / nom / f"forge-{nom}-installer.jar"
    if not installeur.exists():
        installeur.parent.mkdir(parents=True, exist_ok=True)
        # même adresse que Nebula ; le serveur de Forge refuse les requêtes sans nom de navigateur
        url = f"https://files.minecraftforge.net/maven/net/minecraftforge/forge/{nom}/forge-{nom}-installer.jar"
        print(f"[pack] téléchargement de {url}", flush=True)
        requete = urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0 (HeraliZ pack launcher)"})
        with urllib.request.urlopen(requete, timeout=120) as r, open(installeur, "wb") as f:
            shutil.copyfileobj(r, f)
    cache = racine / "cache" / "forge" / nom
    cache.mkdir(parents=True, exist_ok=True)
    (cache / "launcher_profiles.json").write_text('{"profiles": {}}', encoding="utf-8")
    print("[pack] installeur de Forge (sans fenêtre)", flush=True)
    subprocess.run([java, "-Djava.awt.headless=true", "-jar", str(installeur), "--installClient", str(cache)], check=True,
                   env=env_propre())

    nebula(neb, "generate", "distro")
    distro_p = racine / "distribution.json"
    if not distro_p.exists():
        sys.exit("[pack] distribution.json introuvable après « generate distro ».")
    if distro_p.stat().st_size > 32 * 1048576:  # limite du launcher (launcher-service.js)
        sys.exit("[pack] distribution.json dépasse 32 Mo : le launcher la refuserait.")
    distro = json.loads(distro_p.read_text(encoding="utf-8"))

    # seuls les fichiers cités par la distribution partent, contrôlés un par un
    total, n = 0, 0
    for m in modules(s for srv in distro["servers"] for s in srv["modules"]):
        art = m["artifact"]
        url = art["url"]
        if not url.startswith(base_url):
            continue  # fichier pris ailleurs (Mojang, Forge) : le launcher le télécharge à la source
        rel = unquote(urlparse(url).path)[len(urlparse(base_url).path):]
        src = racine / rel
        if not src.is_file():
            sys.exit(f"[pack] fichier cité absent : {rel}")
        if src.stat().st_size != art["size"] or md5(src) != art["MD5"].lower():
            sys.exit(f"[pack] taille ou MD5 faux : {rel}")
        dst = envoi / rel
        dst.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(src, dst)
        total += art["size"]
        n += 1
    shutil.copy2(distro_p, envoi / "distribution.json")

    # le launcher doit accepter cette distribution (même contrôle que chez les joueurs)
    controle = (
        "const { validateDistribution } = require(process.argv[1]);"
        "const fs = require('fs');"
        "validateDistribution(JSON.parse(fs.readFileSync(process.argv[2], 'utf8')), JSON.parse(fs.readFileSync(process.argv[3], 'utf8')));"
        "console.log('[pack] distribution acceptée par le launcher');"
    )
    subprocess.run(["node", "-e", controle, str(RACINE_DEPOT / "launcher" / "app" / "assets" / "js" / "security.js"),
                    str(distro_p), str(RACINE_DEPOT / "launcher" / "app" / "heraliz.json")], check=True)

    # Signature Ed25519 du pack (liste des mods et empreintes, launcher/docs/HERALIZ-SIGNATURE.md) : distribution.sig à
    # côté de distribution.json. Un launcher qui porte une clé publique REFUSE un pack non signé : dans ce cas, pas de
    # mise en ligne sans le secret LAUNCHER_SIGN_KEY (sinon plus personne ne pourrait jouer).
    cle_publique = str(config.get("distributionPublicKey") or "").strip()
    if os.environ.get("LAUNCHER_SIGN_KEY", "").strip():
        subprocess.run(["node", str(RACINE_DEPOT / "launcher" / "ci" / "signer.mjs"), "--pack",
                        str(envoi / "distribution.json")] + ([cle_publique] if cle_publique else []), check=True)
        if not (envoi / "distribution.sig").is_file():
            sys.exit("[pack] signature du pack absente après signer.mjs --pack.")
    elif cle_publique:
        sys.exit("[pack] le launcher attend un pack signé (distributionPublicKey de launcher/app/heraliz.json) mais le "
                 "secret LAUNCHER_SIGN_KEY n'est pas réglé : pack NON envoyé (il serait refusé par tous les launchers).")
    else:
        print("[pack] pack non signé (ni clé publique dans le launcher, ni secret LAUNCHER_SIGN_KEY)")
    types = {}
    for m in modules(s for srv in distro["servers"] for s in srv["modules"]):
        types[m["type"]] = types.get(m["type"], 0) + 1
    print(f"[pack] modules : {types}")
    print(f"[pack] {n} fichiers à déposer, {total / 1048576:.0f} Mo (dans {envoi}) ; "
          f"distribution.json : {distro_p.stat().st_size / 1048576:.1f} Mo")
    (sortie / "resume.json").write_text(json.dumps({
        "version": args.version, "mods": len(jars), "fichiers": n, "octets": total,
        "distribution": config["distributionUrl"],
    }, indent=2), encoding="utf-8")


if __name__ == "__main__":
    main()
