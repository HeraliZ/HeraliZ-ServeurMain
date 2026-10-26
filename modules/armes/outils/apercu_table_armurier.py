"""Aperçu de la Table d'armurier hors du jeu, et contrôles « rien ne déborde » et « texte centré » (Tactique 2.35.1).

    python modules/armes/outils/apercu_table_armurier.py              images dans maquettes/table_armurier/
    python modules/armes/outils/apercu_table_armurier.py --sortie D   images ailleurs
    python modules/armes/outils/apercu_table_armurier.py --toutes     enregistre aussi les cas seulement contrôlés

Refait la mise en page de `camo/client/atelier/TableArmurier.java` avec les MÊMES nombres (constantes lues dans le
Java) et les mêmes polices (TTF du mod, tailles et décalages des fichiers `font/*.json`), comme Minecraft 1.20.1 :
- avancée d'une lettre = avance TTF × taille / (ascendante − descendante) ; Font.width = somme arrondie au-dessus ;
  capitales espacées : avancées exactes (StringSplitter) ; espace = 3 ;
- ligne de base d'une lettre TTF = point de dessin + ascendante + 2 × décalage du JSON − 3 (TrueTypeGlyphProvider : le
  décalage compte dans la boîte stb et dans bearingY = ascendante − haut + shift ; BakedGlyph : up − 3) ; l'approche
  gauche d'une lettre compte aussi deux fois (bearingX) ; le milieu des capitales qui en découle doit être celui
  de `Ui.milieu` (vérifié ici) ;
- point de dessin posé sur le point d'écran le plus proche (`Ui.placer`).

L'écran est dessiné en français, anglais et russe, aux fenêtres usuelles, à l'échelle que choisit `kit/Echelle` (et à
l'échelle 2 forcée en 1080p). Deux contrôles, sur l'encre réellement dessinée (chaque texte rendu à part) :
1. débordement : aucun texte ne sort de sa case ;
2. centrage : pour chaque texte centré dans une case (pastilles, bouton, touche « Échap », champ de recherche, filtres,
   « Fermer », lignes des catégories et des matériaux, bloc chiffre + légende des tuiles, état des cartes à côté de leur
   point), |écart du haut − écart du bas| ≤ 1 point d'écran, mesuré sur la bande des capitales (encre d'un « H » posé
   comme le texte : les jambages de « p » ou « g » ne comptent pas, comme pour l'œil), et, pour les textes centrés en
   largeur, |écart gauche − écart droit| ≤ 1 point d'écran sur l'encre du texte lui-même.
Les listes doivent être vides (code de sortie 1 sinon). Les objets (icônes, modèle 3D) sont des carrés gris.
Besoin : Pillow et fontTools (`pip install fonttools`).
"""
import json
import math
import re
import sys
import unicodedata
from pathlib import Path

from fontTools.ttLib import TTFont
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[3]
MOD = ROOT / "modules/armes/src/client"
JAVA = MOD / "java/fr/heralisz/camo/client/atelier/TableArmurier.java"
UI_JAVA = MOD / "java/fr/heralisz/camo/client/kit/Ui.java"
ASSETS = MOD / "resources/assets/heralisz_camo"
COMPOSANTS = ROOT / "modules/equipement/src/client/resources/assets/heraliz_composants/lang"
SORTIE = ROOT / "maquettes/table_armurier"
TOLERANCE = 1.0  # points d'écran

# ------------------------------------------------------------------ constantes lues dans le Java
SRC = JAVA.read_text(encoding="utf-8")
K = {}
for ligne in SRC.splitlines():
    if "static final int" in ligne or "static final float" in ligne:
        for nom, val in re.findall(r"(\b[A-Z][A-Z0-9_]*)\s*=\s*(-?\d+(?:\.\d+)?)f?", ligne):
            K[nom] = float(val) if "." in val else int(val)


def table_java(methode):
    """Les valeurs d'un `switch` de Ui.java (milieu, capitales) : {police: valeur}."""
    corps = UI_JAVA.read_text(encoding="utf-8").split(f"public static float {methode}(ResourceLocation f)", 1)[1].split("default ->", 1)[0]
    out = {}
    for noms, val in re.findall(r"case ([^-]+?) -> (-?\d+(?:\.\d+)?)f;", corps):
        for n in re.findall(r'"([a-z_]+)"', noms):
            out[n] = float(val)
    return out


MILIEU_JAVA, CAPITALES_JAVA = table_java("milieu"), table_java("capitales")


def arrondi(v):
    """Math.round de Java (moitié vers le haut)."""
    return math.floor(v + 0.5)


