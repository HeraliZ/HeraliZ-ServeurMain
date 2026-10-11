"""Écrit src/commun/java/fr/heraliz/decors/BlocsMaison.java depuis le catalogue de blocs_maison.py (même liste, même
ordre) : à relancer après chaque ajout de bloc maison.

    python mods/heraliz-decors/outils/gen_blocs_maison_java.py
"""
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import blocs_maison as BM  # noqa: E402

CIBLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "commun", "java", "fr", "heraliz", "decors",
                     "BlocsMaison.java")
# matière (son, dureté) et couleur de carte de chaque bloc
MATIERE = {'enduit_sable': ('ENDUIT', 'SAND'),
 'enduit_sable_use': ('ENDUIT', 'SAND'),
 'enduit_blanc': ('ENDUIT', 'QUARTZ'),
 'enduit_blanc_use': ('ENDUIT', 'QUARTZ'),
 'enduit_ocre': ('ENDUIT', 'TERRACOTTA_ORANGE'),
 'enduit_ocre_use': ('ENDUIT', 'TERRACOTTA_ORANGE'),
 'brique_crue': ('TERRE', 'TERRACOTTA_YELLOW'),
 'brique_crue_usee': ('TERRE', 'TERRACOTTA_YELLOW'),
 'pierre_sable': ('PIERRE', 'SAND'),
 'dallage_sable': ('PIERRE', 'SAND'),
 'carreaux_terre_cuite': ('CARREAU', 'TERRACOTTA_RED'),
 'paves_medina': ('PIERRE', 'TERRACOTTA_WHITE'),
 'sable_tasse': ('SABLE', 'SAND'),
 'zellige_bleu': ('CARREAU', 'COLOR_BLUE'),
 'zellige_vert': ('CARREAU', 'COLOR_GREEN'),
 'moucharabieh': ('BOIS', 'COLOR_BROWN'),
 'bois_palmier': ('BOIS', 'WOOD'),
 'rondin_palmier': ('BOIS', 'WOOD'),
 'palmes_sechees': ('PALME', 'COLOR_YELLOW'),
 'toile_rayee_rouge': ('TOILE', 'COLOR_RED'),
 'toile_rayee_bleue': ('TOILE', 'COLOR_BLUE'),
 'toile_safran': ('TOILE', 'COLOR_ORANGE'),
 'tapis_berbere': ('TOILE', 'COLOR_RED'),
 'tole_blanche': ('METAL', 'SNOW'),
 'tole_blanche_givree': ('METAL', 'SNOW'),
 'panneau_polaire_blanc': ('METAL', 'SNOW'),
 'panneau_polaire_orange': ('METAL', 'COLOR_ORANGE'),
 'panneau_polaire_rouge': ('METAL', 'COLOR_RED'),
 'panneau_polaire_gris': ('METAL', 'COLOR_GRAY'),
 'panneau_givre': ('METAL', 'SNOW'),
 'tole_larmee': ('METAL', 'METAL'),
 'beton_polaire': ('BETON', 'STONE'),
 'neige_tassee': ('NEIGE', 'SNOW'),
 'glace_sale': ('NEIGE', 'ICE'),
 'hublot': ('METAL', 'SNOW'),
 'tole_toit_verte': ('METAL', 'COLOR_GREEN'),
 'tole_toit_bleue': ('METAL', 'COLOR_BLUE'),
 'tole_toit_rouille': ('METAL', 'TERRACOTTA_BROWN')}
