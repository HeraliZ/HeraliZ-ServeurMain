#!/usr/bin/env python3
"""Aperçus de l'atelier de réparation (clic droit avec un kit en main, `EcranReparation`) : écran d'avant le 11/11 et
écran proposé le 11/11 (demande du propriétaire : en rouge, la part de l'état qui ne se répare plus ; réparations
restantes de l'arme), dans les trois langues.

    python modules/monde/outils/apercu_atelier.py DOSSIER

Même moteur que les autres aperçus (modules/boutique/outils/apercu_boutique.py) : canevas 640 x 360 unités, rendu à
l'échelle 3 (1920 x 1080, l'échelle que l'écran force), polices DM Sans du jeu à leur vraie taille (taille du .json de
heraliz_usure / 1,302). Mêmes mesures que le code (panneau 228 x 252 centré, cases de 20, aperçu à droite de la case,
boutons 74 x 14). Un texte qui dépasse sa place, dans n'importe quelle langue, arrête le script (code 1). Textes : fichiers
de langue de heraliz_usure (ceux d'avant le 11/11 en dur ci-dessous pour l'écran d'avant). Chaque image a sa version
agrandie autour de l'aperçu (« _zoom ») et les comparaisons « avant | après » sont côte à côte.
"""
import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "boutique" / "outils"))
import apercu_boutique as ab  # noqa: E402

ab.FONTS = RACINE / "modules/noyau/src/client/resources/assets/heraliz_noyau/font"
GW, GH, K = ab.GW, ab.GH, ab.K
LANG = RACINE / "modules/monde/src/client/resources/assets/heraliz_usure/lang"

TEXT, MUTED, FAINT, DANGER, OK, OR, AMBRE, BLANC, VERRE = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0xFF6B6B, 0x5FD08A, 0xE8C36A, 0xE8B34A, 0xFFFFFF, 0x0D0F14
RARETES = {"commun": 0x9A9A9A, "peu_commun": 0x5CBF4A, "rare": 0x3F8CFF, "epique": 0xA64DFF, "legendaire": 0xF2B83A}
# polices de heraliz_usure (gras, taille de l'em = taille du .json / 1,302, espacement des capitales)
TITRE = (True, 14 / 1.302, 0)
BOLD = (True, 11 / 1.302, 0)
SMALL = (False, 9 / 1.302, 0)
PG = (True, 9 / 1.302, 0)
CAPS_PG = (True, 9 / 1.302, 1.0)
MICRO_CAPS = (True, 7.5 / 1.302, 0.6)

# EcranReparation / MenuReparation
L, H, ENTETE, M, CASE = 228, 252, 44, 16, 20
CIBLE_X, CIBLE_Y, INV_X, INV_Y = 18, 66, 16, 148
AX = CIBLE_X + CASE + 10
BY, BH, BW = 116, 14, 74
X0, Y0 = (GW - L) // 2, (GH - H) // 2
POINT = 1 / 3

TACZ = RACINE / "serveur/tacz/heraliz_1_base_tacz/assets/tacz/textures/gun/slot"
USURE = RACINE / "modules/monde/src/client/resources/assets/heraliz_usure/textures/item"
ARMURERIE = RACINE / "modules/equipement/src/client/resources/assets/heraliz_armory/textures/item"
ICONES = {"ak47": TACZ / "ak47.png", "m1911": TACZ / "m1911.png", "glock_17": TACZ / "glock_17.png", "m4a1": TACZ / "m4a1.png",
          "kit_fortune": USURE / "kit_fortune.png", "kit_standard": USURE / "kit_standard.png", "gilet": ARMURERIE / "cobra_vest.png"}
ARMES = ("ak47", "m1911", "glock_17", "m4a1")
NOMS = {"fr_fr": ("AK-47", "Kit de réparation de fortune", "Gilet Cobra"),
        "en_us": ("AK-47", "Makeshift repair kit", "Cobra vest"),
        "ru_ru": ("АК-47", "Самодельный ремкомплект", "Жилет «Кобра»")}
