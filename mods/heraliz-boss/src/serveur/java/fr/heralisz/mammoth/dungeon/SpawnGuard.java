package fr.heralisz.mammoth.dungeon;

import fr.heralisz.mammoth.HeraliszMammoth;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Aucun monstre naturel dans le Cimetière (ni creeper dans la niche pendant la cinématique) : seuls les nôtres y vivent. */
@Mod.EventBusSubscriber(modid = HeraliszMammoth.MODID)
public final class SpawnGuard {
   private SpawnGuard() {}

   @SubscribeEvent
   public static void onSpawn(MobSpawnEvent.FinalizeSpawn e) {
      MobSpawnType t = e.getSpawnType();
      if (t != MobSpawnType.NATURAL && t != MobSpawnType.CHUNK_GENERATION && t != MobSpawnType.PATROL && t != MobSpawnType.REINFORCEMENT
         && t != MobSpawnType.SPAWNER && t != MobSpawnType.STRUCTURE) return;
      if (e.getEntity() instanceof fr.heralisz.mammoth.entity.Mammoth || e.getEntity() instanceof fr.heralisz.mammoth.entity.Draugr) return;
      Level level = e.getLevel().getLevel();
      if (HornAltarBlockEntity.nearest(level, e.getEntity().blockPosition(), 90) != null) e.setSpawnCancelled(true);
   }
}
