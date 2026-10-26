package fr.heraliz.armory.network;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.pont.Crochet;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Canal reseau du mod : le client demande de baisser / relever les lunettes de vision nocturne ; le serveur envoie au
 * porteur de la tenue de plongee la reserve de sa bouteille (0.9.0, protocole 2).
 */
public final class ArmoryNetwork {
    private ArmoryNetwork() {}

    private static final String VERSION = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(HeralizArmory.id("main"), () -> VERSION, VERSION::equals,
            VERSION::equals);

    /** Message vide : "bascule mes lunettes". */
    public record ToggleNvg() {}

    /** Reserve de la bouteille de plongee (ticks) et capacite. */
    public record TankSync(int tank, int max) {}

    /** « Bascule mes lunettes » recu par le serveur : branche par la partie serveur (nvg.NightVisionServeur.toggle). */
    public static final Crochet<ServerPlayer> RECEPTION_SERVEUR = new Crochet<>();
    /** Reserve de la bouteille recue par le client : branche par la partie client (client.DivingClient.sync). */
    public static final Crochet<TankSync> RECEPTION_CLIENT = new Crochet<>();

    public static void register() {
        CHANNEL.messageBuilder(ToggleNvg.class, 0, NetworkDirection.PLAY_TO_SERVER)
                .encoder((m, buf) -> {})
                .decoder(buf -> new ToggleNvg())
                .consumerMainThread((m, ctx) -> {
                    var player = ctx.get().getSender();
                    if (player != null) RECEPTION_SERVEUR.accept(player);
                    ctx.get().setPacketHandled(true);
                })
                .add();
        CHANNEL.messageBuilder(TankSync.class, 1, NetworkDirection.PLAY_TO_CLIENT)
                .encoder((m, buf) -> {
                    buf.writeVarInt(m.tank());
                    buf.writeVarInt(m.max());
                })
                .decoder(buf -> new TankSync(buf.readVarInt(), buf.readVarInt()))
                .consumerMainThread((m, ctx) -> {
                    RECEPTION_CLIENT.accept(m);
                    ctx.get().setPacketHandled(true);
                })
                .add();
    }
}
