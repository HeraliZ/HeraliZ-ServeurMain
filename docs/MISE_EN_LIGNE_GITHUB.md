# Mettre le nouveau serveur sur GitHub (à la place de l'ancien) — 26/10

Ce projet remplace **tout le contenu** de l'ancien dépôt GitHub du serveur. Après l'envoi sur la branche `main`, GitHub
fabrique les mods, fait les contrôles et met à jour le Ptero tout seul (workflow « Serveur HeraliZ »), comme avant.

## 1. Avant l'envoi

1. **Sauvegarde du Ptero** depuis le panel (onglet Backups).
2. Personne ne doit envoyer sur l'ancien dépôt pendant ce temps (l'autre personne aussi).

## 2. Remplacer le contenu du dépôt

Dans le dossier du dépôt GitHub sur le PC (celui qui contient le dossier caché `.git`) :

1. Supprimer **tout** sauf le dossier `.git`.
2. Copier dedans **tout** le contenu du dossier préparé (y compris le dossier caché `.github` et le fichier
   `.gitignore`).
3. **Conseillé : d'abord sur une branche d'essai.** GitHub Desktop : « Current branch » → « New branch »
   `nouveau-serveur`, « Commit », « Publish branch ». GitHub fabrique et contrôle tout (workflow « Contrôle de branche »)
   **sans rien envoyer au Ptero**. C'est la première fabrication de ce projet sur une machine de GitHub : si elle échoue,
   rien n'est cassé, il suffit de demander à Claude de lire l'erreur.
4. Quand c'est vert : passer la branche sur `main` (sur GitHub : « Pull request » → « Merge »). La mise en ligne au Ptero
   part à ce moment-là. (Sans branche d'essai : « Commit to main » puis « Push origin », avec le message
   « Nouveau serveur HeraliZ (projet propre) ».)

L'historique Git est gardé : l'ancien serveur reste lisible dans les anciens commits. La Release « base-serveur » (le jar
de Mohist) ne change pas.

## 3. Ce que GitHub fait tout seul (onglet Actions, « Serveur HeraliZ »)

- Fabrique tous les modules (la première fois : environ 30 à 60 min).
- Contrôles (GeckoLib, langues, noms interdits).
- Met à jour le Ptero par SFTP :
  - `mods/` devient la copie exacte des nouveaux jars : **les anciens jars sont retirés** (dont `heraliz_boss`,
    `heralisz_matchs`, `heraliz_wonder`, `dayzhotbar`…) ;
  - `config/`, `defaultconfigs/` (recopié dans `dayz_map/serverconfig/`), `tacz/`, `heraliz_clashwar/`, plugins ;
  - les **anciens gunpacks** (`arkaniaz_pack_0_converted` à `_5`, `arkaniaz_custom_converted`, `Cislumen_Armory`) sont
    supprimés du Ptero (liste en fin de `serveur/tacz_retires.txt`) ;
  - `config/heralisz_clans/zones.json` (territoires) n'est **jamais écrasé** s'il existe déjà sur le Ptero : les
    drapeaux posés en jeu sont gardés ;
  - les secrets (Spartan, Discord, LiteBans, lien Discord) sont écrits à l'envoi, comme avant.
- Refait le pack du launcher avec les jars joueurs de la même fabrication.
- Le monde, les données des joueurs (clans, hérals, grades, quêtes…), `ops.json` et `server.properties` ne sont jamais
  touchés.

Secrets GitHub : les mêmes qu'avant (Settings → Secrets and variables → Actions), plus `TOPSERVEURS_TOKEN` (facultatif)
pour les votes du site (section 6).

## 4. Après l'envoi

1. Attendre que « Serveur HeraliZ » soit vert.
2. « Run workflow » → cocher **redemarrer** : le serveur redémarre avec les nouveaux mods.
3. Lire le résumé : la ligne « clé des jetons du launcher » doit être réglée. **Spartan refuse maintenant toutes les
   connexions si ce secret manque** (avant, il laissait tout le monde entrer sans rien dire).
4. Les joueurs reçoivent les nouveaux mods au prochain lancement du launcher. Monter la version du launcher et le
   publier (workflow « Launcher HeraliZ », case « publier ») pour qu'il retire lui-même les anciens gunpacks.

