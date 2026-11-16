package fr.heraliz.recolte.serveur;

import fr.heraliz.recolte.HeralizRecolte;
import net.minecraft.server.level.ServerPlayer;

import javax.annotation.Nullable;
import java.lang.reflect.Method;

/**
 * Points de saison des clans (règle du serveur, docs/EVENEMENTS.md) : {@code fr.heralisz.clans.saison.SaisonApi.pve} de
 * HeraliZ Clans, par réflexion (sans le mod des clans, rien ne casse ; aucun point). On n'envoie qu'une source :
 * « recolte_metal » à la fin d'un gisement de métal, « recolte_rare » à la fin d'un gisement de titane, au joueur qui a
 * porté le dernier coup. Le barème (1 et 2 points), le plafond par clan et par jour et les refus (sans clan, sans cœur,
 * hors saison, créatif ou spectateur) sont décidés chez les clans ({@code saison.json}, section {@code pve}). Rien pour
 * le bois et la pierre.
 */
public final class Saison {
    private Saison() {
    }

    private static boolean cherche;
    @Nullable
    private static Method pve;

    /** Un point de saison PVE de la source {@code source} pour le clan du joueur (si le barème des clans le donne). */
    public static void pve(ServerPlayer joueur, String source) {
        if (!cherche) {
            cherche = true;
            try {
                pve = Class.forName("fr.heralisz.clans.saison.SaisonApi").getMethod("pve", ServerPlayer.class, String.class, double.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                pve = null;
                HeralizRecolte.LOG.info("[recolte] saison des clans absente : aucun point de saison pour la recolte ({})", e.toString());
            }
        }
        if (pve == null) {
            return;
        }
        try {
            pve.invoke(null, joueur, source, 1.0);
        } catch (ReflectiveOperationException | RuntimeException e) {
            HeralizRecolte.LOG.warn("[recolte] points de saison non donnes a {} ({}) : {}", joueur.getGameProfile().getName(), source, e.toString());
        }
    }
}
