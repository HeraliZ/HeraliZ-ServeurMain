"""Planches à présenter au propriétaire : une par skin (finition sur 4 armes + vue en main, rechargement en 6 étapes)."""
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

S = Path(sys.argv[1])
R = Path(sys.argv[2])
DEST = Path(sys.argv[3])
DEST.mkdir(parents=True, exist_ok=True)
FONT = R / "modules/noyau/src/client/resources/assets/heraliz_noyau/font"
B = lambda s: ImageFont.truetype(str(FONT / "dmsans_bold.ttf"), s)
M = lambda s: ImageFont.truetype(str(FONT / "dmsans_medium.ttf"), s)
OUT = S / "out"
L = 1920
FOND = (14, 15, 19)
TEXTE = (226, 228, 234)
GRIS = (150, 155, 168)

FICHES = {
    "faille": {
        "titre": "FAILLE",
        "accent": (214, 92, 255),
        "accent2": (60, 225, 255),
        "theme": "Skin mythique · thème : les anomalies de la Zone",
        "finition": ("Verre volcanique noir, poli comme un miroir. De grandes fractures traversent l'arme ; au fond, on voit "
                     "l'espace de l'anomalie (nébuleuse, étoiles) qui bouge quand on tourne l'arme. Bords magenta, lumière "
                     "cyan, fil blanc au cœur : la lumière court le long des fractures et respire."),
        "recharge": ("Une faille s'ouvre sous l'arme et avale l'ancien chargeur, qui part en éclats de lumière. "
                     "Elle recrache le nouveau, chauffé à blanc, qui se refroidit en obsidienne pendant qu'on l'enclenche. "
                     "Clic : la faille se referme d'un coup."),
    },
    "acces": {
        "titre": "ACCÈS ROUGE",
        "accent": (255, 40, 62),
        "accent2": (43, 255, 122),
        "theme": "Skin mythique · thème : les laboratoires des bunkers (carte d'accès rouge)",
        "finition": ("Blindage de laboratoire : panneaux en angles vifs, céramique noire brillante et composite graphite à "
                     "micro-hexagones, quelques panneaux rouges vernis marqués au pochoir. Une lumière rouge de sécurité passe "
                     "dans les joints, pulse comme le voyant d'une porte verrouillée, et un balayage parcourt l'arme."),
        "recharge": ("L'ancien chargeur se coupe comme un hologramme et part en cubes rouges. Le nouveau "
                     "s'imprime dans la main : grille rouge, puis une ligne de balayage le rend solide de bas en haut. Clic : "
                     "une onde parcourt les joints, qui passent au vert une demi-seconde (accès autorisé)."),
    },
}
ARMES = [("ak47", "AK-47"), ("xm177", "XM177"), ("vector45", "Vector"), ("glock_18c", "Glock 18C"), ("ak47_34", "AK-47, de trois quarts")]


def fond_degrade(w, h):
    y = np.linspace(0, 1, h)[:, None, None]
    g = np.array((26, 28, 36), float) * (1 - y) + np.array((10, 11, 14), float) * y
    return Image.fromarray(np.repeat(g, w, axis=1).astype(np.uint8))


