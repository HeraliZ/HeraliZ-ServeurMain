#!/usr/bin/env python3
"""
Montage des TikTok HeraliZ : assemble des plans filmés (outils/tournage) en une vidéo verticale 1080 × 1920, avec zooms,
secousses, flashs, ralentis, gros sous-titres qui « sautent » mot par mot et écran de fin (logo + Discord en description).

usage : python3 outils/tournage/montage.py montage.json
montage.json :
{
  "sortie": "tiktok.mp4", "fps": 30,
  "plans": [
    {"source": "prises/A.mp4", "debut": 1.0, "duree": 2.0,   # secondes dans la source
     "vitesse": 1.0,                                            # 0.5 = ralenti (la source doit avoir assez d'images)
     "zoom": [1.0, 1.15], "centre": [0.5, 0.5],                 # zoom du début à la fin du plan, point visé (0..1)
     "secousse": 0, "flash": false, "coup": [0.4]}              # secousse (px), flash blanc à l'entrée, à-coups (s)
  ],
  "textes": [{"debut": 0.0, "fin": 1.6, "texte": "UN *DAYZ* DANS MINECRAFT", "place": "haut"}],   # *mot* = couleur
  "fin": {"duree": 2.2, "lignes": ["SERVEUR DAYZ MINECRAFT", "DISCORD EN DESCRIPTION"]}
}
Pas de son : la musique se met dans TikTok (sons tendance).
"""
import json
import math
import random
import subprocess
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

ICI = Path(__file__).resolve().parent
DEPOT = ICI.parents[1]
POLICE = DEPOT / "mods/heraliz-interface/src/main/resources/assets/heraliz_interface/font/dmsans_bold.ttf"
LOGO = DEPOT / "launcher/app/assets/images/heraliz-logo.png"
L, H = 1080, 1920
ROUGE = (235, 40, 40)
JAUNE = (255, 214, 64)


def lire_plan(source, debut, duree, vitesse, fps):
    """Images du plan (recadrées en 1080 × 1920), lues par ffmpeg."""
    n = max(1, round(duree * fps))
    filtre = "setpts=PTS/%s,fps=%d,scale=%d:%d:force_original_aspect_ratio=increase,crop=%d:%d" % (
        vitesse, fps, L, H, L, H)
    cmd = ["ffmpeg", "-loglevel", "error", "-ss", str(debut), "-i", str(source), "-vf", filtre,
           "-frames:v", str(n), "-f", "rawvideo", "-pix_fmt", "rgb24", "-"]
    brut = subprocess.run(cmd, check=True, stdout=subprocess.PIPE).stdout
    images = [np.frombuffer(brut[i:i + L * H * 3], np.uint8).reshape(H, L, 3) for i in range(0, len(brut), L * H * 3)]
    while len(images) < n:
        images.append(images[-1])
    return images


def adoucir(u):
    return u * u * (3 - 2 * u)


def cadrer(img, zoom, cx, cy, dx, dy):
    """Zoom autour de (cx, cy) et décalage (secousse)."""
    if zoom <= 1.001 and dx == 0 and dy == 0:
        return img
    im = Image.fromarray(img)
    w, h = L / zoom, H / zoom
    x0 = min(max(cx * L - w / 2 + dx, 0), L - w)
    y0 = min(max(cy * H - h / 2 + dy, 0), H - h)
    return np.asarray(im.resize((L, H), Image.BICUBIC, box=(x0, y0, x0 + w, y0 + h)))


def police(taille):
    return ImageFont.truetype(str(POLICE), taille)


def morceaux(texte):
    """« UN *DAYZ* DANS » -> [(mot, couleur)]."""
    r = []
    for m in texte.split():
        if m.startswith("*") and m.endswith("*"):
            r.append((m.strip("*"), JAUNE))
        elif m.startswith("!") and m.endswith("!"):
            r.append((m.strip("!"), ROUGE))
        else:
            r.append((m, (255, 255, 255)))
    return r


