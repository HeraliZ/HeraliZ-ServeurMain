#!/usr/bin/env python3
"""
Coffres de stockage par rareté des bases (CRac) : modèles 3D, textures et rendus.

Proposition du 27/10 (rendus d'abord : règle 6 de CLAUDE.md). La caisse en métal devient la caisse Commune ; quatre
coffres de plus, chacun avec son modèle et son ouverture animée :
  - Peu commune (vert) : malle de chantier, 2 blocs de large, 1 de haut, couvercle qui bascule vers l'arrière ;
  - Rare (bleu)        : casier métallique, 1 bloc de large, 2 de haut, porte qui pivote ;
  - Épique (violet)    : caisse d'armes renforcée, 2 de large, 1 de haut, couvercle qui s'ouvre sur un calage en mousse ;
  - Légendaire (or)    : coffre-fort, 1 de large, 2 de haut, volant qui tourne puis porte blindée.

Les modèles sont des modèles de bloc de Minecraft (cuits : rien n'est dessiné à chaque image tant que le coffre est
fermé) ; seule la pièce qui bouge (couvercle, porte, volant) est dessinée à part pendant l'ouverture. Les rendus sont
faits depuis ces modèles de bloc, à la densité de texture du jeu (64 points par bloc) et avec l'éclairage par face de
Minecraft : ce qu'on voit est ce que le jeu dessinera. Rien n'est dessiné à la main : on modifie ce script.

    python modules/bases/outils/coffres_modeles.py --apercu DOSSIER     rendus seulement (rien d'installé)
    python modules/bases/outils/coffres_modeles.py --installer          écrit dans le mod : textures, modèles (fermé,
        ouvert, pièces mobiles, moitiés des coffres de 2 de large, variantes collées au mur), états, objets et
        fr/heraliz/crac/block/CoffresDonnees.java (boîtes, formes, durée et charnières de l'ouverture)

Rendus validés par le propriétaire le 27/10. Coordonnées en pixels de modèle (16 = 1 bloc), face avant au nord (z petit), comme l'armoire (tc_modele.py).
Pièces tournées : un seul axe, par pas de 22,5° au plus 45° (règle des modèles de bloc).
"""
import argparse
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import coeur_tc_rendus as cr  # noqa: E402  (matières en plus : bois, acier brut, laiton, or...)
import explosifs as ex  # noqa: E402
from explosifs import Cube, Decal, Os, bruit, col, fbm, masque_texte, melange  # noqa: E402

RACINE = Path(__file__).resolve().parents[1]
ASSETS = RACINE / "src" / "client" / "resources" / "assets" / "crac"
DEPOT = RACINE.parents[1]
POLICE = DEPOT / "launcher" / "app" / "assets" / "fonts" / "DMSans-Bold.ttf"
POLICE_MOY = DEPOT / "launcher" / "app" / "assets" / "fonts" / "DMSans-Medium.ttf"
DENSITE = 4          # points de texture par pixel de modèle (64 par bloc, comme l'armoire)

RARETES = {           # couleurs du serveur (heraliz_loot Rarete)
    "commune": ("Commune", (0x9A, 0x9A, 0x9A)),
    "peu_commune": ("Peu commune", (0x5C, 0xBF, 0x4A)),
    "rare": ("Rare", (0x3F, 0x8C, 0xFF)),
    "epique": ("Épique", (0xA6, 0x4D, 0xFF)),
    "legendaire": ("Légendaire", (0xF2, 0xB8, 0x3A)),
}


# =============================================================================================== matières
_peindre_avant = ex.peindre


def _interieur(p, n, prm):
    """Face tournée vers le dedans du coffre (prm « centre ») : vue seulement coffre ouvert."""
    c = prm.get("centre")
    if c is None:
        return False
    m = p.reshape(-1, 3).mean(0)
    return float(np.dot(n, np.array(c, float) - m)) > 0.05


def _rivets(fu, fv, w, h, pas=2.6, ray=0.30, marge=0.75):
    """Rivets alignés sur la longueur de la face, au milieu de sa largeur (0..1 : dôme)."""
    u, v = fu * w, fv * h
    if w < h:
        u, v, w, h = v, u, h, w
    k = max(1, int((w - 2 * marge) / pas) + 1)
    xs = np.linspace(marge, w - marge, k) if k > 1 else np.array([w / 2])
    d = np.min(np.abs(u[..., None] - xs), -1)
    return np.exp(-(np.hypot(d, v - h / 2) / ray) ** 2)


def _planches(p, n, haut, graine):
    """Planches : (fibres 0..1, joint 0..1, teinte de chaque planche 0..1)."""
    if abs(n[0]) > 0.5:
        long_, travers = p[..., 2], p[..., 1]
    elif abs(n[1]) > 0.5:
        long_, travers = p[..., 0], p[..., 2]
    else:
        long_, travers = p[..., 0], p[..., 1]
    k = np.floor(travers / haut)
    fr = travers / haut - k
    joint = np.exp(-np.minimum(fr, 1 - fr) * haut / 0.11)
    ki = k.astype(np.int64)
    z = np.zeros_like(ki)
    teinte = ex._hash(ki, z, z, graine)
    noeud = fbm(np.stack([long_ * 0.25, travers, z], -1), 1.0, 3, graine + 3)
    fibres = np.sin(long_ * 1.15 + ki * 7.3 + noeud * 7.0) * 0.5 + 0.5
    return fibres, joint, teinte


