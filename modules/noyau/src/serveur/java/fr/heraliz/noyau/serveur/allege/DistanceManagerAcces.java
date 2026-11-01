package fr.heraliz.noyau.serveur.allege;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.DistanceManager;
import net.minecraft.server.level.Ticket;
import net.minecraft.util.SortedArraySet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lecture des tickets de chargement d'un monde, pour {@code /diag etat} et le journal du démarrage (01/11). */
@Mixin(DistanceManager.class)
public interface DistanceManagerAcces {
   @Accessor("tickets")
   Long2ObjectOpenHashMap<SortedArraySet<Ticket<?>>> heraliz$tickets();
}
