#!/usr/bin/env python3
"""
Bloc d'herbe HeraliZ « Prairie sauvage » (choix du propriétaire du 10/11, sur des vues du joueur :
`maquettes/herbe/bloc_herbe_*.jpg`). L'ancien bloc (un grain « poivre et sel » partout, côtés tout verts) faisait une
prairie plate et terne.

Ce que fait ce script, dans NOTRE pack heraliz_terrain (posé tout en haut des packs ; jamais de retouche à la main des
fichiers écrits) :
- le dessus (32 points par bloc) : des brins lisibles, en touffes couchées, en 5 tons francs (ombres vert froid,
  pointes qui tirent vers le jaune), avec des mèches d'herbe sèche ; deux dessins tirés au hasard, tournés au hasard ;
- les côtés : l'herbe retombe en mèches inégales sur la terre (30 à 62 % de la hauteur), avec une ombre sous l'herbe ;
  la terre du côté (`grass_block_side.png`) n'est pas touchée ;
- des détails posés au sol sur environ 1 bloc d'herbe sur 8, jamais teintés par le biome : cailloux, plaque de terre
  nue, feuilles mortes, pâquerettes et boutons-d'or (64 points). Ce sont des images à plat sur le dessus du bloc (une
  face de plus pour ces blocs-là seulement), au loin elles s'effacent d'elles-mêmes ;
- l'état de bloc de `minecraft:grass_block` (tirages pondérés, 4 rotations) et les modèles
  `heralisz_utils:block/sol/*`. Le bloc d'herbe enneigé ne change pas.

Les dessus et le liseré des côtés sont en gris (ou presque) : le jeu les teinte de la couleur de l'herbe du biome
(`modules/interface/outils/couleurs_nature.py`). Tout est calculé, graines fixes : relancer redonne les mêmes images.

    python modules/monde/outils/herbe_sol.py                 écrit les fichiers du pack
    python modules/monde/outils/herbe_sol.py --verifier      contrôle seulement (code 1 si un fichier diffère)
    python modules/monde/outils/herbe_sol.py --apercu F.png  planche des textures (dessus en pavage, côté, détails)

Besoin : numpy, scipy, Pillow.
"""
import io
import json
import math
import sys
from pathlib import Path

import numpy as np
import scipy.ndimage as nd
from PIL import Image

ICI = Path(__file__).resolve().parent
DEPOT = ICI.parents[2]
TERRAIN = DEPOT / "modules/monde/src/client/resources/resourcepacks/heraliz_terrain/assets"
NS = "heralisz_utils"
MOYENNE = 157.0            # clarté moyenne du dessus (celle de l'ancien bloc : la couleur d'ensemble ne change pas)

# tirages du dessus : (modèle, poids). Les détails font 24 sur 200, soit 12 % des blocs d'herbe.
TIRAGES = [("minecraft:block/grass_block", 88), (f"{NS}:block/sol/herbe_2", 88),
           (f"{NS}:block/sol/herbe_cailloux", 5), (f"{NS}:block/sol/herbe_terre", 5),
           (f"{NS}:block/sol/herbe_feuilles", 6), (f"{NS}:block/sol/herbe_fleurs", 8)]
DETAILS = {"cailloux": ("minecraft:block/grass_block_top", 111), "terre": (f"{NS}:block/sol/herbe_2", 112),
           "feuilles": ("minecraft:block/grass_block_top", 113), "fleurs": (f"{NS}:block/sol/herbe_2", 114)}


# ------------------------------------------------------------------ dessin
def bruit(rng, sigma, n):
    """Bruit doux qui se raccorde d'un bord à l'autre, moyenne 0, écart 1."""
    a = nd.gaussian_filter(rng.standard_normal((n, n)), sigma, mode="wrap")
    return (a - a.mean()) / a.std()


