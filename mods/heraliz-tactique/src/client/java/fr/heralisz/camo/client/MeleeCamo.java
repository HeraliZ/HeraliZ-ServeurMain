package fr.heralisz.camo.client;

import fr.heralisz.camo.HeraliszCamo;
import fr.heralisz.camo.knife.KnifeFinish;
import fr.heralisz.camo.knife.KnifeType;
import fr.heralisz.tactical.api.client.MeleeRenderHooks;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Skins animés (shader) des armes de mêlée HeralisZ Tactical : une arme « heralisz_melee:&lt;arme&gt;__&lt;finition&gt; »
 * est dessinée avec la finition sur la lame (rôles des pièces : KnifeType), le manche garde sa texture.
 */
@Mod.EventBusSubscriber(modid = HeraliszCamo.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class MeleeCamo {
   private static final String NAMESPACE = "heralisz_melee";

   private MeleeCamo() {
   }

   @SubscribeEvent
   public static void onClientSetup(FMLClientSetupEvent event) {
      MeleeRenderHooks.set((stack, model, texture, original) -> {
         Skin skin = of(stack);
         if (skin == null || CamoShaders.get(skin.finish.shaderId) == null) {
            return original;
         }
         PosMap map = PosMapCache.get(model, skin.knife);
         // effets des armes à feu (apparition, chauffe, niveau) lus par le shader au dessin : sinon la lame reprenait ceux de
         // la dernière arme TaCZ dessinée (bandes d'hologramme sur une partie de la lame)
         CamoFx.pendingReveal = -1;
         CamoFx.pendingHeat = 0;
         CamoFx.pendingKills = 0;
         return map == null ? original
            : CamoRenderType.get(texture, map.glId, skin.finish.shaderId, 15 | ((skin.knife.ordinal() + 1) << 4));
      });
   }

   private record Skin(KnifeType knife, KnifeFinish finish) {}

   private static Skin of(ItemStack stack) {
      CompoundTag tag = stack.getTag();
      if (tag == null || !tag.contains("MeleeWeaponId", 8)) {
         return null;
      }
      ResourceLocation id = ResourceLocation.tryParse(tag.getString("MeleeWeaponId"));
      if (id == null || !NAMESPACE.equals(id.getNamespace())) {
         return null;
      }
      String[] parts = id.getPath().split("__", 2);
      if (parts.length != 2) {
         return null;
      }
      KnifeType knife = KnifeType.byId(parts[0]);
      KnifeFinish finish = KnifeFinish.byId(parts[1]);
      return knife == null || finish == null ? null : new Skin(knife, finish);
   }
}
