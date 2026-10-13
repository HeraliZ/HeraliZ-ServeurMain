#!/usr/bin/env python3
"""
Sons du train de HeraliZ Utils, fabriqués de toutes pièces par le calcul : bruits filtrés, résonances amorties (chocs de
métal), sons additifs (avertisseur, carillon) et traitements (filtres, réverbération courte). Aucun enregistrement,
aucun son tiré d'un jeu ou d'une banque de sons.

    python mods/heralisz-utils/outils/sons_train.py                    refait les .ogg du mod
    python mods/heralisz-utils/outils/sons_train.py --seul klaxon,freins  seulement ceux-là
    python mods/heralisz-utils/outils/sons_train.py --ecoute DOSSIER      en plus : copies d'écoute (mp3) de chaque son
                                                                        et deux scènes (arrêt en gare, à bord)

Besoins : Python 3.11+, numpy, ffmpeg (avec libvorbis ; libmp3lame pour les copies d'écoute).
Sortie : src/client/resources/assets/heralisz_utils/sounds/train/*.ogg (mono : sinon le jeu ne les atténue pas avec la
distance ; 44,1 kHz ; Vorbis). Sur un même PC, mêmes fichiers d'une fois sur l'autre (hasard à graine fixe, encodage
« bitexact ») : relancer sans rien changer ne crée pas de différence dans Git.
Niveaux homogènes (sonie mesurée, crête plafonnée), aucun clic au début ni à la fin, boucles sans couture (tout ce qui
boucle est calculé « en cercle » : filtres, réverbération et chocs qui débordent de la fin reviennent au début).

Qui joue quoi, quand et à quelle portée : fr.heralisz.utils.train.TrainSonsClient (src/client) et sounds.json
(attenuation_distance). Les temps des portes suivent l'horaire (Horaire : OUVERTURE, FERMETURE, MOUVEMENT_PORTE) et
leur animation (RameRendu : sortie de 7 cm sur 22 % du mouvement, puis glissement adouci).
"""
import argparse
import subprocess
import sys
from pathlib import Path

import numpy as np

SR = 44100
ICI = Path(__file__).resolve().parent
SORTIE = ICI.parent / "src/client/resources/assets/heralisz_utils/sounds/train"

# temps des portes latérales (Horaire.MOUVEMENT_PORTE et RameRendu : sortie puis glissement)
MOUVEMENT_PORTE = 1.7
SORTIE_PORTE = 0.22 * MOUVEMENT_PORTE
# portes_fermeture.ogg : les bips d'abord, la porte bouge à partir de BIPS secondes (TrainSonsClient.BIPS, le même)
BIPS = 1.4
# portes intérieures (Voyageurs.DUREE_PORTE)
DUREE_PORTE_INT = 0.55
# freins.ogg : de 3 blocs/s à l'arrêt en FREINAGE secondes (TrainSonsClient : déclenché 5,5 s avant l'arrivée)
FREINAGE = 5.5


# ------------------------------------------------------------------------------------------------ outils de calcul

def temps(duree):
    return np.arange(int(round(duree * SR))) / SR


def bruit(n, graine):
    return np.random.default_rng(graine).standard_normal(n)


def forme(f, points):
    """Réponse en fréquence donnée par des points (Hz, dB), interpolée sur une échelle logarithmique."""
    lf = np.log10(np.maximum(f, 1.0))
    xs = np.log10([p[0] for p in points])
    ys = [p[1] for p in points]
    return 10 ** (np.interp(lf, xs, ys) / 20)


def bande(f, f0, q):
    """Passe-bande du 2e ordre (amplitude)."""
    r = np.maximum(f, 1e-3) / f0
    return 1 / np.sqrt(1 + q * q * (r - 1 / r) ** 2)


def filtrer(x, gain, circulaire=False):
    """Filtre à phase nulle par FFT. Circulaire : pour une boucle (ce qui sort de la fin revient au début)."""
    n = len(x)
    N = n if circulaire else 1 << int(np.ceil(np.log2(n + SR)))
    X = np.fft.rfft(x, N)
    f = np.fft.rfftfreq(N, 1 / SR)
    g = gain(f) if callable(gain) else gain
    return np.fft.irfft(X * g, N)[:n]


def filtrer_variable(x, gain_ft, trame=2048, saut=512):
    """Filtre qui change avec le temps : gain_ft(f, t) appliqué trame par trame (fenêtres de Hann, recouvrement 3/4)."""
    n = len(x)
    w = np.sqrt(0.5 - 0.5 * np.cos(2 * np.pi * np.arange(trame) / trame))
    xp = np.concatenate([np.zeros(trame), x, np.zeros(trame)])
    y = np.zeros(len(xp))
    f = np.fft.rfftfreq(trame, 1 / SR)
    for d in range(0, len(xp) - trame + 1, saut):
        tc = (d + trame / 2 - trame) / SR
        Y = np.fft.rfft(xp[d:d + trame] * w) * gain_ft(f, tc)
        y[d:d + trame] += np.fft.irfft(Y, trame) * w
    return y[trame:trame + n] / (trame / (2 * saut))


def lisse(u):
    """Courbe des portes du jeu (Voyageurs.lisse) : départ et arrivée tout en douceur."""
    u = np.clip(u, 0, 1)
    return u ** 3 * (u * (u * 6 - 15) + 10)


def vitesse_lisse(u):
    """Dérivée de lisse, ramenée à 1 au maximum : la vitesse d'une porte pendant son glissement."""
    u = np.clip(u, 0, 1)
    return 16 * u * u * (1 - u) * (1 - u)


def ajouter(dest, src, t0, circulaire=False):
    """Ajoute src dans dest à partir de t0 secondes (boucle : ce qui déborde revient au début)."""
    i = int(round(t0 * SR))
    if circulaire:
        idx = (np.arange(len(src)) + i) % len(dest)
        np.add.at(dest, idx, src)
    else:
        j = min(len(dest), i + len(src))
        if j > i:
            dest[i:j] += src[:j - i]


def choc(modes, duree, contact=None, graine=0):
    """Un choc : résonances amorties [(f Hz, t60 s, amplitude)] et bruit de contact (f bas, f haut, durée, amplitude)."""
    t = temps(duree)
    y = np.zeros(len(t))
    for f, t60, a in modes:
        y += a * np.sin(2 * np.pi * f * t) * np.exp(-6.908 * t / t60)
    if contact:
        fb, fh, dc, a = contact
        b = filtrer(bruit(len(t), graine), lambda f: bande(f, np.sqrt(fb * fh), np.sqrt(fb * fh) / (fh - fb)))
        y += a * b / (np.abs(b).max() + 1e-9) * np.exp(-t / dc)
    return y * np.minimum(1, t / 0.0004)


def bruit_filtre(n, graine, points, circulaire=False):
    b = filtrer(bruit(n, graine), lambda f: forme(f, points), circulaire)
    return b / (np.sqrt(np.mean(b * b)) + 1e-12)


