"""Catalogue des props de la carte : conteneurs de loot (HeraliZ Loot, T1 à T7 et familles), meubles de cfm « PATCH »,
décors de HeraliZ Utils et de HeraliZ Décors. Lu dans les sources et les jars du dépôt (rien n'est écrit).

    python outils/props_catalogue.py [--sortie catalogue.md]      liste lisible de tous les props (id, nom, taille,
                                                                  catégorie, pièces, extérieur, doublons visuels)

Utilisé par outils/props_carte.py (analyse de la carte, plan de diversification). Les règles de cohérence (où va
chaque prop) sont les tableaux de ce fichier : on les corrige ici, jamais dans les lots de commandes.

Vocabulaire :
- pièce (intérieur) : cuisine, sdb, chambre, salon, entree, bureau, ecole, buanderie, cave, garage, grange, commerce,
  entrepot, police, secours, hopital, militaire, onu, banque, labo, chasse, prison, gare, port, eglise ;
- extérieur : rue, jardin, ferme, camp, chantier, quai, base (militaire), cimetiere, route, crash ;
- groupe : appareils ou meubles qu'une même pièce n'a qu'en un exemplaire (ou deux) : cuisson, frigo, micro_ondes...
"""
import json
import math
import re
import struct
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

DEPOT = Path(__file__).resolve().parent.parent
LOOT = DEPOT / "mods/heraliz-loot/src"
CFM_JAR = next(iter(sorted((DEPOT / "serveur/mods-tiers").glob("cfm-*.jar"))), None)

PIECES = ["cuisine", "sdb", "chambre", "salon", "entree", "bureau", "ecole", "buanderie", "cave", "garage", "grange",
          "commerce", "entrepot", "police", "secours", "hopital", "militaire", "onu", "banque", "labo", "chasse",
          "prison", "gare", "port", "eglise"]
EXTERIEURS = ["rue", "jardin", "ferme", "camp", "chantier", "quai", "base", "cimetiere", "route", "crash"]
# pièces « de maison » : une seule cuisine, une salle de bain... (les autres types de pièces sont des grands volumes)
MAISON = {"cuisine", "sdb", "chambre", "salon", "entree", "buanderie", "bureau"}
# groupes indispensables à leur pièce : le seul frigo d'une cuisine ne part que pour un autre frigo
ESSENTIELS = {"cuisson", "frigo", "vasque", "lit", "boite_aux_lettres"}

# nombre maximal par pièce pour chaque groupe (au-delà : incohérence « deux frigos dans la même cuisine »)
GROUPES = {
    "cuisson": 1, "frigo": 1, "micro_ondes": 1, "lave_linge": 1, "vasque": 1, "tv": 1, "poubelle_cuisine": 1,
    "vaisselier": 1, "range_chaussures": 1, "canape": 2, "lit": 2, "table_chevet": 2, "armoire": 2, "commode": 2,
    "bibliotheque": 2, "bureau_police": 2, "distributeur": 2, "baie": 2, "machine_coudre": 1, "coffre_jouets": 1,
    "etabli": 2, "boite_aux_lettres": 1,
}

