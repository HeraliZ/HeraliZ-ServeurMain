#!/usr/bin/env python3
"""Maquette (26/10, à valider) : écran des quêtes journalières (/journalieres, bouton du journal des quêtes), à la charte
du journal des quêtes et des écrans du clan.

    python modules/pnj/outils/apercu_journalieres.py [DOSSIER]      (par défaut : maquettes/journalieres)

Même moteur que les autres aperçus (modules/boutique/outils/apercu_boutique.py) : canevas 640 x 360 unités, rendu à
l'échelle 3 (1920 x 1080), polices DM Sans du jeu à leur vraie taille ; panneau 560 x 320 comme le journal. Refuse tout
texte qui dépasse de sa place (code de sortie 1). Chiffres lus dans serveur/config/heraliz_quetes/journalieres.json (écrit
par outils/quetes_journalieres.py). Textes en dur ici (maquette) : ils iront dans modules/pnj/outils/langues.py.

Écrans : 1 choix de la difficulté, 2 les trois propositions, 3 quête en cours, 4 quête réussie, 5 tirage de la
récompense, 6 journée terminée.
"""
import json
import sys
from pathlib import Path

from PIL import Image

RACINE = Path(__file__).resolve().parents[3]
sys.path.insert(0, str(RACINE / "modules" / "boutique" / "outils"))
import apercu_boutique as ab  # noqa: E402

ab.FONTS = RACINE / "modules/pnj/src/client/resources/assets/heraliz_quetes/font"
GW, GH = ab.GW, ab.GH
TEXT, MUTED, FAINT, OK, OR, DANGER, BLANC = 0xECEEF2, 0x8F95A0, 0x5A5F69, 0x5FD08A, 0xE8C36A, 0xFF6B6B, 0xFFFFFF
VERRE = 0x0D0F14
TITRE = (True, 14 / 1.302, 0)
FONT = (False, 11 / 1.302, 0)
BOLD = (True, 11 / 1.302, 0)
SMALL = (False, 9 / 1.302, 0)
SMALL_GRAS = (True, 9 / 1.302, 0)
CAPS = (False, 9 / 1.302, 1.0)
CAPS_GRAS = (True, 9 / 1.302, 1.0)
MICRO = (True, 7.5 / 1.302, 0.8)
DEFAUTS = []

PX, PY, PW, PH = 40, 20, 560, 320
ENTETE, M = 44, 16
DIFF = {"facile": ("Facile", OK, 1), "moyenne": ("Moyenne", OR, 2), "difficile": ("Difficile", DANGER, 3)}
RARETE = {"commun": 0xB9BEC6, "peu_commun": 0x5FD08A, "rare": 0x5AA9FF, "epique": 0xB57BFF, "legendaire": 0xF2B83A, "unique": 0xFF5A4E}
JOUR = json.loads((RACINE / "serveur/config/heraliz_quetes/journalieres.json").read_text(encoding="utf-8"))
RARETES = json.loads((RACINE / "serveur/config/heraliz_loot/raretes.json").read_text(encoding="utf-8"))

