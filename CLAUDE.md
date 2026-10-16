# HeraliZ — dépôt du serveur

Ce dépôt contient **tout le serveur Minecraft HeraliZ** : les sources de nos mods, les mods des autres auteurs, les
plugins, les configs, les gunpacks TaCZ et les outils. Deux personnes y travaillent, chacune avec sa session Claude.
Ce fichier est lu automatiquement par Claude au début de chaque session : il contient les règles, la méthode et tout
ce qu'il faut savoir du projet. **Le lire en entier avant toute modification.**

Les propriétaires ne sont pas développeurs : leur répondre en français simple, sans jargon, avec des questions courtes
quand un choix leur revient.

---

## 0. Règles d'or

1. **On modifie toujours les sources** (`mods/<projet>/src/...`), puis on refabrique les jars. Jamais de retouche à la
   main dans un jar, jamais un jar « bricolé » déposé sur le serveur.
2. **Avant toute demande : récupérer la dernière version** (`git pull --rebase`). **Après : fabriquer, vérifier,
   enregistrer, envoyer** (section 7). L'autre personne doit retrouver le travail à jour à sa prochaine demande.
3. **Un changement = un commit**, avec un message clair en français (« Boss : Ymirak frappe 20 % moins fort »).
   Jamais de `git push --force`, jamais de réécriture de l'historique.
4. **Aucun prénom, ancien pseudo, adresse mail, chemin de PC, nom de compte ou identifiant de session** dans le code,
   les fichiers, les commentaires ou les messages de commit. L'auteur de nos mods est toujours **HeraliZ**.
   `python outils/verifier.py` le contrôle (liste locale `outils/noms_interdits.txt`, hors Git, à demander au
   propriétaire).
5. **Jamais notre nom sur le code des autres** (liste section 3.2). On ne retire pas non plus leurs crédits ni leurs
   licences. Pour qu'un de ces mods devienne « à nous », on le refait nous-mêmes.
6. **Ne pas toucher** aux jars de `serveur/mods-tiers/` et `client/mods-tiers/` sans demande explicite.
7. **Aucun mot de passe, clé, jeton ou adresse de serveur dans le dépôt.** Les accès au Ptero sont dans les
   « Secrets » GitHub (section 8), jamais dans un fichier. Seule exception : l'adresse publique de jeu
   `play.heraliz.fr` (celle que tapent les joueurs, dans le launcher) ; jamais l'IP ni la machine du Ptero ou du site.
8. **Pas de test en jeu par Claude** : les propriétaires testent eux-mêmes. Chaque compte rendu dit clairement ce qui
   n'a PAS été testé en jeu.
9. **Contenu visuel nouveau** (modèle, arme, machine, interface importante) : d'abord des rendus à valider par le
   propriétaire, ensuite seulement le code et les jars.
10. **La carte (map) se construit en ce moment directement sur le serveur** (Ptero, monde `dayz_map`), par les
    propriétaires en jeu (détails, améliorations). **On ne touche JAMAIS aux fichiers du monde** : ni ici, ni sur le
    Ptero (pas de case « deposer_carte », pas de copie, pas de retouche). Quand la construction sera finie, le monde du
    Ptero sera copié dans GitHub : ce sera la carte « finale » de référence (section 9). Un mod ou un pack de textures
    fourni pour la carte (jar + sources) s'ajoute ici comme d'habitude (section 9). Exception (demande du propriétaire
    du 15/10) : les conteneurs de loot HeraliZ Loot T1 à T6 déjà posés peuvent être remplacés par d'autres modèles du
    même palier (et, avec `--ajouts` seulement, un modèle sans aucune place posé sur une case libre), jamais par
    fichier : seulement par les lots de commandes conditionnelles du plan des props (section 8, « Props de la carte »),
    joués par le serveur, après validation du propriétaire, et annulables. Rien d'autre (ni meubles cfm, ni T7).

---

## 1. Le serveur en bref

- Minecraft **1.20.1**, Forge **47** ; sur le Ptero : **Mohist 1.20.1-916** (`mohist-1.20.1-916-server.jar`), Java 17.
- Joueurs en version crackée : `online-mode=false` + plugin **AuthMe** (comptes par mot de passe : `/register` à la
  première connexion puis `/login` ; BCRYPT, 8 caractères minimum, IP bloquée 30 min après 6 erreurs). À venir : le
  compte HeraliZ du site (Azuriom) dans le launcher, avec code par mail sur un nouveau PC ; AuthMe reste la 3e barrière.
- Plugins (triés le 08/10, seulement l'utile ; tout le reste a été retiré) : AuthMe, spark, LuckPerms (grades du staff,
  base locale), KauriVPN (anti-VPN), LiteBans (mutes, avertissements, historique ; les bans pour triche passent par
  Spartan), WorldEdit + WorldGuard + WorldGuardExtraFlags (outils du staff, protection du spawn), ConsoleSpamFix.
