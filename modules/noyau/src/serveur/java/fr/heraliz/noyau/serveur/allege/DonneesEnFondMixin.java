package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.DonneesFond;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.saveddata.SavedData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.io.File;
import java.io.IOException;

/**
 * Fichiers {@code data/} des mondes écrits en fond (19/11, audit des lenteurs, point C2 ; voir {@link DonneesFond}) : dans
 * {@code SavedData.save(File)} de Minecraft (données sans écriture à elles), l'écriture compressée part sur l'écrivain en
 * fond, avec une copie du relevé. Les données qui ont leur propre {@code save(File)} (rotation du loot, CRac, casino, coffres
 * personnels) ne passent pas par ici. Réglage {@code [perf] donnees_en_fond} coupé ou cible absente (require = 0) : comme
 * Minecraft.
 */
@Mixin(SavedData.class)
public abstract class DonneesEnFondMixin {
   @Redirect(method = "save(Ljava/io/File;)V", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/nbt/NbtIo;writeCompressed(Lnet/minecraft/nbt/CompoundTag;Ljava/io/File;)V"))
   private void heraliz$ecrireEnFond(CompoundTag contenu, File fichier) throws IOException {
      if (!DonneesFond.garder((SavedData) (Object) this, contenu, fichier)) {
         NbtIo.writeCompressed(contenu, fichier);
      }
   }
}
