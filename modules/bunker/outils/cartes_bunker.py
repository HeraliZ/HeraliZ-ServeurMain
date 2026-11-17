#!/usr/bin/env python3
"""
Cartes et lecteurs des bunkers de HeraliZ Bunker (17/11, demande du propriétaire : « une nouvelle carte pour chaque
bunker + un keypad aussi »). Une carte et un lecteur par bunker (AccessLevel BUNKER_BOIS, BUNKER_PIC), faits à partir de
la carte rouge et du lecteur rouge (jamais modifiés) :
  - teinte décalée (orange pour le Bunker des Bois, violet pour le Bunker du Pic) ; noirs, gris et blancs gardés ;
  - carte, face : colonne « BUNKER » et un dessin (sapin : Bois ; montagne enneigée : Pic) au lieu de « LVL » et du gros
    chiffre, « SITE B-0n | BUNKER ... » au lieu de « SECTOR C-03 | CLEARANCE III » ; dos : nom du bunker au lieu de
    « CLEARANCE LEVEL 3 » ;
  - lecteur : nom court (BOIS, PIC) sur le panneau au lieu de « LVL 3 » ; écrans et voyants de lecture, d'accès accordé
    et refusé : ceux du lecteur rouge (les mêmes pour toutes les couleurs), repris tels quels par les modèles.

Écrit (jamais à la main) :
  - textures/item/keycard_<id>_front|back|edge.png ;
  - textures/block/keycard/body|head|panel_<id>.png, screen_<id>_idle.png, led_<id>_idle.png ;
  - models/item/keycard_<id>.json, models/item/card_reader_<id>.json,
    models/block/keycard/card_reader_<id>_<état>.json, blockstates/card_reader_<id>.json.

    python modules/bunker/outils/cartes_bunker.py --rendus DOSSIER     planche d'aperçu (rien d'installé)
    python modules/bunker/outils/cartes_bunker.py --installer          écrit les fichiers dans les assets du mod
"""
import argparse
import colorsys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ICI = Path(__file__).resolve().parent
RACINE = ICI.parent.parent.parent
ASSETS = ICI.parent / "src/client/resources/assets/heralisz_bunker"
TEX = ASSETS / "textures"
OSWALD = RACINE / "outils/polices/oswald-700.ttf"
DM_MEDIUM = RACINE / "modules/noyau/src/client/resources/assets/heraliz_noyau/font/dmsans_medium.ttf"
DM_BOLD = RACINE / "modules/noyau/src/client/resources/assets/heraliz_noyau/font/dmsans_bold.ttf"

BUNKERS = [
    {"id": "bunker_bois", "nom": "BUNKER DES BOIS", "court": "BOIS", "site": "SITE B-01", "teinte": 28, "dessin": "sapin"},
    {"id": "bunker_pic", "nom": "BUNKER DU PIC", "court": "PIC", "site": "SITE B-02", "teinte": 272, "dessin": "montagne"},
]
TEINTE_ROUGE = 356          # teinte (degrés) du rouge des cartes et lecteurs d'origine
ROUGE_COLONNE = (200, 34, 44, 255)
ROSE_TITRE = (255, 120, 120, 255)
FOND_PANNEAU = (12, 12, 14, 255)
ETATS = ["idle", "reading", "granted", "denied"]
# textures du lecteur propres à chaque couleur (les autres sont les mêmes partout : reprises du lecteur rouge)
PROPRES = ["body_red", "head_red", "panel_red", "screen_red_idle", "led_red_idle"]


# ------------------------------------------------------------------------------------------------ teinte
def teinter(im, teinte):
    """Teinte décalée de (teinte - rouge) ; saturation et clarté gardées (gris, noirs et blancs inchangés)."""
    a = np.asarray(im.convert("RGBA")).copy()
    delta = ((teinte - TEINTE_ROUGE) % 360) / 360.0
    cache = {}
    rgb = a[..., :3].reshape(-1, 3)
    out = np.empty_like(rgb)
    for k, (r, g, b) in enumerate(map(tuple, rgb)):
        c = cache.get((r, g, b))
        if c is None:
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            rr, gg, bb = colorsys.hsv_to_rgb((h + delta) % 1.0, s, v)
            c = cache[(r, g, b)] = (round(rr * 255), round(gg * 255), round(bb * 255))
        out[k] = c
    a[..., :3] = out.reshape(a.shape[0], a.shape[1], 3)
    return Image.fromarray(a, "RGBA")


# ------------------------------------------------------------------------------------------------ texte et dessins
def police(chemin, hauteur_cap):
    """Police dont les capitales font hauteur_cap pixels."""
    taille = hauteur_cap
    for _ in range(40):
        f = ImageFont.truetype(str(chemin), taille)
        bb = f.getbbox("H")
        h = bb[3] - bb[1]
        if abs(h - hauteur_cap) <= 0 or h > hauteur_cap:
            return f if h <= hauteur_cap else ImageFont.truetype(str(chemin), taille - 1)
        taille += 1
    return f


