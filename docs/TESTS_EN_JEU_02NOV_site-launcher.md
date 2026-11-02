# Essais du chantier « site et launcher » (audit du 02/11, points 15, 41 et 37)

Rien n'a été essayé en vrai : ni le launcher ouvert (pas d'Electron ici), ni le site, ni le Ptero, ni les workflows sur
GitHub. Ce qui a tourné ici : les 108 tests du launcher (`npm test`) et sa relecture (`npm run lint`, ESLint 9), la
syntaxe de tous les fichiers PHP de l'extension et du site (`php -l`), la liste des commandes permises à la console
(essai à part, 25 cas), la configuration nginx produite par `installer.sh` (relue), l'outil des empreintes SSH, et la
lecture de tous les workflows modifiés (YAML valide).

## Ce qui a changé

| Point | Changement | Commit |
|---|---|---|
| 37, 41 | Launcher 1.16.0 : `PackXZExtract.jar` n'est plus livré ; restes de Helios retirés du dépôt (pages, scripts, polices, images, docs, ancien workflow) ; le dépôt privé n'est plus cité | 05444465 |
| 41 | Pack signé avec les empreintes **SHA-256** de chaque fichier et la **date** (`distribution.sig2`) ; le launcher 1.16.0 l'exige, refuse un pack plus ancien que le dernier accepté et contrôle le SHA-256 de chaque fichier avant chaque partie ; les launchers déjà installés gardent leur signature (`distribution.sig`) | d3b2c237 |
| 41 | Launcher : Electron 44.10.1 (faille corrigée), dépendances figées, ESLint 9 | 92ff2342 |
| 41 | Anti-robot (Turnstile du site) à la création de compte depuis le launcher ; plus de plafond « 60 comptes par heure pour tout le site » | 27738491 |
| 15 | Clé des PB : créations de PB plafonnées (20 000 par opération, 50 000 par 24 h), journal, alertes Discord, nouvelle adresse IP signalée, clé changeable (`nouvelle_cle_pb`) | 0334215a |
| 15 | Compte admin du site : seules quelques commandes peuvent partir vers la console ; 10 mots de passe faux = 15 min d'attente pour un compte admin ; le résumé du site dit si la double authentification est exigée | c9ce2709 |
| 15 | Secrets GitHub réservés à `main` (environnements `production` et `lecture`) | 1e6759f7 |
| 41 | VM du site et SFTP du Ptero reconnus à leur empreinte avant d'envoyer un mot de passe | 1e9924ca |
| 41 | AzLink : version et empreinte figées (`serveur/azlink.txt`) | d1dfca51 |
| 41 | Votes : Serveurs-Minecraft.org vérifié sans rien laisser passer dans le doute | 7c52902e |
| 41 | Adresses IP effacées au bout d'un an ; page Confidentialité à jour ; temps de jeu montré au staff | 0587f8a8 |
| 41 | Durcissements web (en-têtes partout, CSP en mode rapport, seul `index.php` exécuté) | 68b39023 |
| 41 | Code mort du site (3 vues, fond « carte tactique », étapes inutiles de `reglages.php`) | 18f7741b |

Ce qui change pour les joueurs : la première partie après la mise à jour du launcher prend 1 à 2 secondes de plus
(contrôle SHA-256 des fichiers) ; créer un compte dans le launcher ouvre une petite fenêtre « Vérification anti-robot »
(seulement si Turnstile est réglé sur le site) ; rien d'autre de visible.

## Ordre de mise en service (important)

1. Envoi sur `main` : « Serveur HeraliZ » refait le pack (avec `distribution.sig2`) et « Site HeraliZ » met
   l'extension 1.4.0 en ligne, tout seuls. Dans le résumé de « Serveur HeraliZ » : « signature v2 ✅ ».
2. **Seulement ensuite**, publier le launcher 1.16.0 (« Launcher HeraliZ » → Run workflow → case « publier »). La
   publication refuse de partir tant que le site ne sert pas la signature v2 (sinon le nouveau launcher refuserait le
   pack et personne ne pourrait jouer).
3. « Site HeraliZ » → Run workflow → `installer` (une fois, à un moment calme) : pose les durcissements nginx.

## À vérifier en jeu et sur le site

