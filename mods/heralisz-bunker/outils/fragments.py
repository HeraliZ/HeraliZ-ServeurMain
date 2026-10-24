#!/usr/bin/env python3
"""
Fragments des cartes d'accès de HeraliZ Bunker : chaque carte (verte, bleue, rouge) est cassée en 4 morceaux numérotés
1 à 4 (1 en haut à gauche, 2 en haut à droite, 3 en bas à gauche, 4 en bas à droite), qui se retrouvent dans le loot de
la carte et s'assemblent en une carte.

Chaque fragment est un vrai morceau de la carte : il découpe les textures de la carte (keycard_<couleur>_front / _back,
jamais modifiées) le long d'une déchirure en dents de scie, partagée par les morceaux voisins (les 4 morceaux
s'emboîtent exactement). Par-dessus : plastique blanchi le long de la cassure, petites fissures, liseré de la couleur
de rareté du fragment (carte verte : Rare, bleu ; bleue : Épique, violet ; rouge : Légendaire, or) et un hexagone
(logo HeraliZ) avec son numéro, gros devant, plus petit derrière.

Le modèle d'objet : une plaque de la même épaisseur que la carte (0,6 px), découpée par la transparence de sa texture,
et sa tranche faite de bandes qui suivent la déchirure (pas de 22,5°, les seuls angles que Minecraft permet aux
éléments) : tranche blanche cassée sur la déchirure, couleur de la carte sur les bords d'origine.

Écrit (jamais à la main) :
  - textures/item/keycard_fragment_<couleur>_<n>.png (512 x 512 : face en haut, dos en bas, 32 points par pixel de
    modèle comme la carte) et textures/item/keycard_fragment_<couleur>_edge_h.png / _edge_v.png (tranche cassée) ;
  - models/item/keycard_fragment_<couleur>_<n>.json.

    python mods/heralisz-bunker/outils/fragments.py --rendus DOSSIER     planches d'aperçu (rien d'installé)
    python mods/heralisz-bunker/outils/fragments.py --installer          écrit les fichiers dans les assets du mod

Les planches sont dessinées par un petit moteur logiciel qui lit les modèles comme Minecraft (sommets et UV de
FaceBakery, rotations d'éléments, transparence découpée) : formes et couleurs fidèles, pas la lumière du jeu.
"""
import argparse
import json
import math
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

RACINE = Path(__file__).resolve().parents[1]
ASSETS = RACINE / "src" / "client" / "resources" / "assets" / "heralisz_bunker"
DEPOT = RACINE.parents[1]
POLICE_CHIFFRE = DEPOT / "mods" / "heralisz-matchs" / "outils" / "bombe" / "polices" / "oswald-700.ttf"
POLICE_TEXTE = DEPOT / "launcher" / "app" / "assets" / "fonts" / "DMSans-Bold.ttf"

W, H = 512, 320          # carte dans sa texture (points), 32 points par pixel de modèle
PX = 32.0
COIN = 20.0              # rayon des coins arrondis de la carte (mesuré dans sa transparence)
Z0, Z1 = 7.7, 8.3        # épaisseur de la carte (keycard_<couleur>.json)
LARGEUR_BANDE = 0.08     # bandes de la tranche : rentrées dans le morceau (seule leur face extérieure est dessinée)
PAS = math.pi / 8        # 22,5°

# carte -> (couleur de rareté du fragment (Rarete de HeraliZ Loot), graine de la cassure)
CARTES = {
    "green": ((0x3F, 0x8C, 0xFF), 11),   # Rare
    "blue": ((0xA6, 0x4D, 0xFF), 23),    # Épique
    "red": ((0xF2, 0xB8, 0x3A), 37),     # Légendaire
}


# =============================================================================================== cassure
def dechirure(rng, s, e, ecart=0.55):
    """Ligne cassée de s à e (points), chaque morceau dans une direction multiple de 22,5°, toujours vers e."""
    pts = [np.array(s, float)]
    p = np.array(s, float)
    e = np.array(e, float)
    while True:
        d = e - p
        dist = math.hypot(*d)
        if dist < 30:
            break
        cible = math.atan2(d[1], d[0])
        a = cible + max(-ecart, min(ecart, rng.gauss(0, ecart * 0.75)))
        q = round(a / PAS) * PAS
        if math.cos(q - cible) < 0.6:
            q = round(cible / PAS) * PAS
        lg = min(rng.uniform(7, 19), dist * 0.6)
        p = p + lg * np.array([math.cos(q), math.sin(q)])
        pts.append(p)
    # fin en deux morceaux de directions permises qui encadrent la direction restante
    d = e - p
    cible = math.atan2(d[1], d[0])
    a = math.floor(cible / PAS) * PAS
    b = a + PAS
    da = np.array([math.cos(a), math.sin(a)])
    db = np.array([math.cos(b), math.sin(b)])
    m = np.array([da, db]).T
    s_, t_ = np.linalg.solve(m, d)
    if s_ > 0.5 and t_ > 0.5:
        pts.append(p + s_ * da)
    pts.append(e)
    return pts


def arc(cx, cy, a0, a1):
    """Coin arrondi (rayon COIN, du point d'angle a0 au point d'angle a1) en trois morceaux de pente permise."""
    s = np.array([cx + COIN * math.cos(a0), cy + COIN * math.sin(a0)])
    e = np.array([cx + COIN * math.cos(a1), cy + COIN * math.sin(a1)])
    return recaler([s, e])


