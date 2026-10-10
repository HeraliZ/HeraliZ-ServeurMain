package fr.heraliz.decors;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blocs maison des villes du désert (médina de Mirage) et de la base militaire arctique (1.5.0) : enduits, briques de terre
 * crue, pierre de taille, zelliges, moucharabiehs, toiles du souk, tapis ; panneaux polaires, tôles, caillebotis, hublots,
 * béton et neige tassée. Blocs simples (aucun calcul) ; escaliers, dalles et murets pour certains. Les textures sont
 * générées par tools/blocs_maison.py (même liste, même ordre) ; aucun objet lâché quand on les casse (blocs de la map).
 */
final class BlocsMaison {
    private BlocsMaison() {}

    /** Famille de matière : son et dureté. */
    private enum M { ENDUIT, TERRE, PIERRE, CARREAU, SABLE, BOIS, PALME, TOILE, METAL, BETON, NEIGE, LAINE }

    private static BlockBehaviour.Properties prop(M m, MapColor c) {
        BlockBehaviour.Properties p = BlockBehaviour.Properties.of().mapColor(c);
        return switch (m) {
            case ENDUIT -> p.strength(1.2f, 6.0f).sound(SoundType.STONE);
            case TERRE -> p.strength(1.2f, 4.0f).sound(SoundType.MUD_BRICKS);
            case PIERRE -> p.strength(1.5f, 6.0f).sound(SoundType.STONE);
            case CARREAU -> p.strength(1.4f, 4.2f).sound(SoundType.DEEPSLATE_TILES);
            case SABLE -> p.strength(0.8f, 1.0f).sound(SoundType.SAND);
            case BOIS -> p.strength(2.0f, 3.0f).sound(SoundType.WOOD);
            case PALME -> p.strength(0.5f).sound(SoundType.GRASS);
            case TOILE -> p.strength(0.8f).sound(SoundType.WOOL);
            case METAL -> p.strength(3.0f, 6.0f).sound(SoundType.METAL);
            case BETON -> p.strength(1.8f, 6.0f).sound(SoundType.STONE);
            case NEIGE -> p.strength(0.6f).sound(SoundType.SNOW);
            case LAINE -> p.strength(0.5f).sound(SoundType.WOOL);
        };
    }

