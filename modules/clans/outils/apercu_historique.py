#!/usr/bin/env python3
"""Rendus de l'onglet « Historique » de l'écran du clan (touche K), demande du propriétaire du 11/11 : toutes les lignes
de l'historique du clan, triables par catégorie (Tout, Points, Combats, Membres, Base, Clan), par pages de 40.

    python modules/clans/outils/apercu_historique.py [DOSSIER]      (par défaut : maquettes/historique)

Même moteur et mêmes mesures de l'écran du clan que modules/clans/outils/apercu_saison.py (panneau 560 x 320, onglet à
x 56, y 92, 528 x 234) ; mesures de l'onglet : celles de client/gui/tabs/HistoriqueTab (puces à y + 16, liste à y + 38,
lignes de 22) : à changer des deux côtés. Vrais textes du mod (français, anglais, russe : `historique.*`,
`gui.historique.*`) et vrai journal d'activité (`evenement.*`). Refuse tout texte qui dépasse de sa place (code de sortie 1).
Images : vue « Tout », vue « Points » (raison et joueur de chaque point, infobulle d'une ligne), vue « Combats », dans les
trois langues.
"""
import json
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "clans" / "outils"))
import apercu_saison as s  # noqa: E402

ab = s.ab
CRAC = RACINE / "modules/bases/src/client/resources/assets/crac/lang"
CLASHWAR = RACINE / "modules/clashwar/src/client/resources/assets/heraliz_clashwar/lang"
X, Y, W, H = s.X, s.Y, s.W, s.H
LIGNE, LISTE_Y, PUCES_Y = 22, 38, 16
# catégories de l'historique (LigneHistorique) et couleurs des puces / points de chaque ligne
CATS = ["tout", "points", "combats", "membres", "base", "clan"]
COUL_CAT = {"tout": s.TEXT, "points": s.OR, "combats": s.DANGER, "membres": s.ACCENT, "base": 0x4FB6C9, "clan": 0x9FA8B8}
COUL_SAISON = {0: s.PVE_C, 1: s.PVP_C, 2: s.EV_C, 3: s.OR}
NOM_SAISON = {0: "gui.saison.cat.pve", 1: "gui.saison.cat.pvp", 2: "gui.saison.cat.events", 3: "gui.historique.toutes"}
MIN = 60_000

# lignes d'exemple (comme LigneHistorique ; les mêmes que ClientClans.lignesFactices, l'historique du banc d'essai) :
# il y a combien de ms, catégorie, saison (-1 sans), qui, clé, args, points
LIGNES = [
    (2 * MIN, "points", 0, "Vortex_77", "historique.source.salle_rouge", [], 40),
    (6 * MIN, "combats", 1, "", "historique.kill", ["Vortex_77", "Rastaban [RVN]"], 1),
    (9 * MIN, "points", 0, "Dziga", "historique.source.zombie", ["37"], 0.37),
    (14 * MIN, "points", 2, "Dziga", "historique.source.objet_boite_noire_helico", [], 12),
    (21 * MIN, "combats", 1, "", "historique.mort.joueur", ["Dziga", "Hexa [ASH]"], -1),
    (33 * MIN, "membres", -1, "", "@PERMISSION", ["Yvann", "Vortex_77", "BASE_CONSTRUIRE", "AUTORISE"], 0),
    (48 * MIN, "points", 0, "Yvann", "historique.source.craft_rare", ["1"], 4),
    (62 * MIN, "base", -1, "", "@PERSO", ["crac.journal.coeur_pose", "Vortex_77"], 0),
    (75 * MIN, "points", 2, "", "historique.event.place", ["~historique.event.clashwar", "2"], 120),
    (90 * MIN, "membres", -1, "", "@ARRIVEE", ["Yvann"], 0),
    (130 * MIN, "points", 0, "Vortex_77", "historique.source.quete_principale", ["1"], 6),
    (180 * MIN, "combats", -1, "", "historique.mort.zombie", ["Yvann"], 0),
    (240 * MIN, "points", 0, "Dziga", "historique.source.vente_1000", ["2300"], 2.3),
    (300 * MIN, "clan", -1, "", "@NIVEAU", ["7"], 0),
    (420 * MIN, "points", -1, "Vortex_77", "historique.plafond.petites", ["50"], 0),
    (600 * MIN, "base", 3, "Hexa", "historique.raid.perdu", ["RVN"], -84.5),
]


