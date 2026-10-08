package fr.heralisz.economie.network;

import fr.heralisz.economie.HeraliszEconomie;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Canal reseau heralisz_economie:main (version "1", identique des deux cotes). Seulement du serveur vers le client :
 * le client ne demande jamais rien (les commandes passent par le chat).
 */
public final class EconomieNet {
    private EconomieNet() {
    }

    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HeraliszEconomie.id("main"),
            () -> VERSION, VERSION::equals, VERSION::equals);

    private static int nextId;

    private static <T> void msg(Class<T> type, NetworkDirection dir, BiConsumer<T, FriendlyByteBuf> enc, Function<FriendlyByteBuf, T> dec,
                                BiConsumer<T, Supplier<NetworkEvent.Context>> handler) {
        CHANNEL.messageBuilder(type, nextId++, dir).encoder(enc).decoder(dec).consumerMainThread(handler).add();
    }

    public static void register() {
        NetworkDirection s2c = NetworkDirection.PLAY_TO_CLIENT;
        msg(Packets.SoldeS2C.class, s2c, Packets.SoldeS2C::write, Packets.SoldeS2C::read, Packets.SoldeS2C::handle);
        msg(Packets.ReglagesS2C.class, s2c, Packets.ReglagesS2C::write, Packets.ReglagesS2C::read, Packets.ReglagesS2C::handle);
    }

    public static void toPlayer(ServerPlayer player, Object msg) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), msg);
    }

    public static void toAll(Object msg) {
        CHANNEL.send(PacketDistributor.ALL.noArg(), msg);
    }
}
