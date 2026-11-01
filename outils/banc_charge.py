"""Banc de charge HeraliZ sur une machine à part (01/11) : un vrai serveur Mohist avec nos mods, des bots qui jouent,
une mesure, un rapport. Rien ne touche au Ptero ni au site. Sert au workflow « Banc de charge »
(.github/workflows/banc.yml) ; marche aussi sur un PC Linux ou Windows avec Java 17.

    python outils/banc_charge.py preparer <dossier> [--plat] [--graine N]
    python outils/banc_charge.py lancer <dossier> [--bots 100] [--chauffe 4] [--minutes 5] [--diag "..."]
                                        [--memoire auto|<Mo>] [--java <java 17>] [--rapport <dossier>] [--duree-max 50]

preparer : serveur d'essai fait par outils/serveur_local.py <dossier> --banc (dernière fabrication de livraison/), puis
    réglages d'essai DANS CE DOSSIER SEULEMENT (le dépôt n'est pas touché) : EULA du dépôt, server-ip=127.0.0.1, monde
    normal (ou plat avec --plat) à graine fixe, secrets vidés (Spartan sans jeton du launcher, LiteBans en base locale H2,
    lien Discord coupé), « Can't keep up » affichés, zombies du peuplement aussi autour des bots, commandes des bots
    (outils/banc/commandes.txt). Le jar de Mohist (mohist-*.jar) est à poser dans le dossier (Release « base-serveur »).
lancer : démarre Mohist (mémoire réglée sur la machine, réglages Aikar, journal du ramasse-miettes, AuthMe en mode essai
    -Dheraliz.essai.sansAuthme=true avec server-ip=127.0.0.1), attend « Done », puis par la console :
      1. comme le Ptero : doMobSpawning false (les zombies viennent du peuplement) ;
      2. bots : 10 % dans le tutoriel des bases (/tuto lancer), le reste par groupes de 5 répartis sur une grille de la
         carte (ils courent, tirent, se soignent, lancent grenades et fumigènes, bâtissent des bases de CRac, appellent
         frappes et largages, fouillent), zones d'extraction ouvertes près de certains groupes, commandes de joueur tapées
         par les bots (/bots commandes) ;
      3. chauffe : au moins --chauffe minutes, et tant que du terrain se crée encore ou que les arènes du ClashWar se
         posent (premier démarrage d'un monde neuf), au plus 2 fois --chauffe ;
      4. mesure : /bots mesure (chaque tick, mémoire, entités, zombies, tronçons) et le diagnostic du noyau (--diag,
         « diag start {s}; diag mesure {s300} » par défaut : {s} = secondes de la mesure, {s300} = les mêmes, 300 au plus)
         pendant --minutes ;
      5. arrêt propre (bots retirés, « stop »), puis rapport : resume.md (aussi dans le résumé de la tâche GitHub),
         rapports du diagnostic (diag/), journal complet, journal du ramasse-miettes, erreurs Java, « Can't keep up ».
Code de sortie 1 si le serveur n'a pas démarré ou si la mesure n'a pas eu lieu.
"""
import argparse
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
# Aikar (docs/DEPLOIEMENT.md), -Xms = -Xmx
AIKAR = ["-XX:+UseG1GC", "-XX:+ParallelRefProcEnabled", "-XX:MaxGCPauseMillis=200", "-XX:+UnlockExperimentalVMOptions",
         "-XX:+DisableExplicitGC", "-XX:+AlwaysPreTouch", "-XX:G1NewSizePercent=30", "-XX:G1MaxNewSizePercent=40",
         "-XX:G1HeapRegionSize=8M", "-XX:G1ReservePercent=20", "-XX:G1HeapWastePercent=5", "-XX:G1MixedGCCountTarget=4",
         "-XX:InitiatingHeapOccupancyPercent=15", "-XX:G1MixedGCLiveThresholdPercent=90",
         "-XX:G1RSetUpdatingPauseTimePercent=5", "-XX:SurvivorRatio=32", "-XX:+PerfDisableSharedMem",
         "-XX:MaxTenuringThreshold=1"]


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
    # personne d'autre ne peut se connecter (et c'est la condition du mode essai d'AuthMe : PontBukkit.essaiLocal)
    proprietes(sp, {"server-ip": "127.0.0.1", "max-players": "250", "level-seed": str(a.graine),
                    "level-type": "minecraft\\:flat" if a.plat else "minecraft\\:normal", "enable-rcon": "false",
                    "enable-query": "false", "motd": "HeraliZ banc de charge", "white-list": "false"})
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
    (d / "banc.json").write_text(json.dumps({"monde": monde, "plat": bool(a.plat), "graine": a.graine}), encoding="utf-8")
    dire("serveur d'essai prêt dans %s (monde %s %s, graine %s)" % (d, monde, "plat" if a.plat else "normal", a.graine))


