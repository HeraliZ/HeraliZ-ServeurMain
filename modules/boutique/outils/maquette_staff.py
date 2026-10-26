"""Maquettes à valider (règle 9) : panel du staff par grade et nouveau chat (demande du propriétaire, 25/10).

    python modules/boutique/outils/maquette_staff.py [DOSSIER]      (par défaut : maquettes/staff_chat)

Rien n'est installé dans le jeu : ce sont des images. Même moteur que les aperçus de la boutique (apercu_boutique.py :
canevas 640 x 360 unités, verre, polices DM Sans à leur vraie taille), enregistrées en 1920 x 1080.
  1_panel_helpeur.png   : ce que voit un Helpeur en ouvrant son panel (touche P) : seulement ses actions
  2_panel_admin.png     : un Admin, onglet Serveur (plus d'onglets, plus d'actions)
  3_permissions.png     : le Fondateur règle ce que chaque grade du staff peut faire (une case par action)
  4_chat_ferme.png      : en jeu, chat fermé (petit, transparent, s'efface) et notification des extractions en haut à gauche
  5_chat_ouvert.png     : chat ouvert : canaux Proximité / Global / Clan, aucune commande affichée dedans
"""
import sys
from pathlib import Path

from PIL import Image, ImageFilter

sys.path.insert(0, str(Path(__file__).resolve().parent))
import apercu_boutique as ab  # noqa: E402

RACINE = Path(__file__).resolve().parents[3]
FOND_JEU = RACINE / "modules/interface/src/main/resources/assets/heraliz_interface/textures/gui/fond_titre.png"
GW, GH, M = ab.GW, ab.GH, 16
ACCENT, WHITE, LIGHT, OPTION, MUTED, FAINT, GOLD, OK, DANGER = (ab.ACCENT, ab.WHITE, ab.LIGHT, ab.OPTION, ab.MUTED, ab.FAINT,
                                                                ab.GOLD, ab.OK, ab.DANGER)
TITRE, NOM, BOUTON, ONGLET, MINI, MINI_NUM = ab.TITRE, ab.NOM, ab.BOUTON, ab.ONGLET, ab.MINI, ab.MINI_NUM
TEXTE, TEXTE_GRAS, PETIT, PETIT_GRAS, MICRO = ab.TEXTE, ab.TEXTE_GRAS, ab.PETIT, ab.PETIT_GRAS, ab.MICRO
CHAT = (False, 6.5, 0)
CHAT_GRAS = (True, 6.5, 0)

GRADES = {"fondateur": ("FONDATEUR", 0xE3201B), "admin": ("ADMIN", 0xFF8A3D), "moderateur": ("MODÉRATEUR", 0x4FA3FF),
          "helpeur": ("HELPEUR", 0x5FD08A), "guide": ("GUIDE", 0xC9A7FF)}


def fond_jeu(flou=0):
    im = Image.open(FOND_JEU).convert("RGB").resize((GW * ab.K, int(GW * ab.K * 1058 / 1600)), Image.LANCZOS)
    im = im.crop((0, 0, GW * ab.K, GH * ab.K))
    if flou:
        im = im.filter(ImageFilter.GaussianBlur(flou))
    return im.convert("RGBA")


def badge(e, cle, x, cy):
    nom, c = GRADES[cle]
    w = e.largeur(nom, PETIT_GRAS) + 8
    e.grad(x, cy - 5, x + w, cy + 5, c, 70, c, 25)
    e.border(x, cy - 5, w, 10, c, 160)
    e.c(nom, x + w / 2, cy, PETIT_GRAS, ab.mix(c, WHITE, 0.35))
    return w


def entete(e, grade, sous, onglets, actif):
    e.texte("PANEL STAFF", M, 18, TITRE, LIGHT)
    w = e.largeur("PANEL STAFF", TITRE)
    badge(e, grade, M + w + 8, 18 + e.hcap(TITRE) / 2)
    e.texte(sous, M, 35, TEXTE, MUTED)
    ty, th, x = 50, 20, M
    for i, o in enumerate(onglets):
        lw = e.largeur(o.upper(), ONGLET) + 16
        if i == actif:
            e.grad(x, ty, x + lw, ty + th, ACCENT, 51, ACCENT, 6)
            e.border(x, ty, lw, th, ACCENT, 92)
            e.rect(x + 4, ty + th - 1.33, x + lw - 4, ty + th, ACCENT, 255)
        else:
            e.rect(x, ty, x + lw, ty + th, WHITE, 8)
            e.border(x, ty, lw, th, WHITE, 22)
        e.c(o, x + lw / 2, ty + th / 2, ONGLET, WHITE if i == actif else OPTION)
        x += lw + 4
    e.rect(M, ty + th + 4, GW - M, ty + th + 4 + 1 / 3, WHITE, 23)


