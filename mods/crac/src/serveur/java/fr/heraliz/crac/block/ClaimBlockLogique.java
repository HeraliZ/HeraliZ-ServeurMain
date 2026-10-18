package fr.heraliz.crac.block;

import fr.heraliz.crac.heraliz.Txt;
import fr.heraliz.crac.heraliz.ClanAccess;
import java.util.UUID;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.claim.ClaimUpkeep;
import fr.heraliz.crac.event.ClaimBlockHandler;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.BlockBehaviour.Properties;
import net.minecraft.world.level.block.state.StateDefinition.Builder;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;

/**
 * Logique serveur du Claim Block (corps d'origine de ClaimBlock.use / onRemove) : claim perdu restauré, droits du
 * groupe et du clan, amélioration de tier, ouverture du stock d'entretien.
 */
public final class ClaimBlockLogique {
    private ClaimBlockLogique() {
    }

    public static InteractionResult utiliser(ClaimBlock bloc, BlockState state, ServerLevel sl, BlockPos pos, ServerPlayer sp, InteractionHand hand) {
            Claim claim = ClaimManager.getByAnchor(sl, pos);
            if (claim == null) {
                claim = restoreClaim(sl, sp, pos, state);
            }
            if (claim == null) {
                return InteractionResult.CONSUME;
            } else {
                Group g = GroupData.get(sl.getServer()).getById(claim.groupId());
                boolean member = ClanAccess.allows(g, sp.getUUID(), ClanAccess.Action.CHESTS);
                if (!member && !sp.getAbilities().instabuild) {
                    sp.displayClientMessage(Txt.red("crac.claim.not_yours"), true);
                    return InteractionResult.CONSUME;
                } else {
                    ItemStack held = sp.getItemInHand(hand);
                    boolean owner = g != null && (g.isOwner(sp.getUUID()) || ClanAccess.viaClan(g, sp.getUUID(), ClanAccess.Action.MANAGE));
                    if (owner && claim.tier() < 3 && tryUpgrade(sl, sp, pos, state, claim, held)) {
                        return InteractionResult.CONSUME;
                    } else {
                        // HeraliZ : pendant le tutoriel des bases, l'entretien attend la cabane en bois (planches comptées)
                        if (fr.heraliz.crac.bases.Tutoriel.entretienTropTot(sp)) {
                            return InteractionResult.CONSUME;
                        }
                        if (sl.getBlockEntity(pos) instanceof ClaimBlockEntity be) {
                            ClaimUpkeep.recompute(sl, claim, be);
                            NetworkHooks.openScreen(sp, be, buf -> buf.writeBlockPos(pos));
                        }

                        return InteractionResult.CONSUME;
                    }
                }
            }
        }

    public static void retire(ServerLevel sl, BlockPos pos) {
        if (ClaimManager.getByAnchor(sl, pos) != null) {
            ClaimManager.removeByAnchor(sl, pos);
            // HeraliZ : plus de Claim Block, plus de texte « Base protégée » au-dessus
            fr.heraliz.crac.bases.Protections.majTextes(sl.getServer());
            C2SRequestNearbyClaimsTraitement.pushAll(sl);
        }
    }

    /**
     * A Claim Block whose claim was lost (a crash between the chunk and the claim data being saved): its group's owner
     * restores the claim by using it, when the placement rules still allow it. Returns the restored claim or null.
     */
    @Nullable
    public static Claim restoreClaim(ServerLevel sl, ServerPlayer sp, BlockPos pos, BlockState state) {
        UUID owner = sl.getBlockEntity(pos) instanceof ClaimBlockEntity be ? be.ownerGroup() : null;
        Group g = GroupData.get(sl.getServer()).getByMember(sp.getUUID());
        if (owner == null || g == null || !g.id().equals(owner)) {
            sp.displayClientMessage(Txt.red("crac.claim.not_linked"), true);
            return null;
        }
        String problem = ClaimBlockHandler.placementProblem(sl, sp, g, pos, state.getValue(ClaimBlock.TIER));
        if (problem != null) {
            sp.displayClientMessage(Txt.red("crac.claim.lost", Txt.c(problem)), false);
            return null;
        }
        ClaimManager.addClaim(sl, pos, g.id(), state.getValue(ClaimBlock.TIER));
        C2SRequestNearbyClaimsTraitement.pushAll(sl);
        sp.displayClientMessage(Txt.green("crac.claim.restored", g.name()), false);
        return ClaimManager.getByAnchor(sl, pos);
    }

    private static boolean tryUpgrade(ServerLevel sl, ServerPlayer sp, BlockPos pos, BlockState state, Claim claim, ItemStack held) {
        int tier = claim.tier();
        String itemId = tier == 1 ? Config.claimUpgradeItemL1L2.get() : Config.claimUpgradeItemL2L3.get();
        int cost = tier == 1 ? Config.claimUpgradeCostL1L2.get() : Config.claimUpgradeCostL2L3.get();
        Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(itemId));
        if (item != null && item != Items.AIR && held.is(item)) {
            String problem = ClaimManager.upgradeProblem(sl, claim, tier + 1);
            if (problem != null) {
                sp.displayClientMessage(Txt.red(problem), true);
                return true;
            }
            if (countItem(sp, item) < cost) {
                sp.displayClientMessage(Txt.red("crac.claim.upgrade_need", Component.literal(cost + " ").append(item.getDescription()), tier + 1), true);
                return true;
            } else {
                consumeItem(sp, item, cost);
                claim.setTier(tier + 1);
                ClaimData.get(sl).markDirty();
                sl.setBlock(pos, state.setValue(ClaimBlock.TIER, tier + 1), 3);
                C2SRequestNearbyClaimsTraitement.pushAll(sl);
                sp.displayClientMessage(Txt.green("crac.claim.upgraded", tier + 1, Config.claimRadius(tier + 1)), true);
                return true;
            }
        } else {
            return false;
        }
    }

    private static int countItem(ServerPlayer p, Item item) {
        int n = 0;

        for (ItemStack s : p.getInventory().items) {
            if (s.is(item)) {
                n += s.getCount();
            }
        }

        return n;
    }

    private static void consumeItem(ServerPlayer p, Item item, int amount) {
        int remaining = amount;

        for (ItemStack s : p.getInventory().items) {
            if (remaining <= 0) {
                break;
            }

            if (s.is(item)) {
                int take = Math.min(remaining, s.getCount());
                s.shrink(take);
                remaining -= take;
            }
        }

        p.getInventory().setChanged();
    }
}
