"""Équilibrage des armes TaCZ contre les joueurs, raretés des armes, des accessoires et des munitions.

Choix du propriétaire (14/10) : dégâts cohérents avec les armures (Armurerie 0.15.0 : une seule protection, tenue
complète de 12 % en Commun à 60 % en Unique au corps, 10 % à 50 % à la tête) ; raretés des armes selon leur famille :
pistolet dès Commun, mitraillette et fusil à pompe dès Peu commun, fusil d'assaut dès Rare, fusil de précision dès
Rare (les meilleurs Épique et plus), mitrailleuse Épique, lance-grenades Légendaire ; aucune arme n'est assez forte
pour Unique ; tous les couteaux Unique (seulement dans les caisses, jamais dans le loot de la carte).
Accessoires : plus la rareté est basse, moins l'accessoire apporte (rareté donnée d'après la force de ses bonus).

Les gunpacks des autres auteurs ne sont PAS modifiés : les dégâts contre les joueurs sont réglés par le mod Tactique
(serveur, `fr.heraliz.tactique.serveur.DegatsArmes`) d'après le fichier écrit ici :
  serveur/config/heraliz_tactique/armes.json : pour chaque arme, « degats » = dégâts d'une balle à courte distance contre
  un joueur (arme à plombs : d'une décharge entière, que TaCZ partage entre les plombs, comme le « damage » des
  gunpacks), « tete » = multiplicateur d'un tir à la tête, « rarete », « plafond » (armes à plombs). La baisse
  avec la distance et les accessoires de l'arme gardent leur effet (même proportion qu'avant). Arme absente du fichier
  (lance-grenades, couteaux, Wonder Weapons) : dégâts de TaCZ inchangés. Contre les créatures (zombies...) : inchangé.
  Armes à plombs seulement : « plombs » (nombre de plombs imposé à une arme qui n'en tire qu'un) et « dispersion_min »
  (écart minimal des plombs, en degrés, même en visant) : appliqués par le serveur (mixin TaczPlombsMixin du mod
  Tactique), gunpacks intacts.
Et la section « armes », « accessoires », « munitions » de serveur/config/heraliz_loot/raretes.json (couleur des cases).

Joueur : 20 points de vie. Repères (fusil d'assaut 5,56 Rare, 5,2 par balle) : 4 balles sans armure, 6 en Rare, 10 en
Unique ; tir à la tête ×1,7 : 3 sans casque, 5 avec un casque Unique.

Usage : python outils/equilibrage_armes.py [--tableau] [--pompes]
  --pompes : tableau des fusils à pompe (plombs × dégâts, tirs pour tuer à bout portant et à 15-20 blocs, sans armure
             et avec une tenue Rare, Épique, Légendaire), calculé comme le serveur (plafond, tête, armures).
"""
import argparse
import glob
import json
import os
import re
import sys
from pathlib import Path
from collections import OrderedDict, Counter

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from silencieux import lire  # noqa: E402  (lecture tolérante : commentaires, virgules finales)

ICI = os.path.dirname(os.path.abspath(__file__))
DEPOT = os.path.normpath(os.path.join(ICI, ".."))
TACZ = os.path.join(DEPOT, "serveur", "tacz")
SORTIE_ARMES = os.path.join(DEPOT, "serveur", "config", "heraliz_tactique", "armes.json")
RARETES_JSON = os.path.join(DEPOT, "serveur", "config", "heraliz_loot", "raretes.json")

RANGS = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]
# Dégâts selon la rareté, à famille égale
BONUS_RARETE = {"commun": 0.92, "peu_commun": 0.96, "rare": 1.0, "epique": 1.04, "legendaire": 1.08, "unique": 1.12}

