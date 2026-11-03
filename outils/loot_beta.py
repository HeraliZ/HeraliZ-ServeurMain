"""Réglage « bêta » du loot (03/11) : ce qu'il change, palier par palier, avant / après. Ne modifie rien.

Le réglage est la section [beta] de serveur/defaultconfigs/heraliz_loot-server.toml (lue par le serveur :
fr.heraliz.loot.LootBeta, appliquée au moment du tirage). Cet outil refait le même calcul que le serveur sur les vraies
tables (serveur/config/heraliz_loot/coffres/) et les vraies raretés (serveur/config/heraliz_loot/raretes.json).

    python outils/loot_beta.py                         avec les valeurs du fichier de réglages
    python outils/loot_beta.py --quantite 2 --raretes 2   pour essayer d'autres valeurs (rien n'est écrit)
    python outils/loot_beta.py --markdown              tableau à coller dans une fiche

Règles (les mêmes que LootBeta.java, à garder ensemble) :
- quantité : le nombre d'objets d'un conteneur (ou d'un zombie : 1 tirage) est multiplié, arrondi au hasard (1,5 x 3 =
  4 ou 5, 4,5 en moyenne), jamais au-delà de « objets_max » (sauf si la table en donnait déjà plus) ;
- raretés : le poids des objets Rare, Épique et Légendaire (et Peu commun si « chances_peu_commun » > 1) est multiplié ;
  la place prise vient des autres objets (Commun…), dont le poids baisse d'autant : le total d'une table ne change pas,
  donc la case vide (« rien » des zombies) garde sa chance par tirage ;
- exclus (Unique, objets à vie, liste « exclus » : couteaux, heralisz_skins:*) : poids divisé par la hausse du nombre
  d'objets de la table, pour garder leur chance PAR CONTENEUR d'aujourd'hui (leur place va aux objets ordinaires) ;
- plafonds : une rareté ne passe jamais devant celle d'en dessous (Épique <= Rare, Légendaire <= Épique), et les autres
  objets gardent au moins une part de leur place (1 / hausse du nombre d'objets de la table, entre 50 % et 90 %) :
  aucun objet ordinaire n'est plus rare qu'avant dans un conteneur ;
- fragments de cartes d'accès des zombies (tirés à part) : chance x le multiplicateur de leur rareté (vert Rare, bleu
  Épique, rouge Légendaire), plafond par jour inchangé.
"""
import json
import re
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parent.parent
TOML = RACINE / "serveur/defaultconfigs/heraliz_loot-server.toml"
COFFRES = RACINE / "serveur/config/heraliz_loot/coffres"
RARETES = json.loads((RACINE / "serveur/config/heraliz_loot/raretes.json").read_text(encoding="utf-8"))
ZOMBIES = json.loads((RACINE / "serveur/config/heraliz_loot/zombies.json").read_text(encoding="utf-8"))
FRAGMENT = "heralisz_bunker:keycard_fragment_"
SORTES = [("armes", "GunId"), ("accessoires", "AttachmentId"), ("munitions", "AmmoId"), ("soins", "ConsumableId")]
PALIERS = ["commun", "peu_commun", "rare", "epique", "legendaire"]
HAUTES = ["rare", "epique", "legendaire"]
NOMS = {"peu_commun": "Peu commun", "rare": "Rare", "epique": "Épique", "legendaire": "Légendaire"}
TAILLE = 27  # cases d'un conteneur


def reglages():
    """Section [beta] du fichier de réglages (valeurs par défaut de ConfigLoot si absente)."""
    r = {"actif": True, "quantite": 1.5, "chances_raretes_hautes": 1.5, "chances_peu_commun": 1.0, "objets_max": 20,
         "exclus": ["heralisz_skins:*"]}
    texte = TOML.read_text(encoding="utf-8")
    m = re.search(r"(?ms)^\[beta\]\s*$(.*?)(?=^\[|\Z)", texte)
    if m:
        for ligne in m.group(1).splitlines():
            ligne = ligne.strip()
            if not ligne or ligne.startswith("#") or "=" not in ligne:
                continue
            k, v = (x.strip() for x in ligne.split("=", 1))
            if v in ("true", "false"):
                r[k] = v == "true"
            elif v.startswith("["):
                r[k] = re.findall(r'"([^"]*)"', v)
            else:
                r[k] = float(v)
    return r


def bornes(r):
    """Mêmes bornes que ConfigLoot."""
    return dict(r, quantite=min(3.0, max(1.0, r["quantite"])),
                chances_raretes_hautes=min(3.0, max(1.0, r["chances_raretes_hautes"])),
                chances_peu_commun=min(3.0, max(1.0, r["chances_peu_commun"])), objets_max=int(min(TAILLE, max(1, r["objets_max"]))))


