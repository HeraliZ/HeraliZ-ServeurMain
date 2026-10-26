package fr.heraliz.crac.build.net;

import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.build.BuildConfig;
import fr.heraliz.crac.build.client.PieceInfoCache;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/**
 * Réception côté client des paquets serveur -> client (docs/METHODE_CLIENT_SERVEUR.md §6) : corps d'origine des
 * méthodes « handleClient » / « openScreen », branchés par client/PartieClient.
 */
public final class ReceptionClientBuild {
    private ReceptionClientBuild() {
    }

    /** Corps d'origine de S2CPieceInfo.handleClient (réception côté client). */
    public static void s2CPieceInfo(S2CPieceInfo msg) {
        PieceInfoCache.put(msg);
    }
}
