#!/usr/bin/env python3
"""Aperçus de la refonte des HUD en jeu (25/10, docs/HUD_REFONTE.md) : joueur à terre, carte « viser un joueur à terre »,
réanimation (en cours, en attente, réussie, interrompue), soins (en cours, interrompu, grenade) et usure de l'arme
(ok, critique, ruinée), dans les trois langues, sur un fond de jeu, à l'échelle d'interface 3 (1920 x 1080).

    python modules/noyau/outils/apercu_hud.py [DOSSIER] [--projet DOSSIER_DU_PROJET]

Mêmes mesures que le code : les constantes de mise en page sont LUES dans les classes Java (HudATerre, CarteCible,
HudReanimateur, HudSoins, HudUsure, AnneauProgression), les tailles des polices dans les fichiers de police du noyau
(assets/heraliz_noyau/font/hud_*.json : taille du .json = em x (ascendante - descendante) / unités par em). Mêmes textes :
fichiers de langue des mods ; noms des touches de Minecraft recopiés ci-dessous (TOUCHES). Chaque texte est mesuré avec
la vraie police : un texte qui dépasse sa place, dans n'importe laquelle des trois langues, arrête le script (code 1).

Placement vertical des lettres (Minecraft 1.20.1, police TTF) : ligne de base = y + em x ascendante / unités par em - 3
(décalage « shift » à 0) ; le code et l'aperçu centrent les textes sur le milieu des capitales.

Par défaut les images vont dans maquettes/hud_refonte/ ; chaque image a sa version agrandie (« _zoom », x2 au pixel près)
autour du HUD. Numpy, Pillow, fontTools. Le moteur de dessin est celui des aperçus de la boutique
(modules/boutique/outils/apercu_boutique.py) ; si ce script tourne hors du projet (copie de travail), --projet ou la
variable HERALIZ_PROJET donne le dossier du projet où chercher ce qui manque.
"""
import argparse
import json
import math
import os
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from fontTools.ttLib import TTFont

RACINE = Path(__file__).resolve().parents[3]
PROJET = RACINE
GW, GH = 640, 360
K = 6  # pixels par unité pendant le dessin ; image finale réduite de moitié (échelle 3)
LANGUES = ("fr_fr", "en_us", "ru_ru")
DEFAUTS = []
LANGUE = "fr_fr"

# noms des touches tels que Minecraft 1.20.1 les écrit (assets minecraft/lang) : Maj, clic droit, Retour arrière, I
TOUCHES = {
    "fr_fr": {"shift": "Maj. gauche", "use": "Clic droit", "backspace": "Retour arrière", "i": "I"},
    "en_us": {"shift": "Left Shift", "use": "Right Button", "backspace": "Backspace", "i": "I"},
    "ru_ru": {"shift": "Shift слева", "use": "ПКМ", "backspace": "Backspace", "i": "I"},
}
PSEUDO = "Kovalenko_77"
OBJETS = {"fr_fr": ("Bandage", "Grenade M67"), "en_us": ("Bandage", "M67 Grenade"), "ru_ru": ("Бинт", "Граната M67")}


def chemin(rel):
    """Fichier de la copie de travail s'il y est, sinon celui du projet."""
    p = RACINE / rel
    return p if p.exists() else PROJET / rel


# ---------------------------------------------------------------- constantes lues dans le code Java
DECL = re.compile(r"static\s+final\s+(?:float|int|double|long)\s+([^;]+);")
VAL = re.compile(r"^\s*([A-Z][A-Z0-9_]*)\s*=\s*(0[xX][0-9a-fA-F]+|-?[0-9]*\.?[0-9]+)[fFdDlL]?\s*$")


def constantes(rel):
    """Constantes numériques simples (« static final float A = 1.5f, B = 2; ») d'une classe Java."""
    src = chemin(rel).read_text(encoding="utf-8")
    src = re.sub(r"/\*.*?\*/", "", src, flags=re.S)
    src = re.sub(r"//[^\n]*", "", src)
    out = {}
    for m in DECL.finditer(src):
        prof, morceau, morceaux = 0, "", []
        for ch in m.group(1):
            prof += ch in "([{"
            prof -= ch in ")]}"
            if ch == "," and prof == 0:
                morceaux.append(morceau)
                morceau = ""
            else:
                morceau += ch
        morceaux.append(morceau)
        for d in morceaux:
            v = VAL.match(d)
            if v:
                n = v.group(2)
                out[v.group(1)] = float(int(n, 16)) if n.lower().startswith("0x") else float(n)
    if not out:
        sys.exit("aucune constante lue dans " + rel)
    return out


KIT = "modules/noyau/src/client/java/fr/heraliz/noyau/client/kit/"
SURVIE = "modules/survie/src/client/java/fr/heraliz/reanimation/client/hud/"
C = {}


def charger_constantes():
    C["anneau"] = constantes(KIT + "AnneauProgression.java")
    C["barre"] = constantes(KIT + "BarreProgression.java")
    C["terre"] = constantes(SURVIE + "HudATerre.java")
    C["carte"] = constantes(SURVIE + "CarteCible.java")
    C["reanim"] = constantes(SURVIE + "HudReanimateur.java")
    C["soins"] = constantes("modules/armes/src/client/java/fr/heraliz/tactique/client/soins/HudSoins.java")
    C["usure"] = constantes("modules/monde/src/client/java/fr/heraliz/usure/client/HudUsure.java")
    C["touche"] = constantes(KIT + "ToucheHud.java")
    C["palette"] = {k: int(v) for k, v in constantes(KIT + "Palette.java").items()}


def palette(nom):
    return C["palette"][nom]


# ---------------------------------------------------------------- couleurs
def rgb(c):
    return (c >> 16 & 255, c >> 8 & 255, c & 255)


def mix(a, b, t):
    ra, rb = rgb(a), rgb(b)
    return sum(int(ra[i] + (rb[i] - ra[i]) * t) << (16 - 8 * i) for i in range(3))


# ---------------------------------------------------------------- polices (mêmes fichiers que le jeu)
FONT = "modules/noyau/src/client/resources/assets/heraliz_noyau/font/"


