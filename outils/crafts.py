"""Recettes de la Table d'armurier (TaCZ) : armes, munitions, soins, viseurs, accessoires, explosifs, divers.

Écrit le gunpack serveur/tacz/heraliz_crafts (recettes, noms des onglets en trois langues), les onglets de la table
(data/tacz/data/blocks/gun_smith_table.json du pack heraliz_1_base_tacz) et son filtre (recipe_filters/default.json :
seules les recettes de heraliz_crafts sont gardées ; les anciennes, faites avec des plans et d'anciens matériaux, ne
servent plus). Jamais à la main : changer les tableaux ci-dessous, puis relancer `python outils/crafts.py`.

Règle (choix du propriétaire, 21/10 et 22/10) : le niveau du composant donne la rareté de l'objet fabriqué :
niveau I (Peu commun) -> objet Commun, II (Rare) -> Peu commun, III (Épique) -> Rare, IV (Légendaire) -> Épique.
Rien de Légendaire ni d'Unique ne se fabrique ; ni fusils de précision, ni mitrailleuses, ni lance-grenades, ni RPG.
Exceptions voulues par le propriétaire : la C4 de CRac (23/10) et, dans « Divers » (26/10), la carte d'accès rouge et les
jumelles de désignation (Légendaires, fabrication seulement) et la munition de frappe puissante.
Munitions : bocal de poudre I (munitions Commun et Peu commun) ou II (Rare et Épique) (28/10, choix du propriétaire),
ferraille de niveau I.
Raretés lues dans serveur/config/heraliz_loot/raretes.json (écrites par equilibrage_armes.py).
"""
import glob, json, math, os, re, shutil, sys
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
TACZ = RACINE / "serveur/tacz"
PACK = TACZ / "heraliz_crafts"
NS = "heraliz_crafts"
TABLE = TACZ / "heraliz_1_base_tacz/data/tacz/data/blocks/gun_smith_table.json"
FILTRE = TACZ / "heraliz_1_base_tacz/data/tacz/recipe_filters/default.json"
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
    ("divers", ("Divers", "Misc", "Разное"), {"item": "airstrike:designator"}),
    ("rangement", ("Rangement", "Storage", "Хранение"), {"item": "crac:coffre_fort"}),
]

# armes : type TaCZ -> onglet et composants (canon, ressort, kit de pièces, ferraille), au niveau de la rareté
ARMES = {
    "pistol": ("pistolets", {"canon": 1, "ressort": 1, "kit_pieces": 1, "ferraille": 2}),
    "smg": ("mitraillettes", {"canon": 1, "ressort": 2, "kit_pieces": 1, "ferraille": 3}),
    "rifle": ("fusils", {"canon": 2, "ressort": 2, "kit_pieces": 2, "ferraille": 4}),
    "shotgun": ("pompes", {"canon": 2, "ressort": 1, "kit_pieces": 1, "ferraille": 3}),
}
ARMES_EXCLUES = {"cis:at_rpg_7_lvd"}

