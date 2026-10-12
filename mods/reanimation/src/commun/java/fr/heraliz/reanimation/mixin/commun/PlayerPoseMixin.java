package fr.heraliz.reanimation.mixin.commun;

import fr.heraliz.reanimation.core.Downed;
import fr.heraliz.reanimation.core.DownedManager;
import javax.annotation.Nullable;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Un joueur à terre reste à plat (pose « nage » de Minecraft : couché, hitbox basse, ramper ralenti), des deux côtés.
 * <p>
 * TaCZ remet la pose forcée de CHAQUE joueur à rien à chaque tick tant qu'il ne rampe pas lui-même avec une arme
 * (LivingEntityCrawl côté serveur, à la fin de LivingEntity.tick ; LocalPlayerCrawl chez le joueur, au début de
 * LocalPlayer.tick), avant que Minecraft n'applique la pose. Notre pose « à terre », remise en fin de tick, était donc
 * effacée à chaque fois : le joueur à terre restait debout (bras levés : la pose couchée des bras sur un corps droit) et
 * rampait sans le ralentissement du jeu (x0,3), qui ne joue que si la pose du joueur est vraiment à plat.
 * <p>
 * Tant que le joueur est à terre, toute autre pose forcée (rien, accroupi...) est ignorée. Le mod la retire lui-même
 * quand le joueur se relève ou meurt : l'état « à terre » est effacé AVANT (serveur : DownedManagerServeur, joueur :
 * ClientDowned), la remise à rien passe alors normalement.
 * <p>
 * setForcedPose est une méthode ajoutée par Forge : même nom en développement et en jeu (remap = false, rien dans la
 * table de correspondance).
 */
@Mixin(Player.class)
public abstract class PlayerPoseMixin {
   @Inject(method = "setForcedPose", at = @At("HEAD"), cancellable = true, remap = false)
   private void reanimation$resteAuSol(@Nullable Pose pose, CallbackInfo ci) {
      if (pose != Pose.SWIMMING) {
         Player joueur = (Player)(Object)this;
         // serveur : simple lecture de l'état (TaCZ passe ici à chaque tick pour chaque joueur) ; joueur : drapeaux reçus
         boolean aTerre = joueur.level().isClientSide ? Downed.isDowned(joueur) : DownedManager.isDowned(joueur);
         if (aTerre) {
            ci.cancel();
         }
      }
   }
}
