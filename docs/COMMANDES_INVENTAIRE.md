# Inventaire des commandes (écrit par outils/commandes.py)

Lu dans les sources de modules/ (les plugins Bukkit — AuthMe, LiteBans, LuckPerms, WorldEdit, WorldGuard — et Minecraft lui-même ne sont pas comptés). « tous » = aucune permission demandée à l'entrée de la commande (une sous-commande ou le code peut en demander une) ; « réservé (droit du mod) » = entrée gardée par une règle du mod (grade du staff, droit de l'éditeur, console…) plutôt que par un niveau d'op.

**87 commandes de premier niveau sur le serveur, 370 sous-commandes repérées** (plus 2 dans des modules jamais livrés avec le serveur normal : /boss, /bots).

| Commande | Module | Entrée | Sous-commandes (niveau si différent) |
|---|---|---|---|
| `/aide` | boutique | tous |  |
| `/airstrike` | monde | staff (op 2) | call, clear |
| `/argent` | boutique | tous |  |
| `/armurier` | armes | op 4 | editer, registre |
| `/avantages` | boutique | tous | couleur, nick, qui, reset |
| `/banpc` | anticheat | staff (op 2) | info |
| `/base` | bases | tous | abandonner, confirmer, effacer (op 4), info |
| `/bon` | boutique | réservé (droit du mod) | editeur |
| `/boss` | events (pas livré) | staff (op 2) | arene, decors, eveil, labo, mammouth, mere, n07, peste, reset, salle, station, sylvorn, vitrine |
| `/bots` | banc (pas livré) | op 4 | action, camp, commandes, etat, frappes, gamerule, joueurs, jvm, lancer, mesure, off, pic, pregenerer, reseau, retirer, rythmes, scenario, stop, troncons, zone |
| `/bourse` | pnj | staff (op 2) | donner (op 4), info |
| `/boutique` | boutique | tous | abonnement |
| `/brume` | monde | staff (op 2) | auto, info |
| `/camos` | armes | staff (op 2) | ajouter, dessins, recharge, retirer, voir |
| `/canal` | monde | tous | aide, clan |
| `/capsules` | boutique | tous | deposer, retirer |
| `/carriere` | bases | staff (op 2) | creer, liste, regenerer, retirer, tp |
| `/carte` | monde | staff (op 2) | ajouter, chercher, lieu, liste, nom, recycleurs, retirer |
| `/casino` | boutique | tous | clochard, defaut, donner (op 4), jackpot, jetons (staff (op 2)), lumiere (staff (op 2)), machine (staff (op 2)), reset, retirer (op 4), salle (staff (op 2)), solde, stats (staff (op 2)), vip, voir |
| `/cinematique` | monde | tous | decaler, devant, epave (staff (op 2)), info (staff (op 2)), joueur (staff (op 2)), poser, retirer, reveil (staff (op 2)), tourner |
| `/clan` | clans | tous | accepter, ameliorer, banque, coffre, confirmer, creer, deposer, dissoudre, don, exclure, info, inviter, perm, promouvoir, quitter, refuser, retirer, retrograder, top, transferer |
| `/clashwar` | clashwar | tous | annuler (staff (op 2)), classement, cloturer (staff (op 2)), lancer (staff (op 2)), mur (staff (op 2)), quitter, regarder, retirer, salle, tableau |
| `/classement` | clans | staff (op 2) | liste, maj, retirer |
| `/coeur` | bases | tous | deplacer |
| `/coffres` | bases | op 4 | voir |
| `/combat` | noyau | staff (op 2) | effacer, etat, marquer |
| `/corps` | monde | staff (op 2) | liste, supprimer, tp, vider |
| `/cracbuild` | bases | op 4 | clear, damage, doors, essai, showcase, tier |
| `/cracghost` | bases (joueur) | tous | off |
| `/cycle` | monde | staff (op 2) | heure, info, jour, nuit, pause, reprendre |
| `/depart` | monde | staff (op 2) | ajouter, borne, essai, info, joueur, liste, point, retirer, tp, zone |
| `/diag` | noyau | op 4 | client, commandes, etat, fuites, mesure, profil, stop |
| `/diagclient` | noyau (joueur) | tous |  |
| `/discord` | boutique | tous | delier, lier |
| `/eco` | clans | staff (op 2) | definir (op 4), donner (op 4), info, journal, retirer (op 4), sauvegarder |
| `/emote` | emotes | staff (op 2) | debloquer, retirer, voir |
| `/event` | evenements | tous | ajouter, annuler (op 4), avec_points, lancer (op 4), liste, objet (op 4), point (op 4), retirer |
| `/extraction` | monde | staff (op 2) | close, create, label, list, mode, next, open, remove, rotation, tp |
| `/extractions` | monde | tous |  |
| `/grade` | boutique | réservé (droit du mod) | acheter, donner, editeur, liste, registre, retirer, voir |
| `/guide` | pnj | staff (op 2) | point, points, retirer, visite |
| `/hdv` | boutique | tous |  |
| `/heraliz` | boutique | tous | cles (staff (op 2)), edit (staff (op 2)), editor (staff (op 2)), fichier, givecrate (op 4), givekey (op 4), interface (op 4), keyall (op 4), loot (staff (op 2)), preview, retirer (op 4), voir |
| `/heralizloot` | loot | staff (op 2) | ajouter, apparaitre, beta, declencher, liste, point, reinitialiser, retirer |
| `/herals` | clans | tous | classement, payer |
| `/home` | bases | tous |  |
| `/inspecter` | monde | op 4 | base, coffres, inventaire, sac |
| `/journalieres` | pnj | tous | avancer (banc d'essai), essai (banc d'essai) (op 4), forcer (banc d'essai), minuit (banc d'essai), remettre (staff (op 2)), voir (staff (op 2)) |
| `/kit` | boutique | tous | reset (réservé (droit du mod)) |
| `/marchand` | pnj | op 4 | confirmer, editer, nom, nouveau, registre, role, supprimer |
| `/money` | boutique | tous |  |
| `/pb` | boutique | tous | stats (op 4), voir (réservé (droit du mod)) |
| `/perso` | voix | tous | open, random, reset |
| `/pnj` | pnj | staff (op 2) | apparence, aucune, bras, creer, deplacer, fichier, fins, larges, liste, moi, retirer, skin |
| `/profil` | clans | tous |  |
| `/protection` | bases | staff (op 2) | donner, retirer, voir |
| `/purge` | bases | staff (op 2) | arreter (op 4), forcer (op 4) |
| `/quete` | pnj | staff (op 2) | ajouter, baguette, confirmer, donner (banc d'essai), finir (op 4), joueur, nom, point, points, reset, retirer, tp, vider_carte (op 4) |
| `/quetes` | pnj | tous |  |
| `/r` | noyau | tous |  |
| `/rarete` | loot | staff (op 2) | aucune, defaut, effacer, voir |
| `/reanimation` | survie | tous | revive (staff (op 2)) |
| `/recharger` | noyau | op 4 |  |
| `/recolte` | recolte | staff (op 2) | info, placer, retirer, vider |
| `/redemarrage` | noyau | op 4 | annuler |
| `/rtp` | bases | tous |  |
| `/saison` | clans | tous | annuler, classement, confirmer, fin (op 4), journal (op 4), points (op 4), reset (op 4) |
| `/salle` | bunker | staff (op 2) | ajouter, baguette, bleue, bunker, creer, delier, effacer, lier, liste, recharger, redefinir, retirer, rouge, selection, verte, voir |
| `/sethome` | bases | tous |  |
| `/spartan` | anticheat | staff (op 2) | add (op 3), capture, checks, clear (op 3), esp, info, learn (op 3), reference, report, scan, setback, status, suspects, vl |
| `/spawn` | bases | tous | definir (staff (op 2)), effacer (staff (op 2)), voir (staff (op 2)) |
| `/staff` | boutique | réservé (droit du mod) | panel |
| `/stats` | clans | tous |  |
| `/suicide` | monde | tous |  |
| `/territoire` | clans | staff (op 2) | capturer, creer, deplacer, difficulte, drapeau, en, est, info, liste, nord, ouest, ravitailler, reset, sud, supprimer, valeur |
| `/tpa` | boutique | tous | accepter, ajouter, liste, refuser, retirer, zone (staff (op 2)) |
| `/trade` | boutique | tous | accepter, annuler, refuser |
| `/train` | monde | staff (op 2) | actif, decalage, gare, info, reparer, reseau, vitres |
| `/tuto` | bases | staff (op 2) | finir, lancer, rejouer |
| `/unbanpc` | anticheat | op 3 |  |
| `/usure` | monde | staff (op 2) | config, definir, reparer, voir |
| `/vehicules` | monde | staff (op 2) | donner, liste |
| `/vitres` | armes | op 4 | pourquoi |
| `/voix` | voix | tous | info, mute, mutes, stats, unmute |
| `/warp` | boutique | tous | del (réservé (droit du mod)), description (réservé (droit du mod)), icone (réservé (droit du mod)), liste (réservé (droit du mod)), nom (réservé (droit du mod)), set (réservé (droit du mod)) |
| `/wipe` | pnj | op 4 | confirmer |
| `/zombies` | monde | staff (op 2) | ajouter, liste, reserves, retirer, stats, zone |
| `/zones` | monde | staff (op 2) | info |
| `/zonevip` | boutique | réservé (droit du mod) | baguette, coin1, coin2, creer, liste, retirer, tp, voir |
