#!/usr/bin/env python3
"""Maquette (25/10, à valider) : nouveau journal des quêtes (touche J), à la charte des écrans du clan et de la Table
d'armurier (audit des écrans en jeu : l'ancien journal « fait très UI IA », titre agrandi flou, panneaux vides).

    python modules/pnj/outils/apercu_journal.py [DOSSIER]      (par défaut : maquettes/quetes)

Même moteur que les autres aperçus (modules/boutique/outils/apercu_boutique.py) : canevas 640 x 360 unités, rendu à
l'échelle 3 (1920 x 1080), polices DM Sans du jeu à leur vraie taille. Panneau 560 x 320 comme l'écran du clan. Refuse tout
texte qui dépasse de sa place (code de sortie 1). Textes en dur ici (maquette) : ils iront dans les fichiers de langue au
moment du code (modules/pnj/outils/langues.py).
"""
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "boutique" / "outils"))
import apercu_boutique as ab  # noqa: E402

ab.FONTS = RACINE / "modules/pnj/src/client/resources/assets/heraliz_quetes/font"
GW, GH = ab.GW, ab.GH
TEXT, MUTED, FAINT, OK, OR, DANGER, BLANC = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0x5FD08A, 0xE8C36A, 0xFF6B6B, 0xFFFFFF
VERRE, ACCENT, AMBRE = 0x0D0F14, 0x6FA8FF, 0xE8B34A
TITRE = (True, 14 / 1.302, 0)          # nouvelle police « ui_titre » (14 px) : jamais de texte agrandi
FONT = (False, 11 / 1.302, 0)
BOLD = (True, 11 / 1.302, 0)
SMALL = (False, 9 / 1.302, 0)
SMALL_GRAS = (True, 9 / 1.302, 0)
CAPS = (False, 9 / 1.302, 1.0)
CAPS_GRAS = (True, 9 / 1.302, 1.0)
MICRO = (True, 7.5 / 1.302, 0.8)
DEFAUTS = []

PX, PY, PW, PH = 40, 20, 560, 320
ENTETE, M = 44, 16
LISTE = 172


def verre(e, x, y, w, h, a=214):
    e.rect(x, y, x + w, y + h, VERRE, a)
    e.border(x, y, w, h, BLANC, 26)


def doux(e, x, y, w, h, a):
    e.rect(x, y, x + w, y + h, BLANC, a)


def puce(e, txt, x, cy, c, plein=False, p=MICRO):
    w = e.largeur(txt, p) + 10
    e.rect(x, cy - 5, x + w, cy + 5, c, 70 if plein else 26)
    e.border(x, cy - 5, w, 10, c, 170)
    e.c(txt, x + w / 2, cy, p, TEXT if plein else c)
    return w


def bouton(e, txt, xd, cy, c=BLANC, plein=False, p=CAPS_GRAS):
    """Petit bouton aligné à droite sur xd ; rend sa largeur."""
    w = e.largeur(txt, p) + 16
    x = xd - w
    if plein:
        e.rect(x, cy - 7, xd, cy + 7, c, 60)
        e.border(x, cy - 7, w, 14, c, 220)
    else:
        e.rect(x, cy - 7, xd, cy + 7, BLANC, 6)
        e.border(x, cy - 7, w, 14, c, 90)
    e.c(txt, x + w / 2, cy, p, TEXT if plein or c == BLANC else c)
    return w


def icone_journal(e, x, y, c):
    """Pictogramme du journal : carnet à pince, trois lignes, une coche."""
    e.rect(x, y, x + 22, y + 26, c, 40)
    e.border(x, y, 22, 26, c, 200)
    e.rect(x + 7, y - 2, x + 15, y + 2, c, 230)
    for i, ly in enumerate((y + 9, y + 14.5, y + 20)):
        e.rect(x + 9, ly - 0.6, x + 18, ly + 0.6, BLANC, 150)
        if i == 0:
            e.coche(x + 3.2, ly - 1.8, 3.6, 0.9, OK)
        else:
            e.border(x + 3.4, ly - 1.6, 3.2, 3.2, BLANC, 110)


def avatar(e, x, y, lettre, c):
    e.rect(x, y, x + 18, y + 18, c, 46)
    e.border(x, y, 18, 18, c, 190)
    e.c(lettre, x + 9, y + 9.2, BOLD, TEXT)


def entete(e, en_cours, finies, titre):
    icone_journal(e, PX + M, PY + 10, OR)
    tx = PX + M + 32
    e.texte("Journal des quêtes", tx, PY + 9, TITRE, TEXT, quoi="titre")
    e.texte("%d EN COURS  ·  %d TERMINÉES" % (en_cours, finies), tx, PY + 27, CAPS, MUTED, quoi="compteurs")
    xd = PX + PW - 34
    if titre:
        w = e.largeur("TITRE : " + titre.upper(), MICRO) + 10
        puce(e, "TITRE : " + titre.upper(), xd - w, PY + 16, OR)
    e.c("×", PX + PW - 18, PY + 16, BOLD, MUTED)
    e.rect(PX + 8, PY + ENTETE, PX + PW - 8, PY + ENTETE + 1 / 3, BLANC, 40)


