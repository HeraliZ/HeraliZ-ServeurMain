"""Écrans des caisses au niveau de la boutique premium : MAQUETTES (étape 1, rien n'est codé dans le jeu).

    python mods/heraliz-crates/outils/apercu_caisses_premium.py [DOSSIER] [--copie DOSSIER2] [--caisse camo]

Demande du propriétaire (24/10) : « refaire au même niveau que la boutique les UIs des caisses à loot ». Ce script dessine,
hors du jeu, les écrans que le joueur voit devant une caisse de HeraliZ Caisses (CaseOpenScreen : roue, contenu, carte du
gain), dans le langage de la boutique premium (client/EcranBoutique de HeraliZ Grades, aperçu
mods/heraliz-grades/outils/apercu_boutique.py) : voile sombre sur le monde, panneaux de verre, bord d'un pixel, filet de
couleur en haut, dégradés de la couleur de la rareté, polices DM Sans (Onest pour le cyrillique) à leur vraie taille,
rangée des touches en bas, nombre de clés en haut à droite (à la place du solde de PB).

Deux variantes de l'écran d'ouverture :
- A « Vitrine » : la mise en page de la boutique (titre et sous-titre en haut à gauche, clés en haut à droite, onglets
  Ouvrir / Contenu), roue horizontale sur toute la largeur, sous elle l'objet sous le repère et les chances, à droite le
  panneau d'ouverture (comme le panneau d'achat des grades). Le contenu complet est un onglet à part (grille de cartes,
  chance de chaque objet, tri par rareté).
- B « Scène » : tout sur un écran, sans onglets : à gauche la caisse elle-même (vrai modèle 3D) avec les clés et le bouton,
  au milieu la roue en colonne verticale, à droite l'objet sous le repère, les chances par rareté et le contenu en
  mini-grille.
Une seule carte du gain (après la roue), dessinée par-dessus la variante A.

Images (maquettes/caisses_premium/, suffixe _1920 = échelle 3, _1280 = échelle 2, _en / _ru = autres langues) :
1_A_vitrine (fr, en, ru), 2_B_scene (fr, en, ru), 3_A_sans_cle, 4_A_contenu, 5_gain. Exemple : caisse Camos (47 objets,
vraies chances de serveur/config/heralizcrates/camo.json), 12 clés, Camouflage Orion sous le repère, Skin X · Séraphin
gagné.

Mesures : en unités d'interface du canevas 640 x 360 (Echelle.forcer de la caisse), en tête de ce fichier ; le Java de la
caisse reprendra exactement ces chiffres (comme EcranBoutique et apercu_boutique.py). Polices : (gras, taille de l'em,
espacement des lettres), mêmes conventions que les polices bq_* de la boutique (taille du .json du jeu = em x 1,302).
Dessin à 6 points par unité, réduit à l'échelle 3 (1920 x 1080) et 2 (1280 x 720). Images des objets : vraies textures du
dépôt (camos de HeraliZ Tactique, emotes, skins de couteaux), dessinées au plus proche voisin comme le jeu dessine une
icône agrandie ; clés et caisse : vrai modèle (KeySmith et Design de la caisse) rendu par le moteur logiciel de
outils/ApercuCaisse.java (JOML : variable JOML_JAR, sinon téléchargé dans ~/.cache/heraliz, jamais dans le dépôt). En jeu,
clé et caisse seraient dessinées par le jeu lui-même (rendu de l'objet, comme dans l'inventaire).

Contrôle : chaque texte est mesuré avec la vraie police dans les trois langues (français, anglais, russe) ; un texte qui
dépasse son cadre arrête le script (code 1). Les textes nouveaux (pas encore dans les fichiers de langue du mod) sont dans
le tableau TEXTES ci-dessous, en trois langues : ils iront dans fr_fr / en_us / ru_ru au moment du code.
Numpy, Pillow, fontTools.
"""
import argparse
import json
import math
import os
import re
import shutil
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from fontTools.ttLib import TTFont

MOD = Path(__file__).resolve().parents[1]
DEPOT = MOD.parents[1]
ASSETS = MOD / "src" / "client" / "resources" / "assets" / "heralizcrates"
FONTS = ASSETS / "font"
LANGS = ASSETS / "lang"
CRATE_JAVA = MOD / "src" / "commun" / "java" / "fr" / "heraliz" / "crates" / "crate" / "CrateType.java"
BUTIN = DEPOT / "serveur" / "config" / "heralizcrates"
BOUTIQUE = DEPOT / "serveur" / "config" / "heraliz_grades" / "boutique.json"
TACTIQUE = DEPOT / "mods" / "heraliz-tactique" / "src" / "client" / "resources" / "assets"
EMOTE = DEPOT / "mods" / "heraliz-emote" / "src" / "client" / "resources" / "assets" / "heraliz_emote"
SKINS = DEPOT / "serveur" / "tacz" / "heralisz_skins" / "assets" / "heralisz_skins"
SORTIE = DEPOT / "maquettes" / "caisses_premium"
CACHE = Path.home() / ".cache" / "heraliz"
RENDUS = CACHE / "caisses_premium"
K = 6  # points par unité pendant le dessin
GW, GH = 640, 360

# ================================================================== mesures (unités du canevas 640 x 360)
M = 16                      # marge de l'écran (comme la boutique)
HAUT, BAS, PIED_Y = 86, 336, 348   # zone de contenu et ligne des touches (comme la boutique)
TITRE_Y, SOUS_Y = 20, 37    # en-tête : haut des capitales du titre et du sous-titre
CLES_W, CLES_W_SEUL, CLES_H, CLES_Y = 158, 112, 30, 14   # case des clés en haut à droite (avec / sans « Acheter »)
ONGLET_Y, ONGLET_H = 54, 22
# --- A « Vitrine »
A_ROUE_H = 118              # bande de la roue (HAUT .. HAUT + A_ROUE_H)
A_CARTE_W, A_CARTE_H = 72, 96   # carte du milieu de la roue
A_STEP = 80                 # écart entre deux centres de cartes
A_RETRAIT = 0.22            # les cartes rapetissent de 22 % par pas
A_ICONE = 40                # objet dans la carte du milieu
A_ECART = 9                 # écart entre panneaux
A_INFO_W = 380              # panneau « sous le repère + chances » ; le panneau d'ouverture prend le reste
A_LIGNE_H = 30              # ligne de la clé dans le panneau d'ouverture
BOUTON_H = 22
# --- A, onglet Contenu
C_FILTRE_W = 150            # colonne des raretés
C_FILTRE_LIGNE = 27
C_COLS, C_CARTE_H, C_GAP = 6, 76, 6
C_ICONE = 30
# --- B « Scène »
B_HERO_W = 196              # caisse, clés, bouton
B_ROUE_W = 150              # roue verticale
B_CARTE = 100               # carte du milieu (carrée)
B_STEP = 106
B_RETRAIT = 0.24
B_ICONE = 52
B_GRILLE_COLS, B_CELLULE, B_GRILLE_GAP, B_GRILLE_RANGS = 7, 28, 4, 4
B_LIGNE_CHANCE = 14
# --- carte du gain
G_W, G_H = 260, 248
G_ICONE = 64

# ================================================================== couleurs (EcranBoutique, HeraliZ Loot)
ACCENT, WHITE, LIGHT, OPTION, MUTED, FAINT, PINK = 0xE3201B, 0xFFFFFF, 0xECEEF2, 0xC3C8D0, 0x8F95A0, 0x5A5F69, 0xF08A86
NIGHT, GLASS, GOLD, OK, DANGER = 0x050608, 0x0D0F14, 0xF2C14E, 0x6FD08C, 0xFF8A80
CARTE = 0x151922            # fond d'une carte d'objet
# raretés de HeraliZ Loot (Rarete.java) dans l'ordre des raretés des caisses (LootRarity)
RARETES = ["common", "uncommon", "rare", "epic", "legendary", "mythic"]
COUL_RARETE = {"common": 0x9A9A9A, "uncommon": 0x5CBF4A, "rare": 0x3F8CFF, "epic": 0xA64DFF, "legendary": 0xF2B83A,
               "mythic": 0xFF3333}

