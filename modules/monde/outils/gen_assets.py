"""Génère les ressources de HeraliZ Décors (cité cachée de la faille, style apocalypse) : textures HD 32 px (animées
pour l'eau et la cascade), modèles 3D (pièces rondes à 16 pans, faces lumineuses par les données de face de Forge),
états de bloc, modèles d'objets, particules, traductions. Efface d'abord les anciennes ressources.

    python modules/monde/outils/gen_assets.py
"""
import json
import math
import os
import shutil

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
A = os.path.join(HERE, "..", "src", "client", "resources", "assets", "heraliz_decors")
NS = "heraliz_decors"
T = 32
RNG = np.random.default_rng(1234)


def dossier(*p):
    d = os.path.join(A, *p)
    os.makedirs(d, exist_ok=True)
    return d


def ecrire_json(chemin, obj):
    os.makedirs(os.path.dirname(chemin), exist_ok=True)
    with open(chemin, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=1, ensure_ascii=False)


# ============================================================================================== bruit tuilable
def bruit(n=T, beta=2.0, graine=None, m=None):
    """Bruit tuilable (filtré en fréquence, 1/f^beta), normalisé 0..1. m : taille (h, w) si non carré."""
    r = np.random.default_rng(graine) if graine is not None else RNG
    h, w = (n, n) if m is None else m
    b = r.normal(size=(h, w))
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    f = np.sqrt(fx ** 2 + fy ** 2)
    f[0, 0] = 1
    s = np.real(np.fft.ifft2(np.fft.fft2(b) / f ** (beta / 2)))
    return (s - s.min()) / (np.ptp(s) + 1e-9)


def bruit_aniso(n=T, sx=1.0, sy=1.0, graine=None, beta=2.0):
    """Bruit tuilable étiré (sx, sy : échelle des fréquences)."""
    r = np.random.default_rng(graine) if graine is not None else RNG
    b = r.normal(size=(n, n))
    fy = np.fft.fftfreq(n)[:, None] * sy
    fx = np.fft.fftfreq(n)[None, :] * sx
    f = np.sqrt(fx ** 2 + fy ** 2)
    f[0, 0] = 1
    s = np.real(np.fft.ifft2(np.fft.fft2(b) / f ** (beta / 2)))
    return (s - s.min()) / (np.ptp(s) + 1e-9)


def rampe(v, couleurs):
    """v (0..1) -> RGB par interpolation entre des couleurs [(t, (r, g, b)), ...]."""
    out = np.zeros(v.shape + (3,))
    ts = [c[0] for c in couleurs]
    for k in range(3):
        out[..., k] = np.interp(v, ts, [c[1][k] for c in couleurs])
    return out


def png(nom, rgb, alpha=None, sous="block", anim=None):
    arr = np.clip(rgb, 0, 255).astype(np.uint8)
    if alpha is not None:
        arr = np.dstack([arr, np.clip(alpha, 0, 255).astype(np.uint8)])
        im = Image.fromarray(arr, "RGBA")
    else:
        im = Image.fromarray(arr, "RGB")
    chemin = os.path.join(dossier("textures", sous), nom + ".png")
    im.save(chemin)
    if anim:
        ecrire_json(chemin + ".mcmeta", {"animation": anim})


def biseau(rgb, cases, fort=0.18):
    """Biseau de taille de pierre sur chaque case (x0, y0, x1, y1) : bord haut/gauche éclairé, bas/droite ombré."""
    for (x0, y0, x1, y1) in cases:
        rgb[y0, x0:x1] *= 1 + fort
        rgb[y0:y1, x0] *= 1 + fort * 0.7
        rgb[y1 - 1, x0:x1] *= 1 - fort
        rgb[y0:y1, x1 - 1] *= 1 - fort * 0.8
    return rgb


# ============================================================================================== textures
def tex_roches():
    """Roche de la faille : pierre marbrée à grande échelle, strates douces et irrégulières (déplacées par du bruit,
    faible contraste), grain fin, quelques fissures fines et grains clairs ; trois teintes (ocre en haut, rouge au
    milieu, sombre au fond). Tuilable dans les deux sens."""
    pal = {
        "roche_ocre": [(0, (112, 82, 56)), (0.3, (146, 110, 76)), (0.55, (172, 136, 96)), (0.8, (196, 162, 118)), (1, (220, 192, 148))],
        "roche_rouge": [(0, (78, 42, 34)), (0.3, (106, 58, 44)), (0.55, (130, 74, 56)), (0.8, (152, 94, 70)), (1, (178, 122, 94))],
        "roche_sombre": [(0, (34, 37, 46)), (0.3, (48, 52, 63)), (0.55, (62, 67, 80)), (0.8, (78, 84, 99)), (1, (100, 107, 122))],
    }
    y = np.arange(T)[:, None] * np.ones((1, T))
    for i, (nom, couleurs) in enumerate(pal.items()):
        r = np.random.default_rng(400 + i)
        depl = (bruit(beta=2.4, graine=410 + i) - 0.5) * 6                # strates ondulées et irrégulières
        strates = 0.5 + 0.5 * np.sin(2 * np.pi * (y + depl) / 16.0) * (0.6 + 0.4 * bruit(beta=2.0, graine=420 + i))
        marbre = bruit(beta=2.2, graine=430 + i)
        grain = bruit(beta=0.9, graine=440 + i)
        v = 0.45 * marbre + 0.25 * strates + 0.30 * grain
        v = (v - v.min()) / (np.ptp(v) + 1e-9)
        rgb = rampe(0.15 + 0.7 * v, couleurs)
        fiss = bruit(beta=2.6, graine=450 + i)
        ligne = np.abs(fiss - 0.5) < 0.012
        rgb[ligne] *= 0.7
        rgb[np.roll(ligne, 1, axis=0) & ~ligne] *= 1.06
        clairs = r.random((T, T)) < 0.025
        rgb[clairs] *= 1.12
        png(nom, rgb)
    # concrétion (stalactites, rochers) : calcaire gris-beige coulé, traînées verticales
    v = 0.55 * bruit_aniso(sx=2.5, sy=0.35, graine=460) + 0.45 * bruit(beta=1.4, graine=461)
    png("concretion", rampe(v, [(0, (70, 68, 66)), (0.4, (104, 100, 94)), (0.75, (138, 132, 122)), (1, (170, 164, 150))]))




def tex_metaux():
    """Fer noir, fer rouillé, tôle émaillée verte des abat-jour (éclats, coulures de rouille), émail intérieur, culot en
    bakélite, câble gainé."""
    g = bruit(beta=1.4, graine=50)
    rgb = rampe(g, [(0, (28, 28, 32)), (0.5, (46, 46, 52)), (1, (70, 70, 78))])
    for (x, y) in ((3, 3), (28, 3), (3, 28), (28, 28), (16, 3), (16, 28)):
        rgb[y - 1:y + 1, x - 1:x + 1] = (96, 96, 104)
    rayures = np.abs(bruit_aniso(sx=0.2, sy=4, graine=51) - 0.5) < 0.02
    rgb[rayures] *= 1.25
    png("fer_noir", rgb)
    base = rampe(bruit(beta=1.6, graine=55), [(0, (30, 28, 28)), (0.5, (48, 44, 42)), (1, (72, 66, 60))])
    rouille = rampe(bruit(beta=1.0, graine=57), [(0, (92, 44, 20)), (0.5, (140, 70, 30)), (1, (186, 106, 52))])
    m = np.clip((bruit(beta=2.2, graine=56) - 0.45) * 4, 0, 1)[..., None]
    png("fer_rouille", base * (1 - m) + rouille * m)
    rgb = rampe(bruit(beta=1.6, graine=58), [(0, (36, 62, 50)), (0.5, (50, 86, 66)), (1, (68, 106, 82))])
    rgb[bruit(beta=2.4, graine=59) > 0.84] = (34, 32, 30)
    c = np.clip((bruit_aniso(sx=4, sy=0.25, graine=60) - 0.68) * 4, 0, 1)[..., None]
    png("tole_verte", rgb * (1 - c * 0.6) + np.array([130, 70, 34]) * c * 0.6)
    png("email", rampe(bruit(beta=1.4, graine=61), [(0, (214, 206, 184)), (1, (244, 240, 226))]))
    png("culot", rampe(bruit(beta=1.2, graine=62), [(0, (36, 26, 20)), (1, (70, 52, 38))]))
    y, x = np.mgrid[0:T, 0:T]
    v = 0.4 * (0.5 + 0.5 * np.sin(2 * np.pi * (x + y) / 8)) + 0.6 * bruit(beta=1.0, graine=63)
    png("cable", rampe(v, [(0, (14, 14, 16)), (1, (46, 46, 50))]))


