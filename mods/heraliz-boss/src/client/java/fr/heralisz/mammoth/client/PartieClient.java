package fr.heralisz.mammoth.client;

import fr.heraliz.boss.pont.Partie;
import net.minecraftforge.eventbus.api.IEventBus;

/**
 * Partie client (affichage) : chargée par le @Mod via Parties.client, seulement sur un client physique et si elle est dans
 * le jar (-client ou -complet). Branche les crochets du code commun vers le code de dessin. Les rendus, le HUD, les sons
 * et les effets s'inscrivent eux-mêmes par @Mod.EventBusSubscriber(modid = ..., value = Dist.CLIENT).
 */
public final class PartieClient implements Partie {
   @Override
   public void init(IEventBus busMod) {
      fr.heralisz.mammoth.vfx.Vfx.RECEPTION_CLIENT.brancher(fr.heralisz.mammoth.client.vfx.VfxManager::receive);
   }
}
