# Essais en jeu — infobulles (retours du propriétaire du 14/11, points 1 à 3)

Loot 0.18.0, Équipement 1.3.0, Tactique 2.55.1, Monde 1.2.1, Boutique 1.5.7. Rien n'a été essayé en jeu par Claude, et
rien n'a été compilé ici (Minecraft ne se télécharge pas dans cet environnement) : la fabrication complète est faite par
le contrôle de branche de GitHub. Joueurs et serveur : nouveaux jars (le launcher met à jour ceux des joueurs).

Une seule source pour la rareté d'un objet : `serveur/config/heraliz_loot/raretes.json` (et les choix du staff `/rarete`,
relus par `/recharger raretes`). Liste complète des armes, accessoires, munitions, armures et composants avec leur
rareté : `python outils/raretes_objets.py --liste` (706 objets, aucun sans rareté).

## 1. Toutes les armes, accessoires, munitions, armures et composants montrent leur rareté

Cause : ces objets avaient tous une rareté dans `raretes.json`, mais l'infobulle ne rappelait jamais la rareté
**Commun** (le Bipod V9 Grip est Commun). Maintenant, tout objet qui a une rareté choisie la montre, Commun compris ;
un objet qui n'a que la rareté par défaut (blocs, objets de Minecraft…) n'en montre toujours pas.

