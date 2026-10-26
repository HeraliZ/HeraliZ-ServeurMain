package fr.heralisz.clans.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/**
 * Touches du mod : K (configurable, categorie « HeraliZ ») ouvre l'ecran du clan.
 */
public final class ClansKeys {
    private ClansKeys() {
    }

    public static final String CATEGORIE = "key.categories.heralisz";
    public static final KeyMapping OUVRIR = new KeyMapping("key.heralisz_clans.ouvrir", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORIE);
}
