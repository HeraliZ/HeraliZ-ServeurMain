package fr.heraliz.crac.raid;

import fr.heraliz.crac.placable.PlacableChutes;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureIntegrity;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.placable.PlacableHpManager;
import fr.heraliz.crac.placable.PlacableSupport;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * World blocks that raids may break. A claim protects the building, not every block in its box: inside a claim, raid
 * explosives break ordinary blocks ({@code explosions.raidBreaksBlocks}), so a claimed house, bunker or cave wall of
 * vanilla blocks is no raid shield. Never broken this way: natural terrain ({@link #TERRAIN}, unless a player put it
 * there, see {@link PlacedTerrainData}), the ground under a foundation, building pieces, placables (they take HP damage
 * instead), the Claim Block and unbreakable blocks. Outside claims explosions break no world block (anyone may mine
 * there).
 */
public final class RaidBlocks {
    /** Natural terrain that raids leave alone inside claims (data/crac/tags/blocks/raid_terrain.json). */
    public static final TagKey<Block> TERRAIN = BlockTags.create(new ResourceLocation("crac", "raid_terrain"));
    /** Climbing and crawling aids anyone may place and use in a base under raid block (data/crac/tags/blocks/raid_access.json). */
    public static final TagKey<Block> ACCESS = BlockTags.create(new ResourceLocation("crac", "raid_access"));
    /** How many falling blocks (sand, gravel...) stacked on a block {@link #holdsUpFoundation} looks through. */
    private static final int MAX_FALLING_STACK = 64;

    private RaidBlocks() {
    }

    /** Whether a raid explosive may break this block. */
    public static boolean breakable(ServerLevel level, BlockPos pos, BlockState state) {
        if (!Config.raidBreaksBlocks.get() || state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        Block block = state.getBlock();
        if (block instanceof StructureBlock || block instanceof ClaimBlock || PlacableHpManager.isTracked(state)) {
            return false;
        }
        if (ClaimManager.getAt(level, pos) == null) {
            return false;
        }
        // base d'un nouveau joueur encore protégée (monde des bases, 1.5.0) : aucun bloc cassé par un explosif
        if (fr.heraliz.crac.bases.Protections.baseProtegee(level, pos)) {
            return false;
        }
        if (state.is(TERRAIN) && !PlacedTerrainData.get(level).contains(pos)) {
            return false;
        }
        // a foundation with no ground left collapses with all it carries: a raid goes through the pieces' HP instead
        return !holdsUpFoundation(level, pos);
    }

    /**
     * Whether removing the block at {@code pos} would take a foundation's ground away: it is the ground of one of its
     * columns, or it carries a stack of falling blocks (sand, gravel...) that ends in such a ground.
     */
    public static boolean holdsUpFoundation(ServerLevel level, BlockPos pos) {
        StructureStore store = StructureStore.get(level);
        BlockPos cur = pos;
        for (int i = 0; i < MAX_FALLING_STACK; i++) {
            if (StructureIntegrity.isFoundationGround(store, cur)) {
                return true;
            }
            cur = cur.above();
            if (!level.isLoaded(cur) || !(level.getBlockState(cur).getBlock() instanceof Fallable)) {
                return false;
            }
        }
        return false;
    }

    /**
     * Whether anyone may place or use {@code state} in {@code claim}: a climbing or crawling aid ({@link #ACCESS}: ladders,
     * scaffolding, vines, wooden trapdoors) while the claim's group is under raid block, so that no way into a base (a
     * ledge, a gap one only gets through crawling) is its owners' alone.
     */
    public static boolean isRaidAccess(ServerLevel level, Claim claim, BlockState state) {
        return state.is(ACCESS) && RaidBlockManager.isRaidBlocked(level, claim.groupId());
    }

    /**
     * C4 or Explosive Charge stuck to the block at {@code center}: breaks what a raid may break within {@code radius}
     * of it (0: that block only) and returns how many blocks went. Their items drop as if mined.
     */
    public static int breakAround(ServerLevel level, BlockPos center, double radius) {
        int r = (int) Math.ceil(radius);
        double r2 = radius * radius + 1.0E-6;
        List<BlockPos> broken = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(center.offset(-r, -r, -r), center.offset(r, r, r))) {
            if (center.distSqr(p) > r2 || !level.isLoaded(p)) continue;
            BlockPos pos = p.immutable();
            if (breakable(level, pos, level.getBlockState(pos)) && level.destroyBlock(pos, true)) {
                broken.add(pos);
            }
        }
        blownAway(level, broken);
        return broken.size();
    }

    /**
     * A blast's block list (TNT, beds, end crystals, other mods' grenades...), worked out with its usual resistances
     * (obsidian shrugs off TNT). Inside claims only what a raid may break is kept, and nothing when {@code raid} is false
     * (a mob caused it: creepers and ghasts do not grief claims). Outside claims the list is left to vanilla and the other
     * mods (HeraliZ: the map has its own protection), except CRac's own blocks: pieces, placables and the Claim Block
     * only ever lose HP.
     */
    public static void keepBreakable(ServerLevel level, List<BlockPos> blocks, boolean raid) {
        blocks.removeIf(p -> {
            BlockState state = level.getBlockState(p);
            if (ClaimManager.getAt(level, p) == null && !fr.heraliz.crac.heraliz.Heraliz.cracRulesOutsideClaims()) {
                return isCracBlock(state);
            }
            return !raid || !breakable(level, p, state);
        });
        if (fr.heraliz.crac.heraliz.Heraliz.cracRulesOutsideClaims()) {
            blownAway(level, blocks);
            return;
        }
        List<BlockPos> inClaims = new ArrayList<>();
        for (BlockPos p : blocks) {
            if (ClaimManager.getAt(level, p) != null) {
                inClaims.add(p);
            }
        }
        blownAway(level, inClaims);
    }

    /** A block of the building system that only takes damage through HP: a piece, the Claim Block. */
    public static boolean isCracBlock(BlockState state) {
        Block block = state.getBlock();
        return block instanceof StructureBlock || block instanceof ClaimBlock;
    }

    /**
     * These blocks are being blown away: their placed-terrain records go with them, breaking into a claim starts its
     * raid block like damage to its pieces does, and the placables they held up fall once they are gone.
     */
    public static void blownAway(ServerLevel level, Collection<? extends BlockPos> blocks) {
        if (blocks.isEmpty()) {
            return;
        }
        PlacedTerrainData data = PlacedTerrainData.get(level);
        BlockPos inClaim = null;
        List<BlockPos> gone = new ArrayList<>(blocks.size());
        for (BlockPos p : blocks) {
            data.remove(p);
            gone.add(p.immutable());
            if (inClaim == null && ClaimManager.getAt(level, p) != null) {
                inClaim = p;
            }
        }
        if (inClaim != null) {
            RaidBlockManager.trigger(level, inClaim);
        }
        // vanilla and SuperbWarfare remove their blocks after this call: the support check waits until they are gone
        MinecraftServer server = level.getServer();
        server.tell(new TickTask(server.getTickCount(), () -> {
            for (BlockPos p : gone) {
                PlacableChutes.dropUnsupportedAbove(level, p);
            }
        }));
    }
}
