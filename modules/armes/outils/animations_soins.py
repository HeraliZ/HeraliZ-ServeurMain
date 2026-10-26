"""Animations en main du Bandage et de l'Attelle (26/10, demande du propriétaire : « chaque objet doit avoir une animation
adaptée et distincte » ; avant, les deux jouaient l'animation de la trousse CAR accélérée, attelle_bandage).

    python modules/armes/outils/animations_soins.py

Écrit assets/heralisz_heal/animations/consumable/bandage.animation.json et attelle.animation.json (format Bedrock de
TaCZ, mêmes os que le modèle de la trousse CAR repris par soins_rendus.py : mag_and_lefthand, gun_and_righthand, medic,
bandage, bone, camera) et branche chaque affichage (display/consumable/<nom>.json, clé « animation »). Les animations de
sortie, de repos, d'inspection et de rangement restent celles d'attelle_bandage ; seule l'utilisation (« use », 3,5 s =
use_duration de 70 ticks) change :
- Bandage : l'avant-bras gauche se lève en travers de l'écran, la main droite prend la bande et l'enroule trois fois
  autour du bras (la bande tourne sur elle-même), puis serre et noue ;
- Attelle : les deux mains descendent vers la jambe en tenant les planchettes, les posent et appuient, la main droite
  serre trois fois la sangle, appui final, retour.
Jamais à la main : changer les poses et les temps ci-dessous, puis relancer. Aperçu en jeu seulement (personne ne l'a vu
avant le 26/10 : à valider par le propriétaire).
"""
import json
import math
from pathlib import Path

ASSETS = Path(__file__).resolve().parents[1] / "src/client/resources/assets/heralisz_heal"
BASE = ASSETS / "animations/consumable/attelle_bandage.animation.json"
DUREE = 3.5
PAS = 0.05

# poses (degrés, pixels) relevées dans static_idle et use de la trousse CAR
G_REPOS = ([-211.66, -4.2, -188.35], [11.33026, -6.39933, -7.45419])
D_REPOS = ([139.09163, -23.07516, -135.37006], [-12.9462, -9.78867, -5.71998])
G_BRAS = ([-168.0, 9.0, -242.0], [7.2, -9.2, -3.6])      # avant-bras gauche en travers de l'écran
D_CENTRE = ([158.0, -10.0, -108.0], [-3.4, -4.4, -7.4])  # main droite au-dessus de l'avant-bras
G_BAS = ([-186.0, 6.0, -214.0], [8.6, -14.2, -9.6])      # mains vers la jambe (attelle)
D_BAS = ([151.0, -16.0, -121.0], [-7.6, -14.6, -9.4])


def mix(a, b, t):
    return [x + (y - x) * t for x, y in zip(a, b)]


def lisse(t):
    t = max(0.0, min(1.0, t))
    return t * t * (3 - 2 * t)


def cle(v):
    return {"post": [round(x, 4) for x in v], "lerp_mode": "catmullrom"}


def piste(fonction):
    """{temps: clé} échantillonné tous les PAS sur DUREE."""
    out = {}
    n = int(round(DUREE / PAS))
    for i in range(n + 1):
        t = round(i * PAS, 4)
        out[f"{t:g}"] = cle(fonction(t))
    return out


def vers(pose_a, pose_b, t0, t1, t):
    k = lisse((t - t0) / (t1 - t0))
    return mix(pose_a[0], pose_b[0], k), mix(pose_a[1], pose_b[1], k)


def visibilite(entree, sortie):
    """Échelle 0 / 1 : visible entre entree et sortie (bascule en 0,02 s)."""
    return {"0": [0, 0, 0] if entree > 0 else [1, 1, 1],
            f"{max(0.0, entree - 0.02):g}": [0, 0, 0] if entree > 0 else [1, 1, 1],
            f"{entree:g}": [1, 1, 1], f"{sortie:g}": [1, 1, 1], f"{sortie + 0.02:g}": [0, 0, 0]}