# ================================================================== textes nouveaux (fr, en, ru) ; les autres viennent des fichiers de langue du mod
TEXTES = {
    "sous": ("%s · %s objets à gagner", "%s · %s items to win", "%s · предметов: %s"),
    "tes_cles": ("Tes clés", "Your keys", "Твои ключи"),
    "acheter": ("Acheter", "Buy", "Купить"),
    "onglet.ouvrir": ("Ouvrir", "Open", "Открыть"),
    "onglet.contenu": ("Contenu", "Contents", "Содержимое"),
    "sous_repere": ("Sous le repère", "Under the marker", "Под указателем"),
    "chance": ("Chance %s", "Chance %s", "Шанс %s"),
    "chances": ("Chances par rareté", "Odds by rarity", "Шансы по редкости"),
    "ouverture": ("Ouverture", "Opening", "Открытие"),
    "une_cle": ("1 clé par ouverture", "1 key per opening", "1 ключ за открытие"),
    "apres": ("Après l'ouverture", "After opening", "После открытия"),
    "n_cles": ("%s clés", "%s keys", "ключей: %s"),
    "aucune": ("Tu n'as aucune %s.", "You have no %s.", "У тебя нет: %s."),
    "acheter_cles": ("Acheter des clés", "Buy keys", "Купить ключи"),
    "raretes": ("Raretés", "Rarities", "Редкости"),
    "plus_rare": ("Le plus rare", "The rarest", "Самое редкое"),
    "tout": ("Tout", "All", "Все"),
    "n_objets": ("%s objets", "%s items", "предметов: %s"),
    "ajoute": ("Ajouté à ton inventaire", "Added to your inventory", "Добавлено в инвентарь"),
    "reste": ("Il te reste %s clés", "%s keys left", "Осталось ключей: %s"),
    "contenu_n": ("Contenu · %s", "Contents · %s", "Содержимое · %s"),
    "note.contenu": ("Chances pour une ouverture", "Odds for one opening", "Шансы за одно открытие"),
    "touche.tab": ("Tab", "Tab", "Tab"),
    "touche.clic": ("Clic", "Click", "Клик"),
    "action.defiler": ("Défiler", "Scroll", "Прокрутка"),
    "action.encore": ("Encore", "Again", "Ещё"),
    "action.passer": ("Passer", "Skip", "Пропустить"),
}


def rgb(c):
    return (c >> 16 & 255, c >> 8 & 255, c & 255)


def mix(a, b, t):
    ra, rb = rgb(a), rgb(b)
    return sum(int(ra[i] + (rb[i] - ra[i]) * t) << (16 - 8 * i) for i in range(3))


def recadrer(im):
    return im.crop(im.getbbox())


# ================================================================== clés et caisses : vrai modèle (aperçu seulement)
def joml():
    if os.environ.get("JOML_JAR"):
        return os.environ["JOML_JAR"]
    j = CACHE / "joml-1.10.5.jar"
    if not j.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve("https://repo1.maven.org/maven2/org/joml/joml/1.10.5/joml-1.10.5.jar", j)
    return str(j)


def rendre_modele(caisse, cle):
    """Rend la clé (KeySmith) ou la caisse (Design) avec le moteur d'ApercuCaisse, fond transparent ; garde en cache."""
    sortie = RENDUS / ("%s_%s.png" % (caisse, "cle" if cle else "caisse"))
    if sortie.exists():
        return sortie
    src = (MOD / "outils" / "ApercuCaisse.java").read_text(encoding="utf-8")
    rep = [("public final class ApercuCaisse", "public final class ApercuPremium"),
           ("int ss = 2;", "int ss = 4;"), ("col[i] = 0.05f - 0.03f * k;", "col[i] = 0;"),
           ("col[i + 1] = 0.045f - 0.03f * k;", "col[i + 1] = 0;"), ("col[i + 2] = 0.08f - 0.05f * k;", "col[i + 2] = 0;")]
    if cle:
        rep += [("d.render(s, list);", "d.renderKey(s, list);"),
                ("camPos.set(8 + (float) (Math.sin(yr) * Math.cos(pr) * dist), lookY + (float) (Math.sin(pr) * dist), 8 + (float) (Math.cos(yr) * Math.cos(pr) * dist));\n"
                 "        view.setLookAt(camPos.x, camPos.y, camPos.z, 8, lookY, 8, 0, 1, 0);",
                 "camPos.set((float) (Math.sin(yr) * Math.cos(pr) * dist), lookY + (float) (Math.sin(pr) * dist), (float) (Math.cos(yr) * Math.cos(pr) * dist));\n"
                 "        view.setLookAt(camPos.x, camPos.y, camPos.z, 0, lookY, 0, 0, 1, 0);")]
    for a, b in rep:
        if a not in src:
            sys.exit("ApercuCaisse.java a changé : adapter rendre_modele()")
        src = src.replace(a, b)
    a0 = src.index("        BufferedImage img = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_RGB);")
    a1 = src.index("        ImageIO.write(img")
    src = src[:a0] + """        BufferedImage img = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < oh; y++) {
            for (int x = 0; x < ow; x++) {
                float r = 0, g = 0, b = 0, cov = 0;
                for (int dy = 0; dy < ss; dy++) {
                    for (int dx = 0; dx < ss; dx++) {
                        int j = (y * ss + dy) * W + x * ss + dx, i = j * 3;
                        r += col[i]; g += col[i + 1]; b += col[i + 2];
                        if (zb[j] < Float.MAX_VALUE) cov += 1;
                    }
                }
                float n = ss * ss;
                r /= n; g /= n; b /= n; cov /= n;
                float al = Math.max(cov, Math.min(1, Math.max(r, Math.max(g, b))));
                if (al <= 0.003f) { img.setRGB(x, y, 0); continue; }
                int A = Math.min(255, (int) (al * 255));
                img.setRGB(x, y, (A << 24) | (tone(r / al) << 16) | (tone(g / al) << 8) | tone(b / al));
            }
        }
""" + src[a1:]
    s = MOD / "src"
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    RENDUS.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        f = Path(tmp) / "ApercuPremium.java"
        f.write_text(src, encoding="utf-8")
        sources = [str(p) for d in ("mesh", "scene") for p in (s / "client/java/fr/heraliz/crates/client/render" / d).rglob("*.java")]
        sources += [str(s / "commun/java/fr/heraliz/crates/registry/Snd.java"), str(s / "commun/java/fr/heraliz/crates/block/CrateAnim.java"), str(f)]
        out = Path(tmp) / "out"
        subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", str(out), "-cp", joml()] + sources, check=True, env=env,
                       stdout=subprocess.DEVNULL)
        args = ["22", "6", "52", "480", "720", "8.6"] if cle else ["32", "20", "64", "720", "640", "13"]
        subprocess.run(["java", "-cp", os.pathsep.join([str(out), joml()]), "ApercuPremium", str(ASSETS), str(sortie), caisse, "-1"] + args,
                       check=True, env=env)
    print("rendu", sortie)
    return sortie


# ================================================================== polices
class Polices:
    def __init__(self):
        self.cmap = TTFont(str(FONTS / "dmsans_bold.ttf")).getBestCmap()
        self.cache = {}

    def f(self, gras, taille, cyr):
        k = (gras, taille, cyr)
        if k not in self.cache:
            nom = ("cyrillique_" if cyr else "dmsans_") + ("bold" if gras else "medium") + ".ttf"
            self.cache[k] = ImageFont.truetype(str(FONTS / nom), int(round(taille * K * (1.021 if cyr else 1))))
        return self.cache[k]

    def morceaux(self, s):
        out = []
        for ch in s:
            cyr = ord(ch) not in self.cmap
            if out and out[-1][1] == cyr:
                out[-1][0] += ch
            else:
                out.append([ch, cyr])
        return out


# polices (gras, taille de l'em, espacement) : mêmes que la boutique (bq_*)
TITRE = (True, 13, 1.93)
NOM_GRAND = (True, 13, 1.4)
NOM = (True, 11, 0)
BOUTON = (True, 9, 0.85)
ONGLET = (True, 9, 1.05)
MINI = (True, 6.5, 0.56)
MINI_NUM = (True, 6.5, 0.5)
TEXTE = (False, 7.5, 0)
TEXTE_GRAS = (True, 9, 0)
PETIT = (False, 6.5, 0)
PETIT_GRAS = (True, 6.5, 0)
MICRO = (True, 5.6, 0.4)
CHIFFRE_GRAND = (True, 13, 0)
CHIFFRE = (True, 11, 0)

POL = None
TXT = {}
LANGUE = "fr_fr"


def t(cle, *args):
    if cle in TEXTES:
        s = TEXTES[cle][["fr_fr", "en_us", "ru_ru"].index(LANGUE)]
    else:
        s = TXT.get(cle, cle)
    if args:
        s = re.sub(r"%(\d+\$)?s", lambda m, it=iter(args): str(next(it)), s)
    return s.replace("%%", "%")


def nombre(v):
    s = "{:,}".format(int(v))
    return s.replace(",", " ") if LANGUE.startswith("fr") else s


def pct(v):
    """Pourcentage comme Ui.pct : une décimale sous 10 %, virgule en français et en russe."""
    s = ("%.1f" % v) if v < 10 else ("%.0f" % v)
    if s.endswith(".0") and v >= 1:
        s = s[:-2]
    if not LANGUE.startswith("en"):
        s = s.replace(".", ",")
    return s + (" %" if not LANGUE.startswith("en") else "%")


