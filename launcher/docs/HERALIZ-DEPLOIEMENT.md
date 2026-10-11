# Préparer HeraliZ et ses futurs mods

L’adresse du serveur de jeu est `play.heraliz.fr` et l’invitation Discord `https://discord.gg/heraliz`. La version exacte de Forge 47.x et l’hébergement HTTPS du pack restent à renseigner. Le launcher ne les remplace pas par des valeurs fictives.

## Les adresses heraliz.fr

Dans la zone DNS de heraliz.fr (chez IONOS) :

- `heraliz.fr` mène au site (machine à part du serveur de jeu) ;
- `play.heraliz.fr` : enregistrement `A` vers l’IP du serveur de jeu. C’est l’adresse que tapent les joueurs et celle du launcher ;
- si le serveur de jeu ne répond pas sur le port 25565 : en plus, un enregistrement `SRV` `_minecraft._tcp.play.heraliz.fr` (priorité 0, poids 5, port du serveur, cible `play.heraliz.fr`) ;
- facultatif, pour que `heraliz.fr` tapé dans Minecraft mène aussi au jeu : un enregistrement `SRV` `_minecraft._tcp.heraliz.fr` (priorité 0, poids 5, port du serveur, cible `play.heraliz.fr`). Le site n’en est pas gêné.

Minecraft et le launcher (statut et ping de l’accueil) suivent les enregistrements `SRV`.

## Générer le pack

Le pack est refait à chaque mise en ligne du serveur (`.github/workflows/serveur.yml`, étape « Pack du launcher », voir `CLAUDE.md` section 8) par `outils/pack_launcher.py`, avec [Nebula, le générateur officiel de Helios](https://github.com/dscalzi/Nebula) (version figée dans le workflow), Java 17 et Forge 1.20.1-47.4.0 :

- mods : exactement `livraison/mods client` de la même fabrication que le Ptero (Spartan compare leurs empreintes avec `officiel.json`) ;
- fichiers : le dossier `serveur/tacz` (gunpacks : modèles, sons, icônes), posé dans le dossier du jeu ;
- Forge : l’installeur de Forge est lancé sans fenêtre avant Nebula, qui reprend son installation ; comme dans tout pack Helios, les bibliothèques et le client préparés par Forge sont servis par le site.

Le résultat (`distribution.json` et les seuls fichiers qu’il cite, contrôlés un par un) est déposé par rsync sur la VM du site, dans `/var/www/launcher`, servi à https://heraliz.fr/launcher/ (dossier de `distributionUrl`). Nouveaux fichiers d’abord, `distribution.json` ensuite, anciens fichiers retirés en dernier : un joueur qui lance le launcher pendant l’envoi ne tombe pas sur un fichier manquant. L’étape relit ensuite la distribution et quelques fichiers depuis internet.

Essai à la main, sans fabrication (mods tiers du dépôt seulement) : `python outils/pack_launcher.py <dossier de Nebula> <sortie> --essai` (Nebula construit, `npm ci` puis `npm run build`). Rien n’est déposé.

La distribution doit contenir un seul serveur `heraliz-1.20.1` avec l’adresse exacte de `serverAddress`, `minecraftVersion: "1.20.1"`, un seul module `ForgeHosted` du groupe `net.minecraftforge` en `1.20.1-47.…` (Nebula le nomme d’après sa première bibliothèque, `lowcodelanguage`) avec son manifeste de version. Chaque module fournit son empreinte MD5 de compatibilité Helios, sa taille et son URL HTTPS ; chemins absolus et traversées `..` sont refusés ; Java est fixé à 17 ; 30 000 fichiers et 32 Mo de distribution au plus. Les empreintes assurent la cohérence avec le manifeste ; elles ne remplacent pas une signature du manifeste. L’outil applique le contrôle du launcher lui-même (`app/assets/js/security.js`) avant tout dépôt.

## Ajouter les mods plus tard

Un mod joueur ajouté au serveur (dans `client/mods-tiers/`, `serveur/mods-tiers/` ou un de nos mods) arrive dans `livraison/mods client`, donc dans le pack à la mise en ligne suivante. Des configurations pour les joueurs iraient dans les `files` de Nebula (à ajouter dans `outils/pack_launcher.py`). Ne pas modifier les JAR après génération de leurs empreintes. Le launcher ne retire pas du dossier du jeu un fichier `tacz` qui a quitté le pack : retirer un gunpack chez les joueurs demande un nettoyage à part.

Les mods obligatoires restent obligatoires. Les mods optionnels suivent les valeurs par défaut du manifeste ; cette refonte n’inclut pas de sélecteur de mods optionnels. Les fichiers sont vérifiés à chaque lancement. Tout téléchargement est contrôlé avant traitement et lancement.

Les modules strictement serveur ne doivent jamais apparaître dans la distribution client. Ne pas publier les sources et JAR serveur privés dans ce dépôt GitHub public.

## Authentification

Une session par pseudo ne peut pas rejoindre un serveur exigeant une session Microsoft authentifiée. Un serveur acceptant des profils hors ligne doit disposer de sa propre authentification. Changer seulement `online-mode` ne protège ni les identités ni les doubles comptes. Vérifier également le chat signé avec la configuration Forge retenue.

Prévoir une inscription ou connexion côté serveur, des limites d’essais, des réservations de pseudos et une protection des comptes staff. Aucun identifiant fourni par le client n’est une preuve de confiance.

## Vérification avant publication

1. Tester avec un profil Windows vierge : pseudo, Java 17, installation Forge et connexion au bon serveur.
2. Corrompre un fichier du pack : le launcher doit réparer ou arrêter le lancement.
3. Tester une panne réseau, le redémarrage, la RAM, le plein écran et la fermeture du jeu.
4. Vérifier côté serveur l’usurpation de pseudo, les droits staff et les sanctions.
5. Signer l’installeur avec le certificat de l’éditeur avant diffusion publique. Aucun certificat n’est intégré au dépôt.

Les tests automatisés couvrent les profils, arguments Minecraft, erreurs, chemins, protections IPC et contrôles d’intégrité. Ils ne remplacent pas un essai sur le vrai serveur, impossible tant que ses paramètres sont absents.
