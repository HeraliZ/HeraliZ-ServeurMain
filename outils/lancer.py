"""Lance Gradle et le client de dev sans que les agents qui travaillent en parallèle se marchent dessus.

    python outils/lancer.py build  <dossier projet> [tâches…]            (défaut : build)
    python outils/lancer.py client <dossier projet> <scenario.json> [--taille 1920x1080] [--xmx 2048m] [--attente 3600]
    python outils/lancer.py etat

- build : au plus 2 Gradle en même temps sur la machine (mémoire : 15 Go), file d'attente automatique ;
  JVM de Gradle limitée à 1 Go ; JAVA_TOOL_OPTIONS retiré de l'environnement (sinon le client hérite d'un GC série et rame).
- client : un seul client de dev à la fois, et au moins 3 Go de mémoire libre avant de lancer (attente sinon).
- Les verrous sont des dossiers dans work/verrous/ (création atomique) ; un verrou dont le processus est mort ou trop vieux
  (Gradle 40 min, client 90 min) est repris automatiquement.
- Ne JAMAIS faire « gradlew --stop » : cela tue les builds des autres.
Code de sortie = celui de Gradle ; les 60 dernières lignes de la sortie sont affichées, le journal complet est écrit dans
<projet>/run/lancer_<tâche>.log.
"""
import ctypes
import json
import os
import shutil
import subprocess
import sys
import time
from pathlib import Path
import sys as _sys
_sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
VERROUS = ROOT / "work" / "verrous"
SLOTS_GRADLE = ["gradle_1", "gradle_2"]
VIEUX = {"gradle": 40 * 60, "client": 90 * 60}


def memoire_libre_go():
    class MS(ctypes.Structure):
        _fields_ = [("dwLength", ctypes.c_ulong), ("dwMemoryLoad", ctypes.c_ulong), ("ullTotalPhys", ctypes.c_ulonglong),
                    ("ullAvailPhys", ctypes.c_ulonglong), ("ullTotalPageFile", ctypes.c_ulonglong), ("ullAvailPageFile", ctypes.c_ulonglong),
                    ("ullTotalVirtual", ctypes.c_ulonglong), ("ullAvailVirtual", ctypes.c_ulonglong), ("ullAvailExtendedVirtual", ctypes.c_ulonglong)]
    m = MS()
    m.dwLength = ctypes.sizeof(MS)
    ctypes.windll.kernel32.GlobalMemoryStatusEx(ctypes.byref(m))
    return m.ullAvailPhys / 1024 ** 3


def pid_vivant(pid):
    try:
        out = subprocess.run(["tasklist", "/FI", f"PID eq {pid}", "/NH"], capture_output=True, text=True, timeout=20).stdout
        return str(pid) in out
    except Exception:
        return True


def prendre(nom, genre, attente_max):
    VERROUS.mkdir(parents=True, exist_ok=True)
    d = VERROUS / nom
    debut = time.time()
    dernier_msg = 0
    while True:
        try:
            d.mkdir()
            (d / "info.json").write_text(json.dumps({"pid": os.getpid(), "depuis": time.time(), "qui": " ".join(sys.argv[1:])}), encoding="utf-8")
            return d
        except FileExistsError:
            try:
                info = json.loads((d / "info.json").read_text(encoding="utf-8"))
                age = time.time() - info.get("depuis", 0)
                if age > VIEUX[genre] or not pid_vivant(info.get("pid", -1)):
                    shutil.rmtree(d, ignore_errors=True)
                    continue
            except Exception:
                # verrou en cours d'écriture : on attend un peu
                pass
        if time.time() - debut > attente_max:
            return None
        if time.time() - dernier_msg > 60:
            print(f"[lancer] attente du verrou {nom}…", flush=True)
            dernier_msg = time.time()
        time.sleep(3)


def prendre_slot_gradle(attente_max):
    debut = time.time()
    while time.time() - debut < attente_max:
        for s in SLOTS_GRADLE:
            d = prendre(s, "gradle", 0.1)
            if d:
                return d
        time.sleep(4)
    return None


def env_propre():
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    env.pop("_JAVA_OPTIONS", None)
    env.setdefault("JAVA_HOME", str(chemins.JAVA_HOME))
    return env


def gradle(projet, taches, extra=None, journal_nom=None):
    projet = Path(projet).resolve()
    gw = projet / ("gradlew.bat" if os.name == "nt" else "gradlew")
    args = [str(gw)] + taches + ["-Dorg.gradle.jvmargs=-Xmx1024m", "--console=plain"] + (extra or [])
    (projet / "run").mkdir(exist_ok=True)
    log = projet / "run" / f"lancer_{journal_nom or '_'.join(t.strip(':') for t in taches)[:40]}.log"
    with open(log, "w", encoding="utf-8", errors="replace") as f:
        p = subprocess.run(args, cwd=projet, env=env_propre(), stdout=f, stderr=subprocess.STDOUT)
    lignes = log.read_text(encoding="utf-8", errors="replace").splitlines()
    print("\n".join(lignes[-60:]))
    print(f"[lancer] code {p.returncode} — journal complet : {log}")
    return p.returncode


def main():
    if len(sys.argv) < 2:
        raise SystemExit(__doc__)
    cmd = sys.argv[1]
    if cmd == "etat":
        VERROUS.mkdir(parents=True, exist_ok=True)
        for d in sorted(VERROUS.iterdir()):
            try:
                print(d.name, (d / "info.json").read_text(encoding="utf-8"))
            except Exception:
                print(d.name, "(illisible)")
        print(f"mémoire libre : {memoire_libre_go():.1f} Go")
        return
    if cmd == "build":
        projet, taches = sys.argv[2], sys.argv[3:] or ["build"]
        slot = prendre_slot_gradle(3 * 3600)
        if not slot:
            raise SystemExit("[lancer] pas de place pour Gradle après 3 h d'attente")
        try:
            sys.exit(gradle(projet, taches))
        finally:
            shutil.rmtree(slot, ignore_errors=True)
    if cmd == "client":
        projet, scenario = sys.argv[2], str(Path(sys.argv[3]).resolve())
        opts = sys.argv[4:]
        taille = opts[opts.index("--taille") + 1] if "--taille" in opts else "1920x1080"
        xmx = opts[opts.index("--xmx") + 1] if "--xmx" in opts else "2048m"
        attente = int(opts[opts.index("--attente") + 1]) if "--attente" in opts else 3600
        verrou = prendre("client", "client", attente)
        if not verrou:
            raise SystemExit("[lancer] un autre client de dev tourne encore (attente dépassée)")
        try:
            debut = time.time()
            while memoire_libre_go() < 3.0 and time.time() - debut < attente:
                print(f"[lancer] mémoire libre {memoire_libre_go():.1f} Go < 3 Go, attente…", flush=True)
                time.sleep(30)
            slot = prendre_slot_gradle(attente)
            try:
                sys.exit(gradle(projet, ["runClient"], [f"-Pharness={scenario}", f"-PharnessSize={taille}", f"-PharnessXmx={xmx}"],
                                journal_nom="client"))
            finally:
                if slot:
                    shutil.rmtree(slot, ignore_errors=True)
        finally:
            shutil.rmtree(verrou, ignore_errors=True)
    raise SystemExit(__doc__)


if __name__ == "__main__":
    main()
