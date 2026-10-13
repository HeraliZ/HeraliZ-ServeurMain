"""
Images d'écran de l'ordinateur d'amorçage « VX-9 » (terminal noir, interface orange) : dessinées à 4x (536 x 360) puis
peintes sur la texture de l'écran (134 x 90 texels). Textes réduits au minimum et sans langue (chiffres, icônes,
« SITE A », quelques mots de terminal : ARM, ARMED, BYPASS, SAFE) : la texture n'est pas traduite.

Deux usages :
  - jeu=True (textures du mod) : les parties qui bougent sont laissées vides, le jeu les dessine en direct par-dessus
    (client : duels/client/bombe/EcranVif.java, mêmes coordonnées que ci-dessous, en pixels de cette image) :
      amorçage : pourcentage (en haut à droite), chiffres tapés dans les cases, case active, coches, barre de 20 cases ;
      amorcée  : compte à rebours (m:ss) et barre du temps restant ;
      piratage : secondes restantes (en haut à droite), anneau de progression, barre de 20 cases ;
  - jeu=False (planches, animation) : tout est peint, comme sur les rendus validés.

Coordonnées (pixels de l'image 536 x 360) reprises par EcranVif :
  en-tête : texte de droite aligné à droite en x = 520, centré en y = 22 (Oswald 30) ;
  cases des codes : ligne i (0..2) en y = 74 + 60 i, case k (0..3) en x = 70 + 62 k, 52 x 46 ; chiffre centré
    (x + 26, y + 24), Oswald/mono 40 ; curseur [x + 16, y + 34, x + 36, y + 39] ; coche (340, y+24) (352, y+36) (374, y+10) ;
  barre : cadre [16, 300, 520, 346], 20 cases de (504 - 12) / 20 de large depuis x = 22, de y = 306 à y = 340 ;
  amorcée : compte à rebours centré (396, 168), Oswald 118 ; barre [260, 326, 520, 344] ;
  piratage : anneau centre (418, 172), rayons 72 à 88, départ en haut, sens horaire.
"""
import random

from PIL import Image, ImageDraw

from geo import police

L, H = 536, 360  # 4 x (134 x 90)

THEME = {"fond": (10, 10, 12), "fond2": (22, 18, 16), "ligne": (70, 44, 22), "texte": (255, 236, 220),
         "accent": (255, 122, 26), "danger": (255, 52, 40), "ok": (80, 255, 170), "pirate": (60, 220, 255),
         "mono": "mono"}

# codes tapés pendant l'amorçage (12 chiffres, dans cet ordre ; repris par EcranVif)
CODES = ("7391", "4082", "1657")


def _fond(t, teinte=None):
    im = Image.new("RGB", (L, H), t["fond"])
    d = ImageDraw.Draw(im)
    # dégradé vertical léger
    for y in range(H):
        k = y / H
        c = tuple(int(t["fond"][i] * (1 - k) + t["fond2"][i] * k) for i in range(3))
        d.line([(0, y), (L, y)], fill=c)
    if teinte:
        voile = Image.new("RGB", (L, H), teinte)
        im = Image.blend(im, voile, 0.10)
        d = ImageDraw.Draw(im)
    # quadrillage fin
    for x in range(0, L, 24):
        d.line([(x, 0), (x, H)], fill=tuple(min(255, c + 6) for c in t["fond"]))
    for y in range(0, H, 24):
        d.line([(0, y), (L, y)], fill=tuple(min(255, c + 6) for c in t["fond"]))
    return im


def _entete(im, t, gauche, droite, couleur):
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, L, 44], fill=tuple(int(c * 0.25) for c in couleur))
    d.line([(0, 44), (L, 44)], fill=couleur, width=3)
    d.text((16, 22), gauche, font=police("oswald", 30), fill=couleur, anchor="lm")
    if droite:
        d.text((L - 16, 22), droite, font=police("oswald", 30), fill=couleur, anchor="rm")


