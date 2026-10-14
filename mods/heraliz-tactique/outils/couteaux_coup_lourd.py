"""Animation du coup puissant (clic droit) des couteaux TaCZ, différente du coup rapide (choix du propriétaire, 14/10).

Les couteaux des gunpacks n'ont qu'un coup, « melee_push ». Ce script en tire pour chacun des 5 modèles (ct_knife,
t_knife, karambit, m9, butterfly_knife) un coup puissant nommé « melee_stock » : le même geste, plus lent (×LENTEUR),
avec par-dessus un grand mouvement du bras (armé en arrière et en haut, puis frappe plongeante, voir ARMER / FRAPPER).
Le mod Tactique le donne à TaCZ chez le joueur (couteaux.CoupLourdClient) et le lance par le signal « bayonet_stock »
de la machine à états par défaut, qui joue « melee_stock ». Les gunpacks ne sont pas modifiés.

Écrit src/client/resources/assets/heraliz_tactique/couteaux/<modèle>.animation.json.
Usage : python mods/heraliz-tactique/outils/couteaux_coup_lourd.py
"""
import json
import os

ICI = os.path.dirname(os.path.abspath(__file__))
PROJET = os.path.normpath(os.path.join(ICI, ".."))
DEPOT = os.path.normpath(os.path.join(PROJET, "..", ".."))
SOURCE = os.path.join(DEPOT, "serveur", "tacz", "arkaniaz_pack_4_converted", "assets", "arkaniaz", "animations")
SORTIE = os.path.join(PROJET, "src", "client", "resources", "assets", "heraliz_tactique", "couteaux")

# modèle -> os qui porte le bras et la lame
MODELES = {"ct_knife": "right_elbow", "t_knife": "right_elbow", "m9": "right_elbow", "butterfly_knife": "right_elbow",
           "karambit": "hand_and_knife"}
LENTEUR = 1.25
# surcouche de rotation du bras (degrés, x y z) aux temps donnés (secondes, après ralenti) : armé, frappe, retour
SURCOUCHE = [(0.0, (0, 0, 0)), (0.10, (-38, 0, 14)), (0.22, (30, 0, -22)), (0.42, (14, 0, -8)), (0.80, (0, 0, 0))]
# surcouche de position (px) : recul en armant, plongée en frappant
SURCOUCHE_POS = [(0.0, (0, 0, 0)), (0.10, (0, 1.6, 1.2)), (0.22, (0, -1.4, -2.0)), (0.80, (0, 0, 0))]


def valeur(k):
    if isinstance(k, dict):
        return k.get("post", k.get("pre"))
    return k


def echantillon(canal, t):
    """Valeur d'un canal (clés « temps » -> valeur) au temps t, en ligne droite entre deux clés."""
    if not isinstance(canal, dict):
        return list(canal)
    cles = sorted((float(x), valeur(v)) for x, v in canal.items())
    if t <= cles[0][0]:
        return list(cles[0][1])
    for (t0, v0), (t1, v1) in zip(cles, cles[1:]):
        if t0 <= t <= t1:
            f = 0.0 if t1 == t0 else (t - t0) / (t1 - t0)
            return [a + (b - a) * f for a, b in zip(v0, v1)]
    return list(cles[-1][1])


def ralentir(canal):
    if not isinstance(canal, dict):
        return canal
    return {str(round(float(x) * LENTEUR, 4)): v for x, v in canal.items()}


def ajouter(canal, surcouche, longueur):
    """Canal ralenti + surcouche, réécrit sur les temps des deux (lissé en catmullrom)."""
    canal = ralentir(canal) if canal is not None else {"0.0": [0, 0, 0]}
    if not isinstance(canal, dict):
        canal = {"0.0": canal}
    temps = sorted({float(x) for x in canal} | {t for t, _ in surcouche if t <= longueur})
    out = {}
    for t in temps:
        base = echantillon(canal, t)
        sur = echantillon({str(a): list(b) for a, b in surcouche}, t)
        out[str(round(t, 4))] = {"post": [round(a + b, 4) for a, b in zip(base, sur)], "lerp_mode": "catmullrom"}
    return out


def main():
    os.makedirs(SORTIE, exist_ok=True)
    for modele, os_bras in MODELES.items():
        with open(os.path.join(SOURCE, modele + ".animation.json"), encoding="utf-8") as f:
            src = json.load(f)
        push = src["animations"]["melee_push"]
        longueur = round(float(push.get("animation_length", 1.0)) * LENTEUR, 4)
        os_ = {}
        for nom, canaux in push.get("bones", {}).items():
            os_[nom] = {c: ralentir(v) for c, v in canaux.items()}
        bras = os_.setdefault(os_bras, {})
        orig = push.get("bones", {}).get(os_bras, {})
        bras["rotation"] = ajouter(orig.get("rotation"), SURCOUCHE, longueur)
        bras["position"] = ajouter(orig.get("position"), SURCOUCHE_POS, longueur)
        clip = {"loop": False, "animation_length": longueur, "bones": os_}
        if "sound_effects" in push:
            clip["sound_effects"] = ralentir(push["sound_effects"])
        sortie = {"format_version": src.get("format_version", "1.8.0"), "animations": {"melee_stock": clip}}
        chemin = os.path.join(SORTIE, modele + ".animation.json")
        with open(chemin, "w", encoding="utf-8", newline="\n") as f:
            json.dump(sortie, f, ensure_ascii=False, indent=1)
            f.write("\n")
        print("écrit", os.path.relpath(chemin, DEPOT), longueur, "s")


if __name__ == "__main__":
    main()
