package fr.heraliz.crac.placable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.raid.RaidBlockManager;
import fr.heraliz.crac.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedOutEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent.RightClickBlock;
import net.minecraftforge.event.level.BlockEvent.BreakEvent;
import net.minecraftforge.event.level.BlockEvent.EntityMultiPlaceEvent;
import net.minecraftforge.event.level.BlockEvent.EntityPlaceEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;

@EventBusSubscriber(
    modid = "crac"
)
public final class PlacableHpManager {
    private static final TagKey<Block> PLACABLE = BlockTags.create(new ResourceLocation("crac", "placable"));
    private static final int DEFAULT_HP = 100;
    private static final String FILE_NAME = "crac-placable-hp.txt";
    /** Item tag holding the HP of a placable picked up while damaged. */
    public static final String ITEM_HP_TAG = PlacableSet.ITEM_HP_TAG;
    /** Written in the file once the version 2 migration ran (vanilla beds and respawn anchors disabled). */
    private static final String FORMAT_MARKER = "# format 2";
    private static final Map<ResourceLocation, PlacableHpManager.Entry> ENTRIES = new LinkedHashMap<>();
    /** Hand each player last started a block use with (server thread), read once by the place event that follows. */
    private static final Map<UUID, InteractionHand> PLACING_HAND = new HashMap<>();
    private static Path filePath;

    private PlacableHpManager() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        filePath = server.getWorldPath(LevelResource.ROOT).resolve("serverconfig").resolve("crac-placable-hp.txt");
        ENTRIES.clear();
        ENTRIES.putAll(readFile(filePath));
        if (!hasMarker(filePath)) {
            retireSpawnBlocks();
        }
        ResourceLocation claimId = ForgeRegistries.BLOCKS.getKey(ModBlocks.CLAIM_BLOCK.get());
        if (claimId != null) {
            ENTRIES.putIfAbsent(claimId, new PlacableHpManager.Entry(DEFAULT_HP, true));
        }

        for (Block block : ForgeRegistries.BLOCKS.tags().getTag(PLACABLE)) {
            if (!isBuildSystemBlock(block)) {
                ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
                if (id != null) {
                    ENTRIES.putIfAbsent(id, new PlacableHpManager.Entry(DEFAULT_HP, true));
                }
            }
        }

