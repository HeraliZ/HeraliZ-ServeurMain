#!/usr/bin/env python3
"""Aperçus de l'onglet « Saison » de l'écran du clan (touche K) et de l'icône de la Purge en jeu.

    python modules/clans/outils/apercu_saison.py [DOSSIER]      (par défaut : maquettes/saison)

Même moteur que les aperçus de la boutique (modules/boutique/outils/apercu_boutique.py) : canevas 640 x 360 unités,
rendu à l'échelle 3 (1920 x 1080), polices du noyau (DM Sans, Onest pour le cyrillique), tailles des fichiers
font/*.json du mod divisées par 1,302 (em). Écran du clan : panneau 560 x 320, en-tête 44, onglets 18, marge 16
(ClanScreen) ; onglet Saison : mêmes mesures que client/gui/tabs/SaisonTab (GAUCHE 300, lignes de 17, colonnes PVE / PVP
/ EVENTS finissant à 196 / 236 / 276) : à changer des deux côtés.

11/11 (retour du propriétaire) : vrais textes du mod (français, anglais, russe), vrai barème (« Gagner des points » d'après
serveur/config/heralisz_clans/saison.json, comme saison/SaisonVue) ; chaque case de catégorie ne montre que les points,
la place dans la catégorie et les points exacts sont dans l'infobulle de la case ; la colonne « Gén. » montre la place au
général (1er, 2e…), expliquée dans son infobulle. Images : onglet de chaque langue, infobulle d'une case et de « Gén. »,
clan seul classé (le cas du propriétaire), icône de la Purge. Refuse tout texte qui dépasse de sa place (code de sortie 1).
"""
import json
import re
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "boutique" / "outils"))
import apercu_boutique as ab  # noqa: E402

ab.FONTS = RACINE / "modules/noyau/src/client/resources/assets/heraliz_noyau/font"
LANG = RACINE / "modules/clans/src/client/resources/assets/heralisz_clans/lang"
BAREME_JSON = RACINE / "serveur/config/heralisz_clans/saison.json"
GW, GH, K = ab.GW, ab.GH, ab.K
TEXT, MUTED, FAINT, OK, OR, DANGER, BLANC = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0x5FD08A, 0xE8C36A, 0xFF6B6B, 0xFFFFFF
VERRE, ACCENT = 0x0D0F14, 0x6FA8FF
ROUGE, ROUGE_VIF, AMBRE = 0xE3201B, 0xFF3B30, 0xE8B34A
PVE_C, PVP_C, EV_C = 0x5FD08A, 0xFF6B6B, 0xB57BFF
COULEURS = (PVE_C, PVP_C, EV_C)
# polices du mod des clans (em = taille du .json / 1,302) ; troisième valeur : espacement des capitales (Ui.spaced)
FONT = (False, 11 / 1.302, 0)
BOLD = (True, 11 / 1.302, 0)
SMALL = (False, 9 / 1.302, 0)
SMALL_GRAS = (True, 9 / 1.302, 0)
SMALL_ESP = (False, 9 / 1.302, 0.8)
TITRE_HUD = (True, 9 / 1.302, 1.4)
DEFAUTS = []

# ------------------------------------------------------------------------------------------------ mesures (Java)
PX, PY, PW, PH = 40, 20, 560, 320                        # ClanScreen : panneau centré dans 640 x 360
ENTETE, ONGLETS, M = 44, 18, 16
X, Y = PX + M, PY + ENTETE + ONGLETS + 10                # contenu de l'onglet (Tab.init)
W, H = PW - 2 * M, PY + PH - 14 - Y
GAUCHE, LIGNE, ENTREE, BOITE = 300, 17, 21, 34           # SaisonTab
R_CAT = (196, 236, 276)
CASE = 40
ONGLETS_CLES = ["apercu", "membres", "banque", "quetes", "niveau", "saison", "permissions", "parametres"]
# haut des capitales sous le y de drawString (glyphes décalés de 1, police à sa vraie taille)
DY = {FONT: 1.6, BOLD: 1.6, SMALL: 1.5, SMALL_GRAS: 1.5, SMALL_ESP: 1.5}