- Sanctions (alignées le 11/10) : LiteBans pour `/kick`, `/ban` (compte seul), `/tempban`, `/banip`, `/mute`,
  `/tempmute`, `/unmute`, `/unban` (lève aussi les bans temporaires et d'IP) et `/untempban` (transmis à `/unban`) ;
  Spartan pour `/banpc` (compte + IP + PC, = `/sban`) et `/unbanpc` (= `/sunban`).
  Pas de plugin anti-duplication ni « ClearLag » (ils voient mal les mods / suppriment leurs entités).
- Armes : **TaCZ** (mod de base, jamais à notre nom) + nos gunpacks dans `serveur/tacz/`.
- Monnaie du serveur : les **hérals** (« 1 héral », « 1 500 hérals »). Identifiants internes inchangés
  (`heralisz_economie`, `EconomieApi`). Les objets du casino s'appellent « jetons ».
- Dimensions à part : `heralisz_matchs:arenes` (ClashWar, duels) et `heralisz_matchs:zombies`.
- Style de toutes les interfaces : celui des **écrans de clan** (panneaux de verre, police DM Sans, échelle 640×360,
  jamais de fond uni). Kit à copier : `python outils/copier_kit_ui.py mods/<projet>`. Exceptions : l'inventaire, la
  fouille du loot (coffres T1 à T7, caisses de ravitaillement, caisse de CRac, cadavres) et l'inventaire des sacs à
  dos gardent leur style de base façon DayZ (choix du propriétaire : le style « clan » y fait trop moderne ; au plus une petite
  amélioration dans le même style), et les écrans à thème (machines du casino). Toutes les autres interfaces des mods
  passent au style des clans.
- Menu Échap (heraliz_interface, maquette validée) : à gauche le logo et le menu (Reprendre, Informations, Discord,
  Boutique, Options, Joueurs, Se déconnecter ; ni Mods, ni Succès, ni Statistiques, ni Ouvrir au LAN : serveur multi
  seulement, boutons retirés aussi du menu vanilla de secours ; tuile « Langue » à drapeaux FR / EN / RU ; dessous, sur
  toute la largeur, le réglage « Sprint en un appui » : interrupteur Activé / Désactivé, explication au survol), à
  droite la carte du joueur (hérals, joueurs en ligne, ping, extractions ouvertes, zones du clan).
- Discord du serveur : https://discord.gg/heraliz (menu Échap et launcher). Noms de domaine heraliz.fr et heraliz.com :
  le site du serveur y sera mis plus tard (la boutique pourra y pointer).
- Adresses : **play.heraliz.fr** = le serveur de jeu (launcher, `launcher/app/heraliz.json` ; enregistrement DNS `A`,
  plus un `SRV` si le port n'est pas 25565) ; **heraliz.fr** = la machine du site (à part du Ptero). Réglages DNS dans
  `launcher/docs/HERALIZ-DEPLOIEMENT.md`. La distribution du launcher doit porter la même adresse de jeu.

---

## 2. Organisation du dépôt

```
CLAUDE.md                 ce fichier
docs/METHODE_CLIENT_SERVEUR.md   la méthode détaillée de séparation client / serveur (à lire avant de découper du code)
mods/<projet>/            les sources de NOS mods (un projet Gradle par jar)
mods/_gabarit/            modèle de projet coupé, pour créer un nouveau mod (outils/fusion_nouveau.py)
serveur/                  tout ce qui va sur le Ptero, en plus de nos jars :
  mods-tiers/             mods des autres auteurs (aussi chez les joueurs)
  plugins/                les plugins gardés (section 1) et leurs réglages, sans données ni secrets
  config/                 configs des mods
  defaultconfigs/         configs « serveur » des mods (copiées par Forge dans chaque nouveau monde)
  tacz/                   gunpacks TaCZ
  heralisz_matchs/, mohist-config/, server.properties, bukkit.yml, spigot.yml, commands.yml, eula.txt, server-icon.png
  mohist.sha256           empreinte du jar Mohist (le jar lui-même est dans la Release « base-serveur »)
client/mods-tiers/        mods des autres auteurs utiles aux joueurs seulement (EntityCulling)
launcher/                 le launcher des joueurs (Electron, Node.js) : `npm install` puis `npm start` dans ce dossier ;
                          doc dans launcher/README.md et launcher/docs/
outils/                   fabrication et contrôles (Python 3.11+)
site/vm/                  installation du site (Azuriom) sur sa VM, lancée par .github/workflows/site.yml (section 8 bis)
site/theme/heraliz/       le thème du site (maquettes validées), envoyé par le même workflow (choix « theme », section 8 bis)
site/extension/heraliz/   l'extension Azuriom « HeraliZ » : création de compte et double authentification depuis le
                          launcher (section 8 bis), envoyée avec le thème
monde/HeraliZ/            ancienne copie de la carte (v3 du 11/10) ; sera remplacée par la copie finale du monde du
                          Ptero quand la construction sera finie (section 9)
.github/workflows/        fabrication et mise en ligne automatiques
```

Le dépôt contient le serveur COMPLET : les 21 mods des autres auteurs + nos 23 mods (sources, fabriqués en 21 jars
`-serveur` pour le Ptero) = les 42 jars du dossier `mods/` du serveur, et les 45 jars des joueurs.

Jamais dans Git (voir `.gitignore`) :
- `build/`, `run/`, `dist/`, `livraison/` (refaits à chaque fabrication) ;
- **Mohist** (139 Mo, GitHub refuse les fichiers de plus de 100 Mo) : il est dans la **Release « base-serveur »** du
  dépôt (onglet Releases), contrôlé par `serveur/mohist.sha256`. Pour changer de version : nouvelle Release, nouvelle
  empreinte ;
- ses `libraries/` : Mohist les télécharge seul au premier démarrage ;
- le **monde en service** sur le Ptero (changé par les joueurs). Seule la carte de départ est dans le dépôt
  (`monde/HeraliZ/`, section 9) ;
- `ops.json`, `whitelist.json`, `usercache.json`, les bases de données des plugins (données des joueurs) ;
- `outils/chemins_local.json`, `outils/noms_interdits.txt` (propres à chaque PC).

---

## 3. Les mods

### 3.1 Nos mods (sources dans `mods/`)

Chaque mod « coupé » donne **3 jars** : `-serveur` (Ptero), `-client` (joueurs), `-complet` (jeu solo).

| Projet | Jar | modIds dedans | Rôle |
|---|---|---|---|
| heraliz-boss | heraliz_boss | heraliz_boss, heralisz_mammoth, heralisz_mere, heralisz_n07, heralisz_plague, heralisz_sylvorn | les 5 boss : Ymirak (mammouth), Mère des Ruines, Sujet N-07 (+ décors du Secteur 7, boss final de The Giant), Porteur de Peste, Sylvorn. Un onglet créatif avec un panneau par boss |
| heraliz-tactique | heraliz_tactique | heraliz_tactique, heralisz_tactical, heralisz_melee, heralisz_heal, heralisz_grenade, heralisz_camo | mêlée, couteaux TaCZ frappés comme la mêlée (2.13.0), soins, grenades, camos et décorations d'armes (C4 [WIP] retiré le 16/10). Moteur repris de LR Tactical (licence GPL gardée) |
| heraliz-clansetbases | heraliz_clansetbases | heraliz_clansetbases, heralisz_economie, heralisz_clans, heralisz_bases | hérals, clans, bunkers et pillage (création de nouveaux bunkers coupée depuis la 2.5.0, réglage `[bases] creation` : bases CRac du monde des bases à la place ; bunkers existants et API inchangés) (tags et blocs de blindage retirés en 2.3.0 ; système des bases à repenser) ; territoires et alliés pour la carte du monde (`CarteApi`, 2.4.0) ; `/territoire deplacer` pour reposer une zone (2.6.0) |
| heralisz-utils | heralisz_utils | heralisz_utils, heraliz_corps, heraliz_ping, heraliz_usure, heraliz_vehicules, heraliz_carte | **carte du monde** (`heraliz_carte`, touche M : carte coupée à la bordure du monde, vue satellite ou relief refaite par `outils/carte_monde.py`, position du joueur, membres de son clan, territoires des clans, extractions ouvertes, lieux posés par `/carte lieu ...`, pings du clan, clic droit = marquer un point ; section 10), train (deux réseaux, choisi par monde : l'ancienne carte et la nouvelle, `train/reseau_v2.json` ; balles de TaCZ arrêtées par les voitures, vitres qui cassent, voir §10), ascenseur, blocs de la carte, plans, ambiance, décors animés (moulins, éoliennes neuves ou à l'abandon, décanteurs), cadavres, roue de ping, usure des armes, véhicules ; forme de collision de meubles cfm sans toucher au jar (`meubles/FormesMeubles` : `cfm:model_239` escalier, `model_240` garde-corps, `model_241` plancher) |
| heralisz-matchs | heralisz_matchs | heralisz_matchs | matchs : moteur commun, ClashWar (tournoi de clans), Duels + killcam + bombe de recherche et destruction (GeckoLib), Zombies (carte The Giant) |
| heraliz-casino | heralizcasino | heralizcasino | casino payé en hérals ; zone du casino (entrée payante) et zone VIP (grades boutique, section 10), chacune avec ses entrées et ses videurs |
| heraliz-crates | heralizcrates | heralizcrates | caisses animées HD, clés, roue de butin, éditeur de butin |
| heraliz-loot | heraliz_loot | heraliz_loot | conteneurs de loot par tiers (T1 à T7 ; 68 modèles HD de plus depuis la 0.5.0 : 12 T3, 16 T4, 20 T5, 20 T6), rotation par zones : un conteneur posé ou remplacé à la main rejoint la zone du même palier à moins de 64 blocs, sinon crée la sienne (`/heralizloot proche`, `/heralizloot zone <id> part <pourcent>`) ; zones des salles à carte (`salles_carte/<nom>`), remplies seulement quand Bunker les recharge ; palier « T1 » à « T7 » en petit badge coloré à côté du nom dans l'écran de fouille et conteneurs de Minecraft scellés en survie (0.8.0) |
| heraliz-decors | heraliz_decors | heraliz_decors | décors de la nouvelle carte : grillage militaire qui se raccorde dans 8 directions (`grillage`, `grillage_haut`), blocs du désert et de l'arctique, taches de sang, lampes et guirlandes, grande cascade, eau animée ; brouillard dynamique (`/brume`) et zones dangereuses gaz / radiation / froid (`/zones`), lus dans le monde (`data/heraliz_brume.json`, `data/heraliz_zones.json`). Seulement les blocs posés sur la carte (1.9.0 : 48 blocs jamais posés retirés, d'après le workflow « Inventaire de la carte » ; gardés aussi `tole_toit_bleue` et `tole_toit_rouille`, dont les escaliers et dalles sont posés). Textures et modèles refaits par `python mods/heraliz-decors/outils/gen_assets.py` |
| heraliz-armory | heraliz_armory | heraliz_armory | armures, ghillies, brassards, masques à gaz, combinaisons ; une seule protection par pièce et six raretés (0.15.0, section 10 « équilibrage ») |
| heraliz-composants | heraliz_composants | heraliz_composants | 47 composants de craft (armes, soins, viseurs, munitions, protection, divers) et objets de récupération à revendre, modèles 3D et icônes ; rareté = couleur du nom ; tags `heraliz_composants:<catégorie>` et `tous`. Aucune recette ni loot (refonte à venir). Modèles, icônes, traductions, tags et `ComponentDefs.java` refaits par `python mods/heraliz-composants/outils/build.py mods/heraliz-composants` (catalogue : `outils/composants.py`) |
| heraliz-wonder | heraliz_wonder | heraliz_wonder | Wonder Weapons façon CoD (zombies) |
| heralisz-backpack | heralisz_backpack | backpacked | sacs à dos (Backpacked de MrCrayfish, modifié, licence LGPL gardée) |
| heralisz-bunker | heralisz_bunker | heralisz_bunker | portes animées (reprises de HBM Modernized, GPL), cartes d'accès, barricades ; salles à carte (`/salle`, baguette du staff) : porte tenue ouverte tant que quelqu'un est dedans, loot de la salle (avec HeraliZ Loot) |
| reanimation | reanimation | reanimation | joueurs à terre, réanimation, fouille (Curios compris), exécutions |
| airstrike | airstrike | airstrike | jumelles et frappes aériennes |
| caracrea | caracrea | caracrea | créateur de personnage + chat vocal de proximité (UDP) |
| heraliz-emote | heraliz_emote | heraliz_emote | emotes (Emotecraft et Player Animator de KosmX embarqués, licences dans le jar) |
| crac | crac | crac | construction façon Rust (fondations, murs, Claim Block/armoire à outils, raids) ; **monde des bases** `crac:bases` (1.5.0, terrain refait en 1.6.0, section 9) : homes (1.6.0 : `/sethome`, `/home`, `/delhome`, `/homes` ; 1 home + 1 par grade de la boutique), `/zone base` (alias `/rtp` : du spawn vers un point au hasard du monde des bases, toutes les 5 min), `/spawn` (de sa base vers le point posé par `/spawn definir`, sinon le point de réveil), tutoriel après la cinématique (kit de cabane 2 × 1, contour vert « nouveau joueur », `/tuto lancer`), base protégée 4 jours, carrières (`/carriere`) |
| heraliz-interface | heraliz_interface | heraliz_interface | **joueurs seulement** : écran titre HeraliZ (Jouer = connexion au serveur, ni solo ni liste de serveurs), menu Échap, choix de langue (drapeaux FR / EN / RU), 1re personne forcée en survie, contours des blocs cachés en survie, pack de textures du serveur imposé, touches par défaut du serveur (sans doublon, touches inutiles cachées) |
| heraliz-extraction | heraliz_extraction | heraliz_extraction | extraction : terminal (PC + fumigène, zone de 5 min, 1:30 à tenir à ciel ouvert, retour au spawn), zones fixes de la carte ouvertes à tour de rôle (par vagues depuis la 1.5.0 : 4 à la fois pendant 1 h, puis toutes changent ; tirées au hasard à 800 blocs au moins les unes des autres et une par quart de la carte, règles relâchées si impossible et notées dans le journal ; 30 s à tenir ; zone ajoutée en jeu par `/extraction create <nom>` à l'endroit du staff, ou `/execute positioned X Y Z run extraction create <nom>` ; `/extractions` pour les joueurs) |
| dayzhotbar | dayzhotbar | dayzhotbar | **joueurs seulement** : barre d'objets façon DayZ |
| heraliz-grades | heraliz_grades | heraliz_grades | **grades HeraliZ** (100 % maison) : Fondateur, Modérateur, Helper (staff), Roi de la Zone, Traqueur, Rescapé (boutique), Survivant (tout le monde) ; nom affiché « [CLAN] [Grade] Pseudo » (tag de clan lu par réflexion dans les clans) ; chat « Pseudo » message » sans < > (clé `chat.type.text` des langues du mod) ; **nouveau tab** (`TabHeraliz`, maquette validée : verre, staff puis survivants sur deux colonnes, grade, clan, voix CaraCrea, ping ; grades et clans envoyés par le serveur, paquet `InfosTab`). `grade donner|retirer <joueur> <grade>` et `kit reset <joueur>` : **console du serveur seulement** (ni joueur, ni op, ni bloc de commande, ni RCON) ; `/grade voir` (Helper+), `/grade liste` (Modérateur+), droits seulement après le `/login` AuthMe (lu par réflexion ; AuthMe injoignable = droits refusés). Menu `/kit` : un seul kit par recharge, celui du grade boutique sinon Survivant, pas d'arme ni d'armure en plus (pas de pay2win) ; kit Soutien en plus (recharge à part) pour ceux qui portent le tag HeraliZ sur Discord, compte lié (HeraliBot répond à `/mc/soutien/<uuid>`) ; contenu dans `serveur/config/heraliz_grades/kits.json`, relu tout seul. Données hors du monde : `heraliz_grades/grades.json`, `recharges.json`, `journal.log` (fichier illisible jamais écrasé). `/lier` (code de 6 caractères, 5 min, à taper sur Discord dans #lien) et `/delier` : lien avec HeraliBot, requêtes signées HMAC (secrets `HERALIBOT_LIEN_*`, section 8) ; grades donnés ou retirés à la console annoncés au staff sur Discord. Lien boutique : à venir |
| spartan | spartan | spartan | **anticheat** : détections serveur, anti-ESP, défis d'intégrité, contrôle du jeu des joueurs (jars, Mixins, injection), bans compte + IP + PC, alertes Discord. Voir section 6 bis et `mods/spartan/README.md` |

Les modIds ne changent JAMAIS (mondes, configs, gunpacks et autres mods en dépendent). Même règle pour les noms de
classes et les signatures des API (section 5).

### 3.2 Mods des autres auteurs (`serveur/mods-tiers/`, `client/mods-tiers/`)

Jamais notre nom dessus, jamais de retouche sans demande. Les sources ne sont pas dans le dépôt.

TaCZ (Serene Wave Studio) et TaCZ Addon (Mafuyu404) ; cfm « PATCH » (Furniture Mod de MrCrayfish, avec nos props
dedans) ; Framework (MrCrayfish) ; geckolib222 (GeckoLib de l'équipe Gecko, avec du contenu ArkaniaZ dedans : c'est
le SEUL GeckoLib du serveur) ; ywzj_vehicle « fuse » (YWZJ, moteur des véhicules) ; Simple Farming « heralisz1 »
(enemeez) ; Mutants & Zombies (Pete MC Mods) ; Zombie Awareness et CoroUtil (Corosus) ; MAtmos et TCTcore (The_TCT) ;
Passable Foliage et Kiwi (Snownee) ; Curios (TheIllusiveC4) ; Cloth Config (shedaniel) ; Player Animation Lib (KosmX) ;
ModernFix, FerriteCore, MemoryLeakFix, PacketFixer (optimisation) ; EntityCulling (joueurs seulement).

Les versions modifiées (cfm PATCH, geckolib222, ywzj fuse, simplefarming heralisz1) ont été retouchées directement
dans les jars : il n'existe pas de sources.

---

## 4. Modifier un mod : la séparation client / serveur

Nos mods sont coupés en deux pour que le code qui « décide » ne quitte jamais le serveur : les joueurs n'ont que
l'affichage. Chaque projet a **trois dossiers de sources** :

| Dossier | Ce qui y va | Jar |
|---|---|---|
| `src/commun` | enregistrements (blocs, objets, entités, sons), configs, paquets réseau, état synchronisé que lit l'affichage (`EntityDataAccessor`, état d'une entité de bloc), API lues des deux côtés | les trois |
| `src/serveur` | ce qui décide : IA, attaques, dégâts, phases, butin, règles, commandes, sauvegardes, traitement des paquets reçus des joueurs, `data/` | `-serveur`, `-complet` |
| `src/client` | l'affichage : rendus, modèles, HUD, écrans, sons d'ambiance, musiques, effets, mixins client, `assets/` | `-client`, `-complet` |

Le compilateur l'impose : `commun` ne voit ni `client` ni `serveur`, et ces deux-là ne se voient pas.

**Où toucher :**
- changer une attaque, des dégâts, du butin, une règle → `src/serveur` (seul le jar `-serveur` change ; les joueurs
  n'ont rien à retélécharger) ;
- changer un modèle, une texture, un effet, le HUD → `src/client` (nouveau jar `-client` pour les joueurs) ;
- ajouter un objet, un bloc, une entité, un paquet ou une donnée synchronisée → `src/commun` (**les deux** jars).
- **textes (3 langues : français, anglais, russe)** : jamais de texte écrit en dur. Chaque texte est une clé de
  `assets/<modid>/lang/` présente dans `fr_fr.json` (la référence), `en_us.json` et `ru_ru.json` (glossaire des termes :
  héral → heral / герал, extraction → extraction / эвакуация, à terre → downed / нокаут…). Message envoyé par le
  serveur : `Component.translatableWithFallback(clé, texte français, args...)` (le joueur le lit dans sa langue, la
  console en français) ; dans ces clés, seulement `%s` / `%1$s` et `%%` (formater les nombres avant). Côté client :
  `Component.translatable` ou `I18n.get`. `python outils/verifier_langues.py` contrôle les trois langues (mêmes
  paramètres et codes couleur) ; les polices DM Sans n'ont pas le cyrillique : `python outils/polices_cyrillique.py`
  ajoute Onest (OFL) en secours à toute nouvelle police d'un mod. Restent en français : journaux, configs, alertes du
  staff de Spartan.
- la bombe et l'ordinateur des duels (modèles, textures HD, masques lumineux, sons de clavier et de piratage) sont
  **générés** par `python mods/heralisz-matchs/outils/bombe/modeles.py` et `sons.py` (`--sortie` pour essayer ailleurs) ;
  jamais retouchés à la main. Matchs dépend de GeckoLib depuis la 0.7.0 (geckolib222, compileOnly, jamais embarqué).
- repères des sites A et B des duels (badge) : `textures/gui/duel_repere.png` est **généré** par
  `python mods/heralisz-matchs/outils/reperes/reperes_sites.py` (`--apercu planche.png` pour une planche d'aperçu).
- protections des armures (Armurerie) : `ArmorCatalog`, `SuitCatalog`, `GearCatalog` et les raretés des objets de
  l'Armurerie dans `raretes.json` sont **écrits** par `python mods/heraliz-armory/outils/equilibrage_armures.py`
  (tableaux `TENUE`, `ENSEMBLES`, `COMBINAISONS` en tête du script) ; jamais à la main.
- dégâts des armes TaCZ contre les joueurs et raretés des armes, accessoires et munitions :
  `serveur/config/heraliz_tactique/armes.json` et les sections `armes`, `accessoires`, `munitions` de
  `serveur/config/heraliz_loot/raretes.json` sont **écrits** par `python outils/equilibrage_armes.py` (tableaux
  `FAMILLES`, `RARETE_ARME`, `BONUS_RARETE` en tête du script) ; à relancer après l'ajout d'un gunpack. Les gunpacks ne
  sont jamais modifiés pour ça.
- les camos des armes (tactique) : les 62 shaders `camo_<id>.fsh` sont **générés** depuis
  `mods/heraliz-tactique/outils/camo_gabarit.fsh` par `python mods/heraliz-tactique/outils/generer_camos.py` ; on modifie
  le gabarit, jamais les fichiers générés.
- la portée du son des silencieux TaCZ (gunpacks de `serveur/tacz/`) : tableau `PORTEES` de `outils/silencieux.py`, puis
  `python outils/silencieux.py` (jamais à la main dans les fichiers des silencieux ; à relancer si la portée d'un tir
  normal change dans `serveur/config/tacz-common.toml`). Un tir silencieux part aussi deux fois moins fort pour les autres
  joueurs (mixin commun `TaczSonSilencieuxMixin` du mod tactique). Choix du propriétaire (12/10) : net et à sa vraie
  place jusqu'à la portée nette, la même pour tous les silencieux (16 blocs, `PORTEE_NETTE` ; TaCZ lit le double et
  l'éteint jusque-là) ;
  au-delà, jusqu'à la portée d'un tir normal, le même son plus grave et plus faible, d'un point flou autour du tireur
  (même point 30 s pour un même joueur), décidé par le serveur (`fr.heraliz.tactique.son.SonLointain`, canal
  `heraliz_tactique:son`, section `[silencieux]` de `heralisz_tactical-server.toml`) : le jeu du joueur ne reçoit jamais
  la vraie position d'un tireur lointain.
- les accessoires TaCZ sont **à plat** (choix du propriétaire) : tout viseur, bouche, crosse, poignée ou laser va sur
  toute arme qui a cet emplacement (ouvert dans `allow_attachment_types` de sa donnée ET os `<type>_pos` dans son
  modèle, sinon l'accessoire serait invisible) ; il y a juste des accessoires meilleurs que d'autres. Gunpack
  `serveur/tacz/heraliz_accessoires` (listes communes `#heraliz_accessoires:viseurs|bouches|crosses|poignees|lasers`,
  ajoutées aux listes des armes ; crosses OEM sur les armes dont le modèle a leur nœud) **refait par
  `python outils/accessoires.py`**, jamais à la main ; les autres gunpacks ne sont pas touchés. Gardent leur liste
  d'origine : chargeurs et munitions spéciales, accessoires faits pour une seule arme (tableau `PROPRES` : lunettes
  intégrées de l'AUG et du P90, montages latéraux d'AK, baïonnettes, canon du Deagle doré…), Wonder Weapons. Un
  emplacement fermé reste fermé : pour l'ouvrir, ajouter le type à `allow_attachment_types` de l'arme puis relancer
  l'outil (`--rapport` liste les os sans emplacement et les emplacements sans os). Un accessoire qui flotte à côté
  d'une arme ou la traverse en est retiré par le tableau `EXCLUS` de l'outil (l'arme reçoit alors la liste commune
  écrite sans lui).
- placement des accessoires TaCZ (12/10) : `python outils/verif_accessoires.py` pose par le calcul chaque accessoire
  sur chaque arme qui l'accepte, exactement comme TaCZ 1.1.8 le dessine (os `<type>_pos`, pièces cachées, adaptateurs),
  et mesure s'il flotte (écart de plus de 0,35 px), s'il traverse l'arme ou s'il est invisible (`verifier.py` le
  lance ; `--rapport` détaille, `--images` fait des planches de contrôle vues de côté et de face). Un défaut se
  corrige dans le modèle de l'arme (os `<type>_pos` déplacé, tube de crosse `ar_stock_adapter` ajouté), sinon
  l'accessoire est retiré de l'arme (`EXCLUS`), sinon il est accepté avec sa raison (`ACCEPTES` de l'outil :
  baïonnettes, silencieux à manchon, lunette d'époque du Springfield). Aucun essai en jeu : à confirmer à l'œil.
- les icônes d'inventaire des armes TaCZ sont des photos 3D faites chez le joueur (`camo/client/IconesArmes` du mod
  tactique : modèle + skin, sans accessoires, 64 au plus en mémoire graphique, rien sur le disque ni le serveur) ; l'image
  `slot` du gunpack ne s'affiche qu'en attendant la photo ou si elle est impossible. Armes de profil, canon à droite ;
  couteaux modélisés couchés (lame le long de X, très minces le long de Z : « Knife » CT et T, « M9 Bayonet » et ses
  skins) photographiés à plat, pointe en haut à gauche comme leurs icônes dessinées (2.12.0 ; avant, on les voyait par
  le bout). Seule l'icône change, pas l'arme en main.
- l'écran de modification des armes TaCZ est remplacé chez le joueur par l'**Atelier d'arme** (`camo/client/atelier` du
  mod tactique, sous-classe de `GunRefitScreen` pour que TaCZ le reconnaisse) : onglets Accessoires, Camo, Personnaliser.
  Poses et retraits par les paquets de TaCZ, camos et stickers par les nôtres. S'il plante, l'écran de TaCZ (avec les
  anciens onglets Skin et Personnaliser) revient jusqu'au redémarrage du jeu. Les accessoires rangés dans un coffre
  proche (fonction de TaCZ Addon) n'y sont pas proposés : seulement ceux de l'inventaire.
- la table de fabrication TaCZ est remplacée de même par la **Table d'armurier** (`camo/client/atelier/TableArmurier`) :
  elle pilote l'écran de TaCZ au lieu de le refaire (recettes classées et matériaux comptés par TaCZ, son bouton de
  fabrication caché pressé par le nôtre, accès par `mixin/GunSmithTableScreenAccessor`) : TaCZ Addon garde les matériaux
  des coffres proches et Maj + clic pour fabriquer en série.
- le camo d'une arme est l'étiquette NBT `HeraliszCamo` ; un camo à vie porte en plus `HeraliszCamoProprio` et
  `HeraliszCamoJeton` (`fr.heralisz.camo.CamoData`). Un mod qui pose lui-même un camo sur une arme qui peut porter un
  camo à vie doit retirer ces deux clés, sinon le serveur la prend pour un camo à vie périmé et lui enlève son camo
  (pas le cas aujourd'hui : le Pack-a-Punch et Wonder ne touchent qu'aux armes prêtées des matchs, où les camos à vie
  sont refusés).

**Les briques à connaître** (paquet `pont` de chaque projet, détails dans `docs/METHODE_CLIENT_SERVEUR.md`) :
- `Parties.serveur(...)` / `Parties.client(...)` à la fin du constructeur `@Mod` : chargent `serveur.PartieServeur` /
  `client.PartieClient` si elles sont dans le jar ;
- une classe qui a de la logique est coupée en `X` (commun) + `XServeur` (serveur, même paquet). On la crée par son
  type (`TYPE.get().create(level)`) ou son usine (`UsineEntite`, `UsineBlocEntite`, `Usine`), **jamais par `new X(...)`**
  (on obtiendrait la version sans logique) ;
- `Crochet` : point d'accroche que l'autre côté branche (paquets reçus, rendus d'objets, tickers client) ;
- façades communes branchées par le serveur : `pont/Services` (Matchs), `pont/Serveur` (CRac) ; sans partie serveur,
  elles rendent un résultat neutre.

**Les pièges les plus courants** (liste complète dans la doc, §9, §15, §16) :
1. `@Mod.EventBusSubscriber` sans `modid` dans un jar à plusieurs mods : écouteur appelé plusieurs fois.
2. `new X(...)` d'une classe qui a une version `XServeur`.
3. `EntityDataAccessor` déclaré ailleurs que dans la classe commune : désynchronisation.
4. `DistExecutor` ou `Minecraft.getInstance()` dans `src/commun` : interdit, passer par un `Crochet`.
5. Un fichier de `assets/` lu par le serveur (`getResourceAsStream`) : le mettre dans `src/commun/resources`.
6. Une constante d'une classe serveur lue par le client : la déclarer dans une classe commune.
7. Ordre des groupes d'un onglet créatif : les déclarer dans le mod porteur (FML construit les mods en parallèle).
8. GeckoLib : `compileOnly` contre `libs/geckolib222-...jar`, **jamais embarqué** (sinon tout le contenu ArkaniaZ
   disparaît).
9. `PlaySoundEvent.getSound()` peut être **null** : un autre mod a pu annuler le son avant (dayzhotbar le fait en
   priorité haute pour les bruits lointains de MAtmos, Emote pour la musique coupée). Toujours le vérifier : c'était la
   cause du plantage du jeu à l'explosion d'une flash (assourdissement de Tactique, corrigé en 2.12.0).

---

## 5. Relier les mods entre eux

Nos mods se parlent par des API stables. **Ne jamais renommer ces classes ni changer leurs signatures** : d'autres mods
les appellent, parfois par leur nom (réflexion, `Class.forName("...")`), ce que le compilateur ne voit pas.

| API | Où (côté) | Pour quoi |
|---|---|---|
| `fr.heralisz.economie.api.EconomieApi` | clansetbases (commun) | soldes en hérals, paiements |
| `fr.heralisz.clans.api.ClansApi`, `ClanInfo`, événements `ClanDissousEvent` / `MembreQuitteEvent` | clansetbases (commun) | clan d'un joueur, membres, droits |
| `fr.heralisz.bases.api.BasesApi`, `PillageHooks` | clansetbases (serveur) | bunkers, pillage |
| `fr.heralisz.matchs.moteur.api.MatchApi` | matchs (commun) | `partieDe(ServerPlayer)`, `partie(id)`, en match ou pas |
| `fr.heralisz.matchs.moteur.api.Partie`, `Equipe`, `CombatTag` (`restantMs(UUID)`), `Coffre`, `Arenes`, `Kit` | matchs (commun) | parties, équipes, tag de combat, coffre de match, arènes |
| `fr.heralisz.matchs.zombies.jeu.api.ZombiesApi` | matchs (commun) | `TAG_PARTIE`, `partieDe(Entity)`, `planches(...)`, `interagir(...)`, Pack-a-Punch, sons |
| `fr.heralisz.matchs.zombies.jeu.api.HudZombies.actif()` | matchs (commun, lu par le client) | HUD zombies affiché |
| `fr.heralisz.matchs.moteur.client.MoteurClient.enMatch()`, `combatRestantMs()` | matchs (client) | états du joueur côté client |
| `fr.heralisz.n07.api.N07Api` | boss (serveur) | boss final de The Giant (utilisé par Matchs) |
| `fr.heraliz.reanimation.core.Downed.isDowned(Entity)`, `DownedManager` | reanimation (commun) | joueur à terre |
| `fr.heraliz.reanimation.execution.Executions` | reanimation (serveur) | exécutions |
| `com.caracrea.api.voice.CaraCreaVoice` | caracrea (serveur) | voix : couper un joueur, portée, routage |
| `fr.heralisz.tactical.api.HeraliszTacticalAPI` | tactique (commun) | index des objets tactiques |
| `com.heraliz.extraction.api.ExtractionApi` | extraction (commun, rempli chez le joueur) | extractions ouvertes (`ouvertes()`, `version()`) pour la carte du monde |
| `fr.heralisz.clans.api.CarteApi` (paquets `CarteDemandeC2S` / `CarteS2C`) | clansetbases (commun, rempli chez le joueur) | pour la carte du monde (lue par réflexion par utils) : `demander()` (au plus 1 par seconde), `zones()` (état public de chaque zone, celui du drapeau), `allies()` (membres de son clan dans le même monde, personne en match), `monClan()`, `version()`, `dessinerBanniere(...)`, `ravitailler(id)` |
| `fr.heraliz.loot.SallesLoot` (`declarer`, `retirer`, `recharger`, `etat`) | loot (serveur, appelée par réflexion par Bunker) | salles à carte : leurs conteneurs quittent la rotation et ne se remplissent qu'à la recharge |
| `fr.heraliz.loot.client.RaretesClient.couleur(ItemStack)` | loot (client, lu par réflexion par dayzhotbar) | couleur de rareté d'un objet (0 : aucune) |
| `fr.heraliz.grades.serveur.GradesApi` (`rangBoutique(UUID)` : 0 à 3, `gradeAffiche(UUID)`) | grades (serveur, lue par réflexion par le casino) | grade d'un joueur (zone VIP du casino) |
| données persistantes du joueur `PlayerPersisted.HeralizBonusLoot.<source>` | lues par loot (serveur), écrites par clansetbases (`clan`) et utils (`objet`) | bonus de loot : `{Pct, Fin (heure en ms)}` ou `{Pct, Restant (ticks de jeu)}`, sources additionnées, plafond 25 % (`fr.heraliz.loot.BonusLoot`) |

**Pour qu'un nouveau mod utilise un des nôtres :**
- dépendance obligatoire : la déclarer dans son `mods.toml` et compiler contre une copie du jar dans son `libs/`
  (`compileOnly fg.deobf("blank:<nom>:<version>-<suffixe du projet>")`, suffixe unique par projet) ;
- dépendance facultative : appel par réflexion, en tolérant l'absence du mod (exemples : `Busy` de CaraCrea,
  `Regles` de Wonder) ;
- appeler une API **du bon côté** : une API rangée dans `src/serveur` n'existe pas dans le jar des joueurs.
Après un changement d'API : `python outils/verif_dependants.py` (doc dans le fichier) sur les jars des mods qui
l'appellent.

---

## 6. Fabriquer, vérifier, livrer

**Une fois par PC :**
- JDK 17, Python 3.11 ou plus, Git ;
- un profil CurseForge Forge 1.20.1 lancé au moins une fois (les contrôles lisent Minecraft dans le dossier `Install`) ;
- une copie du dossier `libraries/` du serveur (par SFTP depuis le Ptero) dans un dossier local ;
- `outils/chemins_local.json` (modèle dans `outils/chemins.py`) et `outils/noms_interdits.txt` (demander la liste au
  propriétaire). Ces deux fichiers ne vont jamais dans Git.

**À chaque modification :**
```
python outils/construire.py heraliz-boss      fabrique ce mod (sans nom : tous les mods) et range les jars dans livraison/
python outils/verifier.py heraliz-boss        tous les contrôles (0 problème exigé avant d'envoyer)
```
Les contrôles :
- `verif_split` : séparation client / serveur correcte (rien de client dans `-serveur`, rien qui manque d'un côté) ;
- `check_geckolib_api` : compatible geckolib222 4.4.7, aucun GeckoLib embarqué ;
- `verif_reobf` : noms de Minecraft réobfusqués (sinon plantage au chargement) ; chaque nom est cherché dans
  Minecraft + Forge du lanceur CurseForge ;
- noms perso : aucun mot interdit dans les sources ni les jars ;
- silencieux : chaque silencieux des gunpacks a sa portée de son (`outils/silencieux.py`).
- accessoires : listes communes d'accessoires TaCZ à jour (`outils/accessoires.py`).
- verif_accessoires : chaque accessoire TaCZ bien placé sur chaque arme qui l'accepte (`outils/verif_accessoires.py` ;
  numpy et Pillow, sauté sans eux).
- langues : textes français, anglais et russe avec les mêmes paramètres et codes couleur (`outils/verifier_langues.py`).

**Où va quoi** (dossier `livraison/`, recréé à chaque fois) :
- `mods serveur/` → `mods/` du Ptero ;
- `mods client/` → `mods/` des joueurs ;
- `mods solo/` → jeu solo et tests ;
- `plugins/` → `plugins/` du Ptero.
Ne jamais laisser deux versions du même mod dans un dossier `mods/`.

---

## 6 bis. L'anticheat Spartan

- **Deux jars de la MÊME fabrication** : `-serveur` sur le Ptero, `-client` chez les joueurs. Le jar serveur contient une
  copie du jar joueur (`META-INF/spartan/client.bin`) et calcule dessus les réponses attendues aux défis : un joueur
  avec un autre jar joueur est signalé. Après toute modification de Spartan, redonner le nouveau jar joueur.
- Le jar joueur ne contient AUCUNE détection serveur, aucun seuil, aucune signature : ne jamais déplacer de code de
  détection vers `src/commun` ou `src/client`.
- **Modpack officiel automatique** : `outils/construire.py` écrit `livraison/spartan/officiel.json` (empreinte de chaque
  jar des joueurs, configs Mixin connues, empreinte du dossier) ; la mise en ligne le dépose dans
  `config/spartan/officiel.json`. Tout jar ou toute config Mixin en plus chez un joueur est signalé. Rien à apprendre
  à la main ; `/spartan officiel` relit le fichier sans redémarrer.
- **Détection silencieuse** (choix du propriétaire) : ce qui vient du jeu du joueur n'expulse jamais, le staff reçoit
  l'alerte sur Discord avec le rapport et décide (`/sban`). `alertOnly = true` les premières semaines : régler les
  seuils sur les vraies alertes avant toute sanction automatique. Attention : les corrections immédiates
  (section `[mitigation]`) ne dépendent pas d'`alertOnly` ; le renvoi en arrière des détections de mouvement
  (`movementSetback`) est coupé depuis le 11/10, après des alertes à tort chez le propriétaire : le remettre quand les
  alertes de mouvement seront fiables.
- **Doublon de position du clic droit** (Spartan 2.1.1, `MovementEngine.isUseDuplicate`) : en utilisant un objet (soin,
  nourriture…), un client 1.17+ renvoie sa dernière position exacte en plus du paquet du tick. Compté comme un vrai pas,
  il déclenchait Speed, Vertical, Fly et Timer à tort (alertes « maximum 0.07 », « 0.65 bloc de trop »). Il ne sert
  plus qu'à la rotation, au plus un toutes les 100 ms.
- **Jars imbriqués** : `outils/construire.py` suit `META-INF/jarjar/metadata.json` pour trouver les jars que Forge
  charge depuis un autre jar (EntityCulling apporte TRansition et TRender dans `META-INF/jars/`) ; leurs configs Mixin
  sont dans `officiel.json`. Avant le 11/10, elles étaient signalées à tort (« config Mixin inconnue »).
- **Lever un ban** : `/sunban <joueur>` lève ses bans ET ceux qui visent un de ses PC (Spartan 2.2.1) : un ban matériel
  sans compte (importé de Spartan 1.0, alerte « Compte secondaire … PC de (ban Spartan 1.0) ») est levé ; le ban d'un autre
  compte reste, seul ce PC en est retiré. Avant, un joueur pris par un ban de la 1.0 ne pouvait pas être débanni par
  son pseudo. `/sbaninfo <joueur>` montre ses bans et ses comptes liés.
- **Injection en cours de partie** (Spartan 2.3.0) : le contrôle d'intégrité passe toutes les 90 à 240 s ; ce qui
  APPARAÎT après le premier contrôle de la session est rouge : code d'origine inconnue dans les piles des threads
  (client injecté, même sans client de référence), DLL chargée hors des programmes installés et du dossier du jeu
  (hors overlays connus). Rouge aussi dès la connexion : du code aux noms de classes faits de caractères de contrôle
  (brouillé, chargé en mémoire sans fichier) et des threads sans nom qui exécutent du code inconnu : c'est ce
  qu'a laissé le client de triche Doomsday le 11/10, sans DLL ni autre trace. Un indice déjà signalé en orange qui
  passe au rouge est re-signalé. Sans client de
  référence appris (`/spartan reference learn <pseudo>` depuis un jeu PROPRE), le code inconnu présent dès la
  connexion reste orange : l'alerte Discord le rappelle. Choix du propriétaire (11/10) : même en rouge, alerte
  seulement, jamais d'expulsion ; le staff bannit avec `/sban`.
- **Enquête sur un joueur en jeu** : workflow « Enquête Spartan » (`.github/workflows/enquete.yml`, « Run workflow » +
  pseudo) : contrôle immédiat par la console du Ptero, puis affiche dans le journal de GitHub les 4 derniers rapports
  de Spartan sur ce joueur (indices, DLL, threads, code inconnu ; ni IP ni identifiant matériel). Rien n'est changé.
- Réglages : `serveur/config/spartan-common.toml` (fait foi). Webhooks et secret du launcher : JAMAIS dans le dépôt,
  marques `__SPARTAN_…__` remplacées par les secrets GitHub à l'envoi (section 8).
- Branchements HeraliZ (`mods/spartan/src/serveur/java/com/Spartan/serveur/compat/HeralizCompat.java`, par réflexion,
  sans dépendance) : alliés de l'anti-ESP = même équipe de match, même partie de zombies, sinon même clan ; voix de
  CaraCrea (un joueur caché qui parle est montré à ceux qui l'entendent). Si une de ces API change, ce fichier aussi.
  Utils envoie en plus son propre test d'alliance par message IMC (`ally_predicate`) : deux passagers d'une même rame du
  train (le train n'est pas fait de blocs, la ligne de vue « monde » coupait dans les tunnels et les gares).
- **Launcher** (`launcher/`, Helios) : il charge les mods depuis son dossier (`common/modstore`, fournisseur Forge
  « maven libs », options `--fml.mavenRoots` / `--fml.modLists`) ; Spartan hache et juge chacun de ces jars comme ceux
  du dossier mods. Le launcher ajoute `-XX:+DisableAttachMechanism`, retire les options d'injection du joueur et, depuis
  la 1.6.1, les variables d'environnement que Java lit au démarrage (`JAVA_TOOL_OPTIONS`, `_JAVA_OPTIONS`,
  `JDK_JAVA_OPTIONS` : un `-javaagent` de triche pouvait s'y glisser ; depuis la 1.8.0 aussi `LD_PRELOAD`, `LD_AUDIT`,
  `DYLD_INSERT_LIBRARIES` sur Mac et Linux) (`launcher/app/assets/js/spartan.js`). Depuis la 1.8.0, le jeu est lancé à
  part du launcher (le fermer pendant la partie ne coupe plus le jeu ; console du jeu dans `logs/console-jeu.log`) ; ses verrous
  Electron (`electronFuses` de `electron-builder.yml`) l'empêchent de tourner comme un simple Node.js ou de charger un
  autre code que son archive. Son pack est refait à chaque mise en ligne avec `livraison/mods client` de la même
  fabrication (section 8).
- **Jeton de lancement, signé par le site (mode B, depuis le launcher 1.6.0)** : on ne joue que depuis le launcher
  HeraliZ, connecté à un compte du site. À chaque partie, le launcher envoie au site l'empreinte de son dossier mods ;
  le site (`/api/heraliz/jeton-jeu`, `JetonController` de l'extension) rend un jeton valable **6 h**, signé pour le
  pseudo du compte connecté (jamais un pseudo envoyé par le launcher), passé au jeu par la variable d'environnement
  `SPARTAN_LAUNCH_TOKEN` depuis la 1.6.1 (avant : `-Dspartan.launchToken=…`, visible de tout programme dans la ligne de
  commande ; Spartan lit les deux) et vérifié par Spartan à la connexion (`LaunchToken.verify`, même format). La
  clé (`storage/app/heraliz/jeton-jeu.cle` sur la VM du site) est créée une seule fois par `theme.sh` et lue par la mise
  en ligne du serveur, qui l'écrit dans `launcherSecret` : elle n'est ni dans Git, ni dans un secret GitHub, ni dans le
  launcher (impossible de fabriquer un jeton sans le site). L'ancien secret GitHub `SPARTAN_LAUNCHER_SECRET` ne sert
  plus. Réglage `tokenRequired` (`serveur/config/spartan-common.toml`) : `false` = alerte seulement ; `true` =
  connexion REFUSÉE sans jeton valide (CurseForge, autre launcher, launcher 1.5.0 ou plus ancien, jeton expiré), même
  en détection silencieuse. **À `true` depuis le 11/10** (demande du propriétaire, serveur pas encore ouvert, avant tout
  essai en jeu) : seul le launcher HeraliZ 1.6.0 ou plus récent peut entrer ; si le site est en panne, plus personne
  n'entre. La mise en ligne refuse d'envoyer `true` si la clé n'a pas pu être lue sur le site. Vérifié (copie locale du site) : un jeton du
  site est accepté par le code Java de Spartan ; autre pseudo, autre clé ou plus de 6 h : refusé.
- **Anti-duplication** (`check/item/AntiDupe`, section `[antidupe]`) : les objets de valeur NON empilables (`tacz:*`,
  `backpacked:*`, `heraliz_armory:*` par défaut) reçoivent un numéro unique invisible (donnée `spartan_uid`) quand ils
  sont rangés dans l'inventaire ; toutes les 20 s, un relevé en une fois (inventaires, souris, coffre de l'Ender,
  Curios, contenu des sacs) : même numéro à deux endroits = copie, alerte au staff avec preuve, rien de supprimé.
  Jamais numéroté : objet en main, porté, dans un sac ; joueurs en créatif ignorés. Un mod qui COPIE un objet suivi
  (au lieu de le déplacer) doit retirer `spartan_uid` de la copie, sinon fausse alerte.
- Un mod qui déplace un joueur doit passer par une vraie téléportation (`teleportTo`, `connection.teleport`) ou un recul
  (`setDeltaMovement` + `hurtMarked`) : Spartan les suit. Un `setPos` / `moveTo` seul sur un joueur peut être pris pour
  de la triche (cas connus : projection du Draugr, remontée de la Mère des Ruines : à surveiller dans les alertes).

---

## 7. Travailler à deux avec Git

Pour chaque demande, dans cet ordre :
1. `git pull --rebase` (récupère ce que l'autre a fait) ;
2. faire la modification dans les sources ;
3. `python outils/construire.py <projets touchés>` puis `python outils/verifier.py <projets touchés>` : 0 problème ;
4. `git add` des fichiers modifiés (jamais de `build/`, `livraison/`, fichiers locaux), `git commit -m "..."` ;
5. `git push`. S'il est refusé (l'autre a envoyé entre-temps) : `git pull --rebase`, régler les conflits en gardant
   les deux changements, refabriquer, revérifier, puis `git push`.

Règles :
- identité de chaque PC, dans ce dépôt : `git config user.name "HeraliZ"` et
  `git config user.email "heraliz@users.noreply.github.com"` ;
- petits commits ; un message par changement ; dire dans le message si les joueurs doivent mettre à jour leurs mods ;
- deux demandes en même temps, c'est possible : Git assemble les fichiers différents ; si le même endroit a été
  modifié des deux côtés, Claude assemble à la main et revérifie.

---

## 8. Mise en ligne automatique (GitHub Actions)

`.github/workflows/serveur.yml`, à chaque envoi sur la branche `main` :
1. fabrique les mods dont les sources ont changé (les autres jars sont repris du cache) et range les jars ;
2. lance les contrôles possibles sans Minecraft (GeckoLib, langues, noms perso si le secret `NOMS_INTERDITS` existe) ; un
   échec arrête tout : rien n'est envoyé au Ptero ;
3. garde nos jars (paquets « mods-serveur » et « mods-client », 3 jours) à télécharger dans l'onglet Actions ; quand le
   quota de stockage des artefacts de GitHub est plein, ces copies manquent (avertissement), sans bloquer la suite ;
4. **si les secrets du Ptero sont réglés**, il met à jour le serveur par SFTP :
   - `mods/` devient la copie exacte de `livraison/mods serveur` : les anciennes versions sont retirées ;
   - plugins : les jars du Ptero qui ne sont pas dans le dépôt sont SUPPRIMÉS un par un (leurs dossiers de données
     restent), ceux du dépôt sont envoyés avec leurs réglages (jamais de base de données) ;
   - `config/`, `tacz/`, `heralisz_matchs/` et `mohist-config/` sont complétés ou mis à jour, sans rien supprimer ; `config/spartan/officiel.json` est remplacé
     (les bans et preuves de Spartan, dans `config/spartan/`, ne sont jamais touchés) ;
   - `defaultconfigs/` est copié aussi dans le `serverconfig/` du monde (nom lu dans le `server.properties` du Ptero) :
     **les configs du dépôt font foi**, un réglage changé à la main sur le serveur est écrasé au prochain envoi ;
   - le monde, les données des joueurs, `ops.json` et `server.properties` ne sont jamais touchés (les fichiers de la
     racine du serveur se mettent à jour à la main) ;
5. **redémarrage seulement sur demande** : bouton « Run workflow » dans l'onglet Actions, case « redemarrer ». Le
   résumé de chaque envoi dit aussi si les webhooks Discord de Spartan sont valides (Discord interrogé sans rien
   poster) et si la clé des jetons du launcher est réglée ; case « tester_discord » : un message de test part dans le
   salon privé des alertes ;
6. **pack du launcher** (si les secrets du site sont réglés, après la mise à jour du Ptero) : `outils/pack_launcher.py`
   met en forme, avec Nebula (le générateur de Helios, version figée dans le workflow), Forge 1.20.1-47.4.0, les jars
   joueurs de CETTE fabrication (ceux de `officiel.json`) et le dossier `tacz`, puis les dépose par rsync sur la VM du
   site : https://heraliz.fr/launcher/ (`distributionUrl` de `launcher/app/heraliz.json`). Nouveaux fichiers d'abord,
   `distribution.json` ensuite, anciens fichiers retirés en dernier ; l'étape relit le tout depuis internet (pack
   réservé aux comptes : avec des liens signés comme le site le fait, clé `pack.cle` lue sur la VM, section 8 bis) et
   le résumé de l'envoi dit ce qui est en ligne et si le pack est réservé aux comptes. Les joueurs ont les nouveaux mods à leur prochain lancement du launcher,
   le serveur à son prochain redémarrage : après un changement de mods, redémarrer le serveur (case « redemarrer »)
   pour que tout le monde soit sur la même version. Un échec ici arrive en dernier : il ne retient ni la mise à jour
   du Ptero ni le redémarrage.

**Nouvelle carte** — **à NE PAS utiliser pendant que la carte se construit sur le serveur** (section 0, règle 10) :
elle mettrait la vieille copie du dépôt à la place du monde en construction (renommé `_ancien`, pas supprimé).
« Run workflow » avec la case « deposer_carte ». Après la mise à jour habituelle, il envoie
`monde/HeraliZ/` à côté du monde en service (`<monde>_nouvelle_<date>`, serveur toujours en marche, 2 fichiers à la fois,
reprise si la liaison coupe), contrôle chaque fichier, y recopie les réglages `serverconfig/` du monde en service puis
ceux de `defaultconfigs/` ; ensuite seulement il prévient les joueurs, arrête le serveur proprement, renomme l'ancien
monde `<monde>_ancien_<date>` (jamais supprimé ; ses dossiers `_nether` / `_the_end` aussi, s'il y en a), donne son nom
à la carte et redémarre. Un échec avant la bascule ne touche ni au serveur ni à son monde (carte incomplète laissée dans
`<monde>_nouvelle_<date>`, à supprimer depuis le panel). Le nom du monde est lu dans le `server.properties` du Ptero
(aujourd'hui `dayz_map`), obligatoirement : s'il est illisible, rien n'est changé (le 11/10, un repli sur le nom du dépôt
a posé la carte dans `/HeraliZ`, que le serveur ne charge pas). Restent hors du monde, donc gardés : clans, hérals, bunkers (reposés seuls), comptes AuthMe, grades,
bans. Repartent de zéro avec la carte : inventaires et positions des joueurs, zones d'extraction posées à la main,
territoires pris. Les zones WorldGuard restent rattachées au nom du monde (mêmes coordonnées) : le résumé de l'envoi
les liste, à refaire pour la nouvelle carte. L'ancien monde se supprime depuis le panel quand tout va bien.

**Installation complète** (serveur Ptero neuf ou à remettre à neuf) : « Run workflow » avec la case
« installation_complete » (et « redemarrer »). En plus de ce qui précède, il envoie Mohist (pris dans la Release
« base-serveur », empreinte contrôlée) et les fichiers de la racine : `server.properties`, `bukkit.yml`, `spigot.yml`,
`commands.yml`, `eula.txt`, `server-icon.png`. Il ne reste alors qu'à déposer le monde (dossier `HeraliZ` ; la carte de départ est dans `monde/HeraliZ/`) et à
démarrer. Attention : `server.properties` du dépôt remplace celui du serveur (port 25565 : vérifier que le panel
remet bien le port de l'allocation, sinon l'y changer).

Secrets à créer par le propriétaire (Settings → Secrets and variables → Actions) : `PTERO_SFTP_HOST`,
`PTERO_SFTP_PORT` (souvent 2022), `PTERO_SFTP_USER`, `PTERO_SFTP_PASSWORD`, et pour le redémarrage `PTERO_URL`,
`PTERO_SERVER_ID`, `PTERO_API_KEY` (clé « client » du compte Ptero), et `NOMS_INTERDITS` (le contenu de
`outils/noms_interdits.txt`, un mot par ligne). Pour Spartan : `SPARTAN_DISCORD_ALERTES` (webhook du salon PRIVÉ du
staff : alertes et rapports), `SPARTAN_DISCORD_SANCTIONS` (webhook du salon public des sanctions : pseudo, durée,
raison) ; la clé des jetons du launcher est lue sur la VM du site (section 6 bis). LiteBans : `LITEBANS_DB_USER` et
`LITEBANS_DB_PASSWORD` (base MySQL du Ptero, onglet « Databases » du panel) ; sans eux, LiteBans passe en base locale
(H2) et repart sans historique. Lien Discord ↔ Minecraft (heraliz-grades, `/lier`) : `HERALIBOT_LIEN_URL` (adresse
du bot HeraliBot sur le Ptero, `http://<machine>:<port du bot>`) et `HERALIBOT_LIEN_CLE` (contenu de
`data/cle-lien.txt`, créé par le bot à son premier démarrage), écrits dans `config/heraliz_grades/lien.json`. Ces deux
secrets sont FACULTATIFS : sans eux, la mise en ligne les lit elle-même sur le Ptero avec `PTERO_API_KEY` (serveur
nommé « HeraliBot » du même compte : sa clé et son port), clé masquée dans les journaux ; si c'est impossible, le lien
est désactivé (avertissement). Pack du launcher : les secrets `SITE_*` du site (section 8 bis). Personne ne doit les
écrire ailleurs.

**Monde des bases à neuf** : « Run workflow » avec la case « reinitialiser_bases » : le serveur est arrêté,
`<monde>/dimensions/crac/bases` est renommé `bases_ancien_<date>` (jamais supprimé), puis le serveur redémarre et Minecraft
recrée le monde des bases avec le terrain du jar CRac en place. Rien d'autre du monde n'est touché.

**Gunpacks du serveur** (`.github/workflows/gunpacks.yml`, « Run workflow ») : compare le dossier `tacz` du Ptero au
dépôt (`outils/gunpacks_serveur.py`). Le serveur envoie aux joueurs les objets de TOUS les gunpacks de son `tacz`, mais
les joueurs n'ont que les images du dépôt : un gunpack (ou un index d'arme) resté sur le Ptero sans être dans le dépôt
donne des carrés roses et noirs dans le menu créatif (12/10 : ~1 000 objets de 5 vieux packs et d'anciens index). Par
défaut lecture seule ; case « supprimer » : ce qui n'est pas dans le dépôt est SUPPRIMÉ du Ptero (gunpacks entiers,
index d'objets en trop ; effacés par le panel, un dossier en un appel, sinon fichier par fichier en SFTP, très lent),
puis redémarrer. Le pack par défaut de TaCZ (`tacz_default_gun`) n'est pas gardé (12/10) : avec
`DefaultPackDebug = true` (`serveur/tacz/tacz-pre.toml`), TaCZ ne le copie jamais de son jar chez les joueurs (49 objets
roses : MP5, AWP, M107, Kar98, Rhino…) et ses fichiers, différents des versions retouchées de `arkaniaz_pack_0_converted`
aux mêmes chemins, pouvaient passer devant sur le serveur selon l'ordre de lecture. Ses armes citées ailleurs ont été
remplacées par celles de nos packs (largages, caisses, Annihilator des Zombies). Les joueurs n'ont jamais ces gunpacks
(pack du launcher = dépôt). À lancer après avoir retiré un gunpack ou une arme du dépôt.

**Props de la carte** (consigne du propriétaire du 15/10) : utiliser les modèles de HeraliZ Loot, T1 à T6 seulement
(T7 = événements, jamais posé par le plan), et que **100 % des modèles T1 à T6** soient sur la carte, en nombre
équilibré (plus « toujours les mêmes »). C'est tout : ni meubles cfm (ni anciens meubles cfm à reposer, ni
variantes), ni points T7, ni familles (nourriture, soins, munitions). « Inventaire de la carte », case « props »
(lecture seule) : `outils/props_carte.py` découpe les pièces et relève les incohérences, puis `outils/props_plan.py`
fait le plan, étape **`diversification`** : un conteneur T1 à T6 déjà posé est remplacé par un autre modèle du MÊME
palier (le loot de la zone ne change pas de niveau), d'abord là où il y a une incohérence (2 cuisinières, 2 frigos,
prop d'extérieur dans une maison...), puis pour que chaque modèle ait son minimum de places (8 en T1, 6 en T2, 4 en T3
et T4, 3 en T5, 2 en T6, plafonné par les places du palier), puis pour équilibrer (un modèle à plus de 1,5 fois sa part
cède des places) ; un modèle posé ne disparaît jamais. Règles (`outils/props_catalogue.py`) : même gabarit (meuble ↔
meuble, objet ↔ objet, cadavre ↔ cadavre, véhicule ↔ véhicule ; un gros conteneur seulement s'il a la place libre
exacte), même orientation, type de pièce (pas de prop d'extérieur dans une maison ni l'inverse), une cuisinière / un
frigo par pièce ; conteneur avec des données (inventaire) jamais touché. Étape **`ajouts`** à part, seulement avec
l'option `--ajouts` (« props_options ») : un modèle sans AUCUNE place de remplacement cohérente (cadavres T2 à T4, dont
aucun n'est posé ; mobilier de salle de bain, de caserne de pompiers, de banque...) est posé sur une case libre, à
moins de 48 blocs d'un conteneur de son palier ; la part d'actifs de la zone baisse d'autant. Le rapport (`plan.md`)
donne les modèles posés avant / après et les absents avec leur raison. Sur la copie v3 : 366 modèles T1 à T6, 256
posés avant (70 %), 337 après la diversification (92 %, 4 615 remplacements sur 27 954 conteneurs), 366 (100 %) avec
les 39 ajouts. Plan enregistré dans `props_carte/` sur la branche de travail (jamais `main`). Après validation du
propriétaire : workflow « Props de la carte » sur cette branche (essai à blanc par défaut ; « envoyer », sauvegarde du
panel, étapes, lots, pause ; « annuler » rejoue le journal). Chaque commande est `execute if block <état relevé> … run
setblock …` (cases en plus d'un gros conteneur : `if block … minecraft:air`) : un prop changé entre-temps par les
constructeurs n'est pas touché ; HeraliZ Loot retire et repose seul les blocs « partie ». Rien testé en jeu.

**Extractions de la carte** (`.github/workflows/extractions.yml`, « Run workflow » ou envoi sur une branche de travail qui
touche le workflow ou `outils/extractions_carte.py`) : LECTURE SEULE sur le Ptero (registre
`data/heraliz_extraction_zones.dat` du monde en service, `level.dat`, config et, au choix, régions) ; liste les zones
d'extraction posées en jeu, dessine leur répartition (fond de la carte du monde, grille de 500 m) et leur couverture,
simule le tirage du serveur et propose des points pour combler les trous, avec la commande à taper en jeu
(`/execute in minecraft:overworld positioned X Y Z run extraction create <nom>`, à vérifier sur place) ; enregistré dans
`monde/extractions/` de la branche de travail (jamais `main`).

**Contrôle de branche** (`.github/workflows/branches.yml`) : à chaque envoi sur une autre branche que `main`, mêmes
fabrication et contrôles que la mise en ligne, sans rien envoyer au Ptero ni au site ; jars gardés 3 jours (paquets
« test-mods-serveur » et « test-mods-client », pour essayer en jeu). Ne faire passer une branche sur `main` que quand il
est vert : un mod qui ne compile pas reste hors de `main`.

Les contrôles `verif_split` et `verif_reobf` ont besoin de Minecraft en noms SRG : ils ne tournent que sur les PC
(section 6), pas sur GitHub. C'est pour ça qu'on vérifie AVANT d'envoyer.

---

## 8 bis. Le site HeraliZ (Azuriom)

- Le site est sur sa propre VM, à part du Ptero : **heraliz.fr** (et www) y mène ; **play.heraliz.fr** mène au serveur
  de jeu. Aucune IP ni aucun accès dans le dépôt.
- `.github/workflows/site.yml`, bouton « Run workflow » de « Site HeraliZ » :
  - **verifier** : regarde la VM sans rien changer (système, place, déjà installé, ports, pare-feu ; Azuriom installé ou
    non, API de connexion des comptes au launcher activée ou non, pack et installateur du launcher en ligne) et dit
    quels noms heraliz.fr mènent à la VM, plus les renvois SRV de Minecraft ;
  - **installer** : `site/vm/installer.sh` en root (sudo si besoin) : nginx, PHP 8.4 (8.3 ou 8.2 à défaut ; dépôt Sury
    ou ondrej si le système n'en a pas), MariaDB (base et utilisateur `azuriom`), installeur officiel d'Azuriom dans
    `/var/www/azuriom`, HTTPS Let's Encrypt pour les noms qui mènent à la VM (renouvelé seul), tâches planifiées
    (`/etc/cron.d/azuriom`), rsync et les dossiers du launcher : `/var/www/launcher` (son pack, servi à
    https://heraliz.fr/launcher/, rempli par la mise en ligne du serveur, section 8) et `/var/www/telecharger` (son
    installateur, https://heraliz.fr/telecharger/, section 8 ter). Se relance sans danger : un site
    déjà installé n'est jamais retéléchargé. Le site lui-même (thème, extension, boutique) se règle ensuite dans
    Azuriom, pas par ce bouton. À relancer après un changement de `installer.sh`.
  - **theme** : `site/vm/theme.sh` dépose le thème `site/theme/heraliz` (dans Azuriom : `resources/themes/heraliz` ; son
    `config.json`, réécrit par l'administration, n'est jamais écrasé) et l'extension `site/extension/heraliz` (dans
    Azuriom : `plugins/heraliz`), télécharge et active les extensions Boutique (`shop`) et Votes (`vote`) d'Azuriom,
    puis lance `site/vm/reglages.php` avec l'utilisateur du site : le thème redevient actif à chaque envoi ; une seule
    fois (repère `heraliz.api_launcher`) : API d'authentification d'Azuriom activée (comptes du launcher) ; une seule
    fois (repère `heraliz.reglages`) : nom HeraliZ, monnaie « crédit »,
    boutique payée en crédits (crédits achetés en euros), menu Accueil / Boutique / Votes / Règlement, page Règlement
    (celui du launcher), grades Rescapé / Traqueur / Roi de la Zone, trois premières actualités (images du launcher),
    serveur play.heraliz.fr suivi par simple ping. Chaque élément n'est créé que s'il manque : ensuite l'administration
    du site fait foi. Lancé tout seul à chaque envoi sur `main` qui touche `site/theme/`, `site/extension/`,
    `site/vm/theme.sh` ou `site/vm/reglages.php`. Le résumé de l'envoi dit si la connexion et la création de compte du
    launcher sont ouvertes (`/api/heraliz/etat`). Il réserve aussi le **pack du launcher aux comptes** (depuis le
    launcher 1.7.0) : clé `storage/app/heraliz/pack.cle` créée une seule fois, recopiée dans
    `/etc/nginx/heraliz-pack.conf` (lu par les deux accès `/launcher/` de nginx, module `secure_link`) : sans lien signé
    par le site (`/api/heraliz/pack`), tout fichier du pack est refusé (403 ; signature périmée : 410). Verrou posé
    seulement si le launcher publié (`/telecharger/latest.yml`) est au moins `PACK_LAUNCHER_MIN` (1.7.0, en tête de
    `theme.sh`) ; `PACK_RESERVE=non` le rouvre à tous. Après la pose, `theme.sh` essaie un fichier sans signature (403
    attendu) et un lien signé (200) : si le lien signé est refusé, le pack est rouvert tout seul (avertissement).
- Le thème : accueil, connexion, inscription, boutique (catégories, articles en cartes de grade), votes, pages et
  actualités refaits d'après les maquettes ; les autres pages (profil, panier, paiement...) gardent celles d'Azuriom,
  habillées par `assets/css/heraliz.css`. Grades en couleur sans icône. La mention « Propulsé par Azuriom » du pied de
  page est exigée par la licence d'Azuriom : ne pas la retirer. L'état du serveur est gardé 1 minute (un serveur éteint
  ne ralentit pas le site). Réglages du thème (liens Discord et launcher, saison, date de début, places) :
  administration → Thèmes → Configurer. Polices DM Sans et Oswald sous licence OFL (`assets/fonts/OFL.txt`).
- **Comptes du launcher** : le launcher se connecte avec les comptes du site par l'API d'authentification d'Azuriom
  (`/api/auth/authenticate`, `verify`, `logout` ; administration → Paramètres → Authentification → « API
  d'authentification »). L'extension HeraliZ ajoute `/api/heraliz/etat` (ce que le launcher peut proposer),
  `/api/heraliz/jeton-jeu` (jeton de lancement du jeu signé pour le compte connecté, section 6 bis),
  `/api/heraliz/pack` (pack de jeu du compte connecté, `PackController` : la distribution déposée dans
  `/var/www/launcher`, chaque lien vers `/launcher/` signé 6 h, MD5 de « expiration + chemin décodé + espace + clé »
  comme `secure_link` de nginx ; compte banni ou jeton inconnu : refusé ; 30 par minute et par adresse IP),
  `/api/heraliz/inscription` (création de compte, mêmes règles que la page d'inscription du site : pseudo de 3 à 16
  lettres, chiffres ou _, e-mail unique, mot de passe de 12 caractères au moins, confirmé ; 3 comptes par heure et par
  adresse IP, 60 par heure en tout) et `/api/heraliz/a2f/preparer|activer` (double authentification : QR code, puis
  code de l'application et mot de passe ; 8 codes de secours). Un compte créé dans le launcher est un compte du site
  (boutique, votes) et inversement. Le site ne garde qu'une session de launcher par compte : se connecter sur un autre
  PC déconnecte le premier. Contre qui essaie des mots de passe (`LimiteConnexion` de l'extension, launcher ET page de
  connexion du site) : 10 erreurs par adresse IP (une IPv6 compte pour son bloc /64, `Adresse`), puis 10 minutes
  d'attente ; 10 codes de double authentification faux pour un même compte, puis 15 minutes, quelle que soit l'adresse ;
  espaces retirés des codes (bug d'Azuriom 1.2.13 : un code de secours tapé avec un espace n'était jamais consommé).
  Aucun intermédiaire cru (`trustedproxy.proxies` vide : Laravel croyait le X-Forwarded-For de n'importe qui qui
  envoyait un nom en `.on-forge.com`, ce qui contournait toutes les limites ; reproduit puis corrigé le 11/10) ; nginx
  donne toujours au site son vrai nom (`fastcgi_param HTTP_HOST $host`, adresses « transmises » vidées). Liens écrits
  par le site forcés sur `APP_URL` s'il est en https (e-mails à venir). Appels d'autres sites (CORS) : seulement
  `/api/posts`, `/api/servers` et les flux, jamais les comptes. Mots de passe chiffrés par le site (bcrypt), jamais
  gardés par le launcher. Si le site passe un jour derrière Cloudflare : déclarer ses adresses dans
  `HeralizServiceProvider::register`, sinon tous les joueurs partageraient la même limite.
- « verifier » dit aussi ce qui est visible depuis internet : ports ouverts, base de données (seulement sur la VM),
  fichiers internes du site (`.env`, journaux, réglages : tous refusés), SSH et fail2ban. Étape « Vu depuis internet
  (comme un pirate) », lancée depuis une machine de GitHub, sans compte et en lecture seule : redirection HTTPS,
  certificat, anciens chiffrements, en-têtes de sécurité, cookies de session, listes de fichiers, pack du launcher,
  fichiers internes et chemins piégés (clé des jetons, sauvegardes, outils de débogage), mode débogage, données
  montrées par les API publiques (`/api/posts`, `/api/servers`, flux RSS), appels depuis un autre site,
  administration, captcha d'inscription. Problème = avertissement, « à renforcer » = conseil.
- **securite** (« Run workflow », et à la fin d'« installer ») : `site/vm/securite.sh` installe fail2ban (6 mots de
  passe SSH faux en 10 minutes = adresse bloquée 1 h, jusqu'à 2 jours si elle recommence ; filtre réglé pour
  OpenSSH 10, dont les refus s'écrivent sous le nom `sshd-session`), coupe LLMNR / mDNS et ajoute à nginx, sur un site
  déjà installé, HSTS et le vrai nom du site (`installer.sh` les écrit dans les nouveaux). `theme.sh` remet à chaque
  envoi `storage/` et la clé des jetons en lecture pour le site seulement (600). Le SSH de la VM accepte
  root avec mot de passe (les workflows s'en servent) : le mot de passe doit rester long et unique. Attention : 6 erreurs
  depuis chez soi bloquent aussi sa propre adresse 1 h.
- Après le premier « installer » : ouvrir https://heraliz.fr et terminer l'installeur d'Azuriom (base : hôte `127.0.0.1`,
  port `3306`, base `azuriom`, utilisateur `azuriom`, mot de passe = secret `SITE_BDD_MDP` ; jeu : Minecraft Java ;
  compte administrateur au choix du propriétaire).
- Secrets : `SITE_SSH_HOTE` (IP de la VM), `SITE_SSH_UTILISATEUR`, `SITE_SSH_MDP`, `SITE_SSH_PORT` (seulement si ce n'est
  pas 22), `SITE_BDD_MDP` (mot de passe de la base, choisi par le propriétaire). Le mot de passe ne passe jamais dans
  une ligne de commande : le script est copié sur la VM (lisible par l'utilisateur seul), lancé puis effacé.
- Lancé sur la vraie VM le 11/10 (Debian 13, PHP 8.4, HTTPS en place) ; l'installeur web d'Azuriom (navigateur) reste
  au propriétaire.

---

## 8 ter. Le launcher des joueurs

- `launcher/` (Electron, moteur Helios) ; réglages dans `launcher/app/heraliz.json` (adresse de jeu, pack, Discord).
  Son pack de jeu est refait à chaque mise en ligne du serveur (section 8, point 6).
- **Compte HeraliZ obligatoire** (`launcher/app/assets/js/compte-site.js`, adresse du site : `siteUrl` de
  `heraliz.json`) : connexion par e-mail ou pseudo + mot de passe (+ code de double authentification si le compte l'a),
  ou création du compte dans le launcher (pseudo, e-mail, mot de passe, confirmation, règlement), section 8 bis. Après
  une création de compte ou une connexion sans double authentification, le launcher la propose (QR code à scanner,
  « Plus tard » possible) ; elle reste dans le menu du profil (« Double authentification »). Le mot de passe ne fait que
  passer : le launcher ne garde que le jeton du site, chiffré par Windows (`safeStorage`). Le compte est revérifié au
  démarrage et avant chaque partie (banni ou déconnecté sur le site : retour à la connexion). Le pseudo en jeu est
  celui du compte.
- `.github/workflows/launcher.yml`, « Launcher HeraliZ » : à chaque envoi sur `main` qui touche `launcher/`, tests puis
  installateur Windows, gardé 7 jours dans l'onglet Actions (paquet « launcher-windows », si le quota d'artefacts le
  permet), et paquets Linux (AppImage et .deb). L'application Mac n'est fabriquée que par « Run workflow » (une minute
  de machine Mac compte pour 10 sur un dépôt privé). « Run workflow » avec la case « publier » : chaque machine met ses
  fichiers en ligne, toujours aux mêmes liens (à mettre sur le site et le Discord) :
  https://heraliz.fr/telecharger/HeraliZ-Launcher-Setup.exe (Windows), `HeraliZ-Launcher.dmg` (Mac),
  `HeraliZ-Launcher.AppImage` et `HeraliZ-Launcher.deb` (Linux), avec leurs fichiers de version (`latest.yml`,
  `latest-mac.yml`, `latest-linux.yml`), envoyés par SFTP (`launcher/ci/publier.py`, aucun artefact entre deux
  tâches). Le launcher ne contient aucune clé : ses jetons de lancement sont signés par le site (section 6 bis).
- Les installateurs ne sont pas signés (certificats payants) : au premier lancement, Windows affiche « Windows a protégé
  votre ordinateur » → « Informations complémentaires » → « Exécuter quand même » ; Mac : Réglages Système →
  Confidentialité et sécurité → « Ouvrir quand même » (macOS 15+), ou clic droit → Ouvrir. Mise à jour automatique sur
  Windows et avec l'AppImage ; sur Mac et avec le .deb, le launcher annonce la nouvelle version et donne le lien.
  Mac et Linux : rien essayé sur une vraie machine (launcher 1.8.0).
- **Mises à jour, rien à retélécharger** : à chaque partie, le launcher relit le pack en ligne et ne télécharge que
  les fichiers changés ou manquants (empreinte de chaque fichier) ; depuis la 1.7.0, il le demande au site pour le
  compte connecté (`/api/heraliz/pack`, liens signés 6 h, section 8 bis) au lieu de le lire à `distributionUrl` :
  sans compte, le pack ne se télécharge plus ; un mod retiré du pack n'est plus chargé (liste de
  mods passée à Forge). Le launcher lui-même se met à jour seul depuis la 1.6.0 (`mise-a-jour.js`, electron-updater) :
  au démarrage il lit https://heraliz.fr/telecharger/latest.yml (écrit par « publier »), télécharge la nouvelle
  version, l'installe sans rien demander et redémarre ; au plus 2 essais par version (pas de redémarrage en boucle),
  ensuite il demande de le retélécharger. Les launchers 1.5.0 et plus anciens doivent être retéléchargés une fois.
  Changer de version : `npm version x.y.z` dans `launcher/` (le numéro doit monter), puis « publier ».

---

## 9. Ajouter ce que le propriétaire fournit

- **Un de nos mods modifié ou nouveau (jar + sources)** :
  - s'il est déjà coupé (dossiers `src/commun`, `src/client`, `src/serveur`), le mettre dans `mods/<projet>` ;
  - sinon : `python outils/fusion_nouveau.py <dossier> <groupe> "<Nom>"` (nouveau projet depuis `mods/_gabarit`),
    `python outils/fusion_copier.py <sources reçues> mods/<dossier>`, puis la méthode (section 4 et la doc) ;
  - ne jamais mettre ses dossiers `build/`, `run/`, ni de README contenant des noms perso ;
  - mettre `LICENCE-HeraliZ.txt` (copie de `mods/_gabarit/src/commun/resources/`) dans ses ressources communes :
    `verifier.py` refuse un jar de nos mods sans cette mention de propriété (version « licence libre » pour un mod
    qui reprend du code GPL / LGPL, voir celles de CaraCrea, Bunker, Backpack, Tactique) ;
  - fabriquer, vérifier (noms perso compris), puis commit et push.
- **Un mod d'un autre auteur** : son jar dans `serveur/mods-tiers/` (ou `client/mods-tiers/` s'il ne sert qu'aux
  joueurs), en retirant l'ancienne version.
- **Un gunpack TaCZ (nouveau ou mis à jour)** : son dossier dans `serveur/tacz/`, puis `python outils/silencieux.py`
  (portée du son de ses silencieux, `verifier.py` le réclame), `python outils/accessoires.py` (ses accessoires dans
  les listes communes et les listes communes sur ses armes, `verifier.py` le réclame ; un accessoire fait pour une
  seule de ses armes va dans `PROPRES` de l'outil), `python outils/verif_accessoires.py` (placement de chaque
  accessoire sur chaque arme, `verifier.py` le réclame), `python outils/icones_sans_contour.py` (retire le
  contour noir de ses icônes d'inventaire) et `python outils/icones_sans_logo.py` (retire le logo « M » de Max Stuff,
  `verifier.py` le réclame). Ses `assets/` (modèles, sons, icônes) servent aussi aux joueurs : leur
  redonner le dossier `tacz` ; ses `data/` sont envoyées par le serveur. Retirer un gunpack ou une arme du dépôt ne
  l'enlève pas du Ptero : ensuite, workflow « Gunpacks du serveur » avec « supprimer », puis redémarrer (section 8).
- **Un pack de textures** : il est imposé par heraliz_interface
  (`mods/heraliz-interface/src/main/resources/resourcepacks/textures` et `.../herbe`) : remplacer le contenu du dossier
  par celui du nouveau pack, puis refabriquer heraliz_interface. Pack imposé depuis le 11/10 (heraliz_interface
  1.2.1) : le « pack 8 », c'est-à-dire l'ancien pack plus les vitres de la nouvelle carte (6 textures de verre
  refaites, 6 modèles de vitres ajoutés) ; l'herbe n'a pas changé. Retouches faites ici depuis (validées par le
  propriétaire) : capteur de lumière rendu à Minecraft (1.5.1) ; porte et trappe en chêne noir vieillies (vitre
  transparente, poignée en métal sombre) et 16 lits à cadre de métal usé (1.6.1). Les anciens fichiers
  `door_dark_oak_upper/lower.png` (noms d'avant la 1.13) ne sont plus lus.
- **Le monde** : depuis le 11/10 (soir), la carte se construit **directement sur le Ptero** (monde `dayz_map`) ;
  personne n'y touche par ici (section 0, règle 10). La mise en ligne ne copie dans le monde que les réglages
  (`serverconfig/`), jamais les blocs. **Quand les propriétaires disent que la carte est finie** : copier le monde du
  Ptero dans GitHub (dossier complet, par SFTP, serveur arrêté ou sauvegarde faite avant pour une copie propre) ; ce
  sera la carte « finale » gardée en mémoire. Les fichiers de plus de 100 Mo sont refusés par GitHub (sinon : archive
  dans une Release, comme Mohist) et chaque copie ajoute sa taille au dépôt pour toujours : une seule copie, à la fin.
  Ce qui est dans `monde/HeraliZ/` aujourd'hui est l'ancienne copie (v3) :
  la **carte de départ** (monde neuf, jamais joué) mise dans `monde/HeraliZ/` le 11/10, sur
  décision du propriétaire. Version « finale v3 » : 64 régions, 259 Mo, apparition en -435, 66, 1118 ; son `level.dat`
  est en créatif, paisible, commandes permises (carte de construction : sur le serveur, c'est `server.properties` qui
  fixe le mode de jeu et la difficulté). Elle contient son pack de données (`datapacks/heraliz_carte` : zones de loot
  par lieu et par palier) et ses réglages (`data/heraliz_zones.json`, `data/heraliz_brume.json`,
  `data/heralisz_train.dat`).
  - On ne la modifie jamais ici ; elle sera remplacée en entier par la copie finale du monde du Ptero.
  - Le **monde en service** sur le Ptero (changé par les joueurs) n'est jamais dans Git. La mise en ligne automatique
    ne dépose et n'écrase jamais de monde. La case « deposer_carte » (section 8) ne doit plus servir tant que la carte
    se construit sur le serveur (voir aussi section 11, « Nouvelle carte » : trois mondes prévus).
  - **La carte a besoin de trois de nos mods à ces versions au moins** (sans eux, leurs blocs disparaissent au
    chargement du monde et le train roule sur l'ancien tracé). Ils ont été apportés du dossier de travail de la carte
    le 11/10 ; les joueurs doivent avoir les jars `-client` de la même fabrication :
    - **HeraliZ Décors 1.7.0** (`mods/heraliz-decors`, nouveau) : grillage militaire, sable et blocs du désert, taches
      de sang, brouillard et zones dangereuses (gaz, radiation, froid) ;
    - **HeraliZ Utils** : apporté le 11/10 (version 2.2.0 : réseau de train de la nouvelle carte, gares droites,
      éoliennes à l'abandon `eolienne_usee` et `eolienne_moderne_usee`, ascenseur qui porte mieux les joueurs) ;
    - **HeraliZ Loot** : apporté le 11/10 (version 0.5.0 : les nouveaux conteneurs HD posés sur la carte, rotation du
      loot quand un conteneur est posé ou remplacé à la main).
    Avant de retirer un bloc d'un de nos mods, relancer le workflow « Inventaire de la carte » (lecture seule, liste
    les blocs posés dans chaque monde du Ptero) : un bloc retiré qui est posé disparaît de la carte au chargement.
    Si le propriétaire fournit une nouvelle version d'un de ces mods pour la carte, la rapporter ici de la même façon (assembler avec ce
    qui a été fait ici entre-temps, fabriquer, vérifier). Rien de tout cela n'a été testé sur le serveur.
- **Monde du spawn** (Utils 2.12.0, 15/10) : dimension `heralisz:spawn`, déclarée par `src/serveur/resources/data/heralisz/`
  de HeraliZ Utils. Vide autour, rien ne s'y génère, pas de monstres ni de combat entre joueurs, nuit fixe, sa propre
  bordure. Son contenu est la grotte du terminus de la cinématique : l'ancien monde du spawn de l'ancienne carte n'a été
  retrouvé ni dans Git ni sur le Ptero. Elle est **générée** par `python outils/monde_spawn.py grotte --rendus <dossier>`,
  jamais retouchée à la main (`copier <monde> --zone ...` recopie une zone d'un monde, en lecture seule). Dépôt UNE fois
  dans `dimensions/heralisz/spawn/` du monde en service : workflow « Serveur HeraliZ », case `deposer_spawn` (refus si le
  dossier existe), puis redémarrer. Arrivée : première connexion et mort sans lit → point de réveil (cinématique).
  Extraction : retour au même point (`useHeralizSpawn` de `heraliz_extraction-common.toml`, par
  `fr.heralisz.utils.api.SpawnApi`, à ne jamais renommer). **Borne de départ** (`heralisz_utils:borne_depart`, modèle de
  `mods/heralisz-utils/outils/borne_depart.py`, provisoire à valider) : clic droit au spawn → 5 s sans bouger → vraie
  téléportation sur la carte, à un point sûr tiré au hasard (sol naturel à ciel ouvert, pas d'eau, loin des joueurs,
  hors zones dangereuses, jamais de terrain généré), fondu et 5 s de protection. Staff : `/depart borne | point
  ajouter|retirer|tp|liste <nom> | zone ajouter <nom> <rayon> | essai [tp] | joueur <cibles> | info`. Points gardés avec
  la carte (`data/heraliz_depart.dat`), réglages dans `heraliz_spawn-server.toml`.
  **Spawn composé** (15/10) : grotte du terminus (réveil -61 265 2, épave, inchangés ; porte de service de la niche
  retirée) + le schematic du spawn `spawnbangerdelamort` à l'est, relié par un tunnel de pierre de taille (3 × 3,
  escaliers si besoin) qui part de la niche ; la borne de départ est dans la partie spawn. Workflow « Schematic du
  spawn » (`.github/workflows/spawn_schematic.yml`, lecture seule sur le Ptero) : cherche le schematic (racine,
  `plugins/`, `schematics/`, `config/`, structures du monde), le copie dans `monde/spawn_schematic/` avec son rapport,
  contrôle l'arrivée, puis compose `monde/spawn/` et `monde/spawn_rendus/` par `python outils/monde_spawn.py composer
  <schematic>` (`--position`, `--borne`, `--ecart`), sur la branche de travail seulement ; jamais retouché à la main.
  Formats : `.schem` de WorldEdit et `.nbt` (pas l'ancien `.schematic` de MCEdit). Si l'ensemble dépasse la bordure
  (256 blocs autour du réveil), la taille conseillée est écrite dans `serveur/defaultconfigs/heraliz_spawn-server.toml`.
  `deposer_spawn` dépose `monde/spawn/` tel quel. La cinématique ne dépend pas de l'ancienne carte : seul un décor réglé
  en jeu (`data/heraliz_cinematique.dat` de la carte, `/cinematique reveil`) passe avant celui du mod. Rien testé en jeu.
  **Protection du spawn** (Utils 2.13.0, 16/10, `spawn/ProtectionSpawn`, serveur) : dans `heralisz:spawn`, aucun dégât
  pour les joueurs, quelle que soit la source ; seul `/kill` passe. Chaque tick : feu éteint, chute, gel et air remis à
  zéro, faim gardée pleine. Effets négatifs refusés et retirés. Un joueur tombé sous le monde est ramené au point de
  réveil par une vraie téléportation. Aucune créature (même celles des mods de zombies) ; celles déjà là sont retirées
  chaque seconde, sauf `entites_permises`. Les explosions n'y cassent rien et ne touchent personne. `/suicide` y est
  refusé, sauf au staff. Réglage `[protection] actif` de `heraliz_spawn-server.toml` ; `/depart info` montre l'état.
  Rien testé en jeu.
- **Monde des bases** (CRac 1.5.0, 16/10) : dimension `crac:bases`, déclarée par `mods/crac/src/serveur/resources/data/crac/`
  (dimension, type, worldgen) ; Minecraft la crée seul au démarrage, **rien à déposer**. Terrain (CRac 1.6.0, 16/10) :
  grands plateaux parfaitement plats à 3 hauteurs (sol en 67, 71, 75 ; environ 65 % de la carte), pentes douces entre
  eux, collines basses et arrondies (jusqu'à +16), 0 à 3 grands reliefs lisses (jusqu'à +38) ; ni eau (ni océan,
  rivière, lac, source ni nappe), ni lave, ni grotte, ni minerai, ni structure ; 7 biomes propres (`crac:bases_*`) ;
  **aucune créature** (biomes sans apparitions, toute entité `Mob` refusée à l'entrée et retirée chaque seconde,
  `NatureBases` ; exceptions : `creatures_permises`) ; aucun seau ne s'y vide, même dans une base (`seaux`). Bordure
  propre de 2 500 blocs (rayon 1 250 autour de 0 0), points d'arrivée à 100 blocs au moins de la bordure
  (`rayon_depart` 1 150). Fichiers **écrits** par `python mods/crac/outils/monde_bases.py` (`--apercu DOSSIER [--graine
  N]` : vraies formes et proportions, pas les vrais emplacements), jamais à la main. Minecraft ne refait pas un morceau
  déjà créé : après un changement de terrain, workflow « Serveur HeraliZ », case `reinitialiser_bases` (serveur arrêté,
  `dimensions/crac/bases` renommé `bases_ancien_<date>`, jamais supprimé, puis redémarré ; perdus : constructions,
  claims et carrières du monde des bases ; gardés : `data/crac_bases.dat`, groupes). En survie, on ne construit que là
  (`construction_ici_seulement`). Déplacements (CRac 1.6.0, 16/10) : « au spawn » = monde `heralisz:spawn` ou à moins de `spawn_rayon` (48) blocs du point /spawn.
  `/home [nom]` seulement depuis le spawn, vers un de ses homes (toutes les 3 min) ; sans home : un message, AUCUNE
  téléportation. `/sethome [nom]` seulement dans sa base (claim de son groupe, ou du clan avec le droit d'entrer) ;
  `/delhome <nom>`, `/homes`. 1 home sans grade boutique, +1 par rang (Rescapé 2, Traqueur 3, Roi de la Zone 4 ;
  `GradesApi.rangBoutique` par réflexion) ; staff sans limite. Home « base » créé tout seul à la pose du premier Claim
  Block ; home qui n'est plus dans sa base : supprimé, pas de téléportation. `/zone base` (alias `/rtp`) seulement
  depuis le spawn, vers un point plat au hasard loin des autres bases (toutes les 5 min). `/spawn` seulement depuis sa
  base, hors combat, vers le point posé par le staff (`/spawn definir|effacer|voir`, gardé dans `crac/spawn.json` à la
  racine du serveur), sinon le point de réveil ; la première arrivée (cinématique) ne change pas. 3 s immobile ; carte
  DayZ par la borne de départ ; tous les messages dans le chat. Tutoriel après la cinématique (joueur en survie, sans
  base, jamais fait ; rien en créatif ou en spectateur) : titres, kit calculé sur les coûts réels de
  `crac-building.toml` (1 plan, 1 marteau, 1 Claim Block, 172 bâtons, 212 planches de chêne), envoi sur un point plat ; protégé (contour vert, ni blessé ni blessant) jusqu'à sa base
  posée (Claim Block sur 2 fondations), 2 h de jeu au plus ou départ sur la carte ; puis base protégée 4 jours (ni raid
  ni usure, texte flottant ; tombe au-delà de 2 membres). Carrières : `/carriere creer <nom> <rayon> <type>` (pierre,
  minerais, fer, bois, sable, argile, terre ; listes `#crac:carriere/<type>`), `liste|retirer|tp|regenerer` ; casse
  permise en survie, repousse en 10 min (jamais sous un joueur), rien ne s'y pose ; le staff bâtit la carrière
  (WorldEdit) puis la déclare. Staff : `/tuto lancer <joueur>` (tutoriel complet tout de suite, même en créatif, même
  avec une base), `/tuto rejouer <joueur>` (remise à zéro), cinématique rejouée par le staff = tutoriel relancé à la
  fin ; `/protection voir|retirer|donner <joueur> [jours]`.
  Réglages : `serveur/defaultconfigs/crac-bases.toml` (`[bases]`, `[home]`, `[tuto]`, `[protection]`, `[carrieres]`).
  Données : `data/crac_bases.dat` du monde principal, `dimensions/crac/bases/data/crac_carrieres.dat`. Rien testé en jeu.
- **Le fond de la carte du monde** (écran « Carte », touche M) : vue de dessus satellite et relief, en tuiles PNG de
  1024 px au plus avec `carte.json`, dans `mods/heralisz-utils/src/client/resources/assets/heraliz_carte/carte/` (jar
  joueur de HeraliZ Utils). Jamais dessiné à la main : refait par `python outils/carte_monde.py <copie du monde>
  --installer` (LECTURE SEULE du monde ; numpy et Pillow ; environ 1 min pour 64 régions ; 1 à 4 blocs par pixel pour
  tenir en 2048 px, environ 1,5 Mo par vue ; `--stats` liste les blocs du dessus et leur couleur), puis fabriquer
  heralisz-utils. Le workflow « Inventaire de la carte » le refait aussi depuis le monde en service (paquet « fond-carte »,
  7 jours, avec deux aperçus) : poser ses fichiers dans le dossier ci-dessus ; case « installer_fond » (sur une branche de
  travail, jamais `main`) : il les pose lui-même dans le mod et les enregistre sur cette branche. Fond actuel : provisoire,
  fait le 12/10 depuis `dayz_map` en construction (4096 × 4096 blocs autour de 0, 2 blocs par pixel). Sans fond, l'écran
  montre « Carte en préparation ». À refaire quand la carte est finie,
  et seulement depuis une carte propre (une construction de joueur posée sur la carte apparaîtrait sur le fond ; les
  blocs de CRac sont ignorés d'office) ; chaque version ajoute environ 3 Mo au dépôt pour toujours : la refaire rarement.
  Recadré sur la bordure du monde (Utils 2.14.0, 16/10) : `carte_monde.py` lit la bordure dans le `level.dat` du monde
  (défaut ; `--bordure x1 z1 x2 z2` ou `--bordure aucune`) et l'écrit dans `carte.json` (clé `bordure`). Au-delà, le
  terrain est « étiré » (bandes de neige au nord, traits dans la mer) : c'était la « zone neige étirée ». L'écran
  n'affiche rien au-delà de la plus petite de : bordure envoyée par le serveur, clé `bordure`, emprise des images. Fond
  actuel : `bordure` mise à la main à -1500 / 1500 (celle de la v3).

---

## 10. Décisions et préférences du propriétaire (à respecter)

**Cap général** : à terme, tous les mods du serveur seront les nôtres, avec leurs sources, reliés entre eux, au style
des écrans de clan, « full opti » (zéro lag, même sur les petits PC). On ne retire un mod tiers qu'une fois son
remplaçant prêt. Projets maison prévus : ambiance sonore (remplacera MAtmos et TCTcore), traverser les feuillages
(remplacera Passable Foliage et Kiwi), zombies de la survie (remplaceront Mutants & Zombies, Zombie Awareness,
CoroUtil), véhicules et parachute sans ywzj, carte du monde « AAA », optimisation des 5 boss,
« HeraliZ Décors » (le mod existe depuis le 11/10 avec les décors de la nouvelle carte ; il lui reste à reprendre
nos props sans le code de MrCrayfish, pour remplacer cfm).

**Fabrication** :
- GeckoLib = geckolib222 4.4.7 seul, jamais embarqué ;
- réponses des commandes en message direct au joueur ; les tests audio sont muets ;
- armes et modèles « niveau AAA » façon TaCZ : formes lisses, textures HD propres, cadrans lisibles sans scintillement,
  main placée comme TaCZ ;
- rien de nouveau dans les caisses ni en plans de craft : le loot et le craft seront refaits d'un bloc. En attente :
  les 7 armes de Fuse, les 4 véhicules, le jerrican, le parachute, les 7 sacs de Fuse.

**Boss** : gardés Ymirak, Mère des Ruines, Sujet N-07, Porteur de Peste, Sylvorn. Glacivore supprimé partout ;
Knight, Kraken et Vulkhar mis de côté. Le propriétaire trouve que les boss rament : les optimiser.

**Zombies (heralisz_matchs, `/zombies`)** :
- première carte The Giant (refonte de Der Riese, BO3), le plus fidèle possible ; ensuite Kino der Toten ;
- fin de carte : boss Sujet N-07 dans sa salle sous l'usine C (descente en monte-charge, intro passable, téléporteur
  de retour après sa mort, pas de récompense pour l'instant) ;
- HUD façon Black Ops 6 (barre de vie seulement après des dégâts, PV ×5, manche à la craie rouge, grosse annonce aux
  manches 5, 10, 15, 20 puis toutes les 10) ;
- dégâts des zombies qui montent avec les manches, plafond à la manche 20 ; zombies qui escaladent ; quête longue
  (fusibles, âmes…), objets de quête placés au hasard, guidage vers les objectifs, secrets cachés ; atouts mélangés à
  chaque partie ; camos du Pack-a-Punch réservés aux zombies ;
- sons d'origine fournis par le propriétaire : lui demander ses fichiers plutôt que d'en synthétiser.

**Autres choix** :
- CRac : aucun sac de couchage (retour à la base par la commande de téléportation, lits normaux) ; seuls les C4
  abîment les constructions ;
- casino : revente avec 5 % de commission, échanges d'objets coupés, entrée non payable avec le compte ;
- zone VIP du casino (16/10, Casino 2.3.0, Grades 1.1.0) : deuxième zone, à part de celle du casino (dedans ou
  ailleurs), réservée aux grades de la boutique Rescapé, Traqueur et Roi de la Zone, sans payer (le staff sans grade
  boutique n'entre qu'en créatif ou spectateur). Commandes : `/casino zone vip a|b` (coins, ou la Tablette du Directeur),
  `creer`, `entree` (deux videurs VIP flanquent la porte), `garde` (un videur VIP de plus), `info`, `supprimer`. Un
  joueur sans grade est remis dehors et le videur VIP lui dit « Non, tu n'es pas désigné comme VIP (grade Rescapé,
  Traqueur ou Roi de la Zone). » (aussi dans son chat). Fauché dedans : sorti par les videurs comme au casino (mêmes
  réglages `expulserFauches`, `delaiExpulsion`). Grade lu par réflexion (`AccesVip` → `GradesApi.rangBoutique`) : sans
  HeraliZ Grades, la zone VIP est fermée. Chaque videur sait sa zone (NBT `Zone`, `casino` par défaut). Rien testé en jeu ;
- bonus de loot (HeraliZ Loot, au premier tirage d'un conteneur) : chaque objet a 10 % de chances d'être tiré une
  seconde fois (le plus rare est gardé) et les quantités montent de 10 % ; sources cumulables, plafond 25 % : membre
  dans une zone tenue par son clan, objet « Bonus de loot » de la boutique (`heralisz_utils:document_lootbonus`, 2 h de
  jeu décomptées seulement en ligne, gardé à la mort) ;
- raretés des objets (HeraliZ Loot, validées) : Commun gris, Peu commun vert, Rare bleu, Épique violet, Légendaire
  or, Unique rouge ; un léger dégradé de la couleur derrière l'objet et un trait fin en bas de la case, partout
  (inventaire, coffres, sacs, barre d'objets DayZ), infobulle « ★ Rare » (rien pour Commun). Réglage :
  `serveur/config/heraliz_loot/raretes.json` (sections `objets`, `armes`, `accessoires`, `munitions`, `soins` ; remplie
  le 14/10 pour l'Armurerie, les armes, accessoires, munitions et soins, voir « équilibrage ») ; le staff, objet en main :
  `/rarete <niveau|aucune|effacer|voir|defaut|recharger>` (choix en jeu dans `raretes_jeu.json`, jamais écrasé) ;
- extractions de la carte (16/10, Extraction 1.5.0) : 35 à 40 points possibles ; rotation par vagues (`waves` de
  `serveur/config/heraliz_extraction-common.toml`, fichier du dépôt qui fait foi) : 4 zones ouvertes ensemble pendant
  1 h, puis toutes changent, sans reprendre celles de la vague précédente si possible ; tirage au hasard
  (`serveur/Repartition`), au moins 800 blocs entre deux zones ouvertes (`minDistance`) et une par quart de la carte
  (`spread`) ; si c'est impossible, les quarts puis la distance (−15 % à chaque pas) puis la vague précédente sont
  relâchés (noté dans le journal) ; les zones qui attendent depuis le plus longtemps passent un peu devant ;
  `/extraction rotation next` lance une vague entière. Répartition : workflow « Extractions de la carte » (§8) et
  `python outils/extractions_carte.py`. Retour au spawn. Rien testé en jeu ;
- camos à vie (pour la boutique) : clic droit sur un objet camo = il entre pour toujours dans la collection du joueur
  (objet consommé ; stockée hors du monde, `heralisz_camo/collections.json` à la racine du serveur, elle survit aux
  changements de carte). Onglet Skin : camos de l'inventaire (posés depuis l'objet : définitifs, comme avant) ou de la
  collection. Un camo de la collection ne vaut que sur une arme à la fois et seulement entre les mains de son
  propriétaire ; mort avec l'arme (pas à terre, pas en match) : il la quitte et ne se remet pas avant 15 min. Camos du
  Pack-a-Punch exclus. Staff : `/camos voir|ajouter|retirer|recharge <joueur> [camo|tous]` ;
- territoires des clans : 3 zones au plus par clan ; chaque zone tenue verse 100 hérals par heure à la banque du clan ;
  une ville vaut son niveau de loot (1 à 7, `/territoire valeur <zone> <1-7>`, clé « valeur » de zones.json) et
  rapporte au clan 10 XP par heure et par point de valeur (l'aérodrome T6 fait monter le clan plus vite dans le
  classement que Tcherno T3-T4) ; captures et temps tenu par ville sont gardés pour les statistiques du clan.
  Récompense à la capture : à décider plus tard (« pas abusé ») ;
- caisses (heraliz_crates) : Vulkhar, Chevalier sans visage, Kraken et Pendentifs supprimées (Glacivore aussi), et
  plus aucun pendentif (charm) d'arme dans Tactique ; la caisse Gemme montre une gemme qui change de couleur (plus de
  couteau dans le cristal) ; hitbox = vraie forme de chaque modèle en cubes de 2 px (`python outils/formes_caisses.py`
  après tout changement de modèle de caisse), contour noir visible seulement en créatif ; nom flottant sans fond
  (rareté entre deux traits, nom de la caisse, clé · clic droit) ;
- barricade en bois (heralisz_bunker) : modèle « mur de rondins couchés » choisi par le propriétaire (rondins entre trois
  poteaux, agrafes de fer, jambes de force), même taille, pose et collision qu'avant ; modèle et textures (4 états
  d'usure) refaits par `python outils/barricade_modele.py` (modèle : `mods/heralisz-bunker/outils/BarricadeModele.java`) ;
- salles à carte (15/10, Bunker 0.12.0, Loot 0.7.0) : le staff délimite une salle au bloc près, avec la baguette (`/salle baguette` : clic gauche = coin 1, clic droit = coin 2, contour visible chez lui) ou avec `/salle coin1|coin2 [x y z]`, puis `/salle creer <nom> [verte|bleue|rouge]`. Les portes à carte à 3 blocs sont liées et la couleur est prise d'elles ; `/salle lier|delier <nom>` en regardant la porte ou son lecteur ; aussi `liste`, `voir`, `redefinir`, `recharger`, `retirer`. Données gardées avec le monde (`heralisz_bunker_salles.dat`). Après la carte, 10 s pour entrer (en plus du temps d'ouverture). Personne dedans : la porte se referme. Tant qu'un joueur vivant est dans la salle ou dans l'encadrement, elle reste ouverte, puis se referme 2 s après la sortie du dernier. Un déconnecté ne compte pas ; un joueur qui se reconnecte dedans fait rouvrir la porte. Loot (conteneurs de HeraliZ Loot dans la boîte, zone `salles_carte/<nom>`) : jamais tant que quelqu'un est dedans ; il réapparaît en entier 30 min (verte), 2 h (bleue), 4 h (rouge) après la sortie du dernier, en temps de jeu. Réglages : section `[salles]` de `heralisz_bunker-common.toml`. Coffres T7 non concernés. Rien testé en jeu ;
- Réanimation : les zombies peuvent achever un joueur à terre ; les exécutions respectent le PvP ; touches : exécuter
  I, menu =, abandon Retour arrière ;
- Emote : roue ², menu 0 ; aucune emote à terre, en combat, en duel ou en ClashWar ;
- vitres (11/10, 12/10, 13/10) : une balle casse la vitre ou le bloc de verre touché, reposé au même endroit au bout de 5 min (même après un redémarrage ; les vitres fines voisines perdent leur raccord vers le trou et le retrouvent à la repousse : deux vitres côte à côte cassées repoussent reliées ; section `[vitres]` de `heralisz_tactical-server.toml`, mod tactique). Cassent aussi les blocs de `blocs_en_plus` : la fenêtre de meubles `cfm:vb_4` (posée sur la carte ; avant, les balles la traversaient) devient `cfm:vb_5` (la même, vitre brisée) puis redevient intacte. Jamais dans le monde du spawn (`heralisz:spawn`), en match, en zombies, dans les bunkers ni dans les claims CRac ; plus de cercle de 64 blocs autour du point d'apparition de la carte (`rayon_spawn = 0` depuis le 13/10 : ce point est au milieu d'une ville, -435 1118 sur la v3, dont le verre ne cassait jamais). Un bloc posé ou cassé à la main à la place d'une vitre en attente : la main l'emporte, elle ne repousse pas. Staff : `/vitres pourquoi [x y z]` (bloc visé ou donné : casse-t-il, sinon pourquoi — réglage, eau, bloc protégé, dimension, rayon du spawn, claim CRac et son bloc de claim —, vitre en attente, ce que ferait une balle tirée d'ici). `DestroyGlass` de TaCZ reste à false (TaCZ casse sans regarder les protections ni les blocs voisins) ;
- portée du corps à corps (13/10, Tactique 2.13.0) : 3 blocs au plus pour toutes les armes (choix du propriétaire), un seul réglage `[melee] portee_max` de `heralisz_tactical-server.toml` (`melee/PorteeMelee`), mesuré des yeux au point le plus proche de la cible comme Spartan (qui annule au-delà d'environ 3,6). Armes de mêlée de LR : avant 4, Lame de Desmoulins 4,25 / 4,5, Marteau du châtiment 3 / 3,5 ; les fiches gardent leur valeur, ramenée au plafond chez le joueur et au serveur, et le serveur mesure comme le joueur (0,4 de marge réseau) au lieu des pieds aux pieds. Couteaux TaCZ : leur ancien réglage `[couteaux] portee` est retiré. Déjà sous 3, inchangés : bouclier flash 2,5, couteau à travers les fenêtres des Zombies 2,6, exécutions de Réanimation 3, crosses et baïonnettes TaCZ 1 à 2 ;
- s'allonger (11/10, 12/10, mod tactique, `allonge/`) : un appui pour s'allonger, un autre pour se relever, avec ou sans arme en
  main (sans arme : même pose et même vitesse que le « ramper » de TaCZ) ; pas de tir pendant qu'on se couche, délai avant
  de se rallonger ; plongeon façon CoD (en avançant, touche « Ramper » pendant un saut ou avec la touche de saut enfoncée) :
  élan décidé et donné par le serveur (setDeltaMovement + hurtMarked, suivi par Spartan), on retombe allongé ; réglages
  `[allonge]` de `heralisz_tactical-server.toml` (`plongeon`, `plongeon_vitesse`, `plongeon_hauteur`, `delai_plongeon`).
  Depuis la 2.12.0 : allongé, on ne court plus (avant, s'allonger ou retomber d'un plongeon en courant gardait la course :
  pour TaCZ le joueur courait à plat, tir refusé et arme baissée en se relevant) ; un plongeon qui finit debout (touche de
  saut tenue en retombant, allongé refusé) est signalé au serveur (paquet « se relever » de TaCZ) et le serveur arrête de
  lui-même un plongeon dont le joueur resaute : avant, le serveur le gardait couché pour les autres et pour les balles
  (petite hitbox, rechargement « couché » vu sur un joueur debout) jusqu'à 4 s, et sans fin s'il enchaînait les sauts ;
- duels (11/10) : défi reçu = petite notification en haut à droite (plus de grand écran au milieu) ; les dimensions des matchs et des zombies ont leur propre bordure du monde (avant, les arènes sortaient de celle de la carte : mort en boucle) ;
- duels (12/10, Matchs 0.6.0) : repères des sites A et B (recherche et destruction) bien visibles seulement pendant le choix de classe et les 8 premières secondes de chaque manche, ensuite une simple lettre très pâle ; toujours effacés près du viseur et quand on est sur le site ; Tab enfoncé = bien visibles ; rien du HUD des duels pendant une killcam (`duels/client/DuelHud`) ; faim figée pendant tout le duel (barre pleine, rien ne s'use, `DuelPartie.figerFaim`), donc plus de régénération naturelle de la vie en duel (seuls les soins de la classe soignent) ; la faim et l'inventaire d'avant le duel (nourriture comprise) sont gardés dans le Coffre et rendus à la fin (victoire, défaite, abandon, annulation ; déconnexion : à la reconnexion) ; killcam (`duels/killcam/client/Lecteur`) : pistes lissées chez le joueur (les positions arrivent au serveur par à-coups : les personnages tremblaient), corps recalculé comme Minecraft, joueur allongé (« ramper » de TaCZ) rejoué à plat, et les 24 dernières images (1,2 s de jeu, ralenti compris) vues par les yeux du tueur avec un petit viseur (« vue du tueur ») ;
- duels (13/10, Matchs 0.7.0) : recherche et destruction avec une vraie bombe (rendus validés : bombe 1 « ogive sur berceau », ordinateur 2 « terminal noir ») : une bombe posée en permanence sur chaque site (points `bombe_a` / `bombe_b` de la carte de duel, module tourné vers les attaquants ; B décalée de 2 blocs vers le nord, sinon cachée par la haie), et l'objet « charge » (`heralisz_matchs:duel_charge`, même identifiant) devenu un ordinateur portable noir (VX-9). L'attaquant accroupi contre la bombe (2 blocs) pose l'ordinateur ouvert et l'amorce en 5 s (3 codes, « AMORÇAGE · CODE n/3 ») ; relevé ou éloigné : l'ordinateur revient dans sa main, tout repart de zéro. Amorcée : l'ordinateur reste au sol, écran rouge avec compte à rebours de 40 s, relié à la bombe par un câble ; bombe rouge, bips qui accélèrent. Le défenseur accroupi sur l'ordinateur (1,6 bloc) le ramasse et le pirate en 6 s, sans s'éloigner de plus de 4 blocs de la bombe (longueur du câble) ; tué, relevé ou déconnecté : l'ordinateur tombe à ses pieds, toujours relié, le piratage repart de zéro. Sons de clavier et de piratage faits maison ; poses des bras par Player Animator s'il est là (réflexion, sinon accroupi normal). Bombe et ordinateur (`duels/bombe/BombeDuel`, `OrdinateurDuel`) ne se sauvegardent jamais : rien hors d'un duel ;
- duels (14/10, Matchs 0.8.0) : la killcam est vue **par les yeux du tueur du début à la fin** (plus de vue par-dessus
  l'épaule), regard lissé en courbe (`Lecteur.courbe`), arme du tueur dessinée en première personne (`ArmeEnMain` : rendu
  de TaCZ par réflexion, objet ordinaire sinon) ; repères des sites A et B refaits (l'ancien carré de 13 px était jugé
  « horriblement moche ») : badge arrondi en verre sombre, bord de la couleur de l'équipe, lettre, pointe vers le site et
  distance en mètres dessous ; bombe amorcée et ordinateur au sol au même style (« ! » rouge / ambre) ; mêmes règles de
  visibilité qu'avant (bien visibles 8 s, puis pâles et sans distance). Rien testé en jeu ;
- duels (15/10, Matchs 0.9.0) : stuff équitable : dans un /duel (1v1, 2v2, les deux équipes), tout le monde choisit parmi les 5 classes de l'hôte (celui qui a lancé le défi), copiées au lancement (`DuelPartie.classesHote`) : s'il modifie ses classes ou se déconnecte pendant la partie, rien ne change pour ce duel. Le serveur n'accepte qu'un numéro de 1 à 5 et construit le kit depuis la classe de l'hôte (aucun camo ni marque de camo à vie) ; le dernier choix mémorisé de chacun n'est ni lu ni écrit (tous partent de la classe 1 de l'hôte). Écran de choix : « Classes de <hôte> · les mêmes pour tous ». Entraînement (`/duel entrainement`) : ses propres classes, comme avant ; cible d'entraînement gardée tant que le duel dure (corrigé en 0.9.0) ;
- duels (16/10, Matchs 0.10.0) : plantage du jeu à l'amorçage de la bombe corrigé : l'écran animé de l'ordinateur
  (`duels/client/bombe/EcranVif`) écrivait ses cases et barres dans un tampon de dessin déjà terminé par le texte
  (chiffres tapés) ; ses textes sont maintenant dessinés après toutes les formes ; la pose des bras par Player Animator
  (`PosesBombe`) ne peut plus faire planter le rendu (erreur = poses coupées, accroupi normal). Repères des sites A et B
  refaits (le badge « faisait bulle de texte ») : petit carré de 10 px d'interface, contour fin, fond presque
  transparent, lettre fine, couleur de l'équipe atténuée, ni pointe ni distance ; opacité 0,40 (0,62 en début de manche
  et avec Tab), jusqu'à 5 fois plus pâle près du viseur, effacé sur le site ; même carré avec « ! » pour la bombe
  amorcée et l'ordinateur au sol ; texture générée par `outils/reperes/reperes_sites.py` (`--apercu`), jamais à la
  main. Bombe des sites 1,75 fois plus grande au rendu (`BombeDuel.ECHELLE`, modèle de `outils/bombe/modeles.py`
  inchangé : 2,9 blocs de long, 1,75 de haut) ; amorçage jusqu'à 2,8 blocs du centre de la bombe, câble du piratage
  4,8 blocs (`DuelsConfig`) ; pas de collision. Killcam : l'arme du tueur est dessinée à la fin du rendu du monde
  (`ArmeEnMain`, étape AFTER_LEVEL) et non plus à l'évènement de la main (le joueur mort est spectateur : Minecraft ne
  dessine jamais sa main, on ne voyait rien) ; le serveur enregistre la visée TaCZ du tueur (`Clip.VISE`, par
  réflexion) : arme en position de visée de TaCZ, léger zoom, petit viseur effacé ; chaque tir joue l'animation
  « shoot » de l'arme et un petit recul. Jars joueurs et serveur de la même fabrication. Rien testé en jeu ;
- duels (16/10, Matchs 0.11.0) : bombe des sites solide : les joueurs ne la traversent plus (boîte de collision qui suit
  son orientation, `BombeDuel.makeBoundingBox`, gyrophare non compris) et les balles de TaCZ s'y arrêtent sans dégât ni
  perforation (`duels/bombe/ReglesBombe`) ; l'ordinateur posé au sol arrête aussi les balles (pas pendant le piratage),
  les joueurs le traversent. HUD des duels en petit (`HudMoteur.compact`) : bandeau réduit, annonces en petit panneau
  sous le bandeau (plus rien au milieu de l'écran), bombe amorcée = petite pastille rouge avec le temps restant en haut
  au centre, « vous portez l'ordinateur » en petite ligne, amorçage / piratage en petit texte et barre fine sous le
  viseur ; ClashWar et zombies gardent leurs grandes annonces. Plus de killcam en duel (`DuelsConfig.KILLCAM = false`).
  Arène remise au propre à chaque début de manche et à la fin du duel (`DuelPartie.nettoyerArene`) : grenades, C4,
  fumigènes, molotovs et feu au sol, nuages, balles, objets au sol retirés dans les bornes du duel, feux éteints,
  particules effacées chez les joueurs ; bombes, ordinateur et cible gardés. Pendant l'amorçage et le piratage : joueur
  immobilisé (vitesse ×0 par un modificateur temporaire), ni tir, ni rechargement, ni visée, ni changement d'arme, ni
  coup, ni objet utilisé (refusé par le serveur) ; il peut regarder autour de lui. Rien testé en jeu ;
- équilibrage (14/10, choix du propriétaire après « meilleure armure + arme banale = 2 balles dans la tête ») :
  - armures (Armurerie 0.15.0) : plus d'icônes balles / explosion / couteau / morsure / radiation sous l'inventaire ni
    dans les infobulles ; **une seule protection**, la même contre tout ; tir à la tête : seuls casque et visage
    comptent ; tir au corps : seules les pièces du corps ; plafond 60 % ; radiation : Hazmat seulement ; froid :
    combinaisons contre le froid seulement ; gaz : masques à gaz et Hazmat seulement (le « Masque anti-émeute Lacrymo »
    et le « Demi-masque Croc » ne filtrent plus) ; les 23 niveaux NV regroupés en six raretés (tenue complète, corps /
    tête) : Commun 12/10 % (citadin, rescapé, ranger, woodland), Peu commun 22/18 % (nomade, gardien, cobra, sentinelle),
    Rare 32/26 % (rempart, vipère, sibérie, scorpion), Épique 41/34 % (contrat, bélier, fennec, blizzard), Légendaire
    50/42 % (barricade, béton, meute, corbeau, ours), Unique 60/50 % (mastodonte, titan) ; casques de vision nocturne :
    Peu commun, Épique, Légendaire, Légendaire ; ghillies, brassards, objets d'événement : aucune protection ;
  - armes : dégâts contre les joueurs réglés par famille (Tactique 2.14.0, `serveur/DegatsArmes`), joueur à 20 PV :
    fusil d'assaut 5,56 Rare 5,2 par balle (4 balles sans armure, 6 en Rare, 10 en Unique ; tête ×1,7 : 3 sans casque,
    5 avec un casque Unique), pistolet 9 mm 4,5 (tête ×1,6), mitraillette 4,0, fusil 7,62 6,0, 7,62 OTAN 7,5,
    mitrailleuse 5,4 / 6,4, précision 16 à 24 (tête ×2), pompe 24 par décharge ; ±4 % par rareté (Commun ×0,92 …
    Légendaire ×1,08) ; lance-grenades, couteaux, Wonder Weapons et dégâts contre les créatures : inchangés ;
  - raretés des armes : pistolet dès Commun, mitraillette et pompe dès Peu commun, fusil d'assaut dès Rare, précision
    Rare (anciens à verrou) à Légendaire (.338 et .50), mitrailleuses Épique (minigun Légendaire), lance-grenades
    Légendaire ; aucune arme Unique ; **tous les couteaux Unique**, jamais dans le loot de la carte (caisse Gemme
    seulement, rareté Unique aussi dans la caisse) ;
  - accessoires : rareté d'après la force de leurs bonus (moins de recul, de dispersion, visée plus rapide, silencieux,
    dégâts) : plus la rareté est basse, plus les bonus sont faibles ; lunettes par grossissement ; chargeurs par taille ;
    munitions spéciales et crosse « précise » Épique ou Légendaire. Leurs effets eux-mêmes viennent des gunpacks des
    autres auteurs et n'ont pas été changés ;
  - soins : au plus Épique (Surv12, CMS) ; nouveau soin « Attelle et bandage » (Peu commun) : jambe cassée et saignement
    soignés en 3,5 s (aspect provisoire : trousse CAR orange, à refaire après validation d'un rendu) ;
  Rien de tout cela n'a été essayé en jeu : régler les chiffres dans les scripts après les premiers combats.
- lot du soir du 14/10 (rien testé en jeu) :
  - barricade en bois (Bunker 0.11.0) : posée à fleur sur ce qu'il y a dessous (forme de collision : demi-dalle des
    routes à y + 0,5) ; collision (balles et joueurs) à la hauteur réelle du modèle (`BarricadeEntity.MUR_H` 37,2 px
    pour le mur de rondins, `POTEAU_H` 44 px pour les trois poteaux, segments 0, 4 et 8) : on ne voit plus par-dessus
    sans pouvoir être touché ;
  - plongeon (Tactique 2.15.0) : dans le sens des touches tenues (devant, derrière, côté, diagonale), vérifié par le
    serveur sur le déplacement ; canal « allonge » version 3 ;
  - train (Utils 2.10.0) : Maj ne fait plus jamais descendre (assis, debout, maintenue) : seulement s'asseoir devant
    une banquette ; on sort par une porte ouverte en gare (ou mort, téléportation, déconnexion) ;
  - armes TaCZ : infobulle sans perforation d'armure, dégâts à la tête, vitesse de déplacement, « Appuyez sur … pour
    les accessoires » ni nom du gunpack (`camo/mixin/ClientGunTooltipMixin`) ; « Dégâts » de l'infobulle et de
    l'Atelier = dégâts contre un joueur (réglages de `armes.json` envoyés à la connexion, `armes.DegatsJoueurs`, canal
    `heraliz_tactique:armes`) ; Atelier d'arme, onglet Accessoires : 11 caractéristiques en bas (4 colonnes), recalculées
    à chaque pose ou retrait, et chaque carte d'accessoire chiffre ce qu'il change sur l'arme (`atelier/Stats.effets`,
    `Effets.sur`) ;
  - couteaux (16/10, Tactique 2.17.0, après les retours du propriétaire sur la 2.16.0) : clic gauche = le coup rapide
    d'avant, gardé tel quel (animation « melee_push » du gunpack par le signal `bayonet_push` de TaCZ, son bruit, gestes
    de TaCZ « melee_upper » / « melee_2_upper » vus des autres, touche du serveur à 2 ticks) ; clic droit = coup puissant
    comme le coup lourd des couteaux de LR : LE COUTEAU LUI-MÊME TOURNE avec le geste (os du bras, du poignet et du
    couteau ; armé à 0,16 s, lame dressée ; impact à 0,24 s, pointe amenée sur le réticule ; torsion, dégagement, retour
    lent, 0,85 s ; karambit : crochet vers le bas puis tour autour de son anneau), bruits des couteaux de LR, petit
    mouvement de caméra (`couteaux/SecousseCouteau`). Animation « melee_stock » de chaque modèle écrite par
    `python mods/heraliz-tactique/outils/couteaux_coups.py` (tableau `GESTES` ; `--apercu DOSSIER`), jamais à la main ;
    jouée par `couteaux/AnimCouteaux` (fondu 0,05 s), secours `bayonet_stock` puis coup du gunpack. Vu des autres : estoc
    de LR « stab_upper » (`couteaux/GesteCouteau`). Le serveur touche le coup puissant à l'impact
    (`CoupsCouteau.DELAI_COUP_LOURD`, 5 ticks) ; dégâts, cadence et portée inchangés. Impact de l'animation (`IMPACT` du
    script), caméra et délai serveur à changer ensemble. Canal « couteaux » version 3 ;
  - corps des joueurs « démembré » et tête vers le ciel en s'allongeant : la remise à zéro de Réanimation 1.15.0
    (`client/Poses.apply`) n'efface plus les poses de Player Animator ; l'animation « allongé » de TaCZ suit le corps
    couché (`mixin/client/TaczAllongeAnimationMixin`) et le regard de la tête allongé est limité
    (`TaczTeteAllongeMixin`) ; gestes debout de la mêlée de LR arrêtés allongé (`IdleAnimationHandler`). CaraCrea ne
    déforme rien lui-même (il recopie le modèle de Minecraft) ; pistes restantes s'il reste des défauts : ses cubes
    ignorent les parties cachées par d'autres mods (jambes du terminal d'extraction), emotes permises allongé.
- C4 [WIP] (`heralisz_grenade:c4`) retiré de tout le jeu le 16/10 (Tactique 2.17.0 : fiche, modèles, textes ; coffres T6
  avec le détonateur, caisses, ravitaillement, recette de heralisz_bases, établis Zombies). La charge de C4 de CRac
  (`crac:c4`) reste et abîme seule les constructions ; `c4ThrowableIds` de `crac-server.toml` et la source
  `heralisz_grenade:c4` du blindage des bases ne visent plus rien. Le moteur des explosifs collants et l'objet détonateur
  restent dans le code, sans source ;
- bases (16/10, CRac 1.5.0, Clans 2.5.0) : plus de bunkers de clan, chaque joueur bâtit sa base CRac dans le monde des
  bases ; ressources de base (bois, pierre) dans les carrières, ressources d'amélioration (fer, diamant) sur la carte
  DayZ ; contour vert des protégés : variante « fin et net » codée, halo en réserve ; indication de base protégée :
  texte flottant codé, panneau sur la porte en réserve (maquettes à valider) ;
- casse des blocs (14/10, CRac 1.4.0-beta, `event/CasseBlocs` + `serveur/event/CasseBlocsServeur`) : en survie, un joueur ne
  casse AUCUN bloc et ne peut même pas commencer (pas de fissures ; avant, casser un bloc qui revenait permettait de se
  glisser dedans accroupi). Libres : créatif, spectateur, staff (permission 2). Exceptions laissées aux règles de CRac :
  le bloc de revendication et, dans une base seulement, les échelles et trappes des pillards (`#crac:raid_access`). Les
  objets posés de CRac se ramassent par leur touche. Les cultures aussi sont bloquées (à rouvrir si la ferme doit marcher) ;
- viseur standard 5-10x (`tacz:scope_standard_8x`) retiré (14/10) : fichiers listés dans `serveur/tacz_retires.txt`,
  retiré des listes, des armes, du loot ; remplacé par la lunette Vudu dans les kits des matchs.
- sons (11/10) : MAtmos jouait le bruit de changement d'objet de chaque joueur visible, sans atténuation (sons stéréo) : dayzhotbar (`SonChangementObjet`) ne laisse entendre celui des autres qu'à 8 blocs au plus ;
- accroupi (11/10) : le clic droit doit toujours marcher accroupi (fouiller, se soigner, viser, réanimer) ; avec une arme TaCZ en main, la touche « Interagir » de TaCZ (O) ouvre les conteneurs de loot, caisses et cadavres (tag `tacz:interact_key/whitelist` dans heraliz-loot) ;
- logo de Max Stuff (11/10) : le petit « M » est retiré des icônes de ses accessoires, choix du propriétaire malgré la licence du pack (CC BY-NC-ND 4.0 : pas de version modifiée diffusée ; tous les gunpacks des autres auteurs ont la même licence, et NC à surveiller avec la boutique). Auteurs crédités dans `serveur/tacz/arkaniaz_pack_5_converted/CREDITS.txt`. D'autres logos restent sur des icônes d'armes (« Chill Deep » du pack arkaniaz_custom, « MIL », « CIB ») : pas touchés sans demande ;
- touches (11/10) : une seule table des touches par défaut, appliquée chez le joueur par heraliz_interface (`client/Touches.java`) sans toucher aux jars des autres auteurs : pas de doublon (G = mode de tir de TaCZ, H = inspecter, Tab = liste des joueurs), Curios sur U, mode de la voix (chuchoter / parler / crier) sur X, carte du monde sur la touche MARQUÉE « M » du clavier du joueur (12/10 ; sur AZERTY c'est la touche du « ; » d'un QWERTY, qui était « Couper mon micro » de CaraCrea : le micro passe alors sur la touche voisine marquée « , »), touches inutiles cachées de l'écran des commandes ; une touche choisie par un joueur n'est jamais changée. Une touche de mod avec Maj, Ctrl ou Alt sur un bouton de souris prend tout ce bouton dès que le modificateur est enfoncé (Forge) : à ne jamais refaire ;
- sprint en un appui (12/10, heraliz_interface 1.7.0) : interrupteur dans le menu Échap ; c'est l'option « Sprint : Basculer / Maintenir » de Minecraft (Options > Commandes), enregistrée dans `options.txt` (`client/Sprint.java`). Activé : un appui sur la touche de sprint pour courir, un autre pour arrêter. En repassant à « Maintenir », la touche de sprint est relâchée (sinon le jeu la croirait encore enfoncée). Réglage du joueur, rien côté serveur ;
- Armurerie : les combinaisons intégrales (Hazmat, froid, plongée) se portent seules (sac à dos permis) ;
- Curios (13/10, Armurerie 0.14.0, Interface 1.7.1) : l'inventaire du joueur est l'écran de Curios. La touche E (survie, aventure) l'ouvre directement : inventaire, armure, artisanat 2 × 2 et, à gauche, le panneau des slots Curios toujours ouvert, aussi haut que l'inventaire, sans bouton Curios (`client/curios/InventaireCurios` de l'Armurerie). Le jeu du joueur n'affiche pas l'inventaire de base : il envoie le paquet d'ouverture de Curios et le serveur ouvre son menu (rien avant, pas de double ouverture ; sans réponse en 1,5 s, l'appui suivant ouvre une fois l'inventaire de base, en secours). Rien ne change en créatif, en spectateur, sur un cheval ou à terre. Livre de recettes fermé à chaque ouverture (ouvert, Curios le pose sur l'inventaire). Fond, cases et pastilles « afficher sur le joueur » au style de l'inventaire du pack (`curios/textures/gui/inventory_revamp.png` du pack imposé) et icônes des 7 slots vides refaites au propre le 15/10 (masque à gaz, gilet porte-plaques, gants, genouillère, sac à dos, ghillie, brassard : dessins pixel par pixel dans l'outil, au gris des icônes d'armure du pack ; celles du sac, du corps et du brassard remplacent celles de Curios dans le pack ; Armurerie 0.15.1, Interface 1.7.2) : refaits par `python mods/heraliz-interface/outils/curios_textures.py`, jamais à la main. Maj + clic : celui de Curios range un objet Curios dans le premier slot Curios libre qui l'accepte (validateurs, combinaisons) ; une armure va d'abord dans son slot d'armure s'il est libre ; l'ancien Maj + clic de l'inventaire de base (mixin `InventoryCuriosQuickMoveMixin`, `MajClicCurios*`) ne sert plus qu'au secours. Règle des combinaisons aussi sur les slots d'armure de l'écran de Curios (`CuriosArmorSlotMixin`, `SuitRules.refusesArmorSlot`). Grille d'artisanat de Curios (`GrillesCurios`, serveur) : à la mort, ses objets vont dans l'inventaire (donc sur le corps, camos à vie retirés) ; à l'ouverture, la grille de l'inventaire de base est rendue à l'inventaire ; objets toujours déplacés, jamais copiés. Maj + clic droit sur une boîte de munitions (TaCZ Addon) marche aussi dans l'écran de Curios (`BoiteMunitions`). Côté serveur, l'inventaire ouvert est un vrai menu : la caisse de largage ne s'ouvre plus d'elle-même si le joueur a son inventaire ouvert. Utilise des classes internes de Curios 5.14.1 : à revérifier après une mise à jour de Curios (en panne : inventaire de base, avec une ligne de journal). Rien testé en jeu ;
- carte du monde (12/10, maquettes validées : vues satellite et topographique, zoom sur une ville ; `heraliz_carte`
  dans HeraliZ Utils 2.8.0) : touche M ; à gauche les calques à cocher (territoires des clans, zones d'extraction,
  points d'intérêt, mon clan, pings du clan, grille de 500 m : colonnes A, B… et lignes 1, 2…), la liste des
  territoires et leur état, son clan ; en haut à droite Satellite / Topographique ; fiche d'une zone au clic
  (propriétaire, tenue depuis, protection, bonus de loot, niveau de loot, ravitaillement de son clan, rayon, distance ;
  « Lancer le ravitaillement », que le serveur n'accepte qu'au drapeau comme avant, sinon « Marquer pour le clan ») ;
  en bas coordonnées, carré, échelle et aide ; boussole. Souris : glisser, molette (zoom autour du curseur), Espace =
  revenir sur soi. Jamais d'autres joueurs que les membres de son clan dans le même monde (envoyés par le serveur,
  rien en match) ; les états des zones sont ceux que le drapeau montre à tous (capture en cours et clan qui capture
  compris). Clic droit = ping du clan sur ce point (sans portée ni ligne de vue ; réglage `carte` de
  `heraliz_ping-server.toml`, `/ping admin carte`), clic droit sur son propre ping = le retirer. Lieux nommés posés EN
  JEU par le staff à l'endroit où il se tient : `/carte lieu ajouter <ville|village|aerodrome|militaire|centrale|usine|
  port|hopital|gare|point> <nom>` (ailleurs : `/execute positioned X Y Z run carte lieu ajouter ...`), `/carte lieu
  retirer <nom>`, `/carte lieu liste`, `/carte nom <nom>` (sous-titre « Monde · … ») ; gardés avec le monde
  (`data/heraliz_carte_lieux.dat`, ils suivront la copie finale de la carte). Fond : section 9 ;
- carte du monde, refonte « AAA » (16/10, Utils 2.14.0, proposition 1 « Verre » des maquettes, À VALIDER ; proposition 2
  « Tactique » en réserve) : aucun texte sous 7,5 unités d'interface ; noms de villes en capitales avec halo, plus grands
  de près ; étiquettes sans chevauchement (villes d'abord) ; pastille d'état sous chaque territoire ; interrupteurs des
  calques ; boutons + et − ; grille depuis le coin nord-ouest de la bordure ; déplacement et zoom bornés à la carte ;
  territoires, lieux et extractions hors de la bordure non affichés (« N hors de la carte (à reposer) ») ;
- chat vocal : celui de CaraCrea (Simple Voice Chat retiré) ;
- launcher (maquettes validées) : fond « carte » (relief sombre, quadrillage, point rouge « Zone HeraliZ »), au-dessus
  du logo « VERSION 1 · SAISON 1 », onglets Accueil / Règlement / Boutique (« Bientôt »), trois nouveautés non cliquables
  sur l'accueil (pas d'onglet Nouveautés, `launcher/app/assets/js/nouveautes.js`), Discord bien en
  vue (bouton en haut à droite + grand bouton sur l'accueil et l'écran de connexion). **Aucun accès au dossier du jeu**
  depuis le launcher et aucun nom de fichier affiché pendant le téléchargement (ne pas faciliter le vol des jars) ;
- comptes et site (choix du propriétaire) : site **Azuriom** au style HeraliZ, comptes communs au site et au launcher
  (un compte créé d'un côté marche de l'autre), boutique, crédits du site, votes, actualités, relié au serveur par
  AzLink. Inscription pseudo + mail + mot de passe, confirmée par un code reçu par mail ; **code par mail à chaque
  nouveau PC** (pas à chaque nouvelle IP) ; le serveur vérifie le ticket du launcher (mode B de Spartan, la clé ne
  quitte plus nos serveurs) ; le mot de passe du jeu AuthMe reste en plus. Mails automatiques depuis une adresse
  @heraliz.fr par un service d'envoi (Brevo). Domaines heraliz.fr et heraliz.com chez IONOS. Site hébergé à part du
  Ptero (petit serveur loué au nom du propriétaire conseillé, 10 € par mois au plus) ;
- boutique : achats en argent ou en crédits du site, livrés tout de suite si le joueur est connecté, sinon à sa
  prochaine connexion, une seule fois. Règles de Mojang : pas d'avantage de jeu payant (les crédits n'y changent rien :
  c'est ce qu'ils achètent qui compte) ; liste des articles à décider avec le propriétaire ;
- grades de la boutique, du plus petit au meilleur : **Rescapé** (cuivre `#E39B5E`), **Traqueur** (bleu glacé
  `#8FD3FF`), **Roi de la Zone** (or `#FFC23A`). Pour l'instant, seulement le nom du grade en couleur (site, chat, liste
  des joueurs), **sans icône** : insignes en métal, pixel art et survivants 3D avec une arme ont tous été refusés
  (« trop IA », « pas du tout l'idée »). Les icônes reviendront plus tard, à partir d'un exemple fourni par le
  propriétaire ou faites par un graphiste : ne pas en reproposer sans l'un ou l'autre ;
- écran titre HeraliZ (maquette validée le 11/10, heraliz_interface 1.3.0) : carte floutée en fond, logo, JOUER
  (rejoint `play.heraliz.fr`, joueurs en ligne et ping à droite), OPTIONS, QUITTER LE JEU, version en bas à gauche,
  drapeaux de langue en bas à droite. Il remplace aussi la liste des serveurs, les mondes solo et Realms (config
  client `ecran_titre.actif = false` : écrans vanilla, pour les essais en solo sur PC). Le launcher ne se connecte
  plus tout seul (option « Connexion directe » retirée) et écrit `lang:fr_fr` dans `options.txt` au premier lancement ;
- vue : 1re personne forcée en survie (façon DayZ) ; F5 seulement en créatif et en spectateur (staff). Les
  cinématiques des mods (exécution, badge, scènes) changent la caméra elles-mêmes ;
- langues : français (par défaut), anglais, russe, au choix par drapeau (écran titre et menu Échap) ; menus,
  descriptions, messages du serveur et commandes traduits ; le chat entre joueurs n'est PAS traduit (choix du
  propriétaire, 11/10 : pas de service de traduction payant) ;
- mort (11/10) : tout ce que porte le joueur part sur son cadavre (slots Curios compris, montrés à la fouille) ; cadavre
  qui disparaît au bout de 10 min, ou tout de suite une fois vide ; on ne peut rien y déposer. `keepInventory` doit être
  à false sur les mondes du Ptero (gamerule tapée en jeu) ; `/suicide` pour tous les joueurs (mort normale).
  Fouille du cadavre (12/10, Utils 2.8.0) : comme un coffre, au style de base façon DayZ (texture du coffre du pack) :
  en haut « Corps de X » (cause, temps restant), Équipement et Sac, Accessoires, Inventaire du mort, « Tout prendre »,
  en dessous son propre inventaire ; on fouille de là où l'on est, rien ne déplace le joueur (serveur : 5 blocs des
  yeux au corps pour ouvrir, 6 pour garder ouvert) ; boîte du cadavre dans le sens du corps (1 × 1,9 bloc, carré de
  1,6 en diagonale, 0,45 de haut), sans collision. Fouiller un joueur À TERRE (Réanimation 1.14.0) ne téléporte plus
  à côté de lui non plus (la réanimation, elle, garde sa place à genoux) ;
