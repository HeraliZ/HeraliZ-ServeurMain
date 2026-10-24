#!/usr/bin/env python3
"""Sons de l'hélicoptère de patrouille de l'Airstrike, fabriqués par le calcul (aucun enregistrement) :

  - helico_rotor.ogg   boucle de 3 s (sans raccord audible) : battement des pales (20 coups par seconde), turbine,
                       rotor de queue, grondement ; joué en boucle chez le joueur (client/helico/RotorSon), qui suit
                       l'hélicoptère, porte loin (420 blocs, sounds.json) ;
  - helico_tir.ogg     rafale de la mitrailleuse rotative (environ 1 s) : montée du moteur, 50 coups par seconde, écho ;
  - helico_chute.ogg   chute (4 s) : alarme du poste, turbine qui s'effondre, pales qui ralentissent, tôle qui gémit.

    python mods/airstrike/outils/sons_helico.py                écrit les .ogg dans les assets du mod
    python mods/airstrike/outils/sons_helico.py --ecoute DOSSIER   copies .wav à écouter, rien d'installé

Besoins : Python 3.11+, numpy, ffmpeg (avec libvorbis). On modifie ce générateur, jamais les .ogg.
"""
import argparse
import subprocess
import wave
from pathlib import Path

import numpy as np

SR = 44100
MOD = Path(__file__).resolve().parents[1]
SONS = MOD / "src" / "client" / "resources" / "assets" / "airstrike" / "sounds"
RNG = np.random.default_rng(2410)


def bruit_periodique(n, bas=20.0, haut=8000.0, pente=-1.0):
    """Bruit dont le début raccorde parfaitement avec la fin (spectre tiré au hasard, transformée inverse)."""
    f = np.fft.rfftfreq(n, 1 / SR)
    amp = np.where((f >= bas) & (f <= haut), np.maximum(f, 1) ** (pente / 2), 0)
    ph = RNG.uniform(0, 2 * np.pi, len(f))
    x = np.fft.irfft(amp * np.exp(1j * ph), n)
    return x / (np.abs(x).max() + 1e-9)


def passe_bande(x, bas, haut):
    X = np.fft.rfft(x)
    f = np.fft.rfftfreq(len(x), 1 / SR)
    X[(f < bas) | (f > haut)] = 0
    return np.fft.irfft(X, len(x))


def rotor(duree=3.0, coups=20.0):
    n = int(SR * duree)
    t = np.arange(n) / SR
    out = np.zeros(n)
    # battement des pales : coup sourd + souffle filtré, identique à chaque passage (boucle exacte)
    per = int(round(SR / coups))
    k = np.arange(per) / SR
    coup = (np.sin(2 * np.pi * 68 * k) * 0.9 + np.sin(2 * np.pi * 136 * k + 0.6) * 0.35) * np.exp(-k * 38)
    souffle = passe_bande(RNG.normal(0, 1, per), 180, 900) * np.exp(-k * 24) * (1 - np.exp(-k * 600))
    souffle /= np.abs(souffle).max() + 1e-9
    motif = coup * 0.85 + souffle * 0.45
    for i in range(int(round(duree * coups))):
        a = 0.92 + 0.08 * np.sin(i * 2 * np.pi / (duree * coups) * 3)     # légère variation, périodique sur la boucle
        out[i * per:(i + 1) * per] += motif[:len(out[i * per:(i + 1) * per])] * a
    # turbine : deux sifflements avec un battement lent, rotor de queue (bourdonnement), grondement
    turb = (np.sin(2 * np.pi * 1340 * t) * 0.6 + np.sin(2 * np.pi * 2680 * t) * 0.25) * (0.75 + 0.25 * np.sin(2 * np.pi * t / duree * 2))
    queue = np.sign(np.sin(2 * np.pi * 96 * t)) * 0.5 + np.sin(2 * np.pi * 96 * t) * 0.5
    queue = passe_bande(queue, 80, 700)
    gronde = passe_bande(bruit_periodique(n, 25, 260, -1.5), 25, 260)
    souffle_fond = passe_bande(bruit_periodique(n, 400, 6000, -1.0), 400, 6000)
    out = out + turb * 0.05 + queue / (np.abs(queue).max() + 1e-9) * 0.10 + gronde * 0.35 + souffle_fond * 0.06
    return out / np.abs(out).max() * 0.92


