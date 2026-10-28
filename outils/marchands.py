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

Contrôle (règle des propriétaires du 25/10, pour toujours) : un objet qu'un marchand vend au prix P n'est jamais racheté
plus de 50 % de P (Marchands.prixRachat de HeraliZ Quêtes : min(prix de la règle, P / 2), plus petit P chez tous les
vendeurs, aussi pour les articles ajoutés en jeu au Registre), et le recycler ne rapporte pas plus : espérance des
composants revendus (ou hérals « rien ») au plus P / 2 (Recyclage.tropRentable, même calcul).
Le script refuse d'écrire si une revente ou un recyclage dépasse ces bornes sans que le serveur les plafonne.

    python outils/marchands.py             écrit les fichiers (après le contrôle)
    python outils/marchands.py --controle  contrôle seulement les fichiers actuels (code 1 en cas d'erreur)
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
    ("crac:claim_block", 500),
    ("crac:metal_crate", 300),
    ("crac:coeur_clan", 1000),
]
RACHAT_VALEUR = {"commun": 2, "peu_commun": 8, "rare": 20, "epique": 50, "legendaire": 200, "mythique": 500}
RACHAT_COMPOSANTS = RACHAT_VALEUR  # mêmes prix que les objets de valeur
# 24/10 (revue de la boucle de jeu) : Mila rachète aussi armes, armures, sacs, grenades et accessoires trouvés sur la
# carte, à petits prix : toujours moins que les composants qu'il faut pour les fabriquer à la Table d'armurier (pas
# d'argent en boucle), et jamais un objet qu'un marchand vend (exclu par le serveur).
RACHAT_ARMES = {"commun": 15, "peu_commun": 30, "rare": 60, "epique": 120, "legendaire": 250, "mythique": 500}
RACHAT_ARMURES = {"commun": 5, "peu_commun": 12, "rare": 25, "epique": 60, "legendaire": 120, "mythique": 250}
RACHAT_SACS = {"commun": 8, "peu_commun": 15, "rare": 30, "epique": 60, "legendaire": 120, "mythique": 250}
RACHAT_GRENADES = {"commun": 5, "peu_commun": 10, "rare": 20, "epique": 40, "legendaire": 80, "mythique": 160}
RACHAT_ACCESSOIRES = {"commun": 5, "peu_commun": 10, "rare": 20, "epique": 50, "legendaire": 100, "mythique": 200}
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
                                          {"tag": "heraliz_composants:tous", "prix": RACHAT_COMPOSANTS},
                                          {"objet": "tacz:modern_kinetic_gun", "prix": RACHAT_ARMES},
                                          {"tag": "heraliz_quetes:rachat_armures", "prix": RACHAT_ARMURES},
                                          {"tag": "heraliz_quetes:rachat_sacs", "prix": RACHAT_SACS},
                                          {"objet": "heralisz_tactical:throwable", "prix": RACHAT_GRENADES},
                                          {"objet": "tacz:attachment", "prix": RACHAT_ACCESSOIRES}]},
    "jacqueline": {"type": "achat", "articles": soins()},
    "doyen": {"type": "achat", "articles": nourriture()},
    "jack": {"type": "achat", "articles": accessoires()},
    "batisseur": {"type": "achat", "articles": [{"objet": o, "prix": p} for o, p in MATERIAUX]},
}

TAGS = RACINE / "modules" / "equipement" / "src" / "serveur" / "resources" / "data" / "heraliz_composants" / "tags" / "items"
MARCHANDS_JAVA = (RACINE / "modules" / "pnj" / "src" / "serveur" / "java" / "fr" / "heraliz" / "quetes" / "serveur"
                  / "Marchands.java")


TAGS_PNJ = RACINE / "modules" / "pnj" / "src" / "serveur" / "resources" / "data" / "heraliz_quetes" / "tags" / "items"
COFFRES = RACINE / "modules" / "loot" / "src" / "serveur" / "resources" / "data" / "heraliz_loot" / "coffres"


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
        (TAGS_PNJ / (nom + ".json")).write_text(json.dumps({"replace": False, "values": valeurs}, indent=2) + "\n",
                                                encoding="utf-8")
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


