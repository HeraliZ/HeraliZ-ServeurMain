"""Refabrique le modèle 3D et les textures de la barricade en bois (mod heralisz-bunker).

    python outils/barricade_modele.py

Écrit, dans modules/bunker/src/client/resources/assets/heralisz_bunker/ :
  - models/entity/wooden_barricade.bin et wooden_barricade_lod.bin : le modèle (de près, de loin), lus par BarricadeMesh ;
  - textures/entity/wooden_barricade_0.png à _3.png : les textures, de intacte (0) à très abîmée (3).
Le modèle est décrit dans modules/bunker/outils/BarricadeModele.java (mur de rondins couchés) ; il est construit
avec le moteur de maillage de HeraliZ Crates (code Java pur, sans Minecraft) et JOML, pris dans le cache de Gradle du PC
(il y est dès qu'un mod a été fabriqué une fois) ou donné par la variable d'environnement JOML_JAR.
Besoin : un JDK 17 (javac), Python 3.11+ avec numpy et Pillow.
"""
import os
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

DEPOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(DEPOT / "outils"))
from formes_caisses import joml, outil  # noqa: E402

CRATES = DEPOT / "modules" / "boutique" / "src"
BUNKER = DEPOT / "modules" / "bunker"
ASSETS = BUNKER / "src" / "client" / "resources" / "assets" / "heralisz_bunker"


# =================================================================== textures
def _up(a, w, h):
    return np.array(Image.fromarray(a.astype(np.float32), mode="F").resize((w, h), Image.BICUBIC))


def _fbm(w, h, sx, sy, octaves, seed):
    t, amp, tot = np.zeros((h, w)), 1.0, 0.0
    for o in range(octaves):
        r = np.random.default_rng(seed + o)
        t += amp * _up(r.random((max(2, int(sy * 2 ** o)), max(2, int(sx * 2 ** o)))), w, h)
        tot += amp
        amp *= 0.5
    return t / tot


def _mix(c0, c1, t):
    c0, c1 = np.array(c0, float), np.array(c1, float)
    t = np.asarray(t, float)
    return c0 * (1 - t[..., None]) + c1 * t[..., None] if t.ndim else c0 * (1 - t) + c1 * t


def _c(x):
    return np.clip(x, 0, 1)


def atlas():
    """Atlas 1024 (mêmes tuiles que Mat de HeraliZ Crates) : écorce (MAIN), bois de bout (DECAL_A), fer (DARK)."""
    a = np.full((1024, 1024, 3), 90.0)
    s = 512
    n, m = _fbm(s, s, 40, 3, 5, 1), _fbm(s, s, 6, 2, 4, 10)
    ridge, deep = _c((n - 0.42) * 4), _c((0.40 - n) * 6)
    bark = _mix((58, 40, 28), (104, 78, 54), _c(m * 1.2 - 0.1)) * (0.62 + 0.5 * ridge[..., None]) * (1 - 0.55 * deep[..., None])
    lichen = _c((_fbm(s, s, 10, 10, 4, 33) - 0.62) * 6)
    a[0:512, 0:512] = _mix(bark / 255, (0.40, 0.42, 0.33), lichen * 0.22) * 255
    yy, xx = np.mgrid[0:256, 0:256]
    d = np.sqrt((xx - 128) ** 2 + (yy - 128) ** 2) / 128
    rings = 0.5 + 0.5 * np.sin((d + _fbm(256, 256, 6, 6, 3, 81) * 0.08) * 60)
    end = _mix((150, 112, 70), (206, 168, 116), rings * 0.6 + 0.2) * (1 - 0.35 * _c(d - 0.3))[..., None]
    end = np.where((d > 0.88)[..., None], np.array([70, 48, 32]), end)
    end *= (1 - 0.4 * _c((_fbm(256, 256, 20, 20, 3, 82) - 0.7) * 5) * _c(d * 1.5))[..., None]
    a[768:1024, 0:256] = end
    a[768:896, 896:1024] = _mix((34, 34, 36), (70, 68, 66), _fbm(128, 128, 12, 12, 5, 102))
    return a


