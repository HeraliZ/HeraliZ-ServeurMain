"""Boutique en jeu (/boutique, maquette validée le 23/10) : textures de l'écran et aperçus hors du jeu.

    python mods/heraliz-grades/outils/apercu_boutique.py --textures        écrit les textures de l'écran dans le mod
    python mods/heraliz-grades/outils/apercu_boutique.py --cles            rend les clés des caisses (vrai modèle) pour l'aperçu
    python mods/heraliz-grades/outils/apercu_boutique.py DOSSIER [--langue fr_fr|en_us|ru_ru|toutes] [--onglet N]

Aperçu : mêmes mesures que client/EcranBoutique (canevas 640 x 360 unités, rendu à l'échelle 3 = 1920 x 1080), mêmes
polices (DM Sans, Onest pour le cyrillique), mêmes textes (fichiers de langue du mod), mêmes articles
(serveur/config/heraliz_grades/boutique.json). Chaque texte est mesuré avec la vraie police : un texte qui dépasse son
cadre, dans N'IMPORTE QUELLE des trois langues, arrête le script (un texte trop long se corrige dans les langues ou dans
les mesures, ici ET dans EcranBoutique). Les mesures sont en tête de EcranBoutique.java et recopiées ici.

Textures (--textures) : personnages des grades et images « sur mesure » reprises du site (site/theme/heraliz/assets/img),
recadrées, jamais agrandies ; pièce de PB et logo Discord dessinés ici. En jeu, les clés sont les vrais objets de
HeraliZ Caisses, dessinés par le jeu ; pour l'aperçu seulement, --cles les rend avec le moteur de
mods/heraliz-crates/outils/ApercuCaisse.java (JOML : variable JOML_JAR, sinon téléchargé dans ~/.cache/heraliz, jamais
dans le dépôt). Numpy et Pillow.
"""
import argparse
import json
import os
import re
import subprocess
import sys
import tempfile
import urllib.request
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont
from fontTools.ttLib import TTFont

RACINE = Path(__file__).resolve().parents[3]
MOD = RACINE / "mods" / "heraliz-grades"
ASSETS = MOD / "src" / "client" / "resources" / "assets" / "heraliz_grades"
TEX = ASSETS / "textures" / "gui" / "boutique"
FONTS = ASSETS / "font"
SITE = RACINE / "site" / "theme" / "heraliz" / "assets" / "img"
CACHE = Path.home() / ".cache" / "heraliz"
CLES = CACHE / "cles_boutique"
K = 6  # pixels par unité pendant le dessin (réduit de moitié : échelle 3)
GW, GH = 640, 360

# ---------------------------------------------------------------- couleurs (comme EcranBoutique)
ACCENT, WHITE, LIGHT, OPTION, MUTED, FAINT, PINK = 0xE3201B, 0xFFFFFF, 0xECEEF2, 0xC3C8D0, 0x8F95A0, 0x5A5F69, 0xF08A86
NIGHT, GLASS, GOLD, DISCORD, OK, DANGER = 0x050608, 0x0D0F14, 0xF2C14E, 0x5865F2, 0x6FD08C, 0xFF8A80
SM = [("camo", "skin", 0xB57BFF, 0.45), ("finisher", "finisher", 0xFF5A4E, 0.40), ("emote", "emote", 0x7FA8FF, 0.20)]
SM_IW, SM_IH = 180.67, 112  # image d'une carte « sur mesure » (unités)

# avantages par grade : (clé, argument) ; mêmes listes que EcranBoutique.AVANTAGES
AVANTAGES = {
    "rescape": [("coffre_vip", "3"), ("kit", None), ("tp", "10"), ("casino", None), ("zombies", None)],
    "traqueur": [("coffre_vip", "6"), ("kit", None), ("nick", None), ("tp", "20"), ("casino", None), ("zombies", None)],
    "roi_de_la_zone": [("coffre_vip", "9"), ("kit", None), ("nick", None), ("chat", None), ("tp", "30"), ("casino", None), ("zombies", None)],
}
AVANTAGES_BETA = [("vie", None), ("cles", "10"), ("sur_mesure", None)] + AVANTAGES["roi_de_la_zone"]
RANG = {"rescape": 1, "traqueur": 2, "roi_de_la_zone": 3}
GRADES = ["rescape", "traqueur", "roi_de_la_zone"]
CAISSES = ["gemme", "camo", "spectacle"]


def rgb(c):
    return (c >> 16 & 255, c >> 8 & 255, c & 255)


def mix(a, b, t):
    ra, rb = rgb(a), rgb(b)
    return sum(int(ra[i] + (rb[i] - ra[i]) * t) << (16 - 8 * i) for i in range(3))


# ---------------------------------------------------------------- textures du mod
def recadrer(im):
    return im.crop(im.getbbox())


