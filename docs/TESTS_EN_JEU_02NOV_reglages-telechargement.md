# Essais du chantier « réglages et téléchargement » (audit du 02/11, points 11, 12, 35, 37 et 38)

Rien n'a été essayé en jeu ni fabriqué avec Gradle (dépôt de Forge bloqué ici). Ce qui a tourné ici : une compilation
partielle avec `javac` (sans Minecraft ni Forge : seules les erreurs sur nos propres classes sont visibles, aucune
trouvée), le mixin du noyau compilé contre l'API réelle de Forge 1.20.1 (lue dans ses sources), les copies allégées de
TaCZ et des meubles (faites, relues, identiques d'une fabrication à l'autre, mêmes fichiers que l'original sauf ceux
retirés), l'allègement des gunpacks sur une copie de `serveur/tacz` avec contrôle complet, et les contrôles des outils
(`verifier_langues.py` 0 erreur, `polices_partagees.py --verifier`, `polices_nettes.py`, `caisses.py`, `silencieux.py`).
La compilation complète et `verif_split` se feront au contrôle de branche de GitHub.

## Ce qui a changé

| Point | Changement | Commit |
|---|---|---|
| 12 | Nos jars compilés sans les noms des variables locales (`-g:source,lines`, numéros de ligne gardés pour `/diag`) ; tout jar refait quand la fabrication commune change ; `outils/brouiller.py` retiré | 9df2c6eb |
| 35 | TaCZ livré sans son pack d'armes intégré (54,8 → 5,4 Mo), `.export-state.json` retiré | cae56fa1 |
| 37 | Meubles (cfm) livrés sans leurs textures pour OptiFine (33,0 → 26,0 Mo) | 23f4eccb |
| 38 | 19 réglages « joueur » et 5 réglages des boss retirés du serveur ; option « reinitialiser_bases » retirée de la mise en ligne ; `flyHoverItems` de Spartan vidé | 9a2c36d7 |
| 38 | Texte du banc : `crac:tuto` au lieu de `crac:bases` (Banc 1.2.1, Monde 1.0.11) | 04d98231 |
| 11 §3 | 9 fichiers de réglages du serveur ne partent plus chez chaque joueur (Noyau 1.0.13) | 23ec1a16 |
| 11 §4 | Réglages de l'extraction gardés au serveur (Monde 1.0.11) | 4a3c3872 |
| 11 §4 | Réglages COMMON de heralisz_tactical gardés au serveur (Tactique 2.43.1) | 8b289f05 |
| 11 §4 | Cartes d'accès : réglages gardés au serveur, passages envoyés par le serveur (Bunker 0.13.6) | f15f09d8 |
| 11 §4, §8 | Armurerie : réglages gardés au serveur ; nage, bouteille, recharge et slots bloqués envoyés par le serveur (Équipement 1.0.3) | b88b786f |
| 11 §4, §8 | Clans et hérals : réglages gardés au serveur ; prix de création envoyé par le serveur (Clans 3.11.1) | abbfc0b0 |
| 11 §7 | Commandes console de la caisse du casino plus envoyées aux joueurs (Boutique 1.4.1) | a75c4906 |
| 37 | Polices DM Sans et Onest : une seule copie, dans le noyau (8 autres modules montés : Bases 1.30.1-beta, Emotes 2.8.9, Interface 2.0.4, Loot 0.14.7, Quêtes 1.17.1, Réanimation 1.16.9, CaraCrea 1.6.6, boss 2.2.5) | 55d11bc1 |
| 37 | Doublons des gunpacks retirés de la copie des joueurs (12,7 Mo) | fdbe036a |

## Poids du téléchargement d'un joueur (installation complète, sans Minecraft ni Forge)

