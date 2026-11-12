package fr.heraliz.noyau.serveur.allege;

import fr.heraliz.noyau.NoyauConfig;
import fr.heraliz.noyau.serveur.perf.Allege;
import java.util.Collections;
import java.util.List;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.world.level.GameRules;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Mélange des tronçons qui tournent (11/11, profil du banc à 100 joueurs : {@code Collections.shuffle} dans
 * {@code tickChunks}, 1,1 ms par tick). À chaque tick, Minecraft mélange au hasard la liste de tous les tronçons qui
 * tournent (plusieurs milliers) pour que les apparitions naturelles de créatures ne favorisent pas toujours les mêmes
 * tronçons. Quand la règle {@code doMobSpawning} du monde est à faux (le cas du serveur : seuls les zombies du peuplement
 * apparaissent, par notre code), aucune apparition naturelle n'est tentée : l'ordre ne sert à rien (le reste de la boucle,
 * temps passé dans le tronçon, tics de blocs, envoi des changements, ne dépend pas de l'ordre). Le mélange est alors sauté.
 * Règle à vrai : le mélange de Minecraft, comme avant. Réglage : {@code [perf] melange_si_apparitions}.
 */
@Mixin(ServerChunkCache.class)
public abstract class MelangeTronconsMixin {
   @Redirect(method = "tickChunks", at = @At(value = "INVOKE", target = "Ljava/util/Collections;shuffle(Ljava/util/List;)V", remap = false))
   private void heraliz$melangeUtile(List<?> tournent) {
      if (NoyauConfig.melangeSiApparitions
            && !((ServerChunkCache) (Object) this).getLevel().getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) {
         Allege.melangesEvites++;
         return;
      }
      Collections.shuffle(tournent);
   }
}
