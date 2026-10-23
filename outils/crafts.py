"""Recettes de la Table d'armurier (TaCZ) : armes, munitions, soins, viseurs, accessoires, explosifs.

Écrit le gunpack serveur/tacz/heraliz_crafts (recettes, noms des onglets en trois langues), les onglets de la table
(data/tacz/data/blocks/gun_smith_table.json du pack arkaniaz_pack_0_converted) et son filtre (recipe_filters/default.json :
seules les recettes de heraliz_crafts sont gardées ; les anciennes, faites avec des plans et d'anciens matériaux, ne
servent plus). Jamais à la main : changer les tableaux ci-dessous, puis relancer `python outils/crafts.py`.

Règle (choix du propriétaire, 21/10 et 22/10) : le niveau du composant donne la rareté de l'objet fabriqué :
niveau I (Peu commun) -> objet Commun, II (Rare) -> Peu commun, III (Épique) -> Rare, IV (Légendaire) -> Épique.
Rien de Légendaire ni d'Unique ne se fabrique ; ni fusils de précision, ni mitrailleuses, ni lance-grenades, ni RPG.
La poudre n'a qu'un niveau (bocal de poudre, Peu commun) : toutes les munitions la prennent.
Raretés lues dans serveur/config/heraliz_loot/raretes.json (écrites par equilibrage_armes.py).
"""
import glob, json, os, re, shutil
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
TACZ = RACINE / "serveur/tacz"
PACK = TACZ / "heraliz_crafts"
NS = "heraliz_crafts"
TABLE = TACZ / "arkaniaz_pack_0_converted/data/tacz/data/blocks/gun_smith_table.json"
FILTRE = TACZ / "arkaniaz_pack_0_converted/data/tacz/recipe_filters/default.json"
RARETES = RACINE / "serveur/config/heraliz_loot/raretes.json"
C = "heraliz_composants:"

NIVEAU = {"commun": "", "peu_commun": "_ii", "rare": "_iii", "epique": "_iv"}  # rareté de l'objet -> niveau du composant

# onglets de la table, dans l'ordre : id, (fr, en, ru), icône
ONGLETS = [
    ("munitions", ("Munitions", "Ammunition", "Боеприпасы"), {"item": "tacz:ammo", "nbt": {"AmmoId": "tacz:9mm"}}),
    ("pistolets", ("Pistolets", "Pistols", "Пистолеты"),
     {"item": "tacz:modern_kinetic_gun", "nbt": {"GunId": "tacz:glock_17"}}),
    ("mitraillettes", ("Mitraillettes", "SMGs", "Пистолеты-пулемёты"),
     {"item": "tacz:modern_kinetic_gun", "nbt": {"GunId": "tacz:uzi"}}),
    ("fusils", ("Fusils d'assaut", "Rifles", "Винтовки"),
     {"item": "tacz:modern_kinetic_gun", "nbt": {"GunId": "tacz:ak47"}}),
    ("pompes", ("Pompes", "Shotguns", "Дробовики"),
     {"item": "tacz:modern_kinetic_gun", "nbt": {"GunId": "tacz:m870"}}),
    ("soins", ("Soins", "Medical", "Медицина"),
     {"item": "heralisz_tactical:consumable", "nbt": {"ConsumableId": "heralisz_heal:ai2"}}),
    ("viseurs", ("Viseurs", "Sights", "Прицелы"), {"item": "tacz:attachment", "nbt": {"AttachmentId": "tacz:scope_acog_ta31"}}),
    ("accessoires", ("Accessoires", "Attachments", "Обвесы"),
     {"item": "tacz:attachment", "nbt": {"AttachmentId": "tacz:muzzle_compensator_trident"}}),
    ("explosifs", ("Explosifs", "Explosives", "Взрывчатка"), {"item": "crac:c4"}),
    ("composants", ("Composants", "Components", "Компоненты"), {"item": C + "canon_ii"}),
]

