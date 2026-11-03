# Essais en jeu du 03/11 : chantier « armes-tir » (rien testé en jeu par Claude)

Retours du propriétaire après son premier passage en jeu (02/11 au soir) : fusils à pompe, « 15 tirs refusés par TaCZ »
au minigun, balles qui touchent à l'écran sans faire de dégâts.

Jars touchés (joueurs ET serveur, même fabrication des deux côtés) : HeraliZ Tactique **2.44.0** (`armes`), Équipement
**1.0.4** (`equipement`, serveur seulement en pratique), Spartan **2.10.3** (`anticheat`, serveur seulement en pratique).
Réglages changés : `serveur/config/heraliz_tactique/armes.json` (écrit par `outils/equilibrage_armes.py`) et
`serveur/config/spartan-common.toml` (nouvelle clé `cooldownRatio`) : mise en ligne complète puis redémarrage.
Jar de TaCZ et gunpacks intacts (quatre petits mixins, `require = 0` : si TaCZ change, son comportement revient).

**Rien n'a été compilé avec Gradle** (le dépôt de Forge est bloqué ici) : les classes changées ont été compilées avec
`javac` (Java 17, tous les avertissements) contre le vrai jar de TaCZ 1.1.8 et des « bouchons » de Minecraft, Forge et
Mixin ; les essais de Spartan (`Point14Test`, 7 / 7) passent ; la correction de l'horloge a été essayée hors du jeu (plus
bas). Le contrôle de branche de GitHub fera la vraie fabrication : à regarder en premier (surtout les mixins).

## 1. Fusils à pompe

### Ce qui se passait
- Chaque fusil à pompe avait une décharge de 34 à 39 dégâts et un **plafond de 13 par tir** pour tous : 4 plombs sur 9
  suffisaient pour atteindre 13, et toutes les armes faisaient 13, quelle que soit leur rareté ou leur mécanisme (avec une
  armure Épique : 13 × 0,59 = 7,7, environ 3,8 cœurs).
- **DB-4 Ursus** (`maxstuff:db_50`) : ancien fusil .50 passé en calibre 12 le 22/10, il tirait **une seule balle**.
- **Stakeout** : en visant, ses 8 plombs partaient avec 0,25° d'écart (les autres : 2,5 à 4,75°) : un seul trou, comme une
  balle.

### Ce qui change
- La décharge entière (tous les plombs au corps, à bout portant) est maintenant le vrai chiffre, partagé entre les plombs
  (moins de 1 cœur par plomb : un seul plomb ne fait presque rien), selon le mécanisme, puis la rareté (+4 % par rang,
  comme les autres armes) :
  - **à pompe** (M870, M870 tactique, Stakeout) : 17,5 en Rare (Stakeout Épique 18,2) ;
  - **canons doubles** (DB long, DB court, DB tactique, 686, Olympia, DB-4 Ursus) : 15 en Rare (14,4 en Peu commun) ;
  - **automatiques** (AA-12, Saiga 12) : 12,96 en Légendaire, plafond 13 : **comme avant** ;
  - **pistolet à plombs** (Deagle 12G) : 12, comme avant.
- Plafond d'un tir (tête comprise, avant l'armure) : 19 pour les armes qu'on réarme (une décharge entière dans la tête
  sans casque ne tue jamais d'un coup), 13 pour les automatiques, 12 pour le pistolet. Deux canons tirés d'un coup (rafale)
  comptent comme un seul tir.
- **Tous dispersent leurs plombs** : DB-4 Ursus 8 plombs (et ils ne traversent plus un joueur pour en toucher un autre) ;
  écart minimal de 2,5° pour toutes les armes à plombs, même en visant et avec des accessoires (Stakeout compris).
- Tout est dans `armes.json` (clés `plombs` et `dispersion_min` en plus), rechargeable par `/recharger armes`.

### Tableau (calculé comme le serveur : `python outils/equilibrage_armes.py --pompes`)
Tenue complète : Rare 32 % au corps / 26 % à la tête, Épique 41 / 34, Légendaire 50 / 42 (plafond des armures 60 %,
jamais atteint par une tenue). Visée au milieu du torse ; à 15 et 20 blocs, en visant ; « tirs pour tuer » : sans armure /
Rare / Épique / Légendaire, en moyenne.