def bandage():
    TOURS, T0, T1 = 3, 0.6, 2.8

    def gauche(t):
        if t < 0.4:
            return vers(G_REPOS, G_BRAS, 0.0, 0.4, t)
        if t < T1:
            a = 2 * math.pi * TOURS * max(0.0, t - T0) / (T1 - T0)
            r, p = G_BRAS
            return [r[0], r[1], r[2] + 3 * math.sin(a + math.pi)], [p[0], p[1] + 0.3 * math.sin(a), p[2]]
        if t < 3.1:
            return G_BRAS
        return vers(G_BRAS, G_REPOS, 3.1, DUREE, t)

    def droite(t):
        if t < 0.55:
            return vers(D_REPOS, D_CENTRE, 0.0, 0.55, t)
        r, p = D_CENTRE
        if t < T1:
            a = 2 * math.pi * TOURS * max(0.0, t - T0) / (T1 - T0)
            return ([r[0] + 6 * math.cos(a), r[1], r[2] + 14 * math.sin(a)],
                    [p[0] + 0.6 * math.sin(a), p[1] + 1.7 * math.sin(a), p[2] + 1.7 * math.cos(a)])
        if t < 3.1:   # serrer et nouer : la main tire vers la droite puis revient
            k = math.sin(math.pi * (t - T1) / 0.3)
            return [r[0], r[1] - 8 * k, r[2] - 20 * k], [p[0] - 3.5 * k, p[1] + 0.6 * k, p[2]]
        return vers(D_CENTRE, D_REPOS, 3.1, DUREE, t)

    def bande(t):   # la bande tourne sur elle-même pendant l'enroulement
        return [720 * lisse((t - T0) / (T1 - T0)), 0, 0]

    def camera(t):
        a = 2 * math.pi * 1.5 * t / DUREE
        return [1.2 * math.sin(a) * lisse(t / 0.4) * (1 - lisse((t - 3.1) / 0.4)), 0, 0.5 * math.cos(a) * lisse(t / 0.4) * (1 - lisse((t - 3.1) / 0.4))]

    return {
        "loop": "hold_on_last_frame", "animation_length": DUREE,
        "bones": {
            "mag_and_lefthand": {"rotation": piste(lambda t: gauche(t)[0]), "position": piste(lambda t: gauche(t)[1])},
            "gun_and_righthand": {"rotation": piste(lambda t: droite(t)[0]), "position": piste(lambda t: droite(t)[1])},
            "righthand": {"scale": [1, 1.5, 1]}, "lefthand": {"scale": [1, 1.5, 1]},
            # rouleau d'abord dans la main gauche (medic), puis donné à la main droite (bandage) pendant l'enroulement
            "medic": {"rotation": [2.81097, -8.38675, 86.47373], "position": [-4.3125, 10.1625, 8.3625],
                      "scale": {"0": [1, 1, 1], "0.43": [1, 1, 1], "0.45": [0, 0, 0], "3.3": [0, 0, 0], "3.32": [1, 1, 1]}},
            "bandage": {"rotation": piste(bande), "scale": visibilite(0.45, 3.3)},
            "camera": {"rotation": piste(camera)},
        },
        "sound_effects": {
            "0.15": {"effect": "heralisz_heal:consumable/item_cloth_generic_pickup"},
            "0.7": {"effect": "heralisz_heal:consumable/bandage_use2"},
            "1.5": {"effect": "heralisz_heal:consumable/bandage_use2"},
            "2.3": {"effect": "heralisz_heal:consumable/bandage_use2"},
            "2.9": {"effect": "heralisz_heal:consumable/item_rollupkit_04_close"},
        },
    }


