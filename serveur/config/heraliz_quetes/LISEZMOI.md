# HeraliZ Quêtes : guide du staff

Ce dossier est copié sur le Ptero par la mise en ligne (`config/heraliz_quetes/`). **Le dépôt fait foi** : une
modification faite à la main sur le serveur est écrasée au prochain envoi. Après un changement : `/quete recharger` en
jeu (pas besoin de redémarrer).

- `pnj/<id>.json` : un fichier par PNJ (son nom, ses phrases, sa suite de quêtes). Depuis le 18/10, un seul PNJ :
  le **Commandant Varga** (`varga.json`, 39 quêtes principales en 5 chapitres : Recrue, Soldat, Caporal, Sergent,
  Officier ; scénario dans `docs/QUETES_SCENARIO.md`). Les 7 autres PNJ ont été retirés.
- La progression des joueurs n'est PAS ici : elle est dans `heraliz_quetes/joueurs/` à la racine du serveur (hors du
  monde, elle survit aux changements de carte).
- Les points de quête posés en jeu sont gardés avec le monde (`data/heraliz_quetes_points.dat`).

## 0. Les marchands

Quatre PNJ sont des marchands (clé `marchand` de leur fichier, **écrite** par `python outils/marchands.py`, jamais à la
main : prix et listes en tête du script) : **Mila** rachète les objets de valeur (Commun 20 → Mythique 1 500 hérals) et
les composants (moitié prix) ; **Jacqueline** vend tous les soins ; **le Doyen** vend 6 aliments en bon état ; **Jack**
vend les accessoires d'armes Commun à Rare. On leur parle, puis « Voir ce que tu vends » / « Te vendre des objets ».
Les poser : `/pnj creer mila`, `/pnj creer jacqueline`, `/pnj creer doyen`, `/pnj creer jack`.

### Créer son propre marchand, en jeu (sans fichier)

Réservé aux op (niveau 4). Tout se fait en jeu, rien à écrire dans un fichier.

1. **Se placer** là où le marchand doit se tenir (au spawn), tourné dans la direction où il doit regarder.
2. **Le créer** : `/marchand nouveau <id> <rôle> <nom affiché>`, par exemple
   `/marchand nouveau armurier_nord vendeur Viktor l'armurier`.
   - `<id>` : un mot court, en minuscules, chiffres ou `_` (2 à 32 caractères), qui ne sert qu'aux commandes ;
   - `<rôle>` : `vendeur` (il vend contre des hérals), `racheteur` (il achète aux joueurs contre des hérals) ou
     `troc` (il ne fait que des échanges d'objets, sans hérals) ;
   - `<nom affiché>` : le nom que voient les joueurs (32 caractères au plus).
   Le marchand apparaît à vos pieds et son éditeur s'ouvre tout de suite.
3. **Lui donner une tête** : faites votre personnage dans `/perso`, puis `/pnj apparence <id> moi` (il prend votre
   apparence ; `aucune` pour revenir à Steve / Alex).
4. **Régler ce qu'il propose** : prendre le Registre du marchand (`/marchand registre`) et faire un clic droit sur lui
   (ou `/marchand editer <id>`). En bas de l'éditeur, votre barre d'objets : on clique un objet, on met un prix ou des
   quantités, puis « Ajouter ».
   - un vendeur, onglet « Ce qu'il vend » : objet + prix en hérals ;
   - un racheteur, onglet « Ce qu'il rachète » : objet + prix (le même pour toutes les raretés, modifiable ensuite
     rareté par rareté ; 0 = il ne prend pas cette rareté) ;
   - onglet « Échanges » : **clic gauche** sur l'objet que le joueur DONNE, **clic droit** sur l'objet qu'il REÇOIT,
     les deux quantités (1 à 576), puis « Ajouter ». Exemple : 10 ferrailles contre 1 kit de réparation. Tout
     marchand peut avoir des échanges, même un vendeur ou un racheteur (les joueurs voient alors deux onglets).
   - chaque ligne : ▲ pour la monter, ✕ pour la retirer, changer le chiffre puis « OK » (ou Entrée).
5. **Autres commandes** : `/marchand nom <id> <nouveau nom>`, `/marchand role <id> <vendeur|racheteur|troc>` (ses
   listes sont gardées), `/pnj deplacer <id>`, `/pnj creer <id>` (en poser un deuxième).