# ------------------------------------------------------------------------------------------------ textes
class Langue:
    def __init__(self, code):
        self.code = code
        self.T = json.loads((LANG / f"{code}.json").read_text(encoding="utf-8"))

    def t(self, cle, *a):
        s = self.T.get(cle, cle)
        it = iter(a)

        def rem(m):
            if m.group(1):
                return str(a[int(m.group(1)[:-1]) - 1])
            return str(next(it))
        return re.sub(r"%(\d+\$)?s", rem, s).replace("%%", "%")

    def nombre(self, v):
        """Ui.nombre : séparateur de milliers (espace fine en français, virgule ailleurs ; ici une espace : DM Sans n'a pas
        l'espace fine, que le jeu dessine avec la police « space » du mod)."""
        s = "{:,}".format(int(v))
        return s.replace(",", " ") if self.code.startswith("fr") else s

    def decimal(self, v):
        """SaisonTab.nombre : entier, sinon deux décimales au plus (virgule hors anglais)."""
        if abs(v - round(v)) < 1e-6:
            return self.nombre(round(v))
        s = ("%.2f" % v).rstrip("0").rstrip(".")
        return s if self.code.startswith("en") else s.replace(".", ",")

    def rang(self, n):
        """SaisonTab.rang : 1er / 2e, 1st / 2nd / 3rd / 11th, 1-е."""
        u, c = n % 10, n % 100
        forme = "premier" if n == 1 else "autre" if 11 <= c <= 13 else {1: "un", 2: "deux", 3: "trois"}.get(u, "autre")
        return self.t("gui.saison.rang." + forme, n)


# ------------------------------------------------------------------------------------------------ données
CLANS = [  # tag, nom, couleur de la bannière, points (pve, pvp, events)
    ("WLF", "Les Loups Gris", 0x9FA8B8, (1842.37, 96, 340)),
    ("RAT", "Rats du Bunker", 0xC9A34A, (1610.5, 131, 180)),
    ("HZ", "Nuit Rouge", 0xE3201B, (1204.08, 64, 280)),
    ("KRK", "Kraken Noir", 0x4FB6C9, (980, 88, 60)),
    ("VLT", "Vautours", 0x8F6BD8, (702.3, 22, 100)),
    ("OUR", "L'Ours de Valmont", 0x5FD08A, (744, 40, 20)),
    ("CRB", "Corbeaux", 0x6B7280, (310.01, 12, 0)),
]
MOI = "HZ"
# le cas de la capture du propriétaire (11/11) : un seul clan classé, 1 point PVP, quelques centièmes de zombies
SEUL = [("HZ", "Nuit Rouge", 0xE3201B, (0.37, 1, 0))]


def classement(clans):
    """Comme Saison.classement : place par catégorie (égalité = même place, 0 point = dernière place), puis général par
    somme des places, à égalité les points. -> [(rang, tag, nom, couleur, points, places)]."""
    n = len(clans)
    places = {c[0]: [0, 0, 0] for c in clans}
    for k in range(3):
        tri = sorted(clans, key=lambda c: -c[3][k])
        for i, c in enumerate(tri):
            v = c[3][k]
            p = n if v <= 0 else i + 1
            if i > 0 and v > 0 and v == tri[i - 1][3][k]:
                p = places[tri[i - 1][0]][k]
            places[c[0]][k] = p
    gen = sorted(clans, key=lambda c: (sum(places[c[0]]), -sum(c[3])))
    return [(i + 1, c[0], c[1], c[2], c[3], places[c[0]]) for i, c in enumerate(gen)]


