package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.serveur.perf.MondesSansSpawn;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Point 3 du chantier « Minecraft allégé » (01/11) : {@code ServerLevel.setDefaultSpawnPos} ({@code /setworldspawn})
 * remet le ticket de la zone de départ (2 209 tronçons gardés) sans regarder si le monde doit la garder. Pour les mondes
 * de {@code [perf] mondes_sans_spawn} avec {@code spawn_jamais_prepare} : le point de départ change, la zone n'est pas
 * rechargée ({@link MondesSansSpawn}). Les autres mondes et les autres tickets : comme Minecraft.
 */
@Mixin(ServerLevel.class)
public abstract class SpawnSansTicketMixin {
   @SuppressWarnings({"unchecked", "rawtypes"})
   @Redirect(method = "setDefaultSpawnPos", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/server/level/ServerChunkCache;addRegionTicket(Lnet/minecraft/server/level/TicketType;Lnet/minecraft/world/level/ChunkPos;ILjava/lang/Object;)V"))
   private void heraliz$ticketDepart(ServerChunkCache cache, TicketType type, ChunkPos pos, int rayon, Object valeur) {
      if (type == TicketType.START && MondesSansSpawn.sansSpawn((ServerLevel) (Object) this)) {
         return;
      }
      cache.addRegionTicket(type, pos, rayon, valeur);
   }
}
