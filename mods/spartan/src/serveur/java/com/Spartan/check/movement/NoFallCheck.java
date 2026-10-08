package com.Spartan.check.movement;

import com.Spartan.check.MovementCheck;
import com.Spartan.config.SpartanConfig;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.level.ServerPlayer;

/**
 * NoFall : le client se déclare « au sol » alors qu'il tombe en plein air (le
 * serveur calcule les dégâts de chute d'après cette déclaration). Signalé après
 * trois positions de suite en chute avec sol déclaré mais aucun sol réel.
 */
public class NoFallCheck extends MovementCheck {
    private static final int STREAK = 3;
    private static final double FALLING = -0.08;

    private final Map<UUID, Integer> streaks = new ConcurrentHashMap<>();

    public NoFallCheck() {
        super("NoFall");
    }

    @Override
    public void onSample(ServerPlayer p, MoveSample s) {
        if (!SpartanConfig.CHECK_NOFALL.get()) {
            return;
        }
        if (exempt(s) || s.groundTo() || !s.claimedGround() || s.dy() > FALLING) {
            streaks.remove(p.getUUID());
            return;
        }
        int n = streaks.merge(p.getUUID(), 1, Integer::sum);
        if (n >= STREAK) {
            flag(p, String.format("se déclare au sol en pleine chute (%d positions, dy=%.2f)", n, s.dy()));
            streaks.remove(p.getUUID());
        }
    }

    private static boolean exempt(MoveSample s) {
        return s.special() || s.fluid() || s.climbable() || s.slowing() || s.platform() || s.piston()
                || s.hoverGear() || s.afterTeleport();
    }

    @Override
    public void clear(UUID uuid) {
        streaks.remove(uuid);
    }
}