def peindre(mat, p, n, fu, fv, w, h, prm):
    sh = p.shape[:2]
    a = np.ones(sh)
    g = np.zeros(sh + (3,))
    e = ex.bords(fu, fv, w, h)
    usure = np.exp(-e / 0.16)
    gr = prm.get("graine", 0)
    dedans = _interieur(p, n, prm)
    if mat == "bois_vert":
        # planches peintes en vert (rareté Peu commune), peinture écaillée sur les arêtes, bois brut dedans
        fibres, joint, teinte = _planches(p, n, 3.0, 70 + gr)
        bois = melange(col(104, 78, 52), col(150, 118, 80), fibres * 0.7 + teinte * 0.3)
        if dedans:
            r = bois * 0.62
        else:
            peinture = melange(col(60, 104, 52), col(74, 122, 62), fbm(p, 0.45, 3, 71 + gr) * 0.6 + teinte * 0.4)
            peinture = peinture * (0.88 + 0.12 * fibres)[..., None]             # le grain se voit sous la peinture
            ecaille = np.clip((fbm(p, 1.3, 4, 72 + gr) - 0.66) * 7, 0, 1)        # petits éclats, surtout aux arêtes
            ecaille = np.maximum(ecaille, np.clip(usure * 1.3 - fbm(p, 2.0, 2, 69 + gr) * 0.6, 0, 1))
            r = melange(peinture, bois * 0.85, ecaille)
            coulure = np.clip(bruit(p * np.array([4.0, 0.25, 4.0]), 1.0, 68 + gr) - 0.55, 0, 1) * 1.6
            r = r * (1 - 0.22 * coulure)[..., None]
            bas = np.clip(1 - (p[..., 1] - prm.get("sol", 0)) / 4.0, 0, 1)        # terre et crasse en bas
            r = melange(r, col(58, 48, 36), bas * 0.35 * fbm(p, 0.9, 3, 73 + gr))
        r = r * (1 - 0.55 * joint)[..., None]
    elif mat == "bois_brut":
        fibres, joint, teinte = _planches(p, n, 2.6, 74 + gr)
        r = melange(col(96, 72, 48), col(140, 110, 76), fibres * 0.7 + teinte * 0.3)
        r = r * (1 - 0.5 * joint)[..., None]
        r = melange(r, col(44, 34, 26), usure * 0.45)
    elif mat == "acier_coin":
        r = melange(col(50, 52, 54), col(78, 80, 82), bruit(p * np.array([1, 5, 1.0]), 2.5, 75 + gr))
        r = melange(r, col(104, 58, 30), np.clip((fbm(p, 1.2, 3, 76 + gr) - 0.55) * 3, 0, 1) * 0.8)
        r = melange(r, col(150, 150, 146), usure * 0.55)
        if min(w, h) >= 0.9 and prm.get("rivets", True):
            d = _rivets(fu, fv, w, h)
            r = melange(r, col(176, 172, 160), d * 0.8)
            r = r * (1 - 0.35 * np.clip(_rivets(fu, fv, w, h, ray=0.45) - d, 0, 1))[..., None]
    elif mat == "toile":
        trame = (np.sin(p[..., 0] * 30 + p[..., 2] * 30) * np.sin(p[..., 1] * 30)) * 0.5 + 0.5
        r = melange(col(92, 86, 58), col(128, 120, 84), trame * 0.5 + fbm(p, 1.0, 2, 77) * 0.4)
        r = melange(r, col(60, 56, 40), usure * 0.5)
    elif mat in ("peinture_bleue", "porte_casier"):
        # tôle d'acier peinte en bleu (rareté Rare), rayures, rouille dans les coins et en bas
        tole = melange(col(118, 122, 126), col(160, 164, 168), bruit(p * np.array([1, 6, 1.0]), 3.0, 78 + gr))
        if dedans:
            r = melange(col(58, 74, 96), col(70, 88, 112), fbm(p, 0.6, 3, 79 + gr))
            r = melange(r, col(96, 60, 36), np.clip((fbm(p, 0.9, 4, 77 + gr) - 0.66) * 4, 0, 1) * 0.6)
        else:
            peinture = melange(col(40, 74, 128), col(56, 96, 156), fbm(p, 0.5, 3, 80 + gr))
            rayures = np.clip(1 - np.abs(bruit(p * np.array([0.6, 9.0, 0.6]), 1.1, 81 + gr) - 0.5) * 26, 0, 1)
            rayures = rayures * (fbm(p, 0.4, 2, 82 + gr) > 0.62)
            r = melange(peinture, tole * 0.8, np.maximum(usure * 0.55, rayures * 0.45))
            bas = np.clip(1 - (p[..., 1] - 1.5) / 6.0, 0, 1)
            rouille = np.clip((fbm(p, 0.8, 4, 83 + gr) - 0.62 + bas * 0.25 + usure * 0.2) * 4, 0, 1)
            r = melange(r, melange(col(112, 58, 28), col(150, 86, 40), fbm(p, 2, 2, 84)), rouille * 0.85)
            bosse = fbm(p, 0.25, 2, 85 + gr)
            r = r * (0.92 + 0.12 * bosse)[..., None]                                   # tôle cabossée
        if mat == "porte_casier" and not dedans:
            x0, x1 = prm["fentes_x"]
            for y0, y1 in prm["fentes"]:
                dans = (p[..., 0] > x0) & (p[..., 0] < x1) & (p[..., 1] > y0) & (p[..., 1] < y1)
                k = (p[..., 1] - y0) % 0.85
                fente = dans & (k < 0.36)
                levre = dans & (k >= 0.36) & (k < 0.52)
                r = np.where(fente[..., None], col(10, 12, 16), r)
                r = np.where(levre[..., None], r * 1.35, r)
            # bord embouti de la porte
            cadre = np.exp(-np.abs(e - 0.9) / 0.12)
            r = r * (1 + 0.25 * cadre)[..., None]
    elif mat == "acier_arme":
        # acier peint gris canon, peinture martelée, bande violette (rareté Épique), mousse noire dedans
        if dedans:
            pores = bruit(p, 7.0, 86 + gr)
            r = melange(col(26, 24, 30), col(40, 36, 48), pores * 0.6 + fbm(p, 1.2, 2, 87) * 0.4)
        else:
            martele = bruit(p, 5.5, 88 + gr) * 0.6 + bruit(p, 11.0, 89 + gr) * 0.4
            r = melange(col(40, 43, 46), col(58, 61, 66), martele)
            r = melange(r, col(124, 128, 132), usure * 0.5)
            b = prm.get("bande")
            if b is not None and abs(n[1]) < 0.5:
                dans = (p[..., 1] > b[0]) & (p[..., 1] < b[1])
                violet = melange(col(112, 52, 176), col(140, 76, 206), fbm(p, 0.6, 3, 90))
                eclat = np.clip((fbm(p, 0.9, 4, 91 + gr) - 0.66) * 6, 0, 1)
                violet = melange(violet, r, eclat)
                r = np.where(dans[..., None], violet, r)
            r = melange(r, col(30, 26, 22), np.clip((fbm(p, 0.7, 3, 92 + gr) - 0.6) * 3, 0, 1) * 0.5)
    elif mat == "gomme":
        r = melange(col(22, 22, 24), col(34, 34, 37), bruit(p, 9.0, 93 + gr) * 0.6)
        r = melange(r, col(64, 64, 66), usure * 0.45)
    elif mat == "violet_anodise":
        brosse = bruit(p * np.array([1, 1, 12.0]), 4.0, 94 + gr)
        r = melange(col(98, 40, 164), col(150, 92, 224), brosse * 0.6 + 0.25 * (n[1] > 0.5))
        r = melange(r, col(214, 186, 246), usure * 0.7)
    elif mat == "mousse":
        pores = bruit(p, 7.0, 95 + gr)
        r = melange(col(38, 36, 44), col(56, 52, 64), pores * 0.5 + fbm(p, 1.2, 2, 96) * 0.3)
        if prm.get("alveoles"):
            u, v = (p[..., 0], p[..., 2]) if abs(n[1]) > 0.5 else (p[..., 0], p[..., 1])
            bosse = (np.sin(u * 2.4) * np.sin(v * 2.4)) * 0.5 + 0.5
            r = r * (0.62 + 0.55 * bosse)[..., None]
    elif mat == "led_violette":
        c = np.exp(-((fu - 0.5) ** 2 + (fv - 0.5) ** 2) / 0.18)
        r = melange(col(150, 70, 230), col(236, 200, 255), c)
        g = r
    elif mat == "clavier":
        u, v = fu * 3, fv * 4
        touche = (np.abs((u % 1) - 0.5) < 0.34) & (np.abs((v % 1) - 0.5) < 0.30)
        r = np.where(touche[..., None], col(70, 72, 78), col(20, 20, 22))
        r = r * (1 - 0.3 * ((v % 1) > 0.62) * touche)[..., None]
    elif mat == "acier_coffre":
        # acier graphite satiné, filet doré à 1 px du bord des grandes faces, velours rouge dedans
        if dedans and prm.get("mecanisme"):
            # dos de la porte : tôle d'acier, plaque du mécanisme et ses trois tringles de pênes
            r = melange(col(70, 72, 78), col(92, 94, 100), bruit(p * np.array([1, 8, 1.0]), 3.0, 96 + gr))
            cadre = (e > 1.4) & (e < 1.6)
            r = np.where(cadre[..., None], r * 0.6, r)
            for yt in (7.65, 16.15, 24.65):
                tringle = np.abs(p[..., 1] - yt) < 0.32
                r = np.where(tringle[..., None], melange(col(176, 180, 186), col(220, 224, 230),
                                                         bruit(p * np.array([6, 1, 1.0]), 3, 95)), r)
        elif dedans:
            r = melange(col(66, 12, 18), col(96, 22, 30), fbm(p, 0.9, 3, 97 + gr) * 0.7 + bruit(p, 9, 98) * 0.3)
        else:
            brosse = bruit(p * np.array([1, 10.0, 1]), 3.0, 99 + gr)
            r = melange(col(40, 42, 47), col(56, 58, 65), brosse * 0.6 + fbm(p, 0.3, 2, 100) * 0.4)
            r = r * (0.94 + 0.10 * np.clip((p[..., 1]) / 32.0, 0, 1))[..., None]          # reflet du haut
            r = melange(r, col(110, 114, 122), np.exp(-e / 0.07) * 0.65)                  # arêtes vives
            f = prm.get("filet")
            if f is not None and min(w, h) > 2 * f + 1:
                fil = np.exp(-((e - f) / 0.09) ** 2)
                r = melange(r, col(224, 182, 88), fil * 0.95)
    elif mat == "verrou":
        r = melange(col(150, 154, 160), col(214, 218, 224), bruit(p * np.array([8, 1, 1.0]), 3.0, 101))
        r = r * (1 - 0.3 * np.exp(-e / 0.06))[..., None]
    elif mat == "cadran":
        cx, cy = prm["c"]
        ray = prm["r"]
        dx, dy = p[..., 0] - cx, p[..., 1] - cy
        rr = np.hypot(dx, dy) / ray
        ang = (np.arctan2(dy, dx) / (2 * math.pi)) % 1.0
        r = melange(col(18, 18, 20), col(30, 30, 33), bruit(p, 12, 102))
        tick = np.abs(((ang * 50) % 1.0) - 0.5) > 0.38
        majeur = np.abs(((ang * 10) % 1.0) - 0.5) > 0.47
        anneau = (rr > 0.72) & (rr < 0.95)
        r = np.where((anneau & tick)[..., None] | ((rr > 0.6) & (rr < 0.95) & majeur)[..., None],
                     col(226, 188, 96), r)
        r = melange(r, col(190, 150, 70), np.exp(-((rr - 0.97) / 0.03) ** 2))
        centre = rr < 0.32
        mol = (np.abs(((ang * 24) % 1.0) - 0.5) > 0.25) * 0.25 + 0.75
        r = np.where(centre[..., None], col(206, 168, 80) * mol[..., None], r)
    elif mat == "plaque_or":
        r = melange(col(176, 132, 44), col(232, 192, 96), bruit(p * np.array([1, 8, 1.0]), 3, 103) * 0.6 + 0.2)
        r = melange(r, col(255, 236, 170), np.exp(-e / 0.08) * 0.7)
    elif mat == "papier":
        r = melange(col(200, 190, 160), col(176, 164, 130), fbm(p, 1.5, 3, 104 + gr))
    elif mat == "sombre":
        r = melange(col(14, 14, 16), col(24, 24, 28), bruit(p, 6, 105))
    else:
        return _peindre_avant(mat, p, n, fu, fv, w, h, prm)
    return np.clip(r, 0, 1), a, np.clip(g, 0, 1)


ex.peindre = peindre


# =============================================================================================== aides de modèle
class Modele:
    """Pièces d'un coffre : os « corps » (fixe) et os qui bougent (couvercle, porte, volant), avec leur charnière."""

    def __init__(self, nom, rarete, blocs, forme, duree):
        """forme : « large » (2 blocs côte à côte, la moitié principale à x 0..16) ou « haut » (2 blocs l'un sur
        l'autre) ; duree : ouverture en ticks de jeu (la fermeture prend le même temps)."""
        self.nom, self.rarete, self.blocs, self.forme, self.duree = nom, rarete, blocs, forme, duree
        self.cubes = []
        self.os = {"corps": Os("corps")}
        self.mouvements = {}          # os -> fonction t (0..1) -> (pivot, axe, angle en degrés)
        self.cles = {}                # os -> (pivot, axe, [(t, angle)...])

    def os_mobile(self, nom, pivot, axe, cles, parent=None):
        """Pièce qui tourne autour de « pivot » ; cles : images clés (t de 0 à 1, angle en degrés), lissées entre deux
        (même calcul que client/render/CoffreRendu.java)."""
        self.os[nom] = Os(nom, parent, pivot)
        self.cles[nom] = (pivot, axe, cles)
        self.mouvements[nom] = lambda t: (pivot, axe, angle_cles(cles, t))

    def B(self, os_, a, b, mat, **k):
        self.cubes.append(Cube(os_, a, b, mat, **k))


def octogone_z(m, os_, cx, cy, z0, z1, ray, mat, prm=None):
    """Prisme à 8 pans d'axe z (cadran, moyeu) : 4 pavés, rotations permises par les modèles de bloc (0, ±45)."""
    d = ray * math.tan(math.radians(22.5))
    m.B(os_, (cx - ray, cy - d, z0), (cx + ray, cy + d, z1), mat, prm=prm)
    m.B(os_, (cx - d, cy - ray, z0), (cx + d, cy + ray, z1), mat, prm=prm)
    for ang in (45, -45):
        m.B(os_, (cx - ray, cy - d, z0), (cx + ray, cy + d, z1), mat, rot=(0, 0, ang), pivot=(cx, cy, z0), prm=prm)


