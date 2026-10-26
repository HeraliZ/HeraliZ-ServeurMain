# Audit avant ouverture — 25/10

Fait sans aucun test en jeu (Claude ne lance pas le jeu). Ce qui a été réellement exécuté est marqué « mesuré » ou
« exécuté » ; tout le reste vient de la lecture du code.

## 1. Ce qui a été réellement vérifié

| Vérification | Comment | Résultat |
|---|---|---|
| Fabrication des 19 modules | `outils/construire.py` | réussie : 37 jars serveur, 40 joueurs |
| Contrôles du projet | `outils/verifier.py` (séparation jeu/serveur, noms Minecraft, GeckoLib, langues, accessoires…) | voir §5 |
| Dépendances des jars | script : chaque `mods.toml` (jars imbriqués compris), dépendances obligatoires et versions | aucune manquante ni incompatible, côté serveur (Forge 47.3.12 de Mohist) et joueurs (Forge 47.4.0) |
| Démarrage réel d'un serveur | Forge 47.3.12 isolé sur ce PC, nos 37 jars serveur, nos réglages, monde plat jetable | **démarre jusqu'au bout** (« Done »), s'arrête proprement ; 2 blocages trouvés (§3) |
| Journaux de la prod | ouverture du 24/10 lue sur le Ptero, en lecture seule | jusqu'à 58 joueurs ; tick médian ~40 ms, 95e centile 51–80 ms, pics 100–257 ms, **un gel de 6 s** pendant une sauvegarde automatique ; 3 exceptions seulement en 2 h |
| Contenu du Ptero | liste des fichiers, en lecture seule | anciens jars encore en place (voir `DEPLOIEMENT.md`) |

Pas fait : démarrage sous Mohist avec les plugins (le jar Mohist exact de la prod n'est pas sur ce PC), test avec de vrais
joueurs, mesure de FPS, test de charge.

## 2. Bugs trouvés et corrigés (relecture de tous les modules, côté admin et côté joueur)

**Signalés par les joueurs, confirmés et corrigés**
- **Bons** : dans une caisse, tous les bons avaient le même numéro ; seul le premier gagnant pouvait s'en servir, et les
  suivants étaient **supprimés**. Maintenant : le bon réglé par l'op est un modèle, chaque bon remis (caisse, kit, kit de
  départ, marchand, échange) reçoit un numéro et une signature neufs ; l'usage unique de chaque exemplaire est gardé, une
  copie faite par un joueur reste refusée (sans être supprimée).
- **Kits** : après un refus (anti-spam, place, carte…), le menu restait figé sur « en attente du serveur » : on croyait le
  kit de grade bloqué après le kit Survivant. Chaque refus répond maintenant au menu, avec un message.

**Autres bugs graves (P1)**
- `/redemarrage` tuait les joueurs en combat ou à terre (équipement perdu sur un corps).
- Un joueur réanimé pouvait revenir à terre à sa reconnexion (et mourir seul).
- Panel du staff : un Modérateur pouvait sanctionner un Admin pas encore connecté par /login.
- Vanish : un staff rétrogradé restait invisible à vie.
- Clans : un fichier `clans.json` abîmé effaçait tous les clans au premier arrêt.
- ClashWar : un joueur revenu en plein match devenait un « fantôme » intouchable qui faisait gagner son équipe.

**Bugs importants (P2)** : PB débités sans livraison si le site répondait lentement ; casino repayé après une
déconnexion ; grade acheté perdu quand le staff en donnait un autre ; double clic « Améliorer » = deux paliers payés ;
dissolution de clan sans avertissement (et coffre vidé sur la carte) ; objets du coffre de clan perdus après un plantage ;
écran de mort bloqué sur « Vérification… » après une reconnexion ; « À la base » proposé pendant le tutoriel ; arrivée
derrière le mur de la base ; chef jamais prévenu du matériel de sa première base ; points de saison des salles à carte
multipliés ; échange d'accessoire qui acceptait n'importe quel accessoire ; arme achetée revendable à Mila (argent infini) ;
fusil à pompe sans dégâts au 2e tir sur un vieux monde ; ClashWar : joueur sorti du tournoi sans le savoir ; Spartan
retirait le bonus de loot et le saut bloqué de la Mère des Ruines (fausses alertes).

