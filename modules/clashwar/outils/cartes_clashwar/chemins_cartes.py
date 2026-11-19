"""Chemins des outils des cartes du ClashWar, relatifs au dépôt : données du module (structures/ et cartes/, où les
générateurs écrivent) et dossier de sortie des images et des mesures (build/, hors de Git).

Outils (Python 3.10 ou plus, avec numpy, scipy et Pillow : pip install numpy scipy pillow) :
    python generer_garnison.py         refait la carte (structures/ et cartes/ du module) ; de même generer_depot.py,
                                       generer_kombinat.py, generer_port.py
    python valider_xl.py               chaque bloc existe (Minecraft, nos décors, cfm), propriétés comprises
    python zones_xl.py                 endroits hauts atteignables ou fermés, sites de bombe, chemin d'un spawn à l'autre
    python analyse.py                  mesures (blocs qui flottent, abris, lignes de vue, spawns qui se voient)
    python diag_xl.py <carte> [48]     où sont les longues lignes de vue et le sol à découvert
    python metriques_xl.py             tableau des mesures (build/cartes_clashwar/metriques)
    python planche_cartes.py           images : vues 3D, vue de dessus, plan annoté (build/cartes_clashwar/)
Après un changement de carte : générateur, puis valider_xl.py, zones_xl.py et analyse.py (0 problème, rien qui flotte,
aucun sol à plus de 6 blocs d'un abri, aucune ligne de vue de 64 blocs), puis nettoyer_structures.py --verifier.
"""
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[4]
DONNEES = DEPOT / "modules" / "clashwar" / "src" / "serveur" / "resources" / "data" / "heraliz_clashwar"
SORTIE = DEPOT / "build" / "cartes_clashwar"
POLICES = DEPOT / "modules" / "noyau" / "src" / "client" / "resources" / "assets" / "heraliz_noyau" / "font"
# les quatre cartes du tournoi (une par format)
CARTES = ["clashwar_1v1_depot", "clashwar_2v2_kombinat", "clashwar_3v3_port", "clashwar_4v4_garnison"]
