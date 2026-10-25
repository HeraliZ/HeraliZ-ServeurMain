"""Textures du bloc d'herbe (grass_block) du pack imposé : dessus, côté (terre du pack + frange d'herbe), surcouche.

Jamais retouchées à la main : on change ce script puis on relance.
  python mods/heraliz-interface/outils/herbe_bloc.py --apercu DOSSIER   planches des variantes A, B, C
  python mods/heraliz-interface/outils/herbe_bloc.py --installer B      écrit la variante choisie dans le pack

Le dessus et la surcouche sont en gris : Minecraft les teinte par la couleur du biome (comme le vanilla).
Le côté reprend la terre du pack (dirt.png, 32 px) pour que côté et terre soient identiques.
"""
import argparse
import os
import random

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
BLOCS = os.path.join(ICI, "..", "src", "main", "resources", "resourcepacks", "textures",
                     "assets", "minecraft", "textures", "block")
T = 32  # même taille que la terre du pack (multiple de 16 : mipmaps gardées)

# variante : (luminosité moyenne du gris, contraste, profondeur de la frange en points)
VARIANTES = {
    "A": (150, 34, 7),   # proche du vanilla, à peine plus sombre
    "B": (128, 38, 8),   # plus foncé
    "C": (108, 42, 9),   # bien foncé
}
TEINTES = {"plaine": (0x91, 0xBD, 0x59), "forêt": (0x79, 0xC0, 0x5A), "taïga": (0x86, 0xB7, 0x83)}


def gris_herbe(lum, contraste, graine):
    r = random.Random(graine)
    img = Image.new("RGBA", (T, T))
    for y in range(T):
        for x in range(T):
            # brins : bruit par point + petits traits verticaux plus clairs / plus sombres
            v = lum + r.gauss(0, contraste * 0.45)
            if r.random() < 0.10:
                v += contraste
            if r.random() < 0.08:
                v -= contraste
            v = max(30, min(235, int(v)))
            img.putpixel((x, y), (v, v, v, 255))
    # quelques brins de 2 points de haut (lisible comme de l'herbe, pas du bruit)
    for _ in range(T * T // 14):
        x, y = r.randrange(T), r.randrange(T)
        v = min(235, lum + contraste + r.randint(0, 12))
        img.putpixel((x, y), (v, v, v, 255))
        img.putpixel((x, (y + 1) % T), (v - 8, v - 8, v - 8, 255))
    return img


def frange(prof, graine):
    """Hauteur de la frange pour chaque colonne (bord irrégulier façon vanilla)."""
    r = random.Random(graine)
    h = []
    for x in range(T):
        base = prof + (2 if x % 5 == 0 else 0) - (1 if x % 3 == 1 else 0)
        h.append(max(3, base + r.choice((-2, -1, 0, 0, 1, 2))))
    return h


def faire(var):
    lum, contraste, prof = VARIANTES[var]
    dessus = gris_herbe(lum, contraste, 11)
    terre = Image.open(os.path.join(BLOCS, "dirt.png")).convert("RGBA").resize((T, T), Image.NEAREST)
    hs = frange(prof, 23)
    surcouche = Image.new("RGBA", (T, T), (0, 0, 0, 0))
    for x in range(T):
        for y in range(hs[x]):
            surcouche.putpixel((x, y), dessus.getpixel((x, y)))
    # côté de base (sans teinte, vu en graphismes rapides et par certains mods) : terre + frange verte foncée
    cote = terre.copy()
    vert = teinter(surcouche, TEINTES["plaine"])
    cote.alpha_composite(vert)
    # ombre douce sous la frange, sur la terre
    for x in range(T):
        y = hs[x]
        if y < T:
            p = cote.getpixel((x, y))
            cote.putpixel((x, y), tuple(int(c * 0.7) for c in p[:3]) + (255,))
    return dessus, cote, surcouche


def teinter(img, t):
    out = Image.new("RGBA", img.size)
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = img.getpixel((x, y))
            out.putpixel((x, y), (r * t[0] // 255, g * t[1] // 255, b * t[2] // 255, a))
    return out


def bloc_iso(dessus, cote, surcouche, teinte, z=8):
    """Bloc en vue isométrique simple (dessus + deux côtés ombrés)."""
    d = teinter(dessus, teinte)
    c = cote.copy()
    c.alpha_composite(teinter(surcouche, teinte))
    n = T * z
    W, H = 2 * n, 2 * n
    img = Image.new("RGBA", (W, H + n // 2), (0, 0, 0, 0))
    px = img.load()
    for y in range(T):
        for x in range(T):
            col = d.getpixel((x, y))
            for sy in range(z):
                for sx in range(z):
                    u, v = (x * z + sx) / n, (y * z + sy) / n
                    X = int(n + (u - v) * n)
                    Y = int((u + v) * n / 2)
                    for k in range(2):
                        if 0 <= X + k < W:
                            px[X + k, Y] = col
    for face, fx, ombre in ((c, 0, 0.78), (c, 1, 0.62)):
        for y in range(T):
            for x in range(T):
                col = face.getpixel((x, y))
                col = tuple(int(q * ombre) for q in col[:3]) + (255,)
                for sy in range(z):
                    for sx in range(z):
                        u = (x * z + sx) / n
                        if fx == 0:
                            X, Y0 = int(u * n), int(n / 2 + u * n / 2)
                        else:
                            X, Y0 = int(n + u * n), int(n - u * n / 2)
                        Y = Y0 + y * z + sy
                        if 0 <= X < W and Y < img.height:
                            px[X, Y] = col
    return img


def planche(var):
    dessus, cote, surcouche = faire(var)
    blocs = [bloc_iso(dessus, cote, surcouche, t, 4) for t in TEINTES.values()]
    sol = Image.new("RGBA", (T * 4, T * 4))
    td = teinter(dessus, TEINTES["plaine"])
    for i in range(4):
        for j in range(4):
            sol.paste(td, (i * T, j * T))
    sol = sol.resize((256, 256), Image.NEAREST)
    cote_v = cote.copy(); cote_v.alpha_composite(teinter(surcouche, TEINTES["plaine"]))
    cote_v = cote_v.resize((256, 256), Image.NEAREST)
    W = 20 + sum(b.width + 20 for b in blocs) + 2 * 276
    out = Image.new("RGBA", (W, 300), (40, 44, 48, 255))
    x = 20
    for b in blocs:
        out.alpha_composite(b, (x, 20)); x += b.width + 20
    out.alpha_composite(cote_v, (x, 22)); x += 276
    out.alpha_composite(sol, (x, 22))
    return out


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apercu")
    p.add_argument("--installer", choices=sorted(VARIANTES))
    a = p.parse_args()
    if a.apercu:
        os.makedirs(a.apercu, exist_ok=True)
        for v in VARIANTES:
            planche(v).save(os.path.join(a.apercu, f"herbe_{v}.png"))
    if a.installer:
        dessus, cote, surcouche = faire(a.installer)
        dessus.save(os.path.join(BLOCS, "grass_block_top.png"))
        cote.save(os.path.join(BLOCS, "grass_block_side.png"))
        surcouche.save(os.path.join(BLOCS, "grass_block_side_overlay.png"))


if __name__ == "__main__":
    main()
