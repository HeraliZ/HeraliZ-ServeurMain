"""Équilibrage des armures de l'Armurerie : une seule protection par pièce, six raretés.

Choix du propriétaire (14/10) :
- une seule protection, la même contre tout (balles, explosions, corps à corps, morsures) ;
- radiation : seulement les combinaisons Hazmat ; froid : seulement les combinaisons contre le froid ; gaz : seulement
  les masques à gaz et les Hazmat ;
- les 23 niveaux (NV1 à NV23) regroupés en six raretés : Commun, Peu commun, Rare, Épique, Légendaire, Unique (deux
  ensembles seulement en Unique).

Protection d'une tenue complète par rareté (en %, tableau TENUE) : « corps » pour un tir au corps (pièces du corps
seulement), « tête » pour un tir à la tête (casque et visage seulement). Chaque ensemble atteint exactement ces totaux,
quel que soit son nombre de pièces : la part de chaque pièce suit POIDS_CORPS / POIDS_TETE, ramenée aux pièces que
l'ensemble a vraiment.

Écrit (rien d'autre n'est touché ; les durabilités, vitesses et lunettes de vision nocturne restent) :
- src/commun/java/fr/heraliz/armory/armor/ArmorCatalog.java (palier = rang de rareté 1 à 6, mêmes valeurs partout) ;
- src/commun/java/fr/heraliz/armory/armor/SuitCatalog.java ;
- src/commun/java/fr/heraliz/armory/armor/GearCatalog.java (rareté, protection, radiation) ;
- serveur/config/heraliz_loot/raretes.json : section « objets », clés heraliz_armory:* (le reste du fichier est gardé) ;
- --tableau : affiche le tableau des ensembles (pour CLAUDE.md et les comptes rendus).

Usage : python mods/heraliz-armory/outils/equilibrage_armures.py [--tableau]
"""
import argparse
import json
import os
import re
from collections import OrderedDict

ICI = os.path.dirname(os.path.abspath(__file__))
PROJET = os.path.normpath(os.path.join(ICI, ".."))
DEPOT = os.path.normpath(os.path.join(PROJET, "..", ".."))
ARMOR = os.path.join(PROJET, "src", "commun", "java", "fr", "heraliz", "armory", "armor")
RARETES_JSON = os.path.join(DEPOT, "serveur", "config", "heraliz_loot", "raretes.json")

RANGS = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]
# noms des raretés de l'Armurerie (couleur du nom, infobulle)
NOMS_ARMURERIE = ["common", "uncommon", "rare", "epic", "legendary", "unique"]

# Tenue complète : (corps, tête) en %
TENUE = {
    "commun": (12, 10),
    "peu_commun": (22, 18),
    "rare": (32, 26),
    "epique": (41, 34),
    "legendaire": (50, 42),
    "unique": (60, 50),
}

# Rareté de chaque ensemble (ancien niveau entre parenthèses)
ENSEMBLES = OrderedDict([
    ("citadin", "commun"),            # NV1
    ("rescape", "commun"),            # NV2
    ("ranger", "commun"),             # NV3
    ("woodland", "commun"),           # NV4
    ("nomade", "peu_commun"),         # NV5
    ("gardien", "peu_commun"),        # NV6
    ("cobra", "peu_commun"),          # NV7
    ("sentinelle", "peu_commun"),     # NV8
    ("nocturne_cheveche", "peu_commun"),  # NV8, casque seul
    ("rempart", "rare"),              # NV9
    ("vipere", "rare"),               # NV10
    ("siberie", "rare"),              # NV11
    ("scorpion", "rare"),             # NV12
    ("contrat", "epique"),            # NV13
    ("belier", "epique"),             # NV14
    ("nocturne_hulotte", "epique"),   # NV14, casque seul
    ("fennec", "epique"),             # NV15
    ("blizzard", "epique"),           # NV16
    ("barricade", "legendaire"),      # NV17
    ("beton", "legendaire"),          # NV18
    ("meute", "legendaire"),          # NV19
    ("nocturne_effraie", "legendaire"),  # NV19, casque seul
    ("corbeau", "legendaire"),        # NV20
    ("ours", "legendaire"),           # NV21
    ("nocturne_grand_duc", "legendaire"),  # NV24, casque seul
    ("mastodonte", "unique"),         # NV22
    ("titan", "unique"),              # NV23
])

