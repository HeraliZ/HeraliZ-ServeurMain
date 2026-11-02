"""Polices partagées (audit du 02/11, point 37) : une seule copie de DM Sans, d'Onest (cyrillique) et d'Oswald, dans le
noyau (modules/noyau/.../assets/heraliz_noyau/font, avec leurs licences OFL). Avant, chaque module en gardait sa copie
(25 copies identiques de chaque fichier, ~3,5 Mo dans les jars des joueurs).

    python outils/polices_partagees.py              fait le partage (sans danger si relancé)
    python outils/polices_partagees.py --essai      montre seulement ce qui changerait
    python outils/polices_partagees.py --verifier   code 1 s'il reste une copie ou une police qui ne pointe pas vers le noyau

Chaque police d'un module (assets/<espace>/font/*.json) qui cite « <espace>:<fichier>.ttf », pour un fichier du noyau
identique octet pour octet, cite à la place « heraliz_noyau:<fichier>.ttf » : Minecraft lit les ressources de tous les
mods ensemble, la police du module trouve le fichier dans le jar du noyau (livré à tous les joueurs). La copie du module
est retirée quand plus aucune police ne la cite, et ses licences (*_ofl.txt) quand le dossier n'a plus de police.
Une police différente (autre fichier, autre version) n'est jamais touchée. Les fichiers .json eux-mêmes restent dans
chaque module (tailles et réglages propres) ; outils/polices_nettes.py et outils/polices_cyrillique.py les relisent.
"""
import json
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NOYAU_ESPACE = "heraliz_noyau"
NOYAU_FONT = ROOT / "modules/noyau/src/client/resources/assets/heraliz_noyau/font"
LICENCES = ("dmsans_ofl.txt", "onest_ofl.txt", "oswald_ofl.txt")


def partagees():
    """Polices du noyau : nom -> contenu."""
    return {f.name: f.read_bytes() for f in sorted(NOYAU_FONT.glob("*.ttf"))}


def dossiers_polices():
    """Dossiers assets/<espace>/font de nos modules (hors noyau et hors build)."""
    for d in sorted((ROOT / "modules").glob("*/src/**/assets/*/font")):
        rel = d.relative_to(ROOT)
        if "build" in rel.parts or d.resolve() == NOYAU_FONT.resolve():
            continue
        yield d


def lire(f):
    return json.loads(f.read_text(encoding="utf-8"))


def main():
    essai = "--essai" in sys.argv
    verifier = "--verifier" in sys.argv
    noyau = partagees()
    if not noyau:
        raise SystemExit("[polices] aucune police dans %s" % NOYAU_FONT.relative_to(ROOT))
    for lic in ("dmsans_ofl.txt", "onest_ofl.txt"):
        if not (NOYAU_FONT / lic).is_file():
            raise SystemExit("[polices] licence %s absente du noyau" % lic)
    problemes, changees, retirees = [], 0, 0
    dossiers = list(dossiers_polices())
    # 1. polices (.json) : un fichier identique à celui du noyau est pris dans le noyau
    for d in dossiers:
        espace = d.parent.name
        for f in sorted(d.glob("*.json")):
            doc = lire(f)
            brut = f.read_bytes().decode("utf-8")
            change = False
            for p in doc.get("providers", []):
                if not isinstance(p, dict) or p.get("type") != "ttf":
                    continue
                ns, _, nom = str(p.get("file", "")).partition(":")
                if ns == NOYAU_ESPACE or nom not in noyau:
                    continue
                source = d.parent.parent / ns / "font" / nom if ns != espace else d / nom
                if not source.is_file():
                    # police d'un autre espace (autre module) : on la cherche dans tout le dépôt
                    trouvees = list((ROOT / "modules").glob("*/src/**/assets/%s/font/%s" % (ns, nom)))
                    source = trouvees[0] if trouvees else None
                if source is None or source.read_bytes() != noyau[nom]:
                    continue  # police différente de celle du noyau : gardée telle quelle
                # remplacement dans le texte : mise en forme et fins de ligne du fichier gardées
                brut = brut.replace('"%s:%s"' % (ns, nom), '"%s:%s"' % (NOYAU_ESPACE, nom))
                change = True
            if change:
                changees += 1
                if verifier:
                    problemes.append("%s : cite encore une copie au lieu de %s" % (f.relative_to(ROOT), NOYAU_ESPACE))
                elif not essai:
                    f.write_bytes(brut.encode("utf-8"))
                print("[polices] %s -> %s" % (f.relative_to(ROOT), NOYAU_ESPACE))
    # 2. copies que plus aucune police ne cite (toutes les polices de tous les modules relues)
    citees = set()
    for f in (ROOT / "modules").glob("*/src/**/assets/*/font/*.json"):
        if "build" in f.relative_to(ROOT).parts:
            continue
        try:
            for p in lire(f).get("providers", []):
                if isinstance(p, dict) and p.get("type") == "ttf":
                    citees.add(str(p.get("file", "")))
        except ValueError:
            problemes.append("%s : illisible" % f.relative_to(ROOT))
    for d in dossiers:
        espace = d.parent.name
        for f in sorted(d.glob("*.ttf")):
            if f.name in noyau and f.read_bytes() == noyau[f.name] and "%s:%s" % (espace, f.name) not in citees:
                retirees += 1
                if verifier:
                    problemes.append("%s : copie de la police du noyau" % f.relative_to(ROOT))
                elif not essai:
                    f.unlink()
                print("[polices] retirée : %s" % f.relative_to(ROOT))
        restantes = [f for f in d.glob("*.ttf") if not (f.name in noyau and f.read_bytes() == noyau[f.name]
                                                        and "%s:%s" % (espace, f.name) not in citees)]
        if not restantes:
            for lic in LICENCES:
                f = d / lic
                if f.is_file():
                    if verifier:
                        problemes.append("%s : licence sans police à côté" % f.relative_to(ROOT))
                    elif not essai:
                        f.unlink()
                    print("[polices] retirée : %s" % f.relative_to(ROOT))
    # 3. toute police qui cite le noyau doit y trouver son fichier
    for c in sorted(citees):
        ns, _, nom = c.partition(":")
        if ns == NOYAU_ESPACE and nom not in noyau:
            problemes.append("police citée absente du noyau : %s" % c)
    for p in problemes:
        print("[polices] PROBLÈME " + p)
    print("[polices] %d police(s) changée(s), %d copie(s) retirée(s)%s ; %d problème(s)"
          % (changees, retirees, " (essai : rien d'écrit)" if essai else "", len(problemes)))
    sys.exit(1 if problemes else 0)


if __name__ == "__main__":
    main()
