package fr.heralisz.tactical.client.input;

import com.tacz.guns.client.input.InteractKey;
import com.tacz.guns.config.util.InteractKeyConfigRead;
import com.tacz.guns.util.InputExtraCheck;
import fr.heralisz.tactical.api.item.IMeleeWeapon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.InputEvent.Key;
import net.minecraftforge.client.event.InputEvent.MouseButton.Post;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(modid = "heralisz_tactical", value = Dist.CLIENT)
public class TacticalInteractKey {
   @SubscribeEvent
   public static void onInteractKeyPress(Key event) {
      if (InputExtraCheck.isInGame() && event.getAction() == 1 && InteractKey.INTERACT_KEY.matches(event.getKey(), event.getScanCode())) {
         doInteractLogic();
      }
   }

   @SubscribeEvent
   public static void onInteractMousePress(Post event) {
      if (InputExtraCheck.isInGame() && event.getAction() == 1 && InteractKey.INTERACT_KEY.matchesMouse(event.getButton())) {
         doInteractLogic();
      }
   }

   private static void doInteractLogic() {
      Minecraft mc = Minecraft.getInstance();
      LocalPlayer player = mc.player;
      if (player != null && !player.isSpectator()) {
         if (player.getMainHandItem().getItem() instanceof IMeleeWeapon) {
            HitResult hitResult = mc.hitResult;
            if (hitResult == null) {
               return;
            }

            if (hitResult instanceof BlockHitResult blockHitResult) {
               interactBlock(blockHitResult, player, mc);
               return;
            }

            if (hitResult instanceof EntityHitResult entityHitResult) {
               interactEntity(entityHitResult, mc);
            }
         }
      }
   }

   private static void interactBlock(BlockHitResult blockHitResult, LocalPlayer player, Minecraft mc) {
      BlockPos blockPos = blockHitResult.getBlockPos();
      BlockState block = player.level().getBlockState(blockPos);
      if (InteractKeyConfigRead.canInteractBlock(block)) {
         mc.startUseItem();
      }
   }

   private static void interactEntity(EntityHitResult entityHitResult, Minecraft mc) {
      Entity entity = entityHitResult.getEntity();
      if (InteractKeyConfigRead.canInteractEntity(entity)) {
         mc.startUseItem();
      }
   }
}
