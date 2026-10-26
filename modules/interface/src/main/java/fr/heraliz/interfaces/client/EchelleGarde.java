package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.platform.Window;
import fr.heraliz.interfaces.HeralizInterface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Échelle d'interface du joueur toujours rendue en jeu (23/10, retour du propriétaire : cœurs et faim « pixelisés »,
 * énormes, après une partie de zombies). Nos écrans plein panneau forcent l'échelle d'interface (kit {@code Echelle} de
 * chaque mod) et la rendent à leur fermeture ; quand un changement de monde ou une téléportation coupe l'écran au mauvais
 * moment, l'échelle forcée pouvait rester sur le HUD. Ici, à chaque tick sans écran (ou avec le chat), l'échelle revient à
 * celle des options du joueur si elle en diffère.
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class EchelleGarde {
    private EchelleGarde() {
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || (mc.screen != null && !(mc.screen instanceof ChatScreen))) return;
        Window w = mc.getWindow();
        int voulue = w.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
        if ((int) w.getGuiScale() != voulue) {
            w.setGuiScale(voulue);
            if (mc.screen != null) mc.screen.resize(mc, w.getGuiScaledWidth(), w.getGuiScaledHeight());
        }
    }
}
