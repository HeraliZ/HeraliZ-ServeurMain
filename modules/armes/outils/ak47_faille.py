"""AK-47 « Faille » : modèle propre au skin Faille sur l'AK-47 (même squelette que l'AK de TaCZ : animations, rechargement,
accessoires intacts). Rendus validés par le propriétaire le 05/11 (maquettes/skins_premium/ak_faille/).

python modules/armes/outils/ak47_faille.py --installer
    écrit dans le gunpack serveur/tacz/heralisz_skins : le modèle (geo_models/gun/ak47_faille_geo.json), sa texture
    (textures/gun/uv/ak47_faille.png) et son affichage (display/guns/ak47_faille_display.json, copie de celui de l'AK-47 :
    animations, sons, petit modèle de loin et icône de l'AK, seuls le modèle et la texture changent). Le jeu du joueur le
    montre sur tout AK-47 qui porte le skin Faille (camo/client/ModelesSkins), rien n'est enregistré sur l'arme.
python modules/armes/outils/ak47_faille.py <ak47_geo.json> <ak47.png> <sortie>   (essais : modèle et texture seulement)

On modifie ce script, jamais les fichiers écrits (ils seraient écrasés au prochain passage).

Pièces refaites, en version fine (2e jet, 05/11) :
- crosse : squelette d'obsidienne mince (deux poutres, montant, plaque de couche chanfreinée), une grappe de cristaux
  lumineux dans le creux, un aileron fin de chaque côté ;
- garde-main : deux plaques minces, arêtes chanfreinées, fente étroite qui laisse voir un fil de lumière ;
- chargeur à fenêtre : deux montants d'obsidienne courbes, liseré lumineux le long de la fenêtre, et à l'intérieur les
  balles en laiton (double rangée), visibles à travers comme dans un chargeur transparent ;
- bouche : couronne de quatre cristaux fins, pointe lumineuse.
Le reste (carcasse, culasse, organes de visée, détente, poignée, canon, rails, chargeurs des accessoires) garde la forme
de l'AK : le skin Faille le recouvre. Les pièces lumineuses sont dans des os « *_illuminated » : TaCZ les dessine en
pleine lumière et le camo ne les touche pas. Les balles sont dans le chargeur (elles partent et reviennent avec lui au
rechargement) ; leur texture porte une marque (alpha 200) qui dit au skin de les laisser en laiton. Chaque face a son
propre morceau de texture (carte de positions des camos).
"""
import json
import math
import random
import sys
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[3]
AK = RACINE / "serveur/tacz/heraliz_1_base_tacz/assets/tacz"
PACK = RACINE / "serveur/tacz/heralisz_skins/assets/heralisz_skins"
INSTALLER = len(sys.argv) > 1 and sys.argv[1] == "--installer"
if INSTALLER:
    SRC_GEO, SRC_TEX = AK / "geo_models/gun/ak47_geo.json", AK / "textures/gun/uv/ak47.png"
    SORTIE_GEO, SORTIE_TEX = PACK / "geo_models/gun/ak47_faille_geo.json", PACK / "textures/gun/uv/ak47_faille.png"
elif len(sys.argv) == 4:
    SRC_GEO, SRC_TEX, OUT = Path(sys.argv[1]), Path(sys.argv[2]), Path(sys.argv[3])
    SORTIE_GEO, SORTIE_TEX = OUT / "ak47_faille_geo.json", OUT / "ak47_faille.png"
else:
    sys.exit(__doc__)
SORTIE_GEO.parent.mkdir(parents=True, exist_ok=True)
SORTIE_TEX.parent.mkdir(parents=True, exist_ok=True)
TW = TH = 512
D = 2.0  # points de texture par pixel de modèle (faces neuves)
MARQUE_LAITON = 200  # alpha des faces des balles : le skin les laisse en métal

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
curseur = {"x": 128, "y": 0, "h": 0, "x0": 128, "ymax": 128}


def place(w, h):
    """Rangement des faces : à droite des 128 premiers points, puis tout le bas de la texture."""
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


TEINTES = {
    "obsidienne": ((30, 26, 40), 10),
    "cristal": ((70, 54, 100), 14),
    "laiton": ((214, 164, 70), 12),
    "cuivre": ((190, 104, 58), 10),
    "lumiere": ((175, 245, 255), 0),
    "lumiere_m": ((255, 120, 235), 0),
}


def peindre(x, y, w, h, matiere, graine):
    r = random.Random(graine)
    base, bruit = TEINTES[matiere]
    a = MARQUE_LAITON if matiere in ("laiton", "cuivre") else 255
    for i in range(w):
        for j in range(h):
            u, t = (i + 0.5) / w, (j + 0.5) / h
            bord = max(abs(u - 0.5), abs(t - 0.5)) * 2
            if matiere.startswith("lumiere"):
                f = 1.0 - 0.15 * max(0.0, bord - 0.6) / 0.4
                c = tuple(int(v * f) for v in base)
            else:
                d = r.randint(-bruit, bruit)
                c = tuple(max(0, min(255, v + d)) for v in base)
            px[x + i, y + j] = c + (a,)