class Police:
    """Une police HUD du noyau : fichiers et em lus dans son .json, mêmes métriques que PoliceHud.java."""

    def __init__(self, nom, espace=0.0, capitales=False):
        self.nom, self.espace, self.capitales = nom, espace, capitales
        doc = json.loads(chemin(FONT + nom + ".json").read_text(encoding="utf-8"))
        ttf = [p for p in doc["providers"] if p.get("type") == "ttf"]
        self.fichiers = []
        for p in ttf:
            f = chemin(FONT + p["file"].split(":")[1])
            t = TTFont(str(f))
            hh, up = t["hhea"], t["head"].unitsPerEm
            em = p["size"] * up / (hh.ascent - hh.descent)
            self.fichiers.append({"chemin": f, "em": em, "asc": hh.ascent / up, "cap": t["OS/2"].sCapHeight / up,
                                  "cmap": t.getBestCmap(), "pil": ImageFont.truetype(str(f), max(1, int(round(em * K))))})
            if p.get("shift", [0, 0]) != [0.0, 0.0]:
                sys.exit("police %s : décalage (shift) attendu à 0" % nom)
        self.em = self.fichiers[0]["em"]
        self.cap = self.fichiers[0]["cap"] * self.em
        # ligne de base sous le point de dessin, comme Minecraft (BakedGlyph : haut des lettres - 3)
        self.base = self.fichiers[0]["asc"] * self.em - 3

    def police_de(self, ch):
        for f in self.fichiers:
            if ord(ch) in f["cmap"]:
                return f
        return self.fichiers[0]

    def preparer(self, s):
        return s.upper() if self.capitales else s

    def largeur(self, s):
        s = self.preparer(s)
        w = sum(self.police_de(ch)["pil"].getlength(ch) for ch in s) / K
        return w + (self.espace * (len(s) - 1) if s else 0)


P = {}


def charger_polices():
    P["titre"] = Police("hud_mini_gras", C["terre"]["TITRE_ESPACE"], True)
    P["chiffre"] = Police("hud_chiffre")
    P["chiffre_petit"] = Police("hud_chiffre_petit")
    P["texte"] = Police("hud_texte")
    P["texte_gras"] = Police("hud_texte_gras")
    P["mini"] = Police("hud_mini")
    P["mini_gras"] = Police("hud_mini_gras")
    P["puce"] = Police("hud_mini_gras", C["carte"]["PUCE_ESPACE"], True)
    P["etat"] = Police("hud_mini_gras", C["usure"]["NOM_ESPACE"], True)
    P["doux"] = Police("hud_mini_gras", C["soins"]["PUCE_ESPACE"], True)


# ---------------------------------------------------------------- écran (moteur des aperçus de la boutique)
ab = None


def charger_moteur():
    """Moteur de dessin des aperçus de la boutique (rectangles, filets, dégradés, traits) : modules/boutique/outils."""
    global ab
    sys.path.insert(0, str(chemin("modules/boutique/outils/apercu_boutique.py").parent))
    import apercu_boutique
    ab = apercu_boutique
    if ab.K != K or (ab.GW, ab.GH) != (GW, GH):
        sys.exit("apercu_boutique.py a changé d'échelle : adapter apercu_hud.py")
    return ab


class Ecran:
    """Écran de l'aperçu : celui de la boutique (ab.Ecran), plus les anneaux, halos et textes des polices HUD."""

    def __init__(self, fond):
        self.base = ab.Ecran(fond)
        self.im = self.base.im
        self.defauts = self.base.defauts

    def zone(self, x0, y0, x1, y1):
        return self.base.zone(x0, y0, x1, y1)

    def rect(self, x0, y0, x1, y1, c, a):
        self.base.rect(x0, y0, x1, y1, c, a)

    def bord(self, x, y, w, h, c, a, e=1 / 3):
        self.base.border(x, y, w, h, c, a, e)

    def degrade(self, x0, y0, x1, y1, c0, a0, c1, a1, vertical=False):
        self.base.grad(x0, y0, x1, y1, c0, a0, c1, a1, vertical)

    def trait(self, pts, e, c, a):
        self.base.trait(pts, e, c, a)

    def poly(self, pts, c, a):
        self.base.poly(pts, c, a)

    def couche(self, arr, bx, by):
        L = Image.fromarray(arr.clip(0, 255).astype(np.uint8), "RGBA")
        self.im.alpha_composite(L, (max(0, bx), max(0, by)), (max(0, -bx), max(0, -by)))

    def arc(self, cx, cy, r_int, r_ext, de, a, c, alpha, flou=None):
        """Anneau de r_int à r_ext, de la fraction de à a d'un tour (0 = midi, sens horaire) ; bord adouci d'un pixel."""
        if a - de <= 1e-4 or alpha <= 0:
            return
        f = (flou if flou else 1.0 / 3) * K  # largeur du bord adouci (un pixel d'écran à l'échelle 3)
        m = (r_ext + (flou or 0) + 1)
        bx, by, w, h = self.zone(cx - m, cy - m, cx + m, cy + m)
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        px, py = (xx + bx + 0.5) - cx * K, (yy + by + 0.5) - cy * K
        d = np.sqrt(px * px + py * py)
        cov = np.clip((d - r_int * K) / f + 0.5, 0, 1) * np.clip((r_ext * K - d) / f + 0.5, 0, 1)
        if a - de < 1:
            ang = (np.arctan2(px, -py) / (2 * np.pi)) % 1.0
            arcl = 2 * np.pi * np.maximum(d, 1)
            cov *= np.clip((ang - de) * arcl / f + 0.5, 0, 1) * np.clip((a - ang) * arcl / f + 0.5, 0, 1)
        arr = np.zeros((h, w, 4), np.float32)
        arr[..., :3] = rgb(c)
        arr[..., 3] = cov * alpha
        self.couche(arr, bx, by)

    def disque(self, cx, cy, r, c, a):
        self.arc(cx, cy, 0, r, 0, 1, c, a)

    def halo(self, cx, cy, rx, ry, c, a):
        bx, by, w, h = self.zone(cx - rx, cy - ry, cx + rx, cy + ry)
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        d = np.sqrt(((xx + bx - cx * K) / (rx * K)) ** 2 + ((yy + by - cy * K) / (ry * K)) ** 2)
        arr = np.zeros((h, w, 4), np.float32)
        arr[..., :3] = rgb(c)
        arr[..., 3] = np.clip(1 - d, 0, 1) ** 2 * a
        self.couche(arr, bx, by)

    # textes : milieu des capitales sur cy ; align -1 gauche, 0 centre, 1 droite ; rend la largeur
    def texte(self, s, x, cy, pol, c, a=255, align=-1, maxw=None, quoi="", ombre=True):
        s = pol.preparer(s)
        w = pol.largeur(s)
        if maxw is not None and w > maxw + 0.05:
            self.defauts.append("[%s] %s : « %s » %.1f > %.1f" % (LANGUE, quoi or "texte", s, w, maxw))
        x0 = x if align < 0 else x - w / 2 if align == 0 else x - w
        base = cy + pol.cap / 2
        # couche locale autour du texte (une couche plein écran par texte serait très lente)
        marge = pol.em * 1.6 + 2
        bx, by = int((x0 - 2) * K), int((base - marge) * K)
        lw, lh = int((w + 6) * K), int(2 * marge * K)
        for dx, dy, cc, aa in ([(1 / 3, 1 / 3, 0x000000, a * 0.55)] if ombre else []) + [(0, 0, c, a)]:
            L = Image.new("RGBA", (lw, lh), (0, 0, 0, 0))
            d = ImageDraw.Draw(L)
            px = (x0 + dx) * K - bx
            for ch in s:
                f = pol.police_de(ch)
                d.text((px, (base + dy) * K - by), ch, font=f["pil"], fill=rgb(cc) + (int(aa),), anchor="ls")
                px += f["pil"].getlength(ch) + pol.espace * K
            bb = L.getbbox()
            if bb:
                self.im.alpha_composite(L.crop(bb), (bx + bb[0], by + bb[1]))
        return w

    def sauver(self, chemin_png, zone=None):
        DEFAUTS.extend(self.defauts)
        img = self.im.resize((GW * 3, GH * 3), Image.LANCZOS).convert("RGB")
        img.save(chemin_png, optimize=True)
        if zone:
            x0, y0, x1, y1 = [int(round(v * 3)) for v in zone]
            z = img.crop((max(0, x0), max(0, y0), min(GW * 3, x1), min(GH * 3, y1)))
            z.resize((z.width * 2, z.height * 2), Image.NEAREST).save(chemin_png.with_name(chemin_png.stem + "_zoom.png"), optimize=True)
        print("écrit", chemin_png.name)


