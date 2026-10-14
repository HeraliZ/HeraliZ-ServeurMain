package fr.heralisz.utils.train;

import fr.heralisz.utils.pont.Crochet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Place d'un voyageur dans le train : entité invisible qui porte le joueur (debout) à sa position dans une voiture,
 * donnée en coordonnées de la voiture (lx le long, lz en travers, au niveau du plancher). Le serveur et chaque client
 * en déduisent la position dans le monde à partir de l'horaire (le train n'est pas une entité) : tout le monde voit le
 * voyageur exactement dans la voiture qu'il dessine. Le joueur qui marche calcule lui-même sa position (sans attendre
 * le serveur) et l'envoie ; le serveur la vérifie (Voyageurs). Debout, il peut sauter (Espace) : la hauteur du saut
 * au-dessus du plancher est prédite de même par son client, vérifiée par le serveur et montrée aux autres (HAUTEUR).
 * Il peut aussi s'allonger sur le plancher (touche « Ramper » de TaCZ, ALLONGE : pose à plat, Train.poseAllonge).
 * Chez les autres joueurs, sa position dans la voiture est lissée sur 3 ticks comme celle de toute entité (rx, rz, rh).
 * Jamais sauvegardée.
 */
public class Voyageur extends Entity {
    static final EntityDataAccessor<Integer> VEHICULE = SynchedEntityData.defineId(Voyageur.class, EntityDataSerializers.INT);
    static final EntityDataAccessor<Float> LX = SynchedEntityData.defineId(Voyageur.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Float> LZ = SynchedEntityData.defineId(Voyageur.class, EntityDataSerializers.FLOAT);
    static final EntityDataAccessor<Boolean> ASSIS = SynchedEntityData.defineId(Voyageur.class, EntityDataSerializers.BOOLEAN);
    /** Hauteur du saut au-dessus du plancher (0 : pieds au plancher), vue par le serveur et les autres joueurs. */
    static final EntityDataAccessor<Float> HAUTEUR = SynchedEntityData.defineId(Voyageur.class, EntityDataSerializers.FLOAT);
    /** Le joueur porté est allongé sur le plancher (vérifié par le serveur, vu par les autres). */
    static final EntityDataAccessor<Boolean> ALLONGE = SynchedEntityData.defineId(Voyageur.class, EntityDataSerializers.BOOLEAN);
    /** Le joueur porté est assis (prédiction de son propre client). */
    boolean passis;
    boolean accroupiPrec;
    /** Allongé (prédiction de son propre client), touche « Ramper » au tick précédent. */
    boolean pallonge;
    boolean ramperPrec;
    /** Vitesse de marche prédite dans la voiture (blocs par tick, coordonnées de la voiture), comme celle de Minecraft :
     *  accélération, frottement du sol (0,546 par tick) ou de l'air (0,91). */
    float vx, vz;
    /** Course (sprint) du joueur porté, prédite par son client et envoyée au serveur (animations de course chez les autres). */
    boolean pcourse;
    /** Chez les autres joueurs : position affichée dans la voiture (voiture, lx, lz, hauteur), lissée vers la position
     *  reçue du serveur en 3 ticks (comme Minecraft pour toute entité) ; rv = -1 : rien d'affiché encore. */
    int rv = -1;
    float rx, rz, rh;
    float cx, cz, ch;
    int rPas;
    boolean rAssis, rAllonge;
    /** Côté client, bruits de pas : distance de marche (comme Entity.moveDist), prochain pas, en l'air au tick précédent. */
    float distPas;
    int prochainPas = 1;
    boolean enLair;
    /** Côté client, pour orienter le corps dans le sens de la marche : pas du tick (coordonnées de la voiture) et cap de
     *  la voiture (radians). */
    float dirX, dirZ;
    double capAnim;

    /** Position locale prédite par le client du joueur porté (sinon celle du serveur). */
    float px, pz;
    int pv = -1;
    double capPrec = Double.NaN;
    /** Saut prédit par le client du joueur porté : hauteur au-dessus du plancher, vitesse verticale (blocs par tick) ;
     *  Espace à relâcher d'abord (il vient de servir à se lever de la banquette). */
    float ph, pvy;
    boolean sautBloque;
    /** Côté client, pour l'animation de la marche (jambes, balancement de la vue) : pas fait dans la voiture depuis le
     *  tick précédent (blocs), dernière position locale vue, balancement de la vue du tick précédent. */
    float pas, animX, animZ, bobPrec;
    int animVeh = -1;
    /** Côté serveur : tick du décollage du dernier saut, retombé au plancher depuis (VoyageursServeur.sautVerifie). */
    int debutSaut = -100;
    boolean sautSol = true;
    /** Le serveur libère lui-même son passager (Voyageurs.liberer) : point de descente imposé (null : le passager reste
     *  où il est, la téléportation qui suit le place). */
    boolean libere;
    Vec3 sortieImposee;

    public Voyageur(EntityType<? extends Voyageur> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setInvisible(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(VEHICULE, 1);
        entityData.define(LX, 0f);
        entityData.define(LZ, 0f);
        entityData.define(ASSIS, false);
        entityData.define(HAUTEUR, 0f);
        entityData.define(ALLONGE, false);
    }

    public boolean assis() {
        return pv >= 0 ? passis : entityData.get(ASSIS);
    }

    /** Allongé sur le plancher (jamais assis en même temps). */
    public boolean allonge() {
        return !assis() && (pv >= 0 ? pallonge : entityData.get(ALLONGE));
    }

    public int vehicule() {
        return pv >= 0 ? pv : rv >= 0 ? rv : entityData.get(VEHICULE);
    }

    public float lx() {
        return pv >= 0 ? px : rv >= 0 ? rx : entityData.get(LX);
    }

    public float lz() {
        return pv >= 0 ? pz : rv >= 0 ? rz : entityData.get(LZ);
    }

    /** Hauteur du saut au-dessus du plancher (0 assis, allongé ou pieds au plancher). */
    public float hauteur() {
        if (assis() || allonge()) return 0f;
        return pv >= 0 ? ph : rv >= 0 ? rh : entityData.get(HAUTEUR);
    }

    /** Debout ou allongé, les pieds (ou le corps) sur le plancher : pas assis, pas en l'air. */
    public boolean surLePlancher() {
        return !assis() && hauteur() <= 0.02f;
    }

    void placer(int v, float x, float z) {
        entityData.set(VEHICULE, v);
        entityData.set(LX, x);
        entityData.set(LZ, z);
    }

    /** Tick client (marche du passager, placement d'après l'horaire) : branché par la partie client (VoyageursClient). */
    public static final Crochet<Voyageur> TICK_CLIENT = new Crochet<>();

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            TICK_CLIENT.accept(this);
        } else {
            tickServeur();
        }
    }

