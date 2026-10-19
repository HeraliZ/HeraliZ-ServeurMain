package fr.heraliz.tournage.mixin;

import net.minecraft.client.KeyboardHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerAccessor {
   @Invoker("keyPress")
   void tournage$keyPress(long window, int key, int scancode, int action, int mods);
}
