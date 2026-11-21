# Validation de l'audit du 21/11 — zone v6 : loot et bunker

Deuxième relecture, demandée par le propriétaire (« un audit bien profond et complet pour bien tout valider ») : un œil
neuf sur la même zone que le compte rendu `../a6_loot_bunker.md`, sans se fier à ses « vérifié sans problème ». Modules
relus : `modules/loot` (HeraliZ Loot : conteneurs, rotation par zones, salles à carte côté loot, coffres T7 et leurs
points, conteneurs des événements, butin des zombies, raretés, conteneurs scellés, présentoirs, nuisances, fouille avec un
objet en main, écrans de fouille, conteneurs allégés) et `modules/bunker` (HeraliZ Bunker et le code de HBM Modernized,
GPL : portes animées, cartes d'accès et fragments, lecteurs, barricades, salles à carte, portes de bunker).

**Rien n'a été testé en jeu** (pas de jeu ici) : relecture du code, et les contrôles qui marchent sans fabrication :
`outils/verif_reflexion.py` (0 erreur, 498 appels vérifiés), `outils/verif_mixins.py loot bunker` (0 erreur),
`outils/verifier_langues.py` (0 erreur, aucun avertissement pour le loot ni le bunker), `outils/raretes_objets.py
--verifier` (0 objet sans rareté), `modules/loot/outils/lod_props.py --controle` (conteneurs allégés à jour), cibles des
mixins lues avec `javap` dans les jars de Curios et de TaCZ. La fabrication et les contrôles se font sur GitHub
(« Contrôle de branche »).

En bref : les **3 commits du jour** de la zone font ce qu'ils disent ; l'un d'eux avait **un effet de bord** (porte d'une
salle à carte qui pouvait rester ouverte quelques secondes à la reconnexion), corrigé ; **2 autres défauts corrigés**
(fuite de mémoire chez les joueurs, petit gel rare des conteneurs de l'épave). Versions : **Loot 0.20.9, Bunker 0.14.9**
(le launcher les donne ; aucun canal réseau ne change : un jeu pas à jour entre encore). Aucune duplication trouvée.
« Contrôle de branche » sur GitHub avec ces corrections (commit `7fc16a9a3`) : **réussi** (loot et bunker fabriqués,
`verifier.py` : 0 problème).

---

## Changements du jour validés

Chaque commit du 21/11 qui touche la zone a été relu ligne à ligne (ce qu'il dit faire, cas limites, fils, lenteurs).

| Commit | Ce qu'il fait | Verdict |
|---|---|---|
| `cc10fec2c` | Loot 0.20.7 : texte de `/recharger loot` dans les trois langues | **OK**. La clé `heraliz_loot.cmd.tables` existe en français, anglais et russe avec un seul `%s`, comme l'appel (`Commandes.java` l. 44). |
| `5f5721dc3` | Fouille arme en main : une demande par tick et par joueur | **OK** (`FouilleServeur.java` l. 42, 53-55). Reçue sur le fil du serveur (`consumerMainThread`), table faible par joueur. Un jeu honnête envoie une demande par clic, ou une tous les 4 ticks touche tenue ; deux clics dans le même tick : le premier a déjà ouvert l'écran, le second aurait été refusé de toute façon. Personne de normal n'est bloqué. |
| `5f5721dc3` | `/heralizloot reinitialiser` : seulement les coffres T7 des tronçons déjà chargés | **OK** (`Commandes.java` l. 207-231). Rien n'est chargé ni créé ; liste faite avant de remettre à neuf (pas de modification pendant la lecture) ; même message. |
| `5f5721dc3` | Coffres T7 automatiques : toutes leurs cases dans des tronçons complets | **OK** (`PointsT7.java` l. 190-201). Même rotation des cases que la pose (`Encombrant.placeLibre` / `poserParties`). La disparition d'un coffre se fait dans son propre tick, toujours dans un tronçon dont les voisins sont complets : rien à changer de ce côté. |
| `5f5721dc3` | Couleur de rareté sans calcul à chaque case ; `raretes.json` lu sans la langue du PC | **OK** (`RaretesObjets.java` l. 137-146, `Rarete.java` l. 89-99, `RaretesServeur.java` l. 96). Mêmes clés qu'avant (« tacz:ammo_box/0 » à « /3 », sinon le texte d'avant) ; l'ordre de création des valeurs de la classe est sans risque (rien ne les lit pendant sa création). Chemin de dessin d'une case relu en entier : plus aucune allocation. |
| `5f5721dc3` | Salles à carte : porte revue toutes les 5 s seulement si les tronçons de ses lecteurs sont complets (`KeycardDoors.lecteursPrets`, `SallesMonde.notifier`) | **OK pour ce qu'il dit** : mêmes tronçons que ceux lus par la recherche des lecteurs, rien de chargé ; une porte sautée est revue 5 s plus tard ; un joueur près de la porte a toujours ces tronçons chargés. **Un effet de bord**, voir « Corrigé » n° 1 : à la reconnexion dans une salle, la porte pouvait rester ouverte jusqu'à 5 s. |
| `f324509e4` | Loot : noyau déclaré des deux côtés | **OK**. La partie serveur du loot s'en sert bien (`Troncons`, `Etalement`, `EcrituresFond`). Petite remarque P1 plus bas (version minimale). |

---

## Corrigé

### 1. Porte d'une salle à carte ouverte quelques secondes à la reconnexion (moyen, loot sans carte) — commit `ae71f5391`

En simple : depuis le 25/10, un joueur qui se déconnecte dans une salle à carte est remis devant la porte à sa
reconnexion, et la porte se referme derrière lui (sinon : se déconnecter dedans, attendre que le loot revienne, se
reconnecter, et reprendre le loot sans carte). Le changement de ce matin ne revoit plus une porte tant que les morceaux de
carte autour de ses lecteurs ne sont pas complets. Or, à la connexion, ces morceaux sont souvent encore en lecture : la
porte (restée ouverte quand le coin s'est vidé après la déconnexion) pouvait rester ouverte jusqu'à 5 s devant le joueur
qui vient d'être sorti, assez pour rentrer. Maintenant ces morceaux sont chargés à ce moment-là (seulement à la connexion
d'un joueur dans une salle, rare, comme la porte elle-même l'était déjà) : la porte se referme tout de suite, comme avant
ce matin. La revue des portes toutes les 5 s ne charge toujours rien.

Pour les développeurs : `modules/bunker/src/serveur/java/fr/heralisz/bunker/serveur/salle/SortieSalles.java` l. 65-70 ;
`modules/bunker/src/commun/java/fr/heralisz/bunker/keycard/KeycardDoors.java` l. 108-128 (`chargerLecteurs`, mêmes
tronçons que `lecteursPrets`).

### 2. Fuite de mémoire dans le jeu des joueurs (moyen, mémoire) — commit `ae71f5391`

En simple : quand quelqu'un passe une carte dans un lecteur, les joueurs à côté voient son bras et sa tête suivre la
carte. Le petit calque d'animation de ce geste était rangé dans une table qui devait oublier le joueur une fois parti,
mais le calque gardait lui-même le joueur : il n'était jamais oublié. Chaque joueur (soi compris) qui avait passé une
carte à côté restait en mémoire jusqu'à la fermeture du jeu, et avec lui tout le monde chargé (morceaux de carte,
entités) après un passage carte / spawn, une mort ou une reconnexion. Plus il y a de passages de carte, plus le jeu
gonfle. Maintenant le calque ne garde le joueur que tant qu'il existe : même animation, mémoire libérée. Même genre de
reste, plus petit, chez le staff qui a tenu la baguette des salles : le monde quitté restait en mémoire jusqu'au prochain
contour ; il est oublié au changement de monde.

