"""Metrics table of the tournament maps, same analysis code for all (analyse.py + reachability acces_xl.py). Writes
build/cartes_clashwar/metriques/metriques_cartes.json and .md. Usage: python metriques_xl.py [id ...]"""
import json
import os
import sys

import numpy as np

import acces_xl
import analyse as an
from chemins_cartes import CARTES, SORTIE
from rendu import niveau_sol


def mesurer(ident):
    r = an.analyser(ident)
    m, st, vu = acces_xl.accessibles(ident)
    sol = niveau_sol(m["meta"]) + 1
    ys = np.nonzero(vu)[1]
    r["hauteur_max_atteignable"] = int(ys.max() - sol) if len(ys) else 0
    r["atteignable_haut_pct"] = round(100.0 * np.mean(ys > sol + 2), 1) if len(ys) else 0.0
    x0, x1, z0, z1 = an.bornes_xz(m["meta"], m["size"])
    r["cote_jouable"] = max(x1 - x0 + 1, z1 - z0 + 1)
    r["surface_jouable"] = (x1 - x0 + 1) * (z1 - z0 + 1)
    r["ligne_max_pct_cote"] = round(100.0 * max(r["plus_longue_ligne_ns"], r["plus_longue_ligne_eo"]) / r["cote_jouable"])
    return r


LIGNES = [
    ("Taille (blocs)", lambda r: f"{r['taille'][0]} × {r['taille'][2]}"),
    ("Surface jouable (m²)", lambda r: f"{r['surface_jouable']:,}".replace(",", " ")),
    ("Densité : blocs au-dessus du sol par m² (≥ 1,7)", lambda r: f"{r['blocs_par_m2']:.2f}".replace(".", ",")),
    ("Types de blocs (≥ 60)", lambda r: str(r["types_blocs"])),
    ("Sol dehors à plus de 6 blocs d'un abri (%)", lambda r: f"{r['exterieur_loin_6_pct']:.1f}".replace(".", ",")),
    ("Distance moyenne à l'abri le plus proche (blocs)", lambda r: f"{r['dist_couvert_moy']:.1f}".replace(".", ",")),
    ("Plus longue ligne de vue droite, nord-sud / est-ouest", lambda r: f"{r['plus_longue_ligne_ns']} / {r['plus_longue_ligne_eo']}"),
    ("… en % du côté de la carte", lambda r: f"{r['ligne_max_pct_cote']} %"),
    ("Couloirs de vue de 64 blocs et plus", lambda r: str(r["couloirs_ns_64"] + r["couloirs_eo_64"])),
    ("Cases debout en hauteur (> 2 blocs), tout compris (%)", lambda r: f"{r['debout_haut_pct']:.1f}".replace(".", ",")),
    ("Cases atteignables en hauteur (> 2 blocs) (%)", lambda r: f"{r['atteignable_haut_pct']:.1f}".replace(".", ",")),
    ("Hauteur max atteignable à pied (blocs)", lambda r: f"+{r['hauteur_max_atteignable']}"),
    ("Blocs flottants (pièces, face / arête)", lambda r: f"{r['flottants_6']['pieces']} / {r['flottants_26']['pieces']}"),
    ("Paires spawn A / spawn B qui se voient", lambda r: r["paires_spawnA_voit_spawnB"]),
    ("Spawn A voit le site A / le site B", lambda r: f"{r['spawnA_voit_siteA']} / {r['spawnA_voit_siteB']}"),
    ("Spawn B voit un site", lambda r: "oui" if (r["vue_spawnB_siteA"] or r["vue_spawnB_siteB"]) else "non"),
    ("Sol visible depuis le spawn A (%)", lambda r: f"{r['visible_depuis_spawnA_pct']:.1f}".replace(".", ",")),
    ("Abris autour des sites A / B (%)", lambda r: f"{r['couvert_autour_siteA_pct']:.0f} / {r['couvert_autour_siteB_pct']:.0f}"),
    ("Distance spawn B → site le plus proche (blocs)", lambda r: f"{r['dist_spawnB_sites']:.0f}"),
]


def main(cartes):
    res = [mesurer(c) for c in cartes]
    dossier = os.path.join(str(SORTIE), "metriques")
    os.makedirs(dossier, exist_ok=True)
    with open(os.path.join(dossier, "metriques_cartes.json"), "w", encoding="utf-8") as f:
        json.dump(res, f, ensure_ascii=False, indent=1, default=str)
    tete = "| Mesure | " + " | ".join(c.replace("clashwar_", "") for c in cartes) + " |"
    lignes = [tete, "|" + "---|" * (len(cartes) + 1)]
    for nom, f in LIGNES:
        lignes.append(f"| {nom} | " + " | ".join(f(r) for r in res) + " |")
    md = "\n".join(lignes)
    with open(os.path.join(dossier, "metriques_cartes.md"), "w", encoding="utf-8") as f:
        f.write("# Mesures des cartes du ClashWar\n\n"
                "Même code de mesure pour toutes les cartes (`analyse.py`, `acces_xl.py`). Ligne de vue = à hauteur "
                "d'yeux, en ligne droite nord-sud ou est-ouest. « Atteignable » = où un joueur peut aller à pied "
                "depuis les spawns (marche, saut d'un bloc, chute de 4 au plus).\n\n" + md + "\n")
    print(md)
    return res


if __name__ == "__main__":
    main(sys.argv[1:] or CARTES)
