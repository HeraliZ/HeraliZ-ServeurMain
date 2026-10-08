package fr.heraliz.wonder.wintershowl.client;

import com.tacz.guns.api.event.common.GunShootEvent;
import fr.heraliz.wonder.Regles;
import fr.heraliz.wonder.net.Regle;
import fr.heraliz.wonder.wintershowl.WintersHowl;
import net.minecraft.ChatFormatting;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Branchements TaCZ cote client du Winter's Howl (seule classe client du paquet qui voit TaCZ) : un tir accepte lance tout
 * de suite le souffle chez le tireur (prediction : brume, cristaux, givre au sol, voile d'ecran, son ; le serveur decide
 * du gel) ; hors partie de zombies le tir est refuse comme sur le serveur ({@code TaczClient} le fait deja quand
 * {@code Armes} connait l'arme : ici en secours, sans double message).
 */
final class TaczGivreClient {
    private TaczGivreClient() {
    }

    private static long dernierRefus;

    static void brancher(IEventBus f) {
        f.addListener(EventPriority.NORMAL, false, GunShootEvent.class, TaczGivreClient::avantTir);
        f.addListener(EventPriority.LOW, false, com.tacz.guns.api.event.common.GunReloadEvent.class, TaczGivreClient::recharge);
    }

    /** Rechargement du joueur local : degazage de la cartouche d'azote (effet seulement). */
    private static void recharge(com.tacz.guns.api.event.common.GunReloadEvent e) {
        if (e.getLogicalSide() != LogicalSide.CLIENT || !(e.getEntity() instanceof LocalPlayer) || !WintersHowl.est(e.getGunItemStack())) return;
        com.tacz.guns.api.item.IGun g = com.tacz.guns.api.item.IGun.getIGunOrNull(e.getGunItemStack());
        FxGivre.recharge(g != null && g.getCurrentAmmoCount(e.getGunItemStack()) <= 0);
    }

    private static void avantTir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.CLIENT || !(e.getShooter() instanceof LocalPlayer p) || !WintersHowl.est(e.getGunItemStack())) return;
        if (p.isCreative() || p.isSpectator() || Regle.clientTirHorsZombies || Regles.dansDimensionZombies(p.level())) {
            FxGivre.tirLocal(p, e.getGunItemStack());
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
