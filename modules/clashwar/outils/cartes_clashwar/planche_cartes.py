"""Owner sheets for the ClashWar maps: two 3D views (south-east, north-west), top view and annotated plan (lanes and
place names); when the map it replaces is still readable (structures/ and cartes/ of the module), also a comparison
with it AT THE SAME SCALE, with a few metrics (python metriques_xl.py <both ids> first). Writes
build/cartes_clashwar/<dossier>/. Usage: python planche_cartes.py [id ...]"""
import json
import os
import sys

import numpy as np
from PIL import Image, ImageDraw

import rendu
from chemins_cartes import SORTIE
from rendu import police

PX = 12                     # top view render: pixels per block (both maps of a sheet)
ISO_PX = 12                 # 3D render width = map side x ISO_PX (both maps of a sheet)
FOND, CARTE = (17, 19, 24), (29, 33, 41)
BLEU, ORANGE, VIOLET = (90, 170, 255), (255, 150, 60), (190, 120, 255)

CARTES = {
    "clashwar_1v1_depot": {
        "dossier": "depot", "actuelle": "clashwar_1v1", "format": "1v1",
        "voies": [("Voie ouest", BLEU, [(41, 7), (30, 11), (22, 16), (14, 30), (8, 45)], 2),
                  ("Voie centre", ORANGE, [(45, 10), (45, 24), (45, 37), (45, 45)], 1),
                  ("Voie est", VIOLET, [(50, 7), (62, 10), (72, 13), (72, 39), (78, 45)], 0)],
        "lieux": [(44.5, 4.5, "Vestiaire (spawn A)", "Vestiaire (spawn B)"), (44, 17, "Lampisterie", "Lampisterie"),
                  (77, 26, "Remise des locomotives", "Remise (B)"), (11.5, 34.5, "Château d'eau", "Château d'eau (B)"),
                  (18.5, 13, "Estacade à charbon", "Estacade (B)"), (36, 24.5, "Passerelle (en hauteur)", "Passerelle (B)"),
                  (27, 33, "Poste d'aiguillage", "Poste (B)"), (6, 7, "Tas de charbon", "Charbon (B)")],
        "centre": "Plaque tournante = zone finale",
    },
    "clashwar_2v2_kombinat": {
        "dossier": "kombinat", "actuelle": "clashwar_2v2", "format": "2v2",
        "voies": [("Voie ouest", BLEU, [(62, 9), (55, 17), (30, 26), (21, 30), (21, 65)], 2),
                  ("Voie centre", ORANGE, [(65, 12), (52, 20), (48, 27), (48, 44), (60, 56), (65, 65)], 1),
                  ("Voie est", VIOLET, [(68, 9), (75, 17), (100, 25), (108, 32), (108, 65)], 0)],
        "lieux": [(16, 13, "Garages", "Garages (B)"), (85, 8, "Chaufferie", "Chaufferie (B)"),
                  (114, 8, "Magasin", "Magasin (B)"), (121, 23.5, "Marché", "Marché (B)"),
                  (40, 16, "Terminus (bus couché)", "Terminus (B)"), (64, 2, "Hangar du spawn A", "Hangar du spawn B"),
                  (64, 35, "Barre A : briques, 4 étages, arche", "Barre B : panneaux, 4 étages, arche"),
                  (94, 56, "Aile A", "Aile B"), (49, 51, "Aire de jeux", "Aire de jeux (B)"),
                  (73, 50, "Sous-station (toit)", "Sous-station (B)"), (9, 36, "Chantier (fosse)", "Chantier (B)"),
                  (7, 53, "Baraque (2 étages)", "Baraque (B)"), (119, 31, "Pigeonnier", "Pigeonnier (B)"),
                  (124, 59, "Transfo", "Transfo (B)"), (21, 43, "Tuyaux de chauffage", "Tuyaux (B)")],
        "centre": "Patinoire = zone finale",
    },
    "clashwar_3v3_port": {
        "dossier": "port", "actuelle": "clashwar_3v3", "format": "3v3",
        "voies": [("Voie ouest", BLEU, [(80, 8), (62, 12), (40, 32), (30, 56), (18, 66), (30, 76), (50, 85)], 2),
                  ("Voie centre", ORANGE, [(85, 11), (85, 40), (85, 60), (85, 85)], 1),
                  ("Voie est", VIOLET, [(90, 8), (102, 14), (124, 30), (128, 52), (138, 66), (140, 85)], 0)],
        "lieux": [(84.5, 4, "Douane (spawn A)", "Douane (spawn B)"), (72, 21, "Capitainerie", "Capitainerie (B)"),
                  (28, 30, "Parc à conteneurs", "Conteneurs (B)"), (44.5, 59, "Grue", "Grue (B)"),
                  (40, 75, "Cargo échoué", "Cargo (B)"), (97.5, 70, "Jetée", "Jetée (B)"),
                  (123, 27, "Entrepôt", "Entrepôt (B)"), (124, 46, "Hangar ouvert", "Hangar ouvert (B)"),
                  (66, 44, "Cantine", "Cantine (B)"), (156, 12, "Réservoirs", "Réservoirs (B)"),
                  (152, 30, "Silo", "Silo (B)"), (156, 44, "Bateaux sur bers", "Bateaux (B)"),
                  (135, 76, "Drague", "Drague (B)"), (7, 72, "Hangar à bateaux", "Hangar à bateaux (B)")],
        "centre": "Pont central = zone finale",
    },
    "clashwar_4v4_garnison": {
        "dossier": "garnison", "actuelle": "clashwar_4v4", "format": "4v4",
        "voies": [("Voie ouest", BLEU, [(99, 11), (92, 18), (60, 19), (27, 22), (28, 40), (34, 58), (34, 66), (34, 80),
                                        (35, 100), (35, 110)], 2),
                  ("Voie centre", ORANGE, [(110, 21), (103, 34), (104, 46), (110, 60), (110, 70), (110, 92),
                                           (110, 110)], 1),
                  ("Voie est", VIOLET, [(121, 11), (128, 18), (172, 21), (175, 40), (185, 60), (185, 66), (185, 84),
                                        (185, 100), (185, 110)], 0)],
        "lieux": [(110, 5, "Hangar (spawn A)", "Hangar (spawn B)"), (57, 27, "Caserne", "Caserne (B)"),
                  (152, 27, "État-major", "État-major (B)"), (48, 7, "Entrepôt", "Entrepôt (B)"),
                  (80, 12, "Polygone (tranchée)", "Polygone (B)"), (140, 8, "Cantine", "Cantine (B)"),
                  (197, 18, "Parc auto", "Parc auto (B)"), (14, 13, "Dépôt de munitions (buttes)", "Munitions (B)"),
                  (13, 55, "Chaufferie", "Chaufferie (B)"), (52, 50, "Place d'armes", "Place d'armes (B)"),
                  (167, 43, "Infirmerie", "Infirmerie (B)"), (136, 61, "Garages", "Garages (B)"),
                  (195, 51, "Dépôt de carburant", "Carburant (B)"), (22, 80, "Étable du kolkhoze", "Étable (B)"),
                  (18, 94, "Silo-tranchée", "Silo (B)"), (17.5, 103.5, "Château d'eau", "Château d'eau (B)"),
                  (110, 80, "Club (galerie, balcon)", "Club (B)"), (135, 77, "Magasin", "Magasin (B)"),
                  (187, 84, "Église en ruine", "Église (B)"), (170, 77, "Clocher", "Clocher (B)"),
                  (189, 98, "Cimetière", "Cimetière (B)"), (63, 76, "Isbas", "Isbas (B)")],
        "centre": "Monument aux morts = zone finale",
    },
}