Pour les développeurs : `modules/bunker/src/client/java/fr/heralisz/bunker/keycard/client/KeycardSwipes.java` l. 176-233
(`AimLayer` : `WeakReference`, la valeur d'une `WeakHashMap` ne doit pas tenir sa clé) ;
`modules/bunker/src/client/java/fr/heralisz/bunker/salle/client/ContoursSalle.java` l. 47-55 (`oublier`) et
`modules/bunker/src/client/java/fr/heralisz/bunker/client/PartieClient.java` l. 77. Code de HeraliZ seulement.

### 3. Conteneurs de l'épave de l'hélicoptère : petit gel rare à la pose et au retrait (faible, lag) — commit `03a767faa`

En simple : les deux conteneurs T6 posés près de l'épave (et retirés à leur heure, ou quand l'événement est annulé)
peuvent faire plusieurs blocs (12 des 49 T6). Les poser ou les retirer touche aussi leurs blocs voisins, parfois dans le
morceau de carte d'à côté. Seul le morceau du bloc principal était regardé : si le voisin était encore en lecture (un
joueur qui arrive de loin au moment où le conteneur expire), le serveur l'attendait — le même défaut que celui des coffres
T7 corrigé ce matin, oublié ici. Maintenant tous les morceaux doivent être complets : sinon une autre place (pose) ou la
seconde suivante (retrait). Rien ne change pour les joueurs.

