"""Rareté de chaque arme, accessoire, munition, pièce d'armure et composant (retour du propriétaire du 14/11 : « sur
beaucoup d'accessoires il n'y a pas écrit de rareté, exemple Bipod V9 Grip »).

L'infobulle montre la rareté de tout objet qui en a une dans serveur/config/heraliz_loot/raretes.json (HeraliZ Loot,
ClientLoot), Commun compris : il suffit donc que chaque objet de ces familles y soit. Cet outil les liste avec leur
rareté et signale ceux qui n'en ont pas (gunpack, pièce d'armure ou composant ajouté sans relancer l'outil qui écrit sa
rareté) :
  - armes, accessoires et munitions des gunpacks (serveur/tacz) : sections « armes », « accessoires », « munitions »,
    écrites par outils/equilibrage_armes.py ;
  - boîtes de munitions, armes de mêlée et grenades de Tactique, armes de mêlée de GeckoLib, jumelles et munitions de
    frappe aérienne, explosifs de raid : section « objets », écrite par outils/loot_armes.py (RARETES_IMPOSEES) ;
  - pièces d'armure, tenues complètes, combinaisons, masques à gaz, ghillies, brassards : section « objets », écrite par
    modules/equipement/outils/equilibrage_armures.py ;
  - composants : section « objets », écrite par modules/equipement/outils/build.py.
Sans rareté, voulu (EXCLUS) : la munition factice des armes de mêlée des gunpacks et les objets d'événement de
l'Armurerie (chapeaux d'Halloween, sacs sur la tête : ligne « Événement », aucun n'est obtenable aujourd'hui).

Usage : python outils/raretes_objets.py [--liste] [--verifier]
  sans option : nombre d'objets par famille et par rareté ; --liste : chaque objet et sa rareté ; --verifier : code de
  sortie 1 s'il manque une rareté (outils/verifier.py).
"""
import json
import re
import sys
from collections import Counter, OrderedDict
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[1]
RARETES = DEPOT / "serveur/config/heraliz_loot/raretes.json"
TACZ = DEPOT / "serveur/tacz"
ARMURERIE = DEPOT / "modules/equipement/src/commun/java/fr/heraliz/armory/armor"
COMPOSANTS = DEPOT / "modules/equipement/src/commun/java/fr/heraliz/composants/ComponentDefs.java"
ORDRE = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]

# munition factice des armes de mêlée des gunpacks (couteaux) : jamais obtenable ; lui donner une rareté ferait aussi une
# recette de munitions dans outils/crafts.py (qui fabrique toutes les munitions de la section « munitions »)
EXCLUS = {"arkaniaz:melee_weapon"}
# armes de mêlée de GeckoLib (jar d'un autre auteur, ArkaniaZ) : machette, batte, tuyau de plomb, masse, matraque, hache
MELEE_GECKOLIB = ("item_machete", "item_bat", "item_lead_pipe", "item_sledge_hammer", "item_telescopic_baton", "item_fire_axe")


def gunpack(genre):
    """Identifiants des armes (« guns »), accessoires (« attachments ») ou munitions (« ammo ») de tous les gunpacks."""
    return sorted("%s:%s" % (f.parts[-4], f.stem) for f in TACZ.glob("*/data/*/index/%s/*.json" % genre))


def lire(nom):
    return (ARMURERIE / nom).read_text(encoding="utf-8")


def familles():
    """famille -> [(section de raretes.json, identifiant)]"""
    f = OrderedDict()
    f["armes des gunpacks"] = [("armes", i) for i in gunpack("guns")]
    f["accessoires"] = [("accessoires", i) for i in gunpack("attachments")]
    f["munitions"] = [("munitions", i) for i in gunpack("ammo") if i not in EXCLUS]
    f["boîtes de munitions"] = [("objets", "tacz:ammo_box/%d" % n) for n in range(3)]
    f["mêlée, grenades, explosifs, frappes"] = [("objets", i) for i in (
        "heralisz_tactical:melee", "heralisz_tactical:throwable", "crac:c4", "crac:explosive_charge",
        "airstrike:designator", "airstrike:cartridge_light", "airstrike:cartridge_heavy", "airstrike:cartridge_special")] + \
        [("objets", "geckolib:" + i) for i in MELEE_GECKOLIB]
    pieces = re.findall(r'new PieceDef\("([a-z_]+)", "([a-z]+)", ', lire("ArmorCatalog.java"))
    f["pièces d'armure"] = [("objets", "heraliz_armory:%s_%s" % p) for p in pieces]
    f["tenues complètes"] = [("objets", "heraliz_armory:kit_" + s) for s, kit in
                             re.findall(r'new SetDef\("([a-z_]+)", \d+, "[a-z]+", (true|false)\)', lire("SetCatalog.java")) if kit == "true"]
    f["combinaisons"] = [("objets", "heraliz_armory:%s_suit" % s) for s in re.findall(r'new PieceDef\("([a-z_]+)", "suit", ', lire("SuitCatalog.java"))]
    f["masques, ghillies, brassards"] = [("objets", "heraliz_armory:" + i) for i, tab in
                                         re.findall(r'new GearDef\("([a-z_]+)", "([a-z]+)", ', lire("GearCatalog.java")) if tab != "event"]
    f["composants"] = [("objets", "heraliz_composants:" + i) for i in
                       re.findall(r'new Def\("([a-z_0-9]+)", "[^"]*", HeralizRarity\.[A-Z]+, \d+\)', COMPOSANTS.read_text(encoding="utf-8"))]
    return f


def main():
    ra = json.loads(RARETES.read_text(encoding="utf-8"))
    manquants = []
    for nom, liste in familles().items():
        par = Counter()
        for section, i in liste:
            r = ra.get(section, {}).get(i)
            if r not in ORDRE:
                manquants.append((nom, section, i))
                r = "AUCUNE"
            par[r] += 1
            if "--liste" in sys.argv:
                print("  %-40s %-12s %s" % (nom, r, i))
        print("[raretes] %-36s %4d : %s" % (nom, len(liste), ", ".join("%d %s" % (par[r], r) for r in ORDRE + ["AUCUNE"] if par[r])))
    for nom, section, i in manquants:
        print("[raretes] SANS RARETÉ : %s (%s, section « %s » de raretes.json)" % (i, nom, section))
    if manquants:
        print("[raretes] relancer l'outil qui écrit cette rareté (voir l'aide de outils/raretes_objets.py)")
    print("[raretes] %d objet(s) sans rareté" % len(manquants))
    if "--verifier" in sys.argv and manquants:
        sys.exit(1)


if __name__ == "__main__":
    main()
