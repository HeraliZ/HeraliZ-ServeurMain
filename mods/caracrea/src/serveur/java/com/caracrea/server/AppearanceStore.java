package com.caracrea.server;

import com.caracrea.CaraCrea;
import com.caracrea.character.Appearance;
import com.caracrea.character.AppearanceCodec;
import com.caracrea.character.AppearanceRandomizer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;

import java.util.UUID;

/**
 * Where a player's appearance lives on the server: inside the player's
 * persistent NBT, under {@code PlayerPersisted} so it survives death.
 * That is about 30 bytes per player, saved with the rest of the player data.
 */
public final class AppearanceStore {
    private static final String KEY = CaraCrea.MOD_ID;
    /** Set once the player confirmed a look in the creator (1.0.1). */
    private static final String CHOSEN = "Chosen";

    private AppearanceStore() {
    }

    /** The saved appearance, or null for a player who never had one. */
    public static Appearance get(Player player) {
        CompoundTag persisted = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG);
        return AppearanceCodec.fromNbt(persisted.getCompound(KEY));
    }

    /** Stores the look; the "chosen" flag is kept. */
    public static void set(Player player, Appearance appearance) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag old = persisted.getCompound(KEY);
        CompoundTag tag = AppearanceCodec.toNbt(appearance);
        if (old.contains(CHOSEN)) {
            tag.putBoolean(CHOSEN, old.getBoolean(CHOSEN));
        }
        persisted.put(KEY, tag);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /**
     * Whether the player has confirmed a look. 1.0.0 stored the default look on the first login, so a player who
     * left before confirming never saw the creator again: the creator now opens until this is set. Data saved by
     * 1.0.0 (an appearance without the flag) counts as chosen, so nobody is asked twice.
     */
    public static boolean hasChosen(Player player) {
        CompoundTag tag = player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getCompound(KEY);
        if (!tag.contains(CHOSEN)) {
            return AppearanceCodec.fromNbt(tag) != null;
        }
        return tag.getBoolean(CHOSEN);
    }

    public static void setChosen(Player player, boolean chosen) {
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        CompoundTag tag = persisted.getCompound(KEY);
        tag.putBoolean(CHOSEN, chosen);
        persisted.put(KEY, tag);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
    }

    /**
     * The appearance a player has before choosing one: a random but stable
     * character derived from the UUID. Clients compute the same one, so a
     * player looks right even before the first sync packet arrives.
     */
    public static Appearance defaultFor(UUID uuid) {
        return AppearanceRandomizer.defaultFor(uuid);
    }
}
