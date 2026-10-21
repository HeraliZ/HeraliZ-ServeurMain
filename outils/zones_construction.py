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
  - tout le haut de la carte (la neige) : un rectangle du bord nord jusqu'au bas de la zone « froid », plus MARGE_EFFET.

Options :
  --pack <dossier>     zones de loot du pack de données de la carte (…/data/heraliz_loot/zones) ; défaut : la copie v3
                       de monde/HeraliZ
  --effets <json>      data/heraliz_zones.json du monde (zones à effets) ; défaut : celui de la copie v3
  --fond <dossier>     fond de la carte du monde (carte.json et tuiles) ; défaut : celui du mod HeraliZ Utils
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
MARGE_GRANDE = 150
MARGE_PETIT = 60
MARGE_VILLAGE = 60
MARGE_MAISON = 30
MAISON = 16
MARGE_EFFET = 40

COULEUR = {"grande": (230, 60, 60), "petit": (240, 140, 40), "village": (240, 200, 60), "maison": (200, 120, 230),
           "effet": (90, 200, 230), "neige": (210, 230, 255)}


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


def calculer(lieux, effets):
    cercles = []
    for nom, pts in lieux.items():
        if nom in MAISONS:
            for k, g in enumerate(maisons(pts)):
                x, z, r = cercle(g, MARGE_MAISON)
                cercles.append({"nom": "%s_%d" % (nom, k), "genre": "maison", "x": x, "z": z, "rayon": r})
        else:
            genre = "grande" if nom in GRANDES else "village" if nom.startswith("v_") else "petit"
            marge = {"grande": MARGE_GRANDE, "village": MARGE_VILLAGE, "petit": MARGE_PETIT}[genre]
            x, z, r = cercle(pts, marge)
            cercles.append({"nom": nom, "genre": genre, "x": x, "z": z, "rayon": r})
    polys = []
    nord = None
    for e in effets.get("zones", []):
        polys.append({"nom": e["nom"], "genre": e["genre"], "points": e["points"],
                      "marge": int(e.get("bord", 0)) + MARGE_EFFET})
        if e["genre"] == "froid":
            bas = max(p[1] for p in e["points"]) + int(e.get("bord", 0)) + MARGE_EFFET
            nord = bas if nord is None else max(nord, bas)
    return cercles, polys, nord


def masque(x0, z0, w, h, pas, cercles, polys, nord):
    """Grille de cases (pas blocs) : vrai là où l'on ne construit pas."""
    xs = x0 + pas * (np.arange(w) + 0.5)
    zs = z0 + pas * (np.arange(h) + 0.5)
    X, Z = np.meshgrid(xs, zs)
    m = np.zeros(X.shape, bool)
    for c in cercles:
        m |= (X - c["x"]) ** 2 + (Z - c["z"]) ** 2 <= c["rayon"] ** 2
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
    return m


def main(argv):
    o = {"pack": str(PACK_V3), "effets": str(EFFETS_V3), "fond": str(ec.FOND_MOD),
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

    fond = ec.Fond(o["fond"])
    b = fond.info.get("bordure", [-1500, -1500, 1500, 1500]) if fond.ok else [-1500, -1500, 1500, 1500]
    x0, z0, x1, z1 = b
    pas = 4
    w, h = (x1 - x0) // pas, (z1 - z0) // pas
    m = masque(x0, z0, w, h, pas, cercles, polys, nord)
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
    for c in sorted(cercles, key=lambda c: -c["rayon"]):
        X, Y = v.p(c["x"], c["z"])
        r = c["rayon"] / v.k
        dr.ellipse([X - r, Y - r, X + r, Y + r], outline=COULEUR[c["genre"]] + (255,), width=2 if c["genre"] != "maison" else 1)
    ec.grille(img, v, x0, z0)
    f = ec.police(15)
    for c in cercles:
        if c["genre"] in ("grande", "petit", "village"):
            X, Y = v.p(c["x"], c["z"])
            ec.texte(dr, (X, Y), c["nom"].replace("v_", "village "), f, (255, 255, 255), True)
    tete = ("Terre interdite : %.0f %% (constructible : %.2f km²)" % (part_terre, libre_km2)) if terre is not None \
        else "Interdit de construire : %.0f %% de la carte" % part
    ec.legende(img, [(tete, None),
                     ("Grandes zones : lieu + %d blocs" % MARGE_GRANDE, COULEUR["grande"]),
                     ("Petits lieux : lieu + %d blocs" % MARGE_PETIT, COULEUR["petit"]),
                     ("Villages : village + %d blocs" % MARGE_VILLAGE, COULEUR["village"]),
                     ("Maisons isolées : maison + %d blocs" % MARGE_MAISON, COULEUR["maison"]),
                     ("Gaz, radiation, froid : zone + bord + %d" % MARGE_EFFET, COULEUR["effet"]),
                     ("Neige : tout le nord jusqu'à z = %s" % (nord if nord is not None else "-"), COULEUR["neige"])])
    ec.enregistrer(img, sortie / "carte_zones_construction.png")

    donnees = {"version": 1, "nord_z": nord, "cercles": cercles,
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
         "| Neige (nord) | 1 | jusqu'à z = %s |" % nord, ""]
    for c in sorted(cercles, key=lambda c: (c["genre"], c["nom"])):
        if c["genre"] != "maison":
            r.append("- %s (%s) : centre %d %d, rayon %d" % (c["nom"], c["genre"], c["x"], c["z"], c["rayon"]))
    (sortie / "rapport.md").write_text("\n".join(r) + "\n", encoding="utf-8")
    print("[zones_construction] %d cercles, %d zones à effets, nord z <= %s ; interdit %.1f %% de la carte%s"
          % (len(cercles), len(polys), nord, part, "" if terre is None else ", %.1f %% de la terre" % part_terre))


if __name__ == "__main__":
    main(sys.argv[1:])
