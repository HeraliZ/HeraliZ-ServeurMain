package com.caracrea.character;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** NBT and network encoding of an {@link Appearance}. */
public final class AppearanceCodec {
    private static final String FORMAT = "Format";
    private static final String VALUES = "Values";

    private AppearanceCodec() {
    }

    public static CompoundTag toNbt(Appearance appearance) {
        CompoundTag tag = new CompoundTag();
        tag.putInt(FORMAT, Appearance.FORMAT);
        tag.putByteArray(VALUES, appearance.toBytes());
        return tag;
    }

    /** Null when the tag holds no appearance. */
    public static Appearance fromNbt(CompoundTag tag) {
        if (tag == null || !tag.contains(VALUES)) {
            return null;
        }
        return Appearance.fromBytes(tag.getByteArray(VALUES));
    }

    public static void write(FriendlyByteBuf buf, Appearance appearance) {
        buf.writeByteArray(appearance.toBytes());
    }

    public static Appearance read(FriendlyByteBuf buf) {
        return Appearance.fromBytes(buf.readByteArray(64));
    }
}