# textes de l'atelier avant le 11/11 (écran d'avant)
ANCIENS = {
    "fr_fr": {"max": "État maximal : %s → %s", "max_stable": "État maximal : %s",
              "code.7": "Plus aucune réparation : cette arme a atteint son maximum de réparations."},
    "en_us": {"max": "Maximum condition: %s → %s", "max_stable": "Maximum condition: %s",
              "code.7": "No repairs left: this weapon has reached its maximum number of repairs."},
    "ru_ru": {"max": "Макс. состояние: %s → %s", "max_stable": "Макс. состояние: %s",
              "code.7": "Ремонт больше невозможен: лимит ремонтов этого оружия исчерпан."},
}
TXT = {}
DEFAUTS = []
_cache = {}


def t(cle, *args, ancien=False):
    if ancien and cle in ANCIENS[ab.LANGUE]:
        s = ANCIENS[ab.LANGUE][cle]
    else:
        s = TXT.get("gui.heraliz_usure.atelier." + cle, TXT.get(cle, cle))
    if args:
        it = iter(args)
        s = re.sub(r"%(\d+\$)?s", lambda m: str(next(it)), s)
    return s.replace("%%", "%")


def pct(milliemes):
    v = round(milliemes / 10)
    if v == 0 and milliemes > 0:
        v = 1
    if v == 100 and milliemes < 1000:
        v = 99
    return "%d %%" % v


def couper(e, s, p, maxw):
    """Net.couper : coupé à la largeur avec « … »."""
    if e.largeur(s, p) <= maxw:
        return s
    n = len(s)
    while n > 0 and e.largeur(s[:n].rstrip() + "…", p) > maxw:
        n -= 1
    return s[:n].rstrip() + "…"


