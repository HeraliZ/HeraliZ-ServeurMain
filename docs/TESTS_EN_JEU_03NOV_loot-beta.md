# Essais en jeu du 03/11 : chantier « loot-beta » (loot plus généreux pendant la bêta)

Demande du propriétaire : « que tout soit un peu plus simple à avoir (sauf couteau dans le T7) : composants, armures,
armes (surtout les rares), fragments de cartes, composants des bases… 2× plus de loot et 2× plus de chance pour chaque
rareté, ou un truc du style (2× c'est peut-être beaucoup) ».

Rien de tout cela n'a été testé en jeu ni compilé avec Minecraft : Gradle ne trouve pas Forge sur la machine de travail
(maven.minecraftforge.net bloqué). Contrôlé ici : syntaxe Java de tous les fichiers touchés, `LootBeta.java` compilé
avec des classes de Minecraft simulées, `outils/verifier_langues.py` : 0 erreur. La vraie compilation sera faite par le
contrôle de branche de GitHub. `outils/verifier.py loot` ne peut pas tourner ici sans le jar fabriqué.

Jar à mettre à jour : **HeraliZ Loot 0.15.0** (serveur ET joueurs : textes de la commande en anglais côté joueur).
Réglage du dépôt changé : `serveur/defaultconfigs/heraliz_loot-server.toml`, nouvelle section `[beta]`.

## Comment ça marche aujourd'hui (avant la bêta)

- **Conteneurs T1 à T7** (rotation des zones, salles à carte du bunker) et **coffres T7** (hélicoptère abattu, points T7
  du staff) : un nombre d'objets est tiré (T1 : 1 à 4, 1,6 en moyenne ; T2 : 2,0 ; T3 : 2,6 ; T4 : 3,5 ; T5 : 5,1 ;
  T6 : 7,6 ; T7 : 7 à 16, 11,3 en moyenne), puis chaque objet est tiré dans la table du palier selon son poids, sans
  doublon (sauf munitions). Tables écrites par `outils/loot_armes.py` : pour 100 conteneurs, tant d'armes, d'armures,
  d'accessoires, de composants… et dans chaque famille une part par rareté (armes T1 : 92 % Commun, 7 % Peu commun,
  0,95 % Rare, 0,04 % Épique, 0,01 % Légendaire ; T5 : 46 / 37 / 15 / 1,75 / 0,25 % ; T7 : 12 / 33 / 38 / 12 / 3,67 %
  et 1,33 % Unique = les couteaux, seulement en T7). Fragments de cartes : environ 0,7 à 1,4 pour 100 conteneurs (T2 à
  T7). Le bonus de loot du clan (zone tenue) s'ajoute à part, inchangé.
- **Conteneurs de soins, nourriture, munitions** : même moteur, 1 à 7 objets selon le niveau.
- **Zombies** : un tirage par zombie compté (rien 15 %, zombies militaires 19 %), plus les fragments tirés à part
  (vert 0,4 %, bleu 0,16 %, rouge 0,09 % par zombie, 6 fragments au plus par joueur et par jour) ; anti-farm inchangé.
- **Pas touchés par le réglage** (ce sont des récompenses, pas du loot trouvé) : largage public (toujours une arme Rare
  ou Épique, composants, munitions, soins : table `airstrike:chests/supply_drop`, `outils/caisses.py`), caisse de
  ravitaillement des territoires (10 à 14 tirages, `serveur/config/heralisz_clans/ravitaillement.json`), quêtes du jour
  (une récompense tirée par quête, `outils/quetes_journalieres.py`), caisses de la boutique, des votes et des boss.

## Ce que fait le réglage `[beta]` (actif par défaut : 1,5× et 1,5×)

Appliqué au moment du tirage : les tables ne sont pas réécrites, et `actif = false` rend le loot d'avant.

- **Quantité ×1,5** : nombre d'objets d'un conteneur multiplié, arrondi au hasard (3 objets → 4 ou 5) ; jamais plus de
  20 objets (`objets_max`) dans un conteneur de 27 cases. Zombies : un zombie sur deux fait un second tirage.
- **Raretés hautes ×1,5** : Rare, Épique et Légendaire 1,5 fois plus fréquents ; la place est prise sur les objets
  ordinaires, qui restent pourtant au moins aussi nombreux qu'avant dans un conteneur grâce à la quantité en plus.
- **Jamais touchés** : les Uniques (couteaux du T7, crâne, sceptre, calice), les objets à vie et la liste `exclus`
  (`heralisz_skins:*` = les couteaux) gardent EXACTEMENT leur chance par coffre d'aujourd'hui (3,86 pour 100 coffres T7).