# ------------------------------------------------------------------ polices (comme Minecraft)
class Police:
    def __init__(self, nom):
        self.nom = nom
        d = json.loads((ASSETS / "font" / f"{nom}.json").read_text(encoding="utf-8"))
        self.espaces = {}
        self.ttf = []
        for p in d["providers"]:
            if p["type"] == "space":
                self.espaces.update(p["advances"])
            elif p["type"] == "ttf":
                fichier = ASSETS / "font" / p["file"].split(":")[1]
                t = TTFont(fichier)
                hh = t["hhea"].ascent - t["hhea"].descent
                self.ttf.append(dict(fichier=str(fichier), cmap=t.getBestCmap(), hmtx=t["hmtx"].metrics, hh=hh,
                                     upem=t["head"].unitsPerEm, asc=t["hhea"].ascent, cap=t["OS/2"].sCapHeight,
                                     taille=p["size"], decal=p.get("shift", [0, 0])))
        self.pil = {}

    def source(self, ch):
        for f in self.ttf:
            if ord(ch) in f["cmap"]:
                return f
        return None

    def avance(self, ch):
        if ch in self.espaces:
            return float(self.espaces[ch])
        f = self.source(ch)
        if f is None:
            return 6.0  # police de Minecraft (repli)
        return f["hmtx"][f["cmap"][ord(ch)]][0] * f["taille"] / f["hh"]

    def largeur(self, s):
        """Font.width : somme des avancées, arrondie au-dessus."""
        return math.ceil(sum(self.avance(c) for c in s) - 1e-6)

    def exacte(self, s):
        """StringSplitter.stringWidth : somme exacte."""
        return sum(self.avance(c) for c in s)

    @staticmethod
    def base(f):
        """Ligne de base sous le point de dessin (Minecraft : ascendante + 2 × décalage − 3)."""
        return f["asc"] * f["taille"] / f["hh"] + 2 * f["decal"][1] - 3  # le décalage Y compte deux fois (boîte stb + bearingY)

    def capitales(self):
        f = self.ttf[0]
        return f["cap"] * f["taille"] / f["hh"]

    def milieu(self):
        return self.base(self.ttf[0]) - self.capitales() / 2

    def image(self, f, echelle):
        cle = (f["fichier"], echelle)
        if cle not in self.pil:
            self.pil[cle] = ImageFont.truetype(f["fichier"], max(1, round(f["taille"] * f["upem"] / f["hh"] * echelle)))
        return self.pil[cle]


P = {n: Police(n) for n in ("ui", "ui_bold", "ui_small", "ui_gros", "ui_nom", "ui_mini")}
FONT, BOLD, SMALL, GROS, NOM, MINI = (P[n] for n in ("ui", "ui_bold", "ui_small", "ui_gros", "ui_nom", "ui_mini"))


def caps_largeur(s, pol, esp):
    """Ui.spacedWidth (avancée exacte de chaque lettre)."""
    s = s.upper()
    if not s:
        return 0.0
    return max(0.0, sum(pol.avance(c) + esp for c in s) - esp)


def ellipsis(s, pol, maxw):
    if pol.largeur(s) <= maxw:
        return s
    dw = pol.largeur("…")
    sb = ""
    for c in s:
        if pol.largeur(sb + c) + dw > maxw:
            break
        sb += c
    return sb.strip() + "…"


def couper_espace(s, pol, esp, maxw):
    if caps_largeur(s, pol, esp) <= maxw:
        return s
    t = s
    while len(t) > 1 and caps_largeur(t + "…", pol, esp) > maxw:
        t = t[:-1]
    return t.strip() + "…"


def ajuster(s, pol, esp, maxw, reduire):
    espaces = [esp, esp / 2, 0.0]
    for e in espaces:
        if caps_largeur(s, pol, e) <= maxw:
            return s, pol, e
    if reduire and pol is not MINI:
        for e in espaces:
            if caps_largeur(s, MINI, e) <= maxw:
                return s, MINI, e
        return couper_espace(s, MINI, 0.0, maxw), MINI, 0.0
    return couper_espace(s, pol, 0.0, maxw), pol, 0.0


def largeur_chip(s):
    return int(caps_largeur(s, SMALL, K["ESP_CHIP"])) + 10


# ------------------------------------------------------------------ tri (même règle que TableArmurier.trier)
ROMAINS = {"I": 1, "II": 2, "III": 3, "IV": 4, "V": 5, "VI": 6, "VII": 7, "VIII": 8, "IX": 9, "X": 10}


def primaire(s):
    return "".join(c for c in unicodedata.normalize("NFD", s.casefold()) if unicodedata.category(c) != "Mn")


def cle_tri(nom):
    m = re.match(r"^(.*\S)\s+(I{1,3}|IV|V|VI{1,3}|IX|X)$", nom.strip())
    if m:
        return primaire(m.group(1)), ROMAINS[m.group(2)], nom
    return primaire(nom), 0, nom