# Part de chaque pièce dans la protection du corps / de la tête (ramenée aux pièces présentes)
POIDS_CORPS = {"chest": 30, "vest": 35, "legs": 16, "knees": 7, "hands": 6, "feet": 6}
POIDS_TETE = {"head": 80, "face": 20}

# Combinaisons : (rareté, protection partout, radiation, gaz)
COMBINAISONS = {
    "hazmat": ("peu_commun", 12, 100, True),
    "hazmat_renforce": ("epique", 36, 100, True),
    "arctique": ("peu_commun", 12, 0, False),
    "arctique_renforcee": ("epique", 36, 0, False),
    "plongee": ("rare", 6, 0, False),
}

# Objets de l'ancien HeralisZ Armor : (rareté, protection) par famille ; radiation 0 partout (Hazmat seulement)
def objet(id_, rarete_avant, fam):
    if fam == "gas_mask":
        return ("rare", 5) if "military" in id_ else ("peu_commun", 4)
    if fam == "ghillie":
        return ("legendaire", 0) if "full" in id_ else ("epique", 0)
    if fam == "armband":
        return ("commun", 0)
    return (None, 0)  # objets d'événement : rareté « event », sans protection


def repartir(total, poids):
    """Entiers dont la somme fait exactement total, proportionnels aux poids (plus grands restes)."""
    s = sum(poids.values())
    if s == 0:
        return {k: 0 for k in poids}
    brut = {k: total * v / s for k, v in poids.items()}
    ent = {k: int(v) for k, v in brut.items()}
    reste = total - sum(ent.values())
    for k in sorted(brut, key=lambda k: brut[k] - ent[k], reverse=True)[:reste]:
        ent[k] += 1
    return ent


LIGNE = re.compile(r'new PieceDef\("(?P<set>[a-z_]+)", "(?P<piece>[a-z]+)", (?P<tier>\d+), (?P<dur>\d+), (?P<speed>-?[\d.]+), '
                   r'(?P<b>\d+), (?P<m>\d+), (?P<bi>\d+), (?P<e>[\d.]+)f, (?P<r>\d+), (?P<gas>true|false), (?P<nvg>true|false)\)')


def lire_pieces():
    chemin = os.path.join(ARMOR, "ArmorCatalog.java")
    with open(chemin, encoding="utf-8") as f:
        texte = f.read()
    pieces = [m.groupdict() for m in LIGNE.finditer(texte)]
    if not pieces:
        raise SystemExit("ArmorCatalog.java : aucune pièce lue")
    return pieces


def ecrire_armures(pieces):
    par_ens = OrderedDict()
    for p in pieces:
        par_ens.setdefault(p["set"], []).append(p)
    valeurs = {}
    for ens, liste in par_ens.items():
        if ens not in ENSEMBLES:
            raise SystemExit(f"ensemble sans rareté : {ens}")
        corps, tete = TENUE[ENSEMBLES[ens]]
        presents = [p["piece"] for p in liste]
        vc = repartir(corps, {k: POIDS_CORPS[k] for k in presents if k in POIDS_CORPS})
        vt = repartir(tete, {k: POIDS_TETE[k] for k in presents if k in POIDS_TETE})
        for p in liste:
            valeurs[(ens, p["piece"])] = vc.get(p["piece"], vt.get(p["piece"], 0))
    lignes = []
    for p in pieces:
        v = valeurs[(p["set"], p["piece"])]
        rang = RANGS.index(ENSEMBLES[p["set"]]) + 1
        lignes.append(f'            new PieceDef("{p["set"]}", "{p["piece"]}", {rang}, {p["dur"]}, {p["speed"]}, {v}, {v}, {v}, {v}.0f, 0, false, {p["nvg"]})')
    texte = ("package fr.heraliz.armory.armor;\n\nimport java.util.List;\n\n"
             "/**\n * GENERE par outils/equilibrage_armures.py : ne pas modifier a la main (changer le script et le relancer).\n"
             " * Une seule protection par piece (balles = explosion = corps a corps = morsure), radiation 0 (Hazmat seulement),\n"
             " * aucun filtre a gaz ; palier = rang de rarete (1 commun ... 6 unique).\n */\n"
             "public final class ArmorCatalog {\n    private ArmorCatalog() {}\n\n"
             "    public static final List<PieceDef> PIECES = List.of(\n" + ",\n".join(lignes) + "\n    );\n}\n")
    with open(os.path.join(ARMOR, "ArmorCatalog.java"), "w", encoding="utf-8", newline="\n") as f:
        f.write(texte)
    return par_ens, valeurs


