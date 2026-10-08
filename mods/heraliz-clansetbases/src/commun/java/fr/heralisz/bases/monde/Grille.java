package fr.heralisz.bases.monde;

import fr.heralisz.bases.BasesConfig;
import fr.heralisz.bases.HeraliszBases;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;

/**
 * Dimension des bases et grille des parcelles. Parcelle i = case (i % COLONNES, i / COLONNES) de cote
 * {@code pas_grille} (192 par defaut) ; le bunker est centre dans sa case, coin bas a {@code y_bunker}.
 * Les coordonnees « relatives » sont celles de bunker.json (coin de la structure = 0, 0, 0).
 */
public final class Grille {
    private Grille() {
    }

    /** Dimension {@code heralisz_bases:bunkers} (data/heralisz_bases/dimension/bunkers.json). */
    public static final ResourceKey<Level> BUNKERS = ResourceKey.create(Registries.DIMENSION, HeraliszBases.id("bunkers"));
    /** Cases par ligne de la grille (10 000 parcelles possibles). */
    public static final int COLONNES = 100;

    @Nullable
    public static ServerLevel niveau(@Nullable MinecraftServer server) {
        return server == null ? null : server.getLevel(BUNKERS);
    }

    public static boolean estBunkers(@Nullable Level level) {
        return level != null && level.dimension() == BUNKERS;
    }

    public static int pas() {
        return BasesConfig.PAS_GRILLE.get();
    }

    /** Parcelle sous un point du monde des bases (-1 hors grille). */
    public static int parcelle(int x, int z) {
        int p = pas();
        int cx = Math.floorDiv(x, p), cz = Math.floorDiv(z, p);
        if (cx < 0 || cz < 0 || cx >= COLONNES) return -1;
        return cz * COLONNES + cx;
    }

    /** Coin bas de la case d'une parcelle (x, z) ; y = 0. */
    public static BlockPos coinCase(int parcelle) {
        int p = pas();
        return new BlockPos((parcelle % COLONNES) * p, 0, (parcelle / COLONNES) * p);
    }

    /** Coin (0, 0, 0) de la structure du bunker dans la parcelle, pour une structure de taille tx x tz (centree). */
    public static BlockPos origine(int parcelle, int tx, int tz) {
        int p = pas();
        BlockPos c = coinCase(parcelle);
        int ox = Math.max(0, (p - tx) / 2), oz = Math.max(0, (p - tz) / 2);
        return new BlockPos(c.getX() + ox, BasesConfig.Y_BUNKER.get(), c.getZ() + oz);
    }
}
