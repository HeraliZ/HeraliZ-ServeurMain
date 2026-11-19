"""Écrit les règles du recycleur : serveur/config/heraliz_quetes/recyclage.json (jamais à la main).

Choix du propriétaire (24/10) : presque tout objet trouvé sur la carte se recycle au PNJ « recycleur » du spawn, contre
quelques composants (à revendre à Mila ou à garder pour fabriquer). Chaque composant de la règle a CHANCE (10 %) de
sortir, MAX_PIECES (2) au plus par objet, et un objet qui ne rend rien donne quelques hérals. Un objet se recycle en
SECONDES_PAR_OBJET (3 s) dans l'écran du recycleur.
19/11 (décision du propriétaire) : « le recycleur doit être un peu aléatoire : avec de la chance, il rapporte plus que
Mila, parce qu'on a plusieurs pièces ; sans chance, moins ». Armes et accessoires sont réglés sur le prix de Mila
(ARMES_PIECES, ACCESSOIRES_PIECES) : en moyenne un peu moins que Mila, un bon tirage nettement plus, un mauvais bien moins.

Paquets (« par ») : une règle peut demander N objets à la fois (munitions : un paquet de 15, comme la recette). Depuis le
27/10 (règle du propriétaire : « pouvoir recycler au moins 1 balle ; 1 balle, tu divises la proba par 15 »), le serveur
accepte aussi un paquet incomplet de n objets (1 à N) : le tirage d'un paquet complet a lieu avec une chance n / N, sinon
rien, et le temps est x n / N ; même espérance par objet (plafond MAX_PIECES et hérals compris). Rien à changer ici quand N dépasse la taille d'une pile (roquettes empilées par 6, règle « par 15 ») : chaque case
se recycle au prorata. N reste donc celui de la recette (le refus « trop rentable » se juge sur un paquet complet).

Profil « carte » (décision du propriétaire du 31/10) : le recycleur posé sur la carte (bloc heraliz_quetes:recycleur_carte,
partagé comme un coffre) suit les mêmes règles avec BONUS_CARTE (10 %) de chance en plus, relatif : une pièce à 10 % au
spawn a 11 % sur la carte. Changent : la chance (clé « profils » -> « carte ») et, depuis le 18/11, la durée
(SECONDES_CARTE : 5 s par objet, 3 au spawn) ; le refus « trop rentable » du serveur
se juge avec la chance du profil, et outils/marchands.py --controle vérifie les deux profils.

Règles, de la plus précise à la plus large :
  1. armes et accessoires (19/11) : règle de leur famille, réglée sur le prix de Mila (ARMES_PIECES,
     ACCESSOIRES_PIECES) ; pièces d'arme pour les armes, pièces selon la sorte de l'accessoire (ACCESSOIRES_BASES : un
     viseur rend des batteries, une crosse du caoutchouc...), une règle par accessoire ; jamais un composant des explosifs
     de raid (avant : leur recette, jusqu'à 3,9 fois le prix de Mila, et un Épique plus qu'un Légendaire) ;
  2. les autres objets qui ont une recette à la Table d'armurier (gunpack heraliz_crafts : soins, munitions, explosifs,
     rangements, divers) : ses composants, unité par unité ;
  3. sinon une règle de famille (armures, sacs, soins, nourriture, mêlée, kits, outils...) : composants du niveau de la
     rareté de l'objet (Commun / Peu commun = niveau I, Rare = II, Épique = III, Légendaire / Unique = IV).
Jamais recyclés : composants, capsules, fragments de cartes, clés, bons, matières brutes de HeraliZ Récolte, objets à vie,
prêtés ou de quête (refusés par le serveur, rendus au joueur).

    python outils/recyclage.py            écrit le fichier (après le contrôle des armes et accessoires)
    python outils/recyclage.py --verifier dit si le fichier est à jour et si armes et accessoires tiennent la règle du
                                          19/11 (0 = oui ; lancé par outils/verifier.py)
"""
import json
import math
import re
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(RACINE / "outils"))
import crafts  # noqa: E402

RECETTES = RACINE / "serveur/tacz/heraliz_crafts/data/heraliz_crafts/recipes"
SORTIE = RACINE / "serveur/config/heraliz_quetes/recyclage.json"
RARETES = RACINE / "serveur/config/heraliz_loot/raretes.json"

