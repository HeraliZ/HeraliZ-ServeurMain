"""Armes, accessoires, armures et composants dans les conteneurs de HeraliZ Loot (T1 à T7).

Réécrit les tables mods/heraliz-loot/src/serveur/resources/data/heraliz_loot/coffres/t1.json à t7.json :
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
Les anciens vêtements heralisz_armor (mod retiré) sont retirés des tables. À relancer après un
changement des raretés, des gunpacks, de l'Armurerie ou des composants.
`--simuler` : ouvre 100 conteneurs par palier (moyenne de 1 000 essais) et compte chaque famille.
"""
import glob, json, random, re, sys
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
COFFRES = RACINE / "mods/heraliz-loot/src/serveur/resources/data/heraliz_loot/coffres"
RARETES = RACINE / "serveur/config/heraliz_loot/raretes.json"
TACZ = RACINE / "serveur/tacz"
ARMES_JSON = RACINE / "serveur/config/heraliz_tactique/armes.json"
COMPOSANTS_DEFS = RACINE / "mods/heraliz-composants/src/commun/java/fr/heraliz/composants/ComponentDefs.java"

# nombre moyen pour 100 conteneurs ouverts
POUR_100 = {
    "armes":       {1: 6, 2: 9, 3: 15, 4: 15, 5: 15, 6: 15, 7: 30},
    # plans de fabrication retirés du jeu le 21/10 (système de craft à refaire) : plus de famille « plans »
    "accessoires": {1: 10, 2: 15, 3: 20, 4: 25, 5: 25, 6: 25, 7: 25},
    "armures":     {1: 20, 2: 20, 3: 20, 4: 20, 5: 20, 6: 20, 7: 35},
    "composants":  {1: 40, 2: 40, 3: 40, 4: 40, 5: 40, 6: 40, 7: 45},
    # sacs à dos (choix du propriétaire, 22/10 : 0,1 % des coffres en T1, 0,5 % en T2 ; un seul sac par coffre)
    "sacs":        {1: 0.1, 2: 0.5, 3: 1, 4: 2, 5: 3, 6: 4, 7: 3},
}
# part de chaque rareté (en %) ; une arme Bleue est déjà une belle trouvaille
PARTS = {
    1: {"commun": 85, "peu_commun": 13, "rare": 1.94, "epique": 0.05, "legendaire": 0.01},
    2: {"commun": 65, "peu_commun": 30, "rare": 4.8, "epique": 0.17, "legendaire": 0.03},
    3: {"commun": 42, "peu_commun": 45, "rare": 12.5, "epique": 0.45, "legendaire": 0.05},
    4: {"commun": 20, "peu_commun": 52, "rare": 26.8, "epique": 1.1, "legendaire": 0.1},
    5: {"commun": 8, "peu_commun": 42, "rare": 46, "epique": 3.6, "legendaire": 0.4},
    6: {"commun": 3, "peu_commun": 30, "rare": 57, "epique": 9, "legendaire": 1},
    7: {"peu_commun": 20, "rare": 56.67, "epique": 18, "legendaire": 4, "unique": 1.33},  # Unique (armes seulement) : 1 coffre sur 250
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
PARTS_SACS = {t: {"commun": 50, "peu_commun": 25, "rare": 15, "epique": 8, "legendaire": 2} for t in range(1, 8)}
PARTS_FAMILLE.update({("sacs", t): v for t, v in PARTS_SACS.items()})
SACS_JAVA = RACINE / "mods/heralisz-backpack/src/commun/java/com/mrcrayfish/backpacked/core/ArkaniaZModItems.java"
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
EMPLACEMENTS = {1: [1, 2], 2: [1, 3], 3: [1, 3], 4: [2, 3], 5: [2, 3], 6: [2, 3], 7: [2, 3]}
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
ARMES_EXCLUES = {"cis:at_rpg_7_lvd"}  # jamais dans le loot (choix du propriétaire)
COMPOSANTS_EXCLUS = {"tonnelet_poudre", "bocal_poudre_ii", "bocal_poudre_iii", "bocal_poudre_iv", "plaque_recyclee", "plaque_soudee", "plaque_metal", "plaque_renforcee"}  # jamais dans le loot
COMPOSANTS_PLUS = {"clou": 3.0, "sac_ciment": 3.0, "equerres_acier": 3.0, "bocal_poudre": 7.0, "ferraille": 9.0}  # chance multipliée dans leur rareté
# composants ajoutés en plus dans un palier, hors raretés du palier (nombre pour 100 conteneurs)
EN_PLUS = {7: {"clou": 2, "sac_ciment": 2}}
IGNORES = RETIRES  # pour la simulation (le mod ignore ce qui manque)
# capsules du casino (HeraliZ Casino, objets heralizcasino:jeton_*), choix du propriétaire (22/10) : plus aucun héral
# dans le casino, les capsules se trouvent sur la carte et s'échangent contre des objets à la caisse. Nombre de piles
# pour 100 conteneurs, puis, par palier, (objet, quantité [min, max], part des piles) ; plus de valeur en haut.
CAPSULE = "heralizcasino:"
CAPSULES_POUR_100 = {1: 8, 2: 10, 3: 12, 4: 14, 5: 16, 6: 18, 7: 25}
CAPSULES = {
    1: [("jeton_1", [1, 5], 1.0)],
    2: [("jeton_1", [2, 8], 1.0)],
    3: [("jeton_1", [3, 10], 0.7), ("jeton_10", [1, 2], 0.3)],
    4: [("jeton_1", [3, 10], 0.55), ("jeton_10", [1, 3], 0.45)],
    5: [("jeton_1", [5, 10], 0.4), ("jeton_10", [1, 3], 0.6)],
    6: [("jeton_10", [1, 5], 0.85), ("jeton_100", [1, 1], 0.15)],
    7: [("jeton_10", [3, 6], 0.6), ("jeton_100", [1, 2], 0.4)],
}
VALEUR_CAPSULE = {"jeton_1": 1, "jeton_10": 10, "jeton_100": 100}


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
    try:
        return json.load(open(f, encoding="utf-8-sig"))
    except Exception:
        return None


def index_tacz(genre):
    out = {}
    for f in glob.glob(str(TACZ / f"*/data/*/index/{genre}/*.json")):
        ns = f.split("/data/")[1].split("/")[0]
        d = lire_json(f)
        if d is not None:
            out[ns + ":" + Path(f).stem] = d
    return out


def armes_du_jeu():
    """id -> nbt de l'arme prête à tirer (mode de tir, chargeur plein), d'après les gunpacks."""
    donnees = {}
    for f in glob.glob(str(TACZ / "*/data/*/data/guns/*.json")):
        ns = f.split("/data/")[1].split("/")[0]
        d = lire_json(f)
        if d is not None:
            donnees[ns + ":" + Path(f).stem] = d
    armes = {}
    for gid, i in index_tacz("guns").items():
        if gid in ARMES_EXCLUES:
            continue
        d = donnees.get(i.get("data", ""), {})
        mode = (d.get("fire_mode") or ["semi"])[0].upper()
        armes[gid] = f'{{GunId:"{gid}",GunFireMode:"{mode}",GunCurrentAmmoCount:{d.get("ammo_amount", 1)},HasBulletInBarrel:1b}}'
    return armes


def familles():
    """famille -> {rareté: [(entrée sans poids, poids relatif)]}"""
    tout = json.load(open(RARETES, encoding="utf-8"))
    fam_armes = {g: v["famille"] for g, v in json.load(open(ARMES_JSON, encoding="utf-8"))["armes"].items()}
    jeu = armes_du_jeu()
    out = {k: {} for k in POUR_100}

    def ajouter(f, r, entree, rel=1.0):
        if r in ORDRE:
            out[f].setdefault(r, []).append((entree, rel))

    for gid, r in sorted(tout["armes"].items()):
        if gid in jeu:
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
        if i.startswith(ARMURE) and not i.startswith(ARMURE + "kit_") and not i.endswith("_suit") and r != "unique":
            ajouter("armures", r, {"item": i, "nombre": [1, 1], "_cat": "armures"})
    for i, r in sacs().items():
        if i not in SACS_HORS_LOOT:
            ajouter("sacs", r, {"item": "backpacked:" + i, "nombre": [1, 1], "_cat": "sacs"})
    for i, r, pile in re.findall(r'new Def\("([a-z_0-9]+)", "[^"]*", HeralizRarity\.([A-Z]+), (\d+)\)',
                                 COMPOSANTS_DEFS.read_text(encoding="utf-8")):
        if r in RARETE_COMPOSANT and i not in COMPOSANTS_EXCLUS:
            n = 1  # un seul exemplaire par case (seules les munitions sortent en quantité)
            ajouter("composants", RARETE_COMPOSANT[r], {"item": COMPOSANT + i, "nombre": [1, n], "_cat": "composants"},
                    COMPOSANTS_PLUS.get(i, 1.0))
    return out


def de_famille(e):
    return e.get("_cat") in POUR_100 or e.get("_cat") == "capsules" or e["item"].startswith(CAPSULE) or e["item"] in (ARME, ACCESSOIRE) or e["item"].startswith((ARMURE, COMPOSANT, PLAN))


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


MELEE = RACINE / "mods/heraliz-tactique/src/serveur/resources/data/heralisz_melee/index/melee"


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
        e = dict(e)
        e.setdefault("_base", e["poids"])
        out.append(e)
    return out


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
        if t in EMPLACEMENTS:
            data["emplacements"] = EMPLACEMENTS[t]
        cases = sum(data["emplacements"]) / 2
        p = {f: POUR_100[f][t] / 100 / cases for f in fams}
        base = [e["_base"] * mult_autre(e, t) for e in autres[t]]
        p["capsules"] = CAPSULES_POUR_100[t] / 100 / cases
        p["vide"] = max(0.0, 1 - sum(p.values()) - AUTRES_POUR_100[t] / 100 / cases)
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
                    rel.append((e, f, parts[r] / somme_parts * x / s, r))
        for i, quantite, part in CAPSULES[t]:
            rel.append(({"item": CAPSULE + i, "nombre": quantite, "_cat": "capsules"}, "capsules", part, None))
        for i, n in EN_PLUS.get(t, {}).items():
            e, r = next((e, r) for r, l in fams["composants"].items() for e, _ in l if e["item"] == COMPOSANT + i)
            p["plus_" + i] = n / 100 / cases
            rel.append((e, "plus_" + i, 1.0, r))
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
        data["objets"] = objets
        ecrire_table(COFFRES / f"t{t}.json", data)
        print(f"T{t} : " + ", ".join(f"{sum(len(l) for r, l in fams[f].items() if r in PARTS_FAMILLE.get((f, t), PARTS[t]))} {f}" for f in fams))


# conteneurs de soins (soins1_... à soins6_...) : une table par niveau, soins.json = niveau 1 (secours).
# Rareté du soin tirée d'abord, puis le soin dans sa rareté (poids d'origine « _base »). Le gris reste toujours le
# plus fréquent ; le bleu et le violet montent un peu avec le niveau (choix du propriétaire, 22/10).
SOINS = RACINE / "serveur/config/heraliz_loot/raretes.json"
PARTS_SOINS = {
    1: {"commun": 50, "peu_commun": 28, "rare": 16, "epique": 6},
    2: {"commun": 46, "peu_commun": 28, "rare": 18, "epique": 8},
    3: {"commun": 42, "peu_commun": 28, "rare": 20, "epique": 10},
    4: {"commun": 38, "peu_commun": 28, "rare": 21, "epique": 13},
    5: {"commun": 34, "peu_commun": 28, "rare": 22, "epique": 16},
    6: {"commun": 31, "peu_commun": 27, "rare": 23, "epique": 19},
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
        for _ in range(essais * 100):
            for _ in range(random.randint(*data["emplacements"])):
                e = random.choices(objs, w)[0]
                f, r = classer(e, ra)
                if f:
                    c[f][r] += 1
                elif e["item"] == "tacz:ammo":
                    balles += random.randint(*e["nombre"])
                elif e["item"].startswith(CAPSULE):
                    capsules += random.randint(*e["nombre"]) * VALEUR_CAPSULE.get(e["item"][len(CAPSULE):], 0)
        print(f"T{t} :", " | ".join(f"{sum(c[f].values()) / essais:.1f} {f} "
                                     + str({r: round(c[f][r] / essais, 2) for r in ORDRE if c[f][r]}) for f in c),
              f"| {balles / essais:.0f} balles | {capsules / essais:.0f} capsules (valeur)")


if __name__ == "__main__":
    simuler() if "--simuler" in sys.argv else ecrire()
