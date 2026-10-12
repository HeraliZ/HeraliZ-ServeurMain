package fr.heraliz.corps.entite;

import fr.heraliz.corps.CorpsConfig;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Corps d'un joueur mort. Une entite simple (pas un mob) : ni IA, ni attributs, ni feu, ni eau, ni portail.
 * <p>Serveur : rien a faire au repos. Une fois par seconde (decale selon l'entite) : fin du minuteur, et appui au sol
 * (s'il a disparu, le corps tombe ; pendant la chute seulement, physique a chaque tick et position envoyee aux clients).
 * Le contenu ({@link CorpsContenu}) n'existe que cote serveur ; les clients le voient par le menu de fouille.
 * <p>Client : seulement l'identite pour le rendu et le petit panneau (proprietaire, apparence, cause, minuteur, clan).
 * <p>Temps : en temps de jeu (le minuteur ne tourne que serveur allume, comme dans DayZ).
 */
public class CorpsEntity extends Entity implements IEntityAdditionalSpawnData {
    static final EntityDataAccessor<Long> DATA_EXPIRE = SynchedEntityData.defineId(CorpsEntity.class, EntityDataSerializers.LONG);
    static final EntityDataAccessor<Boolean> DATA_VIDE = SynchedEntityData.defineId(CorpsEntity.class, EntityDataSerializers.BOOLEAN);

    /** Corps presents dans le monde du client (rendu, plafonds) ; vide cote serveur. Fil du client seulement. */
    public static final List<CorpsEntity> CLIENT = new ArrayList<>();

    // ------------------------------------------------------------------ identite (serveur et client)
    UUID proprietaire = Util.NIL_UUID;
    String nom = "";
    Component cause = Component.empty();
    long protegeJusqua;
    long visibleDes;
    @Nullable
    UUID clan;
    /** Apparence CaraCrea du mort (NBT d'environ 30 octets), null sans CaraCrea. */
    @Nullable
    CompoundTag apparence;
    /** Skin vanilla du mort (propriete « textures » du profil), seulement sans CaraCrea. */
    String texturesValeur = "", texturesSignature = "";

    // ------------------------------------------------------------------ client (rendu)
    /** Dessine ou non (plafonds par chunk et total, recalcules toutes les 10 ticks par le client). */
    public boolean rendu = true;
    /** Le mort est du clan du joueur local (marqueur), recalcule par le client. */
    public boolean clanAmi;
    /** Peau resolue par le client (voir client.Peaux) ; Object pour ne rien charger de client ici. */
    public Object peauCache;

    public CorpsEntity(EntityType<? extends CorpsEntity> type, Level level) {
        super(type, level);
        this.blocksBuilding = false;
    }

    // ------------------------------------------------------------------ acces
    public UUID proprietaire() {
        return proprietaire;
    }

    public String nom() {
        return nom;
    }

    public Component cause() {
        return cause;
    }

    @Nullable
    public UUID clan() {
        return clan;
    }

    @Nullable
    public CompoundTag apparence() {
        return apparence;
    }

    public String texturesValeur() {
        return texturesValeur;
    }

    public String texturesSignature() {
        return texturesSignature;
    }

    public long expire() {
        return entityData.get(DATA_EXPIRE);
    }

    public boolean vide() {
        return entityData.get(DATA_VIDE);
    }

    public long protegeJusqua() {
        return protegeJusqua;
    }

    public long visibleDes() {
        return visibleDes;
    }

    /**
     * Le joueur peut-il OUVRIR la fouille (vivant, meme monde, corps a portee de main) ? Distance mesuree des yeux du
     * joueur jusqu'a la boite du corps, comme le jeu le fait pour un clic sur une entite : on fouille de la ou l'on est,
     * sans etre deplace. Reglage {@code distance_fouille} (5 blocs ; le clic du jeu porte a 3 blocs, la marge couvre la
     * latence).
     */
    public boolean aPortee(Player p) {
        return aPortee(p, CorpsConfig.DISTANCE_FOUILLE.get());
    }

    /** Le joueur peut-il GARDER la fouille ouverte ? Meme mesure, un bloc de plus (un pas en arriere ne la ferme pas). */
    public boolean resteAPortee(Player p) {
        return aPortee(p, CorpsConfig.DISTANCE_FOUILLE.get() + 1.0);
    }

    private boolean aPortee(Player p, double distance) {
        if (isRemoved() || !p.isAlive() || p.level() != level()) return false;
        // distance des yeux au point le plus proche de la boite
        AABB b = getBoundingBox();
        Vec3 o = p.getEyePosition();
        double dx = Math.max(Math.max(b.minX - o.x, o.x - b.maxX), 0.0);
        double dy = Math.max(Math.max(b.minY - o.y, o.y - b.maxY), 0.0);
        double dz = Math.max(Math.max(b.minZ - o.z, o.z - b.maxZ), 0.0);
        return dx * dx + dy * dy + dz * dz <= distance * distance;
    }

    /**
     * Contenu du corps : il n'existe que cote serveur (CorpsEntityServeur) ; ici (monde du client) : null, comme
     * avant (les clients le voient par le menu de fouille).
     */
    @Nullable
    public CorpsContenu contenu() {
        return null;
    }

    // ------------------------------------------------------------------ tick
    @Override
    public void tick() {
        // pas de super.tick() : ni feu, ni eau, ni portail, ni poussee ; le client n'a rien a calculer
        // (cote serveur : CorpsEntityServeur.tick, minuteur, appui au sol, chute)
    }

    // ------------------------------------------------------------------ interactions
    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (level().isClientSide) return InteractionResult.SUCCESS;
        if (player instanceof ServerPlayer sp) ouvrirFouille(sp);
        return InteractionResult.CONSUME;
    }

    /** Ouverture de la fouille (serveur) : neutre ici, CorpsEntityServeur la redefinit (Fouille.ouvrir). */
    void ouvrirFouille(ServerPlayer sp) {
    }

    /** Visable (et donc fouillable) une fois la scene d'execution terminee. */
    @Override
    public boolean isPickable() {
        return !isRemoved() && level().getGameTime() >= visibleDes;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isAttackable() {
        return false;
    }

    @Override
    public boolean skipAttackInteraction(Entity attaquant) {
        return true;
    }

    @Override
    public boolean hurt(DamageSource source, float montant) {
        return false;
    }

    @Override
    public boolean isIgnoringBlockTriggers() {
        return true;
    }

    @Override
    public PushReaction getPistonPushReaction() {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    public boolean displayFireAnimation() {
        return false;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return null;
    }

    // ------------------------------------------------------------------ boite (forme du corps allonge)
    /**
     * Demi-longueur du segment tete-pieds et epaisseur autour de lui (blocs). Le corps fait 1,875 bloc de long et
     * environ 1 bloc de large aux epaules (bras compris), centre sur la position de l'entite, la tete du cote oppose au
     * regard du mort (voir client.Maillage) : DEMI_SEGMENT + RAYON = 0,94 (moitie de la longueur), RAYON = 0,5.
     */
    static final double DEMI_SEGMENT = 0.44, RAYON = 0.5;

    /**
     * Boite qui suit le corps allonge au lieu d'un carre : le jeu n'a que des boites alignees sur les axes, celle-ci
     * entoure le segment tete-pieds tourne comme le corps, epaissi de {@link #RAYON}. Corps nord-sud ou est-ouest :
     * 1 x 1,9 bloc, exactement le corps ; corps en diagonale : carre de 1,6 bloc (un rectangle tourne a 45 degres en
     * demanderait 2). Hauteur 0,45 (le dos, le torse et la tete). On la vise facilement pour fouiller ; elle ne gene
     * aucun deplacement (ni collision ni poussee : voir canBeCollidedWith et isPushable).
     * <p>Recalculee a chaque placement (setPos) ; le lacet est pose avant (moveTo, chargement, readSpawnData).
     */
    @Override
    protected AABB makeBoundingBox() {
        double lacet = Math.toRadians(getYRot());
        double ex = Math.abs(Math.sin(lacet)) * DEMI_SEGMENT + RAYON;
        double ez = Math.abs(Math.cos(lacet)) * DEMI_SEGMENT + RAYON;
        Vec3 p = position();
        return new AABB(p.x - ex, p.y, p.z - ez, p.x + ex, p.y + getBbHeight(), p.z + ez);
    }

    @Override
    public void refreshDimensions() {
        super.refreshDimensions();
        setBoundingBox(makeBoundingBox());
    }

    /** Les mains depassent un peu la boite : boite de rendu elargie. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(0.5, 0.3, 0.5);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceCarre) {
        return distanceCarre < CorpsConfig.distanceRenduCarre;
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (level().isClientSide && !CLIENT.contains(this)) CLIENT.add(this);
    }

    @Override
    public void onRemovedFromWorld() {
        super.onRemovedFromWorld();
        if (level().isClientSide) {
            CLIENT.remove(this);
            if (peauCache instanceof AutoCloseable c) {
                try {
                    c.close();
                } catch (Exception ignore) {
                    // liberation de la peau : rien a faire de plus
                }
            }
            peauCache = null;
        }
    }

    // ------------------------------------------------------------------ donnees
    @Override
    protected void defineSynchedData() {
        entityData.define(DATA_EXPIRE, 0L);
        entityData.define(DATA_VIDE, false);
    }

    /** Sauvegarde : CorpsEntityServeur (le monde du client ne charge ni ne sauve jamais un corps). */
    @Override
    protected void readAdditionalSaveData(CompoundTag t) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag t) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf b) {
        b.writeUUID(proprietaire);
        b.writeUtf(nom, 64);
        b.writeComponent(cause);
        b.writeLong(protegeJusqua);
        b.writeLong(visibleDes);
        b.writeBoolean(clan != null);
        if (clan != null) b.writeUUID(clan);
        b.writeNbt(apparence);
        b.writeUtf(texturesValeur, 8192);
        b.writeUtf(texturesSignature, 8192);
        b.writeFloat(getYRot());
    }

    @Override
    public void readSpawnData(FriendlyByteBuf b) {
        proprietaire = b.readUUID();
        nom = b.readUtf(64);
        cause = b.readComponent();
        protegeJusqua = b.readLong();
        visibleDes = b.readLong();
        clan = b.readBoolean() ? b.readUUID() : null;
        apparence = b.readNbt();
        texturesValeur = b.readUtf(8192);
        texturesSignature = b.readUtf(8192);
        float lacet = b.readFloat();
        setYRot(lacet);
        yRotO = lacet;
        // la position a ete posee avant le lacet exact (paquet d'apparition) : boite recalculee dans le bon sens
        setPos(getX(), getY(), getZ());
    }
}
