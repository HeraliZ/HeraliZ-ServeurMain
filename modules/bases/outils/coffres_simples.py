#!/usr/bin/env python3
"""
Coffres de base « simples » (retours du propriétaire du 03/11 : « des modèles de coffre de base plus clean, sans notre nom
écrit dessus, des modèles simples qui ne buguent pas visuellement »). PROPOSITION, à valider en rendus avant d'installer.

Même gamme, mêmes tailles, mêmes ouvertures que coffres_modeles.py (rendus validés le 27/10), dessin simplifié :
  - plus aucun texte ni marquage (ni « HERALIZ », ni « HZ · 07 », ni « ARMES », ni étiquette « 07 ») ;
  - des caisses pleines et des couvercles plats, quelques pièces seulement (6 à 13 au lieu de 20 à 52) ;
  - aucune pièce tournée dans le modèle (plus de prismes à 8 pans), aucune face confondue (contrôle faces_confondues.py) ;
  - la couleur de la rareté reste (vert, bleu, violet, or), sur des matières propres ;
  - la caisse en métal (Commune) refaite dans le même style, mêmes dimensions (16 × 9 × 12 px).

    python modules/bases/outils/coffres_simples.py --apercu DOSSIER [--avant DOSSIER_OUTILS_AVANT]
        rendus (planche de chaque coffre, gamme, avant / après si --avant donne le dossier d'outils d'avant)
    python modules/bases/outils/coffres_simples.py --installer
        écrit dans le mod (seulement après validation) : les 4 coffres par coffres_modeles.installer, la caisse en métal
        (modèles fermé / ouvert, texture, variantes adossées par adossement.py)

Coordonnées en pixels de modèle (16 = 1 bloc), face avant au nord (z petit). Profondeur 13,75 px au plus (z 1,75 à 15,5), comme
les coffres amincis du 31/10 (rangée des murs, Adosse.PLACE_DANS_MUR) : contrôlé ci-dessous.
"""
import argparse
import json
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import coffres_modeles as cm  # noqa: E402

Modele = cm.Modele


# =============================================================================================== les coffres
def malle_chantier():
    """Peu commune : malle de chantier en planches vertes, deux sangles d'acier, couvercle plat, un fermoir."""
    m = Modele("malle_chantier", "peu_commune", "2 de large × 1 de haut", "large", 8)
    C = {"centre": (16, 6, 9), "sol": 1.0}
    B = m.B
    B("corps", (1.0, 0, 2.5), (31.0, 1.0, 15.5), "acier_noir")                                 # socle
    B("corps", (1.0, 1.0, 2.5), (31.0, 9.5, 3.5), "bois_vert", prm=C)                         # devant
    B("corps", (1.0, 1.0, 14.5), (31.0, 9.5, 15.5), "bois_vert", prm=dict(C, graine=1))       # dos
    B("corps", (1.0, 1.0, 3.5), (2.0, 9.5, 14.5), "bois_vert", prm=dict(C, graine=2))         # côtés
    B("corps", (30.0, 1.0, 3.5), (31.0, 9.5, 14.5), "bois_vert", prm=dict(C, graine=3))
    B("corps", (2.0, 1.0, 3.5), (30.0, 2.0, 14.5), "bois_brut", prm={"graine": 9})            # fond
    for x0 in (7.0, 23.4):                                                                    # sangles, devant
        B("corps", (x0, 1.0, 2.0), (x0 + 1.6, 9.5, 2.5), "acier_coin", prm={"rivets": False},
          faces=("north", "east", "west", "up", "down"))
    # couvercle plat : bascule vers l'arrière autour de la charnière (dos, en haut)
    m.os_mobile("couvercle", (16.0, 9.5, 15.5), "x", [(0, 0), (1, 82)])
    L = {"centre": (16, 6, 9)}
    B("couvercle", (0.5, 9.5, 2.0), (31.5, 11.5, 15.5), "bois_vert", prm=dict(L, graine=5))
    for x0 in (7.0, 23.4):
        B("couvercle", (x0, 11.5, 2.0), (x0 + 1.6, 11.75, 15.5), "acier_coin", prm={"rivets": False},
          faces=("up", "north", "south", "east", "west"))
    B("couvercle", (15.0, 8.0, 1.75), (17.0, 10.5, 2.0), "laiton", faces=("north", "east", "west", "up", "down"))
    return m


