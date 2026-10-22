#!/usr/bin/env python3
"""
Propositions (rendus seulement) du cœur de clan et de la nouvelle armoire à outils (TC) de CRac, saison des clans.

Rien n'est installé dans le mod : ce script fait des images à montrer au propriétaire (règle 9 : rendus validés
d'abord). Il reprend le moteur de explosifs.py (cubes, matières peintes au calcul, rendu par le calcul).

    python mods/crac/outils/coeur_tc_rendus.py --apercu DOSSIER

Coordonnées en pixels de modèle (16 = 1 bloc), face avant au nord (z petit).
  - armoire « de survie » : 1 bloc de large, 2 de haut, tôles rouillées de récupération, palette, chaîne et cadenas,
    batterie de voiture, et un petit écran rouillé qui bugue (entretien demandé et temps avant disparition) ;
  - cœur A « Noyau » : noyau lumineux dans une cage d'acier sur un socle à huit pans ;
  - cœur B « Coffre » : coffre blindé à volant, fente lumineuse et compteur de points.
Les textes des écrans sont des exemples : en jeu, ils seront écrits en direct.
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import explosifs as ex  # noqa: E402
from explosifs import Cube, Decal, Os, bruit, col, fbm, masque_texte, melange  # noqa: E402

MONO = Path("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf")


# =============================================================================================== images d'écran
def image_ecran_tc(larg=360, haut=216):
    """Écran ambre de l'armoire : entretien demandé et temps avant disparition, avec ses défauts (rouille, bugs)."""
    police = ImageFont.truetype(str(MONO if MONO.exists() else ex.POLICE), 40)
    petite = ImageFont.truetype(str(MONO if MONO.exists() else ex.POLICE), 26)
    im = Image.new("L", (larg, haut), 0)
    d = ImageDraw.Draw(im)
    d.text((14, 6), "BESOIN /24H", font=petite, fill=170)
    d.text((14, 38), "12 PLANCHES", font=police, fill=255)
    d.text((14, 84), " 4 CLOUS", font=police, fill=255)
    d.line((14, 136, larg - 14, 136), fill=120, width=3)
    d.text((14, 146), "DEPOP 23:41", font=police, fill=255)
    a = np.asarray(im, float) / 255
    rng = np.random.default_rng(5)
    # bandes décalées (le signal saute)
    for y0, h, dx in ((112, 6, 7), (168, 6, -10), (28, 3, 5)):
        a[y0:y0 + h] = np.roll(a[y0:y0 + h], dx, axis=1)
    rouge = np.roll(a, 3, axis=1) * 0.55                          # image fantôme décalée (couleurs qui bavent)
    lignes = (np.arange(haut) % 4 < 2)[:, None] * 0.85 + 0.15     # lignes de balayage
    neige = rng.random((haut, larg)) * 0.12
    bande = np.zeros(haut)
    bande[176:184] = 0.35                                         # bande de parasites
    fond = 0.06 + neige + bande[:, None] * rng.random((haut, larg))
    vignette = np.clip(1.25 - np.hypot(*np.meshgrid(np.linspace(-1, 1, larg), np.linspace(-1, 1, haut))) * 0.55, 0, 1)
    v = np.clip((np.maximum(a, rouge * 0.6) + fond) * lignes * vignette, 0, 1)
    ambre = np.stack([v * 1.0, v * 0.62, v * 0.12], -1)
    ambre[..., 0] = np.maximum(ambre[..., 0], rouge * lignes * 0.45)
    # coin mort : rouille qui a mangé l'écran, fêlure
    yy, xx = np.mgrid[0:haut, 0:larg]
    mort = (larg - xx) + (haut - yy) * 0.9 < 70 + 18 * np.sin(yy * 0.3)
    ambre[mort] = [0.10, 0.05, 0.03]
    fel = Image.new("L", (larg, haut), 0)
    fd = ImageDraw.Draw(fel)
    fd.line([(250, 0), (232, 44), (260, 80), (241, 130)], fill=255, width=3)
    fd.line([(232, 44), (205, 58)], fill=255, width=2)
    f = np.asarray(fel, float) / 255
    ambre = ambre * (1 - f[..., None] * 0.85) + f[..., None] * np.array([0.35, 0.3, 0.25]) * 0.6
    lueur = np.clip(ambre * 1.1, 0, 1) * (~mort)[..., None]
    return np.clip(ambre, 0, 1), lueur