def ecrire(im, texte, chemin, hauteur_cap, couleur, x=None, haut=0, centre_x=None, largeur_max=None):
    """Texte en capitales dont le haut des lettres est à `haut` ; à gauche en x, ou centré sur centre_x."""
    f = police(chemin, hauteur_cap)
    if largeur_max:
        while f.getlength(texte) > largeur_max and hauteur_cap > 6:
            hauteur_cap -= 1
            f = police(chemin, hauteur_cap)
    bb = f.getbbox(texte)
    d = ImageDraw.Draw(im)
    if centre_x is not None:
        x = centre_x - (bb[2] - bb[0]) / 2 - bb[0]
    d.text((x, haut - f.getbbox("H")[1]), texte, font=f, fill=couleur)
    return f.getlength(texte)


def effacer_interp(a, x0, x1, y0, y1):
    """Efface la boîte (bornes comprises) : chaque colonne passe en dégradé de la ligne au-dessus à celle d'en dessous."""
    haut = a[y0 - 1, x0:x1 + 1].astype(float)
    bas = a[y1 + 1, x0:x1 + 1].astype(float)
    n = y1 - y0 + 2
    for k, y in enumerate(range(y0, y1 + 1), 1):
        t = k / n
        a[y, x0:x1 + 1] = np.round(haut * (1 - t) + bas * t).astype(a.dtype)


def dessin(im, sorte, cx, haut, bas):
    """Sapin ou montagne enneigée, en noir (anti-crénelé : dessiné 4 fois plus grand)."""
    k = 4
    w, h = im.size
    calque = Image.new("RGBA", (w * k, h * k), (0, 0, 0, 0))
    d = ImageDraw.Draw(calque)
    noir = (12, 12, 14, 255)
    H = bas - haut

    def P(x, y):
        return (x * k, y * k)
    if sorte == "sapin":
        etages = [(0.00, 0.34, 0.26), (0.18, 0.56, 0.36), (0.38, 0.80, 0.46)]   # (sommet, base, demi-largeur) / hauteur
        for s, b, dl in etages:
            d.polygon([P(cx, haut + s * H), P(cx - dl * H, haut + b * H), P(cx + dl * H, haut + b * H)], fill=noir)
        d.rectangle([P(cx - 0.06 * H, haut + 0.78 * H), P(cx + 0.06 * H, bas)], fill=noir)
    else:
        neige = (240, 240, 240, 255)
        grand = [P(cx + 0.06 * H, haut), P(cx - 0.40 * H, bas), P(cx + 0.46 * H, bas)]
        petit = [P(cx - 0.24 * H, haut + 0.36 * H), P(cx - 0.50 * H, bas), P(cx + 0.02 * H, bas)]
        d.polygon(petit, fill=noir)
        d.polygon(grand, fill=noir)
        # calotte de neige du grand sommet, bord en dents
        sx, sy = cx + 0.06 * H, haut
        pente_g, pente_d = 0.46 / 1.0, 0.40 / 1.0
        yb = 0.30 * H
        d.polygon([P(sx, sy), P(sx - pente_d * yb, sy + yb), P(sx - 0.07 * H, sy + 0.22 * H), P(sx - 0.02 * H, sy + 0.30 * H),
                   P(sx + 0.05 * H, sy + 0.21 * H), P(sx + pente_g * yb, sy + yb)], fill=neige)
    calque = calque.resize((w, h), Image.LANCZOS)
    im.alpha_composite(calque)


# ------------------------------------------------------------------------------------------------ cartes
def carte_face(b):
    im = Image.open(TEX / "item/keycard_red_front.png").convert("RGBA")
    a = np.asarray(im).copy()
    # colonne : « LVL » et le gros chiffre effacés (couleur unie de la colonne), « ACCESS » gardé
    a[27:52, 23:75] = ROUGE_COLONNE
    a[87:214, 27:100] = ROUGE_COLONNE
    # ligne « SECTOR C-03 | CLEARANCE III »
    zone = a[196:214, 145:371, :3]
    vif = zone[(zone[..., 0] > 150)]
    couleur_ligne = tuple(int(v) for v in np.median(vif, axis=0)) + (255,) if len(vif) else (232, 104, 108, 255)
    effacer_interp(a, 145, 370, 196, 213)
    im = Image.fromarray(a, "RGBA")
    ecrire(im, "BUNKER", OSWALD, 19, (12, 12, 14, 255), centre_x=61, haut=30)
    dessin(im, b["dessin"], 61, 94, 208)
    ecrire(im, b["site"] + "  |  " + b["nom"], DM_MEDIUM, 14, couleur_ligne, x=147, haut=198, largeur_max=262)
    return teinter(im, b["teinte"])


def carte_dos(b):
    im = Image.open(TEX / "item/keycard_red_back.png").convert("RGBA")
    a = np.asarray(im).copy()
    effacer_interp(a, 22, 352, 214, 252)
    im = Image.fromarray(a, "RGBA")
    ecrire(im, b["nom"], OSWALD, 35, ROSE_TITRE, x=26, haut=216, largeur_max=470)
    return teinter(im, b["teinte"])