# munitions : un paquet de PAQUET balles = BOCAUX bocaux de poudre du niveau de la rareté de la munition (I Commun,
# II Peu commun, III Rare, IV Épique) + FERRAILLE ferraille de niveau I. 24/10 (choix du propriétaire : « trop peu
# cher, déjà x2 pour les premières munitions grises, puis plus cher pour les balles des armes puissantes » ; « il y a
# des bocaux de poudre de certains niveaux, faut utiliser ça ») : 9 mm et .45 2 bocaux I + 4 ferraille, 12 g 2 bocaux
# II + 6, 5,56 / 7,62 / .50 AE 2 bocaux III + 8, .308 / .338 3 bocaux IV + 12
PAQUET = 15
BOCAUX = {"commun": 2, "peu_commun": 2, "rare": 2, "epique": 3}
FERRAILLE = {"commun": 4, "peu_commun": 6, "rare": 8, "epique": 12}
# 28/10 (demande du propriétaire) : niveau du bocal de poudre des munitions ramené à deux niveaux : les munitions qui
# demandaient de la poudre I ou II prennent de la poudre I, celles qui demandaient III ou IV prennent de la poudre II
# (mêmes quantités). Rareté de la munition -> suffixe du bocal.
POUDRE = {"commun": "", "peu_commun": "", "rare": "_ii", "epique": "_ii"}
# 31/10 (demande du propriétaire : « 1 poudre IV + 10 ferrailles -> un stack entier », pour les munitions de base du
# PvP) : recettes EN PLUS de la recette normale (jamais à la place), pour qui n'a qu'un bocal de poudre d'un niveau plus
# haut que celui de la munition : UN seul bocal de ce niveau, plus de balles. Par niveau au-dessus du niveau normal :
# FACTEUR_POUDRE fois plus de balles par bocal (9 mm : 7,5 par bocal I -> 20 avec un II, 45 avec un III, une pile
# entière de 60 avec un IV), arrondi à 5 et au plus une pile entière (stack_size de l'index des munitions de TaCZ).
# Ferraille I : FERRAILLE_POUDRE de celle de la recette normale, par balle (9 mm avec un IV : 10 pour 60 balles).
# Jamais une source de profit : 1 bocal d'un niveau vaut 10 du niveau d'en dessous à la table (PAR_NIVEAU), et il ne
# donne que ~2,4 fois plus de balles ; monter ses bocaux pour faire des munitions rapporte donc toujours moins que la
# recette normale (rien ne redescend d'un niveau). Le recycleur et le marchand ne s'en servent pas : outils/recyclage.py
# garde pour chaque munition la recette la moins chère (la normale), Mila ne rachète pas les munitions.
FACTEUR_POUDRE = 2.4
FERRAILLE_POUDRE = 0.625
NIVEAUX_POUDRE = ["", "_ii", "_iii", "_iv"]

# soins : Commun = 1 tissu ; au-dessus, 1 tissu + 1 alcool, au niveau de la rareté
SOIN_COMMUN = {"tissu": 1}
SOIN = {"tissu": 1, "alcool": 1}
# 31/10 (demande du propriétaire) : lait concentré et vaseline à 12 tissus (même recette qu'avant, que du tissu, de
# niveau I comme tout soin Commun ; avant : 1 tissu)
SOINS_A_PART = {"heralisz_heal:condensed_milk": {"tissu": 12}, "heralisz_heal:vaseline": {"tissu": 12}}

# accessoires et viseurs : même règle que les armes et les soins (26/10, demande du propriétaire : « ils ont tous le même
# prix, quelle que soit leur rareté ») : le niveau du composant suit la rareté de l'accessoire (Commun I, Peu commun II,
# Rare III, Épique IV), en quantité double (24/10 : « déjà x2 pour les gris »). Avant : toujours des composants de niveau
# I, seule la quantité montait (x2, x3, x5, x8).
QUANTITE_ACCESSOIRE = {"commun": 2, "peu_commun": 2, "rare": 2, "epique": 2}
ACCESSOIRES = {
    "scope": ("viseurs", {"circuit_imprime": 1, "batterie": 1}),
    "muzzle": ("accessoires", {"ferraille": 2, "scotch": 1}),
    "stock": ("accessoires", {"ferraille": 2, "scotch": 1}),
    "extended_mag": ("accessoires", {"ferraille": 2, "scotch": 1}),
    "grip": ("accessoires", {"ferraille": 1, "caoutchouc": 1, "scotch": 1}),
    "laser": ("accessoires", {"ferraille": 1, "caoutchouc": 1, "scotch": 1, "batterie": 1}),
}

# explosifs de CRac (seuls à abîmer les bases) : charge (Épique) en composants de niveau III, C4 (Légendaire) de niveau
# IV (23/10, choix du propriétaire), quantités à peu près dans le rapport des dégâts (charge ~130, C4 ~300 : environ 2,3 fois).
# 26/10 (demande du propriétaire) : poudre de niveau III pour la charge, IV pour la C4 (avant : niveau I, le suffixe
# manquait) ; la charge demande 2 tissus de niveau IV (à la place de 3 tissus III)
EXPLOSIFS = [
    ("explosive_charge", "crac:explosive_charge", {"bocal_poudre_iii": 3, "scotch_iii": 5, "corde": 1, "tissu_iv": 2}),
    ("c4", "crac:c4", {"bocal_poudre_iv": 5, "scotch_iv": 1, "corde": 2, "circuit_imprime_iv": 3}),
]

