## Recherche de « spawnbangerdelamort » sur le Ptero (lecture seule)

Monde en service (server.properties du Ptero) : `dayz_map`. Dossiers fouillés : racine, `plugins/`, `schematics/`, `config/`, `dayz_map/generated/` (22453 chemins).

Fichiers trouvés :
- `/plugins/WorldEdit/schematics/spawnbangerdelamort.schem`

Choisi : `/plugins/WorldEdit/schematics/spawnbangerdelamort.schem` (135186 octets).

## Arrivée des joueurs (monde `dayz_map` du Ptero, lecture seule)

- `dayz_map/dimensions/heralisz/spawn/` existe déjà (20 fichiers de régions) : la case « deposer_spawn » REFUSERA de l'écraser. Si c'est l'ancien spawn à remplacer : le supprimer depuis le panel, serveur arrêté, avant le dépôt.
- décor réglé en jeu : réveil en 43.5 249.0 98.5 dans `heralisz:spawn` (il passe avant celui du mod ; vérifier qu'il tombe bien dans la grotte : `/cinematique info`).

## Schematic `1-spawnbangerdelamort.schem`

- fichier : 135186 octets, Sponge v2 (.schem), DataVersion 3465
- dimensions : **145 × 93 × 147** (largeur x, hauteur y, longueur z) ; 619779 blocs posés (hors air), 148 types de blocs, 447 états
- entités : 0
- entités de blocs (coffres, panneaux...) : 249 — `minecraft:barrel` 138, `minecraft:beehive` 49, `minecraft:chest` 19, `minecraft:lectern` 13, `cfm:trampoline` 9, `minecraft:furnace` 5, `tacz:target` 4, `minecraft:jukebox` 4, `minecraft:blast_furnace` 3, `minecraft:smoker` 3, `minecraft:campfire` 1, `minecraft:bell` 1
- blocs de nos mods : aucun
- blocs d'autres mods : `cfm:chaise_3` 9, `cfm:trampoline` 9, `tacz:target` 8, `cfm:wooden_pallet_2_a` 7, `cfm:chaise_5` 6, `cfm:hospital_stand` 4, `cfm:hospital_cupboard` 4, `cfm:chaise_4` 3, `cfm:zombie_chainlinkfence` 3, `cfm:hospital_curtain` 2, `cfm:chaise_6` 2, `cfm:hospital_bed` 2, `cfm:zombie_barricade_2` 2, `cfm:fluorescent_light_on` 2, `cfm:zombie_cone` 2, `cfm:bank_atm_shop` 1, `cfm:safe_sentinel_1` 1, `cfm:hospital_chair_block` 1, `cfm:hospital_wheelchair` 1, `cfm:safe_planque_1` 1, `cfm:arkaniaz_corpse_1` 1, `cfm:loot_food_6` 1, `cfm:dungeon_skeleton` 1
- cases où l'on marche : 35086, dont 14032 dans la plus grande zone d'un seul tenant

<details><summary>Palette complète (148 types)</summary>

| bloc | nombre |
|---|---|
| `minecraft:stone` | 450408 |
| `minecraft:stone_bricks` | 40240 |
| `minecraft:gravel` | 24021 |
| `minecraft:dirt` | 19856 |
| `minecraft:moss_block` | 10197 |
| `minecraft:mossy_cobblestone` | 10125 |
| `minecraft:grass_block` | 9364 |
| `minecraft:mossy_stone_bricks` | 8369 |
| `minecraft:andesite` | 7152 |
| `minecraft:diorite` | 6326 |
| `minecraft:oak_leaves` | 5945 |
| `minecraft:black_stained_glass` | 4561 |
| `minecraft:cobblestone` | 4381 |
| `minecraft:tuff` | 4307 |
| `minecraft:granite` | 3763 |
| `minecraft:polished_andesite` | 1594 |
| `minecraft:andesite_wall` | 942 |
| `minecraft:snow` | 854 |
| `minecraft:stone_slab` | 742 |
| `minecraft:barrier` | 610 |
| `minecraft:stone_brick_slab` | 430 |
| `minecraft:vine` | 382 |
| `minecraft:coarse_dirt` | 323 |
| `minecraft:cobblestone_slab` | 322 |
| `minecraft:spruce_fence` | 314 |
| `minecraft:tall_grass` | 260 |
| `minecraft:spruce_trapdoor` | 209 |
| `minecraft:andesite_slab` | 203 |
| `minecraft:oak_planks` | 189 |
| `minecraft:coal_ore` | 170 |
| `minecraft:mossy_cobblestone_slab` | 167 |
| `minecraft:acacia_slab` | 164 |
| `minecraft:stripped_oak_wood` | 159 |
| `minecraft:polished_andesite_slab` | 153 |
| `minecraft:cave_vines_plant` | 153 |
| `minecraft:dirt_path` | 142 |
| `minecraft:barrel` | 138 |
| `minecraft:stone_brick_stairs` | 128 |
| `minecraft:water` | 114 |
| `minecraft:oak_log` | 108 |
| `minecraft:acacia_trapdoor` | 94 |
| `minecraft:copper_ore` | 85 |
| `minecraft:stone_stairs` | 81 |
| `minecraft:lantern` | 78 |
| `minecraft:moss_carpet` | 76 |
| `minecraft:mossy_stone_brick_slab` | 75 |
| `minecraft:stone_brick_wall` | 72 |
| `minecraft:lime_concrete` | 59 |
| `minecraft:spruce_planks` | 51 |
| `minecraft:warped_trapdoor` | 50 |
| `minecraft:gray_wool` | 50 |
| `minecraft:beehive` | 49 |
| `minecraft:acacia_log` | 48 |
| `minecraft:cave_vines` | 45 |
| `minecraft:lily_of_the_valley` | 41 |
| `minecraft:composter` | 36 |
| `minecraft:spruce_fence_gate` | 33 |
| `minecraft:black_carpet` | 33 |
| `minecraft:jungle_trapdoor` | 33 |
| `minecraft:polished_diorite` | 33 |
| `minecraft:black_concrete_powder` | 32 |
| `minecraft:birch_trapdoor` | 30 |
| `minecraft:gray_concrete` | 26 |
| `minecraft:flower_pot` | 25 |
| `minecraft:seagrass` | 24 |
| `minecraft:oxeye_daisy` | 24 |
| `minecraft:crimson_trapdoor` | 24 |
| `minecraft:azure_bluet` | 24 |
| `minecraft:iron_trapdoor` | 20 |
| `minecraft:chest` | 19 |
| `minecraft:iron_bars` | 19 |
| `minecraft:wheat` | 19 |
| `minecraft:farmland` | 19 |
| `minecraft:rail` | 18 |
| `minecraft:cobblestone_wall` | 17 |
| `minecraft:quartz_slab` | 15 |
| `minecraft:stripped_spruce_log` | 15 |
| `minecraft:mossy_cobblestone_stairs` | 14 |
| `minecraft:lever` | 14 |
| `minecraft:lectern` | 13 |
| `minecraft:mossy_cobblestone_wall` | 12 |
| `minecraft:acacia_stairs` | 12 |
| `minecraft:powered_rail` | 12 |
| `minecraft:oak_trapdoor` | 10 |
| `minecraft:mossy_stone_brick_stairs` | 10 |
| `cfm:chaise_3` | 9 |
| `cfm:trampoline` | 9 |
| `minecraft:oak_wood` | 8 |
| `tacz:target` | 8 |
| `minecraft:ladder` | 8 |
| `cfm:wooden_pallet_2_a` | 7 |
| `minecraft:cyan_terracotta` | 7 |
| `minecraft:azalea` | 7 |
| `cfm:chaise_5` | 6 |
| `minecraft:iron_block` | 6 |
| `minecraft:redstone_wire` | 6 |
| `minecraft:cobblestone_stairs` | 5 |
| `minecraft:smithing_table` | 5 |
| `minecraft:cobweb` | 5 |
| `minecraft:furnace` | 5 |
| `minecraft:scaffolding` | 5 |
| `minecraft:coal_block` | 5 |
| `cfm:hospital_stand` | 4 |
| `minecraft:jukebox` | 4 |
| `minecraft:pumpkin` | 4 |
| `minecraft:observer` | 4 |
| `cfm:hospital_cupboard` | 4 |
| `minecraft:loom` | 4 |
| `minecraft:grindstone` | 4 |
| `cfm:chaise_4` | 3 |
| `minecraft:spruce_slab` | 3 |
| `minecraft:cracked_stone_bricks` | 3 |
| `minecraft:smoker` | 3 |
| `cfm:zombie_chainlinkfence` | 3 |
| `minecraft:blast_furnace` | 3 |
| `cfm:hospital_curtain` | 2 |
| `minecraft:red_carpet` | 2 |
| `minecraft:anvil` | 2 |
| `minecraft:magma_block` | 2 |
| `cfm:chaise_6` | 2 |
| `minecraft:iron_ore` | 2 |
| `minecraft:stone_button` | 2 |
| `cfm:hospital_bed` | 2 |
| `minecraft:chain` | 2 |
| `minecraft:dandelion` | 2 |
| `cfm:zombie_barricade_2` | 2 |
| `cfm:fluorescent_light_on` | 2 |
| `cfm:zombie_cone` | 2 |
| `minecraft:red_candle` | 2 |
| `minecraft:fire` | 2 |
| `cfm:bank_atm_shop` | 1 |
| `cfm:safe_sentinel_1` | 1 |
| `minecraft:crafting_table` | 1 |
| `minecraft:big_dripleaf` | 1 |
| `cfm:hospital_chair_block` | 1 |
| `minecraft:flowering_azalea` | 1 |
| `minecraft:cauldron` | 1 |
| `minecraft:redstone_block` | 1 |
| `minecraft:cartography_table` | 1 |
| `cfm:hospital_wheelchair` | 1 |
| `cfm:safe_planque_1` | 1 |
| `minecraft:black_candle` | 1 |
| `minecraft:heavy_weighted_pressure_plate` | 1 |
| `cfm:arkaniaz_corpse_1` | 1 |
| `minecraft:bell` | 1 |
| `cfm:loot_food_6` | 1 |
| `cfm:dungeon_skeleton` | 1 |
| `minecraft:campfire` | 1 |

</details>
## Composition du monde du spawn

```
[spawn] ATTENTION : bordure du monde du spawn à agrandir : 384 blocs (défaut 256)
[spawn]   écrite dans /home/runner/work/HeraliZ-ServeurMain/HeraliZ-ServeurMain/serveur/defaultconfigs/heraliz_spawn-server.toml
[spawn] composition : grotte du terminus + schematic 1-spawnbangerdelamort.schem (outils/monde_spawn.py composer) : 132 chunks, 629 Ko -> compose/spawn
[spawn]   reveil : [-61, 265, 2]
[spawn]   borne : {'x': 29, 'y': 245, 'z': -27, 'facing': 'west', 'origine': 'au cœur de la grande zone du spawn, avec de la place autour, visible en sortant du tunnel'}
[spawn]   barrieres_epave : 1167
[spawn]   epave_dans_la_roche : 0
[spawn]   emprise : {'x': [-92, 122], 'y': [227, 319], 'z': [-39, 113]}
[spawn]   tunnel : {'de': [-46, 265, 9], 'a': [-23, 245, 9], 'longueur': 24, 'montee': -20, 'arrivee': [-22, 245, 9]}
[spawn]   schematic : {'fichier': '1-spawnbangerdelamort.schem', 'format': 'Sponge v2 (.schem)', 'taille': [145, 93, 147], 'position': [-22, 227, -33], 'niveau_de_marche': 245, 'entree_rangee_z': 9, 'blocs_retires': 0, 'sols_ajoutes': 0, 'entites_de_blocs': 249, 'entites': 0, 'entites_ignorees': 0}
[spawn]   bordure_conseillee : 384
[spawn] rendus dans rendus
```

- grotte du terminus inchangée : réveil -61 265 2, regard 45.0°, épave (1167 barrières invisibles) ; seule la porte de service de la niche est retirée (passage vers le spawn).
- tunnel de 24 blocs de la niche (-46 265 9) au schematic, escaliers de -20 ; arrivée dans le spawn en -22 245 9.
- schematic posé avec son coin bas nord-ouest en -22 227 -33 (on y marche en y = 245) ; raccord : 0 blocs retirés, 0 blocs de sol ajoutés ; 249 entités de blocs, 0 entités.
- borne de départ en 29 245 -27, écran vers west (au cœur de la grande zone du spawn, avec de la place autour, visible en sortant du tunnel).
- **bordure du monde du spawn agrandie à 384 blocs** (défaut 256) : `serveur/defaultconfigs/heraliz_spawn-server.toml` (copié dans le monde à chaque mise en ligne).

Rendus : `monde/spawn_rendus/` (vue de dessus, coupe le long de la voie, coupe le long du tunnel, vue au réveil, vue en arrivant face à la borne).

