package fr.heraliz.casino.entity;

import fr.heraliz.casino.zone.ZoneManager;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import javax.annotation.Nullable;

/**
 * Partie SERVEUR de {@link GuardEntity} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new GuardEntity(...) » côté serveur.
 */
public class GuardEntityServeur extends GuardEntity {
   public GuardEntityServeur(EntityType<? extends GuardEntity> type, Level level) {
      super(type, level);
   }


    // ---- serveur
    @Nullable
    private Vec3 post;
    private float postYaw;
    private boolean temporary;
    private int life = 900;

    /** Videur en poste : il y reste (et y revient si on le deplace). */
    public static GuardEntityServeur post(ServerLevel level, Vec3 at, float yaw, int side) {
        GuardEntityServeur g = new GuardEntityServeur(fr.heraliz.casino.registry.ModRegistry.GUARD.get(), level);
        g.post = at;
        g.postYaw = yaw;
        g.entityData.set(SIDE, (byte) side);
        g.place(at, yaw);
        level.addFreshEntity(g);
        return g;
    }

    /** Videur de renfort (expulsion) : sans gravite ni collisions, deplace par l'expulsion, jamais sauvegarde. */
    public static GuardEntityServeur temporary(ServerLevel level, Vec3 at, float yaw, int side) {
        GuardEntityServeur g = new GuardEntityServeur(fr.heraliz.casino.registry.ModRegistry.GUARD.get(), level);
        g.temporary = true;
        g.noPhysics = true;
        g.setNoGravity(true);
        g.setNoAi(true);
        g.entityData.set(SIDE, (byte) side);
        g.place(at, yaw);
        level.addFreshEntity(g);
        return g;
    }

    public boolean isTemporary() {
        return temporary;
    }

    /** Partie serveur du tick : renfort oublie qui s'en va. */
    @Override
    protected void tickServeur() {
        if (temporary && --life <= 0) {
            // renfort oublie (expulsion interrompue) : il ne reste pas la pour toujours
            discard();
        }
    }

    @Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        if (temporary) return;
        if (stanceLeft > 0 && --stanceLeft == 0) stance(IDLE);
        if (post == null) {
            post = position();
            postYaw = getYRot();
        }
        // il ne quitte pas son poste
        if (tickCount % 20 == 0 && position().distanceToSqr(post) > 0.09) {
            setPos(post.x, post.y, post.z);
            setDeltaMovement(Vec3.ZERO);
        }
        Player near = level().getNearestPlayer(this, 7.5);
        if (near != null && !near.isSpectator()) {
            getLookControl().setLookAt(near, 12, 40);
        } else {
            double r = Math.toRadians(postYaw);
            getLookControl().setLookAt(getX() - Math.sin(r) * 6, getEyeY(), getZ() + Math.cos(r) * 6, 8, 40);
        }
    }

    /** Un operateur en creatif retire un videur d'un coup ; personne d'autre ne peut lui faire de mal. */
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) return false;
        if (source.getEntity() instanceof Player p && p.isCreative() && p.hasPermissions(2)) {
            p.displayClientMessage(Component.translatable("command.heralizcasino.zone.guard_removed").withStyle(ChatFormatting.YELLOW), true);
            discard();
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.hurt(source, amount);
        return false;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
        if (player instanceof ServerPlayer sp && !temporary) ZoneManager.talk(sp, this);
        return InteractionResult.sidedSuccess(level().isClientSide);
    }

    @Override
    public boolean shouldBeSaved() {
        return !temporary && super.shouldBeSaved();
    }

    // =================================================================== sauvegarde
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (post != null) {
            tag.putDouble("PostX", post.x);
            tag.putDouble("PostY", post.y);
            tag.putDouble("PostZ", post.z);
            tag.putFloat("PostYaw", postYaw);
        }
        tag.putByte("Side", (byte) side());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.contains("PostX")) {
            post = new Vec3(tag.getDouble("PostX"), tag.getDouble("PostY"), tag.getDouble("PostZ"));
            postYaw = tag.getFloat("PostYaw");
        }
        if (tag.contains("Side")) entityData.set(SIDE, tag.getByte("Side"));
    }
}