    static void enregistrer() {
        // désert : médina de Mirage
        cube("enduit_sable", M.ENDUIT, MapColor.SAND, "escalier", "dalle", "mur");
        cube("enduit_sable_use", M.ENDUIT, MapColor.SAND);
        cube("enduit_blanc", M.ENDUIT, MapColor.QUARTZ, "escalier", "dalle", "mur");
        cube("enduit_blanc_use", M.ENDUIT, MapColor.QUARTZ);
        cube("enduit_ocre", M.ENDUIT, MapColor.TERRACOTTA_ORANGE, "escalier", "dalle", "mur");
        cube("enduit_ocre_use", M.ENDUIT, MapColor.TERRACOTTA_ORANGE);
        cube("brique_crue", M.TERRE, MapColor.TERRACOTTA_YELLOW, "escalier", "dalle", "mur");
        cube("brique_crue_usee", M.TERRE, MapColor.TERRACOTTA_YELLOW);
        cube("pise", M.TERRE, MapColor.TERRACOTTA_ORANGE, "mur");
        cube("pierre_sable", M.PIERRE, MapColor.SAND, "escalier", "dalle", "mur");
        cube("pierre_sable_frise", M.PIERRE, MapColor.SAND);
        pilier("pilier_sable", M.PIERRE, MapColor.SAND);
        cube("dallage_sable", M.PIERRE, MapColor.SAND, "escalier", "dalle");
        cube("carreaux_terre_cuite", M.CARREAU, MapColor.TERRACOTTA_RED, "escalier", "dalle");
        cube("paves_medina", M.PIERRE, MapColor.TERRACOTTA_WHITE, "dalle");
        cube("sable_tasse", M.SABLE, MapColor.SAND, "dalle");
        cube("zellige_bleu", M.CARREAU, MapColor.COLOR_BLUE, "dalle");
        cube("zellige_vert", M.CARREAU, MapColor.COLOR_GREEN, "dalle");
        cube("zellige_ocre", M.CARREAU, MapColor.TERRACOTTA_ORANGE, "dalle");
        decoupe("moucharabieh", M.BOIS, MapColor.COLOR_BROWN);
        decoupe("claustra", M.ENDUIT, MapColor.QUARTZ);
        cube("bois_palmier", M.BOIS, MapColor.WOOD, "escalier", "dalle");
        pilier("rondin_palmier", M.BOIS, MapColor.WOOD);
        cube("palmes_sechees", M.PALME, MapColor.COLOR_YELLOW, "dalle");
        cube("toile_rayee_rouge", M.TOILE, MapColor.COLOR_RED, "dalle");
        cube("toile_rayee_bleue", M.TOILE, MapColor.COLOR_BLUE, "dalle");
        cube("toile_safran", M.TOILE, MapColor.COLOR_ORANGE, "dalle");
        tapis("tapis_berbere", M.TOILE, MapColor.COLOR_RED);
        // arctique : base militaire
        cube("tole_blanche", M.METAL, MapColor.SNOW, "dalle");
        cube("tole_blanche_givree", M.METAL, MapColor.SNOW);
        cube("panneau_polaire_blanc", M.METAL, MapColor.SNOW, "dalle");
        cube("panneau_polaire_orange", M.METAL, MapColor.COLOR_ORANGE, "dalle");
        cube("panneau_polaire_rouge", M.METAL, MapColor.COLOR_RED, "dalle");
        cube("panneau_polaire_gris", M.METAL, MapColor.COLOR_GRAY, "dalle");
        cube("panneau_givre", M.METAL, MapColor.SNOW);
        decoupe("caillebotis", M.METAL, MapColor.METAL, "dalle");
        cube("tole_larmee", M.METAL, MapColor.METAL, "escalier", "dalle");
        cube("tole_larmee_rouille", M.METAL, MapColor.TERRACOTTA_BROWN, "dalle");
        cube("beton_polaire", M.BETON, MapColor.STONE, "escalier", "dalle", "mur");
        cube("beton_marquage", M.BETON, MapColor.COLOR_YELLOW, "dalle");
        cube("neige_tassee", M.NEIGE, MapColor.SNOW, "dalle");
        cube("glace_sale", M.NEIGE, MapColor.ICE);
        cube("isolant", M.LAINE, MapColor.COLOR_YELLOW);
        translucide("hublot", M.METAL, MapColor.SNOW);
        cube("grille_aeration", M.METAL, MapColor.METAL);
        cube("bache_blanche", M.TOILE, MapColor.SNOW, "dalle");
        // toits de tôle (à la place du cuivre)
        cube("tole_toit_verte", M.METAL, MapColor.COLOR_GREEN, "escalier", "dalle");
        cube("tole_toit_bleue", M.METAL, MapColor.COLOR_BLUE, "escalier", "dalle");
        cube("tole_toit_rouille", M.METAL, MapColor.TERRACOTTA_BROWN, "escalier", "dalle");
    }

    /** Bloc plein et ses variantes (escalier, dalle, muret). */
    private static void cube(String nom, M m, MapColor c, String... variantes) {
        RegistryObject<Block> b = Blocs.bloc(nom, () -> new Block(prop(m, c)));
        variantes(nom, b, m, c, false, variantes);
    }

    /** Bloc ajouré (moucharabieh, claustra, caillebotis) : on voit au travers, les faces voisines restent dessinées. */
    private static void decoupe(String nom, M m, MapColor c, String... variantes) {
        RegistryObject<Block> b = Blocs.bloc(nom, () -> new Block(prop(m, c).noOcclusion()
                .isViewBlocking((s, l, p) -> false).isSuffocating((s, l, p) -> false)));
        variantes(nom, b, m, c, true, variantes);
    }

    /** Vitre teintée (hublot). */
    private static void translucide(String nom, M m, MapColor c, String... variantes) {
        decoupe(nom, m, c, variantes);
    }

    /** Pilier ou tronc couché dans les trois axes. */
    private static void pilier(String nom, M m, MapColor c, String... variantes) {
        Blocs.bloc(nom, () -> new RotatedPillarBlock(prop(m, c)));
    }

    /** Tapis posé au sol (1/16 de haut). */
    private static void tapis(String nom, M m, MapColor c, String... variantes) {
        Blocs.bloc(nom, () -> new CarpetBlock(prop(m, c).strength(0.1f)));
    }

    private static void variantes(String nom, RegistryObject<Block> b, M m, MapColor c, boolean ajoure, String... variantes) {
        for (String v : variantes) {
            switch (v) {
                case "escalier" -> Blocs.bloc(nom + "_escalier", () -> new StairBlock(() -> b.get().defaultBlockState(),
                        ajoure ? prop(m, c).noOcclusion() : prop(m, c)));
                case "dalle" -> Blocs.bloc(nom + "_dalle", () -> new SlabBlock(ajoure ? prop(m, c).noOcclusion() : prop(m, c)));
                case "mur" -> Blocs.bloc(nom + "_mur", () -> new WallBlock(prop(m, c).forceSolidOn()));
                default -> throw new IllegalArgumentException(v);
            }
        }
    }
}
