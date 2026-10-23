package fr.heraliz.tactique.mixin.client;

import com.tacz.guns.client.sound.GunSoundInstance;
import com.tacz.guns.client.sound.SoundPlayManager;
import fr.heraliz.tactique.client.inspection.SonsInspection;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Note chaque son d'arme de TaCZ lancé pour le joueur lui-même (sons d'animation compris), pour que la prochaine
 * inspection les coupe ({@link SonsInspection}). Méthode privée par laquelle passent tous les sons : remap = false.
 */
@Mixin(value = SoundPlayManager.class, remap = false)
public abstract class SonsArmeJoueurMixin {
   @Inject(method = "playClientSound(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/resources/ResourceLocation;FFIZIZZ)Lcom/tacz/guns/client/sound/GunSoundInstance;",
      at = @At("RETURN"), require = 0)
   private static void heraliz$noterSon(Entity entity, ResourceLocation son, float volume, float hauteur, int distance, boolean mono,
                                        int limite, boolean suivi, boolean deuxD, CallbackInfoReturnable<GunSoundInstance> cir) {
      Minecraft mc = Minecraft.getInstance();
      if (entity != null && entity == mc.player) {
         SonsInspection.noter(cir.getReturnValue());
      }
   }
}