# ------------------------------------------------------------------ dessin et contrôles
class Ecran:
    def __init__(self, lw, lh, s):
        self.s = s
        self.lw, self.lh = lw, lh
        self.w, self.h = lw // s, lh // s
        self.img = Image.new("RGB", (lw, lh), (24, 27, 33))
        self.d = ImageDraw.Draw(self.img, "RGBA")
        self.debordements = []
        self.decentres = []
        self.cases = []

    # -- formes
    def rect(self, x, y, w, h, rgba, contour=None):
        s = self.s
        self.d.rectangle([round(x * s), round(y * s), round((x + w) * s) - 1, round((y + h) * s) - 1], fill=rgba, outline=contour)

    def ligne_h(self, x0, x1, y, rgba):
        self.d.rectangle([x0 * self.s, y * self.s, x1 * self.s - 1, (y + 1) * self.s - 1], fill=rgba)

    def placer(self, v):
        return arrondi(v * self.s) / self.s

    # -- lettres
    def _glyphes(self, s, pol, x, y, rgba, esp=None, cible=None, facteur=1, origine=(0, 0)):
        """Lettres posées comme Minecraft (point de dessin x, y) ; esp : capitales espacées (chaque lettre posée au point
        d'écran). Dessine sur l'image, ou sur `cible` (masque agrandi `facteur` fois, coin `origine` en points d'écran)."""
        sc = self.s
        d = self.d if cible is None else ImageDraw.Draw(cible)
        cx = x
        for c in s:
            f = pol.source(c)
            # capitales espacées : Ui.spaced recule chaque lettre de son approche gauche, puis la pose au point d'écran
            px = self.placer(cx - approche(c, pol)) if esp is not None else cx
            if f is not None and c not in pol.espaces:
                base = y + Police.base(f)
                # Minecraft compte deux fois l'approche gauche (bearingX) ; Pillow une fois : on ajoute l'autre
                lsb = f["hmtx"][f["cmap"][ord(c)]][1] * f["taille"] / f["hh"]
                pos = (((px + lsb) * sc - origine[0]) * facteur, (base * sc - origine[1]) * facteur)
                d.text(pos, c, font=pol.image(f, sc * facteur), fill=rgba if cible is None else 255, anchor="ls")
            cx += pol.avance(c) + (esp if esp is not None else 0.0)
        return cx - x

    def encre(self, s, pol, x, y, esp=None):
        """Encre réelle du texte, en points d'écran au dixième près : lettres tracées 8 fois plus grandes (sans l'effet
        du « hinting » de FreeType, comme stb_truetype de Minecraft, qui trace à 8 fois aussi), puis réduites."""
        k = 8
        sc = self.s
        w = (caps_largeur(s, pol, esp) if esp is not None else pol.exacte(s)) + 8
        ox, oy = math.floor((x - 4) * sc), math.floor((y - 6) * sc)
        lw, lh = math.ceil((w + 8) * sc), math.ceil(30 * sc)
        m = Image.new("L", (lw * k, lh * k), 0)
        self._glyphes(s, pol, x, y, None, esp, m, k, (ox, oy))
        b = m.getbbox()
        if b is None:
            return None
        return (ox + b[0] / k, oy + b[1] / k, ox + b[2] / k, oy + b[3] / k)

    def texte(self, s, pol, x, cy, rgba, case, nom, centre_h=False, centre_v=True, espace=None, centre_sur=None):
        """Texte (espace=None) ou capitales espacées, bord gauche en x, milieu des capitales sur cy (Ui.gauche /
        Ui.spacedMilieu) ; contrôlé contre sa case."""
        t = s.upper() if espace is not None else s
        y = self.placer(cy - MILIEU_JAVA[pol.nom])
        xx = x if espace is not None else self.placer(x)
        self._glyphes(t, pol, xx, y, rgba, espace)
        if case is not None:
            self.controle(nom, t, pol, xx, y, espace, case, centre_h, centre_v, centre_sur or case)
        return caps_largeur(t, pol, espace) if espace is not None else pol.exacte(t)

    def controle(self, nom, t, pol, x, y, espace, case, centre_h, centre_v, centre_sur):
        sc = self.s
        bx, by, bw, bh = (v * sc for v in case)
        e = self.encre(t, pol, x, y, espace)
        if e is None:
            return
        if e[0] < bx - 0.5 or e[2] > bx + bw + 0.5 or e[1] < by - 0.5 or e[3] > by + bh + 0.5:
            self.debordements.append(f"{nom} « {t} » : encre {e} hors de la case {tuple(round(v) for v in (bx, by, bx + bw, by + bh))}")
        bx, by, bw, bh = (v * sc for v in centre_sur)
        if centre_h:
            g, d = e[0] - bx, bx + bw - e[2]
            if abs(g - d) > TOLERANCE:
                self.decentres.append(f"{nom} « {t} » en largeur : gauche {g:.1f}, droite {d:.1f} points d'écran")
        if centre_v:
            h = self.encre("H", pol, x, y)
            ha, ba = h[1] - by, by + bh - h[3]
            if abs(ha - ba) > TOLERANCE:
                self.decentres.append(f"{nom} « {t} » en hauteur : haut {ha:.1f}, bas {ba:.1f} points d'écran")

    def bloc_vertical(self, nom, morceaux, case):
        """Plusieurs lignes centrées ensemble (chiffre + légende) : bande des capitales de la 1re à la dernière."""
        sc = self.s
        by, bh = case[1] * sc, case[3] * sc
        haut = self.encre("H", *morceaux[0])[1]
        bas = self.encre("H", *morceaux[-1])[3]
        ha, ba = haut - by, by + bh - bas
        if abs(ha - ba) > TOLERANCE:
            self.decentres.append(f"{nom} en hauteur : haut {ha:.1f}, bas {ba:.1f} points d'écran")

    def case(self, nom, x, y, w, h):
        self.cases.append((nom, x, y, w, h))


def echelle_voulue(lw, lh):
    for s in range(8, 1, -1):
        if lw // s >= 640 - 40 and lh // s >= 360 - 20:
            return s
    return 1


def lang(code):
    return json.loads((ASSETS / "lang" / f"{code}.json").read_text(encoding="utf-8"))


def noms_composants(code):
    d = json.loads((COMPOSANTS / f"{code}.json").read_text(encoding="utf-8"))
    return [v for k, v in d.items() if re.fullmatch(r"item\.heraliz_composants\.[a-z0-9_]+", k)]


