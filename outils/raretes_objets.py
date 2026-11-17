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
  - composants : section « objets », écrite par modules/equipement/outils/build.py ;
  - matières brutes et outils de HeraliZ Récolte (16/11) : section « objets », écrite par outils/loot_armes.py
    (RARETES_IMPOSEES, EXTRAS).
Sans rareté, voulu (EXCLUS) : la munition factice des armes de mêlée des gunpacks et les objets d'événement de
l'Armurerie (chapeaux d'Halloween, sacs sur la tête : ligne « Événement », aucun n'est obtenable aujourd'hui).

Noms (même retour, point 3 : « Epic Épique », la rareté écrite 3 fois sur le même objet ; elle n'est plus que le badge
de l'infobulle) : aucun nom d'objet ne contient le mot d'une rareté (« épique », « Legendary », « редкий »…), dans les
fichiers de langue français, anglais et russe de nos modules et des gunpacks.

Usage : python outils/raretes_objets.py [--liste] [--verifier]
  sans option : nombre d'objets par famille et par rareté ; --liste : chaque objet et sa rareté ; --verifier : code de
  sortie 1 s'il manque une rareté ou si un nom contient une rareté (outils/verifier.py).
"""
import json
import re
import sys
from collections import Counter, OrderedDict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from silencieux import lire as lire_json  # noqa: E402  (lecture tolérante des gunpacks : commentaires, virgules finales)

DEPOT = Path(__file__).resolve().parents[1]
RARETES = DEPOT / "serveur/config/heraliz_loot/raretes.json"
TACZ = DEPOT / "serveur/tacz"
ARMURERIE = DEPOT / "modules/equipement/src/commun/java/fr/heraliz/armory/armor"
COMPOSANTS = DEPOT / "modules/equipement/src/commun/java/fr/heraliz/composants/ComponentDefs.java"
RECOLTE = DEPOT / "modules/recolte/src/commun/java/fr/heraliz/recolte/objets/ObjetsRecolte.java"
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
    # 16/11 : matières brutes et outils de HeraliZ Récolte (objets enregistrés dans ObjetsRecolte.java)
    if RECOLTE.exists():
        f["récolte"] = [("objets", "heraliz_recolte:" + i) for i in
                        re.findall(r'ITEMS\.register\("([a-z_0-9]+)"', RECOLTE.read_text(encoding="utf-8"))]
    return f


# mots des raretés (et de l'ancienne « mythique ») en français, anglais et russe
MOTS_RARETE = re.compile(r"(?i)(épique|\bepique\b|\bepic\b|légendaire|\blegendaire\b|\blegendary\b|\brare\b|\bcommun\b|"
                         r"\bcommon\b|\buncommon\b|\bunique\b|mythique|\bmythic\b|\bэпическ|\bлегендарн|\bредк|\bобычн|"
                         r"\bнеобычн|\bуникальн|\bмифическ)")
# clé du nom d'un objet ou d'un bloc (« item.<mod>.<objet> »), d'une arme, d'un accessoire ou d'une munition des gunpacks
CLE_NOM = re.compile(r"^(item|block)\.[^.]+\.[^.]+$|\.(gun|attachment|ammo)\.[^.]+(\.name)?$")


def noms_avec_rarete():
    """[(fichier, clé, nom)] des noms d'objets qui contiennent le mot d'une rareté."""
    fichiers = sorted(set(DEPOT.glob("modules/*/src/*/resources/**/lang/*.json")) | set(TACZ.glob("*/assets/*/lang/*.json")))
    out = []
    for f in fichiers:
        if f.stem not in ("fr_fr", "en_us", "ru_ru"):
            continue
        d = lire_json(f) or {}
        for k, v in d.items():
            if isinstance(v, str) and CLE_NOM.search(k) and MOTS_RARETE.search(v):
                out.append((f.relative_to(DEPOT).as_posix(), k, v))
    return out


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
    noms = noms_avec_rarete()
    for f, k, v in noms:
        print("[raretes] NOM AVEC UNE RARETÉ : %s (%s, %s) : la rareté est le badge de l'infobulle, pas dans le nom" % (v, k, f))
    print("[raretes] %d nom(s) d'objet avec le mot d'une rareté" % len(noms))
    if "--verifier" in sys.argv and (manquants or noms):
        sys.exit(1)


if __name__ == "__main__":
    main()
