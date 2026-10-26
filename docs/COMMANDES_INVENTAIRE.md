# Inventaire des commandes (écrit par outils/commandes.py)

Lu dans les sources de modules/ (les plugins Bukkit — AuthMe, LiteBans, LuckPerms, WorldEdit, WorldGuard, spark — et Minecraft lui-même ne sont pas comptés). « tous » = aucune permission demandée à l'entrée de la commande (une sous-commande ou le code peut en demander une).

**68 commandes de premier niveau, 304 sous-commandes repérées.**

| Commande | Module | Entrée | Sous-commandes (niveau si différent) |
|---|---|---|---|
| `/airstrike` | monde | staff (op 2) | call, clear |
| `/armurier` | armes | op 4 | editer, registre |
| `/avantages` | boutique | tous | couleur, nick, qui, reset |
| `/banpc` | anticheat | op 3 |  |
| `/base` | bases | op 4 | effacer, info |
| `/bon` | boutique | tous | editeur |
| `/boss` | events | staff (op 2) | arene, decors, eveil, labo, mammouth, mere, n07, peste, reset, salle, station, sylvorn, vitrine |
| `/bots` | banc | op 4 | action, etat, frappes, joueurs, lancer, retirer, scenario, stop |
| `/boutique` | boutique | tous | abonnement |
| `/brume` | monde | staff (op 2) | auto, info |
| `/camos` | armes | staff (op 2) | ajouter, recharge, retirer, voir |
| `/carriere` | bases | staff (op 2) | creer, liste, regenerer, retirer, tp |
| `/carte` | monde | staff (op 2) | ajouter, lieu, liste, nom, retirer |
| `/casino` | boutique | staff (op 2) | clochard, defaut, donner, jackpot, jetons, lumiere, machine, reload, reset, retirer, rtp, salle, solde, stats, vip, voir |
| `/cinematique` | monde | staff (op 2) | decaler, devant, epave, info, joueur, poser, retirer, reveil, tourner |
| `/clan` | clans | tous | ameliorer, don |
| `/clashwar` | clashwar | tous | annuler (staff (op 2)), classement, cloturer (staff (op 2)), lancer (staff (op 2)), mur (staff (op 2)), quitter, recharger (staff (op 2)), regarder, retirer, salle, tableau |
| `/coeur` | bases | tous | deplacer |
| `/combat` | noyau | staff (op 2) | effacer, etat, marquer |
| `/corps` | monde | staff (op 2) | liste, supprimer, tp, vider |
| `/depart` | monde | staff (op 2) | ajouter, borne, essai, info, joueur, liste, point, retirer, tp, zone |
| `/diag` | noyau | op 4 | auto, client, etat, fuites, mesure, off, on, seuil, start, stop |
| `/diagclient` | noyau (joueur) | tous |  |
| `/discord` | boutique | tous | delier, lier |
| `/eco` | clans | staff (op 2) | definir, donner, info, journal, recharger, retirer, sauvegarder |
| `/emote` | emotes | staff (op 2) | debloquer, retirer, voir |
| `/extraction` | monde | staff (op 2) | close, create, label, list, mode, next, open, remove, rotation, tp |
| `/extractions` | monde | tous |  |
| `/grade` | boutique | tous | acheter, donner, editeur, liste, registre, retirer, voir |
| `/heraliz` | boutique | tous | add, edit (staff (op 2)), editor (staff (op 2)), fichier, givecrate (staff (op 2)), givekey (staff (op 2)), interface (staff (op 2)), keyall (staff (op 2)), list, loot (staff (op 2)), preview, reload (staff (op 2)), remove |
| `/herals` | clans | tous | classement, payer |
| `/home` | bases | tous |  |
| `/inspecter` | monde | op 4 | base, coffres, inventaire, sac |
| `/kit` | boutique | tous | reset |
| `/marchand` | pnj | op 4 | confirmer, editer, nom, nouveau, registre, role, supprimer |
| `/pb` | boutique | tous | stats, voir |
| `/perso` | voix | staff (op 2) |  |
| `/ping` | monde | tous |  |
| `/pnj` | pnj | staff (op 2) | apparence, aucune, bras, creer, deplacer, fichier, fins, larges, liste, moi, retirer, skin |
| `/protection` | bases | staff (op 2) | donner, retirer, voir |
| `/purge` | bases | op 4 | arreter, forcer, recharger |
| `/quete` | pnj | staff (op 2) | ajouter, baguette, donner, finir, joueur, nom, point, points, recharger, reset, retirer, tp |
| `/quetes` | pnj | tous |  |
| `/r` | noyau | tous |  |
| `/recharger` | noyau | op 4 |  |
| `/redemarrage` | noyau | op 4 | annuler |
| `/rtp` | bases | tous |  |
| `/saison` | clans | tous | classement, commencer (op 4), confirmer, journal (op 4), points (op 4), recharger (op 4), terminer (op 4) |
| `/salle` | bunker | staff (op 2) | baguette, bleue, creer, delier, effacer, lier, liste, recharger, redefinir, retirer, rouge, selection, verte, voir |
| `/sbaninfo` | anticheat | staff (op 2) |  |
| `/spartan` | anticheat | staff (op 2) | add (op 3), bench (op 3), capture, checks, clear (op 3), esp, info, learn (op 3), officiel (op 3), reference, report, scan, selfscan (op 3), setback, status, suspects, vl |
| `/spawn` | bases | staff (op 2) | definir, effacer, voir |
| `/staff` | boutique | tous | nametag, panel, vanish |
| `/suicide` | monde | tous |  |
| `/territoire` | clans | staff (op 2) | capturer, creer, deplacer, difficulte, drapeau, info, liste, ravitailler, recharger, reset, supprimer, valeur |
| `/tpa` | boutique | tous | accepter, refuser |
| `/trade` | boutique | tous | accepter, annuler, refuser |
| `/train` | monde | staff (op 2) | actif, decalage, gare, info, reparer, reseau, vitres |
| `/tuto` | bases | staff (op 2) | finir, lancer, rejouer |
| `/unbanpc` | anticheat | op 3 |  |
| `/usure` | monde | staff (op 2) | config, definir, reparer, voir |
| `/vehicules` | monde | staff (op 2) | donner, liste |
| `/vitres` | armes | staff (op 2) | pourquoi |
| `/voix` | voix | staff (op 2) | info, mute, mutes, stats, unmute |
| `/warp` | boutique | tous | del, description, icone, liste, nom, set |
| `/wipe` | pnj | op 4 | confirmer |
| `/zones` | monde | staff (op 2) | info, recharger |
| `/zonevip` | boutique | tous | baguette, coin1, coin2, creer, liste, retirer, tp, voir |