def casier_metallique():
    """Rare : casier de vestiaire en tôle bleue, porte pleine à aérations, une poignée."""
    m = Modele("casier_metallique", "rare", "1 de large × 2 de haut", "haut", 8)
    C = {"centre": (8, 16, 9)}
    B = m.B
    B("corps", (0.5, 0, 14.5), (15.5, 31.5, 15.5), "peinture_bleue", prm=C)                   # dos
    B("corps", (0.5, 0, 3.0), (1.5, 31.5, 14.5), "peinture_bleue", prm=dict(C, graine=1))     # côtés
    B("corps", (14.5, 0, 3.0), (15.5, 31.5, 14.5), "peinture_bleue", prm=dict(C, graine=2))
    B("corps", (1.5, 0, 3.0), (14.5, 1.5, 14.5), "peinture_bleue", prm=dict(C, graine=3))     # fond
    B("corps", (1.5, 30.0, 3.0), (14.5, 31.5, 14.5), "peinture_bleue", prm=dict(C, graine=4))  # dessus
    B("corps", (1.5, 22.0, 3.5), (14.5, 22.5, 14.5), "peinture_bleue", prm=dict(C, graine=5))  # étagère
    # porte : pivote autour de son bord est
    m.os_mobile("porte", (15.5, 16.0, 2.5), "y", [(0, 0), (1, -108)])
    P = {"fentes": [(26.0, 28.6), (3.0, 5.6)], "fentes_x": (4.5, 11.5), "centre": (8, 16, 9)}
    B("porte", (0.5, 0, 2.0), (15.5, 31.5, 3.0), "porte_casier", prm=P)
    B("porte", (2.0, 14.0, 1.75), (3.0, 18.0, 2.0), "verrou", faces=("north", "east", "west", "up", "down"))
    return m


def caisse_armes():
    """Épique : caisse d'armes en acier gris canon, bande violette, deux fermoirs violets, calage en mousse."""
    m = Modele("caisse_armes", "epique", "2 de large × 1 de haut", "large", 10)
    C = {"centre": (16, 6, 9), "bande": (3.0, 4.4)}
    B = m.B
    B("corps", (0.5, 0, 2.0), (31.5, 1.0, 15.5), "gomme")                                     # socle
    B("corps", (0.5, 1.0, 2.0), (31.5, 10.0, 3.0), "acier_arme", prm=C)                       # devant
    B("corps", (0.5, 1.0, 14.5), (31.5, 10.0, 15.5), "acier_arme", prm=dict(C, graine=1))     # dos
    B("corps", (0.5, 1.0, 3.0), (1.5, 10.0, 14.5), "acier_arme", prm=dict(C, graine=2))       # côtés
    B("corps", (30.5, 1.0, 3.0), (31.5, 10.0, 14.5), "acier_arme", prm=dict(C, graine=3))
    B("corps", (1.5, 1.0, 3.0), (30.5, 2.5, 14.5), "mousse", prm={"alveoles": True})         # mousse du fond
    # couvercle : petit à-coup (fermoirs qui sautent) puis l'ouverture vers l'arrière
    m.os_mobile("couvercle", (16.0, 10.0, 15.5), "x", [(0, 0), (0.15, 4), (1, 78)])
    L = {"centre": (16, 6, 9), "bande": (11.0, 12.0)}
    B("couvercle", (0.5, 10.0, 2.0), (31.5, 13.0, 15.5), "acier_arme", prm=L)
    for x0 in (4.0, 26.0):
        B("couvercle", (x0, 8.0, 1.75), (x0 + 2.0, 11.0, 2.0), "violet_anodise", faces=("north", "east", "west", "up", "down"))
    return m