# ------------------------------------------------------------------------------------------------ icônes et noms
A = RACINE / "modules"
ICONES = {
    "heraliz_composants:": lambda i: [A / "equipement/src/client/resources/assets/heraliz_composants/textures/item/icon" / (i + ".png"),
                                      A / "equipement/build/resources/client/assets/heraliz_composants/textures/item/icon" / (i + ".png")],
    "heraliz_usure:": lambda i: [A / "monde/src/client/resources/assets/heraliz_usure/textures/item" / (i + ".png")],
    "heralisz_bunker:": lambda i: [A / "bunker/src/client/resources/assets/heralisz_bunker/textures/item" / (i + "_front.png"),
                                   A / "bunker/src/client/resources/assets/heralisz_bunker/textures/item" / (i + ".png")],
}
TACZ = RACINE / "serveur/tacz/heraliz_1_base_tacz/assets/tacz/textures"
CLES = Path.home() / ".cache/heraliz/cles_boutique"
NOMS = {"ferraille": "Ferraille ×4", "poudre": "Bocaux de poudre ×3", "balles_9mm": "30 balles 9 mm", "bandages": "Bandages ×3",
        "planches": "Planches ×3", "composant_ii": "Circuit imprimé II", "kit_fortune": "Kit de réparation de fortune",
        "recup_rare": "Montre à gousset", "fragment_vert": "Fragment de carte verte", "arme_pc": "CZ 75",
        "cle_quete": "Clé de la caisse de quêtes", "fragment_bleu": "Fragment de carte bleue", "composants_ii": "Ferraille II ×3",
        "balles_556": "30 balles 5,56", "soin_rare": "Trousse CAR", "metal": "Fragments de métal ×2", "arme_rare": "UMP-45",
        "kit_standard": "Kit de réparation", "recup_epique": "Chronographe de luxe", "composant_iii": "Circuit imprimé III",
        "fragments_verts": "2 fragments de carte verte", "carte_verte": "Carte d'accès verte", "cle_camo": "Clé Camo",
        "carte_bleue": "Carte d'accès bleue", "ak47": "AK-47", "composants_iii": "Kit de pièces III ×2",
        "metal_renforce": "Métal renforcé ×2", "balles_762": "45 balles 7,62", "arme_epique": "SCAR-L",
        "kit_armurier": "Kit d'armurier", "recup_legendaire": "Lingot d'or", "composant_iv": "Circuit imprimé IV",
        "fragment_rouge": "Fragment de carte rouge", "cle_emotes": "Clé des emotes", "cle_gemme": "Clé Gemme",
        "carte_rouge": "Carte d'accès rouge"}
_cache = {}


def icone_de(r):
    """Image de l'icône d'une récompense (ou None)."""
    if "cle" in r:
        cand = [CLES / (r["cle"] + ".png")]
    elif "fragment" in r:
        cand = ICONES["heralisz_bunker:"]("keycard_fragment_" + r["fragment"] + "_2")
    elif r["objet"] == "tacz:modern_kinetic_gun":
        cand = [TACZ / "gun/slot" / (r["nbt"].split('"')[1].split(":")[1] + ".png")]
    elif r["objet"] == "tacz:ammo":
        cand = [TACZ / "ammo/slot" / (r["nbt"].split('"')[1].split(":")[1] + ".png")]
    elif r["objet"] == "heralisz_tactical:consumable":
        cand = [A / "armes/src/client/resources/assets/heralisz_heal/textures/consumable/slot" / (r["nbt"].split('"')[1].split(":")[1] + ".png")]
    else:
        ns, i = r["objet"].split(":")
        cand = ICONES.get(ns + ":", lambda _: [])(i)
    for c in cand:
        if c.exists():
            if c not in _cache:
                im = Image.open(c).convert("RGBA")
                bb = im.getbbox()
                _cache[c] = im.crop(bb) if bb else im
            return _cache[c]
    return None


def rarete_de(r):
    if "cle" in r:
        return "legendaire" if r["cle"] == "gemme" else "epique"
    if "fragment" in r:
        return {"green": "rare", "blue": "epique", "red": "legendaire"}[r["fragment"]]
    nbt = r.get("nbt") or ""
    for cle, sec in (("GunId", "armes"), ("AmmoId", "munitions"), ("ConsumableId", "soins")):
        if cle in nbt:
            return RARETES[sec].get(nbt.split('"')[1], "commun")
    if r["objet"].startswith("heralisz_bunker:keycard_"):
        return {"green": "rare", "blue": "epique", "red": "legendaire"}[r["objet"].rsplit("_", 1)[1]]
    return RARETES["objets"].get(r["objet"], "commun")


def poser(e, im, x, y, w, h, alpha=1.0):
    """Icône d'objet centrée dans la boîte, agrandie au plus proche comme le jeu dessine les objets (pixels nets)."""
    k = ab.K
    s = min(w / im.width, h / im.height)
    iw, ih = max(1, int(round(im.width * s * k))), max(1, int(round(im.height * s * k)))
    r = im.resize((iw, ih), Image.NEAREST if im.width <= 64 else Image.LANCZOS)
    if alpha < 1:
        a = r.getchannel("A").point(lambda v: int(v * alpha))
        r.putalpha(a)
    e.im.alpha_composite(r, (int(round((x + w / 2) * k - iw / 2)), int(round((y + h / 2) * k - ih / 2))))


