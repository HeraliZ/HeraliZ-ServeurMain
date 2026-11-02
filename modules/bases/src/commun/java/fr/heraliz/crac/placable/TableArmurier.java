package fr.heraliz.crac.placable;

import fr.heraliz.crac.block.Adosse;
import fr.heraliz.crac.build.logic.PlacementRules;
import fr.heraliz.crac.build.model.PieceKey;
import fr.heraliz.crac.build.world.Occupant;
import fr.heraliz.crac.build.world.StructureBlock;
import fr.heraliz.crac.build.world.StructureBlockEntity;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Table d'armurier de TaCZ ({@code tacz:gun_smith_table}) posée contre un mur de base (retours du propriétaire du 03/11 :
 * « placer la table directement contre un mur, sans imposer un bloc d'espace »). Mêmes règles des deux côtés : fantôme de
 * pose du joueur ({@code client/PlacableGhostRenderer}), clic du joueur ({@code client/event/TableArmurierClient}) et pose
 * au serveur ({@code placable/TablePose}).
 * <p>
 * La table de TaCZ se pose comme un lit couché de côté : sa case du pied est la case visée, sa tête la case à droite du
 * joueur ({@code facing} = sens du regard tourné d'un quart vers la droite, {@code GunSmithTableBlockB}). Son dos est
 * tourné vers le sens du regard, à ras du bord arrière de ses cases, et son plateau dépasse de 2,875 px devant. Les murs de
 * CRac prennent toute la rangée de cases le long du bord d'une fondation : posée devant un mur, la table restait à
 * 14 px de sa face. Elle peut maintenant prendre deux cases de cette rangée, comme les coffres ({@code Adosse.dansMur}) :
 * chaque mur lui laisse la moitié intérieure de ces cases ({@code StructureService.ceder}, règle d'enfouissement de
 * {@link PlacementRules#mayBury}) et la reprend quand elle part. Son dos est alors sur le plan du milieu du mur, dans la
 * moitié extérieure que la case voisine dessine toujours : on la voit collée à la face du mur.
 * <p>
 * Ce que la table ne peut pas changer (bloc de TaCZ, sans propriété ni modèle à nous) : elle n'est pas poussée, elle prend
 * les cases du mur telles qu'elles sont.
 */
public final class TableArmurier {
    /** Le plateau de la table dépasse de sa case, vers l'avant (px, modèle {@code gun_smith_table} du pack). */
    public static final double DEBORD_AVANT = 2.875;

    private TableArmurier() {
    }

    /** Une pose contre le mur : case du pied (où le bloc est posé), case de la tête, sens de la table, côté du mur. */
    public record DansMur(BlockPos pied, BlockPos tete, Direction facing, Direction dos) {
    }

    /** Le bloc est la table d'armurier de TaCZ (deux cases, propriétés « part » et « facing » lues par leur nom). */
    public static boolean estTable(Block b) {
        return MultiBlocks.estTableTacz(b);
    }

    /** Sens de la table posée par ce joueur : celui de {@code GunSmithTableBlockB.getStateForPlacement}. */
    public static Direction facing(Player p) {
        return p.getDirection().getClockWise();
    }

    /** L'état de la table tournée selon {@code facing} (sa case du pied). */
    public static BlockState etat(Block table, Direction facing) {
        BlockState s = table.defaultBlockState();
        Property<?> f = table.getStateDefinition().getProperty("facing");
        return f != null && f.getValueClass() == Direction.class ? avec(s, f, facing) : s;
    }

    @SuppressWarnings("unchecked")
    private static <T extends Comparable<T>> BlockState avec(BlockState s, Property<?> p, Object v) {
        return s.setValue((Property<T>) p, (T) v);
    }

    /**
     * La pose de la table contre le mur visé, ou null (pose habituelle de Minecraft) : le joueur vise la face d'un mur
     * droit, ou le sol d'une case de mur, face à ce mur ; la case visée devient le pied ou la tête de la table.
     */
    @Nullable
    public static DansMur dansMur(BlockGetter level, Player p, BlockHitResult hit) {
        Direction dos = p.getDirection();
        Direction facing = dos.getClockWise();
        BlockPos vise;
        Direction face = hit.getDirection();
        if (face == dos.getOpposite()) {
            // la case touchée : un pas dans la face, depuis le point visé (la face du mur est dans la case du mur)
            Vec3 c = hit.getLocation();
            vise = BlockPos.containing(c.x - face.getStepX() * 1.0E-3, c.y, c.z - face.getStepZ() * 1.0E-3);
        } else if (face == Direction.UP) {
            vise = hit.getBlockPos().above();   // le sol d'une case de mur : la case du mur au-dessus
        } else {
            return null;
        }
        if (!(level.getBlockState(vise).getBlock() instanceof StructureBlock)) {
            return null;
        }
        // la case visée est le pied (la tête à sa droite), sinon la tête (le pied à sa gauche)
        for (BlockPos pied : new BlockPos[]{vise, vise.relative(facing.getOpposite())}) {
            BlockPos tete = pied.relative(facing);
            if (caseMur(level, pied, dos, facing.getOpposite()) && caseMur(level, tete, dos, facing)
                && devantLibre(level, pied, dos) && devantLibre(level, tete, dos) && sansObjet(level, pied, tete, dos)) {
                return new DansMur(pied.immutable(), tete.immutable(), facing, dos);
            }
        }
        return null;
    }

    /**
     * La case de mur {@code cell} peut être laissée à la table : elle ne tient que des murs droits sans porte, dont l'un a
     * son plan sur le bord {@code dos} de la case (le mur contre lequel on pose) et un autre au plus sur le bord {@code cote}
     * (dans un angle, de l'autre côté de la table) ; chacun peut la laisser (son autre moitié ferme le bord) ; dessous, un
     * sol plein à ras de la case (fondation ou plancher).
     */
    static boolean caseMur(BlockGetter level, BlockPos cell, Direction dos, Direction cote) {
        if (!(level.getBlockState(cell).getBlock() instanceof StructureBlock)
            || !(level.getBlockEntity(cell) instanceof StructureBlockEntity be)) {
            return false;
        }
        List<Occupant> occ = be.occupants();
        if (occ.isEmpty() || Adosse.doitDescendre(level, cell) || !StructureBlock.holdsBlockOnTop(level, cell.below())) {
            return false;
        }
        boolean contreLeMur = false;
        for (Occupant o : occ) {
            PieceKey k = o.key();
            if (!k.kind().isWall() || k.kind().hasDoor() || k.orient() % 3 != 0) {
                return false;
            }
            // plan du mur : x constant (bord est-ouest de la case) pour une arête vers +z, z constant sinon (Adosse.dansMur)
            boolean versZ = k.orient() == 3;
            double plan = versZ ? k.origin().getX() * 16.0 + k.anchor().toX() : k.origin().getZ() * 16.0 + k.anchor().toZ();
            double bord = versZ ? cell.getX() * 16.0 : cell.getZ() * 16.0;
            Direction bordDuMur;
            if (Math.abs(plan - bord) < 1e-6) {
                bordDuMur = versZ ? Direction.WEST : Direction.NORTH;
            } else if (Math.abs(plan - bord - 16.0) < 1e-6) {
                bordDuMur = versZ ? Direction.EAST : Direction.SOUTH;
            } else {
                return false;
            }
            if (bordDuMur == dos) {
                contreLeMur = true;
            } else if (bordDuMur != cote) {
                return false;   // un mur devant la table ou entre ses deux cases
            }
            long id = o.pieceId();
            if (!PlacementRules.mayBury(k, cell, m -> tientLaPiece(level, m, id))) {
                return false;
            }
        }
        return contreLeMur;
    }

    /** Devant la case (où dépasse le plateau) : une case libre ou une case de construction (mur d'un angle), rien d'autre. */
    private static boolean devantLibre(BlockGetter level, BlockPos cell, Direction dos) {
        BlockState s = level.getBlockState(cell.relative(dos.getOpposite()));
        return s.canBeReplaced() || s.getBlock() instanceof StructureBlock;
    }

    /** Aucun objet adossé voisin (armoire, caisse, coffre poussés vers ce mur) dans la place de la table. */
    private static boolean sansObjet(BlockGetter level, BlockPos pied, BlockPos tete, Direction dos) {
        AABB boite = new AABB(pied).minmax(new AABB(tete)).expandTowards(0, 1, 0)
            .expandTowards(dos.getOpposite().getStepX() * DEBORD_AVANT / 16.0, 0, dos.getOpposite().getStepZ() * DEBORD_AVANT / 16.0)
            .deflate(1.0E-3);
        for (BlockPos c : new BlockPos[]{pied, tete}) {
            for (Adosse.Voisin o : Adosse.voisins(level, c, 2)) {
                if (o.boite().intersects(boite)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** La case {@code pos} est une case de construction écrite pour la pièce {@code id} (le joueur n'a pas les pièces). */
    private static boolean tientLaPiece(BlockGetter level, BlockPos pos, long id) {
        if (!(level.getBlockEntity(pos) instanceof StructureBlockEntity be)) {
            return false;
        }
        for (Occupant o : be.occupants()) {
            if (o.pieceId() == id) {
                return true;
            }
        }
        return false;
    }
}
