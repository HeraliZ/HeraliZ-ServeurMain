package fr.heralisz.tactical.mixin.common;

import com.tacz.guns.sound.SoundManager;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * Tir silencieux entendu par les AUTRES joueurs. TaCZ leur envoie le son « silence_3p » au même volume (0,8) qu'un tir
 * normal, et les sons silencieux des gunpacks sont aussi forts que les tirs normaux (seul le timbre change) : de près, un
 * tir silencieux s'entendait comme un vrai tir. Il part maintenant à 0,6 : le rapport que TaCZ applique déjà au son du
 * tireur (silencieux 0,6 / tir 0,8), soit environ deux fois moins fort à toute distance. La portée de chaque silencieux
 * est dans les gunpacks (outils/silencieux.py).
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
}
