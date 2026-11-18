#!/usr/bin/env python3
"""Maquettes (18/11, à valider par le propriétaire) : un seul style, plus petit et plus propre, pour tous les messages
qui s'affichent à l'écran pendant le jeu, et la bourse de hérals.

Demande du propriétaire du 18/11 : « faire une UI plus propre pendant qu'on s'extracte, et toutes les UI qui s'affichent à
l'écran (quêtes, infos sur un nouveau point d'extraction, quand on vend un objet à l'HDV…) : un affichage toujours le même
et bien propre ; là ça fait trop UI IA ; et un peu plus petit, là c'est trop grossier ». Et la bourse : « les hérals
gagnés sur la carte donnent une bourse qu'on revend au PNJ du spawn ».

    python modules/noyau/outils/apercu_notifications.py [FOND] [DOSSIER]

FOND : une capture du jeu (1920 x 1080 ou plus petite, recadrée) ; DOSSIER : par défaut maquettes/notifications. Rendu à
l'échelle d'interface 4 (celle d'un écran 1080p), polices DM Sans du jeu, tailles en unités d'interface comme dans le
code (une unité = 4 pixels).
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

RACINE = Path(__file__).resolve().parents[3]
POLICES = RACINE / "modules/noyau/src/client/resources/assets/heraliz_noyau/font"
S = 4                       # échelle d'interface
W, H = 1920, 1080
GW, GH = W // S, H // S     # 480 x 270 unités

TEXTE, ATTENUE, FAIBLE = (236, 238, 242), (169, 175, 186), (110, 116, 128)
VERRE = (13, 15, 20)
OK, OR, INFO, ALERTE, DANGER = (95, 208, 138), (232, 195, 106), (111, 168, 255), (232, 179, 74), (227, 32, 27)

_polices = {}


def police(gras, taille):
    k = (gras, taille)
    if k not in _polices:
        _polices[k] = ImageFont.truetype(str(POLICES / ("dmsans_bold.ttf" if gras else "dmsans_medium.ttf")), int(round(taille * S)))
    return _polices[k]


class Calque:
    """Dessin en unités d'interface sur une image RGBA (rectangles en points d'écran exacts, textes nets)."""

    def __init__(self, im):
        self.im = im

    def rect(self, x0, y0, x1, y1, c, a=255):
        L = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        ImageDraw.Draw(L).rectangle([round(x0 * S), round(y0 * S), round(x1 * S) - 1, round(y1 * S) - 1], fill=c + (int(a),))
        self.im.alpha_composite(L)

    def cadre(self, x, y, w, h, c, a):
        e = 1 / S
        self.rect(x, y, x + w, y + e, c, a)
        self.rect(x, y + h - e, x + w, y + h, c, a)
        self.rect(x, y, x + e, y + h, c, a)
        self.rect(x + w - e, y, x + w, y + h, c, a)

    def largeur(self, s, gras, taille):
        return police(gras, taille).getlength(s) / S

    def texte(self, s, x, cy, gras, taille, c, a=255, ombre=True):
        """Bord gauche x, milieu des capitales sur cy."""
        f = police(gras, taille)
        hb = f.getbbox("H")
        haut = cy * S - (hb[1] + hb[3]) / 2
        L = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(L)
        if ombre:
            d.text((x * S + 2, haut + 2), s, font=f, fill=(0, 0, 0, int(a * 0.55)))
        d.text((x * S, haut), s, font=f, fill=c + (int(a),))
        self.im.alpha_composite(L)
        return f.getlength(s) / S

    def droite(self, s, xd, cy, gras, taille, c, a=255):
        return self.texte(s, xd - self.largeur(s, gras, taille), cy, gras, taille, c, a)

    def centre(self, s, cx, cy, gras, taille, c, a=255):
        return self.texte(s, cx - self.largeur(s, gras, taille) / 2, cy, gras, taille, c, a)

    def image(self, im, x, y, w, h):
        self.im.alpha_composite(im.resize((round(w * S), round(h * S)), Image.NEAREST), (round(x * S), round(y * S)))


# ---------------------------------------------------------------------------------------------- le style commun
TI, TT = 6, 5.5          # tailles du titre (gras) et du texte, en unités d'interface


def largeur_bandeau(c, titre, texte):
    return max(c.largeur(titre, True, TI), c.largeur(texte or "", False, TT)) + 4 + 1 + 4


def bandeau(c, x, y, titre, texte, couleur, largeur=None):
    """Le bandeau commun : verre sombre, filet d'un point, trait de couleur à gauche, titre gras 6 et texte 5,5 sur une
    ligne chacun (ou une seule ligne si pas de texte). Pas d'icône, pas de capitales espacées. Rend sa hauteur."""
    xi = x + 1 + 4
    w = largeur or largeur_bandeau(c, titre, texte)
    h = 10 if not texte else 17
    c.rect(x, y, x + w, y + h, VERRE, 205)
    c.cadre(x, y, w, h, (255, 255, 255), 20)
    c.rect(x, y, x + 1, y + h, couleur, 255)
    if texte:
        c.texte(titre, xi, y + 5.5, True, TI, TEXTE)
        c.texte(texte, xi, y + 12, False, TT, ATTENUE)
    else:
        c.texte(titre, xi, y + h / 2, True, TI, TEXTE)
    return h


