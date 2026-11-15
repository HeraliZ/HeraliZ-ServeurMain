# Essais en jeu : établi et artisanat réservés aux fondateurs (14/11, Loot 0.18.0, Grades 1.5.7)

Demande du propriétaire : « bloquer l'accès à la table de craft à tout le monde sauf fonda », et plus d'artisanat dans
l'inventaire. Rien n'a été essayé en jeu par Claude.

## Ce qui change

- **Établi (table de craft)** : seul un joueur au grade du staff « Fondateur », connecté à son compte, peut l'ouvrir.
  Tous les autres, en créatif comme le reste du staff, lisent « Ce poste est hors service. ». Avant : hors service
  pour les joueurs, libre en créatif et pour le staff.
- **Fabrication** : pour tous les autres, la grille ne donne plus rien. C'est le cas de la grille 2×2 de l'inventaire
  comme de celle de l'établi, même avec un jeu modifié qui la montrerait encore : c'est le serveur qui décide. Les objets
  posés dans la grille sont rendus à la fermeture, comme d'habitude. La grille 2×2 disparaît de l'écran avec le nouvel
  inventaire.
  **Correction du 15/11 (audit, duplication n° 2)** : c'était faux pour l'inventaire réel du jeu (celui de Curios), qui
  calcule sa recette lui-même : sa grille cachée fabriquait encore avec un jeu modifié. Corrigé dans Loot 0.18.1, voir
  `docs/TESTS_EN_JEU_15NOV_combat-craft.md`.
- Réglage : `craft_fondateurs_seulement` dans `[postes_vanilla]` de `heraliz_loot-server.toml` (vrai par défaut ;
  `false` = règles d'avant).

## À vérifier

- [ ] Joueur normal : clic droit sur un établi → « Ce poste est hors service. », rien ne s'ouvre ;
- [ ] staff qui n'est pas fondateur, en créatif : même refus ;
- [ ] fondateur : l'établi s'ouvre et fabrique normalement (par exemple des planches) ;
- [ ] joueur normal, inventaire : des bûches dans la grille 2×2 → aucune planche en résultat ; les bûches reviennent à la
      fermeture de l'inventaire ;
- [ ] fondateur, inventaire : la grille 2×2 fabrique normalement (tant qu'elle est encore à l'écran) ;
- [ ] Table d'armurier, recycleur, marchands : inchangés (ce ne sont pas des grilles de Minecraft).

## Pas vérifié par Claude

- Pas compilé ici (Minecraft et Forge absents) : la fabrication complète est faite par le contrôle de branche de GitHub.
- Le mixin du serveur (`heraliz_loot_serveur.mixins.json`) ne se vérifie qu'en jeu ; s'il ne s'appliquait pas, rien ne
  plante, mais la grille 2×2 fabriquerait encore pour tout le monde (l'établi resterait bloqué).
