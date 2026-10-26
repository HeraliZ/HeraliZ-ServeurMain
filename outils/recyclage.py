"""Écrit les règles du recycleur : serveur/config/heraliz_quetes/recyclage.json (jamais à la main).

Choix du propriétaire (24/10) : presque tout objet trouvé sur la carte se recycle au PNJ « recycleur » du spawn, contre
quelques composants (à revendre à Mila ou à garder pour fabriquer). C'est un tri pour qui est plein, pas une source de
richesse : chaque composant de la règle a CHANCE (10 %) de sortir, MAX_PIECES (2) au plus par objet, et un objet qui ne
rend rien donne quelques hérals. Un objet se recycle en SECONDES_PAR_OBJET (3 s) dans l'écran du recycleur.

Règles, de la plus précise à la plus large :
  1. l'objet a une recette à la Table d'armurier (gunpack heraliz_crafts) : ses composants, unité par unité ;
  2. sinon une règle de famille (armures, sacs, soins, nourriture, mêlée, kits, outils...) : composants du niveau de la
     rareté de l'objet (Commun / Peu commun = niveau I, Rare = II, Épique = III, Légendaire / Unique = IV).
Jamais recyclés : composants, capsules, fragments de cartes, clés, bons, objets à vie, prêtés ou de quête (refusés par
le serveur, rendus au joueur).

    python outils/recyclage.py            écrit le fichier
    python outils/recyclage.py --verifier dit si le fichier est à jour (0 = oui)
"""
import json
import re
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
RECETTES = RACINE / "serveur/tacz/heraliz_crafts/data/heraliz_crafts/recipes"
SORTIE = RACINE / "serveur/config/heraliz_quetes/recyclage.json"

# 25/10 (demande du propriétaire : « garder une économie dure, juste quelques sous ou composants en plus ») : 10 % par
# composant au lieu de 20 %, au plus MAX_PIECES pièces par objet recyclé, hérals « rien » plus bas ; 3 s par objet
CHANCE = 0.10
MAX_PIECES = 2
SECONDES_PAR_OBJET = 3.0
# hérals donnés pour un objet qui n'a rien rendu, par rang de rareté (Commun ... Unique) ; très bas exprès
HERALS_SI_RIEN = [1, 1, 1, 2, 3, 4]

# composants qui existent en 4 niveaux (heraliz_composants:<id>, <id>_ii, _iii, _iv)
A_NIVEAUX = {"canon", "ressort", "kit_pieces", "tissu", "alcool", "batterie", "circuit_imprime", "ferraille",
             "bocal_poudre", "scotch", "caoutchouc"}
SUFFIXES = ["", "", "_ii", "_iii", "_iv", "_iv"]

# familles sans recette : (objet, clé NBT ou None, composants rendus de base, une unité par entrée). « * » en fin = préfixe
FAMILLES = [
    ("heraliz_armory:*", None, ["tissu", "tissu", "scotch"]),
    ("backpacked:*", None, ["tissu", "tissu", "scotch", "caoutchouc"]),
    ("heralisz_tactical:consumable", "ConsumableId", ["tissu", "alcool"]),
    ("heralisz_tactical:throwable", "ThrowableId", ["ferraille", "bocal_poudre"]),
    ("heralisz_heal:*", None, ["tissu", "alcool"]),  # 25/10 : soins sans recette (Bandage, Attelle…)
    ("tacz:modern_kinetic_gun", "GunId", ["canon", "ressort", "ferraille", "ferraille"]),
    ("tacz:attachment", "AttachmentId", ["ferraille", "kit_pieces"]),
    ("tacz:ammo_box", None, ["ferraille", "ferraille"]),
    ("heraliz_usure:*", None, ["kit_pieces", "scotch"]),
    ("ywzj_vehicle:fuel_tank", None, ["ferraille", "caoutchouc"]),
    ("ywzj_vehicle:repair_tool", None, ["ferraille", "kit_pieces"]),
    ("ywzj_vehicle:parachute_pack", None, ["tissu", "tissu", "corde"]),
    ("geckolib:item_*", None, ["ferraille", "ferraille"]),
    ("geckolib:food_can*", None, ["ferraille"]),
    ("geckolib:food_*", None, ["plastique"]),
]
# composants et objets de récupération qui se défont logiquement (26/10, demande du propriétaire : « recycler la corde
# pour obtenir du tissu, et vérifier les autres objets ») ; tous les autres composants restent jamais recyclés
COMPOSANTS_RECYCLABLES = {
    "corde": ["tissu", "tissu"],
    "telephone_casse": ["circuit_imprime", "batterie"],
    "radio_cassee": ["circuit_imprime", "batterie", "ferraille"],
    "montre_cassee": ["ferraille"],
    "briquet": ["ferraille"],
    "lunettes_cassees": ["plastique"],
    "ampoule_grillee": ["plastique"],
    "plaque_recyclee": ["ferraille", "ferraille"],
    "plaque_soudee": ["ferraille", "ferraille"],
    "plaque_metal": ["ferraille", "ferraille"],
    "plaque_renforcee": ["ferraille", "ferraille"],
}
FAMILLES += [("heraliz_composants:" + i, None, rend) for i, rend in COMPOSANTS_RECYCLABLES.items()]
# fusée d'extraction (26/10 : seul objet du loot sans règle) : électronique
FAMILLES.append(("heraliz_extraction:extraction_terminal", None, ["circuit_imprime", "batterie"]))
COMPOSANTS_DEFS = RACINE / "modules/equipement/src/commun/java/fr/heraliz/composants/ComponentDefs.java"


