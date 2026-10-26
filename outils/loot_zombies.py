"""Butin des zombies de la survie (26/10, demande du propriétaire) : tables, fragments et règles anti-farm.

Écrit, jamais à la main :
- modules/loot/src/serveur/resources/data/heraliz_loot/coffres/zombies.json et zombies_militaire.json (copie dans
  serveur/config/heraliz_loot/coffres/) : même format et même moteur que les conteneurs (TablesCoffres de HeraliZ Loot) ;
  UN tirage par zombie (la case « rien » est l'entrée minecraft:air) ;
- serveur/config/heraliz_loot/zombies.json : règles lues par le serveur (fr.heraliz.loot.ButinZombies) : quels zombies,
  quelle table, chances des fragments de cartes d'accès, anti-farm. Relu par /heralizloot tables (et /recharger).

    python outils/loot_zombies.py              écrit les tables et les règles
    python outils/loot_zombies.py --simuler    valeurs moyennes : conteneur, zombie, heure de jeu ; fragments

Valeur d'un objet (en hérals) : ce que Mila le rachète (outils/marchands.py) ; pour ce qui ne se revend pas, la moitié
du prix d'un marchand (soins, nourriture), le coût de fabrication (munitions), ou une estimation (kits, outils).
Les capsules (casino) et les fragments ne sont pas comptés en hérals : ils sont comptés à part.

Règle d'équilibrage (propriétaire, 26/10) : tuer des zombies rapporte environ DEUX FOIS MOINS par heure que fouiller des
conteneurs, munitions dépensées comprises. Les hypothèses (conteneurs et zombies par heure, munitions par zombie) sont
en tête de fichier ; à recaler sur les vrais chiffres de la bêta (/heralizloot zombies stats).
"""
import json
import random
import sys
from collections import Counter, defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import marchands as M  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
COFFRES = RACINE / "modules/loot/src/serveur/resources/data/heraliz_loot/coffres"
CONFIG_COFFRES = RACINE / "serveur/config/heraliz_loot/coffres"
REGLES = RACINE / "serveur/config/heraliz_loot/zombies.json"
RARETES = json.loads((RACINE / "serveur/config/heraliz_loot/raretes.json").read_text(encoding="utf-8"))
C = "heraliz_composants:"
FRAGMENT = "heralisz_bunker:keycard_fragment_"

# ---------------------------------------------------------------------------------------------- hypothèses de jeu
# Joueur actif, seul. À recaler avec les compteurs de la bêta (/heralizloot zombies stats).
CONTENEURS_PAR_HEURE = 60      # conteneurs REMPLIS ouverts par heure (déplacements compris)
ZOMBIES_PAR_HEURE = 90         # zombies comptés (vrais kills) par heure en ville (réserves par secteur : 5 min par place)
PART_ARME_A_FEU = 0.5          # part des zombies tués à l'arme à feu (le reste : mêlée, sans munitions)
BALLES_PAR_ZOMBIE = 6          # balles tirées par zombie à l'arme à feu (ratés compris) ; zombie : 24 à 60 PV
PALIER_ORDINAIRE = 2.5         # palier moyen des conteneurs là où l'on croise des zombies ordinaires (T2 / T3)
PALIER_MILITAIRE = 5.5         # zones militaires (aérodrome, base Sentinelle : T5 / T6)
RAPPORT_VISE = 0.5             # zombies / conteneurs, par heure, munitions déduites

# ---------------------------------------------------------------------------------------------- tables des zombies
# (objet, nbt ou None, [min, max], pour 100 zombies tués). Un seul tirage par zombie : le reste = rien.
# Familles : récupération à revendre (tag heraliz_composants:vente), composants de fabrication, petites ressources
# (construction, munitions, soins, nourriture), et le rare (kits de réparation, armes, grenades).
VENTE = {"commun": ["plastique", "planche", "collier_casse", "telephone_casse", "montre_cassee", "lunettes_cassees", "ampoule_grillee"],
         "peu_commun": ["radio_cassee", "briquet", "cigarettes", "bague_argent"],
         "rare": ["amulette", "montre_gousset", "bague_fiancailles", "appareil_photo", "crucifix"],
         "epique": ["chronographe", "collier_perles", "statuette_jade", "lingot_argent"]}