À faire une fois à la main sur le Ptero (pas fait par GitHub) :
- `server.properties` : `sync-chunk-writes=false`, et changer la description du serveur (`motd`).
- Réglages Java à demander à l'hébergeur (`-Xms` égal à `-Xmx`, voir `docs/DEPLOIEMENT.md`).
- En jeu : `/gamerule doMobSpawning false`, `/gamerule keepInventory false`, `/forceload query` dans chaque monde.

## 5. Les workflows gardés

| Workflow | Rôle |
|---|---|
| `serveur.yml` « Serveur HeraliZ » | à chaque envoi sur `main` : fabrication, contrôles, mise à jour du Ptero, pack du launcher ; « Run workflow » : redémarrage, installation complète, monde des bases à neuf |
| `branches.yml` | une autre branche : fabrication et contrôles seulement, rien n'est envoyé |
| `launcher.yml` | installateurs du launcher, « publier » |
| `site.yml` | site HeraliZ (Azuriom) |
| `mods_tiers.yml` | ajouter un mod d'un autre auteur depuis Modrinth |
| `enquete.yml` | enquête Spartan sur un joueur (lecture seule) |
| `journaux.yml` | lire les journaux du serveur (lecture seule) |

Retirés (outils de l'ancienne carte qui ne sont plus dans le projet) : props de la carte, inventaire de la carte, monde
du spawn, grotte, schematic, extractions, gunpacks, diagnostic CRac, test de charge. Ils restent dans l'historique Git.

## 6. Votes du site protégés contre les bots (27/10)

Chaque vote donne une clé, 10 hérals et 2 PB : une ferme de votes casse l'économie. L'extension HeraliZ du site
(`site/extension/heraliz/src/Votes/`) ne laisse l'extension Vote d'Azuriom donner la récompense que :
- à un **compte du site connecté** (plus de vote par simple pseudo : réglage remis à chaque envoi par `reglages.php`) ;
- dont le **Discord est lié, membre et validé**, comme pour jouer, et qui a **déjà joué 10 minutes** (temps compté sur
  les relevés d'AzLink, `TempsDeJeu` ; tant qu'AzLink n'a rien envoyé, cette règle ne s'applique pas) ;
- avec le jeton Top-Serveurs : quand **Top-Serveurs a vu le vote** depuis la même connexion internet (vérification
  stricte : une panne de Top-Serveurs ou un jeton refusé ne donnent rien, contrairement à l'extension Vote seule) ;
- sans jeton (cas actuel : identifiants Top-Serveurs perdus) : seulement après un **clic « Voter » sur notre page**,
  anti-robot **Turnstile** passé, depuis la même connexion, **20 s au moins** avant (`Votes\Clic`) ; un programme qui
  appelle directement l'adresse de la récompense n'obtient plus rien ;
- **une fois par connexion internet et par site** sur la durée du vote, tous comptes confondus (même bloc /64 en IPv6).

Une connexion qui vote pour 3 comptes ou plus en 24 h est signalée dans le salon privé des comptes (Discord). La page de
vote dit au joueur ce qui bloque AVANT qu'il clique (il ne gâche pas son vote sur Top-Serveurs).

Sans jeton, le site ne peut pas savoir si le joueur a vraiment voté sur Top-Serveurs : un joueur en règle qui clique
« Voter » puis ferme Top-Serveurs est quand même récompensé (comme avant). Les bots et les fermes de comptes, eux, sont
bloqués.

À régler par le propriétaire :
- **Turnstile** (gratuit) : compte Cloudflare → Turnstile → ajouter le site `heraliz.fr` (mode « Managed ») → copier la
  clé du site et la clé secrète dans l'administration du site → Paramètres → Sécurité → captcha « Turnstile ». Il
  protège aussi l'inscription sur le site ; la connexion du launcher n'est pas touchée.
- Facultatif, si le compte Top-Serveurs est un jour retrouvé : secret GitHub **`TOPSERVEURS_TOKEN`** = le jeton du
  serveur dans le panel Top-Serveurs. Il passe par `site.yml` → `theme.sh` (environnement) → `reglages.php`, jamais
  dans un fichier du dépôt ; chaque vote est alors vérifié auprès de Top-Serveurs.