def reverb(x, rt60, melange, predelai=0.015, clair=3500, graine=7, circulaire=False):
    """Réverbération courte (queue de bruit qui s'éteint, plus sombre que le son) : quai, voiture, plein air."""
    t = temps(rt60 * 1.3)
    ir = bruit(len(t), graine) * np.exp(-6.908 * t / rt60)
    ir = filtrer(ir, lambda f: forme(f, [(60, -12), (200, 0), (clair, -2), (clair * 3, -18), (18000, -40)]))
    ir *= np.minimum(1, t / 0.004)
    ir = np.concatenate([np.zeros(int(predelai * SR)), ir])
    ir /= np.sqrt(np.sum(ir * ir))
    if circulaire:
        n = len(x)
        h = np.zeros(n)
        k = min(n, len(ir))
        h[:k] = ir[:k]
        wet = np.fft.irfft(np.fft.rfft(x) * np.fft.rfft(h), n)
        return x + melange * wet
    N = 1 << int(np.ceil(np.log2(len(x) + len(ir))))
    wet = np.fft.irfft(np.fft.rfft(x, N) * np.fft.rfft(ir, N), N)[:len(x) + len(ir)]
    y = np.concatenate([x, np.zeros(len(ir))])
    return y + melange * wet


def fondu(x, entree=0.002, sortie=0.03):
    """Fondus en cosinus aux deux bouts : ni clic au début ni à la fin."""
    x = x.copy()
    a, b = int(entree * SR), int(sortie * SR)
    if a > 0:
        x[:a] *= 0.5 - 0.5 * np.cos(np.pi * np.arange(a) / a)
    if b > 0:
        x[-b:] *= 0.5 + 0.5 * np.cos(np.pi * np.arange(b) / b)
    return x


def couper_silence(x, seuil_db=-72, marge=0.05):
    """Retire le silence de la fin (queue de réverbération inaudible), puis fondu de sortie."""
    e = np.abs(x)
    lim = np.max(e) * 10 ** (seuil_db / 20)
    fin = np.nonzero(e > lim)[0]
    n = min(len(x), (fin[-1] if len(fin) else len(x) - 1) + int(marge * SR))
    return fondu(x[:n], 0.0, min(0.08, marge))


def passe_haut(x, fc=28, circulaire=False):
    """Retire le continu et l'infragrave inutile."""
    return filtrer(x, lambda f: 1 / np.sqrt(1 + (fc / np.maximum(f, 1e-3)) ** 4), circulaire)


def sonie(x, boucle=False):
    """Sonie approchée (dB, à la manière des LUFS) : pondération K simplifiée ; pour un son ponctuel, la fenêtre de
    400 ms la plus forte ; pour une boucle, la moyenne."""
    k = filtrer(np.concatenate([x, x]) if boucle else x,
                lambda f: (1 / np.sqrt(1 + (60 / np.maximum(f, 1e-3)) ** 4))
                * (1 + (10 ** (4 / 20) - 1) / (1 + (1700 / np.maximum(f, 1.0)) ** 2)))
    if boucle:
        ms = np.mean(k[:len(x)] ** 2)
    else:
        w = int(0.4 * SR)
        k2 = np.concatenate([k * k, np.zeros(w)])
        c = np.cumsum(k2)
        ms = np.max(c[w:] - c[:-w]) / w
    return -0.691 + 10 * np.log10(ms + 1e-20)


def niveau(x, cible, crete=-1.0, boucle=False):
    """Amène le son à la sonie cible, sans que la crête dépasse `crete` dBFS."""
    g = 10 ** ((cible - sonie(x, boucle)) / 20)
    g = min(g, 10 ** (crete / 20) / (np.max(np.abs(x)) + 1e-12))
    return x * g


# ------------------------------------------------------------------------------------------------ éléments communs

def souffle(duree, graine, bas=1200, haut=7000, attaque=0.01, tau=0.25, pente=0.0):
    """Échappement d'air comprimé (« pshh ») : bruit clair, attaque rapide, extinction exponentielle ; `pente` fait
    descendre le timbre pendant que la pression baisse."""
    t = temps(duree)
    b = bruit(len(t), graine)
    fc = np.sqrt(bas * haut)

    def g(f, tc):
        c = fc * (1 - pente * min(1.0, max(0.0, tc) / max(duree, 1e-3)))
        return forme(f / c * fc, [(bas / 3, -30), (bas, -6), (fc, 0), (haut, -4), (haut * 2, -20), (20000, -40)])

    y = filtrer_variable(b, g)
    env = np.minimum(1, t / attaque) * np.exp(-np.maximum(0, t - attaque) / tau)
    return y / (np.sqrt(np.mean(y * y)) + 1e-12) * env


def glissement(t, vitesse, graine, grave=(110, 900), cliquetis=55.0):
    """Une porte qui roule sur son rail : grondement des galets et petits cliquetis, au rythme de sa vitesse (0..1)."""
    n = len(t)
    g0, g1 = grave
    rou = bruit_filtre(n, graine, [(g0 / 3, -30), (g0, -4), (np.sqrt(g0 * g1), 0), (g1, -6), (g1 * 3, -24), (12000, -48)])
    # le galet « tremble » un peu : modulation lente aléatoire
    mod = bruit_filtre(n, graine + 1, [(2, -20), (8, 0), (30, -6), (80, -30)])
    y = rou * vitesse * (1 + 0.25 * mod / (np.abs(mod).max() + 1e-9))
    # cliquetis : petits chocs sur le rail, plus serrés quand la porte va vite
    rng = np.random.default_rng(graine + 2)
    p = np.cumsum(vitesse) / SR * cliquetis
    k = 0.0
    while True:
        k += rng.uniform(0.6, 1.4)
        i = np.searchsorted(p, k)
        if i >= n:
            break
        c = choc([(rng.uniform(2200, 3400), 0.012, 1.0), (rng.uniform(4200, 5600), 0.008, 0.5)], 0.03)
        ajouter(y, 0.18 * vitesse[i] * c, i / SR)
    return y


def butee(force, graine, ferme=False):
    """Fin de course d'une porte, amortie par le caoutchouc (ferme : la porte s'enfonce dans son joint)."""
    rng = np.random.default_rng(graine)
    if ferme:
        modes = [(68, 0.22, 1.0), (124, 0.16, 0.7), (250, 0.1, 0.5), (610, 0.06, 0.3), (1350, 0.04, 0.12)]
        contact = (150, 1600, 0.006, 0.35)
    else:
        modes = [(92, 0.16, 1.0), (176, 0.12, 0.65), (410, 0.07, 0.45), (720, 0.05, 0.3), (1120, 0.04, 0.14)]
        contact = (250, 2500, 0.004, 0.4)
    modes = [(f * rng.uniform(0.96, 1.04), t60, a) for f, t60, a in modes]
    return force * choc(modes, 0.45, contact, graine)


