package fr.heraliz.crac.build.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** Key bindings of the building tools. Right click also opens the radial menus. */
@OnlyIn(Dist.CLIENT)
public final class BuildKeys {
    public static final KeyMapping ROTATE = new KeyMapping("key.crac.rotate_piece", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.crac");
    public static final KeyMapping MENU = new KeyMapping("key.crac.build_menu", KeyConflictContext.IN_GAME,
        InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_G, "key.categories.crac");

    private BuildKeys() {
    }

    /** Whether a key or mouse button is physically held right now (works while a screen is open). */
    public static boolean isHeld(InputConstants.Key key) {
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getValue() != InputConstants.UNKNOWN.getValue() && InputConstants.isKeyDown(window, key.getValue());
    }
}