| Partie | Avant | Après | Comment |
|---|---|---|---|
| Mods des autres auteurs | 143,0 Mo | 86,6 Mo | mesuré : TaCZ −49,4, meubles −7,0 |
| Dossier `tacz` (gunpacks, sans `data/`) | 191,9 Mo | 179,2 Mo | mesuré : −12,7 |
| Nos jars | ≈ 160 Mo | ≈ 157 Mo | estimation de l'audit (pas de fabrication ici) ; polices −1,8 Mo mesuré, noms des variables ≈ −1 Mo estimé |
| **Total** | **≈ 495 Mo** | **≈ 423 Mo** | **≈ −72 Mo (−15 %)**, dont 70,9 Mo mesurés |

À la première mise à jour, chaque joueur retélécharge une fois tous nos jars (≈ 157 Mo : ils sont tous refabriqués avec
la nouvelle option du compilateur), la copie allégée de TaCZ (5,4 Mo) et des meubles (26 Mo), et 35 fichiers de
gunpacks réécrits ; le launcher efface tout seul les fichiers sortis du pack.

## Les fichiers du Ptero gardent leurs valeurs (rien à faire à la main)

- **Réglages SERVER** (`serverconfig` du monde, `defaultconfigs`) : rien n'est déplacé ni renommé. Seul leur envoi aux
  joueurs est filtré par le noyau ; le serveur les lit et les recharge comme avant.
- **Réglages COMMON** (`config/heralisz_clans-common.toml`, `heralisz_economie-common.toml`, `heraliz_armory-common.toml`,
  `heralisz_bunker-common.toml`, `heraliz_extraction-common.toml`, `heralisz_tactical-common.toml`) : toujours des
  réglages COMMON, avec exactement le même nom de fichier (écrit en toutes lettres dans le code), déclarés par la partie
  serveur au lieu du code commun : Forge relit le même fichier `config/<nom>.toml` du Ptero, avec ses valeurs. La mise en
  ligne continue de copier `serveur/config` comme avant.
- **Effacés du Ptero à la prochaine mise en ligne** (listés dans `serveur/config_retires.txt`) : les 19 réglages
  « joueur » de `/config` (jamais lus par le serveur), et les 5 réglages des boss de `/defaultconfigs` et du
  `serverconfig` du monde (boss mis de côté ; leurs valeurs restent dans `modules/events/defaultconfigs/`).
- Chez les joueurs : les anciens fichiers COMMON (avec les valeurs par défaut) restent dans leur dossier `config/`, sans
  effet ; plus aucun n'est créé.

## À vérifier en jeu

Serveur :
- [ ] Le serveur démarre. Journal : « [noyau] réglages du serveur : N fichier(s) envoyé(s) aux joueurs à la connexion,
      9 gardé(s) au serveur ». S'il dit « filtre de l'envoi pas en place », me donner la ligne (tout part alors comme
      avant, rien ne casse).
- [ ] `/config` du Ptero : `heralisz_clans-common.toml` & co. ont gardé leurs valeurs (prix de création 1 750, rayon de
      capture 10…), et ne sont pas réécrits avec d'autres valeurs au démarrage.
- [ ] Les 19 fichiers « joueur » et les 5 fichiers des boss ont disparu du Ptero après la mise en ligne.
- [ ] Mise en ligne : la case « Monde des bases » n'existe plus dans « Run workflow » ; « redemarrer » marche toujours.

Joueur (dossier `config/` vidé avant, pour bien voir) :
- [ ] Connexion normale. Dans `config/` du jeu : plus de `heralisz_clans-common.toml`, `heralisz_economie-common.toml`,
      `heraliz_armory-common.toml`, `heralisz_bunker-common.toml`, `heraliz_extraction-common.toml`,
      `heralisz_tactical-common.toml`.
- [ ] Écran du clan sans clan, et écran de fondation : prix 1 750 hérals. Changer `cout_creation` dans
      `serveur/config/heralisz_clans-common.toml` du Ptero (ex. 2 000) : l'écran d'un joueur connecté affiche 2 000 en
      quelques secondes (Forge relit le fichier), et un joueur qui se connecte aussi. Remettre 1 750.
- [ ] Cartes d'accès : infobulle « N passages restants » et barre d'usure justes (verte 10, bleue 8, rouge 6) ; passer
      une carte au lecteur, la porte s'ouvre, la carte s'use. Changer `usesGreen` sur le Ptero : l'infobulle suit.
