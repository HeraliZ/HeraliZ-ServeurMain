# Refonte visuelle, immersion Day-Z et zombies — audit et propositions (25/10)

Étapes A (audit) et B (propositions) de la méthode demandée. **Rien n'a été modifié, fabriqué ni lancé** pour ce rapport :
lecture du code, des réglages, des jars et d'une copie du monde de test (`C:\hzt\dayz_map`, carte v3). Une autre session
travaille en même temps (saison, événements) : aucun de ses fichiers n'a été touché.

Repères utilisés partout :

- **[vérifié]** lu dans le code, un réglage ou un jar ;
- **[calcul]** calculé hors du jeu (tailles de textures, durées) ;
- **[estimé]** déduit, pas vérifié ;
- **[non testé]** demande un essai en jeu ou un banc de charge ;
- **[mesuré]** : **rien dans ce rapport n'a été mesuré**. Aucun chiffre de FPS ni de capacité n'est promis.

---

## 0. L'essentiel

| Sujet | Constat principal | Gravité |
|---|---|---|
| **Cycle jour/nuit** | Le monde est figé à **midi** : `doDaylightCycle=false`, `DayTime=6000` dans le `level.dat` de la copie de test [vérifié]. Aucun mod ne fait avancer l'heure. Les boutons JOUR/NUIT du panel du staff posent une heure fixe (et une nuit cliquée devient permanente). | haute |
| **Zombies en extraction** | Le peuplement fait apparaître des zombies **autour de chaque joueur**, toutes les 2 s, à 24–56 blocs, **de préférence dans les bâtiments**, sans test de ligne de vue, et il **ignore les zones d'extraction** [vérifié]. Un groupe immobile qui extrait est « rempli » jusqu'à 24 zombies, recomplétés à chaque mort. C'est le bug vu à l'ouverture. | **critique** |
| **Zombie Awareness** | Toujours actif, jamais mesuré. Il entend les grognements et le terminal d'extraction mais **probablement pas les tirs TaCZ** (envoyés par paquet) [estimé]. Chemins longs (64 blocs) qui contournent nos niveaux d'IA. | haute |
| **1 500 zombies** | **Impossible à juger aujourd'hui** : aucun coût par zombie n'a jamais été mesuré, le banc n'a ni Mohist ni peuplement. Le peuplement actuel n'est pas fait pour des populations par zones. | haute |
| **Interfaces** | 4 HUD prioritaires analysés : textes agrandis (`pose().scale`), jauges qui avancent par crans de 1/20 s, aucune animation de sortie, **23 copies différentes** du kit d'interface, 7 rouges et 5 verts différents, coins de l'écran encombrés sans gestion commune. | moyenne |
| **Graphismes** | Pas de chargeur de shaders, et **l'anti-triche Spartan les interdit** (`blockShaders=true`). Terrain qui mélange du 16, du 32 et du 64 px côte à côte (aspect « patchwork »). Herbe à 33–85 faces par touffe (Minecraft : 4–8). 306 textures orphelines, 160 doublons. **Nos nuages sont coupés chez tout le monde** par les réglages imposés. | moyenne |

**À vérifier sur le serveur en ligne avant ce soir** (valeurs de la copie de test, peut-être différentes en ligne) :
`/gamerule doDaylightCycle`, `/gamerule doMobSpawning` (doit rester `false`), **`/gamerule keepInventory`** (la copie de
test est à **`true`** alors que la règle du serveur est `false`), `/time query daytime`.

---

## 1. Cartographie des systèmes

