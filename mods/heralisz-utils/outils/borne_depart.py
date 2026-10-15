"""Borne de départ du spawn (HeraliZ Utils, bloc heralisz_utils:borne_depart) : modèle, textures et rendus de contrôle.

    python mods/heralisz-utils/outils/borne_depart.py [--rendus DOSSIER]

Écrit dans src/client/resources/assets/heralisz_utils/ :
- textures/block/borne_depart_corps.png (tôle peinte vert armée, rivets, rouille, bandeau rayé jaune et noir),
  borne_depart_ecran.png (écran vert : relief de la carte, cible rouge, flèche de départ), borne_depart_lampe.png
  (feu vert et bouton rouge) ;
- models/block/borne_depart.json (pupitre d'un bloc : socle rayé, pied, console, écran incliné vers le joueur, gros
  bouton, mât arrière et feu vert au-dessus), models/item/borne_depart.json, blockstates/borne_depart.json (quatre
  directions, écran vers le joueur qui la pose).
--rendus : vues de face, de trois quarts et de dos (rasterisation des faces du modèle, textures comprises) pour la
validation du propriétaire (CLAUDE.md, règle 9). On modifie ce fichier, jamais les fichiers produits.
"""
import argparse
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ICI = Path(__file__).resolve().parent.parent
ASSETS = ICI / "src/client/resources/assets/heralisz_utils"


