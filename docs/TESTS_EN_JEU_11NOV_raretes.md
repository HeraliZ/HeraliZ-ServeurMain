# Essais en jeu — couleur des raretés dans les noms (retours du 11/11 ; Loot 0.17.0, Tactique 2.54.3, Bunker 0.13.7, Noyau 1.0.18)

Rien n'a été essayé en jeu par Claude. Jars du serveur et des joueurs à mettre à jour (Loot, Tactique, Bunker, Noyau ;
le launcher s'en charge chez les joueurs).

Couleurs des raretés HeraliZ : Commun gris (nom blanc), Peu commun vert, Rare bleu, Épique violet, Légendaire or,
Unique rouge. La rareté de chaque objet vient d'un seul endroit : `serveur/config/heraliz_loot/raretes.json` (et les
choix du staff `/rarete`, rechargés par `/recharger raretes`) — c'est elle qui colore le fond des cases, la ligne de
rareté de l'infobulle et maintenant le NOM, partout pareil.

## 1. Le nom en main a toujours la couleur de la rareté (point 1)

Causes trouvées :
- le nom affiché au-dessus de la barre quand on change d'objet en main était colorié par Minecraft avec SA rareté, sans
  jamais lire la nôtre : blanc pour toutes les armes, lunettes, accessoires et munitions de TaCZ, couleurs de Minecraft
  pour les composants (rose pour les III) ;
- dans l'infobulle, la couleur n'était pas mise sur les noms qui contiennent des codes couleur : beaucoup de noms des
  packs d'armes en ont, sans rapport avec nos raretés (« Viseur LPVO 1-6x » violet foncé alors qu'il est Légendaire,
  « Viseur PK06 » bleu alors qu'il est Commun, « Balle de 9mm » verte…) ; et la couleur propre de certains objets passait
  devant (cartes d'accès, pièces d'armure…).

Maintenant une seule règle (Loot, `NomsRarete`) pour l'infobulle ET le nom en main : tout le nom dans la couleur de la
rareté de l'objet (gras et italique gardés). Les noms des armes, accessoires et munitions de TaCZ n'ont plus les couleurs
de leur pack dans aucun écran (fichiers des packs non touchés).

À vérifier (changer d'objet en main avec la molette, puis survoler l'objet dans l'inventaire) :
- [ ] une lunette / un viseur Légendaire (ex. Viseur LPVO 1-6x, Viseur Vudu) : nom or en main et dans l'infobulle ;
- [ ] un sniper (ex. AWP, Épique) : nom violet en main (avant : blanc) ;
- [ ] des composants I, II, III, IV : vert, bleu, violet, or, en main comme dans l'infobulle ;
- [ ] une munition Commun (ex. 9mm) : nom blanc (avant : vert) ; une munition Rare (ex. 5.56) : bleu ;
- [ ] un objet de loot, un sac, une pièce d'armure, un kit de réparation, des jumelles de frappe : nom de la couleur du
      fond de sa case ;
- [ ] un objet renommé à l'enclume : nom en italique, dans la couleur de sa rareté ;
- [ ] un bon du staff avec une couleur choisie : garde sa couleur ;
- [ ] `/rarete` sur un objet en main (staff) : la couleur de son nom suit tout de suite ;
- [ ] dans un écran (Mila, recycleur, atelier d'arme, caisses, réparation) : le nom d'une arme ou d'un accessoire TaCZ
      n'a plus de couleur du pack (il prend la couleur de l'écran).

## 2. Le violet des épiques, jamais le rose de Minecraft (point 2)

- composants III et tout objet Épique : violet HeraliZ en main, dans l'infobulle et dans les messages des caisses
  (« [Canon III] » dans le chat) ;
- objets qui n'ont pas de rareté dans raretes.json mais une rareté de Minecraft (module StatTrak, finishers, objets du
  staff…) : Épique de Minecraft lu comme notre Épique (violet, plus rose), Rare en bleu, Peu commun en vert ;
- camos et stickers (Tactique) : couleurs de nos raretés pour leur nom, leur ligne de rareté, la ligne « Camo : … » des
  armes et l'onglet Skin (avant : rose pour Épique, autres teintes de Minecraft pour le reste).

À vérifier :
- [ ] composant III en main et dans l'infobulle : violet, pas rose ;
- [ ] gagner un composant III dans une caisse : « [Canon III] » violet dans le chat (une arme ou un accessoire TaCZ y
      garde encore les couleurs de son pack : le serveur ne connaît que la clé de son nom ; son infobulle au survol est
      bonne) ;
- [ ] un camo Épique (ex. Galaxie) : nom et ligne « Épique » violets ; une arme qui le porte : « Camo : Galaxie » violet ;
- [ ] un sticker métallisé (Épique) et le module StatTrak : nom et ligne « Épique » violets ;
- [ ] un camo Commun : nom blanc, ligne « Commun » grise.

## 3. Nom des cartes d'accès complètes (point 3)

Les fragments étaient déjà bons (couleur de leur rareté). La carte complète gardait sa propre couleur (verte, bleue,
rouge) : elle prend maintenant celle de sa rareté, comme ses fragments.

À vérifier :
- [ ] carte verte (Rare) : nom bleu ; carte bleue (Épique) : nom violet ; carte rouge (Légendaire) : nom or ; en main
      et dans l'infobulle ;
- [ ] sa ligne « Ouvre les lecteurs niveau 1 (verte) » garde la couleur de la carte (c'est la couleur des lecteurs) ;
- [ ] fragments : inchangés.

## Changements visibles à connaître

- Couteaux skinnés (Unique) : nom entièrement rouge en main et dans l'infobulle (avant : couleurs de leur rang, avec le
  « | » gris), couleur de l'écran ailleurs (caisse Gemme…) ; le rang reste visible avec les losanges de l'infobulle.
- Capsules du casino : capsule de 1 nom blanc, de 5 vert, de 10 bleu (leurs raretés), au lieu de fer / vert / rouge.
- Noms Commun des packs d'armes (pistolets, munitions…) : blancs.

## Non vérifié par Claude

- Pas compilé ici (Minecraft et Forge ne se téléchargent pas dans cet environnement) : la fabrication complète est faite
  par le contrôle de branche de GitHub. Code relu ; la règle des couleurs a été essayée à part, sur une copie fidèle de la
  façon dont Minecraft découpe le texte.
- Nom en main : petit mixin du joueur sur le nom au-dessus de la barre (Loot, `heraliz_loot.mixins.json`, non
  obligatoire). Si la couleur en main ne change pas alors que l'infobulle est bonne, chercher `heraliz_loot.mixins.json`
  ou `NomEnMainMixin` dans le journal du jeu au lancement et me donner la ligne : rien ne plante dans ce cas.
- Noms de TaCZ sans les couleurs des packs : mixin du joueur sur les objets de TaCZ (jar de TaCZ non touché). Même
  vérification avec `NomsTaczMixin`.
