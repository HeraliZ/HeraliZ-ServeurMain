package fr.heraliz.crac.build.client;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.HashMap;
import java.util.Map;
import fr.heraliz.crac.build.model.PieceKind;
import fr.heraliz.crac.build.model.Tier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * 64x64 GUI icons of pieces ({@code textures/gui/pieces/<kind>.png}) and tiers ({@code textures/gui/tiers/<tier>.png}).
 * Callers fall back to text when an icon is missing.
 */
@OnlyIn(Dist.CLIENT)
public final class BuildIcons {
    private static final Map<ResourceLocation, Boolean> PRESENT = new HashMap<>();

    private BuildIcons() {
    }

    public static ResourceLocation piece(PieceKind kind) {
        return new ResourceLocation("crac", "textures/gui/pieces/" + kind.id + ".png");
    }

    public static ResourceLocation tier(Tier tier) {
        return new ResourceLocation("crac", "textures/gui/tiers/" + tier.id + ".png");
    }

    public static boolean has(ResourceLocation tex) {
        return PRESENT.computeIfAbsent(tex, t -> Minecraft.getInstance().getResourceManager().getResource(t).isPresent());
    }

    /** Resource packs changed. */
    public static void reset() {
        PRESENT.clear();
    }

    /** Draws the icon in a size x size square; false (nothing drawn) when the texture does not exist. */
    public static boolean draw(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        if (!has(tex)) {
            return false;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(tex, x, y, size, size, 0.0F, 0.0F, 64, 64, 64, 64);
        RenderSystem.disableBlend();
        return true;
    }
}
