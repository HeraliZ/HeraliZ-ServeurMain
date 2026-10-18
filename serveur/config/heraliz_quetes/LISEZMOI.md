# HeraliZ Quêtes : guide du staff

Ce dossier est copié sur le Ptero par la mise en ligne (`config/heraliz_quetes/`). **Le dépôt fait foi** : une
modification faite à la main sur le serveur est écrasée au prochain envoi. Après un changement : `/quete recharger` en
jeu (pas besoin de redémarrer).

- `pnj/<id>.json` : un fichier par PNJ (son nom, ses phrases, sa suite de quêtes, ses modèles de contrats).
- La progression des joueurs n'est PAS ici : elle est dans `heraliz_quetes/joueurs/` à la racine du serveur (hors du
  monde, elle survit aux changements de carte).
- Les points de quête posés en jeu sont gardés avec le monde (`data/heraliz_quetes_points.dat`).

## 1. Poser un PNJ

Se placer à l'endroit voulu, regarder dans la direction où le PNJ doit regarder, puis :

| Commande | Effet |
|---|---|
| `/pnj creer <id>` | pose le PNJ (son `id` = le nom de son fichier : `jack`, `jacqueline`, `doyen`...) |
| `/pnj deplacer <id>` | le déplace là où l'on est |
| `/pnj retirer <id>` | l'enlève |
| `/pnj skin <id> <skin>` | skin = fichier `textures/pnj/<skin>.png` du jar joueur (`defaut` : Steve / Alex), utilisé seulement sans apparence CaraCrea |
| `/pnj apparence <id> moi\|fichier\|aucune` | apparence du créateur de personnage (voir dessous) |
| `/pnj bras <id> fins\|larges` | modèle Alex ou Steve |
| `/pnj liste` | les PNJ des fichiers, et combien sont posés |

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

### Les groupes à poser

Points « endroit » (se placer dessus, `/quete point ajouter`) :

| Groupe | Où | Combien |
|---|---|---|
| `villes` | centre de chaque ville (+ `/quete point nom`) | toutes les villes |
| `grandes_villes` | centre des villes T5 / T6 | 2 à 4 |
| `prison` | centre de la prison | 1 |
| `caserne` | entrée de la base militaire | 1 |
| `gares` | chaque gare (+ nom) | toutes |
| `lieux` | chaque lieu de la carte (+ nom) | 15 à 30 |
| `salle_verte`, `salle_bleue`, `salle_rouge` | à l'intérieur de chaque salle à carte | toutes |
| `casino_tables` | à côté de chaque jeu du casino | 4 ou plus |
| `ferme_piege` | une ferme isolée | 1 |
| `mesure_gaz`, `mesure_radiation`, `releve_froid` | dans une zone de gaz / radiation / froid (un bloc : il sert aussi à ramasser) | 1 à 3 |
| `groupe_electrogene`, `tour_radio` | sur un groupe électrogène / l'émetteur de la tour radio (un bloc) | 1 à 3 |

Points « bloc » (baguette, clic droit sur le meuble) :

| Groupe | Sur quoi | Combien | PNJ |
|---|---|---|---|
| `carnet_jack`, `caisse_jack` | tiroirs, caisses, tables (villages) | 10 et plus | Jack |
| `sergent_corps` → `sergent_plaque` | un cadavre, puis un casier dans un poste de garde | 1 et 1 | Jack |
| `caserne_tableau` → `caserne_armoire` | tableau de la salle de briefing, armoire du chef (base militaire) | 1 et 1 | Jack |
| `hopital_armoire` | armoire de la salle de soins d'un hôpital | 1 à 3 | Jacqueline |
| `medecin_notes` (3) → `medecin_cabinet` | 3 meubles dans 3 maisons d'une même ville, puis le cabinet | 3 et 1 | Jacqueline |
| `poste_secours`, `trousse` | postes de secours / cachettes en ville | 5 et plus | Jacqueline |
| `roue` | épaves de voitures | 10 et plus | Ferraille |
| `aiguillage`, `projecteur` | aiguillages le long de la voie / projecteurs d'un camp militaire | 4 et plus | Ferraille |
| `raffinerie_vanne` | au fond de la raffinerie (zone de gaz) | 1 | Ferraille |
| `loco` | 3 blocs de la locomotive du spawn | 3 | Ferraille |
| `radio`, `balise`, `document` | postes radio, balises, cachettes | 10 et plus | Nadia |
| `directeur_bureau`, `directeur_coffre` | bureau et coffre du directeur (usine de Clairval) | 1 et 1 | Nadia |
| `secretaire_photo` | un meuble de l'appartement au-dessus de la pharmacie | 1 | Nadia |
| `ecole_tableau` | tableau noir d'une école | 1 | Nadia |
| `poste_nadia` | le poste radio de Nadia, au spawn | 1 | Nadia |
| `page_explorateur` | 5 cachettes dans 5 villes | 5 et plus | Nadia |
| `micro` | meubles de bâtiments d'une ville T5 | 3 et plus | Nadia |
| `prison_registre`, `tour_controle`, `bunker_bande` | salle bleue de la prison / tour de contrôle / salle rouge d'un bunker | 1 | Nadia |
| `cachette_port`, `caisse_noire` | cachettes du port | 1 | Mila |
| `mairie_tampons` | bureau de la mairie | 1 | Mila |
| `livraison`, `cachette` | boîtes aux lettres, portes, cachettes | 10 et plus | Mila |
| `contact_mila` | 3 meubles dans 3 villes | 3 | Mila |
| `corps_joueur` | un cadavre | 1 à 3 | Lucky |
| `soldat`, `deserteur` (3) → `cachette_deserteurs` | cadavres de soldats / 3 cadavres / une cachette au vieux silo | 10 et plus, 3, 1 | Varga |

