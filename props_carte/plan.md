# Plan des conteneurs de loot : modèles HeraliZ Loot T1 à T6

Consigne : chaque modèle T1 à T6 de HeraliZ Loot posé sur la carte, en remplaçant des conteneurs déjà posés par d'autres modèles du même palier (même gabarit, même orientation, pièce cohérente). T7 réservé aux événements : rien n'est posé. Ni meubles cfm. Familles (vivres, soins, munitions) : étape à part, plus bas.

**Modèles T1 à T6 : 366 au total. Posés avant le plan : 365 (99,7 %) ; après la diversification : 365 (99,7 %).**

2382 changements en 504 lots : diversification 245 (cohérence 68, couverture 16, équilibrage 161), familles 2137.
Conteneurs T1 à T6 sur la carte : 29236 (245 changent de modèle, soit 0,8 %). Gardés tels quels : 0 conteneurs avec des données (inventaire).

| palier | modèles | posés avant | après | conteneurs | le plus répété avant | après | 3 plus posés avant → après | minimum par modèle |
|---|---|---|---|---|---|---|---|---|
| T1 | 111 | 111 | 111 | 18749 | t1_range_chaussures ×1604 | t1_range_chaussures ×1551 | 17 % → 16 % | 8 |
| T2 | 58 | 57 | 57 | 7091 | t2_frigo ×669 | t2_frigo ×669 | 20 % → 20 % | 1-6 |
| T3 | 50 | 50 | 50 | 1255 | t3_bureau_police ×77 | t3_jerricans_essence ×74 | 18 % → 17 % | 4 |
| T4 | 51 | 51 | 51 | 1577 | t4_futs ×63 | t4_futs ×51 | 10 % → 9 % | 4 |
| T5 | 47 | 47 | 47 | 465 | t5_mitrailleuse_trepied ×23 | t5_mitrailleuse_trepied ×16 | 13 % → 10 % | 3 |
| T6 | 49 | 49 | 49 | 99 | t6_fut_biologique ×9 | t6_fut_biologique ×5 | 20 % → 13 % | 1-2 |

Posés mais sous leur minimum (places cohérentes trop rares) : `t1_eta_3` ×4 / 8, `t1_loot_civil_19` ×4 / 8, `t1_meuble_vasque` ×2 / 8, `t2_loot_campagne_1` ×1 / 6, `t6_boite_noire` ×1 / 2, `t6_caisse_lingots` ×1 / 2, `t6_caisson_plomb` ×1 / 2, `t6_coffre_sol` ×1 / 2, `t6_malle_bijoux` ×1 / 2, `t6_valise_diplomatique` ×1 / 2, `t6_valise_fonds` ×1 / 2

### Modèles encore absents après la diversification (1)

- `t2_loot_campagne_11` (T2, objet) : aucun extérieur « cimetiere » repéré sur la carte (ni conteneur ni décor de ce genre autour des lieux). Aucune case libre cohérente non plus : à poser à la main.

Incohérences sans remplaçant du même palier (laissées) : ne va pas avec la pièce ×140, prop d'intérieur à ciel ouvert ×44, prop d'extérieur dans une pièce fermée ×10, doublon : machine_coudre en trop dans la pièce ×2, doublon : frigo en trop dans la pièce ×1

Cohérence : ne va pas avec la pièce ×48, prop d'intérieur à ciel ouvert ×15, prop d'extérieur dans une pièce fermée ×5

Remplacements les plus fréquents : t1_range_chaussures → t1_matelas ×52, t4_portant_brelages → t4_lit_picot ×4, t3_boucliers → t3_mirador_coffre ×4, t2_sac_randonnee → t2_sacoche ×4, t2_sac_randonnee → t2_sac_sport ×4, t2_boite_aux_lettres → t2_brouette ×4, t4_futs → t4_caisse_filet ×4, t2_boite_aux_lettres → t2_sacoche ×4, t5_loot_militaire_10 → t5_casier_gris_9 ×3, t3_jerricans_essence → t3_herse_cloutee ×3, t5_mitrailleuse_trepied → t5_reservoir_souple ×3, t4_loot_crash_4 → t4_loot_militaire_13 ×2

## Étape « familles » : vivres, soins, munitions (demande du 24/10)

Jouée (option --familles). Avant : 263 conteneurs de familles pour 29236 conteneurs T1 à T6 (0,90 pour 100). Posés par le plan : 2137 (8,2 pour 100 avec ceux d'avant). Remplis en même temps, en moyenne (part d'actifs de leur zone) : 51 avant, 438 après.

Visé pour 100 conteneurs du lieu : vivres 3, soins 3, munitions 2 ; lieux de niveau 4 et plus : vivres 2, soins 3,5, munitions 5. Niveau du lieu : palier le plus haut qui fait au moins 5 % de ses conteneurs (réglés à la main : aerodrome 6) ; posés au niveau du lieu pour 50 %, un et deux niveaux en dessous pour le reste, ramenés au plus haut niveau de la famille (vivres 4, soins 3, munitions 6). Parts d'actifs des zones inchangées : le loot T1 à T6 de chaque zone ne baisse pas, les conteneurs de familles s'ajoutent et tournent avec leur zone.

