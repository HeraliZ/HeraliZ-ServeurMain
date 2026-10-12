#!/usr/bin/env python3
"""Accessoires TaCZ « à plat » : chaque accessoire se pose sur toutes les armes qui ont l'emplacement de son type.

    python outils/accessoires.py              régénère le gunpack serveur/tacz/heraliz_accessoires
    python outils/accessoires.py --verifier   contrôle seulement (code de sortie 1 si le gunpack n'est plus à jour)
    python outils/accessoires.py --rapport    régénère et détaille : chiffres avant / après, emplacements de chaque
                                              arme, incohérences entre les données et les modèles des armes

Comment TaCZ 1.1.8 décide qu'un accessoire va sur une arme (lu dans le jar) :
  - l'emplacement : la donnée de l'arme (data/<ns>/data/guns/*.json, « allow_attachment_types ») liste les types
    d'accessoires qu'elle accepte (scope, muzzle, stock, grip, laser, extended_mag). L'atelier d'arme n'affiche que ces
    emplacements (IGun.allowAttachmentType) ;
  - l'accessoire : la liste de l'arme, data/<ns de l'arme>/tacz_tags/attachments/allow_attachments/<arme>.json, donne
    des identifiants d'accessoires ou des listes « #ns:nom » (data/<ns>/tacz_tags/attachments/<nom>.json, qui peuvent
    elles-mêmes contenir des listes). Le serveur ne vérifie que cette liste à la pose (IGun.allowAttachment). Les
    fichiers de même nom de TOUS les gunpacks s'additionnent : un gunpack peut ajouter des accessoires à l'arme d'un
    autre sans toucher à ses fichiers (les gunpacks du serveur le font déjà) ;
  - l'affichage : l'accessoire est dessiné sur l'os « <type>_pos » du modèle de l'arme (assets/<ns>/geo_models/...) ;
    l'os « <type>_default » (pièce d'origine) disparaît quand un accessoire de ce type est posé. Sans os « <type>_pos »,
    l'accessoire posé est invisible (et une lunette sans « scope_pos » ne sert pas à viser). Les chargeurs n'ont pas
    de modèle : l'arme montre ses os mag_extended_1 / 2 / 3 selon le niveau du chargeur. Un accessoire sans modèle
    avec un « adapter » (crosses OEM) n'existe que par un nœud du modèle de l'arme portant ce nom.

Règle de ce script : pour chaque arme et chaque type (viseurs, bouches, crosses, poignées, lasers), si l'emplacement
est ouvert dans la donnée de l'arme ET que son modèle a l'os « <type>_pos », TOUS les accessoires de ce type de tous
les gunpacks vont dessus (liste commune « #heraliz_accessoires:<liste> »). Les crosses OEM vont sur toute arme qui
a l'emplacement des crosses et le nœud du modèle qui les dessine. Rien n'est retiré : ce que les gunpacks autorisaient
avant reste autorisé. Les emplacements ne changent pas : un type fermé dans la donnée de l'arme le reste (beaucoup de
modèles ont des os « _pos » laissés au même endroit sur des armes différentes, restes du modèle de départ : un
accessoire y flotterait). Pour ouvrir un emplacement, ajouter le type à « allow_attachment_types » de l'arme, puis
relancer ce script.

Gardent leur liste d'origine : les chargeurs et munitions spéciales (extended_mag), les accessoires de PROPRES (modèle
fait pour une seule arme ou une famille) et les armes de ARMES_A_PART. Tout est écrit dans le gunpack
serveur/tacz/heraliz_accessoires (fichiers refaits à chaque lancement, jamais à la main) ; les autres gunpacks ne sont
pas touchés. Ces données sont envoyées par le serveur aux joueurs : rien à leur redonner. À relancer après l'ajout ou
la mise à jour d'un gunpack (--verifier le réclame) ; un nouvel accessoire fait pour une seule arme va dans PROPRES.
"""
import json
import sys
from collections import defaultdict
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from silencieux import lire  # noqa: E402  (lecture tolérante : commentaires, virgules finales)

DEPOT = Path(__file__).resolve().parents[1]
TACZ = DEPOT / "serveur" / "tacz"
PACK = "heraliz_accessoires"          # notre gunpack : nom du dossier et espace de noms
SORTIE = TACZ / PACK
RETIRES = DEPOT / "serveur" / "tacz_retires.txt"
ENTETE_RETIRES = "# heraliz_accessoires : listes d'accessoires retirées par outils/accessoires.py"

