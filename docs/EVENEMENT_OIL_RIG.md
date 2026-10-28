# La plateforme pétrolière — événement majeur (étude de game design)

Étude du 25/10, **sans aucun code ni essai en jeu**. Elle remplace la fiche 4.8 de `docs/EVENEMENTS.md` (oil rig V1 / V2)
et sert de cahier des charges aux prochaines sessions. Tout événement suit en plus la liste obligatoire de
`docs/EVENEMENTS.md` §2 (reprise et cochée au §11 ci-dessous).

Repères :
- **[vérifié]** : vu dans le code ou les données (chemin donné) ;
- **[à vérifier en jeu]** : probable, à confirmer par un essai ;
- **[à valider]** : choix proposé, décision des propriétaires.

---

## 0. En une page

**L'idée** : une **caisse stratégique** sur la plateforme pétrolière, qu'on ne peut déverrouiller qu'avec un **Signal de
renfort militaire**, fabriqué en trois étapes rares (plan légendaire → carte rouge stratégique → signal). Utiliser le
signal sur la console de la plateforme **prévient tout le serveur**. Le déverrouillage dure **10 minutes de contrôle de la
console** : il n'avance que tant qu'un seul camp (un clan, ou un joueur sans clan) tient la console ; il s'arrête quand
elle est contestée ; il échoue si plus personne ne la tient. **Le camp qui tient la console à 100 % gagne**, pas celui qui
a appuyé sur le bouton. Deux coffres T7 apparaissent alors, plus un objet d'événement à revendre.

**Ce qui existe déjà et qu'on réutilise** : cartes d'accès rouges et leurs fragments, assemblage par clic droit côté
serveur, coffres T7 temporaires (apparition, tirage unique, disparition), terminal d'extraction, notifications en haut à
gauche, saison des clans (`SaisonApi.event`), anti-duplication de Spartan, signature des bons.

**Ce qui manque (bloquant)** :
1. le module des événements et son Directeur (`modules/evenements`, prévu par `docs/EVENEMENTS.md`, **pas encore codé**) ;
2. **aucun système de plans** : les plans d'ArkaniaZ ont été retirés le 21/10 ; le plan légendaire est un objet à créer ;
3. **le terminal d'extraction (la « fusée ») ne s'obtient nulle part** aujourd'hui : ni loot, ni marchand, ni caisse,
   ni kit (voir §2.3) ; il faut lui donner une source avant d'en faire un ingrédient ;
4. l'accès à la plateforme (bateau ? nage ?) et sa position sur la carte en construction sont à confirmer en jeu.

**Recommandation** : récompense = 2 coffres T7 + « Dossier stratégique » ; **150 points de saison** (catégorie
Événements) au clan qui tient la console à la fin ; une tentative au plus toutes les 4 h sur tout le serveur.

---

## 1. Ce qui existe (analyse)

### 1.1 Cartes d'accès et fragments **[vérifié]**

| Élément | Ce qu'il y a | Où |
|---|---|---|
| Cartes | verte, bleue, rouge (`heralisz_bunker:keycard_green|blue|red`), une par lecteur de sa couleur | `modules/bunker/.../keycard/KeycardRegistry.java`, `AccessLevel.java` |
| Usure | chaque passage réussi use la carte : verte 10, bleue 8, **rouge 6 passages**, puis elle casse (compteur `Uses` dans l'objet) | `KeycardItem.java`, `serveur/config/heralisz_bunker-common.toml` |
| Fragments | 4 fragments numérotés par carte (`keycard_fragment_red_1` à `_4`), raretés : vert Rare, bleu Épique, **rouge Légendaire** | `KeycardRegistry.java`, `outils/loot_armes.py` (`RARETE_FRAGMENTS`) |
| Assemblage | seulement à la Table d'armurier, onglet Divers (les 4 numéros) ; ni grille 2 × 2 (26/10) ni clic droit (28/10) | `serveur/tacz/heraliz_crafts/data/heraliz_crafts/recipes/divers/3_carte_rouge.json` (`outils/crafts.py`) |
| Fréquence | fragments rouges pour 100 conteneurs ouverts : T5 0,15 · T6 0,2 · T7 1,2 ; « environ 4 500 conteneurs ouverts pour une carte rouge complète » (8,3 fragments en moyenne pour avoir les 4 numéros) | `outils/loot_armes.py` (`FRAGMENTS_POUR_100`) |
| Salles à carte | une salle rouge recharge son loot 4 h après la sortie du dernier joueur ; elle rapporte **40 points** de saison à chaque recharge | `heralisz_bunker-common.toml`, `serveur/config/heralisz_clans/saison.json` |

Conséquence pour l'équilibrage : **une carte rouge vaut jusqu'à 6 passages de salle rouge** (6 × 40 = 240 points
possibles, mais étalés sur au moins 20 h et disputés). Consommer une carte rouge pour la plateforme doit donc rapporter
au moins autant (§6).

### 1.2 Plans et recettes **[vérifié]**

- **Aucun plan n'existe aujourd'hui** : « plans de fabrication retirés du jeu le 21/10 (système de craft à refaire) »
  (`outils/loot_armes.py`) ; le catalogue d'objets de HeraliZ Utils ne contient plus que le Bonus de loot
  (`modules/monde/src/commun/resources/heralisz_utils_catalog.json`). Aucun objet `plan_` dans le code.
- La Table d'armurier (TaCZ) ne fait que des armes, munitions et accessoires (gunpack `serveur/tacz/heraliz_crafts`,
  écrit par `outils/crafts.py`). **[à vérifier]** qu'elle puisse donner un objet d'un autre mod : on ne s'appuie pas dessus.
