# Les événements de la carte — guide de branchement

Ce document est le **contrat** de tout événement du serveur (largage, zone chaude, hélicoptère, cargo, convoi…).
Avant d'en coder un, le lire ; quand il est fini, chaque case de la section 2 doit être cochée. Un événement qui ne
coche pas tout ne part pas en ligne.

Le but du serveur est le **classement des clans** : un événement qui ne rapporte pas de points de saison ne sert à rien.

Repères : **[existe]** = déjà dans le code ; **[à faire]** = pas encore codé ; **[à valider]** = chiffre ou choix des
propriétaires.

---

## 1. Le principe

- Un événement = **une classe**, rangée dans **un seul module** : `modules/evenements` (mod `heraliz_evenements`)
  **[existe depuis le 25/10]**. Plus jamais d'événement éparpillé dans un autre module.

### Ajouter un événement, concrètement (le module existe)
1. Une classe `modules/evenements/src/serveur/java/fr/heraliz/evenements/serveur/<Nom>.java` qui implémente `Evenement`
   (`id`, `refus`, `demarrer`, `actif`, `seconde`, `annuler`, `coffreOuvert` ; `minute` pour un événement sans heure fixe
   qui décide lui-même quand partir, comme l'hélicoptère) ; modèles : `Largage.java` (planning), `Helico.java` (hasard).
2. Une ligne dans `PartieServeur.init` : `Directeur.enregistrer(new <Nom>());`.
3. Ses outils communs dans `Outils` : `annoncer` (notification en haut à gauche, par secteur), `secteur`, `journal`,
   `pointsSaison(joueur, "<id>")` ; mémoire sur le disque dans `Etat` (`ouvrir`, `recompenser` AVANT les points).
4. Ses points : `events.points.<id>` et `events.par_jour.<id>` dans `serveur/config/heralisz_clans/saison.json` (l'onglet
   Saison les montre tout seul) + textes `gui.saison.l.<id>` et `gui.saison.c.<id>_plafond` dans les langues du mod des
   clans. Pour un classement de clans (zone chaude) : `SaisonApi.event("<id>", classement, participants)` et
   `events.multiplicateurs.<id>`.
5. Ses textes : `heraliz_evenements.nom.<id>` et ses annonces dans les 3 langues du module.
6. Son planning : un créneau dans `serveur/config/heraliz_evenements/planning.json` ; ses lieux : `/event point ajouter
   <id> <nom>` en jeu.
7. Les tests à faire en jeu dans `docs/TESTS_EN_JEU_*.md`.
- Un **Directeur d'événements** (dans ce module) les lance tout seul : planning dans un fichier
  (`serveur/config/heraliz_evenements/planning.json`, relu sans redémarrer), conditions vérifiées, un seul exemplaire actif
  par type, annonces par **secteur** de la carte M (A1, B2… : jamais la position exacte), journal, points.
- **Rien ne tourne hors événement** : sans événement en cours, le Directeur fait un seul contrôle par minute.
- On **réutilise les briques qui existent** (tableau ci-dessous) au lieu de les recoder.
- Tout ce qui est un chiffre (durées, points, butin, lieux) est **une donnée** dans un fichier, pas dans le code.

### Les briques déjà là (détail : `docs/GAME_DESIGN_25OCT.md` §2.2)

| Brique | Où | Sert à |
|---|---|---|
| Trajectoire d'avion calculée des deux côtés (pas d'entité) | `monde` · `airstrike/strike/FlightPath` | avion cargo, patrouille et chute d'un hélicoptère |
| Caisse larguée en parachute | `monde` · `airstrike/entity/SupplyCrateEntity` | largage public |
| Zone de feu sans blocs de feu, fumée de signal | `monde` · `airstrike/entity/EffectCloudEntity` (`NAPALM`, `MARKER`) | épave en feu, colonne de fumée visible de loin |
| Coffre T7 temporaire (apparaît, disparaît, vidé plus tôt) | `loot` · `PointsT7.apparaitre(...)`, `declencher(...)` | caisses d'un événement |
| Partage des points selon les dégâts | `clans` · `ClanEvents` (`DEGATS_BOSS`) | qui a abattu l'hélicoptère, le boss |
| Vagues de zombies autour d'un point | `clans` · `territory/Assauts`, `assaut.json` | défense d'une zone, d'une épave |
| Capture de zone (rayon, contestation, barre) | `clans` · territoires | zone chaude |
| Extractions par vagues | `monde` · `com.heraliz.extraction` | cargaison à ramener |
| Train calculé d'après l'horaire (pas fait de blocs, balles qui s'arrêtent dessus) | `monde` · `train/` (`BallesTrain`) | modèle du cargo en mer |
| Carte M (secteurs de 500 m, calques) | `monde` · `heraliz_carte` | afficher un événement par secteur |
| Notification en haut à gauche | celle des vagues d'extraction | annonce d'un événement |
| Saison des clans | `clans` · `SaisonApi` (`pve`, `event`, `raid`) | tous les points |

---

## 2. Brancher un nouvel événement : la liste obligatoire

Recopier cette liste dans le compte rendu de l'événement et cocher.

**Directeur**
- [ ] La classe de l'événement est dans `modules/evenements` et se déclare au Directeur : identifiant (`largage`,
      `zone_chaude`, `helico`…), durée maximale, conditions de lancement.
