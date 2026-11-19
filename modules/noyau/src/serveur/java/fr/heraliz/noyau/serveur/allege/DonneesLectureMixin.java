package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.DonneesFond;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.io.File;

/**
 * Fichiers {@code data/} écrits en fond (19/11, voir {@link DonneesFond}) : avant de lire un de ces fichiers (première
 * demande d'une donnée, monde rechargé par un plugin), son écriture encore en file est attendue : jamais une version plus
 * ancienne lue à la place de la dernière.
 */
@Mixin(DimensionDataStorage.class)
public abstract class DonneesLectureMixin {
   @Shadow
   protected abstract File getDataFile(String nom);

   @Inject(method = "readTagFromDisk", at = @At("HEAD"))
   private void heraliz$attendreEcriture(String nom, int version, CallbackInfoReturnable<CompoundTag> cir) {
      DonneesFond.avantLecture(getDataFile(nom));
   }
}