# 25/10 (demande du propriétaire : « garder une économie dure, juste quelques sous ou composants en plus ») : 10 % par
# composant au lieu de 20 %, au plus MAX_PIECES pièces par objet recyclé, hérals « rien » plus bas ; 3 s par objet
CHANCE = 0.10
MAX_PIECES = 2
SECONDES_PAR_OBJET = 3.0
# 31/10 : recycleur de la carte, chance relative en plus (10 % -> 11 %)
BONUS_CARTE = 0.10
# 18/11 (demande du propriétaire : « ça recycle trop rapidement sur la carte, 5 s ») : plus lent que le spawn (3 s)
SECONDES_CARTE = 5.0
# hérals donnés pour un objet qui n'a rien rendu, par rang de rareté (Commun ... Unique). 06/11 (demande du propriétaire) :
# Commun 2, Peu commun 5, Rare 12, Épique 25, Légendaire 50 (avant 1, 1, 1, 2, 3) ; Unique 100 (non donné par le
# propriétaire : le double du Légendaire). Un objet vendu par un marchand reste refusé s'il rapporterait plus de 50 % de
# son prix (Recyclage.tropRentable, contrôlé aussi par outils/marchands.py --controle).
HERALS_SI_RIEN = [2, 5, 12, 25, 50, 100]

# composants qui existent en 4 niveaux (heraliz_composants:<id>, <id>_ii, _iii, _iv)
A_NIVEAUX = {"canon", "ressort", "kit_pieces", "tissu", "alcool", "batterie", "circuit_imprime", "ferraille",
             "bocal_poudre", "scotch", "caoutchouc"}
SUFFIXES = ["", "", "_ii", "_iii", "_iv", "_iv"]
RANGS = ["commun", "peu_commun", "rare", "epique", "legendaire", "unique"]

# 19/11 (décision du propriétaire, après l'audit de l'économie : un accessoire Épique rapportait au recycleur jusqu'à
# 3,9 fois son prix chez Mila, et une arme Épique plus qu'une Légendaire) : armes et accessoires ne se recyclent plus en
# leur recette. Pour chaque rareté, les pièces possibles (une chance par pièce, 2 au plus, comme toute règle), choisies
# pour qu'un recyclage rende en moyenne un peu moins que Mila (ESPERANCE_MILA : 0,85 à 0,95 fois son prix au recycleur
# du spawn, bêta comprise ; celui de la carte, un peu plus généreux, reste sous le prix de Mila), et davantage pour une
# rareté plus haute : une ou deux bonnes pièces rapportent nettement plus que Mila, rien (quelques hérals) bien moins.
# Une pièce de niveau I vaut 8 hérals chez Mila, II 20, III 50, IV 200 ; une arme Commun 15 ... Légendaire 250, un
# accessoire Commun 5 ... Légendaire 100 (outils/marchands.py). Le niveau IV est le plus haut : avec 2 pièces au plus,
# l'Unique (couteaux : objets à vie, jamais recyclés) reprend les pièces du Légendaire. Cinq composants différents au plus
# par rareté : l'aperçu du recycleur n'en montre que 5 (EcranRecycleur). Chiffres : python outils/recyclage.py --verifier.
ARMES_PIECES = {
    "commun": {"canon_ii": 1, "ressort_ii": 1, "ferraille_ii": 1, "ferraille": 4, "kit_pieces": 2},
    "peu_commun": {"canon_iii": 1, "ressort_iii": 1, "ferraille_ii": 3, "kit_pieces_ii": 2},
    "rare": {"canon_iii": 2, "ressort_iii": 2, "ferraille_iii": 2, "kit_pieces_iii": 2, "ferraille_ii": 2},
    "epique": {"canon_iv": 1, "ressort_iv": 1, "ferraille_iv": 1, "ferraille_iii": 2, "kit_pieces_iii": 2},
    "legendaire": {"canon_iv": 2, "ressort_iv": 2, "ferraille_iv": 3, "kit_pieces_iv": 2},
}
ARMES_PIECES["unique"] = ARMES_PIECES["legendaire"]
# accessoires : A, B et C sont les trois pièces de leur sorte (ACCESSOIRES_BASES), suivies de leur niveau
ACCESSOIRES_PIECES = {
    "commun": {"A": 1, "B": 1},
    "peu_commun": {"A_ii": 1, "A": 1, "B": 1, "C": 1},
    "rare": {"A_iii": 1, "A": 2, "B": 1, "C": 1},
    "epique": {"A_iii": 2, "B_iii": 2, "C_iii": 1, "A": 1},
    "legendaire": {"A_iv": 1, "B_iv": 1, "A": 1, "C": 1},
    "unique": {"A_iv": 2, "B_iv": 2, "C_iv": 1, "A": 1},
}
# pièces A, B, C d'un accessoire selon sa sorte (« type » de l'index du gunpack) ; une autre sorte : ACCESSOIRE_BASES
ACCESSOIRES_BASES = {
    "scope": ("batterie", "kit_pieces", "ferraille"),        # viseurs : électronique et optique
    "laser": ("batterie", "caoutchouc", "ferraille"),
    "grip": ("caoutchouc", "ferraille", "kit_pieces"),       # poignées
    "stock": ("ferraille", "caoutchouc", "ressort"),         # crosses : talon en caoutchouc, ressort d'amortisseur
    "extended_mag": ("ressort", "ferraille", "kit_pieces"),  # chargeurs : leur ressort
    "muzzle": ("ferraille", "kit_pieces", "ferraille"),      # bouches : silencieux, freins, cache-flammes
}
ACCESSOIRE_BASES = ("ferraille", "kit_pieces", "ferraille")
ESPERANCE_MILA = (0.85, 0.95)
# composants des explosifs de raid de CRac (charge et C4, outils/crafts.py EXPLOSIFS), tous niveaux : jamais rendus par
# une arme ou un accessoire (avant le 19/11, les accessoires en rendaient : scotch et circuits imprimés jusqu'au IV)
RAID = sorted({re.sub(r"_(ii|iii|iv)$", "", c) for _, _, compo in crafts.EXPLOSIFS for c in compo})

