package fr.heraliz.airstrike.registry;

import fr.heraliz.airstrike.AirstrikeMod;
import fr.heraliz.airstrike.block.SupplyCrateBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AirstrikeMod.MODID);

    public static final RegistryObject<SupplyCrateBlock> SUPPLY_CRATE = BLOCKS.register("supply_crate",
            () -> new SupplyCrateBlock(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GREEN)
                    .strength(2.5f, 6.0f).sound(SoundType.WOOD).noOcclusion()));

    private ModBlocks() {
    }
}
