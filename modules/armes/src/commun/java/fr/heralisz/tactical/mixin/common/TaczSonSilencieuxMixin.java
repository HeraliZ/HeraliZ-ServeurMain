package fr.heralisz.tactical.mixin.common;

import com.tacz.guns.sound.SoundManager;
import fr.heraliz.tactique.son.SonLointain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tir silencieux entendu par les AUTRES joueurs. TaCZ leur envoie le son « silence_3p » au même volume (0,8) qu'un tir
 * normal, et les sons silencieux des gunpacks sont aussi forts que les tirs normaux (seul le timbre change) : de près, un
 * tir silencieux s'entendait comme un vrai tir. Il part maintenant à 0,6 : le rapport que TaCZ applique déjà au son du
 * tireur (silencieux 0,6 / tir 0,8), soit environ deux fois moins fort à toute distance. La portée de chaque silencieux
 * est dans les gunpacks (outils/silencieux.py).
 * <p>
 * Au-delà de cette portée, le tir s'entend encore, étouffé et d'un endroit flou : {@link SonLointain}, prévenu ici de
 * chaque tir silencieux.
 * <p>
 * En jeu, seul le serveur envoie les sons de tir (rien ne change chez les joueurs). require = 0 : si TaCZ change cet
 * envoi, le tir garde simplement son volume d'origine.
 */
@Mixin(value = SoundManager.class, remap = false)
public abstract class TaczSonSilencieuxMixin {
   @ModifyArg(
      method = "sendSoundToNearby",
      at = @At(
         value = "INVOKE",
         target = "Lcom/tacz/guns/network/message/ServerMessageSound;<init>(ILnet/minecraft/resources/ResourceLocation;Lnet/minecraft/resources/ResourceLocation;Ljava/lang/String;FFI)V"
      ),
      index = 4,
      require = 0,
      remap = false
   )
   private static float heraliz$volumeSilencieux(
      int entite, ResourceLocation arme, ResourceLocation affichage, String son, float volume, float hauteur, int distance
   ) {
      // 0,8 -> 0,6 ; le jeu du joueur élève ce volume au carré : 0,56 du son d'un tir normal
      return SoundManager.SILENCE_3P_SOUND.equals(son) ? volume * 0.75F : volume;
   }

   /** Après l'envoi du son net aux joueurs proches : le son lointain pour les autres (partie serveur). */
   @Inject(method = "sendSoundToNearby", at = @At("TAIL"), require = 0, remap = false)
   private static void heraliz$sonLointain(
      LivingEntity tireur, int distance, ResourceLocation arme, ResourceLocation affichage, String son, float volume, float hauteur,
      CallbackInfo ci
   ) {
      if (SoundManager.SILENCE_3P_SOUND.equals(son)) {
         SonLointain.TIR_SERVEUR.accept(new SonLointain.Tir(tireur, distance, arme, affichage, hauteur));
      }
   }
}