def ic_point(c, x, cy, couleur):
    c.rect(x + 2, cy - 2, x + 6, cy + 2, couleur, 255)


def ic_piece(c, x, cy, couleur):
    """Pièce de hérals : disque doré (carré adouci) et un trait au milieu."""
    c.rect(x + 1, cy - 3, x + 7, cy + 3, couleur, 255)
    c.rect(x + 2, cy - 4, x + 6, cy + 4, couleur, 255)
    c.rect(x + 3.5, cy - 2, x + 4.5, cy + 2, (120, 86, 30), 255)


def ic_drapeau(c, x, cy, couleur):
    c.rect(x + 1, cy - 4, x + 2, cy + 4, (220, 224, 230), 255)
    c.rect(x + 2, cy - 4, x + 7, cy, couleur, 255)


def ic_alerte(c, x, cy, couleur):
    c.rect(x + 3.5, cy - 4, x + 4.5, cy + 1.5, couleur, 255)
    c.rect(x + 3.5, cy + 2.5, x + 4.5, cy + 3.5, couleur, 255)


def extraction(c, cx, y, reste, total, zone, dehors=False):
    """Chrono d'extraction compact en haut au milieu : point, « Extraction », temps à droite, barre fine, une ligne d'aide."""
    w, h = 100, 18
    x = cx - w / 2
    couleur = DANGER if dehors else OK
    c.rect(x, y, x + w, y + h, VERRE, 205)
    c.cadre(x, y, w, h, (255, 255, 255), 20)
    c.texte("Extraction", x + 4, y + 5.5, True, TI, TEXTE)
    c.droite("0:%02d" % reste, x + w - 4, y + 5.5, True, 6.5, TEXTE)
    p = 1 - reste / total
    c.rect(x + 4, y + 9.5, x + w - 4, y + 10.25, (255, 255, 255), 30)
    c.rect(x + 4, y + 9.5, x + 4 + (w - 8) * p, y + 10.25, couleur, 255)
    c.texte("Reviens dans la zone !" if dehors else "Reste dans la zone", x + 4, y + 13.75, False, 5, (255, 179, 172) if dehors else ATTENUE)
    c.droite("zone %s" % zone, x + w - 4, y + 13.75, False, 5, FAIBLE)


