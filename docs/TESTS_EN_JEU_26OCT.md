# À tester en jeu — lot du 26/10 (butin des zombies, Divers, frappes, TP, quêtes du jour)

Rien de cette liste n'a été essayé en jeu. Chaque ligne : quoi faire, ce qu'on doit voir. Cocher au fur et à mesure ;
noter ce qui ne va pas (capture d'écran + langue du jeu). Commandes d'essai réservées aux op 4 (serveur d'essai).

Serveur d'essai local déjà à jour (`C:\hzt`, jars de `livraison/` du 26/10, nouveaux gunpacks) et profil de jeu
« HeraliZ Decors » à jour aussi (anciens dossiers gardés : `mods_avant_26oct`, `tacz_avant_26oct`).

## 1. Butin des zombies
- [ ] `/heralizloot zombies simuler 10000` puis `... militaire` : liste des objets tirés, environ 15 % (ordinaire) et 19 %
      (militaire) de « rien », aucune erreur.
- [ ] Tuer des zombies **de la carte** (pas de `/summon`) : environ 8 sur 10 lâchent quelque chose (composants, objets de
      récupération, balles, nourriture…), posé au sol comme un objet normal.
- [ ] `/summon heralisz_utils:zombie_survie` puis le tuer : **rien** (anti-farm : zombie artificiel).
- [ ] Zombie tué par une chute, de la lave ou un cactus : rien.
- [ ] Zombie en tenue militaire (aérodrome, base Sentinelle) : un peu meilleur (munitions de fusil, composants II-III).
- [ ] `/heralizloot zombies joueur <toi>` : kills de l'heure, chance de butin (100 % au début), fragments du jour.
- [ ] `/heralizloot zombies stats` : compteurs du serveur (kills, objets, fragments).
- [ ] Redémarrer le serveur : `/heralizloot zombies joueur` garde les mêmes compteurs.

## 2. Table d'armurier, onglet « Divers »
- [ ] Ouvrir la Table d'armurier : un onglet **Divers** (icône : les jumelles) avec 7 recettes : carte verte, bleue, rouge,
      jumelles de désignation, munition de frappe légère, lourde, spéciale.
- [ ] Avec les 4 fragments verts numérotés 1 à 4 : fabriquer la carte verte ; les 4 fragments sont retirés, la carte donnée.
- [ ] L'ancienne recette de l'établi (4 fragments en carré) ne marche plus.
- [ ] Clic droit avec un fragment quand les 4 numéros sont dans l'inventaire : la carte est toujours donnée (comme avant).

## 3. Frappes aériennes
- [ ] Les jumelles sont **Légendaires** (nom doré) ; la munition légère Rare, la lourde Épique, la spéciale Légendaire.
- [ ] Infobulle d'une munition neuve : « Frappes : 2 / 2 » ; après une frappe : « 1 / 2 », après la 2e : la munition
      disparaît.
- [ ] Frappe sur une base (murs, toit) : **aucun bloc cassé, aucun feu** ; un joueur à l'intérieur ne prend rien.
- [ ] Joueur dehors, à découvert, sur la cible : il prend les dégâts. Joueur sous un toit (même hors base) : rien.
- [ ] Juste après une frappe : se déconnecter, revenir : les jumelles sont toujours en recharge (30 s).
- [ ] Au spawn, dans une zone VIP ou en match : frappe refusée avec un message.
- [ ] Deux frappes en même temps par le même joueur : la 2e est refusée.

## 4. Recharges de téléportation
- [ ] Sans grade : `/home` puis `/home` : refusé, environ 8 min à attendre. `/tpa` réussi puis `/tpa` : « Prochaine
      téléportation dans 7 min 5x s. »
- [ ] Rescapé : 7 min 30 ; Traqueur : 7 min ; Roi de la Zone : 6 min 30 (les deux commandes, chacune de son côté).
- [ ] `/home` en recharge ne bloque pas `/tpa` (et l'inverse).
- [ ] Redémarrer le serveur pendant une recharge : elle continue (pas remise à zéro).
- [ ] Changer le grade d'un joueur pendant une recharge : la recharge en cours garde sa durée ; la suivante prend celle du
      nouveau grade.
- [ ] Boutique (`/boutique`) : avantages « Téléportations (base, /tpa) −30 s / −60 s / −90 s ».

## 5. Quêtes du jour
- [ ] Journal (touche J) : bouton « Quêtes du jour » en haut à droite ; ou `/journalieres`.
- [ ] Choisir une difficulté : 3 quêtes proposées, avec leurs récompenses possibles et leurs chances.
- [ ] En accepter une : les deux autres disparaissent ; fermer et rouvrir : même quête, même progression.
- [ ] Rouvrir après une déconnexion ou un redémarrage : rien n'a changé.
- [ ] Faire la quête (ou, op 4 : `/journalieres essai <toi> avancer 100`) : écran « Réussie », bouton pour le tirage.
- [ ] Tirage : la bande défile ~5 s (ESPACE pour passer), la récompense arrive dans l'inventaire (ou au sol s'il est
      plein ; une clé de caisse va sur le compte de clés).
- [ ] Rouvrir : « Journée terminée », rien d'autre à faire avant minuit.
- [ ] Op 4 : `/journalieres essai <toi> minuit` simule le lendemain : choix de difficulté à nouveau.
- [ ] `/journalieres voir <joueur>` (staff) : état de la journée du joueur.
- [ ] Langues anglaise et russe : rien ne dépasse dans l'écran.

## 6. Ce qu'il faut regarder dans les fichiers du serveur après les essais
- `logs/latest.log` : aucune erreur `ButinZombies`, `Journalieres`, `TpaServeur`, `DefinitionsGrades` (ligne « mis à jour
  en version 3 » une seule fois).
- `heraliz_quetes/journalieres.log` : une ligne par choix, acceptation, réussite, tirage, remise.
- `heraliz_loot/zombies_etat.json`, `heraliz_grades/recharges_tp.json` : créés, lisibles.

## 7. Bêta (liste du 26/10)
- [ ] Chat (T) : les onglets Proximité / Global / Clan ne touchent plus la barre de saisie (aussi en 1080p, échelle 3).
- [ ] Arme en main : en bas à droite, une ligne « 27 / 120 · AUTO » au-dessus de l'état de l'arme ; plus d'image d'arme
      ni de petit texte dessous ; chiffre rouge sous un quart du chargeur ; rien avec un couteau.
- [ ] Icônes des armes dans l'inventaire : images du gunpack (plus de photo 3D) ; arme avec un skin : « Skin : … » dans
      l'infobulle, couleur de la rareté.
- [ ] Conteneurs T1 à T7 : plusieurs exemplaires d'un même objet (clous, planches, ferraille, munitions, capsules) ; un
      composant Épique est presque toujours seul ; kits de réparation rares.
- [ ] Arme trouvée dans un conteneur : environ 10 % du chargeur (au moins 1 balle), rien dans la chambre.
- [ ] Largage (bouton Largage du panel, ou `/event lancer largage`) : annonce, caisse avec une ou deux armes, composants,
      matériaux, objets à revendre, grenades, munitions par tas entiers, soins ; un objet d'événement (dossier,
      mallette ou disque) revendable à Mila (hérals ; points seulement avec `avec_points` ou le planning).
- [ ] Kit de réparation : piles de 5 ; clic droit en main = atelier ; poser une arme abîmée : « 42 % → 49 % » ; Réparer :
      l'état monte, le kit perd une utilisation ; Annuler / Échap : l'arme revient dans l'inventaire ; même chose avec
      une armure ; kit en pile : un seul kit est entamé, les autres restent neufs.
- [ ] Recycleur : une arme très abîmée annonce des chances bien plus basses (« état 20 % ») ; la corde se recycle
      (tissu) ; les objets cassés (téléphone, radio...) aussi.
- [ ] Table d'armurier : C4 (poudre IV), charge explosive (poudre III + 2 tissus IV), barricade dans « Divers », viseurs
      et accessoires plus chers selon leur rareté.
- [ ] Bandage et attelle : chacun son animation (bande enroulée autour de l'avant-bras ; attelle posée et sanglée vers
      la jambe), plus celle de la trousse.
- [ ] Finishers (touche =) : seul « Coup de grâce » est libre ; objet « Finisher : … » (caisses Vote / Quête) : clic droit
      = débloqué, consommé ; déjà possédé : pas consommé.
- [ ] Caisses : camo (stickers, StatTrak, deux mythiques seulement), gemme (tous les couteaux), event, vote, quête,
      émotes ; les autres caisses disent « vide » sans prendre la clé ; une émote déjà débloquée ne sort jamais.
- [ ] Casino, caisse : clés, camos, stickers, StatTrak, finishers, émote au hasard, grades à la journée, matériaux, cœur
      de clan ; acheter 2 « Émote au hasard » donne 2 émotes.
- [ ] Gunpacks : après la mise en ligne (anciens dossiers supprimés du Ptero) et le nouveau launcher, plus aucun dossier
      `arkaniaz_*` dans `tacz` chez un joueur ; aucune case rose et noir dans le menu créatif.

## 8. Règles du 27/10 (événements refusés, territoires tenus 2 h, recycleur)

- [ ] Largage du planning avec moins de 4 joueurs sur la carte : pas de largage ; journal `heraliz_evenements/journal.log`
      : « pas lance (…), prochain essai a HH:MM (intervalle normal) », 3 h 30 plus tard ; `/event` montre cette heure ;
      à cette heure, avec 4 joueurs, il part. `/event lancer largage` part toujours tout de suite.
- [ ] Hélicoptère à l'heure tirée avec moins de 10 joueurs : pas d'hélicoptère, `/event helico info` donne une heure 4 h à
      6 h plus tard (plus d'essai toutes les 5 min) ; `/event helico prochain 1` puis `lancer` marchent toujours.
- [ ] Capturer un drapeau : message au clan « … est à vous pendant 2 h 00 … » ; HUD « Protégée 1 h 59 » ; un autre clan
      au drapeau ne fait pas avancer la barre (« Protégée ») ; carte M : protection 2 h ; au bout de 2 h, message « Fin de
      la tenue … redevient libre » et notification, zone libre sur le HUD, la carte et le drapeau ; plus de revenu
      (« banque … creditee … (territoires) » s'arrête), plus de bonus de loot ; recapture possible tout de suite, par
      n'importe quel clan. Pour aller vite : `tenue_min = 3` dans `heralisz_clans-common.toml` (relu par Forge).
- [ ] Au premier démarrage avec Clans 3.7.2 : une zone capturée il y a plus de 2 h redevient libre tout de suite (ligne
      « fin de la tenue » dans le journal du serveur) ; onglet Saison : plus de ligne « Prendre une zone à un clan ».
- [ ] Recycleur : 1 balle de 9 mm se recycle (« 1 sur 15 : moins de chances », chances divisées par 15, 0,5 s) ; 15 balles
      : « Paquet de 15 », 3 s ; 20 balles : un paquet de 15 puis un de 5 ; 6 roquettes dans une case : se recyclent
      (« 6 sur 15 »), plus jamais « Il en manque » ; les 3 langues : la ligne sous le nom ne déborde pas.

