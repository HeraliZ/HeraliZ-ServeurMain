# Essais en jeu du 31/10 — chantier « réglages »

Rien de ce qui suit n'a été testé en jeu ni compilé par Gradle ici (Forge inaccessible depuis la machine de travail) :
la fabrication complète et les contrôles se font au contrôle de branche de GitHub. Vérifié ici : syntaxe des fichiers
Java, logique de la section `membres` de `niveaux.json` (petit essai hors jeu), langues (0 erreur), contrôle des marchands.

Versions : Bases (CRac) 1.29.3-beta, Clans 3.10.1, Noyau 1.0.7.

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

La place est **donnée toute seule** par le niveau (rien à acheter) ; l'amélioration « Membres » n'est plus dans l'arbre.

- [ ] Clan niveau 1 : 4 membres au plus ; inviter un 5e : « Le clan est plein (4 membres). Une place de plus au niveau 2
      du clan. »
- [ ] Onglet Niveau (K) : l'arbre n'a plus de ligne « Membres » (6 lignes : base, TP, revenu, ravitaillement, loot,
      territoires), rien ne déborde, la ligne de la saison est bien sous l'arbre. « Prochain niveau 2 » montre « +1 point
      d'amélioration » et « 5 membres au plus » (fr, en, ru).
- [ ] Passer le clan au niveau 2 (`/clan admin niveau <tag> 2`, ou de l'XP) : message au clan « Niveau 2 : votre clan
      peut maintenant avoir 5 membres. », tuile Aperçu « x / 5 », un 5e membre peut être invité et accepter, sans rien
      payer. Puis niveau 5 → 6 membres, 10 → 7, 15 → 8 ; au-delà : « Le clan est plein (8 membres, le maximum). »
- [ ] `/clan ameliorer membres` : « Amélioration inconnue (base, tp, …) ».
- [ ] Clan avec plus de membres que son maximum (membre forcé par le staff : `/clan admin forcer`) : personne n'est
      retiré ; les invitations sont refusées tant qu'il est au-dessus.
- [ ] **Remboursement** (clan qui avait acheté des paliers Membres avant cette version) : au premier démarrage, ses
      points d'amélioration reviennent (« N POINT(S) LIBRE(S) » plus haut), la banque reçoit une ligne « remboursement
      (Membres) » avec les hérals rendus, le journal du clan dit « amélioration Membres remboursée (N point(s), N
      hérals) », et le journal du serveur a une ligne `[clans] [TAG] amelioration Membres retiree de l'arbre`. Après un
      2e redémarrage : rien de plus (une seule fois). Son nombre de membres au plus est maintenant celui de son niveau.
- [ ] Au premier démarrage, `config/heralisz_clans/niveaux.json` du serveur reçoit la section `membres` s'il ne l'avait
      pas ; changer un niveau dans la section, `/clan admin recharger` : l'onglet Niveau d'un joueur connecté montre le
      nouveau niveau.
- [ ] Joueur avec l'ancienne version du mod des clans : refusé à la connexion (canal des clans passé en version 8) ; le
      launcher doit avoir la nouvelle version.

## 4. Bouton « Boutique » du menu Échap sur la carte

- [ ] Sur la carte (survie), Échap puis « Boutique » : le menu se ferme et le message « /boutique : seulement au
      spawn, pas sur la carte. » s'affiche au-dessus de la barre d'objets (avant : « seul /tpa marche ici », faux).
- [ ] Taper une autre commande refusée sur la carte (ex. `/warp`) : même message avec le nom de la commande.
- [ ] Au spawn : « Boutique » ouvre la boutique comme avant.
- [ ] En combat sur la carte : « Impossible en combat · N s » (inchangé).

## Questions pour le propriétaire

- Remboursement des hérals de l'ancienne amélioration « Membres » : on rend le prix vraiment payé quand la banque du
  clan s'en souvient (ses 30 derniers mouvements), sinon le prix de base (25 000 / 60 000 / 120 000 / 250 000) ; un
  clan qui l'a payé double (après le 28/10) il y a longtemps peut donc recevoir moins que payé. Le staff peut compléter
  si besoin (`/clan admin argent` au chef, qui dépose à la banque ; le journal du serveur dit ce qui a été rendu à
  chaque clan). Est-ce que ça vous va ?
