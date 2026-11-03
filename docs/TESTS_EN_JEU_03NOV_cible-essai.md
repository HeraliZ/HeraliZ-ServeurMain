# Essais en jeu — cible d'essai (03/11)

Équipement 1.1.0 (heraliz_armory), Tactique 2.43.2. Rien de cela n'a été testé en jeu ni compilé par Gradle ici (Forge
inaccessible depuis la machine de travail) : la fabrication complète est faite par le contrôle de branche de GitHub.
Jars joueurs ET serveur de la même fabrication (canal heraliz_armory:main en version 4).

## Ce qui a été fait

- **Cible d'essai** `heraliz_armory:cible_essai` (entité et objet, même identifiant) : le mannequin d'armurerie (modèle et
  texture existants, aucun dessin nouveau), à la taille d'un joueur. Elle ne bouge pas, ne recule pas, ne meurt jamais, ne
  perd aucun PV ; `/kill` la retire.
- **Même calcul qu'un joueur** : elle porte les vrais objets de la tenue (armure et Curios) ; ses coups passent par les
  mêmes réglages (armes.json : dégâts, tête, plafond des fusils à pompe ; difficulté « facile » du serveur pour les
  explosions ; armures de l'Armurerie et leur plafond de 60 %). Balles : même corps en morceaux qu'un joueur debout (tête =
  tête du modèle). Différences connues : pas de saignement, de poison ni de brûlure (effets refusés, jamais en feu) : les
  dégâts dans la durée des munitions incendiaires et du molotov ne sont pas comptés ; armure toujours neuve.
- **Choix de l'armure** : clic droit main vide (ou accroupi avec un objet) → écran : sans armure, 23 tenues du Commun à
  l'Unique, 5 combinaisons ; protection corps / tête affichée ; « Casque et masque de la tenue » à cocher ; « Ramasser »
  (celui qui l'a posée, le staff, le créatif).
- **Affichage au tireur seulement** : une ligne au-dessus de la barre à chaque coup, puis un récapitulatif dans le chat
  après 4 s sans coup.
- **Rien à farmer** : les dégâts sur la cible ne comptent nulle part (stats et quêtes de clan, minutes de jeu actif), aucune
  mort (saison, butin, kills). Munitions et usure de l'arme : comme tout tir (choix fait, voir questions).
- **Pose** : staff et créatif partout ; un joueur seulement dans la base de son clan (droit de construire de CRac), 2 par
  base au plus (`[cible]` de `config/heraliz_armory-common.toml`). Objet donné par `/give <joueur> heraliz_armory:cible_essai`.
- **Correctif trouvé en route** (Équipement, `TaczCompat`) : tir à la tête avec une arme qui perce l'armure (une centaine
  de fiches des gunpacks) : la part perce-armure était protégée par le gilet au lieu du casque. Vaut aussi pour les joueurs.

## À vérifier en jeu

1. **Mise en ligne** : `serveur/defaultconfigs/crac-server.toml` (`allowedItemsInClaims`) recopié dans
   `dayz_map/serverconfig/` ; section `[cible]` ajoutée à `config/heraliz_armory-common.toml`. Au démarrage, aucune erreur
   de mixin pour `heraliz_armory_serveur.mixins.json` (TaczHitboxCibleMixin).
2. **Pose par le staff au spawn** : `/give @s heraliz_armory:cible_essai`, clic droit sur le sol : la cible apparaît tournée
   vers soi. Elle ressemble au mannequin d'armurerie, à la taille d'un joueur, sur un socle.
3. **WorldGuard** : si au spawn (ou ailleurs) le clic droit n'ouvre rien ou si les tirs ne donnent aucun chiffre pour un
   joueur sans droits, c'est la région WorldGuard : `/rg flag <région du stand> interact allow` sur une petite région autour
   du stand de tir. (Le staff, qui construit partout, ne verra pas ce problème : essayer avec un compte joueur.)
4. **Écran** : clic droit main vide → l'écran s'ouvre, liste qui défile à la molette, la cible change de tenue derrière
   l'écran à chaque clic ; décocher « Casque » retire casque et masque (sauf combinaison). Avec une arme TaCZ en main, le clic
   droit vise sans ouvrir l'écran ; accroupi + clic droit ouvre l'écran. Textes en français, anglais, russe.
5. **Chiffres attendus** (AK-47 Rare, 6 de dégâts au corps, tête × 1,7, à courte distance) :
   sans armure : corps 6,0, tête 10,2 → « mort au tir n° 4 » au corps ; tenue Rare (Rempart, 32 % au corps, 26 % à la
   tête) : corps 4,1, tête 7,5 ; tenue Unique (Titan, 60 % / 50 %) : corps 2,4, tête 5,1. Comparer avec un vrai joueur dans
   la même tenue : mêmes dégâts (la ligne de la cible et la perte de PV du joueur).
6. **Tête / corps** : viser le haut de la tête et le haut du torse : la ligne dit « Tête » ou « Corps » comme on s'y attend
   sur un joueur. Tirer à côté du bras : rien.
7. **Fusil à pompe** (AA-12, Double canon…) : une ligne par tir avec « N plombs », le total d'un tir jamais au-dessus de 13
   (plafond, « plafond du tir atteint » quand il est atteint).
8. **Couteau, arme de mêlée, poing, grenade** : une ligne « Corps à corps » ou « Explosion » ; une grenade fait à la cible les mêmes
   dégâts qu'à un joueur à la même distance (difficulté « facile » comprise).
9. **Récapitulatif** : 4 s après le dernier coup, une ligne grise dans le chat (tirs, temps, totaux, « mort au tir n° N » ou
   tirs qu'il faudrait, arme, tenue). Les autres joueurs autour ne voient rien.
10. **Rien à farmer** : tirer longtemps sur la cible : les stats de dégâts du clan et les quêtes « infliger N dégâts »
    n'avancent pas ; la cible ne meurt jamais ; aucun tag de combat.
11. **Base d'un clan** : un joueur pose une cible dans sa base (permis), une 3e est refusée (« Cette base a déjà 2 cible(s)
    d'essai »), hors de sa base ou dans la base d'un autre clan c'est refusé. Il peut la ramasser (l'objet revient) ; un
    autre joueur ne peut pas (message).
12. **Redémarrage** : la cible garde sa tenue et son casque après un redémarrage du serveur.
13. **Tir à la tête avec une arme perce-armure** (correctif) : joueur avec seulement un gilet lourd, tir à la tête : les
    dégâts ne sont plus réduits par le gilet.

## Non testé

Tout ce qui précède (aucun test en jeu par Claude), la compilation Gradle et `python outils/verifier.py equipement armes`
(il lit les jars fabriqués). Fait ici : contrôle de syntaxe Java, `python outils/verifier_langues.py` (0 erreur).