# familles sans recette : (objet, clé NBT ou None, composants rendus de base, une unité par entrée). « * » en fin = préfixe
FAMILLES = [
    ("heraliz_armory:*", None, ["tissu", "tissu", "scotch"]),
    ("backpacked:*", None, ["tissu", "tissu", "scotch", "caoutchouc"]),
    ("heralisz_tactical:consumable", "ConsumableId", ["tissu", "alcool"]),
    ("heralisz_tactical:throwable", "ThrowableId", ["ferraille", "bocal_poudre"]),
    ("heralisz_heal:*", None, ["tissu", "alcool"]),  # 25/10 : soins sans recette (Bandage, Attelle…)
    ("tacz:ammo_box", None, ["ferraille", "ferraille"]),
    ("heraliz_usure:*", None, ["kit_pieces", "scotch"]),
    ("ywzj_vehicle:fuel_tank", None, ["ferraille", "caoutchouc"]),
    ("ywzj_vehicle:repair_tool", None, ["ferraille", "kit_pieces"]),
    ("ywzj_vehicle:parachute_pack", None, ["tissu", "tissu", "corde"]),
]
# 19/11 (audit de l'économie) : la règle « geckolib:item_* » des armes de mêlée prenait aussi 28 aliments et boissons
# (conserves, bouteille d'eau, sodas...) : les armes de mêlée sont nommées, comme les objets en métal du mod, les conserves
# rendent de la ferraille (comme geckolib:food_can*), le reste du mod de nourriture du plastique (comme geckolib:food_*)
MELEE_GECKOLIB = ["bat", "crow_bar", "fire_axe", "katana_pink", "knife", "lead_pipe", "machete", "sledge_hammer",
                  "telescopic_baton", "halloween_hoe"]
