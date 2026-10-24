"""Panneau admin des op (/panel, 2.7.0) : aperçu hors du jeu et contrôle « rien ne déborde ».

    python mods/heraliz-grades/outils/apercu_panel.py [DOSSIER]      (par défaut : maquettes/panel_admin)

Mêmes mesures que client/EcranPanel (canevas 640 x 360 unités, rendu à l'échelle 3 = 1920 x 1080 ; les nombres sont
LUS dans EcranPanel.java), mêmes polices et mêmes textes (fichiers de langue du mod), dessin repris de
outils/apercu_boutique.py. Les commandes montrées sont un exemple (les vraies viennent de l'arbre des commandes envoyé
par le serveur). Chaque texte fixe est mesuré avec la vraie police dans les trois langues : un texte qui dépasse sa case
arrête le script (à corriger dans les langues ou dans les mesures, ici ET dans EcranPanel). Numpy et Pillow.
"""
import json
import re
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import apercu_boutique as ab  # noqa: E402

MOD = Path(__file__).resolve().parents[1]
JAVA = MOD / "src" / "client" / "java" / "fr" / "heraliz" / "grades" / "client" / "EcranPanel.java"
SRC = JAVA.read_text(encoding="utf-8")


def const(nom):
    m = re.search(r"\b%s = (-?\d+)" % nom, SRC)
    if not m:
        sys.exit("constante %s introuvable dans EcranPanel.java" % nom)
    return int(m.group(1))


GW, GH, M, HAUT, BAS = (const(n) for n in ("GW", "GH", "M", "HAUT", "BAS"))
SX, SY, SW, SH = (const(n) for n in ("SX", "SY", "SW", "SH"))
CX, CW, MX, MW, DX, DW = (const(n) for n in ("CX", "CW", "MX", "MW", "DX", "DW"))
RANG_CAT, RANG_CMD, RANG_CHOIX, RANG_USAGE = (const(n) for n in ("RANG_CAT", "RANG_CMD", "RANG_CHOIX", "RANG_USAGE"))
DH = BAS - HAUT
CATS = re.search(r"CATS = \{([^}]*)\}", SRC).group(1).replace('"', "").replace(" ", "").split(",")
CATEGORIE = {}
for cat, liste in re.findall(r'ranger\("(\w+)", ([^;]*)\);', SRC):
    for r in re.findall(r'"([^"]+)"', liste):
        CATEGORIE[r] = cat

ACCENT, WHITE, LIGHT, OPTION, MUTED, FAINT, PINK, GLASS, OK, GOLD = (ab.ACCENT, ab.WHITE, ab.LIGHT, ab.OPTION, ab.MUTED, ab.FAINT, ab.PINK,
                                                                     ab.GLASS, ab.OK, ab.GOLD)
# polices (gras, em, espacement) : mêmes que EcranPanel
TITRE, NOM, BOUTON = (True, 13, 1.93), (True, 11, 0), (True, 9, 0.85)
MINI, MINI_NUM, TEXTE, TEXTE_GRAS, PETIT = (True, 6.5, 0.56), (True, 6.5, 0.5), (False, 7.5, 0), (True, 7.5, 0), (False, 6.5, 0)

# exemple de racines (celles de nos mods, plus quelques-unes de Minecraft et des plugins)
RACINES = sorted(set(list(CATEGORIE) + ["login", "register", "changepassword", "/wand", "/set"]))
TXT = {}


def t(cle, *args):
    s = TXT.get("gui.heraliz_grades.panel." + cle, "?" + cle)
    if args:
        s = re.sub(r"%(\d+\$)?s", lambda m, it=iter(args): str(next(it)), s)
    return s.replace("%%", "%")


def categorie(r):
    return "monde" if r.startswith("/") else CATEGORIE.get(r, "autres")


def filtrees(c):
    return [r for r in RACINES if c == "toutes" or categorie(r) == c]


def tenir(e, s, p, maxw):
    if e.largeur(s.upper() if p[2] else s, p) <= maxw + 0.05:
        return s
    while len(s) > 1 and e.largeur((s + "…").upper() if p[2] else s + "…", p) > maxw:
        s = s[:-1]
    return s.strip() + "…"


