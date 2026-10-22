package fr.heraliz.crac.client;

import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.client.admin.AdminKeyHandler;
import fr.heraliz.crac.client.render.C4Renderer;
import fr.heraliz.crac.client.render.CoeurClanRendu;
import fr.heraliz.crac.client.render.EcranArmoireRendu;
import fr.heraliz.crac.client.render.ExplosiveChargeRenderer;
import fr.heraliz.crac.client.ui.ClaimUpkeepScreen;
import fr.heraliz.crac.registry.ModBlockEntities;
import fr.heraliz.crac.registry.ModBlocks;
import fr.heraliz.crac.registry.ModEntities;
import fr.heraliz.crac.registry.ModMenus;
import fr.heraliz.crac.registry.ModParticles;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.event.EntityRenderersEvent.RegisterRenderers;
import net.minecraftforge.client.event.RegisterColorHandlersEvent.Block;
import net.minecraftforge.client.event.RegisterColorHandlersEvent.Item;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber.Bus;

@OnlyIn(Dist.CLIENT)
@EventBusSubscriber(
    modid = "crac",
    value = {Dist.CLIENT},
    bus = Bus.MOD
)
public class ClientInit {
    public static void init() {
        // PlacableGhostRenderer subscribes itself (@EventBusSubscriber)
        MinecraftForge.EVENT_BUS.register(RaidBlockOverlay.class);
        MinecraftForge.EVENT_BUS.register(AdminKeyHandler.class);
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.METAL_CRATE.get(), RenderType.cutoutMipped());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.CLAIM_BLOCK.get(), RenderType.cutout());
        ItemBlockRenderTypes.setRenderLayer(ModBlocks.COEUR_CLAN.get(), RenderType.cutout());
        MenuScreens.register(ModMenus.CLAIM_UPKEEP.get(), ClaimUpkeepScreen::new);
    }

    @SubscribeEvent
    public static void onRegisterBlockColors(Block event) {
        event.register(
            (state, level, pos, tintIndex) -> tintIndex == 0 && level != null && pos != null && level.getBlockEntity(pos) instanceof ClaimBlockEntity cbe
                ? cbe.getColor()
                : 16777215,
            ModBlocks.CLAIM_BLOCK.get()
        );
    }

    @SubscribeEvent
    public static void onRegisterItemColors(Item event) {
        event.register((stack, tintIndex) -> 16777215, ModBlocks.CLAIM_BLOCK.get());
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(AdminKeyHandler.KEY_CONSOLE);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SHEET_METAL_SPARK.get(), SparkParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterRenderers(RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.C4.get(), C4Renderer::new);
        event.registerEntityRenderer(ModEntities.EXPLOSIVE_CHARGE.get(), ExplosiveChargeRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.CLAIM_BLOCK.get(), EcranArmoireRendu::new);
        event.registerBlockEntityRenderer(ModBlockEntities.COEUR_CLAN.get(), CoeurClanRendu::new);
    }

    /** Parties du cœur de clan dessinées à part (filets lumineux, cristal qui tourne). */
    @SubscribeEvent
    public static void onRegisterModels(ModelEvent.RegisterAdditional event) {
        event.register(CoeurClanRendu.LUEUR);
        event.register(CoeurClanRendu.CRISTAL);
    }
}
