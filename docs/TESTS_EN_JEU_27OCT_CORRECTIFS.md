# À tester en jeu — correctifs du 27/10 (bases, combat, frappe aérienne, réanimation)

Rien de cette liste n'a été testé en jeu par Claude. Fabriqué et vérifié hors du jeu (`construire.py`, `verifier.py`).
Canal réseau CRac version 26 : joueurs et serveur doivent avoir les jars de la **même** fabrication.

## Combat
- [ ] Deux joueurs de part et d'autre d'une porte fermée (ou d'un mur), collés : ni balle ni coup de mêlée (poing,
      couteau, arme de mêlée, crosse) ne passe. Porte ouverte, fenêtre : les tirs et les coups passent.
- [ ] Un joueur collé à une porte, arme en main : ses bras dépassent de l'autre côté ; on ne le blesse pas en tirant
      dans ses bras depuis l'autre côté. Bras qui dépassent d'un coin de mur ou d'une porte ouverte : touchés.
- [ ] Un zombie collé à une porte fermée ne frappe plus le joueur derrière.
- [ ] Tirer sur un mur de base : la traînée de la balle s'arrête sur le mur (avant : elle continuait derrière).
      Le mur ne prend toujours aucun dégât. Même chose sur un mur de pierre ordinaire.

## Bases
- [ ] Plus de texte « Base protégée — nouveau joueur » ; une base neuve se raide tout de suite. Les protections déjà
      données ont été retirées au premier démarrage (journal : « protection automatique des bases coupée »).
      `/protection donner <joueur> [jours]` marche toujours. Fin du tutoriel : Varga ne parle plus de jours de protection.
- [ ] Dans une base (zone de l'armoire) : barricade en bois, mannequin, laisse, feu d'artifice… refusés (« Dans une base,
      seuls les pièces et objets de construction se posent »). Hors des bases : comme avant. Plan, marteau, C4, charge,
      armoire, caisse en métal : comme avant. Ouvrir une caisse, une porte, l'armoire avec un objet en main : comme avant.
- [ ] Rampes (carrée et triangle) : plus de poteaux sous le bout haut ; on passe dessous, on monte dessus, les rampes
      voisines se raccordent toujours.
- [ ] Marteau : améliorer une pièce seulement à 4 blocs au plus (roue du marteau : « Approche-toi pour améliorer ») ;
      réparer et poser : comme avant (8 blocs). Réglage `upgradeReach` de `crac-building.toml`.
- [ ] Caisse en métal ou armoire posée d'abord contre le bord d'une fondation, puis le mur : plus de creux dans le mur
      au-dessus et autour de l'objet. Retirer l'objet : le mur se rebouche.
- [ ] Bloc posé (staff) au bord d'une fondation, mur posé, puis bloc cassé : le mur se rebouche.
- [ ] Plan en main sur la carte, près d'une ville ou des rails : cases interdites teintées de rouge au sol, bord rouge ;
      le fantôme devient rouge (« Zone en rouge : on ne construit pas ici… »). Plan rangé : plus rien. Aucun ralentissement.
- [ ] Montées : sauter d'un demi-mur sur un plancher posé dessus, puis sur le plancher du niveau suivant, en courant :
      l'aide au saut arrive à temps (elle est donnée plus tôt). À noter : un plancher posé sur un demi-mur laisse 1,3 bloc
      dessous et dessus ; Minecraft demande 1,5 bloc pour passer accroupi : on n'y passe qu'allongé (touche « Ramper »).

## Frappe aérienne, réanimation, armes
- [ ] Recharger les jumelles plusieurs fois, aussi quand le serveur rame : animation fluide, jamais en arrière.
- [ ] Bombardement près de zombies et de joueurs : plus de gel ; lire `latest.log` (`[frappes] … ms`, `[zombies] bruit`).
- [ ] Réanimer : le temps au centre descend jusqu'à 0,1 puis la coche ; interrompre (bouger, tir, déconnexion) : le temps
      disparaît tout de suite.
- [ ] Plus de Blundergat ni d'autres armes du mode Zombies dans le menu des armes, après la mise en ligne et le
      redémarrage (gunpack `heraliz_wonder` effacé du Ptero).