# Dégâts d'une balle à courte distance (rareté Rare) et multiplicateur de la tête, par famille
FAMILLES = {
    "pistolet_9": (4.5, 1.6), "pistolet_45": (5.5, 1.6), "pistolet_50": (8.0, 1.6),
    "smg_9": (4.0, 1.5), "smg_45": (4.6, 1.5),
    "fusil_556": (5.2, 1.7), "fusil_762": (6.0, 1.7), "fusil_308": (7.5, 1.7), "fusil_308_rapide": (6.8, 1.7),
    "mitrailleuse_556": (5.4, 1.6), "mitrailleuse_762": (6.4, 1.6), "minigun": (4.5, 1.5),
    "precision_308": (16.0, 2.0), "precision_semi": (9.6154, 2.0), "precision_762": (14.0, 2.0),
    "precision_338": (20.0, 2.0), "precision_50": (22.0, 2.0),
    # 24/10 (choix du propriétaire) : SSG 08 comme les autres snipers (20,8 en Épique), Dragunov 10 en Épique,
    # .50 (M82A3, M95, M82A2) un peu moins forts : 23,76 en Légendaire au lieu de 25,92
    # fusils à pompe : dégâts d'une décharge entière, que TaCZ partage lui-même entre les plombs. 24/10 (choix du
    # propriétaire : « le shotgun ne fait aucun dégât, 1 cœur par plomb ; pas de gros one tap mais utilisable ») :
    # décharge 36 (4 par plomb pour 9 plombs), plafonnée par tir (PLAFOND_TIR).
    # 31/10 : jusqu'ici ce script divisait encore la décharge par le nombre de plombs, alors que TaCZ 1.1.8 la divise
    # déjà (EntityKineticBullet.applyShotgunDamageSpread) : chaque plomb faisait 36 / 9 / 9 = 0,44 au lieu de 4, et une
    # décharge entière 4 dégâts (2 cœurs) au lieu de 36 (d'où « presque aucun dégât »). La décharge est maintenant
    # écrite telle quelle.
    # 03/11 (retour du propriétaire après son premier passage en jeu, 02/11 au soir) : « les pompes font tous 3 cœurs
    # peu importe la rareté ; pour les automatiques c'est bien, pour les pompes pas auto c'est peu (armure Épique) ;
    # certains tirent une seule balle ». Avant, la décharge valait 34 à 39 et le plafond 13 la coupait toujours : un plomb
    # sur trois suffisait et toutes les armes faisaient 13. Maintenant la décharge ENTIÈRE (tous les plombs au corps, à
    # bout portant) est le vrai chiffre, plomb par plomb (moins de 2 dégâts par plomb : un seul plomb ne fait presque
    # rien), selon le mécanisme de l'arme, puis la rareté (BONUS_RARETE) :
    #   - pompe_manuelle (à pompe, on réarme à chaque coup : M870, M870 tactique, Stakeout) : 17,5 en Rare ;
    #   - pompe_double (canons doubles, 2 coups puis on recharge : DB, 686, Olympia, DB-4 Ursus) : 15 en Rare ;
    #   - pompe_auto (semi-automatiques et automatiques à chargeur : AA-12, Saiga 12) : 12 en Rare, 12,96 en Légendaire
    #     (comme avant : 13 par tir, « c'est bien car sinon trop fort »).
    # Famille d'une arme de type « shotgun » sans famille imposée : culasse « manual_action » = pompe_manuelle, au plus 2
    # coups dans l'arme = pompe_double, sinon pompe_auto (fonction famille()).
    "pompe_manuelle": (17.5, 1.25), "pompe_double": (15.0, 1.25), "pompe_auto": (12.0, 1.25),
    "pistolet_pompe": (11.5, 1.25), "pistolet_50bmg": (14.0, 1.6),
}
# Dégâts au plus d'UN tir sur un même joueur (tous plombs, tête comprise, avant l'armure ; DegatsArmes du mod
# tactique). Objectif du propriétaire (24/10) : jamais tué d'un tir sans armure (20 PV), deux tirs bien placés
# suffisent sans armure. 03/11 : le plafond ne coupe plus que les plombs à la tête (19 : une décharge entière dans la
# tête sans casque ne tue jamais) pour les armes qu'on réarme ; les automatiques gardent 13 (= leur décharge entière,
# comme avant), le pistolet à plombs 12. Une rafale (deux canons d'un coup) compte comme un seul tir.
PLAFOND_TIR = {"pompe_manuelle": 19.0, "pompe_double": 19.0, "pompe_auto": 13.0, "pistolet_pompe": 12.0}
# Armes à plombs (familles ci-dessus) : aucune ne tire une seule balle, et les plombs se dispersent toujours.
#   - PLOMBS_SI_BALLE_UNIQUE : plombs imposés à une arme de ces familles dont le gunpack ne tire qu'un projectile
#     (DB-4 Ursus, ancien fusil .50 passé en calibre 12 le 22/10 : 1 balle) ;
#   - DISPERSION_MIN : écart minimal des plombs (degrés, même en visant, accessoires compris). Le Stakeout visait à
#     0,25 : ses 8 plombs partaient ensemble, comme une seule balle. 2,5 = le plus serré des autres (DB long, 686,
#     Olympia) : à 10 blocs les plombs restent dans ~0,9 bloc, à 20 blocs dans ~1,7 bloc (un joueur fait 0,5 de large).
FAMILLES_PLOMBS = ("pompe_manuelle", "pompe_double", "pompe_auto", "pistolet_pompe")
PLOMBS_SI_BALLE_UNIQUE = 8
DISPERSION_MIN = 2.5

