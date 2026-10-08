package fr.heraliz.wonder.net;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Canal du mod : quatre tout petits paquets serveur -> client, envoyes seulement aux joueurs proches.
 * <ul>
 *   <li>{@link Impact} (15 octets) : impact d'un rayon (eclaboussure du Pistolet a rayons, eclair du Mk II) ;</li>
 *   <li>{@link Chaine} (15 + 16 octets par cible) : la foudre de la Wunderwaffe, calculee une fois par le serveur ;</li>
 *   <li>{@link Regle} (1 octet) : reglage « tir hors zombies » pour que le client refuse le tir comme le serveur ;</li>
 *   <li>{@link Souffle} (33 octets + 1 a 3 par creature) : l'onde de choc d'un tir de Thundergun (1.0.2).</li>
 * </ul>
 * Un client sans le mod reste accepte (les effets sont seulement absents).
 */
public final class Reseau {
    private Reseau() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizWonder.MODID, "net"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    /** Distance a laquelle les effets sont envoyes (blocs). */
    public static final double PORTEE_EFFETS = 96;

    public static void init() {
        int id = 0;
        CANAL.registerMessage(id++, Impact.class, Impact::ecrire, Impact::lire, Impact::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(id++, Chaine.class, Chaine::ecrire, Chaine::lire, Chaine::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(id++, Regle.class, Regle::ecrire, Regle::lire, Regle::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(id, Souffle.class, Souffle::ecrire, Souffle::lire, Souffle::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Envoie aux joueurs a moins de {@link #PORTEE_EFFETS} blocs du point. */
    public static void autour(ServerLevel niveau, double x, double y, double z, Object msg) {
        ResourceKey<Level> dim = niveau.dimension();
        CANAL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, PORTEE_EFFETS, dim)), msg);
    }

    public static void a(ServerPlayer p, Object msg) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }
}
