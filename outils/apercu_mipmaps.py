#!/usr/bin/env python3
"""
Aperçu des mipmaps du sol (décision B de l'audit des images par seconde du 20/11, docs/TESTS_EN_JEU_17NOV_carte-ile.md
section 36) : le sol de la carte rendu hors du jeu sans mipmaps (aujourd'hui) et avec (2 niveaux : profil Performance ;
4 : Équilibré et Qualité), aussi fidèle que possible à Minecraft 1.20.1 + Embeddium :
- textures de nos packs (herbe et ses variantes de heraliz_terrain avec leurs rotations et poids, gravier du pack
  « textures »), teinte de l'herbe lue dans notre carte de couleurs (plaine) ;
- mipmaps fabriquées comme MipmapGenerator.generateMipLevels de Minecraft 1.20.1 (moyenne en gamma 2,2, règle des
  textures à trous : alpha < 96 -> 0) ;
- lecture comme la carte graphique : GL_NEAREST_MIPMAP_LINEAR (niveau choisi par l'écart de texture entre deux points
  voisins de l'écran, plus proche dans chaque niveau, mélange des deux niveaux) ; sans mipmaps : GL_NEAREST ;
- herbe du sol en « cutout_mipped » : détails (fleurs, cailloux, feuilles, terre) gardés si alpha >= 0,5 ;
- écran 1920 x 1080, champ de vision vertical 70°, yeux à 1,62 bloc, brouillard du terrain à 6 tronçons (86,4 à 96).
Rien n'est écrit dans le dépôt ; il faut numpy et Pillow (quelques minutes).

    python outils/apercu_mipmaps.py DOSSIER
"""
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

D = Path(__file__).resolve().parents[1]
OUT = Path(sys.argv[1])
OUT.mkdir(parents=True, exist_ok=True)
TERRAIN = D / "modules/monde/src/client/resources/resourcepacks/heraliz_terrain/assets"
PACK = D / "modules/interface/src/main/resources/resourcepacks/textures/assets/minecraft/textures"
POLICE = D / "launcher/app/assets/fonts/DMSans-Bold.ttf"

POW22 = np.array([(i / 255.0) ** 2.2 for i in range(256)], np.float64)


def mips(img, niveaux):
    """Niveaux de mipmaps d'une image RGBA uint8, comme MipmapGenerator (Minecraft 1.20.1)."""
    out = [img]
    trou = bool((img[..., 3] == 0).any())
    for _ in range(niveaux):
        p = out[-1]
        h, w = p.shape[0] // 2, p.shape[1] // 2
        if h == 0 or w == 0:
            break
        q = [p[0::2, 0::2][:h, :w], p[0::2, 1::2][:h, :w], p[1::2, 0::2][:h, :w], p[1::2, 1::2][:h, :w]]
        if trou:
            s = np.zeros((h, w, 4))
            for c in q:
                vis = (c[..., 3] != 0)[..., None]
                s += np.where(vis, POW22[c], 0.0)
            s /= 4.0
            r = (np.power(s, 0.45454545454545453) * 255.0).astype(np.int64)
            a = r[..., 3]
            r[..., 3] = np.where(a < 96, 0, a)
        else:
            s = sum(POW22[c] for c in q) * 0.25
            r = (np.power(s, 0.45454545454545453) * 255.0).astype(np.int64)
        out.append(np.clip(r, 0, 255).astype(np.uint8))
    return out


def charger(chemin):
    return np.asarray(Image.open(chemin).convert("RGBA"), np.uint8)


# ------------------------------------------------------------------ textures et carte du sol
herbe = charger(TERRAIN / "minecraft/textures/block/grass_block_top.png")
herbe2 = charger(TERRAIN / "heralisz_utils/textures/block/sol/herbe_2.png")
details = {n: charger(TERRAIN / ("heralisz_utils/textures/block/sol/%s.png" % n)) for n in ("fleurs", "feuilles", "cailloux", "terre")}
gravier = charger(PACK / "block/gravel.png")
carte_couleurs = np.asarray(Image.open(PACK / "colormap/grass.png").convert("RGB"), np.float64) / 255.0
# plaine : température 0,8, humidité 0,4 (ColorResolver de Minecraft)
TEINTE = carte_couleurs[int((1 - 0.4 * 0.8) * 255), int((1 - 0.8) * 255)]

# variantes du bloc d'herbe (blockstates/grass_block.json de heraliz_terrain) : poids
VARIANTES = [("herbe", None, 352), ("herbe2", None, 352), ("herbe", "fleurs", 32), ("herbe", "feuilles", 24),
             ("herbe", "cailloux", 20), ("herbe", "terre", 20)]
POIDS = np.cumsum([v[2] for v in VARIANTES]) / sum(v[2] for v in VARIANTES)


def hachage(bx, bz):
    """Graine par bloc (comme Mth.getSeed de Minecraft : position -> nombre pseudo-aléatoire)."""
    s = (bx * 3129871) ^ (bz * 116129781)
    s = s * s * 42317861 + s * 11
    return (s >> 16) & 0xFFFFFFFF


