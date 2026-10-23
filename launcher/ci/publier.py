"""Mise en ligne du launcher sur https://heraliz.fr/telecharger/ (workflow « Launcher HeraliZ », case « publier »).

    python ci/publier.py <fichier de version> <nom public>=<fichier local> [<nom public>=<fichier local> ...]

Exemples : « latest.yml HeraliZ-Launcher-Setup.exe=dist/HeraliZ-Launcher-Setup.exe » (Windows),
« latest-mac.yml HeraliZ-Launcher-mac.zip=… HeraliZ-Launcher.dmg=… » (Mac),
« latest-linux.yml HeraliZ-Launcher.AppImage=… HeraliZ-Launcher.deb=… » (Linux).

Chaque fichier est envoyé par SFTP sur la VM du site (dossier /var/www/telecharger), sous un nom provisoire puis
renommé d'un coup : jamais un fichier à moitié envoyé. Le fichier de version (version, empreinte SHA-512 et taille de
chaque fichier, lu par les launchers installés pour se mettre à jour) part en dernier : il ne désigne jamais un fichier
absent. Le premier fichier est le principal (« path » du fichier de version). Accès : variables d'environnement
SITE_SSH_HOTE, SITE_SSH_PORT, SITE_SSH_UTILISATEUR, SITE_SSH_MDP (secrets GitHub), jamais affichées ni passées en
ligne de commande.
"""

import base64
import datetime
import hashlib
import json
import os
import re
import subprocess
import sys

import paramiko

DOSSIER = "/var/www/telecharger"
ADRESSE = "https://heraliz.fr/telecharger/"
NOM_SUR = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,100}$")
PLATEFORMES = {
    "latest.yml": ("Windows", "les launchers Windows déjà installés (1.6.0 et plus) se mettent à jour seuls à leur prochain démarrage"),
    "latest-mac.yml": ("Mac", "les launchers Mac déjà installés préviennent le joueur et lui proposent le nouveau fichier"),
    "latest-linux.yml": ("Linux", "les launchers en AppImage se mettent à jour seuls ; ceux installés en .deb préviennent le joueur"),
}


def erreur(message):
    print(f"::error::{message}")
    sys.exit(1)