def tex_lumieres():
    """Ampoule chaude, lentille de projecteur (anneaux de Fresnel), halo doux, faisceau, verre ambré, cristaux."""
    y, x = np.mgrid[0:T, 0:T]
    r = np.hypot(x - 15.5, y - 15.5)
    png("ampoule", rampe(np.clip(1 - r / 22, 0, 1), [(0, (255, 170, 80)), (0.5, (255, 214, 140)), (1, (255, 250, 232))]))
    v = np.clip(1 - r / 17, 0, 1) * (0.75 + 0.25 * np.cos(r * 1.6))
    rgb = rampe(v, [(0, (150, 120, 80)), (0.4, (240, 214, 160)), (0.8, (255, 244, 214)), (1, (255, 255, 250))])
    rgb[r > 15.2] = (40, 38, 36)
    png("lentille", rgb)
    halo = np.zeros((T, T, 3))
    halo[...] = (255, 222, 160)
    png("halo", halo, alpha=np.clip(1 - r / 16, 0, 1) ** 2.2 * 150)
    # faisceau : cône qui s'ouvre de gauche (lentille, u=0) à droite (loin), s'efface au loin, légères stries
    u = x / (T - 1)
    demi = 4 + 12 * u
    bord = np.clip((demi - np.abs(y - 15.5)) / 3, 0, 1)
    stries = 0.8 + 0.2 * bruit_aniso(sx=0.2, sy=3, graine=64)
    a = bord * (1 - u) ** 1.3 * stries * 70
    png("faisceau", halo, alpha=a)
    png("faisceau_v", halo, alpha=a.T[::-1, :])                         # même cône, lentille en bas (plan horizontal)
    d = np.hypot(x - 15.5, y - 15.5) / 22
    rgb = rampe(np.clip(1 - d, 0, 1), [(0, (200, 110, 30)), (0.6, (250, 180, 70)), (1, (255, 236, 170))])
    rgb[(x % 16 == 0) | (y % 16 == 0)] *= 0.55
    png("verre_ambre", rgb, alpha=np.full((T, T), 235))
    for nom, (c0, c1, c2) in (("cristal_bleu", ((20, 70, 140), (80, 190, 240), (230, 250, 255))),
                              ("cristal_ambre", ((140, 60, 10), (240, 160, 50), (255, 240, 190)))):
        v = np.clip(0.55 * bruit_aniso(sx=0.5, sy=2.5, graine=80) + 0.45 * (1 - np.abs(x - 15.5) / 16), 0, 1)
        rgb = rampe(v, [(0, c0), (0.65, c1), (1, c2)])
        reflet = (np.abs((x - y) % 16 - 3) < 1.2)
        rgb[reflet] = rgb[reflet] * 0.5 + np.array(c2) * 0.5
        png(nom, rgb, alpha=np.full((T, T), 225))


def aniso(h, w, sx, sy, graine, beta=2.0):
    """Bruit tuilable non carré (h x w), étiré : sx, sy échelles des fréquences (sx petit, sy grand : traînées
    verticales)."""
    r = np.random.default_rng(graine)
    b = r.normal(size=(h, w))
    fy = np.fft.fftfreq(h)[:, None] * sy * (h / 64)
    fx = np.fft.fftfreq(w)[None, :] * sx * (w / 64)
    f = np.sqrt(fx ** 2 + fy ** 2)
    f[0, 0] = 1
    v = np.real(np.fft.ifft2(np.fft.fft2(b) / f ** (beta / 2)))
    return (v - v.min()) / (np.ptp(v) + 1e-9)


def tex_eau():
    """Les textures validées sur les aperçus du 08/10 (apercu_effets.py du dossier de travail de la carte) :
    - grande cascade (entity/, hors atlas : elles se répètent et défilent) : nappe 64 x 128 (traînées verticales,
      écume blanche, trous plus clairs), voile d'embruns, écume du pied ;
    - surface d'eau (block/eau_surface, 64 x 64 par bloc, 32 images en boucle) : léger voile turquoise, fines lignes de
      caustiques qui ondulent, lente dérive vers le sud ; une seule pour toute l'eau, jamais tournée."""
    a, b = aniso(128, 64, 0.22, 5.0, 901, 1.7), aniso(128, 64, 0.5, 3.0, 902, 1.2)
    v = 0.6 * a + 0.4 * b
    v = (v - v.min()) / np.ptp(v)
    rgb = rampe(v, [(0, (22, 70, 82)), (0.35, (56, 132, 140)), (0.62, (140, 204, 206)), (0.8, (226, 246, 246)), (1, (255, 255, 255))])
    al = 150 + 105 * v
    al = np.where(aniso(128, 64, 0.7, 2.5, 903, 1.6) < 0.22, al * 0.55, al)
    png("chute", rgb, alpha=al, sous="entity")
    v = aniso(128, 64, 0.18, 6.0, 904, 1.3)
    png("chute_voile", rampe(v, [(0, (170, 220, 222)), (1, (255, 255, 255))]), alpha=np.clip((v - 0.45) * 2.2, 0, 1) * 200,
        sous="entity")
    v = 0.6 * bruit(n=64, beta=1.4, graine=905) + 0.4 * bruit(n=64, beta=0.8, graine=906)
    png("chute_ecume", rampe(v, [(0, (200, 232, 232)), (1, (255, 255, 255))]), alpha=np.clip((v - 0.3) * 2.0, 0, 1) * 235,
        sous="entity")
    n = 64
    yy0, xx = np.mgrid[0:n, 0:n] / n
    r = np.random.default_rng(95)
    ondes = []
    while len(ondes) < 12:
        kx, ky = int(r.integers(-4, 5)), int(r.integers(-4, 5))
        if kx or ky:
            ondes.append((kx, ky, int(r.integers(1, 3)) * int(r.choice([-1, 1])), r.random() * 6.283))
    frames, alphas = [], []
    for k in range(32):
        t = k / 32
        yy = (yy0 - t) % 1.0
        v1 = sum(np.sin(2 * np.pi * (kx * xx + ky * yy + m * t) + ph) for (kx, ky, m, ph) in ondes[:6]) / 2.4
        v2 = sum(np.sin(2 * np.pi * (kx * xx + ky * yy + m * t) + ph) for (kx, ky, m, ph) in ondes[6:]) / 2.4
        c = np.clip(np.exp(-v1 ** 2 * 16) * 0.5 + np.exp(-v2 ** 2 * 16) * 0.4, 0, 1) ** 1.3
        frames.append(np.array([90, 170, 160])[None, None] * (1 - c[..., None]) + np.array([232, 252, 246])[None, None] * c[..., None])
        alphas.append(18 + 80 * c)
    png("eau_surface", np.vstack(frames), alpha=np.vstack(alphas), anim={"frametime": 3, "interpolate": True})


def tex_mousse():
    """Mousse pendante (lichen en filaments) : fils gris-vert qui ondulent, la moitié traverse tout le bloc (se raccorde
    au bloc du dessous), les autres s'arrêtent plus ou moins bas. Deux variantes."""
    for k in range(2):
        r = np.random.default_rng(130 + k)
        rgb = np.zeros((T, T, 3))
        a = np.zeros((T, T))
        for _ in range(13):
            x0 = int(r.integers(0, T))
            L = T if r.random() < 0.5 else int(r.integers(8, T))
            col = np.array([96, 118, 74]) * (0.7 + 0.55 * r.random())
            ph, amp = r.random() * 6.283, 0.6 + r.random() * 1.0
            for y in range(L):
                xx = int(round(x0 + amp * math.sin(2 * math.pi * y / T * 2 + ph))) % T
                f = 1 - (0.35 * y / T if L < T else 0)
                rgb[y, xx] = col * f
                a[y, xx] = 255
                if r.random() < 0.3:
                    rgb[y, (xx + 1) % T] = col * f * 0.85
                    a[y, (xx + 1) % T] = 255
        png(f"mousse_{k}", rgb, alpha=a)


