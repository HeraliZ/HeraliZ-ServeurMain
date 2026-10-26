package fr.heraliz.noyau.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.noyau.client.diag.MesureRendu;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Temps de dessin de chaque entité, par type ({@link MesureRendu}, seulement pendant un /diag client). */
@Mixin(EntityRenderDispatcher.class)
public abstract class MesureEntiteRenduMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private <E extends Entity> void heraliz$debut(E entite, double x, double y, double z, float lacet, float partiel, PoseStack pose,
                                                   MultiBufferSource tampons, int lumiere, CallbackInfo ci) {
        MesureRendu.debut();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends Entity> void heraliz$fin(E entite, double x, double y, double z, float lacet, float partiel, PoseStack pose,
                                                 MultiBufferSource tampons, int lumiere, CallbackInfo ci) {
        MesureRendu.finEntite(entite.getType());
    }
}