- loot (15/10, Loot 0.8.0 ; 16/10, Loot 0.9.0) : dans l'écran de fouille des conteneurs de HeraliZ Loot (salles à carte
  comprises) et des coffres T7, petit badge du palier « T1 » à « T7 » juste après le nom (T1 gris, T2 vert, T3 bleu,
  T4 violet, T5 or, T6 orange, T7 rouge ; `client/BadgePalier`, `EcranConteneur`), style de base façon DayZ inchangé ;
  palier lu chez le joueur dans le nom du bloc (`ConteneurBlock.palier`). Côté serveur, en survie et en aventure
  (libres : créatif, spectateur, staff permission 2 ; réglages dans `heraliz_loot-server.toml`) :
  - **conteneurs de Minecraft scellés** (`ConteneursVanilla`, `[conteneurs_vanilla]`) : coffres, barils, fours, alambic,
    entonnoir, distributeur, dropper, boîtes de Shulker, juke-box, bibliothèque sculptée et tout autre bloc `minecraft:` à
    inventaire (« Ce conteneur est vide et scellé. »), wagonnets à coffre ou à entonnoir, coffre des bateaux ; permis
    dans un claim de CRac (rangements des bases, `permis_claims_crac = true`) ; jamais nos blocs ni ceux de cfm ;
  - **postes de Minecraft hors service** (0.9.0, `[postes_vanilla]`, « Ce poste est hors service. ») : établi, enclumes,
    table d'enchantement, métier à tisser, coffre de l'Ender, cartographie, tailleur de pierre, table de forge, balise,
    meule, table d'archerie, pupitre, composteur, chaudrons, pots de fleurs, pot décoré, feux de camp, bougies, gâteau,
    ancre de réapparition, bloc musical, répéteur, comparateur, détecteur de lumière, fil de redstone, blocs de commande,
    de structure et jigsaw, cadre de portail de l'End, œuf de dragon, générateur de monstres, citrouille, ruches, et tout
    autre bloc `minecraft:` qui ouvre un menu (`menus_auto`) ; aussi dans les bases de CRac (`permis_claims_crac =
    false`, choix « bloquer en trop »). Restent permis : portes, trappes, portillons, boutons, leviers, plaques, lits,
    échelles, cloches. Rouvrir un bloc : liste `exceptions`. L'ancienne clé `coffre_ender` n'existe plus ;
  - **présentoirs protégés** (0.9.0, `Presentoirs`, `[presentoirs]`, « Ce présentoir est protégé. ») : cadres et cadres
    lumineux, porte-armures, mannequins de l'Armurerie (`heraliz_armory:mannequin`, un porte-armure), peintures, nœuds de
    laisse, et d'office toute entité accrochée ou tout porte-armure d'un autre mod (`auto`). Ni coup (y compris mêlée de
    Tactique et couteaux), ni interaction, ni projectile, ni balle de TaCZ (`EntityHurtByGunEvent.Pre`, par réflexion) ;
    aucune explosion ne les abîme ni ne casse le bloc qui les porte ; pose refusée hors d'une base. Libres dans les bases
    de CRac (`permis_claims_crac = true`) ;
  - **nuisances refusées hors des bases** (0.9.0, `Nuisances`, `[nuisances]`, « Impossible ici. » ; CRac laisse tout
    faire hors des claims) : seaux (eau, lave, neige poudreuse, poisson), briquet et boule de feu, outils sur un bloc
    (écorcer, gratter ou décirer le cuivre, cire, chemin, labour), poudre d'os, panneaux (écrire, teindre, faire
    briller, cirer, poser). Un panneau ciré reste utilisable : le staff cire les panneaux à commande du spawn.
  Rien testé en jeu ;