FABRICATION = ["canon", "ressort", "kit_pieces", "tissu", "alcool", "batterie", "circuit_imprime", "ferraille", "bocal_poudre",
               "caoutchouc", "scotch"]
NIVEAUX = ["", "_ii", "_iii"]  # I Peu commun, II Rare, III Épique (jamais IV sur un zombie)


def munition(aid, n):
    return ("tacz:ammo", '{AmmoId:"%s"}' % aid, n)


def soin(cid):
    return "heralisz_tactical:consumable", '{ConsumableId:"%s"}' % cid


def lignes(familles):
    """[(objet, nbt, nombre, pour 100)] à partir de {famille: (pour 100, [(objet, nbt, nombre, poids relatif)])}."""
    out = []
    for _, (pour100, objets) in familles.items():
        total = sum(o[3] for o in objets)
        for objet, nbt, nombre, poids in objets:
            out.append((objet, nbt, nombre, pour100 * poids / total))
    return out


def fab(niveau, poids=1.0):
    return [(C + c + NIVEAUX[niveau], None, [1, 1], poids * (2.0 if c in ("ferraille", "bocal_poudre", "tissu") else 1.0))
            for c in FABRICATION]


def vente(r):
    return [(C + c, None, [1, 1], 1.0) for c in VENTE[r]]


def armes(raretes):
    """Armes à feu de ces raretés (section « armes » de raretes.json) présentes dans les gunpacks, sans couteaux ; chargeur
    vide (le zombie ne donne pas de balles avec)."""
    import re
    import loot_armes as LA
    jeu = LA.armes_du_jeu()
    out = []
    for g in sorted(jeu):
        if RARETES["armes"].get(g) in raretes and "knife" not in g and "skins" not in g:
            nbt = re.sub(r"GunCurrentAmmoCount:\d+", "GunCurrentAmmoCount:0", jeu[g]).replace(",HasBulletInBarrel:1b", "")
            out.append(("tacz:modern_kinetic_gun", nbt, [1, 1], 1.0))
    return out


ARMES_MILITAIRES = armes(("commun", "peu_commun"))

