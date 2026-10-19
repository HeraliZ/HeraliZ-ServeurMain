"""Armes à feu dans les conteneurs de HeraliZ Loot (T1 à T6).

Réécrit seulement le « poids » des armes TaCZ (tacz:modern_kinetic_gun) dans
mods/heraliz-loot/src/serveur/resources/data/heraliz_loot/coffres/t1.json à t6.json :
- ARMES_POUR_100 : nombre d'armes moyen pour 100 conteneurs ouverts, par palier ;
- dans un palier, une arme sort FACTEUR fois moins qu'une arme de la rareté juste en dessous
  (rareté de serveur/config/heraliz_loot/raretes.json, section « armes »).
Les autres objets gardent leur poids ; les entrées d'un mod absent (heralisz_armor) sont
ignorées au chargement par le mod, donc aussi ici. À relancer après un changement des tables
ou des raretés des armes. `--simuler` : ouvre 100 conteneurs par palier et compte les armes.
"""
import json, random, re, sys
from collections import Counter
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
COFFRES = RACINE / "mods/heraliz-loot/src/serveur/resources/data/heraliz_loot/coffres"
RARETES = RACINE / "serveur/config/heraliz_loot/raretes.json"

ARMES_POUR_100 = {1: 5, 2: 10, 3: 15, 4: 20, 5: 25, 6: 30}
FACTEUR = 2
PRECISION = 20  # plus petit poids d'arme voulu : sinon tous les autres poids sont multipliés par 10
ORDRE = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]
IGNORES = ("heralisz_armor:",)
ARME = "tacz:modern_kinetic_gun"


def gun_id(e):
    m = re.search(r'GunId:"([^"]+)"', e.get("nbt", ""))
    return m.group(1) if m else None


def raretes():
    d = json.load(open(RARETES, encoding="utf-8"))["armes"]
    return {k: (v if isinstance(v, str) else v.get("rarete")) for k, v in d.items()}


def charger(t):
    f = COFFRES / f"t{t}.json"
    return f, f.read_text(encoding="utf-8")


def calculer(t, data, rar, echelle=1):
    objs = data["objets"]
    armes = [e for e in objs if e["item"] == ARME]
    autres = [e for e in objs if e["item"] != ARME and not e["item"].startswith(IGNORES)]
    cases = sum(data["emplacements"]) / 2
    part = ARMES_POUR_100[t] / 100 / cases           # part des tirages qui sont des armes
    total_autres = sum(e["poids"] for e in autres) * echelle
    total_armes = total_autres * part / (1 - part)
    rang = {id(e): ORDRE.index(rar.get(gun_id(e), "rare")) for e in armes}
    bas = min(rang.values())
    rel = {k: FACTEUR ** -(r - bas) for k, r in rang.items()}
    somme = sum(rel.values())
    poids = {k: round(total_armes * v / somme) for k, v in rel.items()}
    return armes, poids


def ecrire():
    rar = raretes()
    for t in ARMES_POUR_100:
        f, texte = charger(t)
        data = json.loads(texte)
        echelle = 1
        armes, poids = calculer(t, data, rar)
        while min(poids.values()) < PRECISION:
            echelle *= 10
            armes, poids = calculer(t, data, rar, echelle)
        nouv, i = [], 0
        lignes = texte.split("\n")
        objs = iter(data["objets"])
        for l in lignes:
            if l.strip().startswith('{"item"'):
                e = next(objs)
                p = poids[id(e)] if e["item"] == ARME else e["poids"] * echelle
                l = re.sub(r'"poids": \d+', f'"poids": {p}', l, count=1)
            nouv.append(l)
        f.write_text("\n".join(nouv), encoding="utf-8")
        print(f"T{t} : {len(armes)} armes, {ARMES_POUR_100[t]} pour 100 conteneurs")


def simuler(n=100, graine=1):
    rar = raretes()
    random.seed(graine)
    for t in range(1, 7):
        data = json.loads(charger(t)[1])
        objs = [e for e in data["objets"] if not e["item"].startswith(IGNORES)]
        w = [e["poids"] for e in objs]
        c = Counter()
        for _ in range(n):
            for _ in range(random.randint(*data["emplacements"])):
                e = random.choices(objs, w)[0]
                if e["item"] == ARME:
                    c[rar.get(gun_id(e), "?")] += 1
        print(f"T{t} : {sum(c.values())} armes", {r: c[r] for r in ORDRE if c[r]})


if __name__ == "__main__":
    simuler() if "--simuler" in sys.argv else ecrire()
