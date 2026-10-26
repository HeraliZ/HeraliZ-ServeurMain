package fr.heraliz.armory.entity;

import fr.heraliz.armory.menu.MannequinMenuServeur;
import fr.heraliz.armory.registry.ModItems;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gameevent.GameEvent;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

/**
 * Partie SERVEUR de {@link Mannequin} (docs/METHODE_CLIENT_SERVEUR.md §5) : logique de jeu absente du jar client.
 * Créée par l'usine du code commun dans les mondes serveur seulement ; ne jamais faire « new Mannequin(...) » côté serveur.
 */
public class MannequinServeur extends Mannequin {
   public MannequinServeur(EntityType<? extends Mannequin> type, Level level) {
      super(type, level);
   }

    @Override
    void ouvrirMenu(ServerPlayer player) {
        MannequinMenuServeur.open(player, this);
    }

    @Override
    boolean equipCurio(Player player, InteractionHand hand, ItemStack held) {
        var inv = CuriosApi.getCuriosInventory(this).resolve();
        if (inv.isEmpty()) return false;
        String bestId = null;
        int bestIndex = -1;
        boolean bestEmpty = false;
        for (String id : CuriosApi.getItemStackSlots(held, this).keySet()) {
            var h = inv.get().getStacksHandler(id);
            if (h.isEmpty()) continue;
            IDynamicStackHandler stacks = h.get().getStacks();
            for (int i = 0; i < stacks.getSlots(); i++) {
                if (!canEquip(id, i, this, held)) continue;
                boolean empty = stacks.getStackInSlot(i).isEmpty();
                if (bestId == null || (empty && !bestEmpty)) {
                    bestId = id;
                    bestIndex = i;
                    bestEmpty = empty;
                }
            }
        }
        if (bestId == null) return false;
        IDynamicStackHandler stacks = inv.get().getStacksHandler(bestId).get().getStacks();
        ItemStack old = stacks.getStackInSlot(bestIndex);
        stacks.setStackInSlot(bestIndex, held.copyWithCount(1));
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
            if (!old.isEmpty()) {
                if (held.isEmpty()) player.setItemInHand(hand, old);
                else if (!player.getInventory().add(old)) player.drop(old, false);
            }
        }
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_EQUIP_GENERIC, getSoundSource(), 1.0F, 1.0F);
        gameEvent(GameEvent.EQUIP, player);
        return true;
    }

    // ------------------------------------------------------------------ casse (meme comportement qu'un porte-armure)
    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) return false;
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            kill();
            return false;
        }
        if (isInvulnerableTo(source) || isInvisible() || isMarker()) return false;
        if (source.is(DamageTypeTags.IS_EXPLOSION)) {
            dropContents(true);
            kill();
            return false;
        }
        if (source.is(DamageTypeTags.IGNITES_ARMOR_STANDS)) {
            if (isOnFire()) wear(source, 0.15F);
            else setSecondsOnFire(5);
            return false;
        }
        if (source.is(DamageTypeTags.BURNS_ARMOR_STANDS) && getHealth() > 0.5F) {
            wear(source, 4.0F);
            return false;
        }
        boolean arrow = source.getDirectEntity() instanceof AbstractArrow;
        boolean pierce = arrow && ((AbstractArrow) source.getDirectEntity()).getPierceLevel() > 0;
        boolean player = "player".equals(source.getMsgId());
        if (!player && !arrow) return false;
        Entity attacker = source.getEntity();
        if (attacker instanceof Player p && !p.getAbilities().mayBuild) return false;
        if (source.isCreativePlayer()) {
            playBreak();
            particles();
            kill();
            return pierce;
        }
        long now = level().getGameTime();
        if (now - lastHit > 5L && !arrow) {
            level().broadcastEntityEvent(this, (byte) 32);
            gameEvent(GameEvent.ENTITY_DAMAGE, attacker);
            lastHit = now;
        } else {
            dropContents(true);
            particles();
            kill();
        }
        return true;
    }

    private void wear(DamageSource source, float amount) {
        float h = getHealth() - amount;
        if (h <= 0.5F) {
            dropContents(false);
            kill();
        } else {
            setHealth(h);
            gameEvent(GameEvent.ENTITY_DAMAGE, source.getEntity());
        }
    }

    /** Rend le mannequin (objet) et tout ce qu'il porte : armure, mains, slots Curios (normaux et cosmetiques). */
    public void dropContents(boolean self) {
        if (self) {
            ItemStack item = new ItemStack(ModItems.MANNEQUIN.get());
            if (hasCustomName()) item.setHoverName(getCustomName());
            Block.popResource(level(), blockPosition(), item);
        }
        playBreak();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack s = getItemBySlot(slot);
            if (!s.isEmpty()) {
                Block.popResource(level(), blockPosition().above(), s);
                setItemSlot(slot, ItemStack.EMPTY);
            }
        }
        CuriosApi.getCuriosInventory(this).ifPresent(inv -> inv.getCurios().values().forEach(h -> {
            for (IDynamicStackHandler stacks : new IDynamicStackHandler[]{h.getStacks(), h.getCosmeticStacks()}) {
                for (int i = 0; i < stacks.getSlots(); i++) {
                    ItemStack s = stacks.getStackInSlot(i);
                    if (!s.isEmpty()) {
                        Block.popResource(level(), blockPosition().above(), s);
                        stacks.setStackInSlot(i, ItemStack.EMPTY);
                    }
                }
            }
        }));
    }

    private void playBreak() {
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ARMOR_STAND_BREAK, getSoundSource(), 1.0F, 1.0F);
    }

    private void particles() {
        if (level() instanceof ServerLevel sl) {
            sl.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SMOOTH_QUARTZ.defaultBlockState()), getX(), getY(0.6666666666666666D),
                    getZ(), 10, getBbWidth() / 4.0F, getBbHeight() / 4.0F, getBbWidth() / 4.0F, 0.05D);
        }
    }
}
