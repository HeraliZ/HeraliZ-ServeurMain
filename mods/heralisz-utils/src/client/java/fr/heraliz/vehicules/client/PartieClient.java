package fr.heraliz.vehicules.client;

import fr.heralisz.utils.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client de heraliz_vehicules (jar fusionné HeraliZ Utils) : touches, caméra, HUD des véhicules. */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        ClientVehicules.init(busMod);
    }
}
