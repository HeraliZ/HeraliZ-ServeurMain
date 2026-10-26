"""Quêtes journalières (26/10, demande du propriétaire) : la grande liste des quêtes et les tables de récompenses par
difficulté. Écrit serveur/config/heraliz_quetes/journalieres.json (lu par HeraliZ Quêtes, relu par /quete recharger et
/recharger), jamais à la main.

    python outils/quetes_journalieres.py              écrit le fichier (après contrôle : chances = 100 %, objets connus)
    python outils/quetes_journalieres.py --simuler    valeur moyenne d'une récompense par difficulté, 100 000 tirages

Règles du jeu (côté serveur, fr.heraliz.quetes.serveur.Journalieres) :
- chaque jour (minuit, heure de Paris), le joueur choisit UNE difficulté, définitive pour la journée ;
- il reçoit 3 propositions tirées au hasard dans la liste de cette difficulté (trois familles différentes, une seule
  quête qui demande une carte d'accès) ; il en accepte une, les deux autres disparaissent. Jamais de nouveau tirage :
  s'il n'en veut aucune, il n'a pas de quête ce jour-là ;
- quête réussie : une récompense tirée dans la table de sa difficulté (chances affichées dans le menu) ; abandon ou
  minuit avant la fin : rien. Une récompense gagnée mais pas encore tirée reste disponible après minuit.

Types d'objectifs (tous mesurés par le serveur) :
  zombies     tuer N zombies (vrais kills, comme le butin des zombies : ni zombies créés à la main, ni fermes) ;
              "tenue": "MILITAIRE" (zombies militaires), "arme": "melee" (au corps à corps)
  conteneurs  fouiller N conteneurs de loot remplis (première ouverture depuis leur remplissage ; chaque conteneur
              compte une fois par jour) ; "palier_min": 4 = T4 ou plus
  salle       fouiller un conteneur d'une salle à carte rechargée ; "couleur": verte | bleue | rouge
  extraction  réussir N extractions (de la carte au spawn par une zone d'extraction)
  vendre      vendre à Mila pour N hérals d'objets trouvés (pas ceux qu'un marchand vend)
  recycler    recycler N objets au recycleur
  livrer      rapporter N objets depuis le menu (ils sont pris) : "tag" (ex. heraliz_composants:vente) ou "niveau" (2 =
              composants de niveau II...)
  distance    parcourir N mètres à pied sur la carte (ni véhicule, ni train)
  pvp         tuer N survivants armés : pas du même clan ni de la même adresse IP, une fois par victime et par jour
Pas d'objectif lié à un événement rare (hélicoptère, largage) : une quête ne doit jamais être impossible un jour donné.
"""
import json
import random
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import loot_zombies as LZ  # noqa: E402

RACINE = Path(__file__).resolve().parent.parent
SORTIE = RACINE / "serveur/config/heraliz_quetes/journalieres.json"
C = "heraliz_composants:"
F = "heralisz_bunker:keycard_fragment_"

# ---------------------------------------------------------------------------------------------- quêtes
# (id, famille, type, paramètres). La famille sert au tirage : 3 propositions de 3 familles différentes.
QUETES = {
    "facile": [
        ("f_zombies", "zombies", "zombies", {"nombre": 35}),
        ("f_zombies_melee", "zombies", "zombies", {"nombre": 15, "arme": "melee"}),
        ("f_conteneurs", "fouille", "conteneurs", {"nombre": 20}),
        ("f_conteneurs_t2", "fouille", "conteneurs", {"nombre": 10, "palier_min": 2}),
        ("f_vendre", "commerce", "vendre", {"nombre": 300}),
        ("f_recycler", "commerce", "recycler", {"nombre": 8}),
        ("f_livrer_recup", "ressources", "livrer", {"nombre": 6, "tag": "heraliz_composants:vente"}),
        ("f_distance", "exploration", "distance", {"nombre": 3000}),
        ("f_extraction", "exploration", "extraction", {"nombre": 1}),
    ],
    "moyenne": [
        ("m_zombies", "zombies", "zombies", {"nombre": 100}),
        ("m_zombies_mil", "zombies", "zombies", {"nombre": 25, "tenue": "MILITAIRE"}),
        ("m_conteneurs", "fouille", "conteneurs", {"nombre": 60}),
        ("m_conteneurs_t4", "fouille", "conteneurs", {"nombre": 15, "palier_min": 4}),
        ("m_salle_verte", "salle", "salle", {"nombre": 1, "couleur": "verte"}),
        ("m_extraction", "exploration", "extraction", {"nombre": 3}),
        ("m_distance", "exploration", "distance", {"nombre": 8000}),
        ("m_vendre", "commerce", "vendre", {"nombre": 1500}),
        ("m_livrer_ii", "ressources", "livrer", {"nombre": 4, "niveau": 2}),
    ],
    "difficile": [
        ("d_salle_bleue", "salle", "salle", {"nombre": 1, "couleur": "bleue"}),
        ("d_zombies_mil", "zombies", "zombies", {"nombre": 60, "tenue": "MILITAIRE"}),
        ("d_zombies", "zombies", "zombies", {"nombre": 250}),
        ("d_conteneurs_t5", "fouille", "conteneurs", {"nombre": 10, "palier_min": 5}),
        ("d_conteneurs", "fouille", "conteneurs", {"nombre": 150}),
        ("d_pvp", "pvp", "pvp", {"nombre": 3}),
        ("d_livrer_iii", "ressources", "livrer", {"nombre": 3, "niveau": 3}),
        ("d_extraction", "exploration", "extraction", {"nombre": 5}),
    ],
}
# durée indicative affichée sur la carte de la difficulté (minutes)
DUREE = {"facile": 30, "moyenne": 60, "difficile": 120}

