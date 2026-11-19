"""Écrit ce que vendent et rachètent les marchands de HeraliZ Quêtes (clé « marchand » de leur fichier dans
serveur/config/heraliz_quetes/pnj/). On modifie les tableaux ci-dessous, puis :

    python outils/marchands.py

Le reste du fichier du PNJ (nom, apparence, phrases) n'est pas touché. Prix en hérals, à l'unité.
- soins : tous ceux du jeu, prix selon leur rareté (serveur/config/heraliz_loot/raretes.json, section « soins »), sauf
  ceux de PRIX_SOINS_A_PART ;
- nourriture : quelques aliments du mod de nourriture (geckolib222), toujours en bon état (FoodState 0), prix selon ce
  qu'ils nourrissent ;
- accessoires TaCZ : ceux de rareté Commun à Rare (section « accessoires »), sans les munitions spéciales ;
- armes et munitions (le Passeur, 19/11) : toutes les armes Commun et Peu commun des gunpacks (section « armes »), les
  Rares d'ARMES_RARES_PASSEUR, puis les munitions de leurs calibres ; prix PRIX_ARMES (rareté, genre) et PRIX_MUNITIONS ;
- matériaux de construction (Bâtisseur) : tableau MATERIAUX ;
- rachat : objets de valeur (tag heraliz_composants:vente), puis tous les composants (heraliz_composants:tous) ;
- échanges sans hérals (Contremaître, troc) : tableau CONTREMAITRE (clé « echanges »). Un objet reçu par un échange
  n'est jamais donné dans un autre échange (le serveur couperait l'échange : Echanges.coupe) ; l'outil le refuse.

Contrôle (règle des propriétaires du 25/10, pour toujours) : un objet qu'un marchand vend au prix P n'est jamais racheté
plus de 50 % de P (Marchands.prixRachat de HeraliZ Quêtes : min(prix de la règle, P / 2), plus petit P chez tous les
vendeurs, aussi pour les articles ajoutés en jeu au Registre), et le recycler ne rapporte pas plus : espérance des
composants revendus (ou hérals « rien ») au plus P / 2 (Recyclage.tropRentable, même calcul).
Le script refuse d'écrire si une revente ou un recyclage dépasse ces bornes sans que le serveur les plafonne.
19/11 (audit de l'économie) : comme le serveur (SaisonQuetes.calculerVentes), un objet reçu par un échange compte pour
P = 0 (jamais racheté), et le contrôle est fait deux fois : sans la bêta, puis avec l'économie de la bêta du fichier de
réglages du loot (section [beta] de serveur/defaultconfigs/heraliz_loot-server.toml : prix d'achat, rachat, recycleur).
Le Passeur (armes et munitions, 19/11) a une borne bien plus dure (controle_passeur) : revendu ou recyclé, un de ses
articles ne rend jamais plus de PASSEUR_RENDU_MAX (10 %) du prix payé (munitions : PASSEUR_RENDU_MAX_MUNITIONS, 20 %,
depuis qu'elles coûtent 4 à 8 hérals la balle), avec et sans l'économie de la bêta.

    python outils/marchands.py             écrit les fichiers (après le contrôle)
    python outils/marchands.py --controle  contrôle seulement les fichiers actuels (code 1 en cas d'erreur ; lancé par
                                           outils/verifier.py)
"""
import json
from pathlib import Path

RACINE = Path(__file__).resolve().parents[1]
PNJ = RACINE / "serveur" / "config" / "heraliz_quetes" / "pnj"
RARETES = json.loads((RACINE / "serveur" / "config" / "heraliz_loot" / "raretes.json").read_text(encoding="utf-8"))

