#!/usr/bin/env python3
"""Maquette (25/10, à valider) : nouvel écran du recycleur, à la charte des écrans du clan et de la Table d'armurier.

Demande du propriétaire : plus de « coffre où l'on dépose puis ferme » ; une barre qui avance quand on met un objet (il se
recycle sous les yeux), une zone où l'on récupère ce qui est sorti, et un aperçu de ce que chaque objet peut donner pour
choisir de le recycler ou non.

    python modules/pnj/outils/apercu_recycleur.py [DOSSIER]      (par défaut : maquettes/recycleur)

Même moteur que les autres aperçus (modules/boutique/outils/apercu_boutique.py) : canevas 640 x 360 unités, rendu à
l'échelle 3 (1920 x 1080), polices DM Sans du jeu à leur vraie taille, vraies icônes des composants et des armes.
Exemples tirés de serveur/config/heraliz_quetes/recyclage.json (AK-47 : 4 Ferraille III, 2 Canon III, 2 Ressort III,
2 Kit de pièces III, 20 % chacun ; rien du tout : 0,8^10 = 11 %, 2 hérals). Refuse tout texte qui dépasse (sortie 1).
"""
import sys
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "boutique" / "outils"))
import apercu_boutique as ab  # noqa: E402

# polices DM Sans : rangées depuis dans le noyau (heraliz_noyau/font) ; ancienne place gardée si elle revient
ab.FONTS = next(p for p in (RACINE / "modules/pnj/src/client/resources/assets/heraliz_quetes/font",
                            RACINE / "modules/noyau/src/client/resources/assets/heraliz_noyau/font") if (p / "dmsans_bold.ttf").exists())
GW, GH, K = ab.GW, ab.GH, ab.K
TEXT, MUTED, FAINT, OK, OR, DANGER, BLANC = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0x5FD08A, 0xE8C36A, 0xFF6B6B, 0xFFFFFF
VERRE = 0x0D0F14
RARETES = {"commun": 0xAAAAAA, "peu_commun": 0x5FD06E, "rare": 0x508CFF, "epique": 0xB96EF0, "legendaire": 0xF0BE46}
TITRE = (True, 14 / 1.302, 0)
BOLD = (True, 11 / 1.302, 0)
SMALL = (False, 9 / 1.302, 0)
SMALL_GRAS = (True, 9 / 1.302, 0)
CAPS = (False, 9 / 1.302, 1.0)
CAPS_GRAS = (True, 9 / 1.302, 1.0)
MICRO = (True, 7.5 / 1.302, 0.8)
DEFAUTS = []

# 18/11 (retour du propriétaire : moins de texte) : plus de sous-titre, d'aide ni de pied ; inventaire centré ; 560 x 284
PX, PY, PW, PH = 40, 38, 560, 284
INV_X, INV_Y = 97, 178    # MenuRecycleur.INVENTAIRE_X / INVENTAIRE_Y
BANDEAU_Y = 151           # EcranRecycleur.BANDEAU_Y
ENTETE, M = 44, 16
CASE = 20
GAUCHE_L = 360           # zone machine + inventaire ; à droite le panneau d'aperçu
AP_X = PX + M + GAUCHE_L + 12

COMPOSANTS = RACINE / "modules/equipement/src/client/resources/assets/heraliz_composants/textures/item/icon"
TACZ = RACINE / "serveur/tacz"
ICONES = {
    "ak47": TACZ / "heraliz_1_base_tacz/assets/tacz/textures/gun/slot/ak47.png",
    "m1911": TACZ / "heraliz_1_base_tacz/assets/tacz/textures/gun/slot/m1911.png",
    "1p87": TACZ / "heraliz_2_armes_nitrogen/assets/arkaniaz/textures/attachment/slot/1p87.png",
}
for nom in ("ferraille_iii", "canon_iii", "ressort_iii", "kit_pieces_iii", "circuit_imprime", "batterie", "ferraille", "scotch"):
    ICONES[nom] = COMPOSANTS / (nom + ".png")
_cache = {}