# Famille imposée (sinon : type de l'arme + munition)
FAMILLE_ARME = {
    "tacz:deagle": "pistolet_50", "maxstuff:deagle_12g": "pistolet_pompe", "maxstuff:deagle_50bmg": "pistolet_50bmg",
    "tacz:minigun": "minigun",
    "maxstuff:ar10": "fusil_308_rapide", "maxstuff:ar10b": "fusil_308_rapide", "maxstuff:mk11": "fusil_308_rapide",
    "maxstuff:dragonuv_svd": "precision_semi", "arkaniaz:ssg_08": "precision_338",
}

# Rareté de chaque arme (lance-grenades : Légendaire ; armes absentes : plancher de leur type)
RARETE_ARME = {
    # pistolets (dès Commun)
    "tacz:glock_17": "commun", "arkaniaz:p250": "commun", "arkaniaz:usp": "commun",
    "maxstuff:m17": "commun", "tacz:m1911": "commun", "tacz:cz75": "peu_commun",
    "tacz:p320": "peu_commun", "arkaniaz:tec9": "peu_commun", "maxstuff:glock_bulpup": "peu_commun",
    "tacz:b93r": "rare", "arkaniaz:g18c": "rare", "maxstuff:glock_18c": "rare", "maxstuff:mk23": "rare",
    "arkaniaz:python": "rare", "maxstuff:glock_54": "rare",
    "tacz:deagle": "epique", "maxstuff:thunderbird_short": "epique", "maxstuff:deagle_12g": "epique",
    "maxstuff:deagle_50bmg": "legendaire",
    # mitraillettes (dès Peu commun)
    "tacz:uzi": "peu_commun", "arkaniaz:sr_2m": "peu_commun", "arkaniaz:pp19": "peu_commun",
    "heraliz_fuse:mp5k": "peu_commun", "maxstuff:hk_mp5k": "peu_commun", "heraliz_fuse:mp5_classic": "peu_commun",
    "tacz:ump45": "rare", "arkaniaz:ump9": "rare", "arkaniaz:evo3": "rare",
    "heraliz_fuse:mp7r": "rare", "heraliz_fuse:ro635v3": "rare", "maxstuff:hk_mp5sd": "rare",
    "tacz:p90": "epique", "maxstuff:ar57": "epique", "tacz:vector45": "epique", "maxstuff:vector9": "epique",
    "arkaniaz_custom:vector_firmax": "legendaire", "arkaniaz_custom:vectorhy": "legendaire",
    # fusils à pompe (dès Peu commun)
    "tacz:db_short": "peu_commun", "tacz:db_long": "peu_commun", "arkaniaz:686": "peu_commun",
    "heraliz_fuse:olympia": "peu_commun",
    "tacz:m870": "rare", "maxstuff:m870t": "rare", "heraliz_fuse:stakeout": "epique", "maxstuff:db_short_tactical": "rare",
    "maxstuff:saiga12": "legendaire", "tacz:aa12": "legendaire",
    "maxstuff:db_50": "peu_commun",
    # fusils d'assaut et de combat (dès Rare)
    "tacz:ak47": "rare", "arkaniaz:aks74u": "rare", "maxstuff:aks74u": "rare", "tacz:m16a1": "rare",
    "arkaniaz:9a91": "rare", "arkaniaz:an94": "rare", "tacz:type_81": "rare", "maxstuff:ak74": "rare",
    "arkaniaz:882": "rare", "tacz:sks_tactical": "rare", "maxstuff:g36": "rare", "maxstuff:arp": "rare",
    "maxstuff:colt_933": "rare", "maxstuff:xm177": "rare", "tacz:m4a1": "rare",
    "tacz:aug": "epique", "maxstuff:aug_a2": "epique", "tacz:g36k": "epique", "tacz:hk416d": "epique",
    "tacz:scar_l": "epique", "arkaniaz:galilace": "epique", "arkaniaz:mod2": "epique", "arkaniaz:type20": "epique",
    "maxstuff:ak12": "epique", "maxstuff:adar2": "epique", "maxstuff:sr16": "epique", "maxstuff:mk18_burst": "epique",
    "maxstuff:z15_a": "epique", "maxstuff:hk_g33": "epique", "maxstuff:honey_badger": "epique",
    "maxstuff:scar_pdw": "epique", "maxstuff:scar_sbr": "epique", "maxstuff:ar762": "epique", "maxstuff:mk47": "epique",
    "tacz:hk_g3": "epique", "tacz:scar_h": "epique", "heraliz_fuse:m14": "epique", "maxstuff:hk417": "epique",
    "tacz:spr15hb": "epique",
    "tacz:mk14": "legendaire", "maxstuff:ar10": "legendaire", "maxstuff:ar10b": "legendaire",
    "maxstuff:mk11": "legendaire", "maxstuff:ak_alpha": "legendaire",
    # mitrailleuses (Épique)
    "tacz:minigun": "legendaire",
    # fusils de précision (dès Rare ; les meilleurs Épique et Légendaire)
    "arkaniaz:gras1874": "rare", "arkaniaz:gew98": "rare", "arkaniaz:berthier": "rare",
    "arkaniaz:luger1906": "rare", "tacz:springfield1873": "rare",
    "arkaniaz:ssg_08": "epique", "maxstuff:dragonuv_svd": "epique", "tacz:aiawp": "epique",
    "arkaniaz:m200": "legendaire", "arkaniaz:aw50": "legendaire", "arkaniaz:gm6_lynx": "legendaire",
    "arkaniaz:m82a3": "legendaire", "tacz:m95": "legendaire",
}
PLANCHER = {"pistol": "commun", "smg": "peu_commun", "shotgun": "peu_commun", "rifle": "rare", "sniper": "rare",
            "mg": "epique", "rpg": "legendaire"}