def usure(a, stage):
    """Etat d'usure : bois assombri, fissures sombres, éclats de bois clair, de plus en plus nombreux."""
    if stage == 0:
        return a
    img = Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))
    d = ImageDraw.Draw(img)
    r = np.random.default_rng(500 + stage)
    for _ in range(14 * stage):
        # fissures dans le fil (verticales) sur l'écorce, rayonnantes sur le bois de bout
        if r.random() < 0.75:
            x, y = r.uniform(0, 512), r.uniform(0, 512)
            pts = [(x, y)]
            for _ in range(int(r.integers(6, 16))):
                x += r.normal(0, 3)
                y += r.uniform(6, 14)
                pts.append((x, y))
            d.line(pts, fill=(22, 14, 10), width=int(r.integers(2, 4 + stage)))
        else:
            ang = r.uniform(0, 2 * np.pi)
            r0, r1 = r.uniform(10, 40), r.uniform(70, 125)
            d.line([(128 + np.cos(ang) * r0, 896 + np.sin(ang) * r0), (128 + np.cos(ang) * r1, 896 + np.sin(ang) * r1)],
                   fill=(40, 26, 16), width=int(r.integers(2, 5)))
    for _ in range(5 * stage):
        # éclats arrachés dans le fil : longues échardes de bois frais, bord sombre
        x, y = r.uniform(0, 500), r.uniform(0, 470)
        w, h = r.uniform(5, 12), r.uniform(26, 70)
        left = [(x - w / 2 * r.uniform(0.3, 1.0), y + h * t) for t in np.linspace(0.1, 0.9, 5)]
        right = [(x + w / 2 * r.uniform(0.3, 1.0), y + h * t) for t in np.linspace(0.9, 0.1, 5)]
        poly = [(x + r.normal(0, 1.5), y)] + left + [(x + r.normal(0, 1.5), y + h)] + right
        d.polygon(poly, fill=(150, 112, 72), outline=(44, 28, 18))
        d.line([(x, y + h * 0.2), (x + r.normal(0, 1), y + h * 0.8)], fill=(186, 146, 100), width=2)
    img = img.filter(ImageFilter.GaussianBlur(0.6))
    out = np.array(img).astype(float)
    return out * (1 - 0.09 * stage)


def textures():
    base = atlas()
    for stage in range(4):
        rgb = usure(base.copy(), stage)
        rgba = np.dstack([np.clip(rgb, 0, 255), np.full((1024, 1024), 255.0)]).astype(np.uint8)
        dest = ASSETS / "textures" / "entity" / ("wooden_barricade_%d.png" % stage)
        Image.fromarray(rgba, "RGBA").resize((512, 512), Image.LANCZOS).save(dest, optimize=True)
        print("écrit :", dest.relative_to(DEPOT))


# =================================================================== modèle
def modele():
    env = dict(os.environ)
    env.pop("JAVA_TOOL_OPTIONS", None)
    jar = joml()
    base = CRATES / "client" / "java" / "fr" / "heraliz" / "crates" / "client" / "render"
    sources = [str(p) for d in ("mesh", "scene") for p in (base / d).rglob("*.java")]
    sources += [str(CRATES / "commun" / "java" / "fr" / "heraliz" / "crates" / "registry" / "Snd.java"),
                str(CRATES / "commun" / "java" / "fr" / "heraliz" / "crates" / "block" / "CrateAnim.java"),
                str(BUNKER / "outils" / "BarricadeModele.java")]
    dossier = ASSETS / "models" / "entity"
    dossier.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run([outil("javac"), "-nowarn", "-encoding", "UTF-8", "-d", tmp, "-cp", jar] + sources, check=True, env=env)
        subprocess.run([outil("java"), "-cp", os.pathsep.join([tmp, jar]), "BarricadeModele",
                        str(dossier / "wooden_barricade.bin"), str(dossier / "wooden_barricade_lod.bin")], check=True, env=env)


if __name__ == "__main__":
    textures()
    modele()
