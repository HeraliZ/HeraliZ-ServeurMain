# HeraliZ — PNJ et quêtes (scénario v2)

> Document de conception, version 2 (16/10), refaite d'après les précisions du propriétaire : les PNJ sont **au
> spawn, posés en jeu par le staff**, chacun a **son nom et son métier**, les joueurs font les quêtes **quand ils
> veulent, sans limite par jour**, une commande **`/quetes`** montre tout et permet de **suivre** une quête affichée à
> droite de l'écran, et les quêtes doivent être **variées, plus ou moins difficiles et faisables** (pas « je me
> téléporte, je ramasse, c'est fini »). **Rien n'est codé.** Chiffres (hérals, nombres, durées) : propositions à régler
> après les premiers essais.

## Sommaire

1. Les règles du jeu des quêtes
2. `/quetes` et le suivi à l'écran
3. Ce que le staff pose en jeu
4. Comment une quête devient difficile (les « ingrédients »)
5. Les 8 PNJ du spawn
6. Les quêtes de chaque PNJ (suite + contrats)
7. Les quêtes à plusieurs PNJ (grandes histoires)
8. Ce qui est faisable, et avec quoi
9. Questions restantes

---

## 1. Les règles du jeu des quêtes

- **Aucune limite par jour.** Chaque PNJ a :
  1. **sa suite** : 14 à 22 quêtes écrites à la main, faites une fois, dans l'ordre (la suivante s'ouvre quand la
     précédente est finie). De facile à très dur.
  2. **son tableau de contrats**, **toujours plein** : 3 contrats proposés en même temps, tirés au hasard dans ses
     modèles. Un contrat pris ou fini est **remplacé tout de suite** par un autre. On peut en refuser un pour en tirer
     un nouveau (une fois toutes les 2 minutes, pour qu'on ne fouille pas la liste jusqu'au plus facile).
- **Plusieurs quêtes à la fois**, chez plusieurs PNJ, sans limite de nombre ; une seule est **suivie** à l'écran.
- **Abandonner** une quête : possible à tout moment depuis `/quetes` (les objets de quête liés disparaissent).
- **Ce qui empêche d'enchaîner trop vite** n'est pas une limite, c'est la quête elle-même : temps de trajet,
  extraction, risque de mort, objets à trouver dans le loot (section 4). Un contrat facile prend 10 à 15 minutes,
  un difficile plus d'une heure.
- **Récompenses** : hérals, réputation chez le PNJ (contrats mieux payés, accueil qui change), titres. Rien de nouveau
  dans le loot ni le craft (choix du propriétaire), pas de pay2win.

---

## 2. `/quetes` et le suivi à l'écran

**Commande `/quetes`** (et une touche, par exemple J) → écran au style des clans :

```
┌───────────────────────┬───────────────────────────────────────────────────────┐
│  MES PNJ              │  JACK — instructeur du camp            Réputation ███░ │
│ ▸ Jack          (2)   │                                                       │
│   Jacqueline    (1)   │  ● Les munitions perdues            ★★★   EN COURS    │
│   Nadia         (3)   │     ✔ Caisse de Belcourt                              │
│   Ferraille     (0)   │     ✔ Caisse de Valmont                               │
│                       │     ○ Caisse de Marbois                               │
│                       │     ○ Rapporter les 3 caisses à Jack (extraction)     │
│                       │     Récompense : 400 hérals                           │
│                       │     [ ★ SUIVRE ]   [ Abandonner ]                     │
│                       │                                                       │
│                       │  ● Contrat : 30 zombies à Clairval  ★   12/30         │
│                       │     [ SUIVRE ]     [ Abandonner ]                     │
├───────────────────────┴───────────────────────────────────────────────────────┤
│  En cours  ·  Terminées  ·  Titres                                            │
└───────────────────────────────────────────────────────────────────────────────┘
```

- À gauche, **les PNJ chez qui on a des quêtes** (avec leur nombre) ; clic → leurs quêtes à droite, étape par étape.
- Bouton **« Suivre »** (mettre en avant) : la quête s'affiche **en petit à droite de l'écran** :

```
                                          JACK · Les munitions perdues
                                          ○ Caisse de Marbois  (≈ 1,2 km)
                                          ○ Rapporter à Jack
```

- La quête suivie a aussi son point (ou sa zone floue) sur la **carte du monde** (touche M), calque « Quêtes ».
- Une seule quête suivie à la fois ; finir une étape fait passer le suivi à l'étape suivante tout seul.
- Notifications courtes en haut à droite : « Étape terminée », « Quête terminée : +400 hérals ».

---

## 3. Ce que le staff pose en jeu

Rien n'est retouché dans les fichiers du monde : tout est gardé à part (comme les salles à carte).

- **PNJ** : `/pnj creer jack "Jack"` à l'endroit où l'on se tient (regard = direction du PNJ), puis `skin`,
  `deplacer`, `geste`, `retirer`. Ses phrases et ses quêtes sont dans son fichier (`heraliz_quetes/pnj/jack.json`),
  écrit par Claude d'après ce que le propriétaire dit en français.
