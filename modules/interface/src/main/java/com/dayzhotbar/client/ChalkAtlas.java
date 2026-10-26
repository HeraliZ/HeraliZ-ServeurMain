package com.dayzhotbar.client;

import com.dayzhotbar.DayzHotbar;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * The {@link SlotArt} cells in one texture, painted again when the GUI scale
 * or the slant changes, and drawn tinted, one texel per screen pixel.
 */
final class ChalkAtlas {
    private static final ResourceLocation ID = new ResourceLocation(DayzHotbar.MOD_ID, "hotbar_chalk");
    private static final int COLUMNS = 8;

    private SlotArt art;
    private int textureWidth;
    private int textureHeight;
    private BufferBuilder buffer;
    private Matrix4f pose;

    /** Starts a batch of cells. */
    SlotArt begin(GuiGraphics g, float guiScale, float k) {
        if (art == null || art.scale != guiScale || art.k != k) {
            paint(guiScale, k);
        }
        g.flush();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, ID);
        buffer = Tesselator.getInstance().getBuilder();
        buffer.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        pose = g.pose().last().pose();
        return art;
    }

    /** Draws a cell over the slot whose unslanted rectangle starts at (slotX, top), in GUI pixels. */
    void draw(int cell, float slotX, float top, int color) {
        float x0 = snap(slotX - art.slotX);
        float y0 = snap(top - SlotArt.MARGIN);
        float x1 = x0 + art.cellWidth;
        float y1 = y0 + art.cellHeight;
        float u0 = cell % COLUMNS * art.cellPixelsX() / (float) textureWidth;
        float v0 = cell / COLUMNS * art.cellPixelsY() / (float) textureHeight;
        float u1 = u0 + art.cellPixelsX() / (float) textureWidth;
        float v1 = v0 + art.cellPixelsY() / (float) textureHeight;
        int a = color >>> 24;
        int r = color >> 16 & 0xFF;
        int g = color >> 8 & 0xFF;
        int b = color & 0xFF;
        buffer.vertex(pose, x0, y0, 0).uv(u0, v0).color(r, g, b, a).endVertex();
        buffer.vertex(pose, x0, y1, 0).uv(u0, v1).color(r, g, b, a).endVertex();
        buffer.vertex(pose, x1, y1, 0).uv(u1, v1).color(r, g, b, a).endVertex();
        buffer.vertex(pose, x1, y0, 0).uv(u1, v0).color(r, g, b, a).endVertex();
    }

    void end() {
        BufferUploader.drawWithShader(buffer.end());
    }

    private float snap(float v) {
        return Math.round(v * art.scale) / art.scale;
    }

    private void paint(float guiScale, float k) {
        art = new SlotArt(guiScale, k);
        int cellX = art.cellPixelsX();
        int cellY = art.cellPixelsY();
        textureWidth = cellX * COLUMNS;
        textureHeight = cellY * ((SlotArt.CELLS + COLUMNS - 1) / COLUMNS);
        NativeImage image = new NativeImage(textureWidth, textureHeight, true);
        for (int cell = 0; cell < SlotArt.CELLS; cell++) {
            Chalk chalk = art.paint(cell);
            int ox = cell % COLUMNS * cellX;
            int oy = cell / COLUMNS * cellY;
            for (int y = 0; y < chalk.height; y++) {
                for (int x = 0; x < chalk.width; x++) {
                    int a = Math.round(chalk.alpha[y * chalk.width + x] * 255);
                    if (a > 0) {
                        image.setPixelRGBA(ox + x, oy + y, a << 24 | 0xFFFFFF);
                    }
                }
            }
        }
        // Replaces (and frees) the texture painted for the previous scale.
        Minecraft.getInstance().getTextureManager().register(ID, new DynamicTexture(image));
    }
}
