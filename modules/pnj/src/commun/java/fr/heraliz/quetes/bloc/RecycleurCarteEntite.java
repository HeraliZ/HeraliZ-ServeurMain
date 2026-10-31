package fr.heraliz.quetes.bloc;

import fr.heraliz.quetes.HeralizQuetes;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Entité de bloc du recycleur de la carte (31/10), partie commune : rien à afficher ni à synchroniser (le modèle suit
 * l'état {@link RecycleurCarte#MARCHE} du bloc). Le contenu partagé, le recyclage et leur sauvegarde HORS DU MONDE sont
 * dans la version serveur ({@code serveur/RecycleurCarteEntiteServeur}), créée par l'usine
 * {@link HeralizQuetes#RECYCLEUR_CARTE_USINE} (jamais par {@code new}). Rien n'est gardé dans le tronçon : un plantage
 * ne peut pas y laisser une copie du contenu.
 */
public class RecycleurCarteEntite extends BlockEntity {
    public RecycleurCarteEntite(BlockPos pos, BlockState s) {
        super(HeralizQuetes.RECYCLEUR_CARTE_ENTITE.get(), pos, s);
    }

    /** Un tick de recyclage (serveur, machine en marche seulement) : la version serveur le fait. */
    public void tickServeur() {
    }

    /** Le bloc part : la version serveur pose le contenu au sol et efface son fichier. */
    public void retire() {
    }
}
