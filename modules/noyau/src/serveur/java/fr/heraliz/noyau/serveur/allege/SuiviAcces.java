package fr.heraliz.noyau.serveur.allege;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Accès à l'entité d'une entrée du suivi des entités de Minecraft ({@code ChunkMap.TrackedEntity}, classe non publique). */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface SuiviAcces {
   @Accessor("entity")
   Entity heraliz$entite();
}
