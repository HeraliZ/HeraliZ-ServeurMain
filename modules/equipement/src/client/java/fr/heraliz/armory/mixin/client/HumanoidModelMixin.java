package fr.heraliz.armory.mixin.client;

import fr.heraliz.armory.client.DivingPoses;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Nages de la tenue de plongee (0.9.0) : a la FIN de HumanoidModel.setupAnim, seulement si l'entite est un joueur dans
 * l'eau qui porte la tenue (client/DivingPoses). Aucun modele de joueur remplace (le mod heraliz_plongee d'origine
 * remplacait le PlayerModel du rendu par reflexion) : tout ce qui vient apres garde la main et passe par-dessus :
 * les emotes (player-animation-lib, appliquees dans PlayerModel.setupAnim apres cet appel), les poses a terre de
 * Reanimation (son modele appelle setupAnim puis pose), les peaux CaraCrea (qui recopient la pose du modele).
 * Priorite basse : appliquee avant les injections des autres mods au meme endroit.
 */
@Mixin(value = HumanoidModel.class, priority = 900, remap = false)
public abstract class HumanoidModelMixin {
    @Inject(method = {"setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V", "m_6973_(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V"},
            at = @At("TAIL"), remap = false, require = 1)
    private void heraliz$diving(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                                float headPitch, CallbackInfo ci) {
        DivingPoses.apply((HumanoidModel<?>) (Object) this, entity, ageInTicks);
    }
}