compte = {"n": 0, "cristaux": 0}


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


def eclat(base, rx, ry, long, ep, matiere="cristal"):
    """Cristal effilé : trois étages qui se resserrent le long de son axe (rx > 0 : vers le haut), même pivot (la base)."""
    bx, by, bz = base
    if matiere == "lumiere":
        compte["cristaux"] += 1
        matiere = "lumiere_m" if compte["cristaux"] % 3 == 0 else "lumiere"
    out, z0 = [], bz
    for (k, part) in ((1.0, 0.55), (0.62, 0.27), (0.3, 0.18)):
        e = ep * k
        out.append(cube((bx - e / 2, by - e / 2, z0), (e, e, long * part), matiere, rot=(rx, ry, 45), pivot=(bx, by, bz)))
        z0 += long * part
    return out


def nouvel_os(nom, parent, pivot, cubes):
    b = {"name": nom, "parent": parent, "pivot": pivot, "cubes": cubes}
    g["bones"].insert(g["bones"].index(bones[parent]) + 1, b)
    bones[nom] = b


def vider(*noms):
    for n in noms:
        bones[n]["cubes"] = []


# ------------------------------------------------------------------ crosse : squelette mince, une grappe de cristaux
vider("wood", "bone", "steel", "group16")
crosse = [
    cube((-0.55, 8.35, 10.0), (1.1, 0.75, 10.8), rot=(-3.5, 0, 0), pivot=(0, 8.7, 10.0)),         # poutre haute
    cube((-0.45, 6.6, 10.0), (0.9, 0.7, 10.9), rot=(-12.5, 0, 0), pivot=(0, 6.95, 10.0)),         # poutre basse
    cube((-0.5, 6.6, 9.8), (1.0, 2.55, 0.9)),                                                      # montant
    cube((-0.75, 4.3, 20.4), (1.5, 5.0, 0.9)),                                                     # plaque de couche
    cube((-0.75, 8.65, 20.05), (1.5, 0.75, 1.15), rot=(-45, 0, 0), pivot=(0, 9.0, 20.6)),          # chanfrein haut
    cube((-0.75, 4.0, 20.05), (1.5, 0.7, 1.15), rot=(45, 0, 0), pivot=(0, 4.35, 20.6)),            # chanfrein bas
]
for sg in (-1, 1):
    crosse += eclat((sg * 0.55, 8.85, 16.8), 30, sg * 16, 2.2, 0.36)                              # ailerons fins
bones["wood"]["cubes"] = crosse


def y_poutre(z):
    return 7.3 - (z - 10.0) * 0.222


grappe = []
bz = 15.2
by = y_poutre(bz) - 0.05
grappe += eclat((0, by, bz), 46, 0, 3.0, 0.5, "lumiere")
grappe += eclat((0.12, by, bz - 0.5), 26, 20, 1.9, 0.32, "lumiere")
grappe += eclat((-0.12, by, bz + 0.5), 68, -18, 1.6, 0.3, "lumiere")
bz = 18.4
by = y_poutre(bz) - 0.05
grappe += eclat((0, by, bz), 55, 0, 1.6, 0.34, "lumiere")
nouvel_os("crosse_cristaux_illuminated", "stock_default", [0, 7, 15], grappe)

# ------------------------------------------------------------------ garde-main : plaques minces, fente étroite, fil de lumière
vider("wood4", "group26", "group27")
gm = [
    cube((-0.9, 7.05, -9.4), (1.8, 1.2, 7.0)),                                                     # plaque basse
    cube((-0.85, 8.9, -9.0), (1.7, 1.3, 6.6)),                                                     # plaque haute (en retrait)
    cube((-0.9, 7.05, -10.15), (1.8, 0.9, 0.9), rot=(-40, 0, 0), pivot=(0, 7.5, -9.4)),           # biseau avant bas
    cube((-0.85, 9.3, -9.65), (1.7, 0.95, 0.8), rot=(35, 0, 0), pivot=(0, 9.8, -9.0)),            # biseau avant haut
    cube((-1.0, 6.9, -9.85), (2.0, 3.55, 0.35)),                                                   # bague avant
    cube((-1.0, 6.9, -2.75), (2.0, 3.55, 0.35)),                                                   # bague arrière
]
for sg in (-1, 1):
    gm.append(cube((sg * 0.72 - 0.22, 10.0, -9.0), (0.44, 0.44, 6.6), rot=(0, 0, 45), pivot=(sg * 0.72, 10.22, -6)))  # arêtes
    gm.append(cube((sg * 0.78 - 0.22, 6.95, -9.4), (0.44, 0.44, 7.0), rot=(0, 0, 45), pivot=(sg * 0.78, 7.17, -6)))
for sg, ry in ((-1, -16), (1, 16)):
    gm += eclat((sg * 0.45, 7.25, -9.9), 205, ry, 1.3, 0.32)                                       # deux éclats vers l'avant