- **Points de quête**, avec la **baguette de quête** (`/quete baguette <nom du point>` puis clic droit sur un bloc) :

| Sorte de point | Pour le joueur qui a la quête | Pour les autres |
|---|---|---|
| **Objet à ramasser** | légère lueur, clic droit = l'objet de quête dans l'inventaire | bloc normal |
| **Indice** | clic droit = une note, une lettre, une photo s'affiche | bloc normal |
| **Endroit** | zone à atteindre (rayon réglable) | rien |
| **Coffre à code** | clic droit = clavier, il faut le bon code | bloc normal |
| **Dépôt** | clic droit avec l'objet en main = déposé / réparé / installé | bloc normal |
| **Tenir** | rester dans la zone X secondes (barre qui se remplit) | rien |

- **Groupes de points** : on pose par exemple **15 points « caisse_jack »** dans 15 endroits différents ; une quête
  ou un contrat en **tire un au hasard pour chaque joueur**. Deux joueurs n'ont pas la même cachette : impossible de se
  passer la réponse ou de la mettre dans une vidéo.
- En créatif, le staff voit tous les points (étiquettes) ; `/quete points` les liste, `voir` y emmène, `retirer`.
- **Objets de quête** : une dizaine d'objets génériques (document, carnet, lettre, photo, clé, clé USB, colis,
  échantillon, plaque d'identité, pièce) renommés par la quête (« Carnet de tir de Jack ») ; modèles montrés en rendus
  avant d'être faits.

---

## 4. Comment une quête devient difficile (les « ingrédients »)

Une quête mélange 1 à 4 de ces ingrédients. Plus il y en a, plus elle est dure (★ à ★★★★).

| Ingrédient | Pourquoi c'est dur | Exemple |
|---|---|---|
| **Rapporter au spawn** | le spawn est un autre monde : on n'y revient **qu'en s'extrayant** (ou en mourant… sans l'objet). Il faut tenir l'extraction avec l'objet sur soi | « Rapporte-moi le carnet » |
| **Perdu à la mort** | l'objet de quête tombe sur le cadavre ; un autre peut le prendre ; il faut retourner le chercher (nouvelle copie au point après 30 min) | tous les objets de quête |
| **Plusieurs lieux éloignés** | 3 villes à l'autre bout de la carte : une sortie ne suffit pas toujours | « 3 caisses dans 3 villages » |
| **Piste à suivre** | l'indice du point A donne le lieu du point B, sans marqueur ; seulement une zone floue sur la carte | « La lettre parle d'un hangar près du port » |
| **Zone dangereuse** | gaz, radiation, froid : il faut l'équipement (masque, Hazmat, combinaison) | « Le dossier est dans le labo, en zone de gaz » |
| **Salle à carte** | il faut d'abord trouver une carte d'accès (verte, bleue, rouge) | « Le registre est dans la salle bleue » |
| **Zone à haut loot** | les villes T5-T6 sont pleines de joueurs et de zombies | aérodrome, prison |
| **Objet à fournir** | il faut le **trouver dans le loot** (rareté ou état minimum) : dépend de la chance | « Un accessoire Épique » |
| **Condition** | sans mourir, de nuit, avec telle arme, en moins de X minutes, sans être touché | « En une seule sortie sans mourir » |
| **Quantité** | tuer 50, fouiller 20 conteneurs | « 50 zombies à la prison » |
| **Attente** | le PNJ « analyse » l'objet : revenir dans 1 h de jeu | « Reviens tout à l'heure, je regarde ça » |
| **Groupe** | boss, gros nettoyage : impossible seul (ou presque) | « Le Porteur de Peste » |

Repères de temps visés : ★ 10-15 min · ★★ 20-40 min · ★★★ 1-2 h ou plusieurs sorties · ★★★★ en groupe, une soirée.

---

## 5. Les 8 PNJ du spawn

Chacun a **un métier**, donc ses quêtes restent dans son domaine. Noms proposés (le propriétaire choisit).

| PNJ | Où au spawn | Son domaine | Phrases d'ambiance (bulle quand on passe) |
|---|---|---|---|
| **Le Doyen** | entrée du spawn, près de la borne de départ | accueil, premiers pas, il envoie vers les autres | « Doucement, petit. T'es au terminus. » · « Personne ne revient sans s'extraire, retiens ça. » |
| **Jack** | camp d'entraînement | **armes** : tir, munitions, accessoires, combat contre les zombies | « On vise la cible, pas le copain. » · « Recharge AVANT d'être à sec. » · « Un silencieux, c'est pas une décoration. » |
| **Jacqueline** | infirmerie | **santé** : soins, réanimation, zones de gaz / radiation / froid, échantillons | « Si tu saignes, assieds-toi. Si tu tousses, va-t'en. » · « Lave-toi les mains. Non, avec du savon. » |
| **Ferraille** | atelier / garage | **mécanique** : composants, réparations, véhicules, train, construction | « T'aurais pas un ressort ? Non ? T'as une tête à en trouver. » · « Ça, c'est pas cassé. C'est fatigué. » |
| **Nadia** | poste radio | **renseignement** : documents, codes, pistes, exploration, extractions | « Fréquence 104.7… toujours rien. » · « Chut. J'écoute. » |
| **Mila** | marché | **commerce** : objets de valeur, livraisons, échanges, clans | « Tout se vend. Même toi. » · « Pas de crédit. Jamais. » |
| **Lucky** | entrée du casino | **casino** : essayer les jeux, petits paris | « Ici on meurt de rire, pas de faim. » · « La chance, ça se travaille. » |
| **Commandant Varga** | poste de commandement | **opérations** : boss, salles à carte, zones militaires, duels, ClashWar, territoires | « Repos. Enfin… pas trop. » · « Une zone tenue, c'est une zone qui respire. » |

