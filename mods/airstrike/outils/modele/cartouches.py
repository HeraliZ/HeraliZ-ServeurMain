#!/usr/bin/env python3
"""Cartouches de frappe de l'Airstrike : Légère, Lourde, Spéciale. Génère pour chacune le modèle d'objet
(models/item/cartridge_<niveau>.json) et sa texture HD (textures/item/cartridge_<niveau>.png).

    python mods/airstrike/outils/modele/cartouches.py               écrit dans le mod
    python mods/airstrike/outils/modele/cartouches.py <dossier>     écrit ailleurs (aperçu avant validation)

Besoin de Pillow et numpy (pip install pillow numpy).

Même gabarit pour les trois (elle doivent toutes entrer dans la baie des jumelles) : une coque à pans coupés de
9 x 12,6 x 5 pixels, le connecteur et ses contacts en haut, une semelle en bas, des poignées sur les côtés. Ce qui les
distingue d'un coup d'œil : la matière (polymère olive, acier, carbone), la couleur du niveau (celle des raretés du
serveur : peu commun, rare, légendaire) en bandeau, le nombre de chevrons (1, 2, 3) et la silhouette (cerclages de la
Lourde, renforts d'angle et hublot lumineux de la Spéciale).

Repère : celui des modèles d'objet du jeu (pixels 0..16). Texture de 512 x 512 : 16 texels par pixel du modèle.
La face « south » (+z) porte l'étiquette : c'est elle qu'on voit dans l'inventaire et qui regarde vers le haut dans la
baie des jumelles. Relancer le script après toute retouche, puis refabriquer le mod (jar -client).
"""
import json
import math
import sys
import zlib
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ICI = Path(__file__).resolve().parent
MOD = ICI.parents[1]
ASSETS = MOD / "src" / "client" / "resources" / "assets" / "airstrike"
RACINE = MOD.parents[1]
POLICE = RACINE / "mods" / "heraliz-interface" / "src" / "main" / "resources" / "assets" / "heraliz_interface" / "font"

TAILLE = 512                # texture
D = 16                      # texels par pixel du modèle
UV = TAILLE / 16.0          # texels par unité d'UV (les UV des modèles vont de 0 à 16)

NIVEAUX = {
    "light": dict(nom="LÉGÈRE", code="AS-1", couleur=(0x5C, 0xBF, 0x4A), chevrons=1, matiere="polymere"),
    "heavy": dict(nom="LOURDE", code="AS-2", couleur=(0x3F, 0x8C, 0xFF), chevrons=2, matiere="acier"),
    "special": dict(nom="SPÉCIALE", code="AS-3", couleur=(0xFF, 0x9B, 0x1F), chevrons=3, matiere="carbone"),
}

