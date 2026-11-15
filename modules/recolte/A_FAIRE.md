# HeraliZ Récolte : cahier des charges (code à écrire)

Branche `recolte`. Ce dossier contient la **source graphique** du mod (modèles en cubes façon Blockbench, textures,
icônes, textes) ; **le code (commun, serveur, client) reste à écrire**, avec les règles du `CLAUDE.md` du dépôt
(séparation client / serveur, 3 langues, aucun nom perso, fabriquer et vérifier, jamais sur `main` sans accord).
Barème et décisions détaillées : `maquettes/recolte/PROPOSITION.md`. Rien n'a été testé en jeu.

## 1. Le principe (décisions du propriétaire)

- Quelques **arbres à abattre** (bouleau, épicéa) et **gisements** (pierre, métal, métal rare) apparaissent sur la carte,
  décidés par le serveur. Ce sont des **entités** dessinées avec nos modèles en cubes : aucun bloc posé ni cassé, aucune
  retouche des fichiers du monde. Les forêts de la carte restent des arbres en blocs de Minecraft.
- **Hache** (arbres), **pioche** (gisements), **hache-pioche de survie** légendaire (les deux, 40 % de coups en moins).
- On récolte des **matières brutes** (bûche, pierre brute, minerai de fer, minerai rare) échangées au spawn par le PNJ
  **« Le Contremaître »** contre les matériaux des bases de CRac. Cher, plafonné, pour ne pas avoir des bases tout en
  métal en une semaine.
- Les modèles doivent rester **en cubes** (choix du propriétaire, pour le moins de lag possible) et être **dessinés en
  un seul envoi** (modèle cuit), jamais un maillage lisse.

## 2. Ce qui est déjà là

| Chemin (dans `modules/recolte/`) | Contenu |
|---|---|
| `outils/recolte_blockbench.py` | **écrit** tous les modèles, textures, icônes (`--installer`, `--rendus DOSSIER`, `--seulement gisements\|arbres\|outils`) ; jamais rien à la main |
| `outils/recolte_cubes.py` | les 3 outils en cubes (`hache_cubes`, `pioche_cubes`, `legendaire_cubes`), matières, `POINTES` (bout qui frappe) |
| `outils/recolte_modeles.py`, `outils/recolte_moteur.py` | matières (pierre, minerais, écorces, bois de bout, feuillage peint), cuisson des textures, moteur des aperçus (éclairage des entités de Minecraft 1.20.1) |
| `blockbench/*.bbmodel` | les 17 modèles à ouvrir dans Blockbench (format « Generic Model », texture intégrée, groupes nommés) |
| `src/client/resources/assets/heraliz_recolte/modeles/*.json` | les mêmes modèles **cuits** pour le jeu (format en §4) |
| `src/client/resources/assets/heraliz_recolte/textures/entity/*.png` | texture de chaque modèle de la carte (gisements, arbres) |
| `src/client/resources/assets/heraliz_recolte/textures/item/*.png` | icônes 64 px : `hache`, `pioche`, `hache_pioche`, `buche`, `pierre_brute`, `minerai_fer`, `minerai_rare` ; `item/modele/*.png` : texture des modèles d'outils |
| `src/client/resources/assets/heraliz_recolte/lang/{fr_fr,en_us,ru_ru}.json` | noms des objets et entités, infobulles, messages de refus (à compléter) |
| `../../maquettes/recolte/` | proposition, barème, rendus validés par le propriétaire |

Modèles (17) :

| Modèle | Groupes | Remarque |
|---|---|---|
| `gisement_pierre_1` … `_4`, `gisement_metal_1` … `_4`, `gisement_rare_1` … `_4` | `gisement` (+ `cristaux` pour le métal rare) | un fichier par étape (1 = entier, 4 = presque fini) ; posé sur y = 0, centré |
| `arbre_bouleau` (9,2 m), `arbre_epicea` (12 m) | `souche` ; `tronc` (pivot = centre de la coupe, clé `pivots.tronc`) qui contient `coupe_0` … `coupe_3`, `fut`, `branches`, `feuillage` | `coupe_k` = le morceau de tronc juste au-dessus de la coupe, entaille de plus en plus profonde côté **+x** : n'en dessiner qu'un (0 au départ) ; le feuillage est en plans découpés (transparence) |
| `hache` (72 cm), `pioche` (86 cm), `hache_pioche` (80 cm) | `manche`, `tete` | manche vers +y depuis y = 0, tranchant vers +x ; bout qui frappe : `recolte_cubes.POINTES` / `POINTES_PIC` |