PRIX_SOINS = {"commun": 30, "peu_commun": 80, "rare": 180, "epique": 400}
# 31/10 (décision du propriétaire) : soins vendus à un prix à part, au lieu de celui de leur rareté (avant : 30, Commun)
PRIX_SOINS_A_PART = {
    "heralisz_heal:condensed_milk": 90,  # lait concentré
    "heralisz_heal:vaseline": 90,
}
# soins retirés du jeu (outils/donnees/objets_retires.json, identifiants figés) : plus vendus (03/11 : Attelle et bandage,
# remplacé par le Bandage et l'Attelle, toujours en vente)
SOINS_RETIRES = set(json.loads((RACINE / "outils" / "donnees" / "objets_retires.json").read_text(encoding="utf-8"))["soins"])
# 28/10 (demande du propriétaire) : prix et ordre réglés en jeu au Registre du marchand le 24/10, repris comme base
# (avant 110 / 100 / 85 / 70 / 65 / 40)
NOURRITURE = [  # (objet, points de faim, prix)
    ("geckolib:food_mre", 8, 15),
    ("geckolib:food_spaggety", 8, 10),
    ("geckolib:food_soup", 7, 8),
    ("geckolib:food_sardines", 6, 10),
    ("geckolib:food_tuna", 6, 10),
    ("geckolib:food_deez", 4, 6),
]
# 28/10 (demande du propriétaire) : prix de vente de Jack multipliés par 4 (avant 60 / 150 / 350)
PRIX_ACCESSOIRES = {"commun": 240, "peu_commun": 600, "rare": 1400}
MATERIAUX = [  # matériaux de construction du Bâtisseur (heraliz_composants), prix à l'unité
    ("heraliz_composants:brindilles", 2),
    ("heraliz_composants:planches", 50),
    ("heraliz_composants:pierres_taillees", 100),
    ("heraliz_composants:fragments_metal", 250),
    ("heraliz_composants:metal_renforce", 400),
    ("crac:building_plan", 50),     # plan et marteau de CRac, au cas où (20/10)
    ("crac:building_hammer", 50),
    # 24/10 (revue de la boucle de jeu) : sans eux, une armoire perdue (raid, mort du chef) ne se remplaçait jamais, aucune
    # base ne pouvait ranger quoi que ce soit, et le cœur de clan (points de saison) n'existait qu'en créatif.
    # CRac garde ses règles : une armoire par joueur, un cœur par clan, posés seulement dans sa base.
    ("crac:claim_block", 250),    # 28/10 : prix réglé en jeu le 24/10, repris comme base (avant 500)
    ("crac:metal_crate", 300),
    ("crac:coeur_clan", 5000),    # 02/11 (choix du propriétaire, A322) : 5 000 au lieu de 1 000, jugé trop bon marché
    # 16/11 (HeraliZ Récolte, choix du propriétaire du 15/11, maquettes/recolte/PROPOSITION.md §4) : la hache (arbres) et
    # la pioche (gisements), Rares, aussi trouvées dans le loot T2 à T5 ; usées, elles cassent
    ("heraliz_recolte:hache", 1200),
    ("heraliz_recolte:pioche", 1200),
]
# jamais vendus ni reçus par un échange chez un marchand des fichiers : l'outil refuse de les écrire. 16/11 : la hache-pioche
# de survie (Légendaire) se trouve seulement dans le loot T6, T7 et les coffres des événements (choix du propriétaire :
# pas de pay2win)
JAMAIS_VENDUS = {"heraliz_recolte:hache_pioche"}
# 16/11 (HeraliZ Récolte, maquettes/recolte/PROPOSITION.md §2, choix du propriétaire) : le Contremaître du spawn échange
# les matières brutes récoltées sur la carte (arbres à abattre, gisements de pierre, de métal et de titane) contre les
# matériaux des bases de CRac, ceux que vend le Bâtisseur (MATERIAUX). Troc seulement : ni hérals, ni rachat. Cher exprès.
# 19/11 (barème de serveur/config/heraliz_recolte/recolte.json : deux fois plus d'arbres et de gisements, qui donnent
# moins chacun) : un arbre (6 bûches) vaut 1,2 planche, un gisement de pierre (6 à 9 pierres brutes) un peu moins d'une
# pierre taillée, un gisement de métal (4 à 8 minerais de métal) 0,6 fragment de métal, un gisement de titane (2 à 5
# minerais de titane) 0,44 métal renforcé, en moyenne. Pas de plafond par jour (choix du propriétaire du 19/11).
CONTREMAITRE = [  # (objet donné, nombre, objet reçu, nombre)
    ("heraliz_recolte:buche", 5, "heraliz_composants:planches", 1),
    ("heraliz_recolte:buche", 1, "heraliz_composants:brindilles", 5),
    ("heraliz_recolte:pierre_brute", 8, "heraliz_composants:pierres_taillees", 1),
    ("heraliz_recolte:minerai_fer", 10, "heraliz_composants:fragments_metal", 1),   # minerai de métal
    ("heraliz_recolte:minerai_rare", 8, "heraliz_composants:metal_renforce", 1),    # minerai de titane
]
# 19/11 (demande du propriétaire : « un PNJ qui vend directement des armes et des munitions : toutes les armes grises et
# vertes, et quelques bleues ; ça doit rester cher, mais un joueur riche, ou un débutant avec un peu d'argent, peut
# s'acheter une arme pas trop forte, et des balles s'il veut dépenser son argent ») : le Passeur (PNJ « passeur », fichier
# pnj/passeur.json), qui fait passer les armes du continent par la mer. Prix à l'unité, selon la rareté de l'arme (section
# « armes » de raretes.json) et son genre (famille de serveur/config/heraliz_tactique/armes.json, le mot avant « _ »).
# Repères (hérals) : une heure de fouille rapporte environ 2 300 à 3 200 en ville moyenne (T2-T3) et 12 500 à 15 700 en
# zone militaire (T5-T6), avant la bêta (outils/loot_zombies.py --simuler) ; Jack vend ses accessoires 240 / 600 / 1 400 ;
# Mila rachète une arme 15 / 30 / 60 (Commun / Peu commun / Rare, neuve). Un pistolet gris vaut donc environ une demi-heure
# de fouille, une arme bleue deux à trois heures en ville. Économie de la bêta : x 0,75 à l'achat, comme les autres.
PRIX_ARMES = {
    "commun":     {"pistolet": 1500, "smg": 2000, "pompe": 2000, "fusil": 2500},
    "peu_commun": {"pistolet": 3000, "smg": 4000, "pompe": 4000, "fusil": 5000},
    "rare":       {"pistolet": 7000, "smg": 9000, "pompe": 9000, "fusil": 10000},
}
# Rares vendues (choix du 19/11) : une par genre, la plus simple de son genre, pour un joueur qui débute : le Mk 23 (pistolet
# .45, coup par coup ou rafale courte), l'UMP-45 (pistolet-mitrailleur .45, déjà une récompense des quêtes du jour), le M870
# (fusil à pompe) et le SKS tactique (fusil semi-automatique de 10 coups). Jamais : précision, mitrailleuse, lance-grenades
# (GENRES_INTERDITS), fusil d'assaut automatique (AK-47, M4…), arme à silencieux intégré (MP5SD), pistolet automatique
# (G18C, 93R) ni .50 AE.
ARMES_RARES_PASSEUR = ["maxstuff:mk23", "tacz:ump45", "tacz:m870", "tacz:sks_tactical"]
GENRES_INTERDITS = ("precision", "mitrailleuse", "minigun")  # et les armes sans famille (lance-grenades)
ORDRE_GENRES = ["pistolet", "smg", "pompe", "fusil"]
# munitions des calibres de ses armes, à la balle (jamais les munitions spéciales du loot : 40 mm, roquettes, .50 BMG ;
# MUNITIONS_HORS de outils/loot_armes.py) : environ 4 fois (3,8 à 4,7) leur fabrication la moins chère à la Table
# d'armurier (9 mm : 2,2 hérals la balle en composants au prix de Mila), au moins MUNITIONS_FOIS_FABRICATION fois
# (fabriquer reste la voie bon marché)
# 19/11 : « entre 4 et 8 la balle », puis le même jour « sauf les balles de sniper, à 45 : moins on doit tirer de balles,
# plus c'est cher ; le plus cher, sniper et fusil semi-automatique balle par balle, entre 25 et 50 ; pompe 30 ; pistolet
# 6 » (demandes du propriétaire). Prix affichés pendant la bêta (prix x 0,75, arrondi) : 9 mm et .45 ACP (pistolets,
# pistolets-mitrailleurs) 6 ; .50 AE (gros pistolets), 5,56 et 7,62 x 39 (fusils d'assaut, mitrailleuses, aussi le SKS :
# le prix va avec le calibre, pas avec l'arme) 8 ; calibre 12 (pompes) 30 ; .308 (fusils de précision et fusils
# semi-automatiques : SVD, M14, Mk 11, AR-10…) et .338 (AWP, M200) 45. Le Passeur vend toutes ces munitions, aussi celles
# des armes qu'il ne vend pas (MUNITIONS_EN_PLUS) ; jamais les munitions spéciales du loot (40 mm, roquettes, .50 BMG :
# MUNITIONS_HORS).
PRIX_MUNITIONS = {"tacz:9mm": 8, "tacz:45acp": 8, "tacz:50ae": 11, "tacz:556x45": 11, "tacz:762x39": 11, "tacz:12g": 40,
                  "tacz:308": 60, "tacz:338": 60}