6. **Le supprimer** : bouton « Supprimer ce marchand » de l'éditeur (cliquer deux fois), ou
   `/marchand supprimer <id>` puis `/marchand supprimer <id> confirmer`. Ses PNJ disparaissent du monde (ceux d'un
   coin du monde que personne n'a chargé disparaissent à son chargement).

Ce qui est gardé : à la racine du serveur, dans `heraliz_quetes/pnj_jeu.json` (nom et rôle) et
`heraliz_quetes/marchands_jeu.json` (ce qu'il vend, rachète, échange). Ces fichiers ne sont **jamais** écrasés par la
mise en ligne et survivent aux redémarrages et aux changements de carte. Les marchands des fichiers (Mila, Jack...) se
modifient aussi au registre ; on ne peut pas les supprimer en jeu (« Revenir au fichier » annule leurs changements).

Règles décidées par le serveur (pour éviter la triche) :
- pas de commerce en match, en combat, à terre, pendant le tutoriel des bases, ni sur la carte : seulement au spawn ;
- un échange prend des objets **en bon état** (un objet abîmé ne compte pas) et qui ont au moins les mêmes données
  que l'objet de l'échange ; s'il n'y a pas la place pour ce que le joueur reçoit, rien n'est échangé ;
- jamais d'objets à vie (couteaux, skins à vie), d'objets prêtés par un match, d'armes à camo à vie ni d'objets de
  quête, ni dans un échange, ni dans une vente, ni dans un rachat ;
- pas d'argent infini (règle du 25/10, pour toujours) : un objet qu'un marchand vend au prix P (fichier ou Registre) est
  racheté **au plus la moitié de P** (le prix de la règle de rareté s'il est plus bas ; plus petit P si plusieurs
  marchands le vendent), et le recycleur refuse de le recycler si, en moyenne, ça rapporterait plus que cette moitié ;
  un objet **reçu par un échange** n'est jamais racheté ni recyclé, et ne peut pas être donné dans un autre échange (pas
  de chaînes ni de boucles : l'éditeur refuse l'échange et dit pourquoi). Contrôle des fichiers :
  `python outils/marchands.py --controle`.

## 1. Poser un PNJ

Se placer à l'endroit voulu, regarder dans la direction où le PNJ doit regarder, puis :

| Commande | Effet |
|---|---|
| `/pnj creer <id>` | pose le PNJ (son `id` = le nom de son fichier : `varga`) |
| `/pnj deplacer <id>` | le déplace là où l'on est |
| `/pnj retirer <id>` | l'enlève |
| `/pnj skin <id> <skin>` | skin = fichier `textures/pnj/<skin>.png` du jar joueur (`defaut` : Steve / Alex), utilisé seulement sans apparence CaraCrea |
| `/pnj apparence <id> moi\|fichier\|aucune` | apparence du créateur de personnage (voir dessous) |
| `/pnj bras <id> fins\|larges` | modèle Alex ou Steve |
| `/pnj liste` | les PNJ des fichiers, et combien sont posés |

**Le Commandant Varga accueille les nouveaux joueurs** (son `id` doit rester `varga`) : après la cinématique, le
suivi à droite leur dit d'aller le voir ; tant qu'ils n'ont pas fait le tutoriel des bases, il ne leur propose que
sa première mission et « Infos sur les bases » (fiche /base info). Le tutoriel des bases vient à la création d'un clan (CRac).
Ses textes d'accueil et de radio sont dans les langues du mod (`outils/langues.py`, clés `heraliz_quetes.tuto.*`), pas
dans `varga.json`. Le poser au spawn avant d'ouvrir le serveur : sans lui, les nouveaux ne peuvent pas commencer.

### Le Guide et sa visite du spawn

Le **Guide** (`/pnj creer guide`) accueille les nouveaux après la cinématique : une traînée verte au sol et un contour
vert les mènent à lui (seulement chez eux). « Fais-moi visiter » : la caméra montre Mila, le Bâtisseur, Jacqueline, le
Doyen, Jack, puis les **points de la visite**, puis le Commandant Varga (Espace maintenu 1 s pour passer). Ensuite la
traînée et le contour passent sur Varga, jusqu'à ce que le joueur lui parle.

Les points de la visite se posent EN JEU (op niveau 2) : se placer là où la caméra doit se tenir, **regarder la chose à
montrer** (le bloc visé, jusqu'à 24 blocs), puis taper la commande. La caméra tourne un peu autour, de ce côté-là.

| Commande | Point |
|---|---|
| `/guide point casino` | le casino (capsules) |
| `/guide point armurier` | la Table d'armurier |
| `/guide point coffre` | le coffre « Safe Planque 1 » |
| `/guide point caisses` | les caisses |
| `/guide point borne` | la borne de départ |
| `/guide point recycleur` | le recycleur (sans point : le PNJ `recycleur` s'il est posé) |
| `/guide points` · `/guide retirer <id>` | liste · effacer |
| `/guide visite` | jouer la visite pour soi (essai) |

Un point non posé : le Guide dit sa phrase sans caméra. Gardés à la racine du serveur
(`heraliz_quetes/guide_points.json`), jamais écrasés par la mise en ligne.

### Questions fréquentes (bouton « Questions »)

Clé `faq` du fichier d'un PNJ (aujourd'hui Varga) : `[{"question": {"fr", "en", "ru"}, "reponse": {"fr", "en", "ru"}}]`.
Le bouton apparaît dès qu'il y a une question ; 7 questions par page. Relu par `/quete recharger`.

### Apparence des PNJ : le créateur de personnage (CaraCrea)

Chaque PNJ a une apparence faite avec le créateur de personnage du serveur (le même que `/perso`), écrite dans son
fichier, clé `"apparence"` : `{"skin_tone": "tan", "hair_style": "buzz", "facial_hair": "stubble", "shirt_style":
"tank_top", "shirt_color": "olive", "marks": ["scar_brow"], "wear": "dirty"...}`. Les noms des choix sont ceux du
créateur (teint, yeux, sourcils, nez, bouche, cheveux, barbe, haut, bas, chaussures et leurs couleurs, marques, usure ;
liste complète : `mods/caracrea/src/commun/java/com/caracrea/character/Catalog.java`). Le PNJ suit son fichier tout
seul (après `/quete recharger`).

| Commande | Effet |
|---|---|
| `/pnj apparence <id> moi` | le PNJ prend TON personnage (fais-le d'abord dans `/perso`) ; le chat donne ses choix, à recopier dans le fichier pour le garder |
| `/pnj apparence <id> fichier` | retour à l'apparence de son fichier |
| `/pnj apparence <id> aucune` | plus d'apparence : son skin (ci-dessous) |

Sans apparence, le skin d'un fichier PNG (64 × 64) : `mods/heraliz-quetes/src/client/resources/assets/heraliz_quetes/textures/pnj/<skin>.png`
(puis refabriquer le mod), sinon Steve ou Alex.

## 2. Poser les points de quête (sur la carte, en jeu)

Un point de quête = un bloc (un tiroir, un bureau, un casier, un ordinateur, une épave...) ou un endroit. **Le bloc
n'est pas modifié.** Les joueurs ne voient le point que s'ils ont la quête (petites étincelles quand ils sont près).

| Commande | Effet |
|---|---|
| `/quete baguette <groupe>` | donne la baguette du groupe : **clic droit sur un bloc** = un point ; **accroupi + clic droit** = retirer |
| `/quete point ajouter <groupe> [nom]` | un point à l'endroit où l'on se tient (villes, gares, lieux...) |
| `/quete point nom <id> <nom>` | nom affiché (« Belcourt ») : utilisé par les contrats (« Chasse à Belcourt ») |
| `/quete points [groupe]` | liste |
| `/quete point tp <id>` · `/quete point retirer <id>` | aller au point · l'effacer |

Avec la baguette en main, le staff voit tous les points autour de lui (particules vertes).

**Un groupe avec plusieurs points** = le jeu en tire un au hasard pour chaque joueur (chacun sa cachette : impossible de
donner la réponse aux autres). Plus il y a de points, mieux c'est. Une quête dont un groupe est vide n'est pas proposée
(« pas encore prête »).

### Les groupes à poser (Commandant Varga)

| Groupe | Où | Combien | Quête |
|---|---|---|---|
| `insigne_varga` | baguette : meubles, cadavres, casiers dans les villes (chacun le sien, 5 par joueur) | 15 et plus, dans plusieurs villes | 9 « Le 7e régiment » |
| `deserteur` | baguette : 3 cadavres ou meubles (les carnets des déserteurs) | 3 et plus | 22 « Les déserteurs » |
| `cachette_deserteurs` | baguette : une cachette (caisse, tiroir) | 1 à 3 | 22 |
| `boite_noire` | baguette : un bloc d'une épave d'avion dans la zone de gaz | 1 à 3 | 29 « La boîte noire » |
| `salle_verte`, `salle_bleue`, `salle_rouge` | `/quete point ajouter` à l'intérieur de CHAQUE salle à carte de cette couleur (une seule suffit au joueur) | toutes | 24, 31, 38 |

Sans ces points, la quête qui en a besoin attend (« pas encore prête ») et la suite s'arrête là.

## 3. Écrire une quête

Le plus simple : demander à Claude en français (« Jack demande de ramener son carnet, au commissariat de Belcourt,
200 hérals »), il écrit la quête dans le fichier. Le format, pour référence :

```json
{
  "id": "carnet", "titre": "Le carnet de tir", "difficulte": 2,
  "demande": "Ce que dit le PNJ en donnant la quête.",
  "fin": "Ce qu'il dit à la fin.", "rappel": "Ce qu'il dit si on revient sans rien.",
  "sans_mourir": false,
  "donne": [{"objet": "colis", "ref": "colis", "nom": "Colis de Mila", "nombre": 1}],
  "etapes": [
    {"type": "ramasser", "libelle": "Trouver le carnet", "groupe": "carnet_jack", "objet": "carnet", "ref": "carnet", "nom": "Carnet de Jack"},
    {"type": "donner", "libelle": "Rapporter le carnet", "objet_quete": "carnet"}
  ],
  "recompense": {"herals": 250, "reputation": 40, "titre": "Diplômé du camp", "objets": {"heralizcrates:quete_key": 1}}
}
```

`objets` de la récompense : objets donnés à la fin (ici une Clé de Quêtes). `"desactivee": true` : la quête est sautée
(son système n'est pas prêt : capture d'un territoire de clan).

Écrire la demande en une phrase claire qui dit exactement quoi faire (« Tue 10 zombies avec une arme à feu »).

Accueil du PNJ (clé `accueil` de son fichier) : `inconnu`, `connu`, `ami` selon la réputation, et surtout `besoin` (quand
il a une nouvelle quête à donner) et `en_cours` (quand une de ses quêtes est en cours) : un texte ou une liste, tiré au
hasard.

Les soins, grenades et armes de mêlée du mod tactique se demandent par leur vrai nom (`heralisz_heal:carfak`...),
pas par `heralisz_tactical:consumable`.

Un texte peut être en français seulement (`"Bonjour"`) ou dans les trois langues (`{"fr": "...", "en": "...", "ru": "..."}`).

### Les étapes

| type | Le joueur doit | Réglages |
|---|---|---|
| `parler` | parler à un PNJ | `pnj` (par défaut : celui de la quête) |
| `donner` | donner des objets au PNJ (ils sont pris) | `objet` (identifiant ou liste), `tag` (`heraliz_composants:vente`...), `objet_quete` (un objet de quête de la quête), `nombre`, `rarete_min` (`commun`, `peu_commun`, `rare`, `epique`, `legendaire`, `unique` ou `mythique` ; composants et objets de valeur compris), `armure` (`tete`, `corps`, `tete_ou_corps`), `usure_max` (pour cent, armes), `arme` (`feu`, `pompe`, `fusil`...), `silencieux`, `differents` (sortes différentes) |
| `montrer` | avoir ces objets sur lui (armure portée comprise) | comme `donner` (rien n'est pris) |
| `payer` | payer des hérals au PNJ | `herals` |
| `tuer` | tuer des créatures | `cible` (`zombies`, `monstres`, un identifiant, `#tag`, `mod:*`), `nombre`, `arme` (`feu`, `melee`, `couteau`, ou une famille : `pistolet`, `smg`, `fusil`, `pompe`, `precision`, `mitrailleuse`), `arme_id`, `silencieux`, `distance_min`, `nuit`, `sans_degats` (touché = compteur à zéro), zone : `groupe` ou `point` + `rayon`, `participation` (boss : tous les joueurs à moins de N blocs comptent) |
| `ramasser` | clic droit sur le point : il reçoit un objet de quête | `groupe`/`point`, `nombre` (de points), `objet` (modèle : document, carnet, lettre, photo, cle, cle_usb, colis, echantillon, plaque, piece ; en 3D : insigne_regiment, boite_noire, sacoche_deserteurs), `ref`, `nom`, `desc` |
| `indice` | clic droit sur le point : une note s'affiche | `groupe`/`point`, `nombre`, `titre`, `texte` |
| `code` | clic droit sur le point : taper un code | `groupe`/`point`, `code`, `titre` |
| `deposer` | clic droit sur le point, l'objet en main | `groupe`/`point`, `nombre`, `objet_quete` ou `objet` / `tag` |
| `endroit` | aller sur place | `groupe`/`point`, `nombre`, `rayon`, `un_seul` (tous les points du groupe, le premier atteint suffit) |
| `tenir` | rester sur place N secondes (sortir = zéro) | `groupe`/`point`, `rayon`, `secondes` |
| `attendre` | attendre (temps réel) | `secondes` |
| `survivre` | rester en vie sur la carte | `secondes`, `vie_max` (n'avance que blessé : 8 = 4 cœurs) |
| `extraction` | rentrer au spawn par une extraction | `nombre` |
| `fouiller` | ouvrir des conteneurs de HeraliZ Loot | `nombre`, `palier_min` (1 à 7), `modele` (morceau du nom), zone (`groupe` + `rayon`) |
| `executer` | achever un joueur à terre (exécution de Réanimation ou coup de grâce) | `nombre` |
| `fabriquer` | fabriquer une arme à feu à la Table d'armurier (TaCZ) | `nombre` |
| `frappe` | appeler une frappe aérienne (jumelles d'Airstrike) | `nombre` |
| `capturer` | capturer un territoire avec son clan : PAS ENCORE BRANCHÉ (quête `desactivee`) | |

Réglages communs : `libelle` (texte affiché), `marqueur` (`exact`, `zone` = distance arrondie, `aucun`), `delai`
(secondes : au-delà, la quête échoue), `meme_point` (mêmes points que l'étape d'avant), `visible` (montrée avant d'y
arriver ; sinon « ??? »), `reponse` (ce que dit le PNJ après cette étape).

Un objet de quête **tombe sur le cadavre** à la mort ; il revient au même point 30 minutes après l'avoir ramassé.
Une quête dont la dernière étape ne se fait pas chez le PNJ se termine en revenant le voir (donc en s'extrayant).

### Les contrats (retirés le 18/10 : listes `contrats` vides ; le format reste au cas où)

```json
{"id": "chasse", "poids": 3, "reputation_min": 0,
 "variables": {"n": {"min": 20, "max": 50, "pas": 5}, "ville": {"points": "villes"},
               "arme": {"choix": [{"valeur": "pompe", "nom": {"fr": "fusil à pompe", "en": "shotgun"}}]}},
 "quete": { ...une quête, avec {n}, {ville}, {ville.nom}, {arme}, {arme.nom}..., "recompense": {"herals": "{n}*6"} }}
```

`poids` : plus il est grand, plus le modèle sort souvent. La récompense peut être un calcul (`"{n}*6+50"`).

## Recycleur

PNJ `recycleur` (`pnj/recycleur.json`, marchand `"type": "recyclage"`) : à poser une fois au spawn, `/pnj creer recycleur`.
Ses règles sont dans `recyclage.json`, **écrit par `outils/recyclage.py`** (jamais à la main) ; relu par `/quete recharger`.

PNJ `commissaire` (`pnj/commissaire.json`, marchand `"type": "hdv"`, 28/10) : le commissaire-priseur ; son bouton ouvre
l'hôtel des ventes de HeraliZ Grades (`/hdv`, mêmes règles : au spawn seulement). À poser une fois : `/pnj creer commissaire`.
Tout marchand de type `hdv` fait de même (aucun article, aucune règle de rachat).

