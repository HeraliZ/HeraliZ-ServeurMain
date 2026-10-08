package fr.heralisz.clans.territory.supply;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Envoi de particules depuis le serveur sans la limite des 32 blocs de {@code ServerLevel.sendParticles} : le signal
 * et la fumee doivent se voir de toute la zone. Paquet « overrideLimiter » aux joueurs a moins de 256 blocs.
 */
public final class Particules {
    private Particules() {
    }

    public static final double PORTEE = 256;

    /**
     * count = 0 : une particule avec la vitesse (dx, dy, dz) x vitesse ; count > 0 : count particules dispersees de
     * (dx, dy, dz) autour du point avec une vitesse aleatoire x vitesse (memes conventions que sendParticles).
     */
    public static void envoyer(ServerLevel level, ParticleOptions type, double x, double y, double z, int count,
                               double dx, double dy, double dz, double vitesse) {
        ClientboundLevelParticlesPacket pk = new ClientboundLevelParticlesPacket(type, true, x, y, z, (float) dx, (float) dy, (float) dz,
                (float) vitesse, count);
        double p2 = PORTEE * PORTEE;
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(x, y, z) < p2) p.connection.send(pk);
        }
    }
}
