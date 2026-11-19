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

Symboles (19/11) : DM Sans et Onest n'ont pas ☢ ⚠ ❄ ✔ ✖ ☠ ★…, que Minecraft prenait dans sa police, posés trop bas.
Chaque police qui utilise DM Sans cite aussi « heraliz_noyau:symboles.ttf » (dessinée aux mesures de DM Sans par
modules/noyau/outils/symboles_police.py), juste après DM Sans et Onest, aux mêmes taille, oversample et décalage : le
fournisseur est recopié de celui de la police (mise en forme et fins de ligne du fichier gardées).
"""
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
NOYAU_ESPACE = "heraliz_noyau"
NOYAU_FONT = ROOT / "modules/noyau/src/client/resources/assets/heraliz_noyau/font"
LICENCES = ("dmsans_ofl.txt", "onest_ofl.txt", "oswald_ofl.txt")
SYMBOLES = NOYAU_ESPACE + ":symboles.ttf"
DM_SANS = ("dmsans_medium.ttf", "dmsans_bold.ttf")
ONEST = ("cyrillique_medium.ttf", "cyrillique_bold.ttf")


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


def elements(brut):
    """Début et fin, dans le texte, de chaque fournisseur de la liste « providers »."""
    j = brut.index("[", brut.index('"providers"'))
    elems, prof, debut, chaine, echap = [], 0, None, False, False
    for k in range(j + 1, len(brut)):
        c = brut[k]
        if chaine:
            if echap:
                echap = False
            elif c == "\\":
                echap = True
            elif c == '"':
                chaine = False
            continue
        if c == '"':
            chaine = True
        elif c in "{[":
            if prof == 0:
                debut = k
            prof += 1
        elif c in "}]":
            if prof == 0:
                break
            prof -= 1
            if prof == 0:
                elems.append((debut, k + 1))
    return elems


def symboles(f):
    """Police de symboles du noyau dans une police qui utilise DM Sans. Renvoie (texte corrigé ou None, problème ou
    None) : None, None quand la police n'utilise pas DM Sans ou qu'elle est déjà à jour."""
    doc = lire(f)
    fournisseurs = doc.get("providers", [])
    ttf = [isinstance(p, dict) and p.get("type") == "ttf" for p in fournisseurs]
    noms = [str(p.get("file", "")).partition(":")[2] if t else "" for p, t in zip(fournisseurs, ttf)]
    if not any(n in DM_SANS for n in noms):
        return None, None
    i = next(k for k, n in enumerate(noms) if n in DM_SANS)
    dm = fournisseurs[i]
    voulu = {"type": "ttf", "size": dm.get("size", 11.0), "oversample": dm.get("oversample", 1.0),
             "shift": dm.get("shift", [0.0, 0.0])}
    deja = [p for p, t in zip(fournisseurs, ttf) if t and p.get("file") == SYMBOLES]
    if deja:
        p = deja[0]
        actuel = {"type": "ttf", "size": p.get("size", 11.0), "oversample": p.get("oversample", 1.0),
                  "shift": p.get("shift", [0.0, 0.0])}
        if actuel != voulu:
            return None, "symboles.ttf n'a pas la taille, l'oversample ou le décalage de DM Sans"
        return None, None
    # juste après DM Sans et Onest
    fin = i
    while fin + 1 < len(fournisseurs) and ttf[fin + 1] and noms[fin + 1] in ONEST:
        fin += 1
    brut = f.read_bytes().decode("utf-8")
    elems = elements(brut)
    if len(elems) != len(fournisseurs) or len(elems) < 2:
        return None, "liste des fournisseurs illisible"
    # modèle : un fournisseur de la police aux mêmes réglages (Onest juste avant, sinon DM Sans), recopié tel quel
    modele = None
    for k in (fin, i):
        p = fournisseurs[k]
        if {c for c in p if c != "file"} <= set(voulu) and {c: p.get(c, voulu[c]) for c in voulu} == voulu:
            modele = k
            break
    if modele is None:
        return None, "aucun fournisseur à recopier pour symboles.ttf (réglages de DM Sans différents)"
    a, b = elems[modele]
    texte = re.sub(r'("file"\s*:\s*)"[^"]*"', lambda m: m.group(1) + '"%s"' % SYMBOLES, brut[a:b], count=1)
    separateur = brut[elems[0][1]:elems[1][0]]
    _, b = elems[fin]
    return brut[:b] + separateur + texte + brut[b:], None


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
    # 4. symboles du noyau dans toutes les polices qui utilisent DM Sans (noyau compris)
    for f in sorted((ROOT / "modules").glob("*/src/**/assets/*/font/*.json")):
        if "build" in f.relative_to(ROOT).parts:
            continue
        try:
            texte, probleme = symboles(f)
        except ValueError:
            continue  # illisible : déjà signalé plus haut
        if probleme:
            problemes.append("%s : %s" % (f.relative_to(ROOT), probleme))
        elif texte is not None:
            changees += 1
            if verifier:
                problemes.append("%s : symboles.ttf du noyau absent" % f.relative_to(ROOT))
            elif not essai:
                json.loads(texte)  # contrôle : le texte reste un JSON valable
                f.write_bytes(texte.encode("utf-8"))
            print("[polices] symboles ajoutés : %s" % f.relative_to(ROOT))
    for p in problemes:
        print("[polices] PROBLÈME " + p)
    print("[polices] %d police(s) changée(s), %d copie(s) retirée(s)%s ; %d problème(s)"
          % (changees, retirees, " (essai : rien d'écrit)" if essai else "", len(problemes)))
    sys.exit(1 if problemes else 0)


if __name__ == "__main__":
    main()
