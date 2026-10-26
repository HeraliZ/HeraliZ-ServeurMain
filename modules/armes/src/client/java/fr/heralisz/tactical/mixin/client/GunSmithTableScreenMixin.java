package fr.heralisz.tactical.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.client.resource.ClientAssetsManager;
import com.tacz.guns.client.resource.pojo.PackInfo;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import java.util.List;
import fr.heralisz.tactical.api.item.ICustomItem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.apache.commons.lang3.StringUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunSmithTableScreen.class)
public abstract class GunSmithTableScreenMixin extends AbstractContainerScreen<GunSmithTableMenu> {
   public GunSmithTableScreenMixin(GunSmithTableMenu pMenu, Inventory pPlayerInventory, Component pTitle) {
      super(pMenu, pPlayerInventory, pTitle);
   }

   @Inject(method = "renderPackInfo", at = @At("HEAD"), remap = false, cancellable = true)
   private void renderPackInfo(GuiGraphics gui, GunSmithTableRecipe recipe, CallbackInfo ci) {
      ItemStack output = recipe.getOutput();
      if (output.getItem() instanceof ICustomItem customItem) {
         ResourceLocation id = customItem.getId(output);
         PackInfo packInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
         PoseStack poseStack = gui.pose();
         if (packInfo != null) {
            poseStack.pushPose();
            poseStack.scale(0.75F, 0.75F, 1.0F);
            Component nameText = Component.translatable(packInfo.getName());
            gui.drawString(
               this.font, nameText, (int)((this.leftPos + 6) / 0.75F), (int)((this.topPos + 122) / 0.75F), ChatFormatting.DARK_GRAY.getColor(), false
            );
            poseStack.popPose();
            poseStack.pushPose();
            poseStack.scale(0.5F, 0.5F, 1.0F);
            int offsetX = (this.leftPos + 6) * 2;
            int offsetY = (this.topPos + 123) * 2;
            int nameWidth = this.font.width(nameText);
            Component ver = Component.literal("v" + packInfo.getVersion()).withStyle(ChatFormatting.UNDERLINE);
            gui.drawString(this.font, ver, (int)(offsetX + nameWidth * 0.75F / 0.5F + 5.0F), offsetY, ChatFormatting.DARK_GRAY.getColor(), false);
            offsetY += 14;
            String descKey = packInfo.getDescription();
            if (StringUtils.isNoneBlank(new CharSequence[]{descKey})) {
               Component desc = Component.translatable(descKey);

               for (FormattedCharSequence charSequence : this.font.split(desc, 245)) {
                  gui.drawString(this.font, charSequence, offsetX, offsetY, ChatFormatting.DARK_GRAY.getColor(), false);
                  offsetY += 9;
               }

               offsetY += 3;
            }

            gui.drawString(
               this.font,
               Component.translatable("gui.tacz.gun_smith_table.license").append(Component.literal(packInfo.getLicense()).withStyle(ChatFormatting.DARK_GRAY)),
               offsetX,
               offsetY,
               ChatFormatting.DARK_GRAY.getColor(),
               false
            );
            offsetY += 12;
            List<String> authors = packInfo.getAuthors();
            if (!authors.isEmpty()) {
               gui.drawString(
                  this.font,
                  Component.translatable("gui.tacz.gun_smith_table.authors")
                     .append(Component.literal(StringUtils.join(authors, ", ")).withStyle(ChatFormatting.DARK_GRAY)),
                  offsetX,
                  offsetY,
                  ChatFormatting.DARK_GRAY.getColor(),
                  false
               );
               offsetY += 12;
            }

            gui.drawString(
               this.font,
               Component.translatable("gui.tacz.gun_smith_table.date").append(Component.literal(packInfo.getDate()).withStyle(ChatFormatting.DARK_GRAY)),
               offsetX,
               offsetY,
               ChatFormatting.DARK_GRAY.getColor(),
               false
            );
            poseStack.popPose();
         } else {
            ResourceLocation recipeId = recipe.getId();
            gui.drawString(
               this.font,
               Component.translatable("gui.tacz.gun_smith_table.error").withStyle(ChatFormatting.DARK_RED),
               this.leftPos + 6,
               this.topPos + 122,
               11468800,
               false
            );
            gui.drawString(
               this.font,
               Component.translatable("gui.tacz.gun_smith_table.error.id", new Object[]{recipeId.toString()}).withStyle(ChatFormatting.DARK_RED),
               this.leftPos + 6,
               this.topPos + 134,
               16777215,
               false
            );
            PackInfo errorPackInfo = ClientAssetsManager.INSTANCE.getPackInfo(id);
            if (errorPackInfo != null) {
               gui.drawString(
                  this.font,
                  Component.translatable(errorPackInfo.getName()).withStyle(ChatFormatting.DARK_RED),
                  this.leftPos + 6,
                  this.topPos + 146,
                  11468800,
                  false
               );
            }
         }

         ci.cancel();
      }
   }
}