def icone(e, nom, x, y, s=16):
    if nom not in _cache:
        im = Image.open(ICONES[nom]).convert("RGBA")
        bbox = im.getbbox()
        if bbox and nom in ARMES:
            im = im.crop(bbox)
            c = max(im.width, im.height)
            carre = Image.new("RGBA", (c, c), (0, 0, 0, 0))
            carre.paste(im, ((c - im.width) // 2, (c - im.height) // 2))
            im = carre
        _cache[nom] = im
    im = _cache[nom]
    taille = (int(round(s * K)), int(round(s * K)))
    im = im.resize(taille, Image.NEAREST if im.width <= 32 else Image.LANCZOS)
    e.im.alpha_composite(im, (int(round(x * K)), int(round(y * K))))


def fond_rarete(e, x, y, rarete):
    """RaretesClient.dessinerFond : léger dégradé de la couleur de la rareté et trait en bas de la case (16 x 16)."""
    c = RARETES[rarete]
    e.grad(x, y, x + 16, y + 9, c, 0, c, 18, vertical=True)
    e.grad(x, y + 9, x + 16, y + 16, c, 18, c, 56, vertical=True)
    e.rect(x, y + 15, x + 16, y + 16, c, 191)


def verre(e, x, y, w, h, a):
    """Ui.glass : fond 0x0D0F14, contour blanc 10 %, liseré haut blanc 16 %."""
    e.rect(x + POINT, y, x + w - POINT, y + h, VERRE, a)
    e.rect(x, y + POINT, x + POINT, y + h - POINT, VERRE, a)
    e.rect(x + w - POINT, y + POINT, x + w, y + h - POINT, VERRE, a)
    e.border(x + POINT, y + POINT, w - 2 * POINT, h - 2 * POINT, BLANC, 26)
    e.rect(x + 2 / 3, y + POINT, x + w - 2 / 3, y + 2 / 3, BLANC, 41)


def voile_ui(e):
    """Ui.fond : dégradé sombre plein écran (le monde reste lisible)."""
    for i in range(24):
        x0, x1 = GW * i / 24, GW * (i + 1) / 24
        tt = (i + 0.5) / 24
        haut = 0x8C + (0xB4 - 0x8C) * tt * 0.5
        bas = 0x8C + (0xB4 - 0x8C) * (0.5 + tt * 0.5)
        e.grad(x0, 0, x1, GH, 0, haut, 0, bas, vertical=True)


def libelle(e, txt, x, y, largeur):
    w = e.m(txt, x, y + 2.42, CAPS_PG, MUTED, maxw=largeur - 10, quoi="libellé")
    e.rect(x + w + 6, y + 2.6, x + largeur, y + 2.6 + POINT, BLANC, 30)


def entete(e, kit_nom, restantes, total):
    x, y = X0, Y0
    cx, cy = x + M + 12, y + 10 + 12
    e.disc(cx, cy, 11, OK, 40)
    e.disc(cx, cy, 11, OK, 200, anneau=1.1)
    e.trait([(cx - 5, cy + 5), (cx + 3, cy - 3)], 2.4, OK, 230)
    e.disc(cx + 4.2, cy - 4.2, 3.2, OK, 230, anneau=1.6)
    e.disc(cx - 5, cy + 5, 1.6, OK, 230)
    tx = x + M + 34
    e.m(t("titre"), tx, y + 9 + 14 * 0.53763 / 2, TITRE, TEXT, maxw=L - M - 34 - 28, quoi="titre")
    sous = kit_nom + " · " + t("utilisations", restantes, total)
    e.m(couper(e, sous, SMALL, L - M - 34 - 28), tx, y + 27 + 9 * 0.53763 / 2, SMALL, MUTED)
    e.c("×", x + L - 16, y + 14, BOLD, MUTED)
    e.rect(x + 8, y + ENTETE, x + L - 8, y + ENTETE + POINT, BLANC, 40)


def cases(e, objet, rarete_objet):
    x, y = X0, Y0
    # case de l'objet à réparer
    e.rect(x + CIBLE_X, y + CIBLE_Y, x + CIBLE_X + CASE, y + CIBLE_Y + CASE, 0x000000, 90)
    e.border(x + CIBLE_X, y + CIBLE_Y, CASE, CASE, BLANC, 70)
    if objet:
        fond_rarete(e, x + CIBLE_X + 2, y + CIBLE_Y + 2, rarete_objet)
        icone(e, objet, x + CIBLE_X + 2, y + CIBLE_Y + 2)
    inv = {27: ("kit_fortune", "rare"), 28: ("m1911", "commun"), 29: ("kit_standard", "epique"), 2: ("glock_17", "commun"),
           5: ("m4a1", "rare"), 10: ("gilet", "peu_commun")}
    for i in range(36):
        cx = x + INV_X + (i % 9) * (CASE + 2)
        cy = y + INV_Y + (i // 9) * (CASE + 2) + (4 if i >= 27 else 0)
        kit = i == 27
        e.rect(cx, cy, cx + CASE, cy + CASE, 0x000000, 150 if kit else 90)
        e.border(cx, cy, CASE, CASE, OK if kit else BLANC, 150 if kit else 34)
        if i in inv:
            nom, r = inv[i]
            fond_rarete(e, cx + 2, cy + 2, r)
            icone(e, nom, cx + 2, cy + 2)


def lignes(e, txt, x, cy, w, c, n):
    for i, l in enumerate(e.lignes(txt, SMALL, w)[:n]):
        e.m(l, x, cy + i * 10, SMALL, c, maxw=w, quoi="message")


def bouton(e, txt, bx, by, c, actif, survol=False):
    e.rect(bx, by, bx + BW, by + BH, c, 14 if not actif else 90 if survol else 50)
    e.border(bx, by, BW, BH, c, 210 if actif else 70)
    e.c(txt, bx + BW / 2, by + BH / 2, CAPS_PG, TEXT if actif else FAINT, maxw=BW - 6, quoi="bouton")


def apercu_avant(e, cas):
    """EcranReparation.apercu d'avant le 11/11 (aucune ligne de réparations restantes, trait rouge du max seulement)."""
    x, y = X0, Y0
    libelle(e, t("objet"), x + M, y + ENTETE + 10, L - 2 * M)
    ax, aw = x + AX, L - M - AX
    e.m(couper(e, cas["nom"], BOLD, aw), ax, y + 70, BOLD, TEXT)
    code = cas["code"]
    if code != "ok":
        lignes(e, t(cas["code_cle"], ancien=True), ax, y + 84, aw, MUTED if code == "plafond" else AMBRE, 3)
        return
    avant, apres = cas["avant"], cas["apres"]
    cy = y + 85
    w1 = e.m(pct(avant), ax, cy, PG, MUTED)
    w2 = e.m("→", ax + w1 + 5, cy, SMALL, FAINT)
    w3 = e.m(pct(apres), ax + w1 + 5 + w2 + 5, cy, PG, OK)
    gain = "+%d %%" % max(0, round((apres - avant) / 10))
    gx, gw = ax + w1 + w2 + w3 + 18, e.largeur(gain, MICRO_CAPS) + 8
    if gx + gw <= ax + aw:
        e.rect(gx, cy - 5, gx + gw, cy + 5, OK, 40)
        e.border(gx, cy - 5, gw, 10, OK, 170)
        e.c(gain, gx + gw / 2, cy, MICRO_CAPS, OK)
    by = y + 94
    e.rect(ax, by, ax + aw, by + 3, BLANC, 22)
    e.rect(ax, by, ax + aw * avant / 1000, by + 3, BLANC, 110)
    e.rect(ax + aw * avant / 1000, by, ax + aw * apres / 1000, by + 3, OK, 235)
    if cas["arme"]:
        mx = ax + aw * cas["max_apres"] / 1000
        e.rect(mx - POINT, by - 2, mx, by + 5, DANGER, 200)
        l = t("max", pct(cas["max_avant"]), pct(cas["max_apres"]), ancien=True) if cas["max_apres"] < cas["max_avant"] \
            else t("max_stable", pct(cas["max_apres"]), ancien=True)
        e.m(couper(e, l, SMALL, aw), ax, y + 106, SMALL, FAINT)
    else:
        e.m(couper(e, t("armure"), SMALL, aw), ax, y + 106, SMALL, FAINT)


def mix(a, b, tt):
    """Ui.mix (troncature des entiers comme en Java)."""
    return sum(int(((a >> s) & 255) * (1 - tt) + ((b >> s) & 255) * tt) << s for s in (16, 8, 0))


ROUGE_DOUX = mix(DANGER, MUTED, 0.25)  # ligne « en rouge » : EcranReparation.ROUGE_DOUX


def apercu_apres(e, cas):
    """EcranReparation.apercu proposé le 11/11 (même code que le jeu, À VALIDER)."""
    x, y = X0, Y0
    libelle(e, t("objet"), x + M, y + ENTETE + 10, L - 2 * M)
    ax, aw = x + AX, L - M - AX
    e.m(couper(e, cas["nom"], BOLD, aw), ax, y + 70, BOLD, TEXT)
    code, arme = cas["code"], cas["arme"]
    barre = code == "ok" or arme and code in ("plafond", "plus")
    if not barre:
        lignes(e, t(cas["code_cle"]), ax, y + 84, aw, MUTED if code == "plafond" else AMBRE, 3)
        return
    avant, apres = cas["avant"], cas["apres"]
    max_avant, max_apres = (cas["max_avant"], cas["max_apres"]) if arme else (1000, 1000)
    cy = y + 82
    if code == "ok":
        w1 = e.m(pct(avant), ax, cy, PG, MUTED)
        w2 = e.m("→", ax + w1 + 5, cy, SMALL, FAINT)
        w3 = e.m(pct(apres), ax + w1 + 5 + w2 + 5, cy, PG, OK)
        gain = "+%d %%" % max(0, round((apres - avant) / 10))
        gx, gw = ax + w1 + w2 + w3 + 18, e.largeur(gain, MICRO_CAPS) + 8
        if gx + gw <= ax + aw:
            e.rect(gx, cy - 5, gx + gw, cy + 5, OK, 40)
            e.border(gx, cy - 5, gw, 10, OK, 170)
            e.c(gain, gx + gw / 2, cy, MICRO_CAPS, OK)
    else:
        e.m(t(cas["code_cle"]), ax, cy, SMALL, MUTED if code == "plafond" else DANGER, maxw=aw, quoi="refus")
    by = y + 89
    e.rect(ax, by, ax + aw, by + 3, BLANC, 22)
    e.rect(ax, by, ax + aw * avant / 1000, by + 3, BLANC, 110)
    if apres > avant:
        e.rect(ax + aw * avant / 1000, by, ax + aw * apres / 1000, by + 3, OK, 235)
    if max_apres < max_avant:
        e.rect(ax + aw * max_apres / 1000, by, ax + aw * max_avant / 1000, by + 3, DANGER, 235)
    if max_avant < 1000:
        e.rect(ax + aw * max_avant / 1000, by, ax + aw, by + 3, DANGER, 120)
    if not arme:
        e.m(t("armure"), ax, y + 99, SMALL, FAINT, maxw=aw, quoi="armure")
        return
    if max_apres < 1000:
        l = t("max", pct(max_avant), pct(max_apres)) if max_apres < max_avant else t("max_stable", pct(max_apres))
        e.m(l, ax, y + 99, SMALL, ROUGE_DOUX, maxw=aw, quoi="ligne rouge")
    rest, tot = cas["restantes"], cas["maximum"]
    l = t("reparations", rest, rest - 1, tot) if code == "ok" else t("reparations_stable", rest, tot)
    c = DANGER if rest <= 0 else AMBRE if code == "ok" and rest <= 1 else MUTED
    e.m(l, ax, y + 108, SMALL, c, maxw=aw, quoi="réparations")


def ecran(sortie, cas, mode, kit=("kit_fortune", 1, 1)):
    e = ab.Ecran(ab.fond())
    voile_ui(e)
    verre(e, X0, Y0, L, H, 214)
    entete(e, NOMS[ab.LANGUE][1], kit[1], kit[2])
    cases(e, cas["icone"], cas["rarete"])
    (apercu_avant if mode == "avant" else apercu_apres)(e, cas)
    rx = X0 + L - M - BW
    bouton(e, t("annuler"), rx - 6 - BW, Y0 + BY, MUTED, True)
    bouton(e, t("reparer"), rx, Y0 + BY, OK, cas["code"] == "ok", survol=cas["code"] == "ok")
    libelle(e, t("inventaire"), X0 + M, Y0 + INV_Y - 10, 9 * (CASE + 2) - 2)
    DEFAUTS.extend(e.defauts)
    e.defauts = []
    e.sauver(sortie)
    # zoom x2 autour du panneau (pixels de l'image à l'échelle 3)
    im = Image.open(sortie)
    boite = ((X0 - 8) * 3, (Y0 - 8) * 3, (X0 + L + 8) * 3, (Y0 + H + 8) * 3)
    z = im.crop(boite)
    z = z.resize((z.width * 2, z.height * 2), Image.NEAREST)
    z.save(sortie.with_name(sortie.stem + "_zoom.png"), optimize=True)
    return sortie


def cote_a_cote(a, b, sortie, titres):
    ia, ib = Image.open(a), Image.open(b)
    marge, haut = 30, 70
    out = Image.new("RGB", (ia.width + ib.width + marge * 3, max(ia.height, ib.height) + haut + marge), (24, 26, 30))
    out.paste(ia, (marge, haut))
    out.paste(ib, (ia.width + marge * 2, haut))
    d = ImageDraw.Draw(out)
    f = ImageFont.truetype(str(ab.FONTS / "dmsans_bold.ttf"), 34)
    d.text((marge, 18), titres[0], font=f, fill=(236, 238, 242))
    d.text((ia.width + marge * 2, 18), titres[1], font=f, fill=(236, 238, 242))
    out.save(sortie, optimize=True)


CAS = {
    # AK-47 (Rare, 8 réparations) déjà réparée 3 fois : état 30 %, max 88 % ; kit de fortune (bleu)
    "arme_avant": {"icone": "ak47", "rarete": "rare", "arme": True, "code": "ok", "avant": 300, "apres": 370,
                   "max_avant": 880, "max_apres": 840},
    "arme_apres": {"icone": "ak47", "rarete": "rare", "arme": True, "code": "ok", "avant": 300, "apres": 550,
                   "max_avant": 880, "max_apres": 840, "restantes": 5, "maximum": 8},
    # même arme à son maximum (84 %) : déjà au mieux
    "au_max": {"icone": "ak47", "rarete": "rare", "arme": True, "code": "plafond", "code_cle": "code.2", "avant": 840,
               "apres": 840, "max_avant": 840, "max_apres": 840, "restantes": 4, "maximum": 8},
    # plus aucune réparation : réparée 8 fois sur 8, état 12 %, max 68 %
    "plus": {"icone": "ak47", "rarete": "rare", "arme": True, "code": "plus", "code_cle": "code.7", "avant": 120, "apres": 120,
             "max_avant": 680, "max_apres": 680, "restantes": 0, "maximum": 8},
    # pièce d'armure à 40 % (pas de limite, pas d'état maximal)
    "armure": {"icone": "gilet", "rarete": "peu_commun", "arme": False, "code": "ok", "avant": 400, "apres": 650},
}


def main():
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    dossier = Path(sys.argv[1])
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    for langue in ("fr_fr", "en_us", "ru_ru"):
        ab.LANGUE = langue
        TXT.clear()
        TXT.update(json.loads((LANG / (langue + ".json")).read_text(encoding="utf-8")))
        nom_arme, _, nom_armure = NOMS[langue]
        for c in CAS.values():
            c["nom"] = nom_armure if c["icone"] == "gilet" else nom_arme
        suf = "" if langue == "fr_fr" else "_" + langue[:2]
        a = ecran(dossier / ("avant_arme%s.png" % suf), CAS["arme_avant"], "avant")
        b = ecran(dossier / ("apres_arme%s.png" % suf), CAS["arme_apres"], "apres")
        pa = ecran(dossier / ("avant_plus_de_reparation%s.png" % suf), dict(CAS["plus"], code="plus"), "avant")
        pb = ecran(dossier / ("apres_plus_de_reparation%s.png" % suf), CAS["plus"], "apres")
        ecran(dossier / ("apres_au_maximum%s.png" % suf), CAS["au_max"], "apres")
        ecran(dossier / ("apres_armure%s.png" % suf), CAS["armure"], "apres")
        if langue == "fr_fr":
            cote_a_cote(a.with_name(a.stem + "_zoom.png"), b.with_name(b.stem + "_zoom.png"), dossier / "comparaison_arme.png",
                        ("AVANT (aujourd'hui) : kit de fortune +7 %", "APRÈS : rouge = ne se répare plus, réparations restantes"))
            cote_a_cote(pa.with_name(pa.stem + "_zoom.png"), pb.with_name(pb.stem + "_zoom.png"),
                        dossier / "comparaison_plus_de_reparation.png",
                        ("AVANT (règles du 11/11, écran actuel)", "APRÈS : barre, rouge et 0 sur 8"))
    if DEFAUTS:
        print("\n".join(DEFAUTS))
        sys.exit(1)
    print("aucun texte ne dépasse ->", dossier)


if __name__ == "__main__":
    main()
