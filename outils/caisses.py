"""Butin des caisses de HeraliZ Caisses (serveur/config/heralizcrates/*.json) : écrit par cet outil, jamais à la main.

    python outils/caisses.py              réécrit les caisses de la table CAISSES et répare les autres
    python outils/caisses.py --verifier   ne change rien : signale tout objet de Minecraft ou inconnu

Choix du propriétaire (24/10) : plus aucun objet de Minecraft dans les caisses (diamants, XP, totems, élytres, perles…) ni
d'objet disparu. Les caisses de quêtes, de vote, d'événement et des boss donnent ce qui sert au DayZ : hérals, soins,
composants, fragments de cartes d'accès, kits de réparation, capsules du casino, clés, et un grade de la boutique pour
une semaine (très, très rare).

Une récompense « commande » est jouée par la console du serveur (RewardHelper) : hérals par `eco donner`, grade par
`grade acheter <joueur> <grade> 7` (même règle qu'un achat du site : la durée s'ajoute, un grade plus haut passe devant).
Leur icône dans la roue est un objet du serveur (bon des grades, clé), jamais donné.

Les caisses cosmétiques (camo, gemme, sticker, spectacle), Chantier (build) et Largage gardent leur contenu ; les
erreurs connues y sont réparées : soins écrits « heralisz_heal:<id> » (objet qui n'existe pas) remis en
« heralisz_tactical:consumable » + ConsumableId, grenades « heralisz_grenade:<id> » remises en
« heralisz_tactical:throwable » + ThrowableId, objets de mods retirés enlevés.
Le largage des frappes aériennes (table de butin airstrike:chests/supply_drop, module monde) est écrit ici aussi depuis
le 24/10 : une arme Rare ou Épique, des munitions et du matériel militaire (avant : pommes d'or, diamants, perles…).
Après une modification : /heraliz reload (ou redémarrage). Un butin changé en jeu (heraliz_crates/butin_jeu/) passe
devant ce fichier : `/heraliz loot fichier <caisse>` y revient.
"""
import glob
import json
import re
import sys
import zipfile
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
CAISSES_DIR = RACINE / "serveur/config/heralizcrates"
RARETES = RACINE / "serveur/config/heraliz_loot/raretes.json"
COMPOSANTS_DEFS = RACINE / "modules/equipement/src/commun/java/fr/heraliz/composants/ComponentDefs.java"
AIDE = ("Écrit par outils/caisses.py (ne pas modifier à la main). Chaque récompense : item, count, weight (poids du "
        "tirage), rarity, nbt, name, commands ({player} = pseudo), give_item (false = commandes seulement).")

# raretés des soins et des composants (HeraliZ Loot) -> raretés des caisses
RARETE = {"commun": "common", "peu_commun": "uncommon", "rare": "rare", "epique": "epic", "legendaire": "legendary",
          "unique": "mythic", "COMMON": "common", "UNCOMMON": "uncommon", "RARE": "rare", "EPIC": "epic",
          "LEGENDARY": "legendary"}
COMPOSANTS_EXCLUS = {"tonnelet_poudre", "plaque_recyclee", "plaque_soudee", "plaque_metal", "plaque_renforcee"}
ICONE_HERALS = "heraliz_grades:bon"
ICONE_GRADE = "heraliz_grades:bon"
COULEUR = {"common": "&7", "uncommon": "&a", "rare": "&9", "epic": "&5", "legendary": "&6", "mythic": "&c"}
NOM_GRADE = {"rescape": "Rescapé", "traqueur": "Traqueur", "roi_de_la_zone": "Roi de la Zone"}

