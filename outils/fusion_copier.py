"""Premier tri d'un mod Forge vers un projet fusionné « commun / client / serveur » (docs/METHODE_CLIENT_SERVEUR.md §3).

    python outils/fusion_copier.py <projet source> <projet fusionné> [--force]
    ex. python outils/fusion_copier.py src/heralisz-mammoth mods/heraliz-boss

Copie (ne modifie JAMAIS la source) :
- Java : un fichier dont le chemin contient un dossier « client » -> src/client/java ; une classe de mixin listée dans la
  section « client » du *.mixins.json -> src/client/java (section « server » -> src/serveur/java) ; tout le reste ->
  src/commun/java. Le découpage fin (logique -> serveur) se fait ensuite à la main, classe par classe.
- Ressources : assets/ -> src/client/resources ; data/ -> src/serveur/resources ; *.mixins.json -> src/client/resources
  (src/commun/resources s'il y a des mixins communs) ; META-INF/mods.toml et pack.mcmeta ne sont PAS copiés (le projet
  fusionné a les siens) ; tout autre fichier -> src/commun/resources.
- Un fichier déjà présent n'est pas écrasé (sauf --force) : relancer ne détruit pas le travail fait.
À la fin, liste les fichiers « commun » qui citent encore du code client (à brancher par un crochet, §4) : tant qu'il en
reste, le projet ne compile pas, et c'est voulu.
"""
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CLIENT_REF = re.compile(r"\b(net\.minecraft\.client\.|com\.mojang\.blaze3d\.|software\.bernie\.geckolib\.(renderer|model)\.)")


def mixins_par_cote(res):
    """{nom de classe complet: 'client' | 'serveur' | 'commun'} d'après les *.mixins.json."""
    cotes = {}
    for f in res.glob("*.mixins.json"):
        d = json.loads(f.read_text(encoding="utf-8"))
        pkg = d.get("package", "")
        for cle, cote in (("mixins", "commun"), ("client", "client"), ("server", "serveur")):
            for c in d.get(cle, []) or []:
                cotes[f"{pkg}.{c}"] = cote
    return cotes


def copier(src, dst, force, journal):
    if dst.exists() and not force:
        journal["gardes"] += 1
        return
    dst.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(src, dst)
    journal["copies"] += 1


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    force = "--force" in sys.argv
    if len(args) != 2:
        raise SystemExit(__doc__)
    source, cible = (Path(a) if Path(a).is_absolute() else ROOT / a for a in args)
    java, res = source / "src/main/java", source / "src/main/resources"
    cotes = mixins_par_cote(res)
    journal = {"copies": 0, "gardes": 0}
    repartition = {"commun": 0, "client": 0, "serveur": 0}
    a_brancher = []
    for f in sorted(java.rglob("*.java")):
        rel = f.relative_to(java)
        nom = ".".join(rel.with_suffix("").parts)
        if nom in cotes:
            cote = cotes[nom]
        elif "client" in rel.parts[:-1]:
            cote = "client"
        else:
            cote = "commun"
        repartition[cote] += 1
        dst = cible / "src" / cote / "java" / rel
        copier(f, dst, force, journal)
        if cote == "commun":
            texte = f.read_text(encoding="utf-8", errors="replace")
            paquet_client = ".".join(rel.parts[:3]) + ".client."  # fr.heralisz.<mod>.client.
            if CLIENT_REF.search(texte) or paquet_client in texte:
                a_brancher.append(str(rel))
    for f in sorted(res.rglob("*")):
        if not f.is_file():
            continue
        rel = f.relative_to(res)
        if rel.as_posix() in ("META-INF/mods.toml", "pack.mcmeta"):
            continue
        if rel.parts[0] == "assets":
            cote = "client"
        elif rel.parts[0] == "data":
            cote = "serveur"
        elif f.name.endswith(".mixins.json"):
            cote = "commun" if any(v == "commun" for v in cotes.values()) else "client"
        else:
            cote = "commun"
        copier(f, cible / "src" / cote / "resources" / rel, force, journal)
    print(f"[fusion] {source.name} -> {cible.name} : {journal['copies']} fichier(s) copiés, {journal['gardes']} déjà là (gardés)")
    print(f"[fusion] classes : {repartition['commun']} commun, {repartition['client']} client, {repartition['serveur']} serveur")
    if a_brancher:
        print(f"[fusion] {len(a_brancher)} classe(s) « commun » citent du code client (crochet à poser, §4) :")
        for r in a_brancher:
            print("   ", r)


if __name__ == "__main__":
    main()