| Domaine | Où | Ce qui existe | Statut |
|---|---|---|---|
| HUD à terre / réanimation | `modules/survie` (reanimation) : `DownedHud`, `ClientEvents`, `ClientDowned`, `LocalEffects` | overlay plein écran, carte « pouls » ECG, vignette, désaturation ; carte d'actions quand on vise un joueur à terre ; anneau au centre pendant la réanimation | [vérifié] |
| Cercle des soins | `modules/armes`, paquet `fr.heralisz.tactical` (**fork GPL de LR Tactical**) : `UsingProgressOverlay` | anneau de 72 segments autour du réticule, nom, temps restant | [vérifié] |
| Usure de l'arme | `modules/monde` (`fr.heraliz.usure.client`) : `HudUsure`, `FormesHud`, `Maillage` | pastille bas-droite sous le HUD de TaCZ, anneau d'état | [vérifié] |
| Autres HUD en jeu | combat (noyau), corps, extraction, jumelles, pings, barre DayZ, voix, clans, solde, quêtes, radio Varga, ClashWar, construction, vision nocturne, chat, tab | une vingtaine d'overlays, chacun avec ses cotes | [vérifié] |
| Kit d'interface | `modules/noyau/.../client/kit` (référence) + 23 copies `Ui.java` dans les modules | verre, DM Sans/Onest, palette, marges 16/8/12 ; `placer`, `Anim`, `anneau` seulement dans certaines copies | [vérifié] |
| Rendu côté joueur | Embeddium 0.3.31, EntityCulling 1.11.2, ModernFix, FerriteCore, TaCZ 1.1.8, ywzj, geckolib222, cfm patch, Player Animator | pas d'Oculus ; Spartan refuse les shaders | [vérifié] |
| Packs imposés | `modules/interface/.../resourcepacks/textures` (2 820 PNG), `.../herbe` (14), `modules/monde/.../heraliz_terrain` (3) | packs du joueur refusés | [vérifié] |
| Ambiance | `modules/monde/.../utils/ambiance` : ciel (étoiles, lune, nuages doux), brouillard selon l'heure et la météo, filtre d'image, tempête du désert ; Décors : `/brume`, zones gaz/radiation/froid | complet mais figé par le midi permanent | [vérifié] |
| Heure du jeu | aucune horloge maison ; panel du staff `PanelStaff.java:417-419` | midi figé | [vérifié] |
| Zombies | `modules/monde/.../utils/zombies` : `ZombieSurvie` (9 tenues), `SpawnZombiesServeur`, `Peuplement`, `PlafondZombies`, `IaZombies`, `CheminsZombies`, `AttaquesZombies` (M&Z seulement) | ~1 800 lignes ; étape 4 « zombie maison » en partie | [vérifié] |
| Mods tiers zombies | Mutants & Zombies 0.9.8 (« tous droits réservés »), Zombie Awareness 1.13.1, CoroUtil 1.3.7 | toujours livrés et actifs | [vérifié] |
| Mesure | `/diag mesure`, `/diag start`, `/diag fuites` (noyau) ; `/bots` (banc) | pas de commande pour poser N zombies, listes zombies non suivies | [vérifié] |

---

## 2. Problèmes identifiés