def scene(niveaux, cam, lacet, tangage=-6.0, W=1920, H=1080, brouillard=(86.4, 96.0)):
    tex = {"herbe": mips(herbe, niveaux), "herbe2": mips(herbe2, niveaux), "gravier": mips(gravier, niveaux)}
    det = {n: mips(t, niveaux) for n, t in details.items()}
    fy = math.tan(math.radians(35))
    fx = fy * W / H
    cl, sl = math.cos(math.radians(lacet)), math.sin(math.radians(lacet))
    ct, st = math.cos(math.radians(tangage)), math.sin(math.radians(tangage))
    avant = np.array([sl * ct, st, cl * ct])
    droite = np.array([cl, 0.0, -sl])
    haut = np.cross(avant, droite)
    jj, ii = np.mgrid[0:H, 0:W].astype(np.float64)

    def rayon(i, j):
        x = (2 * (i + 0.5) / W - 1) * fx
        y = (1 - 2 * (j + 0.5) / H) * fy
        return avant[None, None, :] + x[..., None] * droite[None, None, :] + y[..., None] * haut[None, None, :]

    def sol(i, j):
        r = rayon(i, j)
        t = np.where(r[..., 1] < -1e-6, -cam[1] / np.minimum(r[..., 1], -1e-6), np.inf)
        return cam[0] + t * r[..., 0], cam[2] + t * r[..., 2], t, r

    X, Z, T, R = sol(ii, jj)
    X1, Z1, _, _ = sol(ii + 1, jj)
    X2, Z2, _, _ = sol(ii, jj + 1)
    visible = np.isfinite(T)
    dist = np.hypot(X - cam[0], Z - cam[2])     # brouillard cylindrique (terrain)
    visible &= dist < brouillard[1] + 2
    img = np.zeros((H, W, 3))
    # ciel : bleu de la plaine vers la couleur du brouillard à l'horizon
    ciel = np.array([120, 167, 255]) / 255.0
    brume = np.array([192, 216, 255]) / 255.0
    hauteur_ciel = np.clip(R[..., 1] / 0.35, 0, 1)[..., None]
    img[:] = brume * (1 - hauteur_ciel) + ciel * hauteur_ciel
    bx, bz = np.floor(X).astype(np.int64), np.floor(Z).astype(np.int64)
    fx_, fz_ = X - bx, Z - bz
    graine = hachage(np.where(visible, bx, 0), np.where(visible, bz, 0))
    rot = (graine >> 3) & 3
    choix = np.searchsorted(POIDS, ((graine >> 8) & 0xFFFF) / 65536.0)
    route = np.abs(X - 0.5) < 2.5
    # coordonnées dans la texture (0..1), rotation du bloc (multiples de 90°)
    u0, v0 = fx_, fz_
    u = np.select([rot == 0, rot == 1, rot == 2, rot == 3], [u0, 1 - v0, 1 - u0, v0])
    v = np.select([rot == 0, rot == 1, rot == 2, rot == 3], [v0, u0, 1 - v0, 1 - u0])
    # niveau de mipmap : écart de texture entre deux points voisins de l'écran (en points de la texture de 32)
    dux = np.hypot(X1 - X, Z1 - Z)
    duy = np.hypot(X2 - X, Z2 - Z)
    rho_bloc = np.maximum(dux, duy)          # blocs par point de l'écran

    def lire(chaine, uu, vv, taille):
        """GL_NEAREST_MIPMAP_LINEAR (ou GL_NEAREST si une seule image) : couleurs RGBA flottantes."""
        lam = np.log2(np.maximum(rho_bloc * taille, 1e-9))
        n = len(chaine) - 1
        if n == 0:
            lam = np.zeros_like(lam)
        d = np.clip(lam, 0, n)
        d0 = np.floor(d).astype(int)
        d1 = np.minimum(d0 + 1, n)
        f = (d - d0)[..., None]
        res0 = np.zeros(uu.shape + (4,))
        res1 = np.zeros(uu.shape + (4,))
        for k in range(n + 1):
            im = chaine[k].astype(np.float64) / 255.0
            th, tw = im.shape[:2]
            px = np.clip((uu * tw).astype(int), 0, tw - 1)
            py = np.clip((vv * th).astype(int), 0, th - 1)
            c = im[py, px]
            res0 = np.where((d0 == k)[..., None], c, res0)
            res1 = np.where((d1 == k)[..., None], c, res1)
        return np.where((lam <= 0)[..., None], res0, res0 * (1 - f) + res1 * f)

    c_herbe = lire(tex["herbe"], u, v, herbe.shape[0])[..., :3] * TEINTE
    c_herbe2 = lire(tex["herbe2"], u, v, herbe2.shape[0])[..., :3] * TEINTE
    sol_c = np.where((choix == 1)[..., None], c_herbe2, c_herbe)
    for k, (_, nom, _) in enumerate(VARIANTES):
        if nom is None:
            continue
        cd = lire(det[nom], u, v, details[nom].shape[0])
        garde = ((choix == k) & (cd[..., 3] >= 0.5))[..., None]
        sol_c = np.where(garde, cd[..., :3], sol_c)
    c_route = lire(tex["gravier"], fx_, fz_, gravier.shape[0])[..., :3]
    sol_c = np.where(route[..., None], c_route, sol_c)
    brume_f = np.clip((dist - brouillard[0]) / (brouillard[1] - brouillard[0]), 0, 1)[..., None]
    sol_c = sol_c * (1 - brume_f) + brume * brume_f
    img = np.where(visible[..., None], sol_c, img)
    return Image.fromarray((np.clip(img, 0, 1) * 255 + 0.5).astype(np.uint8), "RGB")