| lieu | niveau | conteneurs | vivres avant → après (visé) | soins avant → après (visé) | munitions avant → après (visé) | remplis en moyenne |
|---|---|---|---|---|---|---|
| valmont | 2 | 8980 | 22 → 269 (269) N1×145 N2×124 | 26 → 269 (269) N1×140 N2×129 | 13 → 180 (180) N1×83 N2×97 | 12,8 → 138,9 |
| tcherno | 2 | 4160 | 33 → 125 (125) N1×61 N2×47 N3×17 | 13 → 125 (125) N1×66 N2×59 | 26 → 83 (83) N1×33 N2×49 N4×1 | 15,1 → 75,4 |
| marbois | 3 | 2301 | 1 → 69 (69) N1×11 N2×24 N3×34 | 17 → 69 (69) N1×21 N2×20 N3×28 | 20 → 46 (46) N1×7 N2×13 N3×26 | 7,7 → 35,1 |
| belcourt | 2 | 1593 | 4 → 48 (48) N1×26 N2×22 | 10 → 48 (48) N1×27 N2×21 | 2 → 29 (32) N1×13 N2×16 · manque 3 | 3,5 → 24,4 |
| port | 2 | 1366 | 0 → 41 (41) N1×20 N2×21 | 6 → 41 (41) N1×20 N2×21 | 2 → 27 (27) N1×12 N2×15 | 1,4 → 20,7 |
| aerodrome | 6 | 1245 | 3 → 25 (25) N2×4 N3×9 N4×12 | 4 → 44 (44) N1×6 N2×17 N3×21 | 1 → 62 (62) N2×1 N4×9 N5×21 N6×31 | 1,1 → 12,8 |
| mirage | 2 | 1193 | 18 → 36 (36) N1×9 N2×27 | 2 → 36 (36) N1×18 N2×18 | 1 → 24 (24) N1×11 N2×13 | 3,1 → 17,5 |
| clairval | 2 | 1108 | 5 → 33 (33) N1×19 N2×14 | 3 → 33 (33) N1×17 N2×16 | 0 → 22 (22) N1×11 N2×11 | 1,8 → 17,2 |
| gaz | 2 | 1074 | 1 → 32 (32) N1×16 N2×16 | 2 → 32 (32) N1×17 N2×15 | 0 → 19 (21) N1×9 N2×10 · manque 2 | 0,7 → 16 |
| campagne | 2 | 762 | 0 → 23 (23) N1×11 N2×12 | 0 → 23 (23) N1×11 N2×12 | 0 → 15 (15) N1×7 N2×8 | 0 → 11,6 |
| radio | 5 | 586 | 0 → 12 (12) N2×2 N3×4 N4×6 | 0 → 21 (21) N1×3 N2×7 N3×11 | 1 → 29 (29) N2×1 N3×4 N4×10 N5×14 | 0,1 → 6,4 |
| silo | 5 | 549 | 0 → 11 (11) N2×2 N3×4 N4×5 | 1 → 19 (19) N1×3 N2×7 N3×9 | 1 → 27 (27) N2×1 N3×4 N4×9 N5×13 | 0,3 → 6,5 |
| prison | 5 | 524 | 1 → 10 (10) N1×1 N2×1 N3×3 N4×5 | 3 → 18 (18) N1×3 N2×7 N3×8 | 2 → 26 (26) N2×2 N3×4 N4×8 N5×12 | 1,3 → 7,6 |
| arctique | 6 | 422 | 0 → 8 (8) N2×1 N3×3 N4×4 | 1 → 15 (15) N1×2 N2×6 N3×7 | 1 → 21 (21) N2×1 N4×3 N5×7 N6×10 | 0,3 → 4 |
| v_1 | 2 | 272 | 2 → 8 (8) N1×3 N2×5 | 0 → 8 (8) N1×4 N2×4 | 0 → 5 (5) N1×2 N2×3 | 0,3 → 3,9 |
| faille | 3 | 267 | 7 → 8 (8) N3×8 | 4 → 6 (8) N1×1 N2×5 · manque 2 | 0 → 5 (5) N1×1 N2×2 N3×2 | 1,3 → 2,5 |
| v_16 | 2 | 215 | 0 → 6 (6) N1×3 N2×3 | 0 → 6 (6) N1×3 N2×3 | 0 → 4 (4) N1×2 N2×2 | 0 → 3,1 |
| v_10 | 2 | 205 | 0 → 6 (6) N1×3 N2×3 | 0 → 6 (6) N1×3 N2×3 | 0 → 4 (4) N1×2 N2×2 | 0 → 3,1 |
| v_11 | 2 | 191 | 0 → 6 (6) N1×3 N2×3 | 0 → 6 (6) N1×3 N2×3 | 0 → 4 (4) N1×2 N2×2 | 0 → 3,5 |
| v_13 | 2 | 170 | 0 → 5 (5) N1×2 N2×3 | 0 → 5 (5) N1×2 N2×3 | 0 → 3 (3) N1×1 N2×2 | 0 → 2,3 |
| v_7 | 2 | 170 | 0 → 5 (5) N1×2 N2×3 | 0 → 5 (5) N1×2 N2×3 | 0 → 1 (3) N2×1 · manque 2 | 0 → 2,5 |
| routes | 4 | 168 | 0 → 3 (3) N3×1 N4×2 | 0 → 6 (6) N1×1 N2×2 N3×3 | 0 → 8 (8) N2×1 N3×3 N4×4 | 0 → 1,9 |
| v_2 | 2 | 168 | 0 → 5 (5) N1×2 N2×3 | 0 → 5 (5) N1×2 N2×3 | 0 → 1 (3) N2×1 · manque 2 | 0 → 2 |
| v_5 | 2 | 163 | 0 → 5 (5) N1×2 N2×3 | 0 → 5 (5) N1×2 N2×3 | 0 → 3 (3) N1×1 N2×2 | 0 → 2,3 |
| v_14 | 2 | 163 | 0 → 5 (5) N1×2 N2×3 | 0 → 5 (5) N1×2 N2×3 | 0 → 0 (3) · manque 3 | 0 → 1,8 |
| v_17 | 2 | 156 | 0 → 5 (5) N1×2 N2×3 | 0 → 5 (5) N1×2 N2×3 | 1 → 3 (3) N1×1 N2×2 | 0,1 → 2,6 |
| v_9 | 2 | 154 | 0 → 5 (5) N1×2 N2×3 | 0 → 5 (5) N1×2 N2×3 | 0 → 2 (3) N2×2 · manque 1 | 0 → 2,1 |
| v_3 | 2 | 153 | 0 → 5 (5) N1×2 N2×3 | 0 → 5 (5) N1×2 N2×3 | 0 → 2 (3) N2×2 · manque 1 | 0 → 2,7 |
| v_6 | 2 | 125 | 1 → 4 (4) N1×2 N2×2 | 0 → 4 (4) N1×2 N2×2 | 0 → 2 (2) N1×1 N2×1 | 0,2 → 1,9 |
| v_0 | 2 | 123 | 0 → 4 (4) N1×2 N2×2 | 0 → 4 (4) N1×2 N2×2 | 0 → 2 (2) N1×1 N2×1 | 0 → 1,9 |
| v_15 | 3 | 109 | 0 → 3 (3) N2×1 N3×2 | 0 → 3 (3) N2×1 N3×2 | 0 → 2 (2) N2×1 N3×1 | 0 → 0,9 |
| v_4 | 3 | 108 | 0 → 3 (3) N2×1 N3×2 | 0 → 3 (3) N2×1 N3×2 | 0 → 2 (2) N2×1 N3×1 | 0 → 0,9 |
| v_12 | 2 | 87 | 0 → 2 (3) N2×2 · manque 1 | 0 → 3 (3) N1×1 N2×2 | 0 → 1 (2) N2×1 · manque 1 | 0 → 1 |
| v_8 | 2 | 74 | 1 → 2 (2) N2×2 | 0 → 2 (2) N1×1 N2×1 | 0 → 1 (1) N2×1 | 0,1 → 0,8 |
| jetee | 5 | 67 | 0 → 1 (1) N4×1 | 0 → 2 (2) N2×1 N3×1 | 0 → 3 (3) N4×1 N5×2 | 0 → 0,5 |
| bunker | 6 | 55 | 0 → 0 (1) · manque 1 | 1 → 1 (2) N3×1 · manque 1 | 0 → 1 (3) N6×1 · manque 2 | 0,1 → 0,1 |
| halte_aerodrome | 2 | 10 | 0 → 0 (0) | 0 → 0 (0) | 0 → 0 (0) | 0 → 0 |

