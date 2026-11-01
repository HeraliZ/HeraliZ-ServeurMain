# Essais du 01/11 — chantier « banc » (banc de charge sur une machine de GitHub)

Demande du propriétaire : mesurer le serveur avec beaucoup de joueurs **sans toucher au Ptero**.
Fait : workflow « Banc de charge » (`.github/workflows/banc.yml`), outil `outils/banc_charge.py`, module `banc`
(`/bots mesure`, `/bots commandes`, bots qui vivent dans le monde où on les envoie). Mode d'emploi : `docs/GUIDE.md` §12.

## Banc réaliste (module `banc` 1.2.0, 01/11 au soir)

Premier banc (100 bots, 5 min) : 80 ms par tick, 11,9 TPS. Mais le banc faussait sa propre mesure :

| Problème du premier banc | Cause trouvée | Maintenant |
|---|---|---|
| Java lancé avec 11 Go, le serveur ne voyait que 3 996 Mo ; une `OutOfMemoryError`, 954 passages du ramasse-miettes (19 s sur 300) | Au premier démarrage (bibliothèques pas encore en cache), Mohist installe Forge puis **se relance lui-même** dans une autre machine Java par `java -jar mohist.jar` (lu dans son lanceur : `v_1_20_1$Install.install` → `restartServer`), **sans aucun réglage** : tas = 1/4 de la mémoire de la machine (16 Go → 3 996 Mo), pas de réglages Aikar, pas de journal du ramasse-miettes, pas de mode essai d'AuthMe. La première machine Java gardait ses 11 Go pendant ce temps. Le Ptero n'est pas touché (installation déjà faite : une seule machine Java). | Installation faite d'abord à part (petit tas), puis la mesure dans UNE machine Java lancée par le banc ; réglages passés aussi par `JDK_JAVA_OPTIONS` (repris par toute machine Java lancée par `java`). Le rapport écrit les **arguments réels** de la machine Java du serveur, sa **mémoire max**, et dit si elle n'est pas celle lancée par le banc. |
| Distances 8 / 5 | Le `server.properties` du dépôt n'est envoyé au Ptero qu'en installation complète | Vue **6**, simulation **4** par défaut, comme le Ptero relevé le 01/11 (réglables) ; le rapport compare au Ptero (et spigot : activation 24 / 5 / 16, mob-spawn-range 4) |
| 345 morts en 5 min, chacune = déconnexion puis connexion complète 10 s après (`Bots.creer` : 11 % du profil) | Le banc retirait le bot mort et en recréait un | Écran de mort 4 à 12 s, puis le **même clic « Réapparaître »** qu'un vrai joueur (`PlayerList.respawn` et nos règles), point de réveil du spawn (« Au camp »), 15 à 45 s au camp, puis la **vraie borne de départ** |
| Morts, connexions, téléportations sans rapport avec un vrai serveur | Combat permanent de tous contre tous | Rythmes pour 150 joueurs (réglables) : **5 morts, 2 connexions, 4 voyages (/home, /tpa) par minute**, chaque zone en combat **25 %** du temps ; morts tenues par un facteur sur les dégâts entre bots (tirs et balles inchangés). Écrits dans le rapport, par minute et pour 150 joueurs. Estimations à recaler sur les journaux du Ptero. |
| Blocages de 7,4 s à l'arrivée des bots (`Bots.partir` : 8 % du profil) | `getChunk` sur le fil du serveur (charge ou crée le tronçon d'un coup) | Tronçon d'arrivée demandé à Minecraft (ticket, tâche de fond), départ quand il est prêt ; préparation des bots toujours avant la mesure |
| 445 tronçons créés pendant la mesure | Bots au bord du terrain déjà créé | Terrain de toutes les zones créé d'avance, avant les bots (768 tronçons à la fois, sans les faire tourner) |
| Coût du banc mêlé au reste | — | Coût du banc mesuré à part (« ms par tick hors banc »), tableau par mod du profil, ce que ferait la connexion de vrais joueurs (tick de connexion, paquets de position, gestes, réapparitions, téléportations, connexions) |

Aussi : « paquets de position » comme un vrai jeu (`ChunkMap.move` à chaque tick où le bot bouge ou tourne la tête, au moins
une fois par seconde ; avant : toutes les 10 ticks). C'est ce que fait le serveur pour chaque vrai joueur : **ce coût réel
monte dans la mesure** (il dépend du nombre d'entités, balles comprises).

Nouveau dans le rapport : tronçons chargés par monde et **ce qui les tient chargés** (tickets par sorte : `start` = zone
de départ gardée par Minecraft/Mohist même vide, `player` = vue des joueurs, nos tickets `heraliz_*`) ; c'est ce qui dira
pourquoi `crac:tuto` et `heralisz:spawn` en avaient autant.

**Rien de ceci n'a été compilé avec Gradle ni essayé dans un vrai Mohist** (ici : pas d'accès à Forge ni à Mojang). Vérifié
ici : syntaxe Java du module (javac, sans les bibliothèques de Minecraft), Python (pyflakes), workflow (YAML), et tout le
déroulé de `lancer` contre un faux Mohist qui se relance comme le vrai (installation à part, `JDK_JAVA_OPTIONS`, une seule
machine Java, cas où l'installation ne se finit pas, terrain d'avance, A/B, pics, rapport). Le lanceur de Mohist a été lu
dans le jar de la Release « base-serveur » (empreinte contrôlée).