def recaler(pts):
    """Coin : ligne aux mêmes extrémités, en trois morceaux égaux de directions permises (a - 22,5°, a, a + 22,5°,
    a = direction de la corde, multiple de 45°)."""
    s, e = pts[0], pts[-1]
    d = e - s
    a = round(math.atan2(d[1], d[0]) / PAS) * PAS
    lg = math.hypot(*d) / (1 + 2 * math.cos(PAS))
    p1 = s + lg * np.array([math.cos(a - PAS), math.sin(a - PAS)])
    p2 = p1 + lg * np.array([math.cos(a), math.sin(a)])
    return [s, p1, p2, e]


def segments_croisent(p, q, r, s):
    def orient(a, b, c):
        return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0])
    o1, o2, o3, o4 = orient(p, q, r), orient(p, q, s), orient(r, s, p), orient(r, s, q)
    return o1 * o2 < -1e-9 and o3 * o4 < -1e-9


def simple(poly):
    n = len(poly)
    for i in range(n):
        for j in range(i + 2, n):
            if i == 0 and j == n - 1:
                continue
            if segments_croisent(poly[i], poly[(i + 1) % n], poly[j], poly[(j + 1) % n]):
                return False
    return True


def morceaux(couleur):
    """Les 4 morceaux d'une carte : liste de (polygone, genres des côtés) en points de texture (y vers le bas).
    Genre d'un côté (du point i au point i+1) : « bord » (bord d'origine de la carte) ou « cassure »."""
    graine = CARTES[couleur][1]
    for essai in range(200):
        rng = random.Random(graine * 1000 + essai)
        j = (W / 2 + rng.uniform(-18, 18), H / 2 + rng.uniform(-12, 12))
        t = (W / 2 + rng.uniform(-34, 34), 0.0)
        b = (W / 2 + rng.uniform(-34, 34), float(H))
        l = (0.0, H / 2 + rng.uniform(-22, 22))
        r = (float(W), H / 2 + rng.uniform(-22, 22))
        t_haut = dechirure(rng, t, j)          # haut -> centre
        t_bas = dechirure(rng, j, b)           # centre -> bas
        t_g = dechirure(rng, l, j)             # gauche -> centre
        t_d = dechirure(rng, j, r)             # centre -> droite
        j = t_haut[-1]
        c_hg = arc(COIN, COIN, math.pi, 1.5 * math.pi)                  # (0,20) -> (20,0)
        c_hd = arc(W - COIN, COIN, 1.5 * math.pi, 2 * math.pi)          # (492,0) -> (512,20)
        c_bd = arc(W - COIN, H - COIN, 0, 0.5 * math.pi)                # (512,300) -> (492,320)
        c_bg = arc(COIN, H - COIN, 0.5 * math.pi, math.pi)              # (20,320) -> (0,300)

        def poly(*parts):
            pts, genres = [], []
            for liste, genre in parts:
                for k, p in enumerate(liste):
                    if pts and np.allclose(pts[-1], p):
                        continue
                    pts.append(np.array(p, float))
                    genres.append(genre)
            if np.allclose(pts[0], pts[-1]):
                pts.pop()
                genres.pop()
            return pts, genres

        # genres : le côté qui PART de chaque point. Les côtés droits le long des bords de la carte sont des « bord ».
        rev = lambda x: list(reversed(x))  # noqa: E731
        m1 = poly((c_hg, "bord"), (t_haut[:-1], "cassure"), (rev(t_g)[:-1], "cassure"), ([np.array(l)], "bord"))
        m2 = poly((c_hd, "bord"), (t_d[::-1][:-1], "cassure"), (rev(t_haut)[:-1], "cassure"), ([np.array(t)], "bord"))
        m3 = poly((c_bg, "bord"), (t_g[:-1], "cassure"), (t_bas[:-1], "cassure"), ([np.array(b)], "bord"))
        m4 = poly((c_bd, "bord"), (rev(t_bas)[:-1], "cassure"), (t_d[:-1], "cassure"), ([np.array(r)], "bord"))
        # m2 commence au coin haut droit ; son dernier point (t) ferme sur le coin c_hd par un côté de bord
        res = [m1, m2, m3, m4]
        aires = [aire(p) for p, _ in res]
        if all(simple(p) for p, _ in res) and abs(sum(abs(a) for a in aires) - aire_carte()) < 60:
            return res
    raise SystemExit("cassure impossible pour " + couleur)


def aire(p):
    return 0.5 * sum(p[i][0] * p[(i + 1) % len(p)][1] - p[(i + 1) % len(p)][0] * p[i][1] for i in range(len(p)))


def aire_carte():
    c_hg = arc(COIN, COIN, math.pi, 1.5 * math.pi)
    c_hd = arc(W - COIN, COIN, 1.5 * math.pi, 2 * math.pi)
    c_bd = arc(W - COIN, H - COIN, 0, 0.5 * math.pi)
    c_bg = arc(COIN, H - COIN, 0.5 * math.pi, math.pi)
    return abs(aire(c_hg + c_hd + c_bd + c_bg))


def corriger_genres(pts, genres):
    """Un côté dont les deux bouts sont sur le bord de la carte et qui est droit le long du bord est un « bord »."""
    out = []
    n = len(pts)
    for i in range(n):
        a, b = pts[i], pts[(i + 1) % n]
        sur_bord = (abs(a[0] - b[0]) < 1e-6 and a[0] in (0.0, float(W))) or \
                   (abs(a[1] - b[1]) < 1e-6 and a[1] in (0.0, float(H)))
        out.append("bord" if sur_bord else genres[i])
    return out


