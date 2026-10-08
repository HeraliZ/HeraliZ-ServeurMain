package fr.heraliz.reanimation.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.LivingEntity;

public class AnimatedPlayerModel<T extends LivingEntity> extends PlayerModel<T> {
   public AnimatedPlayerModel(ModelPart root, boolean slim) {
      super(root, slim);
   }

   public void setupAnim(T entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch) {
      super.setupAnim(entity, limbSwing, limbSwingAmount, ageInTicks, netHeadYaw, headPitch);
      Poses.apply(this, entity, limbSwing, limbSwingAmount, ageInTicks);
      this.leftPants.copyFrom(this.leftLeg);
      this.rightPants.copyFrom(this.rightLeg);
      this.leftSleeve.copyFrom(this.leftArm);
      this.rightSleeve.copyFrom(this.rightArm);
      this.jacket.copyFrom(this.body);
      this.hat.copyFrom(this.head);
   }
}