def bareme_pve():
    """« Gagner des points », catégorie PVE, comme SaisonVue.bareme() : (libellé, points, condition) déjà traduits plus tard."""
    b = json.loads(BAREME_JSON.read_text(encoding="utf-8"))
    p, pl = b["pve"], set(b.get("plafonnees", []))
    jour = b.get("plafond_jour_joueur", 50)
    ent = lambda v: str(int(round(v)))  # noqa: E731
    lignes = [("salle_rouge", "un", [p["salle_rouge"]], "salle", []),
              ("salles", "paire", [p["salle_bleue"], p["salle_verte"]], "salle", []),
              ("conteneur_t7", "un", [p["conteneur_t7"]], "premiere_ouverture", []),
              ("quete_principale", "un", [p["quete_principale"]],
               "une_fois_plafond" if "quete_principale" in pl else "une_fois", [ent(jour)] if "quete_principale" in pl else []),
              ("quete_clan", "un", [p["quete_clan"]], "quetes_clan", ["3", "2"]),
              ("capture_libre", "par_niveau", [b["capture"]["par_valeur"]], "capture", [ent(b["capture"]["plafond_jour_clan"])]),
              ("conteneurs", "plage", [p["conteneur_t1"], p["conteneur_t6"]], "fouille", []),
              ("craft", "plage", [p["craft_commun"], p["craft_epique"]], "craft", []),
              ("vente", "vente", [p["vente_1000"], 1000], "vente", []),
              ("zombies", "plage", [p["zombie"], p["brute"]], "plafond", [ent(jour)])]
    return lignes


# ------------------------------------------------------------------------------------------------ dessin
def verre(e, x, y, w, h, a=214):
    e.rect(x, y, x + w, y + h, VERRE, a)
    e.border(x, y, w, h, BLANC, 26)


def doux(e, x, y, w, h, c, a):
    """Ui.soft : rectangle aux coins entamés."""
    e.rect(x + 1, y, x + w - 1, y + 1, c, a)
    e.rect(x, y + 1, x + w, y + h - 1, c, a)
    e.rect(x + 1, y + h - 1, x + w - 1, y + h, c, a)


def ecrit(e, s, x, y, p, c, maxw=None, quoi=""):
    """g.drawString(font, s, x, y) : y = haut de la ligne du jeu."""
    return e.texte(s, x, y + DY.get(p, 1.5), p, c, maxw=maxw, quoi=quoi)


def droite(e, s, xr, y, p, c, maxw=None, quoi=""):
    """Ui.right : texte qui finit en xr."""
    w = e.largeur(s.upper() if p[2] else s, p)
    return ecrit(e, s, xr - w, y, p, c, maxw=maxw, quoi=quoi)


def couper(e, s, p, maxw):
    """Ui.ellipsis."""
    if e.largeur(s, p) <= maxw:
        return s
    while s and e.largeur(s + "…", p) > maxw:
        s = s[:-1]
    return s + "…"


def cadre_clan(e, L, actif, nom="Nuit Rouge", tag="HZ", banniere=ROUGE):
    """ClanScreen : verre, en-tête (bannière, nom, tag, niveau, XP, en ligne, croix) et barre d'onglets.
    Rend la fin de la barre d'onglets (pour vérifier qu'elle tient dans le panneau)."""
    e.rect(0, 0, GW, GH, 0x050608, 120)
    verre(e, PX, PY, PW, PH)
    e.rect(PX + M, PY + 6, PX + M + 24, PY + 38, banniere, 230)
    e.poly([(PX + M, PY + 38), (PX + M + 12, PY + 32), (PX + M + 24, PY + 38)], VERRE, 255)
    tx = PX + M + 34
    w = ecrit(e, nom, tx, PY + 10, BOLD, TEXT)
    w2 = ecrit(e, "[" + tag + "]", tx + w + 6, PY + 10, FONT, MUTED)
    niv = L.t("gui.niveau", 12).upper()
    cx = tx + w + 6 + w2 + 8
    cw = e.largeur(niv, SMALL_GRAS) + 8
    e.rect(cx, PY + 9, cx + cw, PY + 19, banniere, 50)
    e.border(cx, PY + 9, cw, 10, banniere, 140)
    e.c(niv, cx + cw / 2, PY + 14, SMALL_GRAS, TEXT)
    e.rect(tx, PY + 24, tx + 150, PY + 26, BLANC, 30)
    e.rect(tx, PY + 24, tx + 96, PY + 26, banniere, 230)
    ecrit(e, L.t("gui.entete.xp", "12 480", "19 000"), tx, PY + 29, SMALL, MUTED)
    en_ligne = L.t("gui.en_ligne", 3, 6)
    e.d(en_ligne, PX + PW - 40, PY + 16, SMALL_ESP, MUTED)
    e.disc(PX + PW - 40 - e.largeur(en_ligne.upper(), SMALL_ESP) - 8, PY + 15, 1.6, OK, 255)
    e.c("×", PX + PW - 18, PY + 16, BOLD, MUTED)
    e.rect(PX + 8, PY + ENTETE, PX + PW - 8, PY + ENTETE + 1 / 3, BLANC, 40)
    ox, ty = PX + M, PY + ENTETE + 6
    onglet = (False, 9 / 1.302, 1.0)
    for cle in ONGLETS_CLES if actif in ONGLETS_CLES else ONGLETS_CLES + [actif]:
        lib = L.t("gui.onglet." + cle)
        lw = e.largeur(lib.upper(), onglet)
        ecrit(e, lib, ox, ty, onglet, TEXT if cle == actif else MUTED)
        if cle == actif:
            e.rect(ox, ty + 10, ox + lw, ty + 11, banniere, 230)
        ox += lw + 16
    if ox - 16 > PX + PW - M:
        DEFAUTS.append("[%s] onglets trop larges : %.1f > %.1f" % (L.code, ox - 16, PX + PW - M))
    e.rect(PX + 8, PY + ENTETE + ONGLETS + 2, PX + PW - 8, PY + ENTETE + ONGLETS + 2 + 1 / 3, BLANC, 22)
    return ox - 16


