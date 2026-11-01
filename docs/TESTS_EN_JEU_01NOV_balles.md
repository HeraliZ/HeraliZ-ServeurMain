# Essais en jeu — chantier « balles TaCZ » (01/11)

Version : **Tactique 2.42.0** (module `armes`). Le changement est côté serveur (`BallesEnVol`) ; le réglage est déclaré dans
la partie commune (comme `[vitres]`) : le jar des joueurs change aussi, mais un joueur qui a encore l'ancien n'est pas gêné.
Jar de TaCZ intact (`libs/tacz-1.20.1-1.1.8-hotfix.jar`), aucun mixin.

**Rien de ceci n'a été essayé en jeu, ni compilé avec Gradle** (dépôt de Forge bloqué ici). Les trois fichiers Java changés
ont été compilés (javac, Java 17, tous les avertissements) contre des « bouchons » de Minecraft, Forge et TaCZ, et la logique
a été essayée hors du jeu (plus bas). La fabrication complète sera faite par le contrôle de branche de GitHub.

Lu pour trouver la cause : `EntityKineticBullet`, `ModEntities`, `ModernKineticGunScriptAPI`, `BulletData` de TaCZ 1.1.8
(bytecode décompilé, et la source publique de TaCZ pour les noms de son `EntityType`) ; patchs de Mohist 1.20.1
(`ServerLevel`, `Entity`, `ChunkMap`, `MinecraftServer`) et ses `ActivationRange`, `TrackingRange`, `SpigotWorldConfig` ;
Minecraft 1.20.1 (`ServerLevel.tick`, `DistanceManager`, `ChunkMap.TrackedEntity`) ; les 91 fiches d'armes des gunpacks.

## La vraie cause

### Pourquoi ~40 balles bougent sur ~2 500

- Les balles vont très vite : médiane des gunpacks 240 m/s, × 2 (`GlobalBulletSpeedModifier` de `tacz-common.toml`), soit
  **24 blocs par tick** ; elles vivent **0,5 à 1,1 s** (10 à 22 ticks) pour presque toutes nos armes.
- TaCZ fait avancer la balle et teste sa fin de vie (`tickCount >= life - 1`) **dans le tick de la balle**.
- Minecraft ne fait tourner une entité que dans la **zone simulée** : les tronçons à `simulation-distance` d'un joueur
  (4 sur le Ptero : 64 à 79 blocs autour du tronçon du tireur). Un tir manqué en sort après 3 ou 4 pas.
- Hors de cette zone, la balle n'est plus jamais tournée : **ni son pas, ni le test de sa vie**. Elle reste là, figée, tant que
  son tronçon est chargé (un joueur à moins de ~10 tronçons : distance de vue 8 + 2), donc tant que le combat dure à côté.
- Banc à 150 bots : 92 tirs par seconde ; chaque balle passe en moyenne 9 ticks dans la zone (41 balles tournées par tick) ;
  toutes celles qui en sortent s'entassent : **2 300 à 3 150 présentes** en même temps. À 100 bots : 65 tirs par seconde,
  3,7 ticks chacune dans la zone (12 tournées par tick), 992 présentes.
- Parmi les ~40 « tournées », beaucoup n'avançaient pas non plus pendant le banc à 150 (135 ms par tick) : voir la 2e cause.

### Sauvegarde

**Non** : TaCZ déclare la balle `noSave()` (comme `noSummon()` et `fireImmune()`). Elle n'est jamais écrite avec le tronçon et
disparaît quand son tronçon se décharge ; rien ne revient après un redémarrage.

### Portée de suivi

TaCZ déclare 80 blocs (`clientTrackingRange(5)`), mais Mohist la remplace par la portée « other » de `spigot.yml` : **64
blocs** (une balle n'est ni un objet au sol, ni de l'expérience, ni un monstre, ni un animal). Distance à plat (x, z), au plus
la distance de vue (128).

### Les deux bizarreries des balles figées

- **Traçante fantôme** : un joueur qui arrive à 64 blocs d'une balle figée reçoit son apparition ; TaCZ la fait voler chez lui
  depuis cet endroit, pour toute sa vie.
- **Balle qui touche des minutes après** : à la simulation 4, ce même joueur remet la balle dans la zone simulée (à 64 blocs, il
  est forcément à 4 tronçons au plus) : elle repart avec sa vitesse et le reste de sa vie, et peut toucher quelqu'un.

### 2e cause : la portée d'activation de Spigot (tirs à distance en retard ou perdus)

