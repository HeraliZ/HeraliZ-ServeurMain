# Essais en jeu — chantier « économie » (audit du 02/11)

Modules touchés : `clans` (heraliz_clansetbases **3.11.0**), `boutique` (heraliz_boutique **1.4.0**), `pnj`
(heraliz_quetes **1.17.0**). Rien n'a été testé en jeu ni fabriqué par Gradle (dépôt de Forge bloqué ici) : seul un
contrôle `javac` des lignes changées (les classes de Minecraft manquent) et `outils/verifier_langues.py` (0 erreur).
Le contrôle de branche de GitHub doit fabriquer et vérifier avant tout essai.

Points de l'audit : 4, 5, 6, 8 (pour ces modules), 10, 20, 21, 22, 23 et mods-3 §9, §10, §11 d, §12, §13, §14.

## 1. Quêtes de clan (point 4)
- [ ] Un clan qui tire « Fouiller 60 caches » : ouvrir des conteneurs de loot pleins sur la carte fait avancer la quête
  (une fois par conteneur rempli, première ouverture seulement ; rien en créatif).
- [ ] « Déposer 1 500 hérals » : déposer 1 500 → quête finie. Retirer 1 500 puis redéposer 1 500 le même jour (ou le
  lendemain) → la quête suivante n'avance pas avec ce même argent.
- [ ] Retirer d'abord (banque pleine de revenus des zones), puis déposer : seul le dépôt net compte ; retirer le revenu
  des zones ne pénalise pas les dépôts déjà faits.
- [ ] Une quête « Déposer » déjà en cours avant la mise à jour garde sa progression.
- [ ] « Tuer N joueurs » : tuer un joueur connecté depuis la même adresse IP (deux comptes sur le même PC) ne compte
  plus (ni XP, ni stats) ; un vrai adversaire compte.

## 2. Casino : gains sur le compte de capsules (point 5)
- [ ] Gagner à une machine à sous, au crash, à la roulette, au blackjack : rien n'arrive dans l'inventaire, le compte
  (distributeur) monte, le message dit « versées sur ton compte ».
- [ ] Miser avec 0 capsule sur soi mais un compte rempli : la mise est prise sur le compte (écran : le solde affiché
  compte le compte). Doubler / séparer au blackjack pareil.
- [ ] Partie annulée (roulette, duel) : la mise revient sur le compte.
- [ ] Se déconnecter pendant une partie, gagner : à la reconnexion, gain déjà sur le compte.
- [ ] Distributeur : les nouveaux mouvements s'affichent « Gain au casino », « Mise au casino », « Inventaire plein ».
- [ ] Caisse du casino, inventaire plein, achat de capsules contre un objet : le reste va sur le compte, rien au sol.

## 3. Recycleur du spawn (point 6)
- [ ] Mettre 6 armes dans le recycleur, quitter le jeu (Alt+F4) : à la reconnexion, tout est dans l'inventaire.
- [ ] Même chose avec `/redemarrage`.
- [ ] Inventaire plein à la fermeture : rien au sol ; message « ils t'attendent dans le recycleur » ; en le rouvrant,
  les objets sont dans leurs cases.
- [ ] Un couteau à vie ou un objet de quête ne se pose plus dans les cases « à recycler ».
- [ ] Mourir l'écran ouvert (/kill) : objets rendus à la réapparition.

## 4. Commandes d'argent (point 10)
- [ ] `/clan admin argent <joueur> 500` par un op 2 : refusé ; par un op 4 connecté sur AuthMe : comme `/eco donner`
  (ligne dans `heralisz_economie/journal_admin.log`) ; `-500` = `/eco retirer`.
- [ ] Onglet « Commandes du Fondateur » : donner `/eco` à un Modérateur : `/eco journal` marche, `/eco donner` est refusé.
  Donner `/quete` : `/quete donner` et `/quete finir` refusés (message rouge), les autres marchent. Donner `/tp` ou
  `/gamemode` : marchent comme avant.

## 5. Hérals sans lag (point 20)
- [ ] Dépôt et retrait à la banque du clan : un dossier `heralisz_economie/` contient parfois `comptes.json.wal.N` (petit) ;
  il disparaît dans les secondes qui suivent.