- **Plafonds** : jamais plus de 100 % ; une rareté ne passe jamais devant celle d'en dessous ; rien sur les prix, le
  recyclage ni la couleur des objets (seulement ce qu'on trouve).
- **Fragments des zombies** : chance ×1,5 (comme leur rareté), plafond de 6 par jour inchangé. Ceux des conteneurs
  suivent la quantité et leur rareté.

### Avant / après (calcul `python outils/loot_beta.py`, mêmes règles que le serveur)

Part de chaque rareté parmi les objets tirés ; « rares+ » = objets Rare, Épique ou Légendaire par conteneur.

| Table | Objets par conteneur | Rare | Épique | Légendaire | Rares+ par conteneur | Fragments pour 100 |
|---|---|---|---|---|---|---|
| T1 | 1,61 → 2,41 | 3,8 → 5,7 % | 1,05 → 1,6 % | 0,01 → 0,02 % | 0,08 → 0,18 (×2,25) | 0 → 0 |
| T2 | 2,01 → 3,01 | 5,5 → 8,3 % | 1,3 → 1,95 % | 0,03 → 0,05 % | 0,14 → 0,31 (×2,25) | 0,70 → 1,57 |
| T3 | 2,56 → 3,84 | 9,6 → 14,4 % | 1,6 → 2,4 % | 0,06 → 0,09 % | 0,29 → 0,65 (×2,25) | 0,85 → 1,91 |
| T4 | 3,48 → 5,21 | 15,8 → 23,7 % | 2,35 → 3,5 % | 0,11 → 0,17 % | 0,64 → 1,43 (×2,25) | 1,25 → 2,81 |
| T5 | 5,05 → 7,58 | 21,0 → 31,4 % | 3,7 → 5,55 % | 0,27 → 0,40 % | 1,26 → 2,84 (×2,25) | 1,37 → 3,08 |
| T6 | 7,63 → 11,45 | 25,9 → 38,8 % | 4,8 → 7,2 % | 0,52 → 0,78 % | 2,38 → 5,36 (×2,25) | 0,70 → 1,57 |
| T7 | 11,34 → 16,50 | 38,0 → 49,1 % | 10,9 → 14,1 % | 2,55 → 3,29 % | 5,84 → 10,99 (×1,88) | 1,19 → 2,25 |
| Zombie | 0,85 → 1,28 | 11,0 → 16,6 % | 0,88 → 1,32 % | 0 → 0 % | 0,12 → 0,27 (×2,25) | vert 0,4 → 0,6 % |
| Zombie militaire | 0,81 → 1,22 | 23,7 → 35,5 % | 2,05 → 3,07 % | 0 → 0 % | 0,26 → 0,58 (×2,25) | rouge 0,09 → 0,13 % |

Les deux multiplicateurs se cumulent : avec 1,5× et 1,5×, on trouve environ **2,25 fois plus** d'objets Rare, Épique et
Légendaire par conteneur (T7 : 1,9×, à cause du plafond de 20 objets et des Uniques qui ne bougent pas), et 1,5 fois
plus d'objets en tout. Avec **2× et 2×** : 2× plus d'objets et environ **4 fois plus** de raretés hautes (T5 : 5,05 →
10,1 objets, Rare 21 → 42 %, 1,26 → 5,05 rares+ par conteneur ; T7 : 11,3 → 19,1 objets, 2,3× rares+ ; zombies : 1,7
objet par zombie, fragments ×2). Pour voir d'autres valeurs : `python outils/loot_beta.py --quantite 2 --raretes 2`.

### Régler, couper

- Fichier du dépôt `serveur/defaultconfigs/heraliz_loot-server.toml`, section `[beta]` : `quantite`,
  `chances_raretes_hautes`, `chances_peu_commun` (1 = inchangé), `objets_max`, `exclus`. La mise en ligne le recopie
  dans le monde ; le serveur le relit tout seul quand il change (pas de redémarrage). Les conteneurs déjà remplis
  gardent leur contenu : le changement se voit au prochain remplissage.
- **Fin de la bêta** : `actif = false`.
- En jeu (staff) : `/heralizloot beta` donne l'état et, pour T1 à T7 et les zombies, Rare / Épique / Légendaire avant →
  après, calculés sur les vraies tables et raretés du serveur.

## À vérifier en jeu

- [ ] `/heralizloot beta` : « Loot de la bêta : objets x1.5 (au plus 20 par conteneur), Rare / Épique / Légendaire
      x1.5… », puis une ligne par table avec des chiffres proches du tableau ci-dessus (à 0,1 % près).
- [ ] Fouiller une dizaine de conteneurs T2–T3 puis T5–T6 remplis APRÈS la mise à jour : plus d'objets qu'avant (T3 :
      3,8 en moyenne au lieu de 2,6 ; T6 : 11,5 au lieu de 7,6), jamais plus de 20, plus d'objets bleus et violets.
- [ ] Coffre T7 (`/heralizloot apparaitre <type> ~ ~ ~` ou un point T7) : 10 à 20 objets, l'identification un par un
      marche jusqu'au bout (elle dure un peu plus longtemps, il y a plus d'objets).
- [ ] Salle à carte du bunker rechargée : ses conteneurs se remplissent avec plus d'objets.
- [ ] Zombies : `/heralizloot zombies simuler 10000` (commande présente seulement avec le mod de banc d'essai) :
      « rien » vers 8 à 9 % au lieu de 15 % ; fragments attendus vert 60, bleu 24, rouge 13 environ. Sans banc : tuer
      une trentaine de zombies : environ un sur trois lâche deux objets (avant : jamais).
- [ ] Couper : mettre `actif = false` dans `serverconfig/heraliz_loot-server.toml` du monde (ou dans le dépôt puis
      mise en ligne) → `/heralizloot beta` dit « coupé » sans redémarrer ; les conteneurs remplis ensuite redeviennent
      comme avant. Remettre `actif = true`.
- [ ] Changer `quantite = 2` à chaud : `/heralizloot beta` le montre aussitôt.
- [ ] Prix de Mila, recycleur, couleurs des objets : inchangés.
- [ ] Journal du serveur : aucune erreur « LootBeta » ou « tables de loot » au démarrage et à `/recharger loot`.

## Pas fait (questions pour le propriétaire)

- Largage public, caisse de ravitaillement des territoires, quêtes du jour, caisses : non touchés (récompenses, déjà
  garanties). À dire si eux aussi doivent donner plus pendant la bêta.
- Les conteneurs actifs par zone (rotation) ne changent pas : il y a autant de conteneurs remplis, chacun plus riche.
