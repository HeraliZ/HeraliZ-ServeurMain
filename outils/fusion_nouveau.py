"""Crée un nouveau projet fusionné à partir du gabarit mods/_gabarit (docs/METHODE_CLIENT_SERVEUR.md §3).

    python outils/fusion_nouveau.py <dossier> <groupe> "<Nom affiché>" [--geckolib]
    ex. python outils/fusion_nouveau.py heraliz-tactique tactique "HeraliZ Tactique" --geckolib

- copie mods/_gabarit dans mods/<dossier> (refuse si le dossier existe) ;
- renomme : paquet fr.heraliz.gabarit -> fr.heraliz.<groupe>, mod porteur heraliz_gabarit -> heraliz_<groupe>,
  classes HeralizGabarit / ClientGabarit -> Heraliz<Groupe> / Client<Groupe>, jar heraliz_<groupe>, projet <dossier>,
  nom affiché ;
- --racine <dossier> : crée le projet ailleurs que dans fusion/ (essais) ;
- --geckolib : copie le GeckoLib du modpack (TEST SERVEUR - Ptero/mods/geckolib222-forge-1.20.1-4.4.7.jar) en
  libs/geckolib222-4.4.7-hzfusion.jar (compilation seulement, jamais embarqué).
Ensuite : outils/fusion_copier.py pour chaque mod à fusionner, puis la méthode (§3 à §8).
"""
import shutil
import sys
from pathlib import Path
import sys as _sys
_sys.path.insert(0, str(Path(__file__).resolve().parent))
import chemins  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
GABARIT = ROOT / "mods" / "_gabarit"
GECKOLIB = chemins.GECKOLIB
TEXTE = {".java", ".gradle", ".properties", ".toml", ".json", ".mcmeta"}


def main():
    argv = sys.argv[1:]
    racine = ROOT / "mods"
    if "--racine" in argv:  # essais : créer le projet ailleurs (ex. le scratchpad)
        i = argv.index("--racine")
        racine = Path(argv[i + 1])
        del argv[i:i + 2]
    args = [a for a in argv if not a.startswith("--")]
    if len(args) != 3:
        raise SystemExit(__doc__)
    dossier, groupe, nom = args
    if not groupe.isidentifier() or groupe.lower() != groupe:
        raise SystemExit("groupe : un mot en minuscules (ex. tactique)")
    cible = racine / dossier
    if cible.exists():
        raise SystemExit(f"{cible} existe déjà")
    classe = groupe[0].upper() + groupe[1:]
    remplacements = [
        ("fr.heraliz.gabarit", f"fr.heraliz.{groupe}"),
        ("heraliz_gabarit", f"heraliz_{groupe}"),
        ("HeralizGabarit", f"Heraliz{classe}"),
        ("ClientGabarit", f"Client{classe}"),
        ("heraliz-gabarit", dossier),
        ("HeraliZ · Gabarit", nom.replace("HeraliZ ", "HeraliZ · ", 1) if nom.startswith("HeraliZ ") else nom),
        ("HeraliZ · Template", nom.replace("HeraliZ ", "HeraliZ · ", 1) if nom.startswith("HeraliZ ") else nom),
        ("HeraliZ Gabarit", nom),
    ]
    for src in GABARIT.rglob("*"):
        rel = src.relative_to(GABARIT).as_posix()
        rel = rel.replace("fr/heraliz/gabarit", f"fr/heraliz/{groupe}").replace("heraliz_gabarit", f"heraliz_{groupe}")
        rel = rel.replace("HeralizGabarit", f"Heraliz{classe}").replace("ClientGabarit", f"Client{classe}")
        dst = cible / rel
        if src.is_dir():
            dst.mkdir(parents=True, exist_ok=True)
            continue
        dst.parent.mkdir(parents=True, exist_ok=True)
        if src.suffix in TEXTE:
            t = src.read_text(encoding="utf-8")
            for a, b in remplacements:
                t = t.replace(a, b)
            dst.write_text(t, encoding="utf-8", newline="\n")
        else:
            shutil.copy2(src, dst)
    if "--geckolib" in sys.argv:
        (cible / "libs").mkdir(exist_ok=True)
        shutil.copy2(GECKOLIB, cible / "libs" / "geckolib222-4.4.7-hzfusion.jar")
    print(f"[fusion] projet créé : {cible}")
    print(f"[fusion] mod porteur heraliz_{groupe} (paquet fr.heraliz.{groupe}), jar heraliz_{groupe}-<version>-client|serveur|complet.jar")
    print("[fusion] suite : python outils/fusion_copier.py src/<mod> fusion/" + dossier + "  (pour chaque mod), puis docs/METHODE_CLIENT_SERVEUR.md §3")


if __name__ == "__main__":
    main()
