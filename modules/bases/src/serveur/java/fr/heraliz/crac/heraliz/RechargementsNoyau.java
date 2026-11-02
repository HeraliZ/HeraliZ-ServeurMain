package fr.heraliz.crac.heraliz;

import fr.heraliz.crac.Crac;
import net.minecraft.commands.CommandSourceStack;

import java.util.function.ToIntFunction;

/**
 * /recharger du noyau (02/11, audit point 36) : les relectures de CRac (créneaux de la Purge, limites des coffres de base)
 * y sont inscrites au lieu d'avoir chacune sa sous-commande « recharger ». Par réflexion
 * ({@code fr.heraliz.noyau.api.Rechargements}) : aucune dépendance de compilation. Sans le noyau, ces fichiers ne se
 * relisent qu'au redémarrage (dit dans le journal).
 */
public final class RechargementsNoyau {
    private RechargementsNoyau() {
    }

    public static void inscrire(String nom, ToIntFunction<CommandSourceStack> action) {
        try {
            Class.forName("fr.heraliz.noyau.api.Rechargements").getMethod("ajouter", String.class, ToIntFunction.class)
                    .invoke(null, nom, action);
        } catch (ReflectiveOperationException | LinkageError e) {
            Crac.LOGGER.warn("[CRac] /recharger du noyau introuvable : « {} » ne se relit qu'au redémarrage ({})", nom, e.toString());
        }
    }
}
