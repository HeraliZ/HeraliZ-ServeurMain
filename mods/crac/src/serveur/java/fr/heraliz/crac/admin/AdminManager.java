package fr.heraliz.crac.admin;

import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.Map.Entry;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.logic.PieceIndex;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.S2CAdminData;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacablePositionData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.ForgeConfigSpec.ConfigValue;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.network.PacketDistributor;

@EventBusSubscriber(
    modid = "crac"
)
public final class AdminManager {
    private static final TagKey<Block> PLACABLE = BlockTags.create(new ResourceLocation("crac", "placable"));
    /** Server-thread only (every admin packet is handled through enqueueWork). */
    private static final AdminAuth AUTH = new AdminAuth();
    /** Largest zone (in blocks) whose pieces are looked up block by block; beyond it the level's pieces are walked. */
    private static final long BLOCK_LOOKUP_LIMIT = 1_000_000L;

    private AdminManager() {
    }

    private static boolean permitted(ServerPlayer p) {
        return p.hasPermissions(AdminConfig.permissionLevel.get());
    }

    /**
     * Whether the player holds a valid admin session: logged in with the current password and still operator. Called by
     * every admin packet. A session that just ended closes the client's panel; a player who never had one gets no
     * answer, so spamming admin packets without a session costs nothing.
     */
    public static boolean isAuthed(ServerPlayer p) {
        boolean had = AUTH.hasSession(p.getUUID());
        boolean ok = AUTH.isAuthed(p.getUUID(), permitted(p), AdminConfig.consolePassword.get());
        if (!ok && had) {
            log(p, "admin session ended (password changed, console disabled or operator level lost)");
            p.displayClientMessage(Component.literal("§c[Admin] Your admin session has ended."), false);
            denyLogin(p);
        }
        return ok;
    }

    /** Handles a console login: operator level first, then lockout, then the password. Every attempt is logged. */
    public static void login(ServerPlayer p, String password) {
        long now = System.currentTimeMillis();
        long lockoutMs = AdminConfig.lockoutSeconds.get() * 1000L;
        AdminAuth.Result result = AUTH.login(
            p.getUUID(), permitted(p), AdminConfig.consolePassword.get(), password, now, AdminConfig.maxFailedLogins.get(), lockoutMs
        );
        String reason = switch (result) {
            case OK -> null;
            case DISABLED -> "The admin console is disabled on this server.";
            case NO_PERMISSION -> "You need operator level " + AdminConfig.permissionLevel.get() + " to use the admin console.";
            case LOCKED -> "Too many wrong passwords. Try again in " + (AUTH.lockoutLeft(p.getUUID(), now) + 999L) / 1000L + "s.";
            case WRONG_PASSWORD -> "Wrong password.";
        };
        if (reason == null) {
            log(p, "logged in to the admin console");
            sendSnapshot(p);
        } else {
            if (result == AdminAuth.Result.WRONG_PASSWORD || result == AdminAuth.Result.LOCKED) {
                Crac.LOGGER.warn("[CRac admin] Refused console login for {} ({}): {}", p.getGameProfile().getName(), p.getUUID(), result);
            } else {
                Crac.LOGGER.info("[CRac admin] Refused console login for {} ({}): {}", p.getGameProfile().getName(), p.getUUID(), result);
            }
            p.displayClientMessage(Component.literal("§c[Admin] " + reason), false);
            denyLogin(p);
        }
    }

    /** Audit line for an admin action. */
    public static void log(ServerPlayer admin, String what) {
        Crac.LOGGER.info("[CRac admin] {} ({}) {}", admin.getGameProfile().getName(), admin.getUUID(), what);
    }

    /** A group as audit lines name it: {@code Name [TAG] (uuid)}. */
    public static String groupLabel(MinecraftServer server, UUID id) {
        Group g = GroupData.get(server).getById(id);
        return g == null ? "unknown group (" + id + ")" : g.name() + " [" + g.tag() + "] (" + id + ")";
    }

    /** A player as audit lines name it: {@code name (uuid)}. */
    public static String playerLabel(MinecraftServer server, UUID id) {
        return nameFor(server, id) + " (" + id + ")";
    }

