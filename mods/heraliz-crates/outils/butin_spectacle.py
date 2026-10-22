"""Butin de la caisse des emotes : serveur/config/heralizcrates/spectacle.json, une récompense par emote de HeraliZ Emote.

    python mods/heraliz-crates/outils/butin_spectacle.py

Lit la liste des emotes (mods/heraliz-emote/src/commun/resources/data/heraliz_emote/liste.json, écrite par
mods/heraliz-emote/outils/liste_emotes.py). Chaque récompense est l'icône de l'emote (heraliz_emote:icone, affichée dans la
roue, pas donnée) et la commande `emote debloquer {player} <id>`. Emote avec accessoires : Épique, poids 20 ; sans : Rare,
poids 30. Le tirage saute les emotes déjà débloquées par le joueur ; s'il les a toutes, la clé est refusée. À relancer
après l'ajout ou le retrait d'une emote.
"""
import json
from pathlib import Path

DEPOT = Path(__file__).resolve().parents[3]
LISTE = DEPOT / "mods/heraliz-emote/src/commun/resources/data/heraliz_emote/liste.json"
SORTIE = DEPOT / "serveur/config/heralizcrates/spectacle.json"
AIDE = ("Caisse des emotes : fichier écrit par mods/heraliz-crates/outils/butin_spectacle.py (ne pas modifier à la main). "
        "Chaque récompense débloque une emote (commande), l'icône n'est qu'affichée dans la roue. Le tirage saute les "
        "emotes déjà débloquées ; un joueur qui les a toutes ne peut plus ouvrir de clé.")


def main():
    emotes = json.loads(LISTE.read_text(encoding="utf-8"))["emotes"]
    recompenses = []
    for e in emotes:
        acc = e.get("accessoires", False)
        recompenses.append({
            "item": "heraliz_emote:icone",
            "nbt": "{CustomModelData:%d}" % e["modele"],
            "count": 1,
            "weight": 20 if acc else 30,
            "rarity": "epic" if acc else "rare",
            "name": ("&d" if acc else "&b") + e["nom"],
            "give_item": False,
            "commands": ["emote debloquer {player} " + e["id"]],
        })
    SORTIE.write_text(json.dumps({"_aide": AIDE, "rewards": recompenses}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"{len(recompenses)} récompenses -> {SORTIE.relative_to(DEPOT)}")


if __name__ == "__main__":
    main()
