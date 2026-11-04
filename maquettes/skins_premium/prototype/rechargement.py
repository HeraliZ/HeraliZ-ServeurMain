"""Rechargement des skins premium (rendus à valider) : images clés et animation.

python rechargement.py <scratch> travaux   -> rendu/recharge.json (à passer à rendre.js)
python rechargement.py <scratch> effets    -> out/rech/<skin>_<n>_fx.png (effets 2D posés sur les rendus) + GIF

Le rendu 3D (rendre.js) dessine l'arme avec le vrai shader d'essai (finition + effet du chargeur selon CamoReload) et le
chargeur déplacé (magOff) ; ici on ajoute par-dessus ce que ferait l'effet 3D du skin en jeu (faille, éclats, cubes,
cadre d'hologramme, éclair) : de la lumière additive autour du chargeur.
"""
import json
import math
import random
import sys
from pathlib import Path

S = Path(sys.argv[1])
MODE = sys.argv[2]
ARME = "xm177"
W, H = 960, 600
VUE = {"vue": "orbite", "yaw": 72, "pitch": 9, "zoom": 1.75, "w": W, "h": H}
N_ANIM = 48           # images de l'animation (2,4 s à 20 images/s)
OUT = S / "out" / "rech"


def lisse(a, b, x):
    t = min(1.0, max(0.0, (x - a) / (b - a)))
    return t * t * (3 - 2 * t)


def lerp(a, b, t):
    return a + (b - a) * t


# --------------------------------------------------------------- chronologie (u : 0 .. 1 sur tout le rechargement)
def etat_faille(u):
    e = {"u": u}
    if u < 0.08:
        e.update(r=-1.0, off=0.0)
    elif u < 0.45:
        k = (u - 0.08) / 0.37
        e.update(r=k, off=-0.22 * lisse(0.0, 1.0, k))
    elif u < 0.55:
        e.update(r=1.0, off=-0.22)
    elif u < 0.80:
        k = (u - 0.55) / 0.25
        e.update(r=1.0 + 0.85 * k, off=lerp(-0.24, -0.05, lisse(0.0, 1.0, k)))
    elif u < 0.88:
        k = (u - 0.80) / 0.08
        e.update(r=1.85 + 0.14 * k, off=lerp(-0.05, 0.0, k))
    else:
        e.update(r=2.0 + (u - 0.88) / 0.12 * 0.6, off=0.0)
    # faille : ouverture 0.10 -> 0.22, reste ouverte, se referme 0.86 -> 0.94 ; onde 0.88 -> 1
    e["faille"] = lisse(0.10, 0.22, u) * (1.0 - lisse(0.86, 0.94, u))
    e["eclair"] = math.exp(-((u - 0.93) / 0.025) ** 2) if u > 0.85 else 0.0
    e["onde"] = lisse(0.88, 1.0, u) if u >= 0.88 else -1.0
    e["eclats_out"] = lisse(0.25, 0.4, u) * (1.0 - lisse(0.5, 0.56, u))     # vers la faille
    e["eclats_in"] = lisse(0.52, 0.6, u) * (1.0 - lisse(0.72, 0.8, u))     # sortent de la faille
    return e


def etat_acces(u):
    e = {"u": u}
    if u < 0.08:
        e.update(r=-1.0, off=0.0)
    elif u < 0.40:
        k = (u - 0.08) / 0.32
        e.update(r=k, off=-0.12 * lisse(0.0, 0.7, k))
    elif u < 0.44:
        e.update(r=1.0, off=-0.12)
    elif u < 0.72:
        k = (u - 0.44) / 0.28
        e.update(r=1.0 + 0.97 * k, off=-0.12)
    elif u < 0.82:
        k = (u - 0.72) / 0.10
        e.update(r=1.99, off=lerp(-0.12, 0.0, lisse(0.0, 1.0, k)))
    else:
        e.update(r=2.0 + (u - 0.82) / 0.18 * 0.95, off=0.0)
    e["cubes"] = lisse(0.2, 0.3, u) * (1.0 - lisse(0.5, 0.6, u))
    e["cubes_t"] = lisse(0.2, 0.6, u)
    e["cadre"] = lisse(0.42, 0.46, u) * (1.0 - lisse(0.70, 0.74, u))
    e["flash"] = math.exp(-((u - 0.85) / 0.03) ** 2) if u > 0.78 else 0.0
    return e


