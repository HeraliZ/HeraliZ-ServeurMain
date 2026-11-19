package fr.heraliz.noyau.serveur.allege;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.PlayerList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Sauvegarde automatique étalée (19/11, {@link fr.heraliz.noyau.serveur.perf.SauvegardesEtalees}) : l'écriture d'un joueur
 * par Minecraft lui-même ({@code PlayerList.save} : fichier .dat, .dat_old gardé, événement de Forge, réglages de Bukkit).
 */
@Mixin(PlayerList.class)
public interface JoueursAcces {
   @Invoker("save")
   void heraliz$sauver(ServerPlayer joueur);
}
