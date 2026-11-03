# À faire et à vérifier — état au 04/11

**En ligne depuis le 03/11 au soir** : serveur, site (extension 1.4.1) et launcher **1.16.2**.
Versions : Noyau 1.0.16 · Tactique (armes) 2.48.1 · Bases 1.34.1-beta · Clans 3.14.0 · Loot 0.16.0 · Quêtes 1.19.0 ·
Spartan 2.11.1 · Boutique 1.5.2 · Interface 2.0.6 · Équipement 1.1.1 · Monde 1.0.13 · Réanimation 1.16.10 · Emotes 2.9.0.

**Rien n'a été testé en jeu.** Cette liste remplace la partie 2 de `docs/DEVLOG_25OCT_02NOV.md` (seules les règles
finales du 03/11 sont gardées). Détails : fiches `docs/TESTS_EN_JEU_03NOV_*.md`.
Pour chaque souci : capture d'écran, langue du jeu et heure (pour retrouver la ligne dans le journal du serveur).
Essais avec un compte joueur **sans droits de staff**, en survie ; pour le PvP, deux comptes de **clans différents**.

## 1. À faire d'abord (admins, une fois)

- [ ] Relancer le launcher : il passe seul en 1.16.2 (Windows, AppImage) ; Mac et `.deb` : réinstaller depuis heraliz.fr.
- [ ] Console du Ptero au démarrage : aucune ligne rouge, aucune erreur « Mixin » ; ligne `[armes] 118 armes réglées… 12 armes à plombs`.
- [ ] Ptero, `dayz_map/serverconfig/crac-server.toml` : `lifetimeItemsOwnerOnly = false` et `allowedItemsInClaims = []`.
- [ ] Ptero, `dayz_map/serverconfig/heraliz_noyau-server.toml` : `aide` dans les deux listes, `sethome` dans `commandes_permises`.
- [ ] Ptero, `config/spartan-common.toml` : `ancienLauncher = "refuser"`, `required = true`, `[injection] expulser = true`.
- [ ] Ptero, dossier `/tacz` : `maxstuff_deagle_12g.json`, `maxstuff_thunderbird_short.json`, `heralisz_heal_attelle_bandage.json` ont disparu.
- [ ] `/heralizloot beta` : « objets x1.5 (au plus 20 par conteneur), Rare / Épique / Légendaire x1.5 », puis la ligne
      « Économie de la bêta : achats x0.75, rachat x1, recycleur x1.25, améliorations x0.75, quêtes x1.25 ».
- [ ] Poser les 3 hologrammes au spawn, en visant le sol : `/classement poser argent`, `poser kills`, `poser clans` ;
      `/classement liste` les montre.
- [ ] Poser une ou deux cibles d'essai au stand de tir : `/give @s heraliz_armory:cible_essai`, clic droit au sol.
- [ ] Si un compte joueur ne peut pas ouvrir la cible ou ne voit aucun chiffre : `/rg flag <région du stand> interact allow`.
- [ ] Si pas encore fait : commissaire-priseur (`/pnj creer commissaire`) et points de la visite du Guide (`/guide point …`).
- [ ] Site : double authentification pour les comptes admin ; anti-robot de la création de compte actif.
- [ ] LuckPerms, pour les joueurs : `bukkit.command.plugins`, `bukkit.command.version`, `authme.player.unregister`,
      `authme.player.email` à faux.

À savoir en cas de souci (fichiers relus sans redémarrer) : Spartan `[suiviMurs] enabled = false`,
`[gardien] preuveExigee = "alerte"`, `[injection] expulser = false`, `ancienLauncher = "alerte"` (si trop de joueurs
Mac bloqués) ; loot et économie de la bêta : `[beta] actif = false` dans `heraliz_loot-server.toml`.

## 2. Priorité 1 : les gros systèmes