# ---------------------------------------------------------------- caisses écrites par l'outil
# Poids sur 1 000 environ. Chaque ligne : (genre, réglages, poids, rareté).
#   herals N | soins <rareté HeraliZ> N | composants <rareté> N | fragments <couleur> | kit <id> N | capsules <id> N
#   cle <caisse> N | grade <id> <jours>
CAISSES = {
    # Clé de Quêtes (fin de chapitre des quêtes de Varga)
    "quete": [
        ("herals", 150, 200, "common"), ("herals", 300, 110, "uncommon"), ("herals", 600, 35, "rare"),
        ("soins", "commun", 1, 110, "common"), ("soins", "peu_commun", 1, 80, "uncommon"),
        ("soins", "rare", 1, 45, "rare"), ("soins", "epique", 1, 12, "epic"),
        ("composants", "peu_commun", 3, 130, "uncommon"), ("composants", "rare", 2, 70, "rare"),
        ("fragments", "green", 30, "rare"), ("fragments", "blue", 10, "epic"), ("fragments", "red", 2, "legendary"),
        ("kit", "kit_fortune", 1, 55, "uncommon"), ("kit", "kit_standard", 1, 25, "rare"), ("kit", "kit_armurier", 1, 6, "epic"),
        ("capsules", "jeton_5", 2, 45, "uncommon"),
        ("cle", "quete", 1, 20, "rare"),
        ("grade", "rescape", 7, 1, "mythic"),
    ],
    # Clé de Vote (petite, tous les jours)
    "vote": [
        ("herals", 50, 260, "common"), ("herals", 100, 140, "uncommon"), ("herals", 250, 40, "rare"),
        ("soins", "commun", 2, 150, "common"), ("soins", "peu_commun", 1, 70, "uncommon"),
        ("composants", "commun", 3, 120, "common"), ("composants", "peu_commun", 2, 70, "uncommon"),
        ("kit", "kit_fortune", 1, 60, "uncommon"), ("capsules", "jeton_1", 3, 70, "common"),
        ("fragments", "green", 12, "rare"),
        ("grade", "rescape", 7, 1, "mythic"),
    ],
    # Clé d'Événement (coffres T7, boss, events)
    "event": [
        ("herals", 500, 170, "uncommon"), ("herals", 1000, 90, "rare"), ("herals", 2500, 25, "epic"),
        ("composants", "rare", 3, 120, "rare"), ("composants", "epique", 2, 70, "epic"), ("composants", "legendaire", 1, 20, "legendary"),
        ("soins", "rare", 2, 80, "rare"), ("soins", "epique", 1, 40, "epic"),
        ("fragments", "blue", 45, "epic"), ("fragments", "red", 15, "legendary"),
        ("kit", "kit_standard", 1, 60, "rare"), ("kit", "kit_armurier", 1, 30, "epic"),
        ("capsules", "jeton_10", 2, 60, "rare"),
        ("cle", "event", 1, 25, "epic"),
        ("grade", "rescape", 7, 8, "legendary"), ("grade", "traqueur", 7, 2, "mythic"),
    ],
}
# caisses des boss : même base, avec la spécialité de chaque boss doublée
BOSS = [
    ("herals", 300, 160, "uncommon"), ("herals", 800, 80, "rare"), ("herals", 1500, 25, "epic"),
    ("soins", "rare", 2, 90, "rare"), ("soins", "epique", 1, 35, "epic"),
    ("composants", "rare", 3, 110, "rare"), ("composants", "epique", 2, 55, "epic"), ("composants", "legendaire", 1, 12, "legendary"),
    ("fragments", "blue", 30, "epic"), ("fragments", "red", 8, "legendary"),
    ("kit", "kit_standard", 1, 60, "rare"), ("kit", "kit_armurier", 1, 25, "epic"),
    ("capsules", "jeton_10", 1, 50, "rare"),
    ("grade", "rescape", 7, 3, "mythic"),
]
SPECIALITE = {"mere_des_ruines": "composants", "porteur_de_peste": "soins", "sujet_n07": "fragments", "sylvorn": "herals"}
for _c, _g in SPECIALITE.items():
    CAISSES[_c] = [l[:-2] + (l[-2] * 2, l[-1]) if l[0] == _g else l for l in BOSS]

