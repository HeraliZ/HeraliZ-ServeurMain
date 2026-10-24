"""Écrit ce que vendent et rachètent les marchands de HeraliZ Quêtes (clé « marchand » de leur fichier dans
serveur/config/heraliz_quetes/pnj/). On modifie les tableaux ci-dessous, puis :

    python outils/marchands.py

Le reste du fichier du PNJ (nom, apparence, phrases) n'est pas touché. Prix en hérals, à l'unité.
- soins : tous ceux du jeu, prix selon leur rareté (serveur/config/heraliz_loot/raretes.json, section « soins ») ;
- nourriture : quelques aliments du mod de nourriture (geckolib222), toujours en bon état (FoodState 0), prix selon ce
  qu'ils nourrissent ;
- accessoires TaCZ : ceux de rareté Commun à Rare (section « accessoires »), sans les munitions spéciales ;
- matériaux de construction (Bâtisseur) : tableau MATERIAUX ;
- rachat : pièces de l'hélicoptère de patrouille (Airstrike, prix fixe par pièce, tableau PIECES_HELICO), objets de
  valeur (tag heraliz_composants:vente), puis tous les composants (heraliz_composants:tous).

Contrôle (23/10, brindilles achetées 2 hérals au Bâtisseur et revendues 8 à Mila, sans limite) : un objet vendu par un
marchand n'est JAMAIS racheté par le serveur (Marchands.prixRachat de HeraliZ Quêtes 1.10.3). Le script liste les objets
vendus qu'une règle de rachat paierait autant ou plus que leur prix de vente, et refuse d'écrire si cette exclusion
manque dans le code du serveur.
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
    ("heraliz_composants:planches", 50),
    ("heraliz_composants:pierres_taillees", 100),
    ("heraliz_composants:fragments_metal", 250),
    ("heraliz_composants:metal_renforce", 400),
    ("crac:building_plan", 50),     # plan et marteau de CRac, au cas où (20/10)
    ("crac:building_hammer", 50),
]
RACHAT_VALEUR = {"commun": 2, "peu_commun": 8, "rare": 20, "epique": 50, "legendaire": 200, "mythique": 500}
RACHAT_COMPOSANTS = RACHAT_VALEUR  # mêmes prix que les objets de valeur
# pièces récupérées sur l'épave de l'hélicoptère de patrouille (Airstrike 1.10.0, 24/10) : prix fixe, quelle que soit la
# rareté ; mêmes pièces et poids que fr.heraliz.airstrike.helico.PieceHelico (les moins chères sortent le plus souvent)
PIECES_HELICO = [
    ("airstrike:helico_tole", 35),
    ("airstrike:helico_cables", 55),
    ("airstrike:helico_pale", 90),
    ("airstrike:helico_reservoir", 120),
    ("airstrike:helico_mitrailleuse", 170),
    ("airstrike:helico_avionique", 210),
    ("airstrike:helico_turbine", 250),
]


def prix_fixe(p):
    """Prix d'une règle de rachat d'un seul objet (clé « objet ») : le même pour toutes les raretés."""
    return {r: p for r in ("commun", "peu_commun", "rare", "epique", "legendaire", "mythique")}
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
    "mila": {"type": "rachat", "rachat": [{"objet": o, "prix": prix_fixe(p)} for o, p in PIECES_HELICO] + [
                                          {"tag": "heraliz_composants:vente", "prix": RACHAT_VALEUR},
                                          {"tag": "heraliz_composants:tous", "prix": RACHAT_COMPOSANTS}]},
    "jacqueline": {"type": "achat", "articles": soins()},
    "doyen": {"type": "achat", "articles": nourriture()},
    "jack": {"type": "achat", "articles": accessoires()},
    "batisseur": {"type": "achat", "articles": [{"objet": o, "prix": p} for o, p in MATERIAUX]},
}

TAGS = RACINE / "mods" / "heraliz-composants" / "src" / "serveur" / "resources" / "data" / "heraliz_composants" / "tags" / "items"
MARCHANDS_JAVA = (RACINE / "mods" / "heraliz-quetes" / "src" / "serveur" / "java" / "fr" / "heraliz" / "quetes" / "serveur"
                  / "Marchands.java")


def tag(nom):
    """Identifiants d'un tag de heraliz_composants (vide s'il n'est pas de ce mod)."""
    if not nom.startswith("heraliz_composants:"):
        return set()
    f = TAGS / (nom.split(":", 1)[1] + ".json")
    return set(json.loads(f.read_text(encoding="utf-8")).get("values", [])) if f.exists() else set()


def controle():
    """Objets vendus qu'un racheteur paierait autant ou plus que leur prix : refus si le serveur ne les exclut pas."""
    ventes = {}
    for m in MARCHANDS.values():
        for a in m.get("articles", []):
            ventes[a["objet"]] = min(a["prix"], ventes.get(a["objet"], a["prix"]))
    conflits = []
    for pid, m in MARCHANDS.items():
        for r in m.get("rachat", []):
            ids = tag(r["tag"]) if "tag" in r else {r["objet"]}
            for o, prix in ventes.items():
                if o not in ids:
                    continue
                rar = RARETES["objets"].get(o, "commun")
                v = r["prix"].get("mythique" if rar == "unique" else rar, 0)
                if v >= prix:
                    conflits.append(f"{o} : vendu {prix}, {pid} le paierait {v}")
    if not conflits:
        return
    exclu = "SaisonQuetes.vendu(vendus, s)" in MARCHANDS_JAVA.read_text(encoding="utf-8")
    for c in conflits:
        print(("exclu du rachat par le serveur : " if exclu else "ERREUR rachat >= vente : ") + c)
    if not exclu:
        raise SystemExit("Refusé : le serveur rachèterait des objets vendus (Marchands.prixRachat sans exclusion).")


if __name__ == "__main__":
    controle()
    for pid, m in MARCHANDS.items():
        f = PNJ / f"{pid}.json"
        d = json.loads(f.read_text(encoding="utf-8"))
        d["marchand"] = m
        f.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{pid} : {len(m.get('articles', m.get('rachat', [])))} {'articles' if 'articles' in m else 'règles'}")
