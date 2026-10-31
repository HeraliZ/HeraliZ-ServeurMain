# Essais en jeu du 31/10 : recycleur de la carte (Quêtes 1.16.0)

Rien de ce qui suit n'a été testé en jeu, ni compilé par Gradle ici (Minecraft et Forge ne se téléchargent pas sur cette
machine : seule la syntaxe Java a été contrôlée). La fabrication de GitHub (`branches.yml`) compilera tout.
**La branche ne part pas sur le serveur avant que le propriétaire ait validé les images du modèle.**

Remplace la section « Recycleur de la carte : rien en jeu » de `TESTS_EN_JEU_31OCT_hdv-recycleur.md` : le modèle est
maintenant installé et le bloc codé.

Jars joueurs et serveur de la même fabrication (nouveau bloc, nouvel objet, nouvel écran). Canal réseau des quêtes
inchangé (version 12 : aucun paquet n'a changé).

## Ce qui a changé pour le joueur

- Nouveau bloc **Recycleur de terrain** (`heraliz_quetes:recycleur_carte`, bloc et objet ; case invisible
  `heraliz_quetes:recycleur_carte_partie`). Même machine que celui du spawn, 2 blocs sur 1, en version de terrain (peinture
  sable, rebords orange, bandes rouges et blanches, gyrophare, antenne, jerrican à l'arrière, plaque « R-02 »).
- Mêmes règles et même écran que le recycleur du spawn, avec **10 % de chances en plus** (relatif) : chaque pièce 11 % au
  lieu de 10 %, au plus 2 pièces par objet, 3 s par objet, mêmes hérals « rien » (1 à 4).
- Permis **en combat et sur la carte** ; refusé en match, à terre et au tutoriel des bases. Aucun point de saison.
- **Contenu à la machine, partagé comme un coffre** : tout le monde voit et prend tout ; le recyclage continue quand on
  part ou qu'on meurt. Les hérals d'un objet qui n'a rien rendu vont à celui qui l'a déposé (même hors ligne) ; la quête
  du jour « recycler » avance pour lui s'il est connecté.

## À essayer (staff, créatif puis survie)

**Pose et retrait**
- [ ] Onglet créatif « HeraliZ · Quêtes » : « Recycleur de terrain » à côté du recycleur. Posé face au joueur, 2 blocs de
      large (la case de droite est à droite vu de face), dans les 4 directions. Refusé s'il n'y a pas la place à droite.
- [ ] Le modèle ressemble aux images validées ; à l'arrêt, voyants et gyrophare éteints.
- [ ] En survie : impossible à casser (ni à la main, ni à l'explosion), ni par la case de droite.
- [ ] En créatif, casser l'une ou l'autre case : les deux partent, **tout le contenu tombe au sol** (entrée et sortie).
      Reposer un recycleur au même endroit : il est vide.

**Écran**
- [ ] Clic droit (sur l'une ou l'autre case) : même écran que celui du spawn, titre « Recycleur de terrain », sous-titre
      « Machine partagée… », pastille verte « +10 % de chances », aide d'Échap « le contenu reste dans la machine », note
      « Seuls les objets qui se recyclent entrent ». Rien ne déborde en français, en anglais et en russe.
- [ ] Aperçu d'un objet : les chances affichées sont celles de la carte (1 chance sur 9 au lieu de 1 sur 10 pour un objet
      neuf).
- [ ] Refus : en match, à terre, au tutoriel → message rouge, rien ne s'ouvre. **En combat : il s'ouvre.** Sur la carte :
      il s'ouvre. Passer à terre écran ouvert : il se ferme (dans la demi-seconde).
- [ ] S'éloigner de plus de 8 blocs : l'écran se ferme ; le contenu reste dans la machine.
- [ ] Posé dans une région WorldGuard de la carte : le clic droit d'un joueur ouvre bien l'écran (sinon, autoriser
      l'usage des blocs dans cette région).

**Entrées : jamais une cachette**
- [ ] Un objet qui ne se recycle pas (composant, capsule, clé, objet acheté trop rentable, objet à vie, de quête, de match,
      sac plein) n'entre pas dans les cases de gauche (ni au clic, ni Maj + clic, ni en glissant, ni avec les touches
      1 à 9).
- [ ] Les cases « récupéré » ne prennent rien, on ne peut que les vider.

**Machine partagée**
- [ ] Deux joueurs ouvrent la même machine : ce que l'un dépose ou prend apparaît tout de suite chez l'autre ; la barre
      avance pareil chez les deux.
- [ ] Deux joueurs cliquent en même temps sur la même case « récupéré » : un seul obtient l'objet.
- [ ] Déposer, fermer, partir loin (tronçon déchargé), revenir après 1 min : ce qui a été déposé a été recyclé pendant
      l'absence (rattrapage, 20 min de jeu au plus).
- [ ] Déposer puis mourir / se déconnecter : la machine continue ; un autre joueur peut tout reprendre (pillage).
- [ ] Le déposant reçoit les hérals « rien » (journal du compte : motif « Recycleur de la carte »), même déconnecté ; un
      autre joueur qui regarde ne les reçoit pas.
- [ ] Sortie pleine (moins de 2 cases libres) : la machine s'arrête (voyants éteints), « Sortie pleine » ; on vide une
      case, elle repart toute seule.
- [ ] Machine vide ou sans rien de recyclable : voyants et gyrophare éteints, pas de fumée ; en marche : voyants vert et
      ambre et gyrophare allumés, un peu de lumière, fumée au pot d'échappement, son à chaque objet fini pour ceux qui
      sont autour.
- [ ] Touches 1 à 9 / F entre une case pleine de la machine et une case pleine de l'inventaire : refusé (l'objet revient
      à sa place). Case vide d'un côté : marche.

**Duplication (serveur d'essai seulement)**
- [ ] Déposer un objet puis couper le serveur brutalement (kill du processus) juste après : au retour, l'objet est soit
      dans l'inventaire, soit dans la machine (ou perdu si la coupure tombe entre les deux écritures), **jamais aux deux**.
- [ ] Pareil en reprenant un objet de la machine juste avant la coupure.
- [ ] Fichiers : `heraliz_quetes/recycleurs/<monde>/<x>_<y>_<z>.dat` à côté du dossier du monde, effacé quand la machine est
      vide ou cassée. Un fichier abîmé à la main : la machine dit « hors service », le fichier n'est pas écrasé (journal du
      serveur).
- [ ] `/quete recharger` après avoir changé `recyclage.json` : les machines refont leurs verdicts.
- [ ] `/wipe tout` : toutes les machines vidées (chargées ou non), dossier `heraliz_quetes/recycleurs/` effacé.

**Perf**
- [ ] `/diag mesure` avec 10 recycleurs de terrain vides posés : rien pour eux (pas de ticker à l'arrêt).

**Non-régression du recycleur du spawn** : mêmes chances (10 %), même écran (titre « Recycleur », aide « ce qui reste
t'est rendu »), refus en combat et sur la carte, objets rendus à la fermeture, résumé dans le chat.

## Réglages

- Chance de la carte : `profils.carte.chance` de `config/heraliz_quetes/recyclage.json`, écrit par
  `python outils/recyclage.py` (`BONUS_CARTE`), relu par `/quete recharger`. Contrôle :
  `python outils/marchands.py --controle` (les deux profils : au plus 32,9 % du prix d'achat au spawn, 36,0 % sur la
  carte, borne 50 %).
- Modèle : `python modules/pnj/outils/recycleur_carte_modele.py --apercu DOSSIER` (rendus) / `--installer`.
