package fr.heralisz.matchs.duels.killcam.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.heralisz.matchs.HeraliszMatchs;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.lang.reflect.Method;

/**
 * Arme du tueur en premiere personne pendant la killcam (vue par ses yeux, comme il voyait).
 * <p>Arme de TaCZ : son propre rendu en premiere personne ({@code AnimateGeoItemRenderer.renderFirstPerson}, appele par
 * reflexion : TaCZ n'est pas une dependance de compilation de Matchs), comme le fait TaCZ pour le joueur. Le
 * balancement et la visee sont ceux du joueur qui regarde (il ne bouge pas : arme au repos). Autre objet : rendu de
 * Minecraft dans la main droite. Une erreur du rendu de TaCZ le coupe jusqu'au redemarrage du jeu (l'objet est alors
 * dessine comme un objet ordinaire).
 */
final class ArmeEnMain {
    private static final String RENDU_TACZ = "com.tacz.guns.client.renderer.item.AnimateGeoItemRenderer";

    private static boolean taczCherche, taczEnPanne;
    private static Class<?> classeTacz;
    private static Method preparer, premierePersonne;

    private ArmeEnMain() {
    }

    static void dessiner(RenderHandEvent e, ItemStack objet) {
        if (objet == null || objet.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer joueur = mc.player;
        if (joueur == null) return;
        PoseStack pose = e.getPoseStack();
        MultiBufferSource tampons = e.getMultiBufferSource();
        int lumiere = e.getPackedLight();
        float partiel = e.getPartialTick();
        BlockEntityWithoutLevelRenderer rendu = IClientItemExtensions.of(objet).getCustomRenderer();
        if (rendu != null && estTacz(rendu)) {
            pose.pushPose();
            try {
                preparer.invoke(rendu, objet, joueur, partiel);
                premierePersonne.invoke(rendu, joueur, objet, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, pose, tampons, lumiere, partiel);
                return;
            } catch (ReflectiveOperationException | RuntimeException | LinkageError ex) {
                taczEnPanne = true;
                HeraliszMatchs.LOG.warn("[killcam] arme de TaCZ en premiere personne impossible, rendu ordinaire jusqu'au redemarrage", ex);
            } finally {
                pose.popPose();
            }
        }
        // objet ordinaire : place comme Minecraft tient un objet dans la main droite (sans balancement)
        pose.pushPose();
        try {
            pose.translate(0.56F, -0.52F, -0.72F);
            mc.getEntityRenderDispatcher().getItemInHandRenderer()
                    .renderItem(joueur, objet, ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, false, pose, tampons, lumiere);
        } catch (RuntimeException ex) {
            HeraliszMatchs.LOG.debug("[killcam] objet en main non dessine", ex);
        } finally {
            pose.popPose();
        }
    }

    /** Vrai si ce rendu est celui des armes de TaCZ et que ses methodes sont trouvees. */
    private static boolean estTacz(BlockEntityWithoutLevelRenderer rendu) {
        if (taczEnPanne) return false;
        if (!taczCherche) {
            taczCherche = true;
            try {
                classeTacz = Class.forName(RENDU_TACZ, false, rendu.getClass().getClassLoader());
                preparer = classeTacz.getMethod("tryInit", ItemStack.class, Player.class, float.class);
                premierePersonne = classeTacz.getMethod("renderFirstPerson", LocalPlayer.class, ItemStack.class,
                        ItemDisplayContext.class, PoseStack.class, MultiBufferSource.class, int.class, float.class);
            } catch (ReflectiveOperationException | LinkageError ex) {
                taczEnPanne = true;
                HeraliszMatchs.LOG.info("[killcam] rendu des armes de TaCZ introuvable : armes dessinees comme des objets ordinaires");
                return false;
            }
        }
        return classeTacz.isInstance(rendu);
    }
}