# =============================================================================================== textures
def charger(nom):
    return np.array(Image.open(ASSETS / "textures" / "item" / nom).convert("RGBA")).astype(np.float32) / 255.0


def masque(pts, w, h, dx=0.0, dy=0.0, miroir=False):
    """Masque binaire (sur-échantillonné x4) du polygone, décalé de (dx, dy), miroir horizontal sur la carte."""
    k = 4
    im = Image.new("L", (w * k, h * k), 0)
    q = []
    for p in pts:
        x = (W - p[0]) if miroir else p[0]
        q.append(((x - dx) * k, (p[1] - dy) * k))
    ImageDraw.Draw(im).polygon(q, fill=255)
    im = im.resize((w, h), Image.BOX)
    return (np.array(im).astype(np.float32) / 255.0) >= 0.5


def distance_segments(segs, w, h):
    """Distance (points) de chaque point de la grille w x h au plus proche des segments [(a, b)]."""
    ys, xs = np.mgrid[0:h, 0:w].astype(np.float32)
    xs += 0.5
    ys += 0.5
    d = np.full((h, w), 1e9, np.float32)
    for a, b in segs:
        ab = b - a
        l2 = float(ab @ ab) or 1e-9
        t = np.clip(((xs - a[0]) * ab[0] + (ys - a[1]) * ab[1]) / l2, 0, 1)
        px = a[0] + t * ab[0] - xs
        py = a[1] + t * ab[1] - ys
        d = np.minimum(d, np.sqrt(px * px + py * py))
    return d


def melanger(img, couleur, alpha):
    a = np.clip(alpha, 0, 1)[..., None]
    img[..., :3] = img[..., :3] * (1 - a) + np.array(couleur, np.float32)[None, None, :] / 255.0 * a


def hexagone(cx, cy, r):
    return [(cx + r * math.cos(math.radians(90 + 60 * i)), cy + r * math.sin(math.radians(90 + 60 * i)))
            for i in range(6)]


def pastille(taille, n, rarete, carte_rgb, petite=False):
    """Hexagone sombre bordé de la couleur de rareté, numéro au milieu (image RGBA taille x taille)."""
    k = 4
    s = taille * k
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c = s / 2
    r = s / 2 - 2 * k
    # ombre portée
    ombre = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    ImageDraw.Draw(ombre).polygon(hexagone(c + 3 * k, c + 4 * k, r), fill=(0, 0, 0, 150))
    ombre = ombre.filter(ImageFilter.GaussianBlur(4 * k))
    im.alpha_composite(ombre)
    d = ImageDraw.Draw(im)
    d.polygon(hexagone(c, c, r), fill=rarete + (255,))
    d.polygon(hexagone(c, c, r - 5 * k), fill=(10, 10, 13, 255))
    # léger dégradé vers le bas et filet intérieur de la couleur de la carte
    grad = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    gd = ImageDraw.Draw(grad)
    for i in range(0, s, k):
        a = int(70 * i / s)
        gd.line([(0, i), (s, i)], fill=(255, 255, 255, max(0, 34 - a)), width=k)
    msk = Image.new("L", (s, s), 0)
    ImageDraw.Draw(msk).polygon(hexagone(c, c, r - 5 * k), fill=255)
    im.paste(Image.alpha_composite(im, grad), (0, 0), msk)
    d = ImageDraw.Draw(im)
    d.polygon(hexagone(c, c, r - 8.5 * k), outline=carte_rgb + (200,), width=int(1.5 * k))
    # numéro
    police = ImageFont.truetype(str(POLICE_CHIFFRE), int(s * (0.52 if not petite else 0.56)))
    txt = str(n)
    bb = d.textbbox((0, 0), txt, font=police)
    tw, th = bb[2] - bb[0], bb[3] - bb[1]
    x = c - tw / 2 - bb[0] - (s * 0.06 if not petite else 0)
    y = c - th / 2 - bb[1] - s * 0.02
    d.text((x + 2 * k, y + 3 * k), txt, font=police, fill=(0, 0, 0, 200))
    d.text((x, y), txt, font=police, fill=(250, 250, 252, 255))
    if not petite:
        p2 = ImageFont.truetype(str(POLICE_TEXTE), int(s * 0.17))
        d.text((x + tw + bb[0] + s * 0.015, y + bb[1] + th - s * 0.17), "/4", font=p2, fill=rarete + (255,))
        p3 = ImageFont.truetype(str(POLICE_TEXTE), int(s * 0.085))
        t3 = "FRAGMENT"
        b3 = d.textbbox((0, 0), t3, font=p3)
        d.text((c - (b3[2] - b3[0]) / 2 - b3[0], c - r * 0.62 - (b3[3] - b3[1]) / 2 - b3[1]), t3, font=p3,
               fill=(205, 205, 212, 255))
    return im.resize((taille, taille), Image.LANCZOS)


def point_interieur(pts, w, h, dx, dy, miroir=False):
    """Point du morceau le plus loin de ses bords (et sa distance), en points de la zone découpée."""
    segs = []
    n = len(pts)
    for i in range(n):
        a, b = pts[i].copy(), pts[(i + 1) % n].copy()
        if miroir:
            a[0], b[0] = W - a[0], W - b[0]
        segs.append((a - [dx, dy], b - [dx, dy]))
    m = masque(pts, w, h, dx, dy, miroir)
    d = distance_segments(segs, w, h)
    d[~m] = -1
    y, x = np.unravel_index(np.argmax(d), d.shape)
    return x + 0.5, y + 0.5, float(d[y, x])