| Arme | Rareté | Mécanisme | Plombs × dégâts | Décharge | Bout portant | 15 blocs : plombs qui touchent ; tirs | 20 blocs : plombs ; tirs |
|---|---|---|---|---|---|---|---|
| M870 (`tacz:m870`) | Rare | à pompe | 9 × 1,94 | 17,5 | 2 / 2 / 2 / 3 | 4,9 sur 9 ; 3 / 3 / 4 / 4 | 3,6 ; 4 / 6 / 6 / 7 |
| M870 tactique (`maxstuff:m870t`) | Rare | à pompe | 9 × 1,94 | 17,5 | 2 / 2 / 2 / 3 | 4,9 sur 9 ; 3 / 3 / 4 / 4 | 3,6 ; 4 / 6 / 6 / 7 |
| Stakeout | Épique | à pompe | 8 × 2,27 | 18,2 | 2 / 2 / 2 / 3 | 5,7 sur 8 ; 3 / 4 / 5 / 5 | 4,5 ; 3 / 5 / 5 / 6 |
| DB long | Peu commun | canons doubles | 10 × 1,44 | 14,4 | 2 / 3 / 3 / 3 | 7,0 sur 10 ; 3 / 4 / 4 / 5 | 5,7 ; 3 / 5 / 5 / 6 |
| 686 | Peu commun | canons doubles | 10 × 1,44 | 14,4 | 2 / 3 / 3 / 3 | 7,1 sur 10 ; 3 / 4 / 4 / 5 | 5,7 ; 3 / 4 / 5 / 6 |
| Olympia | Peu commun | canons doubles | 10 × 1,44 | 14,4 | 2 / 3 / 3 / 3 | 7,1 sur 10 ; 3 / 4 / 4 / 5 | 5,7 ; 3 / 5 / 5 / 6 |
| DB-4 Ursus (`maxstuff:db_50`) | Peu commun | canons doubles | 8 × 1,80 | 14,4 | 2 / 3 / 3 / 3 | 4,4 sur 8 ; 3 / 5 / 5 / 6 | 3,2 ; 13 / 18 / 21 / 24 |
| DB court (`tacz:db_short`) | Peu commun | canons doubles | 16 × 0,90 | 14,4 | 2 / 3 / 3 / 3 | 6,3 sur 16 ; 7 / 10 / 12 / 13 | 4,2 ; 10 / 15 / 17 / 20 |
| DB tactique | Rare | canons doubles | 16 × 0,94 | 15,0 | 2 / 2 / 3 / 3 | 6,3 sur 16 ; 7 / 10 / 11 / 13 | 4,2 ; 10 / 14 / 16 / 19 |
| AA-12 | Légendaire | automatique | 10 × 1,30 | 13,0 | 2 / 3 / 3 / 4 | 4,8 sur 10 ; 4 / 5 / 6 / 7 | 3,4 ; 5 / 7 / 8 / 9 |
| Saiga 12 | Légendaire | automatique | 10 × 1,30 | 13,0 | 2 / 3 / 3 / 4 | 4,8 sur 10 ; 4 / 6 / 7 / 8 | 3,4 ; 6 / 8 / 9 / 11 |
| Deagle 12G | Épique | pistolet à plombs | 10 × 1,20 | 12,0 | 2 / 3 / 3 / 4 | 4,5 sur 10 ; 5 / 7 / 7 / 9 | 3,1 ; 11 / 15 / 17 / 20 |

Avant : 13 par tir à bout portant pour toutes (2 / 3 / 3 / 4). Les canons sciés (DB court, DB tactique) et le DB-4 Ursus
perdent beaucoup avec la distance (paliers de leurs gunpacks : -25 % à 5 ou 18 blocs, -50 à -73 % au-delà).

### À vérifier
- [ ] Démarrage du serveur : ligne `[armes] 118 armes réglées contre les joueurs, 12 avec un plafond par tir, 12 armes à
      plombs (heraliz_tactique/armes.json)` ; aucune erreur « Mixin » sur `TaczPlombsMixin`.