# ---------------------------------------------------------------------------------------------------------- serveur

class Serveur:
    """Le serveur Mohist : console (entrée), journal (sortie) lu par un fil, attentes sur un motif."""

    IMPORTANT = re.compile(r"\]: Done \(|Can't keep up|\[banc\]|\[bots\]|\[Diag\]|\[diag\]|\[zombies\] peuplement|Stopping|"
                           r"[/ ](ERROR|FATAL)\]|Exception|Caused by|Mohist|Loading libraries|Download|Prepar|\[matchs\] (arenes|instance)")

    def __init__(self, cmd, dossier, journal):
        self.cmd, self.dossier = cmd, dossier
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
        self.debut = time.time()
        self.proc = subprocess.Popen(self.cmd, cwd=self.dossier, stdin=subprocess.PIPE, stdout=subprocess.PIPE,
                                     stderr=subprocess.STDOUT)
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

    def depuis(self, i):
        with self.verrou:
            return list(self.lignes[i:])

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


def mesure_json(serveur, depuis, limite):
    m = serveur.attendre(r"\[banc\] MESURE (\{.*\})\s*$", depuis, limite)
    if not m:
        return None
    try:
        return json.loads(m.group(1))
    except ValueError:
        return None


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


def nouveaux_rapports(diag, apres):
    if not diag.is_dir():
        return []
    return sorted(f for f in diag.glob("*.txt") if f.stat().st_mtime >= apres)


