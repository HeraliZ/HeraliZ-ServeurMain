package fr.heraliz.noyau.serveur.mixin;

import fr.heraliz.noyau.serveur.perf.MemoiresSauvegarde;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.storage.WorldData;
import net.minecraftforge.common.ForgeHooks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Partie « fml » de level.dat gardée (19/11, audit des lenteurs, point C2 ; voir {@link MemoiresSauvegarde}) : Forge la
 * refabriquait à chaque sauvegarde ({@code ForgeHooks.writeAdditionalLevelSaveData} : liste des mods, puis chaque registre
 * mis en NBT). Après la première sauvegarde, la même partie est remise telle quelle. Réglage
 * {@code [perf] registres_en_memoire} coupé ou cible absente (require = 0) : comme Forge.
 */
@Mixin(value = ForgeHooks.class, remap = false)
public abstract class FmlEnMemoireMixin {
   @Inject(method = "writeAdditionalLevelSaveData", at = @At("HEAD"), cancellable = true)
   private static void heraliz$resservir(WorldData donnees, CompoundTag niveau, CallbackInfo ci) {
      Tag fml = MemoiresSauvegarde.fml();
      if (fml != null) {
         niveau.put("fml", fml);
         ci.cancel();
      }
   }

   @Inject(method = "writeAdditionalLevelSaveData", at = @At("RETURN"))
   private static void heraliz$garder(WorldData donnees, CompoundTag niveau, CallbackInfo ci) {
      MemoiresSauvegarde.garderFml(niveau);
   }
}