def coffre_fort():
    """Légendaire : coffre-fort en acier graphite à filet doré, cadran carré, poignée qui tourne puis porte blindée."""
    m = Modele("coffre_fort", "legendaire", "1 de large × 2 de haut", "haut", 18)
    C = {"centre": (8, 16, 9), "filet": 0.8}
    B = m.B
    # profondeur 13,75 px au plus (rangée des murs) : de la poignée (z 1,75) au dos (z 15,5)
    B("corps", (0.5, 0, 14.75), (15.5, 31.5, 15.5), "acier_coffre", prm=C)                    # dos
    B("corps", (0.5, 0, 3.75), (1.5, 31.5, 14.75), "acier_coffre", prm=dict(C, graine=1))     # côtés
    B("corps", (14.5, 0, 3.75), (15.5, 31.5, 14.75), "acier_coffre", prm=dict(C, graine=2))
    B("corps", (1.5, 0, 3.75), (14.5, 2.0, 14.75), "acier_coffre", prm=dict(C, filet=None, graine=3))    # fond
    B("corps", (1.5, 30.0, 3.75), (14.5, 31.5, 14.75), "acier_coffre", prm=dict(C, filet=None, graine=4))  # dessus
    B("corps", (1.5, 15.0, 4.0), (14.5, 15.75, 14.75), "acier_coffre", prm=dict(C, filet=None, graine=5))  # étagère
    for y0 in (6.0, 22.0):                                                                     # charnières
        B("corps", (15.5, y0, 3.0), (16.0, y0 + 4.0, 4.0), "acier_noir")
    # porte blindée : la poignée tourne d'abord (débloque), puis la porte s'ouvre, lourde
    m.os_mobile("porte", (15.5, 16.0, 3.25), "y", [(0, 0), (0.3, 0), (1, -100)])
    m.os_mobile("poignee", (8.0, 12.0, 2.25), "z", [(0, 0), (0.3, -90), (1, -90)], parent="porte")
    D = {"centre": (8, 16, 9), "filet": 1.0, "mecanisme": True}
    B("porte", (1.5, 2.0, 2.75), (14.5, 30.0, 3.75), "acier_coffre", prm=dict(D, graine=10))
    B("porte", (5.2, 17.7, 2.6), (10.8, 23.3, 2.75), "plaque_or", faces=("north", "east", "west", "up", "down"))
    B("porte", (5.7, 18.2, 2.45), (10.3, 22.8, 2.6), "cadran", prm={"c": (8.0, 20.5), "r": 2.3},
      faces=("north", "east", "west", "up", "down"))
    B("poignee", (7.4, 11.4, 1.75), (8.6, 12.6, 2.75), "plaque_or", faces=("north", "east", "west", "up", "down"))
    B("poignee", (5.0, 11.6, 2.0), (11.0, 12.4, 2.5), "plaque_or")
    return m


def caisse_metal():
    """Commune : la caisse en métal (16 × 9 × 12 px, comme celle d'avant), acier mat, cerclages noirs, couvercle plat."""
    m = Modele("caisse_metal", "commune", "1 bloc", "large", 8)
    B = m.B
    B("corps", (0.5, 0, 2.5), (15.5, 6.0, 13.5), "acier_mat")
    for x0 in (2.5, 12.0):                                                                    # cerclages
        B("corps", (x0, 0, 2.0), (x0 + 1.5, 6.0, 2.5), "acier_noir", faces=("north", "east", "west", "up", "down"))
        B("corps", (x0, 0, 13.5), (x0 + 1.5, 6.0, 14.0), "acier_noir", faces=("south", "east", "west", "up", "down"))
    for x0, x1 in ((0.0, 0.5), (15.5, 16.0)):                                                 # poignées
        B("corps", (x0, 3.0, 6.0), (x1, 4.5, 10.0), "acier_noir", faces=("east", "west", "up", "down", "north", "south"))
    m.os_mobile("couvercle", (8.0, 6.0, 14.0), "x", [(0, 0), (1, 45)])
    B("couvercle", (0.0, 6.0, 2.0), (16.0, 9.0, 14.0), "acier_mat", prm={"graine": 3})
    B("couvercle", (7.0, 4.5, 1.6), (9.0, 7.0, 2.0), "acier_noir", faces=("north", "east", "west", "up", "down"))
    return m


COFFRES = [malle_chantier, casier_metallique, caisse_armes, coffre_fort]
NOMS = {"malle_chantier": ("Malle de chantier", "45 cases (9 × 5)"),
        "casier_metallique": ("Casier métallique", "54 cases (9 × 6)"),
        "caisse_armes": ("Caisse d'armes renforcée", "66 cases (11 × 6)"),
        "coffre_fort": ("Coffre-fort", "78 cases (13 × 6)"),
        "caisse_metal": ("Caisse en métal", "18 cases (9 × 2)")}


# =============================================================================================== caisse en métal : modèles
def modeles_caisse(cf):
    """Modèles fermé et ouvert de la caisse en métal (couvercle tourné de 45° vers l'arrière, comme avant)."""
    corps = cf.groupes["corps"]
    couv = cf.groupes["couvercle"]
    ouvert = []
    for el in couv:
        if "rotation" in el:
            raise SystemExit("pièce déjà tournée dans le couvercle de la caisse")
        ouvert.append(dict(el, rotation={"origin": [8.0, 6.0, 14.0], "axis": "x", "angle": 45}))
    return corps + couv, corps + ouvert