TABLES = {
    # zombies des villes, villages, prison, arctique, zones dangereuses
    "zombies": lignes({
        "vente_commun": (10.0, vente("commun")),
        "vente_peu_commun": (5.0, vente("peu_commun")),
        "vente_rare": (2.5, vente("rare")),
        "vente_epique": (0.25, vente("epique")),
        "composants_i": (8.0, fab(0)),
        "composants_ii": (3.5, fab(1)),
        "composants_iii": (0.30, fab(2)),
        "construction": (4.0, [(C + "corde", None, [1, 1], 1), (C + "clou", None, [1, 1], 1.5), (C + "sac_ciment", None, [1, 1], 0.2)]),
        "munitions": (10.0, [munition("tacz:9mm", [3, 8]) + (3,), munition("tacz:45acp", [3, 6]) + (1,), munition("tacz:12g", [2, 4]) + (1,)]),
        "soins": (3.0, [soin("heralisz_heal:bandage") + ([1, 1], 1)]),
        "nourriture": (5.0, [("geckolib:food_sardines", "{FoodState:0}", [1, 1], 1), ("geckolib:food_tuna", "{FoodState:0}", [1, 1], 1),
                             ("geckolib:food_deez", "{FoodState:0}", [1, 1], 1)]),
        "plaques": (0.50, [(C + "plaque_identite", None, [1, 1], 1)]),
        "capsules": (0.50, [("heralizcasino:jeton_1", None, [1, 1], 1)]),
        "kits": (0.60, [("heraliz_usure:kit_fortune", None, [1, 1], 1)]),
    }),
    # zombies militaires (tenue MILITAIRE : aérodrome, base Sentinelle et autres zones militaires)
    "zombies_militaire": lignes({
        "vente_commun": (6.0, vente("commun")),
        "vente_peu_commun": (5.0, vente("peu_commun")),
        "vente_rare": (4.0, vente("rare")),
        "vente_epique": (0.50, vente("epique")),
        "composants_i": (8.0, fab(0)),
        "composants_ii": (6.0, fab(1)),
        "composants_iii": (0.80, fab(2)),
        "construction": (3.0, [(C + "corde", None, [1, 1], 1), (C + "clou", None, [1, 1], 1), (C + "sac_ciment", None, [1, 1], 0.3),
                               (C + "equerres_acier", None, [1, 1], 0.1)]),
        "munitions": (14.0, [munition("tacz:9mm", [4, 10]) + (2,), munition("tacz:556x45", [3, 8]) + (2,), munition("tacz:762x39", [3, 6]) + (1,),
                            munition("tacz:12g", [2, 5]) + (1,)]),
        "soins": (4.0, [soin("heralisz_heal:bandage") + ([1, 1], 2), soin("heralisz_heal:attelle") + ([1, 1], 1)]),
        "nourriture": (4.0, [("geckolib:food_mre", "{FoodState:0}", [1, 1], 1)]),
        "plaques": (6.0, [(C + "plaque_identite", None, [1, 1], 1)]),
        "grenades": (0.40, [("heralisz_tactical:throwable", '{ThrowableId:"heralisz_grenade:smoke_grenade"}', [1, 1], 1),
                            ("heralisz_tactical:throwable", '{ThrowableId:"heralisz_grenade:flash_grenade"}', [1, 1], 1)]),
        "kits": (0.60, [("heraliz_usure:kit_fortune", None, [1, 1], 3), ("heraliz_usure:kit_standard", None, [1, 1], 1)]),
        "armes": (0.30, ARMES_MILITAIRES),
    }),
}

# Échelle des tables (26/10, recalage après l'enrichissement des conteneurs du 26/10 : quantités comptées pour tout objet,
# plus de matériaux et de munitions) : (facteur sur le nombre d'objets pour 100 zombies, facteur sur les quantités des
# composants, objets de récupération, matériaux et munitions). Recalé pour tenir RAPPORT_VISE (net, munitions déduites)
# chez les zombies ordinaires ; les militaires restent un peu meilleurs (« légère différenciation »), sans suivre les
# conteneurs T5-T6 de leurs zones (ce sont eux, la vraie raison d'y aller).
ECHELLE = {"zombies": (1.6, 3.8), "zombies_militaire": (1.3, 3.5)}


RECUP = {c for liste in VENTE.values() for c in liste if c not in ("plastique", "planche")}


def mettre_a_l_echelle(lignes_, f_objets, f_quantite):
    out = []
    for objet, nbt, nombre, pour100 in lignes_:
        if objet[len(C):] in RECUP:
            nombre = [1, 2]  # objets de récupération (montre, téléphone...) : un ou deux, jamais une poignée
        elif objet.startswith(C) or objet == "tacz:ammo":
            a, b = nombre
            moyenne = (a + b) / 2 * f_quantite
            b = max(a, round(2 * moyenne - a))
            nombre = [a, min(b, 15) if objet == "tacz:ammo" else b]
        out.append((objet, nbt, nombre, pour100 * f_objets))
    return out


TABLES = {nom: mettre_a_l_echelle(TABLES[nom], *ECHELLE[nom]) for nom in TABLES}

