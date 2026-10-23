"""Aperçu de l'écran d'ouverture des caisses (CaseOpenScreen, Caisses 2.20.1) et contrôle « rien ne déborde ».

    python mods/heraliz-crates/outils/apercu_ecran_caisse.py [--sortie DOSSIER]

Refait l'écran comme le jeu le dessine, sans Minecraft :
- les constantes de mise en page sont LUES dans CaseOpenScreen.java (une seule source : changer le Java suffit) ;
- les polices sont les vraies (fichiers TTF du mod, tailles et décalages lus dans assets/heralizcrates/font/*.json),
  avec le secours cyrillique, et placées comme Minecraft 1.20.1 (code lu dans TrueTypeGlyphProvider, SheetGlyphInfo,
  BakedGlyph) : échelle stbtt_ScaleForPixelHeight(taille) = taille / (ascent - descent) ; avance sans crénage ;
  Font.width arrondi au-dessus ; haut du glyphe = y + ascent - haut du glyphe + 2 x décalage - 3 (le décalage vertical
  du fichier de police compte deux fois : dans la boîte du glyphe de stb et dans bearingY), soit la ligne de base à
  y + ascent + 2 x décalage - 3 ; à gauche, l'approche du glyphe compte aussi deux fois (bearingX) ; capitales espacées
  lettre par lettre, chacune posée sur un point d'écran comme Ui.spaced ;
- l'échelle d'interface est celle que choisit Echelle.voulue() (la plus grande qui garde un canevas de 600 x 340).
Les objets sont des carrés de couleur à leur place et à leur taille.

Contrôle : chaque texte (encre réelle des lettres) doit rester dans sa boîte (panneau, carte, bouton, colonne, bande) et
ne toucher aucun autre texte, pour les trois langues et plusieurs tailles de fenêtre. Les textes centrés dans une
boîte (touches, boutons, quantités) : écart en haut et écart en bas des capitales égaux à un point d'écran près (aux
échelles 2 et 3), accents et jambages dans la boîte ; pastilles de la légende sur le milieu des capitales à un point
près. La liste des défauts doit être vide (sinon code de sortie 1).

Images : maquettes/caisses_lisibles/ (1920 x 1080 et 1280 x 720, écran de la roue et carte du gain, en français ; la
roue en russe en 1280 x 720).
"""
import argparse
import json
import math
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont
from fontTools.ttLib import TTFont

MOD = Path(__file__).resolve().parents[1]
DEPOT = MOD.parents[1]
JAVA = MOD / "src/client/java/fr/heraliz/crates/client/gui/CaseOpenScreen.java"
POLICES = MOD / "src/client/resources/assets/heralizcrates/font"
LANGUES = MOD / "src/client/resources/assets/heralizcrates/lang"
SORTIE = DEPOT / "maquettes/caisses_lisibles"

# polices du Java (CaseOpenScreen) -> fichier de police
POLICE_DE = {"TITRE": "ui_titre", "NOM": "ui_nom", "SOUS": "ui_sous", "SOUS_GRAS": "ui_sous_bold", "TEXTE": "ui_small",
             "TEXTE_GRAS": "ui_small_bold", "COMPTE": "ui_compte_bold", "BOLD": "ui_bold"}
RARETES_ID = ["common", "uncommon", "rare", "epic", "legendary", "mythic"]

TEXTE, MUTED, FAINT, OR = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0xE8C36A


# ----------------------------------------------------------------------------------------------- constantes du Java
def constantes():
    src = JAVA.read_text(encoding="utf-8")
    c = {}
    for nom, val in re.findall(r"\b([A-Z][A-Z0-9_]*)\s*=\s*(0x[0-9A-Fa-f]+|-?\d+(?:\.\d+)?)f?\s*(?=[,;])", src):
        if nom not in c:
            c[nom] = int(val, 16) if val.startswith("0x") else (float(val) if "." in val else int(val))
    m = re.search(r"RARETES\s*=\s*\{([^}]*)\}", src)
    c["RARETES"] = [int(v.strip(), 16) for v in m.group(1).split(",")]
    for nom in ("OR_HAUT", "OR_BAS", "OR_TEXTE"):
        assert nom in c, nom
    return c


C = constantes()


# ----------------------------------------------------------------------------------------------- polices
class Fonte:
    """Une police TTF d'un fournisseur : métriques comme TrueTypeGlyphProvider."""

    def __init__(self, fichier, taille, decalage):
        self.chemin = POLICES / fichier.split(":")[1]
        tt = TTFont(self.chemin)
        self.cmap = tt.getBestCmap()
        self.hmtx = tt["hmtx"]
        h = tt["hhea"]
        self.echelle = taille / (h.ascent - h.descent)  # stbtt_ScaleForPixelHeight
        self.upm = tt["head"].unitsPerEm
        self.ascent = h.ascent * self.echelle
        self.caps = tt["OS/2"].sCapHeight * self.echelle
        self.taille = taille
        self.decalage = decalage
        self.pil = {}

    def a(self, ch):
        return ord(ch) in self.cmap

    def avance(self, ch):
        return self.hmtx[self.cmap[ord(ch)]][0] * self.echelle

    def approche(self, ch):
        """Approche gauche (lsb) en unités : Minecraft l'ajoute une 2e fois (bearingX)."""
        return self.hmtx[self.cmap[ord(ch)]][1] * self.echelle

    def image(self, s):
        if s not in self.pil:
            # FreeType : taille = em en pixels
            self.pil[s] = ImageFont.truetype(str(self.chemin), max(1, round(self.echelle * self.upm * s * 64) / 64),
                                             layout_engine=ImageFont.Layout.BASIC)
        return self.pil[s]


