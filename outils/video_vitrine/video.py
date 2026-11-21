#!/usr/bin/env python3
"""Vitrine des skins HeraliZ : vidéo de 30 s (92 BPM, une arme par mesure), demandée par le propriétaire le 21/11.

Les armes sont dessinées hors du jeu par l'outil d'aperçu des camos (modules/armes/outils/apercu_camos : mêmes modèles,
mêmes textures et même code des skins que le jeu ; Chromium sans écran) avec une lumière de coucher de soleil, sur un décor
de ville en ruines vue d'un toit ; titres aux polices du serveur, logo du launcher, musique synthétisée (musique.py, aucun
droit d'auteur). Ce n'est pas une capture du jeu : ni mains, ni inspection, ni accessoires.

    python3 outils/video_vitrine/video.py tout           tout refaire (environ 45 min : rendu 9 min, images 25 min)
    python3 outils/video_vitrine/video.py preparer       armes préparées et page de rendu
    python3 outils/video_vitrine/video.py rendre         rendu des armes (3200 x 1800 par image, réduit ensuite)
    python3 outils/video_vitrine/video.py composer [16_9] [9_16]
    python3 outils/video_vitrine/video.py finir          musique et vidéos finales (TikTok, Discord, < 10 Mo, sans musique)
    python3 outils/video_vitrine/video.py image <k>      une image de contrôle (16:9 et 9:16)

Tout est écrit dans build/video_vitrine/ (hors de Git). Besoin : numpy, Pillow, Node.js et Playwright (son Chromium),
ffmpeg avec libx264. Changer d'armes ou de skins : tableau SKINS (identifiant TaCZ de l'arme, nom affiché, zoom ;
programme du camo = shaderId de Camo.java).
"""
import json
import math
import os
import subprocess
import sys
from multiprocessing import Pool
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

DEPOT = Path(__file__).resolve().parents[2]
ICI = Path(__file__).resolve().parent
TRAVAIL = DEPOT / "build" / "video_vitrine"
APERCU = DEPOT / "modules" / "armes" / "outils" / "apercu_camos"
SHADERS = DEPOT / "modules" / "armes" / "src" / "client" / "resources" / "assets" / "heralisz_camo" / "shaders" / "core"
INCLUS = DEPOT / "modules" / "armes" / "src" / "client" / "resources" / "assets" / "minecraft" / "shaders" / "include"
POLICES = DEPOT / "modules" / "noyau" / "src" / "client" / "resources" / "assets" / "heraliz_noyau" / "font"
LOGO = DEPOT / "launcher" / "app" / "assets" / "images" / "heraliz-logo.png"

FPS = 30
BPM = 92.0
MESURE = 4 * 60.0 / BPM
DUREE = 11.5 * MESURE
NB = int(round(DUREE * FPS))
RW, RH = 3200, 1800   # rendu des armes (suréchantillonné puis réduit : bords lisses)
W, H = 1920, 1080

EPIQUE = (190, 110, 255)
LEGENDAIRE = (255, 184, 48)
SKINS = [
    # titre, rareté, couleur, programme du camo (shaderId de Camo.java), armes (identifiant TaCZ, nom affiché, zoom)
    ("DIAMANT", "SKIN ÉPIQUE", EPIQUE, 5, [("tacz:ak47", "AKM", 0.92), ("tacz:aiawp", "AWP", 0.9),
                                          ("heraliz_fuse:mp5_classic", "MP5", 0.86)]),
    ("DARK MATTER", "SKIN LÉGENDAIRE", LEGENDAIRE, 6, [("tacz:vector45", "VECTOR", 0.9), ("tacz:p90", "P90", 0.84)]),
    ("ORION", "SKIN LÉGENDAIRE", LEGENDAIRE, 32, [("tacz:scar_h", "SCAR-H", 0.92), ("tacz:aug", "AUG", 0.9)]),
    ("CHROME LIQUIDE", "SKIN LÉGENDAIRE", LEGENDAIRE, 44, [("tacz:deagle", "DEAGLE .50", 0.8),
                                                        ("maxstuff:honey_badger", "HONEY BADGER", 0.9)]),
]
ARMES = []  # (index, skin, arme préparée, nom, zoom, programme, identifiant TaCZ)
for s_i, (titre, rarete, coul, prog, armes) in enumerate(SKINS):
    for (gid, nom, z) in armes:
        ARMES.append((len(ARMES), s_i, gid.replace(":", "_"), nom, z, prog, gid))
DEBUT_SKIN = [min(i for (i, s, *_r) in ARMES if s == s_i) for s_i in range(len(SKINS))]
MARGE = 0.16  # secondes rendues avant et après la mesure d'une arme (fondus)


def debut_arme(i):
    return (1 + i) * MESURE


def lisse(x):
    x = min(1.0, max(0.0, x))
    return x * x * (3 - 2 * x)