def petit_bouton(e, lab, x, y, h, actif, choisi):
    w = e.largeur(lab.upper(), MINI) + 16
    if choisi:
        e.rect(x, y, x + w, y + h, ACCENT, 50)
        e.border(x, y, w, h, ACCENT, 150)
    else:
        e.rect(x, y, x + w, y + h, WHITE, 9 if actif else 5)
        e.border(x, y, w, h, WHITE, 35 if actif else 18)
    e.c(lab, x + w / 2, y + h / 2, MINI, (WHITE if choisi else OPTION) if actif else FAINT)
    return w


def champ(e, x, y, w, h, texte, invite, decalage, actif):
    e.rect(x, y, x + w, y + h, 0, 150 if actif else 90)
    e.border(x, y, w, h, ACCENT if actif else WHITE, 200 if actif else 40)
    tx, cy, place = x + 6 + decalage, y + h / 2, w - 12 - decalage
    if not texte:
        e.m(invite, tx, cy, TEXTE, FAINT, maxw=place, quoi="invite")
    else:
        tw = e.m(texte, tx, cy, TEXTE, LIGHT)
        if actif:
            e.rect(tx + tw + 1, cy - 4.5, tx + tw + 1 + 2 / 3, cy + 4.5, ACCENT, 230)


def ecran(sortie, cat, commande, morceaux, valeur, choix, usage):
    e = ab.Ecran(ab.FOND)
    # en-tête
    e.texte(t("titre"), M, 16, TITRE, LIGHT, maxw=SX - M - 12, quoi="titre")
    e.texte(t("sous"), M, 33, TEXTE, MUTED, maxw=SX - M - 12, quoi="sous-titre")
    champ(e, SX, SY, SW, SH, "", t("rechercher"), 12, False)
    e.disc(SX + 10, SY + SH / 2 - 1, 3.7, MUTED, 255, anneau=1.0)
    e.trait([(SX + 12.8, SY + SH / 2 + 1.8), (SX + 15, SY + SH / 2 + 4)], 1.1, MUTED, 255)
    e.rect(M, 44, GW - M, 44 + 1 / 3, WHITE, 23)
    # catégories
    ab.verre(e, CX, HAUT, CW, DH)
    e.texte(t("categories"), CX + 10, HAUT + 9, MINI, MUTED, maxw=CW - 20, quoi="catégories")
    if 22 + len(CATS) * RANG_CAT > DH - 4:
        e.defauts.append("trop de catégories pour la colonne")
    for i, c in enumerate(CATS):
        x, y, w, h = CX + 4, HAUT + 22 + i * RANG_CAT, CW - 8, RANG_CAT - 2
        sel = c == cat
        if sel:
            e.grad(x, y, x + w, y + h, ACCENT, 60, ACCENT, 8)
            e.rect(x, y + 2, x + 1.33, y + h - 2, ACCENT, 255)
        n = str(len(filtrees(c)) * 2)  # deux fois l'exemple : nombre plus large que dans le jeu
        nw = e.d(n, CX + CW - 10, y + h / 2, PETIT, PINK if sel else FAINT)
        e.m(t("cat." + c), CX + 12, y + h / 2, TEXTE_GRAS, WHITE if sel else OPTION, maxw=CW - 22 - nw - 6, quoi="catégorie " + c)
    # commandes
    ab.verre(e, MX, HAUT, MW, DH)
    lst = filtrees(cat)
    e.texte(t("commandes"), MX + 10, HAUT + 9, MINI, MUTED, maxw=MW - 50, quoi="commandes")
    e.d(str(len(lst)), MX + MW - 10, HAUT + 11.5, PETIT, FAINT)
    lx, ly, lw, lh = MX + 4, HAUT + 22, MW - 10, DH - 30
    for i, r in enumerate(lst):
        y = ly + i * RANG_CMD
        if y + RANG_CMD > ly + lh:
            break
        sel = r == commande
        if sel:
            e.grad(lx, y, lx + lw, y + RANG_CMD - 1, ACCENT, 55, ACCENT, 6)
            e.rect(lx, y + 2, lx + 1.33, y + RANG_CMD - 3, ACCENT, 255)
        e.m(tenir(e, "/" + r, TEXTE, lw - 14), lx + 8, y + (RANG_CMD - 1) / 2, TEXTE, WHITE if sel else LIGHT)
    if len(lst) * RANG_CMD > lh:
        tot = len(lst) * RANG_CMD
        e.rect(MX + MW - 5, ly, MX + MW - 5 + 1.33, ly + lh, WHITE, 14)
        e.rect(MX + MW - 5, ly, MX + MW - 5 + 1.33, ly + max(12, lh * lh / tot), WHITE, 90)
    # détail
    ab.verre(e, DX, HAUT, DW, DH, 205)
    ab.filet_haut(e, DX, HAUT, DW, ACCENT)
    e.texte(t("commande"), DX + 10, HAUT + 9, MINI, MUTED, maxw=110, quoi="commande")
    wu = e.largeur(t("utilisation").upper(), MINI) + 16
    wa = e.largeur(t("assistant").upper(), MINI) + 16
    xu = DX + DW - 10 - wu
    xa = xu - 4 - wa
    if xa < DX + 10 + e.largeur(t("commande").upper(), MINI) + 8:
        e.defauts.append("[%s] onglets du détail sur le titre" % ab.LANGUE)
    petit_bouton(e, t("assistant"), xa, HAUT + 5, 13, commande is not None, commande is not None and not usage)
    petit_bouton(e, t("utilisation"), xu, HAUT + 5, 13, commande is not None, commande is not None and usage)
    x, w = DX + 10, DW - 20
    if commande is None:
        e.c(t("choisis"), DX + DW / 2, HAUT + DH / 2 - 6, TEXTE, OPTION, maxw=DW - 30, quoi="choisis")
        e.c(t("permises"), DX + DW / 2, HAUT + DH / 2 + 7, PETIT, FAINT, maxw=DW - 30, quoi="permises")
    else:
        e.m("/" + commande, x, HAUT + 30, NOM, WHITE, maxw=w)
        py = HAUT + 42
        e.rect(x, py, x + w, py + 20, 0, 140)
        e.border(x, py, w, 20, WHITE, 36)
        e.m(tenir(e, "/" + " ".join(morceaux + ([valeur] if valeur else [])), TEXTE, w - 14), x + 7, py + 10, TEXTE, GOLD)
        bx = x + petit_bouton(e, t("retour"), x, HAUT + 68, 15, len(morceaux) > 1, False) + 4
        petit_bouton(e, t("effacer"), bx, HAUT + 68, 15, True, False)
        liste_y = HAUT + 92 if usage else HAUT + 133
        liste_h = HAUT + DH - 46 - liste_y
        e.rect(x, liste_y, x + w, liste_y + liste_h, 0, 60)
        if usage:
            for i, u in enumerate(choix):
                ry = liste_y + i * RANG_USAGE
                if ry + RANG_USAGE > liste_y + liste_h:
                    break
                if i % 2 == 0:
                    e.rect(x, ry, x + w - 4, ry + RANG_USAGE, WHITE, 5)
                e.m(tenir(e, u, PETIT, w - 14), x + 6, ry + RANG_USAGE / 2, PETIT, OPTION)
        else:
            e.texte(t("etape"), x, HAUT + 90, MINI, MUTED, maxw=w, quoi="étape")
            for cle, arg in (("valeur", "<targets> · <item>"), ("valeur_ou_mot", "<joueur>"), ("choisis_mot", "12"), ("fin", None),
                             ("etape_invalide", None)):
                if e.largeur(t(cle, arg) if arg else t(cle), PETIT) > w:
                    e.defauts.append("[%s] indication %s trop longue" % (ab.LANGUE, cle))
            e.m(t("valeur", "<item>"), x, HAUT + 104, PETIT, OPTION, maxw=w)
            champ(e, x, HAUT + 111, w, 18, valeur, t("invite"), 0, True)
            e.c(t("pas_de_suggestion"), x + w / 2, liste_y + liste_h / 2, PETIT, FAINT, maxw=w - 12, quoi="sans suggestion") if not choix else None
            for i, (txt, inf, mot) in enumerate(choix):
                ry = liste_y + i * RANG_CHOIX
                if ry + RANG_CHOIX > liste_y + liste_h:
                    break
                if i % 2 == 0:
                    e.rect(x, ry, x + w - 4, ry + RANG_CHOIX, WHITE, 5)
                iw = min(90, e.largeur(inf, PETIT)) if inf else 0
                if inf:
                    e.d(inf, x + w - 10, ry + RANG_CHOIX / 2, PETIT, PINK if mot else FAINT, maxw=90, quoi="info")
                e.m(tenir(e, txt, TEXTE_GRAS if mot else TEXTE, w - 22 - iw), x + 6, ry + RANG_CHOIX / 2, TEXTE_GRAS if mot else TEXTE, LIGHT)
        for cle in ("prete", "incomplete", "vide", "invalide"):
            if e.largeur(t(cle), PETIT) > w:
                e.defauts.append("[%s] état %s trop long" % (ab.LANGUE, cle))
        if e.largeur(t("envoye", "/give Steve minecraft:apple 1"), PETIT) > w:
            e.defauts.append("[%s] « envoyée » trop long pour une commande courte" % ab.LANGUE)
        e.m(t("prete"), x, HAUT + DH - 36, PETIT, OK, maxw=w, quoi="état")
        ey = HAUT + DH - 28
        e.grad(x, ey, x + w, ey + 20, ACCENT, 90, ACCENT, 40)
        e.border(x, ey, w, 20, ACCENT, 170)
        e.c(t("executer"), x + w / 2, ey + 10, BOUTON, WHITE, maxw=w - 10, quoi="exécuter")
    # pied
    cy, px = 347, M
    for k, lab in (("entree", "valider"), ("tab", "completer"), ("molette", "defiler"), ("echap", "fermer")):
        kl = t("touche." + k).upper()
        kw = max(9, e.largeur(kl, MINI_NUM) + 4.67)
        e.rect(px, cy - 4.5, px + kw, cy + 4.5, WHITE, 30)
        e.border(px, cy - 4.5, kw, 9, WHITE, 61)
        e.c(kl, px + kw / 2, cy, MINI_NUM, WHITE)
        px += kw + 3.5
        px += e.m(t("pied." + lab), px, cy, PETIT, MUTED) + 10
    e.d(t("note"), GW - M, cy, PETIT, FAINT, maxw=GW - M - px - 8, quoi="note du pied")
    e.sauver(sortie)


