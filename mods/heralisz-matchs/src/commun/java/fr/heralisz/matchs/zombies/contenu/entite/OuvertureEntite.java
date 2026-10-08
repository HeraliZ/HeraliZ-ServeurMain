package fr.heralisz.matchs.zombies.contenu.entite;

import fr.heralisz.matchs.zombies.contenu.ZbRegistres;
import fr.heralisz.matchs.zombies.jeu.api.ZombiesApi;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * Animation d'ouverture d'un element payant (purement visuelle, 2 s) : le jeu retire les blocs de la porte a l'achat,
 * le decor fait apparaitre cette entite a la place, dessinee avec le meme modele que l'element ferme : planches qui
 * sautent et battants qui s'ouvrent (porte), chaine et cadenas qui tombent puis grille qui s'ouvre (grille), tas qui
 * s'effondre dans le sol (debris). Position = centre bas du volume, lacet = normale de l'element. Ni sauvegardee, ni
 * touchable ; disparait seule.
 */
public class OuvertureEntite extends Entity {
    public static final int DUREE = 44;
    private static final EntityDataAccessor<String> TYPE = SynchedEntityData.defineId(OuvertureEntite.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> TAILLE = SynchedEntityData.defineId(OuvertureEntite.class, EntityDataSerializers.INT);

    public OuvertureEntite(EntityType<? extends OuvertureEntite> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
    }

    public static OuvertureEntite creer(Level level, double x, double y, double z, float lacet, String type, int largeur, int hauteur, int profondeur,
                                        boolean cadre) {
        OuvertureEntite e = new OuvertureEntite(ZbRegistres.OUVERTURE.get(), level);
        e.moveTo(x, y, z, lacet, 0);
        e.entityData.set(TYPE, type);
        e.entityData.set(TAILLE, Mth.clamp(largeur, 1, 8) | Mth.clamp(hauteur, 1, 6) << 4 | Mth.clamp(profondeur, 1, 4) << 8 | (cadre ? 1 << 12 : 0));
        e.getPersistentData().putBoolean(ZombiesApi.TAG_DECOR, true);
        return e;
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(TYPE, "porte");
        this.entityData.define(TAILLE, 2 | 3 << 4 | 1 << 8);
    }

    public String type() {
        return this.entityData.get(TYPE);
    }

    public int largeur() {
        return this.entityData.get(TAILLE) & 15;
    }

    public int hauteur() {
        return (this.entityData.get(TAILLE) >> 4) & 15;
    }

    public int profondeur() {
        return (this.entityData.get(TAILLE) >> 8) & 15;
    }

    /** Vrai si le cadre du modele est dessine (vitrine du banc ; faux sur la carte). */
    public boolean cadre() {
        return (this.entityData.get(TAILLE) & (1 << 12)) != 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.level().isClientSide && this.tickCount > DUREE) this.discard();
    }

    @Override
    public boolean isPickable() {
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
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return new ClientboundAddEntityPacket(this);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double d2) {
        return d2 < 80 * 80;
    }
}
