package fr.heraliz.noyau.serveur.allege;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Étape 2 du chantier « Minecraft allégé » (01/11) : accès à la carte des tronçons d'un monde : entrées du suivi des
 * entités, distance de vue du monde, le monde. À part de {@link ChunkMapAcces} (étape 1) : un accès introuvable ici ne
 * touche pas l'étape 1.
 */
@Mixin(ChunkMap.class)
public interface CarteAcces {
   @Accessor("entityMap")
   Int2ObjectMap<?> heraliz$suivis();

   @Accessor("viewDistance")
   int heraliz$vue();

   @Accessor("level")
   ServerLevel heraliz$niveau();
}
