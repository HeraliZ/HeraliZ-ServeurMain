package fr.heralisz.bases.pillage;

import fr.heraliz.clansetbases.pont.Crochet;
import fr.heralisz.bases.network.BasesNet;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Paquets du module pillage / blindage, ajoutes au canal du coeur ({@code heralisz_bases:main}) APRES ses messages, dans
 * le meme ordre des deux cotes (depuis {@link PillageModule#init}). Les S2C delèguent a
 * {@code blindage.client.BlindageClient} (classe client seulement).
 */
public final class PillageNet {
    private PillageNet() {
    }

    public static void register() {
        NetworkDirection s2c = NetworkDirection.PLAY_TO_CLIENT;
        BasesNet.msg(InfoBlindageS2C.class, s2c, InfoBlindageS2C::write, InfoBlindageS2C::read, InfoBlindageS2C::handle);
        BasesNet.msg(ProtectionS2C.class, s2c, ProtectionS2C::write, ProtectionS2C::read, ProtectionS2C::handle);
    }

    /**
     * Paquets recus par le client : branche par la partie client du mod (aiguillage vers les traitements d'origine),
     * neutre ailleurs (serveur dedie, jar -serveur).
     */
    public static final Crochet<Object> RECEPTION_CLIENT = new Crochet<>();

    /**
     * Infobulle d'un bloc protege (clic droit accroupi, main vide) : position, id du bloc (nom traduit cote client),
     * niveau, coups encaisses.
     */
    public record InfoBlindageS2C(BlockPos pos, String bloc, int niveau, int coups) {
        void write(FriendlyByteBuf b) {
            b.writeBlockPos(pos);
            b.writeUtf(bloc, 128);
            b.writeVarInt(niveau);
            b.writeVarInt(coups);
        }

        static InfoBlindageS2C read(FriendlyByteBuf b) {
            return new InfoBlindageS2C(b.readBlockPos(), b.readUtf(128), b.readVarInt(), b.readVarInt());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }

    /** Table des niveaux (id de bloc -> niveau) et blocs-pieces : infobulles d'objet et clic droit cote client. */
    public record ProtectionS2C(Map<String, Integer> niveaux, List<String> parties) {
        void write(FriendlyByteBuf b) {
            b.writeVarInt(niveaux.size());
            for (Map.Entry<String, Integer> e : niveaux.entrySet()) {
                b.writeUtf(e.getKey(), 128);
                b.writeVarInt(e.getValue());
            }
            b.writeVarInt(parties.size());
            for (String s : parties) b.writeUtf(s, 128);
        }

        static ProtectionS2C read(FriendlyByteBuf b) {
            int n = Math.min(4096, b.readVarInt());
            Map<String, Integer> m = new LinkedHashMap<>();
            for (int i = 0; i < n; i++) m.put(b.readUtf(128), b.readVarInt());
            int k = Math.min(256, b.readVarInt());
            List<String> p = new ArrayList<>(k);
            for (int i = 0; i < k; i++) p.add(b.readUtf(128));
            return new ProtectionS2C(m, p);
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            RECEPTION_CLIENT.accept(this);
        }
    }
}