class Police:
    def __init__(self, nom):
        d = json.loads((POLICES / f"{nom}.json").read_text(encoding="utf-8"))
        self.nom = nom
        self.espaces = {}
        self.fontes = []
        for p in d["providers"]:
            if p["type"] == "space":
                self.espaces.update(p["advances"])
            elif p["type"] == "ttf":
                self.fontes.append(Fonte(p["file"], p["size"], p.get("shift", [0, 0])[1]))
        self.taille = self.fontes[0].taille

    def fonte(self, ch):
        for f in self.fontes:
            if f.a(ch):
                return f
        return None

    def avance(self, ch):
        if ch in self.espaces:
            return self.espaces[ch]
        f = self.fonte(ch)
        return f.avance(ch) if f else 6.0  # police de Minecraft en secours

    def largeur(self, s):
        """Font.width : somme des avances, arrondie au-dessus."""
        return math.ceil(sum(self.avance(ch) for ch in s) - 1e-6)

    def largeur_espacee(self, s, esp):
        """Ui.spacedWidth : largeur arrondie de chaque lettre + espacement."""
        return max(0.0, sum(self.largeur(ch) + esp for ch in s) - esp)


POL = {k: Police(v) for k, v in POLICE_DE.items()}
# les tailles et métriques recopiées dans le Java (centrage) doivent être celles des polices
assert C["T_BOUTON"] == POL["BOLD"].taille and C["T_TEXTE"] == POL["TEXTE"].taille == POL["TEXTE_GRAS"].taille
assert C["T_COMPTE"] == POL["COMPTE"].taille
_dm = POL["TEXTE"].fontes[0]
assert (C["DM_ASCENT"], C["DM_CAPS"]) == (round(_dm.ascent / _dm.echelle), round(_dm.caps / _dm.echelle))
assert C["DM_HAUTEUR"] == round(_dm.taille / _dm.echelle) and all(p.decalage == C["DECALAGE_Y"] for q in POL.values() for p in q.fontes)

def ligne_de_base(f):
    """Ligne de base sous le point de dessin (TrueTypeGlyphProvider.Glyph.bearingY, BakedGlyph.render : up - 3)."""
    return f.ascent + 2 * f.decalage - 3.0


def milieu_caps_java(taille, texte=""):
    """CaseOpenScreen.milieuCaps : même formule, constantes lues dans le Java (Onest si la 1re lettre est cyrillique)."""
    cyr = False
    for ch in texte:
        if ch.isalpha():
            cyr = 0x400 <= ord(ch) <= 0x4FF
            break
    p = "ON_" if cyr else "DM_"
    return (C[p + "ASCENT"] - C[p + "CAPS"] / 2) * taille / C[p + "HAUTEUR"] + 2 * C["DECALAGE_Y"] - 3


def y_centre(haut, hauteur, taille, texte=""):
    """CaseOpenScreen.yCentre."""
    return haut + hauteur / 2 - milieu_caps_java(taille, texte)


# ----------------------------------------------------------------------------------------------- textes
def langue(code):
    return json.loads((LANGUES / f"{code}.json").read_text(encoding="utf-8"))


def pct(v, fr):
    if v <= 0:
        s = "0"
    elif v >= 10:
        s = f"{v:.1f}"
    elif v >= 0.01:
        s = f"{v:.2f}"
    else:
        s = f"{v:.3f}"
    if "." in s:
        s = s.rstrip("0").rstrip(".")
    if fr:
        s = s.replace(".", ",")
    return s + " %"


def ellipsis(pol, s, maxw):
    if pol.largeur(s) <= maxw:
        return s
    dw = pol.largeur("…")
    out = ""
    for ch in s:
        if pol.largeur(out + ch) + dw > maxw:
            break
        out += ch
    return out.strip() + "…"


