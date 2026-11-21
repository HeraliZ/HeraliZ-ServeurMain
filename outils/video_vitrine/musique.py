#!/usr/bin/env python3
"""Musique de la vitrine des skins : 92 BPM, la mineur, une mesure par arme (2,609 s), entièrement synthétisée ici
(aucun échantillon, aucun droit d'auteur). Mesures : 0 intro (montée), 1 à 9 les armes (impacts aux changements de skin :
mesures 1, 4, 6, 8), 10 et la moitié de 11 : fin (impact, nappe qui s'éteint).

    python3 musique.py sortie.wav
"""
import math
import sys
import wave

import numpy as np

SR = 44100
BPM = 92.0
TEMPS = 60.0 / BPM
MESURE = 4 * TEMPS
NB_MESURES = 11.5
DUREE = NB_MESURES * MESURE
N = int(DUREE * SR) + SR * 3  # queue de réverbération

rng = np.random.default_rng(7)


def note(nom):
    """« A2 », « C#4 »… -> Hz."""
    noms = {"C": -9, "C#": -8, "D": -7, "D#": -6, "E": -5, "F": -4, "F#": -3, "G": -2, "G#": -1, "A": 0, "A#": 1, "B": 2}
    n, o = (nom[:2], int(nom[2:])) if nom[1] == "#" else (nom[:1], int(nom[1:]))
    return 440.0 * 2 ** ((noms[n] + 12 * (o - 4)) / 12)


def filtre(x, bas=None, haut=None, ordre=4):
    """Passe-bas / passe-haut doux par la FFT (traitement hors ligne)."""
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    g = np.ones_like(f)
    if bas:
        g *= 1 / np.sqrt(1 + (f / bas) ** (2 * ordre))
    if haut:
        g *= 1 / np.sqrt(1 + (haut / np.maximum(f, 1e-3)) ** (2 * ordre))
    return np.fft.irfft(X * g, n=len(x))


def convolution(x, ir):
    n = len(x) + len(ir) - 1
    m = 1 << (n - 1).bit_length()
    y = np.fft.irfft(np.fft.rfft(x, m) * np.fft.rfft(ir, m), m)[:len(x)]
    return y


def reverb_ir(duree, decroissance, graine):
    r = np.random.default_rng(graine)
    t = np.arange(int(duree * SR)) / SR
    ir = r.standard_normal(len(t)) * np.exp(-t / decroissance)
    ir = filtre(ir, bas=6000)
    ir[:int(0.012 * SR)] *= np.linspace(0, 1, int(0.012 * SR))
    return ir / np.sqrt(np.sum(ir ** 2))


def poser(piste, son, debut_s, gain=1.0):
    i = int(debut_s * SR)
    if i >= len(piste):
        return
    j = min(len(piste), i + len(son))
    piste[i:j] += son[:j - i] * gain


def kick():
    t = np.arange(int(0.55 * SR)) / SR
    f = 46 + 120 * np.exp(-t / 0.035)
    ph = 2 * np.pi * np.cumsum(f) / SR
    s = np.sin(ph) * np.exp(-t / 0.22)
    clic = rng.standard_normal(len(t)) * np.exp(-t / 0.004) * 0.35
    return np.tanh(1.6 * (s + filtre(clic, bas=4000)))


def sub(freq, duree):
    t = np.arange(int(duree * SR)) / SR
    glisse = freq * (1 + 0.6 * np.exp(-t / 0.03))
    ph = 2 * np.pi * np.cumsum(glisse) / SR
    env = np.minimum(1, t / 0.005) * np.exp(-t / (duree * 0.55))
    return np.tanh(2.2 * np.sin(ph)) * env


def clap():
    t = np.arange(int(0.45 * SR)) / SR
    bruit = rng.standard_normal(len(t))
    env = np.zeros_like(t)
    for k, d in enumerate((0.0, 0.011, 0.023)):
        env += (t >= d) * np.exp(-np.maximum(t - d, 0) / (0.008 if k < 2 else 0.16))
    return filtre(bruit * env, bas=7000, haut=900)