def face(fond, pts, genres, rarete, carte_rgb, n, dos, rng):
    """Image RGBA (largeur x hauteur de la boîte du morceau) d'une face du morceau."""
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    if dos:
        xs = [W - x for x in xs]
    x0, y0 = math.floor(min(xs)), math.floor(min(ys))
    x1, y1 = math.ceil(max(xs)), math.ceil(max(ys))
    w, h = x1 - x0, y1 - y0
    img = fond[y0:y1, x0:x1].copy()
    m = masque(pts, w, h, x0, y0, dos)
    m &= img[..., 3] > 0.5
    # côtés (dans les coordonnées de la zone)
    segs, segs_c = [], []
    k = len(pts)
    for i in range(k):
        a, b = pts[i].copy(), pts[(i + 1) % k].copy()
        if dos:
            a[0], b[0] = W - a[0], W - b[0]
        s = (a - [x0, y0], b - [x0, y0])
        segs.append(s)
        if genres[i] == "cassure":
            segs_c.append(s)
    d_tout = distance_segments(segs, w, h)
    d_cass = distance_segments(segs_c, w, h)
    # plastique blanchi le long de la cassure, avec un grain irrégulier
    grain = np.random.default_rng(rng.randrange(1 << 30)).normal(128, 60, (h, w)).clip(0, 255).astype(np.uint8)
    bruit = np.array(Image.fromarray(grain, "L").filter(ImageFilter.GaussianBlur(1.2))).astype(np.float32) / 255.0
    blanc = np.clip(1 - d_cass / 7.0, 0, 1) ** 1.6 * (0.55 + 0.35 * bruit)
    melanger(img, (236, 238, 240), blanc * 0.85)
    # petites fissures qui partent de la cassure
    fiss = Image.new("L", (w, h), 0)
    fd = ImageDraw.Draw(fiss)
    for a, b in segs_c:
        lg = math.hypot(*(b - a))
        for _ in range(int(lg / 9)):
            t = rng.random()
            p = a + (b - a) * t
            nrm = np.array([-(b - a)[1], (b - a)[0]]) / max(lg, 1e-6)
            for sens in (1, -1):
                q = p + nrm * sens * 3
                if 0 <= q[0] < w and 0 <= q[1] < h and m[int(q[1]), int(q[0])]:
                    nrm = nrm * sens
                    break
            ang = math.atan2(nrm[1], nrm[0]) + rng.uniform(-0.7, 0.7)
            ln = rng.uniform(3, 11)
            q = p + ln * np.array([math.cos(ang), math.sin(ang)])
            fd.line([tuple(p), tuple(q)], fill=int(rng.uniform(90, 170)), width=1)
    fiss = np.array(fiss.filter(ImageFilter.GaussianBlur(0.35))).astype(np.float32) / 255.0
    melanger(img, (245, 246, 248), fiss)
    # ombre douce à l'intérieur de la cassure (relief) puis liseré de rareté sur tout le tour
    melanger(img, (0, 0, 0), np.clip(1 - np.abs(d_cass - 7.5) / 3.5, 0, 1) * 0.18)
    lis = np.clip(1 - (d_tout - 2.4) / 0.9, 0, 1)
    halo = np.clip(1 - d_tout / 11.0, 0, 1) ** 2 * 0.45
    melanger(img, rarete, np.maximum(lis * 0.95, halo))
    # pastille et numéro
    if not dos:
        cx, cy, dist = point_interieur(pts, w, h, x0, y0, dos)
        taille = int(min(128, 2 * dist * 0.93))
    else:
        cx, cy, dist = point_interieur(pts, w, h, x0, y0, dos)
        taille = int(min(76, 2 * dist * 0.8))
    pas_ = pastille(taille, n, rarete, carte_rgb, petite=dos)
    pa = np.array(pas_).astype(np.float32) / 255.0
    px0, py0 = int(round(cx - taille / 2)), int(round(cy - taille / 2))
    zx0, zy0 = max(0, px0), max(0, py0)
    zx1, zy1 = min(w, px0 + taille), min(h, py0 + taille)
    sub = pa[zy0 - py0:zy1 - py0, zx0 - px0:zx1 - px0]
    a = sub[..., 3:4]
    img[zy0:zy1, zx0:zx1, :3] = img[zy0:zy1, zx0:zx1, :3] * (1 - a) + sub[..., :3] * a
    img[..., 3] = m.astype(np.float32)
    img[~m, :3] = 0
    return img, (x0, y0, w, h)


def tranche(carte_rgb, vertical):
    """Tranche cassée (64 x 64) : plastique blanc fibreux entre deux fines peaux de la couleur de la carte.
    Horizontal : l'épaisseur va le long de v ; vertical : le long de u."""
    rng = np.random.default_rng(7)
    t = 64
    img = np.zeros((t, t, 4), np.float32)
    img[..., 3] = 1
    v = (np.arange(t) + 0.5) / t
    peau = (v < 0.16) | (v > 0.84)
    fibres = rng.random(t).astype(np.float32)
    fibres = np.convolve(fibres, np.ones(3) / 3, mode="same")
    for i in range(t):
        if peau[i]:
            c = np.array(carte_rgb, np.float32) / 255.0 * (0.8 + 0.2 * rng.random(t))[:, None]
            img[i, :, :3] = c
        else:
            g = 0.78 + 0.16 * fibres[None, :] + 0.06 * rng.random(t)[None, :]
            img[i, :, :3] = np.clip(g, 0, 1).T * np.array([0.98, 0.99, 1.0])
    if vertical:
        img = img.transpose(1, 0, 2)
    return img


