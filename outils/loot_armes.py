"""Armes, accessoires, armures et composants dans les conteneurs de HeraliZ Loot (T1 à T7).

Réécrit les tables modules/loot/src/serveur/resources/data/heraliz_loot/coffres/t1.json à t7.json :
- quatre familles, chacune avec son nombre moyen pour 100 conteneurs ouverts (POUR_100) :
  armes TaCZ (raretés : section « armes » de serveur/config/heraliz_loot/raretes.json),
  accessoires TaCZ (section « accessoires »), pièces de l'Armurerie (section « objets » ; jamais les
  tenues complètes kit_*, les combinaisons intégrales *_suit ni les ensembles Unique) et composants de
  HeraliZ Composants (rareté = couleur du nom, lue dans ComponentDefs.java ; jamais les Mythiques) ;
- PARTS : part de chaque rareté dans une famille, par palier. Tout peut sortir partout, mais le haut
  de gamme est très rare dans les petits paliers ; Unique (armes : les couteaux) seulement en T7 ;
- armes lourdes (précision, mitrailleuses, lance-grenades) LOURDES fois moins souvent que les autres
  armes de leur rareté ;
- les autres objets gardent leur poids d'origine (clé « _base »), multiplié par AUTRES selon leur
  catégorie (munitions : MUNITIONS par palier) : munitions, soins et nourriture ont leurs propres conteneurs (familles munitions, soins,
  nourriture) et sont donc plus rares dans les conteneurs T1 à T7 ;
- T7 (coffres d'événement : crash, boss…) : un peu de tout. Ses « autres » objets sont ceux du T6
  (nourriture, munitions, soins, grenades, sacs…) ; plus d'objets de Minecraft d'essai.
- fragments des cartes d'accès (HeraliZ Bunker) : FRAGMENTS_POUR_100, ajoutés sans changer les autres poids ;
- objets rares en plus (EXTRAS : fusée d'extraction Épique en T5 à T7), de la même façon.
Les anciens vêtements heralisz_armor (mod retiré) sont retirés des tables. À relancer après un
changement des raretés, des gunpacks, de l'Armurerie ou des composants.
`--simuler` : ouvre 100 conteneurs par palier (moyenne de 1 000 essais) et compte chaque famille.
"""
import glob, json, random, re, sys
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
COFFRES = RACINE / "modules/loot/src/serveur/resources/data/heraliz_loot/coffres"
CONFIG_COFFRES = RACINE / "serveur/config/heraliz_loot/coffres"
RARETES = RACINE / "serveur/config/heraliz_loot/raretes.json"
TACZ = RACINE / "serveur/tacz"
ARMES_JSON = RACINE / "serveur/config/heraliz_tactique/armes.json"
COMPOSANTS_DEFS = RACINE / "modules/equipement/src/commun/java/fr/heraliz/composants/ComponentDefs.java"

# nombre moyen pour 100 conteneurs ouverts
POUR_100 = {
    # 23/10 (choix du propriétaire) : armes, accessoires et armures montent petit à petit avec le palier
    "armes":       {1: 5, 2: 7, 3: 9, 4: 13, 5: 16, 6: 19, 7: 25},   # 25/10 : T4 à T6 un peu plus (avant 11 / 13 / 15)
    # plans de fabrication retirés du jeu le 21/10 (système de craft à refaire) : plus de famille « plans »
    "accessoires": {1: 4, 2: 6, 3: 8, 4: 10, 5: 12, 6: 14, 7: 18},
    "armures":     {1: 11, 2: 13, 3: 15, 4: 17, 5: 19, 6: 21, 7: 27},
    # composants un peu plus souvent que le reste (choix du propriétaire, 23/10)
    "composants":  {1: 50, 2: 50, 3: 50, 4: 50, 5: 50, 6: 50, 7: 55},
    # sacs à dos (choix du propriétaire, 22/10 : 0,1 % des coffres en T1, 0,5 % en T2 ; un seul sac par coffre)
    "sacs":        {1: 0.1, 2: 0.5, 3: 1, 4: 2, 5: 3, 6: 4, 7: 3},
}
# part de chaque rareté (en %) ; une arme Bleue est déjà une belle trouvaille
# 23/10 (choix du propriétaire) : beaucoup moins de vert et de bleu, plus de gris : une arme bleue est déjà très bien
PARTS = {
    1: {"commun": 92, "peu_commun": 7, "rare": 0.95, "epique": 0.04, "legendaire": 0.01},
    2: {"commun": 84, "peu_commun": 13.5, "rare": 2.3, "epique": 0.17, "legendaire": 0.03},
    3: {"commun": 72, "peu_commun": 23, "rare": 4.6, "epique": 0.35, "legendaire": 0.05},
    4: {"commun": 58, "peu_commun": 32, "rare": 9, "epique": 0.9, "legendaire": 0.1},
    5: {"commun": 46, "peu_commun": 37, "rare": 15, "epique": 1.75, "legendaire": 0.25},
    6: {"commun": 36, "peu_commun": 40, "rare": 20, "epique": 3.4, "legendaire": 0.6},
    7: {"commun": 12, "peu_commun": 33, "rare": 38, "epique": 12, "legendaire": 3.67, "unique": 1.33},  # Unique (couteaux) : 1 coffre sur 250
}
LOURDES = 0.35
# parts propres à une famille dans un palier (sinon PARTS)
PARTS_FAMILLE = {}
# sacs à dos : rareté selon leur nombre de cases (choix du propriétaire, 22/10), écrite aussi dans la section
# « objets » de raretes.json (couleur du nom). Préfixe de l'identifiant -> rareté ; le plus long préfixe l'emporte.
RARETE_SACS = {
    "backpack_school": "commun",                                  # 1x2
    "backpack": "peu_commun", "backpack_tactical": "peu_commun",  # 2x3, 3x2
    "backpack_sniper_case": "rare", "backpack_sports_bag": "rare",  # 4x2, 3x3
    "backpack_mountain_military_camo": "epique", "backpack_hiking": "epique",  # 5x3
    "backpack_military_sport_bag": "legendaire",                  # 5x4
}
SACS_HORS_LOOT = ("backpack", "backpack_school", "backpack_expand1", "backpack_expand2", "backpack_sniper_case_enderman",
                  "backpack_military_sport_bag_cow", "backpack_military_sport_bag_pig")  # jamais dans les coffres
