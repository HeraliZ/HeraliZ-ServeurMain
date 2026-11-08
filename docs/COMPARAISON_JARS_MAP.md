# Comparaison des jars de la nouvelle map avec nos sources

Fait le 08/11, en lecture seule : aucun monde ouvert, aucune source modifiée, aucun jar ajouté au dépôt.
Référence : la branche `main` du dépôt à cette date (`modules/*/src/*/resources`, fichiers `.java`), et pour le code
les jars fabriqués depuis ces sources (`livraison/mods solo`, `modules/events/build/libs` pour les boss).

Méthode :
- **identifiants** : `assets/<modid>/blockstates` (blocs), `assets/<modid>/models/item` (objets), clés `block.`,
  `item.`, `entity.` des fichiers de langue, chaînes `<modid>:…` des classes (lues dans le pool de constantes) ;
- **fichiers** : chaque fichier `assets/` et `data/` du jar cherché au même chemin dans les ressources de tous nos
  modules, comparé par empreinte sha1 ; un JSON écrit autrement mais au même contenu est compté identique ;
- **code** : chaque classe du jar cherchée dans nos `.java` ; si elle existe, ses champs et méthodes comparés à ceux
  de notre jar fabriqué ; seules les classes sans source ou avec des membres en plus ont été décompilées.
  Une classe « différente » d'un jar plus ancien que le nôtre est le plus souvent une ancienne version (le jar date
  d'avant nos dernières versions) : le détail ci-dessous dit ce qui est vraiment en plus.

## Résumé

| Jar (dossier des mods de blocs) | modId (version) | Chez nous | Verdict |
|---|---|---|---|
| `cfm-forge-1.20.1-7.0.0-pre36PATCH.jar` | `cfm` 7.0.0-pre36 | `serveur/mods-tiers` | **identique** (même fichier, même empreinte) |
| `framework-forge-1.20.1-0.7.6.jar` | `framework` 0.7.6 | `serveur/mods-tiers` | **identique** |
| `geckolib222-forge-1.20.1-4.4.7.jar` | `geckolib` 4.4.7 | `serveur/mods-tiers` | **identique** |
| `heralisz_bunker-0.13.0-complet.jar` | `heralisz_bunker` 0.13.0 | module `bunker` (0.13.6) | **identique** pour les identifiants ; ancienne version (assemblage des fragments retiré exprès le 28/10) |
| `heralisz_matchs-0.15.4-complet.jar` | `heralisz_matchs` 0.15.4 | aucun module (Matchs retiré, seul ClashWar gardé dans `clashwar`) | **manque chez nous** : 130 blocs, 3 objets, 8 entités |
| `heralisz_utils-2.26.0-complet.jar` | `heralisz_utils` 2.26.0 (+5) | module `monde` (2.26.1) | **identique** pour les identifiants ; **différent** : 3 réseaux de train et la réparation au clic droit du kit absents chez nous |
| `heraliz_boss-2.2.4-complet.jar` | `heraliz_boss` 2.2.4 (+5) | module `events` (2.2.5, mis de côté) | **identique** pour les identifiants ; outils de développement retirés chez nous |
| `heraliz_decors-1.30.3-complet.jar` | `heraliz_decors` 1.30.3 | module `monde` (Décors 1.12.1) | **manque chez nous** : 252 blocs, 63 classes, 1 496 fichiers |
| `heraliz_loot-0.12.1-complet.jar` | `heraliz_loot` 0.12.1 | module `loot` (0.16.0) | **identique** pour les identifiants (435 / 435) ; ancienne version |
| `heralizcasino-2.5.1-complet.jar` | `heralizcasino` 2.5.1 | module `boutique` (casino 2.7.0) | **identique** pour les identifiants (nous avons `atm` en plus) ; ancienne version |

