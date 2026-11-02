# Audit sécurité — 02/11 (site, launcher, anticheat)

Audit en lecture seule sur `main` (65e64fad). Rien n'a été modifié, fabriqué, testé en jeu, ni contacté (ni Ptero, ni site en ligne). Les points d'anticheat sont décrits de façon générale (le risque et la correction), sans mode d'emploi.

## Réponse courte

- **Site** : bien protégé contre les failles web classiques (textes échappés, CSRF, limites de connexion, IP non falsifiable, PB signés et sans double crédit). Je n'ai trouvé aucun moyen pour un joueur de se donner des PB, un grade ou des objets sans payer, ni de double livraison côté site. Les risques réels viennent des **accès privilégiés** (clé des PB sur le Ptero, comptes admin du site, secrets GitHub). Il y a aussi du code mort à retirer.
- **Launcher** : solide (fenêtre isolée, pack et mises à jour signés, Java et mods contrôlés). Mais, comme le dit déjà l'audit du 26/10 (A325), **il peut être imité** : le jeton du site prouve un compte, pas le launcher. Il reste beaucoup de fichiers de Helios inutiles dans le dépôt, et un jar inutile est livré aux joueurs.
- **Anticheat** : bien coupé (aucune détection dans le jar joueur). En revanche, le mode « jeu identique » se fie à ce que le jeu du joueur raconte. Un réglage le rend aveugle à une famille d'outils, il a quelques angles morts, et les triches de tir et de déplacement passent toujours côté serveur.

---

## Points (du plus grave au moins grave)

### 1. Un réglage laisse passer en silence une famille d'outils d'injection — **bloquant**
- Où : `serveur/config/spartan-common.toml:224` (`allowedJavaAgents`), valeur par défaut `modules/anticheat/src/serveur/java/com/Spartan/config/SpartanConfig.java:359`, utilisée par `integrity/ConformiteStricte.java:249-253`, `scan/ScanAnalyzer.java:225-233` et `serveur/ReceptionServeur.java:202-205`.
- Ce qui se passe : la config autorise par leur **nom** certains « agents Java » (programmes chargés au démarrage du jeu, qui peuvent le réécrire). Notre launcher n'en utilise **aucun**. Un jeu lancé à la main avec un agent qui porte un nom de cette liste n'est ni refusé ni signalé. Le fil Discord du joueur affiche même « Jeu validé ».
- Ce qu'un tricheur obtient : un jeu modifié qui passe le mode strict sans aucune alerte.
- Correction : vider la liste (`allowedJavaAgents = []`, et la même chose par défaut dans `SpartanConfig`). Tout agent est alors refusé en mode strict. **Petit effort.**

### 2. Le mode strict ne juge pas tout ce qui est chargé — **important**
- Où : `client/.../scan/collect/ForgeCollector.java:51-52, 169-172, 451` (fichiers non hachés au-delà d'une taille ou d'un budget : ils ne sont pas du tout envoyés), `integrity/ConformiteStricte.java:305-313` (seuls les jars du dossier mods et du dossier du launcher sont jugés ; les bibliothèques et les couches de lancement sont ignorées), `scan/ScanAnalyzer.java` (`files`/`modules` : ne jugent ces jars que si un « client de référence » a été appris), `scan/ScanManager.java:328-344` (un jeu qui cesse de répondre aux contrôles après le premier n'est signalé qu'une fois, jamais refusé).
- Ce qui se passe : un jeu lancé hors du launcher peut charger du code par des chemins que le mode strict ne compare pas au jeu officiel. Le Java du jeu n'est jugé que par le launcher officiel.
- Correction (**moyen**) : (a) tout fichier chargé non haché = écart en mode strict ; (b) ajouter à `officiel.json` les empreintes des bibliothèques Forge et Minecraft (déjà connues de `outils/pack_launcher.py`) et refuser tout jar inconnu, quelle que soit sa couche ; (c) en mode strict, refuser aussi un jeu qui cesse de répondre aux contrôles en cours de partie.
- Question : un client de référence (`/spartan reference learn`) a-t-il été appris sur le Ptero ?