def ecrire_combinaisons():
    chemin = os.path.join(ARMOR, "SuitCatalog.java")
    with open(chemin, encoding="utf-8") as f:
        texte = f.read()
    for ens, (rarete, prot, rad, gaz) in COMBINAISONS.items():
        motif = re.compile(r'new PieceDef\("' + ens + r'", "suit", \d+, (\d+), (-?[\d.]+), \d+, \d+, \d+, [\d.]+f, \d+, (?:true|false), false, ([^)]*)\)')
        m = motif.search(texte)
        if not m:
            raise SystemExit(f"SuitCatalog.java : combinaison {ens} introuvable")
        rang = RANGS.index(rarete) + 1
        nouveau = (f'new PieceDef("{ens}", "suit", {rang}, {m.group(1)}, {m.group(2)}, {prot}, {prot}, {prot}, {prot}.0f, {rad}, '
                   f'{"true" if gaz else "false"}, false, {m.group(3)})')
        texte = texte[:m.start()] + nouveau + texte[m.end():]
    texte = texte.replace("//                                set                 piece   palier dur.  vitesse balles melee morsure expl.  rad. gaz    nvg    bonus",
                          "// valeurs ecrites par outils/equilibrage_armures.py (palier = rang de rarete, une seule protection partout)")
    with open(chemin, "w", encoding="utf-8", newline="\n") as f:
        f.write(texte)


GEAR = re.compile(r'new GearDef\("(?P<id>[a-z_]+)", "(?P<tab>[a-z]+)", "(?P<fam>[a-z_]+)", "(?P<kind>[a-z]+)", "(?P<slot>[a-z]+)", '
                  r'(?P<curio>"[a-z]+"|null), "(?P<rar>[a-z]+)", \d+, \d+, \d+, \d+, ')


def ecrire_objets():
    chemin = os.path.join(ARMOR, "GearCatalog.java")
    with open(chemin, encoding="utf-8") as f:
        texte = f.read()
    sortie = {}

    def remplacer(m):
        rarete, prot = objet(m.group("id"), m.group("rar"), m.group("fam"))
        nom = NOMS_ARMURERIE[RANGS.index(rarete)] if rarete else m.group("rar")
        sortie[m.group("id")] = rarete
        return (f'new GearDef("{m.group("id")}", "{m.group("tab")}", "{m.group("fam")}", "{m.group("kind")}", "{m.group("slot")}", '
                f'{m.group("curio")}, "{nom}", {prot}, {prot}, {prot}, 0, ')

    texte, n = GEAR.subn(remplacer, texte)
    if n == 0:
        raise SystemExit("GearCatalog.java : aucun objet lu")
    with open(chemin, "w", encoding="utf-8", newline="\n") as f:
        f.write(texte)
    return sortie


def ecrire_raretes(par_ens, objets):
    with open(RARETES_JSON, encoding="utf-8") as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    obj = OrderedDict((k, v) for k, v in data.get("objets", {}).items() if not k.startswith("heraliz_armory:"))
    for ens, liste in par_ens.items():
        r = ENSEMBLES[ens]
        for p in liste:
            obj[f"heraliz_armory:{ens}_{p['piece']}"] = r
        if not ens.startswith("nocturne_"):
            obj[f"heraliz_armory:kit_{ens}"] = r
    for ens, (r, _, _, _) in COMBINAISONS.items():
        obj[f"heraliz_armory:{ens}_suit"] = r
    for id_, r in objets.items():
        if r:
            obj[f"heraliz_armory:{id_}"] = r
    data["objets"] = obj
    with open(RARETES_JSON, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def tableau(par_ens, valeurs):
    print("| Rareté | Ensembles | Corps | Tête |")
    print("|---|---|---|---|")
    for r in RANGS:
        noms = [e for e, x in ENSEMBLES.items() if x == r]
        print(f"| {r} | {', '.join(noms)} | {TENUE[r][0]} % | {TENUE[r][1]} % |")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tableau", action="store_true")
    a = ap.parse_args()
    pieces = lire_pieces()
    par_ens, valeurs = ecrire_armures(pieces)
    ecrire_combinaisons()
    objets = ecrire_objets()
    ecrire_raretes(par_ens, objets)
    print(f"{len(pieces)} pièces, {len(par_ens)} ensembles, {len(COMBINAISONS)} combinaisons, {len(objets)} objets")
    if a.tableau:
        tableau(par_ens, valeurs)


if __name__ == "__main__":
    main()
