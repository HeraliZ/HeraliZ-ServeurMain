package fr.heraliz.capture.client;

import fr.heraliz.capture.pont.Partie;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;

/** Partie client de HeraliZ Capture : le lecteur de script, à chaque tick du client. */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        MinecraftForge.EVENT_BUS.addListener(Script::tick);
    }
}