def tex_particules():
    y, x = np.mgrid[0:T, 0:T]
    for k in range(3):
        n = bruit(beta=2.0, graine=120 + k)
        d = np.hypot(x - 15.5, y - 15.5) / 16
        a = np.clip(1 - d, 0, 1) ** 1.6 * (0.55 + 0.45 * n) * 255
        png(f"brume_{k}", np.full((T, T, 3), 245.0), alpha=a, sous="particle")
    s = 8
    yy, xx = np.mgrid[0:s, 0:s]
    d = np.hypot(xx - 3.5, yy - 3.5) / 4
    png("poussiere", np.full((s, s, 3), 255.0), alpha=np.clip(1 - d, 0, 1) ** 1.5 * 255, sous="particle")
    etoile = np.maximum(np.clip(1 - np.abs(xx - 3.5) / 0.9, 0, 1) * np.clip(1 - np.abs(yy - 3.5) / 3.6, 0, 1),
                        np.clip(1 - np.abs(yy - 3.5) / 0.9, 0, 1) * np.clip(1 - np.abs(xx - 3.5) / 3.6, 0, 1))
    png("eclat", np.full((s, s, 3), 255.0), alpha=np.maximum(etoile, np.clip(1 - d * 1.8, 0, 1)) * 255, sous="particle")
    png("embrun", np.full((s, s, 3), 245.0), alpha=np.clip(1 - d * 1.2, 0, 1) * 220, sous="particle")
    for nom, src in (("brume", "brume"), ("brume_basse", "brume"), ("poussiere", "poussiere"), ("poussiere_doree", "poussiere"),
                     ("luciole", "poussiere"), ("eclat", "eclat"), ("embrun", "embrun")):
        tex = [f"{NS}:brume_{k}" for k in range(3)] if src == "brume" else [f"{NS}:{src}"]
        ecrire_json(os.path.join(dossier("particles"), nom + ".json"), {"textures": tex})


def tex_icones():
    """Icônes d'objet des blocs sans modèle visible (ambiance, cascade, surface d'eau)."""
    y, x = np.mgrid[0:16, 0:16]
    d = np.hypot(x - 7.5, y - 7.5) / 8
    png("ambiance", np.full((16, 16, 3), 225.0), alpha=np.clip(1 - d, 0, 1) * 220, sous="item")
    rgb = np.zeros((16, 16, 3))
    rgb[...] = (120, 200, 210)
    rgb[(x + y * 3) % 7 == 0] = (240, 252, 252)
    al = np.where((x >= 4) & (x <= 11) & (y >= 1), 235, 0)
    al[0:2, 2:14] = 255
    rgb[0:2, 2:14] = (110, 100, 90)
    png("grande_cascade", rgb, alpha=al, sous="item")
    rgb = np.zeros((16, 16, 3))
    rgb[...] = (46, 130, 124)
    vague = np.abs(((y + 2 * np.sin(x / 2.5)) % 5) - 2.5) < 0.7
    rgb[vague] = (190, 236, 230)
    png("eau_decor", rgb, alpha=np.where((x > 0) & (x < 15) & (y > 1) & (y < 14), 255, 0), sous="item")


# ============================================================================================== taches de sang
# Validées le 09/10 sur l'aperçu apercu_sang.py du dossier de travail de la carte (même dessin, mêmes graines). Chaque tache tient dans UN bloc ;
# la traînée se raccorde d'un bloc au suivant (tout ce qui varie le long de la traînée est périodique).
FORMES_SANG = (("petite_flaque", "flaque", 1000), ("grande_flaque", "grande_flaque", 1007),
               ("eclaboussures", "eclaboussures", 1014), ("trainee", "trainee", 1021), ("gouttes", "gouttes", 1028),
               ("empreintes", "empreintes", 1035), ("main", "main", 1042), ("projection", "mur", 2024))


def couleur_sang(v, frais):
    """v : épaisseur 0..1 ; frais : 0 (séché, brun) .. 1 (frais, rouge sombre brillant)."""
    sec = rampe(v, [(0, (58, 22, 14)), (0.5, (78, 18, 12)), (1, (96, 14, 12))])
    hum = rampe(v, [(0, (88, 10, 10)), (0.6, (120, 12, 14)), (1, (150, 22, 24))])
    return sec * (1 - frais[..., None]) + hum * frais[..., None]


def _deforme(x, y, graine, force=0.06):
    """Coordonnées déplacées par du bruit : des bords organiques, jamais un rond parfait."""
    dx = (bruit(beta=2.0, graine=graine + 11) - 0.5) * 2 * force
    dy = (bruit(beta=2.0, graine=graine + 12) - 0.5) * 2 * force
    return x + dx, y + dy


def tache(forme, graine):
    """-> (rgb, alpha) 32 x 32 ; le bloc va de -0,5 à 0,5 (y vers le bas de l'image = vers le sud / vers le bas du mur)."""
    r = np.random.default_rng(graine)
    y, x = (np.mgrid[0:T, 0:T] + 0.5) / T - 0.5
    x, y = _deforme(x, y, graine)
    rr = np.hypot(x, y)
    n1 = bruit(beta=2.2, graine=graine)
    n2 = bruit(beta=1.2, graine=graine + 1)
    a = np.zeros((T, T))

    def goutte(cx, cy, s, allonge=1.0, ang=0.0):
        ca, sa = math.cos(ang), math.sin(ang)
        u, v = (x - cx) * ca + (y - cy) * sa, -(x - cx) * sa + (y - cy) * ca
        return np.clip((s - np.hypot(u / allonge, v)) * 40, 0, 1)
    if forme == "flaque":
        a = np.clip((0.27 + 0.06 * (n1 - 0.5) * 2 - rr) * 14, 0, 1)
        for _ in range(5):
            ang = r.random() * 6.28
            a = np.maximum(a, goutte(0.27 * math.cos(ang), 0.27 * math.sin(ang), 0.06 + r.random() * 0.04))
    elif forme == "grande_flaque":
        a = np.clip((0.4 + 0.07 * (n1 - 0.5) * 2 - rr) * 12, 0, 1)
        for _ in range(4):
            ang = r.random() * 6.28
            a = np.maximum(a, goutte(0.36 * math.cos(ang), 0.36 * math.sin(ang), 0.07 + r.random() * 0.03))
    elif forme == "eclaboussures":
        a = np.clip((0.17 + 0.06 * (n1 - 0.5) - rr) * 16, 0, 1)
        for _ in range(22):
            ang = r.random() * 6.28
            d = 0.16 + r.random() * 0.3
            s_ = 0.018 + r.random() * 0.04
            a = np.maximum(a, goutte(d * math.cos(ang), d * math.sin(ang), s_, allonge=1.6, ang=ang))
    elif forme == "trainee":                                 # corps traîné du nord au sud, se raccorde au bloc suivant
        larg = 0.2 + 0.035 * np.sin(y * 2 * math.pi + graine) + 0.05 * (n1 - 0.5)
        a = np.clip((larg - np.abs(x)) * 14, 0, 1)
        a *= 0.8 + 0.2 * np.sin(x * 70)                      # stries dans le sens du mouvement, sans trou
        for k in (-0.23, 0.23):                              # doigts qui ont griffé le sol de part et d'autre
            a = np.maximum(a, (np.abs(x - k) < 0.022) * (y > -0.1) * (y < 0.35) * 1.0)
    elif forme == "gouttes":
        for _ in range(14):
            a = np.maximum(a, goutte(r.uniform(-0.4, 0.4), r.uniform(-0.4, 0.4), 0.03 + r.random() * 0.05))
    elif forme == "empreintes":                              # deux pas de chaussure, vers le nord
        for (cx, cy) in ((-0.15, 0.13), (0.15, -0.25)):
            semelle = ((x - cx) / 0.12) ** 2 + ((y - cy + 0.03) / 0.21) ** 2 < 1
            talon = ((x - cx) / 0.1) ** 2 + ((y - cy - 0.25) / 0.085) ** 2 < 1
            stries = np.sin((y - cy) * 80) > -0.7
            a = np.maximum(a, (semelle | talon) * stries * 1.0)
        a *= 0.7 + 0.3 * n2
    elif forme == "main":                                    # main posée puis qui a glissé : paume, doigts, traînées
        paume = ((x + 0.02) / 0.19) ** 2 + ((y - 0.1) / 0.2) ** 2 < 1
        a = paume * 1.0
        for (ox, l, ang) in ((-0.19, 0.24, -0.25), (-0.07, 0.34, -0.08), (0.05, 0.35, 0.06), (0.16, 0.28, 0.2)):
            for t in np.linspace(0, 1, 9):                   # doigt = chapelet de ronds, un peu écartés
                a = np.maximum(a, goutte(ox + math.sin(ang) * l * t, -0.02 - l * t, 0.045))
        for t in np.linspace(0, 1, 7):                       # pouce, sur le côté
            a = np.maximum(a, goutte(0.17 + 0.13 * t, 0.12 - 0.1 * t, 0.05))
        for k in (-0.12, -0.03, 0.06):                       # la main a glissé : trois traînées sous la paume
            a = np.maximum(a, (np.abs(x - k) < 0.03) * (y > 0.25) * (y < 0.47) * 1.0)
        a *= 0.8 + 0.2 * n2
    elif forme == "mur":                                     # projection sur un mur, qui a coulé
        a = np.clip((0.26 + 0.07 * (n1 - 0.5) - np.hypot(x, (y + 0.15) * 1.15)) * 14, 0, 1)
        for _ in range(18):
            ang = r.random() * 6.28
            d = 0.22 + r.random() * 0.2
            a = np.maximum(a, goutte(d * math.cos(ang), -0.15 + d * math.sin(ang) * 0.8, 0.025 + r.random() * 0.035,
                                     allonge=1.6, ang=ang))
        for _ in range(7):
            cx = r.uniform(-0.2, 0.2)
            l = r.uniform(0.25, 0.6)
            larg = 0.022 + r.random() * 0.018
            a = np.maximum(a, (np.abs(x - cx) < larg) * (y > -0.1) * (y < -0.1 + l) * 1.0)
            a = np.maximum(a, goutte(cx, -0.1 + l, larg * 1.7))
    epais = np.clip(a * (0.55 + 0.45 * n2), 0, 1)
    bord = (a > 0.35) & (np.clip(a, 0, 1) < 0.75)            # anneau séché, plus sombre, sur le pourtour
    frais = np.clip(epais * 1.4 - 0.4, 0, 1) * (0.55 + 0.45 * n1)
    rgb = couleur_sang(epais, frais)
    rgb[bord] *= 0.72
    reflet = (frais > 0.5) & (n2 > 0.7)
    rgb[reflet] = rgb[reflet] * 0.55 + np.array([215, 125, 125]) * 0.45
    alpha = np.where(a > 0.35, 255, 0)                       # découpe nette (rendu « cutout », le moins coûteux)
    return rgb, alpha


