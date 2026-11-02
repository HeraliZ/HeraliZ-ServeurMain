package fr.heralisz.clans.server;

import fr.heralisz.clans.HeraliszClans;
import net.minecraft.commands.CommandSourceStack;

import java.util.function.ToIntFunction;

/**
 * /recharger du noyau (02/11, audit point 36) : les relectures des clans (saison, territoires, niveaux et quêtes de clan,
 * comptes des hérals) y sont inscrites au lieu d'avoir chacune sa sous-commande « recharger ». Par réflexion
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
            HeraliszClans.LOG.warn("[clans] /recharger du noyau introuvable : « {} » ne se relit qu'au redémarrage ({})", nom, e.toString());
        }
    }
}
