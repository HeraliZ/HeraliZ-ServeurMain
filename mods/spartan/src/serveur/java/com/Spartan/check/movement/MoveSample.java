package com.Spartan.check.movement;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Un déplacement revendiqué par le client : de la position précédente à celle
 * de ce paquet. L'environnement est évalué AUX POSITIONS REVENDIQUÉES, à la
 * demande, et mis en cache.
 */
public final class MoveSample {
    private final ServerPlayer player;
    private final Vec3 from;
    private final Vec3 to;
    private final int ticks;
    private final long nanos;
    private final boolean claimedGround;
    private final boolean groundFrom;
    private final boolean afterTeleport;
    private final boolean special;
    private final boolean impulse;
    private final double jumpVelocity;
    private final double gravity;
    private final boolean slowFalling;
    private final AABB boxFrom;
    private final AABB boxTo;

    private Boolean groundTo;
    private Boolean fluid;
    private Boolean climbable;
    private Boolean slowing;
    private Boolean platform;
    private Boolean piston;
    private Boolean hoverGear;
    private Boolean descentGear;
    private MotionTracker.Result motion;

    MoveSample(ServerPlayer player, Vec3 from, Vec3 to, int ticks, long nanos, boolean claimedGround,
               boolean groundFrom, boolean afterTeleport, boolean special, boolean impulse,
               double jumpVelocity, double gravity, boolean slowFalling) {
        this.player = player;
        this.from = from;
        this.to = to;
        this.ticks = ticks;
        this.nanos = nanos;
        this.claimedGround = claimedGround;
        this.groundFrom = groundFrom;
        this.afterTeleport = afterTeleport;
        this.special = special;
        this.impulse = impulse;
        this.jumpVelocity = jumpVelocity;
        this.gravity = gravity;
        this.slowFalling = slowFalling;
        this.boxFrom = MovementEnv.boxAt(player, from);
        this.boxTo = MovementEnv.boxAt(player, to);
    }

    public ServerPlayer player() {
        return player;
    }

    public Vec3 from() {
        return from;
    }

    public Vec3 to() {
        return to;
    }

    public double dx() {
        return to.x - from.x;
    }

    public double dy() {
        return to.y - from.y;
    }

    public double dz() {
        return to.z - from.z;
    }

    public double horizontal() {
        double dx = dx();
        double dz = dz();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Position strictement identique à la précédente. */
    public boolean still() {
        return to.equals(from);
    }

    /** Ticks de temps réel couverts par ce déplacement (1 en temps normal). */
    public int ticks() {
        return ticks;
    }

    /** Instant d'arrivée du paquet (System.nanoTime). */
    public long nanos() {
        return nanos;
    }

    /** Ce que le client affirme : « je suis au sol ». */
    public boolean claimedGround() {
        return claimedGround;
    }

    /** Sol réel (collision serveur) au départ. */
    public boolean groundFrom() {
        return groundFrom;
    }

    /** Sol réel (collision serveur) à l'arrivée. */
    public boolean groundTo() {
        if (groundTo == null) {
            groundTo = MovementEnv.groundUnder(player, boxTo);
        }
        return groundTo;
    }

    /** Premier déplacement après une téléportation confirmée (mesuré depuis sa destination). */
    public boolean afterTeleport() {
        return afterTeleport;
    }

    /**
     * Mode de déplacement spécial, en cours ou il y a moins d'un aller-retour réseau :
     * vol autorisé, spectateur, élytre, riptide, monture/véhicule, lévitation,
     * mort, sommeil. Le client applique ces états avec la latence : on la tolère.
     */
    public boolean special() {
        return special;
    }

    /**
     * Vitesse imposée par le serveur récemment (recul, explosion, propulsion de mod).
     * Les enveloppes de mouvement l'intègrent avec sa valeur exacte (voir motion()).
     */
    public boolean impulse() {
        return impulse;
    }

    /**
     * Enveloppes de mouvement de ce déplacement (vitesse et déplacement vertical
     * maximaux, dépassements), ou null si non calculées.
     */
    public MotionTracker.Result motion() {
        return motion;
    }

    void setMotion(MotionTracker.Result motion) {
        this.motion = motion;
    }

    /** Fluide au départ ou à l'arrivée (eau, lave, fluides de mods, colonnes de bulles). */
    public boolean fluid() {
        if (fluid == null) {
            fluid = MovementEnv.fluidIn(player.level(), boxFrom) || MovementEnv.fluidIn(player.level(), boxTo);
        }
        return fluid;
    }

    public boolean climbable() {
        if (climbable == null) {
            climbable = MovementEnv.climbableAt(player, from) || MovementEnv.climbableAt(player, to);
        }
        return climbable;
    }

    public boolean slowing() {
        if (slowing == null) {
            slowing = MovementEnv.slowingIn(player.level(), boxFrom) || MovementEnv.slowingIn(player.level(), boxTo);
        }
        return slowing;
    }

    public boolean platform() {
        if (platform == null) {
            platform = MovementEnv.platformUnder(player, boxFrom) || MovementEnv.platformUnder(player, boxTo);
        }
        return platform;
    }

    public boolean piston() {
        if (piston == null) {
            piston = MovementEnv.pistonNear(player.level(), boxTo);
        }
        return piston;
    }

    /** Équipement qui permet de voler / planer sur place (jetpack…, identifiants exacts). */
    public boolean hoverGear() {
        if (hoverGear == null) {
            hoverGear = FlightGear.hovers(player);
        }
        return hoverGear;
    }

    /** Équipement qui ralentit la chute (parachute, planeur : mots-clés). */
    public boolean descentGear() {
        if (descentGear == null) {
            descentGear = FlightGear.slowsFall(player);
        }
        return descentGear;
    }

    /**
     * Chute lente, en cours ou que le client peut encore appliquer : il apprend la fin d'un
     * effet avec son ping (voir MovementEngine, états tenus jusqu'à sa réponse).
     */
    public boolean slowFalling() {
        return slowFalling;
    }

    /** Gravité du joueur (attribut Forge : dimensions à faible gravité, mods), la plus faible encore possible. */
    public double gravity() {
        return gravity;
    }

    /** Vitesse verticale initiale d'un saut (Saut amélioré compris), la plus forte encore possible. */
    public double jumpVelocity() {
        return jumpVelocity;
    }

    /** Hauteur de marche du joueur (escaliers, dalles ; attribut Forge compris). */
    public double stepHeight() {
        return player.getStepHeight();
    }

    AABB boxFrom() {
        return boxFrom;
    }

    AABB boxTo() {
        return boxTo;
    }
}