# type TaCZ -> nom de la liste commune (#heraliz_accessoires:<nom>)
LISTES = {"scope": "viseurs", "muzzle": "bouches", "stock": "crosses", "grip": "poignees", "laser": "lasers"}

# Accessoires qui gardent leur liste d'origine : leur modèle n'est fait que pour une arme ou une famille d'armes.
PROPRES = {
    "tacz:scope_aug_default": "lunette d'origine de l'AUG (intégrée à l'arme)",
    "tacz:sight_p90": "viseur d'origine du P90 (intégré à l'arme)",
    "tacz:scope_1873_6x": "lunette d'époque du Springfield 1873, montée sur son canon",
    "maxstuff:retro_2x_ak": "montage latéral des AK (queue d'aronde)",
    "maxstuff:scope_pso1_ak": "montage latéral des AK (queue d'aronde)",
    "maxstuff:sight_okp7_ak": "montage latéral des AK (queue d'aronde)",
    "tacz:bayonet_6h3": "baïonnette d'AK (arme de corps à corps)",
    "tacz:bayonet_m9": "baïonnette d'AR (arme de corps à corps)",
    "tacz:deagle_golden_long_barrel": "canon long du Desert Eagle doré",
    "arkaniaz:sr25s": "silencieux intégré du SR-25 (arme retirée) : il enveloppe tout le canon et traverse le "
                      "garde-main des autres armes",
}

# Armes laissées telles quelles (espace de noms ou identifiant d'arme).
ARMES_A_PART = {
    "heraliz_wonder": "Wonder Weapons du mode zombies",
}

# Accessoires retirés de certaines armes (« * » : de toutes) : ils y flottent ou les traversent, mesuré par
# outils/verif_accessoires.py. accessoire -> (raison, armes). Une arme concernée reçoit, pour ce type, la liste
# commune sans eux (identifiants écrits un à un) au lieu de « #heraliz_accessoires:<liste> ». Sans effet si la liste
# d'origine de l'arme (son gunpack) donne déjà l'accessoire : TaCZ additionne les listes, on ne peut rien y retirer
# d'ici (--verifier le signale).
EXCLUS = {
    "gucci_attachments:laser_hs507c": ("modèle décalé de 10 px au-dessus de son point de montage : flotte à côté de "
                                       "toutes les armes", "*"),
    "gucci_attachments:grip_bipod": (
        "bipied dessiné 8 à 12 px devant son point de montage : flotte devant le garde-main des armes courtes",
        ["arkaniaz:9a91", "arkaniaz:aks74u", "arkaniaz:evo3", "arkaniaz:gm6_lynx", "arkaniaz:negev", "arkaniaz:tec9",
         "arkaniaz:ump9", "cis:at_rpg_7_lvd", "heraliz_fuse:mp7r", "heraliz_fuse:ro635v3", "maxstuff:aks74u",
         "maxstuff:arp", "maxstuff:g36", "maxstuff:glock_bulpup", "maxstuff:honey_badger", "maxstuff:m320t",
         "maxstuff:m870t", "maxstuff:scar_hamr", "maxstuff:vector9", "tacz:hk416d", "tacz:m4a1", "tacz:mk14",
         "tacz:scar_l", "tacz:sks_tactical", "tacz:spr15hb", "tacz:ump45", "tacz:vector45"]),
    "gucci_attachments:grip_cqr": (
        "poignée longue : traverse le chargeur ou la carcasse",
        ["arkaniaz:tec9", "arkaniaz:ump9", "arkaniaz_custom:vector_firmax", "arkaniaz_custom:vectorhy",
         "cis:at_rpg_7_lvd", "maxstuff:m320t", "maxstuff:scar_hamr", "maxstuff:vector9", "tacz:m249", "tacz:mk14",
         "tacz:sks_tactical", "tacz:vector45"]),
    "gucci_attachments:grip_cqr_g2": (
        "poignée longue : traverse le chargeur ou la carcasse",
        ["arkaniaz:tec9", "arkaniaz:ump9", "arkaniaz_custom:vector_firmax", "arkaniaz_custom:vectorhy",
         "cis:at_rpg_7_lvd", "maxstuff:g36", "maxstuff:m320t", "maxstuff:scar_hamr", "maxstuff:vector9",
         "tacz:m249", "tacz:mk14", "tacz:ump45", "tacz:vector45"]),
    "tacz:grip_cqr": (
        "poignée longue : traverse le chargeur ou la carcasse",
        ["arkaniaz:aks74u", "arkaniaz:mod2", "arkaniaz:negev", "arkaniaz:pkp", "arkaniaz:tec9", "arkaniaz:ump9",
         "arkaniaz_custom:vector_firmax", "arkaniaz_custom:vectorhy", "cis:at_rpg_7_lvd", "heraliz_fuse:mp7r",
         "maxstuff:aks74u", "maxstuff:ar10", "maxstuff:g36", "maxstuff:glock_bulpup", "maxstuff:hk417",
         "maxstuff:honey_badger", "maxstuff:m320t", "maxstuff:mk11", "maxstuff:scar_hamr", "maxstuff:scar_pdw",
         "maxstuff:scar_sbr", "maxstuff:vector9", "tacz:hk416d", "tacz:m249", "tacz:mk14", "tacz:scar_h",
         "tacz:scar_l", "tacz:sks_tactical", "tacz:ump45", "tacz:vector45"]),
    "maxstuff:a3_grip": (
        "poignée longue : traverse le chargeur ou la carcasse",
        ["arkaniaz:aks74u", "arkaniaz:evo3", "arkaniaz:tec9", "cis:at_rpg_7_lvd", "maxstuff:glock_bulpup",
         "tacz:m249"]),
    "maxstuff:underbarrel_chainsaw": (
        "tronçonneuse sous le canon : traverse la poignée ou le bipied de l'arme",
        ["cis:at_rpg_7_lvd", "tacz:mk14", "tacz:spr15hb"]),
    "gucci_attachments:muzzle_s_fd197": (
        "silencieux à manchon : sa partie arrière traverse le chargeur hélicoïdal, sous le canon", ["arkaniaz:pp19"]),
    "maxstuff:scw_stock": (
        "crosse sur rails, faite pour une carcasse longue : flotte derrière ces armes",
        ["heraliz_fuse:mp5_classic", "maxstuff:db_short_tactical"]),
}

