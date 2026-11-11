# Essais en jeu — retours du 11/11, chantier « joueur »

Équipement 1.2.1, Réanimation 1.16.11, CRac 1.36.1-beta, Boutique 1.5.6, Monde 1.1.2, réglage
`serveur/config/backpacked.server.toml`. Rien n'a été essayé en jeu par Claude, et rien n'a été compilé ici (Minecraft
ne se télécharge pas dans cet environnement) : la fabrication complète est faite par le contrôle de branche de GitHub.
Les joueurs ont de nouveaux jars (le launcher les met à jour).

## 1. La ghillie protège vraiment

Cause : depuis le 14/10, les ghillies avaient 0 % de protection, mais leur infobulle disait « Protège le corps ».
Maintenant : ghillie **intégrale 8 %**, **incomplète 5 %**, au corps (balles, explosions, couteau / mêlée, morsures des
zombies ; pas les tirs à la tête), en plus de l'armure portée, sous le même plafond de 60 %. Comme un gilet, elle perd
1 point d'usure par coup encaissé (350 / 450) et se répare au kit.

À vérifier :
- [ ] infobulle d'une ghillie intégrale : « Protection : 8 % » (doré), « Protège le corps », puis « ✦ les zombies ne
      vous repèrent plus » ; incomplète : « Protection : 5 % » ;
- [ ] infobulle d'un brassard ou d'un objet event (citrouille, sac sur la tête) : « Protection : 0 % » grisé, et plus de
      « Protège le corps / la tête » ;
- [ ] cible d'essai (clic droit main vide) : deux lignes de plus tout en bas, « Ghillie incomplète forêt » (corps 5 %,
      tête 0 %) et « Ghillie intégrale forêt » (corps 8 %, tête 0 %) ; la cible porte bien la ghillie ; un tir au corps
      montre 8 % (ou 5 %) pris par l'armure, un tir à la tête 0 % ;
- [ ] à deux joueurs : A tire au corps de B, d'abord sans ghillie puis avec une ghillie intégrale : le petit chiffre des
      dégâts baisse de 8 % ;
- [ ] avec une tenue Légendaire complète (50 %) + ghillie intégrale : 58 % au corps (jamais plus de 60 %).

## 2. Exécution vue en 3e personne

Cause : le jar geckolib222 (d'un autre auteur) remet la vue à la 1re personne à chaque tick ; selon l'ordre dans lequel
les mods se lancent, il passait après notre caméra et l'exécution se voyait à la 1re personne. La 3e personne est
maintenant remise juste avant chaque image, pendant toute la scène.

À vérifier (à deux, en survie, plusieurs lancements du jeu si possible) :
- [ ] A met B à terre puis l'exécute (coup de grâce, puis une exécution d'arme ou un finisher) : A voit toute la scène
      de l'extérieur, caméra de cinéma qui bouge, bandes noires ; puis retour à la 1re personne à la fin ;
- [ ] B (la victime) voit la même scène de cinéma, puis l'écran de mort après le coup ;
- [ ] exécution interrompue (B réanimé ou A qui s'éloigne / meurt avant le coup) : retour à la 1re personne ;
- [ ] A se déconnecte pendant la scène puis revient : il est bien à la 1re personne ;
- [ ] appuyer sur F5 pendant la scène ne la casse pas ; après la scène, F5 reste bloqué en survie comme avant ;
- [ ] (facultatif) `executionCamera = false` dans `config/reanimation-client.toml` du joueur : la scène se joue à la
      1re personne, comme avant.

## 3. Sac plein retirable, aucun sac dans les coffres des bases

Cause du blocage : le réglage `lockIntoSlot` de Backpacked (remis le 02/11), passé à `false`.

À vérifier :
- [ ] sac plein sur le dos : il se retire (clic, Maj + clic vers l'inventaire, glisser) et garde tout son contenu ; remis
      sur le dos, tout est là ; plus d'infobulle rouge « Vide le sac à dos avant de le retirer » ;
- [ ] un sac plein ne rentre toujours pas dans un autre sac ;
- [ ] coffres des bases (caisse en métal, malle de chantier, casier métallique, caisse d'armes, coffre-fort) : un sac
      VIDE et un sac PLEIN sont refusés par clic, Maj + clic, glisser sur plusieurs cases, touches 1 à 9, touche de la
      main gauche (F) ; le sac reste au curseur ou dans l'inventaire, et rien ne disparaît ;
- [ ] entonnoir posé au-dessus d'un coffre de base, sac jeté dedans : le sac reste dans l'entonnoir ;
- [ ] un sac rangé dans un coffre de base AVANT la mise à jour se reprend normalement ;
- [ ] planque et coffre VIP (spawn) : comme avant, sac vide accepté, sac plein refusé ; coffre du clan : aucun sac ;
- [ ] mort avec un sac plein dans l'inventaire : le sac tombe avec son contenu (rien de copié, rien de perdu).

## 4. Bonus de loot en temps réel, sans icône à l'écran

À vérifier :
- [ ] objet « Bonus de loot » utilisé : message « activé : 2 h (le temps passe aussi quand tu n'es pas connecté) » ;
      aucune icône en haut à droite de l'écran de jeu ; dans l'inventaire, « Bonus de loot +10 % » et le temps restant ;
- [ ] se déconnecter 10 min puis revenir : message « encore 1 h 50 » environ, l'inventaire montre le même temps ;
- [ ] rester AFK au spawn 5 min : le temps baisse quand même (inventaire) ;
- [ ] un 2e objet pendant le premier : 2 h de plus (4 h en tout), toujours +10 % ;
- [ ] joueur qui avait déjà un bonus avant la mise à jour (ancien temps de jeu) : à sa connexion, message « encore … »
      avec le temps qui lui restait, qui baisse ensuite en temps réel ;
- [ ] à la fin : « Ton bonus de loot est terminé. » ; s'il finit pendant l'absence : le message à la connexion ;
- [ ] premier coffre de loot ouvert pendant le bonus : « Bonus de loot +10 % » au-dessus de la barre ; après la fin :
      plus rien ;
- [ ] bon de bonus de loot (`/bon editeur`) : même affichage (inventaire seulement), comme avant en temps réel.
- Au premier démarrage, Forge peut écrire dans le journal que `heralisz_utils-server.toml` « n'est pas correct » : il
  retire seulement les deux anciens réglages `carte_seulement` et `afk_minutes`, c'est normal.
