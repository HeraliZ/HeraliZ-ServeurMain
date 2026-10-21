package fr.heralisz.utils.zombies.mixin;

import fr.heralisz.utils.zombies.AttaquesZombies;
import fr.heralisz.utils.zombies.client.GestesZombies;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Debut d'une frappe ou d'un bond annonce par le serveur (evenement d'entite) pour un zombie de Mutants & Zombies. */
@Mixin(LivingEntity.class)
public abstract class ZombieEvenementMixin {
    @Inject(method = "handleEntityEvent", at = @At("HEAD"), cancellable = true)
    private void heraliz$gesteZombie(byte id, CallbackInfo ci) {
        if (id != AttaquesZombies.EVT_FRAPPE && id != AttaquesZombies.EVT_BOND) return;
        LivingEntity self = (LivingEntity) (Object) this;
        if (AttaquesZombies.genre(self) == null) return;
        GestesZombies.evenement(self, id);
        ci.cancel();
    }
}
