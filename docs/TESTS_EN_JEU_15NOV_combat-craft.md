# Essais en jeu — chantier « combat-craft » (audit du 15/11 : combat, duplication, artisanat)

Loot **0.18.1**, Équipement **1.3.1**, Tactique **2.56.0**, Boutique **1.5.8**, CRac **1.37.2-beta** ; gunpacks
`heraliz_1_base_tacz` et `heraliz_3_accessoires_gucci` (données et textes) ; `serveur/config/tacz-common.toml`.
Rien n'a été essayé en jeu par Claude, ni fabriqué ici (Gradle ne se télécharge pas sur la machine de travail) : chaque
fichier Java changé a seulement été compilé à part, contre des copies vides de Minecraft, Forge et TaCZ (signatures
relues). `python outils/verifier_langues.py` : 0 erreur.

Jars : serveur obligatoire. Joueurs : nouveaux jars de Tactique et d'Équipement pour les nouveaux textes (sinon les
messages du serveur s'affichent en français, et l'Atelier garde l'ancien mot « Enflamme »). Données des gunpacks (dégâts,
munitions) : envoyées par le serveur à la connexion, rien à faire. Textes des munitions (dossier `assets` du gunpack de
base) : arrivent chez les joueurs avec le prochain pack du launcher.

## Ce qui change

| Constat | Pour le joueur |
|---|---|
| Duplication n° 2 (artisanat) | Seul le grade Fondateur fabrique. La grille 2×2 de l'inventaire réel (écran de Curios) ne donne plus rien aux autres, même avec un jeu modifié ou l'écran de secours de Curios ; la case résultat de toute grille de Minecraft ne se prend plus pour eux (clic, Maj + clic, touches, jet, glisser). Livre de recettes : il pose les objets dans la grille, le résultat reste vide. Autres postes de Minecraft (tailleur, forge, enclume, fours...) : déjà hors service. Table d'armurier, recycleur, marchands : inchangés |
| Duplication n° 2 et n° 4 (déconnexion) | À la déconnexion et au redémarrage, l'objet tenu à la souris et ce qui reste dans une grille d'artisanat reviennent dans l'inventaire ; inventaire plein : mis de côté et rendus dès qu'une case se libère (message « Objets mis de côté rendus »). Plus rien au sol. Un objet ordinaire mis de côté tombe avec le joueur s'il meurt avant de l'avoir récupéré |
| Duplication n° 3 | « Décharger » (touche N) et la mise en vente d'une arme à l'hôtel des ventes rendent aussi la balle de la chambre (PKP pleine : 121 balles rendues, plus 120) |
| Duplication n° 5 | Une vieille caisse en métal à 3 lignes ne repasse à 2 lignes que quand plus personne n'a son écran ouvert : plus de plantage possible, rien de perdu |
| Combat M1 | Plus de nombre géant (39 chiffres) ni de « 0,1 » au-dessus de la victime pendant une exécution ; un coup mortel affiche la vie vraiment perdue (balle de 30 sur un joueur à 4 PV : 4) |
| Combat M2 | Plus de phrases anglaises « You have a broken bone ! Use Splint Item » (chaque seconde) ni « You are bleeding! Use Field Dressing Item » ; à la place, une seule fois au début : « Jambe cassée : utilise une attelle. » / « Tu saignes : utilise un bandage ou une trousse de soins. » (fr, en, ru). Nos autres messages de la barre ne sont plus effacés |
| Combat B1 | Le feu d'un molotov brûle au nom du lanceur : « EN COMBAT » des deux côtés, mort créditée au lanceur, petits chiffres de dégâts ; dans une zone sans PvP, la victime brûle sans perdre de vie. Le lanceur qui marche dans son propre feu brûle comme avant |
| Combat B2 | Mémoire : les joueurs irradiés ne restent plus en mémoire jusqu'au redémarrage (rien de visible) |
| Combat B3 | Jambe cassée : un jeu modifié qui garde l'élan de la course est ramené au point de départ de son saut (ligne « [jambe cassée] » au journal du serveur) |
| Combat B4 | Textes justes de l'Ibuprofène (1 min sans lenteur ni faiblesse, 1 cœur) et du baume Golden Star (2 min, régénération 10 s, un demi-cœur) ; ni l'un ni l'autre ne soigne saignement, jambe cassée ou infection |
| Combat M3 (décision du propriétaire) | « Perce-armure » = +10 % de dégâts : munitions FMJ +10 % (avant −10 %), crosse « Precision-5 » +35 % (avant +25 % et un perce-armure sans effet). Le « − Perce-armure » des munitions HP, explosives et incendiaires disparaît (il ne faisait rien). HP inchangées (+20 %). Munitions incendiaires : la cible (joueur ou créature) prend feu 3 s, visible, et perd 0,5 cœur de plus par balle, compté au tireur ; jamais de feu au sol. Textes des 4 munitions en français, anglais, russe |

## À vérifier

### Artisanat (deux comptes : un Fondateur, un joueur)
- [ ] Joueur : bûches dans la grille 2×2 de l'inventaire (si elle est visible, ou avec l'écran de secours de Curios) → aucune planche ; les bûches reviennent à la fermeture ;
- [ ] Fondateur : la même grille fabrique ; l'établi s'ouvre et fabrique (rien ne change pour lui) ;
- [ ] joueur : établi toujours « hors service » ; Table d'armurier, recycleur, marchands : fabriquent, recyclent, vendent comme avant ;
- [ ] joueur, livre de recettes (si on y arrive) : les objets se posent, aucun résultat.