# ---------------------------------------------------------------------------------------------- récompenses
# (id, chance en %, objet ou "cle:<caisse>", nombre, nbt ou None). Un tirage par quête réussie, chances indépendantes
# d'une quête à l'autre. Une entrée « fragment_<couleur> » donne un fragment au numéro tiré au hasard (1 à 4).
# Les clés de caisse vont sur le compte du joueur (comme /heraliz givekey).
GUN = "tacz:modern_kinetic_gun"


def arme(gid, mode="SEMI", balles=0):
    return GUN, 1, '{GunId:"%s",GunFireMode:"%s",GunCurrentAmmoCount:%d}' % (gid, mode, balles)


def balles(aid, n):
    return "tacz:ammo", n, '{AmmoId:"%s"}' % aid


def soin(cid, n=1):
    return "heralisz_tactical:consumable", n, '{ConsumableId:"%s"}' % cid


def objet(i, n=1):
    return i, n, None


RECOMPENSES = {
    # petites quantités de ressources communes, quelques objets intéressants très rares
    "facile": [
        ("ferraille", 20.0) + objet(C + "ferraille", 4),
        ("poudre", 16.0) + objet(C + "bocal_poudre", 3),
        ("balles_9mm", 14.0) + balles("tacz:9mm", 30),
        ("bandages", 12.0) + soin("heralisz_heal:bandage", 3),
        ("planches", 10.0) + objet(C + "planches", 3),
        ("composant_ii", 9.0) + objet(C + "circuit_imprime_ii", 1),
        ("kit_fortune", 7.0) + objet("heraliz_usure:kit_fortune", 1),
        ("recup_rare", 6.0) + objet(C + "montre_gousset", 1),
        ("fragment_vert", 3.5) + objet("fragment_green", 1),
        ("arme_pc", 2.0) + arme("tacz:cz75"),
        ("cle_quete", 0.4) + objet("cle:quete", 1),
        ("fragment_bleu", 0.1) + objet("fragment_blue", 1),
    ],
    # niveau T3, un peu de T4, armes de progression, cartes raisonnables, quelques raretés
    "moyenne": [
        ("composants_ii", 17.0) + objet(C + "ferraille_ii", 3),
        ("balles_556", 13.0) + balles("tacz:556x45", 30),
        ("soin_rare", 11.0) + soin("heralisz_heal:carfak", 1),
        ("metal", 10.0) + objet(C + "fragments_metal", 2),
        ("arme_rare", 9.0) + arme("tacz:ump45", "AUTO"),
        ("kit_standard", 8.0) + objet("heraliz_usure:kit_standard", 1),
        ("recup_epique", 8.0) + objet(C + "chronographe", 1),
        ("composant_iii", 7.0) + objet(C + "circuit_imprime_iii", 1),
        ("fragments_verts", 7.0) + objet("fragment_green", 2),
        ("carte_verte", 5.0) + objet("heralisz_bunker:keycard_green", 1),
        ("fragment_bleu", 3.0) + objet("fragment_blue", 1),
        ("cle_camo", 1.5) + objet("cle:camo", 1),
        ("carte_bleue", 0.5) + objet("heralisz_bunker:keycard_blue", 1),
    ],
    # les meilleures récompenses (pistes du propriétaire : rouge 0,1 %, camo 5 %, gemme 1 %, bleue 10 %, AK 20 %)
    "difficile": [
        ("ak47", 20.0) + arme("tacz:ak47", "AUTO"),
        ("composants_iii", 14.0) + objet(C + "kit_pieces_iii", 2),
        ("metal_renforce", 10.0) + objet(C + "metal_renforce", 2),
        ("balles_762", 9.0) + balles("tacz:762x39", 45),
        ("arme_epique", 7.0) + arme("tacz:scar_l", "AUTO"),
        ("carte_bleue", 6.0) + objet("heralisz_bunker:keycard_blue", 1),
        ("kit_armurier", 6.0) + objet("heraliz_usure:kit_armurier", 1),
        ("carte_verte", 6.0) + objet("heralisz_bunker:keycard_green", 1),
        ("recup_legendaire", 5.0) + objet(C + "lingot_or", 1),
        ("composant_iv", 4.5) + objet(C + "circuit_imprime_iv", 1),
        ("cle_camo", 5.0) + objet("cle:camo", 1),
        ("fragment_rouge", 4.0) + objet("fragment_red", 1),
        ("cle_emotes", 2.0) + objet("cle:spectacle", 1),
        ("cle_gemme", 1.0) + objet("cle:gemme", 1),
        ("carte_rouge", 0.5) + objet("heralisz_bunker:keycard_red", 1),
    ],
}
# valeur (hérals) des cartes d'accès et des clés, pour la simulation seulement (estimations, à valider)
VALEUR_SPECIALE = {"heralisz_bunker:keycard_green": 600, "heralisz_bunker:keycard_blue": 2000, "heralisz_bunker:keycard_red": 5000,
                   "fragment_green": 150, "fragment_blue": 500, "fragment_red": 1250,
                   "cle:quete": 150, "cle:camo": 290, "cle:spectacle": 290, "cle:gemme": 390}


