package fr.heralisz.mammoth.dungeon;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * La Gueule des Ancêtres, pendue à la voûte au centre de la salle : endormie, un crâne colossal aux orbites vides ; allumée
 * (à la mort d'Ymirak), la mâchoire s'ouvre en grand, les orbites et la gorge s'emplissent d'une lueur spectrale : elle aspire
 * son âme.
 */
public class SoulMawBlock extends TwoStateBlock {
   public SoulMawBlock() {
      super(BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(-1.0F, 3600000.0F).noOcclusion().noLootTable().sound(SoundType.BONE_BLOCK)
         .lightLevel(s -> s.getValue(LIT) ? 15 : 0), Block.box(4, 8, 4, 12, 16, 12));
   }

   @Override
   public String heroId() {
      return "hero_soul_maw";
   }
}
