"""Chat au style HeraliZ : maquette hors du jeu (proposition du 24/10, À VALIDER ; aucun code du jeu).

    python mods/heraliz-grades/outils/apercu_chat.py DOSSIER [--langue fr|en|ru|toutes]

Écrit dans DOSSIER, pour chaque langue : chat_jeu_<langue>.png (chat fermé, pendant la partie) et
chat_ouvert_<langue>.png (touche T), plus planche_chat.png (les deux côte à côte, en français).

Même langage visuel que le tab HeraliZ (client/TabHeraliz, maquette validée) : panneau de verre sombre (0x0D0F14,
contour blanc très léger, liseré clair en haut), barre de la couleur du grade au début de chaque ligne, grade en petites
capitales espacées de sa couleur, tag de clan en gras de la couleur du clan, pseudo en gras, « » » discret, message en
DM Sans (Onest pour le cyrillique). Les polices sont tracées à leur vraie taille (pas d'agrandissement) comme le veut la
règle du 23/10 : taille de l'em = taille du fichier .json du jeu / 1,302 (ui 11 -> 8,45 unités ; ui_small 9 -> 6,9).

Mesures en unités d'interface (canevas 640 x 360, rendu à l'échelle 3 = 1920 x 1080) ; chaque texte est mesuré avec la
vraie police : un texte qui sort de son cadre arrête le script. Numpy et Pillow.
"""
import argparse
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont
from fontTools.ttLib import TTFont

RACINE = Path(__file__).resolve().parents[3]
FONTS = RACINE / "mods" / "heraliz-grades" / "src" / "client" / "resources" / "assets" / "heraliz_grades" / "font"
FOND = RACINE / "mods" / "heraliz-interface" / "src" / "main" / "resources" / "assets" / "heraliz_interface" / "textures" / "gui" / "fond_titre.png"
K = 6  # pixels par unité pendant le dessin (réduit de moitié ensuite : échelle 3)
GW, GH = 640, 360

# couleurs du kit (grades/client/kit/Ui) et des grades (Grade.java)
TEXT, MUTED, FAINT, OK, DANGER, OR, AMBRE, VERRE = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0x5FD08A, 0xFF6B6B, 0xE8C36A, 0xE8B34A, 0x0D0F14
VIOLET, BLEU = 0xB48CFF, 0x6FA8FF
GRADES = {
    "fondateur": ("Fondateur", "Founder", "Основатель", 0xE5484D),
    "moderateur": ("Modérateur", "Moderator", "Модератор", 0xB48CFF),
    "roi": ("Roi de la Zone", "King of the Zone", "Король Зоны", 0xFFC23A),
    "traqueur": ("Traqueur", "Tracker", "Следопыт", 0x8FD3FF),
    "rescape": ("Rescapé", "Survivor+", "Уцелевший", 0xE39B5E),
    "survivant": ("Survivant", "Survivor", "Выживший", 0xA0A6B0),
}
LANGUES = {"fr": 0, "en": 1, "ru": 2}

# polices : (gras, taille de l'em, espacement des lettres, capitales)
MSG = (False, 11 / 1.302, 0, False)
NOM = (True, 11 / 1.302, 0, False)
LABEL = (True, 9 / 1.302, 0.4, True)
PETIT = (False, 9 / 1.302, 0.6, True)
TITRE = (True, 11 / 1.302, 2.2, True)

# mesures (unités)
X0, BAS_JEU = 4, GH - 44          # coin gauche du chat, bas du chat au-dessus de la barre d'objets
LARGEUR = 300                     # largeur du panneau (Minecraft : 320 au plus par défaut)
LIGNE = 12                        # hauteur d'une ligne
MARGE = 8                         # marge intérieure gauche / droite
BARRE = 2                         # barre de la couleur du grade


def rgb(c):
    return (c >> 16 & 255, c >> 8 & 255, c & 255)


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


POL = Polices()


