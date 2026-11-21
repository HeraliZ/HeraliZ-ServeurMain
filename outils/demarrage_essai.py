"""Démarrage d'essai avant la mise en ligne (audit du 19/11, point F10).

Le 18/11, deux démarrages du Ptero se sont arrêtés net (injection de mixin du noyau introuvable dans les noms du jeu en
production). Ici, le serveur fabriqué (exactement les jars de livraison/mods serveur, les plugins et les réglages du
dépôt) démarre sur une machine de GitHub avec le Mohist de la Release « base-serveur » : la mise en ligne (« Serveur
HeraliZ », job « demarrage » : .github/workflows/demarrage.yml) n'envoie RIEN au Ptero si ce démarrage échoue. Aucun
secret, monde plat neuf, jeté après. Marche aussi sur un PC avec Java 17 (après python outils/construire.py).

    python outils/demarrage_essai.py <dossier> [--attente 12] [--apres 20] [--memoire 6144] [--java java]
                                     [--rapport <dossier>] [--mixins-stricts]

1. serveur d'essai fait par outils/serveur_local.py <dossier> (SANS le banc : les jars du Ptero) ; le jar de Mohist
   (mohist-*.jar, Release « base-serveur ») est à poser dans le dossier ;
2. réglages d'essai DANS CE DOSSIER SEULEMENT : EULA du dépôt, server-ip=127.0.0.1 et port 25570 (personne ne peut s'y
   connecter), monde plat à graine fixe, secrets vidés (Spartan sans jeton du launcher, LiteBans en base locale, lien
   Discord coupé), pas de recherche de mise à jour ; tout le reste (mods, plugins, réglages, bukkit.yml, spigot.yml,
   server.properties) vient du dépôt ;
3. démarrage : au premier lancement, Mohist installe Forge puis se relance (attendu jusqu'à « Done », un nouvel essai
   si l'installation l'arrête avant) ; puis --apres secondes à vide, puis « stop ».
Refus (code 1) : « Done » jamais vu en --attente minutes, serveur arrêté avant « Done » ou pendant les secondes à vide,
ou une ligne fatale, même après « Done » (mixin qui ne s'applique pas, chargement des mods raté, plantage). Seulement
signalé : les lignes d'erreur habituelles des mods, les avertissements (réglage hors bornes remis à sa valeur par
défaut, réglage disparu...), un arrêt lent ou forcé. Résumé dans le résumé de la tâche GitHub ($GITHUB_STEP_SUMMARY) ;
chaque sorte d'erreur et d'avertissement avec sa première ligne dans le journal de la tâche ; journal complet et
resume.md dans --rapport.

--mixins-stricts (diagnostic, audit du 21/11 ; jamais pour une mise en ligne ; « Run workflow » avec la case
« mixins_stricts ») : toute injection de mixin qui ne trouve pas sa cible devient une erreur, même celles marquées
« require 0 » qui ratent sans rien dire d'habitude (187 dans nos mods, plus celles des autres auteurs). Options de Java du
serveur d'essai seulement (OPTIONS_STRICTES) :
  -Dmixin.debug.countInjections=true : une injection qui ne trouve rien (ou moins que « expect ») est une erreur ;
  -Dmixin.env.ignoreRequired=true : chaque erreur est notée (« Mixin apply failed ... ») et le démarrage continue ; sans
     elle, la première erreur d'une configuration « required » arrête tout (essais du 21/11 avec « -Dmixin.ignoreRequired »,
     qui n'est pas le nom de l'option : Mixin 0.8.5 lit mixin.env.ignoreRequired) ;
  -Dmodernfix.auditMixinsAtStart=true : à la fin du chargement des mods, ModernFix fait charger chaque classe visée par un
     mixin qui ne l'est pas encore (MixinEnvironment.audit) : les injections des classes chargées seulement plus tard en jeu
     sont essayées aussi ; une classe chargée trop tôt pour ses mixins est signalée (« Could not force-load »).
Après le démarrage, rapport_strict() liste chaque injection ratée (les nôtres d'abord : jars qui ne viennent pas de
serveur/mods-tiers), les classes visées jamais transformées, et ceux de nos mixins jamais appliqués (logs/debug.log :
« Mixing X from config into cible »). Un refus est normal dans ce mode : il ne dit rien de la mise en ligne.
Injections d'autres auteurs qui ratent toujours sur Mohist, sans effet sur le serveur en ligne (elles y ratent déjà sans
rien dire) : coupées dans la copie d'essai de config/modernfix-mixins.properties seulement (MODERNFIX_COUPES), jamais dans
serveur/config :
  - perf.reduce_blockstate_cache_rebuilds (ModernFix, essai n° 20 du 21/11) ;
  - bugfix.forge_vehicle_packets (ModernFix, essai n° 24 du 21/11 : la redirection de movePlayerUsingPositionRider dans
    ServerGamePacketListenerImpl ne trouve pas son appel, que Mohist réécrit).
Résultat attendu (essai n° 28 du 21/11, rapport docs/audit_global_21nov/validation/v10_dependances_mixins.md) : nos 111
mixins du serveur (plus 4 ajoutés par le plugin de Spartan) tous essayés, une seule injection ratée, voulue :
heraliz_noyau_allege ReglagesGenerationMixin.heraliz$encoderUneFoisDimensions (2e forme de WorldGenSettings.encode, celle
« dimensions » que Bukkit pourrait appeler ; la forme de Minecraft, utilisée par Mohist 916, s'applique), et sa classe
PrimaryLevelData signalée une 2e fois par l'audit (suite de l'injection ratée). Tout autre échec est nouveau.
"""
import argparse
import io
import json
import os
import re
import shutil
import signal
import subprocess
import sys
import time
import zipfile
from collections import Counter
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import banc_charge  # noqa: E402  (serveur Mohist, réglages d'essai, installation de Mohist)