def pied(e, touches, note):
    cy, x = 348, M
    for k, lab in touches:
        w = max(9, e.largeur(k, MINI_NUM) + 4.67)
        e.rect(x, cy - 4.5, x + w, cy + 4.5, WHITE, 30)
        e.border(x, cy - 4.5, w, 9, WHITE, 61)
        e.c(k, x + w / 2, cy, MINI_NUM, WHITE)
        x += w + 3.5
        x += e.m(lab, x, cy, PETIT, MUTED) + 10
    e.d(note, GW - M, cy, PETIT, FAINT)


def joueurs(e, x, y, w, h, choisi):
    ab.verre(e, x, y, w, h)
    e.texte("JOUEURS EN LIGNE", x + 8, y + 8, MINI, MUTED)
    e.d("48", x + w - 8, y + 8 + e.hcap(MINI) / 2, MINI_NUM, OPTION)
    e.rect(x + 8, y + 20, x + w - 8, y + 34, WHITE, 10)
    e.border(x + 8, y + 20, w - 16, 14, WHITE, 40)
    e.m("Rechercher un joueur…", x + 13, y + 27, PETIT, FAINT)
    liste = [("Kevin_92", "WOLF", 34, "combat"), ("Lina", "WOLF", 51, ""), ("xXSniperXx", "", 88, "muet"), ("Matteo", "RUST", 40, ""),
             ("Sacha_77", "", 120, ""), ("NoaPlays", "LYNX", 62, ""), ("Hugo", "", 45, ""), ("Clara.b", "RUST", 70, ""),
             ("Zed", "", 210, ""), ("Tomtom", "LYNX", 38, "")]
    ly = y + 40
    for i, (nom, clan, ping, etat) in enumerate(liste):
        if ly + 14 > y + h - 4:
            break
        if i == choisi:
            e.grad(x + 4, ly, x + w - 4, ly + 14, ACCENT, 46, ACCENT, 8)
            e.rect(x + 4, ly, x + 5.3, ly + 14, ACCENT, 255)
        cx = x + 10
        if clan:
            cx += e.m("[%s]" % clan, cx, ly + 7, PETIT_GRAS, 0x9FB4C8) + 3
        e.m(nom, cx, ly + 7, TEXTE, WHITE if i == choisi else LIGHT)
        if etat == "combat":
            e.disc(x + w - 30, ly + 7, 2, DANGER, 255)
        if etat == "muet":
            e.d("MUET", x + w - 34, ly + 7, MICRO, GOLD)
        e.d("%d ms" % ping, x + w - 8, ly + 7, MICRO, OK if ping < 80 else GOLD if ping < 150 else DANGER)
        ly += 15


def fiche(e, x, y, w):
    ab.verre(e, x, y, w, 52)
    e.texte("Kevin_92", x + 10, y + 9, NOM, WHITE)
    nw = e.largeur("Kevin_92", NOM)
    e.m("[WOLF]", x + 10 + nw + 6, y + 9 + e.hcap(NOM) / 2, PETIT_GRAS, 0x9FB4C8)
    infos = [("Sur la carte", "à 1,2 km du spawn"), ("En jeu depuis", "2 h 14"), ("Sanctions", "1 avertissement"), ("En combat", "oui, 9 s")]
    cx = x + 10
    for lab, val in infos:
        e.texte(lab.upper(), cx, y + 28, MICRO, FAINT)
        e.texte(val, cx, y + 36, PETIT_GRAS, DANGER if lab == "En combat" else LIGHT)
        cx += max(e.largeur(lab.upper(), MICRO), e.largeur(val, PETIT_GRAS)) + 16


def action(e, x, y, w, h, titre, sous, c=ACCENT, danger=False):
    cc = DANGER if danger else c
    e.grad(x, y, x + w, y + h, cc, 40, cc, 12)
    e.border(x, y, w, h, cc, 120)
    e.texte(titre, x + 8, y + 7, TEXTE_GRAS, WHITE)
    e.texte(sous, x + 8, y + 19, PETIT, MUTED)