### Déconnexion (curseur et grille)
- [ ] Objet tenu à la souris dans l'inventaire, se déconnecter : à la reconnexion, il est dans l'inventaire, rien au sol à l'endroit du départ ;
- [ ] pareil avec un coffre de base ouvert, et pendant un `/redemarrage` ;
- [ ] inventaire plein + objet à la souris, se déconnecter : à la reconnexion, rien au sol ; vider une case : « Objets mis de côté rendus : 1. » ;
- [ ] même cas, mourir avant d'avoir fait de la place : l'objet est sur le corps (il ne revient pas à la réapparition) ;
- [ ] objets à vie (couteaux) : mêmes règles qu'avant (rendus, jamais au sol, jamais sur le corps).

### Déchargement (n° 3)
- [ ] PKP pleine, balle chambrée, touche N : « 121 balles récupérées » et 121 balles de plus dans l'inventaire ;
- [ ] arme ordinaire : nombre de balles rendu = nombre annoncé ;
- [ ] hôtel des ventes : mettre en vente une PKP pleine chambrée → 121 balles rendues.

### Caisse en métal (n° 5) — seulement s'il reste une caisse à 3 lignes
- [ ] Ouvrir la caisse, vider sa 3e ligne, reculer à 6 ou 7 blocs écran ouvert : pas de plantage ; fermer, rouvrir : 2 lignes.

### Exécution (M1)
- [ ] Mettre un joueur à terre, l'exécuter : aucun chiffre géant ni « 0,1 » ;
- [ ] tir mortel sur un joueur à peu de vie : le chiffre = la vie qu'il avait.

### Jambe cassée et saignement (M2, B3)
- [ ] Sauter de 8 à 10 blocs : « Jambe cassée : utilise une attelle. » une seule fois, plus aucune phrase anglaise ;
- [ ] se faire tirer dessus jusqu'au saignement : « Tu saignes : utilise un bandage ou une trousse de soins. » une seule fois ;
- [ ] avec la jambe cassée, taper une commande refusée : le message reste affiché ;
- [ ] jambe cassée, sauts normaux (course, sur place, en descendant une marche, avec Ibuprofène ou lait concentré) : JAMAIS ramené en arrière ;
- [ ] (staff, si possible avec un jeu modifié qui garde l'élan) : ramené au départ du saut, ligne « [jambe cassée] » au journal.

### Molotov (B1)
- [ ] A lance un molotov sur B : « EN COMBAT » chez les deux dès la première brûlure ; B se déconnecte dans le feu → mort ;
- [ ] B meurt (à terre puis achevé, ou saigne) à cause du feu : kill et points de saison pour A ;
- [ ] A marche dans son propre feu : il brûle et perd de la vie comme avant, sans « EN COMBAT » ;
- [ ] dans une zone sans PvP : B brûle sans perdre de vie ; zombies dans le feu : ils brûlent comme avant, le butin va à A.

### Munitions (M3)
- [ ] Atelier, munitions FMJ sur une arme : « +10 % dégâts » (plus de « Perce-armure ») ; chiffres sur la cible d'essai : +10 % avec FMJ, armure ou non ;
- [ ] crosse « Precision-5 » : « +35 % dégâts » dans l'Atelier, plus de « Perce-armure » ;
- [ ] HP : +20 % comme avant ; HP, explosives, incendiaires : plus de « − Perce-armure » ;
- [ ] incendiaires sur un joueur : il prend feu 3 s, perd 0,5 cœur de plus par balle (chiffre +1), pas 0,5 cœur par seconde de feu ; « EN COMBAT » ; dans l'eau le feu s'éteint ; aucun feu au sol, même en tirant dans l'herbe ou le bois ;
- [ ] incendiaires sur un joueur qui brûle déjà (lave, molotov) : son feu continue normalement ;
- [ ] incendiaires sur un zombie : il prend feu 3 s, butin au tireur ;
- [ ] infobulle des 4 munitions en français (plus d'anglais), en anglais et en russe.

### Textes (B4)
- [ ] Infobulle de l'Ibuprofène et du Golden Star en fr, en, ru.

## Pas vérifié par Claude
- Rien testé en jeu ; rien fabriqué par Gradle. Les mixins ne se vérifient qu'en jeu : s'ils ne s'appliquaient pas, rien
  ne plante (configs non obligatoires, `require = 0`), mais l'effet manquerait (grille de Curios qui fabrique, phrases
  anglaises de geckolib222). Cibles vérifiées par `javap` : `m_6199_` de `CuriosContainer` et `CuriosContainerV2`
  (curios-forge-5.14.1), `m_5661_` dans `m_6742_` de `BROKEN_BONE` et `BLEEDING` (geckolib222-4.4.7), champ privé
  `igniteEntity` des balles de TaCZ 1.1.8, ordre des appels dans `EntityKineticBullet.onHitEntity`.
- Le comportement de Mohist sur les dégâts rejoués au nom du lanceur (événement Bukkit, WorldGuard) est supposé suivre
  celui des balles : à vérifier dans une zone sans PvP.
- Règle du saut (B3) : seuils calculés sur la physique de Minecraft (simulation : saut honnête au plus 1,85 bloc sur sol
  plat pour 2,92 permis ; saut avec élan 3,64 à 4,17), pas mesurés en jeu. Si un joueur honnête est ramené, le dire :
  la règle se coupe en retirant l'écouteur `atterrissage` de `JambeCasseeServeur`.
- Existence d'une caisse en métal encore à 3 lignes sur le serveur (n° 5) : inconnue.