# =============================================================================================== rendus
def planche_avant_apres(avant_png, apres_png, sortie, titre):
    a, b = Image.open(avant_png), Image.open(apres_png)
    im = Image.new("RGB", (a.width + b.width + 30, max(a.height, b.height) + 70), (20, 22, 25))
    d = ImageDraw.Draw(im)
    d.text((20, 10), titre + " · AVANT", font=cm.police(30), fill=(236, 120, 110))
    d.text((a.width + 50, 10), "APRÈS (proposition, à valider)", font=cm.police(30), fill=(120, 220, 130))
    im.paste(a, (0, 70))
    im.paste(b, (a.width + 30, 70))
    im.save(sortie, optimize=True)


def planche_caisse(cf, dossier, avant_quads=None):
    """Caisse en métal : fermée et ouverte, de trois quarts et de face (et la caisse d'avant si donnée)."""
    ferme, ouvert = modeles_caisse(cf)
    tex = cf.tex
    a, b = cf.bornes()
    bornes = (a - np.array([1, 0, 6]), b + np.array([1, 6, 1]))
    S, w = 900, 420
    lignes = [("fermée, trois quarts", cm.VUES["trois_quarts"], ferme), ("ouverte, trois quarts", cm.VUES["trois_quarts"], ouvert),
              ("fermée, de face", cm.VUES["face"], ferme)]
    im = Image.new("RGB", (3 * w, w + 110), (20, 22, 25))
    nom_r, rgb = cm.RARETES["commune"]
    cm.titre(im, "Caisse en métal", f"{nom_r} · 1 bloc · 18 cases (9 × 2)", rgb)
    for k, (vn, (yaw, pitch), els) in enumerate(lignes):
        v = cm.Vue(yaw, pitch, bornes, S)
        r = cm.vers_image(cm.rendre(cm.quads_elements(els, tex), v, ombre_sol=(a[0], a[2], b[0], b[2], 0.0)), w)
        im.paste(r, (k * w, 100))
        ImageDraw.Draw(im).text((k * w + 14, 108), vn, font=cm.police(18, True), fill=(170, 170, 170))
    im.save(dossier / "caisse_metal.png", optimize=True)


def planche_caisse_actuelle(dossier):
    """La caisse en métal d'avant (modèles du mod), mêmes vues."""
    el, ouv, tex = cm.caisse_commune(False)
    a, b = np.array([0, 0, 2.0]), np.array([16, 9, 14.0])
    bornes = (a - np.array([1, 0, 6]), b + np.array([1, 6, 1]))
    S, w = 900, 420
    lignes = [("fermée, trois quarts", cm.VUES["trois_quarts"], el), ("ouverte, trois quarts", cm.VUES["trois_quarts"], ouv),
              ("fermée, de face", cm.VUES["face"], el)]
    im = Image.new("RGB", (3 * w, w + 110), (20, 22, 25))
    nom_r, rgb = cm.RARETES["commune"]
    cm.titre(im, "Caisse en métal", f"{nom_r} · 1 bloc · 18 cases (9 × 2)", rgb)
    for k, (vn, (yaw, pitch), els) in enumerate(lignes):
        v = cm.Vue(yaw, pitch, bornes, S)
        r = cm.vers_image(cm.rendre(cm.quads_elements(els, tex), v, ombre_sol=(a[0], a[2], b[0], b[2], 0.0)), w)
        im.paste(r, (k * w, 100))
        ImageDraw.Draw(im).text((k * w + 14, 108), vn, font=cm.police(18, True), fill=(170, 170, 170))
    im.save(dossier / "caisse_metal.png", optimize=True)


def planche_gamme(coffres, caisse, dossier):
    """Les cinq coffres côte à côte, à l'échelle, adossés à un mur, sur la grille des blocs."""
    ferme, _ = modeles_caisse(caisse)
    largeurs = [16, 32, 16, 32, 16]
    places = []
    x = 0.0
    for wd in reversed(largeurs):
        places.insert(0, x)
        x += wd + 14
    xfin = x - 14
    quads = cm.sol_et_mur(-16, xfin + 16, -24, 16, 16.0)
    quads += cm.quads_elements(ferme, caisse.tex, [("decal", (places[0], 0, 1.5))])
    for cf, x0 in zip(coffres, places[1:]):
        quads += cf.quads(0.0, decal=(x0, 0, 0.1))
    S = 3000
    v = cm.Vue(np.radians(180 - 14), np.radians(20), ((-10, 0, -10), (xfin + 10, 34, 16)), S, marge=0.97)
    img = cm.rendre(quads, v, img=cm.fond(S, (26, 28, 31), (16, 17, 19)))
    im = cm.vers_image(img, 2000)
    k = 2000 / S
    haut = v.ecran([[0, 40, 16], [xfin, 40, 16]])[:, 1].min() * k
    bas = v.ecran([[-16, 0, -10], [xfin + 16, 0, -10]])[:, 1].max() * k
    im = im.crop((0, int(haut) - 30, 2000, int(bas) + 10))
    bande = Image.new("RGB", (2000, im.height + 150), (20, 22, 25))
    bande.paste(im, (0, 0))
    d = ImageDraw.Draw(bande)
    fiches = [NOMS["caisse_metal"] + ("commune", "1 bloc")] + \
             [NOMS[c.m.nom] + (c.m.rarete, c.m.blocs) for c in coffres]
    for (nom, cases, rar, dims), x0, wd in zip(fiches, places, largeurs):
        px = v.ecran([[x0 + wd / 2, 0, 0]])[0][0] * k
        nom_r, rgb = cm.RARETES[rar]
        for j, (txt, f, c) in enumerate(((nom, cm.police(28), (236, 236, 236)), (nom_r, cm.police(24, True), rgb),
                                         (dims, cm.police(20, True), (180, 180, 180)), (cases, cm.police(20, True), (180, 180, 180)))):
            tw = d.textlength(txt, font=f)
            d.text((px - tw / 2, im.height + 12 + j * 32), txt, font=f, fill=c)
    bande.save(dossier / "gamme.png", optimize=True)


