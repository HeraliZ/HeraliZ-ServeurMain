#!/usr/bin/env python3
"""Aperçu du panel du staff (client/EcranStaff, 25/10) dans les trois langues, avec les vrais textes du mod et les mêmes
mesures que le code (canevas 640 x 360 : liste des joueurs à gauche, fiche, cartes d'actions sur 4 colonnes, raison,
bouton). Même moteur que les aperçus de la boutique ; refuse tout texte qui dépasse (code de sortie 1).

    python modules/boutique/outils/apercu_staff.py [DOSSIER]      (par défaut : maquettes/staff_panel)

Vues : Fondateur sur l'onglet Joueurs (toutes les actions sur un joueur, la plus chargée), Admin sur l'onglet Serveur.
Les mesures sont celles de EcranStaff.java : à changer des deux côtés.
"""
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import apercu_boutique as ab  # noqa: E402

RACINE = Path(__file__).resolve().parents[3]
LANG = RACINE / "modules/boutique/src/client/resources/assets/heraliz_grades/lang"
GW, GH, M, HAUT, BAS = 640, 360, 16, 80, 332
LX, LW, RX, RW = 16, 170, 194, 430
ACCENT, WHITE, LIGHT, MUTED, FAINT, GOLD, OK = 0xE3201B, 0xFFFFFF, 0xECEEF2, 0x8F95A0, 0x5A5F69, 0xF2C14E, 0x6FD08C
BLEU, VERT, ROUGE, ORANGE = 0x5B8DEF, 0x4CAF7A, 0xE5484D, 0xFF8A3D
TITRE, ONGLET, BOUTON, CARTE = (True, 13, 1.93), (True, 9, 1.05), (True, 9, 0.85), (True, 9, 0)
TEXTE, VALEUR, PETIT, PETIT_GRAS, MINI, MICRO, NOM = (False, 7.5, 0), (True, 7.5, 0), (False, 6.5, 0), (True, 6.5, 0), (True, 6.5, 0.56), \
    (True, 5.6, 0.4), (True, 13, 1.4)
JOUEURS = ["avertir", "muet_court", "muet_long", "expulser", "ban_temp", "ban_pc", "message", "aller_voir", "faire_venir", "liberer",
           "inventaire_voir", "inventaire_modifier", "analyser_joueur", "donner_grade"]
DANGER = {"ban_temp", "ban_pc", "donner_grade", "frappe", "boss_eveil", "boss_reset", "redemarrage", "nettoyer_corps"}
SERVEUR = {"serveur": ["redemarrage", "recharger", "annonce"], "performances": ["etat", "analyser"],
           "nettoyage": ["nettoyer_sol", "nettoyer_corps", "nettoyer_zombies"]}
OPTIONS = {"muet_court": ["15m", "1h"], "redemarrage": ["5", "10", "15"]}
DEFAUTS = []