def panneau(b):
    im = Image.open(TEX / "block/keycard/panel_red.png").convert("RGBA")
    a = np.asarray(im).copy()
    a[32:46, 6:43] = FOND_PANNEAU
    im = Image.fromarray(a, "RGBA")
    ecrire(im, b["court"], DM_BOLD, 9, ROSE_TITRE, x=9, haut=34, largeur_max=32)
    return teinter(im, b["teinte"])


def textures(b):
    """{chemin relatif à textures/ : image} pour ce bunker."""
    i = b["id"]
    t = {
        f"item/keycard_{i}_front.png": carte_face(b),
        f"item/keycard_{i}_back.png": carte_dos(b),
        f"item/keycard_{i}_edge.png": teinter(Image.open(TEX / "item/keycard_red_edge.png"), b["teinte"]),
        f"block/keycard/panel_{i}.png": panneau(b),
    }
    for n in ("body_red", "head_red", "screen_red_idle", "led_red_idle"):
        t[f"block/keycard/{n.replace('red', i)}.png"] = teinter(Image.open(TEX / f"block/keycard/{n}.png"), b["teinte"])
    return t


# ------------------------------------------------------------------------------------------------ modèles
def modeles(b):
    """{chemin relatif à assets : octets} : copies des modèles rouges, textures propres renommées (fins de ligne gardées)."""
    i = b["id"]
    m = {}
    bs = (ASSETS / "blockstates/card_reader_red.json").read_bytes()
    m[f"blockstates/card_reader_{i}.json"] = bs.replace(b"card_reader_red_", f"card_reader_{i}_".encode())
    for e in ETATS:
        s = (ASSETS / f"models/block/keycard/card_reader_red_{e}.json").read_bytes()
        for n in PROPRES:
            s = s.replace(f"keycard/{n}\"".encode(), f"keycard/{n.replace('red', i)}\"".encode())
        m[f"models/block/keycard/card_reader_{i}_{e}.json"] = s
    s = (ASSETS / "models/item/card_reader_red.json").read_bytes()
    m[f"models/item/card_reader_{i}.json"] = s.replace(b"card_reader_red_idle", f"card_reader_{i}_idle".encode())
    s = (ASSETS / "models/item/keycard_red.json").read_bytes()
    m[f"models/item/keycard_{i}.json"] = s.replace(b"keycard_red_", f"keycard_{i}_".encode())
    return m


# ------------------------------------------------------------------------------------------------ aperçu
def planche(dossier):
    dossier.mkdir(parents=True, exist_ok=True)
    rangs = []
    for b in [{"id": "red", "teinte": TEINTE_ROUGE, "orig": True}] + BUNKERS:
        if b.get("orig"):
            face = Image.open(TEX / "item/keycard_red_front.png").convert("RGBA")
            dos = Image.open(TEX / "item/keycard_red_back.png").convert("RGBA")
            blocs = [Image.open(TEX / f"block/keycard/{n}.png").convert("RGBA") for n in ("panel_red", "body_red", "head_red", "screen_red_idle", "led_red_idle")]
        else:
            t = textures(b)
            i = b["id"]
            face, dos = t[f"item/keycard_{i}_front.png"], t[f"item/keycard_{i}_back.png"]
            blocs = [t[f"block/keycard/{n}_{i}.png"] for n in ("panel", "body", "head")] + [t[f"block/keycard/screen_{i}_idle.png"], t[f"block/keycard/led_{i}_idle.png"]]
        rangs.append((face.crop((0, 0, 512, 320)), dos.crop((0, 0, 512, 320)), blocs))
    W = 512 * 2 + 5 * 168 + 80
    H = len(rangs) * 340 + 20
    pl = Image.new("RGBA", (W, H), (28, 30, 34, 255))
    for j, (face, dos, blocs) in enumerate(rangs):
        y = 10 + j * 340
        pl.alpha_composite(face, (10, y))
        pl.alpha_composite(dos, (532, y))
        for k, bl in enumerate(blocs):
            pl.alpha_composite(bl.resize((160, 160), Image.NEAREST), (1064 + k * 168, y + 80))
    pl.save(dossier / "cartes_bunker.png")
    print("planche :", dossier / "cartes_bunker.png")


def installer():
    for b in BUNKERS:
        for rel, im in textures(b).items():
            im.save(TEX / rel)
            print("écrit", TEX / rel)
        for rel, octets in modeles(b).items():
            (ASSETS / rel).write_bytes(octets)
            print("écrit", ASSETS / rel)


if __name__ == "__main__":
    p = argparse.ArgumentParser()
    p.add_argument("--rendus", type=Path)
    p.add_argument("--installer", action="store_true")
    a = p.parse_args()
    if a.rendus:
        planche(a.rendus)
    if a.installer:
        installer()
    if not a.rendus and not a.installer:
        p.print_help()