def icone(e, nom, x, y, s=16):
    """Icône d'objet comme le jeu la dessine : texture agrandie au plus proche (pixels nets)."""
    if nom not in _cache:
        im = Image.open(ICONES[nom]).convert("RGBA")
        bbox = im.getbbox()
        if bbox and nom in ("ak47", "m1911", "1p87"):
            # icônes des armes : rognées puis posées dans un carré, comme le rendu d'objet de TaCZ
            im = im.crop(bbox)
            c = max(im.width, im.height)
            carre = Image.new("RGBA", (c, c), (0, 0, 0, 0))
            carre.paste(im, ((c - im.width) // 2, (c - im.height) // 2))
            im = carre
        _cache[nom] = im
    im = _cache[nom].resize((int(round(s * K)), int(round(s * K))), Image.NEAREST)
    e.image(im, x, y, s, s)


def verre(e, x, y, w, h, a=214):
    e.rect(x, y, x + w, y + h, VERRE, a)
    e.border(x, y, w, h, BLANC, 26)


def case(e, x, y, objet=None, n=None, rarete=None, survol=False, s=CASE):
    """Case d'objet : fond sombre, contour fin ; rareté = léger dégradé + trait en bas (comme partout en jeu)."""
    e.rect(x, y, x + s, y + s, 0x000000, 90)
    if rarete:
        c = RARETES[rarete]
        e.grad(x, y + s * 0.35, x + s, y + s, c, 0, c, 60, vertical=True)
        e.rect(x + 2, y + s - 1.4, x + s - 2, y + s - 0.6, c, 230)
    e.border(x, y, s, s, OR if survol else BLANC, 200 if survol else 34)
    if objet:
        icone(e, objet, x + (s - 16) / 2, y + (s - 16) / 2)
    if n and n > 1:
        e.d(str(n), x + s - 1.5, y + s - 4.2, SMALL_GRAS, BLANC)


def grille(e, x, y, cols, lignes, objets=(), survol=-1):
    for i in range(cols * lignes):
        o = objets[i] if i < len(objets) else None
        cx, cy = x + (i % cols) * (CASE + 2), y + (i // cols) * (CASE + 2)
        case(e, cx, cy, *(o if o else (None,)), survol=(i == survol))


def libelle(e, txt, x, y, largeur):
    w = e.texte(txt, x, y, CAPS_GRAS, MUTED)
    e.rect(x + w + 6, y + 2.6, x + largeur, y + 2.6 + 1 / 3, BLANC, 30)


def puce(e, txt, x, cy, c, plein=False):
    w = e.largeur(txt, MICRO) + 10
    e.rect(x, cy - 5, x + w, cy + 5, c, 70 if plein else 26)
    e.border(x, cy - 5, w, 10, c, 170)
    e.c(txt, x + w / 2, cy, MICRO, TEXT if plein else c)
    return w


def bouton(e, txt, x, cy, w, c=OR, plein=True, actif=True):
    a = 1 if actif else 0.35
    e.rect(x, cy - 7, x + w, cy + 7, c, int((60 if plein else 6) * a))
    e.border(x, cy - 7, w, 14, c, int((220 if plein else 90) * a))
    e.c(txt, x + w / 2, cy, CAPS_GRAS, TEXT if actif else FAINT, maxw=w - 8, quoi="bouton")


def icone_recycleur(e, x, y, c):
    """Pictogramme : deux flèches qui tournent autour d'un engrenage."""
    cx, cy = x + 12, y + 12
    e.disc(cx, cy, 11, c, 40)
    e.disc(cx, cy, 11, c, 200, anneau=1.1)
    e.disc(cx, cy, 4.2, c, 220, anneau=1.4)
    for k in range(8):
        import math
        a = k * math.pi / 4
        e.trait([(cx + 5.4 * math.cos(a), cy + 5.4 * math.sin(a)), (cx + 7 * math.cos(a), cy + 7 * math.sin(a))], 1.6, c, 220)
    e.poly([(cx + 9, cy - 9.5), (cx + 12.5, cy - 4), (cx + 6, cy - 4.5)], OK, 255)
    e.poly([(cx - 9, cy + 9.5), (cx - 12.5, cy + 4), (cx - 6, cy + 4.5)], OK, 255)


def chevrons(e, x, cy, c, a=150):
    for k in range(3):
        xx = x + k * 4
        e.trait([(xx, cy - 3), (xx + 2.6, cy), (xx, cy + 3)], 0.9, c, a - k * 35)


def entete(e, carte=True):
    icone_recycleur(e, PX + M, PY + 10, OR)
    tx = PX + M + 34
    e.m("Recycleur de terrain" if carte else "Recycleur", tx, PY + 22, TITRE, TEXT, maxw=300, quoi="titre")
    if carte:
        w = e.largeur("+10 % DE CHANCES", MICRO) + 10
        puce(e, "+10 % DE CHANCES", PX + PW - 34 - w, PY + 22, OK)
    e.c("×", PX + PW - 18, PY + 22, BOLD, MUTED)
    e.rect(PX + 8, PY + ENTETE, PX + PW - 8, PY + ENTETE + 1 / 3, BLANC, 40)


def machine(e, y0, en_cours):
    x0 = PX + M
    # entrée : ce qui attend
    libelle(e, "À RECYCLER", x0, y0, x0 + 66 - x0)
    attente = [("1p87", 1, "rare"), ("m1911", 1, "commun")] if en_cours else []
    grille(e, x0, y0 + 12, 3, 2, attente)
    # au milieu : l'objet en cours et sa barre
    mx0, mx1 = x0 + 84, x0 + GAUCHE_L - 84
    chevrons(e, x0 + 70, y0 + 33, OR if en_cours else FAINT)
    chevrons(e, mx1 + 6, y0 + 33, OR if en_cours else FAINT)
    libelle(e, "EN COURS", mx0, y0, mx1 - mx0)
    if en_cours:
        case(e, mx0, y0 + 13, "ak47", None, "rare", s=26)
        e.texte("AK-47", mx0 + 32, y0 + 15, BOLD, TEXT, maxw=mx1 - mx0 - 34, quoi="objet en cours")
        e.texte("Rare", mx0 + 32, y0 + 28, SMALL, RARETES["rare"])
        by = y0 + 47
        e.rect(mx0, by, mx1, by + 2.4, BLANC, 26)
        e.rect(mx0, by, mx0 + (mx1 - mx0) * 0.62, by + 2.4, OR, 235)
        e.disc(mx0 + (mx1 - mx0) * 0.62, by + 1.2, 2.4, OR, 255)
        e.m("Recyclage…  62 %", mx0, by + 9, SMALL, MUTED)
        e.d("0,6 s", mx1, by + 9, SMALL_GRAS, TEXT)
        e.m("+ 2 en attente", mx0, by + 20, SMALL, FAINT)
    else:
        e.rect(mx0, y0 + 47, mx1, y0 + 49.4, BLANC, 18)
    # sortie : ce qui est sorti, à prendre
    ox = x0 + GAUCHE_L - 66
    libelle(e, "RÉCUPÉRÉ", ox, y0, 66)
    sortie = [("ferraille_iii", 3, "epique"), ("canon_iii", 1, "epique"), ("circuit_imprime", 1, "peu_commun")] if en_cours else []
    grille(e, ox, y0 + 12, 3, 2, sortie)
    bouton(e, "TOUT PRENDRE", ox - 8, y0 + 66, 72, OR, plein=True, actif=en_cours)
    if en_cours:
        e.c("+ 1 hérals sur ton compte", ox + 28, y0 + 81, SMALL, OR, maxw=90, quoi="hérals")


def inventaire(e, survol, cas="ak47"):
    x0, y0 = PX + INV_X, PY + INV_Y - 12
    libelle(e, "TON INVENTAIRE", x0, y0, 9 * (CASE + 2) - 2)
    inv = [("ferraille", 6, "commun"), ("scotch", 2, "peu_commun"), None, ("batterie", 1, "peu_commun"), None, None, None, None, None,
           None, None, None, None, None, None, None, None, None,
           None, None, None, None, None, None, None, None, None,
           ("ak47", 1, "rare"), ("m1911", 1, "commun"), None, None, None, None, None, None, None]
    if cas == "1p87":
        inv[2] = ("1p87", 1, "rare")
    grille(e, x0, y0 + 12, 9, 3, inv[:27], survol=survol)
    grille(e, x0, y0 + 12 + 3 * (CASE + 2) + 4, 9, 1, inv[27:], survol=survol - 27 if survol >= 27 else -1)


def bandeau(e, texte):
    """Bandeau rouge d'un objet refusé (EcranRecycleur.dessinerBandeau) : entre la machine et l'inventaire."""
    w = e.largeur(texte, SMALL) + 32
    x0, cy, h = PX + M + GAUCHE_L / 2 - w / 2, PY + BANDEAU_Y, 17
    e.rect(x0, cy - h / 2, x0 + w, cy + h / 2, 0x120C0E, 235)
    e.rect(x0, cy - h / 2, x0 + w, cy + h / 2, DANGER, 30)
    e.border(x0, cy - h / 2, w, h, DANGER, 150)
    e.rect(x0, cy - h / 2, x0 + 2, cy + h / 2, DANGER, 255)
    e.disc(x0 + 12, cy, 4.6, DANGER, 240)
    e.c("!", x0 + 12, cy, SMALL_GRAS, BLANC)
    e.m(texte, x0 + 22, cy, SMALL, ab.mix(DANGER, BLANC, 0.62), quoi="bandeau")


def apercu(e, y0, h, cas):
    x, w = AP_X, PX + PW - M - AP_X
    verre(e, x, y0 - 2, w, h + 2, 110)
    xi = x + 10
    libelle(e, "APERÇU", xi, y0 + 6, w - 20)
    if cas == "ak47":
        case(e, xi, y0 + 18, "ak47", None, "rare", s=28)
        e.texte("AK-47", xi + 34, y0 + 21, BOLD, TEXT, maxw=w - 54, quoi="nom aperçu")
        e.texte("Rare", xi + 34, y0 + 34, SMALL, RARETES["rare"])
        y = y0 + 58
        libelle(e, "PEUT DONNER", xi, y, w - 20)
        y += 14
        for nom, lib, n, r in (("ferraille_iii", "Ferraille III", 4, "epique"), ("canon_iii", "Canon III", 2, "epique"),
                               ("ressort_iii", "Ressort III", 2, "epique"), ("kit_pieces_iii", "Kit de pièces III", 2, "epique")):
            e.rect(xi, y - 1, xi + 14, y + 13, 0x000000, 80)
            e.rect(xi + 2, y + 11.6, xi + 12, y + 12.4, RARETES[r], 220)
            icone(e, nom, xi + 1, y, 12)
            e.m(lib, xi + 19, y + 6, SMALL, TEXT, maxw=w - 70, quoi="composant")
            e.d("%d %%" % round((1 - 0.9 ** n) * 100), x + w - 10, y + 6, SMALL_GRAS, TEXT)
            y += 17
        y += 4
        e.rect(xi, y, x + w - 10, y + 1 / 3, BLANC, 26)
        y += 7
        e.m("En moyenne", xi, y + 4, SMALL, MUTED)
        e.d("0,9 pièce(s)", x + w - 10, y + 4, SMALL_GRAS, OK)
        e.m("Rien du tout (35 %)", xi, y + 16, SMALL, MUTED, maxw=w - 60, quoi="rien")
        e.d("1 hérals", x + w - 10, y + 16, SMALL_GRAS, OR)
        puce(e, "SE RECYCLE", xi, y0 + h - 14, OK, plein=True)
    else:
        # arme chargée survolée : ce qu'elle peut donner, puis ce qu'il faut vider (état « À vider »)
        case(e, xi, y0 + 18, "ak47", None, "rare", s=28)
        e.texte("AK-47", xi + 34, y0 + 21, BOLD, TEXT, maxw=w - 54, quoi="nom aperçu")
        e.texte("Rare", xi + 34, y0 + 34, SMALL, RARETES["rare"])
        y = y0 + 58
        libelle(e, "PEUT DONNER", xi, y, w - 20)
        y += 14
        for nom, lib, n, r in (("ferraille_iii", "Ferraille III", 4, "epique"), ("canon_iii", "Canon III", 2, "epique"),
                               ("ressort_iii", "Ressort III", 2, "epique"), ("kit_pieces_iii", "Kit de pièces III", 2, "epique")):
            e.rect(xi, y - 1, xi + 14, y + 13, 0x000000, 80)
            e.rect(xi + 2, y + 11.6, xi + 12, y + 12.4, RARETES[r], 220)
            icone(e, nom, xi + 1, y, 12)
            e.m(lib, xi + 19, y + 6, SMALL, TEXT, maxw=w - 70, quoi="composant")
            e.d("%d %%" % round((1 - 0.9 ** n) * 100), x + w - 10, y + 6, SMALL_GRAS, TEXT)
            y += 17
        y += 4
        e.rect(xi, y, x + w - 10, y + 1 / 3, BLANC, 26)
        y += 7
        for i, l in enumerate(e.lignes("Arme chargée : décharge-la d'abord, ses balles seraient perdues.", SMALL, w - 20)):
            e.texte(l, xi, y + i * 9, SMALL, MUTED, maxw=w - 20, quoi="refus")
        puce(e, "À VIDER", xi, y0 + h - 14, 0xE8B34A, plein=True)


def ecran(sortie, en_cours=True, cas="ak47", texte_bandeau=None):
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 130)
    verre(e, PX, PY, PW, PH)
    entete(e)
    y0 = PY + ENTETE + 10
    machine(e, y0, en_cours)
    inventaire(e, 27 if cas in ("ak47", "arme_chargee") else 2, cas)
    apercu(e, y0, PH - ENTETE - 34, cas)
    if texte_bandeau:
        bandeau(e, texte_bandeau)
    DEFAUTS.extend(e.defauts)
    e.defauts = []
    e.sauver(sortie)


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "recycleur"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    ab.LANGUE = "fr_fr"
    ecran(dossier / "recycleur_en_cours.png")
    ecran(dossier / "recycleur_vide_refus.png", en_cours=False, cas="arme_chargee",
          texte_bandeau="Arme chargée : décharge-la d'abord.")
    if DEFAUTS:
        print("\n".join(DEFAUTS))
        sys.exit(1)
    print("aucun texte ne dépasse ->", dossier)


if __name__ == "__main__":
    main()