**Plus petits (P3)** : 15 environ (messages trompeurs, recharges, recettes du Registre jamais fabricables, vente sans
paiement si l'économie ne répond pas, Bandage et Attelle refusés par la quête « Trousse de secours » et par le recycleur,
fusil M82A2 cassé dans le loot…). Détail par module : les comptes rendus sont dans l'historique de la session.

## 3. Performances

**Mesuré**
- Démarrage du serveur de test : ~90 s, tick moyen à vide ~1,3 ms.
- **Pose des arènes ClashWar** au premier démarrage : 250 à 340 ms de gel par tick pendant de longues secondes (chaque
  tronçon neuf était créé sur le fil serveur). **Corrigé et re-mesuré** : les tronçons sont demandés puis attendus, créés
  en tâche de fond. Même essai après correction (serveur vide, pose des 11 cartes en cours) : moyenne 4,2 ms, 95 % 10,4 ms,
  99 % 17,9 ms, pire tick 49 ms (avant : pire 336 ms, 99 % 115 ms).
- Anti-triche Spartan : 2,7 s de calcul au démarrage et après un `/reload` (une seule fois, avant l'arrivée des joueurs) :
  **ne jamais faire `/reload` en jeu**.
- Ouverture du 24/10 (prod) : gel de 6 s pendant une sauvegarde automatique, avec `sync-chunk-writes=true` et le script de
  pose des props joué en même temps (2 291 `forceload`, 1 426 `setblock`).

**Corrigé dans le code et les réglages**
- Planque / coffre VIP : le fichier de TOUS les coffres était réécrit, compressé, sur le fil serveur à chaque fermeture ;
  il part maintenant sur un fil à part (écriture atomique, attendue à l'arrêt).
- Première base d'un clan : écrivait toutes les données du monde d'un coup ; maintenant seulement son fichier.
- Journal du loot moins bavard pendant les poses ; `sync-chunk-writes=false` ; `level-name=dayz_map` ; monstres naturels
  70 au lieu de 250, chauves-souris coupées ; seuil du rapport de blocage à 100 ms.

**FPS des joueurs (corrigé)**
- Herbe du pack imposé : 67 % de faces invisibles retirées (98 faces par touffe en moyenne → 33 ; Minecraft : 4), aucun
  changement à l'image.
- 12,6 millions de pixels de textures d'armure GeckoLib jamais utilisées retirés de la grande texture des blocs (elle
  devrait passer de ~16384×8192 à 8192×8192 : moitié moins de mémoire graphique, et les cartes graphiques limitées à
  8192 peuvent la créer). **À vérifier en jeu** : armures GeckoLib visibles.
- Launcher : mémoire par défaut 4 / 5 / 6 Go selon le PC (avant 2 / 3 / 4 Go, saccades).

**Reste à mesurer ou à décider** : conteneurs de loot très lourds (~945 faces chacun, 9 000 à Valmont) ; Zombie Awareness
(portée des chemins, boucle d'IA) ; réglages Java de l'hébergeur ; MAtmos et TCTcore inutiles sur le serveur ; étoiles du
loot et suivi des quêtes renvoyés chaque seconde (réseau) ; texture d'effet des boss de 16×16384 (refusée par certaines
cartes graphiques).

## 4. Deuxième passe (25/10, après-midi) : systèmes du jeu, décisions appliquées, nouveautés

**Décisions des propriétaires, appliquées**
- Lits : plus aucun point de réapparition sur un lit ou une ancre ; le spawn est le point de tous (ceux déjà posés sont
  effacés à la connexion ; `/spawnpoint` du staff reste possible).
- Salles à carte : déconnecté dans la salle = remis devant la porte côté couloir à la reconnexion, porte fermée.
- Revente : tout objet vendu par un PNJ au prix P est racheté au plus 50 % de P, pour toujours (articles du Registre
  compris) ; le recycleur refuse ce qui rapporterait plus que P/2 en moyenne ; contrôle automatique
  `python outils/marchands.py --controle`.
- Clan dissous : l'armoire est retirée sans rien faire tomber.
- `/ping` permis sur la carte (pour `/ping canal`).

**Nouveautés**
- Petit losange vert au-dessus des membres de son clan (jamais à travers les murs, caché avec F1, jamais en ClashWar).
- Canaux de la roue de ping : `/ping canal <nom>`, `/ping canal`, `/ping canal clan` (16 joueurs au plus par canal).
- Clés de caisse virtuelles : comptes par joueur hors du monde (`heraliz_crates/cles/`), onglet « Mes clés » dans
  `/boutique`, conversion sûre des clés physiques, aucune clé échangeable (aperçus à valider :
  `scratchpad/cles/apercu/`). Les commandes du site et d'AzLink (`heraliz givekey`) ne changent pas.
- Emotes : le délai de 10 s sans arme existait ; ses trous sont bouchés (grenades et mêlée comptées, délai gardé à la
  déconnexion, tir TaCZ refusé par le serveur et chez le joueur). La caméra d'emote permet encore de voir par-dessus un mur
  de 2 à 3 blocs : caméra rapprochée recommandée (à décider).

**Bugs corrigés dans cette passe (principaux)**
- TaCZ : un joueur qui court ou saute n'était même pas testé par la balle dès ~100 ms de ping du tireur (« balles qui ne
  touchent pas ») ; corps mal placé pendant une chute ; son lointain des silencieux entendu plus loin qu'un tir normal ;
  journal des tirs refusés par le serveur (« balles qui disparaissent ») pour enquêter.
