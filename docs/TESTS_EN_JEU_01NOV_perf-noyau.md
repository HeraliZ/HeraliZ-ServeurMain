# Essais en jeu du 01/11 — chantier « perf-noyau »

Demande du propriétaire : « le serveur le plus léger possible avec 150 joueurs, en gardant tout ». Périmètre : noyau (sauf
`diag/`), armes, équipement, sacs, boutique, emotes, voix, interface (côté serveur seulement) et Spartan.

**Rien de ceci n'a été essayé en jeu, ni compilé avec Gradle** (le dépôt de Forge est bloqué ici) : seule la syntaxe Java
a été vérifiée (Java 17), plus deux petits essais hors du jeu (liste suivie de l'anti-duplication : 209 cas identiques ;
classement du casino : 45 000 cas identiques ; contrôle d'intégrité de Spartan 2.9.6 : voir plus bas). La fabrication
complète se fait par le contrôle de branche de GitHub.

Versions : Noyau 1.0.9, Spartan 2.9.6, Tactique 2.41.1, Boutique 1.3.4, Sacs 3.0.1-beta.4-heralisz22. Changements côté
serveur seulement, mais les numéros de version changent : les joueurs reçoivent les nouveaux jars par le launcher, comme à
chaque mise en ligne (Spartan : jar joueur et jar serveur de la même fabrication, comme toujours).

## Ce qui a changé (coûts estimés à 150 joueurs, 20 ticks par seconde)

| Endroit | Avant | Après | Pour le joueur |
|---|---|---|---|
| `noyau/.../mixin/MatmosServeurMixin.java:31` (MAtmos, mod d'un autre auteur, jar intact) | 2 procédures d'ambiance tournaient sur le serveur à chaque tick de chaque joueur : ~20 lectures du biome et des blocs, ~40 lectures/écritures de ses données ; **~1,5 à 4 ms par tick** | 0 | Il n'entend plus en double les sons de grotte, d'orage, de Nether et de vent que le serveur renvoyait (volume 3 : jusqu'à 48 blocs, ceux des autres joueurs compris). Son ambiance à lui, jouée par son jeu, ne change pas |
| `anticheat/.../FlightGear.java:52-65` + `SpartanConfig.java:741` | à chaque paquet de mouvement (~3 000/s) : 2 listes neuves du réglage, 12 identifiants recopiés en texte, 2 lectures des cases Curios par réflexion ; **~0,3 à 0,8 ms par tick** | jetpacks : rien (aucun n'existe sur le serveur) ; parachute : 1 lecture des cases ; listes faites une fois | rien |
| `anticheat/.../CheckManager.java:100-160` | ~10 objets créés par joueur, par tick et par paquet (~60 000/s) | 0 | rien |
| `anticheat/.../VisibilityEngine.java:286` (anti-ESP) | 22 350 paires par tick dans un monde à 150 joueurs : 2 recherches par paire + jusqu'à 4 lectures de réglage ; **~0,3 à 1 ms par tick** si tout le monde est au même endroit | 150 recherches, 4 lectures par tick | rien (mêmes paires, mêmes décisions) |
| `anticheat/.../AntiDupe.java:77` (relevé toutes les 20 s) | un tick de **plusieurs dizaines de ms** : ~6 000 objets des sacs recréés (avec l'événement des capacités de Forge), ~15 000 textes | seuls les objets suivis (armes, sacs, armures, objets de valeur) sont recréés ; plus de texte pour une case vide | rien (mêmes numéros, mêmes alertes au staff) |
| `anticheat/.../ScanManager.java:190` | date de `officiel.json` demandée au disque par le fil du serveur toutes les 10 s (un disque occupé = tick gelé, cf. 117 ms le 28/10) | relevée sur un fil à part | rien (relu tout seul ≤ 12 s après une mise en ligne au lieu de ≤ 10 s) |
| `anticheat/.../MovementEngine.java:264` | 1 objet par joueur et par tick pour rien | 0 | rien |
| `anticheat/.../ScanManager.java` (contrôle d'intégrité, Spartan 2.9.6) | chaque rapport décompressé, décodé et analysé sur le fil du serveur : **~2 à 3 ms par rapport, environ un par seconde** à 150 joueurs ; bien plus pour un suspect (preuve complète écrite en JSON) | sur le fil « Spartan-controle » ; seul l'effet (alertes, staff, refus, validation) reste sur le fil du serveur | rien (mêmes verdicts, mêmes alertes, même ordre ; appliqué une fraction de seconde après la réception au lieu de tout de suite) |
| `armes/.../CustomExplosion.java:116` (grenades) | 1 352 rayons de destruction par explosion (~19 000 lectures de blocs, **~1 à 2 ms par grenade**) pour une grenade qui ne casse aucun bloc (toutes celles du serveur) | 0 | rien (dégâts, souffle, secousse, particules identiques) |
| `boutique/.../VanishServeur.java:245` (toutes les 2 s) | 22 500 essais + contrôle AuthMe et grade de chaque joueur | les joueurs cachés cherchés une fois ; sans staff caché, plus rien | rien |
| `boutique/.../TagClan.java:68` (tab, toutes les 2 s) | 150 expressions recompilées | 0 | rien (même tag) |
| `boutique/.../TpaServeur.java:184` | `/tpa` : 2 demandes de date au disque sur le fil du serveur | 0 (date relevée à part) | rien (villes relues ≤ 5 s après un changement de `zones.json`) |
| `boutique/.../CasinoStats.java:159` (tableaux du casino, toutes les 2 s par tableau) | copie et tri de **tous** les joueurs passés au casino (jamais oubliés) | parcours en gardant les 8 premiers | rien (même classement, égalités comprises) |
| `sacs/.../UnlockManagerServeur.java:57` (défis des sacs) | 99 défis testés avec une recherche chacun, pour chaque joueur qui marche, à chaque tick ; biome de chaque joueur lu chaque seconde pour rien | recherche seulement pour un défi fini ; plus de biome | rien (mêmes déblocages) |

Ordre de grandeur du gain total : **~2,5 à 6 ms par tick à 150 joueurs** (surtout MAtmos), plus des pics en moins (relevé
de l'anti-duplication, contrôles d'intégrité, grenades, coups d'œil au disque).

### Spartan 2.9.6 : contrôle d'intégrité analysé à part (demande du 01/11)

- Sur le fil « Spartan-controle » : décompression et décodage du rapport, analyse, écarts au jeu officiel, et le fichier
  de preuve en JSON quand il y a un indice. Sur le fil du serveur, comme avant : alertes (Discord, staff, `alerts.log`),
  demandes du staff, refus de connexion du mode strict, « Jeu validé », `/spartan report`.
- Mêmes résultats : l'analyse lit exactement ce qu'elle lisait (client de référence, modpack officiel, inventaire du
  premier contrôle, réglages), pris sur le fil du serveur. Ces profils ne sont plus modifiés une fois publiés :
  l'apprentissage (`/spartan reference learn`) et le nouveau modpack officiel travaillent sur des copies.
- Même ordre : un seul rapport analysé à la fois, dans l'ordre d'arrivée. Une déconnexion, une demande du staff
  (`/spartan scan`), `/spartan report` et la liste des suspects appliquent d'abord les rapports encore en analyse ; la
  validation du jeu attend un rapport en cours (une fraction de seconde). Si le fil d'analyse ne répondait plus (jamais vu),
  le fil du serveur refait l'analyse lui-même après 10 s.
- Essai hors du jeu : l'ancien et le nouveau code ont fait la même suite de contrôles (joueur propre, tricheur avec refus,
  rapport illisible, apprentissage remplacé puis complété, config Mixin apparue en cours de partie, déconnexion juste après
  la réception, nouveau modpack avec l'ancien accepté) : mêmes alertes, mêmes preuves, mêmes refus, mêmes validations, mêmes
  messages au staff, même référence ; aucune action faite hors du fil du serveur. Seule différence : quand une NOUVELLE
  demande part pour un autre joueur pendant l'analyse d'un rapport, elle peut partir quelques millisecondes avant
  l'alerte du rapport précédent (deux choses indépendantes).

## Regardé, rien à changer

- **Voix (CaraCrea)** : le routage par proximité de chaque paquet de voix est O(n) par paquet et se fait sur le fil de la
  voix (UDP), jamais sur le fil du serveur. Le fil du serveur ne fait qu'une copie des positions par tick (O(n), ~30 à
  50 µs à 150 joueurs). Avec 20 joueurs qui parlent : ~150 000 tests de distance par seconde sur le fil de la voix
  (< 1 ms par seconde). Pas d'O(n²) sur le fil du serveur.
- **Spartan, mouvement** : chaque paquet de position fait ~6 recherches d'entités et ~200 lectures de blocs (collisions,
  échelles, toiles, pistons…). C'est le cœur des détections : rien de retiré. Les résultats sont déjà gardés par paquet.
- **Équipement, emotes, interface, armes (mêlée, soins, couteaux, vitres, camos, allongé)** : travail léger, souvent
  étalé (toutes les 10 ou 20 ticks), ou seulement sur action du joueur. L'interface ne fait rien côté serveur.

## Repéré, pas fait (à décider)

1. ~~Spartan, contrôle d'intégrité sur un fil à part~~ : fait (Spartan 2.9.6, voir plus haut).
2. **Spartan, « transactions »** (`[checks] transactions = true`) : un petit paquet « ping » par joueur et par tick
   (3 000 par seconde, ~0,3 à 0,7 ms par tick sur le fil du serveur). Le couper allégerait le serveur mais **affaiblirait**
   l'anti-recul, le Timer et le suivi des états du joueur : **déconseillé**, pas changé.
3. **Hôtel des ventes** : chaque achat, vente ou fin de vente réécrit tout `hdv.json` sur le fil du serveur (plusieurs ms
   quand l'hôtel est grand). L'écriture attend exprès le disque (argent et objets) : à revoir avec une écriture par
   morceaux ou un fil qui attend, pas sans réfléchir.
4. **Emotes** : chaque déblocage réécrit tout `debloquees.json` sur le fil du serveur (fichier qui grossit toute la
   saison). Rare (caisses) ; même remarque que l'hôtel des ventes.
5. **Grades, toutes les 2 s** : tab, tags de clan, couleur du chat et état AuthMe de chaque joueur refaits dans le même
   tick (~1 ms à 150 joueurs, une fois toutes les 2 s). Peut être étalé sur 40 ticks si une mesure le montre.
6. **Balles** (audit A17) : la recherche élargie des cibles des balles (pour les joueurs à fort ping) vaut aussi pour les
   zombies. La réserver aux joueurs changerait un peu les touches sur un zombie qui court (TaCZ le recule de 5 ticks) :
   gain faible, pas changé.
7. Réglages du serveur : rien à proposer pour ce périmètre (`entity-tracking-range.players: 300` de `spigot.yml` est déjà
   plafonné par la distance de vue 8).

## À vérifier en jeu (serveur redémarré avec les nouveaux jars)

1. **Ambiance MAtmos** : dans une grotte, sous un orage, au Nether : on entend toujours ses propres sons d'ambiance (vent,
   oiseaux, grotte…). À plusieurs, on n'entend plus en double ceux des autres joueurs.
2. **Spartan, vol et chute** : un parachute (`ywzj_vehicle:parachute_pack`) porté ralentit la chute sans alerte « Fly » ;
   sauter, courir, nager, échelles : aucune alerte de plus qu'avant (`/spartan status <joueur>`).
3. **Spartan, anti-ESP** : `/spartan esp` donne les mêmes chiffres qu'avant (paires suivies, cachées) ; un joueur caché
   derrière un mur n'apparaît pas, il apparaît dès qu'il est en vue ; coéquipiers toujours visibles.
4. **Spartan, anti-duplication** : avec un compte de staff, mettre la même arme numérotée à deux endroits (ex. copie
   créative donnée à un autre joueur en survie, dans son sac) : l'alerte « Duplication » arrive dans les 20 s avec les deux
   endroits (« sac (inventaire N) M »). Pas d'alerte sur un jeu normal.
5. **Spartan, modpack officiel** : après une mise en ligne sans redémarrage, le journal dit « Modpack officiel mis à jour
   sans redémarrage » (au plus ~12 s après le changement du fichier).
6. **Grenades** : une M67 blesse et repousse comme avant, ne casse aucun bloc, secoue l'écran ; fumigène, flash et molotov
   inchangés ; le C4 sur une base fait les mêmes dégâts qu'avant.
7. **Vanish** : `/staff vanish` cache le staff aux joueurs (tab et monde) et le remontre en le retirant ; un staff voit
   toujours les autres staffs cachés.
8. **Tab** : tags de clan `[TAG]` identiques dans le tab.
9. **`/tpa`** : refusé dans une ville (`config/heralisz_clans/zones.json`), accepté ailleurs.
10. **Casino** : les tableaux des meilleurs joueurs (gagnants, perdants, parieurs) montrent le même classement.
11. **Sacs** : un défi de sac (ex. marcher 7 000 blocs) se débloque toujours à la fin ; la personnalisation montre la
    progression.
12. **Spartan 2.9.6, contrôle d'intégrité** (jeu officiel, jeu avec un jar en plus, compte de staff) :
    - à la connexion d'un jeu officiel : « ✅ Jeu validé » dans le fil Discord du joueur, comme avant ;
    - jeu avec un jar en plus (mode strict) : connexion refusée en quelques secondes, alerte et preuve sur Discord ;
    - `/spartan scan <joueur>` : le résultat arrive au staff ; `/spartan report <joueur>` montre le dernier contrôle ;
    - `/spartan reference learn <pseudo>` depuis un jeu propre : « Référence apprise », puis les contrôles suivants des
      autres joueurs propres ne sont plus orange ;
    - se déconnecter puis se reconnecter (jeu officiel) : un « Jeu validé » par connexion, jamais « Validation jamais
      reçue » ;
    - dans le journal du serveur : aucune erreur « [Spartan] Contrôle d'intégrité de … » ni « Analyse du contrôle … sans
      réponse ».