def semis(rng, n, nb, ecart):
    """nb points répartis sur un carré qui se raccorde, jamais deux à moins de « ecart »."""
    pts = []
    essais = 0
    while len(pts) < nb and essais < 20000:
        essais += 1
        c = rng.uniform(0, n, 2)
        if all(min(abs(c[0] - d[0]), n - abs(c[0] - d[0])) ** 2 + min(abs(c[1] - d[1]), n - abs(c[1] - d[1])) ** 2 > ecart ** 2 for d in pts):
            pts.append(c)
    return pts


def dessus(rng, n=32):
    """Brins d'un point de large, 3 à 7 de long, en touffes couchées, puis 5 tons francs."""
    H = np.full((n, n), -1.0)
    val = 100 + 5 * bruit(rng, 1.5, n)
    sec = np.zeros((n, n))
    for cx, cy in semis(rng, n, 46, 3.6):
        sens = rng.uniform(0, 2 * math.pi)
        seche = rng.random() < 0.22
        for _ in range(int(rng.integers(5, 9))):
            niv = rng.random()
            lng = rng.uniform(3.0, 7.0)
            x, y = cx + rng.normal(0, 1.2), cy + rng.normal(0, 1.2)
            ang = sens + rng.normal(0, 0.55)
            cb = rng.uniform(-0.9, 0.9)
            pas = int(lng * 2) + 1
            for k in range(pas + 1):
                t = k / pas
                a = ang + cb * t
                x += math.cos(a) * lng / pas
                y += math.sin(a) * lng / pas
                i, j = int(round(y)) % n, int(round(x)) % n
                hgt = niv + t
                if hgt >= H[i, j]:                                   # le brin du dessus cache celui du dessous
                    H[i, j] = hgt
                    val[i, j] = 96 + 78 * t ** 0.8 + 18 * niv
                    sec[i, j] = 1.0 if seche and t > 0.3 else 0.0
    # sans grandes taches : sinon chaque bloc montre la même et la prairie devient un damier
    val = val - nd.gaussian_filter(val, n / 5.0, mode="wrap")
    val = val / val.std()
    tons = np.array([-2.2, -0.95, 0.0, 0.8, 1.6])
    q = np.abs(val[..., None] - tons).argmin(-1)
    pal = np.array([[118, 126, 126], [138, 143, 140], [156, 158, 152], [178, 176, 160], [204, 196, 164]], float)
    img = pal[q]
    img[sec > 0] += np.array([22.0, 6.0, -30.0])                    # herbe sèche
    lum = img.mean(-1)
    return np.clip(img - lum.mean() + MOYENNE + 6, 0, 255), None


