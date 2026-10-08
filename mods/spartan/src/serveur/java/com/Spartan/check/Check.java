package com.Spartan.check;

import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;

/**
 * Base d'une détection. Chaque détection a un nom (affiché dans les alertes) et
 * une catégorie. Quand elle repère quelque chose, elle appelle {@link #flag}.
 */
public abstract class Check {
    private final String name;
    private final String category;

    protected Check(String name, String category) {
        this.name = name;
        this.category = category;
    }

    public String getName() {
        return name;
    }

    public String getCategory() {
        return category;
    }

    /** Signale une violation : incrémente le VL, enregistre la preuve, alerte. */
    protected void flag(ServerPlayer player, String details) {
        CheckManager.flag(this, player, details);
    }

    /** Nettoyage de l'état propre à un joueur (déconnexion). À surcharger si besoin. */
    public void clear(UUID uuid) {
    }

    /** Appelé à chaque tick serveur pour chaque joueur (détections différées). Optionnel. */
    public void tick(ServerPlayer player) {
    }

    /**
     * Appelé à chaque tick de la fenêtre de grâce (tp, respawn…) : par défaut on
     * réinitialise l'état comme à la déconnexion.
     */
    public void onGrace(UUID uuid) {
        clear(uuid);
    }
}