- [ ] Tirer contre un mur à 5-10 blocs avec CHAQUE fusil à pompe, en visant et sans viser : plusieurs impacts dispersés
      (DB-4 Ursus : ~8 ; Stakeout en visant : plus un seul trou).
- [ ] Sur un joueur sans armure, à bout portant : M870 → 2 tirs tuent, jamais 1 ; un seul plomb qui touche (de loin, au
      bord) fait moins d'un cœur.
- [ ] Avec une tenue Épique complète, à bout portant : M870 ou Stakeout → 2 tirs bien placés tuent ; AA-12 → 3, comme
      avant. Tenue Rare : canons doubles → 3 tirs (DB tactique : 2).
- [ ] Tir à bout portant dans la tête d'un joueur sans casque au M870 : il survit (1 PV au plus bas).
- [ ] Infobulle : M870 « 17,5 » (ou « 1,94 x 9 »), AA-12 « 12,96 », DB-4 Ursus « 14,4 ».
- [ ] Changer un chiffre (`degats`, `dispersion_min`) dans `config/heraliz_tactique/armes.json`, `/recharger armes` : pris
      en compte sans redémarrer.

## 2. « 15 tirs refusés par TaCZ » (minigun) et balles à blanc

### Ce qui se passait (vraie cause, dans TaCZ)
Chaque tir part du jeu du joueur avec son heure. Le serveur refuse un tir trop rapide pour l'arme (« cadence ») et un tir
dont l'heure ne colle pas à la sienne à 300 ms près (« horloge » : coupure Wi-Fi, voix, lag, horloge du PC qui dérive) ;
dans ce dernier cas, TaCZ refait l'échange d'horloge avec le joueur. **Défaut de TaCZ** : après ce nouvel échange, il
gardait l'heure du dernier tir réussi comptée avec l'ancienne horloge. Chaque tir suivant paraissait « avant » le
précédent : **tous refusés pour cadence**, jusqu'à ce que le joueur change d'arme, meure ou change de monde ; le jeu du
joueur restait aussi bloqué (impossible de recharger). Le joueur voyait et entendait son arme tirer (le tir est joué chez
lui avant la réponse du serveur), mais aucune balle ne partait : **des balles à blanc**. Au minigun (20 tirs par seconde),
l'anticheat comptait 15 refus en moins d'une seconde : d'où le message.

### Ce qui change
- Tactique : à chaque nouvel échange, l'heure du dernier tir est recomptée avec la nouvelle horloge (même instant réel) ;
  un tir parti avec l'ancienne horloge juste après l'échange est remis à l'heure (3 s au plus). La cadence reste vérifiée
  sur le temps réel : **un tir trop rapide d'un jeu modifié reste refusé**.
- Spartan : refus de cadence et refus d'horloge comptés à part (une coupure ne compte plus comme de la cadence) ; l'alerte
  « cadence » demande en plus qu'au moins 20 % des tirs de la fenêtre soient refusés (`cooldownRatio` de
  `spartan-common.toml`) : un « rapid fire » se fait refuser un tir sur deux ou plus, un joueur honnête presque jamais.
  Messages : « N tirs refusés par TaCZ pour cadence en 10 s (X % des tirs, Y acceptés) » ou « … pour l'horloge … ».

Essai hors du jeu (vraie classe de correction, vraies données du tireur de TaCZ, contrôles du serveur de TaCZ recopiés ;
10 s de tir, une coupure réseau de 500 ms au bout de 3 s) :

| Cas | TaCZ seul | Avec la correction |
|---|---|---|
| Minigun | 60 tirs acceptés, 128 refusés (cadence) | 184 acceptés, 4 refusés (cadence) + 12 en retard pendant la coupure |
| Fusil à 600 coups/min | 30 acceptés, 64 refusés | 93 acceptés, 1 refusé + 6 en retard |
| Sans coupure (minigun) | 200 acceptés | 200 acceptés |
| Triche « rapid fire » (2 × la cadence) | au plus la cadence | au plus la cadence (93 / 100) |

### À vérifier
- [ ] Au démarrage et en jeu : aucune erreur « Mixin » sur `TaczHorlogeBaseMixin`, `TaczHorlogeTirMixin`,
      `ShooterDataHolderHorlogeMixin`.
