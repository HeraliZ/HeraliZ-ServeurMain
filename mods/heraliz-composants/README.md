# HeraliZ Composants

Mod Forge 1.20.1 : composants de craft et objets de récupération à revendre pour HeraliZ (47 objets, sans bloc ni
recette ni loot pour l'instant).

## Organisation (projet coupé, voir CLAUDE.md section 4)

- `src/commun/java/fr/heraliz/composants/` : enregistrement des objets, onglet créatif, infobulles, raretés.
  - `ComponentDefs.java` est **généré** par `outils/build.py` : ne pas le modifier à la main.
- `src/client/resources/assets/` : modèles 3D, icônes, textures, traductions (générés).
- `src/serveur/resources/data/` : tags d'objets (générés).
- `outils/` : générateur Python des modèles, textures, icônes, traductions, tags et de `ComponentDefs.java`.
  - `composants.py` : le catalogue des objets (nom, catégorie, description, modèle 3D) ;
  - `engine.py` : matériaux (textures 16×16) et moteur de rendu des icônes 32×32 ;
  - `build.py` : écrit les fichiers générés et la planche `apercus/apercu_composants.png` ;
  - `variantes_poudre.py` : planche de propositions (choix de la poudre) ;
  - `verif_scintillement.py` : contrôle qu'aucune face ne se superpose (scintillement en jeu) ;
  - `anciens/` : anciennes versions du catalogue (pièces d'armes par tier), non utilisées ;
  - `DevSnapshots.java` : ancien outil de capture en 1re personne, gardé pour mémoire (hors du jar, pas compilé).
- `apercus/` : planches de rendu (objets, en main, variantes de la poudre).

## Modifier les objets

1. Éditer `outils/composants.py`. Raretés et couleur du nom (`HeralizRarity.java`) : `common` gris, `uncommon` vert,
   `rare` bleu, `epic` violet, `legendary` or, `mythic` rouge.
2. Regénérer (Pillow et numpy) : `python mods/heraliz-composants/outils/build.py mods/heraliz-composants`.
3. Vérifier : `python mods/heraliz-composants/outils/verif_scintillement.py mods/heraliz-composants/outils` doit
   afficher `total 0`.
4. Fabriquer et contrôler comme les autres mods : `python outils/construire.py heraliz-composants` puis
   `python outils/verifier.py heraliz-composants`.

## Tags d'objets

`heraliz_composants:armes`, `soins`, `viseurs`, `munitions`, `protection`, `divers`, `vente`, et `tous`.
