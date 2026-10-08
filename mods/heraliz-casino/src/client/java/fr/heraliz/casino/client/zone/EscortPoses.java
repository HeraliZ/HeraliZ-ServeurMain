package fr.heraliz.casino.client.zone;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.heraliz.casino.HeralizCasino;
import fr.heraliz.casino.entity.EscortEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderNameTagEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Le joueur que les videurs sortent du casino : mains en l'air quand ils arrivent, bras tenus et jambes qui trainent
 * pendant qu'ils le poussent, corps penche en arriere quand ils prennent leur elan, plongeon bras en avant quand ils le
 * jettent, puis a plat ventre dans la poussiere. Les membres sont poses par un "ArmPose" Forge ; le corps entier est
 * incline autour d'un point de son axe, de facon que le plongeon finisse exactement dans la pose couchee du jeu.
 */
@Mod.EventBusSubscriber(modid = HeralizCasino.MODID, value = Dist.CLIENT)
public final class EscortPoses {
    private EscortPoses() {
    }

    /** Ticks passes a plat ventre apres l'atterrissage (comme sur le serveur). */
    private static final int PRONE_TICKS = 38;

    private static HumanoidModel.ArmPose pose;
    /** Joueurs dont la matrice de rendu a ete inclinee (a remettre apres le rendu). */
    private static final Map<AbstractClientPlayer, Boolean> TILTED = new IdentityHashMap<>();
    /** Joueurs en plein vol plane, et joueurs qui viennent d'atterrir (ticks depuis l'atterrissage). */
    private static final Set<AbstractClientPlayer> FLYING = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<AbstractClientPlayer, Integer> LANDED = new IdentityHashMap<>();

    public static void init() {
        if (pose == null) pose = HumanoidModel.ArmPose.create("HERALIZCASINO_ESCORT", false, EscortPoses::apply);
    }