META = {
    "namespace": PACK,
    "name": "HeraliZ Accessoires",
    "description": "Accessoires à plat : listes communes par type, refaites par outils/accessoires.py (pas à la main)",
}


# ------------------------------------------------------------------ lecture des gunpacks (sauf le nôtre)
def charger(f):
    texte = f.read_text(encoding="utf-8-sig")
    try:
        return json.loads(texte)
    except ValueError:
        return lire(f)


def identifiants(motif, profondeur):
    """{identifiant: [fichiers]} des gunpacks : <pack>/<data|assets>/<ns>/<profondeur dossiers>/<chemin>.json."""
    out = defaultdict(list)
    for f in sorted(TACZ.glob(motif)):
        p = f.relative_to(TACZ).parts
        if p[0] != PACK:
            out[p[2] + ":" + "/".join(p[3 + profondeur:])[:-5]].append(f)
    return out


def listes_existantes():
    """(listes « #ns:nom », listes des armes) des gunpacks, fichiers de même nom additionnés comme dans TaCZ."""
    listes, armes = defaultdict(list), defaultdict(list)
    for f in sorted(TACZ.glob("*/data/*/tacz_tags/attachments/**/*.json")):
        p = f.relative_to(TACZ).parts
        if p[0] == PACK:
            continue
        chemin = "/".join(p[5:])[:-5]
        contenu = charger(f)
        contenu = [x for x in contenu if isinstance(x, str)] if isinstance(contenu, list) else []
        if chemin.startswith("allow_attachments/"):
            armes[p[2] + ":" + chemin[len("allow_attachments/"):]] += contenu
        else:
            listes[p[2] + ":" + chemin] += contenu
    return listes, armes


def resoudre(entrees, listes, vues=None):
    """Identifiants d'accessoires d'une liste TaCZ (listes « #ns:nom » suivies)."""
    vues = set() if vues is None else vues
    out = set()
    for e in entrees:
        if e.startswith("#"):
            nom = e[1:] if ":" in e else "minecraft:" + e[1:]
            if nom not in vues:
                vues.add(nom)
                out |= resoudre(listes.get(nom, []), listes, vues)
        else:
            out.add(e if ":" in e else "minecraft:" + e)
    return out


