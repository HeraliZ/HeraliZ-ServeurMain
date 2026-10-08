package fr.heraliz.casino.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.block.entity.DuelBlockEntity;
import fr.heraliz.casino.block.entity.RpsBlockEntity;
import fr.heraliz.casino.client.render.scene.Designs;
import fr.heraliz.casino.client.render.scene.DrawList;
import fr.heraliz.casino.client.render.scene.Pose;
import fr.heraliz.casino.client.render.scene.machines.RpsDesign;
import fr.heraliz.casino.game.duel.Rps;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Pierre-feuille-ciseaux : les joueurs du duel mettent les mains dans le dos pendant le choix et le "CHI-FOU-MI",
 * puis sortent la main droite avec leur objet (pierre, feuille ou ciseaux) et le lancent sur la table.
 * Les bras sont poses par un "ArmPose" Forge (applique pendant l'animation du modele du joueur) ; l'objet est dessine
 * dans la main apres le rendu du joueur.
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID, value = Dist.CLIENT)
public final class DuelPoses {
    private DuelPoses() {
    }

    private static HumanoidModel.ArmPose pose;
    /** Joueurs en duel (UUID -> table), mis a jour a chaque tick client par les tables proches. */
    private static final Map<UUID, RpsBlockEntity> PLAYERS = new HashMap<>();
    private static final DrawList LIST = new DrawList();
    private static final Pose POSE = new Pose();

    /** Creation du pose de bras (au demarrage du client). */
    public static void init() {
        if (pose == null) pose = HumanoidModel.ArmPose.create("HERALIZCASINO_DUEL", false, DuelPoses::apply);
    }

    /** Une table de pierre-feuille-ciseaux signale ses joueurs (appele a chaque tick client). */
    public static void track(RpsBlockEntity be) {
        boolean active = be.phase == DuelBlockEntity.PLAYING || (be.phase == DuelBlockEntity.DONE && be.step == RpsBlockEntity.REVEAL);
        for (UUID id : new UUID[]{be.playerA, be.playerB}) {
            if (id == null) continue;
            if (active) PLAYERS.put(id, be);
            else PLAYERS.remove(id, be);
        }
        PLAYERS.values().removeIf(b -> b.isRemoved());
    }

    @Nullable
    private static RpsBlockEntity duelOf(LivingEntity e) {
        RpsBlockEntity be = PLAYERS.get(e.getUUID());
        if (be == null || be.isRemoved() || be.sideOf(e.getUUID()) == 0) return null;
        if (be.phase != DuelBlockEntity.PLAYING && !(be.phase == DuelBlockEntity.DONE && be.step == RpsBlockEntity.REVEAL)) return null;
        // assez pres de la table
        if (e.distanceToSqr(be.getBlockPos().getX() + 0.5, be.getBlockPos().getY(), be.getBlockPos().getZ() + 0.5) > 6 * 6) return null;
        return be;
    }

    private static float sinceReveal(RpsBlockEntity be, float partial) {
        return be.getLevel() == null ? 1e6f : be.getLevel().getGameTime() - be.revealTick + partial;
    }

    private static float partial() {
        return Minecraft.getInstance().getFrameTime();
    }

    // =================================================================== bras
    /** Pose d'un bras (appele par le modele du joueur pendant son animation). */
    private static void apply(HumanoidModel<?> model, LivingEntity e, HumanoidArm arm) {
        RpsBlockEntity be = duelOf(e);
        if (be == null) return;
        float t = be.step == RpsBlockEntity.REVEAL ? sinceReveal(be, partial()) : -1;
        boolean right = arm == HumanoidArm.RIGHT;
        float s = right ? 1 : -1;
        var part = right ? model.rightArm : model.leftArm;
        float time = e.tickCount + partial();
        // mains dans le dos (legere oscillation ; petit a-coup a chaque "CHI", "FOU", "MI")
        float backX = 0.62f + 0.04f * Mth.sin(time * 0.15f + (right ? 0 : 1.5f));
        float beat = t >= 0 && t < Rps.T_SHOW ? 0.12f * (bell(t, 0, 6) + bell(t, Rps.T_FOU, Rps.T_FOU + 6) + bell(t, Rps.T_MI, Rps.T_MI + 6)) : 0;
        float bx = backX + beat, by = 0, bz = -0.3f * s;
        if (t < 0 || t < Rps.T_SHOW || !right) {
            // main gauche : reste dans le dos jusqu'au lancer, puis se relache
            if (!right && t >= Rps.T_THROW + 4) {
                float k = smooth(Math.min(1f, (t - Rps.T_THROW - 4) / 8f));
                set(part, Mth.lerp(k, bx, 0), 0, Mth.lerp(k, bz, 0.05f * -s));
            } else {
                set(part, bx, by, bz);
            }
            return;
        }
        // main droite : sort du dos, bras tendu vers la table avec l'objet, puis lancer et retour
        // bras tendu vers l'avant, legerement rentre vers l'axe du corps (le centre de la table)
        float fx = -1.35f, fy = -0.32f, fz = 0;
        if (t < Rps.T_SHOW + 5) {
            float k = smooth((t - Rps.T_SHOW) / 5f);
            set(part, Mth.lerp(k, bx, fx), Mth.lerp(k, by, fy), Mth.lerp(k, bz, fz));
        } else if (t < Rps.T_THROW) {
            set(part, fx + 0.04f * Mth.sin(time * 0.3f), fy, fz);
        } else if (t < Rps.T_THROW + 4) {
            // armer puis lancer
            float k = (t - Rps.T_THROW) / 4f;
            set(part, fx - 0.5f * Mth.sin(k * Mth.PI) + k * 0.6f, fy, fz);
        } else if (t < Rps.T_THROW + 14) {
            float k = smooth((t - Rps.T_THROW - 4) / 10f);
            set(part, Mth.lerp(k, fx + 0.6f, 0), Mth.lerp(k, fy, 0), 0);
        } else {
            set(part, 0, 0, 0.05f);
        }
    }

