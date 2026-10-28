package fr.heraliz.crac.bases;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Le monde où se bâtissent les bases et le monde du spawn de HeraliZ Utils ({@code heralisz:spawn}, lu par son nom :
 * aucune dépendance à Utils).
 * <p>Depuis CRac 1.16.0 (21/10, demande du propriétaire), les bases des clans sont sur la carte principale (overworld) :
 * {@link #ID} / {@link #BASES} la désignent. L'ancien monde à part ({@code crac:bases}, {@link #ANCIEN}) reste déclaré en
 * données (bancs de charge des bots, données d'avant) ; ses règles (aucune créature, pas de seaux, bordure propre) ne
 * valent que pour lui.
 * <p>Depuis le 28/10 (demande du propriétaire), le tutoriel des bases a son propre monde, {@code crac:tuto} ({@link #TUTO}) :
 * plat et vide, une parcelle de 64 × 64 blocs par joueur, isolée des autres ({@code PlotsTuto}, côté serveur).
 */
public final class MondeBases {
    public static final ResourceLocation ID = Level.OVERWORLD.location();
    public static final ResourceKey<Level> BASES = Level.OVERWORLD;
    /** L'ancien monde des bases (CRac 1.5.0 à 1.15) : depuis 1.16.0, le monde du tutoriel (cabane d'essai effacée à la fin). */
    public static final ResourceLocation ANCIEN = new ResourceLocation("crac", "bases");
    public static final ResourceKey<Level> ANCIEN_CLE = ResourceKey.create(Registries.DIMENSION, ANCIEN);
    /** Le monde du tutoriel des bases (28/10) : plat, vide, une parcelle par joueur. */
    public static final ResourceLocation TUTO_ID = new ResourceLocation("crac", "tuto");
    public static final ResourceKey<Level> TUTO = ResourceKey.create(Registries.DIMENSION, TUTO_ID);
    public static final ResourceLocation SPAWN = new ResourceLocation("heralisz", "spawn");

    private MondeBases() {
    }

    public static boolean estBases(@Nullable Level l) {
        return l != null && ID.equals(l.dimension().location());
    }

    public static boolean estAncien(@Nullable Level l) {
        return l != null && ANCIEN.equals(l.dimension().location());
    }

    /** Le monde du tutoriel des bases ({@code crac:tuto}, depuis le 28/10 ; avant, l'ancien monde des bases). */
    public static boolean estTuto(@Nullable Level l) {
        return l != null && TUTO_ID.equals(l.dimension().location());
    }

    public static boolean estSpawn(@Nullable Level l) {
        return l != null && SPAWN.equals(l.dimension().location());
    }
}