# divers (26/10, demande du propriétaire) : objets spéciaux, matériaux donnés par leur identifiant complet.
# - cartes d'accès : les 4 fragments numérotés de leur couleur (seul endroit pour fabriquer : ni grille d'artisanat 2 x 2,
#   retirée le 26/10, ni clic droit avec un fragment, retiré le 28/10) ;
# - jumelles de désignation (Légendaire, jamais dans le loot, les caisses ni les récompenses) : composants de haut niveau et
#   matériaux de la carte qui ont déjà une valeur (fil à souder) ; elles ne frappent rien sans munition ;
# - munitions de frappe, 2 frappes chacune : légère (mitraillage, fumée ; composants I-II, loot T2-T3), lourde (bombes,
#   sous-munitions, gaz ; II-III, T3-T4), spéciale (bombe guidée, napalm ; III-IV + métal renforcé, très difficile).
F = "heralisz_bunker:keycard_fragment_"
DIVERS = [
    ("1_carte_verte", "heralisz_bunker:keycard_green", {F + "green_1": 1, F + "green_2": 1, F + "green_3": 1, F + "green_4": 1}),
    ("2_carte_bleue", "heralisz_bunker:keycard_blue", {F + "blue_1": 1, F + "blue_2": 1, F + "blue_3": 1, F + "blue_4": 1}),
    ("3_carte_rouge", "heralisz_bunker:keycard_red", {F + "red_1": 1, F + "red_2": 1, F + "red_3": 1, F + "red_4": 1}),
    ("4_jumelles", "airstrike:designator", {C + "circuit_imprime_iv": 2, C + "batterie_iii": 2, C + "kit_pieces_iii": 2,
                                            C + "caoutchouc_ii": 2, C + "fil_souder": 2}),
    ("5_munition_legere", "airstrike:cartridge_light", {C + "bocal_poudre_ii": 2, C + "batterie": 2, C + "circuit_imprime": 1,
                                                        C + "ferraille": 4}),
    ("6_munition_lourde", "airstrike:cartridge_heavy", {C + "bocal_poudre_iii": 3, C + "circuit_imprime_ii": 2, C + "batterie_ii": 2,
                                                        C + "ferraille_ii": 6}),
    ("7_munition_speciale", "airstrike:cartridge_special", {C + "bocal_poudre_iv": 2, C + "circuit_imprime_iii": 2,
                                                            C + "batterie_iii": 2, C + "ferraille_iii": 4, C + "metal_renforce": 1}),
    # barricade en bois (26/10, demande du propriétaire ; avant : seulement dans les caisses) : planches, clous, corde.
    # 03/11 (demande du propriétaire : « plus coûteuse en ressources ») : coût x 3 (avant : 6 planches, 6 clous, 1 corde).
    # x 3 plutôt que x 2 : ces trois objets sont Communs et tombent dans tous les paliers du loot, un seul passage en
    # ramenait de quoi en faire plusieurs ; la barricade se pose n'importe où (piles de 3) et bloque un passage.
    ("8_barricade", "heralisz_bunker:wooden_barricade", {C + "planche": 18, C + "clou": 18, C + "corde": 3}),
    # Table d'armurier de TaCZ, à poser dans sa base (31/10, demande du propriétaire : « très chère », grosse étape de la
    # progression) : plus que le coffre-fort, en matériaux légendaires des bases (métal renforcé, fil à souder) et pièces
    # d'arme de niveau IV. Légendaire (RARETES_IMPOSEES de outils/loot_armes.py), jamais dans le loot. Objet posé de CRac
    # (tag crac:placable, PV dans serveur/defaultconfigs/crac-placable-hp.txt) : seulement dans une base de son clan.
    ("9_table_armurier", "tacz:gun_smith_table", {C + "metal_renforce": 16, C + "fil_souder": 8, C + "equerres_acier": 12,
                                                  C + "kit_pieces_iv": 4, C + "canon_iv": 2, C + "ressort_iv": 2}),
]

