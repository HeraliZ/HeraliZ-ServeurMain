package fr.heraliz.crac.serveur.mixin;

import fr.heraliz.crac.bases.AncienMondeBases;
import net.minecraft.core.Registry;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ancien monde des bases fermé (02/11, audit) : voir {@link AncienMondeBases}. Minecraft garde dans le level.dat du monde
 * principal tous les mondes qu'il a créés, et les recrée au démarrage même quand plus aucun fichier de données ne les
 * déclare ({@code WorldDimensions.bake}) : ici, {@code crac:bases} est laissé de côté tant que CRac ne le déclare plus.
 * Sans ce mixin (facultatif), le monde serait seulement créé comme avant.
 */
@Mixin(WorldDimensions.class)
public abstract class AncienMondeBasesMixin {
    @Inject(method = "bake", at = @At("HEAD"), cancellable = true)
    private void crac$sansAncienMonde(Registry<LevelStem> declares, CallbackInfoReturnable<WorldDimensions.Complete> cir) {
        WorldDimensions sansLui = AncienMondeBases.sansAncienMonde((WorldDimensions) (Object) this, declares);
        if (sansLui != null) {
            cir.setReturnValue(sansLui.bake(declares));
        }
    }
}