# mêmes parts dans tous les paliers (choix du propriétaire, 22/10) : seul le nombre de sacs monte avec le palier
PARTS_SACS = {t: {"commun": 62, "peu_commun": 24, "rare": 10, "epique": 3.5, "legendaire": 0.5} for t in range(1, 8)}  # 23/10
PARTS_FAMILLE.update({("sacs", t): v for t, v in PARTS_SACS.items()})
SACS_JAVA = RACINE / "modules/sacs/src/commun/java/com/mrcrayfish/backpacked/core/ArkaniaZModItems.java"
# jerrican d'essence et chalumeau de réparation des véhicules : un peu plus rares
OUTILS_VEHICULE = {"ywzj_vehicle:fuel_tank": 0.75, "ywzj_vehicle:repair_tool": 0.75}
PARACHUTE = 0.19  # part du poids d'origine du parachute
# poids des autres objets (part de leur poids d'origine) selon un mot de leur catégorie
AUTRES = [("soins", 0.3), ("explosifs", 0.5), ("nourriture", 0.6), ("boissons", 0.6),
          ("rations", 0.6), ("plats", 0.6), ("produits_ferme", 0.6)]
# munitions : part de leur poids d'origine par palier (balles pour 100 conteneurs : environ 40 en T1 à 450 en T7)
MUNITIONS = {1: 2.0, 2: 1.07, 3: 0.129, 4: 0.067, 5: 0.053, 6: 0.061, 7: 0.0735}
# objets par conteneur (22/10 : moins de cases depuis que nourriture, soins et munitions ont quitté les coffres T1 à T7 ;
# environ autant d'objets « restants » qu'avant)
# « autres » objets (grenades, armes de mêlée d'ArkaniaZ, jerrican, chalumeau, parachute) pour 100 conteneurs ; le reste
# des cases est vide (choix du propriétaire, 22/10 : peu de grenades, une seule par coffre)
AUTRES_POUR_100 = {1: 4, 2: 6, 3: 10, 4: 10, 5: 12, 6: 14, 7: 16}
VIDE = {"item": "minecraft:air", "nombre": [1, 1], "_cat": "vide"}
# 23/10 : cases choisies pour que les nombres visés (POUR_100…) soient ceux du jeu malgré la 1re case jamais vide :
# T1 et T2 visent moins d'un objet par conteneur, donc une seule case (1 objet par conteneur) ; ensuite assez peu de
# cases pour que la part de cases pleines reste haute (la 1re case n'est relancée que 20 fois par le jeu)
EMPLACEMENTS = {1: [1, 1], 2: [1, 1], 3: [1, 2], 4: [1, 2], 5: [1, 2], 6: [1, 2], 7: [1, 3]}
# 23/10 (soir, choix du propriétaire) : plusieurs objets par conteneur, « 1 objet 1 coffre c'est relou » ; nombre
# d'objets tiré avec ces poids (clé « cases_poids » des tables, lue par TablesCoffres) ; T1 45/50/4/1 % pour 1/2/3/4,
# T6 de 4-5 à 11-12, T7 de 7-8 à 15-16. Moins de conteneurs actifs par ville en échange (facteur par niveau,
# ConfigLoot « facteurs_actifs », écrit d'après FACTEUR_TOTAL) : un peu moins d'objets par ville qu'avant.
CASES_POIDS = {
    1: {1: 45, 2: 50, 3: 4, 4: 1},
    2: {1: 30, 2: 45, 3: 20, 4: 4, 5: 1},
    3: {1: 15, 2: 35, 3: 33, 4: 13, 5: 4},
    4: {2: 20, 3: 35, 4: 28, 5: 12, 6: 4, 7: 1},
    5: {3: 15, 4: 25, 5: 25, 6: 18, 7: 10, 8: 5, 9: 2},
    6: {4: 6, 5: 12, 6: 16, 7: 17, 8: 15, 9: 12, 10: 10, 11: 7, 12: 5},
    7: {7: 5, 8: 9, 9: 12, 10: 14, 11: 14, 12: 13, 13: 11, 14: 9, 15: 7, 16: 6},
}
# objets par ville après le changement / avant, par palier. 23/10 : 0,9 partout (« pas grand chose mais quand même ») ;
# 24/10 (choix du propriétaire, « à l'aérodrome trop de conteneurs vides en T4-T5 ») : ×1,3 en T1-T2, ×1,5 en T3, ×2
# en T4, ×2,2 en T5-T6 ; T7 (événements) inchangé
# 25/10 (retour d'un joueur : « très peu de caisses d'armes, même à l'aérodrome ») : encore plus de conteneurs remplis à la
# fois, surtout en T5 et T6 (avant : 1,3 / 1,3 / 1,5 / 2,0 / 2,2 / 2,2)
FACTEUR_TOTAL = {1: 1.5, 2: 1.5, 3: 1.8, 4: 2.4, 5: 2.8, 6: 3.0, 7: 0.9}
# objets par conteneur avant le 23/10 au soir (mesurés sur les tables d'alors : 1 case en T1-T2, 1 à 2 en T3-T6, 1 à 3 en T7)
OBJETS_AVANT = {1: 1.0, 2: 1.0, 3: 1.14, 4: 1.21, 5: 1.29, 6: 1.39, 7: 1.63}


def cases_moyennes(t):
    d = CASES_POIDS[t]
    return sum(n * w for n, w in d.items()) / sum(d.values())
PRECISION = 20  # plus petit poids voulu : sinon tous les poids sont multipliés par 10

ORDRE = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]
ARME = "tacz:modern_kinetic_gun"
ACCESSOIRE = "tacz:attachment"
ARMURE = "heraliz_armory:"
COMPOSANT = "heraliz_composants:"
PLAN = "heralisz_utils:plan_"
# catégories « materiaux_* » (anciens matériaux de HeraliZ Utils et de Minecraft) retirées : les composants
# de fabrication sont ceux de HeraliZ Composants (famille « composants »)
RETIRES = ("heralisz_armor:", "simplefarming:")  # retirés des tables (mod retiré ; Simple Farming : choix du propriétaire)
RARETE_COMPOSANT = {"COMMON": "commun", "UNCOMMON": "peu_commun", "RARE": "rare", "EPIC": "epique",
                    "LEGENDARY": "legendaire"}  # MYTHIC : jamais dans le loot
ARMES_EXCLUES = {"cis:at_rpg_7_lvd",  # jamais dans le loot (choix du propriétaire)
                 "maxstuff:m82a2"}  # 25/10 : TaCZ refuse de charger cette arme (« rpm » à 0 dans le gunpack) ; elle tombait cassée
ARMURES_EXCLUES = {"heraliz_armory:plongee_suit"}  # jamais dans le loot (23/10 : tenue de plongée pas lootable pour l'instant)
# couteaux (skins heralisz_skins) : seulement ceux de 1, 2 ou 3 étoiles dans le loot, jamais les 4 étoiles (choix du
# propriétaire, 23/10) ; rangs lus dans ETOILES de modules/armes/outils/skins_couteaux.py (non cité = 4)
SKINS_COUTEAUX = RACINE / "modules/armes/outils/skins_couteaux.py"


def etoiles_couteaux():
    src = SKINS_COUTEAUX.read_text(encoding="utf-8")
    bloc = src[src.index("ETOILES = {}"):src.index("# couleur du nom selon le rang")]
    env = {}
    exec(bloc, env)
    return env["ETOILES"]


