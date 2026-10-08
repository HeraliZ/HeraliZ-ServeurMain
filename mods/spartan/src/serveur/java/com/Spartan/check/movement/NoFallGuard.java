package com.Spartan.check.movement;

import com.Spartan.config.SpartanConfig;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Correction de NoFall (appelée par vanilla via Mixin, thread serveur) : une
 * déclaration « au sol » n'est acceptée que s'il y a vraiment un appui sous le
 * joueur (bloc ou entité solide à moins de 0,2 bloc), là où le serveur vient de le
 * placer. Sinon vanilla la traite comme « en l'air » pour les dégâts de chute.
 */
public final class NoFallGuard {
    private NoFallGuard() {
    }

    public static boolean groundClaimAccepted(ServerPlayer p) {
        if (!SpartanConfig.ENABLED.get() || !SpartanConfig.NOFALL_CORRECT.get() || p instanceof FakePlayer
                || p.isSpectator() || p.getAbilities().flying || p.isPassenger() || p.isFallFlying()
                || p.isInWater() || p.isInLava() || p.onClimbable() || !MovementEnv.loaded(p, p.position())) {
            return true;
        }
        return MovementEnv.groundUnder(p, p.getBoundingBox()) || MovementEnv.platformUnder(p, p.getBoundingBox());
    }
}