### 2.1 Interfaces
- **Texte agrandi** par `pose().scale` dans le HUD à terre (×2, ×1,6, ×1,3), la réanimation (×1,3), les quêtes (×0,7/0,8), la radio, ClashWar, jumelles, extraction : texte flou ou granuleux [vérifié].
- **Jauges à 20 images/s** : sang, abandon, soins avancent par crans de tick, sans interpolation ; l'anneau des soins et celui de la réanimation restent bloqués à 100 % jusqu'à la réponse du serveur [vérifié].
- **Pas d'animation de sortie** : HUD à terre, soins, réanimation, usure disparaissent d'un coup [vérifié].
- **Soins** : l'aide « [Clic droit] pour arrêter » ne s'affiche qu'à 100 %, au moment où le serveur termine le soin : elle est inutile [vérifié].
- **Usure placée sur des cotes en dur de TaCZ** (code d'un autre auteur) et sur une largeur de barre DayZ fixe (`BARRE=134`), alors que la barre se règle (espacement, inclinaison) : chevauchement possible, bord gauche qui « flotte » [vérifié].
- **Réanimation** : aucune information de réussite ou d'échec à l'écran, seulement la barre d'action vanilla [vérifié].
- **Coins encombrés** : haut-droite (solde, toasts de clan, notifications de quêtes, REC voix) ; haut-gauche (groupe vocal sur le panneau de zone du clan, notification d'extraction qui le recouvre de 4 px) ; autour du réticule (soins, corps, réanimation, construction, refus de pose, touche d'interaction) [vérifié positions, estimé simultanéité].
- **Allocations à chaque image** (textes traduits, `String.format`, listes neuves) dans le HUD à terre, les soins, la voix, les clans, le solde, le chat [vérifié].
- **Police vanilla** encore utilisée : voix, touche d'interaction, refus de pose [vérifié].

### 2.2 Graphismes
- **Terrain patchwork** : bloc d'herbe, terre, pierre, bois en 32 px ; herbe haute, chemins, boue, mousse, grès en 16 px ; neige, terre cuite en 64 px [vérifié tailles, estimé effet].
- **Herbe lourde** : 32,8 faces par touffe en moyenne, ~85 pour les hautes herbes (Minecraft 4 et 8), en 16 px [vérifié].
- **Grande texture des blocs** : ~52,7 millions de pixels → au moins 8192×8192, ~256 Mo de mémoire graphique (340 Mo avec mipmaps) ; cfm 34 %, conteneurs de loot 30 %, bunker 15 % [calcul].
- **Ressources inutiles** : 306 textures aux anciens noms jamais utilisées mais cousues dans la grande texture, 160 doublons exacts, `snow.png` en 1024×4096 (~16 Mo quand il neige) [vérifié].
- **Nuages coupés chez tout le monde** par `ReglagesDefaut` alors que nos « nuages doux » ne coûtent qu'un carré [vérifié].
- **Aucun fichier de réglages livré par le launcher** : Embeddium et EntityCulling tournent avec leurs réglages par défaut [vérifié].
- **Mipmaps** : plus aucune texture ne les casse aujourd'hui [vérifié par relecture du script sur les 40 jars et les 3 packs].

### 2.3 Cycle jour/nuit
- Midi figé (voir §0). Conséquences : jamais de nuit, le ×1,25 de nuit des zombies ne joue jamais, la quête « Patrouille de nuit » de Varga est impossible, nuages et lune figés, jamais de brume du matin ni de tempête du désert [vérifié dans le code].

### 2.4 Zombies
1. **Peuplement aveugle aux extractions** et centré sur les joueurs (critique, voir §0) [vérifié].
2. **Pas de « vrai » sommeil de l'IA** : niveaux 16 / 40 blocs, mais jamais endormi ; le niveau proche revoit ses objectifs **plus souvent** que Minecraft (tous les 2 ticks au lieu de 3) [vérifié].
3. **Budget de chemins sans file ni priorité** : un zombie au contact peut être refusé au profit d'un lointain [vérifié].
4. **Boucles globales** : `PlafondZombies` parcourt toutes les entités chaque seconde ; `IaZombies` calcule zombies × joueurs dans un seul tick, le même que le peuplement [vérifié].
5. **Notre zombie frappe au contact comme Minecraft** : les attaques esquivables n'existent que pour les zombies de Mutants & Zombies [vérifié].
6. **Pas de tenue police**, pas de sons ni de butin propres, script de peinture des peaux perdu [vérifié].
7. **Réglages de production inconnus** : `heraliz_zombies-server.toml` n'est pas dans le dépôt (valeurs du code), `doMobSpawning` non vérifié en ligne [vérifié].

---

## 3. Interfaces : refonte proposée

### 3.1 Direction artistique commune (à valider en maquettes)
- **Un seul kit** dans le noyau, utilisé par tous les modules (fin des 23 copies) : verre, `placer` (texte calé au pixel), cache des textes, `Anim` (courbes), composant **anneau de progression** unique.
- **Palette par rôle** (une seule valeur par rôle) : danger `#E3201B` (rouge du logo), alerte `#E8B34A`, ok `#5FD08A`, info `#6FA8FF`, texte `#ECEEF2`, atténué `#8F95A0`, très atténué `#5A5F69`, verre `#0D0F14` à 80 %.
- **Typographie** : DM Sans (texte) + **Oswald** condensée (titres et chiffres « militaires », déjà dans `outils/polices`, licence OFL) ; tailles HUD fixes en vraies polices (6,5 / 8 / 11 / 16), **plus aucun `scale`**.
- **Grille d'ancrage** : 5 zones (haut-gauche, haut-centre, haut-droite, bas-droite, réticule) avec **empilement automatique** ; plus aucune cote en dur reprise d'un autre mod.
- **Animations** : apparition 180 ms (sortie cubique), disparition 250 ms, jauges interpolées entre deux ticks, pulsation lente pour l'urgence ; jamais de clignotement.

### 3.2 Les quatre interfaces
| Interface | Proposition | Ne change pas (décidé par le serveur) |
|---|---|---|
| **À terre** | bloc compact au bas-centre (≈ 220×56) : titre « À TERRE » en Oswald, sang restant en anneau ou barre fine interpolée, aide en une ligne ; vignette rouge plus légère ; plus de texte agrandi ; sortie en fondu ; écran moins masqué | saignement 30 s, réanimation 6 s, portée 3, abandon (1 s mini, refusé si réanimé), pause du chrono |
| **Soins** | anneau fin de 24 px autour du réticule, rempli en continu, temps restant au centre seulement si > 2 s, nom de l'objet en petit dessous ; aide « clic droit = arrêter » **pendant** l'action ; état « interrompu » (anneau rouge qui se vide 300 ms) ; **code neuf hors du paquet GPL** | durées, effets, recharge, refus en main gauche, arrêt en rappuyant |
| **Usure** | ancrée à la zone bas-droite commune, alignée sur la barre DayZ, largeur fixe ; anneau + pourcentage, couleur par état (ok / usé / critique / ruiné) ; transition douce entre états ; rien pour une arme sans usure | règles d'usure, seuils, réparations |
| **Réanimation** | même anneau que les soins, « Réanimation de PSEUDO », temps restant, état « interrompu » (bougé, touché) ou « réussi » (coche verte 600 ms) ; la progression attend la confirmation du serveur | durée 6 s, annulation si on bouge (1,5 bloc), si l'un est touché, si le réanimateur tombe |

Règle 6 du projet : ces écrans sont des interfaces importantes → **maquettes à valider avant le code**.

---

## 4. Pack de ressources et shaders : recommandation

**Pas de shaders pour l'instant** [estimé, justifié par le code] :

- Spartan les refuse (`spartan-common.toml:154-164`), et un joueur avec shaders verrait **à travers notre brouillard** (avantage en PvP) ;
- les 75 shaders de camos des armes, les autocollants, le train, le ping et des effets de mods tiers n'ont **aucune compatibilité Oculus** ;
- coût élevé sur les petits PC.

**À la place, un « look Day-Z » par les ressources et nos systèmes existants** :

1. **Terrain uniforme en 32 px** (herbe, hautes herbes, chemins, boue, mousse, grès, briques, chêne noir, eau terne) : +0,65 million de pixels seulement [calcul].
2. **Herbe crédible et légère** : 6 à 8 plans par touffe (12–16 faces au lieu de 33–85), textures 32–64 px avec transparence propre, verts ternes (colormaps) [estimé gain].
3. **Nettoyage** : retirer les 306 orphelines, les 160 doublons, réduire `snow.png` à 128×512 [calcul].
4. **Option petits PC** : textures HD des conteneurs de loot en 512 au lieu de 1024 (−7,9 millions de pixels) [calcul].
5. **Garder et étalonner** brouillard, ciel, filtre d'image (verts olive, vignette, grain) : c'est déjà notre identité.

Licences : le pack `herbe` est d'un autre auteur (crédit dans `pack.mcmeta`), le pack `textures` vient sans doute d'ArkaniaZ ;
garder leurs crédits ; refaire nous-mêmes ce qu'on remplace.

**Plus tard (phase 3, optionnel)** : un pack de shaders HeraliZ **autorisé** par Spartan, après compatibilité des camos et
tests sur petits PC, sans jamais l'imposer.

## 5. Réglages graphiques par niveau [estimé, à mesurer]

Réglages imposés une fois par PC (`ReglagesDefaut` version 3) + un choix « Performance / Équilibré / Qualité » dans le menu
Échap. La distance de vue reste plafonnée à 8 par le serveur.

| Réglage | Performance | **Équilibré (défaut)** | Qualité |
|---|---|---|---|
| Graphismes | Rapides | Détaillés | Détaillés |
| Distance de vue | 6 | 8 | 8 |
| Particules | Minimal | Réduit | Tout |
| Mipmaps | 2 | 4 | 4 |
| Nuages HeraliZ | non | **oui** (rallumés) | oui |
| Filtre d'image | non | oui | oui |
| Filtrage anisotrope des blocs | non | ×4 | ×8 |
| Textures HD du loot | 512 | 1024 | 1024 |
| Mélange des biomes | 0 | 1 | 2 |

Le brouillard n'est jamais coupé (ambiance et équité PvP). Côté serveur, rien ne change.

---

## 6. Cycle jour/nuit : correction proposée

**Pourquoi il n'y a pas de cycle** : `doDaylightCycle=false` et heure figée à midi [vérifié sur la copie de test, à vérifier en
ligne]. Remettre la règle à `true` donnerait le cycle de Minecraft (≈ 10 min de jour, 7 min de nuit), **pas** le 20 / 4
demandé : il faut une petite horloge maison.