RACINE = banc_charge.RACINE
SRV = banc_charge.SRV
GRAINE = 20261119
PORT = 25570
# lignes qui refusent la mise en ligne, même quand « Done » arrive (une injection de mixin ratée laisse parfois démarrer
# le serveur sans la règle qu'elle portait)
FATALES = re.compile(
    r"Mixin (?:apply|prepare)(?: for mod \S+)? failed|MixinApplyError|MixinPrepareError|MixinTransformerError|"
    r"Critical injection failure|InvalidInjectionException|InvalidMixinException|"
    r"Missing or unsupported mandatory dependencies|Failed to create mod instance|LoadingFailedException|"
    r"ModLoadingException|Encountered an unexpected exception|Exception in server tick loop|"
    r"---- Minecraft Crash Report ----|Failed to start the minecraft server|FAILED TO BIND TO PORT")
ERREUR = re.compile(r"[/ ](ERROR|FATAL)\]")
# avertissements (audit du 21/11) : réglage hors bornes remis à sa valeur par défaut par Forge (« Incorrect key ... was
# corrected »), réglage qui n'existe plus, mixin d'un autre mod sans sa cible... ; comptés comme les erreurs
AVERT = re.compile(r"[/ ]WARN\]")
DONE = r"\]: Done \(([\d.,]+)s\)"

# --mixins-stricts seulement (diagnostic, serveur d'essai ; voir l'aide en tête du fichier)
OPTIONS_STRICTES = ("-Dmixin.debug.countInjections=true", "-Dmixin.env.ignoreRequired=true",
                    "-Dmodernfix.auditMixinsAtStart=true")
# options de ModernFix coupées dans la copie d'essai seulement : injections qui ratent toujours sur Mohist (sans effet sur
# le serveur en ligne, où elles ratent déjà sans rien dire) ; une par essai strict qui les a montrées (voir l'aide)
MODERNFIX_COUPES = ("perf.reduce_blockstate_cache_rebuilds", "bugfix.forge_vehicle_packets")
# lignes de Mixin lues par rapport_strict()
ECHEC_MIXIN = re.compile(r"Mixin (apply|prepare)(?: for mod \S+)? failed (.+?): ((?:[\w$]+\.)+[\w$]*(?:Exception|Error)) ?(.*)$")
FORCE_CHARGE = re.compile(r"Force-loading class (\S+)")
FORCE_RATE = re.compile(r"Could not force-load (\S+)")
CIBLE_ABSENTE = re.compile(r"@Mixin target (\S+) was not found (\S+?\.json):(\S+)")
MIXING = re.compile(r"Mixing (\S+) from (\S+) into (\S+)")