# ---------------------------------------------------------------- caisses de la bêta (26/10, demande du propriétaire)
# Seules ces caisses sont ouvertes pendant la bêta : camo, gemme, event, quete, vote, spectacle (émotes). Les autres
# (stickers, chantier, trauma, largage, boss) sont vidées (DESACTIVEES) : leur clé n'est pas utilisée, rien ne sort. Taux
# plus généreux que la version finale (tous les cosmétiques seront remis à zéro après la bêta), peu de ressources.
# Chaque ligne : (genre, réglages..., pourcentage du groupe entier (partagé à parts égales entre ses objets), rareté).
#   camos <rareté d'origine> | camos_mythiques | stickers <papier|holo|foil|gold> | stattrak | couteaux <étoiles>
#   emotes <rareté> | finishers <vote|quete> <commun|rare> | herals N | grade <id> <jours> | cle <caisse> N | coeur
#   soins <rareté> N | composants <rareté> N | fragments <couleur> | kit <id> N | capsules <id> N
COSMETIQUES = RACINE / "outils/donnees/cosmetiques.json"
CAMOS_MYTHIQUES = ("heralisz_camo:skinx_seraphin", "heralisz_camo:skinx_chaines_enfer")  # seuls camos mythiques gardés
DESACTIVEES = ("sticker", "build", "trauma", "largage", "mere_des_ruines", "porteur_de_peste", "sujet_n07", "sylvorn")
# finishers (survie, ExecutionType) en deux groupes, du moins au plus travaillé (taille de leur mise en scène) ; chaque
# groupe partagé entre les caisses Vote et Quête (un sur deux) pour qu'elles ne se ressemblent pas. Le premier
# (coup_de_grace) et ceux des armes sont libres (reanimation-server.toml).
FINISHERS = {
    "commun": ["frappe_orbitale", "chute_meteore", "arrache_ame", "combo_aerien", "apesanteur", "faille", "tag_final",
               "courroux_ciel", "jugement_runique", "cadeau_surprise", "sucre_d_orge"],
    "rare": ["marionnettiste", "zero_absolu", "singularite", "onde_celeste", "danse_ombres", "mille_coupes",
             "souffle_dragon", "main_joker", "bonhomme_de_neige", "freestyle_combo", "tempete_sakura"],
}
CAISSES_BETA = {
    "camo": [
        ("camos", "common", 29, "common"), ("camos", "uncommon", 20, "uncommon"), ("camos", "rare", 13, "rare"),
        ("camos", "epic", 12, "epic"), ("camos", "legendary", 4, "legendary"), ("camos_mythiques", 1.5, "mythic"),
        ("stickers", "papier", 9, "common"), ("stickers", "holo", 5, "rare"), ("stickers", "foil", 3, "epic"),
        ("stickers", "gold", 1.5, "legendary"), ("stattrak", 2, "epic"),
    ],
    # tous les couteaux : 1 étoile Peu commun, 2 Épique, 3 Légendaire, 4 Mythique (0,12 % chacun ; ils restent Unique
    # sur leur icône, raretes.json)
    "gemme": [
        ("couteaux", 1, None, "uncommon"), ("couteaux", 2, 0.8, "epic"), ("couteaux", 3, 0.5, "legendary"),
        ("couteaux", 4, 0.12, "mythic"),
    ],
    "event": [
        ("herals", 1500, 20, "rare"), ("herals", 2000, 12, "epic"), ("herals", 3000, 6, "legendary"),
        ("grade", "rescape", 3, 6, "epic"), ("grade", "traqueur", 3, 3.5, "legendary"), ("grade", "roi_de_la_zone", 3, 2, "mythic"),
        ("couteaux_total", 4, 2, "mythic"), ("couteaux_total", 3, 8, "legendary"), ("couteaux_total", 2, 6, "epic"),
        ("camos", "epic", 10, "epic"), ("camos", "legendary", 9, "legendary"), ("camos_mythiques", 2, "mythic"),
        ("emotes", "epic", 13.5, "epic"),
    ],
    "vote": [
        ("herals", 30, 22, "common"), ("herals", 50, 14, "common"), ("herals", 100, 6, "uncommon"),
        ("grade", "rescape", 1, 2.5, "rare"), ("grade", "traqueur", 1, 1.5, "epic"), ("grade", "roi_de_la_zone", 1, 1, "legendary"),
        ("cle", "camo", 1, 5, "rare"), ("cle", "spectacle", 1, 3, "epic"), ("cle", "gemme", 1, 1, "legendary"),
        ("coeur", 3, "epic"),
        ("finishers", "vote", "commun", 14, "rare"), ("finishers", "vote", "rare", 3, "legendary"),
        ("soins", "commun", 2, 8, "common"), ("capsules", "jeton_1", 3, 8, "common"), ("composants", "peu_commun", 2, 8, "uncommon"),
    ],
    "quete": [
        ("herals", 150, 23, "common"), ("herals", 300, 10, "uncommon"), ("herals", 600, 4, "rare"),
        ("grade", "rescape", 7, 0.5, "legendary"), ("grade", "traqueur", 7, 0.3, "mythic"), ("grade", "roi_de_la_zone", 7, 0.2, "mythic"),
        ("grade", "rescape", 1, 2, "rare"), ("grade", "traqueur", 1, 1, "epic"),
        ("finishers", "quete", "commun", 14, "rare"), ("finishers", "quete", "rare", 4, "legendary"),
        ("soins", "peu_commun", 1, 7, "uncommon"), ("soins", "rare", 1, 4, "rare"),
        ("composants", "peu_commun", 3, 8, "uncommon"), ("composants", "rare", 2, 5, "rare"),
        ("fragments", "green", 4, "rare"), ("fragments", "blue", 1.5, "epic"),
        ("kit", "kit_fortune", 1, 3, "uncommon"), ("kit", "kit_standard", 1, 1.5, "rare"),
        ("capsules", "jeton_5", 2, 5, "uncommon"), ("cle", "quete", 1, 2, "rare"),
    ],
}
ECHELLE_BETA = 1000  # poids = pourcentage x ECHELLE_BETA (total d'une caisse = 100 000)


