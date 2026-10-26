#!/usr/bin/env python3
"""Maquettes (25/10, à valider) : onglet « Saison » de l'écran du clan (touche K) et bandeau de la Purge en jeu.

    python modules/clans/outils/apercu_saison.py [DOSSIER]      (par défaut : maquettes/saison)

Même moteur que les aperçus de la boutique (modules/boutique/outils/apercu_boutique.py) : canevas 640 x 360 unités,
rendu à l'échelle 3 (1920 x 1080), polices DM Sans / Onest du mod des clans, tailles des fichiers font/*.json du mod
divisées par 1,302 (em). Écran du clan : panneau 560 x 320, en-tête 44, onglets 18, marge 16 (ClanScreen). Refuse tout
texte qui dépasse de sa place (code de sortie 1). Textes en dur ici (maquette) : ils iront dans les fichiers de langue
au moment du code.
"""
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageFilter

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "boutique" / "outils"))
import apercu_boutique as ab  # noqa: E402

ab.FONTS = RACINE / "modules/clans/src/client/resources/assets/heralisz_clans/font"
GW, GH, K = ab.GW, ab.GH, ab.K
TEXT, MUTED, FAINT, OK, OR, DANGER, BLANC = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0x5FD08A, 0xE8C36A, 0xFF6B6B, 0xFFFFFF
VERRE, ACCENT = 0x0D0F14, 0x6FA8FF
ROUGE, ROUGE_VIF, AMBRE = 0xE3201B, 0xFF3B30, 0xE8B34A
PVE_C, PVP_C, EV_C = 0x5FD08A, 0xFF6B6B, 0xB57BFF
# polices du mod des clans (em = taille du .json / 1,302)
FONT = (False, 11 / 1.302, 0)
BOLD = (True, 11 / 1.302, 0)
SMALL = (False, 9 / 1.302, 0)
SMALL_GRAS = (True, 9 / 1.302, 0)
SMALL_ESP = (False, 9 / 1.302, 0.8)
TITRE_HUD = (True, 9 / 1.302, 1.4)
DEFAUTS = []


def fond_jeu():
    """Une scène de jeu floue (ciel, horizon, sol) pour poser le bandeau de Purge."""
    W, H = GW * K, GH * K
    rng = np.random.default_rng(3)
    y = np.linspace(0, 1, H)[:, None]
    ciel, horizon, sol = np.array([96, 112, 128], np.float32), np.array([150, 150, 140], np.float32), np.array([58, 66, 44], np.float32)
    base = np.where(y[..., None] < 0.5, ciel + (horizon - ciel) * np.clip(y / 0.5, 0, 1)[..., None],
                    horizon + (sol - horizon) * np.clip((y - 0.5) / 0.15, 0, 1)[..., None])
    img = np.repeat(base, W, axis=1)
    bruit = Image.fromarray((rng.random((24, 42)) * 255).astype(np.uint8)).resize((W, H), Image.BICUBIC)
    img = img + ((np.array(bruit, np.float32) / 255 - 0.5) * 40)[..., None]
    return Image.fromarray(img.clip(0, 255).astype(np.uint8), "RGB").filter(ImageFilter.GaussianBlur(K * 1.2)).convert("RGBA")


def verre(e, x, y, w, h, a=214):
    e.rect(x, y, x + w, y + h, VERRE, a)
    e.border(x, y, w, h, BLANC, 26)


def doux(e, x, y, w, h, a):
    e.rect(x, y, x + w, y + h, BLANC, a)


# ------------------------------------------------------------------------------------------------ écran du clan
PX, PY, PW, PH = 40, 20, 560, 320
ENTETE, ONGLETS, M = 44, 18, 16
ONGLETS_NOMS = ["APERÇU", "MEMBRES", "BANQUE", "QUÊTES", "NIVEAU", "SAISON", "PERMISSIONS", "PARAMÈTRES"]

