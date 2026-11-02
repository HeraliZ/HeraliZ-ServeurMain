package fr.heraliz.crac.placable;

import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.heraliz.Heraliz;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.jetbrains.annotations.Nullable;

/**
 * Pose de la table d'armurier contre un mur de base, au serveur (retours du 03/11, {@link TableArmurier}) : les deux cases
 * de mur sont laissées à la table ({@link StructureService#ceder}), puis la table y est posée par le chemin habituel de
 * Forge (clichés de blocs, évènement de pose : règles de la base, limite d'une table par base, appui, objet abîmé). Si la
 * pose est refusée, chaque mur reprend sa case au tick suivant ({@code StructureService.reboucher}) : rien n'est perdu.
 * Ailleurs (pas devant un mur droit, un angle impossible, un objet dans la place…), la table se pose comme avant.
 */
@EventBusSubscriber(modid = "crac")
public final class TablePose {
    private TablePose() {
    }

    /** Clic droit en survie, table en main (le clic en aventure passe par {@code C2SPlacePlacableTraitement}). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void clic(PlayerInteractEvent.RightClickBlock e) {
        if (!(e.getEntity() instanceof ServerPlayer p) || !(e.getLevel() instanceof ServerLevel)
            || e.getUseItem() == Event.Result.DENY || !p.getAbilities().mayBuild) {
            return;
        }
        InteractionResult r = poser(p, e.getHand(), e.getHitVec());
        if (r != null) {
            e.setCanceled(true);
            e.setCancellationResult(r);
        }
    }

    /**
     * Pose la table tenue dans {@code hand} contre le mur visé par {@code hit}, ou rend null quand ce n'est pas une pose
     * contre un mur (la pose habituelle a lieu).
     */
    @Nullable
    public static InteractionResult poser(ServerPlayer p, InteractionHand hand, BlockHitResult hit) {
        ItemStack stack = p.getItemInHand(hand);
        if (!(stack.getItem() instanceof BlockItem bi) || !TableArmurier.estTable(bi.getBlock())
            || !(p.level() instanceof ServerLevel level) || Heraliz.blocked(level)) {
            return null;
        }
        TableArmurier.DansMur d = TableArmurier.dansMur(level, p, hit);
        if (d == null || !level.mayInteract(p, d.pied()) || !level.mayInteract(p, d.tete())) {
            return null;
        }
        // seulement dans une base où le joueur construit (sinon : la pose habituelle, refusée par les règles de la base)
        Claim claim = ClaimManager.getAt(level, d.pied());
        if (claim == null || claim != ClaimManager.getAt(level, d.tete())) {
            return null;
        }
        Group g = GroupData.get(level.getServer()).getById(claim.groupId());
        if (!ClanAccess.allows(g, p.getUUID(), ClanAccess.Action.BUILD)) {
            return null;
        }
        // les deux cases prises au mur ; une case refusée : celle déjà prise est rendue au mur au tick suivant
        if (level.getBlockState(d.tete()).getBlock() instanceof StructureBlock && !StructureService.ceder(level, d.tete())
            || level.getBlockState(d.pied()).getBlock() instanceof StructureBlock && !StructureService.ceder(level, d.pied())) {
            return InteractionResult.FAIL;
        }
        // la case du pied est libre : le contexte de pose la vise elle-même (même sens : celui du regard du joueur)
        BlockHitResult dans = new BlockHitResult(Vec3.atCenterOf(d.pied()), d.dos().getOpposite(), d.pied(), false);
        PlacableHpManager.notePlacingHand(p, hand);
        InteractionResult r = ForgeHooks.onPlaceItemIntoWorld(new UseOnContext(p, hand, dans));
        if (r.consumesAction()) {
            // le joueur n'a rien posé chez lui (pose décidée ici) : il entend le son de la pose
            BlockState posee = level.getBlockState(d.pied());
            SoundType st = posee.getSoundType(level, d.pied(), p);
            p.playNotifySound(st.getPlaceSound(), SoundSource.BLOCKS, (st.getVolume() + 1.0F) / 2.0F, st.getPitch() * 0.8F);
        }
        return r.consumesAction() ? InteractionResult.CONSUME : InteractionResult.FAIL;
    }
}