def rarete(e):
    """Comme RaretesObjets.de : rareté inscrite, sinon celle de l'arme / l'accessoire / la munition / le soin, sinon de l'objet."""
    if e.get("rarete"):
        return "unique" if e["rarete"] == "mythique" else e["rarete"]
    nbt = e.get("nbt") or ""
    for s, et in SORTES:
        m = re.search(et + r':"([^"]+)"', nbt)
        if m:
            return RARETES[s].get(m.group(1), RARETES.get("defaut"))
    if e["item"] == "tacz:ammo_box":
        m = re.search(r"Level:(\d+)", nbt)
        x = RARETES["objets"].get("tacz:ammo_box/" + (m.group(1) if m else "0"))
        if x:
            return x
    return RARETES["objets"].get(e["item"], RARETES.get("defaut"))


def cles(e):
    """Identifiants comparés à la liste « exclus » : l'objet, et l'arme / l'accessoire / la munition / le soin."""
    out = [e["item"]]
    for _, et in SORTES:
        m = re.search(et + r':"([^"]+)"', e.get("nbt") or "")
        if m:
            out.append(m.group(1))
    return out


def exclu(e, motifs):
    if rarete(e) == "unique":
        return True
    for k in cles(e):
        for m in motifs:
            if k == m or (m.endswith("*") and k.startswith(m[:-1])):
                return True
    return False


def multiplicateurs(r):
    m = {x: 1.0 for x in PALIERS}
    if r["actif"]:
        m["peu_commun"] = r["chances_peu_commun"]
        for x in HAUTES:
            m[x] = r["chances_raretes_hautes"]
    return m


def plancher(fq):
    """Part minimale de leur place gardée par les objets non boostés d'une table (voir l'en-tête)."""
    return max(0.5, min(0.9, 1.0 / fq))


def facteur_quantite(table, r):
    """Objets par conteneur après / avant (LootBeta.facteurQuantite)."""
    avant = objets_moyens(table, dict(r, actif=False))
    return objets_moyens(table, r) / avant if avant > 0 else r["quantite"]


def poids_beta(table, r):
    """Poids de chaque entrée avec le réglage (même calcul que LootBeta.calculer)."""
    objets = table["objets"]
    w = [float(e.get("poids", 10)) for e in objets]
    if not r["actif"]:
        return w
    m = multiplicateurs(r)
    classe = []
    h = {x: 0.0 for x in PALIERS}
    autres = exclus = 0.0
    for e, p in zip(objets, w):
        if e["item"] == "minecraft:air":
            classe.append(None)
            continue
        if exclu(e, r["exclus"]):
            classe.append("exclu")
            exclus += p
            continue
        ra = rarete(e)
        if ra in m and m[ra] > 1:
            classe.append(ra)
            h[ra] += p
        else:
            classe.append("autre")
            autres += p
    apres = {}
    for i, x in enumerate(PALIERS):
        if h[x] <= 0 or m[x] <= 1:
            continue
        t = h[x] * m[x]
        if x in ("epique", "legendaire"):
            dessous = PALIERS[i - 1]
            t = min(t, max(h[x], apres.get(dessous, h[dessous])))
        apres[x] = t
    fq = facteur_quantite(table, r)
    rendu = exclus - exclus / fq if exclus > 0 and fq > 1 else 0.0
    if not (apres or rendu > 0) or autres <= 0:
        return w
    extra = sum(apres[x] - h[x] for x in apres)
    lim = autres * (1 - plancher(fq))
    if extra > lim:
        k = lim / extra
        for x in apres:
            apres[x] = h[x] + (apres[x] - h[x]) * k
        extra = lim
    s = (autres - extra + rendu) / autres
    return [p if c is None else p * s if c == "autre" else p / fq if c == "exclu" else p * apres[c] / h[c]
            for c, p in zip(classe, w)]


def objets_moyens(table, r):
    """Objets tirés en moyenne par conteneur (cases_poids, sinon au hasard entre min et max)."""
    if "cases_poids" in table:
        d = {int(k): v for k, v in table["cases_poids"].items()}
    else:
        a, b = table["emplacements"]
        d = {n: 1 for n in range(a, b + 1)}
    tot = sum(d.values())
    q = r["quantite"] if r["actif"] else 1.0
    return sum(v * nombre_moyen(n, q, r["objets_max"]) for n, v in d.items()) / tot


def nombre_moyen(n, q, cap):
    """Moyenne de LootBeta.nombre(n) : n x q arrondi au hasard, plafonné à cap (ou n s'il était déjà plus grand)."""
    if q <= 1:
        return n
    x = n * q
    return x if x <= max(n, cap) else max(n, cap)


