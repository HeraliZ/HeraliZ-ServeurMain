"""Aperçu hors du jeu de l'écran du distributeur de capsules (« ATM », client/screen/AtmScreen.java), dans les trois langues,
et contrôle « aucun texte ne dépasse » (demande du propriétaire, 27/10 ; maquette validée le 27/10).

    python modules/boutique/outils/maquette_atm.py [DOSSIER]      (par défaut : maquettes/atm_capsules/ecran)

Même moteur que les aperçus de la boutique (apercu_boutique.py : canevas 640 x 360 unités, verre, polices DM Sans à leur
vraie taille, Onest pour le cyrillique), enregistré en 1920 x 1080. Les textes sont ceux du jeu (lang/*.json du casino) ;
les mesures sont celles de AtmScreen.java : changer l'un, c'est changer l'autre. Refuse (code de sortie 1) tout texte qui
dépasse sa place, en français, anglais et russe, avec de grands nombres.
  1_compte[_en|_ru].png         : l'écran du compte (solde, capsules sur soi, déposer, retirer, derniers mouvements)
  2_retrait_refuse[_en|_ru].png : même écran, retrait refusé faute de place dans l'inventaire
Les images 1_compte.png et 2_retrait_refuse.png de maquettes/atm_capsules sont la maquette validée (ne pas les refaire).
"""
import json
import re
import sys
from pathlib import Path

from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import apercu_boutique as ab  # noqa: E402

RACINE = Path(__file__).resolve().parents[3]
FOND_JEU = RACINE / "modules/interface/src/main/resources/assets/heraliz_interface/textures/gui/fond_titre.png"
ASSETS = RACINE / "modules/boutique/src/client/resources/assets"
JETONS = ASSETS / "heralizcasino/textures/item"
GW, GH, M, GAUCHE = ab.GW, ab.GH, 16, 290
WHITE, LIGHT, MUTED, FAINT, GOLD, OK, DANGER = ab.WHITE, ab.LIGHT, ab.MUTED, ab.FAINT, ab.GOLD, ab.OK, ab.DANGER
TITRE, BOUTON, MINI, MINI_NUM = ab.TITRE, ab.BOUTON, ab.MINI, ab.MINI_NUM
TEXTE, TEXTE_GRAS, PETIT, PETIT_GRAS = ab.TEXTE, ab.TEXTE_GRAS, ab.PETIT, ab.PETIT_GRAS
CHIFFRE_GRAND = ab.CHIFFRE_GRAND
CAPSULE = 0xE0503C  # rouge de la capsule de 10 (Chips.COLORS)
TXT = {}


def t(cle, *args):
    s = TXT.get("screen.heralizcasino.atm." + cle, TXT.get(cle, cle))
    if args:
        s = re.sub(r"%(\d+\$)?s", lambda m, it=iter(args): str(next(it)), s)
    return s.replace("%%", "%")


def nombre(v):
    """Comme Chips.format : 12500 -> « 12 500 », dans toutes les langues."""
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
    e.texte(t("titre"), M, 18, TITRE, LIGHT, maxw=GW - 2 * M, quoi="titre")
    e.texte(t("sous_titre"), M, 35, TEXTE, MUTED, maxw=GW - 2 * M, quoi="sous-titre")
    e.rect(M, 50, GW - M, 50 + 1 / 3, WHITE, 23)


def pied(e):
    cy, x = 348, M
    for k, lab in (("echap", "fermer"), ("entree", "valider")):
        kl = t("touche." + k).upper()
        w = max(9, e.largeur(kl, MINI_NUM) + 4.67)
        e.rect(x, cy - 4.5, x + w, cy + 4.5, WHITE, 30)
        e.border(x, cy - 4.5, w, 9, WHITE, 61)
        e.c(kl, x + w / 2, cy, MINI_NUM, WHITE)
        x += w + 3.5
        x += e.m(t("pied." + lab), x, cy, PETIT, MUTED) + 10
    e.d(t("attente"), GW - M, cy, PETIT, FAINT, maxw=GW - M - x - 8, quoi="attente")


