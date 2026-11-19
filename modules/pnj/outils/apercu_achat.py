#!/usr/bin/env python3
"""Maquette (19/11, à valider) : panneau de confirmation d'un achat chez un marchand, avec la quantité (demande du
propriétaire : « quand on appuie sur Acheter, un panneau de sécurité au cas où on clique par erreur, et combien on en
veut : 500 brindilles, c'est 500 clics »).

    python modules/pnj/outils/apercu_achat.py [DOSSIER]      (par défaut : maquettes/achat_quantite)

Même moteur que les autres aperçus (modules/boutique/outils/apercu_boutique.py) : canevas 640 x 360 unités, rendu à
l'échelle 3 (1920 x 1080), polices DM Sans du jeu à leur vraie taille. Mesures de client/EcranMarchand (écran 600 x 330,
cartes 4 colonnes, panneau CONF_L x CONF_H). Textes en dur ici (maquette) : ils sont dans modules/pnj/outils/langues.py
(clés gui.heraliz_quetes.achat.*) ; un texte qui dépasse sa place, en français, anglais ou russe, arrête le script
(code de sortie 1).

Écrans : 1 le Bâtisseur, 500 brindilles ; 2 le Passeur (armes et munitions), une arme Rare au prix bêta ; 3 la quantité
tapée est trop grande (Confirmer éteint, la raison sous la quantité).
"""
import sys
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "boutique" / "outils"))
import apercu_boutique as ab  # noqa: E402

GW, GH = ab.GW, ab.GH
TEXT, MUTED, FAINT, OK, OR, DANGER, BLANC = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0x5FD08A, 0xE8C36A, 0xFF6B6B, 0xFFFFFF
VERRE, NUIT = 0x0D0F14, 0x0D0F14
# polices du mod (taille du .json / 1,302 = taille de l'em), comme modules/pnj/outils/apercu_journalieres.py
TITRE = (True, 14 / 1.302, 0)
FONT = (False, 11 / 1.302, 0)
BOLD = (True, 11 / 1.302, 0)
SMALL = (False, 9 / 1.302, 0)
CAPS = (False, 9 / 1.302, 0.8)       # Ui.spaced(SMALL, 0.8)
CAPS_ROLE = (False, 9 / 1.302, 0.8)
CAPS_BTN = (True, 11 / 1.302, 1.2)   # HzButton (contour, principal) : BOLD espacé 1,2
CAPS_PILL = (False, 9 / 1.302, 0.6)  # HzButton pastille : SMALL espacé 0,6
CAPS_ACHAT = (False, 9 / 1.302, 0.4)  # bouton ACHETER d'une carte
RARETES = [0xAAAAAA, 0x5FD06E, 0x508CFF, 0xB96EF0, 0xF0BE46, 0xEB4646]  # EcranMarchand.RARETES

# écran du marchand (EcranMarchand) et panneau de confirmation (mêmes valeurs que CONF_L, CONF_H...)
PX, PY, PW, PH = 20, 15, 600, 330
COLS, CARTE_H = 4, 44
CONF_L, CONF_H = 300, 196
DEFAUTS = []

A = RACINE / "modules"
TACZ = RACINE / "serveur/tacz/heraliz_1_base_tacz/assets/tacz/textures"
ICONES = {
    "brindilles": A / "equipement/src/client/resources/assets/heraliz_composants/textures/item/icon/brindilles.png",
    "planches": A / "equipement/src/client/resources/assets/heraliz_composants/textures/item/icon/planches.png",
    "pierres_taillees": A / "equipement/src/client/resources/assets/heraliz_composants/textures/item/icon/pierres_taillees.png",
    "fragments_metal": A / "equipement/src/client/resources/assets/heraliz_composants/textures/item/icon/fragments_metal.png",
    "metal_renforce": A / "equipement/src/client/resources/assets/heraliz_composants/textures/item/icon/metal_renforce.png",
    "glock_17": TACZ / "gun/slot/glock_17.png", "m1911": TACZ / "gun/slot/m1911.png", "ump45": TACZ / "gun/slot/ump45.png",
    "m870": TACZ / "gun/slot/m870.png", "sks_tactical": TACZ / "gun/slot/sks_tactical.png", "cz75": TACZ / "gun/slot/cz75.png",
    "uzi": TACZ / "gun/slot/uzi.png", "db_short": TACZ / "gun/slot/db_short.png", "9mm": TACZ / "ammo/slot/9mm.png",
    "45acp": TACZ / "ammo/slot/45acp.png", "12g": TACZ / "ammo/slot/12g.png", "762x39": TACZ / "ammo/slot/762x39.png",
}