def prix_regle(marchand, objet, nbt):
    """Prix de la première règle de rachat qui prend l'objet (0 : aucune), comme Marchands.prixRachat(m, s)."""
    for r in marchand.get("rachat", []):
        if not (objet == r["objet"] if "objet" in r else objet in ids_tag(r["tag"])):
            continue
        g = rang(objet, nbt)
        nom = "commun" if g < 0 else "mythique" if g == 5 else ORDRE_RANGS[g]
        v = r["prix"].get(nom)
        if v is None and g == 5:
            v = r["prix"].get("unique")
        return max(0, v or 0)
    return 0


def controle(marchands):
    """Revente et recyclage de chaque article vendu : refus (SystemExit) si l'un rapporte plus que la règle des 50 %."""
    plafond = "achat / 2" in MARCHANDS_JAVA.read_text(encoding="utf-8")
    recy_plafond = "tropRentable" in RECYCLAGE_JAVA.read_text(encoding="utf-8")
    ventes = {}
    for m in marchands.values():
        if m.get("type") == "rachat":
            continue
        for a in m.get("articles", []):
            k = (a["objet"], a.get("nbt", ""))
            ventes[k] = min(a["prix"], ventes.get(k, a["prix"]))
    racheteurs = {pid: m for pid, m in marchands.items() if m.get("type") == "rachat"}

    def rachat(objet, nbt):
        """Meilleur prix de rachat, plafond des 50 % compris (objet vendu sans données : il vaut pour toutes)."""
        brut = max([prix_regle(m, objet, nbt) for m in racheteurs.values()] + [0])
        achat = min([p for (o, n), p in ventes.items() if o == objet and (not n or n == nbt)], default=None)
        return brut if achat is None or not plafond else min(brut, achat // 2)

    erreurs, notes = [], []
    for (objet, nbt), prix in sorted(ventes.items()):
        for pid, m in racheteurs.items():
            brut = prix_regle(m, objet, nbt)
            if brut * 2 <= prix:
                continue
            if plafond:
                notes.append(f"{objet}{nbt} : vendu {prix}, règle de {pid} {brut} -> racheté {min(brut, prix // 2)} (plafond des 50 %)")
            else:
                erreurs.append(f"{objet}{nbt} : vendu {prix}, {pid} le rachèterait {brut} (plus de 50 %)")
    if RECYCLAGE.exists():
        rec = json.loads(RECYCLAGE.read_text(encoding="utf-8"))
        for (objet, nbt), prix in sorted(ventes.items()):
            r = regle_recyclage(rec, objet, nbt)
            if r is None:
                continue
            g = max(0, rang(objet, nbt))
            rend = r.get("rend") or r["rend_rarete"][min(len(r["rend_rarete"]) - 1, g)]
            somme = sum(rachat(c, "") for c in rend)
            rien = rec["herals_si_rien"][min(5, g)]
            esperance = rec["chance"] * somme + (1 - rec["chance"]) ** len(rend) * rien
            cout = prix * r.get("par", 1)
            if esperance * 2 > cout:
                texte = f"{objet}{nbt} : vendu {prix}, recyclé rapporte {esperance:.1f} en moyenne (borne : {cout / 2:.0f})"
                (notes if recy_plafond else erreurs).append(texte + (" -> refusé par le recycleur" if recy_plafond else ""))
    for n in notes:
        print("plafonné par le serveur : " + n)
    for e in erreurs:
        print("ERREUR : " + e)
    if erreurs:
        raise SystemExit("Refusé : une revente ou un recyclage rapporterait plus que la règle des 50 % du prix d'achat.")
    print(f"[marchands] contrôle des 50 % : {len(ventes)} articles vendus, aucune revente ni recyclage trop rentable")


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
        actuels = {}
        for f in sorted(PNJ.glob("*.json")):
            m = json.loads(f.read_text(encoding="utf-8")).get("marchand")
            if m and m.get("type") in ("achat", "rachat"):
                actuels[f.stem] = m
        controle(actuels)
        raise SystemExit(0)
    tags_rachat()
    controle(MARCHANDS)
    for pid, m in MARCHANDS.items():
        f = PNJ / f"{pid}.json"
        d = json.loads(f.read_text(encoding="utf-8"))
        d["marchand"] = m
        f.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(f"{pid} : {len(m.get('articles', m.get('rachat', [])))} {'articles' if 'articles' in m else 'règles'}")