def bouton(e, x, y, w, h, texte, actif=True, c=ab.ACCENT):
    ab.bouton(e, x, y, w, h, actif, c)
    e.c(texte, x + w / 2, y + h / 2, BOUTON, WHITE if actif else FAINT, maxw=w - 6, quoi="bouton")


def champ(e, x, y, w, h, saisi=""):
    e.rect(x, y, x + w, y + h, WHITE, 12)
    e.border(x, y, w, h, WHITE, 60 if saisi else 40)
    if saisi:
        tw = e.m(saisi, x + 6, y + h / 2, TEXTE, LIGHT, maxw=w - 10, quoi="champ")
        e.rect(x + 6 + tw + 1, y + 3.5, x + 6 + tw + 1.5, y + h - 3.5, WHITE, 200)
    else:
        e.m(t("autre_nombre"), x + 6, y + h / 2, PETIT, FAINT, maxw=w - 10, quoi="champ vide")


def carte_solde(e, x, y, w, h, solde, sur_toi):
    ab.verre(e, x, y, w, h)
    ab.filet_haut(e, x, y, w, GOLD)
    e.glow(x + 34, y + 44, 34, 30, CAPSULE, 70)
    e.image(jeton(10), x + 14, y + 24, 21, 21)
    e.texte(t("sur_ton_compte"), x + 50, y + 22, MINI, ab.mix(GOLD, WHITE, 0.35), maxw=w - 60, quoi="sur ton compte")
    wu = e.largeur(t("capsules"), TEXTE_GRAS) + 4
    w1 = e.texte(nombre(solde), x + 50, y + 33, CHIFFRE_GRAND, WHITE, maxw=w - 60 - wu, quoi="solde")
    e.texte(t("capsules"), x + 50 + w1 + 4, y + 33 + e.hcap(CHIFFRE_GRAND) - e.hcap(TEXTE_GRAS), TEXTE_GRAS, GOLD)
    e.rect(x + 10, y + 62, x + w - 10, y + 62 + 1 / 3, WHITE, 23)
    e.texte(t("sur_toi"), x + 10, y + 70, MINI, MUTED)
    total = sum(v * n for v, n in sur_toi)
    e.d(t("n_capsules", nombre(total)), x + w - 10, y + 70 + e.hcap(MINI) / 2, PETIT_GRAS, LIGHT, maxw=w - 80, quoi="sur toi")
    cx = x + 10
    for v, n in sur_toi:
        e.image(jeton(v), cx, y + 82, 14, 14)
        cx += 16
        cx += e.m("× %s" % nombre(n), cx, y + 89, TEXTE_GRAS, LIGHT) + 14
    if cx - 14 > x + w - 10:
        e.defauts.append("[%s] capsules sur soi : %.1f > %.1f" % (ab.LANGUE, cx - 14, x + w - 10))
    e.texte(t("note_capsules"), x + 10, y + h - 14, PETIT, FAINT, maxw=w - 20, quoi="note des capsules")