- fouille d'un joueur à terre (15/10, Réanimation 1.16.0) : ses emplacements Curios (masque, gilet, gants, genouillère,
  sac à dos, ghillie, brassard) sont montrés dans une colonne à gauche de la fouille, avec leurs icônes de case vide
  (`menu/SearchMenu`, cases 77 et suivantes ; serveur : `compat/CuriosFouille`, branché sur les vrais emplacements de la
  victime : objets déplacés, jamais copiés, sac à dos avec son contenu). Prendre : toujours (même un sac plein, comme les
  cadavres) ; déposer : validateurs de Curios et règle des combinaisons de l'Armurerie (par réflexion). Sans Curios :
  fouille comme avant. Les cadavres ne changent pas. Rien testé en jeu ;
- usure (12/10, 13/10, Utils 2.9.0) : l'indicateur de l'arme en main est en bas à droite, sous le HUD de TaCZ
  (`HudUsure`) ; habillage C « anneau » choisi par le propriétaire : pastille de verre de 16 px de haut (≈ 45 px de
  large), petit anneau de la couleur de l'état qui se vide dans le sens horaire (part perdue aux réparations en rouge au
  bout du tour), pourcentage en gras et état en petites capitales dessous ; arme ruinée : anneau et contour rouges,
  contour avivé au clic à vide. Formes en maillages adoucis d'un pixel d'écran (`FormesHud`, `Maillage` : nettes à toute
  échelle d'interface, calculées seulement quand l'état change), polices `hud_pct` / `hud_etat` (DM Sans gras 8,2 et
  5,2 px) ; largeur réservée au pourcentage stable (la pastille ne bouge pas à chaque tir), nom de l'état omis si la
  place manque près de la barre d'objets DayZ ;
