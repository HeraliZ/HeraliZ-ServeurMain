#!/usr/bin/env python3
"""
Directeur du tournage : lance le serveur local et les jeux des acteurs, joue une prise (script JSON) et filme les écrans.
Machine de tournage seulement (serveur Forge local + jeux sous Xvfb, voir LISEZMOI.md), jamais le Ptero.

  python3 outils/tournage/directeur.py demarrer           serveur + jeux A et B (ralenti LENT)
  python3 outils/tournage/directeur.py prise scene.json   joue la prise et filme (sortie : prises/<nom>_<acteur>.mp4)
  python3 outils/tournage/directeur.py console "cmd"      commande à la console du serveur
  python3 outils/tournage/directeur.py arreter

scene.json : {"nom": "duel", "duree": 400 (ticks de jeu), "avant": ["commandes console..."],
              "filmer": ["A", "B"], "A": [événements...], "B": [...]}
Événements (mod tournage, TournageClient) : {"t": tick, ...} avec touche/etat, regard, suivre, marcher, cmd, hud,
slot, camera, ips. Les « avant » sont tapés à la console du serveur avant le départ (téléportations, objets...).
"""
import json
import os
import subprocess
import sys
import time
from pathlib import Path

LENT = float(os.environ.get("LENT", "6"))
SRV = Path(os.environ.get("TOURNAGE_SERVEUR", "/root/srv"))
MC = Path(os.environ.get("TOURNAGE_JEUX", "/root/mc"))
DOSSIER = Path(os.environ.get("TOURNAGE_DOSSIER", "/root/tournage"))
SORTIE = Path(os.environ.get("PRISES", str(DOSSIER / "prises")))
ACTEURS = {"Raven_47": (":2", "2500M"), "Dimitri_Z": (":3", "2000M")}
L, H = 720, 1280


def sh(cmd):
    return subprocess.run(cmd, shell=True, capture_output=True, text=True).stdout


def console(cmd):
    subprocess.run(["tmux", "send-keys", "-t", "srv", cmd, "Enter"], check=True)


def attendre(test, limite, pas=3):
    t0 = time.time()
    while time.time() - t0 < limite:
        if test():
            return True
        time.sleep(pas)
    return False


def demarrer():
    arreter()
    (SRV / "user_jvm_args.txt").write_text("-Xms1G -Xmx2500M -Dtournage.lent=%s\n" % LENT)
    (SRV / "srv.log").write_text("")
    subprocess.run(["tmux", "new-session", "-d", "-s", "srv", "env -u JAVA_TOOL_OPTIONS ./run.sh nogui 2>&1 | tee srv.log"], cwd=SRV)
    assert attendre(lambda: "Done (" in (SRV / "srv.log").read_text(errors="replace"), 600), "serveur pas démarré"
    for nom, (d, mem) in ACTEURS.items():
        subprocess.run(["tmux", "new-session", "-d", "-s", nom, "%s/lancer.sh %s %s %d %d %s %s" % (MC, nom, d, L, H, LENT, mem)])
        time.sleep(5)
    for nom in ACTEURS:
        assert attendre(lambda: ("%s joined" % nom) in (SRV / "srv.log").read_text(errors="replace"), 900), nom + " pas connecté"
        console("op " + nom)
    print("prêt")


def arreter():
    for s in list(ACTEURS) + ["srv"]:
        if s == "srv" and subprocess.run(["tmux", "has-session", "-t", "srv"], capture_output=True).returncode == 0:
            console("stop")
            time.sleep(15)
        subprocess.run(["tmux", "kill-session", "-t", s], capture_output=True)
    sh("pkill Xvfb")


def prise(chemin):
    scene = json.loads(Path(chemin).read_text(encoding="utf-8"))
    # acteurs remis en jeu (mort à la prise d'avant : écran de mort fermé, réapparition)
    prep = DOSSIER / "prep.json"
    prep.write_text(json.dumps({n: [{"t": 0, "renaitre": True}, {"t": 1, "camera": False}] for n in ACTEURS}))
    (DOSSIER / "go.json").write_text(json.dumps({"script": str(prep), "n": time.time()}))
    time.sleep(4 + LENT)
    for c in scene.get("avant", []):
        console(c)
        time.sleep(0.4)
    time.sleep(scene.get("pause_avant", 6))
    SORTIE.mkdir(parents=True, exist_ok=True)
    duree_s = scene["duree"] * LENT / 20 + 4
    # la scène dure scene["duree"] ticks de jeu, au ralenti : on filme en temps réel, l'accélération se fait au montage
    films = []
    for nom in scene.get("filmer", []):
        d = ACTEURS[nom][0]
        f = SORTIE / ("%s_%s.mkv" % (scene["nom"], nom))
        films.append(subprocess.Popen(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-f", "x11grab", "-framerate", "30",
                                       "-video_size", "%dx%d" % (L, H), "-i", d, "-t", str(duree_s), "-c:v", "libx264",
                                       "-preset", "ultrafast", "-crf", "12", str(f)]))
    time.sleep(1.0)
    (DOSSIER / "go.json").write_text(json.dumps({"script": str(Path(chemin).resolve()), "n": time.time()}))
    for p in films:
        p.wait()
    # vitesse réelle : chaque seconde filmée = LENT fois moins de temps de jeu
    for nom in scene.get("filmer", []):
        src = SORTIE / ("%s_%s.mkv" % (scene["nom"], nom))
        dst = SORTIE / ("%s_%s.mp4" % (scene["nom"], nom))
        subprocess.run(["ffmpeg", "-nostdin", "-loglevel", "error", "-y", "-ss", "1", "-i", str(src), "-vf",
                        "setpts=PTS/%s,fps=30" % LENT, "-an", "-c:v", "libx264", "-crf", "14", str(dst)], check=True)
        print("prise : %s" % dst)


if __name__ == "__main__":
    {"demarrer": lambda: demarrer(), "arreter": lambda: arreter(), "prise": lambda: prise(sys.argv[2]),
     "console": lambda: console(sys.argv[2])}[sys.argv[1]]()