class Gunpacks:
    """Index, données, affichages, modèles et listes d'accessoires de tous les gunpacks (sauf heraliz_accessoires)."""

    def __init__(self):
        self.armes = identifiants("*/data/*/index/guns/**/*.json", 2)
        self.donnees_armes = identifiants("*/data/*/data/guns/**/*.json", 2)
        self.affichages_armes = identifiants("*/assets/*/display/guns/**/*.json", 2)
        self.accessoires = identifiants("*/data/*/index/attachments/**/*.json", 2)
        self.affichages_acc = identifiants("*/assets/*/display/attachments/**/*.json", 2)
        self.modeles = identifiants("*/assets/*/geo_models/**/*.json", 1)
        self.listes, self.listes_armes = listes_existantes()
        self.alertes = []
        self.type_acc, self.affichage_acc = {}, {}
        for a, fs in sorted(self.accessoires.items()):
            idx = charger(fs[0])
            self.type_acc[a] = idx.get("type")
            aff = self.affichages_acc.get(idx.get("display") or "", [])
            self.affichage_acc[a] = charger(aff[0]) if aff else {}

    def types_arme(self, arme):
        """Emplacements ouverts (« allow_attachment_types ») ; donnée en double dans plusieurs gunpacks : ceux communs
        à toutes (on ne sait pas laquelle TaCZ garde)."""
        idx = charger(self.armes[arme][0])
        copies = [set(charger(f).get("allow_attachment_types") or [])
                  for f in self.donnees_armes.get(idx.get("data") or "", [])]
        if not copies:
            self.alertes.append("%s : donnée de l'arme introuvable (%s)" % (arme, idx.get("data")))
            return set()
        if len({frozenset(c) for c in copies}) > 1:
            self.alertes.append("%s : donnée en %d exemplaires aux emplacements différents %s (seuls les communs "
                                "comptent)" % (arme, len(copies), [sorted(c) for c in copies]))
        return set.intersection(*copies)

    def os_arme(self, arme):
        """Noms des os du modèle de l'arme ; affichage en double : ceux présents dans tous les modèles."""
        idx = charger(self.armes[arme][0])
        ensembles = []
        for f in self.affichages_armes.get(idx.get("display") or "", []):
            modele = charger(f).get("model")
            noms = set()
            for g in self.modeles.get(modele or "", []):
                d = charger(g)
                geos = d.get("minecraft:geometry") or [v for k, v in d.items() if k.startswith("geometry.")]
                for geo in geos if isinstance(geos, list) else [geos]:
                    noms |= {b.get("name") for b in geo.get("bones", [])}
            if not noms:
                self.alertes.append("%s : modèle %s introuvable ou vide" % (arme, modele))
            ensembles.append(noms)
        return set.intersection(*ensembles) if ensembles else set()


# ------------------------------------------------------------------ règle
def par_noeud(gp, acc):
    """Nœud du modèle de l'arme qui dessine l'accessoire, s'il n'a pas de modèle à lui (crosses OEM), sinon None."""
    aff = gp.affichage_acc.get(acc, {})
    return aff.get("adapter") if not aff.get("model") and aff.get("adapter") else None


def a_part(arme):
    return arme in ARMES_A_PART or arme.split(":")[0] in ARMES_A_PART


def exclus_de(arme):
    """Accessoires que EXCLUS retire de cette arme."""
    return {a for a, (_, armes) in EXCLUS.items() if armes == "*" or arme in armes}


def calculer(gp):
    """(listes communes {type: [accessoires]}, ajouts {arme: [entrées]}, détail {arme: (types, os)})."""
    partout = {a for a, (_, armes) in EXCLUS.items() if armes == "*"}
    communes = {t: sorted(a for a, ty in gp.type_acc.items()
                          if ty == t and a not in PROPRES and a not in partout and par_noeud(gp, a) is None)
                for t in LISTES}
    noeuds = {a: (gp.type_acc[a], par_noeud(gp, a)) for a in gp.type_acc
              if gp.type_acc[a] in LISTES and a not in PROPRES and par_noeud(gp, a)}
    ajouts, detail = {}, {}
    for arme in sorted(gp.armes):
        if a_part(arme):
            continue
        types, os_ = gp.types_arme(arme), gp.os_arme(arme)
        detail[arme] = (types, os_)
        exclus = exclus_de(arme)
        entrees = []
        for t in LISTES:
            if t in types and t + "_pos" in os_:
                if exclus & set(communes[t]):
                    entrees += [a for a in communes[t] if a not in exclus]
                else:
                    entrees.append("#%s:%s" % (PACK, LISTES[t]))
        entrees += sorted(a for a, (t, n) in noeuds.items() if t in types and n in os_ and a not in exclus)
        if entrees:
            ajouts[arme] = entrees
    return communes, ajouts, detail


