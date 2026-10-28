package fr.heraliz.crac.network;

import fr.heraliz.crac.block.OrientationObjet;
import java.util.function.Supplier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.NetworkEvent.Context;

/** Serveur : garde les quarts de tour choisis avec R pour les objets collés aux murs (OrientationObjet, 28/10). */
@EventBusSubscriber(modid = "crac")
public final class C2SRotationObjetTraitement {
    private C2SRotationObjetTraitement() {
    }

    public static void traiter(C2SRotationObjet msg, Supplier<Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        int quarts = msg.quarts() & 3;
        ctx.get().enqueueWork(() -> {
            if (p != null) {
                OrientationObjet.serveur(p.getUUID(), quarts);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent event) {
        OrientationObjet.oublier(event.getEntity().getUUID());
    }
}