def empreinte(chemin):
    h = hashlib.sha512()
    with open(chemin, "rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    return base64.b64encode(h.digest()).decode()


def curl(*arguments):
    """Sortie de curl (présent sur les machines Windows, Mac et Linux de GitHub), ou None en cas d'échec."""
    try:
        return subprocess.run(["curl", "-fsS", "--max-time", "60", *arguments], capture_output=True, text=True,
                              check=True).stdout.replace("\r", "")
    except (OSError, subprocess.CalledProcessError):
        return None


def verifier_en_ligne(fichiers, fichier_version, version, signe):
    """Relit depuis internet ce qui vient d'être déposé : tailles des fichiers, version annoncée, signature."""
    for fichier in fichiers:
        entetes = curl("-I", ADRESSE + fichier["nom"]) or ""
        tailles = re.findall(r"(?im)^content-length:\s*(\d+)\s*$", entetes)
        if not tailles or int(tailles[-1]) != fichier["taille"]:
            erreur(f"{ADRESSE}{fichier['nom']} ne donne pas le fichier envoyé (taille {tailles[-1] if tailles else 'inconnue'}).")
    annonce = curl(ADRESSE + fichier_version) or ""
    if f"version: {version}" not in annonce.splitlines():
        erreur(f"{ADRESSE}{fichier_version} n'annonce pas la version {version} : les launchers installés ne la verront pas.")
    if signe and not (curl(ADRESSE + fichier_version + ".sig") or "").strip():
        erreur(f"{ADRESSE}{fichier_version}.sig introuvable en ligne : les launchers à clé publique refuseront la mise à jour.")
    plateforme, mises_a_jour = PLATEFORMES[fichier_version]
    resume = os.environ.get("GITHUB_STEP_SUMMARY")
    if resume:
        with open(resume, "a", encoding="utf-8") as f:
            f.write(f"### Launcher {version} en ligne ({plateforme})\n")
            for fichier in fichiers:
                f.write(f"- {ADRESSE}{fichier['nom']} ({fichier['taille'] // 1048576} Mo)\n")
            f.write(f"- {mises_a_jour}\n")
            if signe:
                f.write(f"- ✅ mise à jour **signée** ({fichier_version}.sig) : les launchers à clé publique l'acceptent\n")
            else:
                f.write("- ⚠️ mise à jour **NON signée** (secret `LAUNCHER_SIGN_KEY` absent) : un launcher avec clé "
                        "publique réglée REFUSERA cette mise à jour. Voir launcher/docs/HERALIZ-SIGNATURE.md\n")


def main(arguments):
    if len(arguments) < 2:
        erreur("Usage : publier.py <fichier de version> <nom public>=<fichier local> [...]")
    fichier_version, couples = arguments[0], arguments[1:]
    if fichier_version not in PLATEFORMES:
        erreur(f"Fichier de version inattendu : {fichier_version}")
    fichiers = []
    for couple in couples:
        nom, _, local = couple.partition("=")
        if not NOM_SUR.match(nom) or not local or not os.path.isfile(local):
            erreur(f"Fichier à publier introuvable ou nom invalide : {couple}")
        fichiers.append({"nom": nom, "local": local, "sha512": empreinte(local), "taille": os.path.getsize(local)})

    hote, utilisateur, mdp = (os.environ.get(v) for v in ("SITE_SSH_HOTE", "SITE_SSH_UTILISATEUR", "SITE_SSH_MDP"))
    if not hote or not utilisateur or not mdp:
        erreur("Secrets SITE_SSH_* manquants (Settings → Secrets and variables → Actions).")

    with open("package.json", encoding="utf-8") as f:
        version = json.load(f)["version"]
    date = datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%S.000Z")
    lignes = [f"version: {version}", "files:"]
    for fichier in fichiers:
        lignes += [f"  - url: {fichier['nom']}", f"    sha512: {fichier['sha512']}", f"    size: {fichier['taille']}"]
    lignes += [f"path: {fichiers[0]['nom']}", f"sha512: {fichiers[0]['sha512']}", f"releaseDate: '{date}'"]
    with open(fichier_version, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lignes) + "\n")

    # Signature Ed25519 du fichier de version (il contient l'empreinte SHA-512 de l'installeur : le signer protège
    # donc aussi l'installeur). Sans le secret LAUNCHER_SIGN_KEY, aucune signature n'est produite : les launchers sans
    # clé publique réglée se mettent à jour comme avant ; ceux qui ont une clé REFUSERONT la mise à jour (prévenir).
    signe = False
    if os.environ.get("LAUNCHER_SIGN_KEY"):
        try:
            subprocess.run(["node", os.path.join("ci", "signer.mjs"), "--signer", fichier_version],
                           check=True, capture_output=True, text=True)
            signe = os.path.isfile(fichier_version + ".sig")
        except (OSError, subprocess.CalledProcessError) as e:
            erreur(f"Signature du fichier de version impossible : {type(e).__name__}")
        if not signe:
            erreur("LAUNCHER_SIGN_KEY réglé mais aucune signature produite (clé invalide ?).")

    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    try:
        client.connect(hote, port=int(os.environ.get("SITE_SSH_PORT") or 22), username=utilisateur, password=mdp,
                       timeout=20, banner_timeout=30, auth_timeout=30, allow_agent=False, look_for_keys=False)
    except Exception as e:
        erreur(f"Connexion à la VM du site impossible : {type(e).__name__}")
    sftp = client.open_sftp()
    try:
        sftp.stat(DOSSIER)
    except OSError:
        erreur(f"La VM du site n'est pas prête (dossier {DOSSIER}) : lancer le workflow « Site HeraliZ » avec "
               "« installer », puis relancer celui-ci.")

    def deposer(local, nom):
        provisoire = f"{DOSSIER}/.{nom}.envoi"
        sftp.put(local, provisoire, confirm=True)
        sftp.chmod(provisoire, 0o644)
        sftp.posix_rename(provisoire, f"{DOSSIER}/{nom}")

    for fichier in fichiers:
        deposer(fichier["local"], fichier["nom"])
    # La signature part AVANT le fichier de version : jamais un latest.yml en ligne sans sa signature à côté.
    if signe:
        deposer(fichier_version + ".sig", fichier_version + ".sig")
    deposer(fichier_version, fichier_version)
    sftp.close()
    client.close()
    noms = ", ".join(f["nom"] for f in fichiers)
    print(f"Launcher {version} déposé dans {DOSSIER} ({noms} et {fichier_version})")
    verifier_en_ligne(fichiers, fichier_version, version, signe)


if __name__ == "__main__":
    main(sys.argv[1:])