def composants_jamais():
    """Tous les composants sauf COMPOSANTS_RECYCLABLES (liste explicite : la règle « jamais » passe avant les autres)."""
    ids = re.findall(r'new Def\("([a-z_0-9]+)"', COMPOSANTS_DEFS.read_text(encoding="utf-8"))
    return ["heraliz_composants:" + i for i in sorted(set(ids)) if i not in COMPOSANTS_RECYCLABLES]


# munitions sans recette : une chance par paquet de 15
MUNITIONS = ("tacz:ammo", "AmmoId", ["bocal_poudre", "ferraille"], 15)


def composant(base, rang):
    return "heraliz_composants:" + base + (SUFFIXES[rang] if base in A_NIVEAUX else "")


def regle_recette(f):
    r = json.loads(f.read_text(encoding="utf-8"))
    res = r.get("result", {})
    rend = []
    for m in r.get("materials", []):
        item = m.get("item", {}).get("item", "")
        if item.startswith("heraliz_composants:"):
            rend += [item] * int(m.get("count", 1))
    if not rend:
        return None
    t = res.get("type")
    if t == "gun":
        return {"objet": "tacz:modern_kinetic_gun", "cle": "GunId", "valeur": res["id"], "rend": rend}
    if t == "attachment":
        return {"objet": "tacz:attachment", "cle": "AttachmentId", "valeur": res["id"], "rend": rend}
    if t == "ammo":
        return {"objet": "tacz:ammo", "cle": "AmmoId", "valeur": res["id"], "par": int(res.get("count", 1)), "rend": rend}
    if t == "custom":
        it = res.get("item", {})
        nbt = it.get("nbt", "")
        m = re.search(r"(\w+)\s*:\s*\"([^\"]+)\"", nbt)
        regle = {"objet": it.get("item", ""), "rend": rend}
        if m:
            regle.update({"cle": m.group(1), "valeur": m.group(2)})
        return regle
    return None


def construire():
    regles = []
    for dossier in sorted(RECETTES.iterdir()):
        if dossier.name == "composants":  # montées de niveau des composants : jamais recyclées
            continue
        for f in sorted(dossier.glob("*.json")):
            r = regle_recette(f)
            if r:
                regles.append(r)
    for objet, cle, base in FAMILLES:
        regle = {"objet": objet, "rend_rarete": [[composant(b, rang) for b in base] for rang in range(6)]}
        if cle:
            regle["cle"] = cle
        regles.append(regle)
    objet, cle, base, par = MUNITIONS
    regles.append({"objet": objet, "cle": cle, "par": par,
                   "rend_rarete": [[composant(b, rang) for b in base] for rang in range(6)]})
    return {
        "_note": "Écrit par outils/recyclage.py : ne pas modifier à la main.",
        "chance": CHANCE,
        "max_pieces": MAX_PIECES,
        "secondes_par_objet": SECONDES_PAR_OBJET,
        "herals_si_rien": HERALS_SI_RIEN,
        # 26/10 : jumelles et munitions de frappe (fabrication seulement, Légendaires) jamais recyclées
        "jamais": composants_jamais() + ["heralizcasino:*", "heralisz_bunker:keycard*", "heralizcrates:*",
                                         "heraliz_grades:*", "airstrike:*"],
        "regles": regles,
    }


def main():
    texte = json.dumps(construire(), ensure_ascii=False, indent=1) + "\n"
    if "--verifier" in sys.argv:
        ok = SORTIE.exists() and SORTIE.read_text(encoding="utf-8") == texte
        print("[recyclage] " + ("à jour" if ok else "PAS à jour : relancer python outils/recyclage.py"))
        sys.exit(0 if ok else 1)
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    SORTIE.write_text(texte, encoding="utf-8")
    d = json.loads(texte)
    print("[recyclage] %d règles écrites dans %s" % (len(d["regles"]), SORTIE.relative_to(RACINE)))


if __name__ == "__main__":
    main()
