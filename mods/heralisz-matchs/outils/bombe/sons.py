"""
Sons de la bombe des duels, fabriqués par programme (aucun enregistrement ni son tiré d'un jeu : licence HeraliZ) :
  - duel_clavier_1..3 : frappe au clavier de l'ordinateur pendant l'amorçage (clic sec + petit « toc » du boîtier) ;
  - duel_piratage_1..3 : bips de piratage (deux ou trois notes électroniques très courtes).
Mono, 44,1 kHz, Ogg Vorbis (ffmpeg et numpy nécessaires), déposés dans
src/client/resources/assets/heralisz_matchs/sounds/ ; joués par le jeu chez le joueur, à la position de l'ordinateur
(duels/bombe/OrdinateurDuel, variante au hasard et hauteur variable).

    python mods/heralisz-matchs/outils/bombe/sons.py
"""
import shutil
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

ICI = Path(__file__).parent
SONS = ICI.parents[1] / "src" / "client" / "resources" / "assets" / "heralisz_matchs" / "sounds"
TAUX = 44100


def _t(duree):
    return np.arange(int(TAUX * duree)) / TAUX


def _passe_bande(x, bas, haut):
    """Filtre passe-bande simple par transformée de Fourier (sons très courts : pas de souci de phase)."""
    n = len(x)
    f = np.fft.rfftfreq(n, 1 / TAUX)
    s = np.fft.rfft(x)
    gain = np.clip((f - bas * 0.7) / (bas * 0.3 + 1e-9), 0, 1) * np.clip((haut * 1.3 - f) / (haut * 0.3 + 1e-9), 0, 1)
    return np.fft.irfft(s * gain, n)


def _normaliser(x, crete_db):
    x = x - np.mean(x)
    m = np.max(np.abs(x)) or 1.0
    return x / m * (10 ** (crete_db / 20))


def _fondu(x, debut=0.002, fin=0.004):
    a, b = int(TAUX * debut), int(TAUX * fin)
    env = np.ones(len(x))
    if a:
        env[:a] = np.linspace(0, 1, a)
    if b:
        env[-b:] = np.linspace(1, 0, b)
    return x * env


def clavier(graine, ton=1.0):
    """Une touche : clic d'attaque (bruit filtré 2,5 à 7 kHz, 5 ms), toc du boîtier (sinus amorti), petit clic de
    relâche 28 ms plus tard."""
    r = np.random.default_rng(graine)
    t = _t(0.075)
    bruit = r.standard_normal(len(t))
    clic = _passe_bande(bruit, 2500 * ton, 7000 * ton) * np.exp(-t / 0.0045)
    f0 = (520 + 90 * r.random()) * ton
    toc = (np.sin(2 * np.pi * f0 * t) + 0.35 * np.sin(2 * np.pi * f0 * 2.7 * t)) * np.exp(-t / 0.012) * 0.55
    rel = np.zeros(len(t))
    d = int(TAUX * (0.026 + 0.006 * r.random()))
    n2 = int(TAUX * 0.02)
    tt = np.arange(n2) / TAUX
    rel[d:d + n2] = _passe_bande(r.standard_normal(n2), 3000 * ton, 8000 * ton) * np.exp(-tt / 0.003) * 0.35
    x = clic + toc + rel
    return _fondu(_normaliser(x, -7.0), 0.0005, 0.01)


def bips(notes, duree=0.045, ecart=0.012):
    """Suite de bips courts (sinus + un peu d'harmonique impaire), attaque et chute douces."""
    morceaux = []
    for f in notes:
        t = _t(duree)
        env = np.minimum(1, t / 0.004) * np.minimum(1, (duree - t) / 0.012)
        s = np.sin(2 * np.pi * f * t) + 0.18 * np.sin(2 * np.pi * 3 * f * t) + 0.06 * np.sin(2 * np.pi * 5 * f * t)
        morceaux.append(s * env)
        morceaux.append(np.zeros(int(TAUX * ecart)))
    x = np.concatenate(morceaux[:-1])
    return _fondu(_normaliser(x, -11.0), 0.001, 0.006)


def ecrire_ogg(x, chemin):
    if shutil.which("ffmpeg") is None:
        sys.exit("ffmpeg introuvable : il sert à encoder les sons en Ogg Vorbis")
    with tempfile.TemporaryDirectory() as tmp:
        w = Path(tmp) / "son.wav"
        with wave.open(str(w), "wb") as f:
            f.setnchannels(1)
            f.setsampwidth(2)
            f.setframerate(TAUX)
            f.writeframes((np.clip(x, -1, 1) * 32767).astype("<i2").tobytes())
        subprocess.run(["ffmpeg", "-y", "-loglevel", "error", "-i", str(w), "-c:a", "libvorbis", "-q:a", "4", "-ac", "1",
                        "-ar", str(TAUX), "-map_metadata", "-1", "-fflags", "+bitexact", "-flags:a", "+bitexact", str(chemin)],
                       check=True)


def main():
    SONS.mkdir(parents=True, exist_ok=True)
    faits = {
        "duel_clavier_1": clavier(11, 1.0),
        "duel_clavier_2": clavier(23, 0.92),
        "duel_clavier_3": clavier(37, 1.08),
        "duel_piratage_1": bips([1760, 2349]),
        "duel_piratage_2": bips([1318, 1568, 2093], duree=0.032, ecart=0.008),
        "duel_piratage_3": bips([2093, 1760], duree=0.04),
    }
    for nom, x in faits.items():
        ecrire_ogg(x, SONS / (nom + ".ogg"))
        print("%s.ogg  %.0f ms" % (nom, len(x) / TAUX * 1000))


if __name__ == "__main__":
    main()