### Connexion, launcher, anticheat
- [ ] Launcher 1.16.2 : « Vérification des fichiers du jeu… » puis partie normale ; plusieurs joueurs à la suite : tous entrent.
- [ ] Joueur resté sur la carte : `/login` (et captcha ou code) passe ; attendre 3 min au `/login` puis jouer : pas de refus.
- [ ] « ✅ Jeu validé » dans le fil Discord du joueur (ou « Jeu validé (en retard) ») ; `/spartan status <joueur>` : ligne « validation ».
- [ ] Gardien : « actif, rapports signés » ; plus d'alerte « n'arrive pas à lister les programmes », même sur un PC chargé.
- [ ] Ancien launcher (1.15.x) : connexion refusée, message « pas à jour » avec le lien entier sur sa ligne (fr, en, ru).
- [ ] Jeu lancé hors du launcher (CurseForge, Prism…) : refusé.
- [ ] Launcher fermé de force juste après le lancement : refus vers 2 min après le `/login` ; en pleine partie : alerte « Gardien muet », pas d'expulsion.
- [ ] (serveur d'essai) Cheat Engine ouvert en jeu : alerte « Injection », capture, expulsion en moins de 30 s ; ensuite le launcher refuse de lancer (message au staff).
- [ ] Joueur honnête avec overlay Discord ou Steam, OBS, MSI Afterburner, antivirus : aucune expulsion, aucune alerte « Injection ».

### Mort, réapparition, home
- [ ] Mort, « Au camp » : spawn. « À la base » : au home posé par `/sethome`, puis « Disponible dans 7:5x » ; déco sur l'écran de mort et retour : bon temps.
- [ ] Base sans home : carte « À la base » fermée, « Ta base n'a pas encore de home » ; `/home` le dit aussi.

### Objets à vie (règle du 03/11)
- [ ] Mourir avec un couteau à vie dans l'inventaire, la barre ou au curseur : rendu à la réapparition ; inventaire plein : « gardé », puis rendu.
- [ ] Alt+F4 avec le couteau au curseur (coffre ou sac ouvert) : dans l'inventaire au retour, une seule fois.
- [ ] Touche Q en jeu, aucun écran ouvert, couteau en main : il ne tombe pas, **aucun message**.
- [ ] Ranger dans un coffre, son sac, la planque, un établi, un meuble (clic, clic droit, glisser, Maj+clic, touches 1-9) : question « Ranger cet objet ici ? » à chaque fois.
- [ ] « Non » ou Échap : il revient dans l'inventaire ; « Oui » : il est dans la case ; le déplacer dans le coffre : plus de question.
- [ ] Une fois rangé, plus protégé : rangé dans le sac porté puis mort → dans le sac sur le corps ; dans un coffre → il y reste, jamais de retour automatique.
- [ ] Jeté au sol (Q dans l'inventaire, confirmation) : ne brûle pas ; personne ne le prend → il disparaît au temps normal ; un autre le ramasse → il est à lui.
- [ ] Pilleur d'un coffre de base : prend le couteau comme un objet normal ; coffre détruit au C4 : couteau au sol (plus envoyé au chef).
- [ ] Fouille d'un joueur à terre : le sac part avec le couteau rangé dedans ; un couteau dans son inventaire ne se prend pas.
- [ ] Toujours refusé : conteneurs de loot, corps, largage, recycleurs, coffre du clan, caisses de ravitaillement, présentoirs, HDV, Mila.

### Tutoriels
- [ ] Tutoriel des bases avec inventaire plein, armure, sac porté, objet à vie : « Ton inventaire est mis de côté », seulement le kit.
- [ ] Fin : retour au spawn, tout rendu à sa place, une seule fois ; ce qui ne tient pas arrive dès qu'une case se libère, rien au sol.
- [ ] Déconnexion, arrêt du serveur ou mort en plein tutoriel : on y revient, rien ne tombe ; à la fin, tout rendu une seule fois.
- [ ] Sortie anormale (staff `/tp` vers la carte, monde du tutoriel absent) : tutoriel validé, kit repris, inventaire rendu, pas à refaire.
- [ ] Visite du Guide et tutoriel de Varga : chat caché ; T ou / ouvre « Appeler le staff » ; message reçu par le staff en jeu et sur Discord.
- [ ] Deuxième appel dans la minute : refusé ; `/aide <message>` marche aussi hors tutoriel, en combat et sur la carte.
- [ ] Fin du tutoriel : Varga explique `/sethome <nom>` dans la vraie base, puis `/home` (fr, en, ru).

### Bases
- [ ] Pose d'une armoire : message doré « tape `/sethome <nom>` » ; aucun home créé tout seul.
- [ ] `/sethome` debout sur une fondation de sa base : « Home posé ici », membres prévenus ; `/home` du spawn arrive exactement là.
- [ ] `/sethome` refusé sur le sol, un plancher, un toit, une caisse, hors zone, sans le droit « Gérer la base », en combat : message rouge.
- [ ] Un seul home par base (le nouveau remplace l'ancien) ; home bouché ou fondation démolie : `/home` refusé, jamais ailleurs.
- [ ] Base posée avant le 03/11 : `/home` devant l'armoire tant que personne ne fait `/sethome`.
- [ ] Usure : armoire sans pierres taillées → seules les pièces en pierre perdent environ 10 %/h ; remettre les pierres : ça s'arrête.
- [ ] Démolition : ligne de 3 fondations, celle du milieu refusée (« démolis depuis le bout »), celle du bout permise.
- [ ] Coffres de base (malle, casier, caisse d'armes, coffre-fort, caisse en métal) : sans texte, contenu gardé, ni clignotement ni scintillement.
- [ ] Fondation carrée fermée : 9 caisses en métal, rien ne dépasse ; ramasser une caisse : le mur se rebouche.
- [ ] Carré posé contre chacun des 3 côtés d'une fondation triangle : accepté, tourné comme le côté.
- [ ] Caisse sur un carré tourné ou un triangle : seulement entièrement sur le sol et sans toucher un mur (2 à 5 sur un carré tourné, 0 ou 1 sur un triangle), sinon refus clair.
- [ ] Cœur de clan : se pose dans sa base (chef ou « Gérer la base ») ; ailleurs ou deuxième cœur : refusé ; clic droit d'un membre : onglet Saison.
- [ ] Échelle de raid : fabriquée (onglet Divers), posée contre la face d'un mur droit, même d'un autre clan : un mur de haut ; on grimpe ; une 2e au-dessus pour l'étage.
- [ ] Sur l'échelle : impossible de tirer (« Impossible de tirer sur une échelle ») ; au sol : on tire ; C4 ou charge à côté : elle part.
- [ ] Sauvegarde : abîmer, améliorer, poser coffres et échelles, `/save-all`, redémarrer : tout est comme avant.

### Tir
- [ ] Chaque fusil à pompe contre un mur : plusieurs impacts dispersés (DB-4 Ursus environ 8 ; Stakeout en visant aussi).
- [ ] Sans armure, à bout portant : M870 → 2 tirs tuent, jamais 1 ; tir à la tête sans casque : il survit.
- [ ] Minigun, longues rafales à deux : plus d'alerte « tirs refusés par TaCZ ».
- [ ] Balles à blanc : tirer en continu, couper le Wi-Fi 1-2 s : l'arme retire de vraies balles sans changer d'arme, on peut recharger.
- [ ] Combat à deux en mouvement : les balles au centre du corps comptent (marqueur, dégâts).
- [ ] Bon gilet sans casque : la tête n'est pas protégée ; bon casque sans gilet : le corps n'est pas réduit.

### Économie
- [ ] Marchands (bêta) : Jacqueline 23 / 60 / 68 / 135 / 300 ; Jack 180 / 450 / 1 050 ; Bâtisseur : caisse 225, cœur 3 750 ; le solde baisse du prix affiché.
- [ ] Mila : métal renforcé 150 ; arme neuve au prix d'avant, arme à 50 % à moitié prix.
- [ ] Recycleur du spawn : aperçu 12,5 % par composant (13,75 % sur celui de la carte) ; 6 armes dedans + Alt+F4 → tout dans l'inventaire au retour.
- [ ] Casino : gain sur le compte de capsules (rien dans l'inventaire) ; miser avec 0 capsule sur soi mais un compte rempli : ça marche.
- [ ] Écrans du casino (gros gagnants, record) : pseudo d'un joueur déconnecté, plus un bout d'identifiant.
- [ ] Panneau P, onglet Économie (Fondateur seulement) : hérals, clés, PB, points de saison, XP du clan ; deux clics, motif obligatoire, ligne dans l'Historique et sur Discord.
- [ ] Banque du clan : dépôt et retrait puis redémarrage : soldes justes, rien en double.
- [ ] Arbre du clan : palier 1 à 7 500 hérals ; quête de Varga affichée 500 → 625 versés.

### Loot de la bêta (×1,5)
- [ ] Dix conteneurs T2-T3 puis T5-T6 remplis après la mise à jour : plus d'objets (T3 ~3,8, T6 ~11,5), jamais plus de 20, plus de bleus et violets.
- [ ] Coffre T7 : 10 à 20 objets, l'identification va jusqu'au bout.
- [ ] Une trentaine de zombies : environ un sur trois lâche deux objets.
- [ ] Conteneurs de loot : on prend tout, rien n'entre (clic, glisser, Maj+clic, touches 1-9).
- [ ] `actif = false` : `/heralizloot beta` dit « coupé » sans redémarrer, prix et recycleur redeviennent normaux ; remettre `true`.

### Quêtes
- [ ] Étape « fouiller » (Varga, quêtes du jour, quêtes de clan) : compte à la première ouverture d'un conteneur qui contient quelque chose ; vide : rien.
- [ ] Quête de clan « Déposer N hérals » : retirer puis redéposer n'avance pas ; kill d'un compte de la même connexion : ne compte pas.
- [ ] Quêtes du jour (J) : trois quêtes ; livrer refusé sur la carte et en combat ; « Journée terminée » jusqu'à minuit.

### Clans
- [ ] Retirer de l'XP (panneau P) sous le palier : le clan redescend de niveau, améliorations gardées, membres en trop gardés mais plus d'invitation.
- [ ] Membres : 4 au niveau 1 (le 5e refusé), puis 5, 6, 7, 8 aux niveaux 2, 5, 10, 15 ; ligne « Membres » en tête de l'arbre, rien à payer.
- [ ] `/clan don` au spawn : Épique 150 / Légendaire 1 000 / Unique 2 500 XP ; infobulle dorée « Se donne au clan » ; refusé sur la carte.

### Extractions et zones
- [ ] 6 extractions ouvertes, changées toutes les 30 min ; menu Échap : les 3 plus proches ; `/extractions` : les 6.
- [ ] Cercle d'extraction : sol teinté, bord net, petit mur bas ; rouge → orange → vert ; bien rond même au milieu de caisses.
- [ ] Plan en main : cases interdites rouges au sol, bord de sa base vert, des autres rouge ; à travers le décor seulement à moins de 10 blocs.

## 3. Priorité 2 : le reste, par thème

### Armes et combat
- [ ] Infobulles : M870 « 17,5 » (ou « 1,94 x 9 »), AA-12 « 12,96 », DB-4 Ursus « 14,4 ».
- [ ] Porte fermée ou mur entre deux joueurs collés : ni balle ni coup ; porte ouverte ou fenêtre : ça passe.
- [ ] Silencieux : plus de trait lumineux ; son net seulement à moins de 10 blocs.
- [ ] Deagle .12g, Thunderbird Short, Attelle et bandage : plus dans le loot, la Table d'armurier ni le créatif ; un exemplaire déjà possédé marche.
- [ ] « Kit de survie militaire » (ancien Surv12) : bon nom en fr, en, ru ; soigne comme avant.
- [ ] Plus de case de main gauche (sauf créatif) ; F3+B ne montre rien en survie.
- [ ] Infobulles des camos, stickers, StatTrak : nom, rareté, une ligne ; rien ne dépasse en anglais et en russe.
- [ ] Camo sur un sac : compte sans op → aucun résultat ; compte op → sac camouflé ; skin à vie jamais accepté.
- [ ] Icône « s'allonger » (bas gauche) : blanche prête, verte allongé et pendant tout le plongeon, ambre avec les secondes pendant le délai, grise barrée à terre ou en véhicule.
- [ ] Après un plongeon : 6 s avant le suivant (anneau clair et secondes) ; s'allonger sur place reste permis.

### Couteaux et batte
- [ ] Couteau TaCZ, couteau à vie ou batte en main : environ +10 % de vitesse ; rangé : rien ; aucune alerte de vitesse.
- [ ] Portée : touche collé à la cible, plus à 2 blocs (batte aussi) ; coup de grâce sur un joueur à terre : marche ; aucune alerte « Reach ».
- [ ] Le coup tombe quand la lame passe ; batte : chaque geste frappe ; le frappeur entend l'impact ; dans le vide : aucun bruit.
- [ ] Toujours 1,2 s entre deux coups ; dégâts de mêlée divisés par deux ; armes de mêlée à vie : pas d'usure, ne cassent jamais.
- [ ] Étoiles du rang dans l'infobulle : 4 étoiles nettes (or ou contour gris) aux échelles 2, 3 et 4.

### Soins et survie
- [ ] Chaque soin va au bout de son animation ; changer de case pendant le soin l'arrête ; manger en regardant un mur : marche.
- [ ] Portes en bois et trappes : s'ouvrent avec nourriture, soin, batte ou pistolet en main ; porte d'un autre clan : verrouillée.
- [ ] Kit de départ : une attelle en plus ; jambe cassée : on frappe normalement.
- [ ] Réanimation : le temps descend jusqu'à 0 ; interrompue, il disparaît. `/suicide` : 5 min entre deux.

### Monde et zombies
- [ ] Ville parcourue 30 min : aucun zombie équipé (épée, pelle, armure, citrouille) ; tenues inchangées.
- [ ] Pas des autres joueurs : mieux entendus de près, encore à 16-20 blocs, plus rien après 24 ; direction juste ; accroupi et staff caché : silencieux.
- [ ] Pas des zombies : volume normal, comme avant le 03/11.
- [ ] Cercle de capture des drapeaux : même dessin que les zones ; blanc libre, vert à soi, rouge à un autre, orange en capture.
- [ ] Zombies : environ 8 sur 10 lâchent quelque chose ; ne ramassent plus une arme jetée ; se massent sous un toit sans se figer.
- [ ] Largage (`/event lancer largage avec_points`) : caisse sur sol dégagé, +8 points au premier clan ; refusé pendant un ClashWar, permis après.
- [ ] Objets d'événement chez Mila : 120 / 300 / 700 hérals et +2 / +4 / +8 points ; une copie est refusée.

### Bases (suite)
- [ ] Cœur de clan : nouveau modèle fixe (socle graphite, cristal cyan qui éclaire) ; autre clan hors Purge : « ne se prend que pendant la Purge ».
- [ ] Table d'armurier (ou four, établi) contre un mur droit : fantôme exact, le mur reste plein dedans et dehors ; une seule table par base.
- [ ] Barricade : 18 planches, 18 clous, 3 cordes.
- [ ] Refus de construction dits une seule fois (sous le viseur) ; plus de texte brut « crac.build.need… ».
- [ ] Partie coupée de l'armoire (C4 pendant la Purge) : elle s'use ; le plan dit « Cette partie n'est plus reliée au bâtiment de ton armoire ».
- [ ] Échelle refusée : hors base, mur en biais, mur avec porte, base encore protégée, quelqu'un collé au mur.
- [ ] Échelle : environ 10 coups de poing la cassent ; mur détruit : elle tombe ; recycleur : rend sa recette ; aucune alerte en grimpant.
- [ ] Purge (`/purge forcer 30`) : un C4 abîme une base neuve ; hors Purge, les explosifs ne font rien.

### Clans et saison
- [ ] Hologrammes : même ordre que `/herals classement` et l'onglet Saison ; joueur déconnecté avec son pseudo ; staff caché absent.
- [ ] Kills : +1 au tueur sous 2 min ; ne comptent pas : zombie, camarade, même adresse, joueur réapparu depuis moins de 60 s, ClashWar.
- [ ] Hologrammes après un redémarrage : toujours là, une seule fois ; `/classement retirer` marche ; aucune baisse d'images.
- [ ] Onglet Saison (K) : classement, son clan surligné, barème ; rien ne déborde en anglais et en russe.
- [ ] Capture : clan niveau 1 à 4 verrouillé ; niveau 5 + Territoires : 1 zone ; niveau 10 : 2.

### Économie (suite)
- [ ] Hôtel des ventes : vente à 10 000 → taxe 1 250 confirmée en deux clics ; refus hors du spawn, en combat, à terre.
- [ ] Atelier de réparation : arme posée puis déconnexion → dans l'inventaire au retour.
- [ ] Caisses, kits, `/trade` annulé avec inventaire plein : « t'attend », l'objet arrive dès qu'une case se libère.
- [ ] Noms d'objets dans les messages (caisses, marchands, HDV, `/clan don`, réparation) : vrai nom, jamais « item.tacz… ».

### Interface et textes
- [ ] Lettres nettes (quêtes, clan, HDV, boutique) aux échelles 2, 3, 4 et Auto ; « Quêtes du jour » avec son espace ; russe net.
- [ ] Gras et ombre propres dans le chat (annonces ClashWar, grades, événements) : plus de lettres doublées.
- [ ] Petits écrans (quêtes, HDV, kits, warps, marchand) plus grands en fenêtre 1080p ; rien ne sort en 1366 × 768 ; clics justes.
- [ ] Roue des emotes (Y) : nouveau dessin ; clic ou touches 1-8 jouent l'emote ; emote pas débloquée grisée avec cadenas.
- [ ] Chat Proximité seul à plus de 48 blocs : son message s'affiche, aucun autre texte.

### Anticheat
- [ ] Suivi à travers les murs (serveur d'essai, seuils baissés) : ligne dans `suivi.log`, alerte et capture.
- [ ] Jamais de ligne pour : vitre, porte, B qui tire, parle ou marche debout, même clan ; partie normale de 30 min : `suivi.log` vide.
- [ ] Sprint, véhicules, allongé, téléportations, mauvais Wi-Fi : aucune alerte de vitesse ; tir normal : pas d'alerte « Tirs ».
- [ ] Jar posé dans `mods` : mis de côté, message au staff ; jar modifié : retéléchargé sans message ; trois essais : un seul message.
- [ ] x64dbg, Process Hacker, fichier « Cheat Engine.txt » ouvert : alerte « Gardien » seulement, pas d'expulsion.

### Staff
- [ ] Cible d'essai, compte joueur : « Seul le staff peut poser une cible d'essai », l'objet reste en main.
- [ ] Cible : écran des tenues complètes ; chiffre au-dessus (doré à la tête), vu du tireur seul ; récapitulatif dans le chat après 4 s.
- [ ] Cible : AK-47 Rare sans armure, corps 6,0 / tête 10,2 ; mêmes dégâts qu'un vrai joueur ; AA-12 jamais plus de 13 par tir ; rien à farmer.
- [ ] Panneau P : Admin et Modérateur ne voient pas l'onglet Économie ; journaux `journal_staff.log`, `journal_admin.log`, `cles_journal.log`.
- [ ] `/diag profil 30` : résumé toutes les 10 s puis rapport ; au spawn avec les hologrammes : rien de visible.
- [ ] Après une soirée : `config/spartan/tirs.log` avec peu de refus ; journal : `[armes] horloge… recalée` après une coupure.

## 4. Questions encore ouvertes

- Kills des hologrammes : remis à zéro à chaque saison ? Aujourd'hui seulement avec `/wipe`.
- Staff non caché : le retirer aussi des classements ?
- Fusils à pompe : 2 décharges tuent maintenant une armure Épique (avant 3) ; d'accord ? +4 % par rang de rareté suffit ?
- Bêta : rester à ×1,5 ou passer à ×2 (environ 4 fois plus d'objets rares) ?
- Bêta : cœur de clan à 3 750 ou le garder à 5 000 hérals ?
- Bêta : largage, caisse de ravitaillement, quêtes du jour et caisses donnent aussi plus ? Rachat de Mila et revenu des territoires ?
- Échelle sur un mur posé sur un demi-mur : elle dépasse d'une demi-case en haut ; ça va ? Toit qui déborde : gênant ?
- Pas des joueurs : volume (1,3) et portée (24 blocs) à ajuster après l'essai ?
- Cœur de clan au casino (20 000 capsules) ; jackpot gagné seulement au gain maximal d'une machine ?
- Portée des traçantes (64, 48 ou 32) ; roquette dans le vide sans explosion ; boutique depuis la carte ; pistolet qui ouvre les portes ?
- Zombies : composants de niveau II (1 à 7 d'un coup) ; portée de poursuite ?
- `/recharger` pour le staff de niveau 2 ? LiteBans pour un staff en combat ? Uniques en T7 (1 coffre sur 50) ? Zones VIP ?
- Nouvelle carte de loot : dossier du Ptero ou Release GitHub, avec ou sans wipe ?
- Ouverture à tous : quand retirer la liste blanche ?

## 5. Reporté ou pas faisable

- Échelle sur un mur en biais : pas possible proprement.
- 9 caisses tournées sur un carré tourné : impossible (les murs en biais prennent les blocs) ; objets droits, donc peu par carré tourné.
- Pas de caisse « dans le mur » le long d'un mur en biais ; petites marches d'un pixel au bord d'un carré tourné (F3+B).
- Le marteau ne prévient pas à l'avance qu'une démolition couperait la base : le refus vient au clic.
- Coûts de la Table d'armurier non réduits par la bêta (recettes du pack d'armes).
- Avec du ping, la traçante à l'écran n'est pas ce que juge le serveur : pas corrigeable (viser le centre du corps).
- Lien du launcher dans le message de refus : pas cliquable (écran de Minecraft).
- Cible d'essai : pas de saignement, poison ni brûlure ; armure toujours neuve.
- Boss mis de côté ; signature Windows du launcher (certificat payant), launcher « scellé », clé SSH du Ptero, clé des jetons de l'anticheat.
- Renvoi en arrière de l'anticheat coupé ; seuils du suivi à travers les murs à revoir après une semaine de `suivi.log`.
- Coffre d'un clan dissous quand le chef est hors ligne : toujours perdu.
- Sauvegarde des clans et de l'hôtel des ventes d'un seul bloc (sûr, un peu lourd).

## Doutes relevés en écrivant cette liste

- La fiche anticheat dit « launcher 1.16.2 pas publié » : il est compté comme publié (en ligne le 03/11 au soir).
- Fiche de la cible : « plafond 13 » pour tous les fusils à pompe ; le réglage final dit 13 pour les automatiques, 19 pour les autres.