class Toile:
    def __init__(self, fond=True):
        self.im = Image.new("RGBA", (GW * K, GH * K), (5, 6, 8, 255))
        self.defauts = []
        if fond and FOND.exists():
            f = Image.open(FOND).convert("RGB")
            s = max(GW * K / f.width, GH * K / f.height)
            f = f.resize((int(f.width * s) + 1, int(f.height * s) + 1), Image.LANCZOS).crop((0, 0, GW * K, GH * K))
            f = f.filter(ImageFilter.GaussianBlur(K * 0.6))
            self.im.paste(f.convert("RGBA"))
            self.rect(0, 0, GW, GH, 0x000000, 70)

    def rect(self, x, y, w, h, c, a=255):
        L = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        ImageDraw.Draw(L).rectangle([round(x * K), round(y * K), round((x + w) * K) - 1, round((y + h) * K) - 1], fill=rgb(c) + (int(a),))
        self.im.alpha_composite(L)

    def arrondi(self, x, y, w, h, r, c, a=255, contour=None, ca=0):
        L = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(L)
        box = [round(x * K), round(y * K), round((x + w) * K) - 1, round((y + h) * K) - 1]
        d.rounded_rectangle(box, radius=r * K, fill=rgb(c) + (int(a),))
        if contour is not None:
            d.rounded_rectangle(box, radius=r * K, outline=rgb(contour) + (int(ca),), width=max(1, K // 3))
        self.im.alpha_composite(L)

    def verre(self, x, y, w, h, alpha=200):
        """Ui.glass : verre sombre, contour blanc à 10 %, liseré clair en haut."""
        self.arrondi(x, y, w, h, 2, VERRE, alpha, 0xFFFFFF, 26)
        self.rect(x + 2, y + 1, w - 4, 1 / 3, 0xFFFFFF, 41)

    def largeur(self, s, p):
        gras, taille, esp, caps = p
        if caps:
            s = s.upper()
        w, n = 0, 0
        for m, cyr in POL.morceaux(s):
            f = POL.f(gras, taille, cyr)
            for ch in m:
                w += f.getlength(ch)
                n += 1
        return w / K + (esp * (n - 1) if n else 0)

    def hcap(self, p):
        b = POL.f(p[0], p[1], False).getbbox("H")
        return (b[3] - b[1]) / K

    def texte(self, s, x, cy, p, c, a=255, maxx=None, quoi=""):
        """Texte posé sur le milieu des capitales (cy), sur un point entier de l'écran (Ui.placer)."""
        gras, taille, esp, caps = p
        if caps:
            s = s.upper()
        w = self.largeur(s, p)
        if maxx is not None and x + w > maxx + 0.05:
            self.defauts.append("%s : « %s » sort du cadre (%.1f > %.1f)" % (quoi or "texte", s, x + w, maxx))
        haut = round((cy - self.hcap(p) / 2) * 3) / 3
        x = round(x * 3) / 3
        L = Image.new("RGBA", self.im.size, (0, 0, 0, 0))
        d = ImageDraw.Draw(L)
        px = x * K
        top = POL.f(gras, taille, False).getbbox("H")[1]
        for m, cyr in POL.morceaux(s):
            f = POL.f(gras, taille, cyr)
            dy = POL.f(gras, taille, False).getmetrics()[0] - f.getmetrics()[0]
            for ch in m:
                d.text((px, haut * K - top + dy), ch, font=f, fill=rgb(c) + (int(a),))
                px += f.getlength(ch) + esp * K
        self.im.alpha_composite(L)
        return w

    def couper(self, mots, p, place):
        """Coupe un message en lignes qui tiennent dans « place »."""
        lignes, cur = [], ""
        for m in mots.split(" "):
            essai = (cur + " " + m).strip()
            if self.largeur(essai, p) <= place or not cur:
                cur = essai
            else:
                lignes.append(cur)
                cur = m
        if cur:
            lignes.append(cur)
        return lignes

    def touche(self, x, cy, touche, action):
        """Pastille de touche (comme la rangée des touches de la boutique) suivie de son action."""
        tw = self.largeur(touche, PETIT) + 8
        self.arrondi(x, cy - 5, tw, 10, 2, 0xFFFFFF, 18, 0xFFFFFF, 40)
        self.texte(touche, x + 4, cy, PETIT, TEXT)
        return tw + 4 + self.texte(action, x + tw + 4, cy, (False, 9 / 1.302, 0, False), MUTED) + 10

    def finir(self):
        return self.im.resize((GW * 3, GH * 3), Image.LANCZOS)


# ---------------------------------------------------------------- textes (proposition, trois langues)
T = {
    "fr": {"tous": "Tous", "clan": "Clan", "mp": "Privés", "serveur": "Serveur", "ecrire": "Écris ton message…",
           "entree": "Entrée", "envoyer": "Envoyer", "echap": "Échap", "fermer": "Fermer", "haut": "↑", "rappel": "Message précédent",
           "chat": "Chat", "nouveaux": "3 nouveaux messages"},
    "en": {"tous": "All", "clan": "Clan", "mp": "Private", "serveur": "Server", "ecrire": "Type your message…",
           "entree": "Enter", "envoyer": "Send", "echap": "Esc", "fermer": "Close", "haut": "↑", "rappel": "Previous message",
           "chat": "Chat", "nouveaux": "3 new messages"},
    "ru": {"tous": "Все", "clan": "Клан", "mp": "Личные", "serveur": "Сервер", "ecrire": "Напиши сообщение…",
           "entree": "Enter", "envoyer": "Отправить", "echap": "Esc", "fermer": "Закрыть", "haut": "↑", "rappel": "Прошлое сообщение",
           "chat": "Чат", "nouveaux": "3 новых сообщения"},
}

# (type, grade, clan, couleur du clan, pseudo, message fr / en / ru)
MESSAGES = [
    ("joueur", "fondateur", "", 0, "HeraliZ", ("Bienvenue sur la bêta ! Signalez les bugs sur le Discord.",
                                              "Welcome to the beta! Report bugs on the Discord.",
                                              "Добро пожаловать на бету! Пишите о багах в Discord.")),
    ("info", "serveur", "", 0, "", ("Extraction 3 ouverte pendant 1 h.", "Extraction 3 is open for 1 h.", "Эвакуация 3 открыта на 1 ч.")),
    ("joueur", "roi", "NOVA", 0x6FD0FF, "Kraken", ("Quelqu'un pour l'aérodrome ? On part dans 2 min.",
                                                   "Anyone up for the airfield? We leave in 2 min.",
                                                   "Кто на аэродром? Выходим через 2 мин.")),
    ("joueur", "traqueur", "RVN", 0xFF8A5C, "Lynx", ("On tient Tcherno, venez vous servir.", "We hold Tcherno, come and get it.",
                                                     "Мы держим Черно, приходите.")),
    ("joueur", "survivant", "", 0, "Rodeur_77", ("je cherche des clous, je paie en hérals", "looking for nails, paying in herals",
                                                 "ищу гвозди, плачу герал")),
    ("quete", "quete", "", 0, "", ("Quête terminée : Fouille (+100 hérals).", "Quest complete: Search (+100 herals).",
                                   "Задание выполнено: Обыск (+100 герал).")),
    ("mp", "mp", "", 0, "Lynx", ("rejoins-moi au drapeau, j'ai ta caisse", "meet me at the flag, I have your crate",
                                 "жду у флага, твой ящик у меня")),
    ("mort", "mort", "", 0, "", ("Kraken a été achevé par Lynx.", "Kraken was finished off by Lynx.", "Lynx добил Kraken.")),
]
ETIQUETTES = {  # pastilles des messages qui ne viennent pas d'un joueur
    "info": ((("Serveur", "Server", "Сервер")), BLEU),
    "quete": ((("Quête", "Quest", "Задание")), OR),
    "mp": ((("MP de", "PM from", "ЛС от")), VIOLET),
}


def ligne(t, langue, m, x, y, largeur, alpha=255):
    """Dessine un message (une ou plusieurs lignes) à partir de y ; rend le y sous le message."""
    i = LANGUES[langue]
    genre, grade, clan, cclan, pseudo, textes = m
    fin = x + largeur - MARGE
    tx = x + MARGE
    cy = y + LIGNE / 2
    if genre == "joueur":
        g = GRADES[grade]
        t.rect(x + 3, y + 2, BARRE, LIGNE - 4, g[3], alpha)
        if grade != "survivant":
            tx += t.texte(g[i], tx, cy, LABEL, g[3], alpha) + 5
        if clan:
            tx += t.texte("[" + clan + "]", tx, cy, NOM, cclan, alpha) + 4
        tx += t.texte(pseudo, tx, cy, NOM, TEXT, alpha) + 4
        tx += t.texte("»", tx, cy, MSG, FAINT, alpha) + 4
        couleur = TEXT
    elif genre == "mort":
        t.rect(x + 3, y + 2, BARRE, LIGNE - 4, DANGER, alpha * 0.8)
        couleur = MUTED
    else:
        noms, c = ETIQUETTES[genre]
        t.rect(x + 3, y + 2, BARRE, LIGNE - 4, c, alpha)
        e = noms[i] + ((" " + pseudo) if genre == "mp" else "")
        w = t.largeur(e, LABEL) + 8
        t.arrondi(tx, cy - 4.5, w, 9, 2, c, alpha * 0.16, c, alpha * 0.45)
        t.texte(e, tx + 4, cy, LABEL, c, alpha)
        tx += w + 5
        couleur = TEXT if genre != "mp" else 0xE4D8FF
    morceaux = t.couper(textes[i], MSG, fin - tx)
    for k, s in enumerate(morceaux):
        t.texte(s, tx, y + LIGNE / 2 + k * LIGNE, MSG, couleur, alpha, fin, "message")
    return y + LIGNE * len(morceaux)


def hauteur(t, langue, m, largeur):
    """Hauteur d'un message (lignes coupées) sans le dessiner."""
    essai = Toile(fond=False)
    return ligne(essai, langue, m, 0, 0, largeur, 0)


def chat_jeu(langue):
    t = Toile()
    msgs = MESSAGES[2:]  # les plus récents
    hs = [hauteur(t, langue, m, LARGEUR) for m in msgs]
    h = sum(hs) + 8
    y = BAS_JEU - h
    t.verre(X0, y, LARGEUR, h, 150)
    yy = y + 4
    for k, m in enumerate(msgs):
        a = 255 if k >= 2 else (110 if k == 0 else 180)   # les plus anciens s'effacent (comme Minecraft après 10 s)
        yy = ligne(t, langue, m, X0, yy, LARGEUR, a)
    barre_objets(t)
    return t


def chat_ouvert(langue):
    t = Toile()
    tx = T[langue]
    hs = [hauteur(t, langue, m, LARGEUR) for m in MESSAGES]
    entete, saisie = 18, 16
    h = entete + sum(hs) + 8
    bas = GH - 6 - saisie - 4
    y = bas - h
    t.verre(X0, y, LARGEUR, h, 205)
    # en-tête : titre et filtres (les filtres demandent un tri des messages par le serveur : facultatif)
    t.texte(tx["chat"], X0 + MARGE, y + 9, TITRE, TEXT)
    cx = X0 + MARGE + t.largeur(tx["chat"], TITRE) + 12
    for k, (cle, c) in enumerate((("tous", TEXT), ("clan", 0x6FD0FF), ("mp", VIOLET), ("serveur", BLEU))):
        w = t.largeur(tx[cle], PETIT) + 10
        actif = k == 0
        t.arrondi(cx, y + 4, w, 10, 2, c if actif else 0xFFFFFF, 40 if actif else 10, c if actif else 0xFFFFFF, 120 if actif else 30)
        t.texte(tx[cle], cx + 5, y + 9, PETIT, TEXT if actif else MUTED)
        cx += w + 4
    if cx > X0 + LARGEUR - MARGE:
        t.defauts.append("filtres trop larges (%s)" % langue)
    t.rect(X0 + MARGE, y + entete - 1, LARGEUR - 2 * MARGE, 1 / 3, 0xFFFFFF, 50)
    yy = y + entete + 4
    for m in MESSAGES:
        yy = ligne(t, langue, m, X0, yy, LARGEUR - 6)
    # défilement : piste fine à droite
    t.rect(X0 + LARGEUR - 5, y + entete + 4, 1, h - entete - 8, 0xFFFFFF, 25)
    t.rect(X0 + LARGEUR - 5, y + entete + 4 + (h - entete - 8) * 0.55, 1, (h - entete - 8) * 0.45, 0xFFFFFF, 110)
    # saisie : verre, contour clair, curseur, texte d'aide ; touches à droite
    sy = GH - 6 - saisie
    t.verre(X0, sy, LARGEUR, saisie, 215)
    t.arrondi(X0 + 0.5, sy + 0.5, LARGEUR - 1, saisie - 1, 2, 0xFFFFFF, 0, 0xFFFFFF, 70)
    t.rect(X0 + MARGE, sy + 4, 1 / 3 * 2, saisie - 8, TEXT, 230)
    t.texte(tx["ecrire"], X0 + MARGE + 3, sy + saisie / 2, MSG, FAINT, 255, X0 + LARGEUR - MARGE, "aide de saisie")
    kx = X0 + LARGEUR + 8
    kx += t.touche(kx, sy + saisie / 2, tx["entree"], tx["envoyer"])
    kx += t.touche(kx, sy + saisie / 2, tx["haut"], tx["rappel"])
    kx += t.touche(kx, sy + saisie / 2, tx["echap"], tx["fermer"])
    if kx > GW - 4:
        t.defauts.append("rangée des touches trop large (%s)" % langue)
    return t


def barre_objets(t):
    """Repère : la barre d'objets DayZ en bas au milieu (simple silhouette, pour situer le chat)."""
    w = 9 * 20 + 8
    x = (GW - w) / 2
    t.verre(x, GH - 26, w, 22, 120)
    for k in range(9):
        t.arrondi(x + 4 + k * 20, GH - 23, 18, 16, 2, 0xFFFFFF, 10 if k else 30, 0xFFFFFF, 30 if k else 120)


def main():
    a = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    a.add_argument("dossier")
    a.add_argument("--langue", default="toutes", choices=["fr", "en", "ru", "toutes"])
    o = a.parse_args()
    out = Path(o.dossier)
    out.mkdir(parents=True, exist_ok=True)
    defauts = []
    langues = list(LANGUES) if o.langue == "toutes" else [o.langue]
    images = {}
    for lg in langues:
        for nom, fn in (("jeu", chat_jeu), ("ouvert", chat_ouvert)):
            t = fn(lg)
            defauts += t.defauts
            im = t.finir()
            im.convert("RGB").save(out / ("chat_%s_%s.png" % (nom, lg)), optimize=True)
            images[(nom, lg)] = im
            print("écrit", out / ("chat_%s_%s.png" % (nom, lg)))
    if ("jeu", "fr") in images and ("ouvert", "fr") in images:
        p = Image.new("RGB", (GW * 3 * 2 + 30, GH * 3), (20, 22, 26))
        p.paste(images[("jeu", "fr")].convert("RGB"), (0, 0))
        p.paste(images[("ouvert", "fr")].convert("RGB"), (GW * 3 + 30, 0))
        p.save(out / "planche_chat.png", optimize=True)
        print("écrit", out / "planche_chat.png")
    if defauts:
        print("\n".join(defauts))
        sys.exit(1)


if __name__ == "__main__":
    main()
