#!/usr/bin/env python3
"""
Icône de la rampe triangle dans la roue du plan (textures/gui/pieces/triangle_ramp.png, 64 x 64), au style des autres
icônes de pièces : vue de trois quarts, faces à plat (dessus clair, côtés sombres), contour fin, petite ombre douce.
Dessinée 4 fois plus grande puis réduite. Écrit aussi le schéma au trait du plan tenu en main
(textures/item/plan/schematic/triangle_ramp.png, 256 x 192, même trait et même échelle que celui de la rampe, dont la
barre d'échelle est reprise). On modifie ce script, jamais les images.

    python modules/bases/outils/icone_rampe_triangle.py              écrit l'icône et le schéma dans les ressources du mod
    python modules/bases/outils/icone_rampe_triangle.py --apercu D   les écrit dans le dossier D
"""
import argparse
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

RACINE = Path(__file__).resolve().parents[1]
TEX = RACINE / "src" / "client" / "resources" / "assets" / "crac" / "textures"
SORTIE = TEX / "gui" / "pieces" / "triangle_ramp.png"
SCHEMA = TEX / "item" / "plan" / "schematic" / "triangle_ramp.png"
SCHEMA_RAMPE = TEX / "item" / "plan" / "schematic" / "ramp.png"
TRAIT_SCHEMA = (236, 246, 255, 240)
SS = 4
TAILLE = 64
DESSUS = (222, 219, 207, 255)
COTE = (97, 96, 91, 255)
COTE_CLAIR = (150, 148, 140, 255)
TRAIT = (46, 45, 42, 255)

# rampe triangle (repère local : u, v au sol, y vers le haut) ; coin bas devant, côté haut au fond
H = 48.0
TRI = [(0.0, 0.0), (48.0, 0.0), (24.0, 24.0 * math.sqrt(3))]


def projeter(u, y, v):
    """Vue de trois quarts : axe u vers la droite et le bas, axe v vers la gauche et le bas, y vers le haut."""
    a = math.radians(30)
    x = (u - v) * math.cos(a)
    yy = (u + v) * math.sin(a) - y
    return x, yy


def faces():
    (ax, az), (bx, bz), (cx, cz) = TRI
    # côté haut = v0-v1 (au fond), coin bas = v2 (devant)
    haut_a, haut_b, bas = (ax, H, az), (bx, H, bz), (cx, 0.0, cz)
    pied_a, pied_b = (ax, 0.0, az), (bx, 0.0, bz)
    return [
        ("arriere", [pied_a, pied_b, haut_b, haut_a], COTE),
        ("gauche", [pied_a, haut_a, bas], COTE_CLAIR),
        ("droite", [pied_b, bas, haut_b], COTE),
        ("dessus", [haut_a, haut_b, bas], DESSUS),
    ]


def dessiner():
    n = TAILLE * SS
    pts2 = {}
    tous = []
    for _, poly, _ in faces():
        for p in poly:
            tous.append(projeter(*p))
    xs = [p[0] for p in tous]
    ys = [p[1] for p in tous]
    echelle = (TAILLE - 12) * SS / max(max(xs) - min(xs), max(ys) - min(ys))
    ox = n / 2 - (max(xs) + min(xs)) / 2 * echelle
    oy = n / 2 - (max(ys) + min(ys)) / 2 * echelle

    def ecran(p):
        x, y = projeter(*p)
        return ox + x * echelle, oy + y * echelle

    img = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    ombre = Image.new("RGBA", (n, n), (0, 0, 0, 0))
    od = ImageDraw.Draw(ombre)
    sol = [ecran((u, 0.0, v)) for u, v in TRI]
    od.polygon([(x + 2 * SS, y + 3 * SS) for x, y in sol], fill=(0, 0, 0, 70))
    ombre = ombre.filter(ImageFilter.GaussianBlur(3 * SS))
    img.alpha_composite(ombre)
    d = ImageDraw.Draw(img)
    # ordre du fond vers l'avant ; une face qui tourne le dos (sens des points à l'écran) n'est pas dessinée
    for nom, poly, couleur in faces():
        e = [ecran(p) for p in poly]
        aire = sum(e[i][0] * e[(i + 1) % len(e)][1] - e[(i + 1) % len(e)][0] * e[i][1] for i in range(len(e)))
        if nom != "dessus" and aire <= 0:
            continue
        d.polygon(e, fill=couleur)
        d.line(e + [e[0]], fill=TRAIT, width=SS, joint="curve")
    return img.resize((TAILLE, TAILLE), Image.LANCZOS)


def schema():
    """Trait de la rampe triangle (coin bas devant, côté haut au fond), dans le cadre du schéma de la rampe carrée."""
    ref = Image.open(SCHEMA_RAMPE).convert("RGBA")
    img = Image.new("RGBA", ref.size, (0, 0, 0, 0))
    # la barre d'échelle du bas, telle quelle
    img.paste(ref.crop((0, 160, ref.width, ref.height)), (0, 160))
    (ax, az), (bx, bz), (cx, cz) = TRI
    bas = [(ax, 0.0, az), (bx, 0.0, bz), (cx, 0.0, cz)]
    haut = [(ax, H, az), (bx, H, bz)]
    aretes = [(bas[0], bas[1]), (bas[1], bas[2]), (bas[2], bas[0]), (bas[0], haut[0]), (bas[1], haut[1]),
              (haut[0], haut[1]), (haut[0], bas[2]), (haut[1], bas[2])]
    pts = [projeter(*p) for a in aretes for p in a]
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    # même place que la figure de la rampe : x 24..152, y 40..150
    k = min(128 / (max(xs) - min(xs)), 110 / (max(ys) - min(ys)))
    ox = 88 - (max(xs) + min(xs)) / 2 * k
    oy = 95 - (max(ys) + min(ys)) / 2 * k
    d = ImageDraw.Draw(img)
    for a, b in aretes:
        pa, pb = projeter(*a), projeter(*b)
        d.line([(ox + pa[0] * k, oy + pa[1] * k), (ox + pb[0] * k, oy + pb[1] * k)], fill=TRAIT_SCHEMA, width=2)
    return img


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--apercu", type=Path, help="dossier où écrire les deux images au lieu du mod")
    args = ap.parse_args()
    icone, sch = (args.apercu / "triangle_ramp.png", args.apercu / "triangle_ramp_schema.png") if args.apercu else (SORTIE, SCHEMA)
    for out, img in ((icone, dessiner()), (sch, schema())):
        out.parent.mkdir(parents=True, exist_ok=True)
        img.save(out)
        print("écrit :", out)


if __name__ == "__main__":
    main()