def valeur(e):
    _, _, o, n, nbt = e
    if o in VALEUR_SPECIALE:
        return VALEUR_SPECIALE[o] * n
    if o == "tacz:ammo":
        return LZ.valeur(o, nbt, n)
    return LZ.valeur(o, nbt, 1) * n


def controle():
    import re
    r = LZ.RARETES
    for d, l in RECOMPENSES.items():
        total = sum(e[1] for e in l)
        if abs(total - 100) > 1e-6:
            sys.exit(f"{d} : chances = {total} % (100 % attendus)")
        if len({e[0] for e in l}) != len(l):
            sys.exit(f"{d} : identifiants en double")
        for e in l:
            o, nbt = e[2], e[4]
            m = re.search(r'GunId:"([^"]+)"', nbt or "")
            if m and m.group(1) not in r["armes"]:
                sys.exit(f"{d} : arme inconnue {m.group(1)}")
            m = re.search(r'ConsumableId:"([^"]+)"', nbt or "")
            if m and m.group(1) not in r["soins"]:
                sys.exit(f"{d} : soin inconnu {m.group(1)}")
            if o.startswith(C) and o not in r["objets"]:
                sys.exit(f"{d} : composant inconnu {o}")
    for d, l in QUETES.items():
        if len({q[1] for q in l}) < 3:
            sys.exit(f"{d} : moins de 3 familles, 3 propositions différentes impossibles")


def recompense_json(e):
    i, chance, o, n, nbt = e
    out = {"id": i, "chance": chance, "nombre": n}
    if o.startswith("cle:"):
        out["cle"] = o[4:]
    elif o.startswith("fragment_"):
        out["fragment"] = o[len("fragment_"):]
    else:
        out["objet"] = o
        if nbt:
            out["nbt"] = nbt
    return out


def ecrire():
    controle()
    d = {"_note": "Écrit par outils/quetes_journalieres.py (jamais à la main). Lu par HeraliZ Quêtes (Journalieres).",
         "version": 1, "fuseau": "Europe/Paris", "difficultes": {}}
    for nom in ("facile", "moyenne", "difficile"):
        d["difficultes"][nom] = {
            "duree_minutes": DUREE[nom],
            "quetes": [dict({"id": i, "famille": f, "type": t}, **p) for i, f, t, p in QUETES[nom]],
            "recompenses": [recompense_json(e) for e in RECOMPENSES[nom]],
        }
    SORTIE.write_text(json.dumps(d, ensure_ascii=False, indent=2) + "\n", encoding="utf-8", newline="\n")
    print(f"écrit {SORTIE.relative_to(RACINE)} : " + ", ".join(f"{k} {len(v)} quêtes" for k, v in QUETES.items()))


def simuler(n=100_000):
    controle()
    rnd = random.Random(3)
    for d, l in RECOMPENSES.items():
        ev = sum(e[1] / 100 * valeur(e) for e in l)
        tir = rnd.choices(l, [e[1] for e in l], k=n)
        ev_t = sum(valeur(e) for e in tir) / n
        rares = [e for e in l if e[1] <= 5]
        print(f"{d:9} : valeur moyenne {ev:6.0f} hérals (tirages {ev_t:6.0f}) ; raretés : "
              + ", ".join(f"{e[0]} {e[1]} % (1 sur {100 / e[1]:.0f})" for e in rares))


if __name__ == "__main__":
    simuler() if "--simuler" in sys.argv else ecrire()
