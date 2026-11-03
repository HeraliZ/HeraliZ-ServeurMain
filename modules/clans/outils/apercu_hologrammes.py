#!/usr/bin/env python3
"""Rendus (03/11, à valider) : hologrammes de classement du spawn (argent, kills, clans), vus à 5 blocs.

    python modules/clans/outils/apercu_hologrammes.py [DOSSIER]

Imite l'entité d'affichage de texte de Minecraft (text_display) telle que la pose le serveur
(serveur/.../classement/Hologrammes.java) : 1 pixel de texte = 0,025 bloc à l'échelle 0,75 de l'entité, une ligne tous les 10 pixels, sans fond,
ombre du texte, tournée vers le joueur, plein éclairage, alignée à gauche, bas du texte au point posé (0,3 bloc
au-dessus du sol). Polices du noyau (DM Sans ; em = taille du .json / 1,302 ; lignes de base 7,38 pour « ui » et
« ui_bold », 6,86 pour « ui_small »), jeton des hérals (police de l'économie). Joueur debout à 5 blocs (yeux à 1,62,
champ de vision 70°, 1920 x 1080). Textes : fichiers de langue du mod (fr). Noms de joueurs et de clans inventés.
Approximatif : le jeu peut décaler le texte d'un ou deux pixels.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

RACINE = Path(__file__).resolve().parents[3]
POLICES = RACINE / "modules/noyau/src/client/resources/assets/heraliz_noyau/font"
JETON = RACINE / "modules/clans/src/client/resources/assets/heralisz_economie/textures/font/jeton.png"
LANG = RACINE / "modules/clans/src/client/resources/assets/heralisz_clans/lang"
S = 8  # sur-échantillonnage du texte (pixels d'image par pixel de texte)
ECHELLE = 0.75  # transformation de l'entité (scale)
PITCH = 10
PODIUM = [0xE8C36A, 0xB9C0CB, 0xD08A4E]
BLANC, CLAIR, GRIS, SOMBRE = 0xECEEF2, 0xC9CED6, 0xA8AEB8, 0x9AA3B2
ACCENT = {"argent": 0xE8C36A, "kills": 0xFF6B6B, "clans": 0x6FA8FF}
# police : (fichier, taille du .json, ligne de base)
UI, GRAS, PETITE = ("dmsans_medium", 11, 7.38), ("dmsans_bold", 11, 7.38), ("dmsans_medium", 9, 6.86)

ARGENT = [("Ferraille88", "WLF", 0x9FA8B8, 1_284_550), ("Sentinelle", "RAT", 0xC9A34A, 902_300), ("Nomade_du_Nord", "", 0, 655_120),
          ("Rodeur_Gris", "HZ", 0xE3201B, 410_000), ("Brindille", "KRK", 0x4FB6C9, 298_745), ("Ossature", "WLF", 0x9FA8B8, 201_390),
          ("Chasseur_de_Nuit", "VLT", 0x8F6BD8, 154_000), ("Pellicule", "", 0, 98_220)]
KILLS = [("Rodeur_Gris", "HZ", 0xE3201B, 342), ("Sentinelle", "RAT", 0xC9A34A, 289), ("Ossature", "WLF", 0x9FA8B8, 251),
         ("Grenaille", "KRK", 0x4FB6C9, 198), ("Nomade_du_Nord", "", 0, 176), ("Silex", "VLT", 0x8F6BD8, 143),
         ("Tourbe", "CRB", 0x6B7280, 120), ("Ferraille88", "WLF", 0x9FA8B8, 97)]
CLANS = [("Les Loups Gris", "WLF", 0x9FA8B8, 2278), ("Rats du Bunker", "RAT", 0xC9A34A, 1921), ("Nuit Rouge", "HZ", 0xE3201B, 1548),
         ("Kraken Noir", "KRK", 0x4FB6C9, 1128), ("L'Ours de Valmont et ses amis", "OUR", 0x5FD08A, 804),
         ("Vautours", "VLT", 0x8F6BD8, 824), ("Corbeaux", "CRB", 0x6B7280, 322), ("Silence", "SIL", 0xE8B34A, 124)]


def rgb(c):
    return (c >> 16) & 255, (c >> 8) & 255, c & 255


def nombre(v):
    return "{:,}".format(int(v)).replace(",", " ")


def court(s, n=22):
    return s if len(s) <= n else s[:n - 1].strip() + "…"


class Texte:
    """Lignes de morceaux (texte, police, couleur) dessinées comme le jeu (ombre décalée d'un pixel, couleur / 4)."""

    def __init__(self):
        self.cmap = TTFont(str(POLICES / "dmsans_bold.ttf")).getBestCmap()
        self.cache = {}
        self.jeton = Image.open(JETON).convert("RGBA")

    def f(self, police, cyr):
        nom, taille, _ = police
        if cyr:
            nom = nom.replace("dmsans", "cyrillique")
        cle = (nom, taille)
        if cle not in self.cache:
            self.cache[cle] = ImageFont.truetype(str(POLICES / (nom + ".ttf")), int(round(taille / 1.302 * S)))
        return self.cache[cle]

    def morceaux(self, s):
        out = []
        for ch in s:
            k = "fine" if ch == " " else (ord(ch) > 127 and ord(ch) not in self.cmap)
            if out and out[-1][1] == k:
                out[-1][0] += ch
            else:
                out.append([ch, k])
        return out

    def largeur(self, ligne):
        w = 0
        for s, police, _ in ligne:
            if police == "jeton":
                w += 10
                continue
            for m, k in self.morceaux(s):
                w += 3 * len(m) if k == "fine" else self.f(police, k).getlength(m) / S
        return w

    def dessiner(self, lignes):
        lw = max(self.largeur(l) for l in lignes)
        W, H = int(math.ceil(lw + 2)) * S, (len(lignes) * PITCH + 2) * S
        im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        for passe in ("ombre", "texte"):
            d = ImageDraw.Draw(im)
            dx = S if passe == "ombre" else 0
            for i, ligne in enumerate(lignes):
                x, y = 0.0, i * PITCH
                for s, police, c in ligne:
                    col = rgb(c)
                    if passe == "ombre":
                        col = tuple(v // 4 for v in col)
                    if police == "jeton":
                        j = self.jeton.resize((9 * S, 9 * S), Image.LANCZOS)
                        if passe == "ombre":
                            a = np.array(j)
                            a[..., :3] //= 4
                            j = Image.fromarray(a, "RGBA")
                        im.alpha_composite(j, (int(x * S + dx), int(y * S + dx)))
                        x += 10
                        continue
                    base = y + police[2]
                    for m, k in self.morceaux(s):
                        if k == "fine":
                            x += 3 * len(m)
                            continue
                        fo = self.f(police, k)
                        d.text((x * S + dx, base * S + dx), m, font=fo, fill=col + (255,), anchor="ls")
                        x += fo.getlength(m) / S
        return im


def lang():
    return json.loads((LANG / "fr_fr.json").read_text(encoding="utf-8"))


def tr(L, cle, *args):
    s = L["heralisz_clans.classement.holo." + cle]
    for a in args:
        s = s.replace("%s", str(a), 1)
    return s


def lignes_holo(genre, donnees, L, saison=3, minutes=2):
    out = [[(tr(L, "titre." + genre, saison), GRAS, ACCENT[genre])], []]
    if not donnees:
        out.append([(tr(L, "vide"), UI, GRIS)])
    for i, (nom, tag, coul, v) in enumerate(donnees[:8]):
        podium = i < 3
        p = GRAS if podium else UI
        l = [("%d.  " % (i + 1), GRAS, PODIUM[i] if podium else SOMBRE)]
        if genre == "clans":
            l += [("[%s] " % tag, GRAS, coul), (court(nom), p, BLANC if podium else CLAIR)]
        else:
            l += [(nom, p, BLANC if podium else CLAIR)]
            if tag:
                l += [(" [%s]" % tag, UI, coul)]
        l += [("  —  ", UI, SOMBRE)]
        cv = BLANC if podium else CLAIR
        if genre == "argent":
            l += [(nombre(v) + " ", p, cv), ("", "jeton", 0)]
        elif genre == "kills":
            l += [(tr(L, "kills", nombre(v)), p, cv)]
        else:
            l += [(tr(L, "points", nombre(v)), p, cv)]
        out.append(l)
    out.append([])
    out.append([(tr(L, "maj", minutes), PETITE, GRIS)])
    if genre == "clans":
        out.append([(tr(L, "regle"), PETITE, GRIS)])
    return out


# ------------------------------------------------------------------------------------------------ scène du spawn
W3, H3, FOV = 1920, 1080, 70.0
F = (H3 / 2) / math.tan(math.radians(FOV / 2))
CAM = (0.0, 1.62, 5.0)


def projeter(p):
    x, y, z = p[0] - CAM[0], p[1] - CAM[1], p[2] - CAM[2]
    if z >= -0.05:
        return None
    return W3 / 2 + F * x / -z, H3 / 2 - F * y / -z


def scene():
    yy = np.linspace(0, 1, H3)[:, None, None]
    ciel = np.array([118, 150, 196], np.float32) * (1 - yy) + np.array([196, 210, 222], np.float32) * yy
    im = Image.fromarray(np.repeat(ciel, W3, axis=1).clip(0, 255).astype(np.uint8), "RGB").convert("RGBA")
    d = ImageDraw.Draw(im)
    hz = projeter((0, 0, -400))[1]
    d.polygon([(0, hz), (W3, hz), (W3, H3), (0, H3)], fill=(120, 122, 118, 255))
    for x in range(-30, 31):
        a, b = projeter((x, 0, CAM[2] - 0.5)), projeter((x, 0, -60))
        d.line([a, b], fill=(98, 100, 97, 255), width=2)
    for z in range(-60, int(CAM[2])):
        d.line([projeter((-30, 0, z)), projeter((30, 0, z))], fill=(98, 100, 97, 255), width=2)
    q = [projeter(p) for p in [(-40, 0, -14), (40, 0, -14), (40, 4, -14), (-40, 4, -14)]]
    d.polygon(q, fill=(140, 136, 128, 255))
    for yb in range(1, 4):
        d.line([projeter((-40, yb, -14)), projeter((40, yb, -14))], fill=(118, 114, 107, 255), width=2)
    # point posé : petit repère au sol (dalle plus claire) sous chaque hologramme
    return im


def poser(im, texte_img, x, z, y=0.3):
    """Hologramme tourné vers le joueur : centré en x, bas du texte à y."""
    dist = CAM[2] - z
    k = 0.025 * ECHELLE * F / dist / S  # pixels d'écran par pixel d'image
    w, h = int(round(texte_img.width * k)), int(round(texte_img.height * k))
    r = texte_img.resize((w, h), Image.LANCZOS)
    bas = projeter((x, y, z))
    im.alpha_composite(r, (int(round(bas[0] - w / 2)), int(round(bas[1] - h))))


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "hologrammes"
    dossier.mkdir(parents=True, exist_ok=True)
    L = lang()
    T = Texte()
    donnees = {"argent": ARGENT, "kills": KILLS, "clans": CLANS}
    imgs = {g: T.dessiner(lignes_holo(g, v, L)) for g, v in donnees.items()}
    for g in donnees:
        im = scene()
        poser(im, imgs[g], 0.0, 0.0)
        im.convert("RGB").save(dossier / ("hologramme_5_blocs_%s.png" % g), optimize=True)
        print("écrit", dossier / ("hologramme_5_blocs_%s.png" % g))
    # les trois côte à côte, à 5 blocs du premier rang (vue d'ensemble : on recule de 4 blocs pour les voir en entier)
    global CAM
    for nom, recul in (("hologrammes_spawn_les_trois_5_blocs.png", 0.0), ("hologrammes_spawn_les_trois_9_blocs.png", 4.0)):
        CAM = (0.0, 1.62, 5.0 + recul)
        im = scene()
        for g, x in zip(donnees, (-5.0, 0.0, 5.0)):
            poser(im, imgs[g], x, 0.0)
        im.convert("RGB").save(dossier / nom, optimize=True)
        print("écrit", dossier / nom)
    CAM = (0.0, 1.62, 5.0)
    im = scene()
    poser(im, T.dessiner(lignes_holo("kills", [], L)), 0.0, 0.0)
    im.convert("RGB").save(dossier / "hologramme_vide.png", optimize=True)
    print("écrit", dossier / "hologramme_vide.png")


if __name__ == "__main__":
    main()
