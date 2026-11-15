package fr.heralisz.utils.api;

import fr.heralisz.utils.cine.CineDecor;
import java.util.function.Consumer;
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
    private static volatile Consumer<ServerPlayer> versBase;

    private SpawnApi() {}

    /** Branché par la partie serveur de HeraliZ Utils. */
    public static void brancher(Predicate<ServerPlayer> p) {
        versSpawn = p;
    }

    /** Branché par la partie serveur de HeraliZ Utils (15/11). */
    public static void brancherBase(Consumer<ServerPlayer> c) {
        versBase = c;
    }

    /**
     * Le joueur mort a choisi « À la base » sur son écran de mort (CRac, par réflexion, 15/11, audit perf n° 2) : sa
     * réapparition se fait sur la carte, d'où CRac le mène à sa base dans le même monde ; avant, il passait d'abord par le
     * point de réveil du spawn (trois changements de monde pour une mort). Si la base le refuse, CRac le renvoie au camp
     * ({@link #allerAuSpawn}). Sans partie serveur : rien.
     */
    public static void reapparitionALaBase(ServerPlayer sp) {
        Consumer<ServerPlayer> c = versBase;
        if (c != null && sp != null) {
            c.accept(sp);
        }
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