def tex_sang():
    for nom, dessin, graine in FORMES_SANG:
        rgb, al = tache(dessin, graine)
        png(f"tache_sang_{nom}", rgb, alpha=al)


def modeles_sang():
    """Une image à plat, à un quart de pixel du sol (sol) ou du mur (mur, le mur est au sud pour facing=north) :
    un seul quadrilatère, découpé (cutout), aucune collision."""
    for nom, _, _ in FORMES_SANG:
        t = {"t": f"tache_sang_{nom}", "particle": f"tache_sang_{nom}"}
        modele(f"tache_sang_{nom}_sol", t, [{"from": [0, 0.25, 0], "to": [16, 0.25, 16],
                                              "faces": {"up": face("t", [0, 0, 16, 16])}}], rendu="minecraft:cutout")
        modele(f"tache_sang_{nom}_mur", t, [{"from": [0, 0, 15.75], "to": [16, 16, 15.75],
                                              "faces": {"north": face("t", [0, 0, 16, 16])}}], rendu="minecraft:cutout")


# ============================================================================================== modèles
LUM = {"block_light": 15, "sky_light": 15, "ambient_occlusion": False}


def lum(n):
    return {"block_light": n, "sky_light": n, "ambient_occlusion": False}


def face(tex, uv=None, lum=None, cull=None, rot=None):
    f = {"texture": "#" + tex}
    if uv:
        f["uv"] = uv
    if lum:
        f["forge_data"] = lum if isinstance(lum, dict) else {"block_light": lum, "sky_light": lum, "ambient_occlusion": False}
    if cull:
        f["cullface"] = cull
    if rot:
        f["rotation"] = rot
    return f


def boite(a, b, tex, faces="nsewud", lum=None, rot=None, uvs=None, shade=True):
    """Élément de a à b (en pixels), même texture sur les faces demandées (uv proportionnels à la taille)."""
    noms = {"n": "north", "s": "south", "e": "east", "w": "west", "u": "up", "d": "down"}
    el = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b], "faces": {}}
    dx, dy, dz = (b[0] - a[0], b[1] - a[1], b[2] - a[2])
    for c in faces:
        if uvs and c in uvs:
            uv = uvs[c]
        elif c in "ns":
            uv = [0, 0, min(16, max(0.01, dx)), min(16, max(0.01, dy))]
        elif c in "ew":
            uv = [0, 0, min(16, max(0.01, dz)), min(16, max(0.01, dy))]
        else:
            uv = [0, 0, min(16, max(0.01, dx)), min(16, max(0.01, dz))]
        el["faces"][noms[c]] = face(tex, uv, lum)
    if rot:
        el["rotation"] = dict(rot)
    if not shade:
        el["shade"] = False
    return el


def cylindre(cx, cz, r, y0, y1, tex, n=8, faces="nsewud", lum=None, tex_bout=None, lum_bout=None):
    """Cylindre plein : n/2 orientations x 2 rectangles -> polygone régulier (n=8 : 16 pans, n=4 : 8 pans)."""
    els = []
    angles = [0, 22.5, 45, -22.5] if n == 8 else [0, 45]
    s = 2 * r * math.tan(math.pi / (2 * len(angles) * 2))
    for ang in angles:
        for (lx, lz) in ((2 * r, s), (s, 2 * r)):
            a = [cx - lx / 2, y0, cz - lz / 2]
            b = [cx + lx / 2, y1, cz + lz / 2]
            rot = {"origin": [cx, (y0 + y1) / 2, cz], "axis": "y", "angle": ang} if ang else None
            el = boite(a, b, tex, "nsew", lum=lum, rot=rot)
            for c, nom in (("u", "up"), ("d", "down")):
                if c in faces:
                    el["faces"][nom] = face(tex_bout or tex, [0, 0, min(16, lx), min(16, lz)], lum_bout or lum)
            els.append(el)
    return els


def anneau(cx, cz, r_in, r_out, y0, y1, ext, intr, lum_int=None, haut=True, bas=True):
    """Anneau creux à 16 pans (16 plaques épaisses tournées autour de l'axe) : face extérieure en `ext`, face intérieure
    en `intr` (éventuellement lumineuse), dessus et dessous pour fermer les marches entre anneaux. Les dessus et
    dessous se recouvrent près du bord intérieur : on les mappe sur un même petit carré de texture (pas de
    scintillement visible)."""
    els = []
    s = 2 * r_out * math.tan(math.pi / 16) + 0.05
    carre = [7.5, 7.5, 8.5, 8.5]
    for ang in (0, 22.5, 45, -22.5):
        for cote in ("+x", "-x", "+z", "-z"):
            if cote == "+x":
                a, b, fe, fi = [cx + r_in, y0, cz - s / 2], [cx + r_out, y1, cz + s / 2], "east", "west"
            elif cote == "-x":
                a, b, fe, fi = [cx - r_out, y0, cz - s / 2], [cx - r_in, y1, cz + s / 2], "west", "east"
            elif cote == "+z":
                a, b, fe, fi = [cx - s / 2, y0, cz + r_in], [cx + s / 2, y1, cz + r_out], "south", "north"
            else:
                a, b, fe, fi = [cx - s / 2, y0, cz - r_out], [cx + s / 2, y1, cz - r_in], "north", "south"
            h = min(16, max(0.01, y1 - y0))
            el = {"from": [round(v, 3) for v in a], "to": [round(v, 3) for v in b],
                  "faces": {fe: face(ext, [0, 0, min(16, s), h]), fi: face(intr, [0, 0, min(16, s), h], lum_int)}}
            if haut:
                el["faces"]["up"] = face(ext, carre)
            if bas:
                el["faces"]["down"] = face(intr, carre, lum_int)
            if ang:
                el["rotation"] = {"origin": [cx, (y0 + y1) / 2, cz], "axis": "y", "angle": ang}
            els.append(el)
    return els