**Conception** (module `monde`, une seule horloge) :

- la règle reste `false` ; l'horloge HeraliZ est la **seule** à écrire l'heure de la carte (overworld) ;
- jour (23460 → 12541) en **20 min réelles** : 0,545 tick Minecraft par tick ; nuit (12541 → 23460) en **4 min** : 2,275 ;
  bornes = celles de « nuit » de Minecraft, pour que zombies, quêtes et ciel soient d'accord [calcul] ; variante plus douce
  avec crépuscule et aube à vitesse normale ;
- position recalculée depuis l'heure sauvée → **persiste** après un redémarrage, sans fichier en plus ;
- côté joueur, un petit prédicteur (sans mixin) applique la même courbe pour que **le soleil ne saute pas** ;
- `/cycle info | jour | nuit | heure | pause | reprendre | durees | recharger` (staff) ; les boutons JOUR/NUIT du panel passent
  par `/cycle` ;
- monde du tutoriel (`crac:bases`) : suit la carte tout seul ; spawn (minuit fixe) et arènes ClashWar (midi fixe) : inchangés.

**Pièges** : `/time set` fait reculer la lune ; `/gamerule doDaylightCycle true` doublerait la vitesse (l'horloge la remet à
`false`) ; WorldGuard `time-lock` ; **`doMobSpawning` doit rester `false`** sinon de vraies nuits feraient apparaître monstres
et phantoms partout.

