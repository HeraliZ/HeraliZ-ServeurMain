package fr.heraliz.interfaces.nettete.mixin;

import fr.heraliz.interfaces.client.PolicesDensite;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Rendu d'une chaîne de texte : la lettre de nos polices est prise à la finesse de l'écran ({@link PolicesDensite}). Un
 * renvoi plutôt qu'une injection : aucun objet créé par lettre.
 */
@Mixin(targets = "net.minecraft.client.gui.Font$StringRenderOutput")
public abstract class TexteDensiteMixin {
    @Shadow
    @Final
    private Matrix4f pose;

    @Redirect(method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/FontSet;getGlyph(I)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"))
    private BakedGlyph heraliz$lettreNette(FontSet set, int cp) {
        return PolicesDensite.glyphe(set, cp, this.pose);
    }
}
