# Essais en jeu du 02/11 : chantier « armes et joueur » (rien testé par Claude)

Corrections de l'audit du 02/11 (`docs/AUDIT_02NOV.md`, détail dans `docs/audit_02nov/mods-1.md`).

Jars touchés (joueurs ET serveur, même fabrication des deux côtés) : HeraliZ Tactique 2.43.0 (`armes`), Équipement
1.0.2, Réanimation 1.16.8 (`survie`), CaraCrea 1.6.5 (`voix`), Emotes 2.8.8, Interface 2.0.3 (joueurs seulement).
Réglages changés : `serveur/config/backpacked.server.toml`, `serveur/config/heraliz_armory-common.toml`,
`serveur/config/heraliz_emote-serveur.toml` (nouveau), `serveur/defaultconfigs/heralisz_tactical-server.toml`,
`serveur/defaultconfigs/heraliz_noyau-server.toml` : mise en ligne complète puis redémarrage.

**Rien n'a été compilé avec Gradle** (le dépôt de Forge est bloqué ici) : seulement une vérification partielle de nos
fichiers avec `javac` (syntaxe et nos propres classes), et les 64 programmes de skins compilés et liés avec un vrai
OpenGL (`verif_glsl.py`, 64 / 64). Le contrôle de branche de GitHub fera la vraie fabrication : à regarder en premier.

## 1. Armes de mêlée à vie : plus d'usure (point 7)
- Frapper un zombie et un joueur avec une batte, une machette… : la barre d'usure ne bouge plus et n'est plus affichée
  (même sur une arme déjà usée).
- Une arme presque cassée avant la mise à jour ne casse plus.