def raison(e, x, y, w):
    e.texte("RAISON", x, y, MINI, MUTED)
    e.rect(x, y + 9, x + w, y + 23, WHITE, 10)
    e.border(x, y + 9, w, 14, WHITE, 40)
    e.m("Insultes envers un joueur", x + 5, y + 16, PETIT, LIGHT)
    px = x
    for r in ("Insultes", "Spam", "Publicité", "Comportement", "Triche (staff)"):
        px += ab.puce(e, r, px, y + 32, 0x9FB4C8) + 4


def maquette_helpeur(dossier):
    e = ab.Ecran(ab.fond())
    entete(e, "helpeur", "Ton grade permet 6 actions. Les autres sont réservées au staff au-dessus de toi.",
           ["Joueurs", "Historique"], 0)
    joueurs(e, M, 80, 170, 252, 0)
    x0 = M + 178
    w = GW - M - x0
    fiche(e, x0, 80, w)
    e.texte("MODÉRATION", x0, 140, MINI, MUTED)
    aw, ah, gap = (w - 8) / 3, 30, 4
    action(e, x0, 150, aw, ah, "Avertir", "message + note dans l'historique", 0x4FA3FF)
    action(e, x0 + aw + gap, 150, aw, ah, "Rendre muet", "15 min · 1 h · 1 jour", 0x4FA3FF)
    action(e, x0 + 2 * (aw + gap), 150, aw, ah, "Expulser", "déconnecté, peut revenir", danger=True)
    e.texte("AIDE", x0, 190, MINI, MUTED)
    action(e, x0, 200, aw, ah, "Aller voir", "téléportation, caché", 0x5FD08A)
    action(e, x0 + aw + gap, 200, aw, ah, "Libérer", "coincé : ramené au spawn", 0x5FD08A)
    action(e, x0 + 2 * (aw + gap), 200, aw, ah, "Ses sanctions", "avertissements, mutes", 0x5FD08A)
    raison(e, x0, 242, w)
    ab.bouton(e, GW - M - 120, 296, 120, 22)
    e.c("Rendre muet 1 h", GW - M - 60, 307, BOUTON, WHITE)
    e.m("Chaque action est notée (qui, quand, pourquoi) et envoyée au salon du staff sur Discord.", x0, 307, PETIT, FAINT)
    pied(e, [("P", "Panel"), ("ÉCHAP", "Fermer"), ("ENTRÉE", "Valider")], "Helpeur · 6 actions")
    e.sauver(dossier / "1_panel_helpeur.png")


def maquette_admin(dossier):
    e = ab.Ecran(ab.fond())
    entete(e, "admin", "Ton grade permet 31 actions, rangées par onglet.", ["Joueurs", "Modération", "Monde", "Événements", "Serveur",
                                                                                "Historique"], 4)
    x0, w = M, GW - 2 * M
    cols = [("SERVEUR", [("Redémarrage doux", "compte à rebours, reconnexion seule", 0xFF8A3D),
                         ("Recharger les données", "loot, prix, quêtes, zones", 0xFF8A3D),
                         ("Annonce à tous", "titre à l'écran + chat", 0xFF8A3D)]),
            ("PERFORMANCES", [("État du serveur", "TPS, ms par tick, mémoire", 0x4FA3FF),
                              ("Analyser 60 s", "quel mod fait ramer", 0x4FA3FF),
                              ("Analyser le jeu d'un joueur", "ses FPS, ce qui le ralentit", 0x4FA3FF)]),
            ("NETTOYAGE", [("Objets au sol", "dans un rayon de 64 blocs", 0x5FD08A),
                           ("Cadavres trop vieux", "plus de 10 min", 0x5FD08A),
                           ("Zombies autour", "rayon de 48 blocs", 0x5FD08A)])]
    cw = (w - 16) / 3
    for i, (titre, acts) in enumerate(cols):
        cx = x0 + i * (cw + 8)
        ab.verre(e, cx, 82, cw, 200)
        e.texte(titre, cx + 10, 92, MINI, MUTED)
        for k, (t1, t2, c) in enumerate(acts):
            action(e, cx + 8, 106 + k * 40, cw - 16, 32, t1, t2, c)
        e.texte("+ 2 autres", cx + 10, 234, PETIT, FAINT)
    ab.verre(e, x0, 290, w, 44)
    e.texte("DERNIÈRES ACTIONS DU STAFF", x0 + 10, 298, MINI, MUTED)
    e.texte("21:42  Lina (Helpeur) a rendu muet xXSniperXx 1 h — « Spam »", x0 + 10, 310, PETIT, LIGHT)
    e.texte("21:30  Matteo (Modérateur) a expulsé Zed — « Insultes »", x0 + 10, 321, PETIT, OPTION)
    pied(e, [("P", "Panel"), ("ÉCHAP", "Fermer")], "Admin · 31 actions")
    e.sauver(dossier / "2_panel_admin.png")


