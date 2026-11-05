# Wipe de la réouverture (05/11) — ce qu'il faut regarder

Commande : `/wipe reouverture` (op 4 ou console), puis `/wipe reouverture confirmer <code>` (1 min), puis
`/wipe reouverture valider <code>` (30 s), 10 s de compte à rebours (`/wipe reouverture annuler` pour arrêter).
Code : `modules/pnj/.../quetes/serveur/WipeServeur.java` (`lancerReouverture`), `Commandes.remettreAZero(..., complet)`.

Choix du propriétaire (05/11) :
- épargnés : grade **Fondateur** ou **Admin** (fichier des grades ; la 1re étape affiche leurs pseudos) ; ils gardent
  toutes leurs affaires, mais leur clan et leur base partent comme ceux de tous ;
- gardés pour tous : **PB** (site), **clés de vote**, clés achetées pas encore ouvertes (Gemme, Camo, emotes), grades ;
- tout le reste repart à 0, contenu à vie compris (couteaux, skins à vie, camos, emotes), et les stats de kills.

## À vérifier en jeu (rien n'a été testé par Claude)
- [ ] Un joueur normal, à sa reconnexion : inventaire, sac, Curios, coffre de l'Ender, planque, coffre VIP vides ;
  hérals à 0 ; capsules à 0 ; plus de couteau ni de skin à vie ; `/camos voir <joueur>` vide ; emotes toutes grisées ;
  quêtes et quête du jour à zéro ; il refait le créateur de personnage, la cinématique et le kit de départ.
- [ ] Ses clés : « Mes clés » de `/boutique` garde les clés **vote**, Gemme, Camo et emotes ; plus de clés de quête,
  d'événement, de chantier, trauma, largage, boss ni de stickers.
- [ ] Ses ventes à l'hôtel des ventes ont disparu (même s'il n'est pas revenu).
- [ ] Naro / Patoches_ (Fondateurs) : inventaire, coffres, hérals, couteaux, camos, emotes et clés gardés ; plus de clan
  ni de base ; un couteau qui était dans un coffre de leur base revient dans leur inventaire (ou à la connexion).
- [ ] Plus aucun clan, aucune base (armoires, pièces), hologramme « Meilleurs tueurs » vide, classement des clans et du
  ClashWar vides, nouvelle saison (`/saison`).
- [ ] Journal du serveur : ligne « remise à zéro de la réouverture n°… » avec les nombres, aucune erreur `[Quêtes] /wipe`.
- [ ] Solde de PB inchangé sur le site.