- Bases : un fichier des clans abîmé livrait toutes les bases au pillage ; le droit « Ouvrir les coffres » n'était vérifié
  nulle part ; matériel de 1re base copié ou perdu après un plantage ; C4 hors Purge sans message ; sauvegarde des bases en
  tâche de fond (~140 ms en moins par sauvegarde).
- Quêtes : Varga bloquait toute la suite si des points de quête manquaient ; récompense perdue si l'économie ne répondait
  pas ; étapes « extraction » et « survivre » validées hors de la carte ; « Fouille N conteneurs » comptée plusieurs fois.
- Clans : exclu / dissous hors ligne jamais prévenu ; écran K figé après 30 s ; tag de la liste Tab pas mis à jour.
- Grades : un op 4 sans grade du staff n'avait pas accès à l'éditeur des grades.
- Réseau : suivi de quête et étoiles du loot renvoyés en boucle (envoyés seulement s'ils changent / groupés).
- Failles du game design : zone « test » capturable au centre de la carte retirée ; boîte à figurine des véhicules
  limitée aux véhicules.
- Fabrication : un jar pouvait sortir sans table des mixins (refmap) après une compilation partielle ; la fabrication
  recompile maintenant toute seule quand une table manque.

## 5. Banc de charge (mesuré sur ce PC, vraie carte v3, bots du module `banc`)

Conditions : Forge 47.3.12 seul (sans Mohist ni plugins), carte v3 (64 régions, ~28 000 conteneurs ; 2 209 tronçons
chargés en permanence par d'anciens `forceload`), bots en combat intense (tirs, grenades, frappes, largages, constructions,
fouilles, soins), sans réseau : la charge porte sur le serveur, pas sur la bande passante. Mémoire 4 Go. Ce PC (AMD Ryzen 5
7535HS) est environ 1,7 à 2 fois plus rapide par cœur que le Xeon E5-2690 v2 (2013) de l'hébergeur : sur la prod, compter
à peu près le double de ces temps. Comparer les tendances, pas les valeurs absolues.

| Charge | Moyenne | Médiane | 95 % | 99 % | Pire | Ticks > 50 ms |
|---|---|---|---|---|---|---|
| à vide (spark) | ~1,5 ms | 1,1 | 2,5 | — | 12 | — |
| 2 bots | 11,1 | 10,0 | 17,2 | 33,2 | 316 | 0,4 % |
| 5 bots | 11,6 | 10,5 | 18,5 | 34,4 | 190 | 0,4 % |
| 10 bots | 18,5 | 17,8 | 25,8 | 42,6 | 302 | 0,7 % |
| 20 bots (+ monstres de Minecraft) | 24,4 | 22,8 | 37,2 | 73,4 | 360 | 2,4 % |
| 20 bots (sans monstres de Minecraft, sauvegarde chaque minute) | 10,8 | 8,4 | 21,5 | 53,2 | 325 | 1,3 % |
| 30 bots (+ monstres de Minecraft) | 32,9 | 29,4 | 57,3 | 107,6 | 531 | 7,4 % |
| exploration, 12 bots sur toute la carte (spark) | — | 9,9 | 14–18 | — | 37 | — |

Objectifs du projet : moyenne ≤ 30 ms, 95 % ≤ 40 ms, 99 % < 50 ms.
- Les monstres de Minecraft (squelettes, creepers) coûtent cher : garder **`doMobSpawning` à `false`** sur la carte (le
  peuplement des zombies est fait par HeraliZ Utils, sans cette règle).
- Ramasse-miettes (4 Go, réglages proches d'Aikar) : 82 pauses jeunes, 38 ms en moyenne, 104 ms au pire.
- Pics restants : arrivée de 20 à 30 bots dans le même tick (artefact du test : de vrais joueurs arrivent un par un,
  ~40 ms chacun), sauvegardes avec beaucoup de joueurs (265–325 ms : écriture des données de chaque joueur), calcul des
  faces d'une base que les bots construisent d'un coup (204 ms).
- Corrigé grâce au banc : pose des arènes ClashWar (pire tick 336 → 49 ms), table des identifiants de Forge refaite à
  chaque sauvegarde (190–300 ms, maintenant gardée : 2e sauvegarde à vide sans blocage), statistiques de Minecraft
  écrites à chaque sauvegarde (plus écrites), frappes aériennes qui faisaient calculer un chemin à tous les monstres du
  secteur (330 ms, 12 au plus), procédures de sons de MAtmos exécutées sur le serveur (110 ms, coupées côté serveur).
- Pas mesuré : charge réseau réelle, Mohist et ses plugins, vrais joueurs, FPS (voir §6).

## 6. FPS des joueurs
Non mesurés : aucun vrai jeu n'a été lancé. Le profil CurseForge « HeraliZ Decors » contient les jars joueurs finaux et
les gunpacks (anciens mods dans `mods_avant_25oct`). Pour mesurer : F3 (images par seconde, mémoire), `/diag client` du
projet, spark côté joueur, à Valmont puis en campagne, avant et après les corrections (herbe, grande texture). Corrigé sans
mesure : herbe −67 % de faces, textures d'armure inutiles retirées de la grande texture, mémoire par défaut du launcher.

## 7. Décisions pour les propriétaires
1. Caméra des emotes : la rapprocher (≈ 2 blocs, angle limité) pour ne plus voir par-dessus les murs ?
2. Tireur au-delà de la distance de vue (≈ 112 blocs) : un fusil de précision tue un joueur qui ne le voit pas. Monter la
   distance de vue, ou pas de dégâts au-delà ?
3. Quêtes de clan « Jouer N minutes » qui avancent AFK, boss à 150 points toutes les heures sans plafond (game design).
4. `/wipe` et clés virtuelles : clés de jeu effacées, clés cosmétiques gardées (réglable).
5. Anciens clans de 5 membres : gardent leurs membres mais ne peuvent plus inviter.

## 8. Verdict
Voir le compte rendu de fin de session.
