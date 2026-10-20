package fr.heraliz.emote.mixin;

import io.github.kosmx.emotes.forge.ClientInit;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Default key of Emotecraft's emote wheel: B is already taken on HeraliZ (backpack, new Xaero waypoint), so the wheel
 * starts on ² (the key left of 1; ` on a QWERTY keyboard), which no other mod of the pack uses. Only the default changes:
 * a key the player chose in Options > Controls is kept.
 * HeraliZ Interface then moves the wheel from ² to Y (Touches) : ² stays free for the cheat-menu alert of Spartan.
 */
@Mixin(value = ClientInit.class, remap = false)
public abstract class EmotecraftKeysMixin {
    @ModifyConstant(method = "initKeyBinding", constant = @Constant(intValue = GLFW.GLFW_KEY_B), remap = false, require = 0)
    private static int heraliz$wheelKey(int original) {
        return GLFW.GLFW_KEY_GRAVE_ACCENT;
    }
}