CLANS = [  # rang, tag, nom, couleur, pve, pvp, events, places (pve, pvp, events)
    (1, "WLF", "Les Loups Gris", 0x9FA8B8, 1842, 96, 340, (1, 2, 1)),
    (2, "RAT", "Rats du Bunker", 0xC9A34A, 1610, 131, 180, (2, 1, 3)),
    (3, "HZ", "Nuit Rouge", 0xE3201B, 1204, 64, 280, (3, 4, 2)),
    (4, "KRK", "Kraken Noir", 0x4FB6C9, 980, 88, 60, (4, 3, 5)),
    (5, "VLT", "Vautours", 0x8F6BD8, 702, 22, 100, (6, 6, 4)),
    (6, "OUR", "L'Ours de Valmont", 0x5FD08A, 744, 40, 20, (5, 5, 6)),
    (7, "CRB", "Corbeaux", 0x6B7280, 310, 12, 0, (7, 7, 7)),
]
MOI = "HZ"

BAREME = {
    "pve": [
        ("Salle à carte rouge", "40", "à chaque recharge de la salle"),
        ("Salle à carte bleue / verte", "15 / 5", "à chaque recharge de la salle"),
        ("Coffre d'événement T7", "5", "première ouverture"),
        ("Quête de Varga", "10", "une fois chacune"),
        ("Quête de clan", "5", "3 par jour, 2 par semaine"),
        ("Conteneur T1 → T6", "0,05 → 1,5", "première fouille après remplissage"),
        ("Vente au PNJ", "1 / 1 000 hérals", "objets trouvés seulement"),
        ("Zombies", "0,01 à 0,5", "plafond : 50 points par joueur et par jour"),
    ],
    "pvp": [
        ("Tuer un joueur", "+1", "victime armée ou en armure"),
        ("Mourir contre un joueur", "−1", "jamais sous 0"),
        ("Même victime en 6 h", "×1, ×0,5, puis 0", "pas de farm"),
        ("Prise du cœur (Purge)", "+10 %", "le clan pris perd 15 %"),
    ],
    "events": [
        ("ClashWar : 1er / 2e", "200 / 120", "4 clans au moins"),
        ("ClashWar : 3e / 4e", "80 / 40", "4 clans au moins"),
        ("ClashWar : participation", "10", ""),
        ("Événements de la carte", "bientôt", "largages, zone chaude, hélicoptère"),
    ],
}