Et un **PNJ caché** sur la carte (pas au spawn) : **le Vieil Oskar**, un ermite qu'on débloque par Nadia (section 7).

Chaque PNJ a 3 accueils selon la réputation : inconnu (« T'es nouveau, je te connais pas. Moi c'est Jack… »),
connu (« Ah, te revoilà. »), ami (« Mon meilleur élève ! »).

---

## 6. Les quêtes de chaque PNJ

Légende : **★** difficulté · **R** = à rapporter au spawn (donc par extraction). Récompenses en hérals sauf mention.

### 6.1 Le Doyen — premiers pas (10 quêtes)

Le tutoriel caché du serveur. Il fait découvrir les autres PNJ.

| # | Quête | À faire | ★ | Récompense |
|---|---|---|---|---|
| 1 | Encore vivant | Parler au Doyen | ★ | — |
| 2 | Fais le tour | Parler à Jack, Jacqueline, Ferraille et Nadia | ★ | 50 |
| 3 | Le grand saut | Utiliser la borne de départ | ★ | 50 |
| 4 | Les poches des morts | Fouiller 5 conteneurs sur la carte | ★ | 100 |
| 5 | Retour au bercail | Réussir une première extraction | ★ | 200 |
| 6 | Ouvrir les yeux | Ouvrir la carte (M), suivre une quête dans `/quetes` | ★ | 50 |
| 7 | Une sortie propre | Une sortie complète : 10 conteneurs fouillés, extraction, sans mourir | ★★ | 300 |
| 8 | Les autres | Parler à Mila, Lucky et Varga | ★ | 50 |
| 9 | La valeur des choses | Gagner 1 000 hérals au total (n'importe comment) | ★★ | 200 |
| 10 | Un vrai survivant | Avoir fini 5 quêtes de suite chez 3 PNJ différents | ★★★ | 1 000 + titre « Survivant du terminus » |

### 6.2 Jack — instructeur du camp (armes) : 22 quêtes

| # | Quête | À faire | ★ | Récompense |
|---|---|---|---|---|
| 1 | Au stand | Au stand de tir du camp : toucher 15 cibles en 60 s | ★ | 100 |
| 2 | Premier sang | Tuer 10 zombies avec une arme à feu | ★ | 100 |
| 3 | Les balles, ça se garde | Tuer 5 zombies à la mêlée ou au couteau | ★ | 100 |
| 4 | Le carnet de tir | Ramasser son carnet (un point tiré dans le groupe « carnet_jack ») | ★★ R | 250 |
| 5 | Bien équipé | Lui montrer une arme avec 3 accessoires posés (viseur, bouche, poignée) | ★★ | 200 |
| 6 | Les munitions perdues | 3 caisses dans 3 villages (3 points éloignés tirés au hasard), les rapporter | ★★★ R | 400 |
| 7 | Silence | Tuer 15 zombies avec une arme à silencieux | ★★ | 250 |
| 8 | Tireur d'élite | Tuer 5 zombies à plus de 60 blocs | ★★ | 300 |
| 9 | Le stand, niveau 2 | Au stand : 25 cibles en 60 s, sans rater plus de 5 tirs | ★★ | 250 |
| 10 | Pompe et pompe | Tuer 20 zombies au fusil à pompe dans une ville T4 ou plus | ★★ | 300 |
| 11 | Une pièce rare | Lui donner un accessoire **Épique ou mieux** (trouvé dans le loot) | ★★★ | 500 |
| 12 | La plaque du sergent | Trouver le cadavre d'un ancien du camp (point), lire sa lettre (indice) : elle dit où est sa plaque (2e point, sans marqueur), la rapporter | ★★★ R | 500 |
| 13 | Démontage | Lui rapporter 3 armes différentes à moins de 25 % d'usure (pour ses cours) | ★★ | 300 |
| 14 | Nettoyage de la prison | Tuer 50 zombies dans la zone de la prison | ★★★ | 600 |
| 15 | La caserne | À la base militaire : un code sur un tableau (indice) ouvre l'armoire du chef (coffre à code) ; rapporter le **registre** | ★★★ R | 700 |
| 16 | De nuit | Tuer 30 zombies la nuit, sans vision nocturne | ★★★ | 500 |
| 17 | Le duel | Gagner 3 duels (`/duel`) | ★★ | 400 |
| 18 | Le prototype | Ferraille lui a fait un silencieux spécial : le **poser** sur 3 cibles placées dans 3 villes (points de dépôt) et tuer 5 zombies à chaque endroit | ★★★ | 600 |
| 19 | Sans une égratignure | Tuer 20 zombies sans perdre de vie | ★★★ | 600 |
| 20 | Le gros calibre | Lui donner une arme **Légendaire** (précision .338 / .50, minigun, lance-grenades) | ★★★ | 1 000 |
| 21 | Les armes du camp | Rapporter 10 armes, toutes différentes, en une ou plusieurs fois | ★★★ | 1 200 |
| 22 | L'examen final | En **une seule sortie sans mourir** : 40 zombies, dont 2 à plus de 60 blocs, 5 conteneurs T4 ou plus, extraction | ★★★★ | 2 000 + titre « Diplômé du camp » |

**Contrats de Jack** (toujours 3 au tableau, remplacés dès qu'ils sont pris) :
- « Tue {20-50} zombies à {ville au hasard}. » ★–★★ · 100-300
- « Aujourd'hui c'est {pistolet / pompe / mitraillette / fusil d'assaut / précision} : {10-20} zombies avec. » ★★ · 200
- « {5-10} zombies à plus de {40-80} blocs. » ★★ · 250
- « Rapporte-moi {2-4} {chargeurs / boîtes de munitions / grenades}. » ★ · 100-200
- « Une caisse du camp traîne à {un point du groupe « caisse_jack »}. Rapporte-la. » ★★ R · 300
- « Montre-moi une arme {Rare / Épique} avec {un viseur / un silencieux}. » ★★ · 250
- « Au stand : {20-30} cibles en 60 s. » ★ · 100
- « {15} zombies sans mourir, avec seulement {une arme de mêlée / un pistolet}. » ★★ · 250
- « Gagne un duel. » ★ · 150 (au plus une fois par heure contre la même personne)

### 6.3 Jacqueline — l'infirmière (santé) : 20 quêtes

| # | Quête | À faire | ★ | Récompense |
|---|---|---|---|---|
| 1 | Premiers soins | Se faire un bandage | ★ | 50 |
| 2 | Ma pharmacie | Lui donner 5 objets de soin différents | ★ | 150 |
| 3 | La jambe | Se soigner une jambe cassée avec une attelle | ★ | 100 |
| 4 | L'hôpital | À l'hôpital de {ville}, ouvrir l'armoire à pharmacie de la salle de soins (point), rapporter le **carnet de commandes** | ★★ R | 300 |
| 5 | On n'abandonne personne | Réanimer 3 joueurs à terre | ★★ | 300 |
| 6 | Du sang frais | Tuer 15 zombies à la mêlée (« le sang se prélève frais ») avec le **flacon** qu'elle donne en poche, le lui rapporter | ★★ R | 300 |
| 7 | L'air qui tue | Tenir 60 s dans une zone de **gaz** avec un masque, rapporter le **filtre usé** | ★★ R | 400 |
| 8 | Les vaccins | Fouiller 5 « glacières à vaccins » ou « aide humanitaire » (conteneurs T5) | ★★★ | 500 |
| 9 | Le froid | Tenir 2 min dans une zone de **froid** en combinaison, ramasser le **thermomètre** (point) | ★★ R | 400 |
| 10 | Rayons | Tenir 60 s en zone de **radiation** en Hazmat, ramasser le **dosimètre** (point) | ★★★ R | 600 |
| 11 | Le médecin de Clairval | Lire 3 notes d'un médecin disparu (3 indices dans 3 maisons de Clairval) : la 3e donne le code de son cabinet (coffre à code), rapporter son **dossier** | ★★★ R | 700 |
| 12 | Garde de nuit | Réanimer 2 joueurs **d'un autre clan** | ★★ | 400 |
| 13 | Analyses | Après la quête 6 : attendre **1 h de jeu** (« je l'analyse »), revenir la voir | ★ | 200 |
| 14 | Rupture de stock | Lui donner 3 soins **Épiques** (Surv12, CMS) | ★★★ | 800 |
| 15 | Postes de secours | Installer 3 **trousses** (objets de quête qu'elle donne) dans 3 postes de secours posés en ville (points de dépôt) | ★★★ | 600 |
| 16 | La mère | Participer à la mort de la **Mère des Ruines**, rapporter le **venin** | ★★★★ R | 1 500 |
| 17 | Le porteur | Participer à la mort du **Porteur de Peste**, rapporter les **spores** dans la cartouche scellée qu'elle donne | ★★★★ R | 1 500 |
| 18 | Sous pression | 20 minutes de suite sur la carte avec moins de 4 cœurs, sans mourir | ★★★ | 600 |
| 19 | Le remède | Avec venin et spores : attendre 2 h, puis réanimer 5 joueurs (« je vois si ça marche ») | ★★★ | 1 000 |
| 20 | Infirmier de la Zone | Avoir réanimé 25 joueurs au total | ★★★★ | 2 000 + titre « Infirmier de la Zone » |

**Contrats de Jacqueline** :
- « Rapporte-moi {2-4} {bandages / attelles / seringues / trousses}. » ★ · 100-200
- « Réanime {1-3} joueurs. » ★★ · 150-300
- « Tiens {30-90} s dans le gaz de {zone} et rapporte le filtre. » ★★ R · 300
- « Fouille {3-6} conteneurs médicaux (pharmacie, armoire de soins, glacière). » ★ · 150
- « Une trousse de secours a été oubliée à {un point du groupe « trousse »}. » ★★ R · 300
- « {15} zombies à la mêlée, et rapporte le flacon. » ★★ R · 300
- « Passe 15 min en zone de {froid / radiation} avec la bonne tenue, sans mourir. » ★★★ · 450

### 6.4 Ferraille — le mécano (mécanique) : 20 quêtes

| # | Quête | À faire | ★ | Récompense |
|---|---|---|---|---|
| 1 | Un ressort ! | Lui donner 2 ressorts | ★ | 100 |
| 2 | Ferraille pour Ferraille | 10 ferrailles | ★ | 150 |
| 3 | Le circuit | Une batterie et un circuit imprimé | ★★ | 250 |
| 4 | Pneus | Ramasser 4 roues sur 4 épaves de voitures (groupe de points « roue ») | ★★ R | 300 |
| 5 | Ça tient avec du scotch | 5 rouleaux de scotch + 3 planches | ★ | 150 |
| 6 | Le groupe électrogène | Au point « groupe électrogène » de {ville} : déposer une batterie et un kit de pièces, puis tenir 2 min pendant qu'il démarre (les zombies arrivent au bruit) | ★★★ | 500 |
| 7 | Les plaques | Une plaque de chaque sorte : recyclée, soudée, métal, renforcée | ★★★ | 600 |
| 8 | Le camion d'Hector | Trouver le camion abandonné (zone floue), déposer 2 kits de pièces dessus, le conduire jusqu'à la gare de {ville} | ★★★ | 700 |
| 9 | Le train | Prendre le train de la gare A à la gare B sans descendre (« écoute le bruit des essieux ») | ★ | 150 |
| 10 | Les rails | Inspecter 4 aiguillages du réseau (points le long de la voie) | ★★ | 400 |
| 11 | Ma base, mes règles | Poser une armoire à outils et 6 fondations (CRac) | ★★ | 300 |
| 12 | Usure | Rapporter une arme à moins de 10 % d'usure (il la répare et te la rend) | ★★ | 200 + réparation |
| 13 | Les pièces d'armes | 2 canons, 2 ressorts, 2 kits de pièces | ★★★ | 700 |
| 14 | La radio de Nadia | 2 radios cassées et un circuit imprimé (pour Nadia) | ★★ | 400 + réputation Nadia |
| 15 | Le projecteur | Poser 3 ampoules (objets de quête qu'il donne) sur 3 projecteurs d'un camp militaire (points de dépôt) | ★★★ | 600 |
| 16 | La raffinerie | Récupérer la **vanne** au fond de la raffinerie (point en zone de gaz) | ★★★ R | 800 |
| 17 | Le blindé | 4 plaques renforcées + 2 kits de pièces | ★★★★ | 1 200 |
| 18 | La loco | Avec la vanne : inspecter la locomotive du spawn (3 points), y déposer les pièces | ★★ | 800 |
| 19 | Le grand chantier | Donner 50 composants à l'événement « La loco » (section 7) | ★★★ | 1 000 |
| 20 | Mécano | Avoir donné 200 composants au total | ★★★★ | 2 000 + titre « Mécano » |

**Contrats de Ferraille** :
- « J'ai besoin de {2-4} {composant au hasard}. » ★ · 40-80 par pièce
- « {5-15} ferrailles / clous / planches / caoutchouc. » ★ · 100-200
- « Une roue sur une épave à {point du groupe « roue »}. » ★★ R · 250
- « Remets en marche le groupe électrogène de {point}. » ★★★ · 450
- « Une arme {famille} à moins de {20} % d'usure. » ★★ · 200
- « Prends le train de {gare} à {gare}. » ★ · 100
- « Pose {4-8} pièces CRac (fondations, murs). » ★ · 150

### 6.5 Nadia — la radio (renseignement) : 22 quêtes

| # | Quête | À faire | ★ | Récompense |
|---|---|---|---|---|
| 1 | Le poste abandonné | Trouver un poste radio dans un village (point tiré dans le groupe « radio »), l'inspecter | ★ | 100 |
| 2 | Les balises | Relever 3 balises météo (3 points éloignés) | ★★ | 300 |
| 3 | **Le bureau du directeur** | À Clairval, l'usine : ramasser le dossier « Bons de transport » au bureau du directeur ; le coffre-fort du bureau a un code, écrit au dos d'une photo chez sa secrétaire (indice : « elle habitait au-dessus de la pharmacie ») ; ouvrir le coffre, prendre la clé USB, rapporter les deux | ★★★ R | 800 |
| 4 | Aux jumelles | Observer 3 lieux aux jumelles depuis 3 points hauts (5 s le regard sur la cible) | ★★ | 300 |
| 5 | Message chiffré | Elle donne un message codé ; la clé est sur le tableau noir de l'école de Valmont (indice) ; taper la réponse | ★★ | 300 |
| 6 | L'antenne | À la tour radio : déposer une batterie et un circuit imprimé, tenir 3 min pendant le redémarrage | ★★★ | 600 |
| 7 | Les extractions | Réussir des extractions par 3 zones différentes | ★★ | 400 |
| 8 | Carnet d'explorateur | 5 pages d'un carnet dans 5 villes ; chaque page donne une **zone floue** pour la suivante | ★★★ R | 800 |
| 9 | Le mouchard | Poser 3 micros (objets de quête) dans 3 bâtiments d'une ville T5 (points de dépôt) | ★★★ | 600 |
| 10 | Les registres de la prison | Le registre est dans la **salle à carte bleue** de la prison : trouver une carte bleue, entrer, prendre le registre | ★★★ R | 900 |
| 11 | La voix du soldat | Un message en boucle : trouver d'où il vient (3 écoutes à 3 endroits pour « trianguler », la zone se resserre à chaque fois) | ★★★ | 700 |
| 12 | Le photographe | Lui donner un appareil photo argentique (objet de valeur à trouver) puis observer 5 lieux aux jumelles | ★★★ | 700 |
| 13 | L'aérodrome | Le journal de bord de la tour de contrôle (point en zone T6), le rapporter | ★★★ R | 900 |
| 14 | Les codes du jour | 3 chiffres dans 3 villes (indices) ouvrent le coffre de son poste (coffre à code, au spawn) | ★★ | 400 |
| 15 | Écoute de clan | Rester 3 min dans un territoire tenu par un clan, sans tirer | ★★ | 300 |
| 16 | Le bunker | Trouver l'entrée d'un bunker (zone floue), entrer dans sa salle rouge, rapporter la **bande audio** | ★★★★ R | 1 500 |
| 17 | La bande | Attendre 1 h (« je la décode »), revenir : elle débloque le Vieil Oskar | ★ | 300 |
| 18 | Cartographe | Visiter les 15 lieux de la carte posés par le staff (`/carte lieu`) | ★★★★ | 1 500 |
| 19 | Le signal de nuit | Un signal n'émet que la nuit : relever 2 points la nuit en jeu | ★★★ | 600 |
| 20 | Fuites | Lui rapporter 5 documents de quêtes différentes (dossiers, registres, journaux) | ★★★ | 800 |
| 21 | La station du nord | Remettre en marche le relais de l'arctique (froid : combinaison) | ★★★ | 800 |
| 22 | Oreille de la Zone | Avoir fini 50 contrats de Nadia | ★★★★ | 2 500 + titre « Oreille de la Zone » |

**Contrats de Nadia** :
- « Relève la balise de {point du groupe « balise »}. » ★★ · 200
- « Ramène le document caché à {point du groupe « document »}. » ★★ R · 350
- « Inspecte {2-4} postes radio. » ★★ · 250
- « Observe {lieu} aux jumelles depuis {point haut}. » ★ · 150
- « Rentre par l'extraction {zone ouverte au hasard}. » ★ · 150
- « Énigme du jour : {une parmi une liste}, la réponse est quelque part à {ville}. » ★★ · 300
- « Un colis radio a été largué à {point}. Rapporte-le. » ★★★ R · 450

### 6.6 Mila — la contrebandière (commerce) : 16 quêtes

| # | Quête | À faire | ★ | Récompense |
|---|---|---|---|---|
| 1 | Brocante | Lui vendre 3 objets de valeur différents (montre, bague, briquet…) | ★ | prix ×2 |
| 2 | Le colis | Livrer un colis au port en moins de 45 min (perdu à la mort : **un autre joueur peut le ramasser et le livrer à ta place**) | ★★ | 300 |
| 3 | Le grand bijou | Un objet de valeur **Rare ou mieux** (couronne, crâne de cristal, sceptre, calice…) | ★★★ | 800 |
| 4 | Faux papiers | Les tampons de la mairie de Belcourt (point) | ★★ R | 400 |
| 5 | Le livreur | Livrer 3 colis à 3 points, dans l'ordre, en une seule sortie | ★★★ | 700 |
| 6 | Le marché du jour | Apporter un des 5 objets recherchés aujourd'hui (liste qui change chaque jour, **n'importe quel objet du jeu**) | ★–★★★ | selon l'objet |
| 7 | La caisse noire | Ouvrir un coffre à code au port : le code est chez 3 « contacts » (3 indices dans 3 villes) | ★★★ | 700 |
| 8 | Le casse | Ouvrir la **salle à carte rouge** de l'aérodrome et rapporter la caisse (un seul porteur) | ★★★★ R | 2 000 |
| 9 | Associés | Verser 2 000 hérals à la banque de ton clan | ★★ | 300 + réputation |
| 10 | Territoire | Que ton clan tienne une zone pendant 1 h | ★★★ | 800 |
| 11 | Le ravitaillement | Lancer un ravitaillement de clan | ★★ | 400 |
| 12 | Le piège | Un contrat « trop beau » : poser les bonnes questions dans le dialogue pour découvrir l'arnaque (sinon des zombies attendent au point de livraison) | ★★ | 500 |
| 13 | Collectionneuse | Lui vendre tous les objets de valeur différents (30) | ★★★★ | 3 000 |
| 14 | Le diamant | Un diamant | ★★★ | 1 000 |
| 15 | Le convoi | Livrer un colis lourd (on ne peut pas courir avec) d'un bout à l'autre de la carte | ★★★★ | 1 500 |
| 16 | Associé de Mila | Avoir livré 30 colis | ★★★★ | titre « Contrebandier » + meilleurs prix |

**Contrats de Mila** : livrer un colis à {point} en {temps} · lui vendre {objet de valeur} · un objet du **marché
du jour** · récupérer un colis perdu par un autre joueur · ouvrir la cachette de {point du groupe « cachette »}.

### 6.7 Lucky — le casino : 14 quêtes

Règle : jamais obligé de perdre beaucoup ; petites mises, remboursées par la récompense.

| # | Quête | À faire | ★ | Récompense |
|---|---|---|---|---|
| 1 | Tour de piste | Jouer à 3 jeux différents (mise d'au moins 10) | ★ | 100 |
| 2 | Tous les jeux | Jouer au moins une fois à chaque jeu du casino | ★★ | 300 |
| 3 | Rouge ou noir | Gagner 3 fois à la roulette | ★★ | 200 |
| 4 | Vingt-et-un | Faire un blackjack | ★★ | 200 |
| 5 | Le crash | Encaisser au crash à ×3 ou plus | ★★ | 250 |
| 6 | Le démineur | Ouvrir 8 cases aux mines sans sauter | ★★ | 250 |
| 7 | Le stoïque | Perdre 5 parties de suite sans quitter la table (petite mise) | ★ | 200 |
| 8 | Face à face | Battre un joueur à pierre-feuille-ciseaux ou à la bataille navale | ★★ | 250 |
| 9 | Les courses | Gagner une course | ★★ | 250 |
| 10 | Le flambeur | Un gain à ×10 ou plus, n'importe quel jeu | ★★★ | 500 |
| 11 | La dette | Un joueur lui doit de l'argent : retrouver son cadavre (zone floue) et sa montre à gousset | ★★★ R | 600 |
| 12 | Les machines | Jouer 20 fois à chaque machine (Olympe, Souffle du loup, La Niche) | ★★ | 300 |
| 13 | Le chanceux | Gagner 5 parties de suite à n'importe quel jeu | ★★★ | 700 |
| 14 | Roi du casino | Avoir joué 500 parties | ★★★★ | titre « Flambeur » |

**Contrats de Lucky** : « {5-10} parties de {jeu} » · « gagne une fois à {jeu} » · « un gain à ×{3-5} » · « bats un
joueur ».

### 6.8 Commandant Varga — les opérations : 18 quêtes

| # | Quête | À faire | ★ | Récompense |
|---|---|---|---|---|
| 1 | Recrue | Tuer 25 zombies | ★ | 100 |
| 2 | Plaques d'identité | 5 plaques de soldats morts (groupe de points « soldat », 5 tirés au hasard) | ★★★ R | 600 |
| 3 | La salle verte | Entrer dans une salle à carte verte | ★★ | 300 |
| 4 | La salle bleue | Entrer dans une salle à carte bleue | ★★★ | 600 |
| 5 | La salle rouge | Entrer dans une salle à carte rouge | ★★★★ | 1 200 |
| 6 | Tenir la gare | Tenir 5 min la zone d'une gare (zombies qui arrivent) | ★★ | 400 |
| 7 | Secteur Bravo | Tuer 60 créatures dans une ville T5 | ★★★ | 700 |
| 8 | Exercice | Participer à un ClashWar | ★★ | 400 |
| 9 | Discipline | Gagner 5 duels | ★★★ | 600 |
| 10 | Les déserteurs | 3 carnets de déserteurs (3 cadavres, zones floues) ; le dernier donne une cachette (point sans marqueur) | ★★★ R | 900 |
| 11 | Le territoire | Que ton clan capture une zone | ★★★ | 800 |
| 12 | Ymirak | Participer à la mort d'Ymirak | ★★★★ | 1 500 |
| 13 | Sylvorn | Participer à la mort de Sylvorn | ★★★★ | 1 500 |
| 14 | Mère des Ruines | Participer à sa mort | ★★★★ | 1 500 |
| 15 | Porteur de Peste | Participer à sa mort | ★★★★ | 1 500 |
| 16 | The Giant | Atteindre la manche 15 aux Zombies | ★★★ | 800 |
| 17 | Sujet N-07 | Battre le boss final de The Giant | ★★★★ | 2 500 |
| 18 | Officier | Avoir fini toute la suite | ★★★★ | titre « Officier » |

**Contrats de Varga** : nettoyer {ville} ({30-60} créatures) · tenir {gare / pont / point} {3-5} min · entrer dans
une salle {couleur} · gagner un duel · participer à un boss · plaques de soldats ({1-3}).

---

## 7. Les quêtes à plusieurs PNJ (grandes histoires)

Elles passent d'un PNJ à l'autre et donnent un fil à suivre. Elles s'ouvrent au milieu des suites.

1. **La loco** (Ferraille → Nadia → Jack → Ferraille) : remettre en route la locomotive du spawn. Ferraille veut la
   vanne de la raffinerie ; Nadia sait où sont les plans (bureau de la gare de Tcherno) ; Jack doit « sécuriser » le
   dépôt (tenir 5 min). **Événement serveur** en même temps : barre commune au spawn (2 000 composants donnés par tous
   les joueurs). À 100 % : tous les participants ont le titre « Cheminot ».
2. **Patient zéro** (Jacqueline → Nadia → Varga) : retrouver d'où vient l'épidémie : dossier de l'hôpital, registre
   de la prison, puis le Porteur de Peste.
3. **Le Vieil Oskar** (Nadia → Oskar, caché sur la carte) : après la bande audio, il faut trouver sa cabane (aucun
   marqueur, 3 écoutes pour trianguler). Oskar a sa propre suite de 10 quêtes « mystiques » : pierres gravées la nuit,
   offrandes (amulette, idole dorée), une nuit entière dans l'arctique, Ymirak et Sylvorn.
4. **Le secret du terminus** (le Doyen, tout à la fin) : le train qui a déraillé dans la cinématique transportait autre
   chose que des gens. Ouvert quand le joueur a fini 3 suites complètes. Fin à choix (quel PNJ reçoit la cargaison),
   avec un titre différent selon le choix.

---

## 8. Ce qui est faisable, et avec quoi

Tout ce qui est dans ce document est prévu pour être **faisable** : le mod de quêtes sait lui-même compter ce qui se
passe dans Minecraft, et pour le reste il écoute **nos** mods (dont on a les sources).

| Ce que la quête vérifie | Comment | État |
|---|---|---|
| parler, donner un objet (n'importe lequel, quantité, rareté, usure), montrer un objet | mod de quêtes | ✔ |
| points de quête (ramasser, indice, endroit, code, dépôt, tenir) | mod de quêtes | ✔ |
| tuer des zombies / créatures, à quelle distance, de nuit, sans être touché | Minecraft | ✔ |
| tuer avec telle arme ou un silencieux | lecture de l'arme TaCZ en main | ✔ |
| tuer un boss | nos boss | ✔ |
| mort du joueur, objet perdu, temps sans mourir | Minecraft | ✔ |
| conteneur de loot fouillé (palier, modèle) | HeraliZ Loot | ➕ |
| extraction réussie (laquelle) | Extraction | ➕ |
| partie de casino (jeu, gain, multiplicateur) | Casino | ➕ |
| duel gagné, ClashWar, manche des Zombies, N-07 | Matchs | ➕ |
| réanimation | Réanimation | ➕ |
| soin utilisé (bandage, attelle) | Tactique | ➕ |
| zone de gaz / radiation / froid | Décors | ➕ |
| salle à carte | Bunker | ➕ |
| territoire, ravitaillement, banque de clan | Clans | ➕ |
| pièces CRac posées | CRac | ➕ |
| trajet en train | Utils | ➕ |
| observer aux jumelles | Airstrike | ➕ |
| stand de tir du camp (cibles) | nouvelle cible du mod de quêtes | ➕ |

✔ = rien à ajouter ailleurs · ➕ = quelques lignes dans le mod concerné pour prévenir le mod de quêtes.

**Ordre de fabrication conseillé** : 1) PNJ, dialogues, `/quetes`, suivi à l'écran, points de quête, baguette,
objets de quête, quêtes « donner / tuer / ramasser / endroit » ; le Doyen et Jack en entier ; 2) contrats ;
3) Jacqueline, Ferraille, Nadia ; 4) les signaux des autres mods ; Mila, Lucky, Varga ; 5) grandes histoires, Oskar.

---

## 9. Questions restantes

1. Les **noms** des PNJ (Doyen, Jack, Jacqueline, Ferraille, Nadia, Mila, Lucky, Varga) : on garde ?
2. **Objet de quête à la mort** : perdu sur le cadavre (proposé, plus de tension) ou gardé ?
3. Les **montants** de hérals vous paraissent-ils bien (ni trop, ni trop peu) ?
4. On commence par **le Doyen et Jack** pour essayer, puis les autres ?
5. Les **écrans** (`/quetes`, dialogue, suivi à droite) : d'abord des images à valider, comme d'habitude.
