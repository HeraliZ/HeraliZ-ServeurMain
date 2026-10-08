package fr.heraliz.wonder.paralyzer;

import com.tacz.guns.api.event.common.GunShootEvent;
import fr.heraliz.wonder.Armes;
import fr.heraliz.wonder.HeralizWonder;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Seule classe serveur du Paralyzer qui voit TaCZ (chargee seulement si TaCZ est la) : chaque impulsion acceptee
 * ({@code GunShootEvent} cote serveur, apres le refus « hors zombies » du mod, priorite HIGH) devient une impulsion du
 * rayon ({@link RayonServeur#impulsion}). Le script Lua du gunpack ne cree aucune balle : il ne tient que la charge.
 */
public final class TaczParalyseur {
    private TaczParalyseur() {
    }

    static void brancher(IEventBus bus) {
        bus.addListener(EventPriority.LOW, false, GunShootEvent.class, TaczParalyseur::tir);
    }

    private static void tir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.SERVER || Armes.type(e.getGunItemStack()) != Armes.PARALYSEUR) return;
        if (!(e.getShooter().level() instanceof ServerLevel niveau)) return;
        try {
            RayonServeur.impulsion(niveau, e.getShooter(), Armes.pap(e.getGunItemStack()));
        } catch (RuntimeException ex) {
            HeralizWonder.LOG.error("[wonder] impulsion du Paralyzer", ex);
        }
    }
}