def banc(a):
    d = Path(a.dossier).resolve()
    jars = sorted(d.glob("mohist-*.jar"))
    if not jars:
        sys.exit("jar de Mohist absent de %s (Release « base-serveur », voir serveur/mohist.sha256)" % d)
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
    info = {"bots": bots, "tuto": tuto, "groupes": len(groupes), "chauffe_min": a.chauffe, "secondes": secondes,
            "tas_mo": tas, "coeurs": os.cpu_count(), "diag": a.diag}
    try:
        info.update(json.loads((d / "banc.json").read_text(encoding="utf-8")))
    except (OSError, ValueError):
        pass
    cmd = [a.java, "-Xms%dM" % tas, "-Xmx%dM" % tas] + AIKAR + [
        "-Xlog:gc:file=%s:uptime" % gc_log.relative_to(d).as_posix(),
        "-Dheraliz.essai.sansAuthme=true", "-Dfile.encoding=UTF-8", "-jar", jars[-1].name, "nogui"]
    fin_max = time.time() + a.duree_max * 60
    res = {"info": info, "etapes": []}
    serveur = None
    debut_mesure = None

    def etape(t):
        res["etapes"].append("%s (%.0f s)" % (t, time.time() - t0))
        dire(t)

    t0 = time.time()

    def arret_demande(*_):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, arret_demande)
    try:
        # Mohist installe ses bibliothèques au premier lancement : s'il s'arrête avant « Done », on le relance une fois
        for essai in (1, 2):
            serveur = Serveur(cmd, d, rapport / "console.log")
            serveur.demarrer()
            m = serveur.attendre(r"\]: Done \(([\d.,]+)s\)", 0, min(a.attente_demarrage * 60, fin_max - time.time()))
            if m:
                break
            if serveur.vivant() or essai == 2:
                serveur.arreter(60)
                res["echec"] = "le serveur n'a pas fini de démarrer (« Done » jamais vu)"
                return finir(a, d, rapport, res, serveur, None, gc_log)
            dire("Mohist s'est arrêté avant « Done » (code %s) : nouvel essai" % serveur.proc.returncode)
        res["demarrage_s"] = round(time.time() - serveur.debut)
        res["ligne_done"] = len(serveur.lignes)
        etape("serveur démarré en %s s" % res["demarrage_s"])

        c = serveur.commande
        c("gamerule doMobSpawning false")   # comme le Ptero : les zombies viennent seulement du peuplement
        c("bots joueurs false")
        c("bots action reapparition true")
        # 1. bots du tutoriel lancés d'abord (ils prennent les noms HzBot001…), envoyés au tutoriel plus bas
        x0, z0 = (groupes[0][0], groupes[0][1]) if groupes else (0, 0)
        t_tuto = time.time()
        if tuto:
            c("execute in minecraft:overworld positioned %d 80 %d run bots lancer %d 24" % (x0, z0, tuto))
            time.sleep(2.5)
        # 2. groupes sur la carte, un par un (chaque bot posé crée ou charge son tronçon sur le fil du serveur)
        for (x, z, n) in groupes:
            c("execute in minecraft:overworld positioned %d 80 %d run bots lancer %d %d" % (x, z, n, a.rayon))
            time.sleep(2.5)
        # /tuto lancer seulement quand les bots sont posés sur la carte (3 s après leur arrivée, plus si le serveur rame) :
        # sinon leur départ les ramènerait sur la carte
        if tuto:
            time.sleep(max(0.0, t_tuto + 30 - time.time()))
            for i in range(1, tuto + 1):
                c("tuto lancer HzBot%03d" % i)
                time.sleep(0.5)
        etape("%d bots lancés (%d dans le tutoriel, %d groupes sur la carte)" % (bots, tuto, len(groupes)))
        time.sleep(10)
        # 3. zones d'extraction ouvertes près d'un groupe sur quatre (au sol), commandes des bots
        for k, (x, z, _) in enumerate(groupes[::4]):
            nom = "banc%d" % (k + 1)
            c("execute in minecraft:overworld positioned %d 0 %d positioned over motion_blocking_no_leaves run extraction create %s 8 30"
              % (x + 12, z + 12, nom))
            c("extraction mode %s permanent" % nom)
        c("bots commandes %d" % a.commandes)
        # 4. chauffe : au moins --chauffe minutes, puis tant que du terrain se crée (mesures d'une minute)
        chauffe_fin = time.time() + a.chauffe * 60
        chauffe_max = time.time() + a.chauffe * 120
        tours = []
        while time.time() < chauffe_max and serveur.vivant() and time.time() < fin_max - secondes - 300:
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
            return finir(a, d, rapport, res, serveur, None, gc_log)
        # 5. mesure + diagnostic du noyau (rapports de la chauffe laissés de côté : écrits avant debut_mesure)
        time.sleep(2)
        res["arenes"] = arenes_posees(serveur)
        debut_mesure = time.time()
        i = serveur.nb()
        res["ligne_mesure"] = i
        c("bots mesure %d" % secondes)
        for x in [x.strip() for x in a.diag.split(";")]:
            if x:
                try:
                    x = x.format(s=secondes, s300=min(300, secondes))
                except (KeyError, IndexError, ValueError):
                    pass
                c(x)
        etape("mesure lancée pour %d s" % secondes)
        j = mesure_json(serveur, i, secondes + 180)
        res["mesure"] = j
        res["ligne_fin_mesure"] = serveur.nb()
        if not j:
            res["echec"] = "pas de résultat de /bots mesure"
        etape("mesure finie")
        # rapports du diagnostic : écrits à la fin de leur durée (fil d'écriture du noyau)
        attente = time.time() + 90
        vus = -1
        while time.time() < attente and serveur.vivant():
            n = len(nouveaux_rapports(d / "diag", debut_mesure))
            if n == vus and n > 0:
                break
            vus = n
            time.sleep(10)
        # état à la fin, bots encore là (réponses dans le journal, gardées pour le résumé)
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
    return finir(a, d, rapport, res, serveur, debut_mesure, gc_log)


