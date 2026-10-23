package com.caracrea.server;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Où un joueur peut changer lui-même d'apparence (/perso, /caracrea, touche du créateur) : seulement au spawn
 * ({@code heralisz:spawn}) et jamais en combat (tag de combat de HeraliZ Matchs, lu par réflexion). Demande du propriétaire
 * (23/10) : on ne se déguise pas en pleine poursuite sur la carte. Le créateur imposé (première arrivée, /wipe, staff)
 * n'est pas concerné ; le staff (permission 2) non plus. Fil du serveur.
 */
final class EditionLibre {
    private static final ResourceLocation SPAWN = new ResourceLocation("heralisz", "spawn");
    private static Method restantMs;
    private static boolean cherche;

    private EditionLibre() {
    }

    /** Message de refus, ou null si le joueur peut ouvrir / enregistrer son créateur. */
    static Component refus(ServerPlayer player) {
        if (player.hasPermissions(2)) {
            return null;
        }
        if (enCombat(player.getUUID())) {
            return Component.translatableWithFallback("caracrea.message.combat",
                    "Pas en combat : attends la fin du combat pour changer d'apparence.");
        }
        if (!SPAWN.equals(player.level().dimension().location())) {
            return Component.translatableWithFallback("caracrea.message.spawn_only",
                    "Tu ne peux changer d'apparence qu'au spawn.");
        }
        return null;
    }

    private static boolean enCombat(UUID joueur) {
        if (!cherche) {
            cherche = true;
            try {
                restantMs = Class.forName("fr.heralisz.matchs.moteur.api.CombatTag").getMethod("restantMs", UUID.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                restantMs = null;
            }
        }
        if (restantMs == null) {
            return false;
        }
        try {
            return ((Number) restantMs.invoke(null, joueur)).longValue() > 0;
        } catch (Throwable e) {
            return false;
        }
    }
}