# ---------------------------------------------------------------------------------------------- fragments
# Référence du propriétaire (26/10) : carte verte 1 pour 1 000 zombies, bleue 1 pour 2 500, rouge 1 pour 4 500.
# Une carte = 4 fragments numérotés 1 à 4 ; le numéro sorti est tiré au hasard (comme dans les conteneurs).
# Convention retenue (à valider) : « 1 carte pour N zombies » = 4 fragments pour N zombies sur l'ensemble du serveur
# (assez de fragments pour exactement une carte, si les joueurs échangent leurs doubles). Un joueur seul qui n'échange
# rien doit en moyenne trouver 8,33 fragments pour avoir les 4 numéros (25/3), donc environ deux fois plus de zombies.
CARTES_POUR = {"green": 1000, "blue": 2500, "red": 4500}
FRAGMENTS_PAR_CARTE = 4
# plafond par joueur et par jour (heure de Paris), toutes couleurs : au-delà, plus aucun fragment ce jour-là (farm)
FRAGMENTS_MAX_JOUR = 6

# ---------------------------------------------------------------------------------------------- anti-farm
ANTI_FARM = {
    "_aide": "Règles lues par fr.heraliz.loot.ButinZombies. Un zombie ne donne rien s'il n'est pas né du peuplement "
             "(étiquette heraliz_peuple) ou d'une apparition naturelle (heraliz_naturel) ; s'il a été frappé surtout par "
             "autre chose qu'un joueur (chute, feu, cactus, lave...) ; s'il n'est pas tué par un vrai joueur en survie à "
             "moins de distance_max blocs ; hors de la carte. Puis, par joueur : au-delà de kills_heure_plein zombies "
             "comptés dans l'heure (moyenne glissante), les chances baissent jusqu'à chance_min à kills_heure_zero ; "
             "même chose par carré de case_blocs blocs (kills_case_plein, kills_case_zero, mémoire case_minutes).",
    "entites": ["heralisz_utils:zombie_survie"],
    "origines": ["heraliz_peuple", "heraliz_naturel"],
    "exclus": ["heraliz_assaut", "heraliz_banc_zombie", "heraliz_bot"],
    "tables": {"MILITAIRE": "zombies_militaire", "*": "zombies"},
    "part_joueur_min": 0.5,
    "distance_max": 96,
    "kills_heure_plein": 100,
    "kills_heure_zero": 200,
    "chance_min": 0.0,
    "case_blocs": 32,
    "case_minutes": 20,
    "kills_case_plein": 25,
    "kills_case_zero": 60,
}


# ---------------------------------------------------------------------------------------------- valeurs
def rarete(objet, nbt):
    for cle, section in (("GunId", "armes"), ("AttachmentId", "accessoires"), ("AmmoId", "munitions"), ("ConsumableId", "soins")):
        if nbt and cle in nbt:
            v = nbt.split(cle)[1].split('"')[1]
            return RARETES[section].get(v, "commun")
    return RARETES["objets"].get(objet, "commun")


def r_rachat(r):
    return "mythique" if r == "unique" else r


PRIX_NOURRITURE = {o: p for o, _, p in M.NOURRITURE}
ESTIMATIONS = {"heraliz_usure:kit_fortune": 40, "heraliz_usure:kit_standard": 100, "heraliz_usure:kit_armurier": 250,
               "ywzj_vehicle:fuel_tank": 25, "ywzj_vehicle:repair_tool": 40, "ywzj_vehicle:parachute_pack": 60,
               "heraliz_extraction:extraction_terminal": 150}


def cout_balle(aid):
    """Coût de fabrication d'une balle (Table d'armurier : bocaux + ferraille, rachetés par Mila)."""
    import crafts
    r = RARETES["munitions"].get(aid, "commun")
    bocal = C + "bocal_poudre" + crafts.NIVEAU[r]
    v = crafts.BOCAUX[r] * M.RACHAT_VALEUR[RARETES["objets"].get(bocal, "peu_commun")] \
        + crafts.FERRAILLE[r] * M.RACHAT_VALEUR[RARETES["objets"].get(C + "ferraille", "peu_commun")]
    return v / crafts.PAQUET


