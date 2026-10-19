package fr.heraliz.tournage.mixin;

import fr.heraliz.tournage.Lent;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Serveur au ralenti : un tick toutes les 50 × lent ms (tournage image par image d'une machine lente). */
@Mixin(MinecraftServer.class)
public abstract class ServeurLentMixin {
   @ModifyConstant(method = "runServer", constant = @Constant(longValue = 50L), require = 1)
   private long tournage$lent(long ms) {
      return Math.round(ms * Lent.FACTEUR);
   }
}
