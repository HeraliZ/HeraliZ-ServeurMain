"""Banc de charge HeraliZ sur une machine à part (01/11) : un vrai serveur Mohist avec nos mods, des bots qui jouent,
une mesure, un rapport. Rien ne touche au Ptero ni au site. Sert au workflow « Banc de charge »
(.github/workflows/banc.yml) ; marche aussi sur un PC Linux ou Windows avec Java 17.

    python outils/banc_charge.py preparer <dossier> [--plat] [--graine N] [--vue 6] [--simulation 4]
    python outils/banc_charge.py lancer <dossier> [--bots 100] [--chauffe 4] [--minutes 5] [--diag "..."]
                                        [--scenario normal|connexions|teleportations] [--rythmes 5/2/4/25|off]
                                        [--regles "randomTickSpeed 0; ..."] [--ab] [--memoire auto|<Mo>]
                                        [--java <java 17>] [--rapport <dossier>] [--duree-max 50]

preparer : serveur d'essai fait par outils/serveur_local.py <dossier> --banc (dernière fabrication de livraison/), puis
    réglages d'essai DANS CE DOSSIER SEULEMENT (le dépôt n'est pas touché) : EULA du dépôt, server-ip=127.0.0.1, monde
    normal (ou plat avec --plat) à graine fixe, distances du Ptero (vue 6, simulation 4, relevées le 01/11 ; le
    server.properties du dépôt dit 8 et 5 mais n'est envoyé qu'en installation complète), secrets vidés (Spartan sans
    jeton du launcher, LiteBans en base locale H2, lien Discord coupé), « Can't keep up » affichés, zombies du peuplement
    aussi autour des bots, commandes des bots (outils/banc/commandes.txt). Le jar de Mohist (mohist-*.jar) est à poser
    dans le dossier (Release « base-serveur »).
lancer :
  0. machine Java (01/11) : au premier démarrage, Mohist installe Forge puis SE RELANCE dans une autre machine Java par
     « java -jar mohist.jar », sans aucun réglage de la ligne de commande (premier banc : 11 Go demandés, 3996 Mo vus,
     soit 1/4 de la mémoire de la machine ; sans le journal du ramasse-miettes ni le mode essai d'AuthMe ; et la
     première machine Java gardait ses 11 Go pendant ce temps). Ici : les réglages passent par JDK_JAVA_OPTIONS (que
     toute machine Java lancée par « java » reprend), et l'installation, s'il y en a une à faire, est faite d'abord à
     part (petit tas, arrêtée dès que le serveur relancé a démarré) : la mesure tourne toujours dans UNE machine Java,
     celle que le banc a lancée. Le rapport écrit ses vrais arguments et sa mémoire maximale ;
  1. comme le Ptero : doMobSpawning false (les zombies viennent du peuplement) ;
  2. spawn du banc (dalle sous le point de réveil, /bots camp), zones de groupe (/bots zone : lieu de vie, zone
     d'arrivée de la vraie borne de départ), terrain des zones créé d'avance (/bots pregenerer) AVANT les bots ;
  3. bots : 10 % dans le tutoriel des bases (/tuto lancer), le reste par groupes de 5 sur une grille de la carte ; chaque
     bot part quand son tronçon d'arrivée est prêt (chargé en tâche de fond) ; zones d'extraction près de certains
     groupes, commandes de joueur tapées par les bots (/bots commandes), rythmes de 150 joueurs (/bots rythmes : morts,
     connexions, voyages par minute, part du temps en combat) ;
  4. chauffe : au moins --chauffe minutes, et tant que du terrain se crée encore ou que les arènes du ClashWar se
     posent (premier démarrage d'un monde neuf), au plus 2 fois --chauffe ;
  5. mesure : /bots mesure et le diagnostic du noyau (--diag) pendant --minutes ; scénario « connexions » (une connexion
     toutes les 10 s environ, coût de chacune) ou « teleportations » (pics : extraction de groupe, /spawn de 10 joueurs,
     groupe qui part de la borne : pire tick de chaque cas) ; essais A/B (--regles, --ab : une mesure sans, puis une
     avec les règles de jeu changées dans tous les mondes) ;
  6. arrêt propre, puis rapport : resume.md (aussi dans le résumé de la tâche GitHub), rapports du diagnostic (diag/),
     journal complet, journal du ramasse-miettes, erreurs Java, « Can't keep up ».
Code de sortie 1 si le serveur n'a pas démarré ou si la mesure n'a pas eu lieu.
"""
import argparse
import hashlib
import json
import math
import os
import re
import shutil
import signal
import subprocess
import sys
import threading
import time
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
SRV = RACINE / "serveur"
COMMANDES_BOTS = RACINE / "outils" / "banc" / "commandes.txt"

DIAG_DEFAUT = "diag start {s}; diag mesure {s300}"
GRAINE_DEFAUT = 20261101
# relevés sur le Ptero le 01/11 (server.properties du Ptero ; celui du dépôt n'est envoyé qu'en installation complète)
VUE_PTERO, SIMULATION_PTERO, MAX_JOUEURS_PTERO = 6, 4, 50
# cibles par minute POUR 150 JOUEURS : morts / connexions / voyages (/home, /tpa) ; part du temps en combat (%)
# (estimations à recaler sur les journaux du Ptero : morts et connexions par heure aux heures pleines)
RYTHMES_DEFAUT = "5/2/4/25"
# Aikar (docs/DEPLOIEMENT.md), -Xms = -Xmx
AIKAR = ["-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200", "-XX:+UnlockExperimentalVMOptions",
         "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40",
         "-XX:G1HeapRegionSize=8M", "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
         "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90",
         "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32", "-XX:+PerfDisableSharedMem",
         "-XX:MaxTenuringThreshold=1"]
# première machine Java quand Mohist doit se relancer : petit tas, rend sa mémoire une fois l'installation faite, et
# n'écrit pas dans le journal du ramasse-miettes du serveur (ses réglages passent APRÈS ceux de JDK_JAVA_OPTIONS)
PARENT_LEGER = ["-Xms128M", "-Xmx2G", "-XX:-AlwaysPreTouch", "-XX:G1PeriodicGCInterval=15000", "-Xlog:disable",
                "-Xlog:all=warning:stdout"]

SORTIE = threading.Lock()


def ecrire(texte):
    """Une ligne entière dans le journal de la tâche (le fil de lecture du serveur écrit aussi)."""
    with SORTIE:
        sys.stdout.write(texte + "\n")
        sys.stdout.flush()


def dire(*a):
    ecrire("[banc] " + " ".join(str(x) for x in a))


# ---------------------------------------------------------------------------------------------------------- préparer

def proprietes(fichier, valeurs):
    """Remplace (ou ajoute) des clés d'un fichier .properties, le reste inchangé."""
    lignes = fichier.read_text(encoding="utf-8").splitlines() if fichier.exists() else []
    vues = set()
    for i, l in enumerate(lignes):
        cle = l.split("=", 1)[0].strip()
        if not l.lstrip().startswith("#") and "=" in l and cle in valeurs:
            lignes[i] = "%s=%s" % (cle, valeurs[cle])
            vues.add(cle)
    lignes += ["%s=%s" % (k, v) for k, v in valeurs.items() if k not in vues]
    fichier.write_text("\n".join(lignes) + "\n", encoding="utf-8")


def lire_proprietes(fichier):
    o = {}
    try:
        for l in fichier.read_text(encoding="utf-8", errors="replace").splitlines():
            if "=" in l and not l.lstrip().startswith("#"):
                k, v = l.split("=", 1)
                o[k.strip()] = v.strip()
    except OSError:
        pass
    return o


def remplacer(fichier, motifs, obligatoire=True):
    """Remplacements (regex, texte) dans un fichier du serveur d'essai ; prévient si un motif ne trouve rien."""
    if not fichier.exists():
        if obligatoire:
            dire("ATTENTION : %s absent" % fichier)
        return
    s = fichier.read_text(encoding="utf-8")
    for motif, par in motifs:
        s, n = re.subn(motif, par, s, flags=re.M)
        if n == 0 and obligatoire:
            dire("ATTENTION : « %s » introuvable dans %s" % (motif, fichier.name))
    fichier.write_text(s, encoding="utf-8")