def dire(*a):
    banc_charge.ecrire("[essai] " + " ".join(str(x) for x in a))


class ServeurEssai(banc_charge.Serveur):
    """Le serveur du banc, sans ses commandes de bots : rien avant « stop », journal préfixé [essai]."""
    PREFIXE = "[essai]"
    AVANT_ARRET = ()
    IMPORTANT = re.compile(banc_charge.Serveur.IMPORTANT.pattern + r"|Mixin (?:apply|prepare)|FAILED TO BIND")


def preparer(d, graine):
    """Serveur d'essai dans d : jars, plugins et réglages du dépôt, puis réglages d'essai dans d seulement. Rend le monde."""
    r = subprocess.run([sys.executable, str(RACINE / "outils" / "serveur_local.py"), str(d)])
    if r.returncode != 0:
        sys.exit("serveur_local.py a échoué (fabrication faite ? python outils/construire.py)")
    if list((d / "mods").glob("heraliz_banc-*.jar")):
        sys.exit("module banc dans %s/mods : le démarrage d'essai prend les jars du Ptero seulement (dossier neuf)" % d)
    shutil.copy2(SRV / "eula.txt", d / "eula.txt")   # EULA de Mojang acceptée par le propriétaire (fichier du dépôt)
    sp = d / "server.properties"
    props = banc_charge.lire_proprietes(sp)
    monde = props.get("level-name") or "dayz_map"
    # personne d'autre ne peut s'y connecter ; port à part (un serveur de test déjà lancé sur ce PC garde le sien)
    banc_charge.proprietes(sp, {"server-ip": "127.0.0.1", "server-port": str(PORT), "query.port": str(PORT),
                                "level-seed": str(graine), "level-type": "minecraft\\:flat", "enable-rcon": "false"})
    # secrets de la mise en ligne : vides ici (rien n'est envoyé nulle part), comme le banc de charge
    banc_charge.remplacer(d / "config" / "spartan-common.toml",
                          [(r"__[A-Z_]+__", ""), (r"^(\s*tokenRequired\s*=\s*)\S+", r"\g<1>false"),
                           (r"^(\s*captureRequired\s*=\s*)\S+", r"\g<1>false")])
    banc_charge.remplacer(d / "plugins" / "LiteBans" / "config.yml",
                          [(r"^(\s*driver\s*:\s*).+$", r"\g<1>H2"), (r"__[A-Z_]+__", "")])
    banc_charge.remplacer(d / "config" / "heraliz_grades" / "lien.json", [(r"__[A-Z_]+__", "")], obligatoire=False)
    banc_charge.remplacer(d / "mohist-config" / "mohist.yml", [(r"^(\s*check_update:\s*)\S+", r"\g<1>false")],
                          obligatoire=False)
    # réglages du monde : ceux du dépôt, recopiés dans le serverconfig du monde comme la mise en ligne le fait
    sc = d / monde / "serverconfig"
    sc.mkdir(parents=True, exist_ok=True)
    for f in (d / "defaultconfigs").glob("*"):
        if f.is_file():
            shutil.copy2(f, sc / f.name)
    dire("serveur d'essai prêt dans %s (monde %s plat neuf, graine %s, 127.0.0.1:%d)" % (d, monde, graine, PORT))
    return monde


def essentiel(l):
    """Une ligne du journal sans l'heure, les nombres ni les chemins (pour compter les erreurs par sorte)."""
    l = re.sub(r"^\[[^\]]*\]\s*(\[[^\]]*\]\s*)*:?\s*", "", l)
    l = re.sub(r"\d+", "N", l)
    return l[:160]