FAMILLES += [("geckolib:item_" + i, None, ["ferraille", "ferraille"]) for i in MELEE_GECKOLIB]
FAMILLES += [("geckolib:item_" + i, None, ["ferraille"]) for i in ("fire_extinguisher", "fishing_hook", "fishing_reel")]
FAMILLES += [
    ("geckolib:item_canned_*", None, ["ferraille"]),
    ("geckolib:item_*", None, ["plastique"]),
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
# 16/11 (HeraliZ Récolte, choix du propriétaire, maquettes/recolte/PROPOSITION.md §3) : les matières brutes récoltées
# s'échangent au Contremaître contre des matériaux, elles ne se revendent jamais (ni Mila, ni le recycleur : la récolte fait
# des matériaux, pas des hérals). Aucune règle ne les prend ; la liste « jamais » le garantit même si une règle large
# arrivait un jour. outils/marchands.py refuse aussi une règle de rachat ou de recyclage qui les prendrait.
MATIERES_BRUTES = ["heraliz_recolte:buche", "heraliz_recolte:pierre_brute", "heraliz_recolte:minerai_fer",
                   "heraliz_recolte:minerai_rare"]
# 16/11 (HeraliZ Récolte, demande du propriétaire : « donner un peu de loot en recyclant pioche et hache basique, et
# légendaire aussi ») : un manche en bois et une tête en acier, composants du niveau de la rareté de l'outil (hache et
# pioche Rare : niveau II ; hache-pioche Légendaire : niveau IV) ; comme toute règle, 10 % par pièce et 2 pièces au plus
FAMILLES += [("heraliz_recolte:" + o, None, ["planche", "ferraille", "ferraille", "kit_pieces"]) for o in ("hache", "pioche", "hache_pioche")]
COMPOSANTS_DEFS = RACINE / "modules/equipement/src/commun/java/fr/heraliz/composants/ComponentDefs.java"


def composants_jamais():
    """Tous les composants sauf COMPOSANTS_RECYCLABLES (liste explicite : la règle « jamais » passe avant les autres)."""
    ids = re.findall(r'new Def\("([a-z_0-9]+)"', COMPOSANTS_DEFS.read_text(encoding="utf-8"))
    return ["heraliz_composants:" + i for i in sorted(set(ids)) if i not in COMPOSANTS_RECYCLABLES]


# munitions sans recette : une chance par paquet de 15 (un paquet incomplet au prorata, côté serveur, depuis le 27/10)
MUNITIONS = ("tacz:ammo", "AmmoId", ["bocal_poudre", "ferraille"], 15)
# recettes jamais reprises par le recycleur : montées de niveau des composants ; armes et accessoires (19/11 : règles
# ci-dessus)
DOSSIERS_SANS_RECETTE = {"composants", "armes", "accessoires"}


def composant(base, rang):
    return "heraliz_composants:" + base + (SUFFIXES[rang] if base in A_NIVEAUX else "")


def pieces(compte, bases=None):
    """Composants d'une liste {composant: nombre}, une unité par entrée ; avec bases (A, B, C), « A_iii » devient la
    pièce A de la sorte au niveau III."""
    out = []
    for c, n in compte.items():
        if bases:
            lettre, _, niveau = c.partition("_")
            c = bases["ABC".index(lettre)] + ("_" + niveau if niveau else "")
        out += ["heraliz_composants:" + c] * n
    return out


def par_rarete(table, bases=None):
    return [pieces(table[r], bases) for r in RANGS]


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


def valeur_unite(regle):
    """Valeur des composants rendus par objet, 1 bocal d'un niveau valant 10 du niveau d'en dessous (comme à la table)."""
    v = 0
    for c in regle["rend"]:
        niveau = next((i for i, s in ((3, "_iv"), (2, "_iii"), (1, "_ii")) if c.endswith(s)), 0)
        v += 10 ** niveau
    return v / regle.get("par", 1)


def regles_accessoires():
    """19/11 : une règle par accessoire des gunpacks dont la sorte a ses propres pièces (ACCESSOIRES_BASES), au niveau
    de sa rareté (celle du moment : une rareté changée en jeu est suivie) ; les autres prennent la règle de famille."""
    out = []
    for aid, i in sorted(crafts.index("attachments").items()):
        bases = ACCESSOIRES_BASES.get(i.get("type"))
        if bases and bases != ACCESSOIRE_BASES:
            out.append({"objet": "tacz:attachment", "cle": "AttachmentId", "valeur": aid,
                        "rend_rarete": par_rarete(ACCESSOIRES_PIECES, bases)})
    return out


def construire():
    regles = []
    vues = {}
    for dossier in sorted(RECETTES.iterdir()):
        if dossier.name in DOSSIERS_SANS_RECETTE:
            continue
        for f in sorted(dossier.glob("*.json")):
            r = regle_recette(f)
            if not r:
                continue
            # 31/10 : plusieurs recettes pour un même objet (munitions faites avec un bocal de poudre d'un niveau plus
            # haut) : seule compte la moins chère par objet, la recette normale ; sinon recycler 60 balles faites avec des
            # bocaux I pourrait rendre un bocal IV (le serveur garde la première règle exacte d'un objet)
            k = (r["objet"], r.get("cle"), r.get("valeur"))
            if k in vues:
                i = vues[k]
                if valeur_unite(r) < valeur_unite(regles[i]):
                    regles[i] = r
                continue
            vues[k] = len(regles)
            regles.append(r)
    regles += regles_accessoires()
    regles.append({"objet": "tacz:modern_kinetic_gun", "cle": "GunId", "rend_rarete": par_rarete(ARMES_PIECES)})
    regles.append({"objet": "tacz:attachment", "cle": "AttachmentId",
                   "rend_rarete": par_rarete(ACCESSOIRES_PIECES, ACCESSOIRE_BASES)})
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
        # 31/10 : recycleur de la carte (mêmes règles, chance relative en plus) ; 18/11 : 5 s par objet
        "profils": {"carte": {"chance": round(CHANCE * (1 + BONUS_CARTE), 6), "secondes_par_objet": SECONDES_CARTE}},
        # 26/10 : jumelles et munitions de frappe (fabrication seulement, Légendaires) jamais recyclées ; 16/11 : matières
        # brutes de HeraliZ Récolte (MATIERES_BRUTES)
        "jamais": composants_jamais() + ["heralizcasino:*", "heralisz_bunker:keycard*", "heralizcrates:*",
                                         "heraliz_grades:*", "airstrike:*"] + MATIERES_BRUTES,
        "regles": regles,
    }


