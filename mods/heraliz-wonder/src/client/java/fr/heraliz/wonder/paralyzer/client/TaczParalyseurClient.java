package fr.heraliz.wonder.paralyzer.client;

import com.tacz.guns.api.event.common.GunShootEvent;
import fr.heraliz.wonder.Armes;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.LogicalSide;

/**
 * Seule classe client du Paralyzer qui voit TaCZ : chaque tir accepte annonce une impulsion du rayon chez ce client
 * (tireur local : son propre tir, tout de suite ; autres joueurs : TaCZ relaie leurs tirs aux joueurs qui les voient).
 * Priorite basse : le refus « hors zombies » du mod (priorite haute) passe avant ; un tir annule ne dessine rien.
 */
public final class TaczParalyseurClient {
    private TaczParalyseurClient() {
    }

    static void brancher(IEventBus f) {
        f.addListener(EventPriority.LOW, false, GunShootEvent.class, TaczParalyseurClient::tir);
    }

    private static void tir(GunShootEvent e) {
        if (e.getLogicalSide() != LogicalSide.CLIENT || Armes.type(e.getGunItemStack()) != Armes.PARALYSEUR) return;
        FxParalyseur.impulsion(e.getShooter(), Armes.pap(e.getGunItemStack()));
    }
}
