package fr.heraliz.corps.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.corps.HeralizCorps;
import fr.heraliz.corps.client.kit.Ui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.Arrays;

/**
 * Losanges de clan au-dessus des corps des membres de son clan : notes pendant le rendu des corps, puis dessines tous
 * ensemble apres les entites, en un seul lot (une seule texture, un seul appel de dessin, au lieu d'un changement de
 * texture par corps). Tableaux et matrices reutilises : aucune allocation.
 */
final class Marqueurs {
    private Marqueurs() {
    }

    private static final ResourceLocation TEXTURE = HeralizCorps.id("textures/misc/marqueur.png");
    private static final int PLEINE_LUMIERE = 0xF000F0;
    private static double[] pos = new double[3 * 32];
    private static int n;
    private static final Matrix4f M = new Matrix4f();
    private static final Vector4f V = new Vector4f();

    static void ajouter(double x, double y, double z) {
        if ((n + 1) * 3 > pos.length) pos = Arrays.copyOf(pos, pos.length * 2);
        int k = n * 3;
        pos[k] = x;
        pos[k + 1] = y;
        pos[k + 2] = z;
        n++;
    }

    static void dessiner(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES || n == 0) return;
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        RenderType type = RenderType.entityTranslucentEmissive(TEXTURE);
        VertexConsumer vc = buffers.getBuffer(type);
        int c = Ui.accent();
        float r = ((c >> 16) & 255) / 255F, g = ((c >> 8) & 255) / 255F, b = (c & 255) / 255F;
        var rotation = mc.getEntityRenderDispatcher().cameraOrientation();
        for (int i = 0, k = 0; i < n; i++, k += 3) {
            M.set(ps.last().pose())
                    .translate((float) (pos[k] - cam.x), (float) (pos[k + 1] - cam.y + 0.75), (float) (pos[k + 2] - cam.z))
                    .rotate(rotation)
                    .scale(0.22F);
            coin(vc, -0.5F, -0.5F, 0.0F, 1.0F, r, g, b);
            coin(vc, 0.5F, -0.5F, 1.0F, 1.0F, r, g, b);
            coin(vc, 0.5F, 0.5F, 1.0F, 0.0F, r, g, b);
            coin(vc, -0.5F, 0.5F, 0.0F, 0.0F, r, g, b);
        }
        n = 0;
        buffers.endBatch(type);
    }

    private static void coin(VertexConsumer vc, float x, float y, float u, float v, float r, float g, float b) {
        M.transform(x, y, 0.0F, 1.0F, V);
        vc.vertex(V.x, V.y, V.z, r, g, b, 0.85F, u, v, OverlayTexture.NO_OVERLAY, PLEINE_LUMIERE, 0.0F, 1.0F, 0.0F);
    }
}
