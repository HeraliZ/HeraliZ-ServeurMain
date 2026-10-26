package fr.heraliz.crac.network;

import java.util.UUID;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Écran des accès de l'armoire à outils (CRac 1.10.0) : ouvrir l'onglet Accès, chercher un joueur, donner ou retirer
 * l'accès, revenir à l'entretien. Le serveur revérifie tout (distance, droits, joueur connu) : traitement dans
 * {@code bases/AccesBaseTraitement}.
 */
public record C2SAccesBase(BlockPos pos, Action action, String texte, UUID cible) {
    public enum Action { OUVRIR, CHERCHER, DONNER, RETIRER, ENTRETIEN }

    private static final UUID AUCUN = new UUID(0L, 0L);

    public static void encode(C2SAccesBase m, FriendlyByteBuf buf) {
        buf.writeBlockPos(m.pos);
        buf.writeEnum(m.action);
        buf.writeUtf(m.texte == null ? "" : m.texte, 16);
        buf.writeUUID(m.cible == null ? AUCUN : m.cible);
    }

    public static C2SAccesBase decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        Action a = PacketLimits.readEnumOrNull(buf, Action.class);
        String t = buf.readUtf(16);
        UUID c = buf.readUUID();
        return new C2SAccesBase(pos, a, t, AUCUN.equals(c) ? null : c);
    }

    public static void handle(C2SAccesBase msg, Supplier<Context> ctx) {
        NetworkHandler.recevoirServeur(msg, ctx);
    }
}