# ---------------------------------------------------------------- fond de jeu (ville abandonnée, ciel couvert)
FONDS = {}


def fond(desature=0.0, seed=3):
    if (desature, seed) not in FONDS:
        FONDS[(desature, seed)] = fond_neuf(desature, seed)
    return FONDS[(desature, seed)]


def fond_neuf(desature, seed):
    W, H = GW * K, GH * K
    rng = np.random.default_rng(seed)
    y = np.linspace(0, 1, H)[:, None, None]
    ciel = np.array([118, 128, 140], np.float32) + (np.array([168, 170, 166], np.float32) - [118, 128, 140]) * np.clip(y / 0.5, 0, 1)
    sol = np.array([92, 96, 70], np.float32) + (np.array([58, 60, 44], np.float32) - [92, 96, 70]) * np.clip((y - 0.55) / 0.45, 0, 1)
    img = np.where(y < 0.55, ciel, sol) * np.ones((1, W, 1), np.float32)
    im = Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGB")
    d = ImageDraw.Draw(im)
    hz = int(0.55 * H)
    # collines et lisière de forêt
    pts = [(0, hz)] + [(x, hz - 40 * K * (0.5 + 0.5 * math.sin(x / (90 * K) + 1.3)) - rng.random() * 6 * K) for x in range(0, W + 40, 30)] + [(W, hz)]
    d.polygon(pts, fill=(84, 94, 86))
    for i in range(70):
        x = rng.random() * W
        h = (14 + rng.random() * 26) * K
        d.polygon([(x - 6 * K, hz + 2 * K), (x, hz - h), (x + 6 * K, hz + 2 * K)], fill=(46, 58, 46))
    # immeubles en ruine
    for bx0, bw, bh, col in ((40, 90, 120, (96, 92, 86)), (150, 60, 80, (110, 104, 96)), (460, 110, 140, (88, 86, 82)), (585, 70, 70, (102, 98, 92))):
        x0, x1, y1 = bx0 * K, (bx0 + bw) * K, hz + 6 * K
        y0 = y1 - bh * K
        d.rectangle([x0, y0, x1, y1], fill=col)
        for wx in range(bx0 + 8, bx0 + bw - 8, 16):
            for wy in range(int(y0 / K) + 10, int(y1 / K) - 12, 20):
                d.rectangle([wx * K, wy * K, (wx + 8) * K, (wy + 11) * K], fill=(30, 32, 34))
    # route
    d.polygon([(250 * K, H), (390 * K, H), (330 * K, hz + 4 * K), (308 * K, hz + 4 * K)], fill=(70, 70, 66))
    small = Image.fromarray((rng.random((36, 64)) * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)
    arr = np.array(im, np.float32) + ((np.array(small, np.float32) / 255 - 0.5) * 18)[..., None]
    if desature:
        gris = arr.mean(axis=2, keepdims=True)
        arr = arr + (gris - arr) * desature
    return Image.fromarray(arr.clip(0, 255).astype(np.uint8), "RGB").filter(ImageFilter.GaussianBlur(K * 0.6)).convert("RGBA")


def reticule(e):
    cx, cy = GW / 2, GH / 2
    e.rect(cx - 4.5, cy - 0.5, cx + 4.5, cy + 0.5, 0xFFFFFF, 210)
    e.rect(cx - 0.5, cy - 4.5, cx + 0.5, cy + 4.5, 0xFFFFFF, 210)


def barre_dayz(e, w=GW, h=GH, ecart=2, inclinaison=12):
    """Barre d'objets DayZ (HotbarOverlay) : 9 cases inclinées, la 1re choisie ; rend son bord droit réel."""
    k = math.tan(math.radians(inclinaison))
    total = 9 * 20 + 8 * ecart
    left = w / 2 - total / 2
    top = h - 2 - 20
    cy = top + 10
    for i in range(9):
        x = left + i * (20 + ecart)
        pts = [(x + k * (cy - top), top), (x + 20 + k * (cy - top), top), (x + 20 + k * (cy - top - 20), top + 20), (x + k * (cy - top - 20), top + 20)]
        e.poly(pts, 0x0E0F0F, 120)
        e.trait(pts + [pts[0]], 1 / 3 if i else 2 / 3, 0xF4F1E8 if i == 0 else 0x4A4844, 230)
    e.rect(left + 4 + k * 5, top + 8, left + 17 + k * 5, top + 12, 0x2A2C2E, 255)  # arme dans la case 1
    return HOTBAR_BORD_DROIT(w, ecart, inclinaison)


def HOTBAR_BORD_DROIT(w, ecart=2, inclinaison=12):
    """Même calcul que HotbarOverlay.bordDroit (droitier, sans jauge d'attaque dans la barre)."""
    total = 9 * 20 + 8 * ecart
    return w / 2 + total / 2 + math.tan(math.radians(inclinaison)) * 10


def hud_tacz(e):
    """Place du HUD des munitions de TaCZ (bas à droite, de h-44 à h-25) : silhouette seulement."""
    x1 = GW - 10
    e.rect(x1 - 66, GH - 44, x1, GH - 25, 0x000000, 60)
    e.texte("30", x1 - 30, GH - 35, P["chiffre"], 0xFFFFFF, 220, align=1)
    e.texte("90", x1 - 4, GH - 32, P["mini_gras"], 0xC9CDD4, 220, align=1)


# ---------------------------------------------------------------- kit : verre, touches, anneau (comme Formes / ToucheHud / AnneauProgression)
def verre(e, x, y, w, h, alpha=None):
    a = palette("VERRE_ALPHA") if alpha is None else alpha
    e.rect(x, y, x + w, y + h, palette("VERRE"), a)
    e.bord(x, y, w, h, 0xFFFFFF, 26)
    e.rect(x + 1 / 3, y + 1 / 3, x + w - 1 / 3, y + 2 / 3, 0xFFFFFF, 41)


def filet(e, x, y, w, c, a=255):
    e.degrade(x, y, x + w / 2, y + 1 / 3, c, 0, c, a)
    e.degrade(x + w / 2, y, x + w, y + 1 / 3, c, a, c, 0)


def touche_largeur(nom):
    return P["mini_gras"].largeur(nom) + 2 * C["touche"]["PAD"]


def touche(e, nom, x, cy, remplissage=0.0, couleur=None):
    """Touche : cadre clair et son nom (ToucheHud) ; rend sa largeur."""
    w = touche_largeur(nom)
    hh = C["touche"]["HAUTEUR"]
    y = cy - hh / 2
    e.rect(x, y, x + w, y + hh, 0xFFFFFF, C["touche"]["FOND_ALPHA"])
    if remplissage > 0:
        e.rect(x, y, x + w * min(1, remplissage), y + hh, couleur, 200)
    e.bord(x, y, w, hh, 0xFFFFFF, C["touche"]["BORD_ALPHA"])
    e.texte(nom, x + C["touche"]["PAD"], cy, P["mini_gras"], 0xFFFFFF, quoi="touche", ombre=False)
    return w


def anneau(e, cx, cy, p, couleur, alpha=1.0, piste=True, de=0.0):
    a = C["anneau"]
    r, ep = a["RAYON"], a["EPAISSEUR"]
    if piste:
        e.arc(cx, cy, r - ep / 2, r + ep / 2, 0, 1, 0xFFFFFF, 255 * a["PISTE_ALPHA"] * alpha)
    if p - de > 0:
        e.arc(cx, cy, r - ep / 2 - a["LUEUR"], r + ep / 2 + a["LUEUR"], de, p, couleur, 255 * a["LUEUR_ALPHA"] * alpha, flou=2 * a["LUEUR"])
        e.arc(cx, cy, r - ep / 2, r + ep / 2, de, p, couleur, 255 * alpha)


def coche(e, cx, cy, couleur, alpha=1.0):
    t = C["anneau"]["COCHE"]
    e.trait([(cx - t * 0.5, cy + t * 0.02), (cx - t * 0.14, cy + t * 0.36), (cx + t * 0.52, cy - t * 0.34)], C["anneau"]["COCHE_EPAISSEUR"],
            couleur, 255 * alpha)


def pente(degres):
    return math.tan(math.radians(degres))


def para(e, x0, y0, x1, y1, k, c, a, yref=None):
    """Parallélogramme penché (cisaillé autour de yref : x + k (yref - y)), comme Formes.parallelogramme."""
    if x1 <= x0 or y1 <= y0 or a <= 0:
        return
    r = (y0 + y1) / 2 if yref is None else yref
    e.poly([(x0 + k * (r - y0), y0), (x1 + k * (r - y0), y0), (x1 + k * (r - y1), y1), (x0 + k * (r - y1), y1)], c, a)


def bord_penche(e, x0, y0, x1, y1, k, ep, c, a):
    r = (y0 + y1) / 2
    para(e, x0 - ep, y0 - ep, x1 + ep, y0, k, c, a, r)
    para(e, x0 - ep, y1, x1 + ep, y1 + ep, k, c, a, r)
    para(e, x0 - ep, y0, x0, y1, k, c, a, r)
    para(e, x1, y0, x1 + ep, y1, k, c, a, r)


def verre_penche(e, x0, y0, x1, y1, k, alpha):
    r = (y0 + y1) / 2
    para(e, x0, y0, x1, y1, k, palette("VERRE"), alpha, r)
    bord_penche(e, x0, y0, x1, y1, k, 1 / 3, 0xFFFFFF, 26)
    para(e, x0 + 1 / 3, y0, x1 - 1 / 3, y0 + 1 / 3, k, 0xFFFFFF, 41, r)


def segments(e, x, y, w, h, k, n, ecart, part, plein, a_plein, piste, a_piste, part_max, perdu, a_perdu):
    sw = (w - (n - 1) * ecart) / n
    r = y + h / 2
    premier_perdu = math.ceil(part_max * n - 1e-4)
    for i in range(n):
        sx = x + i * (sw + ecart)
        f = max(0.0, min(1.0, part * n - i))
        if f > 0:
            para(e, sx, y, sx + sw * f, y + h, k, plein, a_plein, r)
        if f < 1:
            perdu_ici = i >= premier_perdu
            para(e, sx + sw * f, y, sx + sw, y + h, k, perdu if perdu_ici else piste, a_perdu if perdu_ici else a_piste, r)


def barre_progression(e, cx, y, p, couleur, alpha=255):
    """BarreProgression.ajouter : ombre, piste, plein."""
    b = C["barre"]
    k = pente(b["INCLINAISON"])
    x0, x1, y1 = cx - b["LARGEUR"] / 2, cx + b["LARGEUR"] / 2, y + b["HAUTEUR"]
    r = (y + y1) / 2
    para(e, x0 - 1 / 3, y - 1 / 3, x1 + 1 / 3, y1 + 1 / 3, k, 0x000000, b["OMBRE_ALPHA"] * alpha, r)
    xp = x0 + b["LARGEUR"] * p
    para(e, xp, y, x1, y1, k, 0xFFFFFF, b["PISTE_ALPHA"] * alpha, r)
    para(e, x0, y, xp, y1, k, couleur, alpha, r)


def voile_doux(e, x0, y0, x1, y1, fx, fy, c, alpha):
    """Formes.voile : opaque au milieu, transparent sur le pourtour."""
    bx, by, w, h = e.zone(x0, y0, x1, y1)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    px, py = (xx + bx + 0.5) / K, (yy + by + 0.5) / K
    ax = np.clip(np.minimum(px - x0, x1 - px) / fx, 0, 1)
    ay = np.clip(np.minimum(py - y0, y1 - py) / fy, 0, 1)
    arr = np.zeros((h, w, 4), np.float32)
    arr[..., :3] = rgb(c)
    arr[..., 3] = ax * ay * alpha
    e.couche(arr, bx, by)


def secondes(v):
    s = "%.1f" % v
    return s.replace(".", ",") if LANGUE in ("fr_fr", "ru_ru") else s


# ---------------------------------------------------------------- textes des mods
def langue(dossier):
    return json.loads(chemin(dossier + "/" + LANGUE + ".json").read_text(encoding="utf-8"))


def tr(T, cle, *args):
    s = T[cle]
    for a in args:
        s = re.sub(r"%(\d+\$)?s", lambda m: str(a), s, count=1)
    return s.replace("%%", "%")


REANIM = "modules/survie/src/client/resources/assets/reanimation/lang"
TACT = "modules/armes/src/client/resources/assets/heraliz_tactique/lang"
USURE = "modules/monde/src/client/resources/assets/heraliz_usure/lang"


# ---------------------------------------------------------------- (a) joueur à terre (HudATerre)
def a_terre(etat, dossier):
    T = langue(REANIM)
    k = TOUCHES[LANGUE]
    c = C["terre"]
    vie = 0.62
    e = Ecran(fond(desature=0.65))
    # voile et vignette très légers
    e.rect(0, 0, GW, GH, 0x000000, 255 * (c["ECRAN_MIN"] + c["ECRAN_VIE"] * (1 - vie)))
    bord = c["VIGNETTE_MIN"] + c["VIGNETTE_VIE"] * (1 - vie)
    for i in range(18):
        t = i / 18
        e.bord(t * 26, t * 18, GW - 2 * t * 26, GH - 2 * t * 18, 0x8C0505, 255 * bord * (1 - t) ** 1.6 * 0.9, e=26 / 18 + 0.2)
    reanime = etat == "reanime"
    coul = palette("OK") if reanime else palette("DANGER")
    titre = T["hud.reanimation.reviving_title"] if reanime else T["hud.reanimation.downed"]
    chiffre = "0:03" if reanime else "0:24"
    if reanime:
        statut, sc = tr(T, "hud.reanimation.being_revived", PSEUDO), mix(palette("OK"), 0xFFFFFF, 0.45)
    elif etat == "fouille":
        statut, sc = tr(T, "hud.reanimation.being_searched", PSEUDO), palette("ALERTE")
    else:
        statut, sc = T["hud.reanimation.statut_saigne"], palette("ATTENUE")
    cx = GW / 2
    aide_t = T["hud.reanimation.aide_reanime"] if reanime else None
    if aide_t is None:
        aide = (P["mini"].largeur(T["hud.reanimation.hold"]) + c["ECART_AIDE"] + touche_largeur(k["backspace"]) + c["ECART_AIDE"]
                + P["mini"].largeur(T["hud.reanimation.to_give_up"]))
    else:
        aide = P["mini"].largeur(aide_t)
    dl = max(c["VOILE_DL"], max(aide, P["mini"].largeur(statut)) / 2 + c["VOILE_FX"] / 2)
    voile_doux(e, cx - dl, GH - c["VOILE_HAUT"], cx + dl, GH - c["VOILE_BAS"], c["VOILE_FX"], c["VOILE_FY"], 0x000000, c["VOILE_ALPHA"])
    # le texte doit tenir sur la partie bien sombre du voile (sans l'élargir au-delà de la largeur de base)
    for quoi, w in (("aide", aide), ("statut", P["mini"].largeur(statut))):
        if w > 2 * c["VOILE_DL"] - c["VOILE_FX"] + 0.05:
            e.defauts.append("[%s] à terre : %s trop large (%.1f > %.1f)" % (LANGUE, quoi, w, 2 * c["VOILE_DL"] - c["VOILE_FX"]))
    # ligne principale : point, titre, « · », temps
    sep = "·"
    larg = (2 * c["POULS_R"] + c["ECART"] + P["titre"].largeur(titre) + c["ECART"] + P["mini"].largeur(sep) + c["ECART"]
            + P["chiffre_petit"].largeur(chiffre))
    x = cx - larg / 2
    cy = GH - c["LIGNE_BAS"]
    e.halo(x + c["POULS_R"], cy, c["POULS_R"] * 3, c["POULS_R"] * 3, coul, 80)
    e.disque(x + c["POULS_R"], cy, c["POULS_R"], coul, 255)
    x += 2 * c["POULS_R"] + c["ECART"]
    x += e.texte(titre, x, cy, P["titre"], coul) + c["ECART"]
    x += e.texte(sep, x, cy, P["mini"], palette("ATTENUE")) + c["ECART"]
    e.texte(chiffre, x, cy, P["chiffre_petit"], palette("TEXTE"))
    # barre très fine
    bx, by = cx - c["BARRE_L"] / 2, GH - c["BARRE_BAS"]
    e.rect(bx, by, bx + c["BARRE_L"], by + c["BARRE_H"], 0xFFFFFF, 31)
    part = 0.55 if reanime else vie
    fw = c["BARRE_L"] * part
    e.degrade(bx, by, bx + fw, by + c["BARRE_H"], mix(coul, 0, 0.55), 255, coul, 255)
    e.halo(bx + fw, by + c["BARRE_H"] / 2, 3, 3, coul, 115)
    # état, aide
    e.texte(statut, cx, GH - c["ETAT_BAS"], P["mini"], sc, align=0)
    cy = GH - c["AIDE_BAS"]
    if aide_t is not None:
        e.texte(aide_t, cx, cy, P["mini"], palette("ATTENUE"), 217, align=0)
    else:
        x = cx - aide / 2
        x += e.texte(T["hud.reanimation.hold"], x, cy, P["mini"], palette("ATTENUE"), 217) + c["ECART_AIDE"]
        x += touche(e, k["backspace"], x, cy, remplissage=0.35 if etat == "saigne" else 0, couleur=palette("DANGER")) + c["ECART_AIDE"]
        e.texte(T["hud.reanimation.to_give_up"], x, cy, P["mini"], palette("ATTENUE"), 217)
    e.sauver(dossier / ("a_terre_%s_%s.png" % (etat, LANGUE[:2])), (cx - 150, GH - 64, cx + 150, GH))


# ---------------------------------------------------------------- (b) carte « viser un joueur à terre » (CarteCible)
def carte(etat, dossier):
    T = langue(REANIM)
    k = TOUCHES[LANGUE]
    c = C["carte"]
    e = Ecran(fond())
    # le joueur à terre, couché devant
    e.poly([(296, 214), (352, 204), (356, 212), (300, 224)], 0x3A3F34, 255)
    e.disque(357, 205, 5, 0xB58E73, 255)
    reticule(e)
    x0, y0, L = GW / 2 + c["DX"], GH / 2 + c["DY"], c["L"]
    pad = c["PAD"]
    H = c["H_ACTIONS"] if etat == "libre" else c["H_ETAT"]
    verre(e, x0, y0, L, H)
    puce = T["hud.reanimation.downed"]
    pw = P["puce"].largeur(puce) + 2 * c["PUCE_PAD"]
    px = x0 + L - pad - pw
    py = y0 + c["LIGNE1_CY"]
    e.rect(px, py - c["PUCE_H"] / 2, px + pw, py + c["PUCE_H"] / 2, palette("DANGER"), 40)
    e.bord(px, py - c["PUCE_H"] / 2, pw, c["PUCE_H"], palette("DANGER"), 110)
    e.texte(puce, px + c["PUCE_PAD"], py, P["puce"], mix(palette("DANGER"), 0xFFFFFF, 0.35), ombre=False)
    e.texte(PSEUDO, x0 + pad, py, P["texte_gras"], 0xFFFFFF, maxw=px - c["ECART"] - x0 - pad, quoi="pseudo")
    mort = tr(T, "hud.reanimation.bleed_timer", 24)
    mw = P["mini"].largeur(mort)
    by = y0 + c["BARRE_Y"]
    bw = L - 2 * pad - mw - c["ECART"]
    e.rect(x0 + pad, by, x0 + pad + bw, by + c["BARRE_H"], 0xFFFFFF, 26)
    e.degrade(x0 + pad, by, x0 + pad + bw * 0.62, by + c["BARRE_H"], 0x7A0A0A, 255, palette("DANGER"), 255)
    e.texte(mort, x0 + L - pad, by + c["BARRE_H"] / 2, P["mini"], mix(palette("DANGER"), 0xFFFFFF, 0.55), align=1)
    e.rect(x0 + pad, y0 + c["SEP_Y"], x0 + L - pad, y0 + c["SEP_Y"] + 1 / 3, 0xFFFFFF, 22)
    if etat == "libre":
        lignes = [((k["shift"], k["use"]), T["hud.reanimation.revive_action"], palette("OK"), None),
                  ((k["use"],), T["hud.reanimation.search_action"], palette("TEXTE"), None),
                  ((k["i"],), T["hud.reanimation.execute_action"], mix(palette("DANGER"), 0xFFFFFF, 0.25), T["hud.reanimation.hold_hint"])]
        for i, (tt, lab, col, suite) in enumerate(lignes):
            cy = y0 + c["ACTION_Y"] + i * c["ACTION_PAS"]
            x = x0 + pad
            for j, nom in enumerate(tt):
                if j:
                    x += e.texte("+", x + c["ECART_PLUS"], cy, P["mini"], palette("ATTENUE")) + 2 * c["ECART_PLUS"]
                x += touche(e, nom, x, cy, remplissage=0.4 if (i == 2 and etat == "libre") else 0, couleur=palette("DANGER"))
            x += c["ECART"]
            x += e.texte(lab, x, cy, P["mini"], col, maxw=x0 + L - pad - x, quoi="action")
            if suite:
                e.texte(suite, x + c["ECART_SUITE"], cy, P["mini"], palette("TRES_ATTENUE"), maxw=x0 + L - pad - x - c["ECART_SUITE"], quoi="maintenir")
    else:
        lab, col = {"reanime": (T["hud.reanimation.target_revived"], palette("OK")),
                    "fouille": (T["hud.reanimation.target_searched"], palette("ALERTE"))}[etat]
        e.texte(lab, x0 + pad, y0 + c["ACTION_Y"], P["mini"], col, maxw=L - 2 * pad, quoi="état")
    e.sauver(dossier / ("carte_%s_%s.png" % (etat, LANGUE[:2])), (x0 - 40, y0 - 30, x0 + L + 10, y0 + H + 10))


# ---------------------------------------------------------------- (c) réanimation, côté réanimateur (HudReanimateur)
def reanimateur(etat, dossier):
    T = langue(REANIM)
    c = C["reanim"]
    e = Ecran(fond())
    cx, cy = GW / 2, GH / 2
    a = 1.0
    if etat == "en_cours":
        anneau(e, cx, cy, 0.45, palette("OK"))
        e.texte(secondes(3.3), cx, cy, P["chiffre_petit"], palette("TEXTE"), align=0)
        l2, c2 = T["hud.reanimation.move_to_cancel"], palette("ATTENUE")
    elif etat == "attente":
        anneau(e, cx, cy, 1.0, palette("OK"), alpha=C["anneau"]["ATTENTE_ALPHA"])
        an = C["anneau"]
        e.arc(cx, cy, an["RAYON"] - an["EPAISSEUR"] / 2, an["RAYON"] + an["EPAISSEUR"] / 2, 0.30, 0.30 + an["ATTENTE_ARC"], 0xFFFFFF, 230)
        l2, c2 = T["hud.reanimation.attente"], palette("ATTENUE")
    elif etat == "reussie":
        anneau(e, cx, cy, 1.0, palette("OK"))
        coche(e, cx, cy, palette("OK"))
        l2, c2 = T["hud.reanimation.reussie"], palette("OK")
    else:
        anneau(e, cx, cy, 0.38, palette("DANGER"), piste=True)
        l2, c2 = T["hud.reanimation.interrompue"], mix(palette("DANGER"), 0xFFFFFF, 0.3)
    # « Réanimation de PSEUDO » : le pseudo en gras
    morceaux = T["hud.reanimation.reviving"].split("%s")
    avant, apres = morceaux[0], morceaux[1] if len(morceaux) > 1 else ""
    w = P["texte"].largeur(avant) + P["texte_gras"].largeur(PSEUDO) + P["texte"].largeur(apres)
    if w > c["LIGNE_MAX"]:
        e.defauts.append("[%s] ligne du nom %.1f > %.1f" % (LANGUE, w, c["LIGNE_MAX"]))
    x = cx - w / 2
    y1 = cy + c["LIGNE1_CY"]
    x += e.texte(avant, x, y1, P["texte"], palette("TEXTE"), 255 * a)
    x += e.texte(PSEUDO, x, y1, P["texte_gras"], palette("TEXTE"), 255 * a)
    e.texte(apres, x, y1, P["texte"], palette("TEXTE"), 255 * a)
    e.texte(l2, cx, cy + c["LIGNE2_CY"], P["mini"], c2, align=0, maxw=c["LIGNE_MAX"], quoi="ligne 2")
    e.sauver(dossier / ("reanimation_%s_%s.png" % (etat, LANGUE[:2])), (cx - 75, cy - 24, cx + 75, cy + 46))


# ---------------------------------------------------------------- (d) soins et grenades (HudSoins)
def soins(etat, dossier):
    T = langue(TACT)
    c = C["soins"]
    b = C["barre"]
    k = TOUCHES[LANGUE]
    e = Ecran(fond(seed=5))
    cx, cy = GW / 2, GH / 2
    reticule(e)
    bandage, grenade = OBJETS[LANGUE]
    nom = grenade if etat == "grenade" else bandage
    yb = cy + c["BARRE_DY"]
    if etat in ("en_cours", "fin"):
        p = 0.41 if etat == "en_cours" else 0.83
        barre_progression(e, cx, yb, p, palette("OK"))
        reste = tr(T, "overlay.heraliz_tactique.secondes", secondes(3.5 * (1 - p)))
        e.texte(reste, cx + b["LARGEUR"] / 2 + c["TEMPS_ECART"], yb + b["HAUTEUR"] / 2, P["mini"], palette("ATTENUE"))
    elif etat == "interrompu":
        barre_progression(e, cx, yb, 0.30, palette("DANGER"), 230)
    else:
        barre_progression(e, cx, yb, 0.64, palette("DANGER"))
    e.texte(nom, cx, cy + c["NOM_CY"], P["mini_gras"], palette("TEXTE"), align=0, maxw=c["LIGNE_MAX"], quoi="nom")
    cy2 = cy + c["AIDE_CY"]
    aa = 255 * c["AIDE_ALPHA"]
    if etat in ("en_cours", "fin"):
        lab = T["overlay.heraliz_tactique.soin_arreter"]
        tw = touche_largeur(k["use"])
        total = tw + c["ECART_AIDE"] + P["mini"].largeur(lab)
        if total > c["LIGNE_MAX"]:
            e.defauts.append("[%s] aide des soins %.1f > %.1f" % (LANGUE, total, c["LIGNE_MAX"]))
        x = cx - total / 2
        x += touche(e, k["use"], x, cy2) + c["ECART_AIDE"]
        e.texte(lab, x, cy2, P["mini"], palette("ATTENUE"), aa)
    elif etat == "interrompu":
        e.texte(T["overlay.heraliz_tactique.soin_interrompu"], cx, cy2, P["mini"], mix(palette("DANGER"), 0xFFFFFF, 0.3), align=0,
                maxw=c["LIGNE_MAX"], quoi="interrompu")
    else:
        e.texte(tr(T, "overlay.heraliz_tactique.explose", secondes(2.1)), cx, cy2, P["mini"], mix(palette("DANGER"), 0xFFFFFF, 0.45),
                align=0, maxw=c["LIGNE_MAX"], quoi="explose")
        doux = T["overlay.heraliz_tactique.lancer_doux"]
        pw = P["doux"].largeur(doux) + 2 * c["PUCE_PAD"]
        py = cy + c["DOUX_CY"]
        e.rect(cx - pw / 2, py - c["PUCE_H"] / 2, cx + pw / 2, py + c["PUCE_H"] / 2, palette("ATTENUE"), 40)
        e.bord(cx - pw / 2, py - c["PUCE_H"] / 2, pw, c["PUCE_H"], palette("ATTENUE"), 110)
        e.texte(doux, cx - pw / 2 + c["PUCE_PAD"], py, P["doux"], 0xD8DCE2, ombre=False, maxw=c["LIGNE_MAX"], quoi="lancer doux")
    e.sauver(dossier / ("soins_%s_%s.png" % (etat, LANGUE[:2])), (cx - 70, cy - 14, cx + 70, cy + 48))


# ---------------------------------------------------------------- (e) usure de l'arme (HudUsure)
def couleur_usure(etat):
    pal = C["palette"]
    return {"neuf": pal["OK"], "bon": mix(pal["OK"], pal["ALERTE"], C["usure"]["MELANGE_BON"]), "use": pal["ALERTE"],
            "abime": mix(pal["ALERTE"], pal["DANGER"], C["usure"]["MELANGE_ABIME"]), "ruine": pal["DANGER"]}[etat]


def usure(etat, dossier, courte=False):
    T = langue(USURE)
    c = C["usure"]
    pct, cle, maxi = {"ok": (87, "bon", 0.94), "critique": (12, "abime", 0.86), "ruinee": (0, "ruine", 0.86)}[etat]
    e = Ecran(fond(seed=9))
    reticule(e)
    # écran étroit (version courte) : 400 unités de large (1200 x 800 à l'échelle 3), barre DayZ réglée par défaut
    ecart, incl, gw = (2, 12, 400) if courte else (2, 12, GW)
    bord_barre = barre_dayz(e, w=gw, ecart=ecart, inclinaison=incl) if not courte else HOTBAR_BORD_DROIT(gw, ecart, incl)
    if not courte:
        hud_tacz(e)
    k = pente(c["INCLINAISON"])
    n = int(c["SEGMENTS"])
    barre_w = n * c["SEG_L"] + (n - 1) * c["SEG_ECART"]
    noms = [T["heraliz_usure.etat." + x] for x in ("neuf", "bon", "use", "abime", "ruine")]
    larg_pct = P["chiffre_petit"].largeur("100 %")
    L_pleine = math.ceil(c["PAD"] + barre_w + c["ECART"] + larg_pct + c["ECART"] + max(P["etat"].largeur(x) for x in noms) + c["PAD"])
    L_courte = c["PAD"] + barre_w + c["PAD"]
    H = c["H"]
    x1 = gw - c["DROITE"]
    est_courte = x1 - L_pleine < bord_barre + c["ECART_BARRE"]
    if est_courte != courte:
        e.defauts.append("[%s] usure : version %s attendue" % (LANGUE, "courte" if courte else "pleine"))
    L = L_courte if est_courte else L_pleine
    x0 = x1 - L
    if x0 < bord_barre + c["ECART_BARRE"] - 0.01:
        e.defauts.append("[%s] usure : touche la barre DayZ (%.1f < %.1f)" % (LANGUE, x0, bord_barre + c["ECART_BARRE"]))
    y0 = GH - c["BARRE_MILIEU"] - H / 2
    cy = y0 + H / 2
    col = couleur_usure(cle)
    ruinee = cle == "ruine"
    verre_penche(e, x0, y0, x0 + L, y0 + H, k, c["VERRE_ALPHA"])
    if ruinee:
        bord_penche(e, x0, y0, x0 + L, y0 + H, k, 2 / 3, palette("DANGER"), 255 * 0.75)
        bord_penche(e, x0 - 2 / 3, y0 - 2 / 3, x0 + L + 2 / 3, y0 + H + 2 / 3, k, 1, palette("DANGER"), 255 * 0.18)
    sx, sy = x0 + c["PAD"], cy - c["SEG_H"] / 2
    if ruinee:
        segments(e, sx, sy, barre_w, c["SEG_H"], k, n, c["SEG_ECART"], 0, 0, 0, palette("DANGER"), 255 * c["RUINE_ALPHA"], 1, 0, 0)
    else:
        segments(e, sx, sy, barre_w, c["SEG_H"], k, n, c["SEG_ECART"], pct / 100, col, 255, 0xFFFFFF, 255 * c["PISTE_ALPHA"], maxi,
                 palette("DANGER"), 255 * c["PERDU_ALPHA"])
    if not est_courte:
        x_pct = sx + barre_w + c["ECART"]
        e.texte("%d %%" % pct, x_pct, cy, P["chiffre_petit"], mix(palette("DANGER"), 0xFFFFFF, 0.25) if ruinee else palette("TEXTE"))
        x_nom = x_pct + larg_pct + c["ECART"]
        e.texte(T["heraliz_usure.etat." + cle], x_nom, cy, P["etat"], mix(col, 0xFFFFFF, 0.3),
                maxw=x0 + L - c["PAD"] - x_nom + 0.01, quoi="état")
    nom = "usure_%s%s_%s.png" % (etat, "_courte" if courte else "", LANGUE[:2])
    e.sauver(dossier / nom, (gw - L - 50, GH - 60, gw, GH))


def main():
    global PROJET, LANGUE
    ap = argparse.ArgumentParser()
    ap.add_argument("dossier", nargs="?")
    ap.add_argument("--projet", default=os.environ.get("HERALIZ_PROJET"))
    ap.add_argument("--refaites", action="store_true", help="seulement à terre, soins et usure (retours du 25/10)")
    args = ap.parse_args()
    if args.projet:
        PROJET = Path(args.projet)
    dossier = Path(args.dossier) if args.dossier else RACINE / "maquettes" / "hud_refonte"
    dossier.mkdir(parents=True, exist_ok=True)
    charger_moteur()
    charger_constantes()
    charger_polices()
    for code in LANGUES:
        LANGUE = code
        for etat in ("saigne", "reanime", "fouille"):
            a_terre(etat, dossier)
        if not args.refaites:
            for etat in ("libre", "reanime"):
                carte(etat, dossier)
            for etat in ("en_cours", "attente", "reussie", "interrompue"):
                reanimateur(etat, dossier)
        for etat in ("en_cours", "fin", "interrompu", "grenade"):
            soins(etat, dossier)
        for etat in ("ok", "critique", "ruinee"):
            usure(etat, dossier)
        usure("critique", dossier, courte=True)
    if DEFAUTS:
        for d in DEFAUTS:
            print(d)
        sys.exit(1)
    print("aucun texte ne dépasse (fr, en, ru) ->", dossier)


if __name__ == "__main__":
    main()