def attelle():
    SERRAGES = (1.35, 1.8, 2.25)

    def gauche(t):
        if t < 0.5:
            return vers(G_REPOS, G_BAS, 0.0, 0.5, t)
        r, p = G_BAS
        if t < 1.2:   # poser les planchettes : deux appuis
            k = max(0.0, math.sin(math.pi * (t - 0.5) / 0.35))
            return [r[0] + 4 * k, r[1], r[2]], [p[0] - 0.8 * k, p[1] - 1.0 * k, p[2]]
        if t < 2.6:   # tenir pendant le serrage : petites secousses
            k = sum(max(0.0, 1 - abs(t - s) / 0.12) for s in SERRAGES)
            return [r[0] + 2 * k, r[1], r[2] - 2 * k], [p[0], p[1] + 0.4 * k, p[2]]
        if t < 3.0:   # appui final
            k = math.sin(math.pi * (t - 2.6) / 0.4)
            return [r[0] + 5 * k, r[1], r[2]], [p[0] - 0.5 * k, p[1] - 1.4 * k, p[2]]
        return vers(G_BAS, G_REPOS, 3.0, DUREE, t)

    def droite(t):
        if t < 0.5:
            return vers(D_REPOS, D_BAS, 0.0, 0.5, t)
        r, p = D_BAS
        if t < 1.2:
            k = max(0.0, math.sin(math.pi * (t - 0.5) / 0.35))
            return [r[0] + 4 * k, r[1], r[2]], [p[0] + 0.8 * k, p[1] - 1.0 * k, p[2]]
        if t < 2.6:   # trois serrages de la sangle : la main tire vers l'extérieur et vers le haut
            k = sum(max(0.0, 1 - abs(t - s) / 0.18) for s in SERRAGES)
            return [r[0] - 10 * k, r[1] - 6 * k, r[2] - 14 * k], [p[0] - 3.0 * k, p[1] + 1.6 * k, p[2] + 0.4 * k]
        if t < 3.0:
            k = math.sin(math.pi * (t - 2.6) / 0.4)
            return [r[0] + 5 * k, r[1], r[2]], [p[0] + 0.5 * k, p[1] - 1.4 * k, p[2]]
        return vers(D_BAS, D_REPOS, 3.0, DUREE, t)

    def camera(t):   # léger regard vers le bas pendant la pose
        k = lisse(t / 0.5) * (1 - lisse((t - 3.0) / 0.5))
        return [3.0 * k, 0, 0.4 * math.sin(2 * math.pi * t / DUREE) * k]

    return {
        "loop": "hold_on_last_frame", "animation_length": DUREE,
        "bones": {
            "mag_and_lefthand": {"rotation": piste(lambda t: gauche(t)[0]), "position": piste(lambda t: gauche(t)[1])},
            "gun_and_righthand": {"rotation": piste(lambda t: droite(t)[0]), "position": piste(lambda t: droite(t)[1])},
            "righthand": {"scale": [1, 1.5, 1]}, "lefthand": {"scale": [1, 1.5, 1]},
            "medic": {"rotation": [2.81097, -8.38675, 86.47373], "position": [-4.3125, 10.1625, 8.3625]},
            # sangle et boucle (bandage / bone) visibles pendant le serrage
            "bandage": {"scale": visibilite(1.2, 2.65)},
            "camera": {"rotation": piste(camera)},
        },
        "sound_effects": {
            "0.4": {"effect": "heralisz_heal:consumable/item_rollupkit_01_fastex1"},
            "0.85": {"effect": "heralisz_heal:consumable/item_cloth_generic_pickup"},
            "1.35": {"effect": "heralisz_heal:consumable/item_rollupkit_02_fastex2"},
            "1.8": {"effect": "heralisz_heal:consumable/item_rollupkit_02_fastex2"},
            "2.25": {"effect": "heralisz_heal:consumable/item_rollupkit_02_fastex2"},
            "2.8": {"effect": "heralisz_heal:consumable/item_rollupkit_04_close"},
        },
    }


def main():
    base = json.loads(BASE.read_text(encoding="utf-8"))
    for nom, use in (("bandage", bandage()), ("attelle", attelle())):
        anims = {k: v for k, v in base["animations"].items() if k != "use"}
        anims["use"] = use
        out = {"format_version": base.get("format_version", "1.8.0"), "animations": anims}
        f = ASSETS / f"animations/consumable/{nom}.animation.json"
        f.write_text(json.dumps(out, indent=1) + "\n", encoding="utf-8")
        d = ASSETS / f"display/consumable/{nom}.json"
        aff = json.loads(d.read_text(encoding="utf-8"))
        aff["animation"] = f"heralisz_heal:consumable/{nom}"
        d.write_text(json.dumps(aff, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
        print(f"[animations] {nom} : {f.name}, affichage branché")


if __name__ == "__main__":
    main()
