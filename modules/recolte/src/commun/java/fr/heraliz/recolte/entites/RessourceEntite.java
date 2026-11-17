package fr.heraliz.recolte.entites;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;

/**
 * Une ressource de la carte (gisement ou arbre à abattre) : une entité immobile, sans IA ni gravité, qu'on ne traverse pas
 * ({@link #canBeCollidedWith}) et qu'on frappe ({@link #isPickable}). Rien ne l'abîme (ni dégâts, ni explosion, ni feu,
 * ni piston) : seuls les coups d'un outil comptent, jugés par le serveur ({@link #skipAttackInteraction}, remplacée par la
 * version serveur). Enregistrée avec le monde. Créée par son type ({@code TYPE.get().create(level)}), jamais par
 * {@code new} : le monde serveur reçoit alors la version serveur (logique), le joueur la version commune (affichage).
 */
public abstract class RessourceEntite extends Entity {
    protected RessourceEntite(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.blocksBuilding = true;
        this.setNoGravity(true);
    }

    /** Immobile : rien à faire à chaque tick (la version serveur d'un arbre suit sa chute). */
    @Override
    public void tick() {
        this.firstTick = false;
    }

    @Override
    public boolean isPickable() {
        return !this.isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return true;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean ignoreExplosion() {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float degats) {
        return false;
    }

    /**
     * Un coup d'un joueur : jamais de dégâts ni de recul. Vrai = Minecraft s'arrête là ; la version serveur juge le coup
     * (outil, cadence, portée) avant de rendre vrai elle aussi.
     */
    @Override
    public boolean skipAttackInteraction(Entity attaquant) {
        return true;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected Entity.MovementEmission getMovementEmission() {
        return Entity.MovementEmission.NONE;
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    /** Valeur bornée d'une donnée synchronisée (une valeur abîmée ne casse jamais le rendu). */
    protected static int borne(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }
}
