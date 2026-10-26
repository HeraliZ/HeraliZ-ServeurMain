"""Textures de la caisse des emotes (« scène de spectacle ») : atlas 1024 x 1024 et son calque lumineux.

    python modules/boutique/outils/textures_spectacle.py

Écrit src/client/resources/assets/heralizcrates/textures/crate/spectacle.png et spectacle_glow.png, et la texture de
particules du bloc (textures/block/spectacle_particle.png). Cases de l'atlas : celles de client/render/mesh/Mat.java.
Jamais retouchées à la main : on modifie ce script et on le relance. Besoin : numpy et Pillow.
"""
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

RACINE = Path(__file__).resolve().parents[1]
RES = RACINE / "src/client/resources/assets/heralizcrates"
POLICE = RACINE.parents[1] / "launcher/app/assets/fonts/DMSans-Bold.ttf"
R = np.random.default_rng(1307)


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


def tuile(couleur, w, h):
    return np.ones((h, w, 3)) * np.array(couleur) / 255


def velours(w, h):
    """Velours rouge : plis verticaux doux, reflets satinés, grain."""
    x = np.linspace(0, 1, w)
    plis = 0.5 + 0.5 * np.sin(x * np.pi * 2 * 4 + 0.6 * np.sin(x * 9))
    plis = plis[None, :] ** 1.6
    grain = bruit(w, h, 64, 2)
    lum = 0.55 + 0.35 * plis + 0.12 * (grain - 0.5)
    lum = lum * (0.92 + 0.08 * bruit(w, h, 3, 3))
    base = np.array([150, 14, 32]) / 255
    hi = np.array([230, 60, 80]) / 255
    t = np.clip((lum - 0.75) * 3, 0, 1)[..., None]
    return (base * lum[..., None]) * (1 - t) + hi * t * lum[..., None]


def laque(w, h):
    """Laque noire de scène, reflets très doux, paillettes dorées rares."""
    v = 0.07 + 0.05 * bruit(w, h, 2, 3)
    img = np.stack([v * 0.95, v * 0.9, v * 1.15], -1)
    n = int(w * h * 0.0012)
    ys, xs = R.integers(0, h, n), R.integers(0, w, n)
    img[ys, xs] = np.array([0.85, 0.68, 0.3]) * R.uniform(0.4, 1.0, (n, 1))
    return img


