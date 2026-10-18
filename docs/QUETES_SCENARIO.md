# HeraliZ — quêtes principales (scénario v3, 18/10)

> Choix du propriétaire (18/10) : **un seul PNJ, le Commandant Varga** (7e régiment), qui donne les **quêtes
> principales**. Les 7 autres PNJ (Doyen, Jack, Jacqueline, Ferraille, Nadia, Mila, Lucky) et les contrats sont
> retirés. Chaque quête dit en une phrase quoi faire ; difficulté qui monte ; tout se fait seul ; les quantités ne sont
> pas symboliques. Fichier qui fait foi : `serveur/config/heraliz_quetes/pnj/varga.json` (guide du staff :
> `serveur/config/heraliz_quetes/LISEZMOI.md`). Chiffres à régler après les premiers essais. **Rien testé en jeu.**

## 1. Les règles

- 39 quêtes en 5 chapitres, dans l'ordre ; une seule à la fois chez Varga.
- Fin de chaque chapitre : il faut **payer** pour passer au grade suivant (1 000, 3 000, 5 000, 7 000 hérals) et on gagne
  un **titre** (Recrue, Soldat, Caporal, Sergent, Officier).
- 🔑 = la quête donne une **Clé de Quêtes** (`heralizcrates:quete_key`) en plus des hérals.
- Les objets demandés sont ceux du jeu (soins, composants, objets de valeur selon leur rareté, armes, armures) ; seuls
  les objets « à aller chercher » ont leur propre modèle 3D (insigne du 7e régiment, sacoche des déserteurs, boîte
  noire : `mods/heraliz-quetes/outils/objets.py`).
- « Fouiller » ne demande pas de revenir par une extraction : les conteneurs comptent même si on meurt ensuite.
- La quête « Capturer un territoire avec son clan » est écrite mais **désactivée** (sautée) tant que les territoires
  de la nouvelle carte ne sont pas faits.

## 2. Les quêtes

### Chapitre 1 — Recrue

| # | Quête | Ce qu'il faut faire | Récompense |
|---|---|---|---|
| 1 | Premier tir | Prends une arme à feu et tue 10 zombies avec. | 100 hérals |
| 2 | Fouille | Fouille 5 conteneurs sur la carte. | 100 hérals |
| 3 | Une vraie arme | Montre-moi une arme à feu Peu commune ou mieux. | 150 hérals |
| 4 | Trousse de secours | Rapporte-moi 3 soins, n'importe lesquels. | 150 hérals |
| 5 | Ce qui se revend | Rapporte-moi 5 objets de valeur, n'importe lesquels. | 200 hérals 🔑 |
| 6 | Sans un bruit | Tue 10 zombies au corps à corps. | 200 hérals |
| 7 | Tenir le coup | Reste 15 minutes sur la carte sans mourir. | 250 hérals |
| 8 | Fin de classe | Reviens par une extraction, puis paie-moi 1 000 hérals pour passer Soldat. | 300 hérals 🔑 · titre « Recrue » |

### Chapitre 2 — Soldat

| # | Quête | Ce qu'il faut faire | Récompense |
|---|---|---|---|
| 9 | Le 7e régiment | Retrouve 5 insignes du 7e régiment dans les villes et rapporte-les-moi. | 300 hérals |
| 10 | Nettoyage | Tue 40 zombies. | 300 hérals |
| 11 | Ratissage | Fouille 20 conteneurs sur la carte. | 350 hérals |
| 12 | Pièces de radio | Rapporte-moi 5 batteries et 5 circuits imprimés. | 400 hérals |
| 13 | Meilleure prise | Rapporte-moi 10 objets de valeur Peu communs ou mieux. | 450 hérals 🔑 |
| 14 | Protection | Montre-moi un gilet ou un casque Peu commun ou mieux. | 450 hérals |
| 15 | Poudre | Rapporte-moi 5 tonnelets de poudre et 5 bocaux de poudre. | 500 hérals |
| 16 | Fin de classe | Reste 30 minutes sur la carte sans mourir, puis paie-moi 3 000 hérals pour passer Caporal. | 600 hérals 🔑 · titre « Soldat » |

### Chapitre 3 — Caporal