def echo(x, duree=0.6, force=0.35):
    m = int(SR * duree)
    k = np.arange(m) / SR
    rep = RNG.normal(0, 1, m) * np.exp(-k * 7)
    rep[0] = 1 / force
    y = np.convolve(x, rep)[:len(x) + m] * force
    return y


def tir(duree=1.0, cadence=50.0):
    n = int(SR * (duree + 0.15))
    t = np.arange(n) / SR
    out = np.zeros(n)
    moteur = np.sin(2 * np.pi * np.cumsum(380 + 260 * np.clip(t / 0.12, 0, 1)) / SR) * np.clip(t / 0.05, 0, 1)
    moteur *= np.clip((duree + 0.12 - t) / 0.12, 0, 1)
    out += moteur * 0.08
    debut = 0.07
    i = 0
    while debut + i / cadence < duree:
        p = int(SR * (debut + i / cadence + RNG.uniform(-0.0015, 0.0015)))
        L = int(SR * 0.05)
        k = np.arange(L) / SR
        claq = RNG.normal(0, 1, L) * np.exp(-k * 120)
        claq = passe_bande(claq, 300, 9000) * 1.2 + np.sin(2 * np.pi * 95 * k) * np.exp(-k * 60) * 0.9
        out[p:p + L] += claq[:len(out[p:p + L])] * (0.85 + 0.15 * RNG.random())
        i += 1
    out = echo(out, 0.7, 0.3)
    return out / np.abs(out).max() * 0.95


def chute(duree=4.0):
    n = int(SR * duree)
    t = np.arange(n) / SR
    # alarme à deux tons (adoucie)
    ton = np.where((t * 4).astype(int) % 2 == 0, 880, 1100)
    alarme = np.tanh(np.sin(2 * np.pi * np.cumsum(ton) / SR) * 3) * 0.35 * ((t * 8).astype(int) % 2 == 0)
    # turbine qui s'effondre
    f = 1400 - 750 * np.clip(t / duree, 0, 1) ** 0.7
    turb = np.sin(2 * np.pi * np.cumsum(f) / SR) * 0.2 * (1 - 0.5 * t / duree)
    # pales qui ralentissent : coups de 20 à 11 par seconde
    out = np.zeros(n)
    tt = 0.0
    while tt < duree:
        p = int(tt * SR)
        L = int(SR * 0.06)
        k = np.arange(L) / SR
        c = np.sin(2 * np.pi * 64 * k) * np.exp(-k * 36)
        out[p:p + L] += c[:len(out[p:p + L])] * 0.8
        tt += 1 / (20 - 9 * tt / duree)
    # tôle qui gémit
    gem = passe_bande(RNG.normal(0, 1, n), 120, 500) * (0.5 + 0.5 * np.sin(2 * np.pi * 0.7 * t)) * 0.25
    vent = passe_bande(RNG.normal(0, 1, n), 300, 4000) * 0.12 * np.clip(t / duree * 1.5, 0, 1)
    out = out + alarme + turb + gem / (np.abs(gem).max() + 1e-9) * 0.25 + vent / (np.abs(vent).max() + 1e-9) * 0.2
    out *= np.clip((duree - t) / 0.3, 0, 1)
    return out / np.abs(out).max() * 0.95


def ecrire_wav(x, f):
    Path(f).parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(f), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SR)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())


def ecrire_ogg(x, f):
    Path(f).parent.mkdir(parents=True, exist_ok=True)
    cmd = ["ffmpeg", "-v", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "1", "-i", "-",
           "-c:a", "libvorbis", "-q:a", "5", str(f)]
    subprocess.run(cmd, input=np.clip(x, -1, 1).astype(np.float32).tobytes(), check=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("--ecoute", type=Path)
    a = ap.parse_args()
    sons = {"helico_rotor": rotor(), "helico_tir": tir(), "helico_chute": chute()}
    for nom, x in sons.items():
        if a.ecoute:
            ecrire_wav(x, a.ecoute / (nom + ".wav"))
        else:
            ecrire_ogg(x, SONS / (nom + ".ogg"))
        print(nom, "%.2f s" % (len(x) / SR))


if __name__ == "__main__":
    main()
