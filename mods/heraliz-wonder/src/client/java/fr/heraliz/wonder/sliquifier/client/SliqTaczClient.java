package fr.heraliz.wonder.sliquifier.client;

import com.tacz.guns.api.event.common.GunShootEvent;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.net.Regle;
import fr.heraliz.wonder.sliquifier.Sliquifier;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Branchements TaCZ du Sliquifier cote client (seule classe client du paquet qui voit TaCZ) : hors partie de zombies le
 * tir est refuse tout de suite (comme sur le serveur : pas d'animation ni de son pour rien) ; un tir accepte lance tout
 * de suite sa boule de gelee (prediction, recalee par le paquet du serveur).
 */
public final class SliqTaczClient {
    private SliqTaczClient() {
    }

    private static long dernierRefus;

    static void brancher(IEventBus f) {
        f.addListener(EventPriority.HIGH, false, GunShootEvent.class, SliqTaczClient::avantTir);
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.CLIENT || !(e.getShooter() instanceof LocalPlayer p) || !Sliquifier.est(e.getGunItemStack())) return;
        if (p.isCreative() || p.isSpectator() || Regle.clientTirHorsZombies || Regles.dansDimensionZombies(p.level())) {
            FxSliq.tirLocal(p, e.getGunItemStack());
            return;
        }
        e.setCanceled(true);
        long now = System.currentTimeMillis();
        if (now - dernierRefus > 1500) {
            dernierRefus = now;
            p.displayClientMessage(Component.translatable("heraliz_wonder.refus.hors_zombies").withStyle(ChatFormatting.RED), true);
        }
    }
}
