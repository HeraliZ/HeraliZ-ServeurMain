package fr.heraliz.interfaces.nettete.mixin;

import com.mojang.blaze3d.font.GlyphInfo;
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

    @Redirect(method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/font/FontSet;getRandomGlyph(Lcom/mojang/blaze3d/font/GlyphInfo;)Lnet/minecraft/client/gui/font/glyphs/BakedGlyph;"))
    private BakedGlyph heraliz$lettreBrouillee(FontSet set, GlyphInfo info) {
        return PolicesDensite.auHasard(set, info);
    }

    /** Gras de nos lettres nettes : redessinées un point d'écran plus loin, pas une unité (voir PolicesDensite). */
    @Redirect(method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/font/GlyphInfo;getBoldOffset()F"))
    private float heraliz$gras(GlyphInfo info) {
        return PolicesDensite.decalageGras(info);
    }

    /** Ombre de nos lettres nettes : un point d'écran en bas à droite, pas une unité. */
    @Redirect(method = "accept(ILnet/minecraft/network/chat/Style;I)Z",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/font/GlyphInfo;getShadowOffset()F"))
    private float heraliz$ombre(GlyphInfo info) {
        return PolicesDensite.decalageOmbre(info);
    }
}
