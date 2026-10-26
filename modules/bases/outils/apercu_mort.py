#!/usr/bin/env python3
"""Aperçu de l'écran de mort HeraliZ (25/10, client/ui/EcranMort de CRac) dans les trois langues, avec les vrais textes
du mod et les mêmes mesures que le code (panneau 330 x 160 au centre du canevas 640 x 360, deux cartes « Au camp » /
« À la base »). Même moteur que les aperçus de la boutique (modules/boutique/outils/apercu_boutique.py). Refuse tout texte
qui dépasse de sa place (code de sortie 1).

    python modules/bases/outils/apercu_mort.py [DOSSIER]      (par défaut : maquettes/reapparition)

Trois états par langue : base disponible, délai en cours (7:59), clan sans base.
"""
import json
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "boutique" / "outils"))
import apercu_boutique as ab  # noqa: E402

LANG = RACINE / "modules/bases/src/client/resources/assets/crac/lang"
GW, GH = ab.GW, ab.GH
W, H, CARTE_H = 330, 160, 84
ACCENT, BLANC, TEXTE_C, ATTENUE, PALE, GRIS = 0xE3201B, 0xFFFFFF, 0xECEEF2, 0x8F95A0, 0x5A5F69, 0x9AA0AA
VERT, VERT_PALE, OR, ROUGE_PALE = 0x5FD08A, 0x9BE3B3, 0xE8C36A, 0xFF8A80
# polices de CRac (Dessin) : titre gras 13, mini 6,5, mini gras 6,5, moyen 7,5
TITRE = (True, 13, 1.12)
MINI = (False, 6.5, 0)
MINI_GRAS = (True, 6.5, 0.8)
MOYEN = (False, 7.5, 0)
DEFAUTS = []


def textes(code):
    return json.loads((LANG / f"{code}.json").read_text(encoding="utf-8"))


def ecran(code, etat, dossier):
    T = textes(code)
    t = lambda k, *a: (T[k].replace("%s", a[0]) if a else T[k])
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 150)
    x0, y0 = round((GW - W) / 2), round((GH - H) / 2)
    e.rect(x0, y0, x0 + W, y0 + H, 0x0D0F14, 214)
    e.border(x0, y0, W, H, BLANC, 26)
    e.grad(x0 + 13.33, y0, x0 + W / 2, y0 + 2 / 3, ACCENT, 0, ACCENT, 255)
    e.grad(x0 + W / 2, y0, x0 + W - 13.33, y0 + 2 / 3, ACCENT, 255, ACCENT, 0)
    x = x0 + 13.33
    e.rect(x, y0 + 11.33, x + 15.33, y0 + 26.67, ACCENT, 255)
    x += 15.33 + 5.33
    place = x0 + W - 13.33 - x
    e.texte(t("crac.mort.titre").upper(), x, y0 + 14, TITRE, BLANC, maxw=place, quoi="titre")
    e.texte({"fr_fr": "Tué par un zombie", "en_us": "Killed by a zombie", "ru_ru": "Убит зомби"}[code], x, y0 + 26.5, MINI, ATTENUE,
            maxw=place, quoi="cause")
    e.texte(t("crac.mort.ou").upper(), x0 + 13.33, y0 + 38, MINI_GRAS, ATTENUE, maxw=W - 26.67, quoi="section")
    cw = (W - 26.67 - 8) / 2
    cy = y0 + 46
    # carte camp
    cx = x0 + 13.33
    e.rect(cx, cy, cx + cw, cy + CARTE_H, BLANC, 13)
    e.border(cx, cy, cw, CARTE_H, VERT, 102)
    e.texte(t("crac.mort.camp").upper(), cx + 9, cy + 7.5, MINI_GRAS, VERT_PALE, maxw=cw - 18, quoi="camp")
    e.texte(t("crac.mort.camp_aide"), cx + 9, cy + 21, MOYEN, TEXTE_C, maxw=cw - 18, quoi="camp_aide")
    e.texte(t("crac.mort.camp_info"), cx + 9, cy + 33.5, MINI, ATTENUE, maxw=cw - 18, quoi="camp_info")
    e.grad(cx + 9, cy + CARTE_H - 26, cx + cw - 9, cy + CARTE_H - 9, ACCENT, 90, ACCENT, 40)
    e.border(cx + 9, cy + CARTE_H - 26, cw - 18, 17, ACCENT, 170)
    e.c(t("crac.mort.camp_bouton").upper(), cx + cw / 2, cy + CARTE_H - 17.5, MINI_GRAS, BLANC, maxw=cw - 22, quoi="camp_bouton")
    # carte base
    bx = cx + cw + 8
    ouverte = etat == "ok"
    e.rect(bx, cy, bx + cw, cy + CARTE_H, BLANC, 13 if ouverte else 8)
    e.border(bx, cy, cw, CARTE_H, OR if ouverte else BLANC, 102 if ouverte else 30)
    e.texte(t("crac.mort.base").upper(), bx + 9, cy + 7.5, MINI_GRAS, OR if ouverte else PALE, maxw=cw - 18, quoi="base")
    e.texte(t("crac.mort.base_aide"), bx + 9, cy + 21, MOYEN, TEXTE_C if ouverte else GRIS, maxw=cw - 18, quoi="base_aide")
    if etat == "ok":
        s, c = t("crac.mort.base_ok"), VERT
    elif etat == "attente":
        s, c = t("crac.mort.base_attente", "7:59"), OR
    else:
        s, c = t("crac.mort.raison.1"), ROUGE_PALE
    e.texte(s, bx + 9, cy + 33.5, MINI, c, maxw=cw - 18, quoi="etat")
    e.rect(bx + 9, cy + CARTE_H - 26, bx + cw - 9, cy + CARTE_H - 9, OR if ouverte else BLANC, 25 if ouverte else 8)
    e.border(bx + 9, cy + CARTE_H - 26, cw - 18, 17, OR if ouverte else BLANC, 110 if ouverte else 40)
    e.c(t("crac.mort.base_bouton").upper(), bx + cw / 2, cy + CARTE_H - 17.5, MINI_GRAS, BLANC if ouverte else PALE, maxw=cw - 22,
        quoi="base_bouton")
    # pied
    py = y0 + H - 14
    q = t("crac.mort.quitter").upper()
    qw = e.largeur(q, MINI_GRAS) + 9.33
    e.texte(t("crac.mort.partage"), x0 + 13.33, py - 2.3, MINI, PALE, maxw=W - 26.67 - qw - 8, quoi="partage")
    qx = x0 + W - 13.33 - qw
    e.rect(qx, py - 6, qx + qw, py + 6, BLANC, 13)
    e.border(qx, py - 6, qw, 12, BLANC, 36)
    e.c(q, qx + qw / 2, py, MINI_GRAS, 0xC9CDD4, quoi="quitter")
    DEFAUTS.extend(e.defauts)
    e.sauver(dossier / f"mort_{code[:2]}_{etat}.png")


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "reapparition"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    for code in ("fr_fr", "en_us", "ru_ru"):
        for etat in ("ok", "attente", "sans_base"):
            ab.LANGUE = code
            ecran(code, etat, dossier)
    if DEFAUTS:
        for d in DEFAUTS:
            print(d)
        sys.exit(1)
    print("aucun texte ne dépasse (fr, en, ru) ->", dossier)


if __name__ == "__main__":
    main()
