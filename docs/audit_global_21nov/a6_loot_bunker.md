# Audit global du 21/11 — volet a6 : loot et bunker

Relecture du code des modules `modules/loot` (HeraliZ Loot : conteneurs, rotation par zones, raretés, salles à carte côté
loot, coffres T7, butin des zombies, conteneurs scellés, présentoirs, nuisances, fouille avec un objet en main, écrans de
fouille, modèles allégés des conteneurs) et `modules/bunker` (HeraliZ Bunker et le code de HBM Modernized, GPL : portes
animées, cartes d'accès et fragments, lecteurs, barricades, salles à carte, portes de bunker). Versions : Loot **0.20.8**,
Bunker **0.14.8** (le launcher les donne ; aucun changement de canal réseau : un jeu pas à jour entre encore).

**Rien n'a été testé en jeu** : relecture du code, contrôle des cibles (jars de TaCZ, Curios, Forge 47.3.12 de Mohist 916),
contrôle des textes (`outils/verifier_langues.py` : 0 erreur), puis fabrication et contrôles sur GitHub.

Gravité : **moyen** (lag ou faille possible, sans perte d'objet constatée), **faible** (petit coût ou cas très rare).

## Corrigé

Tout dans le commit **5f5721dc**.

1. **Fouille avec une arme en main : demandes envoyées en rafale (moyen, faille et lag).** Le clic droit avec une arme en
   main envoie une demande au serveur (« fouiller ce que je vise »). Rien ne limitait leur nombre : un jeu modifié pouvait
   en envoyer des centaines par tick ; pour chacune, le serveur refaisait les règles de tous les mods (bases, joueur à
   terre, anticheat avec ses rayons de vue, rotation du loot). Maintenant : une demande par tick et par joueur au plus
   (un jeu honnête en envoie une par clic, ou une tous les 4 ticks touche tenue).
   `modules/loot/src/serveur/java/fr/heraliz/loot/FouilleServeur.java` l.36-56 (`DERNIERE`, table faible par joueur).
2. **`/heralizloot reinitialiser <rayon>` : gel du serveur (moyen, staff).** Au rayon 128, la commande lisait chaque bloc
   d'un cube de 257 × 257 × 257 (17 millions de lectures) et chargeait, voire créait, chaque tronçon pas chargé sur le fil
   du serveur : plusieurs secondes de gel. Maintenant : seulement les coffres T7 des tronçons déjà chargés, lus dans leurs
   entités de bloc (même résultat autour du staff, qui tape la commande à côté des coffres).
   `modules/loot/src/serveur/java/fr/heraliz/loot/Commandes.java` l.207-233 (`reinit`).
3. **Porte d'une salle à carte au bord de la vue d'un joueur : le serveur attendait un tronçon (moyen, lag).** Toutes les
   5 s, chaque porte de salle dont le tronçon est chargé revoit ses lecteurs de carte (2 blocs autour de chaque pièce de la
   porte). Quand la porte est au bord de la zone chargée d'un joueur (environ 160 blocs), ces lecteurs pouvaient être dans
   le tronçon voisin, encore en lecture : le fil du serveur l'attendait, en faisant pendant ce temps tout le travail de
   chargement en file (même défaut que le point C4 de l'audit des lenteurs du 19/11). Maintenant la porte n'est revue que
   si tous ses tronçons sont complets (sinon 5 s plus tard). Quand un joueur est près de la porte (carte passée, quelqu'un
   dedans), tout est chargé : rien ne change pour lui.
   `modules/bunker/src/commun/java/fr/heralisz/bunker/keycard/KeycardDoors.java` l.85-106 (`lecteursPrets`) ;
   `modules/bunker/src/serveur/java/fr/heralisz/bunker/serveur/salle/SallesMonde.java` l.302-314 (`notifier`). Code de
   HeraliZ seulement : aucun fichier de HBM touché.
4. **Coffres T7 automatiques posés à cheval sur deux tronçons (faible, lag rare).** Le point d'apparition ne regardait que
   le tronçon du bloc principal ; le coffre d'armes (2 blocs) et le largage (2 × 2) lisaient et posaient aussi leurs cases
   voisines, parfois dans un tronçon encore en lecture (attente du serveur). Maintenant toutes ses cases doivent être
   chargées (sinon il apparaît au passage suivant, une seconde plus tard).
   `modules/loot/src/serveur/java/fr/heraliz/loot/PointsT7.java` l.175 et l.184-201 (`pret`).
5. **Couleur de rareté dans les inventaires : petites allocations à chaque image (faible, images par seconde).** Pour chaque
   case, à chaque image : une boîte de munitions de TaCZ fabriquait un texte neuf (« tacz:ammo_box/1 »), et un objet d'un
   coffre T7 copiait la liste des raretés. Maintenant : textes faits une fois, liste partagée. Même couleur partout.
   `modules/loot/src/commun/java/fr/heraliz/loot/RaretesObjets.java` l.137-146 ; `Rarete.java` l.89-99 (`lire`).
6. **Raretés lues avec la langue du PC (faible).** Les valeurs de `raretes.json` étaient mises en minuscules selon la
   langue du système (un serveur en turc lisait mal « EPIQUE »). Maintenant : toujours la même règle (`Locale.ROOT`).
   `modules/loot/src/serveur/java/fr/heraliz/loot/RaretesServeur.java` l.96.

## À décider par le propriétaire

1. **Porte de transition de HBM (`heralisz_bunker:transition_seal`, 27 blocs de large, 24 de haut).** Si une seule est posée
   sur la carte, elle relit à chaque tick, sur le serveur ET chez chaque joueur proche, environ 600 blocs autour d'elle
   (jusqu'à 13 blocs de côté, tronçons voisins compris) pour savoir si elle est alimentée
   (`modules/bunker/src/commun/java/com/hbm_m/blockentity/machines/TransitionSealBlockEntity.java` l.55-80). Je ne peux
   pas voir la carte d'ici. Proposition : vérifier qu'aucune n'est posée (elle n'est citée nulle part dans nos fichiers) ;
   s'il y en a une, la remplacer par une autre porte, ou ne relire son alimentation que quand un bloc voisin change
   (retouche du code de HBM, à essayer en jeu). Risque : nul si aucune n'est posée.
2. **Rendu des portes de HBM chez les joueurs.** À chaque image, pour chaque porte visible, ses noms de cache sont refaits
   (textes « frame_… », « geom_… », « dae_door:… », `modules/bunker/src/client/java/com/hbm_m/client/render/implementations/DoorRenderer.java`
   l.312, 355, 460) : quelques dizaines de petits textes par image près d'une base ou d'un bunker. Gain faible ; ça touche le
   rendu de HBM : seulement avec un essai en jeu des portes (ouverture, skins, shaders).
3. **Rotation du loot à 150 joueurs, à surveiller.** Deux coûts qui grandissent avec le nombre de conteneurs ouverts
   (quelques milliers à 150 joueurs) : la revue des conteneurs entamés, une fois par seconde, d'un coup
   (`modules/loot/src/serveur/java/fr/heraliz/loot/RotationLoot.java` l.964, environ 0,3 ms au pire d'après le calcul), et
   le relevé de tous leurs objets à chaque sauvegarde automatique sur le fil du serveur (l.391 ; seules la compression et
   l'écriture partent en fond). Rien à changer tant que `/diag profil` un soir chargé ne les montre pas ; sinon : étaler la
   revue sur les 20 ticks de la seconde (comme les étoiles).
4. **Petit : dépendance au noyau.** Le `mods.toml` du loot déclare le noyau « côté joueur » seulement (l.34), alors que la
   partie serveur du loot s'en sert aussi (écritures en fond, tronçons prêts). Aucune conséquence aujourd'hui (le noyau est
   toujours sur le serveur) ; sans lui, le serveur planterait au démarrage avec une erreur moins claire. Proposition :
   `side="BOTH"` (risque nul).

## Vérifié sans problème

- **Paquets reçus des joueurs** : un seul dans ces deux mods (`ReseauLoot.Fouiller`) ; le serveur revérifie joueur en jeu,
  écran fermé, portée (6 blocs des yeux), point visé sur le bloc, chose à fouiller (nos conteneurs seulement), règles des
  autres mods par les événements de Forge (bases de CRac, joueur à terre, ligne de vue de l'anticheat : il les voit bien),
  et maintenant la cadence. Les autres paquets ne vont que vers les joueurs : Forge 47.3.12 refuse un paquet envoyé dans le
  mauvais sens (`NetworkHooks.validatePacketDirection`, vu dans le jar de Mohist 916).
- **Duplications** : rien ne se dépose dans un conteneur de loot (cases « prise seulement », Maj + clic, glisser, touches 1
  à 9, main gauche), ni dans un coffre T7 ou une caisse de largage ; un objet pas encore identifié ne quitte jamais le
  serveur (« ? ») et ne se prend pas ; un conteneur ouvert n'est jamais rempli de nouveau sous les yeux de quelqu'un (la
  rotation ne choisit jamais un conteneur actif) ; recharge d'une salle seulement salle vide ; carte usée seulement au
  passage réussi, avec délai et lecteur occupé ; barricade : pas de ramassage, pose revérifiée par le serveur.
- **Lag** : aucun parcours de tous les conteneurs à chaque tick (réapparitions et conteneurs entamés une fois par seconde,
  étoiles d'un joueur sur 20 à chaque tick, index des zones, de la grille et des salles) ; tronçons « vraiment prêts »
  avant de lire un bloc (étoiles, conteneurs d'événement, points T7, salles) ; portes à l'arrêt sans travail par tick,
  pièces de porte sans ticker ; présence dans les salles : 12 salles × joueurs du monde, une fois par seconde.
- **Sauvegardes** : rotation écrite à côté puis mise à sa place (un arrêt brutal ne l'abîme pas), données des capacités
  copiées ; état du butin des zombies pareil ; salles, points T7 et conteneurs d'événement écrits en fond par le noyau
  avec une copie ; rien de lu sur le disque dans un tick (tables et raretés : au démarrage et par `/recharger`).
- **Cibles lues par réflexion**, toutes présentes avec les mêmes signatures, classes publiques : depuis le loot vers
  Tactique (`ObjetsAVie.estAVie`), Clans (`SaisonApi.pve`), Grades (`GradesApi.fondateur`), Événements
  (`EvenementsApi.refusOuverture / coffreOuvert / objetDuCoffre`), CRac (`ClaimManager.getAt`), noyau
  (`Rechargements.ajouter`), Monde (`ZombieSurvie.tenue`), TaCZ (`EntityHurtByGunEvent$Pre.getHurtEntity / getAttacker`),
  geckolib222 (`CustomFoodItem`) ; vers le loot depuis Bunker (`SallesLoot` : 2 × `declarer`, `retirer`, `recharger`,
  `etat`), Événements (`PointsT7.apparaitre` à 7 arguments, `ConteneursEvenement.poser / retirer / noms`), Monde
  (`RevelationCaisses.ouvrir`, `TrainLoot.tirer`, `ZonesLoot.conteneurPres`), Quêtes et Clans (`EvenementsLoot`,
  `ButinZombies.ecouter`, `EconomieBeta.facteur`, `RaretesObjets.de / couleurNom`), Interface, Armes et noyau
  (`client.RaretesClient.couleur`) ; vers Bunker depuis Quêtes (`SallesMonde.couleurIci`).
- **Mixins** : TaCZ `m_7626_` (nom des armes, accessoires, munitions) et Curios `m_6199_` (deux écrans) présents dans les
  jars ; caméra de la cinématique de carte (`Camera.setup`) inchangée.
- **Textes** : aucun texte nouveau ; `outils/verifier_langues.py` : 0 erreur.
- **Licences** : code de HBM non touché par ce lot (seuls `KeycardDoors` et `SallesMonde`, code de HeraliZ, changent) ;
  crédits et GPL du bunker inchangés.
- Déjà connu, pas redit : la borne de départ ne voit les conteneurs posés en jeu qu'après un redémarrage (section 44).

## À tester en jeu

- [ ] Arme en main, clic droit sur un conteneur, un corps, une caisse de largage, un coffre T7 : ça s'ouvre comme avant,
      même en cliquant vite plusieurs fois.
- [ ] `/heralizloot reinitialiser` puis `/heralizloot reinitialiser 128` à côté d'un coffre T7 : il est remis à neuf
      (« N coffre(s) remis à neuf »), sans gel du serveur.
- [ ] Salle à carte : carte de la bonne couleur, la porte s'ouvre, reste ouverte tant qu'on est dedans, se referme 4 s
      après la sortie ; loot rechargé après le délai de la couleur.
- [ ] Venir en courant vers une salle à carte depuis 200 blocs : pas de « [Diag] Blocage » au staff.
- [ ] Couleur de fond des boîtes de munitions (fer, or, diamant) et des objets d'un coffre T7 : comme avant.
- [ ] Coffres T7 automatiques (`/heralizloot point liste`) : ils réapparaissent comme avant.
