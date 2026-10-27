"""Maquette à valider (règle 6) : écran du compte de capsules (« ATM à capsules », demande du propriétaire, 27/10).

    python modules/boutique/outils/maquette_atm.py [DOSSIER]      (par défaut : maquettes/atm_capsules)

Rien n'est installé dans le jeu : ce sont des images. Même moteur que les aperçus de la boutique (apercu_boutique.py :
canevas 640 x 360 unités, verre, polices DM Sans à leur vraie taille), enregistrées en 1920 x 1080, en français.
  1_compte.png        : l'écran du compte (solde, capsules sur soi, déposer, retirer, derniers mouvements)
  2_retrait_refuse.png : même écran, retrait refusé faute de place dans l'inventaire
Le serveur fait déjà tout (dépôt, retrait, paiement à la caisse et à l'entrée par le compte, /capsules) : seul l'aspect
(écran, et borne ou guichet où on l'ouvre) attend la validation.
"""
import sys
from pathlib import Path

from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import apercu_boutique as ab  # noqa: E402

RACINE = Path(__file__).resolve().parents[3]
FOND_JEU = RACINE / "modules/interface/src/main/resources/assets/heraliz_interface/textures/gui/fond_titre.png"
JETONS = RACINE / "modules/boutique/src/client/resources/assets/heralizcasino/textures/item"
GW, GH, M = ab.GW, ab.GH, 16
ACCENT, WHITE, LIGHT, OPTION, MUTED, FAINT, GOLD, OK, DANGER = (ab.ACCENT, ab.WHITE, ab.LIGHT, ab.OPTION, ab.MUTED, ab.FAINT,
                                                                ab.GOLD, ab.OK, ab.DANGER)
TITRE, NOM, BOUTON, MINI, MINI_NUM = ab.TITRE, ab.NOM, ab.BOUTON, ab.MINI, ab.MINI_NUM
TEXTE, TEXTE_GRAS, PETIT, PETIT_GRAS, MICRO = ab.TEXTE, ab.TEXTE_GRAS, ab.PETIT, ab.PETIT_GRAS, ab.MICRO
CHIFFRE_GRAND = ab.CHIFFRE_GRAND
CAPSULE = 0xE0503C  # rouge de la capsule de 10 (Chips.COLORS)


def nombre(v):
    return "{:,}".format(int(v)).replace(",", " ")


def fond_jeu():
    im = Image.open(FOND_JEU).convert("RGB").resize((GW * ab.K, int(GW * ab.K * 1058 / 1600)), Image.LANCZOS)
    im = im.crop((0, 0, GW * ab.K, GH * ab.K)).filter(ImageFilter.GaussianBlur(ab.K * 3))
    e = ab.Ecran(im.convert("RGBA"))
    ab.voile(e)
    return e


def jeton(v):
    return Image.open(JETONS / ("jeton_%d.png" % v)).convert("RGBA")


def entete(e):
    e.texte("COMPTE DE CAPSULES", M, 18, TITRE, LIGHT)
    e.texte("Guichet du casino · tes capsules gardées par le serveur, à toi seul, même si tu meurs sur la carte", M, 35, TEXTE,
            MUTED, maxw=GW - 2 * M, quoi="sous-titre")
    e.rect(M, 50, GW - M, 50 + 1 / 3, WHITE, 23)


def pied(e, touches, note):
    cy, x = 348, M
    for k, lab in touches:
        w = max(9, e.largeur(k, MINI_NUM) + 4.67)
        e.rect(x, cy - 4.5, x + w, cy + 4.5, WHITE, 30)
        e.border(x, cy - 4.5, w, 9, WHITE, 61)
        e.c(k, x + w / 2, cy, MINI_NUM, WHITE)
        x += w + 3.5
        x += e.m(lab, x, cy, PETIT, MUTED) + 10
    e.d(note, GW - M, cy, PETIT, FAINT, maxw=GW - M - x - 8, quoi="note du pied")