# Munitions : rareté selon le calibre
RARETE_MUNITION = {"tacz:9mm": "commun", "tacz:45acp": "commun", "tacz:12g": "peu_commun", "tacz:556x45": "rare",
                   "tacz:762x39": "rare", "tacz:50ae": "rare", "tacz:308": "epique", "tacz:338": "epique",
                   "tacz:50bmg": "legendaire", "tacz:40mm": "legendaire", "tacz:rpg_rocket": "legendaire"}

PACKS_A_PART = {"heraliz_wonder"}  # Wonder Weapons (zombies) : rien ici


def lire_json(p):
    """Lecture tolérante (commentaires, virgules finales des gunpacks), celle de silencieux.py."""
    return lire(Path(p))


def armes():
    """Toutes les armes des gunpacks : id -> {type, data, ammo, amount, damage, couteau}."""
    datas = {}
    for p in glob.glob(os.path.join(TACZ, "*", "data", "*", "data", "guns", "*.json")):
        ns = p.split(os.sep)[-4]
        nom = os.path.splitext(os.path.basename(p))[0]
        datas.setdefault(f"{ns}:{nom}", lire_json(p))
    sortie = OrderedDict()
    for p in sorted(glob.glob(os.path.join(TACZ, "*", "data", "*", "index", "guns", "*.json"))):
        pack = p.split(os.sep)[-6]
        if pack in PACKS_A_PART:
            continue
        ns = p.split(os.sep)[-4]
        gid = f"{ns}:{os.path.splitext(os.path.basename(p))[0]}"
        idx = lire_json(p)
        data_id = idx.get("data", "")
        d = datas.get(data_id, {})
        balle = d.get("bullet", {})
        extra = balle.get("extra_damage") or {}
        sortie[gid] = {
            "type": idx.get("type", "?"),
            "ammo": d.get("ammo", "?"),
            "plombs": int(balle.get("bullet_amount", 1) or 1),
            "degats": float(balle.get("damage", 0) or 0),
            "couteau": data_id == "arkaniaz:knife_data",
            # pour les fusils à pompe (famille, tableau --pompes)
            "culasse": d.get("bolt", "?"),
            "coups": int(d.get("ammo_amount", 0) or 0),
            "rpm": int(d.get("rpm", 0) or 0),
            "visee": float((d.get("inaccuracy") or {}).get("aim", 0) or 0),
            "paliers": [(float("inf") if str(p.get("distance")) == "infinite" else float(p.get("distance", 0)),
                         float(p.get("damage", 0))) for p in (extra.get("damage_adjust") or [])],
        }
    return sortie