def lancer(a, d):
    jars = sorted(d.glob("mohist-*.jar"))
    if not jars:
        sys.exit("jar de Mohist absent de %s (Release « base-serveur », voir serveur/mohist.sha256)" % d)
    jar = jars[-1]
    rapport = Path(a.rapport).resolve() if a.rapport else d / "rapport_demarrage"
    rapport.mkdir(parents=True, exist_ok=True)
    journal = rapport / "console.log"
    journal.unlink(missing_ok=True)
    tas = int(a.memoire) if a.memoire else min(6144, banc_charge.memoire_auto())
    env = banc_charge.environnement(a.java)
    # reprises par toute machine Java lancée par « java » : celle que Mohist relance après son installation comprise
    env["JDK_JAVA_OPTIONS"] = "-Xmx%dM -Dfile.encoding=UTF-8" % tas
    if a.mixins_stricts:
        env["JDK_JAVA_OPTIONS"] += " " + " ".join(OPTIONS_STRICTES)
        # copie d'essai seulement (serveur_local.py la recopie depuis serveur/config à chaque préparation)
        mf = d / "config" / "modernfix-mixins.properties"
        if mf.exists():
            with open(mf, "a", encoding="utf-8") as f:
                f.write("\n" + "".join("mixin.%s=false\n" % o for o in MODERNFIX_COUPES))
    res = {"jar": jar.name, "tas": tas, "echec": None, "essais": 0, "stricts": a.mixins_stricts}
    t0 = time.time()
    fin_max = t0 + a.attente * 60
    serveur = None

    def arret_demande(*_):
        raise KeyboardInterrupt

    signal.signal(signal.SIGTERM, arret_demande)
    try:
        m = None
        for essai in (1, 2):
            res["essais"] = essai
            relance = banc_charge.installation_prevue(d, jar)
            # installation de Mohist à faire : la première machine Java reste petite, le serveur tourne dans celle qu'il
            # relance (réglages de JDK_JAVA_OPTIONS)
            cmd = [a.java] + (banc_charge.PARENT_LEGER if relance else []) + ["-jar", jar.name, "nogui"]
            serveur = ServeurEssai(cmd, d, journal, env)
            serveur.demarrer()
            m = serveur.attendre(DONE, 0, max(10.0, fin_max - time.time()))
            if m:
                break
            if not serveur.fini:
                # journal toujours ouvert : Java tourne encore, sans « Done » dans le temps donné
                res["echec"] = "« Done » jamais vu en %g min (serveur bloqué au démarrage ?)" % a.attente
                break
            # journal fermé : Java s'arrête (son code de sortie arrive juste après la fin du journal)
            try:
                serveur.proc.wait(15)
            except subprocess.TimeoutExpired:
                pass
            fatale = any(FATALES.search(l) for l in serveur.depuis(0))
            if essai == 1 and relance and not fatale and time.time() < fin_max - 60:
                dire("Mohist s'est arrêté avant « Done » (code %s) pendant son installation : nouvel essai"
                     % serveur.proc.returncode)
                continue
            res["echec"] = "serveur arrêté avant « Done » (code de sortie %s)" % serveur.proc.returncode
            break
        if m:
            res["done_s"] = round(time.time() - serveur.debut)
            res["done_mc_s"] = m.group(1)
            dire("« Done » en %s s depuis le lancement (%s s pour Minecraft) : %d s à vide" % (res["done_s"], m.group(1), a.apres))
            fin = time.time() + a.apres
            while time.time() < fin and serveur.vivant():
                time.sleep(1)
            if not serveur.vivant():
                res["echec"] = "serveur arrêté tout seul %d s après « Done » (code de sortie %s)" % (
                    round(time.time() - serveur.debut) - res["done_s"], serveur.proc.returncode)
    except KeyboardInterrupt:
        res["echec"] = "démarrage d'essai arrêté avant la fin (annulé)"
    finally:
        t = time.time()
        res["code_sortie"] = serveur.arreter(120) if serveur else None
        res["arret_s"] = round(time.time() - t)
    return finir(a, res, serveur, rapport, d)


def reglages_corriges(d):
    """Réglages remis en ordre par Forge, clé par clé (logs/debug.log : Forge n'écrit ce détail qu'en DEBUG ; la console ne
    dit que « Configuration file ... is not correct »). Rend [(fichier, [lignes « clé : avant -> après »], nombre de
    commentaires remis)] : un fichier peut n'être « corrigé » que pour ses commentaires (nos longues notes, que Forge
    remplace par celles du code ; les valeurs ne changent pas)."""
    f = d / "logs" / "debug.log"
    if not f.exists():
        return None
    res, courant = [], None
    for l in f.read_text(encoding="utf-8", errors="replace").splitlines():
        m = re.search(r"Configuration file (.+?) is not correct\. Correcting", l)
        if m:
            nom = re.sub(r"^.*?[/\\](config|serverconfig)[/\\]", r"\1/", m.group(1))
            courant = [nom, [], 0]
            res.append(courant)
            continue
        if courant is None:
            continue
        m = re.search(r"Incorrect key (\S+) was corrected from (.*?) to its default, (.*?)\. ?(This seems to be an error\.)?\s*$", l)
        if m:
            courant[1].append("%s : %s -> %s" % (m.group(1), m.group(2)[:120], m.group(3)[:120]))
        elif re.search(r"The comment on key \S+ does not match the spec", l):
            courant[2] += 1
    return res