def png(img):
    return Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGBA")


# =============================================================================================== modèle
def r4(x):
    return round(float(x) + 0.0, 4)


def bande(a, b, genre, couleur, centre):
    """Élément de tranche du côté a -> b (coordonnées de modèle, morceau parcouru dans n'importe quel sens) : boîte
    rentrée dans le morceau, une seule face, tournée vers l'extérieur."""
    d = b - a
    lg = math.hypot(*d)
    theta = math.atan2(d[1], d[0])
    phi = math.degrees(theta) % 180.0
    phi = round(phi / 22.5) * 22.5 % 180.0
    if phi in (0.0, 22.5, 45.0, 135.0, 157.5):
        axe = "x"
        rot = phi if phi <= 45 else phi - 180
    else:
        axe = "y"
        rot = phi - 90
    m = (a + b) / 2
    # extérieur : du côté opposé au centre du morceau
    nrm = np.array([-d[1], d[0]]) / max(lg, 1e-9)
    if (centre - m) @ nrm > 0:
        nrm = -nrm
    # normale dans le repère de la boîte avant rotation
    c, s = math.cos(math.radians(-rot)), math.sin(math.radians(-rot))
    nl = np.array([c * nrm[0] - s * nrm[1], s * nrm[0] + c * nrm[1]])
    e = LARGEUR_BANDE
    tex = "#cassure_h" if axe == "x" else "#cassure_v"
    if genre == "bord":
        tex = "#bord"
    if axe == "x":
        frm = [m[0] - lg / 2, 0, Z0]
        to = [m[0] + lg / 2, 0, Z1]
        if nl[1] > 0:
            frm[1], to[1], cote = m[1] - e, m[1], "up"
        else:
            frm[1], to[1], cote = m[1], m[1] + e, "down"
        uv = [0, 0, 16, 16]
    else:
        frm = [0, m[1] - lg / 2, Z0]
        to = [0, m[1] + lg / 2, Z1]
        if nl[0] > 0:
            frm[0], to[0], cote = m[0] - e, m[0], "east"
        else:
            frm[0], to[0], cote = m[0], m[0] + e, "west"
        uv = [0, 0, 16, 16]
    el = {"from": [r4(v) for v in frm], "to": [r4(v) for v in to],
          "faces": {cote: {"uv": uv, "texture": tex}}}
    if rot:
        el["rotation"] = {"angle": rot, "axis": "z", "origin": [r4(m[0]), r4(m[1]), 8]}
    return el


AFFICHAGE_CARTE = {
    "thirdperson_righthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "thirdperson_lefthand": {"rotation": [0, 0, 0], "translation": [0, 3, 1], "scale": [0.55, 0.55, 0.55]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
}


def modele(couleur, n, pts, genres, boite_face, boite_dos):
    """Modèle d'objet du morceau n : centré sur (8, 8), même épaisseur que la carte."""
    x0, y0, w, h = boite_face
    cx, cy = x0 + w / 2, y0 + h / 2
    to_m = lambda p: np.array([(p[0] - cx) / PX + 8, 8 - (p[1] - cy) / PX])  # noqa: E731
    X0, X1 = 8 - w / PX / 2, 8 + w / PX / 2
    Y0, Y1 = 8 - h / PX / 2, 8 + h / PX / 2
    nom = f"heralisz_bunker:item/keycard_fragment_{couleur}_{n}"
    els = [{
        "from": [r4(X0), r4(Y0), Z0], "to": [r4(X1), r4(Y1), Z1],
        "faces": {
            "south": {"uv": [0, 0, r4(w / PX), r4(h / PX)], "texture": "#face"},
            "north": {"uv": [0, 8, r4(boite_dos[2] / PX), r4(8 + boite_dos[3] / PX)], "texture": "#face"},
        },
    }]
    mp = [to_m(p) for p in pts]
    centre = np.mean(mp, axis=0)
    # côtés consécutifs alignés et de même genre fusionnés
    k = len(mp)
    cotes = []
    for i in range(k):
        a, b, g = mp[i], mp[(i + 1) % k], genres[i]
        if cotes:
            pa, pb, pg = cotes[-1]
            d1, d2 = pb - pa, b - a
            if pg == g and abs(d1[0] * d2[1] - d1[1] * d2[0]) < 1e-6 and d1 @ d2 > 0:
                cotes[-1] = (pa, b, g)
                continue
        cotes.append((a, b, g))
    for a, b, g in cotes:
        if math.hypot(*(b - a)) < 1e-3:
            continue
        els.append(bande(a, b, g, couleur, centre))
    echelle = round(min(15.2 / (w / PX), 15.2 / (h / PX), 1.9), 3)
    affichage = dict(AFFICHAGE_CARTE)
    affichage["gui"] = {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [echelle] * 3}
    return {
        "render_type": "minecraft:cutout",
        "gui_light": "front",
        "textures": {
            "face": nom,
            "cassure_h": f"heralisz_bunker:item/keycard_fragment_{couleur}_edge_h",
            "cassure_v": f"heralisz_bunker:item/keycard_fragment_{couleur}_edge_v",
            "bord": f"heralisz_bunker:item/keycard_{couleur}_edge",
            "particle": nom,
        },
        "elements": els,
        "display": affichage,
    }