SKINS = {"faille": (210, etat_faille), "acces": (211, etat_acces)}
# images clés de la planche : (u, légende)
CLES = {
    "faille": [(0.04, "1. Chargeur vide"), (0.22, "2. Une faille s'ouvre sous l'arme, le chargeur se fend de lumière"),
               (0.33, "3. Il part en éclats, aspirés dans la faille"),
               (0.67, "4. Le nouveau chargeur sort de la faille, chauffé à blanc"),
               (0.78, "5. Il se refroidit en obsidienne en remontant"),
               (0.93, "6. Clic : la faille se referme, les fractures s'embrasent")],
    "acces": [(0.04, "1. Chargeur vide"), (0.22, "2. Le chargeur se coupe comme un hologramme"),
              (0.36, "3. Il part en cubes rouges"), (0.55, "4. Le nouveau s'imprime : grille rouge, ligne de balayage"),
              (0.77, "5. Solide, il remonte dans l'arme"), (0.86, "6. Clic : onde, accès autorisé (vert)")],
}


def cles_et_anim(skin):
    us = [u for u, _ in CLES[skin]]
    us += [i / (N_ANIM - 1) for i in range(N_ANIM)]
    return us


def nom_image(skin, u):
    return OUT / ("%s_%04d.png" % (skin, round(u * 1000)))


if MODE == "travaux":
    passes = []
    for skin, (camo, etat) in SKINS.items():
        rendus = []
        vus = set()
        for u in cles_et_anim(skin):
            f = nom_image(skin, u)
            if f in vus:
                continue
            vus.add(f)
            e = etat(u)
            temps = 2.0 + u * 2.4
            r = dict(VUE, arme=ARME, camo=camo, temps=temps, recharge=e["r"], magOff=[0, e["off"], 0], sortie=str(f))
            rendus.append(r)
        passes.append({"shader": str(S / ("proto/camo_%d.fsh" % camo)), "inclus": str(S / "proto/inclus.glsl"), "rendus": rendus})
    # boîte du chargeur au repos (position de la faille, du cadre) : un rendu de plus
    passes[0]["rendus"].append(dict(VUE, arme=ARME, camo=210, temps=2.0, sortie=str(OUT / "repos.png")))
    json.dump({"donnees": str(S / "armes"), "armes": [ARME], "passes": passes}, open(S / "rendu/recharge.json", "w"), indent=1)
    print(sum(len(p["rendus"]) for p in passes), "rendus")
    sys.exit(0)

# --------------------------------------------------------------- effets 2D
import numpy as np
from PIL import Image, ImageDraw, ImageFilter

FOND_HAUT, FOND_BAS = (30, 33, 41), (11, 12, 16)


def fond():
    y = np.linspace(0, 1, H)[:, None, None]
    g = np.array(FOND_HAUT, float) * (1 - y) + np.array(FOND_BAS, float) * y
    g = np.repeat(g, W, axis=1)
    # vignettage
    xx, yy = np.meshgrid(np.linspace(-1, 1, W), np.linspace(-1, 1, H))
    g *= (1 - 0.35 * np.clip(xx ** 2 + yy ** 2, 0, 1.5))[..., None]
    return g / 255.0


def couche():
    return Image.new("L", (W, H), 0)


def ajouter(acc, masque, couleur, flou, force):
    """Lumière additive : masque flouté x couleur x force."""
    m = masque.filter(ImageFilter.GaussianBlur(flou)) if flou > 0 else masque
    a = np.asarray(m, float) / 255.0
    acc += a[..., None] * np.array(couleur, float)[None, None, :] / 255.0 * force