def famille(gid, a):
    if gid in FAMILLE_ARME:
        return FAMILLE_ARME[gid]
    t, m = a["type"], a["ammo"]
    if t == "shotgun":
        if a["culasse"] == "manual_action":
            return "pompe_manuelle"
        return "pompe_double" if 0 < a["coups"] <= 2 else "pompe_auto"
    if t == "pistol":
        return {"tacz:45acp": "pistolet_45", "tacz:50ae": "pistolet_50"}.get(m, "pistolet_9")
    if t == "smg":
        return "smg_45" if m == "tacz:45acp" else "smg_9"
    if t == "rifle":
        return {"tacz:762x39": "fusil_762", "tacz:308": "fusil_308"}.get(m, "fusil_556")
    if t == "mg":
        return "mitrailleuse_556" if m == "tacz:556x45" else "mitrailleuse_762"
    if t == "sniper":
        return {"tacz:338": "precision_338", "tacz:50bmg": "precision_50", "tacz:762x39": "precision_762"}.get(m, "precision_308")
    return None  # lance-grenades : dégâts de TaCZ


def regler():
    liste = armes()
    reglage = OrderedDict()
    raretes = OrderedDict()
    for gid, a in liste.items():
        if a["couteau"]:
            raretes[gid] = "unique"
            continue
        r = RARETE_ARME.get(gid) or PLANCHER.get(a["type"], "rare")
        raretes[gid] = r
        fam = famille(gid, a)
        if fam is None:
            continue
        base, tete = FAMILLES[fam]
        degats = base * BONUS_RARETE[r]  # arme à plombs : la décharge entière (TaCZ la partage entre les plombs)
        reglage[gid] = OrderedDict([("famille", fam), ("rarete", r), ("degats", round(degats, 2)), ("tete", tete)])
        if fam in PLAFOND_TIR:
            reglage[gid]["plafond"] = PLAFOND_TIR[fam]
        if fam in FAMILLES_PLOMBS:
            if a["plombs"] < 2:
                reglage[gid]["plombs"] = PLOMBS_SI_BALLE_UNIQUE
            reglage[gid]["dispersion_min"] = DISPERSION_MIN
    return liste, reglage, raretes


# ------------------------------------------------------------------ accessoires
def accessoires():
    """Tous les accessoires : id -> {type, data (dict)}."""
    datas = {}
    for p in glob.glob(os.path.join(TACZ, "*", "data", "*", "data", "attachments", "*.json")):
        ns = p.split(os.sep)[-4]
        datas.setdefault(f"{ns}:{os.path.splitext(os.path.basename(p))[0]}", lire_json(p))
    zooms = {}
    for p in glob.glob(os.path.join(TACZ, "*", "assets", "*", "display", "attachments", "*.json")):
        ns = p.split(os.sep)[-4]
        try:
            z = lire_json(p).get("zoom") or []
        except (ValueError, OSError):
            z = []
        zooms[f"{ns}:{os.path.splitext(os.path.basename(p))[0]}"] = max([float(v) for v in z] or [1.0])
    sortie = OrderedDict()
    for p in sorted(glob.glob(os.path.join(TACZ, "*", "data", "*", "index", "attachments", "*.json"))):
        ns = p.split(os.sep)[-4]
        aid = f"{ns}:{os.path.splitext(os.path.basename(p))[0]}"
        idx = lire_json(p)
        sortie[aid] = {"type": idx.get("type", "?"), "data": datas.get(idx.get("data", ""), {}),
                       "zoom": zooms.get(idx.get("display", ""), 1.0)}
    return sortie


