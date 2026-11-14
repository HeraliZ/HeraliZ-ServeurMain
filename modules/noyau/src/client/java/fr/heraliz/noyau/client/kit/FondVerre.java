package fr.heraliz.noyau.client.kit;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Fond plein écran de nos écrans en verre : celui de nos menus (HeraliZ Interface, {@code fr.heraliz.interfaces.kit.Fond} :
 * monde flouté si le réglage « flou » est mis, voile diagonal, bords assombris), appelé sans dépendance ; sans HeraliZ
 * Interface (ou en panne), le dégradé des écrans du clan ({@link Ui#fond}). Sans allocation.
 */
public final class FondVerre {
    private FondVerre() {
    }

    private static MethodHandle fond;
    private static boolean cherche;

    /** À dessiner en tout début de rendu de l'écran. */
    public static void dessiner(GuiGraphics g, int w, int h) {
        if (!cherche) {
            cherche = true;
            try {
                Method mt = Class.forName("fr.heraliz.interfaces.kit.Fond").getMethod("dessiner", GuiGraphics.class, int.class, int.class, float.class);
                fond = MethodHandles.lookup().unreflect(mt);
            } catch (ReflectiveOperationException | LinkageError | RuntimeException ex) {
                fond = null;
            }
        }
        if (fond != null) {
            try {
                fond.invokeExact(g, w, h, 1f);
                return;
            } catch (Throwable ex) {
                fond = null;
            }
        }
        Ui.fond(g, w, h);
    }
}