# ----------------------------------------------------------------------------------------------- rendu
def argb_rgba(rgb, a=255):
    return ((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, max(0, min(255, int(a))))


class Ecran:
    def __init__(self, fen_w, fen_h, dessiner=True):
        self.s = echelle_voulue(fen_w, fen_h)
        self.w = math.ceil(fen_w / self.s)
        self.h = math.ceil(fen_h / self.s)
        self.dessiner = dessiner
        self.img = Image.new("RGBA", (fen_w, fen_h), (0, 0, 0, 255)) if dessiner else None
        self.textes = []  # (étiquette, boîte d'encre, boîte permise)
        self.centres = []  # (étiquette, haut des capitales, ligne de base, haut de la boîte, bas de la boîte)
        self.pastilles = []  # (étiquette, milieu de la pastille, milieu des capitales du texte)

    # formes (unités d'interface)
    def rect(self, x0, y0, x1, y1, rgb, a=255):
        if not self.dessiner or x1 <= x0 or y1 <= y0:
            return
        calque = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        ImageDraw.Draw(calque).rectangle([x0 * self.s, y0 * self.s, x1 * self.s - 1, y1 * self.s - 1], fill=argb_rgba(rgb, a))
        self.img.alpha_composite(calque)

    def rond(self, x, y, w, h, rgb, a=255, contour=None, rayon=2):
        if not self.dessiner:
            return
        calque = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(calque)
        d.rounded_rectangle([x * self.s, y * self.s, (x + w) * self.s - 1, (y + h) * self.s - 1], radius=rayon * self.s,
                            fill=argb_rgba(rgb, a), outline=argb_rgba(*contour) if contour else None,
                            width=max(1, self.s // 2) if contour else 0)
        self.img.alpha_composite(calque)

    def tri(self, pts, rgb, a=255):
        if not self.dessiner:
            return
        calque = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        ImageDraw.Draw(calque).polygon([(x * self.s, y * self.s) for x, y in pts], fill=argb_rgba(rgb, a))
        self.img.alpha_composite(calque)

    def lueur(self, cx, cy, rx, ry, rgb, a):
        if not self.dessiner:
            return
        calque = Image.new("RGBA", self.img.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(calque)
        n = 16
        for i in range(n, 0, -1):
            t = i / n
            d.ellipse([(cx - rx * t) * self.s, (cy - ry * t) * self.s, (cx + rx * t) * self.s, (cy + ry * t) * self.s],
                      fill=argb_rgba(rgb, a * (1 - t) * 2 / n * 4))
        self.img.alpha_composite(calque)

    def objet(self, cx, cy, taille, rgb):
        """Objet du jeu : carré de couleur en attendant le vrai rendu."""
        self.rect(cx - taille / 2, cy - taille / 2, cx + taille / 2, cy + taille / 2, rgb, 150)

    # textes
    def texte(self, etiquette, pol, s, x, y, rgb, permis, a=255, espacement=None, boite=None):
        """Texte à (x, y) = point de dessin, comme drawString (après Ui.placer : point d'écran le plus proche ; en capitales
        espacées, chaque lettre est posée ainsi). boite = (haut, bas) : texte à centrer dans cette boîte (contrôlé)."""
        y = round(y * self.s) / self.s
        encre = None
        cx = x
        caps = None
        for ch in s:
            f = pol.fonte(ch)
            av = pol.avance(ch)
            px_ = round(cx * self.s) / self.s if espacement is not None or cx == x else cx
            if f is not None and ch not in pol.espaces:
                base = y + ligne_de_base(f)
                if caps is None:
                    caps = (base - f.caps, base)
                gx = px_ + f.approche(ch)
                pil = f.image(self.s)
                bb = pil.getbbox(ch, anchor="ls")
                if bb[2] > bb[0]:
                    b = (gx + bb[0] / self.s, base + bb[1] / self.s, gx + bb[2] / self.s, base + bb[3] / self.s)
                    encre = b if encre is None else (min(encre[0], b[0]), min(encre[1], b[1]), max(encre[2], b[2]), max(encre[3], b[3]))
                if self.dessiner:
                    ImageDraw.Draw(self.img).text((gx * self.s, base * self.s), ch, font=pil, anchor="ls", fill=argb_rgba(rgb, a))
            if espacement is None:
                cx = px_ + av
            else:
                cx = px_ + pol.largeur(ch) + espacement
        if encre is not None:
            self.textes.append((etiquette, encre, permis))
        if boite is not None and caps is not None:
            self.centres.append((etiquette, caps[0], caps[1], boite[0], boite[1]))
        return caps

    def centre(self, etiquette, pol, s, cx, y, rgb, permis, a=255, boite=None):
        return self.texte(etiquette, pol, s, cx - pol.largeur(s) / 2, y, rgb, permis, a, boite=boite)

    def droite(self, etiquette, pol, s, xd, y, rgb, permis, a=255, boite=None):
        return self.texte(etiquette, pol, s, xd - pol.largeur(s), y, rgb, permis, a, boite=boite)

    def espace_centre(self, etiquette, pol, s, cx, y, rgb, esp, permis, a=255, boite=None):
        s = s.upper()
        return self.texte(etiquette, pol, s, cx - pol.largeur_espacee(s, esp) / 2, y, rgb, permis, a, espacement=esp, boite=boite)

    def espace(self, etiquette, pol, s, x, y, rgb, esp, permis, a=255, boite=None):
        return self.texte(etiquette, pol, s.upper(), x, y, rgb, permis, a, espacement=esp, boite=boite)

    def compte(self, etiquette, n, xd, haut, alpha=1.0):
        """CaseOpenScreen.compte : « ×12 » centré dans une pastille sombre (coin haut droit en xd, haut)."""
        t = f"×{n}"
        tw = POL["COMPTE"].largeur(t)
        w = tw + 2 * C["COMPTE_PAD"]
        h = C["COMPTE_H"]
        self.rond(xd - w, haut, w, h, 0x07080A, 170 * alpha)
        self.texte(etiquette, POL["COMPTE"], t, xd - C["COMPTE_PAD"] - tw, y_centre(haut, h, C["T_COMPTE"], t), 0xFFFFFF,
                   (xd - w, haut, xd, haut + h), 235 * alpha, boite=(haut, haut + h))


def echelle_voulue(fw, fh):
    for s in range(8, 1, -1):
        if fw // s >= 600 and fh // s >= 340:
            return s
    return 1


# ----------------------------------------------------------------------------------------------- l'écran
OBJETS = [  # (clé de nom, rareté, quantité, chance)
    ("Boussole", 1, 1, 18.0), ("Bandage", 0, 12, 20.2), ("Lampe torche", 0, 1, 15.0), ("Gourde", 0, 1, 12.0),
    ("Kit de couture", 1, 1, 9.0), ("Jumelles", 2, 1, 7.0), ("Cartouches 12 ×", 1, 24, 6.0), ("Gilet tactique", 3, 1, 5.0),
    ("Carte d'accès verte", 2, 1, 4.0), ("Lunette 4x", 4, 1, 2.6), ("Karambit | Doppler Black Pearl", 5, 1, 1.2),
]
NOMS_RU = {"Boussole": "Компас", "Bandage": "Бинт", "Lampe torche": "Фонарик", "Gourde": "Фляга", "Kit de couture": "Набор для шитья",
           "Jumelles": "Бинокль", "Cartouches 12 ×": "Патроны 12 ×", "Gilet tactique": "Тактический жилет",
           "Carte d'accès verte": "Зелёная карта доступа", "Lunette 4x": "Прицел 4x", "Karambit | Doppler Black Pearl": "Керамбит | Doppler Black Pearl"}
NOMS_EN = {"Boussole": "Compass", "Bandage": "Bandage", "Lampe torche": "Flashlight", "Gourde": "Canteen", "Kit de couture": "Sewing kit",
           "Jumelles": "Binoculars", "Cartouches 12 ×": "12 gauge shells", "Gilet tactique": "Tactical vest",
           "Carte d'accès verte": "Green keycard", "Lunette 4x": "4x scope", "Karambit | Doppler Black Pearl": "Karambit | Doppler Black Pearl"}


def nom_objet(n, code):
    return NOMS_RU[n] if code == "ru_ru" else NOMS_EN[n] if code == "en_us" else n


def dessiner_ecran(fen_w, fen_h, code, etat, dessiner=True, sous_repere=0, titre="Caisse de survie", cles="free",
                   long_nom=False, erreur=False, plein=False):
    """etat : 'repos' (roue, bouton Ouvrir), 'roue' (la roue tourne), 'cle' (pas de clé), 'gain' (carte du gain)."""
    L = langue(code)
    fr = code.startswith("fr")
    e = Ecran(fen_w, fen_h, dessiner)
    W, H = e.w, e.h
    pw, ph, pad = C["PANNEAU_W"], C["PANNEAU_H"], C["PAD"]
    px, py = max(0, (W - pw) // 2), max(0, (H - ph) // 2)
    roueX, roueW, roueY = px + C["ROUE_MARGE"], pw - 2 * C["ROUE_MARGE"], py + C["ROUE_Y"]
    celluleW, gap, gh = C["CELLULE_W"], C["GRILLE_GAP"], C["GRILLE_H"]
    grilleW = C["GRILLE_COLS"] * celluleW + (C["GRILLE_COLS"] - 1) * gap
    grilleX, grilleY, basY = px + (pw - grilleW) // 2, py + C["GRILLE_Y"], py + C["SECTION_Y"]
    colX, colW = px + pad, pw - 2 * pad
    boutonW, boutonH = C["BOUTON_W"], C["BOUTON_H"]
    boutonX, boutonY = px + (pw - boutonW) // 2, py + C["BOUTON_Y"]
    RAR = C["RARETES"]
    clair = C["CLAIR"]

    # fond (le monde assombri) et panneau
    if dessiner:
        haut = Image.linear_gradient("L").resize(e.img.size)
        e.img = Image.merge("RGBA", [haut.point(lambda v: 40 + v // 10), haut.point(lambda v: 46 + v // 12),
                                     haut.point(lambda v: 38 + v // 30), Image.new("L", e.img.size, 255)])
        e.rect(0, 0, W, H, 0x000000, 150)
    panneau = (px, py, px + pw, py + ph)
    interieur = (px + pad - 1, py + 2, px + pw - pad + 1, py + ph - 2)
    e.rond(px, py, pw, ph, 0x0D0F14, 200, contour=(0xFFFFFF, 20))
    e.rect(px + 2, py + 1, px + pw - 2, py + 2, 0xFFFFFF, 30)

    # en-tête
    cx = px + pw / 2
    t = titre.upper()
    while len(t) > 1 and POL["TITRE"].largeur_espacee(t, C["ESP_TITRE"]) > pw - 2 * pad:
        t = t[:-1]
    bande_entete = (interieur[0], py + 2, interieur[2], roueY - 11)
    e.espace_centre("titre", POL["TITRE"], t, cx, py + C["TITRE_Y"], TEXTE, C["ESP_TITRE"], bande_entete)
    n = str(len(OBJETS))
    if cles == "free":
        sous = L["gui.heralizcrates.case.subtitle_free"] % n
    elif cles == 1:
        sous = L["gui.heralizcrates.case.subtitle_one"] % n
    else:
        sous = L["gui.heralizcrates.case.subtitle"] % (str(cles), n)
    e.centre("sous-titre", POL["SOUS"], sous, cx, py + C["SOUS_Y"], clair, bande_entete)

    # halo de la rareté de l'objet sous le repère, derrière la roue
    e.lueur(px + pw / 2, roueY + C["ROUE_H"] / 2, pw * C["HALO_L"], C["ROUE_H"] * C["HALO_H"],
            C["RARETES"][OBJETS[sous_repere % len(OBJETS)][1]], C["HALO_ALPHA"])

    # roue
    step, cw, cmax, roueH = C["STEP"], C["CW"], C["CARTE_MAX"], C["ROUE_H"]
    centre = roueX + roueW / 2
    cy = roueY + roueH / 2
    view = sous_repere * step + cw / 2
    cartes = []
    for i in range(sous_repere - 5, sous_repere + 6):
        d = i * step + cw / 2 - view
        if abs(d) > roueW / 2 + cmax:
            continue
        cartes.append((abs(d), i, d))
    for _, i, d in sorted(cartes, reverse=True):
        nomk, r, cnt, _ = OBJETS[i % len(OBJETS)]
        taille = cmax / (1 + 0.24 * abs(d) / step)
        w = round(taille)
        x, y = round(centre + d - taille / 2), round(cy - taille / 2)
        if x + w < roueX or x > roueX + roueW:
            continue
        milieu = max(0.0, 1 - abs(d) / step)
        fondu = min(1.0, max(0.25, 1 - (abs(d) - step * 1.2) / (roueW / 2 - step * 1.2)))
        if milieu > 0:
            e.lueur(x + w / 2, y + w / 2, w * 0.75, w * 0.75, RAR[r], 70 * milieu)
        e.rond(x, y, w, w, 0x1C2028, 255, contour=(RAR[r], 200 * milieu) if milieu > 0 else (0xFFFFFF, 18))
        e.lueur(x + w / 2, y + w * 0.47, w * 0.34, w * 0.3, RAR[r], 60 + 70 * milieu)
        e.rect(x + w / 2 - w * 0.34, y + w - 4, x + w / 2 + w * 0.34, y + w - 3, RAR[r])
        e.objet(x + w / 2, y + w * 0.47, w * 0.42, RAR[r])
        if 1 - fondu > 0:
            e.rond(x, y, w, w, 0x0D0F14, 215 * (1 - fondu))
        if cnt > 1:
            e.compte(f"compte roue {i}", cnt, x + w - C["COMPTE_RETRAIT"], y + C["COMPTE_RETRAIT"], fondu)
    e.tri([(round(centre) - 6, roueY - 10), (round(centre), roueY - 3), (round(centre) + 6, roueY - 10)], OR)

    # objet sous le repère
    nomk, r, cnt, _ = OBJETS[sous_repere % len(OBJETS)]
    nom = nom_objet(nomk, code)
    if long_nom:
        nom = nom_objet("Karambit | Doppler Black Pearl", code) + " (StatTrak™ édition limitée)"
    nom = nom + (f"  ×{cnt}" if cnt > 1 else "")
    nom = ellipsis(POL["NOM"], nom, pw - 2 * pad)
    bande_objet = (interieur[0], roueY + roueH + 1, interieur[2], py + C["CHANCES_Y"] - 1)
    e.centre("nom", POL["NOM"], nom, cx, py + C["NOM_Y"], TEXTE, bande_objet)
    rn = L["rarity.heralizcrates." + RARETES_ID[r]].upper()
    e.espace_centre("rareté", POL["SOUS_GRAS"], rn, cx, py + C["RARETE_Y"], RAR[r], C["ESP_RARETE"], bande_objet)

    # chances
    chances = [0.0] * 6
    for _, rr, _, ch in OBJETS:
        chances[rr] += ch
    total = sum(chances)
    y = py + C["CHANCES_Y"]
    x = colX
    for rr in range(6):
        if chances[rr] <= 0:
            continue
        w = chances[rr] / total * colW
        e.rect(x, y, max(x + 1, x + w - 1), y + 3, RAR[rr])
        x += w
    textes = [(L["rarity.heralizcrates." + RARETES_ID[rr]] + " " + pct(chances[rr], fr), rr) for rr in range(6) if chances[rr] > 0]
    pt, ec, esp_l, ligne_h = C["LEGENDE_POINT"], C["LEGENDE_ECART"], C["LEGENDE_ESPACE"], C["LEGENDE_LIGNE"]
    lw_ = lambda s: pt + ec + POL["TEXTE"].largeur(s)
    lignes, ligne, lw = [], [], 0
    for k, (s, rr) in enumerate(textes):
        w = lw_(s)
        if ligne and lw + esp_l + w > colW:
            lignes.append(ligne)
            ligne, lw = [], 0
        lw += (esp_l if ligne else 0) + w
        ligne.append(k)
    if ligne:
        lignes.append(ligne)
    ly = py + C["LEGENDE_Y"]
    bande_legende = (colX, y + 3, colX + colW, basY - 1)
    for l in lignes:
        largeur = -esp_l + sum(lw_(textes[k][0]) + esp_l for k in l)
        tx = round(px + pw / 2 - largeur / 2)
        for k in l:
            s, rr = textes[k]
            haut_point = ly + milieu_caps_java(C["T_TEXTE"], s) - pt / 2
            e.rond(round(tx), haut_point, pt, pt, RAR[rr], 255, rayon=2)
            caps = e.texte(f"légende {s}", POL["TEXTE"], s, tx + pt + ec, ly, clair, bande_legende)
            # Minecraft pose la pastille au point d'écran près (rasterisation)
            m_point = (round(haut_point * e.s) + pt * e.s / 2) / e.s
            e.pastilles.append((f"pastille {s}", m_point, (caps[0] + caps[1]) / 2))
            tx += lw_(s) + esp_l
        ly += ligne_h
    if len(lignes) > 2:
        e.textes.append(("légende : plus de deux lignes", (0, 0, 1, 1), (0, 0, 0, 0)))

    # contenu possible
    sect = L["gui.heralizcrates.case.possible"]
    e.espace("section", POL["TEXTE_GRAS"], sect, grilleX, basY, MUTED, C["ESP_SECTION"],
             (grilleX - 1, basY - 3, grilleX + grilleW, grilleY - 1))
    for i, (nomk, rr, cnt, _) in enumerate(OBJETS[:C["GRILLE_COLS"]]):
        x = grilleX + i * (celluleW + gap)
        e.rond(x, grilleY, celluleW, gh, 0xFFFFFF, 10)
        e.rect(x + 6, grilleY + gh - 3, x + celluleW - 6, grilleY + gh - 1, RAR[rr])
        e.objet(x + celluleW / 2, grilleY + gh / 2 - 1, C["GRILLE_ITEM"], RAR[rr])
        if cnt > 1:
            e.compte(f"compte grille {i}", cnt, x + celluleW - 2, grilleY + 2)

    # bouton et aide
    if etat == "roue":
        cle = "gui.heralizcrates.case.skip_space"
    elif etat == "cle":
        cle = "gui.heralizcrates.case.need_key"
    elif cles == "free":
        cle = "gui.heralizcrates.case.open"
    else:
        cle = "gui.heralizcrates.case.open_key"
    dore = etat != "cle"
    if dore:
        e.rond(boutonX, boutonY, boutonW, boutonH, 0xE6C063, 255, rayon=boutonH / 2)
    else:
        e.rond(boutonX, boutonY, boutonW, boutonH, 0xFFFFFF, 18, rayon=boutonH / 2)
    bt = L[cle].upper()
    e.espace_centre("bouton", POL["BOLD"], bt, boutonX + boutonW / 2, y_centre(boutonY, boutonH, C["T_BOUTON"], bt),
                    C["OR_TEXTE"] if dore else MUTED, C["ESP_BOUTON"],
                    (boutonX + boutonH / 2 - 2, boutonY + 3, boutonX + boutonW - boutonH / 2 + 2, boutonY + boutonH - 3),
                    boite=(boutonY, boutonY + boutonH))
    # rangée des touches (renderTouches) : pastilles à gauche, note à droite
    touches = []
    if etat == "roue":
        touches.append(("touche.espace", "skip"))
    elif etat != "cle":
        touches.append(("touche.espace", "open"))
    if plein:
        touches.append(("touche.molette", "action.contenu"))
    touches.append(("touche.echap", "close"))
    ty, th, tpad, tec, tesp = py + C["TOUCHES_Y"], C["TOUCHE_H"], C["TOUCHE_PAD"], C["TOUCHE_ECART"], C["TOUCHES_ESPACE"]
    bande_touches = (interieur[0], boutonY + boutonH + 1, interieur[2], py + ph - 2)
    x = px + pad
    for tk, ak in touches:
        touche = L["gui.heralizcrates.case." + tk].upper()
        action = L["gui.heralizcrates.case." + ak]
        tw = round(POL["TEXTE_GRAS"].largeur_espacee(touche, C["ESP_TOUCHE"])) + 2 * tpad
        tyt, tya = y_centre(ty, th, C["T_TEXTE"], touche), y_centre(ty, th, C["T_TEXTE"], action)
        xr = round(x)
        e.rond(xr, ty, tw, th, 0xFFFFFF, 14, contour=(0xFFFFFF, 70))
        e.espace(f"touche {touche}", POL["TEXTE_GRAS"], touche, xr + tpad, tyt, TEXTE, C["ESP_TOUCHE"],
                 (xr + 1, ty + 1, xr + tw - 1, ty + th - 1), boite=(ty, ty + th))
        ax = xr + tw + tec
        e.texte(f"action {action}", POL["TEXTE"], action, ax, tya, clair, bande_touches, boite=(ty, ty + th))
        x = ax + POL["TEXTE"].largeur(action) + tesp
    note, coul = None, MUTED
    if erreur:
        note, coul = L["gui.heralizcrates.case.error.all_emotes"], 0xFF6B6B
    elif etat == "cle":
        note = L["gui.heralizcrates.case.help.keys"]
    if note is not None:
        place = round(px + pw - pad - 2 - x)
        if place > 20:
            note = ellipsis(POL["TEXTE"], note, place)
            e.droite("note", POL["TEXTE"], note, px + pw - pad - 2, y_centre(ty, th, C["T_TEXTE"], note), coul, bande_touches)
        else:
            e.textes.append(("note : pas de place", (0, 0, 1, 1), (0, 0, 0, 0)))

    if etat == "gain":
        gain(e, L, code, cles)

    # contrôle : tout dans le panneau
    for et, b, permis in list(e.textes):
        if et.startswith("gain"):
            continue
        if not (b[0] >= panneau[0] and b[2] <= panneau[2] and b[1] >= panneau[1] and b[3] <= panneau[3]):
            e.textes.append((et + " hors du panneau", b, (0, 0, 0, 0)))
    if W < pw or H < ph:
        e.textes.append(("panneau plus grand que l'écran", (0, 0, 1, 1), (0, 0, 0, 0)))
    return e


def gain(e, L, code, cles):
    W, H = e.w, e.h
    gw, gh_ = C["GAIN_W"], C["GAIN_H"]
    gx, gy = (W - gw) // 2, max(0, (H - gh_) // 2)
    bw = (gw - 32 - 8) // 2
    bh = C["GAIN_BOUTON_H"]
    by = gy + gh_ - 16 - bh
    ex, fx = gx + 16, gx + 16 + bw + 8
    RAR = C["RARETES"]
    nomk, r, cnt, _ = OBJETS[10]
    e.rect(0, 0, W, H, 0x05060A, 165)
    e.rond(gx, gy, gw, gh_, 0x0D0F14, 235, contour=(0xFFFFFF, 23))
    cx = W // 2
    carte = C["GAIN_CARTE"]
    cxx, cyy = cx - carte // 2, gy + C["GAIN_CARTE_Y"]
    e.espace_centre("gain titre", POL["TEXTE_GRAS"], L["gui.heralizcrates.case.won"], cx, gy + C["GAIN_TITRE_Y"], MUTED,
                    C["ESP_SECTION"], (gx + 16, gy + 3, gx + gw - 16, cyy - 2))
    e.lueur(cx, cyy + carte / 2, carte * 0.9, carte * 0.9, RAR[r], 60)
    e.rond(cxx, cyy, carte, carte, 0xFFFFFF, 12, contour=(RAR[r], 220))
    item_cy = cyy + carte * 0.42
    e.objet(cx, item_cy, C["GAIN_ITEM"], RAR[r])
    e.rect(cxx + 6, cyy + carte - 3, cxx + carte - 6, cyy + carte - 1, RAR[r])
    rn = L["rarity.heralizcrates." + RARETES_ID[r]].upper()
    e.espace_centre("gain rareté", POL["TEXTE_GRAS"], rn, cx, cyy + carte - 20, RAR[r], C["ESP_GAIN"],
                    (cxx + 4, item_cy + C["GAIN_ITEM"] / 2 + 1, cxx + carte - 4, cyy + carte - 4))
    nom = ellipsis(POL["NOM"], nom_objet(nomk, code), gw - 32)
    e.centre("gain nom", POL["NOM"], nom, cx, gy + C["GAIN_NOM_Y"], TEXTE, (gx + 12, cyy + carte + 1, gx + gw - 12, by - 1))
    encore = True
    e.rond(ex, by, bw, bh, 0xE6C063, 255)
    e.rond(fx, by, bw, bh, 0xFFFFFF, 15, contour=(0xFFFFFF, 20))
    t = L["gui.heralizcrates.case.again" if cles == "free" else "gui.heralizcrates.case.again_key"].upper()
    e.espace_centre("gain encore", POL["TEXTE_GRAS"], t, ex + bw / 2, y_centre(by, bh, C["T_TEXTE"], t), C["OR_TEXTE"], C["ESP_GAIN"],
                    (ex + 3, by + 3, ex + bw - 3, by + bh - 3), boite=(by, by + bh))
    f = L["gui.heralizcrates.case.close"].upper()
    e.espace_centre("gain fermer", POL["TEXTE_GRAS"], f, fx + bw / 2, y_centre(by, bh, C["T_TEXTE"], f), MUTED, C["ESP_GAIN"],
                    (fx + 3, by + 3, fx + bw - 3, by + bh - 3), boite=(by, by + bh))
    for et, b, permis in list(e.textes):
        if et.startswith("gain") and not (b[0] >= gx and b[2] <= gx + gw and b[1] >= gy and b[3] <= gy + gh_):
            e.textes.append((et + " hors de la carte du gain", b, (0, 0, 0, 0)))
    if gh_ > H:
        e.textes.append(("carte du gain plus haute que l'écran", (0, 0, 1, 1), (0, 0, 0, 0)))


# ----------------------------------------------------------------------------------------------- contrôle
def debordements(e, contexte):
    fautes = []
    tol = 0.01
    gain_ = any(t[0].startswith("gain") for t in e.textes)
    for et, b, p in e.textes:
        if b[0] < p[0] - tol or b[1] < p[1] - tol or b[2] > p[2] + tol or b[3] > p[3] + tol:
            fautes.append(f"{contexte} : « {et} » sort de sa boîte (encre {tuple(round(v, 2) for v in b)}, boîte {tuple(round(v, 2) for v in p)})")
    for i in range(len(e.textes)):
        for j in range(i + 1, len(e.textes)):
            a, b = e.textes[i], e.textes[j]
            if gain_ and a[0].startswith("gain") != b[0].startswith("gain"):
                continue  # la carte du gain passe par-dessus le panneau
            A, B = a[1], b[1]
            if A[0] < B[2] and B[0] < A[2] and A[1] < B[3] and B[1] < A[3]:
                fautes.append(f"{contexte} : « {a[0]} » touche « {b[0]} »")
    px1 = 1.0 / e.s + 1e-6
    for et, ct, cb, bt, bb in e.centres:
        haut, bas = ct - bt, bb - cb
        if abs(haut - bas) > px1:
            fautes.append(f"{contexte} : « {et} » pas centré (écart haut {haut:.2f}, bas {bas:.2f} unités, échelle {e.s})")
    for et, mp, mc in e.pastilles:
        if abs(mp - mc) > px1:
            fautes.append(f"{contexte} : « {et} » pastille à {mp - mc:+.2f} du milieu des capitales (échelle {e.s})")
    return fautes


FENETRES = [(1920, 1080), (1920, 1057), (1280, 720), (1280, 697), (1366, 768), (1600, 900), (2560, 1440), (1024, 768), (854, 480)]
ETATS = ["repos", "roue", "cle", "gain"]


def controler():
    fautes = []
    modele = "jeu"
    if True:
        for fw, fh in FENETRES:
            for code in ("fr_fr", "en_us", "ru_ru"):
                for etat in ETATS:
                    for cles in ("free", 1, 12):
                        for repere in (0, 1, 6, 10):
                            for long_nom in (False, True):
                                e = dessiner_ecran(fw, fh, code, etat, dessiner=False, sous_repere=repere, cles=cles,
                                                   long_nom=long_nom, titre="Caisse des emotes et des vœux de la saison",
                                                   plein=long_nom)
                                fautes += debordements(e, f"{modele} {fw}x{fh} {code} {etat} clés={cles} repère={repere}")
                for et in ("repos", "cle"):
                    e = dessiner_ecran(fw, fh, code, et, dessiner=False, erreur=True, plein=True)
                    fautes += debordements(e, f"{modele} {fw}x{fh} {code} {et} erreur")
    return sorted(set(fautes))


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--sortie", type=Path, default=SORTIE)
    args = ap.parse_args()
    args.sortie.mkdir(parents=True, exist_ok=True)
    rendus = [((1920, 1080), "fr_fr", "repos", 12, "1920x1080_roue.png"), ((1920, 1080), "fr_fr", "gain", 12, "1920x1080_gain.png"),
              ((1280, 720), "fr_fr", "repos", "free", "1280x720_roue.png"), ((1280, 720), "fr_fr", "gain", "free", "1280x720_gain.png"),
              ((1280, 720), "ru_ru", "cle", 0, "1280x720_roue_ru.png"), ((1920, 1080), "fr_fr", "cle", 0, "1920x1080_sans_cle.png")]
    for (fw, fh), code, etat, cles, nom in rendus:
        e = dessiner_ecran(fw, fh, code, etat, sous_repere=0, cles=cles, plein=True)
        e.img.convert("RGB").save(args.sortie / nom, optimize=True)
        print(f"{args.sortie / nom} (échelle {e.s}, canevas {e.w} x {e.h})")
    fautes = controler()
    print(f"débordements : {len(fautes)}")
    for f in fautes[:60]:
        print("  " + f)
    return 1 if fautes else 0


if __name__ == "__main__":
    sys.exit(main())
