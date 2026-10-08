package fr.heraliz.wonder.blundergat.net;

import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Canal propre au Blundergat ({@code heraliz_wonder:blundergat}, a part du canal commun : rien a renumeroter a
 * l'integration) : deux petits paquets serveur -> client, envoyes seulement aux joueurs proches.
 * <ul>
 *   <li>{@link Eclats} : impacts des obus d'un tireur pendant un tick (regroupes : un paquet par tireur et par tick) ;</li>
 *   <li>{@link Acide} : cartouches d'acide (tir, cartouche collee, explosion).</li>
 * </ul>
 * Un client sans le mod reste accepte (effets absents).
 */
public final class BlunderReseau {
    private BlunderReseau() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizWonder.MODID, "blundergat"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    /** Distance a laquelle les effets sont envoyes (blocs). */
    public static final double PORTEE = 96;

    public static void init() {
        CANAL.registerMessage(0, Eclats.class, Eclats::ecrire, Eclats::lire, Eclats::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(1, Acide.class, Acide::ecrire, Acide::lire, Acide::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void autour(ServerLevel niveau, double x, double y, double z, Object msg) {
        ResourceKey<Level> dim = niveau.dimension();
        CANAL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, PORTEE, dim)), msg);
    }
}