def ecran_clan(sortie, categorie="pve"):
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 120)
    verre(e, PX, PY, PW, PH)
    # en-tête
    e.rect(PX + M, PY + 6, PX + M + 24, PY + 38, ROUGE, 230)
    e.poly([(PX + M, PY + 38), (PX + M + 12, PY + 32), (PX + M + 24, PY + 38)], VERRE, 255)
    tx = PX + M + 34
    w = e.texte("Nuit Rouge", tx, PY + 10, BOLD, TEXT, quoi="nom")
    w2 = e.texte("[HZ]", tx + w + 6, PY + 10, FONT, MUTED)
    cx = tx + w + 6 + w2 + 8
    cw = e.largeur("NIV. 12", SMALL_GRAS) + 8
    e.rect(cx, PY + 9, cx + cw, PY + 19, ROUGE, 50)
    e.border(cx, PY + 9, cw, 10, ROUGE, 140)
    e.c("NIV. 12", cx + cw / 2, PY + 14, SMALL_GRAS, TEXT)
    e.rect(tx, PY + 24, tx + 150, PY + 26, BLANC, 30)
    e.rect(tx, PY + 24, tx + 96, PY + 26, ROUGE, 230)
    e.texte("12 480 / 19 000 XP", tx, PY + 29.5, SMALL, MUTED)
    e.d("3 / 6 EN LIGNE", PX + PW - 40, PY + 15, SMALL_ESP, MUTED)
    e.disc(PX + PW - 40 - e.largeur("3 / 6 EN LIGNE", SMALL_ESP) - 8, PY + 15, 1.6, OK, 255)
    e.c("×", PX + PW - 18, PY + 16, BOLD, MUTED)
    e.rect(PX + 8, PY + ENTETE, PX + PW - 8, PY + ENTETE + 1 / 3, BLANC, 40)
    # onglets
    ox, ty = PX + M, PY + ENTETE + 6
    for nom in ONGLETS_NOMS:
        lw = e.largeur(nom, (False, 9 / 1.302, 1.0))
        actif = nom == "SAISON"
        e.texte(nom, ox, ty, (False, 9 / 1.302, 1.0), TEXT if actif else MUTED)
        if actif:
            e.rect(ox, ty + 10, ox + lw, ty + 11, ROUGE, 230)
        ox += lw + 16
    if ox - 16 > PX + PW - M:
        DEFAUTS.append("onglets trop larges : %.1f > %.1f" % (ox - 16, PX + PW - M))
    e.rect(PX + 8, PY + ENTETE + ONGLETS + 2, PX + PW - 8, PY + ENTETE + ONGLETS + 2 + 1 / 3, BLANC, 22)

    x0, y0 = PX + M, PY + ENTETE + ONGLETS + 10
    W, H = PW - 2 * M, PY + PH - 14 - y0
    gauche = 300
    # ---- gauche : classement
    e.texte("SAISON 1", x0, y0, (True, 9 / 1.302, 1.2), TEXT)
    e.texte("fin dans 23 jours · classement général = somme des 3 places", x0 + 50, y0 + 0.6, SMALL, FAINT,
            maxw=gauche - 50, quoi="sous-titre")
    hy = y0 + 14
    cols = [("PVE", 196, PVE_C), ("PVP", 236, PVP_C), ("EVENTS", 276, EV_C), ("GÉN.", gauche, TEXT)]
    e.texte("#", x0 + 2, hy, SMALL_ESP, MUTED)
    e.texte("CLAN", x0 + 34, hy, SMALL_ESP, MUTED)
    for nom, xr, c in cols:
        e.d(nom, x0 + xr, hy + 2.3, SMALL_ESP, c if nom != "GÉN." else MUTED)
    e.rect(x0, hy + 9, x0 + gauche, hy + 9 + 1 / 3, BLANC, 40)
    ly = hy + 12
    LIGNE = 17
    for rang, tag, nom, coul, pve, pvp, ev, pl in CLANS:
        moi = tag == MOI
        if moi:
            e.rect(x0, ly, x0 + gauche, ly + LIGNE, ROUGE, 28)
            e.rect(x0, ly + 1, x0 + 2, ly + LIGNE - 1, ROUGE, 220)
        cy = ly + LIGNE / 2
        e.d(str(rang), x0 + 16, cy, BOLD, OR if rang == 1 else TEXT if rang <= 3 else MUTED)
        e.rect(x0 + 22, ly + 3, x0 + 30, ly + 14, coul, 220)
        nw = e.m(nom, x0 + 34, cy, BOLD, TEXT, maxw=110, quoi="nom de clan")
        e.m("[" + tag + "]", x0 + 34 + nw + 4, cy, SMALL, MUTED, maxw=150 - nw - 4, quoi="tag")
        for (_, xr, c), v, p in zip(cols[:3], (pve, pvp, ev), pl):
            e.d(ab.nombre(v), x0 + xr - 9, cy - 0.8, SMALL_GRAS, TEXT)
            e.d(str(p), x0 + xr, cy + 0.2, (False, 5, 0), c)
        e.d(str(sum(pl)), x0 + gauche - 2, cy, BOLD, OR if rang == 1 else TEXT)
        e.rect(x0, ly + LIGNE - 1 / 3, x0 + gauche, ly + LIGNE, BLANC, 14)
        ly += LIGNE
    # bas gauche : état du clan
    by = y0 + H - 34
    doux(e, x0, by, gauche, 34, 8)
    e.border(x0, by, gauche, 34, BLANC, 18)
    e.texte("VOTRE CLAN", x0 + 8, by + 6, SMALL_ESP, MUTED)
    e.texte("3e au général · 1 548 points", x0 + 8, by + 17, BOLD, TEXT, maxw=180, quoi="état")
    e.disc(x0 + gauche - 92, by + 13, 2, OK, 255)
    e.texte("Cœur posé", x0 + gauche - 86, by + 10.5, SMALL_GRAS, OK)
    e.texte("Saison en cours", x0 + gauche - 86, by + 20, SMALL, MUTED)
    # ---- droite : comment gagner des points
    rx = x0 + gauche + 14
    rw = W - gauche - 14
    verre(e, rx, y0 - 2, rw, H + 2, 120)
    e.texte("GAGNER DES POINTS", rx + 10, y0 + 6, (True, 9 / 1.302, 1.2), TEXT, maxw=rw - 20, quoi="titre droite")
    # puces de catégorie
    px = rx + 10
    for cle, nom, c in (("pve", "PVE", PVE_C), ("pvp", "PVP", PVP_C), ("events", "ÉVÉNEMENTS", EV_C)):
        pw = e.largeur(nom, (True, 9 / 1.302, 0.8)) + 12
        actif = cle == categorie
        e.rect(px, y0 + 18, px + pw, y0 + 30, c, 60 if actif else 12)
        e.border(px, y0 + 18, pw, 12, c, 200 if actif else 50)
        e.c(nom, px + pw / 2, y0 + 24, (True, 9 / 1.302, 0.8), TEXT if actif else MUTED)
        px += pw + 5
    ly = y0 + 38
    for nom, pts, cond in BAREME[categorie]:
        e.texte(nom, rx + 10, ly, SMALL_GRAS, TEXT, maxw=rw - 20 - e.largeur(pts, SMALL_GRAS) - 8, quoi="action")
        e.d(pts, rx + rw - 10, ly + 2.4, SMALL_GRAS, {"pve": PVE_C, "pvp": PVP_C, "events": EV_C}[categorie])
        if cond:
            e.texte(cond, rx + 10, ly + 9, SMALL, FAINT, maxw=rw - 20, quoi="condition")
        e.rect(rx + 10, ly + 18, rx + rw - 10, ly + 18 + 1 / 3, BLANC, 14)
        ly += 21
    note = "Sans cœur posé dans la base, le clan ne gagne rien."
    for i, l in enumerate(e.lignes(note, SMALL, rw - 20)):
        e.texte(l, rx + 10, y0 + H - 18 + i * 8, SMALL, AMBRE, maxw=rw - 20, quoi="note")
    DEFAUTS.extend(e.defauts)
    e.defauts = []
    e.sauver(sortie)