def maquette_permissions(dossier):
    e = ab.Ecran(ab.fond())
    entete(e, "fondateur", "Fondateur seulement : coche ce que chaque grade du staff peut faire. Gardé à vie, sans redémarrer.",
           ["Joueurs", "Modération", "Monde", "Événements", "Serveur", "Permissions"], 5)
    ab.verre(e, M, 82, GW - 2 * M, 254)
    rangs = ["guide", "helpeur", "moderateur", "admin", "fondateur"]
    colx = [300 + i * 64 for i in range(5)]
    for cx, r in zip(colx, rangs):
        nom, c = GRADES[r]
        e.c(nom, cx, 94, PETIT_GRAS, ab.mix(c, WHITE, 0.3))
    lignes = [("Répondre aux questions (/aide staff)", [1, 1, 1, 1, 1]), ("Avertir", [0, 1, 1, 1, 1]), ("Rendre muet (1 h au plus)", [0, 1, 1, 1, 1]),
              ("Rendre muet (toute durée)", [0, 0, 1, 1, 1]), ("Expulser", [0, 1, 1, 1, 1]), ("Bannir temporairement", [0, 0, 1, 1, 1]),
              ("Bannir (compte + PC)", [0, 0, 0, 1, 1]), ("Aller voir / se cacher", [0, 1, 1, 1, 1]),
              ("Voir l'inventaire d'un joueur", [0, 0, 1, 1, 1]), ("Modifier l'inventaire d'un joueur", [0, 0, 0, 1, 1]),
              ("Événements et boss", [0, 0, 0, 1, 1]), ("Redémarrage doux, recharger les données", [0, 0, 0, 1, 1]),
              ("Donner un grade", [0, 0, 0, 0, 1])]
    y = 106
    for i, (lab, cases) in enumerate(lignes):
        if i % 2 == 0:
            e.rect(M + 4, y - 1, GW - M - 4, y + 16, WHITE, 6)
        e.m(lab, M + 12, y + 7.5, TEXTE, LIGHT)
        for cx, v, r in zip(colx, cases, rangs):
            c = GRADES[r][1]
            if v:
                e.rect(cx - 5, y + 2.5, cx + 5, y + 12.5, c, 70)
                e.border(cx - 5, y + 2.5, 10, 10, c, 200)
                e.coche(cx - 3.2, y + 4.2, 6.4, 1.1, WHITE)
            else:
                e.border(cx - 5, y + 2.5, 10, 10, WHITE, 50)
        y += 17
    pied(e, [("CLIC", "Cocher"), ("ÉCHAP", "Fermer")], "Seul le Fondateur voit cet onglet")
    e.sauver(dossier / "3_permissions.png")


def ligne_chat(e, x, y, canal, nom, msg, alpha=255, grade=None):
    tags = {"P": (0xB0B6BF, "PROX"), "G": (0xFFFFFF, "GLOBAL"), "C": (0x6FD08C, "CLAN")}
    c, tag = tags[canal]
    px = x
    w = e.largeur(tag, MICRO) + 4
    e.rect(px, y - 3.5, px + w, y + 3.5, c, int(40 * alpha / 255))
    e.c(tag, px + w / 2, y, MICRO, c, alpha)
    px += w + 3
    if grade:
        gn, gc = grade
        px += e.m(gn, px, y, CHAT_GRAS, gc, alpha) + 3
    px += e.m(nom, px, y, CHAT_GRAS, LIGHT, alpha) + 3
    e.m(msg, px, y, CHAT, WHITE if canal != "P" else OPTION, alpha)


def hud_bas(e):
    # barre d'objets DayZ simplifiée (repère de place, pas le vrai dessin)
    for i in range(6):
        x = GW / 2 - 66 + i * 22
        e.rect(x, GH - 26, x + 20, GH - 6, 0x0D0F14, 150)
        e.border(x, GH - 26, 20, 20, WHITE, 30)