**Mesure prévue** : l'horloge note dans le journal chaque début de jour et de nuit avec l'heure réelle ; sur le serveur de
test, on vérifie 20 min et 4 min à la seconde près **[non testé]**.

---

## 7. Nouveau système de population des zombies

### 7.1 Principe : des zombies qui « habitent » le monde
- **Zones de population** (fichier rechargeable, sur la base des cercles de villes `zones.json`, des lieux de la carte M et
  des polygones de Décors) : chaque zone a une **population** (nombre de zombies « qui y vivent »), des **points
  d'apparition** préparés (rues, cours, intérieurs) et un **délai de réapparition**.
- **Trois états** distincts (réponse au « 1 500 ») :

  1. **enregistrés** : un simple compteur par zone, sans entité, sans coût (peut dépasser 1 500) ;
  2. **chargés** : entités réelles, seulement quand un joueur approche de la zone (≈ 96–128 blocs) ;
  3. **actifs** : IA complète seulement près des joueurs (niveaux ci-dessous).
- **Plus d'apparition « autour du joueur »** : une zone qui s'active fait apparaître sa population **hors de la vue**
  (test de ligne de vue, > 32 blocs, intérieur seulement si personne ne voit la porte) ; un zombie tué ne revient pas avant
  le délai de la zone ; une zone vidée reste plus calme ; quand plus personne n'est près, les entités repartent dans le
  compteur (survivants comptés, pas de recharge gratuite).