def effet(v, defaut_mult=1.0):
    """Valeur d'un modificateur (nouveau format) en « facteur » : addend ignoré ici, percent et multiplier combinés."""
    if isinstance(v, (int, float)):
        return float(v)
    if not isinstance(v, dict):
        return defaut_mult
    f = 1.0
    if "percent" in v:
        try:
            f *= 1 + float(v["percent"])
        except (TypeError, ValueError):
            pass
    if "multiplier" in v:
        try:
            f *= float(v["multiplier"])
        except (TypeError, ValueError):
            pass
    return f


def score(a):
    """Force des bonus d'un accessoire (plus haut = meilleur) : recul, dispersion, visée, dégâts, silencieux."""
    d = a["data"]
    s = 0.0
    rec = d.get("recoil") or d.get("recoil_modifier") or {}
    for axe in ("pitch", "yaw"):
        v = rec.get(axe) if isinstance(rec, dict) else None
        if isinstance(v, dict):
            s += (1 - effet(v)) * 100 - float(v.get("addend", 0) or 0) * 20
        elif isinstance(v, (int, float)):
            s += -float(v) * 20  # ancien format : addend (négatif = moins de recul)
    for cle in ("inaccuracy", "aim_inaccuracy"):
        v = d.get(cle)
        if isinstance(v, dict):
            s += (1 - effet(v)) * 50 - float(v.get("addend", 0) or 0) * 10
    if "inaccuracy_addend" in d:
        s += -float(d["inaccuracy_addend"]) * 40
    ads = d.get("ads")
    if isinstance(ads, dict):
        s += (1 - effet(ads)) * 40 - float(ads.get("addend", 0) or 0) * 300
    if "ads_addend" in d:
        s += -float(d["ads_addend"]) * 300
    for cle, poids in (("damage", 160), ("armor_ignore", 60), ("head_shot", 120), ("effective_range", 40)):
        v = d.get(cle)
        if isinstance(v, dict):
            s += (effet(v) - 1) * poids + float(v.get("addend", 0) or 0) * poids
    sil = d.get("silence")
    if isinstance(sil, dict) and (sil.get("use_silence_sound") or sil.get("distance_addend") is not None):
        s += 25
    return s


SEUILS_SCORE = [(-1e9, "commun"), (8, "peu_commun"), (20, "rare"), (35, "epique"), (55, "legendaire")]
RARETE_ACCESSOIRE = {
    # munitions spéciales et accessoires qui touchent aux dégâts
    "tacz:ammo_mod_hp": "legendaire", "tacz:ammo_mod_fmj": "epique", "tacz:ammo_mod_he": "legendaire",
    "tacz:ammo_mod_i": "epique", "gucci_attachments:stock_precise": "legendaire",
}


def rarete_accessoire(aid, a):
    if aid in RARETE_ACCESSOIRE:
        return RARETE_ACCESSOIRE[aid]
    t = a["type"]
    if t == "scope":
        z = a["zoom"]
        return "commun" if z <= 1.35 else "peu_commun" if z <= 2.0 else "rare" if z <= 3.3 else "epique" if z <= 4.5 else "legendaire"
    if t == "extended_mag":
        m = re.search(r"(\d)$", aid)
        n = int(m.group(1)) if m else 1
        return {1: "peu_commun", 2: "rare", 3: "epique"}.get(n, "rare")
    s = score(a)
    r = "commun"
    for seuil, nom in SEUILS_SCORE:
        if s >= seuil:
            r = nom
    return r