def _etoiles():
    src = (RACINE / "modules/armes/outils/skins_couteaux.py").read_text(encoding="utf-8")
    env = {}
    exec(src[src.index("ETOILES = {}"):src.index("# couleur du nom selon le rang")], env)
    return env["ETOILES"]


def couteaux_par_etoiles():
    """{étoiles: [récompense sans poids]} : couteaux TaCZ (heralisz_skins) et armes de mêlée (heralisz_melee) skinnées."""
    et = _etoiles()
    out = {}
    for f in sorted((RACINE / "serveur/tacz/heralisz_skins/data/heralisz_skins/index/guns").glob("*.json")):
        out.setdefault(et.get(f.stem, 4), []).append({"item": "tacz:modern_kinetic_gun", "count": 1, "nbt":
            '{GunId:"heralisz_skins:%s",GunFireMode:"SEMI",GunCurrentAmmoCount:1,HasBulletInBarrel:1b}' % f.stem})
    for f in sorted((RACINE / "modules/armes/src/serveur/resources/data/heralisz_melee/index/melee").glob("*__*.json")):
        out.setdefault(et.get(f.stem, 4), []).append({"item": "heralisz_tactical:melee", "count": 1,
                                                      "nbt": '{MeleeWeaponId:"heralisz_melee:%s"}' % f.stem})
    return out


def _groupe(entrees, pourcent, rar):
    """Entrées partagées à parts égales sur pourcent (%)."""
    if not entrees:
        raise SystemExit("[caisses] groupe vide (%s %%, %s)" % (pourcent, rar))
    w = max(1, round(pourcent * ECHELLE_BETA / len(entrees)))
    return [dict(e, weight=w, rarity=rar) for e in entrees]


def recompenses_beta(nom, lignes):
    cos = json.loads(COSMETIQUES.read_text(encoding="utf-8"))
    couteaux = couteaux_par_etoiles()
    spectacle = json.loads((CAISSES_DIR / "spectacle.json").read_text(encoding="utf-8"))["rewards"]
    out = []
    # gemme : pourcentage PAR couteau ; les 1 étoile se partagent le reste
    if nom == "gemme":
        fixe = sum(len(couteaux.get(l[1], [])) * l[2] for l in lignes if l[2] is not None)
        for _, et, par, rar in lignes:
            l = couteaux.get(et, [])
            out += _groupe(l, (100 - fixe) if par is None else par * len(l), rar)
        return out
    for l in lignes:
        g = l[0]
        if g == "camos":
            _, r0, p, rar = l
            out += _groupe([{"item": c["item"], "count": 1} for c in cos["camos"] if c["rarity"] == r0], p, rar)
        elif g == "camos_mythiques":
            _, p, rar = l
            out += _groupe([{"item": i, "count": 1} for i in CAMOS_MYTHIQUES], p, rar)
        elif g == "stickers":
            _, fin, p, rar = l
            suff = "" if fin == "papier" else "_" + fin
            out += _groupe([{"item": s["item"], "count": 1} for s in cos["stickers"]
                            if (s["item"].endswith(suff) if suff else not s["item"].endswith(("_holo", "_foil", "_gold")))], p, rar)
        elif g == "stattrak":
            _, p, rar = l
            out += _groupe([{"item": "heralisz_camo:stattrack_module", "count": 1}], p, rar)
        elif g == "couteaux_total":
            _, et, p, rar = l
            out += _groupe(couteaux.get(et, []), p, rar)
        elif g == "emotes":
            _, r0, p, rar = l
            out += _groupe([{k: v for k, v in e.items() if k not in ("weight", "rarity")} for e in spectacle if e.get("rarity") == r0], p, rar)
        elif g == "finishers":
            _, caisse, groupe, p, rar = l
            ids = FINISHERS[groupe][(0 if caisse == "vote" else 1)::2]
            out += _groupe([{"item": "reanimation:finisher", "count": 1, "nbt": '{Execution:"%s"}' % i} for i in ids], p, rar)
        elif g == "coeur":
            _, p, rar = l
            out += _groupe([{"item": "crac:coeur_clan", "count": 1}], p, rar)
        elif g == "herals":
            _, n, p, rar = l
            out.append(commande(ICONE_HERALS, "+%s hérals" % format(n, ",").replace(",", " "), "eco donner {player} %d Caisse" % n,
                                max(1, round(p * ECHELLE_BETA)), rar))
        elif g == "grade":
            _, gid, jours, p, rar = l
            duree = "1 jour" if jours == 1 else "%d jours" % jours
            out.append(commande(ICONE_GRADE, "Grade %s · %s" % (NOM_GRADE[gid], duree), "grade acheter {player} %s %d" % (gid, jours),
                                max(1, round(p * ECHELLE_BETA)), rar))
        elif g == "cle":
            _, c, n, p, rar = l
            out.append(commande("heralizcrates:%s_key" % c, "+%d Clé %s" % (n, NOM_CLE.get(c, c)), "heraliz givekey {player} %s %d" % (c, n),
                                max(1, round(p * ECHELLE_BETA)), rar))
        else:
            # genres des anciennes caisses (soins, composants, fragments, kit, capsules) : poids en pourcentage du groupe
            r = list(l)
            r[-2] = max(1, round(r[-2] * ECHELLE_BETA))
            out += recompenses([tuple(r)])
    return out