# textes (maquette) : clé -> (français, anglais, russe) ; ceux du jeu sont dans langues.py
TEXTES = {
    "titre": ("Confirmer l'achat", "Confirm purchase", "Подтвердить покупку"),
    "unite": ("%s hérals l'unité", "%s herals each", "%s гералов за штуку"),
    "quantite": ("Quantité", "Quantity", "Количество"),
    "max": ("Max", "Max", "Макс."),
    "total": ("Total", "Total", "Итого"),
    "solde": ("Votre solde", "Your balance", "Ваш баланс"),
    "apres": ("Après l'achat", "After purchase", "После покупки"),
    "annuler": ("Annuler", "Cancel", "Отмена"),
    "confirmer": ("Confirmer", "Confirm", "Подтвердить"),
    "limite_herals": ("Au plus %s avec tes hérals", "At most %s with your herals", "Не больше %s на твои гералы"),
    "limite_place": ("Au plus %s : place dans ton inventaire", "At most %s: room in your inventory", "Не больше %s: место в инвентаре"),
    "trop_herals": ("Pas assez d'hérals : %s au plus", "Not enough herals: %s at most", "Не хватает гералов: не больше %s"),
    "trop_place": ("Pas assez de place : %s au plus", "Not enough room: %s at most", "Не хватает места: не больше %s"),
    "pas_assez": ("Pas assez d'hérals pour en acheter un", "Not enough herals to buy one", "Не хватает гералов даже на одну штуку"),
    "plein": ("Plus de place dans ton inventaire", "No room left in your inventory", "В инвентаре нет места"),
    "herals": ("hérals", "herals", "гералов"),
    "votre_solde": ("VOTRE SOLDE", "YOUR BALANCE", "ВАШ БАЛАНС"),
    "acheter": ("ACHETER", "BUY", "КУПИТЬ"),
    "rarete.0": ("Commun", "Common", "Обычный"),
    "rarete.1": ("Peu commun", "Uncommon", "Необычный"),
    "rarete.2": ("Rare", "Rare", "Редкий"),
    "rarete.3": ("Épique", "Epic", "Эпический"),
    "rarete.4": ("Légendaire", "Legendary", "Легендарный"),
}
LANGUES = ("fr_fr", "en_us", "ru_ru")
LANGUE = 0


def tr(cle, *args):
    s = TEXTES[cle][LANGUE]
    for a in args:
        s = s.replace("%s", str(a), 1)
    return s


def nombre(v):
    s = "{:,}".format(int(v))
    return s.replace(",", " ") if LANGUE != 1 else s


_cache = {}


def icone(nom):
    f = ICONES.get(nom)
    if f is None or not f.exists():
        return None
    if f not in _cache:
        im = Image.open(f).convert("RGBA")
        bb = im.getbbox()
        _cache[f] = im.crop(bb) if bb else im
    return _cache[f]


def coupe(e, s, p, maxw):
    """Ui.ellipsis : texte coupé avec « … » s'il dépasse (noms des cartes, comme le jeu)."""
    if e.largeur(s, p) <= maxw:
        return s
    while s and e.largeur(s + "…", p) > maxw:
        s = s[:-1]
    return s.rstrip() + "…"


def poser(e, im, x, y, w, h):
    """Icône centrée dans la boîte, agrandie comme le jeu dessine les objets (pixels nets)."""
    k = ab.K
    s = min(w / im.width, h / im.height)
    iw, ih = max(1, int(round(im.width * s * k))), max(1, int(round(im.height * s * k)))
    r = im.resize((iw, ih), Image.NEAREST if im.width <= 64 else Image.LANCZOS)
    e.im.alpha_composite(r, (int(round((x + w / 2) * k - iw / 2)), int(round((y + h / 2) * k - ih / 2))))


# ------------------------------------------------------------------------------------------------ briques (kit Ui)
def verre(e, x, y, w, h, a=215):
    """Ui.glass : fond 0x0D0F14, contour blanc à 10 %, liseré haut blanc à 16 %."""
    e.rect(x, y, x + w, y + h, VERRE, a)
    e.border(x + 1, y + 1, w - 2, h - 2, BLANC, 26)
    e.rect(x + 2, y + 1, x + w - 2, y + 2, BLANC, 41)


