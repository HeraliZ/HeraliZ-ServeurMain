package fr.heraliz.crac.registry;

import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.ClaimBlockHaut;
import fr.heraliz.crac.block.MetalCrateBlock;
import fr.heraliz.crac.build.world.StructureBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, "crac");
    public static final RegistryObject<StructureBlock> STRUCTURE = BLOCKS.register("structure", StructureBlock::new);
    public static final RegistryObject<MetalCrateBlock> METAL_CRATE = BLOCKS.register("metal_crate", MetalCrateBlock::new);
    public static final RegistryObject<ClaimBlock> CLAIM_BLOCK = BLOCKS.register(
        "claim_block", () -> new ClaimBlock(Properties.of().mapColor(MapColor.COLOR_GREEN).strength(4.0F, 1200.0F).noOcclusion().sound(SoundType.METAL))
    );
    /** Case du haut de l'armoire (1.10.0) : invisible, incassable seule, sans objet ni butin. */
    public static final RegistryObject<ClaimBlockHaut> CLAIM_BLOCK_HAUT = BLOCKS.register(
        "claim_block_haut", () -> new ClaimBlockHaut(Properties.of().mapColor(MapColor.COLOR_GREEN).strength(-1.0F, 1200.0F).noOcclusion()
            .noLootTable().sound(SoundType.METAL))
    );

    private ModBlocks() {
    }
}