- **L'établi de Minecraft est hors service** pour les joueurs en survie (`[postes_vanilla]`,
  `modules/loot/src/serveur/java/fr/heraliz/loot/ConfigLoot.java`) : seules la grille 2 × 2 de l'inventaire et
  l'assemblage par clic droit restent possibles.
- **Composants de niveau IV** (Légendaire) disponibles : `heraliz_composants:circuit_imprime_iv`,
  `batterie_iv`, `canon_iv`, `ressort_iv`, `kit_pieces_iv`… (`modules/equipement/.../ComponentDefs.java`), trouvés dans le
  loot (T7 : 0,13 composant Légendaire par coffre, §1.4) ou montés de niveau à la Table d'armurier (10 → 1).

**Recommandation : plan consommé.** Pas de système « appris » (il faudrait une sauvegarde par joueur, un écran, et ça ne
se perd jamais : un clan l'apprend une fois et fabrique en boucle) ; pas de plan « conservé » (un seul plan ferait une
infinité de cartes). Un plan consommé, c'est un objet : il se trouve, se vole, se perd à la mort, comme tout le reste.

### 1.3 La « fusée d'extraction » **[vérifié]**

- C'est le **Terminal d'extraction** (`heraliz_extraction:extraction_terminal`) : « PC bricolé relié à une caisse à
  fumigène » ; clic droit maintenu à ciel ouvert = la fusée part, une zone d'extraction de 8 blocs s'ouvre 5 minutes pour
  tous, 90 s à tenir (`modules/monde/.../extraction/ModRegistry.java`, `ExtractionTerminalItemServeur.java`,
  `ExtractionConfig.java`, textes `heraliz_extraction.terminal.*`).
- **Il ne s'obtient nulle part** : aucune trace dans les tables de loot (`modules/loot/.../coffres/t*.json`), le largage
  (`data/airstrike/loot_tables/chests/supply_drop.json`), les marchands (`outils/marchands.py`, `serveur/config/heraliz_quetes`),
  les caisses, les kits, ni dans l'ancien dépôt. Les joueurs s'extraient aujourd'hui par les **zones fixes** ouvertes à
  tour de rôle. **Décision 1** (§13) : lui donner une source.
- Le transformer en « Signal de renfort » ne touche pas au terminal normal : **nouvel objet distinct**, fabriqué à partir
  du terminal (§3). Le terminal garde sa fonction.

### 1.4 Coffres T7 **[vérifié]**

- 7 modèles (`t7_coffre_fort`, `t7_coffre_armes`, `t7_largage`, `t7_mallette`, `t7_reliquaire`, `t7_capsule`,
  `t7_conteneur_heraliz`) (`modules/loot/src/commun/java/fr/heraliz/loot/TypeCoffre.java`).
- `PointsT7.apparaitre(niveau, pos, face, type, présence, point)` pose un coffre qui **apparaît** (animation), reste
  `présence` ticks, puis **disparaît** (30 s après avoir été vidé) ; points nommés « événement » déclenchés par
  `/heralizloot point declencher <nom>` (`PointsT7.java`, `Commandes.java`).
- **Contenu tiré une seule fois**, à la première ouverture, avec le bonus de loot de celui qui ouvre (plafond 25 %), puis
  gardé dans le bloc (`CoffreBlockEntityServeur.generer`, sauvegarde `Genere`, `Rarete`, `Identifie`) ; les objets se
  « révèlent » un par un pendant qu'on regarde (fouille façon DayZ). Plusieurs joueurs peuvent regarder le même coffre :
  les objets sont **déplacés**, jamais copiés (écran de coffre). Le premier qui ouvre donne **5 points** (`conteneur_t7`)
  à son clan (`SaisonLoot.conteneur(p, 7)`).
- **Valeur d'un T7** (espérance calculée sur `t7.json`, script de lecture seule) : **11,4 objets** par coffre, dont
  1,6 arme (0,26 Épique ou mieux, 0,02 Unique), 1,8 pièce d'armure, 1,2 accessoire, 4 composants (0,13 Légendaire),
  1 soin, 0,4 explosif, 0,3 kit de réparation, 0,2 sac. Revente chez Mila (prix de `outils/marchands.py`) : **environ
  280 hérals** par coffre. **Deux T7 ≈ 23 objets, ~560 hérals à la revente**, surtout de l'équipement de bon niveau.
- Repère économique : un clan actif gagne ~13 000 hérals par jour (`docs/SAISON_CLANS.md` §6). Deux T7 ne cassent pas
  l'économie : leur valeur est dans l'équipement, pas dans les hérals.

### 1.5 Annonces et sons **[vérifié]**

- Notification en haut à gauche des vagues d'extraction (`modules/boutique/.../grades/client/chat/ChatHeraliz.java`,
  `NotifExtraction`) ; annonce à tous avec toast et son des clans (`ClanEvents.annoncerATous`, `Packets.NoticeS2C`).
