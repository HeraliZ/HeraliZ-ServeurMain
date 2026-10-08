package fr.heralisz.mere.dungeon;

import fr.heralisz.mere.HeraliszMere;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Aucun monstre naturel dans le Nid : seule la couvée y vit. */
@Mod.EventBusSubscriber(modid = HeraliszMere.MODID)
public final class SpawnGuard {
   private SpawnGuard() {}

   @SubscribeEvent
   public static void onSpawn(MobSpawnEvent.FinalizeSpawn e) {
      MobSpawnType t = e.getSpawnType();
      if (t != MobSpawnType.NATURAL && t != MobSpawnType.CHUNK_GENERATION && t != MobSpawnType.PATROL && t != MobSpawnType.REINFORCEMENT
         && t != MobSpawnType.SPAWNER && t != MobSpawnType.STRUCTURE) return;
      if (e.getEntity() instanceof fr.heralisz.mere.entity.RuinMother) return;
      Level level = e.getLevel().getLevel();
      if (NestHallBE.nearest(level, e.getEntity().blockPosition(), 100) != null) e.setSpawnCancelled(true);
   }
}
