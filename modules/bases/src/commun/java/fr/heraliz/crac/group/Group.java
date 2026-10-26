package fr.heraliz.crac.group;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Config;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;

public final class Group {
    private final UUID id;
    private String name;
    private String tag;
    private int color;
    private UUID owner;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private long lastActiveEpochMs = System.currentTimeMillis();
    /**
     * Overworld game time a member was last online. Game time stops while the server is down, so downtime never
     * counts as inactivity. -1 until first seen (groups saved before this was tracked).
     */
    private long lastActiveTick = -1L;

    public static int maxMembers() {
        try {
            return Config.maxGroupSize.get();
        } catch (IllegalStateException var1) {
            return 8;
        }
    }

    public Group(UUID id, String name, String tag, int color, UUID owner) {
        this.id = id;
        this.name = name;
        this.tag = tag == null ? "" : tag;   // jamais null : la sauvegarde de TOUS les groupes planterait
        this.color = color;
        this.owner = owner;
        this.members.add(owner);
    }

    public UUID id() {
        return this.id;
    }

    public String name() {
        return this.name;
    }

    public String tag() {
        return this.tag;
    }

    public int color() {
        return this.color;
    }

    public UUID owner() {
        return this.owner;
    }

    public Set<UUID> members() {
        return this.members;
    }

    public int size() {
        return this.members.size();
    }

    public boolean isFull() {
        return this.members.size() >= maxMembers();
    }

    public boolean isOwner(UUID player) {
        return this.owner.equals(player);
    }

    public boolean isMember(UUID player) {
        return this.members.contains(player);
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setTag(String tag) {
        this.tag = tag == null ? "" : tag;
    }

    public void setColor(int color) {
        this.color = color;
    }

    /** Wall-clock time a member was last online (informative only, see {@link #lastActiveTick()}). */
    public long lastActive() {
        return this.lastActiveEpochMs;
    }

    public long lastActiveTick() {
        return this.lastActiveTick;
    }

    /** A member is online at overworld game time {@code gameTime}. */
    public void touchActive(long gameTime) {
        this.lastActiveEpochMs = System.currentTimeMillis();
        this.lastActiveTick = gameTime;
    }

    /** Hands the group over to one of its members. */
    public boolean setOwner(UUID member) {
        if (!this.members.contains(member) || this.owner.equals(member)) {
            return false;
        }
        this.owner = member;
        return true;
    }

    public boolean addMember(UUID player) {
        return !this.isFull() && !this.members.contains(player) ? this.members.add(player) : false;
    }

    public boolean removeMember(UUID player) {
        return this.owner.equals(player) ? false : this.members.remove(player);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("Id", this.id);
        tag.putString("Name", this.name);
        tag.putString("Tag", this.tag);
        tag.putInt("Color", this.color);
        tag.putUUID("Owner", this.owner);
        tag.putLong("LastActive", this.lastActiveEpochMs);
        tag.putLong("LastActiveTick", this.lastActiveTick);
        ListTag list = new ListTag();

        for (UUID m : this.members) {
            CompoundTag mt = new CompoundTag();
            mt.putUUID("U", m);
            list.add(mt);
        }

        tag.put("Members", list);
        return tag;
    }

    public static Group load(CompoundTag tag) {
        // names saved before the name rules may hold formatting codes: strip them
        String name = GroupNames.sanitize(tag.getString("Name"), GroupNames.WIRE_MAX_NAME);
        String groupTag = GroupNames.sanitize(tag.getString("Tag"), GroupNames.WIRE_MAX_TAG);
        Group g = new Group(tag.getUUID("Id"), name, groupTag, tag.getInt("Color"), tag.getUUID("Owner"));
        g.lastActiveEpochMs = tag.contains("LastActive") ? tag.getLong("LastActive") : System.currentTimeMillis();
        g.lastActiveTick = tag.contains("LastActiveTick") ? tag.getLong("LastActiveTick") : -1L;
        g.members.clear();
        g.members.add(g.owner);
        ListTag list = tag.getList("Members", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag m = list.getCompound(i);
            // a broken member entry costs that member only, not the whole group
            if (m.hasUUID("U")) {
                g.members.add(m.getUUID("U"));
            }
        }

        return g;
    }
}