# ---------------------------------------------------------------------------------------------- la bourse
def icone_bourse(taille=64):
    """Icône de la bourse de hérals (64 x 64, pixel art éclairé d'en haut à gauche) : sac de cuir noué par un cordon
    doré, un « H » doré en relief."""
    n = taille
    yy, xx = np.mgrid[0:n, 0:n].astype(np.float32) + 0.5
    cx = n / 2
    # corps : ellipse large en bas ; col : rétréci sous le nœud ; collerette froncée au-dessus
    corps = ((xx - cx) / (n * 0.40)) ** 2 + ((yy - n * 0.65) / (n * 0.32)) ** 2 <= 1
    col = (abs(xx - cx) <= n * 0.09 + (yy - n * 0.30) * 0.45) & (yy >= n * 0.28) & (yy <= n * 0.44)
    # collerette froncée au-dessus du cordon : un éventail qui s'ouvre vers le haut, bord ondulé
    haut = n * 0.09 + n * 0.035 * np.abs(np.sin(xx * 0.55))
    fronce = (abs(xx - cx) <= n * 0.08 + (n * 0.30 - yy) * 0.62) & (yy >= haut) & (yy <= n * 0.31)
    forme = corps | col | fronce
    rng = np.random.default_rng(3)
    grain = rng.normal(0, 1, (n, n)).astype(np.float32)
    # éclairage : plus clair en haut à gauche, plus sombre en bas à droite, et selon la distance au centre du corps
    lum = 0.62 - (xx - cx) / n * 0.55 - (yy - n * 0.55) / n * 0.45
    lum -= np.clip(((xx - cx) / (n * 0.42)) ** 2 + ((yy - n * 0.64) / (n * 0.35)) ** 2 - 0.55, 0, 1) * 0.5
    lum += grain * 0.035
    base = np.array([122, 78, 45], np.float32)
    clair = np.array([200, 138, 82], np.float32)
    sombre = np.array([58, 34, 18], np.float32)
    t = np.clip(lum, 0, 1)[..., None]
    rgb = np.where(t > 0.5, base + (clair - base) * ((t - 0.5) * 2), sombre + (base - sombre) * (t * 2))
    # plis de la collerette
    plis = fronce & (np.sin((xx - cx) * 0.95) > 0.45)
    rgb[plis] *= 0.80
    bord_haut = fronce & (yy <= haut + 1.5)
    rgb[bord_haut] = rgb[bord_haut] * 0.6 + np.array([214, 158, 98], np.float32) * 0.4
    a = np.where(forme, 255, 0).astype(np.uint8)
    im = np.dstack([rgb.clip(0, 255).astype(np.uint8), a])
    img = Image.fromarray(im, "RGBA")
    d = ImageDraw.Draw(img)
    # cordon doré autour du col, nœud et deux bouts qui pendent
    y0 = int(n * 0.30)
    for k, c in ((0, (246, 210, 120)), (1, (214, 166, 72)), (2, (150, 104, 38))):
        d.line([(int(n * 0.30), y0 + k), (int(n * 0.70), y0 + k)], fill=c + (255,))
    d.rectangle([int(n * 0.56), y0 - 1, int(n * 0.62), y0 + 3], fill=(226, 178, 84, 255))
    d.line([(int(n * 0.60), y0 + 3), (int(n * 0.66), y0 + 12)], fill=(214, 166, 72, 255), width=2)
    d.line([(int(n * 0.58), y0 + 3), (int(n * 0.57), y0 + 13)], fill=(190, 140, 58, 255), width=2)
    # « H » doré en relief sur le ventre du sac
    hx, hy, hw, hh = int(cx - 6), int(n * 0.55), 12, 13
    for (dx, dy, c) in ((1, 1, (70, 44, 14)), (0, 0, (240, 200, 104))):
        d.rectangle([hx + dx, hy + dy, hx + 2 + dx, hy + hh + dy], fill=c + (255,))
        d.rectangle([hx + hw - 3 + dx, hy + dy, hx + hw - 1 + dx, hy + hh + dy], fill=c + (255,))
        d.rectangle([hx + dx, hy + 5 + dy, hx + hw - 1 + dx, hy + 7 + dy], fill=c + (255,))
    d.point([(hx, hy), (hx + hw - 3, hy)], fill=(255, 236, 170, 255))
    # contour d'un pixel, plus sombre
    al = np.array(img)[..., 3] > 0
    bord = al & ~(np.roll(al, 1, 0) & np.roll(al, -1, 0) & np.roll(al, 1, 1) & np.roll(al, -1, 1))
    arr = np.array(img)
    arr[bord, :3] = (np.array(arr[bord, :3], np.float32) * 0.45).astype(np.uint8)
    return Image.fromarray(arr, "RGBA")


