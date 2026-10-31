# Essais en jeu du 31/10 — chantier « réglages »

Rien de ce qui suit n'a été testé en jeu ni compilé par Gradle ici (Forge inaccessible depuis la machine de travail) :
la fabrication complète et les contrôles se font au contrôle de branche de GitHub. Vérifié ici : syntaxe des fichiers
Java, logique de la section `membres` de `niveaux.json` (petit essai hors jeu), langues (0 erreur), contrôle des marchands.

Versions : Bases (CRac) 1.29.3-beta, Clans 3.10.0, Noyau 1.0.7.

## 1. Une seule table d'armurier par base (Bases)

- [ ] Dans sa base (zone de l'armoire), poser une table d'armurier : elle se pose (600 PV, comme avant).
- [ ] En poser une 2e dans la même base : refusée, message rouge au-dessus de la barre d'objets « Cette base a déjà sa
      table d'armurier (1 au plus par base). » ; la table reste dans la main, aucun bloc fantôme.
- [ ] Ramasser la 1re (touche de ramassage ou marteau), puis la reposer : elle se pose.
- [ ] Base qui a déjà 2 tables (posées avant cette version) : `/coffres voir` (op, dans la base) affiche
      « Tables d'armurier de cette base : 2/1 » ; les deux restent, utilisables ; une 3e est refusée.
- [ ] Deux clans différents : chacun peut avoir la sienne.
- [ ] Les tables du spawn (hors base) ne changent pas ; en créatif, le staff pose ce qu'il veut.
- [ ] Message en anglais et en russe (langue du jeu changée).

## 2. Jacqueline : lait concentré et vaseline à 90 hérals

- [ ] Chez Jacqueline : lait concentré 90, vaseline 90 ; les autres soins inchangés (bandage, ibuprofène 30…).
- [ ] **Si les prix restent à 30** : Jacqueline a été réglée en jeu (Registre du marchand, fichier
      `heraliz_quetes/marchands_jeu.json` du serveur) ; ce réglage passe avant le fichier. Il faut alors soit changer
      les deux prix au Registre, soit « Revenir au fichier » au Registre (ce qui reprend tout le fichier : vérifier
      que rien d'autre réglé en jeu ne se perd).

## 3. Membres des clans : 4, puis 5 au niveau 2, 6 au 5, 7 au 10, 8 au 15

- [ ] Clan niveau 1 : 4 membres au plus ; inviter un 5e : « Le clan est plein (4 membres). Place suivante :
      amélioration « Membres » dès le niveau 2 du clan (le chef, onglet Niveau). »
- [ ] Onglet Niveau (K), clan niveau 1 : « Prochain niveau 2 » montre « +1 point d'amélioration » et « 5e membre
      (amélioration) » ; ligne Membres de l'arbre : « Niv. 2 · 1 pt · 50 000 hérals » (en orange tant que le clan est
      niveau 1). Rien ne déborde de la carte de gauche (fr, en, ru).
- [ ] Clan niveau 2 avec 1 point libre et 50 000 hérals en banque : le chef achète le palier, le clan passe à 5 membres
      (tuile Aperçu « x / 5 ») ; un 5e membre peut être invité et accepter.
- [ ] Paliers suivants : niveau 5 (6 membres, 120 000), 10 (7 membres, 240 000), 15 (8 membres, 500 000) ; au maximum :
      « Le clan est plein (8 membres, le maximum). »
- [ ] Clan avec plus de membres que son maximum (membre forcé par le staff : `/clan admin forcer`) : personne n'est
      retiré ; les invitations sont refusées tant qu'il est au-dessus.
- [ ] Clans d'avant cette version : leurs paliers déjà achetés sont gardés (même nombre de membres qu'avant).
- [ ] Au premier démarrage, `config/heralisz_clans/niveaux.json` du serveur reçoit la section `membres` s'il ne l'avait
      pas ; changer un niveau dans la section, `/clan admin recharger` : l'onglet Niveau d'un joueur connecté montre le
      nouveau niveau.
- [ ] Joueur avec l'ancienne version du mod des clans : refusé à la connexion (canal des clans passé en version 7) ; le
      launcher doit avoir la nouvelle version.

## 4. Bouton « Boutique » du menu Échap sur la carte

- [ ] Sur la carte (survie), Échap puis « Boutique » : le menu se ferme et le message « /boutique : seulement au
      spawn, pas sur la carte. » s'affiche au-dessus de la barre d'objets (avant : « seul /tpa marche ici », faux).
- [ ] Taper une autre commande refusée sur la carte (ex. `/warp`) : même message avec le nom de la commande.
- [ ] Au spawn : « Boutique » ouvre la boutique comme avant.
- [ ] En combat sur la carte : « Impossible en combat · N s » (inchangé).

## Questions pour le propriétaire

- Places de membres : aujourd'hui le niveau **ouvre l'achat** de la place dans l'arbre (le chef paie toujours 1 à 4
  points et 50 000 à 500 000 hérals). Voulez-vous plutôt que la place soit **donnée toute seule** en atteignant le
  niveau, ou des prix plus bas pour ces paliers ? (Réglable sans recompiler dans `niveaux.json`, section `membres`.)
