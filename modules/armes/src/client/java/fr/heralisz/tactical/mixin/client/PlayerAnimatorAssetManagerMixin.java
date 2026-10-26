package fr.heralisz.tactical.mixin.client;

import com.tacz.guns.compat.playeranimator.animation.PlayerAnimatorAssetManager;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import java.util.Optional;
import fr.heralisz.tactical.util.IPAAssetManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

@OnlyIn(Dist.CLIENT)
@Mixin(PlayerAnimatorAssetManager.class)
public abstract class PlayerAnimatorAssetManagerMixin implements IPAAssetManager {
   @Shadow(remap = false)
   abstract Optional<KeyframeAnimation> getAnimations(ResourceLocation var1, String var2);

   @Unique
   @Override
   public Optional<KeyframeAnimation> lrt$getAnimations(ResourceLocation id, String name) {
      return this.getAnimations(id, name);
   }
}
