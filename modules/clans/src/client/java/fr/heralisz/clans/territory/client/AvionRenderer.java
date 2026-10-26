package fr.heralisz.clans.territory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heralisz.clans.HeraliszClans;
import fr.heralisz.clans.territory.supply.AvionEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Rendu de l'avion cargo : maillage avion (nez vers -z, helices = parties tournantes du JSON, t = ticks + fraction
 * d'image), oriente par le lacet de l'entite ; avion_loin au-dela de 160 blocs (il vole a y = 180 : le detail ne
 * sert qu'au passage au-dessus des joueurs).
 */
@OnlyIn(Dist.CLIENT)
public class AvionRenderer extends EntityRenderer<AvionEntity> {
    private static final ResourceLocation TEXTURE = HeraliszClans.id("textures/entity/avion.png");
    private static final double LOIN2 = 160 * 160;

    public AvionRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(AvionEntity e, float yaw, float partialTick, PoseStack ps, MultiBufferSource buf, int packedLight) {
        double d2 = entityRenderDispatcher.camera == null ? 0 : entityRenderDispatcher.camera.getPosition().distanceToSqr(e.position());
        float lacet = Mth.rotLerp(partialTick, e.yRotO, e.getYRot());
        ps.pushPose();
        ps.mulPose(Axis.YP.rotationDegrees(180 - lacet));
        // leger roulis pour casser la raideur de la ligne droite
        ps.mulPose(Axis.ZP.rotationDegrees(Mth.sin((e.tickCount + partialTick) * 0.05f) * 1.5f));
        Maillage.get(d2 > LOIN2 ? "avion_loin" : "avion").dessiner(ps, buf, packedLight, e.tickCount + partialTick, 0);
        ps.popPose();
        super.render(e, yaw, partialTick, ps, buf, packedLight);
    }

    /** Un avion que le serveur ne fait plus bouger n'est pas dessine (il resterait suspendu dans le ciel). */
    @Override
    public boolean shouldRender(AvionEntity e, Frustum frustum, double camX, double camY, double camZ) {
        return !e.fige() && super.shouldRender(e, frustum, camX, camY, camZ);
    }

    @Override
    public ResourceLocation getTextureLocation(AvionEntity e) {
        return TEXTURE;
    }
}