def charleston(ouvert=False):
    t = np.arange(int((0.25 if ouvert else 0.06) * SR)) / SR
    s = rng.standard_normal(len(t)) * np.exp(-t / (0.09 if ouvert else 0.018))
    return filtre(s, haut=7000, bas=15000)


def nappe(freqs, duree):
    """Accord de dents de scie désaccordées (somme d'harmoniques, sans repliement)."""
    t = np.arange(int(duree * SR)) / SR
    s = np.zeros_like(t)
    for f0 in freqs:
        for desac in (-0.12, 0.0, 0.11):
            f = f0 * 2 ** (desac / 12)
            nh = int(min(40, 5000 / f))
            k = np.arange(1, nh + 1)[:, None]
            s += (np.sin(2 * np.pi * f * k * t[None, :] + rng.uniform(0, 6.28)) / k).sum(axis=0)
    env = np.minimum(1, t / 0.35) * np.minimum(1, (duree - t) / 0.25).clip(0, 1)
    return s * env / (len(freqs) * 3)


def cloche(freq, duree=1.2):
    t = np.arange(int(duree * SR)) / SR
    s = (np.sin(2 * np.pi * freq * t) + 0.35 * np.sin(2 * np.pi * freq * 2.76 * t) * np.exp(-t / 0.12)
         + 0.2 * np.sin(2 * np.pi * freq * 5.4 * t) * np.exp(-t / 0.05))
    return s * np.exp(-t / 0.45) * np.minimum(1, t / 0.002)


def impact():
    t = np.arange(int(2.2 * SR)) / SR
    boum = np.sin(2 * np.pi * np.cumsum(38 + 50 * np.exp(-t / 0.08)) / SR) * np.exp(-t / 0.9)
    souffle = filtre(rng.standard_normal(len(t)), bas=2500) * np.exp(-t / 0.35) * 0.5
    return np.tanh(1.4 * (boum + souffle))


def montee(duree):
    """Bruit qui s'ouvre et monte (par blocs, filtre qui s'ouvre) + sinus qui grimpe."""
    n = int(duree * SR)
    s = np.zeros(n)
    bloc = 2048
    for i in range(0, n, bloc):
        j = min(n, i + bloc)
        p = i / n
        b = rng.standard_normal(j - i)
        s[i:j] = filtre(np.concatenate([b, b[::-1]]), bas=300 + 9000 * p ** 2, haut=150)[:j - i] * (0.15 + 0.85 * p ** 2)
    t = np.arange(n) / SR
    s += np.sin(2 * np.pi * np.cumsum(220 * 2 ** (2.5 * t / duree)) / SR) * (t / duree) ** 2 * 0.25
    return s


def cymbale_inverse(duree):
    t = np.arange(int(duree * SR)) / SR
    c = filtre(rng.standard_normal(len(t)), haut=5000) * np.exp(-t / (duree * 0.45))
    return c[::-1]


