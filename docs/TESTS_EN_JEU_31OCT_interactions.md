# Essais en jeu du 31/10 : chantier « interactions » (rien testé par Claude)

Modules touchés : Noyau 1.0.6, Tactique 2.40.1, CRac 1.28.1-beta, Bunker 0.13.3, Équipement 1.0.1, Interface 2.0.2,
Boutique 1.2.1. Les joueurs doivent avoir les nouveaux jars ; redémarrer le serveur. Rien n'a été compilé ici (Gradle
bloqué) : la fabrication de la branche sur GitHub le fera.

Tous les essais se font avec un compte **joueur** (pas op : les op passaient déjà partout), en survie, sur la carte.

## 1. Portes avec un objet en main

Cause trouvée : WorldGuard ne connaît pas les objets des mods et les croyait capables de modifier le bloc visé ; il les
refusait aux joueurs (droit de construire de la région). En plus, une arme TaCZ ou de mêlée prenait le clic droit.

- [ ] Porte en bois de Minecraft (sur la carte, dans une ville) : l'ouvrir puis la fermer avec, en main : de la
      nourriture (conserve, cola), un soin (bandage), une batte, un pistolet, une arme de mêlée. Elle bouge et reste dans
      sa position (elle ne « revient » plus).
- [ ] Pareil avec une trappe en bois et un portillon.
- [ ] Porte en fer avec un pistolet en main : la porte ne s'ouvre pas, et le pistolet vise normalement.
- [ ] Pistolet en main, en train de viser (clic droit déjà fait) en regardant une porte : un nouveau clic droit arrête
      la visée (il ne touche pas la porte).
- [ ] Pistolet en main, viser un joueur ou un zombie avec une porte derrière, à plus de 5 blocs : la visée marche.
- [ ] Pistolet en main, accroupi, clic droit sur une porte : rien (règle de Minecraft), la visée se fait.
- [ ] Arme de mêlée en main, clic droit sur une porte : la porte bouge, pas de coup spécial ; clic droit ailleurs : coup
      spécial comme avant.
- [ ] Base de clan (CRac) : porte de son clan, avec un pistolet ou un soin en main, en visant la porte elle-même : elle
      s'ouvre et se ferme. En visant l'encadrement : rien, la visée se fait.
- [ ] Porte d'un autre clan : toujours « verrouillée » (son de coffre fermé), même avec un objet en main.
- [ ] Bunker : porte animée normale, pistolet en main : elle s'ouvre et se ferme (aussi en cliquant sur un bout de la
      porte, pas seulement son milieu). Porte à carte : pistolet en main, le clic droit vise (pas de message), le
      lecteur de carte marche comme avant.
- [ ] Fouille avec une arme en main (conteneurs, cadavres, largage) : inchangée.
- [ ] Tirer, recharger, viser loin d'une porte : inchangés.
- [ ] Journal du serveur au démarrage : la ligne « [noyau] clic droit avec un objet de mod : jugé par les plugins comme
      une main vide ». Si on lit à la place « correctif WorldGuard impossible à brancher », me la donner.

## 2. Manger et se soigner en visant un bloc

Même cause (WorldGuard) : le serveur refusait l'objet, le jeu du joueur commençait seul.

- [ ] Regarder le sol ou un mur tout près et manger une conserve GeckoLib (porc, cola…) : l'animation s'arrête à la fin
      et la faim remonte, l'objet est consommé.
- [ ] Pareil avec un bandage, une attelle, un AI-2 (soins en bascule) et une poche de sang : le soin va au bout et
      soigne.
- [ ] Pareil en regardant le ciel (déjà bon avant) : toujours bon.
- [ ] Nourriture de Minecraft (si on en a) : comme avant.
- [ ] Clic droit avec de la nourriture sur un conteneur de loot : la fouille s'ouvre (comme avant).
- [ ] Dans une zone protégée où un joueur ne peut pas ouvrir une porte à main vide (s'il y en a) : toujours refusé avec un
      objet en main.

## 3. Kit de départ

- [ ] Nouveau joueur (ou `/wipe` du joueur), après la cinématique : le kit contient une Attelle (en plus du reste), et
      le message dit « … batte, attelle et de quoi manger ».
- [ ] L'attelle soigne une jambe cassée.

## 4. Plus de main gauche

- [ ] Inventaire (écran de Curios) : plus de case de main gauche (ni cadre, ni bouclier gris) à droite du personnage ;
      le reste de l'écran est inchangé (cadre du personnage, artisanat, panneau Curios).
- [ ] Inventaire de base de secours (s'il s'ouvre : serveur lent) : pareil.
- [ ] Touche F dans l'inventaire, sur un objet : rien. Touche F en jeu : rien (déjà avant).
- [ ] Maj + clic sur un objet qui irait en main gauche (bouclier s'il en existe un) : il reste dans l'inventaire (il peut
      clignoter une fraction de seconde au plus).
- [ ] Clic et glisser à l'endroit où était la case : rien ne se pose.
- [ ] Staff : `/item replace entity <joueur> weapon.offhand with minecraft:bread` sur un joueur en survie : le pain
      revient tout de suite dans son inventaire.
- [ ] Inventaire plein + objet en main gauche (même commande) : il reste caché en main gauche, le clic droit ne s'en sert
      pas ; dès qu'une case se libère, il revient dans l'inventaire.
- [ ] Créatif : la case de la main gauche est là et marche, F aussi.

## 5. Bouton « Boutique » du menu Échap

- [ ] Au spawn (hors de la carte) : Échap, Boutique : le menu Échap se ferme et l'écran de la boutique s'ouvre, le même
      que `/boutique`. Fermer la boutique : retour au jeu (pas au menu Échap).
- [ ] Sur la carte : Échap, Boutique : même refus que `/boutique` tapée (« Impossible sur la carte… »), le menu Échap est
      fermé. Voir la question 1 plus bas.
- [ ] En combat : même refus que `/boutique` en combat.
- [ ] Le bouton n'a plus la petite adresse internet à droite, mais une flèche ›.

## Questions pour le propriétaire

1. Sur la carte, `/boutique` est refusée par la règle « commandes permises sur la carte » : le bouton Boutique du menu
   Échap y est donc refusé aussi. Faut-il permettre la boutique sur la carte (ajouter `boutique` à `commandes_permises`
   de `heraliz_noyau-server.toml`) ?
2. Pistolet en main et viseur sur une porte à moins de 5 blocs : le clic droit ouvre ou ferme la porte au lieu de viser
   (comme pour la fouille des conteneurs). Ça vous va ? Sinon, on peut le réserver à une touche (par exemple la touche
   « Interagir » de TaCZ, O par défaut, qui marche déjà).