# coffres de base par rareté (27/10, validés par le propriétaire) : comme le reste, le niveau des composants suit la
# rareté du coffre (I Commun, II Peu commun, III Rare, IV Épique) ; le coffre-fort (Légendaire) est une exception comme les
# jumelles : composants IV et matériaux légendaires des bases (métal renforcé, fil à souder). La caisse en métal reste aussi
# vendue 150 hérals par le Bâtisseur (outils/marchands.py). Raretés dans RARETES_IMPOSEES de outils/loot_armes.py.
# 28/10 (demande du propriétaire) : coût de tous les coffres de base doublé (avant : caisse en métal 6 ferraille + 4 clous,
# malle 8/8/2/3, casier 6/2/3/1, caisse à armes 8/4/2/2/1, coffre-fort 6/3/4/2/1).
RANGEMENT = [
    ("1_caisse_metal", "crac:metal_crate", {C + "ferraille": 12, C + "clou": 8}),
    ("2_malle_chantier", "crac:malle_chantier", {C + "planches": 16, C + "clou": 16, C + "corde": 4, C + "ferraille_ii": 6}),
    ("3_casier_metallique", "crac:casier_metallique", {C + "fragments_metal": 12, C + "equerres_acier": 4, C + "ferraille_iii": 6,
                                                       C + "kit_pieces_iii": 2}),
    ("4_caisse_armes", "crac:caisse_armes", {C + "fragments_metal": 16, C + "equerres_acier": 8, C + "ferraille_iv": 4,
                                             C + "caoutchouc_iv": 4, C + "circuit_imprime_iv": 2}),
    ("5_coffre_fort", "crac:coffre_fort", {C + "metal_renforce": 12, C + "fil_souder": 6, C + "ferraille_iv": 8,
                                           C + "kit_pieces_iv": 4, C + "circuit_imprime_iv": 2}),
]

# composants à niveaux : PAR_NIVEAU d'un niveau donnent 1 du niveau suivant, sans retour possible (23/10, choix du
# propriétaire) ; avant, 1 se défaisait en PAR_NIVEAU du niveau
# d'en dessous (22/10, choix du propriétaire : tous les crafts possibles, pas facilement)
A_NIVEAUX = ["canon", "ressort", "kit_pieces", "tissu", "alcool", "batterie", "circuit_imprime", "ferraille",
             "caoutchouc", "scotch", "bocal_poudre"]
PAR_NIVEAU = 10
SANS_NIVEAU = {"corde", "clou"}


def lire(p):
    t = Path(p).read_text(encoding="utf-8-sig")
    t = re.sub(r"^\s*//.*$", "", t, flags=re.M)
    t = re.sub(r",(\s*[}\]])", r"\1", t)
    return json.loads(t)


def index(genre):
    out = {}
    for f in glob.glob(str(TACZ / f"*/data/*/index/{genre}/*.json")):
        ns = f.replace("\\", "/").split("/data/")[1].split("/")[0]
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


def par5(x):
    """Arrondi au multiple de 5 le plus proche (au moins 5)."""
    return max(5, int(math.floor(x / 5 + 0.5)) * 5)