# ------------------------------------------------------------------ tableau des fusils à pompe (--pompes)
# Tenue complète (Armurerie, modules/equipement/outils/equilibrage_armures.py, TENUE) : % au corps, % à la tête ; le
# plafond commun des armures (60 %, heraliz_armory-common.toml) n'est atteint par aucune de ces tenues.
TENUES = [("sans armure", 0, 0), ("Rare", 32, 26), ("Épique", 41, 34), ("Légendaire", 50, 42)]
PV_JOUEUR = 20.0
# Silhouette d'un joueur debout, de face, arme en main (hitbox « en morceaux » du serveur, HitboxCorps : cubes du modèle
# de Minecraft à l'échelle 0,9375, un demi-pixel de plus autour de la tête, un quart autour du reste) : rectangles
# (x min, x max, y min, y max) en blocs, y depuis les pieds ; vrai si c'est la tête.
_PX = 0.9375 / 16
SILHOUETTE = [
    (-4.5 * _PX, 4.5 * _PX, 23.5 * _PX, 32.5 * _PX, True),       # tête
    (-4.25 * _PX, 4.25 * _PX, 11.75 * _PX, 24.25 * _PX, False),  # torse
    (-8.25 * _PX, -3.75 * _PX, 19.75 * _PX, 24.25 * _PX, False),  # bras tendus vers l'avant (vus par le bout)
    (3.75 * _PX, 8.25 * _PX, 19.75 * _PX, 24.25 * _PX, False),
    (-4.15 * _PX, 4.15 * _PX, -0.25 * _PX, 12.25 * _PX, False),  # jambes
]
VISEE_Y = (11.75 + 24.25) / 2 * _PX  # visée au milieu du torse


def _morceau(x, y):
    """None (manqué), True (tête) ou False (corps)."""
    for x0, x1, y0, y1, tete in SILHOUETTE:
        if x0 <= x <= x1 and y0 <= y <= y1:
            return tete
    return None


def _palier(paliers, origine, distance):
    """Dégâts du gunpack à cette distance (paliers « damage_adjust » de TaCZ, en marches), ramenés à « damage »."""
    for d, dmg in paliers:
        if distance < d:
            return dmg / origine if origine > 0 else 1.0
    return 1.0


def decharge(a, r, distance, tenue, essais, hasard):
    """Moyennes sur `essais` décharges visées au milieu du torse : (plombs qui touchent, dégâts reçus après armure).
    Même calcul que le serveur : dispersion de Minecraft (Projectile.shoot : écart en triangle de 0,0172275 x dispersion
    sur chaque axe), dégâts de TaCZ partagés entre les plombs puis ramenés à « degats » (DegatsArmes), tête x « tete »,
    plafond d'un tir (avant l'armure), armure du corps ou de la tête (ProtectionEvents)."""
    n = r.get("plombs", a["plombs"])
    largeur = 0.0172275 * max(a["visee"], r.get("dispersion_min", 0.0))
    base = r["degats"] / n * _palier(a["paliers"], a["degats"], distance)
    plafond = r.get("plafond", 1e9)
    _, corps, tete = tenue
    touches = degats = 0.0
    for _ in range(essais):
        cumul = 0.0
        for _ in range(n):
            x = distance * largeur * (hasard.random() - hasard.random())
            y = VISEE_Y + distance * largeur * (hasard.random() - hasard.random())
            m = _morceau(x, y)
            if m is None:
                continue
            touches += 1
            coup = base * (r["tete"] if m else 1.0)
            coup = min(coup, max(0.0, plafond - cumul))
            cumul += coup
            degats += coup * (1 - (tete if m else corps) / 100.0)
    return touches / essais, degats / essais


def tableau_pompes(liste, reglage, essais=4000):
    import math
    import random
    hasard = random.Random(3112026)
    lignes = ["| Arme | Rareté | Mécanisme | Plombs × dégâts | Décharge | Bout portant (2 blocs) : tirs pour tuer "
              "sans armure / Rare / Épique / Légendaire | 15 blocs : plombs qui touchent, tirs pour tuer | 20 blocs : "
              "plombs qui touchent, tirs pour tuer |", "|---|---|---|---|---|---|---|---|"]
    noms = {"pompe_manuelle": "à pompe", "pompe_double": "canons doubles", "pompe_auto": "automatique",
            "pistolet_pompe": "pistolet à plombs"}
    for gid, a in liste.items():
        r = reglage.get(gid)
        if not r or r["famille"] not in FAMILLES_PLOMBS:
            continue
        n = r.get("plombs", a["plombs"])
        cellules = []
        for distance in (2.0, 15.0, 20.0):
            tirs, plombs = [], 0.0
            for tenue in TENUES:
                touches, moy = decharge(a, r, distance, tenue, essais if distance > 2 else 200, hasard)
                plombs = touches
                tirs.append(str(math.ceil(PV_JOUEUR / moy - 1e-9)) if moy > 0 else "-")
            texte = " / ".join(tirs)
            cellules.append(texte if distance <= 2 else "%.1f sur %d ; %s" % (plombs, n, texte))
        lignes.append("| %s | %s | %s | %d × %.2f | %.1f (plafond %g) | %s |" % (
            gid, r["rarete"], noms[r["famille"]], n, r["degats"] / n, r["degats"], r["plafond"], " | ".join(cellules)))
    return "\n".join(lignes)


