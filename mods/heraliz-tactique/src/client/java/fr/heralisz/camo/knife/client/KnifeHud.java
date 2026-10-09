package fr.heralisz.camo.knife.client;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import fr.heralisz.camo.HeraliszCamo;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Couteau TaCZ en main (karambit, butterfly, M9 baïonnette, couteaux CT / T...) : pas d'interface d'arme TaCZ en bas à
 * droite (munitions, mode de tir, rechargement), sans objet pour une arme blanche.
 * Un couteau = une arme TaCZ dont la fiche technique est arkaniaz:knife_data (tous les couteaux des gunpacks).
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, value = Dist.CLIENT)
public final class KnifeHud {
   /** Interface d'arme de TaCZ (munitions, rechargement) et fiche technique commune aux couteaux. */
   private static final ResourceLocation TACZ_GUN_HUD = new ResourceLocation("tacz", "tac_gun_hud_overlay");
   private static final ResourceLocation KNIFE_DATA = new ResourceLocation("arkaniaz", "knife_data");

   private KnifeHud() {
   }

   @SubscribeEvent
   public static void onOverlay(RenderGuiOverlayEvent.Pre event) {
      if (!TACZ_GUN_HUD.equals(event.getOverlay().id())) return;
      Minecraft mc = Minecraft.getInstance();
      if (mc.player != null && isKnife(mc.player.getMainHandItem())) event.setCanceled(true);
   }

   /** L'objet est-il un couteau TaCZ ? */
   public static boolean isKnife(ItemStack stack) {
      IGun gun = IGun.getIGunOrNull(stack);
      if (gun == null) return false;
      ResourceLocation id = gun.getGunId(stack);
      return id != null && TimelessAPI.getCommonGunIndex(id).map(i -> KNIFE_DATA.equals(i.getPojo().getData())).orElse(false);
   }
}