# ------------------------------------------------------------------------------------------------ bandeau de Purge
def crane(e, cx, cy, s, c):
    e.disc(cx, cy - s * 0.15, s * 0.5, c, 255)
    e.rect(cx - s * 0.3, cy + s * 0.1, cx + s * 0.3, cy + s * 0.5, c, 255)
    e.disc(cx - s * 0.2, cy - s * 0.15, s * 0.14, VERRE, 255)
    e.disc(cx + s * 0.2, cy - s * 0.15, s * 0.14, VERRE, 255)
    for dx in (-0.15, 0, 0.15):
        e.rect(cx + dx * s - s * 0.03, cy + s * 0.3, cx + dx * s + s * 0.03, cy + s * 0.5, VERRE, 255)


def hud_jeu(e):
    """Barre d'objets et viseur, pour situer le bandeau."""
    e.rect(GW / 2 - 0.5, GH / 2 - 4, GW / 2 + 0.5, GH / 2 + 4, BLANC, 200)
    e.rect(GW / 2 - 4, GH / 2 - 0.5, GW / 2 + 4, GH / 2 + 0.5, BLANC, 200)
    hx = GW / 2 - 91
    for i in range(9):
        e.rect(hx + i * 20.2, GH - 24, hx + i * 20.2 + 19, GH - 5, 0x000000, 110)
        e.border(hx + i * 20.2, GH - 24, 19, 19, BLANC, 40)


