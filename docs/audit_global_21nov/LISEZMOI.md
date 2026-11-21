# Audit global du 21/11 : synthèse

Demandé par le propriétaire le 21/11 : relire tout le serveur (tous les mods, tout le code), les dépendances entre mods,
les lenteurs possibles et le code fragile. Dix relectures en parallèle, une par partie ; chacune a corrigé les vrais
défauts de sa partie et noté ce qui demande une décision. **Rien n'a été testé en jeu** : tout a été fabriqué et contrôlé
sur GitHub (« Contrôle de branche »), les essais en jeu sont dans `docs/TESTS_EN_JEU_17NOV_carte-ile.md`, section 49.

## En bref

| Partie | Compte rendu | Corrigé | À décider | Versions |
|---|---|---|---|---|
| Boutique (grades, casino, caisses) | `a1_boutique.md` | 7 (2 importants) | 6 | Boutique 1.7.2 |
| Monde (zombies, carte, hélicoptère, frappes, train…) | `a2_monde.md` | 6 | 8 | Monde 1.3.10 |
| Bases (CRac) | `a3_bases.md` | 9 (1 important) | 7 | CRac 1.38.9-beta |
| Armes, équipement, sacs | `a4_armes_equipement_sacs.md` | 5 (+1, voir plus bas) | 7 | Armes 2.59.9, Sacs heralisz28 |
| Clans, PNJ, événements, récolte | `a5_clans_pnj_evenements.md` | 6 | 6 | Clans 3.19.2, Quêtes 1.24.4, Événements 1.3.3 |
| Loot et bunker | `a6_loot_bunker.md` | 6 | 4 | Loot 0.20.8, Bunker 0.14.8 |
| Réanimation et ClashWar | `a7_survie_clashwar.md` | 10 (1 grave) | 6 | ClashWar 1.3.7, Réanimation 1.16.14 |
| Anticheat, voix, launcher | `a8_anticheat_voix_launcher.md` | 3 (+1, voir plus bas) | 9 | Spartan 2.15.5 (launcher inchangé) |
| Noyau, interface, emotes, site, GitHub, outils | `a9_noyau_interface_site_outils.md` | 5 | 4 (+1 réglage à faire) | Noyau 1.1.11 (site inchangé) |
| Liens entre les mods | `a10_dependances.md` | relecture seule : 1 important et 4 petits, appliqués ensuite (`f324509e`) sauf un | 2 | Équipement 1.4.2 |