def carte_operations(e, x, y, w, h, porte, retrait_saisi, refus):
    ab.verre(e, x, y, w, h)
    e.texte(t("deposer"), x + 10, y + 10, MINI, OK)
    bouton(e, x + 10, y + 20, w - 20, 18, t("tout_deposer", nombre(porte)).upper(), c=OK)
    bw = (w - 20 - 2 * 4) / 3
    for i, v in enumerate((100, 500, 1000)):
        bouton(e, x + 10 + i * (bw + 4), y + 42, bw, 15, nombre(v), c=OK)
    champ(e, x + 10, y + 61, w - 20 - 40, 15)
    bouton(e, x + w - 10 - 36, y + 61, 36, 15, t("ok"), actif=False, c=OK)
    e.rect(x + 10, y + 86, x + w - 10, y + 86 + 1 / 3, WHITE, 23)
    e.texte(t("retirer"), x + 10, y + 94, MINI, GOLD)
    for i, v in enumerate((100, 1000, 5000)):
        bouton(e, x + 10 + i * (bw + 4), y + 104, bw, 15, nombre(v), c=GOLD)
    champ(e, x + 10, y + 123, w - 20 - 40, 15, retrait_saisi)
    bouton(e, x + w - 10 - 36, y + 123, 36, 15, t("ok"), actif=bool(retrait_saisi), c=GOLD)
    if refus:
        # une ligne, deux au plus (grands nombres) : la case grandit vers le bas de la carte
        lignes = e.lignes(refus, PETIT_GRAS, w - 32)[:2]
        bh = 22.5 if len(lignes) > 1 else 14
        e.rect(x + 10, y + 144, x + w - 10, y + 144 + bh, DANGER, 30)
        e.border(x + 10, y + 144, w - 20, bh, DANGER, 120)
        for k, l in enumerate(lignes):
            e.m(l, x + 16, y + 151 + k * 8.5, PETIT_GRAS, ab.mix(DANGER, WHITE, 0.3), maxw=w - 32, quoi="résultat")
    else:
        lignes = e.lignes(t("note_retrait"), PETIT, w - 20)
        if len(lignes) > 2:
            e.defauts.append("[%s] note du retrait : %d lignes (2 au plus)" % (ab.LANGUE, len(lignes)))
        for k, l in enumerate(lignes[:2]):
            e.texte(l, x + 10, y + 145 + k * 8.5, PETIT, FAINT, maxw=w - 20, quoi="note retrait")


def libelle(genre, detail=""):
    if genre == "caisse":
        return t("genre.caisse", detail) if detail else t("genre.caisse_seul")
    if genre == "rendu":
        return t("genre.rendu", detail) if detail else t("genre.rendu_seul")
    return t("genre." + genre)


