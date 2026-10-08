package fr.heraliz.crac.network;

import java.util.function.Supplier;
import fr.heraliz.crac.admin.AdminManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent.Context;

/**
 * Traitement serveur du paquet C2SAdminLogin (corps d'origine, docs/METHODE_CLIENT_SERVEUR.md §6) : le paquet commun ne garde
 * que son format ; ce traitement est appelé par serveur/PartieServeur.
 */
public final class C2SAdminLoginTraitement {
    private C2SAdminLoginTraitement() {
    }

    /** At most one login attempt per second and player, on top of the lockout in AdminManager. */
    static final long COOLDOWN_MS = 1000L;
    public static void traiter(C2SAdminLogin msg, Supplier<Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                if (PacketThrottle.allow(player, PacketThrottle.Kind.ADMIN_LOGIN, COOLDOWN_MS)) {
                    AdminManager.login(player, msg.password());
                } else {
                    player.displayClientMessage(Component.literal("§c[Admin] Wait a second between two login attempts."), false);
                    AdminManager.denyLogin(player);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }}
