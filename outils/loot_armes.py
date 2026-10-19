"""Armes à feu et armures dans les conteneurs de HeraliZ Loot (T1 à T7).

Réécrit les armes TaCZ (tacz:modern_kinetic_gun) et les pièces de l'Armurerie des tables
mods/heraliz-loot/src/serveur/resources/data/heraliz_loot/coffres/t1.json à t7.json :
- ARMES_POUR_100 : nombre d'armes moyen pour 100 conteneurs ouverts (le même partout) ;
- PARTS : part de chaque rareté dans les armes du palier (rareté de
  serveur/config/heraliz_loot/raretes.json, section « armes ») ; toutes les armes de cette
  rareté y sont mises ; armes lourdes (précision, mitrailleuses, lance-grenades) LOURDES fois
  moins souvent que les autres de la même rareté. Jamais d'Épique ni de Légendaire en T1-T4 ;
- Unique (couteaux) jamais dans le loot ;
- ARMURES_POUR_100 : pièces de l'Armurerie (raretés de la section « objets »), mêmes PARTS ; jamais
  les tenues complètes (kit_*), les combinaisons intégrales (*_suit) ni les ensembles Unique ;
  les anciens vêtements heralisz_armor (mod retiré) sont retirés des tables ;
- COMPOSANTS_POUR_100 : composants de HeraliZ Composants (rareté = couleur du nom, lue dans
  ComponentDefs.java), mêmes PARTS ; jamais les Mythiques (rouge).
Les autres objets ne changent pas ; les entrées d'un mod absent (heralisz_armor) sont ignorées au
chargement par le mod, donc aussi ici. À relancer après un changement des raretés ou des gunpacks.
`--simuler` : ouvre 100 conteneurs par palier (moyenne de 1 000 essais) et compte armes et armures.
"""
import glob, json, random, re, sys
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
COFFRES = RACINE / "mods/heraliz-loot/src/serveur/resources/data/heraliz_loot/coffres"
RARETES = RACINE / "serveur/config/heraliz_loot/raretes.json"
TACZ = RACINE / "serveur/tacz"

ARMES_POUR_100 = {t: 15 for t in range(1, 8)}  # le même à chaque palier ; le hasard donne de 7 à 25 environ
PARTS = {
    1: {"commun": 1.0},
    2: {"commun": 0.6, "peu_commun": 0.4},
    3: {"commun": 0.3, "peu_commun": 0.5, "rare": 0.2},
    4: {"peu_commun": 0.5, "rare": 0.5},
    5: {"rare": 0.85, "epique": 0.13, "legendaire": 0.02},   # Épique et Légendaire très rares
    6: {"rare": 0.65, "epique": 0.28, "legendaire": 0.07},   # rares, mais moins qu'en T5
    7: {"rare": 0.55, "epique": 0.33, "legendaire": 0.12},
}
ARMURES_POUR_100 = {t: 20 for t in range(1, 8)}
ARMURE = "heraliz_armory:"
ARMURES_EXCLUES = ("heraliz_armory:kit_",)
ARMURES_EXCLUES_FIN = ("_suit",)
COMPOSANTS_POUR_100 = {t: 40 for t in range(1, 8)}
COMPOSANT = "heraliz_composants:"
COMPOSANTS_DEFS = RACINE / "mods/heraliz-composants/src/commun/java/fr/heraliz/composants/ComponentDefs.java"
RARETE_COMPOSANT = {"COMMON": "commun", "UNCOMMON": "peu_commun", "RARE": "rare", "EPIC": "epique",
                    "LEGENDARY": "legendaire"}  # MYTHIC : jamais dans le loot
# Armes lourdes (précision, mitrailleuses, lance-grenades) : chance multipliée par LOURDES dans leur rareté.
LOURDES = 0.35
ARMES_JSON = RACINE / "serveur/config/heraliz_tactique/armes.json"