def octogone_y(m, os_, cx, cz, y0, y1, ray, mat, prm=None):
    """Prisme à 8 pans d'axe y (charnières)."""
    d = ray * math.tan(math.radians(22.5))
    m.B(os_, (cx - ray, y0, cz - d), (cx + ray, y1, cz + d), mat, prm=prm)
    m.B(os_, (cx - d, y0, cz - ray), (cx + d, y1, cz + ray), mat, prm=prm)
    for ang in (45, -45):
        m.B(os_, (cx - ray, y0, cz - d), (cx + ray, y1, cz + d), mat, rot=(0, ang, 0), pivot=(cx, y0, cz), prm=prm)


def pochoir(lignes, haut=90, flou=2.2, ratio=None):
    """Masque d'un texte ; ratio (largeur / hauteur de la surface) : bandes vides ajoutées pour ne pas l'écraser."""
    m = masque_texte(lignes, haut)
    if ratio:
        h, w = m.shape
        if w / h < ratio:
            pad = int((h * ratio - w) / 2)
            m = np.pad(m, ((0, 0), (pad, pad)))
        else:
            pad = int((w / ratio - h) / 2)
            m = np.pad(m, ((pad, pad), (0, 0)))
    im = Image.fromarray((m * 255).astype(np.uint8)).filter(ImageFilter.GaussianBlur(flou))
    return np.asarray(im, float) / 255


def decal_nord(masque, x0, x1, y_haut, y_bas, z, couleur, opacite=0.85):
    """Image posée sur une face nord (x0 < x1), lue de face : l'est (x grand) est à gauche du joueur."""
    return Decal(masque, (x1, y_haut, z), (x0 - x1, 0, 0), (0, y_bas - y_haut, 0), couleur, opacite,
                 normale=(0, 0, -1))


def lisse(t):
    return t * t * (3 - 2 * t)


def angle_cles(cles, t):
    t = min(1.0, max(0.0, float(t)))
    for (t0, a0), (t1, a1) in zip(cles, cles[1:]):
        if t <= t1:
            u = 0.0 if t1 <= t0 else (t - t0) / (t1 - t0)
            return a0 + (a1 - a0) * lisse(u)
    return cles[-1][1]


# =============================================================================================== les coffres
def malle_chantier():
    """Peu commune : malle de chantier en planches peintes en vert, coins et sangles d'acier, 2 blocs de large."""
    m = Modele("malle_chantier", "peu_commune", "2 de large × 1 de haut", "large", 8)
    C = {"centre": (16, 6, 9), "sol": 1.0}
    B = m.B
    # patins
    for x0 in (2.0, 15.0, 28.0):
        B("corps", (x0, 0, 3.0), (x0 + 2.0, 1.0, 15.0), "bois_brut", prm={"graine": int(x0)})
    # caisse creuse : faces, dos, côtés, fond
    B("corps", (1.0, 1.0, 2.5), (31.0, 10.0, 3.7), "bois_vert", prm=C)
    B("corps", (1.0, 1.0, 14.3), (31.0, 10.0, 15.5), "bois_vert", prm=dict(C, graine=1))
    B("corps", (1.0, 1.0, 3.7), (2.2, 10.0, 14.3), "bois_vert", prm=dict(C, graine=2))
    B("corps", (29.8, 1.0, 3.7), (31.0, 10.0, 14.3), "bois_vert", prm=dict(C, graine=3))
    B("corps", (2.2, 1.0, 3.7), (29.8, 1.9, 14.3), "bois_brut", prm={"graine": 9})
    # coins d'acier (cornières verticales)
    for x0, z0 in ((0.7, 2.2), (29.3, 2.2), (0.7, 13.8), (29.3, 13.8)):
        B("corps", (x0, 1.0, z0), (x0 + 2.0, 10.0, z0 + 2.0), "acier_coin", faces=("north", "south", "east", "west"))
    # sangles d'acier sur la face avant et les côtés
    for x0 in (7.4, 23.0):
        B("corps", (x0, 1.0, 2.2), (x0 + 1.6, 10.0, 2.5), "acier_coin", faces=("north", "east", "west"))
    # poignées de toile sur les côtés (avec leurs pattes)
    for x0, x1 in ((0.15, 1.0), (31.0, 31.85)):
        B("corps", (x0, 6.2, 6.0), (x1, 6.9, 12.0), "toile")
        for z0 in (5.6, 11.6):
            B("corps", (x0 - 0.05 if x0 < 1 else x0, 5.6, z0), (x1 + 0.05 if x1 > 31 else x1, 7.5, z0 + 0.8),
              "acier_coin", prm={"rivets": False})
    # gâches des fermoirs
    for x0 in (9.6, 20.2):
        B("corps", (x0, 7.6, 2.15), (x0 + 2.2, 9.4, 2.5), "acier_coin", prm={"rivets": False})
        B("corps", (x0 + 0.7, 8.1, 1.9), (x0 + 1.5, 8.9, 2.15), "laiton")
    # pochoir blanc sur la face avant
    st = pochoir(["HZ · 07"], ratio=6.8 / 3.2)
    B("corps", (12.6, 3.4, 2.4), (19.4, 6.6, 2.5), "bois_vert", faces=("north",), prm=dict(C, graine=4),
      decals=(decal_nord(st, 12.6, 19.4, 6.6, 3.4, 2.4, col(226, 222, 206)),))

    # couvercle : bascule vers l'arrière autour de la charnière (dos, en haut)
    m.os_mobile("couvercle", (16.0, 10.0, 15.6), "x", [(0, 0), (1, 82)])
    L = {"centre": (16, 6, 9)}
    B("couvercle", (0.6, 10.0, 2.2), (31.4, 11.8, 15.7), "bois_vert", prm=dict(L, graine=5))
    B("couvercle", (0.6, 9.2, 2.0), (31.4, 10.0, 2.5), "bois_vert", prm=dict(L, graine=6),
      faces=("north", "east", "west", "down"))
    for x0, z0 in ((0.4, 1.8), (29.6, 1.8), (0.4, 13.9), (29.6, 13.9)):
        B("couvercle", (x0, 9.6, z0), (x0 + 2.0, 12.0, z0 + 2.0), "acier_coin", prm={"rivets": False})
    for x0 in (7.4, 23.0):
        B("couvercle", (x0, 11.8, 2.0), (x0 + 1.6, 12.05, 15.7), "acier_coin", faces=("up", "east", "west"))
        B("couvercle", (x0, 9.2, 1.75), (x0 + 1.6, 12.05, 2.0), "acier_coin", faces=("north", "east", "west", "up"))
    for x0 in (9.6, 20.2):
        B("couvercle", (x0 + 0.3, 8.4, 1.75), (x0 + 1.9, 10.6, 2.0), "acier_coin", prm={"rivets": False},
          faces=("north", "east", "west", "down"))
    for x0 in (5.0, 25.0):
        B("couvercle", (x0, 9.4, 15.5), (x0 + 2.4, 10.8, 15.75), "acier_coin", prm={"rivets": False})
    return m


def casier_metallique():
    """Rare : casier de vestiaire en tôle peinte en bleu, porte à aérations, cadenas, 2 blocs de haut."""
    m = Modele("casier_metallique", "rare", "1 de large × 2 de haut", "haut", 8)
    C = {"centre": (8, 16, 9)}
    B = m.B
    B("corps", (1.0, 0, 3.4), (15.0, 1.5, 15.5), "acier_noir")                       # socle
    B("corps", (0.75, 1.5, 14.75), (15.25, 30.5, 15.75), "peinture_bleue", prm=C)     # dos
    B("corps", (0.75, 1.5, 3.0), (1.75, 30.5, 14.75), "peinture_bleue", prm=dict(C, graine=1))
    B("corps", (14.25, 1.5, 3.0), (15.25, 30.5, 14.75), "peinture_bleue", prm=dict(C, graine=2))
    B("corps", (1.75, 1.5, 3.0), (14.25, 2.3, 14.75), "peinture_bleue", prm=dict(C, graine=3))   # fond
    B("corps", (0.5, 30.5, 2.75), (15.5, 31.4, 15.75), "peinture_bleue", prm=dict(C, graine=4))  # chapeau
    # étagère du haut et tringle
    B("corps", (1.75, 23.4, 3.6), (14.25, 24.0, 14.75), "peinture_bleue", prm=dict(C, graine=5))
    B("corps", (1.75, 20.6, 12.4), (14.25, 21.0, 12.8), "verrou")
    # crochet au dos
    B("corps", (7.6, 15.0, 13.9), (8.4, 15.6, 14.75), "verrou")
    B("corps", (7.6, 15.6, 13.9), (8.4, 16.8, 14.3), "verrou")
    # porte : pivote autour de la charnière, à l'est (x grand), devant
    m.os_mobile("porte", (14.95, 16.0, 3.0), "y", [(0, 0), (1, -108)])
    P = {"fentes": [(26.0, 28.6), (4.0, 6.6)], "fentes_x": (4.5, 11.5), "centre": (8, 16, 9)}
    B("porte", (0.95, 2.3, 2.45), (14.95, 30.4, 3.0), "porte_casier", prm=P)
    B("porte", (2.3, 15.0, 1.95), (3.1, 19.4, 2.45), "verrou")                        # poignée
    B("porte", (2.1, 13.4, 2.15), (3.9, 14.6, 2.45), "acier_brut")                    # moraillon
    B("porte", (2.2, 11.2, 1.75), (3.8, 13.0, 2.3), "laiton")                         # cadenas
    B("porte", (2.55, 13.0, 1.9), (3.45, 13.8, 2.15), "verrou")
    carte = pochoir(["07"], 120, 0.6, ratio=5.6 / 2.4)
    B("porte", (4.8, 8.6, 2.25), (11.2, 11.4, 2.45), "laiton")                        # porte-étiquette
    B("porte", (5.2, 8.8, 2.2), (10.8, 11.2, 2.25), "papier", faces=("north",),
      decals=(decal_nord(carte, 5.2, 10.8, 11.2, 8.8, 2.2, col(30, 30, 34), 0.92),))
    for y0 in (5.0, 15.0, 25.0):                                                      # charnières (nœuds)
        B("porte", (14.6, y0, 2.3), (15.2, y0 + 2.2, 3.0), "acier_brut")
    return m


