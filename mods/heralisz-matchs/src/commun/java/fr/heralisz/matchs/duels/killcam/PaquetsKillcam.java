package fr.heralisz.matchs.duels.killcam;

import fr.heralisz.matchs.reseau.MatchsNet;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Paquets de la killcam (ordre fixe) : {@link ClipS2C} (lancer une killcam), {@link ArretS2C} (l'interrompre),
 * {@link FinC2S} (le client signale la fin ou le passage). Le traitement client passe par
 * {@code client.KillcamClient}, jamais charge sur un serveur dedie.
 */
public final class PaquetsKillcam {
    /** Réception côté client des paquets serveur -> client (client/ReceptionPaquetsKillcam) : branchée par la partie client. */
    public static final fr.heralisz.matchs.pont.Crochet<Object> RECEPTION_CLIENT = new fr.heralisz.matchs.pont.Crochet<>();

    private PaquetsKillcam() {
    }

    static void enregistrer(MatchsNet.Registre r) {
        r.s2c(ClipS2C.class, ClipS2C::write, ClipS2C::read, ClipS2C::handle);
        r.s2c(ArretS2C.class, ArretS2C::write, ArretS2C::read, ArretS2C::handle);
        r.c2s(FinC2S.class, FinC2S::write, FinC2S::read, FinC2S::handle);
    }

    /** Lance la lecture de ce clip chez le client. */
    public record ClipS2C(Clip clip) {
        public void write(FriendlyByteBuf buf) {
            clip.write(buf);
        }

        public static ClipS2C read(FriendlyByteBuf buf) {
            return new ClipS2C(Clip.read(buf));
        }

        public static void handle(ClipS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Interrompt la killcam en cours (reapparition forcee, fin de partie). */
    public record ArretS2C() {
        public void write(FriendlyByteBuf buf) {
        }

        public static ArretS2C read(FriendlyByteBuf buf) {
            return new ArretS2C();
        }

        public static void handle(ArretS2C p, Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(p);
            ctx.get().setPacketHandled(true);
        }
    }

    /** Le client a fini (ou passe) sa killcam : le serveur ne la considere plus en cours. */
    public record FinC2S(boolean passee) {
        public void write(FriendlyByteBuf buf) {
            buf.writeBoolean(passee);
        }

        public static FinC2S read(FriendlyByteBuf buf) {
            return new FinC2S(buf.readBoolean());
        }

        public static void handle(FinC2S p, Supplier<NetworkEvent.Context> ctx) {
            // traitement : ReceptionPaquetsKillcamServeur (src/serveur), branché par la partie serveur
            MatchsNet.recevoirServeur(p, ctx);
        }
    }
}