def fabriquer():
    """{couleur: {"fragments": [(modèle, texture RGBA 512, (x0, y0) du morceau sur la carte)], "h": tranche, "v": ...}}"""
    out = {}
    for couleur, (rarete, graine) in CARTES.items():
        devant = charger(f"keycard_{couleur}_front.png")
        dos = charger(f"keycard_{couleur}_back.png")
        tr = charger(f"keycard_{couleur}_edge.png")[0, 0, :3]
        carte_rgb = tuple(int(v * 255) for v in tr)
        liste = []
        for n, (pts, genres) in enumerate(morceaux(couleur), start=1):
            genres = corriger_genres(pts, genres)
            rng = random.Random(graine * 31 + n)
            f, bf = face(devant, pts, genres, rarete, carte_rgb, n, False, rng)
            b, bd = face(dos, pts, genres, rarete, carte_rgb, n, True, rng)
            tex = np.zeros((512, 512, 4), np.float32)
            tex[:bf[3], :bf[2]] = f
            tex[256:256 + bd[3], :bd[2]] = b
            liste.append((modele(couleur, n, pts, genres, bf, bd), tex, (bf[0], bf[1], bf[2], bf[3])))
        out[couleur] = {"fragments": liste, "h": tranche(carte_rgb, False), "v": tranche(carte_rgb, True)}
    return out


def json_modele(mdl):
    """JSON du modèle : un élément et une vue par ligne."""
    lignes = ["{"]
    for k, v in mdl.items():
        if isinstance(v, dict) and k in ("textures", "display"):
            lignes.append(f' {json.dumps(k)}: {{')
            lignes.append(",\n".join(f"  {json.dumps(a)}: {json.dumps(b, ensure_ascii=False)}" for a, b in v.items()))
            lignes.append(" },")
        elif isinstance(v, list):
            lignes.append(f' {json.dumps(k)}: [')
            lignes.append(",\n".join("  " + json.dumps(e, ensure_ascii=False) for e in v))
            lignes.append(" ],")
        else:
            lignes.append(f" {json.dumps(k)}: {json.dumps(v, ensure_ascii=False)},")
    lignes[-1] = lignes[-1].rstrip(",")
    lignes.append("}")
    texte = "\n".join(lignes) + "\n"
    assert json.loads(texte) == mdl
    return texte


def installer(tout):
    tex_dir = ASSETS / "textures" / "item"
    mod_dir = ASSETS / "models" / "item"
    for couleur, d in tout.items():
        png(d["h"]).save(tex_dir / f"keycard_fragment_{couleur}_edge_h.png", optimize=True)
        png(d["v"]).save(tex_dir / f"keycard_fragment_{couleur}_edge_v.png", optimize=True)
        for n, (mdl, tex, _) in enumerate(d["fragments"], start=1):
            png(tex).save(tex_dir / f"keycard_fragment_{couleur}_{n}.png", optimize=True)
            with open(mod_dir / f"keycard_fragment_{couleur}_{n}.json", "w", encoding="utf-8", newline="\n") as f:
                f.write(json_modele(mdl))
            print("écrit :", f"keycard_fragment_{couleur}_{n}", len(mdl["elements"]), "éléments")


# =============================================================================================== rendus
# sommets des faces dans l'ordre de FaceBakery (FaceInfo) ; UV : v0 (u0,v0), v1 (u0,v1), v2 (u1,v1), v3 (u1,v0)
FACES = {
    "down": [("x0", "y0", "z1"), ("x0", "y0", "z0"), ("x1", "y0", "z0"), ("x1", "y0", "z1")],
    "up": [("x0", "y1", "z0"), ("x0", "y1", "z1"), ("x1", "y1", "z1"), ("x1", "y1", "z0")],
    "north": [("x1", "y1", "z0"), ("x1", "y0", "z0"), ("x0", "y0", "z0"), ("x0", "y1", "z0")],
    "south": [("x0", "y1", "z1"), ("x0", "y0", "z1"), ("x1", "y0", "z1"), ("x1", "y1", "z1")],
    "west": [("x0", "y1", "z0"), ("x0", "y0", "z0"), ("x0", "y0", "z1"), ("x0", "y1", "z1")],
    "east": [("x1", "y1", "z1"), ("x1", "y0", "z1"), ("x1", "y0", "z0"), ("x1", "y1", "z0")],
}


def quads(mdl, textures, decalage=(0.0, 0.0, 0.0)):
    """Quadrilatères (sommets 4x3, uv 4x2, image) d'un modèle d'objet, comme FaceBakery."""
    out = []
    for el in mdl["elements"]:
        f, t = el["from"], el["to"]
        v = {"x0": f[0], "y0": f[1], "z0": f[2], "x1": t[0], "y1": t[1], "z1": t[2]}
        rot = el.get("rotation")
        for nom, fc in el["faces"].items():
            p = np.array([[v[a], v[b], v[c]] for a, b, c in FACES[nom]], np.float64)
            if rot:
                o = np.array(rot["origin"], np.float64)
                a = math.radians(rot["angle"])
                ca, sa = math.cos(a), math.sin(a)
                q = p - o
                if rot["axis"] == "z":
                    q = np.stack([q[:, 0] * ca - q[:, 1] * sa, q[:, 0] * sa + q[:, 1] * ca, q[:, 2]], 1)
                elif rot["axis"] == "y":
                    q = np.stack([q[:, 0] * ca + q[:, 2] * sa, q[:, 1], -q[:, 0] * sa + q[:, 2] * ca], 1)
                else:
                    q = np.stack([q[:, 0], q[:, 1] * ca - q[:, 2] * sa, q[:, 1] * sa + q[:, 2] * ca], 1)
                p = q + o
            p = p + np.array(decalage)
            u0, v0, u1, v1 = fc["uv"]
            uv = np.array([[u0, v0], [u0, v1], [u1, v1], [u1, v0]], np.float64)
            ref = fc["texture"].lstrip("#")
            img = textures[mdl["textures"][ref]]
            out.append((p, uv, img))
    return out


