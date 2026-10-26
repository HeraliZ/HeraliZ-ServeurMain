package fr.heraliz.quetes.serveur;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Objets d'événement à revendre (module heraliz_evenements, 25/10) : leur prix, leurs points de saison et leur vente
 * passent par {@code fr.heraliz.evenements.api.EvenementsApi}, par réflexion (sans ce module : ce ne sont que des objets
 * inconnus, jamais rachetés). Un objet d'événement n'est jamais racheté par les règles de rareté du marchand.
 */
final class ObjetsEvenements {
    private ObjetsEvenements() {
    }

    private static final Logger LOG = LogUtils.getLogger();

    @Nullable
    private static Method prix, points, raison, vendre;
    private static boolean cherche;

    private static boolean pret() {
        if (!cherche) {
            cherche = true;
            try {
                Class<?> api = Class.forName("fr.heraliz.evenements.api.EvenementsApi");
                prix = api.getMethod("prixObjet", ItemStack.class);
                points = api.getMethod("pointsObjet", ItemStack.class);
                raison = api.getMethod("raisonSansPoints", ServerPlayer.class);
                vendre = api.getMethod("vendreObjet", ServerPlayer.class, ItemStack.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                prix = null;
            }
        }
        return prix != null;
    }

    /** Prix de rachat : -1 si ce n'est pas un objet d'événement, 0 s'il ne se vend pas (copie, déjà vendu). */
    static long prix(ItemStack s) {
        if (s.isEmpty() || !pret()) return -1;
        try {
            return (long) prix.invoke(null, s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return -1;
        }
    }

    /** Points de saison du barème pour cet objet (avant plafond du jour). */
    static int points(ItemStack s) {
        if (!pret()) return 0;
        try {
            return (int) points.invoke(null, s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 0;
        }
    }

    /** 0 : le clan du joueur gagne des points ; 1 pas de saison, 2 sans clan, 3 clan sans cœur. */
    static int raisonSansPoints(ServerPlayer p) {
        if (!pret()) return 1;
        try {
            return (int) raison.invoke(null, p);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return 1;
        }
    }

    /** Vente : {hérals à verser, points donnés}, ou null s'il ne se vend pas (rien n'est fait). */
    @Nullable
    static long[] vendre(ServerPlayer p, ItemStack s) {
        if (!pret()) return null;
        try {
            return (long[]) vendre.invoke(null, p, s);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOG.warn("[heraliz_quetes] objet d'événement non vendu par {} : {}", p.getGameProfile().getName(), e.toString());
            return null;
        }
    }
}
