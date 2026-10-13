"""Panneau Curios au style de l'inventaire du pack imposé (heraliz_interface).

L'inventaire du joueur est l'écran de Curios (HeraliZ Armurerie, `client/curios/InventaireCurios`) : son panneau de
slots, à gauche de l'inventaire, prend les couleurs, les bords et les cases de l'inventaire du pack au lieu du gris
clair de Curios. Refait, dans le pack (`src/main/resources/resourcepacks/textures/assets/`) :

- `curios/textures/gui/inventory_revamp.png` : texture de l'écran de Curios 5.14 (menu « V2 »), même disposition que
  celle du jar de Curios (lu dans `serveur/mods-tiers/`, jamais modifié) : panneau, colonne de cases, pastilles
  « afficher sur le joueur » ; les autres zones (boutons de page, onglet cosmétique) passent aux gris du pack ;
- `heraliz_armory/textures/slot/*.png` : icônes des slots vides de l'Armurerie (masque, gilet, gants, genouillères),
  dessinées en gris foncé pour les cases claires de Curios, éclaircies au gris des icônes d'armure du pack (sinon
  invisibles sur les cases sombres).

Jamais retouché à la main : on modifie cet outil puis on le relance.

    python mods/heraliz-interface/outils/curios_textures.py
"""
import glob
import io
import os
import sys
import zipfile

from PIL import Image

ICI = os.path.dirname(os.path.abspath(__file__))
RACINE = os.path.normpath(os.path.join(ICI, "..", "..", ".."))
PACK = os.path.join(ICI, "..", "src", "main", "resources", "resourcepacks", "textures", "assets")
INVENTAIRE = os.path.join(PACK, "minecraft", "textures", "gui", "container", "inventory.png")
SORTIE = os.path.join(PACK, "curios", "textures", "gui", "inventory_revamp.png")
ICONES_ARMURERIE = os.path.join(RACINE, "mods", "heraliz-armory", "src", "client", "resources", "assets",
                                "heraliz_armory", "textures", "slot")
SORTIE_ICONES = os.path.join(PACK, "heraliz_armory", "textures", "slot")
TEXTURE_CURIOS = "assets/curios/textures/gui/inventory_revamp.png"

# Couleurs de l'inventaire du pack (relevées dans inventory.png, voir lire_palette)
NOIR = (0, 0, 0, 255)
CLAIR = (0x78, 0x78, 0x78, 255)     # bord haut et gauche
FOND = (0x48, 0x49, 0x4B, 255)      # fond des panneaux
DROITE = (0x59, 0x59, 0x59, 255)    # bord droit, trait au-dessus du bas
OMBRE = (0x2A, 0x2A, 0x28, 255)     # bas des panneaux, haut et gauche des cases
CASE = (0x3E, 0x3F, 0x3F, 255)      # fond des cases
ICONE = (0x55, 0x55, 0x55, 255)     # icônes des cases vides (armure, bouclier) du pack

# Gris de l'interface de Minecraft (texture de Curios) -> gris du pack, pour les zones que l'écran n'utilise pas ici
REMPLACEMENTS = {
    (0xFF, 0xFF, 0xFF): CLAIR[:3],
    (0xC6, 0xC6, 0xC6): FOND[:3],
    (0x8B, 0x8B, 0x8B): CASE[:3],
    (0x55, 0x55, 0x55): OMBRE[:3],
    (0x37, 0x37, 0x37): OMBRE[:3],
}

# Zones de la texture de Curios 5.14.1 (CuriosScreenV2.renderBg, RenderButton) : panneau x 91..122 (haut : lignes
# 0..158, bas : lignes 159..165), colonne de cases x 7, y 7 (8 cases de 18), pastilles 8 x 8 en (75, 0) et (83, 0).
PANNEAU_X, PANNEAU_L = 91, 32
COLONNE_X, COLONNE_Y, CASES = 7, 7, 8
GAUCHE_L, GAUCHE_H = 31, 158
PASTILLE_OUI, PASTILLE_NON = (75, 0), (83, 0)


def jar_curios():
    jars = sorted(glob.glob(os.path.join(RACINE, "serveur", "mods-tiers", "curios-forge-*.jar")))
    if not jars:
        sys.exit("jar de Curios introuvable dans serveur/mods-tiers/")
    return jars[-1]


def texture_curios():
    with zipfile.ZipFile(jar_curios()) as z:
        return Image.open(io.BytesIO(z.read(TEXTURE_CURIOS))).convert("RGBA")


def lire_palette(inv):
    """Vérifie que l'inventaire du pack a toujours les couleurs ci-dessus (sinon : mettre l'outil à jour)."""
    px = inv.load()
    attendu = {(0, 0): NOIR, (1, 1): CLAIR, (5, 5): FOND, (174, 5): DROITE, (1, 163): OMBRE, (16, 16): CASE}
    ecarts = [(xy, px[xy], c) for xy, c in attendu.items() if px[xy] != c]
    if ecarts:
        sys.exit("couleurs de inventory.png changées, outil à mettre à jour : %s" % ecarts)


