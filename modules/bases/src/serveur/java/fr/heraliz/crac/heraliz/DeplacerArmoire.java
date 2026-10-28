package fr.heraliz.crac.heraliz;

import java.util.ArrayList;
import java.util.List;
import fr.heraliz.crac.Config;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.world.BuildCost;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.event.ClaimBlockHandler;
import fr.heraliz.crac.event.ClaimProtectionHandler;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.item.BuildingHammerItem;
import fr.heraliz.crac.raid.RaidBlockManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * HeraliZ (28/10, demande du propriétaire) : l'armoire à outils se déplace pendant les {@code claimMoveMinutes} (15)
 * minutes qui suivent sa pose. Marteau en main, clic droit dessus : elle revient dans l'inventaire avec son stock
 * d'entretien et ce que sa pose avait coûté en plus d'elle (palier du claim payé à la pose), et la zone de la base est
 * libérée jusqu'à ce qu'elle soit reposée (mêmes règles qu'une pose : zone permise, pas trop près d'une autre base...).
 * Mêmes droits que pour la casser (gérer la base) ; jamais pendant un raid, au tutoriel ni en combat (vérifié par
 * {@code C2SPickupTraitement}). Une armoire posée avant cette version n'a pas d'heure de pose : elle ne se déplace pas.
 * La protection des nouvelles bases de la Purge suit le groupe, pas l'armoire : la reposer ne la rend pas « nouvelle ».
 */
public final class DeplacerArmoire {
    private DeplacerArmoire() {
    }

    public static void ramasser(ServerLevel level, ServerPlayer player, BlockPos pos, BlockState state) {
        if (!(player.getMainHandItem().getItem() instanceof BuildingHammerItem)) {
            return;
        }
        int minutes = Config.claimMoveMinutes.get();
        if (minutes <= 0) {
            player.displayClientMessage(Txt.red("crac.armoire.deplacement_coupe"), true);
            return;
        }
        if (!(level.getBlockEntity(pos) instanceof ClaimBlockEntity be)) {
            return;
        }
        Claim claim = ClaimManager.getByAnchor(level, pos);
        if (claim == null) {
            player.displayClientMessage(Txt.red("crac.claim.not_linked"), true);
            return;
        }
        Group g = GroupData.get(level.getServer()).getById(claim.groupId());
        if (!ClanAccess.allows(g, player.getUUID(), ClanAccess.Action.MANAGE)) {
            player.displayClientMessage(Txt.red("crac.claim.not_yours"), true);
            return;
        }
        if (fr.heraliz.crac.bases.MondeBases.estTuto(level) || fr.heraliz.crac.bases.Tutoriel.enCours(player)) {
            player.displayClientMessage(Txt.red("crac.armoire.tuto"), true);
            return;
        }
        if (RaidBlockManager.isRaidBlocked(level, claim, pos)) {
            player.displayClientMessage(Txt.red(ClaimBlockHandler.RAID_BLOCKED_REMOVAL), true);
            return;
        }
        if (be.poseeLe() <= 0 || System.currentTimeMillis() - be.poseeLe() > minutes * 60_000L) {
            player.displayClientMessage(Txt.red("crac.armoire.trop_tard", minutes), true);
            return;
        }
        List<BlockPos> cells = List.of(pos, pos.above());
        if (!ClaimProtectionHandler.blockInSight(level, player, pos, cells)) {
            player.displayClientMessage(Txt.red("crac.reach.no_sight"), true);
            return;
        }
        // tout ce qui est rendu est pris avant de retirer le bloc (son retrait jetterait le stock au sol)
        List<ItemStack> rendre = new ArrayList<>(be.drainToStacks());
        for (BuildCost c : ClaimManager.tierCost(be.tierPaye())) {
            ItemStack[] choix = c.ingredient().getItems();
            if (c.problem() == null && c.count() > 0 && choix.length > 0) {
                rendre.add(choix[0].copyWithCount(c.count()));
            }
        }
        rendre.add(new ItemStack(state.getBlock().asItem()));
        // onRemove : claim retiré, case du haut retirée, mur voisin rebouché, zones renvoyées aux joueurs
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
        for (ItemStack st : rendre) {
            donner(player, st);
        }
        level.playSound(null, pos, state.getSoundType().getBreakSound(), SoundSource.BLOCKS, 0.8F, 1.0F);
        player.displayClientMessage(Txt.green("crac.armoire.ramassee"), false);
        Crac.LOGGER.info("[bases] {} a ramassé l'armoire de son groupe en {} {} {} ({}) pour la déplacer", player.getGameProfile().getName(),
            pos.getX(), pos.getY(), pos.getZ(), level.dimension().location());
    }

    private static void donner(ServerPlayer p, ItemStack st) {
        while (!st.isEmpty()) {
            ItemStack part = st.split(st.getMaxStackSize());
            if (!p.getInventory().add(part)) {
                p.drop(part, false);
            }
        }
    }
}