def infobulle(e, lignes, mx, my, bord_droit=PX + PW):
    """Ui.tooltip : lignes = [(texte, police, couleur)], première ligne en titre (12 sous elle, 10 ensuite)."""
    w = max(e.largeur(s, p) for s, p, _c in lignes)
    h = 9 if len(lignes) == 1 else len(lignes) * 10 + 1
    x, y = mx + 12, my - 10
    if x + w + 8 > min(GW, bord_droit):
        x = max(6, mx - w - 14)
    y = max(6, min(y, GH - h - 6))
    e.rect(x - 6, y - 5, x + w + 6, y + h + 3, 0x0B0C0F, 242)
    e.rect(x - 6, y - 5, x + w + 6, y - 4, ROUGE, 200)
    for x0, y0, x1, y1 in ((x - 6, y - 4, x - 5, y + h + 3), (x + w + 5, y - 4, x + w + 6, y + h + 3), (x - 6, y + h + 2, x + w + 6, y + h + 3)):
        e.rect(x0, y0, x1, y1, BLANC, 18)
    ly = y
    for i, (s, p, c) in enumerate(lignes):
        ecrit(e, s, x, ly, p, c)
        ly += 12 if i == 0 else 10
    if x - 6 < 0 or x + w + 6 > GW:
        DEFAUTS.append("infobulle hors de l'écran : %.1f .. %.1f" % (x - 6, x + w + 6))


def cellules(e, L, ligne, wl, ty, ly, survol):
    """Cases d'une ligne du classement (11/11) : points entiers seulement ; place au général en toutes lettres."""
    rang, _tag, _nom, _coul, pts, _pl = ligne
    for k in range(3):
        xr = X + R_CAT[k]
        if survol == k:
            doux(e, xr - CASE + 2, ly + 1, CASE, LIGNE - 2, COULEURS[k], 22)
        droite(e, L.nombre(int(pts[k] + 1e-9)), xr, ty, FONT, TEXT, quoi="points")
    if survol == 3:
        doux(e, X + R_CAT[2] + 2, ly + 1, wl - R_CAT[2], LIGNE - 2, BLANC, 14)
    r = L.rang(rang)
    droite(e, r, X + wl, ty, BOLD, OR if rang == 1 else TEXT)
    if e.largeur(r, BOLD) > wl - R_CAT[2] - 2:
        DEFAUTS.append("[%s] place « %s » plus large que la colonne Gén." % (L.code, r))