### Lancer

- Normal : Actions → « Banc de charge » → « Run workflow » (réglages par défaut : 100 bots, distances 6/4, rythmes
  5/2/4/25, scénario normal).
- **Essai A/B** (exemple `randomTickSpeed 0`) : champ « regles » = `randomTickSpeed 0` (plusieurs : `randomTickSpeed 0;
  doFireTick false`), case « ab » cochée : une mesure SANS puis une mesure AVEC, dans le même serveur ; tableau « Essai
  A/B » en tête du résumé. Case « ab » décochée : les règles sont posées avant la mesure (à comparer avec un autre banc
  sans règles). Les règles sont changées dans **tous** les mondes (`/bots gamerule`).
- Scénario `connexions` : une connexion toutes les 10 s environ, coût de chacune (tableau « Pics »).
- Scénario `teleportations` : pics répétés : 6 bots d'un groupe extraits ensemble, 10 `/spawn` de partout, un groupe de 6
  qui part de la borne ; pire tick de chaque cas.
- Distances (`6/4`) et rythmes (`morts/connexions/voyages/combat`, ou `off` = comme avant) sont des champs du formulaire.

### À vérifier au premier banc réaliste (résumé de la tâche)

1. **Fabrication** : `banc` 1.2.0 compile (job « fabriquer »). Sinon, me donner l'erreur.
2. **Machine Java** : section « Machine Java du serveur » : « lancé par le banc (une seule machine Java) », mémoire max
   proche de la demande (environ 11 000 Mo sur la machine de GitHub). Au tout premier banc après un changement de Mohist :
   étape « installation de Mohist faite à part ». Plus d'`OutOfMemoryError`.
3. **Réglages** : vue 6, simulation 4 dans le tableau « Réglages ».
4. **Spawn du banc** : ligne `[banc] CAMP … point de réveil praticable : true` dans le journal. Si `false`, les bots vont
   quand même au camp (sur la dalle), mais pas par le vrai chemin.
5. **Terrain d'avance** : « terrain des zones créé d'avance : N tronçons en … s » (sans « PAS fini ») ; « créés » proche
   de 0 pendant la mesure.
6. **Morts et réapparitions** : tableau « Rythmes » : morts proches de 5 par minute pour 150 joueurs, réapparitions ≈
   morts, départs de la borne ≈ réapparitions (sinon « retours sans la borne » : la borne n'a pas marché, me le dire).
7. **Coût du banc** : « ms par tick hors banc » écrit ; « banc lui-même » petit devant le reste.
8. **Tronçons** : tableau « ce qui les tient » rempli (sinon « tickets illisibles » : me le dire).
9. Erreurs Java nouvelles avec `fr.heraliz.banc` dans la pile : me les donner.

### Ce qui change pour la lecture des chiffres

Les chiffres de ce banc ne se comparent pas à ceux du premier : vraie mémoire, distances du Ptero, moins de morts et de
connexions, moins de tirs (combat 25 % du temps), terrain déjà créé, mais paquets de position à chaque tick (coût réel).

## Premier banc (module 1.1.0)

1. Pousser la branche (le propriétaire) : le contrôle de branche fabrique `banc` ; si `banc.yml` est dans l'envoi, le
   banc part tout seul avec les réglages par défaut.
2. Après le passage sur `main` : onglet Actions → « Banc de charge » → « Run workflow », choisir la branche et les réglages.
3. Durée attendue : 25 à 40 min (le premier banc un peu plus : Mohist télécharge ses bibliothèques, gardées ensuite).

## En jeu (serveur d'essai sur un PC, `python outils/serveur_local.py <dossier> --banc`)

- `/bots lancer 10`, puis `/bots mesure 60` : une minute plus tard, message « Scénario terminé : mesure de 60 s, 10 bots … —
  rapport diag/banc-… » et le fichier dans `diag/`. `/bots mesure stop` arrête avant (rapport quand même).
- `/bots commandes 30` : les bots tapent des commandes ; `/bots commandes 0` arrête.
- `/tuto lancer HzBot001` : le bot part au tutoriel et y bouge autour de son point d'arrivée.
- Nouveau (1.2.0) : tuer un bot (`/kill HzBot002`) : il reste mort quelques secondes, réapparaît (même nom, sans
  reconnexion dans le journal), puis repart. Sans `/bots camp`, il rentre directement dans sa zone.
- `/bots gamerule randomTickSpeed 0` : réponse avec la valeur lue dans chaque monde.
- `/bots troncons` : ligne `[banc] TRONCONS {…}` dans la console.

## Questions pour le propriétaire

- Les rythmes (5 morts, 2 connexions, 4 voyages par minute pour 150 joueurs, combat 25 % du temps) sont des estimations :
  si les journaux du Ptero donnent les vrais chiffres (morts et connexions par heure aux heures pleines), je les recale.
- `max-players=50` sur le Ptero : à monter avant d'accueillir 150 joueurs (le banc garde 250 pour ses bots).