- **Extractions** : aucune apparition nouvelle à moins de 64 blocs d'une zone d'extraction ouverte ; les zombies déjà là et
  le bruit restent le danger.
- **Campagne** : pas de peuplement continu ; quelques errants autour de points isolés (fermes, maisons).

### 7.2 Densités de départ [à valider, puis à régler au banc]
| Type de zone | Population | Groupes | Réapparition | Particularités |
|---|---|---|---|---|
| Maison isolée | 0–1 | — | 15 min | une chance sur 3 d'un zombie à l'intérieur |
| Village | 6–12 | 2–3 | 8 min | marcheurs surtout |
| Ville (valeur 2–4) | 20–40 | 3–6 | 6 min | coureurs ~20 % |
| Commissariat | 4–8 | 2–4 | 10 min | **tenue police** (à peindre) |
| Militaire (aérodrome, base, prison) | 15–30 | 3–5 | 10 min | tenues militaires, 30 % de costauds |
| Très haute difficulté (labo, gaz, radiation) | 6–12 | 2–3 | 15 min | variantes rares, butin adapté |

### 7.3 Combat juste
- **3 attaquants au plus en même temps** sur un même joueur (« jetons d'attaque ») ; les autres tournent autour ou
  attendent : plus de mort en une seconde par 8 coups simultanés.
- **Attaques esquivables pour notre zombie** (geste lisible puis coup à l'impact, comme déjà fait pour Mutants & Zombies).
- Dégâts, PV, vitesses par variante dans le fichier de réglages ; un débutant survit aux marcheurs en jouant bien ; les
  costauds restent rares et localisés.

### 7.4 Bruit et sang, sans calcul lourd
- **Stimuli** déposés dans une grille par tronçon, avec une intensité et une durée : tirs (vraie portée TaCZ, silencieux
  réduits, branchement sur notre mixin des sons), explosions, terminal d'extraction, portes cassées, **sang** (joueur blessé
  ou qui saigne : une trace toutes les quelques secondes, pas à chaque tick).
- Chaque zombie chargé regarde **seulement les cases voisines**, une fois toutes les 1–2 s (étalé sur les ticks) ; un groupe
  partage la même destination et le même chemin.
- **Remplace Zombie Awareness** (à retirer avec CoroUtil après validation), puis le rampant maison remplace le Crawler de
  Mutants & Zombies.

### 7.5 IA et chemins
- **Niveaux** : < 24 blocs = IA complète (rythme de Minecraft) ; 24–64 = décisions toutes les 10 ticks, chemins courts ;
  > 64 = **endormi** (aucune IA, réveil par stimulus ou approche) ; zone non chargée = compteur.
- **File de chemins avec priorité** aux zombies proches des joueurs, budget par tick, chemins partagés par groupe.
- Une seule boucle de comptage par zone (au lieu de parcourir toutes les entités chaque seconde).

### 7.6 Visuel
- Variantes de silhouette (taille ±8 %, carrure) sur le modèle actuel ; nouvelles peaux : **police**, militaires abîmés,
  civils de ville / campagne ; script de peinture à refaire (l'ancien est perdu) ; sons propres (grognements, cris de
  repérage), fournis par le propriétaire ou fabriqués.
- Pas de nouveau moteur d'animation : gestes sur le modèle actuel (comme ceux déjà faits pour Mutants & Zombies).
- **Rendus à valider d'abord** (règle 6).

---

## 8. Benchmarks des zombies

**Aucun benchmark n'a été exécuté** pour ce rapport. Les chiffres existants (AUDIT_25OCT §5) mesurent des bots, pas des
zombies, sous Forge seul.

**Protocole proposé** (serveur de test local, jamais la production) :

1. ajouter une commande de banc `/zombies banc <n>` (pose N zombies dans des zones réelles) et suivre les listes des
   zombies dans `/diag fuites` ;
2. paliers **100 / 250 / 500 / 1 000 / 1 500** zombies, avec 0 puis 10 puis 20 bots qui bougent et tirent ;
3. pour chaque palier : ms par tick (moyenne, 95 %, 99 %), TPS, processeur, mémoire, chemins calculés / partagés / refusés,
   zombies par niveau d'IA ; 10 min par palier ;
4. **avec et sans Zombie Awareness** ;
5. sous Mohist si possible (activation range de Spigot), sinon Forge seul en le disant ;
6. ce PC est ~2 fois plus rapide par cœur que l'hébergeur : on vise ≤ 15 ms de moyenne ici pour espérer ≤ 30 ms en ligne
   [estimé].

### 8.1 Résultats du banc du 25/10 (soir) [mesuré]

Serveur de banc à part (`C:\hzb`, Forge 47.3.12 seul, sans Mohist ni plugins, monde `dayz_map`, jars de `livraison/` avec
le nouveau peuplement), à Marbois ; 10 bots qui courent et tirent sans arrêt (`/bots`), zombies posés par
`/zombies banc <n> 96` autour d'eux, puis `/diag start 60`. **Cas le pire** : tous les zombies au même endroit, tous
attirés par les tirs (les bruits les réveillent), donc presque tous au niveau d'IA « proche ». Pilote :
`banc_zombies.py` du chantier, journal `banc_zombies.log`.

| Zombies présents (après le palier) | ms par tick (moyenne des 5 relevés) | pire tick | IA : proches / moyens / loin / endormis | chemins refusés (budget) |
|---|---|---|---|---|
| 84 (bots seuls, déjà là) | 6,6 | 134 ms | 16 / 28 / 13 / 29 | 19 |
| 161 (+100) | 16,0 | 102 ms | 53 / 62 / 32 / 17 | 28 |
| 247 (+150) | 27,5 | 159 ms | 87 / 58 / 61 / 43 | 28 |
| 563 (+250) | 34,3 | 148 ms | 231 / 120 / 113 / 104 | 228 |
| 918 (+500) | 62,4 (trop : moins de 20 TPS) | 259 ms | 274 / 257 / 155 / 234 | 1 562 |
| 886 (+500, les bots en tuent) | 130,8 (≈ 7,6 TPS) | 304 ms | 448 / 123 / 131 / 184 | 5 010 |

- Pose : chaque palier posé en entier (« 0 non posés » ; 10 à 36 essais tombés sur une case sans sol, refaits).
- Bruits et jetons d'attaque marchent [mesuré] : 2 803 bruits, 7 586 zombies attirés ; 801 coups retenus (au plus
  3 attaquants par joueur).
