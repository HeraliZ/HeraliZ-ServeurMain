package fr.heraliz.interfaces.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import fr.heraliz.interfaces.client.TouchesInterdites;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Aucune action sur ² ni sur la touche sous Entrée (TouchesInterdites), quel que soit le chemin du choix. */
@Mixin(KeyMapping.class)
public abstract class KeyMappingInterditMixin {
    @ModifyVariable(method = "setKey", at = @At("HEAD"), argsOnly = true)
    private InputConstants.Key heraliz$touche(InputConstants.Key k) {
        return TouchesInterdites.filtrer(k);
    }

    @ModifyVariable(method = "setKeyModifierAndCode", at = @At("HEAD"), argsOnly = true, remap = false)
    private InputConstants.Key heraliz$toucheForge(InputConstants.Key k) {
        return TouchesInterdites.filtrer(k);
    }
}