# armes : type TaCZ -> onglet et composants (canon, ressort, kit de pièces, ferraille), au niveau de la rareté
ARMES = {
    "pistol": ("pistolets", {"canon": 1, "ressort": 1, "kit_pieces": 1, "ferraille": 2}),
    "smg": ("mitraillettes", {"canon": 1, "ressort": 2, "kit_pieces": 1, "ferraille": 3}),
    "rifle": ("fusils", {"canon": 2, "ressort": 2, "kit_pieces": 2, "ferraille": 4}),
    "shotgun": ("pompes", {"canon": 2, "ressort": 1, "kit_pieces": 1, "ferraille": 3}),
}
ARMES_EXCLUES = {"cis:at_rpg_7_lvd"}

# munitions : un paquet de PAQUET balles = 1 bocal de poudre + 2 ferraille (bocal et ferraille de niveau I)
PAQUET = 15
MUNITION = {"bocal_poudre": 1, "ferraille": 2}

# soins : Commun = 1 tissu ; au-dessus, 1 tissu + 1 alcool, au niveau de la rareté
SOIN_COMMUN = {"tissu": 1}
SOIN = {"tissu": 1, "alcool": 1}

# accessoires et viseurs (22/10, choix du propriétaire : « ce n'est qu'un viseur ») : toujours des composants de
# niveau I ; la quantité monte avec la rareté (Commun x1, Peu commun x1, Rare x2, Épique x3)
QUANTITE_ACCESSOIRE = {"commun": 1, "peu_commun": 1, "rare": 2, "epique": 3}
ACCESSOIRES = {
    "scope": ("viseurs", {"circuit_imprime": 1, "batterie": 1}),
    "muzzle": ("accessoires", {"ferraille": 2, "scotch": 1}),
    "stock": ("accessoires", {"ferraille": 2, "scotch": 1}),
    "extended_mag": ("accessoires", {"ferraille": 2, "scotch": 1}),
    "grip": ("accessoires", {"ferraille": 1, "caoutchouc": 1, "scotch": 1}),
    "laser": ("accessoires", {"ferraille": 1, "caoutchouc": 1, "scotch": 1, "batterie": 1}),
}

# explosifs de CRac (seuls à abîmer les bases) : charge en composants de niveau II, C4 de niveau III (23/10, choix du
# propriétaire), quantités à peu près dans le rapport des dégâts (charge ~130, C4 ~300 : environ 2,3 fois)
EXPLOSIFS = [
    ("explosive_charge", "crac:explosive_charge", {"bocal_poudre": 3, "scotch_ii": 5, "corde": 1, "tissu_ii": 3}),
    ("c4", "crac:c4", {"bocal_poudre": 5, "scotch_iii": 1, "corde": 2, "circuit_imprime_ii": 3}),
]

# composants à niveaux : PAR_NIVEAU d'un niveau donnent 1 du niveau suivant, sans retour possible (23/10, choix du
# propriétaire) ; avant, 1 se défaisait en PAR_NIVEAU du niveau
# d'en dessous (22/10, choix du propriétaire : tous les crafts possibles, pas facilement)
A_NIVEAUX = ["canon", "ressort", "kit_pieces", "tissu", "alcool", "batterie", "circuit_imprime", "ferraille",
             "caoutchouc", "scotch"]
PAR_NIVEAU = 10
SANS_NIVEAU = {"bocal_poudre", "corde", "clou"}


def lire(p):
    t = Path(p).read_text(encoding="utf-8-sig")
    t = re.sub(r"^\s*//.*$", "", t, flags=re.M)
    t = re.sub(r",(\s*[}\]])", r"\1", t)
    return json.loads(t)


def index(genre):
    out = {}
    for f in glob.glob(str(TACZ / f"*/data/*/index/{genre}/*.json")):
        ns = f.split("/data/")[1].split("/")[0]
        try:
            out[f"{ns}:{Path(f).stem}"] = lire(f)
        except Exception:
            pass
    return out


def materiaux(compo, rarete):
    suffixe = NIVEAU[rarete]
    m = []
    for c, n in compo.items():
        iid = c if c in SANS_NIVEAU or c.endswith(("_ii", "_iii", "_iv")) else c + suffixe
        m.append({"item": {"item": C + iid}, "count": n})
    return m


def recette(materials, result):
    return {"type": "tacz:gun_smith_table_crafting", "materials": materials, "result": result}