def quand(s):
    if s < 60:
        return t("quand.instant")
    if s < 3600:
        return t("quand.minutes", s // 60)
    if s < 86400:
        return t("quand.heures", s // 3600)
    if s < 2 * 86400:
        return t("quand.hier")
    return t("quand.jours", s // 86400)


def carte_mouvements(e, x, y, w, h, cle):
    ab.verre(e, x, y, w, h)
    e.texte(t("mouvements"), x + 10, y + 10, MINI, MUTED, maxw=w - 20, quoi="mouvements")
    # les plus longs possibles : grands nombres, libellés longs, « il y a 59 min »
    lignes = [(1_250_000, "depot", "", 59 * 60), (-25, "entree", "", 23 * 3600), (-80_000, "caisse", t("item.heralizcrates." + cle), 86400 + 5),
              (8_400, "rendu", "Rescapé (1 jour)", 3 * 86400), (-500, "retrait", "", 14 * 86400), (-12_000, "staff", "", 30),
              (99_999, "autre", "", 300)]
    ly = y + 24
    for n, genre, detail, age in lignes:
        c = OK if n > 0 else GOLD if genre == "retrait" else DANGER
        e.rect(x + 8, ly - 1, x + w - 8, ly + 13, WHITE, 6)
        e.m(("+" if n > 0 else "−") + nombre(abs(n)), x + 14, ly + 6, TEXTE_GRAS, c, maxw=54, quoi="montant")
        e.m(libelle(genre, detail), x + 70, ly + 6, TEXTE, LIGHT, maxw=w - 70 - 70, quoi="mouvement")
        e.d(quand(age), x + w - 14, ly + 6, PETIT, FAINT, maxw=60, quoi="quand")
        ly += 16
    for g in ("wipe",):
        if e.largeur(libelle(g), TEXTE) > w - 140:
            e.defauts.append("[%s] mouvement : « %s »" % (ab.LANGUE, libelle(g)))


def carte_caisse(e, x, y, w, h, paie=True):
    ab.verre(e, x, y, w, h)
    e.texte(t("caisse_titre"), x + 10, y + 10, MINI, MUTED, maxw=w - 20, quoi="caisse")
    e.coche(x + 10, y + 22, 6, 1.2, OK)
    e.texte(t("caisse_oui" if paie else "caisse_non"), x + 21, y + 23, TEXTE_GRAS, LIGHT, maxw=w - 31, quoi="caisse")
    if e.largeur(t("caisse_non"), TEXTE_GRAS) > w - 31 + 0.05:
        e.defauts.append("[%s] caisse : « %s »" % (ab.LANGUE, t("caisse_non")))
    ly = y + 38
    lignes = e.lignes(t("caisse_explication"), PETIT, w - 20)
    if len(lignes) > 4:
        e.defauts.append("[%s] explication de la caisse : %d lignes (4 au plus)" % (ab.LANGUE, len(lignes)))
    for l in lignes[:4]:
        e.texte(l, x + 10, ly, PETIT, MUTED, maxw=w - 20, quoi="explication")
        ly += 9
    e.texte(t("machines"), x + 10, ly + 6, PETIT_GRAS, FAINT, maxw=w - 20, quoi="machines")
    if ly + 6 + e.hcap(PETIT_GRAS) > y + h - 4:
        e.defauts.append("[%s] carte de la caisse trop pleine" % ab.LANGUE)


def messages(e, w):
    """Chaque résultat possible d'une opération tient dans sa case (avec de grands nombres)."""
    g = "999 999 999"
    for cle, args in (("ferme_partout", ()), ("bloque", ()), ("rien", ()), ("pas_monnaie_borne", (g,)), ("plein", ("1 000 000 000",)),
                      ("erreur", ()), ("depose", (g, g)), ("depose_arrondi", (g, g, g)), ("pas_assez", (g,)), ("place", (g,)),
                      ("retire", (g, g))):
        s = t("message.heralizcasino.compte." + cle, *args)
        lignes = e.lignes(s, PETIT_GRAS, w - 32)
        if len(lignes) > 2 or any(e.largeur(l, PETIT_GRAS) > w - 32 + 0.05 for l in lignes):
            e.defauts.append("[%s] résultat %s : « %s » (%d lignes, 2 au plus)" % (ab.LANGUE, cle, s, len(lignes)))
    for k in ("compte_ferme", "aucun_mouvement"):
        if e.largeur(t(k), PETIT_GRAS) > w - 20 + 0.05:
            e.defauts.append("[%s] %s : « %s »" % (ab.LANGUE, k, t(k)))


def ecran(sortie, retrait_saisi="", refus=""):
    e = fond_jeu()
    entete(e)
    carte_solde(e, M, 60, GAUCHE, 118, 1_234_567_890, [(10, 23_040), (5, 640), (1, 999)])
    carte_mouvements(e, M, 186, GAUCHE, 150, "porteur_de_peste_key")
    xd = M + GAUCHE + 10
    carte_operations(e, xd, 60, GW - M - xd, 170, 239_034, retrait_saisi, refus)
    carte_caisse(e, xd, 238, GW - M - xd, 98)
    messages(e, GW - M - xd)
    pied(e)
    e.sauver(sortie)


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "atm_capsules" / "ecran"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    global TXT
    for lang in ("fr_fr", "en_us", "ru_ru"):
        ab.LANGUE = lang
        TXT = json.loads((ASSETS / "heralizcrates/lang" / (lang + ".json")).read_text(encoding="utf-8"))
        TXT.update(json.loads((ASSETS / "heralizcasino/lang" / (lang + ".json")).read_text(encoding="utf-8")))
        suffixe = "" if lang == "fr_fr" else "_" + lang[:2]
        ecran(dossier / ("1_compte%s.png" % suffixe))
        ecran(dossier / ("2_retrait_refuse%s.png" % suffixe), nombre(12_000),
              t("message.heralizcasino.compte.place", nombre(12_000)))
        print("écrit", dossier, lang)
    print("aucun texte ne dépasse (fr, en, ru)")


if __name__ == "__main__":
    main()