# ------------------------------------------------------------------------------------------------ contrôle (19/11)
def arrondi(x):
    """Math.round de Java (moitié vers le haut)."""
    return int(math.floor(x + 0.5))


def regle_de(rec, objet, nbt):
    """Règle du recycleur pour cet objet, comme Recyclage.regle (jamais, règles exactes, règles larges)."""
    import marchands
    return marchands.regle_recyclage(rec, objet, nbt)


def esperance(rend, chance, rien, valeur):
    """Gain moyen d'un recyclage (Recyclage.tirer, objet neuf) : chaque pièce sort avec la chance, au plus MAX_PIECES
    gardées au hasard parmi celles sorties, sinon les hérals « rien »."""
    k = len(rend)
    if k == 0:
        return rien
    gardee = chance * sum(math.comb(k - 1, x) * chance ** x * (1 - chance) ** (k - 1 - x) * min(1.0, MAX_PIECES / (1 + x))
                          for x in range(k))
    return gardee * sum(valeur(c) for c in rend) + (1 - chance) ** k * rien


def controle(rec, bavard=True):
    """19/11 : armes et accessoires (tous ceux des gunpacks qui ont une rareté, sauf les couteaux, à vie) : gain moyen au
    recycleur du spawn entre ESPERANCE_MILA fois le prix de Mila (au-dessus : erreur ; en dessous : avertissement), celui
    de la carte jamais au-dessus du prix de Mila, plus haut pour une rareté plus haute, aucun composant des explosifs de
    raid. Bêta du fichier de réglages du loot comprise. Rend la liste des erreurs."""
    import marchands as M
    beta = M.facteurs_beta()
    fa, fr, fy = beta or (1.0, 1.0, 1.0)
    tous = {}
    for f in sorted(M.PNJ.glob("*.json")):
        m = json.loads(f.read_text(encoding="utf-8")).get("marchand")
        if m:
            tous[f.stem] = m
    racheteurs = {k: m for k, m in tous.items() if m.get("type") == "rachat"}
    ventes = M.table_ventes(tous, fa)
    ra = json.loads(RARETES.read_text(encoding="utf-8"))
    chances = {"spawn": min(1.0, rec["chance"] * fy),
               "carte": min(1.0, rec.get("profils", {}).get("carte", {}).get("chance", rec["chance"]) * fy)}
    rien = [h if fy == 1.0 or h <= 0 else arrondi(h * fy) for h in rec["herals_si_rien"]]
    valeurs = {}

    def valeur(c):
        if c not in valeurs:
            valeurs[c] = M.meilleur_rachat(c, "", racheteurs, ventes, fr)
        return valeurs[c]

    armes = crafts.index("guns")
    objets = [("armes", "tacz:modern_kinetic_gun", '{GunId:"%s"}' % g, r) for g, r in sorted(ra["armes"].items())
              if g in armes and armes[g].get("data") != "arkaniaz:knife_data"]
    objets += [("accessoires", "tacz:attachment", '{AttachmentId:"%s"}' % a, r)
               for a, r in sorted(ra["accessoires"].items())]
    erreurs, avert = [], []
    moyennes = {}
    for fam, objet, nbt, r in objets:
        g = RANGS.index(r) if r in RANGS else 0
        regle = regle_de(rec, objet, nbt)
        if regle is None:
            erreurs.append("%s %s : aucune règle du recycleur" % (fam, nbt))
            continue
        rend = regle.get("rend") or regle["rend_rarete"][min(len(regle["rend_rarete"]) - 1, g)]
        raid = sorted({c for c in rend if re.sub(r"_(ii|iii|iv)$", "", c.split(":", 1)[1]) in RAID})
        if raid:
            erreurs.append("%s %s : composants des explosifs de raid (%s)" % (fam, nbt, ", ".join(raid)))
        if len(set(rend)) > 5:
            erreurs.append("%s %s : %d composants différents, l'aperçu du recycleur n'en montre que 5"
                           % (fam, nbt, len(set(rend))))
        # prix de Mila pour l'objet trouvé (sans le plafond des 50 % d'un article vendu : outils/marchands.py --controle)
        mila = max([M.prix_regle(m, objet, nbt, fr) for m in racheteurs.values()] + [0])
        if mila <= 0:
            continue
        ev = {p: esperance(rend, c, rien[g], valeur) / regle.get("par", 1) for p, c in chances.items()}
        moyennes.setdefault((fam, g), []).append((ev["spawn"], ev["carte"], mila))
        x, y = ev["spawn"] / mila, ev["carte"] / mila
        if x > ESPERANCE_MILA[1] + 1e-9:
            erreurs.append("%s %s (%s) : le recycleur du spawn rend %.1f en moyenne, %.2f fois Mila (%d) : plus que %.2f"
                           % (fam, nbt, r, ev["spawn"], x, mila, ESPERANCE_MILA[1]))
        if y > 1.0 + 1e-9:
            erreurs.append("%s %s (%s) : le recycleur de la carte rend %.1f en moyenne, plus que Mila (%d)"
                           % (fam, nbt, r, ev["carte"], mila))
        if x < ESPERANCE_MILA[0] - 1e-9:
            avert.append("%s %s (%s) : %.2f fois Mila (sous %.2f)" % (fam, nbt, r, x, ESPERANCE_MILA[0]))
    for fam in ("armes", "accessoires"):
        prec = None
        lignes = []
        for g in range(6):
            l = moyennes.get((fam, g))
            if not l:
                continue
            ev = sum(e[0] for e in l) / len(l)
            evc = sum(e[1] for e in l) / len(l)
            mila = sum(e[2] for e in l) / len(l)
            lignes.append("%s %.1f / %.0f (x%.2f, carte x%.2f)" % (RANGS[g], ev, mila, ev / mila, evc / mila))
            if prec is not None and ev <= prec[1]:
                erreurs.append("%s : %s rend %.1f en moyenne, pas plus que %s (%.1f)"
                               % (fam, RANGS[g], ev, prec[0], prec[1]))
            prec = (RANGS[g], ev)
        if bavard:
            print("[recyclage] %s (gain moyen / Mila%s) : %s" % (fam, ", bêta comprise" if beta else "", " ; ".join(lignes)))
    if bavard:
        for a in avert[:10]:
            print("[recyclage] avertissement : " + a)
        if len(avert) > 10:
            print("[recyclage] avertissement : ... et %d autres" % (len(avert) - 10))
        for e in erreurs:
            print("[recyclage] ERREUR : " + e)
    return erreurs


