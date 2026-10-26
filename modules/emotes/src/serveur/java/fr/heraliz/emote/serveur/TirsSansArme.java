package fr.heraliz.emote.serveur;

import com.tacz.guns.api.event.common.GunMeleeEvent;
import com.tacz.guns.api.event.common.GunShootEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.fml.LogicalSide;

/**
 * Tirs et coups de TaCZ refusés par le serveur pendant une emote et le délai qui la suit ({@link SansArme}) : la règle
 * ne dépend plus seulement de l'arme retirée de la main. TaCZ tire avec l'arme « sortie » (paquet de TaCZ : la case
 * choisie au moment de la sortir), pas avec l'objet en main ; un jeu modifié qui ne ressort rien quand le serveur change
 * de case, ou un inventaire plein d'armes (rien pour l'échanger), gardait sinon l'arme utilisable. Annulé, TaCZ ne tire
 * rien (ni balle, ni munition, ni son pour les autres). Chargé seulement avec TaCZ ({@code PartieServeur}).
 */
final class TirsSansArme {
    private TirsSansArme() {
    }

    static void brancher() {
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunShootEvent.class,
                e -> refuser(e, e.getShooter(), e.getLogicalSide()));
        MinecraftForge.EVENT_BUS.addListener(EventPriority.HIGH, false, GunMeleeEvent.class,
                e -> refuser(e, e.getShooter(), e.getLogicalSide()));
    }

    private static void refuser(Event e, LivingEntity tireur, LogicalSide cote) {
        if (cote != LogicalSide.SERVER || !(tireur instanceof ServerPlayer p)) return;
        long reste = SansArme.restant(p);
        if (reste <= 0) return;
        e.setCanceled(true);
        SansArme.prevenir(p, reste);
    }
}
