package fr.heraliz.crac.bases;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Le monde des bases ({@code crac:bases}, déclaré en données : data/crac/dimension/bases.json, créé par Minecraft au
 * démarrage du serveur) et le monde du spawn de HeraliZ Utils ({@code heralisz:spawn}, lu par son nom : aucune
 * dépendance à Utils).
 */
public final class MondeBases {
    public static final ResourceLocation ID = new ResourceLocation("crac", "bases");
    public static final ResourceKey<Level> BASES = ResourceKey.create(Registries.DIMENSION, ID);
    public static final ResourceLocation SPAWN = new ResourceLocation("heralisz", "spawn");

    private MondeBases() {
    }

    public static boolean estBases(@Nullable Level l) {
        return l != null && ID.equals(l.dimension().location());
    }

    public static boolean estSpawn(@Nullable Level l) {
        return l != null && SPAWN.equals(l.dimension().location());
    }
}
