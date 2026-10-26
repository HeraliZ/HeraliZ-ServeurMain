package com.Spartan.check;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Détection de combat : appelée quand un joueur attaque une entité.
 * Infalsifiable côté client (reach, angle d'attaque, cadence de clics sont
 * observés par le serveur au moment du coup).
 */
public abstract class CombatCheck extends Check {
    protected CombatCheck(String name) {
        super(name, "combat");
    }

    public abstract void onAttack(ServerPlayer attacker, Entity target);

    /**
     * Les mods de combat qui frappent plusieurs cibles par coup (style Better
     * Combat) produisent plusieurs attaques dans le même tick. Seules les
     * détections qui ont un sens par cible (ex. : portée) les analysent.
     */
    public boolean runsOnSecondaryTargets() {
        return false;
    }
}
