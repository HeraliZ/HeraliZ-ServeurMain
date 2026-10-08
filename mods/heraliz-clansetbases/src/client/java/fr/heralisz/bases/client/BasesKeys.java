package fr.heralisz.bases.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Touches du mod : N (configurable, categorie « HeraliZ » partagee avec les clans) ouvre l'ecran BASE ; dans nos
 * ecrans, la meme touche les ferme. B est prise par le sac.
 */
public final class BasesKeys {
    private BasesKeys() {
    }

    public static final String CATEGORIE = "key.categories.heralisz";
    public static final KeyMapping OUVRIR = new KeyMapping("key.heralisz_bases.ouvrir", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_N, CATEGORIE);
}