def _barre(d, x0, y0, x1, y1, r, couleur, fond=(30, 30, 30), segments=20):
    d.rectangle([x0, y0, x1, y1], outline=couleur, width=3)
    w = (x1 - x0 - 12) / segments
    for i in range(segments):
        xa = x0 + 6 + i * w
        plein = (i + 1) / segments <= r + 1e-6
        d.rectangle([xa + 2, y0 + 6, xa + w - 3, y1 - 6], fill=couleur if plein else fond)


def _cadenas(d, cx, cy, s, couleur, ouvert=False):
    d.rounded_rectangle([cx - s, cy - s * 0.2, cx + s, cy + s * 1.2], radius=s * 0.2, fill=couleur)
    dx = s * 0.55
    if ouvert:
        d.arc([cx - dx - s * 0.9, cy - s * 1.25, cx + dx - s * 0.9, cy + s * 0.1], 180, 360, fill=couleur, width=int(s * 0.28))
    else:
        d.arc([cx - dx, cy - s * 1.25, cx + dx, cy + s * 0.1], 180, 360, fill=couleur, width=int(s * 0.28))
        d.line([(cx - dx + s * 0.14, cy - s * 0.6), (cx - dx + s * 0.14, cy - s * 0.15)], fill=couleur, width=int(s * 0.28))
        d.line([(cx + dx - s * 0.14, cy - s * 0.6), (cx + dx - s * 0.14, cy - s * 0.15)], fill=couleur, width=int(s * 0.28))
    d.ellipse([cx - s * 0.18, cy + s * 0.25, cx + s * 0.18, cy + s * 0.6], fill=(0, 0, 0))
    d.rectangle([cx - s * 0.07, cy + s * 0.45, cx + s * 0.07, cy + s * 0.9], fill=(0, 0, 0))


def veille(site="A"):
    t = THEME
    im = _fond(t)
    _entete(im, t, "HZ-DET", "SITE " + site, t["accent"])
    d = ImageDraw.Draw(im)
    d.text((L / 2, 150), site, font=police("oswald", 150), fill=t["accent"], anchor="mm")
    _cadenas(d, L / 2, 270, 22, t["texte"])
    d.text((L / 2, 330), "_ _ _ _", font=police(t["mono"], 30), fill=t["texte"], anchor="mm")
    return im


def amorcage(site="A", progression=0.62, codes=("7391", "40", ""), jeu=False):
    """Saisie des codes de sécurité (attaquant). jeu=True : cases vides, barre vide, pas de pourcentage."""
    t = THEME
    im = _fond(t)
    if jeu:
        progression, codes = 0.0, ("", "", "")
    _entete(im, t, "ARM // SITE " + site, "" if jeu else "%d%%" % round(progression * 100), t["accent"])
    d = ImageDraw.Draw(im)
    f = police(t["mono"], 40)
    y = 74
    for i, code in enumerate(codes):
        fait = len(code) == 4
        d.text((20, y + 22), "%d" % (i + 1), font=police("oswald", 34), fill=t["accent"], anchor="lm")
        for k in range(4):
            x = 70 + k * 62
            cases = (x, y, x + 52, y + 46)
            actif = not jeu and (not fait) and k == len(code) and (i == 0 or len(codes[i - 1]) == 4)
            d.rectangle(cases, outline=t["accent"] if actif else t["ligne"], width=4 if actif else 3,
                        fill=(t["fond2"] if not actif else tuple(int(c * 0.3) for c in t["accent"])))
            if k < len(code):
                d.text((x + 26, y + 24), code[k], font=f, fill=t["texte"], anchor="mm")
            elif actif:
                d.rectangle([x + 16, y + 34, x + 36, y + 39], fill=t["accent"])
        if fait:
            d.line([(340, y + 24), (352, y + 36), (374, y + 10)], fill=t["ok"], width=7)
        y += 60
    # flux de caractères à droite
    rnd = random.Random(7)
    fm = police(t["mono"], 18)
    for r in range(9):
        ligne = "".join(rnd.choice("0123456789ABCDEF") for _ in range(6))
        d.text((404, 66 + r * 22), ligne, font=fm, fill=tuple(int(c * (0.35 + 0.08 * r)) for c in t["texte"]))
    _barre(d, 16, 300, L - 16, 346, progression, t["accent"], fond=t["fond2"])
    return im


