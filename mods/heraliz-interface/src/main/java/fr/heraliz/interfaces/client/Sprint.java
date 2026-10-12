package fr.heraliz.interfaces.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;

/**
 * Sprint en un appui (menu Echap) : c'est l'option « Sprint : Basculer / Maintenir » de Minecraft (Options &gt;
 * Commandes), enregistree dans options.txt comme si le joueur l'avait changee la-bas. Activee : un appui sur la touche
 * de sprint pour courir, un autre pour arreter ; desactivee : la touche se garde enfoncee.
 */
public final class Sprint {
    private Sprint() {
    }

    /** Vrai si le sprint est en un appui (option « Basculer »). */
    public static boolean enUnAppui() {
        return Minecraft.getInstance().options.toggleSprint().get();
    }

    /** Inverse le reglage et l'enregistre. */
    public static void basculer() {
        Options o = Minecraft.getInstance().options;
        boolean v = !o.toggleSprint().get();
        o.toggleSprint().set(v);
        // repasse en « Maintenir » alors que le sprint etait enclenche par un appui : sans ce relachement, la touche
        // resterait enfoncee pour le jeu jusqu'au prochain appui
        if (!v) o.keySprint.setDown(false);
        o.save();
    }
}
