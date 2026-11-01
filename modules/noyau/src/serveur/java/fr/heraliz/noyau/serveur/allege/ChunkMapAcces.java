package fr.heraliz.noyau.serveur.allege;

import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.PlayerMap;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accès à la carte des tronçons d'un monde (« Minecraft allégé », 01/11) : joueurs du monde tels que Minecraft les compte
 * pour les apparitions, et la réponse d'origine de Minecraft (ou de Mohist) quand une optimisation est coupée.
 */
@Mixin(ChunkMap.class)
public interface ChunkMapAcces {
   @Accessor("playerMap")
   PlayerMap heraliz$joueurs();

   @Invoker("anyPlayerCloseEnoughForSpawning")
   boolean heraliz$procheMinecraft(ChunkPos pos);
}