def textures():
    TEX.mkdir(parents=True, exist_ok=True)
    for g in GRADES + ["beta"]:
        im = recadrer(Image.open(SITE / "grades" / (g + ".webp")).convert("RGBA"))
        im.save(TEX / ("grade_%s.png" % g), optimize=True)
        print("écrit", TEX / ("grade_%s.png" % g), im.size)
    for nom, fichier, _c, fy in SM:
        im = Image.open(SITE / "avantages" / (fichier + ".webp")).convert("RGB")
        r = SM_IW / SM_IH
        w = im.width
        h = int(round(w / r))
        y0 = int(round((im.height - h) * fy))
        im.crop((0, y0, w, y0 + h)).save(TEX / ("sur_mesure_%s.png" % nom), optimize=True)
        print("écrit", TEX / ("sur_mesure_%s.png" % nom), (w, h))
    pb = recadrer(Image.open(SITE / "pb" / "petit.webp").convert("RGBA"))
    pb.thumbnail((160, 160), Image.LANCZOS)
    pb.save(TEX / "pb.png", optimize=True)
    # pièce de PB (prix) : disque or, liseré sombre, anneau, « P »
    n, s = 96, 8
    big = Image.new("RGBA", (n * s, n * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    c = n * s / 2
    d.ellipse([0, 0, n * s - 1, n * s - 1], fill=rgb(0x8A6418) + (255,))
    r1 = c - n * s * 0.07
    d.ellipse([c - r1, c - r1 - s * 1.5, c + r1, c + r1 - s * 1.5], fill=rgb(GOLD) + (255,))
    r2 = c * 0.62
    d.ellipse([c - r2, c - r2, c + r2, c + r2], outline=rgb(0xC9962E) + (255,), width=int(n * s * 0.05))
    f = ImageFont.truetype(str(FONTS / "dmsans_bold.ttf"), int(n * s * 0.5))
    bb = d.textbbox((0, 0), "P", font=f)
    d.text((c - (bb[0] + bb[2]) / 2, c - (bb[1] + bb[3]) / 2), "P", font=f, fill=rgb(0x7A5410) + (255,))
    big.resize((n, n), Image.LANCZOS).save(TEX / "piece.png", optimize=True)
    # logo Discord simplifié (blanc, posé sur un disque de la couleur de Discord par l'écran)
    n = 128
    big = Image.new("RGBA", (n * s, n * s), (0, 0, 0, 0))
    d = ImageDraw.Draw(big)
    u = n * s / 26.0
    cx = cy = n * s / 2

    def P(x, y):
        return (cx + x * u, cy + y * u)
    d.polygon([P(-7, -4), P(-3, -6.5), P(3, -6.5), P(7, -4), P(8.2, 4), P(4.5, 6.5), P(2.5, 4.5), P(-2.5, 4.5), P(-4.5, 6.5), P(-8.2, 4)],
              fill=(255, 255, 255, 255))
    for ex in (-3, 3):
        d.ellipse([cx + (ex - 1.6) * u, cy + (0.5 - 1.6) * u, cx + (ex + 1.6) * u, cy + (0.5 + 1.6) * u], fill=(0, 0, 0, 0))
    big.resize((n, n), Image.LANCZOS).save(TEX / "discord.png", optimize=True)
    print("écrit pièce, pb, discord dans", TEX)


# ---------------------------------------------------------------- clés (aperçu seulement)
def joml():
    if os.environ.get("JOML_JAR"):
        return os.environ["JOML_JAR"]
    j = CACHE / "joml-1.10.5.jar"
    if not j.exists():
        CACHE.mkdir(parents=True, exist_ok=True)
        urllib.request.urlretrieve("https://repo1.maven.org/maven2/org/joml/joml/1.10.5/joml-1.10.5.jar", j)
    return str(j)


def rendre_cles():
    """Rend les clés avec le moteur logiciel d'ApercuCaisse (sa méthode renderKey : vrai modèle, vraies textures)."""
    crates = RACINE / "mods" / "heraliz-crates"
    src = (crates / "outils" / "ApercuCaisse.java").read_text(encoding="utf-8")
    rep = [("public final class ApercuCaisse", "public final class ApercuCle"), ("d.render(s, list);", "d.renderKey(s, list);"),
           ("camPos.set(8 + (float) (Math.sin(yr) * Math.cos(pr) * dist), lookY + (float) (Math.sin(pr) * dist), 8 + (float) (Math.cos(yr) * Math.cos(pr) * dist));\n"
            "        view.setLookAt(camPos.x, camPos.y, camPos.z, 8, lookY, 8, 0, 1, 0);",
            "camPos.set((float) (Math.sin(yr) * Math.cos(pr) * dist), lookY + (float) (Math.sin(pr) * dist), (float) (Math.cos(yr) * Math.cos(pr) * dist));\n"
            "        view.setLookAt(camPos.x, camPos.y, camPos.z, 0, lookY, 0, 0, 1, 0);"),
           ("int ss = 2;", "int ss = 4;"), ("col[i] = 0.05f - 0.03f * k;", "col[i] = 0;"), ("col[i + 1] = 0.045f - 0.03f * k;", "col[i + 1] = 0;"),
           ("col[i + 2] = 0.08f - 0.05f * k;", "col[i + 2] = 0;")]
    for a, b in rep:
        if a not in src:
            sys.exit("ApercuCaisse.java a changé : adapter rendre_cles()")
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
    s = crates / "src"
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    CLES.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        f = Path(tmp) / "ApercuCle.java"
        f.write_text(src, encoding="utf-8")
        sources = [str(p) for d in ("mesh", "scene") for p in (s / "client/java/fr/heraliz/crates/client/render" / d).rglob("*.java")]
        sources += [str(s / "commun/java/fr/heraliz/crates/registry/Snd.java"), str(s / "commun/java/fr/heraliz/crates/block/CrateAnim.java"), str(f)]
        out = Path(tmp) / "out"
        subprocess.run(["javac", "-nowarn", "-encoding", "UTF-8", "-d", str(out), "-cp", joml()] + sources, check=True, env=env)
        res = s / "client/resources/assets/heralizcrates"
        for c in CAISSES:
            subprocess.run(["java", "-cp", os.pathsep.join([str(out), joml()]), "ApercuCle", str(res), str(CLES / (c + ".png")), c, "-1",
                            "22", "6", "52", "480", "720", "8.6"], check=True, env=env)
            print("écrit", CLES / (c + ".png"))


# ---------------------------------------------------------------- polices et textes
class Polices:
    def __init__(self):
        self.cmap = TTFont(str(FONTS / "dmsans_bold.ttf")).getBestCmap()
        self.cache = {}

    def f(self, gras, taille, cyr):
        k = (gras, taille, cyr)
        if k not in self.cache:
            nom = ("cyrillique_" if cyr else "dmsans_") + ("bold" if gras else "medium") + ".ttf"
            # taille = celle de l'em comme dans la maquette (le fichier .json du jeu porte taille × 1,302, voir EcranBoutique)
            self.cache[k] = ImageFont.truetype(str(FONTS / nom), int(round(taille * K * (1.021 if cyr else 1))))
        return self.cache[k]

    def morceaux(self, s):
        """Découpe le texte en morceaux d'une même police (DM Sans, sinon Onest pour le cyrillique)."""
        out = []
        for ch in s:
            cyr = ord(ch) not in self.cmap
            if out and out[-1][1] == cyr:
                out[-1][0] += ch
            else:
                out.append([ch, cyr])
        return out


POL = None
TXT = {}
LANGUE = "fr_fr"


def t(cle, *args):
    s = TXT.get("gui.heraliz_grades.boutique." + cle, TXT.get(cle, cle))
    if args:
        s = re.sub(r"%(\d+\$)?s", lambda m, it=iter(args): str(next(it)), s)
    return s.replace("%%", "%")


def nombre(v):
    s = "{:,}".format(int(v))
    return s.replace(",", " ") if LANGUE.startswith("fr") else s


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
        arr[..., 3] = (np.clip(1 - d, 0, 1) * a).astype(np.uint8)  # même chute que CreatorUi.glow (linéaire)
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

    def coche(self, x, y, s, e, c, a=255):
        self.trait([(x, y + s * 0.55), (x + s * 0.38, y + s * 0.9), (x + s, y + s * 0.12)], e, c, a)

    def image(self, im, x, y, w, h, alpha=1.0):
        W, H = int(round(w * K)), int(round(h * K))
        if W / 2 > im.width * 1.03 or H / 2 > im.height * 1.03:
            self.defauts.append("image agrandie à l'échelle 3 (%dx%d -> %dx%d)" % (im.width, im.height, W // 2, H // 2))
        r = im.convert("RGBA").resize((W, H), Image.LANCZOS)
        if alpha < 1:
            arr = np.array(r)
            arr[..., 3] = (arr[..., 3] * alpha).astype(np.uint8)
            r = Image.fromarray(arr, "RGBA")
        self.im.alpha_composite(r, (int(round(x * K)), int(round(y * K))))

    def ajuster(self, im, x, y, w, h, bas=True, alpha=1.0):
        s = min(w / im.width, h / im.height)
        iw, ih = im.width * s, im.height * s
        self.image(im, x + (w - iw) / 2, y + (h - ih) if bas else y + (h - ih) / 2, iw, ih, alpha)

    # textes : (gras, taille, espacement), comme les polices bq_* du jeu
    def largeur(self, s, p):
        gras, taille, esp = p
        w = 0
        n = 0
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
        f = POL.f(p[0], p[1], False)
        b = f.getbbox("H")
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

    def sauver(self, chemin):
        if self.defauts:
            print("\n".join(self.defauts))
            sys.exit("%s : %d défaut(s)" % (chemin.name if chemin else "aperçu", len(self.defauts)))
        if chemin is not None:
            self.im.resize((GW * 3, GH * 3), Image.LANCZOS).convert("RGB").save(chemin, optimize=True)
            print("écrit", chemin)


# polices (gras, taille de l'em, espacement) : mêmes que EcranBoutique
TITRE = (True, 13, 1.93)
NOM_GRAND = (True, 13, 1.4)
NOM = (True, 11, 1.3)
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
PB_GRAND = (True, 9, 0)
PB_MOYEN = (True, 7.5, 0)
PB_PETIT = (True, 6.5, 0)

M = 16
HAUT, BAS = 86, 336
SOLDE = 4120


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
    voile(e)
    return e.im


def voile(e):
    at, al = [0, 0.24, 0.46, 0.62, 1], [235, 215, 185, 200, 232]
    for i in range(4):
        e.grad(GW * at[i], 0, GW * at[i + 1], GH, NIGHT, al[i], NIGHT, al[i + 1])
    e.grad(0, 0, GW, GH * 0.3, 0, 90, 0, 0, vertical=True)
    e.grad(0, GH * 0.6, GW, GH, 0, 0, 0, 120, vertical=True)


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


def piece(e, x, y, d):
    e.image(Image.open(TEX / "piece.png"), x, y, d, d)


def prix(e, n, xd, cy, p=TEXTE_GRAS, pp=PB_PETIT, c=GOLD, ico=8.5):
    lab = nombre(n)
    w = ico + 2 + e.largeur(lab, p) + 1.5 + e.largeur("PB", pp)
    x = xd - w
    piece(e, x, cy - ico / 2, ico)
    lw = e.m(lab, x + ico + 2, cy, p, WHITE)
    e.texte("PB", x + ico + 2 + lw + 1.5, cy + e.hcap(p) / 2 - e.hcap(pp), pp, c)
    return w


def puce(e, txt, x, cy, c):
    w = e.largeur(txt, PETIT_GRAS) + 5
    e.rect(x, cy - 4, x + w, cy + 4, c, 50)
    e.border(x, cy - 4, w, 8, c, 140)
    e.c(txt, x + w / 2, cy, PETIT_GRAS, mix(c, WHITE, 0.3))
    return w


# ---------------------------------------------------------------- en-tête, onglets, pied (EcranBoutique.entete / pied)
ONGLETS = ["grades", "beta", "cles", "sur_mesure"]


def entete(e, onglet):
    e.texte(t("titre"), M, 20, TITRE, LIGHT, maxw=200, quoi="titre")
    e.texte(t("sous." + ONGLETS[onglet]), M, 37, TEXTE, MUTED, maxw=430, quoi="sous-titre")
    cw, ch = 158, 30
    cx, cy = GW - M - cw, 14
    e.grad(cx, cy, cx + cw, cy + ch, GOLD, 40, GOLD, 12)
    e.border(cx, cy, cw, ch, GOLD, 110)
    e.glow(cx + 16, cy + ch / 2, 18, 15, GOLD, 60)
    e.ajuster(Image.open(TEX / "pb.png"), cx + 4, cy + 3, 24, 24, bas=False)
    e.texte(t("ton_solde"), cx + 33, cy + 6, MINI, mix(GOLD, WHITE, 0.35), 220, maxw=cw - 33 - 48, quoi="ton solde")
    w = e.texte(nombre(SOLDE), cx + 33, cy + 15.5, CHIFFRE_GRAND, WHITE, maxw=72, quoi="solde")
    e.texte("PB", cx + 33 + w + 2.5, cy + 15.5 + e.hcap(CHIFFRE_GRAND) - e.hcap(PB_GRAND), PB_GRAND, GOLD)
    bx = cx + cw - 44
    e.rect(bx, cy + 5, bx + 1 / 3, cy + ch - 5, GOLD, 70)
    e.rect(bx + 22 - 4.5, cy + 10.5 - 0.67, bx + 22 + 4.5, cy + 10.5 + 0.67, GOLD, 255)
    e.rect(bx + 22 - 0.67, cy + 10.5 - 4.5, bx + 22 + 0.67, cy + 10.5 + 4.5, GOLD, 255)
    e.c(t("recharger"), bx + 22, cy + 22, MICRO, mix(GOLD, WHITE, 0.3), 230, maxw=40, quoi="recharger")
    ty, th, x = 54, 22, M
    for i, o in enumerate(ONGLETS):
        lab = t("onglet." + o)
        w = e.largeur(lab.upper(), ONGLET) + 21.33 + 12
        if i == onglet:
            e.grad(x, ty, x + w * 0.75, ty + th, ACCENT, 51, ACCENT, 10)
            e.grad(x + w * 0.75, ty, x + w, ty + th, ACCENT, 10, ACCENT, 0)
            e.border(x, ty, w, th, ACCENT, 92)
            e.rect(x + 4, ty + th - 1.33, x + w - 4, ty + th, ACCENT, 255)
        else:
            e.rect(x, ty, x + w, ty + th, WHITE, 8)
            e.border(x, ty, w, th, WHITE, 22)
        e.m("%02d" % (i + 1), x + 6.67, ty + th / 2, MINI_NUM, PINK if i == onglet else FAINT)
        e.m(lab, x + 21.33, ty + th / 2, ONGLET, WHITE if i == onglet else OPTION)
        if o == "beta":
            e.disc(x + w - 3.5, ty + 3.5, 2.2, 0xA970FF, 255)
        x += w + 4
    if x - 4 > GW - M - 0.05:
        e.defauts.append("[%s] onglets trop larges (%.1f)" % (LANGUE, x - 4))
    e.rect(M, ty + th + 4, GW - M, ty + th + 4 + 1 / 3, WHITE, 23)


def pied(e, touches, note):
    cy, x = 348, M
    for k, lab in touches:
        kl = t("touche." + k).upper()
        tw = e.largeur(kl, MINI_NUM)
        w = max(9, tw + 4.67)
        e.rect(x, cy - 4.5, x + w, cy + 4.5, WHITE, 30)
        e.border(x, cy - 4.5, w, 9, WHITE, 61)
        e.c(kl, x + w / 2, cy, MINI_NUM, WHITE)
        x += w + 3.5
        x += e.m(t("pied." + lab), x, cy, PETIT, MUTED) + 10
    e.d(t("note." + note), GW - M, cy, PETIT, FAINT, maxw=GW - M - x - 8, quoi="note du pied")


def avantage(cle, arg):
    return t("av." + cle, arg) if arg else t("av." + cle)


def liste_avantages(e, items, x, y, w, c, pas=10):
    """Une coche et le texte (sur deux lignes au plus) ; rend le y suivant."""
    for cle, arg in items:
        ls = e.lignes(avantage(cle, arg), TEXTE, w - 9)
        if len(ls) > 2:
            e.defauts.append("[%s] avantage sur plus de 2 lignes : %s" % (LANGUE, ls))
        e.coche(x, y - 2.2, 4.6, 1.0, c)
        for k, l in enumerate(ls):
            e.m(l, x + 9, y + k * 9, TEXTE, LIGHT, maxw=w - 9, quoi="avantage")
        y += pas + 9 * (len(ls) - 1)
    return y


# ---------------------------------------------------------------- articles
def articles():
    d = json.loads((RACINE / "serveur" / "config" / "heraliz_grades" / "boutique.json").read_text(encoding="utf-8"))
    return d["articles"]


def offres(groupe):
    return sorted([a for a in articles() if a.get("groupe") == groupe], key=lambda a: a.get("quantite", 1))


def couleur(groupe):
    o = offres(groupe)
    return int(o[0]["couleur"].replace("#", ""), 16) if o else 0x6FA8FF


def nom_grade(g):
    return TXT.get("grade.heraliz_grades." + g, g)


def charger(nom):
    return Image.open(TEX / nom).convert("RGBA")


# ======================================================================= 1. Grades
def ecran_grades(sortie, choisi=1, duree=1):
    e = Ecran(FOND)
    entete(e, 0)
    cw, gap = 138, 9
    for i, g in enumerate(GRADES):
        col = couleur(g)
        x, y, h, s = M + i * (cw + gap), HAUT, BAS - HAUT, i == choisi
        e.rect(x, y, x + cw, y + h, GLASS, 205 if s else 180)
        e.grad(x, y, x + cw, y + 160, col, 52 if s else 22, col, 0, vertical=True)
        e.glow(x + cw / 2, y + 112, cw * 0.48, 78, col, 70 if s else 30)
        e.border(x, y, cw, h, col if s else WHITE, 200 if s else 26, 2 / 3 if s else 1 / 3)
        if s:
            filet_haut(e, x, y, cw, col)
        e.m(t("grade"), x + 8, y + 10, MINI, mix(col, WHITE, 0.4), 230, maxw=cw - 40, quoi="grade")
        for k in range(3):
            px = x + cw - 8 - 2.4 - 13 + k * 6.5
            e.poly([(px, y + 7.6), (px + 2.4, y + 10), (px, y + 12.4), (px - 2.4, y + 10)], col if k < RANG[g] else WHITE, 255 if k < RANG[g] else 40)
        e.glow(x + cw / 2, y + 172, 44, 5, 0, 150)
        e.ajuster(charger("grade_%s.png" % g), x + 6, y + 20, cw - 12, 154, alpha=1.0 if s else 0.78)
        e.grad(x + 1 / 3, y + 150, x + cw - 1 / 3, y + 176, GLASS, 0, GLASS, 200, vertical=True)
        e.rect(x + 1 / 3, y + 176, x + cw - 1 / 3, y + h - 1 / 3, GLASS, 200)
        e.c(nom_grade(g), x + cw / 2, y + 186, NOM, col, maxw=cw - 12, quoi="nom de grade")
        e.c(t("resume." + g), x + cw / 2, y + 199, PETIT, OPTION if s else MUTED, maxw=cw - 12, quoi="résumé")
        e.rect(x + 12, y + 209, x + cw - 12, y + 209 + 1 / 3, WHITE, 26)
        e.c(t("a_partir_de"), x + cw / 2, y + 217, MICRO, MUTED, maxw=cw - 12)
        p1 = offres(g)[0]["prix"]
        lab = nombre(p1)
        tw = 11 + e.largeur(lab, CHIFFRE) + 1.5 + e.largeur("PB", PB_MOYEN) + 2 + e.largeur(t("par_mois"), PETIT)
        if tw > cw - 12:
            e.defauts.append("[%s] prix de carte trop large" % LANGUE)
        px0 = x + cw / 2 - tw / 2
        piece(e, px0, y + 229 - 4.5, 9)
        w = e.m(lab, px0 + 11, y + 229, CHIFFRE, WHITE)
        e.texte("PB", px0 + 11 + w + 1.5, y + 229 + e.hcap(CHIFFRE) / 2 - e.hcap(PB_MOYEN), PB_MOYEN, GOLD)
        e.texte(t("par_mois"), px0 + 11 + w + 1.5 + e.largeur("PB", PB_MOYEN) + 2, y + 229 + e.hcap(CHIFFRE) / 2 - e.hcap(PETIT), PETIT, MUTED)
        if s:
            lab = t("choisi").upper()
            bw = e.largeur(lab, MINI) + 14
            bx, by = x + cw / 2 - bw / 2, y + h - 1
            e.rect(bx, by - 5, bx + bw, by + 5, col, 255)
            e.coche(bx + 4, by - 2.6, 4.8, 1.1, 0x10131A)
            e.m(lab, bx + 11, by, MINI, 0x10131A)
    # panneau d'achat
    g = GRADES[choisi]
    col = couleur(g)
    px = M + 3 * cw + 2 * gap + 10
    pw, py, ph = GW - M - px, HAUT, BAS - HAUT
    verre(e, px, py, pw, ph)
    filet_haut(e, px, py, pw, ACCENT)
    cx, cwid = px + 10, pw - 20
    e.texte(t("ton_choix"), cx, py + 10, MINI, MUTED, maxw=cwid)
    nom = nom_grade(g).upper()
    p = NOM_GRAND if e.largeur(nom, NOM_GRAND) <= cwid else NOM
    e.texte(nom, cx, py + 20 + (0 if p == NOM_GRAND else 1), p, col, maxw=cwid, quoi="grade choisi")
    y = liste_avantages(e, AVANTAGES[g], cx, py + 41, cwid, col)
    e.rect(cx, y - 3, cx + cwid, y - 3 + 1 / 3, WHITE, 23)
    y += 3
    e.texte(t("duree"), cx, y, MINI, MUTED, maxw=cwid)
    y += 8
    os_ = offres(g)
    p1 = os_[0]["prix"]
    oh = 24
    for j, o in enumerate(os_):
        s = j == duree
        if s:
            e.grad(cx, y, cx + cwid, y + oh, ACCENT, 60, ACCENT, 14)
            e.border(cx, y, cwid, oh, ACCENT, 140)
        else:
            e.rect(cx, y, cx + cwid, y + oh, WHITE, 10)
            e.border(cx, y, cwid, oh, WHITE, 30)
        rcy = y + oh / 2
        e.disc(cx + 9, rcy, 3.6, WHITE if s else MUTED, 255 if s else 160, anneau=0.75)
        if s:
            e.disc(cx + 9, rcy, 1.9, ACCENT, 255)
        q = o["quantite"]
        sous = None if q <= 1 else t("soit_mois", nombre(round(o["prix"] / q)))
        ly = rcy if sous is None else rcy - 4
        lab = t("mois.1") if q == 1 else t("mois.n", q)
        wp = prix(e, o["prix"], cx + cwid - 6, rcy)
        w = e.m(lab, cx + 17, ly, TEXTE_GRAS, WHITE if s else OPTION, maxw=cwid - 17 - wp - 10, quoi="durée")
        if q > 1:
            pct = round(100 - 100 * o["prix"] / (q * p1))
            cw2 = puce(e, "−%d %%" % pct, cx + 17 + w + 4, ly, OK)
            if 17 + w + 4 + cw2 > cwid - wp - 8:
                e.defauts.append("[%s] puce de remise trop large" % LANGUE)
            e.m(sous, cx + 17, rcy + 5, PETIT, MUTED, maxw=cwid - 17 - wp - 8, quoi="prix par mois")
        y += oh + 3
    y += 1
    choix = os_[duree]
    e.m(t("ton_solde"), cx, y + 4, PETIT, MUTED, maxw=cwid - 50)
    e.d(nombre(SOLDE) + " PB", cx + cwid, y + 4, PETIT_GRAS, OPTION)
    e.m(t("apres_achat"), cx, y + 13, PETIT, MUTED, maxw=cwid - 50)
    e.d(nombre(SOLDE - choix["prix"]) + " PB", cx + cwid, y + 13, PETIT_GRAS, GOLD)
    bh = 22
    by = py + ph - 10 - bh
    if by < y + 18:
        e.defauts.append("[%s] panneau d'achat trop rempli (%.1f)" % (LANGUE, y + 18 - by))
    bouton(e, cx, by, cwid, bh)
    e.c(t("acheter", nombre(choix["prix"])), cx + cwid / 2, by + bh / 2, BOUTON, WHITE, maxw=cwid - 8, quoi="bouton acheter")
    pied(e, [("clic", "choisir"), ("entree", "acheter"), ("echap", "fermer")], "grades")
    e.sauver(sortie)


# ======================================================================= 2. Offre bêta test
def ecran_beta(sortie):
    e = Ecran(FOND)
    entete(e, 1)
    BETA = couleur("beta")
    h = BAS - HAUT
    sx, sw = M, 300
    e.rect(sx, HAUT, sx + sw, BAS, GLASS, 190)
    e.grad(sx, HAUT, sx + sw, BAS, BETA, 70, BETA, 0, vertical=True)
    e.glow(sx + sw / 2, HAUT + 120, 150, 120, BETA, 80)
    e.border(sx, HAUT, sw, h, BETA, 200, 2 / 3)
    filet_haut(e, sx, HAUT, sw, BETA)
    for k in range(36):
        a0 = k / 36 * 2 * np.pi
        cx0, cy0, r = sx + sw / 2, HAUT + 118, 92
        e.trait([(cx0 + r * np.sin(a0), cy0 - r * np.cos(a0)), (cx0 + r * np.sin(a0 + np.pi / 60), cy0 - r * np.cos(a0 + np.pi / 60))], 0.6, BETA, 120)
    e.glow(sx + sw / 2, BAS - 30, 80, 7, 0, 170)
    e.ajuster(charger("grade_beta.png"), sx + 20, HAUT + 28, sw - 40, h - 58)
    e.grad(sx + 1, BAS - 40, sx + sw - 1, BAS - 2 / 3, GLASS, 0, GLASS, 230, vertical=True)
    lab = t("beta.chip").upper()
    rw = e.largeur(lab, MINI) + 10
    e.rect(sx + 10, HAUT + 10, sx + 10 + rw, HAUT + 20, BETA, 255)
    e.m(lab, sx + 15, HAUT + 15, MINI, WHITE)
    e.c("BETA", sx + sw / 2, BAS - 13, (True, 13, 4), WHITE)
    px = sx + sw + 10
    pw = GW - M - px
    verre(e, px, HAUT, pw, h)
    filet_haut(e, px, HAUT, pw, BETA)
    cx, cw = px + 12, pw - 24
    e.texte(t("beta.sur"), cx, HAUT + 11, MINI, mix(BETA, WHITE, 0.35), maxw=cw)
    e.texte(t("beta.titre"), cx, HAUT + 21, NOM_GRAND, WHITE, maxw=cw, quoi="titre beta")
    y = HAUT + 39
    ls = e.lignes(t("beta.texte"), TEXTE, cw)
    if len(ls) > 3:
        e.defauts.append("[%s] texte beta sur plus de 3 lignes" % LANGUE)
    for l in ls:
        e.texte(l, cx, y, TEXTE, OPTION, maxw=cw, quoi="texte beta")
        y += 10
    y += 6
    colw = (cw - 8) / 2
    items = AVANTAGES_BETA
    for r in range(0, len(items), 2):
        hauteurs = []
        for k, it in enumerate(items[r:r + 2]):
            xx = cx + k * (colw + 8)
            yy = liste_avantages(e, [it], xx, y, colw, BETA)
            hauteurs.append(yy - y)
        y += max(hauteurs)
    e.rect(cx, y - 2, cx + cw, y - 2 + 1 / 3, WHITE, 23)
    y += 4
    e.texte(t("beta.vaut"), cx, y, MINI, MUTED, maxw=cw)
    y += 10
    roi = offres("roi_de_la_zone")
    lignes = [("beta.ligne1", roi[0]["prix"]), ("beta.ligne3", roi[-1]["prix"]), ("beta.ligne_an", roi[-1]["prix"] * 4)]
    for cle, p in lignes:
        e.m(t(cle), cx, y, TEXTE, MUTED, maxw=cw - 60, quoi="ce que ça vaut")
        e.d(nombre(p) + " PB", cx + cw, y, TEXTE, MUTED)
        y += 9.5
    y += 1
    pb = offres("beta")[0]["prix"]
    e.rect(cx, y, cx + cw, y + 22, BETA, 30)
    e.border(cx, y, cw, 22, BETA, 140)
    e.m(t("beta.prix"), cx + 8, y + 11, TEXTE_GRAS, WHITE, maxw=cw - 100)
    prix(e, pb, cx + cw - 8, y + 11, p=CHIFFRE, pp=PB_MOYEN, c=mix(BETA, WHITE, 0.4), ico=10)
    y += 22
    bh = 22
    by = BAS - 10 - bh
    assez = SOLDE >= pb
    if not assez:
        e.m(t("manque", nombre(pb - SOLDE)), cx, by - 7, PETIT_GRAS, DANGER, maxw=cw / 2)
        e.d(t("recharger_site"), cx + cw, by - 7, PETIT_GRAS, GOLD, maxw=cw / 2)
    if by - 13 < y:
        e.defauts.append("[%s] panneau beta trop rempli (%.1f)" % (LANGUE, y - by + 13))
    bouton(e, cx, by, cw, bh, actif=assez)
    e.c(t("acheter", nombre(pb)), cx + cw / 2, by + bh / 2, BOUTON, WHITE if assez else MUTED, maxw=cw - 8)
    pied(e, [("clic", "choisir"), ("echap", "fermer")], "beta")
    e.sauver(sortie)


# ======================================================================= 3. Clés
def ecran_cles(sortie, choix=(1, 0, 1)):
    e = Ecran(FOND)
    entete(e, 2)
    gap = 9
    cw = (GW - 2 * M - 2 * gap) / 3
    for i, g in enumerate(CAISSES):
        col = couleur(g)
        os_ = offres(g)
        x = M + i * (cw + gap)
        e.rect(x, HAUT, x + cw, BAS, GLASS, 195)
        e.grad(x, HAUT, x + cw, HAUT + 150, col, 46, col, 0, vertical=True)
        e.border(x, HAUT, cw, BAS - HAUT, WHITE, 26)
        filet_haut(e, x, HAUT, cw, col)
        e.m(t("caisse"), x + 10, HAUT + 11, MINI, mix(col, WHITE, 0.4), 230, maxw=cw - 20)
        e.glow(x + cw / 2, HAUT + 60, 50, 42, col, 85)
        e.disc(x + cw / 2, HAUT + 60, 37, col, 60, anneau=0.5)
        e.disc(x + cw / 2, HAUT + 60, 29, col, 35, anneau=0.5)
        cle = None
        if (CLES / (g + ".png")).exists():
            cle = recadrer(recadrer(Image.open(CLES / (g + ".png"))).rotate(-28, resample=Image.BICUBIC, expand=True))
            e.ajuster(cle, x + cw / 2 - 38, HAUT + 22, 76, 76, bas=False)
        e.c(t("cle." + g), x + cw / 2, HAUT + 111, NOM, WHITE, maxw=cw - 16, quoi="nom de clé")
        e.c(t("cle_desc." + g), x + cw / 2, HAUT + 124, PETIT, OPTION, maxw=cw - 14, quoi="description clé")
        y = HAUT + 135
        e.rect(x + 10, y, x + cw - 10, y + 1 / 3, WHITE, 23)
        y += 8
        oh = 30
        for j, o in enumerate(os_):
            s = j == choix[i]
            bx, bw = x + 10, cw - 20
            if s:
                e.grad(bx, y, bx + bw, y + oh, ACCENT, 60, ACCENT, 14)
                e.border(bx, y, bw, oh, ACCENT, 140)
            else:
                e.rect(bx, y, bx + bw, y + oh, WHITE, 10)
                e.border(bx, y, bw, oh, WHITE, 30)
            n = o["quantite"]
            if cle is not None:
                for k in range(min(n, 3) - 1, -1, -1):
                    e.ajuster(cle, bx + 5 + k * 4, y + 4, 13, oh - 8, bas=False, alpha=1.0 if k == 0 else 0.55)
            lx = bx + 30
            ly = y + oh / 2 if n == 1 else y + oh / 2 - 4.2
            wp = prix(e, o["prix"], bx + bw - 6, y + oh / 2)
            w = e.m(t("n_cle" if n == 1 else "n_cles", n), lx, ly, TEXTE_GRAS, WHITE if s else OPTION, maxw=bw - 30 - wp - 8, quoi="pack")
            if n > 1:
                plein = os_[0]["prix"] * n
                pw2 = puce(e, "−%d %%" % round(100 - 100 * o["prix"] / plein), lx + w + 4, ly, OK)
                if 30 + w + 4 + pw2 > bw - wp - 6:
                    e.defauts.append("[%s] puce du pack trop large" % LANGUE)
                e.m(t("au_lieu", nombre(plein)), lx, y + oh / 2 + 5.2, PETIT, MUTED, maxw=bw - 30 - wp - 8, quoi="au lieu de")
            y += oh + 4
        bh = 20
        by = BAS - 10 - bh
        if by < y + 2:
            e.defauts.append("[%s] colonne %s trop remplie" % (LANGUE, g))
        bouton(e, x + 10, by, cw - 20, bh)
        e.c(t("acheter", nombre(os_[choix[i]]["prix"])), x + cw / 2, by + bh / 2, BOUTON, WHITE, maxw=cw - 28, quoi="bouton clé")
    pied(e, [("clic", "choisir"), ("entree", "acheter"), ("echap", "fermer")], "cles")
    e.sauver(sortie)


# ======================================================================= 4. Sur mesure
def ecran_sur_mesure(sortie):
    e = Ecran(FOND)
    entete(e, 3)
    gap = 9
    cw = (GW - 2 * M - 2 * gap) / 3
    ch = 180
    for i, (nom, _f, col, _fy) in enumerate(SM):
        x = M + i * (cw + gap)
        e.rect(x, HAUT, x + cw, HAUT + ch, GLASS, 200)
        e.image(charger("sur_mesure_%s.png" % nom), x + 8, HAUT + 8, SM_IW, SM_IH)
        e.grad(x + 8, HAUT + 8 + SM_IH - 34, x + 8 + SM_IW, HAUT + 8 + SM_IH, GLASS, 0, GLASS, 200, vertical=True)
        e.border(x + 8, HAUT + 8, SM_IW, SM_IH, col, 90)
        e.border(x, HAUT, cw, ch, WHITE, 26)
        filet_haut(e, x, HAUT, cw, col)
        lab = t("a_vie").upper()
        rw = e.largeur(lab, MINI) + 10
        e.rect(x + 14, HAUT + 14, x + 14 + rw, HAUT + 24, col, 230)
        e.m(lab, x + 19, HAUT + 19, MINI, WHITE)
        y = HAUT + 128
        e.texte(t("sm.%s.nom" % nom), x + 10, y, NOM, col, maxw=cw - 20, quoi="nom sur mesure")
        ls = e.lignes(t("sm.%s.texte" % nom), TEXTE, cw - 20)
        if len(ls) > 2:
            e.defauts.append("[%s] texte sur mesure sur plus de 2 lignes" % LANGUE)
        for k, l in enumerate(ls):
            e.texte(l, x + 10, y + 17 + k * 10, TEXTE, LIGHT, maxw=cw - 20)
        e.texte(t("sur_devis"), x + 10, y + 41, (True, 6.5, 0.4), MUTED, maxw=cw - 20)
    y = HAUT + ch + 9
    bh = BAS - y
    x, w = M, GW - 2 * M
    e.rect(x, y, x + w, y + bh, GLASS, 205)
    e.grad(x, y, x + w * 0.6, y + bh, DISCORD, 60, DISCORD, 0)
    e.border(x, y, w, bh, DISCORD, 150)
    lx, ly = x + 24, y + bh / 2
    e.disc(lx, ly, 13, DISCORD, 255)
    e.image(charger("discord.png"), lx - 13, ly - 13, 26, 26)
    tx = x + 46
    bw, bhh = 158, 24
    bx, by = x + w - 12 - bw, y + bh / 2 - bhh / 2
    e.texte(t("discord.titre"), tx, y + 12, ONGLET, WHITE, maxw=bx - tx - 10, quoi="titre discord")
    for k in range(3):
        cy = y + 30 + k * 10.5
        e.disc(tx + 3.5, cy, 3.6, DISCORD, 255)
        e.c(str(k + 1), tx + 3.5, cy, (True, 5.6, 0), WHITE)
        e.m(t("discord.etape%d" % (k + 1)), tx + 11, cy, TEXTE, OPTION, maxw=bx - tx - 21, quoi="étape")
    e.grad(bx, by, bx + bw, by + bhh, DISCORD, 210, DISCORD, 150)
    e.border(bx, by, bw, bhh, mix(DISCORD, WHITE, 0.4), 220)
    e.c(t("discord.bouton"), bx + bw / 2, by + bhh / 2 - 3.5, BOUTON, WHITE, maxw=bw - 10, quoi="bouton discord")
    e.c("discord.gg/heraliz", bx + bw / 2, by + bhh / 2 + 6.5, PETIT, mix(DISCORD, WHITE, 0.7))
    pied(e, [("clic", "discord"), ("echap", "fermer")], "sur_mesure")
    e.sauver(sortie)


FOND = None


def main():
    global POL, TXT, LANGUE, FOND
    ap = argparse.ArgumentParser()
    ap.add_argument("dossier", nargs="?")
    ap.add_argument("--textures", action="store_true")
    ap.add_argument("--cles", action="store_true")
    ap.add_argument("--langue", default="fr_fr")
    a = ap.parse_args()
    if a.textures:
        textures()
    if a.cles:
        rendre_cles()
    if not a.dossier and (a.textures or a.cles):
        return
    POL = Polices()
    FOND = fond()
    sortie = Path(a.dossier) if a.dossier else None
    if sortie:
        sortie.mkdir(parents=True, exist_ok=True)
    langues = ["fr_fr", "en_us", "ru_ru"]
    for lang in langues:
        LANGUE = lang
        TXT = json.loads((ASSETS / "lang" / (lang + ".json")).read_text(encoding="utf-8"))
        ecrire = sortie is not None and (a.langue == "toutes" or a.langue == lang)
        nom = (lambda n: sortie / ("%s%s.png" % (n, "" if lang == "fr_fr" else "_" + lang[:2]))) if ecrire else (lambda n: None)
        for choisi in range(3):  # chaque grade choisi doit tenir dans le panneau
            ecran_grades(nom("1_grades") if choisi == 1 else None, choisi=choisi)
        ecran_beta(nom("2_offre_beta"))
        ecran_cles(nom("3_cles"))
        ecran_sur_mesure(nom("4_sur_mesure"))
    print("aucun texte ne dépasse (fr, en, ru)")


if __name__ == "__main__":
    main()
