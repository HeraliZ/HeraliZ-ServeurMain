package fr.heralisz.utils.combat;

import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.pont.Crochet;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Canal du tag de combat (déplacé de HeraliZ Matchs le 24/10, quand les matchs ont été retirés du serveur) : un seul
 * paquet, du serveur au joueur, avec les secondes restantes du tag (0 = masquer l'indicateur « EN COMBAT »). Le joueur
 * décompte seul ensuite. La logique (qui est tagué, déconnexion = mort, commandes refusées) est dans
 * {@code combat.CombatServeur} (src/serveur), l'indicateur dans {@code combat.client.CombatClient} (src/client).
 */
public final class CombatReseau {
    private static final String PROTO = "1";
    public static final SimpleChannel CANAL = NetworkRegistry.newSimpleChannel(new ResourceLocation(HeraliszUtils.MODID, "combat"),
            () -> PROTO, PROTO::equals, PROTO::equals);

    /** Paquet reçu par le joueur : la partie client met à jour l'indicateur (CombatClient.recevoir). */
    public static final Crochet<Indicateur> RECEPTION_CLIENT = new Crochet<>();

    private CombatReseau() {
    }

    /** Secondes restantes du tag de combat (0 = masquer). */
    public record Indicateur(int secondes) {
    }

    public static void init() {
        CANAL.registerMessage(0, Indicateur.class, (m, buf) -> buf.writeVarInt(m.secondes()), buf -> new Indicateur(buf.readVarInt()),
                (m, ctx) -> {
                    ctx.get().enqueueWork(() -> RECEPTION_CLIENT.accept(m));
                    ctx.get().setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** Envoie l'indicateur au joueur (secondes restantes, 0 = masquer). */
    public static void envoyer(ServerPlayer joueur, int secondes) {
        CANAL.send(PacketDistributor.PLAYER.with(() -> joueur), new Indicateur(Math.max(0, secondes)));
    }
}
