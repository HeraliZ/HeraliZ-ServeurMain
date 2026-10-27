"""Textures du distributeur de capsules (« ATM », casino) : tuiles peintes dans la moitié basse, libre, de l'atlas du
guichet (textures/machine/caisse.png et caisse_glow.png : y de 512 à 1023), jamais retouchées à la main.

    python modules/boutique/outils/textures_atm.py DOSSIER             écrit caisse.png et caisse_glow.png dans DOSSIER
    python modules/boutique/outils/textures_atm.py --installer         les écrit dans le mod (après validation des rendus)
    python modules/boutique/outils/textures_atm.py DOSSIER --planche   en plus : planche.png (les tuiles agrandies)

Les tuiles du haut (guichet) ne changent pas : elles sont relues dans l'atlas du mod. Les zones doivent rester les mêmes
que les constantes R_* de AtmDesign.java (src/client/.../scene/machines). Les capsules sont reprises des
icônes des objets (textures/item/jeton_*.png). Textes en DM Sans (OFL), comme les interfaces.
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ICI = Path(__file__).resolve().parent
ASSETS = ICI.parent / "src/client/resources/assets/heralizcasino"
ATLAS = ASSETS / "textures/machine/caisse.png"
GLOW = ASSETS / "textures/machine/caisse_glow.png"
GRAS = ASSETS / "font/dmsans_bold.ttf"
MOYEN = ASSETS / "font/dmsans_medium.ttf"

# zones (x, y, largeur, hauteur) : les mêmes que AtmDesign.java
R_PAINT = (0, 512, 256, 256)       # tôle peinte du distributeur (bleu-gris de banque, écaillée)
R_FASCIA = (256, 512, 256, 256)    # inox brossé de la façade
R_SCREEN = (512, 512, 256, 192)    # écran (phosphore vert), aussi dans le calque lumineux
R_KEYPAD = (768, 512, 128, 128)    # clavier 4 x 4
R_DARK = (896, 512, 128, 128)      # plastique noir granuleux (bouches, joints)
R_IN = (768, 640, 128, 64)         # plaque « dépôt » (flèche vers le bas, capsule)
R_OUT = (896, 640, 128, 64)        # plaque « retrait » (flèche vers le haut)
R_CAP10 = (512, 704, 64, 64)       # dessus des capsules (icônes des objets)
R_CAP5 = (576, 704, 64, 64)
R_CAP1 = (640, 704, 64, 64)
R_CRIMP = (704, 704, 64, 64)       # tranche crantée des capsules
R_SIGN = (0, 768, 512, 128)        # caisson lumineux « ATM »
R_SIDE = (512, 768, 512, 128)      # plaque peinte au pochoir sur le flanc
R_LED = (0, 896, 64, 64)           # voyant vert
R_GLASS = (64, 896, 128, 128)      # vitre de l'écran (reflets, rayures), translucide
R_CHAIN = (192, 896, 64, 64)       # acier de la chaîne (mat, rouillé)

S = 2  # dessin deux fois plus grand puis réduit (bords lisses)


def rng(seed):
    return np.random.default_rng(seed)


def bruit(h, w, echelle, seed, octaves=4):
    """Bruit doux 0..1 (somme d'octaves de valeurs aléatoires agrandies)."""
    r = rng(seed)
    out = np.zeros((h, w), np.float32)
    amp, tot = 1.0, 0.0
    for o in range(octaves):
        gh, gw = max(2, int(h / echelle * 2 ** o)), max(2, int(w / echelle * 2 ** o))
        g = Image.fromarray((r.random((gh, gw)) * 255).astype(np.uint8)).resize((w, h), Image.BICUBIC)
        out += np.array(g, np.float32) / 255 * amp
        tot += amp
        amp *= 0.5
    return out / tot


def couleur(rgb, h, w):
    return np.ones((h, w, 3), np.float32) * np.array(rgb, np.float32)


def fondre(base, dessus, masque):
    m = masque[..., None]
    return base * (1 - m) + dessus * m


def rayures(img, n, seed, clair=(205, 210, 214), alpha=0.55, longueur=(6, 40)):
    h, w = img.shape[:2]
    r = rng(seed)
    calque = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(calque)
    for _ in range(n):
        x, y = r.random() * w, r.random() * h
        a = r.random() * math.pi
        L = r.uniform(*longueur)
        d.line([(x, y), (x + math.cos(a) * L, y + math.sin(a) * L)], fill=int(255 * r.uniform(0.3, 1)), width=1)
    m = np.array(calque, np.float32) / 255 * alpha
    return fondre(img, couleur(clair, h, w), m)


def tuile_rouille(w, h, seed):
    """Rouille reprise de la tuile du guichet (R_RUST), répétée à la taille voulue."""
    src = np.array(Image.open(ATLAS).convert("RGB").crop((384, 256, 512, 384)), np.float32)
    reps = (math.ceil(h / 128), math.ceil(w / 128))
    t = np.tile(src, (reps[0], reps[1], 1))[:h, :w]
    return np.roll(t, (seed * 37) % 128, axis=1)


# ---------------------------------------------------------------- tuiles
def peinture(w, h):
    """Tôle de distributeur : peinture bleu-gris de banque, salie, écaillée jusqu'à l'acier et la rouille."""
    n = bruit(h, w, 48, 11)
    base = couleur((84, 101, 116), h, w) * (0.82 + 0.3 * n[..., None])
    sale = bruit(h, w, 20, 12)
    base = fondre(base, couleur((58, 54, 46), h, w), np.clip((sale - 0.55) * 1.6, 0, 0.55))
    # coulures de crasse vers le bas
    coul = np.array(Image.fromarray((rng(13).random((1, w)) * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR), np.float32) / 255
    base = fondre(base, couleur((40, 38, 33), h, w), np.clip((coul - 0.78) * 2.0, 0, 0.16) * np.linspace(0.2, 1, h)[:, None])
    # écailles : acier nu, puis rouille au cœur
    ec = bruit(h, w, 26, 14, 5)
    acier = couleur((150, 152, 150), h, w) * (0.85 + 0.25 * bruit(h, w, 8, 15)[..., None])
    base = fondre(base, acier, np.clip((ec - 0.66) * 9, 0, 1))
    base = fondre(base, tuile_rouille(w, h, 3), np.clip((ec - 0.72) * 7, 0, 1))
    base = rayures(base, 70, 16, alpha=0.35)
    return base


def inox(w, h):
    """Inox brossé (traits horizontaux), traces de doigts, rayures."""
    r = rng(21)
    lignes = np.array(Image.fromarray((r.random((h, 6)) * 255).astype(np.uint8)).resize((w, h), Image.BILINEAR), np.float32) / 255
    base = couleur((168, 172, 174), h, w) * (0.86 + 0.18 * lignes[..., None]) * (0.9 + 0.12 * bruit(h, w, 60, 22)[..., None])
    doigts = bruit(h, w, 14, 23)
    base = fondre(base, couleur((120, 120, 116), h, w), np.clip((doigts - 0.62) * 2, 0, 0.35))
    return rayures(base, 50, 24, clair=(225, 228, 230), alpha=0.45)


def plastique_noir(w, h):
    n = bruit(h, w, 6, 31, 3)
    return couleur((30, 31, 33), h, w) * (0.75 + 0.5 * n[..., None])


def police(chemin, taille):
    return ImageFont.truetype(str(chemin), int(taille))


def capsule_rgba(valeur, taille):
    return Image.open(ASSETS / ("textures/item/jeton_%d.png" % valeur)).convert("RGBA").resize((taille, taille), Image.LANCZOS)


def fleche(d, cx, cy, s, bas, c):
    """Flèche pleine vers le bas (bas=True) ou vers le haut, centrée en (cx, cy), taille s."""
    k = 1 if bas else -1
    d.polygon([(cx - s * 0.28, cy - k * s * 0.5), (cx + s * 0.28, cy - k * s * 0.5), (cx + s * 0.28, cy), (cx + s * 0.55, cy),
               (cx, cy + k * s * 0.55), (cx - s * 0.55, cy), (cx - s * 0.28, cy)], fill=c)


def ecran(w, h):
    """Écran au repos : fond phosphore, place du solde (écrit en jeu par AtmDesign : celui du joueur qui regarde), deux
    pastilles (dépôt, retrait) ; sans mots à traduire."""
    W, H = w * S, h * S
    im = Image.new("RGB", (W, H), (6, 18, 10))
    d = ImageDraw.Draw(im)
    vert, pale, sombre = (110, 255, 150), (60, 170, 95), (22, 70, 40)
    # cadre et bandeau du haut
    d.rectangle([10 * S, 10 * S, W - 10 * S, 38 * S], fill=(14, 52, 28))
    f = police(GRAS, 17 * S)
    d.text((18 * S, 24 * S), "CAPSULES", font=f, fill=vert, anchor="lm")
    d.text((W - 18 * S, 24 * S), "ATM", font=f, fill=pale, anchor="rm")
    # solde
    cap = capsule_rgba(10, 34 * S)
    im.paste(cap, (22 * S, 58 * S), cap)
    d.line([(18 * S, 106 * S), (W - 18 * S, 106 * S)], fill=sombre, width=2 * S)
    # deux pastilles : dépôt (flèche vers le bas), retrait (flèche vers le haut)
    for i, (bas, c) in enumerate(((True, vert), (False, (255, 200, 90)))):
        x0 = 18 * S + i * (W - 36 * S) // 2 + (4 * S if i else 0)
        x1 = x0 + (W - 36 * S) // 2 - 4 * S
        d.rounded_rectangle([x0, 116 * S, x1, 170 * S], radius=8 * S, outline=c, width=3 * S, fill=(10, 34, 20))
        fleche(d, x0 + 26 * S, 143 * S, 30 * S, bas, c)
        cap = capsule_rgba(5 if bas else 1, 30 * S)
        im.paste(cap, (x0 + 48 * S, 128 * S), cap)
    a = np.array(im.resize((w, h), Image.LANCZOS), np.float32)
    # lignes de balayage et coins assombris (tube cathodique)
    a *= (0.82 + 0.18 * (np.arange(h) % 2))[:, None, None]
    yy, xx = np.mgrid[0:h, 0:w]
    v = ((xx - w / 2) / (w / 2)) ** 2 + ((yy - h / 2) / (h / 2)) ** 2
    a *= np.clip(1.15 - 0.45 * v, 0, 1)[..., None]
    return a


def clavier(w, h):
    """Clavier 4 x 4 : chiffres gravés sur touches inox, colonne de droite rouge / jaune / vert."""
    W, H = w * S, h * S
    fond = Image.fromarray(plastique_noir(W, H).clip(0, 255).astype(np.uint8))
    d = ImageDraw.Draw(fond)
    f = police(GRAS, 15 * S)
    touches = [["1", "2", "3", "x"], ["4", "5", "6", "<"], ["7", "8", "9", "o"], ["*", "0", "#", ""]]
    cote = 32 * S
    for j, ligne in enumerate(touches):
        for i, t in enumerate(ligne):
            x0, y0 = i * cote + 3 * S, j * cote + 3 * S
            x1, y1 = x0 + cote - 6 * S, y0 + cote - 6 * S
            c = {"x": (196, 52, 44), "<": (226, 186, 52), "o": (58, 160, 82)}.get(t, (176, 180, 182))
            d.rounded_rectangle([x0, y0, x1, y1], radius=4 * S, fill=c)
            d.rounded_rectangle([x0, y0, x1, y0 + 5 * S], radius=4 * S, fill=tuple(min(255, k + 30) for k in c))
            cx, cy = (x0 + x1) / 2, (y0 + y1) / 2 + S
            if t == "x":
                d.line([(cx - 6 * S, cy - 6 * S), (cx + 6 * S, cy + 6 * S)], fill=(250, 240, 236), width=3 * S)
                d.line([(cx - 6 * S, cy + 6 * S), (cx + 6 * S, cy - 6 * S)], fill=(250, 240, 236), width=3 * S)
            elif t == "<":
                d.polygon([(cx - 7 * S, cy), (cx + 5 * S, cy - 7 * S), (cx + 5 * S, cy + 7 * S)], fill=(60, 48, 10))
            elif t == "o":
                d.ellipse([cx - 6 * S, cy - 6 * S, cx + 6 * S, cy + 6 * S], outline=(240, 250, 240), width=3 * S)
            elif t:
                d.text((cx, cy), t, font=f, fill=(34, 36, 38), anchor="mm")
    a = np.array(fond.resize((w, h), Image.LANCZOS), np.float32)
    return rayures(a, 25, 41, alpha=0.3)


def plaque(w, h, bas):
    """Plaque d'acier vissée : flèche (vert : dépôt, ambre : retrait) et capsule."""
    W, H = w * S, h * S
    im = Image.fromarray(inox(W, H).clip(0, 255).astype(np.uint8))
    d = ImageDraw.Draw(im)
    c = (58, 170, 86) if bas else (232, 160, 40)
    d.rounded_rectangle([4 * S, 4 * S, W - 4 * S, H - 4 * S], radius=6 * S, outline=(70, 72, 74), width=2 * S)
    fleche(d, 34 * S, H / 2, 40 * S, bas, c)
    cap = capsule_rgba(10 if bas else 5, 40 * S)
    im.paste(cap, (70 * S, int(H / 2 - 20 * S)), cap)
    for x, y in ((10 * S, 10 * S), (W - 10 * S, 10 * S), (10 * S, H - 10 * S), (W - 10 * S, H - 10 * S)):
        d.ellipse([x - 3 * S, y - 3 * S, x + 3 * S, y + 3 * S], fill=(90, 92, 94))
    return np.array(im.resize((w, h), Image.LANCZOS), np.float32)


def capsule_dessus(valeur, w, h):
    im = Image.new("RGB", (w, h), (60, 60, 60))
    cap = capsule_rgba(valeur, w)
    im.paste(cap, (0, 0), cap)
    # le bord de l'icône (fond) prend la couleur du bord de la capsule
    a = np.array(im, np.float32)
    al = np.array(cap, np.float32)[..., 3:4] / 255
    moy = (a * al).sum((0, 1)) / max(1, al.sum())
    return a * al + moy * 0.8 * (1 - al)


def cran(w, h):
    """Tranche crantée d'une capsule (métal clair, ondulé)."""
    xx = np.arange(w)
    onde = 0.75 + 0.25 * np.cos(xx / w * math.pi * 2 * 6)
    base = couleur((196, 192, 180), h, w) * onde[None, :, None]
    return base * (0.85 + 0.15 * bruit(h, w, 8, 51)[..., None])


def enseigne(w, h):
    """Caisson lumineux : plexi bleu, « ATM » en blanc, capsule rouge, rayé et sale."""
    W, H = w * S, h * S
    yy = np.linspace(0, 1, H)[:, None, None]
    fond = (np.array((22, 70, 150), np.float32) * (1 - yy) + np.array((12, 38, 96), np.float32) * yy) * np.ones((H, W, 1))
    im = Image.fromarray(fond.astype(np.uint8))
    d = ImageDraw.Draw(im)
    d.rectangle([6 * S, 6 * S, W - 6 * S, H - 6 * S], outline=(140, 190, 255), width=3 * S)
    d.text((W * 0.40, H * 0.47), "ATM", font=police(GRAS, 92 * S), fill=(248, 250, 255), anchor="mm")
    cap = capsule_rgba(10, 84 * S)
    im.paste(cap, (int(W * 0.75), int(H / 2 - 42 * S)), cap)
    d.text((W * 0.40, H * 0.86), "CAPSULES", font=police(GRAS, 16 * S), fill=(170, 205, 255), anchor="mm")
    a = np.array(im.resize((w, h), Image.LANCZOS), np.float32)
    sale = bruit(h, w, 30, 61)
    a = fondre(a, couleur((40, 36, 30), h, w), np.clip((sale - 0.6) * 1.4, 0, 0.45))
    a = rayures(a, 60, 62, clair=(200, 215, 235), alpha=0.25, longueur=(5, 30))
    # fissure réparée au ruban adhésif, coin droit
    tape = Image.new("L", (w, h), 0)
    ImageDraw.Draw(tape).polygon([(w - 70, 0), (w - 52, 0), (w - 96, h), (w - 114, h)], fill=200)
    a = fondre(a, couleur((150, 146, 128), h, w), np.array(tape, np.float32) / 255 * 0.85)
    return a


def enseigne_glow(w, h):
    """Calque lumineux : lettres et capsule vives, plexi doucement allumé, ruban opaque."""
    W, H = w * S, h * S
    im = Image.new("RGB", (W, H), (10, 40, 95))
    d = ImageDraw.Draw(im)
    d.text((W * 0.40, H * 0.47), "ATM", font=police(GRAS, 92 * S), fill=(255, 255, 255), anchor="mm")
    cap = capsule_rgba(10, 84 * S)
    im.paste(cap, (int(W * 0.75), int(H / 2 - 42 * S)), cap)
    d.text((W * 0.40, H * 0.86), "CAPSULES", font=police(GRAS, 16 * S), fill=(150, 190, 255), anchor="mm")
    a = np.array(im.resize((w, h), Image.LANCZOS), np.float32)
    tape = Image.new("L", (w, h), 0)
    ImageDraw.Draw(tape).polygon([(w - 70, 0), (w - 52, 0), (w - 96, h), (w - 114, h)], fill=255)
    return a * (1 - np.array(tape, np.float32)[..., None] / 255 * 0.9)


def flanc(w, h):
    """Plaque de tôle peinte au pochoir (jaune) : capsule et flèche vers l'écran ; vis aux coins."""
    W, H = w * S, h * S
    base = Image.fromarray(peinture(W, H).clip(0, 255).astype(np.uint8))
    d = ImageDraw.Draw(base)
    jaune = (232, 196, 60)
    d.text((W * 0.42, H * 0.52), "CAPSULES", font=police(GRAS, 64 * S), fill=jaune, anchor="mm")
    fleche(d, W * 0.88, H * 0.5, 70 * S, False, jaune)
    a = np.array(base.resize((w, h), Image.LANCZOS), np.float32)
    trous = bruit(h, w, 4, 71, 2)  # peinture au pochoir : grains manquants
    a = fondre(a, peinture(w, h), np.clip((trous - 0.62) * 3, 0, 0.8) * (a[..., 0] > 150))
    return a


def led(w, h, allume):
    yy, xx = np.mgrid[0:h, 0:w]
    r = np.hypot(xx - w / 2, yy - h / 2) / (w / 2)
    c = couleur((70, 255, 120) if allume else (20, 70, 30), h, w)
    return c * np.clip(1.2 - r, 0.2, 1)[..., None]


def vitre(w, h):
    """Vitre : presque transparente, reflets en biais et rayures (canal alpha)."""
    a = np.zeros((h, w, 4), np.float32)
    a[..., :3] = 210
    yy, xx = np.mgrid[0:h, 0:w]
    reflet = np.clip(1 - np.abs(((xx + yy * 0.6) % (w * 0.9)) - w * 0.25) / (w * 0.08), 0, 1)
    al = 18 + 50 * reflet
    rx = Image.new("L", (w, h), 0)
    r = rng(81)
    dd = ImageDraw.Draw(rx)
    for _ in range(18):
        x, y = r.random() * w, r.random() * h
        dd.line([(x, y), (x + r.uniform(-20, 20), y + r.uniform(-6, 6))], fill=120, width=1)
    al += np.array(rx, np.float32)
    a[..., 3] = np.clip(al, 0, 255)
    return a


def chaine(w, h):
    base = couleur((96, 92, 86), h, w) * (0.8 + 0.3 * bruit(h, w, 10, 91)[..., None])
    return fondre(base, tuile_rouille(w, h, 5), np.clip((bruit(h, w, 12, 92) - 0.5) * 2.5, 0, 0.7))


# ---------------------------------------------------------------- assemblage
def poser(img, zone, a):
    x, y, w, h = zone
    if a.shape[2] == 3:
        a = np.concatenate([a, np.full(a.shape[:2] + (1,), 255, np.float32)], axis=2)
    img[y:y + h, x:x + w] = a.clip(0, 255)


def construire():
    atlas = np.array(Image.open(ATLAS).convert("RGBA"), np.float32)
    glow = np.array(Image.open(GLOW).convert("RGBA"), np.float32)
    atlas[512:] = (128, 128, 128, 255)
    glow[512:] = (0, 0, 0, 255)
    tuiles = {
        R_PAINT: peinture(256, 256), R_FASCIA: inox(256, 256), R_SCREEN: ecran(256, 192), R_KEYPAD: clavier(128, 128),
        R_DARK: plastique_noir(128, 128), R_IN: plaque(128, 64, True), R_OUT: plaque(128, 64, False),
        R_CAP10: capsule_dessus(10, 64, 64), R_CAP5: capsule_dessus(5, 64, 64), R_CAP1: capsule_dessus(1, 64, 64),
        R_CRIMP: cran(64, 64), R_SIGN: enseigne(512, 128), R_SIDE: flanc(512, 128), R_LED: led(64, 64, True),
        R_GLASS: vitre(128, 128), R_CHAIN: chaine(64, 64),
    }
    for z, a in tuiles.items():
        poser(atlas, z, a)
    # calque lumineux : écran, enseigne, voyant, chiffres du clavier faiblement rétroéclairés
    poser(glow, R_SCREEN, tuiles[R_SCREEN] * 1.0)
    poser(glow, R_SIGN, enseigne_glow(512, 128))
    poser(glow, R_LED, led(64, 64, True))
    poser(glow, R_KEYPAD, tuiles[R_KEYPAD] * 0.12)
    return (Image.fromarray(atlas.astype(np.uint8), "RGBA"), Image.fromarray(glow.astype(np.uint8), "RGBA"), tuiles)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("dossier", nargs="?")
    ap.add_argument("--installer", action="store_true")
    ap.add_argument("--planche", action="store_true")
    a = ap.parse_args()
    if not a.dossier and not a.installer:
        sys.exit("donner un DOSSIER, ou --installer")
    atlas, glow, tuiles = construire()
    sortie = ATLAS.parent if a.installer else Path(a.dossier)
    sortie.mkdir(parents=True, exist_ok=True)
    atlas.save(sortie / "caisse.png", optimize=True)
    glow.save(sortie / "caisse_glow.png", optimize=True)
    print("écrit", sortie / "caisse.png", "et caisse_glow.png")
    if a.installer:
        # particules du bloc (casse, pas) : un morceau de la tôle peinte
        p = ASSETS / "textures/block/atm_particle.png"
        Image.fromarray(tuiles[R_PAINT][:64, :64].clip(0, 255).astype(np.uint8)).resize((16, 16), Image.LANCZOS).save(p, optimize=True)
        print("écrit", p)
    if a.planche and a.dossier:
        bas = atlas.crop((0, 512, 1024, 1024)).resize((2048, 1024), Image.NEAREST)
        fond = Image.new("RGBA", bas.size, (40, 40, 40, 255))
        fond.alpha_composite(bas)
        fond.convert("RGB").save(Path(a.dossier) / "planche.png")
        print("écrit", Path(a.dossier) / "planche.png")


if __name__ == "__main__":
    main()