NOM_CLE = {"camo": "Camo", "spectacle": "Émotes", "gemme": "Gemme", "quete": "Quêtes", "event": "Événement", "vote": "Vote"}


# ---------------------------------------------------------------- réparations des autres caisses
RETIRES =("minecraft:", "dyairdrop:", "heraliz_wonder:", "heralisz_matchs:", "heralisz_armor:")


def raretes_soins():
    return {k.split(":", 1)[1]: v for k, v in json.loads(RARETES.read_text(encoding="utf-8"))["soins"].items()}


def composants():
    out = []
    for i, r, pile in re.findall(r'new Def\("([a-z_0-9]+)", "[^"]*", HeralizRarity\.([A-Z]+), (\d+)\)',
                                 COMPOSANTS_DEFS.read_text(encoding="utf-8")):
        if r in RARETE and i not in COMPOSANTS_EXCLUS:
            out.append((i, RARETE[r]))
    return out


def soin(i, n, w, rar):
    return {"item": "heralisz_tactical:consumable", "count": n, "weight": w, "rarity": rar,
            "nbt": '{ConsumableId:"heralisz_heal:%s"}' % i}


def commande(icone, nom, cmd, w, rar):
    return {"item": icone, "count": 1, "weight": w, "rarity": rar, "name": COULEUR[rar] + nom, "give_item": False,
            "commands": [cmd]}


def recompenses(lignes):
    soins = raretes_soins()
    comps = composants()
    out = []
    for l in lignes:
        g = l[0]
        if g == "herals":
            _, n, w, rar = l
            out.append(commande(ICONE_HERALS, "+%s hérals" % format(n, ",").replace(",", " "), "eco donner {player} %d Caisse" % n, w, rar))
        elif g == "soins":
            _, niv, n, w, rar = l
            ids = sorted(i for i, r in soins.items() if r == niv)
            for i in ids:
                out.append(soin(i, n, max(1, round(w / len(ids))), rar))
        elif g == "composants":
            _, niv, n, w, rar = l
            ids = sorted(i for i, r in comps if r == RARETE[niv])
            for i in ids:
                out.append({"item": "heraliz_composants:" + i, "count": n, "weight": max(1, round(w / len(ids))), "rarity": rar})
        elif g == "fragments":
            _, couleur, w, rar = l
            for k in range(1, 5):
                out.append({"item": "heralisz_bunker:keycard_fragment_%s_%d" % (couleur, k), "count": 1, "weight": max(1, round(w / 4)),
                            "rarity": rar})
        elif g == "kit":
            _, i, n, w, rar = l
            out.append({"item": "heraliz_usure:" + i, "count": n, "weight": w, "rarity": rar})
        elif g == "capsules":
            _, i, n, w, rar = l
            out.append({"item": "heralizcasino:" + i, "count": n, "weight": w, "rarity": rar})
        elif g == "cle":
            _, c, n, w, rar = l
            out.append(commande("heralizcrates:%s_key" % c, "+%d Clé" % n, "heraliz givekey {player} %s %d" % (c, n), w, rar))
        elif g == "grade":
            _, gid, jours, w, rar = l
            out.append(commande(ICONE_GRADE, "Grade %s · %d jours" % (NOM_GRADE[gid], jours),
                                "grade acheter {player} %s %d" % (gid, jours), w, rar))
        else:
            raise SystemExit("genre inconnu : %s" % g)
    return out