def case_du_pack(inv):
    """Une case de 18 x 18 du pack (la première case d'armure : la même que l'artisanat et la barre d'objets)."""
    return inv.crop((7, 7, 25, 25))


def panneau(img, x0, y0, l, h, bas=True):
    """Panneau du pack : contour noir, bord clair en haut et à gauche, bord droit, deux lignes d'ombre en bas."""
    px = img.load()
    for y in range(y0, y0 + h):
        for x in range(x0, x0 + l):
            px[x, y] = FOND
    for x in range(x0, x0 + l):
        px[x, y0] = NOIR
        px[x, y0 + 1] = CLAIR
    for y in range(y0, y0 + h):
        px[x0, y] = NOIR
        px[x0 + l - 1, y] = NOIR
        if y > y0:
            px[x0 + 1, y] = CLAIR
        if y > y0 + 1:
            px[x0 + l - 2, y] = DROITE
    px[x0 + l - 2, y0 + 1] = CLAIR
    if bas:
        yb = y0 + h - 4
        for x in range(x0 + 2, x0 + l - 1):
            px[x, yb] = DROITE
        for y in (yb + 1, yb + 2):
            for x in range(x0 + 1, x0 + l - 1):
                px[x, y] = OMBRE
        for x in range(x0, x0 + l):
            px[x, y0 + h - 1] = NOIR


def pastille(img, x0, y0, allumee):
    """Pastille « afficher sur le joueur » : rond de 6 px (dans une case de 8), claire si allumée, sombre sinon."""
    px = img.load()
    for y in range(y0, y0 + 8):
        for x in range(x0, x0 + 8):
            px[x, y] = (0, 0, 0, 0)
    if allumee:
        haut, milieu, bas = (0xB4, 0xB4, 0xB4, 255), (0x8C, 0x8C, 0x8C, 255), (0x66, 0x66, 0x66, 255)
    else:
        haut, milieu, bas = DROITE, CASE, OMBRE
    bord = (0x14, 0x14, 0x13, 255)
    motif = [
        " KKKK ",
        "KHHHMK",
        "KHMMBK",
        "KHMMBK",
        "KMBBBK",
        " KKKK ",
    ]
    couleurs = {"K": bord, "H": haut, "M": milieu, "B": bas}
    for j, ligne in enumerate(motif):
        for i, c in enumerate(ligne):
            if c != " ":
                px[x0 + 1 + i, y0 + 1 + j] = couleurs[c]


def texture(inv):
    img = texture_curios()
    if img.size != (256, 256):
        sys.exit("texture de Curios inattendue (%s), outil à mettre à jour" % (img.size,))
    px = img.load()
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a and (r, g, b) in REMPLACEMENTS:
                px[x, y] = REMPLACEMENTS[(r, g, b)] + (a,)
    # panneau (haut puis bas), comme l'inventaire du pack : 7 px de marge autour des cases
    for y in range(0, 166):
        for x in range(PANNEAU_X, PANNEAU_X + PANNEAU_L):
            px[x, y] = (0, 0, 0, 0)
    panneau(img, PANNEAU_X, 0, PANNEAU_L, 166)
    # colonne de cases : les cases de l'inventaire du pack, dans un panneau du même style
    for y in range(0, GAUCHE_H):
        for x in range(0, GAUCHE_L):
            px[x, y] = (0, 0, 0, 0)
    panneau(img, 0, 0, GAUCHE_L, GAUCHE_H)
    case = case_du_pack(inv)
    for k in range(CASES):
        img.paste(case, (COLONNE_X, COLONNE_Y + 18 * k))
    pastille(img, *PASTILLE_OUI, True)
    pastille(img, *PASTILLE_NON, False)
    return img


def icones():
    """Icônes de l'Armurerie éclaircies (même forme, gris des icônes d'armure du pack)."""
    faites = []
    for f in sorted(glob.glob(os.path.join(ICONES_ARMURERIE, "*.png"))):
        im = Image.open(f).convert("RGBA")
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                a = px[x, y][3]
                if a:
                    px[x, y] = ICONE[:3] + (a,)
        os.makedirs(SORTIE_ICONES, exist_ok=True)
        im.save(os.path.join(SORTIE_ICONES, os.path.basename(f)))
        faites.append(os.path.basename(f))
    return faites


def main():
    inv = Image.open(INVENTAIRE).convert("RGBA")
    lire_palette(inv)
    img = texture(inv)
    os.makedirs(os.path.dirname(SORTIE), exist_ok=True)
    img.save(SORTIE)
    print("écrit :", os.path.relpath(SORTIE, RACINE))
    for n in icones():
        print("écrit :", os.path.relpath(os.path.join(SORTIE_ICONES, n), RACINE))


if __name__ == "__main__":
    main()
