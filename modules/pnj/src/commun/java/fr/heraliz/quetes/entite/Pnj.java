package fr.heraliz.quetes.entite;

import fr.heraliz.quetes.Langue;
import fr.heraliz.quetes.Texte;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;

/**
 * Un PNJ du spawn (le Commandant Varga), posé par le staff avec /pnj creer. Partie commune : ce que lit l'affichage
 * (identifiant du PNJ, nom en trois langues, skin ou apparence CaraCrea) et un corps immobile et invincible. Les dialogues, les quêtes et le
 * regard qui suit les joueurs sont dans {@code PnjServeur} (créé par son type, jamais par {@code new}).
 */
public class Pnj extends PathfinderMob {
    public static final EntityDataAccessor<String> ID = SynchedEntityData.defineId(Pnj.class, EntityDataSerializers.STRING);
    /** Nom affiché (texte JSON en trois langues). */
    public static final EntityDataAccessor<String> NOM = SynchedEntityData.defineId(Pnj.class, EntityDataSerializers.STRING);
    /** Skin : fichier assets/heraliz_quetes/textures/pnj/&lt;skin&gt;.png du jar joueur (vide ou absent : skin par défaut). */
    public static final EntityDataAccessor<String> SKIN = SynchedEntityData.defineId(Pnj.class, EntityDataSerializers.STRING);
    /** Bras fins (modèle « Alex ») ou larges. */
    public static final EntityDataAccessor<Boolean> FIN = SynchedEntityData.defineId(Pnj.class, EntityDataSerializers.BOOLEAN);
    /**
     * Apparence CaraCrea (personnage du créateur du serveur, {@code {Format, Values}} comme celle d'un joueur) ; vide :
     * le skin ci-dessus. Peinte chez le joueur par CaraCrea.
     */
    public static final EntityDataAccessor<CompoundTag> APPARENCE = SynchedEntityData.defineId(Pnj.class, EntityDataSerializers.COMPOUND_TAG);

    /** Apparence copiée d'un membre du staff (/pnj apparence moi) : celle du fichier du PNJ ne la remplace plus. */
    private boolean apparencePerso;

    private String nomCache = "";
    private Texte nomTexte = Texte.VIDE;

    public Pnj(EntityType<? extends Pnj> type, Level level) {
        super(type, level);
        setPersistenceRequired();
        setInvulnerable(true);
    }

    public static AttributeSupplier.Builder attributs() {
        return Mob.createMobAttributes().add(Attributes.MAX_HEALTH, 20.0D).add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData() {
        super.defineSynchedData();
        entityData.define(ID, "");
        entityData.define(NOM, "");
        entityData.define(SKIN, "");
        entityData.define(FIN, false);
        entityData.define(APPARENCE, new CompoundTag());
    }

    public String pnjId() {
        return entityData.get(ID);
    }

    public void definirId(String id) {
        entityData.set(ID, id == null ? "" : id);
    }

    public Texte nom() {
        String s = entityData.get(NOM);
        if (!s.equals(nomCache)) {
            nomCache = s;
            nomTexte = Texte.json(s);
        }
        return nomTexte;
    }

    public void definirNom(Texte t) {
        entityData.set(NOM, t.toString());
    }

    public String skin() {
        return entityData.get(SKIN);
    }

    public void definirSkin(String s) {
        entityData.set(SKIN, s == null ? "" : s);
    }

    public boolean brasFins() {
        return entityData.get(FIN);
    }

    public void definirBrasFins(boolean fin) {
        entityData.set(FIN, fin);
    }

    public CompoundTag apparence() {
        return entityData.get(APPARENCE);
    }

    public boolean apparencePerso() {
        return apparencePerso;
    }

    public void definirApparence(CompoundTag t, boolean perso) {
        entityData.set(APPARENCE, t == null ? new CompoundTag() : t.copy());
        apparencePerso = perso && t != null;
    }

    @Override
    public Component getName() {
        Texte n = nom();
        return n.vide() ? super.getName() : Component.literal(n.choisir(Langue.code()));
    }

    @Override
    public boolean shouldShowName() {
        return true;
    }

    /**
     * Surbrillance chez le joueur seulement (28/10) : le PNJ vers qui il doit aller (guide, puis Varga). Branchée par la
     * partie client ({@code Guidage}) ; rien côté serveur, les autres joueurs ne voient rien.
     */
    public static volatile java.util.function.Predicate<Pnj> SURBRILLANCE_CLIENT;

    @Override
    public boolean isCurrentlyGlowing() {
        java.util.function.Predicate<Pnj> s = SURBRILLANCE_CLIENT;
        return level().isClientSide && s != null && s.test(this) || super.isCurrentlyGlowing();
    }

    @Override
    public int getTeamColor() {
        java.util.function.Predicate<Pnj> s = SURBRILLANCE_CLIENT;
        return level().isClientSide && s != null && s.test(this) ? 0x5FE08A : super.getTeamColor();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag t) {
        super.addAdditionalSaveData(t);
        t.putString("PnjId", pnjId());
        t.putString("PnjNom", entityData.get(NOM));
        t.putString("PnjSkin", skin());
        t.putBoolean("PnjFin", brasFins());
        t.put("PnjApparence", apparence().copy());
        t.putBoolean("PnjApparencePerso", apparencePerso);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag t) {
        super.readAdditionalSaveData(t);
        definirId(t.getString("PnjId"));
        entityData.set(NOM, t.getString("PnjNom"));
        definirSkin(t.getString("PnjSkin"));
        definirBrasFins(t.getBoolean("PnjFin"));
        definirApparence(t.getCompound("PnjApparence"), t.getBoolean("PnjApparencePerso"));
        setInvulnerable(true);
    }

    // ------------------------------------------------------------------ un corps qui ne bouge pas et ne meurt pas
    @Override
    public boolean hurt(DamageSource source, float montant) {
        // seul /kill (ou le vide) l'atteint : le staff le retire avec /pnj retirer
        return source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && super.hurt(source, montant);
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public void push(Entity e) {
    }

    @Override
    protected void doPush(Entity e) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player p) {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    public boolean canChangeDimensions() {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();
        setDeltaMovement(0, getDeltaMovement().y < 0 ? getDeltaMovement().y : 0, 0);
    }

    /** Clic droit : la partie serveur ouvre le dialogue ; ici, on dit juste au jeu que le clic est pris. */
    @Override
    protected InteractionResult mobInteract(Player joueur, InteractionHand main) {
        return InteractionResult.sidedSuccess(level().isClientSide);
    }
}
