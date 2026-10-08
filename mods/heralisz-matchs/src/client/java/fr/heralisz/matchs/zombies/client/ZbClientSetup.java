package fr.heralisz.matchs.zombies.client;

import fr.heralisz.matchs.HeraliszMatchs;
import fr.heralisz.matchs.zombies.client.hud.EffetsHud;
import fr.heralisz.matchs.zombies.client.hud.HudZombiesRendu;
import fr.heralisz.matchs.zombies.client.modele.RodeurModele;
import fr.heralisz.matchs.zombies.client.modele.ZombieLaboModele;
import fr.heralisz.matchs.zombies.client.rendu.ArmeMurRendu;
import fr.heralisz.matchs.zombies.client.rendu.AtelierRendu;
import fr.heralisz.matchs.zombies.client.rendu.EasterRendu;
import fr.heralisz.matchs.zombies.client.rendu.MateriauRendu;
import fr.heralisz.matchs.zombies.client.rendu.ObstacleRendu;
import fr.heralisz.matchs.zombies.client.rendu.OuvertureRendu;
import fr.heralisz.matchs.zombies.client.rendu.PapRendu;
import fr.heralisz.matchs.zombies.client.rendu.AtoutRendu;
import fr.heralisz.matchs.zombies.client.rendu.BoiteRendu;
import fr.heralisz.matchs.zombies.client.rendu.BonusRendu;
import fr.heralisz.matchs.zombies.client.rendu.FenetreRendu;
import fr.heralisz.matchs.zombies.client.rendu.LevierRendu;
import fr.heralisz.matchs.zombies.client.rendu.ModelesZb;
import fr.heralisz.matchs.zombies.client.rendu.RodeurRendu;
import fr.heralisz.matchs.zombies.client.rendu.ZbQuadsModele;
import fr.heralisz.matchs.zombies.client.rendu.ZombieLaboRendu;
import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.jeu.api.HudZombies;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * Branchements client du contenu zombies sur le bus du mod : modeles, rendus des creatures et des blocs-entites,
 * particules maison, HUD (couche au-dessus de tout le reste) et ecouteur des evenements du HUD.
 */
@Mod.EventBusSubscriber(modid = HeraliszMatchs.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ZbClientSetup {
    private ZbClientSetup() {
    }

    @SubscribeEvent
    public static void couches(EntityRenderersEvent.RegisterLayerDefinitions e) {
        e.registerLayerDefinition(ZombieLaboModele.COUCHE, ZombieLaboModele::couche);
        e.registerLayerDefinition(RodeurModele.COUCHE, RodeurModele::couche);
    }

    @SubscribeEvent
    public static void rendus(EntityRenderersEvent.RegisterRenderers e) {
        e.registerEntityRenderer(ZbRegistres.ZOMBIE_LABO.get(), ZombieLaboRendu::new);
        e.registerEntityRenderer(ZbRegistres.RODEUR.get(), RodeurRendu::new);
        e.registerEntityRenderer(ZbRegistres.BONUS.get(), BonusRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.FENETRE_BE.get(), FenetreRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.ARME_MUR_BE.get(), ArmeMurRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.BOITE_BE.get(), BoiteRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.ATOUT_BE.get(), AtoutRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.LEVIER_BE.get(), LevierRendu::new);
        // zombies v2 : elements d'ouverture, Pack-a-Punch, etablis, easter egg, materiaux
        e.registerBlockEntityRenderer(ZbRegistres.OBSTACLE_BE.get(), ObstacleRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.PAP_BE.get(), PapRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.ATELIER_BE.get(), AtelierRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.EASTER_BE.get(), EasterRendu::new);
        e.registerEntityRenderer(ZbRegistres.OUVERTURE.get(), OuvertureRendu::new);
        e.registerEntityRenderer(ZbRegistres.MATERIAU.get(), MateriauRendu::new);
        // zombies v3 (The Giant) : machines (courant, teleporteurs, mainframe, rideau) et objets de quete
        e.registerBlockEntityRenderer(ZbRegistres.MACHINE_BE.get(), fr.heralisz.matchs.zombies.client.rendu.MachinesRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.OBJET_QUETE_BE.get(), fr.heralisz.matchs.zombies.client.rendu.ObjetsQueteRendu::new);
        e.registerBlockEntityRenderer(ZbRegistres.DECO_ANIME_BE.get(), fr.heralisz.matchs.zombies.client.rendu.DecoAnimeRendu::new);
    }


    @SubscribeEvent
    public static void particules(RegisterParticleProvidersEvent e) {
        e.registerSpriteSet(ZbRegistres.COPEAU.get(), ZbParticules.FabCopeau::new);
        e.registerSpriteSet(ZbRegistres.CENDRE.get(), ZbParticules.FabCendre::new);
        e.registerSpriteSet(ZbRegistres.ETINCELLE.get(), ZbParticules.FabEtincelle::new);
        e.registerSpriteSet(ZbRegistres.LUEUR.get(), ZbParticules.FabLueur::new);
        e.registerSpriteSet(ZbRegistres.BRUME.get(), ZbParticules.FabBrume::new);
        e.registerSpriteSet(ZbRegistres.AME.get(), ZbParticules.FabAme::new);
        e.registerSpriteSet(ZbRegistres.ECLAIR.get(), ZbParticules.FabEclair::new);
    }

    /** Chargeur des modeles de bloc faits de quads libres (corps fixe du Pack-a-Punch, cuit dans les troncons). */
    @SubscribeEvent
    public static void chargeursModeles(ModelEvent.RegisterGeometryLoaders e) {
        e.register("zbquads", ZbQuadsModele.CHARGEUR);
    }

    /** Modeles « zombies v2 » (zbmodeles/*.json) relus a chaque rechargement des ressources. */
    @SubscribeEvent
    public static void rechargement(RegisterClientReloadListenersEvent e) {
        e.registerReloadListener(new ModelesZb.Chargeur());
    }

    @SubscribeEvent
    public static void hud(RegisterGuiOverlaysEvent e) {
        e.registerAboveAll("zombies_hud", HudZombiesRendu.INSTANCE);
    }

    @SubscribeEvent
    public static void client(FMLClientSetupEvent e) {
        e.enqueueWork(() -> HudZombies.ecouteur(EffetsHud.INSTANCE));
    }
}
