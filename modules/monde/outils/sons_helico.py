#!/usr/bin/env python3
"""Sons de l'hélicoptère de l'événement « hélico », tous fabriqués par le calcul (aucun enregistrement).

    python modules/monde/outils/sons_helico.py                   refait les .ogg du mod
    python modules/monde/outils/sons_helico.py --ecoute DOSSIER  écrit aussi des copies .wav pour les écouter

Besoins : Python 3.11+, numpy, soundfile (pip install soundfile : il écrit le Vorbis lui-même, sans ffmpeg).
Sortie : src/client/resources/assets/airstrike/sounds/helico_*.ogg (mono : sinon le jeu ne les atténue pas avec la
distance ; 44,1 kHz ; Vorbis). Hasard à graine fixe : mêmes sons d'une fois sur l'autre.

- helico_rotor (boucle de 2 s) : claquement des pales (14 passages par seconde), sifflement de la turbine, bourdonnement
  du rotor de queue, grondement grave ; les boucles sont filtrées « en rond » (FFT) : aucune couture audible ;
- helico_rotor_loin (boucle de 2 s) : le même, entendu de loin (seulement les claquements graves et le grondement) ;
- helico_tir : rafale de minigun (50 coups par seconde, ~0,9 s), moteur des canons qui lance et s'arrête ;
- helico_alerte : alerte de verrouillage chez la cible (bips rapides, 1,2 s) ;
- helico_alarme (boucle de 1,5 s) : alarme de la cabine quand l'hélicoptère est en feu ;
- helico_crash : crash entendu de près (~3 s) : pales qui frappent le sol, tôle broyée, explosion saturée, quelques
  débris, écho du terrain ; seulement des bruits filtrés et saturés (aucun son pur : demande du propriétaire).
On modifie ce script, jamais les .ogg.
"""
import argparse
from pathlib import Path

import numpy as np

SR = 44100
ICI = Path(__file__).resolve().parent
SORTIE = ICI.parents[0] / "src" / "client" / "resources" / "assets" / "airstrike" / "sounds"


def t_(duree):
    return np.arange(int(round(duree * SR))) / SR


