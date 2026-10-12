"""Blocs maison des villes du désert (Mirage, médina) et de la base militaire arctique : textures HD 32 px procédurales,
tuilables dans les deux sens. Importé par gen_assets.py (ressources du mod) et par les aperçus hors jeu.

Chaque entrée de BLOCS : nom -> dict(theme, forme, fr, en, variantes)
  forme : "cube" (plein), "decoupe" (trous transparents), "translucide" (vitre teintée), "pilier" (côté + bout),
          "tapis" (couche de 1/16 posée au sol)
  variantes : sous-ensemble de ("escalier", "dalle", "mur")
TEXTURES[nom] -> (rgb, alpha ou None) ; pour un pilier, deux textures nom et nom + "_bout".
"""
import math

import numpy as np

T = 32


# ============================================================================================== outils
def bruit(beta=2.0, graine=0, m=None):
    """Bruit tuilable 1/f^beta, normalisé 0..1."""
    r = np.random.default_rng(graine)
    h, w = (T, T) if m is None else m
    b = r.normal(size=(h, w))
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.fftfreq(w)[None, :]
    f = np.sqrt(fx ** 2 + fy ** 2)
    f[0, 0] = 1
    s = np.real(np.fft.ifft2(np.fft.fft2(b) / f ** (beta / 2)))
    return (s - s.min()) / (np.ptp(s) + 1e-9)


def bruit_aniso(sx=1.0, sy=1.0, graine=0, beta=2.0):
    """Bruit tuilable étiré : sx grand -> traits verticaux fins ; sy grand -> traits horizontaux."""
    r = np.random.default_rng(graine)
    b = r.normal(size=(T, T))
    fy = np.fft.fftfreq(T)[:, None] * sy
    fx = np.fft.fftfreq(T)[None, :] * sx
    f = np.sqrt(fx ** 2 + fy ** 2)
    f[0, 0] = 1
    s = np.real(np.fft.ifft2(np.fft.fft2(b) / f ** (beta / 2)))
    return (s - s.min()) / (np.ptp(s) + 1e-9)


def diagonale(a):
    """Cisaille d'une case par ligne : un bruit étiré horizontalement devient des traits à 45° (reste tuilable)."""
    return np.stack([np.roll(a[y], y) for y in range(T)])


def rampe(v, couleurs):
    out = np.zeros(v.shape + (3,))
    ts = [c[0] for c in couleurs]
    for k in range(3):
        out[..., k] = np.interp(v, ts, [c[1][k] for c in couleurs])
    return out


def norm(v):
    return (v - v.min()) / (np.ptp(v) + 1e-9)


def relief(h, fort=0.9):
    """Éclairage d'une carte de hauteur (lumière en haut à gauche) -> facteur multiplicatif ~1."""
    gx = np.roll(h, 1, axis=1) - h
    gy = np.roll(h, 1, axis=0) - h
    return 1 + fort * (gx + gy) * 0.5


def grille_xy():
    y, x = np.mgrid[0:T, 0:T]
    return x.astype(float), y.astype(float)


def voronoi(n, graine, jitter=1.0, rangs=None):
    """Cellules de Voronoï tuilables : n germes. -> (F1, F2, indice de cellule)."""
    r = np.random.default_rng(graine)
    if rangs is None:
        k = int(math.sqrt(n))
        pts = [((i + 0.5 + (r.random() - 0.5) * jitter) * T / k, (j + 0.5 + (r.random() - 0.5) * jitter) * T / k)
               for i in range(k) for j in range(k)]
    else:
        pts = rangs
    x, y = grille_xy()
    D = []
    for (px, py) in pts:
        dx = np.abs(x - px)
        dy = np.abs(y - py)
        dx = np.minimum(dx, T - dx)
        dy = np.minimum(dy, T - dy)
        D.append(np.hypot(dx, dy))
    D = np.stack(D)
    o = np.argsort(D, axis=0)
    F1 = np.take_along_axis(D, o[:1], 0)[0]
    F2 = np.take_along_axis(D, o[1:2], 0)[0]
    return F1, F2, o[0]


def fissures(graine, seuil=0.012, part=0.45):
    """Fissures fines : lignes de niveau d'un bruit, gardées par morceaux."""
    f = bruit(2.4, graine)
    garde = bruit(2.0, graine + 1) > 1 - part
    return (np.abs(f - 0.5) < seuil) & garde


def tissage(graine, fort=0.06):
    """Grain de toile : trame et chaîne alternées, fils un peu irréguliers."""
    x, y = grille_xy()
    t = np.where(((x + y) % 2) == 0, 1 + fort, 1 - fort)
    return t * (0.96 + 0.08 * bruit(0.6, graine))


# ============================================================================================== DÉSERT
SABLE = [(0, (182, 152, 108)), (0.35, (204, 176, 132)), (0.7, (222, 198, 154)), (1, (236, 216, 176))]
BLANC = [(0, (206, 200, 186)), (0.35, (226, 221, 208)), (0.7, (238, 234, 223)), (1, (247, 244, 236))]
OCRE = [(0, (160, 92, 58)), (0.35, (184, 112, 72)), (0.7, (202, 132, 88)), (1, (218, 154, 108))]
BRIQUE = [(0, (158, 120, 80)), (0.4, (184, 146, 102)), (0.75, (204, 168, 122)), (1, (220, 188, 144))]


def enduit(pal, graine):
    """Enduit à la chaux lissé à la truelle : grandes taches douces, traits de lissage en biais, grain fin."""
    taches = bruit(2.8, graine)
    truelle = diagonale(bruit_aniso(sx=0.35, sy=2.8, graine=graine + 1))
    grain = bruit(0.8, graine + 2)
    v = 0.5 * taches + 0.22 * truelle + 0.28 * grain
    return rampe(0.2 + 0.6 * norm(v), pal)


