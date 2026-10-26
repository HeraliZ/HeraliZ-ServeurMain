package fr.heraliz.emote.client;

import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

/**
 * Tir et coup de TaCZ du joueur local annulés avant de partir pendant une emote et le délai qui la suit
 * ({@link SansArmeClient}) : ni son, ni balle, ni munition à l'écran ; le serveur les refuse de toute façon. Chargé
 * seulement avec TaCZ ({@code PartieClient}).
 */
final class TirsClient {
    private TirsClient() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunShootEvent.class,
                e -> refuser(e, e.getShooter(), e.getLogicalSide()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunMeleeEvent.class,
                e -> refuser(e, e.getShooter(), e.getLogicalSide()));
    }

    private static void refuser(Event e, LivingEntity tireur, LogicalSide cote) {
        if (cote != LogicalSide.CLIENT || tireur == null || tireur != Minecraft.getInstance().player) return;
        long reste = SansArmeClient.restant();
        if (reste <= 0) return;
        e.setCanceled(true);
        SansArmeClient.prevenir(reste);
    }
}