Mohist applique `org.spigotmc.ActivationRange` (`entity-activation-range: misc 16` de `spigot.yml`). Une balle est une entité
« misc », et pas dans la liste des toujours actives de Spigot (ThrowableProjectile, AbstractHurtingProjectile, fusées,
tridents… mais pas `Projectile`, dont la balle de TaCZ hérite). À plus de 16 blocs de tout joueur, elle ne tourne que si son
activation n'a pas expiré, ou au tick où `(currentTick - activatedTick - 1) % 20 == 0` (jamais au sol, elle est alors
« immunisée » 20 ticks de plus).

Le piège : le `currentTick` de Mohist n'est pas un compteur de ticks, c'est **l'horloge divisée par 50 ms**. À 20 ticks par
seconde il avance de 1 et la balle retrouve son immunité au bon moment ; **dès qu'un tick dure plus de 50 ms**, il saute de 2
ou 3, le bon moment est manqué, et la balle s'arrête jusqu'au prochain multiple de 20 (près d'une seconde), en vieillissant
quand même (Spigot compte le tick sauté). Avec une vie de 0,8 s, elle meurt en chemin.

Simulation hors du jeu (script du chantier, pas gardé dans le dépôt ; tir sur un joueur, personne d'autre près de la balle,
vitesse et vie médianes, ticks de durée variable) :

| Durée moyenne d'un tick | Cible à 40 blocs | Cible à 60 à 130 blocs |
|---|---|---|
| 40 ms (± 8) | rien | ~1 % des balles n'arrivent jamais |
| 48 ms (± 10) | rien | ~6 % jamais arrivées |
| 55 ms (± 15) | rien | ~17 % jamais arrivées |
| 70 ms (± 20) | rien | 30 à 42 % perdues, jusqu'à 12 % en retard de ~12 ticks |
| 100 à 135 ms (banc à 150 avant l'étape 2) | rien | 40 à 64 % perdues, 32 à 40 % en retard de 7 à 10 ticks |

À 40 blocs, la balle est à moins de 16 blocs du tireur ou de la cible à chaque pas : jamais arrêtée. C'est donc un **vrai
défaut des tirs à distance**, dès que le serveur rame un peu (gros combat, sauvegarde).

## Ce qui change (`modules/armes/src/serveur/java/fr/heraliz/tactique/serveur/BallesEnVol.java`)

| Endroit | Avant | Après | Pour le joueur |
|---|---|---|---|
| Balle qui sort de la zone simulée (`ServerLevel.tick` → `DistanceManager.inEntityTickingRange`) → `BallesEnVol.finDuTick` | figée, suivie et jugée pour les joueurs autour, tant que son tronçon reste chargé : 2 300 à 3 150 à 150 joueurs | retirée à la fin du tick qui suit son dernier pas : ~40 à 60 balles dans le monde (seulement celles en vol) | plus de traçante fantôme, plus de balle qui touche des minutes après ; rien d'autre (voir plus bas) |
| Portée d'activation de Spigot (`ActivationRange.checkIfActive`) → `BallesEnVol.arrivee` | balle à plus de 16 blocs de tout joueur arrêtée près d'une seconde dès qu'un tick dépasse 50 ms | `activatedTick` de la balle mis au maximum à son arrivée : Spigot la voit toujours active (comme une flèche en vol) ; les autres entités ne changent pas | les tirs à distance arrivent à l'heure, même quand le serveur rame |

Comment c'est fait, sans parcourir le monde :
- à chaque tir (`EntityJoinLevelEvent`, côté serveur, priorité la plus basse comme `BallesTrain`), la balle entre dans un petit
  tableau ;
- à la fin de chaque tick du serveur (tous les mondes ont tourné leurs entités), pour chaque balle du tableau : retirée par
  TaCZ → oubliée ; son `tickCount` a changé → notée ; **pas changé depuis le passage d'avant** (son monde ne l'a pas tournée
  pendant un tick entier) → retirée ;
- une balle n'est jugée qu'après un tick entier dans le monde (première vue = notée seulement), quel que soit le moment du
  tir (paquet d'un joueur, bot, fin du tick) ;
- un tick sauté par Spigot compte quand même (`tickCount` avance) : ce n'est jamais pris pour une balle figée.

Pourquoi personne ne voit une balle disparaître : une balle qui n'est plus tournée est dans un tronçon à plus de 4 tronçons
de tout joueur (simulation 4), donc à **65 blocs au moins** de chacun, au-delà de la portée de suivi (64) : Minecraft avait déjà
envoyé son retrait aux joueurs qui la voyaient. Une balle qui avance n'est jamais retirée. (Si un jour la simulation passe sous
4, ou la portée « other » au-dessus de 16 × simulation, une balle pourrait disparaître chez un joueur entre 49 et 64 blocs :
à garder en tête.)

Rejeté : `misc: 0` dans `spigot.yml` aurait rendu toujours actives TOUTES les entités « misc » (wagonnets, porte-armures,
blocs qui tombent, véhicules, corps, cadres…), bien plus cher.

### Ce que ça veut dire pour un tir lointain

- Un tir à **moins de ~130 blocs** (plus loin que la distance de vue de 128) : les zones simulées du tireur et de la cible se
  touchent, la balle vole à pleine vitesse jusqu'à toucher ou finir sa vie, sans arrêt.
- Plus loin, il peut y avoir un trou non simulé entre les deux : la balle ne peut pas le traverser (TaCZ ne la fait pas avancer).
  Avant : elle y restait figée (et se réveillait plus tard) ; maintenant : elle disparaît là. Le résultat du tir est le même
  (pas de touche), sans balle fantôme.
- Balle explosive (roquette) tirée vers une zone sans personne à plus de ~70 blocs : avant, figée, elle explosait quand
  quelqu'un approchait (des minutes après) ; maintenant elle disparaît sans exploser, comme elle ne serait jamais arrivée.

## Réglages (`[balles]` de `heralisz_tactical-server.toml`, relus en marche, une fois par seconde)

- `toujours_actives = true` : la portée d'activation de Spigot ne s'applique plus aux balles. `false` : comme avant.
- `retirer_figees = true` : une balle qui n'avance plus est retirée. `false` : comme avant (elle reste figée).

Au démarrage, le journal dit : `[tactique] Balles de TaCZ : toujours actives = true (portée d'activation de Spigot (Mohist)
ignorée pour elles), figées retirées = true`. Si la 2e partie dit « pas de portée d'activation sur ce serveur » sur le Ptero,
le champ de Mohist n'a pas été trouvé : me le dire. Toutes les 30 minutes (seulement s'il y en a eu) : `… N balles figées
retirées en 30 min (… depuis le démarrage), M en vol`. La taille du tableau est surveillée par `/diag fuites`
(`tactique.balles_en_vol`).

