package fr.heraliz.armory.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.item.PieceItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderArmEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

/**
 * Vue a la premiere personne : la manche du haut et les gants apparaissent sur le bras du joueur (rendu translucide, verre).
 * <p>Le bras de peau est dessine ici, par le meme appel que le jeu (PlayerRenderer.renderRightHand / renderLeftHand, que
 * ItemInHandRenderer ferait juste apres cet evenement), puis les pieces reprennent la pose EXACTE avec laquelle il vient
 * d'etre dessine. Une pose recalculee a part (setupAnim appele par nous, avant le jeu) pouvait differer de celle du bras
 * dessine et la manche flottait a cote du bras : animations Player Animator du joueur (tenue et coups des armes de melee
 * de Tactique, emotes, carte d'acces) que le jeu ecarte pour le bras a la premiere personne mais pas pour notre appel,
 * vraies poses des bras (objet tenu a deux mains dans l'autre main).
 * <p>Forge lance cet evenement DANS renderRightHand / renderLeftHand : notre propre appel le relance, il passe alors sans
 * rien faire (dessinEnCours), sinon chaque appel en relance un autre jusqu'au plantage du jeu (0.12.1).
 * <p>Les armes TaCZ et de melee dessinent leurs bras elles-memes, sans cet evenement : rien n'est dessine ici pour elles.
 */
@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, value = Dist.CLIENT)
public final class FirstPersonArms {
    private FirstPersonArms() {}

    /** Vrai pendant que le bras de peau est dessine par notre appel (rendu du jeu, sur le fil de rendu seulement). */
    private static boolean dessinEnCours;

    /** En dernier : un mod qui remplace le bras (evenement annule avant nous) garde la main, rien n'est dessine par-dessus. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRenderArm(RenderArmEvent event) {
        if (dessinEnCours) return; // l'evenement relance par notre renderRightHand : le jeu dessine le bras de peau
        AbstractClientPlayer player = event.getPlayer();
        boolean right = event.getArm() == HumanoidArm.RIGHT;
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        boolean hasChest = chest.getItem() instanceof PieceItem;
        var curios = CuriosApi.getCuriosInventory(player).resolve()
                .map(inv -> inv.findCurios(st -> PieceItem.is(st, "hands") || PieceItem.is(st, "vest"))).orElse(java.util.List.of());
        if (!hasChest && curios.isEmpty()) return;
        if (!(Minecraft.getInstance().getEntityRenderDispatcher().getRenderer(player) instanceof PlayerRenderer renderer)) return;
        PoseStack stack = event.getPoseStack();
        MultiBufferSource buffers = event.getMultiBufferSource();
        int light = event.getPackedLight();
        // le bras de peau, a la place du jeu (meme appel, memes arguments) ...
        event.setCanceled(true);
        dessinEnCours = true;
        try {
            if (right) renderer.renderRightHand(stack, buffers, light, player);
            else renderer.renderLeftHand(stack, buffers, light, player);
        } finally {
            dessinEnCours = false;
        }
        // ... et la pose avec laquelle il vient d'etre dessine (PlayerRenderer.renderHand : setupAnim du jeu et des mods,
        // puis xRot = 0 ; le bras garde son leger penchement de ~6 degres)
        PlayerModel<AbstractClientPlayer> pm = renderer.getModel();
        ModelPart pose = right ? pm.rightArm : pm.leftArm;
        if (hasChest) render(((PieceItem) chest.getItem()).def(), chest, player, right, pose, stack, buffers, light);
        for (var r : curios) {
            if (r.slotContext().visible()) render(((PieceItem) r.stack().getItem()).def(), r.stack(), player, right, pose, stack, buffers, light);
        }
    }

    private static void render(PieceDef def, ItemStack item, LivingEntity player, boolean right, ModelPart pose, PoseStack stack,
                               MultiBufferSource buffers, int light) {
        ArmorModels.Variants v = ArmorModels.INSTANCE.get(def);
        if (v == null) return;
        HumanoidModel<LivingEntity> model = ArmorModels.INSTANCE.modelFor(def, player);
        ModelPart arm = right ? model.rightArm : model.leftArm;
        arm.copyFrom(pose);
        arm.visible = true;
        // modele partage par toutes les entites : aucun ajustement de ghillie (GearLayering) d'un autre rendu ne doit rester
        if (model instanceof MeshArmorModel mesh) mesh.clearFit();
        VertexConsumer vc = ItemRenderer.getArmorFoilBuffer(buffers, v.renderType(), false, item.hasFoil());
        arm.render(stack, vc, light, OverlayTexture.NO_OVERLAY);
        // surfaces libres du bras (0.9.0 : combinaisons)
        if (model instanceof MeshArmorModel mesh) mesh.renderPart(stack, vc, right ? "right_arm" : "left_arm", light, OverlayTexture.NO_OVERLAY);
    }
}