def camera(i, t):
    """Caméra autour de l'arme i au temps t : rotation façon inspection, petit coup de zoom sur le temps fort."""
    zoom = ARMES[i][4]
    u = (t - debut_arme(i)) / MESURE
    e = 0.5 - 0.5 * math.cos(math.pi * min(1.0, max(0.0, u)))
    e += (u - 1.0) * 0.6 if u > 1 else (u * 0.6 if u < 0 else 0.0)   # prolongé dans les fondus
    sens = 1 if i % 2 == 0 else -1
    coup = 1 + 0.045 * math.exp(-max(0.0, u) * 14) if u >= 0 else 1.0
    return {"yaw": 90 + sens * (-30 + 60 * e), "pitch": 13 - 7 * e, "roule": sens * (-6 + 11 * e),
            "zoom": zoom * (0.95 + 0.09 * e) * coup, "dy": 0.012 * math.sin(2 * math.pi * u), "dx": 0.0}


def images_arme(i):
    a = debut_arme(i) - MARGE
    b = debut_arme(i) + MESURE + MARGE
    return [k for k in range(NB) if a <= k / FPS < b]


# ------------------------------------------------------------------------------------------------------- préparation
def modele_et_texture(gid):
    """Modèle (geo) et texture d'une arme TaCZ, d'après son index et son affichage dans les gunpacks du dépôt : comme le
    jeu (et outils/lod_armes.py), le dernier pack dans l'ordre des dossiers gagne."""
    sys.path.insert(0, str(DEPOT / "outils"))
    from silencieux import lire  # lecture tolérante (commentaires, virgules finales)
    tacz = DEPOT / "serveur" / "tacz"

    def dernier(motif):
        trouves = sorted(tacz.glob(motif))
        return trouves[-1] if trouves else None

    ns, nom = gid.split(":")
    idx = dernier("*/data/%s/index/guns/%s.json" % (ns, nom))
    if idx is None:
        raise SystemExit("arme introuvable dans les gunpacks : " + gid)
    dns, dnom = lire(idx)["display"].split(":")
    df = dernier("*/assets/%s/display/guns/%s.json" % (dns, dnom))
    dd = lire(df)
    mns, mp = dd["model"].split(":")
    tns, tp = dd["texture"].split(":")
    m = dernier("*/assets/%s/geo_models/%s.json" % (mns, mp))
    t = dernier("*/assets/%s/textures/%s.png" % (tns, tp))
    if m is None or t is None:
        raise SystemExit("modèle ou texture introuvable pour " + gid)
    return m, t


def preparer():
    """Armes préparées pour le rendu (prep.py de l'aperçu des camos) et page de rendu de la vitrine (copie de la page de
    l'aperçu, avec la lumière de coucher de soleil, le roulis et le champ de vision réglables)."""
    (TRAVAIL / "armes").mkdir(parents=True, exist_ok=True)
    for (_i, _s, prep, _n, _z, _p, gid) in ARMES:
        if (TRAVAIL / "armes" / (prep + ".json")).exists():
            continue
        m, t = modele_et_texture(gid)
        subprocess.run([sys.executable, str(APERCU / "prep.py"), str(m), str(t), str(TRAVAIL / "armes"), prep,
                        "--sans-adaptateurs"], check=True)
    page = (APERCU / "apercu.html").read_text(encoding="utf-8")
    remplacements = [
        ("""  vec3 nw = normalize(IViewRotMat * viewNormal);
  vec3 l0 = normalize(vec3(0.2, 1.0, -0.7));
  vec3 l1 = normalize(vec3(-0.2, 1.0, 0.7));
  float li = min(1.0, (max(0.0, dot(l0, nw)) + max(0.0, dot(l1, nw))) * 0.6 + 0.4);
  vertexColor = vec4(vec3(li), 1.0);""",
         """  // vitrine : lumière de coucher de soleil (soleil chaud derrière à gauche, ciel violet, face douce), dans la vue
  vec3 n = normalize(viewNormal);
  vec3 soleil = normalize(vec3(-0.62, 0.30, -0.72));
  vec3 face = normalize(vec3(0.38, 0.50, 0.78));
  vec3 ciel = normalize(vec3(0.05, 1.0, 0.15));
  vec3 li = vec3(0.20, 0.17, 0.24)
          + vec3(1.15, 0.62, 0.30) * pow(max(0.0, dot(soleil, n)), 0.8) * 1.10
          + vec3(1.00, 0.80, 0.66) * max(0.0, dot(face, n)) * 0.78
          + vec3(0.46, 0.40, 0.75) * max(0.0, dot(ciel, n)) * 0.45;
  vertexColor = vec4(min(li, vec3(1.35)), 1.0);"""),
        ("""    const R = mul(RX(o.pitch || 0), RY(o.yaw || 0));
    V = mul(T(0, 0, -dist), mul(R, T(-c[0], -c[1], -c[2])));
    P = proj(44, w / h, s * 0.05, s * 10);""",
         """    const R = mul(RZ(o.roule || 0), mul(RX(o.pitch || 0), RY(o.yaw || 0)));
    V = mul(T(s * (o.dx || 0), s * (o.dy || 0), -dist), mul(R, T(-c[0], -c[1], -c[2])));
    P = proj(o.fov || 44, w / h, s * 0.05, s * 10);"""),
    ]
    for vieux, neuf in remplacements:
        if page.count(vieux) != 1:
            raise SystemExit("apercu.html a changé : adapter les remplacements de video.py (" + vieux.strip()[:60] + ")")
        page = page.replace(vieux, neuf)
    (TRAVAIL / "vitrine.html").write_text(page, encoding="utf-8")
    js = (APERCU / "rendre.js").read_text(encoding="utf-8")
    js = js.replace("'apercu.html'", "'vitrine.html'").replace("/apercu.html`", "/vitrine.html`")
    js = js.replace("path.join(__dirname, '../../src/client/resources/assets/minecraft/shaders/include/heralisz_camo.glsl')",
                    json.dumps(str(INCLUS / "heralisz_camo.glsl")))
    if "vitrine.html" not in js or "heralisz_camo.glsl" not in js:
        raise SystemExit("rendre.js a changé : adapter preparer() de video.py")
    (TRAVAIL / "rendre_vitrine.js").write_text(js, encoding="utf-8")
    print("préparé : %d armes, page de rendu" % len(ARMES))