def contenu_iso(chemin, haut=150, bas=80):
    """Crop a 3D render to its content (background = vertical gradient)."""
    im = Image.open(chemin).convert("RGB")
    W, H = im.size
    zone = im.crop((0, haut, W, H - bas))
    a = np.asarray(zone).astype(np.int32)
    diff = np.abs(a - a[:, :1, :]).sum(2) > 18
    rows, cols = np.nonzero(diff.any(1))[0], np.nonzero(diff.any(0))[0]
    m = 8
    return zone.crop((max(0, cols[0] - m), max(0, rows[0] - m), min(W, cols[-1] + m), min(zone.size[1], rows[-1] + m)))


def carte_dessus(chemin, taille):
    im = Image.open(chemin).convert("RGB")
    marge, entete = 56, 110
    return im.crop((marge, marge + entete, marge + taille[0] * PX, marge + entete + taille[2] * PX))


def existe(ident):
    base = rendu.dossier_carte(ident)
    return os.path.isfile(os.path.join(base, "structures", ident + ".nbt")) and os.path.isfile(
        os.path.join(base, "cartes", ident + ".json"))


def taille(ident):
    size, _p, _i, meta = rendu.charger(ident)
    return size, meta


def rendus(ident):
    cfg = CARTES[ident]
    out = os.path.join(str(SORTIE), cfg["dossier"])
    os.makedirs(out, exist_ok=True)
    (sx, sy, sz), _ = taille(ident)
    rendu.rendu_iso(ident, out, 45.0, 35.0, largeur=sx * ISO_PX, suffixe="_sud_est")
    rendu.rendu_iso(ident, out, 225.0, 35.0, largeur=sx * ISO_PX, suffixe="_nord_ouest")
    rendu.rendu_dessus(ident, out, px_bloc=PX)
    act = cfg["actuelle"]
    if not existe(act):
        return
    (ax, ay, az), _ = taille(act)
    rendu.rendu_iso(act, out, 45.0, 35.0, largeur=ax * ISO_PX, suffixe="_meme_echelle")
    rendu.rendu_dessus(act, out, px_bloc=PX)