MUNITIONS_EN_PLUS = ["tacz:50ae", "tacz:556x45", "tacz:308", "tacz:338"]
MUNITIONS_FOIS_FABRICATION = 2.0
# acheter chez le Passeur puis revendre (Mila) ou recycler (spawn, carte) ne rend jamais plus que cette part du prix payé
PASSEUR_RENDU_MAX = 0.10
# munitions (19/11, de 6 à 45 hérals la balle) : Mila rachète toujours environ 0,5 à 1,2 héral la balle, soit au plus 15 %
# du prix pendant la bêta ; on y perd toujours plus de 80 %, aucune boucle : borne propre aux munitions
PASSEUR_RENDU_MAX_MUNITIONS = 0.20
RACHAT_VALEUR = {"commun": 2, "peu_commun": 8, "rare": 20, "epique": 50, "legendaire": 200, "mythique": 500}
RACHAT_COMPOSANTS = RACHAT_VALEUR  # mêmes prix que les objets de valeur
# 24/10 (revue de la boucle de jeu) : Mila rachète aussi armes, armures, sacs, grenades et accessoires trouvés sur la
# carte, à petits prix : toujours moins que les composants qu'il faut pour les fabriquer à la Table d'armurier (pas
# d'argent en boucle), et jamais un objet qu'un marchand vend (exclu par le serveur).
RACHAT_ARMES = {"commun": 15, "peu_commun": 30, "rare": 60, "epique": 120, "legendaire": 250, "mythique": 500}
# 02/11 (choix du propriétaire) : une arme est rachetée selon son état (usure) : prix x état, jamais moins de ce
# pourcentage du prix (clé « selon_etat_min » de la règle, Marchands.selonEtat de HeraliZ Quêtes). Avant : une arme à 0 %
# se vendait au prix d'une neuve. Le contrôle des 50 % ci-dessous compte le prix plein (arme neuve), la borne haute.
RACHAT_ARMES_ETAT_MIN = 10
RACHAT_ARMURES = {"commun": 5, "peu_commun": 12, "rare": 25, "epique": 60, "legendaire": 120, "mythique": 250}
RACHAT_SACS = {"commun": 8, "peu_commun": 15, "rare": 30, "epique": 60, "legendaire": 120, "mythique": 250}
RACHAT_GRENADES = {"commun": 5, "peu_commun": 10, "rare": 20, "epique": 40, "legendaire": 80, "mythique": 160}
RACHAT_ACCESSOIRES = {"commun": 5, "peu_commun": 10, "rare": 20, "epique": 50, "legendaire": 100, "mythique": 200}
ORDRE = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]


def soins():
    l = sorted(RARETES["soins"].items(), key=lambda kv: (ORDRE.index(kv[1]), kv[0]))
    return [{"objet": "heralisz_tactical:consumable", "nbt": '{ConsumableId:"%s"}' % k,
             "prix": PRIX_SOINS_A_PART.get(k, PRIX_SOINS[r])}
            for k, r in l if r in PRIX_SOINS and k not in SOINS_RETIRES]


def nourriture():
    return [{"objet": o, "nbt": "{FoodState:0}", "prix": p} for o, _, p in NOURRITURE]


def accessoires():
    l = [(k, r) for k, r in RARETES["accessoires"].items() if r in PRIX_ACCESSOIRES and ":ammo_mod" not in k]
    l.sort(key=lambda kv: (ORDRE.index(kv[1]), kv[0]))
    return [{"objet": "tacz:attachment", "nbt": '{AttachmentId:"%s"}' % k, "prix": PRIX_ACCESSOIRES[r]} for k, r in l]


ARMES_TACTIQUE = RACINE / "serveur" / "config" / "heraliz_tactique" / "armes.json"
TACZ = RACINE / "serveur" / "tacz"
RECETTES_MUNITIONS = TACZ / "heraliz_crafts" / "data" / "heraliz_crafts" / "recipes" / "munitions"


def genre_arme(gid):
    """« pistolet », « smg », « pompe », « fusil », « precision »… : famille de l'arme dans armes.json (mot avant « _ »), ""
    sans famille (lance-grenades)."""
    fam = json.loads(ARMES_TACTIQUE.read_text(encoding="utf-8"))["armes"].get(gid, {}).get("famille", "")
    return fam.split("_", 1)[0]


