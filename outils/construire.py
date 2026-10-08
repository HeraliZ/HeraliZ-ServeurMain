"""Fabrique nos mods et range les jars à livrer.

    python outils/construire.py                      tous les mods de mods/
    python outils/construire.py heraliz-boss crac    seulement ceux-là (les autres jars déjà fabriqués sont repris)
    python outils/construire.py --sans-build         ne fabrique rien, range seulement les jars déjà dans build/libs
    python outils/construire.py --si-change          ne refabrique que les mods dont les sources ont changé depuis leur
                                                     dernière fabrication (empreinte dans build/libs/.empreinte)

Résultat (dossier livraison/, jamais dans Git) :
    livraison/mods serveur/   -> mods/ du Ptero      (mods tiers + nos jars -serveur)
    livraison/mods client/    -> mods/ des joueurs   (mods tiers + mods tiers client + nos jars -client + nos mods client seulement)
    livraison/mods solo/      -> jeu solo / tests    (comme client, avec nos jars -complet)
    livraison/plugins/        -> plugins/ du Ptero
Un mod « coupé » (tâches clientJar / serveurJar dans son build.gradle) donne 3 jars ; un mod client seulement
(heraliz-interface, dayzhotbar) donne un jar, mis côté client et solo.
Code de sortie 1 si un mod ne compile pas.
"""
import datetime
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins  # noqa: E402


def projets():
    return sorted(p for p in chemins.MODS.iterdir() if p.is_dir() and not p.name.startswith("_") and (p / "build.gradle").exists())


def proprietes(p):
    s = (p / "gradle.properties").read_text(encoding="utf-8")
    return dict(re.findall(r"^([\w.]+)=(.*)$", s, re.M))


def coupe(p):
    return "serveurJar" in (p / "build.gradle").read_text(encoding="utf-8")


def jars(p):
    """{côté: jar} fabriqué par le projet (côté = client / serveur / complet, ou seul pour un mod client seulement)."""
    pr = proprietes(p)
    libs = p / "build" / "libs"
    if coupe(p):
        base = "%s-%s" % (pr["jar_name"], pr["mod_version"])
        return {c: libs / ("%s-%s.jar" % (base, c)) for c in ("client", "serveur", "complet")}
    nom = "%s-%s.jar" % (pr.get("mod_id", pr.get("jar_name")), pr["mod_version"])
    return {"seul": libs / nom}


def empreinte(p):
    """Empreinte des sources et fichiers de fabrication du projet (pas de build/ ni run/)."""
    import hashlib
    h = hashlib.sha256()
    for f in sorted(p.rglob("*")):
        rel = f.relative_to(p)
        if f.is_file() and rel.parts[0] not in ("build", "run", ".gradle", "out"):
            h.update(rel.as_posix().encode())
            h.update(f.read_bytes())
    return h.hexdigest()


def a_jour(p):
    e = p / "build" / "libs" / ".empreinte"
    return e.exists() and e.read_text().strip() == empreinte(p) and all(j.exists() for j in jars(p).values())


def env():
    e = dict(os.environ)
    e.pop("JAVA_TOOL_OPTIONS", None)
    e.pop("_JAVA_OPTIONS", None)
    e.setdefault("JAVA_HOME", str(chemins.JAVA_HOME))
    return e


def fabriquer(p):
    gw = p / ("gradlew.bat" if os.name == "nt" else "gradlew")
    if os.name != "nt":
        gw.chmod(0o755)
    avant = empreinte(p)   # prise avant : une source changée pendant la fabrication sera refabriquée la fois suivante
    (p / "run").mkdir(exist_ok=True)
    log = p / "run" / "construire.log"
    print("[construire] %s ..." % p.name, flush=True)
    with open(log, "w", encoding="utf-8", errors="replace") as f:
        r = subprocess.run([str(gw), "build", "--console=plain", "-Dorg.gradle.jvmargs=-Xmx1024m"], cwd=p, env=env(),
                           stdout=f, stderr=subprocess.STDOUT)
    lignes = log.read_text(encoding="utf-8", errors="replace").splitlines()
    if r.returncode != 0:
        print("\n".join(l for l in lignes if "error:" in l or "FAILED" in l or "What went wrong" in l)[:4000])
        print("[construire] ÉCHEC %s (journal : %s)" % (p.name, log))
        return False
    (p / "build" / "libs" / ".empreinte").write_text(avant)
    print("[construire] %s : OK" % p.name)
    return True


