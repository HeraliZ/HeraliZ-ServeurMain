package com.Spartan.check;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Détection sur le minage : appelée quand un joueur casse un bloc.
 * Infalsifiable côté client (cadence et nombre de blocs cassés sont vus par le serveur).
 */
public abstract class BlockCheck extends Check {
    protected BlockCheck(String name) {
        super(name, "block");
    }

    public abstract void onBreak(ServerPlayer player, BlockState state, BlockPos pos, Level level);

    /** Le joueur commence à casser un bloc (clic gauche). Optionnel. */
    public void onStartBreaking(ServerPlayer player, BlockPos pos, Level level) {
    }
}
