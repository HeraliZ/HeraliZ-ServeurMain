package fr.heraliz.interfaces.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heraliz.interfaces.HeralizInterface;
import fr.heraliz.interfaces.mixin.MainEnMainInvoker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Main nue en 1re personne 15 % plus petite (choix du propriétaire, 24/10) : seulement quand la main ne tient rien. Les armes
 * (TaCZ dessine les siennes) et les objets tenus ne changent pas. La main est réduite autour de sa place habituelle
 * (épaule à droite ou à gauche de l'écran), pas vers le centre. Si le rendu échoue une fois, on rend la main à Minecraft.
 */
@Mod.EventBusSubscriber(modid = HeralizInterface.MODID, value = Dist.CLIENT)
public final class MainNue {
    private MainNue() {
    }

    static final float ECHELLE = 0.85f;
    private static boolean enPanne;

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void main(RenderHandEvent e) {
        if (enPanne || e.isCanceled() || !e.getItemStack().isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null || p.isInvisible()) return;
        HumanoidArm bras = e.getHand() == InteractionHand.MAIN_HAND ? p.getMainArm() : p.getMainArm().getOpposite();
        float cote = bras == HumanoidArm.RIGHT ? 1f : -1f;
        PoseStack pose = e.getPoseStack();
        pose.pushPose();
        try {
            // même point d'ancrage que le bras de Minecraft (ItemInHandRenderer.renderPlayerArm)
            pose.translate(cote * 0.64f, -0.6f, -0.72f);
            pose.scale(ECHELLE, ECHELLE, ECHELLE);
            pose.translate(-cote * 0.64f, 0.6f, 0.72f);
            ((MainEnMainInvoker) mc.gameRenderer.itemInHandRenderer).heraliz$renderArmWithItem(p, e.getPartialTick(), e.getInterpolatedPitch(),
                    e.getHand(), e.getSwingProgress(), e.getItemStack(), e.getEquipProgress(), pose, e.getMultiBufferSource(), e.getPackedLight());
            e.setCanceled(true);
        } catch (Throwable t) {
            enPanne = true;
            HeralizInterface.LOG.warn("[interface] main nue réduite impossible : main de Minecraft gardée", t);
        } finally {
            pose.popPose();
        }
    }
}