def main():
    d = construire()
    texte = json.dumps(d, ensure_ascii=False, indent=1) + "\n"
    if "--verifier" in sys.argv:
        ok = SORTIE.exists() and SORTIE.read_text(encoding="utf-8") == texte
        print("[recyclage] " + ("à jour" if ok else "PAS à jour : relancer python outils/recyclage.py"))
        erreurs = controle(json.loads(SORTIE.read_text(encoding="utf-8")) if SORTIE.exists() else d)
        if erreurs:
            print("[recyclage] %d erreur(s) : armes ou accessoires hors de la règle du 19/11 (outils/recyclage.py, "
                  "ARMES_PIECES / ACCESSOIRES_PIECES)" % len(erreurs))
        sys.exit(0 if ok and not erreurs else 1)
    if controle(d):
        raise SystemExit("[recyclage] refusé : armes ou accessoires hors de la règle du 19/11 (rien n'est écrit)")
    SORTIE.parent.mkdir(parents=True, exist_ok=True)
    # 16/11 : fin de ligne du fichier déjà là gardée (lancé sous Linux, l'outil réécrivait en LF le fichier en CRLF)
    crlf = SORTIE.exists() and b"\r\n" in SORTIE.read_bytes()[:4096]
    SORTIE.write_bytes((texte.replace("\n", "\r\n") if crlf else texte).encode("utf-8"))
    print("[recyclage] %d règles écrites dans %s" % (len(d["regles"]), SORTIE.relative_to(RACINE)))


if __name__ == "__main__":
    main()
