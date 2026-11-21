# HeralisZ Backpack

Sacs à dos du serveur HeralisZ. Version modifiée de **Backpacked 3.0.0-beta.4** de **MrCrayfish**, distribué sous
licence **GNU LGPL 2.1** (texte complet : `src/main/resources/LICENSE_Backpacked`). Elle reste sous la même licence :
en cas de distribution, fournir ce code source et la licence.

L'identifiant de mod reste `backpacked` : objets, sacs, emplacement Curios « back » et boutique sont inchangés.

Modifications (ArkaniaZ puis HeralisZ) :
- sacs ArkaniaZ supplémentaires (Mountain, Military camo, Waterproof, Sniper case, peluches…), modèles et textures ;
- rendu : le sac affiché est celui de l'objet équipé (heralisz1), corrections de modèles (heralisz2-3) ;
- 2026-09-27 : plus de boutons de personnalisation (palette) ni de réglages (clé) dans l'interface du sac, et le
  serveur refuse toute demande de personnalisation ; vérification de clé serveur ArkaniaZ retirée ; compatibilité
  manette (Controllable, non installé) rangée dans `src/optional/controllable`.
- 2026-10-04 (heralisz12) : 7 sacs repris du mod Fuse de l'utilisateur (modèles et textures de Tactical Gear Rework,
  licence propriétaire : usage privé du serveur) : sac de sport en bandoulière (4x4), sacs à dos tactiques noir / vert
  (5x4), sacs de randonnée noir / urbain / vert / multicam (5x5). Modèles Java de Fuse recalculés en maillages OBJ
  (chargeur OBJ de Forge), masques de camo, icônes : `tools/fuse_backpacks.py`.
- 2026-11-14 (heralisz24) : l'écran d'un sac ne vaut plus que tant que le même objet sac est sur le dos de son porteur
  vivant (et la personne qui regarde vivante) ; sur le serveur, aucun clic n'est plus traité dans un écran qui ne vaut
  plus (`BackpackInventory.stillValid`, `BackpackContainerMenu.clicked`).
- 2026-11-15 (heralisz25) : le contenu du sac gardé en mémoire par le joueur est oublié dès que le sac n'est plus sur le
  dos, et dès que la liste « Items » de l'objet a été remplacée ailleurs (`PlayerMixin`, `BackpackInventory.aJour`) :
  le même sac revenu sur le dos ne reprend plus un ancien contenu.
- 2026-11-21 (heralisz28) : défis de déblocage plus suivis quand tout est débloqué (`unlockAllCosmetics`,
  `UnlockManager.defisSuivis`) ; reliquats ArkaniaZ retirés (clé et adresse d'un ancien serveur, contrôle d'adresse au
  démarrage jamais branché : `ServerStartingEventHandler`).

Sources obtenues en décompilant le jar du serveur (Vineflower 1.12), voir `tools/fork_backpacked.py`.