def sur_fond(path, w, h, recadrer=True):
    im = Image.open(path).convert("RGBA")
    if recadrer:
        bb = im.getbbox()
        if bb:
            m = 24
            im = im.crop((max(0, bb[0] - m), max(0, bb[1] - m), min(im.width, bb[2] + m), min(im.height, bb[3] + m)))
    k = min(w / im.width, h / im.height)
    im = im.resize((max(1, int(im.width * k)), max(1, int(im.height * k))), Image.LANCZOS)
    bg = fond_degrade(w, h).convert("RGBA")
    bg.alpha_composite(im, ((w - im.width) // 2, (h - im.height) // 2))
    return bg.convert("RGB")


def lignes(d, texte, police, largeur):
    mots, out, cur = texte.split(), [], ""
    for m in mots:
        essai = (cur + " " + m).strip()
        if d.textlength(essai, font=police) <= largeur:
            cur = essai
        else:
            out.append(cur)
            cur = m
    if cur:
        out.append(cur)
    return out


def planche(skin):
    c = FICHES[skin]
    H = 2050
    im = Image.new("RGB", (L, H), FOND)
    d = ImageDraw.Draw(im)
    x = 60
    d.text((x, 44), c["titre"], font=B(64), fill=c["accent"])
    d.text((x + d.textlength(c["titre"], font=B(64)) + 24, 72), c["theme"], font=M(24), fill=GRIS)
    d.line((x, 130, L - x, 130), fill=(40, 42, 50), width=2)
    y = 150
    for l in lignes(d, c["finition"], M(24), L - 2 * x):
        d.text((x, y), l, font=M(24), fill=TEXTE)
        y += 32
    y += 18
    # finition : 4 armes de profil + 3/4, puis vue en main
    cw, ch = (L - 2 * x - 3 * 16) // 4, 260
    for k, (nom, lib) in enumerate(ARMES[:4]):
        cx = x + k * (cw + 16)
        im.paste(sur_fond(OUT / f"{skin}_{nom}.png", cw, ch), (cx, y))
        d.text((cx + 12, y + ch - 34), lib, font=M(20), fill=GRIS)
    y += ch + 16
    w2 = (L - 2 * x - 16) // 2
    im.paste(sur_fond(OUT / f"{skin}_ak47_34.png", w2, 360), (x, y))
    d.text((x + 12, y + 360 - 34), "AK-47, de trois quarts", font=M(20), fill=GRIS)
    im.paste(sur_fond(OUT / f"{skin}_fps.png", w2, 360), (x + w2 + 16, y))
    d.text((x + w2 + 28, y + 360 - 34), "En main (1re personne)", font=M(20), fill=GRIS)
    y += 360 + 34
    d.text((x, y), "RECHARGEMENT", font=B(34), fill=c["accent"])
    y += 52
    for l in lignes(d, c["recharge"], M(24), L - 2 * x):
        d.text((x, y), l, font=M(24), fill=TEXTE)
        y += 32
    y += 14
    sys.path.insert(0, str(S / "proto"))
    cles = CLES[skin]
    fw, fh = (L - 2 * x - 2 * 16) // 3, 360
    for k, (u, leg) in enumerate(cles):
        cx, cy = x + (k % 3) * (fw + 16), y + (k // 3) * (fh + 70)
        f = OUT / "rech" / ("cle_%s_%04d.png" % (skin, round(u * 1000)))
        src = Image.open(f).convert("RGB")
        # cadrage commun : le cœur de l'action (chargeur et faille / cubes)
        cad = src.crop((150, 60, 870, 560)).resize((fw, fh), Image.LANCZOS)
        im.paste(cad, (cx, cy))
        ll = lignes(d, leg, M(21), fw - 10)
        for j, l in enumerate(ll[:2]):
            d.text((cx + 4, cy + fh + 8 + j * 26), l, font=M(21), fill=TEXTE if j == 0 else TEXTE)
    y += 2 * (fh + 70) + 6
    note = ("Rendus faits hors du jeu, avec le vrai modèle des armes du serveur et le code de dessin d'essai du skin. En jeu, "
            "les mains font le geste habituel de chaque arme ; seul le chargeur et les effets autour changent (marche sur "
            "toutes les armes). Rien n'est testé en jeu.")
    for l in lignes(d, note, M(20), L - 2 * x):
        d.text((x, y), l, font=M(20), fill=GRIS)
        y += 27
    im = im.crop((0, 0, L, y + 30))
    im.save(DEST / f"planche_{skin}.png", optimize=True)
    print(DEST / f"planche_{skin}.png", im.size)


exec(compile(open(S / "proto/rechargement.py", encoding="utf-8").read().split("if MODE ==")[0].replace("MODE = sys.argv[2]", "MODE = ''"), "rech", "exec"))
OUT, W, H = S / "out", 960, 600
for skin in FICHES:
    planche(skin)
    g = OUT / "rech" / f"anim_{skin}.gif"
    (DEST / f"rechargement_{skin}.gif").write_bytes(g.read_bytes())