class Textes(s.Langue):
    """Textes du mod des clans, plus ceux de CRac et du ClashWar (lignes écrites par ces mods dans le journal)."""

    def __init__(self, code):
        super().__init__(code)
        for d in (CRAC, CLASHWAR):
            f = d / f"{code}.json"
            if f.exists():
                for k, v in json.loads(f.read_text(encoding="utf-8")).items():
                    self.T.setdefault(k, v)

    def arg(self, a):
        return self.t(a[1:]) if a.startswith("~") else a

    def ligne(self, cle, args):
        """LigneHistorique.texte() : ligne du journal d'activité (« @GENRE », Evenement.texte) ou clé de l'historique."""
        if cle.startswith("@"):
            genre = cle[1:]
            if genre == "PERSO":
                return self.t(args[0], *args[1:])
            if genre == "PERMISSION":
                perm = self.t("perm." + args[2].lower()) if ("perm." + args[2].lower()) in self.T else args[2]
                return self.t("evenement.permission." + args[3].lower(), args[1], perm, args[0])
            return self.t("evenement." + genre.lower(), *args)
        return self.t(cle, *[self.arg(a) for a in args])

    def il_y_a(self, ms):
        """Ui.ilYA / Ui.duree."""
        m = ms // MIN
        if m >= 60:
            h = m // 60
            d = (str(h) + self.t("gui.duree.h") + " " + "%02d" % (m % 60))
        else:
            d = str(m) + self.t("gui.duree.min")
        return self.t("gui.il_y_a", d)


def points(L, v):
    if abs(v) < 0.005:
        return ""
    txt = L.decimal(abs(v))
    return ("+" if v > 0 else "-") + txt