Les textes de certaines quêtes décrivent l'endroit (« au-dessus de la pharmacie », « au vieux silo ») : poser le point
au bon endroit, ou changer le texte dans le fichier.

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
  "recompense": {"herals": 250, "reputation": 40, "titre": "Diplômé du camp"}
}
```

Un texte peut être en français seulement (`"Bonjour"`) ou dans les trois langues (`{"fr": "...", "en": "...", "ru": "..."}`).

### Les étapes

| type | Le joueur doit | Réglages |
|---|---|---|
| `parler` | parler à un PNJ | `pnj` (par défaut : celui de la quête) |
| `donner` | donner des objets au PNJ (ils sont pris) | `objet` (identifiant ou liste), `tag` (`heraliz_composants:vente`...), `objet_quete` (un objet de quête de la quête), `nombre`, `rarete_min` (`commun` ... `unique`), `usure_max` (pour cent, armes), `arme` (`feu`, `pompe`, `fusil`...), `silencieux`, `differents` (sortes différentes) |
| `montrer` | avoir ces objets sur lui | comme `donner` (rien n'est pris) |
| `payer` | payer des hérals au PNJ | `herals` |
| `tuer` | tuer des créatures | `cible` (`zombies`, `monstres`, un identifiant, `#tag`, `mod:*`), `nombre`, `arme` (`feu`, `melee`, `couteau`, ou une famille : `pistolet`, `smg`, `fusil`, `pompe`, `precision`, `mitrailleuse`), `arme_id`, `silencieux`, `distance_min`, `nuit`, `sans_degats` (touché = compteur à zéro), zone : `groupe` ou `point` + `rayon`, `participation` (boss : tous les joueurs à moins de N blocs comptent) |
| `ramasser` | clic droit sur le point : il reçoit un objet de quête | `groupe`/`point`, `nombre` (de points), `objet` (modèle : document, carnet, lettre, photo, cle, cle_usb, colis, echantillon, plaque, piece), `ref`, `nom`, `desc` |
| `indice` | clic droit sur le point : une note s'affiche | `groupe`/`point`, `nombre`, `titre`, `texte` |
| `code` | clic droit sur le point : taper un code | `groupe`/`point`, `code`, `titre` |
| `deposer` | clic droit sur le point, l'objet en main | `groupe`/`point`, `nombre`, `objet_quete` ou `objet` / `tag` |
| `endroit` | aller sur place | `groupe`/`point`, `nombre`, `rayon` |
| `tenir` | rester sur place N secondes (sortir = zéro) | `groupe`/`point`, `rayon`, `secondes` |
| `attendre` | attendre (temps réel) | `secondes` |
| `survivre` | rester en vie sur la carte | `secondes`, `vie_max` (n'avance que blessé : 8 = 4 cœurs) |
| `extraction` | rentrer au spawn par une extraction | `nombre` |
| `fouiller` | ouvrir des conteneurs de HeraliZ Loot | `nombre`, `palier_min` (1 à 7), `modele` (morceau du nom), zone (`groupe` + `rayon`) |

Réglages communs : `libelle` (texte affiché), `marqueur` (`exact`, `zone` = distance arrondie, `aucun`), `delai`
(secondes : au-delà, la quête échoue), `meme_point` (mêmes points que l'étape d'avant), `visible` (montrée avant d'y
arriver ; sinon « ??? »), `reponse` (ce que dit le PNJ après cette étape).

Un objet de quête **tombe sur le cadavre** à la mort ; il revient au même point 30 minutes après l'avoir ramassé.
Une quête dont la dernière étape ne se fait pas chez le PNJ se termine en revenant le voir (donc en s'extrayant).

### Les contrats (sans limite, toujours 3 au tableau)

```json
{"id": "chasse", "poids": 3, "reputation_min": 0,
 "variables": {"n": {"min": 20, "max": 50, "pas": 5}, "ville": {"points": "villes"},
               "arme": {"choix": [{"valeur": "pompe", "nom": {"fr": "fusil à pompe", "en": "shotgun"}}]}},
 "quete": { ...une quête, avec {n}, {ville}, {ville.nom}, {arme}, {arme.nom}..., "recompense": {"herals": "{n}*6"} }}
```

`poids` : plus il est grand, plus le modèle sort souvent. La récompense peut être un calcul (`"{n}*6+50"`).
