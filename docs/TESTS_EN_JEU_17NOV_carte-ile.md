# Carte en île : à faire et à tester en jeu (17/11)

Ce qui est en ligne avec le redémarrage du 17/11 : eau du labo sous-marin (sans tenue de plongée, 2,5 s d'air puis
2,5 cœurs par seconde, noir en profondeur), vitres du labo incassables, zones (capture, extraction, construction) plus
jamais vues à travers les blocs, éponge mouillée = éponge, portes des bunkers, cartes et lecteurs des deux bunkers.
**Rien de tout cela n'a été testé en jeu** : tout est à vérifier ci-dessous.

Fait depuis la console juste après le redémarrage (lot `serveur/commandes/bunkers_17nov.txt`) : « Bunker des Bois » et
« Bunker du Pic » retirés de la carte M ; six zones d'extraction renommées (Plage du Pic, Plage est du Pic, Forêt du
Grand Bois, Clairière du Grand Bois, Grande Prairie, Forêt de Val-Fleuri).

## 1. Portes des deux bunkers

| Bunker | Porte (écluse à air ronde) | Lecteur à poser | Carte |
|---|---|---|---|
| Bunker des Bois | -281 85 161 | `heralisz_bunker:card_reader_bunker_bois` (orange) | `heralisz_bunker:keycard_bunker_bois` |
| Bunker du Pic | 838 120 -961 | `heralisz_bunker:card_reader_bunker_pic` (violet) | `heralisz_bunker:keycard_bunker_pic` |

Pour chaque bunker, en créatif :
1. Retirer les lecteurs déjà posés à côté de la porte (en créatif ils se cassent) : **la porte de bunker s'ouvre avec
   n'importe quel lecteur posé à 2 blocs**, un lecteur rouge oublié l'ouvrirait avec une carte rouge.
2. `/give @s heralisz_bunker:card_reader_bunker_bois 2` (ou `_pic`), puis poser un lecteur **dehors** et un **dedans**,
   à 2 blocs au plus de la porte.
3. Regarder la porte (ou un de ses lecteurs), à 8 blocs au plus, et taper :
   - `/salle bunker ajouter` → annonce « La porte du bunker a été ouverte ! Elle reste ouverte 20 min. » ;
   - ou `/salle bunker ajouter Bunker des Bois` → annonce « La porte du Bunker des Bois a été ouverte ! … ».
   La réponse dit combien de lecteurs sont à côté (il en faut 2).
4. `/salle bunker` en regardant la porte : « porte de bunker, fermée, 2 lecteur(s) à côté ».