def lourde(gid, familles):
    f = familles.get(gid)
    return f is None or f.startswith(("precision", "mitrailleuse", "minigun"))  # sans famille : lance-grenades


ORDRE = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]
IGNORES = ("heralisz_armor:",)
RETIRES = ("heralisz_armor:",)  # retirés des tables à l'écriture
ARME = "tacz:modern_kinetic_gun"
PRECISION = 20  # plus petit poids d'arme voulu : sinon les autres poids sont multipliés par 10


def gun_id(e):
    m = re.search(r'GunId:"([^"]+)"', e.get("nbt", ""))
    return m.group(1) if m else None


def raretes():
    return json.load(open(RARETES, encoding="utf-8"))["armes"]


def armes_du_jeu():
    """id -> nbt de l'arme prête à tirer (mode de tir, chargeur plein), d'après les gunpacks."""
    donnees = {}
    for f in glob.glob(str(TACZ / "*/data/*/data/guns/*.json")):
        ns = f.split("/data/")[1].split("/")[0]
        try:
            donnees[ns + ":" + Path(f).stem] = json.load(open(f, encoding="utf-8-sig"))
        except Exception:
            pass
    armes = {}
    for f in glob.glob(str(TACZ / "*/data/*/index/guns/*.json")):
        ns = f.split("/data/")[1].split("/")[0]
        gid = ns + ":" + Path(f).stem
        try:
            d = donnees.get(json.load(open(f, encoding="utf-8-sig")).get("data", ""), {})
        except Exception:
            continue
        mode = (d.get("fire_mode") or ["semi"])[0].upper()
        armes[gid] = f'{{GunId:"{gid}",GunFireMode:"{mode}",GunCurrentAmmoCount:{d.get("ammo_amount", 1)},HasBulletInBarrel:1b}}'
    return armes


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


def poids_famille(par_rarete, parts, rel_de):
    """Poids relatifs (somme 1) : chaque rareté garde sa part de PARTS, partagée selon rel_de."""
    rel = {g: rel_de(g) for r in parts for g in par_rarete.get(r, [])}
    somme = {r: sum(rel[g] for g in par_rarete.get(r, [])) for r in parts}
    return {g: parts[r] * rel[g] / somme[r] for r in parts for g in par_rarete.get(r, []) if somme[r]}


def armures_du_jeu():
    objets = json.load(open(RARETES, encoding="utf-8"))["objets"]
    par_rarete = {}
    for i, r in sorted(objets.items()):
        if i.startswith(ARMURE) and not i.startswith(ARMURES_EXCLUES) and not i.endswith(ARMURES_EXCLUES_FIN) \
                and r in ORDRE[:5]:
            par_rarete.setdefault(r, []).append(i)
    return par_rarete


def composants_du_jeu():
    """rareté -> [(id, quantité max)], d'après ComponentDefs.java."""
    par_rarete = {}
    for i, r, pile in re.findall(r'new Def\("([a-z_0-9]+)", "[^"]*", HeralizRarity\.([A-Z]+), (\d+)\)',
                                 COMPOSANTS_DEFS.read_text(encoding="utf-8")):
        if r in RARETE_COMPOSANT:
            par_rarete.setdefault(RARETE_COMPOSANT[r], []).append(COMPOSANT + i)
            QUANTITE[COMPOSANT + i] = 3 if r == "COMMON" and int(pile) >= 16 else 1
    return par_rarete


QUANTITE = {}