def fiches_armes():
    """Identifiant -> fiche de l'arme dans son gunpack (data/guns : munition, modes de tir), pour toutes les armes des
    gunpacks ; lues comme TaCZ les lit (commentaires acceptés, outils/loot_armes.py)."""
    import loot_armes as L
    donnees = {}
    for f in sorted(TACZ.glob("*/data/*/data/guns/*.json")):
        d = L.lire_json(f)
        if d is not None:
            donnees[f.as_posix().split("/data/")[1].split("/")[0] + ":" + f.stem] = d
    return {gid: donnees.get(i.get("data", ""), {}) for gid, i in L.index_tacz("guns").items()}


def armes_passeur(bavard=False):
    """Articles du Passeur (19/11) : armes Commun et Peu commun des gunpacks (jamais une arme cassée ou retirée du jeu,
    ARMES_EXCLUES de outils/loot_armes.py), les Rares d'ARMES_RARES_PASSEUR, puis les munitions de leurs calibres. Arme
    vendue neuve et vide (GunCurrentAmmoCount 0 : Mila et le recycleur la prennent telle quelle), premier mode de tir."""
    import loot_armes as L
    fiches, rar = fiches_armes(), RARETES["armes"]
    armes, calibres = [], set()
    for gid in sorted(g for g, r in rar.items() if r in ("commun", "peu_commun")) + ARMES_RARES_PASSEUR:
        if gid not in fiches or gid in L.ARMES_EXCLUES:
            if bavard:
                print(f"[marchands] passeur : {gid} pas vendu (absent des gunpacks, cassé ou retiré du jeu)")
            continue
        r, genre = rar.get(gid, "commun"), genre_arme(gid)
        if genre in GENRES_INTERDITS or not genre:
            if bavard:
                print(f"[marchands] passeur : {gid} pas vendu (arme de précision ou lourde)")
            continue
        prix = PRIX_ARMES.get(r, {}).get(genre)
        if prix is None:
            raise SystemExit(f"Refusé : {gid} ({r}, genre « {genre} ») n'a pas de prix dans PRIX_ARMES.")
        d = fiches[gid]
        mode = str((d.get("fire_mode") or ["semi"])[0]).upper()
        armes.append(((ORDRE.index(r), ORDRE_GENRES.index(genre) if genre in ORDRE_GENRES else 9, prix, gid),
                      {"objet": "tacz:modern_kinetic_gun", "nbt": '{GunId:"%s",GunFireMode:"%s",GunCurrentAmmoCount:0}' % (gid, mode),
                       "prix": prix}))
        if d.get("ammo"):
            calibres.add(d["ammo"])
    munitions = []
    for aid in sorted(calibres | set(MUNITIONS_EN_PLUS)):
        if aid in L.MUNITIONS_HORS:
            continue
        if aid not in PRIX_MUNITIONS:
            raise SystemExit(f"Refusé : munition {aid} (arme du Passeur) sans prix dans PRIX_MUNITIONS.")
        r = RARETES["munitions"].get(aid, "commun")
        munitions.append(((ORDRE.index(r), PRIX_MUNITIONS[aid], aid), {"objet": "tacz:ammo", "nbt": '{AmmoId:"%s"}' % aid,
                                                                     "prix": PRIX_MUNITIONS[aid]}))
    return [a for _, a in sorted(armes, key=lambda x: x[0])] + [m for _, m in sorted(munitions, key=lambda x: x[0])]


MARCHANDS = {
    "mila": {"type": "rachat", "rachat": [{"tag": "heraliz_composants:vente", "prix": RACHAT_VALEUR},
                                          {"tag": "heraliz_composants:tous", "prix": RACHAT_COMPOSANTS},
                                          {"objet": "tacz:modern_kinetic_gun", "prix": RACHAT_ARMES,
                                           "selon_etat_min": RACHAT_ARMES_ETAT_MIN},
                                          {"tag": "heraliz_quetes:rachat_armures", "prix": RACHAT_ARMURES},
                                          {"tag": "heraliz_quetes:rachat_sacs", "prix": RACHAT_SACS},
                                          {"objet": "heralisz_tactical:throwable", "prix": RACHAT_GRENADES},
                                          {"objet": "tacz:attachment", "prix": RACHAT_ACCESSOIRES}]},
    "jacqueline": {"type": "achat", "articles": soins()},
    "doyen": {"type": "achat", "articles": nourriture()},
    "jack": {"type": "achat", "articles": accessoires()},
    "batisseur": {"type": "achat", "articles": [{"objet": o, "prix": p} for o, p in MATERIAUX]},
    "passeur": {"type": "achat", "articles": armes_passeur()},
    "contremaitre": {"type": "troc", "echanges": [{"donne": {"objet": d, "nombre": nd}, "recoit": {"objet": r, "nombre": nr}}
                                                 for d, nd, r, nr in CONTREMAITRE]},
}

TAGS = RACINE / "modules" / "equipement" / "src" / "serveur" / "resources" / "data" / "heraliz_composants" / "tags" / "items"
MARCHANDS_JAVA = (RACINE / "modules" / "pnj" / "src" / "serveur" / "java" / "fr" / "heraliz" / "quetes" / "serveur"
                  / "Marchands.java")


TAGS_PNJ = RACINE / "modules" / "pnj" / "src" / "serveur" / "resources" / "data" / "heraliz_quetes" / "tags" / "items"
COFFRES = RACINE / "modules" / "loot" / "src" / "serveur" / "resources" / "data" / "heraliz_loot" / "coffres"


