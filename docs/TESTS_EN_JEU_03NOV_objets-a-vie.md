# Essais en jeu du 03/11 : chantier « objets à vie » (rien testé par Claude)

Nouvelle règle du propriétaire (03/11, remplace celle du 02/11 décrite dans `TESTS_EN_JEU_02NOV_armes-joueur.md` §2 et
`TESTS_EN_JEU_02NOV_decisions.md` n° 10) :

> « S'il est mis dans un sac, mis dans un coffre, etc., il y a toujours la double vérification, comme si on voulait le
> jeter par terre. Si la personne valide, [...] il ne le récupère pas !! Si sac ou coffre, il faudra le récupérer dans le
> sac ou le coffre ; si jeté au sol, il disparaît comme tout le reste au bout du temps imparti, et si quelqu'un le
> ramasse, il en devient le nouveau propriétaire. »

Jars touchés (joueurs ET serveur, même fabrication des deux côtés : le canal `heraliz_tactique:a_vie` passe en
version 3) : HeraliZ Tactique 2.44.0 (`armes`), Réanimation 1.16.10 (`survie`).

**Rien n'a été compilé avec Gradle** (dépôt de Forge bloqué ici) : seulement une relecture et une vérification de la
syntaxe Java avec `javac`. Le contrôle de branche de GitHub fera la vraie fabrication. Au démarrage du serveur et du
jeu : aucune erreur « Mixin » sur `ObjetsAVieClicMixin` (sinon le jeu ne démarre pas).

Tous les essais : en survie (pas en créatif ni spectateur), avec un couteau à vie ou un skin à vie (Séraphin, Chaînes
de l'Enfer), avec un compte SANS droits de staff.

## 1. Toujours protégé dans l'inventaire (inchangé)
- [ ] Mourir avec le couteau dans l'inventaire, la barre ou au curseur : rendu à la réapparition.
- [ ] Alt+F4 avec le couteau au curseur (inventaire, coffre, sac ouvert) : il est dans l'inventaire au retour.
- [ ] Inventaire plein à la réapparition : « gardé », puis rendu dès qu'une case se libère.
- [ ] Touche Q en jeu (aucun écran ouvert) : rien n'est jeté, message « il ne se jette pas avec la touche de jet ».

## 2. Confirmation pour TOUT contenant (nouveau)
Ouvrir un coffre de base, puis refaire avec : un coffre / une caisse du monde, son sac à dos (touche du sac), la planque
ou le coffre VIP, un établi, l'atelier de réparation, un meuble (placard, frigo…), la boîte aux lettres d'envoi.
Pour CHAQUE chemin ci-dessous, la question doit apparaître (« Ranger cet objet ici ? », dans le sac : « Mettre dans le
sac ? ») et l'objet ne doit pas être dans le contenant tant qu'on n'a pas répondu :
- [ ] clic gauche avec le couteau au curseur sur une case du coffre ;
- [ ] clic droit (poser un seul) avec une pile de skins à vie ;
- [ ] glisser une pile de skins sur plusieurs cases (une question pour une case, le reste revient dans l'inventaire) ;
- [ ] Maj + clic sur le couteau dans l'inventaire ;
- [ ] souris sur une case du coffre + touche 1 à 9 (couteau dans la barre) ;
- [ ] échange : couteau au curseur sur une case du coffre qui contient déjà un autre objet (l'autre objet arrive au
      curseur, le couteau part en question).
Réponses :
- [ ] « Non, le garder » (ou Échap, Entrée) : le couteau revient dans l'inventaire.
- [ ] « Oui, le ranger » : il est dans la case visée (sinon une autre case libre du même coffre).
- [ ] Ensuite, le déplacer d'une case à l'autre DANS le coffre : plus de question.
- [ ] Le reprendre dans l'inventaire puis le remettre : la question revient.
- [ ] Coffre cassé ou hors de portée pendant la question : le couteau revient dans l'inventaire.

## 3. Une fois rangé, il n'est plus protégé (nouveau)
- [ ] Couteau rangé dans le sac porté, mourir : il est dans le sac, sur le corps (plus rendu à la réapparition).
- [ ] Pareil avec un sac rangé dans l'inventaire.
- [ ] Fouille d'un joueur à terre qui a un couteau dans son sac : le fouilleur prend le sac AVEC le couteau ; un
      couteau dans l'inventaire de la victime ne se prend toujours pas.
- [ ] Couteau rangé dans un coffre (planque comprise) : il y reste, il ne revient jamais tout seul.

## 4. Au sol (nouveau)
- [ ] Inventaire ouvert, Q sur le couteau (ou Ctrl+Q, ou clic hors de l'écran) : « Jeter cet objet ? » ; Oui : il tombe.
- [ ] Il ne brûle toujours pas dans la lave ni le feu.
- [ ] Personne ne le ramasse : il disparaît au bout du temps normal des objets au sol (comme une pierre jetée au même
      moment), il ne revient PLUS à celui qui l'a jeté.
- [ ] Un autre joueur le ramasse : il est à lui, protégé (il le garde à sa mort, question s'il le range ou le jette).

## 5. Toujours refusé (rien ne doit détruire un objet à vie par erreur)
- [ ] Conteneur de loot (T1 à T7), corps, caisse du largage public : rien n'entre (pas de question).
- [ ] Recycleur du spawn et recycleur de la carte : le couteau ne se pose pas dans les cases « à recycler ».
- [ ] Coffre du clan : refusé comme avant.
- [ ] Caisse de ravitaillement d'un territoire, caisse de frappe de ravitaillement : le couteau revient tout de suite,
      message « pas dans cette caisse, elle disparaît avec son contenu ».
- [ ] Cadre, porte-armure (dans une base), mannequin de l'Armurerie : clic droit avec le couteau en main → refusé,
      message « il ne se pose pas sur un présentoir » ; accroupi + clic droit sur le mannequin : son écran s'ouvre.
- [ ] Hôtel des ventes, Mila : refusés comme avant.

## 6. Coffres des bases (comportement gardé, question au propriétaire)
- [ ] Le chef range un couteau dans un coffre de sa base (question, Oui) ; un membre avec le droit « Ouvrir les coffres »
      le prend : il est à lui (protégé à sa mort).
- [ ] Un pilleur voit le couteau mais ne peut pas le prendre (`lifetimeItemsOwnerOnly = true`) ; avec `false`, il le
      prend et il est à lui.
- [ ] Coffre détruit au C4 : le couteau va toujours au chef de la base.

## 7. Plantage (serveur local)
- [ ] Question « Ranger cet objet ici ? » à l'écran, sauvegarde, arrêt brutal : au retour, le couteau est dans
      l'inventaire, une seule fois (ni dans le coffre en plus, ni perdu).

## Journal du serveur
Lignes `[a vie]` : « gardé », « rendu », « mort : N objet(s) à vie gardé(s) ». Plus de ligne « jamais ramassé au sol ».