def fissure_points(cx, cy, larg, haut, ouverture, graine):
    """Contour d'une faille horizontale (lentille déchiquetée)."""
    rnd = random.Random(graine)
    n = 46
    haut_pts, bas_pts = [], []
    for i in range(n + 1):
        t = i / n
        x = cx - larg / 2 + larg * t
        prof = math.sin(math.pi * t) ** 0.8 * haut * ouverture
        j1 = (rnd.random() - 0.5) * haut * 0.35 * ouverture
        j2 = (rnd.random() - 0.5) * haut * 0.35 * ouverture
        haut_pts.append((x, cy - prof * 0.55 + j1))
        bas_pts.append((x, cy + prof * 0.45 + j2))
    return haut_pts + bas_pts[::-1]


def geometrie_faille(e, repos):
    x0, y0, x1, y1 = repos
    mw = x1 - x0
    return mw, (x0 + x1) / 2, y1 + (y1 - y0) * 0.62, mw * (0.6 + 1.3 * e["faille"])


def arriere_faille(img, e, repos):
    """Intérieur de la faille (derrière l'arme) ; rend le masque « sous la faille » où le chargeur est caché."""
    mw, cx, cy, larg = geometrie_faille(e, repos)
    o = e["faille"]
    cache = np.zeros((H, W, 1))
    if o > 0.01:
        pts = fissure_points(cx, cy, larg, mw * 0.55, o, 3)
        interieur = couche()
        ImageDraw.Draw(interieur).polygon(pts, fill=255)
        a = np.asarray(interieur, float)[..., None] / 255.0
        img[:] = img * (1 - a * 0.92) + a * np.array([0.03, 0.01, 0.06])
    if o > 0.2:
        x_a, x_b = int(max(0, cx - larg * 0.5)), int(min(W, cx + larg * 0.5))
        cache[int(cy):, x_a:x_b] = 1.0
    return cache