- [ ] Conditions minimales : assez de joueurs **sur la carte** (réglable, ex. 15 pour l'hélicoptère) ; jamais pendant un
      ClashWar ; jamais au-dessus ou à côté du spawn, d'une zone VIP ou d'une base de clan (distance réglable) ; un seul
      exemplaire actif de ce type.
- [ ] Entrée dans `planning.json` (jours, heures, probabilité) ; commandes staff `/event lancer|annuler|liste`.
- [ ] Redémarrage du serveur pendant l'événement : soit annulé proprement (rien de donné), soit repris avec le temps
      restant ; jamais donné deux fois.

**Lieux**
- [ ] Points préparés **hors ligne**, en lecture seule d'une copie de la carte (outil sur le modèle de
      `outils/extractions_carte.py`) : sol valide, jamais dans l'eau (sauf événement en mer), accessible à pied.
- [ ] Commande staff pour en poser en jeu : `/event point ajouter|retirer|liste <type> <nom>`.
- [ ] Contrôle léger au moment du lancement (claim de base proche, zone VIP, bloc du dessus).

**Annonces**
- [ ] Textes en **français, anglais et russe** : clés dans `assets/heraliz_evenements/lang/`, envoyées par
      `Component.translatableWithFallback(clé, texte français, args...)`.
- [ ] Notification en haut à gauche (comme les vagues d'extraction), **par secteur** (« Largage dans le secteur B2 »),
      jamais les coordonnées exactes.
- [ ] Annonce avant le début si c'est un rendez-vous (10 à 15 min avant), annonce de fin et du gagnant.

**Points de saison** (le plus important)
- [ ] Une **clé du barème** dans `serveur/config/heralisz_clans/saison.json` (ex. `"largage": 5`, `"helico": 10`), avec sa
      valeur par défaut aussi dans `Bareme.defaut()`.
- [ ] Appel côté serveur : `SaisonApi.pve(joueur, "<clé>", quantité)` pour un exploit, ou
      `SaisonApi.event("<événement>", classement, participants)` pour un classement de clans (multiplicateur dans
      `events.multiplicateurs` du barème).
- [ ] Partage par **contribution** (dégâts, temps tenu…), pas au dernier tir ; contribution liée au clan du joueur **au
      moment** où il agit.
- [ ] La récompense est **notée sur le disque AVANT d'être donnée** (numéro d'événement « récompensé ») : après un
      plantage, jamais donnée deux fois.
- [ ] Un événement lancé par le staff est **« sans points »**, sauf option explicite pour un event officiel.
- [ ] **Plafond par clan et par jour** pour cet événement (ex. 2 hélicoptères récompensés par clan et par jour).
- [ ] La nouvelle clé est ajoutée à l'onglet **« Saison »** du clan (voir plus bas) avec son libellé et sa condition.

**Butin**
- [ ] Tables T6 / T7 existantes de HeraliZ Loot (`PointsT7`, `t7_largage`, `t7_mallette`…) ; contenu tiré **une seule
      fois**, à la première ouverture, puis gardé.