def bouton(e, x, y, w, h, texte, actif=True, c=ACCENT, p=BOUTON):
    ab.bouton(e, x, y, w, h, actif, c)
    e.c(texte, x + w / 2, y + h / 2, p, WHITE if actif else FAINT, maxw=w - 6, quoi="bouton")


def champ(e, x, y, w, h, texte, saisi=False):
    e.rect(x, y, x + w, y + h, WHITE, 12)
    e.border(x, y, w, h, WHITE, 60 if saisi else 40)
    e.m(texte, x + 6, y + h / 2, TEXTE if saisi else PETIT, LIGHT if saisi else FAINT, maxw=w - 10, quoi="champ")
    if saisi:
        tw = e.largeur(texte, TEXTE)
        e.rect(x + 6 + tw + 1, y + 3.5, x + 6 + tw + 1.5, y + h - 3.5, WHITE, 200)


def carte_solde(e, x, y, w, h, solde, sur_toi):
    ab.verre(e, x, y, w, h)
    ab.filet_haut(e, x, y, w, GOLD)
    e.glow(x + 34, y + 44, 34, 30, CAPSULE, 70)
    e.image(jeton(10), x + 14, y + 24, 21, 21)
    e.texte("SUR TON COMPTE", x + 50, y + 22, MINI, ab.mix(GOLD, WHITE, 0.35))
    w1 = e.texte(nombre(solde), x + 50, y + 33, CHIFFRE_GRAND, WHITE, maxw=w - 60, quoi="solde")
    e.texte("capsules", x + 50 + w1 + 4, y + 33 + e.hcap(CHIFFRE_GRAND) - e.hcap(TEXTE_GRAS), TEXTE_GRAS, GOLD)
    e.rect(x + 10, y + 62, x + w - 10, y + 62 + 1 / 3, WHITE, 23)
    e.texte("SUR TOI", x + 10, y + 70, MINI, MUTED)
    total = sum(v * n for v, n in sur_toi)
    e.d("%s capsules" % nombre(total), x + w - 10, y + 70 + e.hcap(MINI) / 2, PETIT_GRAS, LIGHT)
    cx = x + 10
    for v, n in sur_toi:
        e.image(jeton(v), cx, y + 82, 14, 14)
        cx += 16
        cx += e.m("× %s" % nombre(n), cx, y + 89, TEXTE_GRAS, LIGHT) + 14
    e.texte("Capsules de 1, 5 et 10 : on te rend toujours les plus grosses possibles.", x + 10, y + h - 14, PETIT, FAINT,
            maxw=w - 20, quoi="note des capsules")


def carte_operations(e, x, y, w, h, retrait_saisi, refus):
    ab.verre(e, x, y, w, h)
    # déposer
    e.texte("DÉPOSER", x + 10, y + 10, MINI, OK)
    bouton(e, x + 10, y + 20, w - 20, 18, "TOUT DÉPOSER · 1 386 CAPSULES", c=OK)
    bw = (w - 20 - 2 * 4) / 3
    for i, v in enumerate((100, 500, 1000)):
        bouton(e, x + 10 + i * (bw + 4), y + 42, bw, 15, nombre(v), c=OK)
    champ(e, x + 10, y + 61, w - 20 - 40, 15, "Autre nombre…")
    bouton(e, x + w - 10 - 36, y + 61, 36, 15, "OK", c=OK)
    e.rect(x + 10, y + 86, x + w - 10, y + 86 + 1 / 3, WHITE, 23)
    # retirer
    e.texte("RETIRER", x + 10, y + 94, MINI, GOLD)
    for i, v in enumerate((100, 1000, 5000)):
        bouton(e, x + 10 + i * (bw + 4), y + 104, bw, 15, nombre(v), c=GOLD)
    champ(e, x + 10, y + 123, w - 20 - 40, 15, retrait_saisi or "Autre nombre…", saisi=bool(retrait_saisi))
    bouton(e, x + w - 10 - 36, y + 123, 36, 15, "OK", c=GOLD)
    if refus:
        e.rect(x + 10, y + 144, x + w - 10, y + 158, DANGER, 30)
        e.border(x + 10, y + 144, w - 20, 14, DANGER, 120)
        e.m(refus, x + 16, y + 151, PETIT_GRAS, ab.mix(DANGER, WHITE, 0.3), maxw=w - 32, quoi="refus")
    else:
        for k, l in enumerate(e.lignes("Refusé s'il n'y a pas la place pour toutes les capsules : rien n'est jamais jeté par terre.",
                                       PETIT, w - 20)):
            e.texte(l, x + 10, y + 145 + k * 8.5, PETIT, FAINT, maxw=w - 20, quoi="note retrait")