THEMES = {"desert": "désert : médina de Mirage", "arctique": "arctique : base militaire", "toits": "toits de tôle (à la place du cuivre)"}
DEBUT = 'package fr.heraliz.decors;\n\nimport net.minecraft.world.level.block.Block;\nimport net.minecraft.world.level.block.CarpetBlock;\nimport net.minecraft.world.level.block.RotatedPillarBlock;\nimport net.minecraft.world.level.block.SlabBlock;\nimport net.minecraft.world.level.block.SoundType;\nimport net.minecraft.world.level.block.StairBlock;\nimport net.minecraft.world.level.block.WallBlock;\nimport net.minecraft.world.level.block.state.BlockBehaviour;\nimport net.minecraft.world.level.material.MapColor;\nimport net.minecraftforge.registries.RegistryObject;\n\n/**\n * Blocs maison des villes du désert (médina de Mirage) et de la base militaire arctique (1.5.0) : enduits, briques de terre\n * crue, pierre de taille, zelliges, moucharabiehs, toiles du souk, tapis ; panneaux polaires, tôles, hublots,\n * béton et neige tassée. Blocs simples (aucun calcul) ; escaliers, dalles et murets pour certains. Les textures sont\n * générées par outils/blocs_maison.py (même liste, même ordre) ; aucun objet lâché quand on les casse (blocs de la map).\n */\nfinal class BlocsMaison {\n    private BlocsMaison() {}\n\n    /** Famille de matière : son et dureté. */\n    private enum M { ENDUIT, TERRE, PIERRE, CARREAU, SABLE, BOIS, PALME, TOILE, METAL, BETON, NEIGE, LAINE }\n\n    private static BlockBehaviour.Properties prop(M m, MapColor c) {\n        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(c);\n        return switch (m) {\n            case ENDUIT -> p.strength(1.2f, 6.0f).sound(SoundType.STONE);\n            case TERRE -> p.strength(1.2f, 4.0f).sound(SoundType.MUD_BRICKS);\n            case PIERRE -> p.strength(1.5f, 6.0f).sound(SoundType.STONE);\n            case CARREAU -> p.strength(1.4f, 4.2f).sound(SoundType.DEEPSLATE_TILES);\n            case SABLE -> p.strength(0.8f, 1.0f).sound(SoundType.SAND);\n            case BOIS -> p.strength(2.0f, 3.0f).sound(SoundType.WOOD);\n            case PALME -> p.strength(0.5f).sound(SoundType.GRASS);\n            case TOILE -> p.strength(0.8f).sound(SoundType.WOOL);\n            case METAL -> p.strength(3.0f, 6.0f).sound(SoundType.METAL);\n            case BETON -> p.strength(1.8f, 6.0f).sound(SoundType.STONE);\n            case NEIGE -> p.strength(0.6f).sound(SoundType.SNOW);\n            case LAINE -> p.strength(0.5f).sound(SoundType.WOOL);\n        };\n    }\n\n    static void enregistrer() {\n'
FIN = '\n    }\n\n    /** Bloc plein et ses variantes (escalier, dalle, muret). */\n    private static void cube(String nom, M m, MapColor c, String... variantes) {\n        RegistryObject<Block> b = Blocs.bloc(nom, () -> new Block(prop(m, c)));\n        variantes(nom, b, m, c, false, variantes);\n    }\n\n    /** Bloc ajouré (moucharabieh) : on voit au travers, les faces voisines restent dessinées. */\n    private static void decoupe(String nom, M m, MapColor c, String... variantes) {\n        RegistryObject<Block> b = Blocs.bloc(nom, () -> new Block(prop(m, c).noOcclusion()\n                .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));\n        variantes(nom, b, m, c, true, variantes);\n    }\n\n    /** Vitre teintée (hublot). */\n    private static void translucide(String nom, M m, MapColor c, String... variantes) {\n        decoupe(nom, m, c, variantes);\n    }\n\n    /** Pilier ou tronc couché dans les trois axes. */\n    private static void pilier(String nom, M m, MapColor c, String... variantes) {\n        Blocs.bloc(nom, () -> new RotatedPillarBlock(prop(m, c)));\n    }\n\n    /** Tapis posé au sol (1/16 de haut). */\n    private static void tapis(String nom, M m, MapColor c, String... variantes) {\n        Blocs.bloc(nom, () -> new CarpetBlock(prop(m, c).strength(0.1f)));\n    }\n\n    private static void variantes(String nom, RegistryObject<Block> b, M m, MapColor c, boolean ajoure, String... variantes) {\n        for (String v : variantes) {\n            switch (v) {\n                case "escalier" -> Blocs.bloc(nom + "_escalier", () -> new StairBlock(() -> b.get().defaultBlockState(),\n                        ajoure ? prop(m, c).noOcclusion() : prop(m, c)));\n                case "dalle" -> Blocs.bloc(nom + "_dalle", () -> new SlabBlock(ajoure ? prop(m, c).noOcclusion() : prop(m, c)));\n                case "mur" -> Blocs.bloc(nom + "_mur", () -> new WallBlock(prop(m, c).forceSolidOn()));\n                default -> throw new IllegalArgumentException(v);\n            }\n        }\n    }\n}\n'


def main():
    manque = set(BM.BLOCS) ^ set(MATIERE)
    assert not manque, manque
    lignes = []
    th = None
    for n, d in BM.BLOCS.items():
        if d["theme"] != th:
            th = d["theme"]
            lignes.append(f"        // {THEMES[th]}")
        m, c = MATIERE[n]
        var = "".join(f', "{v}"' for v in d["variantes"])
        lignes.append(f'        {d["forme"]}("{n}", M.{m}, MapColor.{c}{var});')
    open(CIBLE, "w", encoding="utf-8").write(DEBUT + "\n".join(lignes) + FIN)
    print(len(BM.BLOCS), "blocs ->", os.path.abspath(CIBLE))


if __name__ == "__main__":
    main()