def titre(im, texte, sous=None):
    try:
        f = ImageFont.truetype(str(POLICE), 30)
        p = ImageFont.truetype(str(POLICE), 20)
    except OSError:
        f = p = None
    out = Image.new("RGB", (im.width, im.height + (96 if sous else 56)), (24, 26, 30))
    d = ImageDraw.Draw(out)
    d.text((16, 10), texte, fill=(236, 238, 242), font=f)
    if sous:
        d.text((16, 52), sous, fill=(170, 176, 186), font=p)
    out.paste(im, (0, out.height - im.height))
    return out


CAM = (3.2, 1.62, 0.0)
LACET = -14.0
vues = [("sans", 0, "Aujourd'hui : sans mipmaps"), ("mip2", 2, "Mipmaps 2 niveaux (profil Performance)"),
        ("mip4", 4, "Mipmaps 4 niveaux (profils Équilibré et Qualité)")]
images = {}
for cle, n, nom in vues:
    images[cle] = scene(n, CAM, LACET)
    titre(images[cle], nom, "Écran 1920 x 1080, distance de vue 6 tronçons, herbe et gravier de nos packs").save(
        OUT / ("mipmaps_%s.png" % cle), optimize=True)
    print("vue", cle)

# zoom sur le loin (même endroit dans les trois images), agrandi 3 fois au plus proche : un carré = un point
ZX, ZY, ZW, ZH = 820, 464, 320, 110
planche = Image.new("RGB", (ZW * 3 + 32, (ZH * 3 + 46) * 3 + 70), (24, 26, 30))
d = ImageDraw.Draw(planche)
try:
    f = ImageFont.truetype(str(POLICE), 26)
    p = ImageFont.truetype(str(POLICE), 18)
except OSError:
    f = p = None
d.text((16, 12), "Le sol de 10 à 90 blocs, agrandi 3 fois : un carré = un point de l'écran", fill=(236, 238, 242), font=f)
y = 70
for cle, n, nom in vues:
    d.text((16, y), nom, fill=(200, 204, 212), font=p)
    zoom = images[cle].crop((ZX, ZY, ZX + ZW, ZY + ZH)).resize((ZW * 3, ZH * 3), Image.NEAREST)
    planche.paste(zoom, (16, y + 28))
    y += ZH * 3 + 46
planche.save(OUT / "mipmaps_loin_zoom.png", optimize=True)
print("zoom")

# fourmillement : le joueur avance de 0,07 bloc par image (marche à 60 images/s) ; part des points du loin qui changent
# de couleur d'une image à l'autre, et petite animation (gauche : sans mipmaps ; droite : 2 niveaux)
images_gif = []
changes = {"sans": [], "mip2": [], "mip4": []}
prec = {}
for k in range(16):
    cam = (CAM[0], CAM[1], CAM[2] + 0.07 * k)
    cadres = {}
    for cle, n, _ in vues:
        im = scene(n, cam, LACET, W=1920, H=1080)
        z = np.asarray(im.crop((ZX, ZY, ZX + ZW, ZY + ZH)), np.int16)
        if cle in prec:
            changes[cle].append(float((np.abs(z - prec[cle]).max(-1) > 24).mean()))
        prec[cle] = z
        cadres[cle] = im.crop((ZX, ZY, ZX + ZW, ZY + ZH)).resize((ZW * 2, ZH * 2), Image.NEAREST)
    cadre = Image.new("RGB", (ZW * 4 + 24, ZH * 2 + 40), (24, 26, 30))
    dd = ImageDraw.Draw(cadre)
    dd.text((8, 8), "sans mipmaps (aujourd'hui)", fill=(236, 238, 242), font=p)
    dd.text((ZW * 2 + 24, 8), "mipmaps 2 niveaux", fill=(236, 238, 242), font=p)
    cadre.paste(cadres["sans"], (0, 40))
    cadre.paste(cadres["mip2"], (ZW * 2 + 24, 40))
    images_gif.append(cadre)
    print("image", k)
images_gif[0].save(OUT / "mipmaps_fourmillement.gif", save_all=True, append_images=images_gif[1:], duration=70, loop=0,
                   optimize=True)
with open(OUT / "mipmaps_mesures.txt", "w", encoding="utf-8") as fo:
    for cle, n, nom in vues:
        v = changes[cle]
        fo.write("%s : %.0f %% des points du loin changent de couleur d'une image à l'autre en marchant\n"
                 % (nom, 100 * sum(v) / len(v)))
print(open(OUT / "mipmaps_mesures.txt", encoding="utf-8").read())