def parts(objets, w, motifs):
    tot = sum(w)
    p = {x: 0.0 for x in HAUTES + ["peu_commun", "commun", "fragments", "vide", "exclus"]}
    for e, x in zip(objets, w):
        if e["item"] == "minecraft:air":
            p["vide"] += x / tot
            continue
        ra = rarete(e)
        if ra in p:
            p[ra] += x / tot
        if exclu(e, motifs):
            p["exclus"] += x / tot
        if e["item"].startswith(FRAGMENT):
            p["fragments"] += x / tot
    return p


def ligne(nom, table, r0, r1, zombie=False):
    objs = table["objets"]
    w0, w1 = poids_beta(table, r0), poids_beta(table, r1)
    p0, p1 = parts(objs, w0, r1["exclus"]), parts(objs, w1, r1["exclus"])
    if zombie:
        n0, n1 = 1.0, nombre_moyen(1, r1["quantite"] if r1["actif"] else 1, 3)
    else:
        n0, n1 = objets_moyens(table, r0), objets_moyens(table, r1)
    # objets réels (la case vide ne donne rien) ; les fragments sont comptés pour 100 conteneurs / zombies
    o0, o1 = n0 * (1 - p0["vide"]), n1 * (1 - p1["vide"])
    return nom, o0, o1, p0, p1, n0 * p0["fragments"] * 100, n1 * p1["fragments"] * 100, n0, n1


def fragments_zombies(r):
    m = multiplicateurs(r)
    out = {}
    for c, ra in (("green", "rare"), ("blue", "epique"), ("red", "legendaire")):
        out[c] = min(1.0, ZOMBIES["fragments"][c] * m[ra])
    return out


def main():
    r1 = reglages()
    args = sys.argv[1:]
    for k, cle in (("--quantite", "quantite"), ("--raretes", "chances_raretes_hautes"), ("--peu-commun", "chances_peu_commun")):
        if k in args:
            r1[cle] = float(args[args.index(k) + 1])
            r1["actif"] = True
    r1 = bornes(r1)
    r0 = dict(r1, actif=False)
    md = "--markdown" in args
    print(f"Réglage : {'actif' if r1['actif'] else 'COUPÉ'} ; quantité x{r1['quantite']:g} ; Rare / Épique / Légendaire "
          f"x{r1['chances_raretes_hautes']:g} ; Peu commun x{r1['chances_peu_commun']:g} ; au plus {r1['objets_max']} objets "
          f"par conteneur ; exclus : Unique, objets à vie, {', '.join(r1['exclus']) or 'rien'}")
    lignes = []
    for t in range(1, 8):
        lignes.append(ligne(f"T{t}", json.loads((COFFRES / f"t{t}.json").read_text(encoding="utf-8")), r0, r1))
    for nom in ("soins3", "munitions"):
        lignes.append(ligne(nom, json.loads((COFFRES / f"{nom}.json").read_text(encoding="utf-8")), r0, r1))
    for nom in ("zombies", "zombies_militaire"):
        lignes.append(ligne(nom, json.loads((COFFRES / f"{nom}.json").read_text(encoding="utf-8")), r0, r1, zombie=True))

    def pc(x):
        return f"{x * 100:.2f}".rstrip("0").rstrip(".").replace(".", ",") + " %"

    def nb(x):
        return f"{x:.2f}".replace(".", ",")

    entete = ("Table", "Objets (avant → après)", "Rare", "Épique", "Légendaire", "Rares+ par conteneur", "Fragments pour 100",
              "Communs par conteneur", "Exclus pour 100")
    if md:
        print("| " + " | ".join(entete) + " |")
        print("|" + "---|" * len(entete))
    else:
        print("  ".join(entete))
    for nom, o0, o1, p0, p1, f0, f1, n0, n1 in lignes:
        hautes0 = o0 * sum(p0[x] for x in HAUTES) / max(1e-9, 1 - p0["vide"])
        hautes1 = o1 * sum(p1[x] for x in HAUTES) / max(1e-9, 1 - p1["vide"])
        cols = [nom, f"{nb(o0)} → {nb(o1)}"] + [f"{pc(p0[x])} → {pc(p1[x])}" for x in HAUTES] + \
               [f"{nb(hautes0)} → {nb(hautes1)} (x{hautes1 / hautes0:.2f})".replace(".", ","), f"{nb(f0)} → {nb(f1)}",
                f"{nb(n0 * p0['commun'])} → {nb(n1 * p1['commun'])}", f"{nb(n0 * p0['exclus'] * 100)} → {nb(n1 * p1['exclus'] * 100)}"]
        print(("| " + " | ".join(cols) + " |") if md else "  ".join(cols))
    f0, f1 = fragments_zombies(r0), fragments_zombies(r1)
    print("Fragments des zombies (chance par zombie compté) : " + ", ".join(
        f"{c} {pc(f0[c])} → {pc(f1[c])}" for c in f0) + f" ; plafond {ZOMBIES['fragments_max_jour']} par joueur et par jour inchangé")


if __name__ == "__main__":
    main()
