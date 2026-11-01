package fr.heraliz.noyau.serveur.allege;

import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerPlayerConnection;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Set;

/**
 * Étape 2 du chantier « Minecraft allégé » (01/11, {@link fr.heraliz.noyau.serveur.perf.SuiviParTroncon}) : accès à une
 * entrée du suivi des entités ({@code ChunkMap.TrackedEntity}, classe non publique) : son entité, la liste des joueurs qui
 * la voient, son {@code ServerEntity} (paquets d'apparition et de retrait), sa portée de suivi et la décision d'origine pour
 * un joueur ({@code updatePlayer} de Minecraft et de Mohist). À part de {@link SuiviAcces} (étape 1) : un accès introuvable
 * ici ne touche pas l'étape 1.
 */
@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public interface SuiviEntreeAcces {
   @Accessor("entity")
   Entity heraliz$entiteSuivie();

   @Accessor("seenBy")
   Set<ServerPlayerConnection> heraliz$vus();

   @Accessor("serverEntity")
   ServerEntity heraliz$serveur();

   @Invoker("getEffectiveRange")
   int heraliz$portee();

   @Invoker("updatePlayer")
   void heraliz$juger(ServerPlayer joueur);
}
