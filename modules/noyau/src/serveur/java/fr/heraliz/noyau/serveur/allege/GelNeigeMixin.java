package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Eau qui gèle et neige qui s'entasse dans les biomes froids (19/11, retour du propriétaire : « dans l'arctique l'eau
 * gèle »). À chaque tick, dans un tronçon sur seize autour des joueurs ({@code ServerLevel.tickChunk}, partie
 * « iceandsnow »), Minecraft change en glace l'eau au bord d'un plan d'eau d'un biome froid, puis la glace gagne de proche
 * en proche ; quand il neige, il pose aussi des couches de neige. Ce n'est pas un tic aléatoire : {@code [monde]
 * tics_aleatoires = 0} ne l'arrête pas. La carte est construite à la main : par défaut, ni gel ni neige au sol (réglages
 * {@code [monde] gel_eau} et {@code neige_au_sol} de heraliz_noyau-server.toml, true = comme Minecraft). La neige qui
 * tombe (affichage du joueur) ne change pas ; la glace déjà formée reste (à remettre en eau à la main). Le contrôle du
 * jeu (température du biome, lumière, bloc) est sauté : rien à calculer. Compteurs dans {@code /diag etat} (ligne
 * « Minecraft allégé, étape 2 ») : la preuve que le blocage est en place.
 */
@Mixin(ServerLevel.class)
public abstract class GelNeigeMixin {
   @Redirect(method = "tickChunk", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/level/biome/Biome;shouldFreeze(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
   private boolean heraliz$gel(Biome biome, LevelReader monde, BlockPos pos) {
      if (NoyauConfig.gelEau) return biome.shouldFreeze(monde, pos);
      Allege.gelsEvites++;
      return false;
   }

   @Redirect(method = "tickChunk", at = @At(value = "INVOKE",
         target = "Lnet/minecraft/world/level/biome/Biome;shouldSnow(Lnet/minecraft/world/level/LevelReader;Lnet/minecraft/core/BlockPos;)Z"))
   private boolean heraliz$neige(Biome biome, LevelReader monde, BlockPos pos) {
      if (NoyauConfig.neigeAuSol) return biome.shouldSnow(monde, pos);
      Allege.neigesEvitees++;
      return false;
   }
}
