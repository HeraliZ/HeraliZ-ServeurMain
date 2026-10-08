package fr.heralisz.utils;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Blocs de sol de la map HeralisZ (textures : tools/gen_map_blocks.py). Le sable du désert a le son et la dureté du
 * sable, mais ne tombe pas : le terrain reste intact quand on creuse ou qu'on construit dessus.
 */
public final class MapBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, HeraliszUtils.MODID);
    static final List<RegistryObject<Item>> ITEMS = new ArrayList<>();

    public static final RegistryObject<Block> SABLE_DESERT = sand("sable_desert");
    public static final RegistryObject<Block> SABLE_DESERT_OMBRE = sand("sable_desert_ombre");
    public static final RegistryObject<Block> SABLE_CAILLOUTEUX = sand("sable_caillouteux");
    public static final RegistryObject<Block> TERRE_CRAQUELEE = register("terre_craquelee",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.PACKED_MUD)));

    private MapBlocks() {}

    private static RegistryObject<Block> sand(String id) {
        return register(id, () -> new Block(BlockBehaviour.Properties.copy(Blocks.SAND)));
    }

    private static RegistryObject<Block> register(String id, java.util.function.Supplier<Block> block) {
        RegistryObject<Block> b = BLOCKS.register(id, block);
        ITEMS.add(HeraliszUtils.ITEMS.register(id, () -> new BlockItem(b.get(), new Item.Properties())));
        return b;
    }
}