def couteau_4_etoiles(gid, etoiles):
    return gid.startswith("heralisz_skins:") and etoiles.get(gid.split(":", 1)[1], 4) >= 4
COMPOSANTS_EXCLUS = {"tonnelet_poudre", "bocal_poudre_ii", "bocal_poudre_iii", "bocal_poudre_iv", "plaque_recyclee", "plaque_soudee", "plaque_metal", "plaque_renforcee"}  # jamais dans le loot
COMPOSANTS_PLUS = {"clou": 3.0, "sac_ciment": 3.0, "equerres_acier": 3.0, "bocal_poudre": 7.0, "ferraille": 9.0}  # chance multipliée dans leur rareté
# composants ajoutés en plus dans un palier, hors raretés du palier (nombre pour 100 conteneurs)
# 26/10 (demande du propriétaire : « T7 trop pauvre, plus de matériaux de base ») : plus de matériaux en T7, et un peu en T5-T6
EN_PLUS = {5: {"planches": 2, "clou": 2},
           6: {"planches": 3, "clou": 2, "sac_ciment": 2},
           7: {"planches": 6, "clou": 5, "sac_ciment": 4, "pierres_taillees": 3, "fragments_metal": 2, "corde": 2}}
# munitions dans les conteneurs T1 à T7 (26/10, demande du propriétaire : « des munitions variées », plus seulement dans
# les conteneurs de munitions) : nombre pour 100 conteneurs, et quantité = celle de munitions.json x MUNITIONS_ECHELLE
MUNITIONS_POUR_100 = {1: 6, 2: 7, 3: 8, 4: 9, 5: 10, 6: 11, 7: 12}
MUNITIONS_ECHELLE = {1: 0.8, 2: 0.9, 3: 1.0, 4: 1.15, 5: 1.3, 6: 1.5, 7: 1.8}
MUNITIONS_HORS = ("tacz:40mm", "tacz:rpg_rocket", "tacz:50bmg", "Level:")  # jamais dans les conteneurs T1 à T7
IGNORES = RETIRES  # pour la simulation (le mod ignore ce qui manque)
# capsules du casino (HeraliZ Casino, objets heralizcasino:jeton_*), choix du propriétaire (22/10) : plus aucun héral
# dans le casino, les capsules se trouvent sur la carte et s'échangent contre des objets à la caisse. Nombre de piles
# pour 100 conteneurs, puis, par palier, (objet, quantité [min, max], part des piles) ; plus de valeur en haut.
# 23/10 (choix du propriétaire) : seulement les capsules 1, 5 et 10, et assez dures à trouver : 1 conteneur sur 40 en
# moyenne en contient (2,5 pour 100, tous paliers), la 1 courante, la 10 rare.
CAPSULE = "heralizcasino:"
CAPSULES_POUR_100 = {t: 2.5 for t in range(1, 8)}
CAPSULES = {
    1: [("jeton_1", [1, 3], 1.0)],
    2: [("jeton_1", [1, 4], 0.85), ("jeton_5", [1, 1], 0.15)],
    3: [("jeton_1", [1, 4], 0.7), ("jeton_5", [1, 2], 0.25), ("jeton_10", [1, 1], 0.05)],
    4: [("jeton_1", [2, 5], 0.6), ("jeton_5", [1, 2], 0.3), ("jeton_10", [1, 1], 0.1)],
    5: [("jeton_1", [2, 5], 0.5), ("jeton_5", [1, 2], 0.35), ("jeton_10", [1, 1], 0.15)],
    6: [("jeton_1", [3, 6], 0.4), ("jeton_5", [1, 3], 0.4), ("jeton_10", [1, 2], 0.2)],
    7: [("jeton_1", [3, 8], 0.3), ("jeton_5", [1, 3], 0.4), ("jeton_10", [1, 2], 0.3)],
}
# kits de réparation de HeraliZ Utils (24/10, choix du propriétaire) : ils réparent les armes ET les armures (+15 / +35 /
# +60 %). Nombre pour 100 conteneurs, puis part de chaque kit par palier (fortune, kit, armurier) ; plus de valeur en haut.
KIT = "heraliz_usure:"
# 26/10 (demande du propriétaire : « beaucoup trop nombreux ») : environ 6 fois moins (avant 2 / 2,5 / 3 / 3,5 / 4 / 4,5 / 5)
KITS_POUR_100 = {1: 0.3, 2: 0.35, 3: 0.45, 4: 0.55, 5: 0.65, 6: 0.75, 7: 1.0}
KITS = {
    1: [("kit_fortune", 0.85), ("kit_standard", 0.14), ("kit_armurier", 0.01)],
    2: [("kit_fortune", 0.80), ("kit_standard", 0.17), ("kit_armurier", 0.03)],
    3: [("kit_fortune", 0.70), ("kit_standard", 0.24), ("kit_armurier", 0.06)],
    4: [("kit_fortune", 0.60), ("kit_standard", 0.30), ("kit_armurier", 0.10)],
    5: [("kit_fortune", 0.50), ("kit_standard", 0.35), ("kit_armurier", 0.15)],
    6: [("kit_fortune", 0.40), ("kit_standard", 0.38), ("kit_armurier", 0.22)],
    7: [("kit_fortune", 0.30), ("kit_standard", 0.40), ("kit_armurier", 0.30)],
}
VALEUR_CAPSULE = {"jeton_1": 1, "jeton_5": 5, "jeton_10": 10}
# soins et nourriture de nouveau dans les conteneurs T1 à T7 (choix du propriétaire, 23/10), en plus de leurs propres
# conteneurs : nombre pour 100 conteneurs. Soins : ceux de la table soins<niveau> (niveau = palier, 6 au plus), mêmes
# parts de rareté ; nourriture : celle de nourriture.json, toujours Commun.
# 24/10 (choix du propriétaire) : soins moins rares, trouvables aussi hors des conteneurs de soins (environ ×1,6)
SOINS_POUR_100 = {1: 10, 2: 11, 3: 12, 4: 13, 5: 14, 6: 15, 7: 16}
NOURRITURE_POUR_100 = {1: 12, 2: 12, 3: 10, 4: 10, 5: 8, 6: 8, 7: 6}
# raretés imposées dans la section « objets » de raretes.json (choix du propriétaire, 23/10). Les grenades sont toutes
# le même objet (heralisz_tactical:throwable) : M67, RGN, flash, fumigène, molotov = Rare.
RARETES_IMPOSEES = {
    "heralisz_tactical:throwable": "rare",
    # objets d'événement à revendre (25/10) : du plus courant au plus rare
    "heraliz_evenements:dossier_classe": "rare", "heraliz_evenements:mallette_scellee": "epique",
    "heraliz_evenements:disque_dur_militaire": "legendaire",
    # objet de l'hélicoptère abattu (25/10) : le plus disputé, Légendaire
    "heraliz_evenements:boite_noire_helico": "legendaire",
    "ywzj_vehicle:fuel_tank": "peu_commun", "ywzj_vehicle:repair_tool": "rare", "ywzj_vehicle:parachute_pack": "epique",
    "heralizcasino:jeton_1": "commun", "heralizcasino:jeton_5": "peu_commun", "heralizcasino:jeton_10": "rare",
    # explosifs de CRac (23/10) : fabrication seulement, jamais dans le loot
    "crac:c4": "legendaire", "crac:explosive_charge": "epique",
    # kits de réparation (24/10) : fortune Rare, kit Épique, armurier Légendaire
    "heraliz_usure:kit_fortune": "rare", "heraliz_usure:kit_standard": "epique", "heraliz_usure:kit_armurier": "legendaire",
    # boîtes de munitions de TaCZ (24/10) : fer Rare, or Épique, diamant Légendaire (« Level » 0, 1, 2 ; RaretesObjets.boite)
    "tacz:ammo_box/0": "rare", "tacz:ammo_box/1": "epique", "tacz:ammo_box/2": "legendaire",
    # frappes aériennes (26/10, demande du propriétaire) : fabrication seulement (Table d'armurier, onglet Divers), jamais
    # dans le loot, les caisses ni les récompenses ; jumelles Légendaires, cartouches de plus en plus chères
    "airstrike:designator": "legendaire", "airstrike:cartridge_light": "rare", "airstrike:cartridge_heavy": "epique",
    "airstrike:cartridge_special": "legendaire",
    # cartes d'accès entières (26/10) : même rareté que leurs fragments
    "heralisz_bunker:keycard_green": "rare", "heralisz_bunker:keycard_blue": "epique", "heralisz_bunker:keycard_red": "legendaire",
}
# fragments des cartes d'accès de HeraliZ Bunker (24/10, demande du propriétaire) : 4 fragments numérotés par carte, les
# 4 DIFFÉRENTS donnent la carte (grille 2 x 2 ou clic droit). Chaque numéro est tiré à part : on peut trouver plusieurs
# fois le même, inutile sans les trois autres (voulu). Fragments de la carte verte Rare, bleue Épique, rouge Légendaire.
# Nombre de fragments (tous numéros) pour 100 conteneurs ouverts (vrais conteneurs, pas des parts) ; plus rare quand la
# carte est meilleure. Ajoutés aux tables sans changer les poids des autres objets (ajouter_fragments).
FRAGMENT = "heralisz_bunker:keycard_fragment_"
RARETE_FRAGMENTS = {"green": "rare", "blue": "epique", "red": "legendaire"}
# 24/10 (choix du propriétaire) : environ 1 000 conteneurs ouverts pour une carte verte complète, 2 000 pour une bleue,
# 4 500 pour une rouge (8,3 fragments en moyenne pour avoir les 4 numéros) ; une arme sort 10 à 50 fois pour 100
FRAGMENTS_POUR_100 = {
    "green": {2: 0.7, 3: 0.85, 4: 0.9, 5: 0.8},
    "blue": {4: 0.35, 5: 0.42, 6: 0.5},
    "red": {5: 0.15, 6: 0.2, 7: 1.2},
}
RARETES_IMPOSEES.update({f"{FRAGMENT}{c}_{n}": r for c, r in RARETE_FRAGMENTS.items() for n in range(1, 5)})
# objets rares ajoutés comme les fragments (sans changer les poids des autres) : nombre pour 100 conteneurs ouverts par
# palier, et rareté. 25/10 (choix du propriétaire) : la fusée d'extraction (Terminal d'extraction : ouvre une zone
# d'extraction n'importe où, à ciel ouvert) devient un loot Épique ; elle servira aussi au signal de la plateforme
# pétrolière (docs/EVENEMENT_OIL_RIG.md).
EXTRAS = {
    "heraliz_extraction:extraction_terminal": ("epique", {5: 0.4, 6: 0.7, 7: 3.0}),
}
RARETES_IMPOSEES.update({i: r for i, (r, _) in EXTRAS.items()})
# armes de mêlée de GeckoLib (machette, tuyau de plomb, batte, masse…) et nourriture : Commun
COMMUN_PREFIXES = ("geckolib:item_",)