    private static float partial() {
        return Minecraft.getInstance().getFrameTime();
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    private static void set(ModelPart p, float x, float y, float z) {
        p.xRot = x;
        p.yRot = y;
        p.zRot = z;
    }

    // =================================================================== membres
    /** Ticks de fondu d'une attitude a la suivante. */
    private static final float BLEND = 6f;

    /**
     * Attitude du joueur a un temps de la scene (radians) : bras droit x, z ; bras gauche x, z ; jambe droite x ;
     * jambe gauche x ; tete x. Des gestes lents et amples : pas de tremblement.
     */
    private static float[] limbs(int phase, float since, float time) {
        return switch (phase) {
            // il voit arriver les videurs : les mains se levent
            case EscortEntity.WAIT -> {
                float k = ease((since - 4) / 10f), sway = 0.04f * Mth.sin(time * 0.12f);
                yield new float[]{Mth.lerp(k, 0, -2.7f) + sway, Mth.lerp(k, 0.05f, 0.3f), Mth.lerp(k, 0, -2.7f) - sway, Mth.lerp(k, -0.05f, -0.3f),
                        0, 0, -0.1f * k};
            }
            // attrape : les bras tenus sur les cotes, la tete basse
            case EscortEntity.HELD -> new float[]{0.45f, 0.55f, 0.45f, -0.55f, 0.08f, -0.08f, 0.3f};
            // pousse vers la porte : il marche en trainant les pieds, bras tenus, tete basse
            case EscortEntity.MARCH -> {
                float step = Mth.sin(time * 0.5f) * ease(since / 8f);
                yield new float[]{0.5f, 0.55f, 0.5f, -0.55f, 0.15f + 0.42f * step, 0.15f - 0.42f * step, 0.35f};
            }
            // tire en arriere pour l'elan : les bras montent, il se cabre
            case EscortEntity.WINDUP -> new float[]{-2.2f, 0.55f, -2.1f, -0.55f, -0.45f, -0.2f, -0.2f};
            // jete dehors : plongeon, bras tendus devant lui
            case EscortEntity.THROWN -> {
                float kick = 0.18f * Mth.sin(time * 0.45f);
                yield new float[]{-2.95f, 0.3f, -2.95f, -0.3f, 0.3f + kick, 0.15f - kick, -0.45f};
            }
            default -> new float[]{0, 0.05f, 0, -0.05f, 0, 0, 0};
        };
    }

    /** Pose des membres pendant l'animation du modele du joueur (appelee pour chaque bras). */
    private static void apply(HumanoidModel<?> model, LivingEntity e, HumanoidArm arm) {
        byte phase;
        float since;
        if (e.getVehicle() instanceof EscortEntity esc) {
            phase = esc.phase();
            since = esc.phaseAge + partial();
        } else if (FLYING.contains(e)) {
            // lache en plein vol, atterrissage pas encore note : il plonge toujours
            phase = EscortEntity.THROWN;
            since = 100;
        } else {
            return;
        }
        float time = e.tickCount + partial();
        float[] q = limbs(phase, since, time);
        if (since < BLEND) {
            // fondu depuis la fin du temps precedent (les temps se suivent dans l'ordre)
            float[] from = limbs(phase - 1, 1000, time);
            float k = ease(since / BLEND);
            for (int i = 0; i < q.length; i++) q[i] = Mth.lerp(k, from[i], q[i]);
        }
        if (arm == HumanoidArm.RIGHT) set(model.rightArm, q[0], 0, q[1]);
        else set(model.leftArm, q[2], 0, q[3]);
        model.rightLeg.xRot = q[4];
        model.leftLeg.xRot = q[5];
        model.rightLeg.yRot = 0;
        model.leftLeg.yRot = 0;
        // la tete ne suit plus la souris : elle suit la scene
        model.head.xRot = q[6];
        model.head.yRot = 0;
    }

    // =================================================================== corps
    /**
     * Incline le corps de "lean" degres vers l'avant autour du point de son axe situe a la hauteur "pivot" (blocs
     * au-dessus des pieds), ce point etant place a "height" blocs au-dessus de la position du joueur. Avec 90, 1 et 0,3 :
     * exactement la pose couchee du jeu (nage / passage bas).
     */
    private static void lean(PoseStack ps, float turn, float lean, float pivot, float height) {
        ps.pushPose();
        ps.translate(0, height, 0);
        ps.mulPose(Axis.YP.rotationDegrees(turn));
        ps.mulPose(Axis.XP.rotationDegrees(-lean));
        ps.mulPose(Axis.YP.rotationDegrees(-turn));
        ps.translate(0, -pivot, 0);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        if (pose == null || event.isCanceled() || !(event.getEntity() instanceof AbstractClientPlayer p)) return;
        float partial = event.getPartialTick();
        // rotation du modele autour de la verticale (voir LivingEntityRenderer#setupRotations)
        float turn = 180f - Mth.rotLerp(partial, p.yBodyRotO, p.yBodyRot);
        PoseStack ps = event.getPoseStack();
        if (p.getVehicle() instanceof EscortEntity esc) {
            PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
            m.rightArmPose = pose;
            m.leftArmPose = pose;
            float time = p.tickCount + partial, since = esc.phaseAge + partial;
            float lean, pivot = 0.5f, height = 0.5f;
            switch (esc.phase()) {
                case EscortEntity.HELD -> lean = 8f * ease(since / 8f);
                case EscortEntity.MARCH -> lean = Mth.lerp(ease(since / 8f), 8f, 13f + 1.5f * Mth.sin(time * 0.5f));
                case EscortEntity.WINDUP -> lean = Mth.lerp(ease(since / 8f), 13f, -22f);
                case EscortEntity.THROWN -> {
                    // il bascule a l'horizontale et file a plat, bras en avant
                    float k = ease(since / 6f);
                    lean = Mth.lerp(k, -22f, 90f);
                    pivot = Mth.lerp(k, 0.5f, 1f);
                    height = Mth.lerp(k, 0.5f, 0.3f);
                }
                default -> lean = 0;
            }
            if (Math.abs(lean) < 0.5f) return;
            lean(ps, turn, lean, pivot, height);
            TILTED.put(p, Boolean.TRUE);
        } else if (FLYING.contains(p) || (LANDED.containsKey(p) && p.isVisuallySwimming())) {
            // il vient d'atterrir : le jeu le couche en une demi-seconde ; on complete pour qu'il soit a plat d'emblee
            // (et des la premiere image, avant meme que l'atterrissage soit note au tick suivant)
            float f = p.isVisuallySwimming() ? p.getSwimAmount(partial) : 0;
            if (f >= 0.999f) return;
            if (FLYING.contains(p)) {
                PlayerModel<AbstractClientPlayer> m = event.getRenderer().getModel();
                m.rightArmPose = pose;
                m.leftArmPose = pose;
            }
            ps.pushPose();
            ps.mulPose(Axis.YP.rotationDegrees(turn));
            ps.mulPose(Axis.XP.rotationDegrees(-90f * (1 - f)));
            // tant que le jeu n'a pas commence a le coucher, il ne decale pas non plus le modele
            if (f <= 0) ps.translate(0, -1f, 0.3f);
            ps.mulPose(Axis.YP.rotationDegrees(-turn));
            TILTED.put(p, Boolean.TRUE);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderPost(RenderPlayerEvent.Post event) {
        if (event.getEntity() instanceof AbstractClientPlayer p && TILTED.remove(p) != null) event.getPoseStack().popPose();
    }

    /** Pas de pseudo flottant (il basculerait avec le corps) pendant le vol plane et la chute. */
    @SubscribeEvent
    public static void onNameTag(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer p)) return;
        Integer landed = LANDED.get(p);
        if (FLYING.contains(p) || (landed != null && landed < 14)) event.setResult(Event.Result.DENY);
    }

    // =================================================================== atterrissage
    /**
     * A plat ventre apres le vol plane : la pose couchee est calculee par chaque client (elle n'est pas transmise par le
     * serveur), on l'impose donc ici a tous les joueurs qu'on voit atterrir, soi-meme compris.
     */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            FLYING.clear();
            LANDED.clear();
            TILTED.clear();
            return;
        }
        if (mc.isPaused()) return;
        for (AbstractClientPlayer p : mc.level.players()) {
            if (p.getVehicle() instanceof EscortEntity esc) {
                if (esc.phase() == EscortEntity.THROWN) FLYING.add(p);
            } else if (FLYING.remove(p)) {
                LANDED.put(p, 0);
                p.setForcedPose(Pose.SWIMMING);
                p.setPose(Pose.SWIMMING);
            }
        }
        FLYING.removeIf(p -> p.isRemoved() || p.level() != mc.level);
        LANDED.entrySet().removeIf(en -> {
            AbstractClientPlayer p = en.getKey();
            int k = en.getValue() + 1;
            en.setValue(k);
            if (k < PRONE_TICKS && !p.isRemoved() && p.level() == mc.level) return false;
            p.setForcedPose(null);
            return true;
        });
    }
}
