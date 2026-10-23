"""Brouille (obscurcit) les jars DÉJÀ FABRIQUÉS de nos mods, pour gêner la décompilation (CLAUDE.md §10 et §11).

    python outils/brouiller.py airstrike            brouille les jars de ce mod dans son build/libs
    python outils/brouiller.py airstrike --verifier  brouille PUIS relance verif_reobf.py sur les jars brouillés
    python outils/brouiller.py airstrike --annuler    remet les jars d'avant le brouillage (depuis la sauvegarde)

À lancer APRÈS « python outils/construire.py <mod> » (il travaille sur les jars réobfusqués de build/libs),
puis « python outils/verifier.py <mod> », PUIS un ESSAI EN JEU avant toute mise en ligne (CLAUDE.md §8, règle 8).

Méthode (prudente, « modérée ») : voir outils/brouillage/commun.pro.
  - aucune classe n'est renommée (API par réflexion, Mixins, GeckoLib, @Mod, enregistrements Forge gardés) ;
  - seuls les membres PRIVÉS de notre code sont renommés, et les numéros de ligne / noms de variables effacés ;
  - le code des autres auteurs n'est jamais touché (licences) ;
  - Minecraft et Forge (noms SRG) sont donnés en bibliothèques : ProGuard ne les renomme pas → verif_reobf reste vert.

Chaque mod peut avoir un réglage en plus : outils/brouillage/<mod>.pro (ajouté à commun.pro).
Fait exprès : NON branché dans la fabrication automatique ni dans les workflows ; on brouille mod par mod, à la main,
après avoir essayé le mod NON brouillé en jeu. Les jars d'origine sont sauvegardés dans build/libs/.avant_brouillage/.

Besoin sur le PC (comme verif_reobf / verif_split) : le lanceur CurseForge Forge 1.20.1 lancé une fois (Minecraft en
noms SRG) et, pour les jars -serveur, le dossier libraries/ d'un serveur Mohist (chemins dans outils/chemins_local.json).
ProGuard est téléchargé et gardé dans outils/brouillage/.proguard/ au premier lancement.
"""
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins          # noqa: E402
import verif_split as vs  # noqa: E402  (constantes MC_SRG / FORGE_*, mêmes chemins de bibliothèques)

PROGUARD_VERSION = "7.4.2"
PROGUARD_URL = f"https://github.com/Guardsquare/proguard/releases/download/v{PROGUARD_VERSION}/proguard-{PROGUARD_VERSION}.zip"
BROUILLAGE = chemins.DEPOT / "outils" / "brouillage"
CACHE = BROUILLAGE / ".proguard"


def proprietes(p):
    import re
    s = (p / "gradle.properties").read_text(encoding="utf-8")
    return dict(re.findall(r"^([\w.]+)=(.*)$", s, re.M))


def jars_du_mod(mod):
    """{côté: chemin du jar réobfusqué} présents dans build/libs."""
    p = chemins.MODS / mod
    pr = proprietes(p)
    libs = p / "build" / "libs"
    coupe = "serveurJar" in (p / "build.gradle").read_text(encoding="utf-8")
    out = {}
    if coupe:
        base = "%s-%s" % (pr["jar_name"], pr["mod_version"])
        for cote in ("serveur", "client", "complet"):
            j = libs / ("%s-%s.jar" % (base, cote))
            if j.exists():
                out[cote] = j
    else:
        nom = "%s-%s.jar" % (pr.get("jar_name", pr.get("mod_id")), pr["mod_version"])
        j = libs / nom
        if j.exists():
            out["seul"] = j      # mod client seulement (interface, dayzhotbar)
    return out


