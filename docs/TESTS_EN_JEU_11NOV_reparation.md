# Essais en jeu — réparation des armes (retours du 11/11)

Chantier « réparation » : usure des armes et kits de réparation (`modules/monde`, mod `heraliz_usure`, Monde 1.2.0).
**Rien n'a été testé en jeu.** Jars joueurs et serveur de la même fabrication (canal `heraliz_usure:main` changé).

Réglages : `serverconfig/heraliz_usure-server.toml` du monde (copié depuis `serveur/defaultconfigs/` à chaque mise en
ligne, relu tout seul 2 s après un changement), aussi modifiables en jeu avec `/usure config`, onglet « Réparation ».
La rareté d'un objet est celle que montre HeraliZ Loot (couleur derrière l'objet dans les cases, ligne « Rare » de
l'infobulle ; `raretes.json`, `/rarete`).

## Point 1 — nombre de réparations selon la rareté de l'arme

Grise (Commun) 3, verte (Peu commun) 5, bleue (Rare) 8, violette (Épique) 12, légendaire 17 (Unique, aucune arme
aujourd'hui : 17). Section `[reparation.reparations_max]` du fichier ; tableau « Par rareté » de `/usure config`.

- [ ] Infobulle d'une arme neuve : nouvelle ligne « Réparations restantes : 8 / 8 » pour une bleue (AK-47, M4A1) ;
      3 / 3 pour une grise (Glock 17, M1911) ; 5 / 5 verte ; 12 / 12 violette ; 17 / 17 légendaire.
- [ ] Réparer une arme bleue avec n'importe quel kit : message « … · réparations restantes : 7 / 8 », infobulle 7 / 8.
      Avec un autre kit ensuite : 6 / 8 (chaque réparation compte une fois, quel que soit le kit).
- [ ] Une arme déjà réparée avant la mise à jour garde son compte (réparée 3 fois : 5 / 8 pour une bleue).
- [ ] À 0 / 8 : le 0 est en rouge ; dans l'atelier, « Plus aucune réparation : cette arme a atteint son maximum de
      réparations. », bouton « Réparer » grisé, le kit n'est pas consommé.
- [ ] Arme ruinée (0 %) sans réparation restante : infobulle « Ruinée pour de bon : plus aucune réparation possible ».
- [ ] Armure, masque, combinaison : se réparent toujours, sans limite.
- [ ] Infobulle d'un kit : nouvelle ligne grise « Une arme se répare un nombre limité de fois, selon sa rareté ».
- [ ] Staff : `/usure voir <joueur>` dit « réparations : 3 faites sur 8 » ; `/usure reparer <joueur>` remet l'arme à neuf
      et son compte à 8 / 8.
- [ ] Mettre `rare = 2` dans le fichier (ou dans `/usure config`) : quelques secondes après, une arme bleue montre « … / 2 »
      sans redémarrage. Remettre 8.
- [ ] Changer la rareté d'une arme avec `/rarete` : son maximum suit la nouvelle couleur.
