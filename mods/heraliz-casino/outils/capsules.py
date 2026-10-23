"""Icônes des capsules du casino (objets heralizcasino:jeton_*) : capsules de bouteille en métal, vues de trois quarts.

Écrit les 6 textures 64 x 64 de textures/item/ (jeton_1, jeton_5, jeton_10 en usage ; 100 à 10000 : anciennes capsules), dessinées 8 fois plus grandes puis réduites
(bords lisses). Une couleur et une marque par valeur. Jamais retouchées à la main : on modifie ce script.

    python mods/heraliz-casino/outils/capsules.py               écrit les textures
    python mods/heraliz-casino/outils/capsules.py --apercu F.png  planche d'aperçu (grandes et en taille réelle)
"""
import argparse
import math
from pathlib import Path

import numpy as np
from PIL import Image

ICI = Path(__file__).resolve().parent
SORTIE = ICI.parent / "src/client/resources/assets/heralizcasino/textures/item"

TAILLE, S = 64, 8
N = TAILLE * S

# valeur : (couleur du dessus, couleur de la marque, marque)
CAPSULES = {
    "jeton_1": ((168, 172, 178), (110, 114, 120), "anneau"),
    "jeton_5": ((52, 140, 72), (236, 240, 220), "double"),
    "jeton_10": ((196, 46, 40), (245, 232, 210), "etoile"),
    "jeton_100": ((38, 92, 176), (240, 240, 240), "double"),
    "jeton_1000": ((214, 168, 62), (120, 70, 20), "etoile"),
    "jeton_10000": ((26, 26, 30), (232, 190, 80), "couronne"),
}


def _etoile(px, py, cx, cy, r):
    """Masque d'une étoile à 5 branches de rayon r."""
    a = np.arctan2(py - cy, px - cx) + math.pi / 2
    d = np.hypot(px - cx, py - cy)
    k = (a % (2 * math.pi / 5)) / (2 * math.pi / 5)
    lim = r * (0.45 + 0.55 * np.abs(1 - 2 * k) ** 1.6)
    return d <= lim


def capsule(dessus, couleur_marque, marque):
    y, x = np.mgrid[0:N, 0:N].astype(np.float32) + 0.5
    cx, cy = N / 2, N * 0.43
    rx, ry = N * 0.40, N * 0.27          # dessus, ellipse (vue de trois quarts)
    jupe = N * 0.10                      # hauteur de la jupe crantée
    dents = 21
    img = np.zeros((N, N, 4), np.float32)

    # jupe : empilement d'ellipses de cy à cy + jupe, bord cranté
    for i in range(int(jupe), -1, -1):
        oy = cy + i
        dx, dy = (x - cx) / rx, (y - oy) / ry
        th = np.arctan2(dy, dx)
        cran = 1 + 0.075 * np.abs(np.cos(dents * th / 2)) ** 0.7 * (0.6 + 0.4 * i / jupe)
        m = dx * dx + dy * dy <= cran * cran
        m &= dy > -0.2                      # seulement la moitié avant se voit
        flute = 0.55 + 0.45 * np.cos(dents * th) ** 2
        lum = (0.35 + 0.65 * (1 - i / jupe)) * flute * (0.75 + 0.25 * np.sin(th))
        col = np.array(dessus, np.float32) * 0.55 + 70
        for c in range(3):
            img[..., c] = np.where(m, np.clip(col[c] * lum, 0, 255), img[..., c])
        img[..., 3] = np.where(m, 255, img[..., 3])

    # dessus bombé
    dx, dy = (x - cx) / rx, (y - cy) / ry
    r2 = dx * dx + dy * dy
    m = r2 <= 0.92
    lum = 0.78 + 0.32 * (1 - r2) - 0.18 * dy + 0.12 * -dx
    base = np.array(dessus, np.float32)
    for c in range(3):
        img[..., c] = np.where(m, np.clip(base[c] * lum, 0, 255), img[..., c])
    img[..., 3] = np.where(m, 255, img[..., 3])
    # bourrelet du bord du dessus
    bord = (r2 > 0.78) & (r2 <= 0.92)
    for c in range(3):
        img[..., c] = np.where(bord, np.clip(base[c] * (lum + 0.18), 0, 255), img[..., c])

    # marque imprimée (vue écrasée comme le dessus)
    px, py = x, cy + (y - cy) * (rx / ry)
    cm = np.array(couleur_marque, np.float32)
    if marque == "anneau":
        d = np.hypot(px - cx, py - cy) / rx
        mm = (d > 0.46) & (d < 0.56)
    elif marque == "double":
        d = np.hypot(px - cx, py - cy) / rx
        mm = ((d > 0.50) & (d < 0.58)) | (d < 0.30)
    elif marque == "etoile":
        mm = _etoile(px, py, cx, cy, rx * 0.46)
    else:  # couronne : anneau + étoile
        d = np.hypot(px - cx, py - cy) / rx
        mm = ((d > 0.58) & (d < 0.64)) | _etoile(px, py, cx, cy, rx * 0.40)
    mm &= m
    for c in range(3):
        img[..., c] = np.where(mm, np.clip(cm[c] * (lum * 0.9 + 0.1), 0, 255), img[..., c])

    # reflet
    hl = np.exp(-(((x - (cx - rx * 0.35)) / (rx * 0.22)) ** 2 + ((y - (cy - ry * 0.45)) / (ry * 0.18)) ** 2))
    for c in range(3):
        img[..., c] = np.where(m, np.clip(img[..., c] + 70 * hl, 0, 255), img[..., c])

    # ombre portée douce sous la capsule
    sh = np.exp(-((((x - cx) / (rx * 1.05)) ** 2 + ((y - (cy + jupe + ry * 0.55)) / (ry * 0.35)) ** 2)) ** 2)
    a_sh = (sh * 70).astype(np.float32)
    vide = img[..., 3] == 0
    img[..., 3] = np.where(vide, a_sh, img[..., 3])
    grande = Image.fromarray(img.astype(np.uint8), "RGBA")
    # réduction en alpha prémultiplié (pas de liseré sombre)
    arr = np.asarray(grande).astype(np.float32)
    arr[..., :3] *= arr[..., 3:4] / 255
    pre = Image.fromarray(arr.astype(np.uint8), "RGBA").resize((TAILLE, TAILLE), Image.LANCZOS)
    out = np.asarray(pre).astype(np.float32)
    a = out[..., 3:4]
    out[..., :3] = np.where(a > 0, np.clip(out[..., :3] * 255 / np.maximum(a, 1), 0, 255), 0)
    return Image.fromarray(out.astype(np.uint8), "RGBA")


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--apercu")
    a = p.parse_args()
    images = {nom: capsule(*v) for nom, v in CAPSULES.items()}
    if a.apercu:
        planche = Image.new("RGBA", (5 * 272, 340), (24, 26, 32, 255))
        for i, im in enumerate(images.values()):
            planche.alpha_composite(im.resize((256, 256), Image.NEAREST), (8 + i * 272, 8))
            planche.alpha_composite(im, (8 + i * 272 + 96, 272))
            planche.alpha_composite(im.resize((32, 32), Image.LANCZOS), (8 + i * 272 + 180, 288))
        planche.save(a.apercu)
        print("aperçu :", a.apercu)
        return
    SORTIE.mkdir(parents=True, exist_ok=True)
    for nom, im in images.items():
        im.save(SORTIE / f"{nom}.png")
        print("écrit", nom)


if __name__ == "__main__":
    main()