def caisse_armes():
    """Épique : caisse d'armes renforcée, acier gris canon, bande et fermoirs violets, calage en mousse dedans."""
    m = Modele("caisse_armes", "epique", "2 de large × 1 de haut", "large", 10)
    C = {"centre": (16, 6, 9), "bande": (3.0, 4.4)}
    B = m.B
    for z0 in (3.2, 13.3):                                                            # rails
        B("corps", (1.0, 0, z0), (31.0, 0.9, z0 + 1.6), "gomme")
    B("corps", (1.0, 0.9, 2.5), (31.0, 10.4, 3.5), "acier_arme", prm=C)
    B("corps", (1.0, 0.9, 14.5), (31.0, 10.4, 15.5), "acier_arme", prm=dict(C, graine=1))
    B("corps", (1.0, 0.9, 3.5), (2.0, 10.4, 14.5), "acier_arme", prm=dict(C, graine=2))
    B("corps", (30.0, 0.9, 3.5), (31.0, 10.4, 14.5), "acier_arme", prm=dict(C, graine=3))
    # fond en mousse avec l'empreinte d'un fusil
    fusil = Image.new("L", (600, 160), 0)
    d = ImageDraw.Draw(fusil)
    d.polygon([(20, 70), (170, 58), (210, 40), (330, 40), (340, 58), (560, 58), (580, 70), (580, 84), (340, 88),
               (300, 120), (262, 120), (270, 92), (200, 96), (150, 126), (40, 120), (20, 96)], fill=255)
    fusil = np.asarray(fusil.filter(ImageFilter.GaussianBlur(3)), float) / 255
    B("corps", (2.0, 0.9, 3.5), (30.0, 2.6, 14.5), "mousse",
      decals=(Decal(fusil, (28.5, 2.6, 13.0), (-25.0, 0, 0), (0, 0, -8.0), col(10, 9, 12), 0.97,
                    normale=(0, 1, 0)),))
    # coins en caoutchouc
    for x0, z0 in ((0.6, 2.1), (28.6, 2.1), (0.6, 13.1), (28.6, 13.1)):
        B("corps", (x0, 0.4, z0), (x0 + 2.8, 4.2, z0 + 2.8), "gomme")
    # nervures de renfort sur la face avant
    for x0 in (5.6, 14.0, 17.6, 26.2):
        B("corps", (x0, 0.9, 2.1), (x0 + 0.4, 4.6, 2.5), "acier_noir")
    # fermoirs violets (partie basse)
    for x0 in (3.4, 26.8):
        B("corps", (x0, 7.0, 2.2), (x0 + 1.8, 9.9, 2.5), "acier_noir")
        B("corps", (x0 + 0.25, 7.3, 1.85), (x0 + 1.55, 9.7, 2.2), "violet_anodise")
    # poignées repliables sur les côtés
    for x0, x1 in ((0.25, 1.0), (31.0, 31.75)):
        B("corps", (x0, 7.0, 6.2), (x1, 7.7, 11.8), "acier_noir")
        for z0 in (6.2, 11.1):
            B("corps", (x0, 6.2, z0), (x1, 8.2, z0 + 0.7), "acier_noir")
    for txt, x0, x1 in ((["ARMES"], 18.6, 26.0), (["HZ · 07"], 6.0, 13.4)):
        st = pochoir(txt, 100, 1.2, ratio=(x1 - x0) / 2.6)
        B("corps", (x0, 4.9, 2.4), (x1, 7.5, 2.5), "acier_arme", faces=("north",), prm=dict(C, bande=None, graine=4),
          decals=(decal_nord(st, x0, x1, 7.5, 4.9, 2.4, col(222, 220, 214), 0.85),))
    # serrure à code (comme les boîtes de Rust) : boîtier, clavier, voyant violet
    B("corps", (14.6, 4.6, 1.7), (17.4, 8.6, 2.5), "acier_noir")
    B("corps", (14.9, 5.0, 1.65), (17.1, 7.4, 1.7), "clavier", faces=("north",))
    B("corps", (15.6, 7.7, 1.55), (16.4, 8.3, 1.7), "led_violette")

    # couvercle : bascule vers l'arrière, avec un petit à-coup (fermoirs qui sautent) puis l'ouverture
    m.os_mobile("couvercle", (16.0, 10.4, 15.6), "x", [(0, 0), (0.15, 4), (1, 78)])
    L = {"centre": (16, 6, 9), "bande": (11.0, 12.0)}
    B("couvercle", (0.8, 10.4, 2.3), (31.2, 13.0, 15.7), "acier_arme", prm=L)
    for z0 in (5.6, 11.4):
        B("couvercle", (1.0, 13.0, z0), (31.0, 13.45, z0 + 1.2), "acier_noir", faces=("up", "north", "south", "east",
                                                                                     "west"))
    for x0, z0 in ((0.6, 2.1), (28.6, 2.1), (0.6, 13.1), (28.6, 13.1)):
        B("couvercle", (x0, 10.6, z0), (x0 + 2.8, 13.4, z0 + 2.8), "gomme")
    for x0 in (3.4, 26.8):
        B("couvercle", (x0 + 0.35, 9.9, 2.0), (x0 + 1.45, 11.2, 2.3), "acier_noir")
    # dessous du couvercle : mousse
    B("couvercle", (2.0, 10.0, 3.5), (30.0, 10.4, 14.5), "mousse", faces=("down",), prm={"alveoles": True})
    for x0 in (6.0, 23.6):
        B("couvercle", (x0, 10.0, 15.5), (x0 + 2.4, 11.4, 15.75), "acier_noir")
    return m


def coffre_fort():
    """Légendaire : coffre-fort en acier graphite, filets dorés, cadran, volant à 4 branches, porte blindée."""
    m = Modele("coffre_fort", "legendaire", "1 de large × 2 de haut", "haut", 18)
    C = {"centre": (8, 16, 9), "filet": 0.8}
    B = m.B
    for x0, z0 in ((1.0, 2.6), (13.0, 2.6), (1.0, 13.2), (13.0, 13.2)):               # pieds
        B("corps", (x0, 0, z0), (x0 + 2.0, 1.0, z0 + 2.0), "acier_noir")
    B("corps", (0.5, 1.0, 2.0), (15.5, 2.6, 15.75), "acier_coffre", prm=dict(C, filet=None))       # socle
    B("corps", (0.75, 2.6, 13.6), (15.25, 29.8, 15.75), "acier_coffre", prm=dict(C, graine=1))     # dos
    B("corps", (0.75, 2.6, 4.0), (2.4, 29.8, 13.6), "acier_coffre", prm=dict(C, graine=2))         # côtés
    B("corps", (13.6, 2.6, 4.0), (15.25, 29.8, 13.6), "acier_coffre", prm=dict(C, graine=3))
    B("corps", (2.4, 2.6, 4.0), (13.6, 3.6, 13.6), "acier_coffre", prm=dict(C, graine=4))          # fond
    B("corps", (0.5, 29.8, 2.0), (15.5, 31.5, 15.75), "acier_coffre", prm=dict(C, filet=None, graine=5))
    # cadre avant (le chambranle où la porte se ferme)
    B("corps", (0.75, 2.6, 2.0), (1.6, 29.8, 4.0), "acier_coffre", prm=dict(C, filet=None, graine=6))
    B("corps", (14.4, 2.6, 2.0), (15.25, 29.8, 4.0), "acier_coffre", prm=dict(C, filet=None, graine=7))
    # étagères en velours et lingots
    for y in (11.4, 20.6):
        B("corps", (2.4, y, 4.2), (13.6, y + 0.7, 13.6), "acier_coffre", prm=dict(C, filet=None, graine=8))
    for k, (x0, y0) in enumerate(((3.4, 12.1), (6.5, 12.1), (4.95, 13.35))):
        B("corps", (x0, y0, 7.0), (x0 + 2.8, y0 + 1.25, 11.0), "or", prm={"graine": k})
    # charnières (fûts), côté est
    for y0 in (6.5, 22.5):
        octogone_y(m, "corps", 15.15, 3.0, y0, y0 + 4.0, 0.85, "acier_noir")

    # porte blindée : le volant tourne d'abord (débloque), puis la porte s'ouvre, lourde
    m.os_mobile("porte", (15.15, 16.0, 3.0), "y", [(0, 0), (0.3, 0), (1, -100)])
    m.os_mobile("volant", (8.0, 12.6, 1.2), "z", [(0, 0), (0.3, -180), (1, -180)], parent="porte")
    D = {"centre": (8, 16, 9), "filet": 1.0, "mecanisme": True}
    B("porte", (1.6, 2.7, 2.0), (14.4, 29.7, 4.0), "acier_coffre", prm=dict(D, graine=10))
    for y in (7.0, 15.5, 24.0):                                                        # pênes du côté ouverture
        B("porte", (0.9, y, 2.6), (1.6, y + 1.3, 3.4), "verrou")
    # plaque HERALIZ
    plaque = pochoir(["HERALIZ"], 110, 0.4, ratio=8.8 / 2.4)
    B("porte", (3.4, 24.5, 1.85), (12.6, 27.3, 2.0), "plaque_or",
      decals=(decal_nord(plaque, 3.6, 12.4, 27.15, 24.65, 1.85, col(46, 34, 14), 0.95),))
    # cadran (anneau doré, face graduée)
    octogone_z(m, "porte", 8.0, 19.6, 1.55, 2.0, 2.45, "plaque_or")
    octogone_z(m, "porte", 8.0, 19.6, 1.35, 1.55, 2.05, "cadran", prm={"c": (8.0, 19.6), "r": 2.05})
    # volant : moyeu, 4 branches et couronne à 8 pans
    cx, cy, R_ = 8.0, 12.6, 3.0
    octogone_z(m, "volant", cx, cy, 1.0, 2.0, 0.9, "plaque_or")
    for ang in (45, -45):
        m.B("volant", (cx - R_, cy - 0.28, 1.3), (cx + R_, cy + 0.28, 1.75), "plaque_or", rot=(0, 0, ang),
            pivot=(cx, cy, 1.3))
    cote = 2 * R_ * math.tan(math.radians(22.5)) + 0.2
    for k in range(8):
        a = math.radians(k * 45)
        px, py = cx + R_ * math.cos(a), cy + R_ * math.sin(a)
        tang = (k * 45 + 90) % 180
        tang = tang - 180 if tang > 90 else tang                     # 0, 45, 90 ou -45
        if tang == 90:
            m.B("volant", (px - 0.3, py - cote / 2, 1.05), (px + 0.3, py + cote / 2, 1.85), "plaque_or")
        else:
            m.B("volant", (px - cote / 2, py - 0.3, 1.05), (px + cote / 2, py + 0.3, 1.85), "plaque_or",
                rot=(0, 0, tang) if tang else None, pivot=(px, py, 1.05) if tang else None)
    return m


