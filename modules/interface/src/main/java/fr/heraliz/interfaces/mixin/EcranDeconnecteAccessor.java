package fr.heraliz.interfaces.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Raison d'une déconnexion (reconnexion automatique après un redémarrage, voir client/Reconnexion). */
@Mixin(DisconnectedScreen.class)
public interface EcranDeconnecteAccessor {
    @Accessor("reason")
    Component heraliz$raison();
}
