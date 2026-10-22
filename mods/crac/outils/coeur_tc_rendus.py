#!/usr/bin/env python3
"""
Propositions (rendus seulement) du cœur de clan et de la nouvelle armoire à outils (TC) de CRac, saison des clans.

Rien n'est installé dans le mod : ce script fait des images à montrer au propriétaire (règle 9 : rendus validés
d'abord). Il reprend le moteur de explosifs.py (cubes, matières peintes au calcul, rendu par le calcul).

    python mods/crac/outils/coeur_tc_rendus.py --apercu DOSSIER

Coordonnées en pixels de modèle (16 = 1 bloc), face avant au nord (z petit).
  - armoire « de survie » : 1 bloc de large, 2 de haut, tôles rouillées de récupération, palette, chaîne et cadenas,
    batterie de voiture, et un petit écran rouillé qui bugue (entretien demandé et temps avant disparition) ;
  - cœur C « Radio du clan » : radio militaire de campagne sur sacs de sable, compteur à volets, antenne ;
  - cœur D « Étendard » : mât et bannière du clan plantés dans des parpaings, compteur sur le socle ;
  - cœur E « Coffre de récup » : vieux coffre-fort renforcé, chaînes, tag du clan, compteur à volets.
  (Propositions A « Noyau » et B « Coffre » noir et or du 22/10 refusées : trop propres, hors du style.)
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


# =============================================================================================== images des cœurs
def image_compteur_meca(texte="012450", larg=360, haut=90):
    """Compteur mécanique à volets (fond noir, chiffres noirs sur volets crème, trait de pliure)."""
    police = ImageFont.truetype(str(MONO if MONO.exists() else ex.POLICE), 64)
    im = Image.new("RGB", (larg, haut), (14, 14, 14))
    d = ImageDraw.Draw(im)
    n = len(texte)
    w = (larg - 8 * (n + 1)) / n
    for k, ch in enumerate(texte):
        x0 = 8 + k * (w + 8)
        d.rounded_rectangle((x0, 8, x0 + w, haut - 8), 5, fill=(214, 204, 176))
        tw = d.textlength(ch, font=police)
        d.text((x0 + (w - tw) / 2, 6), ch, font=police, fill=(24, 22, 20))
        d.line((x0, haut / 2, x0 + w, haut / 2), fill=(70, 64, 54), width=3)
    a = np.asarray(im, float) / 255
    sale = fbm(np.dstack([*np.meshgrid(np.arange(larg) / 20, np.arange(haut) / 20), np.zeros((haut, larg))]), 1, 3, 70)
    a = a * (0.82 + 0.25 * sale[..., None])
    return np.clip(a, 0, 1), np.zeros_like(a)


def image_drapeau(larg=320, haut=420):
    """Exemple de drapeau de clan (en jeu : la vraie bannière du clan) : tissu usé, tag du clan."""
    im = Image.new("RGB", (larg, haut), (118, 22, 20))
    d = ImageDraw.Draw(im)
    d.rectangle((0, 0, larg, 26), fill=(30, 28, 26))
    d.rectangle((0, haut - 26, larg, haut), fill=(30, 28, 26))
    cx, cy = larg / 2, haut * 0.44
    d.ellipse((cx - 90, cy - 90, cx + 90, cy + 90), outline=(228, 220, 200), width=14)
    d.polygon([(cx, cy - 70), (cx + 22, cy - 10), (cx + 70, cy), (cx + 22, cy + 12), (cx, cy + 70), (cx - 22, cy + 12),
               (cx - 70, cy), (cx - 22, cy - 10)], fill=(228, 220, 200))
    police = ImageFont.truetype(str(ex.POLICE), 64)
    t = "[HZ]"
    d.text((cx - d.textlength(t, font=police) / 2, haut * 0.72), t, font=police, fill=(228, 220, 200))
    a = np.asarray(im, float) / 255
    yy, xx = np.mgrid[0:haut, 0:larg]
    p = np.dstack([xx / 30, yy / 30, np.zeros_like(xx, float)])
    a = a * (0.75 + 0.35 * fbm(p, 1, 4, 71))[..., None]               # tissu sale
    a = a * (0.9 + 0.1 * (np.sin(xx * 0.9) * np.sin(yy * 0.9)))[..., None]  # trame
    dechire = (larg - xx) < 14 + 30 * fbm(p * 3, 1, 3, 72)              # bord libre effiloché
    alpha = (~dechire).astype(float)
    return np.clip(a, 0, 1), alpha


def peindre_coeurs(mat, p, n, fu, fv, w, h, prm):
    sh = p.shape[:2]
    a = np.ones(sh)
    g = np.zeros(sh + (3,))
    e = ex.bords(fu, fv, w, h)
    usure = np.exp(-e / 0.15)
    gr = prm.get("graine", 0)
    if mat == "olive":                                               # peinture militaire, propre mais vécue
        r = melange(col(72, 80, 56), col(86, 94, 66), fbm(p, 0.6, 3, 80 + gr))
        r = melange(r, col(124, 128, 104), usure * 0.55)              # arêtes frottées
        r = melange(r, col(110, 64, 32), np.clip((fbm(p, 1.4, 4, 81 + gr) - 0.66) * 5, 0, 1) * (usure + 0.3))
        r = r * (0.95 + 0.08 * bruit(p, 10, 82 + gr))[..., None]
    elif mat == "pochoir":                                          # inscriptions blanches au pochoir sur olive
        return peindre_coeurs("olive", p, n, fu, fv, w, h, prm)
    elif mat == "parpaing":
        trous = (np.abs(np.sin(p[..., 0] * 2.1)) * np.abs(np.sin(p[..., 1] * 2.1 + p[..., 2] * 2.1)))
        r = melange(col(104, 104, 98), col(138, 136, 128), fbm(p, 1.0, 3, 83 + gr))
        r = r * (0.88 + 0.12 * bruit(p, 14, 84 + gr))[..., None] * (1 - 0.15 * (trous > 0.92))[..., None]
        r = melange(r, col(70, 70, 66), usure * 0.4)
    elif mat == "tissu_img":
        img, al = prm["img"]
        x0, x1, y0, y1 = prm["cadre"]
        aa = np.clip((x1 - p[..., 0]) / (x1 - x0), 0, 1) if prm.get("nord") else np.clip((p[..., 0] - x0) / (x1 - x0), 0, 1)
        bb = (y1 - p[..., 1]) / (y1 - y0)
        r = np.stack([ex.echantillon(img[..., k], aa, bb) for k in range(3)], -1)
        a = (ex.echantillon(al, aa, bb) > 0.5).astype(float)
        r = r * (0.85 + 0.15 * np.sin(fu * 18) ** 2)[..., None] if prm.get("plis") else r
    elif mat == "papier_cadran":
        r = melange(col(210, 200, 170), col(180, 170, 140), fbm(p, 1.5, 2, 85))
        rr = np.hypot(fu - 0.5, fv - 0.5)
        graduations = (np.abs(np.sin(np.arctan2(fv - 0.5, fu - 0.5) * 12)) > 0.9) & (rr > 0.32) & (rr < 0.45)
        r = np.where(graduations[..., None], col(30, 28, 24), r)
    elif mat == "lampe_rouge":
        c = np.exp(-((fu - 0.5) ** 2 + (fv - 0.4) ** 2) / 0.15)
        r = melange(col(170, 20, 16), col(255, 140, 120), c)
        g = r
    else:
        return peindre(mat, p, n, fu, fv, w, h, prm)
    return np.clip(r, 0, 1), a, np.clip(g, 0, 1)


def cadran(B, os_, x0, y0, x1, y1, z):
    """Cadran rond (8 pans) avec aiguille, posé sur une face nord."""
    cx, cy, ray = (x0 + x1) / 2, (y0 + y1) / 2, (x1 - x0) / 2
    demi = ray * math.tan(math.radians(22.5))
    for k in range(4):
        B(os_, (cx - ray, cy - demi, z - 0.2), (cx + ray, cy + demi, z), "chaine", rot=(0, 0, k * 45), pivot=(cx, cy, z))
    B(os_, (cx - ray * 0.8, cy - demi * 0.8, z - 0.25), (cx + ray * 0.8, cy + demi * 0.8, z - 0.2), "papier_cadran",
      rot=(0, 0, 0), pivot=(cx, cy, z))
    B(os_, (cx - 0.08, cy - 0.08, z - 0.3), (cx + ray * 0.7, cy + 0.08, z - 0.25), "borne_rouge", rot=(0, 0, 35),
      pivot=(cx, cy, z))


# =============================================================================================== cœur C : balise radio
def modele_coeur_radio():
    """Grosse radio militaire de campagne posée sur des sacs de sable : compteur à volets, cadrans, antenne qui
    clignote. Le clan « émet » ses points."""
    os_ = [Os("coeur")]
    cubes = []
    B = lambda *a, **k: cubes.append(Cube(*a, **k))
    # sacs de sable (deux rangs)
    for k, (x0, z0) in enumerate(((0.3, 1.0), (5.6, 0.6), (10.9, 1.1), (0.6, 8.2), (5.9, 8.6), (11.0, 8.0))):
        B("coeur", (x0, 0.0, z0), (x0 + 5.0, 2.6, z0 + 6.6), "jute", prm={"graine": k})
    # caisse de la radio
    B("coeur", (1.5, 2.6, 3.0), (14.5, 12.6, 13.5), "olive")
    B("coeur", (1.2, 12.6, 2.7), (14.8, 13.4, 13.8), "olive", prm={"graine": 2})
    for x0 in (1.0, 14.2):                                           # cornières
        B("coeur", (x0, 2.6, 2.8), (x0 + 0.8, 13.4, 3.6), "acier_brut")
    # façade : compteur à volets, deux cadrans, boutons, voyant, plaque
    img = image_compteur_meca()
    B("coeur", (2.6, 8.4, 2.4), (13.4, 11.6, 3.0), "plastique")
    B("coeur", (3.0, 8.8, 2.35), (13.0, 11.2, 2.4), "ecran_img", faces=("north",),
      prm={"img": img, "cadre": (3.0, 13.0, 8.8, 11.2)})
    cadran(B, "coeur", 2.8, 3.6, 6.6, 7.4, 3.0)
    cadran(B, "coeur", 9.4, 3.6, 13.2, 7.4, 3.0)
    for x0 in (7.2, 8.3):
        B("coeur", (x0, 6.2, 2.4), (x0 + 0.8, 7.0, 3.0), "plastique")
    B("coeur", (7.4, 4.0, 2.5), (8.8, 5.4, 3.0), "lampe_rouge")
    plaque = masque_texte(["RÉSERVE DU CLAN"], 72)
    B("coeur", (4.0, 12.0, 2.95), (12.0, 12.5, 3.0), "olive", faces=("north",),
      decals=(Decal(plaque, (12.0, 12.5, 2.95), (-8.0, 0, 0), (0, -0.5, 0), col(220, 216, 200), 0.9,
                    normale=(0, 0, -1)),))
    # poignées sur le dessus, antenne fouet avec lampe
    for x0 in (3.5, 11.0):
        B("coeur", (x0, 13.4, 7.6), (x0 + 1.5, 14.2, 8.4), "acier_brut")
    B("coeur", (4.0, 14.0, 7.6), (12.5, 14.4, 8.4), "acier_brut")
    B("coeur", (12.4, 13.4, 11.4), (13.6, 14.2, 12.6), "acier_brut")
    B("coeur", (12.85, 14.2, 11.85), (13.15, 30.5, 12.15), "cable")
    B("coeur", (12.5, 30.5, 11.5), (13.5, 31.5, 12.5), "lampe_rouge")
    # câble qui pend vers une batterie au sol
    B("coeur", (14.5, 2.6, 9.0), (16.0, 7.6, 13.0), "plastique")
    B("coeur", (14.8, 7.6, 9.4), (15.4, 8.1, 10.0), "borne_rouge")
    return os_, cubes


# =============================================================================================== cœur D : étendard
def modele_coeur_etendard():
    """Étendard du clan planté dans un socle de parpaings et d'acier : la vraie bannière du clan flotte au-dessus,
    compteur à volets sur le socle. Se voit de loin (2 blocs de haut)."""
    os_ = [Os("coeur")]
    cubes = []
    B = lambda *a, **k: cubes.append(Cube(*a, **k))
    for k, (x0, z0) in enumerate(((0.5, 0.5), (8.0, 0.5), (0.5, 8.0), (8.0, 8.0))):
        B("coeur", (x0, 0.0, z0), (x0 + 7.5, 4.0, z0 + 7.5), "parpaing", prm={"graine": k})
    B("coeur", (1.5, 4.0, 1.5), (14.5, 5.2, 14.5), "plaque_soudee", prm={"graine": 3})
    B("coeur", (5.0, 5.2, 5.0), (11.0, 7.0, 11.0), "acier_brut")
    # jambes de force, du bord du socle vers le mât
    B("coeur", (7.6, 5.2, 3.0), (8.4, 11.0, 3.8), "acier_brut", rot=(38, 0, 0), pivot=(8.0, 5.2, 3.4))
    B("coeur", (7.6, 5.2, 12.2), (8.4, 11.0, 13.0), "acier_brut", rot=(-38, 0, 0), pivot=(8.0, 5.2, 12.6))
    B("coeur", (3.0, 5.2, 7.6), (3.8, 11.0, 8.4), "acier_brut", rot=(0, 0, -38), pivot=(3.4, 5.2, 8.0))
    B("coeur", (12.2, 5.2, 7.6), (13.0, 11.0, 8.4), "acier_brut", rot=(0, 0, 38), pivot=(12.6, 5.2, 8.0))
    B("coeur", (7.4, 7.0, 7.4), (8.6, 31.6, 8.6), "acier_brut")       # mât
    B("coeur", (7.2, 31.2, 7.2), (8.8, 32.0, 8.8), "laiton")
    # drapeau (3 bandes légèrement tournées : il flotte)
    img = image_drapeau()
    x = 8.6
    for k, (lg, ang) in enumerate(((2.6, 6), (2.6, -8), (2.6, 10))):
        B("coeur", (x, 14.0, 7.9), (x + lg, 31.0, 8.1), "tissu_img", faces=("north", "south"),
          rot=(0, ang, 0), pivot=(x, 14.0, 8.0),
          prm={"img": img, "cadre": (8.6, 8.6 + 7.8, 14.0, 31.0), "plis": True, "nord": True})
        x += lg * math.cos(math.radians(ang))
    # compteur et plaque sur le socle
    imgc = image_compteur_meca()
    B("coeur", (2.0, 0.6, 0.0), (14.0, 3.4, 0.5), "plastique")
    B("coeur", (2.4, 0.9, -0.05), (13.6, 3.1, 0.0), "ecran_img", faces=("north",),
      prm={"img": imgc, "cadre": (2.4, 13.6, 0.9, 3.1)})
    return os_, cubes


# =============================================================================================== cœur E : coffre de récup
def modele_coeur_coffre_recup():
    """Vieux coffre-fort de banque récupéré et renforcé : plaques soudées, chaînes, tag du clan à la bombe, compteur
    à volets vissé dessus, lampe rouge."""
    os_ = [Os("coeur")]
    cubes = []
    B = lambda *a, **k: cubes.append(Cube(*a, **k))
    B("coeur", (0.5, 0.0, 0.5), (15.5, 1.5, 15.5), "bois")
    B("coeur", (1.2, 1.5, 1.6), (14.8, 15.0, 14.8), "tole_peinte", prm={"graine": 11})
    B("coeur", (1.6, 2.4, 1.1), (14.4, 13.6, 1.6), "acier_brut", prm={"graine": 4})   # porte
    B("coeur", (2.4, 3.2, 0.9), (13.6, 12.8, 1.1), "tole_peinte", prm={"graine": 12})
    # volant (moyeu + 4 rayons)
    for k in range(4):
        B("coeur", (5.0, 7.7, 0.4), (11.0, 8.3, 0.9), "chaine", rot=(0, 0, k * 45), pivot=(8.0, 8.0, 0.65))
    B("coeur", (7.0, 7.0, 0.3), (9.0, 9.0, 0.9), "laiton")
    # plaques soudées et sangles de chaîne
    B("coeur", (1.0, 4.0, 3.0), (1.2, 12.0, 13.0), "plaque_soudee", prm={"graine": 6})
    B("coeur", (14.8, 3.0, 2.5), (15.0, 9.0, 9.0), "plaque_soudee", prm={"graine": 7})
    for y0 in (4.2, 11.0):
        B("coeur", (0.9, y0, 0.8), (15.1, y0 + 0.7, 15.1), "chaine")
    B("coeur", (11.0, 10.4, 0.2), (12.6, 12.4, 0.8), "laiton")         # cadenas
    # tag du clan à la bombe sur le flanc
    tag = np.asarray(Image.fromarray((masque_texte(["[HZ]"], 120) * 255).astype(np.uint8))
                     .filter(ImageFilter.GaussianBlur(4)), float) / 255
    B("coeur", (1.1, 2.0, 2.0), (1.2, 14.0, 14.0), "tole_peinte", faces=("west",), prm={"graine": 13},
      decals=(Decal(tag, (1.1, 11.5, 3.0), (0, 0, 10.0), (0, -6.0, 0), col(230, 40, 30), 0.9, normale=(-1, 0, 0)),))
    # compteur à volets et lampe sur le dessus de la porte
    img = image_compteur_meca()
    B("coeur", (3.0, 13.8, 0.6), (13.0, 16.0, 1.6), "plastique")
    B("coeur", (3.3, 14.1, 0.55), (12.7, 15.7, 0.6), "ecran_img", faces=("north",),
      prm={"img": img, "cadre": (3.3, 12.7, 14.1, 15.7)})
    B("coeur", (13.4, 15.0, 13.0), (14.6, 16.0, 14.2), "lampe_rouge")
    return os_, cubes



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
    ex.peindre = peindre_coeurs
    c = faire("coeur_C_radio", modele_coeur_radio, dos)
    d = faire("coeur_D_etendard", modele_coeur_etendard, dos)
    e = faire("coeur_E_coffre_recup", modele_coeur_coffre_recup, dos)
    ex.planche([c, d, e], ["Cœur C : Radio du clan", "Cœur D : Étendard", "Cœur E : Coffre de récup"],
               dos / "coeurs.png")
    ex.planche([a], ["Armoire (TC) de survie"], dos / "armoire.png")
    img, _ = image_ecran_tc()
    Image.fromarray((img * 255).astype(np.uint8)).resize((720, 432), Image.NEAREST).save(dos / "ecran_armoire.png")
    print("rendus dans", dos)


if __name__ == "__main__":
    main()
