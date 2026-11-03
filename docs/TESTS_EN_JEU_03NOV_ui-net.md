# Essais en jeu du 03/11 — chantier ui-net (lettres nettes, écrans plus grands, roue des emotes)

Rien de ceci n'a été testé en jeu ni compilé par Gradle ici (dépôts de Forge injoignables) : le code a été compilé contre
les classes d'Emotecraft et des copies des signatures de Minecraft 1.20.1. La fabrication de la branche le confirmera.

## 1a. Lettres nettes (heraliz_interface 2.0.5)

Cause du flou : nos polices étaient tracées 8 fois plus fin que l'interface puis réduites par la carte graphique (4 fois à
l'échelle 2, 2,7 fois à l'échelle 3) : traits inégaux, petites capitales « grignotées ». Maintenant chaque lettre est
tracée à la finesse exacte de l'écran (1 point de lettre = 1 point d'écran). Minecraft comptait aussi deux fois la marge
gauche des lettres (« Quêtes dujour ») : corrigé.

- [ ] Au démarrage, le journal ne contient pas « heraliz_interface_net.mixins.json » en erreur, ni « Lettres nettes
      coupées ». (Si l'un des deux apparaît : le jeu tourne avec le rendu d'avant ; envoyer le journal.)
- [ ] Quêtes du jour, journal des quêtes, clan (K), hôtel des ventes, boutique : petits textes en capitales (ENVIRON
      30 MIN, PAR EXEMPLE, LES PLUS RARES), pourcentages, ligne du bas : nets, traits réguliers, plus « pixelisés ».
- [ ] Même chose avec l'échelle d'interface du jeu sur 2, 3, 4 et Auto (Options > Vidéo), écrans et HUD (munitions,
      notifications, chat).
- [ ] « Quêtes du jour » : l'espace entre « du » et « jour » est visible ; aucun texte ne déborde ni ne bouge par rapport
      à avant (les largeurs n'ont pas changé).
- [ ] Lettres russes (jeu en russe) : nettes aussi.
- [ ] Changer de langue, puis F3 + T (rechargement des ressources) : les textes restent justes, pas de lettres
      manquantes ou en carrés.
- [ ] Texte dans le monde (noms au-dessus des têtes, écran de la borne ATM) : comme avant.
- [ ] Fiche `/base info` : comme avant.
- [ ] Images par seconde : pas de baisse dans un écran plein de texte (quêtes, hôtel des ventes).

## 1b. Petits écrans plus grands (Noyau 1.0.15, Quêtes 1.17.2, Boutique 1.4.2)

Règle commune du kit : un écran « panneau » prend un ou deux demi-crans d'échelle de plus quand il tient dans 90 % de la
fenêtre. Branché sur : Quêtes du jour, Journal des quêtes, Marchand, Dialogue, Note, Clavier, Hôtel des ventes, Kits,
Warps.

- [ ] Fenêtre agrandie en 1080p (pas plein écran) : Quêtes du jour fait environ 1400 x 800 points (avant 1120 x 640),
      texte net.
- [ ] Plein écran 1080p : même taille qu'avant (échelle 3).
- [ ] Petite fenêtre 1366 x 768 (Auto) : rien ne sort de l'écran, même taille qu'avant.
- [ ] Dans ces écrans à l'échelle 2,5 : clics justes (boutons, cartes, onglets, défilement), icônes d'objets bien
      placées, infobulles lisibles.
- [ ] Passer d'un de ces écrans à un autre (journal → quêtes du jour, marchand → dialogue), puis fermer : le HUD revient à
      l'échelle choisie dans les options.
- [ ] Clans, table d'armurier, boutique, carte : inchangés.

## 2. Roue des emotes (Emotes 2.9.0) — NOUVEAU DESSIN, à valider sur les rendus avant intégration

- [ ] Y ouvre la nouvelle roue (verre sombre, DM Sans), avec les 8 emotes déjà rangées dans la roue d'Emotecraft.
- [ ] Survol à la souris (par l'angle), clic : l'emote se joue et la roue se ferme ; touches 1 à 8 : pareil.
- [ ] Molette et flèches ← → : pages 1 à 10 ; la page reste la même à la réouverture.
- [ ] Emote pas encore débloquée : grisée avec cadenas, clic → « À débloquer dans la caisse des emotes ».
- [ ] Refus (en combat, à terre, en mouvement…) : message rouge dans la roue, pas d'emote.
- [ ] « Toutes les emotes » ouvre le menu des emotes (touche 0) ; « Modifier la roue » ouvre le menu d'Emotecraft où
      l'on range les emotes ; Échap y revient à la roue.
- [ ] Textes en anglais et en russe : rien ne déborde.
