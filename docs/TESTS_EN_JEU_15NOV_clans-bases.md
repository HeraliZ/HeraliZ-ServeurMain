# Essais en jeu — corrections de l'audit du 15/11, chantier « clans et bases »

Bases (CRac) **1.38.0-beta**, Clans **3.16.0** (canal des clans en **version 13**), Boutique **1.5.8** ; réglages du serveur :
`serveur/config/crac/purge.json` (Purge arrêtée), `serveur/config/heraliz_quetes/pnj/varga.json` (texte de Varga).
Rien n'a été essayé en jeu par Claude, ni fabriqué avec Gradle (Minecraft et Forge ne se téléchargent pas sur la machine
de travail). Essais faits hors du jeu : compilation avec javac contre de petites copies de Minecraft (classes nouvelles ou
très changées : armoires perdues, Purge, prise et déplacement du cœur, saison, journal des clans, `/tpa`), essai du journal
d'écriture des clans (écriture, arrêt brutal, ligne coupée, effacement) et de la pause de la saison (fichier abîmé,
relecture, archives). `python outils/verifier_langues.py` : 0 erreur.

Jars : serveur ET joueurs à mettre à jour ensemble (canal des clans en version 13 : un ancien jeu est refusé à la
connexion, le launcher le met à jour).

## Ce qui change