def main():
    ra = json.load(open(RARETES, encoding="utf-8"))
    recettes = {}
    armes = index("guns")
    for gid, r in sorted(ra["armes"].items()):
        i = armes.get(gid)
        if r not in NIVEAU or i is None or gid in ARMES_EXCLUES or i.get("type") not in ARMES:
            continue
        onglet, compo = ARMES[i["type"]]
        recettes[f"armes/{gid.replace(':', '_')}"] = recette(
            materiaux(compo, r), {"type": "gun", "id": gid, "group": f"{NS}:{onglet}"})
    for aid, r in sorted(ra["munitions"].items()):
        if r not in NIVEAU:
            continue
        recettes[f"munitions/{aid.replace(':', '_')}"] = recette(
            materiaux(MUNITION, "commun"), {"type": "ammo", "id": aid, "count": PAQUET, "group": f"{NS}:munitions"})
    for sid, r in sorted(ra["soins"].items()):
        if r not in NIVEAU:
            continue
        recettes[f"soins/{sid.replace(':', '_')}"] = recette(
            materiaux(SOIN_COMMUN if r == "commun" else SOIN, r),
            {"type": "custom", "item": {"item": "heralisz_tactical:consumable", "nbt": f'{{ConsumableId:"{sid}"}}'},
             "group": f"{NS}:soins"})
    acc = index("attachments")
    for aid, r in sorted(ra["accessoires"].items()):
        i = acc.get(aid)
        if r not in NIVEAU or i is None or i.get("type") not in ACCESSOIRES:
            continue
        onglet, compo = ACCESSOIRES[i["type"]]
        q = QUANTITE_ACCESSOIRE[r]
        recettes[f"accessoires/{aid.replace(':', '_')}"] = recette(
            materiaux({c: n * q for c, n in compo.items()}, "commun"),
            {"type": "attachment", "id": aid, "group": f"{NS}:{onglet}"})
    for nom, item, compo in EXPLOSIFS:
        recettes[f"explosifs/{nom}"] = recette(materiaux(compo, "commun"),
                                               {"type": "custom", "item": {"item": item}, "group": f"{NS}:explosifs"})

    niveaux = ["", "_ii", "_iii", "_iv"]
    # noms numérotés : la table range les recettes par nom, donc composant par composant, niveau par niveau
    for i, c in enumerate(A_NIVEAUX):
        for j, (bas, haut) in enumerate(zip(niveaux, niveaux[1:])):
            recettes[f"composants/{i:02d}_{c}_{j}a{haut}"] = recette(
                [{"item": {"item": C + c + bas}, "count": PAR_NIVEAU}],
                {"type": "custom", "item": {"item": C + c + haut}, "group": f"{NS}:composants"})

    # gunpack refait en entier
    if PACK.exists():
        shutil.rmtree(PACK)
    for chemin, r in recettes.items():
        f = PACK / "data" / NS / "recipes" / f"{chemin}.json"
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(json.dumps(r, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    (PACK / "gunpack.meta.json").write_text(json.dumps({
        "namespace": NS, "name": "HeraliZ Crafts",
        "description": "Recettes de la Table d'armurier, refaites par outils/crafts.py (pas à la main)"},
        ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    for langue, k in (("fr_fr", 0), ("en_us", 1), ("ru_ru", 2)):
        f = PACK / "assets" / NS / "lang" / f"{langue}.json"
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(json.dumps({f"{NS}.onglet.{o}": noms[k] for o, noms, _ in ONGLETS}, ensure_ascii=False, indent=2)
                     + "\n", encoding="utf-8")

    table = lire(TABLE)
    table["tabs"] = [{"id": f"{NS}:{o}", "name": f"{NS}.onglet.{o}", "icon": icone} for o, _, icone in ONGLETS]
    TABLE.write_text(json.dumps(table, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    FILTRE.write_text(json.dumps({"whitelist": [f"^{NS}:.*$"], "blacklist": []}, indent=2) + "\n", encoding="utf-8")

    compte = {}
    for chemin, r in recettes.items():
        g = r["result"]["group"].split(":")[1]
        compte[g] = compte.get(g, 0) + 1
    print(f"[crafts] {len(recettes)} recettes : " + ", ".join(f"{compte.get(o, 0)} {o}" for o, _, _ in ONGLETS))


if __name__ == "__main__":
    main()