- [ ] **Launcher 1.15.3 déjà installé**, AVANT la publication du 1.16.0 : il lance toujours le jeu (pack signé v1).
- [ ] Publication du 1.16.0 : un launcher Windows se met à jour seul au démarrage ; Mac et .deb proposent le fichier.
- [ ] Launcher 1.16.0 : « Vérification des fichiers du jeu… » puis partie normale ; la deuxième partie aussi.
- [ ] Launcher 1.16.0 : remplacer un mod du dossier du launcher par un autre fichier → il est retiré et retéléchargé à
      la préparation suivante (message « Un fichier du jeu ne correspond pas au pack HeraliZ »).
- [ ] Connexion au compte depuis le launcher : inchangée (avec et sans double authentification).
- [ ] Création de compte depuis le launcher, Turnstile réglé sur le site : la fenêtre « Vérification anti-robot »
      s'ouvre après « CRÉER MON COMPTE », se ferme seule, le compte est créé et connecté. Fermer la fenêtre à la main :
      message « Vérification anti-robot annulée ». Formulaire faux : pas de fenêtre, l'erreur du champ s'affiche.
- [ ] Création de compte avec le launcher 1.15.3 (Turnstile réglé) : message « Mets ton launcher à jour… ou crée ton
      compte sur heraliz.fr ».
- [ ] PB : achat en jeu (`/boutique`), remboursement d'un achat raté, `/trade`, échange annulé, vente et achat en PB à
      l'hôtel des ventes : tout passe comme avant (crédits couverts par l'opération d'origine).
- [ ] PB : `pb donner <pseudo> 100` (console) passe ; `pb donner <pseudo> 25000` est refusé (« plafond ») et le salon
      privé Discord reçoit l'alerte ; un bon de PB donné en jeu passe.
- [ ] Salon privé Discord : une alerte « clé utilisée depuis une première adresse IP » arrive au premier achat après
      la mise à jour (adresse du serveur de jeu) ; ensuite plus rien tant que l'adresse ne change pas.
- [ ] Administration du site : enregistrer un article ou une récompense avec la commande `op {player}` → refusé avec
      un message ; les récompenses de vote actuelles (`heraliz givekey {player} vote 1`, `eco donner {player} 10`)
      partent toujours ; un ban Discord envoie toujours `banpc` au serveur.
- [ ] Résumé de « Site HeraliZ » : nombre de comptes admin, double authentification exigée ou non, aucun article ni
      récompense avec une commande refusée (sinon le résumé la nomme : me la donner pour l'ajouter à la liste).
- [ ] Vote sur Serveurs-Minecraft.org : récompense seulement une fois le vote vu par le site de vote.
- [ ] Après `installer` : le site, la boutique, les votes, la connexion, le launcher (pack et mises à jour) marchent ;
      « Site HeraliZ » → `verifier` dit « en-têtes … tous présents » et « Politique de contenu (CSP) : en mode
      rapport » ; dans les jours qui suivent, me donner les lignes « HeraliZ CSP » du journal du site pour la passer en
      vrai blocage.
- [ ] Page Confidentialité du site : nouveau texte (2 novembre 2026), si elle n'avait pas été modifiée dans
      l'administration (sinon le résumé de « Site HeraliZ » le dit, et le nouveau texte est dans
      `site/vm/pages_legales.php`).

## Ce qui n'est pas fait (et pourquoi)

- **Signature Windows du launcher** : demande un certificat de signature de code, payant (environ 200 à 400 € par an).
  Sans lui, SmartScreen avertit au premier lancement de l'installateur (la page Launcher du site l'explique).
- **Archive du launcher scellée** (`enableEmbeddedAsarIntegrityValidation`, audit §12) : pas fait. Si le réglage est
  faux, le launcher installé ne démarre plus du tout, avant même de pouvoir se réparer : à faire seulement avec un
  essai sur un vrai Windows et un vrai Mac. À me redemander si tu veux qu'on le prépare avec cet essai.
- **Empreinte d'AzLink** : la version est figée (1.3.13) mais son empreinte n'a pas pu être lue d'ici (GitHub d'Azuriom
  inaccessible) : le résumé de la prochaine mise en ligne la donne ; tant qu'elle n'est pas recopiée dans
  `serveur/azlink.txt`, l'AzLink déjà sur le Ptero est gardé (la boutique marche).