def valeur(objet, nbt, n=1):
    """Valeur en hérals de n objets (0 : non compté, voir à part)."""
    if objet == "minecraft:air" or objet.startswith(FRAGMENT) or objet.startswith("heralizcasino:"):
        return 0.0
    r = rarete(objet, nbt)
    if objet == "tacz:ammo":
        return n * cout_balle(nbt.split('"')[1])
    if objet.startswith(C):
        return n * M.RACHAT_VALEUR[r_rachat(r)]
    if objet == "tacz:modern_kinetic_gun":
        return M.RACHAT_ARMES[r_rachat(r)]
    if objet == "tacz:attachment":
        return M.RACHAT_ACCESSOIRES[r_rachat(r)]
    if objet.startswith("heraliz_armory:"):
        return M.RACHAT_ARMURES[r_rachat(r)]
    if objet.startswith("backpacked:"):
        return M.RACHAT_SACS[r_rachat(r)]
    if objet == "heralisz_tactical:throwable":
        return M.RACHAT_GRENADES[r_rachat(r)]
    if objet == "heralisz_tactical:consumable":
        return M.PRIX_SOINS.get(r, 400) / 2
    if objet in PRIX_NOURRITURE:
        return PRIX_NOURRITURE[objet] / 2
    if objet.startswith("geckolib:food") or objet.startswith("geckolib:drink"):
        return 15.0
    return float(ESTIMATIONS.get(objet, 0))


# ---------------------------------------------------------------------------------------------- conteneurs (vrais tirages)
def lire_table(nom):
    return json.loads((COFFRES / f"{nom}.json").read_text(encoding="utf-8"))


def tirer_conteneur(t, rnd):
    """Un conteneur rempli, comme RotationLoot : nombre d'objets (cases_poids), 1re case jamais vide, pas de doublon."""
    objs = [o for o in t["objets"] if o.get("poids", 10) > 0]
    poids = [o.get("poids", 10) for o in objs]
    cp = t.get("cases_poids")
    n = int(rnd.choices(list(cp), [cp[k] for k in cp])[0]) if cp else t["emplacements"][0]
    deja, sortis = set(), []
    for k in range(n):
        for _ in range(20):
            o = rnd.choices(objs, poids)[0]
            cle = None if o["item"] in ("tacz:ammo", "tacz:ammo_box", "minecraft:air") else (o["item"] + (o.get("nbt") or ""))
            if o["item"].startswith("backpacked:"):
                cle = "sac"
            if o["item"] == "heralisz_tactical:throwable":
                cle = "grenade"
            if (cle is None or cle not in deja) and not (k == 0 and o["item"] == "minecraft:air"):
                break
        else:
            continue
        if cle:
            deja.add(cle)
        sortis.append(o)
    return sortis


def nombre_tire(o, rnd):
    """Quantité tirée, comme TablesCoffres.objet : la quantité compte pour tout objet depuis le 26/10 (avant : seulement
    les munitions) ; « suite » = quantité qui monte de 1 tant qu'un tirage passe ; munitions : 15 au plus."""
    a, b = o.get("nombre", [1, 1])
    if o.get("suite", -1) >= 0:
        n = a
        while n < b and rnd.random() < o["suite"]:
            n += 1
    else:
        n = rnd.randint(a, b)
    return min(n, 15) if o["item"] in ("tacz:ammo", "tacz:ammo_box") else n