def pied(e):
    y = PY + PH - 12
    e.rect(PX + 8, y - 8, PX + PW - 8, y - 8 + 1 / 3, BLANC, 22)
    x = PX + M
    for touche, aide in (("J", "Fermer"), ("MOLETTE", "Défiler"), ("CLIC", "Choisir un personnage")):
        w = e.largeur(touche, MICRO) + 8
        e.rect(x, y - 4.5, x + w, y + 4.5, BLANC, 18)
        e.border(x, y - 4.5, w, 9, BLANC, 60)
        e.c(touche, x + w / 2, y, MICRO, TEXT)
        x += w + 4
        x += e.m(aide, x, y, SMALL, MUTED) + 12


def liste_pnj(e, pnj, choix, y0, h):
    x0 = PX + M
    e.texte("PERSONNAGES", x0, y0, CAPS_GRAS, MUTED)
    e.rect(x0 + e.largeur("PERSONNAGES", CAPS_GRAS) + 6, y0 + 3.5, x0 + LISTE, y0 + 3.5 + 1 / 3, BLANC, 30)
    ly = y0 + 12
    if not pnj:
        for i, l in enumerate(e.lignes("Personne ne t'a encore confié de quête.", SMALL, LISTE - 4)):
            e.texte(l, x0, ly + 2 + i * 9, SMALL, FAINT, maxw=LISTE, quoi="liste vide")
        return
    for i, (lettre, nom, role, coul, niveau, rep, rep_max, n) in enumerate(pnj):
        hy = 34
        if i == choix:
            doux(e, x0, ly, LISTE, hy, 12)
            e.rect(x0, ly + 2, x0 + 2, ly + hy - 2, OR, 230)
        avatar(e, x0 + 8, ly + 5, lettre, coul)
        cw = 0
        if n:
            cw = puce(e, str(n), x0 + LISTE - 16, ly + 11, OR, plein=True)
        e.texte(nom, x0 + 32, ly + 5, BOLD, TEXT, maxw=LISTE - 36 - cw - 4, quoi="nom du PNJ")
        e.texte(role, x0 + 32, ly + 16, SMALL, MUTED, maxw=LISTE - 36, quoi="rôle")
        e.rect(x0 + 32, ly + 27, x0 + LISTE - 8, ly + 28.2, BLANC, 26)
        e.rect(x0 + 32, ly + 27, x0 + 32 + (LISTE - 40) * rep / max(1, rep_max), ly + 28.2, OR, 220)
        ly += hy + 4


def carte(e, x, y, w, q):
    """Une quête : titre, difficulté, état, ce qu'il faut faire, étapes, récompense, actions. Rend sa hauteur."""
    titre, diff, etat, resume, etapes, recompense = q
    h = 30 + 13 * len(etapes) + 22
    suivie, a_rendre = etat == "suivie", etat == "a_rendre"
    bord = OK if a_rendre else OR if suivie else BLANC
    e.rect(x, y, x + w, y + h, BLANC, 9 if suivie or a_rendre else 5)
    e.border(x, y, w, h, bord, 120 if suivie or a_rendre else 30)
    if suivie or a_rendre:
        e.rect(x, y + 2, x + 2, y + h - 2, bord, 230)
    # en-tête de la carte
    tw = e.texte(titre, x + 10, y + 7, BOLD, TEXT, maxw=w - 150, quoi="titre de quête")
    cx = x + 10 + tw + 6
    for k in range(3):
        e.disc(cx + 3 + k * 6, y + 11.2, 1.8, OR if k < diff else FAINT, 255)
    xd = x + w - 10
    if a_rendre:
        puce(e, "À RENDRE", xd - e.largeur("À RENDRE", MICRO) - 10, y + 11, OK, plein=True)
    elif suivie:
        pw = e.largeur("SUIVIE", MICRO) + 10
        puce(e, "SUIVIE", xd - pw, y + 11, OR, plein=True)
    e.texte(resume, x + 10, y + 18, SMALL, MUTED, maxw=w - 20, quoi="résumé")
    # étapes
    ly = y + 31
    for libelle, fait, total in etapes:
        fini = fait >= total
        if fini:
            e.rect(x + 10, ly - 3.2, x + 16.4, ly + 3.2, OK, 60)
            e.coche(x + 10.8, ly - 2.4, 4.8, 1.0, OK)
        else:
            e.border(x + 10, ly - 3.2, 6.4, 6.4, BLANC, 110)
        e.m(libelle, x + 22, ly, SMALL_GRAS if not fini else SMALL, TEXT if not fini else MUTED, maxw=w - 100, quoi="étape")
        e.d("%d / %d" % (fait, total), x + w - 10, ly, SMALL_GRAS, OK if fini else TEXT)
        e.rect(x + w - 70, ly + 4.6, x + w - 10, ly + 5.6, BLANC, 22)
        e.rect(x + w - 70, ly + 4.6, x + w - 70 + 60 * min(1, fait / total), ly + 5.6, OK if fini else OR, 220)
        ly += 13
    # pied de carte : récompense, actions
    fy = y + h - 11
    rw = e.texte("RÉCOMPENSE", x + 10, fy - 3.6, MICRO, MUTED)
    e.m(recompense, x + 10 + rw + 6, fy, SMALL_GRAS, OR, maxw=w - 200, quoi="récompense")
    xd = x + w - 8
    if a_rendre:
        e.d("Retourne voir le Commandant Varga", xd, fy, SMALL, OK, quoi="à rendre")
    elif suivie:
        xd -= bouton(e, "ABANDONNER", xd, fy, DANGER) + 5
        bouton(e, "NE PLUS SUIVRE", xd, fy)
    else:
        xd -= bouton(e, "ABANDONNER", xd, fy, DANGER) + 5
        bouton(e, "SUIVRE", xd, fy, OR, plein=True)
    return h