## 2. Objets à vie jamais perdus (point 8) — le plus important
Avec un couteau à vie ou un skin à vie (Séraphin, Chaînes de l'Enfer), en survie (pas en créatif) :
- **Déconnexion avec l'objet au curseur**, inventaire de base ouvert (touche E) : quitter (Alt+F4 ou « Se déconnecter »).
  Revenir : l'objet est dans l'inventaire. Pareil avec l'objet posé dans la grille 2x2.
- **Pareil avec l'écran de Curios ouvert** (objet au curseur, puis objet dans sa grille de fabrication).
- **Pareil avec un coffre, un sac à dos ou le recycleur ouvert** et l'objet au curseur.
- **« Jeter cet objet ? » à l'écran** puis déconnexion sans répondre : l'objet revient à la reconnexion.
- **Mort** avec un couteau à vie rangé DANS le sac porté (réponse « Oui, le ranger » à « Mettre dans le sac ? ») : à la
  réapparition le couteau est rendu ; le sac sur le corps ne le contient plus. Pareil avec un sac rangé dans l'inventaire.
- **Mort avec un établi ouvert** et le couteau dans sa grille : rendu à la réapparition.
- **Inventaire plein** : remplir l'inventaire, mourir avec un couteau à vie, réapparaître avec un inventaire plein (kit) :
  message « Inventaire plein : ton objet à vie est gardé… », puis vider une case : le couteau revient dans la seconde.
- **Au sol** : jeter un couteau à vie (« Oui, jeter »), ne pas le ramasser : il ne brûle pas dans la lave ni le feu ; au
  bout de 5 min il revient dans l'inventaire de celui qui l'a jeté (message « Objet à vie rendu »). Déconnecté entre-temps :
  rendu à la connexion suivante.
- **Fouille d'un joueur à terre** : la victime a un couteau à vie dans son sac porté ; le fouilleur ouvre la fouille :
  le couteau est dans l'inventaire de la victime (pas prenable), le sac ne le contient plus.
- **Plantage du serveur** (essai sur le serveur local) : objet en attente de « Jeter ? », sauvegarde, arrêt brutal :
  au retour, l'objet est là une seule fois (ni perdu, ni en double).
- Journal du serveur : lignes `[a vie]` (gardé, rendu, boîte aux lettres) ; dossier `heraliz_tactique/objets_a_vie/`
  (vide la plupart du temps).

## 3. Tirs à la tête et coups de mêlée (point 9)
Un mixin coupe les recalculs de dégâts de l'ancien code d'ArkaniaZ dans geckolib222 (jar intact).
| Fusil à pompe (tête × 1,25), un plomb | Avant | Maintenant |
|---|---|---|
| Au corps | 1 × | 1 × |
| À la tête | 0,78 × (moins que le corps) | 1,25 × |
- Tir à la tête puis tir au corps sur un joueur sans armure (même arme) : la tête fait plus.
- Un coup de batte juste après un tir à la tête sur un autre joueur : dégâts normaux.
- La console du serveur n'affiche plus deux nombres à chaque coup reçu par un joueur.
- Le saignement après un tir arrive toujours (chance gardée).
- **Au démarrage du serveur et du jeu** : aucune erreur « Mixin » sur `GeckolibDegatsMixin` (sinon le jeu ne démarre pas).

## 4. Écran Informations (point 26)
- Avec un ancien `config/heraliz_interface/informations.json` sur le PC (copie d'avant le 26/10 : `/duel`, `/zombies`) :
  ouvrir Échap > Informations : le texte actuel s'affiche ; l'ancien fichier est gardé en `informations.json.ancien`.

## 5. Gilet et plomb à la tête (point 27)
- Joueur avec casque faible et bon gilet : une décharge de fusil à pompe dont un seul plomb touche la tête ; les autres
  plombs comptent le gilet (avant : tous ignoraient le gilet).

## 6. Reconnexion automatique (point 28)
- `/redemarrage` : l'écran d'attente revient, le jeu se reconnecte seul quand le serveur est revenu (comme avant).
- Pendant les 10 min : serveur plein, joueur banni, ou ancien jeu après une mise à jour du code : le vrai message de
  refus s'affiche, plus « Reconnexion automatique dans … s ».

## 7. Voix (point 29)
- Créer, rejoindre, quitter des groupes vocaux, couper son micro : la liste se met à jour chez les autres (au plus 0,5 s).

## 8. Fichiers des camos, emotes et sourdines (point 30)
Sur le serveur local seulement : abîmer `heralisz_camo/collections.json` (ou `heraliz_emote/debloquees.json`,
`<monde>/caracrea/voix_sourdines.json`), démarrer :
- erreur dans le journal, message rouge à un op qui se connecte ;
- le fichier abîmé n'est jamais réécrit ; une copie `.illisible-<date>` est faite ; un camo ajouté, une emote débloquée,
  une sourdine posée vont dans `.secours-<date>` ;
- aucun camo à vie n'est retiré des armes, ni pendant la session, ni au redémarrage suivant.

## 9. Soins pendant leur utilisation (point 31)
- Se soigner, puis changer de soin dans la même case avant la fin (glisser un autre soin) : le soin s'arrête.
- Un soin normal (sans rien toucher) va jusqu'au bout comme avant ; une grenade se lance comme avant.

## 10. Le reste
- **Dégâts des armes** : changer un chiffre de `config/heraliz_tactique/armes.json`, taper `/armurier recharger` (ou
  `/recharger`) : « Dégâts des armes relus », chiffres de l'infobulle à jour sans redémarrer. Fichier cassé : les anciens
  réglages restent.
- **Stickers** : une arme avec 5 stickers et un StatTrak, en 1re et 3e personne, pendant un rechargement (culasse,
  chargeur) : les stickers épousent l'arme comme avant, aucun décalage ni clignotement. Plusieurs joueurs proches avec
  des armes à stickers : images par seconde meilleures qu'avant.
- **Skins** (code commun des 64 programmes inclus une seule fois) : tous les skins s'affichent comme avant (essayer Gold,
  Dark Matter, Séraphin, un couteau Doppler). Si un skin ne s'affiche plus, regarder le journal du jeu (« camo_ »).
  Le jar joueur de Tactique fait ~13 Mo de moins.
- **Pack-a-Punch** : les camos `pap_1` à `pap_3` n'existent plus (objets, programmes, textes).
- **Bot d'essai de la réanimation** : plus dans l'onglet créatif des œufs ; l'aperçu des exécutions a toujours ses
  personnages (Steve, Kai).
- **Mannequins** et jumelles de vision nocturne : comme avant (outil de tournage retiré).
- **Sacs** : un sac qui contient quelque chose ne se retire plus du dos (infobulle rouge « Vide le sac… ») ; vide, il
  s'enlève ; un sac plein sur un corps se ramasse toujours.
- **Réglages** (mêmes valeurs qu'avant) : plafond des armures 60 % (`heraliz_armory-common.toml`), attente des camos à vie
  15 min et angle de mêlée 100° (`heralisz_tactical-server.toml`), 10 s sans arme après une emote
  (`heraliz_emote-serveur.toml`, nouveau, serveur seulement) : changer une valeur, vérifier qu'elle est prise.
- **Emote** puis arme en main : 10 s d'attente comme avant ; réglé à 5 s, le jeu laisse tirer au bout de 5 s.

## Pas fait dans ce chantier (dit au coordinateur)
- Modules d'autres chantiers qui doivent appeler `ObjetsAVie.neJamaisDetruire(UUID, ItemStack, String)` : `loot`
  (conteneurs nettoyés), `pnj` (recycleur du spawn et de la carte), `boutique` (caisses, achats, casino quand l'inventaire
  est plein), `monde` (atelier de réparation à la déconnexion, corps qui disparaissent).
- `noyau` : unités « min / s » de `/redemarrage` en français pour tous, annonce « ton jeu se reconnectera tout seul »
  (ajouter « si une mise à jour est sortie, relance le launcher »), `armurier recharger` à ajouter aussi à la liste par
  défaut de `NoyauConfig`.

## Questions pour le propriétaire
- Sacs : un sac plein ne se retire plus du dos (réglage d'origine de Backpacked, remis comme avant en un mot). D'accord ?
- Un objet à vie jeté au sol et jamais ramassé revient à celui qui l'a jeté au bout de 5 min. D'accord ?
- Un membre du staff gardait-il un œuf du bot d'essai, ou une arme avec un camo Pack-a-Punch ? (ils disparaissent /
  s'affichent sans camo)
