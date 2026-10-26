"""Zones où l'on ne construit pas (bases des clans sur la carte principale) : calcul et carte à faire valider.
LECTURE SEULE : on lit une COPIE du pack de données de la carte (zones de loot par lieu) et des zones à effets.

    python outils/zones_construction.py [options]

Règles (demande du propriétaire du 21/10, façon Rust) :
  - grandes zones (villes, aérodrome, port, prison, silo, radio, gaz, arctique…) : grand cercle, l'étendue du lieu plus
    MARGE_GRANDE blocs ;
  - petits lieux nommés (bunker, jetée, faille, pétrole, halte) : l'étendue plus MARGE_PETIT ;
  - villages (v_0, v_1…) : l'étendue plus MARGE_VILLAGE ;
  - maisons isolées (campagne, routes) : les conteneurs sont regroupés en maisons (à moins de MAISON blocs les uns des
    autres), cercle de la maison plus MARGE_MAISON ;
  - zones à effets (gaz, radiation, froid de HeraliZ Décors) : leur contour, bord compris, plus MARGE_EFFET ;
  - tout le haut de la carte (la neige) : un rectangle du bord nord jusqu'au bas de la zone « froid », plus MARGE_EFFET ;
  - la voie du train : RAIL blocs de chaque côté des rails ; la plateforme pétrolière : RAYON_PETROLE blocs au moins.

Options :
  --pack <dossier>     zones de loot du pack de données de la carte (…/data/heraliz_loot/zones) ; défaut : la copie v3
                       de monde/HeraliZ
  --effets <json>      data/heraliz_zones.json du monde (zones à effets) ; défaut : celui de la copie v3
  --fond <dossier>     fond de la carte du monde (carte.json et tuiles) ; défaut : celui du mod HeraliZ Utils
  --reseau <json>      voie du train (défaut : train/reseau_v2.json de HeraliZ Utils)
  --sortie <dossier>   où écrire carte_zones_construction.png, zones_construction.json et rapport.md
                       (défaut : livraison/zones_construction)

Besoin : numpy et Pillow.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

DEPOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(DEPOT / "outils"))
import extractions_carte as ec  # noqa: E402  (fond, polices, grille)

PACK_V3 = DEPOT / "monde/HeraliZ/datapacks/heraliz_carte/data/heraliz_loot/zones"
EFFETS_V3 = DEPOT / "monde/HeraliZ/data/heraliz_zones.json"

GRANDES = {"valmont", "tcherno", "marbois", "belcourt", "port", "mirage", "clairval", "gaz", "aerodrome", "silo",
           "prison", "arctique", "radio"}
MAISONS = {"campagne", "routes"}
# marges réduites le 21/10 (« laisser plus de zone dispo pour build ») : 150 / 60 / 60 / 30 / 40 avant ; depuis la
# version 3 (21/10), formes qui suivent les bâtiments : est interdit tout point à moins de la marge du conteneur de loot le
# plus proche du lieu (plus de grands cercles : une île ou un champ sans loot au bord d'une ville reste constructible)
MARGE_GRANDE = 80
MARGE_PETIT = 40
MARGE_VILLAGE = 40
MARGE_MAISON = 25
MAISON = 16
MARGE_EFFET = 25
# plateforme pétrolière (lieu « petrole », au sud) : la plateforme dépasse de loin ses quelques conteneurs
RAYON_PETROLE = 150
# voie du train (réseau de la nouvelle carte, HeraliZ Utils) : bande interdite de part et d'autre des rails
RAIL = 20
RESEAU = DEPOT / "modules/monde/src/commun/resources/assets/heralisz_utils/train/reseau_v2.json"

COULEUR = {"grande": (230, 60, 60), "petit": (240, 140, 40), "village": (240, 200, 60), "maison": (200, 120, 230),
           "effet": (90, 200, 230), "neige": (210, 230, 255), "rail": (235, 235, 235)}


def lire_lieux(pack):
    lieux = {}
    for d in sorted(Path(pack).iterdir()):
        pts = []
        for f in sorted(d.glob("*.json")):
            pts += [(c[0], c[2]) for c in json.loads(f.read_text(encoding="utf-8")).get("conteneurs", [])]
        if pts:
            lieux[d.name] = pts
    return lieux


def cercle(pts, marge):
    cx = sum(p[0] for p in pts) / len(pts)
    cz = sum(p[1] for p in pts) / len(pts)
    r = max(math.hypot(p[0] - cx, p[1] - cz) for p in pts)
    return round(cx), round(cz), round(r + marge)


def maisons(pts):
    """Regroupe des conteneurs en maisons : chaînes de conteneurs à moins de MAISON blocs les uns des autres."""
    reste = list(pts)
    groupes = []
    while reste:
        g = [reste.pop()]
        i = 0
        while i < len(g):
            x, z = g[i]
            proches = [p for p in reste if abs(p[0] - x) <= MAISON and abs(p[1] - z) <= MAISON]
            for p in proches:
                reste.remove(p)
            g += proches
            i += 1
        groupes.append(g)
    return groupes


def reduire(pts, pas=4):
    """Conteneurs d'un lieu, un seul par case de pas blocs (assez fin pour la forme, bien plus léger)."""
    vus = {}
    for x, z in pts:
        vus.setdefault((x // pas, z // pas), [x // pas * pas + pas // 2, z // pas * pas + pas // 2])
    return sorted(vus.values())


def calculer(lieux, effets):
    cercles = []
    for nom, pts in lieux.items():
        if nom in MAISONS:
            for k, g in enumerate(maisons(pts)):
                x, z, r = cercle(g, MARGE_MAISON)
                cercles.append({"nom": "%s_%d" % (nom, k), "genre": "maison", "x": x, "z": z, "rayon": r,
                                "marge": MARGE_MAISON, "points": reduire(g)})
        else:
            genre = "grande" if nom in GRANDES else "village" if nom.startswith("v_") else "petit"
            marge = {"grande": MARGE_GRANDE, "village": MARGE_VILLAGE, "petit": MARGE_PETIT}[genre]
            x, z, r = cercle(pts, marge)
            if nom == "petrole":
                # la plateforme dépasse de loin ses quelques conteneurs : un vrai cercle
                cercles.append({"nom": nom, "genre": genre, "x": x, "z": z, "rayon": max(r, RAYON_PETROLE),
                                "marge": RAYON_PETROLE, "points": [[x, z]]})
            else:
                cercles.append({"nom": nom, "genre": genre, "x": x, "z": z, "rayon": r, "marge": marge,
                                "points": reduire(pts)})
    polys = []
    nord = None
    for e in effets.get("zones", []):
        polys.append({"nom": e["nom"], "genre": e["genre"], "points": e["points"],
                      "marge": int(e.get("bord", 0)) + MARGE_EFFET})
        if e["genre"] == "froid":
            bas = max(p[1] for p in e["points"]) + int(e.get("bord", 0)) + MARGE_EFFET
            nord = bas if nord is None else max(nord, bas)
    return cercles, polys, nord


def legende(img, lignes):
    """Légende en haut à droite (sur la neige, déjà interdite) : en bas, elle cachait la plateforme pétrolière."""
    dr = ImageDraw.Draw(img, "RGBA")
    f = ec.police(13, fine=True)
    h = 10 + 18 * len(lignes)
    w = 10 + max(dr.textbbox((0, 0), t, font=f)[2] for t, _ in lignes) + 26
    x0, y0 = img.width - w - 12, 30
    dr.rounded_rectangle([x0, y0, x0 + w, y0 + h], 8, fill=(10, 12, 16, 210), outline=(255, 255, 255, 70))
    for i, (t, c) in enumerate(lignes):
        y = y0 + 8 + 18 * i
        if c:
            dr.ellipse([x0 + 8, y + 3, x0 + 18, y + 13], fill=c + (255,), outline=(0, 0, 0, 255))
        dr.text((x0 + 26, y), t, font=f, fill=(235, 235, 235))


def lire_rails(chemin):
    """Voie du train : suite de points (x, z), un par bloc ; « x » dans genre = trou dans la voie (pas de rails)."""
    if not Path(chemin).is_file():
        return []
    d = json.loads(Path(chemin).read_text(encoding="utf-8"))
    p = d["points"]
    pts = [(p[i], p[i + 2]) for i in range(0, len(p), 3)]
    return pts[::4] + pts[-1:]


def couche_lieux(x0, z0, w, h, pas, cercles, genres=None):
    """Interdit à moins de la marge d'un conteneur de chaque lieu (disques autour de chaque conteneur réunis)."""
    img = Image.new("L", (w, h), 0)
    dr = ImageDraw.Draw(img)
    for c in cercles:
        if genres is not None and c["genre"] not in genres:
            continue
        r = c["marge"] / pas
        for x, z in c["points"]:
            X, Y = (x - x0) / pas, (z - z0) / pas
            dr.ellipse([X - r, Y - r, X + r, Y + r], fill=255)
    return np.array(img) > 0


def masque(x0, z0, w, h, pas, cercles, polys, nord, rails=()):
    """Grille de cases (pas blocs) : vrai là où l'on ne construit pas."""
    xs = x0 + pas * (np.arange(w) + 0.5)
    zs = z0 + pas * (np.arange(h) + 0.5)
    X, Z = np.meshgrid(xs, zs)
    m = couche_lieux(x0, z0, w, h, pas, cercles)
    for p in polys:
        img = Image.new("L", (w, h), 0)
        pts = [((x - x0) / pas, (z - z0) / pas) for x, z in p["points"]]
        dr = ImageDraw.Draw(img)
        dr.polygon(pts, fill=255)
        r = p["marge"] / pas
        dr.line(pts + [pts[0]], fill=255, width=max(1, int(2 * r)), joint="curve")
        for q in pts:
            dr.ellipse([q[0] - r, q[1] - r, q[0] + r, q[1] + r], fill=255)
        m |= np.array(img) > 0
    if nord is not None:
        m |= Z <= nord
    if rails:
        img = Image.new("L", (w, h), 0)
        dr = ImageDraw.Draw(img)
        pts = [((x - x0) / pas, (z - z0) / pas) for x, z in rails]
        dr.line(pts + pts[:1], fill=255, width=max(1, int(2 * RAIL / pas)), joint="curve")
        m |= np.array(img) > 0
    return m


def main(argv):
    o = {"pack": str(PACK_V3), "effets": str(EFFETS_V3), "fond": str(ec.FOND_MOD), "reseau": str(RESEAU),
         "sortie": str(DEPOT / "livraison/zones_construction")}
    i = 0
    while i < len(argv):
        k = argv[i].lstrip("-")
        if k not in o:
            sys.exit("Option inconnue : %s" % argv[i])
        o[k] = argv[i + 1]
        i += 2
    sortie = Path(o["sortie"])
    sortie.mkdir(parents=True, exist_ok=True)
    lieux = lire_lieux(o["pack"])
    effets = json.loads(Path(o["effets"]).read_text(encoding="utf-8")) if Path(o["effets"]).is_file() else {"zones": []}
    cercles, polys, nord = calculer(lieux, effets)
    rails = lire_rails(o["reseau"])

    fond = ec.Fond(o["fond"])
    b = fond.info.get("bordure", [-1500, -1500, 1500, 1500]) if fond.ok else [-1500, -1500, 1500, 1500]
    x0, z0, x1, z1 = b
    pas = 4
    w, h = (x1 - x0) // pas, (z1 - z0) // pas
    m = masque(x0, z0, w, h, pas, cercles, polys, nord, rails)
    part = 100.0 * m.mean()
    terre = None
    if fond.ok:
        # terre = tout ce qui n'est pas de l'eau sur le fond satellite (eau : le bleu domine nettement)
        sat0 = fond.vues.get("satellite") or next(iter(fond.vues.values()))
        k0 = fond.bpp
        a = np.asarray(sat0.crop(((x0 - fond.x0) // k0, (z0 - fond.z0) // k0, (x1 - fond.x0) // k0, (z1 - fond.z0) // k0))
                       .resize((w, h), Image.NEAREST)).astype(int)
        terre = ~((a[..., 2] > a[..., 0] + 25) & (a[..., 2] > a[..., 1] + 5))
        part_terre = 100.0 * (m & terre).sum() / max(1, terre.sum())
        libre_km2 = (terre & ~m).sum() * pas * pas / 1e6

    v = ec.Vue(x0, z0, x1 - x0, z1 - z0)
    if fond.ok:
        sat = fond.vues.get("satellite") or next(iter(fond.vues.values()))
        k = fond.bpp
        img = sat.crop(((x0 - fond.x0) // k, (z0 - fond.z0) // k, (x1 - fond.x0) // k, (z1 - fond.z0) // k))
        img = img.resize((v.w, v.h), Image.LANCZOS).convert("RGBA")
    else:
        img = Image.new("RGBA", (v.w, v.h), ec.VIDE + (255,))
    # voile rouge sur tout ce qui est interdit, puis contours par genre
    voile = Image.fromarray((m * 255).astype(np.uint8)).resize((v.w, v.h), Image.NEAREST)
    rouge = Image.new("RGBA", (v.w, v.h), (200, 30, 30, 110))
    img = Image.composite(Image.alpha_composite(img, rouge), img, voile)
    dr = ImageDraw.Draw(img, "RGBA")
    if nord is not None:
        X0, Y0 = v.p(x0, z0)
        X1, Y1 = v.p(x1, nord)
        dr.rectangle([X0, Y0, X1, Y1], outline=COULEUR["neige"] + (255,), width=3)
    for p in polys:
        dr.polygon([v.p(x, z) for x, z in p["points"]], outline=COULEUR["effet"] + (255,), width=2)
    if rails:
        dr.line([v.p(x, z) for x, z in rails + rails[:1]], fill=COULEUR["rail"] + (200,), width=1)
    # contour de chaque genre de lieu (bord de sa couche), de sa couleur
    for genre in ("maison", "village", "petit", "grande"):
        c_ = couche_lieux(x0, z0, w, h, pas, cercles, {genre})
        e = c_.copy()
        e[1:, :] &= c_[:-1, :]
        e[:-1, :] &= c_[1:, :]
        e[:, 1:] &= c_[:, :-1]
        e[:, :-1] &= c_[:, 1:]
        bord = Image.fromarray(((c_ & ~e) * 255).astype(np.uint8)).resize((v.w, v.h), Image.NEAREST)
        img.paste(Image.new("RGBA", (v.w, v.h), COULEUR[genre] + (255,)), (0, 0), bord)
    ec.grille(img, v, x0, z0)
    f = ec.police(15)
    for c in cercles:
        if c["genre"] in ("grande", "petit", "village"):
            X, Y = v.p(c["x"], c["z"])
            nom = "plateforme pétrolière" if c["nom"] == "petrole" else c["nom"].replace("v_", "village ")
            ec.texte(dr, (X, Y), nom, f, (255, 255, 255), True)
    tete = ("Terre interdite : %.0f %% (constructible : %.2f km²)" % (part_terre, libre_km2)) if terre is not None \
        else "Interdit de construire : %.0f %% de la carte" % part
    legende(img, [(tete, None),
                     ("Grandes zones : %d blocs autour du loot" % MARGE_GRANDE, COULEUR["grande"]),
                     ("Petits lieux : %d blocs autour du loot" % MARGE_PETIT, COULEUR["petit"]),
                     ("Villages : %d blocs autour du loot" % MARGE_VILLAGE, COULEUR["village"]),
                     ("Maisons isolées : %d blocs autour du loot" % MARGE_MAISON, COULEUR["maison"]),
                     ("Gaz, radiation, froid : zone + bord + %d" % MARGE_EFFET, COULEUR["effet"]),
                     ("Neige : tout le nord jusqu'à z = %s" % (nord if nord is not None else "-"), COULEUR["neige"]),
                     ("Rails du train : %d blocs de chaque côté" % RAIL, COULEUR["rail"]),
                     ("Plateforme pétrolière : rayon %d" % RAYON_PETROLE, COULEUR["petit"])])
    ec.enregistrer(img, sortie / "carte_zones_construction.png")

    # masque pour le mod (1 point = pas blocs, blanc = interdit), depuis le coin nord-ouest de la bordure
    Image.fromarray((m * 255).astype(np.uint8)).save(sortie / "zones_construction_masque.png", optimize=True)
    cercles_json = [{k: c[k] for k in ("nom", "genre", "x", "z", "rayon", "marge")} for c in cercles]
    donnees = {"version": 2, "masque": "zones_construction_masque.png", "x0": x0, "z0": z0, "pas": pas, "nord_z": nord, "rail": RAIL, "rails": [[round(x), round(z)] for x, z in rails], "lieux": cercles_json,
               "polygones": [{"nom": p["nom"], "genre": p["genre"], "points": p["points"], "marge": p["marge"]} for p in polys]}
    (sortie / "zones_construction.json").write_text(json.dumps(donnees, ensure_ascii=False, indent=1), encoding="utf-8")
    compte = {g: sum(1 for c in cercles if c["genre"] == g) for g in COULEUR}
    r = ["# Zones où l'on ne construit pas", "",
         "Bordure : %s. Interdit : %.0f %% de la carte (mer comprise)%s." % (b, part, "" if terre is None else
         " ; **terre interdite : %.0f %%**, terre constructible : %.2f km² (%.0f %%)" % (part_terre, libre_km2, 100 - part_terre)), "",
         "| Genre | Nombre | Marge |", "|---|---|---|",
         "| Grandes zones | %d | %d blocs |" % (compte["grande"], MARGE_GRANDE),
         "| Petits lieux | %d | %d blocs |" % (compte["petit"], MARGE_PETIT),
         "| Villages | %d | %d blocs |" % (compte["village"], MARGE_VILLAGE),
         "| Maisons isolées | %d | %d blocs |" % (compte["maison"], MARGE_MAISON),
         "| Zones à effets | %d | bord + %d blocs |" % (len(polys), MARGE_EFFET),
         "| Neige (nord) | 1 | jusqu'à z = %s |" % nord,
         "| Rails du train | %d points | %d blocs de chaque côté |" % (len(rails), RAIL), ""]
    for c in sorted(cercles, key=lambda c: (c["genre"], c["nom"])):
        if c["genre"] != "maison":
            r.append("- %s (%s) : centre %d %d, étendue %d, marge %d autour de chaque conteneur"
                     % (c["nom"], c["genre"], c["x"], c["z"], c["rayon"] - c["marge"], c["marge"]))
    (sortie / "rapport.md").write_text("\n".join(r) + "\n", encoding="utf-8")
    print("[zones_construction] %d cercles, %d zones à effets, nord z <= %s ; interdit %.1f %% de la carte%s"
          % (len(cercles), len(polys), nord, part, "" if terre is None else ", %.1f %% de la terre" % part_terre))


if __name__ == "__main__":
    main(sys.argv[1:])