def reparer(r):
    """Soins et grenades mal écrits remis au bon objet ; None si l'objet doit disparaître."""
    it = r["item"]
    if it.startswith("heralisz_heal:"):
        r = dict(r, item="heralisz_tactical:consumable", nbt='{ConsumableId:"%s"}' % it)
    elif it.startswith("heralisz_grenade:"):
        r = dict(r, item="heralisz_tactical:throwable", nbt='{ThrowableId:"%s"}' % it)
    elif it.startswith(RETIRES) and not (r.get("commands") and r.get("give_item") is False):
        return None
    return r


# ---------------------------------------------------------------- contrôle : chaque objet existe
def objets_connus():
    jars = set()
    for j in glob.glob(str(RACINE / "serveur/mods-tiers/*.jar")) + glob.glob(str(RACINE / "client/mods-tiers/*.jar")):
        with zipfile.ZipFile(j) as z:
            jars.update(n for n in z.namelist() if "/models/item/" in n)
    return jars


def existe(it, jars):
    ns, p = it.split(":", 1)
    if ns == "minecraft":
        return False
    if ns == "tacz":
        return True
    if glob.glob(str(RACINE / ("modules/*/src/*/resources/assets/%s/models/item/%s.json" % (ns, p)))):
        return True
    return "assets/%s/models/item/%s.json" % (ns, p) in jars


def verifier():
    jars = objets_connus()
    problemes = 0
    for f in sorted(CAISSES_DIR.glob("*.json")):
        d = json.loads(f.read_text(encoding="utf-8"))
        for r in d.get("rewards", []):
            if r.get("commands") and r.get("give_item") is False:
                continue
            if not existe(r["item"], jars):
                print("[caisses] %s : objet de Minecraft ou inconnu : %s" % (f.name, r["item"]))
                problemes += 1
    for pool in json.loads(LARGAGE.read_text(encoding="utf-8")).get("pools", []):
        for e in pool.get("entries", []):
            if not existe(e["name"], jars):
                print("[caisses] largage aérien : objet de Minecraft ou inconnu : %s" % e["name"])
                problemes += 1
    print("[caisses] %d problème(s)" % problemes)
    return problemes


LARGAGE = RACINE / "modules/monde/src/serveur/resources/data/airstrike/loot_tables/chests/supply_drop.json"
COFFRE_T6 = RACINE / "modules/loot/src/serveur/resources/data/heraliz_loot/coffres/t6.json"


def _entree(item, poids, nbt=None, mini=1, maxi=1):
    e = {"type": "minecraft:item", "name": item, "weight": poids}
    fonctions = []
    if nbt:
        fonctions.append({"function": "minecraft:set_nbt", "tag": nbt})
    if maxi > 1:
        fonctions.append({"function": "minecraft:set_count", "count": {"type": "minecraft:uniform", "min": mini, "max": maxi}})
    if fonctions:
        e["functions"] = fonctions
    return e


# Largage aérien (26/10, demande du propriétaire : « trop pauvre et trop fragmenté, presque toujours une balle ou un seul
# composant ») : plusieurs lots, chacun avec une quantité tirée au hasard ; jamais deux largages pareils. Chaque lot :
# (nombre de tirages [min, max], liste). La caisse pose chaque tas entier dans une case (SupplyCrateEntityServeur).
LARGAGE_ARMES = {"peu_commun": 45, "rare": 40, "epique": 15}          # part de chaque rareté (arme du T6)
LARGAGE_ARME_EN_PLUS = 0.30                                           # chance d'une deuxième arme
# composants de fabrication I à IV : 4 à 5 lots ; niveau -> (poids, quantité min, max). 3 ou 4 lots de niveau III
# d'un coup : possible, très rare (moins de 1 largage sur 100)
LARGAGE_PIECES_TIRAGES = [4, 5]
LARGAGE_PIECES = {"": (60, 2, 4), "_ii": (28, 1, 3), "_iii": (10, 1, 2), "_iv": (2, 1, 1)}
PIECES = ("canon", "ressort", "kit_pieces", "tissu", "alcool", "batterie", "circuit_imprime", "ferraille", "caoutchouc",
          "scotch", "bocal_poudre")
