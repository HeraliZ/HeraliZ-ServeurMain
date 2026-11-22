"""Textures de la caisse de Noël (« sapin ») : atlas 1024 x 1024 et son calque lumineux.

    python modules/boutique/outils/textures_noel.py

Écrit src/client/resources/assets/heralizcrates/textures/crate/noel.png et noel_glow.png, et la texture de particules du
bloc (textures/block/noel_particle.png). Cases de l'atlas : celles de client/render/mesh/Mat.java, rôles dans
client/render/scene/crates/Sapin.java. Jamais retouchées à la main : on modifie ce script et on le relance. Besoin : numpy
et Pillow.
"""
import math
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

RACINE = Path(__file__).resolve().parents[1]
RES = RACINE / "src/client/resources/assets/heralizcrates"
R = np.random.default_rng(2512)


def bruit(w, h, echelle, octaves=4):
    out = np.zeros((h, w))
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        n = max(2, int(echelle * 2 ** o))
        g = R.random((n + 1, n + 1))
        im = Image.fromarray((g * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += np.asarray(im) / 255 * amp
        tot += amp
        amp *= 0.5
    return out / tot


def ecrire(img, xy, arr):
    x, y = xy
    h, w = arr.shape[:2]
    img[y:y + h, x:x + w] = arr


def tuile(couleur, w, h):
    return np.ones((h, w, 3)) * np.array(couleur) / 255


def aiguilles(n):
    """Aiguilles de sapin : fond vert très sombre en profondeur, petits rameaux (une tige, des aiguilles fines de part et
    d'autre, vers le bas de la pente), verts de plus en plus clairs vers le dessus, quelques pointes vert tendre."""
    base = np.stack([0.02 + 0.02 * bruit(n, n, 4), 0.08 + 0.05 * bruit(n, n, 4), 0.04 + 0.03 * bruit(n, n, 4)], -1)
    im = Image.fromarray((np.clip(base, 0, 1) * 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    verts = [(10, 40, 22), (16, 62, 32), (22, 84, 40), (30, 104, 50), (44, 126, 62), (70, 152, 80)]
    for couche in range(4):
        for _ in range(900):
            x, y = R.random() * n, R.random() * n
            a = math.radians(R.normal(90, 14))
            ln = R.uniform(18, 34)
            c = verts[min(len(verts) - 1, couche + int(R.integers(0, 2)))]
            dx, dy = math.cos(a), math.sin(a)
            d.line([(x, y), (x + dx * ln, y + dy * ln)], fill=tuple(int(v * 0.8) for v in c), width=1)
            for k in range(int(ln / 2.2)):
                px, py = x + dx * k * 2.2, y + dy * k * 2.2
                for sd in (-1, 1):
                    b = a + sd * math.radians(R.uniform(35, 55))
                    l2 = R.uniform(3.5, 6.5) * (1 - 0.4 * k * 2.2 / ln)
                    d.line([(px, py), (px + math.cos(b) * l2, py + math.sin(b) * l2)], fill=c, width=1)
    im = im.filter(ImageFilter.GaussianBlur(0.4))
    a = np.asarray(im) / 255
    return np.clip(a * (0.85 + 0.3 * bruit(n, n, 6, 2))[..., None], 0, 1)


def neige(n):
    """Neige fraîche : blanc bleuté doux, creux un peu plus bleus, grains qui scintillent."""
    b = bruit(n, n, 5, 4)
    lum = 0.86 + 0.12 * b
    img = np.stack([lum * 0.95, lum * 0.97, lum * 1.0], -1)
    img = img * (1 - 0.06 * (1 - b))[..., None]
    pts = R.random((n, n))
    img[pts > 0.9975] = 1.0
    return np.clip(img, 0, 1)


def flocon(d, cx, cy, r, col, w=2):
    for k in range(6):
        a = math.radians(k * 60 + 90)
        x1, y1 = cx + math.cos(a) * r, cy + math.sin(a) * r
        d.line([(cx, cy), (x1, y1)], fill=col, width=w)
        for f in (0.45, 0.7):
            bx, by = cx + math.cos(a) * r * f, cy + math.sin(a) * r * f
            for s in (-1, 1):
                b2 = a + s * math.radians(40)
                d.line([(bx, by), (bx + math.cos(b2) * r * 0.28, by + math.sin(b2) * r * 0.28)], fill=col, width=w)


def etoile(d, cx, cy, r1, r2, col):
    pts = []
    for k in range(10):
        a = -math.pi / 2 + k * math.pi / 5
        r = r1 if k % 2 == 0 else r2
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(pts, fill=col)


def papier(n, fond, motif):
    """Papier cadeau : fond satiné et motif répété (cases décalées)."""
    im = Image.new("RGB", (n, n), fond)
    d = ImageDraw.Draw(im)
    pas = n // 4
    for j in range(5):
        for i in range(5):
            cx = i * pas + (pas // 2 if j % 2 else 0)
            cy = j * pas
            motif(d, cx % n, cy % n, pas)
    a = np.asarray(im) / 255
    return np.clip(a * (0.9 + 0.14 * bruit(n, n, 3, 2))[..., None], 0, 1)


def papier_rayures(n):
    """Papier blanc à rayures rouges en biais (sucre d'orge)."""
    y, x = np.mgrid[0:n, 0:n]
    band = ((x + y) // (n // 8)) % 2 == 0
    img = np.where(band[..., None], np.array([0.94, 0.93, 0.92]), np.array([0.78, 0.08, 0.13]))
    return np.clip(img * (0.92 + 0.1 * bruit(n, n, 3, 2))[..., None], 0, 1)


def brosse(w, h, base, var):
    """Métal brossé horizontal."""
    lignes = R.random((h, 1)) * 0.6 + bruit(1, h, h // 4, 1) * 0.4
    s = np.repeat(lignes, w, 1)
    s = np.asarray(Image.fromarray((s * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur((6, 0)))) / 255
    lum = 0.8 + var * (s - 0.5) + 0.1 * (bruit(w, h, 2, 2) - 0.5)
    return np.clip(np.array(base)[None, None, :] / 255 * lum[..., None], 0, 1)


def ecorce(n):
    """Écorce brune à fibres verticales."""
    fibres = np.repeat(R.random((1, n)), n, 0)
    fibres = np.asarray(Image.fromarray((fibres * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur((0, 2)))) / 255
    lum = 0.55 + 0.35 * fibres + 0.15 * (bruit(n, n, 6, 3) - 0.5)
    return np.clip(np.stack([lum * 0.42, lum * 0.27, lum * 0.16], -1), 0, 1)


def face_cadenas(n):
    """Face du cadenas : laque rouge de Noël, liseré doré, flocon doré."""
    im = Image.new("RGB", (n, n), (178, 18, 34))
    d = ImageDraw.Draw(im)
    for i in range(14):
        d.rectangle([i, i, n - 1 - i, n - 1 - i], outline=(int(200 + 3 * i), int(150 + 4 * i), 50))
    flocon(d, n / 2, n * 0.36, 44, (255, 214, 96), 6)
    a = np.asarray(im) / 255 * (0.9 + 0.12 * bruit(n, n, 3, 2)[..., None])
    return np.clip(a, 0, 1)


def verre(n):
    """Verre des boules et des ampoules (teinté par le modèle) : clair, très léger dégradé."""
    y, x = np.mgrid[0:n, 0:n] / (n - 1)
    lum = 0.82 + 0.1 * (1 - np.hypot(x - 0.35, y - 0.3))
    return np.clip(np.stack([lum, lum, lum], -1), 0, 1)


def satin(n):
    """Ruban de satin (teinté par le modèle) : reflets fins dans le sens du ruban."""
    y = np.linspace(0, 1, n)[:, None]
    lum = 0.78 + 0.16 * np.sin(y * math.pi * 6) ** 2 + 0.05 * (bruit(n, n, 4, 2) - 0.5)
    lum = np.repeat(lum, n, 1) if lum.shape[1] == 1 else lum
    return np.clip(np.stack([lum, lum, lum], -1), 0, 1)


def main():
    A = np.zeros((1024, 1024, 3))
    G = np.zeros((1024, 1024, 3))
    ecrire(A, (0, 0), aiguilles(512))
    ecrire(A, (512, 0), neige(512))
    ecrire(A, (0, 512), papier(256, (172, 16, 30), lambda d, x, y, p: etoile(d, x, y, p * 0.22, p * 0.09, (240, 196, 84))))
    ecrire(A, (256, 512), brosse(256, 256, (236, 186, 74), 0.5))
    ecrire(A, (512, 512), papier(256, (18, 96, 48), lambda d, x, y, p: flocon(d, x, y, p * 0.26, (236, 244, 240), 3)))
    ecrire(A, (768, 512), face_cadenas(256))
    ecrire(A, (0, 768), papier(256, (26, 64, 150), lambda d, x, y, p: etoile(d, x, y, p * 0.2, p * 0.08, (214, 224, 236))))
    ecrire(A, (256, 768), papier_rayures(256))
    ecrire(A, (512, 768), ecorce(256))
    ecrire(A, (768, 768), verre(128))
    ecrire(A, (896, 768), tuile((22, 20, 26), 128, 128) * (0.85 + 0.3 * bruit(128, 128, 16, 2))[..., None])
    ecrire(A, (768, 896), satin(128))
    blanc = tuile((255, 255, 255), 128, 128)
    ecrire(A, (896, 896), blanc)
    ecrire(G, (896, 896), blanc)
    out = RES / "textures/crate"
    Image.fromarray((np.clip(A, 0, 1) * 255).astype(np.uint8)).save(out / "noel.png")
    Image.fromarray((np.clip(G, 0, 1) * 255).astype(np.uint8)).save(out / "noel_glow.png")
    part = Image.fromarray((np.clip(A[0:512, 0:512], 0, 1) * 255).astype(np.uint8)).resize((16, 16), Image.LANCZOS)
    part.save(RES / "textures/block/noel_particle.png")
    print("écrit : textures/crate/noel.png, noel_glow.png, textures/block/noel_particle.png")


if __name__ == "__main__":
    main()