def ecrire():
    rar, jeu = raretes(), armes_du_jeu()
    familles = {g: v["famille"] for g, v in json.load(open(ARMES_JSON, encoding="utf-8"))["armes"].items()}
    par_rarete = {}
    for gid, r in sorted(rar.items()):
        if gid in jeu:
            par_rarete.setdefault(r, []).append(gid)
    armures = armures_du_jeu()
    rar_armures = {i: r for r, l in armures.items() for i in l}
    composants = composants_du_jeu()
    rar_comp = {i: r for r, l in composants.items() for i in l}
    for t, parts in PARTS.items():
        f = COFFRES / f"t{t}.json"
        data = json.loads(f.read_text(encoding="utf-8"))
        autres = [e for e in data["objets"] if e["item"] != ARME and not e["item"].startswith((ARMURE, COMPOSANT))
                  and not e["item"].startswith(RETIRES)]
        actifs = sum(e["poids"] for e in autres)
        cases = sum(data["emplacements"]) / 2
        p_armes = ARMES_POUR_100[t] / 100 / cases
        p_armures = ARMURES_POUR_100[t] / 100 / cases
        rel_armes = poids_famille(par_rarete, parts, lambda g: LOURDES if lourde(g, familles) else 1)
        rel_armures = poids_famille(armures, parts, lambda g: 1)
        p_comp = COMPOSANTS_POUR_100[t] / 100 / cases
        rel_comp = poids_famille(composants, parts, lambda g: 1)
        echelle = 1
        while True:
            reste = actifs * echelle / (1 - p_armes - p_armures - p_comp)
            poids = {g: reste * p_armes * v for g, v in rel_armes.items()}
            poids_a = {i: reste * p_armures * v for i, v in rel_armures.items()}
            poids_c = {i: reste * p_comp * v for i, v in rel_comp.items()}
            if min(list(poids.values()) + list(poids_a.values()) + list(poids_c.values())) >= PRECISION:
                break
            echelle *= 10
        for e in autres:
            e["poids"] *= echelle
        armes = [{"item": ARME, "nombre": [1, 1], "poids": round(poids[g]), "nbt": jeu[g], "rarete": rar[g],
                  "_cat": "armes"} for g in poids]
        tenues = [{"item": i, "nombre": [1, 1], "poids": round(p), "rarete": rar_armures[i], "_cat": "armures"}
                  for i, p in poids_a.items()]
        comps = [{"item": i, "nombre": [1, QUANTITE[i]], "poids": round(p), "rarete": rar_comp[i], "_cat": "composants"}
                 for i, p in poids_c.items()]
        if t != 7:  # la rareté inscrite ne sert qu'aux coffres T7 (animations) : HeraliZ Loot la lit sinon dans raretes.json
            for a in armes + tenues + comps:
                del a["rarete"]
        data["objets"] = autres + armes + tenues + comps
        ecrire_table(f, data)
        print(f"T{t} : {len(armes)} armes, {len(tenues)} pièces d'armure, {len(comps)} composants ({', '.join(parts)})"
              )


def simuler(essais=1000):
    rar = raretes()
    ra = {i: r for r, l in armures_du_jeu().items() for i in l}
    rc = {i: r for r, l in composants_du_jeu().items() for i in l}
    for t in PARTS:
        data = json.loads((COFFRES / f"t{t}.json").read_text(encoding="utf-8"))
        objs = [e for e in data["objets"] if not e["item"].startswith(IGNORES)]
        w = [e["poids"] for e in objs]
        c, a, k = Counter(), Counter(), Counter()
        for _ in range(essais * 100):
            for _ in range(random.randint(*data["emplacements"])):
                e = random.choices(objs, w)[0]
                if e["item"] == ARME:
                    c[rar.get(gun_id(e), "?")] += 1
                elif e["item"].startswith(ARMURE):
                    a[ra.get(e["item"], "?")] += 1
                elif e["item"].startswith(COMPOSANT):
                    k[rc.get(e["item"], "?")] += 1
        print(f"T{t} : {sum(c.values()) / essais:.1f} armes", {r: round(c[r] / essais, 1) for r in ORDRE if c[r]},
              f"| {sum(a.values()) / essais:.1f} armures", {r: round(a[r] / essais, 1) for r in ORDRE if a[r]},
              f"| {sum(k.values()) / essais:.1f} composants", {r: round(k[r] / essais, 1) for r in ORDRE if k[r]})


if __name__ == "__main__":
    simuler() if "--simuler" in sys.argv else ecrire()
