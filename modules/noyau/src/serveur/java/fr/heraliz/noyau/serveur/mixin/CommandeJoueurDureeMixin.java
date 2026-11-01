package fr.heraliz.noyau.serveur.mixin;

import fr.heraliz.noyau.serveur.diag.DernieresCommandes;
import net.minecraft.network.chat.LastSeenMessages;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Durée de chaque commande tapée par un joueur (01/11, profileur), plugins Bukkit compris (Mohist les passe par là) :
 * seul le PREMIER mot est gardé pour la nommer quand aucun événement de Forge ne le fait (« /login », jamais ce qui suit),
 * voir {@link DernieresCommandes}. Facultatif : une cible absente n'empêche rien.
 */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CommandeJoueurDureeMixin {
    private static final String CIBLE = "performChatCommand(Lnet/minecraft/network/protocol/game/ServerboundChatCommandPacket;Lnet/minecraft/network/chat/LastSeenMessages;)V";

    @Inject(method = CIBLE, at = @At("HEAD"), require = 0)
    private void heraliz$debut(ServerboundChatCommandPacket paquet, LastSeenMessages vus, CallbackInfo ci) {
        DernieresCommandes.debut(paquet.command());
    }

    @Inject(method = CIBLE, at = @At("RETURN"), require = 0)
    private void heraliz$fin(CallbackInfo ci) {
        DernieresCommandes.fin();
    }
}
