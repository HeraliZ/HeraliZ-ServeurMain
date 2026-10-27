#!/usr/bin/env python3
"""
Icônes d'inventaire des armes à feu TaCZ, rendues HORS DU JEU depuis le vrai modèle 3D et la vraie texture de chaque arme
(gunpacks de serveur/tacz/), puis posées à la place de l'image « slot » du gunpack : le jeu n'a plus qu'à lire une image
du pack, rien n'est photographié en jeu.

Rendu : géométrie reconstruite comme TaCZ par modules/armes/outils/apercu_camos/prep.py (mêmes pièces cachées :
variantes de chargeur, lunettes « scope_ », mains, douilles…), même cadrage que l'icône validée (vue « icone » de
apercu.html : profil droit, canon vers la droite, penchée de ROULIS degrés, sans perspective), éclairage des objets de
Minecraft. Moteur logiciel (numpy) : rendu en TAILLE x SUR, réduit en moyenne -> bords lisses, aucun escalier.

Couteaux (fiche arkaniaz:knife_data) jamais touchés : chaque skin garde l'image de son gunpack.

usage :
  python outils/icones_armes.py --apercu DOSSIER [--armes ak47,m4a1]   planche + icônes dans DOSSIER, rien n'est changé
  python outils/icones_armes.py --installer [--armes ...]              remplace les images « slot » des gunpacks
Numpy et Pillow.
"""
import argparse, glob, json, math, os, re, subprocess, sys, tempfile

import numpy as np
from PIL import Image, ImageDraw

RACINE = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
TACZ = os.path.join(RACINE, 'serveur', 'tacz')
PREP = os.path.join(RACINE, 'modules', 'armes', 'outils', 'apercu_camos', 'prep.py')
TAILLE = 128          # icône finale (px) ; le jeu la réduit lui-même avec ses niveaux de détail (IconesPack)
SUR = 4               # rendu 4 fois plus grand, puis moyenne : bords lisses
ROULIS = -34.0        # comme l'icône validée (IconesArmes.ROULIS, vue « icone » de apercu.html)
MARGE = 0.04
COUTEAU = 'arkaniaz:knife_data'


def lire_json(chemin):
    """JSON des gunpacks : certains ont des commentaires ou des virgules en trop."""
    t = open(chemin, encoding='utf-8-sig').read()
    try:
        return json.loads(t)
    except ValueError:
        t = re.sub(r'^\s*//.*$', '', t, flags=re.M)
        t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)
        t = re.sub(r',(\s*[}\]])', r'\1', t)
        t = re.sub(r'([{,]\s*)([A-Za-z_][A-Za-z0-9_]*)(\s*:)', r'\1"\2"\3', t)
        return json.loads(t)


def chercher(ns_id, sous, ext):
    """ns:chemin -> fichier dans l'un des gunpacks (assets/<ns>/<sous>/<chemin><ext>)."""
    ns, ch = ns_id.split(':', 1)
    for f in glob.glob(os.path.join(TACZ, '*', 'assets', ns, sous, ch + ext)):
        return f
    return None


def armes_a_feu():
    """[(nom, modèle, texture, image slot)] une fois par affichage, couteaux exclus."""
    vus, out = set(), []
    for idx in sorted(glob.glob(os.path.join(TACZ, '*', 'data', '*', 'index', 'guns', '*.json'))):
        try:
            d = lire_json(idx)
        except ValueError:
            print('illisible :', os.path.relpath(idx, RACINE))
            continue
        if d.get('data') == COUTEAU or not d.get('display') or d['display'] in vus:
            continue
        vus.add(d['display'])
        ns, ch = d['display'].split(':', 1)
        disp = chercher(ns + ':' + ch, 'display/guns', '.json')
        if not disp:
            continue
        a = lire_json(disp)
        modele = chercher(a.get('model', ''), 'geo_models', '.json') if ':' in a.get('model', '') else None
        texture = chercher(a.get('texture', ''), 'textures', '.png') if ':' in a.get('texture', '') else None
        slot = a.get('slot')
        if not (modele and texture and slot and ':' in slot):
            print('incomplet :', d['display'])
            continue
        sns, sch = slot.split(':', 1)
        dossier_pack = disp.split(os.sep + 'assets' + os.sep)[0]
        slot_png = chercher(slot, 'textures', '.png') or os.path.join(dossier_pack, 'assets', sns, 'textures', sch + '.png')
        nom = os.path.splitext(os.path.basename(idx))[0]
        out.append((nom, modele, texture, slot_png))
    return out