# matériaux de base : 2 à 3 lots ; (id, poids, min, max)
LARGAGE_BASE_TIRAGES = [2, 3]
LARGAGE_BASE = [("planches", 20, 8, 20), ("clou", 20, 8, 20), ("brindilles", 14, 10, 24), ("corde", 10, 2, 5),
                ("sac_ciment", 12, 3, 8), ("pierres_taillees", 10, 3, 8), ("equerres_acier", 6, 2, 5),
                ("fragments_metal", 5, 2, 6), ("metal_renforce", 2, 1, 3), ("fil_souder", 1, 1, 2)]
# objets à revendre : 1 à 3 lots ; poids par rareté, quantité par rareté
LARGAGE_VENTE_TIRAGES = [1, 3]
LARGAGE_VENTE = {"common": (10, 1, 3), "uncommon": (6, 1, 2), "rare": (3, 1, 2), "epic": (1, 1, 1), "legendary": (0.3, 1, 1)}
# grenades : 1 à 2 lots de 1 à 2 ; munitions : 2 à 3 calibres différents ; soins : 1 à 3 lots
LARGAGE_GRENADES = [("m67", 4), ("rgn", 2), ("flash_grenade", 3), ("smoke_grenade", 3)]
LARGAGE_MUNITIONS = {"tacz:9mm": (5, 20, 45), "tacz:45acp": (4, 15, 35), "tacz:556x45": (5, 20, 45), "tacz:762x39": (5, 20, 45),
                     "tacz:12g": (3, 10, 20), "tacz:308": (3, 10, 25), "tacz:50ae": (2, 10, 20), "tacz:338": (1, 6, 12)}
LARGAGE_SOINS = {"commun": (40, 1, 3), "peu_commun": (30, 1, 2), "rare": (20, 1, 1), "epique": (10, 1, 1)}


def _pool(tirages, entrees):
    rolls = tirages if isinstance(tirages, (int, float)) else {"type": "minecraft:uniform", "min": tirages[0], "max": tirages[1]}
    return {"rolls": rolls, "entries": entrees}


def _poids(x):
    return max(1, round(x * 10))