def amorcee(site="A", reste="0:38", part=0.95, jeu=False):
    """Bombe amorcée : rouge, lettre du site ; jeu=True : sans compte à rebours ni barre pleine (dessinés en direct)."""
    t = THEME
    im = _fond(t, teinte=t["danger"])
    _entete(im, t, "ARMED", "SITE " + site, t["danger"])
    d = ImageDraw.Draw(im)
    cx, cy = 128, 196
    d.ellipse([cx - 92, cy - 92, cx + 92, cy + 92], outline=t["danger"], width=10)
    d.arc([cx - 112, cy - 112, cx + 112, cy + 112], -90, 180, fill=t["danger"], width=6)
    d.text((cx, cy + 4), site, font=police("oswald", 130), fill=t["danger"], anchor="mm")
    if not jeu:
        d.text((396, 168), reste, font=police("oswald", 118), fill=(255, 235, 225), anchor="mm")
    _cadenas(d, 396, 270, 20, t["danger"])
    d.rectangle([260, 326, L - 16, 344], fill=tuple(int(c * 0.35) for c in t["danger"]))
    if not jeu:
        d.rectangle([260, 326, 260 + (L - 16 - 260) * part, 344], fill=t["danger"])
    return im


def piratage(site="A", progression=0.45, reste="3.3", jeu=False):
    """Piratage par un défenseur (6 s) ; jeu=True : anneau, barre et secondes laissés au jeu."""
    t = THEME
    c = t["pirate"]
    im = _fond(t, teinte=c)
    if jeu:
        progression = 0.0
    _entete(im, t, "BYPASS // " + site, "" if jeu else reste + " s", c)
    d = ImageDraw.Draw(im)
    rnd = random.Random(3)
    fm = police(t["mono"], 20)
    for r in range(10):
        ligne = " ".join("".join(rnd.choice("0123456789ABCDEF") for _ in range(4)) for _ in range(3))
        gris = 0.25 + 0.07 * r
        d.text((16, 60 + r * 23), ligne, font=fm, fill=tuple(int(k * gris) for k in c))
    # anneau de progression
    cx, cy, R = 418, 172, 88
    d.ellipse([cx - R, cy - R, cx + R, cy + R], outline=tuple(int(k * 0.3) for k in c), width=16)
    if progression > 0:
        d.arc([cx - R, cy - R, cx + R, cy + R], -90, -90 + 360 * progression, fill=c, width=16)
    _cadenas(d, cx, cy - 6, 28, c, ouvert=progression > 0.99)
    _barre(d, 16, 300, L - 16, 346, progression, c, fond=t["fond2"])
    return im


def desamorcee(site="A"):
    t = THEME
    c = t["ok"]
    im = _fond(t, teinte=c)
    _entete(im, t, "SAFE", "SITE " + site, c)
    d = ImageDraw.Draw(im)
    cx, cy = L / 2, 200
    d.ellipse([cx - 100, cy - 100, cx + 100, cy + 100], outline=c, width=12)
    d.line([(cx - 52, cy + 2), (cx - 12, cy + 44), (cx + 60, cy - 46)], fill=c, width=22)
    return im


def tous(site="A", jeu=False):
    return {
        "veille": veille(site),
        "amorcage": amorcage(site, jeu=jeu),
        "amorcee": amorcee(site, jeu=jeu),
        "piratage": piratage(site, jeu=jeu),
        "desamorcee": desamorcee(site),
    }


def animation(site="A"):
    """Images successives (GIF) : saisie des 3 codes, amorcée (compte à rebours), piratage, désamorcée."""
    images = []
    for i in range(0, 13):
        c = []
        reste = i
        for cf in CODES:
            n = min(4, reste)
            c.append(cf[:n])
            reste -= n
        images.append((amorcage(site, progression=i / 12, codes=c), 160))
    for s in range(40, 34, -1):
        images.append((amorcee(site, "0:%02d" % s, part=s / 40), 330))
    for i in range(0, 13):
        images.append((piratage(site, progression=i / 12, reste="%.1f" % (6 - i * 0.5)), 160))
    images.append((desamorcee(site), 1400))
    return images