Aucun canal réseau n'a changé : un jeu de joueur d'avant se connecte toujours (les jars des joueurs suivent comme
d'habitude à la mise en ligne). Ni le launcher ni le site n'ont besoin d'être republiés.

## Les corrections les plus importantes

1. **ClashWar, duplication (grave)** : pendant les 1 à 2 ticks où le dépôt d'un joueur s'écrit, un jeu modifié pouvait
   ouvrir un écran (coffre du clan, sac, Curios) et échanger une arme équipée contre la même arme nue. Tout écran ouvert à
   ce moment est refermé. Aussi : objets à la souris rendus à la déconnexion, Coffre jamais rendu à un mort ni à un joueur
   à terre, spectateur qui ne peut plus aller voir les bases des autres (à vérifier en jeu sur Mohist).
2. **Casino (important, sécurité)** : un opérateur de niveau 2 avec un jeu modifié pouvait faire jouer n'importe quelle
   commande à la console par la caisse. Le serveur garde maintenant ses propres « encaissements ».
3. **Tutoriel des bases à deux du même clan (important)** : le premier joueur restait bloqué (caisse en métal refusée)
   et sa cabane n'était pas effacée. Les armoires sont maintenant gardées une par une.
4. **Réponses de commandes invisibles (important)** : `/boutique abonnement`, `/avantages nick qui`, `/grade voir`,
   `/grade liste` ne répondaient rien aux joueurs qui ne sont pas op.
5. **Argent** : `/bourse donner` (jusqu'à 1 000 000 hérals) demande maintenant l'op 4 connecté, comme `/eco donner` ; si le
   fichier des comptes est illisible au démarrage, l'économie se dit « indisponible » au lieu de payer dans le vide.
6. **Petits gels du serveur** : une dizaine d'endroits (hélicoptère, frappes, ping, train, lampes, vitres qui repoussent,
   portes des salles à carte, coffres T7, explosions des bases…) lisaient un morceau de carte encore en lecture et
   faisaient attendre tout le serveur. Ils attendent maintenant que le morceau soit complet (`Troncons` du noyau).
7. **Jeux modifiés qui spamment** : limites ajoutées à la fouille arme en main (1 par tick), aux marchands (4 par
   seconde), aux exécutions (1 toutes les 400 ms) ; `/heralizloot reinitialiser 128` ne gèle plus le serveur.
8. **Anticheat** : le disjoncteur des règles en rodage reconnaît un même PC seulement par une adresse publique (derrière
   la passerelle du Ptero, tous les joueurs avaient la même) ; alertes en jeu réservées au staff vérifié (launcher +
   `/login`) ; et, corrigé à la suite : après une remise en service, les PC qui avaient coupé la règle ne restent plus
   expulsés à chaque connexion quand la fausse alerte touche aussi d'autres PC (`62fbb244`).
9. **GitHub** : le secret des noms interdits n'est plus donné à Gradle (qui fait tourner du code d'autres auteurs) ; le
   numéro de version d'un mod de Modrinth ne peut plus injecter de variable dans la tâche qui écrit dans le dépôt.
10. **Panneau du staff** : ban PC, jour/nuit, largage, frappe, redémarrage… marchent aussi pour un membre du staff sans
    op en survie sur la carte ou en combat. Les commandes tapées gardent les mêmes règles.

Corrigé aussi par le coordinateur, d'après les relectures : un objet à vie n'entre plus dans la caisse de largage posée
en bloc côté serveur (le nom de classe ne correspondait jamais, `bb4da7c1`) ; le disjoncteur ci-dessus (`62fbb244`).

## À faire par le propriétaire

- **Créer le secret `NOMS_INTERDITS`** sur GitHub (Settings → Secrets and variables → Actions, un mot par ligne : les
  prénoms et pseudos qui ne doivent jamais apparaître dans le code ; `docs/MISE_EN_LIGNE_GITHUB.md` §7). Il manque
  aujourd'hui : le contrôle de la règle n° 3 est sauté à chaque envoi.
- **Alerte de sécurité GitHub (Dependabot n° 22, gravité moyenne)** sur la branche principale : à regarder dans l'onglet
  Security du dépôt (une bibliothèque d'un outil a une faille connue ; aucun détail lisible d'ici).
- **Sur la carte** : vérifier qu'aucune porte HBM « transition seal » n'est posée (elle relirait 600 blocs à chaque
  tick) ; dans `ops.json` du Ptero, qui est op 2 ou 3 (voir la question de la tablette du casino).
- **Un soir chargé** : `/diag profil` pour mesurer l'anticheat (anti-ESP), la rotation du loot et le placement des zombies
  en ville (rien de mesuré avec 150 vrais joueurs).

## À décider

Chaque compte rendu détaille ses propositions (risque, gain, fichiers). Regroupées :
- **Règles du jeu** (choix du propriétaire) : captures de territoire par le staff sans points de saison (a5 P1) ;
  `/trade` avec les mêmes refus que l'hôtel des ventes (a1 n° 2) ; tablette du casino réservée à l'op 4 (a1 n° 1) ;
  ClashWar, joueur déconnecté pendant les dépôts : retirer l'équipe ou non (a7 B) ; `/base effacer <chef>` limité à la
  cabane du joueur visé (a3 n° 1).
