package fr.heralisz.tactical.util;

import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Unique;

public interface IPAAssetManager {
   @Unique
   Optional<KeyframeAnimation> lrt$getAnimations(ResourceLocation var1, String var2);

   static Optional<KeyframeAnimation> getAnimations(ResourceLocation id, String name) {
      return PlayerAnimatorAssetManager.get() instanceof IPAAssetManager ipaAssetManager ? ipaAssetManager.lrt$getAnimations(id, name) : Optional.empty();
   }
}