def gun_id(e):
    m = re.search(r'GunId:"([^"]+)"', e.get("nbt", ""))
    return m.group(1) if m else None


def sacs():
    """identifiant -> rareté de chaque sac à dos du mod."""
    out = {}
    for i in sorted(set(re.findall(r'"backpacked", "([a-z0-9_]+)"', SACS_JAVA.read_text(encoding="utf-8")))):
        k = max((k for k in RARETE_SACS if i == k or i.startswith(k + "_")), key=len, default=None)
        if k and not i.startswith("backpack_expand"):  # objets vides gardés pour Curios
            out[i] = RARETE_SACS[k]
    return out


def ecrire_raretes_sacs():
    data = json.load(open(RARETES, encoding="utf-8"))
    obj = {k: v for k, v in data["objets"].items() if not k.startswith("backpacked:")}
    obj.update({"backpacked:" + i: r for i, r in sacs().items()})
    data["objets"] = obj
    with open(RARETES, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def raretes():
    return json.load(open(RARETES, encoding="utf-8"))["armes"]


def lourde(gid, familles):
    f = familles.get(gid)
    return f is None or f.startswith(("precision", "mitrailleuse", "minigun"))  # sans famille : lance-grenades


def lire_json(f):
    """JSON des gunpacks, lu comme TaCZ le lit (26/10 : commentaires // et virgules en trop acceptés ; avant, 36 armes sur
    120 tombaient en mode SEMI avec 1 balle parce que leur fiche n'était pas du JSON strict)."""
    try:
        t = open(f, encoding="utf-8-sig").read()
    except OSError:
        return None
    try:
        return json.loads(t)
    except ValueError:
        pass
    try:
        t = re.sub(r'("(?:\\.|[^"\\])*")|//[^\n]*', lambda m: m.group(1) or "", t)
        return json.loads(re.sub(r",(\s*[}\]])", r"\1", t))
    except ValueError:
        return None


def index_tacz(genre):
    out = {}
    for f in glob.glob(str(TACZ / f"*/data/*/index/{genre}/*.json")):
        ns = f.replace("\\", "/").split("/data/")[1].split("/")[0]
        d = lire_json(f)
        if d is not None:
            out[ns + ":" + Path(f).stem] = d
    return out


# balles dans le chargeur d'une arme trouvée dans un conteneur : 10 % de sa capacité, au moins 1, rien dans la chambre
# (26/10, demande du propriétaire ; avant : chargeur plein + une balle dans la chambre)
CHARGE_ARME = 0.10


def armes_du_jeu():
    """id -> nbt de l'arme trouvée (premier mode de tir, chargeur à CHARGE_ARME), d'après les gunpacks."""
    donnees = {}
    for f in glob.glob(str(TACZ / "*/data/*/data/guns/*.json")):
        ns = f.replace("\\", "/").split("/data/")[1].split("/")[0]
        d = lire_json(f)
        if d is not None:
            donnees[ns + ":" + Path(f).stem] = d
    armes = {}
    for gid, i in index_tacz("guns").items():
        if gid in ARMES_EXCLUES:
            continue
        d = donnees.get(i.get("data", ""), {})
        mode = (d.get("fire_mode") or ["semi"])[0].upper()
        try:
            balles = max(1, int(int(d.get("ammo_amount", 1)) * CHARGE_ARME))
        except (TypeError, ValueError):
            balles = 1
        armes[gid] = f'{{GunId:"{gid}",GunFireMode:"{mode}",GunCurrentAmmoCount:{balles},HasBulletInBarrel:0b}}'
    return armes


def familles():
    """famille -> {rareté: [(entrée sans poids, poids relatif)]}"""
    tout = json.load(open(RARETES, encoding="utf-8"))
    fam_armes = {g: v["famille"] for g, v in json.load(open(ARMES_JSON, encoding="utf-8"))["armes"].items()}
    jeu = armes_du_jeu()
    etoiles = etoiles_couteaux()
    out = {k: {} for k in POUR_100}

    def ajouter(f, r, entree, rel=1.0):
        if r in ORDRE:
            out[f].setdefault(r, []).append((entree, rel))

    for gid, r in sorted(tout["armes"].items()):
        if gid in jeu and not couteau_4_etoiles(gid, etoiles):
            ajouter("armes", r, {"item": ARME, "nombre": [1, 1], "nbt": jeu[gid], "_cat": "armes"},
                    LOURDES if lourde(gid, fam_armes) else 1)
    for f in (sorted(glob.glob(str(TACZ / "*/data/*/recipes/gun/*.json"))) if "plans" in POUR_100 else []):
        d = lire_json(f) or {}
        gid = (d.get("result") or {}).get("id")
        plans = [m["item"]["item"] for m in d.get("materials", []) if str(m.get("item", {}).get("item", "")).startswith(PLAN)]
        if gid in jeu and gid in tout["armes"] and plans and not any(e["item"] == plans[0] for r in out["plans"].values() for e, _ in r):
            ajouter("plans", tout["armes"][gid], {"item": plans[0], "nombre": [1, 1], "_cat": "plans"},
                    LOURDES if lourde(gid, fam_armes) else 1)
    accessoires = index_tacz("attachments")
    for aid, r in sorted(tout["accessoires"].items()):
        if aid in accessoires:
            ajouter("accessoires", r, {"item": ACCESSOIRE, "nombre": [1, 1], "nbt": f'{{AttachmentId:"{aid}"}}',
                                       "_cat": "accessoires"})
    for i, r in sorted(tout["objets"].items()):
        if i.startswith(ARMURE) and not i.startswith(ARMURE + "kit_") and not i.endswith("_suit") and r != "unique" and i not in ARMURES_EXCLUES:
            ajouter("armures", r, {"item": i, "nombre": [1, 1], "_cat": "armures"})
    for i, r in sacs().items():
        if i not in SACS_HORS_LOOT:
            ajouter("sacs", r, {"item": "backpacked:" + i, "nombre": [1, 1], "_cat": "sacs"})
    for i, cat, r, pile in re.findall(r'new Def\("([a-z_0-9]+)", "([^"]*)", HeralizRarity\.([A-Z]+), (\d+)\)',
                                      COMPOSANTS_DEFS.read_text(encoding="utf-8")):
        if r in RARETE_COMPOSANT and i not in COMPOSANTS_EXCLUS:
            # quantité selon le palier : écrite par ecrire() (quantite_composant), la case garde ici sa sorte
            ajouter("composants", RARETE_COMPOSANT[r], {"item": COMPOSANT + i, "nombre": [1, 1], "_cat": "composants",
                                                        "_sorte": sorte_composant(i, cat)},
                    COMPOSANTS_PLUS.get(i, 1.0))
    return out


# quantités des composants par case (26/10, demande du propriétaire : « trop de stacks de 1 : une balle, une ferraille,
# un objet gris ») : (min, max, suite) par sorte, rareté et palier. suite = None : quantité tirée au hasard entre min et
# max ; sinon chance de chaque exemplaire en plus du minimum (clé « suite » des tables, TablesCoffres) : les gros tas
# restent rares. Composants Épiques (niveau III) : 2, 3, 4 ou 5 d'un coup est exceptionnel mais possible.
# Sortes : « pieces » = composants de fabrication à niveaux (canon, ressort, tissu, batterie...), « base » = matériaux de
# construction et de barricade (clous, corde, planches, ciment, métal...), « vente » = objets à revendre.
def _q(*paliers):
    """7 valeurs (T1 à T7) -> {palier: valeur}."""
    return dict(zip(range(1, 8), paliers))


QUANTITES = {
    "pieces": {
        "peu_commun": _q((1, 2, None), (1, 2, None), (1, 3, None), (1, 3, None), (2, 4, None), (2, 4, None), (2, 6, None)),
        "rare": _q((1, 2, .25), (1, 2, .25), (1, 2, .3), (1, 3, .3), (1, 3, .3), (1, 3, .35), (1, 4, .35)),
        "epique": _q((1, 3, .06), (1, 3, .06), (1, 3, .08), (1, 3, .08), (1, 5, .1), (1, 5, .12), (1, 5, .15)),
        "legendaire": _q((1, 2, .04), (1, 2, .04), (1, 2, .05), (1, 2, .05), (1, 2, .06), (1, 3, .07), (1, 3, .1)),
    },
    "base": {
        "commun": _q((2, 5, None), (2, 6, None), (3, 8, None), (3, 10, None), (4, 12, None), (5, 14, None), (8, 20, None)),
        "peu_commun": _q((2, 5, None), (2, 6, None), (3, 8, None), (3, 10, None), (4, 12, None), (5, 14, None), (8, 20, None)),
        "rare": _q((1, 3, None), (1, 3, None), (2, 5, None), (2, 5, None), (2, 8, None), (3, 8, None), (4, 12, None)),
        "epique": _q((1, 2, .3), (1, 2, .3), (1, 3, .35), (1, 4, .4), (1, 4, .4), (2, 6, None), (2, 8, None)),
        "legendaire": _q((1, 2, .15), (1, 2, .15), (1, 2, .2), (1, 3, .2), (1, 3, .25), (1, 4, .3), (1, 5, .4)),
    },
    "vente": {
        "commun": _q((1, 2, None), (1, 2, None), (1, 2, None), (1, 3, None), (1, 3, None), (1, 3, None), (2, 4, None)),
        "peu_commun": _q((1, 2, .3), (1, 2, .3), (1, 2, .3), (1, 2, None), (1, 2, None), (1, 2, None), (1, 3, None)),
        "rare": _q((1, 1, None), (1, 1, None), (1, 2, .15), (1, 2, .15), (1, 2, .2), (1, 2, .2), (1, 2, None)),
        "epique": _q(*[(1, 1, None)] * 6, (1, 2, .2)),
        "legendaire": _q(*[(1, 1, None)] * 7),
    },
}


def sorte_composant(i, cat):
    if "vente" in cat:
        return "vente"
    if "construction" in cat or "barricades" in cat:
        return "base"
    return "pieces"


def quantite_composant(e, r, t):
    """Entrée de composant avec sa quantité du palier t (copie)."""
    n = {k: v for k, v in e.items() if k != "_sorte"}
    q = QUANTITES.get(e.get("_sorte", "pieces"), {}).get(r, {}).get(t)
    if q:
        n["nombre"] = [q[0], q[1]]
        if q[2] is not None and q[1] > q[0]:
            n["suite"] = q[2]
    return n


def de_famille(e):
    return e.get("_cat") in POUR_100 or e.get("_cat") in ("capsules", "fragments") or e["item"].startswith((CAPSULE, KIT, FRAGMENT)) or e.get("_cat") == "kits" or e["item"] in (ARME, ACCESSOIRE) or e["item"].startswith((ARMURE, COMPOSANT, PLAN))


def mult_autre(e, t):
    cat = e.get("_cat", "")
    if "munitions" in cat:
        return MUNITIONS[t]
    if "parachute" in e["item"]:
        return PARACHUTE
    if e["item"] in OUTILS_VEHICULE:
        return OUTILS_VEHICULE[e["item"]]
    return next((m for mot, m in AUTRES if mot in cat), 1.0)


def ecrire_table(f, data):
    lignes = ["{"]
    for k, v in data.items():
        if k != "objets":
            lignes.append(f' {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)},')
    lignes.append(' "objets": [')
    lignes.append(",\n".join("  " + json.dumps(e, ensure_ascii=False) for e in data["objets"]))
    lignes.append(" ]")
    lignes.append("}")
    f.write_text("\n".join(lignes) + "\n", encoding="utf-8")
    # copie dans la config du serveur (24/10) : celle-là compte en jeu et se relit sans redémarrer (/heralizloot tables) ;
    # celle du jar sert seulement si la config manque
    CONFIG_COFFRES.mkdir(parents=True, exist_ok=True)
    (CONFIG_COFFRES / f.name).write_text("\n".join(lignes) + "\n", encoding="utf-8")


MELEE = RACINE / "modules/armes/src/serveur/resources/data/heralisz_melee/index/melee"


def melee_absente(e):
    """Arme de mêlée du mod tactique qui n'existe plus (fiche retirée)."""
    if e["item"] != "heralisz_tactical:melee":
        return False
    m = re.search(r'MeleeWeaponId:"heralisz_melee:([^"]+)"', e.get("nbt", ""))
    return not m or not (MELEE / (m.group(1) + ".json")).exists()


# nourriture, soins et munitions : seulement dans leurs propres conteneurs (nourriture.json, soins.json,
# munitions.json), jamais dans les coffres T1 à T7 (choix du propriétaire, 22/10)
HORS_COFFRES = ("nourriture", "boissons", "rations", "plats", "produits_ferme", "soins", "munitions")


def hors_coffres(e):
    cat = e.get("_cat", "")
    # petits objets inutiles (piles, radios, savon...) et outils d'ArkaniaZ (lampe, allumettes, canne à pêche) :
    # retirés (22/10) ; seuls restent les objets de valeur et composants de HeraliZ Composants
    if "petits_objets" in cat or ("outils" in cat and e["item"].startswith("geckolib:")):
        return True
    return any(m in cat for m in HORS_COFFRES)


def autres_de(data):
    out = []
    for e in data["objets"]:
        if e.get("_cat") == "vide" or de_famille(e) or e["item"].startswith(RETIRES + ("backpacked:",)) or hors_coffres(e) or melee_absente(e) or e.get("_cat", "").startswith("materiaux"):
            continue
        # 25/10 : objets de EXTRAS remis par ajouter_extras à chaque passage (avant, ils s'ajoutaient une fois de plus)
        if e.get("_cat") == "extras" or e["item"] in EXTRAS:
            continue
        e = dict(e)
        e.setdefault("_base", e["poids"])
        out.append(e)
    return out


def munitions_coffres(t):
    """Munitions des conteneurs T1 à T7 : celles de munitions.json (sans les MUNITIONS_HORS), poids relatifs (somme 1),
    quantités x MUNITIONS_ECHELLE[t] (au moins 2 balles)."""
    liste = json.loads((COFFRES / "munitions.json").read_text(encoding="utf-8"))["objets"]
    liste = [e for e in liste if e["item"] == "tacz:ammo" and e.get("poids", 0) > 0 and not any(h in e.get("nbt", "") for h in MUNITIONS_HORS)]
    s = sum(e["poids"] for e in liste)
    out = []
    k = MUNITIONS_ECHELLE[t]
    for e in liste:
        a, b = e.get("nombre", [1, 1])
        a, b = max(2, round(a * k)), max(2, round(b * k))
        out.append(({"item": e["item"], "nombre": [a, max(a, b)], "nbt": e["nbt"], "_cat": "munitions_coffres"}, e["poids"] / s))
    return out


def tables_survie(t):
    """(soins, nourriture) : entrées et poids relatifs (somme 1) pour le palier t."""
    soins = json.loads((COFFRES / f"soins{min(t, 6)}.json").read_text(encoding="utf-8"))["objets"]
    nourr = json.loads((COFFRES / "nourriture.json").read_text(encoding="utf-8"))["objets"]
    out = []
    for liste in (soins, nourr):
        liste = [e for e in liste if e.get("poids", 0) > 0 and not e["item"].startswith(RETIRES)]
        s = sum(e["poids"] for e in liste)
        out.append([(dict({k: v for k, v in e.items() if k not in ("poids", "_base")}, nombre=[1, 1]), e["poids"] / s) for e in liste])
    return out


def ecrire_raretes_imposees():
    data = json.load(open(RARETES, encoding="utf-8"))
    obj = data["objets"]
    obj.update(RARETES_IMPOSEES)
    for t in PARTS:
        for e in json.loads((COFFRES / f"t{t}.json").read_text(encoding="utf-8"))["objets"]:
            if e["item"].startswith(COMMUN_PREFIXES) and e["item"] not in RARETES_IMPOSEES:
                obj[e["item"]] = "commun"
    for e in json.loads((COFFRES / "nourriture.json").read_text(encoding="utf-8"))["objets"]:
        obj[e["item"]] = "commun"
    with open(RARETES, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def ecrire_facteurs(avant):
    """Facteur de conteneurs actifs par niveau (« facteurs_actifs ») : objets par ville x FACTEUR_TOTAL du palier. Écrit
    comme défaut de ConfigLoot ET dans serveur/defaultconfigs/heraliz_loot-server.toml (le fichier du monde garde sinon
    l'ancienne valeur : Forge n'y remet pas un nouveau défaut)."""
    racine = Path(__file__).resolve().parents[1]
    vals = ", ".join(f"{min(1.0, FACTEUR_TOTAL[t] * avant[t] / cases_moyennes(t)):.3f}" for t in sorted(CASES_POIDS))
    f = racine / "modules/loot/src/serveur/java/fr/heraliz/loot/ConfigLoot.java"
    s = f.read_text(encoding="utf-8")
    nouveau = re.sub(r"(// facteurs_actifs \(outils/loot_armes\.py\)\n\s*List\.of\()[^)]*(\))", lambda m: m.group(1) + vals + m.group(2), s)
    if nouveau != s:
        f.write_text(nouveau, encoding="utf-8")
    f = racine / "serveur/defaultconfigs/heraliz_loot-server.toml"
    s = f.read_text(encoding="utf-8")
    nouveau = re.sub(r"(?m)^(\s*facteurs_actifs = \[)[^\]]*(\])", lambda m: m.group(1) + vals + m.group(2), s)
    if nouveau == s and "facteurs_actifs" not in s:
        raise SystemExit(f"{f} : ligne facteurs_actifs introuvable")
    if nouveau != s:
        f.write_text(nouveau, encoding="utf-8")


def ajouter_fragments(objets, t):
    """Fragments de cartes d'accès du palier t, placés juste après la case vide. Leurs poids sont calculés sur le total
    de la table (les poids des autres objets ne changent pas) : FRAGMENTS_POUR_100 fragments pour 100 conteneurs, en
    comptant le nombre moyen d'objets par conteneur ; chaque numéro a la même chance."""
    parts = {c: n / 100 / cases_moyennes(t) for c, d in FRAGMENTS_POUR_100.items() for p, n in d.items() if p == t}
    if not parts:
        return objets
    q = sum(parts.values())
    total = sum(e["poids"] for e in objets) * q / (1 - q)
    ajout = []
    for c, part in parts.items():
        for n in range(1, 5):
            e = {"item": f"{FRAGMENT}{c}_{n}", "nombre": [1, 1], "_cat": "fragments", "poids": max(1, round(total * part / q / 4))}
            if t == 7:
                e["rarete"] = RARETE_FRAGMENTS[c]
            ajout.append(e)
    i = next((k + 1 for k, e in enumerate(objets) if e.get("_cat") == "vide"), len(objets))
    return objets[:i] + ajout + objets[i:]


def ajouter_extras(objets, t):
    """Objets de EXTRAS du palier t, placés juste après la case vide, comme les fragments : leur poids est calculé sur le
    total de la table pour donner le nombre voulu pour 100 conteneurs (les poids des autres objets ne changent pas)."""
    parts = {i: d[t] / 100 / cases_moyennes(t) for i, (_, d) in EXTRAS.items() if t in d}
    if not parts:
        return objets
    q = sum(parts.values())
    total = sum(e["poids"] for e in objets) * q / (1 - q)
    ajout = []
    for i, part in parts.items():
        e = {"item": i, "nombre": [1, 1], "_cat": "extras", "poids": max(1, round(total * part / q))}
        if t == 7:
            e["rarete"] = EXTRAS[i][0]
        ajout.append(e)
    k = next((k + 1 for k, e in enumerate(objets) if e.get("_cat") == "vide"), len(objets))
    return objets[:k] + ajout + objets[k:]


def ecrire():
    ecrire_raretes_sacs()
    ecrire_soins()
    fams = familles()
    tables = {t: json.loads((COFFRES / f"t{t}.json").read_text(encoding="utf-8")) for t in PARTS}
    autres = {t: autres_de(tables[t]) for t in PARTS}
    # T7 : les autres objets du T6 (plus d'objets de Minecraft d'essai)
    autres[7] = [dict(e) for e in autres[6]]
    tables[7]["_note"] = ("Coffres d'événement T7 (crash, boss...) : un peu de tout. "
                          "Écrit par outils/loot_armes.py (ne pas modifier à la main).")
    for t, parts in PARTS.items():
        data = tables[t]
        data["emplacements"] = [min(CASES_POIDS[t]), max(CASES_POIDS[t])]
        data["cases_poids"] = {str(n): w for n, w in sorted(CASES_POIDS[t].items())}
        cases = 1  # nombres « pour 100 » = parts des objets : chaque case tirée est pleine (presque jamais vide)
        p = {f: POUR_100[f][t] / 100 / cases for f in fams}
        base = [e["_base"] * mult_autre(e, t) for e in autres[t]]
        p["capsules"] = CAPSULES_POUR_100[t] / 100 / cases
        p["kits"] = KITS_POUR_100[t] / 100 / cases
        p["munitions_t"] = MUNITIONS_POUR_100[t] / 100 / cases
        # matériaux en plus (EN_PLUS) : comptés avec les autres parts (26/10 : ajoutés après, ils faisaient dépasser 100 %)
        for i, n in EN_PLUS.get(t, {}).items():
            p["plus_" + i] = n / 100 / cases
        p["soins"] = SOINS_POUR_100[t] / 100 / cases
        p["nourriture"] = NOURRITURE_POUR_100[t] / 100 / cases
        part_autres = AUTRES_POUR_100[t] / 100 / cases
        # chaque case tirée est pleine : les nombres « pour 100 » ne donnent plus que les parts de chaque famille
        # (case vide gardée à 0,1 % : poids jamais nul)
        k = 0.999 / (sum(p.values()) + part_autres)
        p = {f: v * k for f, v in p.items()}
        part_autres *= k
        p["vide"] = max(0.0, 1 - sum(p.values()) - part_autres)
        actifs = sum(base)
        rel = []
        for f, par_r in fams.items():
            parts = PARTS_FAMILLE.get((f, t), PARTS[t])
            somme_parts = sum(parts.get(r, 0) for r in par_r)
            for r, liste in par_r.items():
                if r not in parts:
                    continue
                s = sum(x for _, x in liste)
                for e, x in liste:
                    if f == "composants":
                        e = quantite_composant(e, r, t)
                    rel.append((e, f, parts[r] / somme_parts * x / s, r))
        for e, v in munitions_coffres(t):
            rel.append((e, "munitions_t", v, None))
        for i, quantite, part in CAPSULES[t]:
            rel.append(({"item": CAPSULE + i, "nombre": quantite, "_cat": "capsules"}, "capsules", part, None))
        for i, part in KITS[t]:
            rel.append(({"item": KIT + i, "nombre": [1, 1], "_cat": "kits"}, "kits", part, None))
        for f, liste in zip(("soins", "nourriture"), tables_survie(t)):
            for e, v in liste:
                rel.append((dict(e, _cat=f), f, v, None))
        for i, n in EN_PLUS.get(t, {}).items():
            e, r = next((e, r) for r, l in fams["composants"].items() for e, _ in l if e["item"] == COMPOSANT + i)
            rel.append((quantite_composant(e, r, t), "plus_" + i, 1.0, r))
        echelle = 1
        while True:
            reste = actifs * echelle / (1 - sum(p.values()))
            poids = [reste * p[f] * v for e, f, v, r in rel]
            if min(poids) >= PRECISION:
                break
            echelle *= 10
        objets = []
        for e, b in zip(autres[t], base):
            e["poids"] = round(b * echelle)
            if e["poids"] > 0:
                objets.append(e)
        objets.append(dict(VIDE, poids=round(reste * p["vide"])))
        for (e, f, v, r), w in zip(rel, poids):
            n = dict(e)
            n["poids"] = round(w)
            if t == 7 and r:  # la rareté inscrite ne sert qu'aux coffres T7 (animations) : sinon lue dans raretes.json
                n["rarete"] = r
            objets.append(n)
        objets = ajouter_fragments(objets, t)
        objets = ajouter_extras(objets, t)
        data["objets"] = objets
        ecrire_table(COFFRES / f"t{t}.json", data)
        print(f"T{t} : " + ", ".join(f"{sum(len(l) for r, l in fams[f].items() if r in PARTS_FAMILLE.get((f, t), PARTS[t]))} {f}" for f in fams))
    ecrire_raretes_imposees()
    ecrire_facteurs(OBJETS_AVANT)


# conteneurs de soins (soins1_... à soins6_...) : une table par niveau, soins.json = niveau 1 (secours).
# Rareté du soin tirée d'abord, puis le soin dans sa rareté (poids d'origine « _base »). Le gris reste toujours le
# plus fréquent ; le bleu et le violet montent un peu avec le niveau (choix du propriétaire, 22/10).
SOINS = RACINE / "serveur/config/heraliz_loot/raretes.json"
PARTS_SOINS = {  # 23/10 : moins de vert et de bleu (choix du propriétaire)
    1: {"commun": 68, "peu_commun": 22, "rare": 8, "epique": 2},
    2: {"commun": 63, "peu_commun": 24, "rare": 10, "epique": 3},
    3: {"commun": 58, "peu_commun": 26, "rare": 12, "epique": 4},
    4: {"commun": 53, "peu_commun": 27, "rare": 14, "epique": 6},
    5: {"commun": 48, "peu_commun": 28, "rare": 16, "epique": 8},
    6: {"commun": 44, "peu_commun": 29, "rare": 18, "epique": 9},
}


def ecrire_soins():
    ra = json.load(open(RARETES, encoding="utf-8"))["soins"]
    data = json.loads((COFFRES / "soins.json").read_text(encoding="utf-8"))
    base = []
    for e in data["objets"]:
        m = re.search(r'ConsumableId:"([^"]+)"', e.get("nbt", ""))
        if e["item"].startswith(RETIRES) or not m or ra.get(m.group(1)) not in PARTS_SOINS[1]:
            continue
        e = dict(e)
        e.setdefault("_base", e["poids"])
        e["nombre"] = [1, 1]
        base.append((e, ra[m.group(1)]))
    for niv, parts in PARTS_SOINS.items():
        somme = {r: sum(e["_base"] for e, x in base if x == r) for r in parts}
        objets = []
        for e, r in base:
            n = dict(e)
            n["poids"] = max(1, round(1000000 * parts[r] / 100 * e["_base"] / somme[r]))
            objets.append(n)
        d = dict(data)
        d["_note"] = (f"Conteneurs de soins de niveau {niv} (soins{niv}_...). Écrit par outils/loot_armes.py "
                      "(PARTS_SOINS ; ne pas modifier à la main).")
        d["emplacements"] = data["niveaux"][str(niv)]
        d.pop("niveaux", None)
        d["objets"] = objets
        ecrire_table(COFFRES / f"soins{niv}.json", d)
        if niv == 1:
            ecrire_table(COFFRES / "soins.json", dict(data, objets=objets, _note=d["_note"].replace(
                "niveau 1 (soins1_...)", "niveau 1 (soins1_...) et secours")))


def classer(e, ra):
    if e["item"] == ARME:
        return "armes", ra["armes"].get(gun_id(e), "?")
    if e["item"] == ACCESSOIRE:
        m = re.search(r'AttachmentId:"([^"]+)"', e.get("nbt", ""))
        return "accessoires", ra["accessoires"].get(m.group(1) if m else "", "?")
    if e.get("_cat") == "plans":
        return "plans", e.get("rarete") or ra["plans"].get(e["item"], "?")
    if e.get("_cat") == "sacs":
        return "sacs", ra["objets"].get(e["item"], "?")
    if e.get("_cat") in ("armures", "composants"):
        return e["_cat"], e.get("rarete") or ra["objets"].get(e["item"]) or ra["comp"].get(e["item"], "?")
    return None, None


def simuler(essais=1000):
    ra = json.load(open(RARETES, encoding="utf-8"))
    fams = familles()
    ra["comp"] = {e["item"]: r for r, l in fams["composants"].items() for e, _ in l}
    ra["plans"] = {e["item"]: r for r, l in fams.get("plans", {}).items() for e, _ in l}
    for t in PARTS:
        data = json.loads((COFFRES / f"t{t}.json").read_text(encoding="utf-8"))
        objs = [e for e in data["objets"] if not e["item"].startswith(IGNORES)]
        w = [e["poids"] for e in objs]
        c = {f: Counter() for f in POUR_100}
        balles = capsules = 0
        fragments = Counter()
        for _ in range(essais * 100):
            cp = {int(k): v for k, v in data["cases_poids"].items()}
            for _ in range(random.choices(list(cp), list(cp.values()))[0]):
                e = random.choices(objs, w)[0]
                f, r = classer(e, ra)
                if f:
                    c[f][r] += 1
                elif e["item"] == "tacz:ammo":
                    balles += random.randint(*e["nombre"])
                elif e["item"].startswith(CAPSULE):
                    capsules += random.randint(*e["nombre"]) * VALEUR_CAPSULE.get(e["item"][len(CAPSULE):], 0)
                elif e["item"].startswith(FRAGMENT):
                    fragments[e["item"][len(FRAGMENT):].split("_")[0]] += 1
        print(f"T{t} :", " | ".join(f"{sum(c[f].values()) / essais:.1f} {f} "
                                     + str({r: round(c[f][r] / essais, 2) for r in ORDRE if c[f][r]}) for f in c),
              f"| {balles / essais:.0f} balles | {capsules / essais:.0f} capsules (valeur)",
              "| fragments " + str({k: round(v / essais, 2) for k, v in fragments.items()}))


if __name__ == "__main__":
    simuler() if "--simuler" in sys.argv else ecrire()