def preparer(a):
    d = Path(a.dossier).resolve()
    r = subprocess.run([sys.executable, str(RACINE / "outils" / "serveur_local.py"), str(d), "--banc"])
    if r.returncode != 0:
        sys.exit("serveur_local.py a échoué (fabrication faite ? python outils/construire.py)")
    if not list((d / "mods").glob("heraliz_banc-*.jar")):
        sys.exit("module banc absent de livraison/banc/ : le fabriquer (python outils/construire.py banc)")
    shutil.copy2(SRV / "eula.txt", d / "eula.txt")   # EULA de Mojang acceptée par le propriétaire (fichier du dépôt)
    sp = d / "server.properties"
    monde = "dayz_map"
    for l in sp.read_text(encoding="utf-8").splitlines():
        if l.startswith("level-name="):
            monde = l.split("=", 1)[1].strip() or monde
    vue, sim = max(2, min(32, a.vue)), max(2, min(32, a.simulation))
    # personne d'autre ne peut se connecter (et c'est la condition du mode essai d'AuthMe : PontBukkit.essaiLocal) ;
    # max-players 250 : la place des bots (le Ptero en est à 50, le rapport le dit)
    proprietes(sp, {"server-ip": "127.0.0.1", "max-players": "250", "level-seed": str(a.graine),
                    "level-type": "minecraft\\:flat" if a.plat else "minecraft\\:normal", "enable-rcon": "false",
                    "enable-query": "false", "motd": "HeraliZ banc de charge", "white-list": "false",
                    "view-distance": str(vue), "simulation-distance": str(sim)})
    remplacer(d / "bukkit.yml", [(r"^(\s*warn-on-overload:\s*)\S+", r"\g<1>true")])
    remplacer(d / "mohist-config" / "mohist.yml", [(r"^(\s*check_update:\s*)\S+", r"\g<1>false")], obligatoire=False)
    # secrets de la mise en ligne : vides ici (rien n'est envoyé nulle part)
    remplacer(d / "config" / "spartan-common.toml", [(r"__[A-Z_]+__", ""), (r"^(\s*tokenRequired\s*=\s*)\S+", r"\g<1>false"),
                                                     (r"^(\s*captureRequired\s*=\s*)\S+", r"\g<1>false")])
    remplacer(d / "plugins" / "LiteBans" / "config.yml", [(r"^(\s*driver\s*:\s*).+$", r"\g<1>H2"), (r"__[A-Z_]+__", "")])
    remplacer(d / "config" / "heraliz_grades" / "lien.json", [(r"__[A-Z_]+__", "")], obligatoire=False)
    # réglages du monde : ceux du dépôt, zombies du peuplement aussi autour des bots
    zomb = [(r"^(\s*bots\s*=\s*)\S+", r"\g<1>true")]
    remplacer(d / "defaultconfigs" / "heraliz_zombies-server.toml", zomb)
    sc = d / monde / "serverconfig"
    sc.mkdir(parents=True, exist_ok=True)
    for f in (d / "defaultconfigs").glob("*"):
        if f.is_file():
            shutil.copy2(f, sc / f.name)
    cb = d / "config" / "heraliz_banc"
    cb.mkdir(parents=True, exist_ok=True)
    shutil.copy2(COMMANDES_BOTS, cb / "commandes.txt")
    (d / "banc.json").write_text(json.dumps({"monde": monde, "plat": bool(a.plat), "graine": a.graine, "vue": vue,
                                             "simulation": sim}), encoding="utf-8")
    dire("serveur d'essai prêt dans %s (monde %s %s, graine %s, vue %s, simulation %s)" % (
        d, monde, "plat" if a.plat else "normal", a.graine, vue, sim))


# ---------------------------------------------------------------------------------------------------------- serveur

class Serveur:
    """Le serveur Mohist : console (entrée), journal (sortie) lu par un fil, attentes sur un motif."""

    IMPORTANT = re.compile(r"\]: Done \(|Can't keep up|\[banc\]|\[bots\]|\[Diag\]|\[diag\]|\[zombies\] peuplement|Stopping|"
                           r"[/ ](ERROR|FATAL)\]|Exception|Caused by|Mohist|Loading libraries|Download|Prepar|\[matchs\] (arenes|instance)|"
                           r"Picked up JDK_JAVA_OPTIONS|[Ii]nstall")

    def __init__(self, cmd, dossier, journal, env=None):
        self.cmd, self.dossier, self.env = cmd, dossier, env
        self.lignes = []
        self.moments = []
        self.verrou = threading.Condition()
        self.journal = open(journal, "a", encoding="utf-8", errors="replace")
        self.proc = None
        self.debut = 0.0
        self.affichees = 0
        self.fini = False   # tout le journal est lu (Java arrêté)

    def demarrer(self):
        dire("démarrage :", " ".join(self.cmd))
        if self.env and self.env.get("JDK_JAVA_OPTIONS"):
            dire("JDK_JAVA_OPTIONS (reprises par toute machine Java lancée par « java », Mohist relancé compris) :",
                 self.env["JDK_JAVA_OPTIONS"])
        self.debut = time.time()
        self.proc = subprocess.Popen(self.cmd, cwd=self.dossier, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT, env=self.env)
        threading.Thread(target=self._lire, daemon=True).start()

    def _lire(self):
        for brut in self.proc.stdout:
            l = brut.decode("utf-8", "replace").rstrip("\r\n")
            l = re.sub(r"\x1b\[[0-9;]*[A-Za-z]", "", l)   # couleurs de la console
            self.journal.write(l + "\n")
            with self.verrou:
                self.lignes.append(l)
                self.moments.append(time.time())
                self.verrou.notify_all()
            if self.IMPORTANT.search(l) and not l.lstrip().startswith("at ") and self.affichees < 4000:
                self.affichees += 1
                ecrire("  | " + l[:400])
        self.journal.flush()
        with self.verrou:
            self.fini = True
            self.verrou.notify_all()

    def vivant(self):
        return self.proc is not None and self.proc.poll() is None

    def nb(self):
        with self.verrou:
            return len(self.lignes)

    def commande(self, texte):
        if not self.vivant():
            return
        dire("> " + texte)
        try:
            self.proc.stdin.write((texte + "\n").encode("utf-8"))
            self.proc.stdin.flush()
        except OSError as e:
            dire("console fermée (%s)" % e)

    def attendre(self, motif, depuis=0, limite=60.0):
        """Première ligne (à partir de l'indice depuis) qui correspond au motif, ou None (délai, serveur arrêté)."""
        rx = re.compile(motif)
        fin = time.time() + limite
        i = depuis
        with self.verrou:
            while True:
                while i < len(self.lignes):
                    m = rx.search(self.lignes[i])
                    if m:
                        return m
                    i += 1
                reste = fin - time.time()
                # Java arrêté : seulement quand tout son journal est lu (des lignes peuvent encore être en route)
                if reste <= 0 or self.fini:
                    return None
                self.verrou.wait(min(reste, 1.0))

    def depuis(self, i, j=None):
        with self.verrou:
            return list(self.lignes[i:j])

    def arreter(self, limite=240):
        if not self.vivant():
            return self.proc.returncode if self.proc else None
        self.commande("bots retirer")
        time.sleep(3)
        self.commande("stop")
        try:
            self.proc.wait(limite)
        except subprocess.TimeoutExpired:
            dire("le serveur ne s'arrête pas : arrêt forcé")
            self.proc.terminate()
            try:
                self.proc.wait(30)
            except subprocess.TimeoutExpired:
                self.proc.kill()
                self.proc.wait(30)
        time.sleep(1)
        self.journal.flush()
        return self.proc.returncode


