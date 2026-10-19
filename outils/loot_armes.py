"""Armes à feu dans les conteneurs de HeraliZ Loot (T1 à T7).

Réécrit les armes TaCZ (tacz:modern_kinetic_gun) des tables
mods/heraliz-loot/src/serveur/resources/data/heraliz_loot/coffres/t1.json à t7.json :
- ARMES_POUR_100 : nombre d'armes moyen pour 100 conteneurs ouverts, par palier ;
- PARTS : part de chaque rareté dans les armes du palier (rareté de
  serveur/config/heraliz_loot/raretes.json, section « armes ») ; toutes les armes de cette
  rareté y sont mises, à chances égales entre elles. Jamais d'Épique ni de Légendaire en T1-T3 ;
- Unique (couteaux) jamais dans le loot.
Les autres objets ne changent pas ; les entrées d'un mod absent (heralisz_armor) sont ignorées au
chargement par le mod, donc aussi ici. À relancer après un changement des raretés ou des gunpacks.
`--simuler` : ouvre 100 conteneurs par palier (moyenne de 1 000 essais) et compte les armes.
"""
import glob, json, random, re, sys
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
COFFRES = RACINE / "mods/heraliz-loot/src/serveur/resources/data/heraliz_loot/coffres"
RARETES = RACINE / "serveur/config/heraliz_loot/raretes.json"
TACZ = RACINE / "serveur/tacz"

ARMES_POUR_100 = {1: 10, 2: 12, 3: 14, 4: 16, 5: 18, 6: 20, 7: 20}
PARTS = {
    1: {"commun": 1.0},
    2: {"commun": 0.6, "peu_commun": 0.4},
    3: {"commun": 0.2, "peu_commun": 0.5, "rare": 0.3},
    4: {"peu_commun": 0.25, "rare": 0.55, "epique": 0.2},
    5: {"rare": 0.35, "epique": 0.5, "legendaire": 0.15},
    6: {"rare": 0.1, "epique": 0.6, "legendaire": 0.3},
    7: {"epique": 0.5, "legendaire": 0.5},
}
ORDRE = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]
IGNORES = ("heralisz_armor:",)
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


def ecrire():
    rar, jeu = raretes(), armes_du_jeu()
    par_rarete = {}
    for gid, r in sorted(rar.items()):
        if gid in jeu:
            par_rarete.setdefault(r, []).append(gid)
    for t, parts in PARTS.items():
        f = COFFRES / f"t{t}.json"
        data = json.loads(f.read_text(encoding="utf-8"))
        autres = [e for e in data["objets"] if e["item"] != ARME]
        actifs = sum(e["poids"] for e in autres if not e["item"].startswith(IGNORES))
        cases = sum(data["emplacements"]) / 2
        part = ARMES_POUR_100[t] / 100 / cases
        echelle = 1
        while True:
            total = actifs * echelle * part / (1 - part)
            poids = {g: total * p / len(par_rarete[r]) for r, p in parts.items() for g in par_rarete.get(r, [])}
            if min(poids.values()) >= PRECISION:
                break
            echelle *= 10
        for e in autres:
            e["poids"] *= echelle
        armes = [{"item": ARME, "nombre": [1, 1], "poids": round(poids[g]), "nbt": jeu[g], "rarete": rar[g],
                  "_cat": "armes"} for g in poids]
        if t != 7:  # la rareté inscrite ne sert qu'aux coffres T7 (animations) : HeraliZ Loot la lit sinon dans raretes.json
            for a in armes:
                del a["rarete"]
        data["objets"] = autres + armes
        ecrire_table(f, data)
        print(f"T{t} : {len(armes)} armes ({', '.join(parts)}), {ARMES_POUR_100[t]} pour 100 conteneurs")


def simuler(essais=1000):
    rar = raretes()
    for t in PARTS:
        data = json.loads((COFFRES / f"t{t}.json").read_text(encoding="utf-8"))
        objs = [e for e in data["objets"] if not e["item"].startswith(IGNORES)]
        w = [e["poids"] for e in objs]
        c = Counter()
        for _ in range(essais * 100):
            for _ in range(random.randint(*data["emplacements"])):
                e = random.choices(objs, w)[0]
                if e["item"] == ARME:
                    c[rar.get(gun_id(e), "?")] += 1
        print(f"T{t} : {sum(c.values()) / essais:.1f} armes", {r: round(c[r] / essais, 1) for r in ORDRE if c[r]})


if __name__ == "__main__":
    simuler() if "--simuler" in sys.argv else ecrire()