def pct(x):
    s = ("%.1f" % x).rstrip("0").rstrip(".") if x < 10 else "%d" % round(x)
    return s.replace(".", ",") + " %"


# ------------------------------------------------------------------------------------------------ briques de dessin
def verre(e, x, y, w, h, a=214):
    e.rect(x, y, x + w, y + h, VERRE, a)
    e.border(x, y, w, h, BLANC, 26)


def puce(e, txt, x, cy, c, plein=False, p=MICRO):
    w = e.largeur(txt, p) + 10
    e.rect(x, cy - 5, x + w, cy + 5, c, 70 if plein else 26)
    e.border(x, cy - 5, w, 10, c, 170)
    e.c(txt, x + w / 2, cy, p, TEXT if plein else c)
    return w


def bouton(e, txt, x, cy, w, c=BLANC, plein=False, p=CAPS_GRAS, h=16):
    if plein:
        e.rect(x, cy - h / 2, x + w, cy + h / 2, c, 60)
        e.border(x, cy - h / 2, w, h, c, 220)
    else:
        e.rect(x, cy - h / 2, x + w, cy + h / 2, BLANC, 6)
        e.border(x, cy - h / 2, w, h, c, 90)
    e.c(txt, x + w / 2, cy, p, TEXT if plein or c == BLANC else c, maxw=w - 8, quoi="bouton")


def points(e, x, cy, n, c):
    for k in range(3):
        e.disc(x + 3 + k * 6, cy, 1.8, c if k < n else FAINT, 255)


def icone_jour(e, x, y, c):
    """Pictogramme : page de calendrier, anneaux, une case cochée."""
    e.rect(x, y + 3, x + 22, y + 26, c, 36)
    e.border(x, y + 3, 22, 23, c, 200)
    e.rect(x, y + 3, x + 22, y + 9, c, 150)
    for ax in (x + 6, x + 16):
        e.rect(ax - 1, y, ax + 1, y + 5, BLANC, 220)
    for i in range(3):
        for j in range(2):
            cx, cy = x + 5 + i * 6, y + 13 + j * 6
            if i == 2 and j == 1:
                e.coche(cx - 1.5, cy - 2, 4.2, 0.9, OK)
            else:
                e.rect(cx - 1, cy - 1, cx + 1, cy + 1, BLANC, 90)


def entete(e, diff=None, sous="RENOUVELLEMENT À MINUIT  ·  DANS 6 H 18"):
    icone_jour(e, PX + M, PY + 9, OR)
    tx = PX + M + 32
    e.texte("Quêtes du jour", tx, PY + 9, TITRE, TEXT, quoi="titre")
    e.texte(sous, tx, PY + 27, CAPS, MUTED, maxw=PW - 150, quoi="sous-titre")
    if diff:
        nom, c, _ = DIFF[diff]
        w = e.largeur(nom.upper(), MICRO) + 10
        puce(e, nom.upper(), PX + PW - 34 - w, PY + 16, c, plein=True)
    e.c("×", PX + PW - 18, PY + 16, BOLD, MUTED)
    e.rect(PX + 8, PY + ENTETE, PX + PW - 8, PY + ENTETE + 1 / 3, BLANC, 40)


def pied(e, touches, note=None):
    y = PY + PH - 12
    e.rect(PX + 8, y - 8, PX + PW - 8, y - 8 + 1 / 3, BLANC, 22)
    x = PX + M
    for touche, aide in touches:
        w = e.largeur(touche, MICRO) + 8
        e.rect(x, y - 4.5, x + w, y + 4.5, BLANC, 18)
        e.border(x, y - 4.5, w, 9, BLANC, 60)
        e.c(touche, x + w / 2, y, MICRO, TEXT)
        x += w + 4
        x += e.m(aide, x, y, SMALL, MUTED) + 12
    if note:
        e.d(note, PX + PW - M, y, SMALL, FAINT, maxw=PX + PW - M - x - 8, quoi="note du pied")