    private static void set(net.minecraft.client.model.geom.ModelPart p, float x, float y, float z) {
        p.xRot = x;
        p.yRot = y;
        p.zRot = z;
    }

    private static float bell(float t, float a, float b) {
        return t <= a || t >= b ? 0 : Mth.sin(Mth.PI * (t - a) / (b - a));
    }

    private static float smooth(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** Objets tenus en main, retires le temps du rendu (les mains ne montrent que le coup du duel). */
    private static final java.util.Map<AbstractClientPlayer, net.minecraft.world.item.ItemStack[]> HELD = new java.util.IdentityHashMap<>();

    @SubscribeEvent
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (pose == null || !(event.getEntity() instanceof AbstractClientPlayer p) || duelOf(p) == null) return;
        PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
        m.rightArmPose = pose;
        m.leftArmPose = pose;
        net.minecraft.world.item.ItemStack main = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        net.minecraft.world.item.ItemStack off = p.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.OFFHAND);
        if (!main.isEmpty() || !off.isEmpty()) {
            HELD.put(p, new net.minecraft.world.item.ItemStack[]{main, off});
            p.getInventory().items.set(p.getInventory().selected, net.minecraft.world.item.ItemStack.EMPTY);
            p.getInventory().offhand.set(0, net.minecraft.world.item.ItemStack.EMPTY);
        }
    }

    /** Securite : si un autre mod a annule le rendu apres nous, les objets sont remis au tick suivant. */
    @SubscribeEvent
    public static void onClientTick(net.minecraftforge.event.TickEvent.ClientTickEvent event) {
        if (event.phase != net.minecraftforge.event.TickEvent.Phase.START || HELD.isEmpty()) return;
        HELD.forEach((p, held) -> {
            p.getInventory().items.set(p.getInventory().selected, held[0]);
            p.getInventory().offhand.set(0, held[1]);
        });
        HELD.clear();
    }

    // =================================================================== objet dans la main
    @SubscribeEvent
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p)) return;
        net.minecraft.world.item.ItemStack[] held = HELD.remove(p);
        if (held != null) {
            p.getInventory().items.set(p.getInventory().selected, held[0]);
            p.getInventory().offhand.set(0, held[1]);
        }
        RpsBlockEntity be = duelOf(p);
        if (be == null || be.step != RpsBlockEntity.REVEAL) return;
        float partial = event.getPartialTick();
        float t = sinceReveal(be, partial);
        if (t < Rps.T_SHOW + 2 || t >= Rps.T_THROW + 2) return;
        int side = be.sideOf(p.getUUID());
        int kind = side == 1 ? be.shownA : be.shownB;
        if (kind < Rps.ROCK) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        PoseStack ps = event.getPoseStack();
        ps.pushPose();
        float bodyYaw = Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        ps.mulPose(Axis.YP.rotationDegrees(180.0F - bodyYaw));
        ps.scale(-1.0F, -1.0F, 1.0F);
        ps.scale(0.9375F, 0.9375F, 0.9375F);
        ps.translate(0.0F, -1.501F, 0.0F);
        model.translateToHand(HumanoidArm.RIGHT, ps);
        ps.mulPose(Axis.XP.rotationDegrees(-90.0F));
        ps.mulPose(Axis.YP.rotationDegrees(180.0F));
        ps.translate(-1 / 16f, 0.125F, -0.625F);
        // objet tenu devant la main, un peu tourne vers la camera
        LIST.clear();
        POSE.reset();
        RpsDesign d = (RpsDesign) Designs.get("pierre_feuille_ciseaux");
        POSE.scale(1.8f).rotX(kind == Rps.PAPER ? 70 : 0);
        d.drawObject(POSE, LIST, kind, kind == Rps.SCISSORS ? 30 : 0, 0.15f);
        SceneRenderer.Frame f = new SceneRenderer.Frame(ps, event.getMultiBufferSource(), d.id);
        f.model.identity().scale(1f / 16f);
        f.light = event.getPackedLight();
        f.item = true;
        SceneRenderer.solid(f, LIST);
        SceneRenderer.glow(f, LIST);
        ps.popPose();
    }
}