def bibliotheques(cote):
    """Jars à passer en -libraryjars : Minecraft + Forge (noms SRG) + geckolib + libs/ du mod + mods tiers + JDK.
    ProGuard ne renomme jamais une bibliothèque : les références à Minecraft gardent leurs noms SRG."""
    libs, vus = [], set()

    def add(p):
        p = Path(p)
        if p.exists() and p.name not in vus:
            vus.add(p.name)
            libs.append(p)

    lib_c = vs.LANCEUR / "libraries"
    lib_s = vs.SERVEUR / "libraries"
    cote_client = cote in ("client", "complet", "seul")
    cote_serveur = cote in ("serveur", "complet")

    if cote_client:
        add(lib_c / f"net/minecraft/client/{vs.MC_SRG}/client-{vs.MC_SRG}-srg.jar")
        add(lib_c / f"net/minecraftforge/forge/{vs.FORGE_CLIENT}/forge-{vs.FORGE_CLIENT}-client.jar")
        add(lib_c / f"net/minecraftforge/forge/{vs.FORGE_CLIENT}/forge-{vs.FORGE_CLIENT}-universal.jar")
    if cote_serveur:
        add(lib_s / f"net/minecraft/server/{vs.MC_SRG}/server-{vs.MC_SRG}-srg.jar")
        add(lib_s / f"net/minecraftforge/forge/{vs.FORGE_SERVEUR}/forge-{vs.FORGE_SERVEUR}-server.jar")
        add(lib_s / f"net/minecraftforge/forge/{vs.FORGE_SERVEUR}/forge-{vs.FORGE_SERVEUR}-universal.jar")
    # toutes les autres bibliothèques du lanceur / serveur (Forge en a beaucoup)
    for racine in ((lib_c,) if cote_client else ()) + ((lib_s,) if cote_serveur else ()):
        if racine.exists():
            for j in racine.rglob("*.jar"):
                if "net/minecraft/" in j.as_posix() and "-srg.jar" not in j.name:
                    continue
                add(j)
    return libs


def libs_compilation(mod):
    """Jars dont le mod a besoin pour compiler (ses dépendances) : son dossier libs/ + GeckoLib + les mods tiers."""
    out = []
    ld = chemins.MODS / mod / "libs"
    if ld.exists():
        out += sorted(ld.glob("*.jar"))
    if chemins.GECKOLIB.exists():
        out.append(chemins.GECKOLIB)
    if chemins.MODS_TIERS.exists():
        out += sorted(chemins.MODS_TIERS.glob("*.jar"))
    return out


def jmods_jdk():
    """Modules du JDK à donner en bibliothèque (ProGuard 7 ne les ajoute pas tout seul)."""
    out = []
    for m in ("java.base", "java.desktop", "java.logging", "java.management", "jdk.unsupported"):
        j = chemins.JAVA_HOME / "jmods" / f"{m}.jmod"
        if j.exists():
            out.append(j)
    return out


def proguard_jar():
    """Chemin du proguard.jar (téléchargé et mis en cache au premier lancement)."""
    jar = CACHE / f"proguard-{PROGUARD_VERSION}" / "lib" / "proguard.jar"
    if jar.exists():
        return jar
    CACHE.mkdir(parents=True, exist_ok=True)
    zip_local = CACHE / f"proguard-{PROGUARD_VERSION}.zip"
    if not zip_local.exists():
        print(f"Téléchargement de ProGuard {PROGUARD_VERSION}…")
        try:
            urllib.request.urlretrieve(PROGUARD_URL, zip_local)
        except Exception as e:
            sys.exit(f"Impossible de télécharger ProGuard ({e}).\n"
                     f"Télécharge-le à la main depuis {PROGUARD_URL}\n"
                     f"et dézippe-le dans {CACHE} (pour obtenir {jar}).")
    with zipfile.ZipFile(zip_local) as z:
        z.extractall(CACHE)
    if not jar.exists():
        sys.exit(f"proguard.jar introuvable après dézippage dans {CACHE}.")
    return jar


def java_exe():
    for c in (chemins.JAVA_HOME / "bin" / "java.exe", chemins.JAVA_HOME / "bin" / "java"):
        if c.exists():
            return str(c)
    return shutil.which("java") or "java"