def ligne_recompense(e, r, x, cy, w, mise_en_avant=False):
    """Icône, nom, chance à droite ; rend la hauteur."""
    col = RARETE[rarete_de(r)]
    if mise_en_avant:
        e.rect(x - 4, cy - 6.5, x + w + 4, cy + 6.5, col, 22)
    e.rect(x, cy - 5.5, x + 11, cy + 5.5, col, 34)
    e.rect(x, cy + 4.8, x + 11, cy + 5.5, col, 200)
    im = icone_de(r)
    if im is not None:
        poser(e, im, x + 1, cy - 4.5, 9, 9)
    cw = e.largeur(pct(r["chance"]), SMALL_GRAS)
    e.m(NOMS.get(r["id"], r["id"]), x + 16, cy, SMALL, TEXT, maxw=w - 16 - cw - 6, quoi="récompense")
    e.d(pct(r["chance"]), x + w, cy, SMALL_GRAS, col if r["chance"] <= 5 else MUTED)


def table_recompenses(e, diff, x, y, w, h, titre="RÉCOMPENSES POSSIBLES"):
    verre(e, x, y, w, h, 110)
    e.texte(titre, x + 10, y + 8, CAPS_GRAS, MUTED, maxw=w - 20, quoi="titre récompenses")
    e.texte("Un tirage par quête réussie", x + 10, y + 20, SMALL, FAINT, maxw=w - 20, quoi="aide récompenses")
    rec = sorted(JOUR["difficultes"][diff]["recompenses"], key=lambda r: -r["chance"])
    cy = y + 38
    pas = min(14.5, (h - 44) / len(rec))
    if pas < 12:
        DEFAUTS.append("liste des récompenses trop serrée (%.1f par ligne)" % pas)
    for r in rec:
        ligne_recompense(e, r, x + 10, cy, w - 20, mise_en_avant=r["chance"] <= 1)
        cy += pas


