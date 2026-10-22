package com.Spartan.Client;

import com.Spartan.Spartan;
import com.mojang.blaze3d.platform.Monitor;
import com.mojang.blaze3d.platform.VideoMode;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.glfw.GLFW;

/**
 * Windows : le plein écran (F11, ou réglé dans les options) devient un plein écran sans bordure, fenêtre posée sur tout
 * l'écran. Même rendu pour le joueur, mais Windows peut alors copier l'écran (CaptureEcran) : en plein écran exclusif,
 * la copie sort noire et ce qu'un programme dessine par-dessus le jeu n'y est pas. F11 à nouveau : fenêtre normale.
 */
@Mod.EventBusSubscriber(modid = Spartan.MOD_ID, value = Dist.CLIENT)
public final class PleinEcran {
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    private static boolean sansBordure;
    private static boolean horsService;
    private static int x;
    private static int y;
    private static int largeur;
    private static int hauteur;

    private PleinEcran() {
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !WINDOWS || horsService) {
            return;
        }
        Window fenetre = Minecraft.getInstance().getWindow();
        if (!fenetre.isFullscreen()) {
            return;
        }
        long h = fenetre.getWindow();
        try {
            if (sansBordure) {
                // F11 pressé depuis le plein écran sans bordure : le joueur veut une fenêtre normale
                fenetre.toggleFullScreen();
                GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_DECORATED, GLFW.GLFW_TRUE);
                GLFW.glfwSetWindowMonitor(h, 0L, x, y, largeur, hauteur, GLFW.GLFW_DONT_CARE);
                sansBordure = false;
                return;
            }
            Monitor ecran = fenetre.findBestMonitor();
            if (ecran == null) {
                return;
            }
            VideoMode mode = ecran.getCurrentMode();
            fenetre.toggleFullScreen();
            int[] px = new int[1];
            int[] py = new int[1];
            int[] pw = new int[1];
            int[] ph = new int[1];
            GLFW.glfwGetWindowPos(h, px, py);
            GLFW.glfwGetWindowSize(h, pw, ph);
            x = px[0];
            y = py[0];
            largeur = Math.max(320, pw[0]);
            hauteur = Math.max(240, ph[0]);
            GLFW.glfwSetWindowAttrib(h, GLFW.GLFW_DECORATED, GLFW.GLFW_FALSE);
            GLFW.glfwSetWindowMonitor(h, 0L, ecran.getX(), ecran.getY(), mode.getWidth(), mode.getHeight(), GLFW.GLFW_DONT_CARE);
            sansBordure = true;
        } catch (Throwable t) {
            horsService = true; // plein écran normal, capture de l'image du jeu seule
            Spartan.LOGGER.debug("[Spartan] plein écran sans bordure impossible : {}", t.toString());
        }
    }
}
