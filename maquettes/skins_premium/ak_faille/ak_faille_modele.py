"""AK-47 « Faille » : modèle remodelé (même squelette que l'AK de TaCZ : animations, rechargement, accessoires intacts).

python ak_faille_modele.py <ak47_geo.json> <ak47.png> <sortie>   -> sortie/ak47_faille_geo.json + sortie/ak47_faille.png

Pièces refaites : crosse (squelette d'obsidienne et grappe de cristaux), garde-main (deux plaques et une fente qui laisse
voir un cœur lumineux), chargeur (éclat courbe à cœur lumineux), bouche (couronne de cristaux). Le reste (carcasse, culasse,
organes de visée, détente, poignée, canon, rails, chargeurs des accessoires) garde la forme de l'AK : le skin Faille le
recouvre. Les pièces lumineuses sont dans des os « *_illuminated » : TaCZ les dessine en pleine lumière et le camo ne les
touche pas. Chaque face a son propre morceau de texture (carte de positions des camos).
"""
import copy
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

SRC_GEO, SRC_TEX, OUT = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
OUT.mkdir(parents=True, exist_ok=True)
TW = TH = 512
D = 2.0  # points de texture par pixel de modèle (faces neuves)

geo = json.load(open(SRC_GEO, encoding="utf-8"))
g = geo["minecraft:geometry"][0]
g["description"]["identifier"] = "geometry.ak47_faille"
g["description"]["texture_width"] = TW
g["description"]["texture_height"] = TH
bones = {b["name"]: b for b in g["bones"]}

# ------------------------------------------------------------------ texture : AK d'origine en haut à gauche, faces neuves
tex = Image.new("RGBA", (TW, TH), (0, 0, 0, 0))
tex.paste(Image.open(SRC_TEX).convert("RGBA"), (0, 0))
px = tex.load()
rnd = random.Random(210)

# rangées libres : à droite des 128 premiers points, puis tout le bas
etageres = [[128, 0, 0, 128], [0, 128, 0, TH]]   # x de départ, y, hauteur de la rangée courante, y max de la zone
curseur = {"x": 128, "y": 0, "h": 0, "x0": 128, "ymax": 128}


def place(w, h):
    w, h = max(1, math.ceil(w)), max(1, math.ceil(h))
    c = curseur
    if c["x"] + w + 1 > TW:
        c["x"] = c["x0"]
        c["y"] += c["h"] + 1
        c["h"] = 0
    if c["y"] + h + 1 > c["ymax"]:
        if c["ymax"] == 128:
            c.update(x=0, y=128, h=0, x0=0, ymax=TH)
            return place(w, h)
        raise SystemExit("texture pleine")
    x, y = c["x"], c["y"]
    c["x"] += w + 1
    c["h"] = max(c["h"], h)
    return x, y, w, h


def peindre(x, y, w, h, matiere, graine):
    r = random.Random(graine)
    for i in range(w):
        for j in range(h):
            if matiere == "obsidienne":
                v = 30 + r.randint(0, 10)
                c = (v, v - 4, v + 10, 255)
            elif matiere == "cristal":
                v = 60 + r.randint(0, 18)
                c = (v + 10, v - 6, v + 40, 255)
            else:  # lumière : couleur unie (cyan pâle une fois sur deux, sinon magenta), bords à peine plus sombres
                u = (i + 0.5) / w
                t = (j + 0.5) / h
                bord = max(abs(u - 0.5), abs(t - 0.5)) * 2
                teinte = (255, 120, 235) if matiere == "lumiere_m" else (175, 245, 255)
                f = 1.0 - 0.18 * max(0.0, bord - 0.6) / 0.4
                c = tuple(int(v * f) for v in teinte) + (255,)
            px[x + i, y + j] = c


compte = {"n": 0}


def cube(origine, taille, matiere="obsidienne", rot=None, pivot=None):
    sx, sy, sz = taille
    faces = {"north": (sx, sy), "south": (sx, sy), "east": (sz, sy), "west": (sz, sy), "up": (sx, sz), "down": (sx, sz)}
    uv = {}
    for nom, (fw, fh) in faces.items():
        x, y, w, h = place(fw * D, fh * D)
        compte["n"] += 1
        peindre(x, y, w, h, matiere, compte["n"])
        uv[nom] = {"uv": [x, y], "uv_size": [w, h]}
    c = {"origin": [round(v, 4) for v in origine], "size": [round(v, 4) for v in taille], "uv": uv}
    if rot:
        c["rotation"] = [round(v, 3) for v in rot]
        c["pivot"] = [round(v, 4) for v in (pivot or origine)]
    return c