def lisere(rng, n=32, prof_min=0.30, prof_max=0.62, larg=(1, 2), pied_sombre=0.5):
    """Côté : l'herbe retombe sur la terre en mèches de longueurs inégales. Rend (image, opacité)."""
    courbe = nd.gaussian_filter1d(rng.standard_normal(n), n / 10.0, mode="wrap")
    courbe = (courbe - courbe.min()) / (courbe.max() - courbe.min())
    img = np.zeros((n, n))
    al = np.zeros((n, n))
    j = 0
    while j < n:
        w = int(rng.integers(larg[0], larg[1] + 1))
        prof = prof_min + (prof_max - prof_min) * (0.65 * courbe[j] + 0.35 * rng.random())
        dn = max(3, int(round(prof * n)))
        ton = rng.normal(0, 9)
        for jj in range(j, min(j + w, n)):
            d2 = dn - (1 if (w > 1 and jj in (j, j + w - 1) and rng.random() < 0.7) else 0)      # pointe effilée
            for i in range(min(d2, n)):
                t = i / max(d2 - 1, 1)
                img[i, jj] = MOYENNE + 6 + ton - 255 * pied_sombre * 0.42 * t ** 1.4 + (5 if jj == j else 0)
                al[i, jj] = 255
        j += w
    img[:2] += 7                                                    # arête du haut un peu plus claire
    img += rng.normal(0, 2.0, (n, n))
    for j in range(n):                                              # ombre sous l'herbe : dernier point de chaque mèche
        k = int(al[:, j].sum() // 255)
        img[k - 1, j] = 96
    return np.dstack([img, img, img]), al


def cailloux(rng, n=64):
    img = np.zeros((n, n, 3))
    al = np.zeros((n, n))
    for cx, cy in semis(rng, n - 16, int(rng.integers(3, 6)), 11):
        cx, cy = cx + 8, cy + 8
        rx, ry = rng.uniform(2.0, 4.6), rng.uniform(1.8, 3.6)
        ang = rng.uniform(0, math.pi)
        y, x = np.mgrid[0:n, 0:n]
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        u = (dx * math.cos(ang) + dy * math.sin(ang)) / rx
        v = (-dx * math.sin(ang) + dy * math.cos(ang)) / ry
        r2 = u * u + v * v
        m = r2 <= 1
        gris = rng.uniform(118, 160)
        relief = 1 - 0.35 * r2 - 0.22 * (dx + dy) / (rx + ry)          # clair en haut à gauche
        val = gris * np.clip(relief, 0.45, 1.25)
        img[m] = np.stack([val * 1.0, val * 0.99, val * 0.95], -1)[m]
        al[m] = 255
    return img + rng.normal(0, 3, (n, n, 1)), al


def terre(rng, n=64):
    b = bruit(rng, 5.0, n) + 0.35 * bruit(rng, 1.5, n)
    y, x = np.mgrid[0:n, 0:n]
    c = 2.3 - ((x - n / 2 + 0.5) ** 2 + (y - n / 2 + 0.5) ** 2) / (n * 0.25) ** 2 * 2.4
    m = (0.8 * b + c) > 1.2
    lab, nb = nd.label(m)
    if nb > 1:                                                     # une seule plaque de terre nue
        m = lab == (1 + int(np.argmax(np.bincount(lab.ravel())[1:])))
    bord = m & ~nd.binary_erosion(m, iterations=2)
    val = 1.0 + 0.10 * bruit(rng, 0.8, n)
    img = np.stack([86 * val, 63 * val, 43 * val], -1)
    img[bord] *= 0.78
    return img, m * 255.0


def feuilles(rng, n=64):
    img = np.zeros((n, n, 3))
    al = np.zeros((n, n))
    tons = [(132, 90, 42), (152, 112, 52), (112, 72, 36), (146, 96, 38)]
    for cx, cy in semis(rng, n - 12, int(rng.integers(9, 14)), 7):
        cx, cy = cx + 6, cy + 6
        ang = rng.uniform(0, math.pi)
        rx, ry = rng.uniform(3.0, 4.6), rng.uniform(1.5, 2.3)
        y, x = np.mgrid[0:n, 0:n]
        dx, dy = x + 0.5 - cx, y + 0.5 - cy
        u = (dx * math.cos(ang) + dy * math.sin(ang)) / rx
        v = (-dx * math.sin(ang) + dy * math.cos(ang)) / ry
        m = (u * u + v * v) <= 1
        t = np.array(tons[int(rng.integers(0, len(tons)))], float)
        img[m] = t * (1 - 0.25 * np.abs(v[m])[:, None])
        img[m & (np.abs(v) < 0.22)] = t * 0.68                      # nervure
        al[m] = 255
    return img, al


def fleurs(rng, n=64):
    img = np.zeros((n, n, 3))
    al = np.zeros((n, n))
    for cx, cy in semis(rng, n - 10, int(rng.integers(7, 11)), 8):
        i, j = int(cy) + 5, int(cx) + 5
        if rng.random() < 0.6:                                     # pâquerette : croix blanche, cœur jaune
            for di, dj in ((-1, 0), (1, 0), (0, -1), (0, 1)):
                img[i + di, j + dj] = (236, 236, 226)
                al[i + di, j + dj] = 255
            if rng.random() < 0.5:
                for di, dj in ((-1, -1), (1, 1), (-1, 1), (1, -1)):
                    img[i + di, j + dj] = (214, 216, 208)
                    al[i + di, j + dj] = 255
            img[i, j] = (236, 190, 52)
            al[i, j] = 255
        else:                                                      # bouton-d'or
            for di, dj in ((0, 0), (0, 1), (1, 0), (1, 1)):
                img[i + di, j + dj] = (242, 206, 60) if (di + dj) % 2 == 0 else (222, 178, 40)
                al[i + di, j + dj] = 255
    return img, al


def png(a, alpha=None):
    """Octets du PNG. Les points transparents prennent la couleur moyenne des points pleins : pas de liseré sombre
    quand le jeu calcule les petites versions de l'image pour le lointain."""
    a = np.clip(a + 0.5, 0, 255).astype(np.uint8)
    if alpha is None:
        al = np.full(a.shape[:2], 255, np.uint8)
    else:
        al = np.clip(alpha, 0, 255).astype(np.uint8)
        plein = al > 127
        if plein.any() and not plein.all():
            a[~plein] = a[plein].mean(0).astype(np.uint8)
    b = io.BytesIO()
    Image.fromarray(np.dstack([a, al]), "RGBA").save(b, "PNG", optimize=True)
    return b.getvalue()


# ------------------------------------------------------------------ fichiers
def fichiers():
    """{chemin relatif à assets/ : octets}."""
    out = {}
    out["minecraft/textures/block/grass_block_top.png"] = png(*dessus(np.random.default_rng(101)))
    out[f"{NS}/textures/block/sol/herbe_2.png"] = png(*dessus(np.random.default_rng(102)))
    out["minecraft/textures/block/grass_block_side_overlay.png"] = png(*lisere(np.random.default_rng(103)))
    dessins = {"cailloux": cailloux, "terre": terre, "feuilles": feuilles, "fleurs": fleurs}
    for nom, (_, graine) in DETAILS.items():
        out[f"{NS}/textures/block/sol/{nom}.png"] = png(*dessins[nom](np.random.default_rng(graine)))

    def js(o):
        return (json.dumps(o, ensure_ascii=False, indent=2) + "\n").encode("utf8")

    out[f"{NS}/models/block/sol/herbe_2.json"] = js({"parent": "minecraft:block/grass_block",
                                                    "textures": {"top": f"{NS}:block/sol/herbe_2"}})
    # bloc d'herbe de Minecraft + une face de plus sur le dessus pour le détail (pas de teinte du biome)
    cotes = ("north", "south", "west", "east")
    out[f"{NS}/models/block/sol/herbe_detail.json"] = js({
        "parent": "minecraft:block/block",
        "textures": {"particle": "minecraft:block/dirt", "bottom": "minecraft:block/dirt",
                     "top": "minecraft:block/grass_block_top", "side": "minecraft:block/grass_block_side",
                     "overlay": "minecraft:block/grass_block_side_overlay"},
        "elements": [
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": dict(
                [("down", {"uv": [0, 0, 16, 16], "texture": "#bottom", "cullface": "down"}),
                 ("up", {"uv": [0, 0, 16, 16], "texture": "#top", "cullface": "up", "tintindex": 0})]
                + [(c, {"uv": [0, 0, 16, 16], "texture": "#side", "cullface": c}) for c in cotes])},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": dict(
                (c, {"uv": [0, 0, 16, 16], "texture": "#overlay", "tintindex": 0, "cullface": c}) for c in cotes)},
            {"from": [0, 0, 0], "to": [16, 16, 16], "faces": {
                "up": {"uv": [0, 0, 16, 16], "texture": "#detail", "cullface": "up"}}},
        ]})
    for nom, (top, _) in DETAILS.items():
        out[f"{NS}/models/block/sol/herbe_{nom}.json"] = js({
            "parent": f"{NS}:block/sol/herbe_detail",
            "textures": {"top": top, "detail": f"{NS}:block/sol/{nom}"}})
    variantes = []
    for modele, poids in TIRAGES:
        for rot in (0, 90, 180, 270):
            v = {"model": modele, "weight": poids}
            if rot:
                v["y"] = rot
            variantes.append(v)
    out["minecraft/blockstates/grass_block.json"] = js({"variants": {
        "snowy=false": variantes, "snowy=true": {"model": "minecraft:block/grass_block_snow"}}})
    return out