CATEGORIES = {
    "fr_fr": ["Munitions", "Pistolets", "Mitraillettes", "Fusils d'assaut", "Pompes", "Soins", "Viseurs", "Accessoires", "Explosifs", "Composants", "Divers"],
    "en_us": ["Ammunition", "Pistols", "SMGs", "Rifles", "Shotguns", "Medical", "Sights", "Attachments", "Explosives", "Components", "Misc"],
    "ru_ru": ["Боеприпасы", "Пистолеты", "Пистолеты-пулемёты", "Винтовки", "Дробовики", "Медицина", "Прицелы", "Обвесы", "Взрывчатка", "Компоненты", "Разное"],
}
ARME = {
    "fr_fr": ("Thunderbird .50 édition Désert", "Balle de 9mm", "Kit de pièces III"),
    "en_us": ("Thunderbird .50 Desert edition", "9mm round", "Parts kit III"),
    "ru_ru": ("Thunderbird .50 пустынная версия", "Патрон 9 мм", "Набор деталей III"),
}
GRIS, CLAIR, TEXTE, PALE, VERT, ROUGE = (143, 149, 160, 255), (190, 194, 200, 255), (236, 238, 242, 255), (90, 95, 105, 255), (95, 208, 138, 255), (255, 107, 107, 255)


RETOUCHES = [0, 1, -1, 2, -2, 3, -3]
DECALAGES = [0, 1, -1, 2, -2, 3, -3, 4, -4, 5, -5, 6, -6, 7, -7, 8, -8]


def x_caps_centre(e, t, pol, esp, cx):
    """Ui.spacedCentre : (départ, espacement) qui centrent l'ENCRE sur cx une fois chaque lettre posée au point
    d'écran : petits décalages (huitième de point d'écran) et retouches de l'espacement (0,01 par intervalle)."""
    t = t.upper()
    n = len(t)
    if n == 0:
        return cx, esp
    w = caps_largeur(t, pol, esp)
    gl, dr = encre_px(t, pol, True)
    av = pol.avance(t[-1])
    lf, ll = approche(t[0], pol), approche(t[-1], pol)
    meilleur, ecart = (cx - (w + gl - dr) / 2, esp), float("inf")
    for j in RETOUCHES:
        wj = w + (n - 1) * j * 0.01
        dern = wj - av
        x0 = cx - (wj + gl - dr) / 2
        for i in DECALAGES:
            xc = x0 + i / (8 * e.s)
            milieu = (e.placer(xc - lf) + lf + gl + e.placer(xc + dern - ll) + ll + av - dr) / 2
            d = abs(milieu - cx)
            if d < ecart - 1e-4:
                ecart, meilleur = d, (xc, esp + j * 0.01)
    return meilleur


def chip(e, texte, x, y, nom):
    """Ui.chip : largeur int(capitales) + 10, hauteur 11, texte centré (largeur exacte, milieu des capitales)."""
    w = largeur_chip(texte)
    e.rect(x, y, w, 11, (143, 149, 160, 34), (143, 149, 160, 90))
    xc, esp = x_caps_centre(e, texte, SMALL, K["ESP_CHIP"], x + w / 2)
    e.texte(texte, SMALL, xc, y + 11 / 2, CLAIR, (x, y, w, 11), nom, centre_h=True, espace=esp)
    e.case(nom + " " + texte, x, y, w, 11)
    return w