def notif(e):
    x, y, w, h = 10, 10, 168, 28
    ab.verre(e, x, y, w, h, a=185)
    e.rect(x, y, x + 1.6, y + h, 0xF2C14E, 255)
    e.texte("EXTRACTIONS OUVERTES", x + 8, y + 6, MICRO, 0xF2C14E)
    e.texte("Extraction 3 · 7 · 12 · 18 — pendant 1 h", x + 8, y + 15, PETIT_GRAS, WHITE)


def maquette_chat(dossier, ouvert):
    e = ab.Ecran(fond_jeu())
    hud_bas(e)
    notif(e)
    x, w = 10, 200
    msgs = [("G", "Lina", "quelqu'un vend des fragments verts ?", ("[Rescapé]", 0xE39B5E)), ("C", "Matteo", "je suis à Tcherno, venez", None),
            ("P", "Sacha_77", "t'es où ? je t'entends pas", None), ("G", "Hugo", "gg pour le drapeau", None),
            ("C", "Kevin_92", "base attaquée !! côté nord", None), ("P", "NoaPlays", "baisse-toi, il y a un sniper", None)]
    if ouvert:
        bas = GH - 40
        ab.verre(e, x - 4, bas - 6 - len(msgs) * 10 - 4, w + 8, len(msgs) * 10 + 8, a=150)
        for i, m in enumerate(msgs):
            ligne_chat(e, x, bas - 6 - (len(msgs) - i) * 10 + 5, m[0], m[1], m[2], grade=m[3])
        # onglets des canaux
        ty = bas
        tx = x - 4
        for i, (lab, n, c) in enumerate([("Proximité", "", 0xB0B6BF), ("Global", "", 0xFFFFFF), ("Clan", "2", 0x6FD08C)]):
            lw = e.largeur(lab, PETIT_GRAS) + 10 + (8 if n else 0)
            actif = i == 2
            e.rect(tx, ty, tx + lw, ty + 11, c if actif else 0x0D0F14, 60 if actif else 160)
            e.border(tx, ty, lw, 11, c, 160 if actif else 50)
            e.m(lab, tx + 5, ty + 5.5, PETIT_GRAS, WHITE if actif else OPTION)
            if n:
                e.disc(tx + lw - 6, ty + 5.5, 3, ACCENT, 255)
                e.c(n, tx + lw - 6, ty + 5.5, MICRO, WHITE)
            tx += lw + 3
        e.m("TAB pour changer de canal", tx + 4, ty + 5.5, MICRO, FAINT)
        e.rect(x - 4, ty + 14, x + w + 4, ty + 27, 0x0D0F14, 190)
        e.border(x - 4, ty + 14, w + 8, 13, 0x6FD08C, 120)
        e.m("on se rejoint au drapeau dans 5 min", x + 1, ty + 20.5, CHAT, WHITE)
        e.rect(x + 1 + e.largeur("on se rejoint au drapeau dans 5 min", CHAT) + 1, ty + 17, x + 2 + e.largeur("on se rejoint au drapeau dans 5 min", CHAT) + 1.3, ty + 24, WHITE, 220)
        e.d("Les commandes ne s'affichent jamais dans le chat", GW - 10, GH - 6, MICRO, FAINT)
        nom = "5_chat_ouvert.png"
    else:
        bas = GH - 36
        for i, m in enumerate(msgs[-4:]):
            a = [70, 130, 200, 255][i]
            ligne_chat(e, x, bas - (4 - i) * 10 + 5, m[0], m[1], m[2], alpha=a, grade=m[3])
        e.d("Chat fermé : 4 lignes, s'effacent en 8 s, cachées sous les interfaces", GW - 10, GH - 6, MICRO, FAINT)
        nom = "4_chat_ferme.png"
    e.sauver(dossier / nom)


def main():
    dossier = Path(sys.argv[1]) if len(sys.argv) > 1 else RACINE / "maquettes" / "staff_chat"
    dossier.mkdir(parents=True, exist_ok=True)
    ab.POL = ab.Polices()
    maquette_helpeur(dossier)
    maquette_admin(dossier)
    maquette_permissions(dossier)
    maquette_chat(dossier, False)
    maquette_chat(dossier, True)


if __name__ == "__main__":
    main()