Pour les développeurs : `modules/loot/src/serveur/java/fr/heraliz/loot/ConteneursEvenement.java` l. 133-134 (`poser`),
l. 165-178 (`retirer`), l. 291-294 (`tick`), l. 450-463 (`casesPretes`).

---

## À décider par le propriétaire

**P1. Version minimale du noyau dans le loot (petit, risque nul).** Le `mods.toml` du loot demande le noyau 1.0.19 ou
plus (`modules/loot/src/commun/resources/META-INF/mods.toml` l. 33), alors que le loot se sert de fonctions du noyau
apparues le 19 et le 20/11 (`Troncons`, `Etalement`). Sans effet aujourd'hui (les deux partent toujours ensemble) ; avec
un noyau trop vieux, le serveur planterait au premier tick au lieu de refuser de démarrer avec un message clair.
Proposition : demander la version actuelle du noyau.

Toujours ouvertes, déjà proposées par la première relecture (`../a6_loot_bunker.md`, pas redites ici) : porte HBM
« transition seal » à vérifier sur la carte, noms de cache refaits à chaque image dans le rendu des portes de HBM, rotation
du loot à surveiller un soir chargé avec `/diag profil`.

---

## Vérifié sans problème

- **Paquets venus des joueurs** : un seul dans ces deux mods (`ReseauLoot.Fouiller`), revérifié par le serveur (joueur en
  jeu, écran fermé, objet en main, portée de 6 blocs, point visé sur le bloc, nos conteneurs seulement, règles des autres
  mods par les événements de Forge) et maintenant limité à un par tick. Tous les paquets du bunker vont du serveur vers
  les joueurs, sens imposé à l'enregistrement.
- **Duplications** : écran de fouille (cases où rien ne se pose ; Maj + clic, touches 1 à 9, glisser, double clic suivent
  les règles de Minecraft : on ne fait que prendre) ; coffres T7 et caisses de largage (objet pas identifié = « ? » qui ne
  quitte jamais le serveur et ne se prend pas) ; conteneur jamais rempli de nouveau sous les yeux de quelqu'un (la
  rotation ne choisit jamais un conteneur regardé ; une salle se recharge seulement vide ; `/heralizloot reinitialiser` et
  `zone retirer` : staff) ; fragments (aucun usage hors de la Table d'armurier) ; barricade (pas de ramassage, rien ne tombe
  à la casse, pose décidée par le serveur) ; carte d'accès (une par case, délai, lecteur occupé pendant la lecture, carte
  retirée au dernier passage) ; lecteurs de carte introuvables pour les joueurs (caisse « Chantier » vidée).