- Sons utilisables : `heralisz_bunker:block.alarm_6` (alarme), `airstrike:radio_confirm`, `airstrike:designator_beep`,
  `heralisz_clans:signal_fusee`, `capture_debut`, `capture_reussie`, `heralizcrates:crate.unlock`, `fx.radio`
  (fichiers `sounds.json` des modules). Les sons de ywzj (`radar_lock_warn`…) existent aussi (`docs/GAME_DESIGN_25OCT.md` §2.1).
- Fumée visible de loin : `EffectCloudEntity` type `MARKER` des frappes aériennes.

### 1.6 La plateforme sur la carte

- Sur la copie v3 de la carte : lieu « petrole », au sud, centre ≈ **x 1001, z 1399** ; quelques conteneurs T1 à T5 sur
  deux niveaux (y 75 et y 81), entre x 992-1011 et z 1393-1410 **[vérifié : `serveur/config/crac/zones_construction.json`,
  pack de données v3 `heraliz_loot/zones/petrole/`]**. Zone interdite à la construction de 150 blocs
  (`outils/zones_construction.py`, `RAYON_PETROLE`).
- La carte se reconstruit sur le serveur : **position, salle rouge éventuelle et accès à confirmer en jeu**. La console, la
  zone de contrôle et les 2 points des coffres se posent **en jeu par le staff** (commandes §8), jamais dans les fichiers
  du monde (règle 7 du projet).
- **[à vérifier en jeu]** comment on y va : bateau de Minecraft, nage (100 blocs ou plus ?), véhicule ? C'est ce qui
  fait la difficulté d'accès ; à régler avant l'ouverture de l'événement.

### 1.7 Persistance et règles existantes **[vérifié]**

- Données « hors du monde » en fichiers JSON écrits d'un coup (fichier temporaire puis remplacement) : saison
  (`SaisonDonnees.ecrire`), grades, quêtes. Données du monde en `SavedData` (`PointsT7`, salles, Purge).
- Déconnexion en combat = mort (noyau, `CombatService`) ; tag de combat de 15 s.
- Bons signés : numéro unique + signature du serveur, joués une seule fois (`heraliz_grades`, voir `docs/AUDIT_25OCT.md` §2).
- Anti-duplication de Spartan : numéro invisible sur les objets **non empilables** suivis
  (`trackedItems = ["tacz:*", "backpacked:*", "heraliz_armory:*"]`, `spartan-common.toml`).
- `/trade` (au spawn seulement) refuse déjà certains objets (objets de match, camos à vie).
- Saison : `SaisonApi.event(événement, classement, participants)` donne les places de la grille (100 / 60 / 40 / 20) ×
  le multiplicateur de l'événement (`saison.json`, `events.multiplicateurs`) ; clé `oil_rig: 60` présente en PVE mais
  **jamais appelée** (aucun code).

---

## 2. Le parcours du joueur

```
Loot rare ──► Plan légendaire ─┐
Carte rouge NEUVE ─────────────┼─► (clic droit) Carte rouge stratégique ─┐
Circuit imprimé IV + Batterie IV┘                                        ├─► (clic droit) Signal de renfort militaire
Terminal d'extraction ───────────────────────────────────────────────────┘
        │
        ▼
Plateforme pétrolière (y aller, monter, atteindre la console)
        │  clic droit maintenu 5 s sur la console
        ▼
ALERTE à tout le serveur ─► 10 min de contrôle de la console (contestable) ─► 2 coffres T7 + Dossier stratégique
```

1. **Trouver le plan** « Plan : Autorisation de la plateforme » (Légendaire) dans le loot de haut niveau (§3.1).
2. **Réunir** une carte rouge **neuve**, un Circuit imprimé IV et une Batterie IV.
3. **Assembler** (clic droit avec le plan) → **Carte rouge stratégique**.
4. **Combiner** avec un Terminal d'extraction (clic droit avec la carte stratégique) → **Signal de renfort militaire**.
5. **Aller sur la plateforme**, rejoindre la console, maintenir clic droit 5 s avec le signal.
6. **Tenir** la console 10 minutes (cumulées) face aux autres.
7. **Piller** les deux coffres T7 qui apparaissent, revendre le **Dossier stratégique** à Mila.

---

## 3. Les objets (liste exacte)

| Objet (identifiant proposé) | Rareté | Empilable | Source | Rôle |
|---|---|---|---|---|
| **Plan : Autorisation de la plateforme** (`heraliz_evenements:plan_oil_rig`) | Légendaire | non | loot (§3.1) | consommé à l'étape 3 |
| Carte d'accès rouge (`heralisz_bunker:keycard_red`) **neuve** (0 passage) | — | non | 4 fragments rouges | consommée à l'étape 3 |
| Circuit imprimé IV (`heraliz_composants:circuit_imprime_iv`) | Légendaire | oui | loot / montée de niveau | consommé à l'étape 3 |
| Batterie IV (`heraliz_composants:batterie_iv`) | Légendaire | oui | loot / montée de niveau | consommé à l'étape 3 |
| **Carte rouge stratégique** (`heraliz_evenements:carte_strategique`) | Légendaire | non | étape 3 | n'ouvre **aucune** salle ; seulement ingrédient |
| Terminal d'extraction (`heraliz_extraction:extraction_terminal`) | Épique **[à valider]** | non | **à créer** (décision 1) | consommé à l'étape 4 |
| **Signal de renfort militaire** (`heraliz_evenements:signal_renfort`) | Unique | non | étape 4 | déclenche l'événement, rien d'autre |
| **Dossier stratégique** (`heraliz_evenements:dossier_oil_rig`) | Unique | non | caisse de la plateforme | objet d'événement à revendre (§6.3) |