# =============================================================================================== installation
def installer_caisse(cf):
    import adossement
    tex = "crac:block/caisse_metal"
    cf.atlas.save(cm.ASSETS / "textures" / "block" / "caisse_metal.png", optimize=True)
    ferme, ouvert = modeles_caisse(cf)
    for nom, els in (("metal_crate_closed", ferme), ("metal_crate_open", ouvert)):
        m = {"credit": cm.CREDIT.replace("coffres_modeles", "coffres_simples"), "textures": {"1": tex, "particle": tex},
             "elements": [dict(e, faces={f: dict(fd, texture="#1") for f, fd in e["faces"].items()}) for e in els]}
        adossement.ecrire(cm.ASSETS / f"models/block/{nom}.json", m)
    adossement.caisse()


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    g = ap.add_mutually_exclusive_group(required=True)
    g.add_argument("--apercu", help="dossier des rendus")
    g.add_argument("--installer", action="store_true", help="écrit les modèles dans le mod (après validation)")
    ap.add_argument("--avant", help="dossier d'outils d'avant (coffres_modeles.py) pour les planches avant / après")
    ap.add_argument("--sans-gif", action="store_true")
    args = ap.parse_args()
    coffres = [cm.Coffre(f()) for f in COFFRES]
    caisse = cm.Coffre(caisse_metal())
    cm.controler(coffres + [caisse])
    for cf in coffres:
        a, b = cf.bornes()
        if min(b[2], 16.0) - max(a[2], 0.0) > 13.75 + 1e-6:
            raise SystemExit(f"{cf.m.nom} : {min(b[2], 16.0) - max(a[2], 0.0)} px de profondeur, 13,75 au plus (rangée des murs)")
    if args.installer:
        cm.installer(coffres)
        installer_caisse(caisse)
        return
    dossier = Path(args.apercu)
    apres = dossier / "apres"
    apres.mkdir(parents=True, exist_ok=True)
    for cf in coffres:
        nom, cont = NOMS[cf.m.nom]
        cm.planche_coffre(cf, apres, nom, cont)
        if not args.sans_gif:
            cm.gif_ouverture(cf, apres)
        print(f"[coffres simples] {cf.m.nom} : éléments {cf.nb_elements()}")
    planche_caisse(caisse, apres)
    planche_gamme(coffres, caisse, dossier)
    print(f"[coffres simples] caisse_metal : éléments {caisse.nb_elements()}")
    if args.avant:
        avant = dossier / "avant"
        avant.mkdir(exist_ok=True)
        sys.path.insert(0, str(Path(args.avant)))
        import importlib.util
        spec = importlib.util.spec_from_file_location("cm_avant", Path(args.avant) / "coffres_modeles.py")
        av = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(av)
        av.POLICE, av.POLICE_MOY, av.ex.POLICE = cm.POLICE, cm.POLICE_MOY, cm.ex.POLICE
        for fab in av.COFFRES:
            cf = av.Coffre(fab())
            nom, cont = NOMS[cf.m.nom]
            av.planche_coffre(cf, avant, nom, cont)
        planche_caisse_actuelle(avant)
        for n in list(NOMS):
            planche_avant_apres(avant / f"{n}.png", apres / f"{n}.png", dossier / f"{n}_avant_apres.png", NOMS[n][0])
    print("rendus dans", dossier)


if __name__ == "__main__":
    main()
