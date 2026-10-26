package fr.heraliz.crac.build.client.render;

import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Installs the dynamic structure model and refreshes the piece quad caches when the block atlas changes. */
@Mod.EventBusSubscriber(modid = "crac", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class StructureModelSetup {
    private StructureModelSetup() {
    }

    @SubscribeEvent
    public static void onModifyBakingResult(ModelEvent.ModifyBakingResult event) {
        StructureModel model = new StructureModel();
        for (BlockState s : ModBlocks.STRUCTURE.get().getStateDefinition().getPossibleStates()) {
            event.getModels().put(BlockModelShaper.stateToModelLocation(s), model);
        }
    }

    @SubscribeEvent
    public static void onAtlasStitched(TextureStitchEvent.Post event) {
        PieceBakery.onAtlasStitched(event.getAtlas());
    }
}