def effets_faille(img, e, mag, repos, t):
    acc = np.zeros((H, W, 3))
    x0, y0, x1, y1 = repos
    mw, cx, cy, larg = geometrie_faille(e, repos)
    o = e["faille"]
    rnd = random.Random(7)
    if o > 0.01:
        pts = fissure_points(cx, cy, larg, mw * 0.55, o, 3)
        interieur = couche()
        ImageDraw.Draw(interieur).polygon(pts, fill=255)
        etoiles = couche()
        d = ImageDraw.Draw(etoiles)
        for _ in range(int(140 * o)):
            sx, sy = cx + (rnd.random() - 0.5) * larg, cy + (rnd.random() - 0.5) * mw * 0.6 * o
            d.point((sx, sy), fill=int(150 + 105 * rnd.random()))
        etoiles = Image.composite(etoiles, couche(), interieur)
        ajouter(acc, etoiles, (200, 230, 255), 0, 1.0)
        nebuleuse = couche()
        ImageDraw.Draw(nebuleuse).ellipse((cx - larg * 0.3, cy - mw * 0.12 * o, cx + larg * 0.3, cy + mw * 0.12 * o), fill=255)
        ajouter(acc, Image.composite(nebuleuse, couche(), interieur), (90, 30, 160), 10, 0.8)
        # bords : magenta flou, cyan net, fil blanc
        bord = couche()
        ImageDraw.Draw(bord).line(pts + [pts[0]], fill=255, width=3)
        ajouter(acc, bord, (255, 43, 214), 9, 1.5)
        ajouter(acc, bord, (34, 227, 255), 2, 1.2)
        fil = couche()
        ImageDraw.Draw(fil).line(pts + [pts[0]], fill=255, width=1)
        ajouter(acc, fil, (255, 255, 255), 0, 0.8)
        halo = couche()
        ImageDraw.Draw(halo).ellipse((cx - larg * 0.6, cy - mw * 0.25, cx + larg * 0.6, cy + mw * 0.25), fill=255)
        ajouter(acc, halo, (140, 40, 200), 26, 0.35 * o)
    # éclats : petits triangles lumineux entre le chargeur et la faille
    for sens, force in (("out", e["eclats_out"]), ("in", e["eclats_in"])):
        if force <= 0.01:
            continue
        ec = couche()
        dd = ImageDraw.Draw(ec)
        src = mag if mag else repos
        for i in range(40):
            rr = random.Random(100 + i)
            ph = (rr.random() + t * (1.6 if sens == "out" else -1.6)) % 1.0
            ax = lerp(src[0], src[2], rr.random())
            ay = lerp(src[1], src[3], 0.4 + 0.6 * rr.random())
            if sens == "in":
                ay = min(ay, cy - 6 - rr.random() * mw * 0.8)
            bx = cx + (rr.random() - 0.5) * mw * 1.2
            by = cy
            k = ph if sens == "out" else 1 - ph
            px = lerp(ax, bx, k) + math.sin(k * 6 + i) * mw * 0.15
            py = lerp(ay, by, k * k)
            s = (4 + rr.random() * 7) * (1 - 0.5 * k)
            ang = rr.random() * 6.28 + k * 4
            tri = [(px + s * math.cos(ang + a), py + s * math.sin(ang + a)) for a in (0, 2.3, 4.1)]
            dd.polygon(tri, fill=int(255 * force))
            # traînée vers la faille
            k2 = max(0.0, k - 0.08) if sens == "out" else min(1.0, k + 0.08)
            qx = lerp(ax, bx, k2) + math.sin(k2 * 6 + i) * mw * 0.15
            qy = lerp(ay, by, k2 * k2)
            dd.line((px, py, qx, qy), fill=int(150 * force), width=2)
        ajouter(acc, ec, (255, 90, 230), 4, 1.2)
        ajouter(acc, ec, (150, 240, 255), 0, 1.0)
    if e["eclair"] > 0.01:
        fl = couche()
        ImageDraw.Draw(fl).ellipse((cx - mw * 1.6, cy - 4, cx + mw * 1.6, cy + 4), fill=255)
        ajouter(acc, fl, (255, 255, 255), 3, 2.0 * e["eclair"])
        ajouter(acc, fl, (34, 227, 255), 14, 1.6 * e["eclair"])
    if e["onde"] >= 0:
        k = e["onde"]
        on = couche()
        rx, ry = mw * (0.4 + 2.2 * k), mw * (0.15 + 0.8 * k)
        ImageDraw.Draw(on).ellipse((cx - rx, (y0 + y1) / 2 - ry, cx + rx, (y0 + y1) / 2 + ry), outline=int(255 * (1 - k)), width=3)
        ajouter(acc, on, (180, 120, 255), 4, 1.3)
    return acc


