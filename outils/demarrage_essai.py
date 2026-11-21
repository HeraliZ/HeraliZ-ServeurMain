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

--mixins-stricts (diagnostic, audit du 21/11 ; jamais pour une mise en ligne) : -Dmixin.debug.countInjections=true, toute
injection de mixin qui ne trouve pas sa cible devient une erreur, même celles marquées « require 0 » qui ratent sans rien
dire d'habitude (187 dans nos mods, plus celles des autres auteurs). La première qui rate dans une configuration
obligatoire arrête le serveur. ModernFix en a une qui rate toujours sur Mohist (« reduce_blockstate_cache_rebuilds », sans
effet sur le serveur en ligne) : coupée dans le serveur d'essai seulement.
"""
import argparse
import os
import re
import shutil
import signal
import subprocess
import sys
import time
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
        env["JDK_JAVA_OPTIONS"] += " -Dmixin.debug.countInjections=true"
        mf = d / "config" / "modernfix-mixins.properties"
        if mf.exists():
            with open(mf, "a", encoding="utf-8") as f:
                f.write("\nmixin.perf.reduce_blockstate_cache_rebuilds=false\n")
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
        o.append("- **Mixins stricts** (diagnostic, `-Dmixin.debug.countInjections=true`) : toute injection qui ne trouve "
                 "pas sa cible est une erreur (« Mixin ... failed »), même celles qui ratent d'habitude sans rien dire. Un "
                 "refus ici ne dit rien de la mise en ligne : lire les lignes d'erreur et d'avertissement.")
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