- Connexion à la VM et au Ptero par **clé SSH** au lieu du mot de passe : pas fait (la vérification de la machine
  règle le risque relevé par l'audit ; la clé SSH demanderait des réglages sur la VM et le panel).
- **Clé des jetons de lancement** (Spartan) : même risque que la clé des PB (en clair sur le Ptero) ; la changer
  bloquerait tous les joueurs jusqu'au redémarrage du serveur avec la nouvelle : à faire avec le chantier anticheat.
- Liens signés du pack (`secure_link_md5` de nginx) : restent en MD5, seule forme que nginx connaît ; c'est une
  signature avec clé secrète, pas une empreinte de fichier : sans risque ici.
- Le Gardien et l'anticheat : autre chantier.

## Questions pour le propriétaire

1. Plafonds des PB : 20 000 PB créés d'un coup au plus, 50 000 par 24 h, alerte à partir de 2 000 PB créés et de
   10 000 PB pour toute opération. Ça te va ?
2. Commandes permises depuis le site : clés de caisses, hérals, PB, grades, emotes (pour le joueur concerné). Une
   autre commande sert-elle dans un article ou une récompense de vote (par exemple pour un grade de LuckPerms) ?

## À faire par le propriétaire (dans GitHub, le panel et l'administration du site)

1. **GitHub → environnements** (secrets réservés à `main`) : `docs/MISE_EN_LIGNE_GITHUB.md`, section 7, pas à pas :
   - Settings → Environments → `production` → Deployment branches and tags → *Selected branches and tags* → `main` ;
     pas de « Required reviewers » ; y recréer les secrets listés (PTERO_*, SITE_*, LAUNCHER_SIGN_KEY, SPARTAN_*,
     LITEBANS_*, HERALIBOT_*, DISCORD_*, TOPSERVEURS_TOKEN…) ;
   - Settings → Environments → `lecture` → pas de restriction de branche ; secrets PTERO_SFTP_HOST, PTERO_SFTP_PORT et
     PTERO_SFTP_USER / PTERO_SFTP_PASSWORD d'un **sous-utilisateur du Ptero en lecture seule** ;
   - puis **supprimer** ces secrets des « Repository secrets » (garder seulement `NOMS_INTERDITS`).
2. **Panel du Ptero → Users** : créer le sous-utilisateur en lecture seule (cases « file.sftp », « file.read »,
   « file.read-content » seulement), pour l'environnement `lecture`.
3. **GitHub → variables des empreintes** : après une mise en ligne et un « Site HeraliZ » → `verifier`, recopier les
   lignes données par le résumé dans Settings → Secrets and variables → Actions → **Variables** :
   `SITE_SSH_EMPREINTE` et `PTERO_SFTP_EMPREINTE` (section 8 du même guide).
4. **AzLink** : recopier (ou me donner) l'empreinte affichée par le résumé de la mise en ligne dans
   `serveur/azlink.txt` (`sha256=`).
5. **Administration du site → double authentification** : chaque compte admin l'active dans son profil, puis
   Paramètres → Authentification → cocher « Exiger la double authentification » pour l'administration. Garder le moins
   de comptes admin possible.
6. **Administration du site → Paramètres → Sécurité → captcha « Turnstile »** (s'il n'est pas déjà réglé) : sans lui,
   ni la création de compte dans le launcher ni le bouton « Voter » n'ont d'anti-robot.
7. **« Site HeraliZ » → Run workflow → `installer`**, une fois, à un moment calme (durcissements nginx).
8. **Publier le launcher 1.16.0** seulement après la mise en ligne du serveur et du site (ordre ci-dessus).
9. En cas de doute sur la clé des PB (alerte « nouvelle adresse IP », accès au Ptero retiré à quelqu'un) : « Site
   HeraliZ » → `nouvelle_cle_pb`, puis « Serveur HeraliZ » → Run workflow avec « redemarrer ».
10. Facultatif : jeton Top-Serveurs (secret `TOPSERVEURS_TOKEN`), seul moyen de vérifier les votes Top-Serveurs.
