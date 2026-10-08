package fr.heralisz.tactical.client.renderer;

import com.tacz.guns.client.model.BedrockAnimatedModel;
import com.tacz.guns.client.model.bedrock.BedrockPart;
import com.tacz.guns.util.math.SecondOrderDynamics;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

public class JumpSwayUtil {
   private static float jumpingSwayProgress = 0.0F;
   private static boolean lastOnGround = false;
   private static long jumpingTimeStamp = -1L;
   private static final SecondOrderDynamics JUMPING_DYNAMICS = new SecondOrderDynamics(0.28F, 1.0F, 0.65F, 0.0F);
   private static final float JUMPING_Y_SWAY = -2.0F;
   private static final float JUMPING_SWAY_TIME = 0.3F;
   private static final float LANDING_SWAY_TIME = 0.15F;

   public static void applyJumpingSway(BedrockAnimatedModel model, float partialTicks) {
      if (jumpingTimeStamp == -1L) {
         jumpingTimeStamp = System.currentTimeMillis();
      }

      LocalPlayer player = Minecraft.getInstance().player;
      if (player != null) {
         double posY = Mth.lerp(partialTicks, Minecraft.getInstance().player.yOld, Minecraft.getInstance().player.getY());
         float velocityY = (float)(posY - Minecraft.getInstance().player.yOld) / partialTicks;
         if (player.onGround()) {
            if (!lastOnGround) {
               jumpingSwayProgress = velocityY / -0.1F;
               if (jumpingSwayProgress > 1.0F) {
                  jumpingSwayProgress = 1.0F;
               }

               lastOnGround = true;
            } else {
               jumpingSwayProgress = jumpingSwayProgress - (float)(System.currentTimeMillis() - jumpingTimeStamp) / 150.0F;
               if (jumpingSwayProgress < 0.0F) {
                  jumpingSwayProgress = 0.0F;
               }
            }
         } else if (lastOnGround) {
            jumpingSwayProgress = velocityY / 0.42F;
            if (jumpingSwayProgress > 1.0F) {
               jumpingSwayProgress = 1.0F;
            }

            lastOnGround = false;
         } else {
            jumpingSwayProgress = jumpingSwayProgress - (float)(System.currentTimeMillis() - jumpingTimeStamp) / 300.0F;
            if (jumpingSwayProgress < 0.0F) {
               jumpingSwayProgress = 0.0F;
            }
         }
      }

      jumpingTimeStamp = System.currentTimeMillis();
      float ySway = JUMPING_DYNAMICS.update(-2.0F * jumpingSwayProgress);
      BedrockPart rootNode = model.getRootNode();
      if (rootNode != null) {
         rootNode.offsetY += -ySway / 16.0F;
      }
   }
}