def RX(deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return np.array([[1, 0, 0, 0], [0, c, -s, 0], [0, s, c, 0], [0, 0, 0, 1]])


def RY(deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return np.array([[c, 0, s, 0], [0, 1, 0, 0], [-s, 0, c, 0], [0, 0, 0, 1]])


def RZ(deg):
    a = math.radians(deg); c, s = math.cos(a), math.sin(a)
    return np.array([[c, -s, 0, 0], [s, c, 0, 0], [0, 0, 1, 0], [0, 0, 0, 1]])


def T(x, y, z):
    m = np.eye(4); m[:3, 3] = (x, y, z); return m


def canon(d):
    """Axe du canon dans le repère du rendu (même calcul que chargerArme de apercu.html)."""
    pos = np.array(d['pos']).reshape(-1, 3)[::7]
    cam = np.array(d['cam']).reshape(-1, 3)[::7, 0]
    best, bestc = 0, 0.0
    for k in range(3):
        x = pos[:, k]
        den = math.sqrt(max(1e-12, x.var() * cam.var()))
        c = ((x * cam).mean() - x.mean() * cam.mean()) / den
        if abs(c) > abs(bestc):
            best, bestc = k, c
    v = [0, 0, 0]; v[best] = 1 if bestc >= 0 else -1
    return v


def rendre(d, tex, taille=TAILLE, sur=SUR):
    """Icône RGBA (taille x taille) : la vue « icone » de apercu.html, en logiciel."""
    W = taille * sur
    P = np.array(d['pos']).reshape(-1, 3, 3)
    N = np.array(d['nrm']).reshape(-1, 3, 3)[:, 0]
    UV = np.array(d['uv']).reshape(-1, 3, 2)
    c = d['center']
    k = canon(d)
    yaw = (90 if k[0] > 0 else -90) if k[0] else (180 if k[2] > 0 else 0)
    # mêmes matrices que la vue « icone » de apercu.html
    R = RZ(ROULIS) @ RY(-90) @ RY(-yaw) @ T(-c[0], -c[1], -c[2])
    v = np.einsum('ij,tkj->tki', R[:3, :3], P) + R[:3, 3]
    n = N @ R[:3, :3].T
    x0, x1 = v[..., 0].min(), v[..., 0].max()
    y0, y1 = v[..., 1].min(), v[..., 1].max()
    demi = max(x1 - x0, y1 - y0) / 2 * (1 + MARGE)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    sx = (v[..., 0] - cx) / demi * W / 2 + W / 2
    sy = W / 2 - (v[..., 1] - cy) / demi * W / 2
    z = v[..., 2]   # vers la caméra : plus grand = plus près
    # éclairage d'icône (dans la vue) : lumière douce venant de devant, en haut à gauche, fond assez clair pour garder
    # les couleurs de la texture (l'éclairage des entités de Minecraft, vu de profil, assombrissait tout)
    L = np.array([-0.35, 0.55, 1.0]); L /= np.linalg.norm(L)
    li = np.minimum(1.08, 0.62 + 0.5 * np.maximum(0, n @ L))
    th, tw = tex.shape[:2]
    prof = np.full((W, W), -1e30)
    img = np.zeros((W, W, 4))
    for t in range(len(v)):
        X, Y, Z = sx[t], sy[t], z[t]
        aire = (X[1] - X[0]) * (Y[2] - Y[0]) - (X[2] - X[0]) * (Y[1] - Y[0])
        if aire >= -1e-9:   # face arrière (écran : y vers le bas, donc sens inversé)
            continue
        bx0, bx1 = max(int(math.floor(X.min())), 0), min(int(math.ceil(X.max())), W - 1)
        by0, by1 = max(int(math.floor(Y.min())), 0), min(int(math.ceil(Y.max())), W - 1)
        if bx0 > bx1 or by0 > by1:
            continue
        gx, gy = np.meshgrid(np.arange(bx0, bx1 + 1) + 0.5, np.arange(by0, by1 + 1) + 0.5)
        w0 = ((X[1] - gx) * (Y[2] - gy) - (X[2] - gx) * (Y[1] - gy)) / aire
        w1 = ((X[2] - gx) * (Y[0] - gy) - (X[0] - gx) * (Y[2] - gy)) / aire
        w2 = 1 - w0 - w1
        dedans = (w0 >= 0) & (w1 >= 0) & (w2 >= 0)
        if not dedans.any():
            continue
        zz = w0 * Z[0] + w1 * Z[1] + w2 * Z[2]
        bloc = prof[by0:by1 + 1, bx0:bx1 + 1]
        dedans &= zz > bloc
        if not dedans.any():
            continue
        u = w0 * UV[t, 0, 0] + w1 * UV[t, 1, 0] + w2 * UV[t, 2, 0]
        vv = w0 * UV[t, 0, 1] + w1 * UV[t, 1, 1] + w2 * UV[t, 2, 1]
        tx = np.clip((u * tw).astype(int), 0, tw - 1)
        ty = np.clip((vv * th).astype(int), 0, th - 1)
        coul = tex[ty, tx]
        dedans &= coul[..., 3] >= 0.1
        if not dedans.any():
            continue
        bloc[dedans] = zz[dedans]
        cible = img[by0:by1 + 1, bx0:bx1 + 1]
        cible[dedans, :3] = coul[dedans, :3] * li[t]
        cible[dedans, 3] = 1.0
    # réduction en moyenne, couleurs prémultipliées (pas de liseré sombre sur les bords)
    pre = img.copy(); pre[..., :3] *= pre[..., 3:4]
    pre = pre.reshape(taille, sur, taille, sur, 4).mean(axis=(1, 3))
    a = pre[..., 3:4]
    rgb = np.where(a > 0, pre[..., :3] / np.maximum(a, 1e-6), 0)
    out = np.concatenate([rgb, a], axis=-1)
    return Image.fromarray(np.clip(out * 255 + 0.5, 0, 255).astype(np.uint8), 'RGBA')


def icone(nom, modele, texture, travail):
    subprocess.run([sys.executable, PREP, modele, texture, travail, nom], check=True, stdout=subprocess.DEVNULL)
    d = json.load(open(os.path.join(travail, nom + '.json')))
    tex = np.asarray(Image.open(texture).convert('RGBA'), dtype=np.float64) / 255.0
    return rendre(d, tex)


def planche(paires, sortie):
    """Avant (image du gunpack) / après, sur un fond gris de case d'inventaire, en 64 px et en 32 px."""
    col, lig = 4, (len(paires) + 3) // 4
    L, H = 300, 140
    fond = Image.new('RGBA', (col * L, lig * H), (40, 42, 48, 255))
    dr = ImageDraw.Draw(fond)
    for i, (nom, avant, apres) in enumerate(paires):
        x, y = (i % col) * L, (i // col) * H
        dr.text((x + 6, y + 4), nom, fill=(220, 220, 220, 255))
        for j, im in enumerate((avant, apres)):
            if im is None:
                continue
            bx = x + 6 + j * 150
            for taille, dy in ((64, 20), (32, 90)):
                dr.rectangle([bx, y + dy, bx + taille - 1, y + dy + taille - 1], fill=(139, 139, 139, 255))
                fond.alpha_composite(im.resize((taille, taille), Image.LANCZOS), (bx, y + dy))
        dr.text((x + 76, y + 104), 'avant', fill=(150, 150, 150, 255))
        dr.text((x + 226, y + 104), 'apres', fill=(150, 150, 150, 255))
    fond.convert('RGB').save(sortie)


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--apercu')
    p.add_argument('--installer', action='store_true')
    p.add_argument('--armes', help='noms séparés par des virgules (nom du fichier index)')
    a = p.parse_args()
    if not a.apercu and not a.installer:
        p.error('--apercu DOSSIER ou --installer')
    armes = armes_a_feu()
    if a.armes:
        voulu = set(a.armes.split(','))
        armes = [x for x in armes if x[0] in voulu]
    print(len(armes), 'armes à feu')
    paires, echecs = [], []
    with tempfile.TemporaryDirectory() as travail:
        for nom, modele, texture, slot in armes:
            try:
                im = icone(nom, modele, texture, travail)
            except Exception as e:   # un modèle illisible garde son image d'origine
                echecs.append((nom, str(e)[:120]))
                continue
            if a.apercu:
                os.makedirs(a.apercu, exist_ok=True)
                im.save(os.path.join(a.apercu, nom + '.png'))
                avant = Image.open(slot).convert('RGBA') if os.path.exists(slot) else None
                paires.append((nom, avant, im))
            else:
                os.makedirs(os.path.dirname(slot), exist_ok=True)
                im.save(slot, optimize=True)
            print('ok', nom)
    if a.apercu and paires:
        planche(paires, os.path.join(a.apercu, '_planche.png'))
    for nom, e in echecs:
        print('ECHEC', nom, e)


if __name__ == '__main__':
    main()