def adoucir(x, k):
    """Arrondit les crêtes (tangente hyperbolique) : un choc garde son attaque mais sonne plus plein."""
    m = np.max(np.abs(x)) + 1e-12
    return np.tanh(k * x / m) / np.tanh(k) * m


def declic(graine, force=1.0):
    """Petit déclic d'électrovanne ou de verrou."""
    rng = np.random.default_rng(graine)
    return force * choc([(rng.uniform(1700, 2100), 0.02, 1.0), (rng.uniform(3300, 3900), 0.012, 0.6),
                         (rng.uniform(5200, 6000), 0.006, 0.3)], 0.06, (2000, 8000, 0.0015, 0.6), graine)


# ------------------------------------------------------------------------------------------------ les sons

def joint(graine):
    """Une roue passe un joint de rail : « tac » mat du rail, coup sourd du bogie (un son par essieu)."""
    rng = np.random.default_rng(graine)

    def v():
        return rng.uniform(0.92, 1.08)

    modes = [(72 * v(), 0.22, 0.55), (106 * v(), 0.18, 0.45), (165 * v(), 0.12, 0.35),    # bogie, caisse
             (330 * v(), 0.09, 0.3), (640 * v(), 0.08, 0.42), (1150 * v(), 0.06, 0.3),       # rail
             (1840 * v(), 0.04, 0.18), (2700 * v(), 0.025, 0.09), (3900 * v(), 0.016, 0.04)]  # roue
    y = choc(modes, 0.5, (600, 4000, 0.004, 0.7 * v()), graine)
    # la roue tombe dans la lacune puis frappe le rail suivant : un second contact juste après (« t-tac »)
    ajouter(y, 0.45 * choc([(f * 1.03, t60 * 0.8, a) for f, t60, a in modes[3:]], 0.3, (800, 5000, 0.003, 0.5), graine + 50),
            rng.uniform(0.006, 0.011))
    y = filtrer(y, lambda f: forme(f, [(30, -18), (60, 0), (3000, 0), (7000, -9), (16000, -30)]))
    y = adoucir(y, 3.0)                                       # crête arrondie : plus présent à crête égale
    return niveau(couper_silence(fondu(y, 0.0005, 0.06), -66), -19, -1.5)


def attelage():
    """Les attelages et les tampons se tendent au départ : « cla-clong » lourd."""
    y = choc([(46, 0.34, 1.0), (81, 0.28, 0.8), (128, 0.24, 0.6), (410, 0.18, 0.38), (745, 0.15, 0.3),
              (1230, 0.11, 0.2), (1960, 0.08, 0.1), (3050, 0.05, 0.05)], 0.9, (150, 2500, 0.008, 0.45), 41)
    y2 = choc([(52, 0.25, 0.5), (93, 0.2, 0.4), (455, 0.14, 0.22), (820, 0.11, 0.16), (1380, 0.08, 0.1)], 0.7,
              (200, 2500, 0.005, 0.3), 42)
    ajouter(y, y2, 0.05)
    y = reverb(y, 0.7, 0.18, 0.02, 2500, 43)
    y = filtrer(y, lambda f: forme(f, [(25, -24), (45, 0), (2500, 0), (6000, -12), (16000, -36)]))
    return niveau(couper_silence(fondu(y, 0.0005, 0.05)), -18, -2)


def voie(duree=4.0):
    """Boucle du roulement vu de dehors (sans les joints, joués à part selon la vitesse) : souffle des roues sur le rail,
    grondement du sol, léger « chant » du rail. Tout est circulaire : pas de couture."""
    t = temps(duree)
    n = len(t)
    roul = bruit_filtre(n, 101, [(20, -40), (40, -14), (80, -5), (160, -3), (420, 0), (950, 0), (1600, -3),
                                 (3000, -10), (6000, -21), (12000, -36), (20000, -50)], True)
    # rugosité : le souffle n'est pas plat, il « grêle » un peu (modulation aléatoire 4 à 30 Hz)
    rug = bruit_filtre(n, 102, [(1, -30), (4, -3), (12, 0), (30, -6), (90, -30)], True)
    roul *= 1 + 0.22 * rug / np.abs(rug).max()
    sol = bruit_filtre(n, 103, [(15, -20), (30, 0), (60, 0), (110, -6), (220, -24), (800, -50)], True)
    # roues un peu ovalisées : battement au tour de roue (5,25 Hz à pleine vitesse, entier sur la boucle)
    sol *= 1 + 0.18 * np.sin(2 * np.pi * 5.25 * t)
    chant = bruit_filtre(n, 104, [(700, -40), (1050, -12), (1120, 0), (1190, -12), (1600, -40)], True)
    chant *= 1 + 0.5 * np.sin(2 * np.pi * 0.75 * t + 1.0)
    y = roul + 0.65 * sol + 0.06 * chant
    y *= 1 + 0.06 * np.sin(2 * np.pi * 0.5 * t) + 0.04 * np.sin(2 * np.pi * 1.25 * t + 2.0)
    y = reverb(y, 0.5, 0.1, 0.02, 3000, 105, circulaire=True)
    y = passe_haut(y, 25, True)
    return niveau(y, -20, -3, boucle=True)


def cabine(duree=6.0):
    """Boucle à bord : grondement sourd du plancher et de la caisse, roulement étouffé, petits bruits de l'aménagement
    (les joints sont joués à part, sous les bogies)."""
    t = temps(duree)
    n = len(t)
    grond = bruit_filtre(n, 201, [(18, -24), (32, -4), (48, 0), (70, -1), (110, -6), (200, -13), (400, -20),
                                  (800, -29), (1600, -40), (3200, -52), (10000, -70)], True)
    caisse = bruit_filtre(n, 202, [(30, -40), (44, -10), (47, 0), (50, -10), (64, -12), (68, -2), (72, -12),
                                   (100, -40)], True)
    roul = bruit_filtre(n, 203, [(60, -20), (150, -6), (350, 0), (700, -6), (1400, -20), (3000, -40)], True)
    y = grond + 0.5 * caisse + 0.3 * roul
    # balancement lent de la caisse et tour de roue (entiers sur la boucle)
    y *= 1 + 0.10 * np.sin(2 * np.pi * 0.5 * t) + 0.08 * np.sin(2 * np.pi * 5.5 * t + 0.7)
    # petits bruits : vitres, porte-bagages, tablette (rares et faibles)
    rng = np.random.default_rng(204)
    for k in range(12):
        c = choc([(rng.uniform(1800, 3200), rng.uniform(0.01, 0.03), 1.0), (rng.uniform(3500, 5200), 0.01, 0.5),
                  (rng.uniform(400, 900), 0.03, 0.4)], 0.08)
        ajouter(y, rng.uniform(0.03, 0.08) * c * np.sqrt(np.mean(y * y)) * 8, rng.uniform(0, duree), circulaire=True)
    y = reverb(y, 0.35, 0.12, 0.004, 2000, 205, circulaire=True)
    y = passe_haut(y, 22, True)
    return niveau(y, -21, -3, boucle=True)