**En bref** : 10 jars de blocs. 3 identiques à l'octet près (cfm, framework, GeckoLib). 5 ont tous leurs
identifiants chez nous (Bunker, Utils, Boss, Loot, Casino : ce sont d'anciennes versions de nos mods). **2 ont des
identifiants qui n'existent pas chez nous** :
- `heraliz_decors` 1.30.3 : **252 blocs** en plus de nos 83 (liste complète section 1). Le jar a été fait à partir
  de notre Décors (nos 83 blocs y sont tous, au même nom) puis enrichi pour la map. Sans eux, ces blocs
  disparaissent de la map au premier chargement sur notre serveur ;
- `heralisz_matchs` 0.15.4 : le mod Matchs (Zombies, duels), retiré de notre projet exprès (ARCHITECTURE.md). Ses
  **130 blocs** (`zb_*`, décors de la carte Zombies, et les 2 tableaux du ClashWar) disparaîtraient aussi s'ils sont
  posés sur une des deux maps. Attention : les tableaux du ClashWar existent chez nous sous un **autre** identifiant
  (`heraliz_clashwar:cw_tableau`, `heraliz_clashwar:cw_tableau_bord`), pas `heralisz_matchs:…`.

Le fichier qui accompagne les maps dit que `heralisz_matchs` sert à « quelques blocs du catalogue » (la map de
validation des bâtiments). Les mondes n'ont pas été ouverts : savoir lesquels de ces blocs sont vraiment posés, et
sur quelle map, demande le workflow « Inventaire de la carte » (lecture seule) une fois la map sur le serveur.

## 1. `heraliz_decors-1.30.3-complet.jar` — manque chez nous

Module chez nous : `monde` (Décors 1.12.1). Verdict : **différent, avec des identifiants qui manquent chez nous.**

### a) Identifiants

- Blocs : 335 dans le jar, 83 chez nous. Nos 83 blocs sont tous dans le jar.
- **252 blocs manquants chez nous** (chacun a aussi son objet, son modèle et son nom dans la
  langue) ; aucun objet, aucune entité ni entité de bloc en plus. Liste exacte (`heraliz_decors:` devant chacun) :

```
aiguillage  antenne_parabolique  arc_blanc  arc_blanc_droit  arc_blanc_gauche  arc_sable
arc_sable_droit  arc_sable_gauche  arcade_blanc  arcade_sable  armoire_combinaisons  auto_tamponneuse
bache_kaki  bache_kaki_dalle  bache_kaki_escalier  balustrade_bois  balustrade_bois_basse  balustrade_fer
banc_parc  banc_parc_casse  barbele  barque_buche  barrage_routier  barre_carrousel
barre_navire  barreaux  barricade  barriere_file_attente  berline_charbon  berline_charbon_pente
berline_fer  berline_fer_pente  berline_renversee  berline_vide  berline_vide_pente  beton_banche
beton_banche_dalle  beton_banche_escalier  beton_banche_mur  beton_piste  beton_piste_dalle  bois_cedre
bois_cedre_dalle  bois_cedre_escalier  bouee_sauvetage  bouquet_palmier  butoir  cables_pendants
caisse_materiel_polaire  camion_porte_conteneur  carlingue_grise  carlingue_grise_dalle  carlingue_grise_escalier  carlingue_kaki
carlingue_kaki_dalle  carlingue_kaki_escalier  carottier_glace  carte_murale_tactique  cellule_door  chaise_bistrot
chaise_metal  chaise_paillee  chariot_elevateur  chariot_glacier  chariot_repas  chenillette
cheval_carrousel  claustra  coffre_capitaine  combinaison_nrbc  comptoir_caisse  conduit
console_navigation  console_sonar  conteneur_paroi_blanc  conteneur_paroi_bleu  conteneur_paroi_gris  conteneur_paroi_orange
conteneur_paroi_rouge  conteneur_paroi_vert  conteneur_porte_blanc  conteneur_porte_bleu  conteneur_porte_gris  conteneur_porte_orange
conteneur_porte_rouge  conteneur_porte_vert  coussin  coussin_bleu  coussin_rouge  cristaux_mine
crochet_grue  cuve_specimen  distributeur_billets  echelle_bois_brut  echelle_corde_desert  echelle_givree
echelle_jaune  enduit_bleu  enduit_bleu_use  extincteur_mural  fauteuil_renverse  fenetre_arc
fenetre_claustra  fenetre_moucharabieh  fenetre_moucharabieh_blanc  fenetre_volets  feu_phare  feuilles_palmier
filon_charbon  filon_fer  flaque_toxique  fronde_palmier  fronde_palmier_diagonale  fut_gele
fut_radioactif  fut_renverse  gaine_calorifugee  galley_avion  grand_groupe_ventilation  grande_arcade_blanc
grande_arcade_sable  grande_nacelle  grande_nacelle_cassee  gravats  grille_toit  groupe_electrogene_polaire
groupe_ventilation  gyrophare  habitacle_compas  horloge_gare  hublot_carlingue  hublot_carlingue_use
hublot_grand  jarre  jonchee  lampadaire_casse  lampadaire_tombe  lampe_mineur
lampe_orientale_sol  lampe_rouge_clignotante  lampe_rouge_fixe  lampe_urgence  lance_haute_pression  lanterne_orientale
lanterne_orientale_murale  liquide_toxique  lit_camp_militaire  lumiere_phare  lustre_oriental  marquage_blanc
marquage_jaune  masque_gaz_au_sol  microscope_labo  motoneige  mur_prison  mur_prison_bas
nacelle_grande_roue  nacelle_grande_roue_cassee  panier_basket  panneau_carlingue  panneau_carlingue_bande  panneau_carlingue_bande_use
panneau_carlingue_brule  panneau_carlingue_use  panneau_danger  panneau_departs  panneau_polaire_gris_dalle  panneau_polaire_gris_escalier
panneau_polaire_gris_mur  pendu  petit_train_locomotive  petit_train_wagon  petite_jarre  pierre_sable_frise
pilier_sable  pise  pise_dalle  pise_escalier  pneu  poele_a_bois_arctique
pompe_essence  pompe_hydraulique  porte_arctique  porte_atelier  porte_au_sol  porte_chalet_isolee
porte_chambre_froide  porte_cloutee_sombre  porte_douane  porte_grange  porte_grille  porte_maison
porte_motel  porte_orientale  porte_orientale_bleue  porte_orientale_verte  porte_phare  porte_sanitaires
porte_station_service  portique_lavage  poste_commande_sous_marin  poteau_basket  presentoir_huile  pylone_montagnes_russes
rack_bouteilles_plongee  radar_bord  radiateur_mural  radio_militaire  rail_mine  rail_mine_pente
rail_mine_virage  rail_montagnes_russes  rail_montagnes_russes_pente  rail_montagnes_russes_vertical  rail_montagnes_russes_virage  rayonnage_boutique
rayonnage_renverse  rouleau_lavage  sable_dune  sac_mortuaire  scaphandre  siege_avion
siege_avion_double  siege_gare  skis_croises  sol_prison  sol_prison_dalle  station_auto
stuc_sculpte  table_bistrot  table_cartes_marines  table_metal  table_rustique  tableau_compteur_jours
tableau_ecole  tag_mur  tapis_kilim  tas_charbon  tas_fer  tole_larmee_escalier
tole_larmee_mur  tracteur_portuaire  traineau  transmetteur_ordres  treuil_cable  tuile_verte
tuile_verte_dalle  tuile_verte_escalier  verriere  voiture_accidentee  wagonnet  zellige_ocre
```

- Chaînes des classes : rien en plus dans le jar ; nos sources citent en plus `heraliz_decors:dallage_sable` et
  `heraliz_decors:sable_tasse` (blocs présents des deux côtés).

### b) Fichiers

- Fichiers `assets/` et `data/` du jar : 1931 (sans les `LISEZ-MOI-HeraliZ.txt` posés à la fabrication). Identiques chez nous (même empreinte sha1) : 417, dont 337 seulement écrits autrement (même contenu JSON).
- **Manquants chez nous : 1496** :
  - `assets/heraliz_decors/blockstates/aiguillage.json`
  - `assets/heraliz_decors/blockstates/antenne_parabolique.json`
  - `assets/heraliz_decors/blockstates/arc_blanc.json`
  - `assets/heraliz_decors/blockstates/arc_blanc_droit.json`
  - `assets/heraliz_decors/blockstates/arc_blanc_gauche.json`
  - `assets/heraliz_decors/blockstates/arc_sable.json`
  - `assets/heraliz_decors/blockstates/arc_sable_droit.json`
  - `assets/heraliz_decors/blockstates/arc_sable_gauche.json`
  - `assets/heraliz_decors/blockstates/arcade_blanc.json`
  - `assets/heraliz_decors/blockstates/arcade_sable.json`
  - `assets/heraliz_decors/blockstates/armoire_combinaisons.json`
  - `assets/heraliz_decors/blockstates/auto_tamponneuse.json`
  - … et 1484 autres (même dossier que les identifiants manquants ci-dessus)
- **Contenu différent : 18** :
  - `assets/heraliz_decors/blockstates/ambiance.json`
  - `assets/heraliz_decors/blockstates/grande_cascade.json`
  - `assets/heraliz_decors/lang/en_us.json`
  - `assets/heraliz_decors/lang/fr_fr.json`
  - `assets/heraliz_decors/textures/block/couverture_grise.png`
  - `assets/heraliz_decors/textures/block/cristal_bleu.png`
  - `assets/heraliz_decors/textures/block/eau_surface.png.mcmeta`
  - `assets/heraliz_decors/textures/block/hublot.png`
  - `assets/heraliz_decors/textures/block/lavabo_vasque.png`
  - `assets/heraliz_decors/textures/block/matelas_sale.png`
  - `assets/heraliz_decors/textures/block/merde.png`
  - `assets/heraliz_decors/textures/block/metal_rouille.png`
  - `assets/heraliz_decors/textures/block/porcelaine_sale.png`
  - `assets/heraliz_decors/textures/block/tache_sang_grande_flaque.png`
  - `assets/heraliz_decors/textures/block/toilettes_cuvette.png`
  - `assets/heraliz_decors/textures/block/tole_toit_bleue.png`
  - `assets/heraliz_decors/textures/block/tube_peint.png`
  - `data/minecraft/tags/blocks/sand.json`
- Chez nous et pas dans le jar (même espace de noms, hors `minecraft`) : 1 :
  - `assets/heraliz_decors/lang/ru_ru.json`

Remarques : les 1 492 fichiers `assets/heraliz_decors/` manquants sont les modèles, textures et états des 252 blocs
(568 modèles de bloc, 406 textures, 252 modèles d'objet, 14 textures d'objet, 252 fichiers d'états). Les 4 fichiers
`data/minecraft/tags/blocks/` manquants (`climbable`, `fences`, `walls`, `wooden_fences`) rangent ces nouveaux blocs
(échelles, barrières, murets). Les 13 textures différentes sont des blocs que nous avons aussi (tôle bleue, hublot,
toilettes, lavabo, matelas, etc.) : le jar a d'autres images. Le jar n'a **pas de russe** (`ru_ru.json` absent) :
les 252 nouveaux blocs n'ont que le français et l'anglais.

### c) Code

- Classes du jar : 119. Mêmes champs et méthodes que notre jar fabriqué (`heraliz_monde-1.1.1-complet.jar`) : 47.
- Classes sans source chez nous : 63 : `fr.heraliz.decors.bloc.Aiguillage`, `fr.heraliz.decors.bloc.Aiguillage$Cote`, `fr.heraliz.decors.bloc.Applique`, `fr.heraliz.decors.bloc.Arcade`, `fr.heraliz.decors.bloc.Arcade$Cote`, `fr.heraliz.decors.bloc.Arcade$Rang`, `fr.heraliz.decors.bloc.ArcadeFormes`, `fr.heraliz.decors.bloc.BalustradeBasse`, `fr.heraliz.decors.bloc.Barriere`, `fr.heraliz.decors.bloc.Barriere$1`, `fr.heraliz.decors.bloc.BlocOriente`, `fr.heraliz.decors.bloc.Clignotement`, `fr.heraliz.decors.bloc.Clignotement$Lancement`, `fr.heraliz.decors.bloc.Conduit`, `fr.heraliz.decors.bloc.Conduit$1`, `fr.heraliz.decors.bloc.Cristaux`, `fr.heraliz.decors.bloc.Cristaux$1`, `fr.heraliz.decors.bloc.CuveSpecimen`, `fr.heraliz.decors.bloc.EchelleJaune`, `fr.heraliz.decors.bloc.Engin`, `fr.heraliz.decors.bloc.Engin$Partie`, `fr.heraliz.decors.bloc.FenetreOrientale`, `fr.heraliz.decors.bloc.FenetreOrientale$Partie`, `fr.heraliz.decors.bloc.FenetreOrientale$Saillie`, `fr.heraliz.decors.bloc.FormesEngins`, `fr.heraliz.decors.bloc.FormesEngins123`, `fr.heraliz.decors.bloc.FormesEngins124`, `fr.heraliz.decors.bloc.FormesEngins125`, `fr.heraliz.decors.bloc.FormesEngins126`, `fr.heraliz.decors.bloc.FormesEngins127`, `fr.heraliz.decors.bloc.FormesEngins128`, `fr.heraliz.decors.bloc.FormesEngins129`, `fr.heraliz.decors.bloc.FormesEngins130`, `fr.heraliz.decors.bloc.Gyrophare`, `fr.heraliz.decors.bloc.HublotGrand`, `fr.heraliz.decors.bloc.LampeBunker`, `fr.heraliz.decors.bloc.LampeBunker$1`, `fr.heraliz.decors.bloc.LampeFixe`, `fr.heraliz.decors.bloc.LampeFixe$1`, `fr.heraliz.decors.bloc.LiquideToxique`, `fr.heraliz.decors.bloc.MultiBloc`, `fr.heraliz.decors.bloc.MultiBloc$Camion`, `fr.heraliz.decors.bloc.MultiBloc$Chariot`, `fr.heraliz.decors.bloc.MultiBloc$Colonne`, `fr.heraliz.decors.bloc.MultiBloc$EnginCarre`, `fr.heraliz.decors.bloc.MultiBloc$EnginLong`, `fr.heraliz.decors.bloc.MultiBloc$Lampadaire`, `fr.heraliz.decors.bloc.MultiBloc$Morceau`, `fr.heraliz.decors.bloc.MultiBloc$Nacelle`, `fr.heraliz.decors.bloc.MultiBloc$PartieCamion`, `fr.heraliz.decors.bloc.MultiBloc$PartieCarre`, `fr.heraliz.decors.bloc.MultiBloc$PartieChariot`, `fr.heraliz.decors.bloc.MultiBloc$PartieColonne`, `fr.heraliz.decors.bloc.MultiBloc$PartieLampadaire`, `fr.heraliz.decors.bloc.MultiBloc$PartieLong`, `fr.heraliz.decors.bloc.MultiBloc$PartieNacelle`, `fr.heraliz.decors.bloc.MultiBloc$PartieVoiture`, `fr.heraliz.decors.bloc.MultiBloc$Voiture`, `fr.heraliz.decors.bloc.PorteGrille`, `fr.heraliz.decors.bloc.TagMur`, `fr.heraliz.decors.bloc.TagMur$Motif`, `fr.heraliz.decors.client.AlarmeRouge`, `fr.heraliz.decors.client.AlarmeRouge$Forge_`.
- Classes différentes (champs ou méthodes en plus dans le jar) : 8 : `fr.heraliz.decors.Blocs`, `fr.heraliz.decors.Blocs$1`, `fr.heraliz.decors.bloc.Deco$ParQuart`, `fr.heraliz.decors.bloc.Proprietes`, `fr.heraliz.decors.bloc.Proprietes$Quart`, `fr.heraliz.decors.client.AmbianceClient$Forge_`, `fr.heraliz.decors.serveur.MeteoServeur`, `fr.heraliz.decors.serveur.ZonesServeur`.
- Ce que font ces classes (décompilées avec Vineflower 1.11.1, elles seules) :
  - `Blocs` : la liste des enregistrements, avec les 252 nouveaux blocs (tableau, sans entité de bloc ni menu) ;
  - `bloc/Deco$ParQuart` et `Proprietes$Quart` (propriété `partie`) : bloc découpé en quarts (grandes pièces) ;
  - `bloc/MultiBloc` (+ `Camion`, `Chariot`, `Colonne`, `EnginCarre`, `EnginLong`, `Lampadaire`, `Nacelle`, `Voiture`) : décors sur plusieurs blocs (camion porte-conteneur, chariot élévateur, colonnes, engins, lampadaire, nacelle de grande roue, voiture accidentée), chaque morceau posé et retiré avec les autres ;
  - `bloc/Engin`, `FormesEngins`, `FormesEngins123` à `130` : formes de collision des gros engins (tables de boîtes) ;
  - `bloc/Arcade`, `ArcadeFormes`, `FenetreOrientale` : arcades et fenêtres orientales qui se raccordent à leurs voisines ;
  - `bloc/Clignotement`, `LampeBunker`, `LampeFixe`, `Gyrophare`, `Applique` : lampes allumées, lampes rouges qui clignotent (cycle de 6 s, 1 s éteinte), gyrophare, appliques murales ;
  - `client/AlarmeRouge` (joueurs seulement) : près des lampes rouges qui clignotent, teinte la lumière en rouge (modifie la table d'éclairage de Minecraft par réflexion, 48 blocs autour) ;
  - `bloc/LiquideToxique` : flaque qui englue (déplacement ralenti) et empoisonne 3 s, au hasard (1 chance sur 20 par tick) — **un bloc de décor qui agit sur les joueurs, décidé dans le bloc lui-même** ;
  - `bloc/TagMur` : tag peint sur un mur, 24 motifs (« Vous êtes morts », « Aidez-nous », crâne, flèche…) ; un clic droit fait passer au motif suivant, **pour tout joueur, sans restriction** ;
  - `bloc/Aiguillage`, `Conduit`, `Cristaux`, `CuveSpecimen`, `HublotGrand`, `Barriere`, `BalustradeBasse`, `EchelleJaune`, `PorteGrille`, `BlocOriente` : formes et raccords des rails de mine, conduits, cristaux, cuve de laboratoire, grand hublot, barrières, balustrades, échelles, porte grillagée ;
  - `serveur/ZonesServeur` : la liste des masques et combinaisons qui protègent des zones est **écrite dans le code** (ancienne façon) ; chez nous elle est dans `heraliz_decors-server.toml` (section `[protections]`) : notre version est plus récente ;
  - `serveur/MeteoServeur`, `client/AmbianceClient$Forge_` : mêmes commandes et mêmes textes que chez nous (seulement l'écouteur déclaré autrement).

## 2. `heralisz_matchs-0.15.4-complet.jar` — manque chez nous (mod retiré exprès)

Aucun module ne déclare `heralisz_matchs` : ARCHITECTURE.md le dit « jeté » (Zombies, The Giant, duels, killcam),
seul le ClashWar est gardé, dans le module `clashwar`, sous le modId `heraliz_clashwar`.

### a) Identifiants

- **130 blocs manquants chez nous** (`heralisz_matchs:` devant chacun) :

```
cw_tableau  cw_tableau_bord  zb_arme_mur  zb_atelier  zb_atout  zb_boite
zb_collision  zb_courant3  zb_cuve_ames  zb_deco_banc  zb_deco_bocal_moelle  zb_deco_boitier_fly_trap
zb_deco_brasero  zb_deco_bureau  zb_deco_bureau_maxis  zb_deco_cachette_fusible  zb_deco_cages_2x2  zb_deco_cages_3x2
zb_deco_camion  zb_deco_classeur  zb_deco_classeur_renverse  zb_deco_cuve_bouillante  zb_deco_cuve_eau  zb_deco_etagere
zb_deco_etagere_outils  zb_deco_fourneau  zb_deco_fourneau_garage  zb_deco_futs_2x1  zb_deco_futs_2x2  zb_deco_generateur_a
zb_deco_generateur_cour  zb_deco_generateur_depart  zb_deco_horloge  zb_deco_incinerateur  zb_deco_levier_piege  zb_deco_lit_camp
zb_deco_paillasse  zb_deco_panneau_affichage  zb_deco_panneau_maxis  zb_deco_porte_confinement  zb_deco_presse  zb_deco_pylone_piege
zb_deco_radio  zb_deco_radio_mur  zb_deco_sacs_sable_2x1  zb_deco_sacs_sable_2x2  zb_deco_sacs_sable_3x1  zb_deco_tableau_noir
zb_deco_tete_robot  zb_deco_tour_metaux  zb_deco_vestiaires  zb_deco_voiture  zb_deco_welcome  zb_easter
zb_fenetre  zb_levier  zb_mat_acier_peint  zb_mat_acier_rivete  zb_mat_acier_rouille  zb_mat_applique
zb_mat_ardoise  zb_mat_ardoise_slab  zb_mat_ardoise_stairs  zb_mat_asphalte  zb_mat_barbeles  zb_mat_beton
zb_mat_beton_fissure  zb_mat_beton_slab  zb_mat_beton_sombre  zb_mat_beton_stairs  zb_mat_beton_tache  zb_mat_brique
zb_mat_brique_abimee  zb_mat_brique_pale  zb_mat_brique_slab  zb_mat_brique_stairs  zb_mat_brique_suie  zb_mat_caisse
zb_mat_caisse_renforcee  zb_mat_carreaux_blancs  zb_mat_carreaux_verts  zb_mat_dalles  zb_mat_damier  zb_mat_egout
zb_mat_enduit  zb_mat_fenetre_eclairee  zb_mat_fut  zb_mat_gravats  zb_mat_gravats_slab  zb_mat_grille_aeration
zb_mat_grille_sol  zb_mat_lampe_encastree  zb_mat_lueur_violette  zb_mat_moellon  zb_mat_neon  zb_mat_parquet
zb_mat_paves  zb_mat_paves_slab  zb_mat_paves_stairs  zb_mat_planches  zb_mat_planches_slab  zb_mat_planches_stairs
zb_mat_poteau_acier  zb_mat_poutre_bois  zb_mat_rambarde_wall  zb_mat_rayures  zb_mat_relais_935  zb_mat_rideau
zb_mat_sol_usine  zb_mat_suspension  zb_mat_terre_gelee  zb_mat_tole_larmee  zb_mat_tole_larmee_slab  zb_mat_tole_larmee_stairs
zb_mat_tole_ondulee  zb_mat_tuyau  zb_mat_verre_arme  zb_mat_vitre_sombre  zb_mf_plateforme  zb_mf_pupitre
zb_mf_tour  zb_neige_rebord  zb_neige_tache  zb_objet_quete  zb_obstacle  zb_pap
zb_pap_rideau  zb_piece  zb_tp_pad  zb_tp_pupitre
```

- Objets sans bloc manquants : `duel_charge`, `zb_fusible`, `zb_noyau_115`.
- Entités manquantes : `duel_bombe`, `duel_cible`, `duel_ordinateur`, `zb_bonus`, `zb_materiau`, `zb_ouverture`, `zb_rodeur`, `zb_zombie`.
- Identifiants qui ont changé de nom chez nous : `heralisz_matchs:cw_tableau` et `heralisz_matchs:cw_tableau_bord`
  existent chez nous comme `heraliz_clashwar:cw_tableau` et `heraliz_clashwar:cw_tableau_bord` : un tableau posé avec
  l'ancien identifiant disparaîtrait quand même.

### b) Fichiers

- 2363 fichiers `assets/` et `data/` ; aucun n'est chez nous au même chemin avec le même contenu (tout le mod
  manque), sauf 7 fichiers de `data/minecraft/tags/damage_type/` partagés, au contenu différent.

### c) Code

- 600 classes, aucune avec une source chez nous (paquets `fr.heralisz.matchs.moteur`, `duels`,
  `zombies`, `clashwar`). Rien décompilé : c'est le mod entier, retiré exprès ; son ClashWar a été repris et récrit
  dans le module `clashwar`.

## 3. `heralisz_utils-2.26.0-complet.jar` — identifiants identiques, quelques fichiers en plus dans le jar

Module chez nous : `monde` (Utils 2.26.1). modIds du jar : `heralisz_utils`, `heraliz_ping`, `heraliz_corps`, `heraliz_usure`, `heraliz_vehicules`, `heraliz_carte`.

### a) Identifiants

- Tous les blocs (66), objets (65 + 3 kits de réparation + 1 objet `ywzj_vehicle`) et entités sont chez nous, au même nom.
- Une chaîne en plus dans le jar : `heraliz_corps:corpse` (convertisseur des cadavres de l'ancien mod « corpse »,
  retiré chez nous le 02/11 : la map est neuve, plus aucun ancien cadavre à convertir).

### b) Fichiers

- Fichiers `assets/` et `data/` du jar : 658 (sans les `LISEZ-MOI-HeraliZ.txt` posés à la fabrication). Identiques chez nous (même empreinte sha1) : 593.
- **Manquants chez nous : 27** :
  - `assets/heralisz_utils/font/cyrillique_bold.ttf`
  - `assets/heralisz_utils/font/cyrillique_medium.ttf`
  - `assets/heralisz_utils/font/dmsans_bold.ttf`
  - `assets/heralisz_utils/font/dmsans_medium.ttf`
  - `assets/heralisz_utils/font/dmsans_ofl.txt`
  - `assets/heralisz_utils/font/onest_ofl.txt`
  - `assets/heralisz_utils/train/reseau_map_v1.json`
  - `assets/heralisz_utils/train/reseau_map_v2_4000.json`
  - `assets/heralisz_utils/train/reseau_map_v2_sol.json`
  - `assets/heraliz_corps/font/cyrillique_bold.ttf`
  - `assets/heraliz_corps/font/cyrillique_medium.ttf`
  - `assets/heraliz_corps/font/dmsans_bold.ttf`
  - `assets/heraliz_corps/font/dmsans_medium.ttf`
  - `assets/heraliz_corps/font/dmsans_ofl.txt`
  - `assets/heraliz_corps/font/onest_ofl.txt`
  - `assets/heraliz_ping/font/cyrillique_bold.ttf`
  - `assets/heraliz_ping/font/cyrillique_medium.ttf`
  - `assets/heraliz_ping/font/dmsans_bold.ttf`
  - `assets/heraliz_ping/font/dmsans_medium.ttf`
  - `assets/heraliz_ping/font/dmsans_ofl.txt`
  - `assets/heraliz_ping/font/onest_ofl.txt`
  - `assets/heraliz_usure/font/cyrillique_bold.ttf`
  - `assets/heraliz_usure/font/cyrillique_medium.ttf`
  - `assets/heraliz_usure/font/dmsans_bold.ttf`
  - `assets/heraliz_usure/font/dmsans_medium.ttf`
  - `assets/heraliz_usure/font/dmsans_ofl.txt`
  - `assets/heraliz_usure/font/onest_ofl.txt`
- **Contenu différent : 38** :
  - `assets/geckolib/lang/en_us.json`
  - `assets/geckolib/lang/fr_fr.json`
  - `assets/geckolib/lang/ru_ru.json`
  - `assets/heralisz_utils/font/ui.json`
  - `assets/heralisz_utils/font/ui_bold.json`
  - `assets/heralisz_utils/font/ui_small.json`
  - `assets/heralisz_utils/lang/en_us.json`
  - `assets/heralisz_utils/lang/fr_fr.json`
  - `assets/heralisz_utils/lang/ru_ru.json`
  - `assets/heraliz_carte/lang/en_us.json`
  - `assets/heraliz_carte/lang/fr_fr.json`
  - `assets/heraliz_carte/lang/ru_ru.json`
  - `assets/heraliz_carte/textures/gui/icones.png`
  - `assets/heraliz_corps/font/ui.json`
  - `assets/heraliz_corps/font/ui_bold.json`
  - `assets/heraliz_corps/font/ui_small.json`
  - `assets/heraliz_corps/lang/en_us.json`
  - `assets/heraliz_corps/lang/fr_fr.json`
  - `assets/heraliz_corps/lang/ru_ru.json`
  - `assets/heraliz_ping/font/ui.json`
  - `assets/heraliz_ping/font/ui_bold.json`
  - `assets/heraliz_ping/font/ui_small.json`
  - `assets/heraliz_ping/lang/en_us.json`
  - `assets/heraliz_ping/lang/fr_fr.json`
  - `assets/heraliz_ping/lang/ru_ru.json`
  - `assets/heraliz_ping/textures/gui/atlas.json`
  - `assets/heraliz_ping/textures/gui/atlas.png`
  - `assets/heraliz_usure/font/hud_etat.json`
  - `assets/heraliz_usure/font/hud_pct.json`
  - `assets/heraliz_usure/font/ui.json`
  - `assets/heraliz_usure/font/ui_bold.json`
  - `assets/heraliz_usure/font/ui_small.json`
  - `assets/heraliz_usure/lang/en_us.json`
  - `assets/heraliz_usure/lang/fr_fr.json`
  - `assets/heraliz_usure/lang/ru_ru.json`
  - `assets/heraliz_vehicules/lang/en_us.json`
  - `assets/heraliz_vehicules/lang/fr_fr.json`
  - `assets/heraliz_vehicules/lang/ru_ru.json`
- Chez nous et pas dans le jar (même espace de noms, hors `minecraft`) : 9 :
  - `assets/heralisz_utils/font/ui_16.json`
  - `assets/heralisz_utils/font/ui_bold_22.json`
  - `assets/heralisz_utils/font/ui_bold_44.json`
  - `assets/heralisz_utils/textures/entity/zombies/machoire_police.png`
  - `assets/heralisz_utils/textures/entity/zombies/police.png`
  - `assets/heraliz_usure/font/ui_micro.json`
  - `assets/heraliz_usure/font/ui_small_bold.json`
  - `assets/heraliz_usure/font/ui_titre.json`
  - `data/geckolib/curios/entities/heraliz_armory_joueur.json`

Remarques : les **3 réseaux de train** `reseau_map_v1.json`, `reseau_map_v2_4000.json` et `reseau_map_v2_sol.json`
ne sont que dans le jar (aucune classe ne les cite : le réseau d'un monde est choisi par son nom). Le
`reseau_map_v2_4000` semble fait pour la nouvelle map de 4 000 × 4 000 : **à récupérer** si le train doit rouler
dessus. Les polices `.ttf` manquantes sont rangées chez nous dans `heraliz_noyau` (nos `font/*.json` pointent vers
elles) ; les langues et polices différentes sont d'anciennes versions des nôtres.

### c) Code

- Classes du jar : 448. Mêmes champs et méthodes que notre jar fabriqué (`heraliz_monde-1.1.1-complet.jar`) : 375.
- Classes sans source chez nous : 17 : `fr.heralisz.utils.bots.AuthMeBots`, `fr.heralisz.utils.bots.BotJoueur`, `fr.heralisz.utils.bots.Bots`, `fr.heralisz.utils.bots.Bots$1`, `fr.heralisz.utils.bots.Bots$Place`, `fr.heralisz.utils.bots.Cerveau`, `fr.heralisz.utils.bots.Kit`, `fr.heralisz.utils.bots.Reglages`, `fr.heralisz.utils.bots.Reglages$Stats`, `fr.heraliz.corps.entite.AncienCorps`, `fr.heraliz.corps.entite.AncienCorpsServeur`, `fr.heraliz.ping.dev.BancLiens`, `fr.heraliz.ping.dev.BancLiens$PartieTest`, `fr.heraliz.ping.dev.BancPing`, `fr.heraliz.ping.dev.BancServeur`, `fr.heraliz.ping.dev.FauxJoueur`, `fr.heraliz.vehicules.dev.BancVehicules`.
- Classes différentes (champs ou méthodes en plus dans le jar) : 21 : `fr.heralisz.utils.cine.CineReseauServeur`, `fr.heralisz.utils.coffres.CoffresPersonnels`, `fr.heralisz.utils.coffres.CoffresPersonnels$Donnees`, `fr.heralisz.utils.inspecter.Inspecter`, `fr.heralisz.utils.spawn.DepartServeur`, `fr.heralisz.utils.zombies.AttaquesZombiesServeur`, `fr.heralisz.utils.zombies.Peuplement`, `fr.heralisz.utils.zombies.ZombiesConfig`, `fr.heraliz.carte.CarteNet`, `fr.heraliz.corps.mort.CaptureMort`, `fr.heraliz.corps.registre.Registres`, `fr.heraliz.corps.serveur.PartieServeur`, `fr.heraliz.ping.client.hud.RenduPings`, `fr.heraliz.ping.client.hud.Texte`, `fr.heraliz.ping.server.MatchsCompat`, `fr.heraliz.ping.server.PingCommandes`, `fr.heraliz.usure.KitReparation`, `fr.heraliz.usure.KitReparationServeur`, `fr.heraliz.usure.ReparationServeur`, `fr.heraliz.usure.client.ClientUsure`, `fr.heraliz.usure.client.HudUsure`.
- Ce que font ces classes (décompilées avec Vineflower 1.11.1, elles seules) :
  - `fr.heralisz.utils.bots.*` (bots de charge) : chez nous dans le module `banc`, sous `fr.heraliz.banc` ;
  - `fr.heraliz.corps.entite.AncienCorps`, `AncienCorpsServeur`, champs `ANCIEN*` de `corps.registre.Registres` : convertisseur des cadavres de l'ancien mod « corpse », retiré exprès chez nous (02/11) ;
  - `fr.heraliz.ping.dev.*`, `fr.heraliz.vehicules.dev.BancVehicules` : bancs d'essai de développement, retirés des jars ;
  - `fr.heraliz.usure.KitReparation` et `KitReparationServeur` : **en plus dans le jar** : clic droit avec un kit de réparation porté par la souris sur une arme ou une armure de l'inventaire (et l'inverse) pour la réparer ; chez nous cette façon de réparer n'existe pas ;
  - `fr.heralisz.utils.zombies.Peuplement`, `ZombiesConfig`, `fr.heraliz.usure.client.HudUsure`, `ReparationServeur`, `fr.heraliz.ping.*` (`RenduPings`, `Texte`, `MatchsCompat`, `PingCommandes`), `carte.CarteNet`, `corps.mort.CaptureMort`, `spawn.DepartServeur`, `inspecter.Inspecter`, `coffres.CoffresPersonnels`, `cine.CineReseauServeur` : anciennes versions des nôtres (méthodes et réglages rangés autrement depuis) ; rien qui manque chez nous.

## 4. `heralisz_bunker-0.13.0-complet.jar` — identique pour les identifiants

Module chez nous : `bunker` (0.13.6).

### a) Identifiants

- 21 blocs et 37 objets des deux côtés, mêmes noms ; aucune entité en plus.

### b) Fichiers

- Fichiers `assets/` et `data/` du jar : 385 (sans les `LISEZ-MOI-HeraliZ.txt` posés à la fabrication). Identiques chez nous (même empreinte sha1) : 379.
- **Manquants chez nous : 3** :
  - `data/heralisz_bunker/recipes/keycard_blue_fragments.json`
  - `data/heralisz_bunker/recipes/keycard_green_fragments.json`
  - `data/heralisz_bunker/recipes/keycard_red_fragments.json`
- **Contenu différent : 3** :
  - `assets/heralisz_bunker/lang/en_us.json`
  - `assets/heralisz_bunker/lang/fr_fr.json`
  - `assets/heralisz_bunker/lang/ru_ru.json`
- Chez nous et pas dans le jar (même espace de noms, hors `minecraft`) : 0.

Les 3 recettes manquantes (assemblage de 4 fragments en carte d'accès) ont été retirées exprès le 28/10.

### c) Code

- Classes du jar : 223. Mêmes champs et méthodes que notre jar fabriqué (`heralisz_bunker-0.13.6-complet.jar`) : 207.
- Classes sans source chez nous : 3 : `fr.heralisz.bunker.keycard.FragmentsPont`, `fr.heralisz.bunker.keycard.FragmentsPont$Assemblage`, `fr.heralisz.bunker.serveur.fragments.AssemblageFragments`.
- Classes différentes (champs ou méthodes en plus dans le jar) : 5 : `com.hbm_m.block.entity.doors.DoorDecl`, `com.hbm_m.multiblock.MultiblockStructureHelper`, `fr.heralisz.bunker.keycard.KeycardConfig`, `fr.heralisz.bunker.keycard.KeycardFragmentItem`, `fr.heralisz.bunker.keycard.KeycardNetwork`.
- Ce que font ces classes (décompilées avec Vineflower 1.11.1, elles seules) :
  - `keycard.FragmentsPont`, `serveur.fragments.AssemblageFragments`, `KeycardFragmentItem.use`, `KeycardConfig.uses` : assemblage des fragments par clic droit, retiré exprès chez nous (28/10) ;
  - `DoorDecl.getLockedMessage` : message de porte verrouillée jamais affiché, retiré chez nous (02/11) ;
  - `MultiblockStructureHelper`, `KeycardNetwork` : ancienne écriture des mêmes fonctions.

## 5. `heraliz_boss-2.2.4-complet.jar` — identique pour les identifiants

Module chez nous : `events` (2.2.5, fabriqué mais mis de côté pour l'ouverture). modIds : `heraliz_boss`, `heralisz_mammoth`, `heralisz_mere`, `heralisz_n07`, `heralisz_plague`, `heralisz_sylvorn`.

### a) Identifiants

- Mêmes blocs et objets pour les 5 boss (Ymirak 41 / 46, Mère 29 / 35, N-07 176 / 181, Peste 28 / 34, Sylvorn 9 / 15),
  mêmes entités.

### b) Fichiers

- Fichiers `assets/` et `data/` du jar : 2418 (sans les `LISEZ-MOI-HeraliZ.txt` posés à la fabrication). Identiques chez nous (même empreinte sha1) : 2403.
- **Manquants chez nous : 6** :
  - `assets/heraliz_boss/font/cyrillique_bold.ttf`
  - `assets/heraliz_boss/font/cyrillique_medium.ttf`
  - `assets/heraliz_boss/font/dmsans_bold.ttf`
  - `assets/heraliz_boss/font/dmsans_medium.ttf`
  - `assets/heraliz_boss/font/dmsans_ofl.txt`
  - `assets/heraliz_boss/font/onest_ofl.txt`
- **Contenu différent : 9** :
  - `assets/heralisz_n07/lang/en_us.json`
  - `assets/heralisz_n07/lang/fr_fr.json`
  - `assets/heralisz_n07/lang/ru_ru.json`
  - `assets/heraliz_boss/font/ui.json`
  - `assets/heraliz_boss/font/ui_bold.json`
  - `assets/heraliz_boss/font/ui_mini.json`
  - `assets/heraliz_boss/font/ui_mini_bold.json`
  - `assets/heraliz_boss/font/ui_small.json`
  - `assets/heraliz_boss/font/ui_titre.json`
- Chez nous et pas dans le jar (même espace de noms, hors `minecraft`) : 0.

### c) Code

- Classes du jar : 667. Mêmes champs et méthodes que notre jar fabriqué (`heraliz_boss-2.2.5-complet.jar`) : 653.
- Classes sans source chez nous : 8 : `fr.heralisz.n07.dev.DevCommand`, `fr.heralisz.n07.dev.Harness`, `fr.heralisz.n07.dev.ServerTest`, `fr.heralisz.n07.hall.ZombiesHallCommand`, `fr.heralisz.n07.hall.ZombiesHallCommand$1`, `fr.heralisz.n07.hall.ZombiesHallCommand$2`, `fr.heralisz.plague.dev.Harness`, `fr.heralisz.sylvorn.dev.Harness`.
- Classes différentes (champs ou méthodes en plus dans le jar) : 6 : `fr.heralisz.mammoth.dungeon.MammothCommand`, `fr.heralisz.mere.dungeon.NestCommand`, `fr.heralisz.n07.decor.DecorCommand`, `fr.heralisz.n07.dungeon.DungeonCommand`, `fr.heralisz.plague.dungeon.StationCommand`, `fr.heralisz.sylvorn.dungeon.TreeCommand`.
- Ce que font ces classes (décompilées avec Vineflower 1.11.1, elles seules) :
  - `n07.dev.*`, `plague.dev.Harness`, `sylvorn.dev.Harness` : bancs d'essai de développement, retirés ;
  - `n07.hall.ZombiesHallCommand` : salle du boss du mode Zombies, retiré avec ce mode ;
  - `MammothCommand`, `NestCommand`, `DecorCommand`, `DungeonCommand`, `StationCommand`, `TreeCommand` : mêmes commandes, messages écrits autrement chez nous (traduits).

## 6. `heraliz_loot-0.12.1-complet.jar` — identique pour les identifiants

Module chez nous : `loot` (0.16.0).

### a) Identifiants

- 435 blocs et 435 objets des deux côtés, mêmes noms (tous les conteneurs de loot posés sur la map existent chez nous).

### b) Fichiers

- Fichiers `assets/` et `data/` du jar : 1572 (sans les `LISEZ-MOI-HeraliZ.txt` posés à la fabrication). Identiques chez nous (même empreinte sha1) : 1555.
- **Manquants chez nous : 0**.
- **Contenu différent : 17** :
  - `assets/heraliz_loot/lang/en_us.json`
  - `assets/heraliz_loot/lang/fr_fr.json`
  - `assets/heraliz_loot/lang/ru_ru.json`
  - `data/heraliz_loot/coffres/soins.json`
  - `data/heraliz_loot/coffres/soins1.json`
  - `data/heraliz_loot/coffres/soins2.json`
  - `data/heraliz_loot/coffres/soins3.json`
  - `data/heraliz_loot/coffres/soins4.json`
  - `data/heraliz_loot/coffres/soins5.json`
  - `data/heraliz_loot/coffres/soins6.json`
  - `data/heraliz_loot/coffres/t1.json`
  - `data/heraliz_loot/coffres/t2.json`
  - `data/heraliz_loot/coffres/t3.json`
  - `data/heraliz_loot/coffres/t4.json`
  - `data/heraliz_loot/coffres/t5.json`
  - `data/heraliz_loot/coffres/t6.json`
  - `data/heraliz_loot/coffres/t7.json`
- Chez nous et pas dans le jar (même espace de noms, hors `minecraft`) : 8 :
  - `assets/heraliz_loot/font/ui_badge.json`
  - `assets/heraliz_loot/lod/conteneurs_lod.txt`
  - `assets/heraliz_loot/particles/etoile.json`
  - `assets/heraliz_loot/particles/etoile_or.json`
  - `data/heraliz_loot/coffres/zombies.json`
  - `data/heraliz_loot/coffres/zombies_militaire.json`
  - `data/tacz/recipes/ammo_workbench.json`
  - `data/tacz/recipes/attachment_workbench.json`

Les tables `coffres/*.json` et les langues différentes sont d'anciennes versions des nôtres (refaites depuis par
`outils/loot_armes.py`).

### c) Code

- Classes du jar : 180. Mêmes champs et méthodes que notre jar fabriqué (`heraliz_loot-0.16.0-complet.jar`) : 160.
- Classes sans source chez nous : 0.
- Classes différentes (champs ou méthodes en plus dans le jar) : 12 : `fr.heraliz.loot.CoffreMenu`, `fr.heraliz.loot.Commandes`, `fr.heraliz.loot.CommandesRarete`, `fr.heraliz.loot.RotationLoot`, `fr.heraliz.loot.RotationLoot$Salle`, `fr.heraliz.loot.SaisonLoot`, `fr.heraliz.loot.TablesCoffres`, `fr.heraliz.loot.TablesCoffres$Entree`, `fr.heraliz.loot.TablesCoffres$Table`, `fr.heraliz.loot.client.BadgePalier`, `fr.heraliz.loot.client.ModeleConteneur`, `fr.heraliz.loot.client.PartieClient`.
- Ce que font ces classes (décompilées avec Vineflower 1.11.1, elles seules) :
  - aucune classe sans source ; les classes « différentes » (`RotationLoot`, `TablesCoffres`, `Commandes`, `CoffreMenu`, `BadgePalier`, `ModeleConteneur`…) sont d'anciennes versions des nôtres : rien qui manque chez nous.

## 7. `heralizcasino-2.5.1-complet.jar` — identique pour les identifiants

Module chez nous : `boutique` (casino 2.7.0).

### a) Identifiants

- 81 blocs et 87 objets du jar tous chez nous ; nous avons en plus `heralizcasino:atm` (distributeur).

### b) Fichiers

- Fichiers `assets/` et `data/` du jar : 405 (sans les `LISEZ-MOI-HeraliZ.txt` posés à la fabrication). Identiques chez nous (même empreinte sha1) : 391.
- **Manquants chez nous : 6** :
  - `assets/heralizcasino/font/cyrillique_bold.ttf`
  - `assets/heralizcasino/font/cyrillique_medium.ttf`
  - `assets/heralizcasino/font/dmsans_bold.ttf`
  - `assets/heralizcasino/font/dmsans_medium.ttf`
  - `assets/heralizcasino/font/dmsans_ofl.txt`
  - `assets/heralizcasino/font/onest_ofl.txt`
- **Contenu différent : 8** :
  - `assets/heralizcasino/font/ui.json`
  - `assets/heralizcasino/font/ui_bold.json`
  - `assets/heralizcasino/font/ui_small.json`
  - `assets/heralizcasino/lang/en_us.json`
  - `assets/heralizcasino/lang/fr_fr.json`
  - `assets/heralizcasino/lang/ru_ru.json`
  - `assets/heralizcasino/textures/machine/caisse.png`
  - `assets/heralizcasino/textures/machine/caisse_glow.png`
- Chez nous et pas dans le jar (même espace de noms, hors `minecraft`) : 4 :
  - `assets/heralizcasino/blockstates/atm.json`
  - `assets/heralizcasino/models/block/atm.json`
  - `assets/heralizcasino/models/item/atm.json`
  - `assets/heralizcasino/textures/block/atm_particle.png`

### c) Code

- Classes du jar : 399. Mêmes champs et méthodes que notre jar fabriqué (`heraliz_boutique-1.5.5-complet.jar`) : 383.
- Classes sans source chez nous : 1 : `fr.heraliz.casino.client.render.scene.machines.FruitsDesign`.
- Classes différentes (champs ou méthodes en plus dans le jar) : 2 : `fr.heraliz.casino.command.CasinoCommand`, `fr.heraliz.casino.data.CasinoStats`.
- Ce que font ces classes (décompilées avec Vineflower 1.11.1, elles seules) :
  - `client.render.scene.machines.FruitsDesign` : rendu de la machine « Fruits d'Or », disparue du jeu, retiré exprès (02/11) ;
  - `CasinoCommand`, `CasinoStats` : ancienne écriture des mêmes commandes.

## 8. `cfm`, `framework`, `geckolib222` — identiques

Même nom de fichier et même empreinte sha1 que les jars de `serveur/mods-tiers` : rien à faire.

## 9. Le dossier de l'instance complète (46 jars)

| Jar | modId (version) | Chez nous | Verdict |
|---|---|---|---|
| `airstrike-1.9.1-complet.jar` | `airstrike` 1.9.1 | module `monde` (1.9.1) | même numéro de version dans `mods.toml` (code regroupé depuis dans le jar du module, il a pu changer) |
| `caracrea-1.6.4-complet.jar` | `caracrea` 1.6.4 | module `voix` (1.6.6) | ancienne version |
| `cfm-forge-1.20.1-7.0.0-pre36PATCH.jar` | `cfm` 7.0.0-pre36 | `serveur/mods-tiers` | **identique** |
| `cloth-config-11.1.136-forge.jar` | `cloth_config` 11.1.136 | `serveur/mods-tiers` | **identique** |
| `coroutil-forge-1.20.1-1.3.7.jar` | `coroutil` 1.20.1-1.3.7 | `serveur/mods-tiers` | **identique** |
| `crac-1.22.0-beta-complet.jar` | `crac` 1.22.0-beta | module `bases` (1.36.0-beta) | ancienne version |
| `curios-forge-5.14.1+1.20.1.jar` | `curios` — | `serveur/mods-tiers` | **identique** |
| `dayzhotbar-1.5.0.jar` | `dayzhotbar` 1.5.0 | module `interface` (1.5.0) | même numéro de version dans `mods.toml` (code regroupé depuis dans le jar du module, il a pu changer) |
| `embeddium-0.3.31+mc1.20.1.jar` | `embeddium` 0.3.31+mc1.20.1 | `client/mods-tiers` | **identique** |
| `entityculling-forge-1.11.2-mc1.20.1.jar` | `entityculling` 1.11.2 | `client/mods-tiers` | **identique** |
| `ferritecore-6.0.1-forge.jar` | `ferritecore` — | `serveur/mods-tiers` | **identique** |
| `framework-forge-1.20.1-0.7.6.jar` | `framework` 0.7.6 | `serveur/mods-tiers` | **identique** |
| `geckolib222-forge-1.20.1-4.4.7.jar` | `geckolib` 4.4.7 | `serveur/mods-tiers` | **identique** |
| `heralisz_backpack-3.0.1-beta.4-heralisz21-complet.jar` | `backpacked` 3.0.0-beta.4 | module `sacs` (3.0.0-beta.4) | même numéro de version dans `mods.toml` (code regroupé depuis dans le jar du module, il a pu changer) |
| `heralisz_bunker-0.13.0-complet.jar` | `heralisz_bunker` 0.13.0 | module `bunker` (0.13.6) | ancienne version |
| `heralisz_matchs-0.15.4-complet.jar` | `heralisz_matchs` 0.15.4 | aucun module | **manque chez nous** — retiré exprès (seul ClashWar gardé, module `clashwar`) |
| `heralisz_utils-2.26.0-complet.jar` | `heralisz_utils` 2.26.0 | module `monde` (2.26.1) | ancienne version |
| `heraliz_armory-0.16.1-complet.jar` | `heraliz_armory` 0.16.1 | module `equipement` (0.16.1) | même numéro de version dans `mods.toml` (code regroupé depuis dans le jar du module, il a pu changer) |
| `heraliz_boss-2.2.4-complet.jar` | `heraliz_boss` 2.2.4 | module `events` (2.2.5, mis de côté) | ancienne version |
| `heraliz_clansetbases-3.6.8-complet.jar` | `heraliz_clansetbases` 3.6.8 | module `clans` (3.14.1) | ancienne version |
| `heraliz_composants-1.4.1-complet.jar` | `heraliz_composants` 1.4.1 | module `equipement` (1.4.1) | même numéro de version dans `mods.toml` (code regroupé depuis dans le jar du module, il a pu changer) |
| `heraliz_decors-1.30.3-complet.jar` | `heraliz_decors` 1.30.3 | module `monde` (1.12.1) | **différent : 252 blocs manquent chez nous** |
| `heraliz_emote-2.8.6-complet.jar` | `heraliz_emote` 2.8.6 | module `emotes` (2.10.2) | ancienne version |
| `heraliz_extraction-1.5.4-complet.jar` | `heraliz_extraction` 1.5.4 | module `monde` (1.5.4) | même numéro de version dans `mods.toml` (code regroupé depuis dans le jar du module, il a pu changer) |
| `heraliz_grades-2.7.2-complet.jar` | `heraliz_grades` 2.7.2 | module `boutique` (2.10.1) | ancienne version |
| `heraliz_interface-1.9.5.jar` | `heraliz_interface` 1.9.5 | module `interface` (1.9.5) | même numéro de version dans `mods.toml` (code regroupé depuis dans le jar du module, il a pu changer) |
| `heraliz_loot-0.12.1-complet.jar` | `heraliz_loot` 0.12.1 | module `loot` (0.16.0) | ancienne version |
| `heraliz_quetes-1.11.0-complet.jar` | `heraliz_quetes` 1.11.0 | module `pnj` (1.21.0) | ancienne version |
| `heraliz_tactique-2.37.0-complet.jar` | `heraliz_tactique` 2.37.0 | module `armes` (2.54.2) | ancienne version |
| `heraliz_wonder-1.4.1-complet.jar` | `heraliz_wonder` 1.4.1 | aucun module | **manque chez nous** — retiré exprès (armes du mode Zombies) |
| `heralizcasino-2.5.1-complet.jar` | `heralizcasino` 2.5.1 | module `boutique` (2.7.0) | ancienne version |
| `heralizcrates-2.21.0-complet.jar` | `heralizcrates` 2.21.0 | module `boutique` (2.21.0) | même numéro de version dans `mods.toml` (code regroupé depuis dans le jar du module, il a pu changer) |
| `Kiwi-1.20.1-Forge-11.8.30.jar` | `kiwi` — | `serveur/mods-tiers` | **identique** |
| `MAtmos-7.1-forge-1.20.1.jar` | `matmos_tct` 7.1 | `serveur/mods-tiers` | **identique** |
| `memoryleakfix-forge-1.17+-1.1.5.jar` | `memoryleakfix` 1.1.5 | `serveur/mods-tiers` | **identique** |
| `modernfix-forge-5.27.85+mc1.20.1.jar` | `modernfix` 5.27.85+mc1.20.1 | `serveur/mods-tiers` | **identique** |
| `MutantsZombies-0.9.8-Forge-mc1.20.1.jar` | `mutantszombies` 0.9.8-Forge-mc1.20.1 | `serveur/mods-tiers` | **identique** |
| `packetfixer-forge-2.0.0-1.19-to-1.20.1.jar` | `packetfixer` 2.0.0 | `serveur/mods-tiers` | **identique** |
| `PassableFoliage-1.20.1-forge-8.2.1.jar` | `passablefoliage` — | `serveur/mods-tiers` | **identique** |
| `player-animation-lib-forge-1.0.2-rc1+1.20.jar` | `playeranimator` 1.0.2-rc1+1.20 | `serveur/mods-tiers` | **identique** |
| `reanimation-1.20.1-1.16.6-complet.jar` | `reanimation` 1.16.6 | module `survie` (1.16.10) | ancienne version |
| `tacz-1.20.1-1.1.8-hotfix.jar` | `tacz` — | `serveur/mods-tiers` | **identique** |
| `taczaddon-1.1.8.1-hotfix6-forge-1.20.1.jar` | `taczaddon` 1.1.8.1 | `serveur/mods-tiers` | **identique** |
| `TCTcore-1.20.1.jar` | `tctcore` 2.0 | `serveur/mods-tiers` | **identique** |
| `ywzj_vehicle-1.20.1-0.5.10-fuse.jar` | `ywzj_vehicle` 0.5.10 | `serveur/mods-tiers` | **identique** |
| `zombieawareness-1.20.1-1.13.1.jar` | `zombieawareness` — | `serveur/mods-tiers` | **identique** |

Bilan : 22 jars d'autres auteurs identiques (même fichier, même empreinte) ; 22 de nos mods
présents chez nous (presque tous en version plus récente ; Décors à part, voir section 1) ; 2 sans
module chez nous, retirés exprès : `heralisz_matchs` et `heraliz_wonder`.

Chez nous et pas dans l'instance : `heraliz_noyau` (obligatoire pour tous nos mods depuis la v2), `heraliz_clashwar`,
`heraliz_evenements`, `renderlib`. Aucun jar de `serveur/mods-tiers` ni de `client/mods-tiers` ne manque à
l'instance. Plusieurs de nos anciens jars (`airstrike`, `heraliz_extraction`, `heraliz_armory`, `heraliz_composants`,
`heralisz_utils`, `heraliz_decors`, `dayzhotbar`…) sont regroupés chez nous dans un seul jar par module
(`heraliz_monde`, `heraliz_equipement`, `heraliz_interface`) : mêmes modIds, donc mêmes identifiants.

## 10. Ce qu'il faudrait faire (rien n'a été fait)

1. **Décors** : reprendre dans le module `monde` les 252 blocs de `heraliz_decors` 1.30.3, au même identifiant (règle
   d'or 8), avec leurs modèles, textures, tags et les 63 classes ; ajouter le russe ; décider pour le tag mural (motif
   changeable par tout joueur) et la flaque toxique (poison décidé dans le bloc). Les sources de ce jar ne sont pas
   dans le dépôt : les demander à qui a fait la map plutôt que repartir du jar décompilé.
2. **Matchs** : savoir quels blocs `heralisz_matchs:*` sont posés sur la map (workflow « Inventaire de la carte »),
   puis les remplacer en jeu par des décors à nous, ou garder ces seuls identifiants dans un de nos modules.
3. **Train** : récupérer `reseau_map_v2_4000.json` (et les deux autres réseaux si besoin) pour la nouvelle map.
4. **Kit de réparation** : dire si la réparation par clic droit dans l'inventaire doit revenir.

Rien n'a été testé en jeu ; aucun monde n'a été ouvert.