Places : dans une pièce qui leur va 1849, dehors 285, petite pièce vide d'une maison (soins) 3.
Modèles posés : `munitions1_loot_munitions_1` ×99, `munitions1_loot_munitions_2` ×93, `munitions2_loot_munitions_3` ×88, `munitions2_loot_munitions_5` ×131, `munitions3_loot_munitions_4` ×32, `munitions4_loot_munitions_6` ×44, `munitions5_loot_munitions_7` ×69, `munitions6_loot_munitions_8` ×42, `nourriture1_loot_nourriture_10` ×301, `nourriture2_loot_nourriture_4` ×342, `nourriture3_caisse_nourriture` ×5, `nourriture3_loot_nourriture_1` ×31, `nourriture3_loot_nourriture_2` ×26, `nourriture4_loot_nourriture_3` ×34, `soins1_loot_medical_1` ×332, `soins2_loot_medical_3` ×369, `soins2_loot_medical_4` ×4, `soins2_loot_medical_7` ×6, `soins3_loot_medical_10` ×10, `soins3_loot_medical_2` ×57, `soins3_loot_medical_8` ×11, `soins3_loot_medical_9` ×11.
Chaque conteneur rejoint dans le jeu la zone de loot de son niveau la plus proche (102 zones touchées, aucune zone créée : jamais plus de 60 blocs d'une zone de son niveau).
Manque 22 conteneurs (pas assez de places libres cohérentes près d'une zone du bon niveau).