def plan_croise(cx, cz, larg, y0, y1, tex, lum=None, uv=None, ang=45):
    """Deux plans verticaux croisés centrés en (cx, cz)."""
    els = []
    for a in (ang, -ang):
        el = {"from": [cx - larg / 2, y0, cz], "to": [cx + larg / 2, y1, cz],
              "rotation": {"origin": [cx, y0, cz], "axis": "y", "angle": a, "rescale": False}, "shade": False,
              "faces": {"north": face(tex, uv or [0, 0, 16, 16], lum), "south": face(tex, uv or [0, 0, 16, 16], lum)}}
        els.append(el)
    return els


def modele(nom, textures, elements, rendu=None, ao=True, parent=None, affichage=None):
    m = {}
    if parent:
        m["parent"] = parent
    m["textures"] = {k: (v if ":" in v else f"{NS}:block/{v}") for k, v in textures.items()}
    if elements is not None:
        m["elements"] = elements
    if rendu:
        m["render_type"] = rendu
    if not ao:
        m["ambientocclusion"] = False
    if affichage:
        m["display"] = affichage
    ecrire_json(os.path.join(dossier("models", "block"), nom + ".json"), m)


AFF = {"gui": {"rotation": [30, 225, 0], "translation": [0, 0, 0], "scale": [0.6, 0.6, 0.6]},
       "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
       "fixed": {"scale": [0.5, 0.5, 0.5]},
       "thirdperson_righthand": {"rotation": [75, 45, 0], "translation": [0, 2.5, 0], "scale": [0.375, 0.375, 0.375]},
       "firstperson_righthand": {"rotation": [0, 45, 0], "scale": [0.4, 0.4, 0.4]}}


def modeles_lanternes():
    """Lanterne en fer (sol, mur, plafond) : pied, verre ambré lumineux, montants et bague de protection, chapeau
    rouillé, anse."""
    def lanterne(dy=0):
        e = []
        e += cylindre(8, 8, 4.2, 0 + dy, 1.5 + dy, "fer", n=4)
        e.append(boite([5, 1.5 + dy, 5], [11, 9.5 + dy, 11], "verre", "nsew", lum=15))
        for (x, z) in ((4.5, 4.5), (10.5, 4.5), (4.5, 10.5), (10.5, 10.5)):
            e.append(boite([x, 1.5 + dy, z], [x + 1, 9.5 + dy, z + 1], "fer"))
        e.append(boite([4.6, 5 + dy, 4.6], [11.4, 5.6 + dy, 11.4], "fer", "nsew"))
        e += cylindre(8, 8, 4.4, 9.5 + dy, 10.5 + dy, "rouille", n=4)
        e += cylindre(8, 8, 3.2, 10.5 + dy, 11.5 + dy, "fer", n=4)
        e += cylindre(8, 8, 1.8, 11.5 + dy, 12.5 + dy, "fer", n=4)
        e.append(boite([7.5, 12.5 + dy, 6.5], [8.5, 13.5 + dy, 9.5], "fer"))
        return e
    tx = {"fer": "fer_noir", "rouille": "fer_rouille", "verre": "verre_ambre", "particle": "fer_noir"}
    modele("lanterne_fer_sol", tx, lanterne(0), rendu="minecraft:cutout", affichage=AFF)
    e = lanterne(2.5)
    e.append(boite([7.5, 15, 7.5], [8.5, 16, 8.5], "fer"))
    e += plan_croise(8, 8, 3, 13.5, 16, "chaine", uv=[0, 0, 3, 16])
    modele("lanterne_fer_plafond", dict(tx, chaine="minecraft:block/chain"), e, rendu="minecraft:cutout")
    e = lanterne(1.5)
    e.append(boite([7.5, 11, 8], [8.5, 12, 16], "fer"))                   # potence vers le mur (sud)
    e.append(boite([6, 8, 15], [10, 14, 16], "rouille"))                  # platine au mur
    modele("lanterne_fer_mur", tx, e, rendu="minecraft:cutout")


def modeles_lampes():
    """Grosse lampe d'atelier suspendue (3 tailles) : rosace au plafond, câble, chapeau, abat-jour conique en tôle
    émaillée verte (anneaux creux à 16 pans, intérieur émaillé blanc éclairé), culot, ampoule lumineuse et halo."""
    tailles = {  # r_haut, y_haut, r_bas, y_bas, anneaux, rayon ampoule, bas ampoule
        "petit": (1.8, 10.5, 4.6, 7.5, 4, 1.25, 5.8),
        "moyen": (2.2, 10.0, 6.6, 5.0, 6, 1.6, 3.4),
        "grand": (2.6, 9.6, 9.0, 3.4, 8, 1.95, 1.4),
    }
    for nom, (rh, yh, rb, yb, n, ra, ya) in tailles.items():
        e = [boite([6, 15.4, 6], [10, 16, 10], "fer", "nsewd"),
             boite([7.6, yh + 1.5, 7.6], [8.4, 15.4, 8.4], "cable", "nsew")]
        chapeau = cylindre(8, 8, rh + 0.2, yh, yh + 1.5, "tole", n=8, faces="ud")
        for el in chapeau:
            el["faces"]["down"] = face("email", [7.5, 7.5, 8.5, 8.5], lum(13))
        e += chapeau
        r_prec = rh
        for i in range(n):
            y1 = yh - i * (yh - yb) / n
            y0 = yh - (i + 1) * (yh - yb) / n
            r_out = rh + (rb - rh) * ((i + 1) / n) ** 1.7
            e += anneau(8, 8, max(0.3, r_prec - 0.35), r_out, y0, y1, "tole", "email", lum_int=lum(13))
            r_prec = r_out
        e += anneau(8, 8, rb - 0.2, rb + 0.45, yb, yb + 0.5, "tole", "email", lum_int=lum(13))     # rebord roulé
        e += cylindre(8, 8, ra * 0.75, ya + 2.6 * ra, yh, "culot", n=4, faces="d")
        e += cylindre(8, 8, ra, ya + 0.6, ya + 2.6 * ra, "ampoule", n=8, lum=15, faces="d")
        e += cylindre(8, 8, ra * 0.6, ya, ya + 0.6, "ampoule", n=4, lum=15, faces="d")
        cy = ya + 1.3 * ra
        e += plan_croise(8, 8, ra * 5, cy - ra * 2.5, cy + ra * 2.5, "halo", lum=LUM)
        e.append({"from": [8 - rb * 0.9, yb + 0.2, 8 - rb * 0.9], "to": [8 + rb * 0.9, yb + 0.2, 8 + rb * 0.9], "shade": False,
                  "faces": {"down": face("halo", [0, 0, 16, 16], LUM)}})
        modele(f"lampe_suspendue_{nom}", {"tole": "tole_verte", "email": "email", "cable": "cable", "fer": "fer_noir",
                                          "culot": "culot", "ampoule": "ampoule", "halo": "halo", "particle": "tole_verte"},
               e, rendu="minecraft:translucent", affichage=AFF)


def modeles_guirlande():
    """Guirlande d'ampoules (le long de x) : câble tendu, deux ampoules qui pendent, chacune avec son petit halo."""
    e = [boite([0, 10.6, 7.7], [16, 11.2, 8.3], "cable", "nsud", uvs={"n": [0, 0, 16, 1], "s": [0, 0, 16, 1],
                                                                       "u": [0, 0, 16, 1], "d": [0, 0, 16, 1]})]
    for x in (4, 12):
        e.append(boite([x - 0.25, 9.4, 7.75], [x + 0.25, 10.6, 8.25], "cable", "nsew"))
        e += cylindre(x, 8, 0.7, 8.5, 9.4, "culot", n=4, faces="u")
        e += cylindre(x, 8, 0.95, 6.4, 8.5, "ampoule", n=4, lum=15, faces="")
        e += cylindre(x, 8, 0.55, 5.8, 6.4, "ampoule", n=4, lum=15, faces="d")
        e += plan_croise(x, 8, 5, 4.9, 9.9, "halo", lum=LUM)
    modele("guirlande", {"cable": "cable", "culot": "culot", "ampoule": "ampoule", "halo": "halo", "particle": "cable"}, e,
           rendu="minecraft:translucent", affichage=AFF)


