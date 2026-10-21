package fr.heralisz.utils.zombies.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.utils.HeraliszUtils;
import fr.heralisz.utils.zombies.ZombieSurvie;
import fr.heralisz.utils.zombies.ZombiesSurvie;
import fr.heralisz.utils.zombies.ZombiesSurvie.Tenue;
import net.minecraft.client.model.AnimationUtils;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rendu des zombies de la survie : corps de joueur (64 x 64, calques du dessus compris), bras tendus du zombie de
 * Minecraft, et machoire 3D (6 x 2 x 3, texture 32 x 32 a part, dents du bas) accrochee sous la tete : entrouverte au
 * repos, grogne quand il chasse, grande ouverte quand il frappe. Textures : textures/entity/zombies/.
 */
@Mod.EventBusSubscriber(modid = HeraliszUtils.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ZombiesSurvieClient {
    private ZombiesSurvieClient() {
    }

    static final ModelLayerLocation CORPS = new ModelLayerLocation(new ResourceLocation(HeraliszUtils.MODID, "zombie_survie"), "main");
    static final ModelLayerLocation MACHOIRE = new ModelLayerLocation(new ResourceLocation(HeraliszUtils.MODID, "zombie_survie"), "machoire");

    private static final ResourceLocation[] PEAUX = new ResourceLocation[Tenue.values().length];
    private static final ResourceLocation[] MACHOIRES = new ResourceLocation[Tenue.values().length];

    static {
        for (Tenue t : Tenue.values()) {
            String n = t.ordinal() < Tenue.VILLES ? "ville_" + t.ordinal() : t.name().toLowerCase(java.util.Locale.ROOT);
            PEAUX[t.ordinal()] = new ResourceLocation(HeraliszUtils.MODID, "textures/entity/zombies/" + n + ".png");
            MACHOIRES[t.ordinal()] = new ResourceLocation(HeraliszUtils.MODID, "textures/entity/zombies/machoire_" + n + ".png");
        }
    }

    @SubscribeEvent
    public static void couches(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(CORPS, () -> LayerDefinition.create(PlayerModel.createMesh(CubeDeformation.NONE, false), 64, 64));
        e.registerLayerDefinition(MACHOIRE, () -> {
            MeshDefinition m = new MeshDefinition();
            m.getRoot().addOrReplaceChild("machoire", CubeListBuilder.create().texOffs(0, 0).addBox(-3f, -0.4f, -3.15f, 6f, 2f, 3f),
                    PartPose.offset(0f, -1.6f, -1.2f));
            return LayerDefinition.create(m, 32, 32);
        });
    }

    @SubscribeEvent
    public static void rendus(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ZombiesSurvie.ZOMBIE.get(), Rendu::new);
    }

    public static final class Modele extends PlayerModel<ZombieSurvie> {
        Modele(ModelPart racine) {
            super(racine, false);
        }

        @Override
        public void setupAnim(ZombieSurvie z, float pas, float ampleur, float age, float lacet, float tangage) {
            super.setupAnim(z, pas, ampleur, age, lacet, tangage);
            AnimationUtils.animateZombieArms(leftArm, rightArm, z.isAggressive(), attackTime, age);
            leftSleeve.copyFrom(leftArm);
            rightSleeve.copyFrom(rightArm);
        }
    }

    static final class Rendu extends HumanoidMobRenderer<ZombieSurvie, Modele> {
        Rendu(EntityRendererProvider.Context ctx) {
            super(ctx, new Modele(ctx.bakeLayer(CORPS)), 0.5f);
            addLayer(new HumanoidArmorLayer<>(this, new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_INNER_ARMOR)),
                    new HumanoidModel<>(ctx.bakeLayer(ModelLayers.ZOMBIE_OUTER_ARMOR)), ctx.getModelManager()));
            addLayer(new Machoire(this, ctx.bakeLayer(MACHOIRE).getChild("machoire")));
        }

        @Override
        public ResourceLocation getTextureLocation(ZombieSurvie z) {
            return PEAUX[z.tenue().ordinal()];
        }
    }

    static final class Machoire extends RenderLayer<ZombieSurvie, Modele> {
        private final ModelPart machoire;

        Machoire(RenderLayerParent<ZombieSurvie, Modele> parent, ModelPart machoire) {
            super(parent);
            this.machoire = machoire;
        }

        @Override
        public void render(PoseStack ps, MultiBufferSource buf, int lumiere, ZombieSurvie z, float pas, float ampleur, float partiel,
                           float age, float lacet, float tangage) {
            if (z.isInvisible()) return;
            ModelPart tete = getParentModel().head;
            if (!tete.visible) return;
            float coup = z.getAttackAnim(partiel);
            float o;
            if (coup > 0f) o = 0.2f + 0.45f * Mth.sin(coup * Mth.PI);
            else if (z.isAggressive()) o = 0.3f + 0.12f * Mth.sin(age * 0.35f);
            else o = 0.1f + 0.12f * Math.max(0f, Mth.sin(age * 0.07f));
            machoire.xRot = o;
            ps.pushPose();
            tete.translateAndRotate(ps);
            machoire.render(ps, buf.getBuffer(RenderType.entityCutoutNoCull(MACHOIRES[z.tenue().ordinal()])), lumiere,
                    LivingEntityRenderer.getOverlayCoords(z, 0f));
            ps.popPose();
        }
    }
}
