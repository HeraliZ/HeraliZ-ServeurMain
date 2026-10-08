package fr.heralisz.tactical.mixin.client;

import com.tacz.guns.api.client.other.KeepingItemRenderer;
import fr.heralisz.tactical.api.item.ICustomItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerModel.class)
public class PlayerModelMixin<T extends LivingEntity> extends HumanoidModel<T> {
   @Shadow
   @Final
   public ModelPart leftSleeve;
   @Shadow
   @Final
   public ModelPart rightSleeve;

   public PlayerModelMixin(ModelPart part) {
      super(part);
   }

   @Inject(method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", at = @At("TAIL"))
   private void setRotationAnglesTail(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
      if (entityIn instanceof Player player) {
         ItemStack currentItem = KeepingItemRenderer.getRenderer().getCurrentItem();
         if (ageInTicks == 0.0F && currentItem != null && currentItem.getItem() instanceof ICustomItem) {
            this.tacz$resetAll(this.rightArm);
            this.tacz$resetAll(this.leftArm);
            this.rightSleeve.copyFrom(this.rightArm);
            this.leftSleeve.copyFrom(this.leftArm);
         }
      }
   }

   @Unique
   private void tacz$resetAll(ModelPart part) {
      part.xRot = 0.0F;
      part.yRot = 0.0F;
      part.zRot = 0.0F;
   }
}