- [ ] Tenue de plongée : infobulle (bouteille 250 s, palmes +80 %), nage plus rapide, carte « Oxygène » sous l'eau puis
      « recharge en N s » hors de l'eau (5 s pour une bouteille vide). Combinaison : les slots Curios de vêtements
      refusent un objet quand elle est portée, comme avant.
- [ ] Protections : dégâts reçus avec une armure inchangés (plafond 60 %).
- [ ] Armes de mêlée : coups, usure (pas pour les armes à vie) et grenades qui cassent les blocs : comme avant. Menu Mods →
      heralisz_tactical → Config : seulement « flash noir » et « secousses des explosions ».
- [ ] Extraction (terminal et zones fixes) : comme avant.
- [ ] Hérals : solde, « /herals payer », montants affichés avec « hérals » : comme avant.
- [ ] Casino, caisse : les encaissements (grades à la journée, émote au hasard) sont listés et marchent. Tablette du
      Directeur, page Caisse : changer un prix et enregistrer, puis acheter un encaissement : il marche toujours (la
      commande est reprise du serveur).
- [ ] Polices : tous les écrans à la police DM Sans (clans, boutique, casino, caisses, quêtes, carte, ping, corps,
      extraction, usure, frappes, réanimation, voix, emotes, armurerie, interface, bases) s'affichent comme avant, en
      français et en russe (lettres cyrilliques).
- [ ] Armes : sons de tir, de rechargement et d'inspection des armes des packs Nitrogen, MaxStuff, Fuse et de base (dont
      AW50, MG3, Negev, Glock 18C, AK-47, MP5, 686, 9A91, AKS-74U) : un son à chaque action, comme avant.
- [ ] Une partie sans OptiFine : meubles, zombies et joueurs s'affichent comme avant (textures OptiFine retirées).
- [ ] Un plantage chez un joueur ou sur le serveur : le rapport (et `/diag`) donne toujours fichier:ligne.

## Ce qui n'est pas fait, ou pour un autre chantier

- Doublons des gunpacks qui ne sont pas des sons (modèles, animations .gltf, textures à un autre identifiant : ≈ 3 Mo) :
  leurs références ont plusieurs formes (extensions, chemins implicites) ; à ne faire qu'avec un essai en jeu. 4 sons en
  double gardés aussi (identifiant écrit ailleurs que comme valeur d'un JSON).
- Caisses vidées : pas touchées (choix du propriétaire). « Ressources mortes » de l'audit : le reste (outils de tournage,
  bot de la réanimation, console de CRac, moteur du ClashWar, `PackXZExtract.jar`) était déjà fait par les autres
  chantiers.
- Jar des meubles (cfm, version modifiée ArkaniaZ) : seul le dossier OptiFine est retiré. Il contient aussi des sons de
  zombies qui remplacent ceux de Minecraft et des modèles d'anciens menus de clans d'ArkaniaZ, non jugés. Son
  `mods.toml` porte « authors = "HeraliZ" » sur le code de MrCrayfish (déjà comme ça avant ce chantier, non touché).
- Actions des boss dans le panel du staff (§12 de l'audit) : module boutique, chantier des commandes.
- Les anciens fichiers COMMON restent sur le PC des joueurs (le launcher ne gère pas le dossier `config/`).
- `outils/brouiller.py` retiré : brouiller (renommer) n'est pas voulu ; seuls les noms des variables partent.

## Questions pour le propriétaire

1. Les réglages « joueur » (viseur de TaCZ, volume de MAtmos…) : on les a supprimés du serveur (ils ne servaient à
   personne). Veux-tu imposer certains réglages aux joueurs ? Il faudrait alors changer la valeur par défaut dans le mod
   (pour nos mods), pas donner le fichier (le launcher l'écraserait à chaque partie).
2. Le jar des meubles dit « HeraliZ » comme auteur du mod de MrCrayfish : on remet ses crédits d'origine dans la copie
   livrée ?
