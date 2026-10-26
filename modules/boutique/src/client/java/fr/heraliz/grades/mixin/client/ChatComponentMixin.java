package fr.heraliz.grades.mixin.client;

import fr.heraliz.grades.client.chat.ChatHeraliz;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Chat à canaux (25/10) : chaque ligne que Minecraft ajoute à son chat (messages du serveur, des commandes, des mods côté
 * joueur comme /diagclient) est aussi rangée dans le chat HeraliZ ({@link ChatHeraliz}), qui remplace l'affichage.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"))
    private void heraliz$ligne(Component message, MessageSignature signature, GuiMessageTag tag, CallbackInfo ci) {
        ChatHeraliz.ajouterMinecraft(message);
    }

    /** Chat vidé (F3 + D, ou le staff pour tous) : le chat HeraliZ aussi. */
    @Inject(method = "clearMessages(Z)V", at = @At("HEAD"))
    private void heraliz$vider(boolean historique, CallbackInfo ci) {
        ChatHeraliz.vider();
    }
}
