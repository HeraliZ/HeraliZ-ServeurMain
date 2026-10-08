package fr.heralisz.tactical.init;

import com.tacz.guns.init.ModCreativeTabs;
import fr.heralisz.tactical.api.HeraliszTacticalAPI;
import fr.heralisz.tactical.item.index.MeleeWeaponIndex;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/** HeralisZ : toutes les armes de mêlée vont dans l'onglet « Heavy Weapon » de TaCZ, à côté des couteaux. */
@EventBusSubscriber(modid = "heralisz_tactical", bus = Bus.MOD)
public final class HeraliszTabs {
   private HeraliszTabs() {}

   @SubscribeEvent
   public static void build(BuildCreativeModeTabContentsEvent e) {
      if (e.getTabKey().equals(ModCreativeTabs.GUN_RPG_TAB.getKey())) {
         for (MeleeWeaponIndex<?> index : HeraliszTacticalAPI.getMeleeIndexes()) {
            e.accept(index.createItemStack());
         }
      }
   }
}
