package fr.heraliz.crac.client;

import fr.heraliz.crac.build.client.render.PlanHandRenderer;
import fr.heraliz.crac.build.net.ReceptionClientBuild;
import fr.heraliz.crac.build.net.S2CPieceInfo;
import fr.heraliz.crac.client.render.C4ItemRenderer;
import fr.heraliz.crac.client.render.TenueExplosifs;
import fr.heraliz.crac.client.render.ExplosiveChargeItemRenderer;
import fr.heraliz.crac.item.BuildingPlanItem;
import fr.heraliz.crac.item.C4Item;
import fr.heraliz.crac.item.ExplosiveChargeItem;
import fr.heraliz.crac.network.*;
import fr.heraliz.crac.pont.Partie;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Partie client de CRac (docs/METHODE_CLIENT_SERVEUR.md §4) : aperçus de construction, rendus, écrans, HUD, réception
 * des paquets serveur -> client. Les autres écouteurs client s'inscrivent seuls (@EventBusSubscriber value = CLIENT).
 */
public final class PartieClient implements Partie {
    @Override
    public void init(IEventBus busMod) {
        busMod.addListener((FMLClientSetupEvent event) -> event.enqueueWork(ClientInit::init));
        BuildingPlanItem.RENDU_CLIENT.brancher(c -> c.accept(PLAN_EXT));
        C4Item.RENDU_CLIENT.brancher(c -> c.accept(C4_EXT));
        ExplosiveChargeItem.RENDU_CLIENT.brancher(c -> c.accept(CHARGE_EXT));
        NetworkHandler.RECEPTION_CLIENT.brancher(PartieClient::recevoir);
    }

    /** Une instance par objet, comme avant (Forge appelle initializeClient une fois par objet). */
    private static final IClientItemExtensions PLAN_EXT = new IClientItemExtensions() {
                @Override
                public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                    return PlanHandRenderer.ARM_POSE;
                }
            };

    private static final IClientItemExtensions C4_EXT = new IClientItemExtensions() {
                private C4ItemRenderer renderer;

                @Override
                public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                    return TenueExplosifs.pose(entity, hand);
                }

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (this.renderer == null) {
                        this.renderer = new C4ItemRenderer();
                    }
    
                    return this.renderer;
                }
            };

    private static final IClientItemExtensions CHARGE_EXT = new IClientItemExtensions() {
                private ExplosiveChargeItemRenderer renderer;

                @Override
                public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                    return TenueExplosifs.pose(entity, hand);
                }

                @Override
                public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                    if (this.renderer == null) {
                        this.renderer = new ExplosiveChargeItemRenderer();
                    }
    
                    return this.renderer;
                }
            };

    private static void recevoir(Object paquet) {
        if (paquet instanceof S2CRaidBlock p) {
            ReceptionClient.s2CRaidBlock(p);
        } else if (paquet instanceof S2CNearbyClaims p) {
            ReceptionClient.s2CNearbyClaims(p);
        } else if (paquet instanceof S2CAdminData p) {
            ReceptionClient.s2CAdminData(p);
        } else if (paquet instanceof S2CConfigResync p) {
            ReceptionClient.s2CConfigResync(p);
        } else if (paquet instanceof S2COnlinePlayers p) {
            ReceptionClient.s2COnlinePlayers(p);
        } else if (paquet instanceof S2COpenGroupCreate p) {
            ReceptionClient.s2COpenGroupCreate(p);
        } else if (paquet instanceof S2COpenGroupManage p) {
            ReceptionClient.s2COpenGroupManage(p);
        } else if (paquet instanceof S2COpenInvites p) {
            ReceptionClient.s2COpenInvites(p);
        } else if (paquet instanceof S2CPieceInfo p) {
            ReceptionClientBuild.s2CPieceInfo(p);
        } else if (paquet instanceof S2CProteges p) {
            // monde des bases (1.5.0) : joueurs entourés de vert
            fr.heraliz.crac.bases.ContourProtege.proteges(p.joueurs());
        } else if (paquet instanceof S2CTuto p) {
            // tutoriel des bases (1.8.0) : pièces à poser, objets et choix à montrer
            fr.heraliz.crac.bases.TutoClient.recevoir(p);
        } else if (paquet instanceof S2CTutoFin p) {
            fr.heraliz.crac.bases.TutoClient.fin(p);
        } else if (paquet instanceof fr.heraliz.crac.network.S2CAccesBase p) {
            // armoire à outils (1.10.0) : onglets Accès et Base
            fr.heraliz.crac.client.ui.EcranAccesBase.recevoir(p);
        } else if (paquet instanceof fr.heraliz.crac.network.S2CFicheBase p) {
            // fiche technique des bases, /base info (1.13.0)
            net.minecraft.client.Minecraft.getInstance().setScreen(new fr.heraliz.crac.client.ui.EcranFicheBase(p));
        }
    }
}
