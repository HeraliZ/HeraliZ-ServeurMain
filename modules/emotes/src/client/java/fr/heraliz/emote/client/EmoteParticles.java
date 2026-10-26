package fr.heraliz.emote.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.kosmx.playerAnim.api.TransformType;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.core.util.Vec3f;
import fr.heraliz.emote.client.catalog.ParticleSpec;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector3f;
import org.joml.Vector4f;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * Particles of the Heraliz emotes, for every player in sight who plays one. Positions follow the
 * animated body parts: the same transforms as PlayerRenderer and playerAnimator, computed here
 * instead of during rendering so shader packs and extra render passes do not matter.
 */
public final class EmoteParticles {
    private static final Map<Integer, Integer> LAST_TICK = new HashMap<>();
    private static final Map<String, ParticleOptions> TYPES = new HashMap<>();

    private EmoteParticles() {
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.isPaused()) {
            LAST_TICK.clear();
            return;
        }
        for (AbstractClientPlayer player : mc.level.players()) {
            EmotePlayback playback = EmotePlayback.of(player);
            if (playback == null || playback.info().particles().isEmpty() || player.isInvisible()
                    || player.distanceToSqr(mc.gameRenderer.getMainCamera().getPosition()) > 48 * 48) {
                LAST_TICK.remove(player.getId());
                continue;
            }
            int tick = playback.player().getTick();
            Integer last = LAST_TICK.put(player.getId(), tick);
            if (last != null && last == tick) continue;
            for (ParticleSpec spec : playback.info().particles()) {
                if (spec.emitsAt(tick)) emit(mc, player, playback, spec);
            }
        }
        if (LAST_TICK.size() > 64) LAST_TICK.clear();
    }

    private static void emit(Minecraft mc, AbstractClientPlayer player, EmotePlayback playback, ParticleSpec spec) {
        ParticleOptions options = options(spec);
        if (options == null || mc.level == null) return;
        Vector4f at = worldPoint(player, playback, spec.attach, spec.pos);
        RandomSource random = mc.level.random;
        float yaw = player.yBodyRot * Mth.DEG_TO_RAD;
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);   // forward
        double rx = -Mth.cos(yaw), rz = -Mth.sin(yaw);  // right
        for (int i = 0; i < Math.max(1, spec.count); i++) {
            double x = at.x() + (random.nextDouble() * 2 - 1) * spec.spread[0];
            double y = at.y() + (random.nextDouble() * 2 - 1) * spec.spread[1];
            double z = at.z() + (random.nextDouble() * 2 - 1) * spec.spread[2];
            double vx = rx * spec.velocity[0] + fx * spec.velocity[2] + (random.nextDouble() * 2 - 1) * spec.speed;
            double vy = spec.velocity[1] + (random.nextDouble() * 2 - 1) * spec.speed;
            double vz = rz * spec.velocity[0] + fz * spec.velocity[2] + (random.nextDouble() * 2 - 1) * spec.speed;
            if (options == ParticleTypes.NOTE) vx = random.nextInt(25) / 24.0; // the note particle takes its colour here
            mc.level.addParticle(options, x, y, z, vx, vy, vz);
        }
    }

    @Nullable
    private static ParticleOptions options(ParticleSpec spec) {
        if (spec.type.equals("minecraft:dust")) {
            float[] c = spec.color == null ? new float[]{1, 1, 1} : spec.color;
            return new DustParticleOptions(new Vector3f(c[0], c[1], c[2]), spec.size);
        }
        if (spec.type.equals("minecraft:block") && spec.block != null) {
            return new BlockParticleOption(ParticleTypes.BLOCK, BuiltInRegistries.BLOCK.get(new ResourceLocation(spec.block)).defaultBlockState());
        }
        if (spec.type.equals("minecraft:item") && spec.item != null) {
            return new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(BuiltInRegistries.ITEM.get(new ResourceLocation(spec.item))));
        }
        return TYPES.computeIfAbsent(spec.type, id -> {
            ParticleType<?> type = BuiltInRegistries.PARTICLE_TYPE.get(new ResourceLocation(id));
            return type instanceof SimpleParticleType simple ? simple : null;
        });
    }

    /** A point given in an attachment frame (pixels), in world coordinates. */
    public static Vector4f worldPoint(AbstractClientPlayer player, EmotePlayback playback, String attach, float[] pos) {
        KeyframeAnimationPlayer anim = (KeyframeAnimationPlayer) playback.player();
        PoseStack pose = new PoseStack();
        // LivingEntityRenderer.render + setupRotations, then playerAnimator's whole-body transform
        pose.translate(player.getX(), player.getY(), player.getZ());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - player.yBodyRot));
        boolean world = attach.equals("world");
        if (!world) {
            Vec3f bodyPos = anim.get3DTransform("body", TransformType.POSITION, 0, Vec3f.ZERO);
            Vec3f bodyRot = anim.get3DTransform("body", TransformType.ROTATION, 0, Vec3f.ZERO);
            pose.translate(bodyPos.getX(), bodyPos.getY() + 0.7, bodyPos.getZ());
            pose.mulPose(Axis.ZP.rotation(bodyRot.getZ()));
            pose.mulPose(Axis.YP.rotation(bodyRot.getY()));
            pose.mulPose(Axis.XP.rotation(bodyRot.getX()));
            pose.translate(0, -0.7, 0);
        }
        pose.scale(-1.0F, -1.0F, 1.0F);
        pose.scale(0.9375F, 0.9375F, 0.9375F);
        pose.translate(0.0F, -1.501F, 0.0F);
        float ox = 0, oy = 0;
        String part = switch (attach) {
            case "rightHand" -> {
                ox = -1;
                oy = 10;
                yield "rightArm";
            }
            case "leftHand" -> {
                ox = 1;
                oy = 10;
                yield "leftArm";
            }
            case "body" -> "torso";
            default -> attach;
        };
        float[] rest = switch (part) {
            case "rightArm" -> new float[]{-5, 2, 0};
            case "leftArm" -> new float[]{5, 2, 0};
            case "rightLeg" -> new float[]{-1.9F, 12, 0};
            case "leftLeg" -> new float[]{1.9F, 12, 0};
            default -> new float[]{0, 0, 0};
        };
        if (!part.equals("root") && !world) {
            Vec3f p = anim.get3DTransform(part, TransformType.POSITION, 0, new Vec3f(rest[0], rest[1], rest[2]));
            Vec3f r = anim.get3DTransform(part, TransformType.ROTATION, 0, Vec3f.ZERO);
            pose.translate(p.getX() / 16.0F, p.getY() / 16.0F, p.getZ() / 16.0F);
            pose.mulPose(Axis.ZP.rotation(r.getZ()));
            pose.mulPose(Axis.YP.rotation(r.getY()));
            pose.mulPose(Axis.XP.rotation(r.getX()));
        }
        return new Vector4f((pos[0] + ox) / 16.0F, (pos[1] + oy) / 16.0F, pos[2] / 16.0F, 1.0F).mul(pose.last().pose());
    }
}
