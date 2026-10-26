"""Dessine les icônes 16 x 16 des objets de quête et de la baguette (pixel par pixel), et leurs modèles d'objet.

    python modules/pnj/outils/icones.py [--apercu planche.png]

Icônes PROVISOIRES (à valider par le propriétaire, règle 9) : on modifie ce script, jamais les .png à la main.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw

RACINE = Path(__file__).resolve().parents[1] / "src" / "client" / "resources" / "assets" / "heraliz_quetes"
TEX = RACINE / "textures" / "item"
MOD = RACINE / "models" / "item"

C = {
    "papier": (232, 224, 204), "papier2": (205, 194, 168), "papier3": (170, 158, 132),
    "encre": (70, 66, 74), "rouge": (176, 52, 48), "rouge2": (128, 34, 32),
    "cuir": (120, 78, 46), "cuir2": (88, 56, 32), "cuir3": (150, 102, 62),
    "or": (226, 186, 92), "or2": (176, 136, 58), "or3": (250, 220, 140),
    "acier": (168, 174, 182), "acier2": (118, 124, 134), "acier3": (210, 216, 222), "noir": (40, 42, 48),
    "carton": (176, 136, 88), "carton2": (138, 102, 60), "carton3": (204, 168, 116), "scotch": (214, 200, 150),
    "verre": (176, 214, 226), "verre2": (120, 168, 186), "vert": (96, 196, 110), "vert2": (54, 140, 70),
    "bleu": (70, 120, 200), "bleu2": (44, 80, 150), "bois": (122, 88, 54), "bois2": (90, 62, 36), "violet": (180, 110, 230),
}


def img():
    return Image.new("RGBA", (16, 16), (0, 0, 0, 0))


def rect(d, x0, y0, x1, y1, c):
    d.rectangle([x0, y0, x1, y1], fill=C[c] + (255,))


def px(d, pts, c):
    for x, y in pts:
        d.point((x, y), fill=C[c] + (255,))


def contour(im):
    """Fin contour sombre autour de la forme (lisible sur tous les fonds)."""
    src = im.copy()
    out = im.copy()
    for y in range(16):
        for x in range(16):
            if src.getpixel((x, y))[3]:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < 16 and 0 <= ny < 16 and src.getpixel((nx, ny))[3]:
                    out.putpixel((x, y), (28, 26, 30, 200))
                    break
    return out


def document():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 3, 1, 12, 14, "papier"); rect(d, 12, 2, 13, 14, "papier2"); rect(d, 4, 14, 13, 14, "papier3")
    for y in (4, 6, 8, 10):
        rect(d, 5, y, 10 if y != 10 else 8, y, "encre")
    px(d, [(9, 11), (10, 11), (10, 12), (9, 12), (11, 12)], "rouge")  # tampon « R »
    return im


def carnet():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 3, 2, 12, 14, "cuir"); rect(d, 3, 2, 4, 14, "cuir2"); rect(d, 12, 3, 12, 13, "papier2")
    rect(d, 6, 5, 10, 6, "cuir3"); rect(d, 8, 2, 8, 14, "cuir2")
    px(d, [(11, 9), (12, 9)], "or")
    return im


def lettre():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 1, 4, 14, 12, "papier"); rect(d, 1, 12, 14, 12, "papier3")
    for i in range(7):
        px(d, [(1 + i, 4 + i // 1 if i < 5 else 9), (14 - i, 4 + i if i < 5 else 9)], "papier2")
    for i in range(6):
        px(d, [(2 + i, 5 + i), (13 - i, 5 + i)], "papier2")
    rect(d, 7, 9, 8, 10, "rouge"); px(d, [(7, 9)], "rouge2")
    return im


def photo():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 2, 3, 13, 13, "papier"); rect(d, 3, 4, 12, 10, "verre2")
    rect(d, 3, 8, 12, 10, "vert2"); px(d, [(5, 7), (6, 6), (7, 7), (8, 8), (9, 7), (10, 6), (11, 7)], "vert2")
    rect(d, 9, 5, 10, 5, "or3"); rect(d, 2, 13, 13, 13, "papier3")
    return im


def cle():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 2, 3, 6, 7, "or"); rect(d, 3, 4, 5, 6, (0, 0, 0) and "or2"); px(d, [(4, 5)], "noir")
    for i in range(7):
        px(d, [(6 + i, 7 + i // 2 if False else 7 + i), (7 + i, 7 + i)], "or")
    px(d, [(10, 12), (11, 11), (12, 14), (13, 13)], "or2")
    px(d, [(3, 3), (2, 4)], "or3")
    return im


def cle_usb():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 5, 4, 10, 13, "bleu"); rect(d, 9, 4, 10, 13, "bleu2"); rect(d, 6, 1, 9, 4, "acier")
    rect(d, 7, 2, 7, 2, "noir"); rect(d, 8, 2, 8, 2, "noir"); rect(d, 6, 1, 9, 1, "acier3")
    rect(d, 7, 10, 8, 11, "acier3")
    return im


def colis():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 2, 4, 13, 14, "carton"); rect(d, 2, 4, 13, 6, "carton3"); rect(d, 11, 6, 13, 14, "carton2")
    rect(d, 7, 4, 8, 14, "scotch"); rect(d, 2, 9, 13, 9, "scotch")
    rect(d, 3, 11, 5, 12, "papier")
    return im


def echantillon():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 6, 1, 9, 2, "acier2"); rect(d, 6, 3, 9, 13, "verre"); rect(d, 9, 3, 9, 13, "verre2")
    rect(d, 6, 8, 9, 13, "vert"); rect(d, 9, 8, 9, 13, "vert2"); rect(d, 7, 14, 8, 14, "verre2")
    px(d, [(7, 4), (7, 5)], "acier3")
    return im


def plaque():
    im = img(); d = ImageDraw.Draw(im)
    for i in range(5):
        px(d, [(3 + i, 1 + i), (12 - i, 1 + i)], "acier2")
    rect(d, 4, 6, 11, 13, "acier"); rect(d, 11, 7, 11, 13, "acier2"); rect(d, 5, 13, 10, 13, "acier2")
    rect(d, 5, 7, 6, 7, "acier3"); px(d, [(7, 6), (8, 6)], "noir")
    rect(d, 6, 9, 9, 9, "noir"); rect(d, 6, 11, 8, 11, "noir")
    return im


def piece():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 4, 4, 11, 11, "acier"); rect(d, 6, 6, 9, 9, "noir"); rect(d, 7, 7, 8, 8, "acier2")
    for x, y in ((7, 1), (8, 1), (7, 2), (8, 2), (7, 13), (8, 13), (7, 14), (8, 14), (1, 7), (1, 8), (2, 7), (2, 8),
                 (13, 7), (13, 8), (14, 7), (14, 8), (3, 3), (12, 3), (3, 12), (12, 12)):
        px(d, [(x, y)], "acier2")
    rect(d, 4, 4, 11, 4, "acier3")
    return im


def baguette():
    im = img(); d = ImageDraw.Draw(im)
    for i in range(10):
        px(d, [(3 + i, 13 - i)], "bois"); px(d, [(4 + i, 13 - i)], "bois2")
    rect(d, 11, 1, 14, 4, "violet"); px(d, [(12, 0), (15, 2), (10, 3), (13, 5)], "or3"); px(d, [(12, 2)], "or3")
    return im


def registre_marchand():
    im = img(); d = ImageDraw.Draw(im)
    rect(d, 2, 2, 12, 14, "cuir"); rect(d, 2, 2, 3, 14, "cuir2"); rect(d, 12, 3, 13, 14, "papier2")
    rect(d, 5, 4, 10, 4, "or2"); rect(d, 5, 6, 10, 6, "or2")
    rect(d, 9, 9, 13, 13, "or"); rect(d, 10, 8, 12, 8, "or"); rect(d, 10, 14, 12, 14, "or2"); rect(d, 14, 10, 14, 12, "or2")
    px(d, [(10, 10), (11, 9)], "or3"); px(d, [(11, 11), (11, 12)], "or2")
    return im


ICONES = {"document": document, "carnet": carnet, "lettre": lettre, "photo": photo, "cle": cle, "cle_usb": cle_usb,
          "colis": colis, "echantillon": echantillon, "plaque": plaque, "piece": piece, "baguette": baguette,
          "registre_marchand": registre_marchand}


def main():
    TEX.mkdir(parents=True, exist_ok=True)
    MOD.mkdir(parents=True, exist_ok=True)
    rendus = []
    for nom, f in ICONES.items():
        im = contour(f())
        im.save(TEX / (nom + ".png"))
        (MOD / (nom + ".json")).write_text(json.dumps({"parent": "minecraft:item/generated",
                                                        "textures": {"layer0": "heraliz_quetes:item/" + nom}}, indent=2) + "\n")
        rendus.append(im)
    if "--apercu" in sys.argv:
        dest = Path(sys.argv[sys.argv.index("--apercu") + 1])
        planche = Image.new("RGBA", (len(rendus) * 72 + 8, 80), (30, 32, 38, 255))
        for i, im in enumerate(rendus):
            planche.paste(im.resize((64, 64), Image.NEAREST), (8 + i * 72, 8), im.resize((64, 64), Image.NEAREST))
        planche.save(dest)
        print("[icones] aperçu :", dest)
    print("[icones] %d icônes écrites" % len(rendus))


if __name__ == "__main__":
    main()