- **Droits** : `/heralizloot`, `/rarete`, `/salle` demandent l'op 2 ; baguette des salles sans effet hors du staff.
- **Lenteurs** : rien ne parcourt tous les conteneurs à chaque tick (rotation et conteneurs d'événement une fois par
  seconde, étoiles d'un joueur sur 20 par tick, index par zone, par case et par salle) ; tronçons « vraiment prêts » avant
  de lire un bloc partout où c'est périodique ; seuls chargements voulus : reconnexion dans une salle (rare) et commandes
  du staff ; écritures des fichiers en fond (rotation, butin des zombies) ; portes HBM à l'arrêt sans travail par tick.
- **Réflexion** : toutes les cibles existent avec les mêmes signatures (`verif_reflexion.py`) : vers le loot depuis
  Bunker (`SallesLoot`), Événements (`PointsT7.apparaitre`, `ConteneursEvenement.poser / retirer / noms`), Monde
  (`RevelationCaisses.ouvrir`, `TrainLoot.tirer`, `ZonesLoot.conteneurPres`, `RaretesObjets.de`), Quêtes, Clans, Boutique,
  noyau, Interface et Armes (`RaretesObjets.de / couleurNom`, `client.RaretesClient.couleur`, `EvenementsLoot`,
  `ButinZombies.ecouter`, `EconomieBeta.facteur`) ; vers Bunker depuis Quêtes (`SallesMonde.couleurIci`) ; depuis le loot
  vers Tactique, Clans, Grades, Événements, CRac, noyau, TaCZ, geckolib222 et Monde (`ZombieSurvie.tenue()`).
- **Mixins** : Curios `m_6199_` dans `CuriosContainer` et `CuriosContainerV2`, TaCZ `m_7626_` dans `AbstractGunItem`,
  `AttachmentItem`, `AmmoItem` (lus avec `javap` dans les jars livrés) ; caméra du bunker inchangée.
- **Dépendances** : loot → noyau des deux côtés ; bunker → Player Animator chez le joueur (geste de la carte) ; bunker →
  noyau seulement par réflexion protégée.
- **Images par seconde** : conteneurs cuits dans le décor (aucun calcul par image), versions allégées à jour ; couleur de
  rareté des cases sans allocation ; coffres T7 peu nombreux.
- **Textes** : aucun texte nouveau dans ces corrections (commentaires seulement).
- **Licences** : aucun fichier de HBM touché ; crédits et GPL du bunker inchangés.

---

## À tester en jeu

- [ ] **Salle à carte, reconnexion** : se déconnecter dans une salle (porte large si possible), attendre que le coin se
      vide, se reconnecter : sorti devant la porte, qui se referme aussitôt ; essayer de rentrer tout de suite : impossible.
- [ ] **Passage de carte** (verte, bleue, rouge, carte d'un bunker) : bras et tête suivent la carte, carte dans la fente,
      comme avant ; un deuxième joueur à côté voit le même geste.
- [ ] **Staff** : baguette des salles (contours), changer de monde (carte ↔ spawn), reprendre la baguette : les contours
      reviennent.
- [ ] **Hélicoptère** (`/event helico lancer`) : l'épave a ses deux conteneurs T6, ils s'ouvrent après le feu et
      disparaissent à leur heure ; aucune alerte « [Diag] Blocage ».
- [ ] La liste de la première relecture (`../a6_loot_bunker.md`, « À tester en jeu ») reste à faire.

Pas testé : tout en jeu ; la mémoire du jeu après des passages de carte n'a pas été mesurée (`/diag client` sur un soir
chargé le montrerait).

---

## Fin du 21/11 (demande du propriétaire : « tout lançable et terminé à 100 % »)

Propositions encore ouvertes revues (celles de `../a6_loot_bunker.md` et celle de ce compte rendu) : celles qui sont sûres
et ne demandent aucun essai en jeu sont faites, les autres restent notées. **Loot 0.20.10**, commit `b3258e80f` ; Bunker
inchangé (0.14.9, en ligne) ; aucun canal réseau ne change. Rien n'a été testé en jeu. « Contrôle de branche » avec
ce commit (`19008fffa`) : **réussi** (fabrication, contrôles et images).

### Fait

1. **Version minimale du noyau dans le loot (P1 ci-dessus)** : le loot demande maintenant le noyau **1.1.5** au moins
   (avant : 1.0.19). C'est la version qui a apporté les morceaux du noyau dont la partie serveur du loot se sert
   (`Troncons`, `Etalement`, `EcrituresFond`, le 19/11, jamais changés depuis) ; en ligne : noyau 1.1.12. Avec un noyau
   trop vieux, le serveur refuse de démarrer avec un message clair au lieu de planter au premier tick.
   `modules/loot/src/commun/resources/META-INF/mods.toml` l. 28-35.
2. **Revue des conteneurs entamés étalée sur la seconde (a6, proposition n° 3, première moitié)** : en simple, une fois
   par seconde le serveur regarde les conteneurs déjà fouillés (vidés sans fermer l'écran proprement, ou laissés avec des
   objets) pour les rendre à la rotation ou les recycler au bout de 5 min sans personne. Avant, tous dans le même tick
   (environ 0,3 ms d'un coup à 150 joueurs, d'après le calcul) ; maintenant chacun à son tick, toujours une fois par
   seconde : même délai, mêmes règles (objet à vie jamais détruit, salle à carte jamais recyclée, conteneur regardé jamais
   touché). Le rythme de la rotation ne change pas.
   `modules/loot/src/serveur/java/fr/heraliz/loot/RotationLoot.java` l. 159-166 (rangées par tick), l. 429-445 (`tire`,
   `tickDeRevue`), l. 459 (retrait), l. 992-1047 (`entamesTick`) ; `LootServeur.java` l. 90.
3. **Borne de départ et conteneurs posés en jeu** (connu depuis la section 44 de la fiche des essais) : un conteneur de
   loot posé en jeu est évité tout de suite par la borne de départ (avant : seulement après un redémarrage). Un conteneur
   retiré reste évité jusqu'à la prochaine lecture des zones (la borne l'évite un peu trop : sans danger).
   `modules/loot/src/serveur/java/fr/heraliz/loot/ZonesLoot.java` l. 455-477 (`tronconAjouter`).