bones["wood4"]["cubes"] = gm
nouvel_os("garde_main_coeur_illuminated", "handguard2", [0, 8.6, -6],
          [cube((-0.95, 8.4, -9.1), (1.9, 0.3, 6.3), "lumiere")])

# ------------------------------------------------------------------ chargeur à fenêtre, balles visibles
vider("group4", "group5")
mag, liseres = [], []
y, z = 8.1, 1.3
angle = 0.0
for i in range(4):
    lg = 2.25
    a = angle + 3.5
    rot, piv = (-a, 0, 0), (0, y, z)
    h0, hh = y - lg - 0.3, lg + 0.6
    mag.append(cube((-0.95, h0, z - 1.55), (1.9, hh, 0.5), rot=rot, pivot=piv))                    # montant avant
    mag.append(cube((-0.95, h0, z + 1.05), (1.9, hh, 0.5), rot=rot, pivot=piv))                    # montant arrière
    for sg in (-1, 1):
        x0 = 0.95 if sg > 0 else -1.0
        liseres.append(cube((x0, h0, z - 1.05), (0.05, hh, 0.09), "lumiere", rot=rot, pivot=piv))  # liseré de la fenêtre
        liseres.append(cube((x0, h0, z + 0.96), (0.05, hh, 0.09), "lumiere", rot=rot, pivot=piv))
    # balles : double rangée, couchées, pointe vers le canon
    for k in range(4):
        yk = y - 0.32 - k * 0.56
        bx = 0.24 if (k + i) % 2 == 0 else -0.24
        mag.append(cube((bx - 0.23, yk - 0.23, z - 0.38), (0.46, 0.46, 1.3), "laiton", rot=rot, pivot=piv))
        mag.append(cube((bx - 0.25, yk - 0.25, z + 0.92), (0.5, 0.5, 0.06), "cuivre", rot=rot, pivot=piv))           # culot
        mag.append(cube((bx - 0.17, yk - 0.17, z - 0.9), (0.34, 0.34, 0.54), "cuivre", rot=rot, pivot=piv))
        mag.append(cube((bx - 0.09, yk - 0.09, z - 1.05), (0.18, 0.18, 0.17), "cuivre", rot=rot, pivot=piv))
    r = math.radians(a)
    y -= lg * math.cos(r)
    z -= lg * math.sin(r)
    angle += 7.0
r = math.radians(angle - 3.5)
mag.append(cube((-1.0, y - 0.3, z - 1.6), (2.0, 0.45, 3.2), rot=(-(angle - 3.5), 0, 0), pivot=(0, y, z)))   # plaque du fond
bones["group4"]["cubes"] = mag
nouvel_os("chargeur_fenetre_illuminated", "d_mag", [0, 8, -8], liseres)

# ------------------------------------------------------------------ bouche : couronne fine
vider("muzzle_default")
bouche = [cube((-0.45, 8.1, -20.15), (0.9, 0.9, 0.85)), cube((-0.36, 8.19, -20.7), (0.72, 0.72, 0.55))]
for k in range(4):
    an = math.radians(45 + k * 90)
    bouche += eclat((0.38 * math.cos(an), 8.55 + 0.38 * math.sin(an), -20.25), 180 - 12 * math.sin(an), 12 * math.cos(an), 0.95, 0.26)
bones["muzzle_default"]["cubes"] = bouche
nouvel_os("bouche_cristaux_illuminated", "muzzle_default", [0, 8.55, -20.8],
          [cube((-0.14, 8.41, -21.45), (0.28, 0.28, 0.7), "lumiere")])

# ------------------------------------------------------------------ carcasse : un éclat fin sur chaque flanc du couvercle
for sg in (-1, 1):
    bones["cover"]["cubes"] = bones["cover"]["cubes"] + eclat((sg * 0.88, 9.6, 6.8), 20, sg * 32, 1.6, 0.32)

SORTIE_GEO.write_bytes((json.dumps(geo, separators=(",", ":"), ensure_ascii=False) + "\n").encode("utf-8"))
tex.save(SORTIE_TEX)
print("faces neuves :", compte["n"], "| os :", len(g["bones"]), "|", SORTIE_GEO.name, SORTIE_TEX.name)

if INSTALLER:
    # affichage : celui de l'AK-47 (commentaires du fichier retirés), avec ce modèle et cette texture
    import re
    texte = (AK / "display/guns/ak47_display.json").read_text(encoding="utf-8")
    texte = "\n".join(re.sub(r"^\s*//.*$", "", l) for l in texte.splitlines())
    aff = json.loads(texte)
    assert aff["model"] == "tacz:gun/ak47_geo" and aff["texture"] == "tacz:gun/uv/ak47", (aff["model"], aff["texture"])
    aff["model"] = "heralisz_skins:gun/ak47_faille_geo"
    aff["texture"] = "heralisz_skins:gun/uv/ak47_faille"
    d = PACK / "display/guns/ak47_faille_display.json"
    d.write_bytes((json.dumps(aff, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))
    print("affichage :", d.relative_to(RACINE).as_posix())