def largage_aerien():
    """Table de butin du largage : armes, composants I à IV, matériaux de base, objets à revendre, grenades, munitions
    variées et soins, en quantités au hasard (tableaux LARGAGE_* ci-dessus)."""
    r = json.loads(RARETES.read_text(encoding="utf-8"))
    par_rarete = {}
    for o in json.loads(COFFRE_T6.read_text(encoding="utf-8"))["objets"]:
        if o.get("_cat") != "armes":
            continue
        m = re.search(r'GunId:"([^"]+)"', o.get("nbt", ""))
        rar = r["armes"].get(m.group(1)) if m else None
        if rar in LARGAGE_ARMES:
            par_rarete.setdefault(rar, []).append(o["nbt"])
    armes = [_entree("tacz:modern_kinetic_gun", _poids(LARGAGE_ARMES[rar] / len(l)), nbt)
             for rar, l in par_rarete.items() for nbt in l]
    pieces = [_entree("heraliz_composants:" + c + niv, _poids(w / len(PIECES)), mini=a, maxi=b)
              for c in PIECES for niv, (w, a, b) in LARGAGE_PIECES.items()]
    base = [_entree("heraliz_composants:" + i, _poids(w), mini=a, maxi=b) for i, w, a, b in LARGAGE_BASE]
    vente_ids = {}
    for i, cat, rar in re.findall(r'new Def\("([a-z_0-9]+)", "([^"]*)", HeralizRarity\.([A-Z]+), \d+\)',
                                  COMPOSANTS_DEFS.read_text(encoding="utf-8")):
        if "vente" in cat and RARETE.get(rar) in LARGAGE_VENTE and i not in ("planche", "plastique"):
            vente_ids.setdefault(RARETE[rar], []).append(i)
    vente = [_entree("heraliz_composants:" + i, _poids(LARGAGE_VENTE[rar][0] / len(l)), mini=LARGAGE_VENTE[rar][1],
                     maxi=LARGAGE_VENTE[rar][2]) for rar, l in vente_ids.items() for i in l]
    grenades = [_entree("heralisz_tactical:throwable", w, '{ThrowableId:"heralisz_grenade:%s"}' % g, 1, 2)
                for g, w in LARGAGE_GRENADES]
    munitions = [_entree("tacz:ammo", w, '{AmmoId:"%s"}' % a, mini, maxi) for a, (w, mini, maxi) in LARGAGE_MUNITIONS.items()
                 if a in r["munitions"]]
    soins_par = {}
    for k, rar in sorted(r["soins"].items()):
        if rar in LARGAGE_SOINS:
            soins_par.setdefault(rar, []).append(k.split(":")[-1])
    soins = [_entree("heralisz_tactical:consumable", _poids(LARGAGE_SOINS[rar][0] / len(l)),
                     '{ConsumableId:"heralisz_heal:%s"}' % i, LARGAGE_SOINS[rar][1], LARGAGE_SOINS[rar][2])
             for rar, l in soins_par.items() for i in l]
    deuxieme = dict(_pool(1, armes), conditions=[{"condition": "minecraft:random_chance", "chance": LARGAGE_ARME_EN_PLUS}])
    table = {"type": "minecraft:chest",
             "_note": "Écrit par outils/caisses.py (largage_aerien) : ne pas modifier à la main.",
             "pools": [_pool(1, armes), deuxieme,
                       _pool(LARGAGE_PIECES_TIRAGES, pieces),
                       _pool(LARGAGE_BASE_TIRAGES, base),
                       _pool(LARGAGE_VENTE_TIRAGES, vente),
                       _pool([1, 2], grenades),
                       _pool([2, 3], munitions),
                       _pool([1, 3], soins)]}
    return table


def ecrire():
    LARGAGE.write_text(json.dumps(largage_aerien(), ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print("[caisses] largage aérien écrit (%s)" % LARGAGE.relative_to(RACINE))
    for nom, lignes in CAISSES.items():
        f = CAISSES_DIR / ("%s.json" % nom)
        f.write_text(json.dumps({"_aide": AIDE, "rewards": recompenses(lignes)}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("[caisses] %s : %d récompenses" % (nom, len(json.loads(f.read_text(encoding="utf-8"))["rewards"])))
    # caisses de la bêta (26/10) : passent devant celles du dessus ; les autres sont vidées
    for nom, lignes in CAISSES_BETA.items():
        r = recompenses_beta(nom, lignes)
        f = CAISSES_DIR / ("%s.json" % nom)
        f.write_text(json.dumps({"_aide": AIDE, "rewards": r}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        total = sum(x["weight"] for x in r)
        par = {}
        for x in r:
            par[x["rarity"]] = par.get(x["rarity"], 0) + x["weight"]
        print("[caisses] %s (bêta) : %d récompenses ; %s" % (nom, len(r), ", ".join(
            "%s %.2f %%" % (k, 100 * v / total) for k, v in sorted(par.items(), key=lambda kv: -kv[1]))))
    for nom in DESACTIVEES:
        f = CAISSES_DIR / ("%s.json" % nom)
        f.write_text(json.dumps({"_aide": "Caisse fermée pendant la bêta (outils/caisses.py, DESACTIVEES) : vide, la clé "
                                          "n'est pas utilisée.", "rewards": []}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print("[caisses] %s : fermée pour la bêta" % nom)
    for f in sorted(CAISSES_DIR.glob("*.json")):
        if f.stem in CAISSES or f.stem in CAISSES_BETA or f.stem in DESACTIVEES:
            continue
        d = json.loads(f.read_text(encoding="utf-8"))
        if "rewards" not in d:
            continue
        avant = d["rewards"]
        apres = [x for x in (reparer(r) for r in avant) if x is not None]
        if f.stem == "trauma" and not any(r.get("rarity") == "legendary" for r in apres):
            apres.append(soin("surv12", 5, 3, "legendary"))   # à la place de l'ancien totem
        if apres != avant:
            d["rewards"] = apres
            f.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
            print("[caisses] %s réparée (%d -> %d récompenses)" % (f.name, len(avant), len(apres)))


if __name__ == "__main__":
    if "--verifier" not in sys.argv:
        ecrire()
    sys.exit(1 if verifier() else 0)