PNJ = [
    ("V", "Commandant Varga", "Chef du camp", OR, 2, 120, 250, 2),
    ("G", "Le Guide", "Accueil du spawn", ACCENT, 1, 40, 100, 1),
]
QUETES = [
    ("Premier tir", 1, "suivie", "Prends une arme à feu et tue 10 zombies avec.",
     [("Tuer 10 zombies avec une arme à feu", 4, 10)], "100 hérals · +10 réputation"),
    ("Fouille", 1, "a_rendre", "Fouille 5 conteneurs sur la carte.",
     [("Fouiller 5 conteneurs", 5, 5)], "100 hérals · +10 réputation"),
    ("Une vraie arme", 1, "", "Montre-moi une arme à feu Peu commune ou mieux.",
     [("Montrer une arme Peu commune ou mieux", 0, 1)], "150 hérals · +15 réputation"),
]


def ecran(sortie, vide=False):
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 130)
    verre(e, PX, PY, PW, PH)
    entete(e, 0 if vide else 3, 0 if vide else 7, None if vide else "Recrue")
    y0 = PY + ENTETE + 10
    h = PH - ENTETE - 34
    liste_pnj(e, [] if vide else PNJ, 0, y0, h)
    rx = PX + M + LISTE + 12
    rw = PX + PW - M - rx
    verre(e, rx, y0 - 2, rw, h + 2, 110)
    if vide:
        cx, cy = rx + rw / 2, y0 + h / 2 - 14
        icone_journal(e, cx - 11, cy - 30, FAINT)
        e.c("Aucune quête en cours", cx, cy + 8, BOLD, TEXT)
        for i, l in enumerate(e.lignes("Va voir le Commandant Varga au spawn : il a du travail pour toi.", SMALL, rw - 60)):
            e.c(l, cx, cy + 22 + i * 9, SMALL, MUTED, maxw=rw - 40, quoi="aide vide")
        bouton(e, "SUIVRE LE GUIDE", cx + (e.largeur("SUIVRE LE GUIDE", CAPS_GRAS) + 16) / 2, cy + 46, OR, plein=True)
    else:
        nom, role = "Commandant Varga", "CHEF DU CAMP"
        nw = e.texte(nom, rx + 12, y0 + 6, BOLD, TEXT)
        e.texte(role, rx + 12 + nw + 8, y0 + 7.6, CAPS, MUTED)
        rep = "ALLIÉ · 120 / 250"
        e.d(rep, rx + rw - 12, y0 + 9.5, MICRO, OR)
        e.rect(rx + rw - 12 - 80, y0 + 15, rx + rw - 12, y0 + 16.2, BLANC, 26)
        e.rect(rx + rw - 12 - 80, y0 + 15, rx + rw - 12 - 80 + 80 * 120 / 250, y0 + 16.2, OR, 220)
        cy = y0 + 24
        for q in QUETES:
            cy += carte(e, rx + 8, cy, rw - 16, q) + 6
        if cy > y0 + h:
            DEFAUTS.append("cartes trop hautes : %.1f > %.1f (défilement)" % (cy, y0 + h))
    pied(e)
    DEFAUTS.extend(e.defauts)
    e.defauts = []
    e.sauver(sortie)


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "quetes"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    ab.LANGUE = "fr_fr"
    ecran(dossier / "journal_quetes.png")
    ecran(dossier / "journal_quetes_vide.png", vide=True)
    if DEFAUTS:
        print("\n".join(DEFAUTS))
        sys.exit(1)
    print("aucun texte ne dépasse ->", dossier)


if __name__ == "__main__":
    main()