COFFRES = [malle_chantier, casier_metallique, caisse_armes, coffre_fort]
LUMINEUX = {"led_violette"}


# =============================================================================================== modèle de bloc
def faces_cachees(cubes):
    """Faces couvertes par une pièce du même os (collées contre elle ou dedans) : jamais vues, pas exportées."""
    cachees = set()
    for i, c in enumerate(cubes):
        if c.rot is not None:
            continue
        for f in c.faces:
            axe = {"north": 2, "south": 2, "west": 0, "east": 0, "down": 1, "up": 1}[f]
            signe = 1 if f in ("south", "east", "up") else -1
            plan = (c.b if signe > 0 else c.a)[axe] + signe * 0.01
            autres = [k for k in range(3) if k != axe]
            for j, d in enumerate(cubes):
                if j == i or d.rot is not None or d.os != c.os:
                    continue
                if not (d.a[axe] < plan < d.b[axe]):
                    continue
                if all(d.a[k] <= c.a[k] + 1e-6 and d.b[k] >= c.b[k] - 1e-6 for k in autres):
                    cachees.add((i, f))
                    break
    return cachees


def modele_bloc(m, largeur=512):
    """Éléments de modèle de bloc par os, et la texture. UV comme tc_modele.modele_minecraft."""
    ex.D, ex.LARGEUR, ex.MARGE = DENSITE, largeur, 1
    tex, _, tw, th = ex.construire(list(m.os.values()), m.cubes)
    if tex.shape[0] < tex.shape[1]:
        tex = np.concatenate([tex, np.zeros((tex.shape[1] - tex.shape[0], tex.shape[1], 4))])
        th = tw
    cachees = faces_cachees(m.cubes)
    groupes = {o: [] for o in m.os}
    for i, c in enumerate(m.cubes):
        faces = {}
        for f in c.faces:
            if (i, f) in cachees:
                continue
            u0, v0, w, h = c.uv[f]
            U0, V0, U1, V1 = u0 * 16 / tw, v0 * 16 / th, (u0 + w) * 16 / tw, (v0 + h) * 16 / th
            uv = [U0, V1, U1, V0] if f in ("up", "down") else [U1, V0, U0, V1]
            faces[f] = {"uv": [round(x, 4) for x in uv], "texture": "#0"}
        if not faces:
            continue
        el = {"from": [round(x, 4) for x in c.a], "to": [round(x, 4) for x in c.b], "faces": faces}
        if c.mat in LUMINEUX:
            el["forge_data"] = {"block_light": 15, "sky_light": 15}
            el["shade"] = False
        if c.rot:
            axes = [k for k, v in enumerate(c.rot) if v]
            if len(axes) != 1 or abs(c.rot[axes[0]]) % 22.5 or abs(c.rot[axes[0]]) > 45:
                raise SystemExit(f"rotation impossible dans un modèle de bloc : {c.rot}")
            el["rotation"] = {"origin": [round(x, 4) for x in c.pivot], "axis": "xyz"[axes[0]],
                              "angle": c.rot[axes[0]]}
        for v in list(c.a) + list(c.b):
            if v < -16 or v > 32:
                raise SystemExit(f"pièce hors des limites de Minecraft : {c.a} {c.b}")
        groupes[c.os].append(el)
    img = Image.fromarray((np.clip(tex, 0, 1) * 255).astype(np.uint8), "RGBA")
    return groupes, img


# =============================================================================================== rendu (comme le jeu)
COINS = {
    "down": [(0, 0, 1), (0, 0, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (0, 1, 1), (1, 1, 1), (1, 1, 0)],
    "north": [(1, 1, 0), (1, 0, 0), (0, 0, 0), (0, 1, 0)],
    "south": [(0, 1, 1), (0, 0, 1), (1, 0, 1), (1, 1, 1)],
    "west": [(0, 1, 0), (0, 0, 0), (0, 0, 1), (0, 1, 1)],
    "east": [(1, 1, 1), (1, 0, 1), (1, 0, 0), (1, 1, 0)],
}
AXES = {"x": np.array([1.0, 0, 0]), "y": np.array([0, 1.0, 0]), "z": np.array([0, 0, 1.0])}


def rot_axe(axe, deg):
    """Rotation à droite autour de l'axe (comme FaceBakery)."""
    k = AXES[axe]
    t = math.radians(deg)
    K = np.array([[0, -k[2], k[1]], [k[2], 0, -k[0]], [-k[1], k[0], 0]])
    return np.eye(3) + math.sin(t) * K + (1 - math.cos(t)) * K @ K


def quads_elements(elements, tex, transfos=()):
    """Faces des éléments (coins dans l'ordre de FaceInfo, UV de BlockFaceUV) ; transfos : (pivot, axe, angle) appliquées
    dans l'ordre, puis décalage éventuel (« decal », vecteur)."""
    H, W = tex.shape[:2]
    out = []
    for el in elements:
        a, b = np.array(el["from"], float), np.array(el["to"], float)
        R = np.eye(3)
        o = np.zeros(3)
        if "rotation" in el:
            R = rot_axe(el["rotation"]["axis"], el["rotation"]["angle"])
            o = np.array(el["rotation"]["origin"], float)
        for f, fd in el["faces"].items():
            u0, v0, u1, v1 = fd["uv"]
            uv = np.array([(u0, v0), (u0, v1), (u1, v1), (u1, v0)]) * np.array([W / 16, H / 16])
            c = np.array([[b[i] if s else a[i] for i, s in enumerate(sel)] for sel in COINS[f]], float)
            c = (c - o) @ R.T + o
            for t in transfos:
                if t[0] == "decal":
                    c = c + np.array(t[1], float)
                else:
                    pv, axe, ang = t
                    Rt = rot_axe(axe, ang)
                    c = (c - np.array(pv, float)) @ Rt.T + np.array(pv, float)
            out.append((c, uv, tex, el.get("shade", True)))
    return out


class Vue:
    def __init__(self, yaw, pitch, bornes, taille, marge=0.86):
        cy, sy, cp, sp = math.cos(yaw), math.sin(yaw), math.cos(pitch), math.sin(pitch)
        Rv = np.array([[cy, 0, sy], [0, 1, 0], [-sy, 0, cy]])
        Rp = np.array([[1, 0, 0], [0, cp, -sp], [0, sp, cp]])
        self.V = Rp @ Rv
        (x0, y0, z0), (x1, y1, z1) = bornes
        pts = np.array([[x, y, z] for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]) @ self.V.T
        self.cx, self.cy = (pts[:, 0].max() + pts[:, 0].min()) / 2, (pts[:, 1].max() + pts[:, 1].min()) / 2
        self.ech = taille * marge / max(np.ptp(pts[:, 0]), np.ptp(pts[:, 1]))
        self.taille = taille

    def ecran(self, p):
        q = np.asarray(p, float) @ self.V.T
        return np.stack([(q[..., 0] - self.cx) * self.ech + self.taille / 2,
                         self.taille / 2 - (q[..., 1] - self.cy) * self.ech, -q[..., 2]], -1)


L_SOLEIL = np.array([0.45, 0.8, -0.55]) / np.linalg.norm([0.45, 0.8, -0.55])


def eclairage(n, ombre):
    if not ombre:
        return 1.0
    mc = n[0] ** 2 * 0.6 + n[2] ** 2 * 0.8 + n[1] ** 2 * (1.0 if n[1] > 0 else 0.5)
    return mc * (0.8 + 0.32 * max(0.0, float(n @ L_SOLEIL)))


def fond(taille, haut=(30, 33, 37), bas=(18, 20, 23)):
    t = np.linspace(0, 1, taille)[:, None, None]
    return np.broadcast_to(np.array(haut) / 255 * (1 - t) + np.array(bas) / 255 * t, (taille, taille, 3)).copy()


def rendre(quads, vue, img=None, ombre_sol=None):
    T = vue.taille
    if img is None:
        img = fond(T)
    if ombre_sol is not None:                                   # ombre douce au sol (boîte x0, z0, x1, z1, y)
        x0, z0, x1, z1, y = ombre_sol
        pts = vue.ecran([[x0, y, z0], [x1, y, z0], [x1, y, z1], [x0, y, z1]])
        om = Image.new("L", (T, T), 0)
        ImageDraw.Draw(om).polygon([tuple(p[:2]) for p in pts], fill=150)
        om = np.asarray(om.filter(ImageFilter.GaussianBlur(T / 60)), float) / 255
        img = img * (1 - om[..., None] * 0.75)
    zb = np.full((T, T), np.inf)
    for coins, uv, tex, ombre in quads:
        H, W = tex.shape[:2]
        n = np.cross(coins[1] - coins[0], coins[3] - coins[0])
        nn = np.linalg.norm(n)
        if nn < 1e-9:
            continue
        n = n / nn
        if (vue.V @ n)[2] <= 1e-6:     # face tournée vers l'arrière : jamais dessinée (comme le jeu)
            continue
        lum = eclairage(n, ombre)
        s = vue.ecran(coins)
        for idx in ((0, 1, 2), (0, 2, 3)):
            P, Tt = s[list(idx)], uv[list(idx)]
            x0, x1 = int(max(0, np.floor(P[:, 0].min()))), int(min(T - 1, np.ceil(P[:, 0].max())))
            y0, y1 = int(max(0, np.floor(P[:, 1].min()))), int(min(T - 1, np.ceil(P[:, 1].max())))
            if x1 < x0 or y1 < y0:
                continue
            X, Y = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            (ax, ay, az), (bx, by, bz), (qx, qy, qz) = P
            den = (by - qy) * (ax - qx) + (qx - bx) * (ay - qy)
            if abs(den) < 1e-9:
                continue
            l1 = ((by - qy) * (X - qx) + (qx - bx) * (Y - qy)) / den
            l2 = ((qy - ay) * (X - qx) + (ax - qx) * (Y - qy)) / den
            l3 = 1 - l1 - l2
            dedans = (l1 >= -1e-6) & (l2 >= -1e-6) & (l3 >= -1e-6)
            if not dedans.any():
                continue
            z = l1 * az + l2 * bz + l3 * qz
            tu = np.clip(np.floor(l1 * Tt[0, 0] + l2 * Tt[1, 0] + l3 * Tt[2, 0]), 0, W - 1).astype(int)
            tv = np.clip(np.floor(l1 * Tt[0, 1] + l2 * Tt[1, 1] + l3 * Tt[2, 1]), 0, H - 1).astype(int)
            texel = tex[tv, tu]
            ok = dedans & (texel[..., 3] > 0.5) & (z < zb[y0:y1 + 1, x0:x1 + 1] - 1e-4)
            zb[y0:y1 + 1, x0:x1 + 1] = np.where(ok, z, zb[y0:y1 + 1, x0:x1 + 1])
            img[y0:y1 + 1, x0:x1 + 1] = np.where(ok[..., None], texel[..., :3] * lum, img[y0:y1 + 1, x0:x1 + 1])
    return img


def vers_image(img, taille):
    im = Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8))
    return im.resize((taille, taille), Image.LANCZOS)