def configs_mixins(d):
    """Mode strict : configs Mixin des jars du serveur d'essai, lues dans leur manifeste (MixinConfigs). Rend (nous,
    autres) : nous = {config: (jar, [mixins listés pour un serveur : « mixins » et « server »])} pour nos jars (ceux qui ne
    viennent pas de serveur/mods-tiers), autres = {config: jar} pour les autres auteurs (jars imbriqués compris, même dans
    un des nôtres : Emotecraft dans celui des emotes)."""
    tiers = {j.name for j in (SRV / "mods-tiers").glob("*.jar")}
    nous, autres = {}, {}

    def lire(z, nom_jar, a_nous):
        noms = set(z.namelist())
        mf = z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace") if "META-INF/MANIFEST.MF" in noms else ""
        mf = re.sub(r"\r?\n ", "", mf)   # lignes de plus de 72 octets coupées par le format du manifeste
        m = re.search(r"^MixinConfigs:\s*(.*?)\s*$", mf, re.M)
        for c in [x.strip() for x in (m.group(1).split(",") if m else [])]:
            if not c:
                continue
            if not a_nous:
                autres[c] = nom_jar
                continue
            try:
                cfg = json.loads(z.read(c).decode("utf-8"))
            except (KeyError, ValueError):
                cfg = {}
            nous[c] = (nom_jar, sorted(set(list(cfg.get("mixins") or []) + list(cfg.get("server") or []))))
        for n in sorted(noms):
            if n.startswith("META-INF/jarjar/") and n.endswith(".jar"):
                try:
                    with zipfile.ZipFile(io.BytesIO(z.read(n))) as zi:
                        lire(zi, nom_jar + "!" + n.rsplit("/", 1)[-1], False)
                except (OSError, zipfile.BadZipFile):
                    pass

    for j in sorted((d / "mods").glob("*.jar")):
        try:
            with zipfile.ZipFile(j) as z:
                lire(z, j.name, j.name not in tiers)
        except (OSError, zipfile.BadZipFile):
            continue
    return nous, autres


