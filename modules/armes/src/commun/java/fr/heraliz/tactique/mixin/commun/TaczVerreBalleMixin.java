package fr.heraliz.tactique.mixin.commun;

import com.tacz.guns.util.block.BlockRayTrace;
import fr.heraliz.tactique.vitres.Vitres;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Vitres cassées par les balles (fr.heraliz.tactique.vitres.Vitres) : le trajet d'une balle de TaCZ, bloc par bloc.
 * <p>
 * TaCZ fait passer ses balles au travers des blocs de sa liste {@code PassThroughBlocks} (tacz-common.toml : les blocs de
 * verre pleins et les fenêtres de meubles {@code cfm:vb_*} y sont) sans même les toucher : une balle ne pourrait jamais
 * les casser. Là où le bloc casse (décision de la partie serveur : verre ou bloc de la liste {@code [vitres]
 * blocs_en_plus}, hors zones protégées, réglage {@code [vitres] casser}), il redevient un bloc que la balle touche,
 * avec le même calcul que TaCZ ; la balle le casse alors ({@code AmmoHitBlockEvent}, VitresServeur) et s'y arrête, comme
 * avec le réglage DestroyGlass de TaCZ. Ailleurs, rien ne change. Chaque appel est noté ({@code Vitres.crochetAppele}) :
 * {@code /vitres pourquoi} sait ainsi si ce mixin est bien en place.
 * <p>
 * Cible : la fonction {@code lambda$rayTraceBlocks$1(Level, ClipContext, BlockPos)} de BlockRayTrace (TaCZ 1.1.8), qui
 * regarde chaque bloc du trajet (null = la balle continue). Seul le serveur calcule les impacts des balles. Noms de TaCZ
 * (non obfusqués) : remap = false. require = 0 : si TaCZ change cette fonction, les blocs de verre pleins redeviennent
 * simplement traversés sans casser (les vitres fines cassent toujours), rien ne plante.
 */
@Mixin(value = BlockRayTrace.class, remap = false)
public abstract class TaczVerreBalleMixin {
   @Inject(
      method = "lambda$rayTraceBlocks$1(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/level/ClipContext;Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/phys/BlockHitResult;",
      at = @At("HEAD"),
      cancellable = true,
      require = 0
   )
   private static void heraliz$verreQuiCasse(Level level, ClipContext contexte, BlockPos pos, CallbackInfoReturnable<BlockHitResult> cir) {
      if (!Vitres.branche() || level.isClientSide) {
         return;
      }
      Vitres.crochetAppele();
      BlockState etat = level.getBlockState(pos);
      // verre ou bloc de la liste blocs_en_plus (fenêtres de meubles), hors zones protégées
      if (Vitres.cassable(level, pos, etat)) {
         cir.setReturnValue(Vitres.toucher(level, contexte, pos, etat));
      }
   }
}
