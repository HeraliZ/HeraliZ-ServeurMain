# Essais en jeu du 03/11 : chantier « monde-zones » (retours du propriétaire après son premier passage en jeu)

Rien de tout cela n'a été testé en jeu ni compilé ici : Gradle ne trouve pas Forge sur la machine de travail
(maven.minecraftforge.net bloqué). Seule la syntaxe Java a été contrôlée (javac sans Minecraft, erreurs des classes de
Minecraft et de Forge absentes écartées) ; les signatures de Minecraft et de Forge utilisées ont été relues dans leurs
sources 1.20.1 (Zombie, Mob, Entity, PlayLevelSoundEvent, patchs de Mohist pour l'envoi des sons). La vraie compilation
sera faite par le contrôle de branche de GitHub. `outils/verifier_langues.py` : 0 erreur (aucun texte nouveau).
`outils/verifier.py` ne peut pas tourner ici sans les jars fabriqués.

Jars à mettre à jour ensemble (serveur ET joueurs) : **Monde 1.0.12**, **Noyau 1.0.15**, **Bases (CRac) 1.30.2-beta**.
Réglage du dépôt changé : `serveur/defaultconfigs/heraliz_noyau-server.toml` (nouvelle section `[pas]`).

## 1. Zones plan en main et cercle d'extraction posés sur le vrai sol (point 1, défauts corrigés, même dessin)

Le nouveau rendu des zones (zone rouge visible à travers les arbres) n'est PAS branché : il attend le choix du
propriétaire entre les deux propositions (rendus : `rendus_03nov/monde-zones/`).

- [ ] Plan de construction en main, près d'une base dont le bord passe sous des arbres : le liseré et le voile restent
      au ras du sol sous les arbres (avant : ils montaient sur les feuilles et redescendaient). Pareil sur un bord qui
      passe sur une caisse, un conteneur de loot, un lampadaire, une barrière : le bord reste au sol.
- [ ] Zones interdites (près d'une ville, des rails, du nord enneigé), plan en main : la teinte rouge des cases et leur
      bord sont au sol, jamais en l'air sur la cime des arbres ou sur les props.
- [ ] Bord sur un chemin de terre, une dalle, de l'eau, de la glace, de la neige : le bord est posé dessus (pas dessous).
- [ ] Bord le long d'un mur de bâtiment ou sur un toit : il reste sur le toit ou le haut du mur (c'est du terrain).
- [ ] Images par seconde avec le plan en main au milieu d'une forêt (F3 ou `/diag client`) : pas de baisse par rapport
      au 02/11 (la hauteur du sol est gardée 10 ticks, jamais recalculée à chaque image).
- [ ] Fusée d'extraction posée au milieu de caisses, de barils, d'un lampadaire ou près d'un arbre : le cercle reste rond,
      sans encoche vers le centre devant chaque objet ; le mur ne saute plus d'un bloc sur une caisse.
- [ ] Fusée posée près d'un mur de bâtiment : le cercle s'arrête toujours au mur dans cette direction.
- [ ] Rien ne change pour l'extraction elle-même : on s'extrait aux mêmes endroits qu'avant (dans le cercle, pas sous un
      toit, pas sur une caisse hors d'atteinte), même chrono.
- [ ] Zone fixe d'admin (`/extraction create`) ouverte : même cercle propre.

## 2. Aucun zombie équipé (point 2)

- [ ] Parcourir une ville peuplée une bonne demi-heure (des centaines de zombies) : aucun zombie avec une épée, une pelle,
      une armure ou une citrouille.
- [ ] `/summon heralisz_utils:zombie_survie` une vingtaine de fois : aucun équipé, aucun poulet chevauché.
- [ ] Vagues d'un drapeau (capture d'une zone) : zombies sans équipement.
- [ ] Un zombie déjà sur la carte avant la mise à jour et qui avait une pelle ou une épée : au retour dans son coin (tronçon
      rechargé), il ne l'a plus.
- [ ] Les tenues ne changent pas : ville (5 tenues), hazmat, arctique, militaire, prisonnier, police (ce sont des textures).
- [ ] Le butin des zombies tombe comme avant (rien de plus, rien de moins).

## 3. Bruits de pas des autres joueurs et des zombies (point 3)

Réglages : section `[pas]` de `heraliz_noyau-server.toml` (relue seule quand le fichier change) : `portee_joueurs = 32`,
`volume_joueurs = 2.0`, `volume_monstres = 2.0`, `actif = true` (false : comme Minecraft).

- [ ] À deux joueurs : l'un marche, court, saute ; l'autre écoute à 4, 8, 16, 24, 30 et 35 blocs. Les pas s'entendent
      nettement plus près (environ deux fois plus forts), encore à 24-30 blocs, plus du tout au-delà de 32.
- [ ] La direction est juste (casque) : on sait de quel côté vient le joueur, de près comme de loin, même en tournant la
      tête pendant qu'il marche.
- [ ] Ses propres pas ne changent pas (même volume qu'avant, jamais en double, pas d'écho).
- [ ] Un joueur accroupi qui marche reste silencieux, comme avant.
- [ ] Staff en vanish (panneau du staff) qui marche à côté d'un joueur : le joueur n'entend rien.
- [ ] Pas sur différents sols (herbe, pierre, bois, gravier, métal, neige, laine) : chaque son est le bon.
- [ ] Zombies qui approchent : leurs pas s'entendent mieux de près (volume de Minecraft, deux fois plus qu'avant), toujours
      plus du tout au-delà de 11 blocs. Une horde n'est pas assourdissante (sinon baisser `volume_monstres` à 1,5).
- [ ] Changer `portee_joueurs` ou `volume_joueurs` dans le fichier du serveur : pris en compte sans redémarrer.
- [ ] Lenteurs : `/diag profil` pendant un combat à plusieurs : rien de nouveau dans le haut du profil.