def filet(e, x0, x1, y, a=30):
    """Ui.hairline qui s'efface à droite."""
    e.grad(x0, y, x1, y + 1 / 3, BLANC, a, BLANC, 0)


def haut(y):
    """Haut des capitales d'un texte dessiné à y par drawString (police TTF du mod, décalage de 1 en y)."""
    return y + 2.2


def bouton(e, txt, x, y, w, h, c=OR, look="contour", actif=True):
    """HzButton : contour (défaut), principal (teinte d'accent), pastille."""
    if look == "pastille":
        e.rect(x, y, x + w, y + h, c if actif else BLANC, 26 if actif else 8)
        e.border(x + 1, y + 1, w - 2, h - 2, c if actif else BLANC, 70 if actif else 24)
        col = ab.mix(c, 0xFFFFFF, 0.25) if actif else FAINT
        e.c(txt, x + w / 2, y + h / 2, CAPS_PILL, col, maxw=w - 6, quoi="pastille")
        return
    if not actif:
        e.rect(x, y, x + w, y + h, BLANC, 4)
        e.border(x + 1, y + 1, w - 2, h - 2, BLANC, 20)
        e.c(txt, x + w / 2, y + h / 2, CAPS_BTN, BLANC, 70, maxw=w - 8, quoi="bouton")
        return
    if look == "principal":
        e.rect(x, y, x + w, y + h, c, 34)
        e.border(x + 1, y + 1, w - 2, h - 2, c, 170)
    else:
        e.rect(x, y, x + w, y + h, BLANC, 7)
        e.border(x + 1, y + 1, w - 2, h - 2, BLANC, 46)
    e.c(txt, x + w / 2, y + h / 2, CAPS_BTN, TEXT, maxw=w - 8, quoi="bouton")


def champ(e, valeur, x, y, w, h, focus=True):
    """HzField : fond noir, filet en bas (or quand il a le focus), texte à gauche."""
    e.rect(x, y, x + w, y + h, 0, 150 if focus else 80)
    e.rect(x, y + h - 1 / 3 * 2, x + w, y + h, OR if focus else BLANC, 230 if focus else 34)
    e.texte(valeur, x + 4, y + (h - 8) / 2 + 1.4, FONT, TEXT)
    if focus:
        lw = e.largeur(valeur, FONT)
        e.rect(x + 4 + lw + 1, y + (h - 8) / 2, x + 4 + lw + 4 / 3, y + (h - 8) / 2 + 9, OR, 230)


