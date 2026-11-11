# Essais en jeu — réparation des armes (retours du 11/11)

Chantier « réparation » : usure des armes et kits de réparation (`modules/monde`, mod `heraliz_usure`, Monde 1.2.0).
**Rien n'a été testé en jeu.** Jars joueurs et serveur de la même fabrication (canal `heraliz_usure:main` changé).

Réglages : `serverconfig/heraliz_usure-server.toml` du monde (copié depuis `serveur/defaultconfigs/` à chaque mise en
ligne, relu tout seul 2 s après un changement), aussi modifiables en jeu avec `/usure config`, onglet « Réparation ».
La rareté d'un objet est celle que montre HeraliZ Loot (couleur derrière l'objet dans les cases, ligne « Rare » de
l'infobulle ; `raretes.json`, `/rarete`).

## Point 1 — nombre de réparations selon la rareté de l'arme

Grise (Commun) 3, verte (Peu commun) 5, bleue (Rare) 8, violette (Épique) 12, légendaire 17 (Unique, aucune arme
aujourd'hui : 17). Section `[reparation.reparations_max]` du fichier ; tableau « Par rareté » de `/usure config`.

- [ ] Infobulle d'une arme neuve : nouvelle ligne « Réparations restantes : 8 / 8 » pour une bleue (AK-47, M4A1) ;
      3 / 3 pour une grise (Glock 17, M1911) ; 5 / 5 verte ; 12 / 12 violette ; 17 / 17 légendaire.
- [ ] Réparer une arme bleue avec n'importe quel kit : message « … · réparations restantes : 7 / 8 », infobulle 7 / 8.
      Avec un autre kit ensuite : 6 / 8 (chaque réparation compte une fois, quel que soit le kit).
- [ ] Une arme déjà réparée avant la mise à jour garde son compte (réparée 3 fois : 5 / 8 pour une bleue).
- [ ] À 0 / 8 : le 0 est en rouge ; dans l'atelier, « Plus aucune réparation : cette arme a atteint son maximum de
      réparations. », bouton « Réparer » grisé, le kit n'est pas consommé.
- [ ] Arme ruinée (0 %) sans réparation restante : infobulle « Ruinée pour de bon : plus aucune réparation possible ».
- [ ] Armure, masque, combinaison : se réparent toujours, sans limite.
- [ ] Infobulle d'un kit : nouvelle ligne grise « Une arme se répare un nombre limité de fois, selon sa rareté ».
- [ ] Staff : `/usure voir <joueur>` dit « réparations : 3 faites sur 8 » ; `/usure reparer <joueur>` remet l'arme à neuf
      et son compte à 8 / 8.
- [ ] Mettre `rare = 2` dans le fichier (ou dans `/usure config`) : quelques secondes après, une arme bleue montre « … / 2 »
      sans redémarrage. Remettre 8.
- [ ] Changer la rareté d'une arme avec `/rarete` : son maximum suit la nouvelle couleur.

## Point 2 — gain des kits selon leur rareté

Gris (Commun) +15 %, vert +20 %, bleu (Rare) +25 %, violet (Épique) +35 %, légendaire +50 % (Unique : +50 %), sur les
armes comme sur les armures. Section `[reparation.gain_kit]` du fichier ; colonne « Rend un kit » de `/usure config`.
Les kits gardent leur rareté du loot (pas de kit gris ni vert aujourd'hui) :

| Kit | Rareté (couleur) | Avant | Après |
|---|---|---|---|
| Kit de réparation de fortune | Rare (bleu) | +7 %, 1 utilisation | +25 %, 1 utilisation |
| Kit de réparation | Épique (violet) | +18 %, 2 utilisations | +35 %, 2 utilisations |
| Kit d'armurier | Légendaire (doré) | +45 %, 3 utilisations | +50 %, 3 utilisations |

- [ ] Infobulle du kit de fortune : « Rend +25 % d'état (jusqu'à 100 %) » et « Répare aussi les armures : +25 % » ;
      kit de réparation +35 % ; kit d'armurier +50 %.
- [ ] Atelier, arme bleue neuve tirée jusqu'à 30 %, kit de fortune : « 30 % → 55 % », pastille « +25 % » ; même arme avec
      le kit d'armurier : « 30 % → 80 % ».
- [ ] Une armure à moitié usée : +25 % / +35 % / +50 % de sa durabilité selon le kit.
- [ ] Passer le kit de fortune en gris avec `/rarete` : son infobulle dit +15 % et l'atelier rend 15 %. Remettre Rare.
- [ ] `/usure config`, onglet « Réparation » : la colonne « Rend » des kits montre le gain de leur rareté (pastille de
      couleur, non modifiable) ; le tableau « Par rareté » règle « Réparations d'une arme » et « Rend un kit ».
- [ ] Mettre `rare = 30` dans `[reparation.gain_kit]` du fichier : le kit de fortune rend 30 % quelques secondes après.

## Point 3 — atelier : en rouge ce qui ne se répare plus (À VALIDER)

Écran changé seulement après validation des rendus (aperçus refaits par `python modules/monde/outils/apercu_atelier.py
DOSSIER`). Sans ce changement, les points 1 et 2 marchent avec l'écran actuel (message du point 1 à 0 réparation).

- [ ] Arme abîmée posée (ex. AK-47 à 30 %, max 88 %, kit de fortune) : « 30 % → 55 % » et « +25 % » ; sur la barre, en
      vert ce que la réparation rend, en rouge vif ce qu'elle coûte (88 → 84 %), en rouge plus sombre ce qui était déjà
      perdu (au-delà de 88 %) ; dessous « En rouge : ne se répare plus (max 88 % → 84 %) » puis « Réparations
      restantes : 5 → 4 sur 8 » (en orange pour la dernière : 1 → 0).
- [ ] Arme déjà à son maximum : « Déjà au mieux avec ce kit. », la barre et son rouge restent, « Réparations restantes :
      4 sur 8 », « Réparer » grisé.
- [ ] Arme sans réparation : « Plus aucune réparation possible. » et « Réparations restantes : 0 sur 8 » en rouge, barre
      avec son rouge, « Réparer » grisé.
- [ ] Armure : barre sans rouge, « Pièce d'armure : jusqu'à 100 % », pas de ligne de réparations.
- [ ] En anglais et en russe : aucun texte coupé par « … » sous la barre.