# =============================================================================================== coffre prêt à rendre
class Coffre:
    def __init__(self, m):
        self.m = m
        self.groupes, self.atlas = modele_bloc(m, 1024 if "2 de large" in m.blocs else 512)
        self.tex = np.asarray(self.atlas, float) / 255

    def quads(self, t=0.0, decal=(0, 0, 0)):
        out = []
        for os_, els in self.groupes.items():
            tr = []
            # chaîne des os (le volant suit la porte)
            chaine = []
            o = self.m.os.get(os_)
            while o is not None and o.nom in self.m.mouvements:
                chaine.append(o.nom)
                o = self.m.os.get(o.parent) if o.parent else None
            for nom in chaine:                               # du plus proche au plus lointain
                tr.append(self.m.mouvements[nom](t))
            tr.append(("decal", decal))
            out += quads_elements(els, self.tex, tr)
        return out

    def nb_elements(self):
        return {o: len(e) for o, e in self.groupes.items()}

    def bornes(self):
        a = np.min([c.a for c in self.m.cubes], 0)
        b = np.max([c.b for c in self.m.cubes], 0)
        return a, b


def caisse_commune(gris=False):
    """La caisse en métal actuelle (modèle et texture du mod), ou sa variante repeinte en gris."""
    import json
    el = json.loads((ASSETS / "models/block/metal_crate_closed.json").read_text(encoding="utf-8"))["elements"]
    ouv = json.loads((ASSETS / "models/block/metal_crate_open.json").read_text(encoding="utf-8"))["elements"]
    im = Image.open(ASSETS / "textures/block/chest_one.png").convert("RGBA")
    a = np.asarray(im, float) / 255
    if gris:
        lum = a[..., :3] @ np.array([0.3, 0.55, 0.15])
        acier = np.stack([lum * 1.02, lum * 1.04, lum * 1.08], -1)
        a = np.concatenate([np.clip(acier * 1.08, 0, 1), a[..., 3:]], -1)
    return el, ouv, a


# =============================================================================================== planches
def police(t, moy=False):
    return ImageFont.truetype(str(POLICE_MOY if moy else POLICE), t)


def titre(im, texte, sous, couleur):
    d = ImageDraw.Draw(im)
    d.text((24, 16), texte, font=police(34), fill=(236, 236, 236))
    d.text((24, 58), sous, font=police(20, True), fill=couleur)


VUES = {"trois_quarts": (math.radians(180 - 32), math.radians(26)), "face": (math.radians(180), math.radians(10)),
        "dos": (math.radians(-30), math.radians(24)), "dessus": (math.radians(180 - 20), math.radians(62))}


def planche_coffre(cf, dossier, nom_fr, contenance):
    """Trois vues fermé, deux vues ouvert, et la bande d'ouverture (5 images)."""
    a, b = cf.bornes()
    bornes = (a - np.array([2, 0, 14]), b + np.array([2, 14, 2]))     # place pour le couvercle ou la porte ouverts
    S = 900
    im = Image.new("RGB", (3 * 460, 2 * 460 + 2 * 300 + 110), (20, 22, 25))
    nom_r, rgb = RARETES[cf.m.rarete]
    titre(im, nom_fr, f"{nom_r} · {cf.m.blocs} · {contenance}", rgb)
    y = 100
    for k, (vn, (yaw, pitch)) in enumerate([("fermé, trois quarts", VUES["trois_quarts"]), ("fermé, de face", VUES["face"]),
                                            ("fermé, de dos", VUES["dos"])]):
        v = Vue(yaw, pitch, bornes, S)
        sol = (a[0], a[2], b[0], b[2], 0.0)
        r = vers_image(rendre(cf.quads(0.0), v, ombre_sol=sol), 460)
        im.paste(r, (k * 460, y))
        ImageDraw.Draw(im).text((k * 460 + 14, y + 8), vn, font=police(18, True), fill=(170, 170, 170))
    y += 460
    for k, (vn, (yaw, pitch), t) in enumerate([("ouvert, trois quarts", VUES["trois_quarts"], 1.0),
                                               ("ouvert, de dessus", VUES["dessus"], 1.0),
                                               ("ouvert, de face", VUES["face"], 1.0)]):
        v = Vue(yaw, pitch, bornes, S)
        r = vers_image(rendre(cf.quads(t), v, ombre_sol=(a[0], a[2], b[0], b[2], 0.0)), 460)
        im.paste(r, (k * 460, y))
        ImageDraw.Draw(im).text((k * 460 + 14, y + 8), vn, font=police(18, True), fill=(170, 170, 170))
    y += 470
    d = ImageDraw.Draw(im)
    d.text((24, y), "Ouverture (en jeu : animation fluide, voir le .gif)", font=police(22), fill=(220, 220, 220))
    y += 36
    w = (3 * 460) // 5
    v = Vue(*VUES["trois_quarts"], bornes, 600)
    for k, t in enumerate((0.0, 0.25, 0.5, 0.75, 1.0)):
        r = vers_image(rendre(cf.quads(t), v, ombre_sol=(a[0], a[2], b[0], b[2], 0.0)), w)
        im.paste(r.crop((0, 0, w, w)), (k * w, y))
    for k, txt in enumerate(("0 %", "25 %", "50 %", "75 %", "100 %")):
        tw = d.textlength(txt, font=police(18, True))
        d.text((k * w + (w - tw) / 2, y + w + 6), txt, font=police(18, True), fill=(150, 150, 150))
    im = im.crop((0, 0, im.width, y + w + 40))
    im.save(dossier / f"{cf.m.nom}.png", optimize=True)


def gif_ouverture(cf, dossier, taille=420):
    a, b = cf.bornes()
    bornes = (a - np.array([2, 0, 14]), b + np.array([2, 14, 2]))
    v = Vue(*VUES["trois_quarts"], bornes, taille * 2)
    images = []
    pas = [i / 16 for i in range(17)]
    seq = [0.0] * 6 + pas + [1.0] * 10 + pas[::-1] + [0.0] * 4
    cache = {}
    for t in seq:
        if t not in cache:
            cache[t] = vers_image(rendre(cf.quads(t), v, ombre_sol=(a[0], a[2], b[0], b[2], 0.0)), taille)
        images.append(cache[t])
    images[0].save(dossier / f"{cf.m.nom}_ouverture.gif", save_all=True, append_images=images[1:], duration=45, loop=0,
                   optimize=True)


def sol_et_mur(x0, x1, z0, z1, mur_z):
    """Dalle avec la grille des blocs, et mur de planches derrière : pour la planche de la gamme."""
    W = int((x1 - x0) * 4)
    Hh = int((z1 - z0) * 4)
    yy, xx = np.mgrid[0:Hh, 0:W] / 4.0
    p = np.stack([xx + x0, np.zeros_like(xx), yy + z0], -1)
    beton = melange(col(70, 72, 74), col(92, 94, 96), fbm(p, 0.2, 4, 7))
    lignes = (np.abs(((xx + x0) % 16) - 8) > 7.85) | (np.abs(((yy + z0) % 16) - 8) > 7.85)
    beton = np.where(lignes[..., None], beton * 0.62, beton)
    tex_sol = np.dstack([beton, np.ones((Hh, W))])
    sol = [(np.array([[x0, 0, z0], [x0, 0, z1], [x1, 0, z1], [x1, 0, z0]], float),
            np.array([[0, 0], [0, Hh], [W, Hh], [W, 0]], float), tex_sol, True)]
    sol[0] = (sol[0][0], sol[0][1], sol[0][2], True)
    Hm = 40 * 4
    yy, xx = np.mgrid[0:Hm, 0:W] / 4.0
    p = np.stack([xx + x0, 40 - yy, np.full_like(xx, mur_z)], -1)
    fibres, joint, teinte = _planches(p, np.array([0, 0, -1.0]), 3.2, 11)
    bois = melange(col(78, 60, 42), col(110, 86, 60), fibres * 0.6 + teinte * 0.4) * (1 - 0.6 * joint)[..., None]
    tex_mur = np.dstack([bois, np.ones((Hm, W))])
    mur = [(np.array([[x1, 40, mur_z], [x1, 0, mur_z], [x0, 0, mur_z], [x0, 40, mur_z]], float),
            np.array([[0, 0], [0, Hm], [W, Hm], [W, 0]], float), tex_mur, True)]
    return sol + mur