- [ ] Jamais un objet vendu par un PNJ (sinon on l'achète pour le revendre : règle déjà en place chez Mila).
- [ ] **Un objet d'événement à revendre** dans chaque coffre d'événement (règle ci-dessous).

**Carte M, journal, performances**
- [ ] Calque « Événements » de la carte M : secteur de l'événement en cours (pas le point exact).
- [ ] Journal : une ligne par étape dans `heraliz_evenements/journal.log` (début, lieu, participants, gagnants, points,
      fin, annulation).
- [ ] Hors événement : **rien** ne tourne. Pendant : une seule entité si possible, contrôles **une fois par seconde** sur la
      liste des joueurs (jamais de recherche d'entités dans une zone à chaque tick), effets dessinés chez les joueurs.
- [ ] Mesuré avec `/diag mesure` pendant l'événement et au banc (`/bots scenario`) : ticks dans les objectifs (moyenne
      ≤ 30 ms, 95 % ≤ 40, 99 % < 50).

**Tests** (à faire en jeu par les propriétaires ; à lister dans `docs/TESTS_EN_JEU_*.md`)
- [ ] Lancement par le planning et par le staff ; deux événements à la même heure ; aucun lieu valide.
- [ ] Points : 3 clans + un solo ; vérifier `/saison journal <tag>` ; redémarrage au milieu ; plafond du jour.
- [ ] Déconnexion, mort, changement de clan pendant l'événement.
- [ ] Les 3 langues : aucun texte qui déborde.

### L'objet d'événement à revendre (règle du propriétaire) — **codé le 25/10** (rien testé en jeu)

Chaque coffre d'événement contient **toujours** un objet d'événement. **Revendu à Mila** (marchand racheteur du spawn), il
donne des hérals au vendeur **et des points de saison à son clan**. Trois objets (rendus validés le 25/10,
`maquettes/evenements/objet_evenement_propositions.png`), du plus courant au plus rare : plus il est rare, plus il vaut.

| Objet (identifiant figé) | Rareté | Prix chez Mila | Points de saison | Plafond / clan / jour | Chance (largage) |
|---|---|---|---|---|---|
| Dossier classé (`heraliz_evenements:dossier_classe`) | Rare | 120 hérals | 2 | 5 | 65 % |
| Mallette scellée (`heraliz_evenements:mallette_scellee`) | Épique | 300 hérals | 4 | 3 | 28 % |
| Disque dur militaire (`heraliz_evenements:disque_dur_militaire`) | Légendaire | 700 hérals | 8 | 2 | 7 % |
| Boîte noire d'hélicoptère (`heraliz_evenements:boite_noire_helico`, 25/10, enregistreur de vol orange refait à la demande du propriétaire : `maquettes/helico/boite_noire_v2_zoom.png`) | Légendaire | 400 hérals (choix du propriétaire) | 12 (choix du propriétaire ; seuls points de l'hélicoptère) | 2 | hélicoptère seulement (100 %) |

En moyenne, l'objet d'un largage vaut environ 210 hérals et 3 points (≈ 1/3 des 8 points du largage) : un bonus, pas une
source principale (Mila paie 200 hérals un composant légendaire, 250 une arme légendaire).

| Règle | Détail |
|---|---|
| Où c'est réglé | prix et chances par événement : `serveur/config/heraliz_evenements/objets.json` ; points et plafonds : `events.points.objet_<id>` et `events.par_jour.objet_<id>` de `serveur/config/heralisz_clans/saison.json` ; rareté : `RARETES_IMPOSEES` de `outils/loot_armes.py` |
| Jamais achetable | aucun PNJ ne le vend ; pas dans les caisses de la boutique |
| Marqué à la création | étiquette `HeralizObjetEvenement` : numéro unique, événement d'origine (`largage#12`), signature HMAC du serveur (clé `heraliz_evenements/objets.cle` à la racine du serveur, créée au premier démarrage, **jamais dans le dépôt**) |
| Revente une seule fois | numéro écrit dans `heraliz_evenements/objets_vendus.txt` **avant** le paiement ; une copie ou un objet fait à la main n'est jamais racheté (Mila le refuse) |
| Écran de Mila | au survol : prix et « +N points de saison pour ton clan » ; sinon, en orange, pourquoi il n'y aurait aucun point (pas de saison, sans clan, **clan sans cœur**) et « Garde-le pour plus tard : il vaudra des points » ; même ligne sous le total de la vente ; « Tout vendre » laisse de côté les objets d'événement qui ne rapporteraient aucun point |
| Sans clan, sans cœur, hors saison | le vendeur touche les hérals, aucun point |
| Essai du staff | `/event objet <type>` (op 4) : un objet signé dans son inventaire ; il donne des hérals, **jamais** de points |
| Anti-relais | possible plus tard : pas de points si l'objet a changé de clan dans les dernières minutes **[à valider]** |

Avantage : un **solo** peut voler l'objet et le revendre à un clan, ou le garder pour les hérals ; le PvP continue après
l'ouverture du coffre.

