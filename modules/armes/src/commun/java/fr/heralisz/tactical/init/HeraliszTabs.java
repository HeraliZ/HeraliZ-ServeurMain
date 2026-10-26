package fr.heralisz.tactical.init;

import com.tacz.guns.init.ModCreativeTabs;
import fr.heraliz.tactique.onglets.Couteaux;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

/**
 * HeraliZ : l'onglet « Arme lourde » de TaCZ ne garde que les vraies armes lourdes. Les couteaux des gunpacks (que TaCZ
 * y range) en sont retirés ; eux et les armes de mêlée sont dans le groupe Mêlée de l'onglet HeraliZ · Tactique
 * (ModItems.remplirMelee).
 */
@EventBusSubscriber(modid = "heralisz_tactical", bus = Bus.MOD)
public final class HeraliszTabs {
   private HeraliszTabs() {}

   @SubscribeEvent
   public static void build(BuildCreativeModeTabContentsEvent e) {
      if (!e.getTabKey().equals(ModCreativeTabs.GUN_RPG_TAB.getKey())) {
         return;
      }
      List<ItemStack> couteaux = new ArrayList<>();
      for (Map.Entry<ItemStack, CreativeModeTab.TabVisibility> en : e.getEntries()) {
         if (Couteaux.estCouteau(en.getKey())) {
            couteaux.add(en.getKey());
         }
      }
      couteaux.forEach(e.getEntries()::remove);
   }
}
