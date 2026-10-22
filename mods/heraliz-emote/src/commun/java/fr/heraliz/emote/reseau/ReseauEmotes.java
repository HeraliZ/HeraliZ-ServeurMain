package fr.heraliz.emote.reseau;

import fr.heraliz.emote.HeralizEmote;
import fr.heraliz.emote.pont.Crochet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Canal {@code heraliz_emote:main}, du serveur vers le joueur : {@link Deblocages}, les emotes qu'il a débloquées (à la
 * connexion et à chaque changement). Un serveur sans ce canal : le jeu du joueur ne verrouille rien.
 */
public final class ReseauEmotes {
    private static final String VERSION = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeralizEmote.MODID, "main"),
            () -> VERSION, NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    /** Déblocages reçus : branché par la partie client. */
    public static final Crochet<Deblocages> DEBLOCAGES_CLIENT = new Crochet<>();

    private static final int MAX = 4096;

    private ReseauEmotes() {
    }

    public static void enregistrer() {
        CANAL.registerMessage(0, Deblocages.class, Deblocages::ecrire, Deblocages::lire, Deblocages::traiter,
                Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    public static void envoyer(ServerPlayer joueur, Object message) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), message);
    }

    /** {@code tout} : staff en créatif ou spectateur, rien n'est verrouillé. */
    public record Deblocages(boolean tout, List<UUID> emotes) {
        static void ecrire(Deblocages m, FriendlyByteBuf b) {
            b.writeBoolean(m.tout);
            b.writeVarInt(m.emotes.size());
            for (UUID u : m.emotes) b.writeUUID(u);
        }

        static Deblocages lire(FriendlyByteBuf b) {
            boolean tout = b.readBoolean();
            int n = b.readVarInt();
            if (n < 0 || n > MAX) throw new IllegalArgumentException("Emotes : " + n);
            List<UUID> l = new ArrayList<>(n);
            for (int i = 0; i < n; i++) l.add(b.readUUID());
            return new Deblocages(tout, l);
        }

        static void traiter(Deblocages m, Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DEBLOCAGES_CLIENT.accept(m));
            ctx.get().setPacketHandled(true);
        }
    }
}