def image_compteur(texte, couleur, larg=300, haut=90):
    police = ImageFont.truetype(str(MONO if MONO.exists() else ex.POLICE), 64)
    im = Image.new("L", (larg, haut), 0)
    d = ImageDraw.Draw(im)
    w = d.textlength(texte, font=police)
    d.text(((larg - w) / 2, 8), texte, font=police, fill=255)
    a = np.asarray(im.filter(ImageFilter.GaussianBlur(0.6)), float) / 255
    fond = np.asarray(Image.new("L", (larg, haut), 0), float)
    v = np.clip(a + 0.05 + fond, 0, 1)
    rgb = v[..., None] * np.array(couleur)
    return rgb, rgb * (a[..., None] > 0.3)


# =============================================================================================== matières en plus
_peindre_base = ex.peindre


def peindre(mat, p, n, fu, fv, w, h, prm):
    sh = p.shape[:2]
    a = np.ones(sh)
    g = np.zeros(sh + (3,))
    e = ex.bords(fu, fv, w, h)
    usure = np.exp(-e / 0.15)
    gr = prm.get("graine", 0)
    if mat in ("tole_rouille", "tole_peinte", "tole_ondulee"):
        rouille = melange(col(112, 58, 28), col(70, 38, 20), fbm(p, 0.5, 4, 40 + gr))
        rouille = melange(rouille, col(150, 86, 40), np.clip((fbm(p, 1.6, 3, 41 + gr) - 0.55) * 4, 0, 1) * 0.6)
        coulures = np.clip(bruit(p * np.array([3.0, 0.15, 3.0]), 1.0, 42 + gr) - 0.45, 0, 1) * 2
        if mat == "tole_peinte":
            peinture = melange(col(78, 88, 62), col(60, 70, 48), fbm(p, 0.7, 3, 43 + gr))
            reste = np.clip((fbm(p, 0.35, 5, 44 + gr) - 0.36) * 5, 0, 1) * (1 - usure)
            r = melange(rouille, peinture, reste)
        else:
            r = rouille
        r = melange(r, col(54, 30, 18), coulures * 0.55)
        if mat == "tole_ondulee":
            axe = p[..., 0] if abs(n[2]) > 0.5 else p[..., 2]
            onde = np.sin(axe * 2.2) * 0.5 + 0.5
            r = r * (0.78 + 0.32 * onde)[..., None]
        r = melange(r, col(40, 24, 16), usure * 0.5)
        r = r * (0.92 + 0.12 * bruit(p, 9.0, 45 + gr))[..., None]
    elif mat == "plaque_soudee":
        r = melange(col(84, 86, 84), col(62, 64, 64), fbm(p, 0.8, 3, 46 + gr))
        r = melange(r, col(110, 64, 34), np.clip((fbm(p, 0.6, 4, 47 + gr) - 0.5) * 3, 0, 1))
        cordon = np.exp(-e / 0.18)
        perles = np.sin((p[..., 0] + p[..., 1] + p[..., 2]) * 9) * 0.5 + 0.5
        r = melange(r, melange(col(48, 42, 38), col(120, 112, 100), perles), cordon * 0.9)
    elif mat == "danger":
        bande = ((p[..., 0] + p[..., 1] * 1.0 + p[..., 2]) % 3.0) < 1.5
        r = np.where(bande[..., None], col(200, 160, 30), col(26, 24, 22))
        ecaille = np.clip((fbm(p, 1.1, 4, 48 + gr) - 0.55) * 5, 0, 1)
        r = melange(r, col(104, 56, 28), ecaille)
    elif mat == "bois":
        fibres = np.sin(p[..., 0] * 1.3 + fbm(p * np.array([0.2, 1, 1]), 1.2, 3, 49 + gr) * 6) * 0.5 + 0.5
        r = melange(col(92, 72, 50), col(132, 110, 80), fibres)
        r = melange(r, col(66, 62, 58), np.clip((fbm(p, 0.6, 3, 50 + gr) - 0.45) * 2, 0, 1) * 0.6)  # bois grisé
        r = melange(r, col(40, 30, 22), usure * 0.6)
    elif mat == "acier_brut":
        r = melange(col(58, 60, 62), col(84, 86, 88), bruit(p * np.array([1, 6, 1.0]), 2.0, 51 + gr))
        r = melange(r, col(96, 54, 30), np.clip((fbm(p, 1.0, 3, 52 + gr) - 0.55) * 3, 0, 1))
        r = melange(r, col(140, 140, 136), usure * 0.5)
    elif mat == "chaine":
        r = melange(col(48, 46, 44), col(96, 90, 84), bruit(p, 6, 53))
        r = melange(r, col(110, 60, 30), np.clip((fbm(p, 2, 2, 54) - 0.5) * 3, 0, 1))
    elif mat == "laiton":
        r = melange(col(150, 116, 52), col(214, 176, 92), bruit(p, 4, 55) * 0.6 + 0.2 * (n[1] > 0.5))
        r = melange(r, col(80, 70, 40), np.clip((fbm(p, 2, 2, 56) - 0.55) * 3, 0, 1))
        r = melange(r, col(240, 214, 140), usure * 0.6)
    elif mat == "plastique":
        r = melange(col(22, 22, 24), col(36, 36, 40), bruit(p, 10, 57))
        r = melange(r, col(70, 70, 74), usure * 0.4)
    elif mat == "borne_rouge":
        r = np.broadcast_to(col(160, 30, 26), sh + (3,)).copy()
    elif mat == "cable":
        r = melange(col(14, 14, 16), col(38, 38, 42), bruit(p, 6, 58))
    elif mat == "ecran_img":
        img, lu = prm["img"]
        x0, x1, y0, y1 = prm["cadre"]
        aa = (x1 - p[..., 0]) / (x1 - x0)   # face nord vue de face : l'est (+x) est à gauche
        bb = (y1 - p[..., 1]) / (y1 - y0)
        r = np.stack([ex.echantillon(img[..., k], aa, bb) for k in range(3)], -1)
        g = np.stack([ex.echantillon(lu[..., k], aa, bb) for k in range(3)], -1)
    elif mat == "lampe_ambre":
        c = np.exp(-((fu - 0.5) ** 2 + (fv - 0.4) ** 2) / 0.15)
        r = melange(col(200, 110, 20), col(255, 220, 140), c)
        g = r * 0.9
    # ------------------------------------------------------------------------------------ cœur (propre)
    elif mat == "acier_noir":
        brosse = bruit(p * np.array([1, 10.0, 1]), 3.0, 60 + gr)
        r = melange(col(30, 32, 36), col(46, 49, 54), brosse * 0.7 + 0.2 * (n[1] > 0.5))
        r = melange(r, col(120, 126, 134), np.exp(-e / 0.08) * 0.7)      # arêtes nettes, éclairées
    elif mat == "acier_mat":
        r = melange(col(52, 55, 60), col(64, 67, 72), bruit(p, 12, 61 + gr) * 0.5)
        r = melange(r, col(130, 136, 144), np.exp(-e / 0.08) * 0.6)
    elif mat == "or":
        r = melange(col(176, 132, 44), col(236, 196, 96), bruit(p * np.array([1, 8, 1.0]), 3, 62) * 0.6
                    + 0.25 * (n[1] > 0.5))
        r = melange(r, col(255, 236, 170), np.exp(-e / 0.08) * 0.8)
    elif mat == "noyau":
        c = prm.get("couleur", (255, 60, 30))
        base = col(*c)
        tour = math.atan2(1, 1)
        ang = np.arctan2(p[..., 2] - prm["cz"], p[..., 0] - prm["cx"])
        spirale = np.sin(ang * 3 + p[..., 1] * 0.9 + fbm(p, 0.8, 3, 63) * 5) * 0.5 + 0.5
        veines = np.clip(1 - np.abs(fbm(p, 0.7, 4, 64) - 0.5) * 9, 0, 1)
        r = melange(base * 0.55, np.clip(base * 1.3 + 0.25, 0, 1), spirale * 0.6 + veines * 0.6)
        g = np.clip(r * 1.05, 0, 1)
        del tour
    elif mat == "verre_fume":
        r = melange(col(20, 24, 28), col(34, 40, 46), fbm(p, 1.2, 2, 65))
        reflet = np.clip(1 - np.abs(fu - fv - 0.2) / 0.08, 0, 1)
        r = r + 0.18 * reflet[..., None]
    elif mat == "joint_noir":
        r = np.broadcast_to(col(12, 12, 14), sh + (3,)).copy()
    else:
        return _peindre_base(mat, p, n, fu, fv, w, h, prm)
    return np.clip(r, 0, 1), a, np.clip(g, 0, 1)


