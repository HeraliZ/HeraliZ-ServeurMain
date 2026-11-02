# Essais en jeu du 02/11 : chantier « démarrage propre »

But : un démarrage du serveur sans rien de bizarre dans la console, et rien de chargé pour rien.

Rien de tout cela n'a été testé en jeu ni compilé avec Minecraft ici : Gradle ne trouve pas Forge sur la machine de
travail (maven.minecraftforge.net bloqué). Le code Java changé a été compilé avec javac contre des classes de
remplacement (sans Minecraft) et ses cas essayés hors du jeu ; la vraie compilation sera faite par le contrôle de branche
de GitHub. Les structures du ClashWar ont été relues et comparées bloc par bloc après l'écriture (deux lecteurs
différents) ; `outils/verifier_langues.py` : 0 erreur.

Jars à mettre à jour ensemble (serveur ET joueurs, même fabrication) : **ClashWar 1.1.2**, **Spartan 2.10.2** (le jar
serveur garde une copie du jar joueur pour le défi d'intégrité), **Noyau 1.0.14**. Réglage du dépôt changé :
`serveur/defaultconfigs/heraliz_noyau-server.toml` (`mondes_sans_spawn`, nouveau `retirer_troncons_forces`).

## Ce qu'on doit voir au prochain démarrage (console)

- [ ] Plus aucune ligne `[matchs] structure … bloc(s) inconnu(s) poses en air`.
- [ ] Plus de rapport de blocage de Spartan (`[diag] blocage de … ms : spartan …`, `Occluders.compute`).
- [ ] La ligne `[Spartan] Anti-ESP : 55346 états de bloc, 22009 cachent la vue (… ms, fil à part).` : **mêmes nombres
      qu'avant** (55 346 et 22 009), c'est la preuve que la table est la même.
- [ ] Au premier démarrage seulement : `[noyau] 13 tronçon(s) forcé(s) retiré(s) de minecraft:overworld
      ([monde] retirer_troncons_forces), tronçon x z (blocs x z) : …` (la liste dit où ils étaient). Aux démarrages
      suivants, cette ligne n'apparaît plus (il n'y a plus rien à retirer).
- [ ] `[noyau] zone de départ de heralisz:spawn jamais préparée (mondes_sans_spawn)`, puis
      `[noyau] zone de départ plus gardée chargée : crac:tuto, heraliz_clashwar:arenes, heralisz:spawn`. Mohist écrit
      encore « Preparing start region for dimension heralisz:spawn » (normal), mais plus « Preparing spawn area » pour lui.
- [ ] 30 s après le démarrage, les lignes des tickets :
  - `tickets de minecraft:overworld (…) : forced x0 start x1 (rayon 11)` (plus de `forced x13`) ; tronçons chargés de la
    carte serveur vide : environ 2 200 au lieu de 4 357 ;
  - `tickets de heralisz:spawn (…) : crac_arrivee_fixe x1 (rayon 2) forced x0 heraliz_arrivee_fixe x1 (rayon 2)` : plus
    de `start x1 (rayon 11)` ; tronçons chargés du spawn serveur vide : moins de 1 700 au lieu de 3 191 ;
  - chaque autre monde dit aussi `forced x0`.

## 1. Arènes du ClashWar (structures sans meubles absents)

Les fichiers `lobby_clashwar` et `clashwar_1v1` à `clashwar_4v4` citaient 97 états de meubles `cfm:…` absents de notre
jar de meubles (wires, scifi_desk, office_tv, police_iron_locker…) : 149 blocs. Le jeu les posait déjà en air ; ils sont
maintenant de l'air dans les fichiers. Rien d'autre n'a changé.

- [ ] **Une seule fois, au prochain démarrage** : `[matchs] arenes : … 5 obsoletes` (l'empreinte d'une arène suit son
      fichier) ; une nouvelle arène de chacune de ces 5 cartes est posée d'avance, en tâche de fond. Au démarrage suivant :
      `0 obsoletes`.
- [ ] `/clashwar` : salle d'attente et arènes 1v1 à 4v4 comme avant (les meubles absents manquaient déjà), aucun bloc
      violet ou noir, aucun trou.
- [ ] Une nouvelle structure faite en jeu : `python modules/clashwar/outils/nettoyer_structures.py` (sans option) dit
      ce qui serait changé, `--installer` nettoie ; `python outils/verifier.py clashwar` le signale.

## 2. Anti-ESP de Spartan (table calculée sur un fil à part)

- [ ] Juste après le démarrage, `/spartan esp` : « en préparation » pendant quelques secondes (personne n'est caché), puis
      « Anti-ESP actif : … ».
- [ ] À deux : un joueur derrière un mur reste caché, réapparaît dès qu'il est visible (comme avant) ; derrière une porte
      fermée : caché ; porte ouverte, vitre, feuilles : visible.
- [ ] Un changement de `opaqueBlocks` dans `spartan-common.toml` (relu tout seul) pendant que deux joueurs se cachent :
      une ou deux secondes où tout le monde est montré, puis tout redevient comme avant ; aucun gel du serveur.

## 3. Tronçons forcés retirés (carte)

- [ ] Les décors autour des coordonnées données par la ligne du journal sont intacts (rien n'est effacé : ces tronçons ne
      sont simplement plus gardés chargés quand personne n'y est).
- [ ] Serveur vide quelques minutes, puis `/diag etat` : la carte n'a plus de `forced`.

## 4. Monde du spawn (zone de départ plus gardée)

Ses deux points d'arrivée restent chargés (point de réveil de la cinématique, point `/spawn`) ; le reste du spawn se charge
quand un joueur y va, comme la carte.

- [ ] Spawn vide depuis quelques minutes, puis : première connexion (cinématique), mort puis « Au camp », extraction,
      `/spawn` : arrivée sans gel du serveur (`/diag` : pas de rapport de blocage), sol et décor là tout de suite.
- [ ] Toujours avec un spawn vide avant : aller vers les PNJ (Varga, marchands, Mila, recycleur), le casino (videurs,
      machines, distributeur de capsules) et la borne de départ : tout se charge en moins d'une seconde, rien en double
      (un seul Varga, deux videurs par entrée…), tout marche (quêtes, achats, recyclage, départ).
- [ ] Warps du menu et `/warp` depuis le spawn : comme avant.

## Ce qui n'a PAS été testé

Tout ce qui est en jeu (rien n'a été lancé : ni serveur, ni client). Seuls essais : outil des structures (relu et comparé),
classes Java changées compilées contre des classes de remplacement et leurs cas déroulés hors du jeu (table de l'anti-ESP :
démarrage, changement pendant un calcul, échecs ; tronçons forcés : retrait et liste).

## À savoir

- Secours touchés par le point 4 (rares) : si le point de réveil du spawn est inutilisable, le ClashWar renvoie au point
  de départ du monde du spawn, qui n'est plus chargé d'avance (court gel possible) ; `/wipe` ne retire les objets au sol du
  spawn que dans les tronçons chargés (comme partout ailleurs).
- Les anciennes arènes devenues obsolètes restent dans le monde des arènes (comme pour toute nouvelle version de carte).