def stats_conteneurs(essais=20000, graine=1):
    rnd = random.Random(graine)
    out = {}
    for p in range(1, 8):
        t = lire_table(f"t{p}")
        v, frag, caps = 0.0, Counter(), 0
        for _ in range(essais):
            for o in tirer_conteneur(t, rnd):
                n = nombre_tire(o, rnd)
                v += valeur(o["item"], o.get("nbt"), n)
                if o["item"].startswith(FRAGMENT):
                    frag[o["item"][len(FRAGMENT):].split("_")[0]] += 1
                if o["item"].startswith("heralizcasino:"):
                    caps += 1
        out[p] = (v / essais, {k: frag[k] / essais for k in ("green", "blue", "red")}, caps / essais)
    return out


def valeur_palier(stats, palier):
    """Valeur interpolée d'un conteneur au palier (2,5 = moyenne de T2 et T3)."""
    a = int(palier)
    b = min(7, a + 1)
    x = palier - a
    return stats[a][0] * (1 - x) + stats[b][0] * x


# ---------------------------------------------------------------------------------------------- zombies
def ev_zombie(nom):
    """Valeur moyenne d'un zombie (hors fragments) et objets pour 100 zombies."""
    v = 0.0
    for objet, nbt, nombre, pour100 in TABLES[nom]:
        v += pour100 / 100 * valeur(objet, nbt, (nombre[0] + nombre[1]) / 2)
    return v


def chances_fragments():
    """Chance (par zombie compté) d'un fragment de chaque couleur."""
    return {c: FRAGMENTS_PAR_CARTE / n for c, n in CARTES_POUR.items()}


def cout_munitions_zombie():
    return PART_ARME_A_FEU * BALLES_PAR_ZOMBIE * cout_balle("tacz:9mm")


# ---------------------------------------------------------------------------------------------- écriture
PRECISION = 1_000_000  # poids pour 100 zombies x PRECISION / 100


def table_json(nom):
    lignes_ = TABLES[nom]
    total = sum(x[3] for x in lignes_)
    if total >= 100:
        sys.exit(f"{nom} : {total:.1f} objets pour 100 zombies (plus d'un par zombie) : impossible avec un tirage")
    objets = [{"item": "minecraft:air", "nombre": [1, 1], "poids": round((100 - total) * PRECISION / 100), "_cat": "rien"}]
    for objet, nbt, nombre, pour100 in lignes_:
        o = {"item": objet, "nombre": nombre, "poids": max(1, round(pour100 * PRECISION / 100))}
        if nbt:
            o["nbt"] = nbt
        o["_pour_100"] = round(pour100, 4)
        objets.append(o)
    return {"_note": "Écrit par outils/loot_zombies.py (jamais à la main). Un tirage par zombie compté ; minecraft:air = rien.",
            "emplacements": [1, 1], "objets": objets}


def regles_json():
    d = dict(ANTI_FARM)
    d["fragments"] = {c: round(p, 6) for c, p in chances_fragments().items()}
    d["fragments_max_jour"] = FRAGMENTS_MAX_JOUR
    d["_fragments"] = ("chance par zombie compté d'un fragment de chaque couleur (numéro 1 à 4 au hasard), tirée à part de la "
                       "table ; référence : 4 fragments (une carte) pour %s zombies" % CARTES_POUR)
    return d