def filtre_rond(x, bas=None, haut=None, pente=4):
    """Filtre passe-bande dans le domaine de Fourier, sur un signal périodique (la fin rejoint le début)."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.ones_like(f)
    if haut:
        g *= 1 / (1 + (f / haut) ** (2 * pente))
    if bas:
        g *= 1 / (1 + (bas / np.maximum(f, 1e-6)) ** (2 * pente))
    return np.fft.irfft(X * g, len(x))


def filtre(x, bas=None, haut=None, pente=4):
    """Même filtre sur un son ponctuel : remplissage de zéros pour ne pas replier la fin sur le début."""
    n = len(x)
    y = filtre_rond(np.concatenate([x, np.zeros(n)]), bas, haut, pente)
    return y[:n]


def normer(x, crete=0.89):
    m = np.max(np.abs(x))
    return x * (crete / m) if m > 0 else x


def enveloppe(t, attaque, decroit):
    return np.where(t < attaque, t / max(attaque, 1e-6), np.exp(-(t - attaque) / decroit))


def periodique(n, periode_ech, motif):
    """Répète un motif tous les periode_ech échantillons sur n échantillons, en rond (le motif peut déborder)."""
    out = np.zeros(n)
    k = 0
    while k * periode_ech < n:
        i0 = int(round(k * periode_ech))
        idx = (i0 + np.arange(len(motif))) % n
        np.add.at(out, idx, motif)
        k += 1
    return out


# ------------------------------------------------------------------------------------------------ sons
def rotor(loin=False):
    duree, chops = 2.0, 28                    # 14 passages de pale par seconde, nombre entier dans la boucle
    n = int(duree * SR)
    rng = np.random.default_rng(11 if not loin else 12)
    per = n / chops
    tm = np.arange(int(0.09 * SR)) / SR
    # claquement d'une pale : bouffée de bruit grave + « wop » (sinus qui descend)
    bouffee = rng.normal(0, 1, len(tm)) * enveloppe(tm, 0.004, 0.022)
    wop = np.sin(2 * np.pi * (95 * tm - 160 * tm * tm)) * enveloppe(tm, 0.006, 0.035)
    motif = 0.55 * bouffee + 1.0 * wop
    chop = periodique(n, per, motif)
    # pales légèrement inégales : une sur deux un peu plus forte (le « whop-whop » des hélicoptères)
    alt = periodique(n, per * 2, 0.25 * motif)
    chop = filtre_rond(chop + alt, 30, 900 if not loin else 260)
    t = np.arange(n) / SR
    grondement = filtre_rond(rng.normal(0, 1, n), 20, 140, 2) * 0.9
    if loin:
        x = 1.0 * chop + 0.55 * grondement
        return normer(x, 0.8)
    # turbine : sifflement aigu (fréquences choisies pour boucler : nombre entier de périodes en 2 s)
    sifflet = sum(a * np.sin(2 * np.pi * f * t) for f, a in ((2205.0, 0.05), (4410.0, 0.02), (3307.5, 0.015)))
    souffle = filtre_rond(rng.normal(0, 1, n), 900, 5200, 2) * 0.10
    # rotor de queue : bourdonnement à 79 Hz (158 périodes en 2 s) riche en harmoniques
    queue = sum(np.sin(2 * np.pi * 79.0 * k * t) / k for k in range(1, 9)) * 0.10
    queue = filtre_rond(queue, 60, 700)
    x = 1.0 * chop + 0.45 * grondement + sifflet + souffle + queue
    return normer(x)


def tir():
    duree = 1.25
    n = int(duree * SR)
    t = np.arange(n) / SR
    rng = np.random.default_rng(21)
    x = np.zeros(n)
    tm = np.arange(int(0.05 * SR)) / SR
    debut, fin, cadence = 0.10, 0.95, 50.0      # rafale de 0,85 s à 50 coups par seconde
    k = 0
    while True:
        tc = debut + k / cadence
        if tc > fin:
            break
        i0 = int(tc * SR)
        claque = rng.normal(0, 1, len(tm)) * enveloppe(tm, 0.0008, 0.006)
        boum = np.sin(2 * np.pi * (140 + rng.uniform(-10, 10)) * tm) * enveloppe(tm, 0.001, 0.012)
        motif = 0.7 * claque + 0.8 * boum
        j = min(n, i0 + len(motif))
        x[i0:j] += motif[:j - i0]
        k += 1
    x = filtre(x, 60, 7000)
    # moteur électrique des canons : monte au départ, redescend après la rafale
    vit = np.clip((t - 0.0) / 0.12, 0, 1) * np.clip((1.10 - t) / 0.25, 0, 1)
    phase = 2 * np.pi * np.cumsum(180 + 420 * vit) / SR
    moteur = (np.sin(phase) + 0.4 * np.sin(2 * phase)) * vit * 0.08
    # écho (réverbération courte du terrain)
    echo = np.zeros(n)
    for retard, gain in ((0.045, 0.30), (0.11, 0.18), (0.21, 0.10)):
        d = int(retard * SR)
        echo[d:] += x[:-d] * gain
    y = x + filtre(echo, 80, 2500) + moteur
    queue_ = np.clip((duree - t) / 0.08, 0, 1)
    return normer(y * queue_)


def alerte():
    duree = 1.2
    t = t_(duree)
    on = (np.floor(t / 0.075) % 2 == 0).astype(float)
    # bords adoucis (pas de clic)
    on = filtre(on, None, 400, 2)
    f = np.where(np.floor(t / 0.3) % 2 == 0, 1250.0, 1650.0)
    phase = 2 * np.pi * np.cumsum(f) / SR
    carre = np.tanh(3 * np.sin(phase)) * 0.6
    x = carre * on * np.clip((duree - t) / 0.02, 0, 1)
    return normer(filtre(x, 300, 6000), 0.45)


def alarme():
    duree = 1.5                               # boucle : 3 alternances complètes
    t = t_(duree)
    f = np.where(np.floor(t / 0.25) % 2 == 0, 700.0, 940.0)
    # fréquences choisies pour revenir à la même phase en fin de boucle (nombre entier de périodes par tranche)
    phase = 2 * np.pi * np.cumsum(f) / SR
    x = np.tanh(2.2 * np.sin(phase)) * 0.5
    bords = np.minimum(1, np.minimum((t % 0.25) / 0.006, (0.25 - t % 0.25) / 0.006))
    return normer(filtre_rond(x * bords, 250, 5000), 0.38)


# ------------------------------------------------------------------ crash (3e version, 25/10 : bruits travaillés, saturés)
# Demande du propriétaire : rien de « synthé » (aucun son pur : seulement des bruits filtrés et saturés, comme une prise
# de son de terrain), plus puissant, plus court et plus net (environ 3 s).
def poser(x, i0, m, gain=1.0):
    """Ajoute le son m dans x à partir de l'échantillon i0 (coupé au bout de x)."""
    if i0 >= len(x):
        return
    j = min(len(x), i0 + len(m))
    x[i0:j] += gain * m[:j - i0]