def dessiner(code, lw, lh, s, manque, sortie, garder=True):
    L = lang(code)

    def tr(cle, *args):
        t = L["gui.heralisz_camo.table." + cle]
        for a in args:
            t = t.replace("%s", str(a), 1)
        return t

    e = Ecran(lw, lh, s)
    W, H = e.w, e.h
    pw, ph = min(K["PANNEAU_L"], W - 24), min(K["PANNEAU_H"], H - 20)
    px, py = (W - pw) // 2, (H - ph) // 2
    e.rect(px, py, pw, ph, (13, 15, 20, 230), (255, 255, 255, 26))
    cx = px + K["GRILLE_X"]
    cw = K["COLONNES"] * K["CARTE_W"] + (K["COLONNES"] - 1) * K["ECART"]
    # ---- en-tête (TableArmurier.construire)
    fermer, echap = L["gui.heralisz_camo.atelier.fermer"], L["gui.heralisz_camo.atelier.echap"]
    fw = SMALL.largeur(echap) + 8 + 5 + FONT.largeur(fermer)
    fx = px + pw - K["MARGE"] - fw
    titre_w = math.ceil(max(caps_largeur(tr("titre_petit"), SMALL, K["ESP_TITRE_PETIT"]), caps_largeur(tr("titre"), GROS, K["ESP_TITRE"])))
    sx = max(cx, px + K["MARGE"] + titre_w + K["AVANT_FERMER"])
    en_main, dispo = tr("en_main"), tr("dispo")
    case_t = K["CASE"] + K["CASE_TEXTE"]
    ew, dw2 = case_t + FONT.largeur(en_main), case_t + FONT.largeur(dispo)
    filtres_w = ew + K["FILTRE_ECART"] + dw2
    une_ligne = fx - K["AVANT_FERMER"] - filtres_w - K["APRES_RECHERCHE"] - sx
    filtres = []
    if une_ligne >= K["RECHERCHE_MIN"]:
        entete = K["ENTETE_1"]
        rw = min(une_ligne, cw)
        fx0 = sx + rw + K["APRES_RECHERCHE"]
        filtres.append((fx0, py + K["RECHERCHE_Y"], K["RECHERCHE_H"], en_main, 10 ** 6, True))
        filtres.append((fx0 + ew + K["FILTRE_ECART"], py + K["RECHERCHE_Y"], K["RECHERCHE_H"], dispo, 10 ** 6, False))
    else:
        entete = K["ENTETE_2"]
        rw = max(60, min(cx + cw, fx - K["AVANT_FERMER"]) - sx)
        place = px + pw - K["MARGE"] - sx
        m1 = m2 = 10 ** 6
        if filtres_w > place:
            m1 = m2 = (place - K["FILTRE_ECART"]) // 2 - case_t
        v1 = ellipsis(en_main, FONT, m1)
        filtres.append((sx, py + K["LIGNE2_Y"], K["LIGNE2_H"], en_main, m1, True))
        filtres.append((sx + case_t + FONT.largeur(v1) + K["FILTRE_ECART"], py + K["LIGNE2_Y"], K["LIGNE2_H"], dispo, m2, False))
    # titre (à gauche, sans case : contrôlé seulement contre la place du bloc)
    bloc_titre = (px + K["MARGE"], py + 4, titre_w + 1, 30)
    e.texte(tr("titre_petit"), SMALL, px + K["MARGE"], py + K["TITRE_PETIT_CY"], GRIS, bloc_titre, "titre petit", centre_v=False, espace=K["ESP_TITRE_PETIT"])
    e.texte(tr("titre"), GROS, px + K["MARGE"], py + K["TITRE_CY"], TEXTE, bloc_titre, "titre", centre_v=False, espace=K["ESP_TITRE"])
    e.case("titre", *bloc_titre)
    # recherche (HzField : texte à x + 4, milieu des capitales au milieu du champ)
    ry = py + K["RECHERCHE_Y"]
    rh = K["RECHERCHE_H"]
    e.rect(sx, ry, rw, rh, (0, 0, 0, 80))
    e.ligne_h(sx, sx + rw, ry + rh - 1, (255, 255, 255, 60))
    e.texte(ellipsis(tr("recherche"), FONT, rw - 8), FONT, sx + 4, ry + rh / 2, PALE, (sx, ry, rw, rh), "invite de recherche")
    e.case("recherche", sx, ry, rw, rh)
    for (zx, zy, zh, texte, mx, coche) in filtres:
        vu = ellipsis(texte, FONT, mx)
        zw = case_t + FONT.largeur(vu)
        cyz = zy + zh / 2
        by = arrondi(cyz - K["CASE"] / 2)
        e.rect(zx, by, K["CASE"], K["CASE"], (111, 168, 255, 215) if coche else (255, 255, 255, 30))
        e.texte(vu, FONT, zx + case_t, by + K["CASE"] / 2, GRIS, (zx, zy, zw, zh), "filtre", centre_sur=(zx, by, zw, K["CASE"]))  # centré sur la case à cocher
        e.case("filtre " + texte, zx, zy, zw, zh)
    # fermer : touche « Échap » (largeur Font.width + 8, hauteur 11), libellé centré dedans ; « Fermer » sur le même milieu
    fy = py + K["FERMER_Y"]
    kw = SMALL.largeur(echap) + 8
    e.rect(fx, fy, kw, 11, (255, 255, 255, 30), (255, 255, 255, 60))
    gl, dr = encre_px(echap, SMALL)
    e.texte(echap, SMALL, fx + kw / 2 - (SMALL.exacte(echap) + gl - dr) / 2, fy + 5.5, TEXTE, (fx, fy, kw, 11), "touche Échap", centre_h=True)
    e.texte(fermer, FONT, fx + kw + 5, fy + 5.5, GRIS, (fx + kw, fy, fw - kw, 11), "Fermer")
    e.case("fermer", fx, fy, fw, 11)
    e.ligne_h(px + 8, px + pw - 8, py + entete, (255, 255, 255, 34))
    # ---- catégories
    gx, gy, gw = px + K["CAT_X"], py + entete + 8, K["CAT_W"]
    cats = CATEGORIES[code]
    rang = max(12, min(15, (py + ph - 10 - gy) // len(cats)))
    y = gy
    for i, c in enumerate(cats):
        if y + rang - 1 > py + ph - 8:
            break
        h = rang - 1
        if i == len(cats) - 1:
            e.rect(gx, y, gw, h, (111, 168, 255, 34))
            e.rect(gx, y + 2, 2, h - 4, (111, 168, 255, 255))
        e.rect(gx + 5, y + (h - 10) / 2, 10, 10, (120, 120, 120, 255))
        n = str(17 + i * 9)
        nw = SMALL.largeur(n)
        nom = ellipsis(c, FONT, gw - 19 - 5 - nw - 4)
        e.texte(nom, FONT, gx + 19, y + h / 2, (200, 204, 210, 255), (gx + 17, y, gw - 5 - nw - 4 - 17, h), "catégorie")
        e.texte(n, SMALL, gx + gw - 5 - SMALL.exacte(n), y + h / 2, PALE, (gx, y, gw, h), "nombre de la catégorie")
        y += rang
    # ---- cartes (catégorie Composants, triée)
    cy = py + entete + 22
    noms = noms_composants(code)
    noms.sort(key=cle_tri)
    rangs_vis = max(1, (py + ph - 10 - cy + K["ECART"]) // (K["CARTE_H"] + K["ECART"]))
    sy = py + entete + 10  # Ui.section : point de dessin (filet au milieu des capitales)
    nb = str(len(noms))
    nbw = math.ceil(caps_largeur(nb, SMALL, 0.8))
    titre_grille = couper_espace(cats[-1], SMALL, K["ESP_SECTION"], cw - nbw - 24)
    tgw = e.texte(titre_grille, SMALL, cx, sy + MILIEU_JAVA["ui_small"], GRIS, (cx, sy - 2, cw - nbw - 8, 10), "titre de la grille", centre_v=False, espace=K["ESP_SECTION"])
    e.ligne_h(cx + int(tgw) + 8, cx + cw - nbw - 8, sy + arrondi(MILIEU_JAVA["ui_small"]), (255, 255, 255, 40))
    e.texte(nb, SMALL, cx + cw - caps_largeur(nb, SMALL, 0.8), sy + MILIEU_JAVA["ui_small"], PALE, (cx + cw - nbw - 4, sy - 2, nbw + 4, 10), "nombre d'objets", centre_v=False, espace=0.8)
    for k in range(min(len(noms), rangs_vis * K["COLONNES"])):
        x0 = cx + (k % K["COLONNES"]) * (K["CARTE_W"] + K["ECART"])
        y0 = cy + (k // K["COLONNES"]) * (K["CARTE_H"] + K["ECART"])
        w, h = K["CARTE_W"], K["CARTE_H"]
        e.rect(x0, y0, w, h, (255, 255, 255, 12), (255, 255, 255, 40) if k else (111, 168, 255, 230))
        e.rect(x0 + w // 2 - 16, y0 + 4, 32, 32, (110, 110, 110, 255))
        nom = ellipsis(noms[k], FONT, w - 8)
        e.texte(nom, FONT, x0 + 4, y0 + K["CARTE_NOM_CY"], TEXTE, (x0 + 2, y0 + 36, w - 4, 15), "nom de carte", centre_v=False)
        ok = k % 3 != 2
        st = tr("fabricable") if ok else tr("manque", 2)
        pt = y0 + K["CARTE_ETAT_CY"] - 3
        e.rect(x0 + 4, pt, 5, 5, VERT if ok else PALE)
        t, pol, esp = ajuster(st, SMALL, K["ESP_CHIP"], w - 11 - 4, True)
        e.texte(t, pol, x0 + 11, pt + 2.5, VERT if ok else GRIS, (x0 + 11, y0 + 49, w - 11 - 3, h - 49), "état de carte (centré sur son point)", espace=esp,
                centre_sur=(x0 + 11, pt, w - 14, 5))
    # ---- détail
    dx = cx + cw + 8
    dy = py + entete + 8
    dw = px + pw - 8 - dx
    dh = py + ph - 8 - dy
    e.rect(dx, dy, dw, dh, (255, 255, 255, 10), (255, 255, 255, 26))
    x, w = dx + K["DETAIL_PAD"], dw - 2 * K["DETAIL_PAD"]
    modele_h = max(56, min(74, dh - 190))
    e.rect(dx + 4 + (dw - 8) // 2 - 40, dy + 4 + 6, 80, modele_h - 12, (90, 90, 90, 255))
    y = dy + modele_h + 8
    nom_arme, calibre, ingredient = ARME[code]
    nom = couper_espace(nom_arme, NOM, K["ESP_NOM"], w)
    e.texte(nom, NOM, x, y + 5, TEXTE, (x, y - 4, w, 15), "nom de l'objet", centre_v=False, espace=K["ESP_NOM"])
    y += 16
    cat = cats[1]
    cat_w, cal_w = largeur_chip(cat), largeur_chip(calibre)
    if cat_w + 4 + cal_w > w:
        moitie = (w - 4) // 2
        if cat_w <= moitie:
            cal_w = w - 4 - cat_w
        elif cal_w <= moitie:
            cat_w = w - 4 - cal_w
        else:
            cat_w, cal_w = moitie, w - 4 - moitie
    chip_x = x
    for texte, cwid in ((cat, cat_w), (calibre, cal_w)):
        chip_x += chip(e, couper_espace(texte, SMALL, K["ESP_CHIP"], cwid - 9), chip_x, y, "pastille") + 4
    if chip_x - 4 > x + w:
        e.debordements.append(f"pastilles : fin {chip_x - 4} > {x + w}")
    y += 17
    tw = (w - 2 * K["TUILE_ECART"]) // 3
    tuiles = [("4,5", tr("stat.degats")), ("450", tr("stat.cadence")), ("1 200", tr("stat.chargeur"))]
    th = K["TUILE_H"]
    for i, (val, leg) in enumerate(tuiles):
        tx = x + i * (tw + K["TUILE_ECART"])
        tww = tw if i < 2 else w - 2 * (tw + K["TUILE_ECART"])
        e.rect(tx, y, tww, th, (255, 255, 255, 12), (255, 255, 255, 30))
        mx = tww - 2 * K["TUILE_PAD"]
        pv = GROS if GROS.largeur(val) <= mx else BOLD
        v = ellipsis(val, pv, mx)
        t, pol, esp = ajuster(leg, SMALL, 0.8, mx, True)
        hv, hl = CAPITALES_JAVA[pv.nom], CAPITALES_JAVA[pol.nom]
        haut = (th - (hv + K["TUILE_INTER"] + hl)) / 2
        case = (tx + 2, y + 2, tww - 4, th - 4)
        e.texte(v, pv, tx + K["TUILE_PAD"], y + haut + hv / 2, TEXTE, case, "valeur de tuile", centre_v=False)
        e.texte(t, pol, tx + K["TUILE_PAD"], y + th - haut - hl / 2, GRIS, case, "légende de tuile", centre_v=False, espace=esp)
        yv = e.placer(y + haut + hv / 2 - MILIEU_JAVA[pv.nom])
        yl = e.placer(y + th - haut - hl / 2 - MILIEU_JAVA[pol.nom])
        e.bloc_vertical("tuile « " + val + " / " + t + " »", [(pv, e.placer(tx + K["TUILE_PAD"]), yv), (pol, tx + K["TUILE_PAD"], yl)], (tx, y, tww, th))
    y += th + 8
    tgw = e.texte(tr("materiaux"), SMALL, x, y + MILIEU_JAVA["ui_small"], GRIS, (x, y - 2, w, 10), "titre Matériaux", centre_v=False, espace=K["ESP_SECTION"])
    e.ligne_h(x + int(tgw) + 8, x + w, y + arrondi(MILIEU_JAVA["ui_small"]), (255, 255, 255, 40))
    y += 12
    ingredients = [(noms[0], 1, 1), (noms[5], 0, 2), (ingredient, 3, 4), (noms[12], 12, 16), (noms[20], 1, 2), (noms[30], 1, 1)]
    fin = dy + dh - 9 - K["BOUTON_H"] - 6
    ligne = max(11, min(14, (fin - y) // len(ingredients)))
    lignes = max(0, (fin - y) // ligne)
    montrees = len(ingredients) if len(ingredients) <= lignes else max(0, lignes - 1)
    for i in range(montrees):
        n, a, b = ingredients[i]
        e.rect(x, y + (ligne - 10) / 2, 10, 10, (120, 120, 120, 255))
        gauche, droite = f"{a}", f" / {b}"
        dw_ = BOLD.exacte(gauche) + FONT.exacte(droite)
        nm = ellipsis(n, FONT, w - 14 - math.ceil(dw_) - 6)
        cyl = y + ligne / 2
        e.texte(nm, FONT, x + 14, cyl, TEXTE, (x + 12, y, w - 12 - math.ceil(dw_) - 4, ligne), "matériau")
        x1 = e.texte(gauche, BOLD, x + w - dw_, cyl, VERT if a >= b else ROUGE, (x, y, w, ligne), "compte de matériau")
        e.texte(droite, FONT, x + w - dw_ + x1, cyl, GRIS, (x, y, w, ligne), "compte de matériau")
        y += ligne
    if montrees < len(ingredients):
        plus = ellipsis(tr("autres", len(ingredients) - montrees), FONT, w - 14)
        e.texte(plus, FONT, x + 14, y + ligne / 2, GRIS, (x, y, w, ligne), "autres matériaux")
        y += ligne
    if y > fin + 0.01:
        e.debordements.append(f"matériaux : fin {y} > {fin}")
    # bouton (HzButton : capitales espacées centrées, largeur exacte, milieu des capitales)
    bw = dw - 2 * K["DETAIL_PAD"]
    by = dy + dh - 9 - K["BOUTON_H"]
    bh = K["BOUTON_H"]
    texte = tr("manque_un", 2, ingredient) if manque else tr("fabriquer")
    vu = couper_espace(texte, BOLD, K["ESP_BOUTON"], bw - 12)
    e.rect(x, by, bw, bh, (255, 107, 107, 20) if manque else (111, 168, 255, 34), (255, 107, 107, 200) if manque else (111, 168, 255, 170))
    xc, esp = x_caps_centre(e, vu, BOLD, K["ESP_BOUTON"], x + bw / 2)
    e.texte(vu, BOLD, xc, by + bh / 2, (255, 255, 255, 255), (x, by, bw, bh), "bouton", centre_h=True, espace=esp)
    # cases de l'en-tête : aucune ne doit en toucher une autre
    for i in range(len(e.cases)):
        for j in range(i + 1, len(e.cases)):
            a, b = e.cases[i], e.cases[j]
            if a[0].startswith("pastille") or b[0].startswith("pastille"):
                continue
            if a[1] < b[1] + b[3] and b[1] < a[1] + a[3] and a[2] < b[2] + b[4] and b[2] < a[2] + a[4]:
                e.debordements.append(f"cases qui se touchent : {a[0]} et {b[0]}")
    for nom_, xx, yy, ww, hh in e.cases:
        if xx < px or xx + ww > px + pw or yy < py or yy + hh > py + ph:
            e.debordements.append(f"{nom_} sort du panneau")
    fichier = sortie / f"table_{code[:2]}_{lw}x{lh}_echelle{s}{'_manque' if manque else ''}.png"
    if garder:
        sortie.mkdir(parents=True, exist_ok=True)
        e.img.save(fichier)
    return fichier, e.debordements, e.decentres, entete, noms


ENCRE = ASSETS / "ui/encre.json"


def table_encre():
    """Approches des lettres (encre à gauche et à droite de l'avancée) pour une police de taille 1 : lues par
    `kit/Encre.java` pour centrer l'ENCRE d'un texte et non sa boîte d'avancée. {graisse: {lettre: [gauche, droite]}}."""
    out = {}
    for graisse, pol in (("medium", FONT), ("bold", BOLD)):
        table = {}
        for f in reversed(pol.ttf):  # la 1re police qui a la lettre l'emporte (comme Minecraft)
            t = TTFont(f["fichier"])
            glyf, hmtx = t["glyf"], t["hmtx"].metrics
            for cp, nom in f["cmap"].items():
                ch = chr(cp)
                if ch in pol.espaces or ch.isspace():
                    continue
                g = glyf[nom]
                if not hasattr(g, "xMin") or g.numberOfContours == 0:
                    continue
                av = hmtx[nom][0]
                lsb = hmtx[nom][1]  # Minecraft ajoute l'approche gauche une 2e fois : encre décalée d'autant
                table[ch] = [round((g.xMin + lsb) / f["hh"], 5), round((av - g.xMax - lsb) / f["hh"], 5), round(lsb / f["hh"], 5)]
        out[graisse] = dict(sorted(table.items()))
    return out


def approche(c, pol):
    """Encre.approche : approche gauche (une fois) d'une lettre, en points d'interface."""
    t = TABLE_ENCRE["bold" if pol.nom in ("ui_bold", "ui_gros", "ui_nom") else "medium"]
    return t.get(c, [0, 0, 0])[2] * pol.ttf[0]["taille"]


def encre_px(s, pol, capitales=False):
    """(gauche, droite) de l'encre du texte, en points d'interface (comme Encre.de / Encre.deCapitales) : texte entier,
    approche gauche comptée deux fois ; capitales espacées (Ui.spaced retire la 2e fois lettre par lettre)."""
    t = TABLE_ENCRE["bold" if pol.nom in ("ui_bold", "ui_gros", "ui_nom") else "medium"]
    s = s.strip()
    if not s:
        return 0.0, 0.0
    taille = pol.ttf[0]["taille"]
    a, b = t.get(s[0], [0, 0, 0]), t.get(s[-1], [0, 0, 0])
    if capitales:
        return (a[0] - a[2]) * taille, (b[1] + b[2]) * taille
    return a[0] * taille, b[1] * taille


TABLE_ENCRE = table_encre()


def verifier_constantes():
    """Ui.milieu et Ui.capitales du Java = valeurs calculées sur les TTF et les JSON."""
    erreurs = []
    for nom, pol in P.items():
        if abs(MILIEU_JAVA.get(nom, -99) - pol.milieu()) > 0.01:
            erreurs.append(f"Ui.milieu(« {nom} ») = {MILIEU_JAVA.get(nom)} dans le Java, {pol.milieu():.3f} d'après la police")
        if abs(CAPITALES_JAVA.get(nom, -99) - pol.capitales()) > 0.01:
            erreurs.append(f"Ui.capitales(« {nom} ») = {CAPITALES_JAVA.get(nom)} dans le Java, {pol.capitales():.3f} d'après la police")
    return erreurs


def main():
    sortie = SORTIE
    toutes = "--toutes" in sys.argv
    if "--sortie" in sys.argv:
        sortie = Path(sys.argv[sys.argv.index("--sortie") + 1])
    tous = verifier_constantes()
    texte_encre = json.dumps(TABLE_ENCRE, ensure_ascii=False, separators=(",", ":")) + "\n"
    if "--encre" in sys.argv:
        ENCRE.parent.mkdir(parents=True, exist_ok=True)
        ENCRE.write_text(texte_encre, encoding="utf-8")
        print(f"{ENCRE.relative_to(ROOT)} écrit.")
    elif not ENCRE.exists() or ENCRE.read_text(encoding="utf-8") != texte_encre:
        tous.append(f"{ENCRE.relative_to(ROOT)} pas à jour : relancer avec --encre")
    decentres = []
    fenetres = [(1920, 1080, None), (1920, 1057, None), (1280, 720, None), (1280, 689, None), (1366, 705, None), (1920, 1080, 2)]
    for code in ("fr_fr", "en_us", "ru_ru"):
        for lw, lh, forcee in fenetres:
            s = forcee or echelle_voulue(lw, lh)
            for manque in (False, True):
                if manque and (lw, lh) not in ((1920, 1080), (1280, 720)):
                    continue
                garder = toutes or (forcee is None and (lw, lh) in ((1920, 1080), (1280, 720)) and (code == "fr_fr" or not manque))
                f, deb, dec, entete, noms = dessiner(code, lw, lh, s, manque, sortie, garder)
                print(f"{f.relative_to(ROOT) if f.is_relative_to(ROOT) else f}{'' if garder else ' (contrôlé, non enregistré)'} : "
                      f"échelle {s}, en-tête {'1 ligne' if entete == K['ENTETE_1'] else '2 lignes'}, {len(deb)} débordement(s), {len(dec)} décentré(s)")
                tous += [f"{code} {lw}x{lh} échelle {s} : {d}" for d in deb]
                decentres += [f"{code} {lw}x{lh} échelle {s} : {d}" for d in dec]
        print(f"  ordre ({code}) : " + ", ".join(noms[:14]) + " …")
    if echelle_voulue(1280, 720) != 3:
        print(f"note : 1280 x 720 à l'échelle 3 n'existe pas (kit/Echelle choisit {echelle_voulue(1280, 720)} : 720 / 3 = 240 points de haut, moins que le canevas)")
    print()
    if tous:
        print("DÉBORDEMENTS :")
        for d in tous:
            print("  " + d)
    if decentres:
        print("TEXTES DÉCENTRÉS :")
        for d in decentres:
            print("  " + d)
    if tous or decentres:
        sys.exit(1)
    print("Aucun débordement, tout est centré.")


if __name__ == "__main__":
    main()
