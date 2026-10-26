package fr.heraliz.airstrike.block;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Partie SERVEUR de {@link SupplyCrateBlockEntity} (docs/METHODE_CLIENT_SERVEUR.md §5.2) : minuteries de fumée et de
 * disparition, préparation au largage. Créée par l'usine (ModBlockEntities.SUPPLY_CRATE_USINE), jamais par « new ».
 */
public class SupplyCrateBlockEntityServeur extends SupplyCrateBlockEntity {
    public SupplyCrateBlockEntityServeur(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /** Called when the crate lands. */
    public void initDrop(String lootTable, long seed, int smokeTicks, int despawnTicks) {
        ResourceLocation rl = ResourceLocation.tryParse(lootTable);
        if (rl != null) setLootTable(rl, seed);
        this.smokeTicks = smokeTicks;
        this.despawnTicks = despawnTicks;
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    @Override
    void tickServeur(Level level, BlockPos pos) {
        SupplyCrateBlockEntity be = this;
        if (be.smokeTicks > 0) be.smokeTicks--;
        if (be.despawnTicks > 0) {
            be.despawnTicks--;
            if (be.despawnTicks == 0) {
                be.despawning = true;
                be.clearContent();
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
            }
        }
    }
}
