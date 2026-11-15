# Récolte façon Rust : proposition à valider

Date : 15/11. Rien n'est codé dans un mod : ce dossier contient le barème et les rendus à valider (règle 6). Les rendus
sont faits hors du jeu, avec l'éclairage des entités de Minecraft 1.20.1 et l'herbe du pack de la carte en île : c'est ce
que le jeu dessinera (à l'anticrénelage près). **Rien n'a été testé en jeu.**

Modèles et images écrits par `python modules/recolte/outils/recolte_modeles.py --apercu DOSSIER` (rien à la main).

## 1. Le principe

- Sur la carte, quelques **arbres à abattre** (bouleau, épicéa) et **gisements** (pierre, métal, métal rare) en modèles
  3D. Les forêts restent des arbres en blocs de Minecraft : seuls ces arbres-là se coupent.
- La **hache** abat les arbres, la **pioche** casse les gisements. Aucun bloc du terrain n'est jamais cassé.
- On récolte des **matières brutes** (bûches, pierres brutes, minerai de fer, minerai rare), qu'un **PNJ du spawn**
  échange contre les matériaux des bases (planches, pierres taillées, fragments de métal, métal renforcé). Cher.
- Un gisement vidé disparaît et **réapparaît ailleurs** plus tard ; un arbre abattu laisse une souche, puis repousse ailleurs.

## 2. Le barème proposé

Chiffres calculés pour une île d'environ 5 km² de terre (à recaler sur la carte finie : ce sont des réglages dans un
fichier, pas dans le code).

| Ressource | Sur la carte à la fois | Donne | Récolte | Revient ailleurs après |
|---|---|---|---|---|
| Arbre à abattre (bouleau, épicéa) | 30 | 30 bûches | 12 coups de hache (≈ 15 s) + 3 s de chute | 1 h 30 |
| Gisement de pierre | 24 | 24 pierres brutes | 16 coups de pioche (≈ 20 s) | 2 h |
| Gisement de métal | 10 | 30 minerais de fer | 20 coups de pioche (≈ 25 s) | 4 h |
| Gisement de métal rare | 4 | 16 minerais rares | 24 coups de pioche (≈ 30 s) | 8 h |

- Un coup toutes les 1,2 s, comme la mêlée. Gisement : un quart de la matière à chaque étape (4 étapes). Arbre :
  2 bûches par coup, 6 de plus quand il tombe.
- Matières brutes : piles de 32 (minerai rare : 16), elles prennent de la place et restent sur le corps à la mort.

### Les échanges au spawn (PNJ « troc »)

| Le joueur donne | Il reçoit | Prix du même objet chez le Bâtisseur |
|---|---|---|
| 5 bûches | 1 planche | 50 hérals |
| 1 bûche | 5 brindilles | 10 hérals |
| 8 pierres brutes | 1 pierre taillée | 100 hérals |
| 10 minerais de fer | 1 fragment de métal | 250 hérals |
| 8 minerais rares | 1 métal renforcé | 400 hérals |

Un arbre vaut donc 6 planches (300 hérals), un gisement de pierre 3 pierres taillées (300), un gisement de métal
3 fragments (750), un gisement de métal rare 2 métal renforcé (800).

**Plafond par joueur et par jour (conseillé)** : 12 fragments de métal et 3 métal renforcé. Rien pour les planches et
les pierres. Il faut ajouter ce plafond aux échanges des marchands (petit ajout dans HeraliZ Quêtes).

## 3. Pourquoi ça ne déséquilibre pas

- **Une heure de récolte ≈ une heure de fouille d'une ville moyenne.** Un joueur seul, recherche comprise : ≈ 8 arbres
  (48 planches, 2 400 hérals de matériaux), ou ≈ 6 gisements de pierre (18 pierres taillées, 1 800), ou 2 à 3 gisements
  de métal (6 à 9 fragments, 1 500 à 2 250). La fouille des conteneurs rapporte ≈ 2 300 hérals par heure en ville
  moyenne et ≈ 12 500 dans les zones T5-T6 (simulation `outils/loot_zombies.py --simuler`).
- **Tout le serveur est plafonné**, quel que soit le nombre de joueurs : au plus, par jour, 2 880 planches, 864 pierres
  taillées, **180 fragments** et **24 métal renforcé** (si tout est récolté dès que ça réapparaît, 24 h sur 24). En
  vrai, plutôt 40 % : ≈ 1 150 / 350 / **70** / **10**.
- **Le métal reste rare.** Passer une pièce en métal coûte 5 fragments + 5 équerres (les équerres ne se trouvent qu'en
  fouillant), et l'armoire mange 1 fragment par pièce de métal et par jour. Les ≈ 70 fragments du serveur entier ne
  paient chaque jour que l'entretien de 70 pièces en métal, pour tout le monde.
