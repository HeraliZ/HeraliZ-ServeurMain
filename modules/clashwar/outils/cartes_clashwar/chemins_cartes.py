"""Chemins des outils des cartes du ClashWar, relatifs au dépôt : données du module (structures/ et cartes/, où les
générateurs écrivent) et dossier de sortie des images et des mesures (build/, hors de Git)."""
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[4]
DONNEES = DEPOT / "modules" / "clashwar" / "src" / "serveur" / "resources" / "data" / "heraliz_clashwar"
SORTIE = DEPOT / "build" / "cartes_clashwar"
POLICES = DEPOT / "modules" / "noyau" / "src" / "client" / "resources" / "assets" / "heraliz_noyau" / "font"
# les quatre cartes du tournoi (une par format)
CARTES = ["clashwar_1v1_depot", "clashwar_2v2_kombinat", "clashwar_3v3_port", "clashwar_4v4_garnison"]
