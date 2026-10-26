package fr.heraliz.noyau.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.noyau.client.diag.MesureRendu;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Temps de dessin de chaque entité de bloc (conteneurs de loot, props animés, caisses…), par type ({@link MesureRendu},
 * seulement pendant un /diag client).
 */
@Mixin(BlockEntityRenderDispatcher.class)
public abstract class MesureBlocRenduMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private <E extends BlockEntity> void heraliz$debut(E bloc, float partiel, PoseStack pose, MultiBufferSource tampons, CallbackInfo ci) {
        MesureRendu.debut();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private <E extends BlockEntity> void heraliz$fin(E bloc, float partiel, PoseStack pose, MultiBufferSource tampons, CallbackInfo ci) {
        MesureRendu.finBloc(bloc.getType());
    }
}
