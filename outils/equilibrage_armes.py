"""Équilibrage des armes TaCZ contre les joueurs, raretés des armes, des accessoires et des munitions.

Choix du propriétaire (14/10) : dégâts cohérents avec les armures (Armurerie 0.15.0 : une seule protection, tenue
complète de 12 % en Commun à 60 % en Unique au corps, 10 % à 50 % à la tête) ; raretés des armes selon leur famille :
pistolet dès Commun, mitraillette et fusil à pompe dès Peu commun, fusil d'assaut dès Rare, fusil de précision dès
Rare (les meilleurs Épique et plus), mitrailleuse Épique, lance-grenades Légendaire ; aucune arme n'est assez forte
pour Unique ; tous les couteaux Unique (seulement dans les caisses, jamais dans le loot de la carte).
Accessoires : plus la rareté est basse, moins l'accessoire apporte (rareté donnée d'après la force de ses bonus).

Les gunpacks des autres auteurs ne sont PAS modifiés : les dégâts contre les joueurs sont réglés par le mod Tactique
(serveur, `fr.heraliz.tactique.serveur.DegatsArmes`) d'après le fichier écrit ici :
  serveur/config/heraliz_tactique/armes.json : pour chaque arme, « degats » = dégâts d'une balle (d'un plomb pour un
  fusil à pompe) à courte distance contre un joueur, « tete » = multiplicateur d'un tir à la tête, « rarete ». La baisse
  avec la distance et les accessoires de l'arme gardent leur effet (même proportion qu'avant). Arme absente du fichier
  (lance-grenades, couteaux, Wonder Weapons) : dégâts de TaCZ inchangés. Contre les créatures (zombies...) : inchangé.
Et la section « armes », « accessoires », « munitions » de serveur/config/heraliz_loot/raretes.json (couleur des cases).

Joueur : 20 points de vie. Repères (fusil d'assaut 5,56 Rare, 5,2 par balle) : 4 balles sans armure, 6 en Rare, 10 en
Unique ; tir à la tête ×1,7 : 3 sans casque, 5 avec un casque Unique.

Usage : python outils/equilibrage_armes.py [--tableau]
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
    # fusils à pompe : dégâts d'une décharge entière (répartis sur les plombs)
    "pompe": (24.0, 1.25), "pompe_balle": (20.0, 1.5), "pistolet_pompe": (20.0, 1.25), "pistolet_50bmg": (14.0, 1.6),
}

# Famille imposée (sinon : type de l'arme + munition)
FAMILLE_ARME = {
    "tacz:deagle": "pistolet_50", "maxstuff:deagle_12g": "pistolet_pompe", "maxstuff:deagle_50bmg": "pistolet_50bmg",
    "maxstuff:db_50": "pompe_balle", "tacz:minigun": "minigun",
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
    "arkaniaz:m82a3": "legendaire", "tacz:m95": "legendaire", "maxstuff:m82a2": "legendaire",
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
        sortie[gid] = {
            "type": idx.get("type", "?"),
            "ammo": d.get("ammo", "?"),
            "plombs": int(balle.get("bullet_amount", 1) or 1),
            "degats": float(balle.get("damage", 0) or 0),
            "couteau": data_id == "arkaniaz:knife_data",
        }
    return sortie


def famille(gid, a):
    if gid in FAMILLE_ARME:
        return FAMILLE_ARME[gid]
    t, m = a["type"], a["ammo"]
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
    if t == "shotgun":
        return "pompe"
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
        degats = base * BONUS_RARETE[r]
        if fam.startswith("pompe") or fam == "pistolet_pompe":
            degats /= max(1, a["plombs"])
        reglage[gid] = OrderedDict([("famille", fam), ("rarete", r), ("degats", round(degats, 2)), ("tete", tete)])
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


# ------------------------------------------------------------------ écriture
def ecrire(reglage, raretes_armes, raretes_acc):
    os.makedirs(os.path.dirname(SORTIE_ARMES), exist_ok=True)
    doc = OrderedDict()
    doc["_aide"] = ("Dégâts des armes TaCZ contre les JOUEURS (mod Tactique, serveur). « degats » : une balle (un plomb pour "
                    "un fusil à pompe) à courte distance ; la baisse avec la distance et les accessoires gardent leur effet. "
                    "« tete » : multiplicateur d'un tir à la tête. Arme absente : dégâts de TaCZ. Fichier ÉCRIT par "
                    "outils/equilibrage_armes.py : changer le script et le relancer. Relu à chaque démarrage du serveur.")
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


if __name__ == "__main__":
    main()
