# Essais en jeu du 31/10 : chantier « armes » (rien testé par Claude)

Jars touchés : HeraliZ Tactique 2.41.0 (`armes`, joueurs ET serveur : nouveau mixin), Loot 0.14.1, Monde 1.0.4,
Clans 3.8.1 (données du serveur seulement). Gunpacks du serveur (`serveur/tacz`) et configs (`serveur/config`) changés :
mise en ligne complète, puis redémarrage. Rien n'a été compilé ici (Gradle bloqué) : le contrôle de branche de GitHub
le fera.

## 1. Armes trouvées avec la moitié de leur chargeur
Toute arme qui sort du loot a 50 % de la capacité de SON chargeur (arrondi en dessous, au moins 1, rien dans la chambre).
Avant : 10 %. Exemples : Glock 17 8 sur 17, AK-47 15 sur 30, M870 2 sur 5, PP-19 32 sur 64.

| D'où vient l'arme | Avant | Maintenant |
|---|---|---|
| Conteneurs T1 à T7 (et cadavres de zombies posés sur la carte, qui sont des T1) | 10 % | 50 % |
| Coffre T7 de l'hélicoptère abattu | 10 % | 50 % |
| Largage (frappes aériennes et événement « largage public ») | 10 % | 50 % |
| Butin des zombies (table militaire) | vide | 50 % |
| Caisse de ravitaillement des territoires de clan | vide | 50 % |
| Boutique, kits, quêtes, caisses de la boutique, ClashWar | inchangé | inchangé |

À vérifier :
- ouvrir plusieurs conteneurs, un largage, tuer des zombies militaires : le chargeur d'une arme trouvée est à moitié plein ;
- une arme à 2 coups (double canon) arrive avec 1 balle.

## 2. Fusils à pompe : vrais dégâts
Cause trouvée : TaCZ partage déjà la décharge entre les plombs ; notre réglage la divisait une deuxième fois. Une
décharge entière de M870 faisait 4 dégâts (2 cœurs) au lieu de 36. Maintenant : 4 par plomb, plafond de 13 par tir sur
un même joueur (tête comprise, avant l'armure ; pistolet à plombs Deagle 12g : 12).

| Contre un joueur de 20 PV, à courte portée | Avant (tir parfait) | Maintenant |
|---|---|---|
| Sans armure | 4 dégâts : 5 à 10 tirs | 13 : 2 tirs (jamais 1) |
| Armure Rare (32 %) | 2,7 dégâts : 8 à 14 tirs | 8,8 : 3 tirs |
| Armure Épique (41 %) | | 7,7 : 3 tirs |
| Armure Légendaire (50 %) | | 6,5 : 4 tirs |

Plombs qui doivent toucher pour le plafond : M870, AA-12, Saiga 12, 686, Olympia, double canon long 4 ; Stakeout 3 ;
double canon court 6 à 7 ; Deagle 12g 5. Le double canon à balle unique (DB-50) : 13 par balle.
Une rafale (mode rafale du double canon court, du Stakeout) compte comme UN tir (13 au plus). Deux tirs rapides
d'AA-12 ou de Saiga ne partagent plus le même plafond (avant, à 2 ticks d'écart, le second ne faisait rien).

À vérifier :
- sans armure, deux décharges bien placées de près tuent (ou mettent à terre), une seule jamais ;
- avec une armure Rare complète, il en faut trois ;
- à 15-20 blocs et plus, les dégâts baissent (moins de plombs touchent) ;
- l'infobulle d'un fusil à pompe montre maintenant les dégâts de la décharge entière (M870 : 36 ou « 4 x 9 ») ; le
  plafond de 13 n'y est pas écrit.

## 3. Silencieux
Ce qui trahissait le tireur : 123 armes sur 124 tiraient une traçante à chaque balle (trait lumineux vu par tous à
80 blocs, qui part du canon), même avec un silencieux. Maintenant : avec un silencieux, plus aucune traçante (réglage
`[silencieux] traceuses = false` de `heralisz_tactical-server.toml`). Le son « net » (à la vraie place) s'entend jusqu'à
10 blocs au lieu de 16 et s'éteint à 20 au lieu de 32 ; au-delà, le son étouffé et flou du serveur (inchangé). Pas
d'autre indice trouvé : TaCZ ne dessine la flamme du canon que chez le tireur, les douilles et les sous-titres ne
montrent rien de plus.

À vérifier (deux joueurs) :
- arme avec silencieux : plus de trait lumineux, ni pour le tireur ni pour les autres ; sans silencieux : traçantes comme avant ;
- à moins de 10 blocs, on entend bien d'où vient le tir ; entre 10 et 20, le son net baisse et le son flou monte ;
- à 30-60 blocs, on devine la direction (à 20-30° près) mais pas l'endroit exact ; on entend toujours qu'on a tiré ;
- les impacts des balles (sur les blocs) restent visibles et audibles là où elles tombent.

## 4. Icônes sans crosse
Glock 18, TEC-9, 9A-91 et Vector 9 mm : l'icône d'inventaire n'a plus la crosse (TaCZ ne la montre que si une crosse est
posée). Planche : `maquettes/icones_armes/sans_crosse_31oct.png` (image d'origine de l'auteur / icône du 28/10 /
nouvelle). Les autres armes n'ont pas changé.

À vérifier : les quatre icônes dans l'inventaire, en grand (atelier) et en petit (barre) ; aucune autre icône ne change.
À valider : Scorpion EVO 3 et PP-19 ont le même défaut (en bas de la planche), pas encore corrigés.

## 5. « Glock 54 Integrated Laser Battery » retiré
Accessoire `maxstuff:glaser` (un viseur du Glock 54 qui se posait sur toutes les armes) : retiré du gunpack, du loot T1
à T7, de la Table d'armurier, de la boutique de Jack et de la liste commune des viseurs ; ses fichiers sont effacés du
Ptero à la mise en ligne (`serveur/tacz_retires.txt`). Le Glock 54 (l'arme) reste.

À vérifier :
- il n'apparaît plus à la Table d'armurier, chez Jack, ni dans le loot ;
- un exemplaire déjà gardé par un joueur devient un objet sans image (comme les autres objets retirés) ; posé sur une
  arme, il disparaît de l'arme. Le recycleur sait encore le recycler (circuits, batteries) tant que
  `outils/recyclage.py` n'a pas été relancé.