def modeles_projecteur():
    """Projecteur de chantier (face au nord, mur au sud) : boîtier, lentille lumineuse et sa bague, visière, ailettes
    de refroidissement, étrier, faisceau doux qui sort de la lentille. Au sol il vise un peu vers le haut (pied rond),
    au mur et au plafond un peu vers le bas (potence)."""
    for pose in ("sol", "mur", "plafond"):
        ang = 22.5 if pose == "sol" else -22.5
        rot = {"origin": [8, 9, 7], "axis": "x", "angle": ang}
        corps = [boite([3, 5, 4], [13, 13, 9.5], "fer"),
                 boite([3.8, 5.8, 3.7], [12.2, 12.2, 4], "lentille", "n", lum=15, uvs={"n": [0, 0, 16, 16]}),
                 boite([3, 12.2, 3.3], [13, 13, 4.2], "rouille", "nsewu"), boite([3, 5, 3.3], [13, 5.8, 4.2], "rouille", "nsewd"),
                 boite([3, 5.8, 3.3], [3.8, 12.2, 4.2], "rouille", "nswe"), boite([12.2, 5.8, 3.3], [13, 12.2, 4.2], "rouille", "nsew"),
                 boite([3, 13, 1.5], [13, 13.6, 5], "fer")]
        for x in (4.5, 6.5, 8.5, 10.5):
            corps.append(boite([x - 0.4, 5.6, 9.5], [x + 0.4, 12.4, 11.2], "fer", "sewud"))
        corps.append({"from": [8, -2, -16], "to": [8, 20, 3.7], "shade": False,
                      "faces": {"east": face("faisceau", [0, 0, 16, 16], LUM), "west": face("faisceau", [16, 0, 0, 16], LUM)}})
        corps.append({"from": [-3, 9, -16], "to": [19, 9, 3.7], "shade": False,
                      "faces": {"up": face("faisceau_v", [0, 0, 16, 16], LUM), "down": face("faisceau_v", [0, 16, 16, 0], LUM)}})
        for el in corps:
            el["rotation"] = dict(rot)
        e = corps
        e += [boite([1.8, 6, 6], [3, 10, 8], "fer"), boite([13, 6, 6], [14.2, 10, 8], "fer")]           # bras de l'étrier
        if pose == "plafond":
            e += [boite([1.8, 10, 6], [3, 14.6, 8], "fer"), boite([13, 10, 6], [14.2, 14.6, 8], "fer"),
                  boite([1.8, 14.6, 6], [14.2, 15.4, 8], "fer"), boite([5, 15.4, 4], [11, 16, 10], "rouille")]
        else:
            e += [boite([1.8, 3.4, 6], [3, 6, 8], "fer"), boite([13, 3.4, 6], [14.2, 6, 8], "fer"),
                  boite([1.8, 2.6, 6], [14.2, 3.4, 8], "fer")]
            if pose == "sol":
                e += cylindre(8, 7, 1.0, 1, 2.6, "fer", n=4)
                e += cylindre(8, 7, 4.2, 0, 1, "rouille", n=8)
            else:
                e += [boite([7, 2.6, 8], [9, 3.4, 16], "fer"), boite([5.5, 0.5, 15], [10.5, 5.5, 16], "rouille")]
        modele(f"projecteur_{pose}", {"fer": "fer_noir", "rouille": "fer_rouille", "lentille": "lentille", "faisceau": "faisceau",
                                      "faisceau_v": "faisceau_v", "particle": "fer_noir"}, e, rendu="minecraft:translucent",
               affichage=AFF if pose == "sol" else None)


def modeles_cristaux():
    """Cristaux : grappes de prismes inclinés, faces lumineuses (petit, moyen, grand ; au sol ou au plafond)."""
    def grappe(taille, plafond, tex):
        h = {"petit": 7, "moyen": 12, "grand": 16}[taille]
        e = []
        r = np.random.default_rng({"petit": 1, "moyen": 2, "grand": 3}[taille])
        prismes = [(8, 8, 2.2, h, 0, "x")] + [(8 + dx, 8 + dz, 1.3 + r.random() * 0.8, h * (0.45 + 0.4 * r.random()),
                                               ang, ax) for (dx, dz, ang, ax) in
                                              ((-3, 1, 22.5, "z"), (3, -1, -22.5, "z"), (1, 3, 22.5, "x"), (-1, -3, -22.5, "x"))]
        if taille == "petit":
            prismes = prismes[:3]
        for (cx, cz, rr, hh, ang, ax) in prismes:
            rot = {"origin": [cx, 0, cz], "axis": ax, "angle": ang} if ang else {"origin": [cx, 0, cz], "axis": "y", "angle": 45}
            e.append(boite([cx - rr, 0, cz - rr], [cx + rr, hh, cz + rr], tex, "nsewu", lum=lum(13), rot=rot))
            e.append(boite([cx - rr * 0.55, hh, cz - rr * 0.55], [cx + rr * 0.55, hh + rr * 1.2, cz + rr * 0.55], tex, "nsewu",
                           lum=15, rot=rot))
        if plafond:
            for el in e:
                el["from"][1], el["to"][1] = 16 - el["to"][1], 16 - el["from"][1]
                el["rotation"]["origin"][1] = 16 - el["rotation"]["origin"][1]
                if el["rotation"]["axis"] != "y":
                    el["rotation"]["angle"] = -el["rotation"]["angle"]
                f = el["faces"]
                if "up" in f:
                    f["down"] = f.pop("up")
        return e
    for couleur in ("bleu", "ambre"):
        for taille in ("petit", "moyen", "grand"):
            for pose in ("sol", "plafond"):
                modele(f"cristal_{couleur}_{taille}_{pose}", {"c": f"cristal_{couleur}", "particle": f"cristal_{couleur}"},
                       grappe(taille, pose == "plafond", "c"), rendu="minecraft:translucent", ao=False,
                       affichage=AFF if pose == "sol" else None)


def modeles_eau():
    """Surface d'eau : une nappe juste au-dessus du niveau de l'eau (14,5 px), vue de dessus et de dessous ; modèle vide
    des blocs invisibles ; mousse pendante."""
    e = [{"from": [0, 14.5, 0], "to": [16, 14.5, 16], "shade": False,
          "faces": {"up": face("t", [0, 0, 16, 16], lum(7)), "down": face("t", [0, 0, 16, 16], lum(7))}}]
    modele("eau_surface", {"t": "eau_surface", "particle": "eau_surface"}, e, rendu="minecraft:translucent", ao=False)
    modele("vide", {"particle": "minecraft:block/glass"}, [])
    for k in range(2):
        modele(f"mousse_pendante_{k}", {"m": f"mousse_{k}", "particle": f"mousse_{k}"},
               plan_croise(8, 8, 16, 0, 16, "m"), rendu="minecraft:cutout",
               ao=False)


def modeles_roche():
    for nom in ("roche_ocre", "roche_rouge", "roche_sombre"):
        modele(nom, {"all": nom}, None, parent="minecraft:block/cube_all")
        t = {"bottom": nom, "top": nom, "side": nom}
        for suf, par in (("escalier", "stairs"), ("escalier_interieur", "inner_stairs"), ("escalier_exterieur", "outer_stairs")):
            modele(f"{nom}_{suf}", t, None, parent=f"minecraft:block/{par}")

    def rocher(taille):
        r = np.random.default_rng({"petit": 7, "moyen": 8, "grand": 9}[taille])
        n, s = {"petit": (4, 0.45), "moyen": (6, 0.8), "grand": (8, 1.15)}[taille]
        e = []
        for i in range(n):
            w = (5 + r.random() * 6) * s
            h = (3 + r.random() * 5) * s
            d = (5 + r.random() * 6) * s
            cx, cz = 8 + (r.random() - 0.5) * 8 * s, 8 + (r.random() - 0.5) * 8 * s
            y0 = 0 if i < n // 2 else r.random() * h * 0.6
            ax = ["y", "x", "z"][i % 3]
            ang = [22.5, -22.5, 45, -45][i % 4]
            e.append(boite([cx - w / 2, y0, cz - d / 2], [cx + w / 2, y0 + h, cz + d / 2], "r",
                           rot={"origin": [cx, y0, cz], "axis": ax, "angle": ang}))
        return e
    for taille in ("petit", "moyen", "grand"):
        modele(f"rocher_{taille}", {"r": "concretion", "particle": "concretion"}, rocher(taille), affichage=AFF)
    def pointe(taille, plafond):
        L = {"petit": 8, "moyen": 16, "grand": 30}[taille]
        r0 = {"petit": 2.6, "moyen": 3.8, "grand": 5.4}[taille]
        n = {"petit": 3, "moyen": 5, "grand": 7}[taille]
        e = []
        for i in range(n):
            t0, t1 = i / n, (i + 1) / n
            rr = max(0.5, r0 * (1 - t0) ** 1.15)
            e += cylindre(8, 8, rr, L * t0, L * t1 + 0.3, "r", n=4, faces="nsewu" if i == 0 else "nsew")
        if plafond:
            for el in e:
                el["from"][1], el["to"][1] = 16 - el["to"][1], 16 - el["from"][1]
                if "rotation" in el:
                    el["rotation"]["origin"][1] = 16 - el["rotation"]["origin"][1]
                f = el["faces"]
                if "up" in f:
                    f["down"] = f.pop("up")
        return e
    for taille in ("petit", "moyen", "grand"):
        for pose in ("sol", "plafond"):
            modele(f"stalactite_{taille}_{pose}", {"r": "concretion", "particle": "concretion"}, pointe(taille, pose == "plafond"),
                   affichage=AFF if pose == "sol" else None)


