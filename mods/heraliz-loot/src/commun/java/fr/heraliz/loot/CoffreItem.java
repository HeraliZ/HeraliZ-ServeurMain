package fr.heraliz.loot;

import java.util.function.Consumer;
import fr.heraliz.loot.pont.Crochet;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/** Objet d'un coffre T7 : dessiné par son modèle 3D en code (comme le bloc), fermé. */
public class CoffreItem extends BlockItem {
    public CoffreItem(Block bloc, Properties p) {
        super(bloc, p);
    }

    /** Rendu 3D du coffre dans l'inventaire : branche par la partie client (Forge l'appelle a la creation de l'objet). */
    public static final Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }
}