def munitions_poudre(rarete, pile):
    """Recettes en plus d'une munition de cette rareté : [(suffixe du bocal, balles, ferraille I)], du niveau juste
    au-dessus du normal jusqu'au IV (FACTEUR_POUDRE, FERRAILLE_POUDRE). Une pile entière au plus ; deux niveaux qui
    toucheraient la même pile : celui d'en dessous en donne moins (cartes toujours différentes à la table)."""
    normal = NIVEAUX_POUDRE.index(POUDRE[rarete])
    par_bocal = PAQUET / BOCAUX[rarete]
    niveaux = NIVEAUX_POUDRE[normal + 1:]
    balles = [min(pile, par5(par_bocal * FACTEUR_POUDRE ** (k + 1))) for k in range(len(niveaux))]
    for k in range(len(balles) - 2, -1, -1):
        if balles[k] >= balles[k + 1]:
            balles[k] = par5(((balles[k - 1] if k else PAQUET) + balles[k + 1]) / 2)
    if len(set(balles + [PAQUET])) != len(balles) + 1 or balles != sorted(balles) and len(balles) > 1:
        sys.exit(f"[crafts] barème des munitions {rarete} incohérent : {balles} (normale {PAQUET})")
    fer_par_balle = FERRAILLE[rarete] / PAQUET * FERRAILLE_POUDRE
    return [(niv, b, max(1, int(math.floor(fer_par_balle * b + 0.5)))) for niv, b in zip(niveaux, balles)]


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
    piles = {k: int(v.get("stack_size", 64)) for k, v in index("ammo").items()}
    bareme = []
    for aid, r in sorted(ra["munitions"].items()):
        if r not in NIVEAU:
            continue
        nom = f"munitions/{aid.replace(':', '_')}"
        recettes[nom] = recette(
            materiaux({"bocal_poudre" + POUDRE[r]: BOCAUX[r], "ferraille": FERRAILLE[r]}, "commun"),
            {"type": "ammo", "id": aid, "count": PAQUET, "group": f"{NS}:munitions"})
        # même nom + suffixe du bocal : la table range les cartes d'une munition côte à côte, la normale d'abord
        for niv, balles, fer in munitions_poudre(r, piles.get(aid, 64)):
            recettes[f"{nom}_poudre{niv}"] = recette(
                materiaux({"bocal_poudre" + niv: 1, "ferraille": fer}, "commun"),
                {"type": "ammo", "id": aid, "count": balles, "group": f"{NS}:munitions"})
            bareme.append(f"{aid} 1 bocal{niv} + {fer} ferraille -> {balles}")
    for sid, r in sorted(ra["soins"].items()):
        if r not in NIVEAU:
            continue
        recettes[f"soins/{sid.replace(':', '_')}"] = recette(
            materiaux(SOINS_A_PART.get(sid) or (SOIN_COMMUN if r == "commun" else SOIN), r),
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
            materiaux({c: n * q for c, n in compo.items()}, r),
            {"type": "attachment", "id": aid, "group": f"{NS}:{onglet}"})
    for nom, item, compo in EXPLOSIFS:
        recettes[f"explosifs/{nom}"] = recette(materiaux(compo, "commun"),
                                               {"type": "custom", "item": {"item": item}, "group": f"{NS}:explosifs"})
    for nom, item, compo in DIVERS:
        recettes[f"divers/{nom}"] = recette([{"item": {"item": i}, "count": n} for i, n in compo.items()],
                                            {"type": "custom", "item": {"item": item}, "group": f"{NS}:divers"})
    for nom, item, compo in RANGEMENT:
        recettes[f"rangement/{nom}"] = recette([{"item": {"item": i}, "count": n} for i, n in compo.items()],
                                               {"type": "custom", "item": {"item": item}, "group": f"{NS}:rangement"})

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
        f.write_text(json.dumps(r, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    (PACK / "gunpack.meta.json").write_text(json.dumps({
        "namespace": NS, "name": "HeraliZ Crafts",
        "description": "Recettes de la Table d'armurier, refaites par outils/crafts.py (pas à la main)"},
        ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    for langue, k in (("fr_fr", 0), ("en_us", 1), ("ru_ru", 2)):
        f = PACK / "assets" / NS / "lang" / f"{langue}.json"
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(json.dumps({f"{NS}.onglet.{o}": noms[k] for o, noms, _ in ONGLETS}, ensure_ascii=False, indent=2)
                     + "\n", encoding="utf-8", newline="\n")

    table = lire(TABLE)
    table["tabs"] = [{"id": f"{NS}:{o}", "name": f"{NS}.onglet.{o}", "icon": icone} for o, _, icone in ONGLETS]
    TABLE.write_text(json.dumps(table, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    # 26/10 : aussi les recettes ajoutées en jeu au Registre de l'armurier (heraliz_tactique:atelier/...), refusées avant
    FILTRE.write_text(json.dumps({"whitelist": [f"^{NS}:.*$", "^heraliz_tactique:atelier/.*$"], "blacklist": []}, indent=2) + "\n",
                      encoding="utf-8", newline="\n")

    compte = {}
    for chemin, r in recettes.items():
        g = r["result"]["group"].split(":")[1]
        compte[g] = compte.get(g, 0) + 1
    print(f"[crafts] {len(recettes)} recettes : " + ", ".join(f"{compte.get(o, 0)} {o}" for o, _, _ in ONGLETS))
    if "--bareme" in sys.argv:
        print("\n".join("  " + b for b in bareme))


if __name__ == "__main__":
    main()