def moteur(duree=2.4):
    """Boucle du diesel de la locomotive (V12 à 600 tr/min : 60 explosions par seconde, un cycle toutes les 0,2 s) :
    échappement, claquement des injecteurs, souffle du turbo. Le jeu en change la hauteur (régime) et le volume
    (charge). 735 échantillons entre deux explosions : la boucle tombe juste."""
    t = temps(duree)
    n = len(t)
    pas = SR // 60
    rng = np.random.default_rng(301)
    motif = rng.uniform(0.72, 1.0, 12)                                    # chaque cylindre a sa force
    imp = np.zeros(n)
    for k in range(n // pas):
        i = k * pas + int(rng.integers(-8, 9))
        bouf = rng.standard_normal(400) * np.exp(-np.arange(400) / rng.uniform(90, 140))
        bouf[0] += 6.0
        ajouter(imp, motif[k % 12] * (1 + rng.normal(0, 0.1)) * bouf, i / SR, circulaire=True)
    ech = filtrer(imp, lambda f: forme(f, [(18, -36), (35, -8), (60, 0), (120, 0), (190, -2), (300, -8),
                                           (600, -20), (1200, -32), (3000, -48), (12000, -70)]), True)
    # claquement : bruit clair modulé par chaque explosion
    env = filtrer(np.abs(imp), lambda f: 1 / np.sqrt(1 + (f / 90) ** 2), True)
    env = np.maximum(env, 0) / (env.max() + 1e-9)
    clac = bruit_filtre(n, 303, [(600, -30), (1500, -4), (2600, 0), (4200, -6), (9000, -30)], True) * env ** 1.5
    turbo = np.sin(2 * np.pi * 3150 * t) * (1 + 0.2 * np.sin(2 * np.pi * 2.5 * t))      # 7 560 tours : entier
    souf = bruit_filtre(n, 304, [(100, -20), (400, 0), (1500, -8), (5000, -30)], True)
    y = (ech / np.sqrt(np.mean(ech * ech)) + 0.16 * clac / np.sqrt(np.mean(clac * clac) + 1e-12)
         + 0.012 * turbo + 0.12 * souf)
    y = reverb(y, 0.45, 0.12, 0.01, 2500, 305, circulaire=True)
    y = passe_haut(y, 25, True)
    return niveau(y, -19, -3, boucle=True)


def depart():
    """Départ : les freins se desserrent (souffle), le turbo monte en sifflant, la charge gronde (le régime du moteur
    lui-même monte dans la boucle « moteur »)."""
    d = 5.5
    t = temps(d)
    n = len(t)
    y = 0.35 * souffle(d, 401, 1000, 6000, 0.02, 0.4, 0.35)
    f = 1100 + 2300 * (1 - np.exp(-t / 1.3))
    ph = 2 * np.pi * np.cumsum(f) / SR
    a = np.clip(t / 1.2, 0, 1) ** 1.5 * np.clip((d - 0.3 - t) / 2.2, 0, 1)
    turbo = (np.sin(ph) + 0.25 * np.sin(2 * ph + 0.4)) * a
    # souffle du turbo autour de son sifflement
    sf = filtrer_variable(bruit(n, 402), lambda fr, tc: bande(fr, 1100 + 2300 * (1 - np.exp(-max(tc, 0) / 1.3)), 6))
    turbo = 0.05 * turbo + 0.12 * sf / (np.sqrt(np.mean(sf * sf)) + 1e-9) * a
    charge = bruit_filtre(n, 403, [(15, -24), (30, -2), (55, 0), (100, -6), (200, -20), (600, -40)])
    ca = np.clip(t / 1.4, 0, 1) * np.clip((d - t) / 3.0, 0, 1)
    y += turbo + 0.55 * charge * ca
    y = reverb(y, 0.8, 0.15, 0.03, 2500, 404)
    y = passe_haut(y, 25)
    return niveau(couper_silence(fondu(y, 0.003, 0.2)), -20, -3)


def klaxon():
    """Avertisseur de la locomotive (deux tons, tierce mineure ré–fa) : souffle au départ, attaque qui monte en pression,
    timbre de pavillon (harmoniques adoucies), fin qui retombe un peu."""
    d, tenue = 1.95, 1.25
    t = temps(d)
    n = len(t)
    env = np.clip(t / 0.06, 0, 1) ** 2
    rel = np.where(t < tenue, 1.0, np.exp(-(t - tenue) / 0.09))
    env = env * rel * (1 + 0.04 * np.sin(2 * np.pi * 1.7 * t))
    y = np.zeros(n)
    for f0, gain, dec in ((293.66, 1.0, 1.0016), (349.23, 0.85, 0.9985)):
        for dt in (1.0, dec):                                             # deux pavillons à peine désaccordés
            f = f0 * dt * (1 - 0.035 * np.exp(-t / 0.045)) * (1 - 0.018 * np.clip((t - tenue) / 0.25, 0, 1))
            ph = 2 * np.pi * np.cumsum(f) / SR
            for h in range(1, 40):
                fh = h * f0
                if fh > 11000:
                    break
                a = h ** -0.75 * forme(np.array([fh]), [(200, -6), (700, 0), (1300, 0), (2600, -10), (5000, -24),
                                                        (11000, -42)])[0]
                y += gain * a * np.sin(h * ph + 0.37 * h)
    y = y / np.abs(y).max() * env
    souf = bruit_filtre(n, 501, [(300, -20), (900, 0), (2500, -3), (6000, -20)]) * env * 0.05
    souf[:int(0.12 * SR)] *= 1 + 2.0 * np.exp(-t[:int(0.12 * SR)] / 0.03)  # bouffée d'air à l'ouverture
    y = y + souf
    y = reverb(y, 0.9, 0.18, 0.05, 3000, 502)
    y = passe_haut(y, 40)
    return niveau(couper_silence(fondu(y, 0.003, 0.25)), -15, -1.5)


def freins():
    """Arrivée en gare, les 5,5 dernières secondes : les sabots serrent, le frottement s'assourdit avec la vitesse, un
    léger crissement monte à la fin, puis l'arrêt (la caisse bascule un peu et revient) et le silence."""
    T = FREINAGE
    d = T + 1.6
    t = temps(d)
    n = len(t)
    v = np.clip(1 - t / T, 0, 1)                                       # vitesse relative (3 blocs/s → 0)
    pression = 0.5 + 0.5 * lisse(t / 1.2)
    # frottement des sabots : de plus en plus sourd à mesure que le train ralentit
    fr = filtrer_variable(bruit(n, 601), lambda f, tc: forme(
        f, [(80, -30), (200, -10), (450 + 500 * max(0.0, 1 - tc / T), 0), (1400 + 900 * max(0.0, 1 - tc / T), -3),
            (4000, -22), (9000, -40), (18000, -56)]))
    fr /= np.sqrt(np.mean(fr * fr))
    # grain du frottement : le sabot « accroche » irrégulièrement (modulation aléatoire de 3 à 25 Hz)
    grain = filtrer(bruit(n, 608), lambda f: forme(f, [(1, -30), (3, -6), (8, 0), (25, -4), (60, -30)]))
    fr *= 1 + 0.35 * grain / np.abs(grain).max()
    tour = 2 * np.pi * np.cumsum(3.0 * v / (2 * np.pi * 0.46)) / SR       # une bosse par tour de roue
    efr = pression * (0.12 + 0.88 * v ** 0.9) * (1 + 0.18 * np.sin(tour)) * np.where(t < T, 1.0, np.exp(-(t - T) / 0.06))
    y = fr * efr
    # crissement léger : deux modes qui flottent un peu, de 2,4 s jusqu'à l'arrêt
    lent = filtrer(bruit(n, 602), lambda f: 1 / (1 + (f / 2.5) ** 4))
    lent /= np.abs(lent).max()
    lent2 = filtrer(bruit(n, 603), lambda f: 1 / (1 + (f / 6.0) ** 4))
    lent2 /= np.abs(lent2).max()
    asq = lisse((t - 2.0) / 2.6) * np.clip((T - t) / 0.12, 0, 1) * (0.7 + 0.3 * lent2)
    fsq = 3050 * (1 + 0.006 * lent) * (1 - 0.025 * (1 - v))
    ph = 2 * np.pi * np.cumsum(fsq) / SR
    ph2 = 2 * np.pi * np.cumsum(fsq * 1.47) / SR
    sq = (np.sin(ph) + 0.35 * np.sin(ph2 + 1.0) + 0.12 * np.sin(2 * ph)) * asq
    y += 0.2 * sq
    # roulement qui s'éteint
    roul = bruit_filtre(n, 604, [(30, -12), (60, 0), (200, -2), (600, -10), (1500, -26), (5000, -50)])
    y += 0.35 * roul * v ** 1.2
    # arrêt : la caisse bascule, les tampons se touchent, elle revient
    arret = choc([(42, 0.5, 1.0), (68, 0.4, 0.7), (112, 0.3, 0.4), (520, 0.12, 0.12), (980, 0.08, 0.06)], 1.2,
                 (100, 1500, 0.01, 0.2), 605)
    ajouter(y, 0.9 * arret, T - 0.02)
    ajouter(y, 0.35 * attelage_brut(606), T + 0.1)
    ajouter(y, 0.4 * choc([(38, 0.45, 1.0), (61, 0.35, 0.6), (98, 0.25, 0.3)], 1.0, None), T + 0.62)
    y = reverb(y, 0.6, 0.12, 0.02, 2500, 607)
    y = passe_haut(y, 25)
    return niveau(couper_silence(fondu(y, 0.03, 0.3)), -20, -2)


def attelage_brut(graine):
    rng = np.random.default_rng(graine)
    return choc([(50 * rng.uniform(0.95, 1.05), 0.3, 1.0), (420 * rng.uniform(0.95, 1.05), 0.15, 0.4),
                 (760, 0.12, 0.3), (1250, 0.09, 0.18)], 0.8, (150, 2500, 0.006, 0.4), graine)


def detente():
    """Détente de l'air à l'arrêt : déclic de la valve, gros souffle qui baisse avec la pression, petit coup sourd."""
    d = 3.0
    t = temps(d)
    y = np.zeros(len(t))
    ajouter(y, 0.25 * declic(701), 0.0)
    y += souffle(d, 702, 900, 7500, 0.03, 0.55, 0.45)
    whump = bruit_filtre(len(t), 703, [(30, -20), (70, 0), (160, -2), (400, -18), (1000, -40)])
    y += 0.5 * whump * np.clip(t / 0.02, 0, 1) * np.exp(-t / 0.12)
    y = reverb(y, 0.6, 0.12, 0.02, 3000, 704)
    y = passe_haut(y, 30)
    return niveau(couper_silence(fondu(y, 0.002, 0.3)), -21, -2)


def carillon():
    """Annonce discrète à l'arrivée : trois notes douces (sol, do, mi) façon lames de métal, dans le haut-parleur du
    quai (bande un peu étroite) et l'écho de la gare."""
    d = 3.6
    t = temps(d)
    y = np.zeros(len(t))
    for k, f0 in enumerate((392.0, 523.25, 659.25)):
        tn = temps(d - 0.38 * k)
        a = 0.5 - 0.5 * np.cos(np.pi * np.clip(tn / 0.008, 0, 1))
        note = (np.sin(2 * np.pi * f0 * tn) * np.exp(-6.908 * tn / 2.4)
                + 0.22 * np.sin(2 * np.pi * f0 * 4.0 * tn + 0.3) * np.exp(-6.908 * tn / 0.7)
                + 0.05 * np.sin(2 * np.pi * f0 * 10.0 * tn + 1.1) * np.exp(-6.908 * tn / 0.18))
        ajouter(y, a * note * (1.0 - 0.08 * k), 0.38 * k)
    y = filtrer(y, lambda f: forme(f, [(120, -24), (260, -4), (500, 0), (3500, 0), (6000, -10), (12000, -30)]))
    y = reverb(y, 1.5, 0.28, 0.03, 3500, 801)
    y = passe_haut(y, 60)
    return niveau(couper_silence(fondu(y, 0.002, 0.4)), -22, -3)


def portes_ouverture():
    """Ouverture d'une porte latérale (1,7 s, synchronisée sur l'animation) : déclic de l'électrovanne, « pschh » de
    l'air, la porte sort de 7 cm (verrou, joint qui décolle), glisse sur ses galets, s'arrête en douceur en butée."""
    d = MOUVEMENT_PORTE + 0.9
    t = temps(d)
    n = len(t)
    y = np.zeros(n)
    ajouter(y, 0.3 * declic(901), 0.0)
    y += 0.9 * souffle(d, 902, 1300, 7000, 0.008, 0.18, 0.2)
    ajouter(y, 0.45 * choc([(380, 0.07, 1.0), (820, 0.05, 0.6), (1610, 0.03, 0.35), (125, 0.1, 0.5)], 0.25,
                           (400, 3000, 0.003, 0.3), 903), 0.05)
    joint_decolle = bruit_filtre(int(0.12 * SR), 904, [(60, -20), (150, 0), (400, -8), (1200, -30)])
    ajouter(y, 0.25 * joint_decolle * np.exp(-np.arange(len(joint_decolle)) / SR / 0.03), 0.1)
    vit = vitesse_lisse((t - SORTIE_PORTE) / (MOUVEMENT_PORTE - SORTIE_PORTE))
    y += 0.28 * glissement(t, vit, 905)
    y += 0.06 * bruit_filtre(n, 906, [(1000, -20), (2500, 0), (6000, -6), (12000, -30)]) * vit   # vérin
    ajouter(y, butee(1.0, 907), MOUVEMENT_PORTE - 0.01)
    y += 0.08 * souffle(d, 908, 1500, 6000, 0.02, 0.15, 0.0) * (t > MOUVEMENT_PORTE)
    y = reverb(y, 0.45, 0.14, 0.012, 3000, 909)
    y = passe_haut(y, 35)
    return niveau(couper_silence(fondu(y, 0.001, 0.15)), -21, -2)


def portes_fermeture():
    """Fermeture : quatre bips courts d'avertissement, puis la porte glisse (à BIPS s, 1,7 s comme l'animation), rentre
    dans son joint (coup ferme), le verrou claque et l'air s'échappe."""
    d = BIPS + MOUVEMENT_PORTE + 0.9
    t = temps(d)
    n = len(t)
    y = np.zeros(n)
    tb = temps(0.13)
    bip = (np.sin(2 * np.pi * 1046.5 * tb) + 0.12 * np.sin(2 * np.pi * 2093 * tb)
           + 0.04 * np.sin(2 * np.pi * 3139.5 * tb))
    bip = fondu(bip, 0.006, 0.012)
    bips = np.zeros(n)
    for k in range(4):
        ajouter(bips, bip, 0.3 * k)
    bips = filtrer(bips, lambda f: forme(f, [(250, -20), (600, -2), (1000, 0), (3500, -3), (7000, -24)]))
    y += 0.16 * bips
    tm = t - BIPS
    u = tm / MOUVEMENT_PORTE                                   # 0..1 pendant le mouvement
    glis = vitesse_lisse(u / (1 - 0.22))                       # retour sur ses galets (1 → 0,22)
    y += 0.28 * glissement(t, glis, 1001)
    y += 0.06 * bruit_filtre(n, 1002, [(1000, -20), (2500, 0), (6000, -6), (12000, -30)]) * glis
    rentree = vitesse_lisse((u - 0.78) / 0.22)                 # la porte rentre dans la caisse
    y += 0.12 * bruit_filtre(n, 1003, [(60, -20), (140, 0), (400, -10), (1200, -30)]) * rentree
    ajouter(y, butee(0.9, 1004, ferme=True), BIPS + MOUVEMENT_PORTE - 0.015)
    ajouter(y, 0.35 * choc([(640, 0.05, 1.0), (1310, 0.04, 0.6), (2380, 0.025, 0.35), (150, 0.08, 0.4)], 0.2,
                           (500, 4000, 0.002, 0.35), 1005), BIPS + MOUVEMENT_PORTE + 0.035)
    sf = souffle(0.6, 1006, 1500, 6500, 0.01, 0.12, 0.1)
    ajouter(y, 0.5 * sf, BIPS + MOUVEMENT_PORTE + 0.06)
    y = reverb(y, 0.45, 0.14, 0.012, 3000, 1007)
    y = passe_haut(y, 35)
    return niveau(couper_silence(fondu(y, 0.001, 0.15)), -21, -2)


def porte_interieure():
    """Porte coulissante entre les voitures ou du vestibule (0,55 s) : petit souffle, glissement, arrêt doux."""
    d = DUREE_PORTE_INT + 0.5
    t = temps(d)
    n = len(t)
    y = 0.35 * souffle(d, 1101, 1500, 6000, 0.006, 0.07, 0.1)
    vit = vitesse_lisse(t / DUREE_PORTE_INT)
    y += 0.4 * glissement(t, vit, 1102, (180, 1400), 70.0)
    ajouter(y, butee(0.35, 1103), DUREE_PORTE_INT - 0.01)
    y = reverb(y, 0.3, 0.12, 0.004, 2500, 1104)
    y = passe_haut(y, 50)
    return niveau(couper_silence(fondu(y, 0.001, 0.1)), -24, -3)


def roulement(duree=3.8):
    """Boucle de la cinématique (dans le tunnel, à bord) : roulement étouffé et joints réguliers, une paire d'essieux
    toutes les 0,95 s (les secousses de la cinématique tombent toutes les 19 images)."""
    t = temps(duree)
    n = len(t)
    a = voie(duree)
    b = cabine(duree)
    y = 0.5 * a / np.sqrt(np.mean(a * a)) + 0.8 * b / np.sqrt(np.mean(b * b))
    y = filtrer(y, lambda f: forme(f, [(30, 0), (1500, 0), (4000, -8), (10000, -24)]), True)
    base = np.sqrt(np.mean(y * y))
    for k in range(4):
        for j, dt in enumerate((0.0, 0.17)):
            c = joint(1200 + 2 * k + j)
            c = filtrer(c, lambda f: forme(f, [(30, 0), (1200, 0), (3000, -6), (8000, -20)]))
            ajouter(y, 2.6 * base * c / np.sqrt(np.mean(c[:int(0.1 * SR)] ** 2)) * (1.0 if j == 0 else 0.85),
                    0.95 * k + dt + 0.02, circulaire=True)
    y = passe_haut(y, 25, True)
    return niveau(y, -20, -2, boucle=True)


# nom du fichier -> (fonction, boucle ?)
SONS = {
    "voie": (voie, True),
    "cabine": (cabine, True),
    "moteur": (moteur, True),
    "roulement": (roulement, True),
    "joint_1": (lambda: joint(11), False),
    "joint_2": (lambda: joint(12), False),
    "joint_3": (lambda: joint(13), False),
    "joint_4": (lambda: joint(14), False),
    "attelage": (attelage, False),
    "depart": (depart, False),
    "klaxon": (klaxon, False),
    "freins": (freins, False),
    "detente": (detente, False),
    "carillon": (carillon, False),
    "portes_ouverture": (portes_ouverture, False),
    "portes_fermeture": (portes_fermeture, False),
    "porte_interieure": (porte_interieure, False),
}


# ------------------------------------------------------------------------------------------------ fichiers

def ffmpeg(x, chemin, codec, canaux=1):
    pcm = np.clip(x, -1, 1).astype("<f4").tobytes()
    cmd = ["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", str(canaux), "-i", "-",
           "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact"] + codec + [str(chemin)]
    subprocess.run(cmd, input=pcm, check=True)


# Silence posé devant chaque son ponctuel : sans lui, l'encodeur Vorbis « bave » un peu avant une attaque franche
# (choc, joint) et le fichier décodé ne commence plus à zéro (petit clic). 8 ms : imperceptible.
AVANCE = 0.008


def ecrire_ogg(x, chemin, boucle=False):
    if not boucle:
        x = np.concatenate([np.zeros(int(AVANCE * SR)), x])
    ffmpeg(x, chemin, ["-c:a", "libvorbis", "-q:a", "5"])


def ecrire_mp3(x, chemin, canaux=1):
    ffmpeg(x, chemin, ["-c:a", "libmp3lame", "-b:a", "192k" if canaux == 2 else "128k"], canaux)


def relire(chemin):
    raw = subprocess.run(["ffmpeg", "-v", "error", "-i", str(chemin), "-f", "f32le", "-ac", "1", "-ar", str(SR), "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype="<f4").astype(np.float64)


def controle(nom, x, boucle):
    """Contrôle du fichier relu : durée, sonie, crête, bouts (clic), couture d'une boucle."""
    pb = []
    crete = 20 * np.log10(np.max(np.abs(x)) + 1e-12)
    if crete > -0.5:
        pb.append("crête trop haute")
    if boucle:
        saut = abs(x[0] - x[-1])
        pente = np.median(np.abs(np.diff(x)))
        if saut > 8 * pente + 0.02:
            pb.append("couture : saut de %.3f" % saut)
    else:
        if abs(x[0]) > 0.01 or abs(x[-1]) > 0.01:
            pb.append("bout non nul (clic)")
    print("  %-17s %5.2f s  sonie %6.1f  crête %5.1f dBFS  %s" % (nom, len(x) / SR, sonie(x, boucle), crete,
                                                                 "; ".join(pb) if pb else "ok"))
    return not pb


# ------------------------------------------------------------------------------------------------ scènes d'écoute
# Petite simulation de ce que fait le jeu (TrainSonsClient), pour écouter les sons ensemble : un auditeur, la voie en
# ligne droite le long de x, la rame et ses essieux, l'atténuation linéaire du jeu (portée de sounds.json), la stéréo
# selon la direction. Approché : pas de son de Minecraft autour, pas d'effet Doppler.

RAME = [("loco", 20.0, 6.4)] + [("voiture", 23.0, 8.0)] * 6
ATTELAGE = 0.6
JOINT = 25.0


def decalages():
    longueur = sum(v[1] for v in RAME) + ATTELAGE * (len(RAME) - 1)
    avant, dec = longueur / 2, []
    for _, lg, _ in RAME:
        dec.append(avant - lg / 2)
        avant -= lg + ATTELAGE
    return dec


def essieux():
    """Abscisses des essieux par rapport au milieu de la rame (comme Horaire et TrainSonsClient)."""
    out = []
    for (genre, _, bog), c in zip(RAME, decalages()):
        ecarts = (-1.9, 0.0, 1.9) if genre == "loco" else (-1.25, 1.25)
        for b in (bog, -bog):
            for e in ecarts:
                out.append(c + b + e)
    return out


class Mixeur:
    def __init__(self, duree, auditeur):
        self.n = int(duree * SR)
        self.g = np.zeros(self.n)
        self.d = np.zeros(self.n)
        self.aud = np.array(auditeur, dtype=float)

    def gain_pan(self, p, portee, vol):
        v = np.array(p, dtype=float) - self.aud
        dist = np.linalg.norm(v)
        g = vol * max(0.0, 1 - dist / portee)
        az = np.arctan2(v[0], v[2] if abs(v[2]) > 1e-6 else 1e-6)     # l'auditeur regarde vers +z (la voie)
        pan = np.clip(np.sin(az), -1, 1) * 0.8
        return g * np.cos((pan + 1) * np.pi / 4), g * np.sin((pan + 1) * np.pi / 4)

    def poser(self, son, t0, p, portee, vol=1.0, pitch=1.0):
        if pitch != 1.0:
            idx = np.arange(0, len(son) - 1, pitch)
            son = np.interp(idx, np.arange(len(son)), son)
        gg, gd = self.gain_pan(p, portee, vol)
        i = int(t0 * SR)
        j = min(self.n, i + len(son))
        if j > i:
            self.g[i:j] += gg * son[:j - i]
            self.d[i:j] += gd * son[:j - i]

    def boucle(self, son, pos, vol, pitch, portee, pas=0.05):
        """pos, vol, pitch : fonctions du temps (s) ; recalculés à chaque « tick », comme dans le jeu."""
        ticks = np.arange(0, self.n / SR + pas, pas)
        gg, gd, pp = [], [], []
        for tk in ticks:
            a, b = self.gain_pan(pos(tk), portee, vol(tk))
            gg.append(a)
            gd.append(b)
            pp.append(pitch(tk))
        ts = np.arange(self.n) / SR
        rate = np.interp(ts, ticks, pp)
        ph = np.cumsum(rate) % len(son)
        sig = np.interp(ph, np.arange(len(son) + 1), np.concatenate([son, son[:1]]))
        self.g += sig * np.interp(ts, ticks, gg)
        self.d += sig * np.interp(ts, ticks, gd)

    def stereo(self):
        y = np.stack([self.g, self.d], axis=1)
        y = y / (np.max(np.abs(y)) + 1e-9) * 10 ** (-1.5 / 20)
        return y.reshape(-1)


def regimes(vit, a_quai, duree, pas=0.05):
    """Régime du moteur tick par tick, comme TrainSonsClient (effort lissé, ralenti à quai et au freinage)."""
    g, acc, out = 0.0, 0.0, []
    for tk in np.arange(0, duree + pas, pas):
        acc += ((vit(tk) - vit(tk - pas)) / pas - acc) * 0.2
        cible = 0.0 if a_quai(tk) else 1.0 if acc > 0.08 else 0.12 if acc < -0.08 else 0.55
        g += (cible - g) * 0.04
        out.append(g)
    out = np.array(out)
    return lambda t: out[min(len(out) - 1, max(0, int(t / pas)))]


def joints_scene(m, sons, s_milieu, vitesse, duree, point, a_bord=False, graine=5):
    """Chaque essieu qui passe un joint de rail joue un « joint » à sa place (comme TrainSonsClient) ; point(abscisse
    de l'essieu, abscisse du milieu de la rame) donne sa position."""
    rng = np.random.default_rng(graine)
    ts = np.arange(0, duree, 0.002)
    ss = np.array([s_milieu(x) for x in ts])
    for e in essieux():
        k = np.floor((ss + e) / JOINT)
        for i in np.nonzero(np.diff(k) > 0)[0]:
            tc = ts[i + 1]
            v = vitesse(tc)
            if v < 0.8:
                continue
            vol = min(1.0, 0.3 + v / 12.0)
            pitch = (0.85 if a_bord else 1.0) * rng.uniform(0.94, 1.06)
            m.poser(sons["joint_%d" % rng.integers(1, 5)], tc, point(ss[i + 1] + e, ss[i + 1]), 40, vol, pitch)


def scene_gare(sons):
    """Sur le quai, à 4,5 blocs de l'axe de la voie, vers l'avant de la rame : arrivée (freins, arrêt, air, carillon),
    ouverture des portes, puis (le temps à quai raccourci) bips et fermeture, avertisseur, départ."""
    ACC, DEC = 0.45, 0.55
    A = 16.0                     # arrivée (le son des freins part 5,5 s avant)
    OUV = A + 3.0                # Horaire.OUVERTURE
    FERM = 27.0                  # portes_fermeture : 11,1 s avant le départ (8 + 1,7 + 1,4)
    DEP = FERM + 11.1
    FIN = DEP + 15.0
    dec = decalages()

    def s_milieu(t):
        if t < A:
            return -0.5 * DEC * (A - t) ** 2
        return 0.0 if t < DEP else 0.5 * ACC * (t - DEP) ** 2

    def vit(t):
        return DEC * (A - t) if t < A else (0.0 if t < DEP else ACC * (t - DEP))

    aud = (30.0, 1.6, -4.5)
    m = Mixeur(FIN, aud)

    def proche(t):
        s = s_milieu(t)
        return (float(np.clip(aud[0], s + dec[-1] - 10.5, s + dec[0] + 9)), 1.5, 0.0)

    m.boucle(sons["voie"], proche, lambda t: min(1.0, vit(t) / 12.0) ** 1.2,
             lambda t: 0.6 + 0.5 * min(1.0, vit(t) / 15.0), 72)
    reg = regimes(vit, lambda t: A <= t < DEP, FIN)
    m.boucle(sons["moteur"], lambda t: (s_milieu(t) + dec[0], 2.5, 0.0), lambda t: 0.45 + 0.55 * reg(t),
             lambda t: 0.8 + 0.35 * reg(t), 64)
    joints_scene(m, sons, s_milieu, vit, FIN, lambda sa, sm: (sa, 0.6, 0.0))
    m.poser(sons["klaxon"], 2.0, (s_milieu(2.0) + dec[0], 3.5, 0.0), 128, 1.0, 0.97)   # (25 s avant : ici plus tard)
    m.poser(sons["freins"], A - FREINAGE, proche(A - 2.5), 48)
    m.poser(sons["detente"], A + 0.6, (proche(A)[0], 0.6, 0.0), 40)
    m.poser(sons["detente"], A + 0.8, (dec[0], 2.5, 0.0), 40, 0.8, 0.94)
    m.poser(sons["carillon"], A + 1.5, (aud[0] + 4, 3.2, -4.5), 48, 0.9)       # haut-parleur du quai
    portes = sorted([c + dx for c in dec[1:] for dx in (9.6, -9.6)], key=lambda x: abs(x - aud[0]))[:3]
    for k, x in enumerate(portes):
        p = (x, 2.42, -2.25)
        pitch = (1.0, 0.985, 1.015)[k]
        m.poser(sons["portes_ouverture"], OUV, p, 28, (1.0, 0.7, 0.5)[k], pitch)
        m.poser(sons["portes_fermeture"], FERM, p, 28, (1.0, 0.7, 0.5)[k], pitch)
    m.poser(sons["klaxon"], DEP - 2.0, (dec[0], 3.5, 0.0), 128, 1.0)
    m.poser(sons["depart"], DEP, (dec[0], 2.5, 0.0), 64, 1.0)
    for i in range(len(RAME) - 1):
        x = dec[i] - RAME[i][1] / 2 - ATTELAGE / 2
        m.poser(sons["attelage"], DEP + 0.6 + 0.2 * i, (x, 1.2, 0.0), 40, 0.85, (1.0, 0.97, 1.03)[i % 3])
    return m.stereo()


def scene_a_bord(sons):
    """Debout au milieu de la 4e voiture (repère de la voiture : tout ce qui est sur le train reste à sa place) : pleine
    vitesse, puis l'arrivée (freins, arrêt, air), les portes de la voiture, une porte intérieure qu'on passe."""
    A = 24.0
    DEC = 0.55
    duree = A + 7.0
    dec = decalages()
    c = dec[4]

    def vit(t):
        return min(15.0, DEC * max(0.0, A - t))

    tt = np.arange(0, duree + 0.002, 0.002)
    s = np.cumsum([vit(x) for x in tt]) * 0.002

    def s_milieu(t):
        return float(np.interp(t, tt, s)) + 3.0

    m = Mixeur(duree, (0.0, 2.0, 0.0))
    m.boucle(sons["cabine"], lambda t: (0.0, 2.0, 0.0), lambda t: 0.7 * min(1.0, vit(t) / 10.0),
             lambda t: 0.75 + 0.35 * min(1.0, vit(t) / 15.0), 1e9)
    m.boucle(sons["voie"], lambda t: (0.0, 1.5, 1.0), lambda t: 0.3 * min(1.0, vit(t) / 12.0) ** 1.2,
             lambda t: 0.6 + 0.5 * min(1.0, vit(t) / 15.0), 72)
    joints_scene(m, sons, s_milieu, vit, duree, lambda sa, sm: (sa - sm - c, 0.3, 0.0), a_bord=True, graine=9)
    m.poser(sons["porte_interieure"], 3.0, (7.3, 2.4, 0.0), 12, 0.8, 1.0)
    m.poser(sons["porte_interieure"], 4.4, (7.3, 2.4, 0.0), 12, 0.8, 0.93)
    m.poser(sons["freins"], A - FREINAGE, (0.0, 1.5, 0.0), 48)
    m.poser(sons["detente"], A + 0.6, (0.0, 0.6, 0.0), 40)
    m.poser(sons["portes_ouverture"], A + 3.0, (9.6, 2.42, 2.25), 28, 1.0)
    m.poser(sons["portes_ouverture"], A + 3.0, (-9.6, 2.42, 2.25), 28, 0.7, 0.985)
    m.poser(sons["carillon"], A + 1.5, (0.0, 4.6, 6.0), 48, 0.9)
    return m.stereo()


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    ap.add_argument("--seul", help="noms séparés par des virgules")
    ap.add_argument("--ecoute", help="dossier des copies d'écoute (mp3) et des scènes")
    a = ap.parse_args()
    noms = a.seul.split(",") if a.seul else list(SONS)
    inconnus = [x for x in noms if x not in SONS]
    if inconnus:
        sys.exit("sons inconnus : " + ", ".join(inconnus))
    SORTIE.mkdir(parents=True, exist_ok=True)
    print("Sons du train ->", SORTIE)
    ok = True
    calcules = {}
    for nom in noms:
        fn, boucle = SONS[nom]
        x = fn()
        calcules[nom] = x
        chemin = SORTIE / (nom + ".ogg")
        ecrire_ogg(x, chemin, boucle)
        ok &= controle(nom, relire(chemin), boucle)
    if a.ecoute:
        dos = Path(a.ecoute)
        dos.mkdir(parents=True, exist_ok=True)
        for nom in SONS:
            if nom not in calcules:
                calcules[nom] = relire(SORTIE / (nom + ".ogg"))
        for nom in noms:
            x = calcules[nom]
            if SONS[nom][1]:
                x = np.tile(x, max(2, int(np.ceil(8.0 / (len(x) / SR)))))      # une boucle : 8 s environ
            ecrire_mp3(x, dos / (nom + ".mp3"))
        ecrire_mp3(scene_gare(calcules), dos / "scene_arret_en_gare.mp3", 2)
        ecrire_mp3(scene_a_bord(calcules), dos / "scene_a_bord.mp3", 2)
        print("Copies d'écoute ->", dos)
    if not ok:
        sys.exit("des sons ont un défaut (voir ci-dessus)")


if __name__ == "__main__":
    main()
