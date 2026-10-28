package fr.heraliz.grades.serveur;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.lang.reflect.Method;

/**
 * Règles communes du propriétaire (Grades 2.4.0, 23/10) pour les commandes qui téléportent, donnent ou rangent des
 * objets (/warp, /kit, /trade, /boutique) : refusées en combat, à terre (Réanimation), en match, pendant le tutoriel
 * des bases (CRac) et, pour un joueur en survie ou en aventure, sur la carte (overworld). Les autres mods sont lus par
 * leurs étiquettes ou par réflexion : un mod absent ne bloque rien. Fil serveur seulement.
 */
final class Restrictions {
    private static final String CLE_COMBAT = "heralisz:combat_jusqua", DRAPEAU_MATCH = "heralisz:en_match";
    private static Method downed, etatTuto;
    private static boolean downedCherche, tutoCherche;

    private Restrictions() {
    }

    static boolean enCombat(ServerPlayer p) {
        return p.getPersistentData().getLong(CLE_COMBAT) > System.currentTimeMillis();
    }

    static boolean enMatch(ServerPlayer p) {
        return p.getPersistentData().getBoolean(DRAPEAU_MATCH) || p.level().dimension().location().getNamespace().equals("heraliz_clashwar");
    }

    static boolean surCarte(ServerPlayer p) {
        return p.level().dimension() == Level.OVERWORLD;
    }

    static boolean aTerre(ServerPlayer p) {
        if (!downedCherche) {
            downedCherche = true;
            try {
                downed = Class.forName("fr.heraliz.reanimation.core.Downed").getMethod("isDowned", net.minecraft.world.entity.Entity.class);
            } catch (Throwable ignore) {
                downed = null;
            }
        }
        try {
            return downed != null && (Boolean) downed.invoke(null, p);
        } catch (Throwable t) {
            return false;
        }
    }

    /** Tutoriel des bases de CRac en cours (kit reçu ou cabane en cours), ou joueur dans le monde du tutoriel. */
    static boolean enTutoriel(ServerPlayer p) {
        if (p.level().dimension().location().toString().equals("crac:bases") || p.level().dimension().location().toString().equals("crac:tuto")) return true;
        if (!tutoCherche) {
            tutoCherche = true;
            try {
                etatTuto = Class.forName("fr.heraliz.crac.bases.TutorielApi").getMethod("etat", ServerPlayer.class);
            } catch (Throwable ignore) {
                etatTuto = null;
            }
        }
        try {
            if (etatTuto == null) return false;
            int e = (Integer) etatTuto.invoke(null, p);
            return e == 1 || e == 2;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * Raison qui empêche ce joueur d'utiliser une commande d'objets ou de téléportation maintenant, ou null.
     * Créatif et spectateur ne sont jamais concernés par la règle « pas sur la carte ».
     */
    static Component empechement(ServerPlayer p) {
        if (enMatch(p)) return Component.translatableWithFallback("heraliz_grades.regle.match", "Impossible pendant un match.");
        if (enCombat(p)) return Component.translatableWithFallback("heraliz_grades.regle.combat", "Impossible en combat.");
        if (aTerre(p)) return Component.translatableWithFallback("heraliz_grades.regle.a_terre", "Impossible à terre.");
        if (enTutoriel(p)) return Component.translatableWithFallback("heraliz_grades.regle.tutoriel", "Impossible pendant le tutoriel des bases.");
        if (surCarte(p) && !p.isCreative() && !p.isSpectator()) {
            return Component.translatableWithFallback("heraliz_grades.regle.carte", "Impossible sur la carte : seulement au spawn.");
        }
        return null;
    }
}