def bandeau(e, etat):
    """etat : « pendant » (rouge), « bientot » (ambre, 10 min avant)."""
    rouge = etat == "pendant"
    c = ROUGE_VIF if rouge else AMBRE
    titre = "PURGE EN COURS" if rouge else "PURGE DANS 9:41"
    sous = "Les bases des clans sont raidables" if rouge else "Les bases seront raidables jusqu'à 22:00"
    temps = "1 h 05" if rouge else "15:00 → 22:00"
    wt = e.largeur(titre, TITRE_HUD)
    ws = e.largeur(sous, SMALL)
    wd = e.largeur(temps, BOLD)
    W = 22 + max(wt, ws) + 14 + wd + 12
    H = 26
    x, y = round(GW / 2 - W / 2), 6
    e.rect(x, y, x + W, y + H, VERRE, 205)
    e.border(x, y, W, H, c, 90)
    e.grad(x, y, x + W / 2, y + 2 / 3, c, 0, c, 255)
    e.grad(x + W / 2, y, x + W, y + 2 / 3, c, 255, c, 0)
    e.rect(x + 3, y + 3, x + 19, y + H - 3, c, 40)
    crane(e, x + 11, y + H / 2, 10, c)
    e.texte(titre, x + 24, y + 6, TITRE_HUD, c)
    e.texte(sous, x + 24, y + 16, SMALL, TEXT)
    e.rect(x + W - wd - 20, y + 5, x + W - wd - 20 + 1 / 3, y + H - 5, BLANC, 40)
    e.d(temps, x + W - 8, y + H / 2 - (2.5 if rouge else 0), BOLD, TEXT)
    if rouge:
        e.d("restantes", x + W - 8, y + H / 2 + 6, SMALL, MUTED)
        # barre du temps restant
        e.rect(x + 1, y + H - 1.2, x + W - 1, y + H - 1 / 3, BLANC, 20)
        e.rect(x + 1, y + H - 1.2, x + 1 + (W - 2) * 0.16, y + H - 1 / 3, c, 230)


def icone_purge(e, temps="1 h 05", part=0.84, souffle=1.0):
    """Icône retenue (25/10) : mêmes mesures que client/PurgeHud de CRac (coin 6,6 ; hauteur 12 ; police mini grasse)."""
    MINI_GRAS = (True, 6.5 / 1.0, 0.9)
    x, y, h = 6, 6, 12
    wt = e.largeur("PURGE", MINI_GRAS)
    wd = e.largeur(temps, (True, 6.5, 0))
    w = 12 + wt + 6 + 1 + 5 + wd + 6
    e.rect(x, y, x + w, y + h, VERRE, 200)
    e.border(x, y, w, h, BLANC, 26)
    e.rect(x + 1 / 3, y + 1 / 3, x + w - 1 / 3, y + 2 / 3, BLANC, 36)
    e.glow(x + 6, y + h / 2, 4.5, 4.5, ROUGE_VIF, 60 + 70 * souffle)
    e.disc(x + 6, y + h / 2, 2.2, ROUGE_VIF, 255)
    e.m("PURGE", x + 12, y + h / 2, MINI_GRAS, ROUGE_VIF)
    tx = x + 12 + wt + 6
    e.rect(tx, y + 3, tx + 1 / 3, y + h - 3, BLANC, 40)
    e.m(temps, tx + 5, y + h / 2, (True, 6.5, 0), TEXT)
    e.rect(x + 1 / 3, y + h - 2 / 3, x + 1 / 3 + (w - 2 / 3) * (1 - part), y + h - 1 / 3, ROUGE_VIF, 200)


def ecran_purge(sortie, etat):
    e = ab.Ecran(fond_jeu())
    hud_jeu(e)
    if etat == "icone":
        icone_purge(e)
    else:
        bandeau(e, etat)
    DEFAUTS.extend(e.defauts)
    e.defauts = []
    e.sauver(sortie)


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "saison"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    ab.LANGUE = "fr_fr"
    for cat in ("pve", "pvp", "events"):
        ecran_clan(dossier / f"clan_saison_{cat}.png", cat)
    ecran_purge(dossier / "purge_icone.png", "icone")
    if DEFAUTS:
        print("\n".join(DEFAUTS))
        sys.exit(1)
    print("aucun texte ne dépasse ->", dossier)


if __name__ == "__main__":
    main()
