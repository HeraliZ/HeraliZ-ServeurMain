package fr.heraliz.armory.client;

import fr.heraliz.armory.HeralizArmory;
import fr.heraliz.armory.armor.ArmorCatalog;
import fr.heraliz.armory.armor.PieceDef;
import fr.heraliz.armory.item.GearCurioItem;
import fr.heraliz.armory.registry.ModEntities;
import fr.heraliz.armory.registry.ModItems;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterClientReloadListenersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;

@Mod.EventBusSubscriber(modid = HeralizArmory.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientSetup {
    private ClientSetup() {}

    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        fr.heraliz.armory.item.PieceItem.NVG_KEY.key = NvgClient.KEY::getTranslatedKeyMessage;
        event.enqueueWork(() -> {
            MenuScreens.register(ModEntities.MANNEQUIN_MENU.get(), MannequinScreen::new);
            for (PieceDef def : ArmorCatalog.PIECES) {
                if (def.curioSlot() != null) {
                    CuriosRendererRegistry.register(ModItems.get(def), () -> new CurioPieceRenderer(def));
                }
            }
            // objets repris de HeralisZ Armor (0.9.0) : rendu GeckoLib d'origine
            for (var ro : ModItems.GEAR.values()) {
                if (ro.get() instanceof GearCurioItem g) CuriosRendererRegistry.register(g, () -> new GearRenderer<GearCurioItem>(g.gear()));
            }
        });
    }

    @SubscribeEvent
    public static void onRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.MANNEQUIN.get(), MannequinRenderer::new);
    }

    /**
     * Couche translucide des pieces portees dans les slots vanilla (SetArmorLayer) : sur tous les rendus a modele humanoide
     * (joueurs, mannequin, porte-armure, mobs). Notre mod est charge apres Curios : la couche passe apres la sienne, les
     * visieres en verre se dessinent donc par-dessus une cagoule ou un masque Curios.
     */
    @SubscribeEvent
    public static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        SetArmorLayer.reset();
        for (String skin : event.getSkins()) SetArmorLayer.attach(event.getSkin(skin));
        for (EntityRenderer<?> renderer : event.getContext().getEntityRenderDispatcher().renderers.values()) SetArmorLayer.attach(renderer);
    }

    @SubscribeEvent
    public static void onLayers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MannequinModel.LAYER, MannequinModel::createBodyLayer);
    }

    @SubscribeEvent
    public static void onReloadListeners(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(ArmorModels.INSTANCE);
    }
}