À tester (de préférence en **survie** : en créatif, la carte ne s'use pas) :
- [ ] Clic droit sur la porte : elle ne s'ouvre pas (message « passe une carte dans le lecteur à côté »).
- [ ] `/give @s heralisz_bunker:keycard_bunker_bois`, glisser la carte dans le lecteur du dehors : cinématique, la porte
      s'ouvre, **tout le serveur** lit l'annonce dans le tchat.
- [ ] La carte du Pic dans le lecteur des Bois : refusée (et l'inverse) ; une carte rouge : refusée.
- [ ] `/salle bunker` : « ouverte, se ferme dans 19 min … ».
- [ ] 5 minutes avant la fin, tout le serveur lit dans le tchat « La porte du bunker se ferme dans 5 min : sans carte,
      plus de sortie ! » ; au bout de 20 min, la porte se ferme ; qui est dans le passage est repoussé.
- [ ] Enfermé dedans : la carte dans le lecteur du dedans rouvre la porte (20 min de plus, nouvelle annonce).
- [ ] Infobulle de la carte : « Ouvre les lecteurs du Bunker des Bois », « Passages restants : 2 » ; au 2e passage,
      elle disparaît de l'inventaire et se casse dans le lecteur (bruit et éclats, comme un outil de Minecraft).
- [ ] Un redémarrage pendant que la porte est ouverte : elle garde son heure de fermeture.

Réglages (`config/heralisz_bunker-common.toml`) : `[bunkers] ouvertureMinutes = 20`, `avertirSecondes = 300` ;
`[keycards] usesBunker = 2`. Les cartes sortent des coffres T7 et des salles rouges (environ 1 coffre T7 sur 100 donne
l'une des deux) ; elles sont Légendaires et suivies par l'anti-duplication.

## 2. Usure des cartes (18/11)

Passages avant que la carte casse : **verte 4, bleue 3, rouge 2, cartes des bunkers 2** (`[keycards]` de
`config/heralisz_bunker-common.toml`). En créatif, la carte ne s'use pas : essayer en survie.
- [ ] Infobulle : « Passages restants : N » ; barre d'usure sous la carte après le premier passage.
- [ ] Au dernier passage, la porte s'ouvre quand même ; la carte disparaît de l'inventaire et se casse dans la fente du
      lecteur à la fin de la lecture (bruit d'objet cassé, éclats de la carte).
- [ ] Aucune réparation : enclume, meule, Raccommodage, recycleur (refusée), table d'armurier.

## 3. Salles à carte

Coordonnées données le 17/11 (lecteur posé devant la porte). La porte doit aller avec la couleur du lecteur :
- verte : Trappe de silo, Porte cargo, Porte coulissante QE, Porte coup-de-feu ;
- bleue : Porte d'accès sécurisé, Porte de confinement QE, Trappe étanche ;
- rouge : Porte blindée Vault-Tec, Grande porte de véhicule.

| # | Lieu | Coordonnées | Couleur | Commande de création |
|---|---|---|---|---|
| 1 | Arctique | -160 79 -1824 | rouge | `/salle creer arctique` |
| 2 | Plateforme pétrolière | -2146 85 259 | rouge | `/salle creer oilrig` |
| 3 | Aérodrome | -51 67 -698 | rouge | `/salle creer aerodrome` |
| 4 | Base Sentinelle | 1072 68 -294 | bleue | `/salle creer sentinelle` |
| 5 | Labo sous-marin | 1956 21 2067 | bleue | `/salle creer labo` |
| 6 | Zone radioactive (Radio) | 1388 83 1407 | bleue | `/salle creer radio` |
| 7 | Silo | -1596 103 -783 | bleue | `/salle creer silo` |
| 8 | Port-Margot | -1775 72 923 | verte | `/salle creer portmargot` |
| 9 | Bellerive | -481 106 1704 | verte | `/salle creer bellerive` |
| 10 | Clairval | 121 72 532 | verte | `/salle creer clairval` |
| 11 | Belcourt | -960 100 -783 | verte | `/salle creer belcourt` |
| 12 | Prison | 1605 68 -1143 | verte | `/salle creer prison` |

Il en manque pour le plan 8 vertes / 5 bleues / 3 rouges : **1 bleue** (proposée : Mirage) et **3 vertes** (proposées :
Valmont, Marbois, Gaz).

Pour chaque salle :
1. `/tp @s <coordonnées>` puis `/salle baguette` (une seule fois suffit).
2. Clic gauche sur un bloc d'un coin de la salle, clic droit sur le coin opposé : la boîte doit prendre **toute la salle
   et ses conteneurs de loot** ; la porte doit être à 3 blocs au plus de la boîte. `/salle selection` donne la taille.
3. `/salle creer <nom>` (tableau ci-dessus) : la couleur est prise de la porte. Si la réponse dit « Aucune porte à carte
   autour » : vérifier la porte, ou donner la couleur (`/salle creer silo bleue`).
4. Si la porte n'est pas liée : la regarder et `/salle lier <nom>`.
5. `/salle voir <nom>` : contour montré 30 s. `/salle liste` : toutes les salles, avec leur état.
6. Les conteneurs de loot dans la boîte sont remplis par la salle, tous à la fois, chacun à SON niveau (18/11 : plus de
   niveau imposé par la couleur ; avant, tout passait en T5 / T6 / T7). Pas de conteneur dedans = pas de loot.
   Après la mise à jour : `/salle recharger <nom>` pour remplir tout de suite une salle à son vrai niveau.

Quand c'est fait, le dire : la liste complète est vérifiée depuis la console (`salle liste`).

À tester :
- [ ] Carte de la bonne couleur : la porte s'ouvre, 20 s pour entrer (18/11), elle reste ouverte tant que quelqu'un est dedans,
      puis se ferme 4 s après la sortie du dernier (18/11, avant 2 s) ; mauvaise couleur : refusée.
- [ ] Loot de la salle au bon niveau ; il revient 30 min (verte), 2 h (bleue), 4 h (rouge) après la sortie du dernier.
- [ ] Déconnexion dans une salle : à la reconnexion, on est devant sa porte.

## 4. Quêtes de Varga : plus rien à poser (18/11)

Décision du propriétaire du 18/11 : les insignes, les carnets et la sacoche des déserteurs et la boîte noire se trouvent
dans les caisses pendant que l'étape est en cours ; les salles à carte sont reconnues toutes seules. Aucun point de quête.
- [ ] Quête « Le 7e régiment » : en ouvrant des caisses, « Tu trouves : Insigne du 7e régiment » à la fermeture de la
      caisse (bruit de ramassage tout de suite) ; jamais plus de 20 caisses d'affilée sans insigne ; 5 puis les rapporter.
- [ ] « Les déserteurs » : 3 carnets (le carnet s'ouvre à la fermeture de la caisse), puis la sacoche dans une caisse T4 ou
      mieux.
- [ ] « La boîte noire » : seulement dans les caisses de la zone de radiation de Radio (tenue hazmat).
- [ ] « Fin de classe » et « La salle rouge » : entrer dans n'importe quelle salle à carte de la bonne couleur.
- [ ] Mourir avec un insigne avant de le rapporter : il se retrouve dans les caisses (« Tu retrouves : … »).

## 5. PNJ du spawn

`/pnj liste` doit montrer : varga, mila, jack, jacqueline, doyen, commissaire, batisseur, recycleur, guide,
contremaitre. Pour un PNJ absent : se placer à l'endroit voulu, regard dans la bonne direction, `/pnj creer <id>`
(ex. `/pnj creer contremaitre`). Le déplacer : `/pnj deplacer <id>` (il vient là où on se tient). Le retirer :
`/pnj retirer <id>`.

## 6. Arrivée sur la carte, coffres T7, événements

- [ ] `/depart info` doit dire « prêt » ; `/depart liste` : les points et zones d'arrivée doivent être **sur l'île**.
      Sinon : `/depart zone ajouter <nom> <rayon>` (au centre d'un endroit dégagé) ou `/depart point ajouter <nom>`, puis
      `/depart essai tp` pour essayer.
- [ ] `/heralizloot point liste` : points des coffres T7 (anciens points de l'autre carte à retirer :
      `/heralizloot point retirer <nom>`).
- [ ] Événements (largage public toutes les 3 h 30, hélicoptère) : lieux tirés au hasard sur la carte, rien à poser ;
      `/event liste` montre le planning. Vérifier qu'un largage tombe bien sur l'île.

## 7. Le reste à vérifier

- [ ] Carte M : plus de « Bunker des Bois » ni de « Bunker du Pic » ; noms des extractions à jour (`/extraction list`).
- [ ] Labo sous-marin, **sans** tenue de plongée : 5,5 s d'air (18/11, avant 2,5), puis 2 cœurs par seconde (avant 2,5) ;
      sous y 52, tout noir.
      **Avec** la tenue : on respire et on voit correctement.
- [ ] Vitres du labo : un tir ne les casse pas (`/vitres pourquoi` en regardant une vitre dit pourquoi). 18/11 : elles
      cassaient encore ; maintenant une vitre qui touche l'eau ne casse jamais, partout sur la carte, et le labo est protégé
      même si le réglage n'est pas lu. Si une vitre du labo casse encore : `/vitres pourquoi` en la regardant, et m'envoyer
      ce qu'il répond.
- [ ] Téléportation (18/11), avec un compte **qui n'est pas du staff** (le staff passe outre) : dans une ville à drapeau,
      l'aérodrome, la prison, la radio…, et dans les zones de loot (`/tpa zone liste` : plateforme pétrolière, labo
      sous-marin), `/tpa <joueur>` est refusé ; une demande reçue ne s'accepte pas (elle attend : sorti de la zone, on
      l'accepte encore) ; on ne peut pas se téléporter vers quelqu'un qui y est. Régler une zone de loot : debout au centre,
      `/tpa zone ajouter <rayon> <nom>` (même nom : remplacée) ; `/tpa zone retirer <nom>`.
- [ ] Récolte (18/11) : 120 arbres, 90 gisements de pierre, 40 de métal, 14 de titane sur la carte (avant 30, 24, 10, 4),
      qui reviennent 2 fois plus vite ; 7 sur 10 apparaissent autour d'un joueur de la carte, entre 100 et 450 blocs de
      lui (jamais sous ses yeux), les autres n'importe où. `/recolte info` : combien sont posées et combien attendent. Au
      démarrage, elles sont tirées partout : elles se rapprochent des joueurs au fil des minutes.
- [ ] Tenues (18/11) : plongée, arctique et hazmat sortent des conteneurs de T3 à T7 (environ 2 de chaque pour 100
      conteneurs T5, 3 pour 100 en T6) ; les versions renforcées en T6 et T7, plus rares.
- [ ] Zones de capture et d'extraction : plus d'effet « rayons X », plus de traits vus à travers les blocs.
- [ ] Éponge mouillée : même texture que l'éponge.
- [ ] Drapeaux des villes : `/territoire info` ; une capture.
- [ ] Une extraction ouverte : la tenir, retour au spawn.
