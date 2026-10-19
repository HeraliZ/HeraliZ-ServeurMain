#!/usr/bin/env python3
"""
Sonorisation des vidéos : mixe des sons (fichiers du jeu, voix) et des sons fabriqués (nappe de tension, souffle,
acouphène, battements) aux instants donnés, en un fichier WAV stéréo 48 kHz.

usage : python3 outils/tournage/sonorisation.py son.json sortie.wav
son.json : {"duree": 30.0, "evenements": [
   {"fichier": "pluie.ogg", "t": 0, "gain": -14, "jusqu": 25, "boucle": true, "fondu": [1, 2]},
   {"fichier": "tir.ogg", "t": 8.4, "gain": -3, "vitesse": 0.95, "filtre": "lowpass=f=900"},
   {"synth": "nappe", "t": 0, "duree": 9, "gain": -12, "note": 55, "monte": true},
   {"synth": "souffle", "t": 4, "duree": 3.5, "gain": -10, "periode": [1.0, 0.55]},
   {"synth": "acouphene", "t": 10, "duree": 3, "gain": -22},
   {"synth": "silence", "t": 9.5, "duree": 0.5}]}            # coupe tout ce qui joue déjà (« trou » de son)
gain en dB ; vitesse < 1 = plus grave et plus lent ; fondu [entrée, sortie] en secondes.
"""
import json
import subprocess
import sys
from pathlib import Path

import numpy as np

SR = 48000


def lire(chemin, vitesse=1.0, filtre=None):
    f = "aresample=%d" % SR
    if vitesse != 1.0:
        f = "asetrate=%d,aresample=%d" % (int(SR * vitesse), SR)
    if filtre:
        f += "," + filtre
    brut = subprocess.run(["ffmpeg", "-loglevel", "error", "-i", str(chemin), "-af", f, "-ac", "2", "-ar", str(SR),
                           "-f", "f32le", "-"], check=True, stdout=subprocess.PIPE).stdout
    return np.frombuffer(brut, np.float32).reshape(-1, 2).copy()


def enveloppe(n, fi, fo):
    e = np.ones(n, np.float32)
    a, b = int(fi * SR), int(fo * SR)
    if a:
        e[:a] = np.linspace(0, 1, min(a, n))[:min(a, n)]
    if b:
        e[-b:] *= np.linspace(1, 0, min(b, n))[-min(b, n):]
    return e[:, None]


def nappe(duree, note, monte, rng):
    """Nappe grave dissonante (deux sinus proches + bruit filtré), qui peut monter en tension."""
    t = np.arange(int(duree * SR)) / SR
    k = (t / duree) if monte else np.ones_like(t) * 0.5
    s = np.sin(2 * np.pi * note * t) + 0.7 * np.sin(2 * np.pi * note * 1.06 * t + 1) + 0.4 * np.sin(2 * np.pi * note * 2.01 * t)
    s *= 0.6 + 0.4 * np.sin(2 * np.pi * (0.3 + 2.5 * k) * t)        # pulsation qui accélère
    bruit = np.convolve(rng.normal(0, 1, len(t)), np.ones(60) / 60, mode="same") * (0.3 + 1.2 * k)
    m = (s * 0.5 + bruit * 0.6) * (0.5 + 0.5 * k)
    g = np.stack([m, np.roll(m, 300)], 1)
    return g / (np.abs(g).max() + 1e-9)


def souffle(duree, periode, rng):
    """Respiration haletante : bruit filtré (bande 300-2500 Hz) en inspirations / expirations."""
    n = int(duree * SR)
    b = rng.normal(0, 1, n)
    b = np.convolve(b, np.ones(8) / 8, mode="same") - np.convolve(b, np.ones(80) / 80, mode="same")
    env = np.zeros(n)
    t, i = 0.0, 0
    while t < duree:
        p = periode[0] + (periode[-1] - periode[0]) * (t / duree)
        a, l = int(t * SR), int(p * SR * 0.42)
        x = np.linspace(0, np.pi, max(l, 2))
        forme = np.sin(x) ** (1.5 if i % 2 == 0 else 3) * (0.7 if i % 2 == 0 else 1.0)
        env[a:a + l] += forme[:len(env[a:a + l])]
        t += p / 2
        i += 1
    m = b * env
    return np.stack([m, m * 0.92], 1) / (np.abs(m).max() + 1e-9)


def acouphene(duree):
    t = np.arange(int(duree * SR)) / SR
    m = np.sin(2 * np.pi * 3400 * t) * np.exp(-t * 1.2)
    return np.stack([m, m], 1)


def main():
    cfg = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    base = Path(sys.argv[1]).resolve().parent
    rng = np.random.default_rng(3)
    piste = np.zeros((int(cfg["duree"] * SR) + SR, 2), np.float32)
    for ev in sorted(cfg["evenements"], key=lambda e: e.get("synth") == "silence"):
        t0 = int(ev["t"] * SR)
        if ev.get("synth") == "silence":
            n = int(ev["duree"] * SR)
            f = int(0.03 * SR)
            piste[t0:t0 + n] *= 0
            piste[max(0, t0 - f):t0] *= np.linspace(1, 0, len(piste[max(0, t0 - f):t0]))[:, None]
            continue
        if "synth" in ev:
            d = ev["duree"]
            s = {"nappe": lambda: nappe(d, ev.get("note", 55), ev.get("monte", False), rng),
                 "souffle": lambda: souffle(d, ev.get("periode", [1.0, 0.6]), rng),
                 "acouphene": lambda: acouphene(d)}[ev["synth"]]()
        else:
            s = lire(base / ev["fichier"], ev.get("vitesse", 1.0), ev.get("filtre"))
            if ev.get("boucle"):
                n = int((ev["jusqu"] - ev["t"]) * SR)
                s = np.concatenate([s] * (n // len(s) + 1))[:n]
            elif "duree" in ev:
                s = s[:int(ev["duree"] * SR)]
        s = s * enveloppe(len(s), *ev.get("fondu", [0, 0.01]))
        s = s * (10 ** (ev.get("gain", 0) / 20))
        if "pan" in ev:
            s = s * np.array([1 - max(0, ev["pan"]), 1 + min(0, ev["pan"])], np.float32)
        n = min(len(s), len(piste) - t0)
        piste[t0:t0 + n] += s[:n]
    # limiteur doux
    piste = np.tanh(piste * 1.2) / np.tanh(1.2)
    piste = piste[:int(cfg["duree"] * SR)]
    subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
                    str(sys.argv[2])], input=piste.astype(np.float32).tobytes(), check=True)
    print("son : %s (%.1f s)" % (sys.argv[2], cfg["duree"]))


if __name__ == "__main__":
    main()