def ecrire():
    for nom in TABLES:
        txt = json.dumps(table_json(nom), ensure_ascii=False, indent=1) + "\n"
        for dossier in (COFFRES, CONFIG_COFFRES):
            dossier.mkdir(parents=True, exist_ok=True)
            (dossier / f"{nom}.json").write_text(txt, encoding="utf-8", newline="\n")
        print(f"{nom}.json : {len(TABLES[nom])} objets, {sum(x[3] for x in TABLES[nom]):.1f} pour 100 zombies")
    REGLES.write_text(json.dumps(regles_json(), ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"règles -> {REGLES.relative_to(RACINE)}")


# ---------------------------------------------------------------------------------------------- simulation
def simuler():
    st = stats_conteneurs()
    print("Conteneurs (vrais tirages des tables, 20 000 par palier) : valeur moyenne d'un conteneur rempli")
    for p in range(1, 8):
        v, f, caps = st[p]
        print(f"  T{p} : {v:7.1f} hérals | fragments pour 1 000 conteneurs : vert {f['green'] * 1000:.1f}, bleu {f['blue'] * 1000:.1f}, "
              f"rouge {f['red'] * 1000:.1f} | capsules {caps * 100:.1f} pour 100")
    cm = cout_munitions_zombie()
    print(f"\nMunitions : 9 mm {cout_balle('tacz:9mm'):.2f} hérals la balle ; par zombie (moyenne) {cm:.1f} hérals "
          f"({PART_ARME_A_FEU:.0%} à l'arme à feu, {BALLES_PAR_ZOMBIE} balles)")
    print(f"Hypothèses : {CONTENEURS_PAR_HEURE} conteneurs remplis / h, {ZOMBIES_PAR_HEURE} zombies / h")
    for nom, palier in (("zombies", PALIER_ORDINAIRE), ("zombies_militaire", PALIER_MILITAIRE)):
        vz = ev_zombie(nom)
        vc = valeur_palier(st, palier)
        h_c = vc * CONTENEURS_PAR_HEURE
        h_z = (vz - cm) * ZOMBIES_PAR_HEURE
        h_zb = vz * ZOMBIES_PAR_HEURE
        print(f"\n{nom} (comparé aux conteneurs T{palier}) :")
        print(f"  un zombie : {vz:.2f} hérals (brut) ; munitions {cm:.1f} -> net {vz - cm:.2f}")
        print(f"  par heure : conteneurs {h_c:.0f} hérals ; zombies {h_zb:.0f} brut, {h_z:.0f} net")
        print(f"  rapport zombies / conteneurs : {h_z / h_c:.2f} net ({h_zb / h_c:.2f} brut) ; visé {RAPPORT_VISE}")
        rien = 100 - sum(x[3] for x in TABLES[nom])
        print(f"  rien sur {rien:.0f} % des zombies ; objets pour 100 zombies : {100 - rien:.1f}")
        fam = defaultdict(float)
        for objet, nbt, nombre, pour100 in TABLES[nom]:
            n = (nombre[0] + nombre[1]) / 2
            fam[objet.split(":")[0] if not objet.startswith(C) else "composants/récup"] += pour100 / 100 * valeur(objet, nbt, n)
        print("  valeur par famille (hérals par zombie) :", {k: round(v, 2) for k, v in sorted(fam.items(), key=lambda kv: -kv[1])})
    fr = chances_fragments()
    print("\nFragments (par zombie compté, tirés à part) :")
    for c, p in fr.items():
        n = CARTES_POUR[c]
        print(f"  {c:5} : {p * 100:.3f} % -> {p * n:.1f} fragments pour {n} zombies ; un joueur seul : une carte en "
              f"{25 / 3 / p:.0f} zombies en moyenne (8,33 fragments pour avoir les 4 numéros)")
    # contrôle par tirage : chances réelles après le plafond du jour, pour un joueur qui tue Z zombies par jour
    rnd = random.Random(7)
    for z in (100, 300, 600, 1500):
        jours, frag, coupes = 3000, Counter(), 0
        for _ in range(jours):
            n_jour = 0
            for _ in range(z):
                for c, p in fr.items():
                    if rnd.random() < p:
                        if n_jour >= FRAGMENTS_MAX_JOUR:
                            coupes += 1
                            continue
                        n_jour += 1
                        frag[c] += 1
        attendu = {c: z * jours * p for c, p in fr.items()}
        print(f"  {z:4} zombies/jour : réel / annoncé = " + ", ".join(f"{c} {frag[c] / attendu[c]:.3f}" for c in fr)
              + f" ; fragments coupés par le plafond : {coupes / jours:.2f} par jour")


if __name__ == "__main__":
    simuler() if "--simuler" in sys.argv else ecrire()
