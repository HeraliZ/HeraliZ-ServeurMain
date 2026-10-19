package fr.heraliz.tournage.mixin;

import fr.heraliz.tournage.Lent;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Horloge du jeu au même ralenti que le serveur. */
@Mixin(Minecraft.class)
public abstract class ClientLentMixin {
   @ModifyArg(method = "<init>", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/Timer;<init>(FJ)V"), index = 0)
   private float tournage$lent(float tps) {
      return (float) (tps / Lent.FACTEUR);
   }
}
