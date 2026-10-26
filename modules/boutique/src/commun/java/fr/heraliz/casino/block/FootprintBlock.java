package fr.heraliz.casino.block;

import fr.heraliz.casino.machine.Footprint;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bloc principal d'un objet sur plusieurs blocs (machine, element de decor) : les cellules autres que la sienne sont
 * des blocs "parties" invisibles ({@link MachinePartBlock}) qui lui renvoient les clics et prennent sa forme. Son
 * orientation est la propriete {@link MachineBlock#FACING}.
 */
public interface FootprintBlock {
    Footprint footprint();

    /** Encombrement dans un etat donne (porte ouverte ou fermee...) ; les cellules occupees ne changent pas. */
    default Footprint footprint(BlockState state) {
        return footprint();
    }
}