def fichiers_voulus(communes, ajouts):
    """{chemin relatif au gunpack: texte} de tout le gunpack heraliz_accessoires."""
    def texte(x):
        return json.dumps(x, indent=2, ensure_ascii=False) + "\n"
    out = {"gunpack.meta.json": texte(META)}
    for t, nom in LISTES.items():
        out["data/%s/tacz_tags/attachments/%s.json" % (PACK, nom)] = texte(communes[t])
    for arme, entrees in ajouts.items():
        ns, chemin = arme.split(":", 1)
        out["data/%s/tacz_tags/attachments/allow_attachments/%s.json" % (ns, chemin)] = texte(entrees)
    return out


# ------------------------------------------------------------------ rapport
def rapport(gp, communes, ajouts, detail):
    listes_apres = dict(gp.listes)
    for t, nom in LISTES.items():
        listes_apres["%s:%s" % (PACK, nom)] = communes[t]

    def posables(arme, apres):
        types = detail[arme][0]
        entrees = gp.listes_armes.get(arme, []) + (ajouts.get(arme, []) if apres else [])
        return {a for a in resoudre(entrees, listes_apres if apres else gp.listes)
                if gp.type_acc.get(a) in types}

    armes = sorted(detail)
    equipables = [a for a in armes if detail[a][0]]      # armes qui ont au moins un emplacement (pas les couteaux)
    for quand, apres in (("avant", False), ("après", True)):
        par_arme = {a: posables(a, apres) for a in armes}
        utiles = set().union(*par_arme.values())
        print("[accessoires] %-5s : %.1f accessoires possibles en moyenne par arme à emplacements (%d armes) ; "
              "%d accessoires sur %d ne vont sur aucune arme"
              % (quand, sum(len(par_arme[a]) for a in equipables) / len(equipables), len(equipables),
                 len(set(gp.type_acc) - utiles), len(gp.type_acc)))
        for t in list(LISTES) + ["extended_mag"]:
            avec = [a for a in equipables if t in detail[a][0]]
            if avec:
                print("      %-12s %3d armes ont l'emplacement, %.1f possibles en moyenne"
                      % (LISTES.get(t, "chargeurs"), len(avec),
                         sum(sum(1 for x in par_arme[a] if gp.type_acc[x] == t) for a in avec) / len(avec)))
        for a in sorted(set(gp.type_acc) - utiles):
            print("      sur aucune arme : %s (%s)" % (a, gp.type_acc[a]))
    print("[accessoires] listes communes : " + ", ".join("%s %d" % (LISTES[t], len(communes[t])) for t in LISTES))
    for arme in armes:
        types, os_ = detail[arme]
        recus = [LISTES[t] for t in LISTES if t in types and t + "_pos" in os_]
        sans_os = [t for t in LISTES if t in types and t + "_pos" not in os_]
        fermes = [t for t in LISTES if t not in types and t + "_pos" in os_]
        print("   %-42s %s%s%s" % (
            arme, ", ".join(recus) or "-",
            ("  | emplacement sans os (accessoire invisible) : " + ", ".join(sans_os)) if sans_os else "",
            ("  | os sans emplacement : " + ", ".join(fermes)) if fermes else ""))


# ------------------------------------------------------------------ fichiers retirés (serveur/tacz_retires.txt)
def lignes_retires():
    """Chemins de notre gunpack listés dans serveur/tacz_retires.txt (relatifs au gunpack)."""
    if not RETIRES.exists():
        return set()
    debut = PACK + "/"
    return {l.strip()[len(debut):] for l in RETIRES.read_text(encoding="utf-8").splitlines()
            if l.strip().startswith(debut)}


