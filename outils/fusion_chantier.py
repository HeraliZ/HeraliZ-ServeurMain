"""Travail à plusieurs sur un projet fusionné (docs/METHODE_CLIENT_SERVEUR.md §14) : un « chantier » par mod.

    python outils/fusion_chantier.py creer     <projet> <modId> [<paquet>]
    python outils/fusion_chantier.py rapatrier <projet> <modId> [<paquet>]
    ex. python outils/fusion_chantier.py creer mods/heraliz-boss heralisz_mere

creer : mods/_chantier/<nom court>/ = copie du projet réduite au code partagé (fr/heraliz/*) + ce seul mod (son code dans
        les trois ensembles de sources, ses ressources, son bloc [[mods]] et celui du porteur). Un agent peut y construire
        (lancer.py) et contrôler (verif_split) sans gêner les autres.
rapatrier : remet le code du mod découpé dans le projet (les anciennes copies vont dans _retires/fusion_chantier/), recopie
        ses ressources si elles ont changé, et signale toute modification du code partagé ou du bloc [[mods]] (à reporter à
        la main).
<paquet> : par défaut fr/heralisz/<nom court> (modId heralisz_<nom court>).
"""
import filecmp
import re
import shutil
import sys
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def arbre(d):
    return {p.relative_to(d).as_posix() for p in d.rglob("*") if p.is_file()} if d.exists() else set()


def differences(a, b):
    fa, fb = arbre(a), arbre(b)
    return sorted(x for x in fa | fb if x not in fa or x not in fb or not filecmp.cmp(a / x, b / x, shallow=False))


def chemins(modid, paquet):
    res = [f"src/client/resources/assets/{modid}", f"src/serveur/resources/data/{modid}", f"src/client/resources/{modid}.mixins.json",
           f"src/commun/resources/{modid}.mixins.json"]
    code = [f"src/{c}/java/{paquet}" for c in ("commun", "client", "serveur")]
    return code, res


def bloc_mods(toml, modid):
    m = re.search(r'(?ms)^\[\[mods\]\]\s*\nmodId="' + re.escape(modid) + r'".*?(?=^\[\[mods\]\]|\Z)', toml)
    return m.group(0).strip() if m else None


def creer(projet, modid, paquet, chantier):
    if chantier.exists():
        raise SystemExit(f"{chantier} existe déjà")
    chantier.mkdir(parents=True)
    for f in ("build.gradle", "gradle.properties", "gradlew", "gradlew.bat"):
        shutil.copy2(projet / f, chantier / f)
    shutil.copytree(projet / "gradle", chantier / "gradle")
    if (projet / "libs").exists():
        shutil.copytree(projet / "libs", chantier / "libs")
    s = (projet / "settings.gradle").read_text(encoding="utf-8")
    s = re.sub(r"rootProject\.name = '[^']*'", f"rootProject.name = 'chantier-{chantier.name}'", s)
    (chantier / "settings.gradle").write_text(s, encoding="utf-8", newline="\n")
    code, res = chemins(modid, paquet)
    partage = [f"src/{c}/java/fr/heraliz" for c in ("commun", "client", "serveur")] + ["src/commun/resources/pack.mcmeta"]
    porteur = [p.name for p in (projet / "src/client/resources/assets").glob("heraliz_*")]
    partage += [f"src/client/resources/assets/{n}" for n in porteur]
    for rel in partage + code + res:
        a, b = projet / rel, chantier / rel
        if a.is_dir():
            shutil.copytree(a, b)
        elif a.exists():
            b.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(a, b)
    toml = (projet / "src/commun/resources/META-INF/mods.toml").read_text(encoding="utf-8")
    blocs = re.split(r"(?m)^(?=\[\[mods\]\])", toml)
    premier = re.search(r'modId="([^"]+)"', blocs[1]).group(1)
    garde = [blocs[0]] + [b for b in blocs[1:] if re.search(r'modId="(' + re.escape(premier) + "|" + re.escape(modid) + ')"', b.split("\n")[1])]
    (chantier / "src/commun/resources/META-INF").mkdir(parents=True, exist_ok=True)
    (chantier / "src/commun/resources/META-INF/mods.toml").write_text("".join(garde), encoding="utf-8", newline="\n")
    print(f"[chantier] {chantier} prêt (porteur {premier} + {modid})")
    print(f"[chantier] construire : PYTHONIOENCODING=utf-8 python outils/lancer.py build {chantier.relative_to(ROOT).as_posix()} compileJava compileClientJava compileServeurJava")


def rapatrier(projet, modid, paquet, chantier):
    for c in ("commun", "client", "serveur"):
        d = differences(projet / f"src/{c}/java/fr/heraliz", chantier / f"src/{c}/java/fr/heraliz")
        if d:
            print(f"[chantier] code partagé différent ({c}) — à reporter à la main si voulu : {d}")
    code, res = chemins(modid, paquet)
    garde = ROOT / "_retires" / "fusion_chantier" / time.strftime("%Y%m%d-%H%M%S") / chantier.name
    for rel in code:
        a, b = projet / rel, chantier / rel
        if a.exists():
            (garde / rel).parent.mkdir(parents=True, exist_ok=True)
            shutil.move(str(a), str(garde / rel))
        if b.exists():
            shutil.copytree(b, a)
        print(f"[chantier] {rel} : {len(arbre(b))} fichier(s)")
    for rel in res:
        a, b = projet / rel, chantier / rel
        if b.is_file() and (not a.exists() or not filecmp.cmp(a, b, shallow=False)):
            a.parent.mkdir(parents=True, exist_ok=True)
            shutil.copy2(b, a)
            print(f"[chantier] ressource mise à jour : {rel}")
        elif b.is_dir():
            d = differences(a, b)
            for x in d:
                if (b / x).exists():
                    (a / x).parent.mkdir(parents=True, exist_ok=True)
                    shutil.copy2(b / x, a / x)
            if d:
                print(f"[chantier] {len(d)} ressource(s) mise(s) à jour dans {rel}")
        elif a.exists() and not b.exists():
            print(f"[chantier] ATTENTION : {rel} n'existe plus dans le chantier (laissé en place)")
    tp = (projet / "src/commun/resources/META-INF/mods.toml").read_text(encoding="utf-8")
    tc = (chantier / "src/commun/resources/META-INF/mods.toml").read_text(encoding="utf-8")
    if bloc_mods(tp, modid) != bloc_mods(tc, modid):
        print("[chantier] ATTENTION : bloc [[mods]] différent dans le chantier — à reporter à la main")
    print(f"[chantier] {modid} rapatrié ; anciennes copies : {garde}")


def main():
    if len(sys.argv) < 4 or sys.argv[1] not in ("creer", "rapatrier"):
        raise SystemExit(__doc__)
    projet = Path(sys.argv[2])
    projet = projet if projet.is_absolute() else ROOT / projet
    modid = sys.argv[3]
    court = modid.split("_", 1)[1] if "_" in modid else modid
    paquet = sys.argv[4].replace(".", "/") if len(sys.argv) > 4 else f"fr/heralisz/{court}"
    chantier = ROOT / "mods" / "_chantier" / court
    (creer if sys.argv[1] == "creer" else rapatrier)(projet, modid, paquet, chantier)


if __name__ == "__main__":
    main()