# ============================================================================================== états de bloc, objets, traductions
ROT = {"north": 0, "east": 90, "south": 180, "west": 270}
ROCHES = ("roche_ocre", "roche_rouge", "roche_sombre")


def etats():
    E = dossier("blockstates")

    def m(n, y=0, **k):
        d = {"model": f"{NS}:block/{n}"}
        if y:
            d["y"] = y
        d.update(k)
        return d
    for n in ROCHES:
        ecrire_json(os.path.join(E, n + ".json"), {"variants": {"": m(n)}})
    for n in ("ambiance", "grande_cascade"):                                    # dessinés par leur entité ou invisibles
        ecrire_json(os.path.join(E, n + ".json"), {"variants": {"": m("vide")}})
    for bloc, modl in (("lanterne_fer", "lanterne_fer"), ("projecteur", "projecteur")):
        ecrire_json(os.path.join(E, bloc + ".json"), {"variants": {
            f"facing={f},pose={p}": m(f"{modl}_{p}", r) for f, r in ROT.items() for p in ("sol", "mur", "plafond")}})
    ecrire_json(os.path.join(E, "lampe_suspendue.json"), {"variants": {
        f"taille={t}": m(f"lampe_suspendue_{t}") for t in ("petit", "moyen", "grand")}})
    ecrire_json(os.path.join(E, "guirlande.json"), {"variants": {"axis=x": m("guirlande"), "axis=z": m("guirlande", 90)}})
    for c in ("bleu", "ambre"):
        ecrire_json(os.path.join(E, f"cristal_{c}.json"), {"variants": {
            f"pose={p},taille={t}": m(f"cristal_{c}_{t}_{p}") for t in ("petit", "moyen", "grand") for p in ("sol", "plafond")}})
    ecrire_json(os.path.join(E, "stalactite.json"), {"variants": {
        f"pose={p},taille={t}": m(f"stalactite_{t}_{p}") for t in ("petit", "moyen", "grand") for p in ("sol", "plafond")}})
    ecrire_json(os.path.join(E, "rocher.json"), {"variants": {f"taille={t}": m(f"rocher_{t}") for t in ("petit", "moyen", "grand")}})
    # surface d'eau : jamais tournée au hasard (aucun raccord) ; elle dérive vers « facing » (le modèle dérive vers le sud)
    ecrire_json(os.path.join(E, "eau_decor.json"), {"variants": {
        f"facing={f}": m("eau_surface", y) for f, y in (("south", 0), ("west", 90), ("north", 180), ("east", 270))}})
    ecrire_json(os.path.join(E, "mousse_pendante.json"), {"variants": {"": [m(f"mousse_pendante_{k}", y) for k in range(2)
                                                                            for y in (0, 90)]}})
    ecrire_json(os.path.join(E, "tache_sang.json"), {"variants": {
        f"facing={f},forme={n},pose={p}": m(f"tache_sang_{n}_{p}", r) for f, r in ROT.items()
        for n, _, _ in FORMES_SANG for p in ("sol", "mur")}})
    for nom in ROCHES:
        ecrire_json(os.path.join(E, f"{nom}_escalier.json"), etats_escalier(nom))


# Minecraft 1.20.1 du lanceur CurseForge (dossier Install : outils/chemins.py du dépôt, réglable par PC)
import sys  # noqa: E402
sys.path.insert(0, os.path.join(HERE, "..", "..", "..", "outils"))
import chemins  # noqa: E402
VANILLA = str(chemins.LANCEUR / "versions" / "1.20.1" / "1.20.1.jar")


def depuis_vanilla(fichier, remplacements):
    """Recopie un état de bloc vanilla en remplaçant ses modèles par les nôtres."""
    import zipfile
    txt = zipfile.ZipFile(VANILLA).read(f"assets/minecraft/blockstates/{fichier}.json").decode("utf-8")
    for a, b in remplacements:
        txt = txt.replace(f'"{a}"', f'"{b}"')
    return json.loads(txt)


def etats_escalier(nom):
    p = f"{NS}:block/{nom}"
    return depuis_vanilla("stone_brick_stairs", [("minecraft:block/stone_brick_stairs_inner", p + "_escalier_interieur"),
                                                 ("minecraft:block/stone_brick_stairs_outer", p + "_escalier_exterieur"),
                                                 ("minecraft:block/stone_brick_stairs", p + "_escalier")])


def objets():
    I = dossier("models", "item")

    def obj(nom, parent):
        ecrire_json(os.path.join(I, nom + ".json"), {"parent": parent})
    blocs = {"lanterne_fer": "lanterne_fer_sol", "lampe_suspendue": "lampe_suspendue_moyen", "guirlande": "guirlande",
             "projecteur": "projecteur_sol", "cristal_bleu": "cristal_bleu_moyen_sol", "cristal_ambre": "cristal_ambre_moyen_sol",
             "rocher": "rocher_moyen", "stalactite": "stalactite_moyen_sol"}
    for nom in ROCHES:
        blocs[nom] = nom
        blocs[f"{nom}_escalier"] = f"{nom}_escalier"
    for k, v in blocs.items():
        obj(k, f"{NS}:block/{v}")
    for nom, layer in (("grande_cascade", "item/grande_cascade"), ("ambiance", "item/ambiance"),
                       ("eau_decor", "item/eau_decor"), ("mousse_pendante", "block/mousse_0"),
                       ("tache_sang", "block/tache_sang_eclaboussures")):
        ecrire_json(os.path.join(I, nom + ".json"), {"parent": "minecraft:item/generated", "textures": {"layer0": f"{NS}:{layer}"}})


NOMS = {
    "lanterne_fer": ("Lanterne en fer", "Iron lantern"), "lampe_suspendue": ("Lampe suspendue", "Hanging lamp"),
    "guirlande": ("Guirlande d'ampoules", "String lights"), "projecteur": ("Projecteur", "Floodlight"),
    "cristal_bleu": ("Cristal bleu", "Blue crystal"), "cristal_ambre": ("Cristal ambré", "Amber crystal"),
    "grande_cascade": ("Grande cascade", "Great waterfall"),
    "eau_decor": ("Surface d'eau animée", "Animated water surface"),
    "ambiance": ("Ambiance (brume, lucioles...)", "Ambience (mist, fireflies...)"),
    "mousse_pendante": ("Mousse pendante", "Hanging moss"),
    "tache_sang": ("Tache de sang", "Blood stain"),
    "roche_ocre": ("Roche ocre", "Ochre rock"), "roche_rouge": ("Roche rouge", "Red rock"), "roche_sombre": ("Roche sombre", "Dark rock"),
    "roche_ocre_escalier": ("Escalier de roche ocre", "Ochre rock stairs"),
    "roche_rouge_escalier": ("Escalier de roche rouge", "Red rock stairs"),
    "roche_sombre_escalier": ("Escalier de roche sombre", "Dark rock stairs"),
    "rocher": ("Rocher", "Boulder"), "stalactite": ("Stalactite", "Stalactite"),
}


import blocs_maison as BM  # noqa: E402

VARIANTE_NOM = {"escalier": ("Escalier", "stairs"), "dalle": ("Dalle", "slab"), "mur": ("Muret", "wall")}
for _n, _d in BM.BLOCS.items():
    NOMS[_n] = (_d["fr"], _d["en"])
    for _v in _d["variantes"]:
        NOMS[f"{_n}_{_v}"] = (f"{VARIANTE_NOM[_v][0]} : {_d['fr'].lower()}", f"{_d['en']} {VARIANTE_NOM[_v][1]}")


