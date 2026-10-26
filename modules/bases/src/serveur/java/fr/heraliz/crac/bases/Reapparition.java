package fr.heraliz.crac.bases;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import fr.heraliz.crac.network.C2SReapparition;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CReapparition;
import net.minecraft.ChatFormatting;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.NetworkEvent.Context;
import net.minecraftforge.network.PacketDistributor;

/**
 * Réapparition au camp ou à la base (25/10, demande du propriétaire). À la mort, le joueur reçoit ce que permet sa
 * situation ({@link TeleportBase#raison}) ; son écran de mort propose « Au camp » (toujours) et « À la base » (s'il a une
 * base et le droit, une fois le délai partagé avec /home fini). Son choix n'est gardé que s'il est mort ; à la
 * réapparition, le serveur revérifie tout ({@link TeleportBase#teleporter}) : délai encore en cours, droit retiré, base
 * détruite = réapparition au camp, avec un message. Le camp, c'est la réapparition habituelle (point de réveil du
 * spawn, HeraliZ Utils), qui passe avant ; la base part de là, en dernier.
 */
@EventBusSubscriber(modid = "crac")
public final class Reapparition {
    private static final Map<UUID, Boolean> CHOIX = new HashMap<>();

    private Reapparition() {
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void mort(LivingDeathEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || p.getTags().contains("heraliz_bot")) {
            return;
        }
        CHOIX.remove(p.getUUID());
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new S2CReapparition(TeleportBase.raison(p), TeleportBase.resteMs(p)));
    }

    public static void traiter(C2SReapparition m, Supplier<Context> ctx) {
        ServerPlayer p = ctx.get().getSender();
        ctx.get().enqueueWork(() -> {
            // le choix ne compte que pour un joueur mort, juste avant sa demande de réapparition
            if (p != null && !p.isAlive()) {
                CHOIX.put(p.getUUID(), m.base());
            }
        });
        ctx.get().setPacketHandled(true);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void reapparition(PlayerEvent.PlayerRespawnEvent e) {
        if (e.isEndConquered() || !(e.getEntity() instanceof ServerPlayer p)) {
            return;
        }
        Boolean base = CHOIX.remove(p.getUUID());
        if (base == null || !base) {
            return;
        }
        int r = TeleportBase.teleporter(p);
        if (r == TeleportBase.OK) {
            Deplacements.apres(p, T.t("crac.reapparition.base", "Te voici à ta base. Prochaine téléportation vers ta base (/home compris) dans %s.",
                T.duree(TeleportBase.resteMs(p) + 999)).withStyle(ChatFormatting.GREEN));
        } else if (r < 0) {
            Deplacements.apres(p, T.t("crac.reapparition.obstrue",
                "Réapparition à la base impossible : la place devant l'armoire est bouchée. Te voici au camp.").withStyle(ChatFormatting.RED));
        } else {
            Deplacements.apres(p, T.t("crac.reapparition.refus", "Réapparition à la base impossible : %s Te voici au camp.",
                TeleportBase.texte(p, r)).withStyle(ChatFormatting.RED));
        }
    }

    /**
     * Connexion d'un joueur encore mort (déconnecté sur l'écran de mort, ou tué en se déconnectant en combat) : son écran
     * de mort se rouvre sans nouvelle mort, il lui faut l'état de maintenant (avant : « Vérification… » sans fin et
     * « À la base » jamais proposé).
     */
    @SubscribeEvent
    public static void connexion(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.getEntity() instanceof ServerPlayer p && !p.isAlive() && !p.getTags().contains("heraliz_bot")) {
            CHOIX.remove(p.getUUID());
            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new S2CReapparition(TeleportBase.raison(p), TeleportBase.resteMs(p)));
        }
    }

    @SubscribeEvent
    public static void deconnexion(PlayerEvent.PlayerLoggedOutEvent e) {
        CHOIX.remove(e.getEntity().getUUID());
    }
}