Les 4 objets nouveaux portent dès leur création un **numéro unique signé par le serveur** (comme les bons) ; leur
numéro est noté « utilisé » sur le disque au moment où ils sont consommés ou vendus (§7).

### 3.1 Où trouver le plan

Pas de chiffre au hasard : la bonne rareté dépend du **nombre de coffres T7 et de salles rouges ouverts par semaine**,
qu'on ne connaît pas encore. Méthode recommandée :
1. Pendant la première semaine, compter dans `heralisz_clans/saison_journal.log` les lignes `conteneur_t7` (chaque
   premier opener d'un T7) et les recharges de salles rouges.
2. Viser **2 à 4 plans par semaine sur tout le serveur** (avec la carte rouge, ça donne 1 à 3 événements par semaine).
3. Taux de départ, en attendant la mesure **[à valider]** :

| Source | Plans pour 100 ouvertures | Pourquoi |
|---|---|---|
| Coffres T7 (événements, largages des clans) | 3 | la source principale : on se bat déjà pour ces coffres |
| Conteneurs des salles rouges (T6) | 1 | récompense ceux qui ont déjà une carte rouge |
| Conteneurs T6 ordinaires (zones militaires) | 0,05 | la « trouvaille » possible pour un solo |
| Largage public (quand il existera) | 3 | événement disputé |

Ajouté par `outils/loot_armes.py` comme les fragments (`ajouter_fragments` : sans changer les poids des autres objets),
jamais vendu par un PNJ, jamais dans une caisse de la boutique.

### 3.2 Recette de la carte stratégique (étape 3)

**Assemblage par clic droit, décidé par le serveur** (même méthode que l'ancien `AssemblageFragments` des cartes d'accès, retiré le 28/10) : clic droit avec le plan en
main ; si l'inventaire contient **une carte rouge neuve, un Circuit imprimé IV et une Batterie IV**, le serveur retire un
exemplaire de chaque, puis donne la carte stratégique. Sinon : « Il te manque : … ». Pas de place : refusé, rien retiré.

- **Pourquoi pas la grille 2 × 2** : une recette de Minecraft ne sait pas exiger une carte **neuve** (une carte à 5 passages
  sur 6 coûterait presque rien) ni écrire le numéro signé.
- **Pourquoi une carte neuve** : la carte rouge est le vrai prix (~4 500 conteneurs) ; une carte usée ne compte pas.
- **Option plus simple [à valider]** : accepter les **4 fragments rouges** à la place de la carte (même coût, sans
  usure à contrôler).
- Coût : plan + carte rouge + 2 composants Légendaires ≈ le prix d'une sortie de haut niveau pour un clan entier ; rien de
  payable en hérals ni en PB.

### 3.3 Recette du signal (étape 4)

Clic droit avec la carte stratégique quand un **Terminal d'extraction** est dans l'inventaire → le serveur retire les
deux, donne le **Signal de renfort militaire**. Le terminal normal n'est jamais modifié : c'est un objet consommé.

Variante possible **[à valider]** : fusionner les étapes 3 et 4 (plan + carte + composants + terminal → signal). Moins
d'objets à créer, mais on perd « l'étape » que les propriétaires veulent marquer.

### 3.4 Le signal hors de la plateforme

Clic droit ailleurs que sur la console : « Ce signal ne s'active que sur la console de la plateforme pétrolière. »
Rien n'est consommé. Il n'extrait pas et ne sert à rien d'autre.

---

## 4. Le déclenchement sur la plateforme

### 4.1 La console

Un **bloc nouveau** « Console de sécurité » (`heraliz_evenements:console_oil_rig`), **incassable** (comme les lecteurs de
cartes, `strength(-1, 3600000)`), posé en jeu par le staff (`/event oilrig console`). Modèle : armoire de commande
militaire, écran, voyant, fente pour le signal (**rendus à valider d'abord**, règle 6). Un hologramme léger au-dessus
montre l'état (verrouillé, en cours %, contesté, ouvert), dessiné chez le joueur.

### 4.2 La séquence (clic droit **maintenu 5 s** avec le signal en main)

Maintenu comme le terminal d'extraction (bruit de clavier), pour qu'on ne le lance pas par erreur. À la fin des 5 s, le
**serveur** vérifie, dans cet ordre, et refuse avec une explication **sans rien consommer** :

| # | Vérification | Message si refus |
|---|---|---|
| 1 | Le joueur est vivant, en survie, pas à terre, à 3 blocs au plus de la console, vue dégagée | « Approche-toi de la console. » |
| 2 | L'objet en main est un **Signal** dont le numéro signé est valide et jamais utilisé | « Ce signal ne répond pas. » |
| 3 | Aucun événement de la plateforme en cours, **et** le délai global est écoulé (§7.2) | « La console est verrouillée. Prochaine tentative possible dans 2 h 14. » |
| 4 | Au moins **10 joueurs sur la carte** **[à valider]** ; pas de ClashWar en cours ni dans les 30 min | « Pas assez de monde sur la carte pour déclencher l'opération (10 joueurs). » / « Impossible pendant le ClashWar. » |
| 5 | Pas de redémarrage du serveur annoncé dans les 30 min | « Redémarrage prévu : réessaie après. » |

Tout est bon → dans **le même tick** : le numéro du signal est noté « utilisé » et l'état « événement n°N lancé » est écrit
sur le disque (fichier temporaire puis remplacement), **puis** le signal est retiré, **puis** l'annonce part. Un deuxième
clic (même joueur, autre joueur, latence) trouve l'événement déjà en cours : refusé, rien consommé.

---

## 5. Le déroulement (phases A à D)

### Phase A — Activation (quelques secondes)
- Fente de la console qui avale le signal, alarme `heralisz_bunker:block.alarm_6` sur la plateforme, voyant rouge.
- Colonne de **fumée rouge** au-dessus de la plateforme (`EffectCloudEntity` `MARKER`), visible de la côte.
- **Annonce mondiale** (§5.5).

### Phase B — Déverrouillage (10 min de contrôle cumulé **[à valider]**, 45 min au plus)

La **zone de contrôle** : 12 blocs autour de la console, sur la hauteur de la plateforme (posée par le staff).

Chaque seconde, le serveur regarde la **liste des joueurs du monde** (jamais une recherche d'entités) et garde ceux qui
**comptent** : vivants, en survie ou aventure, **pas à terre**, pas spectateur ni staff caché, pas en véhicule, dans la zone.
Il les range par **camp** : leur clan, ou le joueur lui-même s'il n'a pas de clan (il n'y a pas d'alliances sur le serveur).

| Situation | Progression | Affichage |
|---|---|---|
| **Un seul camp** dans la zone | avance (+1/600 par seconde) | « Déverrouillage 46 % » |
| **Deux camps ou plus** | **pause** | « CONTESTÉ » |
| **Personne** | pause, compteur d'abandon qui monte | « Console abandonnée — échec dans 2:41 » |
| Personne pendant **3 minutes d'affilée** | **échec** | annonce d'échec (§5.5) |
| 45 min depuis l'activation sans finir | **échec** | idem |

Règles qui découlent de ce tableau :
- **Les déclencheurs restent exposés** : s'ils partent, l'événement échoue ; ils ne peuvent pas « lancer et revenir ».
- **On peut reprendre l'objectif** : la progression appartient à la console, pas au groupe qui l'a lancée. Un clan qui
  élimine les déclencheurs et tient la zone fait avancer **la même barre**.
- **Mettre à terre** un adversaire suffit à ce qu'il ne compte plus (Réanimation) : le combat sur place décide.
- Pas de remise à zéro en cas de contestation : la barre attend. Pas de punition en cas de bug : voir §7.4.

### Phase C — Confrontation
C'est la phase B vue par les autres : l'annonce les fait venir ; ils contestent (pause), éliminent, ou reprennent la
console. Une vague de zombies (brique `Assauts` des drapeaux, difficulté 2) à 0 %, 33 % et 66 % **[à valider]** ajoute du
danger PvE aux tenants, comme pendant une capture de drapeau.

### Phase D — Déverrouillage terminé
- À 100 %, le **camp qui tenait la zone à cette seconde** est le **vainqueur** : il est noté sur le disque (événement n°N,
  clan) **avant** toute récompense.
- La « caisse stratégique » s'ouvre : les deux coffres T7 **apparaissent** à leurs deux points (animation existante des
  T7, ~2 s de plus de suspense), avec une **présence de 10 minutes** ; lumière verte et son `heralizcrates:crate.unlock` ;
  fumée verte à la place de la rouge.
- Annonce mondiale de fin (§5.5). Après 10 minutes (ou 30 s après qu'ils sont vides), les coffres disparaissent ; la
  console revient « verrouillée » avec le délai global.

### 5.5 Messages (français ; anglais et russe à écrire avec)

| Quand | À qui | Texte proposé |
|---|---|---|
| Activation | **tout le serveur** : notification rouge en haut à gauche + ligne de chat + bip radio discret | « ⚠ ALERTE MILITAIRE — Le système de sécurité de la plateforme pétrolière vient d'être forcé. Une caisse stratégique se déverrouille. Qui tiendra la console ? » |
| 50 % | tout le serveur (notification seule) | « Plateforme pétrolière : déverrouillage à 50 %. » |
| 90 % | tout le serveur | « Plateforme pétrolière : déverrouillage imminent ! » |
| Contesté / abandon | joueurs à moins de 150 blocs : petit indicateur au style du bandeau de Purge (§5.6) | « CONTESTÉ » / « Console abandonnée — échec dans 2:41 » |
| Réussite | tout le serveur | « La caisse stratégique de la plateforme pétrolière est ouverte. [TAG] contrôle la plateforme. » (le nom du joueur jamais) |
| Échec | tout le serveur | « Le système de la plateforme pétrolière s'est reverrouillé. » |

Jamais la position des participants, jamais leur nom ; seulement le lieu (connu de tous) et, à la fin, le tag du clan.

### 5.6 Affichage chez les joueurs proches
Un petit indicateur au **même style que le nouvel indicateur de Purge** (icône simple en haut à gauche), visible à moins de
150 blocs : icône de la plateforme, « 46 % », état (en cours / CONTESTÉ / abandon). Rien d'autre à l'écran. Hologramme de
la console pour ceux qui sont dessus.

---

## 6. Récompenses et équilibrage

### 6.1 Butin
- **2 coffres T7** (`t7_coffre_armes` et `t7_conteneur_heraliz`, tables T7 existantes) : ~23 objets, ~3 armes dont ~0,5
  Épique ou mieux, ~3,5 pièces d'armure, ~8 composants (§1.4). Tirés **une seule fois**, à la première ouverture.
- **1 Dossier stratégique** garanti dans l'un des deux (petit ajout à `CoffreBlockEntityServeur.generer` : une liste
  d'« objets imposés » passée à `PointsT7.apparaitre`).
- Option **[à valider]**, seulement si la mesure montre que c'est trop peu pour le risque : une 3e case « arsenal » avec
  une arme Épique ou mieux garantie. Ne pas la mettre au départ.

### 6.2 Points de saison
- **150 points, catégorie Événements, au clan vainqueur** (celui qui tient la console à 100 %) **[à valider]**. Codé avec
  l'API existante : `SaisonApi.event("oil_rig", [clan vainqueur], [clan vainqueur])` et un multiplicateur
  `"oil_rig": 1.5` (place 1 = 100 × 1,5). La clé PVE `oil_rig: 60`, jamais utilisée, est retirée.
- Pourquoi 150 : rare (1 à 3 fois par semaine), très cher à préparer (carte rouge = jusqu'à 240 points de salles), très
  risqué ; au niveau d'une 2e place de ClashWar (120) ou d'une victoire (200).
- **Aucun point** pour celui qui déclenche s'il ne gagne pas. **Aucun point** pour un vainqueur sans clan (le butin est à
  lui). Événement lancé par le staff : **sans points** (sauf option « officiel »).
- **1 victoire récompensée par clan toutes les 48 h** **[à valider]** : au-delà, butin oui, points non.
- Les 2 coffres T7 donnent en plus leurs **5 points** habituels au clan de leur premier ouvreur (règle existante).

### 6.3 Le Dossier stratégique (objet d'événement, règle de `docs/EVENEMENTS.md`)
- Revendu à **Mila** : **1 000 hérals** au vendeur et **25 points** (clé `objet_event_oil_rig`) à son clan **[à valider]**.
- Jamais vendu par un PNJ, jamais dans une caisse de la boutique ; numéro signé, revendu une seule fois.
- Il crée une deuxième course : un solo peut le voler et le vendre ; un clan doit le ramener au spawn vivant.

### 6.4 Fréquence et monopole
| Frein | Proposition | Rôle |
|---|---|---|
| Rareté du plan | 2 à 4 par semaine (mesuré, §3.1) | le vrai limiteur |
| Carte rouge neuve | ~4 500 conteneurs | coût de préparation |
| Délai global | **4 h** entre deux tentatives (réussies ou non) **[à valider]** | pas d'enchaînement |
| Un seul à la fois | oui | pas de double événement |
| Joueurs sur la carte | 10 au moins | toujours quelqu'un pour contester |
| Points | 1 victoire récompensée par clan / 48 h | un gros clan ne monopolise pas le classement |

---

## 7. Protections contre les abus

### 7.1 Objets
| Abus | Parade |
|---|---|
| Dupliquer un plan, une carte, un signal, un dossier | numéro unique **signé par le serveur** à la création ; noté « utilisé » sur le disque à la consommation ou à la vente ; une copie (créatif, faille) est refusée ; objets ajoutés à `trackedItems` de l'anti-duplication de Spartan |
| Les acheter avec des PB (pay-to-win) via `/trade` | ces 4 objets **refusés dans `/trade`** (seul échange qui peut porter des PB) ; ils se donnent en main propre dans le monde |
| Les acheter / revendre en boucle | jamais vendus par un PNJ ; refusés par le recycleur ; seul le dossier se revend (une fois) |
| Carte rouge usée dans la recette | seulement une carte neuve (0 passage) |
| Signal utilisé ailleurs | seulement sur la console ; ailleurs, rien ne se passe et il reste dans la main |
| Perdre son signal par un refus | aucune consommation avant que toutes les vérifications soient passées |

### 7.2 Déclenchement
| Abus | Parade |
|---|---|
| Deux joueurs en même temps, double clic, latence | état « en cours » écrit avant la consommation ; tout second essai voit l'événement en cours |
| Déclencher puis partir | progression seulement en présence ; échec après 3 min sans personne |
| Déclencher la nuit sans personne pour contester | 10 joueurs sur la carte au moins |
| Deux clans complices (l'un lance, l'autre « conteste » pour rien) | rien à gagner : un seul vainqueur, pas de points de participation |
| Se cacher dans un recoin de la zone | zone posée par le staff sur la plateforme seulement ; présence vérifiée sur la hauteur ; à terre = ne compte pas |
| Changer de clan au dernier moment | le camp est celui **au moment de la seconde qui finit la barre** ; le délai de 24 h avant de rejoindre un autre clan (saison) rend ça inutile **[à vérifier]** |

### 7.3 Déconnexions
- Un participant qui se déconnecte **ne compte plus** ; s'il est en combat, la règle du noyau s'applique (mort).
- Celui qui a déclenché n'a aucun droit particulier : sa déconnexion ne change rien.

### 7.4 Redémarrage et pannes
| Moment | Comportement |
|---|---|
| Pendant la phase B | l'état (progression, n° d'événement, signal utilisé) est sur le disque. Au retour du serveur : **5 minutes de grâce** (« Le système de la plateforme redémarre »), puis les règles reprennent. Serveur éteint **plus de 30 minutes** : événement **annulé** et **signal rendu** au déclencheur (à sa prochaine connexion, noté sur le disque) : c'est une panne, pas un échec |
| Après la réussite | le vainqueur et les points sont déjà écrits : jamais redonnés. Les coffres T7 sont des blocs du monde : ils gardent leur contenu et leur heure de disparition |
| Plantage au milieu de la remise des récompenses | ordre : écrire « récompensé » → donner les points → faire apparaître les coffres ; au redémarrage, ce qui est noté fait foi, rien en double |
| Console cassée ou retirée par erreur | l'événement s'annule proprement, signal rendu ; journal |
| Bug qui bloquerait l'événement | `/event oilrig annuler [rendre]` (staff) remet tout à zéro et peut rendre le signal |

---

## 8. Contraintes techniques

- **Prérequis** : module `modules/evenements` + Directeur (`docs/EVENEMENTS.md` §1). L'événement de la plateforme y est une
  classe ; il se déclare au Directeur (délai global, conditions) même s'il est lancé par un joueur.
- Objets et bloc dans `src/commun` du module (les deux jars) ; toute la logique dans `src/serveur` ; affichage
  (hologramme, indicateur) dans `src/client` ; méthode `docs/METHODE_CLIENT_SERVEUR.md`.
- Réglages en données : `serveur/config/heraliz_evenements/oil_rig.json` (durées, rayon, joueurs mini, délai, points du
  dossier, objets imposés) ; points dans `saison.json`.
- État : `heraliz_evenements/oil_rig_etat.json` hors du monde (écriture atomique) ; numéros d'objets utilisés :
  `heraliz_evenements/objets_utilises.json` ; journal : `heraliz_evenements/journal.log`.
- Commandes staff : `/event oilrig console` (pose la console face au regard), `/event oilrig zone <rayon>`,
  `/event oilrig coffre 1|2` (points des coffres), `/event oilrig etat`, `/event oilrig annuler [rendre]`,
  `/event oilrig donner <joueur> plan|carte|signal` (essais, **sans points**).
- Coffres : `PointsT7.apparaitre` existant + petit ajout « objets imposés » (dossier) dans HeraliZ Loot.
- Loot du plan : `outils/loot_armes.py` (comme les fragments), jamais les tables à la main.
- Source du terminal d'extraction : idem (décision 1).
- Refus dans `/trade` et au recycleur : une étiquette d'objet (`heraliz_evenements:lie`) lue par HeraliZ Grades et HeraliZ
  Quêtes.
- Textes en 3 langues (`assets/heraliz_evenements/lang/`), `translatableWithFallback` côté serveur.

## 9. Performances

| Moment | Coût |
|---|---|
| Hors événement | **rien** (la console ne « tique » pas : bloc sans entité animée côté serveur ; l'hologramme est dessiné chez le joueur) |
| Phase B | 1 contrôle par seconde : liste des joueurs du monde (≤ 60) et distance à la console ; 3 vagues de zombies déjà plafonnées ; 1 fumée ; 1 son toutes les 10 s |
| Phase D | 2 coffres T7 (blocs existants) pendant 10 min |
| Après | console verrouillée, rien ne tourne ; fichiers d'état mis à jour 3 ou 4 fois par événement, jamais à chaque tick |

Risques : le combat lui-même (tirs, grenades, 20 à 40 joueurs sur une petite plateforme) coûte plus que l'événement. À
mesurer avec `/diag mesure` et au banc `/bots scenario` placé sur la plateforme. Pas de tronçon tenu chargé : la
progression n'avance qu'avec des joueurs présents (qui chargent la zone).

---

## 10. Plan de tests (serveur de test, puis en jeu par les propriétaires)

1. **Objets** : assemblage avec chaque ingrédient manquant (message, rien retiré) ; carte rouge usée refusée ; inventaire
   plein ; plan / signal copiés en créatif puis utilisés → refusés.
2. **Déclenchement** : signal hors plateforme (rien) ; < 10 joueurs ; pendant le ClashWar ; deux joueurs en même temps sur
   la console ; double clic ; déconnexion pendant les 5 s.
3. **Phase B** : un camp seul (10 min) ; contestation à 2 clans (pause) ; un solo contre un clan ; mise à terre des tenants ;
   abandon 3 min (échec) ; reprise par un autre clan (même barre) ; plafond de 45 min.
4. **Phase D** : vainqueur = camp présent à 100 % ; points (150) dans `/saison journal <tag>` ; coffres apparus, contenu tiré
   une fois, dossier présent ; disparition après 10 min.
5. **Dossier** : vente chez Mila (hérals + 25 points) ; deuxième vente d'une copie refusée ; vente par un solo (hérals seuls).
6. **Redémarrages** : pendant B (reprise après 5 min) ; serveur éteint 31 min (annulation + signal rendu) ; juste après la
   réussite (rien en double).
7. **`/trade`** : plan, carte, signal, dossier refusés ; recycleur refusé.
8. **Délais** : 2e tentative avant 4 h refusée avec le temps restant ; 2e victoire du même clan en 48 h sans points.
9. **Langues** : français, anglais, russe ; aucun texte qui déborde (indicateur, hologramme, messages).
10. **Performances** : `/diag mesure` pendant un combat sur la plateforme ; banc de 20 bots sur place.

---

## 11. Liste de branchement (`docs/EVENEMENTS.md` §2)

- [ ] Classe dans `modules/evenements`, déclarée au Directeur (identifiant `oil_rig`, 45 min au plus, conditions §4.2)
- [ ] Conditions : 10 joueurs sur la carte, pas de ClashWar, un seul à la fois, délai global 4 h
- [ ] Lancement par le joueur (signal) — pas dans `planning.json` ; commandes staff `/event oilrig …`
- [ ] Redémarrage : reprise ou annulation avec signal rendu (§7.4), jamais deux récompenses
- [ ] Lieu posé en jeu par le staff (console, zone, 2 points de coffres)
- [ ] Textes FR / EN / RU, notification en haut à gauche, annonces de début, 50 %, 90 %, fin, échec
- [ ] Clé de barème : `events.multiplicateurs.oil_rig = 1.5` ; `objet_event_oil_rig = 25` ; `Bareme.defaut()` à jour
- [ ] Vainqueur noté sur le disque avant les points ; événement du staff sans points ; 1 victoire / clan / 48 h
- [ ] Onglet « Saison » : libellés `saison.source.oil_rig`, `saison.source.objet_event_oil_rig` et leurs conditions
- [ ] Butin : 2 T7 existants + dossier imposé ; aucun objet vendu par un PNJ
- [ ] Calque « Événements » de la carte M : la plateforme pendant l'événement
- [ ] Journal `heraliz_evenements/journal.log` : activation, %, camps présents, vainqueur, points, fin, échec
- [ ] Performances mesurées (`/diag mesure`, banc)
- [ ] Tests §10 dans `docs/TESTS_EN_JEU_*.md`

---

## 12. Ce qu'il faut créer, et la taille du travail

**Rendus à faire valider d'abord (règle 6)** : la console de sécurité (bloc + hologramme), les icônes du plan, de la carte
stratégique (carte rouge barrée de noir et d'or ?), du signal (le terminal d'extraction avec une bande rouge et une
antenne ?) et du dossier stratégique ; l'indicateur proche (au style du nouvel indicateur de Purge). Pas de nouveau
modèle de coffre au départ (les deux T7 existants).

| Étape | Contenu | Taille |
|---|---|---|
| 0 | Module `evenements` + Directeur minimal (délai global, conditions, journal, annonces) — prérequis commun à tous les événements | 1 à 2 sessions |
| 1 | Rendus (console, 4 icônes, indicateur) puis validation | 1 session + validation |
| 2 | Objets, numéros signés, assemblages par clic droit, refus `/trade` et recycleur, anti-duplication | 1 session |
| 3 | Console, phases A-D, zone de contrôle, sauvegarde et reprise, annonces, indicateur, points | 2 sessions |
| 4 | Loot : plan dans les tables, source du terminal d'extraction, dossier imposé dans les T7 | ½ session |
| 5 | Tests sur le serveur de test, banc de charge, réglages | 1 session + essais en jeu |

Soit **environ 6 à 8 sessions de travail**, plus le temps de validation des rendus. **Pas avant l'ouverture.**

---

## 13. Décisions des propriétaires

1. **Source du Terminal d'extraction** (aujourd'hui introuvable). *Recommandé* : le mettre dans le loot, rare (Épique),
   en T5, T6, T7 et dans le largage ; il redevient utile pour s'extraire seul, et devient l'ingrédient du signal.
   *Autre* : remplacer cet ingrédient par un fumigène (grenade fumigène existante), mais on perd l'idée de « fusée ».
2. **Récompense**. *Recommandé* : 2 coffres T7 + Dossier stratégique (1 000 hérals, 25 points) ; mesurer, puis ajouter si
   besoin une arme Épique garantie. *Autre* : la 3e case garantie dès le départ.
3. **Points de saison**. *Recommandé* : 150 points (Événements) au clan qui tient la console à 100 %, 1 victoire récompensée
   par clan toutes les 48 h. *Autre* : partage entre les clans présents au prorata du temps tenu (plus juste, mais plus
   difficile à lire pour les joueurs).
4. **Cadence**. *Recommandé* : délai global de 4 h, 10 joueurs sur la carte au moins, jamais pendant le ClashWar ni dans
   les 30 min avant. *Autre* : un créneau fixe par jour (rendez-vous), au risque qu'il ne se passe rien faute de signal.
5. **Plan**. *Recommandé* : consommé, 2 à 4 par semaine sur tout le serveur (taux réglé après une semaine de mesure) ;
   carte rouge **neuve** exigée (ou les 4 fragments rouges, à choisir).

### Réponses du propriétaire (25/10)
- **Plan** : un objet « plan » à créer, demandé dans la recette de l'objet de la plateforme (le signal) : décision 5 =
  plan exigé dans la recette (consommé, comme recommandé).
- **Fusée d'extraction** : loot **Épique** ajouté aux tables (fait le 25/10 : `EXTRAS` de `outils/loot_armes.py`, environ
  0,4 pour 100 conteneurs en T5, 0,7 en T6, 3 en T7) : décision 1 réglée.
- Récompense, points et cadence : principe du propriétaire, « récompenser la difficulté d'y accéder et de le faire » :
  les recommandations (2 T7 + Dossier, 150 points, une tentative toutes les 4 h) sont retenues jusqu'à nouvel ordre.

Rien de ce document n'a été développé ni testé en jeu (sauf la fusée dans le loot, codée, pas testée).