def apercu(fic, sortie):
    def img(rel):
        return np.array(Image.open(io.BytesIO(fic[rel])).convert("RGBA"), np.float32)

    teinte = np.array([145, 180, 114], np.float32) / 255
    tops = [img("minecraft/textures/block/grass_block_top.png"), img(f"{NS}/textures/block/sol/herbe_2.png")]
    rng = np.random.default_rng(1)
    n = tops[0].shape[0]
    pave = np.zeros((6 * n, 6 * n, 3), np.float32)
    for i in range(6):
        for j in range(6):
            pave[i * n:(i + 1) * n, j * n:(j + 1) * n] = np.rot90(tops[int(rng.integers(0, 2))][..., :3], int(rng.integers(0, 4))) * teinte
    k = 4
    out = Image.new("RGB", (6 * n * k + 20, 6 * n * k + n * k + 64 * 4 + 50), (24, 24, 24))
    out.paste(Image.fromarray(pave.clip(0, 255).astype(np.uint8)).resize((6 * n * k, 6 * n * k), Image.NEAREST), (10, 10))
    li = img("minecraft/textures/block/grass_block_side_overlay.png")
    terre_cote = TERRAIN / "minecraft/textures/block/grass_block_side.png"
    base = np.array(Image.open(terre_cote).convert("RGB").resize((n, n), Image.NEAREST), np.float32) if terre_cote.exists() \
        else np.full((n, n, 3), (63, 47, 32), np.float32)
    al = li[..., 3:4] / 255
    cote = np.tile(base * (1 - al) + li[..., :3] * teinte * al, (1, 6, 1))
    out.paste(Image.fromarray(cote.clip(0, 255).astype(np.uint8)).resize((6 * n * k, n * k), Image.NEAREST), (10, 20 + 6 * n * k))
    for q, nom in enumerate(DETAILS):
        d = img(f"{NS}/textures/block/sol/{nom}.png")
        fond = np.tile(tops[q % 2][..., :3] * teinte, (2, 2, 1))
        a = d[..., 3:4] / 255
        c = Image.fromarray((fond * (1 - a) + d[..., :3] * a).clip(0, 255).astype(np.uint8)).resize((176, 176), Image.NEAREST)
        out.paste(c, (10 + q * 190, 30 + 6 * n * k + n * k))
    out.save(sortie)
    print("aperçu :", sortie)


def main():
    fic = fichiers()
    if "--apercu" in sys.argv:
        apercu(fic, sys.argv[sys.argv.index("--apercu") + 1])
        return
    verifier = "--verifier" in sys.argv
    diff = 0
    for rel, octets in sorted(fic.items()):
        p = TERRAIN / rel
        pareil = p.exists() and p.read_bytes() == octets
        if not pareil:
            diff += 1
            if verifier:
                print("  diffère :", rel)
            else:
                p.parent.mkdir(parents=True, exist_ok=True)
                p.write_bytes(octets)
                print("  écrit :", rel)
    detail = sum(p for m, p in TIRAGES if "herbe_2" not in m and not m.startswith("minecraft:")) / sum(p for _, p in TIRAGES)
    print(f"bloc d'herbe : {len(fic)} fichiers, {diff} {'différent(s)' if verifier else 'écrit(s)'} ; "
          f"détails au sol sur {detail * 100:.0f} % des blocs d'herbe")
    if verifier and diff:
        sys.exit(1)


if __name__ == "__main__":
    main()
