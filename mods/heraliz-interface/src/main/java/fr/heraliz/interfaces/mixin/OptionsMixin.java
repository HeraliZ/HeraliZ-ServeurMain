package fr.heraliz.interfaces.mixin;

import fr.heraliz.interfaces.client.PackImpose;
import net.minecraft.client.Options;
import net.minecraft.server.packs.repository.PackRepository;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Packs choisis dans options.txt : sélection imposée (PackImpose) avant le premier chargement des textures. */
@Mixin(Options.class)
public abstract class OptionsMixin {
    @Inject(method = "loadSelectedResourcePacks", at = @At("TAIL"))
    private void heraliz$packImpose(PackRepository repo, CallbackInfo ci) {
        PackImpose.appliquer(repo);
    }
}