def ecrire_texte(f, texte):
    """Écrit texte (lignes en \\n) en gardant les fins de ligne du fichier déjà là (16/11 : lancé sous Linux, l'outil
    réécrivait en LF les fichiers des PNJ et les tags en CRLF, chaque ligne paraissait changée ; comme loot_armes.py)."""
    crlf = f.exists() and b"\r\n" in f.read_bytes()[:4096]
    f.write_bytes((texte.replace("\n", "\r\n") if crlf else texte).encode("utf-8"))


def tags_rachat():
    """Tags des armures et des sacs rachetés : tout ce que le loot T1 à T7 peut donner (entrées facultatives)."""
    familles = {"rachat_armures": set(), "rachat_sacs": set()}
    for f in sorted(COFFRES.glob("*.json")):
        for o in json.loads(f.read_text(encoding="utf-8")).get("objets", []):
            if o["item"].startswith("heraliz_armory:"):
                familles["rachat_armures"].add(o["item"])
            elif o["item"].startswith("backpacked:"):
                familles["rachat_sacs"].add(o["item"])
    TAGS_PNJ.mkdir(parents=True, exist_ok=True)
    for nom, ids in familles.items():
        valeurs = [{"id": i, "required": False} for i in sorted(ids)]
        ecrire_texte(TAGS_PNJ / (nom + ".json"), json.dumps({"replace": False, "values": valeurs}, indent=2) + "\n")
        print(f"tag heraliz_quetes:{nom} : {len(ids)} objets")


def tag(nom):
    """Identifiants d'un tag de heraliz_composants (vide s'il n'est pas de ce mod)."""
    if not nom.startswith("heraliz_composants:"):
        return set()
    f = TAGS / (nom.split(":", 1)[1] + ".json")
    return set(json.loads(f.read_text(encoding="utf-8")).get("values", [])) if f.exists() else set()


RECYCLAGE = RACINE / "serveur" / "config" / "heraliz_quetes" / "recyclage.json"
RECYCLAGE_JAVA = MARCHANDS_JAVA.with_name("Recyclage.java")
ORDRE_RANGS = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]


def rang(objet, nbt):
    """Rang de rareté (0 à 5, -1 sans rareté) comme RaretesObjets.de du serveur : données (soin, accessoire...) puis objet."""
    import re
    m = re.search(r'(\w+)\s*:\s*"([^"]+)"', nbt or "")
    if m:
        for sec in ("soins", "accessoires", "armes", "munitions"):
            if m.group(2) in RARETES.get(sec, {}):
                return ORDRE_RANGS.index(RARETES[sec][m.group(2)])
    r = RARETES["objets"].get(objet)
    return ORDRE_RANGS.index(r) if r in ORDRE_RANGS else -1


def ids_tag(nom):
    """Identifiants d'un tag d'objets (heraliz_composants ou heraliz_quetes), vide s'il est inconnu."""
    ns, n = nom.lstrip("#").split(":", 1)
    for f in RACINE.glob(f"modules/*/src/*/resources/data/{ns}/tags/items/{n}.json"):
        return {v if isinstance(v, str) else v["id"] for v in json.loads(f.read_text(encoding="utf-8")).get("values", [])}
    return set()


def arrondi(x):
    """Math.round de Java (moitié vers le haut), pas l'arrondi bancaire de Python."""
    import math
    return int(math.floor(x + 0.5))


def prix_regle(marchand, objet, nbt, facteur=1.0):
    """Prix de la première règle de rachat qui prend l'objet (0 : aucune), comme Marchands.prixRachat(m, s) ; facteur
    « rachat » de l'économie de la bêta (03/11) compris, comme Marchands.prixRachat(m, s, ventes)."""
    for r in marchand.get("rachat", []):
        if not (objet == r["objet"] if "objet" in r else objet in ids_tag(r["tag"])):
            continue
        g = rang(objet, nbt)
        nom = "commun" if g < 0 else "mythique" if g == 5 else ORDRE_RANGS[g]
        v = r["prix"].get(nom)
        if v is None and g == 5:
            v = r["prix"].get("unique")
        v = max(0, v or 0)
        return max(1, arrondi(v * facteur)) if v > 0 and facteur != 1.0 else v
    return 0


def facteurs_beta():
    """(achat, rachat, recyclage) de l'économie de la bêta (19/11 : section [beta] de heraliz_loot-server.toml, lue par
    outils/loot_beta.py, bornes de ConfigLoot), ou None quand la bêta est coupée (le serveur prend alors tout à 1)."""
    import loot_beta
    r = loot_beta.reglages()
    if not r["actif"]:
        return None
    return (min(1.0, max(0.25, r["prix_achat"])), min(2.0, max(1.0, r["prix_rachat"])), min(3.0, max(1.0, r["recyclage"])))


def controle(marchands, achat=None, rachat_f=None, recyclage=None):
    """Revente et recyclage de chaque article vendu : refus (SystemExit) si l'un rapporte plus que la règle des 50 %.
    19/11 : sans facteurs donnés, contrôle fait sans la bêta puis avec l'économie de la bêta (facteurs_beta)."""
    if achat is None:
        cas = [("sans la bêta", (1.0, 1.0, 1.0))]
        beta = facteurs_beta()
        if beta:
            cas.append(("avec la bêta (achat x%g, rachat x%g, recycleur x%g)" % beta, beta))
    else:
        cas = [("", (achat, rachat_f or 1.0, recyclage or 1.0))]
    erreurs, vues = [], set()
    for nom, (fa, fr, fy) in cas:
        if nom:
            print(f"[marchands] {nom} :")
        e, notes = analyser(marchands, fa, fr, fy, bavard=True)
        for n in notes:
            if n not in vues:
                vues.add(n)
                print("plafonné par le serveur : " + n)
        erreurs += e
    for e in erreurs:
        print("ERREUR : " + e)
    if erreurs:
        raise SystemExit("Refusé : une revente ou un recyclage rapporterait plus que la règle des 50 % du prix d'achat.")


