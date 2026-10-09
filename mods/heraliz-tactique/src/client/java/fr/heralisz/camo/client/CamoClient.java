package fr.heralisz.camo.client;

import com.tacz.guns.api.item.IGun;
import com.tacz.guns.client.gui.GunRefitScreen;
import fr.heralisz.camo.Camo;
import fr.heralisz.camo.CamoData;
import fr.heralisz.camo.CollectionVue;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class CamoClient {
   /** Camo à afficher : l'aperçu choisi dans l'onglet Skin (arme en main, écran ouvert), sinon celui de l'arme. */
   @Nullable
   public static Camo camoFor(ItemStack stack) {
      Minecraft mc = Minecraft.getInstance();
      if (RefitSkinTab.preview != null && mc.screen instanceof GunRefitScreen && mc.player != null && stack == mc.player.getMainHandItem()) {
         return RefitSkinTab.preview;
      }
      return CamoData.get(stack);
   }

   @SubscribeEvent
   public static void onTooltip(ItemTooltipEvent event) {
      Camo camo = CamoData.get(event.getItemStack());
      if (camo != null && IGun.getIGunOrNull(event.getItemStack()) != null) {
         // camo à vie (collection d'un joueur) : il ne suit pas l'arme si elle change de main
         String cle = CamoData.owner(event.getItemStack()) != null ? "tooltip.heralisz_camo.gun_camo_life" : "tooltip.heralisz_camo.gun_camo";
         event.getToolTip().add(1, Component.translatable(cle,
            Component.translatable(camo.nameKey()).withStyle(camo.tier.color, ChatFormatting.BOLD)).withStyle(ChatFormatting.GRAY));
      } else if (camo != null && fr.heralisz.camo.BagSupport.isBag(event.getItemStack())) {
         event.getToolTip().add(1, Component.translatable("tooltip.heralisz_camo.bag_camo",
            Component.translatable(camo.nameKey()).withStyle(camo.tier.color, ChatFormatting.BOLD)).withStyle(ChatFormatting.GRAY));
      }
   }

   /** Déconnexion : la collection à vie d'un serveur ne reste pas affichée sur un autre. */
   @SubscribeEvent
   public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
      CollectionVue.vider();
   }

   @SubscribeEvent
   public static void onClientTick(TickEvent.ClientTickEvent event) {
      if (event.phase == TickEvent.Phase.END && Minecraft.getInstance().screen instanceof GunRefitScreen screen) {
         RefitSkinTab.refreshIfChanged(screen);
      }
   }

   private CamoClient() {}
}
