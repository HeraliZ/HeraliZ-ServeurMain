package fr.heraliz.interfaces.nettete.mixin;

import com.mojang.blaze3d.font.TrueTypeGlyphProvider;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.nio.ByteBuffer;
import org.lwjgl.stb.STBTTFontinfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Réglages d'un fournisseur TTF du jeu, pour en faire une copie à une autre finesse ({@code client.PolicesDensite}) :
 * police en mémoire (null une fois fermée), oversample, échelle, décalages (déjà multipliés par l'oversample), lettres
 * sautées.
 */
@Mixin(TrueTypeGlyphProvider.class)
public interface TrueTypeAccesseur {
    @Accessor("font")
    STBTTFontinfo heraliz$police();

    @Accessor("fontMemory")
    ByteBuffer heraliz$memoire();

    @Accessor("oversample")
    float heraliz$oversample();

    @Accessor("pointScale")
    float heraliz$echelle();

    @Accessor("shiftX")
    float heraliz$decalageX();

    @Accessor("shiftY")
    float heraliz$decalageY();

    @Accessor("skip")
    IntSet heraliz$sautees();
}