def onglet_saison(e, L, clans, moi, survol=None, cat_bareme=0):
    """SaisonTab : classement à gauche, état du clan en bas, « Gagner des points » à droite. survol : (ligne, case) avec
    case 0..2 = catégorie, 3 = Gén. ; rend l'infobulle à dessiner [(texte, police, couleur)] et la position de la souris."""
    lignes = classement(clans)
    n = len(lignes)
    titre = L.t("gui.saison.titre", 1)
    tw = e.largeur(titre.upper(), (True, 11 / 1.302, 1.2))
    ecrit(e, titre, X, Y, (True, 11 / 1.302, 1.2), TEXT)
    # Ui.duree : « 23 j 4 h » (fin de saison dans 23 jours)
    duree = "23" + L.t("gui.duree.j") + " 4" + L.t("gui.duree.h")
    sous = couper(e, L.t("gui.saison.sous_titre", duree), SMALL, GAUCHE - tw - 12)
    ecrit(e, sous, X + tw + 12, Y + 1, SMALL, FAINT)
    hy = Y + 14
    ecrit(e, "#", X + 2, hy, SMALL_ESP, MUTED)
    ecrit(e, L.t("gui.saison.col.clan"), X + 34, hy, SMALL_ESP, MUTED)
    for k, cle in enumerate(("pve", "pvp", "events")):
        droite(e, L.t("gui.saison.col." + cle), X + R_CAT[k], hy, SMALL_ESP, COULEURS[k])
    droite(e, L.t("gui.saison.col.general"), X + GAUCHE, hy, SMALL_ESP, MUTED)
    e.rect(X, hy + 10, X + GAUCHE, hy + 10 + 1 / 3, BLANC, 40)
    wl, y0 = GAUCHE - 2, Y + 26
    bulle, souris = None, None
    for i, ligne in enumerate(lignes):
        rang, tag, nom, coul, pts, pl = ligne
        ly, ty = y0 + i * LIGNE, y0 + i * LIGNE + 5
        if tag == moi:
            doux(e, X, ly, wl, LIGNE, ROUGE, 28)
            e.rect(X, ly + 1, X + 2, ly + LIGNE - 1, ROUGE, 220)
        droite(e, str(rang), X + 16, ty, BOLD, OR if rang == 1 else TEXT if rang <= 3 else MUTED)
        e.rect(X + 21, ly + 3, X + 29, ly + 14, coul, 220)
        nom_c = couper(e, nom, BOLD, 100)
        nw = ecrit(e, nom_c, X + 34, ty, BOLD, TEXT)
        tagc = couper(e, "[" + tag + "]", SMALL, max(0, R_CAT[0] - 36 - 4 - (34 + nw + 4)))
        ecrit(e, tagc, X + 34 + nw + 4, ty + 1, SMALL, MUTED)
        k = survol[1] if survol and survol[0] == i else None
        cellules(e, L, ligne, wl, ty, ly, k)
        if i < n - 1:
            e.rect(X, ly + LIGNE - 1, X + wl, ly + LIGNE - 1 + 1 / 3, BLANC, 14)
        if k is not None:
            souris = (X + (R_CAT[k] - 12 if k < 3 else wl - 6), ly + 8)
            nb = max(n, rang)
            if k < 3:
                place = L.t("gui.saison.infobulle.place_seul", L.rang(pl[k])) if nb <= 1 else \
                    L.t("gui.saison.infobulle.place", L.rang(pl[k]), nb)
                bulle = [(L.t(("gui.saison.cat.pve", "gui.saison.cat.pvp", "gui.saison.cat.events")[k]), BOLD, COULEURS[k]),
                         (L.t("gui.saison.infobulle.points", L.decimal(pts[k])), FONT, TEXT),
                         (place, FONT, TEXT)]
            else:
                t1 = L.t("gui.saison.infobulle.general_seul", L.rang(rang)) if nb <= 1 else \
                    L.t("gui.saison.infobulle.general", L.rang(rang), nb)
                bulle = [(t1, BOLD, TEXT), (L.t("gui.saison.infobulle.general_regle"), FONT, TEXT),
                         (L.t("gui.saison.infobulle.general_calcul", pl[0], pl[1], pl[2], sum(pl)), FONT, MUTED)]
    # bas gauche : état du clan
    by = Y + H - BOITE
    doux(e, X, by, GAUCHE, BOITE, BLANC, 8)
    e.border(X + 1, by + 1, GAUCHE - 2, BOITE - 2, BLANC, 18)
    ecrit(e, L.t("gui.saison.votre_clan"), X + 8, by + 6, SMALL_ESP, MUTED)
    mien = next(x for x in lignes if x[1] == moi)
    etat = L.t("gui.saison.etat", mien[0], L.nombre(int(sum(mien[4]) + 1e-9)))
    ecrit(e, couper(e, etat, BOLD, GAUCHE - 116), X + 8, by + 17, BOLD, TEXT)
    ix = X + GAUCHE - 100
    e.disc(ix, by + 10, 1.6, OK, 255)
    ecrit(e, couper(e, L.t("gui.saison.coeur_ok"), SMALL, 90), ix + 8, by + 8, SMALL, OK)
    ecrit(e, couper(e, L.t("gui.saison.en_cours"), SMALL, 90), ix + 8, by + 19, SMALL, MUTED)
    # droite : « Gagner des points »
    rx, rw = X + GAUCHE + 14, W - GAUCHE - 14
    e.rect(rx, Y - 2, rx + rw, Y + H, VERRE, 120)
    e.border(rx + 1, Y - 1, rw - 2, H, BLANC, 26)
    ecrit(e, L.t("gui.saison.gagner"), rx + 10, Y + 5, (True, 11 / 1.302, 1.2), TEXT, maxw=rw - 20, quoi="Gagner des points")
    px = rx + 10
    for k, cle in enumerate(("gui.saison.cat.pve", "gui.saison.cat.pvp", "gui.saison.cat.events")):
        nom = L.t(cle)
        pw = int(np.ceil(e.largeur(nom.upper(), SMALL_ESP))) + 12
        actif = k == cat_bareme
        e.rect(px, Y + 18, px + pw, Y + 30, COULEURS[k], 60 if actif else 12)
        e.border(px, Y + 18, pw, 12, COULEURS[k], 200 if actif else 50)
        e.c(nom, px + pw / 2, Y + 24, SMALL_ESP, TEXT if actif else MUTED)
        px += pw + 5
    e.rect(rx + 10, Y + 34, rx + rw - 10, Y + 34 + 1 / 3, BLANC, 22)
    ly = Y + 38
    for cle, forme, valeurs, cond, args in bareme_pve():
        if ly + ENTREE > Y + H - 24:      # liste défilante de hauteur h - 62 : la suite se voit en faisant défiler
            break
        pts = L.t("gui.saison.pts." + forme, *[L.decimal(v) for v in valeurs])
        pwid = e.largeur(pts, BOLD)
        droite(e, pts, rx + rw - 12, ly + 2, BOLD, PVE_C)
        ecrit(e, couper(e, L.t("gui.saison.l." + cle), BOLD, rw - 22 - pwid - 8), rx + 10, ly + 2, BOLD, TEXT)
        if cond:
            ecrit(e, couper(e, L.t("gui.saison.c." + cond, *args), SMALL, rw - 22), rx + 10, ly + 12, SMALL, FAINT)
        e.rect(rx + 10, ly + ENTREE - 1, rx + rw - 12, ly + ENTREE - 1 + 1 / 3, BLANC, 14)
        ly += ENTREE
    ecrit(e, couper(e, L.t("gui.saison.note"), SMALL, rw - 20), rx + 10, Y + H - 14, SMALL, AMBRE)
    return bulle, souris


