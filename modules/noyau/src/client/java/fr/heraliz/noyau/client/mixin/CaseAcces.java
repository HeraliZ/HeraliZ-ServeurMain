package fr.heraliz.noyau.client.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Place d'une case à l'écran ({@code Slot.x} et {@code Slot.y}, finales dans Minecraft), changée seulement chez le joueur
 * par nos écrans en verre ({@link fr.heraliz.noyau.client.kit.CasesEcran#placer}). Le serveur ne lit jamais ces nombres :
 * un clic ne transporte que le numéro de la case.
 */
@Mixin(Slot.class)
public interface CaseAcces {
    @Mutable
    @Accessor("x")
    void heraliz$poserX(int x);

    @Mutable
    @Accessor("y")
    void heraliz$poserY(int y);
}