| # | Quête | Ce qu'il faut faire | Récompense |
|---|---|---|---|
| 17 | Pas de pitié | Achève un joueur à terre. | 600 hérals |
| 18 | Zones dangereuses | Fouille 10 conteneurs T3 ou mieux. | 650 hérals |
| 19 | Ferraille | Rapporte-moi 10 plaques recyclées et 5 plaques soudées. | 700 hérals |
| 20 | Armurier | Fabrique une arme à la table d'armurier. | 750 hérals 🔑 |
| 21 | Patrouille de nuit | Tue 20 zombies pendant la nuit. | 800 hérals |
| 22 | Les déserteurs | Lis les carnets de 3 déserteurs, retrouve leur sacoche cachée et rapporte-la-moi. | 900 hérals |
| 23 | Objets rares | Rapporte-moi 5 objets de valeur Rares ou mieux. | 1 000 hérals |
| 24 | Fin de classe | Entre dans une salle à carte verte, puis paie-moi 5 000 hérals pour passer Sergent. | 1 200 hérals 🔑 · titre « Caporal » |

### Chapitre 4 — Sergent

| # | Quête | Ce qu'il faut faire | Récompense |
|---|---|---|---|
| 25 | Bien équipé | Montre-moi une arme à feu Rare ou mieux et un gilet Rare ou mieux. | 1 200 hérals |
| 26 | Trésors | Rapporte-moi 3 objets de valeur Épiques ou mieux. | 1 300 hérals 🔑 |
| 27 | En silence | Tue 20 zombies avec une arme équipée d'un silencieux. | 1 300 hérals |
| 28 | Coins gardés | Fouille 10 conteneurs T4 ou mieux. | 1 400 hérals |
| 29 | La boîte noire | Va dans la zone de gaz, retrouve la boîte noire et rapporte-la-moi. | 1 500 hérals |
| 30 | Appui aérien | Appelle une frappe aérienne. | 1 500 hérals |
| 31 | Fin de classe | Entre dans une salle à carte bleue, puis paie-moi 7 000 hérals pour passer Officier. | 1 800 hérals 🔑 · titre « Sergent » |

### Chapitre 5 — Officier

| # | Quête | Ce qu'il faut faire | Récompense |
|---|---|---|---|
| 32 | Prendre le terrain | Capture un territoire avec ton clan. *(désactivée pour l'instant)* | 2 000 hérals |
| 33 | Blindage | Rapporte-moi 5 plaques en métal et 3 plaques renforcées. | 2 000 hérals |
| 34 | Tireur d'élite | Tue 10 zombies à plus de 60 mètres. | 2 200 hérals |
| 35 | Pièces de musée | Rapporte-moi 2 objets de valeur Légendaires ou mieux. | 2 400 hérals 🔑 |
| 36 | Arme d'officier | Montre-moi une arme à feu Légendaire. | 2 500 hérals |
| 37 | L'introuvable | Rapporte-moi 1 objet Mythique (composant ou objet de valeur). | 2 800 hérals |
| 38 | La salle rouge | Entre dans une salle à carte rouge. | 3 000 hérals |
| 39 | Dernière mission | Sans mourir : tue 30 zombies, fouille 3 conteneurs T6, puis reviens par une extraction. | 5 000 hérals 🔑 · titre « Officier » |

## 3. Ce que le staff pose en jeu

Points de quête (baguette `/quete baguette <groupe>` ou `/quete point ajouter <groupe>`) : `insigne_varga` (15 et plus,
dans plusieurs villes), `deserteur` (3 et plus), `cachette_deserteurs` (1 à 3), `boite_noire` (dans la zone de gaz),
`salle_verte`, `salle_bleue`, `salle_rouge` (dans chaque salle à carte). Puis `/pnj creer varga` au spawn.

## 4. Comment le serveur voit chaque action

- zombies tués (arme à feu, corps à corps, de nuit, silencieux, à plus de 60 m), conteneurs de HeraliZ Loot fouillés
  (palier lu dans le nom du bloc), extraction (arrivée au spawn), temps sur la carte sans mourir ;
- joueur à terre achevé : mort d'un joueur encore « à terre » (Réanimation), tuée par un autre joueur ;
- arme fabriquée : une arme de plus dans l'inventaire pendant que la Table d'armurier de TaCZ est ouverte ;
- frappe aérienne : une frappe de plus en cours pour ce joueur (Airstrike) ;
- raretés : celle de HeraliZ Loot, ou celle des composants et objets de valeur (couleur de leur nom).
