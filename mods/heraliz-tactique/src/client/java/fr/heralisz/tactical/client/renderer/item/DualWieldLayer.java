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
 * <p>21/10 : aussi les os « push_l » / « push_r » du Couteau Push (pack CS2) ; sa dague gauche est en miroir dans le modèle
 * (pointe vers l'arrière, poignée ailleurs) : retournée et recentrée sur la poignée de la droite ({@link #placerGauche}).
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

   /** Noms des os (gauche, droite) reconnus. */
   private static final String[][] PAIRES = {{"left_weapon", "right_weapon"}, {"push_l", "push_r"}};

   static boolean isDual(BedrockAnimatedModel model) {
      return paire(model) != null;
   }

   private static String[] paire(BedrockAnimatedModel model) {
      if (model == null) return null;
      for (String[] p : PAIRES) {
         if (model.getNode(p[0]) != null && model.getNode(p[1]) != null) return p;
      }
      return null;
   }

   /**
    * Main gauche du Push : la dague gauche (poignée en 1,37 / 10,09 / -5,74 du modèle, pointe vers +z) est tournée d'un demi-tour
    * autour de la verticale et posée là où la droite est dans sa main (poignée en 1,37 / 7,59 / 1,74, pointe vers -z), en
    * miroir gauche-droite. À appeler dans le repère des pièces du modèle (après la mise en place de MeleeItemRenderer).
    */
   static void placerGauche(PoseStack poseStack, BedrockAnimatedModel model) {
      if (model.getNode("push_l") == null) return;
      poseStack.translate(0.0F, 2.5F / 16.0F, -4.0F / 16.0F);
      poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
   }

   /** Montre l'arme gauche ou l'arme droite seulement (3e personne, sol, cadre) ; les deux en 1re personne. */
   static void showOnly(BedrockAnimatedModel model, boolean left, boolean both) {
      String[] p = paire(model);
      if (p == null) return;
      BedrockPart l = model.getNode(p[0]), r = model.getNode(p[1]);
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
