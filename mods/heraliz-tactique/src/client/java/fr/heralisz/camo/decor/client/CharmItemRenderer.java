package fr.heralisz.camo.decor.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.camo.decor.DecorCatalog;
import fr.heralisz.camo.decor.DecorItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Pendentifs de mobs en objet (inventaire, main, sol) : le mob en 3D, animé (modèle d'objet « builtin/entity »). */
public final class CharmItemRenderer extends BlockEntityWithoutLevelRenderer {
   private static CharmItemRenderer instance;

   private CharmItemRenderer() {
      super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
   }

   public static CharmItemRenderer get() {
      if (instance == null) instance = new CharmItemRenderer();
      return instance;
   }

   @Override
   public void renderByItem(ItemStack stack, ItemDisplayContext ctx, PoseStack ps, MultiBufferSource buffers, int light, int overlay) {
      if (stack.getItem() instanceof DecorItems.CharmItem ci && ci.design.kind == DecorCatalog.Charm.Kind.MOB) {
         MobCharms.renderItem(ci.design, ps, buffers, light);
      }
   }
}