    /** Tick serveur : neutre ici ; VoyageurServeur (partie serveur) place le voyageur et gère la descente. */
    void tickServeur() {}

    @Override
    protected void readAdditionalSaveData(CompoundTag t) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {}

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected boolean canAddPassenger(Entity e) {
        return e instanceof Player && getPassengers().isEmpty();
    }

    @Override
    public boolean shouldRiderSit() {
        return assis();
    }

    /** Debout : pieds au plancher (plus la hauteur du saut) ; allongé : le corps à plat sur le plancher ; assis : un peu
     *  plus bas, le bassin sur le coussin (pose assise du modèle). */
    @Override
    protected void positionRider(Entity p, Entity.MoveFunction f) {
        if (hasPassenger(p)) f.accept(p, getX(), getY() + (assis() ? -0.15 : hauteur()), getZ());
    }

    /** Descente par le chemin vanilla (stopRiding : téléportation d'un autre mod, commande, mort...) : sortie sûre à côté
     *  du train (VoyageursServeur.sortie, partie serveur), jamais la position du voyageur (dans la caisse, au-dessus de la voie). */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity p) {
        if (libere) return sortieImposee != null ? sortieImposee : p.position();
        return super.getDismountLocationForPassenger(p);              // sans partie serveur : sortie vanilla (VoyageurServeur)
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
        // position calculée par le client lui-même (horaire + coordonnées dans la voiture)
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d2) {
        return false;
    }
}