class Vue:
    def __init__(self, code):
        self.T = json.loads((LANG / f"{code}.json").read_text(encoding="utf-8"))
        self.e = ab.Ecran(ab.fond())

    def t(self, cle, *a):
        s = self.T["gui.heraliz_grades.staff." + cle]
        for i, x in enumerate(a):
            s = s.replace("%s", str(x), 1).replace(f"%{i + 1}$s", str(x))
        return s

    def act(self, a, aide=False):
        return self.T["heraliz_grades.staff.action." + a + (".aide" if aide else "")]

    def verre(self, x, y, w, h):
        self.e.rect(x, y, x + w, y + h, 0x0D0F14, 199)
        self.e.border(x, y, w, h, WHITE, 26)

    def entete(self, grade, couleur, n, onglets, actif):
        e = self.e
        tw = e.texte(self.t("titre").upper(), M, 17, TITRE, LIGHT, quoi="titre")
        g = self.T["grade.heraliz_grades." + grade]
        bw = e.largeur(g, PETIT_GRAS) + 10
        bx = M + tw + 8
        e.rect(bx, 16.5, bx + bw, 27.5, couleur, 40)
        e.border(bx, 16.5, bw, 11, couleur, 200)
        e.c(g.upper(), bx + bw / 2, 22, PETIT_GRAS, couleur)
        e.texte(self.t("sous", n), M, 35, TEXTE, MUTED, maxw=GW - 2 * M, quoi="sous-titre")
        x = M
        for o in onglets:
            nom = self.t("onglet." + o).upper()
            w = e.largeur(nom, ONGLET) + 16
            on = o == actif
            e.rect(x, 50, x + w, 70, ACCENT if on else WHITE, 46 if on else 9)
            e.border(x, 50, w, 20, ACCENT if on else WHITE, 160 if on else 30)
            e.c(nom, x + w / 2, 60, ONGLET, WHITE if on else MUTED)
            x += w + 4
        if x - 4 > GW - M:
            DEFAUTS.append(f"[{ab.LANGUE}] onglets : {x - 4:.1f} > {GW - M}")
        e.rect(M, 74, GW - M, 74 + 1 / 3, WHITE, 23)

    def carte(self, x, y, w, h, a, choisie=False, sec="moderation"):
        e = self.e
        c = ROUGE if a in DANGER else {"moderation": BLEU, "aide": VERT, "serveur": ORANGE, "performances": BLEU, "nettoyage": VERT}[sec]
        e.grad(x, y, x + w, y + h, c, 70 if choisie else 30, c, 30 if choisie else 10)
        e.border(x, y, w, h, c, 255 if choisie else 110)
        ty = y + 10 if h >= 30 else y + h / 2 - 3.5
        e.m(self.act(a), x + 8, ty, CARTE, WHITE, maxw=w - 16, quoi="carte " + a)
        e.m(self.act(a, True), x + 8, ty + (11 if h >= 30 else 8.5), PETIT, MUTED, maxw=w - 16, quoi="aide " + a)

    def joueurs(self):
        e = self.e
        self.verre(LX, HAUT, LW, BAS - HAUT)
        e.texte(self.t("en_ligne").upper(), LX + 8, HAUT + 7, MINI, MUTED, maxw=LW - 30, quoi="en ligne")
        e.rect(LX + 8, HAUT + 18, LX + LW - 8, HAUT + 32, 0x000000, 90)
        e.m(self.t("rechercher"), LX + 13, HAUT + 25, TEXTE, FAINT, maxw=LW - 26, quoi="recherche")
        for i, (clan, nom, ping) in enumerate([("WOLF", "Kevin_92", 34), ("", "Sacha_77", 120), ("LYNX", "NoaPlays", 62)]):
            y = HAUT + 38 + i * 15
            if i == 0:
                e.rect(LX + 4, y, LX + LW - 4, y + 15, ACCENT, 50)
            x = LX + 10
            if clan:
                x += e.m(f"[{clan}]", x, y + 7.5, VALEUR, BLEU) + 3
            e.d(f"{ping} MS", LX + LW - 10, y + 7.5, PETIT_GRAS, OK if ping < 60 else GOLD)
            e.m(nom, x, y + 7.5, TEXTE, LIGHT)
        self.verre(RX, HAUT, RW, 51)
        e.texte("KEVIN_92", RX + 12, HAUT + 9, NOM, WHITE)
        cols = [(self.t("f_monde"), "Carte · -1234 66 1118"), (self.t("f_en_jeu"), "2 h 14"),
                (self.t("f_sanctions"), self.t("sanctions_n", 1)), (self.t("f_combat"), self.t("oui_s", 9))]
        cx = [RX + 12, RX + 168, RX + 248, RX + 340, RX + RW - 12]
        for i, (lab, val) in enumerate(cols):
            w = cx[i + 1] - cx[i] - 8
            e.texte(lab.upper(), cx[i], HAUT + 29, MICRO, MUTED, maxw=w, quoi="fiche " + lab)
            e.texte(val, cx[i], HAUT + 39, VALEUR, LIGHT, maxw=w, quoi="fiche valeur")
        cw = (RW - 3 * 5) / 4
        y = 139
        for sec, groupe in (("moderation", JOUEURS[:6]), ("aide", JOUEURS[6:])):
            e.texte(self.t("section." + sec).upper(), RX, y, MINI, MUTED, quoi="section")
            y += 8
            for i, a in enumerate(groupe):
                self.carte(RX + (i % 4) * (cw + 5), y + (i // 4) * 25, cw, 22, a, a == "muet_court", sec)
            y += ((len(groupe) + 3) // 4) * 25 + 6
        e.texte(self.t("raison").upper(), RX, 263, MINI, MUTED)
        e.rect(RX, 270, RX + RW, 284, 0x000000, 150)
        e.border(RX, 270, RW, 14, ACCENT, 200)
        e.m(self.T["heraliz_grades.staff.raison.insultes"], RX + 5, 277, TEXTE, LIGHT)
        x = RX
        for r in ("insultes", "spam", "pub", "comportement", "triche"):
            s = self.T["heraliz_grades.staff.raison." + r]
            w = e.largeur(s, PETIT_GRAS) + 8
            e.rect(x, 287.5, x + w, 298.5, WHITE, 10)
            e.border(x, 287.5, w, 11, WHITE, 60)
            e.c(s, x + w / 2, 293, PETIT_GRAS, LIGHT)
            x += w + 4
        if x - 4 > RX + RW:
            DEFAUTS.append(f"[{ab.LANGUE}] raisons rapides : {x - 4:.1f} > {RX + RW}")
        bw, bx, yb = 128, RX + RW - 128, 304
        x = RX + e.m(self.t("duree"), RX, yb + 11, PETIT_GRAS, MUTED) + 6
        for o in OPTIONS["muet_court"]:
            s = self.T["heraliz_grades.staff.opt." + o]
            w = e.largeur(s, PETIT_GRAS) + 8
            e.rect(x, yb + 5.5, x + w, yb + 16.5, BLEU, 60 if o == "1h" else 10)
            e.border(x, yb + 5.5, w, 11, BLEU, 220 if o == "1h" else 60)
            e.c(s, x + w / 2, yb + 11, PETIT_GRAS, WHITE)
            x += w + 4
        e.grad(bx, yb, bx + bw, yb + 22, ACCENT, 110, ACCENT, 60)
        e.border(bx, yb, bw, 22, ACCENT, 200)
        e.c((self.act("muet_court") + " " + self.T["heraliz_grades.staff.opt.1h"]).upper(), bx + bw / 2, yb + 11, BOUTON, WHITE,
            maxw=bw - 10, quoi="bouton")
        for a in JOUEURS:
            l = self.e.largeur(self.act(a).upper(), BOUTON)
            if l > bw - 10 + 0.05:
                DEFAUTS.append(f"[{ab.LANGUE}] bouton « {self.act(a).upper()} » {l:.1f} > {bw - 10}")

    def serveur(self):
        e = self.e
        cw, top, h = 197, HAUT, 202
        for i, (sec, acts) in enumerate(SERVEUR.items()):
            x = M + i * (cw + 8.5)
            self.verre(x, top, cw, h)
            e.texte(self.t("section." + sec).upper(), x + 10, top + 9, MINI, MUTED)
            y = top + 20
            for a in acts:
                hh = 40 if a in OPTIONS else 32
                self.carte(x + 8, y, cw - 16, hh, a, sec=sec)
                if a in OPTIONS:
                    xx = x + 16
                    for o in OPTIONS[a]:
                        s = self.T["heraliz_grades.staff.opt." + o]
                        w = e.largeur(s, PETIT_GRAS) + 8
                        e.rect(xx, y + 27.5, xx + w, y + 38.5, ROUGE, 60 if o == "5" else 10)
                        e.border(xx, y + 27.5, w, 11, ROUGE, 220 if o == "5" else 60)
                        e.c(s, xx + w / 2, y + 33, PETIT_GRAS, WHITE)
                        xx += w + 4
                y += hh + 6
            if sec == "serveur":
                e.texte(self.t("texte_annonce").upper(), x + 10, y + 2, MINI, MUTED)
                e.rect(x + 8, y + 9, x + cw - 8, y + 23, 0x000000, 90)
                e.m(self.t("texte_invite"), x + 13, y + 16, TEXTE, FAINT, maxw=cw - 26, quoi="invite annonce")
        by = top + h + 6
        self.verre(M, by, GW - 2 * M, BAS - by)
        e.texte(self.t("dernieres").upper(), M + 10, by + 8, MINI, MUTED, quoi="dernières")

    def pied(self, grade, n):
        e = self.e
        x, cy = M, 348
        for k, v in (("P", self.t("panel")), (self.t("t_echap"), self.t("fermer")), (self.t("t_entree"), self.t("valider"))):
            kw = e.largeur(k, PETIT_GRAS) + 6
            e.border(x, cy - 5, kw, 10, WHITE, 90)
            e.c(k, x + kw / 2, cy, PETIT_GRAS, LIGHT)
            x += kw + 4
            x += e.m(v, x, cy, PETIT, MUTED) + 12
        e.d(self.t("pied", self.T["grade.heraliz_grades." + grade], n), GW - M, cy, PETIT, FAINT, maxw=150, quoi="pied")


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "staff_panel"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    tous = ["joueurs", "moderation", "monde", "evenements", "serveur", "historique", "permissions"]
    for code in ("fr_fr", "en_us", "ru_ru"):
        ab.LANGUE = code
        v = Vue(code)
        v.entete("fondateur", 0xE5484D, 35, tous, "joueurs")
        v.joueurs()
        v.pied("fondateur", 35)
        DEFAUTS.extend(v.e.defauts)
        v.e.sauver(dossier / f"panel_{code[:2]}_joueurs.png")
        v = Vue(code)
        v.entete("admin", 0xFF8A3D, 31, tous[:-1], "serveur")
        v.serveur()
        v.pied("admin", 31)
        DEFAUTS.extend(v.e.defauts)
        v.e.sauver(dossier / f"panel_{code[:2]}_serveur.png")
    if DEFAUTS:
        for d in DEFAUTS:
            print(d)
        sys.exit(1)
    print("aucun texte ne dépasse (fr, en, ru) ->", dossier)


if __name__ == "__main__":
    main()