def carte_mouvements(e, x, y, w, h):
    ab.verre(e, x, y, w, h)
    e.texte("DERNIERS MOUVEMENTS", x + 10, y + 10, MINI, MUTED)
    lignes = [("+1 250", "Dépôt", "il y a 2 min", OK), ("−25", "Entrée du casino", "il y a 9 min", DANGER),
              ("−10 000", "Finisher Cerisier Pourpre · caisse", "hier", DANGER), ("+8 400", "Dépôt", "hier", OK),
              ("−500", "Retrait", "avant-hier", GOLD), ("−4 500", "Clé camo · caisse", "avant-hier", DANGER),
              ("+15 000", "Dépôt", "il y a 3 jours", OK)]
    ly = y + 24
    for montant, quoi, quand, c in lignes:
        e.rect(x + 8, ly - 1, x + w - 8, ly + 13, WHITE, 6)
        e.m(montant, x + 14, ly + 6, TEXTE_GRAS, c)
        e.m(quoi, x + 70, ly + 6, TEXTE, LIGHT, maxw=w - 70 - 70, quoi="mouvement")
        e.d(quand, x + w - 14, ly + 6, PETIT, FAINT)
        ly += 16


def carte_caisse(e, x, y, w, h):
    ab.verre(e, x, y, w, h)
    e.texte("À LA CAISSE ET À L'ENTRÉE", x + 10, y + 10, MINI, MUTED)
    e.coche(x + 10, y + 22, 6, 1.2, OK)
    e.texte("Ce qui te manque sur toi est pris sur ton compte", x + 21, y + 23, TEXTE_GRAS, LIGHT, maxw=w - 31, quoi="caisse")
    ly = y + 38
    for l in e.lignes("Un inventaire rempli de capsules de 10 n'en porte que 23 040 : sans le compte, la Main du Joker "
                      "(80 000) ou le Bonus de loot (25 000) seraient impossibles à payer.", PETIT, w - 20):
        e.texte(l, x + 10, ly, PETIT, MUTED, maxw=w - 20, quoi="explication")
        ly += 9
    e.texte("Machines de jeu : mises avec les capsules sur toi (à décider)", x + 10, ly + 6, PETIT_GRAS, FAINT, maxw=w - 20,
            quoi="machines")


def ecran(sortie, retrait_saisi="", refus=""):
    e = fond_jeu()
    entete(e)
    gauche = 290
    carte_solde(e, M, 60, gauche, 118, 12450, [(10, 124), (5, 20), (1, 46)])
    carte_mouvements(e, M, 186, gauche, 150)
    xd = M + gauche + 10
    carte_operations(e, xd, 60, GW - M - xd, 170, retrait_saisi, refus)
    carte_caisse(e, xd, 238, GW - M - xd, 98)
    pied(e, [("ÉCHAP", "Fermer"), ("ENTRÉE", "Valider le nombre")], "Maquette à valider · rien n'est installé en jeu")
    e.sauver(sortie)


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "atm_capsules"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    ecran(dossier / "1_compte.png")
    ecran(dossier / "2_retrait_refuse.png", "12 000", "Pas assez de place dans ton inventaire pour 12 000 capsules.")


if __name__ == "__main__":
    main()