    public static void sendSnapshot(ServerPlayer admin) {
        MinecraftServer server = admin.getServer();
        if (server != null) {
            GroupData gd = GroupData.get(server);
            List<S2CAdminData.GroupInfo> groups = new ArrayList<>();

            for (Group g : gd.all()) {
                List<S2CAdminData.MemberInfo> members = new ArrayList<>();

                for (UUID m : g.members()) {
                    ServerPlayer online = server.getPlayerList().getPlayer(m);
                    String name = online != null ? online.getGameProfile().getName() : nameFor(server, m);
                    members.add(new S2CAdminData.MemberInfo(m, name, online != null, g.isOwner(m)));
                }

                // the claim may be in any dimension, not only the admin's
                Claim gc = ClaimManager.getByGroupAnywhere(server, g.id());
                groups.add(new S2CAdminData.GroupInfo(g.id(), g.name(), g.tag(), gc != null ? gc.tier() : -1, members));
            }

            List<S2CAdminData.ClaimInfo> claims = new ArrayList<>();

            for (ServerLevel level : server.getAllLevels()) {
                String dimension = level.dimension().location().toString();
                for (Claim c : ClaimData.get(level).all()) {
                    Group g = gd.getById(c.groupId());
                    Building b = StructureService.buildingOfClaim(level, c.anchor());
                    int pieces = b == null ? 0 : b.pieces.size();
                    claims.add(new S2CAdminData.ClaimInfo(
                        c.groupId(), g != null ? g.name() : "?", c.tier(), c.center().getX(), c.center().getZ(), c.radius(), pieces, dimension
                    ));
                }
            }

            List<S2CAdminData.PlayerInfo> players = new ArrayList<>();

            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                Group g = gd.getByMember(p.getUUID());
                players.add(
                    new S2CAdminData.PlayerInfo(
                        p.getUUID(),
                        p.getGameProfile().getName(),
                        g != null ? g.name() : "",
                        p.getBlockX(),
                        p.getBlockY(),
                        p.getBlockZ(),
                        p.level().dimension().location().toString()
                    )
                );
            }

            List<S2CAdminData.ConfigInfo> config = new ArrayList<>();

            for (Entry<String, ConfigValue<?>> e : AdminConfigAccess.values().entrySet()) {
                String value = AdminConfigAccess.asString(e.getValue());
                if (value.length() > S2CAdminData.MAX_VALUE_CHARS) {
                    Crac.LOGGER.warn("[CRac admin] {} is too long to show in the admin panel; edit it in the config file", e.getKey());
                    continue;
                }
                config.add(new S2CAdminData.ConfigInfo(e.getKey(), AdminConfigAccess.typeOf(e.getValue()), value));
            }

            List<S2CAdminData.PlacableInfo> placables = new ArrayList<>();

            for (Entry<ResourceLocation, PlacableHpManager.Entry> e : PlacableHpManager.entries().entrySet()) {
                placables.add(new S2CAdminData.PlacableInfo(e.getKey().toString(), e.getValue().hp(), e.getValue().enabled()));
            }

            NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> admin), new S2CAdminData(true, groups, claims, players, config, placables));
        }
    }

    public static void denyLogin(ServerPlayer p) {
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), new S2CAdminData(false, List.of(), List.of(), List.of(), List.of(), List.of()));
    }

    private static String nameFor(MinecraftServer server, UUID id) {
        GameProfile profile = server.getProfileCache() != null ? server.getProfileCache().get(id).orElse(null) : null;
        return profile != null ? profile.getName() : id.toString().substring(0, 8);
    }

    public static void kickMember(ServerPlayer admin, UUID groupId, UUID member) {
        MinecraftServer server = admin.getServer();
        if (server != null) {
            GroupData gd = GroupData.get(server);
            Group g = gd.getById(groupId);
            if (g != null && !g.isOwner(member) && gd.removeMember(g, member)) {
                // only the kicked player's view of the claims changes
                C2SRequestNearbyClaimsTraitement.pushTo(server, List.of(member));
            }
        }
    }

    /** Hands a group over to one of its members (an owner who left for good no longer freezes the group). */
    public static boolean setOwner(ServerPlayer admin, UUID groupId, UUID member) {
        MinecraftServer server = admin.getServer();
        if (server == null) {
            return false;
        }
        GroupData gd = GroupData.get(server);
        Group g = gd.getById(groupId);
        return g != null && gd.transferOwnership(g, member);
    }

    public static void disbandGroup(ServerPlayer admin, UUID groupId) {
        MinecraftServer server = admin.getServer();
        if (server != null) {
            GroupData gd = GroupData.get(server);
            Group g = gd.getById(groupId);
            if (g != null) {
                releaseClaim(server, groupId);
                gd.disband(g);
            }
        }
    }

    public static void unclaim(ServerPlayer admin, UUID groupId) {
        MinecraftServer server = admin.getServer();
        if (server != null) {
            releaseClaim(server, groupId);
        }
    }

    /** Releases the group's claim in whichever level it is, and refreshes the claim view of that level's players. */
    private static void releaseClaim(MinecraftServer server, UUID groupId) {
        for (ServerLevel level : server.getAllLevels()) {
            if (ClaimManager.releaseClaim(level, groupId)) {
                C2SRequestNearbyClaimsTraitement.pushAll(level);
            }
        }
    }

    /** Removes the building pieces and placable blocks of the group's claim, in whichever level it is. */
    public static int wipeClaim(ServerPlayer admin, UUID groupId) {
        for (ServerLevel level : admin.server.getAllLevels()) {
            Claim claim = ClaimManager.getByGroup(level, groupId);
            if (claim != null) {
                return wipe(level, claim);
            }
        }
        return 0;
    }

    private static int wipe(ServerLevel level, Claim claim) {
        int removed = 0;
        int r = claim.radius();
        int yr = claim.yRadius();
        BlockPos c = claim.anchor();
        Set<Piece> pieces = new LinkedHashSet<>();
        Building claimed = StructureService.buildingOfClaim(level, c);
        if (claimed != null) {
            pieces.addAll(StructureStore.get(level).piecesOf(claimed));
        }
        BlockPos min = new BlockPos(c.getX() - r, Math.max(level.getMinBuildHeight(), c.getY() - yr), c.getZ() - r);
        BlockPos max = new BlockPos(c.getX() + r, Math.min(level.getMaxBuildHeight() - 1, c.getY() + yr), c.getZ() + r);
        pieces.addAll(piecesIn(level, min, max));
        for (Piece p : pieces) {
            if (StructureStore.get(level).index.piece(p.id) != null) {
                StructureService.remove(level, p, StructureService.Cause.ADMIN);
                removed++;
            }
        }
        return removed + removePlacables(level, min, max);
    }

    /**
     * The pieces with a block between {@code min} and {@code max} (inclusive). A small zone is looked up block by block;
     * a large one (the config allows claims of over 500 million blocks) walks the level's pieces instead.
     */
    public static Collection<Piece> piecesIn(ServerLevel level, BlockPos min, BlockPos max) {
        long volume = (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1);
        if (volume <= BLOCK_LOOKUP_LIMIT) {
            return StructureService.piecesIn(level, new AABB(min.getX(), min.getY(), min.getZ(), max.getX(), max.getY(), max.getZ()));
        }
        List<Piece> out = new ArrayList<>();
        for (Piece p : StructureStore.get(level).index.allPieces()) {
            for (BlockPos pos : PieceIndex.blocksOf(p)) {
                if (pos.getX() >= min.getX() && pos.getX() <= max.getX() && pos.getY() >= min.getY() && pos.getY() <= max.getY()
                    && pos.getZ() >= min.getZ() && pos.getZ() <= max.getZ()) {
                    out.add(p);
                    break;
                }
            }
        }
        return out;
    }

    /**
     * Removes every placable block between {@code min} and {@code max} (inclusive) and returns how many. Chunk sections
     * whose palette holds no placable are skipped, and an unloaded chunk is only loaded when a player placed a placable
     * in it, so even the largest claim the config allows is wiped without a long freeze.
     */
    public static int removePlacables(ServerLevel level, BlockPos min, BlockPos max) {
        Set<Long> placedChunks = new HashSet<>();
        for (long packed : PlacablePositionData.get(level).snapshot()) {
            BlockPos p = BlockPos.of(packed);
            if (p.getX() >= min.getX() && p.getX() <= max.getX() && p.getY() >= min.getY() && p.getY() <= max.getY()
                && p.getZ() >= min.getZ() && p.getZ() <= max.getZ()) {
                placedChunks.add(ChunkPos.asLong(SectionPos.blockToSectionCoord(p.getX()), SectionPos.blockToSectionCoord(p.getZ())));
            }
        }
        List<BlockPos> found = new ArrayList<>();
        for (int cx = SectionPos.blockToSectionCoord(min.getX()); cx <= SectionPos.blockToSectionCoord(max.getX()); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(min.getZ()); cz <= SectionPos.blockToSectionCoord(max.getZ()); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    if (!placedChunks.contains(ChunkPos.asLong(cx, cz))) {
                        continue;
                    }
                    chunk = level.getChunk(cx, cz);
                }
                collectPlacables(chunk, min, max, found);
            }
        }
        // collected first: removing one half of a bed or door also removes the other
        for (BlockPos pos : found) {
            if (PlacableHpManager.isTracked(level.getBlockState(pos))) {
                level.removeBlock(pos, false);
            }
            PlacableHpManager.clear(level, pos);
        }
        return found.size();
    }

    private static void collectPlacables(LevelChunk chunk, BlockPos min, BlockPos max, List<BlockPos> out) {
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        int x0 = Math.max(min.getX(), baseX);
        int x1 = Math.min(max.getX(), baseX + 15);
        int z0 = Math.max(min.getZ(), baseZ);
        int z1 = Math.min(max.getZ(), baseZ + 15);
        for (int sy = SectionPos.blockToSectionCoord(min.getY()); sy <= SectionPos.blockToSectionCoord(max.getY()); sy++) {
            int index = chunk.getSectionIndexFromSectionY(sy);
            if (index < 0 || index >= chunk.getSectionsCount()) {
                continue;
            }
            LevelChunkSection section = chunk.getSection(index);
            if (section.hasOnlyAir() || !section.maybeHas(PlacableHpManager::isTracked)) {
                continue;
            }
            int y0 = Math.max(min.getY(), SectionPos.sectionToBlockCoord(sy));
            int y1 = Math.min(max.getY(), SectionPos.sectionToBlockCoord(sy) + 15);
            for (int x = x0; x <= x1; x++) {
                for (int y = y0; y <= y1; y++) {
                    for (int z = z0; z <= z1; z++) {
                        if (PlacableHpManager.isTracked(section.getBlockState(x & 15, y & 15, z & 15))) {
                            out.add(new BlockPos(x, y, z));
                        }
                    }
                }
            }
        }
    }

    public static void teleportTo(ServerPlayer admin, UUID target) {
        MinecraftServer server = admin.getServer();
        if (server != null) {
            ServerPlayer t = server.getPlayerList().getPlayer(target);
            if (t != null) {
                admin.teleportTo(t.serverLevel(), t.getX(), t.getY(), t.getZ(), t.getYRot(), t.getXRot());
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent event) {
        AUTH.logout(event.getEntity().getUUID());
    }

    /** The password moved out of crac-server.toml (which every client receives): say where it is now. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!AdminConfig.SPEC.isLoaded()) {
            return;
        }
        if (AdminConfig.consolePassword.get().isBlank()) {
            Crac.LOGGER.info("[CRac admin] Admin console disabled: set admin.consolePassword in config/crac-admin.toml to enable it");
        } else {
            Crac.LOGGER.info("[CRac admin] Admin console enabled for operator level {} and above", AdminConfig.permissionLevel.get());
        }
    }

    /** An integrated server can be restarted in the same JVM: sessions and lockouts never carry over. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        AUTH.clear();
    }
}