Coupe des arbres à **0,5 m** (`HAUTEUR_COUPE`).

## 3. Identifiants (figés dès la première mise en ligne)

- Mod `heraliz_recolte`, module `recolte` (paquet `fr.heraliz.recolte`), version 1.0.0.
- Objets : `heraliz_recolte:buche`, `pierre_brute`, `minerai_fer`, `minerai_rare` (piles de 32 ; minerai rare 16),
  `hache`, `pioche`, `hache_pioche` (pile de 1, usure : 400, 400, 1 000 coups).
- Entités : `heraliz_recolte:gisement`, `heraliz_recolte:arbre`.
- Raretés (`outils/loot_armes.py`, `RARETES_IMPOSEES`) : bûche et pierre brute Commun, minerai de fer Peu commun,
  minerai rare Rare, hache et pioche **Rare**, hache-pioche **Légendaire**.

## 4. Format des modèles cuits (`modeles/<nom>.json`)

```
{ "texture": "heraliz_recolte:textures/entity/<nom>.png", "taille_texture": [L, H], "unite": "metre",
  "pivots": { "tronc": [x, y, z] },
  "groupes": { "<groupe>": [ [x y z u v nx ny nz] x 4 sommets, ... ] } }
```

Positions en mètres (1 bloc), UV de 0 à 1 (v = 0 en haut de l'image), un quadrilatère par face, sommets dans le sens
direct vu de dehors (comme les faces de Minecraft). Les plans de feuillage ont leurs deux faces. Pour le jeu : lire le
fichier une fois (ressources du jar joueur), garder les quadrilatères par groupe dans un `float[]` (comme
`fr.heraliz.casino.client.render.mesh.Mesh`) et les envoyer avec `RenderType.entityCutoutNoCull(texture)` ; sommets avec
normale (la lumière des entités de Minecraft fait le reste). Les textures d'outils sont dans `textures/item/modele/`.

## 5. Code commun (`src/commun`)

- `@Mod` `HeralizRecolte` (modèle : `modules/evenements`), `Parties.serveur` / `Parties.client`, `LICENCE-HeraliZ.txt`,
  `mods.toml`, `pack.mcmeta`, module ajouté à `settings.gradle`, `build.gradle` avec `gradle/module-coupe.gradle`.
- Objets ci-dessus. Les outils : objet simple, usure, `IClientItemExtensions` branché par le client (Crochet).
- Entités (`EntityType`, catégorie MISC, pas d'IA, ne bougent pas, pas de gravité, `canBeCollidedWith` vrai pour qu'on
  ne les traverse pas, `isPickable` vrai pour qu'on les frappe ; enregistrement du monde : oui).
  Données synchronisées (`EntityDataAccessor` dans la classe commune) :
  - gisement : `TYPE` (0 pierre, 1 métal, 2 rare), `ETAPE` (0 à 3), `SECOUSSE` (heure de jeu du dernier coup, pour le
    tremblement chez le joueur) ; taille de la boîte selon type et étape ;
  - arbre : `ESPECE` (0 bouleau, 1 épicéa), `COUPS` (pour l'entaille : 0 à 3), `CHUTE` (heure de jeu du début de la
    chute, -1 = debout), `DIRECTION` (lacet de la chute), `SOUCHE` (le tronc est parti).
  Logique dans `GisementServeur` / `ArbreServeur` (serveur), créées par leur type, jamais `new X(...)`.
- Réseau : rien d'obligatoire (les coups passent par l'attaque de Minecraft, les effets par les particules et sons du
  serveur). Pour l'animation de l'outil vue des autres : la pose à deux bras (§7) suffit.

## 6. Code serveur (`src/serveur`)

**Réglages** : `serveur/config/heraliz_recolte/recolte.json` (dans le dépôt, relu par `/recolte recharger` et à chaud,
voir noyau « données vivantes ») avec le barème de `PROPOSITION.md` :

| Ressource | À la fois | Matière | Coups (outil / légendaire) | Réapparition |
|---|---|---|---|---|
| arbre (moitié bouleau, moitié épicéa) | 30 | 30 bûches (2 par coup + 6 à la chute) | 12 / 7 | 1 h 30 |
| pierre | 24 | 24 pierres brutes (un quart par étape) | 16 / 10 | 2 h |
| métal | 10 | 30 minerais de fer | 20 / 12 | 4 h |
| métal rare | 4 | 16 minerais rares | 24 / 14 | 8 h |

Plus : cadence 1 coup / 1,2 s par joueur (contrôlée par le serveur, comme la mêlée), écarts minimaux (arbres 50 blocs,
pierre 60, métal 150, rare 300), distance aux joueurs à l'apparition (48), liste des sols naturels, mondes permis
(overworld seulement).

**Coups** : un joueur frappe l'entité (attaque de Minecraft) ; le serveur vérifie l'outil (hache ou hache-pioche pour un
arbre ; pioche ou hache-pioche pour un gisement ; sinon message `heraliz_recolte.refus.*` au-dessus de la barre), la
portée (comme la mêlée : 3 blocs), la cadence, le mode de jeu (pas spectateur), use l'outil d'1, donne la matière dans
l'inventaire (au sol à ses pieds s'il est plein), particules et son :
- gisement : tremblement (`SECOUSSE`), éclats (particules d'objet de la pierre brute / du minerai, étincelles `CRIT` pour
  le métal), son de pierre ; à chaque quart, `ETAPE` + 1 (le modèle suivant), à la fin l'entité disparaît et la place
  est libérée (réapparition ailleurs après le délai) ;