def brouiller_jar(mod, cote, jar):
    """Lance ProGuard sur un jar, en place. L'original part dans build/libs/.avant_brouillage/."""
    libs_dir = jar.parent
    sauvegarde_dir = libs_dir / ".avant_brouillage"
    sauvegarde_dir.mkdir(exist_ok=True)
    sauvegarde = sauvegarde_dir / jar.name
    source = sauvegarde if sauvegarde.exists() else jar   # re-brouiller repart toujours de l'original
    if not sauvegarde.exists():
        shutil.copy2(jar, sauvegarde)

    sortie = libs_dir / (jar.stem + ".brouille.jar")
    commun = BROUILLAGE / "commun.pro"
    propre = BROUILLAGE / f"{mod}.pro"

    lignes = [f'-injars "{source}"', f'-outjars "{sortie}"']
    for b in bibliotheques(cote) + libs_compilation(mod) + jmods_jdk():
        lignes.append(f'-libraryjars "{b}"')   # ProGuard lit les .jar comme les .jmod du JDK
    lignes.append(f'@"{commun}"')
    if propre.exists():
        lignes.append(f'@"{propre}"')

    with tempfile.NamedTemporaryFile("w", suffix=".pro", delete=False, encoding="utf-8") as f:
        f.write("\n".join(lignes) + "\n")
        conf = f.name
    try:
        print(f"  {cote:8s} {jar.name} …")
        r = subprocess.run([java_exe(), "-jar", str(proguard_jar()), f"@{conf}"],
                           capture_output=True, text=True)
        if r.returncode != 0:
            sys.stderr.write(r.stdout[-4000:] + "\n" + r.stderr[-4000:] + "\n")
            sys.exit(f"ProGuard a échoué sur {jar.name} (code {r.returncode}).")
        if not sortie.exists():
            sys.exit(f"ProGuard n'a pas produit {sortie.name}.")
        shutil.move(str(sortie), str(jar))     # remplace le jar par sa version brouillée
    finally:
        os.unlink(conf)


def annuler(mod):
    n = 0
    for cote, jar in jars_du_mod(mod).items():
        sauvegarde = jar.parent / ".avant_brouillage" / jar.name
        if sauvegarde.exists():
            shutil.copy2(sauvegarde, jar)
            print(f"  remis : {jar.name}")
            n += 1
    print(f"{n} jar(s) remis à l'état d'avant le brouillage." if n else
          "Aucune sauvegarde trouvée (ces jars n'ont pas été brouillés).")


def main(argv):
    args = [a for a in argv if not a.startswith("--")]
    opts = {a for a in argv if a.startswith("--")}
    if not args:
        sys.exit(__doc__)
    mod = args[0]
    if not (chemins.MODS / mod / "build.gradle").exists():
        sys.exit(f"Mod inconnu : {mod} (dossier mods/{mod} introuvable).")
    if "--annuler" in opts:
        return annuler(mod)

    jars = jars_du_mod(mod)
    if not jars:
        sys.exit(f"Aucun jar fabriqué pour {mod}. Lance d'abord : python outils/construire.py {mod}")
    print(f"Brouillage de {mod} ({len(jars)} jar(s)) — réglage : commun.pro"
          + (f" + {mod}.pro" if (BROUILLAGE / f'{mod}.pro').exists() else "") + " :")
    for cote, jar in jars.items():
        brouiller_jar(mod, cote, jar)
    print("Terminé. Les jars d'origine sont dans build/libs/.avant_brouillage/ (--annuler pour les remettre).")

    if "--verifier" in opts:
        print("\nverif_reobf.py sur les jars brouillés :")
        import verif_reobf
        code = subprocess.run([sys.executable, str(chemins.DEPOT / "outils" / "verif_reobf.py"),
                               "--minecraft", "--verifier", *[str(j) for j in jars.values()]]).returncode
        if code:
            sys.exit("verif_reobf a signalé un problème sur les jars brouillés : NE PAS mettre en ligne.")
        print("verif_reobf : vert.")
    print("\nRappel : « python outils/verifier.py %s » puis un ESSAI EN JEU avant toute mise en ligne." % mod)


if __name__ == "__main__":
    main(sys.argv[1:])
