package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.item.SetArmorItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Pieces du mod portees dans les slots d'armure vanilla (tete, torse, jambes, pieds), rendues en translucide.
 * La couche d'armure vanilla (HumanoidArmorLayer) dessine toujours en armorCutoutNoCull, qui ignore l'alpha partiel
 * (visieres en verre) : sur les rendus qui portent cette couche, elle recoit un modele vide (ClientArmor) et c'est ici
 * que la piece est dessinee, une seule fois, avec la meme pose (copiee du modele parent), les memes parties visibles
 * par slot, les bras fins, le niveau de detail et l'animation des lunettes de vision nocturne (et leur lueur en service).
 * Ajoutee a tous les rendus d'entites a modele humanoide (joueurs, mannequin, porte-armure, mobs) par ClientSetup.
 * 0.9.0 : combinaisons (portees au torse, toutes les parties visibles) ; superposition avec les ghillies (GearLayering).
 */
public class SetArmorLayer<T extends LivingEntity, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    /** Meme ordre que la couche vanilla. */
    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD};
    /** Rendus qui portent cette couche (recrees a chaque rechargement des ressources). */
    private static final Set<EntityRenderer<?>> HANDLED = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Modele rendu par la couche vanilla a la place de nos pieces : rien. */
    public static final Model NOTHING = new Model(RenderType::entityTranslucent) {
        @Override
        public void renderToBuffer(PoseStack pose, VertexConsumer consumer, int light, int overlay, float r, float g, float b, float a) {
        }
    };

    public SetArmorLayer(RenderLayerParent<T, M> parent) {
        super(parent);
    }

    /** Ajoute la couche a un rendu d'entite a modele humanoide (une seule fois). */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void attach(@Nullable EntityRenderer<?> renderer) {
        if (renderer instanceof LivingEntityRenderer living && living.getModel() instanceof HumanoidModel && HANDLED.add(renderer)) {
            living.addLayer(new SetArmorLayer(living));
        }
    }

    /** Nouveaux rendus (rechargement des ressources) : on oublie les anciens. */
    public static void reset() {
        HANDLED.clear();
    }

    /** Le rendu de cette entite dessine nos pieces lui-meme : la couche vanilla doit les ignorer. */
    public static boolean handles(LivingEntity entity) {
        return HANDLED.contains(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(entity));
    }

    @Override
    public void render(PoseStack pose, MultiBufferSource buffers, int light, T entity, float limbSwing, float limbSwingAmount,
                       float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.getItem() instanceof SetArmorItem item && item.getEquipmentSlot() == slot) {
                renderPiece(item.def(), stack, slot, entity, pose, buffers, light);
            }
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void renderPiece(PieceDef def, ItemStack stack, EquipmentSlot slot, T entity, PoseStack pose, MultiBufferSource buffers,
                             int light) {
        ArmorModels.Variants v = ArmorModels.INSTANCE.get(def);
        if (v == null) return;
        HumanoidModel<LivingEntity> model = ArmorModels.INSTANCE.modelFor(def, entity);
        getParentModel().copyPropertiesTo((HumanoidModel) model);
        setPartVisibility(model, def.isSuit() ? null : slot);
        // 0.9.0 : superposition avec une ghillie (haut resserre ou cache, casque agrandi...)
        if (!GearLayering.fitPiece(entity, def, model)) return;
        try {
            renderFitted(def, stack, entity, pose, buffers, light, v, model);
        } finally {
            if (model instanceof MeshArmorModel mesh) mesh.clearFit();
        }
    }

    private void renderFitted(PieceDef def, ItemStack stack, T entity, PoseStack pose, MultiBufferSource buffers, int light,
                              ArmorModels.Variants v, HumanoidModel<LivingEntity> model) {
        fr.heraliz.armory.client.devtools.CinemaClient.Pose cp = def.nvg() ? fr.heraliz.armory.client.devtools.CinemaClient.pose(entity) : null;
        float down = !def.nvg() ? 0.0F : cp != null && cp.nvg() >= 0.0F ? cp.nvg() : NvgClient.progress(entity, stack);
        if (def.nvg()) ArmorModels.applyGroups(model, "nvg", down);
        VertexConsumer vc = ItemRenderer.getArmorFoilBuffer(buffers, v.renderType(), false, stack.hasFoil());
        model.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        // jumelles en service : objectifs qui luisent en vert (pleine lumiere, additif), estompes avec la distance
        if (down > 0.95F && model instanceof MeshArmorModel mesh && mesh.hasGlow()) {
            float k = glowFade(entity) * (down - 0.95F) / 0.05F;
            if (k > 0.01F) {
                RenderType rt = fr.heraliz.armory.client.devtools.CinemaClient.ACTIVE
                        ? fr.heraliz.armory.client.devtools.CinemaClient.glowType(v.texture()) : RenderType.eyes(v.texture());
                mesh.renderGlow(pose, buffers.getBuffer(rt), k);
            }
        }
    }

    /** Lueur pleine jusqu'a 12 blocs de la camera, eteinte a 32 : on la voit en face de soi la nuit, pas de tres loin. */
    private static float glowFade(LivingEntity entity) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.gameRenderer == null || !mc.gameRenderer.getMainCamera().isInitialized()) return 1.0F;
        double d = Math.sqrt(mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(entity.getEyePosition()));
        return (float) Math.max(0.0, Math.min(1.0, (32.0 - d) / 20.0));
    }

    /** Parties affichees selon le slot, comme HumanoidArmorLayer.setPartVisibility ; null = tout (combinaison, 0.9.0). */
    private static void setPartVisibility(HumanoidModel<?> model, @Nullable EquipmentSlot slot) {
        if (slot == null) {
            model.setAllVisible(true);
            return;
        }
        model.setAllVisible(false);
        switch (slot) {
            case HEAD -> {
                model.head.visible = true;
                model.hat.visible = true;
            }
            case CHEST -> {
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
            }
            case LEGS -> {
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            case FEET -> {
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
            }
            default -> {
            }
        }
    }
}
