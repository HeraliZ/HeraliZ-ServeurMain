#!/usr/bin/env python3
"""Aperçu (03/11) : carte « Améliorations du clan » de l'onglet Niveau (client/gui/tabs/NiveauTab), avec la ligne
« Membres » en tête (places données par le niveau du clan, chaque palier débloqué ou bloqué « au niveau X »), dans les
trois langues, avec les vrais textes du mod.

    python modules/clans/outils/apercu_niveau.py [DOSSIER]      (par défaut : maquettes/niveau)

Même moteur que les aperçus de la boutique (modules/boutique/outils/apercu_boutique.py), polices du noyau, tailles des
fichiers font/*.json du mod divisées par 1,302 (em). Mesures de NiveauTab (panneau du clan 560 x 320, contenu à 56, 92,
528 x 234 ; carte de l'arbre à x + 178, largeur 350, rangées de 24) : à changer des deux côtés. Refuse tout texte qui
dépasse de sa place (code de sortie 1). Clan d'exemple : niveau 6 (deux paliers de membres atteints sur quatre).
"""
import json
import sys
from pathlib import Path

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "boutique" / "outils"))
import apercu_boutique as ab  # noqa: E402

ab.FONTS = RACINE / "modules/noyau/src/client/resources/assets/heraliz_noyau/font"
LANG = RACINE / "modules/clans/src/client/resources/assets/heralisz_clans/lang"
GW, GH = ab.GW, ab.GH
TEXT, MUTED, FAINT, OK, OR, AMBRE, BLANC, VERRE, ACCENT = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0x5FD08A, 0xE8C36A, 0xE8B34A, 0xFFFFFF, 0x0D0F14, \
    0x6FA8FF
BOLD = (True, 11 / 1.302, 0)
SMALL = (False, 9 / 1.302, 0)
SMALL_ESP = (False, 9 / 1.302, 0.6)
X, Y, W, H, GAUCHE, RANG = 56, 92, 528, 234, 170, 24
NIVEAU, DEPART, PALIERS = 6, 4, [2, 5, 10, 15]
BRANCHES = [("base", 3, 1, lambda p: 20 + 3 * p), ("tp", 3, 0, lambda p: 8 - p), ("revenu", 3, 2, lambda p: 10 * p),
            ("ravitaillement", 3, 0, lambda p: 15 * p), ("loot", 2, 1, lambda p: 2 * p), ("territoires", 2, 1, lambda p: min(2, p))]