# ------------------------------------------------------------------ écriture
def ecrire(reglage, raretes_armes, raretes_acc):
    os.makedirs(os.path.dirname(SORTIE_ARMES), exist_ok=True)
    doc = OrderedDict()
    doc["_aide"] = ("Dégâts des armes TaCZ contre les JOUEURS (mod Tactique, serveur). « degats » : une balle à courte "
                    "distance (fusil à pompe : la décharge entière, que TaCZ partage entre les plombs) ; la baisse avec la "
                    "distance et les accessoires gardent leur effet. "
                    "« tete » : multiplicateur d'un tir à la tête. « plafond » (fusils à pompe) : dégâts au plus d'un tir, tous plombs. "
                    "« plombs » : plombs imposés à une arme à plombs qui n'en tire qu'un ; « dispersion_min » : écart minimal "
                    "des plombs en degrés (même en visant). Arme absente : dégâts de TaCZ. Fichier ÉCRIT par "
                    "outils/equilibrage_armes.py : changer le script et le relancer. Relu à chaque démarrage du serveur "
                    "et par /recharger armes.")
    doc["actif"] = True
    doc["armes"] = reglage
    with open(SORTIE_ARMES, "w", encoding="utf-8", newline="\n") as f:
        json.dump(doc, f, ensure_ascii=False, indent=2)
        f.write("\n")
    with open(RARETES_JSON, encoding="utf-8") as f:
        data = json.load(f, object_pairs_hook=OrderedDict)
    data["armes"] = raretes_armes
    data["accessoires"] = raretes_acc
    data["munitions"] = OrderedDict(sorted(RARETE_MUNITION.items()))
    with open(RARETES_JSON, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, ensure_ascii=False, indent=2)
        f.write("\n")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--tableau", action="store_true")
    ap.add_argument("--pompes", action="store_true", help="tableau des fusils à pompe (tirs pour tuer selon l'armure)")
    a = ap.parse_args()
    liste, reglage, raretes = regler()
    inconnues = [g for g in RARETE_ARME if g not in liste]
    if inconnues:
        raise SystemExit(f"armes du tableau absentes des gunpacks : {inconnues}")
    acc = accessoires()
    raretes_acc = OrderedDict((aid, rarete_accessoire(aid, x)) for aid, x in acc.items())
    ecrire(reglage, raretes, raretes_acc)
    print(f"{len(liste)} armes ({sum(1 for x in liste.values() if x['couteau'])} couteaux), {len(reglage)} réglées ; "
          f"{len(acc)} accessoires")
    print("armes par rareté :", dict(Counter(raretes.values())))
    print("accessoires par rareté :", dict(Counter(raretes_acc.values())))
    if a.tableau:
        print("\n| Arme | Type | Rareté | Dégâts (balle) | Tête |")
        print("|---|---|---|---|---|")
        for gid, x in liste.items():
            r = reglage.get(gid)
            print(f"| {gid} | {x['type']} | {raretes[gid]} | {r['degats'] if r else '-'} | {r['tete'] if r else '-'} |")
        print("\n| Accessoire | Type | Score | Rareté |")
        print("|---|---|---|---|")
        for aid, x in acc.items():
            print(f"| {aid} | {x['type']} | {score(x):.0f} | {raretes_acc[aid]} |")
    if a.pompes:
        print()
        print(tableau_pompes(liste, reglage))


if __name__ == "__main__":
    main()
