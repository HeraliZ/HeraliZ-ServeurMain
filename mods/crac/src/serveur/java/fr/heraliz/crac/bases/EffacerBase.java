package fr.heraliz.crac.bases;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.CommandDispatcher;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import fr.heraliz.crac.Crac;
import fr.heraliz.crac.admin.AdminManager;
import fr.heraliz.crac.build.model.Building;
import fr.heraliz.crac.build.model.Piece;
import fr.heraliz.crac.build.world.StructureService;
import fr.heraliz.crac.build.world.StructureStore;
import fr.heraliz.crac.claim.Claim;
import fr.heraliz.crac.claim.ClaimData;
import fr.heraliz.crac.claim.ClaimManager;
import fr.heraliz.crac.group.Group;
import fr.heraliz.crac.group.GroupData;
import fr.heraliz.crac.network.C2SRequestNearbyClaimsTraitement;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.GameProfileArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

/**
 * {@code /base effacer <joueur>} (op seulement, demande du propriétaire du 18/10) : la base d'un joueur disparaît d'un
 * coup, contenu compris, rien ne tombe au sol : toutes les pièces et les objets posés du claim de son groupe, le Claim
 * Block et ses planches, puis le claim lui-même ; aussi les bâtiments sans claim qu'il a posés (cabane du tutoriel pas
 * finie). Joueur connecté ou non. Ses homes hors d'une base disparaissent seuls à leur prochain usage ; le tutoriel ne
 * change pas ({@code /tuto rejouer} pour le refaire).
 */
final class EffacerBase {
    private EffacerBase() {
    }

    static void commandes(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("base").requires(s -> s.hasPermission(4))
            .then(Commands.literal("effacer").then(Commands.argument("joueur", GameProfileArgument.gameProfile())
                .executes(c -> {
                    int n = 0;
                    for (GameProfile g : GameProfileArgument.getGameProfiles(c, "joueur")) {
                        n += effacer(c.getSource(), g);
                    }
                    return n;
                }))));
    }

    private static int effacer(CommandSourceStack src, GameProfile profil) {
        MinecraftServer server = src.getServer();
        UUID joueur = profil.getId();
        int pieces = 0;
        boolean claim = false;
        Group g = GroupData.get(server).getByMember(joueur);
        if (g != null) {
            for (ServerLevel level : server.getAllLevels()) {
                Claim c = ClaimData.get(level).getByGroup(g.id());
                if (c == null) {
                    continue;
                }
                BlockPos a = c.anchor();
                BlockPos min = new BlockPos(a.getX() - c.radius(), Math.max(level.getMinBuildHeight(), a.getY() - c.yRadius()), a.getZ() - c.radius());
                BlockPos max = new BlockPos(a.getX() + c.radius(), Math.min(level.getMaxBuildHeight() - 1, a.getY() + c.yRadius()), a.getZ() + c.radius());
                Set<Piece> tout = new LinkedHashSet<>(AdminManager.piecesIn(level, min, max));
                Building b = StructureService.buildingOfClaim(level, a);
                if (b != null) {
                    tout.addAll(StructureStore.get(level).piecesOf(b));
                }
                vider(level, min, max);
                pieces += retirer(level, tout);
                AdminManager.removePlacables(level, min, max);
                // le Claim Block sans le faire tomber, puis le claim
                level.removeBlock(a, false);
                ClaimManager.releaseClaim(level, g.id());
                AccesBase.get(server).effacer(g.id());   // accès donnés dans l'armoire
                C2SRequestNearbyClaimsTraitement.pushAll(level);
                ramasser(level, min, max);
                claim = true;
            }
        }
        // ses bâtiments sans claim (cabane du tutoriel pas finie, essais)
        for (ServerLevel level : server.getAllLevels()) {
            StructureStore store = StructureStore.get(level);
            Set<Long> batiments = new LinkedHashSet<>();
            for (Piece p : store.index.allPieces()) {
                if (joueur.equals(p.owner) && StructureService.claimOf(level, store.index.building(p.buildingId)) == null) {
                    batiments.add(p.buildingId);
                }
            }
            for (long id : batiments) {
                Building b = store.index.building(id);
                if (b == null) {
                    continue;
                }
                List<Piece> ps = store.piecesOf(b);
                AABB zone = null;
                for (Piece p : ps) {
                    AABB box = StructureService.bounds(p);
                    if (box != null) {
                        zone = zone == null ? box : zone.minmax(box);
                    }
                }
                if (zone != null) {
                    BlockPos min = BlockPos.containing(zone.minX, zone.minY, zone.minZ);
                    BlockPos max = BlockPos.containing(zone.maxX, zone.maxY + 3, zone.maxZ);
                    vider(level, min, max);
                    pieces += retirer(level, ps);
                    AdminManager.removePlacables(level, min, max);
                    ramasser(level, min, max);
                }
            }
        }
        BasesDonnees d = BasesDonnees.get(server);
        BasesDonnees.Joueur j = d.joueur(joueur);
        j.batiment = -1L;
        j.arete = -1;
        d.nettoyerHomes(server);   // les homes qui étaient dans la base effacée
        Protections.majTextes(server);
        String nom = profil.getName();
        Crac.LOGGER.info("[CRac] base de {} effacée par {} ({} pièces, claim : {})", nom, src.getTextName(), pieces, claim);
        if (pieces == 0 && !claim) {
            src.sendSuccess(() -> T.t("crac.base.effacer.rien", "%s n'a aucune base.", nom).withStyle(ChatFormatting.YELLOW), false);
            return 0;
        }
        String n = String.valueOf(pieces);
        src.sendSuccess(() -> T.t("crac.base.effacer.fait", "Base de %s effacée (%s pièces).", nom, n).withStyle(ChatFormatting.GREEN), true);
        return 1;
    }

    private static int retirer(ServerLevel level, Collection<Piece> pieces) {
        StructureStore store = StructureStore.get(level);
        int n = 0;
        for (Piece p : new ArrayList<>(pieces)) {
            if (store.index.piece(p.id) != null) {
                StructureService.remove(level, p, StructureService.Cause.ADMIN);
                n++;
            }
        }
        return n;
    }

    /** Tous les conteneurs de la zone vidés avant d'être retirés : leur contenu disparaît au lieu de tomber. */
    private static void vider(ServerLevel level, BlockPos min, BlockPos max) {
        for (int cx = min.getX() >> 4; cx <= max.getX() >> 4; cx++) {
            for (int cz = min.getZ() >> 4; cz <= max.getZ() >> 4; cz++) {
                for (BlockEntity be : level.getChunk(cx, cz).getBlockEntities().values()) {
                    BlockPos q = be.getBlockPos();
                    if (be instanceof Container c && q.getX() >= min.getX() && q.getX() <= max.getX() && q.getY() >= min.getY()
                        && q.getY() <= max.getY() && q.getZ() >= min.getZ() && q.getZ() <= max.getZ()) {
                        c.clearContent();
                        be.setChanged();
                    }
                }
            }
        }
    }

    /** Ce qui serait quand même tombé (objets posés sur une pièce retirée) est retiré aussi. */
    private static void ramasser(ServerLevel level, BlockPos min, BlockPos max) {
        AABB zone = new AABB(min, max.offset(1, 1, 1)).inflate(2);
        for (ItemEntity it : level.getEntitiesOfClass(ItemEntity.class, zone)) {
            it.discard();
        }
    }
}