def brosse(w, h, base, var):
    """Métal brossé horizontal."""
    lignes = R.random((h, 1)) * 0.6 + bruit(1, h, h // 4, 1) * 0.4
    s = np.repeat(lignes, w, 1)
    s = np.asarray(Image.fromarray((s * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur((6, 0)))) / 255
    lum = 0.8 + var * (s - 0.5) + 0.1 * (bruit(w, h, 2, 2) - 0.5)
    return np.clip(np.array(base)[None, None, :] / 255 * lum[..., None], 0, 1)


def ecrire(img, xy, arr):
    x, y = xy
    h, w = arr.shape[:2]
    img[y:y + h, x:x + w] = arr


def texte_centre(d, boite, txt, police, couleur):
    x0, y0, x1, y1 = boite
    l, t, r, b = d.textbbox((0, 0), txt, font=police)
    d.text(((x0 + x1 - (r - l)) / 2 - l, (y0 + y1 - (b - t)) / 2 - t), txt, font=police, fill=couleur)


def enseigne(n, w=1040, h=240):
    """Enseigne « EMOTES » : plaque sombre, cadre doré à ampoules, lettres néon roses, deux étoiles. Dessinée au format de
    l'enseigne (w x h, environ 13 x 3 px du modèle) puis ramenée au carré de la case de l'atlas (projection FIT)."""
    im = Image.new("RGB", (w, h), (16, 10, 26))
    gl = Image.new("RGB", (w, h), (0, 0, 0))
    d, g = ImageDraw.Draw(im), ImageDraw.Draw(gl)
    for i in range(14):
        d.rectangle([i, i, w - 1 - i, h - 1 - i], outline=(int(140 + 8 * i), int(100 + 7 * i), 36))
    nb = 26
    for k in range(nb):
        for y in (28, h - 29):
            x = 34 + k * (w - 68) / (nb - 1)
            d.ellipse([x - 8, y - 8, x + 8, y + 8], fill=(255, 238, 180))
            g.ellipse([x - 8, y - 8, x + 8, y + 8], fill=(255, 205, 110))
    police = ImageFont.truetype(str(POLICE), 120)
    boite = (150, 50, w - 150, h - 50)
    texte_centre(g, boite, "EMOTES", police, (255, 60, 190))
    halo = gl.filter(ImageFilter.GaussianBlur(10))
    im = Image.fromarray(np.clip(np.asarray(im, float) + np.asarray(halo, float) * 0.8, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    texte_centre(d, boite, "EMOTES", police, (255, 200, 240))
    for cx in (85, w - 85):
        etoile(d, cx, h / 2, 34, 14, (255, 210, 90))
        etoile(g, cx, h / 2, 34, 14, (255, 190, 60))
    im, gl = im.resize((n, n), Image.LANCZOS), gl.resize((n, n), Image.LANCZOS)
    return np.asarray(im) / 255, np.asarray(gl) / 255


def etoile(d, cx, cy, r1, r2, col):
    import math
    pts = []
    for k in range(10):
        a = -math.pi / 2 + k * math.pi / 5
        r = r1 if k % 2 == 0 else r2
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a)))
    d.polygon(pts, fill=col)


def face_cadenas(n):
    """Face du cadenas : laque rose framboise, liseré doré, étoile dorée."""
    im = Image.new("RGB", (n, n), (170, 20, 110))
    d = ImageDraw.Draw(im)
    for i in range(14):
        d.rectangle([i, i, n - 1 - i, n - 1 - i], outline=(int(200 + 3 * i), int(150 + 4 * i), 50))
    etoile(d, n / 2, n * 0.34, 46, 19, (255, 214, 96))
    a = np.asarray(im) / 255 * (0.9 + 0.12 * bruit(n, n, 3, 2)[..., None])
    return np.clip(a, 0, 1)


def dalle(n):
    """Dalle de piste de danse : verre dépoli clair, biseau, centre plus lumineux."""
    y, x = np.mgrid[0:n, 0:n] / (n - 1)
    bord = np.minimum(np.minimum(x, 1 - x), np.minimum(y, 1 - y))
    lum = 0.16 + 0.14 * np.clip(1 - np.hypot(x - 0.5, y - 0.5) * 1.6, 0, 1) + 0.04 * bruit(n, n, 8, 2)
    lum = np.where(bord < 0.05, 0.55, lum)
    lum = np.where(bord < 0.02, 0.08, lum)
    img = np.stack([lum * 0.95, lum * 0.9, lum * 1.1], -1)
    glow = np.clip(1 - np.hypot(x - 0.5, y - 0.5) * 1.5, 0, 1)[..., None] * np.array([0.08, 0.08, 0.1])
    glow = np.where((bord < 0.02)[..., None], 0, glow)
    return img, glow


def grille_hp(n):
    """Grille de haut-parleur : maille noire, membrane en cercles."""
    y, x = np.mgrid[0:n, 0:n]
    maille = ((x % 6 < 2) | (y % 6 < 2)).astype(float)
    r = np.hypot(x - n / 2, y - n / 2) / (n / 2)
    cone = 0.12 + 0.06 * np.cos(r * 30) * (r < 0.85)
    lum = np.where(maille > 0, 0.05, cone)
    lum = np.where(r > 0.9, 0.18, lum)
    return np.stack([lum, lum, lum * 1.1], -1)


def fond_etoile(w, h):
    """Velours violet nuit du fond de la loge, petites étoiles (lumineuses)."""
    v = 0.12 + 0.06 * bruit(w, h, 3, 3)
    img = np.stack([v * 1.1, v * 0.4, v * 1.7], -1)
    gl = np.zeros((h, w, 3))
    for _ in range(70):
        x, y = R.integers(2, w - 2), R.integers(2, h - 2)
        c = np.array([1, 0.9, 0.7]) if R.random() < 0.5 else np.array([0.8, 0.85, 1])
        img[y - 1:y + 2, x] = c
        img[y, x - 1:x + 2] = c
        gl[y - 1:y + 2, x] = c * 0.9
        gl[y, x - 1:x + 2] = c * 0.9
    return img, gl


def miroirs(n):
    """Facettes de la boule à facettes : carrés argentés de luminosités variées, joints sombres, quelques éclats."""
    k = 8
    c = n // k
    img = np.zeros((n, n, 3))
    gl = np.zeros((n, n, 3))
    for i in range(k):
        for j in range(k):
            v = R.uniform(0.45, 1.0)
            tint = np.array([0.92, 0.94, 1.0]) * v
            img[i * c + 1:(i + 1) * c - 1, j * c + 1:(j + 1) * c - 1] = tint
            img[i * c + 1:i * c + 3, j * c + 1:(j + 1) * c - 1] = np.minimum(1, tint * 1.25)
            if R.random() < 0.12:
                gl[i * c + 1:(i + 1) * c - 1, j * c + 1:(j + 1) * c - 1] = 0.8
    return img, gl


def main():
    A = np.zeros((1024, 1024, 3))
    G = np.zeros((1024, 1024, 3))
    ecrire(A, (0, 0), velours(512, 512))
    ecrire(A, (512, 0), laque(512, 512))
    ecrire(A, (0, 512), brosse(256, 256, (232, 182, 72), 0.5))
    ecrire(A, (256, 512), brosse(256, 256, (196, 202, 212), 0.45))
    e, eg = enseigne(256)
    ecrire(A, (512, 512), e)
    ecrire(G, (512, 512), eg)
    ecrire(A, (768, 512), face_cadenas(256))
    dl, dg = dalle(256)
    ecrire(A, (0, 768), dl)
    ecrire(G, (0, 768), dg)
    ecrire(A, (256, 768), grille_hp(256))
    fe, fg = fond_etoile(256, 256)
    ecrire(A, (512, 768), fe)
    ecrire(G, (512, 768), fg)
    m, mg = miroirs(128)
    ecrire(A, (768, 768), m)
    ecrire(G, (768, 768), mg)
    ecrire(A, (896, 768), tuile((22, 20, 26), 128, 128) * (0.85 + 0.3 * bruit(128, 128, 16, 2))[..., None])
    neon = tuile((255, 225, 245), 128, 128)
    ecrire(A, (768, 896), neon)
    ecrire(G, (768, 896), neon)
    ecrire(A, (896, 896), tuile((255, 255, 255), 128, 128))
    ecrire(G, (896, 896), tuile((255, 255, 255), 128, 128))
    out = RES / "textures/crate"
    Image.fromarray((np.clip(A, 0, 1) * 255).astype(np.uint8)).save(out / "spectacle.png")
    Image.fromarray((np.clip(G, 0, 1) * 255).astype(np.uint8)).save(out / "spectacle_glow.png")
    part = Image.fromarray((np.clip(A[0:512, 0:512], 0, 1) * 255).astype(np.uint8)).resize((16, 16), Image.LANCZOS)
    part.save(RES / "textures/block/spectacle_particle.png")
    print("écrit : textures/crate/spectacle.png, spectacle_glow.png, textures/block/spectacle_particle.png")


if __name__ == "__main__":
    main()
