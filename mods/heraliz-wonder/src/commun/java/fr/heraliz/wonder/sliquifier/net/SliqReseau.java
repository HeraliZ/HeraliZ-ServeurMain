package fr.heraliz.wonder.sliquifier.net;

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
 * Canal propre au Sliquifier ({@code heraliz_wonder:sliquifier}, a part du canal commun : aucun numero de paquet a
 * partager avec les autres Wonder Weapons). Quatre petits paquets serveur -> client, envoyes aux joueurs proches :
 * <ul>
 *   <li>{@link PaquetTir} (~34 octets) : une boule de gelee part (le client refait exactement son vol) ;</li>
 *   <li>{@link PaquetFlaque} (~25 octets) : une flaque apparait (avec ou sans eclaboussure), s'etale ou disparait ;</li>
 *   <li>{@link PaquetZombie} (5 a 7 octets) : un zombie glisse, fond, ou un boss est touche ;</li>
 *   <li>{@link PaquetSync} : les flaques de la dimension et les reglages de glisse (connexion, changement de dimension).</li>
 * </ul>
 * Un client sans le mod reste accepte (les effets sont seulement absents).
 */
public final class SliqReseau {
    private SliqReseau() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizWonder.MODID, "sliquifier"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    /** Distance a laquelle les effets sont envoyes (blocs). */
    public static final double PORTEE = 96;

    public static void init() {
        int id = 0;
        CANAL.registerMessage(id++, PaquetTir.class, PaquetTir::ecrire, PaquetTir::lire, PaquetTir::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(id++, PaquetFlaque.class, PaquetFlaque::ecrire, PaquetFlaque::lire, PaquetFlaque::recevoir,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(id++, PaquetZombie.class, PaquetZombie::ecrire, PaquetZombie::lire, PaquetZombie::recevoir,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CANAL.registerMessage(id, PaquetSync.class, PaquetSync::ecrire, PaquetSync::lire, PaquetSync::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void autour(ServerLevel niveau, double x, double y, double z, Object msg) {
        ResourceKey<Level> dim = niveau.dimension();
        CANAL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, PORTEE, dim)), msg);
    }

    public static void a(ServerPlayer p, Object msg) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> p), msg);
    }
}
