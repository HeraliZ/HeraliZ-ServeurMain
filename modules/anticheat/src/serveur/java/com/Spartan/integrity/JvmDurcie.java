package com.Spartan.integrity;

import java.util.Locale;

/**
 * Protections anti-injection déclarées par le jeu à la connexion (sonde {@code JvmProbe} du jeu, rapport « jvm » :
 * « attach activé » et/ou « agents dynamiques autorisés »), jugées pour {@code requireHardenedJvm}. Java pur (testé hors
 * du jeu).
 *
 * <p>21/11 (audit du serveur en ligne) : seule l'attache compte. {@code -XX:+EnableDynamicAgentLoading} vaut {@code true}
 * par défaut dès Java 9, donc AUSSI en Java 17 (la sonde du jeu la croyait réservée à Java 21) ; le launcher ne coupe que
 * l'attache ({@code -XX:+DisableAttachMechanism}). Depuis {@code requireHardenedJvm = true} (2.15.0), CHAQUE joueur du
 * launcher était donc signalé « JvmHardening » à chaque connexion (alerte au staff en jeu et sur Discord, « lancez le jeu
 * via le launcher officiel »), pour rien. L'attache coupée suffit : sans elle, aucun outil ne charge d'agent dans le jeu
 * déjà lancé ; c'est aussi la seule protection que le mode strict exige ({@link ConformiteStricte}).
 */
public final class JvmDurcie {
    private JvmDurcie() {
    }

    /** Vrai si le jeu déclare l'attache ouverte (protection du launcher absente) : seul cas signalé. */
    public static boolean attacheOuverte(String detail) {
        return detail != null && detail.toLowerCase(Locale.ROOT).contains("attach");
    }
}