- véhicules (13/10, Utils 2.9.0) : plus de coffre dans les véhicules, ni à bord ni sur une épave (mixin serveur
  `VehiculeSansCoffreMixin` sur `ContainerCraft` d'ywzj) ; ce qui était déjà rangé dans un véhicule posé est posé au sol à
  côté de lui, sans limite de temps, dès qu'il se charge (objets déplacés, jamais copiés ; noté dans le journal du
  serveur). PV max divisés par 5 (moto 20, buggy et fourgon 60, camion 100 ; `PointsDeVie`) : les véhicules déjà posés y
  passent en gardant leur pourcentage de vie. Plus de panneau « TOUCHÉ / DÉGÂTS / CRITIQUE » avec la petite vue 3D du
  véhicule quand on le touche, ni de barre de vie du véhicule touché (`client/IndicateurDegats`, calque
  `ywzj_vehicle:vehicle_hit_indicator` caché chez le joueur) ; restent les impacts de balles et la barre de vie d'un
  véhicule qu'on regarde (`showVehicleInfoDistance` de `ywzj_vehicle-server.toml`, 0 pour la retirer). Réglages
  `coffre_retire` et `pv_pourcent` de `heraliz_vehicules-server.toml`. Seuil de blindage d'ywzj inchangé (un coup sous
  50, ou 10 pour le buggy, n'enlève que 0,1 PV) ;
