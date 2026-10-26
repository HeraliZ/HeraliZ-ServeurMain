package fr.heralisz.utils.api;

import fr.heralisz.utils.cine.CineDecor;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

/**
 * API du monde du spawn de HeraliZ (dimension {@code heralisz:spawn}), pour les autres mods. Ne JAMAIS renommer cette
 * classe ni changer ses signatures : HeraliZ Extraction l'appelle par réflexion (Class.forName), le compilateur ne le
 * voit pas. Côté serveur seulement pour {@link #allerAuSpawn} (sans partie serveur : rend false).
 */
public final class SpawnApi {
    /** Le monde du spawn. */
    public static final ResourceKey<Level> SPAWN = ResourceKey.create(Registries.DIMENSION, CineDecor.SPAWN);
    private static volatile Predicate<ServerPlayer> versSpawn;

    private SpawnApi() {}

    /** Branché par la partie serveur de HeraliZ Utils. */
    public static void brancher(Predicate<ServerPlayer> p) {
        versSpawn = p;
    }

    /**
     * Pose le joueur au point de réveil du spawn (vraie téléportation, regard réglé), s'il est praticable : monde du
     * spawn déposé, place libre et sol dessous. Rend false sans rien faire sinon (l'appelant garde son ancienne
     * destination).
     */
    public static boolean allerAuSpawn(ServerPlayer sp) {
        Predicate<ServerPlayer> p = versSpawn;
        return p != null && sp != null && p.test(sp);
    }

    /** Ce niveau est-il le monde du spawn ? */
    public static boolean estAuSpawn(Level l) {
        return l != null && l.dimension().location().equals(CineDecor.SPAWN);
    }
}
