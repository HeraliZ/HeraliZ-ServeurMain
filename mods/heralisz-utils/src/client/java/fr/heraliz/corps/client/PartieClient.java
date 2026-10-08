package fr.heraliz.corps.client;

import fr.heralisz.utils.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client de heraliz_corps (jar fusionné HeraliZ Utils) : rendu du corps, écran de fouille, HUD, peaux. */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        ClientCorps.init(busMod);
    }
}
