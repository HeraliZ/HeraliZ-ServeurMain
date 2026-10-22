package fr.heraliz.crates.block;

import fr.heraliz.crates.crate.CrateType;
import fr.heraliz.crates.loot.CrateLoot;
import fr.heraliz.crates.loot.GeneralConfig;
import fr.heraliz.crates.loot.LootEntry;
import fr.heraliz.crates.loot.LootManager;
import fr.heraliz.crates.loot.LootRarity;
import fr.heraliz.crates.loot.PendingRewards;
import fr.heraliz.crates.loot.RewardHelper;
import fr.heraliz.crates.registry.ModRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import fr.heraliz.crates.item.CrateKeyItem;
import fr.heraliz.crates.opening.CaseOpening;
import net.minecraft.world.entity.player.Player;

/**
 * Version serveur d'un coffre (docs/METHODE_CLIENT_SERVEUR.md §5.2) : clé, tirage du lot, séquence d'ouverture, remise
 * de la récompense (ou mise en attente), sauvegarde du lot. Le client n'a que l'état synchronisé (CrateBlockEntity).
 */
public class CrateBlockEntityServeur extends CrateBlockEntity {
    // ---- serveur uniquement
    @Nullable
    private CompoundTag reward;
    private boolean rewarded;

    // ---- serveur : parties verifiees depuis le chargement (caisses posees avec une ancienne version)
    private boolean partsChecked;

    public CrateBlockEntityServeur(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    /**
     * Clic droit (accroupi ou non, avec ou sans clé) : toujours l'interface d'ouverture façon CS:GO
     * (CaseOpening.openScreen). Plus aucune animation dans le monde (choix du propriétaire, 22/10 : trop de lag) :
     * avec une clé, la roue tourne dans l'écran ; le serveur vérifie et consomme la clé, tire le lot et le donne
     * (CaseOpening.handleOpen). L'ancienne séquence dans le monde (tryOpen, denyOpen, CrateAnim) n'est plus appelée ;
     * son code reste, ainsi que la fin d'une ouverture déjà en cours dans une sauvegarde (tickServeur).
     */
    @Override
    public void clicDroit(ServerPlayer sp, boolean accroupi) {
        CaseOpening.openScreen(sp, type, worldPosition);
    }

    private static ItemStack findKey(Player player, CrateType type) {
        ItemStack main = player.getMainHandItem();
        if (main.getItem() instanceof CrateKeyItem k && k.type == type) return main;
        ItemStack off = player.getOffhandItem();
        if (off.getItem() instanceof CrateKeyItem k && k.type == type) return off;
        return ItemStack.EMPTY;
    }

    /** Clic sans la bonne cle : son de verrou + le coffre tremble. */
    public void denyOpen(ServerPlayer player, boolean wrongKey) {
        if (level == null) return;
        if (active) {
            player.displayClientMessage(Component.translatable("message.heralizcrates.busy").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        Component msg = Component.translatable(wrongKey ? "message.heralizcrates.wrong_key" : "message.heralizcrates.need_key",
                type.keyName()).withStyle(ChatFormatting.GRAY);
        player.displayClientMessage(msg, true);
        level.playSound(null, worldPosition, ModRegistry.SND_LOCKED.get(), SoundSource.BLOCKS, 0.9F,
                0.95F + level.random.nextFloat() * 0.1F);
        level.blockEvent(worldPosition, getBlockState().getBlock(), EVENT_SHAKE, 0);
    }

    /** Clic avec la bonne cle : on tire la recompense et on lance la sequence. */
    public void tryOpen(ServerPlayer player, ItemStack key) {
        if (level == null) return;
        if (active) {
            player.displayClientMessage(Component.translatable("message.heralizcrates.busy").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        CrateLoot loot = LootManager.get(type);
        LootEntry win = loot.roll(level.random, fr.heraliz.crates.loot.EmotesCaisse.permis(player, type));
        if (win == null) {
            // caisse des emotes : il a deja toutes celles qu'elle peut donner (la cle n'est pas consommee)
            player.displayClientMessage(Component.translatable(type == fr.heraliz.crates.crate.CrateType.SPECTACLE && !loot.isEmpty()
                    ? "message.heralizcrates.all_emotes" : "message.heralizcrates.empty").withStyle(ChatFormatting.RED), false);
            return;
        }
        RandomSource rnd = level.random;
        int size = Mth.clamp(GeneralConfig.ringSize, 4, 12);
        ring.clear();
        ringRarity.clear();
        winIndex = rnd.nextInt(size);
        for (int i = 0; i < size; i++) {
            LootEntry e = i == winIndex ? win : loot.rollDisplayEntry(rnd, win);
            ring.add(e == null ? ItemStack.EMPTY : e.displayStack());
            ringRarity.add(e == null ? LootRarity.COMMON : e.rarity);
        }
        if (!(player.getAbilities().instabuild && !GeneralConfig.consumeKeyInCreative)) {
            key.shrink(1);
        }
        reward = win.toTag();
        rewarded = false;
        winRarity = win.rarity;
        winCount = win.count;
        opener = player.getUUID();
        active = true;
        startTick = level.getGameTime();
        setChanged();
        sync();
    }

    @Override
    protected void tickServeur(Level level, BlockPos pos, BlockState state) {
        CrateBlockEntityServeur be = this;
        if (!be.partsChecked) {
            be.partsChecked = true;
            CrateBlock.placeParts(level, pos, be.type, state.getValue(CrateBlock.FACING));
        }
        if (!be.active) return;
        long t = level.getGameTime() - be.startTick;
        if (t < 0) {
            // horloge du monde modifiee (/time) : on recale
            be.startTick = level.getGameTime();
            be.sync();
            return;
        }
        if (!be.rewarded && t >= CrateAnim.REWARD && level instanceof ServerLevel sl) {
            be.deliverReward(sl);
        }
        if (t >= CrateAnim.END) {
            be.active = false;
            be.ring.clear();
            be.ringRarity.clear();
            be.reward = null;
            be.opener = null;
            be.setChanged();
            be.sync();
        }
    }

    private void deliverReward(ServerLevel sl) {
        rewarded = true;
        setChanged();
        if (reward == null || opener == null) return;
        ServerPlayer player = sl.getServer().getPlayerList().getPlayer(opener);
        if (player != null) {
            RewardHelper.give(player, reward, type);
        } else {
            PendingRewards.get(sl.getServer()).add(opener, reward, type);
        }
    }

    /** Le coffre est casse pendant une ouverture : le joueur recoit quand meme son lot. */
    @Override
    public void onCrateRemoved() {
        if (active && !rewarded && level instanceof ServerLevel sl) {
            deliverReward(sl);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (reward != null) tag.put("Reward", reward);
        tag.putBoolean("Rewarded", rewarded);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        reward = tag.contains("Reward", Tag.TAG_COMPOUND) ? tag.getCompound("Reward") : null;
        rewarded = tag.getBoolean("Rewarded");
    }
}
