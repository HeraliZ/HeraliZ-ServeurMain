package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * Touches qu'aucune action ne peut prendre : ² (à gauche de 1) et Maj droite,
 * celles des menus de clients de triche. Les laisser libres chez tout le monde permet
 * à Spartan de prendre un appui sur l'une d'elles pour un indice de triche. Une touche refusée (options.txt, écran
 * des commandes, autre mod) devient « aucune touche » ; mixin {@code KeyMappingInterditMixin}.
 */
public final class TouchesInterdites {
    private static long dernierMessage;

    private TouchesInterdites() {
    }

    public static boolean interdite(InputConstants.Key k) {
        if (k == null || k.getType() != InputConstants.Type.KEYSYM) return false;
        int v = k.getValue();
        return v == GLFW.GLFW_KEY_GRAVE_ACCENT || v == GLFW.GLFW_KEY_RIGHT_SHIFT;
    }

    /** La touche à garder : aucune si elle est interdite (petit message si le joueur est dans un écran). */
    public static InputConstants.Key filtrer(InputConstants.Key k) {
        if (!interdite(k)) return k;
        Minecraft mc = Minecraft.getInstance();
        long t = System.currentTimeMillis();
        if (mc != null && mc.screen != null && t - dernierMessage > 1000) {
            dernierMessage = t;
            SystemToast.addOrUpdate(mc.getToasts(), SystemToast.SystemToastIds.TUTORIAL_HINT,
                    Component.translatable("heraliz_interface.touche_interdite.titre"),
                    Component.translatable("heraliz_interface.touche_interdite.texte"));
        }
        return InputConstants.UNKNOWN;
    }
}
