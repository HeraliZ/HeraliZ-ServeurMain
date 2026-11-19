package fr.heraliz.noyau.serveur.allege;

import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import fr.heraliz.noyau.serveur.perf.MemoiresSauvegarde;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.WorldGenSettings;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Réglages de génération gardés (19/11, audit des lenteurs, point C2 ; voir {@link MemoiresSauvegarde}) : dans
 * {@code PrimaryLevelData.setTagData} (contenu de level.dat), {@code WorldGenSettings.encode} n'est refait que si ses
 * entrées changent (options du monde, registre des dimensions). Deux formes : celle de Minecraft (registres du serveur) et
 * celle que Bukkit peut utiliser (dimensions données) ; chacune facultative (require = 0). Réglage
 * {@code [perf] registres_en_memoire} coupé : comme Minecraft.
 */
@Mixin(PrimaryLevelData.class)
public abstract class ReglagesGenerationMixin {
   @Redirect(method = "setTagData", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/level/levelgen/WorldGenSettings;encode(Lcom/mojang/serialization/DynamicOps;Lnet/minecraft/world/level/levelgen/WorldOptions;Lnet/minecraft/core/RegistryAccess;)Lcom/mojang/serialization/DataResult;"))
   private <T> DataResult<T> heraliz$encoderUneFois(DynamicOps<T> ops, WorldOptions options, RegistryAccess registres) {
      return MemoiresSauvegarde.reglages(options, registres.registryOrThrow(Registries.LEVEL_STEM),
            () -> WorldGenSettings.encode(ops, options, registres));
   }

   @Redirect(method = "setTagData", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/level/levelgen/WorldGenSettings;encode(Lcom/mojang/serialization/DynamicOps;Lnet/minecraft/world/level/levelgen/WorldOptions;Lnet/minecraft/world/level/levelgen/WorldDimensions;)Lcom/mojang/serialization/DataResult;"))
   private <T> DataResult<T> heraliz$encoderUneFoisDimensions(DynamicOps<T> ops, WorldOptions options, WorldDimensions dimensions) {
      return MemoiresSauvegarde.reglages(options, dimensions.dimensions(), () -> WorldGenSettings.encode(ops, options, dimensions));
   }
}