**Déclarer un nouvel objet d'événement** (ex. la boîte noire de l'hélicoptère) :
1. `modules/evenements/src/commun/.../objets/ObjetsEvenement.java` : son identifiant dans `IDS` et une ligne `ITEMS.register` ;
2. son modèle dans `modules/evenements/outils/objets.py` (rendus validés d'abord), puis `--installer` ;
3. son nom dans les 3 langues de `heraliz_evenements` (`item.heraliz_evenements.<id>`) ;
4. son prix et ses chances dans `objets.json` (`prix.<id>`, et un tirage pour l'événement : `tirages.helico = {"<id>": 100}`) ;
5. ses points dans `saison.json` (`events.points.objet_<id>`, `events.par_jour.objet_<id>`), les mêmes valeurs par défaut dans
   `Bareme.defaut()`, et ses textes de l'onglet Saison (`gui.saison.l.objet_<id>`, `gui.saison.c.objet_<id>_plafond`) ;
6. sa rareté dans `RARETES_IMPOSEES` de `outils/loot_armes.py`, puis relancer le script ;
7. le coffre de l'événement l'obtient par `EvenementsApi.objetDuCoffre("<événement>#<numéro>")` à sa première ouverture
   (comme `SupplyCrateEntityServeur` pour le largage). Mila le rachète sans rien changer chez elle.

### L'onglet « Saison » du clan (codé le 25/10)

Les joueurs voient **comment gagner des points** dans l'onglet Saison (touche K). La liste est **construite par le
serveur** depuis le barème réel (`modules/clans/.../saison/SaisonVue.bareme()`) : un nouvel événement y ajoute **sa
ligne** (catégorie, clé, forme des points, condition) et ses textes dans les 3 langues du mod des clans :
`gui.saison.l.<clé>` (libellé), `gui.saison.c.<condition>` (condition). Formes de points disponibles :
`gui.saison.pts.<un|paire|plage|par_niveau|vente|plus|moins|coefs|pct|bientot>`. Une clé sans texte apparaît sous son
identifiant : `outils/verifier_langues.py` ne la voit pas, la vérifier dans l'aperçu (`modules/clans/outils/apercu_saison.py`).

---

## 3. Ce qui rapporte des points aujourd'hui

Vérifié dans le code le 25/10. Chiffres dans `serveur/config/heralisz_clans/saison.json` (relu par `/saison recharger`).

**Deux conditions pour tout :**
- **une saison doit être en cours** : elle commence toute seule au premier démarrage, et repart de 0 avec `/saison reset`
  (puis `/saison reset confirmer`) ; elle ne s'arrête qu'à la date posée par `/saison fin jj/mm/aaaa hh:mm` ;
- **le clan doit avoir posé son cœur** (CRac) : sans cœur, aucun point et pas de place au classement.

| Source | Points | Conditions | Où c'est branché |
|---|---|---|---|
| Zombie / mutant / Brute | 0,01 / 0,02 / 0,5 | hors vagues de capture ; plafond 50 pts par joueur et par jour (petites actions) | `ClanEvents` |
| Boss | 60, partagé selon les dégâts | par clan et par boss : 100 %, puis 20 %, puis 0 dans la journée ; **boss mis de côté pour l'ouverture** | `ClanEvents`, `Saison.boss` |
| Salle à carte verte / bleue / rouge | 5 / 15 / 40 | une fois par recharge de la salle | `loot` · `SaisonLoot` |
| Conteneur T1 → T6 | 0,05 / 0,1 / 0,2 / 0,4 / 0,8 / 1,5 | première ouverture ; plafonné | `loot` · `SaisonLoot` |
| Coffre d'événement T7 | 5 | première ouverture | `loot` · `SaisonLoot` |
| Vente aux PNJ | 1 point pour 1 000 hérals | objets trouvés seulement ; plafonné | `pnj` · `SaisonQuetes` |
| Arme fabriquée | 1 / 2 / 4 / 8 selon la rareté | plafonné | `pnj` · `SaisonQuetes` |
| Quête de Varga | 10 | une fois chacune | `pnj` · `Moteur` |
| Quête de clan | 5 | 3 par jour, 2 par semaine ; temps et distance comptés seulement en jeu actif sur la carte | `clans` · `Quetes` |
| Kill d'un joueur / mort | +1 / −1 | hors même IP, même clan, victime sans arme ni armure ; même victime ×1, ×0,5 puis 0 sur 6 h ; jamais sous 0 | `ClanEvents`, `Saison.kill` |
| ClashWar | places 100 / 60 / 40 / 20, participation 5, **×2** | au moins 4 clans différents | `clashwar` · `Recompenses` |
| Prise du cœur pendant la Purge | −15 % au clan pris, +10 % à l'attaquant | une fois par clan pris et par Purge | `bases` · `PriseCoeur` |
| Capture d'un drapeau (25/10) | zone libre : 3 × valeur × difficulté (PVE) ; prise à un clan qui la tenait depuis 60 min : × 2 (PVP) — **depuis le 27/10, une zone n'est tenue que 2 h, protégée tout ce temps, puis redevient libre** (`tenue_min` de `heralisz_clans-common.toml`) : on ne la prend plus à un clan, seule la capture d'une zone libre compte (la ligne PVP de l'onglet Saison n'est montrée qu'avec `tenue_min = 0`) | même zone et même clan : 12 h ; mêmes deux clans sur la même zone : 48 h ; 40 points de capture par clan et par jour | `clans` · `Territoires.capturer`, `Saison.capture` |

| Largage public (25/10) | 8 au clan du premier qui ouvre la caisse | 3 par clan et par jour ; lancé par le staff : sans points sauf `avec_points` | `evenements` · `Largage` |
| Hélicoptère abattu (25/10, rien testé en jeu) | aucun par lui-même : 12 à la revente de sa boîte noire (`objet_boite_noire_helico`) | 2 par clan et par jour ; lancé par le staff : boîte noire « essai » (sans points) sauf `avec_points` | `evenements` · `Helico` |

**Ne rapporte rien aujourd'hui** :
- le **largage d'une frappe aérienne demandée par un joueur** (jumelles) : sa caisse n'est pas un coffre T7 de HeraliZ
  Loot ; seul le largage public ci-dessus donne des points ;
- la **plateforme pétrolière** : clé `oil_rig` (60) dans le barème, mais aucun code ne l'appelle **[à faire]**
  (étude complète : `docs/EVENEMENT_OIL_RIG.md`).

---

## 4. Le catalogue des événements prévus

### 4.1 Largage public disputé — **codé le 25/10** (rien testé en jeu)
- **But** : escarmouche courte et fréquente : **toutes les 3 h 30** (demande du propriétaire, 25/10 ; `planning.json` :
  `"toutes_les": "3:30"`, premier départ 30 min après le démarrage si rien n'est prévu ; l'heure du suivant est gardée
  dans `heraliz_evenements/etat.json` : un redémarrage ne remet pas l'attente à zéro ; **refusé** (pas assez de joueurs,
  ClashWar, aucun lieu… ; règle du propriétaire du 27/10) : **pas de nouvel essai rapproché**, l'attente repart pour
  3 h 30 comme s'il avait eu lieu, et il part à ce moment-là si les conditions sont remplies ; journal : « pas lance (…),
  prochain essai a HH:MM (intervalle normal) » ; l'ancienne clé `reessai_apres` est ignorée).
- **Déroulement** : un point est **tiré au hasard sur la carte** (dans la bordure du monde moins 150 blocs ; sol à ciel
  ouvert, ni eau ni feuillage, terrain à peu près plat, à 48 blocs au moins des bases ; réglages dans
  `serveur/config/heraliz_evenements/largage.json`, `"lieux": "poses"` ou `"les_deux"` pour utiliser aussi les lieux du
  staff). **Annonce à tous, dans le chat ET en notification, avec les coordonnées 6 minutes avant** (« Largage militaire
  dans 6 min · X …, Z … (secteur C4) »), **rappel 3 minutes avant** ; puis l'avion cargo passe et la caisse descend en
  parachute avec sa fumée (l'avion part 40 s avant la fin de l'annonce : passage et chute). La caisse disparaît au bout
  de 20 min si personne ne l'ouvre. Elle contient toujours un objet d'événement à revendre (ci-dessus). **Son contenu se
  découvre comme un coffre T7** (25/10, demande du propriétaire, pour le suspense) : même écran, objets cachés « ? »
  identifiés un par un, d'autant plus longtemps qu'ils sont rares, seulement pendant que quelqu'un regarde ; rien ne se
  dépose dedans (`fr.heraliz.loot.RevelationCaisses`, appelé par réflexion par la caisse ; identification gardée en
  mémoire seulement : après un redémarrage, ce qui reste se redécouvre).
- **Points** : 8 (catégorie Événements) au clan du **premier** joueur qui ouvre la caisse, 3 largages récompensés par clan
  et par jour au plus (`events.points.largage`, `events.par_jour.largage` de `saison.json`). Lancé par le staff : sans
  points, sauf `/event lancer largage avec_points`.
- **Code** : `modules/evenements/.../serveur/Largage.java` ; la caisse porte le numéro de l'événement
  (`Strike.evenement`, NBT `Evenement` de la caisse) et prévient `EvenementsApi.coffreOuvert` à sa première ouverture.
- **Lieux posés par le staff** (facultatif depuis le tirage au hasard) : `/event point ajouter largage <nom>` (sol dégagé,
  pas dans l'eau, loin des bases), utilisés seulement avec `"lieux": "poses"` ou `"les_deux"`.
- **Sans joueur près du lieu** : la zone visée reste chargée 2 min 30 (ticket de `StrikeManager`), la caisse apparaît
  donc toujours ; ensuite elle attend (au sol, ou figée en l'air si elle tombait encore) qu'un joueur s'approche. Ses
  20 min avant de disparaître ne comptent que zone chargée. Les points ne sont plus donnés 23 min après le lancement.
- **Essai sur le serveur de test (25/10, sans joueur, annonce raccourcie à 2 min)** : point tiré au hasard, annonce et
  rappel avec les coordonnées, départ de l'avion au bon moment, agenda `/event` toutes les 3 h 30, arrêt propre ; la chute,
  l'ouverture de la caisse, l'objet dedans et sa revente restent à voir en jeu.

### 4.2 Zone chaude (règles du propriétaire, 25/10)
- **But** : tenir une zone le plus longtemps possible sur une durée totale de **20 minutes** ; classement « events »
  entre deux ClashWar.
- **Lieux** : jamais au milieu de rien (sinon on campe de loin et on tue tout le monde) : **dans un bâtiment, sur un toit,
  une cour fermée** ; des lieux choisis par le staff (`/event point ajouter zone_chaude <nom>`), petits (rayon 6 à 10
  blocs), avec des murs et des fenêtres : ceux qui tiennent la zone ont un **avantage de position** (couverture), les
  attaquants doivent entrer.
- **Ce que voient les autres** : la **position grossière** de l'équipe qui tient la zone (le cercle de la zone sur la
  carte M et à l'écran, jamais les joueurs eux-mêmes) ; ceux qui tiennent sont **obligés d'être dans le cercle** pour
  compter.
- **Déroulement** : annonce 10 min avant (secteur) ; pendant 20 min, chaque seconde où un seul clan (ou un seul joueur
  sans clan) est dans le cercle lui fait gagner une seconde de tenue ; deux camps dedans = contestée, personne ne gagne ;
  vagues de zombies légères pendant la tenue ; à la fin, classement au temps tenu.
- **Points** : grille des events × 0,3 = **60 / 36 / 24 / 12** pour les 4 premiers clans (× 2 du ClashWar : 200 / 120 / 80
  / 40), 3 de participation ; il faut 60 s de tenue et au moins un combat pour compter ; une zone chaude par jour au départ.
  Coffre T7 au centre pour le 1er (ouvrable par lui seul 1 min, puis par tous) + l'objet d'événement.
- **Existe** : capture de zone (rayon, contestation, barre), cercle lumineux des drapeaux et des extractions, vagues
  `Assauts`, Directeur, annonces, lieux, `SaisonApi.event`.
- **Manque** : la classe `ZoneChaude` (zone temporaire, compteur par camp, HUD du temps tenu), le cercle sur la carte M.
- **Complexité** : moyenne. **Priorité** : 2 (après le largage).

### 4.3 Boss du monde annoncés (quand les boss reviendront)
- **But** : boss disputés au lieu d'être farmés en silence.
- **Déroulement** : le Directeur programme le réveil d'un boss et l'annonce 15 min avant.
- **Points** : 60 partagés selon les dégâts, 100 / 20 / 0 % par clan et par jour (déjà codé).
- **Existe** : donjons, réveil, partage des points. **Manque** : boss placés sur la carte, rééquilibrés (vie, dégâts),
  optimisés ; appel par le Directeur.
- **Complexité** : faible (après le travail sur les boss). **Priorité** : après le retour des boss.

### 4.4 Hélicoptère abattu — **codé le 25/10** (rien testé en jeu)
- Fiche d'origine : `docs/GAME_DESIGN_25OCT.md` §5 ; **règles du propriétaire du 25/10, qui l'emportent** : il n'attaque
  **que** les joueurs qui lui ont fait des dégâts (oubliés après 60 s sans nouveau tir) ; apparition **imprévisible** ; il
  s'écrase **là où il est abattu**, jamais dans l'eau, toujours à un endroit accessible.
- **Quand** : aucune heure dans `planning.json`. Après chaque hélicoptère (abattu, parti, annulé) : 4 h de pause, puis un
  instant tiré au hasard dans les 2 h suivantes ; dès le départ d'un hélicoptère, une heure provisoire (pause + hasard) est
  écrite (un arrêt du serveur pendant le vol ne casse pas le cycle). Heure gardée dans
  `heraliz_evenements/helico_etat.json` (hors du monde) ; au démarrage du serveur, une heure absente, passée ou dans la
  minute est repoussée de 30 à 60 min au hasard (jamais d'hélicoptère juste après un redémarrage). À l'heure prévue :
  10 joueurs en survie sur la carte au moins, pas de ClashWar, un seul à la fois ; sinon (27/10, règle du propriétaire)
  **pas de nouvel essai rapproché** : le cycle repart comme s'il avait eu lieu (4 h de pause puis un instant dans les 2 h ;
  l'ancienne clé `reessai_minutes` de `helico.json` est ignorée). Jamais annoncé à l'avance (staff : `/event helico info`). Le Directeur appelle `Evenement.minute` une fois par minute.
- **Vol** (module monde, `airstrike/entity/HelicopterServeur`, `api/HelicoApi`, `helico/Veille`) : entre par le bord de la
  carte le plus proche d'un joueur, au moins 56 blocs à l'intérieur de la bordure ; son tronçon d'arrivée est demandé par
  un ticket et jamais chargé sur le fil du serveur (60 s au plus, sinon perdu). Patrouille entre des points tirés sur la
  terre ferme près des joueurs (jamais au-dessus du point d'apparition, d'une zone VIP ni près d'une base de clan), 30 à
  45 blocs au-dessus du terrain relevé devant lui tous les 16 blocs ; il note au passage le dernier sol où il pourrait
  s'écraser. **Patrouille de 5 à 10 min** (au hasard) ; attaqué, il ne repart que 60 s après le dernier coup reçu, et
  jamais après **25 min de vol** ; il sort par le bord le plus proche (56 blocs à l'intérieur ; aucune récompense).
  Tronçon tenu chargé par un ticket qui le suit ; jamais sauvegardé (redémarrage : plus là, rien donné).
- **Carte M** : tous les joueurs de la carte voient l'hélicoptère en direct (pictogramme vu de dessus, tourné dans son
  sens de vol, rouge orangé, halo qui pulse) et l'épave tant qu'elle est là (pictogramme ambre), au-dessus des
  extractions ; infobulles « Hélicoptère militaire · abats-le pour récupérer sa boîte noire » et « Épave d'hélicoptère ·
  coffre et boîte noire quand le feu s'éteint ». Le serveur envoie les positions toutes les 2 s (`HelicoCartePacket`,
  canal `airstrike:main` version 3), « retiré » à la fin ; chez le joueur, une marque non rafraîchie s'efface (6 s
  l'hélicoptère, 12 s l'épave), tout est vidé à la déconnexion. Pictogrammes `helico` et `epave` de
  `modules/monde/outils/icones_carte.py` (planche : `maquettes/helico/icones_carte.png`). Rien hors de l'événement.
- **Combat** : 600 PV ; sans coup reçu depuis 30 s, il regagne 1,5 % de ses PV max par seconde. Boîte de touche en
  5 morceaux (`PartEntity` de Forge comme le dragon : 2 pour la cabine, 3 pour la queue ×1,25) que les balles de TaCZ
  touchent (vérifié dans TaCZ 1.1.8 : `EntityKineticBullet.MaybeMultipartEntity`) ; une balle qui traverse plusieurs
  morceaux ne compte que sur le premier (même projectile, même tick) ; seulement les dégâts d'un joueur, explosions ×2 une
  fois par tick. Un joueur en créatif ou en spectateur qui le touche le passe en « essai » (boîte noire sans points). Contre
  ses agresseurs : tir jusqu'à 60 blocs, poursuite jusqu'à 96 (au-delà, il ne les suit plus), cercle de 40 blocs, alerte
  chez la cible, rafale de 8 coups de 2,5 toutes les 3 à 5 s (un rayon par coup, arrêté par le premier bloc : sous un
  toit, rien ; personne d'autre que la cible n'est touché) ; la rafale s'arrête si la cible n'est plus en vue, est à terre
  ou n'est plus un agresseur.
- **Chute et crash** : point de crash cherché une fois (`airstrike/helico/PointCrash`) : la position un peu en avant, puis
  autour jusqu'à 96 puis 200 blocs : sol naturel (étiquette `airstrike:sol_crash`, jamais de matériau de toit), sans eau ni
  feuillage, plat à 3 blocs près sur 9 × 9, 3 blocs pleins sous la surface au centre et aux 4 coins, surface à ±3 blocs de
  la hauteur médiane d'un anneau à 12 blocs (jamais un toit), tronçons déjà chargés, loin des bases (24), du point
  d'apparition (64) et des zones VIP ; zones VIP ou bases illisibles : point REFUSÉ, une ligne au journal, nouvelle lecture
  plus tard. Chute scénarisée de 5 à 8 s (spirale, rotation, descente qui accélère ; le bruit du rotor au loin s'arrête) ;
  impact : explosion sans casse, petits dégâts tout près, bruit pour les zombies ; épave en feu 2 min à 3 min 30 (zone
  `NAPALM` sans bloc de feu, fumée noire `MARKER`), puis coffre T7 (`t7_coffre_armes`, à 2 blocs de hauteur au plus de
  l'épave, sur un sol de crash) avec la boîte noire à sa première ouverture ; épave et fumée retirées 15 min après le feu
  (heures de jeu absolues, vérifiées même quand l'épave ne tourne pas). Aucun point valide : explosion en l'air, boîte
  noire posée sur le dernier sol valide survolé, sinon le sol le plus proche (contrôles relâchés), sinon rien (journal) ;
  tronçon pas chargé : demandé par un ticket, objet posé dès qu'il est là (60 s au plus), pas de coffre.
- **Filet de sécurité** : en heure réelle, un hélicoptère encore en cours après sa vie la plus longue possible (vol
  maximal, sortie, chute, feu, retrait, marge ; ×1,5 contre un serveur qui rame) est retiré (`HelicoApi.annuler`).
- **Points** (choix du propriétaire, 25/10) : **aucun à la destruction**, ni à l'ouverture du coffre de l'épave (un coffre
  d'événement ne donne pas les points d'un coffre T7) : seulement la boîte noire, revendue à Mila : 400 hérals et 12 points
  (`events.points.objet_boite_noire_helico`), 2 par clan et par jour. Hélicoptère du staff sans `avec_points`, ou touché
  en créatif ou en spectateur : boîte noire « essai » (numéro d'événement terminé par `!essai`, hérals seulement). Les
  dégâts de chaque tireur sont notés au journal.
- **Annonces** (joueurs sur la carte) : il patrouille (sans position), il s'est écrasé (secteur), il s'éloigne.
- **Réglages** : `serveur/config/heraliz_evenements/helico.json` (dont `regen_apres_s`, `regen_pct_par_s`,
  `patrouille_min`, `patrouille_max`, `reste_apres_attaque_s`, `vol_max_minutes`, `portee_tir`, `poursuite`). **Tests** :
  `docs/TESTS_EN_JEU_25OCT.md` §11.
- **Reste (v2)** : pièces d'épave à démonter, roquettes, projecteur rouge, compte à rebours au-dessus de l'épave, collision
  de l'épave, XP de clan, points de crash préparés hors ligne.

### 4.5 Cargaison sensible
- **But** : le meilleur PvP naturel : un objet de grande valeur qu'il faut **ramener** à une extraction.
- **Déroulement** : une « Mallette de renseignement » sort d'un coffre d'événement ; tant qu'on la porte, sa position
  approximative (cercle de 75 blocs) apparaît chaque minute sur la carte de tous ; il faut tenir 90 s dans une extraction.
- **Points** : 15 PVE au clan du porteur à l'encaissement + hérals au porteur **[à valider]**.
- **Existe** : extractions par vagues, carte M, cadavres, coffre T7 mallette.
- **Manque** : objet et règles de transport (pas de sac, pas de coffre, tombe à la mort), marqueur sur la carte,
  encaissement.
- **Complexité** : moyenne. **Priorité** : 4.

### 4.6 Cargo en mer (comme le « Cargo Ship » de Rust)
- **But** : un gros événement de clan en mer, visible de loin.
- **Déroulement** : un navire entre par le bord de la carte, jette l'ancre au large ; on l'aborde en barque ou à la nage
  (échelles sur la coque) ; des caisses verrouillées s'ouvrent après un temps passé à les **tenir** ; le navire repart
  au bout de 20 à 30 min avec ce qui reste à bord.
- **Points** : partagés selon le temps tenu près des caisses + coffres T7 + objet d'événement (« Manifeste du cargo »).
- **Existe** : la **même logique que le train** : un objet qui n'est pas fait de blocs, calculé des deux côtés d'après
  l'heure, sur lequel on marche et où les balles s'arrêtent (`train/`, `BallesTrain`, voyageurs).
- **Manque** : modèle du navire (**rendus d'abord**), trajet en mer, marche à bord adaptée (pont, coursives, échelles),
  caisses à tenir, tests de performance.
- **Complexité** : élevée. **Priorité** : 5.

### 4.7 Braquage du convoi (idée Rust / DayZ)
- **But** : un camion blindé roule sur une route, s'arrête (panne ou embuscade) ; il faut tuer ses gardes et ouvrir le
  coffre.
- **Points** : au clan qui ouvre le coffre + objet d'événement ; partage selon les gardes tués **[à valider]**.
- **Existe** : véhicules (camion de ywzj, mais **aucune conduite automatique** dans ce mod), coffres temporaires.
- **Manque** : trajet calculé du camion (comme le train, sans ywzj), **un système de PNJ tireurs** (gardes armés : visée,
  couverture, tirs TaCZ), à mesurer au banc de charge avant tout ; modèle du camion blindé (rendus d'abord).
- **Complexité** : élevée. **Priorité** : 6.

### 4.8 Plateforme pétrolière
- **But** : la « fin de partie » des clans organisés : salle à carte rouge et coffre T7 au sommet.
- **V1** : vagues de zombies costauds à la place de gardes. **V2** : gardes armés (même système de PNJ tireurs que le
  convoi).
- **Points** : clé `oil_rig` déjà dans le barème (60, **trop haut** à côté de l'hélicoptère : proposition 30
  **[à valider]**) + coffre T7 + objet d'événement.
- **Existe** : plateforme sur la carte (construction interdite autour), salles à carte, cartes rouges et fragments,
  vagues `Assauts`. **Manque** : déclencheur, vagues, coffre ; en V2, les PNJ tireurs.
- **Complexité** : moyenne (V1) / élevée (V2). **Priorité** : V1 après la zone chaude si besoin ; V2 en 6.

---

## 5. L'ordre conseillé

| Étape | Contenu | Pourquoi d'abord |
|---|---|---|
| 1 | **Directeur** + **largage public** + **objet d'événement à revendre** + onglet « Saison » du clan | le socle ; tout le reste se branche dessus ; gain rapide, tout existe |
| 2 | **Zone chaude** | conflit court pour la catégorie « events », presque tout le code existe |
| 3 | **Hélicoptère** (rendus et son d'abord) | l'événement phare |
| 4 | **Cargaison sensible** | relie événements et extractions |
| 5 | **Cargo en mer** | gros événement de clan, reprend la technique du train |
| 6 | **Convoi** et **plateforme V2** | demandent un système de PNJ tireurs, à mesurer au banc avant |

À chaque étape : rendus validés pour tout visuel nouveau, fabrication et `outils/verifier.py` sans erreur, liste de la
section 2 cochée, tests ajoutés à `docs/TESTS_EN_JEU_*.md`. **Rien de ce document n'est encore codé ni testé en jeu.**