- **Sécurités en plus, sans changement pour les joueurs** : vérification plus stricte du Coffre du ClashWar (a7 A, C) ;
  voix coupée avant le `/login` (a8 P4) ; limites de cadence restantes (camos et stickers a4 n° 2, journal des quêtes et
  saison a5 P4, deux paquets des bases a3 n° 7) ; recycleur de la carte si son fichier ne s'écrit pas (a5 P2) ;
  clé du Gardien comme signe d'un même PC (a8 P2) ; tâches GitHub (a9 n° 1 à 4).
- **Lenteurs à alléger** : `profils.json` refait chaque minute sur le fil du serveur (a5 P3) ; collection des camos à vie
  écrite pendant le tick (a4 n° 1) ; `/kit` qui interroge le bot à chaque ouverture (a1 n° 4) ; `/avantages nick` qui
  relit `usercache.json` (a1 n° 5) ; date du fichier de l'usure (a2 n° 4) ; balles et horaire du train (a2 n° 6) ;
  défi d'intégrité calculé sur le fil de l'image chez le joueur (a8 P7, jar des joueurs).
- **Lettres nettes de l'interface** (a10 n° 1) : voir ci-dessous.
- **Contrôles de GitHub** : brancher `outils/verif_reflexion.py` et `outils/verif_mixins.py` (a10) sur chaque envoi
  d'une branche ; version minimale du noyau dans les armes (a4 n° 5).

## Liens entre les mods (a10)

Relecture de tous les liens entre nos mods et avec ceux des autres auteurs (`a10_dependances.md`) : 808 appels par
réflexion (613 exacts, 33 vers des mods facultatifs absents et protégés, le reste non vérifiable d'ici), 3 552 liens directs
entre modules (tous du bon côté), 36 configurations de mixins et 420 injections (209 cibles d'autres auteurs exactes, cibles
serveur comparées à Mohist 916), 44 canaux réseau (tous enregistrés dans le code commun, les 63 paquets venus des joueurs
ont tous une borne), 17 clés de données partagées (même nom, même type partout).

Appliqué ensuite (`f324509e`) : noyau déclaré des deux côtés (clans, extraction, armory, loot) ; dépendances qui
manquaient (Événements → noyau ; Grades → Économie et Clans, côté serveur) ; sous-mods du monde qui acceptent
heralisz_utils 2.26 et plus ; ModernFix « remove_spawn_chunks » coupé noir sur blanc ; démarrage d'essai « mixins
stricts » (case de « Run workflow ») qui fait une erreur de chaque injection de mixin sans cible, même celles qui ratent
sans rien dire (187 dans nos mods).

**Pas appliqué, décision du propriétaire : les lettres nettes de l'interface (03/11) n'ont jamais marché chez les
joueurs.** Leur configuration de mixins n'est pas dans le manifeste du jar (`modules/interface/build.gradle` l.56) :
le jeu ne la charge pas, sans rien dire. L'activer change l'écriture de tous les textes chez tous les joueurs, un rendu
que personne n'a jamais vu en vrai (le code se coupe tout seul en cas d'erreur, « Lettres nettes coupées »).

Démarrage « mixins stricts » essayé trois fois sur GitHub : il s'arrête chaque fois sur une injection de **ModernFix**
(autre auteur) qui ne trouve pas sa cible sur Mohist (« reduce_blockstate_cache_rebuilds », puis
« bugfix.forge_vehicle_packets »). Elles ratent aussi sur le serveur en ligne, sans rien dire et sans danger (ces deux
réglages de ModernFix ne font rien chez nous). Nos propres injections n'ont donc pas pu être passées en revue de cette
façon ; prochaine étape possible : une fabrication d'essai où seules nos configurations sont strictes. Le démarrage normal
de la dernière version (`f545589a`) est réussi, sans erreur nouvelle.

Outils ajoutés (branchés nulle part pour l'instant) : `outils/verif_reflexion.py` (réflexion et liens entre modules,
environ 20 s) et `outils/verif_mixins.py` (configurations, manifestes, cibles des autres auteurs, conflits).
