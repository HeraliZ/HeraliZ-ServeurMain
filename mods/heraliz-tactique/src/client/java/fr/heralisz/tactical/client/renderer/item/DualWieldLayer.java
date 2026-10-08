package fr.heralisz.tactical.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import fr.heralisz.tactical.api.client.HeraliszTacticalClientAPI;
import fr.heralisz.tactical.client.resource.display.MeleeDisplayInstance;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Armes de mêlée à deux mains (HeralisZ) : en 3e personne, dessine l'arme gauche dans la main gauche du joueur.
 * Convention du modèle : os « left_weapon » (arme de la main gauche) et « right_weapon » (arme de la main droite).
 * Le moteur ne dessine sinon que l'objet tenu en main droite : MeleeItemRenderer cache « left_weapon » dans la main
 * droite, et ce calque redessine le même objet dans la main gauche en ne gardant que « left_weapon ».
 */
public class DualWieldLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
   /** Vrai pendant le dessin de la main gauche : MeleeItemRenderer n'affiche alors que « left_weapon ». */
   static boolean renderingLeft = false;

   public DualWieldLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
      super(parent);
   }

   /** Vrai si l'objet est une arme de mêlée dont le modèle a une arme gauche et une arme droite. */
   public static boolean isDual(ItemStack stack) {
      return HeraliszTacticalClientAPI.getMeleeDisplay(stack).map(MeleeDisplayInstance::getModel).map(DualWieldLayer::isDual).orElse(false);
   }

   static boolean isDual(BedrockAnimatedModel model) {
      return model != null && model.getNode("left_weapon") != null && model.getNode("right_weapon") != null;
   }

   /** Montre l'arme gauche ou l'arme droite seulement (3e personne, sol, cadre) ; les deux en 1re personne. */
   static void showOnly(BedrockAnimatedModel model, boolean left, boolean both) {
      BedrockPart l = model.getNode("left_weapon"), r = model.getNode("right_weapon");
      if (l != null && r != null) {
         l.visible = both || left;
         r.visible = both || !left;
      }
   }

   @Override
   public void render(PoseStack poseStack, MultiBufferSource buffers, int light, AbstractClientPlayer player, float limbSwing, float limbSwingAmount,
         float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
      ItemStack stack = player.getMainHandItem();
      if (!player.getOffhandItem().isEmpty() || player.getMainArm() != HumanoidArm.RIGHT || !isDual(stack)) {
         return;
      }
      poseStack.pushPose();
      this.getParentModel().translateToHand(HumanoidArm.LEFT, poseStack);
      poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
      poseStack.translate(-1.0F / 16.0F, 0.125F, -0.625F);
      renderingLeft = true;
      try {
         Minecraft.getInstance().getItemRenderer().renderStatic(player, stack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, true, poseStack, buffers,
            player.level(), light, OverlayTexture.NO_OVERLAY, player.getId() + 7);
      } finally {
         renderingLeft = false;
      }
      poseStack.popPose();
   }
}