# affichage (repris des anciennes recharges : même place dans la main et dans l'inventaire)
AFFICHAGE = {
    "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.0, 0.5], "scale": [0.42, 0.42, 0.42]},
    "thirdperson_lefthand": {"rotation": [75, 45, 0], "translation": [0, 2.0, 0.5], "scale": [0.42, 0.42, 0.42]},
    "firstperson_righthand": {"rotation": [0, 30, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "firstperson_lefthand": {"rotation": [0, 30, 0], "translation": [0, 2, 0], "scale": [0.45, 0.45, 0.45]},
    "gui": {"rotation": [20, -25, 0], "translation": [0, 0, 0], "scale": [0.95, 0.95, 0.95]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.4, 0.4, 0.4]},
    "fixed": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
}


# ------------------------------------------------------------------ petits outils d'image
def rng(graine):
    return np.random.default_rng(graine)


def flou(a, n=1):
    for _ in range(n):
        p = np.pad(a, 1, mode="edge")
        a = sum(p[1 + dy:1 + dy + a.shape[0], 1 + dx:1 + dx + a.shape[1]] for dy in (-1, 0, 1) for dx in (-1, 0, 1)) / 9
    return a


def bruit(h, w, graine, lissage=1):
    return flou(rng(graine).normal(0, 1, (h, w)), lissage)


def bruit_bas(h, w, graine, pas=8):
    """Taches douces (bruit grossier agrandi)."""
    g = rng(graine).normal(0, 1, (h // pas + 2, w // pas + 2))
    im = Image.fromarray(((g - g.min()) / (np.ptp(g) + 1e-9) * 255).astype(np.uint8)).resize((w + 2 * pas, h + 2 * pas), Image.BICUBIC)
    a = np.asarray(im, float)[pas:pas + h, pas:pas + w] / 255.0
    return a - 0.5


def rgba(rgb, h, w):
    a = np.zeros((h, w, 4))
    a[..., :3] = rgb
    a[..., 3] = 255
    return a


def fondu(a, masque, rgb, k=1.0):
    """Mélange la couleur rgb là où masque (0..1) est non nul."""
    m = np.clip(masque, 0, 1)[..., None] * k
    a[..., :3] = a[..., :3] * (1 - m) + np.array(rgb, float) * m


def police(taille, gras=True):
    return ImageFont.truetype(str(POLICE / ("dmsans_bold.ttf" if gras else "dmsans_medium.ttf")), taille)


def texte(a, s, x, y, taille, rgb, alpha=1.0, gras=True, centre=False, espace=0.0, droite=False):
    """Écrit s (capitales espacées si espace > 0) ; x, y en texels, y = haut du texte (x = bord droit si droite)."""
    h, w = a.shape[:2]
    f = police(taille, gras)
    largeur = sum(f.getlength(c) for c in s) + espace * (len(s) - 1)
    if centre:
        x = x - largeur / 2
    elif droite:
        x = x - largeur
    calque = Image.new("L", (w, h), 0)
    d = ImageDraw.Draw(calque)
    cx = x
    for c in s:
        d.text((cx, y), c, font=f, fill=255)
        cx += f.getlength(c) + espace
    fondu(a, np.asarray(calque, float) / 255.0 * alpha, rgb)


def masque_poly(h, w, pts, sur=4):
    """Polygone anti-crénelé (sur-échantillonné)."""
    im = Image.new("L", (w * sur, h * sur), 0)
    ImageDraw.Draw(im).polygon([(x * sur, y * sur) for x, y in pts], fill=255)
    return np.asarray(im.resize((w, h), Image.LANCZOS), float) / 255.0


def masque_ellipse(h, w, box, sur=4, epaisseur=None):
    im = Image.new("L", (w * sur, h * sur), 0)
    d = ImageDraw.Draw(im)
    b = [c * sur for c in box]
    if epaisseur:
        d.ellipse(b, outline=255, width=int(epaisseur * sur))
    else:
        d.ellipse(b, fill=255)
    return np.asarray(im.resize((w, h), Image.LANCZOS), float) / 255.0


def bords(a, clair=0.10, sombre=0.22, larg=2):
    """Biseau peint : liseré clair en haut et à gauche, ombre en bas et à droite, coins assombris."""
    h, w = a.shape[:2]
    for i in range(larg):
        k = 1 - i / larg
        a[i, :, :3] = a[i, :, :3] * (1 - clair * k) + 255 * clair * k
        a[:, i, :3] = a[:, i, :3] * (1 - clair * 0.6 * k) + 255 * clair * 0.6 * k
        a[h - 1 - i, :, :3] *= 1 - sombre * k
        a[:, w - 1 - i, :3] *= 1 - sombre * 0.7 * k


def usure(a, graine, densite=0.004, rgb=(170, 170, 160), force=0.5):
    """Éraflures fines près des bords (peinture usée)."""
    h, w = a.shape[:2]
    r = rng(graine)
    m = np.zeros((h, w))
    n = int(h * w * densite)
    for _ in range(n):
        y, x = r.integers(0, h), r.integers(0, w)
        bord = min(x, y, w - 1 - x, h - 1 - y)
        if bord > 6 and r.random() > 0.15:
            continue
        l = r.integers(1, 5)
        horiz = r.random() < 0.6
        for k in range(l):
            yy, xx = (y, x + k) if horiz else (y + k, x)
            if 0 <= yy < h and 0 <= xx < w:
                m[yy, xx] = max(m[yy, xx], r.uniform(0.4, 1.0))
    fondu(a, m * force, rgb)


# ------------------------------------------------------------------ matières
def matiere(nom, h, w, graine, teinte=None):
    if nom == "polymere":
        base = np.array(teinte or (86, 92, 60), float)
        a = rgba(base, h, w)
        a[..., :3] += (bruit(h, w, graine) * 5 + bruit_bas(h, w, graine + 1, 10) * 12)[..., None]
    elif nom == "caoutchouc":
        base = np.array(teinte or (40, 42, 38), float)
        a = rgba(base, h, w)
        a[..., :3] += (bruit(h, w, graine) * 4)[..., None]
    elif nom == "acier":
        base = np.array(teinte or (66, 71, 78), float)
        a = rgba(base, h, w)
        brosse = flou(rng(graine).normal(0, 1, (h, w)), 1)
        brosse = np.repeat(brosse.mean(axis=1, keepdims=True), w, axis=1) * 0.6 + bruit(h, w, graine + 2) * 0.4
        a[..., :3] += (brosse * 7 + bruit_bas(h, w, graine + 3, 12) * 10)[..., None]
    elif nom == "carbone":
        base = np.array(teinte or (26, 27, 30), float)
        a = rgba(base, h, w)
        y, x = np.mgrid[0:h, 0:w]
        # sergé 2 x 2 : fils en diagonale, reflet alterné
        cellule = ((x // 3) + (y // 3)) % 2
        diag = ((x + y) % 6) / 6.0
        reflet = np.where(cellule == 0, 9 + 10 * diag, 3 + 6 * (1 - diag))
        a[..., :3] += (reflet + bruit(h, w, graine) * 2)[..., None]
    elif nom == "or":
        base = np.array(teinte or (196, 150, 64), float)
        a = rgba(base, h, w)
        a[..., :3] += (bruit(h, w, graine) * 8)[..., None]
        y = np.mgrid[0:h, 0:w][0]
        a[..., :3] += (18 * np.cos(y / max(1, h) * math.pi))[..., None]
    elif nom == "metal_sombre":
        base = np.array(teinte or (48, 50, 55), float)
        a = rgba(base, h, w)
        a[..., :3] += (bruit(h, w, graine) * 5)[..., None]
    else:
        raise ValueError(nom)
    return a


# ------------------------------------------------------------------ modèle et atlas
FACES_TAILLE = {
    "north": lambda d: (d[0], d[1]), "south": lambda d: (d[0], d[1]),
    "east": lambda d: (d[2], d[1]), "west": lambda d: (d[2], d[1]),
    "up": lambda d: (d[0], d[2]), "down": lambda d: (d[0], d[2]),
}


class Face:
    """Une face à peindre : nom, taille en texels, boîte d'origine (pour placer les détails à la bonne hauteur)."""

    def __init__(self, nom, a, b, w, h):
        self.nom, self.a, self.b, self.w, self.h = nom, a, b, w, h

    def y_ligne(self, j):
        """Hauteur (pixels du modèle) de la ligne j d'une face de côté."""
        return self.b[1] - (j + 0.5) / D

    def ligne_y(self, y):
        """Ligne de texture d'une hauteur du modèle (faces de côté)."""
        return (self.b[1] - y) * D


class Modele:
    def __init__(self):
        self.elements = []
        self.a_peindre = []

    def boite(self, a, b, peintres, rotation=None, lumineux=False):
        """peintres : face -> fonction(Face) -> image RGBA ; les faces absentes ne sont pas dessinées (cachées)."""
        e = {"from": [round(v, 4) for v in a], "to": [round(v, 4) for v in b], "faces": {}}
        if rotation:
            e["rotation"] = rotation
        if lumineux:
            e["forge_data"] = {"block_light": 15, "sky_light": 15}
        dims = [b[i] - a[i] for i in range(3)]
        for nom, peintre in peintres.items():
            fw, fh = FACES_TAILLE[nom](dims)
            w, h = max(2, int(math.ceil(fw * D - 1e-6))), max(2, int(math.ceil(fh * D - 1e-6)))
            e["faces"][nom] = {"uv": None, "texture": "#0"}
            self.a_peindre.append((e, nom, Face(nom, a, b, w, h), fw * D, fh * D, peintre))
        self.elements.append(e)
        return e

    def atlas(self):
        img = np.zeros((TAILLE, TAILLE, 4))
        marge = 2
        ordre = sorted(self.a_peindre, key=lambda t: -t[2].h)
        x = y = 0
        haut_ligne = 0
        deja = {}
        for e, nom, face, fw, fh, peintre in ordre:
            # même peintre et même taille : même morceau de texture (pans, côtés, plaques, contacts, poignées)
            cle = (id(peintre), face.w, face.h)
            if cle in deja:
                px, py = deja[cle]
                e["faces"][nom]["uv"] = [round(px / UV, 4), round(py / UV, 4), round((px + fw) / UV, 4), round((py + fh) / UV, 4)]
                continue
            if x + face.w + marge > TAILLE:
                x = 0
                y += haut_ligne + marge
                haut_ligne = 0
            if y + face.h > TAILLE:
                raise RuntimeError("texture trop petite")
            morceau = np.clip(peintre(face), 0, 255)
            img[y:y + face.h, x:x + face.w] = morceau
            # débord d'un texel autour (pas de couture au bord des faces)
            if y > 0:
                img[y - 1, x:x + face.w] = morceau[0]
            if y + face.h < TAILLE:
                img[y + face.h, x:x + face.w] = morceau[-1]
            if x > 0:
                img[y:y + face.h, x - 1] = morceau[:, 0]
            if x + face.w < TAILLE:
                img[y:y + face.h, x + face.w] = morceau[:, -1]
            e["faces"][nom]["uv"] = [round(x / UV, 4), round(y / UV, 4), round((x + fw) / UV, 4), round((y + fh) / UV, 4)]
            deja[cle] = (x, y)
            x += face.w + marge
            haut_ligne = max(haut_ligne, face.h)
        return Image.fromarray(img.astype(np.uint8), "RGBA")


# ------------------------------------------------------------------ peintres communs
def uni(nom_matiere, graine, teinte=None, biseau=True, use=0.0):
    def p(f):
        a = matiere(nom_matiere, f.h, f.w, graine, teinte)
        if use:
            usure(a, graine + 7, force=use)
        if biseau:
            bords(a)
        return a
    return p


def chevrons(a, n, x0, y0, taille, rgb, alpha=1.0):
    """n chevrons empilés pointe en haut (grade), coin haut-gauche (x0, y0)."""
    h, w = a.shape[:2]
    m = np.zeros((h, w))
    ep = taille * 0.32
    for k in range(n):
        yk = y0 + k * taille * 0.55
        pts = [(x0, yk + taille * 0.55), (x0 + taille / 2, yk), (x0 + taille, yk + taille * 0.55),
               (x0 + taille, yk + taille * 0.55 + ep), (x0 + taille / 2, yk + ep), (x0, yk + taille * 0.55 + ep)]
        m = np.maximum(m, masque_poly(h, w, pts))
    fondu(a, m * alpha, rgb)


def avion(a, cx, cy, l, rgb, alpha=1.0):
    """Silhouette d'avion d'attaque vue de dessus (emblème de l'Airstrike), nez vers le haut."""
    h, w = a.shape[:2]
    s = l / 100.0
    pts = [(0, -50), (5, -36), (6, -12), (46, 10), (46, 18), (7, 10), (6, 30), (20, 42), (20, 48), (3, 44), (0, 50)]
    gauche = [(cx - x * s, cy + y * s) for x, y in reversed(pts[1:-1])]
    droite = [(cx + x * s, cy + y * s) for x, y in pts]
    fondu(a, masque_poly(h, w, droite + [(cx, cy + 50 * s)] + gauche) * alpha, rgb)


def code_barres(a, x0, y0, l, h, graine, rgb, alpha=0.8):
    r = rng(graine)
    x = x0
    m = np.zeros(a.shape[:2])
    while x < x0 + l:
        e = int(r.integers(1, 4))
        if r.random() < 0.55:
            m[int(y0):int(y0 + h), int(x):int(min(x0 + l, x + e))] = 1
        x += e
    fondu(a, m * alpha, rgb)


def rayures(a, y0, y1, larg, rgb1, rgb2):
    """Bande hachurée de danger (diagonales)."""
    h, w = a.shape[:2]
    y, x = np.mgrid[0:h, 0:w]
    zone = (y >= y0) & (y < y1)
    fondu(a, (zone & (((x + y) // larg) % 2 == 0)).astype(float), rgb1)
    fondu(a, (zone & (((x + y) // larg) % 2 == 1)).astype(float), rgb2)


def rivet(a, cx, cy, r, rgb=(120, 124, 130)):
    h, w = a.shape[:2]
    fondu(a, masque_ellipse(h, w, (cx - r, cy - r, cx + r, cy + r)), rgb)
    fondu(a, masque_ellipse(h, w, (cx - r * 0.5, cy - r * 0.7, cx + r * 0.2, cy - r * 0.1)) * 0.5, (230, 230, 230))


# ------------------------------------------------------------------ une cartouche
def cartouche(cle):
    n = NIVEAUX[cle]
    teinte = n["couleur"]
    mat = n["matiere"]
    clair = tuple(min(255, int(c * 0.45 + 255 * 0.55)) for c in teinte)
    g = {"light": 10, "heavy": 20, "special": 30}[cle]
    M = Modele()
    use = {"polymere": 0.45, "acier": 0.6, "carbone": 0.3}[mat]
    usure_rgb = {"polymere": (150, 150, 120), "acier": (175, 180, 186), "carbone": (120, 120, 125)}[mat]

    # dimensions de la coque (la Légère est un peu plus courte)
    y0, y1 = (1.4, 12.8) if cle == "light" else (1.0, 13.6)
    x0, x1, z0, z1 = 3.5, 12.5, 5.5, 10.5
    ch = 0.5                                                  # pans coupés

    def coque(f):
        a = matiere(mat, f.h, f.w, g + zlib.crc32(f.nom.encode()) % 97)
        usure(a, g + 3, rgb=usure_rgb, force=use)
        bords(a, larg=2)
        return a

    def etiquette(f):
        """Face +z : bandeau du niveau, chevrons, nom, emblème, code-barres."""
        a = coque(f)
        h, w = f.h, f.w
        # bandeau de couleur sous le connecteur
        jb0, jb1 = int(f.ligne_y(y1 - 0.9)), int(f.ligne_y(y1 - 2.3))
        bande = np.zeros((h, w))
        bande[jb0:jb1, 3:w - 3] = 1
        fondu(a, bande, teinte)
        a[jb0:jb1, 3:w - 3, :3] += (bruit(jb1 - jb0, w - 6, g + 5) * 6)[..., None]
        a[jb0, 3:w - 3, :3] = a[jb0, 3:w - 3, :3] * 0.7 + 255 * 0.3
        a[jb1 - 1, 3:w - 3, :3] *= 0.75
        chevrons(a, n["chevrons"], w - 3 - 26 - 4, jb0 + 3 - (n["chevrons"] - 1) * 2.2, 26 * 0.62, (16, 17, 19), 0.85)
        texte(a, n["code"], 10, jb0 + 4, 13, (16, 17, 19), 0.9, espace=1.0)
        # nom du niveau
        jn = int(f.ligne_y(y1 - 3.0))
        texte(a, n["nom"], w / 2, jn, 26 if len(n["nom"]) <= 6 else 21, (236, 238, 242), 0.95, centre=True, espace=1.0)
        texte(a, "CARTOUCHE DE FRAPPE", w / 2, jn + 31, 9, clair, 0.9, gras=False, centre=True, espace=0.4)
        # emblème : avion dans un cercle
        jc = f.ligne_y((y0 + y1) / 2 - 1.6)
        r = min(w, h) * 0.24
        fondu(a, masque_ellipse(h, w, (w / 2 - r, jc - r, w / 2 + r, jc + r), epaisseur=2.2) * 0.75, clair)
        avion(a, w / 2, jc, r * 1.25, (232, 234, 238), 0.92)
        # code-barres et numéro de lot
        jbar = int(f.ligne_y(y0 + 1.6))
        code_barres(a, 12, jbar, w * 0.45, 12, g + 9, (14, 14, 16), 0.8)
        texte(a, "LRF-9", w - 10, jbar + 1, 10, (14, 14, 16), 0.75, gras=True, espace=0.6, droite=True)
        bords(a, larg=2)
        return a

    def dos(f):
        """Face -z : consignes, rayures de danger, lot."""
        a = coque(f)
        h, w = f.h, f.w
        bande = np.zeros((h, w))
        bande[int(f.ligne_y(y1 - 0.9)):int(f.ligne_y(y1 - 1.5)), 3:w - 3] = 1
        fondu(a, bande, teinte)
        j = int(f.ligne_y(y1 - 2.2))
        for k, s in enumerate(["NE PAS DÉMONTER", "BAIE DES JUMELLES", "CONTACTS EN AVANT"]):
            texte(a, s, 10, j + k * 13, 9, (225, 226, 228), 0.75, gras=False, espace=0.3)
        rayures(a, int(f.ligne_y(y0 + 2.4)), int(f.ligne_y(y0 + 1.2)), 7, (232, 180, 40), (20, 20, 22))
        texte(a, "LOT 0472 · " + n["code"], 10, int(f.ligne_y(y0 + 1.0)), 9, (225, 226, 228), 0.6, gras=False, espace=0.8)
        bords(a, larg=2)
        return a

    def cote(f):
        """Côtés : liseré de couleur en haut, rainures de prise."""
        a = coque(f)
        h, w = f.h, f.w
        bande = np.zeros((h, w))
        bande[int(f.ligne_y(y1 - 0.9)):int(f.ligne_y(y1 - 1.5)), :] = 1
        fondu(a, bande, teinte)
        bords(a, larg=2)
        return a

    def pan(f):
        a = matiere(mat, f.h, f.w, g + 40)
        if cle == "special":
            a = matiere("or", f.h, f.w, g + 41)
        a[..., :3] *= 1.08
        bande = np.zeros((f.h, f.w))
        bande[int(f.ligne_y(y1 - 0.9)):int(f.ligne_y(y1 - 1.5)), :] = 1
        if cle != "special":
            fondu(a, bande, teinte)
        return a

    def plaque_sombre(f):
        a = matiere("metal_sombre", f.h, f.w, g + 50)
        bords(a, larg=1)
        return a

    def contact(f):
        a = matiere("or", f.h, f.w, g + 60)
        bords(a, clair=0.3, sombre=0.3, larg=1)
        return a

    def poignee(f):
        a = matiere("caoutchouc", f.h, f.w, g + 70, teinte=(44, 46, 40) if mat == "polymere" else None)
        for j in range(3, f.h - 3, 6):
            a[j:j + 2, 1:-1, :3] *= 0.55
            a[j + 2, 1:-1, :3] = a[j + 2, 1:-1, :3] * 0.8 + 255 * 0.08
        bords(a, larg=1)
        return a

    tous = ("north", "south", "east", "west", "up", "down")

    # coque à pans coupés : noyau (avant / arrière), largeur (côtés), quatre pans à 45°
    M.boite((x0 + ch, y0, z0), (x1 - ch, y1, z1), {"south": etiquette, "north": dos, "up": plaque_sombre, "down": plaque_sombre})
    M.boite((x0, y0, z0 + ch), (x1, y1, z1 - ch), {"east": cote, "west": cote, "up": plaque_sombre, "down": plaque_sombre})
    r = ch / math.sqrt(2)
    for (cx, cz) in ((x0 + ch, z0 + ch), (x1 - ch, z0 + ch), (x0 + ch, z1 - ch), (x1 - ch, z1 - ch)):
        # carré tourné de 45° autour de y : sa diagonale bouche le coin entre le noyau et la largeur
        M.boite((cx - r, y0, cz - r), (cx + r, y1, cz + r),
                {"north": pan, "south": pan, "east": pan, "west": pan, "up": plaque_sombre, "down": plaque_sombre},
                rotation={"angle": 45, "axis": "y", "origin": [round(cx, 4), 7.0, round(cz, 4)]})

    # semelle et connecteur
    M.boite((3.2, y0 - 0.6, 5.2), (12.8, y0 + 0.4, 10.8), {f: plaque_sombre for f in tous})
    M.boite((4.6, y1, 6.2), (11.4, y1 + 1.1, 9.8), {f: plaque_sombre for f in tous})
    xs = (5.6, 7.3, 9.0) if cle != "special" else (5.3, 6.6, 7.9, 9.2)
    for x in xs:
        M.boite((x, y1 + 1.1, 7.5), (x + 0.9, y1 + 1.6, 8.5), {f: contact for f in ("north", "south", "east", "west", "up")})

    # poignées latérales
    for xa, xb in ((x0 - 0.45, x0), (x1, x1 + 0.45)):
        M.boite((xa, y0 + 2.6, 6.4), (xb, y0 + 7.6, 9.6), {f: poignee for f in tous})

    if cle == "heavy":
        # deux cerclages d'acier vissés
        def cerclage(f):
            a = matiere("acier", f.h, f.w, g + 80, teinte=(96, 101, 108))
            usure(a, g + 81, rgb=(190, 195, 200), force=0.7)
            bords(a, clair=0.18, sombre=0.3, larg=2)
            if f.nom in ("south", "north"):
                for x in (8, f.w - 8):
                    rivet(a, x, f.h / 2, 3.2)
            return a
        # en haut (sous le connecteur, au-dessus du bandeau) et en bas (sur la semelle) : l'étiquette reste lisible
        for ya, yb in ((y1 - 0.85, y1 - 0.05), (y0 + 0.3, y0 + 1.1)):
            M.boite((x0 - 0.2, ya, z0 - 0.2), (x1 + 0.2, yb, z1 + 0.2), {f: cerclage for f in tous})

    if cle == "special":
        # renforts d'angle (or anodisé) et hublot lumineux
        for (cx, cz) in ((x0 + ch, z0 + ch), (x1 - ch, z0 + ch), (x0 + ch, z1 - ch), (x1 - ch, z1 - ch)):
            rr = 0.62
            M.boite((cx - rr, y0 + 0.2, cz - rr), (cx + rr, y0 + 2.2, cz + rr), {f: pan for f in tous},
                    rotation={"angle": 45, "axis": "y", "origin": [round(cx, 4), 7.0, round(cz, 4)]})
            M.boite((cx - rr, y1 - 2.2, cz - rr), (cx + rr, y1 - 0.2, cz + rr), {f: pan for f in tous},
                    rotation={"angle": 45, "axis": "y", "origin": [round(cx, 4), 7.0, round(cz, 4)]})

        def hublot(f):
            a = rgba((255, 170, 60), f.h, f.w)
            yy, xx = np.mgrid[0:f.h, 0:f.w]
            d = np.hypot((xx - f.w / 2) / (f.w / 2), (yy - f.h / 2) / (f.h / 2))
            a[..., :3] = a[..., :3] * (1 - 0.35 * np.clip(d, 0, 1))[..., None] + (60 * np.clip(1 - d, 0, 1))[..., None]
            a[..., :3] = np.clip(a[..., :3], 0, 255)
            bords(a, clair=0.25, sombre=0.25, larg=1)
            return a

        def cadre(f):
            a = matiere("or", f.h, f.w, g + 90)
            bords(a, larg=1)
            return a
        yh = y0 + 1.0
        M.boite((6.3, yh - 0.25, z1 - 0.02), (9.7, yh + 1.35, z1 + 0.25), {f: cadre for f in ("north", "east", "west", "up", "down")})
        M.boite((6.6, yh, z1 + 0.05), (9.4, yh + 1.1, z1 + 0.3), {"south": hublot, "east": hublot, "west": hublot,
                                                                  "up": hublot, "down": hublot}, lumineux=True)
    return M


def ecrire(cle, M, dossier_modeles, dossier_textures):
    tex = M.atlas()
    nom = "cartridge_" + cle
    modele = {
        "credit": "HeraliZ",
        "texture_size": [TAILLE, TAILLE],
        "textures": {"0": "airstrike:item/" + nom, "particle": "airstrike:item/" + nom},
        "elements": M.elements,
        "display": AFFICHAGE,
    }
    (dossier_modeles / (nom + ".json")).write_text(json.dumps(modele, indent=1, ensure_ascii=False) + "\n", encoding="utf-8")
    tex.save(dossier_textures / (nom + ".png"), optimize=True)
    return len(M.elements)


def main():
    if len(sys.argv) > 1:
        dm = dt = Path(sys.argv[1])
        dm.mkdir(parents=True, exist_ok=True)
    else:
        dm, dt = ASSETS / "models" / "item", ASSETS / "textures" / "item"
    for cle in NIVEAUX:
        n = ecrire(cle, cartouche(cle), dm, dt)
        print("[cartouches] %s : %d éléments -> %s" % (cle, n, dm))


if __name__ == "__main__":
    main()