# ------------------------------------------------------------------------------------------------------------- rendu
def rendre():
    dossier = TRAVAIL / "rendus"
    seules = {int(x) for x in os.environ.get("IMAGES", "").split(",") if x}
    moitie = (len(ARMES) + 1) // 2
    groupes = [list(range(moitie)), list(range(moitie, len(ARMES)))]
    procs = []
    for g_i, groupe in enumerate(groupes):
        passes = {}
        for i in groupe:
            (_i, _s, prep, _n, _z, prog, _g) = ARMES[i]
            p = passes.setdefault(prog, {"shader": str(SHADERS / ("camo_%d.fsh" % prog)), "rendus": []})
            for k in images_arme(i):
                if seules and k not in seules:
                    continue
                sortie = dossier / ("a%d" % i) / ("%04d.png" % k)
                if sortie.exists():
                    continue
                o = {"arme": prep, "camo": prog, "vue": "orbite", "w": RW, "h": RH, "temps": k / FPS, "sortie": str(sortie),
                     "fov": 36}
                o.update(camera(i, k / FPS))
                p["rendus"].append(o)
        travail = {"donnees": str(TRAVAIL / "armes"), "armes": sorted({ARMES[i][2] for i in groupe}),
                   "passes": list(passes.values())}
        f = TRAVAIL / ("travail_%d.json" % g_i)
        f.write_text(json.dumps(travail), encoding="utf-8")
        print("groupe %d : %d images à rendre" % (g_i, sum(len(p["rendus"]) for p in passes.values())), flush=True)
        procs.append(subprocess.Popen(["node", str(TRAVAIL / "rendre_vitrine.js"), str(f)], cwd=str(TRAVAIL)))
    for p in procs:
        p.wait()


# ------------------------------------------------------------------------------------------------------------- décor
def degrade(h, arrets):
    ys = np.linspace(0, 1, h)
    out = np.zeros((h, 3), np.float32)
    for c in range(3):
        out[:, c] = np.interp(ys, [a for a, _ in arrets], [col[c] / 255 for _, col in arrets])
    return out


def ciel(w, h, soleil):
    rng = np.random.default_rng(3)
    g = degrade(h, [(0.0, (16, 9, 34)), (0.25, (52, 22, 66)), (0.46, (128, 44, 78)), (0.60, (214, 92, 64)),
                    (0.70, (255, 150, 84)), (0.76, (255, 196, 120)), (1.0, (70, 30, 40))])
    img = np.repeat(g[:, None, :], w, axis=1)
    yy, xx = np.mgrid[0:h, 0:w].astype(np.float32)
    sx, sy = soleil
    d = np.sqrt((xx - sx) ** 2 + ((yy - sy) * 1.15) ** 2)
    img += np.exp(-(d / (0.42 * w)) ** 2)[..., None] * np.array([0.55, 0.26, 0.10], np.float32)
    img += np.exp(-(d / (0.12 * w)) ** 2)[..., None] * np.array([0.55, 0.38, 0.18], np.float32)
    disque = np.clip((0.034 * w - d) / 2.5, 0, 1)
    img = img * (1 - disque[..., None]) + disque[..., None] * np.array([1.0, 0.92, 0.75], np.float32)
    # nuages : bandes étirées, éclairées par en dessous (plus chaudes près du soleil)
    nu = Image.new("L", (w, h), 0)
    dn = ImageDraw.Draw(nu)
    for _ in range(26):
        cx = rng.uniform(-0.1, 1.1) * w
        cy = rng.uniform(0.18, 0.58) * h
        lw = rng.uniform(0.18, 0.5) * w
        lh = rng.uniform(0.008, 0.026) * h
        dn.ellipse([cx - lw / 2, cy - lh / 2, cx + lw / 2, cy + lh / 2], fill=int(rng.uniform(90, 200)))
    nu = np.asarray(nu.filter(ImageFilter.GaussianBlur(w * 0.006)), np.float32) / 255
    chaud = np.exp(-(d / (0.5 * w)) ** 2)[..., None]
    teinte = chaud * np.array([1.0, 0.55, 0.32], np.float32) + (1 - chaud) * np.array([0.42, 0.20, 0.34], np.float32)
    img = img * (1 - 0.55 * nu[..., None]) + 0.55 * nu[..., None] * teinte
    return np.clip(img, 0, 1)