- Coût d'un zombie en plus, réveillé : environ 0,06 ms par tick ici, donc ≈ 0,12 ms sur l'hébergeur [estimé].
- **Choix pour l'ouverture** : `max_total = 400` (au lieu de 1 000) ; le peuplement s'arrête aussi tout seul au-dessus de
  45 ms par tick (`tick_max_ms`). En jeu, les zombies sont répartis autour des joueurs (14 au plus à 48 blocs d'un joueur,
  2 à 4 par carré de 64 blocs), une bonne partie dort : moins cher que ce banc [estimé, non testé avec de vrais joueurs].
- **1 500 zombies réveillés en même temps : non** sur ce serveur [mesuré ici, ×2 sur l'hébergeur estimé].
- Pas fait : 20 bots, avec et sans Zombie Awareness, sous Mohist.

---

## 9. Modifications effectuées

Au moment de l'audit : aucune. Ensuite (25/10, soir, à la demande du propriétaire ; compilé, rien testé en jeu) :
- zombies (HeraliZ Utils) : peuplement par secteurs au hasard, jamais à la vue d'un joueur, moins près des extractions
  ouvertes (`Peuplement`, `ReservesZombies`), zones de zombies (`ZonesZombies`, tenue « police »), bruit des tirs, des
  explosions et du sang (`StimuliZombies`), 3 attaquants au plus par joueur (`JetonsAttaque`), IA endormie au-delà de
  64 blocs, `/zombies` (stats, réserves, banc, zones) ; réglages `serveur/defaultconfigs/heraliz_zombies-server.toml` ;
- cycle jour / nuit 20 min / 4 min (`cycle/`, `/cycle`, `heraliz_cycle-server.toml`) ;
- herbe allégée (pack terrain : 12 faces au lieu de 33, haute herbe 27 au lieu de 85), neige des intempéries, profils
  graphiques du menu Échap, nuages rallumés sur la carte seulement (HeraliZ Interface, `ReglagesDefaut` v3) ;
- interfaces (soins, à terre, réanimation, usure) : prêtes dans le chantier, **pas mises dans le jeu** tant que le
  propriétaire n'a pas validé les maquettes (`docs/HUD_REFONTE.md` du chantier).

## 10. Compatibilité et risques

- **Isolation** : ce PC a 15 Go de mémoire ; deux fabrications Gradle en même temps (4 Go chacune) + un jeu = trop. Les
  prototypes se feront dans une **copie de travail séparée** (dossier `HeraliZ-chantier` à côté du projet, sans `build/` ni
  `livraison/`), fabriquée seulement quand l'autre session ne fabrique pas ; on recopie ensuite les fichiers validés, sans
  toucher à ceux de l'autre session.
- **Code d'un autre auteur** : le cercle des soins est dans un paquet GPL (LR Tactical) → refonte en code neuf à part.
- **Usure** : ne plus s'appuyer sur les cotes du HUD de TaCZ.
- **Zombies** : retirer Zombie Awareness / Mutants & Zombies demande que leurs remplaçants soient prêts (bruit, rampant,
  vagues de capture, barème de saison, quêtes qui citent leurs identifiants).
- **Cycle** : plugins Bukkit qui touchent l'heure (WorldGuard `time-lock`) ; `/time set`.
- **Shaders** : voir §4.

## 11. Tests encore nécessaires sur de vrais clients

FPS à Valmont et en campagne avant/après (F3, `/diag client`) sur un petit PC et un PC moyen ; lisibilité des nouvelles
interfaces en 1080p et 1440p, échelles d'interface 2, 3, 4 ; soleil sans saut ; durée réelle du jour et de la nuit ;
ressenti des zombies (densité, fuite possible, extraction) ; nuages HeraliZ avec Embeddium.

## 12. Feuille de route priorisée

| Ordre | Chantier | Pourquoi | Taille |
|---|---|---|---|
| **1 — tout de suite** | **Correctif extraction** : aucune apparition à < 64 blocs d'une extraction ouverte, test de ligne de vue, plus d'apparition en intérieur près d'un joueur | bug vu à l'ouverture, petit, serveur seulement | petit |
| 2 | **Cycle 20 / 4 min** (horloge + prédicteur + `/cycle`) | demandé, isolé, mesurable sans client | petit |
| 3 | **Banc des zombies** (commande, suivi, protocole §8) | sans lui, aucun chiffre sur 1 500 | petit |
| 4 | **Maquettes** des 4 interfaces + kit commun dans le noyau | règle 6 : valider avant de coder | moyen |
| 5 | **Zone pilote** de population (une ville) + jetons d'attaque + stimuli bruit | cœur du nouveau PvE | moyen |
| 6 | Code des 4 interfaces validées, puis le reste des HUD | finition | moyen |
| 7 | Pack terrain 32 px + herbe légère + nettoyage + profils graphiques | identité visuelle | moyen |
| 8 | Extension des zones à toute la carte, retrait de Zombie Awareness, rampant maison, tenues police | fin de l'étape 4 | grand |
| 9 | (option) shaders autorisés | après mesures et compat | grand |

**Prototypes isolés proposés (étape C)** : 1 + 2 + 3 d'abord (serveur seulement, mesurables), puis les maquettes de
l'interface des soins et du HUD à terre.