# ---------------------------------------------------------------------------------------------------------- HeraliZ Loot
# nom (sans tN_) -> (pièces, extérieurs, groupe). « * » = partout (pièces de vie et de stockage), « - » = nulle part.
_P_STOCK = "cave garage entrepot commerce grange"
_P_VIE = "cuisine salon chambre entree bureau"
_P_TOUT_CIVIL = _P_VIE + " sdb buanderie cave garage grange commerce entrepot ecole gare hopital police secours bureau"
_P_MIL = "militaire onu"
L = {
    # ---- T1
    "sacs_poubelle": ("cuisine garage entrepot commerce cave", "rue chantier", ""),
    "poubelle_metal": ("garage entrepot entree", "rue chantier", ""),
    "bac_roulant": ("garage", "rue jardin", ""),
    "benne": ("", "rue chantier", ""),
    "cartons_ecrases": (_P_TOUT_CIVIL, "rue chantier", ""),
    "paquets_journaux": ("commerce bureau entree gare", "rue", ""),
    "caddie_renverse": ("commerce", "rue", ""),
    "palette_cartons": ("entrepot commerce garage", "rue chantier quai", ""),
    "cagettes_marche": ("commerce cuisine cave", "rue", ""),
    "caisses_biere": ("commerce cave cuisine", "rue", ""),
    "frigo_boissons": ("commerce gare ecole hopital", "", "distributeur"),
    "distributeur_snacks": ("commerce gare ecole hopital bureau police", "rue", "distributeur"),
    "corbeille_rue": ("gare", "rue", ""),
    "sacs_courses": ("cuisine commerce entree", "rue", ""),
    "cartable": ("ecole chambre", "rue", ""),
    "sac_a_main": ("chambre salon entree", "rue", ""),
    "valise_ouverte": ("chambre entree gare", "rue route", ""),
    "tas_vetements": ("chambre commerce buanderie sdb", "rue", ""),
    "pneus": ("garage", "rue chantier ferme route", ""),
    "fut_brule": ("", "rue chantier camp", ""),
    "jerricans": ("garage grange", "ferme rue camp route", ""),
    "pots_peinture": ("garage entrepot cave", "chantier", ""),
    "boites_pizza": ("cuisine salon", "rue", ""),
    "bac_recyclage": ("cuisine garage", "rue", ""),
    "presentoir_journaux": ("commerce gare", "rue", ""),
    "etal_marche": ("commerce", "rue", ""),
    "table_chevet": ("chambre hopital", "", "table_chevet"),
    "micro_ondes": ("cuisine bureau", "", "micro_ondes"),
    "cuisiniere": ("cuisine", "", "cuisson"),
    "lave_linge": ("buanderie sdb cuisine", "", "lave_linge"),
    "element_cuisine": ("cuisine", "", ""),
    "poubelle_cuisine": ("cuisine", "", "poubelle_cuisine"),
    "panier_linge": ("sdb buanderie chambre", "", ""),
    "matelas": ("chambre", "camp", "lit"),
    "cartons_demenagement": (_P_TOUT_CIVIL, "", ""),
    "bureau_ecolier": ("ecole bureau chambre", "", ""),
    "classeur_metal": ("bureau police ecole hopital banque", "", ""),
    "vestiaire": ("ecole militaire police secours garage hopital entrepot", "", ""),
    "etagere_garage": ("garage entrepot cave grange", "", ""),
    "bibliotheque": ("salon bureau chambre ecole entree", "", "bibliotheque"),
    "coffre_jouets": ("chambre", "", "coffre_jouets"),
    "poussette": ("entree", "rue", ""),
    "canape": ("salon", "", "canape"),
    "fauteuil": ("salon chambre bureau", "", ""),
    "meuble_vasque": ("sdb", "", "vasque"),
    "range_chaussures": ("entree chambre", "", "range_chaussures"),
    "grand_cabas": ("cuisine entree commerce", "rue", ""),
    "tiroir_tombe": ("chambre bureau salon cuisine", "", ""),
    "mini_frigo": ("bureau commerce", "", "frigo"),
    "chariot_menage": ("hopital bureau commerce ecole", "", ""),
    "meuble_tv": ("salon", "", "tv"),
    "camionnette_bleue": ("-", "route rue", ""),
    "eta_14": ("garage entrepot", "", ""),
    "eta_3": ("commerce", "", ""),
    "eta_4": ("commerce", "", ""),
    "eta_5": ("commerce cave", "", ""),
    "loot_civil_1": ("chambre entree gare", "camp", ""), "loot_civil_2": ("chambre entree gare", "camp", ""),
    "loot_civil_3": ("chambre entree gare", "camp", ""), "loot_civil_7": ("chambre entree", "camp", ""),
    "loot_civil_8": ("chambre entree", "camp", ""), "loot_civil_4": ("ecole chambre", "", ""),
    "loot_civil_5": ("chambre garage", "camp quai", ""), "loot_civil_6": ("chambre garage", "camp quai", ""),
    "loot_civil_19": ("sdb", "", ""), "loot_civil_20": ("garage", "route rue", ""),
    # ---- T2
    "caisse_outils": ("garage grange entrepot", "chantier", ""),
    "caisse_ferme": ("grange", "ferme", ""),
    "boite_aux_lettres": ("entree", "rue jardin", "boite_aux_lettres"),
    "frigo": ("cuisine", "", "frigo"),
    "valise": ("chambre entree gare", "rue route", ""),
    "cartons": (_P_TOUT_CIVIL, "chantier rue", ""),
    "commode": ("chambre", "", "commode"),
    "sacs_grain": ("grange cave", "ferme", ""),
    "caisses_pommes": ("grange cave commerce cuisine", "ferme", ""),
    "glaciere": ("cuisine garage", "camp quai", ""),
    "panier": ("cuisine salon grange", "jardin", ""),
    "bac_rangement": ("garage chambre cave", "rue chantier", ""),
    "caisse_bouteilles": ("cuisine cave commerce", "rue", ""),
    "sac_sport": ("chambre entree ecole", "rue route", ""),
    "malle": ("chambre cave", "rue", ""),
    "seau": ("grange buanderie cave", "ferme jardin", ""),
    "caisse_lait": ("grange", "ferme", ""),
    "coffre_jardin": ("-", "jardin", ""),
    "boites_chaussures": ("chambre entree commerce", "rue", ""),
    "sacoche": ("entree chambre bureau", "rue route", ""),
    "armoire": ("chambre", "", "armoire"),
    "buffet": ("cuisine salon", "", "vaisselier"),
    "vaisselier": ("cuisine salon", "", "vaisselier"),
    "coffre_bois": ("chambre", "", ""),
    "etabli": ("garage grange", "", "etabli"),
    "servante": ("garage", "", ""),
    "bidons_lait": ("grange", "ferme", ""),
    "brouette": ("grange", "jardin ferme chantier", ""),
    "bottes_paille": ("grange", "ferme", ""),
    "sacs_engrais": ("grange", "ferme jardin", ""),
    "ruche": ("-", "jardin ferme", ""),
    "clapier": ("grange", "ferme jardin", ""),
    "tonneau": ("cave grange", "ferme", ""),
    "casier_vin": ("cave cuisine", "", ""),
    "etagere_conserves": ("cave cuisine entrepot commerce", "", ""),
    "congelateur_coffre": ("cave garage commerce", "", "frigo"),
    "cuisiniere_bois": ("cuisine", "", "cuisson"),
    "machine_coudre": ("chambre salon", "", "machine_coudre"),
    "valises_anciennes": ("chambre cave", "rue", ""),
    "sac_randonnee": ("chambre entree", "camp route rue", ""),
    "materiel_camping": ("garage cave", "camp", ""),
    "caisse_peche": ("garage", "quai", ""),
    "trousse_secours": ("sdb cuisine garage hopital ecole bureau", "rue route", ""),
    "tondeuse": ("garage", "jardin", ""),
    "outils_jardin": ("garage grange", "jardin", ""),
    "loot_campagne_1": ("bureau grange", "", ""), "loot_campagne_2": ("cave grange cuisine", "", ""),
    "loot_campagne_3": ("grange chasse", "", ""), "loot_campagne_4": ("grange chasse", "", ""),
    "loot_campagne_11": ("-", "cimetiere", ""), "loot_campagne_12": ("cuisine", "", "cuisson"),
    "loot_crash_7": ("-", "crash", ""),
    # ---- T3
    "valise_arme": ("police chasse", "base", ""),
    "housse_fusil": ("chasse police", "camp", ""),
    "caisse_police": ("police", "rue route", ""),
    "sac_police": ("police", "rue", ""),
    "armoire_fusils": ("police chasse", "", ""),
    "caisse_preuves": ("police", "", ""),
    "gilet_caisse": ("police militaire", "base", ""),
    "sac_chasse": ("chasse", "camp", ""),
    "coffre_chasse": ("chasse", "", ""),
    "casier_police": ("police", "", ""),
    "bureau_police": ("police", "", "bureau_police"),
    "cage_scelles": ("police", "", ""),
    "boucliers": ("police", "rue", ""),
    "sac_secours": ("secours hopital", "rue route", ""),
    "malle_pompier": ("secours", "rue", ""),
    "extincteurs": ("secours garage entrepot hopital ecole", "rue chantier", ""),
    "brancard": ("hopital secours", "rue", ""),
    "coffre_pickup": ("-", "route rue ferme", ""),
    "glaciere_gibier": ("chasse", "camp", ""),
    "ratelier_chasse": ("chasse", "", ""),
    "caisse_pieges": ("chasse", "camp", ""),
    "caisse_cartouches": ("chasse police", "camp", ""),
    "sacs_appats": ("chasse", "camp", ""),
    "trousse_soins": ("hopital secours sdb", "rue route", ""),
    "sac_tactique": ("police militaire", "base rue", ""),
    "caisse_douane": ("entrepot port", "quai", ""),
    "jerricans_essence": ("garage secours", "route rue", ""),
    "caisse_securite_civile": ("secours", "rue", ""),
    "mirador_coffre": ("prison militaire", "base", ""),
    "devidoir_lances": ("secours", "", ""),
    "tenue_feu": ("secours", "", ""),
    "sac_saisie": ("police", "", ""),
    "malle_balisage": ("secours police garage", "route", ""),
    "herse_cloutee": ("police", "route", ""),
    "sac_carabine": ("chasse", "camp", ""),
    "valise_scientifique": ("police labo", "", ""),
    "mallette_radar": ("police", "route", ""),
    "extincteur_chariot": ("secours entrepot hopital", "", ""),
    "bouee_sauvetage": ("port secours", "quai", ""),
    "motopompe": ("secours", "rue quai", ""),
    "topcase_moto": ("police", "route", ""),
    "loot_crash_2": ("-", "crash", ""),
    "loot_chasse_6": ("chasse", "camp", ""),
    "loot_police_1": ("police", "", ""), "loot_police_2": ("police", "", ""), "loot_police_3": ("police", "", ""),
    "loot_police_4": ("police", "", ""),
    # ---- T4 (militaire : dedans et dehors)
    "sacs_sable": ("militaire", "base", ""),
    "futs": ("militaire entrepot", "base chantier", ""),
    "filet_camouflage": ("militaire", "base", ""),
    "sac_eau": ("militaire", "base camp", ""),
    "lit_picot": ("militaire", "base", ""),
    "table_armurier": ("militaire", "", ""),
    "norvegiens_cuisine": ("militaire", "base", ""),
    "portant_brelages": ("militaire", "", ""),
    "loot_militaire_1": ("militaire", "base crash", ""),
    "loot_militaire_13": ("militaire", "", ""), "loot_militaire_14": ("militaire", "", ""),
    "loot_militaire_15": ("militaire", "base", ""), "loot_militaire_16": ("militaire", "base", ""),
    "loot_militaire_32": ("militaire", "base", ""), "loot_militaire_33": ("militaire", "base", ""),
    "loot_militaire_34": ("militaire", "base", ""), "loot_militaire_35": ("militaire", "base", ""),
    "loot_militaire_31": ("militaire", "base", ""),
    "loot_crash_3": ("-", "crash", ""), "loot_crash_4": ("-", "crash", ""),
    # ---- T5 (ONU et militaire lourd)
    "caisse_armes_onu": ("onu", "base", ""), "aide_humanitaire": ("onu entrepot", "base", ""),
    "caisse_medicale_onu": ("onu hopital", "base", ""), "cantine_onu": ("onu", "base", ""),
    "bidons_eau_onu": ("onu", "base camp", ""), "caisse_casques_bleus": ("onu", "base", ""),
    "kit_decontamination": ("onu labo hopital", "base", ""), "table_operations": ("onu militaire", "", ""),
    "caisse_tentes_onu": ("onu", "base", ""), "glaciere_vaccins": ("onu hopital labo", "", ""),
    "groupe_electrogene": ("militaire onu", "base chantier", ""), "projecteur_campagne": ("militaire onu", "base", ""),
    "mitrailleuse_trepied": ("militaire", "base", ""), "robot_demineur": ("militaire", "base", ""),
    "reservoir_souple": ("-", "base", ""), "rack_obus": ("militaire", "base", ""),
    "poste_observation": ("militaire", "base", ""), "palette_largage": ("militaire entrepot", "base crash", ""),
    "sac_parachute": ("militaire", "base crash", ""),
    "casier_gris_9": ("militaire police bureau", "", ""), "casier_gris_18": ("militaire police bureau", "", ""),
    "casier_gris_27": ("militaire police bureau", "", ""),
    "loot_crash_5": ("-", "crash", ""),
    # ---- T6 (banque, laboratoire, militaire haute sécurité)
    "coffres_banque": ("banque", "", ""), "coffre_fort_grand": ("banque bureau", "", ""),
    "caisse_lingots": ("banque", "", ""), "distributeur_billets": ("banque gare commerce", "rue", "distributeur"),
    "valise_fonds": ("banque", "", ""), "chariot_fonds": ("banque", "", ""), "malle_bijoux": ("banque", "", ""),
    "armoire_cles": ("banque police", "", ""), "poste_securite": ("banque labo militaire", "", ""),
    "coffre_mural": ("banque bureau chambre", "", ""), "coffre_sol": ("banque bureau", "", ""),
    "valise_diplomatique": ("banque bureau", "", ""),
    "caisse_labo": ("labo", "", ""), "frigo_labo": ("labo hopital", "", ""), "fut_biologique": ("labo", "base", ""),
    "caisse_prototype": ("labo militaire", "base", ""), "dewar_azote": ("labo hopital", "", ""),
    "capsule_echantillons": ("labo", "", ""), "caisson_plomb": ("labo", "", ""),
    "terminal_biometrique": ("labo banque militaire", "", ""), "baie_serveurs": ("labo bureau militaire banque", "", "baie"),
    "chariot_urgence": ("hopital labo", "", ""),
    "boite_noire": ("-", "crash", ""), "station_satellite": ("militaire", "base", ""),
    "pupitre_lancement": ("militaire", "", ""), "brouilleur": ("militaire", "base", ""),
    "valise_drone": ("militaire", "base", ""),
}
# « meubles » : posés contre un mur, à hauteur d'homme ou de plan de travail ; un meuble n'est remplacé que par un
# meuble (une cuisine reste une rangée de meubles), un objet posé au sol par un objet, un cadavre par un cadavre
MEUBLES = set("""
element_cuisine cuisiniere lave_linge frigo mini_frigo congelateur_coffre meuble_vasque range_chaussures table_chevet
commode armoire vaisselier buffet bibliotheque meuble_tv classeur_metal vestiaire etagere_garage etagere_conserves
casier_vin coffre_bois etabli servante bureau_ecolier machine_coudre cuisiniere_bois frigo_boissons distributeur_snacks
canape fauteuil matelas coffre_jouets etal_marche presentoir_journaux chariot_menage
armoire_fusils casier_police bureau_police cage_scelles ratelier_chasse devidoir_lances tenue_feu extincteur_chariot
table_armurier portant_brelages lit_picot ratelier_campagne armoire_armes casier_gris_9 casier_gris_18 casier_gris_27
table_operations rack_obus poste_commandement coffres_banque coffre_fort_grand armoire_forte distributeur_billets
armoire_cles baie_serveurs frigo_labo terminal_biometrique chariot_urgence cage_munitions ratelier_mural poste_securite
pupitre_lancement vitrine_arme casier_rouge_9 casier_rouge_18 casier_rouge_27 coffre_mural eta_3 eta_4 eta_5 eta_14
loot_police_2 loot_police_3 loot_police_4 loot_militaire_7 loot_militaire_8 loot_militaire_9 loot_militaire_10
loot_militaire_11 loot_militaire_12 loot_militaire_18 loot_militaire_19 loot_militaire_20 loot_militaire_21
loot_militaire_22 loot_militaire_23 loot_militaire_24 loot_militaire_28 loot_militaire_29 loot_campagne_1
loot_campagne_12 soins2_loot_medical_4
""".split())


