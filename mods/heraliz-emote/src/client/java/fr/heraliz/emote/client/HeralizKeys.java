package fr.heraliz.emote.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.IKeyConflictContext;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

public final class HeralizKeys {
    public static final String CATEGORY = "key.categories.heraliz_emote";

    /**
     * In game only, and never reported as a conflict: Shift + right click is meant to share the
     * right button with "Use item" (the use is cancelled only when an emote is borrowed).
     */
    private static final IKeyConflictContext SHARED = new IKeyConflictContext() {
        @Override
        public boolean isActive() {
            return KeyConflictContext.IN_GAME.isActive();
        }

        @Override
        public boolean conflicts(IKeyConflictContext other) {
            return false;
        }
    };

    public static final KeyMapping BORROW = new KeyMapping("key.heraliz_emote.borrow", SHARED, KeyModifier.SHIFT,
            InputConstants.Type.MOUSE, GLFW.GLFW_MOUSE_BUTTON_RIGHT, CATEGORY);
    public static final KeyMapping REVERSE_SOUND = new KeyMapping("key.heraliz_emote.reverse_sound", SHARED, KeyModifier.NONE,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_LEFT_CONTROL, CATEGORY);
    /**
     * 0 (à on an AZERTY keyboard): H is already taken on HeraliZ (TaCZ inspect, voice chat icons, TCTcore). The emote
     * wheel of Emotecraft moves from B to ² ({@code mixin.EmotecraftKeysMixin}).
     */
    public static final KeyMapping OPEN_MENU = new KeyMapping("key.heraliz_emote.open_menu", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_0, CATEGORY);

    private HeralizKeys() {
    }

    static void register(RegisterKeyMappingsEvent event) {
        event.register(BORROW);
        event.register(REVERSE_SOUND);
        event.register(OPEN_MENU);
    }

    /** The key itself is held (whatever the modifier), read straight from the window. */
    static boolean keyHeld(KeyMapping mapping) {
        InputConstants.Key key = mapping.getKey();
        if (key.equals(InputConstants.UNKNOWN)) return false;
        long window = Minecraft.getInstance().getWindow().getWindow();
        if (key.getType() == InputConstants.Type.MOUSE) {
            return GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
        }
        return key.getType() == InputConstants.Type.KEYSYM && InputConstants.isKeyDown(window, key.getValue());
    }

    /** The modifier of the mapping (Shift, Ctrl, Alt) is held, or it has none. */
    static boolean modifierHeld(KeyMapping mapping) {
        KeyModifier modifier = mapping.getKeyModifier();
        return modifier == KeyModifier.NONE || modifier.isActive(KeyConflictContext.IN_GAME);
    }

    /** Bound to the same button as "Use item / Place block" (the default: Shift + right click). */
    static boolean sharesUseKey(KeyMapping mapping) {
        return mapping.getKey().equals(Minecraft.getInstance().options.keyUse.getKey());
    }
}
