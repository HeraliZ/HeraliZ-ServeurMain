package fr.heraliz.casino.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.heraliz.casino.block.MachineBlock;
import fr.heraliz.casino.block.MachineBlockEntity;
import fr.heraliz.casino.block.entity.DuelBlockEntity;
import fr.heraliz.casino.machine.Footprint;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ZombieModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;

/**
 * Le bot des duels (commande /casino bot) : un zombie a la place du joueur absent, debout a la table de
 * pierre-feuille-ciseaux, assis a la bataille navale. Simple modele dessine, sans entite dans le monde.
 */
public final class BotFigure {
    private BotFigure() {
    }

    private static final ResourceLocation TEX = new ResourceLocation("minecraft", "textures/entity/zombie/zombie.png");
    private static ZombieModel<Zombie> model;
    private static Zombie dummy;

    public static void render(DuelBlockEntity be, PoseStack pose, MultiBufferSource buf, int light, float time) {
        if (be.botSide == 0 || be.phase == DuelBlockEntity.IDLE) return;
        MachineBlockEntity.Spot s = be.seatOf(be.botSide);
        Minecraft mc = Minecraft.getInstance();
        if (s == null || mc.level == null) return;
        if (model == null) model = new ZombieModel<>(mc.getEntityModels().bakeLayer(ModelLayers.ZOMBIE));
        if (dummy == null || dummy.level() != mc.level) dummy = new Zombie(EntityType.ZOMBIE, mc.level);
        Direction facing = be.getBlockState().getValue(MachineBlock.FACING);
        float[] c = MachineRenderer.toLocal(s.x(), s.y(), s.z(), MachineRenderer.yRot(be.getBlockState()));
        float yaw = Footprint.toWorldYaw(facing, s.lookX(), s.lookZ());
        pose.pushPose();
        pose.translate(c[0], c[1] + (s.sit() ? s.feet() : 0), c[2]);
        // leger balancement, comme un zombie qui attend
        float sway = (float) Math.sin(time * 0.05f) * 4f;
        pose.mulPose(Axis.YP.rotationDegrees(180f - yaw + sway * 0.3f));
        pose.scale(-0.9375f, -0.9375f, 0.9375f);
        pose.translate(0, -1.501f, 0);
        dummy.setAggressive(be.phase == DuelBlockEntity.PLAYING);
        model.riding = s.sit();
        model.young = false;
        model.attackTime = 0;
        model.prepareMobModel(dummy, 0, 0, 0);
        model.setupAnim(dummy, 0, 0, time, sway, s.sit() ? 25f : 10f);
        VertexConsumer vc = buf.getBuffer(model.renderType(TEX));
        model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        pose.popPose();
    }
}