- **Exemple : base de 40 pièces tout en métal** = 200 fragments + 200 équerres, puis 40 fragments par jour. Même si un
  clan de 4 prenait TOUT le métal du serveur (plafond : 48 fragments par jour), il lui faudrait ≈ 5 jours sans partage,
  et les 200 équerres. Partagé entre une dizaine de clans (≈ 7 fragments par jour chacun), la récolte seule ne paie
  même pas l'entretien d'une dizaine de pièces en métal.
- **Pas d'argent en plus** : les matières brutes ne se revendent pas (ni Mila, ni le recycleur) et un objet reçu par
  un échange n'est jamais racheté ni recyclé (règle déjà en place). La récolte fait des matériaux, pas des hérals.
- **La moitié de chaque amélioration reste dans le loot** : clous, sacs de ciment, équerres, fil à souder.

## 4. Les outils (choix du propriétaire du 15/11)

| Outil | Prix (Bâtisseur) | Solidité | Rareté | Au combat |
|---|---|---|---|---|
| Hache de bûcheron (72 cm) | 1 200 hérals | 400 coups (≈ 33 arbres) | **Rare** (bleu) | faible : 2 cœurs, 1,2 s |
| Pioche (86 cm) | 1 200 hérals | 400 coups (≈ 25 gisements de pierre) | **Rare** (bleu) | faible : 2 cœurs, 1,2 s |
| Hache-pioche de survie (80 cm) | pas vendue | 1 000 coups | **Légendaire** (or) | faible : 2 cœurs, 1,2 s |

- Hache et pioche aussi **dans le loot** : pour 100 conteneurs, environ 0,3 en T2, 0,4 en T3, 0,5 en T4, 0,4 en T5
  (chacune). Usée : elle casse. L'usure coûte environ 36 hérals par arbre et 48 par gisement de pierre (une petite part
  de ce qu'ils rapportent).