- physique des véhicules (15/10, Utils 2.11.0, `fr.heraliz.vehicules.Stabilite`, mixins serveur `VehiculeStableMixin` sur
  `PhysicsEngine` et `VehiculeConduiteMixin` sur `WheeledVehicle` d'ywzj, jar d'ywzj intact) : le moteur décalait le centre
  de gravité de (changement de vitesse × hauteur × 8) à chaque choc (≈ 10 blocs contre une dalle) et faisait basculer à
  17° par tick : retournements au moindre obstacle. Décalage réduit et borné (un choc seul ne retourne plus un véhicule à
  plat ; pente trop forte ou vide : il bascule toujours, moins vite), dalles et petites marches (0,55 bloc) franchies
  sans s'arrêter net dans le sens de la marche (pas par les flancs), plus de bonds (vitesse vers le haut donnée par les
  contacts plafonnée), meilleure adhérence à pleine vitesse (plus de glissade façon glace), volant du camion plus
  rapide. Section `[physique]` de `heraliz_vehicules-server.toml` (`stable = false` : moteur d'origine). Calculé par le
  serveur seulement (seul le jar `-serveur` compte). Rien testé en jeu ;
- couteaux TaCZ (13/10, Tactique 2.13.0, `fr.heraliz.tactique.couteaux`) : « Knife » CT et T, karambits, papillons, M9
  (fiche `arkaniaz:knife_data`) frappent comme les armes de mêlée de LR, réglés comme la Dague tactique : clic gauche
  (touche Tirer de TaCZ) = coup rapide, 6,5 dégâts (attaque du joueur + 5,5) toutes les 0,45 s, touche tenue = coups
  enchaînés ; clic droit (Viser) = coup puissant ×1,5 toutes les 0,9 s, plus de visée au couteau, rien si le viseur est
  sur un joueur à terre (Réanimation) ; V (« Mêlée » de TaCZ) = coup rapide (le coup de TaCZ n'est plus donné aux
  couteaux). Cône de 3 blocs (65° / 75°), plusieurs cibles, vue dégagée ; coup décidé et donné par le serveur
  (`serveur/CouteauxServeur`, coup de LR `IMeleeWeapon.attackTarget`), cadence vérifiée en temps réel. Animation
  « melee_push » du couteau pour les deux coups. Avant : pour TaCZ un couteau est une arme à feu, le clic gauche tirait
  une balle de 0,005 bloc et seul V frappait. Portée : `[melee] portee_max` (3, commune à toutes les armes de corps à
  corps). Réglages : section `[couteaux]` de `heralisz_tactical-server.toml` (`actif = false` : comme avant) ;
- train (11/10, 12/10) : un train qui roule heurte (2,5 cœurs, joueur projeté à côté) ; il ne tue que devant la locomotive ou
  dans la voie, sous la caisse ; dans la voiture, on marche comme au sol dans Minecraft (élan, vitesse du joueur, course
  vue des autres, bruits de pas, corps tourné dans le sens de la marche, arme de TaCZ qui balance) et on peut sauter ; on
  s'allonge avec la touche « Ramper » de TaCZ (elle ou Espace pour se relever ; seulement si TaCZ permet de ramper) ; plus de
  pose « assis sur une selle » avec une arme (mixins client sur l'animation de TaCZ). Les balles de TaCZ s'arrêtent sur la
  caisse, les cloisons, les banquettes, les tablettes, les caisses du fourgon, le soufflet et la locomotive
  (`train/Blindage`, `train/BallesTrain`, côté serveur) ; une vitre touchée casse et se répare au bout de 5 min (`/train
  vitres`, `/train vitres reparer`) ; un passager se touche au même instant que les murs (ping compensé pour un tireur hors
  du train ; mixin serveur sur `HitboxHelper` de TaCZ, config `heralisz_utils_serveur.mixins.json` dans `-serveur` et
  `-complet`). Contre les « téléportations » : horloge du train lissée chez le joueur (le temps du monde, recalé chaque
  seconde par le serveur, faisait reculer le train), voyageur annoncé avec son passager déjà assis dessus (sinon figé à la
  porte au-delà de 64 blocs), autres passagers lissés sur 3 ticks, voyageurs d'une même rame jamais cachés l'un à l'autre
  par l'anti-ESP de Spartan (message IMC `ally_predicate`). Réseau du train version 5 : jars joueurs et serveur de la même
  fabrication. La voie se corrige EN JEU, jamais dans les fichiers du monde (staff) :
  `/train voie info` (point visé), `genre <p|r|t|T|P|x> [longueur]`, `retirer [longueur]`, `annuler`, `liste`,
  `effacer` (aspect seulement, le trajet ne bouge pas ; gardé dans les données du train du monde) ; `/train voie remplir
  [ponts|tout]` pose sous la voie dessinée un bloc invisible et incassable (`heralisz_utils:voie_pleine`) pour qu'on
  ne puisse plus entrer dans le ballast des ponts, `vider` le retire. Sons du train (13/10, Utils 2.9.0) : tous fabriqués
  par le calcul (`python mods/heralisz-utils/outils/sons_train.py`, aucun enregistrement ; `--ecoute DOSSIER` donne des
  copies mp3 et deux scènes d'écoute) ; on modifie le générateur, jamais les `.ogg`. Joués chez le joueur seulement,
  tirés de l'horaire (`train/TrainSonsClient`, rien du serveur), portées dans `sounds.json` (`attenuation_distance`).
  Roulement sur le point de la rame le plus proche (72 blocs) ; à bord, boucle « cabine » sur le joueur et roulement
  extérieur baissé ; un « tac » par essieu qui passe un joint de rail (tous les 25 blocs, le rythme suit la vitesse ;
  40 blocs) ; moteur de la locomotive dont le régime suit l'effort (64 blocs) ; avertisseur 25 s avant chaque gare et
  2 s avant le départ (128 blocs) ; freins 5,5 s avant l'arrêt, puis air et carillon dans le haut-parleur du quai le plus
  proche ; portes côté quai (les 3 plus proches, 28 blocs) : ouverture 3 s après l'arrêt, bips puis fermeture 8 s avant
  le départ, calées sur l'animation ; au départ, desserrage, turbo et attelages qui se tendent ; portes intérieures
  (12 blocs). Les temps `BIPS` (1,4 s) et `FREINAGE` (5,5 s) sont à la fois dans le générateur et dans
  `TrainSonsClient` : à changer des deux côtés (et relancer le générateur) si l'horaire ou l'animation des portes change ;
- meubles cfm (12/10) : « Model 239 » (petit escalier en grille de métal) a la forme d'un escalier de Minecraft (on y monte sans
  sauter) au lieu d'un cube ; « Model 240 » celle de son garde-corps (poteaux et lisses, juste devant le bord du bloc) et
  « Model 241 » celle de son plancher en caillebotis (sur le dessus du bloc) ; ses meubles « Standard_Block » sont tous
  des cubes de 15 px : pour en reformer un autre, ajouter ses boîtes (relevées dans son modèle, meuble tourné vers le
  nord) à `FORMES` de `fr.heralisz.utils.meubles.FormesMeubles` (Utils, code commun : joueurs et serveur à la même
  version). Les formes s'arrêtent à 0,01 px des côtés du bloc : une forme qui couvre toute une face cache la face du
  bloc voisin, et on voyait le vide à travers la grille (Utils 2.7.0) ;
- onglets créatifs (11/10) : l'onglet « HeraliZ » (meubles cfm) ne montre que les meubles posés sur la carte (liste
  `heralisz_utils_onglet_heraliz.json`, refaite d'après le workflow « Inventaire de la carte ») ; armes de mêlée et
  couteaux seulement dans le groupe Mêlée de HeraliZ · Tactique (plus dans « Arme lourde ») ; l'onglet « HeraliZ —
  Décors de la map » (Utils 2.8.0, 12/10) n'a plus que les décors posés : les 200 jamais posés nulle part (carte, carte
  des zombies, arènes ; d'après le workflow « Inventaire de la carte ») sont retirés du mod avec leurs modèles et
  textures (panneaux de villes et de villages, 80 flèches, pièces de la voiture de train décorative, grande roue,
  drapeaux blanc et de secours, bannières naines…) ; les sols `sable_caillouteux` et `terre_craquelee` (autre onglet)
  restent ;
- au premier lancement : plus de tutoriel de Minecraft, et une fois par installation synchronisation verticale coupée,
  images par seconde illimitées, nuages et ombres des entités coupés (heraliz_interface, `ReglagesDefaut`) ;
- refusés par le propriétaire : ImmediatelyFast, Embeddium (aucun risque sur les interfaces) ; pas de barre de soif ;
- retirés, à ne pas réinstaller : Epic Hunt, zombiemod, Dynmap, Xaero, TaCZ NPC, SDM, FTB Library, CreativeCore,
  Necronomicon, Invocore, Resourceful Lib, SmartBrainLib, dyairdrop, CS Grenades, Kotlin for Forge, EndlessAmmo,
  Architectury, Magazine & Casing, Thirst Was Taken, PlayerRevive, heralisz_armor (remplacé par l'Armurerie),
  Heraliz Drugs, Chunky, immersivemessages.

---

## 11. Points ouverts et bugs connus

- **Loot** : 618 des 1 853 entrées des tables de HeraliZ Loot citent encore `heralisz_armor:*` (retiré) : les
  vêtements n'apparaissent plus dans le loot. À régénérer avec les objets de l'Armurerie (avec la refonte du loot).
- **Territoires des clans** (`serveur/config/heralisz_clans/zones.json`) : suivent l'ancienne carte, à refaire pour la
  nouvelle, en donnant sa valeur (niveau de loot) à chaque ville. Les statistiques par ville (captures, temps tenu)
  sont enregistrées mais pas encore affichées dans l'écran du clan (interface à valider d'abord).
  Les zones de l'ancienne carte hors de la bordure (Rudnik, Pristan, Gorny…) sont cachées sur la carte ; les reposer en
  jeu avec `/territoire deplacer <id> [rayon]` (Clans 2.6.0, centre à la position du staff) ou `/territoire drapeau
  <id>` ; `/territoire liste` marque « HORS DE LA BORDURE ». Bordure du monde `dayz_map` à vérifier (`/worldborder get`).
- **Loot dans le train** (souhaité, T1 à T4, T4 rare ; pas fait) : le train n'est pas fait de blocs, donc pas de
  conteneur posé ; prévu : des emplacements fixes dans chaque voiture (coordonnées de la voiture, comme les banquettes),
  tirés par HeraliZ Loot (paliers T1 à T4) et gardés par le serveur voiture par voiture (vidés à chaque tour de boucle),
  ouverts par un paquet quand un passager vise l'emplacement (même fouille que les conteneurs de la carte), dessinés par
  RameRendu.
- **Bugs repérés, non corrigés** (attendre l'accord du propriétaire) :
  - Sylvorn : les attributs de `SapNode` ne sont pas déclarés (risque de plantage à l'attaque CHANNEL ; correction d'une
    ligne dans `HeraliszSylvorn.attributes`) ;
  - Tactique : le feu au sol des molotovs dure toujours 2 s ;
    l'éclaboussure des grenades à effet est limitée à 4 blocs.
- **Carte du monde** (HeraliZ Utils 2.8.0, Clans 2.4.0, Interface 1.7.0) : fond provisoire du 12/10 (carte en
  construction), à refaire quand la carte est finie (workflow « Inventaire de la carte », case « installer_fond »,
  section 9) ; lieux et nom
  de la carte à poser en jeu (`/carte`) ; rien testé en jeu (écran, touche, pings depuis la carte, paquets des
  territoires). Les territoires suivent encore l'ancienne carte (`zones.json`, point au-dessus).
- **Wonder Weapons** : elles ont besoin de SimpleBedrockModel, fourni aujourd'hui par TaCZ et ywzj_vehicle (Jar-in-Jar).
  Si on retire ywzj, vérifier qu'il reste fourni.
- **Sécurité** : en mode cracké, un pseudo op est à qui l'enregistre en premier sur AuthMe : les admins enregistrent
  leurs comptes dès le premier démarrage. `ops.json` n'est pas dans le dépôt.
- **Monde** : sur le Ptero, le monde s'appelle **`dayz_map`** (`level-name` du `server.properties` du Ptero, lu par la
  mise en ligne). Celui du dépôt dit encore `HeraliZ` : une « installation complète » le mettrait à la place et le
  serveur créerait un monde neuf `HeraliZ` (aligner `level-name` avant). Avant le 11/10, la mise en ligne prenait le nom
  du dépôt et copiait `serverconfig/` dans un dossier `HeraliZ/` du Ptero, qui n'est pas le monde en service. Les
  configs de `serveur/defaultconfigs/` sont copiées par Forge dans tout nouveau monde.
- **Nouvelle carte (en construction sur le Ptero, monde `dayz_map` ; copie finale dans GitHub à la fin, section 9)** :
  trois mondes séparés : le spawn, la carte (loot, villes, extractions) et
  les bases des joueurs. Au spawn, un PNJ téléportera au hasard sur la carte (à faire quand la carte sera finie). Les
  territoires des clans ne marchent aujourd'hui que dans l'overworld (`Territoires.monde()`) : à rendre réglable si la
  carte n'est pas l'overworld ; l'extraction renvoie au spawn (`Destination`, réglable).
- **Rien n'a été testé en jeu** depuis la séparation client / serveur (version 2.x des groupes, Matchs 0.3.0, pack
  imposé de heraliz_interface 1.1.0).
- **Protection des jars contre la décompilation** : pas encore faite. Le propriétaire veut pousser au maximum (11/10).
  Ce qu'on a le droit de brouiller, vérifié dans le dépôt le 11/10 (paquets Java et champs `authors`/`credits` des
  `mods.toml`) :
  - **jamais** (code d'autres auteurs, licence à garder lisible) : `fr.heralisz.tactical` de **tactique** (crédité
    « LesRaisins Tactical, GPL 3.0 » dans son mods.toml) ; `com.hbm` de **bunker** (103 fichiers, « base HBM Modernized,
    GPL-3.0 ») ; `com.mrcrayfish` de **backpack** (178 fichiers, LGPL 2.1) ; Emotecraft (CC-BY-4.0) et PlayerAnimator
    (MIT) embarqués dans **emote** ; Concentus (BSD) embarqué dans **caracrea**. Le propriétaire pense ces mods « 100 %
    à lui » ; son propre dépôt le contredit (ces crédits et paquets). Les brouiller = violer la licence, risque pour
    HeraliZ : ne pas le faire sans preuve de propriété et relicence écrite.
  - **possible** (code 100 % HeraliZ) : boss, clansetbases, utils, matchs, casino, crates, loot, decors, composants, armory,
    wonder, reanimation, airstrike, interface, extraction, dayzhotbar, crac ; + le code propre à HeraliZ de caracrea
    (`com.caracrea`) et d'emote (`fr.heraliz.emote`) et les parties « All Rights Reserved » de tactique (camo, mêlée,
    soins, grenades) — en gardant à côté, lisibles, les parties tierces ci-dessus.
  - **Attention casse-serveur** : le brouillage renomme les classes ; or les mods s'appellent entre eux par le NOM de
    classes (API par réflexion, section 5), plus Mixins, GeckoLib, enregistrements Forge et modIds qui ne changent
    jamais. Un brouillage mal réglé casse le serveur. À faire par petits lots sur un PC, en gardant (`-keep`) toutes
    les API, Mixins, entités et le `@Mod`, puis `verifier.py` + essai en jeu avant d'envoyer. Gain réel modéré sur
    Forge.
  - **Pack réservé aux comptes** : fait (launcher 1.7.0, extension `/api/heraliz/pack`, verrou nginx de `theme.sh`,
    section 8 bis) : sans compte du site, ni la liste du pack ni ses fichiers ne se téléchargent. Un compte se crée
    librement : c'est un frein (rien d'anonyme, rien d'automatique), pas un coffre. Ensuite, le plus fort : modèles et
    skins de valeur envoyés par le serveur, gardés en mémoire, filigrane par compte. Un fichier affiché à l'écran peut
    toujours être capturé : but = vol difficile, inutile hors de HeraliZ, traçable.
