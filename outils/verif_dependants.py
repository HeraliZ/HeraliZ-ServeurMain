"""Les AUTRES mods installés trouvent-ils encore, du bon côté, chaque classe, méthode et champ qu'ils appellent dans un jar
fusionné et coupé (docs/METHODE_CLIENT_SERVEUR.md, retours du groupe ClansEtBases) ?

    python outils/verif_dependants.py <jar -client> <jar -serveur> <jar -complet> --paquets <p1,p2,...> --mods <dossier>...
    ex. python outils/verif_dependants.py dist/fusion/heraliz_clansetbases-2.0.0-{client,serveur,complet}.jar
          --paquets fr/heralisz/economie,fr/heralisz/clans,fr/heralisz/bases,fr/heralisz/tags
          --mods "livraison/mods client" "livraison/mods serveur"

Pour chaque jar des dossiers --mods (sauf ceux qui contiennent déjà ces paquets : les mods d'origine), relève les
références vers les paquets donnés, puis cherche chacune :
  - dans -serveur : références des classes « non client » du dépendant (hors paquet client et sans net/minecraft/client) ;
  - dans -client et -complet : toutes ses références (une classe commune d'un autre mod tourne aussi sur le client).
Un membre est cherché dans la classe puis ses ascendants du jar ; un ascendant Minecraft / Forge est supposé le fournir.
Une référence absente du -client vers une classe devenue serveur n'est grave que si ce code tourne sur le client :
regarder la classe qui l'appelle (souvent un banc d'essai de développement, qui demande le jar -complet).
Code de sortie : 1 si une référence manque dans -serveur (côté serveur) ou dans -complet.
"""
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from verif_split import Classe  # noqa: E402

BASES_JAVA = ("java/lang/Object", "java/lang/Record", "java/lang/Enum")


def charger(jar):
    z = zipfile.ZipFile(jar)
    return {n[:-6]: Classe(z.read(n)) for n in z.namelist() if n.endswith(".class")}


def a_membre(classes, prefs, nom, membre, desc, vus=None):
    vus = vus or set()
    if nom in vus:
        return False
    vus.add(nom)
    c = classes.get(nom)
    if c is None:
        return nom not in BASES_JAVA and not nom.startswith(prefs)
    if any(m == membre and d == desc for (m, d) in c.membres):
        return True
    return any(x and a_membre(classes, prefs, x, membre, desc, vus) for x in [c.mere] + c.interfaces)


def main():
    args = sys.argv[1:]
    if "--paquets" not in args or "--mods" not in args:
        raise SystemExit(__doc__)
    i, k = args.index("--paquets"), args.index("--mods")
    prefs = tuple(p.strip().rstrip("/") + "/" for p in args[i + 1].split(","))
    dossiers = [Path(a) for a in args[k + 1:] if not a.startswith("--")]
    jars = [Path(a) for a in args[:min(i, k)]]
    nos = {}
    for j in jars:
        cote = next((c for c in ("client", "serveur", "complet") if j.stem.endswith("-" + c)), None)
        if cote is None:
            raise SystemExit(f"{j.name} : côté inconnu (nom en -client / -serveur / -complet)")
        nos[cote] = charger(j)
    vus, graves, a_voir = set(), 0, 0
    for d in dossiers:
        for j in sorted(d.glob("*.jar")):
            if j.name in vus or j.resolve() in [x.resolve() for x in jars]:
                continue
            vus.add(j.name)
            try:
                z = zipfile.ZipFile(j)
            except zipfile.BadZipFile:
                continue
            noms = z.namelist()
            if any(n.startswith(prefs) and n.endswith(".class") for n in noms):
                continue  # un mod d'origine (ou le jar fusionné lui-même)
            refs = {}
            for n in noms:
                if not n.endswith(".class"):
                    continue
                data = z.read(n)
                if not any(p.encode() in data for p in prefs):
                    continue
                c = Classe(data)
                client = "/client/" in n or any(r[1].startswith(("net/minecraft/client/", "com/mojang/blaze3d/")) for r in c.refs)
                for g, cl, nm, ds in c.refs:
                    base = cl.lstrip("[")
                    base = base[1:-1] if base.startswith("L") and base.endswith(";") else base
                    if base.startswith(prefs):
                        refs.setdefault((g, base, nm, ds), set()).add((n[:-6], client))
            if not refs:
                continue
            print(f"\n=== {j.name} : {len(refs)} référence(s)")
            n_ko = 0
            for (g, base, nm, ds), qui in sorted(refs.items()):
                for cote, cl in nos.items():
                    concernes = sorted(q for q, client in qui if not (cote == "serveur" and client))
                    if not concernes:
                        continue
                    if base in cl and (g == "classe" or a_membre(cl, prefs, base, nm, ds)):
                        continue
                    n_ko += 1
                    grave = cote in ("serveur", "complet")
                    graves += grave
                    a_voir += not grave
                    etat = "classe absente" if base not in cl else "membre absent"
                    print(f"   {'ERREUR' if grave else 'à voir'} [{cote}] {etat} : {g} {base}.{nm}{ds}  <- {', '.join(concernes)[:200]}")
            if not n_ko:
                print("   tout est trouvé (client, serveur, complet)")
    print(f"\n[verif_dependants] {graves} erreur(s), {a_voir} référence(s) client à examiner")
    sys.exit(1 if graves else 0)


if __name__ == "__main__":
    main()