def main():
    sortie = sys.argv[1] if len(sys.argv) > 1 else "musique.wav"
    G = {k: np.zeros(N) for k in ("kick", "sub", "clap", "hat", "nappe", "cloche", "fx")}
    accords = {  # mesure -> (fondamentale du sub, notes de la nappe, arpège)
        "Am": ("A1", ["A2", "C3", "E3", "A3"], ["A4", "C5", "E5", "A5"]),
        "F": ("F1", ["F2", "A2", "C3", "F3"], ["F4", "A4", "C5", "F5"]),
        "C": ("C2", ["C3", "E3", "G3", "C4"], ["G4", "C5", "E5", "G5"]),
        "G": ("G1", ["G2", "B2", "D3", "G3"], ["G4", "B4", "D5", "G5"]),
        "Dm": ("D2", ["D3", "F3", "A3", "D4"], ["F4", "A4", "D5", "F5"]),
        "E": ("E2", ["E3", "G#3", "B3", "E4"], ["E4", "G#4", "B4", "E5"]),
    }
    suite = ["Am", "Am", "F", "C", "G", "Am", "F", "Dm", "E", "Am", "Am", "Am"]
    pas = TEMPS / 4  # double croche

    for m in range(12):
        debut = m * MESURE
        if debut >= DUREE:
            break
        nom = suite[m]
        racine, nap, arp = accords[nom]
        long = MESURE if m < 11 else MESURE * 0.5 + 2.0
        poser(G["nappe"], nappe([note(x) for x in nap], long + 0.3), debut, 1.0 if m > 0 else 0.7)
        if m == 0:
            continue
        if 1 <= m <= 9:
            # grosse caisse (demi-temps) et sub sur ses coups
            for k in (0, 6, 10) if m % 2 else (0, 10, 13):
                poser(G["kick"], kick(), debut + k * pas)
                poser(G["sub"], sub(note(racine), 0.55 if k else 1.1), debut + k * pas)
            poser(G["clap"], clap(), debut + 8 * pas)
            # charleston en croches, roulement en doubles croches sur le dernier temps une mesure sur deux
            for k in range(0, 16, 2):
                poser(G["hat"], charleston(ouvert=(k == 14 and m % 2 == 0)), debut + k * pas, 0.8 if k % 4 else 1.0)
            if m % 2 == 0:
                for k in (13, 15):
                    poser(G["hat"], charleston(), debut + k * pas, 0.6)
            # arpège de cloches (scintillement des skins), plus dense vers la fin
            if m >= 2:
                motif = [0, 2, 1, 3, 2, 1, 3, 2] if m < 6 else [0, 1, 2, 3, 2, 1, 2, 3]
                for k, idx in enumerate(motif):
                    poser(G["cloche"], cloche(note(arp[idx])), debut + k * 2 * pas, 0.9 if k % 2 == 0 else 0.6)
        if m == 10:
            poser(G["kick"], kick(), debut)
            poser(G["sub"], sub(note("A1"), 2.4), debut)

    # montée de l'intro, impacts aux changements de skin (mesures 1, 4, 6, 8) et à la fin (10)
    poser(G["fx"], montee(MESURE), 0.0, 0.8)
    for m in (1, 4, 6, 8, 10):
        poser(G["fx"], impact(), m * MESURE, 0.9 if m in (1, 10) else 0.6)
        if m > 1:
            poser(G["fx"], cymbale_inverse(TEMPS * 2), m * MESURE - TEMPS * 2, 0.5)

    # mélange (réverbération sur la nappe, les cloches, le clap et les effets)
    ir_l, ir_r = reverb_ir(2.2, 0.55, 1), reverb_ir(2.2, 0.55, 2)
    nap = filtre(G["nappe"], bas=1400, haut=90)
    clo = filtre(G["cloche"], bas=9000)
    sec = 1.0 * G["kick"] + 0.75 * G["sub"] + 0.34 * G["clap"] + 0.12 * G["hat"]
    hum = 0.22 * nap + 0.16 * clo + 0.55 * G["fx"] + 0.12 * G["clap"]
    gauche = sec + hum + 0.35 * convolution(hum, ir_l)
    droite = sec + hum + 0.35 * convolution(hum, ir_r)
    # légère largeur stéréo sur la nappe et les cloches
    gauche += 0.05 * nap - 0.03 * clo
    droite -= 0.05 * nap - 0.03 * clo
    st = np.stack([gauche, droite], axis=1)
    st = np.tanh(st * 0.9)
    # fondu de fin (mesure 11 et queue)
    fin = int((DUREE - 1.2) * SR)
    st[fin:] *= np.linspace(1, 0, len(st) - fin)[:, None] ** 2
    st = st[:int((DUREE + 0.2) * SR)]
    st /= np.max(np.abs(st)) / 0.89
    pcm = (st * 32767).astype(np.int16)
    with wave.open(sortie, "wb") as w:
        w.setnchannels(2)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes(pcm.tobytes())
    print("musique : %.2f s, %d mesures de %.3f s" % (len(pcm) / SR, int(NB_MESURES), MESURE))


if __name__ == "__main__":
    main()