# ------------------------------------------------------------------ textures (16 × 16, pixel art)
def tex_corps():
    r = random.Random(7)
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            if y < 12:
                b = r.randint(-5, 5)
                c = (74 + b, 84 + b, 60 + b)                       # vert armée délavé
                if x in (0, 15) or y in (0, 11):
                    c = (52, 58, 44)                                # joints des panneaux
                if y == 1 or x == 1:
                    c = (c[0] + 10, c[1] + 10, c[2] + 8)            # arête claire
            else:
                bande = ((x + y) // 2) % 2 == 0                     # bandeau rayé jaune et noir
                c = (214, 172, 38) if bande else (34, 33, 30)
                if r.random() < 0.18:
                    c = (110, 96, 70)                               # usure
            px[x, y] = c + (255,)
    for (x, y) in ((3, 3), (12, 3), (3, 8), (12, 8)):               # rivets et coulures de rouille
        px[x, y] = (150, 152, 140, 255)
        for k in range(1, 3):
            if y + k < 11 and r.random() < 0.8:
                px[x, y + k] = (120 - 10 * k, 72, 40, 255)
    for _ in range(9):                                              # éclats de peinture
        x, y = r.randint(2, 13), r.randint(2, 9)
        px[x, y] = (128, 82, 46, 255)
    return im


def tex_ecran():
    r = random.Random(11)
    im = Image.new("RGBA", (16, 16), (28, 30, 28, 255))
    px = im.load()
    for y in range(1, 15):
        for x in range(1, 15):
            fond = 22 + (6 if y % 2 == 0 else 0)                   # lignes de balayage
            px[x, y] = (10, fond + 18, 18, 255)
    # relief de la carte : courbes de niveau vertes
    for y in range(1, 15):
        for x in range(1, 15):
            if x % 5 == 0 or y % 5 == 0:
                px[x, y] = (20, 62, 32, 255)                        # quadrillage de la carte
    for x in range(1, 15):                                          # deux courbes de niveau
        for base, amp in ((11.0, 1.6), (6.0, 1.2)):
            y = int(round(base + amp * math.sin(x * 0.55 + base)))
            if 1 <= y <= 14:
                px[x, y] = (60, 190, 90, 255)
    # flèche de départ (blanc vert) vers la cible rouge
    for (x, y) in ((3, 11), (4, 10), (5, 9), (6, 8), (7, 7), (8, 6), (9, 5), (8, 5), (9, 6), (7, 5), (9, 7)):
        px[x, y] = (200, 255, 200, 255)
    for (x, y) in ((11, 3), (12, 3), (11, 4), (12, 4)):
        px[x, y] = (255, 60, 50, 255)
    px[11, 2] = px[13, 4] = (160, 40, 30, 255)
    # cadre de l'écran
    for k in range(16):
        px[k, 0] = px[k, 15] = px[0, k] = px[15, k] = (20, 21, 20, 255)
    px[14, 14] = (60, 220, 100, 255)                                # témoin allumé
    return im


def tex_lampe():
    im = Image.new("RGBA", (16, 16))
    px = im.load()
    for y in range(16):
        for x in range(16):
            if x < 8:                                              # feu vert (verre bombé)
                d = math.hypot(x - 3.5, y - 7.5) / 8
                c = (int(90 + 120 * (1 - d)), 255, int(120 + 100 * (1 - d)))
            else:                                                  # bouton rouge
                d = math.hypot(x - 11.5, y - 7.5) / 8
                c = (int(230 - 80 * d), int(50 - 20 * d), int(40 - 20 * d))
                if y in (0, 15) or x in (8, 15):
                    c = (90, 20, 18)
            px[x, y] = c + (255,)
    return im


# ------------------------------------------------------------------ modèle
def face(uv, tex, lumineux=False, cull=None):
    f = {"uv": uv, "texture": tex}
    if lumineux:
        f["forge_data"] = {"block_light": 15, "sky_light": 15}     # dessiné à pleine lumière (Forge)
    return f


def boite(de, a, faces, rotation=None):
    e = {"from": de, "to": a, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def modele():
    C, E, L = "#corps", "#ecran", "#lampe"
    panneau = [0, 0, 16, 11]
    rot = {"origin": [8, 13, 8], "axis": "x", "angle": -22.5}
    els = [
        # socle rayé
        boite([2, 0, 2], [14, 2, 14], {d: face([0, 12, 16, 14] if d not in ("up", "down") else [2, 2, 14, 11], C)
                                        for d in ("north", "south", "east", "west", "up", "down")}),
        # pied
        boite([4, 2, 5], [12, 10, 11], {d: face([4, 1, 12, 10], C) for d in ("north", "south", "east", "west")}),
        # console
        boite([2.5, 10, 2.5], [13.5, 13, 13.5], {d: face([1, 1, 15, 4], C) for d in ("north", "south", "east", "west")}
              | {"down": face(panneau, C), "up": face(panneau, C)}),
        # écran incliné vers le joueur
        boite([2.5, 13, 3], [13.5, 14.5, 13], {"up": face([1, 1, 15, 15], E, True),
                                              "north": face([1, 1, 15, 3], C), "south": face([1, 1, 15, 3], C),
                                              "east": face([1, 1, 11, 3], C), "west": face([1, 1, 11, 3], C)}, rot),
        # gros bouton rouge, à droite de l'écran
        boite([11, 14.5, 3.6], [12.8, 15.3, 5.4], {d: face([9, 6, 14, 10], L, True)
                                                    for d in ("up", "north", "south", "east", "west")}, rot),
        # mât arrière
        boite([11.75, 13, 12], [13.25, 22, 13.5], {d: face([6, 1, 8, 11], C) for d in ("north", "south", "east", "west")}),
        # feu vert et son chapeau
        boite([11, 22, 11.25], [14, 24.5, 14.25], {d: face([1, 4, 7, 11], L, True) for d in ("north", "south", "east", "west")}
              | {"down": face([1, 4, 7, 11], L, True)}),
        boite([10.5, 24.5, 10.75], [14.5, 25.25, 14.75], {d: face([1, 1, 15, 3], C)
                                                         for d in ("north", "south", "east", "west", "up", "down")}),
    ]
    return {"parent": "block/block", "ambientocclusion": False,
            "textures": {"particle": "heralisz_utils:block/borne_depart_corps", "corps": "heralisz_utils:block/borne_depart_corps",
                         "ecran": "heralisz_utils:block/borne_depart_ecran", "lampe": "heralisz_utils:block/borne_depart_lampe"},
            "display": {"gui": {"rotation": [30, 225, 0], "translation": [0, -2, 0], "scale": [0.5, 0.5, 0.5]},
                        "ground": {"rotation": [0, 0, 0], "translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
                        "fixed": {"rotation": [0, 0, 0], "translation": [0, -1, 0], "scale": [0.5, 0.5, 0.5]},
                        "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
                        "firstperson_righthand": {"rotation": [0, 45, 0], "translation": [0, 0, 0], "scale": [0.4, 0.4, 0.4]}},
            "elements": els}


def blockstate():
    v = {}
    for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270)):
        v["facing=" + d] = {"model": "heralisz_utils:block/borne_depart"} | ({"y": y} if y else {})
    return {"variants": v}


# ------------------------------------------------------------------ rendu (faces du modèle, painter)
NORMALES = {"north": (0, 0, -1), "south": (0, 0, 1), "east": (1, 0, 0), "west": (-1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}


def coins(de, a, f):
    x0, y0, z0 = de
    x1, y1, z1 = a
    # ordre : haut gauche, haut droite, bas droite, bas gauche (vu de l'extérieur), comme les uv de Minecraft
    return {"north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)]}[f]


def tourner(p, rot):
    if not rot:
        return p
    o, a = rot["origin"], math.radians(rot["angle"])
    x, y, z = p[0] - o[0], p[1] - o[1], p[2] - o[2]
    c, s = math.cos(a), math.sin(a)
    if rot["axis"] == "x":
        y, z = y * c - z * s, y * s + z * c
    elif rot["axis"] == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + o[0], y + o[1], z + o[2])


def rendu(m, textures, lacet, tangage, taille=900, fond=(24, 26, 30)):
    k = taille / 34.0
    cy, sy_ = math.cos(math.radians(lacet)), math.sin(math.radians(lacet))
    cp, sp = math.cos(math.radians(tangage)), math.sin(math.radians(tangage))

    def vue(p):
        x, y, z = p[0] - 8, p[1] - 10, p[2] - 8
        x, z = x * cy - z * sy_, x * sy_ + z * cy
        y, z = y * cp + z * sp, -y * sp + z * cp
        return (taille / 2 + x * k, taille * 0.58 - y * k, z)

    lumiere = (-0.4, 0.8, -0.45)
    polys = []
    for e in m["elements"]:
        for f, fd in e["faces"].items():
            pts = [tourner(p, e.get("rotation")) for p in coins(e["from"], e["to"], f)]
            n = tourner(NORMALES[f], dict(e["rotation"], origin=[0, 0, 0]) if e.get("rotation") else None)
            # normale dans la vue : face cachée si elle regarde loin de la caméra
            nx, nz = n[0] * cy - n[2] * sy_, n[0] * sy_ + n[2] * cy
            ny, nz = n[1] * cp + nz * sp, -n[1] * sp + nz * cp
            if nz > 0.01:
                continue
            v = [vue(p) for p in pts]
            prof = sum(p[2] for p in v) / 4
            ombre = 0.55 + 0.45 * max(0.0, -(n[0] * lumiere[0] + n[1] * lumiere[1] + n[2] * lumiere[2]) * -1)
            if "forge_data" in fd:
                ombre = 1.0
            polys.append((prof, v, fd, ombre))
    polys.sort(key=lambda t: -t[0])
    im = Image.new("RGB", (taille, taille), fond)
    for prof, v, fd, ombre in polys:
        tex = textures[fd["texture"][1:]]
        u0, v0, u1, v1 = fd["uv"]
        src = tex.crop((int(u0), int(v0), max(int(u0) + 1, int(math.ceil(u1))), max(int(v0) + 1, int(math.ceil(v1)))))
        sw, sh = src.size
        src = src.resize((sw * 32, sh * 32), Image.NEAREST)
        # affine : sortie -> source, à partir de trois coins (haut gauche, haut droite, bas gauche)
        (ax, ay, _), (bx, by, _), _, (dx, dy, _) = v
        W, H = src.size
        det = (bx - ax) * (dy - ay) - (dx - ax) * (by - ay)
        if abs(det) < 1e-6:
            continue
        ia = ((dy - ay) * W / det, -(dx - ax) * W / det)
        ib = (-(by - ay) * H / det, (bx - ax) * H / det)
        coef = (ia[0], ia[1], -(ia[0] * ax + ia[1] * ay), ib[0], ib[1], -(ib[0] * ax + ib[1] * ay))
        couche = src.convert("RGB").transform((taille, taille), Image.AFFINE, coef, resample=Image.NEAREST)
        couche = Image.eval(couche, lambda c, o=ombre: int(c * o))
        masque = Image.new("L", (taille, taille), 0)
        ImageDraw.Draw(masque).polygon([(p[0], p[1]) for p in v], fill=255)
        im.paste(couche, (0, 0), masque)
    return im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--rendus")
    a = ap.parse_args()
    textures = {"corps": tex_corps(), "ecran": tex_ecran(), "lampe": tex_lampe()}
    (ASSETS / "textures/block").mkdir(parents=True, exist_ok=True)
    for n, im in textures.items():
        im.save(ASSETS / "textures/block" / ("borne_depart_%s.png" % n))
    m = modele()
    for chemin, contenu in ((ASSETS / "models/block/borne_depart.json", m),
                            (ASSETS / "models/item/borne_depart.json", {"parent": "heralisz_utils:block/borne_depart"}),
                            (ASSETS / "blockstates/borne_depart.json", blockstate())):
        chemin.parent.mkdir(parents=True, exist_ok=True)
        chemin.write_text(json.dumps(contenu, ensure_ascii=False, indent=1) + "\n", encoding="utf-8")
    print("borne de départ : modèle, 3 textures et états écrits dans", ASSETS)
    if a.rendus:
        d = Path(a.rendus)
        d.mkdir(parents=True, exist_ok=True)
        vues = [rendu(m, textures, -35, 24), rendu(m, textures, 0, 14), rendu(m, textures, 180 + 35, 20)]
        planche = Image.new("RGB", (900 * 3, 940), (24, 26, 30))
        for i, v in enumerate(vues):
            planche.paste(v, (900 * i, 40))
        dr = ImageDraw.Draw(planche)
        try:
            from PIL import ImageFont
            f = ImageFont.truetype("DejaVuSans.ttf", 26)
        except Exception:
            f = None
        for i, t in enumerate(("Trois quarts avant", "Face (écran vers le joueur)", "Trois quarts arrière")):
            dr.text((900 * i + 20, 8), t, fill=(230, 230, 230), font=f)
        planche.save(d / "borne_depart.png")
        for n, im in textures.items():
            im.resize((256, 256), Image.NEAREST).save(d / ("borne_texture_%s.png" % n))
        print("rendus :", d / "borne_depart.png")


if __name__ == "__main__":
    main()