def bande(x, f, q):
    """Passe-bande résonant (ordre 2) centré sur f, de largeur f / q."""
    from scipy.signal import butter, sosfilt
    lo, hi = f * (1 - 0.5 / q), f * (1 + 0.5 / q)
    sos = butter(2, [max(lo, 20), min(hi, SR / 2.2)], btype="band", fs=SR, output="sos")
    return sosfilt(sos, x)


def somme(*morceaux):
    """Somme de sons de longueurs différentes (complétés par du silence)."""
    y = np.zeros(max(len(m) for m in morceaux))
    for m in morceaux:
        y[:len(m)] += m
    return y


def grain(rng, duree, decroit):
    """Bouffée de bruit à attaque nette (contact d'un choc)."""
    t = np.arange(int(duree * SR)) / SR
    return rng.normal(0, 1, len(t)) * np.exp(-t / decroit) * np.clip(t / 0.0004, 0, 1)


def passe_bas_variable(x, fc):
    """Passe-bas d'ordre 2 à fréquence de coupure variable fc[n] (le souffle d'une explosion qui se referme)."""
    a = 1 - np.exp(-2 * np.pi * np.clip(fc, 10, SR / 2.2) / SR)
    y = np.empty_like(x)
    p1 = p2 = 0.0
    for i in range(len(x)):
        p1 += a[i] * (x[i] - p1)
        p2 += a[i] * (p1 - p2)
        y[i] = p2
    return y


def sature(x, force):
    """Saturation douce (micro de terrain qui sature sur une explosion : c'est ce qui donne la puissance)."""
    m = np.max(np.abs(x)) or 1.0
    return np.tanh(force * x / m) / np.tanh(force)