def ecran_saison(sortie, L, clans=CLANS, moi=MOI, survol=None):
    e = ab.Ecran(ab.fond())
    cadre_clan(e, L, "saison")
    bulle, souris = onglet_saison(e, L, clans, moi, survol)
    if bulle:
        infobulle(e, bulle, *souris)
    DEFAUTS.extend(e.defauts)
    e.defauts = []
    if sortie:
        e.sauver(sortie)
    return e


# ------------------------------------------------------------------------------------------------ icône de la Purge
def fond_jeu():
    """Une scène de jeu floue (ciel, horizon, sol) pour poser l'icône de la Purge."""
    Wp, Hp = GW * K, GH * K
    rng = np.random.default_rng(3)
    y = np.linspace(0, 1, Hp)[:, None]
    ciel, horizon, sol = np.array([96, 112, 128], np.float32), np.array([150, 150, 140], np.float32), np.array([58, 66, 44], np.float32)
    base = np.where(y[..., None] < 0.5, ciel + (horizon - ciel) * np.clip(y / 0.5, 0, 1)[..., None],
                    horizon + (sol - horizon) * np.clip((y - 0.5) / 0.15, 0, 1)[..., None])
    img = np.repeat(base, Wp, axis=1)
    bruit = Image.fromarray((rng.random((24, 42)) * 255).astype(np.uint8)).resize((Wp, Hp), Image.BICUBIC)
    img = img + ((np.array(bruit, np.float32) / 255 - 0.5) * 40)[..., None]
    return Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGB").filter(ImageFilter.GaussianBlur(K * 1.2)).convert("RGBA")