def planche_gamme(coffres, dossier, fiches, gris=False):
    """Les cinq coffres côte à côte, à l'échelle, adossés à un mur, sur la grille des blocs."""
    el, _, tex_c = caisse_commune(gris)
    # vue de face, l'est (x grand) est à gauche : la commune prend la place la plus à l'est
    largeurs = [16, 32, 16, 32, 16]
    places = []
    x = 0.0
    for wd in reversed(largeurs):
        places.insert(0, x)
        x += wd + 14
    xfin = x - 14
    quads = sol_et_mur(-16, xfin + 16, -24, 16, 16.0)
    quads += quads_elements(el, tex_c, [("decal", (places[0], 0, 1.2))])
    for cf, x0 in zip(coffres, places[1:]):
        quads += cf.quads(0.0, decal=(x0, 0, 0.1))
    S = 3000
    v = Vue(math.radians(180 - 14), math.radians(20), ((-10, 0, -10), (xfin + 10, 34, 16)), S, marge=0.97)
    img = rendre(quads, v, img=fond(S, (26, 28, 31), (16, 17, 19)), ombre_sol=None)
    im = vers_image(img, 2000)
    k = 2000 / S
    haut = v.ecran([[0, 40, 16], [xfin, 40, 16]])[:, 1].min() * k
    bas = v.ecran([[-16, 0, -10], [xfin + 16, 0, -10]])[:, 1].max() * k
    im = im.crop((0, int(haut) - 30, 2000, int(bas) + 10))
    bande = Image.new("RGB", (2000, im.height + 150), (20, 22, 25))
    bande.paste(im, (0, 0))
    d = ImageDraw.Draw(bande)
    for (nom, rar, dims, cases), x0, wd in zip(fiches, places, largeurs):
        px = v.ecran([[x0 + wd / 2, 0, 0]])[0][0] * k
        nom_r, rgb = RARETES[rar]
        for j, (txt, f, c) in enumerate(((nom, police(28), (236, 236, 236)), (nom_r, police(24, True), rgb),
                                         (dims, police(20, True), (180, 180, 180)), (cases, police(20, True),
                                                                                   (180, 180, 180)))):
            tw = d.textlength(txt, font=f)
            d.text((px - tw / 2, im.height + 12 + j * 32), txt, font=f, fill=c)
    bande.save(dossier / ("gamme_gris.png" if gris else "gamme.png"), optimize=True)


# =============================================================================================== installation
JAVA_DONNEES = RACINE / "src" / "commun" / "java" / "fr" / "heraliz" / "crac" / "block" / "CoffresDonnees.java"
CREDIT = "HeraliZ, modules/bases/outils/coffres_modeles.py"
# en main et dans l'inventaire : coffre recentré sur le bloc (éléments décalés de 8 px), puis réduit
AFFICHAGE = {
    "large": {"gui": ([30, 225, 0], [0, 0, 0], 0.36), "fixed": ([0, 180, 0], [0, 0, 0], 0.42),
              "ground": ([0, 0, 0], [0, 2, 0], 0.22)},
    "haut": {"gui": ([30, 225, 0], [0, 0, 0], 0.42), "fixed": ([0, 180, 0], [0, 0, 0], 0.42),
             "ground": ([0, 0, 0], [0, 2, 0], 0.2)},
}
EN_MAIN = {"thirdperson_righthand": ([75, 45, 0], [0, 2.5, 0], 0.25), "thirdperson_lefthand": ([75, 45, 0], [0, 2.5, 0], 0.25),
           "firstperson_righthand": ([0, 45, 0], [0, 0, 0], 0.28), "firstperson_lefthand": ([0, 225, 0], [0, 0, 0], 0.28)}


def _arrondi(v):
    return [round(x, 4) for x in v]


def _u_de_x(f, uv, x0, x1, x):
    """u de la face (uv de Minecraft) au point x (faces nord, sud, dessus, dessous) : ordre des coins de FaceInfo."""
    u0, v0, u1, v1 = uv
    if f == "north":        # le coin (u0, v0) est du côté x grand
        return u0 + (x1 - x) / (x1 - x0) * (u1 - u0)
    return u0 + (x - x0) / (x1 - x0) * (u1 - u0)


def couper_x(elements, xc=16.0):
    """Moitiés gauche (x < xc) et droite (x > xc, ramenée de xc) des éléments, UV recoupées."""
    gauche, droite = [], []
    for el in elements:
        a, b = el["from"], el["to"]
        if b[0] <= xc + 1e-6:
            gauche.append(el)
            continue
        if a[0] >= xc - 1e-6:
            d = dict(el, **{"from": _arrondi([a[0] - xc, a[1], a[2]]), "to": _arrondi([b[0] - xc, b[1], b[2]])})
            if "rotation" in el:
                o = el["rotation"]["origin"]
                d["rotation"] = dict(el["rotation"], origin=_arrondi([o[0] - xc, o[1], o[2]]))
            droite.append(d)
            continue
        if "rotation" in el:
            raise SystemExit(f"pièce tournée à cheval sur la coupe des deux moitiés : {a} {b}")
        for cote in ("g", "d"):
            lo, hi = (a[0], xc) if cote == "g" else (xc, b[0])
            faces = {}
            for f, fd in el["faces"].items():
                if f == ("east" if cote == "g" else "west"):
                    continue      # face de coupe : n'existe pas
                uv = list(fd["uv"])
                if f in ("north", "south", "up", "down"):
                    ua, ub = _u_de_x(f, uv, a[0], b[0], lo), _u_de_x(f, uv, a[0], b[0], hi)
                    if f == "north":
                        uv[0], uv[2] = ub, ua
                    else:
                        uv[0], uv[2] = ua, ub
                faces[f] = dict(fd, uv=_arrondi(uv))
            dx = 0.0 if cote == "g" else xc
            n = {"from": _arrondi([lo - dx, a[1], a[2]]), "to": _arrondi([hi - dx, b[1], b[2]]), "faces": faces}
            for k in ("shade", "forge_data"):
                if k in el:
                    n[k] = el[k]
            (gauche if cote == "g" else droite).append(n)
    return gauche, droite


def decaler_elements(elements, dx, dy, dz):
    out = []
    for el in elements:
        d = dict(el, **{"from": _arrondi([el["from"][0] + dx, el["from"][1] + dy, el["from"][2] + dz]),
                        "to": _arrondi([el["to"][0] + dx, el["to"][1] + dy, el["to"][2] + dz])})
        if "rotation" in el:
            o = el["rotation"]["origin"]
            d["rotation"] = dict(el["rotation"], origin=_arrondi([o[0] + dx, o[1] + dy, o[2] + dz]))
        out.append(d)
    return out


def modele_json(elements, texture, display=None):
    m = {"credit": CREDIT, "textures": {"0": texture, "particle": texture}, "elements": elements}
    if display:
        m["display"] = display
    return m


def mesures(m):
    """Boîte d'adossement (x0, z0, x1, z1), formes des deux cases (x0, y0, z0, x1, y1, z1), coffre tourné au nord."""
    a = np.min([c.a for c in m.cubes], 0)
    b = np.max([c.b for c in m.cubes], 0)
    z0, z1 = round(float(max(a[2], 0.0)), 3), round(float(min(b[2], 16.0)), 3)
    if m.forme == "large":
        boite = (0.0, z0, 16.0, z1)
        h = round(float(b[1]), 3)
        principale = (round(float(max(a[0], 0.0)), 3), 0.0, z0, 16.0, h, z1)
        annexe = (0.0, 0.0, z0, round(float(min(b[0], 32.0)) - 16.0, 3), h, z1)
    else:
        x0, x1 = round(float(max(a[0], 0.0)), 3), round(float(min(b[0], 16.0)), 3)
        boite = (x0, z0, x1, z1)
        principale = (x0, 0.0, z0, x1, 16.0, z1)
        annexe = (x0, 0.0, z0, x1, round(float(b[1]) - 16.0, 3), z1)
    return boite, principale, annexe