# ================================================================== surface de dessin (comme apercu_boutique.Ecran)
class Ecran:
    def __init__(self, fond):
        self.im = fond.copy()
        self.defauts = []

    def zone(self, x0, y0, x1, y1):
        bx, by = int(np.floor(x0 * K)), int(np.floor(y0 * K))
        return bx, by, max(1, int(np.ceil(x1 * K)) - bx), max(1, int(np.ceil(y1 * K)) - by)

    def rect(self, x0, y0, x1, y1, c, a):
        if x1 <= x0 or y1 <= y0 or a <= 0:
            return
        bx, by, w, h = self.zone(x0, y0, x1, y1)
        L = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(L).rectangle([x0 * K - bx, y0 * K - by, x1 * K - bx - 1, y1 * K - by - 1], fill=rgb(c) + (int(a),))
        self.im.alpha_composite(L, (bx, by))

    def border(self, x, y, w, h, c, a, e=1 / 3):
        self.rect(x, y, x + w, y + e, c, a)
        self.rect(x, y + h - e, x + w, y + h, c, a)
        self.rect(x, y + e, x + e, y + h - e, c, a)
        self.rect(x + w - e, y + e, x + w, y + h - e, c, a)

    def grad(self, x0, y0, x1, y1, c0, a0, c1, a1, vertical=False):
        if x1 <= x0 or y1 <= y0:
            return
        bx, by, w, h = self.zone(x0, y0, x1, y1)
        n = h if vertical else w
        tt = np.linspace(0, 1, n)[:, None] if vertical else np.linspace(0, 1, n)[None, :]
        arr = np.zeros((h, w, 4), np.float32)
        r0, r1 = np.array(rgb(c0), np.float32), np.array(rgb(c1), np.float32)
        arr[..., :3] = r0 + (r1 - r0) * tt[..., None]
        arr[..., 3] = a0 + (a1 - a0) * tt
        self.im.alpha_composite(Image.fromarray(arr.clip(0, 255).astype(np.uint8), "RGBA"), (bx, by))

    def glow(self, cx, cy, rx, ry, c, a):
        bx, by, w, h = self.zone(cx - rx, cy - ry, cx + rx, cy + ry)
        yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
        d = np.sqrt(((xx + bx - cx * K) / (rx * K)) ** 2 + ((yy + by - cy * K) / (ry * K)) ** 2)
        arr = np.zeros((h, w, 4), np.uint8)
        arr[..., :3] = rgb(c)
        arr[..., 3] = (np.clip(1 - d, 0, 1) * a).astype(np.uint8)
        self.im.alpha_composite(Image.fromarray(arr, "RGBA"), (bx, by))

    def disc(self, cx, cy, r, c, a, anneau=None):
        bx, by, w, h = self.zone(cx - r - 1, cy - r - 1, cx + r + 1, cy + r + 1)
        L = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        boite = [(cx - r) * K - bx, (cy - r) * K - by, (cx + r) * K - bx, (cy + r) * K - by]
        if anneau is None:
            ImageDraw.Draw(L).ellipse(boite, fill=rgb(c) + (int(a),))
        else:
            ImageDraw.Draw(L).ellipse(boite, outline=rgb(c) + (int(a),), width=max(1, int(round(anneau * K))))
        self.im.alpha_composite(L, (bx, by))

    def trait(self, pts, e, c, a):
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        bx, by, w, h = self.zone(min(xs) - e, min(ys) - e, max(xs) + e, max(ys) + e)
        L = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(L).line([(x * K - bx, y * K - by) for x, y in pts], fill=rgb(c) + (int(a),), width=max(1, int(round(e * K))), joint="curve")
        self.im.alpha_composite(L, (bx, by))

    def poly(self, pts, c, a):
        xs, ys = [p[0] for p in pts], [p[1] for p in pts]
        bx, by, w, h = self.zone(min(xs), min(ys), max(xs), max(ys))
        L = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(L).polygon([(x * K - bx, y * K - by) for x, y in pts], fill=rgb(c) + (int(a),))
        self.im.alpha_composite(L, (bx, by))

    def image(self, im, x, y, w, h, alpha=1.0, lisse=True):
        """lisse : images lissées (rendus 3D, jamais agrandies) ; sinon icône d'objet au plus proche voisin (comme le jeu)."""
        W, H = int(round(w * K)), int(round(h * K))
        if lisse and (W / 2 > im.width * 1.03 or H / 2 > im.height * 1.03):
            self.defauts.append("image agrandie à l'échelle 3 (%dx%d -> %dx%d)" % (im.width, im.height, W // 2, H // 2))
        if lisse:
            r = im.convert("RGBA").resize((W, H), Image.LANCZOS)
        else:
            # un point de texture = un carré d'écran à l'échelle 3 (comme le rendu d'objet de Minecraft), puis réduction
            px3 = im.convert("RGBA").resize((max(1, W // 2), max(1, H // 2)), Image.NEAREST)
            r = px3.resize((W, H), Image.NEAREST)
        if alpha < 1:
            arr = np.array(r)
            arr[..., 3] = (arr[..., 3] * alpha).astype(np.uint8)
            r = Image.fromarray(arr, "RGBA")
        self.im.alpha_composite(r, (int(round(x * K)), int(round(y * K))))

    def ajuster(self, im, x, y, w, h, bas=True, alpha=1.0):
        s = min(w / im.width, h / im.height)
        iw, ih = im.width * s, im.height * s
        self.image(im, x + (w - iw) / 2, y + (h - ih) if bas else y + (h - ih) / 2, iw, ih, alpha)

    def largeur(self, s, p):
        gras, taille, esp = p
        w, n = 0, 0
        for m, cyr in POL.morceaux(s):
            f = POL.f(gras, taille, cyr)
            if esp:
                for ch in m:
                    w += f.getlength(ch)
                    n += 1
            else:
                w += f.getlength(m)
        return w / K + (esp * (n - 1) if esp and n else 0)

    def hcap(self, p):
        b = POL.f(p[0], p[1], False).getbbox("H")
        return (b[3] - b[1]) / K

    def texte(self, s, x, haut, p, c, a=255, maxw=None, quoi=""):
        """Haut des capitales en « haut »."""
        if p[2]:
            s = s.upper()
        w = self.largeur(s, p)
        if maxw is not None and w > maxw + 0.05:
            self.defauts.append("[%s] %s : « %s » %.1f > %.1f" % (LANGUE, quoi or "texte", s, w, maxw))
        gras, taille, esp = p
        L = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(L)
        px = x * K
        top = POL.f(gras, taille, False).getbbox("H")[1]
        for m, cyr in POL.morceaux(s):
            f = POL.f(gras, taille, cyr)
            dy = POL.f(gras, taille, False).getmetrics()[0] - f.getmetrics()[0]
            for ch in (m if esp else [m]):
                d.text((px, haut * K - top + dy), ch, font=f, fill=rgb(c) + (int(a),))
                px += f.getlength(ch) + esp * K
        bb = L.getbbox()
        if bb:
            self.im.alpha_composite(L.crop(bb), (bb[0], bb[1]))
        return w

    def m(self, s, x, cy, p, c, a=255, maxw=None, quoi=""):
        return self.texte(s, x, cy - self.hcap(p) / 2, p, c, a, maxw, quoi)

    def c(self, s, cx, cy, p, c, a=255, maxw=None, quoi=""):
        w = self.largeur(s.upper() if p[2] else s, p)
        return self.texte(s, cx - w / 2, cy - self.hcap(p) / 2, p, c, a, maxw, quoi)

    def d(self, s, xd, cy, p, c, a=255, maxw=None, quoi=""):
        w = self.largeur(s.upper() if p[2] else s, p)
        return self.texte(s, xd - w, cy - self.hcap(p) / 2, p, c, a, maxw, quoi)

    def lignes(self, s, p, maxw):
        mots, out, cur = s.split(" "), [], ""
        for mot in mots:
            essai = (cur + " " + mot).strip()
            if cur and self.largeur(essai, p) > maxw:
                out.append(cur)
                cur = mot
            else:
                cur = essai
        if cur:
            out.append(cur)
        return out

    def coupe(self, s, p, maxw):
        """Texte coupé avec « … » s'il ne tient pas (comme Ui.ellipsis)."""
        if self.largeur(s, p) <= maxw:
            return s
        while s and self.largeur(s + "…", p) > maxw:
            s = s[:-1]
        return s.rstrip() + "…"

    def sauver(self, chemins):
        if self.defauts:
            print("\n".join(self.defauts))
            sys.exit("%d défaut(s)" % len(self.defauts))
        for ch, echelle in chemins:
            self.im.resize((GW * echelle, GH * echelle), Image.LANCZOS).convert("RGB").save(ch, optimize=True)
            print("écrit", ch)


# ================================================================== fond, verre, boutons (comme la boutique)
def fond():
    W, H = GW * K, GH * K
    rng = np.random.default_rng(7)
    y = np.linspace(0, 1, H)[:, None]
    sky, hor, grd = np.array([52, 66, 88], np.float32), np.array([78, 84, 86], np.float32), np.array([46, 52, 34], np.float32)
    base = np.where(y[..., None] < 0.55, sky + (hor - sky) * np.clip(y / 0.55, 0, 1)[..., None], hor + (grd - hor) * np.clip((y - 0.55) / 0.45, 0, 1)[..., None])
    img = np.repeat(base, W, axis=1)
    small = Image.fromarray((rng.random((18, 32)) * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)
    img = img + ((np.array(small, np.float32) / 255 - 0.5) * 26)[..., None]
    im = Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGB").filter(ImageFilter.GaussianBlur(K * 3)).convert("RGBA")
    e = Ecran(im)
    at, al = [0, 0.24, 0.46, 0.62, 1], [235, 215, 185, 200, 232]
    for i in range(4):
        e.grad(GW * at[i], 0, GW * at[i + 1], GH, NIGHT, al[i], NIGHT, al[i + 1])
    e.grad(0, 0, GW, GH * 0.3, 0, 90, 0, 0, vertical=True)
    e.grad(0, GH * 0.6, GW, GH, 0, 0, 0, 120, vertical=True)
    return e.im


def verre(e, x, y, w, h, a=199):
    e.rect(x, y, x + w, y + h, GLASS, a)
    e.border(x, y, w, h, WHITE, 26)
    e.rect(x + 1 / 3, y + 1 / 3, x + w - 1 / 3, y + 2 / 3, WHITE, 41)


def filet_haut(e, x, y, w, c):
    mid = x + w / 2
    e.grad(x + 10, y, mid, y + 2 / 3, c, 0, c, 255)
    e.grad(mid, y, x + w - 10, y + 2 / 3, c, 255, c, 0)


def bouton(e, x, y, w, h, actif=True, c=ACCENT):
    if actif:
        e.grad(x, y, x + w, y + h, c, 77, c, 31)
        e.border(x, y, w, h, c, 140)
    else:
        e.rect(x, y, x + w, y + h, WHITE, 13)
        e.border(x, y, w, h, WHITE, 31)


def separateur(e, x0, x1, y):
    e.rect(x0, y, x1, y + 1 / 3, WHITE, 23)


def puce_pleine(e, txt, cx, cy, c, sombre=0x10131A):
    """Pastille pleine de la couleur (comme « CHOISI » des cartes de grade), texte sombre en petites capitales."""
    lab = txt.upper()
    w = e.largeur(lab, MINI) + 10
    e.rect(cx - w / 2, cy - 5, cx + w / 2, cy + 5, c, 255)
    e.c(lab, cx, cy, MINI, sombre)
    return w


def puce_pleine_g(e, txt, x, cy, c, sombre=0x10131A):
    lab = txt.upper()
    w = e.largeur(lab, MINI) + 10
    e.rect(x, cy - 5, x + w, cy + 5, c, 255)
    e.m(lab, x + 5, cy, MINI, sombre)
    return w


def puce(e, txt, x, cy, c):
    w = e.largeur(txt, PETIT_GRAS) + 5
    e.rect(x, cy - 4, x + w, cy + 4, c, 50)
    e.border(x, cy - 4, w, 8, c, 140)
    e.c(txt, x + w / 2, cy, PETIT_GRAS, mix(c, WHITE, 0.3))
    return w


def triangle(e, cx, y, taille, bas, c, a=255):
    """Repère : triangle pointe en bas (bas=True) ou en haut, base en y."""
    if bas:
        e.poly([(cx - taille, y), (cx + taille, y), (cx, y + taille * 1.1)], c, a)
    else:
        e.poly([(cx - taille, y), (cx + taille, y), (cx, y - taille * 1.1)], c, a)


def triangle_cote(e, x, cy, taille, droite, c, a=255):
    """Repère latéral : pointe vers la droite (droite=True) ou vers la gauche, base en x."""
    s = 1 if droite else -1
    e.poly([(x, cy - taille), (x, cy + taille), (x + s * taille * 1.1, cy)], c, a)


# ================================================================== la caisse d'exemple
class Objet:
    def __init__(self, r):
        self.id = r["item"]
        self.rarete = r.get("rarity", "common")
        self.poids = max(0, r.get("weight", 1))
        self.nombre = r.get("count", 1)
        self.nbt = r.get("nbt") or ""
        self.nom_fixe = re.sub(r"&[0-9a-fk-or]", "", r["name"]) if r.get("name") else None
        self.chance = 0.0
        self._icone = None

    def couleur(self):
        return COUL_RARETE[self.rarete]

    def nom(self):
        if self.nom_fixe:
            return self.nom_fixe
        ns, p = self.id.split(":")
        for cle in ("item.%s.%s" % (ns, p),):
            for f in (TACTIQUE / ns / "lang" / (LANGUE + ".json"),):
                if f.exists():
                    d = json.loads(f.read_text(encoding="utf-8"))
                    if cle in d:
                        return d[cle]
        m = re.search(r'GunId:"[^:]+:([^"]+)"', self.nbt)
        if m:
            arme, fin = (m.group(1).split("__") + [""])[:2]
            return "%s | %s" % (arme.replace("_", " ").title(), fin.replace("_", " ").title())
        return p.replace("_", " ").capitalize()

    def icone(self):
        if self._icone is not None:
            return self._icone
        ns, p = self.id.split(":")
        chemin = None
        m = re.search(r'GunId:"heralisz_skins:([^"]+)"', self.nbt)
        if m:
            chemin = SKINS / "textures" / "gun" / "slot" / (m.group(1) + ".png")
        elif self.id == "heraliz_emote:icone":
            n = int(re.search(r"CustomModelData:(\d+)", self.nbt).group(1))
            modele = json.loads((EMOTE / "models" / "item" / "icone.json").read_text(encoding="utf-8"))
            for o in modele.get("overrides", []):
                if o["predicate"].get("custom_model_data") == n:
                    sous = json.loads((EMOTE / "models" / "item" / (o["model"].split(":")[1] + ".json")).read_text(encoding="utf-8"))
                    tex = sous["textures"]["layer0"].split(":")
                    chemin = EMOTE.parent / tex[0] / "textures" / (tex[1] + ".png")
        else:
            chemin = TACTIQUE / ns / "textures" / "item" / (p + ".png")
        if chemin is not None and chemin.exists():
            im = Image.open(chemin).convert("RGBA")
            if im.height > im.width:  # texture animée : première image
                im = im.crop((0, 0, im.width, im.width))
            self._icone = im
        else:
            im = Image.new("RGBA", (16, 16), rgb(self.couleur()) + (255,))
            self._icone = im
        return self._icone


def charger_caisse(caisse):
    d = json.loads((BUTIN / (caisse + ".json")).read_text(encoding="utf-8"))
    objets = [Objet(r) for r in d["rewards"]]
    total = sum(o.poids for o in objets) or 1
    for o in objets:
        o.chance = 100.0 * o.poids / total
    return objets


def couleur_caisse(caisse):
    src = CRATE_JAVA.read_text(encoding="utf-8")
    m = re.search(r'\("%s",\s*(0x[0-9A-Fa-f]+)' % re.escape(caisse), src)
    return int(m.group(1), 16) if m else GOLD


def caisse_vendue(caisse):
    d = json.loads(BOUTIQUE.read_text(encoding="utf-8"))
    return any(a.get("groupe") == caisse for a in d["articles"])


class Exemple:
    """Ce que montrent les maquettes : une caisse, ses objets, les clés du joueur, l'objet sous le repère."""

    def __init__(self, caisse, cles, repere_id, gain_id):
        self.caisse = caisse
        self.objets = charger_caisse(caisse)
        self.couleur = couleur_caisse(caisse)
        self.vendue = caisse_vendue(caisse)
        self.cles = cles
        self.repere = next(o for o in self.objets if o.id.endswith(repere_id))
        self.gain = next(o for o in self.objets if o.id.endswith(gain_id))
        # bande de la roue : objets tirés comme la vraie roue (surtout des communs), objet choisi sous le repère
        rng = np.random.default_rng(3)
        poids = np.array([o.poids for o in self.objets], np.float64)
        tirage = rng.choice(len(self.objets), 15, p=poids / poids.sum())
        self.bande = [self.objets[i] for i in tirage]
        self.bande[7] = self.repere
        self.bande[9] = next(o for o in self.objets if o.rarete == "mythic")
        self.bande[5] = next(o for o in self.objets if o.rarete == "epic")
        self.cle_img = recadrer(Image.open(rendre_modele(caisse, True)))
        self.caisse_img = recadrer(Image.open(rendre_modele(caisse, False)))

    def nom_caisse(self):
        return t("block.heralizcrates.%s_crate" % self.caisse)

    def nom_cle(self):
        return t("item.heralizcrates.%s_key" % self.caisse)

    def chances(self):
        out = []
        for r in RARETES:
            os_ = [o for o in self.objets if o.rarete == r]
            if os_:
                out.append((r, len(os_), sum(o.chance for o in os_)))
        return out

    def tries(self):
        return sorted(self.objets, key=lambda o: (-RARETES.index(o.rarete), o.chance))


def nom_rarete(r):
    return t("rarity.heralizcrates." + r)


# ================================================================== en-tête, onglets et pied (comme EcranBoutique)
def cle(e, ex, x, y, w, h, alpha=1.0, inclinaison=-28):
    im = recadrer(ex.cle_img.rotate(inclinaison, resample=Image.BICUBIC, expand=True))
    e.ajuster(im, x, y, w, h, bas=False, alpha=alpha)


def case_cles(e, ex, cles):
    """En haut à droite, à la place du solde de PB : tes clés de cette caisse ; « + Acheter » si la boutique la vend."""
    col = ex.couleur
    cw = CLES_W if ex.vendue else CLES_W_SEUL
    cx, cy, ch = GW - M - cw, CLES_Y, CLES_H
    e.grad(cx, cy, cx + cw, cy + ch, col, 40, col, 12)
    e.border(cx, cy, cw, ch, col, 110)
    e.glow(cx + 16, cy + ch / 2, 18, 15, col, 60)
    cle(e, ex, cx + 4, cy + 3, 24, 24)
    droite = cx + cw - (44 if ex.vendue else 6)
    e.texte(t("tes_cles"), cx + 33, cy + 6, MINI, mix(col, WHITE, 0.45), 230, maxw=droite - cx - 33 - 2, quoi="tes clés")
    lab = nombre(cles)
    e.texte(lab, cx + 33, cy + 15.5, CHIFFRE_GRAND, WHITE if cles else DANGER, maxw=droite - cx - 33 - 2, quoi="nombre de clés")
    if ex.vendue:
        bx = cx + cw - 44
        e.rect(bx, cy + 5, bx + 1 / 3, cy + ch - 5, col, 70)
        e.rect(bx + 22 - 4.5, cy + 10.5 - 0.67, bx + 22 + 4.5, cy + 10.5 + 0.67, col, 255)
        e.rect(bx + 22 - 0.67, cy + 10.5 - 4.5, bx + 22 + 0.67, cy + 10.5 + 4.5, col, 255)
        e.c(t("acheter"), bx + 22, cy + 22, MICRO, mix(col, WHITE, 0.4), 230, maxw=40, quoi="acheter")
    return cx


def entete(e, ex, onglet, cles):
    gauche = case_cles(e, ex, cles)
    e.texte(ex.nom_caisse(), M, TITRE_Y, TITRE, LIGHT, maxw=gauche - M - 12, quoi="titre")
    e.texte(t("sous", t("gui.heralizcrates.case.kind.items"), len(ex.objets)), M, SOUS_Y, TEXTE, MUTED, maxw=gauche - M - 12, quoi="sous-titre")
    x = M
    for i, (o, n) in enumerate([("ouvrir", None), ("contenu", len(ex.objets))]):
        lab = t("onglet." + o)
        badge = 0 if n is None else e.largeur(str(n), MINI_NUM) + 8
        w = e.largeur(lab.upper(), ONGLET) + 21.33 + 12 + (badge + 5 if badge else 0)
        ty, th = ONGLET_Y, ONGLET_H
        if i == onglet:
            e.grad(x, ty, x + w * 0.75, ty + th, ACCENT, 51, ACCENT, 10)
            e.grad(x + w * 0.75, ty, x + w, ty + th, ACCENT, 10, ACCENT, 0)
            e.border(x, ty, w, th, ACCENT, 92)
            e.rect(x + 4, ty + th - 1.33, x + w - 4, ty + th, ACCENT, 255)
        else:
            e.rect(x, ty, x + w, ty + th, WHITE, 8)
            e.border(x, ty, w, th, WHITE, 22)
        e.m("%02d" % (i + 1), x + 6.67, ty + th / 2, MINI_NUM, PINK if i == onglet else FAINT)
        lw = e.m(lab, x + 21.33, ty + th / 2, ONGLET, WHITE if i == onglet else OPTION)
        if badge:
            bx = x + 21.33 + lw + 5
            e.rect(bx, ty + th / 2 - 4.5, bx + badge, ty + th / 2 + 4.5, WHITE, 22)
            e.c(str(n), bx + badge / 2, ty + th / 2, MINI_NUM, OPTION)
        x += w + 4
    separateur(e, M, GW - M, ONGLET_Y + ONGLET_H + 4)


def pied(e, touches, note, note_c=FAINT):
    cy, x = PIED_Y, M
    for k, lab in touches:
        kl = t(k).upper()
        tw = e.largeur(kl, MINI_NUM)
        w = max(9, tw + 4.67)
        e.rect(x, cy - 4.5, x + w, cy + 4.5, WHITE, 30)
        e.border(x, cy - 4.5, w, 9, WHITE, 61)
        e.c(kl, x + w / 2, cy, MINI_NUM, WHITE)
        x += w + 3.5
        x += e.m(t(lab), x, cy, PETIT, MUTED) + 10
    if note:
        e.d(note, GW - M, cy, PETIT, note_c, maxw=GW - M - x - 8, quoi="note du pied")


TOUCHES_OUVRIR = [("gui.heralizcrates.case.touche.espace", "gui.heralizcrates.case.open"), ("touche.tab", "onglet.contenu"),
                  ("gui.heralizcrates.case.touche.echap", "gui.heralizcrates.case.close")]


# ================================================================== cartes d'objet
def carte_objet(e, o, cx, cy, w, h, icone, avant, voile, rarete_txt=True):
    """Carte d'objet de la roue (verticale w x h) : verre, dégradé de la rareté depuis le haut, lueur derrière l'objet, trait
    de couleur en bas ; avant (0..1) = carte sous le repère (bord de couleur, filet, nom de la rareté) ; voile (0..1) =
    effacement vers les bords."""
    col = o.couleur()
    x, y = cx - w / 2, cy - h / 2
    if avant > 0:
        e.glow(cx, cy, w * 0.85, h * 0.75, col, 60 * avant)
    e.rect(x, y, x + w, y + h, CARTE, 240)
    e.grad(x, y, x + w, y + h * 0.62, col, 30 + 40 * avant, col, 0, vertical=True)
    iy = cy - h * (0.08 if rarete_txt else 0.02)
    e.glow(cx, iy, w * 0.42, w * 0.42, col, 55 + 50 * avant)
    if avant > 0:
        e.border(x, y, w, h, col, 200 * avant, 2 / 3)
        filet_haut(e, x, y, w, mix(col, WHITE, 0.3))
    else:
        e.border(x, y, w, h, WHITE, 26)
    e.image(o.icone(), cx - icone / 2, iy - icone / 2, icone, icone, lisse=False)
    e.rect(cx - w * 0.3, y + h - 4, cx + w * 0.3, y + h - 4 + 4 / 3, col, 255)
    if avant > 0 and rarete_txt:
        e.c(nom_rarete(o.rarete), cx, y + h - 13, MICRO, col, 255 * avant, maxw=w - 8, quoi="rareté de carte")
    if voile > 0:
        e.rect(x - 1, y - 1, x + w + 1, y + h + 1, GLASS, 225 * voile)


def legende(e, ex, x, y, w):
    """Barre des chances (une part par rareté) et légende en trois colonnes : pastille, nom, pourcentage."""
    ch = ex.chances()
    xx = x
    for k, (r, _n, p) in enumerate(ch):
        fin = x + w if k == len(ch) - 1 else xx + p / 100 * w
        e.rect(xx, y, max(xx + 1, fin - (0 if k == len(ch) - 1 else 2 / 3)), y + 4, COUL_RARETE[r], 255)
        xx = fin
    colw = (w - 2 * 14) / 3
    for k, (r, _n, p) in enumerate(ch):
        cx, cy = x + (k % 3) * (colw + 14), y + 16 + (k // 3) * 13
        e.disc(cx + 2.4, cy, 2.4, COUL_RARETE[r], 255)
        pw = e.d(pct(p), cx + colw, cy, PETIT_GRAS, mix(COUL_RARETE[r], WHITE, 0.25))
        e.m(nom_rarete(r), cx + 8, cy, TEXTE, LIGHT, maxw=colw - 8 - pw - 4, quoi="légende")
    return y + 16 + ((len(ch) + 2) // 3 - 1) * 13


def sous_repere(e, ex, x, y, w):
    o = ex.repere
    e.texte(t("sous_repere"), x, y, MINI, MUTED, maxw=w, quoi="sous le repère")
    e.texte(o.nom(), x, y + 11, NOM, WHITE, maxw=w, quoi="nom de l'objet")
    pw = puce_pleine_g(e, nom_rarete(o.rarete), x, y + 31, o.couleur())
    e.m(t("chance", pct(o.chance)), x + pw + 6, y + 31, TEXTE, OPTION, maxw=w - pw - 6, quoi="chance")


# ================================================================== A « Vitrine » : onglet Ouvrir
def ecran_a(sortie, ex, cles=None, gain=False):
    cles = ex.cles if cles is None else cles
    e = Ecran(FOND)
    entete(e, ex, 0, cles)
    # roue
    rx, ry, rw, rh = M, HAUT, GW - 2 * M, A_ROUE_H
    verre(e, rx, ry, rw, rh, 175)
    centre, cy = rx + rw / 2, ry + rh / 2 + 2
    cartes = []
    for i, o in enumerate(ex.bande):
        d = (i - 7) * A_STEP
        s = 1 / (1 + A_RETRAIT * abs(d) / A_STEP)
        if abs(d) - A_CARTE_W * s / 2 > rw / 2 - 4:
            continue
        cartes.append((abs(d), i, o, d, s))
    for _ad, i, o, d, s in sorted(cartes, key=lambda c: -c[0]):
        fondu = min(1, max(0, (abs(d) - A_STEP * 1.2) / (rw / 2 - A_STEP * 1.2)))
        carte_objet(e, o, centre + d, cy, A_CARTE_W * s, A_CARTE_H * s, A_ICONE * s, 1.0 if d == 0 else 0.0, fondu * 0.75)
    # bords : la roue s'efface sous le verre
    e.grad(rx + 1 / 3, ry + 1, rx + 70, ry + rh - 1, GLASS, 230, GLASS, 0)
    e.grad(rx + rw - 70, ry + 1, rx + rw - 1 / 3, ry + rh - 1, GLASS, 0, GLASS, 230)
    triangle(e, centre, ry + 1.5, 5, True, GOLD)
    triangle(e, centre, ry + rh - 1.5, 5, False, GOLD)
    # sous le repère + chances
    by = HAUT + A_ROUE_H + A_ECART
    ix, iw = M, A_INFO_W
    verre(e, ix, by, iw, BAS - by)
    sous_repere(e, ex, ix + 12, by + 11, iw - 24)
    separateur(e, ix + 12, ix + iw - 12, by + 51)
    e.texte(t("chances"), ix + 12, by + 59, MINI, MUTED, maxw=iw - 24, quoi="chances")
    fin = legende(e, ex, ix + 12, by + 72, iw - 24)
    if fin > BAS - 6:
        e.defauts.append("[%s] légende trop basse" % LANGUE)
    # panneau d'ouverture (comme le panneau d'achat des grades)
    ox = ix + iw + A_ECART
    ow = GW - M - ox
    verre(e, ox, by, ow, BAS - by)
    filet_haut(e, ox, by, ow, ACCENT)
    cx, cw = ox + 10, ow - 20
    e.texte(t("ouverture"), cx, by + 11, MINI, MUTED, maxw=cw)
    ly = by + 21
    e.rect(cx, ly, cx + cw, ly + A_LIGNE_H, WHITE, 10)
    e.border(cx, ly, cw, A_LIGNE_H, WHITE, 30)
    cle(e, ex, cx + 4, ly + 3, 22, A_LIGNE_H - 6)
    nw = e.d("×" + nombre(cles), cx + cw - 7, ly + A_LIGNE_H / 2, CHIFFRE, WHITE if cles else DANGER)
    e.m(ex.nom_cle(), cx + 30, ly + A_LIGNE_H / 2 - 4.2, TEXTE_GRAS, WHITE, maxw=cw - 30 - nw - 12, quoi="nom de la clé")
    if cles > 0:
        e.m(t("une_cle"), cx + 30, ly + A_LIGNE_H / 2 + 5.2, PETIT, MUTED, maxw=cw - 30 - nw - 12, quoi="1 clé par ouverture")
    else:
        e.m(t("aucune", ex.nom_cle()), cx + 30, ly + A_LIGNE_H / 2 + 5.2, PETIT_GRAS, DANGER, maxw=cw - 30 - nw - 12, quoi="aucune clé")
    yy = ly + A_LIGNE_H + 10
    bh = BOUTON_H
    bty = BAS - 10 - bh
    if cles > 0:
        e.m(t("apres"), cx, yy, PETIT, MUTED, maxw=cw - 50)
        e.d(t("n_cles", nombre(cles - 1)), cx + cw, yy, PETIT_GRAS, mix(ex.couleur, WHITE, 0.35))
        bouton(e, cx, bty, cw, bh)
        e.c(t("gui.heralizcrates.case.open_key"), cx + cw / 2, bty + bh / 2, BOUTON, WHITE, maxw=cw - 8, quoi="bouton ouvrir")
        if yy + 6 > bty - 4:
            e.defauts.append("[%s] panneau d'ouverture trop rempli" % LANGUE)
    else:
        if ex.vendue:
            # vers la boutique (/boutique, onglet Clés) : seulement pour les caisses dont la boutique vend la clé
            sy = bty - 6 - 20
            e.grad(cx, sy, cx + cw, sy + 20, GOLD, 40, GOLD, 12)
            e.border(cx, sy, cw, 20, GOLD, 120)
            e.c("+ " + t("acheter_cles"), cx + cw / 2, sy + 10, BOUTON, mix(GOLD, WHITE, 0.3), maxw=cw - 8, quoi="acheter des clés")
            if ly + A_LIGNE_H > sy - 4:
                e.defauts.append("[%s] panneau sans clé trop rempli" % LANGUE)
        bouton(e, cx, bty, cw, bh, actif=False)
        e.c(t("gui.heralizcrates.case.need_key"), cx + cw / 2, bty + bh / 2, BOUTON, MUTED, maxw=cw - 8, quoi="bouton sans clé")
    if gain:
        carte_gain(e, ex, cles)
    else:
        touches = TOUCHES_OUVRIR if cles > 0 else TOUCHES_OUVRIR[1:]
        pied(e, touches, t("gui.heralizcrates.case.help.keys"))
    return e


# ================================================================== A « Vitrine » : onglet Contenu
def ecran_a_contenu(ex):
    e = Ecran(FOND)
    entete(e, ex, 1, ex.cles)
    fx, fw = M, C_FILTRE_W
    verre(e, fx, HAUT, fw, BAS - HAUT)
    e.texte(t("raretes"), fx + 10, HAUT + 11, MINI, MUTED, maxw=fw - 20)
    lignes = [(None, len(ex.objets), 100.0)] + ex.chances()
    y = HAUT + 22
    for k, (r, n, p) in enumerate(lignes):
        s = k == 0
        h = C_FILTRE_LIGNE - 3
        if s:
            e.grad(fx + 6, y, fx + fw - 6, y + h, ACCENT, 60, ACCENT, 14)
            e.border(fx + 6, y, fw - 12, h, ACCENT, 140)
        else:
            e.rect(fx + 6, y, fx + fw - 6, y + h, WHITE, 8)
        col = WHITE if r is None else COUL_RARETE[r]
        if r is not None:
            e.disc(fx + 14, y + h / 2, 2.6, col, 255)
        lx = fx + 22
        pw = e.d(pct(p), fx + fw - 12, y + h / 2, PETIT_GRAS, mix(col, WHITE, 0.25) if r else OPTION)
        e.m(t("tout") if r is None else nom_rarete(r), lx, y + h / 2 - 3.8, TEXTE_GRAS if s else TEXTE, WHITE if s else LIGHT,
            maxw=fx + fw - 12 - pw - 4 - lx, quoi="filtre")
        e.m(t("n_objets", n), lx, y + h / 2 + 4.6, PETIT, MUTED, maxw=fx + fw - 12 - pw - 4 - lx, quoi="nombre d'objets")
        y += C_FILTRE_LIGNE
    if y > BAS - 4:
        e.defauts.append("[%s] colonne des raretés trop remplie" % LANGUE)
    gx = fx + fw + A_ECART
    gw = GW - M - gx - 8
    cw = (gw - (C_COLS - 1) * C_GAP) / C_COLS
    objets = ex.tries()
    rangs = int((BAS - HAUT + C_GAP) // (C_CARTE_H + C_GAP))
    for i, o in enumerate(objets[:C_COLS * rangs]):
        x = gx + (i % C_COLS) * (cw + C_GAP)
        y = HAUT + (i // C_COLS) * (C_CARTE_H + C_GAP)
        col = o.couleur()
        survol = i == 1
        e.rect(x, y, x + cw, y + C_CARTE_H, CARTE, 235)
        e.grad(x, y, x + cw, y + C_CARTE_H * 0.6, col, 60 if survol else 36, col, 0, vertical=True)
        e.glow(x + cw / 2, y + 24, cw * 0.4, 18, col, 70)
        e.border(x, y, cw, C_CARTE_H, col if survol else WHITE, 200 if survol else 26, 2 / 3 if survol else 1 / 3)
        if survol:
            filet_haut(e, x, y, cw, mix(col, WHITE, 0.3))
        e.image(o.icone(), x + cw / 2 - C_ICONE / 2, y + 24 - C_ICONE / 2, C_ICONE, C_ICONE, lisse=False)
        ls = e.lignes(o.nom(), PETIT, cw - 8)
        if len(ls) > 2:
            ls = [ls[0], e.coupe(" ".join(ls[1:]), PETIT, cw - 8)]
        for k, l in enumerate(ls):
            e.c(l, x + cw / 2, y + 46 + k * 8.5 + (4.25 if len(ls) == 1 else 0), PETIT, LIGHT, maxw=cw - 8, quoi="nom (contenu)")
        e.c(pct(o.chance), x + cw / 2, y + 66, PETIT_GRAS, mix(col, WHITE, 0.25), maxw=cw - 8)
        e.rect(x + cw * 0.2, y + C_CARTE_H - 4, x + cw * 0.8, y + C_CARTE_H - 4 + 4 / 3, col, 255)
    # barre de défilement
    sx = GW - M - 3
    e.rect(sx, HAUT, sx + 2, BAS, WHITE, 18)
    part = (C_COLS * rangs) / len(objets)
    e.rect(sx, HAUT, sx + 2, HAUT + (BAS - HAUT) * min(1, part), WHITE, 110)
    pied(e, [("gui.heralizcrates.case.touche.molette", "action.defiler"), ("touche.tab", "onglet.ouvrir"),
             ("gui.heralizcrates.case.touche.echap", "gui.heralizcrates.case.close")], t("note.contenu"))
    return e


# ================================================================== B « Scène »
def ecran_b(ex):
    e = Ecran(FOND)
    col = ex.couleur
    top = CLES_Y
    # --- la caisse
    hx, hw, hh = M, B_HERO_W, BAS - top
    e.rect(hx, top, hx + hw, BAS, GLASS, 195)
    e.grad(hx, top, hx + hw, top + 190, col, 58, col, 0, vertical=True)
    e.border(hx, top, hw, hh, WHITE, 26)
    filet_haut(e, hx, top, hw, col)
    e.texte(t("gui.heralizcrates.case.kind.items"), hx + 10, top + 11, MINI, mix(col, WHITE, 0.45), 230, maxw=hw - 20, quoi="genre")
    ccx, ccy = hx + hw / 2, top + 82
    e.glow(ccx, ccy, 86, 66, col, 85)
    e.disc(ccx, ccy + 4, 60, col, 55, anneau=0.5)
    e.disc(ccx, ccy + 4, 46, col, 32, anneau=0.5)
    e.glow(ccx, top + 134, 56, 6, 0, 160)
    e.ajuster(ex.caisse_img, hx + 22, top + 24, hw - 44, 112)
    nom = ex.nom_caisse().upper()
    p = NOM_GRAND if e.largeur(nom, NOM_GRAND) <= hw - 16 else (True, 11, 1.1)
    e.c(nom, ccx, top + 150, p, WHITE, maxw=hw - 16, quoi="nom de la caisse")
    e.c(t("n_objets", len(ex.objets)), ccx, top + 163, PETIT, OPTION, maxw=hw - 16)
    separateur(e, hx + 12, hx + hw - 12, top + 174)
    # clés
    ky = top + 182
    e.rect(hx + 10, ky, hx + hw - 10, ky + 36, col, 22)
    e.border(hx + 10, ky, hw - 20, 36, col, 110)
    cle(e, ex, hx + 14, ky + 3, 30, 30)
    e.texte(t("tes_cles"), hx + 50, ky + 8, MINI, mix(col, WHITE, 0.45), maxw=hw - 60 - (52 if ex.vendue else 0), quoi="tes clés (B)")
    e.texte(nombre(ex.cles), hx + 50, ky + 18, CHIFFRE_GRAND, WHITE)
    if ex.vendue:
        bw = 48
        bx = hx + hw - 14 - bw
        e.rect(bx, ky + 11, bx + bw, ky + 25, col, 40)
        e.border(bx, ky + 11, bw, 14, col, 160)
        e.c("+ " + t("acheter"), bx + bw / 2, ky + 18, MICRO, WHITE, maxw=bw - 6, quoi="acheter (B)")
    # l'objet le plus rare de la caisse (le gros lot)
    rare = ex.tries()[0]
    ry = ky + 44
    e.texte(t("plus_rare"), hx + 12, ry, MINI, MUTED, maxw=hw - 24, quoi="le plus rare")
    ry += 9
    rc = rare.couleur()
    e.rect(hx + 10, ry, hx + hw - 10, ry + 24, rc, 22)
    e.border(hx + 10, ry, hw - 20, 24, rc, 110)
    e.image(rare.icone(), hx + 14, ry + 3, 18, 18, lisse=False)
    ppw = e.d(pct(rare.chance), hx + hw - 16, ry + 12, PETIT_GRAS, mix(rc, WHITE, 0.25))
    e.m(rare.nom(), hx + 38, ry + 12, TEXTE_GRAS, WHITE, maxw=hw - 20 - 28 - ppw - 12, quoi="nom du plus rare")
    bh = 24
    bty = BAS - 10 - bh
    if ry + 24 + 6 > bty - 14:
        e.defauts.append("[%s] colonne de la caisse trop remplie" % LANGUE)
    e.c(t("apres") + " · " + t("n_cles", nombre(ex.cles - 1)), ccx, bty - 9, PETIT, MUTED, maxw=hw - 20, quoi="après (B)")
    bouton(e, hx + 10, bty, hw - 20, bh)
    e.c(t("gui.heralizcrates.case.open_key"), ccx, bty + bh / 2, BOUTON, WHITE, maxw=hw - 28, quoi="bouton ouvrir (B)")
    # --- roue verticale
    rx = hx + hw + A_ECART
    rw = B_ROUE_W
    verre(e, rx, top, rw, hh, 175)
    rcx, rcy = rx + rw / 2, top + hh / 2
    cartes = []
    for i, o in enumerate(ex.bande):
        d = (i - 7) * B_STEP
        s = 1 / (1 + B_RETRAIT * abs(d) / B_STEP)
        if abs(d) - B_CARTE * s / 2 > hh / 2 - 2:
            continue
        cartes.append((abs(d), o, d, s))
    for _ad, o, d, s in sorted(cartes, key=lambda c: -c[0]):
        fondu = min(1, max(0, (abs(d) - B_STEP * 0.8) / (hh / 2 - B_STEP * 0.8)))
        carte_objet(e, o, rcx, rcy + d, B_CARTE * s, B_CARTE * s, B_ICONE * s, 1.0 if d == 0 else 0.0, fondu * 0.75)
    e.grad(rx + 1, top + 1 / 3, rx + rw - 1, top + 60, GLASS, 235, GLASS, 0, vertical=True)
    e.grad(rx + 1, BAS - 60, rx + rw - 1, BAS - 1 / 3, GLASS, 0, GLASS, 235, vertical=True)
    triangle_cote(e, rx + 1.5, rcy, 5, True, GOLD)
    triangle_cote(e, rx + rw - 1.5, rcy, 5, False, GOLD)
    # --- objet, chances, contenu
    px = rx + rw + A_ECART
    pw = GW - M - px
    verre(e, px, top, pw, hh)
    filet_haut(e, px, top, pw, ex.repere.couleur())
    e.grad(px, top, px + pw, top + 60, ex.repere.couleur(), 34, ex.repere.couleur(), 0, vertical=True)
    cx, cw = px + 12, pw - 24
    sous_repere(e, ex, cx, top + 11, cw)
    separateur(e, cx, cx + cw, top + 51)
    e.texte(t("chances"), cx, top + 59, MINI, MUTED, maxw=cw, quoi="chances (B)")
    ch = ex.chances()
    pmax = max(p for _r, _n, p in ch)
    y = top + 74
    for r, n, p in ch:
        c = COUL_RARETE[r]
        e.disc(cx + 2.4, y, 2.4, c, 255)
        pw_ = e.d(pct(p), cx + cw, y, PETIT_GRAS, mix(c, WHITE, 0.25))
        bx0, bx1 = cx + cw * 0.52, cx + cw - 34
        e.m(nom_rarete(r), cx + 8, y, TEXTE, LIGHT, maxw=bx0 - cx - 8 - 4, quoi="rareté (B)")
        e.rect(bx0, y - 1.5, bx1, y + 1.5, WHITE, 16)
        e.rect(bx0, y - 1.5, bx0 + max(1.5, (bx1 - bx0) * (p / pmax) ** 0.5), y + 1.5, c, 255)
        if pw_ > 30:
            e.defauts.append("[%s] pourcentage trop large (B)" % LANGUE)
        y += B_LIGNE_CHANCE
    gy0 = y + 2
    separateur(e, cx, cx + cw, gy0)
    e.texte(t("contenu_n", len(ex.objets)), cx, gy0 + 8, MINI, MUTED, maxw=cw, quoi="contenu (B)")
    gw = B_GRILLE_COLS * B_CELLULE + (B_GRILLE_COLS - 1) * B_GRILLE_GAP
    if gw > cw:
        e.defauts.append("grille B trop large (%.1f > %.1f)" % (gw, cw))
    gx, gy = cx + (cw - gw) / 2, gy0 + 20
    for i, o in enumerate(ex.tries()[:B_GRILLE_COLS * B_GRILLE_RANGS]):
        x = gx + (i % B_GRILLE_COLS) * (B_CELLULE + B_GRILLE_GAP)
        yy = gy + (i // B_GRILLE_COLS) * (B_CELLULE + B_GRILLE_GAP)
        c = o.couleur()
        e.rect(x, yy, x + B_CELLULE, yy + B_CELLULE, CARTE, 235)
        e.grad(x, yy, x + B_CELLULE, yy + B_CELLULE, c, 40, c, 0, vertical=True)
        e.border(x, yy, B_CELLULE, B_CELLULE, WHITE, 24)
        e.image(o.icone(), x + 5, yy + 4, B_CELLULE - 10, B_CELLULE - 10, lisse=False)
        e.rect(x + 6, yy + B_CELLULE - 3, x + B_CELLULE - 6, yy + B_CELLULE - 3 + 4 / 3, c, 255)
    bas_grille = gy + B_GRILLE_RANGS * (B_CELLULE + B_GRILLE_GAP) - B_GRILLE_GAP
    if bas_grille > BAS - 6:
        e.defauts.append("grille B trop basse (%.1f)" % bas_grille)
    triangle(e, cx + cw / 2, bas_grille + 6.5, 3, True, WHITE, 110)
    pied(e, [("gui.heralizcrates.case.touche.espace", "gui.heralizcrates.case.open"), ("gui.heralizcrates.case.touche.molette", "gui.heralizcrates.case.action.contenu"),
             ("gui.heralizcrates.case.touche.echap", "gui.heralizcrates.case.close")], t("gui.heralizcrates.case.help.keys"))
    return e


# ================================================================== carte du gain
def carte_gain(e, ex, cles):
    o = ex.gain
    col = o.couleur()
    e.rect(0, 0, GW, GH, NIGHT, 175)
    x, y = (GW - G_W) / 2, (GH - G_H) / 2 - 6
    e.glow(GW / 2, y + 90, 200, 140, col, 50)
    e.rect(x, y, x + G_W, y + G_H, GLASS, 238)
    e.grad(x, y, x + G_W, y + 170, col, 80, col, 0, vertical=True)
    icy = y + 86
    for k in range(40):
        a = k / 40 * 2 * math.pi
        r0, r1 = 44, 82 if k % 2 == 0 else 66
        e.trait([(GW / 2 + r0 * math.sin(a), icy - r0 * math.cos(a)), (GW / 2 + r1 * math.sin(a), icy - r1 * math.cos(a))], 0.6, col, 60)
    e.glow(GW / 2, icy, 58, 58, col, 110)
    e.disc(GW / 2, icy, 46, col, 70, anneau=0.5)
    e.border(x, y, G_W, G_H, col, 200, 2 / 3)
    filet_haut(e, x, y, G_W, mix(col, WHITE, 0.3))
    e.c(t("gui.heralizcrates.case.won"), GW / 2, y + 14, MINI, mix(col, WHITE, 0.45), maxw=G_W - 20, quoi="tu as obtenu")
    e.glow(GW / 2, icy + 38, 34, 4, 0, 150)
    e.image(o.icone(), GW / 2 - G_ICONE / 2, icy - G_ICONE / 2, G_ICONE, G_ICONE, lisse=False)
    puce_pleine(e, nom_rarete(o.rarete), GW / 2, y + 140, col)
    ls = e.lignes(o.nom(), NOM, G_W - 24)
    if len(ls) > 2:
        e.defauts.append("[%s] nom du gain sur plus de 2 lignes" % LANGUE)
    for k, l in enumerate(ls):
        e.c(l, GW / 2, y + 157 + k * 13, NOM, WHITE, maxw=G_W - 24, quoi="nom du gain")
    ny = y + 157 + len(ls) * 13 + 2
    e.c(t("ajoute") + " · " + t("chance", pct(o.chance)), GW / 2, ny, PETIT, OPTION, maxw=G_W - 24, quoi="ajouté")
    separateur(e, x + 14, x + G_W - 14, y + 186)
    e.c(t("reste", nombre(cles - 1)), GW / 2, y + 196, PETIT, MUTED, maxw=G_W - 24, quoi="il te reste")
    bh = BOUTON_H
    bw = (G_W - 24 - 8) / 2
    by = y + G_H - 12 - bh
    if ny + 6 > y + 186 or y + 202 > by - 6:
        e.defauts.append("[%s] carte du gain trop remplie" % LANGUE)
    bouton(e, x + 12, by, bw, bh)
    e.c(t("gui.heralizcrates.case.open_key"), x + 12 + bw / 2, by + bh / 2, BOUTON, WHITE, maxw=bw - 8, quoi="encore")
    bouton(e, x + 12 + bw + 8, by, bw, bh, actif=False)
    e.c(t("gui.heralizcrates.case.close"), x + 12 + bw + 8 + bw / 2, by + bh / 2, BOUTON, OPTION, maxw=bw - 8, quoi="fermer")
    pied(e, [("gui.heralizcrates.case.touche.espace", "action.encore"), ("gui.heralizcrates.case.touche.echap", "gui.heralizcrates.case.close")], None)


FOND = None


def main():
    global POL, TXT, LANGUE, FOND
    ap = argparse.ArgumentParser()
    ap.add_argument("dossier", nargs="?", default=str(SORTIE))
    ap.add_argument("--copie", help="autre dossier où recopier les images")
    ap.add_argument("--caisse", default="camo")
    ap.add_argument("--controle", action="store_true", help="contrôle seulement, aucune image écrite")
    a = ap.parse_args()
    POL = Polices()
    FOND = fond()
    sortie = Path(a.dossier)
    sortie.mkdir(parents=True, exist_ok=True)
    ecrites = []
    for lang in ["fr_fr", "en_us", "ru_ru"]:
        LANGUE = lang
        TXT = json.loads((LANGS / (lang + ".json")).read_text(encoding="utf-8"))
        ex = Exemple(a.caisse, 12, "camo_orion", "skinx_seraphin")
        suf = "" if lang == "fr_fr" else "_" + lang[:2]

        def ch(nom, toutes=True):
            if a.controle:
                return []
            out = [(sortie / ("%s%s_1920.png" % (nom, suf)), 3)]
            if toutes:
                out.append((sortie / ("%s%s_1280.png" % (nom, suf)), 2))
            return out

        fr = lang == "fr_fr"
        for chemins, e in [(ch("1_A_vitrine"), ecran_a(None, ex)),
                           (ch("2_B_scene"), ecran_b(ex))]:
            e.sauver(chemins)
            ecrites += [c for c, _ in chemins]
        autres = [("3_A_sans_cle", ecran_a(None, ex, cles=0)), ("4_A_contenu", ecran_a_contenu(ex)),
                  ("5_gain", ecran_a(None, ex, gain=True))]
        for nom, e in autres:
            chemins = ch(nom) if fr else []
            e.sauver(chemins)
            ecrites += [c for c, _ in chemins]
    if a.copie and ecrites:
        Path(a.copie).mkdir(parents=True, exist_ok=True)
        for c in ecrites:
            shutil.copy(c, Path(a.copie) / c.name)
        print("copié dans", a.copie)
    print("aucun texte ne dépasse (fr, en, ru)")


if __name__ == "__main__":
    main()