def ranger():
    L = chemins.LIVRAISON
    if L.exists():
        shutil.rmtree(L)
    ms, mc, mo, pl = L / "mods serveur", L / "mods client", L / "mods solo", L / "plugins"
    for d in (ms, mc, mo, pl):
        d.mkdir(parents=True)
    for j in chemins.MODS_TIERS.glob("*.jar"):
        for d in (ms, mc, mo):
            shutil.copy2(j, d / j.name)
    for j in chemins.MODS_TIERS_CLIENT.glob("*.jar"):
        for d in (mc, mo):
            shutil.copy2(j, d / j.name)
    manquants = []
    for p in projets():
        for cote, j in jars(p).items():
            if not j.exists():
                manquants.append(j)
                continue
            cibles = {"serveur": [ms], "client": [mc], "complet": [mo], "seul": [mc, mo]}[cote]
            for d in cibles:
                shutil.copy2(j, d / j.name)
    src_pl = chemins.DEPOT / "serveur" / "plugins"
    for f in src_pl.iterdir():
        (shutil.copytree if f.is_dir() else shutil.copy2)(f, pl / f.name)
    for d in (ms, mc, mo):
        print("[construire] %-12s %d jars" % (d.name, len(list(d.glob("*.jar")))))
    officiel(mc, L / "spartan" / "officiel.json")
    for j in manquants:
        print("[construire] ATTENTION : jar absent (mod pas encore fabriqué ?) :", j)
    return not manquants


def _sha256(data):
    return hashlib.sha256(data).hexdigest()


def _paquet(z, nom):
    """Paquet déclaré par une config Mixin du jar ("" si illisible)."""
    try:
        d = json.loads(z.read(nom).decode("utf-8", "replace"))
        return d.get("package", "") if isinstance(d, dict) else ""
    except (KeyError, ValueError):
        return ""


def _mixins(z, out, profondeur=0):
    """Configs Mixin d'un jar (et de ses jars imbriqués) -> paquet : manifeste MixinConfigs + fichiers de config."""
    noms = z.namelist()
    if "META-INF/MANIFEST.MF" in noms:
        attrs, cle = {}, None
        for l in z.read("META-INF/MANIFEST.MF").decode("utf-8", "replace").splitlines():
            if l.startswith(" ") and cle:
                attrs[cle] += l[1:]
            elif ":" in l:
                cle, v = l.split(":", 1)
                attrs[cle.strip()] = v.strip()
        for c in attrs.get("MixinConfigs", "").split(","):
            if c.strip():
                out[c.strip()] = out.get(c.strip()) or _paquet(z, c.strip())
    for n in noms:
        if n.endswith(".json") and not n.startswith(("assets/", "data/")) and z.getinfo(n).file_size < 65536:
            try:
                d = json.loads(z.read(n).decode("utf-8", "replace"))
            except ValueError:
                continue
            if isinstance(d, dict) and "package" in d and any(isinstance(d.get(k), list) for k in ("mixins", "client", "server")):
                out[n] = out.get(n) or str(d.get("package", ""))
        elif n.startswith("META-INF/jarjar/") and n.endswith(".jar") and profondeur < 3:
            import io
            with zipfile.ZipFile(io.BytesIO(z.read(n))) as zi:
                _mixins(zi, out, profondeur + 1)


def officiel(dossier, sortie):
    """Modpack officiel des joueurs pour Spartan (config/spartan/officiel.json sur le serveur) : empreinte de chaque jar,
    configs Mixin connues (nom -> paquet), empreinte du dossier (même calcul que LaunchToken.modsHash du mod). Spartan signale tout jar
    ou toute config Mixin en plus, sans rien apprendre à la main."""
    mods, mixins = {}, {}
    for j in sorted(dossier.glob("*.jar")):
        data = j.read_bytes()
        mods[j.name.lower()] = _sha256(data)
        with zipfile.ZipFile(j) as z:
            _mixins(z, mixins)
    lignes = sorted("%s:%s" % (n, h) for n, h in mods.items())
    doc = {
        "genere": datetime.datetime.now(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "empreinteDossier": _sha256("\n".join(lignes).encode("utf-8")),
        "mods": mods,
        "mixins": dict(sorted(mixins.items())),
    }
    sortie.parent.mkdir(parents=True, exist_ok=True)
    sortie.write_text(json.dumps(doc, indent=2, ensure_ascii=False), encoding="utf-8")
    print("[construire] spartan/officiel.json : %d jars, %d configs Mixin" % (len(mods), len(mixins)))


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    ok = True
    if "--sans-build" not in sys.argv:
        choisis = [chemins.MODS / a for a in args] if args else projets()
        for p in choisis:
            if not p.is_dir():
                raise SystemExit("projet inconnu : %s" % p)
            if "--si-change" in sys.argv and a_jour(p):
                print("[construire] %s : inchangé" % p.name)
                continue
            ok = fabriquer(p) and ok
    ok = ranger() and ok
    sys.exit(0 if ok else 1)


if __name__ == "__main__":
    main()
