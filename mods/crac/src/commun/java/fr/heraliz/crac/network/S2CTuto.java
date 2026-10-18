package fr.heraliz.crac.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import fr.heraliz.crac.build.model.PieceKey;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Le tutoriel des bases, pour l'affichage chez le joueur : l'étape (0 = pas de tutoriel), les pièces à poser à leur
 * place exacte (fantômes verts qui clignotent ; à l'étape du bois, les pièces à améliorer), les blocs à montrer
 * (Claim Block, caisse), l'objet à prendre en main (surligné dans la barre d'objets), la pièce à choisir dans la roue
 * du plan (-1 : aucune) et le choix « Bois » à montrer dans la roue du marteau. Le serveur revérifie chaque pose.
 */
public record S2CTuto(int etape, List<PieceKey> cibles, List<BlockPos> blocs, String objet, int roue, boolean bois) {
    static final int MAX = 64;

    public static final S2CTuto AUCUN = new S2CTuto(0, List.of(), List.of(), "", -1, false);

    public static void encode(S2CTuto msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.etape);
        buf.writeVarInt(Math.min(msg.cibles.size(), MAX));
        for (int i = 0; i < Math.min(msg.cibles.size(), MAX); i++) {
            msg.cibles.get(i).write(buf);
        }
        buf.writeVarInt(Math.min(msg.blocs.size(), MAX));
        for (int i = 0; i < Math.min(msg.blocs.size(), MAX); i++) {
            buf.writeBlockPos(msg.blocs.get(i));
        }
        buf.writeUtf(msg.objet, 128);
        buf.writeVarInt(msg.roue + 1);
        buf.writeBoolean(msg.bois);
    }

    public static S2CTuto decode(FriendlyByteBuf buf) {
        int etape = buf.readVarInt();
        int n = PacketLimits.readCount(buf, MAX, 17);
        List<PieceKey> cibles = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            PieceKey k = PieceKey.read(buf);
            if (k != null) {
                cibles.add(k);
            }
        }
        int nb = PacketLimits.readCount(buf, MAX, 8);
        List<BlockPos> blocs = new ArrayList<>(nb);
        for (int i = 0; i < nb; i++) {
            blocs.add(buf.readBlockPos());
        }
        String objet = buf.readUtf(128);
        if (!objet.isEmpty() && ResourceLocation.tryParse(objet) == null) {
            objet = "";
        }
        return new S2CTuto(etape, cibles, blocs, objet, buf.readVarInt() - 1, buf.readBoolean());
    }

    public static void handle(S2CTuto msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            if (FMLEnvironment.dist == Dist.CLIENT) {
                NetworkHandler.RECEPTION_CLIENT.accept(msg);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