def eclat(base, dirz_deg, dirx_deg, long, ep, matiere="cristal"):
    """Cristal : prisme fin partant de base, incliné (rot x, rot y), avec une pointe plus fine."""
    bx, by, bz = base
    if matiere == "lumiere":
        compte["cristaux"] = compte.get("cristaux", 0) + 1
        matiere = "lumiere_m" if compte["cristaux"] % 3 == 0 else "lumiere"
    out, z0 = [], bz
    for (k, part) in ((1.0, 0.55), (0.66, 0.28), (0.34, 0.22)):
        e = ep * k
        out.append(cube((bx - e / 2, by - e / 2, z0), (e, e, long * part), matiere, rot=(dirz_deg, dirx_deg, 45), pivot=(bx, by, bz)))
        z0 += long * part
    return out


def nouvel_os(nom, parent, pivot, cubes):
    b = {"name": nom, "parent": parent, "pivot": pivot, "cubes": cubes}
    g["bones"].insert(g["bones"].index(bones[parent]) + 1, b)
    bones[nom] = b


def vider(*noms):
    for n in noms:
        bones[n]["cubes"] = []


# ------------------------------------------------------------------ crosse : squelette d'obsidienne, grappe de cristaux
vider("wood", "bone", "steel", "group16")
crosse = []
crosse.append(cube((-0.7, 8.25, 10.0), (1.4, 1.0, 10.9), rot=(-3.5, 0, 0), pivot=(0, 8.75, 10.0)))          # poutre haute
crosse.append(cube((-0.55, 6.5, 10.0), (1.1, 0.9, 10.95), rot=(-12.5, 0, 0), pivot=(0, 6.95, 10.0)))         # poutre basse
crosse.append(cube((-0.65, 6.55, 9.75), (1.3, 2.75, 1.1)))                                                   # montant
crosse.append(cube((-0.9, 4.25, 20.35), (1.8, 5.2, 1.0)))                                                    # plaque de couche
crosse.append(cube((-0.9, 8.6, 20.0), (1.8, 0.9, 1.3), rot=(-45, 0, 0), pivot=(0, 9.05, 20.65)))             # chanfrein haut
crosse.append(cube((-0.9, 3.9, 20.0), (1.8, 0.8, 1.3), rot=(45, 0, 0), pivot=(0, 4.3, 20.65)))             # chanfrein bas
# ailerons d'éclats vers l'arrière, sur les côtés de la poutre haute (hors de la ligne de visée)
for sg in (-1, 1):
    crosse += eclat((sg * 0.7, 8.9, 16.5), 34, sg * 18, 2.6, 0.5)
    crosse += eclat((sg * 0.6, 8.6, 13.0), 24, sg * 24, 1.9, 0.45)
bones["wood"]["cubes"] = crosse
# grappe de cristaux lumineux dans le creux de la crosse
grappe = []
def y_poutre(z):
    return 7.35 - (z - 10.0) * 0.222


for (bz, lg) in ((12.8, 2.1), (15.4, 2.5), (17.9, 1.9)):
    by = y_poutre(bz) - 0.1
    grappe += eclat((0, by, bz), 42, 0, lg * 1.2, 0.6, "lumiere")
    grappe += eclat((0.15, by, bz - 0.3), 28, 25, lg * 0.85, 0.42, "lumiere")
    grappe += eclat((-0.15, by, bz + 0.3), 62, -22, lg * 0.7, 0.38, "lumiere")
nouvel_os("crosse_cristaux_illuminated", "stock_default", [0, 7, 15], grappe)

# ------------------------------------------------------------------ garde-main : deux plaques, fente, cœur lumineux
vider("wood4", "group26", "group27")
gm = []
gm.append(cube((-1.05, 6.95, -9.5), (2.1, 1.4, 7.25)))                                                       # plaque basse
gm.append(cube((-0.95, 8.95, -9.0), (1.9, 1.55, 6.75)))                                                      # plaque haute (en retrait)
gm.append(cube((-1.05, 6.95, -10.3), (2.1, 1.0, 1.0), rot=(-40, 0, 0), pivot=(0, 7.45, -9.5)))             # biseau avant bas
gm.append(cube((-0.95, 9.4, -9.7), (1.9, 1.1, 0.9), rot=(35, 0, 0), pivot=(0, 9.95, -9.0)))                 # biseau avant haut
for k in range(3):
    for sg in (-1, 1):
        gm.append(cube((sg * 1.05 - (0.12 if sg > 0 else -0.0), 7.15, -8.6 + k * 2.1), (0.12, 1.0, 1.3), "cristal"))   # nervures