def table_ventes(marchands, achat=1.0):
    """Comme SaisonQuetes.calculerVentes du serveur : (objet, nbt) -> plus petit prix payé chez un vendeur (facteur
    « achat » de la bêta compris : arrondi, 1 au moins) ; 19/11 : un objet reçu par un échange vaut 0 (jamais racheté ni
    recyclé avec profit), même s'il est aussi vendu."""
    ventes = {}
    for m in marchands.values():
        for e in m.get("echanges", []):
            ventes[(e["recoit"]["objet"], e["recoit"].get("nbt", ""))] = 0
    for m in marchands.values():
        if m.get("type") == "rachat":
            continue
        for a in m.get("articles", []):
            k = (a["objet"], a.get("nbt", ""))
            p = a["prix"] if achat == 1.0 or a["prix"] <= 0 else max(1, arrondi(a["prix"] * achat))
            ventes[k] = min(max(0, p), ventes.get(k, p))
    return ventes


def recus_par_echange(marchands):
    """Objets reçus par un échange (19/11 : rachetés 0 par le serveur)."""
    return {(e["recoit"]["objet"], e["recoit"].get("nbt", "")) for m in marchands.values() for e in m.get("echanges", [])}


def meilleur_rachat(objet, nbt, racheteurs, ventes, rachat_f=1.0):
    """Meilleur prix de rachat, comme Marchands.meilleurRachat du serveur : règle de chaque racheteur (facteur « rachat » de
    la bêta compris), plafonnée à la moitié du plus petit prix payé chez un vendeur (objet vendu sans données : il vaut pour
    toutes ; reçu par un échange : 0)."""
    brut = max([prix_regle(m, objet, nbt, rachat_f) for m in racheteurs.values()] + [0])
    achat = min([p for (o, n), p in ventes.items() if o == objet and (not n or n == nbt)], default=None)
    return brut if achat is None or not plafond_serveur() else min(brut, achat // 2)


_PLAFOND = []


def plafond_serveur():
    """Le serveur plafonne-t-il le rachat à la moitié du prix d'achat (Marchands.java) ? Lu une fois."""
    if not _PLAFOND:
        _PLAFOND.append("achat / 2" in MARCHANDS_JAVA.read_text(encoding="utf-8"))
    return _PLAFOND[0]


def controle_echanges(marchands):
    """Échanges des fichiers (clé « echanges ») : refus (SystemExit) si un objet reçu par un échange est donné dans un
    échange (chaîne, boucle, ou échange qui rend ce qu'il prend) : le serveur le couperait (Echanges.coupe)."""
    echanges = [(pid, e) for pid, m in marchands.items() for e in m.get("echanges", [])]
    recus = {e["recoit"]["objet"] for _, e in echanges}
    erreurs = [f"{pid} : {e['donne']['objet']} -> {e['recoit']['objet']} : l'objet donné est déjà reçu par un échange"
               for pid, e in echanges if e["donne"]["objet"] in recus]
    for e in erreurs:
        print("ERREUR : " + e)
    if erreurs:
        raise SystemExit("Refusé : pas de chaîne ni de boucle d'échanges.")
    if echanges:
        print(f"[marchands] échanges : {len(echanges)}, aucune chaîne ni boucle")


def controle_jamais_vendus(marchands):
    """Refus (SystemExit) si un objet de JAMAIS_VENDUS est vendu ou reçu par un échange chez un marchand."""
    erreurs = [f"{pid} : {o} jamais vendu ni échangé (JAMAIS_VENDUS)" for pid, m in marchands.items()
               for o in [a["objet"] for a in m.get("articles", [])] + [e["recoit"]["objet"] for e in m.get("echanges", [])]
               if o in JAMAIS_VENDUS]
    for e in erreurs:
        print("ERREUR : " + e)
    if erreurs:
        raise SystemExit("Refusé : un objet qui ne se vend jamais est chez un marchand.")


def controle_matieres_brutes(marchands):
    """16/11 (HeraliZ Récolte, choix du propriétaire) : refus (SystemExit) si une règle de rachat ou du recycleur prendrait
    une matière brute (MATIERES_BRUTES de outils/recyclage.py : échangées au Contremaître, jamais revendues)."""
    from recyclage import MATIERES_BRUTES
    rec = json.loads(RECYCLAGE.read_text(encoding="utf-8")) if RECYCLAGE.exists() else {"regles": []}
    erreurs = [f"{pid} rachèterait {o}" for o in MATIERES_BRUTES for pid, m in marchands.items()
               if m.get("type") == "rachat" and prix_regle(m, o, "") > 0]
    erreurs += [f"le recycleur prendrait {o}" for o in MATIERES_BRUTES if regle_recyclage(rec, o, "") is not None]
    for e in erreurs:
        print("ERREUR : " + e)
    if erreurs:
        raise SystemExit("Refusé : les matières brutes de la Récolte ne se revendent jamais (ni Mila, ni le recycleur).")


def analyser(marchands, achat=1.0, rachat_f=1.0, recyclage=1.0, bavard=False):
    """(erreurs, notes) du contrôle des 50 %, avec les facteurs de l'économie de la bêta (03/11, réglage [beta] de
    heraliz_loot-server.toml, Ponts.prixAchat / Marchands.prixRachat / Recyclage de HeraliZ Quêtes) : prix d'achat x
    achat (arrondi, 1 au moins), règles de rachat x rachat_f, chance et hérals « rien » du recycleur x recyclage."""
    plafond = "achat / 2" in MARCHANDS_JAVA.read_text(encoding="utf-8")
    recy_plafond = "tropRentable" in RECYCLAGE_JAVA.read_text(encoding="utf-8")
    # 19/11 : objets reçus par un échange à 0 (SaisonQuetes.calculerVentes) ; il faut alors les fichiers de tous les
    # marchands, troc compris
    ventes = table_ventes(marchands, achat)
    recus = recus_par_echange(marchands)
    racheteurs = {pid: m for pid, m in marchands.items() if m.get("type") == "rachat"}

    def rachat(objet, nbt):
        """Meilleur prix de rachat, plafond des 50 % compris (objet vendu sans données : il vaut pour toutes)."""
        return meilleur_rachat(objet, nbt, racheteurs, ventes, rachat_f)

    erreurs, notes = [], []
    for (objet, nbt), prix in sorted(ventes.items()):
        for pid, m in racheteurs.items():
            brut = prix_regle(m, objet, nbt, rachat_f)
            if brut * 2 <= prix:
                continue
            if plafond and (objet, nbt) in recus:
                notes.append(f"{objet}{nbt} : reçu par un échange, règle de {pid} {brut} -> racheté 0")
            elif plafond:
                notes.append(f"{objet}{nbt} : vendu {prix}, règle de {pid} {brut} -> racheté {min(brut, prix // 2)} (plafond des 50 %)")
            else:
                erreurs.append(f"{objet}{nbt} : vendu {prix}, {pid} le rachèterait {brut} (plus de 50 %)")
    if RECYCLAGE.exists():
        rec = json.loads(RECYCLAGE.read_text(encoding="utf-8"))
        # 31/10 : chaque profil du recycleur (spawn, et « carte » : même règles, chance relative en plus)
        profils = [("spawn", rec["chance"])] + [(nom, p.get("chance", rec["chance"])) for nom, p in rec.get("profils", {}).items()]
        profils = [(nom, min(1.0, c * recyclage)) for nom, c in profils]
        for nom_profil, chance in profils:
            recyclables, pire = 0, (0.0, "")
            for (objet, nbt), prix in sorted(ventes.items()):
                r = regle_recyclage(rec, objet, nbt)
                if r is None:
                    continue
                g = max(0, rang(objet, nbt))
                rend = r.get("rend") or r["rend_rarete"][min(len(r["rend_rarete"]) - 1, g)]
                somme = sum(rachat(c, "") for c in rend)
                rien = rec["herals_si_rien"][min(5, g)]
                rien = rien if recyclage == 1.0 or rien <= 0 else arrondi(rien * recyclage)
                esperance = chance * somme + (1 - chance) ** len(rend) * rien
                cout = prix * r.get("par", 1)
                recyclables += 1
                if cout > 0 and esperance * 2 / cout > pire[0]:
                    pire = (esperance * 2 / cout, f"{objet}{nbt}")
                if esperance * 2 > cout:
                    texte = (f"recycleur {nom_profil} : {objet}{nbt} : vendu {prix}, recyclé rapporte {esperance:.1f} en moyenne"
                             f" (borne : {cout / 2:.0f})")
                    (notes if recy_plafond else erreurs).append(texte + (" -> refusé par le recycleur" if recy_plafond else ""))
            if bavard:
                print(f"[marchands] recycleur {nom_profil} (chance {chance * 100:g} %) : {recyclables} articles vendus recyclables, "
                      f"le plus proche de la borne rapporte {pire[0] * 50:.1f} % de son prix ({pire[1] or '-'})")
    if bavard and not erreurs:
        print(f"[marchands] contrôle des 50 % : {len(ventes)} articles vendus, aucune revente ni recyclage trop rentable")
    return erreurs, notes


def cout_fabrication(aid, mila):
    """Coût d'une munition à la Table d'armurier, à la balle : la recette la moins chère de heraliz_crafts, composants au
    prix de rachat de Mila ; None sans recette."""
    best = None
    for f in sorted(RECETTES_MUNITIONS.glob("*.json")):
        r = json.loads(f.read_text(encoding="utf-8"))
        res = r.get("result", {})
        if res.get("type") != "ammo" or res.get("id") != aid:
            continue
        v = sum(prix_regle(mila, m["item"]["item"], "") * int(m.get("count", 1)) for m in r.get("materials", []))
        v /= max(1, int(res.get("count", 1)))
        best = v if best is None else min(best, v)
    return best


def rendus_articles(marchands, pid, achat=1.0, rachat_f=1.0, recyclage=1.0):
    """[(article, prix payé, revente, recyclé au spawn, recyclé sur la carte)] des articles du marchand pid, à l'unité et
    en moyenne, comme le serveur : prix payé = prix x achat (Ponts.prixAchat) ; revente = meilleure règle de rachat x
    rachat_f, plafond des 50 % du plus petit prix d'achat (Marchands.prixRachat) ; recyclé = espérance du recycleur sur un
    paquet complet (composants revendus, ou hérals « rien »), chance et hérals x recyclage (Recyclage.tropRentable), divisée
    par la taille du paquet. Objet neuf (état 100 %)."""
    rec = json.loads(RECYCLAGE.read_text(encoding="utf-8"))
    racheteurs = {k: m for k, m in marchands.items() if m.get("type") == "rachat"}
    # 19/11 : même table des ventes que le serveur (objets reçus par un échange à 0), comme analyser
    ventes = table_ventes(marchands, achat)

    def revente(objet, nbt):
        return meilleur_rachat(objet, nbt, racheteurs, ventes, rachat_f)

    chances = [rec["chance"], rec.get("profils", {}).get("carte", {}).get("chance", rec["chance"])]
    out = []
    for a in marchands[pid].get("articles", []):
        objet, nbt = a["objet"], a.get("nbt", "")
        paye = a["prix"] if achat == 1.0 else max(1, arrondi(a["prix"] * achat))
        recy = [0.0, 0.0]
        r = regle_recyclage(rec, objet, nbt)
        if r is not None:
            g = max(0, rang(objet, nbt))
            rend = r.get("rend") or r["rend_rarete"][min(len(r["rend_rarete"]) - 1, g)]
            somme = sum(revente(c, "") for c in rend)
            rien = rec["herals_si_rien"][min(5, g)]
            rien = rien if recyclage == 1.0 or rien <= 0 else arrondi(rien * recyclage)
            for i, ch in enumerate(chances):
                ch = min(1.0, ch * recyclage)
                recy[i] = (ch * somme + (1 - ch) ** len(rend) * rien) / r.get("par", 1)
        out.append((a, paye, revente(objet, nbt), recy[0], recy[1]))
    return out


def controle_passeur(marchands, bavard=True):
    """19/11 : le Passeur vend cher. Refus (SystemExit) si un de ses articles est une arme de précision ou lourde, une
    munition spéciale du loot, une munition à moins de MUNITIONS_FOIS_FABRICATION fois sa fabrication, ou s'il rend, revendu
    à Mila ou recyclé (spawn ou carte), plus de PASSEUR_RENDU_MAX du prix payé : avec les prix des fichiers, et avec
    l'économie de la bêta (section [beta] de serveur/defaultconfigs/heraliz_loot-server.toml) quand elle est active."""
    import re
    import loot_armes as L
    if "passeur" not in marchands:
        return
    erreurs = []
    mila = marchands.get("mila", {})
    for a in marchands["passeur"].get("articles", []):
        m = re.search(r'(GunId|AmmoId):"([^"]+)"', a.get("nbt", ""))
        if not m:
            continue
        if m.group(1) == "GunId" and (genre_arme(m.group(2)) in GENRES_INTERDITS or not genre_arme(m.group(2))):
            erreurs.append(f"{m.group(2)} : arme de précision ou lourde, jamais chez le Passeur")
        if m.group(1) == "AmmoId":
            if m.group(2) in L.MUNITIONS_HORS:
                erreurs.append(f"{m.group(2)} : munition spéciale, jamais chez le Passeur")
            cout = cout_fabrication(m.group(2), mila)
            if cout and a["prix"] < MUNITIONS_FOIS_FABRICATION * cout:
                erreurs.append(f"{m.group(2)} : {a['prix']} la balle, moins de {MUNITIONS_FOIS_FABRICATION:g} fois sa fabrication ({cout:.1f})")
    jeux = [("fichiers", 1.0, 1.0, 1.0)]
    if facteurs_beta():
        jeux.append(("bêta",) + facteurs_beta())
    for nom, fa, fr, fy in jeux:
        pire = (0.0, "")
        for a, paye, revente, spawn, carte in rendus_articles(marchands, "passeur", fa, fr, fy):
            part = max(revente, spawn, carte) / paye
            borne = PASSEUR_RENDU_MAX_MUNITIONS if "AmmoId" in (a.get("nbt") or "") else PASSEUR_RENDU_MAX
            if part > pire[0]:
                pire = (part, a.get("nbt") or a["objet"])
            if part > borne:
                erreurs.append(f"{nom} : {a['objet']}{a.get('nbt', '')} payé {paye}, rend jusqu'à {max(revente, spawn, carte):.1f} "
                               f"({part * 100:.1f} %, borne {borne * 100:g} %)")
        if bavard:
            print(f"[marchands] passeur ({nom}) : revendu ou recyclé, un article rend au plus {pire[0] * 100:.1f} % de son prix "
                  f"({pire[1]}) ; bornes {PASSEUR_RENDU_MAX * 100:g} % (armes), {PASSEUR_RENDU_MAX_MUNITIONS * 100:g} % (munitions)")
    for e in erreurs:
        print("ERREUR : " + e)
    if erreurs:
        raise SystemExit("Refusé : le Passeur doit vendre cher, sans argent en boucle.")


def regle_recyclage(rec, objet, nbt):
    """Règle du recycleur pour cet objet, dans l'ordre de Recyclage.regle (jamais, règles exactes, règles larges)."""
    import re
    for j in rec.get("jamais", []):
        if (objet.startswith(j[:-1]) if j.endswith("*") else objet == j):
            return None
    donnees = dict(re.findall(r'(\w+)\s*:\s*"([^"]+)"', nbt or ""))
    exacte = lambda r: "valeur" in r and "cle" in r and not r["objet"].endswith("*")
    for r in rec["regles"]:
        if exacte(r) and r["objet"] == objet and donnees.get(r["cle"]) == r["valeur"]:
            return r
    for r in rec["regles"]:
        if exacte(r):
            continue
        o = r["objet"]
        if (objet.startswith(o[:-1]) if o.endswith("*") else objet == o) and ("cle" not in r or r["cle"] in donnees):
            return r
    return None


if __name__ == "__main__":
    import sys
    if "--controle" in sys.argv:
        # tous les marchands, troc compris (19/11 : un objet reçu par un échange compte pour un prix d'achat de 0)
        tous = {}
        for f in sorted(PNJ.glob("*.json")):
            m = json.loads(f.read_text(encoding="utf-8")).get("marchand")
            if m:
                tous[f.stem] = m
        controle(tous)
        controle_echanges(tous)
        controle_jamais_vendus(tous)
        controle_matieres_brutes(tous)
        controle_passeur(tous)
        raise SystemExit(0)
    tags_rachat()
    controle(MARCHANDS)
    controle_echanges(MARCHANDS)
    controle_jamais_vendus(MARCHANDS)
    controle_matieres_brutes(MARCHANDS)
    controle_passeur(MARCHANDS)
    for pid, m in MARCHANDS.items():
        f = PNJ / f"{pid}.json"
        d = json.loads(f.read_text(encoding="utf-8"))
        d["marchand"] = m
        ecrire_texte(f, json.dumps(d, ensure_ascii=False, indent=2) + "\n")
        cle = next((k for k in ("articles", "echanges", "rachat") if k in m), "rachat")
        print(f"{pid} : {len(m.get(cle, []))} {dict(articles='articles', echanges='échanges', rachat='règles')[cle]}")
