package fr.heraliz.crac.serveur;

import fr.heraliz.crac.Config;
import fr.heraliz.crac.block.ClaimBlock;
import fr.heraliz.crac.block.ClaimBlockLogique;
import fr.heraliz.crac.block.entity.ClaimBlockEntity;
import fr.heraliz.crac.build.world.StructureIntegrity;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.event.ClaimProtectionHandler;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.heraliz.ClanAccess;
import fr.heraliz.crac.network.NetworkHandler;
import fr.heraliz.crac.network.C2SAdminAction;
import fr.heraliz.crac.network.C2SAdminActionTraitement;
import fr.heraliz.crac.network.C2SAdminConfig;
import fr.heraliz.crac.network.C2SAdminConfigTraitement;
import fr.heraliz.crac.network.C2SAdminLogin;
import fr.heraliz.crac.network.C2SAdminLoginTraitement;
import fr.heraliz.crac.network.C2SAdminPlacable;
import fr.heraliz.crac.network.C2SAdminPlacableTraitement;
import fr.heraliz.crac.network.C2SCreateGroup;
import fr.heraliz.crac.network.C2SCreateGroupTraitement;
import fr.heraliz.crac.network.C2SGroupAction;
import fr.heraliz.crac.network.C2SGroupActionTraitement;
import fr.heraliz.crac.network.C2SInviteResponse;
import fr.heraliz.crac.network.C2SInviteResponseTraitement;
import fr.heraliz.crac.network.C2SPickup;
import fr.heraliz.crac.network.C2SPickupTraitement;
import fr.heraliz.crac.network.C2SPlacePlacable;
import fr.heraliz.crac.network.C2SPlacePlacableTraitement;
import fr.heraliz.crac.network.C2SRequestNearbyClaims;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import fr.heraliz.crac.build.net.C2SHammer;
import fr.heraliz.crac.build.net.C2SHammerTraitement;
import fr.heraliz.crac.build.net.C2SPieceInfo;
import fr.heraliz.crac.build.net.C2SPieceInfoTraitement;
import fr.heraliz.crac.build.net.C2SPlacePiece;
import fr.heraliz.crac.build.net.C2SPlacePieceTraitement;
import fr.heraliz.crac.pont.Partie;
import fr.heraliz.crac.pont.Serveur;
import fr.heraliz.crac.raid.BlastDamage;
import fr.heraliz.crac.raid.RaidBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.eventbus.api.IEventBus;
import org.jetbrains.annotations.Nullable;

/**
 * Partie serveur de CRac (docs/METHODE_CLIENT_SERVEUR.md §4) : règles de construction et intégrité des structures,
 * claims et entretien, raids et explosifs, protections, groupes, administration, sièges de zombies, commandes.
 * Les écouteurs serveur (@EventBusSubscriber de src/serveur) s'inscrivent seuls.
 */
public final class PartieServeur implements Partie {
    @Override
    public void init(IEventBus busMod) {
        Serveur.MOTEUR = new Moteur();
        // monde des bases (1.5.0) : décision des carrières
        fr.heraliz.crac.bases.CarrieresServeur.brancher();
        NetworkHandler.RECEPTION_SERVEUR.brancher(PartieServeur::recevoir);
    }

    /** Traitement d'origine des paquets client -> serveur. */
    private static void recevoir(NetworkHandler.Recu recu) {
        Object paquet = recu.paquet();
        if (paquet instanceof C2SAdminAction p) {
            C2SAdminActionTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SAdminConfig p) {
            C2SAdminConfigTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SAdminLogin p) {
            C2SAdminLoginTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SAdminPlacable p) {
            C2SAdminPlacableTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SCreateGroup p) {
            C2SCreateGroupTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SGroupAction p) {
            C2SGroupActionTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SInviteResponse p) {
            C2SInviteResponseTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SPickup p) {
            C2SPickupTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SPlacePlacable p) {
            C2SPlacePlacableTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SRequestNearbyClaims p) {
            C2SRequestNearbyClaimsTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SHammer p) {
            C2SHammerTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SPieceInfo p) {
            C2SPieceInfoTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof C2SPlacePiece p) {
            C2SPlacePieceTraitement.traiter(p, recu.ctx());
        } else if (paquet instanceof fr.heraliz.crac.network.C2SAccesBase p) {
            fr.heraliz.crac.bases.AccesBaseTraitement.traiter(p, recu.ctx());
        }
    }

    /** Corps serveur d'origine des blocs, entités et mixins communs. */
    private static final class Moteur implements Serveur.Moteur {
        @Override
        public InteractionResult claimUtiliser(ClaimBlock bloc, BlockState state, ServerLevel sl, BlockPos pos, ServerPlayer sp, InteractionHand hand) {
            return ClaimBlockLogique.utiliser(bloc, state, sl, pos, sp, hand);
        }

        @Override
        public void claimRetire(ServerLevel sl, BlockPos pos) {
            ClaimBlockLogique.retire(sl, pos);
        }

        @Override
        public boolean claimAcces(ClaimBlockEntity be, ServerLevel sl, Player player) {
            Claim claim = ClaimManager.getByAnchor(sl, be.getBlockPos());
            Group g = claim == null ? null : GroupData.get(sl.getServer()).getById(claim.groupId());
            return ClanAccess.allows(g, player.getUUID(), ClanAccess.Action.CHESTS);
        }

        @Override
        public InteractionResult structureUtiliser(Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
            return StructureService.onUse(level, pos, player, hand, hit);
        }

        @Override
        public void structureSol(Level level, BlockPos pos) {
            StructureIntegrity.queueGroundCheck(level, pos);
        }

        @Override
        public void structureDisparue(Level level, BlockPos pos) {
            StructureService.onBlockGone(level, pos);
        }

        @Override
        public void structureVerifier(Level level, BlockPos pos) {
            StructureService.queueVerify(level, pos);
        }

        @Override
        public void c4(ServerLevel level, BlockPos pos, Vec3 hit, @Nullable Direction face) {
            if (!BlastDamage.stuck(level, pos, hit, Config.c4Damage.get()) && face != null) {
                RaidBlocks.breakAround(level, pos, Config.c4BlockRadius.get());
            }
        }

        @Override
        public void charge(ServerLevel level, BlockPos pos, Vec3 hit, Vec3 position, @Nullable Direction face) {
            // pieces first: the sight lines are traced while the blocks the charge breaks still stand
            BlastDamage.charge(level, pos, hit, position, Config.explosiveChargeDamage.get());
            if (face != null) {
                RaidBlocks.breakAround(level, pos, Config.explosiveChargeBlockRadius.get());
            }
        }

        @Override
        public boolean feuPeutPasser(ServerLevel sl, BlockPos from, BlockPos to) {
            return ClaimProtectionHandler.fireMaySpread(sl, from, to);
        }

        @Override
        public boolean fluidePeutEntrer(ServerLevel sl, BlockPos from, BlockPos to) {
            return ClaimProtectionHandler.fluidMayEnter(sl, from, to);
        }
    }
}