| Quoi | Pour le joueur |
|---|---|
| **La Purge est arrêtée** (décision du propriétaire) | toutes les bases se raident à toute heure (C4, charges, explosions), coffres pillables à toute heure ; plus d'annonce ni d'icône « PURGE » ; `/purge` n'est plus proposée aux joueurs (staff : « Pas de Purge en ce moment… ») ; le code reste : `"actif": true` dans `purge.json` puis `/recharger purge` la remet |
| Cœur de clan sans Purge | il se prend à toute heure (90 s, comme avant), **une fois toutes les 24 h au plus pour un même clan** (`prise_hors_purge_heures`) ; avant, Purge arrêtée = jamais (« pas de saison en cours »). `/coeur deplacer` refusé pendant un raid de la base |
| **Armoire détruite pendant un raid** | la base reste 24 h au clan : lui seul y repose une armoire (même écart qu'entre deux bases), lui seul y construit et touche aux pièces au marteau, elle ne s'use pas ; message aux membres avec un bouton « Abandonner la base » (chef ou « Gérer la base »), rappel à la connexion, `/base abandonner` (puis confirmer) ; au bout de 24 h ou après l'abandon : libre pour tous |
| Armoire reposée (taille de l'arbre, H2) | plus de « trop grand » sur une base au palier 4 (rayon 29), plus de métal renforcé redemandé aux paliers 2 et 3 : la taille payée par l'arbre est reprise ; le home de la base revient si c'est la même base |
| **Permission « Accepter un /tpa dans la base »** (C1) | nouvelle ligne du groupe BASE de l'onglet Permissions (par grade et par membre). **Défauts : chef et haut gradé oui, membre et recrue non.** Sans elle, dans la zone de l'armoire de son clan, on ne peut pas accepter un `/tpa` ; celui qui demandait le sait, la demande reste en attente (sortir de la base puis accepter) ; revérifié au départ (15 s) |
| Saison : fichier illisible (M1) | la saison se met en pause (aucun point, rien d'écrit) au lieu d'une « saison 1 » vide ; staff prévenu à la connexion et par `/saison` ; `/recharger saison` après réparation ; archives jamais écrasées |
| Points PvP (M2) | une victime qui ne porte qu'une arme de mêlée ou des grenades de Tactique (Peu commun ou plus) compte comme armée |
| `/clan don` (M3) | le joueur est enregistré sans l'objet avant que le clan reçoive l'XP |
| Onglet Saison | « ClashWar : N clans au moins » lu dans `clashwar.json` (B1) ; « Prise du cœur » : « une fois par 24 h au plus » (B5 et Purge) ; « Mourir contre un joueur » : « même si le tueur n'a pas de clan ; jamais sous 0 » |
| Banque du clan, revenus des zones, quêtes payées, arbre, dons (B2) | plus de gel : le clan changé est écrit en une ligne dans `heralisz_clans/clans.json.wal.<n>`, `clans.json` suit en fond (minute) |
| Ravitaillement (B4) | XP, quête « ravitaillements » et ligne du journal au **largage** de la caisse (annonce toujours au signal) |
| Perf | stabilité des bases calculée une fois au premier tick (au lieu de deux) ; recharges du `/tpa` plus gardées en mémoire après usage |

## À vérifier en jeu

1. **Purge arrêtée.** Au démarrage, journal : « Purge : 3 créneau(x), arrêtée (raids et prise du cœur à toute heure… ». Un
   vendredi à 19 h (ou n'importe quand) : aucune annonce, aucune icône. Un C4 sur un mur d'une base d'un autre clan : le mur
   perd ses PV, raid block, aucun message « hors Purge ». Un coffre dans la base raidée s'ouvre.
2. Joueur sans op : `/purge` est inconnue (pas proposée au Tab). Staff : `/purge` → « Pas de Purge en ce moment… » ;
   `/purge forcer 5` refusée avec l'explication.
3. `/recharger purge` après avoir mis `"actif": true` (serveur d'essai seulement) : `/purge` revient pour les joueurs
   sans se reconnecter ; remettre `false`.
4. **Cœur.** Clan B avec cœur ; un membre de A (avec cœur) le prend (clic droit, 90 s à 3 blocs) : B perd 15 %, A gagne 10 %.
   Recommencer tout de suite : « Ce cœur a déjà été pris il y a peu : il se reprend dans 23 h 59 ». Redémarrer : toujours
   refusé. Pendant que la base de B est en raid block : `/coeur deplacer` refusé (« pendant un raid de la base »).
5. Onglet Saison (K) : ligne « Prise du cœur » sans « (Purge) », condition « le clan pris perd 15 % ; une fois par 24 h au
   plus » ; ligne ClashWar « 4 clans au moins » (changer `saison_clans_min` + `/recharger clashwar` : le chiffre suit) ;
   « Mourir contre un joueur » : texte entier visible en français, anglais et russe. Historique du clan : « Cœur pris
   par [X] » sans « pendant la Purge ».
6. Varga, question « Les clans et la saison, c'est quoi ? » : plus de Purge dans la réponse (3 langues).
7. **Armoire détruite en raid.** Base de A : C4 sur l'armoire (100 PV). Les membres de A connectés reçoivent le message et
   (chef) le bouton « [Abandonner la base] » ; journal du clan : « Armoire détruite en raid : base réservée 24 h ».
   - Un joueur de B pose une armoire sur une fondation de A, ou juste à côté : refusé « Cette base a perdu son armoire
     pendant un raid : seul son clan… encore 23 h … ».
   - B pose une fondation, un mur dans la zone de A : refusé (même message, « y construire »). B ne retire plus vitres et
     barreaux au marteau.
   - Les murs de A ne perdent pas de PV par l'usure (attendre quelques minutes, `/base info` ou le marteau).
   - Fin du raid block (5 min) : un membre de A qui gère la base repose une armoire (achetée au Bâtisseur) sur sa base :
     posée sans métal, la zone reprend la taille de l'arbre en 5 s (ligne verte plan en main), home de la base rendu
     (`/home` du spawn), message « … a reposé l'armoire : votre base est récupérée ».
   - Autre essai : `/base abandonner` → « Confirme dans les 30 s » → [Confirmer l'abandon] : B peut poser son armoire.
   - Autre essai : redémarrer pendant la réservation : toujours réservée ; un membre de A qui se connecte reçoit le
     rappel avec le temps restant.
   - Au bout de 24 h réelles (pas d'essai plus court possible) : message « restée 24 h sans armoire », libre pour tous.
   - L'armoire cassée par son clan HORS raid (ou déplacée au marteau) : aucune réservation (règles d'avant).
8. **Taille de l'arbre (H2).** Clan avec « Taille de la base » au palier 3 (rayon 29), murs jusqu'à la ligne verte :
   casser l'armoire (hors raid) puis en reposer une : acceptée, aucun métal pris. Palier 1 (rayon 23) : aucun métal.
9. **Permission /tpa.** Clan : recrue R dans la zone de l'armoire. Un ami F fait `/tpa R` ; R accepte : refusé, R lit « Tu
   es dans la base de ton clan sans le droit… », F lit « R est dans la base de son clan et n'a pas le droit d'y faire venir
   quelqu'un ». La demande reste en attente : R sort de la zone et reclique [Accepter] avant 60 s : accepté, F arrive.
   R accepte dehors puis entre dans la base pendant les 15 s : départ annulé, les
   deux prévenus. Un haut gradé (défaut oui) accepte dans la base : F arrive. Onglet Permissions : la ligne « Accepter un
   /tpa dans la base » tient dans sa place (PAR GRADE et PAR JOUEUR, 3 langues) ; la cocher pour la recrue : R accepte.
10. **Saison en pause** (serveur d'essai) : arrêter, abîmer `heralisz_clans/saison_etat.json` (une virgule en trop),
    démarrer : journal « saison EN PAUSE », op connecté prévenu en rouge, `/saison` (op) le dit, un kill ne donne rien,
    le fichier abîmé n'est pas réécrit (copie `.illisible-…` à côté) ; réparer, `/recharger saison` : la saison reprend
    avec ses points.
11. **Machette** : B ne porte qu'une machette Peu commune ; A le tue : +1 point (journal `[saison]`).
12. **Don** : `/clan don` d'un objet Épique au spawn, puis arrêt brutal du serveur (kill du processus) : au retour, l'objet
    n'est pas revenu (l'XP peut être perdue si l'arrêt tombe entre les deux, jamais l'objet ET l'XP).
13. **Journal des clans** : dépôt à la banque puis arrêt brutal tout de suite : au retour, la banque a le dépôt (journal
    `[clans] journal d'ecriture : 1 etat(s) de clan rejoue(s)`) ; une minute après un dépôt, plus de fichier
    `clans.json.wal.*` dans `heralisz_clans/` (sauf si `clans.json` était illisible au démarrage).
14. **Ravitaillement** : signal → annonce tout de suite, XP et quête « ravitaillements » seulement au largage de la caisse ;
    arrêt du serveur pendant le vol : au retour, délai rendu, et le nouveau signal ne compte qu'une fois.
15. Perf : `/diag profil` au démarrage : `StructureIntegrity.tick` ne refait plus `recomputeAll`.

## Pas vérifié / limites

- Aucun essai en jeu ; compilation complète et serveur d'essai à faire après intégration.
- Défaut de la permission « Accepter un /tpa dans la base » pour le grade Membre (non) : choix prudent, à confirmer par le
  propriétaire (un membre a déjà « Ouvrir les coffres » par défaut).
- Prise du cœur sans Purge : choisi « à toute heure, une fois par 24 h par clan » (comme les raids) ; à confirmer.
- La réservation suit l'heure réelle (un arrêt du serveur compte dans les 24 h). Le stock d'entretien de l'armoire
  détruite tombe au sol (pillable), comme avant.
- `modules/banc` (bots de charge, jamais livré) tape encore `/purge` : commande maintenant inconnue pour eux (sans
  effet sur le jeu).
- `docs/COMMANDES_INVENTAIRE.md` (écrit par `outils/commandes.py`) pas régénéré : `/base abandonner` n'y est pas encore.
