package fr.heralisz.utils.inspecter;

import fr.heralisz.utils.HeraliszUtils;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Base CRac d'un joueur (CRac, par réflexion : {@code fr.heraliz.crac.api.BaseCracApi.base}). */
final class BaseCrac {
    private static Method base;
    private static boolean cherche;

    private BaseCrac() {
    }

    /** {monde, bloc de revendication, rayon, rayon en hauteur, nom du groupe}, ou null (sans base, ou sans CRac). */
    static Object[] base(MinecraftServer srv, UUID joueur) {
        if (!cherche) {
            cherche = true;
            try {
                base = Class.forName("fr.heraliz.crac.api.BaseCracApi").getMethod("base", MinecraftServer.class, UUID.class);
            } catch (Throwable t) {
                HeraliszUtils.LOG.warn("[inspecter] CRac 1.7.1 ou plus récent absent : pas de vue des bases ({})", t.toString());
            }
        }
        if (base == null) {
            return null;
        }
        try {
            return base.invoke(null, srv, joueur) instanceof Object[] o && o.length >= 4 ? o : null;
        } catch (Throwable t) {
            HeraliszUtils.LOG.warn("[inspecter] base illisible : {}", t.toString());
            return null;
        }
    }
}