def ecran_marchand(e, nom, role, solde, articles):
    """Le fond : l'écran du marchand tel qu'il est (titre, rôle, solde, cartes des articles)."""
    verre(e, PX, PY, PW, PH)
    e.texte(nom, PX + 18, haut(PY + 11), TITRE, TEXT, quoi="nom du marchand")
    e.texte(role, PX + 19, haut(PY + 32) - 0.6, CAPS_ROLE, OR, quoi="rôle")
    e.d(nombre(solde) + " " + tr("herals"), PX + PW - 18, haut(PY + 15) + 2.5, BOLD, OR)
    e.d(tr("votre_solde"), PX + PW - 18, haut(PY + 28) + 1.6, (False, 9 / 1.302, 0.6), MUTED)
    filet(e, PX + 18, PX + PW - 18, PY + 44)
    cl = (PW - 36 - (COLS - 1) * 8) // COLS
    for n, (ic, nomo, rar, prix) in enumerate(articles):
        x, y = PX + 18 + (n % COLS) * (cl + 8), PY + 56 + (n // COLS) * (CARTE_H + 8)
        if y + CARTE_H > PY + PH - 16:
            break
        col = RARETES[rar]
        verre(e, x, y, cl, CARTE_H, 110)
        e.rect(x + 6, y + 6, x + 38, y + 38, col, 40)
        e.border(x + 5, y + 5, 34, 34, col, 170)
        im = icone(ic)
        if im is not None:
            poser(e, im, x + 10, y + 10, 24, 24)
        e.texte(coupe(e, nomo, BOLD, cl - 50), x + 44, haut(y + 6), BOLD, TEXT, maxw=cl - 50, quoi="nom d'article")
        e.texte(tr("rarete.%d" % rar), x + 44, haut(y + 17) - 0.4, SMALL, col)
        e.texte(nombre(prix), x + 44, haut(y + CARTE_H - 14), BOLD, OR)
        bx, by = x + cl - 46, y + CARTE_H - 17
        e.rect(bx, by, bx + 40, by + 13, OR, 210)
        e.c(tr("acheter"), bx + 20, by + 6.8, CAPS_ACHAT, 0x0D0F14, maxw=38, quoi="ACHETER")


def panneau(e, ic, nomo, rar, prix, solde, quantite, maxi, raison_max, rapides, trop=False):
    """Le panneau de confirmation, au milieu de l'écran du marchand (assombri)."""
    e.rect(0, 0, GW, GH, NUIT, 160)
    x0, y0 = PX + (PW - CONF_L) // 2, PY + (PH - CONF_H) // 2
    verre(e, x0, y0, CONF_L, CONF_H, 252)
    e.rect(x0 + 2, y0 + 1, x0 + CONF_L - 2, y0 + 2, OR, 200)   # trait d'accent en haut
    e.texte(tr("titre"), x0 + 14, haut(y0 + 11) - 0.6, CAPS, MUTED, maxw=CONF_L - 28, quoi="titre")
    filet(e, x0 + 14, x0 + CONF_L - 14, y0 + 25)
    # l'article : icône teintée de sa rareté, nom (couleur de la rareté), rareté, prix à l'unité
    col = RARETES[rar]
    e.rect(x0 + 14, y0 + 33, x0 + 46, y0 + 65, col, 40)
    e.border(x0 + 13, y0 + 32, 34, 34, col, 170)
    im = icone(ic)
    if im is not None:
        poser(e, im, x0 + 18, y0 + 37, 24, 24)
    e.texte(nomo, x0 + 54, haut(y0 + 33), BOLD, col, maxw=CONF_L - 68, quoi="nom")
    e.texte(tr("rarete.%d" % rar), x0 + 54, haut(y0 + 44) - 0.4, SMALL, col)
    e.texte(tr("unite", nombre(prix)), x0 + 54, haut(y0 + 55) - 0.4, SMALL, MUTED, maxw=CONF_L - 68, quoi="prix à l'unité")
    # la quantité : − [champ] +, puis ×1 ×10 ×64 Max ; dessous, le plafond et sa raison
    e.texte(tr("quantite"), x0 + 14, haut(y0 + 74) - 0.6, CAPS, MUTED, maxw=100, quoi="quantité")
    yq = y0 + 86
    bouton(e, "−", x0 + 14, yq, 18, 16)
    champ(e, str(quantite), x0 + 36, yq, 54, 16)
    bouton(e, "+", x0 + 94, yq, 18, 16)
    px_ = x0 + 120
    for i, (txt, actif) in enumerate(rapides):
        w = 46 if i == 3 else 36
        bouton(e, txt, px_, yq, w, 16, look="pastille", actif=actif)
        px_ += w + 4
    if px_ - 4 > x0 + CONF_L - 14 + 0.01:
        DEFAUTS.append("[%s] pastilles trop larges" % LANGUES[LANGUE])
    ligne = tr(raison_max, nombre(maxi))
    e.texte(ligne, x0 + 14, haut(y0 + 106) - 0.4, SMALL, DANGER if trop else FAINT, maxw=CONF_L - 28, quoi="plafond")
    # le total, le solde avant et après
    filet(e, x0 + 14, x0 + CONF_L - 14, y0 + 120, 50)
    total = prix * quantite
    e.texte(tr("total"), x0 + 14, haut(y0 + 127), BOLD, TEXT)
    e.d(nombre(total) + " " + tr("herals"), x0 + CONF_L - 14, haut(y0 + 127) + 3.1, BOLD, DANGER if trop else OR)
    e.texte(tr("solde"), x0 + 14, haut(y0 + 140) - 0.2, FONT, MUTED)
    e.d(nombre(solde) + " " + tr("herals"), x0 + CONF_L - 14, haut(y0 + 140) + 2.9, FONT, TEXT)
    apres = solde - total
    e.texte(tr("apres"), x0 + 14, haut(y0 + 152) - 0.2, FONT, MUTED)
    e.d(("−" if apres < 0 else "") + nombre(abs(apres)) + " " + tr("herals"), x0 + CONF_L - 14, haut(y0 + 152) + 2.9, FONT,
        DANGER if apres < 0 else OK)
    # Annuler / Confirmer
    bw = (CONF_L - 28 - 16) // 2
    bouton(e, tr("annuler"), x0 + 14, y0 + CONF_H - 28, bw, 18)
    bouton(e, tr("confirmer"), x0 + CONF_L - 14 - bw, y0 + CONF_H - 28, bw, 18, look="principal", actif=not trop)


BATISSEUR = [("brindilles", "Brindilles", 1, 2), ("planches", "Planches", 1, 38), ("pierres_taillees", "Pierres taillées", 2, 75),
             ("fragments_metal", "Fragments de métal", 3, 188), ("metal_renforce", "Métal renforcé", 4, 300)]
PASSEUR = [("glock_17", "Glock 17", 0, 1125), ("m1911", "M1911", 0, 1125), ("cz75", "CZ-75", 1, 2250), ("uzi", "UZI", 1, 3000),
           ("db_short", "DB-2 Durin", 1, 3000), ("ump45", "Pistolet mitrailleur UMP45", 2, 6750), ("m870", "M870", 2, 6750),
           ("sks_tactical", "Fusil Tactique Sks", 2, 7500), ("9mm", "Balle de 9mm", 0, 8), ("45acp", "Balle de .45 ACP", 0, 8),
           ("12g", "Balle de calibre 12", 1, 11), ("762x39", "Balle de 7.62x39mm", 2, 15)]
ROLES = {"batisseur": ("Le Bâtisseur", ("MATÉRIAUX DE CONSTRUCTION", "BUILDING MATERIALS", "СТРОЙМАТЕРИАЛЫ")),
         "passeur": ("Le Passeur", ("ARMES ET MUNITIONS", "WEAPONS AND AMMO", "ОРУЖИЕ И ПАТРОНЫ"))}


def fin(e, sortie):
    DEFAUTS.extend(e.defauts)
    e.defauts = []
    if sortie is not None:
        e.sauver(sortie)


def ecran(sortie, pnj, articles, solde, choix, quantite, maxi, raison, rapides, trop=False):
    e = ab.Ecran(ab.fond())
    nom, roles = ROLES[pnj]
    ecran_marchand(e, nom, roles[LANGUE], solde, articles)
    ic, nomo, rar, prix = articles[choix]
    panneau(e, ic, nomo, rar, prix, solde, quantite, maxi, raison, rapides, trop)
    fin(e, sortie)


def longueurs():
    """Les lignes du plafond qui ne sont dans aucune image : même place que dans le panneau (largeur CONF_L - 28)."""
    e = ab.Ecran(ab.fond())
    for cle in ("limite_herals", "limite_place", "trop_herals", "trop_place", "pas_assez", "plein"):
        e.texte(tr(cle, nombre(2304)), 0, 0, SMALL, TEXT, maxw=CONF_L - 28, quoi=cle)
    e.texte(tr("unite", nombre(10000)), 0, 0, SMALL, TEXT, maxw=CONF_L - 68, quoi="unite")
    fin(e, None)


def tous(dossier):
    # 1 : 500 brindilles chez le Bâtisseur (2 hérals l'unité au prix bêta, 15 400 hérals, 20 cases libres : 1 280 au plus)
    ecran(dossier and dossier / "1_brindilles_500.png", "batisseur", BATISSEUR, 15400, 0, 500, 1280, "limite_place",
          [("×1", True), ("×10", True), ("×64", True), (tr("max"), True)])
    # 2 : une arme Rare chez le Passeur (6 750 au prix bêta, 8 200 hérals : une seule possible)
    ecran(dossier and dossier / "2_passeur_arme.png", "passeur", PASSEUR, 8200, 5, 1, 1, "limite_herals",
          [("×1", True), ("×5", False), ("×10", False), (tr("max"), True)])
    # 3 : 120 balles de 7.62 tapées, 8 200 hérals : 546 au plus... ici 1 200 balles, trop : Confirmer éteint
    ecran(dossier and dossier / "3_trop.png", "passeur", PASSEUR, 8200, 11, 1200, 546, "trop_herals",
          [("×1", True), ("×10", True), ("×60", True), (tr("max"), True)], trop=True)


def main():
    global LANGUE
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "achat_quantite"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    for i, code in enumerate(LANGUES):
        LANGUE = i
        ab.LANGUE = code
        tous(dossier if i == 0 else None)   # images en français ; anglais et russe : contrôle des longueurs seulement
        longueurs()
    if DEFAUTS:
        print("\n".join(DEFAUTS))
        sys.exit(1)
    print("aucun texte ne dépasse (fr, en, ru) ->", dossier)


if __name__ == "__main__":
    main()