# ---------------------------------------------------------------------------------------------------------- rapport

RX_RETARD = re.compile(r"Can't keep up! Is the server overloaded\? Running (\d+)ms or (\d+) ticks behind")
RX_ERREUR = re.compile(r"[/ ](ERROR|FATAL)\]")
RX_EXCEPTION = re.compile(r"((?:[a-zA-Z_$][\w$]*\.)+[A-Z][\w$]*(?:Exception|Error))(?::\s*(.*))?")
RX_PAUSE = re.compile(r"^\[([\d.,]+)s\].*Pause (\w+).*?([\d.,]+)ms\s*$")


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


def gc_pauses(fichier, debut_s=None, fin_s=None):
    pauses = []
    if not fichier.exists():
        return pauses
    for l in fichier.read_text(encoding="utf-8", errors="replace").splitlines():
        m = RX_PAUSE.match(l)
        if m:
            t = float(m.group(1).replace(",", "."))
            if (debut_s is None or t >= debut_s) and (fin_s is None or t <= fin_s):
                pauses.append((t, m.group(2), float(m.group(3).replace(",", "."))))
    return pauses


def ok(b):
    return "atteint" if b else "**PAS atteint**"


def finir(a, d, rapport, res, serveur, debut_mesure, gc_log):
    lignes = serveur.depuis(0) if serveur else []
    i_done, i_mes, i_fin = res.get("ligne_done", len(lignes)), res.get("ligne_mesure"), res.get("ligne_fin_mesure")
    pendant = lignes[i_mes:i_fin] if i_mes is not None else []
    apres_done = lignes[i_done:]
    r_demarrage, e_demarrage, _ = journal(lignes[:i_done])
    r_banc, e_banc, ex_banc = journal(apres_done)
    r_mesure, _, _ = journal(pendant)
    # journal du ramasse-miettes (secondes depuis le lancement de Java, à peu près celui du processus)
    deb = (debut_mesure - serveur.debut) if (serveur and debut_mesure) else None
    j = res.get("mesure") or {}
    fin = (deb + j.get("secondes", 0)) if deb is not None else None
    pauses = gc_pauses(gc_log, deb, fin) if deb is not None else []
    if gc_log.exists():
        shutil.copy2(gc_log, rapport / "gc.log")
    diag = d / "diag"
    rapports = nouveaux_rapports(diag, debut_mesure) if debut_mesure else []
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
    o = ["## Banc de charge HeraliZ — %s bots, %s min de mesure" % (info["bots"], round(info["secondes"] / 60, 1)), ""]
    o.append("Machine : %s cœurs, Java %s Mo (-Xms = -Xmx) · monde %s %s, graine %s · %d bots dans le tutoriel, %d groupes sur la carte"
             % (info.get("coeurs"), info.get("tas_mo"), info.get("monde", "?"), "plat" if info.get("plat") else "normal",
                info.get("graine"), info["tuto"], info["groupes"]))
    if os.environ.get("GITHUB_SHA"):
        o.append("Commit : `%s` (branche `%s`)" % (os.environ["GITHUB_SHA"][:10], os.environ.get("GITHUB_REF_NAME", "?")))
    o.append("")
    if res.get("echec"):
        o += ["**Échec : %s.** Voir le journal complet (artefact, console.log)." % res["echec"], ""]
    ar = res.get("arenes") or (0, 0)
    if ar[0] < ar[1]:
        o += ["Attention : arènes du ClashWar pas encore toutes posées au début de la mesure (%d sur %d, monde neuf) : leur pose "
              "(6 ms par tick au plus) compte dans la mesure." % tuple(ar), ""]
    if j:
        o += ["### Ticks pendant la mesure (%s ticks, %s s)" % (j.get("ticks"), j.get("secondes")), "",
              "| ms par tick | moyenne | médiane | 95 % | 99 % | pire |", "|---|---|---|---|---|---|",
              "| | %s | %s | %s | %s | %s |" % (j.get("ms_moyenne"), j.get("ms_mediane"), j.get("ms_p95"), j.get("ms_p99"), j.get("ms_max")), "",
              "- **TPS réel : %s** (ticks faits / secondes) ; ticks de plus de 50 ms : %s, de plus de 100 ms : %s ; plus long écart "
              "entre deux débuts de tick : %s ms" % (j.get("tps_reel"), j.get("ticks_plus_50ms"), j.get("ticks_plus_100ms"), j.get("ecart_max_ms")),
              "- Objectifs : moyenne ≤ 30 ms %s, 95 %% ≤ 40 ms %s, 99 %% < 50 ms %s" % (
                  ok(j.get("ms_moyenne", 99) <= 30), ok(j.get("ms_p95", 99) <= 40), ok(j.get("ms_p99", 99) < 50)),
              "- « Can't keep up » : %d pendant la mesure (pire retard %s ms), %d depuis le démarrage, %d pendant le démarrage" % (
                  len(r_mesure), max([x[0] for x in r_mesure], default=0), len(r_banc), len(r_demarrage)), ""]
        o += ["### Machine", "",
              "- Mémoire : %s Mo au plus, %s Mo en moyenne ; gardée après le ramasse-miettes : %s Mo au plus (sur %s Mo)" % (
                  j.get("memoire_max_mo"), j.get("memoire_moyenne_mo"), j.get("memoire_apres_gc_max_mo"), j.get("memoire_java_mo")),
              "- Ramasse-miettes : %s passages, %s ms en tout%s" % (j.get("gc_nombre"), j.get("gc_ms"),
                  (" ; pauses : %d, pire %.0f ms, %d de plus de 50 ms" % (len(pauses), max(p[2] for p in pauses), sum(1 for p in pauses if p[2] > 50)))
                  if pauses else ""),
              "- Processeur (Java, %s cœurs = 100 %%) : %s %% en moyenne, %s %% au plus" % (j.get("coeurs"), j.get("cpu_moyen_pct"), j.get("cpu_max_pct")), ""]
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
    if res.get("chauffe"):
        o += ["### Chauffe (une ligne par minute)", "", "| ms moyenne | 95 % | pire | zombies | tronçons | créés | bots |", "|---|---|---|---|---|---|---|"]
        for t in res["chauffe"]:
            o.append("| %s | %s | %s | %s | %s | %s | %s |" % (t.get("ms_moyenne"), t.get("ms_p95"), t.get("ms_max"), t.get("zombies_max"),
                                                           t.get("troncons_max"), t.get("troncons_neufs"), t.get("bots_max")))
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
          "- Ce n'est pas la vraie carte : monde généré (graine fixe), sans conteneurs de loot, villes, décors ni spawn construit.",
          "- Bots sans vrai jeu : le réseau est simulé (paquets mis en forme et compressés, pas envoyés), aucun paquet des joueurs vers le serveur.",
          "- La machine de GitHub n'est pas celle du Ptero : comparer des bancs entre eux (même nombre de bots, même durée), pas avec le Ptero.",
          "- Le profil du diagnostic (/diag start) relève la pile toutes les 10 ms : il coûte un peu pendant la mesure.", ""]
    texte = "\n".join(o)
    (rapport / "resume.md").write_text(texte, encoding="utf-8")
    (rapport / "resultat.json").write_text(json.dumps(res, ensure_ascii=False, indent=1), encoding="utf-8")
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
    la = s.add_parser("lancer")
    la.add_argument("dossier")
    la.add_argument("--bots", type=int, default=100)
    la.add_argument("--tuto", type=int, default=-1, help="bots envoyés dans le tutoriel des bases (défaut : 10 %%)")
    la.add_argument("--groupe", type=int, default=5, help="bots par groupe sur la carte")
    la.add_argument("--ecart", type=int, default=320, help="blocs entre deux groupes")
    la.add_argument("--rayon", type=int, default=48, help="rayon de jeu d'un groupe (blocs)")
    la.add_argument("--commandes", type=int, default=120, help="secondes entre deux commandes d'un bot (0 : aucune)")
    la.add_argument("--chauffe", type=float, default=4, help="minutes de chauffe au moins")
    la.add_argument("--minutes", type=float, default=5, help="minutes de mesure")
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
