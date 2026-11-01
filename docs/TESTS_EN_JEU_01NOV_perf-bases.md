# À tester en jeu — perf-bases (01/11 : CRac 1.29.12-beta, Clans 3.10.3, Quêtes 1.16.2, Loot 0.14.5, Bunker 0.13.4)

Demande du propriétaire : « optimiser le serveur le plus possible », en gardant tout ; il cite « le monde des bases, quand
on lance les tutos, quand on fait des commandes ». Périmètre de ce chantier : bases (CRac), clans (et l'économie), PNJ et
quêtes, loot, bunker, événements.

**Rien de tout cela n'a été testé en jeu ni compilé avec Gradle** (ForgeGradle introuvable sur la machine de travail) :
la fabrication complète est faite par le contrôle de branche de GitHub. Vérifié hors du jeu : `javac` sur chaque fichier
changé (syntaxe, et types de notre code : seuls les noms de Minecraft / Forge restent inconnus), `verifier_langues`
(0 erreur ; aucun texte ajouté). Aucun paquet réseau ni canal changé : jars joueurs et serveur restent compatibles.

Déjà fait avant ce chantier (vérifié, pas refait) : point /spawn gardé chargé, murs invisibles du tutoriel posés en
quelques ticks une fois les tronçons chargés (commit `d77a3ad9`).

## Ce qui a changé (rien de visible pour les joueurs, sauf mention)

| Endroit | Coût avant | Après | Pour le joueur |
|---|---|---|---|
| Quêtes : progression des joueurs (`pnj` `Donnees`) | toutes les 30 s, écriture + renommage de chaque fichier changé sur le fil du serveur (des dizaines dans le même tick à 150 joueurs : ~10 à 30 ms, bien plus si le disque est occupé) ; pareil à chaque déconnexion | texte fait sur le fil du serveur (~50 µs par joueur), écriture sur un fil à part | rien |
| Zones interdites à la construction (`ZonesConstruction`) | 2 accès au disque à **chaque** lecture : chaque case d'une pièce posée, chaque essai de /rtp (jusqu'à 48), 4 fois par seconde par joueur plan en main | date relevée à part toutes les 5 s, aucun accès au disque | un changement du fichier des zones est vu en 5 s au plus |
| `/home`, réapparition « À la base » | tronçons de l'armoire chargés sur le fil du serveur au départ : 100 à 300 ms de gel quand personne n'est près de la base | lus en tâche de fond pendant les 3 s du compte à rebours / l'écran de mort | rien (staff sans compte à rebours : comme avant) |
| `/rtp` | jusqu'à 6 tronçons (et leurs voisins) chargés d'un coup au départ | points tirés à la commande, leurs tronçons lus pendant le compte à rebours ; sinon, le tirage d'avant | rien |
| Aide au saut des montées (`SautRebord`) | chaque joueur au sol, tous les 2 ticks : ~50 lectures + 4 objets (~0,1 ms par tick à 150 joueurs) | 1 à 4 lectures de l'index quand aucune base n'est autour | rien |
| « Dans quel claim est ce bloc ? » (`ClaimData.getAt`) | toutes les bases parcourues à chaque appel : chaque bloc d'une explosion (2 fois), chaque écoulement d'eau ou de lave, chaque feu, chaque apparition de zombie, chaque coup sur une vitre… (coût × nombre de bases) | 1 à 4 cases de 64 blocs lues | rien (même claim trouvé) |
| Économie : sauvegarde de `comptes.json` | arbre JSON de **tous** les comptes et de leurs 50 transactions sur le fil du serveur, chaque minute, toutes les 1,5 s pendant les gros paiements, à chaque achat à l'hôtel des ventes (dizaines de ms avec quelques milliers de comptes) | le fil du serveur copie seulement solde, nom et journal (~0,3 ms pour 3 000 comptes) ; JSON fait sur le fil d'écriture | rien (même fichier) |
| Étoiles des conteneurs (`RotationLoot`) | tous les joueurs dans le même tick, une fois par seconde (~1 ms d'un coup à 150 joueurs en ville) | un joueur sur 20 à chaque tick | rien (toujours une fois par seconde) |
| `/base info` (`FicheBase`) | ~10 fichiers des marchands lus sur le fil du serveur à chaque ouverture | lus à côté, la fiche part ensuite | la fiche arrive une fraction de seconde plus tard |
| Blocs de base qui se chargent (`StructureIntegrity`) | 2 ensembles neufs par bloc (plus d'un millier pour une grande base) | comparaison directe | rien |
| Barricades (`BarricadeEntity`) | 9 segments replacés à chaque tick (18 vecteurs, 9 boîtes) par barricade chargée, serveur et joueurs | seulement si elle bouge | rien |
| Portes du bunker (`DoorBlockEntity`) | déclaration relue à chaque tick par porte | rien tant qu'elle ne bouge pas | rien |

## À vérifier en jeu (serveur redémarré avec les nouveaux jars)

### Quêtes (le plus important : données des joueurs)
- [ ] Faire avancer une quête (tuer des zombies), attendre 30 s, **arrêter le serveur proprement**, redémarrer : la
      progression est là.
- [ ] Finir une quête payée, puis **tuer le serveur** (arrêt brutal) juste après la récompense : au redémarrage, la quête
      est bien finie et n'est pas payée une 2e fois.
- [ ] Se déconnecter puis se reconnecter tout de suite (moins d'une seconde) : la progression est celle d'avant la
      déconnexion.
- [ ] Journal du serveur : aucune ligne « [heraliz_quetes] écriture de … impossible ».

### Déplacements
- [ ] `/home` depuis le spawn alors que personne n'est près de la base : arrivée devant l'armoire comme avant, **pas
      d'alerte `[Diag] Blocage`** au départ.
- [ ] Mourir avec « À la base » proposé, attendre quelques secondes sur l'écran de mort, choisir « À la base » : arrivée
      normale, pas d'alerte. Pareil en choisissant « Au camp » (rien ne change).
- [ ] `/rtp` depuis le spawn : arrivée sur un sol plat, loin des bases, hors des zones interdites, pas d'alerte au départ.
      Le refaire plusieurs fois (staff : sans délai) : jamais dans l'eau ni dans un bloc.

### Bases
- [ ] Poser des pièces dans une zone permise, puis essayer dans une zone interdite (ville, rails) : refus comme avant ;
      plan en main, les zones rouges s'affichent comme avant.
- [ ] Changer `config/crac/zones_construction.json` (staff) : pris en compte en 5 s au plus.
- [ ] Sauter sur un plancher posé sur un demi-mur, et sur le dessus d'un demi-mur : l'aide au saut marche comme avant.
- [ ] Dans une base : coffres, portes, protections, explosifs (C4), eau / lave versées au bord d'une base (ne rentrent
      pas), feu (ne rentre pas) : comme avant. Deux bases proches : chaque bloc reste à la bonne base.
- [ ] Zombies : toujours aucune apparition dans une base.
- [ ] `/base info` (et la fiche ouverte depuis le tutoriel) : la fiche s'ouvre, avec les prix des marchands.
- [ ] S'approcher en volant d'une grande base pas chargée : elle apparaît entière, pas d'alerte.

### Économie
- [ ] Gagner / dépenser des hérals, acheter à l'hôtel des ventes, déposer à la banque du clan, puis arrêter et
      redémarrer : soldes et journaux (`/eco journal`) identiques ; `heralisz_economie/comptes.json` et ses copies
      `.1` à `.5` toujours écrits.

### Loot, bunker
- [ ] En ville : les étoiles des conteneurs pleins scintillent comme avant (une fois par seconde, pour chacun).
- [ ] Barricade posée : elle arrête les joueurs et les balles, se casse comme avant.
- [ ] Portes du bunker (cartes, salles à carte) : s'ouvrent, se ferment, restent ouvertes / fermées comme avant.

### Mesure
- [ ] Avec du monde : `/diag mesure 60` avant / après (ou `/hz perf`) ; les pointes toutes les 30 s (quêtes) et à chaque
      sauvegarde de l'économie ne doivent plus apparaître.

## Pas changé (à savoir, propositions)

- Sauvegarde des bases (`crac_structures`, toutes les pièces) et des clans (`clans.json`) : l'arbre NBT / JSON complet est
  encore relevé sur le fil du serveur (estimé 10 à 50 ms pour 10 000 à 50 000 pièces à chaque sauvegarde du monde,
  ~5 à 10 ms par minute pour les clans) ; l'écrire en fond est déjà fait. Le réduire demande un cache par pièce / par clan
  avec beaucoup d'endroits qui les modifient : à faire à part, avec des essais.
- Coffre du clan : encore écrit sur le fil du serveur quand il a changé (au plus une fois par minute), exprès dans le
  même ordre que la sauvegarde des joueurs (jamais d'objet en double après un plantage) : laissé tel quel.
- Entretien des bases (chaque minute) : toutes les pièces passées en revue dans le même tick (~1 à 3 ms pour 30 000
  pièces) : pas étalé.
- Tutoriel avec Varga : la parcelle n'a que 2 s pour se charger avant l'arrivée ; une parcelle jamais créée (beaucoup de
  tutoriels en même temps) peut encore geler un court instant à l'arrivée.
- PNJ : IA de Minecraft (regarder les joueurs), une quinzaine au spawn, environ 0,1 ms par tick en tout : laissée.
- Aucun réglage de Minecraft / Mohist proposé par ce chantier.