def rendre(liste_quads, taille, yaw=0.0, pitch=0.0, zoom=1.0, centre=(8, 8, 8), fond=(0, 0, 0, 0), ss=3,
           lumiere=True):
    """Rendu orthographique (yaw, pitch en degrés ; zoom = pixels d'image par pixel de modèle)."""
    S = taille[0] * ss, taille[1] * ss
    col = np.zeros((S[1], S[0], 4), np.float32)
    col[...] = np.array(fond, np.float32) / 255.0
    zb = np.full((S[1], S[0]), -1e9, np.float32)
    cy, sy = math.cos(math.radians(yaw)), math.sin(math.radians(yaw))
    cp, sp = math.cos(math.radians(pitch)), math.sin(math.radians(pitch))
    lum = np.array([-0.35, 0.55, 0.76])
    lum /= np.linalg.norm(lum)
    for p, uv, img in liste_quads:
        q = p - np.array(centre)
        x = q[:, 0] * cy + q[:, 2] * sy
        z = -q[:, 0] * sy + q[:, 2] * cy
        y = q[:, 1] * cp - z * sp
        z = q[:, 1] * sp + z * cp
        sx = S[0] / 2 + x * zoom * ss
        syy = S[1] / 2 - y * zoom * ss
        nrm = np.cross(np.array([sx[1] - sx[0], syy[1] - syy[0], 0]), np.array([sx[3] - sx[0], syy[3] - syy[0], 0]))
        # face vue de dos : non dessinée (comme l'entityCutout des objets)
        if nrm[2] >= 0:
            continue
        n3 = np.cross(p[1] - p[0], p[3] - p[0])
        n3 /= (np.linalg.norm(n3) or 1)
        # normale dans le repère de la vue
        nx = n3[0] * cy + n3[2] * sy
        nz = -n3[0] * sy + n3[2] * cy
        ny = n3[1] * cp - nz * sp
        nz = n3[1] * sp + nz * cp
        k = (0.55 + 0.45 * max(0.0, float(np.dot([nx, ny, nz], lum)))) if lumiere else 1.0
        th, tw = img.shape[:2]
        for tri in ((0, 1, 2), (0, 2, 3)):
            X = sx[list(tri)]
            Y = syy[list(tri)]
            Z = z[list(tri)]
            U = uv[list(tri), 0] / 16 * tw
            V = uv[list(tri), 1] / 16 * th
            xa, xb = max(0, int(math.floor(X.min()))), min(S[0], int(math.ceil(X.max())) + 1)
            ya, yb = max(0, int(math.floor(Y.min()))), min(S[1], int(math.ceil(Y.max())) + 1)
            if xa >= xb or ya >= yb:
                continue
            gy, gx = np.mgrid[ya:yb, xa:xb].astype(np.float64) + 0.5
            den = (Y[1] - Y[2]) * (X[0] - X[2]) + (X[2] - X[1]) * (Y[0] - Y[2])
            if abs(den) < 1e-12:
                continue
            l0 = ((Y[1] - Y[2]) * (gx - X[2]) + (X[2] - X[1]) * (gy - Y[2])) / den
            l1 = ((Y[2] - Y[0]) * (gx - X[2]) + (X[0] - X[2]) * (gy - Y[2])) / den
            l2 = 1 - l0 - l1
            dedans = (l0 >= -1e-6) & (l1 >= -1e-6) & (l2 >= -1e-6)
            if not dedans.any():
                continue
            zz = l0 * Z[0] + l1 * Z[1] + l2 * Z[2]
            uu = np.clip(l0 * U[0] + l1 * U[1] + l2 * U[2], 0, tw - 1e-3)
            vv = np.clip(l0 * V[0] + l1 * V[1] + l2 * V[2], 0, th - 1e-3)
            texel = img[vv.astype(int), uu.astype(int)]
            ok = dedans & (texel[..., 3] > 0.1) & (zz > zb[ya:yb, xa:xb] + 1e-4)
            zb[ya:yb, xa:xb][ok] = zz[ok]
            c = texel[..., :3] * k
            col[ya:yb, xa:xb, :3][ok] = c[ok]
            col[ya:yb, xa:xb, 3][ok] = 1
    im = png(col)
    return im.resize(taille, Image.LANCZOS)


def textures_de(tout):
    t = {}
    for couleur, d in tout.items():
        t[f"heralisz_bunker:item/keycard_fragment_{couleur}_edge_h"] = d["h"]
        t[f"heralisz_bunker:item/keycard_fragment_{couleur}_edge_v"] = d["v"]
        t[f"heralisz_bunker:item/keycard_{couleur}_edge"] = charger(f"keycard_{couleur}_edge.png")
        t[f"heralisz_bunker:item/keycard_{couleur}_front"] = charger(f"keycard_{couleur}_front.png")
        t[f"heralisz_bunker:item/keycard_{couleur}_back"] = charger(f"keycard_{couleur}_back.png")
        for n, (_, tex, _) in enumerate(d["fragments"], start=1):
            t[f"heralisz_bunker:item/keycard_fragment_{couleur}_{n}"] = tex
    return t


NOMS = {"green": "Carte verte · fragments Rare", "blue": "Carte bleue · fragments Épique",
        "red": "Carte rouge · fragments Légendaire"}


