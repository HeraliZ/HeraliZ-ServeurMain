"""Vérifie qu'un jar de mod Forge 1.20.1 est bien réobfusqué : toute référence à un champ ou une méthode de Minecraft
(net/minecraft/..., com/mojang/blaze3d/...) doit porter un nom SRG (f_123_ / m_123_), sauf les noms qui ne sont jamais
obfusqués (constantes d'enum, méthodes ajoutées par Forge, blaze3d, méthodes héritées de Netty ou de Java).

    python outils/verif_reobf.py --minecraft --verifier <jars>...                 (conseillé)
    python outils/verif_reobf.py --reference <jars connus bons>... --verifier <jars>...

--minecraft : chaque nom non SRG doit exister tel quel dans la classe visée (ou ses ascendants) de Minecraft CLIENT
              1.20.1 en noms SRG + Forge 47.4.0 + bibliothèques, lus dans le lanceur CurseForge (outils/chemins.py).
              Un nom officiel de Mojang (ex. Entity.getX) n'y existe pas : c'est un jar non réobfusqué.
--reference : ancienne méthode, moins sûre : les noms « légitimes » sont appris sur des jars connus pour marcher.

À lancer AVANT toute installation : un jar dont le build a été interrompu après la tâche « jar » (sans reobfJar) garde
les noms officiels et plante au chargement (ex. NoSuchFieldError: CREATIVE_MODE_TAB).
Code de sortie 1 si une référence suspecte est trouvée.
"""
import re
import sys
import zipfile
from pathlib import Path
import sys as _sys
_sys.path.insert(0, str(Path(__file__).resolve().parent))
import verif_split as vs  # noqa: E402

MC = ("net/minecraft/", "com/mojang/blaze3d/")
SRG = re.compile(r"^(f|m)_\d+_$")


def minecraft_disponible():
    """Vrai si Minecraft client en noms SRG est trouvé (lanceur CurseForge lancé une fois en Forge 1.20.1)."""
    return (vs.LANCEUR / "libraries" / f"net/minecraft/client/{vs.MC_SRG}/client-{vs.MC_SRG}-srg.jar").exists()


def references(jar):
    """Ensemble (classe, membre) des références Minecraft non SRG du jar (tables de constantes de ses classes)."""
    out = set()
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if not n.endswith(".class") or n.startswith("META-INF/"):
                continue
            for genre, cls, membre, _ in vs.Classe(z.read(n)).refs:
                if genre == "classe" or not cls.startswith(MC):
                    continue
                if membre in ("<init>", "<clinit>") or SRG.match(membre):
                    continue
                out.add((cls, membre))
    return out


def existe(monde, cls, membre, vus=None):
    """Le membre (n'importe quel descripteur) est déclaré dans la classe ou un de ses ascendants."""
    vus = set() if vus is None else vus
    if cls in vus:
        return False
    vus.add(cls)
    if cls.startswith(vs.JAVA):
        membres, parents = vs.classe_java(cls)
        if membres is None:
            return True
        return membre in membres or any(existe(monde, x, membre, vus) for x in parents + ["java/lang/Object"])
    c = monde.classe(cls)
    if c is None:
        return False
    if any(m == membre for m, _ in c.membres):
        return True
    return any(x and existe(monde, x, membre, vus) for x in [c.mere] + c.interfaces)


def main():
    args = sys.argv[1:]
    if "--verifier" not in args:
        raise SystemExit(__doc__)
    a_verifier = args[args.index("--verifier") + 1:]
    if "--minecraft" in args:
        if not minecraft_disponible():
            raise SystemExit("[verif_reobf] Minecraft en noms SRG introuvable (voir outils/chemins.py)")
        monde = vs.monde_client()
        legitime = lambda c, m: existe(monde, c, m)
    else:
        refs = args[args.index("--reference") + 1: args.index("--verifier")]
        appris = {m for j in refs for _, m in references(j)}
        print(f"noms non SRG légitimes appris : {len(appris)}")
        legitime = lambda c, m: m in appris
    ko = 0
    for j in a_verifier:
        sus = sorted((c, m) for c, m in references(j) if not legitime(c, m))
        print(f"\n{Path(j).name} : {len(sus)} référence(s) suspecte(s)")
        for c, m in sus[:40]:
            print(f"   {c}.{m}")
        ko += len(sus)
    sys.exit(1 if ko else 0)


if __name__ == "__main__":
    main()
