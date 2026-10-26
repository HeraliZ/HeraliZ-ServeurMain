package com.Spartan.Client;

import com.sun.management.HotSpotDiagnosticMXBean;
import java.lang.management.ManagementFactory;

/**
 * Vérifie, côté client, si la JVM est durcie contre l'injection à chaud.
 *
 * La vraie parade contre les injecteurs n'est pas de les détecter après coup
 * mais d'empêcher l'attache à la JVM. Le launcher officiel lance le jeu avec :
 *   -XX:+DisableAttachMechanism   (empêche tout outil externe de s'attacher)
 *   -XX:-EnableDynamicAgentLoading (Java 21+ : refuse le chargement d'un agent à chaud)
 *
 * Cette sonde signale si ces protections manquent. Best-effort : un client
 * modifié peut mentir ; c'est une incitation à passer par le launcher, pas une
 * garantie.
 */
public final class JvmProbe {
    private JvmProbe() {
    }

    /** Description des protections manquantes, ou null si la JVM est durcie. */
    public static String missingHardening() {
        HotSpotDiagnosticMXBean bean;
        try {
            bean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        } catch (Throwable t) {
            return null; // JVM non-HotSpot : on n'accuse pas
        }
        StringBuilder sb = new StringBuilder();
        if (!flagIs(bean, "DisableAttachMechanism", true)) {
            sb.append("attach activé");
        }
        // EnableDynamicAgentLoading n'existe qu'à partir de Java 21 : absent = ignoré.
        if (flagIs(bean, "EnableDynamicAgentLoading", true)) {
            sb.append(sb.length() > 0 ? ", " : "").append("agents dynamiques autorisés");
        }
        return sb.length() == 0 ? null : sb.toString();
    }

    /** true si l'option vaut la valeur attendue ; false si différente ; false aussi si absente. */
    private static boolean flagIs(HotSpotDiagnosticMXBean bean, String name, boolean expected) {
        try {
            return Boolean.parseBoolean(bean.getVMOption(name).getValue()) == expected;
        } catch (Throwable absent) {
            return !expected; // option absente : on la considère comme "non positionnée"
        }
    }
}