def hud_jeu(e):
    """Barre d'objets et viseur, pour situer l'icône."""
    e.rect(GW / 2 - 0.5, GH / 2 - 4, GW / 2 + 0.5, GH / 2 + 4, BLANC, 200)
    e.rect(GW / 2 - 4, GH / 2 - 0.5, GW / 2 + 4, GH / 2 + 0.5, BLANC, 200)
    hx = GW / 2 - 91
    for i in range(9):
        e.rect(hx + i * 20.2, GH - 24, hx + i * 20.2 + 19, GH - 5, 0x000000, 110)
        e.border(hx + i * 20.2, GH - 24, 19, 19, BLANC, 40)


def icone_purge(e, temps="1 h 05", part=0.84, souffle=1.0):
    """Icône retenue (25/10) : mêmes mesures que client/PurgeHud de CRac (coin 6,6 ; hauteur 12 ; police mini grasse)."""
    MINI_GRAS = (True, 6.5 / 1.0, 0.9)
    x, y, h = 6, 6, 12
    wt = e.largeur("PURGE", MINI_GRAS)
    wd = e.largeur(temps, (True, 6.5, 0))
    w = 12 + wt + 6 + 1 + 5 + wd + 6
    e.rect(x, y, x + w, y + h, VERRE, 200)
    e.border(x, y, w, h, BLANC, 26)
    e.rect(x + 1 / 3, y + 1 / 3, x + w - 1 / 3, y + 2 / 3, BLANC, 36)
    e.glow(x + 6, y + h / 2, 4.5, 4.5, ROUGE_VIF, 60 + 70 * souffle)
    e.disc(x + 6, y + h / 2, 2.2, ROUGE_VIF, 255)
    e.m("PURGE", x + 12, y + h / 2, MINI_GRAS, ROUGE_VIF)
    tx = x + 12 + wt + 6
    e.rect(tx, y + 3, tx + 1 / 3, y + h - 3, BLANC, 40)
    e.m(temps, tx + 5, y + h / 2, (True, 6.5, 0), TEXT)
    e.rect(x + 1 / 3, y + h - 2 / 3, x + 1 / 3 + (w - 2 / 3) * (1 - part), y + h - 1 / 3, ROUGE_VIF, 200)


def ecran_purge(sortie):
    e = ab.Ecran(fond_jeu())
    hud_jeu(e)
    icone_purge(e)
    DEFAUTS.extend(e.defauts)
    e.defauts = []
    e.sauver(sortie)


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "saison"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    for code in ("fr_fr", "en_us", "ru_ru"):
        ab.LANGUE = code
        L = Langue(code)
        court = code[:2]
        ecran_saison(dossier / f"saison_{court}.png", L)
        # infobulles : case PVE de la 2e ligne, colonne « Gén. » de la 3e (le clan du joueur)
        ecran_saison(dossier / f"saison_infobulle_case_{court}.png", L, survol=(1, 0))
        ecran_saison(dossier / f"saison_infobulle_general_{court}.png", L, survol=(2, 3))
        ecran_saison(dossier / f"saison_clan_seul_{court}.png", L, clans=SEUL, survol=(0, 0))
    ab.LANGUE = "fr_fr"
    ecran_purge(dossier / "purge_icone.png")
    if DEFAUTS:
        print("\n".join(DEFAUTS))
        sys.exit(1)
    print("aucun texte ne dépasse (fr, en, ru) ->", dossier)


if __name__ == "__main__":
    main()
