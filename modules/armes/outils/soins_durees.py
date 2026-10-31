"""Durées d'utilisation des soins du mod tactique (heralisz_heal, objet heralisz_tactical:consumable), 31/10.

Choix du propriétaire après l'ouverture du 24/10 (« ça mettait 100 ans à se mettre le heal ») : soins légers environ
1,5 s, moyens environ 2,5 s, lourds (attelle, trousses chirurgicales) 3,5 s au plus ; lait concentré (nourriture) 1,6 s
comme la nourriture de Minecraft. Sortie de l'objet (dégainer) 0,5 s au plus.

Ce script écrit, pour chaque soin du tableau DUREES :
- « use_duration » et « draw_time » de sa fiche (src/serveur/resources/data/heralisz_heal/index/consumable/<id>.json ;
  le serveur l'envoie aux joueurs : temps de soin et barre de progression changent ensemble) ;
- ses animations « use » et « draw » (src/client/resources/assets/heralisz_heal/animations/consumable/<id>.animation.json)
  resserrées à la même durée : chaque clé (rotations, positions, tailles, sons) est ramenée au même rapport, pour que le
  geste aille au bout au lieu d'être coupé quand le soin est fini.
On relance le script après tout changement de DUREES (jamais à la main dans ces fichiers). Il ne réécrit un fichier que
s'il change ; relancé sans changement, il ne fait rien.
Usage : python modules/armes/outils/soins_durees.py [--controle]   (--controle : dit ce qui changerait)
"""
import json
import re
import sys
from pathlib import Path

MOD = Path(__file__).resolve().parent.parent
FICHES = MOD / "src/serveur/resources/data/heralisz_heal/index/consumable"
ANIMS = MOD / "src/client/resources/assets/heralisz_heal/animations/consumable"

# id du soin : durée d'utilisation en ticks (20 par seconde). Entre parenthèses : avant le 25/10.
DUREES = {
    # légers : environ 1,5 s
    "ibuprofen": 30,       # (50) cachets
    "vaseline": 30,        # (57) pommade
    "goldenstar": 30,      # (112) baume
    "amoxycillin": 30,     # (145) antibiotique
    # moyens : environ 2,5 s
    "ai2": 50,             # (93) petite trousse
    "carfak": 50,          # (121) trousse de combat
    "blood_pack": 60,      # (93) poche de sang (soigne 24 et la jambe cassée)
    "bandage": 50,         # (70) bandage (arrête le saignement)
    # lourds : 3,5 s
    "attelle_bandage": 70,  # (70) attelle et bandage
    "attelle": 70,         # (70) attelle seule
    "surv12": 70,          # (160) kit chirurgical de campagne
    "cms": 70,             # (259) kit chirurgical complet
    # nourriture : 1,6 s comme Minecraft
    "condensed_milk": 32,  # (109) lait concentré
}
# sortie de l'objet en main : 0,5 s au plus (avant : 0,25 à 1 s selon le soin)
SORTIE_MAX = 10


def nombre(x):
    if isinstance(x, float):
        r = repr(x)
        if "e" in r or "E" in r:
            r = format(x, ".10f").rstrip("0").rstrip(".")
        return r
    return json.dumps(x, ensure_ascii=False)


def ecrire_json(o, style, ind=0):
    """Même mise en forme que les animations d'origine : tabulations, listes sur une ligne."""
    if style != "\t":
        return json.dumps(o, ensure_ascii=False, indent=style)
    t = "\t"
    if isinstance(o, dict):
        if not o:
            return "{}"
        lignes = [f"{t * (ind + 1)}{json.dumps(k, ensure_ascii=False)}: {ecrire_json(v, style, ind + 1)}"
                  for k, v in o.items()]
        return "{\n" + ",\n".join(lignes) + "\n" + t * ind + "}"
    if isinstance(o, list):
        return "[" + ", ".join(ecrire_json(x, style, ind) for x in o) + "]"
    return nombre(o)


def temps(k):
    try:
        return float(k)
    except ValueError:
        return None


def resserrer_cles(d, f):
    """Clés de temps d'un dictionnaire (« 0.2333 » : {...}) multipliées par f, ordre gardé, jamais deux fois la même."""
    if not isinstance(d, dict) or not d or any(temps(k) is None for k in d):
        return d
    sortie, pris = {}, set()
    for k, v in d.items():
        t = round(temps(k) * f, 4)
        while t in pris:
            t = round(t + 0.0001, 4)
        pris.add(t)
        sortie[nombre(float(t))] = v
    return sortie


def resserrer(anim, duree_s):
    longueur = anim.get("animation_length")
    if not longueur or abs(longueur - duree_s) < 0.0005:
        return False
    f = duree_s / longueur
    for os_ in anim.get("bones", {}).values():
        if isinstance(os_, dict):
            for canal in list(os_):
                os_[canal] = resserrer_cles(os_[canal], f)
    if "sound_effects" in anim:
        anim["sound_effects"] = resserrer_cles(anim["sound_effects"], f)
    anim["animation_length"] = round(duree_s, 4)
    return True


def ecrire(p, texte, crlf):
    """Garde les fins de ligne du fichier d'origine (certains fichiers des gunpacks sont en CRLF)."""
    with open(p, "w", encoding="utf-8", newline="\r\n" if crlf else "\n") as f:
        f.write(texte)


def fiche(id_, duree, controle):
    p = FICHES / f"{id_}.json"
    texte = p.read_text(encoding="utf-8")
    data = json.loads(texte)["data"]
    sortie = min(data.get("draw_time", 0), SORTIE_MAX)
    neuf = re.sub(r'("use_duration":\s*)\d+', lambda m: f"{m.group(1)}{duree}", texte, count=1)
    neuf = re.sub(r'("draw_time":\s*)\d+', lambda m: f"{m.group(1)}{sortie}", neuf, count=1)
    if neuf != texte:
        print(f"{id_} : utilisation {data.get('use_duration')} -> {duree} ticks, sortie {data.get('draw_time')} -> {sortie}")
        if not controle:
            ecrire(p, neuf, b"\r\n" in p.read_bytes())
    return sortie


def animations(id_, duree, sortie, controle):
    p = ANIMS / f"{id_}.animation.json"
    texte = p.read_text(encoding="utf-8")
    style = "\t" if texte.startswith("{\n\t") else len(re.match(r"\{\n( *)", texte).group(1))
    o = json.loads(texte)
    a = o["animations"]
    change = False
    if "use" in a:
        change |= resserrer(a["use"], duree / 20)
    if sortie == SORTIE_MAX and "draw" in a and a["draw"].get("animation_length", 0) > sortie / 20 + 0.0005:
        change |= resserrer(a["draw"], sortie / 20)
    if change:
        neuf = ecrire_json(o, style) + ("\n" if texte.endswith("\n") else "")
        print(f"{id_} : animations « use » / « draw » resserrées")
        if not controle:
            ecrire(p, neuf, b"\r\n" in p.read_bytes())


def main():
    controle = "--controle" in sys.argv
    for id_, duree in DUREES.items():
        sortie = fiche(id_, duree, controle)
        animations(id_, duree, sortie, controle)


if __name__ == "__main__":
    main()