        writeFile();
        PlacableSet.setServerSide(enabledBlocks());
        Crac.LOGGER.info("CRac placable register: {} entries -> {}", ENTRIES.size(), filePath);
    }

    private static boolean hasMarker(Path file) {
        try {
            return Files.exists(file) && Files.readAllLines(file, StandardCharsets.UTF_8).stream().anyMatch(l -> l.trim().equals(FORMAT_MARKER));
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Vanilla beds and respawn anchors used to be seeded as placables. They give free, ownerless respawn points
     * (or a raider-triggered blast), so files written before format 2 get them disabled once. An admin may turn
     * them back on afterwards.
     */
    private static void retireSpawnBlocks() {
        for (Map.Entry<ResourceLocation, PlacableHpManager.Entry> e : ENTRIES.entrySet()) {
            Block b = ForgeRegistries.BLOCKS.getValue(e.getKey());
            boolean spawnBlock = b instanceof RespawnAnchorBlock || b instanceof BedBlock;
            if (spawnBlock && e.getValue().enabled()) {
                e.setValue(new PlacableHpManager.Entry(e.getValue().hp(), false));
                Crac.LOGGER.info("CRac placable register: disabled {} (respawn block)", e.getKey());
            }
        }
    }

    private static Map<ResourceLocation, PlacableHpManager.Entry> readFile(Path file) {
        Map<ResourceLocation, PlacableHpManager.Entry> out = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return out;
        } else {
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    line = line.trim();
                    if (!line.isEmpty() && !line.startsWith("#")) {
                        boolean enabled = true;
                        if (line.startsWith("-")) {
                            enabled = false;
                            line = line.substring(1).trim();
                        }

                        int sep = line.indexOf(61);
                        if (sep > 0) {
                            ResourceLocation id = ResourceLocation.tryParse(line.substring(0, sep).trim());
                            if (id != null) {
                                try {
                                    out.put(id, new PlacableHpManager.Entry(Math.max(1, Integer.parseInt(line.substring(sep + 1).trim())), enabled));
                                } catch (NumberFormatException var8) {
                                }
                            }
                        }
                    }
                }
            } catch (IOException var9) {
                Crac.LOGGER.error("Failed to read {}", file, var9);
            }

            return out;
        }
    }

    private static void writeFile() {
        if (filePath != null) {
            List<String> lines = new ArrayList<>();
            lines.add("# CRac placable blocks");
            lines.add("# This file, not the crac:placable tag, decides what counts as placable.");
            lines.add("# The tag only seeds it: blocks new to the tag are appended on server start.");
            lines.add("# Format: modid:block = hp     (a leading '-' disables the block)");
            lines.add(FORMAT_MARKER);

            for (Map.Entry<ResourceLocation, PlacableHpManager.Entry> e : ENTRIES.entrySet()) {
                lines.add((e.getValue().enabled() ? "" : "-") + e.getKey() + " = " + e.getValue().hp());
            }

            try {
                Files.createDirectories(filePath.getParent());
                Files.write(filePath, lines, StandardCharsets.UTF_8);
            } catch (IOException var3) {
                Crac.LOGGER.error("Failed to write {}", filePath, var3);
            }
        }
    }

    public static Map<ResourceLocation, PlacableHpManager.Entry> entries() {
        return new LinkedHashMap<>(ENTRIES);
    }

    public static boolean isEnabled(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) {
            return false;
        } else {
            PlacableHpManager.Entry e = ENTRIES.get(id);
            return e != null && e.enabled();
        }
    }

    private static List<Block> enabledBlocks() {
        Block claim = ModBlocks.CLAIM_BLOCK.get();
        List<Block> out = new ArrayList<>();

        for (Map.Entry<ResourceLocation, PlacableHpManager.Entry> e : ENTRIES.entrySet()) {
            if (e.getValue().enabled()) {
                Block b = ForgeRegistries.BLOCKS.getValue(e.getKey());
                if (b != null && b != claim) {
                    out.add(b);
                }
            }
        }

        return out;
    }

    public static void applyFromPanel(MinecraftServer server, Map<ResourceLocation, PlacableHpManager.Entry> newEntries) {
        ENTRIES.clear();
        ENTRIES.putAll(newEntries);
        writeFile();
        PlacableSet.setServerSide(enabledBlocks());
        PlacableSet.syncToAll(server);
    }

    private static boolean isBuildSystemBlock(Block block) {
        return block instanceof StructureBlock;
    }

    public static boolean isTracked(BlockState state) {
        return isEnabled(state.getBlock()) && !isBuildSystemBlock(state.getBlock());
    }

    public static int maxHpFor(Block block) {
        ResourceLocation id = ForgeRegistries.BLOCKS.getKey(block);
        if (id == null) {
            return 100;
        } else {
            PlacableHpManager.Entry e = ENTRIES.get(id);
            return e == null ? DEFAULT_HP : e.hp();
        }
    }

    public static void damage(ServerLevel level, BlockPos pos, int amount) {
        apply(level, pos, amount, true);
    }

    /** Explosive damage; {@code raid} false for blasts that must not start raid block (mob explosions). */
    public static void damage(ServerLevel level, BlockPos pos, int amount, boolean raid) {
        apply(level, pos, amount, raid);
    }

    public static void decayDamage(ServerLevel level, BlockPos pos, int amount) {
        apply(level, pos, amount, false);
    }

    private static void apply(ServerLevel level, BlockPos pos, int amount, boolean triggerRaid) {
        // base d'un nouveau joueur encore protégée (monde des bases, 1.5.0) : ses objets posés ne perdent rien
        if (amount > 0 && fr.heraliz.crac.bases.Protections.baseProtegee(level, pos)) {
            return;
        }
        if (amount > 0) {
            BlockState state = level.getBlockState(pos);
            if (isTracked(state)) {
                if (triggerRaid) {
                    RaidBlockManager.trigger(level, pos);
                }

                // both halves of a bed or door share one entry, so a blast covering both hits it once
                BlockPos key = MultiBlocks.canonical(state, pos);
                PlacableHpData data = PlacableHpData.get(level);
                int hp = hpOf(data, key, state) - amount;
                if (hp <= 0) {
                    BlockPos other = MultiBlocks.partnerIn(level, state, pos);
                    forget(level, state, pos);
                    spillShulker(level, pos);
                    MultiBlocks.remove(level, pos, true);
                    PlacableChutes.dropUnsupportedAbove(level, pos);
                    if (other != null) {
                        PlacableChutes.dropUnsupportedAbove(level, other);
                    }
                } else {
                    data.setHp(key, hp);
                }
            }
        }
    }

    /** Current HP of the placable keyed at {@code key}, never above its configured maximum. */
    private static int hpOf(PlacableHpData data, BlockPos key, BlockState state) {
        int max = maxHpFor(state.getBlock());
        return Math.min(max, data.getHp(key, max));
    }

    /** Current HP of the placable at {@code pos} (both halves of a two-block one share it). */
    public static int currentHp(ServerLevel level, BlockState state, BlockPos pos) {
        return hpOf(PlacableHpData.get(level), MultiBlocks.canonical(state, pos), state);
    }

    /** Whether the placable at {@code pos} has lost HP. */
    public static boolean isDamaged(ServerLevel level, BlockState state, BlockPos pos) {
        return isTracked(state) && currentHp(level, state, pos) < maxHpFor(state.getBlock());
    }

    /**
     * The item a placable goes back to when picked up. A damaged one remembers its HP in the item, so putting it
     * down again does not heal it (see {@link #onPlace}).
     */
    public static ItemStack pickupItem(ServerLevel level, BlockState state, BlockPos pos) {
        ItemStack stack = new ItemStack(state.getBlock());
        if (isDamaged(level, state, pos)) {
            stack.getOrCreateTag().putInt(ITEM_HP_TAG, currentHp(level, state, pos));
        }
        return stack;
    }

    /**
     * Records the hand a player is about to place from: the block use event (vanilla placement) and the adventure
     * placement packet both say it, the place event that follows does not.
     */
    public static void notePlacingHand(Player player, InteractionHand hand) {
        PLACING_HAND.put(player.getUUID(), hand);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public static void onRightClickBlock(RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            notePlacingHand(player, event.getHand());
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerLoggedOutEvent event) {
        PLACING_HAND.remove(event.getEntity().getUUID());
    }

    /** HP the item stands for: its remembered HP, or {@code max} for a fresh one; -1 if it does not place {@code block}. */
    private static int itemHp(ItemStack stack, Block block, int max) {
        if (!(stack.getItem() instanceof BlockItem bi) || bi.getBlock() != block) {
            return -1;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && tag.contains(ITEM_HP_TAG, Tag.TAG_INT) ? Math.max(1, Math.min(max, tag.getInt(ITEM_HP_TAG))) : max;
    }

    /**
     * HP of the stack the player placed {@code block} from (the item still holds its pre-placement data during the
     * place event). When the hand is unknown and both hands hold that block, the lower HP wins: a fresh copy in the
     * other hand must never heal a damaged one.
     */
    private static int carriedHp(Player player, Block block, int max) {
        InteractionHand used = PLACING_HAND.remove(player.getUUID());
        if (used != null) {
            int hp = itemHp(player.getItemInHand(used), block, max);
            if (hp > 0) {
                return hp;
            }
        }
        int lowest = max;
        for (InteractionHand hand : InteractionHand.values()) {
            int hp = itemHp(player.getItemInHand(hand), block, max);
            if (hp > 0) {
                lowest = Math.min(lowest, hp);
            }
        }
        return lowest;
    }

    /** Shulker boxes only give their contents back through their loot table, which a raid kill or a fall skips. */
    static void spillShulker(ServerLevel level, BlockPos pos) {
        if (level.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity box) {
            Containers.dropContents(level, pos, box);
            box.clearContent();
        }
    }

    public static void clear(ServerLevel level, BlockPos pos) {
        PlacableHpData.get(level).remove(pos);
        PlacablePositionData.get(level).remove(pos);
    }

    /** Drops the HP and position entries of the placable at {@code pos} (both halves of a two-block one). */
    public static void forget(ServerLevel level, BlockState state, BlockPos pos) {
        clear(level, pos);
        clear(level, MultiBlocks.canonical(state, pos));
        BlockPos other = MultiBlocks.partner(state, pos);
        if (other != null) {
            clear(level, other);
        }
    }

    /** Lowest priority: only placements every protection check let through (a cancelled one never reaches it). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPlace(EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            // a new block starts at full HP whatever stood there before
            List<BlockPos> cells = cells(event);
            PlacableHpData hp = PlacableHpData.get(level);
            for (BlockPos cell : cells) {
                hp.remove(cell);
            }
            BlockState placed = event.getPlacedBlock();
            if (isTracked(placed)) {
                BlockPos key = MultiBlocks.canonical(placed, event.getPos());
                PlacablePositionData data = PlacablePositionData.get(level);
                data.add(key);
                if (ClaimManager.getAt(level, key) != null) {
                    data.markClaimed(key);
                }
                int max = maxHpFor(placed.getBlock());
                int carried = event.getEntity() instanceof Player player ? carriedHp(player, placed.getBlock(), max) : max;
                if (carried < max) {
                    hp.setHp(key, carried);
                }
            }
        }
    }

    /** Every block a placement wrote: both halves of a bed or a door, else the single block. */
    public static List<BlockPos> cells(EntityPlaceEvent event) {
        if (event instanceof EntityMultiPlaceEvent multi) {
            List<BlockPos> out = new ArrayList<>();
            for (BlockSnapshot snapshot : multi.getReplacedBlockSnapshots()) {
                out.add(snapshot.getPos().immutable());
            }
            if (!out.isEmpty()) {
                return out;
            }
        }
        return List.of(event.getPos().immutable());
    }

    /** Mined, broken by a mob or another mod's tool: the entry must not outlive the block. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level && isTracked(event.getState())) {
            BlockState state = event.getState();
            BlockPos pos = event.getPos().immutable();
            forget(level, state, pos);
            BlockPos other = MultiBlocks.partner(state, pos);
            // the event comes before the block is gone: what stood on it falls once the break is done
            MinecraftServer server = level.getServer();
            server.tell(new TickTask(server.getTickCount(), () -> {
                PlacableChutes.dropUnsupportedAbove(level, pos);
                if (other != null) {
                    PlacableChutes.dropUnsupportedAbove(level, other);
                }
            }));
        }
    }

    public record Entry(int hp, boolean enabled) {
    }
}