def ville(w, h, sol, hmin, hmax, couleur, graine, fenetres=0.0, tours=0):
    """Silhouette de ville en ruines (RGBA), sol à la hauteur « sol » (pixels) ; miradors comme ceux du logo."""
    rng = np.random.default_rng(graine)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    x = rng.uniform(-60, 0)
    while x < w:
        bw = rng.uniform(0.022, 0.075) * w
        bh = rng.uniform(hmin, hmax)
        haut = sol - bh
        if rng.random() < 0.45:   # toit cassé
            pts = [(x, h), (x, haut + rng.uniform(0, 0.25) * bh)]
            n = int(rng.integers(3, 7))
            for k in range(1, n):
                pts.append((x + bw * k / n, haut + rng.uniform(0, 0.35) * bh))
            pts += [(x + bw, haut + rng.uniform(0, 0.2) * bh), (x + bw, h)]
            d.polygon(pts, fill=couleur)
        else:
            d.rectangle([x, haut, x + bw, h], fill=couleur)
            if rng.random() < 0.35:   # antenne
                ax = x + bw * rng.uniform(0.2, 0.8)
                d.line([ax, haut, ax, haut - rng.uniform(0.15, 0.5) * bh], fill=couleur, width=max(2, w // 900))
        if fenetres and rng.random() < 0.5:
            for _ in range(int(rng.integers(1, 4))):
                if rng.random() < fenetres:
                    fx = x + rng.uniform(0.15, 0.8) * bw
                    fy = haut + rng.uniform(0.2, 0.8) * bh
                    s = max(2, w // 700)
                    d.rectangle([fx, fy, fx + s * 1.6, fy + s], fill=(255, 170, 90, 200))
        x += bw + rng.uniform(-0.004, 0.02) * w
    for _ in range(tours):
        tx = rng.uniform(0.08, 0.92) * w
        th = rng.uniform(0.9, 1.25) * hmax
        top = sol - th
        lw = max(2, w // 700)
        d.line([tx - 0.012 * w, sol, tx - 0.005 * w, top + 0.18 * th], fill=couleur, width=lw)
        d.line([tx + 0.012 * w, sol, tx + 0.005 * w, top + 0.18 * th], fill=couleur, width=lw)
        for k in range(4):
            yy = sol - (k + 1) * (th * 0.82) / 5
            d.line([tx - 0.011 * w, yy, tx + 0.011 * w, yy - 0.02 * th], fill=couleur, width=lw)
        d.rectangle([tx - 0.014 * w, top + 0.08 * th, tx + 0.014 * w, top + 0.2 * th], fill=couleur)
        d.polygon([(tx - 0.018 * w, top + 0.08 * th), (tx, top), (tx + 0.018 * w, top + 0.08 * th)], fill=couleur)
    return im


def toit(w, h, haut, graine):
    """Rebord de toit en béton au premier plan, arête éclairée par le soleil."""
    rng = np.random.default_rng(graine)
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    pts = [(0, h)]
    x = 0.0
    while x <= w:
        pts.append((x, haut + rng.uniform(-3, 3)))
        x += rng.uniform(30, 90)
    pts.append((w, haut))
    pts.append((w, h))
    d.polygon(pts, fill=(18, 11, 20, 255))
    for k in range(len(pts) - 3):
        (x0, y0), (x1, y1) = pts[k + 1], pts[k + 2]
        d.line([x0, y0, x1, y1], fill=(255, 150, 80, 170), width=3)
    for _ in range(5):   # fers à béton
        fx = rng.uniform(0.05, 0.95) * w
        d.line([fx, haut, fx + rng.uniform(-14, 14), haut - rng.uniform(18, 46)], fill=(22, 14, 24, 255), width=4)
    bx = 0.07 * w   # caisson de ventilation
    d.rectangle([bx, haut - 0.07 * h, bx + 0.09 * w, haut], fill=(22, 14, 26, 255))
    d.line([bx, haut - 0.07 * h, bx + 0.09 * w, haut - 0.07 * h], fill=(255, 150, 80, 140), width=2)
    return im


class Decor:
    """Décor d'un format (couches avec parallaxe, poussières, vignette)."""

    def __init__(self, w, h, vertical):
        self.w, self.h = w, h
        soleil = (0.66 * w, 0.62 * h) if vertical else (0.72 * w, 0.66 * h)
        self.ciel = ciel(w, h, soleil)
        sol = 0.80 * h if not vertical else 0.74 * h
        large = w + 600
        self.loin = np.asarray(ville(large, h, sol, 0.05 * h, 0.15 * h, (96, 40, 70, 210), 11, 0.0, 2), np.float32) / 255
        self.milieu = np.asarray(ville(large, h, sol + 0.03 * h, 0.06 * h, 0.2 * h, (44, 20, 40, 255), 12, 0.35, 1),
                                 np.float32) / 255
        self.toit = np.asarray(toit(large, h, 0.885 * h if not vertical else 0.86 * h, 13), np.float32) / 255
        yy = np.linspace(0, 1, h)[:, None, None]
        self.brume = np.exp(-((yy - sol / h) / 0.05) ** 2) * np.array([1.0, 0.55, 0.32], np.float32) * 0.22
        rng = np.random.default_rng(5)
        self.poussiere = [(rng.uniform(0, w), rng.uniform(0, h), rng.uniform(0.8, 2.6), rng.uniform(-14, -4),
                           rng.uniform(-6, 3), rng.uniform(0.2, 0.75)) for _ in range(70)]
        yv, xv = np.mgrid[0:h, 0:w].astype(np.float32)
        dv = np.sqrt(((xv - w / 2) / (w / 2)) ** 2 + ((yv - h / 2) / (h / 2)) ** 2)
        self.vignette = (1 - 0.38 * np.clip(dv - 0.35, 0, 1.2) ** 1.6)[..., None]

    def image(self, t):
        img = self.ciel.copy()
        for couche, vitesse in ((self.loin, 5.0), (self.milieu, 11.0), (self.toit, 18.0)):
            x0 = int(300 - (t * vitesse) % 300)
            c = couche[:, x0:x0 + self.w]
            a = c[..., 3:4]
            img = img * (1 - a) + c[..., :3] * a
            if couche is self.loin:
                img += self.brume
        return np.clip(img, 0, 1)


# ------------------------------------------------------------------------------------------------------------- textes
def police(nom, taille):
    return ImageFont.truetype(str(POLICES / nom), taille)


def texte_espace(d, xy, texte, f, fill, espace, ancre="l"):
    """Texte avec un espacement des lettres ; ancre l (gauche), c (centre), r (droite). Rend la largeur."""
    largeurs = [d.textlength(ch, font=f) for ch in texte]
    total = sum(largeurs) + espace * (len(texte) - 1)
    x, y = xy
    if ancre == "c":
        x -= total / 2
    elif ancre == "r":
        x -= total
    for ch, lw in zip(texte, largeurs):
        d.text((x, y), ch, font=f, fill=fill)
        x += lw + espace
    return total


LOGO_IMG = None


def logo(largeur):
    global LOGO_IMG
    if LOGO_IMG is None:
        LOGO_IMG = Image.open(LOGO).convert("RGBA")
    h = int(LOGO_IMG.height * largeur / LOGO_IMG.width)
    return LOGO_IMG.resize((largeur, h), Image.LANCZOS)


def calque_texte(w, h, vertical, t):
    """Titres (skin, rareté, arme), logo, intro et fin, selon le temps t. En 9:16, rien d'important dans le haut (onglets
    de TikTok) ni le bas (légende, boutons)."""
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    m = t / MESURE
    if m < 1.0:   # intro
        a = lisse(m / 0.45) * (1 - lisse((m - 0.82) / 0.18))
        lw = int((0.62 if not vertical else 0.86) * w * (0.94 + 0.06 * lisse(m)))
        lg = logo(lw)
        lg.putalpha(lg.getchannel("A").point(lambda v: int(v * a)))
        cy = 0.40 * h
        im.alpha_composite(lg, (int(w / 2 - lg.width / 2), int(cy - lg.height / 2)))
        a2 = lisse((m - 0.35) / 0.35) * (1 - lisse((m - 0.82) / 0.18))
        f = police("oswald_bold.ttf", int(0.055 * (w if not vertical else 1.6 * w)))
        texte_espace(d, (w / 2, cy + lg.height / 2 + 0.03 * h), "LES SKINS", f, (255, 232, 210, int(255 * a2)),
                     int(0.012 * w), "c")
        return im
    if m >= 10.0:   # fin
        a = lisse((m - 10.0) / 0.3)
        lw = int((0.56 if not vertical else 0.84) * w)
        lg = logo(lw)
        lg.putalpha(lg.getchannel("A").point(lambda v: int(v * a)))
        cy = 0.38 * h
        im.alpha_composite(lg, (int(w / 2 - lg.width / 2), int(cy - lg.height / 2)))
        f1 = police("dmsans_bold.ttf", int(0.042 * (w if not vertical else 1.55 * w)))
        f2 = police("dmsans_medium.ttf", int(0.03 * (w if not vertical else 1.55 * w)))
        a1 = lisse((m - 10.25) / 0.3)
        a2 = lisse((m - 10.5) / 0.3)
        y1 = (0.66 * h) if vertical else (cy + lg.height / 2 + 0.06 * h)
        lp = max(d.textlength("play.heraliz.fr", font=f1), d.textlength("discord.gg/heraliz", font=f2)) + 0.08 * w
        hp = f1.size * 1.5 + f2.size * 1.6 + 0.03 * h
        d.rounded_rectangle([w / 2 - lp / 2, y1 - 0.018 * h, w / 2 + lp / 2, y1 - 0.018 * h + hp], radius=int(0.012 * w),
                            fill=(14, 8, 20, int(170 * a1)), outline=(255, 170, 100, int(90 * a1)), width=2)
        texte_espace(d, (w / 2, y1), "play.heraliz.fr", f1, (255, 255, 255, int(255 * a1)), 2, "c")
        texte_espace(d, (w / 2, y1 + f1.size * 1.5), "discord.gg/heraliz", f2, (255, 214, 182, int(255 * a2)), 1, "c")
        return im
    i = min(len(ARMES) - 1, int(m) - 1)   # armes
    (_i, s_i, _a, nom, _z, _p, _g) = ARMES[i]
    titre, rarete, coul, _prog, _armes = SKINS[s_i]
    debut_s = debut_arme(DEBUT_SKIN[s_i])
    fin_s = debut_arme(max(j for (j, s, *_r) in ARMES if s == s_i)) + MESURE
    a_skin = lisse((t - debut_s) / 0.35) * (1 - lisse((t - (fin_s - 0.2)) / 0.2))
    glisse = (1 - lisse((t - debut_s) / 0.45)) * 0.05 * w
    a_arme = lisse((t - debut_arme(i) - 0.05) / 0.25) * (1 - lisse((t - (debut_arme(i) + MESURE - 0.18)) / 0.18))
    lg = logo(int((0.15 if not vertical else 0.42) * w))
    lg.putalpha(lg.getchannel("A").point(lambda v: int(v * 0.92)))
    ar = int(255 * a_skin)
    aa = int(255 * a_arme)
    if not vertical:
        im.alpha_composite(lg, (int(0.025 * w), int(0.03 * h)))
        f_r = police("dmsans_bold.ttf", int(0.0165 * w))
        f_t = police("oswald_bold.ttf", int(0.066 * w))
        f_a = police("oswald_bold.ttf", int(0.03 * w))
        x0 = 0.045 * w - glisse
        y_r = 0.735 * h
        texte_espace(d, (x0, y_r), rarete, f_r, coul + (ar,), int(0.004 * w))
        lt = texte_espace(d, (x0, y_r + 0.032 * h), titre, f_t, (255, 246, 236, ar), int(0.004 * w))
        d.rectangle([x0, y_r + 0.032 * h + f_t.size * 1.18, x0 + min(lt, 0.42 * w) * lisse((t - debut_s) / 0.6),
                     y_r + 0.032 * h + f_t.size * 1.18 + max(3, int(0.003 * h))], fill=coul + (ar,))
        texte_espace(d, (0.955 * w, 0.865 * h), nom, f_a, (255, 236, 220, aa), int(0.003 * w), "r")
        d.rectangle([0.955 * w - 0.06 * w, 0.865 * h - 0.012 * h, 0.955 * w, 0.865 * h - 0.012 * h + 3],
                    fill=coul + (aa,))
    else:
        im.alpha_composite(lg, (int(w / 2 - lg.width / 2), int(0.085 * h)))
        f_r = police("dmsans_bold.ttf", int(0.034 * w))
        taille = int(0.115 * w)
        f_t = police("oswald_bold.ttf", taille)
        while taille > 40 and d.textlength(titre, font=f_t) + 0.006 * w * (len(titre) - 1) > 0.78 * w:
            taille -= 4
            f_t = police("oswald_bold.ttf", taille)
        f_a = police("oswald_bold.ttf", int(0.06 * w))
        y_r = 0.625 * h
        texte_espace(d, (w / 2 - glisse, y_r), rarete, f_r, coul + (ar,), int(0.008 * w), "c")
        lt = texte_espace(d, (w / 2 - glisse, y_r + 0.028 * h), titre, f_t, (255, 246, 236, ar), int(0.006 * w), "c")
        lb = min(lt, 0.8 * w) * lisse((t - debut_s) / 0.6)
        yb = y_r + 0.028 * h + f_t.size * 1.2
        d.rectangle([w / 2 - lb / 2, yb, w / 2 + lb / 2, yb + max(4, int(0.003 * h))], fill=coul + (ar,))
        texte_espace(d, (w / 2, 0.745 * h), nom, f_a, (255, 236, 220, aa), int(0.005 * w), "c")
    return im


# ------------------------------------------------------------------------------------------------------------- image
def arme_rgba(i, k):
    f = TRAVAIL / "rendus" / ("a%d" % i) / ("%04d.png" % k)
    if not f.exists():
        return None
    im = Image.open(f).convert("RGBA").resize((W, H), Image.LANCZOS)
    return np.asarray(im, np.float32) / 255


def flou(a, r, reduction=4):
    """Flou gaussien d'un tableau HxWxC (réduit puis agrandi : rapide)."""
    h, w = a.shape[:2]
    petit = (max(1, w // reduction), max(1, h // reduction))
    canaux = []
    for c in range(a.shape[2]):
        im = Image.fromarray((np.clip(a[..., c], 0, 1) * 255).astype(np.uint8)).resize(petit, Image.BILINEAR)
        im = im.filter(ImageFilter.GaussianBlur(r / reduction)).resize((w, h), Image.BILINEAR)
        canaux.append(np.asarray(im, np.float32) / 255)
    return np.stack(canaux, axis=-1)


DECORS = {}
BOITES = None


def boites():
    """Boîte (x0, y0, x1, y1, en pixels du rendu réduit 1920 x 1080) de chaque arme sur toutes ses images : chaque arme
    est cadrée d'après sa vraie place à l'écran ; gardée dans boites.json."""
    global BOITES
    if BOITES is not None:
        return BOITES
    f = TRAVAIL / "boites.json"
    if f.exists():
        BOITES = {int(k): v for k, v in json.loads(f.read_text(encoding="utf-8")).items()}
        return BOITES
    BOITES = {}
    for (i, *_r) in ARMES:
        x0 = y0 = 10 ** 9
        x1 = y1 = -1
        for k in images_arme(i):
            p = TRAVAIL / "rendus" / ("a%d" % i) / ("%04d.png" % k)
            if not p.exists():
                continue
            b = Image.open(p).getchannel("A").reduce(5).point(lambda v: 255 if v > 12 else 0).getbbox()
            if b:
                x0, y0, x1, y1 = min(x0, b[0]), min(y0, b[1]), max(x1, b[2]), max(y1, b[3])
        r = W / (RW / 5)
        BOITES[i] = [x0 * r, y0 * r, x1 * r, y1 * r]
    f.write_text(json.dumps(BOITES), encoding="utf-8")
    return BOITES


def image(k, vertical):
    t = k / FPS
    w, h = (1080, 1920) if vertical else (W, H)
    if vertical not in DECORS:
        DECORS[vertical] = Decor(w, h, vertical)
    dec = DECORS[vertical]
    img = dec.image(t)
    couches = []   # armes visibles (fondu entre deux armes sur les marges)
    for (i, *_r) in ARMES:
        a = debut_arme(i)
        if a - MARGE <= t < a + MESURE + MARGE:
            poids = lisse((t - (a - MARGE)) / (2 * MARGE)) * (1 - lisse((t - (a + MESURE - MARGE)) / (2 * MARGE)))
            if poids > 0.001:
                rgba = arme_rgba(i, k)
                if rgba is not None:
                    couches.append((i, rgba, poids))
    for i_arme, rgba, poids in couches:
        im = Image.fromarray((rgba * 255).astype(np.uint8), "RGBA")
        toile = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        bx0, by0, bx1, by1 = boites()[i_arme]
        if vertical:   # tiers haut de l'écran vertical
            ech = min(0.95, 0.90 * w / (bx1 - bx0), 0.30 * h / (by1 - by0))
            cx, cy = w / 2, 0.37 * h
        else:          # un peu à droite et en haut (titres en bas à gauche)
            ech = min(1.0, 0.74 * W / (bx1 - bx0), 0.62 * H / (by1 - by0))
            cx, cy = 0.53 * W, 0.40 * H
        im = im.resize((int(W * ech), int(H * ech)), Image.LANCZOS)
        toile.paste(im, (int(cx - ech * (bx0 + bx1) / 2), int(cy - ech * (by0 + by1) / 2)))
        rgba = np.asarray(toile, np.float32) / 255
        alpha = rgba[..., 3:4] * poids
        halo = flou(rgba[..., 3:4], 46 if not vertical else 36)[..., 0:1] * poids   # lueur chaude derrière l'arme
        img = img + halo * np.array([1.0, 0.55, 0.28], np.float32) * 0.42
        img = img * (1 - alpha) + rgba[..., :3] * alpha
    for mm in (1, 4, 6, 8, 10):   # éclair aux changements de skin et à la fin (impacts de la musique)
        dt = t - mm * MESURE
        if 0 <= dt < 0.35:
            img = img + (1 - dt / 0.35) ** 2 * 0.55
    lum = img.max(axis=2, keepdims=True)   # bloom
    img = img + flou(np.clip((lum - 0.78) / 0.22, 0, 1) * img, 26, 4) * 0.5
    pous = Image.new("L", (w, h), 0)   # poussières éclairées par le soleil
    dp = ImageDraw.Draw(pous)
    for (x, y, r, vx, vy, lum_p) in dec.poussiere:
        px = (x + vx * t * 3) % w
        py = (y + vy * t * 3 + 8 * math.sin(t * 0.7 + x)) % h
        dp.ellipse([px - r, py - r, px + r, py + r], fill=int(255 * lum_p))
    pous = np.asarray(pous.filter(ImageFilter.GaussianBlur(1.2)), np.float32)[..., None] / 255
    img = img + pous * np.array([1.0, 0.75, 0.5], np.float32) * 0.55
    tx = np.asarray(calque_texte(w, h, vertical, t), np.float32) / 255
    if tx[..., 3].max() > 0:
        ombre = flou(tx[..., 3:4], 10, 2)
        img = img * (1 - 0.55 * ombre)
        img = img * (1 - tx[..., 3:4]) + tx[..., :3] * tx[..., 3:4]
    img = np.clip(img, 0, None)   # étalonnage : contraste doux, tons chauds, vignette, grain
    img = img / (1 + 0.12 * img)
    img = 0.5 + (img - 0.5) * 1.06
    img = img * np.array([1.03, 1.0, 0.96], np.float32)
    img = img * dec.vignette
    if t > DUREE - 0.7:
        img = img * max(0.0, (DUREE - t) / 0.7)
    img = img + np.random.default_rng(k).normal(0, 0.012, img.shape[:2])[..., None]
    return (np.clip(img, 0, 1) * 255).astype(np.uint8)


def _image_16_9(k):
    return image(k, False).tobytes()


def _image_9_16(k):
    return image(k, True).tobytes()


def encoder(fonction, taille, sortie, pool):
    w, h = taille
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % (w, h),
           "-r", str(FPS), "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p",
           "-movflags", "+faststart", str(sortie)]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    for n, octets in enumerate(pool.imap(fonction, range(NB), chunksize=2)):
        p.stdin.write(octets)
        if n % 60 == 0:
            print("  %s : %d / %d" % (Path(sortie).name, n, NB), flush=True)
    p.stdin.close()
    p.wait()


def composer(formats=("16_9", "9_16")):
    (TRAVAIL / "sortie").mkdir(parents=True, exist_ok=True)
    boites()
    with Pool(4) as pool:
        if "16_9" in formats:
            encoder(_image_16_9, (W, H), TRAVAIL / "sortie" / "image_16_9.mp4", pool)
        if "9_16" in formats:
            encoder(_image_9_16, (1080, 1920), TRAVAIL / "sortie" / "image_9_16.mp4", pool)


def finir():
    """Musique (musique.py) et vidéos finales."""
    s = TRAVAIL / "sortie"
    wav = TRAVAIL / "musique.wav"
    subprocess.run([sys.executable, str(ICI / "musique.py"), str(wav)], check=True)
    f = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y"]
    x = ["-c:v", "libx264", "-preset", "slow", "-pix_fmt", "yuv420p", "-movflags", "+faststart"]
    son = ["-c:a", "aac", "-b:a", "192k", "-shortest"]
    v, hz = str(s / "image_9_16.mp4"), str(s / "image_16_9.mp4")
    lancer = lambda *a: subprocess.run(list(a), check=True)
    lancer(*f, "-i", v, "-i", str(wav), "-map", "0:v", "-map", "1:a", *x, "-crf", "18", "-maxrate", "16M", "-bufsize", "32M",
           *son, str(s / "heraliz_skins_tiktok.mp4"))
    lancer(*f, "-i", v, "-map", "0:v", *x, "-crf", "18", "-maxrate", "16M", "-bufsize", "32M",
           str(s / "heraliz_skins_tiktok_sans_musique.mp4"))
    lancer(*f, "-i", hz, "-i", str(wav), "-map", "0:v", "-map", "1:a", *x, "-crf", "19", "-maxrate", "10M", "-bufsize", "20M",
           *son, str(s / "heraliz_skins_discord.mp4"))
    # Discord sans Nitro : 10 Mo au plus (2 passes, 2,35 Mb/s pour l'image)
    journal = str(s / "passe")
    lancer(*f, "-i", hz, "-c:v", "libx264", "-preset", "slow", "-b:v", "2350k", "-pass", "1", "-passlogfile", journal,
           "-an", "-f", "mp4", os.devnull)
    lancer(*f, "-i", hz, "-i", str(wav), "-map", "0:v", "-map", "1:a", "-c:v", "libx264", "-preset", "slow", "-b:v", "2350k",
           "-pass", "2", "-passlogfile", journal, "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "128k", "-shortest",
           "-movflags", "+faststart", str(s / "heraliz_skins_discord_moins_de_10Mo.mp4"))
    for p in s.glob("passe*"):
        p.unlink()
    lancer(*f, "-ss", "3.6", "-i", str(s / "heraliz_skins_tiktok.mp4"), "-frames:v", "1", "-q:v", "2",
           str(s / "couverture_tiktok.jpg"))
    print("vidéos dans", s)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return
    etape = sys.argv[1]
    if etape in ("preparer", "tout"):
        preparer()
    if etape in ("rendre", "tout"):
        rendre()
    if etape in ("composer", "tout"):
        composer(tuple(sys.argv[2:]) if etape == "composer" and len(sys.argv) > 2 else ("16_9", "9_16"))
    if etape in ("finir", "tout"):
        finir()
    if etape == "image":
        k = int(sys.argv[2])
        Image.fromarray(image(k, False)).save(TRAVAIL / ("controle_%04d_16_9.jpg" % k), quality=92)
        Image.fromarray(image(k, True)).save(TRAVAIL / ("controle_%04d_9_16.jpg" % k), quality=92)
        print("images de contrôle dans", TRAVAIL)


if __name__ == "__main__":
    main()