def effets_acces(img, e, mag, repos, t):
    acc = np.zeros((H, W, 3))
    x0, y0, x1, y1 = repos
    mw, mh = x1 - x0, y1 - y0
    rouge = (255, 21, 48)
    if e["cubes"] > 0.01:
        cb = couche()
        dd = ImageDraw.Draw(cb)
        src = mag if mag else repos
        for i in range(70):
            rr = random.Random(300 + i)
            k = min(1.0, e["cubes_t"] * 1.3 * (0.6 + 0.4 * rr.random()))
            ax = lerp(src[0], src[2], rr.random())
            ay = lerp(src[1], src[3], rr.random())
            vx, vy = (rr.random() - 0.5) * mw * 2.2, (rr.random() * 0.8 + 0.2) * mh * 0.9
            px, py = ax + vx * k, ay + vy * k + 40 * k * k
            s = 2 + rr.random() * 4
            alpha = e["cubes"] * (1 - k * 0.8)
            dd.rectangle((px - s, py - s, px + s, py + s), outline=int(255 * alpha), width=1)
            if rr.random() < 0.4:
                dd.rectangle((px - s * 0.5, py - s * 0.5, px + s * 0.5, py + s * 0.5), fill=int(255 * alpha))
        ajouter(acc, cb, rouge, 3, 1.4)
        ajouter(acc, cb, (255, 160, 170), 0, 0.8)
    if e["cadre"] > 0.01 and mag:
        a, b, c, d = mag
        pad = 6
        cad = couche()
        dd = ImageDraw.Draw(cad)
        L = min(c - a, d - b) * 0.3
        v = int(255 * e["cadre"])
        for (px, py, sx, sy) in ((a - pad, b - pad, 1, 1), (c + pad, b - pad, -1, 1), (a - pad, d + pad, 1, -1), (c + pad, d + pad, -1, -1)):
            dd.line((px, py, px + sx * L, py), fill=v, width=2)
            dd.line((px, py, px, py + sy * L), fill=v, width=2)
        # contour pointillé
        for x in range(int(a - pad), int(c + pad), 8):
            dd.line((x, b - pad, x + 4, b - pad), fill=v // 2, width=1)
            dd.line((x, d + pad, x + 4, d + pad), fill=v // 2, width=1)
        ajouter(acc, cad, rouge, 2, 1.6)
        ajouter(acc, cad, (255, 200, 200), 0, 0.6)
        # ligne de balayage de l'impression (même hauteur que dans le shader)
        k = lisse(0.1, 0.95, e["r"] - 1.0)
        ligne = lerp(-0.15, 1.15, k)
        sy_ = d - (d - b) * ligne
        if b - 4 < sy_ < d + 4:
            sc = couche()
            ImageDraw.Draw(sc).line((a - pad * 2, sy_, c + pad * 2, sy_), fill=255, width=2)
            ajouter(acc, sc, rouge, 6, 1.6)
            ajouter(acc, sc, (255, 220, 220), 0, 1.0)
    if e["flash"] > 0.01:
        fl = couche()
        cx, cy = (x0 + x1) / 2, y0 + mh * 0.05
        ImageDraw.Draw(fl).ellipse((cx - mw * 0.7, cy - mw * 0.25, cx + mw * 0.7, cy + mw * 0.25), fill=255)
        ajouter(acc, fl, (43, 255, 122), 12, 0.7 * e["flash"])
        ri = couche()
        rr_ = mw * (0.5 + 0.9 * (1 - e["flash"]))
        ImageDraw.Draw(ri).ellipse((cx - rr_, cy - rr_ * 0.45, cx + rr_, cy + rr_ * 0.45), outline=255, width=2)
        ajouter(acc, ri, (43, 255, 122), 2, 1.2 * e["flash"])
    return acc


def composer(skin, u, repos):
    camo, etat = SKINS[skin]
    e = etat(u)
    f = nom_image(skin, u)
    rendu = np.asarray(Image.open(f).convert("RGBA"), float) / 255.0
    mag = json.load(open(str(f) + ".json"))
    img = fond()
    a = rendu[..., 3:4]
    if skin == "faille":
        cache = arriere_faille(img, e, repos)
        # seul le chargeur (sous la faille) est caché : l'arme elle-même n'y descend jamais
        a = a * (1 - cache)
    img = img * (1 - a) + rendu[..., :3] * a
    fx = (effets_faille if skin == "faille" else effets_acces)(img, e, mag, repos, 2.0 + u * 2.4)
    img = np.clip(img + fx, 0, 1)
    return Image.fromarray((img * 255).astype(np.uint8))


repos = json.load(open(str(OUT / "repos.png") + ".json"))
for skin in SKINS:
    for u, _ in CLES[skin]:
        composer(skin, u, repos).save(OUT / ("cle_%s_%04d.png" % (skin, round(u * 1000))))
    images = [composer(skin, i / (N_ANIM - 1), repos) for i in range(N_ANIM)]
    # un temps d'arrêt au début et à la fin
    images = [images[0]] * 6 + images + [images[-1]] * 10
    petites = [im.resize((W * 2 // 3, H * 2 // 3), Image.LANCZOS).convert("P", palette=Image.ADAPTIVE, colors=255) for im in images]
    petites[0].save(OUT / ("anim_%s.gif" % skin), save_all=True, append_images=petites[1:], duration=50, loop=0, disposal=2)
    print(skin, "ok")