for sg in (-1, 1):
    gm.append(cube((sg * 0.85 - 0.3, 10.05, -9.5), (0.6, 0.6, 7.25), rot=(0, 0, 45), pivot=(sg * 0.85, 10.35, -6)))   # arêtes chanfreinées
    gm.append(cube((sg * 0.95 - 0.3, 6.85, -9.5), (0.6, 0.6, 7.25), rot=(0, 0, 45), pivot=(sg * 0.95, 7.15, -6)))
gm.append(cube((-1.15, 6.75, -9.95), (2.3, 3.95, 0.5)))                                                      # bague avant
gm.append(cube((-1.15, 6.75, -2.7), (2.3, 3.95, 0.45)))                                                      # bague arrière
# éclats qui sortent de l'avant du garde-main, vers l'avant et le bas
for sg, rx, ry in ((-1, 30, -18), (1, 26, 16), (0, 40, 0)):
    gm += eclat((sg * 0.6, 7.1, -9.9), 180 + rx, ry, 1.6, 0.45)
bones["wood4"]["cubes"] = gm
nouvel_os("garde_main_coeur_illuminated", "handguard2", [0, 8.7, -6],
          [cube((-1.0, 8.4, -9.2), (2.0, 0.5, 6.6), "lumiere")])

# ------------------------------------------------------------------ chargeur : éclat courbe à cœur lumineux
vider("group4", "group5")
mag, coeur = [], []
haut_y, haut_z = 8.1, 1.3
angle = 0.0
y, z = haut_y, haut_z
for i in range(4):
    lg = 2.25
    a_mid = angle + 3.5
    # segment : boîte posée sous le point (y, z), tournée autour de x au point
    mag.append(cube((-0.95, y - lg - 0.3, z - 1.55), (1.9, lg + 0.6, 3.1), rot=(-a_mid, 0, 0), pivot=(0, y, z)))
    mag.append(cube((-0.6, y - lg - 0.3, z + 1.45), (1.2, lg + 0.6, 0.45), "cristal", rot=(-a_mid, 0, 0), pivot=(0, y, z)))   # arête arrière
    mag.append(cube((-0.6, y - lg - 0.3, z - 1.9), (1.2, lg + 0.6, 0.45), "cristal", rot=(-a_mid, 0, 0), pivot=(0, y, z)))    # arête avant
    coeur.append(cube((-1.0, y - lg - 0.3, z - 0.2), (2.0, lg + 0.6, 0.4), "lumiere", rot=(-a_mid, 0, 0), pivot=(0, y, z)))
    rad = math.radians(a_mid)
    y -= lg * math.cos(rad)
    z -= lg * math.sin(rad)
    angle += 7.0
# pointe de cristal au bas du chargeur
mag += eclat((0, y + 0.1, z), -105, 0, 1.3, 0.6)
bones["group4"]["cubes"] = mag
nouvel_os("chargeur_coeur_illuminated", "d_mag", [0, 8, -8], coeur)

# ------------------------------------------------------------------ bouche : couronne de cristaux
vider("muzzle_default")
bouche = [cube((-0.5, 8.05, -20.2), (1.0, 1.0, 0.95)), cube((-0.42, 8.13, -20.8), (0.84, 0.84, 0.6))]
for k in range(5):
    a = k * 72
    ox, oy = 0.42 * math.cos(math.radians(a)), 8.55 + 0.42 * math.sin(math.radians(a))
    bouche += eclat((ox, oy, -20.3), 180 - 14 * math.sin(math.radians(a)), 14 * math.cos(math.radians(a)), 1.1, 0.32)
bones["muzzle_default"]["cubes"] = bouche
nouvel_os("bouche_cristaux_illuminated", "muzzle_default", [0, 8.55, -20.8],
          [cube((-0.18, 8.37, -21.6), (0.36, 0.36, 0.8), "lumiere")])

# ------------------------------------------------------------------ carcasse : deux éclats sur les flancs du couvercle
for sg in (-1, 1):
    bones["cover"]["cubes"] = bones["cover"]["cubes"] + eclat((sg * 0.9, 9.6, 6.5), 22, sg * 35, 2.0, 0.42)

json.dump(geo, open(OUT / "ak47_faille_geo.json", "w", encoding="utf-8"), indent=1)
tex.save(OUT / "ak47_faille.png")
print("faces neuves :", compte["n"], "| os :", len(g["bones"]))