def java_donnees(coffres):
    def tab(v):
        return "{" + ", ".join(f"{x}" for x in v) + "}"

    def tabf(v):
        return "{" + ", ".join(f"{x}F" for x in v) + "}"
    lignes = []
    for cf in coffres:
        m = cf.m
        boite, principale, annexe = mesures(m)
        mobiles = []
        for os_, (pv, axe, cles) in m.cles.items():
            parent = m.os[os_].parent
            par = '"' + parent + '"' if parent else "null"
            mobiles.append(f'new Mobile("{os_}", {par}, {pv[0]}F, {pv[1]}F, '
                           f"{pv[2]}F, '{axe}', new float[]{tabf([c[0] for c in cles])}, "
                           f"new float[]{tabf([c[1] for c in cles])})")
        sep = ",\n" + " " * 20
        liste = sep.join(mobiles)
        lignes.append(f"            case \"{m.nom}\" -> new Mesures(new double[]{tab(boite)}, new double[]{tab(principale)},"
                      f"\n                new double[]{tab(annexe)}, {m.duree}, new Mobile[]{{\n"
                      f"                    {liste}}});")
    corps = "\n".join(lignes)
    return f"""package fr.heraliz.crac.block;

/**
 * Mesures des coffres par rareté (px, coffre tourné vers le nord) : boîte d'adossement, formes des deux cases, durée et
 * pièces qui bougent à l'ouverture. ÉCRIT par modules/bases/outils/coffres_modeles.py --installer : jamais à la main.
 */
public final class CoffresDonnees {{
    /**
     * Une pièce qui tourne à l'ouverture autour de sa charnière (px), sur un axe ; images clés : t de 0 à 1, angle en
     * degrés, lissé entre deux (comme angle_cles du script). parent : pièce qui la porte (le volant suit la porte).
     */
    public record Mobile(String os, String parent, float px, float py, float pz, char axe, float[] t, float[] angle) {{
    }}

    /** boite : x0, z0, x1, z1 ; formes : x0, y0, z0, x1, y1, z1 (case principale, puis l'autre case) ; durée en ticks. */
    public record Mesures(double[] boite, double[] formePrincipale, double[] formeAnnexe, int dureeTicks, Mobile[] mobiles) {{
    }}

    private CoffresDonnees() {{
    }}

    public static Mesures de(String nom) {{
        return switch (nom) {{
{corps}
            default -> throw new IllegalArgumentException(nom);
        }};
    }}
}}
"""


def installer(coffres):
    import json
    import adossement
    for cf in coffres:
        m = cf.m
        tex = f"crac:block/{m.nom}"
        cf.atlas.save(ASSETS / "textures" / "block" / f"{m.nom}.png", optimize=True)
        corps = cf.groupes["corps"]
        mobiles = {o: e for o, e in cf.groupes.items() if o != "corps"}
        tous_mobiles = [e for els in mobiles.values() for e in els]
        boite, _, _ = mesures(m)
        modeles = {}
        if m.forme == "large":
            cg, cd = couper_x(corps)
            mg, md = couper_x(tous_mobiles)
            modeles["annexe=false,open=false"] = (f"{m.nom}_ferme", cg + mg)
            modeles["annexe=false,open=true"] = (f"{m.nom}_ouvert", cg)
            modeles["annexe=true,open=false"] = (f"{m.nom}_annexe_ferme", cd + md)
            modeles["annexe=true,open=true"] = (f"{m.nom}_annexe_ouvert", cd)
        else:
            modeles["annexe=false,open=false"] = (f"{m.nom}_ferme", corps + tous_mobiles)
            modeles["annexe=false,open=true"] = (f"{m.nom}_ouvert", corps)
        noms = {}
        for cle, (nom, els) in modeles.items():
            base = modele_json(els, tex)
            adossement.ecrire(ASSETS / f"models/block/{nom}.json", base)
            noms[cle] = adossement.modeles_decales(nom, base, boite)
        if m.forme == "haut":
            nom_haut = f"{m.nom}_haut"
            adossement.ecrire(ASSETS / f"models/block/{nom_haut}.json", {"credit": CREDIT, "textures": {"particle": tex}})
            for ouvert in ("false", "true"):
                noms[f"annexe=true,open={ouvert}"] = {(sx, sz): f"crac:block/{nom_haut}" for sx in (-1, 0, 1)
                                                      for sz in (-1, 0, 1)}
        adossement.ecrire(ASSETS / f"blockstates/{m.nom}.json",
                          {"variants": adossement.variantes(noms, list(noms))})
        for os_, els in mobiles.items():
            adossement.ecrire(ASSETS / f"models/block/{m.nom}_mobile_{os_}.json", modele_json(els, tex))
        # objet : le coffre entier, fermé, recentré sur le bloc
        dx, dy = (-8.0, 0.0) if m.forme == "large" else (0.0, -8.0)
        aff = {k: {"rotation": r, "translation": t, "scale": [e, e, e]}
               for k, (r, t, e) in list(AFFICHAGE[m.forme].items()) + list(EN_MAIN.items())}
        adossement.ecrire(ASSETS / f"models/item/{m.nom}.json",
                          modele_json(decaler_elements(corps + tous_mobiles, dx, dy, 0.0), tex, aff))
        nb = sum(len(e) for _, e in modeles.values())
        print(f"[coffres] {m.nom} : {len(corps) + len(tous_mobiles)} éléments, {nb} dans les modèles de bloc")
    JAVA_DONNEES.write_text(java_donnees(coffres), encoding="utf-8", newline="\n")
    print("[coffres] installé dans", ASSETS, "et", JAVA_DONNEES.name)


PACK_GUI = DEPOT / "modules/interface/src/main/resources/resourcepacks/textures/assets/minecraft/textures/gui/container"


def ecran_coffre(colonnes, lignes, titre_txt, echelle=4):
    """Écran de coffre de colonnes × lignes, découpé dans generic_54.png du pack (bords, cases, inventaire du joueur)."""
    g = Image.open(PACK_GUI / "generic_54.png").convert("RGBA")
    k = g.width // 256
    c = lambda x0, y0, x1, y1: g.crop((x0 * k, y0 * k, x1 * k, y1 * k))
    larg = 7 + colonnes * 18 + 7
    haut_coffre = 17 + lignes * 18 + 14
    im = Image.new("RGBA", (max(larg, 176), haut_coffre + 83), (0, 0, 0, 0))
    ox = (im.width - larg) // 2
    # panneau du coffre : coins et bords étirés, cases répétées
    im.paste(c(0, 0, 7, 17), (ox, 0))
    im.paste(c(169, 0, 176, 17), (ox + larg - 7, 0))
    for x in range(7, larg - 7, 18):
        im.paste(c(7, 0, 25, 17), (ox + x, 0))
    for r in range(lignes):
        y = 17 + r * 18
        im.paste(c(0, 17, 7, 35), (ox, y))
        im.paste(c(169, 17, 176, 35), (ox + larg - 7, y))
        for col_ in range(colonnes):
            im.paste(c(7, 17, 25, 35), (ox + 7 + col_ * 18, y))
    yb = 17 + lignes * 18
    im.paste(c(0, 125, 7, 139), (ox, yb))
    im.paste(c(169, 125, 176, 139), (ox + larg - 7, yb))
    for x in range(7, larg - 7, 18):
        im.paste(c(7, 125, 25, 139), (ox + x, yb))
    # inventaire du joueur, centré
    im.paste(c(0, 139, 176, 222), ((im.width - 176) // 2, haut_coffre), c(0, 139, 176, 222))
    im = im.resize((im.width * echelle, im.height * echelle), Image.NEAREST)
    d = ImageDraw.Draw(im)
    d.text(((ox + 8) * echelle, 5 * echelle), titre_txt, font=police(7 * echelle, True), fill=(205, 205, 205))
    return im


def planche_ecrans(dossier):
    """Les écrans des coffres à 11 et 13 colonnes, posés dans une fenêtre de jeu 1920 × 1080 (taille d'interface 4)."""
    out = Image.new("RGB", (2 * 1920 // 2 + 40, 1080 // 2 + 120), (20, 22, 25))
    d = ImageDraw.Draw(out)
    d.text((24, 14), "Écrans des grands coffres (même style que les coffres du jeu, plus larges)", font=police(30),
           fill=(236, 236, 236))
    for k, (cols, nom, rar) in enumerate(((11, "Caisse d'armes renforcée", "epique"), (13, "Coffre-fort", "legendaire"))):
        fen = Image.new("RGB", (1920, 1080), (12, 13, 15))
        e = ecran_coffre(cols, 6, nom)
        fen.paste(e, ((1920 - e.width) // 2, (1080 - e.height) // 2), e)
        fen = fen.resize((960, 540), Image.LANCZOS)
        out.paste(fen, (k * 980, 64))
        nom_r, rgb = RARETES[rar]
        d.text((k * 980 + 8, 64 + 548), f"{nom} · {cols} × 6 = {cols * 6} cases · fenêtre 1920 × 1080, interface taille 4",
               font=police(20, True), fill=rgb)
    out.save(dossier / "ecrans_grands_coffres.png", optimize=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", help="dossier des rendus")
    g.add_argument("--installer", action="store_true", help="écrit les modèles, textures, états et données dans le mod")
    ap.add_argument("--seul", help="un seul coffre (nom de la fonction)")
    ap.add_argument("--sans-gif", action="store_true")
    args = ap.parse_args()
    if args.installer:
        installer([Coffre(fab()) for fab in COFFRES])
        return
    dossier = Path(args.apercu)
    dossier.mkdir(parents=True, exist_ok=True)
    noms = {"malle_chantier": ("Malle de chantier", "45 cases (9 × 5)"),
            "casier_metallique": ("Casier métallique", "54 cases (9 × 6)"),
            "caisse_armes": ("Caisse d'armes renforcée", "66 cases (11 × 6)"),
            "coffre_fort": ("Coffre-fort", "78 cases (13 × 6)")}
    coffres = []
    for fab in COFFRES:
        if args.seul and fab.__name__ != args.seul:
            continue
        cf = Coffre(fab())
        coffres.append(cf)
        nom, cont = noms[cf.m.nom]
        planche_coffre(cf, dossier, nom, cont)
        if not args.sans_gif:
            gif_ouverture(cf, dossier)
        cf.atlas.save(dossier / f"{cf.m.nom}_texture.png")
        print(f"[coffres] {cf.m.nom} : éléments {cf.nb_elements()}, texture {cf.atlas.size[0]} px")
    if len(coffres) == 4:
        fiches = [("Caisse en métal", "commune", "1 bloc", "18 cases (9 × 2)")] + \
                 [(noms[c.m.nom][0], c.m.rarete, c.m.blocs.replace(" × ", " × "), noms[c.m.nom][1]) for c in coffres]
        planche_gamme(coffres, dossier, fiches)
        planche_gamme(coffres, dossier, fiches, gris=True)
        planche_ecrans(dossier)
    print("rendus dans", dossier)


if __name__ == "__main__":
    main()
