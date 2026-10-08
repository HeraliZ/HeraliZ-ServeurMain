package fr.heraliz.casino.client.zone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.entity.GuardEntity;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Rendu du videur : un peu plus grand et plus large qu'un joueur, et sa replique dans une bulle au-dessus de sa tete.
 */
public class GuardRenderer extends HumanoidMobRenderer<GuardEntity, GuardModel> {
    private static final ResourceLocation TEXTURE = HeralizCasino.id("textures/entity/videur.png");
    /** Hauteur par rapport a un modele de joueur (2 blocs sans reduction) : une bonne tete de plus qu'un joueur. */
    public static final float SCALE = 1.05f;
    /** Hauteur du modele et de ses yeux (blocs). */
    public static final float HEIGHT = 2.0f * SCALE, EYES = 1.72f * SCALE;

    public GuardRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new GuardModel(ctx.bakeLayer(GuardModel.LAYER)), 0.55f);
    }

    @Override
    public ResourceLocation getTextureLocation(GuardEntity e) {
        return TEXTURE;
    }

    @Override
    protected void scale(GuardEntity e, PoseStack pose, float partial) {
        pose.scale(SCALE * 1.08f, SCALE, SCALE * 1.08f);
    }

    /** Pas de nom flottant : le videur parle par sa bulle. */
    @Override
    protected boolean shouldShowName(GuardEntity e) {
        return false;
    }

    @Override
    public void render(GuardEntity e, float yaw, float partial, PoseStack pose, MultiBufferSource buf, int light) {
        super.render(e, yaw, partial, pose, buf, light);
        bubble(e, partial, pose, buf);
    }

    /** Bulle : panneau sombre a lisere ambre, texte blanc, toujours face a la camera ; elle "pousse" puis s'efface. */
    private void bubble(GuardEntity e, float partial, PoseStack pose, MultiBufferSource buf) {
        float age = e.speechAge + partial;
        Component text = e.speech();
        if (age >= GuardEntity.SPEECH_TICKS || text.getString().isEmpty()) return;
        // scene de l'expulsion, dialogue a l'entree : la replique est deja a l'ecran, la bulle masquerait le videur
        if (ZoneCinema.subtitles(e.getId()) || ZoneClient.talkingTo(e.getId())) return;
        if (entityRenderDispatcher.distanceToSqr(e) > 40 * 40) return;
        Font font = getFont();
        List<FormattedCharSequence> lines = font.split(text, 140);
        if (lines.isEmpty()) return;
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
        int h = lines.size() * 10;
        float in = Mth.clamp(age / 4f, 0, 1), out = Mth.clamp((GuardEntity.SPEECH_TICKS - age) / 8f, 0, 1);
        float pop = in * in * (3 - 2 * in);
        int alpha = (int) (255 * out);
        if (alpha < 8) return;
        pose.pushPose();
        pose.translate(0, HEIGHT + 0.42f, 0);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        float s = 0.022f * (0.55f + 0.45f * pop);
        pose.scale(-s, -s, s);
        Matrix4f m = pose.last().pose();
        float x0 = -w / 2f - 5, x1 = w / 2f + 5, y0 = -h - 5, y1 = 3;
        VertexConsumer bg = buf.getBuffer(RenderType.textBackground());
        quad(bg, m, x0 - 1.5f, y0 - 1.5f, x1 + 1.5f, y1 + 1.5f, 0.03f, 0xFFB040, (int) (alpha * 0.9f));
        quad(bg, m, x0, y0, x1, y1, 0.02f, 0x14100C, (int) (alpha * 0.93f));
        // petite pointe vers le videur
        quad(bg, m, -3, y1, 3, y1 + 3, 0.02f, 0x14100C, (int) (alpha * 0.93f));
        int color = (alpha << 24) | 0xFFFFFF;
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence l = lines.get(i);
            font.drawInBatch(l, -font.width(l) / 2f, y0 + 4 + i * 10, color, false, m, buf, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        }
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, float x0, float y0, float x1, float y1, float z, int rgb, int alpha) {
        int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
        vc.vertex(m, x0, y0, z).color(r, g, b, alpha).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x0, y1, z).color(r, g, b, alpha).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x1, y1, z).color(r, g, b, alpha).uv2(LightTexture.FULL_BRIGHT).endVertex();
        vc.vertex(m, x1, y0, z).color(r, g, b, alpha).uv2(LightTexture.FULL_BRIGHT).endVertex();
    }
}