ex.peindre = peindre


def prisme(B, os_, cx, cz, y0, y1, ray, mat, pans=8, prm=None, faces=ex.FACES):
    """Prisme vertical à 8 (ou 6) pans autour de (cx, cz) : pavés tournés autour de l'axe y."""
    pas = 180 / (pans // 2)
    demi = ray * math.tan(math.radians(180 / pans))
    for k in range(pans // 2):
        B(os_, (cx - ray, y0, cz - demi), (cx + ray, y1, cz + demi), mat, rot=(0, k * pas, 0), pivot=(cx, y0, cz),
          prm=prm, faces=faces)


# =============================================================================================== armoire « de survie »
def modele_tc():
    os_ = [Os("tc")]
    cubes = []
    B = lambda *a, **k: cubes.append(Cube(*a, **k))
    # palette de récupération (y 0..2.5)
    for z0 in (1.0, 7.25, 13.5):
        B("tc", (0.5, 0, z0), (15.5, 1.2, z0 + 1.5), "bois", prm={"graine": int(z0)})
    for x0 in (0.5, 4.2, 7.9, 11.6):
        B("tc", (x0, 1.2, 0.8), (x0 + 3.4, 2.4, 15.5), "bois", prm={"graine": int(x0 * 3)})
    # caisse : flancs en tôle ondulée rouillée, dos en tôle, toit qui déborde
    B("tc", (1.0, 2.4, 2.0), (15.0, 30.0, 15.0), "tole_ondulee")
    B("tc", (0.4, 30.0, 1.0), (15.6, 31.2, 15.8), "tole_ondulee", prm={"graine": 3})
    # façade : porte du bas peinte (vieux vert) avec plaques soudées, bande danger, porte du haut
    B("tc", (1.5, 3.0, 1.2), (14.5, 13.5, 2.0), "tole_peinte")
    B("tc", (2.2, 4.0, 0.9), (6.8, 7.8, 1.2), "plaque_soudee", prm={"graine": 1})
    B("tc", (9.4, 8.6, 0.9), (13.6, 12.6, 1.2), "plaque_soudee", prm={"graine": 2})
    B("tc", (1.5, 13.5, 1.0), (14.5, 15.0, 2.0), "danger")
    B("tc", (1.5, 15.0, 1.2), (14.5, 29.4, 2.0), "tole_peinte", prm={"graine": 5})
    # cornières d'acier aux angles avant
    for x0 in (0.7, 14.3):
        B("tc", (x0, 2.4, 0.8), (x0 + 1.0, 30.0, 2.2), "acier_brut")
    # pochoir sur le flanc : « NE PAS TOUCHER » à la bombe
    pochoir = np.asarray(Image.fromarray((masque_texte(["NE PAS", "TOUCHER"], 90) * 255).astype(np.uint8))
                         .filter(ImageFilter.GaussianBlur(3)), float) / 255
    B("tc", (0.6, 6.0, 4.0), (1.0, 22.0, 13.5), "tole_ondulee", prm={"graine": 8},
      decals=(Decal(pochoir, (0.6, 18.5, 4.5), (0, 0, 8.5), (0, -6.0, 0), col(150, 24, 18), 0.85,
                    normale=(-1, 0, 0)),))
    # écran rouillé dans son boîtier, vissé sur la porte du haut
    img = image_ecran_tc()
    B("tc", (2.8, 18.6, 0.2), (13.2, 26.6, 1.2), "acier_brut", prm={"graine": 9})
    B("tc", (3.5, 19.3, 0.15), (12.5, 25.9, 0.2), "ecran_img", faces=("north",),
      prm={"img": img, "cadre": (3.5, 12.5, 19.3, 25.9)})
    for vx in (3.1, 12.6):
        for vy in (19.0, 26.0):
            B("tc", (vx - 0.25, vy - 0.25, 0.05), (vx + 0.25, vy + 0.25, 0.2), "chaine")
    # interrupteurs, voyant ambre et petite lampe sous l'écran
    for k, x0 in enumerate((4.0, 5.6, 7.2)):
        B("tc", (x0, 16.4, 0.7), (x0 + 0.9, 17.6, 1.2), "plastique")
        B("tc", (x0 + 0.3, 17.1, 0.2), (x0 + 0.6, 17.4, 0.7), "acier_brut")
    B("tc", (10.6, 16.3, 0.6), (11.8, 17.5, 1.2), "lampe_ambre")
    # chaîne en travers de la porte du bas et cadenas
    for k in range(9):
        x = 2.3 + k * 1.35
        y = 11.6 - abs(k - 4) * 0.55 + 1.6
        B("tc", (x, y - 0.35, 0.45), (x + 1.1, y + 0.35, 0.9), "chaine", rot=(0, 0, 18 if k < 4 else -18),
          pivot=(x + 0.55, y, 0.6))
    B("tc", (7.0, 8.0, 0.1), (9.0, 10.4, 0.9), "laiton")
    B("tc", (7.3, 10.4, 0.35), (8.7, 11.6, 0.65), "chaine")
    # batterie de voiture sanglée sur le flanc droit, câbles vers l'écran
    B("tc", (15.0, 2.4, 4.0), (16.0, 9.0, 12.0), "plastique")
    B("tc", (15.4, 9.0, 5.0), (15.9, 9.6, 6.0), "borne_rouge")
    B("tc", (15.4, 9.0, 10.0), (15.9, 9.6, 11.0), "acier_brut")
    B("tc", (15.6, 9.6, 5.3), (15.9, 24.0, 5.7), "cable")
    B("tc", (14.4, 23.6, 0.6), (15.9, 24.0, 5.7), "cable", faces=("up", "down", "north", "east", "west"))
    # antenne bricolée sur le toit
    B("tc", (11.5, 31.2, 11.5), (12.5, 31.8, 12.5), "acier_brut")
    B("tc", (11.85, 31.8, 11.85), (12.15, 32.0, 12.15), "cable")
    return os_, cubes


# =============================================================================================== cœur A : noyau
def modele_coeur_noyau(couleur=(255, 70, 34)):
    os_ = [Os("coeur")]
    cubes = []
    B = lambda *a, **k: cubes.append(Cube(*a, **k))
    cx = cz = 8.0
    prisme(B, "coeur", cx, cz, 0.0, 1.6, 7.6, "acier_noir")
    prisme(B, "coeur", cx, cz, 1.6, 2.2, 7.0, "or")
    prisme(B, "coeur", cx, cz, 2.2, 3.4, 6.2, "acier_mat")
    prisme(B, "coeur", cx, cz, 3.4, 13.0, 3.0, "noyau", prm={"cx": cx, "cz": cz, "couleur": couleur})
    # cage : 8 barreaux et deux anneaux
    for k in range(8):
        a = math.radians(22.5 + k * 45)
        bx, bz = cx + 4.6 * math.cos(a), cz + 4.6 * math.sin(a)
        B("coeur", (bx - 0.45, 3.4, bz - 0.45), (bx + 0.45, 13.0, bz + 0.45), "acier_noir")
    for y0 in (6.6, 9.6):
        for k in range(8):
            a = math.radians(k * 45)
            mx, mz = cx + 4.6 * math.cos(a), cz + 4.6 * math.sin(a)
            B("coeur", (mx - 1.95, y0, mz - 0.25), (mx + 1.95, y0 + 0.5, mz + 0.25), "or",
              rot=(0, -k * 45 + 90, 0), pivot=(mx, y0, mz))
    # chapeau et emblème
    prisme(B, "coeur", cx, cz, 13.0, 14.0, 6.2, "acier_mat")
    prisme(B, "coeur", cx, cz, 14.0, 14.5, 5.0, "or")
    prisme(B, "coeur", cx, cz, 14.5, 15.4, 2.2, "acier_noir")
    B("coeur", (cx - 0.5, 15.4, cz - 0.5), (cx + 0.5, 16.0, cz + 0.5), "noyau",
      prm={"cx": cx, "cz": cz, "couleur": couleur})
    # compteur de points sur l'avant du socle
    img = image_compteur("12 450", (1.0, 0.36, 0.2))
    B("coeur", (4.4, 0.25, 0.25), (11.6, 1.45, 0.9), "acier_mat")
    B("coeur", (4.7, 0.4, 0.2), (11.3, 1.3, 0.25), "ecran_img", faces=("north",),
      prm={"img": img, "cadre": (4.7, 11.3, 0.4, 1.3)})
    return os_, cubes


# =============================================================================================== cœur B : coffre blindé
def modele_coeur_coffre(couleur=(255, 70, 34)):
    os_ = [Os("coeur")]
    cubes = []
    B = lambda *a, **k: cubes.append(Cube(*a, **k))
    B("coeur", (1.0, 0.0, 1.0), (15.0, 1.2, 15.0), "acier_mat")
    B("coeur", (1.5, 1.2, 1.5), (14.5, 14.2, 14.5), "acier_noir")
    B("coeur", (1.0, 14.2, 1.0), (15.0, 15.4, 15.0), "acier_mat")
    # arêtes en or
    for x0 in (1.2, 14.0):
        B("coeur", (x0, 1.2, 1.2), (x0 + 0.8, 14.2, 2.0), "or")
    B("coeur", (1.2, 13.4, 1.2), (14.8, 14.2, 2.0), "or")
    B("coeur", (1.2, 1.2, 1.2), (14.8, 2.0, 2.0), "or")
    # porte : volant et moyeu
    cx, cy = 8.0, 6.6
    prisme_face(B, cx, cy, 1.05, 1.5, 4.0, "acier_mat")
    prisme_face(B, cx, cy, 0.65, 1.05, 1.3, "or")
    for k in range(3):
        B("coeur", (cx - 3.4, cy - 0.3, 0.75), (cx + 3.4, cy + 0.3, 1.15), "or", rot=(0, 0, k * 60),
          pivot=(cx, cy, 0.95))
    # fente lumineuse et compteur au-dessus
    B("coeur", (3.0, 11.0, 1.2), (13.0, 12.6, 1.5), "joint_noir")
    B("coeur", (3.4, 11.3, 1.15), (12.6, 12.3, 1.2), "noyau", faces=("north",),
      prm={"cx": 8, "cz": 0, "couleur": couleur})
    img = image_compteur("12 450", (1.0, 0.36, 0.2))
    B("coeur", (4.0, 12.8, 1.1), (12.0, 13.4, 1.5), "joint_noir")
    B("coeur", (4.4, 12.85, 1.05), (11.6, 13.35, 1.1), "ecran_img", faces=("north",),
      prm={"img": img, "cadre": (4.4, 11.6, 12.85, 13.35)})
    # gonds et pieds
    for y0 in (3.0, 10.0):
        B("coeur", (13.6, y0, 0.8), (14.6, y0 + 2.0, 1.5), "acier_mat")
    for x0 in (1.0, 13.6):
        for z0 in (1.0, 13.6):
            B("coeur", (x0, 0.0, z0), (x0 + 1.4, 1.4, z0 + 1.4), "or")
    return os_, cubes


def prisme_face(B, cx, cy, z0, z1, ray, mat):
    """Disque à 8 pans posé sur la face avant (axe z)."""
    demi = ray * math.tan(math.radians(22.5))
    for k in range(4):
        B("coeur", (cx - ray, cy - demi, z0), (cx + ray, cy + demi, z1), mat, rot=(0, 0, k * 45), pivot=(cx, cy, z0))


# =============================================================================================== rendus
VUES = {"trois quarts": (math.radians(200), math.radians(22)), "face": (math.radians(180), math.radians(8)),
        "autre côté": (math.radians(140), math.radians(22)), "plongée": (math.radians(215), math.radians(42))}


def faire(nom, fab, dossier):
    os_, cubes = fab()
    tex, lueur, _, _ = ex.construire(os_, cubes)
    ims = [ex.rendu(os_, cubes, tex, lueur, set(), v, taille=1000) for v in VUES.values()]
    ex.planche(ims, list(VUES), dossier / f"{nom}_vues.png")
    return ims[0]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apercu", type=Path, required=True)
    args = ap.parse_args()
    dos = args.apercu
    dos.mkdir(parents=True, exist_ok=True)
    a = faire("armoire_survie", modele_tc, dos)
    b = faire("coeur_A_noyau", modele_coeur_noyau, dos)
    c = faire("coeur_B_coffre", modele_coeur_coffre, dos)
    ex.planche([a, b, c], ["Armoire (TC) de survie", "Cœur A : Noyau", "Cœur B : Coffre"], dos / "comparaison.png")
    img, _ = image_ecran_tc()
    Image.fromarray((img * 255).astype(np.uint8)).resize((720, 432), Image.NEAREST).save(dos / "ecran_armoire.png")
    print("rendus dans", dos)


if __name__ == "__main__":
    main()
