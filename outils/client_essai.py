"""Client d'essai pour l'audit des écrans (docs/AUDIT_UI.md) : prépare un dossier de jeu à partir d'un profil CurseForge à
jour, y ajoute HeraliZ Capture (modules/capture, jamais livré) et un script, puis lance Minecraft (Forge du lanceur
CurseForge, compte hors ligne) qui rejoint le serveur de test, joue le script et se ferme.

    python outils/client_essai.py <profil CurseForge> <dossier du client> <script.json> [--serveur 127.0.0.1:25599]
                                   [--pseudo HzCapture] [--memoire 3G] [--taille 1920x1080] [--forge forge-47.4.0]

- <profil> : dossier d'une instance CurseForge à jour (mods joueurs de la même fabrication que le serveur de test) ;
- <dossier du client> : copie de travail (mods, config, packs, tacz, options.txt), jamais le profil lui-même ;
- les captures arrivent dans <dossier du client>/screenshots/, le journal dans <dossier du client>/capture/journal.txt.
Le serveur de test doit accepter un compte hors ligne sans le launcher HeraliZ (tokenRequired et captureRequired à false
dans son spartan-common.toml) ; c'est un serveur d'essai, jamais le Ptero.
"""
import argparse
import hashlib
import os
import json
import shutil
import subprocess
import sys
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins  # noqa: E402

COPIER = ("config", "defaultconfigs", "resourcepacks", "tacz", "emotes", "options.txt", "servers.dat")


def uuid_hors_ligne(pseudo):
    h = bytearray(hashlib.md5(("OfflinePlayer:" + pseudo).encode("utf-8")).digest())
    h[6] = (h[6] & 0x0F) | 0x30
    h[8] = (h[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(h)))


def lier(src, dst):
    """Lien dur vers le fichier du profil (mods, gunpacks : rien de dupliqué sur le disque, ~500 Mo par client) ; copie si
    impossible (autre disque) et pour les réglages que le jeu réécrit (.toml, .json de config)."""
    if not str(src).endswith((".toml", ".cfg", ".txt")):
        try:
            if os.path.exists(dst):
                os.remove(dst)
            os.link(src, dst)
            return dst
        except OSError:
            pass
    return shutil.copy2(src, dst)


def preparer(profil, dossier, script, serveur):
    dossier.mkdir(parents=True, exist_ok=True)
    for n in COPIER:
        src = profil / n
        if not src.exists():
            continue
        dst = dossier / n
        if src.is_dir():
            shutil.copytree(src, dst, dirs_exist_ok=True, copy_function=lier if n == "tacz" else shutil.copy2)
        else:
            shutil.copy2(src, dst)
    mods = dossier / "mods"
    if mods.exists():
        shutil.rmtree(mods)
    shutil.copytree(profil / "mods", mods, ignore=shutil.ignore_patterns("*.disabled"), copy_function=lier)
    capture = sorted((chemins.MODS / "capture" / "build" / "libs").glob("heraliz_capture-*-client.jar"))
    if not capture:
        raise SystemExit("HeraliZ Capture pas fabriqué : python outils/construire.py capture")
    shutil.copy2(capture[-1], mods / capture[-1].name)
    # options : interface en français, pas de pause quand la fenêtre n'a pas le focus, pas de tutoriel
    opt = dossier / "options.txt"
    lignes = opt.read_text(encoding="utf-8").splitlines() if opt.exists() else []
    voulu = {"lang": "fr_fr", "pauseOnLostFocus": "false", "fullscreen": "false", "tutorialStep": "none", "onboardAccessibility": "false"}
    vus = set()
    for i, l in enumerate(lignes):
        k = l.split(":", 1)[0]
        if k in voulu:
            lignes[i] = k + ":" + voulu[k]
            vus.add(k)
    lignes += [k + ":" + v for k, v in voulu.items() if k not in vus]
    opt.write_text("\n".join(lignes) + "\n", encoding="utf-8")
    # bouton Jouer du jeu : le serveur de test
    toml = dossier / "config" / "heraliz_interface-client.toml"
    if toml.exists():
        t = toml.read_text(encoding="utf-8")
        import re
        t = re.sub(r'adresse = "[^"]*"', 'adresse = "%s"' % serveur, t)
        toml.write_text(t, encoding="utf-8")
    (dossier / "capture").mkdir(exist_ok=True)
    shutil.copy2(script, dossier / "capture" / "script.json")
    j = dossier / "capture" / "journal.txt"
    if j.exists():
        j.unlink()


