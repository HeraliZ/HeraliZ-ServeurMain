package fr.heralisz.n07.client;

import fr.heralisz.n07.scene.ScenePackets;
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
      fr.heralisz.n07.vfx.Vfx.RECEPTION_CLIENT.brancher(fr.heralisz.n07.client.vfx.VfxManager::receive);
      ScenePackets.START_CLIENT.brancher(p -> ScenePlayer.start(p.run(), p.scene(), p.skipMode()));
      ScenePackets.STOP_CLIENT.brancher(p -> ScenePlayer.stop(p.run()));
      ScenePackets.VOTES_CLIENT.brancher(p -> ScenePlayer.votes(p.run(), p.holding(), p.needed()));
      fr.heralisz.n07.decor.DecorBlockEntity.TICK_CLIENT.brancher(DecorFx::tick);
      fr.heralisz.n07.dungeon.PropBlockEntity.TICK_CLIENT.brancher(PropFx::tick);
   }
}