DEFAUTS = []


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "niveau"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    for code in ("fr_fr", "en_us", "ru_ru"):
        ab.LANGUE = code
        T = json.loads((LANG / f"{code}.json").read_text(encoding="utf-8"))

        def t(cle, *a):
            s = T[cle]
            for i, x in enumerate(a):
                s = s.replace(f"%{i + 1}$s", str(x)).replace("%s", str(x), 1)
            return s.replace("%%", "%")

        e = ab.Ecran(ab.fond())
        e.rect(0, 0, GW, GH, 0x050608, 140)
        tx, tw = X + GAUCHE + 8, W - GAUCHE - 8
        e.rect(tx, Y, tx + tw, Y + H, BLANC, 8)
        e.border(tx + 1, Y + 1, tw - 2, H - 2, BLANC, 16)
        e.texte(t("gui.arbre.titre"), tx + 10, Y + 9, SMALL_ESP, MUTED, quoi="titre")
        libres = t("gui.arbre.points", 2)
        e.d(libres, tx + tw - 10, Y + 12, SMALL_ESP, OR)
        fin, cx = tx + tw - 80, tx + 44
        # ---- ligne Membres (rang 0)
        ry = Y + 26
        e.rect(tx + 5, ry - 2, tx + tw - 5, ry - 2 + RANG - 2, BLANC, 6)
        maxi = DEPART + sum(1 for v in PALIERS if NIVEAU >= v)
        prochain = next((v for v in PALIERS if v > NIVEAU), 0)
        nom = t("arbre.membres")
        wn = e.texte(nom, tx + 10, ry + 1, BOLD, TEXT, maxw=fin - tx - 10, quoi="Membres")
        for k, v in enumerate(PALIERS):
            e.disc(tx + 12 + k * 6, ry + 14, 1.5, OR if NIVEAU >= v else FAINT, 255)
        effet = t("arbre.effet.membres", maxi) + ("  →  " + t("arbre.effet.membres", maxi + 1) if prochain else "")
        e.texte(effet, tx + 10 + wn + 8, ry + 2, SMALL, MUTED, maxw=fin - (tx + 10 + wn + 8), quoi="effet membres")
        px = cx
        for k, v in enumerate(PALIERS):
            s = t("gui.arbre.membres.palier", DEPART + k + 1, v)
            pw = e.largeur(s, SMALL)
            if px + pw > fin:
                DEFAUTS.append(f"[{code}] palier « {s} » ne tient plus ({px + pw:.1f} > {fin})")
                break
            e.texte(s, px, ry + 12, SMALL, OR if NIVEAU >= v else FAINT)
            px += pw + 8
        droite = t("gui.arbre.membres.bloque", prochain) if prochain else t("gui.arbre.membres.toutes")
        for s in (t("gui.arbre.membres.bloque", 15), t("gui.arbre.membres.toutes")):
            if e.largeur(s, SMALL) > 64:
                DEFAUTS.append(f"[{code}] « {s} » plus large que le bouton ({e.largeur(s, SMALL):.1f} > 64)")
        e.d(droite, tx + tw - 12, ry + 10, SMALL, AMBRE if prochain else OK)
        # ---- branches (rangs 1 à 6)
        for i, (b, mx, p, eff) in enumerate(BRANCHES):
            ry = Y + 26 + (i + 1) * RANG
            if (i + 1) % 2 == 0:
                e.rect(tx + 5, ry - 2, tx + tw - 5, ry - 2 + RANG - 2, BLANC, 6)
            wn = e.texte(t("arbre." + b), tx + 10, ry + 1, BOLD, TEXT, maxw=fin - tx - 10, quoi=b)
            for k in range(mx):
                e.disc(tx + 12 + k * 6, ry + 14, 1.5, OR if k < p else FAINT, 255)
            ef = t("arbre.effet." + b, eff(p)) + ("  →  " + t("arbre.effet." + b, eff(p + 1)) if p < mx else "")
            ex = tx + 10 + wn + 8
            if fin - ex > 12:
                e.texte(ab_couper(e, ef, fin - ex), ex, ry + 2, SMALL, MUTED)
            if p < mx:
                req = 5 if b == "territoires" and p == 0 else 10 if b == "territoires" else 0
                cout = t("gui.arbre.cout_niveau", p + 1, f"{(p + 1) * 10000:,}".replace(",", " "), req) if req else \
                    t("gui.arbre.cout", p + 1, f"{(p + 1) * 10000:,}".replace(",", " "))
                e.texte(ab_couper(e, cout, fin - cx), cx, ry + 12, SMALL, AMBRE if req > NIVEAU else FAINT)
                bx = tx + tw - 74
                e.rect(bx, ry + 4, bx + 64, ry + 18, ACCENT, 30)
                e.border(bx, ry + 4, 64, 14, ACCENT, 120)
                e.c(t("gui.arbre.ameliorer"), bx + 32, ry + 11, SMALL, TEXT, maxw=60, quoi="Améliorer")
            else:
                e.texte(t("gui.arbre.max"), cx, ry + 12, SMALL, OK)
        sy = Y + 30 + (len(BRANCHES) + 1) * RANG
        e.rect(tx + 8, sy - 4, tx + tw - 8, sy - 4 + 1 / 3, BLANC, 30)
        e.texte(t("gui.saison.points", 120, "#3", 4, "#5", 30, "#2"), tx + 10, sy + 3, SMALL, TEXT, maxw=tw - 20, quoi="saison")
        e.texte(t("gui.saison.general", "#3"), tx + 10, sy + 14, BOLD, OR)
        if sy + 14 + 9 > Y + H:
            DEFAUTS.append(f"[{code}] la saison sort de la carte ({sy + 23} > {Y + H})")
        DEFAUTS.extend(e.defauts)
        e.defauts.clear()
        e.sauver(dossier / f"arbre_membres_{code[:2]}.png")
    if DEFAUTS:
        print("\n".join(DEFAUTS))
        sys.exit(1)
    print("aucun texte ne dépasse (fr, en, ru) ->", dossier)


def ab_couper(e, s, maxw):
    """Texte coupé avec « … » comme Ui.ellipsis (secours du code : ici seulement pour les lignes des branches)."""
    if e.largeur(s, SMALL) <= maxw:
        return s
    while s and e.largeur(s + "…", SMALL) > maxw:
        s = s[:-1]
    return s.strip() + "…"


if __name__ == "__main__":
    main()
