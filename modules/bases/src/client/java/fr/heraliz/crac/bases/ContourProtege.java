package fr.heraliz.crac.bases;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.lwjgl.opengl.GL11;

/**
 * Contour vert d'un joueur protégé « nouveau joueur » (variante 1 des maquettes : contour fin et net), vu par les autres.
 * <p>Coque du modèle du joueur gonflée de {@link #EPAISSEUR} pixels de skin (un peu plus que l'armure de Minecraft, qui
 * est à 1), dont on ne dessine que les faces de derrière : seul le bord qui dépasse du corps se voit. Couleur pleine,
 * sans ombre (même la nuit), et jamais à travers les murs (test de profondeur normal, contrairement à la surbrillance de
 * Minecraft). Rien pour un joueur invisible. La liste des protégés vient du serveur ({@code S2CProteges}).
 * <p>Pour la variante 2 (halo doux) : {@link #EPAISSEUR} plus grande et {@link #ALPHA} plus faible.
 */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ContourProtege {
    static final float EPAISSEUR = 1.15F;
    static final float ROUGE = 0.33F;
    static final float VERT = 0.88F;
    static final float BLEU = 0.48F;
    static final float ALPHA = 0.92F;

    static final ModelLayerLocation LARGE = new ModelLayerLocation(new ResourceLocation("crac", "contour_protege"), "large");
    static final ModelLayerLocation FINE = new ModelLayerLocation(new ResourceLocation("crac", "contour_protege"), "fine");

    private static volatile Set<UUID> proteges = Set.of();

    private ContourProtege() {
    }

    /** Reçu du serveur. */
    public static void proteges(Set<UUID> s) {
        proteges = Set.copyOf(s);
    }

    @SubscribeEvent
    public static void couches(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(LARGE, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(EPAISSEUR), false), 64, 64));
        e.registerLayerDefinition(FINE, () -> LayerDefinition.create(PlayerModel.createMesh(new CubeDeformation(EPAISSEUR), true), 64, 64));
    }

    @SubscribeEvent
    public static void ajout(EntityRenderersEvent.AddLayers e) {
        for (String skin : e.getSkins()) {
            PlayerRenderer r = e.getSkin(skin);
            if (r == null) {
                continue;
            }
            boolean fin = "slim".equals(skin);
            PlayerModel<AbstractClientPlayer> coque = new PlayerModel<>(e.getEntityModels().bakeLayer(fin ? FINE : LARGE), fin);
            r.addLayer(new Couche(r, coque));
        }
    }

    static final class Couche extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {
        private final PlayerModel<AbstractClientPlayer> coque;

        Couche(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent, PlayerModel<AbstractClientPlayer> coque) {
            super(parent);
            this.coque = coque;
        }

        @Override
        public void render(PoseStack pose, MultiBufferSource buffers, int lumiere, AbstractClientPlayer joueur, float limbSwing,
            float limbSwingAmount, float partial, float age, float tete, float regard) {
            if (joueur.isInvisible() || !proteges.contains(joueur.getUUID())) {
                return;
            }
            PlayerModel<AbstractClientPlayer> corps = this.getParentModel();
            corps.copyPropertiesTo(this.coque);
            // seulement les membres (la seconde couche du skin est déjà sous la coque)
            this.coque.setAllVisible(false);
            this.coque.head.visible = corps.head.visible;
            this.coque.body.visible = corps.body.visible;
            this.coque.leftArm.visible = corps.leftArm.visible;
            this.coque.rightArm.visible = corps.rightArm.visible;
            this.coque.leftLeg.visible = corps.leftLeg.visible;
            this.coque.rightLeg.visible = corps.rightLeg.visible;
            VertexConsumer vc = buffers.getBuffer(Types.CONTOUR);
            this.coque.renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ROUGE, VERT, BLEU, ALPHA);
        }
    }

    /** Type de rendu de la coque : couleur pleine (texture blanche), translucide, faces de DEVANT écartées. */
    static final class Types extends RenderType {
        static final ResourceLocation BLANC = new ResourceLocation("crac", "textures/entity/contour_blanc.png");

        private static final CullStateShard FACES_ARRIERE = new CullStateShard(true) {
            @Override
            public void setupRenderState() {
                RenderSystem.enableCull();
                GL11.glCullFace(GL11.GL_FRONT);
            }

            @Override
            public void clearRenderState() {
                GL11.glCullFace(GL11.GL_BACK);
            }
        };

        static final RenderType CONTOUR = create("crac_contour_protege", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
            CompositeState.builder()
                .setShaderState(RENDERTYPE_EYES_SHADER)
                .setTextureState(new TextureStateShard(BLANC, false, false))
                .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                .setCullState(FACES_ARRIERE)
                .setWriteMaskState(COLOR_DEPTH_WRITE)
                .createCompositeState(false));

        private Types(String nom, VertexFormat format, VertexFormat.Mode mode, int taille, boolean crumbling, boolean tri, Runnable avant,
            Runnable apres) {
            super(nom, format, mode, taille, crumbling, tri, avant, apres);
        }
    }
}