def regles_ok(regles):
    if not regles:
        return True
    ok = False
    for r in regles:
        os_ = r.get("os", {})
        vaut = (not os_ or os_.get("name") in (None, "windows")) and not r.get("features")
        if vaut:
            ok = r["action"] == "allow"
    return ok


def chemin_lib(lib):
    art = lib.get("downloads", {}).get("artifact")
    if art and art.get("path"):
        return art["path"]
    g, a, v = lib["name"].split(":")[:3]
    clas = lib["name"].split(":")[3] if lib["name"].count(":") >= 3 else None
    return "%s/%s/%s/%s-%s%s.jar" % (g.replace(".", "/"), a, v, a, v, "-" + clas if clas else "")


def commande(dossier, version, pseudo, memoire, taille, serveur):
    inst = chemins.LANCEUR
    libdir = inst / "libraries"
    enfant = json.loads((inst / "versions" / version / (version + ".json")).read_text(encoding="utf-8"))
    parent = json.loads((inst / "versions" / enfant["inheritsFrom"] / (enfant["inheritsFrom"] + ".json")).read_text(encoding="utf-8"))
    cp, vus = [], set()
    for lib in enfant["libraries"] + parent["libraries"]:
        if not regles_ok(lib.get("rules")):
            continue
        cle = ":".join(lib["name"].split(":")[:2] + lib["name"].split(":")[3:4])
        if cle in vus:
            continue
        vus.add(cle)
        p = libdir / chemin_lib(lib)
        if p.exists():
            cp.append(str(p))
    cp.append(str(inst / "versions" / version / (version + ".jar")))
    natives = dossier / "natives"
    natives.mkdir(exist_ok=True)
    larg, haut = taille.split("x")
    val = {"library_directory": str(libdir), "classpath_separator": ";", "version_name": version, "natives_directory": str(natives),
           "launcher_name": "heraliz-essai", "launcher_version": "1", "classpath": ";".join(cp), "auth_player_name": pseudo,
           "game_directory": str(dossier), "assets_root": str(inst / "assets"), "assets_index_name": parent["assetIndex"]["id"],
           "auth_uuid": uuid_hors_ligne(pseudo), "auth_access_token": "0", "clientid": "0", "auth_xuid": "0", "user_type": "legacy",
           "version_type": "release"}

    def remplir(s):
        for k, v in val.items():
            s = s.replace("${%s}" % k, v)
        return s

    def args(liste):
        out = []
        for a in liste:
            if isinstance(a, str):
                out.append(remplir(a))
            elif regles_ok(a.get("rules")):
                v = a["value"]
                out += [remplir(x) for x in (v if isinstance(v, list) else [v])]
        return out

    jvm = ["-Xmx" + memoire, "-Xms1G"] + args(parent["arguments"]["jvm"]) + args(enfant["arguments"]["jvm"])
    jeu = args(parent["arguments"]["game"]) + args(enfant["arguments"]["game"])
    jeu += ["--width", larg, "--height", haut, "--quickPlayMultiplayer", serveur]
    java = shutil.which("java") or "java"
    return [java] + jvm + [enfant["mainClass"]] + jeu


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("profil")
    ap.add_argument("dossier")
    ap.add_argument("script")
    ap.add_argument("--serveur", default="127.0.0.1:25599")
    ap.add_argument("--pseudo", default="HzCapture")
    ap.add_argument("--memoire", default="3G")
    ap.add_argument("--taille", default="1920x1080")
    ap.add_argument("--forge", default="forge-47.4.0")
    ap.add_argument("--sans-copie", action="store_true", help="ne recopie que le script (dossier déjà prêt)")
    a = ap.parse_args()
    dossier = Path(a.dossier).resolve()
    if a.sans_copie:
        (dossier / "capture").mkdir(parents=True, exist_ok=True)
        shutil.copy2(a.script, dossier / "capture" / "script.json")
        j = dossier / "capture" / "journal.txt"
        if j.exists():
            j.unlink()
    else:
        preparer(Path(a.profil).resolve(), dossier, Path(a.script).resolve(), a.serveur)
    cmd = commande(dossier, a.forge, a.pseudo, a.memoire, a.taille, a.serveur)
    with open(dossier / "client_essai.log", "w", encoding="utf-8") as log:
        r = subprocess.run(cmd, cwd=dossier, stdout=log, stderr=subprocess.STDOUT)
    print("client fermé, code", r.returncode, "; captures :", dossier / "screenshots")
    sys.exit(r.returncode)


if __name__ == "__main__":
    main()