def memoire_auto():
    """Tas de Java pour la machine : la mémoire moins 4,5 Go (système, mémoire hors tas de Java), entre 2 et 12 Go."""
    total = 0
    try:
        for l in open("/proc/meminfo", encoding="utf-8"):
            if l.startswith("MemTotal:"):
                total = int(l.split()[1]) // 1024
    except OSError:
        pass
    if total <= 0:
        return 8192
    return int(max(2048, min(12288, total - 4608)) // 256 * 256)


def md5(fichier):
    h = hashlib.md5()
    with open(fichier, "rb") as f:
        for bloc in iter(lambda: f.read(1 << 20), b""):
            h.update(bloc)
    return h.hexdigest()


def installation_prevue(d, jar):
    """Mohist va-t-il installer Forge (et donc se relancer dans une autre machine Java) ? Comme son lanceur
    (MohistMCStart, v_1_20_1$Install, Action.checkDependencies) : seulement si mohist.yml ne dit pas
    installation-finished: true, et si libraries/com/mohistmc/installation/installInfo manque ou ne porte pas (2e ligne)
    l'empreinte MD5 de ce jar de Mohist."""
    try:
        if re.search(r"^\s*installation-finished:\s*true\s*$", (d / "mohist-config" / "mohist.yml").read_text(
                encoding="utf-8", errors="replace"), re.M):
            return False
    except OSError:
        pass
    try:
        lignes = (d / "libraries" / "com" / "mohistmc" / "installation" / "installInfo").read_text(
            encoding="utf-8", errors="replace").splitlines()
    except OSError:
        return True
    return len(lignes) < 2 or lignes[1].strip().lower() != md5(jar)


def environnement(java):
    """Environnement du serveur : celui-ci, avec le dossier de --java en tête du PATH (Mohist se relance par « java »)."""
    env = dict(os.environ)
    dossier = os.path.dirname(java)
    if dossier:
        env["PATH"] = dossier + os.pathsep + env.get("PATH", "")
    return env


def installer(a, d, jar, tas, journal):
    """Installation de Mohist à part (premier démarrage) : la machine Java lancée ici installe Forge puis relance le
    serveur dans une autre ; on attend « Done » du serveur relancé, puis on l'arrête. Vrai si l'installation est faite."""
    env = environnement(a.java)
    env["JDK_JAVA_OPTIONS"] = " ".join(["-Xmx%dM" % tas, "-Dheraliz.essai.sansAuthme=true", "-Dfile.encoding=UTF-8"])
    s = Serveur([a.java] + PARENT_LEGER + ["-jar", jar.name, "nogui"], d, journal, env)
    s.demarrer()
    m = s.attendre(r"\]: Done \(", 0, a.attente_demarrage * 60)
    if not m:
        dire("installation de Mohist : « Done » jamais vu")
    s.arreter(120)
    return not installation_prevue(d, jar)


# ---------------------------------------------------------------------------------------------------------- banc

def repartition(bots, tuto, groupe, ecart):
    """Groupes de bots sur la carte : [(x, z, nombre)] sur une grille centrée sur 0 0, écart en blocs."""
    reste = bots - tuto
    n = max(1, math.ceil(reste / groupe)) if reste > 0 else 0
    if n == 0:
        return []
    cols = math.ceil(math.sqrt(n))
    rangs = math.ceil(n / cols)
    tailles = [reste // n + (1 if i < reste % n else 0) for i in range(n)]
    out = []
    for i, t in enumerate(tailles):
        c, r = i % cols, i // cols
        out.append((round((c - (cols - 1) / 2) * ecart), round((r - (rangs - 1) / 2) * ecart), t))
    return out


def ligne_json(serveur, motif, depuis, limite):
    """JSON de la première ligne « motif {json} » à partir de l'indice depuis, ou None."""
    m = serveur.attendre(motif + r" (\{.*\})\s*$", depuis, limite)
    if not m:
        return None
    try:
        return json.loads(m.group(1))
    except ValueError:
        return None


def lignes_json(lignes, motif):
    """Tous les JSON des lignes « motif {json} »."""
    out = []
    rx = re.compile(motif + r" (\{.*\})\s*$")
    for l in lignes:
        m = rx.search(l)
        if m:
            try:
                out.append(json.loads(m.group(1)))
            except ValueError:
                pass
    return out


def mesure_json(serveur, depuis, limite):
    return ligne_json(serveur, r"\[banc\] MESURE", depuis, limite)


def arenes_posees(serveur):
    """(posées, attendues) : au premier démarrage d'un monde neuf, ClashWar pose ses arènes d'avance, par tranches de
    6 ms par tick (ArenesService.prechauffer) ; la mesure attend qu'elles soient finies."""
    attendues = posees = 0
    for l in serveur.depuis(0):
        m = re.search(r"\[matchs\] arenes : (\d+) carte\(s\) posee\(s\) d'avance", l)
        if m:
            attendues += int(m.group(1))
        elif re.search(r"\[matchs\] instance \S+ posee", l):
            posees += 1
    return posees, attendues


def nouveaux_rapports(diag, apres, avant=None):
    if not diag.is_dir():
        return []
    return sorted(f for f in diag.glob("*.txt") if f.stat().st_mtime >= apres and (avant is None or f.stat().st_mtime < avant))


def regles_jeu(texte):
    """« randomTickSpeed 0; doFireTick false » -> [("randomTickSpeed", "0"), ("doFireTick", "false")]."""
    out = []
    for x in (texte or "").split(";"):
        p = x.split()
        if len(p) == 2 and re.fullmatch(r"[A-Za-z_]+", p[0]) and re.fullmatch(r"[A-Za-z0-9_.-]+", p[1]):
            out.append((p[0], p[1]))
        elif x.strip():
            dire("règle ignorée (« nom valeur » attendu) : %s" % x.strip())
    return out


def rythmes(texte):
    """« 5/2/4/25 » -> (5.0, 2.0, 4.0, 25) ; « off » -> None."""
    if (texte or "").strip().lower() in ("off", "non", "0"):
        return None
    p = [x for x in re.split(r"[/ ,;]+", texte.strip()) if x]
    try:
        m, c, v, k = (float(p[0]), float(p[1]), float(p[2]), int(float(p[3])))
    except (IndexError, ValueError):
        dire("rythmes illisibles (%s) : %s" % (texte, RYTHMES_DEFAUT))
        return rythmes(RYTHMES_DEFAUT)
    return max(0.0, m), max(0.0, c), max(0.0, v), max(0, min(100, k))


def mesurer(a, serveur, d, secondes, etiquette, res, c):
    """Une mesure : /bots mesure, diagnostic, pics du scénario ; ajoute {etiquette, mesure, pics, rapports...} à res."""
    time.sleep(2)
    debut = time.time()
    i = serveur.nb()
    c("bots mesure %d" % secondes)
    for x in [x.strip() for x in a.diag.split(";")]:
        if x:
            try:
                x = x.format(s=secondes, s300=min(300, secondes))
            except (KeyError, IndexError, ValueError):
                pass
            c(x)
    pics = 0
    if a.scenario == "connexions":
        # une connexion à la fois (déconnexion 3 s avant, 1 s de référence, 5 s mesurées) : environ une toutes les 10 s
        pics = max(3, secondes // 10)
        for _ in range(pics):
            c("bots pic connexion 1")
    elif a.scenario == "teleportations":
        # chaque tour : 6 bots d'un groupe extraits ensemble, 10 /spawn de partout, un groupe de 6 qui part de la borne
        tours = max(1, secondes // 45)
        for _ in range(tours):
            c("bots pic extraction 6")
            c("bots pic spawn 10")
            c("bots pic borne 6")
        pics = tours * 3
    quoi = "mesure" if etiquette == "mesure" else "mesure " + etiquette
    res["etapes"].append("%s lancée pour %d s (%.0f s)" % (quoi, secondes, time.time() - res["t0"]))
    dire("%s lancée pour %d s" % (quoi, secondes))
    j = mesure_json(serveur, i, secondes + 180)
    fin = time.time()
    # pics encore en cours : un peu de temps pour les finir (rien de plus n'est lancé)
    if pics:
        attente = time.time() + 120
        while time.time() < attente and len(lignes_json(serveur.depuis(i), r"\[banc\] PIC")) < pics and serveur.vivant():
            time.sleep(2)
    # rapports du diagnostic : écrits à la fin de leur durée (fil d'écriture du noyau)
    attente = time.time() + 90
    vus = -1
    while time.time() < attente and serveur.vivant():
        n = len(nouveaux_rapports(d / "diag", debut))
        if n == vus and n > 0:
            break
        vus = n
        time.sleep(10)
    m = {"etiquette": etiquette, "mesure": j, "debut": debut, "fin": fin, "ligne": i, "ligne_fin": serveur.nb(),
         "pics": lignes_json(serveur.depuis(i), r"\[banc\] PIC"),
         "rapports": [f.name for f in nouveaux_rapports(d / "diag", debut)]}
    res["mesures"].append(m)
    return j


def banc(a):
    d = Path(a.dossier).resolve()
    jars = sorted(d.glob("mohist-*.jar"))
    if not jars:
        sys.exit("jar de Mohist absent de %s (Release « base-serveur », voir serveur/mohist.sha256)" % d)
    jar = jars[-1]
    rapport = Path(a.rapport).resolve() if a.rapport else d / "rapport_banc"
    if rapport.exists():
        shutil.rmtree(rapport)
    rapport.mkdir(parents=True)
    (d / "logs").mkdir(exist_ok=True)
    gc_log = d / "logs" / "gc_banc.log"
    gc_log.unlink(missing_ok=True)
    tas = int(a.memoire) if str(a.memoire).isdigit() else memoire_auto()
    bots = max(1, min(200, a.bots))
    tuto = a.tuto if a.tuto >= 0 else (round(bots * 0.10) if bots >= 10 else 0)
    tuto = min(tuto, bots)
    groupes = repartition(bots, tuto, max(1, a.groupe), a.ecart)
    secondes = max(10, int(a.minutes * 60))
    ry = rythmes(a.rythmes)
    regles = regles_jeu(a.regles)
    info = {"bots": bots, "tuto": tuto, "groupes": len(groupes), "chauffe_min": a.chauffe, "secondes": secondes,
            "tas_mo": tas, "coeurs": os.cpu_count(), "diag": a.diag, "scenario": a.scenario,
            "rythmes": list(ry) if ry else None, "regles": regles, "ab": bool(a.ab and regles)}
    try:
        info.update(json.loads((d / "banc.json").read_text(encoding="utf-8")))
    except (OSError, ValueError):
        pass
    fin_max = time.time() + a.duree_max * 60
    res = {"info": info, "etapes": [], "mesures": [], "t0": time.time()}
    serveur = None

    def etape(t):
        res["etapes"].append("%s (%.0f s)" % (t, time.time() - res["t0"]))
        dire(t)

    def arret_demande(*_):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, arret_demande)
    try:
        # 0. machine Java : installation de Mohist d'abord, à part, s'il y en a une à faire
        if installation_prevue(d, jar):
            etape("Mohist doit d'abord installer Forge (il se relancerait dans une autre machine Java, sans nos réglages) : "
                  "installation à part")
            ok = installer(a, d, jar, tas, rapport / "installation.log")
            res["installation"] = "faite à part" if ok else "PAS finie"
            etape("installation de Mohist %s" % res["installation"])
        relance = installation_prevue(d, jar)
        if relance:
            # rare : l'installation à part n'a pas abouti ; la première machine Java reste petite, le serveur a moins
            tas = max(2048, tas - 2048)
            info["tas_mo"] = tas
        opts = ["-Xms%dM" % tas, "-Xmx%dM" % tas] + AIKAR + [
            "-Xlog:gc:file=%s:timemillis,uptime:filecount=0" % gc_log.relative_to(d).as_posix(),
            "-Dheraliz.essai.sansAuthme=true", "-Dfile.encoding=UTF-8"]
        env = environnement(a.java)
        env["JDK_JAVA_OPTIONS"] = " ".join(opts)
        cmd = [a.java] + (PARENT_LEGER if relance else []) + ["-jar", jar.name, "nogui"]
        info["jvm_demandee"] = {"options": opts, "commande": cmd, "relance_attendue": relance}
        # Mohist installe ses bibliothèques au premier lancement : s'il s'arrête avant « Done », on le relance une fois
        for essai in (1, 2):
            serveur = Serveur(cmd, d, rapport / "console.log", env)
            serveur.demarrer()
            m = serveur.attendre(r"\]: Done \(([\d.,]+)s\)", 0, min(a.attente_demarrage * 60, fin_max - time.time()))
            if m:
                break
            if serveur.vivant() or essai == 2:
                serveur.arreter(60)
                res["echec"] = "le serveur n'a pas fini de démarrer (« Done » jamais vu)"
                return finir(a, d, rapport, res, serveur, gc_log)
            dire("Mohist s'est arrêté avant « Done » (code %s) : nouvel essai" % serveur.proc.returncode)
        res["demarrage_s"] = round(time.time() - serveur.debut)
        res["ligne_done"] = len(serveur.lignes)
        res["pid_lance"] = serveur.proc.pid
        etape("serveur démarré en %s s" % res["demarrage_s"])
        res["jvm"] = ligne_json(serveur, r"\[banc\] JVM", 0, 60)
        jv = res["jvm"] or {}
        if jv:
            dire("machine Java du serveur : processus %s (lancé par le banc : %s), mémoire max %s Mo (demandé %s), vue %s, "
                 "simulation %s" % (jv.get("pid"), jv.get("pid") == serveur.proc.pid, jv.get("memoire_max_mo"), tas,
                                    jv.get("vue"), jv.get("simulation")))

        c = serveur.commande
        c("gamerule doMobSpawning false")   # comme le Ptero : les zombies viennent seulement du peuplement
        c("bots joueurs false")
        c("bots action reapparition true")
        # 1. spawn du banc, zones de groupe (lieu de vie, arrivée de la borne), terrain créé d'avance
        i = serveur.nb()
        c("bots camp")
        m = serveur.attendre(r"\[banc\] CAMP (.*)$", i, 60)
        res["camp"] = m.group(1) if m else None
        for (x, z, _) in groupes:
            c("execute in minecraft:overworld positioned %d 80 %d run bots zone %d %d" % (x, z, a.rayon, a.arrivee))
        vue = int(jv.get("vue") or info.get("vue") or VUE_PTERO)
        r_pregen = max(a.rayon, a.arrivee) + vue * 16 + 32
        if a.pregen_max > 0 and groupes:
            i = serveur.nb()
            for (x, z, _) in groupes:
                c("execute in minecraft:overworld positioned %d 80 %d run bots pregenerer %d" % (x, z, r_pregen))
            limite = min(a.pregen_max * 60, max(60, fin_max - time.time() - secondes * (2 if info["ab"] else 1) - 600))
            res["pregen"] = ligne_json(serveur, r"\[banc\] PREGEN", i, limite)
            if not res["pregen"]:
                c("bots pregenerer stop")
            p = res["pregen"] or {}
            etape("terrain des zones créé d'avance : %s tronçons en %s s (%s par seconde)%s" % (
                p.get("prets", "?"), p.get("secondes", "?"), p.get("par_seconde", "?"),
                "" if res["pregen"] else " — PAS fini dans le temps donné, arrêté"))
        # 2. bots du tutoriel lancés d'abord (ils prennent les noms HzBot001…), envoyés au tutoriel plus bas
        x0, z0 = (groupes[0][0], groupes[0][1]) if groupes else (0, 0)
        t_tuto = time.time()
        if tuto:
            c("execute in minecraft:overworld positioned %d 80 %d run bots lancer %d 24" % (x0, z0, tuto))
            time.sleep(2.5)
        # 3. groupes sur la carte : chaque bot part quand son tronçon d'arrivée est chargé (tâche de fond)
        for (x, z, n) in groupes:
            c("execute in minecraft:overworld positioned %d 80 %d run bots lancer %d %d" % (x, z, n, a.rayon))
            time.sleep(1.5)
        # /tuto lancer seulement quand les bots sont arrivés sur la carte : sinon leur départ les ramènerait sur la carte
        if tuto:
            time.sleep(max(0.0, t_tuto + 30 - time.time()))
            for k in range(1, tuto + 1):
                c("tuto lancer HzBot%03d" % k)
                time.sleep(0.5)
        etape("%d bots lancés (%d dans le tutoriel, %d groupes sur la carte)" % (bots, tuto, len(groupes)))
        time.sleep(10)
        # 4. zones d'extraction ouvertes près d'un groupe sur quatre (au sol), commandes et rythmes des bots
        for k, (x, z, _) in enumerate(groupes[::4]):
            nom = "banc%d" % (k + 1)
            c("execute in minecraft:overworld positioned %d 0 %d positioned over motion_blocking_no_leaves run extraction create %s 8 30"
              % (x + 12, z + 12, nom))
            c("extraction mode %s permanent" % nom)
        c("bots commandes %d" % a.commandes)
        c("bots rythmes %s %s %s %d" % ry if ry else "bots rythmes off")
        if regles and not info["ab"]:
            for (k, v) in regles:
                c("bots gamerule %s %s" % (k, v))
        # 5. chauffe : au moins --chauffe minutes, puis tant que du terrain se crée (mesures d'une minute)
        chauffe_fin = time.time() + a.chauffe * 60
        chauffe_max = time.time() + a.chauffe * 120
        tours = []
        while time.time() < chauffe_max and serveur.vivant() and time.time() < fin_max - secondes * (2 if info["ab"] else 1) - 300:
            i = serveur.nb()
            c("bots mesure 55")
            j = mesure_json(serveur, i, 120)
            if j:
                tours.append(j)
                dire("chauffe : %s bots en vie, %.1f ms/tick (pire %.0f), %s zombies, %s tronçons, %s créés en 1 min"
                     % (j.get("bots_max"), j.get("ms_moyenne", 0), j.get("ms_max", 0), j.get("zombies_max"),
                        j.get("troncons_max"), j.get("troncons_neufs")))
            arenes = arenes_posees(serveur)
            if arenes[0] < arenes[1]:
                dire("arènes du ClashWar posées d'avance (monde neuf) : %d sur %d" % arenes)
            if time.time() >= chauffe_fin and (not j or j.get("troncons_neufs", -1) < 50) and arenes[0] >= arenes[1]:
                break
            time.sleep(3)
        res["chauffe"] = [{k: t.get(k) for k in ("ms_moyenne", "ms_p95", "ms_max", "zombies_max", "troncons_max", "troncons_neufs",
                                                 "bots_max", "memoire_max_mo")} for t in tours]
        etape("chauffe finie (%d min ; arènes du ClashWar posées : %d sur %d)"
              % ((round((time.time() - (chauffe_fin - a.chauffe * 60)) / 60),) + arenes_posees(serveur)))
        if not serveur.vivant():
            res["echec"] = "le serveur s'est arrêté pendant la chauffe"
            return finir(a, d, rapport, res, serveur, gc_log)
        # 6. mesure(s) + diagnostic du noyau (rapports de la chauffe laissés de côté : écrits avant la mesure)
        res["arenes"] = arenes_posees(serveur)
        if info["ab"]:
            mesurer(a, serveur, d, secondes, "A (sans les règles)", res, c)
            i = serveur.nb()
            for (k, v) in regles:
                c("bots gamerule %s %s" % (k, v))
            serveur.attendre(r"\[banc\] GAMERULE", i, 30)
            time.sleep(20)
            mesurer(a, serveur, d, secondes, "B (avec les règles)", res, c)
        else:
            mesurer(a, serveur, d, secondes, "mesure", res, c)
        res["mesure"] = res["mesures"][-1]["mesure"] if res["mesures"] else None
        if not res["mesure"]:
            res["echec"] = "pas de résultat de /bots mesure"
        etape("mesure finie")
        # état à la fin, bots encore là (réponses dans le journal, gardées pour le résumé)
        i = serveur.nb()
        c("bots troncons")
        res["troncons"] = ligne_json(serveur, r"\[banc\] TRONCONS", i, 90)
        i = serveur.nb()
        for x in ("zombies stats", "diag etat", "forge tps", "bots etat"):
            c(x)
            time.sleep(2)
        time.sleep(3)
        res["etat_fin"] = [l[:300] for l in serveur.depuis(i)][:150]
    except KeyboardInterrupt:
        res["echec"] = "banc arrêté avant la fin (annulé)"
    finally:
        code = serveur.arreter() if serveur else None
        res["code_sortie"] = code
    return finir(a, d, rapport, res, serveur, gc_log)


# ---------------------------------------------------------------------------------------------------------- rapport

RX_RETARD = re.compile(r"Can't keep up! Is the server overloaded\? Running (\d+)ms or (\d+) ticks behind")
RX_ERREUR = re.compile(r"[/ ](ERROR|FATAL)\]")
RX_EXCEPTION = re.compile(r"((?:[a-zA-Z_$][\w$]*\.)+[A-Z][\w$]*(?:Exception|Error))(?::\s*(.*))?")
# -Xlog:gc:...:timemillis,uptime → « [1730479808123ms][12.345s] GC(5) Pause Young (Normal) ... 12.345ms »
RX_PAUSE = re.compile(r"^\[(\d+)ms\]\[[\d.,]+s\].*Pause (\w+).*?([\d.,]+)ms\s*$")


def journal(lignes):
    """« Can't keep up », lignes d'erreur et exceptions Java (avec leurs premières lignes de pile)."""
    retards = [(int(m.group(1)), int(m.group(2))) for l in lignes for m in [RX_RETARD.search(l)] if m]
    erreurs = Counter()
    exemples = {}
    i = 0
    while i < len(lignes):
        l = lignes[i]
        e = None
        if RX_ERREUR.search(l[:160]):
            e = re.sub(r"^\[[^\]]*\]\s*(\[[^\]]*\]\s*)*:?\s*", "", l)[:220]
        else:
            m = RX_EXCEPTION.search(l)
            if m and (l.startswith(m.group(1)) or l.startswith("Caused by") or "Exception in" in l):
                e = l.strip()[:220]
        if e:
            e = re.sub(r"\d{2,}", "N", e)   # mêmes messages aux nombres près
            erreurs[e] += 1
            if e not in exemples:
                pile = [l]
                k = i + 1
                while k < len(lignes) and len(pile) < 25 and (lignes[k].lstrip().startswith(("at ", "Caused by", "...")) or
                                                               RX_EXCEPTION.match(lignes[k].strip() or "-")):
                    pile.append(lignes[k])
                    k += 1
                exemples[e] = pile
        i += 1
    return retards, erreurs, exemples


def gc_pauses(fichier, debut_ms=None, fin_ms=None):
    """Pauses du ramasse-miettes [(heure ms, sorte, ms)], entre deux heures (millisecondes depuis 1970)."""
    pauses = []
    if not fichier.exists():
        return pauses
    for l in fichier.read_text(encoding="utf-8", errors="replace").splitlines():
        m = RX_PAUSE.match(l)
        if m:
            t = int(m.group(1))
            if (debut_ms is None or t >= debut_ms) and (fin_ms is None or t <= fin_ms):
                pauses.append((t, m.group(2), float(m.group(3).replace(",", "."))))
    return pauses


def ok(b):
    return "atteint" if b else "**PAS atteint**"


def mods_du_profil(texte):
    """Tableau « 1. Temps par mod ou plugin, pendant les ticks » d'un rapport profil-<date> : [(ms/tick, %, mod)]."""
    out, dedans = [], False
    for l in texte.splitlines():
        if l.startswith("== 1. Temps par mod"):
            dedans = True
            continue
        if dedans:
            if l.startswith("--") or l.startswith("=="):
                break
            m = re.match(r"^\s*([\d.]+)\s+([\d.]+) %\s+(.+?)\s*$", l)
            if m:
                out.append((float(m.group(1)), float(m.group(2)), m.group(3)))
    return out


def chauds_du_banc(texte, mod="heraliz_banc", n=8):
    """Points chauds du banc dans « 2 bis. Points chauds par mod » d'un rapport profil-<date>."""
    out, dedans = [], False
    for l in texte.splitlines():
        if l.startswith("-- %s :" % mod):
            dedans = True
            continue
        if dedans:
            if l.startswith("--") or l.startswith("==") or not l.strip():
                break
            out.append(l.strip())
    return out[:n]


def lire_spigot(fichier):
    """Réglages de spigot.yml qui comptent ici : portées d'activation, apparition des monstres."""
    o = {}
    try:
        s = fichier.read_text(encoding="utf-8", errors="replace")
    except OSError:
        return o
    m = re.search(r"entity-activation-range:\s*\n((?:\s+[\w-]+:.*\n)+)", s)
    if m:
        for k in ("animals", "monsters", "misc"):
            mm = re.search(r"^\s+%s:\s*(\S+)" % k, m.group(1), re.M)
            if mm:
                o["activation_" + k] = mm.group(1)
    mm = re.search(r"^\s+mob-spawn-range:\s*(\S+)", s, re.M)
    if mm:
        o["mob_spawn_range"] = mm.group(1)
    return o


def table_mesure(o, titre, j, r_mesure, pauses):
    o += ["### %s (%s ticks, %s s)" % (titre, j.get("ticks"), j.get("secondes")), "",
          "| ms par tick | moyenne | médiane | 95 % | 99 % | pire |", "|---|---|---|---|---|---|",
          "| | %s | %s | %s | %s | %s |" % (j.get("ms_moyenne"), j.get("ms_mediane"), j.get("ms_p95"), j.get("ms_p99"), j.get("ms_max")), "",
          "- **TPS réel : %s** (ticks faits / secondes) ; ticks de plus de 50 ms : %s, de plus de 100 ms : %s ; plus long écart "
          "entre deux débuts de tick : %s ms" % (j.get("tps_reel"), j.get("ticks_plus_50ms"), j.get("ticks_plus_100ms"), j.get("ecart_max_ms")),
          "- Objectifs : moyenne ≤ 30 ms %s, 95 %% ≤ 40 ms %s, 99 %% < 50 ms %s" % (
              ok(j.get("ms_moyenne", 99) <= 30), ok(j.get("ms_p95", 99) <= 40), ok(j.get("ms_p99", 99) < 50)),
          "- « Can't keep up » : %d pendant la mesure (pire retard %s ms)" % (len(r_mesure), max([x[0] for x in r_mesure], default=0)), ""]
    co = j.get("couts") or {}
    if co:
        o += ["- **Banc lui-même : %s ms par tick → ms par tick hors banc : %s** (le reste du tick : Minecraft, nos mods, "
              "plugins, et ce que la connexion de vrais joueurs ferait faire au serveur, %s ms par tick)" % (
                  co.get("banc_ms_tick"), co.get("hors_banc_ms_tick"), co.get("joueurs_total_ms_tick")), ""]
    o += ["- Mémoire : %s Mo au plus, %s Mo en moyenne ; gardée après le ramasse-miettes : %s Mo au plus (sur %s Mo)" % (
              j.get("memoire_max_mo"), j.get("memoire_moyenne_mo"), j.get("memoire_apres_gc_max_mo"), j.get("memoire_java_mo")),
          "- Ramasse-miettes : %s passages, %s ms en tout%s" % (j.get("gc_nombre"), j.get("gc_ms"),
              (" ; pauses : %d, pire %.0f ms, %d de plus de 50 ms" % (len(pauses), max(p[2] for p in pauses), sum(1 for p in pauses if p[2] > 50)))
              if pauses else ""),
          "- Processeur (Java, %s cœurs = 100 %%) : %s %% en moyenne, %s %% au plus" % (j.get("coeurs"), j.get("cpu_moyen_pct"), j.get("cpu_max_pct")), ""]


def finir(a, d, rapport, res, serveur, gc_log):
    lignes = serveur.depuis(0) if serveur else []
    i_done = res.get("ligne_done", len(lignes))
    apres_done = lignes[i_done:]
    r_demarrage, e_demarrage, _ = journal(lignes[:i_done])
    r_banc, e_banc, ex_banc = journal(apres_done)
    if gc_log.exists():
        shutil.copy2(gc_log, rapport / "gc.log")
    diag = d / "diag"
    debut_mesures = res["mesures"][0]["debut"] if res.get("mesures") else None
    rapports = nouveaux_rapports(diag, debut_mesures) if debut_mesures else []
    (rapport / "diag").mkdir(exist_ok=True)
    for f in nouveaux_rapports(diag, serveur.debut if serveur else 0):
        shutil.copy2(f, rapport / "diag" / f.name)
    with open(rapport / "erreurs.txt", "w", encoding="utf-8") as f:
        for e, n in e_banc.most_common():
            f.write("== %d fois : %s\n%s\n\n" % (n, e, "\n".join(ex_banc.get(e, []))))
    for nom in ("crash-reports",):
        if (d / nom).is_dir():
            for c in sorted((d / nom).glob("*.txt")):
                if serveur and c.stat().st_mtime >= serveur.debut:
                    shutil.copy2(c, rapport / c.name)

    info = res["info"]
    j = res.get("mesure") or {}
    jv = res.get("jvm") or j.get("jvm") or {}
    o = ["## Banc de charge HeraliZ — %s bots, %s min de mesure%s" % (
        info["bots"], round(info["secondes"] / 60, 1), "" if info.get("scenario", "normal") == "normal" else
        ", scénario « %s »" % info["scenario"]), ""]
    o.append("Machine : %s cœurs · monde %s %s, graine %s · %d bots dans le tutoriel, %d groupes sur la carte"
             % (info.get("coeurs"), info.get("monde", "?"), "plat" if info.get("plat") else "normal",
                info.get("graine"), info["tuto"], info["groupes"]))
    if os.environ.get("GITHUB_SHA"):
        o.append("Commit : `%s` (branche `%s`)" % (os.environ["GITHUB_SHA"][:10], os.environ.get("GITHUB_REF_NAME", "?")))
    o.append("")
    if res.get("echec"):
        o += ["**Échec : %s.** Voir le journal complet (artefact, console.log)." % res["echec"], ""]

    # ---- machine Java (01/11 : 11 Go demandés, 3996 Mo vus au premier banc)
    o += ["### Machine Java du serveur", ""]
    if jv:
        pid_ok = jv.get("pid") == res.get("pid_lance")
        demande = info.get("tas_mo")
        vu = jv.get("memoire_max_mo") or 0
        o.append("- Processus %s, %s ; mémoire max **%s Mo** (demandé : %s Mo, -Xms = -Xmx)%s" % (
            jv.get("pid"), "lancé par le banc (une seule machine Java)" if pid_ok else
            "**PAS celui lancé par le banc (%s) : Mohist s'est relancé dans une autre machine Java** (parent : %s)" % (
                res.get("pid_lance"), jv.get("parent_programme", "?")),
            vu, demande, "" if demande and abs(vu - demande) <= demande * 0.05 else " — **différent de la demande**"))
        o.append("- Java %s, ramasse-miettes : %s ; réglages pris dans JDK_JAVA_OPTIONS : %s%s" % (
            jv.get("java"), ", ".join(jv.get("ramasse_miettes") or []), "oui" if jv.get("jdk_java_options") else "non",
            " ; installation de Mohist : %s" % res["installation"] if res.get("installation") else ""))
        o += ["", "<details><summary>Arguments réels de la machine Java (RuntimeMXBean)</summary>", "", "```"] + \
             [str(x) for x in jv.get("arguments") or []] + ["```", "</details>", ""]
    else:
        o += ["Pas de ligne `[banc] JVM` (module banc trop ancien, ou serveur pas démarré).", ""]

    # ---- réglages (comme le Ptero)
    props = lire_proprietes(d / "server.properties")
    sp = lire_spigot(d / "spigot.yml")
    o += ["### Réglages (comme le Ptero du 01/11)", "",
          "| réglage | banc | Ptero (01/11) |", "|---|---|---|",
          "| view-distance (vue) | %s | %s |" % (jv.get("vue", props.get("view-distance", "?")), VUE_PTERO),
          "| simulation-distance | %s | %s |" % (jv.get("simulation", props.get("simulation-distance", "?")), SIMULATION_PTERO),
          "| max-players | %s (place des bots) | %s |" % (jv.get("max_joueurs", props.get("max-players", "?")), MAX_JOUEURS_PTERO),
          "| spigot : activation monstres / animaux / divers | %s / %s / %s | 24 / 5 / 16 |" % (
              sp.get("activation_monsters", "?"), sp.get("activation_animals", "?"), sp.get("activation_misc", "?")),
          "| spigot : mob-spawn-range | %s | 4 |" % sp.get("mob_spawn_range", "?"), ""]
    if info.get("regles"):
        o.append("- Règles de jeu changées dans tous les mondes%s : %s" % (
            " pour la mesure B seulement" if info.get("ab") else " avant la mesure",
            ", ".join("`%s %s`" % r for r in info["regles"])))
        for l in lignes:
            if "[banc] GAMERULE" in l:
                o.append("  - " + l.split("[banc] ", 1)[1][:300])
        o.append("")

    ar = res.get("arenes") or (0, 0)
    if ar[0] < ar[1]:
        o += ["Attention : arènes du ClashWar pas encore toutes posées au début de la mesure (%d sur %d, monde neuf) : leur pose "
              "(6 ms par tick au plus) compte dans la mesure." % tuple(ar), ""]
    if res.get("pregen") is not None or "pregen" in res:
        p = res.get("pregen") or {}
        o += ["Terrain des zones des bots créé d'avance (avant les bots, hors mesure) : %s tronçons en %s s%s." % (
            p.get("prets", "?"), p.get("secondes", "?"), "" if p else " (**pas fini dans le temps donné**)"), ""]

    # ---- mesures
    for k, mm in enumerate(res.get("mesures") or []):
        jm = mm.get("mesure") or {}
        if not jm:
            continue
        pendant = lignes[mm["ligne"]:mm["ligne_fin"]]
        r_mesure, _, _ = journal(pendant)
        pauses = gc_pauses(gc_log, int(mm["debut"] * 1000), int(mm["fin"] * 1000))
        titre = "Ticks pendant la mesure" if len(res["mesures"]) == 1 else "Mesure %s" % mm["etiquette"]
        table_mesure(o, titre, jm, r_mesure, pauses)
    if len([m for m in res.get("mesures") or [] if m.get("mesure")]) == 2:
        A, B = res["mesures"][0]["mesure"], res["mesures"][1]["mesure"]
        o += ["### Essai A/B : %s" % ", ".join("`%s %s`" % r for r in info.get("regles") or []), "",
              "| | A (sans) | B (avec) | écart |", "|---|---|---|---|"]
        for cle, nom in (("ms_moyenne", "ms par tick, moyenne"), ("ms_p95", "95 %"), ("ms_p99", "99 %"), ("ms_max", "pire"),
                         ("tps_reel", "TPS réel"), ("ticks_plus_50ms", "ticks > 50 ms")):
            va, vb = A.get(cle), B.get(cle)
            ec = ("%+.1f" % (vb - va)) if isinstance(va, (int, float)) and isinstance(vb, (int, float)) else ""
            o.append("| %s | %s | %s | %s |" % (nom, va, vb, ec))
        ca, cb = (A.get("couts") or {}).get("hors_banc_ms_tick"), (B.get("couts") or {}).get("hors_banc_ms_tick")
        if ca is not None and cb is not None:
            o.append("| ms par tick hors banc | %s | %s | %+.1f |" % (ca, cb, cb - ca))
        o += ["", "B est mesuré après A, dans le même serveur (un peu plus de bases, de zombies et de tronçons) : refaire "
              "l'essai dans l'autre ordre ou en deux bancs pour confirmer un petit écart.", ""]

    if j:
        # ---- coût du banc à part
        co = j.get("couts") or {}
        if co:
            o += ["### Coût du banc à part", "",
                  "| part du tick | ms par tick |", "|---|---|",
                  "| tick entier (moyenne) | %s |" % j.get("ms_moyenne"),
                  "| **banc lui-même** (cerveau des bots : cibles, vue, chemin ; préparation, rythmes, relevés) | **%s** |" % co.get("banc_ms_tick"),
                  "| **hors banc** (estimation de ce que coûteraient de vrais joueurs) | **%s** |" % co.get("hors_banc_ms_tick")]
            noms = {"tick_connexion": "dont tick de leur connexion (doTick)", "deplacements": "dont paquets de position (ChunkMap.move)",
                    "gestes": "dont gestes (tirs, lancers, soins, fouilles, frappes, constructions)",
                    "reapparitions": "dont réapparitions", "teleportations": "dont téléportations",
                    "connexions": "dont connexions et déconnexions"}
            for k2, v in (co.get("joueurs_ms_tick") or {}).items():
                o.append("| &nbsp;&nbsp;%s | %s |" % (noms.get(k2, k2), v))
            o += ["", "Le banc fait dans le tick ce que les paquets de vrais joueurs font faire au serveur entre deux ticks "
                  "(déplacements, gestes) : « hors banc » est donc le travail du fil serveur par tick pour ces joueurs.", ""]
        # ---- profil par mod
        profils = [f for f in rapports if f.name.startswith("profil-")]
        de = {n: mm["etiquette"] for mm in res.get("mesures") or [] for n in mm.get("rapports") or []}
        for f in profils:
            texte = f.read_text(encoding="utf-8", errors="replace")
            mods = mods_du_profil(texte)
            if not mods:
                continue
            o += ["### Profil : temps par mod pendant les ticks (`%s`%s)" % (
                      f.name, ", %s" % de[f.name] if len(res.get("mesures") or []) > 1 and f.name in de else ""), "",
                  "| mod ou plugin | ms par tick | % |", "|---|---|---|"]
            for ms, pct, mod in mods[:25]:
                o.append("| %s | %.2f | %.1f |" % (mod.replace("|", "/"), ms, pct))
            banc_p = next((x for x in mods if x[2].startswith("heraliz_banc")), None)
            if banc_p and co:
                o += ["", "`heraliz_banc` dans le profil (%.2f ms par tick) = le banc lui-même (%s ms, mesuré) + ce que Minecraft fait "
                      "quand le banc l'appelle à la place de la connexion d'un joueur (tick de la connexion, paquets de position, "
                      "réapparitions… : %s ms). Seule la première part est du banc pur." % (banc_p[0], co.get("banc_ms_tick"),
                                                                                           co.get("joueurs_total_ms_tick"))]
            chauds = chauds_du_banc(texte)
            if chauds:
                o += ["", "Points chauds du banc dans le profil :", "```"] + chauds + ["```"]
            o.append("")
        # ---- rythmes
        ry = j.get("rythmes") or {}
        if ry:
            cib = ry.get("cibles_150") or {}
            pm, p150, cpt = ry.get("par_minute") or {}, ry.get("pour_150") or {}, ry.get("comptes") or {}
            o += ["### Rythmes pendant la mesure", "",
                  "Cibles pour 150 joueurs : %s morts, %s connexions, %s voyages (/home, /tpa) par minute ; combat %s %% du temps "
                  "par zone%s ; facteur des dégâts entre bots (pour tenir les morts) : %s." % (
                      cib.get("morts"), cib.get("connexions"), cib.get("voyages"), cib.get("combat_pct"),
                      "" if ry.get("actif") else " (**rythmes coupés**)", ry.get("facteur_degats")), "",
                  "| | en tout | par minute | par minute pour 150 joueurs |", "|---|---|---|---|"]
            noms = {"morts": "morts", "reapparitions": "réapparitions (au camp)", "connexions": "connexions",
                    "deconnexions": "déconnexions", "borne": "départs de la borne", "voyages": "voyages (/home, /tpa)",
                    "extractions": "extractions (vers le spawn)", "retours": "retours sans la borne", "tutoriel": "vers le tutoriel",
                    "pics": "téléportations des pics", "autres": "autres téléportations", "teleportations": "**téléportations en tout**",
                    "tirs": "tirs"}
            for k2 in cpt:
                o.append("| %s | %s | %s | %s |" % (noms.get(k2, k2), cpt.get(k2), pm.get(k2), p150.get(k2)))
            o.append("")
        # ---- monde
        o += ["### Monde", "",
              "- Au plus : %s entités, %s monstres, **%s zombies HeraliZ**, %s objets au sol, %s tronçons chargés, %s joueurs" % (
                  j.get("entites_max"), j.get("monstres_max"), j.get("zombies_max"), j.get("objets_sol_max"), j.get("troncons_max"), j.get("joueurs_max")),
              "- Tronçons chargés pendant la mesure : %s (créés : %s%s)" % (j.get("troncons_charges"), j.get("troncons_neufs"),
                  " : du terrain s'est encore créé pendant la mesure, ce qui la charge un peu plus que la vraie carte"
                  if (j.get("troncons_neufs") or 0) > 50 else ""), "",
              "| monde | entités | monstres | zombies | objets | tronçons | joueurs |", "|---|---|---|---|---|---|---|"]
        for nom, m in (j.get("mondes") or {}).items():
            o.append("| %s | %s | %s | %s | %s | %s | %s |" % (nom, m.get("entites_max"), m.get("monstres_max"), m.get("zombies_max"),
                                                           m.get("objets_sol_max"), m.get("troncons_max"), m.get("joueurs_max")))
        o += ["", "(au plus pendant la mesure)", ""]
    # ---- ce qui tient les tronçons chargés
    tr = res.get("troncons") or {}
    if tr:
        o += ["### Tronçons chargés par monde, et ce qui les tient (à la fin)", "",
              "Un ticket de niveau N garde chargés les tronçons jusqu'à 44 − N tronçons autour de lui (un joueur : un ticket "
              "« player » de niveau 31 par tronçon de sa vue, donc sa vue + 13 tronçons autour). « tenus » = tronçons couverts "
              "par les tickets de cette sorte (une sorte peut en recouvrir une autre).", "",
              "| monde | chargés | joueurs | tickets par sorte : nombre (niveau) → tronçons tenus | sans ticket (en train de partir) |",
              "|---|---|---|---|---|"]
        for nom, m in tr.items():
            sortes = m.get("sortes") or {}
            txt = "; ".join("%s : %s (%s) → %s" % (k2, v.get("tickets"), v.get("niveau"), v.get("tenus"))
                            for k2, v in sorted(sortes.items(), key=lambda kv: -(kv[1].get("tenus") or 0)))
            o.append("| %s | %s | %s | %s | %s |" % (nom, m.get("charges"), m.get("joueurs"), txt or m.get("erreur", "aucun"),
                                                   m.get("sans_ticket", "?")))
        o += ["", "`start` : zone de départ gardée chargée par Minecraft (et Mohist) dans chaque monde, même vide ; "
              "`player` : vue des joueurs ; `heraliz_*` : nos tickets (arrivées de la borne, banc).", ""]
    if j:
        act = j.get("actions") or {}
        cm = j.get("commandes") or {}
        o += ["### Bots", "", "- Actions pendant la mesure : " + ", ".join("%s %s" % (v, k) for k, v in act.items()),
              "- Commandes tapées : %s (%s réussies), %s ms en tout, la plus lente %s ms (`%s`)" % (
                  cm.get("jouees"), cm.get("reussies"), cm.get("ms_total"), cm.get("ms_max"), cm.get("plus_lente") or "-"), ""]
        par = cm.get("par_commande") or {}
        if par:
            o += ["| commande | fois | ms en moyenne | ms au plus |", "|---|---|---|---|"]
            for k, v in sorted(par.items(), key=lambda kv: -kv[1][2]):
                o.append("| /%s | %s | %.2f | %s |" % (k, v[0], v[1] / max(1, v[0]), v[2]))
            o.append("")
    # ---- pics (scénarios connexions et téléportations), par mesure
    noms_pics = {"extraction": "extraction de groupe (vrai chemin de HeraliZ Extraction)", "spawn": "/spawn de joueurs de partout",
                 "borne": "groupe qui part de la borne (fenêtre de 15 s)", "connexion": "une connexion"}
    for mm in res.get("mesures") or []:
        pics = mm.get("pics") or []
        if not pics:
            continue
        o += ["### Pics (%d%s)" % (len(pics), ", mesure %s" % mm["etiquette"] if len(res["mesures"]) > 1 else ""), "",
              "| cas | fois | bots (faits) | tick du déclenchement, ms (moyenne / pire) | **pire tick de la fenêtre, ms** | référence avant, ms |",
              "|---|---|---|---|---|---|"]
        for cas in ("extraction", "spawn", "borne", "connexion"):
            l = [p for p in pics if p.get("cas") == cas]
            if not l:
                continue
            o.append("| %s | %d | %s | %.1f / %.1f | **%.1f** | %.1f |" % (
                noms_pics[cas], len(l), "/".join("%s" % p.get("faits") + ("→%s" % p["arrives"] if "arrives" in p else "") for p in l)[:60],
                sum(p.get("tick_ms", 0) for p in l) / len(l), max(p.get("tick_ms", 0) for p in l),
                max(p.get("pire_ms", 0) for p in l), sum(p.get("avant_ms", 0) for p in l) / len(l)))
        o += ["", "Chaque pic : 20 ticks de référence, le déclenchement dans un tick (comme de vrais joueurs), puis 5 s (15 s "
              "pour la borne : compte à rebours, point cherché, arrivées ; « faits→arrivés ») mesurées tick par tick.", ""]
    if res.get("chauffe"):
        o += ["### Chauffe (une ligne par minute)", "", "| ms moyenne | 95 % | pire | zombies | tronçons | créés | bots |", "|---|---|---|---|---|---|---|"]
        for t in res["chauffe"]:
            o.append("| %s | %s | %s | %s | %s | %s | %s |" % (t.get("ms_moyenne"), t.get("ms_p95"), t.get("ms_max"), t.get("zombies_max"),
                                                           t.get("troncons_max"), t.get("troncons_neufs"), t.get("bots_max")))
        o.append("")
    o.append("« Can't keep up » : %d depuis le démarrage, %d pendant le démarrage." % (len(r_banc), len(r_demarrage)))
    o.append("")
    if "ligne_done" not in res:
        # démarrage raté : ce sont ses erreurs qui comptent
        e_banc, ex_banc = journal(lignes)[1:]
        o += ["### Erreurs Java pendant le démarrage (%d lignes, %d sortes)" % (sum(e_banc.values()), len(e_banc)), ""]
        with open(rapport / "erreurs.txt", "w", encoding="utf-8") as f:
            for e, n in e_banc.most_common():
                f.write("== %d fois : %s\n%s\n\n" % (n, e, "\n".join(ex_banc.get(e, []))))
    else:
        o += ["### Erreurs Java après le démarrage (%d lignes, %d sortes)" % (sum(e_banc.values()), len(e_banc)), ""]
    if e_banc:
        o += ["| fois | message |", "|---|---|"] + ["| %d | `%s` |" % (n, e.replace("|", "/").replace("`", "'")) for e, n in e_banc.most_common(25)]
        o += ["", "Piles complètes : `erreurs.txt` de l'artefact. Pendant le démarrage : %d lignes d'erreur (journal complet)." % sum(e_demarrage.values())]
    else:
        o.append("Aucune. Pendant le démarrage : %d lignes d'erreur (journal complet)." % sum(e_demarrage.values()))
    o.append("")
    o += ["### Rapports du diagnostic (diag/)", ""]
    if rapports:
        for f in rapports:
            o.append("- `%s` (%d Ko)" % (f.name, max(1, f.stat().st_size // 1024)))
        o.append("")
        for f in rapports:
            if f.name.startswith(("profil-", "mesure-", "banc-")):
                texte = f.read_text(encoding="utf-8", errors="replace").splitlines()
                o += ["<details><summary>%s</summary>" % f.name, "", "```"] + texte[:300] + (["… (suite dans l'artefact)"] if len(texte) > 300 else []) + ["```", "</details>", ""]
        blocages = [f for f in rapports if f.name.startswith("blocage-")]
        if blocages:
            o.append("Blocages pendant la mesure : %d (premières lignes) :" % len(blocages))
            o.append("```")
            for f in blocages[:10]:
                o += f.read_text(encoding="utf-8", errors="replace").splitlines()[:4] + [""]
            o.append("```")
    else:
        o.append("Aucun rapport écrit pendant la mesure.")
    if res.get("etat_fin"):
        o += ["", "<details><summary>État à la fin (zombies stats, diag etat, forge tps, bots etat)</summary>", "", "```"]
        o += res["etat_fin"] + ["```", "</details>"]
    o += ["", "### Étapes", ""] + ["- " + e for e in res["etapes"]]
    o += ["", "### Ce que ce banc ne mesure pas", "",
          "- Ce n'est pas la vraie carte : monde généré (graine fixe), sans conteneurs de loot, villes, décors ni spawn construit "
          "(le camp est une dalle sous le point de réveil).",
          "- Bots sans vrai jeu : le réseau est simulé (paquets mis en forme et compressés, pas envoyés) ; leurs « paquets » "
          "(déplacements, gestes, clic de réapparition) sont joués dans le tick ; leurs mouvements sont calculés par le serveur.",
          "- Les rythmes (morts, connexions, voyages, part du temps en combat) sont des estimations pour 150 joueurs, à recaler "
          "sur les journaux du Ptero.",
          "- La machine de GitHub n'est pas celle du Ptero : comparer des bancs entre eux (même nombre de bots, même durée), pas avec le Ptero.",
          "- Le profil du diagnostic (/diag start = /diag profil) relève la pile toutes les 5 ms, pendant et entre les ticks : environ 1 % du serveur pendant la mesure (coût mesuré en tête du rapport profil-<date>).", ""]
    texte = "\n".join(o)
    (rapport / "resume.md").write_text(texte, encoding="utf-8")
    sortie = {k: v for k, v in res.items() if k != "t0"}
    (rapport / "resultat.json").write_text(json.dumps(sortie, ensure_ascii=False, indent=1, default=str), encoding="utf-8")
    print("\n" + texte, flush=True)
    resume = os.environ.get("GITHUB_STEP_SUMMARY")
    if resume:
        with open(resume, "a", encoding="utf-8") as f:
            f.write(texte[:900_000] + "\n")
    # rapports complets dans le journal de la tâche
    for f in rapports:
        print("\n========== diag/%s ==========" % f.name, flush=True)
        print(f.read_text(encoding="utf-8", errors="replace"), flush=True)
    if res.get("echec") or not j:
        lignes_fin = lignes[-80:]
        print("\n========== fin du journal du serveur ==========\n" + "\n".join(lignes_fin), flush=True)
        return 1
    return 0


# ---------------------------------------------------------------------------------------------------------- main

def main():
    p = argparse.ArgumentParser(description="Banc de charge HeraliZ (voir l'aide en tête du fichier)")
    s = p.add_subparsers(dest="action", required=True)
    pr = s.add_parser("preparer")
    pr.add_argument("dossier")
    pr.add_argument("--plat", action="store_true", help="monde plat (création du terrain presque gratuite)")
    pr.add_argument("--graine", type=int, default=GRAINE_DEFAUT)
    pr.add_argument("--vue", type=int, default=VUE_PTERO, help="view-distance (Ptero du 01/11 : 6)")
    pr.add_argument("--simulation", type=int, default=SIMULATION_PTERO, help="simulation-distance (Ptero du 01/11 : 4)")
    la = s.add_parser("lancer")
    la.add_argument("dossier")
    la.add_argument("--bots", type=int, default=100)
    la.add_argument("--tuto", type=int, default=-1, help="bots envoyés dans le tutoriel des bases (défaut : 10 %%)")
    la.add_argument("--groupe", type=int, default=5, help="bots par groupe sur la carte")
    la.add_argument("--ecart", type=int, default=320, help="blocs entre deux groupes")
    la.add_argument("--rayon", type=int, default=48, help="rayon de jeu d'un groupe (blocs)")
    la.add_argument("--arrivee", type=int, default=150, help="rayon de la zone d'arrivée de la borne autour d'un groupe (blocs)")
    la.add_argument("--commandes", type=int, default=120, help="secondes entre deux commandes d'un bot (0 : aucune)")
    la.add_argument("--rythmes", default=RYTHMES_DEFAUT,
                    help="morts/connexions/voyages par minute pour 150 joueurs, puis %% du temps en combat ; off : comme avant")
    la.add_argument("--scenario", choices=("normal", "connexions", "teleportations"), default="normal")
    la.add_argument("--regles", default="", help="règles de jeu changées dans tous les mondes, séparées par ; (ex. « randomTickSpeed 0 »)")
    la.add_argument("--ab", action="store_true", help="avec --regles : une mesure sans, puis une mesure avec")
    la.add_argument("--chauffe", type=float, default=4, help="minutes de chauffe au moins")
    la.add_argument("--minutes", type=float, default=5, help="minutes de mesure (chacune, en A/B)")
    la.add_argument("--pregen-max", type=float, default=15, help="minutes au plus pour créer d'avance le terrain des zones (0 : rien)")
    la.add_argument("--diag", default=DIAG_DEFAUT, help="commandes du diagnostic, séparées par ; ({s}, {s300})")
    la.add_argument("--memoire", default="auto", help="tas de Java en Mo, ou auto")
    la.add_argument("--java", default="java", help="Java 17 à utiliser (défaut : celui du PATH)")
    la.add_argument("--rapport", default=None)
    la.add_argument("--attente-demarrage", type=float, default=20, help="minutes au plus pour « Done »")
    la.add_argument("--duree-max", type=float, default=50, help="minutes au plus pour tout le banc")
    a = p.parse_args()
    if a.action == "preparer":
        preparer(a)
    else:
        sys.exit(banc(a))


if __name__ == "__main__":
    main()