def comparaison(ident, met):
    cfg = CARTES[ident]
    out = os.path.join(str(SORTIE), cfg["dossier"])
    act = cfg["actuelle"]
    (sx, sy, sz), meta = taille(ident)
    (ax, ay, az), meta_a = taille(act)
    s = min(1.0, 1500.0 / (sx * ISO_PX))
    t = min(1.0, 1300.0 / (sx * PX))
    iso_n = contenu_iso(os.path.join(out, f"{ident}_iso_sud_est.png"))
    iso_a = contenu_iso(os.path.join(out, f"{act}_iso_meme_echelle.png"))
    iso_n = iso_n.resize((int(iso_n.size[0] * s), int(iso_n.size[1] * s)), Image.LANCZOS)
    iso_a = iso_a.resize((int(iso_a.size[0] * s), int(iso_a.size[1] * s)), Image.LANCZOS)
    top_n = carte_dessus(os.path.join(out, f"{ident}_dessus.png"), (sx, sy, sz))
    top_a = carte_dessus(os.path.join(out, f"{act}_dessus.png"), (ax, ay, az))
    top_n = top_n.resize((int(top_n.size[0] * t), int(top_n.size[1] * t)), Image.LANCZOS)
    top_a = top_a.resize((int(top_a.size[0] * t), int(top_a.size[1] * t)), Image.LANCZOS)
    pad = 28
    col_a = max(iso_a.size[0], top_a.size[0], 420)
    col_n = max(iso_n.size[0], top_n.size[0])
    W = max(pad * 3 + col_a + col_n, 1400)
    entete = 150
    par_id = {r["id"]: r for r in met}
    rn, ra = par_id[ident], par_id[act]

    def v(x):
        return f"{x:.2f}".replace(".", ",")

    def vues(r):
        return ("oui" if r["vue_spawnA_spawnB"] else "non") + " / " + (
            "oui" if (r["vue_spawnB_siteA"] or r["vue_spawnB_siteB"] or r["vue_spawnA_siteA"] or r["vue_spawnA_siteB"])
            else "non")

    lignes = [
        ("", f"{cfg['format']} actuelle", "nouvelle carte"),
        ("Taille (blocs)", f"{ax} × {az}", f"{sx} × {sz}"),
        ("Densité (blocs au-dessus du sol / m²)", v(ra["blocs_par_m2"]), v(rn["blocs_par_m2"])),
        ("Types de blocs", str(ra["types_blocs"]), str(rn["types_blocs"])),
        ("Sol dehors à plus de 6 blocs d'un abri", f"{ra['exterieur_loin_6_pct']:.0f} %", f"{rn['exterieur_loin_6_pct']:.0f} %"),
        ("Plus longue ligne de vue droite (blocs, part du côté)",
         f"{max(ra['plus_longue_ligne_ns'], ra['plus_longue_ligne_eo'])} ({ra['ligne_max_pct_cote']} %)",
         f"{max(rn['plus_longue_ligne_ns'], rn['plus_longue_ligne_eo'])} ({rn['ligne_max_pct_cote']} %)"),
        ("Couloirs de vue de 64 blocs et plus", str(ra["couloirs_ns_64"] + ra["couloirs_eo_64"]),
         str(rn["couloirs_ns_64"] + rn["couloirs_eo_64"])),
        ("Cases atteignables en hauteur (> 2 blocs)", f"{ra['atteignable_haut_pct']:.0f} %", f"{rn['atteignable_haut_pct']:.0f} %"),
        ("Hauteur max atteignable à pied", f"+{ra['hauteur_max_atteignable']}", f"+{rn['hauteur_max_atteignable']}"),
        ("Blocs flottants (pièces)", str(ra["flottants_6"]["pieces"]), str(rn["flottants_6"]["pieces"])),
        ("Les spawns se voient / un spawn voit un site", vues(ra), vues(rn)),
    ]
    tab_h = 40 * len(lignes) + 30
    hrow = max(iso_a.size[1], iso_n.size[1])
    H = entete + hrow + pad + max(top_n.size[1], top_a.size[1]) + 70 + tab_h + 70
    im = Image.new("RGB", (W, H), FOND)
    d = ImageDraw.Draw(im)
    d.text((pad, 24), f"ClashWar {cfg['format']} · nouvelle carte contre la carte actuelle, à la même échelle",
           font=police(40, gras=True), fill=(242, 244, 248))
    d.text((pad, 80), f"À gauche : « {meta_a['nom_fr']} » ({act}, {ax} × {az}). À droite : « {meta['nom_fr']} » "
           f"({ident}, {sx} × {sz}). Un bloc a la même taille dans les deux images.", font=police(19),
           fill=(190, 196, 206))
    d.text((pad, 110), "En haut : vue 3D depuis le sud-est. En bas : vue de dessus (nord en haut). Repères : jaune = spawn A, "
           "vert = spawn B, rouge = sites de bombe.", font=police(19), fill=(160, 166, 176))
    xa, xn = pad, pad * 2 + col_a
    y0 = entete
    im.paste(iso_a, (xa + (col_a - iso_a.size[0]) // 2, y0 + hrow - iso_a.size[1]))
    im.paste(iso_n, (xn, y0))
    y1 = y0 + hrow + pad
    im.paste(top_a, (xa + (col_a - top_a.size[0]) // 2, y1))
    im.paste(top_n, (xn, y1))
    d.text((xa, y1 + top_a.size[1] + 12), f"{meta_a['nom_fr']} · {ax} × {az}", font=police(22, semi=True),
           fill=(225, 228, 234))
    d.text((xn, y1 + top_n.size[1] + 12), f"{meta['nom_fr']} · {sx} × {sz}", font=police(22, semi=True),
           fill=(225, 228, 234))
    y2 = y1 + max(top_n.size[1], top_a.size[1]) + 70
    d.rounded_rectangle([pad, y2 - 14, W - pad, y2 + 40 * len(lignes) + 4], radius=12, fill=CARTE)
    for i, (a, b, c) in enumerate(lignes):
        f = police(20, gras=(i == 0))
        yy = y2 + i * 40
        d.text((pad + 20, yy), a, font=f, fill=(225, 228, 234))
        d.text((pad + 680, yy), b, font=f, fill=(200, 205, 214))
        d.text((pad + 1000, yy), c, font=police(20, gras=True) if i else f, fill=(255, 214, 102) if i else (225, 228, 234))
    d.text((pad, H - 40), "Rendus avec les textures du pack imposé aux joueurs. Mesures : metriques/metriques_cartes.md.",
           font=police(15), fill=(130, 136, 146))
    chemin = os.path.join(out, f"comparaison_{cfg['format']}_meme_echelle.png")
    im.save(chemin, optimize=True)
    print(chemin, im.size)


def plan_annote(ident):
    cfg = CARTES[ident]
    out = os.path.join(str(SORTIE), cfg["dossier"])
    (sx, sy, sz), meta = taille(ident)
    base = Image.open(os.path.join(out, f"{ident}_dessus.png")).convert("RGB")
    ov = Image.new("RGBA", base.size, (0, 0, 0, 0))
    d = ImageDraw.Draw(ov)
    ox, oz = 56, 166

    def p(x, z):
        return (ox + x * PX, oz + z * PX)

    def miroir(pts):
        return [(sx - x, sz - z) for x, z in pts]

    voies = cfg["voies"]
    for nom, c, pts, partenaire in voies:
        tout = pts + miroir(voies[partenaire][2])[::-1][1:]
        xy = [p(x, z) for x, z in tout]
        d.line(xy, fill=c + (90,), width=26, joint="curve")
        d.line(xy, fill=c + (235,), width=6, joint="curve")
    im = Image.alpha_composite(base.convert("RGBA"), ov)
    d = ImageDraw.Draw(im)
    f = police(17, semi=True)
    cx, cz = sx / 2.0, sz / 2.0
    for x, z, t, tb in cfg["lieux"] + [(cx, cz, cfg["centre"], None)]:
        for (xx, zz, tt) in ((x, z, t), (sx - x, sz - z, tb)):
            if tt is None:
                continue
            X, Y = p(xx, zz)
            bb = d.textbbox((X, Y), tt, font=f, anchor="mm")
            d.rounded_rectangle([bb[0] - 6, bb[1] - 4, bb[2] + 6, bb[3] + 4], radius=6, fill=(18, 20, 26, 205))
            d.text((X, Y), tt, font=f, fill=(245, 245, 245), anchor="mm")
    W, H = im.size
    d.rectangle([0, H - 150, W, H - 60], fill=(20, 22, 27, 255))
    lx = 56
    for nom, c, _pts, _p in voies:
        d.line([(lx, H - 110), (lx + 40, H - 110)], fill=c + (255,), width=6)
        d.text((lx + 50, H - 110), nom, font=police(18, semi=True), fill=(230, 232, 236), anchor="lm")
        lx += 50 + d.textlength(nom, font=police(18, semi=True)) + 40
    d.text((56, H - 80), "Carte symétrique : la moitié sud (B) est la moitié nord tournée d'un demi-tour, avec un autre "
           "aspect. Chaque voie passe d'une moitié à l'autre (la voie ouest de A devient la voie est de B).",
           font=police(16), fill=(170, 176, 186))
    chemin = os.path.join(out, f"{ident}_plan_annote.png")
    im.convert("RGB").save(chemin, optimize=True)
    print(chemin, im.size)


if __name__ == "__main__":
    ids = sys.argv[1:] or list(CARTES)
    chemin_met = os.path.join(str(SORTIE), "metriques", "metriques_cartes.json")
    met = []
    if os.path.isfile(chemin_met):
        with open(chemin_met, encoding="utf-8") as fm:
            met = json.load(fm)
    mesurees = {r["id"] for r in met}
    for ident in ids:
        rendus(ident)
        if existe(CARTES[ident]["actuelle"]) and {ident, CARTES[ident]["actuelle"]} <= mesurees:
            comparaison(ident, met)
        plan_annote(ident)
