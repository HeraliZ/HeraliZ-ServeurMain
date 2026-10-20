package com.Spartan.Client;

import com.Spartan.Networks.ClientReportPacket;
import com.Spartan.Networks.ModMessages;
import com.Spartan.Spartan;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Touches des menus de clients de triche : ² et Maj droite. Aucune action du jeu ne peut les prendre (HeraliZ
 * Interface, TouchesInterdites) : un appui en jeu, hors de tout écran, sur l'une d'elles alors qu'aucune touche des
 * options n'y est attachée est signalé au serveur, qui décide (ReceptionServeur, type « touche »).
 */
@Mod.EventBusSubscriber(modid = Spartan.MOD_ID, value = Dist.CLIENT)
public final class TouchesSuspectes {
    private TouchesSuspectes() {
    }

    @SubscribeEvent
    public static void onKey(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) {
            return;
        }
        int code = event.getKey();
        String nom;
        if (code == GLFW.GLFW_KEY_GRAVE_ACCENT) {
            nom = "²";
        } else if (code == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            nom = "Maj droite";
        } else {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || mc.getConnection() == null || mc.screen != null) {
            return;
        }
        InputConstants.Key touche = InputConstants.Type.KEYSYM.getOrCreate(code);
        for (KeyMapping k : mc.options.keyMappings) {
            if (k != null && touche.equals(k.getKey())) {
                return; // attachée à une action : appui normal
            }
        }
        ModMessages.sendToServer(new ClientReportPacket("touche", nom));
    }
}