## Portée de suivi des balles à 32 ou 48 blocs (pas fait, à décider)

Ce qui changerait (seulement pour `tacz:bullet`, par un mixin sur `org.spigotmc.TrackingRange` ou `ChunkMap.addEntity` ;
changer « other » toucherait aussi véhicules, largages, grenades, corps…) :

| | 64 (aujourd'hui) | 48 | 32 |
|---|---|---|---|
| Traçante d'un tir qui arrive sur vous | vue sur ses 64 derniers blocs (~3 ticks) | 48 derniers blocs (~2 ticks) | 32 derniers blocs (1 à 2 ticks, à peine visible) |
| Fusillade entre d'autres joueurs, vue de loin | traçantes vues jusqu'à 64 blocs | jusqu'à 48 | jusqu'à 32 : au-delà, seulement le bruit |
| Votre propre traçante | suivie jusqu'à 64 blocs devant vous | 48 | 32 : on ne la voit plus arriver sur une cible à 50 blocs |
| Sniper repéré par sa traçante | sur 64 blocs | sur 48 | sur 32 : plus dur de savoir d'où il tire |
| Dégâts, touches, bruit des tirs, impacts | inchangés (décidés par le serveur, impacts envoyés à part) | inchangés | inchangés |
| Serveur : apparitions de balles envoyées | 100 % | ~75 à 85 % | ~50 à 60 % (les joueurs sont groupés près du tireur et de la cible) |
| Serveur : ms par tick à 150 joueurs en combat | — | ~0,1 à 0,4 ms de moins | ~0,2 à 0,9 ms de moins |
| Réseau (~200 octets par apparition, ~2 à 4 Mbit/s avant compression à 150 joueurs en combat) | — | ~20 % de moins | ~40 à 50 % de moins |

Avec ce chantier, les balles figées ne coûtent plus rien : il ne reste que les balles en vol, donc le gain d'une portée plus
courte est surtout réseau. Chiffres estimés (nombre de joueurs autour d'une balle supposé de 15 à 25 à 64 blocs) : à mesurer
avec `/diag profil` en combat (part de `ServerEntity.addPairing` et `EntityKineticBullet.writeSpawnData`).

## Gain estimé en combat à 150 joueurs

- **Entités** : −2 300 à −3 150 (≈ −60 % des ~4 100 du banc) ; dans le monde, il ne reste que les ~40 à 60 balles en vol.
- **Suivi des entités** (`ChunkMap.move` avec l'index de l'étape 2, même essai hors du jeu que l'étape 2 rejoué sans balles
  figées) : 139 → 44 entités jugées par paquet de mouvement, temps −40 % : **~1,5 à 3 ms par tick** de moins (3 à 5 ms estimés
  en jeu à l'étape 2). Si l'étape 2 est coupée (réglage ou sécurité), ce chantier seul retire ~3/4 du coût de Minecraft
  (même essai : 13,8 → 3,5 ms hors du jeu ; 77 ms → ~20 ms en jeu).
- **Boucle des entités et `ChunkMap.tick`** (chaque balle figée y passait à chaque tick sans tourner, avec un objet créé) :
  **~0,3 à 0,6 ms**.
- **Apparitions fantômes** (paquets d'apparition des balles figées quand un groupe approche) : **~0,1 à 0,5 ms**, et la
  traçante en moins chez les joueurs.
- **Train** (quand il roule, `BallesTrain` regardait chaque balle suivie, figées comprises, contre les deux rames à chaque
  tick) : **~1 ms** de plus.
- Total : **~2 à 4 ms par tick** (~4 à 7 % d'un tick de 55 ms), +1 ms quand le train roule ; ~2 à 5 Mo de mémoire.
- Coût ajouté : le passage de fin de tick sur les seules balles en vol (quelques microsecondes) ; les balles que Spigot
  arrêtait tournent de nouveau, ~0,2 à 0,5 ms en combat lourd quand le serveur rame (rien sous 50 ms par tick) : c'est le prix
  des tirs qui comptent de nouveau.

## Essai hors du jeu

`EssaiBalles` (faux serveur autour de la vraie classe, pas gardé dans le dépôt) : 1,16 million de balles (qui touchent, qui sortent de la zone, jamais
tournées, sautées par Spigot), tirées entre deux ticks, à la fin du tick avant ou après le passage ; plus des balles côté
joueur. Résultat : **0 erreur** ; aucune balle tournée pendant le dernier tick retirée ; aucune balle sautée par Spigot
retirée ; une balle figée retirée 1 tick après son dernier pas (2 si elle n'a jamais été tournée) ; tableau d'au plus ~60
balles, vide à la fin ; `activatedTick` mis au maximum pour chaque balle du serveur, jamais côté joueur ; réglage coupé : rien
retiré, rien suivi.

## À vérifier en jeu

1. **Démarrage** : la ligne `[tactique] Balles de TaCZ : toujours actives = true (portée d'activation de Spigot (Mohist)
   ignorée pour elles), figées retirées = true` dans le journal du Ptero ; aucune erreur.
2. **Combat (ou banc à 150 bots)** : `/diag etat` → dans « Entités les plus nombreuses », `tacz:bullet` doit rester à quelques
   dizaines (avant : 2 300 à 3 150) ; `ChunkMap.move` et le tick moyen doivent baisser ; me renvoyer le résumé.
3. **Tirs à distance** : tirer sur un joueur (ou un zombie) à 60, 90 et 120 blocs, pendant qu'un gros combat ou le banc fait
   ramer le serveur : la touche arrive tout de suite, jamais une seconde après, jamais « perdue ».
4. **Traçantes fantômes** : après un long échange de tirs, marcher vers l'endroit où partaient les balles manquées (à ~70 à 150
   blocs des tireurs) : plus aucune traçante qui part de nulle part, plus aucune touche venue de nulle part.
5. **Rien ne disparaît en vol** : à côté d'un tireur, et à 30-60 blocs de lui, les traçantes vont jusqu'au bout comme avant
   (jusqu'à l'impact ou à 64 blocs de vous) ; tirs à travers une vitre, sur le train, au fusil à pompe, à la roquette :
   inchangés.
6. **Réglages** : mettre `retirer_figees = false` puis `true` (et `toujours_actives`) dans `heralisz_tactical-server.toml`
   pendant que le serveur tourne : pris en compte en une seconde, sans erreur.

## Questions pour le propriétaire

1. **Portée de suivi des balles** : la garder à 64 blocs, ou la baisser à 48 (traçantes vues un peu moins loin, ~20 % de réseau
   en moins sur les balles) ou 32 (traçantes presque plus visibles de loin, ~40 à 50 % en moins) ? Je conseille 64 ou 48 : le
   gain serveur est petit maintenant que les balles figées sont retirées.
2. **Roquettes** vers une zone vide à plus de ~70 blocs : elles disparaissent maintenant sans exploser (avant : elles
   restaient figées et explosaient quand quelqu'un arrivait). D'accord ?