def briques_crues(graine, usure=0.0):
    """Briques de terre crue (adobe) : 2 briques par rang, 4 rangs, joints de terre en creux, arêtes arrondies,
    brins de paille ; usure -> arêtes rongées, joints vidés, sable dans les creux."""
    r = np.random.default_rng(graine)
    x, y = grille_xy()
    rang = (y // 8).astype(int)
    xd = (x + np.where(rang % 2 == 1, 8, 0)) % T
    bx = (xd // 16).astype(int)
    u = xd % 16
    v = y % 8
    ton = np.array([[r.random() for _ in range(2)] for _ in range(4)])[rang, bx]
    d_bord = np.minimum(np.minimum(u, 15 - u), np.minimum(v, 7 - v)).astype(float)
    joint = (u == 0) | (v == 0)
    if usure:                                                            # arêtes rongées par le vent
        ronge = (bruit(1.4, graine + 3) > 1 - 0.35 * usure) & (d_bord <= 1)
        joint = joint | ronge
    h = np.clip(d_bord / 1.6, 0, 1)
    h = np.where(joint, 0, h)
    base = rampe(np.clip(0.3 + 0.3 * ton + 0.25 * bruit(1.0, graine + 4) + 0.1 * bruit(2.4, graine + 8), 0, 1), BRIQUE)
    base *= relief(h, 0.28)[..., None]
    base[joint] = rampe(0.2 + 0.15 * bruit(1.0, graine + 5), BRIQUE)[joint] * 0.84
    paille = (bruit_aniso(sx=3.0, sy=0.3, graine=graine + 6) > 0.9) & ~joint
    base[paille] = base[paille] * 0.8 + np.array([226, 200, 132]) * 0.2
    if usure:
        trous = (bruit(1.2, graine + 7) > 0.8) & ~joint
        base[trous] *= 0.86
    return base


def tex_desert():
    TX = {}
    # --- enduits : sable, chaux blanche, ocre rouge de Marrakech ; versions usées (plaques tombées sur l'adobe)
    for nom, pal, g in (("enduit_sable", SABLE, 10), ("enduit_blanc", BLANC, 20), ("enduit_ocre", OCRE, 30)):
        rgb = enduit(pal, g)
        TX[nom] = (rgb, None)
        use = rgb.copy()
        dessous = briques_crues(g + 5, usure=0.6)
        m = bruit(3.4, g + 8)
        plaque = m > 0.74
        bord = (m > 0.71) & ~plaque
        use[plaque] = dessous[plaque] * 0.94
        ombre = plaque & ~np.roll(plaque, 1, axis=0)                      # lèvre d'enduit au-dessus du trou
        use[ombre] *= 0.72
        use[bord] *= 0.93
        fi = fissures(g + 9)
        use[fi & ~plaque] *= 0.74
        sale = bruit_aniso(sx=2.2, sy=0.4, graine=g + 10)                    # coulures verticales
        use *= (1 - 0.08 * np.clip((sale - 0.55) * 3, 0, 1))[..., None]
        TX[nom + "_use"] = (use, None)
    TX["brique_crue"] = (briques_crues(40), None)
    TX["brique_crue_usee"] = (briques_crues(41, usure=1.0), None)
    # --- pisé : terre damée par levées horizontales (banches), cailloux, trous de boulin
    x, y = grille_xy()
    ond = (bruit(2.6, 50) - 0.5) * 2.0
    couche = np.sin(2 * np.pi * (y + ond) / 5.3)
    v = 0.45 + 0.18 * couche + 0.25 * (bruit(1.0, 51) - 0.5) + 0.2 * (bruit(2.4, 52) - 0.5)
    rgb = rampe(np.clip(v, 0, 1), [(0, (150, 108, 72)), (0.5, (186, 140, 96)), (1, (214, 172, 124))])
    levee = (np.abs(y - 15.5) < 0.6)
    rgb[levee] *= 0.8
    rgb[np.roll(levee, 1, axis=0)] *= 1.07
    cail = np.random.default_rng(53).random((T, T)) < 0.03
    rgb[cail] = rgb[cail] * 0.7 + np.array([120, 112, 104]) * 0.3
    rgb[21:23, 9:12] *= 0.55
    rgb[20, 9:12] *= 1.08
    TX["pise"] = (rgb, None)
    # --- pierre de taille (calcaire doré) : assises de 16, joints fins, ciselure en biais, biseau
    pal_p = [(0, (172, 146, 102)), (0.4, (198, 174, 128)), (0.75, (216, 194, 150)), (1, (230, 212, 170))]
    rang = (y // 16).astype(int)
    xd = (x + np.where(rang == 1, 10, 0)) % T
    coupe = np.where(rang == 0, np.where(xd < 18, 0, 1), np.where(xd < 14, 0, 1))
    deb = np.where(rang == 0, np.where(coupe == 0, 0, 18), np.where(coupe == 0, 0, 14))
    fin = np.where(rang == 0, np.where(coupe == 0, 18, 32), np.where(coupe == 0, 14, 32))
    u = xd - deb
    lg = fin - deb
    vv = y % 16
    ton = np.random.default_rng(60).random((2, 2))[rang, coupe]
    cis = diagonale(bruit_aniso(sx=0.3, sy=4.0, graine=61))
    rgb = rampe(np.clip(0.3 + 0.25 * ton + 0.25 * cis + 0.2 * bruit(1.4, 62), 0, 1), pal_p)
    joint = (u == 0) | (vv == 0)
    rgb[joint] = np.array([150, 128, 92])
    rgb[(u == 1) | (vv == 1)] *= 1.1
    rgb[(u == lg - 1) | (vv == 15)] *= 0.86
    rgb[fissures(63, part=0.25) & ~joint] *= 0.8
    TX["pierre_sable"] = (rgb, None)
    pierre = rgb
    # --- frise sculptée : bandeau de losanges entrelacés en relief, entre deux listels
    h = np.zeros((T, T))
    xm = x % 8
    lo = np.abs(xm - 3.5) + np.abs(y - 15.5) * 0.8
    h[(y >= 7) & (y <= 24)] = np.clip(1 - np.abs(lo - 3.2)[(y >= 7) & (y <= 24)] / 1.4, 0, 1)
    h[(y >= 2) & (y <= 4)] = 0.8
    h[(y >= 27) & (y <= 29)] = 0.8
    rgb = rampe(np.clip(0.35 + 0.2 * cis + 0.2 * bruit(1.4, 64) + 0.25 * h, 0, 1), pal_p)
    rgb *= relief(h * 2.0, 0.55)[..., None]
    rgb[(y == 5) | (y == 26)] *= 0.78
    rgb[(y == 0)] = np.array([150, 128, 92])
    TX["pierre_sable_frise"] = (rgb, None)
    # --- pilier cannelé (côté) et son bout (assise)
    fl = np.cos(2 * np.pi * (x + 0.5) / 8)
    rgb = rampe(np.clip(0.4 + 0.18 * fl + 0.18 * bruit_aniso(sx=3, sy=0.3, graine=65) + 0.12 * bruit(1.2, 66), 0, 1), pal_p)
    rgb *= (1 + 0.16 * np.sin(2 * np.pi * (x + 0.5) / 8))[..., None]
    rgb[y == 0] *= 0.82
    TX["pilier_sable"] = (rgb, None)
    TX["pilier_sable_bout"] = (pierre, None)
    # --- dallage de grès irrégulier (cours, terrasses, trottoirs)
    F1, F2, idx = voronoi(9, 70, jitter=0.9)
    ton = np.random.default_rng(71).random(9)[idx]
    joint = (F2 - F1) < 1.1
    hh = np.clip((F2 - F1) / 3.0, 0, 1)
    rgb = rampe(np.clip(0.25 + 0.3 * ton + 0.3 * bruit(1.2, 72) + 0.1 * cis, 0, 1), pal_p)
    rgb *= relief(hh, 0.35)[..., None]
    rgb[joint] = rampe(0.2 + 0.2 * bruit(1.0, 73), SABLE)[joint] * 0.8
    TX["dallage_sable"] = (rgb, None)
    # --- carreaux de terre cuite 8 x 8, joints crème, tons variés, coins ébréchés
    r = np.random.default_rng(80)
    i_, j_ = (x // 8).astype(int), (y // 8).astype(int)
    ton = r.random((4, 4))[j_, i_]
    u, v = x % 8, y % 8
    pal_t = [(0, (138, 62, 38)), (0.4, (166, 82, 50)), (0.75, (188, 104, 66)), (1, (204, 126, 86))]
    rgb = rampe(np.clip(0.2 + 0.5 * ton + 0.2 * bruit(1.0, 81), 0, 1), pal_t)
    joint = (u == 0) | (v == 0)
    rgb[(u == 1) | (v == 1)] *= 1.07
    rgb[(u == 7) | (v == 7)] *= 0.9
    rgb[joint] = np.array([206, 190, 160])
    eb = r.random((4, 4))[j_, i_] < 0.25
    rgb[eb & (u <= 2) & (v <= 2) & ~joint] = rgb[eb & (u <= 2) & (v <= 2) & ~joint] * 0.5 + np.array([200, 160, 120]) * 0.5
    TX["carreaux_terre_cuite"] = (rgb, None)
    # --- pavés de la médina : galets arrondis serrés dans un mortier de sable
    F1, F2, idx = voronoi(25, 90, jitter=0.8)
    ton = np.random.default_rng(91).random(25)[idx]
    joint = (F2 - F1) < 1.0
    dome = np.clip((F2 - F1) / 3.5, 0, 1) ** 0.6
    pal_g = [(0, (128, 112, 92)), (0.4, (156, 138, 112)), (0.75, (180, 162, 132)), (1, (200, 184, 152))]
    rgb = rampe(np.clip(0.2 + 0.45 * ton + 0.2 * bruit(1.0, 92), 0, 1), pal_g)
    rgb *= relief(dome * 1.4, 0.5)[..., None]
    rgb[joint] = rampe(0.3 + 0.3 * bruit(1.0, 93), SABLE)[joint] * 0.85
    TX["paves_medina"] = (rgb, None)
    # --- sable tassé (ruelles, cours) : terre claire compacte, ornières douces, gravillons
    v = 0.5 * bruit(2.4, 100) + 0.25 * bruit_aniso(sx=0.3, sy=2.5, graine=101) + 0.25 * bruit(0.9, 102)
    rgb = rampe(0.15 + 0.7 * norm(v), [(0, (176, 148, 106)), (0.5, (200, 174, 130)), (1, (220, 198, 156))])
    g_ = np.random.default_rng(103).random((T, T)) < 0.04
    rgb[g_] *= 0.82
    TX["sable_tasse"] = (rgb, None)
    # --- zelliges : étoiles à 8 branches (2 x 2 par bloc), émail un peu irrégulier, joints gris
    for nom, etoile, fond, accent, g in (("zellige_bleu", (34, 92, 152), (236, 230, 214), (196, 146, 62), 110),
                                         ("zellige_vert", (34, 118, 84), (236, 230, 214), (40, 72, 128), 111),
                                         ("zellige_ocre", (176, 88, 48), (238, 226, 200), (38, 42, 50), 112)):
        uu = (x % 16 + 0.5) / 16 - 0.5
        vv = (y % 16 + 0.5) / 16 - 0.5
        a, b_ = 0.27, 0.38
        dA = np.maximum(np.abs(uu), np.abs(vv)) - a
        dB = np.maximum(np.abs(uu + vv), np.abs(uu - vv)) / math.sqrt(2) * 1.0 - b_ / math.sqrt(2)
        d_et = np.minimum(dA, dB)
        cu = 0.5 - np.abs(uu)
        cv = 0.5 - np.abs(vv)
        d_coin = (cu + cv) - 0.17
        rgb = np.zeros((T, T, 3))
        rgb[:] = fond
        rgb[d_et < 0] = etoile
        rgb[d_coin < 0] = accent
        piece = np.where(d_et < 0, 0, np.where(d_coin < 0, 1, 2)) + 3 * ((x // 16) + 2 * (y // 16)).astype(int)
        emaille = np.random.default_rng(g).random(12)[piece]
        rgb *= (0.9 + 0.16 * emaille)[..., None]
        rgb *= (0.95 + 0.1 * bruit(1.8, g + 1))[..., None]
        joint = (np.abs(d_et) < 0.025) | (np.abs(d_coin) < 0.03)
        rgb[joint] = np.array([118, 112, 104])
        reflet = (bruit(2.2, g + 2) > 0.8) & ~joint
        rgb[reflet] = rgb[reflet] * 0.75 + 255 * 0.25
        TX[nom] = (rgb, None)
    # --- moucharabieh : treillis de bois tourné (barreaux, perles aux croisements), jours transparents
    bois_f = rampe(np.clip(0.3 + 0.4 * bruit_aniso(sx=3, sy=0.3, graine=120) + 0.2 * bruit(1, 121), 0, 1),
                   [(0, (64, 40, 26)), (0.5, (96, 64, 40)), (1, (128, 90, 58))])
    dx_ = np.minimum(x % 8, 8 - x % 8)                                     # distance au barreau le plus proche
    dy_ = np.minimum(y % 8, 8 - y % 8)
    barre_v = np.abs((x % 8) - 0.0) <= 0.0
    xm, ym = (x + 1) % 8, (y + 1) % 8                                      # barreaux sur les colonnes 7 et 0
    bar = (xm <= 1) | (ym <= 1)
    perle = np.hypot(xm - 0.5, ym - 0.5) <= 1.9
    perle |= np.hypot(xm - 8.5, ym - 0.5) <= 1.9
    perle |= np.hypot(xm - 0.5, ym - 8.5) <= 1.9
    perle |= np.hypot(xm - 8.5, ym - 8.5) <= 1.9
    mid = (np.abs(xm - 4.5) + np.abs(ym - 4.5)) <= 1.0                      # petit losange au centre des jours
    plein = bar | perle | mid
    h = np.where(perle, 1.0, np.where(bar, 0.6, np.where(mid, 0.5, 0)))
    rgb = bois_f * relief(h, 0.6)[..., None]
    alpha = np.where(plein, 255, 0)
    TX["moucharabieh"] = (rgb, alpha)
    del barre_v, dx_, dy_
    # --- claustra : panneau d'enduit percé d'étoiles
    rgb = enduit(BLANC, 125)
    uu = (x % 16 + 0.5) / 16 - 0.5
    vv = (y % 16 + 0.5) / 16 - 0.5
    dA = np.maximum(np.abs(uu), np.abs(vv)) - 0.25
    dB = np.maximum(np.abs(uu + vv), np.abs(uu - vv)) / math.sqrt(2) - 0.34 / math.sqrt(2)
    trou = np.minimum(dA, dB) < 0
    bordt = (np.minimum(dA, dB) < 0.05) & ~trou
    rgb[bordt] *= 0.8
    TX["claustra"] = (rgb, np.where(trou, 0, 255))
    # --- bois de palmier : planches fibreuses horizontales, jours sombres
    fib = bruit_aniso(sx=0.25, sy=4.0, graine=130)
    rangp = (y // 8).astype(int)
    ton = np.random.default_rng(131).random(4)[rangp]
    rgb = rampe(np.clip(0.25 + 0.35 * fib + 0.25 * ton + 0.1 * bruit(1, 132), 0, 1),
                [(0, (104, 74, 46)), (0.45, (140, 104, 66)), (0.8, (166, 128, 86)), (1, (186, 150, 104))])
    rgb[y % 8 == 7] *= 0.55
    rgb[y % 8 == 0] *= 1.08
    for k, cx_ in enumerate((5, 21, 13, 27)):                               # clous
        rgb[k * 8 + 3, cx_] = np.array([70, 64, 60])
    TX["bois_palmier"] = (rgb, None)
    # --- tronc de palmier : écailles des anciennes palmes (côté), cœur fibreux (bout)
    rr = (y + np.where((x // 8) % 2 == 1, 4, 0)) % 8
    ec = np.clip(1 - np.abs((x % 8) - 3.5) / 4.5 - rr / 10, 0, 1)
    rgb = rampe(np.clip(0.3 + 0.4 * ec + 0.2 * bruit_aniso(sx=3, sy=0.4, graine=140), 0, 1),
                [(0, (70, 54, 40)), (0.5, (118, 94, 68)), (1, (160, 132, 96))])
    rgb[rr == 0] *= 0.62
    TX["rondin_palmier"] = (rgb, None)
    d = np.hypot(x - 15.5, y - 15.5)
    anneaux = 0.5 + 0.5 * np.sin(d * 1.3 + 2 * bruit(2, 141))
    rgb = rampe(np.clip(0.3 + 0.35 * anneaux + 0.2 * bruit(1, 142), 0, 1), [(0, (120, 92, 62)), (1, (196, 164, 118))])
    rgb[d > 14] = rgb[d > 14] * 0.5 + np.array([70, 54, 40]) * 0.5
    TX["rondin_palmier_bout"] = (rgb, None)
    # --- palmes séchées (toitures d'ombre du souk, abris) : folioles en biais, deux sens
    f1 = diagonale(bruit_aniso(sx=0.2, sy=5.0, graine=150))
    f2 = np.fliplr(diagonale(bruit_aniso(sx=0.2, sy=5.0, graine=151)))
    sens = ((y // 8) % 2 == 0)
    f = np.where(sens, f1, f2)
    rgb = rampe(np.clip(0.15 + 0.7 * f, 0, 1), [(0, (92, 70, 38)), (0.4, (150, 120, 66)), (0.75, (190, 162, 96)),
                                                 (1, (214, 190, 124))])
    nerv = (y % 8 == 0)
    rgb[nerv] *= 0.7
    TX["palmes_sechees"] = (rgb, None)
    # --- toiles du souk : rayures rouge / crème et bleu / crème, safran uni ; grain de toile
    for nom, c1, c2 in (("toile_rayee_rouge", (150, 50, 40), (222, 206, 172)), ("toile_rayee_bleue", (52, 78, 128), (222, 208, 176))):
        xs = x % 16
        ray = (xs < 6) | (xs == 9)
        rgb = np.where(ray[..., None], np.array(c1, float), np.array(c2, float))
        rgb *= tissage(160)[..., None]
        rgb *= (0.92 + 0.12 * bruit(2.2, 161))[..., None]
        rgb *= (1 + 0.06 * np.sin(2 * np.pi * (y + 4 * bruit(2.5, 164)) / 16))[..., None]
        TX[nom] = (rgb, None)
    rgb = rampe(0.3 + 0.5 * bruit(2.0, 162), [(0, (186, 120, 30)), (1, (226, 164, 52))]) * tissage(163)[..., None]
    TX["toile_safran"] = (rgb, None)
    # --- tapis berbère : losanges crème / noir / orange sur fond rouge
    lx = np.abs((x % 16) - 7.5)
    ly = np.abs((y % 16) - 7.5)
    l1 = lx + ly
    rgb = np.zeros((T, T, 3))
    rgb[:] = (146, 34, 30)
    rgb[(l1 > 5) & (l1 < 7)] = (232, 214, 180)
    rgb[l1 <= 3] = (30, 26, 28)
    rgb[l1 <= 1.2] = (222, 128, 36)
    rgb[(np.abs(l1 - 11) < 0.8)] = (222, 128, 36)
    rgb *= tissage(170, 0.08)[..., None]
    TX["tapis_berbere"] = (rgb, None)
    return TX


# ============================================================================================== ARCTIQUE
def panneau(coul, graine, givre=0.0):
    """Panneau sandwich isolé : deux lés par bloc (joints verticaux), nervures horizontales peu profondes, vis aux
    joints, salissures douces ; givre -> plaques de givre bleuté qui s'accrochent en bas des nervures."""
    x, y = grille_xy()
    c = np.array(coul, float)
    rgb = np.ones((T, T, 3)) * c
    rgb *= (0.93 + 0.1 * bruit(2.6, graine))[..., None]
    rgb *= (1 + 0.035 * np.where(y % 4 == 0, 1, np.where(y % 4 == 3, -1, 0)))[..., None]
    jv = (x % 16 == 0)
    rgb[jv] *= 0.6
    rgb[(x % 16 == 1)] *= 1.08
    vis = ((x % 16 == 2) | (x % 16 == 14)) & (y % 8 == 4)
    rgb[vis] = c * 0.55
    sale = bruit_aniso(sx=2.5, sy=0.35, graine=graine + 1)
    rgb *= (1 - 0.1 * np.clip((sale - 0.6) * 3, 0, 1))[..., None]
    if givre:
        g = bruit(2.0, graine + 2) + 0.25 * (y % 4 == 3)
        m = g > 1 - givre
        rgb[m] = rgb[m] * 0.3 + np.array([226, 238, 248]) * 0.7
        ec = (np.random.default_rng(graine + 3).random((T, T)) < 0.05) & m
        rgb[ec] = (250, 253, 255)
    return rgb


def tex_arctique():
    TX = {}
    x, y = grille_xy()
    # --- tôle ondulée blanche (ondes verticales de 8), rangée de rivets, coulures ; version givrée
    for nom, gv in (("tole_blanche", 0.0), ("tole_blanche_givree", 0.42)):
        onde = np.cos(2 * np.pi * (x + 0.5) / 8)
        rgb = np.ones((T, T, 3)) * np.array([218, 222, 224], float)
        rgb *= (1 + 0.13 * onde + 0.05 * np.sin(2 * np.pi * (x + 0.5) / 8))[..., None]
        rgb *= (0.95 + 0.08 * bruit(2.4, 200))[..., None]
        coul = bruit_aniso(sx=3.0, sy=0.3, graine=201)
        rgb *= (1 - 0.12 * np.clip((coul - 0.6) * 3, 0, 1))[..., None]
        rgb[(y == 1) & (x % 8 == 4)] = (120, 124, 128)
        rgb[y == 0] *= 0.9
        if gv:
            creux = (onde < -0.3)
            g = bruit(1.8, 202)
            m = (g > 1 - gv) | (creux & (g > 0.45))
            rgb[m] = rgb[m] * 0.25 + np.array([228, 240, 250]) * 0.75
            ec = (np.random.default_rng(203).random((T, T)) < 0.06) & m
            rgb[ec] = (252, 254, 255)
        TX[nom] = (rgb, None)
    for nom, c, g in (("panneau_polaire_blanc", (226, 228, 226), 210), ("panneau_polaire_orange", (222, 108, 34), 211),
                      ("panneau_polaire_rouge", (172, 44, 38), 212), ("panneau_polaire_gris", (126, 132, 136), 213)):
        TX[nom] = (panneau(c, g), None)
    TX["panneau_givre"] = (panneau((226, 228, 226), 214, givre=0.5), None)
    # --- caillebotis : grille d'acier galvanisé (porteurs tous les 4, entretoises tous les 8), jours transparents
    acier = np.ones((T, T, 3)) * np.array([150, 154, 158], float) * (0.9 + 0.15 * bruit(1.5, 220))[..., None]
    porteur = (x % 4 == 0)
    entret = (y % 8 == 0) | (y % 8 == 1)
    plein = porteur | entret
    acier[porteur & ~entret] *= 1.1
    acier[entret & ~porteur] *= 0.85
    TX["caillebotis"] = (acier, np.where(plein, 255, 0))
    # --- tôle larmée : losanges en relief alternés ; version rouillée
    for nom, rouille, g in (("tole_larmee", 0.0, 230), ("tole_larmee_rouille", 0.5, 231)):
        rgb = np.ones((T, T, 3)) * np.array([122, 126, 132], float) * (0.9 + 0.16 * bruit(2.2, g))[..., None]
        cx_ = x % 8 - 3.5
        cy_ = y % 8 - 3.5
        sens = (((x // 8) + (y // 8)) % 2 == 0)
        a_ = np.where(sens, cx_ + cy_, cx_ - cy_) / math.sqrt(2)
        b_ = np.where(sens, cx_ - cy_, cx_ + cy_) / math.sqrt(2)
        bosse = np.clip(1 - (np.abs(a_) / 3.6) ** 2 - (np.abs(b_) / 0.9) ** 2, 0, 1)
        rgb *= relief(bosse * 2.2, 0.6)[..., None]
        rgb[bosse > 0.3] *= 1.08
        if rouille:
            m = bruit(2.2, g + 1)
            rr = np.clip((m - (1 - rouille)) * 4, 0, 1)
            rouil = rampe(bruit(1.0, g + 2), [(0, (92, 44, 22)), (0.5, (138, 70, 32)), (1, (170, 96, 46))])
            rgb = rgb * (1 - rr[..., None]) + rouil * rr[..., None]
        TX[nom] = (rgb, None)
    # --- béton polaire : gris bleuté froid, traces de coffrage, givre en grains
    v = 0.5 * bruit(2.4, 240) + 0.3 * bruit(0.9, 241) + 0.2 * bruit_aniso(sx=0.3, sy=3, graine=242)
    rgb = rampe(0.2 + 0.6 * norm(v), [(0, (118, 126, 134)), (0.5, (146, 154, 162)), (1, (172, 180, 188))])
    rgb[y % 16 == 0] *= 0.85
    for (bx_, by_) in ((7, 7), (23, 23)):
        rgb[by_ - 1:by_ + 1, bx_ - 1:bx_ + 1] *= 0.6
    gr = (bruit(2.0, 243) > 0.72) & (np.random.default_rng(244).random((T, T)) < 0.35)
    rgb[gr] = rgb[gr] * 0.4 + np.array([232, 240, 248]) * 0.6
    TX["beton_polaire"] = (rgb, None)
    beton = rgb
    # --- béton à bandes de danger jaune / noir (usées)
    bande = (((x + y) // 8) % 2 == 0)
    peint = np.where(bande[..., None], np.array([214, 172, 36], float), np.array([34, 34, 36], float))
    use_ = np.clip((bruit(1.6, 250) - 0.62) * 4, 0, 1)
    use_ = np.maximum(use_, (np.random.default_rng(251).random((T, T)) < 0.05).astype(float))
    rgb = peint * (1 - use_[..., None]) + beton * use_[..., None]
    rgb *= (0.94 + 0.1 * bruit(1.0, 252))[..., None]
    TX["beton_marquage"] = (rgb, None)
    # --- neige tassée (allées de la base) : blanc bleuté, empreintes de chenilles douces, cristaux
    chen = 0.5 + 0.5 * np.cos(2 * np.pi * y / 4) * (np.abs(x - 15.5) < 11)
    v = 0.45 * bruit(2.4, 260) + 0.2 * chen + 0.35 * bruit(1.0, 261)
    rgb = rampe(0.2 + 0.6 * norm(v), [(0, (196, 208, 220)), (0.5, (220, 230, 238)), (1, (240, 246, 250))])
    ec = np.random.default_rng(262).random((T, T)) < 0.04
    rgb[ec] = (255, 255, 255)
    TX["neige_tassee"] = (rgb, None)
    # --- glace sale : neige grise mêlée de glace et de boue gelée
    v = bruit(2.2, 270)
    rgb = rampe(0.15 + 0.7 * norm(0.6 * v + 0.4 * bruit(1, 271)),
                [(0, (98, 96, 92)), (0.35, (142, 146, 150)), (0.7, (186, 196, 206)), (1, (214, 224, 232))])
    gl = bruit(2.6, 272) > 0.7
    rgb[gl] = rgb[gl] * 0.5 + np.array([170, 200, 222]) * 0.5
    TX["glace_sale"] = (rgb, None)
    # --- isolant (mousse jaune à nu, murs éventrés)
    v = 0.6 * bruit(1.6, 280) + 0.4 * bruit(0.6, 281)
    rgb = rampe(norm(v), [(0, (176, 136, 44)), (0.5, (220, 182, 74)), (1, (240, 214, 120))])
    TX["isolant"] = (rgb, None)
    # --- hublot : panneau blanc, cadre rond gris foncé, vitre teintée (translucide) avec reflet
    rgb = panneau((226, 228, 226), 290)
    d = np.hypot(x - 15.5, y - 15.5)
    cadre = (d >= 9) & (d < 12)
    vitre = d < 9
    rgb[cadre] = (70, 74, 80)
    rgb[(d >= 11) & (d < 12)] = (52, 56, 60)
    rgb[(d >= 9) & (d < 9.8)] = (110, 116, 124)
    rgb[vitre] = (96, 140, 170)
    refl = vitre & (np.abs((x - y) - (-6)) < 2.2)
    rgb[refl] = (190, 220, 236)
    vis = cadre & (np.abs(np.arctan2(y - 15.5, x - 15.5) % (np.pi / 3)) < 0.09) & (d > 10) & (d < 11)
    rgb[vis] = (150, 154, 160)
    TX["hublot"] = (rgb, np.where(vitre & ~refl, 150, np.where(refl, 190, 255)))
    # --- grille d'aération : persiennes (fentes sombres, lèvres claires) dans un panneau gris
    rgb = panneau((150, 156, 160), 300)
    zone = (x >= 3) & (x <= 28) & (y >= 3) & (y <= 28)
    fente = zone & (y % 4 == 0)
    levre = zone & (y % 4 == 1)
    rgb[fente] = (34, 36, 40)
    rgb[levre] *= 1.18
    rgb[zone & (y % 4 == 3)] *= 0.88
    rgb[((x == 2) | (x == 29)) & (y >= 2) & (y <= 29)] *= 0.7
    rgb[((y == 2) | (y == 29)) & (x >= 2) & (x <= 29)] *= 0.7
    TX["grille_aeration"] = (rgb, None)
    # --- bâche militaire blanche (tentes, abris) : toile épaisse, couture, salissures
    rgb = rampe(0.3 + 0.5 * bruit(2.2, 310), [(0, (200, 198, 188)), (1, (232, 230, 222))]) * tissage(311, 0.05)[..., None]
    rgb[y == 15] *= 0.82
    rgb[(y == 14) & (x % 3 == 0)] *= 0.86
    sale = bruit(2.4, 312) > 0.7
    rgb[sale] *= 0.9
    TX["bache_blanche"] = (rgb, None)
    return TX


# ============================================================================================== TOITS DE TÔLE
def tex_toits():
    """Tôles de toit en fer (10/10, à la place du cuivre « très moche ») : tôle ondulée peinte (vert armée, bleu passé)
    ou rouillée ; ondes de 8 le long de la pente, peinture écaillée sur la rouille, coulures de rouille sous les vis,
    salissures. Tuilable."""
    TX = {}
    x, y = grille_xy()
    onde = np.cos(2 * np.pi * (x + 0.5) / 8)
    rouille = rampe(bruit(1.2, 330), [(0, (86, 44, 24)), (0.5, (128, 66, 32)), (1, (160, 92, 46))])
    for nom, coul, g in (("tole_toit_verte", (70, 98, 72), 340), ("tole_toit_bleue", (66, 92, 120), 341),
                         ("tole_toit_rouille", None, 342)):
        if coul is None:
            base = rampe(np.clip(0.55 * bruit(2.2, g) + 0.45 * bruit(0.9, g + 1), 0, 1),
                         [(0, (82, 42, 24)), (0.4, (116, 60, 30)), (0.75, (146, 80, 40)), (1, (170, 104, 58))])
            peint = np.zeros((T, T), bool)
        else:
            base = np.ones((T, T, 3)) * np.array(coul, float) * (0.9 + 0.14 * bruit(2.4, g))[..., None]
            ecaille = bruit(1.8, g + 2) * 0.7 + bruit_aniso(sx=3.0, sy=0.4, graine=g + 3) * 0.3
            peint = ecaille < 0.68
            base = np.where(peint[..., None], base, rouille)
            bord = peint & ~np.roll(peint, 1, axis=0)
            base[bord] *= 1.1
        base *= (1 + 0.16 * onde + 0.05 * np.sin(2 * np.pi * (x + 0.5) / 8))[..., None]
        coul_r = bruit_aniso(sx=3.2, sy=0.25, graine=g + 4)                    # coulures de rouille
        traine = np.clip((coul_r - 0.62) * 3.5, 0, 1) * (onde < 0.2)
        base = base * (1 - 0.55 * traine[..., None]) + rouille * 0.55 * traine[..., None]
        base *= (1 - 0.08 * np.clip((bruit(2.6, g + 5) - 0.6) * 3, 0, 1))[..., None]
        vis = (y == 2) & (x % 8 == 4)
        base[vis] = (54, 52, 50)
        base[(y == 3) & (x % 8 == 4)] *= 0.8
        base[y == 0] *= 0.85                                                   # recouvrement entre deux tôles
        TX[nom] = (base, None)
    return TX


# ============================================================================================== catalogue
D, A_ = "desert", "arctique"
BLOCS = {
    "enduit_sable": dict(theme=D, forme="cube", fr="Enduit sable", en="Sand plaster", variantes=("dalle", "mur")),
    "enduit_sable_use": dict(theme=D, forme="cube", fr="Enduit sable usé", en="Worn sand plaster", variantes=()),
    "enduit_blanc": dict(theme=D, forme="cube", fr="Enduit à la chaux", en="Lime plaster", variantes=()),
    "enduit_blanc_use": dict(theme=D, forme="cube", fr="Enduit à la chaux usé", en="Worn lime plaster", variantes=()),
    "enduit_ocre": dict(theme=D, forme="cube", fr="Enduit ocre", en="Ochre plaster", variantes=()),
    "enduit_ocre_use": dict(theme=D, forme="cube", fr="Enduit ocre usé", en="Worn ochre plaster", variantes=()),
    "brique_crue": dict(theme=D, forme="cube", fr="Briques de terre crue", en="Adobe bricks", variantes=("escalier",)),
    "brique_crue_usee": dict(theme=D, forme="cube", fr="Briques de terre crue usées", en="Worn adobe bricks", variantes=()),
    "pierre_sable": dict(theme=D, forme="cube", fr="Pierre de taille dorée", en="Golden ashlar", variantes=("escalier", "dalle", "mur")),
    "dallage_sable": dict(theme=D, forme="cube", fr="Dallage de grès", en="Sandstone flagstones", variantes=()),
    "carreaux_terre_cuite": dict(theme=D, forme="cube", fr="Carreaux de terre cuite", en="Terracotta tiles", variantes=()),
    "paves_medina": dict(theme=D, forme="cube", fr="Pavés de la médina", en="Medina cobbles", variantes=("dalle",)),
    "sable_tasse": dict(theme=D, forme="cube", fr="Sable tassé", en="Packed sand", variantes=()),
    "zellige_bleu": dict(theme=D, forme="cube", fr="Zellige bleu", en="Blue zellige", variantes=()),
    "zellige_vert": dict(theme=D, forme="cube", fr="Zellige vert", en="Green zellige", variantes=()),
    "moucharabieh": dict(theme=D, forme="decoupe", fr="Moucharabieh", en="Mashrabiya", variantes=()),
    "bois_palmier": dict(theme=D, forme="cube", fr="Planches de palmier", en="Palm planks", variantes=("escalier", "dalle")),
    "rondin_palmier": dict(theme=D, forme="pilier", fr="Tronc de palmier", en="Palm trunk", variantes=()),
    "palmes_sechees": dict(theme=D, forme="cube", fr="Palmes séchées", en="Dried palm fronds", variantes=()),
    "toile_rayee_rouge": dict(theme=D, forme="cube", fr="Toile rayée rouge", en="Red striped canvas", variantes=()),
    "toile_rayee_bleue": dict(theme=D, forme="cube", fr="Toile rayée bleue", en="Blue striped canvas", variantes=()),
    "toile_safran": dict(theme=D, forme="cube", fr="Toile safran", en="Saffron canvas", variantes=()),
    "tapis_berbere": dict(theme=D, forme="tapis", fr="Tapis berbère", en="Berber rug", variantes=()),
    "tole_blanche": dict(theme=A_, forme="cube", fr="Tôle ondulée blanche", en="White corrugated sheet", variantes=()),
    "tole_blanche_givree": dict(theme=A_, forme="cube", fr="Tôle ondulée givrée", en="Frosted corrugated sheet", variantes=()),
    "panneau_polaire_blanc": dict(theme=A_, forme="cube", fr="Panneau polaire blanc", en="White polar panel", variantes=()),
    "panneau_polaire_orange": dict(theme=A_, forme="cube", fr="Panneau polaire orange", en="Orange polar panel", variantes=()),
    "panneau_polaire_rouge": dict(theme=A_, forme="cube", fr="Panneau polaire rouge", en="Red polar panel", variantes=()),
    "panneau_polaire_gris": dict(theme=A_, forme="cube", fr="Panneau polaire gris", en="Grey polar panel", variantes=()),
    "panneau_givre": dict(theme=A_, forme="cube", fr="Panneau givré", en="Frosted panel", variantes=()),
    "tole_larmee": dict(theme=A_, forme="cube", fr="Tôle larmée", en="Tread plate", variantes=("dalle",)),
    "beton_polaire": dict(theme=A_, forme="cube", fr="Béton polaire", en="Polar concrete", variantes=("escalier", "dalle", "mur")),
    "neige_tassee": dict(theme=A_, forme="cube", fr="Neige tassée", en="Packed snow", variantes=()),
    "glace_sale": dict(theme=A_, forme="cube", fr="Glace sale", en="Dirty ice", variantes=()),
    "hublot": dict(theme=A_, forme="translucide", fr="Hublot", en="Porthole", variantes=()),
    "tole_toit_verte": dict(theme="toits", forme="cube", fr="Tôle de toit verte", en="Green roof sheet", variantes=("escalier", "dalle")),
    "tole_toit_bleue": dict(theme="toits", forme="cube", fr="Tôle de toit bleue", en="Blue roof sheet", variantes=("escalier", "dalle")),
    "tole_toit_rouille": dict(theme="toits", forme="cube", fr="Tôle de toit rouillée", en="Rusty roof sheet", variantes=("escalier", "dalle")),
}


def textures():
    TX = {}
    TX.update(tex_desert())
    TX.update(tex_arctique())
    TX.update(tex_toits())
    manque = [n for n in BLOCS if n not in TX]
    assert not manque, manque
    # seulement les textures des blocs gardés : les blocs jamais posés sur la carte ont été retirés le 11/10 (leurs
    # dessins restent ci-dessus, inutilisés)
    utiles = set(BLOCS) | {n + "_bout" for n, d in BLOCS.items() if d["forme"] == "pilier"}
    return {n: v for n, v in TX.items() if n in utiles}