4. **Petit plus** : le bruit de fermeture d'un écran de fouille fermé de loin (après une téléportation) ne lit plus jamais
   un morceau de carte pas encore complet (le serveur l'attendait pour un son).
   `RotationLoot.java` l. 1465-1468, `ConteneursEvenement.java` l. 446-448.

### Laissé noté (demande un essai en jeu ou une vérification sur la carte)

- **Porte HBM « transition seal »** (a6 n° 1) : vérifier sur la carte qu'aucune n'est posée. Si une l'est : la remplacer
  par une autre porte, ou retoucher le code de HBM (relire son alimentation seulement quand un bloc voisin change), à
  essayer en jeu.
- **Rendu des portes de HBM** (a6 n° 2 : noms de cache refaits à chaque image) : seulement avec un essai des portes en jeu
  (ouverture, skins, shaders), le gain est faible.
- **Relevé des objets de la rotation à chaque sauvegarde automatique** (a6 n° 3, seconde moitié) : le garder en mémoire
  entre deux sauvegardes demanderait de suivre chaque changement de chaque objet ; un seul oubli ferait revenir un objet
  déjà pris après un redémarrage (une duplication). À faire seulement si `/diag profil` un soir chargé le montre.

### À tester en jeu (en plus de la liste ci-dessus)

- [ ] **Conteneur entamé** : fouiller un conteneur, en laisser une partie, s'éloigner : 5 min après, il est vide et un
      autre se remplit plus tard (comme avant). Le vider en entier : il redevient vide tout de suite après la fermeture.
      Une salle à carte garde ses restes jusqu'à sa recharge.
- [ ] **Borne de départ** (staff) : poser un conteneur de loot loin de toute zone, puis plusieurs départs par la borne
      dans ce coin : aucun joueur posé juste à côté.
- [ ] **Démarrage du serveur** : aucune erreur « heraliz_noyau » dans le journal (dépendance du loot satisfaite).

Pas testé : tout en jeu (fabrication et contrôles sur GitHub seulement).
