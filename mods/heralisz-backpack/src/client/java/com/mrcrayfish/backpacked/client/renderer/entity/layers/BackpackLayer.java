package com.mrcrayfish.backpacked.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.mrcrayfish.backpacked.client.renderer.backpack.BackpackRenderContext;
import com.mrcrayfish.backpacked.common.backpack.Backpack;
import com.mrcrayfish.backpacked.common.backpack.BackpackManager;
import com.mrcrayfish.backpacked.common.backpack.ModelMeta;
import com.mrcrayfish.backpacked.common.backpack.ModelMetas;
import com.mrcrayfish.backpacked.common.backpack.ModelProperty;
import com.mrcrayfish.backpacked.item.BackpackItem;
import com.mrcrayfish.backpacked.platform.ClientServices;
import com.mrcrayfish.backpacked.platform.Services;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

public class BackpackLayer<T extends Player, M extends PlayerModel<T>> extends RenderLayer<T, M> {
   private final ItemRenderer itemRenderer;

   public BackpackLayer(RenderLayerParent<T, M> var1, ItemRenderer var2) {
      super(var1);
      this.itemRenderer = var2;
   }

   public void render(PoseStack var1, MultiBufferSource var2, int var3, T var4, float var5, float var6, float var7, float var8, float var9, float var10) {
      ItemStack var11 = Services.BACKPACK.getBackpackStack(var4);
      if (var11.getItem() instanceof BackpackItem) {
         ItemStack var12 = var4.getItemBySlot(EquipmentSlot.CHEST);
         if (var12.getItem() == Items.ELYTRA && !canRenderWithElytra(var11)) {
            return;
         }

         if (!Services.BACKPACK.isBackpackVisible(var4)) {
            return;
         }

         ResourceLocation var14 = ForgeRegistries.ITEMS.getKey(var11.getItem());
         Backpack var15 = var14 != null ? BackpackManager.instance().getClientBackpack(var14) : null;
         if (var15 == null) {
            // HeraliZ (optimisation) : le modele de secours n'est cherche que si le sac n'a pas de modele a son nom ;
            // le nom de l'objet n'est plus traduit 8 fois par image et par joueur, mais une seule fois et seulement ici.
            // Memes regles, meme ordre (la derniere couleur trouvee l'emporte).
            String var13 = var11.getOrCreateTag().getString("BackpackModel");
            String nom = var11.getItem().getName(var11).getString();
            if (nom.contains("black")) {
               var13 = "backpacked:mountain_black";
            }

            if (nom.contains("red")) {
               var13 = "backpacked:mountain_red";
            }

            if (nom.contains("green")) {
               var13 = "backpacked:mountain_green";
            }

            if (nom.contains("yellow")) {
               var13 = "backpacked:mountain_yellow";
            }

            if (nom.contains("white")) {
               var13 = "backpacked:mountain_white";
            }

            if (nom.contains("orange")) {
               var13 = "backpacked:mountain_orange";
            }

            if (nom.contains("pink")) {
               var13 = "backpacked:mountain_pink";
            }

            if (nom.contains("blue")) {
               var13 = "backpacked:mountain_blue";
            }

            var15 = BackpackManager.instance().getClientBackpackOrDefault(var13);
         }

         Backpack var16 = var15;
         if (var16 == null) {
            return;
         }

         var1.pushPose();
         ((PlayerModel)this.getParentModel()).body.translateAndRotate(var1);
         var1.mulPose(Axis.YP.rotationDegrees(180.0F));
         var1.scale(1.05F, -1.05F, -1.05F);
         int var17 = !var12.isEmpty() ? 3 : 2;
         var1.translate(0.0, -0.06, var17 * 0.0625);
         ModelMeta var18 = ModelMetas.getModelMeta(var16);
         var18.renderer()
            .ifPresentOrElse(
               var8x -> {
                  var1.pushPose();
                  BackpackRenderContext var9x = new BackpackRenderContext(
                     var1,
                     var2,
                     var3,
                     var11,
                     var16,
                     var4,
                     var7,
                     var4.tickCount,
                     var5xx -> this.itemRenderer.render(var11, ItemDisplayContext.NONE, false, var1, var2, var3, OverlayTexture.NO_OVERLAY, var5xx)
                  );
                  var8x.forEach(var1xx -> var1xx.apply(var9x));
                  var1.popPose();
               },
               () -> {
                  BakedModel var6x = ClientServices.MODEL.getBakedModel(var16.getBaseModel());
                  this.itemRenderer.render(var11, ItemDisplayContext.NONE, false, var1, var2, var3, OverlayTexture.NO_OVERLAY, var6x);
               }
            );
         this.itemRenderer.render(var11, ItemDisplayContext.NONE, false, var1, var2, var3, OverlayTexture.NO_OVERLAY, this.getModel(var16.getStrapsModel()));
         var1.popPose();
      }
   }

   private BakedModel getModel(ResourceLocation var1) {
      BakedModel var2 = ClientServices.MODEL.getBakedModel(var1);
      return var2 != null ? var2 : this.itemRenderer.getItemModelShaper().getModelManager().getMissingModel();
   }

   public static boolean canRenderWithElytra(ItemStack var0) {
      return var0.getOrCreateTag().getBoolean(ModelProperty.SHOW_WITH_ELYTRA.getTagName());
   }

   public static boolean canShowEnchantmentGlint(ItemStack var0) {
      return var0.getOrCreateTag().getBoolean(ModelProperty.SHOW_GLINT.getTagName());
   }
}
