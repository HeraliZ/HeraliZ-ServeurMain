# Essais en jeu du chantier « divers » (31/10)

Rien n'a été testé en jeu ni sur le vrai Ptero. Jars pas fabriqués ici (Gradle bloqué) ; aucun code Java n'a changé.

## 1. Icônes du Scorpion EVO 3 et du PP-19 Bizon (sans crosse)

Planche : `maquettes/icones_armes/sans_crosse_evo3_pp19_31oct.png`.
- [ ] Inventaire : le Scorpion EVO 3 et le PP-19 n'ont plus de crosse sur leur icône (comme dans la main du joueur).
- [ ] Les autres armes n'ont pas changé (seules ces deux images ont été refaites).
- [ ] Le launcher a bien pris le nouveau dossier `tacz` (sinon l'ancienne icône reste chez le joueur).

Ensuite (décision du propriétaire, 31/10) : le fusil à canon scié (`db_short`) et sa version tactique
(`db_short_tactical`) aussi, planche `maquettes/icones_armes/sans_crosse_canon_scie_31oct.png`.
- [ ] Inventaire : les deux fusils à canon scié n'ont plus la longue crosse en bois sur leur icône.

Les autres armes qui ont des pièces sous
`attachment_adapter` (AK, M4, SCAR, RPK…) montrent leur crosse sur l'image d'origine : gardées telles quelles.

## 2. Crâne de cristal, sceptre et calice dans les coffres T7 (Loot 0.14.3)

Chiffres : 2 objets Uniques pour 100 coffres T7, soit environ **1 coffre T7 sur 50** (simulé : 1 sur 49,9) ; les trois
à parts égales. Seulement dans la table T7 (hélicoptère abattu, points T7 du staff…), jamais en T1 à T6, ni sur les
zombies, ni dans le largage public, ni dans les caisses.
- [ ] `/heralizloot tables` (ou redémarrage) : pas d'erreur de lecture de `t7.json`.
- [ ] Faire apparaître des coffres T7 (`/heralizloot point declencher <nom>` ou `apparaitre`) jusqu'à en voir un :
      l'objet se révèle avec la couleur et la durée « Unique » (rouge, le plus long).
- [ ] `/clan don` avec l'objet trouvé dans un T7 : accepté, 2 500 XP (ou demande de confirmation au-delà du plafond du jour).
- [ ] Mila le rachète (prix d'un Unique) ; le recycleur le refuse.
- [ ] Spartan le numérote (anti-duplication) : un deuxième don de la même copie est refusé.
- [ ] Le reste du T7 ne change pas (même nombre d'objets, fusée d'extraction toujours aussi rare).

Remarque : le nombre réel dépend du nombre de coffres T7 ouverts (inconnu ; compter les lignes `conteneur_t7` de
`heralisz_clans/saison_journal.log` la première semaine). Avec ~5 coffres T7 par jour, un Unique tous les 10 jours
environ. Pour changer : `UNIQUES_T7_POUR_100` de `outils/loot_armes.py`, puis relancer l'outil.

## 3. Workflow « Remplacer la carte de loot »

Ne rien lancer en vraie exécution sans avoir lu le résumé d'un essai à blanc.
- [ ] Onglet Actions → « Remplacer la carte de loot » → « Run workflow » sans rien cocher : le résumé liste le monde
      (`/dayz_map`), le spawn trouvé, et dit « dossier /carte_nouvelle introuvable » tant que la carte n'y est pas
      (normal ; rien n'est changé).
- [ ] Avec la nouvelle carte dans `/carte_nouvelle/` (au moins `region/`) : essai à blanc, relire les listes « mis de
      côté », « mis en place », « gardé » (le spawn `heralisz:spawn` doit être dans « gardé »).
- [ ] Case « executer » cochée avec une phrase fausse : erreur, rien n'est changé.
- [ ] Avant la vraie exécution : `/wipe tout` en jeu si la nouvelle carte arrive avec un wipe (question §9 de
      `docs/NOUVELLE_CARTE.md`), place libre pour une sauvegarde du Ptero.
- [ ] Après la vraie exécution : vérifications du §7 de `docs/NOUVELLE_CARTE.md` (réveil au spawn, borne de départ,
      `/spawn`, extraction qui ramène au spawn, mort, `/home`, ClashWar, carte M…), puis tout ce qu'il faut refaire en
      jeu (§6).

Essayé seulement contre un faux Ptero sur la machine de Claude : essai à blanc (aucun changement), refus sans la phrase
ou hors de `main`, vraie exécution (bon résultat : spawn, `level.dat`, `playerdata/` et données gardées), retour en
arrière quand un renommage échoue. **Pas essayés** : le vrai Ptero (SFTP de Wings, API du panel, sauvegardes), la
source « release » (téléchargement de l'archive).

## Questions pour le propriétaire

1. (Réglé le 31/10 : crosse enlevée des icônes des deux fusils à canon scié.)
2. Uniques : 1 coffre T7 sur 50 vous va, ou plus rare / plus fréquent ?
3. Nouvelle carte : la mettre dans `/carte_nouvelle/` du Ptero (conseillé) ou dans une Release GitHub ?
