package fr.heralisz.utils.cine;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/** Zombie de la cinématique (balafré, couvert de sang) : créé seulement côté client, jamais sur le serveur. */
public class ZombieCinematique extends Zombie {
    public ZombieCinematique(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
    }

    @Override
    protected boolean isSunSensitive() {
        return false;
    }
}