- **Hache-pioche de survie (légendaire)** : un seul objet pour le bois ET la pierre (lame de hache d'un côté, pic de
  l'autre), et **40 % de coups en moins** : arbre 7 coups (au lieu de 12), pierre 10 (16), métal 12 (20), métal rare 14
  (24). Même matière récoltée, même plafond par jour : elle fait gagner du temps, pas plus de ressources. Ni vendue
  (pas de pay2win), ni fabriquée (règle des crafts : rien de Légendaire) : seulement dans le loot T6 (0,05 pour 100
  conteneurs) et T7 (0,4 pour 100), et dans les coffres des événements.
- **Modèles en cubes, comme les armes** (Blockbench, format Bedrock de GeckoLib) : 35 à 52 cubes, moins de
  650 triangles par outil (la première version lisse en avait environ 15 fois plus), texture de 512 points de large.
  **Le serveur ne dessine jamais un modèle** : pour lui un outil n'est qu'un objet et son usure ; aucun modèle ne peut
  faire ramer le serveur. Les cubes allègent le travail des PC des joueurs.

## 5. Où ça apparaît (décidé par le serveur)

- Posé **sur** un sol naturel (herbe, terre, pierre, sable, neige), à plat, à ciel ouvert : jamais dans un bâtiment ni
  une grotte. **Aucun bloc remplacé** : l'emplacement doit être libre (pas même une fleur), sinon on cherche ailleurs.
- Jamais sur une route ni à moins de 25 blocs d'une route, d'une voie ferrée ou d'un bâtiment ; jamais dans l'eau ni à
  moins de 3 blocs ; jamais dans une base CRac (zone de l'armoire + 16 blocs), au spawn, dans une zone VIP, une zone
  d'extraction, près d'un drapeau de territoire ni dans une salle à carte.
- Jamais à moins de 48 blocs d'un joueur au moment où il apparaît (personne ne le voit surgir). Écart entre deux
  ressources du même type : arbres 50 blocs, pierre 60, métal 150, métal rare 300. Le métal rare de préférence en
  hauteur et loin des côtes.
- Aucun lag : le serveur ne charge jamais un coin de carte exprès. Il garde la liste des places ; la ressource apparaît
  quand le coin est chargé par un joueur, après un dernier contrôle (si une base a été construite là entre-temps, elle
  part ailleurs). Une carte des coins possibles est calculée une fois depuis la carte finie (comme les zones de
  construction).

## 6. Ce qu'on voit et entend

- **Gisement** : à chaque coup il tremble, lâche des éclats (pierre grise ; métal : écailles de rouille et petites
  étincelles ; métal rare : éclats bleu acier), son selon la matière. Chaque quart récolté arrache un morceau : il
  rétrécit (4 étapes, cassures fraîches plus claires), puis disparaît.
- **Arbre** : copeaux et écorce à chaque coup, quelques feuilles tombent, l'entaille se creuse. Au dernier coup : il
  craque, penche, tombe en ≈ 3 s (lent au début puis de plus en plus vite), rebondit ; poussière et feuilles à
  l'impact. Le tronc s'efface en 2 s (les dernières bûches sont données), la souche reste 10 min.
- L'arbre qui tombe ne blesse personne.

## 6 bis. L'outil en main (demande du propriétaire du 15/11)

- Comme les armes : à la première personne, on voit **les deux mains du personnage** (son skin du créateur `/perso`)
  qui tiennent le manche, main droite en bas, main gauche plus haut. Jamais un outil qui flotte sans main.
- **Un vrai coup, fluide** (≈ 0,6 s) : élan (la hache monte au-dessus de l'épaule droite, la pioche au-dessus de la
  tête), frappe rapide, impact (petite secousse de la vue, copeaux ou éclats au point touché, son), retour en douceur au
  repos. Une frappe toutes les 1,2 s ; clic maintenu = les coups s'enchaînent. Coup dans le vide : même geste, sans
  impact.
- Les autres joueurs voient le même geste à deux mains (vue à la troisième personne, comme la mêlée et les emotes).
- Animation du coup faite comme celles des armes (animation Blockbench de GeckoLib : élan, frappe, impact, retour),
  les bras du personnage accrochés aux deux prises du manche ; le mouvement exact se règle en jeu. Les images `vue_joueur_*.png` montrent 4 moments du coup ; en jeu, le
  mouvement est continu entre eux.

## 7. À décider

1. Le barème (section 2) : d'accord, ou des chiffres à changer ?
2. Le plafond par jour (12 fragments, 3 métal renforcé par joueur) : oui ou non ?
3. Points de saison (règle du serveur : toute activité rapporte des points) : proposition 1 point de saison PVE par
   gisement de métal fini, 2 par métal rare, 10 par clan et par jour au plus ; rien pour le bois et la pierre.
4. Hache-pioche légendaire : seulement loot T6-T7 et coffres des événements, d'accord ?
5. Les rendus : 3 gisements, 2 arbres, la chute, les 3 outils en cubes, l'outil en main (deux mains, le coup).

## 8. Après validation (pas commencé)

Nouveau module `recolte` (mod `heraliz_recolte`) : outils tenus à deux mains avec leur coup animé (vue du joueur et des autres), gisements et arbres en entités (le serveur décide des coups, de la
matière, des étapes et des places ; le joueur n'a que l'affichage, maillages cuits dessinés en un seul envoi), objets
(bûche, pierre brute, minerai de fer, minerai rare, hache, pioche, hache-pioche de survie ; modèles et animations en
cubes Bedrock pour GeckoLib, écrits par l'outil comme les autres modèles), réglages dans
`serveur/config/heraliz_recolte/recolte.json` (relus sans redémarrer), carte des places par
`outils/zones_recolte.py`, PNJ échangeur écrit par `outils/marchands.py`, plafond par jour dans les échanges de
HeraliZ Quêtes, textes en français, anglais et russe, essais à faire en jeu dans `docs/TESTS_EN_JEU_*.md`.

## Les images

Depuis le 15/11 (choix du propriétaire), tout est en cubes façon Blockbench ; les premiers rendus en maillage lisse sont
retirés. Le code du mod est confié à une autre session : cahier des charges dans `modules/recolte/A_FAIRE.md`.

| Fichier | Contenu |
|---|---|
| `cubes_gisement_pierre.png`, `cubes_gisement_metal.png`, `cubes_gisement_rare.png` | les 3 gisements en cubes, leurs 4 étapes, à côté d'un joueur (1,8 m) |
| `cubes_arbre_bouleau.png`, `cubes_arbre_epicea.png` | les 2 arbres en cubes, à côté d'un arbre en blocs de la carte et d'un joueur |
| `cubes_souche_bouleau.png`, `cubes_souche_epicea.png` | l'entaille (4e état) et la souche |
| `cubes_chute.png` | la chute du bouleau |
| `cubes_icones.png` | les icônes des 7 objets |
| `outil_hache.png`, `outil_pioche.png`, `outil_legendaire.png` | les 3 outils en cubes, sous plusieurs angles |
| `vue_joueur_hache.png`, `vue_joueur_pioche.png`, `vue_joueur_legendaire.png` | l'outil en main, vue du joueur : le coup |