def rapport_strict(d, lignes):
    """Mode strict : (lignes du résumé, lignes du journal de la tâche). Chaque injection ratée (« Mixin apply|prepare
    failed »), les nôtres d'abord, chaque classe visée qui n'a pas pu être transformée (« Could not force-load », vu par
    l'audit de ModernFix), les cibles absentes, et ceux de nos mixins jamais appliqués pendant ce démarrage (logs/debug.log :
    « Mixing X from config into cible », écrit au niveau DEBUG par Mixin)."""
    nous, autres = configs_mixins(d)
    echecs, forces, absentes = [], [], []
    audit = 0   # classes chargées par l'audit de ModernFix (preuve qu'il a tourné)
    for k, l in enumerate(lignes):
        if FORCE_CHARGE.search(l):
            audit += 1
            continue
        m = ECHEC_MIXIN.search(l)
        if m:
            ctx = m.group(2)
            mc = re.search(r"(\S+?\.json):(\S+)", ctx)
            mp = re.match(r"preparing (\S+) in (\S+)", ctx)
            config, mixin = (mc.group(1), mc.group(2)) if mc else (mp.group(2), mp.group(1)) if mp else ("?", ctx)
            mt = re.search(r" -> (\S+)$", ctx)
            echecs.append((config in nous, config, mixin, mt.group(1) if mt else "", m.group(1), m.group(3).rsplit(".", 1)[-1],
                           m.group(4).strip()))
            continue
        m = FORCE_RATE.search(l)
        if m:
            suite = [x.strip() for x in lignes[k + 1:k + 4] if "Exception" in x or "Error" in x]
            forces.append((m.group(1), suite[0][:300] if suite else ""))
            continue
        m = CIBLE_ABSENTE.search(l)
        if m:
            absentes.append((m.group(2) in nous, m.group(2), m.group(3), m.group(1)))
    appliques = {}   # config -> {mixin: {cibles}}
    dbg = d / "logs" / "debug.log"
    lu = dbg.exists()
    if lu:
        with open(dbg, encoding="utf-8", errors="replace") as f:
            for l in f:
                if "Mixing " not in l:
                    continue
                m = MIXING.search(l)
                if m:
                    appliques.setdefault(m.group(2), {}).setdefault(m.group(1), set()).add(m.group(3))
    a_nous = [e for e in echecs if e[0]]
    rates_nous = {(e[1], e[2]) for e in a_nous}
    listes = sum(len(v[1]) for v in nous.values())
    vus = sum(1 for c, (_, xs) in nous.items() for x in xs if x in appliques.get(c, {}))
    plugins = sum(len(set(appliques.get(c, {})) - set(xs)) for c, (_, xs) in nous.items())
    jamais = [(c, x) for c, (_, xs) in sorted(nous.items()) for x in xs
              if x not in appliques.get(c, {}) and (c, x) not in rates_nous]
    # une classe visée par un mixin raté reste « pas traitée » pour sa config : l'audit la redemande, elle est déjà chargée
    # (« Could not force-load ... already classloaded ») ; seules les autres disent qu'un mixin n'a jamais pu s'appliquer
    cibles_ratees = {e[3] for e in echecs if e[3]}
    forces = [(c, r, c in cibles_ratees) for c, r in forces]
    resume = ["", "**Mixins stricts** : %d injection(s) ratée(s) dans nos mods, %d chez les autres auteurs ; %d classe(s) "
              "visée(s) pas transformée(s) (« Could not force-load », dont %d seulement à cause d'une injection ratée "
              "ci-dessous) ; %d cible(s) absente(s) dans nos configs ; %d classe(s) chargée(s) par l'audit de ModernFix." % (
                  len(a_nous), len(echecs) - len(a_nous), len(forces), sum(1 for f in forces if f[2]),
                  sum(1 for x in absentes if x[0]), audit)]
    resume.append("- Nos mixins : %d configs, %d mixins listés pour un serveur ; %s." % (
        len(nous), listes, ("%d essayés pendant ce démarrage (logs/debug.log ; plus %d ajoutés par le plugin d'une config), "
                            "%d jamais essayés" % (vus, plugins, len(jamais))) if lu
        else "logs/debug.log absent : essayés ou non, pas lu"))
    if echecs:
        resume += ["", "```"]
        for e in sorted(echecs, key=lambda e: (not e[0], e[1], e[2])):
            resume.append("%s %s:%s%s : %s" % ("NOUS  " if e[0] else "autres", e[1], e[2], " -> " + e[3] if e[3] else "",
                                               e[6][:220]))
        resume.append("```")
    detail = ["--- MIXINS STRICTS : injections ratées, les nôtres d'abord (%d, dont %d à nous) ---" % (len(echecs), len(a_nous))]
    for e in sorted(echecs, key=lambda e: (not e[0], e[1], e[2])):
        detail.append("%s [%s] %s:%s%s (%s, jar %s)" % ("NOUS  " if e[0] else "autres", e[4], e[1], e[2],
                                                        " -> " + e[3] if e[3] else "", e[5],
                                                        nous[e[1]][0] if e[0] else autres.get(e[1], "?")))
        detail.append("         " + e[6][:1500])
    detail.append("--- MIXINS STRICTS : classes visées pas transformées (« Could not force-load », %d ; audit de ModernFix : "
                  "%d classe(s) chargée(s)) ---" % (len(forces), audit))
    detail += ["  %s : %s%s" % (c, r, " (suite de l'injection ratée sur cette classe)" if suite else "")
               for c, r, suite in forces]
    detail.append("--- MIXINS STRICTS : cibles absentes (%d) ---" % len(absentes))
    detail += ["  %s %s:%s -> %s" % ("NOUS  " if x[0] else "autres", x[1], x[2], x[3]) for x in absentes]
    detail.append("--- MIXINS STRICTS : nos mixins jamais appliqués pendant ce démarrage (%d ; classe visée jamais chargée, "
                  "mixin écarté par le plugin de sa config ou cible absente) ---" % len(jamais))
    detail += ["  %s:%s" % j for j in jamais]
    detail.append("--- MIXINS STRICTS : nos configs (appliqués / listés pour un serveur) ---")
    for c, (jar, xs) in sorted(nous.items()):
        vus_c = appliques.get(c, {})
        en_plus = sorted(set(vus_c) - set(xs))   # ajoutés par le plugin de la config (Spartan : mixins de TaCZ)
        detail.append("  %s (%s) : %d / %d%s" % (c, jar, sum(1 for x in xs if x in vus_c), len(xs),
                                                 " ; ajoutés par son plugin : %s" % ", ".join(en_plus) if en_plus else ""))
    return resume, detail


