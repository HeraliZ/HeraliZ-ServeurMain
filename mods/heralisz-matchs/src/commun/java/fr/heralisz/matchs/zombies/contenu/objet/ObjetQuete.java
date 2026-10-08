package fr.heralisz.matchs.zombies.contenu.objet;

import net.minecraft.world.item.Item;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * Objet de quete de The Giant (fusible, noyau de 115) : dessine par son modele « zombies v3 »
 * ({@code zbmodeles/<modele>.json}, valide le 05/10) via le rendu d'objet du client ({@code ObjetsRendu}). Sert d'icone au
 * HUD (fusibles portes par l'equipe) et au banc d'essai ; jamais dans l'onglet creatif ni dans les caisses.
 */
public class ObjetQuete extends Item {
    public final String modele;

    public ObjetQuete(Properties p, String modele) {
        super(p);
        this.modele = modele;
    }

    /** Rendu 3D des objets de quête : branché par la partie client (zombies/client/rendu/ObjetsRendu). */
    public static final fr.heralisz.matchs.pont.Crochet<Consumer<IClientItemExtensions>> RENDU_CLIENT = new fr.heralisz.matchs.pont.Crochet<>();

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        RENDU_CLIENT.accept(consumer);
    }
}