| Objets qui montrent maintenant leur rareté | Nombre | Rareté | D'où elle vient |
|---|---|---|---|
| Accessoires de base : 24 viseurs point rouge (Aimpoint ACRO, DeltaPoint, RMR, PK06…), 12 poignées et bipieds (Bipod V9 Grip, X48 Bipod, CQR Grip…), 6 bouches (baïonnettes, freins), 3 lasers, 2 crosses | 47 | Commun | `outils/equilibrage_armes.py` (force de leurs bonus) ; la même à la Table d'armurier et dans le loot |
| Pistolets de base : Glock 17, M1911, P250, USP, M17 | 5 | Commun | `outils/equilibrage_armes.py` (pistolets dès Commun) |
| Munitions 9 mm et .45 ACP | 2 | Commun | `outils/equilibrage_armes.py` (rareté selon le calibre) |
| Armes de mêlée : machette, batte, tuyau de plomb, masse, matraque télescopique, hache | 6 | Commun | `outils/loot_armes.py` |
| Composants : corde, clou, plastique, planche, collier, téléphone, montre, lunettes cassés, ampoule grillée | 9 | Commun | `modules/equipement/outils/build.py` |
| Pièces Commun (Citadin, Rescapé, Ranger, Woodland), leurs tenues complètes, brassards | 35 | Commun | `modules/equipement/outils/equilibrage_armures.py` (avant : seulement la ligne de l'Armurerie) |
| Boîtes de munitions fer / or / diamant | 3 | Rare / Épique / Légendaire | décidée le 24/10 ; avant, une boîte remplie prenait la rareté de ses balles et une boîte vide n'en avait aucune |

Restent sans rareté, voulu : la munition factice des couteaux (`arkaniaz:melee_weapon`, jamais obtenable) et les 17
objets d'événement de l'Armurerie (citrouille, crâne, fantôme, momie, Frankenstein, chapeaux, pattes d'araignée, sacs sur
la tête : aucun n'est donné aujourd'hui ; ils gardent leur ligne rose « Événement »).

Boîtes de munitions : leur propre rareté partout (fond de la case, couleur du nom, badge, `/rarete` sur une boîte en main,
plafond des prix en PB à l'hôtel des ventes : 200 / 500 / 1 200 PB au lieu de celui de leurs balles).

À vérifier (survoler l'objet dans l'inventaire) :
- [ ] Bipod V9 Grip, un viseur point rouge, un Glock 17, des balles de 9 mm, une machette, une corde : badge **COMMON**
      juste sous le nom ;
- [ ] une pièce Citadin, un brassard : badge COMMON (une seule fois) ;
- [ ] boîte de munitions en diamant vide puis remplie de 9 mm : badge **LEGENDARY** les deux fois, fond doré ;
- [ ] un bloc de construction ou un objet de Minecraft : pas de badge ;
- [ ] `/rarete` (staff) sur un objet en main : le badge suit tout de suite ; `/recharger raretes` après une rareté changée
      dans `raretes.json` : pareil.

## 2. Plus aucun malus de vitesse sur l'équipement

Le seul effet négatif de l'équipement était un malus de vitesse (« -1 % Rapidité » sous « Sur la tête : »). Il est
retiré du catalogue (par l'outil `modules/equipement/outils/equilibrage_armures.py`), du calcul réel (aucun attribut de
vitesse négatif n'est plus appliqué, même avec un catalogue faux) et de l'infobulle. Ghillies, masques à gaz, brassards
et sacs n'en avaient pas. Les effets positifs restent (protection, palmes +80 % de nage, bouteille, vision nocturne,
froid, gaz, radiation).

| Objets (39) | Avant | Après |
|---|---|---|
| Gilets Commun et Peu commun (Ranger, Woodland, Nomade, Gardien, Cobra) | -1 % | 0 % |
| Gilet Sentinelle (Peu commun) et gilets Rare (Rempart, Vipère, Sibérie, Scorpion) | -2 % | 0 % |
| Gilets Épiques (Contrat, Bélier, Fennec, Blizzard) et Légendaire Barricade | -4 % | 0 % |
| Gilets Légendaires Béton, Meute, Corbeau | -7 % | 0 % |
| Gilets Ours (Légendaire), Mastodonte et Titan (Unique) | -10 % | 0 % |
| Casques Épiques et Légendaires (Contrat, Bélier, Fennec, Blizzard, Barricade, Béton, Meute, Corbeau), vision nocturne Hulotte et Effraie | -1 % | 0 % |
| Casques Ours, Mastodonte, Titan, vision nocturne Grand-duc | -2 % | 0 % |
| Pantalons Ours, Mastodonte, Titan | -1 % | 0 % |
| Tenue de plongée, à terre | -6 % | 0 % (palmes : toujours +80 % de nage) |

Un objet déjà dans un inventaire perd aussi son malus : rien n'est gardé sur l'objet ni avec le joueur ; le jeu et
Curios redemandent les attributs à l'objet quand on l'enfile et à chaque connexion.

À vérifier :
- [ ] infobulle d'un gilet Mastodonte, d'un casque Bélier : plus de « Sur la tête : / -1 % Rapidité » (ni « Porté en
      gilet »), le reste de l'infobulle ne change pas ;
- [ ] tenue de plongée : « Palmes : nage +80 % » (plus de « à terre : -6 % de vitesse ») ; on marche à terre à la même
      vitesse qu'en t-shirt, on nage toujours plus vite ;
- [ ] avec une tenue Titan complète portée AVANT la mise à jour : après la reconnexion, on court à la vitesse normale
      (même temps pour une même distance avec et sans la tenue, et avec un gilet lourd rangé puis remis).

## 3. La rareté une seule fois : le petit badge « EPIC »

D'où venaient les répétitions sur le casque Épique (« Épique », puis « [EPIC] Épique ») : la ligne « Épique » de Loot,
puis la ligne de l'Armurerie, badge + mot. Aucun nom d'objet ne contient le mot de sa rareté (fichiers de langue
français, anglais et russe de nos mods et des gunpacks, contrôlé maintenant par `outils/verifier.py`). Les camos,
stickers, le module StatTrak et le Bonus de loot avaient eux aussi le mot « Épique ».

Maintenant, une seule indication de rareté : le badge, juste sous le nom (COMMON, UNCOMMON, RARE, EPIC, LEGENDARY ;
**ARTIFACT** rouge pour Unique : la police n'a pas d'image « UNIQUE »). Ces badges viennent de la police des rangs du jar
des meubles (cfm), que les joueurs ont : ce sont ceux que montrait déjà l'Armurerie.

À vérifier :
- [ ] casque Bélier (Épique) : nom violet, badge EPIC, puis « Ensemble Bélier », « Emplacement : Tête »,
      « Protection : 27 % », « Protège la tête » ; plus aucun « Épique » écrit ;
- [ ] tenue complète, combinaison (Hazmat), masque à gaz, ghillie : badge seul, une fois ;
- [ ] objet d'événement de l'Armurerie (citrouille, staff) : pas de badge, ligne « Événement » ;
- [ ] Canon III (Épique) : badge EPIC, puis « Utilisé pour : Armes » et sa description ;
- [ ] arme TaCZ, couteau skinné (Unique : badge ARTIFACT), munition, grenade, soin : un badge sous le nom ;
- [ ] camo, sticker, module StatTrak : badge à la place du mot « Épique », une fois ;
- [ ] Bonus de loot (boutique) : badge EPIC à la place du mot ;
- [ ] arme avec un camo : badge sous le nom, puis « Camo : Galaxie » ;
- [ ] dans les écrans de Mila, du recycleur, de l'atelier de réparation, du coffre T7 : le badge s'affiche (pas un carré
      vide) ;
- [ ] caisse (casino / caisses) : l'infobulle d'une carte dit « Épique · 2,5 % » et ne répète pas le badge de l'objet ;
- [ ] en anglais et en russe : même badge (il est en anglais pour tous).

## Pas changé

- Atelier d'arme et onglet Skin : l'étiquette de rareté des camos dans ces écrans reste un texte (ce sont des écrans,
  pas l'infobulle d'un objet ; leur dessin ne change pas sans rendus validés).
- Le Flash Shield de Tactique (-25 % de vitesse en main) : ce n'est pas de l'équipement porté, et il n'est donné nulle
  part (plus dans aucun onglet).
- Anciennes clés de caisse du jar des meubles (« Epic Amethyst Key »…) : jar d'un autre auteur, jamais données.

## Rendus

`rendus_14nov/infobulles/` (scratchpad de la session) : `0_infobulles_avant_apres.png` (les trois), `1_casque_epique.png`,
`2_accessoire_commun.png`, `3_composant_epique.png`, `4_les_6_badges.png`. Police et fond d'infobulle de Minecraft,
badges tirés du jar des meubles ; les lignes d'effets du bipied sont recopiées des textes de TaCZ.
