package fr.heralisz.tags.util;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.loading.FMLEnvironment;

import java.util.function.BooleanSupplier;

/**
 * Langue courante : francais si le client est en fr_* ; cote serveur dedie, toujours francais (langue de reference).
 */
public final class Langue {
    private Langue() {
    }

    /**
     * Langue du jeu cote client (code d'origine dans {@code tags.client.LangueClient}, branche par la partie client) ;
     * sans partie client : francais.
     */
    public static volatile BooleanSupplier CLIENT = () -> true;

    public static boolean francais() {
        if (FMLEnvironment.dist != Dist.CLIENT) return true;
        return CLIENT.getAsBoolean();
    }
}