- **Audit de sécurité « façon pirate » du 11/10** (site, launcher, dépôt, séparation client / serveur) : corrigé et
  mis en ligne (adresse IP falsifiable, limites de connexion du site, codes de secours, liens des e-mails, CORS, HSTS,
  droits de la clé, launcher 1.6.1). Reste :
  - mises à jour du launcher et `distribution.json` non signés : qui prend la VM du site peut livrer du code à tous
    les joueurs. Prévu : signature Ed25519, clé privée en secret GitHub (fabriquée chez le propriétaire), clé publique
    dans le launcher ;
  - SSH de la VM en root par mot de passe (≈ 7 600 essais de robots par jour, bloqués par fail2ban) : passer à une clé ;
  - Spartan : le jar joueur écarte d'office les crochets de rendu des modules dont le nom contient steam, discord,
    radeon… (`InjectionCollector`) et toutes les bibliothèques de `java.home` (`NativeCollector`) : tout remonter et
    décider côté serveur ; identifiant de ban PC fait de variables faciles à changer (`ComponentInfo`) ; en mode hors
    ligne, la connexion du jeu n'est pas chiffrée (jeton de lancement et `/login` d'AuthMe lisibles sur un Wi-Fi
    partagé) : envoyer le jeton chiffré avec un défi du serveur ;
  - inscription : « cet e-mail a déjà un compte » révèle qui a un compte (comme la plupart des sites) ; avec les
    e-mails, répondre pareil dans tous les cas et prévenir le titulaire ; captcha (Turnstile) à brancher par le
    propriétaire (administration → Paramètres → Sécurité) ;
  - pseudos du staff : à créer sur le site avant tout le monde (avec `tokenRequired = true`, le pseudo en jeu est celui
    du compte du site) ;
  - les fusions faites sur GitHub portent le compte et l'adresse mail du propriétaire (règle 4) : historique non
    réécrit (règle 3) ; réglage « Keep my email addresses private » conseillé.
