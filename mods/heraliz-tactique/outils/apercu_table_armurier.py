"""Aperçu de la Table d'armurier hors du jeu, et contrôle « rien ne déborde » (Tactique 2.35.0).

    python mods/heraliz-tactique/outils/apercu_table_armurier.py              images dans maquettes/table_armurier/
    python mods/heraliz-tactique/outils/apercu_table_armurier.py --sortie D   images ailleurs
    python mods/heraliz-tactique/outils/apercu_table_armurier.py --toutes     enregistre aussi les cas seulement contrôlés

Refait la mise en page de `camo/client/atelier/TableArmurier.java` avec les MÊMES nombres (les constantes sont lues
dans le fichier Java lui-même) et les mêmes polices (TTF du mod, tailles des fichiers `font/*.json`, avancées des
lettres calculées comme Minecraft 1.20.1 : taille / (ascendante - descendante) de la police, somme arrondie au-dessus,
espace = 3), puis dessine l'écran en français, anglais et russe, pour les fenêtres usuelles, à l'échelle que choisit
`kit/Echelle` (et à l'échelle 2 forcée sur une fenêtre 1080p). Chaque texte est contrôlé contre sa case : la liste des
débordements est affichée et doit être vide (code de sortie 1 sinon). Les cases des en-têtes ne doivent pas se toucher.

Les objets (icônes, modèle 3D) sont des carrés gris : seuls les textes et les cases comptent ici. Besoin : Pillow et
fontTools (`pip install fonttools`).
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
MOD = ROOT / "mods/heraliz-tactique/src/client"
JAVA = MOD / "java/fr/heralisz/camo/client/atelier/TableArmurier.java"
ASSETS = MOD / "resources/assets/heralisz_camo"
COMPOSANTS = ROOT / "mods/heraliz-composants/src/client/resources/assets/heraliz_composants/lang"
SORTIE = ROOT / "maquettes/table_armurier"

# ------------------------------------------------------------------ constantes lues dans le Java
SRC = JAVA.read_text(encoding="utf-8")
K = {}
for ligne in SRC.splitlines():
    if "static final int" in ligne or "static final float" in ligne:
        for nom, val in re.findall(r"(\b[A-Z][A-Z0-9_]*)\s*=\s*(-?\d+(?:\.\d+)?)f?", ligne):
            K[nom] = float(val) if "." in val else int(val)

# ------------------------------------------------------------------ polices (comme Minecraft)
class Police:
    def __init__(self, nom):
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
                                     upem=t["head"].unitsPerEm, asc=t["hhea"].ascent, taille=p["size"],
                                     decal=p.get("shift", [0, 0])))
        self.taille = self.ttf[0]["taille"]
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

    def ascendante(self):
        f = self.ttf[0]
        return f["asc"] * f["taille"] / f["hh"]

    def hauteur_capitale(self):
        f = self.ttf[0]
        return 0.7 * f["upem"] * f["taille"] / f["hh"]

    def image(self, f, echelle):
        cle = (f["fichier"], echelle)
        if cle not in self.pil:
            self.pil[cle] = ImageFont.truetype(f["fichier"], max(1, round(f["taille"] * f["upem"] / f["hh"] * echelle)))
        return self.pil[cle]


P = {n: Police(n) for n in ("ui", "ui_bold", "ui_small", "ui_gros", "ui_nom", "ui_mini")}
FONT, BOLD, SMALL, GROS, NOM, MINI = (P[n] for n in ("ui", "ui_bold", "ui_small", "ui_gros", "ui_nom", "ui_mini"))


def caps_largeur(s, pol, esp):
    """Ui.spacedWidth (avancée réelle de chaque lettre, sans arrondi : StringSplitter.stringWidth)."""
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


# ------------------------------------------------------------------ dessin et contrôle
class Ecran:
    def __init__(self, lw, lh, s):
        self.s = s
        self.w, self.h = lw // s, lh // s
        self.img = Image.new("RGB", (lw, lh), (24, 27, 33))
        self.d = ImageDraw.Draw(self.img, "RGBA")
        self.debordements = []
        self.cases = []

    def rect(self, x, y, w, h, rgba, contour=None):
        s = self.s
        self.d.rectangle([x * s, y * s, (x + w) * s - 1, (y + h) * s - 1], fill=rgba, outline=contour)

    def ligne_h(self, x0, x1, y, rgba):
        self.d.rectangle([x0 * self.s, y * self.s, x1 * self.s - 1, (y + 1) * self.s - 1], fill=rgba)

    def _glyphes(self, s, pol, x, y, rgba, esp=None):
        """Dessine lettre par lettre, posée au point d'écran (comme Ui.placer) ; rend la largeur."""
        sc = self.s
        cx = x
        for c in s:
            f = pol.source(c)
            if f is not None and c not in pol.espaces:
                base = (y + f["decal"][1] + f["asc"] * f["taille"] / f["hh"])
                self.d.text((round(cx * sc), round(base * sc)), c, font=pol.image(f, sc), fill=rgba, anchor="ls")
            cx += pol.avance(c) + (esp if esp is not None else 0.0)
        return cx - x

    def texte(self, s, pol, x, y, rgba, case, nom):
        w = pol.largeur(s)
        self._glyphes(s, pol, x, y, rgba)
        self.controle(nom, s, x, y, w, pol, case)
        return w

    def caps(self, s, pol, x, y, rgba, esp, case, nom):
        up = s.upper()
        w = caps_largeur(up, pol, esp)
        self._glyphes(up, pol, x, y, rgba, esp)
        self.controle(nom, up, x, y, w, pol, case)
        return w

    def controle(self, nom, s, x, y, w, pol, case):
        if case is None:
            return
        cx, cy, cw, ch = case
        haut = y + 1 + pol.ascendante() - pol.hauteur_capitale()
        bas = y + 1 + pol.ascendante()
        if x < cx - 0.01 or x + w > cx + cw + 0.01 or haut < cy - 0.01 or bas > cy + ch + 0.01:
            self.debordements.append(f"{nom} « {s} » : texte x {x:.1f}..{x + w:.1f}, y {haut:.1f}..{bas:.1f} ; case x {cx}..{cx + cw}, y {cy}..{cy + ch}")

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
    "fr_fr": ["Munitions", "Pistolets", "Mitraillettes", "Fusils d'assaut", "Pompes", "Soins", "Viseurs", "Accessoires", "Explosifs", "Composants"],
    "en_us": ["Ammunition", "Pistols", "SMGs", "Rifles", "Shotguns", "Medical", "Sights", "Attachments", "Explosives", "Components"],
    "ru_ru": ["Боеприпасы", "Пистолеты", "Пистолеты-пулемёты", "Винтовки", "Дробовики", "Медицина", "Прицелы", "Обвесы", "Взрывчатка", "Компоненты"],
}
ARME = {
    "fr_fr": ("Thunderbird .50 édition Désert", "Balle de 9mm", "Kit de pièces III"),
    "en_us": ("Thunderbird .50 Desert edition", "9mm round", "Parts kit III"),
    "ru_ru": ("Thunderbird .50 пустынная версия", "Патрон 9 мм", "Набор деталей III"),
}


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
        filtres.append((fx0, py + K["RECHERCHE_Y"], K["RECHERCHE_H"], 4, en_main, 10 ** 6, True))
        filtres.append((fx0 + ew + K["FILTRE_ECART"], py + K["RECHERCHE_Y"], K["RECHERCHE_H"], 4, dispo, 10 ** 6, False))
    else:
        entete = K["ENTETE_2"]
        rw = max(60, min(cx + cw, fx - K["AVANT_FERMER"]) - sx)
        place = px + pw - K["MARGE"] - sx
        m1 = m2 = 10 ** 6
        if filtres_w > place:
            m1 = m2 = (place - K["FILTRE_ECART"]) // 2 - case_t
        v1 = ellipsis(en_main, FONT, m1)
        filtres.append((sx, py + K["LIGNE2_Y"], K["LIGNE2_H"], 1, en_main, m1, True))
        filtres.append((sx + case_t + FONT.largeur(v1) + K["FILTRE_ECART"], py + K["LIGNE2_Y"], K["LIGNE2_H"], 1, dispo, m2, False))
    # titre
    e.caps(tr("titre_petit"), SMALL, px + K["MARGE"], py + 10, (143, 149, 160, 255), K["ESP_TITRE_PETIT"], (px + K["MARGE"], py + 6, titre_w, 14), "titre petit")
    e.caps(tr("titre"), GROS, px + K["MARGE"], py + 18, (236, 238, 242, 255), K["ESP_TITRE"], (px + K["MARGE"], py + 18, titre_w, entete - 20), "titre")
    e.case("titre", px + K["MARGE"], py + 8, titre_w, 26)
    # recherche
    ry = py + K["RECHERCHE_Y"]
    e.rect(sx, ry, rw, K["RECHERCHE_H"], (0, 0, 0, 80))
    e.ligne_h(sx, sx + rw, ry + K["RECHERCHE_H"] - 1, (255, 255, 255, 60))
    e.texte(ellipsis(tr("recherche"), FONT, rw - 8), FONT, sx + 4, ry + (K["RECHERCHE_H"] - 8) // 2, (90, 95, 105, 255), (sx + 2, ry, rw - 4, K["RECHERCHE_H"]), "invite de recherche")
    e.case("recherche", sx, ry, rw, K["RECHERCHE_H"])
    for (zx, zy, zh, dec, texte, mx, coche) in filtres:
        vu = ellipsis(texte, FONT, mx)
        zw = case_t + FONT.largeur(vu)
        e.rect(zx, zy + dec + 2, K["CASE"], K["CASE"], (111, 168, 255, 215) if coche else (255, 255, 255, 30))
        e.texte(vu, FONT, zx + case_t, zy + dec, (143, 149, 160, 255), (zx, zy, zw, zh), "filtre")
        e.case("filtre " + texte, zx, zy, zw, zh)
    # fermer
    kw = SMALL.largeur(echap) + 8
    e.rect(fx, py + 17, kw, 11, (255, 255, 255, 30), (255, 255, 255, 60))
    e.texte(echap, SMALL, fx + 4, py + 19, (236, 238, 242, 255), (fx, py + 17, kw, 11), "touche Échap")
    e.texte(fermer, FONT, fx + kw + 5, py + 18, (143, 149, 160, 255), (fx + kw, py + 17, fw - kw, 12), "Fermer")
    e.case("fermer", fx, py + 17, fw, 12)
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
        e.rect(gx + 5, y + (h - 10) // 2, 10, 10, (120, 120, 120, 255))
        n = str(17 + i * 9)
        nw = SMALL.largeur(n)
        nom = ellipsis(c, FONT, gw - 19 - 5 - nw - 4)
        e.texte(nom, FONT, gx + 19, y + (h - 8) // 2 - 1, (200, 204, 210, 255), (gx + 17, y, gw - 5 - nw - 4 - 17, h + 2), "catégorie")
        e.texte(n, SMALL, gx + gw - 5 - nw, y + (h - 8) / 2, (90, 95, 105, 255), (gx, y, gw, h + 2), "nombre de la catégorie")
        y += rang
    # ---- cartes (catégorie Composants, triée)
    cy = py + entete + 22
    noms = noms_composants(code)
    noms.sort(key=cle_tri)
    rangs_vis = max(1, (py + ph - 10 - cy + K["ECART"]) // (K["CARTE_H"] + K["ECART"]))
    sy = py + entete + 10
    nb = str(len(noms))
    nbw = math.ceil(caps_largeur(nb, SMALL, 0.8))
    titre_grille = couper_espace(cats[-1], SMALL, K["ESP_SECTION"], cw - nbw - 24)
    tgw = e.caps(titre_grille, SMALL, cx, sy, (143, 149, 160, 255), K["ESP_SECTION"], (cx, sy - 2, cw - nbw - 8, 10), "titre de la grille")
    e.ligne_h(cx + int(tgw) + 8, cx + cw - nbw - 8, sy + 4, (255, 255, 255, 40))
    e.caps(nb, SMALL, cx + cw - nbw, sy, (90, 95, 105, 255), 0.8, (cx + cw - nbw - 4, sy - 2, nbw + 4, 10), "nombre d'objets")
    for k in range(min(len(noms), rangs_vis * K["COLONNES"])):
        x0 = cx + (k % K["COLONNES"]) * (K["CARTE_W"] + K["ECART"])
        y0 = cy + (k // K["COLONNES"]) * (K["CARTE_H"] + K["ECART"])
        w, h = K["CARTE_W"], K["CARTE_H"]
        e.rect(x0, y0, w, h, (255, 255, 255, 12), (255, 255, 255, 40) if k else (111, 168, 255, 230))
        e.rect(x0 + w // 2 - 16, y0 + 4, 32, 32, (110, 110, 110, 255))
        nom = ellipsis(noms[k], FONT, w - 8)
        e.texte(nom, FONT, x0 + 4, y0 + h - 24, (236, 238, 242, 255), (x0 + 2, y0, w - 4, h), "nom de carte")
        ok = k % 3 != 2
        st = tr("fabricable") if ok else tr("manque", 2)
        e.rect(x0 + 4, y0 + h - 11, 5, 5, (95, 208, 138, 255) if ok else (90, 95, 105, 255))
        t, pol, esp = ajuster(st, SMALL, K["ESP_CHIP"], w - 11 - 4, True)
        e.caps(t, pol, x0 + 11, y0 + h - 13 + (1 if pol is MINI else 0), (95, 208, 138, 255) if ok else (143, 149, 160, 255), esp,
               (x0 + 11, y0 + h - 14, w - 11 - 3, 13), "état de carte")
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
    e.caps(nom, NOM, x, y, (236, 238, 242, 255), K["ESP_NOM"], (x, y, w, 16), "nom de l'objet")
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
        t = couper_espace(texte, SMALL, K["ESP_CHIP"], cwid - 9)
        lc = largeur_chip(t)
        e.rect(chip_x, y, lc, 11, (143, 149, 160, 34), (143, 149, 160, 90))
        e.caps(t, SMALL, chip_x + 5, y + 2.5, (190, 194, 200, 255), K["ESP_CHIP"], (chip_x + 1, y, lc - 2, 11), "pastille")
        e.case("pastille " + texte, chip_x, y, lc, 11)
        chip_x += lc + 4
    if chip_x - 4 > x + w:
        e.debordements.append(f"pastilles : fin {chip_x - 4} > {x + w}")
    y += 17
    tw = (w - 2 * K["TUILE_ECART"]) // 3
    tuiles = [("4,5", tr("stat.degats")), ("450", tr("stat.cadence")), ("1 200", tr("stat.chargeur"))]
    for i, (val, leg) in enumerate(tuiles):
        tx = x + i * (tw + K["TUILE_ECART"])
        tww = tw if i < 2 else w - 2 * (tw + K["TUILE_ECART"])
        th = K["TUILE_H"]
        e.rect(tx, y, tww, th, (255, 255, 255, 12), (255, 255, 255, 30))
        mx = tww - 2 * K["TUILE_PAD"]
        pv = GROS if GROS.largeur(val) <= mx else BOLD
        v = ellipsis(val, pv, mx)
        e.texte(v, pv, tx + K["TUILE_PAD"], y + (6 if pv is GROS else 9), (236, 238, 242, 255), (tx + 2, y + 2, tww - 4, th - 4), "valeur de tuile")
        t, pol, esp = ajuster(leg, SMALL, 0.8, mx, True)
        e.caps(t, pol, tx + K["TUILE_PAD"], y + th - 13 + (1 if pol is MINI else 0), (143, 149, 160, 255), esp, (tx + 2, y + 2, tww - 4, th - 4), "légende de tuile")
    y += K["TUILE_H"] + 8
    tgw = e.caps(tr("materiaux"), SMALL, x, y, (143, 149, 160, 255), K["ESP_SECTION"], (x, y - 2, w, 10), "titre Matériaux")
    e.ligne_h(x + int(tgw) + 8, x + w, y + 4, (255, 255, 255, 40))
    y += 12
    ingredients = [(n, a, b) for n, a, b in ((noms[0], 1, 1), (noms[5], 0, 2), (ingredient, 3, 4), (noms[12], 12, 16), (noms[20], 1, 2), (noms[30], 1, 1))]
    fin = dy + dh - 9 - K["BOUTON_H"] - 6
    ligne = max(11, min(14, (fin - y) // len(ingredients)))
    lignes = max(0, (fin - y) // ligne)
    montrees = len(ingredients) if len(ingredients) <= lignes else max(0, lignes - 1)
    for i in range(montrees):
        n, a, b = ingredients[i]
        e.rect(x, y + (ligne - 10) // 2, 10, 10, (120, 120, 120, 255))
        droite = (f"{a}", f" / {b}") if not manque else (f"{a}", f" / {b}")
        dw_ = math.ceil(sum(BOLD.avance(c) for c in droite[0]) + sum(FONT.avance(c) for c in droite[1]) - 1e-6)
        nm = ellipsis(n, FONT, w - 14 - dw_ - 6)
        ty = y + (ligne - 9) // 2
        e.texte(nm, FONT, x + 14, ty, (236, 238, 242, 230), (x + 12, y - 1, w - 12 - dw_ - 4, ligne + 2), "matériau")
        e._glyphes(droite[0], BOLD, x + w - dw_, ty, (95, 208, 138, 255) if a >= b else (255, 107, 107, 255))
        e._glyphes(droite[1], FONT, x + w - dw_ + sum(BOLD.avance(c) for c in droite[0]), ty, (143, 149, 160, 255))
        e.controle("compte de matériau", "".join(droite), x + w - dw_, ty, dw_, FONT, (x, y - 1, w, ligne + 2))
        y += ligne
    if montrees < len(ingredients):
        plus = ellipsis(tr("autres", len(ingredients) - montrees), FONT, w - 14)
        e.texte(plus, FONT, x + 14, y + (ligne - 9) // 2, (143, 149, 160, 255), (x, y - 1, w, ligne + 2), "autres matériaux")
        y += ligne
    if y > fin + 0.01:
        e.debordements.append(f"matériaux : fin {y} > {fin}")
    # bouton
    bw = dw - 2 * K["DETAIL_PAD"]
    by = dy + dh - 9 - K["BOUTON_H"]
    texte = tr("manque_un", 2, ingredient) if manque else tr("fabriquer")
    vu = couper_espace(texte, BOLD, K["ESP_BOUTON"], bw - 12)
    e.rect(x, by, bw, K["BOUTON_H"], (255, 107, 107, 20) if manque else (111, 168, 255, 34), (255, 107, 107, 200) if manque else (111, 168, 255, 170))
    lw_ = caps_largeur(vu, BOLD, K["ESP_BOUTON"])
    e.caps(vu, BOLD, x + bw / 2 - lw_ / 2, by + (K["BOUTON_H"] - 8) / 2, (255, 255, 255, 255), K["ESP_BOUTON"], (x + 2, by, bw - 4, K["BOUTON_H"]), "bouton")
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
    return fichier, e.debordements, entete, noms


def main():
    sortie = SORTIE
    toutes = "--toutes" in sys.argv
    if "--sortie" in sys.argv:
        sortie = Path(sys.argv[sys.argv.index("--sortie") + 1])
    fenetres = [(1920, 1080, None), (1920, 1057, None), (1280, 720, None), (1280, 689, None), (1366, 705, None), (1920, 1080, 2)]
    tous = []
    for code in ("fr_fr", "en_us", "ru_ru"):
        for lw, lh, forcee in fenetres:
            s = forcee or echelle_voulue(lw, lh)
            for manque in (False, True):
                if manque and (lw, lh) not in ((1920, 1080), (1280, 720)):
                    continue
                garder = toutes or (forcee is None and (lw, lh) in ((1920, 1080), (1280, 720)) and (code == "fr_fr" or not manque))
                f, deb, entete, noms = dessiner(code, lw, lh, s, manque, sortie, garder)
                print(f"{f.relative_to(ROOT) if f.is_relative_to(ROOT) else f}{'' if garder else ' (contrôlé, non enregistré)'} :échelle {s}, en-tête {'1 ligne' if entete == K['ENTETE_1'] else '2 lignes'}, {len(deb)} débordement(s)")
                tous += [f"{code} {lw}x{lh} échelle {s} : {d}" for d in deb]
        print(f"  ordre ({code}) : " + ", ".join(noms[:14]) + " …")
    if echelle_voulue(1280, 720) != 3:
        print(f"note : 1280 x 720 à l'échelle 3 n'existe pas (kit/Echelle choisit {echelle_voulue(1280, 720)} : 720 / 3 = 240 points de haut, moins que le canevas)")
    print()
    if tous:
        print("DÉBORDEMENTS :")
        for d in tous:
            print("  " + d)
        sys.exit(1)
    print("Aucun débordement.")


if __name__ == "__main__":
    main()