def fond_planche(w, h):
    im = Image.new("RGBA", (w, h), (24, 26, 31, 255))
    d = ImageDraw.Draw(im)
    for y in range(h):
        a = int(10 * y / h)
        d.line([(0, y), (w, y)], fill=(24 + a, 26 + a, 31 + a, 255))
    return im


def case_inventaire(icone, rarete, taille=96):
    """Icône dans une case façon inventaire, avec le léger dégradé de rareté de HeraliZ Loot (approximation)."""
    c = Image.new("RGBA", (taille, taille), (40, 42, 48, 255))
    d = ImageDraw.Draw(c)
    for y in range(taille):
        a = (y / taille) ** 2 * 0.35
        col = tuple(int(40 * (1 - a) + rarete[i] * a) for i in range(3)) + (255,)
        d.line([(0, y), (taille, y)], fill=col)
    d.rectangle([0, taille - 4, taille, taille], fill=rarete + (255,))
    d.rectangle([0, 0, taille - 1, taille - 1], outline=(70, 72, 80, 255))
    ic = icone.resize((taille - 12, taille - 12), Image.LANCZOS)
    c.alpha_composite(ic, (6, 6))
    return c


def rendus(tout, dossier):
    dossier = Path(dossier)
    dossier.mkdir(parents=True, exist_ok=True)
    texs = textures_de(tout)
    p_titre = ImageFont.truetype(str(POLICE_TEXTE), 26)
    p_leg = ImageFont.truetype(str(POLICE_TEXTE), 16)
    cartes_json = {c: json.load(open(ASSETS / "models" / "item" / f"keycard_{c}.json", encoding="utf-8")) for c in CARTES}
    planches_assemblees = []
    for couleur, d in tout.items():
        rarete = CARTES[couleur][0]
        W_, H_ = 1560, 1040
        im = fond_planche(W_, H_)
        dr = ImageDraw.Draw(im)
        dr.text((30, 22), "Fragments · " + NOMS[couleur], font=p_titre, fill=(235, 236, 240, 255))
        dr.text((30, 58), "icône (vue de l'inventaire) · case avec la rareté · 3/4 · dos", font=p_leg,
                fill=(160, 162, 170, 255))
        for i, (mdl, tex, _) in enumerate(d["fragments"]):
            q = quads(mdl, texs)
            x = 30 + i * 380
            gui = mdl["display"]["gui"]["scale"][0]
            icone = rendre(q, (320, 320), zoom=20 * gui, lumiere=False)
            im.alpha_composite(icone, (x, 100))
            im.alpha_composite(case_inventaire(rendre(q, (128, 128), zoom=8 * gui, lumiere=False), rarete), (x, 440))
            im.alpha_composite(case_inventaire(rendre(q, (48, 48), zoom=3 * gui, lumiere=False), rarete, 54), (x + 110, 440))
            im.alpha_composite(rendre(q, (300, 260), yaw=-35, pitch=22, zoom=24), (x, 560))
            im.alpha_composite(rendre(q, (300, 220), yaw=180, pitch=0, zoom=24), (x, 820))
            dr.text((x, 412), f"keycard_fragment_{couleur}_{i + 1}", font=p_leg, fill=(200, 202, 210, 255))
        im.save(dossier / f"fragments_{couleur}.png")
        print("écrit :", dossier / f"fragments_{couleur}.png")
        # assemblés à côté de la carte
        A = fond_planche(1500, 520)
        da = ImageDraw.Draw(A)
        da.text((30, 20), "4 fragments assemblés · carte d'accès " + {"green": "verte", "blue": "bleue", "red": "rouge"}[couleur],
                font=p_titre, fill=(235, 236, 240, 255))
        tous = []
        cxj, cyj = W / 2, H / 2
        for mdl, tex, (x0, y0, w, h) in d["fragments"]:
            # remis à sa place sur la carte, écarté de 6 points du centre
            cxm, cym = x0 + w / 2, y0 + h / 2
            vx, vy = cxm - cxj, cym - cyj
            nv = math.hypot(vx, vy) or 1
            ex, ey = vx / nv * 7, vy / nv * 7
            dx = (cxm + ex) / PX - 8
            dy = 13 - (cym + ey) / PX - 8
            tous += quads(mdl, texs, (dx, dy, 0))
        carte = quads(cartes_json[couleur], texs)
        A.alpha_composite(rendre(tous, (680, 440), zoom=40, centre=(8, 8, 8), lumiere=False), (20, 70))
        A.alpha_composite(rendre(carte, (680, 440), zoom=40, lumiere=False), (790, 70))
        da.text((300, 490), "fragments 1 à 4", font=p_leg, fill=(170, 172, 180, 255))
        da.text((1080, 490), "carte d'origine", font=p_leg, fill=(170, 172, 180, 255))
        A.save(dossier / f"assembles_{couleur}.png")
        planches_assemblees.append(A)
        print("écrit :", dossier / f"assembles_{couleur}.png")
    tout_ = fond_planche(1500, 520 * 3)
    for i, a in enumerate(planches_assemblees):
        tout_.alpha_composite(a, (0, i * 520))
    tout_.save(dossier / "assembles_tous.png")
    print("écrit :", dossier / "assembles_tous.png")


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--rendus", metavar="DOSSIER")
    ap.add_argument("--installer", action="store_true")
    a = ap.parse_args()
    if not a.rendus and not a.installer:
        ap.error("--rendus DOSSIER ou --installer")
    tout = fabriquer()
    if a.installer:
        installer(tout)
    if a.rendus:
        rendus(tout, a.rendus)


if __name__ == "__main__":
    main()
