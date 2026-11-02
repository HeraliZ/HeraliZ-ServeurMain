# Inventaire des commandes (écrit par outils/commandes.py)

Lu dans les sources de modules/ (les plugins Bukkit — AuthMe, LiteBans, LuckPerms, WorldEdit, WorldGuard — et Minecraft lui-même ne sont pas comptés). « tous » = aucune permission demandée à l'entrée de la commande (une sous-commande ou le code peut en demander une).

**78 commandes de premier niveau sur le serveur, 341 sous-commandes repérées** (plus 2 dans des modules jamais livrés avec le serveur normal : /boss, /bots).

| Commande | Module | Entrée | Sous-commandes (niveau si différent) |
|---|---|---|---|
| `/airstrike` | monde | staff (op 2) | call, clear |
| `/armurier` | armes | op 4 | editer, registre |
| `/avantages` | boutique | tous | couleur, nick, qui, reset |
| `/banpc` | anticheat | staff (op 2) | info |
| `/base` | bases | tous | effacer (op 4), info |
| `/bon` | boutique | tous | editeur |
| `/boss` | events (pas livré) | staff (op 2) | arene, decors, eveil, labo, mammouth, mere, n07, peste, reset, salle, station, sylvorn, vitrine |
| `/bots` | banc (pas livré) | op 4 | action, camp, commandes, etat, frappes, gamerule, joueurs, jvm, lancer, mesure, off, pic, pregenerer, reseau, retirer, rythmes, scenario, stop, troncons, zone |
| `/boutique` | boutique | tous | abonnement |
| `/brume` | monde | staff (op 2) | auto, info |
| `/camos` | armes | staff (op 2) | ajouter, recharge, retirer, voir |
| `/canal` | monde | tous | aide |
| `/capsules` | boutique | tous | deposer, retirer |
| `/carriere` | bases | staff (op 2) | creer, liste, regenerer, retirer, tp |
| `/carte` | monde | staff (op 2) | ajouter, lieu, liste, nom, retirer |
| `/casino` | boutique | tous | clochard, defaut, donner, jackpot, jetons (staff (op 2)), lumiere (staff (op 2)), machine (staff (op 2)), reset, retirer, salle (staff (op 2)), solde, stats (staff (op 2)), vip, voir |
| `/cinematique` | monde | tous | decaler, devant, epave (staff (op 2)), info (staff (op 2)), joueur (staff (op 2)), poser, retirer, reveil (staff (op 2)), tourner |
| `/clan` | clans | tous | accepter, ameliorer, banque, coffre, confirmer, creer, deposer, dissoudre, don, exclure, info, inviter, perm, promouvoir, quitter, refuser, retirer, retrograder, top, transferer |
| `/clashwar` | clashwar | tous | annuler (staff (op 2)), classement, cloturer (staff (op 2)), lancer (staff (op 2)), mur (staff (op 2)), quitter, regarder, retirer, salle, tableau |
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
| `/grade` | boutique | tous | acheter, donner, editeur, liste, registre, retirer, voir |
| `/guide` | pnj | staff (op 2) | point, points, retirer, visite |
| `/hdv` | boutique | tous |  |
| `/heraliz` | boutique | tous | cles (staff (op 2)), edit (staff (op 2)), editor (staff (op 2)), fichier, givecrate (staff (op 2)), givekey (staff (op 2)), interface (staff (op 2)), keyall (staff (op 2)), loot (staff (op 2)), preview, retirer, voir |
| `/heralizloot` | loot | staff (op 2) | ajouter, apparaitre, declencher, liste, point, reinitialiser, retirer |
| `/herals` | clans | tous | classement, payer |
| `/home` | bases | tous |  |
| `/inspecter` | monde | op 4 | base, coffres, inventaire, sac |
| `/journalieres` | pnj | tous | avancer (banc d'essai), essai (banc d'essai) (op 4), forcer (banc d'essai), minuit (banc d'essai), remettre (staff (op 2)), voir (staff (op 2)) |
| `/kit` | boutique | tous | reset |
| `/marchand` | pnj | op 4 | confirmer, editer, nom, nouveau, registre, role, supprimer |
| `/pb` | boutique | tous | stats, voir |
| `/perso` | voix | tous | open, random, reset |
| `/pnj` | pnj | staff (op 2) | apparence, aucune, bras, creer, deplacer, fichier, fins, larges, liste, moi, retirer, skin |
| `/protection` | bases | staff (op 2) | donner, retirer, voir |
| `/purge` | bases | tous | arreter (op 4), forcer (op 4) |
| `/quete` | pnj | staff (op 2) | ajouter, baguette, donner (banc d'essai), finir (op 4), joueur, nom, point, points, reset, retirer, tp |
| `/quetes` | pnj | tous |  |
| `/r` | noyau | tous |  |
| `/rarete` | loot | staff (op 2) | aucune, defaut, effacer, voir |
| `/reanimation` | survie | tous | revive (staff (op 2)) |
| `/recharger` | noyau | op 4 |  |
| `/redemarrage` | noyau | op 4 | annuler |
| `/rtp` | bases | tous |  |
| `/saison` | clans | tous | annuler, classement, confirmer, fin (op 4), journal (op 4), points (op 4), reset (op 4) |
| `/salle` | bunker | staff (op 2) | baguette, bleue, creer, delier, effacer, lier, liste, recharger, redefinir, retirer, rouge, selection, verte, voir |
| `/spartan` | anticheat | staff (op 2) | add (op 3), capture, checks, clear (op 3), esp, info, learn (op 3), reference, report, scan, setback, status, suspects, vl |
| `/spawn` | bases | tous | definir (staff (op 2)), effacer (staff (op 2)), voir (staff (op 2)) |
| `/staff` | boutique | tous | panel |
| `/suicide` | monde | tous |  |
| `/territoire` | clans | staff (op 2) | capturer, creer, deplacer, difficulte, drapeau, info, liste, ravitailler, reset, supprimer, valeur |
| `/tpa` | boutique | tous | accepter, refuser |
| `/trade` | boutique | tous | accepter, annuler, refuser |
| `/train` | monde | staff (op 2) | actif, decalage, gare, info, reparer, reseau, vitres |
| `/tuto` | bases | staff (op 2) | finir, lancer, rejouer |
| `/unbanpc` | anticheat | op 3 |  |
| `/usure` | monde | staff (op 2) | config, definir, reparer, voir |
| `/vehicules` | monde | staff (op 2) | donner, liste |
| `/vitres` | armes | op 4 | pourquoi |
| `/voix` | voix | tous | info, mute, mutes, stats, unmute |
| `/warp` | boutique | tous | del, description, icone, liste, nom, set |
| `/wipe` | pnj | op 4 | confirmer |
| `/zombies` | monde | staff (op 2) | ajouter, liste, reserves, retirer, stats, zone |
| `/zones` | monde | staff (op 2) | info |
| `/zonevip` | boutique | tous | baguette, coin1, coin2, creer, liste, retirer, tp, voir |
