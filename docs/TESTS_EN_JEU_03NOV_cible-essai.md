# Essais en jeu — cible d'essai (03/11)

Équipement 1.1.1 (heraliz_armory), Tactique 2.43.2. Rien de cela n'a été testé en jeu ni compilé par Gradle ici (Forge
inaccessible depuis la machine de travail) : la fabrication complète est faite par le contrôle de branche de GitHub.
Jars joueurs ET serveur de la même fabrication (canal heraliz_armory:main en version 5).

## Ce qui a été fait

- **Cible d'essai** `heraliz_armory:cible_essai` (entité et objet, même identifiant) : le mannequin d'armurerie (modèle et
  texture existants, aucun dessin nouveau), à la taille d'un joueur. Elle ne bouge pas, ne recule pas, ne meurt jamais, ne
  perd aucun PV ; `/kill` la retire.
- **Même calcul qu'un joueur** : elle porte les vrais objets de la tenue (armure et Curios) ; ses coups passent par les
  mêmes réglages (armes.json : dégâts, tête, plafond des fusils à pompe ; difficulté « facile » du serveur pour les
  explosions ; armures de l'Armurerie et leur plafond de 60 %). Balles : même corps en morceaux qu'un joueur debout (tête =
  tête du modèle). Différences connues : pas de saignement, de poison ni de brûlure (effets refusés, jamais en feu) : les
  dégâts dans la durée des munitions incendiaires et du molotov ne sont pas comptés ; armure toujours neuve.
- **Choix de l'armure** (réponses du propriétaire du 03/11 : tenues complètes seulement) : clic droit main vide (ou
  accroupi avec un objet) → écran : sans armure, 23 tenues complètes du Commun à l'Unique (casque compris), 5 combinaisons ;
  protection corps / tête affichée ; « Ramasser » (staff, créatif). Plus de case « Casque ».
- **Affichage au tireur seulement** : le chiffre des dégâts reçus juste au-dessus de la cible (doré à la tête, blanc
  ailleurs), qui monte et s'efface en 1 s (plombs d'un même tir additionnés) ; une ligne au-dessus de la barre à chaque coup ;
  un récapitulatif dans le chat après 4 s sans coup.
- **Rien à farmer** : les dégâts sur la cible ne comptent nulle part (stats et quêtes de clan, minutes de jeu actif), aucune
  mort (saison, butin, kills). Munitions et usure de l'arme : comme tout tir (choix du propriétaire).
- **Pose** (choix du propriétaire) : seulement le staff et le créatif, où ils veulent (ex. stand de tir au spawn). Un joueur
  ne peut la poser nulle part, même dans sa base. Objet donné par `/give <joueur> heraliz_armory:cible_essai`.

## À vérifier en jeu

1. **Mise en ligne** : `serveur/defaultconfigs/crac-server.toml` (`allowedItemsInClaims` revenu à `[]`) recopié dans
   `dayz_map/serverconfig/` ; si la section `[cible]` a été mise dans `config/heraliz_armory-common.toml` sur le Ptero, elle
   peut être effacée (plus lue). Au démarrage, aucune erreur de mixin pour `heraliz_armory_serveur.mixins.json`.
2. **Pose par le staff au spawn** : `/give @s heraliz_armory:cible_essai`, clic droit sur le sol : la cible apparaît tournée
   vers soi. Elle ressemble au mannequin d'armurerie, à la taille d'un joueur, sur un socle.
3. **Pose refusée à un joueur** : donner l'objet à un compte joueur : clic droit sur le sol, partout (base comprise) :
   « Seul le staff peut poser une cible d'essai. », l'objet reste dans la main.
4. **WorldGuard** : si au spawn le clic droit d'un compte joueur n'ouvre rien ou si ses tirs ne donnent aucun chiffre, c'est
   la région WorldGuard : `/rg flag <région du stand> interact allow` sur une petite région autour du stand de tir. (Le
   staff, qui construit partout, ne verra pas ce problème : essayer avec un compte joueur.)
5. **Écran** : clic droit main vide → l'écran s'ouvre, liste qui défile à la molette, la cible change de tenue complète
   (casque compris) derrière l'écran à chaque clic ; pas de case « Casque ». Avec une arme TaCZ en main, le clic droit vise
   sans ouvrir l'écran ; accroupi + clic droit ouvre l'écran. « Ramasser » seulement pour le staff. Textes fr / en / ru.
6. **Chiffres au-dessus de la cible** : à chaque touche, un chiffre juste au-dessus de la tête de la cible, doré pour la
   tête, blanc sinon, qui monte et disparaît en 1 s ; un seul chiffre par tir de fusil à pompe (tous les plombs). Un autre
   joueur à côté ne voit rien. Sans tirer, rien n'est dessiné.
7. **Chiffres attendus** (AK-47 Rare, 6 de dégâts au corps, tête × 1,7, à courte distance) :
   sans armure : corps 6,0, tête 10,2 → « mort au tir n° 4 » au corps ; tenue Rare (Rempart, 32 % au corps, 26 % à la
   tête) : corps 4,1, tête 7,5 ; tenue Unique (Titan, 60 % / 50 %) : corps 2,4, tête 5,1. Comparer avec un vrai joueur dans
   la même tenue complète : mêmes dégâts.
8. **Tête / corps** : viser le haut de la tête et le haut du torse : « Tête » ou « Corps » comme sur un joueur. Tirer à
   côté du bras : rien.
9. **Fusil à pompe** (AA-12, Double canon…) : une ligne par tir avec « N plombs », le total d'un tir jamais au-dessus de 13
   (« plafond du tir atteint » quand il est atteint).
10. **Couteau, arme de mêlée, poing, grenade** : ligne « Corps à corps » ou « Explosion » ; une grenade fait à la cible les
    mêmes dégâts qu'à un joueur à la même distance.
11. **Récapitulatif** : 4 s après le dernier coup, une ligne grise dans le chat (tirs, temps, totaux, « mort au tir n° N »
    ou tirs qu'il faudrait, arme, tenue).
12. **Rien à farmer** : tirer longtemps sur la cible : les stats de dégâts du clan et les quêtes « infliger N dégâts »
    n'avancent pas ; la cible ne meurt jamais ; aucun tag de combat. L'arme s'use et les munitions partent, comme tout tir.
13. **Redémarrage** : la cible garde sa tenue après un redémarrage du serveur.

## Non testé

Tout ce qui précède (aucun test en jeu par Claude), la compilation Gradle et `python outils/verifier.py equipement armes`
(il lit les jars fabriqués). Fait ici : contrôle de syntaxe Java, `python outils/verifier_langues.py` (0 erreur).