- [ ] Arrêter brutalement le serveur (kill) juste après un dépôt en banque : au redémarrage, journal « état(s) de compte
  rejoué(s) », soldes justes, pas de copie.
- [ ] `/eco info`, `/eco sauvegarder`, `/eco recharger` marchent ; copies `comptes.json.1` à `.5` au plus une par heure.
- [ ] Compte sans transaction depuis 30 jours : son journal disparaît de `comptes.json`, solde gardé.
- [ ] Banque : deux opérations en moins d'une seconde → « Doucement : une opération de banque par seconde ».
- [ ] Recycleur de la carte : Maj + clic en boucle → au-delà de 4 dépôts par seconde le clic est refusé, l'écran reste juste.

## 6. Boutique, don, inventaire plein (points 21, 22, 23, 8)
- [ ] Achat en PB quand le site répond une erreur (essai sur le site de test) : l'achat reste en cours, puis remboursé ;
  ligne « ERREUR DU SITE » dans le journal de la boutique.
- [ ] `/clan don` sur la carte : refusé (« au spawn ») ; au spawn : marche comme avant.
- [ ] Inventaire plein, ouvrir une caisse : message « t'attend » ; libérer une case : l'objet arrive dans les 5 s.
  Pareil à la connexion, et pour un `/trade` annulé, un kit, un bon.
- [ ] Caisse du casino (acheter un objet) et marchand (lot de 5) inventaire plein : refusé AVANT de payer.
- [ ] Quête de Varga terminée inventaire plein : l'objet attend, arrive dès qu'il y a de la place.

## 7. Petits points (mods-3 §9 à §14)
- [ ] Staff en créatif : ouvrir une caisse consomme la clé.
- [ ] Les 15 machines du casino s'affichent comme avant (outils de matières déplacés de l'ancienne « Fruits d'Or »).
- [ ] Panel du staff : boutons « boss » absents (le jar des boss n'est pas livré).
- [ ] Dépôt au distributeur : capsules retirées, solde crédité (ordre d'écriture inversé).
- [ ] Vente à Mila puis arrêt brutal : objets partis ET hérals versés (ou les deux perdus, jamais les deux gardés).
- [ ] Quêtes du jour : livrer ou tirer la récompense sur la carte ou en combat → refusé avec un message ; au spawn, ok.

## Pas fait (et pourquoi)
- Jackpot du casino : il est montré par l'« Afficheur du jackpot » et la télé (chaîne 2) posés au casino ; le retirer
  change ce décor. Question plus bas.
- Écriture de `hdv.json` en arrière-plan : la logique de l'hôtel des ventes s'appuie sur le résultat de chaque écriture
  (retour en arrière si elle rate) ; à faire avec un essai complet.
- Seuil « 4 clans » du ClashWar (§11 c) : dans le module ClashWar, autre chantier. Recettes, monde `crac:bases`, tests
  de CRac (§10 a-c, i), restes du ClashWar (§10 g) : autres chantiers. Les 8 caisses vidées : pas touchées (consigne).
- Coffre d'un clan dissous quand le chef est hors ligne : toujours perdu (journal) ; le coffre refuse les objets à vie.

## Questions pour le propriétaire (avec une proposition)
1. **Points PvP de saison sans plafond par jour** (A314) : proposition, 40 points de kills par clan et par jour (comme les
   captures).
2. **Quêtes de Varga, 10 points de saison chacune** (A316) : proposition, les mettre dans les « petites actions »
   plafonnées à 50 points par joueur et par jour (`plafonnees` de `saison.json`, sans code).
3. **Mila rachète une arme ruinée au prix d'une neuve** : proposition, prix × état de l'arme (une arme à 50 % se revend
   moitié prix, 10 % au moins).
4. **Cœur de clan à 1 000 hérals chez le Bâtisseur** (20 000 capsules au casino) : proposition, 5 000 hérals.
5. **Jackpot du casino** jamais alimenté : on le nourrit (1 % des mises des machines à sous, gagné au plus gros
   tirage), ou l'afficheur montre autre chose ?
