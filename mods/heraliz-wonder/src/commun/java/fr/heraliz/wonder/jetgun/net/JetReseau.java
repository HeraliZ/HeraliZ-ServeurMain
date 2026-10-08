package fr.heraliz.wonder.jetgun.net;

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
 * Canal du Jet Gun ({@code heraliz_wonder:jetgun}), séparé de celui des autres Wonder Weapons (aucun conflit d'ids de
 * paquets). Un seul paquet, serveur -> clients proches : {@link Broyage} (créatures déchiquetées ce tick). L'état de tir
 * se lit chez le client dans les événements de TaCZ ({@code GunFireEvent}), la chaleur dans le NBT de l'arme tenue
 * (synchronisé par Minecraft) : aucun paquet de plus. Un client sans le mod reste accepté.
 */
public final class JetReseau {
    private JetReseau() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizWonder.MODID, "jetgun"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    public static final double PORTEE_EFFETS = 96;

    public static void init() {
        CANAL.registerMessage(0, Broyage.class, Broyage::ecrire, Broyage::lire, Broyage::recevoir, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void autour(ServerLevel niveau, double x, double y, double z, Object msg) {
        ResourceKey<Level> dim = niveau.dimension();
        CANAL.send(PacketDistributor.NEAR.with(() -> new PacketDistributor.TargetPoint(x, y, z, PORTEE_EFFETS, dim)), msg);
    }
}