def main():
    global TXT
    sortie = Path(sys.argv[1]) if len(sys.argv) > 1 else MOD.parents[1] / "maquettes" / "panel_admin"
    sortie.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    ab.FOND = ab.fond()
    items = [("minecraft:apple", "", False), ("minecraft:arrow", "", False), ("tacz:modern_kinetic_gun", "", False),
             ("heraliz_composants:planches", "", False), ("heraliz_armory:casque_titan", "", False)] * 3
    for lang in ("fr_fr", "en_us", "ru_ru"):
        ab.LANGUE = lang
        TXT = json.loads((ab.ASSETS / "lang" / (lang + ".json")).read_text(encoding="utf-8"))
        suf = "" if lang == "fr_fr" else "_" + lang[:2]
        mot = TXT["gui.heraliz_grades.panel.mot_cle"]
        ecran(sortie / ("1_assistant%s.png" % suf), "objets", "give", ["give", "Steve"], "", items, False)
        clan = [("creer", mot, True), ("quitter", mot, True), ("inviter", mot, True), ("banque", mot, True), ("coffre", mot, True),
                ("don", mot, True), ("ameliorer", mot, True)]
        ecran(sortie / ("2_mots_cles%s.png" % suf), "clans", "clan", ["clan"], "", clan, False)
        usages = ["/clan creer <nom> <tag>", "/clan inviter <joueur>", "/clan quitter", "/clan banque deposer <montant>",
                  "/clan coffre", "/clan don <montant>", "/clan ameliorer <branche>", "/clan admin dissoudre <clan>"] * 2
        ecran(sortie / ("3_utilisation%s.png" % suf), "clans", "clan", ["clan"], "", usages, True)
        ecran(sortie / ("4_vide%s.png" % suf), "toutes", None, [], "", [], False)
    print("aucun texte ne dépasse (fr, en, ru) ; images dans", sortie)


if __name__ == "__main__":
    main()