- [ ] Minigun : plusieurs longues rafales (10 s), à deux joueurs au moins : plus d'alerte « tirs refusés par TaCZ ».
- [ ] **Coupure** (le plus important) : en tirant en continu (minigun ou fusil automatique), couper le Wi-Fi 1 à 2 s puis le
      remettre (ou s'éloigner de la box) : après la coupure, l'arme retire de vraies balles (traçantes, dégâts) SANS changer
      d'arme, et on peut recharger. Avant : balles à blanc jusqu'au changement d'arme.
- [ ] Journal du serveur pendant les combats : lignes `[armes] tirs refusés par le serveur depuis le dernier bilan` avec
      peu de `COOL_DOWN` ; une ligne `[armes] horloge des tirs de TaCZ recalée N fois après un tir…` quand il y a eu une
      coupure (c'est exactement le cas qui faisait des balles à blanc).
- [ ] Staff : `config/spartan/tirs.log` après une soirée : refus rares chez les joueurs honnêtes.

## 3. Touches qui ne comptent pas

### Ce qui a été trouvé
- **Balles à blanc** après une coupure : la cause du point 2 (corrigée). C'est le cas le plus probable du « je tire, il ne
  prend rien » : une seule coupure de plus de 300 ms en plein combat suffisait à rendre l'arme muette jusqu'au changement
  d'arme.
- La compensation de latence de TaCZ (le serveur remet la cible là où le tireur la voyait, d'après son ping) est bonne à
  un demi-tick près avec nos réglages (`ServerHitboxOffset = 3`, ping gardé jusqu'à 350 ms) ; notre hitbox en morceaux
  essaie déjà le corps sur tout un tick de mouvement de la cible. Rien de faux trouvé dans nos balles (balles toujours
  actives, balles figées retirées, traçantes, vitres, murs) ni dans l'anticheat (il ne touche jamais aux tirs).
- **Ce qui reste (réseau, pas corrigeable)** : la traçante qu'on voit à l'écran n'est pas ce que le serveur juge. Le
  serveur juge d'après l'endroit où était la cible quand on a tiré ; la traçante arrive un peu plus tard sur une image de
  la cible en retard de son ping. Contre une cible qui court de côté, une balle qui « frôle » le bord du corps à l'écran
  peut ne pas compter (environ 0,2 bloc par tranche de 50 ms de ping). Viser le centre du corps reste fiable.

### À vérifier
- [ ] Combat à deux, les deux qui bougent en visant (lunette) : la grande majorité des balles qui touchent le centre du
      corps comptent (marqueur de touche, dégâts), y compris après une coupure.
- [ ] Si des balles à blanc reviennent : noter l'heure et regarder le journal du serveur à ce moment (`[armes] tirs refusés`
      : quelle raison ? `horloge … recalée` ?) et me le renvoyer.

## 4. Armure et plomb à la tête (Équipement 1.0.4)
TaCZ inflige chaque balle en deux coups (la part normale, puis la part « perce-armure » : 25 à 33 % pour les fusils à
pompe). Depuis la correction du point 27 (02/11), la part perce-armure d'un plomb à la tête comptait le gilet au lieu du
casque. Maintenant les deux parts d'une balle à la tête comptent le casque, et aucune autre balle.
- [ ] Joueur avec un bon gilet et sans casque : un tir à la tête fait autant qu'avant le gilet (le gilet ne protège pas la
      tête) ; avec un bon casque et sans gilet : un tir au corps n'est pas réduit, un tir à la tête l'est.

## Pas fait / pas testé
- Rien n'a été essayé en jeu ; pas de fabrication Gradle (voir en haut).
- Portée de suivi des balles et `ServerHitboxOffset` : inchangés (à régler seulement après un relevé en jeu).

## Questions pour le propriétaire
- À pompe : deux décharges bien placées à bout portant tuent maintenant aussi une armure **Épique** (avant : trois). D'accord ?
- La rareté compte comme pour les autres armes (+4 % par rang). En veux-tu plus ?
- Canons doubles entre les deux (14,4 à 15 : trois tirs contre une armure Rare). D'accord ?
