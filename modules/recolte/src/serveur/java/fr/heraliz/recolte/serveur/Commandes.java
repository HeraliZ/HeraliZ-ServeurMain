package fr.heraliz.recolte.serveur;

import fr.heraliz.recolte.HeralizRecolte;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.event.RegisterCommandsEvent;

import java.util.function.ToIntFunction;

/**
 * Commandes du staff de la récolte. Réglages relus par {@code /recharger recolte} (noyau).
 */
final class Commandes {
    private Commandes() {
    }

    static void enregistrer(RegisterCommandsEvent e) {
        inscrireRechargement("recolte", Commandes::recharger);
    }

    /** Inscrit le rechargement dans /recharger du noyau ({@code fr.heraliz.noyau.api.Rechargements}), par réflexion. */
    private static void inscrireRechargement(String nom, ToIntFunction<CommandSourceStack> action) {
        try {
            Class.forName("fr.heraliz.noyau.api.Rechargements").getMethod("ajouter", String.class, ToIntFunction.class).invoke(null, nom, action);
        } catch (ReflectiveOperationException | LinkageError ex) {
            HeralizRecolte.LOG.warn("[recolte] /recharger du noyau introuvable : recolte.json n'est relu que quand il change ({})", ex.toString());
        }
    }

    private static MutableComponent t(String cle, String fr, Object... args) {
        return Component.translatableWithFallback(cle, fr, args);
    }

    private static void ok(CommandSourceStack s, Component c) {
        s.sendSuccess(() -> c, false);
    }

    // ------------------------------------------------------------------ /recharger recolte
    private static int recharger(CommandSourceStack s) {
        int lu = Reglages.charger();
        if (lu < 0) {
            s.sendFailure(t("heraliz_recolte.cmd.recharger.erreur", "recolte.json illisible, réglages en cours gardés : %s", String.valueOf(Reglages.derniereErreur())));
            return 0;
        }
        Reglages r = Reglages.get();
        ok(s, lu == 0 ? t("heraliz_recolte.cmd.recharger.absent", "recolte.json absent : barème par défaut.")
                : t("heraliz_recolte.cmd.recharger.ok", "recolte.json relu : %1$s arbres, %2$s gisements de pierre, %3$s de métal et %4$s de titane à la fois.",
                r.ressource(Reglages.ARBRE).aLaFois(), r.ressource(Reglages.PIERRE).aLaFois(), r.ressource(Reglages.METAL).aLaFois(),
                r.ressource(Reglages.RARE).aLaFois()));
        return 1;
    }
}