- arbre : copeaux (particules d'objet de la bûche), quelques feuilles, son de bois, `COUPS` monte l'entaille (coupe_1 à
  coupe_3 aux tiers) ; au dernier coup, `CHUTE` = heure de jeu, `DIRECTION` = à l'opposé du joueur ; à l'impact
  (≈ 2,4 s) poussière et feuilles au sol (particules du serveur), son de chute ; 2 s plus tard, les 6 bûches de la chute
  et `SOUCHE` = vrai ; la souche reste 10 min puis l'entité disparaît. L'arbre qui tombe ne blesse personne.
- Points de saison (règle du serveur, `docs/EVENEMENTS.md`) : `SaisonApi.pve(joueur, "recolte_metal", 1)` à la fin d'un
  gisement de métal, `"recolte_rare"` (2 points) ; clés dans `serveur/config/heralisz_clans/saison.json` (section
  `pve`), ligne dans `SaisonVue.bareme()` et textes `gui.saison.l.*` du mod des clans ; 10 points par clan et par jour au
  plus. Rien pour le bois et la pierre.

**Apparition** (le plus important pour les perfs : ne jamais charger un tronçon exprès) :
- Une liste des places (données hors du monde ou `SavedData` de l'overworld : type, variante, position, état
  « attente » / « posée », UUID de l'entité, heure de réapparition). Il manque une ressource : une place tirée au hasard
  dans la bordure du monde (et, plus tard, dans une carte des places possibles calculée depuis la carte finie, comme
  `outils/zones_construction.py`).
- Une place « en attente » n'est contrôlée et posée que quand son tronçon est **déjà chargé** par un joueur et qu'aucun
  joueur n'est à moins de 48 blocs ; au plus une place contrôlée par tick.
- Contrôles (sinon : une autre place au hasard) : posé SUR un sol naturel (herbe, terre, pierre, sable, neige ; liste du
  réglage), toute l'emprise libre (air seulement, pas même une fleur : on ne remplace aucun bloc), à plat (1 bloc au
  plus d'écart), ciel visible (jamais dans un bâtiment ni une grotte), pas d'eau à 3 blocs, aucun bloc non naturel
  (route, mur, bâtiment) à 6 blocs autour, hors des villes (cercles de `config/heralisz_clans/zones.json`) et de leurs
  abords, hors des bases CRac (aucune entité de bloc `crac:` dans les tronçons voisins), hors des zones VIP
  (`heraliz_grades/zones_vip.json`), hors du spawn.
- Une entité enlevée autrement (/kill) libère sa place (`remove` avec une raison autre que le déchargement). Une entité
  sans place connue (copie, ancienne version) est retirée.
- Commandes du staff (op 2) : `/recolte info` (nombres, attentes), `placer <type>` (à l'endroit du staff),
  `retirer` (la plus proche), `recharger`, `vider`.

**PNJ « Le Contremaître »** (troc, `serveur/config/heraliz_quetes/pnj/contremaitre.json`, échanges **écrits** par
`outils/marchands.py` dans un nouveau tableau, comme le Bâtisseur) :
5 bûches → 1 planche ; 1 bûche → 5 brindilles ; 8 pierres brutes → 1 pierre taillée ; 10 minerais de fer → 1 fragment
de métal ; 8 minerais rares → 1 métal renforcé.
**Plafond par joueur et par jour** (heure de Paris) : 12 fragments de métal et 3 métal renforcé reçus par des échanges ;
à ajouter dans HeraliZ Quêtes (`modules/pnj/.../Echanges.java`, par exemple un fichier
`serveur/config/heraliz_quetes/plafonds_echanges.json` : objet reçu → nombre par jour ; compteurs hors du monde dans
`heraliz_quetes/`, refus clair « plafond du jour »). Le **Bâtisseur** vend la hache et la pioche **1 200 hérals**
(`MATERIAUX` de `outils/marchands.py`). Mila et le recycleur ne prennent pas les matières brutes (vérifier).

**Loot** (`outils/loot_armes.py`, puis relancer le script) : `EXTRAS` hache et pioche Rare (pour 100 conteneurs : T2 0,3 ;
T3 0,4 ; T4 0,5 ; T5 0,4 chacune), hache-pioche Légendaire (T6 0,05 ; T7 0,4 ; coffres des événements) ; jamais vendue
ni fabriquée. Ajouter `heraliz_recolte:hache_pioche` aux `trackedItems` de Spartan (anti-duplication).

## 7. Code client (`src/client`)

- **Rendu des entités** : modèle cuit (§4), une seule passe, `entityCutoutNoCull`, lumière de l'endroit, petite ombre
  ronde ; rien au-delà de 96 blocs (arbres) / 64 (gisements).
  - gisement : modèle de l'étape `ETAPE` ; 6 ticks de tremblement après `SECOUSSE` (petites rotations et décalages
    amortis) ; au changement d'étape, petit « pop » d'échelle.
  - arbre : `souche` toujours ; si pas `SOUCHE` : `coupe_<min(COUPS,3)>` + `fut` + `branches` + `feuillage`, tournés
    autour de `pivots.tronc` décalé vers le bord opposé à l'entaille, dans la direction `DIRECTION`, angle de la chute
    lent au début puis de plus en plus vite (≈ θ'' = 1,5 g / L · sin θ, partir de 2°, impact à 84° vers 2,4 s, petit
    rebond de 3°), puis le tronc s'enfonce dans le sol et s'efface en 2 s.
- **Outils en main** (demande du propriétaire, comme les armes) : à la première personne, **les deux mains du
  personnage** (son skin, `PlayerRenderer.renderRightHand/LeftHand` comme `TenueExplosifs` de CRac) tiennent le manche
  (main droite à 6 cm du bas, main gauche à 34 cm) ; vrai coup fluide d'environ 0,6 s : élan (hache au-dessus de
  l'épaule droite, pioche au-dessus de la tête), frappe rapide, impact (petite secousse de la vue), retour au repos.
  Poses de référence dans `outils/recolte_modeles.py` (`POSES_HACHE`, `POSES_PIOCHE`, `PRISES`) et images
  `maquettes/recolte/vue_joueur_*.png`. Clic maintenu = les coups s'enchaînent toutes les 1,2 s (la cadence reste
  décidée par le serveur). Icône : `"loader": "forge:separate_transforms"` (image `textures/item/<id>.png` dans
  l'inventaire, modèle en cubes en main).
- **Vu des autres** : `HumanoidModel.ArmPose` à deux mains (comme `TenueExplosifs.ARM_POSE`), le bras suit le geste du coup.
- Particules et sons de Minecraft en attendant de vrais sons.

## 8. Avant d'envoyer

`python outils/construire.py recolte pnj` puis `python outils/verifier.py recolte pnj` (0 problème), langues
(`outils/verifier_langues.py`), tests à faire en jeu dans `docs/TESTS_EN_JEU_<date>_recolte.md`, un commit par
changement, branche de travail, jamais `main` sans l'accord du propriétaire. Jars joueurs et serveur de la même
fabrication (nouveaux objets et entités).
