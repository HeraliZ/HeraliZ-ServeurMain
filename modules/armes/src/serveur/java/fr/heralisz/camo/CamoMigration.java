package fr.heralisz.camo;

import com.mojang.logging.LogUtils;
import net.minecraft.world.item.Item;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.MissingMappingsEvent;
import org.slf4j.Logger;

/**
 * Objets retirés : au chargement du monde, Forge signale les ids d'objets qui n'existent plus ; on les reprend sous l'objet
 * qui les remplace, pour que les piles encore présentes (coffres, inventaires, caisses, sacs) ne soient pas perdues.
 * Le camo posé sur les armes (NBT) est repris de son côté par {@link Camo#byId}.
 * <ul>
 *    <li>0.9.12 : camo_dark_matter_origin (Dark Matter Origine, à piquots) -> camo_dark_matter.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID)
public final class CamoMigration {
   private static final Logger LOG = LogUtils.getLogger();

   private CamoMigration() {
   }

   @SubscribeEvent
   public static void onMissingMappings(MissingMappingsEvent event) {
      for (MissingMappingsEvent.Mapping<Item> m : event.getMappings(ForgeRegistries.Keys.ITEMS, HeraliszCamo.MODID)) {
         if (m.getKey().getPath().equals("camo_" + Camo.DARK_MATTER_ORIGIN_ID)) {
            m.remap(HeraliszCamo.CAMO_ITEMS.get(Camo.DARK_MATTER).get());
            LOG.info("Objet retiré {} repris en heralisz_camo:camo_dark_matter", m.getKey());
         }
      }
   }
}
