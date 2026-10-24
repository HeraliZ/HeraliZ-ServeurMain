#!/usr/bin/env python3
"""
Mipmaps des blocs : Minecraft range toutes les textures de blocs et d'objets (dossiers textures/block et textures/item
de chaque mod) dans un seul grand atlas, et n'y fait des mipmaps (versions réduites pour le loin : pas de scintillement,
moins de travail pour la carte graphique) que jusqu'au niveau permis par la PLUS MAL TAILLÉE de toutes ces textures. Une
seule texture de 13 x 13 points coupe les mipmaps de toute la carte (journal du jeu : « limits mip level »).

Ce script cherche les textures dont un côté n'est pas un multiple de 16 (images animées : chaque image) :
- dans nos mods (mods/*/src) : il les retaille à la place (multiple de 16 le plus proche ; petites textures en pixels
  nets, grandes lissées ; le jeu lit les coordonnées de texture de 0 à 1, donc les modèles ne changent pas) ;
- dans les jars des autres auteurs (serveur/mods-tiers, client/mods-tiers) : jamais touchés ; une copie retaillée est
  posée dans le pack de textures imposé par heraliz_interface (même chemin, il passe devant le jar).

    python outils/mipmaps.py              liste ce qui coupe les mipmaps (rien n'est écrit)
    python outils/mipmaps.py --corriger   retaille nos textures et écrit les copies dans le pack imposé

À relancer après l'ajout d'un mod, d'un gunpack ou de textures de blocs. verifier.py peut l'appeler (code de sortie 1
s'il reste une texture fautive).
"""
import argparse
import glob
import io
import json
import re
import sys
import zipfile
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[1]
PACK = RACINE / "mods" / "heraliz-interface" / "src" / "main" / "resources" / "resourcepacks" / "textures"
PACKS_IMPOSES = [PACK, RACINE / "mods" / "heraliz-interface" / "src" / "main" / "resources" / "resourcepacks" / "herbe"]
ATLAS = re.compile(r"^assets/[^/]+/textures/(block|item)/.+\.png$")


def bon(n):
    return n % 16 == 0


def cible(n):
    return max(16, int(round(n / 16.0)) * 16)


def dims(data, meta):
    """(largeur d'une image, hauteur d'une image, nombre d'images, mcmeta lu) ; images animées empilées en hauteur."""
    im = Image.open(io.BytesIO(data))
    w, h = im.size
    fw, fh, m = w, h, None
    if meta is not None:
        try:
            m = json.loads(meta)
        except ValueError:
            m = {}
        a = m.get("animation") if isinstance(m, dict) else None
        if isinstance(a, dict):
            # même règle que Minecraft (AnimationMetadataSection.calculateFrameSize)
            if "width" in a:
                fw, fh = a["width"], a.get("height", h)
            elif "height" in a:
                fw, fh = w, a["height"]
            else:
                fw = fh = min(w, h)
    n = max(1, (w // fw) * (h // fh)) if fw and fh else 1
    return fw, fh, n, m


def retailler(data, meta):
    """Image (et mcmeta) retaillées : chaque image animée à un multiple de 16."""
    im = Image.open(io.BytesIO(data)).convert("RGBA")
    w, h = im.size
    fw, fh, _, m = dims(data, meta)
    nw, nh = cible(fw), cible(fh)
    filtre = Image.NEAREST if max(fw, fh) <= 32 else Image.LANCZOS
    cols, rangs = max(1, w // fw), max(1, h // fh)
    out = Image.new("RGBA", (cols * nw, rangs * nh))
    for j in range(rangs):
        for i in range(cols):
            morceau = im.crop((i * fw, j * fh, (i + 1) * fw, (j + 1) * fh)).resize((nw, nh), filtre)
            out.paste(morceau, (i * nw, j * nh))
    b = io.BytesIO()
    out.save(b, "PNG", optimize=True)
    nouveau_meta = None
    if m is not None:
        a = m.get("animation", {})
        if "width" in a:
            a["width"] = nw
        if "height" in a:
            a["height"] = nh
        nouveau_meta = (json.dumps(m, indent=2) + "\n").encode()
    return b.getvalue(), nouveau_meta, (fw, fh), (nw, nh)


def sources():
    """(chemin dans les assets, octets, mcmeta, origine) dans l'ordre où le jeu les empile : jars, nos mods, packs."""
    vus = {}
    for jar in sorted(glob.glob(str(RACINE / "serveur" / "mods-tiers" / "*.jar")) + glob.glob(str(RACINE / "client" / "mods-tiers" / "*.jar"))):
        try:
            z = zipfile.ZipFile(jar)
        except zipfile.BadZipFile:
            continue
        noms = set(z.namelist())
        for n in noms:
            if ATLAS.match(n):
                vus[n] = (z.read(n), z.read(n + ".mcmeta") if n + ".mcmeta" in noms else None, ("jar", jar))
    for p in sorted(glob.glob(str(RACINE / "mods" / "*" / "src" / "**" / "assets" / "*" / "textures" / "*" / "**" / "*.png"), recursive=True)):
        if "/_gabarit/" in p or "/resourcepacks/" in p:
            continue
        rel = p[p.index("assets/"):]
        if ATLAS.match(rel):
            mp = Path(p + ".mcmeta")
            vus[rel] = (Path(p).read_bytes(), mp.read_bytes() if mp.exists() else None, ("mod", p))
    for pack in PACKS_IMPOSES:
        for p in sorted(glob.glob(str(pack / "assets" / "*" / "textures" / "*" / "**" / "*.png"), recursive=True)):
            rel = p[p.index("assets/"):]
            if ATLAS.match(rel):
                mp = Path(p + ".mcmeta")
                vus[rel] = (Path(p).read_bytes(), mp.read_bytes() if mp.exists() else None, ("pack", p))
    return vus


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--corriger", action="store_true")
    a = ap.parse_args()
    fautives = []
    for rel, (data, meta, (genre, ou)) in sorted(sources().items()):
        try:
            fw, fh, _, _ = dims(data, meta)
        except Exception:
            continue
        if not (bon(fw) and bon(fh)):
            fautives.append((rel, data, meta, genre, ou, fw, fh))
    for rel, data, meta, genre, ou, fw, fh in fautives:
        print(f"[mipmaps] {fw}x{fh}  {rel}  ({'jar ' + Path(ou).name if genre == 'jar' else genre})")
    if not a.corriger:
        print(f"[mipmaps] {len(fautives)} texture(s) coupent les mipmaps de l'atlas des blocs")
        return 1 if fautives else 0
    for rel, data, meta, genre, ou, fw, fh in fautives:
        img, m, avant, apres = retailler(data, meta)
        dest = Path(ou) if genre in ("mod", "pack") else PACK / rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        dest.write_bytes(img)
        if m is not None:
            Path(str(dest) + ".mcmeta").write_bytes(m)
        print(f"[mipmaps] {avant[0]}x{avant[1]} -> {apres[0]}x{apres[1]}  {dest.relative_to(RACINE)}")
    print(f"[mipmaps] {len(fautives)} texture(s) retaillée(s)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
