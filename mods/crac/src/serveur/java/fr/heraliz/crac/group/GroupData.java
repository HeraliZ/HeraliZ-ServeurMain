package fr.heraliz.crac.group;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.data.SafeSavedData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class GroupData extends SafeSavedData {
    public static final String ID = "crac_groups";
    /** Pending invites expire after this long (they are never saved either). */
    public static final long INVITE_TTL_MS = 10L * 60L * 1000L;
    /** Most pending invites one player can hold, and one group can have out. */
    public static final int MAX_INVITES_PER_PLAYER = 8;
    public static final int MAX_INVITES_PER_GROUP = 16;

    public enum InviteResult { OK, ALREADY_INVITED, PLAYER_FULL, GROUP_FULL }

    private final Map<UUID, Group> byId = new HashMap<>();
    private final Map<UUID, UUID> byMember = new HashMap<>();
    /** Invitee -> (group id -> expiry time in epoch ms). */
    private final Map<UUID, LinkedHashMap<UUID, Long>> invites = new HashMap<>();
    /** Entries that could not be read: written back unchanged instead of being lost at the next save. */
    private final List<CompoundTag> unreadable = new ArrayList<>();

    public static GroupData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(GroupData::load, () -> SafeSavedData.fresh(server.overworld(), ID, new GroupData()), ID);
    }

    @Nullable
    public Group getById(UUID id) {
        return this.byId.get(id);
    }

    @Nullable
    public Group getByMember(UUID player) {
        UUID gid = this.byMember.get(player);
        return gid == null ? null : this.byId.get(gid);
    }

    public boolean tagTaken(String tag) {
        for (Group g : this.byId.values()) {
            if (g.tag().equalsIgnoreCase(tag)) {
                return true;
            }
        }

        return false;
    }

    public Collection<Group> all() {
        return Collections.unmodifiableCollection(this.byId.values());
    }

    public InviteResult invite(UUID invitee, UUID groupId) {
        return this.invite(invitee, groupId, System.currentTimeMillis());
    }

    /** Records an invite unless it exists already or a cap is reached. */
    public InviteResult invite(UUID invitee, UUID groupId, long now) {
        this.purgeExpired(now);
        LinkedHashMap<UUID, Long> mine = this.invites.get(invitee);
        if (mine != null && mine.containsKey(groupId)) {
            return InviteResult.ALREADY_INVITED;
        }
        if (mine != null && mine.size() >= MAX_INVITES_PER_PLAYER) {
            return InviteResult.PLAYER_FULL;
        }
        if (this.pendingInvitesOf(groupId) >= MAX_INVITES_PER_GROUP) {
            return InviteResult.GROUP_FULL;
        }
        this.invites.computeIfAbsent(invitee, k -> new LinkedHashMap<>()).put(groupId, now + INVITE_TTL_MS);
        return InviteResult.OK;
    }

    private int pendingInvitesOf(UUID groupId) {
        int n = 0;
        for (LinkedHashMap<UUID, Long> m : this.invites.values()) {
            if (m.containsKey(groupId)) {
                n++;
            }
        }
        return n;
    }

    private void purgeExpired(long now) {
        Iterator<LinkedHashMap<UUID, Long>> it = this.invites.values().iterator();
        while (it.hasNext()) {
            LinkedHashMap<UUID, Long> m = it.next();
            m.values().removeIf(expiry -> expiry <= now);
            if (m.isEmpty()) {
                it.remove();
            }
        }
    }

    public boolean hasInvite(UUID invitee, UUID groupId) {
        return this.hasInvite(invitee, groupId, System.currentTimeMillis());
    }

    public boolean hasInvite(UUID invitee, UUID groupId, long now) {
        LinkedHashMap<UUID, Long> m = this.invites.get(invitee);
        Long expiry = m == null ? null : m.get(groupId);
        return expiry != null && expiry > now;
    }

    public boolean hasAnyInvite(UUID invitee) {
        return !this.getInvites(invitee).isEmpty();
    }

    /** Group ids of the player's pending, unexpired invites, oldest first. */
    public Set<UUID> getInvites(UUID invitee) {
        long now = System.currentTimeMillis();
        LinkedHashSet<UUID> out = new LinkedHashSet<>();
        LinkedHashMap<UUID, Long> m = this.invites.get(invitee);
        if (m != null) {
            for (Map.Entry<UUID, Long> e : m.entrySet()) {
                if (e.getValue() > now) {
                    out.add(e.getKey());
                }
            }
        }
        return out;
    }

    public void removeInvite(UUID invitee, UUID groupId) {
        LinkedHashMap<UUID, Long> m = this.invites.get(invitee);
        if (m != null) {
            m.remove(groupId);
            if (m.isEmpty()) {
                this.invites.remove(invitee);
            }
        }
    }

    @Nullable
    public Group create(String name, String tag, int color, UUID owner) {
        if (this.byMember.containsKey(owner)) {
            return null;
        } else {
            int maxLen = Config.groupNameMaxLength.get();
            if (name.length() > maxLen) {
                name = name.substring(0, maxLen);
            }

            Group g = new Group(UUID.randomUUID(), name, tag, color, owner);
            this.byId.put(g.id(), g);
            this.byMember.put(owner, g.id());
            this.setDirty();
            return g;
        }
    }

    public boolean addMember(Group g, UUID player) {
        if (this.byMember.containsKey(player)) {
            return false;
        } else if (!g.addMember(player)) {
            return false;
        } else {
            this.byMember.put(player, g.id());
            this.setDirty();
            return true;
        }
    }

    public boolean removeMember(Group g, UUID player) {
        if (!g.removeMember(player)) {
            return false;
        } else {
            this.byMember.remove(player);
            this.setDirty();
            return true;
        }
    }

    /** Makes {@code member} the owner of the group; the previous owner stays a member. */
    public boolean transferOwnership(Group g, UUID member) {
        if (!g.setOwner(member)) {
            return false;
        }
        this.setDirty();
        return true;
    }

    public void disband(Group g) {
        for (UUID m : g.members()) {
            this.byMember.remove(m);
        }

        this.byId.remove(g.id());

        for (LinkedHashMap<UUID, Long> m : this.invites.values()) {
            m.remove(g.id());
        }

        this.invites.values().removeIf(Map::isEmpty);
        this.setDirty();
    }

    public void markDirty() {
        this.setDirty();
    }

    public static GroupData load(CompoundTag tag) {
        GroupData data = new GroupData();
        ListTag list = tag.getList("Groups", 10);

        for (int i = 0; i < list.size(); i++) {
            CompoundTag t = list.getCompound(i);
            Group g;
            try {
                g = Group.load(t);
            } catch (RuntimeException e) {
                Crac.LOGGER.error("[CRac] Unreadable group entry, kept unchanged: {}", t, e);
                data.unreadable.add(t);
                continue;
            }
            data.byId.put(g.id(), g);

            for (UUID m : g.members()) {
                data.byMember.put(m, g.id());
            }
        }

        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();

        for (Group g : this.byId.values()) {
            list.add(g.save());
        }

        list.addAll(this.unreadable);
        tag.put("Groups", list);
        return tag;
    }
}
