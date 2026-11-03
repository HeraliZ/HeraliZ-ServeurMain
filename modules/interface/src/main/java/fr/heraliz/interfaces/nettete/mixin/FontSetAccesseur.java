package fr.heraliz.interfaces.nettete.mixin;

import com.mojang.blaze3d.font.GlyphProvider;
import java.util.List;
import net.minecraft.client.gui.font.FontSet;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Nom et fournisseurs d'une police du jeu, lus par {@code client.PolicesDensite}. */
@Mixin(FontSet.class)
public interface FontSetAccesseur {
    @Accessor("name")
    ResourceLocation heraliz$nom();

    @Accessor("providers")
    List<GlyphProvider> heraliz$fournisseurs();
}
