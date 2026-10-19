"""Écrit ce que vendent et rachètent les marchands de HeraliZ Quêtes (clé « marchand » de leur fichier dans
serveur/config/heraliz_quetes/pnj/). On modifie les tableaux ci-dessous, puis :

    python outils/marchands.py

Le reste du fichier du PNJ (nom, apparence, phrases) n'est pas touché. Prix en hérals, à l'unité.
- soins : tous ceux du jeu, prix selon leur rareté (serveur/config/heraliz_loot/raretes.json, section « soins ») ;
- nourriture : quelques aliments du mod de nourriture (geckolib222), toujours en bon état (FoodState 0), prix selon ce
  qu'ils nourrissent ;
- accessoires TaCZ : ceux de rareté Commun à Rare (section « accessoires »), sans les munitions spéciales ;
- matériaux de construction (Bâtisseur) : tableau MATERIAUX ;
- rachat : objets de valeur (tag heraliz_composants:vente), puis tous les composants (heraliz_composants:tous).
"""
import json
from pathlib import Path

RACINE = Path(__file__).resolve().parents[1]
PNJ = RACINE / "serveur" / "config" / "heraliz_quetes" / "pnj"
RARETES = json.loads((RACINE / "serveur" / "config" / "heraliz_loot" / "raretes.json").read_text(encoding="utf-8"))

PRIX_SOINS = {"commun": 30, "peu_commun": 80, "rare": 180, "epique": 400}
NOURRITURE = [  # (objet, points de faim, prix)
    ("geckolib:food_mre", 8, 110),
    ("geckolib:food_spaggety", 8, 100),
    ("geckolib:food_soup", 7, 85),
    ("geckolib:food_tuna", 6, 70),
    ("geckolib:food_sardines", 6, 65),
    ("geckolib:food_deez", 4, 40),
]
PRIX_ACCESSOIRES = {"commun": 60, "peu_commun": 150, "rare": 350}
MATERIAUX = [  # matériaux de construction du Bâtisseur (heraliz_composants), prix à l'unité
    ("heraliz_composants:brindilles", 2),
    ("heraliz_composants:planches", 10),
    ("heraliz_composants:pierres_taillees", 20),
    ("heraliz_composants:fragments_metal", 40),
    ("heraliz_composants:metal_renforce", 120),
]
RACHAT_VALEUR = {"commun": 20, "peu_commun": 50, "rare": 120, "epique": 300, "legendaire": 700, "mythique": 1500}
RACHAT_COMPOSANTS = {k: v // 2 for k, v in RACHAT_VALEUR.items()}
ORDRE = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]


def soins():
    l = sorted(RARETES["soins"].items(), key=lambda kv: (ORDRE.index(kv[1]), kv[0]))
    return [{"objet": "heralisz_tactical:consumable", "nbt": '{ConsumableId:"%s"}' % k, "prix": PRIX_SOINS[r]}
            for k, r in l if r in PRIX_SOINS]


def nourriture():
    return [{"objet": o, "nbt": "{FoodState:0}", "prix": p} for o, _, p in NOURRITURE]


def accessoires():
    l = [(k, r) for k, r in RARETES["accessoires"].items() if r in PRIX_ACCESSOIRES and ":ammo_mod" not in k]
    l.sort(key=lambda kv: (ORDRE.index(kv[1]), kv[0]))
    return [{"objet": "tacz:attachment", "nbt": '{AttachmentId:"%s"}' % k, "prix": PRIX_ACCESSOIRES[r]} for k, r in l]


MARCHANDS = {
    "mila": {"type": "rachat", "rachat": [{"tag": "heraliz_composants:vente", "prix": RACHAT_VALEUR},
                                          {"tag": "heraliz_composants:tous", "prix": RACHAT_COMPOSANTS}]},
    "jacqueline": {"type": "achat", "articles": soins()},
    "doyen": {"type": "achat", "articles": nourriture()},
    "jack": {"type": "achat", "articles": accessoires()},
    "batisseur": {"type": "achat", "articles": [{"objet": o, "prix": p} for o, p in MATERIAUX]},
}

if __name__ == "__main__":
    for pid, m in MARCHANDS.items():
        f = PNJ / f"{pid}.json"
        d = json.loads(f.read_text(encoding="utf-8"))
        d["marchand"] = m
        f.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{pid} : {len(m.get('articles', m.get('rachat', [])))} {'articles' if 'articles' in m else 'règles'}")