def gabarit(nom, categorie):
    """meuble, objet, cadavre ou véhicule (les remplacements restent dans le même gabarit)."""
    if categorie in ("cadavre", "véhicule", "coffre T7"):
        return categorie
    court = re.sub(r"^(t\d|soins\d|nourriture\d|munitions\d)_", "", nom)
    return "meuble" if court in MEUBLES or nom in MEUBLES else "objet"


# fréquence voulue d'un modèle par rapport aux autres de son palier (1 : courant ; défaut 1) : le plan donne moins de
# places aux objets rares (un coffre à jouets, une poussette...) qu'aux meubles de tous les jours
FREQUENCE = {
    "coffre_jouets": 0.3, "poussette": 0.3, "machine_coudre": 0.3, "mini_frigo": 0.4, "chariot_menage": 0.3,
    "tiroir_tombe": 0.5, "sac_a_main": 0.5, "valise_ouverte": 0.5, "pots_peinture": 0.4, "caddie_renverse": 0.5,
    "cartable": 0.4, "boites_pizza": 0.5, "presentoir_journaux": 0.4, "etal_marche": 0.5, "frigo_boissons": 0.5,
    "distributeur_snacks": 0.5, "eta_3": 0.3, "eta_4": 0.3, "eta_5": 0.3, "eta_14": 0.3, "lave_linge": 0.6,
    "loot_civil_1": 0.2, "loot_civil_2": 0.2, "loot_civil_3": 0.2, "loot_civil_4": 0.2, "loot_civil_5": 0.2,
    "loot_civil_6": 0.2, "loot_civil_7": 0.2, "loot_civil_8": 0.2, "loot_civil_19": 0.3, "loot_civil_20": 0.3,
    "ruche": 0.3, "clapier": 0.3, "tondeuse": 0.4, "coffre_jardin": 0.5, "caisse_lait": 0.5, "bidons_lait": 0.5,
    "congelateur_coffre": 0.4, "cuisiniere_bois": 0.4, "valises_anciennes": 0.5, "caisse_peche": 0.4,
    "loot_campagne_1": 0.3, "loot_campagne_2": 0.3, "loot_campagne_3": 0.3, "loot_campagne_4": 0.3,
    "loot_campagne_11": 0.3, "loot_campagne_12": 0.3,
}