def assets_maison():
    """Blocs maison du désert et de l'arctique (1.5.0) : textures (blocs_maison.py), modèles, états, objets."""
    E = dossier("blockstates")
    I = dossier("models", "item")
    for nom, (rgb, alpha) in BM.textures().items():
        png(nom, rgb, alpha)

    def m(n, **k):
        d = {"model": f"{NS}:block/{n}"}
        d.update(k)
        return d
    for nom, d in BM.BLOCS.items():
        forme = d["forme"]
        rendu = {"decoupe": "minecraft:cutout", "translucide": "minecraft:translucent"}.get(forme)
        t = {"bottom": nom, "top": nom, "side": nom}
        if forme == "pilier":
            tc = {"end": nom + "_bout", "side": nom}
            modele(nom, tc, None, parent="minecraft:block/cube_column")
            modele(nom + "_horizontal", tc, None, parent="minecraft:block/cube_column_horizontal")
            ecrire_json(os.path.join(E, nom + ".json"), {"variants": {
                "axis=y": m(nom), "axis=x": m(nom + "_horizontal", x=90, y=90), "axis=z": m(nom + "_horizontal", x=90)}})
        elif forme == "tapis":
            modele(nom, {"wool": nom, "particle": nom}, None, parent="minecraft:block/carpet")
            ecrire_json(os.path.join(E, nom + ".json"), {"variants": {"": m(nom)}})
        else:
            modele(nom, {"all": nom}, None, rendu=rendu, parent="minecraft:block/cube_all")
            ecrire_json(os.path.join(E, nom + ".json"), {"variants": {"": m(nom)}})
        ecrire_json(os.path.join(I, nom + ".json"), {"parent": f"{NS}:block/{nom}"})
        for v in d["variantes"]:
            if v == "escalier":
                for suf, par in (("escalier", "stairs"), ("escalier_interieur", "inner_stairs"), ("escalier_exterieur", "outer_stairs")):
                    modele(f"{nom}_{suf}", t, None, rendu=rendu, parent=f"minecraft:block/{par}")
                ecrire_json(os.path.join(E, f"{nom}_escalier.json"), etats_escalier(nom))
                ecrire_json(os.path.join(I, f"{nom}_escalier.json"), {"parent": f"{NS}:block/{nom}_escalier"})
            elif v == "dalle":
                modele(f"{nom}_dalle", t, None, rendu=rendu, parent="minecraft:block/slab")
                modele(f"{nom}_dalle_haut", t, None, rendu=rendu, parent="minecraft:block/slab_top")
                ecrire_json(os.path.join(E, f"{nom}_dalle.json"), {"variants": {
                    "type=bottom": m(f"{nom}_dalle"), "type=top": m(f"{nom}_dalle_haut"), "type=double": m(nom)}})
                ecrire_json(os.path.join(I, f"{nom}_dalle.json"), {"parent": f"{NS}:block/{nom}_dalle"})
            elif v == "mur":
                for suf, par in (("mur_poteau", "template_wall_post"), ("mur_cote", "template_wall_side"),
                                 ("mur_cote_haut", "template_wall_side_tall"), ("mur_inventaire", "wall_inventory")):
                    modele(f"{nom}_{suf}", {"wall": nom}, None, parent=f"minecraft:block/{par}")
                p_ = f"{NS}:block/{nom}"
                ecrire_json(os.path.join(E, f"{nom}_mur.json"), depuis_vanilla("cobblestone_wall", [
                    ("minecraft:block/cobblestone_wall_post", p_ + "_mur_poteau"),
                    ("minecraft:block/cobblestone_wall_side_tall", p_ + "_mur_cote_haut"),
                    ("minecraft:block/cobblestone_wall_side", p_ + "_mur_cote")]))
                ecrire_json(os.path.join(I, f"{nom}_mur.json"), {"parent": f"{NS}:block/{nom}_mur_inventaire"})


def traductions():
    L = dossier("lang")
    fr = {f"block.{NS}.{k}": v[0] for k, v in NOMS.items()}
    en = {f"block.{NS}.{k}": v[1] for k, v in NOMS.items()}
    fr["onglet.heraliz_decors"] = "HeraliZ · Décors"
    en["onglet.heraliz_decors"] = "HeraliZ · Decors"
    fr["msg.heraliz_decors.zone.gaz"] = "§a⚠ Zone de gaz toxique : masque à gaz ou combinaison Hazmat"
    en["msg.heraliz_decors.zone.gaz"] = "§a⚠ Toxic gas zone: gas mask or Hazmat suit"
    fr["msg.heraliz_decors.zone.radiation"] = "§e☢ Zone irradiée : combinaison Hazmat obligatoire"
    en["msg.heraliz_decors.zone.radiation"] = "§e☢ Irradiated zone: Hazmat suit required"
    fr["msg.heraliz_decors.zone.froid"] = "§b❄ Froid extrême : combinaison arctique conseillée"
    en["msg.heraliz_decors.zone.froid"] = "§b❄ Extreme cold: arctic suit advised"
    ecrire_json(os.path.join(L, "fr_fr.json"), fr)
    ecrire_json(os.path.join(L, "en_us.json"), en)


def verifier():
    """Contrôle : chaque modèle référencé existe, chaque texture de modèle existe, chaque bloc a état, objet et nom."""
    err = []
    mods = {f[:-5] for f in os.listdir(os.path.join(A, "models", "block"))}
    for f in os.listdir(os.path.join(A, "blockstates")):
        txt = open(os.path.join(A, "blockstates", f), encoding="utf-8").read()
        for ref in set(__import__("re").findall(r'"heraliz_decors:block/([a-z0-9_]+)"', txt)):
            if ref not in mods:
                err.append(f"état {f} : modèle {ref} absent")
    for f in mods:
        m = json.load(open(os.path.join(A, "models", "block", f + ".json"), encoding="utf-8"))
        for k, t in m.get("textures", {}).items():
            if t.startswith(NS + ":") and not os.path.exists(os.path.join(A, "textures", t.split(":")[1] + ".png")):
                err.append(f"modèle {f} : texture {t} absente")
        for el in m.get("elements", []):
            for k in range(3):
                if not (-16 <= el["from"][k] <= 32 and -16 <= el["to"][k] <= 32):
                    err.append(f"modèle {f} : élément hors limites {el['from']} {el['to']}")
            r = el.get("rotation")
            if r and r["angle"] not in (-45, -22.5, 0, 22.5, 45):
                err.append(f"modèle {f} : angle {r['angle']}")
            for fn, fd in el["faces"].items():
                if fd["texture"][1:] not in m.get("textures", {}):
                    err.append(f"modèle {f} : face {fn} variable {fd['texture']} inconnue")
    for nom in NOMS:
        if not os.path.exists(os.path.join(A, "models", "item", nom + ".json")):
            err.append(f"objet {nom} absent")
        if not os.path.exists(os.path.join(A, "blockstates", nom + ".json")):
            err.append(f"état {nom} absent")
    return err


def main():
    if os.path.isdir(A):
        shutil.rmtree(A)
    tex_roches()
    tex_metaux()
    tex_lumieres()
    tex_eau()
    tex_mousse()
    tex_particules()
    tex_icones()
    tex_sang()
    modeles_lanternes()
    modeles_lampes()
    modeles_guirlande()
    modeles_projecteur()
    modeles_cristaux()
    modeles_eau()
    modeles_roche()
    modeles_sang()
    assets_maison()
    import grillage as GRL                                         # grillage militaire (1.7.0) : outils/grillage.py
    GRL.ecrire(A)
    for _n, (_fr, _en) in GRL.NOMS.items():
        NOMS[_n] = (_fr, _en)
    import garde_corps as GC                                       # garde-corps à plusieurs côtés (1.10.0) : outils/garde_corps.py
    GC.ecrire(A)
    for _n, (_fr, _en) in GC.NOMS.items():
        NOMS[_n] = (_fr, _en)
    import prison as PR                                            # mobilier de prison (1.11.0) : outils/prison.py
    PR.installer()
    for _n, (_fr, _en) in PR.NOMS.items():
        NOMS[_n] = (_fr, _en)
    etats()
    objets()
    traductions()
    n = sum(len(f) for _, _, f in os.walk(A))
    err = verifier()
    print("ressources générées :", n, "fichiers dans", os.path.abspath(A))
    print("contrôle :", len(err), "erreur(s)")
    for e in err[:40]:
        print("  ", e)


if __name__ == "__main__":
    main()