def finir(a, res, serveur, rapport, d=None):
    lignes = serveur.depuis(0) if serveur else []
    fatales = []
    for k, l in enumerate(lignes):
        if FATALES.search(l) and len(fatales) < 12:
            fatales.append([l[:400]] + [x[:300] for x in lignes[k + 1:k + 4] if x.lstrip().startswith(("at ", "Caused by"))])
    if fatales and not res["echec"]:
        res["echec"] = "ligne fatale dans le journal (le serveur a démarré, mais sans une partie de son code)"
    erreurs = Counter(essentiel(l) for l in lignes if ERREUR.search(l))
    avertis = Counter(essentiel(l) for l in lignes if AVERT.search(l))
    arret_propre = res.get("code_sortie") == 0 and any("Stopping server" in l or "Stopping the server" in l for l in lignes)
    ok = not res["echec"]
    o = ["### Démarrage d'essai (avant l'envoi au Ptero)", ""]
    o.append("- Serveur : nos jars de `livraison/mods serveur`, les plugins et les réglages du dépôt, %s, mémoire de Java "
             "%s Mo au plus, monde plat neuf (rien du Ptero, aucun secret)." % (res["jar"], res["tas"]))
    if res.get("stricts"):
        o.append("- **Mixins stricts** (diagnostic, `%s` ; ModernFix coupé dans la copie d'essai : %s) : toute injection qui "
                 "ne trouve pas sa cible est une erreur (« Mixin ... failed »), même celles qui ratent d'habitude sans rien "
                 "dire, et chaque classe visée est chargée. Un refus ici ne dit rien de la mise en ligne : lire la liste "
                 "ci-dessous." % (" ".join(OPTIONS_STRICTES), ", ".join(MODERNFIX_COUPES)))
    if ok:
        o.append("- **Réussi** : « Done » en %s s (%s s pour Minecraft)%s, puis %d s à vide sans arrêt."
                 % (res.get("done_s"), res.get("done_mc_s"), " après un second lancement (installation de Mohist)"
                    if res["essais"] > 1 else "", a.apres))
    else:
        o.append("- **Refusé** : %s. **Rien n'est envoyé au Ptero** : le serveur en ligne garde la version d'avant." % res["echec"])
        o.append("- Pour passer quand même (urgence seulement) : « Run workflow » avec la case « sans_demarrage_essai ».")
    o.append("- Arrêt : %s (%s s)." % ("propre" if arret_propre else "**pas propre** (code %s)" % res.get("code_sortie"),
                                      res.get("arret_s")))
    if fatales:
        o += ["", "Lignes fatales (les premières) :", "", "```"]
        for bloc in fatales[:6]:
            o += bloc
        o.append("```")
    if erreurs:
        o += ["", "Lignes d'erreur du journal (%d, %d sortes ; la plupart sont habituelles et ne bloquent rien) :" % (
            sum(erreurs.values()), len(erreurs)), "", "```"]
        o += ["%4d  %s" % (n, e) for e, n in erreurs.most_common(12)]
        o.append("```")
    if avertis:
        o += ["", "Avertissements du journal (%d, %d sortes ; les %d plus fréquents, liste complète dans le journal de la tâche) :"
              % (sum(avertis.values()), len(avertis), min(25, len(avertis))), "", "```"]
        o += ["%4d  %s" % (n, e) for e, n in avertis.most_common(25)]
        o.append("```")
    corriges = reglages_corriges(d) if d else None
    avec_cles = [c for c in corriges if c[1]] if corriges else []
    if avec_cles:
        # « null » avant : clé absente du fichier, ajoutée avec sa valeur par défaut ; « null » après : clé qui n'existe plus
        # dans le code, retirée ; sinon : valeur hors bornes ou du mauvais type, REMPLACÉE par la valeur par défaut
        o += ["", "Réglages corrigés par Forge au démarrage (%d fichier(s) ; « null -> x » : clé ajoutée avec sa valeur par "
              "défaut ; « x -> null » : clé qui n'existe plus ; sinon valeur refusée, remplacée par la valeur par défaut) :"
              % len(avec_cles), "", "```"]
        for nom, cles, _ in avec_cles:
            o.append("%s (%d clé(s))" % (nom, len(cles)))
            o += ["    " + c for c in cles[:40]]
            if len(cles) > 40:
                o.append("    ... et %d autre(s)" % (len(cles) - 40))
        o.append("```")
    commentaires = [c[0] for c in corriges if not c[1]] if corriges else []
    if commentaires:
        o += ["", "Fichiers dont Forge n'a remis que les commentaires (valeurs inchangées) : %s." % ", ".join(commentaires)]
    detail_strict = []
    if res.get("stricts") and d:
        resume_strict, detail_strict = rapport_strict(d, lignes)
        o += resume_strict
    texte = "\n".join(o) + "\n"
    (rapport / "resume.md").write_text(texte, encoding="utf-8")
    print(texte)
    # toutes les sortes d'erreurs et d'avertissements, chacune avec sa première ligne entière (audit du 21/11 : seules
    # les 12 erreurs les plus fréquentes étaient visibles, aucun avertissement ; un réglage hors bornes remis à sa valeur
    # par défaut par Forge ne se voyait nulle part)
    for titre, rx, compte in (("ERREURS", ERREUR, erreurs), ("AVERTISSEMENTS", AVERT, avertis)):
        if not compte:
            continue
        exemple = {}
        for l in lignes:
            if rx.search(l):
                exemple.setdefault(essentiel(l), l)
        print("--- %s : %d sortes (nombre, première ligne) ---" % (titre, len(compte)))
        for e, n in compte.most_common(300):
            print("%4d  %s" % (n, exemple.get(e, e)[:400]))
    for l in detail_strict:
        print(l)
    if not ok:
        print("--- 80 dernières lignes du journal du serveur ---")
        for l in lignes[-80:]:
            print("  | " + l[:400])
    if os.environ.get("GITHUB_STEP_SUMMARY"):
        with open(os.environ["GITHUB_STEP_SUMMARY"], "a", encoding="utf-8") as f:
            f.write(texte)
    if not ok:
        print("::error::Démarrage d'essai refusé : %s" % res["echec"])
    return 0 if ok else 1


def main():
    p = argparse.ArgumentParser(description="Démarrage d'essai du serveur fabriqué (voir l'aide en tête du fichier)")
    p.add_argument("dossier", help="dossier du serveur d'essai (neuf, avec le jar de Mohist)")
    p.add_argument("--attente", type=float, default=12, help="minutes au plus jusqu'à « Done » (installation de Mohist comprise)")
    p.add_argument("--apres", type=int, default=20, help="secondes à vide après « Done », serveur toujours en marche")
    p.add_argument("--memoire", default="", help="tas de Java en Mo (défaut : 6 Go, moins sur une petite machine)")
    p.add_argument("--java", default="java", help="Java 17 à utiliser (défaut : celui du PATH)")
    p.add_argument("--graine", type=int, default=GRAINE)
    p.add_argument("--rapport", default=None, help="dossier du journal complet et de resume.md")
    p.add_argument("--mixins-stricts", action="store_true",
                   help="diagnostic : toute injection de mixin sans cible devient une erreur (jamais pour une mise en ligne)")
    a = p.parse_args()
    d = Path(a.dossier).resolve()
    d.mkdir(parents=True, exist_ok=True)
    preparer(d, a.graine)
    sys.exit(lancer(a, d))


if __name__ == "__main__":
    main()
