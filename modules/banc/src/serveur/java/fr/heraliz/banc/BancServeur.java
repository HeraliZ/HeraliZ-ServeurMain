package fr.heraliz.banc;

import fr.heraliz.noyau.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie serveur du banc : les bots de charge. */
public final class BancServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        Bots.init();
    }
}