def crash():
    """Crash de l'hélicoptère entendu de près (3,2 s), uniquement des bruits filtrés et saturés :
    1. quatre coups lourds des pales sur le sol, de plus en plus espacés ;
    2. le fuselage qui s'écrase : coup sourd et tôle broyée (nuée de petits chocs de bruit résonant, saturée) ;
    3. l'explosion : onde de choc grave, souffle qui se referme, grondement court, fortement saturée ;
    4. quelques débris qui retombent ;
    5. l'écho du terrain (deux échos nets puis une courte queue)."""
    duree = 3.2
    n = int(duree * SR)
    t = np.arange(n) / SR
    rng = np.random.default_rng(90210)
    x = np.zeros(n)

    # 1. pales : coups lourds et sourds (terre et métal), sans aucune résonance pure
    for k, (tp, g) in enumerate(((0.00, 0.85), (0.085, 0.70), (0.19, 0.55), (0.33, 0.42))):
        coup = somme(filtre(grain(rng, 0.12, 0.022), 60, 1100) * 1.6, filtre(grain(rng, 0.25, 0.06), 30, 160) * 2.4,
                     filtre(grain(rng, 0.04, 0.006), 900, 5000) * 0.8)
        poser(x, int(tp * SR), sature(coup, 2.0), g)

    # 2. fuselage au sol : coup sourd + tôle broyée (petits chocs de bruit passés dans des résonances larges)
    T0 = 0.12
    poser(x, int(T0 * SR), sature(somme(filtre(grain(rng, 0.5, 0.11), 25, 220) * 3, filtre(grain(rng, 0.2, 0.03), 200, 2500)), 2.5), 1.1)
    broyage = np.zeros(int(0.6 * SR))
    for _ in range(140):
        tc = rng.gamma(1.6, 0.06)
        if tc >= 0.55:
            continue
        g_ = grain(rng, rng.uniform(0.01, 0.05), rng.uniform(0.003, 0.012))
        f = rng.uniform(160, 2600)
        poser(broyage, int(tc * SR), bande(g_, f, rng.uniform(3, 9)), rng.uniform(0.3, 1.0) * np.exp(-tc / 0.25))
    broyage += filtre(rng.normal(0, 1, len(broyage)), 300, 4000) * np.exp(-np.arange(len(broyage)) / SR / 0.08) * 0.25
    poser(x, int(T0 * SR), sature(broyage, 3.0), 0.75)

    # 3. explosion : onde de choc (une seule poussée grave), souffle dont l'aigu se referme, grondement
    TB = 0.36
    tb = np.clip(t - TB, 0, None)
    actif = (t >= TB).astype(float)
    onde = filtre(np.where((tb > 0) & (tb < 0.004), 1.0, 0.0) * actif, 15, 140, 2)
    onde = onde / (np.max(np.abs(onde)) or 1)
    souffle = rng.normal(0, 1, n) * actif * np.clip(tb / 0.0015, 0, 1) * (np.exp(-tb / 0.07) + 0.45 * np.exp(-tb / 0.55))
    fc = 220 + 9000 * np.exp(-tb / 0.06) + 1400 * np.exp(-tb / 0.35)
    souffle = passe_bas_variable(souffle, fc)
    grond = filtre(rng.normal(0, 1, n), 20, 95, 2) * actif * np.clip(tb / 0.01, 0, 1) * np.exp(-tb / 0.75)
    explosion = sature(1.6 * onde + 1.0 * souffle / (np.max(np.abs(souffle)) or 1) + 1.3 * grond / (np.max(np.abs(grond)) or 1), 3.5)
    x += 1.7 * explosion

    # 4. quelques débris (bouffées de bruit résonant, sans tintement), terre qui retombe
    for _ in range(16):
        tc = TB + 0.15 + rng.exponential(0.45)
        if tc > duree - 0.5:
            continue
        g_ = grain(rng, 0.05, rng.uniform(0.004, 0.015))
        poser(x, int(tc * SR), bande(g_, rng.uniform(700, 3500), rng.uniform(2, 5)), rng.uniform(0.05, 0.16) * np.exp(-(tc - TB) / 0.8))
    terre = np.zeros(n)
    for _ in range(60):
        tc = TB + 0.1 + rng.exponential(0.35)
        poser(terre, int(tc * SR), filtre(grain(rng, 0.02, 0.004), 300, 3000), rng.uniform(0.03, 0.10))
    x += terre

    # 5. écho du terrain : deux échos nets (étouffés) puis une courte queue
    y = x.copy()
    for retard, g in ((0.17, 0.32), (0.41, 0.18)):
        d = int(retard * SR)
        y[d:] += filtre(x, 40, 1600)[:-d] * g
    rng2 = np.random.default_rng(7)
    lt = np.arange(int(0.9 * SR)) / SR
    ir = filtre(rng2.normal(0, 1, len(lt)) * np.exp(-lt / 0.22), 60, 2200)
    ir /= np.sqrt(np.sum(ir ** 2))
    taille = 1 << (n + len(ir) - 1).bit_length()
    queue = np.fft.irfft(np.fft.rfft(x, taille) * np.fft.rfft(ir, taille), taille)[:n]
    y += queue * (0.12 * np.max(np.abs(x)) / (np.max(np.abs(queue)) or 1))

    y = filtre(y, 24, None, 2)
    y = sature(y, 1.9)
    return normer(y * np.clip((duree - t) / 0.45, 0, 1), 0.95)



SONS = {
    "helico_rotor": (lambda: rotor(False), True),
    "helico_rotor_loin": (lambda: rotor(True), True),
    "helico_tir": (tir, False),
    "helico_alerte": (alerte, False),
    "helico_alarme": (alarme, True),
    "helico_crash": (crash, False),
}


def main():
    import soundfile as sf
    ap = argparse.ArgumentParser()
    ap.add_argument("--ecoute", type=Path)
    ap.add_argument("--sortie", type=Path, default=SORTIE)
    a = ap.parse_args()
    a.sortie.mkdir(parents=True, exist_ok=True)
    for nom, (f, boucle) in SONS.items():
        x = f()
        if not boucle:
            x = np.concatenate([np.zeros(int(0.008 * SR)), x])    # pas de clic à l'attaque après l'encodage
        sf.write(a.sortie / (nom + ".ogg"), x.astype(np.float32), SR, format="OGG", subtype="VORBIS")
        if a.ecoute:
            a.ecoute.mkdir(parents=True, exist_ok=True)
            sf.write(a.ecoute / (nom + ".wav"), np.tile(x, 3 if boucle else 1).astype(np.float32), SR)
        rms = float(np.sqrt(np.mean(x ** 2)))
        print("[sons] %-18s %.2f s  crête %.2f  efficace %.3f%s" % (nom, len(x) / SR, np.max(np.abs(x)), rms, "  (boucle)" if boucle else ""))


if __name__ == "__main__":
    main()