def dessiner_texte(img, texte, t, place):
    """Sous-titre façon TikTok : mots qui apparaissent un par un en sautant, gros contour noir, ombre."""
    mots = morceaux(texte)
    taille = 96
    f = police(taille)
    # lignes de 15 caractères au plus
    lignes, cour = [], []
    for m in mots:
        if cour and sum(len(x[0]) + 1 for x in cour) + len(m[0]) > 15:
            lignes.append(cour)
            cour = []
        cour.append(m)
    if cour:
        lignes.append(cour)
    calque = Image.new("RGBA", (L, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(calque)
    y_base = {"haut": 330, "milieu": 820, "bas": 1300}[place]
    k = 0
    for i, ligne in enumerate(lignes):
        largeurs = [d.textlength(m[0], font=f) for m in ligne]
        total = sum(largeurs) + 26 * (len(ligne) - 1)
        x = (L - total) / 2
        y = y_base + i * (taille + 18)
        for (m, coul), w in zip(ligne, largeurs):
            apparition = k * 0.09
            k += 1
            if t < apparition:
                x += w + 26
                continue
            u = min(1.0, (t - apparition) / 0.12)
            echelle = 1.0 + 0.35 * (1 - u) ** 2 - 0.06 * math.sin(u * math.pi)
            fm = police(max(10, int(taille * echelle)))
            wm = d.textlength(m, font=fm)
            cxm, cym = x + w / 2, y + taille / 2
            d.text((cxm - wm / 2 + 6, cym - taille * echelle / 2 + 8), m, font=fm, fill=(0, 0, 0, 150))
            d.text((cxm - wm / 2, cym - taille * echelle / 2), m, font=fm, fill=coul + (255,),
                   stroke_width=9, stroke_fill=(0, 0, 0, 255))
            x += w + 26
    base = Image.fromarray(img).convert("RGBA")
    base.alpha_composite(calque)
    return np.asarray(base.convert("RGB"))


def ecran_fin(fond, lignes, n, fps):
    """Dernière image floutée et assombrie, logo qui arrive, deux lignes de texte."""
    flou = Image.fromarray(fond).filter(ImageFilter.GaussianBlur(18))
    flou = Image.blend(flou, Image.new("RGB", (L, H), (0, 0, 0)), 0.55)
    logo = Image.open(LOGO).convert("RGBA")
    lw = 940
    logo = logo.resize((lw, round(logo.height * lw / logo.width)), Image.LANCZOS)
    # fond noir du logo : rendu transparent selon la luminosité (le logo est clair ou rouge sur noir)
    a = np.asarray(logo).astype(np.float32)
    lum = a[..., :3].max(axis=2)
    a[..., 3] = np.clip((lum - 18) * 4, 0, 255) * (a[..., 3] / 255)
    logo = Image.fromarray(a.astype(np.uint8))
    images = []
    for i in range(n):
        t = i / fps
        im = flou.copy().convert("RGBA")
        u = adoucir(min(1.0, t / 0.35))
        s = 1.25 - 0.25 * u
        lg = logo.resize((round(logo.width * s), round(logo.height * s)), Image.BICUBIC)
        lg.putalpha(lg.getchannel("A").point(lambda v: int(v * u)))
        im.alpha_composite(lg, (round((L - lg.width) / 2), round(640 - lg.height / 2)))
        img = np.asarray(im.convert("RGB"))
        for j, ligne in enumerate(lignes):
            if t >= 0.3 + j * 0.25:
                img = dessiner_texte(img, ligne, t - 0.3 - j * 0.25, ["milieu", "bas"][min(j, 1)])
        images.append(img)
    return images


def main():
    m = json.loads(Path(sys.argv[1]).read_text(encoding="utf-8"))
    base = Path(sys.argv[1]).resolve().parent
    fps = m.get("fps", 30)
    sortie = base / m["sortie"]
    enc = subprocess.Popen(["ffmpeg", "-loglevel", "error", "-y", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", "%dx%d" % (L, H),
                            "-r", str(fps), "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "17",
                            "-pix_fmt", "yuv420p", "-movflags", "+faststart", str(sortie)], stdin=subprocess.PIPE)
    alea = random.Random(7)
    t_global = 0.0
    derniere = None
    for p in m["plans"]:
        images = lire_plan(base / p["source"], p.get("debut", 0), p["duree"], p.get("vitesse", 1.0), fps)
        z0, z1 = p.get("zoom", [1.0, 1.0])
        cx, cy = p.get("centre", [0.5, 0.5])
        coups = p.get("coup", [])
        for i, img in enumerate(images):
            u = i / max(1, len(images) - 1)
            t = i / fps
            zoom = z0 + (z1 - z0) * adoucir(u)
            amp = p.get("secousse", 0)
            for c in coups:   # à-coup : petit zoom et secousse forte qui retombent en 0,25 s
                if 0 <= t - c < 0.25:
                    v = 1 - (t - c) / 0.25
                    zoom += 0.08 * v
                    amp += 28 * v
            dx = alea.uniform(-amp, amp) if amp else 0
            dy = alea.uniform(-amp, amp) if amp else 0
            img = cadrer(img, zoom, cx, cy, dx, dy)
            if p.get("flash") and i < 4:
                img = (img.astype(np.float32) * (i / 4) + 255 * (1 - i / 4)).astype(np.uint8)
            for tx in m.get("textes", []):
                tg = t_global + t
                if tx["debut"] <= tg < tx["fin"]:
                    img = dessiner_texte(img, tx["texte"], tg - tx["debut"], tx.get("place", "haut"))
            enc.stdin.write(np.ascontiguousarray(img).tobytes())
            derniere = img
        t_global += len(images) / fps
    f = m.get("fin")
    if f and derniere is not None:
        for img in ecran_fin(derniere, f["lignes"], round(f["duree"] * fps), fps):
            enc.stdin.write(img.tobytes())
    enc.stdin.close()
    enc.wait()
    print("vidéo : %s (%.1f s)" % (sortie, t_global + (f["duree"] if f else 0)))


if __name__ == "__main__":
    main()
