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