def puces(e, L, actif):
    """Puces des catégories (même dessin que celles de l'onglet Saison) ; rend la fin de la dernière."""
    px = X
    for cle in CATS:
        nom = L.t("gui.historique.cat." + cle)
        pw = int(-(-e.largeur(nom.upper(), s.SMALL_ESP) // 1)) + 12
        on = cle == actif
        c = COUL_CAT[cle]
        e.rect(px, Y + PUCES_Y, px + pw, Y + PUCES_Y + 12, c, 60 if on else 12)
        e.border(px, Y + PUCES_Y, pw, 12, c, 200 if on else 50)
        e.c(nom, px + pw / 2, Y + PUCES_Y + 6, s.SMALL_ESP, s.TEXT if on else s.MUTED)
        px += pw + 5
    return px - 5


def onglet(e, L, actif, page, pages, survol=None):
    lignes = [x for x in LIGNES if actif == "tout" or x[1] == actif or (actif == "points" and abs(x[6]) > 0.004)]
    titre = L.t("gui.historique.titre")
    tw = e.largeur(titre.upper(), (True, 11 / 1.302, 1.2))
    s.ecrit(e, titre, X, Y, (True, 11 / 1.302, 1.2), s.TEXT)
    sous = L.t("gui.historique.sous_titre", L.nombre(len(lignes) + 40 * (pages - 1)))
    s.ecrit(e, s.couper(e, sous, s.SMALL, W - tw - 12 - 110), X + tw + 12, Y + 1, s.SMALL, s.FAINT)
    fin = puces(e, L, actif)
    # pagination à droite des puces : ← 1 / 3 →
    num = L.t("gui.historique.page", page, pages)
    nw = e.largeur(num, s.SMALL)
    xr = X + W
    for i, (lab, x0) in enumerate((("→", xr - 12), ("←", xr - 12 - 8 - nw - 8 - 12))):
        actif_b = (i == 0 and page < pages) or (i == 1 and page > 1)
        e.rect(x0, Y + PUCES_Y, x0 + 12, Y + PUCES_Y + 12, s.BLANC, 14 if actif_b else 5)
        e.border(x0, Y + PUCES_Y, 12, 12, s.BLANC, 40 if actif_b else 14)
        e.c(lab, x0 + 6, Y + PUCES_Y + 6.5, s.BOLD, s.TEXT if actif_b else s.FAINT)
    e.c(num, xr - 12 - 8 - nw / 2, Y + PUCES_Y + 6, s.SMALL, s.MUTED)
    if fin + 8 > xr - 12 - 8 - nw - 8 - 12:
        s.DEFAUTS.append("[%s] les puces touchent la pagination (%.1f > %.1f)" % (L.code, fin + 8, xr - 12 - 8 - nw - 8 - 12))
    e.rect(X, Y + 34, X + W, Y + 34 + 1 / 3, s.BLANC, 22)
    # liste
    ly, wl = Y + LISTE_Y, W - 2
    bas = Y + H - 4
    bulle = souris = None
    for i, (age, cat, saison, qui, cle, args, pts) in enumerate(lignes):
        if ly + LIGNE > bas:
            break
        survole = survol == i
        if survole:
            s.doux(e, X, ly, wl, LIGNE, s.BLANC, 13)
        # Ui.point(x + 2, ly + 5) : point d'état (dessiné en rond, comme celui des membres en ligne)
        e.disc(X + 4.5, ly + 7.5, 1.8, COUL_CAT[cat], 255)
        p = points(L, pts)
        pw = e.largeur(p, s.BOLD) if p else 0
        texte = L.ligne(cle, args)
        s.ecrit(e, s.couper(e, texte, s.FONT, wl - 12 - (pw + 34 if p else 0)), X + 12, ly + 3, s.FONT, s.TEXT)
        sous = L.il_y_a(age) + (" · " + L.t("gui.historique.par", qui) if qui else "")
        s.ecrit(e, s.couper(e, sous, s.SMALL, wl - 12 - 40), X + 12, ly + 13, s.SMALL, s.FAINT)
        if p:
            c = s.DANGER if pts < 0 else COUL_SAISON[saison]
            s.droite(e, p, X + wl, ly + 3, s.BOLD, c)
            s.droite(e, L.t(NOM_SAISON[saison]).upper(), X + wl, ly + 13, s.SMALL, c)
        if i < len(lignes) - 1:
            e.rect(X, ly + LIGNE - 1, X + wl, ly + LIGNE - 1 + 1 / 3, s.BLANC, 14)
        if survole:
            souris = (X + 160, ly + 10)
            bulle = [(texte, s.BOLD, s.TEXT), (L.t("gui.historique.le", "11/11", "14:32"), s.FONT, s.MUTED)]
            if qui:
                bulle.append((L.t("gui.historique.par", qui), s.FONT, s.TEXT))
            if p:
                cat_s = L.t(NOM_SAISON[saison])
                bulle.append((L.t("gui.historique.infobulle.points", p, cat_s), s.FONT, s.DANGER if pts < 0 else COUL_SAISON[saison]))
        ly += LIGNE
    if not lignes:
        e.c(L.t("gui.historique.vide"), X + W / 2, Y + LISTE_Y + 60, s.SMALL, s.FAINT)
    return bulle, souris


def ecran(sortie, L, actif="tout", page=1, pages=3, survol=None):
    e = ab.Ecran(ab.fond())
    fin = s.cadre_clan(e, L, "historique")
    bulle, souris = onglet(e, L, actif, page, pages, survol)
    if bulle:
        s.infobulle(e, bulle, *souris)
    s.DEFAUTS.extend(e.defauts)
    e.defauts = []
    e.sauver(sortie)
    return fin


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "historique"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    for code in ("fr_fr", "en_us", "ru_ru"):
        ab.LANGUE = code
        L = Textes(code)
        court = code[:2]
        ecran(dossier / f"historique_tout_{court}.png", L, "tout", 1, 3)
        ecran(dossier / f"historique_points_{court}.png", L, "points", 1, 2, survol=1)
        ecran(dossier / f"historique_combats_{court}.png", L, "combats", 1, 1, survol=1)
    if s.DEFAUTS:
        print("\n".join(s.DEFAUTS))
        sys.exit(1)
    print("aucun texte ne dépasse (fr, en, ru) ->", dossier)


if __name__ == "__main__":
    main()
