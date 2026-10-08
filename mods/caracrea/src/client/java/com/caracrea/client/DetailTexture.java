package com.caracrea.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * A texture kept only on the GPU: the detail atlas of a character is
 * uploaded once after painting, then only the face rectangle is replaced
 * when the character blinks or talks.
 */
final class DetailTexture extends AbstractTexture {
    private final int size;
    private boolean allocated;

    DetailTexture(int size) {
        this.size = size;
    }

    @Override
    public void load(ResourceManager manager) {
    }

    /** Replaces a rectangle of the texture with a small image (kept open for reuse). Render thread only. */
    void uploadRegion(NativeImage image, int x, int y) {
        if (!allocated) {
            return;
        }
        bind();
        image.upload(0, x, y, 0, 0, image.getWidth(), image.getHeight(), false, false, false, false);
    }

    /** Uploads the image and frees it. Render thread only. */
    void upload(NativeImage image) {
        RenderSystem.assertOnRenderThreadOrInit();
        if (!allocated) {
            TextureUtil.prepareImage(getId(), size, size);
            allocated = true;
        }
        bind();
        image.upload(0, 0, 0, 0, 0, size, size, false, false, false, true);
    }
}