def grille_recompenses(e, diff, x, y, w, h, titre, cols=3):
    """Les récompenses en colonnes (écran des propositions), de la plus courante à la plus rare."""
    verre(e, x, y, w, h, 110)
    e.texte(titre, x + 10, y + 8, CAPS_GRAS, MUTED, maxw=w - 140, quoi="titre récompenses")
    e.d("Un tirage par quête réussie", x + w - 10, y + 11.5, SMALL, FAINT)
    rec = sorted(JOUR["difficultes"][diff]["recompenses"], key=lambda r: -r["chance"])
    lignes = -(-len(rec) // cols)
    cw = (w - 20 - (cols - 1) * 14) / cols
    pas = (h - 30) / lignes
    if pas < 12:
        DEFAUTS.append("grille des récompenses trop serrée (%.1f par ligne)" % pas)
    for i, r in enumerate(rec):
        cx = x + 10 + (i // lignes) * (cw + 14)
        cy = y + 30 + (i % lignes) * pas
        ligne_recompense(e, r, cx, cy, cw, mise_en_avant=r["chance"] <= 1)


# ------------------------------------------------------------------------------------------------ textes des quêtes
def texte_quete(q):
    t, n = q["type"], q["nombre"]
    if t == "zombies":
        if q.get("tenue") == "MILITAIRE":
            return "Tuer %d zombies militaires" % n, "Aérodrome, base Sentinelle, zones militaires"
        if q.get("arme") == "melee":
            return "Tuer %d zombies au corps à corps" % n, "Couteau, batte ou arme de mêlée en main"
        return "Tuer %d zombies" % n, "Partout sur la carte"
    if t == "conteneurs":
        if q.get("palier_min"):
            return "Fouiller %d conteneurs T%d ou plus" % (n, q["palier_min"]), "Conteneurs remplis, chacun une fois"
        return "Fouiller %d conteneurs" % n, "Conteneurs remplis, chacun une fois"
    if t == "salle":
        return "Fouiller une salle à carte %s" % q["couleur"], "Il faut une carte d'accès %s" % q["couleur"]
    if t == "extraction":
        return ("Réussir une extraction" if n == 1 else "Réussir %d extractions" % n), "Revenir au spawn par une zone d'extraction"
    if t == "vendre":
        return "Vendre pour %d hérals d'objets trouvés" % n, "Chez Mila, au spawn"
    if t == "recycler":
        return "Recycler %d objets" % n, "Au recycleur du spawn"
    if t == "livrer":
        if q.get("tag"):
            return "Rapporter %d objets de récupération" % n, "Ils sont pris quand tu valides"
        return "Rapporter %d composants de niveau %s" % (n, "I" * q["niveau"] if q["niveau"] < 4 else "IV"), "Ils sont pris quand tu valides"
    if t == "distance":
        return "Parcourir %d km à pied" % (n // 1000), "Sur la carte, ni véhicule ni train"
    if t == "pvp":
        return "Tuer %d survivants armés" % n, "Pas de ton clan, une fois par joueur"
    return t, ""


FAMILLE = {"zombies": "ZOMBIES", "fouille": "FOUILLE", "commerce": "COMMERCE", "ressources": "RESSOURCES",
           "exploration": "EXPLORATION", "salle": "SALLE À CARTE", "pvp": "JOUEURS"}


def quete(diff, i):
    return next(q for q in JOUR["difficultes"][diff]["quetes"] if q["id"] == i)


# ------------------------------------------------------------------------------------------------ écrans
def ecran_choix(sortie):
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 130)
    verre(e, PX, PY, PW, PH)
    entete(e)
    y0 = PY + ENTETE + 10
    e.texte("Choisis la difficulté du jour. Le choix est définitif jusqu'à minuit.", PX + M, y0, SMALL, MUTED, maxw=PW - 2 * M, quoi="intro")
    cy0 = y0 + 14
    ch = PY + PH - 26 - cy0
    cw = (PW - 2 * M - 16) / 3
    exemples = {"facile": ["f_zombies", "f_conteneurs", "f_vendre"], "moyenne": ["m_zombies_mil", "m_conteneurs", "m_salle_verte"],
                "difficile": ["d_salle_bleue", "d_zombies_mil", "d_pvp"]}
    for k, diff in enumerate(("facile", "moyenne", "difficile")):
        nom, c, n = DIFF[diff]
        x = PX + M + k * (cw + 8)
        e.rect(x, cy0, x + cw, cy0 + ch, BLANC, 5)
        e.border(x, cy0, cw, ch, c, 70)
        e.rect(x, cy0, x + cw, cy0 + 2, c, 230)
        nw = e.texte(nom, x + 12, cy0 + 10, BOLD, TEXT, maxw=cw - 50, quoi="difficulté")
        points(e, x + 12 + nw + 6, cy0 + 14.4, n, c)
        e.texte("ENVIRON %d MIN" % JOUR["difficultes"][diff]["duree_minutes"], x + 12, cy0 + 25, MICRO, MUTED, maxw=cw - 24)
        e.rect(x + 10, cy0 + 37, x + cw - 10, cy0 + 37 + 1 / 3, BLANC, 30)
        e.texte("PAR EXEMPLE", x + 12, cy0 + 43, MICRO, FAINT)
        ly = cy0 + 59
        for qid in exemples[diff]:
            t, _ = texte_quete(quete(diff, qid))
            e.disc(x + 14.5, ly, 1.3, c, 230)
            e.m(t, x + 20, ly, SMALL, TEXT, maxw=cw - 30, quoi="exemple")
            ly += 12
        e.rect(x + 10, ly + 2, x + cw - 10, ly + 2 + 1 / 3, BLANC, 30)
        e.texte("LES PLUS RARES", x + 12, ly + 8, MICRO, FAINT)
        rares = sorted(JOUR["difficultes"][diff]["recompenses"], key=lambda r: r["chance"])[:5]
        ry = ly + 25
        for r in rares:
            ligne_recompense(e, r, x + 12, ry, cw - 24)
            ry += 14
        bouton(e, "CHOISIR", x + 12, cy0 + ch - 16, cw - 24, c, plein=True)
    pied(e, [("ÉCHAP", "Fermer"), ("CLIC", "Choisir")], "Trois propositions, tu en gardes une")
    fin(e, sortie)


def ecran_propositions(sortie, diff="moyenne", ids=("m_zombies_mil", "m_conteneurs", "m_salle_verte")):
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 130)
    verre(e, PX, PY, PW, PH)
    entete(e, diff)
    nom, c, _ = DIFF[diff]
    y0 = PY + ENTETE + 10
    e.texte("Garde une de ces trois quêtes. Les deux autres disparaissent, sans nouveau tirage aujourd'hui.", PX + M, y0, SMALL, MUTED,
            maxw=PW - 2 * M, quoi="intro")
    cy0 = y0 + 14
    ch = 112
    cw = (PW - 2 * M - 16) / 3
    for k, qid in enumerate(ids):
        q = quete(diff, qid)
        t, aide = texte_quete(q)
        x = PX + M + k * (cw + 8)
        e.rect(x, cy0, x + cw, cy0 + ch, BLANC, 5)
        e.border(x, cy0, cw, ch, BLANC, 34)
        e.texte(FAMILLE[q["famille"]], x + 12, cy0 + 10, MICRO, c)
        lignes = e.lignes(t, BOLD, cw - 24)
        ty = cy0 + 24
        for l in lignes:
            e.texte(l, x + 12, ty, BOLD, TEXT, maxw=cw - 24, quoi="titre de quête")
            ty += 13
        for l in e.lignes(aide, SMALL, cw - 24):
            e.texte(l, x + 12, ty + 2, SMALL, MUTED if q["type"] != "salle" else OR, maxw=cw - 24, quoi="aide de quête")
            ty += 10
        e.rect(x + 12, cy0 + ch - 44, x + cw - 12, cy0 + ch - 43, BLANC, 22)
        e.d("0 / %d" % (q["nombre"] if q["type"] != "distance" else q["nombre"] // 1000), x + cw - 12, cy0 + ch - 50, SMALL_GRAS, MUTED)
        bouton(e, "ACCEPTER", x + 12, cy0 + ch - 18, cw - 24, c, plein=True)
    grille_recompenses(e, diff, PX + M, cy0 + ch + 8, PW - 2 * M, PY + PH - 26 - (cy0 + ch + 8), "RÉCOMPENSES · " + nom.upper())
    pied(e, [("ÉCHAP", "Fermer"), ("CLIC", "Accepter")], "Aucune ne te va ? Reviens demain")
    fin(e, sortie, "propositions")


def gauche_quete(e, diff, qid, fait, x, y, w, h):
    nom, c, n = DIFF[diff]
    q = quete(diff, qid)
    t, aide = texte_quete(q)
    verre(e, x, y, w, h, 110)
    e.texte("QUÊTE ACCEPTÉE", x + 12, y + 10, MICRO, c)
    ty = y + 24
    for l in e.lignes(t, BOLD, w - 24):
        e.texte(l, x + 12, ty, BOLD, TEXT, maxw=w - 24, quoi="titre")
        ty += 13
    e.texte(aide, x + 12, ty + 1, SMALL, MUTED, maxw=w - 24, quoi="aide")
    total = q["nombre"]
    by = y + 86
    e.texte("PROGRESSION", x + 12, by - 12, MICRO, FAINT)
    e.d("%d / %d" % (fait, total), x + w - 12, by - 9, BOLD, OK if fait >= total else TEXT)
    e.rect(x + 12, by, x + w - 12, by + 3, BLANC, 26)
    e.rect(x + 12, by, x + 12 + (w - 24) * min(1, fait / total), by + 3, OK if fait >= total else c, 230)
    return q


def ecran_en_cours(sortie, diff="moyenne", qid="m_zombies_mil", fait=12):
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 130)
    verre(e, PX, PY, PW, PH)
    entete(e, diff)
    y0 = PY + ENTETE + 10
    gw = 250
    h = PY + PH - 26 - y0
    gauche_quete(e, diff, qid, fait, PX + M, y0, gw, 112)
    ix = PX + M
    iy = y0 + 124
    infos = [("Temps restant", "6 h 18 (minuit, heure de Paris)"), ("Ce qui compte", "Les zombies que tu tues toi-même"),
             ("Ne compte pas", "Zombies d'une capture, d'une ferme ou créés à la main")]
    for k, (a, b) in enumerate(infos):
        e.texte(a.upper(), ix + 12, iy + k * 26, MICRO, FAINT)
        e.texte(b, ix + 12, iy + k * 26 + 10, SMALL, TEXT, maxw=gw - 24, quoi="info")
    bouton(e, "ABANDONNER", ix, y0 + h - 8, gw, DANGER)
    table_recompenses(e, diff, PX + M + gw + 12, y0, PW - 2 * M - gw - 12, h)
    pied(e, [("ÉCHAP", "Fermer"), ("CLIC ×2", "Abandonner")], "Abandon = plus de quête aujourd'hui")
    fin(e, sortie, "en cours")


def ecran_reussie(sortie, diff="moyenne", qid="m_zombies_mil"):
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 130)
    verre(e, PX, PY, PW, PH)
    entete(e, diff)
    y0 = PY + ENTETE + 10
    gw = 250
    h = PY + PH - 26 - y0
    q = gauche_quete(e, diff, qid, quete(diff, qid)["nombre"], PX + M, y0, gw, 112)
    cx, cy = PX + M + gw / 2, y0 + 150
    e.glow(cx, cy, 40, 30, OK, 60)
    e.disc(cx, cy, 13, OK, 60)
    e.disc(cx, cy, 13, OK, 230, anneau=1.2)
    e.coche(cx - 6, cy - 6, 12, 2.2, OK)
    e.c("Quête réussie", cx, cy + 25, BOLD, TEXT)
    e.c("Ta récompense t'attend : un tirage dans la table « Moyenne ».", cx, cy + 38, SMALL, MUTED, maxw=gw - 16, quoi="aide réussie")
    bouton(e, "TIRER LA RÉCOMPENSE", PX + M, y0 + h - 8, gw, OR, plein=True, h=18)
    e.c("Garde une case libre dans ton inventaire", cx, y0 + h - 24, SMALL, FAINT, maxw=gw)
    table_recompenses(e, diff, PX + M + gw + 12, y0, PW - 2 * M - gw - 12, h)
    pied(e, [("ÉCHAP", "Fermer"), ("CLIC", "Tirer")], "La récompense reste là après minuit")
    fin(e, sortie, "réussie")


def ecran_tirage(sortie, diff="moyenne", gagnant="carte_verte"):
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 130)
    verre(e, PX, PY, PW, PH)
    entete(e, diff)
    rec = {r["id"]: r for r in JOUR["difficultes"][diff]["recompenses"]}
    bande = ["composants_ii", "metal", "balles_556", "soin_rare", gagnant, "kit_standard", "composants_ii", "recup_epique", "arme_rare"]
    y0 = PY + ENTETE + 10
    e.c("TIRAGE  ·  TABLE « %s »" % DIFF[diff][0].upper(), PX + PW / 2, y0 + 8, CAPS_GRAS, MUTED)
    sy = y0 + 30
    sh = 86
    verre(e, PX + M, sy, PW - 2 * M, sh, 120)
    cw, gap = 52, 6
    milieu = PX + PW / 2
    # la bande s'arrête un peu à côté du milieu de la carte gagnante (comme la roue des caisses)
    x0 = milieu - (len(bande) // 2) * (cw + gap) - cw / 2 + 9
    for k, rid in enumerate(bande):
        r = rec[rid]
        x = x0 + k * (cw + gap)
        if x < PX + M + 2 or x + cw > PX + PW - M - 2:
            continue
        col = RARETE[rarete_de(r)]
        gagne = k == len(bande) // 2
        e.rect(x, sy + 10, x + cw, sy + sh - 10, col, 40 if gagne else 16)
        e.border(x, sy + 10, cw, sh - 20, col, 230 if gagne else 70)
        e.rect(x, sy + sh - 13, x + cw, sy + sh - 10, col, 220)
        im = icone_de(r)
        if im is not None:
            poser(e, im, x + 9, sy + 18, cw - 18, 34, alpha=1 if gagne else 0.55)
        e.c(pct(r["chance"]), x + cw / 2, sy + sh - 21, MICRO, TEXT if gagne else MUTED)
    e.rect(milieu - 0.6, sy + 2, milieu + 0.6, sy + sh - 2, OR, 255)
    e.poly([(milieu - 4, sy + 2), (milieu + 4, sy + 2), (milieu, sy + 7)], OR, 255)
    e.poly([(milieu - 4, sy + sh - 2), (milieu + 4, sy + sh - 2), (milieu, sy + sh - 7)], OR, 255)
    r = rec[gagnant]
    col = RARETE[rarete_de(r)]
    ry = sy + sh + 26
    e.glow(milieu, ry + 10, 120, 26, col, 40)
    e.c("Tu obtiens", milieu, ry, SMALL, MUTED)
    e.c(NOMS[gagnant], milieu, ry + 15, TITRE, col)
    e.c("Rare  ·  %s de chances  ·  ajoutée à ton inventaire" % pct(r["chance"]), milieu, ry + 32, SMALL, TEXT, maxw=PW - 60, quoi="résultat")
    bouton(e, "FERMER", milieu - 50, PY + PH - 40, 100, BLANC)
    pied(e, [("ESPACE", "Passer l'animation"), ("ÉCHAP", "Fermer")], "Une seule récompense par quête")
    fin(e, sortie, "tirage")


def ecran_termine(sortie, diff="moyenne"):
    e = ab.Ecran(ab.fond())
    e.rect(0, 0, GW, GH, 0x050608, 130)
    verre(e, PX, PY, PW, PH)
    entete(e, diff)
    cx, cy = PX + PW / 2, PY + PH / 2 - 10
    icone_jour(e, cx - 11, cy - 46, FAINT)
    e.c("C'est tout pour aujourd'hui", cx, cy, BOLD, TEXT)
    e.c("Nouvelles quêtes dans 6 h 18, à minuit (heure de Paris).", cx, cy + 14, SMALL, MUTED, maxw=PW - 80)
    w = 250
    verre(e, cx - w / 2, cy + 30, w, 44, 110)
    e.texte("AUJOURD'HUI", cx - w / 2 + 12, cy + 38, MICRO, FAINT)
    e.texte("Tuer 25 zombies militaires", cx - w / 2 + 12, cy + 50, SMALL_GRAS, TEXT, maxw=w - 90)
    e.d("RÉUSSIE", cx + w / 2 - 12, cy + 53, MICRO, OK)
    e.texte("Récompense : Carte d'accès verte", cx - w / 2 + 12, cy + 62, SMALL, MUTED, maxw=w - 24)
    pied(e, [("ÉCHAP", "Fermer")])
    fin(e, sortie, "terminé")


def fin(e, sortie, quoi=""):
    DEFAUTS.extend(e.defauts)
    e.defauts = []
    e.sauver(sortie)


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "journalieres"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    ab.LANGUE = "fr_fr"
    ecran_choix(dossier / "1_choix_difficulte.png")
    ecran_propositions(dossier / "2_trois_propositions.png")
    ecran_en_cours(dossier / "3_quete_en_cours.png")
    ecran_reussie(dossier / "4_quete_reussie.png")
    ecran_tirage(dossier / "5_tirage.png")
    ecran_termine(dossier / "6_journee_terminee.png")
    if DEFAUTS:
        print("\n".join(DEFAUTS))
        sys.exit(1)
    print("aucun texte ne dépasse ->", dossier)


if __name__ == "__main__":
    main()