def compact(v):
    if v < 1000:
        return str(v)
    if v < 10000:
        return ("%.1fk" % (v / 1000)).replace(".", ",").replace(",0k", "k")
    return "%dk" % (v // 1000)


def case(c, x, y, ic, montant=None, rarete=None, survol=False):
    c.rect(x, y, x + 20, y + 20, (0, 0, 0), 110)
    if rarete:
        c.rect(x + 2, y + 18.6, x + 18, y + 19.4, rarete, 230)
    c.cadre(x, y, 20, 20, (232, 195, 106) if survol else (255, 255, 255), 200 if survol else 34)
    if ic is not None:
        c.image(ic, x + 2, y + 2, 16, 16)
    if montant is not None:
        c.droite(compact(montant), x + 19, y + 16, True, 6.5, (255, 214, 120))


def infobulle(c, x, y, lignes):
    """Infobulle des objets (verre sombre, nom à la couleur de sa rareté)."""
    w = max(c.largeur(t, g, s) for t, g, s, _ in lignes) + 12
    h = 8 + sum(10 if s >= 7.5 else 9 for _, _, s, _ in lignes)
    c.rect(x, y, x + w, y + h, (10, 12, 16), 236)
    c.cadre(x, y, w, h, (255, 255, 255), 26)
    yy = y + 9
    for t, g, s, col in lignes:
        c.texte(t, x + 6, yy, g, s, col)
        yy += 10 if s >= 7.5 else 9


# ---------------------------------------------------------------------------------------------- planches
def fond(chemin):
    if chemin and Path(chemin).exists():
        im = Image.open(chemin).convert("RGB")
        r = max(W / im.width, H / im.height)
        im = im.resize((int(im.width * r + 1), int(im.height * r + 1)), Image.BICUBIC)
        l, t = (im.width - W) // 2, (im.height - H) // 2
        im = im.crop((l, t, l + W, t + H))
    else:
        im = Image.new("RGB", (W, H), (40, 46, 52)).filter(ImageFilter.GaussianBlur(4))
    return im.convert("RGBA")


def planche_notifications(fond_im, sortie):
    c = Calque(fond_im.copy())
    # en haut au milieu : chrono d'extraction compact
    extraction(c, GW / 2, 5, 24, 60, "15:19")
    # en haut à gauche : territoire
    bandeau(c, 5, 5, "Arctique", "Zone libre · drapeau à 25 m", INFO)
    # à droite, empilés sur une colonne de même largeur : les messages d'information (le plus récent en haut)
    messages = (("Extraction ouverte", "Plage du Pic · 12 min · 850 m au nord", OK),
                ("Le 7e régiment", "Tu trouves : Insigne du 7e régiment (2/5)", OR),
                ("Hôtel des ventes", "AK-47 vendu : 1 200 hérals", OR),
                ("+60 hérals", "dans ta bourse (63 en tout)", OR))
    w = max(120, max(largeur_bandeau(c, t, x) for t, x, _ in messages))
    y = 34
    for titre, texte, col in messages:
        y += bandeau(c, GW - 5 - w, y, titre, texte, col, largeur=w) + 2
    # au-dessus de la barre : les messages courts (avant : le texte de Minecraft au-dessus de la barre)
    t = "Impossible en combat"
    w = largeur_bandeau(c, t, None)
    bandeau(c, GW / 2 - w / 2, GH - 42, t, None, DANGER)
    c.im.convert("RGB").save(sortie, quality=92)
    print("écrit", sortie)


def planche_extraction(fond_im, avant, sortie):
    """Avant (capture du propriétaire) / après, à la même échelle."""
    im = Image.new("RGBA", (W, 640), (16, 18, 22, 255))
    c = Calque(im)
    c.texte("Avant", 8, 10, True, 8, ATTENUE)
    if avant and Path(avant).exists():
        a = Image.open(avant).convert("RGBA")
        im.alpha_composite(a, (32, 64))
    c.texte("Après (même échelle)", 8, 92, True, 8, ATTENUE)
    zone = fond_im.crop((0, 0, W, 240))
    im.alpha_composite(zone, (0, 400))
    c2 = Calque(im)
    # le chrono, posé à y = 400 px (100 unités)
    extraction(c2, GW / 2, 100 + 6, 24, 60, "15:19")
    extraction(c2, GW / 2 + 130, 100 + 6, 41, 60, "15:02", dehors=True)
    c2.texte("hors de la zone", GW / 2 + 130 - 20, 100 + 30, False, 5.5, FAIBLE)
    im.convert("RGB").save(sortie, quality=92)
    print("écrit", sortie)


def planche_bourse(fond_im, sortie, sortie_icone):
    ic = icone_bourse(64)
    ic.save(sortie_icone)
    print("écrit", sortie_icone)
    im = fond_im.copy()
    voile = Image.new("RGBA", im.size, (8, 10, 14, 170))
    im.alpha_composite(voile)
    c = Calque(im)
    c.texte("Bourse de hérals", 16, 16, True, 11, TEXTE)
    c.texte("Les hérals gagnés sur la carte vont dans la bourse ; on la revend à Mila, au spawn.", 16, 28, False, 7, ATTENUE)
    # l'icône en grand
    c.rect(16, 42, 16 + 72, 42 + 72, (0, 0, 0), 90)
    c.cadre(16, 42, 72, 72, (255, 255, 255), 30)
    c.image(ic, 20, 46, 64, 64)
    c.texte("icône 64 x 64", 16, 120, False, 6, FAIBLE)
    # dans l'inventaire : le montant à la place du nombre d'objets
    c.texte("Dans l'inventaire", 104, 46, True, 7.5, ATTENUE)
    xs = 104
    for i, m in enumerate((63, 412, 1250, None, None)):
        case(c, xs + i * 22, 54, ic if m else None, m, survol=(i == 2))
    c.texte("le montant, en bas à droite (1,2k = 1 250)", 104, 82, False, 6, FAIBLE)
    # infobulle
    infobulle(c, 104 + 2 * 22 + 6, 92, [("Bourse de hérals", True, 7.5, TEXTE),
                                        ("1 250 hérals", True, 7, (255, 214, 120)),
                                        ("À revendre à Mila, au spawn", False, 6.5, ATTENUE),
                                        ("Tombe au sol si tu meurs", False, 6.5, (255, 140, 130))])
    # le message quand des hérals arrivent dans la bourse
    c.texte("Quand des hérals arrivent dans la bourse", 300, 46, True, 7.5, ATTENUE)
    bandeau(c, 300, 56, "+60 hérals", "dans ta bourse (63 en tout)", OR)
    bandeau(c, 300, 78, "+12 hérals", "recycleur de terrain · bourse : 75", OR)
    c.im.convert("RGB").save(sortie, quality=92)
    print("écrit", sortie)


def main():
    fond_chemin = sys.argv[1] if len(sys.argv) > 1 else None
    dossier = Path(sys.argv[2]) if len(sys.argv) > 2 else RACINE / "maquettes" / "notifications"
    avant = sys.argv[3] if len(sys.argv) > 3 else None
    dossier.mkdir(parents=True, exist_ok=True)
    f = fond(fond_chemin)
    planche_notifications(f, dossier / "1_messages_a_l_ecran.jpg")
    planche_extraction(f, avant, dossier / "2_extraction_avant_apres.jpg")
    planche_bourse(f, dossier / "3_bourse.jpg", dossier / "bourse_64.png")


if __name__ == "__main__":
    main()