### 3. Le launcher officiel peut être imité, et le défi ne prouve pas le code qui tourne — **important** (reste A325 et A328)
- Où : `site/extension/heraliz/src/Controllers/JetonController.php:26, 46-49` (jeton signé pour tout compte validé, avec l'empreinte fournie par le demandeur) ; `client/.../Client/Gardien.java:44-50` (la clé du Gardien est choisie par celui qui lance le jeu) ; `commun/.../integrity/Fingerprint.java:153-169, 264-276` et `client/.../scan/collect/SelfCheck.java` (le défi et l'auto-contrôle voient un Spartan modifié de façon grossière, pas une réécriture discrète) ; `scan/ScanManager.java:504` (le rapport n'est pas lié à la demande au-delà de son numéro).
- Ce qui se passe : avec un compte validé, un programme maison peut faire tout ce que fait le launcher. Un Spartan modifié avec soin peut répondre juste au défi et renvoyer un rapport propre. C'est une limite de fond : tout ce qui tourne chez le joueur peut mentir.
- Correction : accepter cette limite et ne pas compter sur le launcher. Les vraies défenses sont : le point 1, le point 2, un jar joueur de Spartan différent à chaque fabrication (cela casse les outils tout faits, audit 26/10 §8.3 point 6) et les détections serveur (point 7). Optionnel : lier le jeton à l'adresse IP vue par le site, contrôlée à la connexion. **Moyen à gros.**
- Question : faut-il garder le Gardien en « alerte seulement » (il n'arrête que les tricheurs peu doués) ?

### 4. La clé des PB, en clair sur le Ptero, permet de créer des PB sans limite — **important**
- Où : écrite par `.github/workflows/serveur.yml:280-284` dans `config/heraliz_grades/boutique_site.json` ; acceptée par `site/extension/heraliz/src/Controllers/PbController.php:148-172` (signature seule), `226-231` (jusqu'à 100 millions par appel), sans plafond, sans alerte, depuis n'importe quelle adresse IP. La clé des jetons de lancement est dans le même cas (`spartan-common.toml`).
- Ce qu'obtient quelqu'un qui lit les fichiers du serveur (sous-compte Ptero, sauvegarde, plugin fautif) : des PB, donc de la valeur réelle.
- Correction (**petit**) : n'accepter `/api/heraliz/pb/*` que depuis l'adresse du serveur de jeu (nginx), alerter sur Discord pour tout crédit au-delà d'un seuil ou d'un total par jour, et limiter l'accès aux fichiers du Ptero au propriétaire.
- Question : qui a accès aux fichiers du Ptero ?

### 5. Un compte admin du site = la console du serveur de jeu — **important**
- Où : les commandes des articles et des récompenses de vote sont jouées par la console via AzLink (`site/vm/reglages.php:322, 432` ; `Discord/Comptes.php:395-420`) ; `Middleware/LimiteConnexion.php:126-128` : la limite par compte ne vise que les codes A2F (les mots de passe ne sont limités que par adresse IP).
- Ce qui se passe : un admin du site piraté peut faire jouer n'importe quelle commande à la console (grades, PB, op…). Rien n'exige l'A2F pour les admins.
- Correction (**petit**) : A2F obligatoire pour tout compte qui a accès à l'administration (réglage d'Azuriom s'il existe, sinon un garde dans l'extension), le moins d'admins possible, et une limite d'erreurs de mot de passe par compte.

### 6. Les secrets GitHub sont lisibles depuis n'importe quelle branche — **important**
- Où : `.github/workflows/*.yml` : aucun `environment:` ; `LAUNCHER_SIGN_KEY`, `SITE_SSH_*` et `PTERO_*` sont des secrets du dépôt entier.
- Ce qui se passe : tout compte ou outil qui peut envoyer une branche peut y ajouter un workflow qui lit ces secrets. La clé de signature du launcher protège le PC de chaque joueur.
- Correction (**petit**) : un « environment » GitHub `production` réservé à `main`, avec validation du propriétaire, où l'on range ces secrets.

### 7. Triches de tir et de déplacement toujours sans parade côté serveur — **important** (reste A326, A327, A331)
- Où : `SpartanEvents.java:79` (seuls les coups de mêlée sont contrôlés, rien n'analyse les tirs TaCZ) ; `serveur/config/tacz-common.toml:54` (latence prise en compte jusqu'à 1 000 ms) ; `spartan-common.toml:28` (`movementSetback = false` depuis le 11/10), `:356` (marge de vitesse 0,06, soit environ +20 % non signalés).
- Correction : latence 350 ms et contrôle « faux lag » (petit) ; renvoi en arrière remis une fois les seuils réglés (petit) ; statistiques de tir collectées puis seuils (moyen à gros).
- Question : remet-on le renvoi en arrière du vol et de la vitesse ?

### 8. Mise en ligne : la machine distante n'est jamais vérifiée, connexion par mot de passe — **mineur**
- Où : `serveur.yml:188, 732`, `site.yml:403, 467, 496, 514` (`StrictHostKeyChecking=accept-new` sur une machine GitHub toujours neuve, `PubkeyAuthentication=no`) ; `serveur.yml:401` (`sftp:auto-confirm yes` pour le Ptero).
- Risque : si le nom ou le réseau est détourné, le mot de passe part chez un inconnu.
- Correction (**petit**) : empreinte de la machine dans un secret (`known_hosts`) et connexion par clé SSH.

### 9. AzLink pris « en dernière version » à chaque mise en ligne — **mineur**
- Où : `serveur.yml:372-375`.
- Correction (**petit**) : figer la version et vérifier l'empreinte (comme Nebula).

### 10. Création de compte depuis le launcher : sans anti-robot, plafond global — **mineur**
- Où : `Controllers/InscriptionController.php:29-33, 94`.
- Ce qui se passe : 60 comptes par heure en tout. Quelques connexions suffisent pour bloquer toutes les inscriptions par le launcher et remplir la base de faux comptes (le site, lui, a un captcha).
- Correction (**petit**) : renvoyer vers la page d'inscription du site (captcha), ou ajouter Turnstile ; retirer le plafond global.

### 11. Votes non vérifiables : récompense après un simple clic — **mineur**
- Où : `Middleware/VotesProteges.php:65-75`.
- Question : le secret `TOPSERVEURS_TOKEN` est-il réglé ? Sans lui, un compte validé reçoit la récompense sans vrai vote.

### 12. Launcher : archive non scellée, pas de signature Windows — **mineur** (A335 en partie corrigé)
- Où : `launcher/electron-builder.yml:14-19` (`enableEmbeddedAsarIntegrityValidation` absent) ; aucun certificat. La page Launcher du site apprend aux joueurs à passer outre l'alerte SmartScreen, ce qui aide un faux launcher à paraître normal.
- Correction : ajouter ce verrou (petit) ; certificat de signature de code (payant).

### 13. Pack signé : empreintes MD5, sans date — **mineur**
- Où : `launcher/app/assets/js/signature.js:49-67`.
- Correction (**moyen**) : ajouter un SHA-256 et un numéro de pack au texte signé.

### 14. Données personnelles — **mineur**
- Où : les tables d'IP (`heraliz_ips`, `heraliz_votes`) et `heraliz_temps_jeu` ne sont jamais purgées, alors que `site/vm/pages_legales.php:75` promet 1 an ; les IP partent chez Top-Serveurs et ipv6-adapter.com (`Votes/VerificationStricte.php:24, 107`), et Cloudflare (Turnstile) n'est pas cité dans la page (`:76`) ; le temps de jeu est collecté sans être lu (`Votes/TempsDeJeu.php:18, 46, 57` jamais appelés).
- Correction (**petit**) : purge dans la tâche `balayer`, mise à jour de la page, arrêt de la collecte du temps de jeu (ou affichage au staff).

### 15. Durcissements web — **mineur**
- Où : `site/vm/installer.sh:146-150` contre `182-201` (les `add_header` des blocs `/launcher/` et `/telecharger/` font perdre HSTS et nosniff) ; aucune CSP ; `location ~ \.php$` exécute tout `.php` du dossier public.
- Correction (**petit**) : répéter les en-têtes, `location = /index.php`, CSP en mode rapport d'abord.

### 16. Code mort du site — **mineur**
- `theme/heraliz/views/heraliz/carte.blade.php`, `telecharger.blade.php`, `boutique-infos.blade.php` : jamais inclus ; le dessin du fond (`assets/js/heraliz.js:1-145`) et le CSS `.carte-tac` ne servent plus.
- `site/vm/reglages.php` : `CAISSE_COSMETIQUE` (l.41) inutilisé ; `boutiquePB` (l.291-330) crée puis `pbMonnaieDuSite` (l.382) supprime des articles, `PB_EN_VENTE = false` ; mauvais commentaire l.291 ; ancienne monnaie « crédits ».
- Effort : **petit**.

### 17. Restes de Helios dans le launcher — **mineur**
- Non livrés mais dans le dépôt (environ 13 Mo) : `app/*.ejs`, `app/assets/js/scripts/`, `authmanager.js`, `discordwrapper.js`, `distromanager.js`, `dropinmodutil.js`, `isdev.js`, `langloader.js`, `preloader.js`, `serverstatus.js`, `ipcconstants.js`, `app/assets/lang/`, `launcher.css`, polices Avenir, ringbearer et Oswald, images d'origine, `docs/MicrosoftAuth.md`, `docs/distro.md`, `docs/sample_distribution.json`.
- **Livrés aux joueurs** sans servir : `libraries/java/PackXZExtract.jar` (`electron-builder.yml:36-37`). `package.json:10-11, 43` met le nom du dépôt privé dans le launcher.
- Garder la licence et les crédits de Helios. Effort : **petit**.

### 18. Docs de l'anticheat périmées — **mineur**
- `modules/anticheat/docs/LAUNCHER.md:22` et `docs/launcher/spartan-launcher.js` décrivent l'ancien « mode A » (secret dans le launcher) et le passage du jeton en option Java. Le jeu lit encore cette option (`ClientEvents.java:100-106`).
- Correction (**petit**) : réécrire la doc, retirer cette lecture.

### 19. Dépendances du launcher — **mineur / info**
- Rien de connu comme vulnérable dans ce qui est livré (lecture de `package-lock.json`). `got` 11 est une vieille version (corrigée) ; `eslint` 8 n'est plus suivi (outil de développement seulement).

---

## Audit du 26/10 : ce qui a été corrigé, ce qui reste

| Point | État |
|---|---|
| A324 secrets de Spartan effacés | **corrigé** (refus si la clé manque, mise en ligne bloquée sans clé) |
| A225 limite Discord | **corrigé** (file d'envoi, sanctions d'abord) |
| A226 joueurs signalés sans `officiel.json` | sans objet (le fichier est toujours déposé) |
| A230 `/logout` d'AuthMe | en partie (le joueur est de nouveau retenu) |
| A325 jeton = compte, pas launcher | **reste** (point 3) |
| A326 tirs TaCZ, A327 faux lag | **reste** (point 7) |
| A328 défi calculable | en partie (forme des classes ajoutée) ; **reste** (point 3) |
| A329 listes de recherche envoyées au jeu | **reste** (`ScanManager.send`) |
| A330 DLL ignorées sur leur nom | **reste** (`InjectionCollector.java:45-48`) |
| A331 vitesse, pas de renvoi en arrière | **reste** (point 7) |
| A332 anti-ESP limité aux joueurs | **reste** (pas retouché, sauf perf) |
| A333 ban PC sur des identifiants du jeu | **reste** |
| A334 capture prévisible, image non liée | **reste** |
| A335 archive du launcher, Gardien | en partie (verrous posés, Gardien rallumé) ; **reste** (point 12) |

## Pas vérifié / pas testé
Rien n'a été testé en jeu ni en ligne. Pas vérifié : la version d'Azuriom et de ses extensions sur la VM, la présence d'un client de référence sur le Ptero, les réglages de l'administration (A2F, captcha, jeton Top-Serveurs), qui a accès au Ptero et au dépôt.

## Résumé
1. Site propre côté web ; aucun PB, grade ou objet gratuit trouvé, pas de double livraison ; risques = accès privilégiés (clé des PB, admins, secrets GitHub).
2. Anticheat : un réglage (point 1) laisse passer en silence une famille d'outils d'injection ; à corriger tout de suite (petit).
3. Le mode strict a des angles morts (point 2) et le launcher reste imitable (A325) : priorité aux détections serveur (point 7).
4. Durcir la mise en ligne : secrets réservés à `main`, empreinte SSH, AzLink figé.
5. Nettoyage : code mort du site, restes de Helios (dont un jar livré pour rien), docs de l'anticheat périmées.