- **Site et comptes HeraliZ (Azuriom)** : VM du site en place (heraliz.fr, section 8 bis), pack du launcher servi par
  le site (section 8), thème HeraliZ avec Boutique et Votes (choix « theme »). Reste au propriétaire, dans
  l'administration du site : moyens de paiement (Boutique → Paiements), articles et prix (les grades sont prêts, sans
  prix), sites de vote et leurs récompenses ; avant d'ouvrir la boutique : pages Conditions de vente
  (`cgv`), Confidentialité (`confidentialite`) et Mentions légales (`mentions-legales`), que le pied de page affiche dès
  qu'elles existent. Comptes du launcher (création, connexion, double authentification) en place (section 8 bis).
  Ensuite : AzLink sur le Ptero (livraison des achats en jeu), mails (Brevo : code de confirmation à l'inscription,
  code sur un nouveau PC). Ticket signé du site pour Spartan (mode B) en place, `tokenRequired = true` depuis le 11/10 :
  aucun autre launcher que le nôtre ne peut rejoindre le serveur ; AuthMe reste la barrière suivante.
- **Launcher** : installateur Windows fabriqué par GitHub (section 8 ter), pas encore donné aux joueurs. Comptes du
  site (connexion, création, double authentification) branchés le 11/10 à la demande du propriétaire ; essayés contre
  une copie locale du site (création, connexion par e-mail et par pseudo, activation de la double authentification
  avec un vrai code), pas encore sur Windows ni avec un vrai téléphone. Jamais essayé de bout en bout (pack du site,
  Forge, connexion au serveur) : à faire par le propriétaire avant de le distribuer.
- **Spartan** (08/10) : rien testé en jeu. Launcher : protections anti-injection branchées ; jeton signé par le site
  (mode B) depuis le launcher 1.6.0, `tokenRequired = true` depuis le 11/10 (jamais essayé en jeu). Prévu
  ensuite : détection de la visée assistée (aimbot) sur les tirs TaCZ (d'abord
  mesurer sans alerte), butin caché à travers les murs, protection du jar joueur contre la décompilation.

---

## 12. Le serveur Ptero

- Docker Image : `ghcr.io/pterodactyl/yolks:java_17` ; Server Jar File : `mohist-1.20.1-916-server.jar`.
- Mémoire « illimitée » dans le panel → la mémoire est fixée dans la commande de démarrage :
  ```
  java -Xms12G -Xmx12G -XX:+UseG1GC -XX:+ParallelRefProcEnabled -XX:MaxGCPauseMillis=200 -XX:+UnlockExperimentalVMOptions -XX:+DisableExplicitGC -XX:+AlwaysPreTouch -XX:G1NewSizePercent=40 -XX:G1MaxNewSizePercent=50 -XX:G1HeapRegionSize=16M -XX:G1ReservePercent=15 -XX:G1HeapWastePercent=5 -XX:G1MixedGCCountTarget=4 -XX:InitiatingHeapOccupancyPercent=20 -XX:G1MixedGCLiveThresholdPercent=90 -XX:G1RSetUpdatingPauseTimePercent=5 -XX:SurvivorRatio=32 -XX:+PerfDisableSharedMem -XX:MaxTenuringThreshold=1 -Dusing.aikars.flags=https://mcflags.emc.gs -Daikars.new.flags=true -Dterminal.jline=false -Dterminal.ansi=true -jar {{SERVER_JARFILE}} nogui
  ```
- Chat vocal de CaraCrea : port UDP dédié (24454 par défaut). Ajouter une allocation au serveur dans le panel et mettre
  son numéro dans `serveur/defaultconfigs/caracrea-server.toml` (`port = ...`) ainsi que dans le `serverconfig` du
  monde. Sans ça, la voix passe par la connexion du jeu et peut saccader.
- Un mod changé n'est pris en compte qu'au redémarrage du serveur.