def noter_retires(enleves, revenus):
    """La mise en ligne copie serveur/tacz sans rien effacer : un fichier retiré d'ici doit être listé dans
    tacz_retires.txt pour disparaître du Ptero ; un fichier refait ne doit plus y être (effacé après la copie)."""
    if not enleves and not revenus:
        return
    lignes = RETIRES.read_text(encoding="utf-8").splitlines() if RETIRES.exists() else []
    lignes = [l for l in lignes if l.strip() not in {PACK + "/" + c for c in revenus}]
    nouveaux = [PACK + "/" + c for c in sorted(enleves) if PACK + "/" + c not in {l.strip() for l in lignes}]
    if nouveaux:
        if ENTETE_RETIRES not in lignes:
            lignes += ["", ENTETE_RETIRES]
        i = lignes.index(ENTETE_RETIRES) + 1
        while i < len(lignes) and lignes[i].strip():
            i += 1
        lignes[i:i] = nouveaux
    RETIRES.write_bytes(("\n".join(lignes) + "\n").encode("utf-8"))


def controle_exclus(gp):
    """EXCLUS sans effet : arme inconnue, ou accessoire déjà donné par la liste d'origine de l'arme."""
    out = []
    for a, (_, armes) in sorted(EXCLUS.items()):
        if a not in gp.type_acc:
            out.append("EXCLUS : accessoire inconnu %s" % a)
            continue
        for arme in sorted(gp.armes) if armes == "*" else armes:
            if arme not in gp.armes:
                out.append("EXCLUS : arme inconnue %s (pour %s)" % (arme, a))
            elif a in resoudre(gp.listes_armes.get(arme, []), gp.listes) and gp.type_acc[a] in gp.types_arme(arme):
                out.append("EXCLUS sans effet : %s reçoit déjà %s par sa liste d'origine (à retirer de son gunpack "
                           "ou à accepter dans outils/verif_accessoires.py)" % (arme, a))
    return out


# ------------------------------------------------------------------ principal
def main():
    verifier = "--verifier" in sys.argv[1:]
    gp = Gunpacks()
    communes, ajouts, detail = calculer(gp)
    voulus = fichiers_voulus(communes, ajouts)
    presents = {str(f.relative_to(SORTIE)).replace("\\", "/") for f in SORTIE.rglob("*") if f.is_file()} \
        if SORTIE.exists() else set()
    problemes, ecrits = 0, 0
    for alerte in controle_exclus(gp):
        print("[accessoires] " + alerte)
        problemes += 1
    retires = lignes_retires()
    for chemin in sorted(set(voulus) & retires):
        if verifier:
            print("[accessoires] %s/%s : dans serveur/tacz_retires.txt (la mise en ligne l'effacerait du Ptero)"
                  % (PACK, chemin))
            problemes += 1
    for chemin, texte in sorted(voulus.items()):
        f = SORTIE / chemin
        actuel = f.read_text(encoding="utf-8") if f.exists() else None
        if actuel == texte:
            continue
        if verifier:
            print("[accessoires] %s : %s" % ((SORTIE / chemin).relative_to(DEPOT),
                                             "manquant" if actuel is None else "pas à jour"))
            problemes += 1
            continue
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_bytes(texte.encode("utf-8"))
        json.loads(f.read_text(encoding="utf-8"))
        ecrits += 1
    for chemin in sorted(presents - set(voulus)):
        if verifier:
            print("[accessoires] %s : en trop" % (SORTIE / chemin).relative_to(DEPOT))
            problemes += 1
        else:
            (SORTIE / chemin).unlink()
            ecrits += 1
    if not verifier:
        noter_retires(presents - set(voulus), set(voulus) & retires)
        for d in sorted((p for p in SORTIE.rglob("*") if p.is_dir()), key=lambda p: -len(p.parts)):
            if not any(d.iterdir()):
                d.rmdir()
    if "--rapport" in sys.argv[1:]:
        for a in gp.alertes:
            print("[accessoires] attention : " + a)
        rapport(gp, communes, ajouts, detail)
    print("[accessoires] %d armes (%d à part), %d accessoires ; %d armes reçoivent les listes communes ; "
          "%s ; %d problème(s)"
          % (len(gp.armes), len(gp.armes) - len(detail), len(gp.type_acc), len(ajouts),
             "contrôle seul" if verifier else "%d fichier(s) écrit(s) ou retiré(s)" % ecrits, problemes))
    sys.exit(1 if problemes else 0)


if __name__ == "__main__":
    main()
