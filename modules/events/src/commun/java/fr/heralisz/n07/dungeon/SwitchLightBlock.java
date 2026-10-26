package fr.heralisz.n07.dungeon;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;

/** Plafonnier du labo : allumé par défaut ; le sanctuaire le coupe pendant l'alerte (combat de boss). */
public class SwitchLightBlock extends Block {
   public static final BooleanProperty LIT = BlockStateProperties.LIT;

   public SwitchLightBlock(BlockBehaviour.Properties p, int light) {
      super(p.lightLevel(s -> s.getValue(LIT) ? light : 0));
      this.registerDefaultState(this.stateDefinition.any().setValue(LIT, true));
   }

   @Override
   protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
      b.add(LIT);
   }
}
