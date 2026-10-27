package fr.heraliz.noyau.serveur.mixin;

import fr.heraliz.noyau.serveur.perf.LevelDatFond;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.storage.LevelStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.io.IOException;

/**
 * level.dat écrit à côté du fil serveur (27/10, perf) : voir {@link LevelDatFond}. Les deux appels de
 * {@code saveDataTag} (écriture compressée dans le fichier temporaire, puis remplacement de level.dat) sont pris
 * ensemble : chacun est obligatoire (un seul des deux = le serveur refuse de démarrer, jamais un level.dat vide).
 */
@Mixin(LevelStorageSource.LevelStorageAccess.class)
public abstract class LevelDatEnFondMixin {
   private static final String SAUVEGARDE = "saveDataTag(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/storage/WorldData;Lnet/minecraft/nbt/CompoundTag;)V";

   @Redirect(method = SAUVEGARDE, at = @At(value = "INVOKE",
         target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V"))
   private void heraliz$ecrire(CompoundTag contenu, File temporaire) throws IOException {
      if (!LevelDatFond.garder(contenu, temporaire)) {
         NbtIo.writeCompressed(contenu, temporaire);
      }
   }

   @Redirect(method = SAUVEGARDE, at = @At(value = "INVOKE",
         target = "Lnet/minecraft/Util;safeReplaceFile(Ljava/io/File;Ljava/io/File;Ljava/io/File;)V"))
   private void heraliz$remplacer(File cible, File temporaire, File ancien) {
      if (!LevelDatFond.remplacer(cible, temporaire, ancien)) {
         Util.safeReplaceFile(cible, temporaire, ancien);
      }
   }
}