def maxi_groupe(groupe, piece):
    """Nombre maximal d'un groupe dans une pièce : strict dans une maison, plus large ailleurs (un magasin a
    plusieurs vitrines réfrigérées, un réfectoire plusieurs cuisinières...)."""
    m = GROUPES.get(groupe, 1)
    if piece.get("type") not in MAISON:
        m *= 3 * max(1, piece.get("cases", 0) // 150)
    return m


def frequence(nom):
    return FREQUENCE.get(re.sub(r"^(t\d|soins\d|nourriture\d|munitions\d)_", "", nom), 1.0)


# conteneurs militaires (T4 à T6) sans entrée explicite : salle militaire, dedans ou sur la base
_MIL_AUTRES = ("militaire", "base", "")
# familles (nourriture, soins, munitions) : niveau = chiffre du nom
FAMILLES = {
    "nourriture": ("cuisine cave commerce militaire entrepot", "camp", ""),
    "soins": ("hopital secours sdb", "rue route base", ""),
    "munitions": ("militaire police chasse", "base", ""),
}
# conteneurs repris des anciens props d'ArkaniaZ (modèles cfm) : ce qu'ils montrent, d'après leurs textures
ARK = {
    "t1_eta_3": "étagère de magasin (cartons, vitre)", "t1_eta_4": "étagère de magasin (cartons, vitre)",
    "t1_eta_5": "rayon de conserves", "t1_eta_14": "meuble de métal rouillé",
    "t1_loot_civil_1": "sac de montagne vert", "t1_loot_civil_2": "sac de montagne bleu",
    "t1_loot_civil_3": "sac de montagne noir", "t1_loot_civil_4": "sac d'écolier", "t1_loot_civil_5": "sac étanche",
    "t1_loot_civil_6": "sac étanche noir", "t1_loot_civil_7": "sac de montagne", "t1_loot_civil_8": "sac de montagne",
    "t1_loot_civil_19": "papier toilette et brosse à dents", "t1_loot_civil_20": "bidon d'essence",
    "t2_loot_campagne_1": "bureau de campagne", "t2_loot_campagne_2": "conserves sur planches",
    "t2_loot_campagne_3": "munitions .357 sur planches", "t2_loot_campagne_4": "munitions 5.56 sur planches",
    "t2_loot_campagne_11": "pierre tombale", "t2_loot_campagne_12": "réchaud (kompor)",
    "t3_loot_police_1": "lampe, menottes, radio", "t3_loot_police_2": "armoire métallique grise",
    "t3_loot_police_3": "armoire métallique grise", "t3_loot_police_4": "armoire métallique grise",
    "t4_loot_militaire_1": "caisse de ravitaillement", "t4_loot_militaire_13": "valise de sniper (neige)",
    "t4_loot_militaire_14": "valise de sniper (verte)", "t4_loot_militaire_15": "sac militaire",
    "t4_loot_militaire_16": "sac militaire", "t4_loot_militaire_32": "cadavre de soldat à la hache",
    "t5_loot_militaire_8": "caisse militaire (portes)", "t5_loot_militaire_9": "établi militaire",
    "t5_loot_militaire_10": "établi militaire", "t5_loot_militaire_11": "établi", "t5_loot_militaire_18": "armoire militaire bleue",
    "t5_loot_militaire_19": "armoire militaire bleue", "t5_loot_militaire_20": "armoire militaire bleue",
    "t5_loot_militaire_21": "armoire militaire bleue", "t5_loot_militaire_28": "établi militaire",
    "t5_loot_militaire_30": "caisse militaire", "t6_loot_militaire_7": "caisse militaire (portes)",
    "t6_loot_militaire_12": "établi militaire", "t6_loot_militaire_22": "armoire militaire rouge",
    "t6_loot_militaire_23": "armoire militaire rouge", "t6_loot_militaire_24": "armoire militaire rouge",
    "t6_loot_militaire_26": "MP5 exposé", "t6_loot_militaire_27": "RPG-7 exposé", "t6_loot_militaire_29": "cage d'armes",
    "nourriture3_caisse_nourriture": "caisse de nourriture", "soins3_loot_medical_2": "boîte médicale",
    "soins2_loot_medical_4": "casier médical", "munitions6_loot_munitions_8": "caisses de munitions",
}
# modèles « cadavre » d'ArkaniaZ (corps couché, 9 éléments) : cadavres à thème
CADAVRES_ARK = re.compile(r"^(t1_loot_civil_(9|1[0-5]|2[1-9]|30)|t2_loot_campagne_([5-9]|10)|soins\d_loot_medical_([5-9]|10)|"
                          r"t3_loot_prison_\d+|t4_loot_militaire_3[1345])$")
THEME_CADAVRE = {"civil": ("*", "rue route jardin", ""), "campagne": ("grange", "ferme jardin route", ""),
                 "medical": ("hopital secours", "rue", ""), "prison": ("prison", "base", ""),
                 "militaire": ("militaire", "base", "")}

# anciens props cfm (retirés du jar cfm « PATCH », repris dans HeraliZ Loot) : nom cfm -> conteneur équivalent
CFM_VERS_LOOT_FAMILLE = {"military": "militaire", "civil": "civil", "medical": "medical", "food": "nourriture",
                         "ammo": "munitions", "police": "police", "hunting": "chasse", "prison": "prison",
                         "crash": "crash", "campagne": "campagne"}


def _niveau(nom):
    p = nom.split("_", 1)[0]
    m = re.search(r"(\d+)$", p)
    return int(m.group(1)) if m else 0


def regle_loot(nom):
    """(pièces, extérieurs, groupe, catégorie) d'un conteneur de HeraliZ Loot."""
    court = re.sub(r"^t\d_", "", nom)
    if nom.startswith("vehicules_") or re.match(r"t1_(vehicule_civil|camionnette)", nom):
        return set(), {"route", "rue"}, "", "véhicule"
    if nom.startswith("t7_"):
        return {"militaire", "banque", "labo"}, {"base", "crash"}, "", "coffre T7"
    if re.match(r"t1_cadavre_zombie_\d+$", nom):
        return set(PIECES) - {"eglise"}, set(EXTERIEURS) - {"cimetiere"}, "", "cadavre"
    if CADAVRES_ARK.match(nom):
        theme = next(t for t in THEME_CADAVRE if t in nom)
        p, e, g = THEME_CADAVRE[theme]
        return _ens(p, PIECES), _ens(e, EXTERIEURS), g, "cadavre"
    fam = re.match(r"(nourriture|soins|munitions)\d+_", nom)
    if fam:
        p, e, g = FAMILLES[fam.group(1)]
        return _ens(p, PIECES), _ens(e, EXTERIEURS), g, fam.group(1)
    if court in L:
        p, e, g = L[court]
    elif nom[:2] in ("t4", "t5", "t6"):
        p, e, g = _MIL_AUTRES
    else:
        p, e, g = ("*", "", "")
    pieces, ext = _ens(p, PIECES), _ens(e, EXTERIEURS)
    return pieces, ext, g, _categorie(pieces, ext, nom)


def _ens(s, tous):
    if s == "*":
        return set(tous) - {"eglise", "prison", "labo", "banque"}
    if s == "-":
        return set()
    return set(s.split())


def _categorie(pieces, ext, nom):
    if not pieces and ext:
        return "extérieur"
    ordre = ["cuisine", "sdb", "chambre", "salon", "buanderie", "entree", "ecole", "bureau", "garage", "grange", "cave",
             "commerce", "police", "chasse", "secours", "hopital", "onu", "militaire", "banque", "labo", "entrepot"]
    for o in ordre:
        if o in pieces and len(pieces) <= 4:
            return o
    return "stockage"


# ------------------------------------------------------------------------------------------------------------------ cfm
# familles de cfm (dossier des textures du modèle ou début du nom) -> (pièces, extérieurs, gardé pour DayZ)
CFM_FAMILLES = {
    "hospital": ("hopital", "", True), "bank": ("banque", "", True), "classroom": ("ecole", "", True),
    "ik": ("cuisine commerce militaire", "", True), "hunting": ("chasse", "", True),
    "fashion": ("commerce", "", True), "century": ("salon chambre bureau", "", True),
    "zombie": ("garage entrepot", "rue chantier base", True), "detritus": ("*", "rue chantier route", True),
    "wooden_pallet": ("entrepot garage grange", "rue chantier quai", True), "chaise": ("salon cuisine bureau ecole", "", True),
    "arkaniaz_corpse": ("*", "rue route base", True), "arkaniaz_office": ("bureau police banque", "", True),
    "arkaniaz_old_tv": ("salon chambre bureau", "", True), "arkaniaz_trash": ("cuisine garage", "rue", True),
    "arkaniaz": ("*", "rue chantier", True), "dungeon": ("cave prison", "", False), "halloween": ("-", "cimetiere ferme", False),
    "carribean": ("-", "quai", False), "aqua": ("-", "quai", False), "summoning": ("-", "", False),
    "giving": ("-", "", False), "noel": ("-", "", False), "nm": ("-", "", False), "plushies": ("-", "", False),
    "christmas": ("-", "", False), "santa": ("-", "", False), "sovietblocks": ("bureau labo militaire hopital", "", True),
    "custom": ("*", "rue base", True), "blocks": ("*", "rue", True),
}
# cfm : blocs de structure (vitres, garde-corps, escaliers, planchers, portes, volets...) et blocs d'administration
# (points d'apparition, coffres de la boutique, trophées...) : jamais déplacés ni remplacés par le plan
CFM_STRUCTURE = re.compile(r"^(vb_\d|volet_\d|model_(239|240|241|113|223|251|252|88|89|90|32|109|107|108)|prison_guardrail|"
                           r"metal_door_\d|century_door|zombie_chainlinkfence|zombie_barricade_\d|arkaniaz_wire_mesh|"
                           r"arkaniaz_(red|hollow)_brick|bank_glass|bank_barrier|bank_safe_door|hospital_curtain.*|"
                           r"fashion_curtain|century_curtain.*|hunting_block_camo_curtain|board_\d+|sign_map_\d+|"
                           r"model_(209|210|211|212))$")
CFM_ADMIN = re.compile(r"^(spawn_.*|back_spawn|return_position|room_forcefield_.*|quest_board|bank_atm_shop|"
                       r"safe_planque_\d+|safe_sentinel_\d+|safe|(normal|rare|epic|legendary)_[a-z]+_box|christmas_box|"
                       r"gold_trophy_season_\d|hommage_lola|model_20|model_84|bedrock_\d|gold_ingot_9)$")
# blocs de construction de cfm (carrelages, bétons...) : la liste du pack soviétique sans les modèles
CFM_MATERIAU = re.compile(r"^(aquamarine_tile.*|asphalt|beton.*|blue_.*|brown_tile.*|ceramik|cracked_.*|d_dark_.*|dark_.*|"
                          r"glass\d*|glass_muddy|glazed_tile.*|gofro_.*|gold_spiral|gray_.*|green_.*|kafel.*|lab_tile.*|"
                          r"light_bricks|lino\d*|moss.*|nii_.*|panel_block.*|planks_.*|quartz|raid_block|raw_beton|"
                          r"red_banner|red_pixel|green_pixel|rusty_ralling|small_tiles.*|soviet_.*|swampwater_.*|sysblock|"
                          r"table_up|tilled_block.*|travertine_block|wallpaper_\d|white_.*|czech|empty\d*|front|back|"
                          r"display\d*|dsp\d*|door_wood\d*|hermodoor\d*|keyboard|lamp|lampsteel|monitor_.*|red_lamp.*|"
                          r"barometer|clock)$")
# mots des textures d'un modèle cfm générique -> ce qu'il montre, pièces, extérieurs
CFM_MOTS = [
    (r"carton", "cartons", "*", "rue chantier"), (r"metal_barrel|oil_drum|biohard", "fûts", "garage entrepot", "rue chantier base"),
    (r"blockfeuille", "feuilles mortes", "*", "rue route jardin"), (r"bouteilledeau", "bouteilles au sol", "*", "rue"),
    (r"blood", "sang", "*", "rue route"), (r"cadavre|zombie\d", "cadavre", "*", "rue route"),
    (r"pneu", "pneus", "garage", "rue route chantier"), (r"military_tent|camo_leaves", "matériel militaire", "militaire", "base camp"),
    (r"refrigerator|fridge", "frigo", "cuisine", ""), (r"sink", "évier", "cuisine sdb", ""), (r"bathtub", "baignoire", "sdb", ""),
    (r"bed_1|bed", "lit", "chambre hopital", ""), (r"washer", "lave-linge", "buanderie sdb", ""), (r"boiler", "chaudière", "cave garage", ""),
    (r"brazier", "brasero", "-", "camp rue"), (r"trash", "poubelle", "cuisine garage", "rue"), (r"bookshelf", "bibliothèque", "salon bureau", ""),
    (r"fusebox|cable", "électricité", "garage entrepot cave", "chantier"), (r"desk", "bureau", "bureau ecole chambre", ""),
    (r"medic|first_aid", "soins", "hopital sdb", ""), (r"boxe_ammo|weaponbox|lootbox|armored|elitecase", "caisse d'armes", "militaire police", "base"),
    (r"crafting_table|iron_trapdoor", "établi", "garage militaire", ""), (r"chessboard", "jeu d'échecs", "salon", ""),
    (r"scarecrow", "épouvantail", "-", "ferme jardin"), (r"texturetumba", "tombe", "-", "cimetiere"),
    (r"bench", "banc", "gare", "rue jardin"), (r"stone|log_big_oak|planks", "débris", "*", "rue chantier"),
]


def chaines_classe(data):
    """Littéraux String d'un fichier .class (pool de constantes) : les noms enregistrés par cfm."""
    n = struct.unpack_from(">H", data, 8)[0]
    i, k = 10, 1
    utf8, refs = {}, []
    while k < n:
        t = data[i]
        if t == 1:
            ln = struct.unpack_from(">H", data, i + 1)[0]
            utf8[k] = data[i + 3:i + 3 + ln].decode("utf-8", "replace")
            i += 3 + ln
        elif t in (3, 4):
            i += 5
        elif t in (5, 6):
            i += 9
            k += 1
        elif t in (7, 16, 19, 20):
            i += 3
        elif t == 8:
            refs.append(struct.unpack_from(">H", data, i + 1)[0])
            i += 3
        elif t in (9, 10, 11, 12, 17, 18):
            i += 5
        elif t == 15:
            i += 4
        else:
            raise ValueError("constante inconnue %d" % t)
        k += 1
    return {utf8[r] for r in refs if r in utf8}


# --------------------------------------------------------------------------------------------- modèles et formes
def rotation_y(blockstate):
    """facing -> rotation y (degrés) d'après un fichier blockstates (variants « facing=... »)."""
    out = {}
    for cle, v in (blockstate.get("variants") or {}).items():
        v = v[0] if isinstance(v, list) else v
        m = re.search(r"facing=(north|south|east|west)", cle)
        if m and m.group(1) not in out:
            out[m.group(1)] = int(v.get("y", 0)) % 360
    return out


def modeles_blockstate(blockstate):
    out = []
    for v in (blockstate.get("variants") or {}).values():
        for vv in (v if isinstance(v, list) else [v]):
            out.append(vv["model"])
    for p in blockstate.get("multipart") or []:
        a = p.get("apply")
        for vv in (a if isinstance(a, list) else [a]):
            if vv:
                out.append(vv["model"])
    return out


class Ressources:
    """Lecture des assets (dossiers des sources puis jars), avec cache."""

    def __init__(self):
        self.dossiers = sorted(DEPOT.glob("mods/*/src/*/resources"))
        self.jars = []
        for j in sorted((DEPOT / "serveur/mods-tiers").glob("*.jar")):
            try:
                self.jars.append(zipfile.ZipFile(j))
            except zipfile.BadZipFile:
                pass
        self.noms_jars = [set(z.namelist()) for z in self.jars]
        self.cache = {}

    def lire(self, chemin):
        if chemin in self.cache:
            return self.cache[chemin]
        out = None
        for d in self.dossiers:
            f = d / chemin
            if f.is_file():
                out = f.read_bytes()
                break
        if out is None:
            for z, noms in zip(self.jars, self.noms_jars):
                if chemin in noms:
                    out = z.read(chemin)
                    break
        self.cache[chemin] = out
        return out

    def json(self, chemin):
        b = self.lire(chemin)
        if b is None:
            return None
        try:
            return json.loads(b.decode("utf-8", "replace"))
        except ValueError:
            return None

    def modele(self, ref):
        """Éléments d'un modèle de bloc (parents suivis) : (liste de boîtes px, textures)."""
        ns, _, p = ref.partition(":") if ":" in ref else ("minecraft", ":", ref)
        boites, textures = [], {}
        for _ in range(8):
            m = self.json("assets/%s/models/%s.json" % (ns, p))
            if not m:
                break
            for k, v in (m.get("textures") or {}).items():
                textures.setdefault(k, v)
            if not boites and m.get("elements"):
                boites = [list(e["from"]) + list(e["to"]) for e in m["elements"] if "from" in e and "to" in e]
            par = m.get("parent")
            if not par or par.startswith("block/cube") or par.startswith("minecraft:block/cube"):
                if par and not boites:
                    boites = [[0, 0, 0, 16, 16, 16]]
                break
            ns, _, p = par.partition(":") if ":" in par else ("minecraft", ":", par)
        return boites, textures


def cellules_boites(boites):
    """Cellules (x, y, z) entières occupées par des boîtes en px (repère du modèle), la cellule 0 en premier."""
    cel = {(0, 0, 0)}
    for b in boites:
        x0, y0, z0 = (int(math.floor(b[i] / 16 + 1e-3)) for i in range(3))
        x1, y1, z1 = (int(math.ceil(b[i] / 16 - 1e-3)) - 1 for i in range(3, 6))
        for x in range(x0, x1 + 1):
            for y in range(max(0, y0), y1 + 1):
                for z in range(z0, z1 + 1):
                    cel.add((x, y, z))
    return [(0, 0, 0)] + sorted(c for c in cel if c != (0, 0, 0))


def tourner_loot(c, facing):
    """Cellule du repère du modèle (avant vers +Z) -> décalage monde, comme Encombrement.rotateCell de HeraliZ Loot."""
    cx, cy, cz = c
    return {"north": (-cx, cy, -cz), "east": (cz, cy, -cx), "west": (-cz, cy, cx)}.get(facing, (cx, cy, cz))


def tourner_json(c, y_deg):
    """Cellule d'un modèle JSON tourné de y degrés (sens de Minecraft) autour du centre du bloc."""
    cx, cy, cz = c
    for _ in range((y_deg // 90) % 4):
        cx, cz = -cz, cx
    return cx, cy, cz


# ------------------------------------------------------------------------------------------------------- catalogue
class Prop:
    __slots__ = ("id", "mod", "nom", "niveau", "categorie", "pieces", "ext", "groupe", "cellules", "visuel", "rot",
                 "modele", "doublon", "dayz", "structure", "loot", "decrit", "existe", "be", "gab", "haut", "famille")

    def __init__(self, **k):
        for s in self.__slots__:
            setattr(self, s, k.get(s))

    def cellules_monde(self, facing):
        """Cellules réellement occupées (blocs « partie » compris) pour cette orientation."""
        if self.mod == "heraliz_loot":
            return [tourner_loot(c, facing) for c in self.cellules]
        return [(0, 0, 0)]

    def visuel_monde(self, facing):
        """Cellules que le modèle dessine (il peut déborder du bloc pour les meubles cfm)."""
        if self.mod == "heraliz_loot":
            return [tourner_loot(c, facing) for c in self.cellules]
        y = (self.rot or {}).get(facing, 0)
        return [tourner_json(c, y) for c in (self.visuel or [(0, 0, 0)])]

    def avant(self, facing):
        """Direction vers laquelle regarde la face avant du modèle."""
        if self.mod == "heraliz_loot":
            return facing
        # cfm (modèles d'ArkaniaZ dessinés l'avant vers +Z, comme les mêmes modèles repris dans HeraliZ Loot) :
        y = (self.rot or {}).get(facing, 0)
        return ["south", "west", "north", "east"][(y // 90) % 4]


def _lang(res, mod):
    out = {}
    for d in res.dossiers:
        f = d / ("assets/%s/lang/fr_fr.json" % mod)
        if f.is_file():
            out.update(json.loads(f.read_text(encoding="utf-8")))
    return out


def catalogue(res=None):
    """id complet (mod:nom) -> Prop, pour tous les props connus (y compris les anciens blocs cfm retirés du jar)."""
    res = res or Ressources()
    out = {}
    # ---- HeraliZ Loot
    langl = _lang(res, "heraliz_loot")
    conts = json.loads((LOOT / "commun/resources/assets/heraliz_loot/conteneurs.json").read_text(encoding="utf-8"))
    t7 = re.findall(r'new TypeCoffre\("(t7_\w+)",\s*\d+,(.*?)\)\);', (LOOT / "commun/java/fr/heraliz/loot/TypeCoffre.java")
                    .read_text(encoding="utf-8"))
    entrees = [(o["nom"], o.get("boites") or [[16 - x[3], x[1], 16 - x[5], 16 - x[0], x[4], 16 - x[2]] for x in o.get("collision") or []],
                o.get("matiere", "bois")) for o in conts]
    for nom, boites in t7:
        bx = [[float(v) for v in b.split(",")] for b in re.findall(r"\{([^}]*)\}", boites)]
        entrees.append((nom, bx, "metal"))
    modeles = {}
    for nom, boites, _mat in entrees:
        bs = res.json("assets/heraliz_loot/blockstates/%s.json" % nom) or {}
        mods = modeles_blockstate(bs)
        modele = mods[0] if mods else nom
        pieces, ext, groupe, cat = regle_loot(nom)
        p = Prop(id="heraliz_loot:" + nom, mod="heraliz_loot", nom=langl.get("block.heraliz_loot." + nom, nom),
                 niveau=_niveau(nom), categorie=cat, pieces=pieces, ext=ext, groupe=groupe,
                 cellules=cellules_boites(boites or [[0, 0, 0, 16, 16, 16]]), rot=rotation_y(bs), modele=modele,
                 dayz=True, structure=False, loot=not nom.startswith("vehicules_"), decrit=ARK.get(nom, ""),
                 existe=True, be=nom.startswith("t7_"), gab=gabarit(nom, cat),
                 haut=max([b[4] for b in boites] or [16]), famille=nom)
        p.visuel = p.cellules
        out[p.id] = p
        modeles.setdefault(modele, []).append(p.id)
    # ---- cfm
    langu = _lang(res, "heralisz_utils")
    noms_cfm = set()
    if CFM_JAR is not None:
        with zipfile.ZipFile(CFM_JAR) as z:
            noms_cfm = chaines_classe(z.read("com/mrcrayfish/furniture/core/ArkaniaZModBlocks.class"))
            bss = {n[len("assets/cfm/blockstates/"):-5] for n in z.namelist()
                   if n.startswith("assets/cfm/blockstates/") and n.endswith(".json")}
        noms_cfm &= bss | {"century_ceiling_fan"}
    for nom in sorted(noms_cfm):
        bs = res.json("assets/cfm/blockstates/%s.json" % nom) or {}
        mods = modeles_blockstate(bs)
        boites, textures = res.modele(mods[0]) if mods else ([], {})
        texs = " ".join(sorted({t.split("/")[-1] for k, t in textures.items() if k != "particle"}))
        pack = next((t.split(":")[-1].split("/")[1] for k, t in textures.items()
                     if k != "particle" and len(t.split(":")[-1].split("/")) > 2), "")
        structure = bool(CFM_STRUCTURE.match(nom)) or bool(CFM_MATERIAU.match(nom)) and pack == "sovietblocks"
        admin = bool(CFM_ADMIN.match(nom))
        pieces, ext, dayz, decrit = _regle_cfm(nom, pack, texs)
        vis = cellules_boites(boites) if boites else [(0, 0, 0)]
        p = Prop(id="cfm:" + nom, mod="cfm", nom=langu.get("block.cfm." + nom) or _nom_cfm(nom), niveau=0,
                 categorie=_categorie(pieces, ext, nom) if not structure else "structure", pieces=pieces, ext=ext,
                 groupe=_groupe_cfm(decrit), cellules=[(0, 0, 0)], visuel=vis, rot=rotation_y(bs),
                 modele=mods[0] if mods else nom, dayz=dayz and not admin and not structure, structure=structure or admin,
                 loot=False, decrit=decrit or texs[:60], existe=True, be=None, gab="decor",
                 haut=max([b[4] for b in boites] or [16]), famille=famille_cfm(nom, decrit))
        out[p.id] = p
        modeles.setdefault(p.modele, []).append(p.id)
    # ---- décors de HeraliZ Utils et HeraliZ Décors (seulement ceux qui sont des objets posés, pas des matériaux)
    for mod, liste in DECORS.items():
        lang = _lang(res, mod)
        for nom, (pieces, ext) in liste.items():
            bs = res.json("assets/%s/blockstates/%s.json" % (mod, nom))
            if bs is None:
                continue
            p = Prop(id="%s:%s" % (mod, nom), mod=mod, nom=lang.get("block.%s.%s" % (mod, nom), nom), niveau=0,
                     categorie=_categorie(_ens(pieces, PIECES), _ens(ext, EXTERIEURS), nom),
                     pieces=_ens(pieces, PIECES), ext=_ens(ext, EXTERIEURS), groupe="", cellules=[(0, 0, 0)],
                     visuel=[(0, 0, 0)], rot=rotation_y(bs), modele=nom, dayz=True, structure=False, loot=False,
                     decrit="", existe=True, be=None, gab="decor", haut=16, famille=nom)
            out[p.id] = p
    # ---- anciens blocs cfm retirés du jar (ils disparaissent au chargement d'un secteur) : équivalents HeraliZ Loot
    for p in list(out.values()):
        if p.mod == "heraliz_loot":
            p.doublon = None
    for mod_, ids in modeles.items():
        if len(ids) > 1:
            for i in ids:
                out[i].doublon = ids[0]
    return out


# ce que montrent des modèles cfm assez semblables pour être échangés entre eux
FAMILLES_VUES = {"cartons", "fûts", "feuilles mortes", "bouteilles au sol", "sang", "cadavre", "pneus", "banc",
                 "poubelle", "brasero", "caisse d'armes"}
# meubles cfm aux noms numérotés sans rapport entre eux (« Model 207 », « Detritus 12 »...) : une famille par ce
# qu'ils montrent (cartons, fûts, feuilles mortes...), sinon aucune (jamais échangés)
CFM_NUMEROTES = re.compile(r"^(model|detritus|eta|si_ren|board|sign_map|safe_planque|graffiti|bedrock)_\d+$")


def famille_cfm(nom, decrit=""):
    """Famille visuelle d'un meuble cfm (variantes d'un même objet) : nom sans numéro, couleur ni « _a »."""
    if CFM_NUMEROTES.match(nom):
        return ("vu:" + decrit) if decrit in FAMILLES_VUES else nom
    f = re.sub(r"(_a)?$", "", nom)
    f = re.sub(r"_?\d+(_\d+)?$", "", f)
    f = re.sub(r"_(gray|grey|purple|red|blue|green|white|brown|open|closed|single|with_.*)$", "", f)
    return f


def _nom_cfm(nom):
    return nom.replace("_", " ").capitalize()


def _groupe_cfm(decrit):
    return {"frigo": "frigo", "évier": "vasque", "lave-linge": "lave_linge", "baignoire": "baignoire"}.get(decrit or "", "")


def _regle_cfm(nom, pack, texs):
    """(pièces, extérieurs, gardé pour DayZ, ce qu'il montre) d'un meuble cfm."""
    decrit = ""
    cle = None
    for k in sorted(CFM_FAMILLES, key=len, reverse=True):
        if nom.startswith(k + "_") or nom == k or pack.startswith(k):
            cle = k
            break
    if pack in ("santa_decoration", "christmas", "christmas_furniture_v1"):
        cle = "noel"
    if cle in (None, "blocks", "custom", "sovietblocks", "arkaniaz"):
        for motif, d, p, e in CFM_MOTS:
            if re.search(motif, texs):
                return _ens(p, PIECES), _ens(e, EXTERIEURS), True, d
    p, e, dayz = CFM_FAMILLES.get(cle or "blocks")
    return _ens(p, PIECES), _ens(e, EXTERIEURS), dayz, decrit


DECORS = {
    "heralisz_utils": {
        "herisson": ("-", "base route"), "barbeles": ("-", "base route"),
        "barbeles_piquet": ("-", "base route"), "eolienne": ("-", "ferme"), "eolienne_moderne": ("-", "ferme chantier"),
        "eolienne_moderne_usee": ("-", "ferme chantier"), "eolienne_usee": ("-", "ferme"), "drapeau_armee": ("-", "base"),
        "drapeau_quarantaine": ("-", "base rue"), "drapeau_heraliz": ("-", "base rue"), "gyrophare": ("militaire", "base route"),
        "chateau_eau": ("-", "ferme rue"), "chevalet_pompage": ("-", "chantier"), "grue_tour": ("-", "chantier"),
        "panneau_radiation": ("labo", "base chantier"), "decanteur": ("-", "chantier"), "manche_a_air": ("-", "base"),
        "phare_lanterne": ("-", "quai"), "melangeur": ("-", "chantier"), "radar_antenne": ("-", "base"),
        "mat_antenne": ("-", "base"), "tuyau": ("entrepot", "chantier"), "ventilateur": ("entrepot garage", "chantier"),
        "corbeaux": ("-", "ferme cimetiere"), "tableau_gare": ("gare", "quai"), "epave_bleue": ("-", "route rue"),
        "epave_rouge": ("-", "route rue"), "epave_brulee": ("-", "route rue"), "epave_retournee": ("-", "route"),
        "voiture_couchee": ("-", "route rue"), "filet_camouflage": ("-", "base"), "generateur": ("garage entrepot", "chantier base"),
        "lampadaire_pied": ("-", "rue"), "lanterne_corde": ("-", "camp quai"), "poulie": ("-", "chantier quai"),
        "seau_corde": ("-", "ferme"), "vanne": ("entrepot", "chantier"), "moulin_ailes": ("-", "ferme"),
        "roue_aubes": ("-", "ferme"),
    },
    "heraliz_decors": {
        "lanterne_fer": ("*", "rue"), "lampe_suspendue": ("*", ""), "guirlande": ("commerce", "rue"),
        "projecteur": ("militaire entrepot", "base"), "tache_sang": ("*", "rue route"), "tapis_berbere": ("salon chambre commerce", "rue"),
    },
}


# ------------------------------------------------------------------------------------------------------- équivalences
def equivalent_loot(nom_cfm, cat):
    """Conteneur de HeraliZ Loot qui reprend le modèle d'un ancien meuble cfm retiré du jar (ou None)."""
    m = re.match(r"loot_([a-z]+)_(\d+)$", nom_cfm)
    if m:
        fam = CFM_VERS_LOOT_FAMILLE.get(m.group(1))
        if fam:
            for pid in cat:
                if pid.startswith("heraliz_loot:") and re.match(r"heraliz_loot:[a-z]+\d_loot_%s_%s$" % (fam, m.group(2)), pid):
                    return pid
    m = re.match(r"eta_(\d+)$", nom_cfm)
    if m and "heraliz_loot:t1_eta_%s" % m.group(1) in cat:
        return "heraliz_loot:t1_eta_%s" % m.group(1)
    if nom_cfm == "box_food" and "heraliz_loot:nourriture3_caisse_nourriture" in cat:
        return None  # modèle différent (box_food) : pas d'équivalent sûr
    return None


def main():
    sortie = sys.argv[sys.argv.index("--sortie") + 1] if "--sortie" in sys.argv else None
    cat = catalogue()
    lignes = ["# Catalogue des props", "",
              "Taille = cellules occupées (L x H x P) ; doublon = même modèle 3D qu'un autre prop.", ""]
    par_mod = defaultdict(list)
    for p in cat.values():
        par_mod[p.mod].append(p)
    for mod in ("heraliz_loot", "cfm", "heralisz_utils", "heraliz_decors"):
        ps = sorted(par_mod.get(mod, []), key=lambda p: (p.niveau, p.categorie, p.id))
        lignes += ["## %s (%d)" % (mod, len(ps)), "", "| id | nom | niv. | catégorie | taille | pièces | extérieur | groupe | remarque |",
                   "|---|---|---|---|---|---|---|---|---|"]
        for p in ps:
            cel = p.visuel or p.cellules
            xs = [c[0] for c in cel]; ys = [c[1] for c in cel]; zs = [c[2] for c in cel]
            taille = "%dx%dx%d" % (max(xs) - min(xs) + 1, max(ys) - min(ys) + 1, max(zs) - min(zs) + 1)
            rem = []
            if p.structure:
                rem.append("structure/admin")
            elif not p.dayz:
                rem.append("hors thème DayZ")
            if p.doublon and p.doublon != p.id:
                rem.append("doublon de " + p.doublon.split(":")[1])
            if p.decrit:
                rem.append(p.decrit)
            pieces = "partout" if len(p.pieces) >= 15 else " ".join(sorted(p.pieces)) or "-"
            lignes.append("| %s | %s | %s | %s | %s | %s | %s | %s | %s |" % (
                p.id.split(":")[1], p.nom, p.niveau or "", p.categorie, taille, pieces,
                " ".join(sorted(p.ext)) or "-", p.groupe or "", " ; ".join(rem)))
        lignes.append("")
    texte = "\n".join(lignes) + "\n"
    if sortie:
        Path(sortie).write_text(texte, encoding="utf-8")
        print("[catalogue] %d props écrits dans %s" % (len(cat), sortie))
    else:
        print(texte)


if __name__ == "__main__":
    main()
